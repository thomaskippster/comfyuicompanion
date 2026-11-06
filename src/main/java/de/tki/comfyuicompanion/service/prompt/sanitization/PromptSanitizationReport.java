package de.tki.comfyuicompanion.service.prompt.sanitization;

import java.util.List;
import java.util.Set;

/**
 * Immutable result of a {@link PromptSanitizationPipeline} run.
 *
 * @param resolvedClassTypes        class types of missing nodes that were replaced by core constructs
 * @param remainingMissingClassTypes class types that could not be resolved and require installation
 */
public record PromptSanitizationReport(List<String> resolvedClassTypes, Set<String> remainingMissingClassTypes) {

    /**
     * Compact constructor creating defensive copies.
     */
    public PromptSanitizationReport {
        resolvedClassTypes = resolvedClassTypes != null ? List.copyOf(resolvedClassTypes) : List.of();
        remainingMissingClassTypes = remainingMissingClassTypes != null ? Set.copyOf(remainingMissingClassTypes) : Set.of();
    }

    /** @return {@code true} if the prompt can be queued without additional custom node installations */
    public boolean isExecutable() {
        return remainingMissingClassTypes.isEmpty();
    }
}
