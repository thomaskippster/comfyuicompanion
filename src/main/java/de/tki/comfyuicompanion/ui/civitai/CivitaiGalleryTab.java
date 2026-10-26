package de.tki.comfyuicompanion.ui.civitai;

import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.ICivitaiHubService;
import de.tki.comfyuicompanion.service.ICivitaiOperationalModelService;
import de.tki.comfyuicompanion.service.IDownloadManager;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.huggingface.HuggingFaceGalleryView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main gallery tab for the Model Hub, providing a unified provider switch between
 * the Civitai Model Hub and the Hugging Face Hub browser.
 * Features asynchronous querying, debounce, card rendering, and live download state synchronization.
 */
@Component
public class CivitaiGalleryTab extends JPanel {

    private static final Logger logger = LoggerFactory.getLogger(CivitaiGalleryTab.class);

    private final ICivitaiHubService civitaiHubService;
    private final IDownloadManager downloadManager;
    private final ConfigService configService;
    private final ICivitaiOperationalModelService operationalModelService;
    private final de.tki.comfyuicompanion.service.IAbortedDownloadService abortedDownloadService;
    private final HuggingFaceGalleryView huggingFaceGalleryView;
    private final de.tki.comfyuicompanion.service.IModelFavoritesRepository modelFavoritesRepository;

    private final CivitaiHeaderToolbar headerToolbar;
    private final CivitaiGridRenderer gridRenderer;

    private final CardLayout hubCardLayout = new CardLayout();
    private final JPanel hubContentPanel = new JPanel(hubCardLayout);
    private JPanel providerBar;
    private JToggleButton civitaiToggle;
    private JToggleButton hfToggle;

    private final AtomicBoolean isQuerying = new AtomicBoolean(false);
    private boolean initialLoaded = false;
    private volatile List<CivitaiModelEntry> currentResults = Collections.emptyList();
    private java.util.function.Consumer<List<ModelInfo>> onSwitchToDownloadManager;

    /**
     * Constructs a new CivitaiGalleryTab with all dependencies.
     *
     * @param civitaiHubService       the Civitai API service
     * @param downloadManager         the download manager service
     * @param configService           the application configuration service
     * @param operationalModelService service for operational dependency resolution and workflow generation
     * @param abortedDownloadService  service for scanning, resuming, and cleaning incomplete downloads
     * @param huggingFaceGalleryView  browser component for the Hugging Face Hub
     */
    @Autowired
    public CivitaiGalleryTab(
            ICivitaiHubService civitaiHubService,
            IDownloadManager downloadManager,
            ConfigService configService,
            @Autowired(required = false) ICivitaiOperationalModelService operationalModelService,
            @Autowired(required = false) de.tki.comfyuicompanion.service.IAbortedDownloadService abortedDownloadService,
            @Autowired(required = false) HuggingFaceGalleryView huggingFaceGalleryView,
            @Autowired(required = false) de.tki.comfyuicompanion.service.IModelFavoritesRepository modelFavoritesRepository) {
        super(new BorderLayout(0, 0));
        this.civitaiHubService = civitaiHubService;
        this.downloadManager = downloadManager;
        this.configService = configService;
        this.operationalModelService = operationalModelService;
        this.abortedDownloadService = abortedDownloadService;
        this.huggingFaceGalleryView = huggingFaceGalleryView;
        this.modelFavoritesRepository = modelFavoritesRepository;

        headerToolbar = new CivitaiHeaderToolbar();
        gridRenderer = new CivitaiGridRenderer();

        buildUi();

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                if (!initialLoaded) {
                    initialLoaded = true;
                    loadDynamicFilters();
                    performSearch();
                }
            }
        });
    }

    /**
     * Constructor supporting legacy callers without HuggingFaceGalleryView.
     *
     * @param civitaiHubService       the Civitai API service
     * @param downloadManager         the download manager service
     * @param configService           the application configuration service
     * @param operationalModelService service for operational model analysis
     * @param abortedDownloadService  aborted download service
     */
    public CivitaiGalleryTab(
            ICivitaiHubService civitaiHubService,
            IDownloadManager downloadManager,
            ConfigService configService,
            ICivitaiOperationalModelService operationalModelService,
            de.tki.comfyuicompanion.service.IAbortedDownloadService abortedDownloadService) {
        this(civitaiHubService, downloadManager, configService, operationalModelService, abortedDownloadService, null, null);
    }

    /**
     * Convenience constructor supporting operationalModelService without abortedDownloadService.
     *
     * @param civitaiHubService       the Civitai API service
     * @param downloadManager         the download manager service
     * @param configService           the application configuration service
     * @param operationalModelService service for operational model analysis
     */
    public CivitaiGalleryTab(
            ICivitaiHubService civitaiHubService,
            IDownloadManager downloadManager,
            ConfigService configService,
            ICivitaiOperationalModelService operationalModelService) {
        this(civitaiHubService, downloadManager, configService, operationalModelService, null, null, null);
    }

    /**
     * Minimal constructor for standalone or test scenarios.
     *
     * @param civitaiHubService the Civitai API service
     * @param downloadManager   the download manager service
     * @param configService     the application configuration service
     */
    public CivitaiGalleryTab(
            ICivitaiHubService civitaiHubService,
            IDownloadManager downloadManager,
            ConfigService configService) {
        this(civitaiHubService, downloadManager, configService, null, null, null, null);
    }

    private void buildUi() {
        setOpaque(true);

        // 1. Top Provider Switcher Bar
        providerBar = new JPanel(new BorderLayout(15, 0));
        providerBar.setOpaque(false);
        providerBar.setBorder(new EmptyBorder(10, 15, 6, 15));

        JPanel switchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        switchPanel.setOpaque(false);

        civitaiToggle = new JToggleButton("Civitai Model Hub", true);
        civitaiToggle.setFont(new Font("SansSerif", Font.BOLD, 12));
        civitaiToggle.setPreferredSize(new Dimension(150, 30));

        hfToggle = new JToggleButton("Hugging Face Hub", false);
        hfToggle.setFont(new Font("SansSerif", Font.BOLD, 12));
        hfToggle.setPreferredSize(new Dimension(150, 30));

        ButtonGroup group = new ButtonGroup();
        group.add(civitaiToggle);
        group.add(hfToggle);

        civitaiToggle.addActionListener(e -> hubCardLayout.show(hubContentPanel, "CIVITAI"));
        hfToggle.addActionListener(e -> {
            hubCardLayout.show(hubContentPanel, "HUGGINGFACE");
            if (huggingFaceGalleryView != null) {
                huggingFaceGalleryView.ensureInitialSearch();
            }
        });

        switchPanel.add(civitaiToggle);
        switchPanel.add(hfToggle);
        providerBar.add(switchPanel, BorderLayout.WEST);

        add(providerBar, BorderLayout.NORTH);

        // 2. Civitai Card View
        JPanel civitaiPanel = new JPanel(new BorderLayout(0, 8));
        civitaiPanel.setOpaque(false);
        headerToolbar.setOnFilterChanged(this::performSearch);
        headerToolbar.setOnIncompleteDownloadsClicked(this::handleIncompleteDownloads);
        civitaiPanel.add(headerToolbar, BorderLayout.NORTH);
        civitaiPanel.add(gridRenderer.getScrollPane(), BorderLayout.CENTER);

        // 3. Multi-Card Container
        hubContentPanel.setOpaque(false);
        hubContentPanel.add(civitaiPanel, "CIVITAI");

        if (huggingFaceGalleryView != null) {
            hubContentPanel.add(huggingFaceGalleryView, "HUGGINGFACE");
        } else {
            JPanel fallbackHf = new JPanel(new GridBagLayout());
            fallbackHf.setOpaque(false);
            fallbackHf.add(new JLabel("Hugging Face Hub is initializing..."));
            hubContentPanel.add(fallbackHf, "HUGGINGFACE");
        }

        add(hubContentPanel, BorderLayout.CENTER);

        loadDynamicFilters();

        if (downloadManager != null) {
            downloadManager.addProgressListener((filename, status) -> SwingUtilities.invokeLater(() -> {
                for (CivitaiModelEntry entry : currentResults) {
                    if (filename.equalsIgnoreCase(entry.filename()) || filename.equalsIgnoreCase(entry.name())) {
                        gridRenderer.updateDownloadStatus(entry.id(), status);
                        break;
                    }
                }
            }));
        }

        boolean dark = configService == null || configService.isDarkMode();
        updateTheme(dark);
    }

    /**
     * Loads dynamic architecture and model type filters from Civitai metadata asynchronously.
     */
    /**
     * Registers a callback invoked when a model download should switch to the Download Manager tab.
     *
     * @param callback the callback receiving missing model weights
     */
    public void setOnSwitchToDownloadManager(java.util.function.Consumer<List<ModelInfo>> callback) {
        this.onSwitchToDownloadManager = callback;
        if (huggingFaceGalleryView != null) {
            huggingFaceGalleryView.setOnSwitchToDownloadManager(callback);
        }
    }

    /**
     * Toggles the visibility of the internal provider switcher bar.
     * Useful when CivitaiGalleryTab is embedded inside BlueprintGalleryTab's unified sub-tabs.
     *
     * @param visible true to show the provider switcher bar, false to hide it
     */
    public void setProviderBarVisible(boolean visible) {
        if (providerBar != null) {
            providerBar.setVisible(visible);
        }
    }

    /**
     * Switches the active view to Civitai Model Hub and triggers search if not loaded yet.
     */
    public void showCivitaiView() {
        if (civitaiToggle != null) civitaiToggle.setSelected(true);
        hubCardLayout.show(hubContentPanel, "CIVITAI");
        if (!initialLoaded) {
            initialLoaded = true;
            loadDynamicFilters();
            performSearch();
        }
        hubContentPanel.revalidate();
        hubContentPanel.repaint();
    }

    /**
     * Switches the active view to Hugging Face Hub and triggers initial search if not loaded yet.
     */
    public void showHuggingFaceView() {
        if (hfToggle != null) hfToggle.setSelected(true);
        hubCardLayout.show(hubContentPanel, "HUGGINGFACE");
        if (huggingFaceGalleryView != null) {
            huggingFaceGalleryView.ensureInitialSearch();
        }
        hubContentPanel.revalidate();
        hubContentPanel.repaint();
    }

    public HuggingFaceGalleryView getHuggingFaceGalleryView() {
        return huggingFaceGalleryView;
    }

    public CivitaiHeaderToolbar getHeaderToolbar() {
        return headerToolbar;
    }

    public CivitaiGridRenderer getGridRenderer() {
        return gridRenderer;
    }

    public void loadDynamicFilters() {
        if (civitaiHubService == null) {
            return;
        }
        civitaiHubService.getActiveBaseModels()
                .thenAccept(headerToolbar::setAvailableBaseModels)
                .exceptionally(ex -> {
                    logger.debug("Failed loading dynamic base models from Civitai: {}", ex.getMessage());
                    return null;
                });
        civitaiHubService.getModelTypes()
                .thenAccept(headerToolbar::setAvailableTypes)
                .exceptionally(ex -> {
                    logger.debug("Failed loading dynamic model types from Civitai: {}", ex.getMessage());
                    return null;
                });
    }

    /**
     * Executes an asynchronous query against Civitai API on a virtual thread,
     * updating the grid renderer and status indicators upon completion.
     */
    public void performSearch() {
        if (!isQuerying.compareAndSet(false, true)) {
            logger.debug("Civitai search already running, skipping overlapping call.");
            return;
        }

        String query = headerToolbar.getSearchQuery();
        String type = headerToolbar.getSelectedType();
        String baseModel = headerToolbar.getSelectedBaseModel();
        String sort = headerToolbar.getSelectedSort();
        boolean allowNsfw = headerToolbar.isAllowNsfw();
        boolean favoritesOnly = headerToolbar.isFavoritesOnly();

        headerToolbar.setStatusText("Fetching models from Civitai...");
        headerToolbar.setRefreshEnabled(false);
        gridRenderer.showLoading();

        Thread.ofVirtual().name("Civitai-SearchWorker").start(() -> {
            try {
                List<CivitaiModelEntry> rawResults = civitaiHubService.queryModels(query, type, baseModel, sort, 30, 1, allowNsfw).join();
                final List<CivitaiModelEntry> results;
                if (favoritesOnly && modelFavoritesRepository != null && rawResults != null) {
                    results = rawResults.stream()
                            .filter(e -> modelFavoritesRepository.isFavorite(String.valueOf(e.id()))
                                    || modelFavoritesRepository.isFavorite(e.name())
                                    || modelFavoritesRepository.isFavorite(e.filename()))
                            .toList();
                } else {
                    results = rawResults;
                }
                this.currentResults = results != null ? results : Collections.emptyList();
                SwingUtilities.invokeLater(() -> {
                    gridRenderer.renderEntries(
                            results,
                            entry -> {
                                if (operationalModelService != null) {
                                    List<ModelInfo> req = operationalModelService.resolveRequiredModels(entry);
                                    return operationalModelService.areAllModelsInstalled(req);
                                }
                                return civitaiHubService.isModelInstalled(entry);
                            },
                            this::handleDownload,
                            this::handleDetails
                    );

                    // Sync any in-flight download statuses onto the cards
                    if (downloadManager != null && results != null) {
                        for (CivitaiModelEntry entry : results) {
                            Optional<String> active = downloadManager.getActiveDownloadStatus(entry.filename());
                            if (active.isPresent()) {
                                gridRenderer.updateDownloadStatus(entry.id(), active.get());
                            }
                        }
                    }

                    headerToolbar.setStatusText(currentResults.size() + " models found");
                    headerToolbar.setRefreshEnabled(true);
                });
            } catch (Exception ex) {
                logger.error("Failed to fetch Civitai models: {}", ex.getMessage(), ex);
                SwingUtilities.invokeLater(() -> {
                    gridRenderer.showError(ex.getMessage());
                    headerToolbar.setStatusText("Error fetching models");
                    headerToolbar.setRefreshEnabled(true);
                });
            } finally {
                isQuerying.set(false);
            }
        });
    }

    /**
     * Initiates downloading a Civitai model along with its operational dependencies.
     *
     * @param entry the model entry to download and prepare
     */
    public void handleDownload(CivitaiModelEntry entry) {
        if (entry == null) return;
        handleDetails(entry);
    }

    /**
     * Opens the detailed metadata dialog for a selected Civitai model entry.
     *
     * @param entry the model entry to inspect
     */
    public void handleDetails(CivitaiModelEntry entry) {
        if (entry == null) return;
        Window owner = SwingUtilities.getWindowAncestor(this);
        CivitaiModelDetailsDialog dialog = new CivitaiModelDetailsDialog(
                owner,
                entry,
                operationalModelService,
                downloadManager,
                configService,
                onSwitchToDownloadManager
        );
        dialog.setVisible(true);
    }

    /**
     * Opens the dialog to inspect and manage aborted or incomplete downloads.
     */
    public void handleIncompleteDownloads() {
        if (abortedDownloadService == null) return;
        Window owner = SwingUtilities.getWindowAncestor(this);
        CivitaiAbortedDownloadsDialog dialog = new CivitaiAbortedDownloadsDialog(
                owner,
                abortedDownloadService,
                downloadManager,
                configService,
                this::performSearch
        );
        dialog.setVisible(true);
    }

    /**
     * Updates theme styling across the tab, toolbar, provider switch, and card grid renderer.
     *
     * @param darkMode true for dark theme, false for light theme
     */
    public void updateTheme(boolean darkMode) {
        setBackground(darkMode ? new Color(15, 16, 21) : new Color(245, 247, 250));
        if (headerToolbar != null) {
            headerToolbar.updateTheme(darkMode);
        }
        gridRenderer.updateTheme(darkMode);
        if (huggingFaceGalleryView != null) {
            huggingFaceGalleryView.updateTheme(darkMode);
        }
        repaint();
    }
}
