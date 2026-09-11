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

    private final com.thomaskippster.comfyuicompanion.service.llm.ILlmRoutingService llmRoutingService;
    private final SafetensorsInspectorService inspectorService;
    private final ComfyHttpClient comfyHttpClient;
    private final ResourceGapAnalyzer gapAnalyzer;

    public AgenticWorkflowOrchestrator(com.thomaskippster.comfyuicompanion.service.llm.ILlmRoutingService llmRoutingService,
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
        boolean hasResolvedModel = report != null && !report.isMissing() && report.getResolvedModelName() != null;
        if (hasResolvedModel) {
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

        // --- 2.5 Heuristik: KSampler Parameter für Turbo / LCM Modelle ---
        int steps = 20;
        double cfg = 7.0;
        String samplerName = "euler";
        String scheduler = "normal";

        String lowerModelName = finalModelName.toLowerCase();
        if (lowerModelName.contains("turbo") || lowerModelName.contains("lcm") || lowerModelName.contains("lightning") || "z_image_turbo_bf16.safetensors".equals(lowerModelName)) {
            logger.info("Turbo-Modell erkannt ({}). Reduziere Steps und CFG zur Vermeidung von Noise.", finalModelName);
            steps = 4;
            cfg = 1.5;
            samplerName = "lcm"; // Oft am besten für turbo/lcm, oder euler_ancestral
            scheduler = "sgm_uniform";
        }

        String samplerId = builder.addNode("KSampler")
                .param("seed", System.currentTimeMillis() % 1000000)
                .param("steps", steps).param("cfg", cfg)
                .param("sampler_name", samplerName).param("scheduler", scheduler).param("denoise", 1.0)
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
                    .param("steps", steps).param("cfg", cfg) // Nutze die gleichen Turbo-Settings!
                    .param("sampler_name", samplerName).param("scheduler", scheduler).param("denoise", 0.35)
                    .link("image", finalOutputLink, 0) // Baut auf dem bisherigen Output auf (VAE oder vorheriger Node)
                    .link("model", checkpointId, 0)
                    .link("clip", clipSourceId, clipSourceIndex)
                    .link("vae", checkpointId, 2)
                    .link("positive", positiveId, 0)
                    .link("negative", negativeId, 0)
                    .link("bbox_detector", bboxDetectorId, 0).getId();
            
            // Verbiege den finalen Link, sodass der nächste Knoten das FaceDetailer-Ergebnis abspeichert
            finalOutputLink = faceDetailerId; 
        }

        // 4. Conditional Routing: Upscaling
        if (intent.isRequiresUpscaling()) {
            logger.info("Conditional Routing: Injecting UltimateSDUpscale for high-resolution output.");
            
            String upscaleModelId = builder.addNode("UpscaleModelLoader")
                    .param("model_name", "4x-UltraSharp.pth").getId(); // Standard Upscaler Model (Beispiel)

            String ultimateUpscaleId = builder.addNode("UltimateSDUpscale")
                    .param("upscale_by", 2.0)
                    .param("seed", System.currentTimeMillis() % 1000000)
                    .param("steps", steps).param("cfg", cfg)
                    .param("sampler_name", samplerName).param("scheduler", scheduler).param("denoise", 0.2)
                    .param("mode_type", "Linear")
                    .param("tile_width", 512).param("tile_height", 512)
                    .param("mask_blur", 8).param("tile_padding", 32)
                    .param("seam_fix_mode", "None").param("seam_fix_denoise", 1.0)
                    .param("seam_fix_width", 64).param("seam_fix_mask_blur", 8)
                    .param("seam_fix_padding", 16).param("force_uniform_tiles", true)
                    .link("image", finalOutputLink, 0)
                    .link("model", checkpointId, 0)
                    .link("positive", positiveId, 0)
                    .link("negative", negativeId, 0)
                    .link("vae", checkpointId, 2)
                    .link("upscale_model", upscaleModelId, 0).getId();
            
            finalOutputLink = ultimateUpscaleId;
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
