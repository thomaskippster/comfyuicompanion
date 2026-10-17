package de.tki.comfyuicompanion.service.pipeline;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.ProcessTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Component handling media post-processing, audio muxing via FFmpeg,
 * and fallback video simulation for scene pipeline execution.
 */
@Component
public class SceneMediaPostProcessor {
    private static final Logger logger = LoggerFactory.getLogger(SceneMediaPostProcessor.class);

    private final ConfigService configService;
    private final SceneSimulationService simulationService;
    private final MediaTranscodingService mediaTranscodingService;
    private final ProcessTracker defaultProcessTracker;
    private final de.tki.comfyuicompanion.service.IVideoLipSyncService lipSyncService;

    public SceneMediaPostProcessor(
            ConfigService configService,
            SceneSimulationService simulationService,
            MediaTranscodingService mediaTranscodingService,
            ProcessTracker defaultProcessTracker) {
        this(configService, simulationService, mediaTranscodingService, defaultProcessTracker, null);
    }

    @Autowired
    public SceneMediaPostProcessor(
            ConfigService configService,
            SceneSimulationService simulationService,
            MediaTranscodingService mediaTranscodingService,
            @Autowired(required = false) ProcessTracker defaultProcessTracker,
            @Autowired(required = false) de.tki.comfyuicompanion.service.IVideoLipSyncService lipSyncService) {
        this.configService = configService;
        this.simulationService = simulationService != null ? simulationService : new SceneSimulationService(defaultProcessTracker);
        this.mediaTranscodingService = mediaTranscodingService != null ? mediaTranscodingService : new MediaTranscodingService(configService, defaultProcessTracker);
        this.defaultProcessTracker = defaultProcessTracker;
        this.lipSyncService = lipSyncService != null ? lipSyncService : new de.tki.comfyuicompanion.service.impl.VideoLipSyncService(configService);
    }

    /**
     * Post-processes raw ComfyUI image or video output by attaching audio or transcoding.
     *
     * @param rawOutputFile generated output file
     * @param scene scene metadata
     * @param finalVideoFile destination video file
     * @param duration target duration in seconds
     * @param fps target frame rate
     * @param overrideTracker optional process tracker override
     * @throws Exception if processing fails
     */
    public void postProcessMedia(
            File rawOutputFile,
            Scene scene,
            File finalVideoFile,
            float duration,
            float fps,
            ProcessTracker overrideTracker) throws Exception {

        boolean isImage = rawOutputFile.getName().toLowerCase().matches(".*\\.(png|jpg|jpeg|webp)$");
        String audioPath = scene.getAudioPath();
        File audioFile = (audioPath != null && !audioPath.trim().isEmpty()) ? new File(audioPath) : null;

        boolean isLipSyncEnabled = configService != null && configService.isAudioLipSyncEnabled();
        if (isLipSyncEnabled && audioFile != null && audioFile.exists() && lipSyncService != null) {
            String engine = configService.getLipSyncEngine();
            logger.info("🎤 [SceneMediaPostProcessor] Audio Lip-Sync enabled. Applying head animation via '{}'...", engine);
            try {
                File syncedFile = lipSyncService.applyLipSync(rawOutputFile, audioFile, finalVideoFile, scene).join();
                if (syncedFile != null && syncedFile.exists()) {
                    logger.info("✅ [SceneMediaPostProcessor] Audio Lip-Sync complete: {}", syncedFile.getAbsolutePath());
                    if (rawOutputFile.exists() && !rawOutputFile.getAbsolutePath().equals(finalVideoFile.getAbsolutePath())) {
                        rawOutputFile.delete();
                    }
                    return;
                }
            } catch (Exception e) {
                logger.warn("⚠️ [SceneMediaPostProcessor] Lip-sync synthesis encountered an error, falling back to standard audio post-processing: {}", e.getMessage());
            }
        }

        if (isImage) {
            logger.info("🖼️ [SceneMediaPostProcessor] Raw output is an image. Converting to MP4 scene video...");
            mediaTranscodingService.convertImageToVideo(rawOutputFile, audioFile, duration, fps, finalVideoFile, scene.getWidth(), scene.getHeight());
        } else if (audioFile != null && audioFile.exists()) {
            muxAudio(rawOutputFile, audioFile, finalVideoFile, overrideTracker);
            if (rawOutputFile.exists() && !rawOutputFile.getAbsolutePath().equals(finalVideoFile.getAbsolutePath())) {
                rawOutputFile.delete();
            }
        } else {
            logger.info("ℹ️ [SceneMediaPostProcessor] No audio file specified. Copying raw video to: {}", finalVideoFile.getAbsolutePath());
            Files.copy(rawOutputFile.toPath(), finalVideoFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Muxes audio with video using FFmpeg.
     *
     * @param rawOutputFile input video file
     * @param audioFile input audio file
     * @param finalVideoFile output video file
     * @param overrideTracker process tracker
     * @throws IOException on I/O error
     * @throws InterruptedException if interrupted
     */
    public void muxAudio(File rawOutputFile, File audioFile, File finalVideoFile, ProcessTracker overrideTracker) throws IOException, InterruptedException {
        String ffmpegPath = configService.getFfmpegPath();
        List<String> cmd = List.of(
                ffmpegPath,
                "-y",
                "-i", rawOutputFile.getAbsolutePath(),
                "-i", audioFile.getAbsolutePath(),
                "-c:v", "copy",
                "-c:a", "aac",
                "-shortest",
                finalVideoFile.getAbsolutePath()
        );

        logger.info("🎬 [SceneMediaPostProcessor] Running FFmpeg audio muxing command: {}", String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        Process process = startProcess(pb, overrideTracker);

        Thread errorReaderThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("[FFmpeg-Mux] {}", line);
                }
            } catch (IOException e) {
                logger.error("Error reading FFmpeg error stream: {}", e.getMessage());
            }
        });
        errorReaderThread.start();

        int exitCode = process.waitFor();
        errorReaderThread.join();

        if (exitCode != 0) {
            throw new IOException("FFmpeg audio muxing failed with exit code: " + exitCode);
        }
    }

    /**
     * Generates a simulation fallback video when ComfyUI generation fails.
     *
     * @param scene scene definition
     * @param finalVideoFile destination file
     * @return generated fallback video file
     * @throws Exception if simulation fails
     */
    public File generateSimulationFallback(Scene scene, File finalVideoFile) throws Exception {
        File fallbackVideo = simulationService.generateSimulatedVideo(scene, configService.getFfmpegPath());
        Files.copy(fallbackVideo.toPath(), finalVideoFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        fallbackVideo.delete();
        scene.setVideoPath(finalVideoFile.getAbsolutePath());
        return finalVideoFile;
    }

    private Process startProcess(ProcessBuilder pb, ProcessTracker tracker) throws IOException {
        ProcessTracker effectiveTracker = tracker != null ? tracker : this.defaultProcessTracker;
        return effectiveTracker != null ? effectiveTracker.start(pb) : pb.start();
    }
}
