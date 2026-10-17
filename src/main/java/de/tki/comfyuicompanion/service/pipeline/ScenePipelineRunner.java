package de.tki.comfyuicompanion.service.pipeline;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.EnvironmentBootstrapperImpl;
import de.tki.comfyuicompanion.service.impl.ProcessTracker;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private final SceneBlueprintInjector blueprintInjector;
    private final SceneMediaPostProcessor mediaPostProcessor;

    public ScenePipelineRunner(
            ConfigService configService,
            SceneSimulationService simulationService,
            MediaTranscodingService mediaTranscodingService,
            VideoWorkflowBuilder workflowBuilder,
            ComfyJobPollingService jobPollingService,
            ComfyNodeInspectionClient inspectionClient) {
        this(configService, simulationService, mediaTranscodingService, workflowBuilder,
                jobPollingService, inspectionClient, null, null, null, null, null, null);
    }

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
                jobPollingService, inspectionClient, bootstrapper, lifecycleService, processTracker, null, null, null);
    }

    public ScenePipelineRunner(
            ConfigService configService,
            SceneSimulationService simulationService,
            MediaTranscodingService mediaTranscodingService,
            VideoWorkflowBuilder workflowBuilder,
            ComfyJobPollingService jobPollingService,
            ComfyNodeInspectionClient inspectionClient,
            EnvironmentBootstrapperImpl bootstrapper,
            IComfyLifecycleService lifecycleService,
            ProcessTracker processTracker,
            WorkflowTransformationService workflowTransformationService) {
        this(configService, simulationService, mediaTranscodingService, workflowBuilder,
                jobPollingService, inspectionClient, bootstrapper, lifecycleService, processTracker,
                workflowTransformationService, null, null);
    }

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
            @Autowired(required = false) WorkflowTransformationService workflowTransformationService,
            @Autowired(required = false) SceneBlueprintInjector blueprintInjector,
            @Autowired(required = false) SceneMediaPostProcessor mediaPostProcessor) {
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
        this.blueprintInjector = blueprintInjector != null
                ? blueprintInjector
                : new SceneBlueprintInjector(this.workflowTransformationService);
        this.mediaPostProcessor = mediaPostProcessor != null
                ? mediaPostProcessor
                : new SceneMediaPostProcessor(configService, this.simulationService, this.mediaTranscodingService, processTracker);
    }

    /**
     * Executes scene generation in standard or strict mode asynchronously.
     *
     * @param scene the scene domain object containing generation parameters
     * @param strictMode true to fail fast without fallback simulation; false to allow fallback
     * @return a CompletableFuture resolving to the generated video file
     */
    public CompletableFuture<File> executeScene(Scene scene, boolean strictMode) {
        return executeScene(scene, strictMode, this.lifecycleService, this.processTracker);
    }

    /**
     * Executes scene generation with explicit lifecycle service and process tracker overrides.
     *
     * @param scene the scene domain object containing generation parameters
     * @param strictMode true to fail fast without fallback simulation; false to allow fallback
     * @param overrideLifecycle optional lifecycle service override
     * @param overrideTracker optional process tracker override
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
                    mediaPostProcessor.postProcessMedia(rawOutputFile, scene, finalVideoFile, duration, fps, overrideTracker);
                    scene.setVideoPath(finalVideoFile.getAbsolutePath());
                    return finalVideoFile;
                } else {
                    if (strictMode) {
                        throw new RuntimeException("Strict mode enabled: ComfyUI finished generation, but no output video or image file was found.");
                    }
                    return mediaPostProcessor.generateSimulationFallback(scene, finalVideoFile);
                }

            } catch (Exception e) {
                if (strictMode) {
                    logger.error("🔴 [ScenePipelineRunner] Failed generating scene via ComfyUI (Strict Mode): {}", e.getMessage());
                    throw new RuntimeException("Strict mode generation failed: " + e.getMessage(), e);
                }
                logger.error("🔴 [ScenePipelineRunner] Failed generating scene: {}. Generating simulated fallback video.", e.getMessage());
                try {
                    File finalVideoFile = new File(new File("").getAbsoluteFile(), "scene_" + scene.getSceneId() + "_final.mp4");
                    return mediaPostProcessor.generateSimulationFallback(scene, finalVideoFile);
                } catch (Exception fe) {
                    throw new RuntimeException("Failed generating scene and fallback also failed: " + fe.getMessage(), fe);
                }
            }
        });
    }

    /**
     * Determines the appropriate workflow graph to execute based on available models and scene specifications.
     *
     * @param serverUrl the ComfyUI server address
     * @param scene the scene domain model
     * @param speakerImage uploaded speaker image reference name, if present
     * @param seed the generation seed
     * @param filenamePrefix prefix for generated files
     * @param availableUnets available diffusion models
     * @param availableCheckpoints available full checkpoints
     * @param availableClips available CLIP text encoders
     * @param availableVaes available VAE decoders
     * @param wanNodesAvailable whether Wan native nodes are present
     * @param hasWanModels whether Wan weights are found
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
            JSONObject blueprintJson = blueprintInjector.loadBlueprintWorkflow("Text to Video (Wan 2.2).json", configService.getComfyUIPath());
            if (blueprintJson != null) {
                logger.info("📄 [ScenePipelineRunner] Using Wan 2.2 blueprint workflow with start image.");
                blueprintInjector.injectPrompt(blueprintJson, scene.getPrompt());
                blueprintInjector.injectParamsIntoBlueprint(blueprintJson, seed, filenamePrefix, scene);
                blueprintInjector.injectSpeakerImage(blueprintJson, speakerImage);
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

    /**
     * Injects narration audio file reference into LoadAudio nodes in a workflow graph.
     *
     * @param workflowJson the workflow prompt JSON
     * @param filename the uploaded audio filename
     */
    public void injectAudio(JSONObject workflowJson, String filename) {
        blueprintInjector.injectAudio(workflowJson, filename);
    }

    /**
     * Injects speaker image reference into LoadImage nodes in a workflow graph.
     *
     * @param workflowJson the workflow prompt JSON
     * @param filename the uploaded speaker image filename
     */
    public void injectSpeakerImage(JSONObject workflowJson, String filename) {
        blueprintInjector.injectSpeakerImage(workflowJson, filename);
    }

    /**
     * Injects textual prompt into text encoding nodes in a workflow graph.
     *
     * @param workflowJson the workflow prompt JSON
     * @param promptText the visual prompt description
     */
    public void injectPrompt(JSONObject workflowJson, String promptText) {
        blueprintInjector.injectPrompt(workflowJson, promptText);
    }

    /**
     * Injects generation parameters (seeds, steps, cfg, duration) into a loaded blueprint workflow.
     *
     * @param workflowJson the blueprint workflow JSON
     * @param seed the random generation seed
     * @param filenamePrefix output filename prefix
     * @param scene the scene domain model
     */
    public void injectParamsIntoBlueprint(JSONObject workflowJson, long seed, String filenamePrefix, Scene scene) {
        blueprintInjector.injectParamsIntoBlueprint(workflowJson, seed, filenamePrefix, scene);
    }

    /**
     * Executes single-frame keyframe image generation for a scene.
     *
     * @param scene the scene domain model
     * @return CompletableFuture resolving to the generated keyframe image file
     */
    public CompletableFuture<File> executeKeyframe(Scene scene) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String serverUrl = configService.getComfyUIUrl();
                long seed = Math.abs(new Random().nextLong());
                String filenamePrefix = "keyframe_" + scene.getSceneId();
                String speakerImage = uploadSpeakerImageIfPresent(serverUrl, scene);

                List<String> unets = inspectionClient.fetchObjectInfoOptions(serverUrl, "UNETLoader", "unet_name");
                List<String> ckpts = inspectionClient.fetchAvailableCheckpoints(serverUrl);
                List<String> clips = inspectionClient.fetchObjectInfoOptions(serverUrl, "CLIPLoader", "clip_name");
                List<String> vaes = inspectionClient.fetchObjectInfoOptions(serverUrl, "VAELoader", "vae_name");

                JSONObject workflowJson = workflowBuilder.generateSceneImageWorkflow(
                        serverUrl, scene, speakerImage, seed, filenamePrefix, unets, ckpts, clips, vaes);
                workflowBuilder.sanitizeWorkflow(workflowJson, serverUrl);

                String promptId = jobPollingService.submitPrompt(serverUrl, workflowJson);
                logger.info("🎨 [ScenePipelineRunner] Submitted keyframe job for scene {}. Prompt ID: {}", scene.getSceneId(), promptId);
                ComfyJobPollingService.ComfyOutputRef outputRef = jobPollingService.pollHistoryForOutput(serverUrl, promptId);

                String comfyOutputPath = configService.getResolvedOutputDir();
                if (comfyOutputPath == null || comfyOutputPath.trim().isEmpty()) {
                    comfyOutputPath = new File("output").getAbsolutePath();
                }
                File imageFile = jobPollingService.resolveOutputFile(comfyOutputPath, outputRef, scene.getSceneId());
                if (imageFile == null && outputRef != null && outputRef.filename() != null && !outputRef.filename().trim().isEmpty()) {
                    imageFile = jobPollingService.downloadOutput(serverUrl, outputRef, scene.getSceneId());
                }
                return imageFile;
            } catch (Exception e) {
                logger.error("🔴 [ScenePipelineRunner] Failed generating keyframe for scene {}: {}", scene.getSceneId(), e.getMessage());
                throw new RuntimeException("Keyframe generation failed: " + e.getMessage(), e);
            }
        });
    }
}

