package de.tki.comfymodels.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.IDownloadManager;
import de.tki.comfymodels.service.IModelAnalyzer;
import de.tki.comfymodels.service.IModelSearchService;
import de.tki.comfymodels.service.IModelValidator;
import de.tki.comfymodels.service.IWorkflowService;
import de.tki.comfymodels.service.impl.*;
import de.tki.comfymodels.ui.DownloadManagerView;
import de.tki.comfymodels.util.BackgroundExecutor;

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
            view.getPauseButton().setText(downloadManager.isPaused() ? "Resume" : "Pause");
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
            String type = info.getType() != null ? info.getType() : de.tki.comfymodels.domain.ModelFolder.CHECKPOINTS.getDefaultFolderName();
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
                        String folder = info.getSave_path() != null ? info.getSave_path() : (info.getType() != null ? info.getType() : de.tki.comfymodels.domain.ModelFolder.CHECKPOINTS.getDefaultFolderName());
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
                                    String type = info.getType() != null ? info.getType() : de.tki.comfymodels.domain.ModelFolder.CHECKPOINTS.getDefaultFolderName();
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
        String baseDir = configService.getModelsPath();
        if (baseDir == null || baseDir.isEmpty()) {
            JOptionPane.showMessageDialog(view, "Please set a models directory first.");
            return;
        }

        File root = new File(baseDir);
        if (!root.exists() || !root.isDirectory()) {
            JOptionPane.showMessageDialog(view, "Invalid models directory.");
            return;
        }

        String taskName = checkDuplicates ? "Verifying models & checking for duplicates" : "Verifying models (Fast Check)";
        if (view != null && view.getStatusLabel() != null) view.getStatusLabel().setText(taskName + "... please wait.");
        backgroundExecutor.execute(() -> {
            try {
                List<IModelValidator.ValidationResult> errors = new ArrayList<>();
                Map<String, List<Path>> hashToPaths = new HashMap<>();

                List<Path> allFiles;
                try (java.util.stream.Stream<Path> pathStream = Files.walk(root.toPath())) {
                    allFiles = pathStream
                            .filter(Files::isRegularFile)
                            .filter(p -> {
                                String relPath = root.toPath().relativize(p).toString().toLowerCase();
                                return !relPath.contains(".venv") && !relPath.contains("archive");
                            })
                            .filter(p -> {
                                String n = p.getFileName().toString().toLowerCase();
                                return n.endsWith(".safetensors") || n.endsWith(".sft") || n.endsWith(".ckpt") || n.endsWith(".pth") || n.endsWith(".pt") || n.endsWith(".bin") || n.endsWith(".onnx");
                            })
                            .collect(Collectors.toList());
                }

                int total = allFiles.size();
                for (int i = 0; i < total; i++) {
                    Path p = allFiles.get(i);
                    final int current = i + 1;
                    SwingUtilities.invokeLater(() -> {
                        if (view != null && view.getStatusLabel() != null) view.getStatusLabel().setText("Checking (" + current + "/" + total + "): " + p.getFileName());
                    });

                    IModelValidator.ValidationResult res = modelValidator.validateFile(p.toFile());
                    if (!res.ok) {
                        errors.add(res);
                    } else if (checkDuplicates) {
                        String hash = hashRegistry.getOrCalculateHash(p.toFile());
                        if (hash != null) {
                            hashToPaths.computeIfAbsent(hash, k -> new ArrayList<>()).add(p);
                        }
                    }
                }

                Map<String, List<Path>> duplicates = checkDuplicates ? hashToPaths.entrySet().stream()
                        .filter(e -> e.getValue().size() > 1)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)) : new HashMap<>();

                SwingUtilities.invokeLater(() -> {
                    if (view != null && view.getStatusLabel() != null) {
                        view.getStatusLabel().setText("Scan finished. Found " + errors.size() + " issues and " + duplicates.size() + " duplicate sets.");
                    }

                    if (errors.isEmpty() && duplicates.isEmpty()) {
                        JOptionPane.showMessageDialog(view, "All " + total + " models verified successfully!", "Verification Complete", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }

                    if (!errors.isEmpty()) {
                        StringBuilder sb = new StringBuilder("The following " + errors.size() + " files appear to be corrupted or invalid:\n\n");
                        for (IModelValidator.ValidationResult err : errors) {
                            sb.append("- ").append(new File(err.filePath).getName())
                              .append(" (").append(err.message).append(")\n")
                              .append("  Path: ").append(err.filePath).append("\n\n");
                        }

                        JTextArea textArea = new JTextArea(sb.toString());
                        textArea.setEditable(false);
                        JScrollPane scrollPane = new JScrollPane(textArea);
                        scrollPane.setPreferredSize(new Dimension(800, 500));

                        Object[] options = {"OK", "Delete All Corrupted Files"};
                        int choice = JOptionPane.showOptionDialog(view, scrollPane, "Verification Results - Issues Found", 
                                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);

                        if (choice == 1) {
                            int confirm = JOptionPane.showConfirmDialog(view, 
                                "Are you sure you want to delete these " + errors.size() + " files?\nThis action cannot be undone.",
                                "Confirm Deletion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

                            if (confirm == JOptionPane.YES_OPTION) {
                                int deletedCount = 0;
                                for (IModelValidator.ValidationResult err : errors) {
                                    File f = new File(err.filePath);
                                    if (f.exists() && f.delete()) {
                                        deletedCount++;
                                    }
                                }
                                JOptionPane.showMessageDialog(view, "Deleted " + deletedCount + " files.");
                                if (view != null && view.getStatusLabel() != null) view.getStatusLabel().setText("Cleanup finished. Deleted " + deletedCount + " files.");
                            }
                        }
                    }
                    
                    if (!duplicates.isEmpty()) {
                        showDuplicatesDialog(duplicates);
                    }
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(view, "Error during verification: " + e.getMessage()));
            }
        });
    }

    private void showDuplicatesDialog(Map<String, List<Path>> duplicates) {
        StringBuilder sb = new StringBuilder("Storage Optimizer - Duplicate Models Found:\n\n");
        sb.append("The following files have identical content (SHA-256 match).\n");
        sb.append("You might want to delete redundant copies to save space.\n\n");

        for (Map.Entry<String, List<Path>> entry : duplicates.entrySet()) {
            sb.append("SHA-256: ").append(entry.getKey()).append("\n");
            for (Path p : entry.getValue()) {
                sb.append("  -> ").append(p.toAbsolutePath()).append("\n");
            }
            sb.append("\n");
        }

        JTextArea textArea = new JTextArea(sb.toString());
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(900, 600));
        JOptionPane.showMessageDialog(view, scrollPane, "Duplicates Found", JOptionPane.INFORMATION_MESSAGE);
    }

    public void showArchiveDialog() {
        JDialog dialog = new JDialog((JFrame) SwingUtilities.getWindowAncestor(view), "Archive Manager", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(1000, 700);
        dialog.setLocationRelativeTo(view);

        JTabbedPane tabs = new JTabbedPane();
        
        JPanel archivePanel = new JPanel(new BorderLayout(10, 10));
        archivePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        Map<String, List<ModelInfo>> groupedToArchive = archiveService.getModelsGroupedByFolder();
        String[] columns = {"Select", "Folder", "Name", "Size"};
        DefaultTableModel archiveTableModel = new DefaultTableModel(columns, 0) {
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : String.class; }
            @Override public boolean isCellEditable(int r, int c) { return c == 0; }
        };

        for (Map.Entry<String, List<ModelInfo>> entry : groupedToArchive.entrySet()) {
            for (ModelInfo model : entry.getValue()) {
                archiveTableModel.addRow(new Object[]{false, entry.getKey(), model.getName(), model.getSize()});
            }
        }

        Map<String, List<ModelInfo>> groupedToRestore = archiveService.getArchivedModelsGroupedByFolder();
        DefaultTableModel restoreTableModel = new DefaultTableModel(columns, 0) {
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : String.class; }
            @Override public boolean isCellEditable(int r, int c) { return c == 0; }
        };

        for (Map.Entry<String, List<ModelInfo>> entry : groupedToRestore.entrySet()) {
            for (ModelInfo model : entry.getValue()) {
                restoreTableModel.addRow(new Object[]{false, entry.getKey(), model.getName(), model.getSize()});
            }
        }

        JTable archiveTable = new JTable(archiveTableModel);
        archiveTable.setRowHeight(25);
        archiveTable.getColumnModel().getColumn(0).setMaxWidth(50);
        
        javax.swing.table.TableRowSorter<DefaultTableModel> archiveSorter = new javax.swing.table.TableRowSorter<>(archiveTableModel);
        archiveSorter.setComparator(3, (s1, s2) -> Long.compare(parseSizeToBytes((String)s1), parseSizeToBytes((String)s2)));
        archiveSorter.setSortable(0, false);
        archiveTable.setRowSorter(archiveSorter);

        archiveTable.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewIdx = archiveTable.columnAtPoint(e.getPoint());
                if (viewIdx == -1) return;
                int modelIdx = archiveTable.convertColumnIndexToModel(viewIdx);
                if (modelIdx == 0) {
                    boolean allSelected = true;
                    for (int i = 0; i < archiveTableModel.getRowCount(); i++) {
                        if (!(Boolean) archiveTableModel.getValueAt(i, 0)) {
                            allSelected = false;
                            break;
                        }
                    }
                    boolean newValue = !allSelected;
                    for (int i = 0; i < archiveTableModel.getRowCount(); i++) {
                        archiveTableModel.setValueAt(newValue, i, 0);
                    }
                }
            }
        });

        JScrollPane archiveScroll = new JScrollPane(archiveTable);
        archivePanel.add(archiveScroll, BorderLayout.CENTER);

        JPanel archiveBottom = new JPanel(new BorderLayout());
        JLabel archiveCountLabel = new JLabel("Selected: 0 models");
        archiveTableModel.addTableModelListener(e -> {
            int selected = 0;
            for (int i = 0; i < archiveTableModel.getRowCount(); i++) {
                if ((Boolean) archiveTableModel.getValueAt(i, 0)) selected++;
            }
            archiveCountLabel.setText("Selected: " + selected + " models");
        });
        
        JButton archiveNowBtn = new JButton("📦 Move to Archive");
        archiveNowBtn.putClientProperty("JButton.buttonType", "accent");
        archiveNowBtn.addActionListener(e -> {
            List<Integer> selectedRows = new ArrayList<>();
            for (int i = 0; i < archiveTableModel.getRowCount(); i++) if ((Boolean) archiveTableModel.getValueAt(i, 0)) selectedRows.add(i);
            if (selectedRows.isEmpty()) return;
            
            showModalProgressDialog("Archive", "Archiving", selectedRows, archiveTableModel, restoreTableModel, (folder, name, update) -> {
                try {
                    archiveService.moveToArchiveWithProgress(folder, name, update);
                    return true;
                } catch (IOException ex) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(dialog, 
                            "Failed to archive '" + name + "':\n" + ex.getMessage(), 
                            "Archive Error", JOptionPane.ERROR_MESSAGE);
                    });
                    return false;
                }
            });
        });

        JPanel archiveActionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        archiveActionPanel.add(archiveNowBtn);
        archiveBottom.add(archiveCountLabel, BorderLayout.WEST);
        archiveBottom.add(archiveActionPanel, BorderLayout.EAST);
        archivePanel.add(archiveBottom, BorderLayout.SOUTH);

        JPanel restorePanel = new JPanel(new BorderLayout(10, 10));
        restorePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JTable restoreTable = new JTable(restoreTableModel);
        restoreTable.setRowHeight(25);
        restoreTable.getColumnModel().getColumn(0).setMaxWidth(50);
        
        javax.swing.table.TableRowSorter<DefaultTableModel> restoreSorter = new javax.swing.table.TableRowSorter<>(restoreTableModel);
        restoreSorter.setComparator(3, (s1, s2) -> Long.compare(parseSizeToBytes((String)s1), parseSizeToBytes((String)s2)));
        restoreSorter.setSortable(0, false);
        restoreTable.setRowSorter(restoreSorter);

        restoreTable.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewIdx = restoreTable.columnAtPoint(e.getPoint());
                if (viewIdx == -1) return;
                int modelIdx = restoreTable.convertColumnIndexToModel(viewIdx);
                if (modelIdx == 0) {
                    boolean allSelected = true;
                    for (int i = 0; i < restoreTableModel.getRowCount(); i++) {
                        if (!(Boolean) restoreTableModel.getValueAt(i, 0)) {
                            allSelected = false;
                            break;
                        }
                    }
                    boolean newValue = !allSelected;
                    for (int i = 0; i < restoreTableModel.getRowCount(); i++) {
                        restoreTableModel.setValueAt(newValue, i, 0);
                    }
                }
            }
        });

        JScrollPane restoreScroll = new JScrollPane(restoreTable);
        restorePanel.add(restoreScroll, BorderLayout.CENTER);

        JPanel restoreBottom = new JPanel(new BorderLayout());
        JLabel restoreCountLabel = new JLabel("Selected: 0 models");
        restoreTableModel.addTableModelListener(e -> {
            int selected = 0;
            for (int i = 0; i < restoreTableModel.getRowCount(); i++) {
                if ((Boolean) restoreTableModel.getValueAt(i, 0)) selected++;
            }
            restoreCountLabel.setText("Selected: " + selected + " models");
        });

        JButton restoreNowBtn = new JButton("🚀 Restore from Archive");
        restoreNowBtn.putClientProperty("JButton.buttonType", "accent");
        restoreNowBtn.addActionListener(e -> {
            List<Integer> selectedRows = new ArrayList<>();
            for (int i = 0; i < restoreTableModel.getRowCount(); i++) if ((Boolean) restoreTableModel.getValueAt(i, 0)) selectedRows.add(i);
            if (selectedRows.isEmpty()) return;
            
            showModalProgressDialog("Restore", "Restoring", selectedRows, restoreTableModel, archiveTableModel, (folder, name, update) -> {
                return archiveService.restoreFromArchiveWithProgress(folder, name, update);
            });
        });

        JPanel restoreActionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        restoreActionPanel.add(restoreNowBtn);
        restoreBottom.add(restoreCountLabel, BorderLayout.WEST);
        restoreBottom.add(restoreActionPanel, BorderLayout.EAST);
        restorePanel.add(restoreBottom, BorderLayout.SOUTH);

        tabs.addTab("📦 Archive Models", archivePanel);
        tabs.addTab("🚀 Restore Models", restorePanel);
        
        dialog.add(tabs, BorderLayout.CENTER);
        
        JPanel dialogButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dialog.dispose());
        dialogButtons.add(closeBtn);
        dialog.add(dialogButtons, BorderLayout.SOUTH);
        
        dialog.setVisible(true);
    }

    public void showCivitaiSearchDialog(int row) {
        if (view == null) return;
        DefaultTableModel tableModel = view.getTableModel();
        String initialName = (String) tableModel.getValueAt(row, 2);
        String cleanedQuery = initialName.replaceAll("(?i)\\.(safetensors|ckpt|sft|pt|bin)$", "").trim();

        JDialog dialog = new JDialog((JFrame) SwingUtilities.getWindowAncestor(view), "Civitai Version Selector", true);
        dialog.setSize(950, 650);
        dialog.setLocationRelativeTo(view);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel leftPanel = new JPanel(new BorderLayout(10, 10));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JPanel searchBar = new JPanel(new BorderLayout(5, 0));
        JTextField searchField = new JTextField(cleanedQuery);
        JButton searchBtn = new JButton("Search");
        searchBtn.putClientProperty("JButton.buttonType", "accent");
        searchBar.add(searchField, BorderLayout.CENTER);
        searchBar.add(searchBtn, BorderLayout.EAST);
        leftPanel.add(searchBar, BorderLayout.NORTH);
        
        DefaultListModel<CivitaiModel> modelListModel = new DefaultListModel<>();
        JList<CivitaiModel> modelList = new JList<>(modelListModel);
        modelList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane listScroll = new JScrollPane(modelList);
        leftPanel.add(listScroll, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout(10, 10));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JPanel versionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        versionPanel.add(new JLabel("Version:"));
        JComboBox<CivitaiVersion> versionCombo = new JComboBox<>();
        versionPanel.add(versionCombo);
        rightPanel.add(versionPanel, BorderLayout.NORTH);
        
        JPanel detailsPanel = new JPanel(new BorderLayout(5, 5));
        JTextArea infoText = new JTextArea();
        infoText.setEditable(false);
        infoText.setLineWrap(true);
        infoText.setWrapStyleWord(true);
        JScrollPane infoScroll = new JScrollPane(infoText);
        infoScroll.setPreferredSize(new Dimension(350, 180));
        detailsPanel.add(infoScroll, BorderLayout.NORTH);
        
        JLabel previewLabel = new JLabel("No Preview Image Available", JLabel.CENTER);
        previewLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        previewLabel.setPreferredSize(new Dimension(350, 350));
        detailsPanel.add(previewLabel, BorderLayout.CENTER);
        
        rightPanel.add(detailsPanel, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(420);
        dialog.add(splitPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dialog.dispose());
        JButton applyBtn = new JButton("Apply Selected Version");
        applyBtn.putClientProperty("JButton.buttonType", "accent");
        
        bottomPanel.add(cancelBtn);
        bottomPanel.add(applyBtn);
        dialog.add(bottomPanel, BorderLayout.SOUTH);

        Runnable doSearch = () -> {
            String q = searchField.getText().trim();
            if (q.isEmpty()) return;
            searchBtn.setEnabled(false);
            civitaiService.searchModelsRaw(q).thenAccept(root -> {
                SwingUtilities.invokeLater(() -> {
                    searchBtn.setEnabled(true);
                    modelListModel.clear();
                    if (root != null) {
                        com.fasterxml.jackson.databind.JsonNode items = root.get("items");
                        if (items != null && items.isArray()) {
                            for (com.fasterxml.jackson.databind.JsonNode item : items) {
                                String name = item.path("name").asText("Unknown");
                                String type = item.path("type").asText("Unknown");
                                modelListModel.addElement(new CivitaiModel(name, type, item));
                            }
                        }
                    }
                    if (modelListModel.isEmpty()) {
                        modelListModel.addElement(new CivitaiModel("No results found", "", null));
                    }
                });
            }).exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    searchBtn.setEnabled(true);
                    modelListModel.clear();
                    modelListModel.addElement(new CivitaiModel("Error: " + ex.getMessage(), "", null));
                });
                return null;
            });
        };

        searchBtn.addActionListener(e -> doSearch.run());
        searchField.addActionListener(e -> doSearch.run());

        modelList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            CivitaiModel selectedModel = modelList.getSelectedValue();
            versionCombo.removeAllItems();
            infoText.setText("");
            previewLabel.setIcon(null);
            previewLabel.setText("No Preview Image Available");
            
            if (selectedModel == null || selectedModel.rawNode == null) return;
            
            com.fasterxml.jackson.databind.JsonNode versions = selectedModel.rawNode.get("modelVersions");
            if (versions != null && versions.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode v : versions) {
                    String vName = v.path("name").asText("Unknown");
                    String downloadUrl = v.path("downloadUrl").asText("");
                    
                    long byteSize = 0;
                    String sizeStr = "Unknown";
                    com.fasterxml.jackson.databind.JsonNode files = v.get("files");
                    if (files != null && files.isArray() && files.size() > 0) {
                        double sizeKb = files.get(0).path("sizeKB").asDouble(0);
                        byteSize = (long) (sizeKb * 1024);
                        double sizeMb = sizeKb / 1024.0;
                        if (sizeMb > 1024) {
                            sizeStr = String.format("%.2f GB", sizeMb / 1024.0);
                        } else {
                            sizeStr = String.format("%.2f MB", sizeMb);
                        }
                    }
                    
                    String imgUrl = null;
                    com.fasterxml.jackson.databind.JsonNode images = v.get("images");
                    if (images != null && images.isArray() && images.size() > 0) {
                        imgUrl = images.get(0).path("url").asText(null);
                    }
                    
                    List<String> triggers = new ArrayList<>();
                    com.fasterxml.jackson.databind.JsonNode trainedWords = v.get("trainedWords");
                    if (trainedWords != null && trainedWords.isArray()) {
                        for (com.fasterxml.jackson.databind.JsonNode word : trainedWords) {
                            triggers.add(word.asText());
                        }
                    }
                    
                    versionCombo.addItem(new CivitaiVersion(vName, downloadUrl, sizeStr, byteSize, imgUrl, triggers, v));
                }
            }
        });

        versionCombo.addActionListener(e -> {
            CivitaiVersion selectedVersion = (CivitaiVersion) versionCombo.getSelectedItem();
            infoText.setText("");
            previewLabel.setIcon(null);
            previewLabel.setText("Loading Preview...");
            
            if (selectedVersion == null) {
                previewLabel.setText("No Preview Image Available");
                return;
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("File Size: ").append(selectedVersion.size).append("\n");
            sb.append("Download URL: ").append(selectedVersion.downloadUrl).append("\n\n");
            
            if (!selectedVersion.triggerWords.isEmpty()) {
                sb.append("Trigger Words: ").append(String.join(", ", selectedVersion.triggerWords)).append("\n\n");
            }
            
            String desc = selectedVersion.rawNode.path("description").asText("");
            if (!desc.isEmpty()) {
                desc = desc.replaceAll("<[^>]*>", "");
                sb.append("Description:\n").append(desc);
            }
            
            infoText.setText(sb.toString());
            infoText.setCaretPosition(0);
            
            String imgUrl = selectedVersion.imageUrl;
            if (imgUrl != null && !imgUrl.isEmpty()) {
                backgroundExecutor.execute(() -> {
                    try {
                        java.net.URL url = new java.net.URL(imgUrl);
                        BufferedImage img = javax.imageio.ImageIO.read(url);
                        if (img != null) {
                            int lblWidth = previewLabel.getWidth();
                            int lblHeight = previewLabel.getHeight();
                            if (lblWidth <= 0) lblWidth = 350;
                            if (lblHeight <= 0) lblHeight = 350;
                            
                            double ratio = Math.min((double) lblWidth / img.getWidth(), (double) lblHeight / img.getHeight());
                            int w = (int) (img.getWidth() * ratio);
                            int h = (int) (img.getHeight() * ratio);
                            Image scaled = img.getScaledInstance(w, h, Image.SCALE_SMOOTH);
                            
                            SwingUtilities.invokeLater(() -> {
                                if (versionCombo.getSelectedItem() == selectedVersion) {
                                    previewLabel.setIcon(new ImageIcon(scaled));
                                    previewLabel.setText("");
                                }
                            });
                        } else {
                            SwingUtilities.invokeLater(() -> {
                                if (versionCombo.getSelectedItem() == selectedVersion) {
                                    previewLabel.setText("Failed to load image preview");
                                }
                            });
                        }
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(() -> {
                            if (versionCombo.getSelectedItem() == selectedVersion) {
                                previewLabel.setText("Preview Image unavailable");
                            }
                        });
                    }
                });
            } else {
                previewLabel.setText("No Preview Image Available");
            }
        });

        applyBtn.addActionListener(e -> {
            CivitaiVersion selectedVersion = (CivitaiVersion) versionCombo.getSelectedItem();
            CivitaiModel selectedModel = modelList.getSelectedValue();
            if (selectedVersion == null || selectedModel == null) {
                JOptionPane.showMessageDialog(dialog, "Please select a model and a version first.", "No Selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            ModelInfo info = modelsToDownload.get(row);
            
            String targetFileName = "";
            com.fasterxml.jackson.databind.JsonNode files = selectedVersion.rawNode.get("files");
            if (files != null && files.isArray() && files.size() > 0) {
                targetFileName = files.get(0).path("name").asText("");
            }
            if (targetFileName.isEmpty()) {
                targetFileName = info.getName();
            }
            
            info.setName(targetFileName);
            info.setUrl(selectedVersion.downloadUrl);
            info.setSize(selectedVersion.size);
            info.setByteSize(selectedVersion.byteSize);
            
            tableModel.setValueAt(true, row, 0);
            tableModel.setValueAt(targetFileName, row, 2);
            tableModel.setValueAt(selectedVersion.size, row, 3);
            tableModel.setValueAt(selectedVersion.downloadUrl, row, 6);
            tableModel.setValueAt("✅ Known Good", row, 7);
            
            dialog.dispose();
            JOptionPane.showMessageDialog(view, "Model version applied! Ready to download.", "Version Applied", JOptionPane.INFORMATION_MESSAGE);
        });

        SwingUtilities.invokeLater(() -> doSearch.run());
        dialog.setVisible(true);
    }

    // --- Helpers ---

    private void showModalProgressDialog(String title, String statusPrefix, List<Integer> selectedRows, 
                                        DefaultTableModel sourceModel, DefaultTableModel targetModel, TaskExecutor executor) {
        JDialog progressDialog = new JDialog((JFrame) SwingUtilities.getWindowAncestor(view), title, true);
        progressDialog.setLayout(new BorderLayout(10, 10));
        progressDialog.setSize(500, 180);
        progressDialog.setLocationRelativeTo(view);
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel label = new JLabel(statusPrefix + "...");
        label.setFont(new Font("SansSerif", Font.BOLD, 12));
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(400, 30));

        panel.add(label, BorderLayout.NORTH);
        panel.add(progressBar, BorderLayout.CENTER);
        progressDialog.add(panel, BorderLayout.CENTER);

        List<ArchiveWorkItem> workItems = new ArrayList<>();
        long totalBytes = 0;
        for (int row : selectedRows) {
            String sizeStr = (String) sourceModel.getValueAt(row, 3);
            workItems.add(new ArchiveWorkItem(row, (String) sourceModel.getValueAt(row, 1), (String) sourceModel.getValueAt(row, 2), sizeStr));
            totalBytes += parseSizeToBytes(sizeStr);
        }
        final long finalTotalBytes = totalBytes > 0 ? totalBytes : 1; 

        backgroundExecutor.execute(() -> {
            int successCount = 0;
            List<Object[]> rowsToMove = new ArrayList<>();
            List<Integer> processedRowIndices = new ArrayList<>();
            long[] bytesProcessedTotal = {0};

            for (int i = 0; i < workItems.size(); i++) {
                ArchiveWorkItem item = workItems.get(i);
                final int fileIndex = i + 1;
                SwingUtilities.invokeLater(() -> label.setText(statusPrefix + " (" + fileIndex + "/" + workItems.size() + "): " + item.name));

                if (executor.execute(item.folder, item.name, (bytesDelta) -> {
                    bytesProcessedTotal[0] += bytesDelta;
                    final long current = bytesProcessedTotal[0];
                    final int percent = (int) Math.min(100, (current * 100) / finalTotalBytes);
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(percent);
                        progressBar.setString(percent + "% (" + searchService.formatSize(current) + " / " + searchService.formatSize(finalTotalBytes) + ")");
                    });
                })) {
                    successCount++;
                    processedRowIndices.add(item.index);
                    rowsToMove.add(new Object[]{false, item.folder, item.name, item.size});
                }
            }

            final int finalSuccess = successCount;
            SwingUtilities.invokeLater(() -> {
                progressDialog.dispose();
                processedRowIndices.sort((a, b) -> b.compareTo(a));
                for (int r : processedRowIndices) sourceModel.removeRow(r);
                for (Object[] rowData : rowsToMove) targetModel.addRow(rowData);
                
                analyzeJsonContent();
                if (postOperationCallback != null) {
                    postOperationCallback.run();
                }
                
                JOptionPane.showMessageDialog(view, finalSuccess + " models successfully " + title.toLowerCase() + "ed.", 
                    "Operation Complete", JOptionPane.INFORMATION_MESSAGE);
            });
        });

        progressDialog.setVisible(true);
    }

    private long parseSizeToBytes(String sizeStr) {
        if (sizeStr == null || sizeStr.equals("Unknown") || sizeStr.isEmpty()) return 0;
        try {
            String[] parts = sizeStr.trim().split("\\s+");
            double val = Double.parseDouble(parts[0].replace(",", "."));
            if (parts.length < 2) return (long) val;
            String unit = parts[1].toUpperCase();
            if (unit.contains("GB")) return (long) (val * 1024 * 1024 * 1024);
            if (unit.contains("MB")) return (long) (val * 1024 * 1024);
            if (unit.contains("KB")) return (long) (val * 1024);
            return (long) val;
        } catch (Exception e) { return 0; }
    }

    @FunctionalInterface
    interface TaskExecutor {
        boolean execute(String folder, String name, java.util.function.LongConsumer progressUpdate);
    }

    private static class ArchiveWorkItem {
        int index;
        String folder;
        String name;
        String size;
        ArchiveWorkItem(int i, String f, String n, String s) { index = i; folder = f; name = n; size = s; }
    }

    private static class CivitaiModel {
        String name;
        String type;
        com.fasterxml.jackson.databind.JsonNode rawNode;
        CivitaiModel(String name, String type, com.fasterxml.jackson.databind.JsonNode rawNode) {
            this.name = name;
            this.type = type;
            this.rawNode = rawNode;
        }
        @Override public String toString() {
            return name + " (" + type + ")";
        }
    }

    private static class CivitaiVersion {
        String name;
        String downloadUrl;
        String size;
        long byteSize;
        String imageUrl;
        List<String> triggerWords;
        com.fasterxml.jackson.databind.JsonNode rawNode;
        CivitaiVersion(String name, String downloadUrl, String size, long byteSize, String imageUrl, List<String> triggerWords, com.fasterxml.jackson.databind.JsonNode rawNode) {
            this.name = name;
            this.downloadUrl = downloadUrl;
            this.size = size;
            this.byteSize = byteSize;
            this.imageUrl = imageUrl;
            this.triggerWords = triggerWords;
            this.rawNode = rawNode;
        }
        @Override public String toString() {
            return name;
        }
    }
}
