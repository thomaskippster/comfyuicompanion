package de.tki.comfyuicompanion.service.pipeline.strategy;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.pipeline.FastWanWorkflowBuilder;
import de.tki.comfyuicompanion.service.pipeline.SceneBlueprintInjector;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

/**
 * Strategy implementation for Wan 2.2 5B / FastWan video diffusion generation.
 * Optimal for 16 GB VRAM GPUs, eliminating CPU offloading and accelerating generation
 * to 1.5 - 2.5 seconds per step.
 */
@Component
public class Wan22FastWan5BStrategy implements IVideoModelStrategy {

    private static final Logger log = LoggerFactory.getLogger(Wan22FastWan5BStrategy.class);
    public static final String ID = "wan-2.2-fastwan-5b";
    public static final String DISPLAY_NAME = "FastWan 5B (Wan 2.2 - 16GB VRAM)";

    private final FastWanWorkflowBuilder fastWanWorkflowBuilder;
    private final SceneBlueprintInjector blueprintInjector;
    private final ConfigService configService;
    private final de.tki.comfyuicompanion.service.pipeline.ComfyNodeInspectionClient inspectionClient;

    public Wan22FastWan5BStrategy(
            FastWanWorkflowBuilder fastWanWorkflowBuilder,
            @Autowired(required = false) SceneBlueprintInjector blueprintInjector,
            @Autowired(required = false) ConfigService configService) {
        this(fastWanWorkflowBuilder, blueprintInjector, configService, null);
    }

    @Autowired
    public Wan22FastWan5BStrategy(
            FastWanWorkflowBuilder fastWanWorkflowBuilder,
            @Autowired(required = false) SceneBlueprintInjector blueprintInjector,
            @Autowired(required = false) ConfigService configService,
            @Autowired(required = false) de.tki.comfyuicompanion.service.pipeline.ComfyNodeInspectionClient inspectionClient) {
        this.fastWanWorkflowBuilder = fastWanWorkflowBuilder != null ? fastWanWorkflowBuilder : new FastWanWorkflowBuilder();
        this.blueprintInjector = blueprintInjector;
        this.configService = configService;
        this.inspectionClient = inspectionClient != null ? inspectionClient : new de.tki.comfyuicompanion.service.pipeline.ComfyNodeInspectionClient();
    }

    @Override
    public String getModelId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    public int getPriority() {
        // Priority 110 ensures FastWan 5B is prioritized over the memory-heavy 14B model
        return 110;
    }

    @Override
    public boolean isAvailable(String serverUrl,
                               List<String> availableUnets,
                               List<String> availableCheckpoints,
                               List<String> availableClips,
                               List<String> availableVaes,
                               Function<String, Boolean> nodeClassChecker) {

        String comfyPath = configService != null ? configService.getComfyUIPath() : null;
        if (comfyPath != null) {
            fastWanWorkflowBuilder.ensureDiffusionModelLinked(comfyPath);
        }

        boolean hasFastWanModel = false;
        if (availableUnets != null) {
            hasFastWanModel = availableUnets.stream().anyMatch(this::isFastWanMatch);
        }
        if (!hasFastWanModel && availableCheckpoints != null) {
            hasFastWanModel = availableCheckpoints.stream().anyMatch(this::isFastWanMatch);
        }

        boolean hasClip = availableClips != null && availableClips.stream()
                .anyMatch(c -> c.toLowerCase().contains("umt5") || c.toLowerCase().contains("wan"));
        boolean hasVae = availableVaes != null && availableVaes.stream()
                .anyMatch(v -> v.toLowerCase().contains("wan"));

        return hasFastWanModel && hasClip && hasVae;
    }

    private boolean isFastWanMatch(String modelName) {
        if (modelName == null) return false;
        String lower = modelName.toLowerCase();
        return lower.contains("fastwan") || (lower.contains("wan2_2") && lower.contains("5b"))
                || lower.contains("wan2.2-ti2v-5b") || lower.contains("wan2_2-ti2v-5b");
    }

    @Override
    public de.tki.comfyuicompanion.domain.VideoPresetConfig getDefaultPreset(boolean isLowVram) {
        return new de.tki.comfyuicompanion.domain.VideoPresetConfig(
                DISPLAY_NAME,
                832, 480,
                6, 1.0, 127,
                ID,
                "Detected Preset: FastWan 2.2 5B (832x480 | 6 Steps, 16GB VRAM Fast)"
        );
    }

    @Override
    public boolean isVramIntensive() {
        return false; // Fits completely in 16 GB VRAM with 5-7 GB footprint
    }

    @Override
    public JSONObject buildWorkflow(String serverUrl,
                                    Scene scene,
                                    String speakerImage,
                                    long seed,
                                    String filenamePrefix,
                                    List<String> availableUnets,
                                    List<String> availableCheckpoints,
                                    List<String> availableClips,
                                    List<String> availableVaes) throws Exception {

        log.info("⚡ [Wan22FastWan5BStrategy] Constructing FastWan 5B execution pipeline (16GB VRAM optimized)...");

        String effectiveImage = speakerImage;
        if ((effectiveImage == null || effectiveImage.isBlank()) && scene.getSpeakerImagePath() != null && !scene.getSpeakerImagePath().isBlank()) {
            effectiveImage = scene.getSpeakerImagePath();
        }

        // 1. Attempt generic blueprint discovery from user workflows or Vault
        if (blueprintInjector != null) {
            try {
                String comfyPath = configService != null ? configService.getComfyUIPath() : null;
                String directionKeyword = (effectiveImage != null && !effectiveImage.isBlank()) ? "i2v" : "t2v";

                JSONObject blueprint = blueprintInjector.findBlueprintByKeywords(comfyPath, "fastwan", "5b", directionKeyword);
                if (blueprint == null) {
                    String blueprintName = (effectiveImage != null && !effectiveImage.isBlank())
                            ? "WAN 2.2 FASTWAN 5B I2V.png"
                            : "WAN 2.2 FASTWAN 5B T2V.png";
                    blueprint = blueprintInjector.loadBlueprintWorkflow(blueprintName, comfyPath);
                }

                if (blueprint != null) {
                    java.util.Set<String> availableNodeClasses = (inspectionClient != null)
                            ? inspectionClient.fetchAvailableNodeClasses(serverUrl)
                            : java.util.Collections.emptySet();
                    boolean allNodesOk = blueprintInjector.areAllNodesAvailable(blueprint, availableNodeClasses);

                    if (allNodesOk) {
                        log.info("📄 [Wan22FastWan5BStrategy] Successfully validated and loaded FastWan 5B blueprint from workflows/vault.");
                        blueprintInjector.injectPrompt(blueprint, scene.getPrompt());
                        blueprintInjector.injectParamsIntoBlueprint(blueprint, seed, filenamePrefix, scene);
                        if (effectiveImage != null && !effectiveImage.isBlank()) {
                            blueprintInjector.injectSpeakerImage(blueprint, effectiveImage);
                        }
                        return blueprint;
                    } else {
                        log.info("ℹ️ [Wan22FastWan5BStrategy] Blueprint requires custom nodes not installed on ComfyUI server. Bypassing blueprint and using native core pipeline.");
                    }
                }
            } catch (Exception e) {
                log.warn("⚠️ [Wan22FastWan5BStrategy] Blueprint loading/injection failed ({}). Falling back to pure native ComfyUI pipeline.", e.getMessage());
            }
        }

        // 2. Pure native ComfyUI core node pipeline (Zero external custom node dependency)
        return fastWanWorkflowBuilder.generateFastWanWorkflowJson(
                serverUrl, scene, effectiveImage, seed, filenamePrefix,
                availableUnets, availableCheckpoints, availableClips, availableVaes);
    }
}
