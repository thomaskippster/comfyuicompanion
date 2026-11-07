package de.tki.comfyuicompanion.service.pipeline.strategy;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.pipeline.SceneBlueprintInjector;
import de.tki.comfyuicompanion.service.pipeline.VideoWorkflowBuilder;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

/**
 * Strategy implementation for LTX-Video (Lightricks 0.9.x / 2.x),
 * optimized for fast draft generation and budget GPU architectures.
 */
@Component
public class LtxVideoStrategy implements IVideoModelStrategy {

    private static final Logger log = LoggerFactory.getLogger(LtxVideoStrategy.class);
    public static final String ID = "ltx-video";
    public static final String DISPLAY_NAME = "Text to Video (LTX-Video 0.9.5)";

    private final VideoWorkflowBuilder workflowBuilder;
    private final SceneBlueprintInjector blueprintInjector;
    private final ConfigService configService;

    @Autowired
    public LtxVideoStrategy(VideoWorkflowBuilder workflowBuilder,
                            @Autowired(required = false) SceneBlueprintInjector blueprintInjector,
                            @Autowired(required = false) ConfigService configService) {
        this.workflowBuilder = workflowBuilder;
        this.blueprintInjector = blueprintInjector;
        this.configService = configService;
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
        return 70; // Lightweight draft & budget favorite
    }

    @Override
    public boolean isAvailable(String serverUrl,
                               List<String> availableUnets,
                               List<String> availableCheckpoints,
                               List<String> availableClips,
                               List<String> availableVaes,
                               Function<String, Boolean> nodeClassChecker) {
        boolean hasLtxCheckpoint = availableCheckpoints != null && availableCheckpoints.stream()
                .anyMatch(c -> c.toLowerCase().contains("ltx") || c.toLowerCase().contains("ltxv"));
        boolean hasLtxUnet = availableUnets != null && availableUnets.stream()
                .anyMatch(u -> u.toLowerCase().contains("ltx") || u.toLowerCase().contains("ltxv"));
        return hasLtxCheckpoint || hasLtxUnet;
    }

    @Override
    public de.tki.comfyuicompanion.domain.VideoPresetConfig getDefaultPreset(boolean isLowVram) {
        int w = isLowVram ? 1024 : 1280;
        int h = isLowVram ? 576 : 720;
        return new de.tki.comfyuicompanion.domain.VideoPresetConfig(
                DISPLAY_NAME, w, h, 30, 3.0, 127, ID,
                "Detected Preset: LTX-Video High-Speed (" + w + "x" + h + " | 24 fps)"
        );
    }

    @Override
    public boolean isVramIntensive() {
        return false; // Lightweight draft & budget model
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
        log.info("🎬 [LtxVideoStrategy] Synthesizing LTX-Video lightweight diffusion graph...");

        if (blueprintInjector != null) {
            String comfyPath = configService != null ? configService.getComfyUIPath() : null;
            String templateName = (speakerImage != null && !speakerImage.isBlank())
                    ? "ltx-25_image_to_video.json"
                    : "ltx-25_text_to_video.json";

            JSONObject blueprint = blueprintInjector.loadBlueprintWorkflow(templateName, comfyPath);
            if (blueprint != null) {
                blueprintInjector.injectPrompt(blueprint, scene.getPrompt());
                blueprintInjector.injectParamsIntoBlueprint(blueprint, seed, filenamePrefix, scene);
                if (speakerImage != null && !speakerImage.isBlank()) {
                    blueprintInjector.injectSpeakerImage(blueprint, speakerImage);
                }
                return blueprint;
            }
        }

        return workflowBuilder.generateSceneImageWorkflow(serverUrl, scene, speakerImage, seed, filenamePrefix,
                availableUnets, availableCheckpoints, availableClips, availableVaes);
    }
}
