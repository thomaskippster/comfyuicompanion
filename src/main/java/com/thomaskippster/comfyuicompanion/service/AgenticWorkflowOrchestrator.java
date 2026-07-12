package com.thomaskippster.comfyuicompanion.service;

import com.thomaskippster.comfyuicompanion.client.ComfyHttpClient;
import com.thomaskippster.comfyuicompanion.domain.graph.ComfyWorkflow;
import com.thomaskippster.comfyuicompanion.domain.llm.GenerationIntent;
import com.thomaskippster.comfyuicompanion.service.graph.WorkflowBuilder;
import com.thomaskippster.comfyuicompanion.service.inspector.SafetensorsInspectorService;
import com.thomaskippster.comfyuicompanion.service.llm.LlmRoutingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.thomaskippster.comfyuicompanion.service.provisioning.ResourceGapAnalyzer;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class AgenticWorkflowOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(AgenticWorkflowOrchestrator.class);

    private final LlmRoutingService llmRoutingService;
    private final SafetensorsInspectorService inspectorService;
    private final ComfyHttpClient comfyHttpClient;
    private final ResourceGapAnalyzer gapAnalyzer;

    public AgenticWorkflowOrchestrator(LlmRoutingService llmRoutingService,
                                       SafetensorsInspectorService inspectorService,
                                       ComfyHttpClient comfyHttpClient,
                                       ResourceGapAnalyzer gapAnalyzer) {
        this.llmRoutingService = llmRoutingService;
        this.inspectorService = inspectorService;
        this.comfyHttpClient = comfyHttpClient;
        this.gapAnalyzer = gapAnalyzer;
    }

    /**
     * Das Herzstück der Anwendung. Übersetzt rohe Nutzerwünsche durch das LLM in 
     * einen validierten, dynamischen ComfyUI-Ausführungsgraphen.
     */
    public Mono<String> orchestrateAndRun(String userInput) {
        // --- Schritt A: Intent Parsing ---
        logger.info("Routing user request to local LLM: {}", userInput);
        GenerationIntent intent = llmRoutingService.parseUserRequest(userInput);
        logger.info("Parsed Intent - Arch: {}, Prompt: {}, Faces: {}", 
                intent.getArchitecture(), intent.getOptimizedPositivePrompt(), intent.isRequiresFaceDetailer());

        // --- Schritt B: Hardware-Check & Model-Selection ---
        com.thomaskippster.comfyuicompanion.service.provisioning.MissingResourceReport report = gapAnalyzer.analyze(intent);
        
        String finalModelName;
        if (!report.isMissing() && report.getResolvedModelName() != null) {
            // Windows path separator replace just in case, ComfyUI needs forward slashes or double backslashes
            finalModelName = report.getResolvedModelName().replace("\\", "\\\\");
            logger.info("Auto-Provisioning: Nutze bestes lokal verfügbares Modell '{}' für Architektur '{}'", finalModelName, intent.getArchitecture());
        } else {
            String keyword = intent.getSuggestedCheckpointKeyword() != null ? intent.getSuggestedCheckpointKeyword() : "default";
            finalModelName = keyword + "_" + intent.getArchitecture() + ".safetensors";
            logger.info("Auto-Provisioning: Kein lokales Modell gefunden. Fallback-Name '{}' wird für Download reserviert.", finalModelName);
        }

        // --- Schritt C: Dynamischer Graph-Bau ---
        // Der Builder ist zustandsbehaftet für diesen spezifischen Workflow
        WorkflowBuilder builder = new WorkflowBuilder();
        
        // 1. Base Nodes
        String checkpointId = builder.addNode("CheckpointLoaderSimple")
                .param("ckpt_name", finalModelName)
                .getId();

        String clipSourceId = checkpointId;
        int clipSourceIndex = 1;

        if ("FLUX".equalsIgnoreCase(intent.getArchitecture())) {
            logger.info("FLUX architecture detected. Injecting CLIPLoader (T5XXL) to prevent 'clip input is invalid: None' errors.");
            // Nutzen wir den single CLIPLoader mit ltxv/t5, da wir wissen, dass der User clip_l.safetensors nicht hat
            clipSourceId = builder.addNode("CLIPLoader")
                    .param("clip_name", "t5\\t5xxl_fp16.safetensors")
                    .param("type", "ltxv") // 'ltxv' erlaubt t5xxl_fp16 im ComfyUI
                    .getId();
            clipSourceIndex = 0;
        }

        String positiveId = builder.addNode("CLIPTextEncode")
                .param("text", intent.getOptimizedPositivePrompt())
                .link("clip", clipSourceId, clipSourceIndex)
                .getId();

        String negativeId = builder.addNode("CLIPTextEncode")
                .param("text", "ugly, blurry, deformed, low quality")
                .link("clip", clipSourceId, clipSourceIndex)
                .getId();

        // 2. Aspect Ratio / Resolution logic
        int width = 1024, height = 1024;
        if ("16:9".equals(intent.getTargetAspectRatio())) {
            width = 1344; height = 768;
        }

        String emptyLatentId = builder.addNode("EmptyLatentImage")
                .param("width", width).param("height", height).param("batch_size", 1).getId();

        String samplerId = builder.addNode("KSampler")
                .param("seed", System.currentTimeMillis() % 1000000)
                .param("steps", 20).param("cfg", 7.0)
                .param("sampler_name", "euler").param("scheduler", "normal").param("denoise", 1.0)
                .link("model", checkpointId, 0)
                .link("positive", positiveId, 0)
                .link("negative", negativeId, 0)
                .link("latent_image", emptyLatentId, 0).getId();

        String vaeDecodeId = builder.addNode("VAEDecode")
                .link("samples", samplerId, 0).link("vae", checkpointId, 2).getId();

        // 3. Conditional Routing: FaceDetailer
        String finalOutputLink = vaeDecodeId; // Defaultmäßig geht das Bild direkt vom VAE in den SaveImage Node
        
        if (intent.isRequiresFaceDetailer()) {
            logger.info("Conditional Routing: Injecting FaceDetailer and BboxDetector nodes.");
            
            String bboxDetectorId = builder.addNode("BboxDetectorSEGS")
                    .param("detector_model", "bbox/face_yolov8m.pt").getId();

            String faceDetailerId = builder.addNode("FaceDetailer")
                    .param("guide_size", 256)
                    .param("guide_size_for", true)
                    .param("max_size", 768)
                    .param("seed", System.currentTimeMillis() % 1000000)
                    .param("steps", 20).param("cfg", 8.0)
                    .param("sampler_name", "euler").param("scheduler", "normal").param("denoise", 0.5)
                    .link("image", vaeDecodeId, 0) // Baut auf dem VAE Output auf
                    .link("model", checkpointId, 0)
                    .link("clip", clipSourceId, clipSourceIndex)
                    .link("vae", checkpointId, 2)
                    .link("positive", positiveId, 0)
                    .link("negative", negativeId, 0)
                    .link("bbox_detector", bboxDetectorId, 0).getId();
            
            // Verbiege den finalen Link, sodass SaveImage das FaceDetailer-Ergebnis abspeichert
            finalOutputLink = faceDetailerId; 
        }

        builder.addNode("SaveImage")
                .param("filename_prefix", "Cognitive_Router")
                .link("images", finalOutputLink, 0);

        // --- Schritt D: Ausführung via Auto-Provisioning Pipeline ---
        ComfyWorkflow workflow = builder.build();
        String clientId = UUID.randomUUID().toString();
        
        logger.info("Übergabe an Auto-Provisioning Engine... (ClientID: {})", clientId);
        // Wir nutzen den ResourceGapAnalyzer, der fehlende Modelle ggf. herunterlädt 
        // und erst danach den Graphen asynchron ausführt.
        return gapAnalyzer.resolveGapAndExecute(intent, workflow, clientId);
    }
}
