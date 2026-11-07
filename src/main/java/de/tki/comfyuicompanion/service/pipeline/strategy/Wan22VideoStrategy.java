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
 * Strategy implementation for Wan 2.2 Text-to-Video and High/Low Noise UNET video synthesis.
 */
@Component
public class Wan22VideoStrategy implements IVideoModelStrategy {

    private static final Logger log = LoggerFactory.getLogger(Wan22VideoStrategy.class);
    public static final String ID = "wan-2.2";
    public static final String DISPLAY_NAME = "Text to Video (Wan 2.2)";

    private final VideoWorkflowBuilder workflowBuilder;
    private final SceneBlueprintInjector blueprintInjector;
    private final ConfigService configService;

    @Autowired
    public Wan22VideoStrategy(VideoWorkflowBuilder workflowBuilder,
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
        return 100; // Primary high-tier choice
    }

    @Override
    public boolean isAvailable(String serverUrl,
                               List<String> availableUnets,
                               List<String> availableCheckpoints,
                               List<String> availableClips,
                               List<String> availableVaes,
                               Function<String, Boolean> nodeClassChecker) {
        boolean nodesOk = nodeClassChecker != null && nodeClassChecker.apply("WanImageToVideo");
        boolean modelsOk = workflowBuilder != null && workflowBuilder.isWanSetupComplete(availableUnets, availableClips, availableVaes);
        return nodesOk && modelsOk;
    }

    @Override
    public de.tki.comfyuicompanion.domain.VideoPresetConfig getDefaultPreset(boolean isLowVram) {
        int w = isLowVram ? 1024 : 1280;
        int h = isLowVram ? 576 : 720;
        return new de.tki.comfyuicompanion.domain.VideoPresetConfig(
                DISPLAY_NAME, w, h, 20, 3.0, 127, ID,
                "Detected Preset: Wan 2.2 Cinematic Video (" + w + "x" + h + " | 16 fps)"
        );
    }

    @Override
    public boolean isVramIntensive() {
        return true; // 14B parameter model
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
        log.info("🎬 [Wan22VideoStrategy] Synthesizing Wan 2.2 native video workflow...");

        if (speakerImage != null && !speakerImage.trim().isEmpty() && blueprintInjector != null) {
            String comfyPath = configService != null ? configService.getComfyUIPath() : null;
            JSONObject blueprint = blueprintInjector.loadBlueprintWorkflow("Text to Video (Wan 2.2).json", comfyPath);
            if (blueprint != null) {
                log.info("📄 [Wan22VideoStrategy] Injected speaker image into Wan 2.2 blueprint workflow.");
                blueprintInjector.injectPrompt(blueprint, scene.getPrompt());
                blueprintInjector.injectParamsIntoBlueprint(blueprint, seed, filenamePrefix, scene);
                blueprintInjector.injectSpeakerImage(blueprint, speakerImage);
                return blueprint;
            }
        }

        return workflowBuilder.generateWanWorkflowJson(serverUrl, scene, speakerImage, seed, filenamePrefix);
    }
}
