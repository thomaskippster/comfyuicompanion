package de.tki.comfyuicompanion.service;

import de.tki.comfyuicompanion.domain.AbortedDownloadItem;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Service interface for discovering, inspecting, cleaning, and resuming aborted or incomplete
 * model downloads across the ComfyUI models storage directory.
 */
public interface IAbortedDownloadService {

    /**
     * Scans the base models directory tree for interrupted, temporary, or segmented partial download files.
     *
     * @param baseModelsDir the root directory containing model categories (e.g. checkpoints, loras), or null to use default
     * @return a list of detected incomplete download items, sorted by most recently modified
     */
    List<AbortedDownloadItem> scanAbortedDownloads(String baseModelsDir);

    /**
     * Deletes the specified incomplete download files and any associated segment files safely.
     *
     * @param items the list of aborted download items to remove
     * @return total bytes reclaimed from deleted files
     */
    long deleteAbortedDownloads(List<AbortedDownloadItem> items);

    /**
     * Checks whether an aborted download artifact exists for a specific target model file.
     *
     * @param baseModelsDir  the base models directory, or null for configured default
     * @param folder         the model subfolder (e.g. "checkpoints", "loras")
     * @param targetFileName the expected final model file name
     * @return {@code true} if an aborted download file (.cmfd, .part, etc.) exists
     */
    boolean hasAbortedDownload(String baseModelsDir, String folder, String targetFileName);

    /**
     * Finds the aborted download artifact for a specific model file if one exists.
     *
     * @param baseModelsDir  the base models directory, or null for configured default
     * @param folder         the model subfolder
     * @param targetFileName the expected final model file name
     * @return an {@link Optional} containing the aborted download item if found
     */
    Optional<AbortedDownloadItem> findAbortedDownload(String baseModelsDir, String folder, String targetFileName);

    /**
     * Asynchronously resumes downloading an incomplete model using the download engine.
     *
     * @param item             the aborted download item to resume
     * @param progressConsumer callback for real-time progress and status updates
     * @return a CompletableFuture indicating true if download completed successfully, or false if stopped/failed
     */
    CompletableFuture<Boolean> resumeDownload(AbortedDownloadItem item, Consumer<String> progressConsumer);
}
