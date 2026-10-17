package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.IVideoFrameChainingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Enterprise implementation of {@link IVideoFrameChainingService} utilizing FFmpeg
 * to extract terminal video frames for continuous shot chaining across sequential scenes.
 */
@Service
public class VideoFrameChainingService implements IVideoFrameChainingService {

    private static final Logger logger = LoggerFactory.getLogger(VideoFrameChainingService.class);

    private final ConfigService configService;
    private final ProcessTracker processTracker;

    /**
     * Default constructor with configuration only.
     *
     * @param configService the configuration service
     */
    public VideoFrameChainingService(ConfigService configService) {
        this(configService, null);
    }

    /**
     * Autowired constructor injecting configuration and process tracking.
     *
     * @param configService  the configuration service
     * @param processTracker optional process tracker for monitoring spawned processes
     */
    @Autowired
    public VideoFrameChainingService(
            ConfigService configService,
            @Autowired(required = false) ProcessTracker processTracker) {
        this.configService = configService;
        this.processTracker = processTracker;
    }

    @Override
    public File extractLastFrame(File videoFile, File targetImageFile) {
        if (videoFile == null || !videoFile.exists() || videoFile.length() < 1024) {
            logger.warn("⚠️ [Frame-Chaining] Source video is invalid or missing: {}", videoFile);
            return null;
        }
        if (targetImageFile == null) {
            return null;
        }

        if (targetImageFile.getParentFile() != null && !targetImageFile.getParentFile().exists()) {
            targetImageFile.getParentFile().mkdirs();
        }

        String ffmpeg = configService != null ? configService.getFfmpegPath() : "ffmpeg";

        // Strategy 1: Accurate end-of-file seeking (-sseof -1)
        List<String> cmd1 = List.of(
                ffmpeg,
                "-y",
                "-sseof", "-1",
                "-i", videoFile.getAbsolutePath(),
                "-update", "1",
                "-q:v", "1",
                targetImageFile.getAbsolutePath()
        );

        boolean success = runFfmpegCommand(cmd1, "Last-frame extraction (-sseof)");
        if (success && targetImageFile.exists() && targetImageFile.length() > 500) {
            logger.info("🎬 [Frame-Chaining] Extracted final frame of '{}' -> '{}' ({} bytes)",
                    videoFile.getName(), targetImageFile.getName(), targetImageFile.length());
            return targetImageFile;
        }

        // Strategy 2: Fallback reverse select filter
        List<String> cmd2 = List.of(
                ffmpeg,
                "-y",
                "-i", videoFile.getAbsolutePath(),
                "-vf", "select=gte(n\\,0)",
                "-vframes", "1",
                "-q:v", "1",
                targetImageFile.getAbsolutePath()
        );

        if (runFfmpegCommand(cmd2, "Fallback frame extraction") && targetImageFile.exists() && targetImageFile.length() > 500) {
            logger.info("🎬 [Frame-Chaining] Extracted frame using fallback selector -> '{}'", targetImageFile.getName());
            return targetImageFile;
        }

        logger.error("❌ [Frame-Chaining] Failed to extract final frame from: {}", videoFile.getAbsolutePath());
        return null;
    }

    @Override
    public String resolveChainedStartImage(List<Scene> scenes, int sceneIndex, String defaultSpeakerImage, File outputDirectory) {
        if (scenes == null || sceneIndex < 0 || sceneIndex >= scenes.size()) {
            return defaultSpeakerImage;
        }

        Scene currentScene = scenes.get(sceneIndex);

        // If this is not the first scene, attempt to chain from the preceding scene's video
        if (sceneIndex > 0) {
            Scene prevScene = scenes.get(sceneIndex - 1);
            String prevVideoPath = prevScene.getVideoPath();
            if (prevVideoPath != null && !prevVideoPath.isBlank()) {
                File prevVideoFile = new File(prevVideoPath);
                if (prevVideoFile.exists() && prevVideoFile.length() > 1024) {
                    File targetFrame = new File(outputDirectory, "chained_frame_from_" + prevScene.getSceneId() + ".png");
                    File extracted = extractLastFrame(prevVideoFile, targetFrame);
                    if (extracted != null && extracted.exists()) {
                        logger.info("🔗 [Continuous Shot] Chained Scene {} to terminal frame of Scene {}.",
                                currentScene.getSceneId(), prevScene.getSceneId());
                        return extracted.getAbsolutePath();
                    }
                }
            }
        }

        // Fallback: Scene's explicit start image or global default speaker image
        if (currentScene.getSpeakerImagePath() != null && !currentScene.getSpeakerImagePath().isBlank()) {
            return currentScene.getSpeakerImagePath();
        }
        return defaultSpeakerImage;
    }

    private boolean runFfmpegCommand(List<String> command, String label) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process proc = processTracker != null ? processTracker.start(pb) : pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Drain buffer
                }
            }
            int exitCode = proc.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            logger.debug("{} command failed: {}", label, e.getMessage());
            return false;
        }
    }
}
