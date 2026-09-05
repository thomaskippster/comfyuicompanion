package de.tki.comfymodels.service.pipeline;

import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.ProcessTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for media conversion and transcoding operations (e.g. FFmpeg image-to-video).
 */
@Service
public class MediaTranscodingService {

    private static final Logger logger = LoggerFactory.getLogger(MediaTranscodingService.class);

    private final ConfigService configService;
    private final ProcessTracker processTracker;

    @Autowired
    public MediaTranscodingService(ConfigService configService,
                                   @Autowired(required = false) ProcessTracker processTracker) {
        this.configService = configService;
        this.processTracker = processTracker;
    }

    public MediaTranscodingService(ConfigService configService) {
        this(configService, null);
    }

    private Process startProcess(ProcessBuilder pb) throws IOException {
        return processTracker != null ? processTracker.start(pb) : pb.start();
    }

    public void validateFfmpeg() throws IOException {
        String ffmpeg = configService != null ? configService.getFfmpegPath() : "ffmpeg";
        try {
            Process p = startProcess(new ProcessBuilder(ffmpeg, "-version"));
            int exit = p.waitFor();
            if (exit != 0) {
                throw new IOException("FFmpeg executable check returned exit code " + exit);
            }
        } catch (Exception e) {
            throw new IOException("FFmpeg not found or not executable at '" + ffmpeg + "': " + e.getMessage(), e);
        }
    }

    public File convertImageToVideo(File imageFile, File audioFile, float durationSeconds, float fps, File outputFile) throws Exception {
        return convertImageToVideo(imageFile, audioFile, durationSeconds, fps, outputFile, 1920, 1080);
    }

    public File convertImageToVideo(File imageFile, File audioFile, float durationSeconds, float fps, File outputFile, int targetWidth, int targetHeight) throws Exception {
        validateFfmpeg();
        String ffmpegPath = configService != null ? configService.getFfmpegPath() : "ffmpeg";

        int w = targetWidth;
        int h = targetHeight;
        if (w <= 0 || h <= 0) {
            try {
                java.awt.image.BufferedImage bimg = javax.imageio.ImageIO.read(imageFile);
                if (bimg != null && bimg.getWidth() > 0 && bimg.getHeight() > 0) {
                    w = (bimg.getWidth() / 2) * 2;
                    h = (bimg.getHeight() / 2) * 2;
                }
            } catch (Exception ignored) {}
        }
        if (w <= 0 || h <= 0) {
            w = 1920;
            h = 1080;
        }
        w = (w / 2) * 2;
        h = (h / 2) * 2;

        String scaleFilter = String.format(java.util.Locale.US,
                "scale=%d:%d:force_original_aspect_ratio=decrease,pad=%d:%d:(ow-iw)/2:(oh-ih)/2", w, h, w, h);

        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpegPath);
        cmd.add("-y");
        cmd.add("-loop");
        cmd.add("1");
        cmd.add("-i");
        cmd.add(imageFile.getAbsolutePath());

        if (audioFile != null && audioFile.exists()) {
            cmd.add("-i");
            cmd.add(audioFile.getAbsolutePath());
            cmd.add("-c:v");
            cmd.add("libx264");
            cmd.add("-tune");
            cmd.add("stillimage");
            cmd.add("-preset");
            cmd.add("medium");
            cmd.add("-crf");
            cmd.add("18");
            cmd.add("-c:a");
            cmd.add("aac");
            cmd.add("-b:a");
            cmd.add("192k");
            cmd.add("-pix_fmt");
            cmd.add("yuv420p");
            cmd.add("-shortest");
            cmd.add("-vf");
            cmd.add(scaleFilter);
        } else {
            cmd.add("-t");
            cmd.add(String.valueOf(durationSeconds > 0 ? durationSeconds : 5.0f));
            cmd.add("-c:v");
            cmd.add("libx264");
            cmd.add("-tune");
            cmd.add("stillimage");
            cmd.add("-preset");
            cmd.add("medium");
            cmd.add("-crf");
            cmd.add("18");
            cmd.add("-pix_fmt");
            cmd.add("yuv420p");
            cmd.add("-r");
            cmd.add(String.valueOf(fps > 0 ? fps : 24.0f));
            cmd.add("-vf");
            cmd.add(scaleFilter);
        }
        cmd.add(outputFile.getAbsolutePath());

        logger.info("🎬 [MediaTranscoding] Converting image to video via FFmpeg: {}", String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = startProcess(pb);

        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                logger.debug("   [FFmpeg Img2Vid] {}", line);
            }
        }
        int exit = p.waitFor();
        if (exit != 0 || !outputFile.exists() || outputFile.length() < 100) {
            throw new IOException("FFmpeg image-to-video conversion failed with exit code " + exit);
        }
        return outputFile;
    }
}
