package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final CivitaiLocalModelScanner localModelScanner;
    private final CivitaiApiWorkflowBuilder apiWorkflowBuilder;
    private final CivitaiWorkflowDispatcher workflowDispatcher;
    private final HttpClient httpClient;

    /**
     * Constructs a new CivitaiOperationalModelService with all required and optional dependencies.
     *
     * @param configService       configuration service for directory paths and settings
     * @param civitaiHubService   hub service for primary model conversions
     * @param architectureService architecture classification service
     * @param comfyApiClient      API client for ComfyUI communication
     * @param lifecycleService    optional lifecycle service for auto-booting ComfyUI
     * @param objectMapper        Jackson object mapper for JSON operations
     * @param guiWorkflowBuilder  builder for synthesizing LiteGraph GUI format workflows
     * @param localModelScanner   scanner for detecting installed model files on disk
     * @param apiWorkflowBuilder  builder for synthesizing ComfyUI API workflow payloads
     * @param workflowDispatcher  dispatcher for sending workflows to ComfyUI
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
            @Autowired(required = false) CivitaiWorkflowDispatcher workflowDispatcher) {
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
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Convenience constructor supporting earlier 7-argument calls used in tests.
     *
     * @param configService       configuration service for directory paths and settings
     * @param civitaiHubService   hub service for primary model conversions
     * @param architectureService architecture classification service
     * @param comfyApiClient      API client for ComfyUI communication
     * @param lifecycleService    optional lifecycle service for auto-booting ComfyUI
     * @param objectMapper        Jackson object mapper for JSON operations
     * @param guiWorkflowBuilder  builder for synthesizing LiteGraph GUI format workflows
     */
    public CivitaiOperationalModelService(
            ConfigService configService,
            ICivitaiHubService civitaiHubService,
            IModelArchitectureService architectureService,
            ComfyApiClient comfyApiClient,
            IComfyLifecycleService lifecycleService,
            ObjectMapper objectMapper,
            CivitaiGuiWorkflowBuilder guiWorkflowBuilder) {
        this(configService, civitaiHubService, architectureService, comfyApiClient, lifecycleService, objectMapper, guiWorkflowBuilder, null, null, null);
    }

    @Override
    public List<ModelInfo> resolveRequiredModels(CivitaiModelEntry entry) {
        if (entry == null) {
            return Collections.emptyList();
        }

        List<ModelInfo> required = new ArrayList<>();

        // 1. Primary Model (the Civitai entry itself)
        ModelInfo primary = civitaiHubService.createModelInfoForDownload(entry);
        localModelScanner.checkAndSetModelStatus(primary);
        required.add(primary);

        String typeLower = entry.type() != null ? entry.type().toLowerCase(Locale.ROOT) : "checkpoint";
        String baseLower = entry.baseModel() != null ? entry.baseModel().toLowerCase(Locale.ROOT) : "";

        // 2. Resolve Operational Dependencies based on Type and Architecture
        if (typeLower.contains("lora") || typeLower.contains("locon") || typeLower.contains("controlnet")
                || typeLower.contains("workflow") || typeLower.contains("textualinversion")) {
            resolveLoraAndControlDependencies(entry, baseLower, required);
        } else if (typeLower.contains("checkpoint")) {
            resolveCheckpointDependencies(baseLower, required);
        }

        return required;
    }

    private void resolveLoraAndControlDependencies(CivitaiModelEntry entry, String baseLower, List<ModelInfo> required) {
        if (baseLower.contains("flux")) {
            resolveFluxDependencies(required);
        } else if (baseLower.contains("sdxl") || baseLower.contains("pony")) {
            resolveSdxlDependencies(baseLower, required);
        } else if (baseLower.contains("sd 1.5") || baseLower.contains("sd1.5") || baseLower.contains("1.5")) {
            resolveSd15Dependencies(required);
        } else if (baseLower.contains("wan")) {
            resolveWanDependencies(required);
        } else {
            resolveGenericLoraDependencies(baseLower, required);
        }
    }

    private void resolveFluxDependencies(List<ModelInfo> required) {
        ModelInfo baseUnet = localModelScanner.findLocalModelMatching("flux", "unet", "diffusion_models", "checkpoints");
        if (baseUnet == null) {
            baseUnet = localModelScanner.createRemoteModelInfo(
                    "flux1-dev-fp8.safetensors", "unet", "11.9 GB",
                    "https://huggingface.co/Comfy-Org/flux1-dev/resolve/main/flux1-dev-fp8.safetensors",
                    "FLUX.1 Dev FP8 Base UNET (Required by Flux LoRA)"
            );
        }
        localModelScanner.checkAndSetModelStatus(baseUnet);
        required.add(baseUnet);

        ModelInfo vae = localModelScanner.findLocalModelMatching("ae.safetensors", "vae");
        if (vae == null) {
            vae = localModelScanner.createRemoteModelInfo(
                    "ae.safetensors", "vae", "335 MB",
                    "https://huggingface.co/receptektas/black-forest-labs-ae_safetensors/resolve/main/ae.safetensors",
                    "FLUX.1 VAE (Required by Flux pipeline)"
            );
        }
        localModelScanner.checkAndSetModelStatus(vae);
        required.add(vae);

        ModelInfo clipL = localModelScanner.findLocalModelMatching("clip_l.safetensors", "clip", "text_encoders");
        if (clipL == null) {
            clipL = localModelScanner.createRemoteModelInfo(
                    "clip_l.safetensors", "clip", "246 MB",
                    "https://huggingface.co/comfyanonymous/flux_text_encoders/resolve/main/clip_l.safetensors",
                    "OpenCLIP ViT-L/14 (Required by Flux text conditioning)"
            );
        }
        localModelScanner.checkAndSetModelStatus(clipL);
        required.add(clipL);

        ModelInfo t5 = localModelScanner.findLocalModelMatching("t5xxl", "clip", "text_encoders");
        if (t5 == null) {
            t5 = localModelScanner.createRemoteModelInfo(
                    "t5xxl_fp8_e4m3fn.safetensors", "clip", "4.8 GB",
                    "https://huggingface.co/comfyanonymous/flux_text_encoders/resolve/main/t5xxl_fp8_e4m3fn.safetensors",
                    "T5-XXL FP8 Text Encoder (Required by Flux text conditioning)"
            );
        }
        localModelScanner.checkAndSetModelStatus(t5);
        required.add(t5);
    }

    private void resolveSdxlDependencies(String baseLower, List<ModelInfo> required) {
        ModelInfo baseSdxl = localModelScanner.findLocalModelMatching("sd_xl_base", "checkpoints");
        if (baseSdxl == null) {
            baseSdxl = localModelScanner.findLocalModelMatching("sdxl", "checkpoints");
        }
        if (baseSdxl == null && baseLower.contains("pony")) {
            baseSdxl = localModelScanner.findLocalModelMatching("pony", "checkpoints");
        }
        if (baseSdxl == null) {
            baseSdxl = localModelScanner.createRemoteModelInfo(
                    "sd_xl_base_1.0.safetensors", "checkpoints", "6.94 GB",
                    "https://huggingface.co/stabilityai/stable-diffusion-xl-base-1.0/resolve/main/sd_xl_base_1.0.safetensors",
                    "Stable Diffusion XL Base 1.0 (Required by SDXL LoRA)"
            );
        }
        localModelScanner.checkAndSetModelStatus(baseSdxl);
        required.add(baseSdxl);
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
        localModelScanner.checkAndSetModelStatus(wanBase);
        required.add(wanBase);
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
    public String generateGuiWorkflowJson(CivitaiModelEntry entry, List<ModelInfo> requiredModels, String userPrompt) {
        return guiWorkflowBuilder.buildGuiWorkflowJson(entry, requiredModels, userPrompt);
    }

    @Override
    public String generateWorkflowJson(CivitaiModelEntry entry, List<ModelInfo> requiredModels, String userPrompt) {
        return apiWorkflowBuilder.generateWorkflowJson(entry, requiredModels, userPrompt);
    }

    @Override
    public boolean isModelFilePresent(ModelInfo info) {
        return localModelScanner.isModelFilePresent(info);
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
