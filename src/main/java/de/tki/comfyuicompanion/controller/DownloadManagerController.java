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
    private final DownloadModelResolutionHelper resolutionHelper;

    private DownloadManagerView view;
    private List<ModelInfo> modelsToDownload = new ArrayList<>();
    private volatile boolean isDownloading = false;
    private String currentFileName = "input.json";
    private Runnable postOperationCallback;

    /**
     * Constructs a new DownloadManagerController.
     *
     * @param configService      the configuration service
     * @param downloadManager    the download manager core service
     * @param analyzer           the model analyzer service
     * @param searchService      the model search service
     * @param modelValidator     the model validator
     * @param workflowService    the workflow parsing service
     * @param modelListService   the model list service
     * @param archiveService     the archive management service
     * @param localScanner       the local model scanner
     * @param hashRegistry       the hash registry for duplicate detection
     * @param diagnosticService  the diagnostic service for checking ComfyUI model visibility
     * @param backgroundExecutor the executor for background tasks
     * @param civitaiService     the service for Civitai API integration
     */
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
        this.resolutionHelper = new DownloadModelResolutionHelper(configService, archiveService, localScanner, searchService, this.backgroundExecutor);
    }

    /**
     * Sets the view and registers this controller as its listener.
     *
     * @param view the view to manage
     */
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
     *
     * @param view the view to manage
     */
    public void setViewReference(DownloadManagerView view) {
        this.view = view;
    }

    /**
     * @return the associated view
     */
    public DownloadManagerView getView() {
        return view;
    }

    /**
     * @return the list of models currently staged for download
     */
    public List<ModelInfo> getModelsToDownload() {
        return modelsToDownload;
    }

    /**
     * @return true if a download queue is currently executing, false otherwise
     */
    public boolean isDownloading() {
        return isDownloading;
    }

    /**
     * Sets a callback to be executed after a download queue operation completes.
     *
     * @param postOperationCallback the callback to run
     */
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

    /**
     * Loads a workflow JSON file, extracts its content, and triggers analysis.
     *
     * @param file the workflow file to load
     */
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

    /**
     * Imports a workflow file from external drag-and-drop or other sources,
     * switches to the download manager tab, and loads it.
     *
     * @param file                 the workflow file to import
     * @param mainTabs             the main tabbed pane to switch context
     * @param downloadManagerPanel the panel representing the download manager tab
     */
    public void importWorkflow(File file, JTabbedPane mainTabs, JPanel downloadManagerPanel) {
        if (mainTabs != null && downloadManagerPanel != null) {
            int index = mainTabs.indexOfComponent(downloadManagerPanel);
            if (index != -1) {
                mainTabs.setSelectedIndex(index);
            }
        }
        loadFile(file);
    }

    /**
     * Imports a custom model list JSON file and re-runs the analysis.
     *
     * @param file the model list JSON file
     */
    public void importModelListFile(File file) {
        try {
            modelListService.importJson(file);
            JOptionPane.showMessageDialog(view, "Model list successfully imported! (" + modelListService.getModels().size() + " models)");
            analyzeJsonContent();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(view, "Error: " + ex.getMessage());
        }
    }

    /**
     * Analyzes the currently loaded JSON content to identify required models,
     * checks their local presence, archive presence, and populates the UI table.
     */
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

    /**
     * Initiates the download queue for all selected models in the table.
     * Starts by extracting models from the archive if available, then falls back to network download.
     */
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

    /**
     * Automatically searches for missing models online without user prompting.
     */
    public void searchMissingOnline() {
        searchMissingOnline(false);
    }

    /**
     * Searches for missing models online using the configured ModelSearchService.
     *
     * @param manual true if the search was manually triggered by the user
     */
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
        resolutionHelper.fetchMissingRemoteSizes(modelsToDownload, view);
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
        if (modelsToDownload == null) {
            modelsToDownload = new ArrayList<>();
        }
        resolutionHelper.focusDownloadTabAndSelectModels(missingModels, modelsToDownload, view,
                this::updateDownloadManagerSelection, this::updateDownloadButtonsState);
    }
}

