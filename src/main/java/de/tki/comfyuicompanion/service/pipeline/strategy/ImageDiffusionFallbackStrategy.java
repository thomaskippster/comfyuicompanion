package de.tki.comfyuicompanion.service.pipeline.strategy;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.pipeline.VideoWorkflowBuilder;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

/**
 * Universal fallback strategy: When dedicated video diffusion models or VRAM are unavailable,
 * synthesizes high-quality keyframe imagery via available image diffusion architectures (SDXL, Flux, SD1.5)
 * and animates them into scene video clips.
 */
@Component
public class ImageDiffusionFallbackStrategy implements IVideoModelStrategy {

    private static final Logger log = LoggerFactory.getLogger(ImageDiffusionFallbackStrategy.class);
    public static final String ID = "image-fallback";
    public static final String DISPLAY_NAME = "Image Diffusion Fallback (Flux / SDXL)";

    private final VideoWorkflowBuilder workflowBuilder;

    @Autowired
    public ImageDiffusionFallbackStrategy(VideoWorkflowBuilder workflowBuilder) {
        this.workflowBuilder = workflowBuilder;
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
        return 0; // Universal base fallback
    }

    @Override
    public boolean isAvailable(String serverUrl,
                               List<String> availableUnets,
                               List<String> availableCheckpoints,
                               List<String> availableClips,
                               List<String> availableVaes,
                               Function<String, Boolean> nodeClassChecker) {
        return true; // Always capable of generating fallback visual workflows
    }

    @Override
    public de.tki.comfyuicompanion.domain.VideoPresetConfig getDefaultPreset(boolean isLowVram) {
        int w = isLowVram ? 1024 : 1280;
        int h = isLowVram ? 576 : 720;
        return new de.tki.comfyuicompanion.domain.VideoPresetConfig(
                DISPLAY_NAME, w, h, 22, 1.0, 127, ID,
                "Detected Preset: Flux Widescreen (" + w + "x" + h + " | FluxGuidance 3.5)"
        );
    }

    @Override
    public boolean isVramIntensive() {
        return false;
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
        log.info("🖼️ [ImageDiffusionFallbackStrategy] Generating scene visuals via available image diffusion model (Flux / SDXL / SD1.5)...");
        return workflowBuilder.generateSceneImageWorkflow(
                serverUrl, scene, speakerImage, seed, filenamePrefix,
                availableUnets, availableCheckpoints, availableClips, availableVaes);
    }
}
