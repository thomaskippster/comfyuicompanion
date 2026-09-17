package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.domain.ComfyTemplate;
import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import de.tki.comfyuicompanion.service.IComfyTemplateService;
import de.tki.comfyuicompanion.service.IHardwareProfileService;
import de.tki.comfyuicompanion.service.IModelArchitectureService;
import de.tki.comfyuicompanion.service.PromptBlueprintApiService;
import de.tki.comfyuicompanion.service.pipeline.ComfyJobPollingService;
import de.tki.comfyuicompanion.service.pipeline.ComfyNodeInspectionClient;
import de.tki.comfyuicompanion.service.pipeline.MediaTranscodingService;
import de.tki.comfyuicompanion.service.pipeline.SceneSimulationService;
import de.tki.comfyuicompanion.service.pipeline.VideoWorkflowBuilder;
import de.tki.comfyuicompanion.service.pipeline.WorkflowTransformationService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing scene video generation pipelines, ComfyUI job submissions,
 * video muxing, and fallback synthetic scene rendering.
 */
@Service
public class ComfyPipelineService {
    private static final Logger logger = LoggerFactory.getLogger(ComfyPipelineService.class);

    /**
     * Immutable reference to an output artifact produced by ComfyUI.
     */
    public static final class ComfyOutputRef {
        public final String filename;
        public final String subfolder;
        public final String type;

        public ComfyOutputRef(String filename, String subfolder, String type) {
            this.filename = filename;
            this.subfolder = subfolder != null ? subfolder : "";
            this.type = type != null ? type : "output";
        }

        public static ComfyOutputRef from(ComfyJobPollingService.ComfyOutputRef ref) {
            if (ref == null) return null;
            return new ComfyOutputRef(ref.filename(), ref.subfolder(), ref.type());
        }

        public ComfyJobPollingService.ComfyOutputRef toPollingRef() {
            return new ComfyJobPollingService.ComfyOutputRef(filename, subfolder, type);
        }
    }

    private static final Set<String> ATTEMPTED_INSTALLS = ConcurrentHashMap.newKeySet();

    private final HttpClient httpClient;
    private final ConfigService configService;
    private final ProcessTracker processTracker;
    private final IComfyLifecycleService lifecycleService;
    private final IModelArchitectureService modelArchitectureService;
    private final EnvironmentBootstrapperImpl bootstrapper;
    private final PromptBlueprintApiService promptBlueprintApiService;
    private final IComfyTemplateService comfyTemplateService;
    private final MediaTranscodingService mediaTranscodingService;
    private final WorkflowTransformationService workflowTransformationService;
    private final IHardwareProfileService hardwareProfileService;
    private final ComfyNodeInspectionClient inspectionClient;
    private final SceneSimulationService simulationService;
    private final VideoWorkflowBuilder workflowBuilder;
    private final ComfyJobPollingService jobPollingService;

    private static final WorkflowTransformationService DEFAULT_TRANSFORMATION_SERVICE =
            new WorkflowTransformationService(null);

    public ComfyPipelineService(ConfigService configService) {
        this(configService, null, null, null, null, null, null, null, null);
    }

    public ComfyPipelineService(ConfigService configService, HttpClient httpClient) {
        this(configService, httpClient, null, null, null, null, null, null, null, null);
    }

    @Autowired
    public ComfyPipelineService(
            ConfigService configService,
            @Autowired(required = false) ProcessTracker processTracker,
            @Autowired(required = false) @Lazy IComfyLifecycleService lifecycleService,
            @Autowired(required = false) IModelArchitectureService modelArchitectureService,
            @Autowired(required = false) EnvironmentBootstrapperImpl bootstrapper,
            @Autowired(required = false) PromptBlueprintApiService promptBlueprintApiService,
            @Autowired(required = false) IComfyTemplateService comfyTemplateService,
            @Autowired(required = false) MediaTranscodingService mediaTranscodingService,
            @Autowired(required = false) WorkflowTransformationService workflowTransformationService) {
        this(configService, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(),
                processTracker, lifecycleService, modelArchitectureService, bootstrapper,
                promptBlueprintApiService, comfyTemplateService, mediaTranscodingService, workflowTransformationService, null);
    }

    public ComfyPipelineService(
            ConfigService configService,
            HttpClient httpClient,
            ProcessTracker processTracker,
            IComfyLifecycleService lifecycleService,
            IModelArchitectureService modelArchitectureService,
            EnvironmentBootstrapperImpl bootstrapper,
            PromptBlueprintApiService promptBlueprintApiService,
            IComfyTemplateService comfyTemplateService,
            MediaTranscodingService mediaTranscodingService,
            WorkflowTransformationService workflowTransformationService) {
        this(configService, httpClient, processTracker, lifecycleService, modelArchitectureService, bootstrapper,
                promptBlueprintApiService, comfyTemplateService, mediaTranscodingService, workflowTransformationService, null);
    }

    public ComfyPipelineService(
            ConfigService configService,
            HttpClient httpClient,
            ProcessTracker processTracker,
            IComfyLifecycleService lifecycleService,
            IModelArchitectureService modelArchitectureService,
            EnvironmentBootstrapperImpl bootstrapper,
            PromptBlueprintApiService promptBlueprintApiService,
            IComfyTemplateService comfyTemplateService,
            MediaTranscodingService mediaTranscodingService,
            WorkflowTransformationService workflowTransformationService,
            IHardwareProfileService hardwareProfileService) {
        this(configService, httpClient, processTracker, lifecycleService, modelArchitectureService, bootstrapper,
                promptBlueprintApiService, comfyTemplateService, mediaTranscodingService, workflowTransformationService,
                hardwareProfileService, new ComfyNodeInspectionClient(httpClient), new SceneSimulationService(processTracker),
                new VideoWorkflowBuilder(new ComfyNodeInspectionClient(httpClient), modelArchitectureService, hardwareProfileService, promptBlueprintApiService, comfyTemplateService, workflowTransformationService));
    }

    public ComfyPipelineService(
            ConfigService configService,
            HttpClient httpClient,
            ProcessTracker processTracker,
            IComfyLifecycleService lifecycleService,
            IModelArchitectureService modelArchitectureService,
            EnvironmentBootstrapperImpl bootstrapper,
            PromptBlueprintApiService promptBlueprintApiService,
            IComfyTemplateService comfyTemplateService,
            MediaTranscodingService mediaTranscodingService,
            WorkflowTransformationService workflowTransformationService,
            IHardwareProfileService hardwareProfileService,
            ComfyNodeInspectionClient inspectionClient,
            SceneSimulationService simulationService,
            VideoWorkflowBuilder workflowBuilder) {
        this.configService = configService;
        this.httpClient = httpClient != null ? httpClient : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        this.processTracker = processTracker;
        this.lifecycleService = lifecycleService;
        this.modelArchitectureService = modelArchitectureService;
        this.bootstrapper = bootstrapper;
        this.promptBlueprintApiService = promptBlueprintApiService;
        this.comfyTemplateService = comfyTemplateService;
        this.mediaTranscodingService = mediaTranscodingService;
        this.workflowTransformationService = workflowTransformationService;
        this.hardwareProfileService = hardwareProfileService;
        this.inspectionClient = inspectionClient != null ? inspectionClient : new ComfyNodeInspectionClient(this.httpClient);
        this.simulationService = simulationService != null ? simulationService : new SceneSimulationService(processTracker);
        this.workflowBuilder = workflowBuilder != null ? workflowBuilder : new VideoWorkflowBuilder(this.inspectionClient, modelArchitectureService, hardwareProfileService, promptBlueprintApiService, comfyTemplateService, workflowTransformationService);
        this.jobPollingService = new ComfyJobPollingService(this.httpClient);
    }

    private Process startProcess(ProcessBuilder pb) throws IOException {
        return processTracker != null ? processTracker.start(pb) : pb.start();
    }

    public static JSONObject convertUiToApi(JSONObject uiWorkflow) {
        return DEFAULT_TRANSFORMATION_SERVICE.convertUiToApi(uiWorkflow);
    }

    public static JSONObject flattenWorkflow(JSONObject uiWorkflow) {
        return DEFAULT_TRANSFORMATION_SERVICE.flattenWorkflow(uiWorkflow);
    }

    public File convertImageToVideo(File imageFile, File audioFile, float durationSeconds, float fps, File outputFile) throws Exception {
        return convertImageToVideo(imageFile, audioFile, durationSeconds, fps, outputFile, 1920, 1080);
    }

    public File convertImageToVideo(File imageFile, File audioFile, float durationSeconds, float fps, File outputFile, int targetWidth, int targetHeight) throws Exception {
        if (mediaTranscodingService != null) {
            return mediaTranscodingService.convertImageToVideo(imageFile, audioFile, durationSeconds, fps, outputFile, targetWidth, targetHeight);
        }
        MediaTranscodingService fallbackTranscoder = new MediaTranscodingService(configService);
        return fallbackTranscoder.convertImageToVideo(imageFile, audioFile, durationSeconds, fps, outputFile, targetWidth, targetHeight);
    }

    public CompletableFuture<File> generateScene(Scene scene) {
        return generateSceneInternal(scene, false);
    }

    public CompletableFuture<File> generateSceneStrict(Scene scene) {
        return generateSceneInternal(scene, true);
    }

    private CompletableFuture<File> generateSceneInternal(Scene scene, boolean strictMode) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                simulationService.validateFfmpeg(configService.getFfmpegPath());

                // Auto-start ComfyUI if offline
                if (lifecycleService != null && !lifecycleService.isHealthy()) {
                    logger.info("🔄 [ComfyPipeline] ComfyUI server is offline. Attempting auto-start...");
                    lifecycleService.start();

                    int maxWaitSeconds = 90;
                    boolean started = false;
                    for (int i = 0; i < maxWaitSeconds; i++) {
                        if (lifecycleService.isHealthy()) {
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
                    logger.info("✅ [ComfyPipeline] ComfyUI successfully started and healthy.");
                }

                String serverUrl = configService.getComfyUIUrl();

                // Check and install VHS_VideoCombine if missing
                if (lifecycleService != null && lifecycleService.isHealthy() && bootstrapper != null) {
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
                            logger.info("⚠️ [ComfyPipeline] ComfyUI-Video-Helper-Suite installation already attempted. Skipping.");
                        } else {
                            ATTEMPTED_INSTALLS.add("VHS_VideoCombine");
                            if (!folderExists) {
                                logger.info("⚠️ [ComfyPipeline] VHS_VideoCombine is missing. Installing ComfyUI-Video-Helper-Suite...");
                                lifecycleService.stop();

                                String pythonPath = configService.getPythonPath();
                                if (comfyPath != null && !comfyPath.trim().isEmpty()) {
                                    Path comfyDir = Paths.get(comfyPath);
                                    Path pythonExe = (pythonPath != null && !pythonPath.trim().isEmpty()) ? Paths.get(pythonPath) : null;
                                    bootstrapper.ensureVideoHelperSuiteInstalled(comfyDir, pythonExe, System.out::println);
                                }
                                lifecycleService.start();
                            } else {
                                logger.info("🔄 [ComfyPipeline] VHS_VideoCombine not active but folder exists. Restarting ComfyUI...");
                                lifecycleService.restart();
                            }

                            int maxWaitSeconds = 90;
                            boolean started = false;
                            for (int i = 0; i < maxWaitSeconds; i++) {
                                if (lifecycleService.isHealthy()) {
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
                            logger.info("✅ [ComfyPipeline] ComfyUI successfully restarted and healthy.");
                        }
                    }
                }

                long seed = Math.abs(new Random().nextLong());
                String filenamePrefix = "videoarchitect_" + scene.getSceneId();

                String speakerImage = null;
                String speakerPath = (scene != null && scene.getSpeakerImagePath() != null && !scene.getSpeakerImagePath().trim().isEmpty())
                        ? scene.getSpeakerImagePath().trim() : null;
                if (speakerPath != null && !speakerPath.trim().isEmpty()) {
                    File speakerFile = new File(speakerPath.trim());
                    if (speakerFile.exists() && speakerFile.isFile()) {
                        logger.info("📤 [ComfyPipeline] Uploading speaker image for scene {}: {}", scene.getSceneId(), speakerFile.getName());
                        speakerImage = uploadFile(serverUrl, speakerFile);
                    } else {
                        logger.warn("⚠️ [ComfyPipeline] Speaker image path invalid: {}, proceeding with pure Text-to-Video.", speakerPath);
                    }
                }

                List<String> availableUnets = inspectionClient.fetchObjectInfoOptions(serverUrl, "UNETLoader", "unet_name");
                List<String> availableCheckpoints = inspectionClient.fetchAvailableCheckpoints(serverUrl);
                List<String> availableClips = inspectionClient.fetchObjectInfoOptions(serverUrl, "CLIPLoader", "clip_name");
                List<String> availableVaes = inspectionClient.fetchObjectInfoOptions(serverUrl, "VAELoader", "vae_name");

                boolean wanNodesAvailable = inspectionClient.isNodeClassAvailable(serverUrl, "WanImageToVideo");
                boolean hasWanModels = workflowBuilder.isWanSetupComplete(availableUnets, availableClips, availableVaes);

                JSONObject workflowJson = null;
                if (wanNodesAvailable && hasWanModels) {
                    logger.info("🎬 [ComfyPipeline] Generating Wan 2.2 native video workflow...");
                    workflowJson = workflowBuilder.generateWanWorkflowJson(serverUrl, scene, speakerImage, seed, filenamePrefix);
                } else if (speakerImage != null && !speakerImage.trim().isEmpty() && hasWanModels) {
                    JSONObject blueprintJson = loadBlueprintWorkflow("Text to Video (Wan 2.2).json");
                    if (blueprintJson != null) {
                        logger.info("📄 [ComfyPipeline] Using Wan 2.2 blueprint workflow with start image.");
                        injectPrompt(blueprintJson, scene.getPrompt());
                        injectParamsIntoBlueprint(blueprintJson, seed, filenamePrefix, scene);
                        injectSpeakerImage(blueprintJson, speakerImage);
                        workflowJson = blueprintJson;
                    }
                }

                if (workflowJson == null) {
                    logger.info("🖼️ [ComfyPipeline] Dedicated video diffusion models not found; generating scene visuals via available image model...");
                    workflowJson = workflowBuilder.generateSceneImageWorkflow(serverUrl, scene, speakerImage, seed, filenamePrefix, availableUnets, availableCheckpoints, availableClips, availableVaes);
                }

                sanitizeWorkflow(workflowJson, serverUrl);

                ComfyOutputRef outputRef = null;
                boolean isWanAttempt = (wanNodesAvailable && hasWanModels);

                try {
                    String promptId = submitPrompt(serverUrl, workflowJson);
                    logger.info("🚀 [ComfyPipeline] Submitted job. Prompt ID: {}", promptId);
                    outputRef = pollHistoryForOutput(serverUrl, promptId);
                } catch (Exception ex) {
                    if (isWanAttempt) {
                        logger.warn("⚠️ [ComfyPipeline] Video model execution failed ({}); falling back to image diffusion model synthesis...", ex.getMessage());
                        JSONObject fallbackWorkflow = workflowBuilder.generateSceneImageWorkflow(serverUrl, scene, speakerImage, seed, filenamePrefix, availableUnets, availableCheckpoints, availableClips, availableVaes);
                        sanitizeWorkflow(fallbackWorkflow, serverUrl);
                        String fallbackPromptId = submitPrompt(serverUrl, fallbackWorkflow);
                        logger.info("🚀 [ComfyPipeline] Submitted fallback job. Prompt ID: {}", fallbackPromptId);
                        outputRef = pollHistoryForOutput(serverUrl, fallbackPromptId);
                    } else {
                        throw ex;
                    }
                }

                String finishedFilename = outputRef != null ? outputRef.filename : null;
                logger.info("✅ [ComfyPipeline] Job finished. Filename: {}{}",
                        finishedFilename,
                        (outputRef != null && !outputRef.subfolder.isEmpty() ? " (subfolder: " + outputRef.subfolder + ")" : ""));

                String comfyOutputPath = configService.getResolvedOutputDir();
                if (comfyOutputPath == null || comfyOutputPath.trim().isEmpty()) {
                    comfyOutputPath = new File("output").getAbsolutePath();
                }

                File rawOutputFile = resolveOutputFile(comfyOutputPath, outputRef, scene.getSceneId());
                if (rawOutputFile == null && finishedFilename != null && !finishedFilename.trim().isEmpty()) {
                    rawOutputFile = downloadOutput(serverUrl, outputRef, scene.getSceneId());
                }

                File finalVideoFile = new File(new File("").getAbsoluteFile(), "scene_" + scene.getSceneId() + "_final.mp4");

                float duration = 5.0f;
                if (scene.getEndFrame() > scene.getStartFrame()) {
                    duration = Math.max(1.0f, (scene.getEndFrame() - scene.getStartFrame()) / 24.0f);
                }
                float fps = 24.0f;

                if (rawOutputFile != null && rawOutputFile.exists()) {
                    boolean isImage = rawOutputFile.getName().toLowerCase().matches(".*\\.(png|jpg|jpeg|webp)$");
                    String audioPath = scene.getAudioPath();
                    File audioFile = (audioPath != null && !audioPath.trim().isEmpty()) ? new File(audioPath) : null;

                    if (isImage) {
                        logger.info("🖼️ [ComfyPipeline] Raw output is an image. Converting to MP4 scene video...");
                        convertImageToVideo(rawOutputFile, audioFile, duration, fps, finalVideoFile, scene.getWidth(), scene.getHeight());
                    } else if (audioFile != null && audioFile.exists()) {
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

                        logger.info("🎬 [ComfyPipeline] Running FFmpeg audio muxing command: {}", String.join(" ", cmd));
                        ProcessBuilder pb = new ProcessBuilder(cmd);
                        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                        Process process = startProcess(pb);

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

                        if (rawOutputFile.exists() && !rawOutputFile.getAbsolutePath().equals(finalVideoFile.getAbsolutePath())) {
                            rawOutputFile.delete();
                        }
                    } else {
                        logger.info("ℹ️ [ComfyPipeline] No audio file specified. Copying raw video to: {}", finalVideoFile.getAbsolutePath());
                        Files.copy(rawOutputFile.toPath(), finalVideoFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }

                    scene.setVideoPath(finalVideoFile.getAbsolutePath());
                    return finalVideoFile;
                } else {
                    if (strictMode) {
                        throw new Exception("Strict mode enabled: ComfyUI finished generation, but no output video or image file was found.");
                    }
                    File fallbackVideo = simulationService.generateSimulatedVideo(scene, configService.getFfmpegPath());
                    Files.copy(fallbackVideo.toPath(), finalVideoFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    fallbackVideo.delete();
                    scene.setVideoPath(finalVideoFile.getAbsolutePath());
                    return finalVideoFile;
                }

            } catch (Exception e) {
                if (strictMode) {
                    logger.error("🔴 [ComfyPipeline] Failed generating scene via ComfyUI (Strict Mode): {}", e.getMessage());
                    throw new RuntimeException("Strict mode generation failed: " + e.getMessage(), e);
                }
                logger.error("🔴 [ComfyPipeline] Failed generating scene: {}. Generating simulated fallback video.", e.getMessage());
                try {
                    File fallbackVideo = simulationService.generateSimulatedVideo(scene, configService.getFfmpegPath());
                    File finalVideoFile = new File(new File("").getAbsoluteFile(), "scene_" + scene.getSceneId() + "_final.mp4");
                    Files.copy(fallbackVideo.toPath(), finalVideoFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    fallbackVideo.delete();
                    scene.setVideoPath(finalVideoFile.getAbsolutePath());
                    return finalVideoFile;
                } catch (Exception fe) {
                    throw new RuntimeException("Failed generating scene and fallback also failed: " + fe.getMessage(), fe);
                }
            }
        });
    }

    public CompletableFuture<File> generateMontageScene(Scene scene) {
        return CompletableFuture.supplyAsync(() -> {
            String sourceClip = scene.getSourceClipPath();
            if (sourceClip != null && !sourceClip.trim().isEmpty() && new File(sourceClip).exists()) {
                logger.info("🎬 [ComfyPipeline] Montage mode: Using source clip as input: {}", sourceClip);
                scene.setSpeakerImagePath(sourceClip);
            }
            return generateScene(scene).join();
        });
    }

    public void sanitizeWorkflow(JSONObject workflowJson, String serverUrl) {
        workflowBuilder.sanitizeWorkflow(workflowJson, serverUrl);
    }

    private String submitPrompt(String serverUrl, JSONObject workflowJson) throws IOException, InterruptedException {
        return jobPollingService.submitPrompt(serverUrl, workflowJson);
    }

    public String pollHistoryForFilename(String serverUrl, String promptId) throws IOException, InterruptedException {
        return jobPollingService.pollHistoryForFilename(serverUrl, promptId);
    }

    public ComfyOutputRef pollHistoryForOutput(String serverUrl, String promptId) throws IOException, InterruptedException {
        return ComfyOutputRef.from(jobPollingService.pollHistoryForOutput(serverUrl, promptId));
    }

    private File resolveOutputFile(String comfyOutputPath, ComfyOutputRef outputRef, String sceneId) {
        return jobPollingService.resolveOutputFile(
                comfyOutputPath,
                outputRef != null ? outputRef.toPollingRef() : null,
                sceneId
        );
    }

    private File downloadOutput(String serverUrl, ComfyOutputRef outputRef, String sceneId) throws IOException, InterruptedException {
        return jobPollingService.downloadOutput(
                serverUrl,
                outputRef != null ? outputRef.toPollingRef() : null,
                sceneId
        );
    }

    private void injectPrompt(JSONObject workflowJson, String promptText) {
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
                    logger.info("📥 [ComfyPipeline] Injected visual_prompt into CLIPTextEncode node ID: {}", key);
                }
            } else if ("98ee9e5b-467b-40aa-a534-36033f27d0b4".equals(classType)
                    || "84e2cf3f-de93-40ef-ab22-b9375296917b".equals(classType)) {
                if (inputs.has("text")) {
                    inputs.put("text", promptText);
                } else {
                    inputs.put("value", promptText);
                }
                injected = true;
                logger.info("📥 [ComfyPipeline] Injected visual_prompt into Video Gen Subgraph node ID: {}", key);
            } else if ("PrimitiveStringMultiline".equals(classType)) {
                inputs.put("value", promptText);
                injected = true;
                logger.info("📥 [ComfyPipeline] Injected visual_prompt into PrimitiveStringMultiline node ID: {}", key);
            }
        }

        if (!injected) {
            throw new RuntimeException("Could not find suitable prompt input node in workflow template!");
        }
    }

    private void injectSpeakerImage(JSONObject workflowJson, String filename) {
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
                    logger.info("📥 [ComfyPipeline] Injected speaker image '{}' into LoadImage node ID: {}", filename, key);
                }
            }
        }
        if (!injected) {
            logger.info("ℹ️ [ComfyPipeline] No LoadImage node found in workflow to inject speaker image.");
        }
    }

    private void injectAudio(JSONObject workflowJson, String filename) {
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
                    logger.info("🎵 [ComfyPipeline] Injected audio '{}' into node ID: {}", filename, key);
                }
            }
        }
        if (!injected) {
            logger.info("ℹ️ [ComfyPipeline] No LoadAudio/VHS_LoadAudio node found in workflow to inject audio.");
        }
    }

    private JSONObject generateWanWorkflowJson(String serverUrl, Scene scene, String uploadedSpeakerImage, long seed, String filenamePrefix) {
        return workflowBuilder.generateWanWorkflowJson(serverUrl, scene, uploadedSpeakerImage, seed, filenamePrefix);
    }

    public String uploadFile(String serverUrl, File file) throws IOException, InterruptedException {
        return jobPollingService.uploadFile(serverUrl, file);
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
                JSONObject flattened = flattenWorkflow(json);
                return convertUiToApi(flattened);
            }
        } catch (Exception e) {
            logger.error("⚠️ [ComfyPipeline] Failed to load blueprint {}: {}", blueprintFile.getName(), e.getMessage());
        }
        return null;
    }

    private void injectParamsIntoBlueprint(JSONObject workflowJson, long seed, String filenamePrefix, Scene scene) {
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
                    logger.info("📥 [ComfyPipeline] Injected scene duration {}s into video subgraph node ID: {}", sceneDuration, key);
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
}
