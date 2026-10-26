package de.tki.comfyuicompanion.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for querying and caching dynamic metadata and enumerations
 * from Civitai, such as active base model architectures and model types.
 */
public interface ICivitaiMetadataService {

    /**
     * Retrieves the list of active base model architectures dynamically from Civitai.
     * Caches results in memory to minimize redundant network requests.
     *
     * @return CompletableFuture resolving to the list of active base model names
     */
    CompletableFuture<List<String>> getActiveBaseModels();

    /**
     * Retrieves the list of supported model types dynamically from Civitai.
     * Caches results in memory to minimize redundant network requests.
     *
     * @return CompletableFuture resolving to the list of model types
     */
    CompletableFuture<List<String>> getModelTypes();

    /**
     * Forces an asynchronous refresh of the cached metadata from Civitai.
     *
     * @return CompletableFuture completing when the cache is refreshed
     */
    CompletableFuture<Void> refreshMetadata();
}
