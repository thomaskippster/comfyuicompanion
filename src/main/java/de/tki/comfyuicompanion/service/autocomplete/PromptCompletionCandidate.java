package de.tki.comfyuicompanion.service.autocomplete;

import java.util.Objects;

/**
 * Immutable Java 21 record representing a single prompt autocomplete suggestion.
 *
 * @param insertText  the exact text token to insert into the prompt field
 * @param displayText the human-readable label rendered in the popup list
 * @param category    the source category badge (e.g. "LoRA", "Trigger", "Embedding", "Tag")
 * @param priority    ranking priority (higher values appear first)
 */
public record PromptCompletionCandidate(
        String insertText,
        String displayText,
        String category,
        int priority
) {
    /**
     * Compact constructor validating non-null invariants.
     */
    public PromptCompletionCandidate {
        Objects.requireNonNull(insertText, "insertText must not be null");
        displayText = (displayText == null || displayText.isBlank()) ? insertText : displayText;
        category = (category == null || category.isBlank()) ? "Tag" : category;
    }

    /**
     * Creates a new fluent builder for {@link PromptCompletionCandidate}.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for constructing {@link PromptCompletionCandidate} instances.
     */
    public static final class Builder {
        private String insertText;
        private String displayText;
        private String category = "Tag";
        private int priority = 50;

        private Builder() {}

        /**
         * Sets the text to insert into the prompt.
         *
         * @param insertText replacement token
         * @return this builder
         */
        public Builder insertText(String insertText) {
            this.insertText = insertText;
            return this;
        }

        /**
         * Sets the display label shown in the completion popup.
         *
         * @param displayText UI label
         * @return this builder
         */
        public Builder displayText(String displayText) {
            this.displayText = displayText;
            return this;
        }

        /**
         * Sets the semantic category of the candidate.
         *
         * @param category category badge name
         * @return this builder
         */
        public Builder category(String category) {
            this.category = category;
            return this;
        }

        /**
         * Sets the ranking priority of the candidate.
         *
         * @param priority numeric priority
         * @return this builder
         */
        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        /**
         * Builds the immutable {@link PromptCompletionCandidate}.
         *
         * @return constructed candidate record
         */
        public PromptCompletionCandidate build() {
            return new PromptCompletionCandidate(insertText, displayText, category, priority);
        }
    }
}
