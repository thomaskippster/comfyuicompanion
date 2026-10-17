package de.tki.comfyuicompanion.domain;

/**
 * Immutable snapshot of an Image Lab prompt execution session for history and recall.
 *
 * @param timestamp epoch millisecond timestamp when the prompt was submitted
 * @param positivePrompt positive prompt text
 * @param negativePrompt negative prompt text
 * @param model name of the blueprint or model used
 * @param width image width
 * @param height image height
 * @param steps sampling steps count
 * @param cfg guidance scale value
 * @param sampler sampler algorithm name
 * @param scheduler scheduler algorithm name
 */
public record PromptHistoryEntry(
        long timestamp,
        String positivePrompt,
        String negativePrompt,
        String model,
        int width,
        int height,
        int steps,
        double cfg,
        String sampler,
        String scheduler
) {
    /**
     * Returns a truncated summary suitable for UI display in dropdowns or lists.
     *
     * @return truncated prompt summary
     */
    public String getDisplayTitle() {
        if (positivePrompt == null || positivePrompt.isBlank()) {
            return "[Empty Prompt]";
        }
        String singleLine = positivePrompt.replace('\n', ' ').trim();
        return singleLine.length() > 60 ? singleLine.substring(0, 57) + "..." : singleLine;
    }
}
