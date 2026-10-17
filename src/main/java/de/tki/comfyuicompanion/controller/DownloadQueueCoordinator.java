package de.tki.comfyuicompanion.controller;

import de.tki.comfyuicompanion.domain.ModelFolder;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.IDownloadManager;
import de.tki.comfyuicompanion.service.impl.ArchiveService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.DownloadManagerView;
import de.tki.comfyuicompanion.util.BackgroundExecutor;
import org.springframework.stereotype.Component;

import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.util.List;
import java.util.function.Consumer;

/**
 * Coordinator managing the asynchronous execution of the download queue,
 * restoring archived models with progress reporting, and dispatching to the core download manager.
 */
@Component
public class DownloadQueueCoordinator {

    /**
     * Initiates the download queue for selected items in the table.
     *
     * @param view download manager view
     * @param modelsToDownload models list
     * @param archiveService archive service for restoring
     * @param downloadManager core download manager
     * @param configService configuration service
     * @param backgroundExecutor executor for background tasks
     * @param setDownloadingState callback to set downloading flag
     * @param updateButtonsState callback to refresh button states
     * @param postOperationCallback optional callback after queue completion
     */
    public void startDownloadQueue(
            DownloadManagerView view,
            List<ModelInfo> modelsToDownload,
            ArchiveService archiveService,
            IDownloadManager downloadManager,
            ConfigService configService,
            BackgroundExecutor backgroundExecutor,
            Consumer<Boolean> setDownloadingState,
            Runnable updateButtonsState,
            Runnable postOperationCallback) {

        if (view == null) return;
        DefaultTableModel tableModel = view.getTableModel();
        int rowCount = tableModel.getRowCount();
        if (rowCount == 0) return;

        boolean[] selected = new boolean[rowCount];
        for (int i = 0; i < rowCount; i++) {
            selected[i] = (Boolean) tableModel.getValueAt(i, 0);
            String url = (String) tableModel.getValueAt(i, 6);
            if (url != null && !url.equals("MISSING") && i < modelsToDownload.size()) {
                modelsToDownload.get(i).setUrl(url);
            }
        }

        setDownloadingState.accept(true);
        updateButtonsState.run();

        backgroundExecutor.execute(() -> {
            for (int i = 0; i < rowCount; i++) {
                if (selected[i] && i < modelsToDownload.size()) {
                    String currentStatus = (String) tableModel.getValueAt(i, 7);
                    ModelInfo info = modelsToDownload.get(i);

                    boolean isArchived = (info.getArchivedPath() != null) || "📦 Archived".equals(currentStatus);

                    if (isArchived) {
                        final int idx = i;
                        String folder = info.getSave_path() != null ? info.getSave_path() : (info.getType() != null ? info.getType() : ModelFolder.CHECKPOINTS.getDefaultFolderName());
                        String normalizedFolder = archiveService.normalizeFolder(folder);

                        SwingUtilities.invokeLater(() -> tableModel.setValueAt("📦 Restoring...", idx, 7));

                        long totalSize = info.getByteSize();
                        long[] currentBytes = {0};
                        long[] lastUpdate = {0};

                        boolean success = archiveService.restoreFromArchiveWithProgress(normalizedFolder, info.getName(), (delta) -> {
                            currentBytes[0] += delta;
                            long now = System.currentTimeMillis();
                            if (totalSize > 0 && (now - lastUpdate[0] > 200)) {
                                lastUpdate[0] = now;
                                int percent = (int) Math.min(100, (currentBytes[0] * 100) / totalSize);
                                SwingUtilities.invokeLater(() -> tableModel.setValueAt("📦 Restoring (" + percent + "%)...", idx, 7));
                            }
                        });

                        SwingUtilities.invokeLater(() -> {
                            if (success) {
                                info.setArchivedPath(null);
                                tableModel.setValueAt("✅ Already exists", idx, 7);
                                tableModel.setValueAt(false, idx, 0);
                            } else {
                                tableModel.setValueAt("❌ Restore Failed - Downloading...", idx, 7);
                            }
                        });
                    }
                }
            }

            SwingUtilities.invokeLater(() -> {
                for (int i = 0; i < rowCount; i++) {
                    selected[i] = (Boolean) tableModel.getValueAt(i, 0);
                }

                downloadManager.startQueue(modelsToDownload, selected, configService.getModelsPath(),
                    (idx, status) -> SwingUtilities.invokeLater(() -> {
                        String current = (String) tableModel.getValueAt(idx, 7);
                        if (status.startsWith("Skipped") && current != null && (current.equals("✅ Already exists") || current.equals("✅ Finished"))) {
                            return;
                        }
                        tableModel.setValueAt(status, idx, 7);
                    }),
                    () -> SwingUtilities.invokeLater(() -> {
                        setDownloadingState.accept(false);
                        updateButtonsState.run();
                        if (view != null && view.getStatusLabel() != null) {
                            view.getStatusLabel().setText("Queue finished.");
                        }
                        if (postOperationCallback != null) {
                            postOperationCallback.run();
                        }
                    })
                );
            });
        });
    }
}
