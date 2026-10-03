package de.tki.comfyuicompanion.domain;

/**
 * Encapsulates the execution result of a generation workflow sent to ComfyUI.
 *
 * @param success      whether the generation completed successfully or was queued
 * @param promptId     the unique ComfyUI prompt identifier
 * @param filename     the output filename of the generated image/media, or {@code null} if pending
 * @param subfolder    the output subfolder, if applicable
 * @param type         the output folder type (usually "output")
 * @param imageUrl     the URL to view or download the generated output from ComfyUI
 * @param errorMessage error message if generation failed, or {@code null} on success
 */
public record CivitaiGenerationResult(
        boolean success,
        String promptId,
        String filename,
        String subfolder,
        String type,
        String imageUrl,
        String errorMessage
) {

    /**
     * Creates a successful generation result containing the produced image details.
     *
     * @param promptId  the unique prompt identifier
     * @param filename  the output filename
     * @param subfolder the subfolder
     * @param type      the folder type
     * @param imageUrl  the URL to view the image
     * @return a successful {@link CivitaiGenerationResult} instance
     */
    public static CivitaiGenerationResult success(String promptId, String filename, String subfolder, String type, String imageUrl) {
        return new CivitaiGenerationResult(true, promptId, filename, subfolder, type, imageUrl, null);
    }

    /**
     * Creates a failure generation result.
     *
     * @param promptId     the prompt identifier, if available
     * @param errorMessage descriptive error message
     * @return a failed {@link CivitaiGenerationResult} instance
     */
    public static CivitaiGenerationResult failure(String promptId, String errorMessage) {
        return new CivitaiGenerationResult(false, promptId, null, null, null, null, errorMessage);
    }
}
