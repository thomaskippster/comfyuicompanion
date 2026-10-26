package de.tki.comfyuicompanion.ui.huggingface;

import de.tki.comfyuicompanion.domain.HuggingFaceFileEntry;
import de.tki.comfyuicompanion.domain.HuggingFaceModelEntry;
import de.tki.comfyuicompanion.service.IDownloadManager;
import de.tki.comfyuicompanion.service.IHuggingFaceHubService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.HuggingFaceWorkflowDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main gallery view for browsing, exploring, and downloading models from the Hugging Face Hub,
 * matching the visual structure, layout, responsive card grid, and detail dialog workflow of the Civitai Model Hub.
 */
@Component
public class HuggingFaceGalleryView extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(HuggingFaceGalleryView.class);

    private final IHuggingFaceHubService huggingFaceHubService;
    private final IDownloadManager downloadManager;
    private final ConfigService configService;
    private final HuggingFaceWorkflowDispatcher workflowDispatcher;

    private final HuggingFaceHeaderToolbar headerToolbar;
    private final HuggingFaceGridRenderer gridRenderer;

    private final AtomicBoolean isQuerying = new AtomicBoolean(false);
    private boolean initialLoaded = false;
    private volatile List<HuggingFaceModelEntry> currentResults = Collections.emptyList();
    private java.util.function.Consumer<List<de.tki.comfyuicompanion.domain.ModelInfo>> onSwitchToDownloadManager;

    /**
     * Constructs a new HuggingFaceGalleryView with injected dependencies.
     *
     * @param huggingFaceHubService service for querying and inspecting Hugging Face repositories
     * @param downloadManager       service for managing active and queued downloads
     * @param configService         service for configuration and paths
     * @param workflowDispatcher    dispatcher for ComfyUI canvas workflows and generation
     */
    @Autowired
    public HuggingFaceGalleryView(
            IHuggingFaceHubService huggingFaceHubService,
            IDownloadManager downloadManager,
            ConfigService configService,
            @Autowired(required = false) HuggingFaceWorkflowDispatcher workflowDispatcher) {
        super(new BorderLayout(0, 8));
        this.huggingFaceHubService = huggingFaceHubService;
        this.downloadManager = downloadManager;
        this.configService = configService;
        this.workflowDispatcher = workflowDispatcher;

        this.headerToolbar = new HuggingFaceHeaderToolbar();
        this.gridRenderer = new HuggingFaceGridRenderer();

        buildUi();
    }

    /**
     * Backward-compatible constructor without explicit workflowDispatcher.
     *
     * @param huggingFaceHubService service for querying and inspecting Hugging Face repositories
     * @param downloadManager       service for managing active and queued downloads
     * @param configService         service for configuration and paths
     */
    public HuggingFaceGalleryView(
            IHuggingFaceHubService huggingFaceHubService,
            IDownloadManager downloadManager,
            ConfigService configService) {
        this(huggingFaceHubService, downloadManager, configService, null);
    }

    private void buildUi() {
        setOpaque(true);

        headerToolbar.setOnFilterChanged(this::performSearch);
        add(headerToolbar, BorderLayout.NORTH);
        add(gridRenderer.getScrollPane(), BorderLayout.CENTER);

        if (downloadManager != null) {
            downloadManager.addProgressListener((filename, status) -> SwingUtilities.invokeLater(() -> {
                for (HuggingFaceModelEntry repo : currentResults) {
                    if (repo.files() != null) {
                        for (HuggingFaceFileEntry f : repo.files()) {
                            if (filename.equalsIgnoreCase(f.filename())) {
                                gridRenderer.updateDownloadStatus(repo.repoId(), status);
                                return;
                            }
                        }
                    } else if (repo.modelName() != null && filename.toLowerCase().contains(repo.modelName().toLowerCase())) {
                        gridRenderer.updateDownloadStatus(repo.repoId(), status);
                        return;
                    }
                }
            }));
        }

        boolean dark = configService == null || configService.isDarkMode();
        updateTheme(dark);
    }

    /**
     * Triggers initial search when the Hugging Face view is first displayed.
     */
    /**
     * Registers a callback invoked when a download should switch to the Download Manager tab.
     *
     * @param callback the callback receiving missing model weights
     */
    public void setOnSwitchToDownloadManager(java.util.function.Consumer<List<de.tki.comfyuicompanion.domain.ModelInfo>> callback) {
        this.onSwitchToDownloadManager = callback;
    }

    public void ensureInitialSearch() {
        if (!initialLoaded) {
            initialLoaded = true;
            performSearch();
        }
    }

    /**
     * Performs an asynchronous search on a virtual thread against the Hugging Face API.
     */
    public void performSearch() {
        if (!isQuerying.compareAndSet(false, true)) {
            log.debug("Hugging Face search already in progress, skipping overlapping call.");
            return;
        }

        String query = headerToolbar.getSearchQuery();
        String format = headerToolbar.getSelectedFormat();
        String task = headerToolbar.getSelectedTask();
        String sort = headerToolbar.getSelectedSort();

        headerToolbar.setStatusText("Searching Hugging Face Hub...");
        headerToolbar.setRefreshEnabled(false);
        gridRenderer.showLoading();

        Thread.ofVirtual().name("HuggingFace-SearchWorker").start(() -> {
            try {
                String effectiveQuery = (query != null && !query.isBlank()) ? query.trim() : null;
                List<HuggingFaceModelEntry> results = huggingFaceHubService.searchRepositories(
                        effectiveQuery, format, task, sort, 30
                ).join();

                this.currentResults = results != null ? results : Collections.emptyList();
                SwingUtilities.invokeLater(() -> {
                    try {
                    gridRenderer.renderEntries(
                            currentResults,
                            this::isRepoInstalled,
                            this::handleDetails,
                            this::handleDetails
                    );

                    // Sync in-flight downloads onto cards
                    if (downloadManager != null && results != null) {
                        for (HuggingFaceModelEntry repo : results) {
                            if (repo.files() != null) {
                                for (HuggingFaceFileEntry f : repo.files()) {
                                    Optional<String> active = downloadManager.getActiveDownloadStatus(f.filename());
                                    if (active.isPresent()) {
                                        gridRenderer.updateDownloadStatus(repo.repoId(), active.get());
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    headerToolbar.setStatusText(currentResults.size() + " models found on Hugging Face");
                    headerToolbar.setRefreshEnabled(true);
                    } catch (Exception ex) {
                        log.error("Failed rendering Hugging Face grid: {}", ex.getMessage(), ex);
                        gridRenderer.showError(ex.getMessage());
                        headerToolbar.setRefreshEnabled(true);
                    }
                });
            } catch (Exception ex) {
                log.error("Failed searching Hugging Face repositories: {}", ex.getMessage(), ex);
                SwingUtilities.invokeLater(() -> {
                    gridRenderer.showError(ex.getMessage());
                    headerToolbar.setStatusText("Error fetching Hugging Face models");
                    headerToolbar.setRefreshEnabled(true);
                });
            } finally {
                isQuerying.set(false);
            }
        });
    }

    /**
     * Opens the modal details dialog for a selected Hugging Face repository.
     *
     * @param entry the repository entry to inspect and download from
     */
    public void handleDetails(HuggingFaceModelEntry entry) {
        if (entry == null) return;
        Window owner = SwingUtilities.getWindowAncestor(this);
        HuggingFaceModelDetailsDialog dialog = new HuggingFaceModelDetailsDialog(
                owner,
                entry,
                huggingFaceHubService,
                downloadManager,
                configService,
                workflowDispatcher,
                this::checkFileStatus,
                onSwitchToDownloadManager
        );
        dialog.setVisible(true);
    }

    private boolean isRepoInstalled(HuggingFaceModelEntry repo) {
        if (repo == null || huggingFaceHubService == null || repo.files() == null || repo.files().isEmpty()) {
            return false;
        }
        for (HuggingFaceFileEntry f : repo.files()) {
            if (!huggingFaceHubService.isFileInstalled(f.filename(), f.suggestedFolder())) {
                return false;
            }
        }
        return true;
    }
    private String checkFileStatus(HuggingFaceFileEntry file) {
        if (file == null || huggingFaceHubService == null) return "Missing";
        if (huggingFaceHubService.isFileInstalled(file.filename(), file.suggestedFolder())) {
            return "Installed";
        }
        if (huggingFaceHubService.isFileInArchive(file.filename())) {
            return "In Archive";
        }
        return "Missing";
    }

    /**
     * Returns the header toolbar component.
     *
     * @return HuggingFaceHeaderToolbar
     */
    public HuggingFaceHeaderToolbar getHeaderToolbar() {
        return headerToolbar;
    }

    /**
     * Returns the grid renderer component.
     *
     * @return HuggingFaceGridRenderer
     */
    public HuggingFaceGridRenderer getGridRenderer() {
        return gridRenderer;
    }

    /**
     * Updates colors and styling for dark/light themes.
     *
     * @param darkMode true for dark theme
     */
    public void updateTheme(boolean darkMode) {
        setBackground(darkMode ? new Color(15, 16, 21) : new Color(245, 247, 250));
        headerToolbar.updateTheme(darkMode);
        gridRenderer.updateTheme(darkMode);
        repaint();
    }
}


