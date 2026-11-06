package de.tki.comfyuicompanion.service.prompt.sanitization;

/**
 * A single, self-contained transformation step of the {@link PromptSanitizationPipeline}.
 * <p>
 * Each stage tries to eliminate unregistered (missing) custom nodes from an API prompt graph by
 * replacing them with semantically equivalent ComfyUI core constructs, deciding purely on the type
 * information published by ComfyUI's {@code /object_info} registry.
 */
public interface PromptSanitizationStage {

    /**
     * Applies the stage to the graph.
     *
     * @param graph type-aware prompt graph view
     * @return {@code true} if the graph was mutated
     */
    boolean apply(ApiPromptGraph graph);
}
