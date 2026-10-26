package de.tki.comfyuicompanion.service.provisioning;

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

/**
 * High-performance non-blocking streaming download service for large model weights.
 * Utilizes Netty HTTP client with transparent redirect handling and NIO asynchronous channels
 * to write chunks directly to storage without heap memory overhead.
 */
@Service
public class LargeFileDownloadService {

    private static final Logger logger = LoggerFactory.getLogger(LargeFileDownloadService.class);
    private final WebClient webClient;

    public LargeFileDownloadService() {
        // IMPORTANT: Hugging Face and LFS CDNs issue 302/301 redirects to their storage nodes.
        // The default WebClient does not follow these; configure Netty HttpClient with followRedirect(true).
        HttpClient httpClient = HttpClient.create().followRedirect(true);
        ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);

        this.webClient = WebClient.builder()
                .clientConnector(connector)
                // Remove codec in-memory size limits to prevent ExceededLimitException on multi-gigabyte streams
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(-1)) 
                .build();
    }

    /**
     * Streams a large remote file non-blocking and chunked directly to local storage,
     * preventing Java heap saturation during multi-gigabyte weight downloads.
     *
     * @param fileUrl           the direct or redirecting download URL
     * @param targetDestination local target destination path
     * @return Flux emitting download progress percentages [0.0 - 100.0]
     */
    public Flux<Double> downloadModel(String fileUrl, Path targetDestination) {
        return webClient.get()
                .uri(fileUrl)
                .exchangeToFlux(response -> {
                    // Extract Content-Length header to compute progress percentages
                    long contentLength = response.headers().contentLength().orElse(-1L);
                    logger.info("Starting reactive download: {} bytes to {}", contentLength, targetDestination);

                    AtomicLong downloadedBytes = new AtomicLong(0L);

                    try {
                        // Open asynchronous file channel directly to local storage
                        AsynchronousFileChannel channel = AsynchronousFileChannel.open(
                                targetDestination,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.WRITE,
                                StandardOpenOption.TRUNCATE_EXISTING
                        );

                        // Stream byte chunks from network response
                        Flux<DataBuffer> dataBufferFlux = response.bodyToFlux(DataBuffer.class);

                        // Stream chunks to disk using NIO DataBufferUtils
                        return DataBufferUtils.write(dataBufferFlux, channel)
                                .map(dataBuffer -> {
                                    int byteCount = dataBuffer.readableByteCount();
                                    long totalRead = downloadedBytes.addAndGet(byteCount);

                                    // CRITICAL: Netty allocates off-heap memory for buffers.
                                    // Manually release them to prevent native memory leaks!
                                    DataBufferUtils.release(dataBuffer);

                                    if (contentLength > 0) {
                                        return ((double) totalRead / contentLength) * 100.0;
                                    } else {
                                        return 0.0; // Fallback if server provides no Content-Length header
                                    }
                                })
                                .doFinally(signalType -> {
                                    // Ensure file channel is closed on completion or cancellation
                                    try {
                                        logger.info("Closing file stream for: {} (Signal: {})", targetDestination, signalType);
                                        channel.close();
                                    } catch (Exception e) {
                                        logger.error("Error closing download channel", e);
                                    }
                                });
                    } catch (Exception e) {
                        return Flux.error(new RuntimeException("Could not open AsynchronousFileChannel.", e));
                    }
                });
    }
}
