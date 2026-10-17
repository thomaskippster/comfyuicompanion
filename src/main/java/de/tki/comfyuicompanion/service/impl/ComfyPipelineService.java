package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import de.tki.comfyuicompanion.service.IComfyTemplateService;
import de.tki.comfyuicompanion.service.IHardwareProfileService;
import de.tki.comfyuicompanion.service.IModelArchitectureService;
import de.tki.comfyuicompanion.service.PromptBlueprintApiService;
import de.tki.comfyuicompanion.service.pipeline.ComfyJobPollingService;
import de.tki.comfyuicompanion.service.pipeline.ComfyNodeInspectionClient;
import de.tki.comfyuicompanion.service.pipeline.MediaTranscodingService;
import de.tki.comfyuicompanion.service.pipeline.ScenePipelineRunner;
import de.tki.comfyuicompanion.service.pipeline.SceneSimulationService;
import de.tki.comfyuicompanion.service.pipeline.VideoWorkflowBuilder;
import de.tki.comfyuicompanion.service.pipeline.WorkflowTransformationService;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Service facade managing scene video generation pipelines, ComfyUI job submissions,
 * video transcoding, and delegation to specialized pipeline execution components.
 */
@Service
public class ComfyPipelineService {

    private static final Logger logger = LoggerFactory.getLogger(ComfyPipelineService.class);
    private static final WorkflowTransformationService DEFAULT_TRANSFORMATION_SERVICE =
            new WorkflowTransformationService(null);

    /**
     * Immutable reference to an output artifact produced by ComfyUI.
     */
    public static final class ComfyOutputRef {
        public final String filename;
        public final String subfolder;
        public final String type;

        /**
         * Constructs a new output reference.
         *
         * @param filename  the output filename
         * @param subfolder the subfolder containing the file, if any
         * @param type      the output storage type
         */
        public ComfyOutputRef(String filename, String subfolder, String type) {
            this.filename = filename;
            this.subfolder = subfolder != null ? subfolder : "";
            this.type = type != null ? type : "output";
        }

        /**
         * Creates a ComfyOutputRef from a polling service record.
         *
         * @param ref the polling service output reference
         * @return the converted reference, or null if input is null
         */
        public static ComfyOutputRef from(ComfyJobPollingService.ComfyOutputRef ref) {
            if (ref == null) return null;
            return new ComfyOutputRef(ref.filename(), ref.subfolder(), ref.type());
        }

        /**
         * Converts this reference to the polling service representation.
         *
         * @return the polling service record
         */
        public ComfyJobPollingService.ComfyOutputRef toPollingRef() {
            return new ComfyJobPollingService.ComfyOutputRef(filename, subfolder, type);
        }
    }

    private final HttpClient httpClient;
    private final ConfigService configService;
    private ProcessTracker processTracker;
    private IComfyLifecycleService lifecycleService;
    private final MediaTranscodingService mediaTranscodingService;
    private final VideoWorkflowBuilder workflowBuilder;
    private final ComfyJobPollingService jobPollingService;
    private final ScenePipelineRunner scenePipelineRunner;

    /**
     * Minimal constructor using configuration defaults.
     *
     * @param configService the configuration service
     */
    public ComfyPipelineService(ConfigService configService) {
        this(configService, (HttpClient) null);
    }

    /**
     * Constructor specifying a custom HTTP client.
     *
     * @param configService the configuration service
     * @param httpClient    the HTTP client
     */
    public ComfyPipelineService(ConfigService configService, HttpClient httpClient) {
        this(configService, httpClient, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Constructor injecting a custom ScenePipelineRunner.
     *
     * @param configService       the configuration service
     * @param scenePipelineRunner the scene pipeline runner
     */
    public ComfyPipelineService(ConfigService configService, ScenePipelineRunner scenePipelineRunner) {
        this(configService, null, null, null, null, null, null, null, null, null, null, null, null, null, scenePipelineRunner, null);
    }

    /**
     * Primary Spring-managed constructor with autowired dependencies.
     */
    @Autowired
    public ComfyPipelineService(
            ConfigService configService,
            @Autowired(required = false) ScenePipelineRunner scenePipelineRunner,
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
                promptBlueprintApiService, comfyTemplateService, mediaTranscodingService,
                workflowTransformationService, null, null, null, null, scenePipelineRunner, null);
    }

    /**
     * Overloaded constructor with hardware profile service for legacy integration tests.
     */
    public ComfyPipelineService(
            ConfigService configService, HttpClient httpClient, ProcessTracker processTracker,
            IComfyLifecycleService lifecycleService, IModelArchitectureService modelArchitectureService,
            EnvironmentBootstrapperImpl bootstrapper, PromptBlueprintApiService promptBlueprintApiService,
            IComfyTemplateService comfyTemplateService, MediaTranscodingService mediaTranscodingService,
            WorkflowTransformationService workflowTransformationService, IHardwareProfileService hardwareProfileService) {
        this(configService, httpClient, processTracker, lifecycleService, modelArchitectureService, bootstrapper,
                promptBlueprintApiService, comfyTemplateService, mediaTranscodingService, workflowTransformationService,
                hardwareProfileService, null, null, null, null, null);
    }

    /**
     * Master constructor initializing all services and dependencies.
     */
    public ComfyPipelineService(
            ConfigService configService, HttpClient httpClient, ProcessTracker processTracker,
            IComfyLifecycleService lifecycleService, IModelArchitectureService modelArchitectureService,
            EnvironmentBootstrapperImpl bootstrapper, PromptBlueprintApiService promptBlueprintApiService,
            IComfyTemplateService comfyTemplateService, MediaTranscodingService mediaTranscodingService,
            WorkflowTransformationService workflowTransformationService, IHardwareProfileService hardwareProfileService,
            ComfyNodeInspectionClient inspectionClient, SceneSimulationService simulationService,
            VideoWorkflowBuilder workflowBuilder, ScenePipelineRunner scenePipelineRunner,
            ComfyJobPollingService jobPollingService) {
        this.configService = configService;
        this.httpClient = httpClient != null ? httpClient : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        this.processTracker = processTracker;
        this.lifecycleService = lifecycleService;
        this.mediaTranscodingService = mediaTranscodingService;
        ComfyNodeInspectionClient effInspection = inspectionClient != null ? inspectionClient : new ComfyNodeInspectionClient(this.httpClient);
        SceneSimulationService effSimulation = simulationService != null ? simulationService : new SceneSimulationService(processTracker);
        this.workflowBuilder = workflowBuilder != null ? workflowBuilder : new VideoWorkflowBuilder(effInspection, modelArchitectureService, hardwareProfileService, promptBlueprintApiService, comfyTemplateService, workflowTransformationService);
        this.jobPollingService = jobPollingService != null ? jobPollingService : new ComfyJobPollingService(this.httpClient);
        this.scenePipelineRunner = scenePipelineRunner != null ? scenePipelineRunner : new ScenePipelineRunner(
                configService, effSimulation, mediaTranscodingService, this.workflowBuilder,
                this.jobPollingService, effInspection, bootstrapper, lifecycleService, processTracker, workflowTransformationService);
    }

    /**
     * Converts UI workflow to API prompt JSON.
     *
     * @param uiWorkflow the UI workflow
     * @return converted API prompt JSON
     */
    public static JSONObject convertUiToApi(JSONObject uiWorkflow) {
        return DEFAULT_TRANSFORMATION_SERVICE.convertUiToApi(uiWorkflow);
    }

    /**
     * Flattens subgraphs in the UI workflow.
     *
     * @param uiWorkflow the UI workflow
     * @return flattened UI workflow
     */
    public static JSONObject flattenWorkflow(JSONObject uiWorkflow) {
        return DEFAULT_TRANSFORMATION_SERVICE.flattenWorkflow(uiWorkflow);
    }

    /**
     * Converts image and audio to video (1080p).
     *
     * @param imageFile       image file
     * @param audioFile       audio file
     * @param durationSeconds duration in seconds
     * @param fps             frames per second
     * @param outputFile      output file
     * @return generated video file
     * @throws Exception on error
     */
    public File convertImageToVideo(File imageFile, File audioFile, float durationSeconds, float fps, File outputFile) throws Exception {
        return convertImageToVideo(imageFile, audioFile, durationSeconds, fps, outputFile, 1920, 1080);
    }

    /**
     * Converts image and audio to video with target dimensions.
     *
     * @param imageFile       image file
     * @param audioFile       audio file
     * @param durationSeconds duration in seconds
     * @param fps             frames per second
     * @param outputFile      output file
     * @param targetWidth     target width
     * @param targetHeight    target height
     * @return generated video file
     * @throws Exception on error
     */
    public File convertImageToVideo(File imageFile, File audioFile, float durationSeconds, float fps, File outputFile, int targetWidth, int targetHeight) throws Exception {
        if (mediaTranscodingService != null) {
            return mediaTranscodingService.convertImageToVideo(imageFile, audioFile, durationSeconds, fps, outputFile, targetWidth, targetHeight);
        }
        return new MediaTranscodingService(configService).convertImageToVideo(imageFile, audioFile, durationSeconds, fps, outputFile, targetWidth, targetHeight);
    }

    /**
     * Generates a video for the scene with fallback simulation on error.
     *
     * @param scene the scene
     * @return CompletableFuture with the generated video file
     */
    public CompletableFuture<File> generateScene(Scene scene) {
        return scenePipelineRunner.executeScene(scene, false, this.lifecycleService, this.processTracker);
    }

    /**
     * Generates a video for the scene in strict mode without synthetic fallback.
     *
     * @param scene the scene
     * @return CompletableFuture with the generated video file
     */
    public CompletableFuture<File> generateSceneStrict(Scene scene) {
        return scenePipelineRunner.executeScene(scene, true, this.lifecycleService, this.processTracker);
    }

    /**
     * Generates a high-resolution keyframe image for the given scene (Pass 1 of two-phase rendering).
     *
     * @param scene the scene domain model
     * @return CompletableFuture resolving to the generated keyframe image file
     */
    public CompletableFuture<File> generateKeyframe(Scene scene) {
        return scenePipelineRunner.executeKeyframe(scene);
    }


    /**
     * Generates a montage scene using existing source clips where applicable.
     *
     * @param scene the scene
     * @return CompletableFuture with the generated video file
     */
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

    /**
     * Sanitizes workflow nodes against server models and available checkpoints.
     *
     * @param workflowJson workflow JSON
     * @param serverUrl    server URL
     */
    public void sanitizeWorkflow(JSONObject workflowJson, String serverUrl) {
        workflowBuilder.sanitizeWorkflow(workflowJson, serverUrl);
    }

    /**
     * Polls ComfyUI history for completion and returns output filename.
     *
     * @param serverUrl server URL
     * @param promptId  prompt ID
     * @return output filename
     * @throws IOException          on I/O error
     * @throws InterruptedException on interrupt
     */
    public String pollHistoryForFilename(String serverUrl, String promptId) throws IOException, InterruptedException {
        return jobPollingService.pollHistoryForFilename(serverUrl, promptId);
    }

    /**
     * Polls ComfyUI history and returns output reference.
     *
     * @param serverUrl server URL
     * @param promptId  prompt ID
     * @return output reference
     * @throws IOException          on I/O error
     * @throws InterruptedException on interrupt
     */
    public ComfyOutputRef pollHistoryForOutput(String serverUrl, String promptId) throws IOException, InterruptedException {
        return ComfyOutputRef.from(jobPollingService.pollHistoryForOutput(serverUrl, promptId));
    }

    /**
     * Uploads a file to ComfyUI.
     *
     * @param serverUrl server URL
     * @param file      file to upload
     * @return uploaded filename
     * @throws IOException          on I/O error
     * @throws InterruptedException on interrupt
     */
    public String uploadFile(String serverUrl, File file) throws IOException, InterruptedException {
        return jobPollingService.uploadFile(serverUrl, file);
    }

    private JSONObject generateWanWorkflowJson(String serverUrl, Scene scene, String uploadedSpeakerImage, long seed, String filenamePrefix) {
        return workflowBuilder.generateWanWorkflowJson(serverUrl, scene, uploadedSpeakerImage, seed, filenamePrefix);
    }

    private void injectSpeakerImage(JSONObject workflowJson, String filename) {
        scenePipelineRunner.injectSpeakerImage(workflowJson, filename);
    }

    private void injectAudio(JSONObject workflowJson, String filename) {
        scenePipelineRunner.injectAudio(workflowJson, filename);
    }
}
