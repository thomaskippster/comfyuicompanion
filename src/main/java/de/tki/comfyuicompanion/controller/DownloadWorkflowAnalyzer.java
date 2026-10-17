package de.tki.comfyuicompanion.controller;

import de.tki.comfyuicompanion.domain.ModelFolder;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.IModelAnalyzer;
import de.tki.comfyuicompanion.service.IModelSearchService;
import de.tki.comfyuicompanion.service.impl.ArchiveService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.LocalModelScanner;
import de.tki.comfyuicompanion.ui.DownloadManagerView;
import org.springframework.stereotype.Component;

import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Coordinator analyzing workflow JSON for required models, resolving local
 * and archived presence, and searching for missing models online.
 */
@Component
public class DownloadWorkflowAnalyzer {

    /**
     * Analyzes JSON workflow text and populates the view's model table.
     *
     * @param view download manager view
     * @param currentFileName current workflow filename
     * @param analyzer model analyzer
     * @param configService config service
     * @param archiveService archive service
     * @param localScanner local model scanner
     * @param searchService model search service
     * @param resolutionHelper resolution helper
     * @param updateButtonsState callback to update button state
     * @return analyzed list of models to download
     */
    public List<ModelInfo> analyzeJsonContent(
            DownloadManagerView view,
            String currentFileName,
            IModelAnalyzer analyzer,
            ConfigService configService,
            ArchiveService archiveService,
            LocalModelScanner localScanner,
            IModelSearchService searchService,
            DownloadModelResolutionHelper resolutionHelper,
            Runnable updateButtonsState) {

        if (view == null) return new ArrayList<>();
        String text = view.getJsonInputArea().getText();
        if (text == null || text.isEmpty()) return new ArrayList<>();
        if (view.getWorkflowGraphPanel() != null) {
            view.getWorkflowGraphPanel().setWorkflowJson(text);
        }

        List<ModelInfo> modelsToDownload = analyzer.analyze(text, currentFileName);
        DefaultTableModel tableModel = view.getTableModel();
        tableModel.setRowCount(0);
        String base = configService.getModelsPath();
        String archive = configService.getArchivePath();

        for (int i = 0; i < modelsToDownload.size(); i++) {
            ModelInfo info = modelsToDownload.get(i);
            String type = info.getType() != null ? info.getType() : ModelFolder.CHECKPOINTS.getDefaultFolderName();
            String folder = info.getSave_path() != null ? info.getSave_path() : type;

            String normalizedFolder = archiveService.normalizeFolder(folder);

            Path local = "root".equals(normalizedFolder) ? Paths.get(base, info.getName()) : Paths.get(base, normalizedFolder, info.getName());
            boolean exists = Files.exists(local) && Files.isRegularFile(local);

            boolean inArchive = false;
            Path archivedPath = null;
            if (archive != null && !archive.trim().isEmpty()) {
                archivedPath = "root".equals(normalizedFolder) ? Paths.get(archive, info.getName()) : Paths.get(archive, normalizedFolder, info.getName());
                inArchive = Files.exists(archivedPath) && Files.isRegularFile(archivedPath);
            }

            boolean sizeMismatch = false;

            if (archive != null && !archive.isEmpty()) {
                try {
                    Path absArchive = Paths.get(archive).toAbsolutePath().normalize();
                    if (exists && local.toAbsolutePath().normalize().startsWith(absArchive)) {
                        exists = false;
                        inArchive = true;
                    }
                    if (inArchive && info.getByteSize() > 0) {
                        if (Files.size(archivedPath) != info.getByteSize()) {
                            inArchive = false;
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (!inArchive && archive != null && !archive.isEmpty()) {
                Optional<Path> foundInArchive = localScanner.findModelWithPrefSize(Paths.get(archive), info.getName(), info.getByteSize());
                if (foundInArchive.isPresent()) {
                    archivedPath = foundInArchive.get();
                    inArchive = true;
                }
            }

            if (inArchive && archivedPath != null) {
                info.setArchivedPath(archivedPath.toAbsolutePath().toString());
            }

            if ((!exists || sizeMismatch) && base != null && !base.isEmpty()) {
                Optional<Path> foundLocally = localScanner.findModelWithPrefSizeAndType(Paths.get(base), info.getName(), info.getByteSize(), type);
                if (foundLocally.isPresent()) {
                    Path potentialLocal = foundLocally.get();
                    try {
                        long potSize = Files.size(potentialLocal);
                        if (info.getByteSize() <= 0 || potSize == info.getByteSize()) {
                            local = potentialLocal;
                            exists = true;
                            sizeMismatch = false;

                            Path root = Paths.get(base).toAbsolutePath().normalize();
                            Path absPotential = potentialLocal.toAbsolutePath().normalize();

                            if (absPotential.startsWith(root)) {
                                Path rel = root.relativize(absPotential);
                                normalizedFolder = (rel.getParent() != null) ? rel.getParent().toString().replace("\\", "/") : "root";
                            } else {
                                normalizedFolder = "extra/" + potentialLocal.getParent().getFileName();
                            }
                            info.setSave_path(normalizedFolder);
                        }
                    } catch (Exception ignored) {}
                }
            }

            if ("Unknown".equalsIgnoreCase(info.getSize()) || info.getSize() == null || info.getSize().isBlank()) {
                Path fileToMeasure = null;
                if (exists && local != null) {
                    fileToMeasure = local;
                } else if (inArchive && archivedPath != null) {
                    fileToMeasure = archivedPath;
                }
                if (fileToMeasure != null && Files.exists(fileToMeasure)) {
                    try {
                        long bytes = Files.size(fileToMeasure);
                        if (bytes > 0) {
                            info.setByteSize(bytes);
                            info.setSize(searchService.formatSize(bytes));
                        }
                    } catch (Exception ignored) {}
                }
            }

            String status;
            if (exists) {
                status = "✅ Already exists";
            } else if (inArchive) {
                status = "📦 Archived";
            } else if (sizeMismatch) {
                status = "🔄 Size Mismatch";
            } else if (info.getUrl().equals("MISSING")) {
                status = "Idle";
            } else {
                status = "✅ Known Good";
            }

            boolean isSelected = !exists;
            tableModel.addRow(new Object[]{isSelected, info.getType(), info.getName(), info.getSize(), info.getPopularity(), "models/" + normalizedFolder, info.getUrl(), status});
        }

        updateButtonsState.run();
        resolutionHelper.fetchMissingRemoteSizes(modelsToDownload, view);
        return modelsToDownload;
    }

    /**
     * Searches for missing models online and updates table status.
     *
     * @param view download manager view
     * @param modelsToDownload models list
     * @param currentFileName current workflow file name
     * @param manual whether user initiated
     * @param searchService model search service
     * @param configService config service
     * @param localScanner local model scanner
     */
    public void searchMissingOnline(
            DownloadManagerView view,
            List<ModelInfo> modelsToDownload,
            String currentFileName,
            boolean manual,
            IModelSearchService searchService,
            ConfigService configService,
            LocalModelScanner localScanner) {

        if (modelsToDownload == null || view == null) return;
        DefaultTableModel tableModel = view.getTableModel();
        if (view.getStatusLabel() != null) view.getStatusLabel().setText("Searching...");
        boolean[] selected = new boolean[tableModel.getRowCount()];
        for (int i = 0; i < selected.length; i++) selected[i] = (Boolean) tableModel.getValueAt(i, 0);

        searchService.searchOnline(modelsToDownload, selected, view.getJsonInputArea().getText(), currentFileName, manual,
            (idx, status) -> SwingUtilities.invokeLater(() -> {
                String current = (String) tableModel.getValueAt(idx, 7);
                if (current != null && (current.contains("✅") || current.contains("📦"))) {
                    if (status.contains("No trusted match") || status.startsWith("🔍") || status.startsWith("✨")) {
                        return;
                    }
                }
                tableModel.setValueAt(status, idx, 7);
            }),
            (idx, info) -> SwingUtilities.invokeLater(() -> {
                tableModel.setValueAt(info.getSize(), idx, 3);
                tableModel.setValueAt(info.getUrl(), idx, 6);

                String base = configService.getModelsPath();
                String archive = configService.getArchivePath();
                long size = info.getByteSize();

                boolean exists = false;
                boolean inArchive = false;

                if (base != null && !base.isEmpty()) {
                    Optional<Path> foundLocally = localScanner.findModelWithPrefSize(Paths.get(base), info.getName(), size);
                    if (foundLocally.isPresent()) {
                        exists = true;
                        try {
                            Path rel = Paths.get(base).relativize(foundLocally.get());
                            tableModel.setValueAt("models/" + rel.getParent().toString().replace("\\", "/"), idx, 5);
                        } catch (Exception ignored) {}
                    }
                }

                if (!exists && archive != null && !archive.isEmpty()) {
                    Optional<Path> foundInArchive = localScanner.findModelWithPrefSize(Paths.get(archive), info.getName(), size);
                    if (foundInArchive.isPresent()) {
                        inArchive = true;
                        info.setArchivedPath(foundInArchive.get().toAbsolutePath().toString());
                    }
                }

                if (!exists && !inArchive && info.getArchivedPath() != null) {
                    inArchive = true;
                }

                String newStatus;
                if (exists) {
                    newStatus = "✅ Already exists";
                } else if (inArchive) {
                    newStatus = "📦 Archived";
                } else {
                    newStatus = "✅ Known Good";
                }

                tableModel.setValueAt(newStatus, idx, 7);
                tableModel.setValueAt(!exists, idx, 0);
            }),
            () -> SwingUtilities.invokeLater(() -> {
                if (view != null && view.getStatusLabel() != null) {
                    view.getStatusLabel().setText("Search finished.");
                }
            })
        );
    }
}
