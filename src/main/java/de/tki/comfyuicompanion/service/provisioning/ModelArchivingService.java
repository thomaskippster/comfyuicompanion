package de.tki.comfyuicompanion.service.provisioning;

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
                    logger.warn("Archiving failed: Source file does not exist: {}", source);
                    return false;
                }

                Files.createDirectories(target.getParent());
                logger.info("Starting archiving (move): {} -> {}", source, target);
                
                // Prefer ATOMIC_MOVE (almost instant on same partition)
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                
                logger.info("Archiving (move) completed successfully: {}", target);
                return true;
            } catch (AtomicMoveNotSupportedException e) {
                // Fallback if source and target directories are on different drives
                try {
                    logger.info("Atomic move not supported (different partition?). Performing regular move...");
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                    logger.info("Regular move successful: {}", target);
                    return true;
                } catch (IOException ex) {
                    logger.error("Error moving file", ex);
                    return false;
                }
            } catch (IOException e) {
                logger.error("I/O error during archiving", e);
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
                    logger.warn("Compression failed: Source file does not exist: {}", source);
                    return false;
                }

                Files.createDirectories(target.getParent());
                logger.info("Starting chunked compression (GZIP): {} -> {}", source, target);

                long totalBytes = Files.size(source);
                long bytesReadTotal = 0;

                // Explicit streams with massive buffer for high-performance I/O without RAM exhaustion
                try (InputStream is = Files.newInputStream(source, StandardOpenOption.READ);
                     OutputStream os = Files.newOutputStream(target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                     GZIPOutputStream gzipOs = new GZIPOutputStream(os, BUFFER_SIZE)) {
                    
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    int progressCounter = 0;

                    while ((bytesRead = is.read(buffer)) != -1) {
                        gzipOs.write(buffer, 0, bytesRead);
                        bytesReadTotal += bytesRead;
                        
                        // Log progress approximately every 10 chunks
                        if (++progressCounter % 10 == 0) {
                            double percent = (double) bytesReadTotal / totalBytes * 100;
                            logger.debug("Compression progress: {}%", String.format("%.1f", percent));
                        }
                    }
                }
                
                // Delete original file after successful compression
                Files.deleteIfExists(source);
                logger.info("Chunked compression completed successfully. Original deleted. Target: {}", target);
                return true;

            } catch (IOException e) {
                logger.error("I/O error during chunked compression", e);
                // Aufräumen bei Fehler
                try { Files.deleteIfExists(target); } catch (IOException ignored) {}
                return false;
            }
        });
    }
}
