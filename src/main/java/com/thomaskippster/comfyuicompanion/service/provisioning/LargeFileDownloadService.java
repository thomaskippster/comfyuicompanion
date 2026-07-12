package com.thomaskippster.comfyuicompanion.service.provisioning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class LargeFileDownloadService {

    private static final Logger logger = LoggerFactory.getLogger(LargeFileDownloadService.class);
    private final WebClient webClient;

    public LargeFileDownloadService() {
        // WICHTIG: Hugging Face (und viele LFS-Hoster) nutzen 302/301 Redirects auf ihre CDNs.
        // Der Standard-WebClient folgt diesen nicht! Wir müssen den Netty HttpClient explizit 
        // mit .followRedirect(true) konfigurieren.
        HttpClient httpClient = HttpClient.create().followRedirect(true);
        ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);

        this.webClient = WebClient.builder()
                .clientConnector(connector)
                // Speicherlimitierung aufheben, da wir sonst bei Giga-Byte Streams in eine 
                // ExceededLimit-Exception des Codecs rennen könnten.
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(-1)) 
                .build();
    }

    /**
     * Zieht eine Datei non-blocking und chunked aus dem Netz direkt auf die NVMe/SSD,
     * ohne den Java-Heap mit Gigabytes an Safetensors-Gewichten vollzumüllen.
     * 
     * @param fileUrl Die direkte (oder Redirect-) Download-URL
     * @param targetDestination Wohin die Datei auf der Platte gespeichert werden soll
     * @return Ein Stream aus Double-Werten, die den prozentualen Fortschritt [0.0 - 100.0] emittieren
     */
    public Flux<Double> downloadModel(String fileUrl, Path targetDestination) {
        return webClient.get()
                .uri(fileUrl)
                .exchangeToFlux(response -> {
                    // Versuche, den Content-Length Header auszulesen, um Prozente zu berechnen
                    long contentLength = response.headers().contentLength().orElse(-1L);
                    logger.info("Start reaktiver Download: {} Bytes nach {}", contentLength, targetDestination);

                    AtomicLong downloadedBytes = new AtomicLong(0L);

                    try {
                        // NIO AsynchronousFileChannel öffnet den Kanal zur Festplatte direkt
                        AsynchronousFileChannel channel = AsynchronousFileChannel.open(
                                targetDestination,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.WRITE,
                                StandardOpenOption.TRUNCATE_EXISTING
                        );

                        // Den reinen Byte-Stream der Netzwerkantwort abgreifen
                        Flux<DataBuffer> dataBufferFlux = response.bodyToFlux(DataBuffer.class);

                        // DataBufferUtils.write pumpt die Chunks via NIO auf die Platte
                        return DataBufferUtils.write(dataBufferFlux, channel)
                                .map(dataBuffer -> {
                                    int byteCount = dataBuffer.readableByteCount();
                                    long totalRead = downloadedBytes.addAndGet(byteCount);

                                    // KRITISCH: Netty allokiert Off-Heap Memory für Buffers.
                                    // Wenn wir diese nicht manuell freigeben, entsteht ein natives Memory Leak!
                                    DataBufferUtils.release(dataBuffer);

                                    if (contentLength > 0) {
                                        return ((double) totalRead / contentLength) * 100.0;
                                    } else {
                                        return 0.0; // Fallback, falls der Server keinen Length-Header schickt
                                    }
                                })
                                .doFinally(signalType -> {
                                    // Am Ende oder bei Abbruch zwingend den Datei-Kanal schließen
                                    try {
                                        logger.info("Schließe Datei-Stream für: {} (Signal: {})", targetDestination, signalType);
                                        channel.close();
                                    } catch (Exception e) {
                                        logger.error("Fehler beim Schließen des Download-Channels", e);
                                    }
                                });
                    } catch (Exception e) {
                        return Flux.error(new RuntimeException("AsynchronousFileChannel konnte nicht geöffnet werden.", e));
                    }
                });
    }
}
