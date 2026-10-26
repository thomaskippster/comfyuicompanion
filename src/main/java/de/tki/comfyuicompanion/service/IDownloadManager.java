package de.tki.comfyuicompanion.service;

import de.tki.comfyuicompanion.domain.ModelInfo;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Interface coordinating model download queues, pause/stop states,
 * live progress reporting, observer subscriptions, and ComfyUI bridge notification.
 */
public interface IDownloadManager {

    /**
     * Listener callback interface for real-time model download progress updates.
     */
    @FunctionalInterface
    interface DownloadProgressListener {
        /**
         * Invoked whenever the download status of a specific model changes.
         *
         * @param filename the canonical filename or model name
         * @param status   the updated status string (e.g. "⏳ Downloading 45% (12 MB/s)", "✅ Finished", "Stopped")
         */
        void onProgress(String filename, String status);
    }

    void startQueue(List<ModelInfo> models, boolean[] selectedIndices, String baseDir, BiConsumer<Integer, String> statusUpdater, Runnable onFinished);
    void updateSelection(boolean[] selectedIndices);
    void togglePause();
    void stop();
    boolean isPaused();

    /**
     * Checks if the download queue is currently running.
     *
     * @return true if downloads are actively in progress
     */
    default boolean isQueueRunning() {
        return false;
    }

    /**
     * Checks whether the specified model filename is currently being downloaded or queued.
     *
     * @param filename the model filename
     * @return true if currently active in download queue
     */
    default boolean isDownloading(String filename) {
        return false;
    }

    /**
     * Retrieves the current download status for a given model filename if active.
     *
     * @param filename the model filename
     * @return Optional containing current status or empty if not active
     */
    default Optional<String> getActiveDownloadStatus(String filename) {
        return Optional.empty();
    }

    /**
     * Stops download of a specific model in the active queue.
     *
     * @param filename the model filename to stop
     */
    default void stopDownload(String filename) {
    }

    /**
     * Adds a listener for real-time download status updates by filename.
     *
     * @param listener the progress listener
     */
    default void addProgressListener(DownloadProgressListener listener) {
    }

    /**
     * Removes a previously registered download progress listener.
     *
     * @param listener the progress listener
     */
    default void removeProgressListener(DownloadProgressListener listener) {
    }

    /**
     * Triggers a refresh signal to the ComfyUI bridge.
     */
    void notifyComfyUI();

    /**
     * Triggers a refresh signal and optionally forces a browser reload.
     */
    void notifyComfyUI(boolean forceReload);

    /**
     * Returns a map of current statuses for each model in the queue.
     */
    Map<Integer, String> getQueueStatus();
}
