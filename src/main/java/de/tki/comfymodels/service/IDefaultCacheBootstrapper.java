package de.tki.comfymodels.service;

import java.nio.file.Path;

/**
 * Interface defining the default cache bootstrapper contract.
 * Seeds non-sensitive pre-computed caches from classpath resources into local application storage.
 */
public interface IDefaultCacheBootstrapper {

    /**
     * Bootstraps default cache files into the standard application storage directories
     * if they are missing or empty.
     */
    void bootstrapDefaultCache();

    /**
     * Bootstraps default cache files into the designated base directory.
     *
     * @param targetBaseDirectory designated base directory (e.g. ~/.comfyui-companion)
     */
    void bootstrapDefaultCache(Path targetBaseDirectory);
}
