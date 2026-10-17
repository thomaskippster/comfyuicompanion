package de.tki.comfyuicompanion.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Service responsible for verifying and automatically installing external native dependencies
 * such as FFmpeg required by the Video Architect pipeline.
 */
@Service
public class DependencyService {
    private static final Logger logger = LoggerFactory.getLogger(DependencyService.class);

    private static final List<String> FFMPEG_DOWNLOAD_MIRRORS = List.of(
            "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip",
            "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip",
            "https://github.com/GyanD/codexffmpeg/releases/download/7.1/ffmpeg-7.1-essentials_build.zip"
    );

    private final ConfigService configService;
    private final ProcessTracker processTracker;
    private final HttpClient httpClient;

    /**
     * Constructs a DependencyService with full dependency injection.
     *
     * @param configService  the configuration service providing executable paths
     * @param processTracker the process tracker for managing spawned processes
     */
    @Autowired
    public DependencyService(
            ConfigService configService,
            @Autowired(required = false) ProcessTracker processTracker) {
        this.configService = configService;
        this.processTracker = processTracker;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    /**
     * Constructs a DependencyService with only the configuration service.
     *
     * @param configService the configuration service
     */
    public DependencyService(ConfigService configService) {
        this(configService, null);
    }

    /**
     * Checks whether FFmpeg is currently installed and accessible.
     *
     * @return {@code true} if the FFmpeg binary exists or responds to {@code -version}
     */
    public boolean isFfmpegInstalled() {
        String ffmpegPath = configService.getFfmpegPath();
        if ("ffmpeg".equals(ffmpegPath)) {
            try {
                ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
                Process p = processTracker != null ? processTracker.start(pb) : pb.start();
                return p.waitFor() == 0;
            } catch (Exception e) {
                return false;
            }
        }
        return ffmpegPath != null && new File(ffmpegPath).exists();
    }

    /**
     * Installs FFmpeg into the local {@code tools/ffmpeg} directory, attempting Conda first
     * and falling back to resilient direct HTTP mirror downloads.
     *
     * @param onProgress callback receiving human-readable status updates
     * @throws Exception if all installation strategies fail
     */
    public void installFfmpeg(Consumer<String> onProgress) throws Exception {
        onProgress.accept("Checking environment...");
        if (isFfmpegInstalled()) {
            onProgress.accept("FFmpeg is already installed!");
            return;
        }

        File toolsDir = new File(System.getProperty("user.dir"), "tools/ffmpeg");
        if (!toolsDir.exists() && !toolsDir.mkdirs()) {
            logger.warn("Could not create tools directory at: {}", toolsDir.getAbsolutePath());
        }

        if (tryCondaInstall(onProgress)) {
            return;
        }

        IOException lastException = null;
        for (int i = 0; i < FFMPEG_DOWNLOAD_MIRRORS.size(); i++) {
            String mirrorUrl = FFMPEG_DOWNLOAD_MIRRORS.get(i);
            onProgress.accept("Downloading FFmpeg package from mirror " + (i + 1) + "/" + FFMPEG_DOWNLOAD_MIRRORS.size() + "...");
            try {
                if (downloadAndExtractFfmpeg(mirrorUrl, toolsDir, onProgress)) {
                    onProgress.accept("FFmpeg successfully installed!");
                    return;
                }
            } catch (IOException e) {
                lastException = e;
                logger.warn("FFmpeg download from mirror {} failed: {}", mirrorUrl, e.getMessage());
                onProgress.accept("Mirror " + (i + 1) + " unreachable (" + e.getMessage() + "), trying next fallback...");
            }
        }

        throw new IOException("All FFmpeg download mirrors failed. Last error: "
                + (lastException != null ? lastException.getMessage() : "unknown"), lastException);
    }

    private boolean tryCondaInstall(Consumer<String> onProgress) {
        try {
            onProgress.accept("Checking Conda package manager...");
            ProcessBuilder pb = new ProcessBuilder("conda", "install", "-y", "-c", "conda-forge", "ffmpeg");
            pb.redirectErrorStream(true);
            Process p = processTracker != null ? processTracker.start(pb) : pb.start();

            try (InputStream is = p.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("[Conda Install] {}", line);
                }
            }
            int exitCode = p.waitFor();
            if (exitCode == 0 && isFfmpegInstalled()) {
                onProgress.accept("FFmpeg successfully installed via Conda!");
                return true;
            }
        } catch (Exception e) {
            logger.info("Conda install not available, falling back to direct download: {}", e.getMessage());
        }
        return false;
    }

    private boolean downloadAndExtractFfmpeg(String urlStr, File toolsDir, Consumer<String> onProgress) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(openUrlStream(urlStr))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (!entry.isDirectory() && (name.endsWith("/ffmpeg.exe") || name.endsWith("\\ffmpeg.exe")
                        || name.equals("ffmpeg.exe") || name.endsWith("/ffmpeg"))) {
                    onProgress.accept("Extracting FFmpeg executable...");
                    File targetFile = new File(toolsDir, name.endsWith(".exe") ? "ffmpeg.exe" : "ffmpeg");
                    try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    targetFile.setExecutable(true);
                    return true;
                }
                zis.closeEntry();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("FFmpeg download interrupted", e);
        }
        throw new IOException("ffmpeg executable was not found in the downloaded archive.");
    }

    private InputStream openUrlStream(String urlStr) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlStr))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) ComfyUICompanion/1.0")
                .timeout(Duration.ofMinutes(3))
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP status " + response.statusCode() + " from " + urlStr);
        }
        return response.body();
    }
}

