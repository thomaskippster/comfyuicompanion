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
 * Strategy implementation for Wan 2.1 Image-to-Video diffusion generation,
 * ideal for vertical formats (TikTok/Reels) and keyframe conditioning.
 */
@Component
public class Wan21ImageToVideoStrategy implements IVideoModelStrategy {

    private static final Logger log = LoggerFactory.getLogger(Wan21ImageToVideoStrategy.class);
    public static final String ID = "wan-2.1-i2v";
    public static final String DISPLAY_NAME = "Image to Video (Wan 2.1)";

    private final VideoWorkflowBuilder workflowBuilder;
    private final SceneBlueprintInjector blueprintInjector;
    private final ConfigService configService;

    @Autowired
    public Wan21ImageToVideoStrategy(VideoWorkflowBuilder workflowBuilder,
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
        return 90; // High priority, especially when image/keyframe is present
    }

    @Override
    public boolean isAvailable(String serverUrl,
                               List<String> availableUnets,
                               List<String> availableCheckpoints,
                               List<String> availableClips,
                               List<String> availableVaes,
                               Function<String, Boolean> nodeClassChecker) {
        boolean hasWan21Unet = availableUnets != null && availableUnets.stream()
                .anyMatch(u -> u.toLowerCase().contains("wan2.1") || u.toLowerCase().contains("wan_2.1") || u.toLowerCase().contains("wan2_1"));
        boolean hasGeneralWan = workflowBuilder != null && workflowBuilder.isWanSetupComplete(availableUnets, availableClips, availableVaes);
        return hasWan21Unet || hasGeneralWan;
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
        log.info("🎬 [Wan21ImageToVideoStrategy] Synthesizing Wan 2.1 Image-to-Video workflow (TikTok/Vertical optimized)...");

        String effectiveImage = speakerImage;
        if ((effectiveImage == null || effectiveImage.isBlank()) && scene.getSpeakerImagePath() != null && !scene.getSpeakerImagePath().isBlank()) {
            effectiveImage = scene.getSpeakerImagePath();
        }

        if (effectiveImage != null && !effectiveImage.isBlank() && blueprintInjector != null) {
            String comfyPath = configService != null ? configService.getComfyUIPath() : null;
            JSONObject blueprint = blueprintInjector.loadBlueprintWorkflow("wan_21_image_to_video.json", comfyPath);
            if (blueprint != null) {
                log.info("📄 [Wan21ImageToVideoStrategy] Successfully loaded wan_21_image_to_video blueprint.");
                blueprintInjector.injectPrompt(blueprint, scene.getPrompt());
                blueprintInjector.injectParamsIntoBlueprint(blueprint, seed, filenamePrefix, scene);
                blueprintInjector.injectSpeakerImage(blueprint, effectiveImage);
                return blueprint;
            }
        }

        return workflowBuilder.generateWanWorkflowJson(serverUrl, scene, effectiveImage, seed, filenamePrefix);
    }
}
