package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Enterprise implementation of {@link ICivitaiOperationalModelService}.
 * Resolves all complementary base models, VAEs, and text encoders required to operate
 * workflows and models browsed in the Workflows Hub, synthesizes runnable ComfyUI workflows,
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
    private final CivitaiLocalModelScanner localModelScanner;
    private final CivitaiApiWorkflowBuilder apiWorkflowBuilder;
    private final CivitaiWorkflowDispatcher workflowDispatcher;
    private final CivitaiWorkflowFileResolver workflowFileResolver;
    private final de.tki.comfyuicompanion.service.workflow.WorkflowModelExtractor workflowModelExtractor;
    private final HttpClient httpClient;
    private final Map<String, List<ModelInfo>> resolvedModelsCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Constructs a new CivitaiOperationalModelService with all required and optional dependencies.
     *
     * @param configService          configuration service for directory paths and settings
     * @param civitaiHubService      hub service for primary model conversions
     * @param architectureService    architecture classification service
     * @param comfyApiClient         API client for ComfyUI communication
     * @param lifecycleService       optional lifecycle service for auto-booting ComfyUI
     * @param objectMapper           Jackson object mapper for JSON operations
     * @param guiWorkflowBuilder     builder for synthesizing LiteGraph GUI format workflows
     * @param localModelScanner      scanner for detecting installed model files on disk
     * @param apiWorkflowBuilder     builder for synthesizing ComfyUI API workflow payloads
     * @param workflowDispatcher     dispatcher for sending workflows to ComfyUI
     * @param workflowFileResolver   resolver for loading downloaded workflows
     * @param workflowModelExtractor extractor for parsing models from workflow graphs
     */
    @Autowired
    public CivitaiOperationalModelService(
            ConfigService configService,
            ICivitaiHubService civitaiHubService,
            IModelArchitectureService architectureService,
            ComfyApiClient comfyApiClient,
            @Autowired(required = false) IComfyLifecycleService lifecycleService,
            ObjectMapper objectMapper,
            @Autowired(required = false) CivitaiGuiWorkflowBuilder guiWorkflowBuilder,
            @Autowired(required = false) CivitaiLocalModelScanner localModelScanner,
            @Autowired(required = false) CivitaiApiWorkflowBuilder apiWorkflowBuilder,
            @Autowired(required = false) CivitaiWorkflowDispatcher workflowDispatcher,
            @Autowired(required = false) CivitaiWorkflowFileResolver workflowFileResolver,
            @Autowired(required = false) de.tki.comfyuicompanion.service.workflow.WorkflowModelExtractor workflowModelExtractor) {
        this.configService = configService;
        this.civitaiHubService = civitaiHubService;
        this.architectureService = architectureService;
        this.comfyApiClient = comfyApiClient;
        this.lifecycleService = lifecycleService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.localModelScanner = localModelScanner != null ? localModelScanner : new CivitaiLocalModelScanner(configService);
        this.apiWorkflowBuilder = apiWorkflowBuilder != null ? apiWorkflowBuilder : new CivitaiApiWorkflowBuilder(this.objectMapper, this.localModelScanner);
        this.guiWorkflowBuilder = guiWorkflowBuilder != null ? guiWorkflowBuilder : new CivitaiGuiWorkflowBuilder(this.objectMapper, configService);
        this.workflowDispatcher = workflowDispatcher != null ? workflowDispatcher : new CivitaiWorkflowDispatcher(configService, lifecycleService, comfyApiClient, this.guiWorkflowBuilder, this.objectMapper);
        this.workflowFileResolver = workflowFileResolver != null ? workflowFileResolver : new CivitaiWorkflowFileResolver(this.objectMapper, configService, null, this.localModelScanner);
        this.workflowModelExtractor = workflowModelExtractor != null ? workflowModelExtractor : new de.tki.comfyuicompanion.service.workflow.WorkflowModelExtractor(this.localModelScanner);
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Backward-compatible 10-argument constructor.
     */
    public CivitaiOperationalModelService(
            ConfigService configService, ICivitaiHubService civitaiHubService, IModelArchitectureService architectureService,
            ComfyApiClient comfyApiClient, IComfyLifecycleService lifecycleService, ObjectMapper objectMapper,
            CivitaiGuiWorkflowBuilder guiWorkflowBuilder, CivitaiLocalModelScanner localModelScanner,
            CivitaiApiWorkflowBuilder apiWorkflowBuilder, CivitaiWorkflowDispatcher workflowDispatcher) {
        this(configService, civitaiHubService, architectureService, comfyApiClient, lifecycleService, objectMapper,
                guiWorkflowBuilder, localModelScanner, apiWorkflowBuilder, workflowDispatcher, null, null);
    }

    /**
     * Convenience constructor supporting earlier 7-argument calls used in tests.
     */
    public CivitaiOperationalModelService(
            ConfigService configService, ICivitaiHubService civitaiHubService, IModelArchitectureService architectureService,
            ComfyApiClient comfyApiClient, IComfyLifecycleService lifecycleService, ObjectMapper objectMapper,
            CivitaiGuiWorkflowBuilder guiWorkflowBuilder) {
        this(configService, civitaiHubService, architectureService, comfyApiClient, lifecycleService, objectMapper, guiWorkflowBuilder, null, null, null);
    }

    @Override
    public List<ModelInfo> resolveRequiredModels(CivitaiModelEntry entry) {
        if (entry == null) {
            return Collections.emptyList();
        }
        String cacheKey = entry.id() + ":" + (entry.filename() != null ? entry.filename() : entry.name());
        List<ModelInfo> cached = resolvedModelsCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<ModelInfo> required = new ArrayList<>();

        // 1. Primary Model (the Civitai entry itself)
        ModelInfo primary = civitaiHubService.createModelInfoForDownload(entry);
        localModelScanner.checkAndSetModelStatus(primary);
        if ("missing".equals(primary.getPopularity()) && canBeSatisfiedBySplitComponents(primary)) {
            primary.setPopularity("installed");
            primary.setDescription("Ready (Split Components)");
        }
        required.add(primary);

        String typeLower = entry.type() != null ? entry.type().toLowerCase(Locale.ROOT) : "checkpoint";
        String baseLower = entry.baseModel() != null ? entry.baseModel().toLowerCase(Locale.ROOT) : "";

        // 2. Resolve Operational Dependencies based on Type and Architecture
        if (typeLower.contains("workflow")) {
            List<ModelInfo> extracted = extractWorkflowModelsIfAvailable(entry);
            localModelScanner.checkAndSetModelStatus(primary);
            if (!extracted.isEmpty()) {
                required.addAll(extracted);
                List<ModelInfo> result = Collections.unmodifiableList(required);
                resolvedModelsCache.put(cacheKey, result);
                return result;
            }
            resolveLoraAndControlDependencies(entry, baseLower, required);
        } else if (typeLower.contains("lora") || typeLower.contains("locon") || typeLower.contains("controlnet")
                || typeLower.contains("textualinversion")) {
            resolveLoraAndControlDependencies(entry, baseLower, required);
        } else if (typeLower.contains("checkpoint")) {
            resolveCheckpointDependencies(baseLower, required);
        }

        List<ModelInfo> result = Collections.unmodifiableList(required);
        resolvedModelsCache.put(cacheKey, result);
        return result;
    }

    private List<ModelInfo> extractWorkflowModelsIfAvailable(CivitaiModelEntry entry) {
        if (workflowFileResolver == null || workflowModelExtractor == null) {
            return Collections.emptyList();
        }
        try {
            var realWf = workflowFileResolver.tryResolveRealWorkflow(entry);
            if (realWf.isPresent()) {
                List<ModelInfo> models = workflowModelExtractor.extractModels(realWf.get());
                if (!models.isEmpty()) {
                    log.debug("Extracted {} concrete model dependencies from workflow '{}'", models.size(), entry.filename());
                    return models;
                }
            }
        } catch (Exception ex) {
            log.debug("Workflow model extraction skipped: {}", ex.getMessage());
        }
        return Collections.emptyList();
    }

    private void resolveLoraAndControlDependencies(CivitaiModelEntry entry, String baseLower, List<ModelInfo> required) {
        if (baseLower.contains("flux")) {
            resolveFluxDependencies(required, baseLower);
        } else if (baseLower.contains("moss")) {
            resolveMossDependencies(required);
        } else if (baseLower.contains("sdxl") || baseLower.contains("pony")) {
            resolveSdxlDependencies(baseLower, required);
        } else if (baseLower.contains("sd 1.5") || baseLower.contains("sd1.5") || baseLower.contains("1.5")) {
            resolveSd15Dependencies(required);
        } else if (baseLower.contains("wan")) {
            resolveWanDependencies(required);
        } else if (baseLower.contains("ltx")) {
            resolveLtxDependencies(required);
        } else if (baseLower.contains("hunyuan")) {
            resolveHunyuanDependencies(required);
        } else {
            resolveGenericLoraDependencies(baseLower, required);
        }
    }

    private void resolveLtxDependencies(List<ModelInfo> required) {
        ModelInfo ltx = localModelScanner.findLocalModelMatching("ltx", "checkpoints");
        if (ltx == null) ltx = localModelScanner.createRemoteModelInfo("ltx-2.3-22b-dev-nvfp4.safetensors", "checkpoints", "13.2 GB", "https://huggingface.co/Lightricks/LTX-Video/resolve/main/ltx-2.3-22b-dev-nvfp4.safetensors", "LTX-Video Checkpoint");
        localModelScanner.checkAndSetModelStatus(ltx); required.add(ltx);
    }

    private void resolveHunyuanDependencies(List<ModelInfo> required) {
        ModelInfo hy = localModelScanner.findLocalModelMatching("hunyuan", "diffusion_models", "checkpoints");
        if (hy == null) hy = localModelScanner.createRemoteModelInfo("hunyuan_video_720_cfgdistill_bf16.safetensors", "diffusion_models", "12.8 GB", "https://huggingface.co/tencent/HunyuanVideo/resolve/main/hunyuan_video_720_cfgdistill_bf16.safetensors", "Hunyuan Video Model");
        localModelScanner.checkAndSetModelStatus(hy); required.add(hy);
        ModelInfo clipL = localModelScanner.findLocalModelMatching("clip_l", "clip", "text_encoders");
        if (clipL == null) clipL = localModelScanner.createRemoteModelInfo("clip_l.safetensors", "clip", "246 MB", "https://huggingface.co/comfyanonymous/flux_text_encoders/resolve/main/clip_l.safetensors", "OpenCLIP ViT-L/14");
        localModelScanner.checkAndSetModelStatus(clipL); required.add(clipL);
        ModelInfo t5 = localModelScanner.findLocalModelMatching("t5xxl", "clip", "text_encoders");
        if (t5 == null) t5 = localModelScanner.createRemoteModelInfo("t5xxl_fp8_e4m3fn.safetensors", "clip", "4.8 GB", "https://huggingface.co/comfyanonymous/flux_text_encoders/resolve/main/t5xxl_fp8_e4m3fn.safetensors", "T5-XXL FP8 Text Encoder");
        localModelScanner.checkAndSetModelStatus(t5); required.add(t5);
        ModelInfo vae = localModelScanner.findLocalModelMatching("ae.", "vae");
        if (vae == null) vae = localModelScanner.createRemoteModelInfo("ae.safetensors", "vae", "335 MB", "https://huggingface.co/black-forest-labs/FLUX.1-dev/resolve/main/ae.safetensors", "FLUX / Hunyuan VAE");
        localModelScanner.checkAndSetModelStatus(vae); required.add(vae);
    }

    private void resolveMossDependencies(List<ModelInfo> required) {
        ModelInfo m = localModelScanner.findLocalModelMatching("moss_image", "diffusion_models", "unet", "checkpoints");
        if (m == null) m = localModelScanner.createRemoteModelInfo("moss_image_sft_int8_convrot.safetensors", "diffusion_models", "6.8 GB", "https://huggingface.co/Comfy-Org/MOSS-Image/resolve/main/diffusion_models/moss_image_sft_int8_convrot.safetensors", "MOSS-Image Diffusion Model");
        localModelScanner.checkAndSetModelStatus(m); required.add(m);
        ModelInfo q = localModelScanner.findLocalModelMatching("qwen3.5_9b", "text_encoders", "clip");
        if (q == null) q = localModelScanner.createRemoteModelInfo("qwen3.5_9b_w4a8_gptq.safetensors", "text_encoders", "6.2 GB", "https://huggingface.co/Comfy-Org/MOSS-Image/resolve/main/text_encoders/qwen3.5_9b_w4a8_gptq.safetensors", "Qwen 3.5 9B Text Encoder");
        localModelScanner.checkAndSetModelStatus(q); required.add(q);
        ModelInfo v = localModelScanner.findLocalModelMatching("flux2-vae", "vae");
        if (v == null) v = localModelScanner.createRemoteModelInfo("flux2-vae_small.safetensors", "vae", "168 MB", "https://huggingface.co/Comfy-Org/MOSS-Image/resolve/main/vae/flux2-vae_small.safetensors", "FLUX.2 Small VAE");
        localModelScanner.checkAndSetModelStatus(v); required.add(v);
    }

    private void resolveFluxDependencies(List<ModelInfo> required) {
        resolveFluxDependencies(required, "flux");
    }

    private void resolveFluxDependencies(List<ModelInfo> required, String baseLower) {
        String pattern = "flux";
        if (baseLower != null && (baseLower.contains("flux-3") || baseLower.contains("flux.3") || baseLower.contains("flux 3"))) {
            pattern = "flux3";
        } else if (baseLower != null && (baseLower.contains("flux-2") || baseLower.contains("flux.2") || baseLower.contains("flux 2") || baseLower.contains("klein"))) {
            pattern = "flux-2";
        }

        ModelInfo baseUnet = localModelScanner.findLocalModelMatching(pattern, "unet", "diffusion_models", "checkpoints");
        if (baseUnet == null && !"flux".equals(pattern)) {
            baseUnet = localModelScanner.findLocalModelMatching("flux", "unet", "diffusion_models", "checkpoints");
        }
        if (baseUnet == null) {
            String fallbackName = "flux3".equals(pattern) ? "flux3_image_klein_sd_int8_convrot.safetensors" : "flux1-dev-fp8.safetensors";
            baseUnet = localModelScanner.createRemoteModelInfo(
                    fallbackName, "unet", "11.9 GB",
                    "https://huggingface.co/Comfy-Org/flux1-dev/resolve/main/flux1-dev-fp8.safetensors",
                    "FLUX Base UNET (Required by Flux LoRA)"
            );
        }
        localModelScanner.checkAndSetModelStatus(baseUnet);
        required.add(baseUnet);

        ModelInfo vae = localModelScanner.findLocalModelMatching("ae.safetensors", "vae");
        if (vae == null) vae = localModelScanner.createRemoteModelInfo("ae.safetensors", "vae", "335 MB", "https://huggingface.co/receptektas/black-forest-labs-ae_safetensors/resolve/main/ae.safetensors", "FLUX.1 VAE");
        localModelScanner.checkAndSetModelStatus(vae); required.add(vae);

        ModelInfo clipL = localModelScanner.findLocalModelMatching("clip_l.safetensors", "clip", "text_encoders");
        if (clipL == null) clipL = localModelScanner.createRemoteModelInfo("clip_l.safetensors", "clip", "246 MB", "https://huggingface.co/comfyanonymous/flux_text_encoders/resolve/main/clip_l.safetensors", "OpenCLIP ViT-L/14");
        localModelScanner.checkAndSetModelStatus(clipL); required.add(clipL);

        ModelInfo t5 = localModelScanner.findLocalModelMatching("t5xxl", "clip", "text_encoders");
        if (t5 == null) t5 = localModelScanner.createRemoteModelInfo("t5xxl_fp8_e4m3fn.safetensors", "clip", "4.8 GB", "https://huggingface.co/comfyanonymous/flux_text_encoders/resolve/main/t5xxl_fp8_e4m3fn.safetensors", "T5-XXL FP8 Text Encoder");
        localModelScanner.checkAndSetModelStatus(t5); required.add(t5);
    }

    private void resolveSdxlDependencies(String baseLower, List<ModelInfo> required) {
        ModelInfo baseSdxl = localModelScanner.findLocalModelMatching("sd_xl_base", "checkpoints");
        if (baseSdxl == null) baseSdxl = localModelScanner.findLocalModelMatching("sdxl", "checkpoints");
        if (baseSdxl == null && baseLower.contains("pony")) baseSdxl = localModelScanner.findLocalModelMatching("pony", "checkpoints");
        if (baseSdxl == null) baseSdxl = localModelScanner.createRemoteModelInfo("sd_xl_base_1.0.safetensors", "checkpoints", "6.94 GB", "https://huggingface.co/stabilityai/stable-diffusion-xl-base-1.0/resolve/main/sd_xl_base_1.0.safetensors", "Stable Diffusion XL Base 1.0");
        localModelScanner.checkAndSetModelStatus(baseSdxl); required.add(baseSdxl);
    }

    private void resolveSd15Dependencies(List<ModelInfo> required) {
        ModelInfo baseSd15 = localModelScanner.findLocalModelMatching("v1-5", "checkpoints");
        if (baseSd15 == null) {
            baseSd15 = localModelScanner.findLocalModelMatching("1.5", "checkpoints");
        }
        if (baseSd15 == null) {
            baseSd15 = localModelScanner.findFirstLocalCheckpointInfo().orElse(null);
        }
        if (baseSd15 == null) {
            baseSd15 = localModelScanner.createRemoteModelInfo(
                    "v1-5-pruned-emaonly.safetensors", "checkpoints", "4.27 GB",
                    "https://huggingface.co/runwayml/stable-diffusion-v1-5/resolve/main/v1-5-pruned-emaonly.safetensors",
                    "Stable Diffusion 1.5 Pruned Checkpoint (Required by SD 1.5 LoRA)"
            );
        }
        localModelScanner.checkAndSetModelStatus(baseSd15);
        required.add(baseSd15);
    }

    private void resolveWanDependencies(List<ModelInfo> required) {
        ModelInfo wanBase = localModelScanner.findLocalModelMatching("wan2.1", "diffusion_models", "unet");
        if (wanBase == null) {
            wanBase = localModelScanner.createRemoteModelInfo(
                    "wan2.1_t2v_1.3B_bf16.safetensors", "diffusion_models", "2.7 GB",
                    "https://huggingface.co/Comfy-Org/Wan_2.1_ComfyUI_repackaged/resolve/main/split_files/diffusion_models/wan2.1_t2v_1.3B_bf16.safetensors",
                    "Wan 2.1 Diffusion Model (Required by Wan LoRA)"
            );
        }
        localModelScanner.checkAndSetModelStatus(wanBase); required.add(wanBase);
        ModelInfo wanClip = localModelScanner.findLocalModelMatching("umt5", "clip", "text_encoders");
        if (wanClip == null) wanClip = localModelScanner.createRemoteModelInfo("umt5_xxl_fp8_e4m3fn.safetensors", "text_encoders", "5.1 GB", "https://huggingface.co/Comfy-Org/Wan_2.1_ComfyUI_repackaged/resolve/main/split_files/text_encoders/umt5_xxl_fp8_e4m3fn.safetensors", "UMT5 Text Encoder");
        localModelScanner.checkAndSetModelStatus(wanClip); required.add(wanClip);
        ModelInfo wanVae = localModelScanner.findLocalModelMatching("wan_2.1_vae", "vae");
        if (wanVae == null) wanVae = localModelScanner.createRemoteModelInfo("wan_2.1_vae.safetensors", "vae", "242 MB", "https://huggingface.co/Comfy-Org/Wan_2.1_ComfyUI_repackaged/resolve/main/split_files/vae/wan_2.1_vae.safetensors", "Wan 2.1 VAE");
        localModelScanner.checkAndSetModelStatus(wanVae); required.add(wanVae);
    }

    private void resolveGenericLoraDependencies(String baseLower, List<ModelInfo> required) {
        ModelInfo fallbackCheckpoint = localModelScanner.findFirstLocalCheckpointInfo().orElse(null);
        if (fallbackCheckpoint == null) {
            if (baseLower.contains("xl")) {
                fallbackCheckpoint = localModelScanner.createRemoteModelInfo(
                        "sd_xl_base_1.0.safetensors", "checkpoints", "6.94 GB",
                        "https://huggingface.co/stabilityai/stable-diffusion-xl-base-1.0/resolve/main/sd_xl_base_1.0.safetensors",
                        "Stable Diffusion XL Base 1.0 (Required by LoRA)"
                );
            } else {
                fallbackCheckpoint = localModelScanner.createRemoteModelInfo(
                        "v1-5-pruned-emaonly.safetensors", "checkpoints", "4.27 GB",
                        "https://huggingface.co/runwayml/stable-diffusion-v1-5/resolve/main/v1-5-pruned-emaonly.safetensors",
                        "Stable Diffusion 1.5 Pruned Checkpoint (Required by LoRA)"
                );
            }
        }
        localModelScanner.checkAndSetModelStatus(fallbackCheckpoint);
        required.add(fallbackCheckpoint);
    }

    private void resolveCheckpointDependencies(String baseLower, List<ModelInfo> required) {
        if (baseLower.contains("flux")) {
            ModelInfo vae = localModelScanner.findLocalModelMatching("ae.safetensors", "vae");
            if (vae == null) {
                vae = localModelScanner.createRemoteModelInfo(
                        "ae.safetensors", "vae", "335 MB",
                        "https://huggingface.co/receptektas/black-forest-labs-ae_safetensors/resolve/main/ae.safetensors",
                        "FLUX.1 VAE"
                );
            }
            localModelScanner.checkAndSetModelStatus(vae);
            required.add(vae);

            ModelInfo clipL = localModelScanner.findLocalModelMatching("clip_l.safetensors", "clip", "text_encoders");
            if (clipL == null) {
                clipL = localModelScanner.createRemoteModelInfo(
                        "clip_l.safetensors", "clip", "246 MB",
                        "https://huggingface.co/comfyanonymous/flux_text_encoders/resolve/main/clip_l.safetensors",
                        "OpenCLIP ViT-L/14"
                );
            }
            localModelScanner.checkAndSetModelStatus(clipL);
            required.add(clipL);

            ModelInfo t5 = localModelScanner.findLocalModelMatching("t5xxl", "clip", "text_encoders");
            if (t5 == null) {
                t5 = localModelScanner.createRemoteModelInfo(
                        "t5xxl_fp8_e4m3fn.safetensors", "clip", "4.8 GB",
                        "https://huggingface.co/comfyanonymous/flux_text_encoders/resolve/main/t5xxl_fp8_e4m3fn.safetensors",
                        "T5-XXL FP8 Text Encoder"
                );
            }
            localModelScanner.checkAndSetModelStatus(t5);
            required.add(t5);
        }
    }

    @Override
    public boolean areAllModelsInstalled(List<ModelInfo> requiredModels) {
        if (requiredModels == null || requiredModels.isEmpty()) {
            return true;
        }
        for (ModelInfo info : requiredModels) {
            if (isWorkflowBundleEntry(info)) {
                continue;
            }
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
            if (isWorkflowBundleEntry(info)) {
                continue;
            }
            if (!isModelFilePresent(info)) {
                missing.add(info);
            }
        }
        return missing;
    }

    private boolean isWorkflowBundleEntry(ModelInfo info) {
        if (info == null) return false;
        String type = info.getType() != null ? info.getType().toLowerCase(Locale.ROOT) : "";
        String savePath = info.getSave_path() != null ? info.getSave_path().toLowerCase(Locale.ROOT) : "";
        String fn = info.getFilename() != null ? info.getFilename().toLowerCase(Locale.ROOT) : "";
        return "workflows".equals(type) || "workflows".equals(savePath)
                || fn.endsWith(".zip") || fn.endsWith(".json");
    }

    @Override
    public String generateGuiWorkflowJson(CivitaiModelEntry entry, List<ModelInfo> requiredModels, String userPrompt) {
        return guiWorkflowBuilder.buildGuiWorkflowJson(entry, requiredModels, userPrompt);
    }

    @Override
    public String generateWorkflowJson(CivitaiModelEntry entry, List<ModelInfo> requiredModels, String userPrompt) {
        return apiWorkflowBuilder.generateWorkflowJson(entry, requiredModels, userPrompt);
    }

    @Override
    public boolean isModelFilePresent(ModelInfo info) {
        if (localModelScanner.isModelFilePresent(info)) {
            return true;
        }
        return canBeSatisfiedBySplitComponents(info);
    }

    private boolean canBeSatisfiedBySplitComponents(ModelInfo info) {
        if (info == null) return false;
        String type = info.getType() != null ? info.getType().toLowerCase(Locale.ROOT) : "";
        String fn = (info.getFilename() != null ? info.getFilename() : info.getName()).toLowerCase(Locale.ROOT);
        String base = info.getBase() != null ? info.getBase().toLowerCase(Locale.ROOT) : "";
        if (type.contains("diffusion") || type.contains("unet") || type.contains("clip")
                || type.contains("text_encoder") || type.contains("vae") || type.contains("lora") || type.contains("workflow")) {
            return false;
        }
        boolean isFlux = fn.contains("flux") || base.contains("flux");
        if (!isFlux) return false;
        boolean isAioOrCkpt = type.contains("checkpoint") || fn.contains("aio");
        if (!isAioOrCkpt) return false;

        boolean hasUnet = localModelScanner.findLocalModelMatching("unet", "unet", "diffusion_models") != null
                || localModelScanner.findLocalModelMatching("flux", "unet", "diffusion_models") != null;
        boolean hasClip = localModelScanner.findLocalModelMatching("clip", "clip", "text_encoders") != null
                || localModelScanner.findLocalModelMatching("t5", "clip", "text_encoders") != null;
        boolean hasVae = localModelScanner.findLocalModelMatching("ae", "vae") != null;

        return hasUnet && hasClip && hasVae;
    }

    /**
     * Searches local model directories (checkpoints, diffusion_models, unet) for the first existing checkpoint file.
     *
     * @return an {@link Optional} containing the {@link ModelInfo} for the first found checkpoint, or empty if none exist
     */
    public Optional<ModelInfo> findFirstLocalCheckpointInfo() {
        return localModelScanner.findFirstLocalCheckpointInfo();
    }

    /**
     * Resolves the filename of the first locally installed checkpoint, or empty if none are found.
     *
     * @return optional containing the filename of the installed checkpoint
     */
    public Optional<String> findFirstLocalCheckpointName() {
        return localModelScanner.findFirstLocalCheckpointName();
    }

    @Override
    public CompletableFuture<Boolean> sendWorkflowToComfyUI(CivitaiModelEntry entry, List<ModelInfo> requiredModels, String userPrompt) {
        List<ModelInfo> missing = getMissingModels(requiredModels);
        return workflowDispatcher.sendWorkflowToComfyUI(entry, requiredModels, userPrompt, missing);
    }

    @Override
    public CompletableFuture<CivitaiGenerationResult> sendToComfyUIAndGenerate(CivitaiModelEntry entry, List<ModelInfo> requiredModels, String userPrompt) {
        String workflowJson = generateWorkflowJson(entry, requiredModels, userPrompt);
        return workflowDispatcher.sendToComfyUIAndGenerate(entry, workflowJson);
    }
}
