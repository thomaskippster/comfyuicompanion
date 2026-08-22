package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ComfyRegistryWorkflow;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface ILocalModelValidator {
    /**
     * Scans the local models/ directory recursively and builds a set of available model file names.
     */
    void scanLocalModels();

    /**
     * Validates if all required models for a registry workflow are available locally.
     * Sets the result directly on the workflow object's `hasAllModelsLocal` field.
     * 
     * If the API does not provide required models (Weg A), it will download the workflow
     * json via `json_download_url`, parse it to extract required models (Weg B), and perform the check.
     *
     * @param workflow the workflow to validate
     * @return a CompletableFuture completing when validation is done
     */
    CompletableFuture<Void> validateWorkflowModelsAsync(ComfyRegistryWorkflow workflow);

    /**
     * Returns the set of all scanned local model file names (base names, lowercase).
     *
     * @return a set of local model base names
     */
    Set<String> getLocalModelBaseNames();

    /**
     * Checks if a model exists in active local model directories.
     */
    default boolean isModelActive(String modelName) {
        return false;
    }

    /**
     * Checks if a model exists in the archive directory.
     */
    default boolean isModelArchived(String modelName) {
        return false;
    }

    /**
     * Returns the set of active local model names and basenames (excluding archive).
     */
    default Set<String> getActiveLocalModelNames() {
        return getLocalModelBaseNames();
    }

    /**
     * Returns cached resolved ModelInfo list for a workflow ID or title if available.
     */
    default java.util.List<de.tki.comfymodels.domain.ModelInfo> getCachedModelsForWorkflow(String idOrTitle) {
        return java.util.Collections.emptyList();
    }
}
