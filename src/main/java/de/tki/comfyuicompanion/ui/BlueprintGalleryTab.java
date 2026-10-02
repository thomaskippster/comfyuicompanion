package de.tki.comfyuicompanion.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import de.tki.comfyuicompanion.domain.*;
import de.tki.comfyuicompanion.service.*;
import de.tki.comfyuicompanion.service.impl.*;
import de.tki.comfyuicompanion.ui.blueprint.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.swing.*;
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
 * Blueprint Gallery tab for exploring, filtering, inspecting, and launching
 * workflow templates fetched from Comfy.org or bundled blueprints.
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
    private BlueprintHeaderToolbar headerToolbar;
    private BlueprintGridRenderer gridRenderer;
    private JPanel galleryWrapper;

    private final List<BlueprintEntry> allEntries = new CopyOnWriteArrayList<>();
    private final AtomicBoolean isRefreshing = new AtomicBoolean(false);
    private final javax.swing.Timer filterDebounceTimer = new javax.swing.Timer(250, e -> applyFilter());
    private boolean hasScanned = false;
    private Runnable onDataLoadedCallback;
    private Consumer<File> onWorkflowImportRequested;

    /** Constructs a new BlueprintGalleryTab with necessary dependencies. */
    @Autowired
    public BlueprintGalleryTab(ConfigService configService, IModelArchitectureService modelArchitectureService,
                               IComfyLifecycleService lifecycleService, IComfyRegistryClient registryClient,
                               ILocalModelValidator localModelValidator, IWorkflowDownloader workflowDownloader,
                               LocalModelScanner localModelScanner, ArchiveService archiveService,
                               LocalAIService localAIService, IModelSearchService modelSearchService,
                               @Autowired(required = false) ProcessTracker processTracker) {
        this.configService = configService;
        this.lifecycleService = lifecycleService;
        this.localModelValidator = localModelValidator;
        this.workflowDownloader = workflowDownloader;
        this.localAIService = localAIService;
        this.modelSearchService = modelSearchService;
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

    /** Registers a callback invoked when the user requests to import a workflow file. */
    public void setOnWorkflowImportRequested(Consumer<File> callback) { this.onWorkflowImportRequested = callback; }

    /** Registers a callback invoked when blueprint data finishes loading. */
    public void setOnDataLoadedCallback(Runnable callback) { this.onDataLoadedCallback = callback; }

    /** Triggers a debounced re-filter of visible blueprints. */
    public void requestFilterUpdate() {
        if (SwingUtilities.isEventDispatchThread()) filterDebounceTimer.restart();
        else SwingUtilities.invokeLater(filterDebounceTimer::restart);
    }

    private void buildUI(boolean initialDark) {
        headerToolbar = new BlueprintHeaderToolbar(initialDark);
        headerToolbar.setOnSearchChanged(this::requestFilterUpdate);
        headerToolbar.setOnCategoryChanged(this::applyFilter);
        headerToolbar.setOnReadyOnlyChanged(this::applyFilter);
        headerToolbar.setOnHideCloudChanged(this::applyFilter);
        headerToolbar.setOnRefreshClicked(() -> refreshAllData(true));
        add(headerToolbar, BorderLayout.NORTH);
        gridRenderer = new BlueprintGridRenderer(previewCache);
        this.galleryWrapper = gridRenderer.getComponent();
        add(gridRenderer.getScrollPane(), BorderLayout.CENTER);
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

        Thread thread = new Thread(() -> {
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
                    if (onDataLoadedCallback != null) try { onDataLoadedCallback.run(); } catch (Exception ignored) {}
                });
            } catch (Exception e) {
                logger.error("❌ [BlueprintGallery] Refresh failed: {}", e.getMessage(), e);
                SwingUtilities.invokeLater(() -> {
                    headerToolbar.setStatusText("❌ Failed to fetch registry data: " + e.getMessage());
                    headerToolbar.setRefreshing(false);
                });
            } finally { isRefreshing.set(false); }
        }, "BlueprintGalleryRefresh");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    private void updateStatusAsync() {
        if (!isRefreshing.compareAndSet(false, true)) return;
        Thread t = new Thread(() -> {
            try {
                if (localModelValidator != null) localModelValidator.scanLocalModels();
                for (BlueprintEntry e : allEntries) e.status = computeStatus(e);
                requestFilterUpdate();
            } catch (Exception e) {
                logger.error("❌ [BlueprintGallery] Status update failed: {}", e.getMessage());
            } finally { isRefreshing.set(false); }
        }, "BlueprintGalleryStatusUpdate");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
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
}
