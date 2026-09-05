package de.tki.comfymodels.service.provider;

import de.tki.comfymodels.domain.ModelInfo;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Strategy interface for external and internal model providers (e.g. Civitai, HuggingFace, Local Catalog).
 */
public interface ModelProviderStrategy {

    /**
     * Unique identifier / name of this provider.
     */
    String getProviderName();

    /**
     * Checks if this strategy can resolve or process a query or source type.
     */
    boolean supports(String source);

    /**
     * Searches for models matching the given query asynchronously.
     */
    CompletableFuture<List<ModelInfo>> searchModels(String query);

    /**
     * Looks up a downloadable model or URL by file SHA-256 hash.
     */
    Optional<String> findDownloadUrlByHash(String hash);

    /**
     * Checks whether a newer version or update exists for the given model.
     */
    Optional<ModelInfo> checkForUpdate(ModelInfo current);
}
