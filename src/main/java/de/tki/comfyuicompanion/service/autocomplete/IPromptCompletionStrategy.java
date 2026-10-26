package de.tki.comfyuicompanion.service.autocomplete;

import java.nio.file.Path;
import java.util.List;

/**
 * Strategy interface for generating context-aware prompt autocomplete suggestions
 * based on the active token being typed by the user.
 */
public interface IPromptCompletionStrategy {

    /**
     * Generates completion candidates matching the active token prefix.
     *
     * @param activeToken   the current word or tag token under the caret
     * @param modelsBaseDir optional path to the local ComfyUI models directory
     * @return list of matching completion candidates
     */
    List<PromptCompletionCandidate> suggest(String activeToken, Path modelsBaseDir);
}
