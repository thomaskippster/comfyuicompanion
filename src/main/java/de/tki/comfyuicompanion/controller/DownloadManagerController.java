package de.tki.comfyuicompanion.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.IDownloadManager;
import de.tki.comfyuicompanion.service.IModelAnalyzer;
import de.tki.comfyuicompanion.service.IModelSearchService;
import de.tki.comfyuicompanion.service.IModelValidator;
import de.tki.comfyuicompanion.service.IWorkflowService;
import de.tki.comfyuicompanion.service.impl.*;
import de.tki.comfyuicompanion.ui.DownloadManagerView;
import de.tki.comfyuicompanion.ui.dialog.CivitaiVersionSelectorDialog;
import de.tki.comfyuicompanion.ui.dialog.DownloadArchiveDialog;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
import de.tki.comfyuicompanion.util.BackgroundExecutor;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller managing Download Manager operations, model analysis, queue execution,
 * archive operations, model verification, Civitai lookup, and UI table updates.
 */
@Component
public class DownloadManagerController implements DownloadManagerView.DownloadManagerListener {

    private final ConfigService configService;
    private final IDownloadManager downloadManager;
    private final IModelAnalyzer analyzer;
    private final IModelSearchService searchService;
    private final IModelValidator modelValidator;
    private final IWorkflowService workflowService;
    private final ModelListService modelListService;
    private final ArchiveService archiveService;
    private final LocalModelScanner localScanner;
    private final ModelHashRegistry hashRegistry;
    private final ComfyDiagnosticService diagnosticService;
    private final BackgroundExecutor backgroundExecutor;
    private final CivitaiService civitaiService;

    private DownloadManagerView view;
    private List<ModelInfo> modelsToDownload = new ArrayList<>();
    private volatile boolean isDownloading = false;
    private String currentFileName = "input.json";
    private Runnable postOperationCallback;

    public DownloadManagerController(ConfigService configService,
                                     IDownloadManager downloadManager,
                                     IModelAnalyzer analyzer,
                                     IModelSearchService searchService,
                                     IModelValidator modelValidator,
                                     IWorkflowService workflowService,
                                     ModelListService modelListService,
                                     ArchiveService archiveService,
                                     LocalModelScanner localScanner,
                                     ModelHashRegistry hashRegistry,
                                     ComfyDiagnosticService diagnosticService,
                                     @Autowired(required = false) BackgroundExecutor backgroundExecutor,
                                     CivitaiService civitaiService) {
        this.configService = configService;
        this.downloadManager = downloadManager;
        this.analyzer = analyzer;
        this.searchService = searchService;
        this.modelValidator = modelValidator;
        this.workflowService = workflowService;
        this.modelListService = modelListService;
        this.archiveService = archiveService;
        this.localScanner = localScanner;
        this.hashRegistry = hashRegistry;
        this.diagnosticService = diagnosticService;
        this.backgroundExecutor = backgroundExecutor != null ? backgroundExecutor : new BackgroundExecutor();
        this.civitaiService = civitaiService;
    }

    public void setView(DownloadManagerView view) {
        this.view = view;
        if (this.view != null) {
            this.view.setListener(this);
        }
    }

    /**
     * Sets the view reference WITHOUT overriding the view's listener.
     * Use this when an external listener (e.g. Main's anonymous listener)
     * should remain the active listener but the controller still needs
     * access to the view's UI components (table, buttons, etc.).
     */
    public void setViewReference(DownloadManagerView view) {
        this.view = view;
    }

    public DownloadManagerView getView() {
        return view;
    }

    public List<ModelInfo> getModelsToDownload() {
        return modelsToDownload;
    }

    public boolean isDownloading() {
        return isDownloading;
    }

    public void setPostOperationCallback(Runnable postOperationCallback) {
        this.postOperationCallback = postOperationCallback;
    }

    // --- Listener Implementations ---

    @Override
    public void onVerifyLocalModels(boolean deepCheck) {
        verifyLocalModels(deepCheck);
    }

    @Override
    public void onShowArchiveDialog() {
        showArchiveDialog();
    }

    @Override
    public void onRunDiagnostics() {
        runDiagnostics(null);
    }

    @Override
    public void onLoadWorkflowFile(File file) {
        loadFile(file);
    }

    @Override
    public void onImportModelListFile(File file) {
        importModelListFile(file);
    }

    @Override
    public void onAnalyzeJsonContent() {
        analyzeJsonContent();
        searchMissingOnline(true);
    }

    @Override
    public void onShowCivitaiSearchDialog(int modelRowIndex) {
        showCivitaiSearchDialog(modelRowIndex);
    }

    @Override
    public void onUpdateDownloadManagerSelection() {
        updateDownloadManagerSelection();
    }

    @Override
    public void onUpdateDownloadButtonsState() {
        updateDownloadButtonsState();
    }

    @Override
    public void onStartDownloadQueue() {
        startDownloadQueue();
    }

    @Override
    public void onTogglePause() {
        downloadManager.togglePause();
        if (view != null && view.getPauseButton() != null) {
            boolean paused = downloadManager.isPaused();
            view.getPauseButton().setText(paused ? "Resume" : "Pause");
            view.getPauseButton().setIcon(SvgIconFactory.get(paused ? AppIcon.PLAY : AppIcon.PAUSE));
        }
        updateDownloadButtonsState();
    }

    @Override
    public void onStopDownloadQueue() {
        downloadManager.stop();
    }

    // --- Core Operations ---

    public void loadFile(File file) {
        try {
            currentFileName = file.getName();
            if (view != null && view.getJsonInputArea() != null) {
                view.getJsonInputArea().setText(workflowService.extractWorkflow(file));
            }
            analyzeJsonContent();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(view, "Error: " + ex.getMessage());
        }
    }

    public void importWorkflow(File file, JTabbedPane mainTabs, JPanel downloadManagerPanel) {
        if (mainTabs != null && downloadManagerPanel != null) {
            int index = mainTabs.indexOfComponent(downloadManagerPanel);
            if (index != -1) {
                mainTabs.setSelectedIndex(index);
            }
        }
        loadFile(file);
    }

    public void importModelListFile(File file) {
        try {
            modelListService.importJson(file);
            JOptionPane.showMessageDialog(view, "Model list successfully imported! (" + modelListService.getModels().size() + " models)");
            analyzeJsonContent();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(view, "Error: " + ex.getMessage());
        }
    }

    public void analyzeJsonContent() {
        if (view == null) return;
        String text = view.getJsonInputArea().getText();
        if (text == null || text.isEmpty()) return;
        if (view.getWorkflowGraphPanel() != null) {
            view.getWorkflowGraphPanel().setWorkflowJson(text);
        }
        modelsToDownload = analyzer.analyze(text, currentFileName);
        DefaultTableModel tableModel = view.getTableModel();
        tableModel.setRowCount(0);
        String base = configService.getModelsPath();
        String archive = configService.getArchivePath();
        
        for (int i = 0; i < modelsToDownload.size(); i++) {
            ModelInfo info = modelsToDownload.get(i);
            String type = info.getType() != null ? info.getType() : de.tki.comfyuicompanion.domain.ModelFolder.CHECKPOINTS.getDefaultFolderName();
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

            // Persist the resolved archive location on the ModelInfo so downstream code
            // (startDownloadQueue, fetchMissingRemoteSizes) does not have to re-derive it
            // from the mutable status string alone.
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
        
        updateDownloadButtonsState();
        fetchMissingRemoteSizes();
    }

    public void startDownloadQueue() {
        if (view == null) return;
        DefaultTableModel tableModel = view.getTableModel();
        int rowCount = tableModel.getRowCount();
        if (rowCount == 0) return;
        boolean[] selected = new boolean[rowCount];
        
        for (int i = 0; i < rowCount; i++) {
            selected[i] = (Boolean) tableModel.getValueAt(i, 0);
            String url = (String) tableModel.getValueAt(i, 6);
            if (url != null && !url.equals("MISSING")) modelsToDownload.get(i).setUrl(url);
        }

        isDownloading = true;
        updateDownloadButtonsState();

        backgroundExecutor.execute(() -> {
            for (int i = 0; i < rowCount; i++) {
                if (selected[i]) {
                    String currentStatus = (String) tableModel.getValueAt(i, 7);
                    ModelInfo info = modelsToDownload.get(i);

                    // Treat a model as archived if the ModelInfo has a recorded archivedPath
                    // OR if the table status still shows the archived badge. Relying solely on
                    // the status string is fragile because background tasks (fetchMissingRemoteSizes)
                    // may overwrite it before the user clicks "Start queue".
                    boolean isArchived = (info.getArchivedPath() != null)
                            || "📦 Archived".equals(currentStatus);

                    if (isArchived) {
                        final int idx = i;
                        String folder = info.getSave_path() != null ? info.getSave_path() : (info.getType() != null ? info.getType() : de.tki.comfyuicompanion.domain.ModelFolder.CHECKPOINTS.getDefaultFolderName());
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
                                info.setArchivedPath(null); // no longer in archive
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
                        isDownloading = false;
                        updateDownloadButtonsState();
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

    public void updateDownloadManagerSelection() {
        if (downloadManager == null || view == null) return;
        DefaultTableModel tableModel = view.getTableModel();
        if (tableModel == null) return;
        boolean[] selected = new boolean[tableModel.getRowCount()];
        for (int i = 0; i < selected.length; i++) selected[i] = (Boolean) tableModel.getValueAt(i, 0);
        downloadManager.updateSelection(selected);
    }

    public void updateDownloadButtonsState() {
        if (view == null) return;
        DefaultTableModel tableModel = view.getTableModel();
        JButton downloadButton = view.getDownloadButton();
        JButton pauseButton = view.getPauseButton();
        JButton stopButton = view.getStopButton();

        if (downloadButton == null || pauseButton == null || stopButton == null) return;

        boolean hasItems = tableModel != null && tableModel.getRowCount() > 0;
        boolean anySelected = false;
        if (hasItems) {
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                Boolean sel = (Boolean) tableModel.getValueAt(i, 0);
                String status = (String) tableModel.getValueAt(i, 7);
                if (Boolean.TRUE.equals(sel) && status != null && !status.contains("Already exists")) {
                    anySelected = true;
                    break;
                }
            }
        }

        downloadButton.setEnabled(hasItems && anySelected && !isDownloading);
        pauseButton.setEnabled(isDownloading);
        stopButton.setEnabled(isDownloading);

        if (!isDownloading) {
            pauseButton.setText("Pause");
            pauseButton.setIcon(SvgIconFactory.get(AppIcon.PAUSE));
        }
    }

    public void searchMissingOnline() {
        searchMissingOnline(false);
    }

    public void searchMissingOnline(boolean manual) {
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

                // Also treat as archived if it was confirmed during the initial analysis pass
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

    private void fetchMissingRemoteSizes() {
        if (modelsToDownload == null || view == null) return;
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
                                    String type = info.getType() != null ? info.getType() : de.tki.comfyuicompanion.domain.ModelFolder.CHECKPOINTS.getDefaultFolderName();
                                    String folder = info.getSave_path() != null ? info.getSave_path() : type;
                                    String base = configService.getModelsPath();
                                    String archive = configService.getArchivePath();
                                    
                                    Path local = Paths.get(base, archiveService.normalizeFolder(folder), info.getName());
                                    if (!Files.exists(local)) {
                                        local = Paths.get(base, type, info.getName());
                                    }
                                    
                                    if (!Files.exists(local)) {
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
                                        Path archived = Paths.get(archive, archiveService.normalizeFolder(folder), info.getName());
                                        try {
                                            if (Files.exists(archived) && Files.size(archived) == size) {
                                                inArchive = true;
                                            } else {
                                                inArchive = localScanner.findModelWithPrefSize(Paths.get(archive), info.getName(), size).isPresent();
                                            }
                                        } catch (IOException ignored) {}
                                    }

                                    String newStatus;
                                    boolean shouldSelect = false;

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
                                        // archivedPath was recorded during analysis — keep the
                                        // badge even if the live size-based check above could
                                        // not re-confirm it (e.g. byteSize was 0 initially).
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

    public void runDiagnostics(JTabbedPane tabs) {
        if (modelsToDownload == null || modelsToDownload.isEmpty()) {
            JOptionPane.showMessageDialog(view, "Please load a workflow first to perform diagnostics.");
            return;
        }
        List<String> missing = diagnosticService.getMissingModels(modelsToDownload);
        if (missing.isEmpty()) {
            JOptionPane.showMessageDialog(view, "✅ All workflow models are visible to ComfyUI!", "Diagnostics Successful", JOptionPane.INFORMATION_MESSAGE);
        } else {
            String list = String.join("\n- ", missing);
            Object[] options = {"Switch to Overview (Restart)", "Ignore"};
            int choice = JOptionPane.showOptionDialog(view, 
                "❌ ComfyUI still reports these models as missing:\n- " + list + "\n\n" +
                "A restart is required for ComfyUI to recognize new models.", 
                "Diagnostics Failed", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
            
            if (choice == 0 && tabs != null) tabs.setSelectedIndex(0);
        }
    }

    public void verifyLocalModels(boolean checkDuplicates) {
        new ModelVerificationHandler(view, configService, modelValidator, hashRegistry, backgroundExecutor)
                .verifyLocalModels(checkDuplicates);
    }

    public void showArchiveDialog() {
        new DownloadArchiveDialog(view, archiveService, searchService, backgroundExecutor, postOperationCallback, this::analyzeJsonContent)
                .showArchiveDialog();
    }

    public void showCivitaiSearchDialog(int row) {
        new CivitaiVersionSelectorDialog(view, civitaiService, backgroundExecutor)
                .showDialog(row, modelsToDownload);
    }

    public void focusDownloadTabAndSelectModels(List<ModelInfo> missingModels) {
        if (missingModels == null || missingModels.isEmpty()) return;
        if (modelsToDownload == null) {
            modelsToDownload = new ArrayList<>();
        }

        String base = configService.getModelsPath();
        String archive = configService.getArchivePath();

        for (ModelInfo req : missingModels) {
            String name = req.getName();
            String url = req.getUrl();
            if (name == null || name.isBlank()) continue;

            String type = req.getType() != null ? req.getType() : de.tki.comfyuicompanion.domain.ModelFolder.CHECKPOINTS.getDefaultFolderName();
            String folder = req.getSave_path() != null ? req.getSave_path() : type;
            String normalizedFolder = archiveService.normalizeFolder(folder);
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

            if (!inArchive && archive != null && !archive.isEmpty()) {
                Optional<Path> foundInArchive = localScanner.findModelWithPrefSize(Paths.get(archive), name, byteSize);
                if (foundInArchive.isPresent()) {
                    archivedPath = foundInArchive.get();
                    inArchive = true;
                }
            }

            if ((!exists || sizeMismatch) && base != null && !base.isEmpty()) {
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

        updateDownloadManagerSelection();
        updateDownloadButtonsState();

        JOptionPane.showMessageDialog(view,
                "Added " + missingModels.size() + " missing model(s) to the Download Manager queue.\n" +
                        "Please click 'Start queue' to start.",
                "Downloads Queued",
                JOptionPane.INFORMATION_MESSAGE);
    }
}

