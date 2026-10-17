package de.tki.comfyuicompanion.ui.civitai;

import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.ICivitaiHubService;
import de.tki.comfyuicompanion.service.ICivitaiOperationalModelService;
import de.tki.comfyuicompanion.service.IDownloadManager;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main coordinator panel for the Civitai Model Hub gallery tab.
 * Manages model searches, architecture filtering, model detail inspection,
 * and seamless background downloading into the ComfyUI models hierarchy.
 */
@Component
public class CivitaiGalleryTab extends JPanel {

    private static final Logger logger = LoggerFactory.getLogger(CivitaiGalleryTab.class);

    private final ICivitaiHubService civitaiHubService;
    private final IDownloadManager downloadManager;
    private final ConfigService configService;
    private final ICivitaiOperationalModelService operationalModelService;
    private final de.tki.comfyuicompanion.service.IAbortedDownloadService abortedDownloadService;

    private final CivitaiHeaderToolbar headerToolbar;
    private final CivitaiGridRenderer gridRenderer;

    private final AtomicBoolean isQuerying = new AtomicBoolean(false);
    private boolean initialLoaded = false;

    /**
     * Constructs a new CivitaiGalleryTab with injected dependencies.
     *
     * @param civitaiHubService       the Civitai API service
     * @param downloadManager         the download manager service
     * @param configService           the application configuration service
     * @param operationalModelService service for operational dependency resolution and workflow generation
     * @param abortedDownloadService  service for scanning, resuming, and cleaning incomplete downloads
     */
    @Autowired
    public CivitaiGalleryTab(ICivitaiHubService civitaiHubService,
                             IDownloadManager downloadManager,
                             ConfigService configService,
                             @Autowired(required = false) ICivitaiOperationalModelService operationalModelService,
                             @Autowired(required = false) de.tki.comfyuicompanion.service.IAbortedDownloadService abortedDownloadService) {
        super(new BorderLayout());
        this.civitaiHubService = civitaiHubService;
        this.downloadManager = downloadManager;
        this.configService = configService;
        this.operationalModelService = operationalModelService;
        this.abortedDownloadService = abortedDownloadService;

        headerToolbar = new CivitaiHeaderToolbar();
        gridRenderer = new CivitaiGridRenderer();

        buildUi();

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                if (!initialLoaded) {
                    initialLoaded = true;
                    performSearch();
                }
            }
        });
    }

    /**
     * Convenience constructor supporting operationalModelService without abortedDownloadService.
     *
     * @param civitaiHubService       the Civitai API service
     * @param downloadManager         the download manager service
     * @param configService           the application configuration service
     * @param operationalModelService operational model service
     */
    public CivitaiGalleryTab(ICivitaiHubService civitaiHubService,
                             IDownloadManager downloadManager,
                             ConfigService configService,
                             ICivitaiOperationalModelService operationalModelService) {
        this(civitaiHubService, downloadManager, configService, operationalModelService, null);
    }

    /**
     * Convenience constructor for backwards compatibility or tests.
     *
     * @param civitaiHubService the Civitai API service
     * @param downloadManager   the download manager service
     * @param configService     the application configuration service
     */
    public CivitaiGalleryTab(ICivitaiHubService civitaiHubService,
                             IDownloadManager downloadManager,
                             ConfigService configService) {
        this(civitaiHubService, downloadManager, configService, null, null);
    }

    private void buildUi() {
        setOpaque(true);

        headerToolbar.setOnFilterChanged(this::performSearch);
        headerToolbar.setOnIncompleteDownloadsClicked(this::handleIncompleteDownloads);
        add(headerToolbar, BorderLayout.NORTH);
        add(gridRenderer.getScrollPane(), BorderLayout.CENTER);

        boolean dark = configService == null || configService.isDarkMode();
        updateTheme(dark);
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

        headerToolbar.setStatusText("Fetching models from Civitai...");
        headerToolbar.setRefreshEnabled(false);
        gridRenderer.showLoading();

        Thread.ofVirtual().name("Civitai-SearchWorker").start(() -> {
            try {
                List<CivitaiModelEntry> results = civitaiHubService.queryModels(query, type, baseModel, sort, 30, 1, allowNsfw).join();
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
                    headerToolbar.setStatusText(results.size() + " models found");
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
     * Delegates to the operational details dialog to ensure users have full visibility
     * into complementary models and direct generation dispatching.
     *
     * @param entry the model entry to download and prepare
     */
    public void handleDownload(CivitaiModelEntry entry) {
        if (entry == null) return;
        handleDetails(entry);
    }

    /**
     * Opens the detailed inspection dialog for a selected model, resolving all complementary
     * operational dependencies (base checkpoint, VAE, CLIP) and enabling ComfyUI dispatch.
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
                configService
        );
        dialog.setVisible(true);
        // Refresh statuses to reflect newly downloaded models
        performSearch();
    }

    /**
     * Opens the incomplete downloads dialog allowing users to inspect, resume,
     * or purge aborted and partial download files to reclaim disk space.
     */
    public void handleIncompleteDownloads() {
        if (abortedDownloadService == null) return;
        Window owner = SwingUtilities.getWindowAncestor(this);
        CivitaiAbortedDownloadsDialog dialog = new CivitaiAbortedDownloadsDialog(
                owner,
                abortedDownloadService,
                configService,
                this::performSearch
        );
        dialog.setVisible(true);
    }

    /**
     * Updates theme styling across the tab, toolbar, and card grid renderer.
     *
     * @param darkMode true for dark theme, false for light theme
     */
    public void updateTheme(boolean darkMode) {
        setBackground(darkMode ? new Color(15, 16, 21) : new Color(245, 247, 250));
        gridRenderer.updateTheme(darkMode);
        repaint();
    }
}
