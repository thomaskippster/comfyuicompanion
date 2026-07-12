package com.thomaskippster.comfyuicompanion.service.provisioning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPOutputStream;

@Service
public class ModelArchivingService {

    private static final Logger logger = LoggerFactory.getLogger(ModelArchivingService.class);
    
    // 8 MB Chunk size für I/O Operationen, um den RAM (Heap) nicht zu fluten
    private static final int BUFFER_SIZE = 8 * 1024 * 1024; 

    private final String modelsBasePath;
    private final String archiveBasePath;

    public ModelArchivingService(@Value("${comfyui.models.path:models}") String modelsBasePath,
                                 @Value("${comfyui.archive.path:archives}") String archiveBasePath) {
        this.modelsBasePath = modelsBasePath;
        this.archiveBasePath = archiveBasePath;
    }

    /**
     * Verschiebt ein Modell extrem schnell innerhalb derselben Festplatte (NIO Move) 
     * oder kopiert es asynchron, falls Partitionsgrenzen überschritten werden.
     */
    public CompletableFuture<Boolean> moveToArchive(String relativeModelPath) {
        return CompletableFuture.supplyAsync(() -> {
            Path source = Paths.get(modelsBasePath, relativeModelPath);
            Path target = Paths.get(archiveBasePath, relativeModelPath);

            try {
                if (!Files.exists(source)) {
                    logger.warn("Archivierung fehlgeschlagen: Quelldatei existiert nicht: {}", source);
                    return false;
                }

                Files.createDirectories(target.getParent());
                logger.info("Starte Archivierung (Move): {} -> {}", source, target);
                
                // ATOMIC_MOVE bevorzugen (nahezu instant auf derselben Partition)
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                
                logger.info("Archivierung (Move) erfolgreich abgeschlossen: {}", target);
                return true;
            } catch (AtomicMoveNotSupportedException e) {
                // Fallback, wenn Quell- und Zielordner auf unterschiedlichen Laufwerken liegen
                try {
                    logger.info("Atomic Move nicht unterstützt (andere Partition?). Führe regulären Move aus...");
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                    logger.info("Regulärer Move erfolgreich: {}", target);
                    return true;
                } catch (IOException ex) {
                    logger.error("Fehler beim Verschieben der Datei", ex);
                    return false;
                }
            } catch (IOException e) {
                logger.error("I/O Fehler bei der Archivierung", e);
                return false;
            }
        });
    }

    /**
     * Komprimiert ein extrem großes Modell (6-20 GB) in ein GZIP-Archiv,
     * strikt gechunked, um OutOfMemoryError (OOM) zu vermeiden.
     */
    public CompletableFuture<Boolean> compressAndArchive(String relativeModelPath) {
        return CompletableFuture.supplyAsync(() -> {
            Path source = Paths.get(modelsBasePath, relativeModelPath);
            Path target = Paths.get(archiveBasePath, relativeModelPath + ".gz");

            try {
                if (!Files.exists(source)) {
                    logger.warn("Komprimierung fehlgeschlagen: Quelldatei existiert nicht: {}", source);
                    return false;
                }

                Files.createDirectories(target.getParent());
                logger.info("Starte chunked Komprimierung (GZIP): {} -> {}", source, target);

                long totalBytes = Files.size(source);
                long bytesReadTotal = 0;

                // Explizite Streams mit massivem Buffer für High-Performance I/O ohne RAM-Erschöpfung
                try (InputStream is = Files.newInputStream(source, StandardOpenOption.READ);
                     OutputStream os = Files.newOutputStream(target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                     GZIPOutputStream gzipOs = new GZIPOutputStream(os, BUFFER_SIZE)) {
                    
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    int progressCounter = 0;

                    while ((bytesRead = is.read(buffer)) != -1) {
                        gzipOs.write(buffer, 0, bytesRead);
                        bytesReadTotal += bytesRead;
                        
                        // Logge Fortschritt ca. alle 10 Chunks
                        if (++progressCounter % 10 == 0) {
                            double percent = (double) bytesReadTotal / totalBytes * 100;
                            logger.debug("Komprimierung Fortschritt: {}%", String.format("%.1f", percent));
                        }
                    }
                }
                
                // Originaldatei nach erfolgreicher Komprimierung löschen
                Files.deleteIfExists(source);
                logger.info("Chunked Komprimierung erfolgreich abgeschlossen. Original gelöscht. Ziel: {}", target);
                return true;

            } catch (IOException e) {
                logger.error("I/O Fehler bei der chunked Komprimierung", e);
                // Aufräumen bei Fehler
                try { Files.deleteIfExists(target); } catch (IOException ignored) {}
                return false;
            }
        });
    }
}
