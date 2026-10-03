package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tki.comfyuicompanion.domain.CivitaiGenerationResult;
import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.ICivitaiHubService;
import de.tki.comfyuicompanion.service.ICivitaiOperationalModelService;
import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import de.tki.comfyuicompanion.service.IModelArchitectureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * Enterprise implementation of {@link ICivitaiOperationalModelService}.
 * Resolves all complementary base models, VAEs, and text encoders required to operate
 * models browsed in the Model Hub, synthesizes runnable ComfyUI workflows,
 * and coordinates lifecycle starting and queue submission.
 */
@Service
public class CivitaiOperationalModelService implements ICivitaiOperationalModelService {

    private static final Logger log = LoggerFactory.getLogger(CivitaiOperationalModelService.class);

    private final ConfigService configService;
    private final ICivitaiHubService civitaiHubService;
    private final IModelArchitectureService architectureService;
    private final ComfyApiClient comfyApiClient;
    private final IComfyLifecycleService lifecycleService;
    private final ObjectMapper objectMapper;
    private final CivitaiGuiWorkflowBuilder guiWorkflowBuilder;
    private final HttpClient httpClient;

    /**
     * Constructs a new CivitaiOperationalModelService with required and optional dependencies.
     *
     * @param configService       configuration service for directory paths and settings
     * @param civitaiHubService   hub service for primary model conversions
     * @param architectureService architecture classification service
     * @param comfyApiClient      API client for ComfyUI communication
     * @param lifecycleService    optional lifecycle service for auto-booting ComfyUI
     * @param objectMapper        Jackson object mapper for JSON operations
     * @param guiWorkflowBuilder  builder for synthesizing LiteGraph GUI format workflows
     */
    @Autowired
    public CivitaiOperationalModelService(
            ConfigService configService,
            ICivitaiHubService civitaiHubService,
            IModelArchitectureService architectureService,
            ComfyApiClient comfyApiClient,
            @Autowired(required = false) IComfyLifecycleService lifecycleService,
            ObjectMapper objectMapper,
            @Autowired(required = false) CivitaiGuiWorkflowBuilder guiWorkflowBuilder) {
        this.configService = configService;
        this.civitaiHubService = civitaiHubService;
        this.architectureService = architectureService;
        this.comfyApiClient = comfyApiClient;
        this.lifecycleService = lifecycleService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.guiWorkflowBuilder = guiWorkflowBuilder != null ? guiWorkflowBuilder : new CivitaiGuiWorkflowBuilder(this.objectMapper);
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public List<ModelInfo> resolveRequiredModels(CivitaiModelEntry entry) {
        if (entry == null) {
            return Collections.emptyList();
        }

        List<ModelInfo> required = new ArrayList<>();

        // 1. Primary Model (the Civitai entry itself)
        ModelInfo primary = civitaiHubService.createModelInfoForDownload(entry);
        checkAndSetModelStatus(primary);
        required.add(primary);

        String typeLower = entry.type() != null ? entry.type().toLowerCase(Locale.ROOT) : "checkpoint";
        String baseLower = entry.baseModel() != null ? entry.baseModel().toLowerCase(Locale.ROOT) : "";

        // 2. Resolve Operational Dependencies based on Type and Architecture
        if (typeLower.contains("lora") || typeLower.contains("locon") || typeLower.contains("controlnet")) {
            resolveLoraAndControlDependencies(entry, baseLower, required);
        } else if (typeLower.contains("checkpoint")) {
            resolveCheckpointDependencies(baseLower, required);
        }

        return required;
    }

    private void resolveLoraAndControlDependencies(CivitaiModelEntry entry, String baseLower, List<ModelInfo> required) {
        if (baseLower.contains("flux")) {
            // Flux LoRA requires Flux UNET/Checkpoint + ae.safetensors + clip_l + t5xxl
            ModelInfo baseUnet = findLocalModelMatching("flux", "unet", "diffusion_models", "checkpoints");
            if (baseUnet == null) {
                baseUnet = createRemoteModelInfo(
                        "flux1-dev-fp8.safetensors",
                        "unet",
                        "11.9 GB",
                        "https://huggingface.co/Comfy-Org/flux1-dev/resolve/main/flux1-dev-fp8.safetensors",
                        "FLUX.1 Dev FP8 Base UNET (Required by Flux LoRA)"
                );
            }
            checkAndSetModelStatus(baseUnet);
            required.add(baseUnet);

            ModelInfo vae = findLocalModelMatching("ae.safetensors", "vae");
            if (vae == null) {
                vae = createRemoteModelInfo(
                        "ae.safetensors",
                        "vae",
                        "335 MB",
                        "https://huggingface.co/black-forest-labs/FLUX.1-dev/resolve/main/ae.safetensors",
                        "FLUX.1 VAE (Required by Flux pipeline)"
                );
            }
            checkAndSetModelStatus(vae);
            required.add(vae);

            ModelInfo clipL = findLocalModelMatching("clip_l.safetensors", "clip", "text_encoders");
            if (clipL == null) {
                clipL = createRemoteModelInfo(
                        "clip_l.safetensors",
                        "clip",
                        "246 MB",
                        "https://huggingface.co/comfyanonymous/flux_text_encoders/resolve/main/clip_l.safetensors",
                        "OpenCLIP ViT-L/14 (Required by Flux text conditioning)"
                );
            }
            checkAndSetModelStatus(clipL);
            required.add(clipL);

            ModelInfo t5 = findLocalModelMatching("t5xxl", "clip", "text_encoders");
            if (t5 == null) {
                t5 = createRemoteModelInfo(
                        "t5xxl_fp8_e4m3fn.safetensors",
                        "clip",
                        "4.8 GB",
                        "https://huggingface.co/comfyanonymous/flux_text_encoders/resolve/main/t5xxl_fp8_e4m3fn.safetensors",
                        "T5-XXL FP8 Text Encoder (Required by Flux text conditioning)"
                );
            }
            checkAndSetModelStatus(t5);
            required.add(t5);

        } else if (baseLower.contains("sdxl") || baseLower.contains("pony")) {
            // SDXL / Pony LoRA requires an SDXL Checkpoint
            ModelInfo baseSdxl = findLocalModelMatching("sd_xl_base", "checkpoints");
            if (baseSdxl == null) {
                baseSdxl = findLocalModelMatching("sdxl", "checkpoints");
            }
            if (baseSdxl == null && baseLower.contains("pony")) {
                baseSdxl = findLocalModelMatching("pony", "checkpoints");
            }
            if (baseSdxl == null) {
                baseSdxl = createRemoteModelInfo(
                        "sd_xl_base_1.0.safetensors",
                        "checkpoints",
                        "6.94 GB",
                        "https://huggingface.co/stabilityai/stable-diffusion-xl-base-1.0/resolve/main/sd_xl_base_1.0.safetensors",
                        "Stable Diffusion XL Base 1.0 (Required by SDXL LoRA)"
                );
            }
            checkAndSetModelStatus(baseSdxl);
            required.add(baseSdxl);

        } else if (baseLower.contains("sd 1.5") || baseLower.contains("sd1.5") || baseLower.contains("1.5")) {
            // SD 1.5 LoRA requires an SD 1.5 Checkpoint
            ModelInfo baseSd15 = findLocalModelMatching("v1-5", "checkpoints");
            if (baseSd15 == null) {
                baseSd15 = findLocalModelMatching("1.5", "checkpoints");
            }
            if (baseSd15 == null) {
                baseSd15 = createRemoteModelInfo(
                        "v1-5-pruned-emaonly.safetensors",
                        "checkpoints",
                        "4.27 GB",
                        "https://huggingface.co/runwayml/stable-diffusion-v1-5/resolve/main/v1-5-pruned-emaonly.safetensors",
                        "Stable Diffusion 1.5 Pruned Checkpoint (Required by SD 1.5 LoRA)"
                );
            }
            checkAndSetModelStatus(baseSd15);
            required.add(baseSd15);

        } else if (baseLower.contains("wan")) {
            // Wan video LoRA requires Wan 2.1 base model and text encoder
            ModelInfo wanBase = findLocalModelMatching("wan2.1", "diffusion_models", "unet");
            if (wanBase == null) {
                wanBase = createRemoteModelInfo(
                        "wan2.1_t2v_1.3B_bf16.safetensors",
                        "diffusion_models",
                        "2.7 GB",
                        "https://huggingface.co/Comfy-Org/Wan_2.1_ComfyUI_repackaged/resolve/main/split_files/diffusion_models/wan2.1_t2v_1.3B_bf16.safetensors",
                        "Wan 2.1 Diffusion Model (Required by Wan LoRA)"
                );
            }
            checkAndSetModelStatus(wanBase);
            required.add(wanBase);
        }
    }

    private void resolveCheckpointDependencies(String baseLower, List<ModelInfo> required) {
        if (baseLower.contains("flux")) {
            // Some Flux checkpoints are UNET only and need VAE & CLIP
            ModelInfo vae = findLocalModelMatching("ae.safetensors", "vae");
            if (vae == null) {
                vae = createRemoteModelInfo(
                        "ae.safetensors",
                        "vae",
                        "335 MB",
                        "https://huggingface.co/black-forest-labs/FLUX.1-dev/resolve/main/ae.safetensors",
                        "FLUX.1 VAE"
                );
            }
            checkAndSetModelStatus(vae);
            required.add(vae);

            ModelInfo clipL = findLocalModelMatching("clip_l.safetensors", "clip", "text_encoders");
            if (clipL == null) {
                clipL = createRemoteModelInfo(
                        "clip_l.safetensors",
                        "clip",
                        "246 MB",
                        "https://huggingface.co/comfyanonymous/flux_text_encoders/resolve/main/clip_l.safetensors",
                        "OpenCLIP ViT-L/14"
                );
            }
            checkAndSetModelStatus(clipL);
            required.add(clipL);

            ModelInfo t5 = findLocalModelMatching("t5xxl", "clip", "text_encoders");
            if (t5 == null) {
                t5 = createRemoteModelInfo(
                        "t5xxl_fp8_e4m3fn.safetensors",
                        "clip",
                        "4.8 GB",
                        "https://huggingface.co/comfyanonymous/flux_text_encoders/resolve/main/t5xxl_fp8_e4m3fn.safetensors",
                        "T5-XXL FP8 Text Encoder"
                );
            }
            checkAndSetModelStatus(t5);
            required.add(t5);
        }
    }

    @Override
    public boolean areAllModelsInstalled(List<ModelInfo> requiredModels) {
        if (requiredModels == null || requiredModels.isEmpty()) {
            return true;
        }
        for (ModelInfo info : requiredModels) {
            if (!isModelFilePresent(info)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<ModelInfo> getMissingModels(List<ModelInfo> requiredModels) {
        if (requiredModels == null || requiredModels.isEmpty()) {
            return Collections.emptyList();
        }
        List<ModelInfo> missing = new ArrayList<>();
        for (ModelInfo info : requiredModels) {
            if (!isModelFilePresent(info)) {
                missing.add(info);
            }
        }
        return missing;
    }

    @Override
    public String generateGuiWorkflowJson(CivitaiModelEntry entry, List<ModelInfo> requiredModels) {
        return guiWorkflowBuilder.buildGuiWorkflowJson(entry, requiredModels);
    }

    @Override
    public CompletableFuture<Boolean> sendWorkflowToComfyUI(CivitaiModelEntry entry, List<ModelInfo> requiredModels) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. Fail-fast verification: Ensure all required models are physically present on disk
                List<ModelInfo> missing = getMissingModels(requiredModels);
                if (!missing.isEmpty()) {
                    List<String> missingNames = missing.stream()
                            .map(m -> m.getFilename() != null && !m.getFilename().isBlank() ? m.getFilename() : m.getName())
                            .toList();
                    throw new IllegalStateException("Cannot send workflow because required model files are missing on disk: " + missingNames);
                }

                // 2. Ensure ComfyUI server is alive; start lifecycle service if needed
                ensureComfyUiHealthy();

                String comfyUrl = configService != null ? configService.getComfyUIUrl() : "http://localhost:8188";
                if (comfyUrl.endsWith("/")) {
                    comfyUrl = comfyUrl.substring(0, comfyUrl.length() - 1);
                }

                // 3. Trigger model cache refresh in ComfyUI
                try {
                    HttpRequest refreshReq = HttpRequest.newBuilder()
                            .uri(URI.create(comfyUrl + "/cmfc/refresh-models"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"force_reload\":false}"))
                            .timeout(Duration.ofSeconds(5))
                            .build();
                    httpClient.send(refreshReq, HttpResponse.BodyHandlers.discarding());
                    log.debug("Notified ComfyUI bridge to refresh model list before loading workflow.");
                } catch (Exception ex) {
                    log.debug("ComfyUI model refresh notification skipped: {}", ex.getMessage());
                }

                // 4. Synthesize GUI LiteGraph workflow
                ObjectNode guiGraphNode = guiWorkflowBuilder.buildGuiWorkflow(entry, requiredModels);

                // 5. Save copy to user_workflows directory for persistence
                saveWorkflowToUserWorkflows(entry, guiGraphNode);

                // 6. Post workflow to /cmfc/load-workflow
                ObjectNode bridgePayload = objectMapper.createObjectNode();
                bridgePayload.set("workflow", guiGraphNode);

                HttpRequest bridgeReq = HttpRequest.newBuilder()
                        .uri(URI.create(comfyUrl + "/cmfc/load-workflow"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(bridgePayload.toString()))
                        .timeout(Duration.ofSeconds(10))
                        .build();

                HttpResponse<String> response = httpClient.send(bridgeReq, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    log.info("Successfully loaded GUI workflow for '{}' into ComfyUI canvas via bridge.", entry.name());
                    return true;
                } else {
                    log.warn("ComfyUI bridge rejected /cmfc/load-workflow (HTTP {}): {}", response.statusCode(), response.body());
                    throw new IOException("ComfyUI bridge returned HTTP " + response.statusCode() + ": " + response.body());
                }
            } catch (Exception e) {
                log.error("Failed sending GUI workflow to ComfyUI: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, runnable -> Thread.ofVirtual().name("Civitai-SendWorkflowWorker").start(runnable));
    }

    private void saveWorkflowToUserWorkflows(CivitaiModelEntry entry, ObjectNode workflow) {
        try {
            Path userWorkflowsDir = Path.of("user_workflows");
            if (!Files.exists(userWorkflowsDir)) {
                Files.createDirectories(userWorkflowsDir);
            }
            String safeName = entry.name()
                    .toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9_-]+", "_")
                    .replaceAll("_+", "_");
            Path targetFile = userWorkflowsDir.resolve("model_" + safeName + ".json");
            Files.writeString(targetFile, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(workflow));
            log.debug("Saved synthesized GUI workflow copy to {}", targetFile);
        } catch (Exception ex) {
            log.warn("Could not save workflow backup copy to user_workflows: {}", ex.getMessage());
        }
    }

    @Override
    public String generateWorkflowJson(CivitaiModelEntry entry, List<ModelInfo> requiredModels, String userPrompt) {
        String baseLower = entry.baseModel() != null ? entry.baseModel().toLowerCase(Locale.ROOT) : "";
        String typeLower = entry.type() != null ? entry.type().toLowerCase(Locale.ROOT) : "checkpoint";

        String templateName;
        if (baseLower.contains("flux")) {
            templateName = "template_flux_api.json";
        } else if (baseLower.contains("sdxl") || baseLower.contains("pony")) {
            templateName = "template_sdxl_api.json";
        } else if (baseLower.contains("wan")) {
            templateName = "template_wan_api.json";
        } else if (baseLower.contains("hunyuan")) {
            templateName = "template_hunyuan_api.json";
        } else if (baseLower.contains("sd 3") || baseLower.contains("sd3")) {
            templateName = "template_sd3_api.json";
        } else {
            templateName = "template_sd15_api.json";
        }

        String rawTemplate = DefaultComfyTemplates.getDefaultTemplateForName(templateName);

        try {
            JsonNode root = objectMapper.readTree(rawTemplate);
            ObjectNode promptObj = root.has("prompt") ? (ObjectNode) root.get("prompt") : (ObjectNode) root;

            // 1. Identify and configure base model / UNET loader
            String activeModelName = entry.filename();
            if (typeLower.contains("lora") || typeLower.contains("locon")) {
                // Find the base model name among required dependencies
                for (ModelInfo info : requiredModels) {
                    String infoType = info.getType() != null ? info.getType().toLowerCase(Locale.ROOT) : "";
                    if (infoType.contains("checkpoint") || infoType.contains("unet") || infoType.contains("diffusion")) {
                        activeModelName = info.getFilename() != null ? info.getFilename() : info.getName();
                        break;
                    }
                }
            }

            String ckptNodeId = null;
            Iterator<String> nodeIds = promptObj.fieldNames();
            while (nodeIds.hasNext()) {
                String id = nodeIds.next();
                JsonNode node = promptObj.get(id);
                String classType = node.path("class_type").asText("");
                ObjectNode inputs = (ObjectNode) node.path("inputs");

                if ("CheckpointLoaderSimple".equals(classType)) {
                    inputs.put("ckpt_name", activeModelName);
                    ckptNodeId = id;
                } else if ("UNETLoader".equals(classType) || "UNetLoader".equals(classType)) {
                    inputs.put("unet_name", activeModelName);
                    ckptNodeId = id;
                }
            }

            // 2. If LoRA: Inject LoraLoader node and rewire connections
            if ((typeLower.contains("lora") || typeLower.contains("locon")) && ckptNodeId != null) {
                injectLoraNode(promptObj, entry.filename(), ckptNodeId, baseLower.contains("flux"));
            }

            // 3. Positive Prompt assembly
            String effectivePrompt;
            if (userPrompt != null && !userPrompt.isBlank()) {
                effectivePrompt = userPrompt.trim();
            } else if (entry.triggerWords() != null && !entry.triggerWords().isEmpty()) {
                effectivePrompt = String.join(", ", entry.triggerWords()) + ", masterpiece, high quality, 8k wallpaper";
            } else {
                effectivePrompt = entry.name() + ", masterpiece, high quality, highly detailed";
            }

            // Inject positive and negative prompt text
            injectPromptTexts(promptObj, effectivePrompt, "blurry, low quality, deformed, extra limbs, bad anatomy");

            // Randomize seeds
            randomizeSeeds(promptObj);

            ObjectNode wrapped = objectMapper.createObjectNode();
            wrapped.set("prompt", promptObj);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(wrapped);

        } catch (Exception ex) {
            log.error("Failed synthesizing ComfyUI workflow JSON: {}", ex.getMessage(), ex);
            return rawTemplate;
        }
    }

    private void injectLoraNode(ObjectNode promptObj, String loraFilename, String sourceNodeId, boolean isFlux) {
        String loraNodeId = "99";
        while (promptObj.has(loraNodeId)) {
            loraNodeId = String.valueOf(Integer.parseInt(loraNodeId) + 1);
        }

        ObjectNode loraNode = objectMapper.createObjectNode();
        loraNode.put("class_type", "LoraLoader");

        ObjectNode loraInputs = objectMapper.createObjectNode();
        loraInputs.put("lora_name", loraFilename);
        loraInputs.put("strength_model", 0.85);
        loraInputs.put("strength_clip", 0.85);

        ArrayNode modelLink = objectMapper.createArrayNode();
        modelLink.add(sourceNodeId);
        modelLink.add(0);
        loraInputs.set("model", modelLink);

        ArrayNode clipLink = objectMapper.createArrayNode();
        clipLink.add(isFlux ? "2" : sourceNodeId);
        clipLink.add(isFlux ? 0 : 1);
        loraInputs.set("clip", clipLink);

        loraNode.set("inputs", loraInputs);
        promptObj.set(loraNodeId, loraNode);

        // Rewire downstream model and clip references
        final String finalLoraId = loraNodeId;
        promptObj.fields().forEachRemaining(entry -> {
            if (entry.getKey().equals(finalLoraId)) return;
            JsonNode node = entry.getValue();
            if (node.has("inputs")) {
                ObjectNode inputs = (ObjectNode) node.get("inputs");
                // Rewire model
                if (inputs.has("model") && inputs.get("model").isArray()) {
                    ArrayNode arr = (ArrayNode) inputs.get("model");
                    if (arr.size() >= 2 && sourceNodeId.equals(arr.get(0).asText())) {
                        arr.set(0, finalLoraId);
                    }
                }
                // Rewire clip
                if (inputs.has("clip") && inputs.get("clip").isArray()) {
                    ArrayNode arr = (ArrayNode) inputs.get("clip");
                    if (arr.size() >= 2 && (sourceNodeId.equals(arr.get(0).asText()) || (isFlux && "2".equals(arr.get(0).asText())))) {
                        arr.set(0, finalLoraId);
                        arr.set(1, 1);
                    }
                }
            }
        });
    }

    private void injectPromptTexts(ObjectNode promptObj, String positive, String negative) {
        promptObj.fields().forEachRemaining(entry -> {
            JsonNode node = entry.getValue();
            if ("CLIPTextEncode".equals(node.path("class_type").asText())) {
                ObjectNode inputs = (ObjectNode) node.path("inputs");
                if (inputs != null && inputs.has("text")) {
                    String existing = inputs.path("text").asText().toLowerCase(Locale.ROOT);
                    if (existing.contains("bad") || existing.contains("blurry") || existing.contains("deformed")) {
                        inputs.put("text", negative);
                    } else {
                        inputs.put("text", positive);
                    }
                }
            }
        });
    }

    private void randomizeSeeds(ObjectNode promptObj) {
        long seed = Math.abs(ThreadLocalRandom.current().nextLong(1000000000L, 999999999999999L));
        promptObj.fields().forEachRemaining(entry -> {
            JsonNode node = entry.getValue();
            String classType = node.path("class_type").asText("");
            if (classType.contains("Sampler") || classType.contains("Noise")) {
                ObjectNode inputs = (ObjectNode) node.path("inputs");
                if (inputs != null) {
                    if (inputs.has("seed")) inputs.put("seed", seed);
                    if (inputs.has("noise_seed")) inputs.put("noise_seed", seed);
                }
            }
        });
    }

    private void ensureComfyUiHealthy() throws IOException, InterruptedException {
        if (lifecycleService != null && !lifecycleService.isHealthy()) {
            log.info("ComfyUI server is offline. Triggering lifecycle start...");
            lifecycleService.start();
            int maxWaitSec = 90;
            boolean online = false;
            for (int i = 0; i < maxWaitSec; i++) {
                if (lifecycleService.isHealthy()) {
                    online = true;
                    break;
                }
                Thread.sleep(1000);
            }
            if (!online) {
                throw new IOException("ComfyUI server did not start within 90 seconds.");
            }
        }
    }

    @Override
    public CompletableFuture<CivitaiGenerationResult> sendToComfyUIAndGenerate(CivitaiModelEntry entry, List<ModelInfo> requiredModels, String userPrompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Fail-fast verification: Ensure all required models are physically present on disk
                List<ModelInfo> missing = getMissingModels(requiredModels);
                if (!missing.isEmpty()) {
                    List<String> missingNames = missing.stream()
                            .map(m -> m.getFilename() != null && !m.getFilename().isBlank() ? m.getFilename() : m.getName())
                            .toList();
                    throw new IllegalStateException("Cannot queue ComfyUI generation because required model files are missing on disk: " + missingNames);
                }

                // 1. Ensure ComfyUI server is alive
                ensureComfyUiHealthy();

                String comfyUrl = configService != null ? configService.getComfyUIUrl() : "http://localhost:8188";
                if (comfyUrl.endsWith("/")) {
                    comfyUrl = comfyUrl.substring(0, comfyUrl.length() - 1);
                }

                String workflowJson = generateWorkflowJson(entry, requiredModels, userPrompt);
                JsonNode parsedTree = objectMapper.readTree(workflowJson);

                // 2. Load into ComfyUI canvas via Bridge (if active)
                try {
                    ObjectNode bridgePayload = objectMapper.createObjectNode();
                    bridgePayload.set("workflow", parsedTree);

                    HttpRequest bridgeReq = HttpRequest.newBuilder()
                            .uri(URI.create(comfyUrl + "/cmfc/load-workflow"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(bridgePayload.toString()))
                            .timeout(Duration.ofSeconds(6))
                            .build();
                    httpClient.send(bridgeReq, HttpResponse.BodyHandlers.ofString());
                } catch (Exception bridgeEx) {
                    log.debug("Bridge canvas sync bypassed: {}", bridgeEx.getMessage());
                }

                // 3. Submit directly to /prompt queue
                HttpResponse<String> promptResponse = comfyApiClient.postPrompt(comfyUrl, workflowJson);
                if (promptResponse == null || promptResponse.statusCode() < 200 || promptResponse.statusCode() >= 300) {
                    int status = promptResponse != null ? promptResponse.statusCode() : 0;
                    String body = promptResponse != null ? promptResponse.body() : "No response";
                    throw new IOException("ComfyUI /prompt rejected submission (HTTP " + status + "): " + body);
                }

                JsonNode resNode = objectMapper.readTree(promptResponse.body());
                String promptId = resNode.path("prompt_id").asText("");
                log.info("Successfully queued Civitai generation with prompt_id {} for {}", promptId, entry.name());

                // 4. Poll ComfyUI for generation completion (up to 90 seconds)
                ComfyApiClient.ImageOutput imgOutput = null;
                int maxAttempts = 180; // 180 * 500ms = 90s
                for (int i = 0; i < maxAttempts; i++) {
                    imgOutput = comfyApiClient.fetchPromptHistoryImage(comfyUrl, promptId);
                    if (imgOutput != null) {
                        break;
                    }
                    Thread.sleep(500);
                }

                if (imgOutput != null) {
                    String imgUrl = comfyUrl + "/view?filename=" + imgOutput.filename() + "&subfolder=" + imgOutput.subfolder() + "&type=" + imgOutput.type();
                    log.info("Civitai generation finished successfully: {}", imgOutput.filename());
                    return CivitaiGenerationResult.success(promptId, imgOutput.filename(), imgOutput.subfolder(), imgOutput.type(), imgUrl);
                }

                return CivitaiGenerationResult.success(promptId, null, null, null, null);

            } catch (Exception e) {
                log.error("Failed sending workflow to ComfyUI: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, runnable -> Thread.ofVirtual().name("Civitai-SendComfyWorker").start(runnable));
    }

    private void checkAndSetModelStatus(ModelInfo info) {
        boolean present = isModelFilePresent(info);
        info.setPopularity(present ? "installed" : "missing");
        info.setDescription(present ? "✅ Already exists" : "Missing");
    }

    @Override
    public boolean isModelFilePresent(ModelInfo info) {
        if (info == null) return false;
        String baseDir = configService != null ? configService.getModelsPath() : "models";
        String folder = info.getSave_path() != null ? info.getSave_path() : (info.getType() != null ? info.getType() : "checkpoints");
        String filename = info.getFilename() != null && !info.getFilename().isBlank() ? info.getFilename() : info.getName();

        Path direct = Path.of(baseDir, folder, filename);
        if (Files.exists(direct) && Files.isRegularFile(direct)) {
            return true;
        }

        // Subfolder search or alternative folder (e.g. unet vs diffusion_models)
        Path folderPath = Path.of(baseDir, folder);
        if (Files.exists(folderPath) && Files.isDirectory(folderPath)) {
            try (Stream<Path> stream = Files.walk(folderPath, 3)) {
                if (stream.anyMatch(p -> p.getFileName().toString().equalsIgnoreCase(filename))) {
                    return true;
                }
            } catch (IOException ignored) {}
        }

        return false;
    }

    private ModelInfo findLocalModelMatching(String pattern, String... folders) {
        String baseDir = configService != null ? configService.getModelsPath() : "models";
        String lowerPattern = pattern.toLowerCase(Locale.ROOT);

        for (String folder : folders) {
            Path dir = Path.of(baseDir, folder);
            if (!Files.exists(dir) || !Files.isDirectory(dir)) continue;

            try (Stream<Path> stream = Files.walk(dir, 3)) {
                Optional<Path> match = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).contains(lowerPattern))
                        .findFirst();

                if (match.isPresent()) {
                    Path file = match.get();
                    String name = file.getFileName().toString();
                    long sizeBytes = Files.size(file);
                    String formattedSize = formatBytes(sizeBytes);

                    ModelInfo info = new ModelInfo(folder, name, "LOCAL");
                    info.setFilename(name);
                    info.setSize(formattedSize);
                    info.setByteSize(sizeBytes);
                    info.setPopularity("installed");
                    info.setDescription("✅ Already exists");
                    return info;
                }
            } catch (IOException ignored) {}
        }
        return null;
    }

    private ModelInfo createRemoteModelInfo(String filename, String folder, String formattedSize, String url, String desc) {
        ModelInfo info = new ModelInfo(folder, filename, url);
        info.setFilename(filename);
        info.setSize(formattedSize);
        info.setDescription("Missing");
        info.setPopularity("missing");
        info.setReference(desc);
        return info;
    }

    private String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        } else if (bytes >= 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return bytes + " B";
        }
    }
}
