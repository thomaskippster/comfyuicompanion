package de.tki.comfyuicompanion.domain;

/**
 * Domain record encapsulating generation parameters extracted from ComfyUI output images
 * (PNG text chunks, EXIF, or Civitai metadata blocks).
 */
public record ComfyGenerationMetadata(
        String positivePrompt,
        String negativePrompt,
        String modelName,
        Long seed,
        Integer steps,
        Double cfg,
        String sampler,
        String scheduler,
        Integer width,
        Integer height,
        String rawPromptJson,
        String rawWorkflowJson
) {
    /**
     * @return {@code true} if any meaningful generation parameter is present
     */
    public boolean hasParameters() {
        return (positivePrompt != null && !positivePrompt.isBlank())
                || seed != null || steps != null || (modelName != null && !modelName.isBlank());
    }

    /**
     * Creates an empty metadata record.
     */
    public static ComfyGenerationMetadata empty() {
        return new ComfyGenerationMetadata(null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
