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
 * Strategy implementation for Tencent Hunyuan Video 1.5 diffusion pipelines.
 */
@Component
public class HunyuanVideoStrategy implements IVideoModelStrategy {

    private static final Logger log = LoggerFactory.getLogger(HunyuanVideoStrategy.class);
    public static final String ID = "hunyuan-video";
    public static final String DISPLAY_NAME = "Text to Video (Hunyuan Video 1.5)";

    private final VideoWorkflowBuilder workflowBuilder;
    private final SceneBlueprintInjector blueprintInjector;
    private final ConfigService configService;

    @Autowired
    public HunyuanVideoStrategy(VideoWorkflowBuilder workflowBuilder,
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
        return 60; // Cinematic mid-tier choice
    }

    @Override
    public boolean isAvailable(String serverUrl,
                               List<String> availableUnets,
                               List<String> availableCheckpoints,
                               List<String> availableClips,
                               List<String> availableVaes,
                               Function<String, Boolean> nodeClassChecker) {
        boolean hasHunyuanUnet = availableUnets != null && availableUnets.stream()
                .anyMatch(u -> u.toLowerCase().contains("hunyuan") || u.toLowerCase().contains("hyvideo"));
        boolean hasHunyuanCkpt = availableCheckpoints != null && availableCheckpoints.stream()
                .anyMatch(c -> c.toLowerCase().contains("hunyuan") || c.toLowerCase().contains("hyvideo"));
        return hasHunyuanUnet || hasHunyuanCkpt;
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
        log.info("🎬 [HunyuanVideoStrategy] Synthesizing Hunyuan Video 1.5 workflow graph...");

        if (blueprintInjector != null) {
            String comfyPath = configService != null ? configService.getComfyUIPath() : null;
            JSONObject blueprint = blueprintInjector.loadBlueprintWorkflow("hunyuan_video_15_image_to_video.json", comfyPath);
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
