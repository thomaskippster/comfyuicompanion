package de.tki.comfyuicompanion.service.pipeline;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import de.tki.comfyuicompanion.service.PromptBlueprintApiService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.EnvironmentBootstrapperImpl;
import de.tki.comfyuicompanion.service.impl.ProcessTracker;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runner responsible for orchestrating scene video generation workflows.
 * Determines model strategies (native video diffusion, blueprint execution,
 * image-diffusion synthesis fallback, or local FFmpeg simulation), coordinates
 * generation execution and handles media post-processing and artifact retrieval.
 */
@Component
public class ScenePipelineRunner {

    private static final Logger logger = LoggerFactory.getLogger(ScenePipelineRunner.class);
    private static final Set<String> ATTEMPTED_INSTALLS = ConcurrentHashMap.newKeySet();

    private final ConfigService configService;
    private final SceneSimulationService simulationService;
    private final MediaTranscodingService mediaTranscodingService;
    private final VideoWorkflowBuilder workflowBuilder;
    private final ComfyJobPollingService jobPollingService;
    private final ComfyNodeInspectionClient inspectionClient;
    private final EnvironmentBootstrapperImpl bootstrapper;
    private final IComfyLifecycleService lifecycleService;
    private final ProcessTracker processTracker;
    private final WorkflowTransformationService workflowTransformationService;

    /**
     * Minimal constructor for programmatic initialization.
     *
     * @param configService          the system configuration service
     * @param simulationService      the FFmpeg scene simulation service
     * @param mediaTranscodingService the media transcoding service
     * @param workflowBuilder        the workflow generator service
     * @param jobPollingService      the ComfyUI job polling and artifact retrieval service
     * @param inspectionClient       the ComfyUI node inspection client
     */
    public ScenePipelineRunner(
            ConfigService configService,
            SceneSimulationService simulationService,
            MediaTranscodingService mediaTranscodingService,
            VideoWorkflowBuilder workflowBuilder,
            ComfyJobPollingService jobPollingService,
            ComfyNodeInspectionClient inspectionClient) {
        this(configService, simulationService, mediaTranscodingService, workflowBuilder,
                jobPollingService, inspectionClient, null, null, null, null);
    }

    /**
     * Programmatic constructor including lifecycle and bootstrapping dependencies.
     *
     * @param configService          the system configuration service
     * @param simulationService      the FFmpeg scene simulation service
     * @param mediaTranscodingService the media transcoding service
     * @param workflowBuilder        the workflow generator service
     * @param jobPollingService      the ComfyUI job polling and artifact retrieval service
     * @param inspectionClient       the ComfyUI node inspection client
     * @param bootstrapper           the optional environment bootstrapper
     * @param lifecycleService       the optional ComfyUI lifecycle service
     * @param processTracker         the optional process tracker
     */
    public ScenePipelineRunner(
            ConfigService configService,
            SceneSimulationService simulationService,
            MediaTranscodingService mediaTranscodingService,
            VideoWorkflowBuilder workflowBuilder,
            ComfyJobPollingService jobPollingService,
            ComfyNodeInspectionClient inspectionClient,
            EnvironmentBootstrapperImpl bootstrapper,
            IComfyLifecycleService lifecycleService,
            ProcessTracker processTracker) {
        this(configService, simulationService, mediaTranscodingService, workflowBuilder,
                jobPollingService, inspectionClient, bootstrapper, lifecycleService, processTracker, null);
    }

    /**
     * Primary constructor with Spring dependency injection support.
     *
     * @param configService                  the system configuration service
     * @param simulationService              the FFmpeg scene simulation service
     * @param mediaTranscodingService        the media transcoding service
     * @param workflowBuilder                the workflow generator service
     * @param jobPollingService              the ComfyUI job polling and artifact retrieval service
     * @param inspectionClient               the ComfyUI node inspection client
     * @param bootstrapper                   the optional environment bootstrapper
     * @param lifecycleService               the optional ComfyUI lifecycle service
     * @param processTracker                 the optional process tracker
     * @param workflowTransformationService  the workflow transformation service
     */
    @Autowired
    public ScenePipelineRunner(
            ConfigService configService,
            SceneSimulationService simulationService,
            MediaTranscodingService mediaTranscodingService,
            VideoWorkflowBuilder workflowBuilder,
            ComfyJobPollingService jobPollingService,
            ComfyNodeInspectionClient inspectionClient,
            @Autowired(required = false) EnvironmentBootstrapperImpl bootstrapper,
            @Autowired(required = false) @Lazy IComfyLifecycleService lifecycleService,
            @Autowired(required = false) ProcessTracker processTracker,
            @Autowired(required = false) WorkflowTransformationService workflowTransformationService) {
        this.configService = configService;
        this.simulationService = simulationService != null ? simulationService : new SceneSimulationService(processTracker);
        this.mediaTranscodingService = mediaTranscodingService != null ? mediaTranscodingService : new MediaTranscodingService(configService, processTracker);
        this.workflowBuilder = workflowBuilder;
        this.jobPollingService = jobPollingService != null ? jobPollingService : new ComfyJobPollingService();
        this.inspectionClient = inspectionClient != null ? inspectionClient : new ComfyNodeInspectionClient();
        this.bootstrapper = bootstrapper;
        this.lifecycleService = lifecycleService;
        this.processTracker = processTracker;
        this.workflowTransformationService = workflowTransformationService != null ? workflowTransformationService : new WorkflowTransformationService(null);
    }

    /**
     * Executes scene generation in standard or strict mode asynchronously.
     *
     * @param scene      the scene domain object containing generation parameters
     * @param strictMode true to fail fast without fallback simulation; false to allow fallback
     * @return a CompletableFuture resolving to the generated video file
     */
    public CompletableFuture<File> executeScene(Scene scene, boolean strictMode) {
        return executeScene(scene, strictMode, this.lifecycleService, this.processTracker);
    }

    /**
     * Executes scene generation with explicit lifecycle service and process tracker overrides.
     *
     * @param scene             the scene domain object containing generation parameters
     * @param strictMode        true to fail fast without fallback simulation; false to allow fallback
     * @param overrideLifecycle optional lifecycle service override
     * @param overrideTracker   optional process tracker override
     * @return a CompletableFuture resolving to the generated video file
     */
    public CompletableFuture<File> executeScene(
            Scene scene,
            boolean strictMode,
            IComfyLifecycleService overrideLifecycle,
            ProcessTracker overrideTracker) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                simulationService.validateFfmpeg(configService.getFfmpegPath());

                IComfyLifecycleService effectiveLifecycle = overrideLifecycle != null ? overrideLifecycle : this.lifecycleService;
                ensureComfyUiStarted(effectiveLifecycle);

                String serverUrl = configService.getComfyUIUrl();
                ensureVideoHelperSuite(serverUrl, effectiveLifecycle);

                long seed = Math.abs(new Random().nextLong());
                String filenamePrefix = "videoarchitect_" + scene.getSceneId();

                String speakerImage = uploadSpeakerImageIfPresent(serverUrl, scene);

                List<String> availableUnets = inspectionClient.fetchObjectInfoOptions(serverUrl, "UNETLoader", "unet_name");
                List<String> availableCheckpoints = inspectionClient.fetchAvailableCheckpoints(serverUrl);
                List<String> availableClips = inspectionClient.fetchObjectInfoOptions(serverUrl, "CLIPLoader", "clip_name");
                List<String> availableVaes = inspectionClient.fetchObjectInfoOptions(serverUrl, "VAELoader", "vae_name");

                boolean wanNodesAvailable = inspectionClient.isNodeClassAvailable(serverUrl, "WanImageToVideo");
                boolean hasWanModels = workflowBuilder.isWanSetupComplete(availableUnets, availableClips, availableVaes);

                JSONObject workflowJson = determineWorkflow(
                        serverUrl, scene, speakerImage, seed, filenamePrefix,
                        availableUnets, availableCheckpoints, availableClips, availableVaes,
                        wanNodesAvailable, hasWanModels);

                workflowBuilder.sanitizeWorkflow(workflowJson, serverUrl);

                ComfyJobPollingService.ComfyOutputRef outputRef = null;
                boolean isWanAttempt = (wanNodesAvailable && hasWanModels);

                try {
                    String promptId = jobPollingService.submitPrompt(serverUrl, workflowJson);
                    logger.info("🚀 [ScenePipelineRunner] Submitted job. Prompt ID: {}", promptId);
                    outputRef = jobPollingService.pollHistoryForOutput(serverUrl, promptId);
                } catch (Exception ex) {
                    if (isWanAttempt) {
                        logger.warn("⚠️ [ScenePipelineRunner] Video model execution failed ({}); falling back to image diffusion model synthesis...", ex.getMessage());
                        JSONObject fallbackWorkflow = workflowBuilder.generateSceneImageWorkflow(
                                serverUrl, scene, speakerImage, seed, filenamePrefix,
                                availableUnets, availableCheckpoints, availableClips, availableVaes);
                        workflowBuilder.sanitizeWorkflow(fallbackWorkflow, serverUrl);
                        String fallbackPromptId = jobPollingService.submitPrompt(serverUrl, fallbackWorkflow);
                        logger.info("🚀 [ScenePipelineRunner] Submitted fallback job. Prompt ID: {}", fallbackPromptId);
                        outputRef = jobPollingService.pollHistoryForOutput(serverUrl, fallbackPromptId);
                    } else {
                        throw ex;
                    }
                }

                String finishedFilename = outputRef != null ? outputRef.filename() : null;
                logger.info("✅ [ScenePipelineRunner] Job finished. Filename: {}{}",
                        finishedFilename,
                        (outputRef != null && !outputRef.subfolder().isEmpty() ? " (subfolder: " + outputRef.subfolder() + ")" : ""));

                String comfyOutputPath = configService.getResolvedOutputDir();
                if (comfyOutputPath == null || comfyOutputPath.trim().isEmpty()) {
                    comfyOutputPath = new File("output").getAbsolutePath();
                }

                File rawOutputFile = jobPollingService.resolveOutputFile(comfyOutputPath, outputRef, scene.getSceneId());
                if (rawOutputFile == null && finishedFilename != null && !finishedFilename.trim().isEmpty()) {
                    rawOutputFile = jobPollingService.downloadOutput(serverUrl, outputRef, scene.getSceneId());
                }

                File finalVideoFile = new File(new File("").getAbsoluteFile(), "scene_" + scene.getSceneId() + "_final.mp4");

                float duration = 5.0f;
                if (scene.getEndFrame() > scene.getStartFrame()) {
                    duration = Math.max(1.0f, (scene.getEndFrame() - scene.getStartFrame()) / 24.0f);
                }
                float fps = 24.0f;

                if (rawOutputFile != null && rawOutputFile.exists()) {
                    postProcessMedia(rawOutputFile, scene, finalVideoFile, duration, fps, overrideTracker);
                    scene.setVideoPath(finalVideoFile.getAbsolutePath());
                    return finalVideoFile;
                } else {
                    if (strictMode) {
                        throw new RuntimeException("Strict mode enabled: ComfyUI finished generation, but no output video or image file was found.");
                    }
                    return generateSimulationFallback(scene, finalVideoFile);
                }

            } catch (Exception e) {
                if (strictMode) {
                    logger.error("🔴 [ScenePipelineRunner] Failed generating scene via ComfyUI (Strict Mode): {}", e.getMessage());
                    throw new RuntimeException("Strict mode generation failed: " + e.getMessage(), e);
                }
                logger.error("🔴 [ScenePipelineRunner] Failed generating scene: {}. Generating simulated fallback video.", e.getMessage());
                try {
                    File finalVideoFile = new File(new File("").getAbsoluteFile(), "scene_" + scene.getSceneId() + "_final.mp4");
                    return generateSimulationFallback(scene, finalVideoFile);
                } catch (Exception fe) {
                    throw new RuntimeException("Failed generating scene and fallback also failed: " + fe.getMessage(), fe);
                }
            }
        });
    }

    /**
     * Determines the appropriate workflow graph to execute based on available models and scene specifications.
     *
     * @param serverUrl          the ComfyUI server address
     * @param scene              the scene domain model
     * @param speakerImage       uploaded speaker image reference name, if present
     * @param seed               the generation seed
     * @param filenamePrefix     prefix for generated files
     * @param availableUnets     available diffusion models
     * @param availableCheckpoints available full checkpoints
     * @param availableClips     available CLIP text encoders
     * @param availableVaes      available VAE decoders
     * @param wanNodesAvailable  whether Wan native nodes are present
     * @param hasWanModels       whether Wan weights are found
     * @return the assembled workflow JSON
     */
    public JSONObject determineWorkflow(
            String serverUrl,
            Scene scene,
            String speakerImage,
            long seed,
            String filenamePrefix,
            List<String> availableUnets,
            List<String> availableCheckpoints,
            List<String> availableClips,
            List<String> availableVaes,
            boolean wanNodesAvailable,
            boolean hasWanModels) throws Exception {

        JSONObject workflowJson = null;
        if (wanNodesAvailable && hasWanModels) {
            logger.info("🎬 [ScenePipelineRunner] Generating Wan 2.2 native video workflow...");
            workflowJson = workflowBuilder.generateWanWorkflowJson(serverUrl, scene, speakerImage, seed, filenamePrefix);
        } else if (speakerImage != null && !speakerImage.trim().isEmpty() && hasWanModels) {
            JSONObject blueprintJson = loadBlueprintWorkflow("Text to Video (Wan 2.2).json");
            if (blueprintJson != null) {
                logger.info("📄 [ScenePipelineRunner] Using Wan 2.2 blueprint workflow with start image.");
                injectPrompt(blueprintJson, scene.getPrompt());
                injectParamsIntoBlueprint(blueprintJson, seed, filenamePrefix, scene);
                injectSpeakerImage(blueprintJson, speakerImage);
                workflowJson = blueprintJson;
            }
        }

        if (workflowJson == null) {
            logger.info("🖼️ [ScenePipelineRunner] Dedicated video diffusion models not found; generating scene visuals via available image model...");
            workflowJson = workflowBuilder.generateSceneImageWorkflow(serverUrl, scene, speakerImage, seed, filenamePrefix, availableUnets, availableCheckpoints, availableClips, availableVaes);
        }

        return workflowJson;
    }

    private void ensureComfyUiStarted(IComfyLifecycleService lifecycle) {
        if (lifecycle != null && !lifecycle.isHealthy()) {
            logger.info("🔄 [ScenePipelineRunner] ComfyUI server is offline. Attempting auto-start...");
            lifecycle.start();

            int maxWaitSeconds = 90;
            boolean started = false;
            for (int i = 0; i < maxWaitSeconds; i++) {
                if (lifecycle.isHealthy()) {
                    started = true;
                    break;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Auto-start interrupted", ie);
                }
            }
            if (!started) {
                throw new RuntimeException("Failed to auto-start ComfyUI within " + maxWaitSeconds + " seconds.");
            }
            logger.info("✅ [ScenePipelineRunner] ComfyUI successfully started and healthy.");
        }
    }

    private void ensureVideoHelperSuite(String serverUrl, IComfyLifecycleService lifecycle) {
        if (lifecycle != null && lifecycle.isHealthy() && bootstrapper != null) {
            String comfyPath = configService.getComfyUIPath();
            boolean folderExists = false;
            if (comfyPath != null && !comfyPath.trim().isEmpty()) {
                File videoHelperDir = new File(new File(comfyPath, "custom_nodes"), "ComfyUI-Video-Helper-Suite");
                if (videoHelperDir.exists() && videoHelperDir.isDirectory()) {
                    folderExists = true;
                }
            }

            boolean nodeAvailable = inspectionClient.isNodeClassAvailable(serverUrl, "VHS_VideoCombine");
            if (!nodeAvailable) {
                if (ATTEMPTED_INSTALLS.contains("VHS_VideoCombine")) {
                    logger.info("⚠️ [ScenePipelineRunner] ComfyUI-Video-Helper-Suite installation already attempted. Skipping.");
                } else {
                    ATTEMPTED_INSTALLS.add("VHS_VideoCombine");
                    if (!folderExists) {
                        logger.info("⚠️ [ScenePipelineRunner] VHS_VideoCombine is missing. Installing ComfyUI-Video-Helper-Suite...");
                        lifecycle.stop();

                        String pythonPath = configService.getPythonPath();
                        if (comfyPath != null && !comfyPath.trim().isEmpty()) {
                            Path comfyDir = Paths.get(comfyPath);
                            Path pythonExe = (pythonPath != null && !pythonPath.trim().isEmpty()) ? Paths.get(pythonPath) : null;
                            bootstrapper.ensureVideoHelperSuiteInstalled(comfyDir, pythonExe, msg -> logger.info("[Bootstrap] {}", msg));
                        }
                        lifecycle.start();
                    } else {
                        logger.info("🔄 [ScenePipelineRunner] VHS_VideoCombine not active but folder exists. Restarting ComfyUI...");
                        lifecycle.restart();
                    }

                    int maxWaitSeconds = 90;
                    boolean started = false;
                    for (int i = 0; i < maxWaitSeconds; i++) {
                        if (lifecycle.isHealthy()) {
                            started = true;
                            break;
                        }
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Restart interrupted", ie);
                        }
                    }
                    if (!started) {
                        throw new RuntimeException("Failed to restart ComfyUI within " + maxWaitSeconds + " seconds.");
                    }
                    logger.info("✅ [ScenePipelineRunner] ComfyUI successfully restarted and healthy.");
                }
            }
        }
    }

    private String uploadSpeakerImageIfPresent(String serverUrl, Scene scene) throws IOException, InterruptedException {
        String speakerPath = (scene != null && scene.getSpeakerImagePath() != null && !scene.getSpeakerImagePath().trim().isEmpty())
                ? scene.getSpeakerImagePath().trim() : null;
        if (speakerPath != null && !speakerPath.trim().isEmpty()) {
            File speakerFile = new File(speakerPath.trim());
            if (speakerFile.exists() && speakerFile.isFile()) {
                logger.info("📤 [ScenePipelineRunner] Uploading speaker image for scene {}: {}", scene.getSceneId(), speakerFile.getName());
                return jobPollingService.uploadFile(serverUrl, speakerFile);
            } else {
                logger.warn("⚠️ [ScenePipelineRunner] Speaker image path invalid: {}, proceeding with pure Text-to-Video.", speakerPath);
            }
        }
        return null;
    }

    private void postProcessMedia(
            File rawOutputFile,
            Scene scene,
            File finalVideoFile,
            float duration,
            float fps,
            ProcessTracker overrideTracker) throws Exception {
        boolean isImage = rawOutputFile.getName().toLowerCase().matches(".*\\.(png|jpg|jpeg|webp)$");
        String audioPath = scene.getAudioPath();
        File audioFile = (audioPath != null && !audioPath.trim().isEmpty()) ? new File(audioPath) : null;

        if (isImage) {
            logger.info("🖼️ [ScenePipelineRunner] Raw output is an image. Converting to MP4 scene video...");
            mediaTranscodingService.convertImageToVideo(rawOutputFile, audioFile, duration, fps, finalVideoFile, scene.getWidth(), scene.getHeight());
        } else if (audioFile != null && audioFile.exists()) {
            muxAudio(rawOutputFile, audioFile, finalVideoFile, overrideTracker);
            if (rawOutputFile.exists() && !rawOutputFile.getAbsolutePath().equals(finalVideoFile.getAbsolutePath())) {
                rawOutputFile.delete();
            }
        } else {
            logger.info("ℹ️ [ScenePipelineRunner] No audio file specified. Copying raw video to: {}", finalVideoFile.getAbsolutePath());
            Files.copy(rawOutputFile.toPath(), finalVideoFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void muxAudio(File rawOutputFile, File audioFile, File finalVideoFile, ProcessTracker overrideTracker) throws IOException, InterruptedException {
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

        logger.info("🎬 [ScenePipelineRunner] Running FFmpeg audio muxing command: {}", String.join(" ", cmd));
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

    private File generateSimulationFallback(Scene scene, File finalVideoFile) throws Exception {
        File fallbackVideo = simulationService.generateSimulatedVideo(scene, configService.getFfmpegPath());
        Files.copy(fallbackVideo.toPath(), finalVideoFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        fallbackVideo.delete();
        scene.setVideoPath(finalVideoFile.getAbsolutePath());
        return finalVideoFile;
    }

    private Process startProcess(ProcessBuilder pb, ProcessTracker tracker) throws IOException {
        ProcessTracker effectiveTracker = tracker != null ? tracker : this.processTracker;
        return effectiveTracker != null ? effectiveTracker.start(pb) : pb.start();
    }

    /**
     * Injects narration audio file reference into LoadAudio nodes in a workflow graph.
     *
     * @param workflowJson the workflow prompt JSON
     * @param filename     the uploaded audio filename
     */
    public void injectAudio(JSONObject workflowJson, String filename) {
        JSONObject promptObj = workflowJson.has("prompt") ? workflowJson.getJSONObject("prompt") : workflowJson;
        boolean injected = false;
        for (String key : promptObj.keySet()) {
            JSONObject node = promptObj.optJSONObject(key);
            if (node == null) continue;
            String classType = node.optString("class_type", "");
            if ("LoadAudio".equals(classType) || "VHS_LoadAudio".equals(classType)) {
                JSONObject inputs = node.optJSONObject("inputs");
                if (inputs != null) {
                    inputs.put("audio", filename);
                    injected = true;
                    logger.info("🎵 [ScenePipelineRunner] Injected audio '{}' into node ID: {}", filename, key);
                }
            }
        }
        if (!injected) {
            logger.info("ℹ️ [ScenePipelineRunner] No LoadAudio/VHS_LoadAudio node found in workflow to inject audio.");
        }
    }

    /**
     * Injects speaker image reference into LoadImage nodes in a workflow graph.
     *
     * @param workflowJson the workflow prompt JSON
     * @param filename     the uploaded speaker image filename
     */
    public void injectSpeakerImage(JSONObject workflowJson, String filename) {
        JSONObject promptObj = workflowJson.has("prompt") ? workflowJson.getJSONObject("prompt") : workflowJson;
        boolean injected = false;
        for (String key : promptObj.keySet()) {
            JSONObject node = promptObj.optJSONObject(key);
            if (node == null) continue;
            String classType = node.optString("class_type", "");
            if ("LoadImage".equals(classType)) {
                JSONObject inputs = node.optJSONObject("inputs");
                if (inputs != null) {
                    inputs.put("image", filename);
                    injected = true;
                    logger.info("📥 [ScenePipelineRunner] Injected speaker image '{}' into LoadImage node ID: {}", filename, key);
                }
            }
        }
        if (!injected) {
            logger.info("ℹ️ [ScenePipelineRunner] No LoadImage node found in workflow to inject speaker image.");
        }
    }

    /**
     * Injects textual prompt into text encoding nodes in a workflow graph.
     *
     * @param workflowJson the workflow prompt JSON
     * @param promptText   the visual prompt description
     */
    public void injectPrompt(JSONObject workflowJson, String promptText) {
        JSONObject promptObj = workflowJson.has("prompt") ? workflowJson.getJSONObject("prompt") : workflowJson;
        boolean injected = false;

        for (String key : promptObj.keySet()) {
            JSONObject node = promptObj.optJSONObject(key);
            if (node == null) continue;

            String classType = node.optString("class_type", "");
            JSONObject inputs = node.optJSONObject("inputs");
            if (inputs == null) continue;

            if ("CLIPTextEncode".equals(classType)) {
                String text = inputs.optString("text", "");
                if (!text.toLowerCase().contains("blurry") && !text.toLowerCase().contains("bad hands")
                        && !text.toLowerCase().contains("worst quality") && !text.toLowerCase().contains("low quality")
                        && !text.toLowerCase().contains("静止") && !text.toLowerCase().contains("最差质量")) {
                    inputs.put("text", promptText);
                    injected = true;
                    logger.info("📥 [ScenePipelineRunner] Injected visual_prompt into CLIPTextEncode node ID: {}", key);
                }
            } else if ("98ee9e5b-467b-40aa-a534-36033f27d0b4".equals(classType)
                    || "84e2cf3f-de93-40ef-ab22-b9375296917b".equals(classType)) {
                if (inputs.has("text")) {
                    inputs.put("text", promptText);
                } else {
                    inputs.put("value", promptText);
                }
                injected = true;
                logger.info("📥 [ScenePipelineRunner] Injected visual_prompt into Video Gen Subgraph node ID: {}", key);
            } else if ("PrimitiveStringMultiline".equals(classType)) {
                inputs.put("value", promptText);
                injected = true;
                logger.info("📥 [ScenePipelineRunner] Injected visual_prompt into PrimitiveStringMultiline node ID: {}", key);
            }
        }

        if (!injected) {
            throw new RuntimeException("Could not find suitable prompt input node in workflow template!");
        }
    }

    /**
     * Injects generation parameters (seeds, steps, cfg, duration) into a loaded blueprint workflow.
     *
     * @param workflowJson   the blueprint workflow JSON
     * @param seed           the random generation seed
     * @param filenamePrefix output filename prefix
     * @param scene          the scene domain model
     */
    public void injectParamsIntoBlueprint(JSONObject workflowJson, long seed, String filenamePrefix, Scene scene) {
        JSONObject promptObj = workflowJson.has("prompt") ? workflowJson.getJSONObject("prompt") : workflowJson;
        double sceneDuration = (scene.getEndFrame() - scene.getStartFrame()) / 30.0;
        if (sceneDuration <= 0) {
            sceneDuration = 5.0;
        }

        for (String key : promptObj.keySet()) {
            JSONObject node = promptObj.optJSONObject(key);
            if (node == null) continue;

            String classType = node.optString("class_type", "");
            JSONObject inputs = node.optJSONObject("inputs");
            if (inputs == null) continue;

            if ("84e2cf3f-de93-40ef-ab22-b9375296917b".equals(classType)
                    || "98ee9e5b-467b-40aa-a534-36033f27d0b4".equals(classType)) {
                if (inputs.has("value_1")) {
                    inputs.put("value_1", sceneDuration);
                    logger.info("📥 [ScenePipelineRunner] Injected scene duration {}s into video subgraph node ID: {}", sceneDuration, key);
                }
            }

            long clampedSeed = PromptBlueprintApiService.clampSeedForNode(classType, seed);
            if (inputs.has("seed")) {
                inputs.put("seed", clampedSeed);
            }
            if (inputs.has("noise_seed")) {
                inputs.put("noise_seed", clampedSeed);
            }

            if ("KSampler".equals(classType) || "KSamplerAdvanced".equals(classType)) {
                int wanSteps = scene.getSteps() > 0 ? scene.getSteps() : 4;
                if (wanSteps > 8) {
                    wanSteps = 4;
                }
                if (inputs.has("steps")) {
                    inputs.put("steps", wanSteps);
                }
                double wanCfg = scene.getCfgScale() > 0 ? scene.getCfgScale() : 2.0;
                if (wanCfg > 4.0) {
                    wanCfg = 3.0;
                }
                if (inputs.has("cfg")) {
                    inputs.put("cfg", wanCfg);
                }
            }

            if ("VHS_VideoCombine".equals(classType) || "SaveVideo".equals(classType)) {
                inputs.put("filename_prefix", filenamePrefix);
                if ("VHS_VideoCombine".equals(classType)) {
                    inputs.put("save_output", true);
                }
            }
        }
        PromptBlueprintApiService.sanitizeAllSeedsInPrompt(promptObj);
    }

    private JSONObject loadBlueprintWorkflow(String filename) {
        List<File> candidateDirs = new ArrayList<>();
        String comfyPath = configService.getComfyUIPath();
        if (comfyPath != null && !comfyPath.trim().isEmpty()) {
            candidateDirs.add(new File(comfyPath, "companion_blueprints"));
            candidateDirs.add(new File(comfyPath, "resources/ComfyUI/companion_blueprints"));
        }
        candidateDirs.add(new File("companion_blueprints"));
        candidateDirs.add(new File(System.getProperty("user.dir"), "companion_blueprints"));

        for (File blueprintsDir : candidateDirs) {
            if (blueprintsDir == null || !blueprintsDir.exists() || !blueprintsDir.isDirectory()) {
                continue;
            }
            File blueprintFile = new File(blueprintsDir, filename);
            JSONObject parsed = parseBlueprintFile(blueprintFile);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private JSONObject parseBlueprintFile(File blueprintFile) {
        if (!blueprintFile.exists() || !blueprintFile.isFile()) {
            return null;
        }
        try {
            String content = Files.readString(blueprintFile.toPath(), StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(content);
            if (json.has("prompt") || json.keySet().stream().anyMatch(key -> {
                JSONObject node = json.optJSONObject(key);
                return node != null && node.has("class_type");
            })) {
                return json;
            }
            if (json.has("nodes")) {
                JSONObject flattened = workflowTransformationService != null
                        ? workflowTransformationService.flattenWorkflow(json)
                        : new WorkflowTransformationService(null).flattenWorkflow(json);
                return workflowTransformationService != null
                        ? workflowTransformationService.convertUiToApi(flattened)
                        : new WorkflowTransformationService(null).convertUiToApi(flattened);
            }
        } catch (Exception e) {
            logger.error("⚠️ [ScenePipelineRunner] Failed to load blueprint {}: {}", blueprintFile.getName(), e.getMessage());
        }
        return null;
    }
}
