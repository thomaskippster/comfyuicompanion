package de.tki.comfymodels.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class DependencyService {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DependencyService.class);

    private final ConfigService configService;


    @Autowired(required = false)
    private ProcessTracker processTracker;
    public DependencyService(ConfigService configService) {
        this.configService = configService;
    }

    public boolean isFfmpegInstalled() {
        String ffmpegPath = configService.getFfmpegPath();
        if ("ffmpeg".equals(ffmpegPath)) {
            // Check if ffmpeg actually runs
            try {
                Process p = processTracker.start(new ProcessBuilder("ffmpeg", "-version"));
                p.waitFor();
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        return new File(ffmpegPath).exists();
    }

    public void installFfmpeg(java.util.function.Consumer<String> onProgress) throws Exception {
        onProgress.accept("Checking environment...");
        if (isFfmpegInstalled()) {
            onProgress.accept("FFmpeg is already installed!");
            return;
        }

        File toolsDir = new File(System.getProperty("user.dir"), "tools/ffmpeg");
        if (!toolsDir.exists()) {
            toolsDir.mkdirs();
        }

        // Try using Conda install if possible since we run in conda
        try {
            onProgress.accept("Attempting installation via Conda...");
            ProcessBuilder pb = new ProcessBuilder("conda", "install", "-y", "-c", "conda-forge", "ffmpeg");
            pb.redirectErrorStream(true);
            Process p = processTracker.start(pb);
            
            // Read output to avoid blocking
            try (InputStream is = p.getInputStream();
                 java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("[Conda Install] " + line);
                }
            }
            int exitCode = p.waitFor();
            if (exitCode == 0 && isFfmpegInstalled()) {
                onProgress.accept("FFmpeg successfully installed via Conda!");
                return;
            }
        } catch (Exception e) {
            logger.info("Conda install failed or not available, falling back to direct download: " + e.getMessage());
        }

        // Direct Download Fallback
        onProgress.accept("Downloading FFmpeg package (this might take a minute)...");
        String urlStr = "https://github.com/GyanD/codexffmpeg/releases/download/7.1/ffmpeg-7.1-essentials_build.zip";
        
        try (ZipInputStream zis = new ZipInputStream(openUrlStreamWithUserAgent(urlStr))) {
            ZipEntry entry;
            boolean extracted = false;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.endsWith("ffmpeg.exe") || name.endsWith("ffmpeg")) {
                    onProgress.accept("Extracting FFmpeg executable...");
                    File targetFile = new File(toolsDir, name.endsWith("ffmpeg.exe") ? "ffmpeg.exe" : "ffmpeg");
                    try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    targetFile.setExecutable(true);
                    extracted = true;
                    break;
                }
                zis.closeEntry();
            }
            if (extracted) {
                onProgress.accept("FFmpeg successfully installed!");
            } else {
                throw new IOException("ffmpeg.exe was not found in the downloaded zip file.");
            }
        }
    }

    private java.io.InputStream openUrlStreamWithUserAgent(String urlStr) throws IOException {
        return openUrlStreamWithUserAgent(urlStr, 0);
    }

    private java.io.InputStream openUrlStreamWithUserAgent(String urlStr, int redirectCount) throws IOException {
        if (redirectCount > 5) {
            throw new IOException("Too many redirects (max 5) for URL: " + urlStr);
        }
        java.net.URL url = new java.net.URL(urlStr);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        conn.setInstanceFollowRedirects(true);
        int status = conn.getResponseCode();
        if (status == java.net.HttpURLConnection.HTTP_MOVED_TEMP 
            || status == java.net.HttpURLConnection.HTTP_MOVED_PERM 
            || status == 307 
            || status == 308) {
            String newUrl = conn.getHeaderField("Location");
            return openUrlStreamWithUserAgent(newUrl, redirectCount + 1);
        }
        if (status != java.net.HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP error code: " + status + " for URL: " + urlStr);
        }
        return conn.getInputStream();
    }

}
