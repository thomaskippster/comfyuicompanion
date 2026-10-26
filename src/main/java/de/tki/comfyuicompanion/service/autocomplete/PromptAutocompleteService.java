package de.tki.comfyuicompanion.service.autocomplete;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Coordinator service orchestrating all registered {@link IPromptCompletionStrategy} implementations
 * to provide unified, ranked, and deduplicated prompt completions.
 */
@Service
public class PromptAutocompleteService {

    private final List<IPromptCompletionStrategy> strategies;

    /**
     * Default constructor initializing standard completion strategies.
     */
    public PromptAutocompleteService() {
        this(List.of(
                new LoraSyntaxCompletionStrategy(),
                new EmbeddingCompletionStrategy(),
                new CivitaiTriggerWordCompletionStrategy(),
                new CuratedTagCompletionStrategy()
        ));
    }

    /**
     * Spring constructor injecting all available completion strategies.
     *
     * @param strategies list of completion strategies
     */
    @Autowired
    public PromptAutocompleteService(List<IPromptCompletionStrategy> strategies) {
        this.strategies = (strategies != null && !strategies.isEmpty())
                ? List.copyOf(strategies)
                : List.of(
                        new LoraSyntaxCompletionStrategy(),
                        new EmbeddingCompletionStrategy(),
                        new CivitaiTriggerWordCompletionStrategy(),
                        new CuratedTagCompletionStrategy()
                );
    }

    /**
     * Queries all completion strategies for the active token and returns up to {@code maxResults}
     * deduplicated candidates ordered by descending priority.
     *
     * @param activeToken   current token being typed
     * @param modelsBaseDir optional path to local models directory
     * @param maxResults    maximum number of candidates to return
     * @return sorted list of completion candidates
     */
    public List<PromptCompletionCandidate> querySuggestions(String activeToken, Path modelsBaseDir, int maxResults) {
        if (activeToken == null || activeToken.isBlank() || maxResults <= 0) {
            return List.of();
        }
        Map<String, PromptCompletionCandidate> deduplicated = new LinkedHashMap<>();
        for (IPromptCompletionStrategy strategy : strategies) {
            List<PromptCompletionCandidate> list = strategy.suggest(activeToken, modelsBaseDir);
            if (list == null) continue;
            for (PromptCompletionCandidate c : list) {
                String key = c.insertText().toLowerCase(Locale.ROOT);
                PromptCompletionCandidate existing = deduplicated.get(key);
                if (existing == null || c.priority() > existing.priority()) {
                    deduplicated.put(key, c);
                }
            }
        }
        List<PromptCompletionCandidate> sorted = new ArrayList<>(deduplicated.values());
        sorted.sort(Comparator.comparingInt(PromptCompletionCandidate::priority).reversed()
                .thenComparing(PromptCompletionCandidate::insertText));
        return sorted.size() > maxResults ? sorted.subList(0, maxResults) : sorted;
    }

    /**
     * Extracts the active comma-delimited or whitespace-delimited token immediately preceding the caret.
     *
     * @param fullText      full text of the prompt input component
     * @param caretPosition current caret index
     * @return extracted active token
     */
    public static String extractActiveToken(String fullText, int caretPosition) {
        if (fullText == null || fullText.isEmpty() || caretPosition <= 0) {
            return "";
        }
        int end = Math.min(caretPosition, fullText.length());
        int start = end;
        while (start > 0) {
            char ch = fullText.charAt(start - 1);
            if (ch == ',' || ch == '\n' || ch == '\r') {
                break;
            }
            start--;
        }
        String segment = fullText.substring(start, end).stripLeading();
        int loraIdx = segment.lastIndexOf("<lora:");
        if (loraIdx >= 0) {
            return segment.substring(loraIdx);
        }
        int embIdx = segment.toLowerCase(Locale.ROOT).lastIndexOf("embedding:");
        if (embIdx >= 0) {
            return segment.substring(embIdx);
        }
        return segment;
    }

    /**
     * Replaces the active token immediately preceding the caret with the selected completion text.
     *
     * @param fullText      current prompt text
     * @param caretPosition current caret position
     * @param replacement   selected completion insert text
     * @return updated full prompt text
     */
    public static String applyCompletion(String fullText, int caretPosition, String replacement) {
        if (fullText == null) return replacement != null ? replacement : "";
        int end = Math.min(Math.max(0, caretPosition), fullText.length());
        int start = end;
        while (start > 0) {
            char ch = fullText.charAt(start - 1);
            if (ch == ',' || ch == '\n' || ch == '\r') {
                break;
            }
            start--;
        }
        while (start < end && Character.isWhitespace(fullText.charAt(start))) {
            start++;
        }
        String prefix = fullText.substring(0, start);
        String suffix = fullText.substring(end);
        String suffixAdjusted = suffix.startsWith(",") ? suffix : ", " + suffix.stripLeading();
        if (suffix.isBlank()) {
            suffixAdjusted = ", ";
        }
        return prefix + replacement + suffixAdjusted;
    }
}
