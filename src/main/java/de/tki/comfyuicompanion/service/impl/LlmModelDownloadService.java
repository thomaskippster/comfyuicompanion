package de.tki.comfyuicompanion.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.function.BiConsumer;

/**
 * Service handling background on-demand downloading of GGUF model files
 * with progress notification and streaming buffer management.
 */
@Service
public class LlmModelDownloadService {

    private static final Logger logger = LoggerFactory.getLogger(LlmModelDownloadService.class);

    /**
     * Downloads a model file from a remote URL to target file with progress reporting.
     *
     * @param displayName      human-readable model name
     * @param downloadUrl      remote GGUF download URL
     * @param targetFile       destination file on disk
     * @param progressListener callback receiving (fraction, statusString)
     * @param onFinished       callback on successful download
     * @param onError          callback on download failure
     */
    public void downloadModelFile(String displayName, String downloadUrl, File targetFile,
                                  BiConsumer<Double, String> progressListener,
                                  Runnable onFinished,
                                  BiConsumer<String, Exception> onError) {
        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        File tempFile = new File(parentDir, targetFile.getName() + ".download");
        try {
            logger.info("Downloading LLM GGUF ({}) from: {}", displayName, downloadUrl);
            URL url = new URL(downloadUrl);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);

            long totalBytes = connection.getContentLengthLong();

            try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream out = new FileOutputStream(tempFile)) {

                byte[] buffer = new byte[8192];
                long bytesRead = 0;
                int count;
                long lastUpdateTime = System.currentTimeMillis();

                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                    bytesRead += count;

                    long now = System.currentTimeMillis();
                    if (now - lastUpdateTime > 1000 && totalBytes > 0) {
                        double percent = (double) bytesRead / totalBytes;
                        double mbRead = (double) bytesRead / (1024 * 1024);
                        double mbTotal = (double) totalBytes / (1024 * 1024);
                        String status = String.format("Downloaded %.1f / %.1f MB (%.1f%%)", mbRead, mbTotal, percent * 100);
                        if (progressListener != null) {
                            progressListener.accept(percent, status);
                        }
                        lastUpdateTime = now;
                    }
                }
            }

            if (targetFile.exists()) {
                targetFile.delete();
            }
            tempFile.renameTo(targetFile);

            logger.info("{} model download completed successfully.", displayName);
            if (onFinished != null) {
                onFinished.run();
            }
        } catch (Exception e) {
            logger.error("Download failed for {}: {}", displayName, e.getMessage(), e);
            if (tempFile.exists()) {
                tempFile.delete();
            }
            if (onError != null) {
                onError.accept("Download failed: " + e.getMessage(), e);
            }
        }
    }
}
