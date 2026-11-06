package de.tki.comfyuicompanion.domain;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Immutable domain representation of a ComfyUI workflow unified across all provider ecosystems
 * (ComfyUI Blueprints, Civitai, HuggingFace, and Local Vault).
 *
 * @param id                    unique workflow identifier
 * @param title                 human-readable workflow title
 * @param category              workflow functional category (e.g. Text-to-Image, Inpainting, Upscaling)
 * @param architecture          underlying model architecture (e.g. SDXL 1.0, Flux.1 D, Z-Image)
 * @param author                creator or repository owner
 * @param description           descriptive text or markdown
 * @param sourceType            origin ecosystem identifier
 * @param sourceUrl             external web link for provenance
 * @param previewPathOrUrl      local file path or remote URL for preview image
 * @param workflowFilePathOrUrl local file path or remote URL for the workflow JSON
 * @param requiredModels        list of model dependencies extracted or declared for this workflow
 * @param missingModelsCount    calculated count of missing model dependencies
 * @param rawSourceEntry        original raw entry object (ComfyRegistryWorkflow, CivitaiModelEntry, etc.)
 * @param extendedAttributes    additional arbitrary key-value pairs (e.g., stars, download counts, license)
 *
 * @author Senior Java Developer
 */
public record WorkflowDescriptor(
        String id,
        String title,
        String category,
        String architecture,
        String author,
        String description,
        WorkflowSourceType sourceType,
        String sourceUrl,
        String previewPathOrUrl,
        String workflowFilePathOrUrl,
        List<ModelInfo> requiredModels,
        int missingModelsCount,
        Object rawSourceEntry,
        Map<String, String> extendedAttributes
) {

    /**
     * Compact constructor enforcing non-null collections.
     */
    public WorkflowDescriptor {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(sourceType, "sourceType must not be null");
        requiredModels = requiredModels != null ? List.copyOf(requiredModels) : List.of();
        extendedAttributes = extendedAttributes != null ? Map.copyOf(extendedAttributes) : Map.of();
    }

    /**
     * Checks if all required models are locally available.
     *
     * @return true if all required models exist locally
     */
    public boolean isReadyToExecute() {
        return missingModelsCount == 0;
    }

    /**
     * Creates a new fluent builder instance.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for constructing immutable {@link WorkflowDescriptor} instances.
     */
    public static final class Builder {
        private String id;
        private String title = "Untitled Workflow";
        private String category = "General";
        private String architecture = "Unknown";
        private String author = "Unknown";
        private String description = "";
        private WorkflowSourceType sourceType = WorkflowSourceType.VAULT;
        private String sourceUrl;
        private String previewPathOrUrl;
        private String workflowFilePathOrUrl;
        private List<ModelInfo> requiredModels = Collections.emptyList();
        private int missingModelsCount = 0;
        private Object rawSourceEntry;
        private Map<String, String> extendedAttributes = Collections.emptyMap();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder architecture(String architecture) {
            this.architecture = architecture;
            return this;
        }

        public Builder author(String author) {
            this.author = author;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder sourceType(WorkflowSourceType sourceType) {
            this.sourceType = sourceType;
            return this;
        }

        public Builder sourceUrl(String sourceUrl) {
            this.sourceUrl = sourceUrl;
            return this;
        }

        public Builder previewPathOrUrl(String previewPathOrUrl) {
            this.previewPathOrUrl = previewPathOrUrl;
            return this;
        }

        public Builder workflowFilePathOrUrl(String workflowFilePathOrUrl) {
            this.workflowFilePathOrUrl = workflowFilePathOrUrl;
            return this;
        }

        public Builder requiredModels(List<ModelInfo> requiredModels) {
            this.requiredModels = requiredModels;
            return this;
        }

        public Builder missingModelsCount(int missingModelsCount) {
            this.missingModelsCount = missingModelsCount;
            return this;
        }

        public Builder calculateMissingModels(Predicate<ModelInfo> isModelPresent) {
            if (this.requiredModels != null && isModelPresent != null) {
                this.missingModelsCount = (int) this.requiredModels.stream()
                        .filter(m -> !isModelPresent.test(m))
                        .count();
            }
            return this;
        }

        public Builder rawSourceEntry(Object rawSourceEntry) {
            this.rawSourceEntry = rawSourceEntry;
            return this;
        }

        public Builder extendedAttributes(Map<String, String> extendedAttributes) {
            this.extendedAttributes = extendedAttributes;
            return this;
        }

        public WorkflowDescriptor build() {
            return new WorkflowDescriptor(
                    id, title, category, architecture, author, description,
                    sourceType, sourceUrl, previewPathOrUrl, workflowFilePathOrUrl,
                    requiredModels, missingModelsCount, rawSourceEntry, extendedAttributes
            );
        }
    }
}
