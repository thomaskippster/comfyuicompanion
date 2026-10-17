package de.tki.comfyuicompanion.controller;

import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.IDownloadManager;
import de.tki.comfyuicompanion.service.IModelAnalyzer;
import de.tki.comfyuicompanion.service.IModelSearchService;
import de.tki.comfyuicompanion.service.IModelValidator;
import de.tki.comfyuicompanion.service.IWorkflowService;
import de.tki.comfyuicompanion.service.impl.ArchiveService;
import de.tki.comfyuicompanion.service.impl.CivitaiService;
import de.tki.comfyuicompanion.service.impl.ComfyDiagnosticService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.LocalModelScanner;
import de.tki.comfyuicompanion.service.impl.ModelHashRegistry;
import de.tki.comfyuicompanion.service.impl.ModelListService;
import de.tki.comfyuicompanion.service.provisioning.CustomNodeResolverService;
import de.tki.comfyuicompanion.service.provisioning.CustomNodeResolverService.CustomNodePackage;
import de.tki.comfyuicompanion.ui.DownloadManagerView;
import de.tki.comfyuicompanion.ui.dialog.CivitaiVersionSelectorDialog;
import de.tki.comfyuicompanion.ui.dialog.DownloadArchiveDialog;
import de.tki.comfyuicompanion.ui.dialog.MissingCustomNodesDialog;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
import de.tki.comfyuicompanion.util.BackgroundExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller managing Download Manager operations, model analysis, queue execution,
 * archive operations, model verification, Civitai lookup, missing custom node resolution,
 * and UI table updates.
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
    private final DownloadQueueCoordinator queueCoordinator;
    private final DownloadWorkflowAnalyzer workflowAnalyzer;
    private final CustomNodeResolverService customNodeResolverService;

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
        this(configService, downloadManager, analyzer, searchService, modelValidator, workflowService,
                modelListService, archiveService, localScanner, hashRegistry, diagnosticService,
                backgroundExecutor, civitaiService, new DownloadQueueCoordinator(), new DownloadWorkflowAnalyzer(), null);
    }

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
                                     CivitaiService civitaiService,
                                     @Autowired(required = false) DownloadQueueCoordinator queueCoordinator,
                                     @Autowired(required = false) DownloadWorkflowAnalyzer workflowAnalyzer) {
        this(configService, downloadManager, analyzer, searchService, modelValidator, workflowService,
                modelListService, archiveService, localScanner, hashRegistry, diagnosticService,
                backgroundExecutor, civitaiService, queueCoordinator, workflowAnalyzer, null);
    }

    @Autowired
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
                                     CivitaiService civitaiService,
                                     @Autowired(required = false) DownloadQueueCoordinator queueCoordinator,
                                     @Autowired(required = false) DownloadWorkflowAnalyzer workflowAnalyzer,
                                     @Autowired(required = false) CustomNodeResolverService customNodeResolverService) {
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
        this.queueCoordinator = queueCoordinator != null ? queueCoordinator : new DownloadQueueCoordinator();
        this.workflowAnalyzer = workflowAnalyzer != null ? workflowAnalyzer : new DownloadWorkflowAnalyzer();
        this.customNodeResolverService = customNodeResolverService;
    }

    public void setView(DownloadManagerView view) {
        this.view = view;
        if (this.view != null) {
            this.view.setListener(this);
        }
    }

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
    public void onCheckMissingCustomNodes() {
        if (view == null) return;
        String text = view.getJsonInputArea().getText();
        if (text == null || text.isBlank()) {
            JOptionPane.showMessageDialog(view, "Please load or paste a workflow JSON first.", "No Workflow", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        if (customNodeResolverService == null) {
            JOptionPane.showMessageDialog(view, "Custom node resolver service is currently unavailable.", "Service Unavailable", JOptionPane.WARNING_MESSAGE);
            return;
        }

        view.getStatusLabel().setText("Status: Analyzing workflow for missing custom nodes...");
        customNodeResolverService.analyzeWorkflowNodes(text).subscribe(report -> SwingUtilities.invokeLater(() -> {
            view.getStatusLabel().setText("Status: Custom node analysis completed.");
            if (report.missingNodeTypes().isEmpty()) {
                JOptionPane.showMessageDialog(view, "All required custom nodes are already installed in ComfyUI! ✅", "Custom Nodes Verified", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            Map<CustomNodePackage, List<String>> packagesMap = new LinkedHashMap<>();
            if (customNodeResolverService.getManagerBridgeService() != null) {
                packagesMap = customNodeResolverService.getManagerBridgeService().resolvePackagesForNodes(report.missingNodeTypes());
            }

            Window owner = SwingUtilities.getWindowAncestor(view);
            new MissingCustomNodesDialog(owner, packagesMap, customNodeResolverService.getManagerBridgeService(), () -> {
                JOptionPane.showMessageDialog(owner, "Custom nodes installation complete. Restart ComfyUI if running.", "Installation Complete", JOptionPane.INFORMATION_MESSAGE);
            }).setVisible(true);
        }), err -> SwingUtilities.invokeLater(() -> {
            view.getStatusLabel().setText("Status: Failed to analyze custom nodes: " + err.getMessage());
        }));
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
        this.modelsToDownload = workflowAnalyzer.analyzeJsonContent(
                view, currentFileName, analyzer, configService, archiveService,
                localScanner, searchService, resolutionHelper, this::updateDownloadButtonsState
        );
    }

    public void startDownloadQueue() {
        queueCoordinator.startDownloadQueue(
                view, modelsToDownload, archiveService, downloadManager,
                configService, backgroundExecutor, (state) -> this.isDownloading = state,
                this::updateDownloadButtonsState, postOperationCallback
        );
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

        boolean anySelected = false;
        if (tableModel != null) {
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                if (Boolean.TRUE.equals(tableModel.getValueAt(i, 0))) {
                    anySelected = true;
                    break;
                }
            }
        }

        downloadButton.setEnabled(anySelected && !isDownloading);
        pauseButton.setEnabled(isDownloading);
        stopButton.setEnabled(isDownloading);

        if (!isDownloading) {
            pauseButton.setText("Pause");
            pauseButton.setIcon(SvgIconFactory.get(AppIcon.PAUSE));
        }
    }

    public void searchMissingOnline(boolean notifyIfAllPresent) {
        workflowAnalyzer.searchMissingOnline(view, modelsToDownload, currentFileName, notifyIfAllPresent,
                searchService, configService, localScanner);
    }

    public void runDiagnostics(JTabbedPane tabs) {
        if (diagnosticService == null) return;
        List<String> missing = diagnosticService.getMissingModels(modelsToDownload);
        if (missing == null) {
            JOptionPane.showMessageDialog(view, "Could not query ComfyUI API.\nPlease check if ComfyUI is running.", "Diagnostics Error", JOptionPane.ERROR_MESSAGE);
        } else if (missing.isEmpty()) {
            JOptionPane.showMessageDialog(view, "ComfyUI recognizes all models required for this workflow!", "Diagnostics Passed", JOptionPane.INFORMATION_MESSAGE);
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

    @Override
    public void onShowStorageCleanerDialog() {
        Window owner = view != null ? SwingUtilities.getWindowAncestor(view) : null;
        new de.tki.comfyuicompanion.ui.dialog.StorageCleanerDialog(
                owner, configService, archiveService, hashRegistry, backgroundExecutor,
                () -> {
                    analyzeJsonContent();
                    if (postOperationCallback != null) postOperationCallback.run();
                }
        ).setVisible(true);
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
