package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.IVideoLipSyncService;
import de.tki.comfyuicompanion.service.pipeline.ComfyJobPollingService;
import de.tki.comfyuicompanion.service.pipeline.ComfyNodeInspectionClient;
import de.tki.comfyuicompanion.service.pipeline.MediaTranscodingService;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Enterprise implementation of {@link IVideoLipSyncService} managing audio-driven
 * head animation, ComfyUI node graph generation (LivePortrait, SadTalker, Wav2Lip),
 * and fallback audio-visual transcoding.
 */
@Service
public class VideoLipSyncService implements IVideoLipSyncService {

    private static final Logger logger = LoggerFactory.getLogger(VideoLipSyncService.class);

    private final ConfigService configService;
    private final ComfyNodeInspectionClient inspectionClient;
    private final ComfyJobPollingService jobPollingService;
    private final MediaTranscodingService mediaTranscodingService;
    private final ProcessTracker processTracker;

    public VideoLipSyncService(ConfigService configService) {
        this(configService, new ComfyNodeInspectionClient(), new ComfyJobPollingService(),
                new MediaTranscodingService(configService), null);
    }

    @Autowired
    public VideoLipSyncService(
            ConfigService configService,
            @Autowired(required = false) ComfyNodeInspectionClient inspectionClient,
            @Autowired(required = false) ComfyJobPollingService jobPollingService,
            @Autowired(required = false) MediaTranscodingService mediaTranscodingService,
            @Autowired(required = false) ProcessTracker processTracker) {
        this.configService = configService;
        this.inspectionClient = inspectionClient != null ? inspectionClient : new ComfyNodeInspectionClient();
        this.jobPollingService = jobPollingService != null ? jobPollingService : new ComfyJobPollingService();
        this.mediaTranscodingService = mediaTranscodingService != null ? mediaTranscodingService : new MediaTranscodingService(configService);
        this.processTracker = processTracker;
    }

    @Override
    public boolean isLipSyncAvailable(String serverUrl) {
        if (serverUrl == null || serverUrl.isBlank()) {
            serverUrl = getComfyUrl();
        }
        try {
            if (inspectionClient.isNodeClassAvailable(serverUrl, "LivePortraitCropper")
                    || inspectionClient.isNodeClassAvailable(serverUrl, "LivePortraitAudioDriven")
                    || inspectionClient.isNodeClassAvailable(serverUrl, "LivePortraitPredict")
                    || inspectionClient.isNodeClassAvailable(serverUrl, "SadTalker")
                    || inspectionClient.isNodeClassAvailable(serverUrl, "Wav2Lip")) {
                return true;
            }
        } catch (Exception e) {
            logger.debug("ComfyUI node inspection unavailable: {}", e.getMessage());
        }
        return false;
    }

    @Override
    public List<String> getAvailableLipSyncEngines(String serverUrl) {
        if (serverUrl == null || serverUrl.isBlank()) {
            serverUrl = getComfyUrl();
        }
        List<String> engines = new ArrayList<>();
        engines.add("Auto");
        if (inspectionClient.isNodeClassAvailable(serverUrl, "LivePortraitCropper")
                || inspectionClient.isNodeClassAvailable(serverUrl, "LivePortraitPredict")
                || inspectionClient.isNodeClassAvailable(serverUrl, "LivePortraitAudioDriven")) {
            engines.add("LivePortrait");
        }
        if (inspectionClient.isNodeClassAvailable(serverUrl, "SadTalker")) {
            engines.add("SadTalker");
        }
        if (inspectionClient.isNodeClassAvailable(serverUrl, "Wav2Lip")) {
            engines.add("Wav2Lip");
        }
        if (engines.size() == 1) {
            // Provide full list of supported engines so user can select preferred target
            engines.addAll(List.of("LivePortrait", "SadTalker", "Wav2Lip"));
        }
        return engines;
    }

    @Override
    public CompletableFuture<File> applyLipSync(File mediaFile, File audioFile, File outputFile, Scene scene) {
        String configuredEngine = configService != null ? configService.getLipSyncEngine() : "Auto";
        return applyLipSync(mediaFile, audioFile, outputFile, scene, configuredEngine);
    }

    @Override
    public CompletableFuture<File> applyLipSync(File mediaFile, File audioFile, File outputFile, Scene scene, String engineOverride) {
        return CompletableFuture.supplyAsync(() -> {
            if (mediaFile == null || !mediaFile.exists()) {
                throw new IllegalArgumentException("Source media file for lip sync does not exist.");
            }
            if (audioFile == null || !audioFile.exists()) {
                throw new IllegalArgumentException("Source audio narration file for lip sync does not exist.");
            }

            String serverUrl = getComfyUrl();
            String effectiveEngine = resolveEffectiveEngine(engineOverride, serverUrl);
            logger.info("🎤 [VideoLipSyncService] Starting audio lip-sync using engine '{}' for media: {}",
                    effectiveEngine, mediaFile.getName());

            boolean comfyNodeAvailable = hasNodeForEngine(effectiveEngine, serverUrl);

            if (comfyNodeAvailable) {
                try {
                    return executeComfyLipSync(mediaFile, audioFile, outputFile, scene, effectiveEngine, serverUrl);
                } catch (Exception e) {
                    logger.warn("⚠️ [VideoLipSyncService] ComfyUI lip-sync execution failed ({}), falling back to FFmpeg sync: {}",
                            effectiveEngine, e.getMessage());
                }
            } else {
                logger.info("💡 [VideoLipSyncService] ComfyUI node for '{}' not installed. Applying high-quality fallback audio synchronization.",
                        effectiveEngine);
            }

            return executeFallbackSync(mediaFile, audioFile, outputFile, scene);
        });
    }

    private File executeComfyLipSync(File mediaFile, File audioFile, File outputFile, Scene scene,
                                    String engine, String serverUrl) throws Exception {
        String inputDir = configService != null ? configService.getResolvedInputDetailDir() : "input";
        File targetMedia = new File(inputDir, mediaFile.getName());
        File targetAudio = new File(inputDir, audioFile.getName());

        if (!targetMedia.exists() || targetMedia.length() != mediaFile.length()) {
            targetMedia.getParentFile().mkdirs();
            Files.copy(mediaFile.toPath(), targetMedia.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        if (!targetAudio.exists() || targetAudio.length() != audioFile.length()) {
            targetAudio.getParentFile().mkdirs();
            Files.copy(audioFile.toPath(), targetAudio.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        JSONObject promptPayload = buildLipSyncPrompt(engine, mediaFile.getName(), audioFile.getName(), scene);
        String promptId = jobPollingService.submitPrompt(serverUrl, promptPayload);
        if (promptId == null || promptId.isBlank()) {
            throw new IOException("Failed to submit lip-sync prompt to ComfyUI server.");
        }

        ComfyJobPollingService.ComfyOutputRef outputRef = jobPollingService.pollHistoryForOutput(serverUrl, promptId);
        String comfyOutputDir = configService != null ? configService.getResolvedOutputDir() : "output";
        File generatedFile = jobPollingService.resolveOutputFile(comfyOutputDir, outputRef, scene != null ? scene.getSceneId() : "lipsync");

        if (generatedFile != null && generatedFile.exists()) {
            outputFile.getParentFile().mkdirs();
            Files.copy(generatedFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.info("✅ [VideoLipSyncService] Lip-sync video successfully generated at: {}", outputFile.getAbsolutePath());
            return outputFile;
        }

        throw new IOException("ComfyUI finished lip-sync job but output video was not found.");
    }

    private File executeFallbackSync(File mediaFile, File audioFile, File outputFile, Scene scene) {
        try {
            boolean isImage = mediaFile.getName().toLowerCase(Locale.ROOT).matches(".*\\.(png|jpg|jpeg|webp)$");
            float duration = 5.0f;
            if (scene != null && scene.getEndFrame() > scene.getStartFrame()) {
                duration = Math.max(1.0f, (scene.getEndFrame() - scene.getStartFrame()) / 24.0f);
            }
            float fps = 24.0f;
            int width = scene != null && scene.getWidth() > 0 ? scene.getWidth() : 1920;
            int height = scene != null && scene.getHeight() > 0 ? scene.getHeight() : 1080;

            if (isImage) {
                return mediaTranscodingService.convertImageToVideo(mediaFile, audioFile, duration, fps, outputFile, width, height);
            } else {
                muxAudioDirectly(mediaFile, audioFile, outputFile);
                return outputFile;
            }
        } catch (Exception e) {
            throw new RuntimeException("Fallback audio lip-sync alignment failed: " + e.getMessage(), e);
        }
    }

    private void muxAudioDirectly(File videoFile, File audioFile, File outputFile) throws IOException, InterruptedException {
        String ffmpeg = configService != null ? configService.getFfmpegPath() : "ffmpeg";
        List<String> cmd = List.of(
                ffmpeg, "-y",
                "-i", videoFile.getAbsolutePath(),
                "-i", audioFile.getAbsolutePath(),
                "-c:v", "copy",
                "-c:a", "aac",
                "-shortest",
                outputFile.getAbsolutePath()
        );
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        Process p = processTracker != null ? processTracker.start(pb) : pb.start();

        Thread errThread = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                while (r.readLine() != null) { /* discard */ }
            } catch (IOException ignored) {}
        });
        errThread.start();

        int exit = p.waitFor();
        errThread.join();
        if (exit != 0) {
            throw new IOException("FFmpeg audio muxing exited with code " + exit);
        }
    }

    @Override
    public JSONObject buildLipSyncPrompt(String engine, String inputMediaName, String audioFileName, Scene scene) {
        String normEngine = (engine != null ? engine : "Auto").toLowerCase(Locale.ROOT);
        if (normEngine.contains("sadtalker")) {
            return buildSadTalkerPrompt(inputMediaName, audioFileName);
        } else if (normEngine.contains("wav2lip")) {
            return buildWav2LipPrompt(inputMediaName, audioFileName);
        } else {
            return buildLivePortraitPrompt(inputMediaName, audioFileName);
        }
    }

    private JSONObject buildLivePortraitPrompt(String mediaName, String audioName) {
        JSONObject prompt = new JSONObject();
        prompt.put("1", new JSONObject()
                .put("class_type", "LoadImage")
                .put("inputs", new JSONObject().put("image", mediaName)));
        prompt.put("2", new JSONObject()
                .put("class_type", "LoadAudio")
                .put("inputs", new JSONObject().put("audio", audioName)));
        prompt.put("3", new JSONObject()
                .put("class_type", "LivePortraitLoadCropperModel")
                .put("inputs", new JSONObject()));
        prompt.put("4", new JSONObject()
                .put("class_type", "LivePortraitCropper")
                .put("inputs", new JSONObject()
                        .put("pipeline", new org.json.JSONArray(List.of("3", 0)))
                        .put("source_image", new org.json.JSONArray(List.of("1", 0)))));
        prompt.put("5", new JSONObject()
                .put("class_type", "LivePortraitAudioDriven")
                .put("inputs", new JSONObject()
                        .put("source", new org.json.JSONArray(List.of("4", 0)))
                        .put("audio", new org.json.JSONArray(List.of("2", 0)))
                        .put("relative", true)
                        .put("expression_friendly", 1.0)));
        prompt.put("6", new JSONObject()
                .put("class_type", "VHS_VideoCombine")
                .put("inputs", new JSONObject()
                        .put("images", new org.json.JSONArray(List.of("5", 0)))
                        .put("audio", new org.json.JSONArray(List.of("2", 0)))
                        .put("frame_rate", 24)
                        .put("format", "video/h264-mp4")
                        .put("filename_prefix", "lipsync_liveportrait")));
        return prompt;
    }

    private JSONObject buildSadTalkerPrompt(String mediaName, String audioName) {
        JSONObject prompt = new JSONObject();
        prompt.put("1", new JSONObject()
                .put("class_type", "LoadImage")
                .put("inputs", new JSONObject().put("image", mediaName)));
        prompt.put("2", new JSONObject()
                .put("class_type", "LoadAudio")
                .put("inputs", new JSONObject().put("audio", audioName)));
        prompt.put("3", new JSONObject()
                .put("class_type", "SadTalkerLoader")
                .put("inputs", new JSONObject().put("checkpoint", "SadTalker_V0.0.2_256.safetensors")));
        prompt.put("4", new JSONObject()
                .put("class_type", "SadTalker")
                .put("inputs", new JSONObject()
                        .put("source_image", new org.json.JSONArray(List.of("1", 0)))
                        .put("driven_audio", new org.json.JSONArray(List.of("2", 0)))
                        .put("sadtalker_model", new org.json.JSONArray(List.of("3", 0)))
                        .put("pose_style", 0)
                        .put("batch_size", 2)));
        prompt.put("5", new JSONObject()
                .put("class_type", "VHS_VideoCombine")
                .put("inputs", new JSONObject()
                        .put("images", new org.json.JSONArray(List.of("4", 0)))
                        .put("audio", new org.json.JSONArray(List.of("2", 0)))
                        .put("frame_rate", 24)
                        .put("format", "video/h264-mp4")
                        .put("filename_prefix", "lipsync_sadtalker")));
        return prompt;
    }

    private JSONObject buildWav2LipPrompt(String mediaName, String audioName) {
        JSONObject prompt = new JSONObject();
        prompt.put("1", new JSONObject()
                .put("class_type", "LoadImage")
                .put("inputs", new JSONObject().put("image", mediaName)));
        prompt.put("2", new JSONObject()
                .put("class_type", "LoadAudio")
                .put("inputs", new JSONObject().put("audio", audioName)));
        prompt.put("3", new JSONObject()
                .put("class_type", "Wav2LipPreprocessor")
                .put("inputs", new JSONObject().put("face_detection", "retinaface")));
        prompt.put("4", new JSONObject()
                .put("class_type", "Wav2Lip")
                .put("inputs", new JSONObject()
                        .put("face", new org.json.JSONArray(List.of("1", 0)))
                        .put("audio", new org.json.JSONArray(List.of("2", 0)))
                        .put("model", new org.json.JSONArray(List.of("3", 0)))
                        .put("pads", "0,10,0,0")));
        prompt.put("5", new JSONObject()
                .put("class_type", "VHS_VideoCombine")
                .put("inputs", new JSONObject()
                        .put("images", new org.json.JSONArray(List.of("4", 0)))
                        .put("audio", new org.json.JSONArray(List.of("2", 0)))
                        .put("frame_rate", 24)
                        .put("format", "video/h264-mp4")
                        .put("filename_prefix", "lipsync_wav2lip")));
        return prompt;
    }

    private String resolveEffectiveEngine(String engine, String serverUrl) {
        if (engine != null && !engine.equalsIgnoreCase("Auto")) {
            return engine;
        }
        if (inspectionClient.isNodeClassAvailable(serverUrl, "LivePortraitCropper")
                || inspectionClient.isNodeClassAvailable(serverUrl, "LivePortraitPredict")) {
            return "LivePortrait";
        }
        if (inspectionClient.isNodeClassAvailable(serverUrl, "SadTalker")) {
            return "SadTalker";
        }
        if (inspectionClient.isNodeClassAvailable(serverUrl, "Wav2Lip")) {
            return "Wav2Lip";
        }
        return "LivePortrait";
    }

    private boolean hasNodeForEngine(String engine, String serverUrl) {
        String norm = engine.toLowerCase(Locale.ROOT);
        if (norm.contains("sadtalker")) {
            return inspectionClient.isNodeClassAvailable(serverUrl, "SadTalker");
        } else if (norm.contains("wav2lip")) {
            return inspectionClient.isNodeClassAvailable(serverUrl, "Wav2Lip");
        } else {
            return inspectionClient.isNodeClassAvailable(serverUrl, "LivePortraitCropper")
                    || inspectionClient.isNodeClassAvailable(serverUrl, "LivePortraitPredict")
                    || inspectionClient.isNodeClassAvailable(serverUrl, "LivePortraitAudioDriven");
        }
    }

    private String getComfyUrl() {
        return configService != null ? configService.getComfyUIUrl() : "http://127.0.0.1:8188";
    }
}
