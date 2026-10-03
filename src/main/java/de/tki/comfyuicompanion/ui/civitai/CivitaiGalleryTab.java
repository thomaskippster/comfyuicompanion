package de.tki.comfyuicompanion.ui.civitai;

import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.ICivitaiHubService;
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

    private final CivitaiHeaderToolbar headerToolbar;
    private final CivitaiGridRenderer gridRenderer;

    private final AtomicBoolean isQuerying = new AtomicBoolean(false);
    private boolean initialLoaded = false;

    /**
     * Constructs a new CivitaiGalleryTab with injected dependencies.
     *
     * @param civitaiHubService the Civitai API service
     * @param downloadManager   the download manager service
     * @param configService     the application configuration service
     */
    @Autowired
    public CivitaiGalleryTab(ICivitaiHubService civitaiHubService,
                             IDownloadManager downloadManager,
                             ConfigService configService) {
        super(new BorderLayout());
        this.civitaiHubService = civitaiHubService;
        this.downloadManager = downloadManager;
        this.configService = configService;

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

    private void buildUi() {
        setOpaque(true);

        headerToolbar.setOnFilterChanged(this::performSearch);
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
                            civitaiHubService::isModelInstalled,
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
     * Initiates downloading a Civitai model through the download manager.
     *
     * @param entry the model entry to download
     */
    public void handleDownload(CivitaiModelEntry entry) {
        if (entry == null) return;

        ModelInfo modelInfo = civitaiHubService.createModelInfoForDownload(entry);
        String modelsBaseDir = configService != null ? configService.getModelsPath() : "models";

        logger.info("Initiating download for Civitai model: {} to {}", entry.name(), modelInfo.getSave_path());
        gridRenderer.updateDownloadStatus(entry.id(), "Starting...");

        downloadManager.startQueue(
                List.of(modelInfo),
                new boolean[]{true},
                modelsBaseDir,
                (index, status) -> SwingUtilities.invokeLater(() -> gridRenderer.updateDownloadStatus(entry.id(), status)),
                () -> SwingUtilities.invokeLater(() -> {
                    gridRenderer.updateDownloadStatus(entry.id(), "✅ Finished");
                    downloadManager.notifyComfyUI(true);
                    headerToolbar.setStatusText("Downloaded " + entry.name());
                })
        );
    }

    /**
     * Opens the detailed inspection dialog for a selected model.
     *
     * @param entry the model entry to inspect
     */
    public void handleDetails(CivitaiModelEntry entry) {
        if (entry == null) return;
        boolean installed = civitaiHubService.isModelInstalled(entry);
        Window owner = SwingUtilities.getWindowAncestor(this);
        CivitaiModelDetailsDialog dialog = new CivitaiModelDetailsDialog(owner, entry, installed, this::handleDownload);
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
