package de.tki.comfyuicompanion.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Immutable Java 21 record representing persistent sidecar metadata for a workflow in My Vault.
 * Stored as a companion {@code .meta.json} file alongside the workflow JSON in {@code user_workflows/}.
 *
 * @param id             unique identifier or slug
 * @param title          human-readable workflow title
 * @param description    workflow description or usage guide
 * @param category       primary category (e.g., text-to-image, video)
 * @param architecture   detected base model architecture (Flux, Qwen, SDXL, Wan, etc.)
 * @param sourcePlatform origin platform (CIVITAI, HUGGINGFACE, COMFY_ORG, LOCAL_PNG, LOCAL_JSON)
 * @param sourceUrl      upstream link or origin identifier
 * @param previewFile    local relative filename of the preview image/video thumbnail
 * @param requiredModels list of concrete model weights required by this workflow
 * @param addedAtEpochMs timestamp in milliseconds when saved to the vault
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record VaultWorkflowMetadata(
        String id,
        String title,
        String description,
        String category,
        String architecture,
        String sourcePlatform,
        String sourceUrl,
        String previewFile,
        List<ModelInfo> requiredModels,
        long addedAtEpochMs
) {
    /**
     * Compact canonical constructor providing defensive defaults.
     */
    public VaultWorkflowMetadata {
        title = (title != null && !title.isBlank()) ? title : "Custom Vault Workflow";
        architecture = (architecture != null && !architecture.isBlank()) ? architecture : "General";
        sourcePlatform = (sourcePlatform != null && !sourcePlatform.isBlank()) ? sourcePlatform : "LOCAL";
        requiredModels = (requiredModels != null) ? List.copyOf(requiredModels) : List.of();
    }
}
