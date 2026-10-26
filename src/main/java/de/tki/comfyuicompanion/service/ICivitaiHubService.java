package de.tki.comfyuicompanion.service;

import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.domain.ModelInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for querying, filtering, and resolving models from the Civitai Model Hub.
 */
public interface ICivitaiHubService {

    /**
     * Queries models asynchronously from Civitai with filtering and pagination parameters.
     *
     * @param query     search query (can be empty)
     * @param type      model type (e.g. Checkpoint, LORA, VAE, or null/empty for all)
     * @param baseModel base model architecture (e.g. Flux.1 D, SDXL 1.0, or null/empty for all)
     * @param sort      sorting criteria (e.g. Highest Rated, Most Downloaded, Newest)
     * @param limit     maximum number of results per page
     * @param page      page index (1-based)
     * @param allowNsfw whether NSFW content is permitted
     * @return CompletableFuture resolving to the list of retrieved model entries
     */
    CompletableFuture<List<CivitaiModelEntry>> queryModels(
            String query,
            String type,
            String baseModel,
            String sort,
            int limit,
            int page,
            boolean allowNsfw);

    /**
     * Checks whether a model with the specified filename is installed locally in the ComfyUI models directory.
     *
     * @param filename  canonical filename of the model
     * @param modelType type of the model to determine candidate directories
     * @return true if installed locally, false otherwise
     */
    boolean isModelInstalled(String filename, String modelType);

    /**
     * Checks whether the specified model entry is installed locally in the ComfyUI models directory.
     *
     * @param entry the model entry to check
     * @return true if installed locally, false otherwise
     */
    default boolean isModelInstalled(CivitaiModelEntry entry) {
        if (entry == null) return false;
        return isModelInstalled(entry.filename(), entry.type());
    }

    /**
     * Converts a CivitaiModelEntry to a ModelInfo instance configured with appropriate ComfyUI subfolders.
     *
     * @param entry the model entry
     * @return ModelInfo instance ready for queueing into IDownloadManager
     */
    ModelInfo createModelInfoForDownload(CivitaiModelEntry entry);

    /**
     * Retrieves the list of active base model architectures dynamically from Civitai.
     *
     * @return CompletableFuture resolving to the list of active base model names
     */
    CompletableFuture<List<String>> getActiveBaseModels();

    /**
     * Retrieves the list of supported model types dynamically from Civitai.
     *
     * @return CompletableFuture resolving to the list of model types
     */
    CompletableFuture<List<String>> getModelTypes();
}
