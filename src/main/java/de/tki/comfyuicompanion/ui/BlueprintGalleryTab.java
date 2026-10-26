package de.tki.comfyuicompanion.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import de.tki.comfyuicompanion.domain.*;
import de.tki.comfyuicompanion.service.*;
import de.tki.comfyuicompanion.service.impl.*;
import de.tki.comfyuicompanion.ui.blueprint.*;
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
    private JToggleButton btnSubComfy;
    private JToggleButton btnSubCivitai;
    private JToggleButton btnSubHf;

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
                               @Autowired(required = false) CivitaiGalleryTab civitaiGalleryTab) {
        this.configService = configService;
        this.lifecycleService = lifecycleService;
        this.localModelValidator = localModelValidator;
        this.workflowDownloader = workflowDownloader;
        this.localAIService = localAIService;
        this.modelSearchService = modelSearchService;
        this.civitaiGalleryTab = civitaiGalleryTab;
        this.previewCache = new BlueprintPreviewCache(configService);
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
                               ProcessTracker processTracker) {
        this(configService, modelArchitectureService, lifecycleService, registryClient, localModelValidator,
                workflowDownloader, localModelScanner, archiveService, localAIService, modelSearchService,
                processTracker, null);
    }

    /** Registers a callback invoked when the user requests to import a workflow file. */
    public void setOnWorkflowImportRequested(Consumer<File> callback) { this.onWorkflowImportRequested = callback; }

    /** Registers a callback invoked when blueprint data finishes loading. */
    public void setOnDataLoadedCallback(Runnable callback) { this.onDataLoadedCallback = callback; }

    /** Registers a callback invoked to transition into the Download Manager tab. */
    public void setOnSwitchToDownloadManager(Consumer<List<ModelInfo>> callback) {
        this.onSwitchToDownloadManager = callback;
        if (civitaiGalleryTab != null) {
            civitaiGalleryTab.setOnSwitchToDownloadManager(callback);
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
            tab.setProviderBarVisible(false);
            tab.setVisible(true);
            if (onSwitchToDownloadManager != null) {
                tab.setOnSwitchToDownloadManager(onSwitchToDownloadManager);
            }
            if (btnSubHf != null && btnSubHf.isSelected()) {
                tab.showHuggingFaceView();
            } else if (btnSubCivitai != null && btnSubCivitai.isSelected()) {
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
        JPanel topSubTabBar = new JPanel(new BorderLayout(15, 0));
        topSubTabBar.setOpaque(false);
        topSubTabBar.setBorder(new EmptyBorder(10, 15, 6, 15));

        JPanel switchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        switchPanel.setOpaque(false);

        btnSubComfy = new JToggleButton("Comfy.org Blueprints", SvgIconFactory.get(AppIcon.BLUEPRINT_GALLERY, 15), true);
        btnSubComfy.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnSubComfy.setPreferredSize(new Dimension(185, 32));
        btnSubComfy.putClientProperty("Button.arc", 999);
        btnSubComfy.setFocusPainted(false);

        btnSubCivitai = new JToggleButton("Civitai Model Hub", SvgIconFactory.get(AppIcon.DOWNLOAD_MANAGER, 15), false);
        btnSubCivitai.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnSubCivitai.setPreferredSize(new Dimension(165, 32));
        btnSubCivitai.putClientProperty("Button.arc", 999);
        btnSubCivitai.setFocusPainted(false);

        btnSubHf = new JToggleButton("Hugging Face Hub", SvgIconFactory.get(AppIcon.MODEL_HUB, 15), false);
        btnSubHf.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnSubHf.setPreferredSize(new Dimension(165, 32));
        btnSubHf.putClientProperty("Button.arc", 999);
        btnSubHf.setFocusPainted(false);

        ButtonGroup subGroup = new ButtonGroup();
        subGroup.add(btnSubComfy);
        subGroup.add(btnSubCivitai);
        subGroup.add(btnSubHf);

        btnSubComfy.addActionListener(e -> {
            rootCardLayout.show(rootCardsPanel, "COMFY");
            rootCardsPanel.revalidate();
            rootCardsPanel.repaint();
        });
        btnSubCivitai.addActionListener(e -> {
            rootCardLayout.show(rootCardsPanel, "MODEL_HUB");
            if (civitaiGalleryTab != null) {
                civitaiGalleryTab.setProviderBarVisible(false);
                civitaiGalleryTab.showCivitaiView();
            }
            rootCardsPanel.revalidate();
            rootCardsPanel.repaint();
        });
        btnSubHf.addActionListener(e -> {
            rootCardLayout.show(rootCardsPanel, "MODEL_HUB");
            if (civitaiGalleryTab != null) {
                civitaiGalleryTab.setProviderBarVisible(false);
                civitaiGalleryTab.showHuggingFaceView();
            }
            rootCardsPanel.revalidate();
            rootCardsPanel.repaint();
        });

        switchPanel.add(btnSubComfy);
        switchPanel.add(btnSubCivitai);
        switchPanel.add(btnSubHf);
        topSubTabBar.add(switchPanel, BorderLayout.WEST);
        add(topSubTabBar, BorderLayout.NORTH);

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
        this.galleryWrapper = gridRenderer.getComponent();
        comfyViewPanel.add(gridRenderer.getScrollPane(), BorderLayout.CENTER);

        // 3. Root Card Container
        modelHubContainer.setName("modelHubContainer");
        modelHubContainer.setOpaque(false);
        rootCardsPanel.setOpaque(false);
        rootCardsPanel.add(comfyViewPanel, "COMFY");
        rootCardsPanel.add(modelHubContainer, "MODEL_HUB");

        setCivitaiGalleryTab(this.civitaiGalleryTab);

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
            logger.info("ℹ️ [BlueprintGallery] Refresh already in progress, skipping duplicate call.");
            return;
        }
        if (forceRefresh) isRefreshing.set(true);
        hasScanned = true;
        Runnable uiInit = () -> {
            headerToolbar.setStatusText("⏳  Fetching workflows and checking local models in background…");
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
                logger.error("❌ [BlueprintGallery] Refresh failed: {}", e.getMessage(), e);
                SwingUtilities.invokeLater(() -> {
                    headerToolbar.setStatusText("❌ Failed to fetch registry data: " + e.getMessage());
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
                logger.error("❌ [BlueprintGallery] Status update failed: {}", e.getMessage());
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
        new BlueprintDetailsDialog(SwingUtilities.getWindowAncestor(this), entry, status, configService,
                workflowDownloader, lifecycleService, localModelValidator, modelSearchService, previewCache,
                (e, b) -> dataCoordinator.playVideoPreview(e, SwingUtilities.getWindowAncestor(b), b),
                dataCoordinator::getModelStatus, dataCoordinator::resolveModelSize,
                f -> { if (onWorkflowImportRequested != null) onWorkflowImportRequested.accept(f); },
                this::showDetails).setVisible(true);
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
        if (gridRenderer != null) gridRenderer.updateTheme(darkMode);
        if (civitaiGalleryTab != null) civitaiGalleryTab.updateTheme(darkMode);
        applyFilter();
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

    @SuppressWarnings("unused")
    private String getModelStatus(ModelInfo info) {
        return dataCoordinator != null ? dataCoordinator.getModelStatus(info) : "Idle";
    }

    @SuppressWarnings("unused")
    private boolean isModelPresentDeep(ModelInfo info) {
        return dataCoordinator != null && dataCoordinator.isModelPresentDeep(info);
    }

    /**
     * Returns the Comfy.org blueprints toggle button.
     *
     * @return JToggleButton
     */
    public JToggleButton getBtnSubComfy() {
        return btnSubComfy;
    }

    /**
     * Returns the Civitai model hub toggle button.
     *
     * @return JToggleButton
     */
    public JToggleButton getBtnSubCivitai() {
        return btnSubCivitai;
    }

    /**
     * Returns the Hugging Face hub toggle button.
     *
     * @return JToggleButton
     */
    public JToggleButton getBtnSubHf() {
        return btnSubHf;
    }

    /**
     * Returns the embedded CivitaiGalleryTab component.
     *
     * @return CivitaiGalleryTab
     */
    public CivitaiGalleryTab getCivitaiGalleryTab() {
        return civitaiGalleryTab;
    }

    /**
     * Returns the Comfy.org blueprints header toolbar.
     *
     * @return BlueprintHeaderToolbar
     */
    public BlueprintHeaderToolbar getHeaderToolbar() {
        return headerToolbar;
    }

    /**
     * Returns the Comfy.org blueprints grid renderer.
     *
     * @return BlueprintGridRenderer
     */
    public BlueprintGridRenderer getGridRenderer() {
        return gridRenderer;
    }

    /**
     * Returns a thread-safe snapshot list of all loaded blueprint entries.
     *
     * @return list of BlueprintEntry objects
     */
    public List<BlueprintEntry> getAllEntries() {
        return new ArrayList<>(allEntries);
    }
}
