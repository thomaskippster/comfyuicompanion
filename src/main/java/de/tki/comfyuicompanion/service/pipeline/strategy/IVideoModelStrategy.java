package de.tki.comfyuicompanion.service.pipeline.strategy;

import de.tki.comfyuicompanion.domain.Scene;
import org.json.JSONObject;

import java.util.List;
import java.util.function.Function;

/**
 * Strategy contract for synthesizing video workflows from scene specifications
 * and inspecting hardware and model capabilities in ComfyUI.
 */
public interface IVideoModelStrategy {

    /**
     * Unique machine identifier for this video pipeline (e.g. "wan-2.2", "wan-2.1-i2v", "ltx-video", "hunyuan-video", "image-fallback").
     */
    String getModelId();

    /**
     * Human-readable display name for UI dropdowns and status messages.
     */
    String getDisplayName();

    /**
     * Priority order when auto-detecting the best strategy (higher values tried first).
     */
    default int getPriority() {
        return 50;
    }

    /**
     * Checks if this video model is supported and ready in the active ComfyUI environment.
     *
     * @param serverUrl active ComfyUI server URL
     * @param availableUnets list of detected UNET models
     * @param availableCheckpoints list of detected checkpoint models
     * @param availableClips list of detected CLIP/text encoder models
     * @param availableVaes list of detected VAE models
     * @param nodeClassChecker function verifying if a ComfyUI node class is installed
     * @return true if the pipeline can execute with current models and nodes
     */
    boolean isAvailable(String serverUrl,
                        List<String> availableUnets,
                        List<String> availableCheckpoints,
                        List<String> availableClips,
                        List<String> availableVaes,
                        Function<String, Boolean> nodeClassChecker);

    /**
     * Builds the executable ComfyUI workflow JSON for the given scene.
     *
     * @param serverUrl active ComfyUI server URL
     * @param scene scene specification containing prompts, seeds, and dimensions
     * @param speakerImage optional reference/speaker/keyframe image path
     * @param seed random seed for generation
     * @param filenamePrefix prefix for output artifacts
     * @param availableUnets list of detected UNET models
     * @param availableCheckpoints list of detected checkpoint models
     * @param availableClips list of detected CLIP/text encoder models
     * @param availableVaes list of detected VAE models
     * @return executable ComfyUI prompt JSON object
     * @throws Exception if generation fails
     */
    JSONObject buildWorkflow(String serverUrl,
                             Scene scene,
                             String speakerImage,
                             long seed,
                             String filenamePrefix,
                             List<String> availableUnets,
                             List<String> availableCheckpoints,
                             List<String> availableClips,
                             List<String> availableVaes) throws Exception;

    /**
     * Returns the recommended generation preset configuration for this strategy.
     * Encapsulates model-specific optimal parameters (resolution, sampling steps, CFG scale)
     * eliminating growing if-else / switch cascades in UI coordinators.
     *
     * @param isLowVram true if the host system has constrained VRAM (< 12 GB)
     * @return immutable preset configuration record
     */
    de.tki.comfyuicompanion.domain.VideoPresetConfig getDefaultPreset(boolean isLowVram);

    /**
     * Indicates whether this model requires heavy VRAM (> 12 GB),
     * warranting an automated memory advisory warning on lower-tier hardware.
     *
     * @return true if memory intensive (e.g. 14B parameter models)
     */
    default boolean isVramIntensive() {
        return false;
    }
}
