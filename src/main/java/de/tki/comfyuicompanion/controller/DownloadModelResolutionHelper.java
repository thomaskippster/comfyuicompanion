package de.tki.comfyuicompanion.controller;

import de.tki.comfyuicompanion.domain.ModelFolder;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.IModelSearchService;
import de.tki.comfyuicompanion.service.impl.ArchiveService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.LocalModelScanner;
import de.tki.comfyuicompanion.ui.DownloadManagerView;
import de.tki.comfyuicompanion.util.BackgroundExecutor;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Helper component handling local and archive filesystem resolution, remote size probing,
 * and queue model mapping for the Download Manager.
 */
public class DownloadModelResolutionHelper {

    private final ConfigService configService;
    private final ArchiveService archiveService;
    private final LocalModelScanner localScanner;
    private final IModelSearchService searchService;
    private final BackgroundExecutor backgroundExecutor;

    public DownloadModelResolutionHelper(ConfigService configService,
                                         ArchiveService archiveService,
                                         LocalModelScanner localScanner,
                                         IModelSearchService searchService,
                                         BackgroundExecutor backgroundExecutor) {
        this.configService = configService;
        this.archiveService = archiveService;
        this.localScanner = localScanner;
        this.searchService = searchService;
        this.backgroundExecutor = backgroundExecutor;
    }

    /**
     * Resolves missing models against local disk and archive, builds table rows, and adds them to the download queue.
     *
     * @param missingModels        the list of requested missing models
     * @param modelsToDownload     the target download list to append to
     * @param view                 the download manager view
     * @param onSelectionUpdated   callback to sync selection states
     * @param onButtonStateUpdated callback to sync button enabled states
     */
    public void focusDownloadTabAndSelectModels(List<ModelInfo> missingModels,
                                               List<ModelInfo> modelsToDownload,
                                               DownloadManagerView view,
                                               Runnable onSelectionUpdated,
                                               Runnable onButtonStateUpdated) {
        if (missingModels == null || missingModels.isEmpty()) return;

        String base = configService != null ? configService.getModelsPath() : null;
        String archive = configService != null ? configService.getArchivePath() : null;

        for (ModelInfo req : missingModels) {
            String name = req.getName();
            String url = req.getUrl();
            if (name == null || name.isBlank()) continue;

            String type = req.getType() != null ? req.getType() : ModelFolder.CHECKPOINTS.getDefaultFolderName();
            String folder = req.getSave_path() != null ? req.getSave_path() : type;
            String normalizedFolder = archiveService != null ? archiveService.normalizeFolder(folder) : folder;
            String sizeStr = req.getSize() != null ? req.getSize() : "Unknown";
            String pop = req.getPopularity() != null ? req.getPopularity() : "📂 BLUEPRINT REQUIRED";
            String dlUrl = (url != null) ? url : "MISSING";
            long byteSize = req.getByteSize();

            boolean exists = false;
            Path local = null;
            if (base != null && !base.isEmpty()) {
                local = "root".equals(normalizedFolder) ? Paths.get(base, name) : Paths.get(base, normalizedFolder, name);
                exists = Files.exists(local) && Files.isRegularFile(local);
            }

            boolean inArchive = false;
            Path archivedPath = null;
            if (archive != null && !archive.trim().isEmpty()) {
                archivedPath = "root".equals(normalizedFolder) ? Paths.get(archive, name) : Paths.get(archive, normalizedFolder, name);
                inArchive = Files.exists(archivedPath) && Files.isRegularFile(archivedPath);
            }

            boolean sizeMismatch = false;

            if (archive != null && !archive.isEmpty()) {
                try {
                    Path absArchive = Paths.get(archive).toAbsolutePath().normalize();
                    if (exists && local != null && local.toAbsolutePath().normalize().startsWith(absArchive)) {
                        exists = false;
                        inArchive = true;
                    }
                    if (inArchive && byteSize > 0 && archivedPath != null) {
                        if (Files.size(archivedPath) != byteSize) {
                            inArchive = false;
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (!inArchive && archive != null && !archive.isEmpty() && localScanner != null) {
                Optional<Path> foundInArchive = localScanner.findModelWithPrefSize(Paths.get(archive), name, byteSize);
                if (foundInArchive.isPresent()) {
                    archivedPath = foundInArchive.get();
                    inArchive = true;
                }
            }

            if ((!exists || sizeMismatch) && base != null && !base.isEmpty() && localScanner != null) {
                Optional<Path> foundLocally = localScanner.findModelWithPrefSizeAndType(Paths.get(base), name, byteSize, type);
                if (foundLocally.isPresent()) {
                    Path potentialLocal = foundLocally.get();
                    try {
                        long potSize = Files.size(potentialLocal);
                        if (byteSize <= 0 || potSize == byteSize) {
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
                            req.setSave_path(normalizedFolder);
                        }
                    } catch (Exception ignored) {}
                }
            }

            String status;
            boolean isSelected;
            if (exists) {
                status = "✅ Already exists";
                isSelected = false;
            } else if (inArchive) {
                status = "📦 Archived";
                isSelected = true;
            } else if (sizeMismatch) {
                status = "🔄 Size Mismatch";
                isSelected = true;
            } else if (dlUrl == null || dlUrl.equals("MISSING") || dlUrl.isBlank()) {
                status = "Queued";
                isSelected = true;
            } else {
                status = "✅ Known Good";
                isSelected = true;
            }

            DefaultTableModel tableModel = (view != null) ? view.getTableModel() : null;
            int foundRow = -1;
            if (tableModel != null) {
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    String rowName = (String) tableModel.getValueAt(i, 2);
                    if (name.equalsIgnoreCase(rowName)) {
                        foundRow = i;
                        break;
                    }
                }

                if (foundRow != -1) {
                    tableModel.setValueAt(isSelected, foundRow, 0);
                    if (url != null && !url.equals("MISSING")) {
                        tableModel.setValueAt(url, foundRow, 6);
                    }
                    tableModel.setValueAt("models/" + normalizedFolder, foundRow, 5);
                    tableModel.setValueAt(status, foundRow, 7);

                    if (foundRow < modelsToDownload.size()) {
                        ModelInfo existingInfo = modelsToDownload.get(foundRow);
                        existingInfo.setSave_path(normalizedFolder);
                        if (url != null && !url.equals("MISSING")) {
                            existingInfo.setUrl(url);
                        }
                    }
                } else {
                    tableModel.addRow(new Object[]{
                            isSelected,
                            req.getType(),
                            name,
                            sizeStr,
                            pop,
                            "models/" + normalizedFolder,
                            dlUrl,
                            status
                    });

                    ModelInfo newInfo = new ModelInfo();
                    newInfo.setName(name);
                    newInfo.setType(req.getType());
                    newInfo.setSize(sizeStr);
                    newInfo.setUrl(dlUrl);
                    newInfo.setPopularity(pop);
                    newInfo.setSave_path(normalizedFolder);
                    newInfo.setByteSize(byteSize);
                    modelsToDownload.add(newInfo);
                }
            }
        }

        if (onSelectionUpdated != null) onSelectionUpdated.run();
        if (onButtonStateUpdated != null) onButtonStateUpdated.run();

        if (view != null) {
            JOptionPane.showMessageDialog(view,
                    "Added " + missingModels.size() + " missing model(s) to the Download Manager queue.\n" +
                            "Please click 'Start queue' to start.",
                    "Downloads Queued",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Probes remote file sizes asynchronously in the background and updates table rows and selection.
     *
     * @param modelsToDownload list of models
     * @param view             the view
     */
    public void fetchMissingRemoteSizes(List<ModelInfo> modelsToDownload, DownloadManagerView view) {
        if (modelsToDownload == null || view == null || backgroundExecutor == null || searchService == null) return;
        DefaultTableModel tableModel = view.getTableModel();
        backgroundExecutor.execute(() -> {
            for (int i = 0; i < modelsToDownload.size(); i++) {
                final int idx = i;
                ModelInfo info = modelsToDownload.get(idx);

                String status = (idx < tableModel.getRowCount()) ? (String) tableModel.getValueAt(idx, 7) : "";
                boolean needsCheck = "Unknown".equals(info.getSize()) || "🔄 Size Mismatch".equals(status);

                if (!info.getUrl().equals("MISSING") && needsCheck) {
                    long size = searchService.getRemoteSize(info.getUrl());
                    if (size > 0) {
                        info.setByteSize(size);
                        String formatted = searchService.formatSize(size);
                        info.setSize(formatted);
                        SwingUtilities.invokeLater(() -> {
                            if (idx < tableModel.getRowCount()) {
                                tableModel.setValueAt(formatted, idx, 3);

                                String currentStatus = (String) tableModel.getValueAt(idx, 7);
                                if (currentStatus.contains("Already exists") || "🔄 Size Mismatch".equals(currentStatus) || "📦 Archived".equals(currentStatus)) {
                                    String type = info.getType() != null ? info.getType() : ModelFolder.CHECKPOINTS.getDefaultFolderName();
                                    String folder = info.getSave_path() != null ? info.getSave_path() : type;
                                    String base = configService != null ? configService.getModelsPath() : null;
                                    String archive = configService != null ? configService.getArchivePath() : null;

                                    Path local = Paths.get(base, archiveService != null ? archiveService.normalizeFolder(folder) : folder, info.getName());
                                    if (!Files.exists(local)) {
                                        local = Paths.get(base, type, info.getName());
                                    }

                                    if (!Files.exists(local) && localScanner != null) {
                                        Optional<Path> recursiveLocal = localScanner.findModelWithPrefSize(Paths.get(base), info.getName(), size);
                                        if (recursiveLocal.isPresent()) local = recursiveLocal.get();
                                    }

                                    boolean localExists = Files.exists(local);
                                    boolean localSizeMatch = false;
                                    if (localExists) {
                                        try {
                                            localSizeMatch = (Files.size(local) == size);
                                        } catch (IOException ignored) {}
                                    }

                                    boolean inArchive = false;
                                    if (archive != null && !archive.isEmpty()) {
                                        Path archived = Paths.get(archive, archiveService != null ? archiveService.normalizeFolder(folder) : folder, info.getName());
                                        try {
                                            if (Files.exists(archived) && Files.size(archived) == size) {
                                                inArchive = true;
                                            } else if (localScanner != null) {
                                                inArchive = localScanner.findModelWithPrefSize(Paths.get(archive), info.getName(), size).isPresent();
                                            }
                                        } catch (IOException ignored) {}
                                    }

                                    String newStatus;
                                    boolean shouldSelect;

                                    if (localSizeMatch) {
                                        newStatus = "✅ Already exists";
                                        shouldSelect = false;
                                    } else if (localExists) {
                                        newStatus = "🔄 Size Mismatch";
                                        shouldSelect = true;
                                    } else if (inArchive) {
                                        newStatus = "📦 Archived";
                                        shouldSelect = true;
                                    } else if (info.getArchivedPath() != null) {
                                        newStatus = "📦 Archived";
                                        shouldSelect = true;
                                    } else {
                                        newStatus = "✅ Known Good";
                                        shouldSelect = true;
                                    }

                                    final String finalStatus = newStatus;
                                    final boolean finalSelect = shouldSelect;
                                    SwingUtilities.invokeLater(() -> {
                                        tableModel.setValueAt(finalStatus, idx, 7);
                                        tableModel.setValueAt(finalSelect, idx, 0);
                                    });
                                }
                            }
                        });
                    }
                }
            }
        });
    }
}
