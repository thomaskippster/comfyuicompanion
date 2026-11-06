package de.tki.comfyuicompanion.ui;

import org.slf4j.Logger; import de.tki.comfyuicompanion.domain.WorkflowDescriptor;
import de.tki.comfyuicompanion.service.workflow.IWorkflowSourceStrategy; import de.tki.comfyuicompanion.service.workflow.impl.ComfyBlueprintStrategy; import de.tki.comfyuicompanion.ui.workflow.UnifiedWorkflowDetailsDialog; 
import org.slf4j.LoggerFactory; import de.tki.comfyuicompanion.domain.*;
import de.tki.comfyuicompanion.service.*;
import de.tki.comfyuicompanion.service.impl.*;
import de.tki.comfyuicompanion.ui.blueprint.*;
import de.tki.comfyuicompanion.service.vault.WorkflowVaultCoordinator;
import de.tki.comfyuicompanion.ui.vault.WorkflowVaultPanel;
import de.tki.comfyuicompanion.ui.civitai.CivitaiGalleryTab;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Enterprise Blueprint Gallery tab providing unified exploration of Comfy.org workflow blueprints,
 * Civitai Model Hub, and Hugging Face Hub under a single streamlined sub-tab interface.
 */
@Component
public class BlueprintGalleryTab extends JPanel {
    private static final Logger logger = LoggerFactory.getLogger(BlueprintGalleryTab.class);

    private final ConfigService configService;
    private final IComfyLifecycleService lifecycleService;
    private final ILocalModelValidator localModelValidator;
    private final IWorkflowDownloader workflowDownloader;
    private final LocalAIService localAIService;
    private final IModelSearchService modelSearchService;
    private final BlueprintPreviewCache previewCache;
    private final BlueprintDataCoordinator dataCoordinator;

    private CivitaiGalleryTab civitaiGalleryTab;
    private BlueprintHeaderToolbar headerToolbar;
    private BlueprintGridRenderer gridRenderer;
    private JPanel galleryWrapper;

    private final CardLayout rootCardLayout = new CardLayout();
    private final JPanel rootCardsPanel = new JPanel(rootCardLayout);
    private final JPanel modelHubContainer = new JPanel(new BorderLayout());
    private final WorkflowVaultCoordinator vaultCoordinator;
    private WorkflowVaultPanel vaultPanel;
    private BlueprintSubTabBar subTabBar;

    private final List<BlueprintEntry> allEntries = new CopyOnWriteArrayList<>();
    private final AtomicBoolean isRefreshing = new AtomicBoolean(false);
    private final javax.swing.Timer filterDebounceTimer = new javax.swing.Timer(250, e -> applyFilter());
    private boolean hasScanned = false;
    private Runnable onDataLoadedCallback;
    private Consumer<File> onWorkflowImportRequested;
    private Consumer<List<ModelInfo>> onSwitchToDownloadManager;

    /**
     * Constructs a new BlueprintGalleryTab with necessary dependencies and optional CivitaiGalleryTab.
     */
    @Autowired
    public BlueprintGalleryTab(ConfigService configService, IModelArchitectureService modelArchitectureService,
                               IComfyLifecycleService lifecycleService, IComfyRegistryClient registryClient,
                               ILocalModelValidator localModelValidator, IWorkflowDownloader workflowDownloader,
                               LocalModelScanner localModelScanner, ArchiveService archiveService,
                               LocalAIService localAIService, IModelSearchService modelSearchService,
                               @Autowired(required = false) ProcessTracker processTracker,
                               @Autowired(required = false) CivitaiGalleryTab civitaiGalleryTab,
                               @Autowired(required = false) WorkflowVaultCoordinator vaultCoordinator,
                               @Autowired(required = false) de.tki.comfyuicompanion.service.IWorkflowVaultRepository vaultRepository,
                               @Autowired(required = false) de.tki.comfyuicompanion.service.IDownloadManager downloadManager) {
        this.configService = configService;
        this.lifecycleService = lifecycleService;
        this.localModelValidator = localModelValidator;
        this.workflowDownloader = workflowDownloader;
        this.localAIService = localAIService;
        this.modelSearchService = modelSearchService;
        this.civitaiGalleryTab = civitaiGalleryTab;
        this.previewCache = new BlueprintPreviewCache(configService);
        this.vaultCoordinator = vaultCoordinator != null ? vaultCoordinator :
                new WorkflowVaultCoordinator(vaultRepository, localModelValidator, downloadManager, modelSearchService);
        this.dataCoordinator = new BlueprintDataCoordinator(configService, localModelScanner, archiveService,
                localModelValidator, modelArchitectureService, registryClient);
        this.filterDebounceTimer.setRepeats(false);
        setLayout(new BorderLayout());
        setOpaque(true);

        boolean darkMode = configService == null || configService.isDarkMode();
        buildUI(darkMode);
        updateTheme(darkMode);
        addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) {
                if (!hasScanned) refreshAllData(); else updateStatusAsync();
                if (vaultPanel != null) vaultPanel.refreshVaultData();
            }
        });
    }

    /**
     * Backward-compatible constructor for unit testing scenarios.
     */
    public BlueprintGalleryTab(ConfigService configService, IModelArchitectureService modelArchitectureService,
                               IComfyLifecycleService lifecycleService, IComfyRegistryClient registryClient,
                               ILocalModelValidator localModelValidator, IWorkflowDownloader workflowDownloader,
                               LocalModelScanner localModelScanner, ArchiveService archiveService,
                               LocalAIService localAIService, IModelSearchService modelSearchService,
                               ProcessTracker processTracker, CivitaiGalleryTab civitaiGalleryTab) {
        this(configService, modelArchitectureService, lifecycleService, registryClient, localModelValidator,
                workflowDownloader, localModelScanner, archiveService, localAIService, modelSearchService,
                processTracker, civitaiGalleryTab, null, null, null);
    }

    public BlueprintGalleryTab(ConfigService configService, IModelArchitectureService modelArchitectureService,
                               IComfyLifecycleService lifecycleService, IComfyRegistryClient registryClient,
                               ILocalModelValidator localModelValidator, IWorkflowDownloader workflowDownloader,
                               LocalModelScanner localModelScanner, ArchiveService archiveService,
                               LocalAIService localAIService, IModelSearchService modelSearchService,
                               ProcessTracker processTracker) {
        this(configService, modelArchitectureService, lifecycleService, registryClient, localModelValidator,
                workflowDownloader, localModelScanner, archiveService, localAIService, modelSearchService,
                processTracker, null);
    }

    /** Registers a callback invoked when the user requests to import a workflow file. */
    public void setOnWorkflowImportRequested(Consumer<File> callback) {
        this.onWorkflowImportRequested = callback;
        if (vaultPanel != null) vaultPanel.setOnWorkflowImportRequested(callback);
    }

    /** Registers a callback invoked when blueprint data finishes loading. */
    public void setOnDataLoadedCallback(Runnable callback) { this.onDataLoadedCallback = callback; }

    /** Registers a callback invoked to transition into the Download Manager tab. */
    public void setOnSwitchToDownloadManager(Consumer<List<ModelInfo>> callback) {
        this.onSwitchToDownloadManager = callback;
        if (civitaiGalleryTab != null) {
            civitaiGalleryTab.setOnSwitchToDownloadManager(callback);
        }
        if (vaultPanel != null) {
            vaultPanel.setOnSwitchToDownloadManager(callback);
        }
    }

    /**
     * Injects or updates the embedded CivitaiGalleryTab component.
     *
     * @param tab the Civitai gallery tab
     */
    public void setCivitaiGalleryTab(CivitaiGalleryTab tab) {
        this.civitaiGalleryTab = tab;
        modelHubContainer.removeAll();
        if (tab != null) {
            tab.setVaultCoordinator(vaultCoordinator);
            tab.setModelSearchService(modelSearchService);
            if (tab.getHuggingFaceGalleryView() != null) {
                tab.getHuggingFaceGalleryView().setVaultCoordinator(vaultCoordinator);
                tab.getHuggingFaceGalleryView().setModelSearchService(modelSearchService);
            }
            tab.setProviderBarVisible(false);
            tab.setVisible(true);
            if (onSwitchToDownloadManager != null) {
                tab.setOnSwitchToDownloadManager(onSwitchToDownloadManager);
            }
            if (subTabBar != null && subTabBar.getBtnSubHf().isSelected()) {
                tab.showHuggingFaceView();
            } else if (subTabBar != null && subTabBar.getBtnSubCivitai().isSelected()) {
                tab.showCivitaiView();
            }
            modelHubContainer.add(tab, BorderLayout.CENTER);
        } else {
            JPanel fallbackPanel = new JPanel(new GridBagLayout());
            fallbackPanel.setOpaque(false);
            fallbackPanel.add(new JLabel("Model Hub is initializing or not available."));
            modelHubContainer.add(fallbackPanel, BorderLayout.CENTER);
        }
        modelHubContainer.revalidate();
        modelHubContainer.repaint();
    }

    /** Triggers a debounced re-filter of visible blueprints. */
    public void requestFilterUpdate() {
        if (SwingUtilities.isEventDispatchThread()) filterDebounceTimer.restart();
        else SwingUtilities.invokeLater(filterDebounceTimer::restart);
    }

    private void buildUI(boolean initialDark) {
        // 1. Top Sub-Tab Switcher Bar
        subTabBar = new BlueprintSubTabBar();
        subTabBar.setOnVaultSelected(() -> {
            rootCardLayout.show(rootCardsPanel, "VAULT");
            if (vaultPanel != null) vaultPanel.refreshVaultData();
            rootCardsPanel.revalidate();
            rootCardsPanel.repaint();
        });
        subTabBar.setOnComfySelected(() -> {
            rootCardLayout.show(rootCardsPanel, "COMFY");
            rootCardsPanel.revalidate();
            rootCardsPanel.repaint();
        });
        subTabBar.setOnCivitaiSelected(() -> {
            rootCardLayout.show(rootCardsPanel, "MODEL_HUB");
            if (civitaiGalleryTab != null) {
                civitaiGalleryTab.setProviderBarVisible(false);
                civitaiGalleryTab.showCivitaiView();
            }
            rootCardsPanel.revalidate();
            rootCardsPanel.repaint();
        });
        subTabBar.setOnHfSelected(() -> {
            rootCardLayout.show(rootCardsPanel, "MODEL_HUB");
            if (civitaiGalleryTab != null) {
                civitaiGalleryTab.setProviderBarVisible(false);
                civitaiGalleryTab.showHuggingFaceView();
            }
            rootCardsPanel.revalidate();
            rootCardsPanel.repaint();
        });
        add(subTabBar, BorderLayout.NORTH);

        // 2. Comfy.org Blueprints View
        JPanel comfyViewPanel = new JPanel(new BorderLayout());
        comfyViewPanel.setName("comfyViewPanel");
        comfyViewPanel.setOpaque(false);

        headerToolbar = new BlueprintHeaderToolbar(initialDark);
        headerToolbar.setOnSearchChanged(this::requestFilterUpdate);
        headerToolbar.setOnCategoryChanged(this::applyFilter);
        headerToolbar.setOnReadyOnlyChanged(this::applyFilter);
        headerToolbar.setOnHideCloudChanged(this::applyFilter);
        headerToolbar.setOnRefreshClicked(() -> refreshAllData(true));
        comfyViewPanel.add(headerToolbar, BorderLayout.NORTH);

        gridRenderer = new BlueprintGridRenderer(previewCache);
        this.galleryWrapper = gridRenderer.getGalleryWrapper();
        comfyViewPanel.add(gridRenderer.getScrollPane(), BorderLayout.CENTER);

        // 3. Root Card Container
        modelHubContainer.setName("modelHubContainer");
        modelHubContainer.setOpaque(false);
        rootCardsPanel.setOpaque(false);
        vaultPanel = new WorkflowVaultPanel(this.vaultCoordinator, this.previewCache, this.configService,
                this.workflowDownloader, this.lifecycleService, this.localModelValidator, this.modelSearchService);
        if (onWorkflowImportRequested != null) {
            vaultPanel.setOnWorkflowImportRequested(onWorkflowImportRequested);
        }
        if (onSwitchToDownloadManager != null) {
            vaultPanel.setOnSwitchToDownloadManager(onSwitchToDownloadManager);
        }
        rootCardsPanel.add(vaultPanel, "VAULT");
        rootCardsPanel.add(comfyViewPanel, "COMFY");
        rootCardsPanel.add(modelHubContainer, "MODEL_HUB");

        setCivitaiGalleryTab(this.civitaiGalleryTab);
        rootCardLayout.show(rootCardsPanel, "VAULT");

        add(rootCardsPanel, BorderLayout.CENTER);
    }

    /** Refreshes all blueprint data from online and bundled sources asynchronously. */
    public void refreshAllData() { refreshAllData(false); }

    /**
     * Refreshes all blueprint data with an option to force re-fetch.
     * @param forceRefresh whether to force a new fetch even if one is running
     */
    public void refreshAllData(boolean forceRefresh) {
        if (!forceRefresh && !isRefreshing.compareAndSet(false, true)) {
            logger.info("[BlueprintGallery] Refresh already in progress, skipping duplicate call.");
            return;
        }
        if (forceRefresh) isRefreshing.set(true);
        hasScanned = true;
        Runnable uiInit = () -> {
            headerToolbar.setStatusText("Fetching workflows and checking local models in background...");
            headerToolbar.setRefreshing(true);
            if (allEntries.isEmpty()) gridRenderer.showLoading();
        };
        if (SwingUtilities.isEventDispatchThread()) uiInit.run(); else SwingUtilities.invokeLater(uiInit);

        Thread.ofVirtual().name("BlueprintGalleryRefresh").start(() -> {
            try {
                List<BlueprintEntry> loaded = dataCoordinator.fetchAndIndexEntries(cb -> requestFilterUpdate());
                allEntries.clear();
                allEntries.addAll(loaded);
                SwingUtilities.invokeLater(() -> {
                    headerToolbar.updateCategories(BlueprintFilterController.getSortedCategories(allEntries));
                    headerToolbar.setRefreshing(false);
                    applyFilter();
                    headerToolbar.setStatusText(" ");
                    if (localAIService != null) localAIService.setGemmaEnabled(true);
                    if (onDataLoadedCallback != null) try { onDataLoadedCallback.run(); } catch (Exception ignored) {
                        logger.debug("Ignored non-fatal exception: {}", ignored.getMessage());
                    }
                });
            } catch (Exception e) {
                logger.error("[BlueprintGallery] Refresh failed: {}", e.getMessage(), e);
                SwingUtilities.invokeLater(() -> {
                    headerToolbar.setStatusText("Failed to fetch registry data: " + e.getMessage());
                    headerToolbar.setRefreshing(false);
                });
            } finally { isRefreshing.set(false); }
        });
    }

    private void updateStatusAsync() {
        if (!isRefreshing.compareAndSet(false, true)) return;
        Thread.ofVirtual().name("BlueprintGalleryStatusUpdate").start(() -> {
            try {
                if (localModelValidator != null) localModelValidator.scanLocalModels();
                for (BlueprintEntry e : allEntries) e.status = computeStatus(e);
                requestFilterUpdate();
            } catch (Exception e) {
                logger.error("[BlueprintGallery] Status update failed: {}", e.getMessage());
            } finally { isRefreshing.set(false); }
        });
    }

    private void applyFilter() {
        if (headerToolbar == null || gridRenderer == null) return;
        List<BlueprintEntry> visible = BlueprintFilterController.filterAndSort(
                allEntries, headerToolbar.getSearchText(), headerToolbar.getSelectedCategory(),
                headerToolbar.isReadyOnly(), headerToolbar.isHideCloud(), this::computeStatus);
        gridRenderer.renderVisibleEntries(visible, this::computeStatus,
                e -> showDetails(e, computeStatus(e)),
                (e, w) -> dataCoordinator.playVideoPreview(e, w, null));
        headerToolbar.updateSummary(allEntries, this::computeStatus);
        if (onDataLoadedCallback != null) {
            try { onDataLoadedCallback.run(); } catch (Exception ignored) {
                logger.debug("Ignored non-fatal exception: {}", ignored.getMessage());
            }
        }
    }

    private void showDetails(BlueprintEntry entry, BlueprintStatus status) {
        if (entry == null) return;
        entry.status = status != null ? status : computeStatus(entry);
        IWorkflowSourceStrategy strategy = new ComfyBlueprintStrategy();
        WorkflowDescriptor desc = strategy.toWorkflowDescriptor(entry);
        new UnifiedWorkflowDetailsDialog(
                SwingUtilities.getWindowAncestor(this),
                desc,
                strategy,
                configService,
                null,
                modelSearchService,
                vaultCoordinator,
                json -> BlueprintComfySender.sendToComfyUI(
                        entry, null, workflowDownloader, lifecycleService, configService,
                        null, this, success -> {
                            if (Boolean.TRUE.equals(success)) {
                                logger.info("Blueprint '{}' successfully dispatched to ComfyUI.", entry.name);
                            }
                        }
                ),
                onSwitchToDownloadManager
        ).setVisible(true);
    }

    private BlueprintStatus computeStatus(BlueprintEntry entry) {
        if (entry.status == null) entry.status = dataCoordinator.computeStatusInternal(entry);
        return entry.status;
    }

    /**
     * Updates theme styling across the gallery tab, toolbar, and grid renderer.
     * @param darkMode true for dark mode, false for light mode
     */
    public void updateTheme(boolean darkMode) {
        setBackground(darkMode ? new Color(15, 16, 21) : new Color(245, 247, 250));
        if (headerToolbar != null) headerToolbar.updateTheme(darkMode);
        if (vaultPanel != null) vaultPanel.updateTheme(darkMode);
        if (gridRenderer != null) gridRenderer.updateTheme(darkMode);
        if (civitaiGalleryTab != null) civitaiGalleryTab.updateTheme(darkMode);
        repaint();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        SwingUtilities.updateComponentTreeUI(this);
        updateTheme(ThemeManager.isDarkMode());
    }

    /** Returns a list of blueprints where all required models are locally present. */
    public List<BlueprintEntry> getAvailableBlueprints() { return BlueprintFilterController.getAvailableBlueprints(allEntries); }

    /** Model object representing a single workflow template entry. */
    public static class BlueprintEntry {
        public final String name, filename, filePath, category, description;
        public final List<ModelInfo> requiredModels;
        public final ComfyRegistryWorkflow registryWorkflow;
        public String previewPath = "";
        public BlueprintStatus status = null;
        public boolean hasBeenValidated = false;

        /** Constructs a BlueprintEntry from a registry workflow model. */
        public BlueprintEntry(ComfyRegistryWorkflow workflow) {
            this.registryWorkflow = workflow;
            this.name = workflow.getTitle();
            this.filename = workflow.getTitle() + ".json";
            this.filePath = workflow.getJsonDownloadUrl();
            this.category = workflow.getCategory() != null ? workflow.getCategory() : "General";
            this.description = workflow.getDescription() != null ? workflow.getDescription() : "";
            this.previewPath = workflow.getThumbnailUrl() != null ? workflow.getThumbnailUrl() : "";
            this.requiredModels = new ArrayList<>();
            if (workflow.getRequiredModelInfos() != null && !workflow.getRequiredModelInfos().isEmpty()) {
                this.requiredModels.addAll(workflow.getRequiredModelInfos());
            } else if (workflow.getRequiredModels() != null) {
                for (String m : workflow.getRequiredModels()) {
                    if (BlueprintDataCoordinator.isSupportedModelFile(m)) {
                        ModelInfo info = new ModelInfo(); info.setName(m);
                        info.setType(BlueprintDataCoordinator.inferTypeFromFilename(m));
                        this.requiredModels.add(info);
                    }
                }
            }
        }

        @Override public String toString() { return this.name; }
    }

    /** Summary status of model availability for a blueprint. */
    public static class BlueprintStatus {
        public final int presentCount, missingCount;
        public final List<String> missingNames;

        /** Constructs a BlueprintStatus with counts and list of missing models. */
        public BlueprintStatus(int presentCount, int missingCount, List<String> missingNames) {
            this.presentCount = presentCount;
            this.missingCount = missingCount;
            this.missingNames = missingNames;
        }
    }

    /** Determines if a blueprint entry represents a video workflow. */
    public static boolean isVideoBlueprint(BlueprintEntry entry) { return BlueprintFilterController.isVideoBlueprint(entry); }

    /** Gets the media rank for sorting purposes (video > 3d/audio > other). */
    public static int getBlueprintMediaRank(BlueprintEntry entry) { return BlueprintFilterController.getBlueprintMediaRank(entry); }

    /** Returns an order score for categories, lower score is higher priority. */
    public static int getCategoryOrderScore(String categoryName) { return BlueprintFilterController.getCategoryOrderScore(categoryName); }

    /** Returns a list of blueprints from My Workflow Vault where all required models are locally present. */
    public List<BlueprintEntry> getVaultAvailableBlueprints() {
        return vaultCoordinator != null ? vaultCoordinator.getReadyVaultBlueprints() : java.util.Collections.emptyList();
    }

    /** Returns the workflow vault coordinator. */
    public WorkflowVaultCoordinator getWorkflowVaultCoordinator() { return vaultCoordinator; }

    /** Returns the workflow vault panel. */
    public WorkflowVaultPanel getWorkflowVaultPanel() { return vaultPanel; }

    /** Returns the vault toggle button. */
    public JToggleButton getBtnSubVault() { return subTabBar != null ? subTabBar.getBtnSubVault() : null; }

    /** Returns the Comfy.org blueprints toggle button. */
    public JToggleButton getBtnSubComfy() { return subTabBar != null ? subTabBar.getBtnSubComfy() : null; }

    public JToggleButton getBtnSubCivitai() { return subTabBar != null ? subTabBar.getBtnSubCivitai() : null; }

    public JToggleButton getBtnSubHf() { return subTabBar != null ? subTabBar.getBtnSubHf() : null; }

    /** Returns the embedded CivitaiGalleryTab component. */
    public CivitaiGalleryTab getCivitaiGalleryTab() { return civitaiGalleryTab; }

    public BlueprintHeaderToolbar getHeaderToolbar() { return headerToolbar; }

    public BlueprintGridRenderer getGridRenderer() { return gridRenderer; }

    /**
     * Returns a thread-safe snapshot list of all loaded blueprint entries.
     *
     * @return list of BlueprintEntry objects
     */
    @SuppressWarnings("unused")
    private String getModelStatus(ModelInfo info) { return dataCoordinator != null ? dataCoordinator.getModelStatus(info) : "Idle"; }
    @SuppressWarnings("unused")
    private boolean isModelPresentDeep(ModelInfo info) { return dataCoordinator != null && dataCoordinator.isModelPresentDeep(info); }

    public List<BlueprintEntry> getAllEntries() {
        return new ArrayList<>(allEntries);
    }
}
