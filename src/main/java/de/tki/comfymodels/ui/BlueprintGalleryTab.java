package de.tki.comfymodels.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tki.comfymodels.Main;
import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.domain.ComfyRegistryWorkflow;
import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.ProcessTracker;
import de.tki.comfymodels.service.impl.LocalModelScanner;
import de.tki.comfymodels.service.impl.ArchiveService;
import de.tki.comfymodels.service.IModelArchitectureService;
import de.tki.comfymodels.service.IComfyLifecycleService;
import de.tki.comfymodels.service.IComfyRegistryClient;
import de.tki.comfymodels.service.ILocalModelValidator;
import de.tki.comfymodels.service.IWorkflowDownloader;
import org.json.JSONObject;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.metaloom.video4j.VideoFile;
import io.metaloom.video4j.VideoFrame;
import io.metaloom.video4j.Videos;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;

/**
 * Blueprint Gallery – redesigned Model Manager tab.
 *
 * Fetches workflow metadata from the Comfy.org cloud registry API,
 * validates required model files against the local filesystem,
 * and allows saving templates with their offline previews.
 */
@Component
public class BlueprintGalleryTab extends JPanel {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(BlueprintGalleryTab.class);

    // ── services ─────────────────────────────────────────────────────────────
    private final ConfigService configService;
    private final IModelArchitectureService modelArchitectureService;
    private final IComfyLifecycleService lifecycleService;
    private final IComfyRegistryClient registryClient;
    private final ILocalModelValidator localModelValidator;
    private final IWorkflowDownloader workflowDownloader;
    private final LocalModelScanner localModelScanner;
    private final ArchiveService archiveService;
    private final de.tki.comfymodels.service.impl.LocalAIService localAIService;
    private final ProcessTracker processTracker;
    private final de.tki.comfymodels.service.IModelSearchService modelSearchService;
    private final ObjectMapper mapper = new ObjectMapper();

    // ── ui ────────────────────────────────────────────────────────────────────
    private JPanel galleryWrapper;   // holds the GridLayout gallery
    private JPanel northPanel;
    private JLabel lblSummary;
    private JLabel lblStatus;
    private JTextField txtSearch;
    private JComboBox<String> cbCategory;
    private ThemedCheckBox chkReadyOnly;
    private ThemedCheckBox chkHideCloud;
    private JButton btnRefresh;
    private JLabel lblTitle;
    private JScrollPane scroll;

    private static final HttpClient SHARED_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    private static final ExecutorService imageLoadExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "BlueprintPreviewLoader");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    // ── data ──────────────────────────────────────────────────────────────────
    private final List<BlueprintEntry> allEntries = new CopyOnWriteArrayList<>();
    private final java.util.concurrent.atomic.AtomicBoolean isRefreshing = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final javax.swing.Timer filterDebounceTimer = new javax.swing.Timer(250, e -> {
        applyFilter();
    });
    private final List<JPanel> categoryGridPanels = new CopyOnWriteArrayList<>();
    private final Map<String, Image> previewCache = new ConcurrentHashMap<>();
    private final Map<String, Image> scaledPreviewCache = new ConcurrentHashMap<>();
    private final Set<String> loadingPaths = ConcurrentHashMap.newKeySet();
    private boolean hasScanned = false;
    private boolean initialLoadComplete = false;
    private boolean hasSetInitialDefaultCategory = false;
    private final Set<String> failedPaths = ConcurrentHashMap.newKeySet();
    private final Set<String> failedUrls = ConcurrentHashMap.newKeySet();

    public void requestFilterUpdate() {
        if (SwingUtilities.isEventDispatchThread()) {
            filterDebounceTimer.restart();
        } else {
            SwingUtilities.invokeLater(filterDebounceTimer::restart);
        }
    }
    private Runnable onDataLoadedCallback;

    public void setOnDataLoadedCallback(Runnable callback) {
        this.onDataLoadedCallback = callback;
    }


    // ── design ────────────────────────────────────────────────────────────────
    private Color bgPage          = new Color(15, 16, 21);
    private Color cardBg          = new Color(24, 26, 34);
    private Color cardBorder      = new Color(48, 52, 68);
    private Color cardHoverBorder = new Color(0, 210, 190);
    private Color textPrimary     = new Color(228, 230, 240);
    private Color textSecondary   = new Color(120, 126, 152);
    private Color textDim         = new Color(80, 86, 110);
    private static final Color GREEN_READY     = new Color(34, 197, 130);
    private static final Color AMBER_PARTIAL   = new Color(255, 153, 0); // Neon Orange #FF9900
    private static final Color RED_MISSING     = new Color(239, 68, 68);
    private static final Color BADGE_BG        = new Color(0, 0, 0, 160);

    private static final int CARD_W     = 220;
    private static final int CARD_H     = 280;
    private static final int PREVIEW_H  = 140;
    private static final int ARC        = 14;
    private static final int CARD_GAP   = 14;

    // ── category palette map ─────────────────────────────────────────────────
    private static final List<String[]> CAT_PALETTES = List.of(
        new String[]{"text to image",     "#0A1440", "#1A5CD4", "#55AAFF"},
        new String[]{"text to video",     "#100A35", "#6B2CE0", "#C080FF"},
        new String[]{"image to video",    "#280A40", "#B030D0", "#FF80FF"},
        new String[]{"image edit",        "#0D2810", "#1A9050", "#40FF90"},
        new String[]{"image inpaint",     "#1A2000", "#8A9800", "#D4FF00"},
        new String[]{"depth",             "#0A2830", "#0090B0", "#00D4FF"},
        new String[]{"controlnet",        "#280814", "#B02040", "#FF4080"},
        new String[]{"pose",              "#281400", "#B06000", "#FF9000"},
        new String[]{"upscal",            "#1A1040", "#5040C0", "#A090FF"},
        new String[]{"segmentation",      "#0A2810", "#30A050", "#70FF90"},
        new String[]{"caption",           "#280A10", "#C03060", "#FF80A0"},
        new String[]{"background",        "#1A1A10", "#909010", "#FFFF40"},
        new String[]{"frame",             "#0A1828", "#205880", "#50AACC"},
        new String[]{"interpolat",        "#1A0A28", "#702898", "#CC60FF"},
        new String[]{"brightness",        "#281800", "#B07000", "#FFB800"},
        new String[]{"color",             "#001828", "#006880", "#00CCE8"},
        new String[]{"blur",              "#181820", "#505080", "#9090CC"},
        new String[]{"grain",             "#1A1210", "#706050", "#B09880"},
        new String[]{"glow",              "#0A1028", "#3050A0", "#80B0FF"},
        new String[]{"sharpen",           "#0A2018", "#208060", "#40C080"},
        new String[]{"audio",             "#280A18", "#B03060", "#FF70A0"},
        new String[]{"3d",               "#102800", "#408800", "#80FF00"},
        new String[]{"video",             "#1A0A38", "#7030C8", "#C060FF"}
    );
    private static final String[] DEFAULT_PALETTE = {"#0C0E16", "#2A3060", "#5060A0"};

    @Autowired
    public BlueprintGalleryTab(ConfigService configService,
                               IModelArchitectureService modelArchitectureService,
                               IComfyLifecycleService lifecycleService,
                               IComfyRegistryClient registryClient,
                               ILocalModelValidator localModelValidator,
                               IWorkflowDownloader workflowDownloader,
                               LocalModelScanner localModelScanner,
                               ArchiveService archiveService,
                               de.tki.comfymodels.service.impl.LocalAIService localAIService,
                               de.tki.comfymodels.service.IModelSearchService modelSearchService,
                               @org.springframework.beans.factory.annotation.Autowired(required = false) ProcessTracker processTracker) {
        this.configService = configService;
        this.modelArchitectureService = modelArchitectureService;
        this.lifecycleService = lifecycleService;
        this.registryClient = registryClient;
        this.localModelValidator = localModelValidator;
        this.workflowDownloader = workflowDownloader;
        this.localModelScanner = localModelScanner;
        this.archiveService = archiveService;
        this.localAIService = localAIService;
        this.modelSearchService = modelSearchService;
        this.processTracker = processTracker;
        this.filterDebounceTimer.setRepeats(false);

        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(bgPage);

        buildUI();
        boolean darkMode = configService != null ? configService.isDarkMode() : true;
        updateTheme(darkMode);
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                if (!hasScanned) {
                    refreshAllData();
                } else {
                    updateStatusAsync();
                }
            }
        });
    }

    private void buildUI() {
        // ── TOP BAR ──────────────────────────────────────────────────────────
        JPanel topBar = new JPanel();
        topBar.setLayout(new BoxLayout(topBar, BoxLayout.Y_AXIS));
        topBar.setOpaque(false);
        topBar.setBorder(new EmptyBorder(14, 22, 6, 22));

        // Row 1: Left: summary / status, Right: search, category, refresh
        JPanel row1 = new JPanel(new BorderLayout(12, 0));
        row1.setOpaque(false);

        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.setOpaque(false);

        lblTitle = new JLabel(" ");
        lblSummary = new JLabel(" ");
        lblSummary.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lblSummary.setForeground(textSecondary);

        titleBlock.add(lblSummary);
        row1.add(titleBlock, BorderLayout.WEST);

        JPanel mainControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        mainControls.setOpaque(false);

        txtSearch = new JTextField(14);
        txtSearch.putClientProperty("JTextField.placeholderText", "Search blueprints…");
        txtSearch.setFont(new Font("SansSerif", Font.PLAIN, 12));
        txtSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { requestFilterUpdate(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { requestFilterUpdate(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { requestFilterUpdate(); }
        });

        cbCategory = new JComboBox<>(new String[]{"All Categories"});
        cbCategory.setFont(new Font("SansSerif", Font.PLAIN, 12));
        cbCategory.addActionListener(e -> requestFilterUpdate());

        btnRefresh = new JButton("🔄  Refresh");
        btnRefresh.setFont(new Font("SansSerif", Font.PLAIN, 12));
        btnRefresh.putClientProperty("Button.arc", 999);
        btnRefresh.addActionListener(e -> refreshAllData(true));

        mainControls.add(txtSearch);
        mainControls.add(cbCategory);
        mainControls.add(btnRefresh);
        row1.add(mainControls, BorderLayout.EAST);

        // Row 2: Dedicated Filter Checkboxes Bar
        JPanel row2 = new JPanel(new BorderLayout(12, 0));
        row2.setOpaque(false);
        row2.setBorder(new EmptyBorder(6, 0, 2, 0));

        JPanel filterControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 0));
        filterControls.setOpaque(false);

        boolean initDark = configService != null ? configService.isDarkMode() : true;
        chkReadyOnly = new ThemedCheckBox("Ready only (no missing models)", initDark);
        chkReadyOnly.addActionListener(e -> requestFilterUpdate());

        chkHideCloud = new ThemedCheckBox("Hide cloud-only workflows", initDark);
        chkHideCloud.setSelected(true);
        chkHideCloud.addActionListener(e -> requestFilterUpdate());

        filterControls.add(chkReadyOnly);
        filterControls.add(chkHideCloud);
        row2.add(filterControls, BorderLayout.EAST);

        topBar.add(row1);
        topBar.add(row2);

        // Status line
        lblStatus = new JLabel(" ");
        lblStatus.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lblStatus.setForeground(textSecondary);
        lblStatus.setBorder(new EmptyBorder(0, 22, 8, 22));

        northPanel = new JPanel(new BorderLayout());
        northPanel.setOpaque(true);
        northPanel.setBackground(bgPage);
        northPanel.add(topBar,    BorderLayout.NORTH);
        northPanel.add(lblStatus, BorderLayout.SOUTH);
        add(northPanel, BorderLayout.NORTH);

        // ── GALLERY AREA ─────────────────────────────────────────────────────
        galleryWrapper = new ScrollablePanel(null);
        galleryWrapper.setOpaque(true);
        galleryWrapper.setBackground(bgPage);
        galleryWrapper.setBorder(new EmptyBorder(10, 16, 20, 16));
        galleryWrapper.setLayout(new BoxLayout(galleryWrapper, BoxLayout.Y_AXIS));

        galleryWrapper.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                recomputeColumns();
            }
        });

        scroll = new JScrollPane(galleryWrapper,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setOpaque(true);
        scroll.setBackground(bgPage);
        scroll.getViewport().setOpaque(true);
        scroll.getViewport().setBackground(bgPage);
        scroll.getViewport().setScrollMode(JViewport.BLIT_SCROLL_MODE);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        add(scroll, BorderLayout.CENTER);
    }

    private void recomputeColumns() {
        int available = galleryWrapper.getWidth()
                - galleryWrapper.getInsets().left
                - galleryWrapper.getInsets().right;
        if (available <= 0) return;
        int cols = Math.max(1, available / (CARD_W + CARD_GAP));
        
        for (JPanel grid : categoryGridPanels) {
            if (grid.getLayout() instanceof GridLayout gl) {
                if (gl.getColumns() != cols) {
                    gl.setColumns(cols);
                    grid.revalidate();
                }
            }
        }
    }

    // ── DATA LOADING ─────────────────────────────────────────────────────────

    public void refreshAllData() {
        refreshAllData(false);
    }

    public void refreshAllData(boolean forceRefresh) {
        if (!forceRefresh && !isRefreshing.compareAndSet(false, true)) {
            logger.info("ℹ️ [BlueprintGallery] Refresh already in progress, skipping duplicate call.");
            return;
        }
        if (forceRefresh) {
            isRefreshing.set(true);
        }
        hasScanned = true;

        Runnable setupUiState = () -> {
            lblStatus.setText("⏳  Fetching workflows and checking local models in background…");
            if (btnRefresh != null) {
                btnRefresh.setEnabled(false);
                btnRefresh.setText("🔄  Refreshing…");
            }
            if (allEntries.isEmpty()) {
                previewCache.clear();
                scaledPreviewCache.clear();
                loadingPaths.clear();
                failedPaths.clear();

                // Clear gallery and show a modern loading indicator/progress bar in the background
                galleryWrapper.removeAll();
                JPanel loadingPanel = new JPanel(new GridBagLayout());
                loadingPanel.setOpaque(false);
                loadingPanel.setPreferredSize(new Dimension(800, 400));
                
                JProgressBar spinner = new JProgressBar();
                spinner.setIndeterminate(true);
                spinner.setPreferredSize(new Dimension(240, 6));
                spinner.putClientProperty("FlatLaf.style", "arc: 999; foreground: $SlimStat.barForeground; background: $SlimStat.barBackground;");
                
                JLabel loadingLabel = new JLabel("Loading Blueprint Gallery...");
                loadingLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
                loadingLabel.setForeground(textSecondary);
                loadingLabel.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
                
                JPanel inner = new JPanel();
                inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
                inner.setOpaque(false);
                inner.add(loadingLabel);
                inner.add(Box.createVerticalStrut(12));
                inner.add(spinner);
                
                loadingPanel.add(inner);
                galleryWrapper.add(loadingPanel);
                galleryWrapper.revalidate();
                galleryWrapper.repaint();
            }
        };

        if (SwingUtilities.isEventDispatchThread()) {
            setupUiState.run();
        } else {
            SwingUtilities.invokeLater(setupUiState);
        }

        Thread refreshThread = new Thread(() -> {
            try {
                // 1. Scan local models in background
                if (localModelValidator != null) {
                    localModelValidator.scanLocalModels();
                }

                // 2. Fetch cloud workflows from Comfy.org API in background
                List<ComfyRegistryWorkflow> workflows = registryClient != null ? registryClient.fetchWorkflowsAsync().get() : Collections.emptyList();

                // 3. Map to internal entries with indexed fast lookup
                List<Map<String, Object>> scanResults = Collections.emptyList();
                if (modelArchitectureService != null) {
                    try {
                        List<Map<String, Object>> res = modelArchitectureService.getBlueprintScanResults();
                        if (res != null) {
                            scanResults = res;
                        }
                    } catch (Exception ignored) {}
                }

                Map<String, Map<String, Object>> scanLookup = new HashMap<>();
                for (Map<String, Object> scan : scanResults) {
                    String scanName = (String) scan.get("name");
                    String scanFilename = (String) scan.get("filename");
                    if (scanName != null) scanLookup.put(scanName.toLowerCase(Locale.ROOT), scan);
                    if (scanFilename != null) {
                        String fnLower = scanFilename.toLowerCase(Locale.ROOT);
                        scanLookup.put(fnLower, scan);
                        if (fnLower.endsWith(".json")) {
                            scanLookup.put(fnLower.substring(0, fnLower.length() - 5), scan);
                        }
                    }
                }

                List<BlueprintEntry> loadedEntries = new ArrayList<>();
                Set<String> seenIds = new HashSet<>();

                for (ComfyRegistryWorkflow wf : workflows) {
                    String wfId = wf.getId() != null ? wf.getId() : wf.getTitle();
                    if (wfId == null || !seenIds.add(wfId)) {
                        continue;
                    }

                    BlueprintEntry entry = new BlueprintEntry(wf);
                    
                    // Match with scanned results from ModelArchitectureService via O(1) lookup
                    Map<String, Object> matchedScan = null;
                    if (wfId != null) matchedScan = scanLookup.get(wfId.toLowerCase(Locale.ROOT));
                    if (matchedScan == null && wf.getTitle() != null) {
                        matchedScan = scanLookup.get(wf.getTitle().toLowerCase(Locale.ROOT));
                    }

                    List<ModelInfo> resolved = null;
                    if (matchedScan != null) {
                        Object reqObj = matchedScan.get("requiredModels");
                        resolved = extractModelInfos(reqObj, mapper);
                    }
                    if ((resolved == null || resolved.isEmpty()) && localModelValidator != null) {
                        if (wfId != null) resolved = localModelValidator.getCachedModelsForWorkflow(wfId);
                        if ((resolved == null || resolved.isEmpty()) && wf.getTitle() != null) {
                            resolved = localModelValidator.getCachedModelsForWorkflow(wf.getTitle());
                        }
                    }

                    if (resolved != null && !resolved.isEmpty()) {
                        entry.requiredModels.clear();
                        entry.requiredModels.addAll(resolved);
                        entry.status = computeStatusInternal(entry);
                    } else if (wf.getRequiredModelInfos() != null && !wf.getRequiredModelInfos().isEmpty()) {
                        entry.requiredModels.clear();
                        entry.requiredModels.addAll(wf.getRequiredModelInfos());
                        entry.status = computeStatusInternal(entry);
                    } else {
                        // We don't have the actual models parsed yet. Set status to Unknown and fetch asynchronously.
                        entry.status = new BlueprintStatus(0, 0, new ArrayList<>()); // Temporary unknown status
                        if (localModelValidator != null) {
                            localModelValidator.validateWorkflowModelsAsync(wf).thenAccept(v -> {
                                entry.requiredModels.clear();
                                if (wf.getRequiredModelInfos() != null) {
                                    entry.requiredModels.addAll(wf.getRequiredModelInfos());
                                }
                                entry.status = computeStatusInternal(entry);
                                // Debounced update so we don't flood the EDT
                                requestFilterUpdate();
                            });
                        }
                    }

                    loadedEntries.add(entry);
                }

                allEntries.clear();
                allEntries.addAll(loadedEntries);

                SwingUtilities.invokeLater(() -> {
                    updateCategoryComboBox();
                    if (btnRefresh != null) {
                        btnRefresh.setEnabled(true);
                        btnRefresh.setText("🔄  Refresh");
                    }
                    applyFilter();
                    updateSummary();
                    lblStatus.setText(" ");
                    initialLoadComplete = true;
                    if (localAIService != null) {
                        localAIService.setGemmaEnabled(true);
                    }
                    if (onDataLoadedCallback != null) {
                        try {
                            onDataLoadedCallback.run();
                        } catch (Exception ignored) {}
                    }
                });

            } catch (Exception e) {
                logger.error("❌ [BlueprintGallery] Refresh failed: " + e.getMessage(), e);
                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("❌ Failed to fetch registry data: " + e.getMessage());
                    if (btnRefresh != null) {
                        btnRefresh.setEnabled(true);
                        btnRefresh.setText("🔄  Refresh");
                    }
                });
            } finally {
                isRefreshing.set(false);
            }
        }, "BlueprintGalleryRefresh");
        refreshThread.setDaemon(true);
        refreshThread.setPriority(Thread.MIN_PRIORITY);
        refreshThread.start();
    }

    private void updateStatusAsync() {
        if (!isRefreshing.compareAndSet(false, true)) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                if (localModelValidator != null) {
                    localModelValidator.scanLocalModels();
                }
                for (BlueprintEntry entry : allEntries) {
                    entry.status = computeStatusInternal(entry);
                }
                requestFilterUpdate();
            } catch (Exception e) {
                logger.error("❌ [BlueprintGallery] Status update failed: " + e.getMessage());
            } finally {
                isRefreshing.set(false);
            }
        }, "BlueprintGalleryStatusUpdate");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    // ── GALLERY RENDERING ─────────────────────────────────────────────────────

    public static boolean isVideoBlueprint(BlueprintEntry entry) {
        if (entry == null) return false;
        if (entry.registryWorkflow != null && entry.registryWorkflow.isVideoPreview()) {
            return true;
        }
        String cat = entry.category != null ? entry.category.toLowerCase(Locale.ROOT) : "";
        String name = entry.name != null ? entry.name.toLowerCase(Locale.ROOT) : "";
        String desc = entry.description != null ? entry.description.toLowerCase(Locale.ROOT) : "";
        
        if (cat.contains("video") || cat.contains("animate") || cat.contains("motion")
                || cat.contains("i2v") || cat.contains("t2v") || cat.contains("v2v")
                || cat.contains("interpolat") || cat.contains("frame")) {
            return true;
        }
        if (name.contains("text to video") || name.contains("image to video") || name.contains("video to video")
                || name.contains("video") || name.contains("animatediff") || name.contains("cogvideo")
                || name.contains("wan 2") || name.contains("hunyuanvideo") || name.contains("ltx-video")
                || desc.contains("generates video") || desc.contains("text-to-video") || desc.contains("image-to-video")) {
            return true;
        }
        return false;
    }

    public static int getBlueprintMediaRank(BlueprintEntry entry) {
        if (entry == null) return 2;
        if (isVideoBlueprint(entry)) {
            return 1; // Video blueprints come after Image blueprints
        }
        String cat = entry.category != null ? entry.category.toLowerCase(Locale.ROOT) : "";
        if (cat.contains("audio") || cat.contains("sound") || cat.contains("music") || cat.contains("voice")) {
            return 2;
        }
        if (cat.contains("3d") || cat.contains("mesh")) {
            return 2;
        }
        // Image blueprints (Text to Image, Image Edit, Inpaint, ControlNet, Upscaling, Depth, Pose, etc.)
        return 0; // Image blueprints come first!
    }

    public static int getCategoryOrderScore(String categoryName) {
        if (categoryName == null || categoryName.trim().isEmpty()) return 500;
        String cat = categoryName.toLowerCase(Locale.ROOT).trim();

        // 1. IMAGE Categories (0 - 99): Image blueprints come first!
        if (cat.contains("text to image") || cat.contains("txt2img") || cat.contains("text-to-image")) return 10;
        if (cat.contains("image edit") || cat.contains("image to image") || cat.contains("img2img")) return 20;
        if (cat.contains("inpaint") || cat.contains("outpaint")) return 30;
        if (cat.contains("controlnet")) return 40;
        if (cat.contains("pose") || cat.contains("depth")) return 50;
        if (cat.contains("upscal")) return 60;
        if (cat.contains("segmentation")) return 70;
        if (cat.contains("caption")) return 80;
        if (cat.contains("background") || cat.contains("style")) return 85;
        if (cat.contains("image") || cat.contains("face") || cat.contains("photo") || cat.contains("art")) return 90;

        // 2. VIDEO Categories (100 - 199): Video blueprints come after image blueprints!
        if (cat.contains("text to video") || cat.contains("t2v") || cat.contains("text-to-video")) return 110;
        if (cat.contains("image to video") || cat.contains("i2v") || cat.contains("image-to-video")) return 120;
        if (cat.contains("video to video") || cat.contains("v2v") || cat.contains("video-to-video")) return 130;
        if (cat.contains("video") || cat.contains("animate") || cat.contains("motion")
                || cat.contains("interpolat") || cat.contains("frame")) return 140;

        // 3. 3D, Audio, Other (200 - 300)
        if (cat.contains("3d") || cat.contains("mesh")) return 210;
        if (cat.contains("audio") || cat.contains("sound") || cat.contains("music") || cat.contains("voice")) return 220;
        if (cat.contains("use case") || cat.contains("star")) return 230;

        return 300;
    }

    private void updateCategoryComboBox() {
        if (cbCategory == null) return;

        // Prevent ActionEvents while rebuilding items
        java.awt.event.ActionListener[] listeners = cbCategory.getActionListeners();
        for (java.awt.event.ActionListener l : listeners) {
            cbCategory.removeActionListener(l);
        }

        String selected = (String) cbCategory.getSelectedItem();

        cbCategory.removeAllItems();
        cbCategory.addItem("All Categories");

        Set<String> categories = new HashSet<>();
        for (BlueprintEntry entry : allEntries) {
            String cat = entry.category != null && !entry.category.isEmpty() ? entry.category : "General";
            categories.add(cat);
        }

        List<String> sortedCategories = new ArrayList<>(categories);
        sortedCategories.sort((c1, c2) -> {
            int o1 = getCategoryOrderScore(c1);
            int o2 = getCategoryOrderScore(c2);
            if (o1 != o2) {
                return Integer.compare(o1, o2);
            }
            return c1.compareToIgnoreCase(c2);
        });

        for (String cat : sortedCategories) {
            cbCategory.addItem(cat);
        }

        if (selected != null) {
            cbCategory.setSelectedItem(selected);
        } else {
            cbCategory.setSelectedIndex(0);
        }

        for (java.awt.event.ActionListener l : listeners) {
            cbCategory.addActionListener(l);
        }
    }

    private void applyFilter() {
        if (txtSearch == null || cbCategory == null) return; // UI initialization guard
        
        final int scrollVal = scroll != null ? scroll.getVerticalScrollBar().getValue() : 0;

        String filter = txtSearch.getText().toLowerCase(Locale.ROOT).trim();
        String selectedCategory = (String) cbCategory.getSelectedItem();
        galleryWrapper.removeAll();
        categoryGridPanels.clear();

        List<BlueprintEntry> visible = new ArrayList<>();
        for (BlueprintEntry e : allEntries) {
            boolean matchesSearch = filter.isEmpty()
                    || (e.name != null && e.name.toLowerCase(Locale.ROOT).contains(filter))
                    || (e.description != null && e.description.toLowerCase(Locale.ROOT).contains(filter));

            if (!matchesSearch) continue;

            // Option: Only workflows with no missing models
            if (chkReadyOnly.isSelected() && computeStatus(e).missingCount > 0) {
                continue;
            }

            // Option: Hide cloud-only workflows
            if (chkHideCloud.isSelected() && e.registryWorkflow.isCloudOnly()) {
                continue;
            }

            // Category filter
            if (selectedCategory != null && !"All Categories".equals(selectedCategory)) {
                String entryCat = e.category != null && !e.category.isEmpty() ? e.category : "General";
                if (!selectedCategory.equalsIgnoreCase(entryCat)) {
                    continue;
                }
            }

            visible.add(e);
        }

        // Sort implementation:
        // 1) Image blueprints first, then Video blueprints, then other media
        // 2) Category order score (Text to Image -> Image Edit -> ControlNet... -> Text to Video -> Image to Video...)
        // 3) Runnable workflows first
        // 4) Popularity / popularScore descending
        // 5) Name alphabetically
        visible.sort((e1, e2) -> {
            int rank1 = getBlueprintMediaRank(e1);
            int rank2 = getBlueprintMediaRank(e2);
            if (rank1 != rank2) {
                return Integer.compare(rank1, rank2);
            }

            int catOrder1 = getCategoryOrderScore(e1.category);
            int catOrder2 = getCategoryOrderScore(e2.category);
            if (catOrder1 != catOrder2) {
                return Integer.compare(catOrder1, catOrder2);
            }

            boolean run1 = computeStatus(e1).missingCount == 0;
            boolean run2 = computeStatus(e2).missingCount == 0;
            if (run1 != run2) {
                return run1 ? -1 : 1;
            }

            double pop1 = e1.registryWorkflow != null ? e1.registryWorkflow.getPopularScore() : 0.0;
            double pop2 = e2.registryWorkflow != null ? e2.registryWorkflow.getPopularScore() : 0.0;
            int popCmp = Double.compare(pop2, pop1);
            if (popCmp != 0) {
                return popCmp;
            }

            String n1 = e1.name != null ? e1.name : "";
            String n2 = e2.name != null ? e2.name : "";
            return n1.compareToIgnoreCase(n2);
        });

        if (visible.isEmpty()) {
            JLabel empty = new JLabel("No blueprints match your filter criteria.");
            empty.setFont(new Font("SansSerif", Font.ITALIC, 13));
            empty.setForeground(textSecondary);
            empty.setBorder(new EmptyBorder(10, 4, 10, 4));
            galleryWrapper.add(empty);
        } else {
            // Group by category with Image categories first, then Video categories, then Other
            Map<String, List<BlueprintEntry>> grouped = new TreeMap<>((c1, c2) -> {
                int o1 = getCategoryOrderScore(c1);
                int o2 = getCategoryOrderScore(c2);
                if (o1 != o2) {
                    return Integer.compare(o1, o2);
                }
                return c1.compareToIgnoreCase(c2);
            });

            for (BlueprintEntry entry : visible) {
                String cat = entry.category != null && !entry.category.isEmpty() ? entry.category : "General";
                grouped.computeIfAbsent(cat, k -> new ArrayList<>()).add(entry);
            }

            for (Map.Entry<String, List<BlueprintEntry>> groupEntry : grouped.entrySet()) {
                String categoryName = groupEntry.getKey();
                List<BlueprintEntry> items = groupEntry.getValue();

                // 1. Add Category Header Panel
                galleryWrapper.add(buildCategoryHeader(categoryName));

                // 2. Add Category Grid Panel
                JPanel gridPanel = new JPanel();
                gridPanel.setOpaque(false);
                gridPanel.setBorder(new EmptyBorder(8, 4, 14, 4));
                // Grid layout with dynamic columns, updated on resize
                gridPanel.setLayout(new GridLayout(0, 3, CARD_GAP, CARD_GAP));

                for (BlueprintEntry entry : items) {
                    gridPanel.add(buildCard(entry));
                }

                categoryGridPanels.add(gridPanel);
                galleryWrapper.add(gridPanel);
            }
        }

        recomputeColumns();
        galleryWrapper.revalidate();
        galleryWrapper.repaint();

        updateSummary();

        if (scroll != null) {
            SwingUtilities.invokeLater(() -> {
                int max = scroll.getVerticalScrollBar().getMaximum() - scroll.getVerticalScrollBar().getModel().getExtent();
                scroll.getVerticalScrollBar().setValue(Math.min(scrollVal, max));
            });
        }
    }

    private JPanel buildCategoryHeader(String categoryName) {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(12, 4, 6, 4));
        headerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        String emoji = categoryIcon(categoryName);

        JLabel lblHeader = new JLabel(emoji + "  " + categoryName);
        lblHeader.setFont(new Font("SansSerif", Font.BOLD, 15));
        lblHeader.setForeground(textPrimary);
        headerPanel.add(lblHeader, BorderLayout.WEST);

        // Thin line under the category title
        JPanel line = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(cardBorder);
                g.drawLine(0, 0, getWidth(), 0);
            }
        };
        line.setPreferredSize(new Dimension(0, 1));
        headerPanel.add(line, BorderLayout.SOUTH);

        return headerPanel;
    }

    private void updateSummary() {
        int total = allEntries.size();
        int ready = 0, partial = 0, missing = 0;
        for (BlueprintEntry e : allEntries) {
            BlueprintStatus s = computeStatus(e);
            if (s.missingCount == 0) ready++;
            else if (s.presentCount > 0) partial++;
            else missing++;
        }
        lblSummary.setText(total + " workflows on Comfy.org  ·  "
                + ready + " ready  ·  "
                + partial + " partial  ·  "
                + missing + " not installed");
    }

    private JPanel buildCard(BlueprintEntry entry) {
        BlueprintStatus status = computeStatus(entry);
        Color[] palette = resolvePalette(entry);

        JPanel card = new JPanel(new BorderLayout()) {
            private boolean hovered = false;

            {
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hovered = true;  repaint(); }
                    @Override public void mouseExited(MouseEvent e)  { hovered = false; repaint(); }
                    @Override public void mouseClicked(MouseEvent e) {
                        if (e.getClickCount() == 1) showDetails(entry, status);
                    }
                });
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Theme-aware card background
                g2.setColor(cardBg);
                g2.fillRect(0, 0, getWidth(), getHeight());

                // Theme-aware border
                g2.setStroke(new BasicStroke(hovered ? 2f : 1f));
                
                if (hovered) {
                    // Outer glow using hover border with transparency
                    g2.setColor(new Color(cardHoverBorder.getRed(), cardHoverBorder.getGreen(), cardHoverBorder.getBlue(), 100));
                    g2.drawRect(1, 1, getWidth()-3, getHeight()-3);
                    g2.drawRect(2, 2, getWidth()-5, getHeight()-5);
                }
                
                // Solid border
                g2.setColor(hovered ? cardHoverBorder : cardBorder);
                g2.drawRect(0, 0, getWidth()-1, getHeight()-1);
                
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setPreferredSize(new Dimension(CARD_W, CARD_H));
        card.setMinimumSize(new Dimension(160, CARD_H));
        card.setMaximumSize(new Dimension(400, CARD_H));

        JPanel preview = buildPreviewPanel(entry, palette, status, PREVIEW_H);
        card.add(preview, BorderLayout.NORTH);

        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setOpaque(false);
        info.setBorder(new EmptyBorder(8, 10, 8, 10));

        JTextArea lblName = new JTextArea(entry.name);
        lblName.setFont(new Font("SansSerif", Font.BOLD, 12));
        lblName.setForeground(textPrimary);
        lblName.setLineWrap(true);
        lblName.setWrapStyleWord(true);
        lblName.setEditable(false);
        lblName.setFocusable(false);
        lblName.setOpaque(false);
        lblName.setBorder(null);
        lblName.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        lblName.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showDetails(entry, status);
            }
        });
        info.add(lblName);
        info.add(Box.createVerticalStrut(3));

        if (!entry.category.isEmpty()) {
            JLabel lblCat = new JLabel(entry.category);
            lblCat.setFont(new Font("SansSerif", Font.PLAIN, 10));
            lblCat.setForeground(textDim);
            info.add(lblCat);
            info.add(Box.createVerticalStrut(4));
        }

        int totalReqs = entry.requiredModels.size();
        String reqText;
        Color reqColor;
        if (totalReqs == 0) {
            reqText  = "✔  No external models needed";
            reqColor = GREEN_READY;
        } else if (status.missingCount == 0) {
            reqText  = "✔  All " + totalReqs + " model(s) ready";
            reqColor = GREEN_READY;
        } else {
            reqText  = "⚠  " + status.missingCount + " / " + totalReqs + " missing";
            reqColor = AMBER_PARTIAL;
        }
        
        JPanel statusRow = new JPanel(new BorderLayout());
        statusRow.setOpaque(false);

        JLabel lblReq = new JLabel(reqText);
        lblReq.setFont(new Font("SansSerif", Font.PLAIN, 10));
        lblReq.setForeground(reqColor);
        statusRow.add(lblReq, BorderLayout.WEST);

        info.add(statusRow);

        card.add(info, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildPreviewPanel(BlueprintEntry entry, Color[] palette,
                                     BlueprintStatus status, int height) {
        return new JPanel() {
            {
                setPreferredSize(new Dimension(CARD_W, height));
                setOpaque(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) {
                        if (entry.registryWorkflow != null && entry.registryWorkflow.isVideoPreview()) {
                            // Double click = open the video player; single click = show details.
                            if (e.getClickCount() == 2) {
                                playVideoPreview(entry, SwingUtilities.getWindowAncestor(BlueprintGalleryTab.this), null);
                            } else if (e.getClickCount() == 1) {
                                showDetails(entry, status);
                            }
                        } else if (e.getClickCount() == 1) {
                            showDetails(entry, status);
                        }
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,   RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING,      RenderingHints.VALUE_RENDER_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int w = getWidth(), h = getHeight();
                if (w <= 0 || h <= 0) {
                    g2.dispose();
                    return;
                }
                // No rounded corners for the preview clip
                g2.setClip(0, 0, w, h);

                Image img = previewCache.get(entry.filePath);
                if (img != null) {
                    String scaledKey = entry.filePath + "_" + w + "_" + h;
                    Image scaledImg = scaledPreviewCache.get(scaledKey);
                    if (scaledImg == null) {
                        int imgW = img.getWidth(null);
                        int imgH = img.getHeight(null);
                        if (imgW > 0 && imgH > 0) {
                            double scale = Math.max((double) w / imgW, (double) h / imgH);
                            int newW = (int) (imgW * scale);
                            int newH = (int) (imgH * scale);
                            BufferedImage bufferedScaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                            Graphics2D bg2 = bufferedScaled.createGraphics();
                            bg2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                            bg2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                            bg2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                            int x = (w - newW) / 2;
                            int y = (h - newH) / 2;
                            bg2.drawImage(img, x, y, newW, newH, null);
                            bg2.dispose();
                            scaledImg = bufferedScaled;
                            scaledPreviewCache.put(scaledKey, scaledImg);
                        }
                    }
                    if (scaledImg != null) {
                        g2.drawImage(scaledImg, 0, 0, null);
                    } else {
                        paintBokehFallback(g2, w, h, palette, entry);
                    }
                } else {
                    triggerImageLoad(entry, this);
                    paintBokehFallback(g2, w, h, palette, entry);
                }

                // Status badge
                String badgeText;
                Color badgeColor;
                if (status.missingCount == 0) {
                    badgeText  = "✅  Ready";
                    badgeColor = new Color(34, 197, 130, 220);
                } else if (status.missingCount > 0) {
                    badgeText  = "⚠  " + status.missingCount + " missing";
                    badgeColor = new Color(255, 153, 0, 220); // Neon Orange #FF9900

                } else {
                    badgeText  = null;
                    badgeColor = null;
                }

                if (badgeText != null) {
                    g2.setFont(new Font("SansSerif", Font.BOLD, 9));
                    FontMetrics fm = g2.getFontMetrics();
                    int bw = fm.stringWidth(badgeText) + 10;
                    int bh = 16;
                    int bx = w - bw - 6;
                    int by = 6;
                    g2.setColor(badgeColor);
                    g2.fillRect(bx, by, bw, bh); // Sharp corners
                    g2.setColor(Color.WHITE);
                    g2.drawString(badgeText, bx + 5, by + bh - 4);
                }

                // Video preview badge: a circular play icon bottom-left.
                if (entry.registryWorkflow != null && entry.registryWorkflow.isVideoPreview()) {
                    int pad = 6;
                    int size = 24;
                    int cx = pad + size / 2;
                    int cy = h - pad - size / 2;
                    g2.setColor(new Color(0, 0, 0, 110));
                    g2.fillOval(cx - size / 2, cy - size / 2, size, size);
                    // Small play triangle inside the circle.
                    int tx = cx - 4;
                    int ty = cy - 5;
                    int tw = 8;
                    int th = 10;
                    Polygon tri = new Polygon(new int[]{tx, tx, tx + tw}, new int[]{ty, ty + th, ty + th / 2}, 3);
                    g2.setColor(Color.WHITE);
                    g2.fill(tri);
                    // VIDEO label.
                    g2.setFont(new Font("SansSerif", Font.BOLD, 8));
                    g2.setColor(new Color(0, 0, 0, 150));
                    g2.drawString("VIDEO", pad + 30, h - pad - 4);
                }

                g2.dispose();
            }
        };
    }

    private void paintBokehFallback(Graphics2D g2, int w, int h, Color[] palette, BlueprintEntry entry) {
        // Theme-aware fallback background
        g2.setColor(cardBg);
        g2.fillRect(0, 0, w, h);

        // Subtle grid using border color with transparency
        g2.setColor(new Color(cardBorder.getRed(), cardBorder.getGreen(), cardBorder.getBlue(), 40));
        g2.setStroke(new BasicStroke(0.5f));
        for (int x = 0; x < w; x += 20) g2.drawLine(x, 0, x, h);
        for (int y = 0; y < h; y += 20) g2.drawLine(0, y, w, y);

        String icon = categoryIcon(entry.category);
        Font iconFont = new Font("SansSerif", Font.BOLD, 26);
        g2.setFont(iconFont);
        FontMetrics fm = g2.getFontMetrics();
        int ix = (w - fm.stringWidth(icon)) / 2;
        int iy = h / 2 - 4;
        g2.setColor(new Color(textPrimary.getRed(), textPrimary.getGreen(), textPrimary.getBlue(), 210));
        g2.drawString(icon, ix, iy);
    }

    private void triggerImageLoad(BlueprintEntry entry, JPanel panelToRepaint) {
        if (failedPaths.contains(entry.filePath)) {
            return;
        }
        if (loadingPaths.add(entry.filePath)) {
            CompletableFuture.supplyAsync(() -> loadPreviewImage(entry), imageLoadExecutor)
                .thenAccept(img -> {
                    if (img != null) {
                        previewCache.put(entry.filePath, img);
                        SwingUtilities.invokeLater(() -> panelToRepaint.repaint());
                    } else {
                        failedPaths.add(entry.filePath);
                    }
                    loadingPaths.remove(entry.filePath);
                });
        }
    }

    private Image loadPreviewImage(BlueprintEntry entry) {
        if (entry.previewPath != null && !entry.previewPath.isEmpty()) {
            String pathOrUrl = entry.previewPath;
            // Fix legacy cached URLs that erroneously included /templates/ for root folders
            if (pathOrUrl.contains("/main/templates/input/")) {
                pathOrUrl = pathOrUrl.replace("/main/templates/input/", "/main/input/");
            } else if (pathOrUrl.contains("/main/templates/output/")) {
                pathOrUrl = pathOrUrl.replace("/main/templates/output/", "/main/output/");
            } else if (pathOrUrl.contains("/main/templates/thumbnail/")) {
                pathOrUrl = pathOrUrl.replace("/main/templates/thumbnail/", "/main/thumbnail/");
            }

            if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
                Image img = readImageNoJavaFX(pathOrUrl);
                if (img != null) return img;

                // Fallback 1: If it's not webp, try webp version
                if (!pathOrUrl.endsWith(".webp")) {
                    int lastDot = pathOrUrl.lastIndexOf('.');
                    if (lastDot > 0) {
                        String webpUrl = pathOrUrl.substring(0, lastDot) + ".webp";
                        img = readImageNoJavaFX(webpUrl);
                        if (img != null) return img;
                    }
                }

                // Fallback 2: If it had "-1", try loading without it
                if (pathOrUrl.contains("-1.")) {
                    String fallbackUrl = pathOrUrl.replace("-1.", ".");
                    img = readImageNoJavaFX(fallbackUrl);
                    if (img != null) return img;

                    if (!fallbackUrl.endsWith(".webp")) {
                        int lastDot = fallbackUrl.lastIndexOf('.');
                        if (lastDot > 0) {
                            String webpFallbackUrl = fallbackUrl.substring(0, lastDot) + ".webp";
                            img = readImageNoJavaFX(webpFallbackUrl);
                            if (img != null) return img;
                        }
                    }
                }
            }
        }

        // Secondary fallback by workflow ID if primary preview URL failed
        if (entry.registryWorkflow != null && entry.registryWorkflow.getId() != null) {
            String id = entry.registryWorkflow.getId();
            String repoBase = "https://raw.githubusercontent.com/Comfy-Org/workflow_templates/main/";
            String[] candidates = new String[] {
                repoBase + "thumbnail/" + id + "_thumbnail.mp4",
                repoBase + "thumbnail/" + id + "_preview.mp4",
                repoBase + "thumbnail/" + id + ".png",
                repoBase + "thumbnail/" + id + ".webp",
                repoBase + "output/" + id + ".mp4",
                repoBase + "output/" + id + ".png",
                repoBase + "output/" + id + ".webp",
                repoBase + "input/" + id + "_input_image.png",
                repoBase + "input/" + id + ".png"
            };
            for (String cand : candidates) {
                if (cand.equals(entry.previewPath)) continue;
                Image img = readImageNoJavaFX(cand);
                if (img != null) return img;
            }

            // Also try any previewCandidates populated from io.inputs / io.outputs
            if (entry.registryWorkflow.getPreviewCandidates() != null) {
                for (String cand : entry.registryWorkflow.getPreviewCandidates()) {
                    if (cand != null && !cand.equals(entry.previewPath)) {
                        Image img = readImageNoJavaFX(cand);
                        if (img != null) return img;
                    }
                }
            }
        }

        return null;
    }


    private File getPreviewDiskCacheDir() {
        File dir = new File(configService != null ? configService.getAppDataPath() : System.getProperty("user.home") + File.separator + ".comfyuicompanion", "cache" + File.separator + "previews");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private String hashUrl(String url) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(url.hashCode());
        }
    }

    private Image readImageNoJavaFX(String urlStr) {
        if (urlStr == null || urlStr.trim().isEmpty()) return null;
        if (failedUrls.contains(urlStr)) return null;

        String cleanUrl = urlStr.toLowerCase();
        int qIdx = cleanUrl.indexOf('?');
        if (qIdx > 0) cleanUrl = cleanUrl.substring(0, qIdx);

        String ext = ".png";
        if (cleanUrl.endsWith(".webp")) ext = ".webp";
        else if (cleanUrl.endsWith(".mp4")) ext = ".mp4";
        else if (cleanUrl.endsWith(".webm")) ext = ".webm";
        else if (cleanUrl.endsWith(".gif")) ext = ".gif";

        boolean isVideo = cleanUrl.endsWith(".mp4") || cleanUrl.endsWith(".webm") || cleanUrl.endsWith(".mov");

        File cacheDir = getPreviewDiskCacheDir();
        File cachedFile = new File(cacheDir, hashUrl(urlStr) + ext);

        // 1. Check persistent disk cache first!
        if (cachedFile.exists() && cachedFile.length() > 0) {
            try {
                if (!isVideo) {
                    BufferedImage img = ImageIO.read(cachedFile);
                    if (img != null) return img;
                }
                return readImageFromFile(cachedFile, isVideo, urlStr);
            } catch (Exception ignored) {}
        }

        // 2. Fetch via shared HTTP client
        int maxRetries = 2;
        int delayMs = 300;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(urlStr))
                        .timeout(Duration.ofSeconds(8))
                        .GET().build();
                java.net.http.HttpResponse<byte[]> response = SHARED_HTTP_CLIENT.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
                
                if (response.statusCode() == 200) {
                    byte[] bytes = response.body();
                    
                    try {
                        Files.write(cachedFile.toPath(), bytes);
                    } catch (Exception ignored) {}

                    if (!isVideo) {
                        try {
                            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
                            if (img != null) return img;
                        } catch (Exception ignored) {}
                    }

                    return readImageFromFile(cachedFile, isVideo, urlStr);
                } else if (response.statusCode() == 404) {
                    failedUrls.add(urlStr);
                    break;
                }
            } catch (Exception e) {
                logger.debug("⚠️ [BlueprintGallery] Network error for {}: {}", urlStr, e.getMessage());
            }

            if (attempt < maxRetries) {
                try {
                    Thread.sleep(delayMs * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        failedUrls.add(urlStr);
        return null;
    }

    private Image readImageFromFile(File file, boolean isVideo, String originalUrl) {
        try {
            try {
                de.tki.comfymodels.util.OpenCvLoader.load();
            } catch (Throwable ignored) {}

            if (isVideo) {
                try {
                    try (VideoFile vid = Videos.open(file.getAbsolutePath())) {
                        vid.seekToFrame(0);
                        BufferedImage img = vid.frameToImage();
                        if (img != null) return img;
                    }
                } catch (Throwable ex) {
                    logger.debug("Video4j fallback failed for " + originalUrl, ex);
                }
            }

            Mat mat = Imgcodecs.imread(file.getAbsolutePath());
            if (mat != null && !mat.empty()) {
                MatOfByte buffer = new MatOfByte();
                Imgcodecs.imencode(".png", mat, buffer);
                return ImageIO.read(new ByteArrayInputStream(buffer.toArray()));
            } else {
                try {
                    org.opencv.videoio.VideoCapture cap = new org.opencv.videoio.VideoCapture(file.getAbsolutePath(), org.opencv.videoio.Videoio.CAP_FFMPEG);
                    if (!cap.isOpened()) {
                        cap = new org.opencv.videoio.VideoCapture(file.getAbsolutePath());
                    }
                    if (cap.isOpened()) {
                        Mat frame = new Mat();
                        if (cap.read(frame) && !frame.empty()) {
                            MatOfByte buffer = new MatOfByte();
                            Imgcodecs.imencode(".png", frame, buffer);
                            cap.release();
                            return ImageIO.read(new ByteArrayInputStream(buffer.toArray()));
                        }
                        cap.release();
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            logger.debug("OpenCV/Video processing failed for preview: " + originalUrl, t);
        }
        return null;
    }

    // ── DETAIL DIALOG ─────────────────────────────────────────────────────────

    private void showDetails(BlueprintEntry entry, BlueprintStatus status) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dlg = new JDialog(owner instanceof Frame ? (Frame) owner : null,
                "Blueprint: " + entry.name, true);
        dlg.setSize(1250, 720);
        dlg.setLocationRelativeTo(this);

        Color bg = configService.isDarkMode() ? new Color(20, 22, 30) : new Color(245, 247, 250);

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(bg);
        root.setBorder(new EmptyBorder(18, 20, 16, 20));

        JLabel title = new JLabel(entry.name);
        title.setFont(new Font("SansSerif", Font.BOLD, 17));
        title.setForeground(textPrimary);

        // If this is a video preview, wrap the title in a header panel that also
        // hosts a Play Preview button. For image previews the title is alone.
        boolean isVideoPreview = entry.registryWorkflow != null && entry.registryWorkflow.isVideoPreview();
        if (isVideoPreview) {
            JPanel header = new JPanel(new BorderLayout(8, 0));
            header.setOpaque(false);
            header.add(title, BorderLayout.WEST);
            JButton btnPlayPreview = new JButton("▶  Play Preview");
            btnPlayPreview.setFont(new Font("SansSerif", Font.BOLD, 11));
            btnPlayPreview.setBackground(new Color(0, 150, 136));
            btnPlayPreview.setForeground(Color.WHITE);
            btnPlayPreview.setFocusPainted(false);
            btnPlayPreview.putClientProperty("Button.arc", 999);
            btnPlayPreview.addActionListener(evt -> playVideoPreview(entry, dlg, btnPlayPreview));
            JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            headerRight.setOpaque(false);
            headerRight.add(btnPlayPreview);
            header.add(headerRight, BorderLayout.EAST);
            root.add(header, BorderLayout.NORTH);
        } else {
            root.add(title, BorderLayout.NORTH);
        }

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(bg);

        Image previewImg = previewCache.get(entry.filePath);
        if (previewImg != null) {
            JLabel imgLabel = new JLabel();
            imgLabel.setAlignmentX(JComponent.CENTER_ALIGNMENT);
            int targetW = 1100;
            int imgW = previewImg.getWidth(null);
            int imgH = previewImg.getHeight(null);
            if (imgW > 0 && imgH > 0) {
                int targetH = (int) (imgH * ((double) targetW / imgW));
                if (targetH > 280) {
                    targetH = 280;
                    targetW = (int) (imgW * ((double) targetH / imgH));
                }
                BufferedImage bImg = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = bImg.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
                g2.drawImage(previewImg, 0, 0, targetW, targetH, null);
                g2.dispose();
                imgLabel.setIcon(new ImageIcon(bImg));
                
                JPanel imgPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
                imgPanel.setOpaque(false);
                imgPanel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
                imgPanel.setBorder(new EmptyBorder(0, 0, 14, 0));
                imgPanel.add(imgLabel);
                body.add(imgPanel);
                dlg.setSize(1250, 860);
            }
        }

        if (!entry.category.isEmpty()) addRow(body, "Category", entry.category);
        if (!entry.description.isEmpty()) {
            JTextArea desc = new JTextArea(entry.description);
            desc.setFont(new Font("SansSerif", Font.PLAIN, 11));
            desc.setForeground(textSecondary);
            desc.setLineWrap(true);
            desc.setWrapStyleWord(true);
            desc.setEditable(false);
            desc.setFocusable(false);
            desc.setOpaque(false);
            desc.setBorder(null);
            desc.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            
            // Limit maximum width to ensure it wraps correctly within the BoxLayout
            desc.setMaximumSize(new Dimension(1150, Integer.MAX_VALUE));

            body.add(Box.createVerticalStrut(6));
            body.add(desc);
            body.add(Box.createVerticalStrut(10));
        }

        addRow(body, "Required models", String.valueOf(entry.requiredModels.size()));
        addRow(body, "Available locally", String.valueOf(status.presentCount));
        addRow(body, "Missing", String.valueOf(status.missingCount));

        if (entry.requiredModels.isEmpty()) {
            if (!entry.hasBeenValidated) {
                entry.hasBeenValidated = true;
                body.add(Box.createVerticalStrut(12));
                JLabel loadingLbl = new JLabel("⏳ Analyzing workflow JSON for required models...");
                loadingLbl.setFont(new Font("SansSerif", Font.ITALIC, 12));
                loadingLbl.setForeground(textSecondary);
                body.add(loadingLbl);
                if (localModelValidator != null) {
                    localModelValidator.validateWorkflowModelsAsync(entry.registryWorkflow).thenAccept(v -> {
                        SwingUtilities.invokeLater(() -> {
                            entry.requiredModels.clear();
                            if (entry.registryWorkflow.getRequiredModelInfos() != null) {
                                entry.requiredModels.addAll(entry.registryWorkflow.getRequiredModelInfos());
                            }
                            entry.status = computeStatusInternal(entry);
                            dlg.dispose();
                            showDetails(entry, entry.status); // Reopen with data
                        });
                    });
                }
            } else {
                body.add(Box.createVerticalStrut(12));
                JLabel noModelsLbl = new JLabel("✔ No external models required for this workflow.");
                noModelsLbl.setFont(new Font("SansSerif", Font.ITALIC, 12));
                noModelsLbl.setForeground(new Color(34, 197, 130)); // GREEN_READY
                body.add(noModelsLbl);
            }
        } else {
            body.add(Box.createVerticalStrut(12));
            JLabel hdr = new JLabel("Required Models Status:");
            hdr.setFont(new Font("SansSerif", Font.PLAIN, 12));
            hdr.setForeground(textPrimary);
            hdr.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            body.add(hdr);
            body.add(Box.createVerticalStrut(6));

            String[] columns = {"Model Name", "Type", "Size", "Status"};
            javax.swing.table.DefaultTableModel modelTblModel = new javax.swing.table.DefaultTableModel(columns, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };

            for (int i = 0; i < entry.requiredModels.size(); i++) {
                ModelInfo info = entry.requiredModels.get(i);
                String name = info.getName();
                String type = info.getType() != null ? info.getType() : "checkpoints";
                String size = resolveModelSize(info);
                String statusStr = getModelStatus(info);
                modelTblModel.addRow(new Object[]{name, type, size, statusStr});

                if ("Unknown".equalsIgnoreCase(size) && info.getUrl() != null && !info.getUrl().isBlank() && !"MISSING".equals(info.getUrl())) {
                    final int rowIndex = i;
                    final ModelInfo finalInfo = info;
                    CompletableFuture.supplyAsync(() -> {
                        long bytes = modelSearchService.getRemoteSize(finalInfo.getUrl());
                        return modelSearchService.formatSize(bytes);
                    }).thenAccept(resolvedSize -> {
                        if (!"Unknown".equalsIgnoreCase(resolvedSize) && !resolvedSize.contains("Auth")) {
                            finalInfo.setSize(resolvedSize);
                            SwingUtilities.invokeLater(() -> {
                                if (rowIndex < modelTblModel.getRowCount()) {
                                    modelTblModel.setValueAt(resolvedSize, rowIndex, 2);
                                }
                            });
                        }
                    }).exceptionally(ex -> {
                        logger.warn("Failed to fetch remote size for model: " + name, ex);
                        return null;
                    });
                }
            }

            JTable table = new JTable(modelTblModel);
            table.setFont(new Font("SansSerif", Font.PLAIN, 11));
            table.setRowHeight(24);
            table.setShowGrid(false);
            table.setIntercellSpacing(new Dimension(0, 0));
            table.setBackground(bg);
            table.setForeground(textPrimary);
            table.getTableHeader().setBackground(bg);
            table.getTableHeader().setForeground(textSecondary);
            table.getTableHeader().setFont(new Font("SansSerif", Font.PLAIN, 11));
            table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

            // Set column widths so Model Name takes primary weight while Type, Size, and Status stay clear and legible
            table.getColumnModel().getColumn(0).setPreferredWidth(620); // Model Name
            table.getColumnModel().getColumn(0).setMinWidth(350);
            table.getColumnModel().getColumn(1).setPreferredWidth(150); // Type
            table.getColumnModel().getColumn(1).setMinWidth(100);
            table.getColumnModel().getColumn(2).setPreferredWidth(120); // Size
            table.getColumnModel().getColumn(2).setMinWidth(80);
            table.getColumnModel().getColumn(3).setPreferredWidth(280); // Status
            table.getColumnModel().getColumn(3).setMinWidth(180);

            table.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
                @Override
                public java.awt.Component getTableCellRendererComponent(JTable t, Object val, boolean isSel, boolean hasFoc, int row, int col) {
                    java.awt.Component c = super.getTableCellRendererComponent(t, val, isSel, hasFoc, row, col);
                    c.setBackground(row % 2 == 0 ? bg : new Color(Math.max(0, bg.getRed() - 5), Math.max(0, bg.getGreen() - 5), Math.max(0, bg.getBlue() - 5)));
                    c.setForeground(textPrimary);
                    
                    if (col == 3 && val != null) {
                        String st = val.toString();
                        if ("✅ Already exists".equals(st)) {
                            c.setForeground(new Color(0, 180, 160));
                        } else if ("📦 Archived".equals(st)) {
                            c.setForeground(new Color(224, 169, 109));
                        } else if ("🔄 Size Mismatch".equals(st)) {
                            c.setForeground(new Color(226, 88, 88));
                        } else if ("Idle".equals(st)) {
                            c.setForeground(textSecondary);
                        } else {
                            c.setForeground(new Color(0, 150, 186));
                        }
                    }
                    setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
                    return c;
                }
            });

            JScrollPane tblScroll = new JScrollPane(table);
            int calcHeight = Math.min(220, Math.max(80, (entry.requiredModels.size() + 1) * 26 + 6));
            tblScroll.setPreferredSize(new Dimension(1190, calcHeight));
            tblScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, calcHeight));
            tblScroll.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            tblScroll.getViewport().setBackground(bg);
            body.add(tblScroll);
        }

        JScrollPane scroll = new JScrollPane(body,


                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBackground(bg);
        scroll.getViewport().setBackground(bg);
        scroll.setBorder(null);
        root.add(scroll, BorderLayout.CENTER);

        JButton btnClose = new JButton("Close");
        btnClose.addActionListener(e -> dlg.dispose());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        bottom.setBackground(bg);

        // Download Workflow Button
        JButton btnDlWorkflow = new JButton("⬇ Download Workflow");
        btnDlWorkflow.setFont(new Font("SansSerif", Font.BOLD, 11));
        btnDlWorkflow.putClientProperty("Button.arc", 999);
        btnDlWorkflow.setBackground(new Color(0, 150, 136));
        btnDlWorkflow.setForeground(Color.WHITE);
        btnDlWorkflow.addActionListener(e -> {
            btnDlWorkflow.setEnabled(false);
            btnDlWorkflow.setText("Downloading...");
            workflowDownloader.downloadWorkflowAsync(entry.registryWorkflow)
                .thenAccept(file -> SwingUtilities.invokeLater(() -> {
                    btnDlWorkflow.setText("✔ Downloaded");
                    dlg.dispose(); // Close detail dialog
                    
                    Window ownerWin = SwingUtilities.getWindowAncestor(this);
                    if (ownerWin instanceof Main mainApp) {
                        mainApp.importWorkflow(file);
                    }
                }))
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        btnDlWorkflow.setEnabled(true);
                        btnDlWorkflow.setText("⬇ Download Workflow");
                        JOptionPane.showMessageDialog(dlg, 
                            "Failed to download workflow: " + ex.getCause().getMessage(), 
                            "Download Error", JOptionPane.ERROR_MESSAGE);
                    });
                    return null;
                });
        });
        // Send to ComfyUI Button
        JButton btnSendToComfy = new JButton("🚀 Send to ComfyUI");
        btnSendToComfy.setFont(new Font("SansSerif", Font.BOLD, 11));
        btnSendToComfy.putClientProperty("Button.arc", 999);
        btnSendToComfy.setBackground(new Color(33, 150, 243));
        btnSendToComfy.setForeground(Color.WHITE);
        if (status.missingCount > 0) {
            btnSendToComfy.setEnabled(false);
            btnSendToComfy.setToolTipText("Please download all missing models before sending to ComfyUI.");
        }
        btnSendToComfy.addActionListener(evt -> {
            btnSendToComfy.setEnabled(false);
            btnSendToComfy.setText("Downloading...");
            workflowDownloader.downloadWorkflowAsync(entry.registryWorkflow)
                .thenAccept(file -> {
                    try {
                        if (lifecycleService != null && !lifecycleService.isHealthy()) {
                            SwingUtilities.invokeLater(() -> btnSendToComfy.setText("Starting ComfyUI..."));
                            logger.info("🔌 [BlueprintGallery] ComfyUI server is offline. Starting lifecycle service...");
                            lifecycleService.start();
                            int maxWait = 90;
                            boolean healthy = false;
                            for (int w = 0; w < maxWait; w++) {
                                if (lifecycleService.isHealthy()) {
                                    healthy = true;
                                    break;
                                }
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                            if (!healthy) {
                                throw new Exception("ComfyUI server could not be started or is not reachable at: " + configService.getComfyUIUrl());
                            }
                        }

                        SwingUtilities.invokeLater(() -> btnSendToComfy.setText("Sending..."));
                        String fileContent = Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8).trim();
                        JSONObject json = new JSONObject(fileContent);
                        
                        String comfyUrl = configService.getComfyUIUrl();
                        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                                .connectTimeout(java.time.Duration.ofSeconds(15))
                                .build();
                                
                        java.net.http.HttpRequest request;
                        boolean isUIFormat = json.has("nodes") || (json.has("workflow") && new JSONObject(json.get("workflow").toString()).has("nodes"));
                        
                        if (isUIFormat) {
                            // UI format: Send to Bridge so it loads visually on the ComfyUI canvas!
                            JSONObject payload = new JSONObject().put("workflow", json);
                            request = java.net.http.HttpRequest.newBuilder()
                                    .uri(java.net.URI.create(comfyUrl + "/cmfc/load-workflow"))
                                    .header("Content-Type", "application/json")
                                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload.toString()))
                                    .build();
                        } else {
                            // API format: Queue directly
                            JSONObject promptObj = json.has("prompt") ? json.getJSONObject("prompt") : json;
                            JSONObject payload = new JSONObject().put("prompt", promptObj);
                            request = java.net.http.HttpRequest.newBuilder()
                                    .uri(java.net.URI.create(comfyUrl + "/prompt"))
                                    .header("Content-Type", "application/json")
                                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload.toString()))
                                    .build();
                        }
                        
                        java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                        
                        SwingUtilities.invokeLater(() -> {
                            btnSendToComfy.setEnabled(true);
                            btnSendToComfy.setText("🚀 Send to ComfyUI");
                            if (response.statusCode() == 200) {
                                if (isUIFormat) {
                                    JOptionPane.showMessageDialog(dlg,
                                        "Workflow successfully sent and loaded into your ComfyUI browser tab!",
                                        "Success", JOptionPane.INFORMATION_MESSAGE);
                                } else {
                                    JOptionPane.showMessageDialog(dlg,
                                        "Workflow successfully sent to ComfyUI and queued!",
                                        "Success", JOptionPane.INFORMATION_MESSAGE);
                                }
                                dlg.dispose();
                            } else {
                                if (isUIFormat && response.statusCode() == 404) {
                                    JOptionPane.showMessageDialog(dlg,
                                        "ComfyUI Bridge is not installed or outdated.\n" +
                                        "Please install/update the ComfyUI Bridge in Settings -> Install ComfyUI Bridge.",
                                        "Bridge Not Found", JOptionPane.WARNING_MESSAGE);
                                } else {
                                    JOptionPane.showMessageDialog(dlg,
                                        "Failed to send to ComfyUI.\nStatus: " + response.statusCode() + "\nResponse: " + response.body(),
                                        "Error", JOptionPane.ERROR_MESSAGE);
                                }
                            }
                        });
                    } catch (Exception ex) {
                        String msg = ex.getMessage();
                        if (ex instanceof java.net.ConnectException || (ex.getCause() != null && ex.getCause() instanceof java.net.ConnectException)) {
                            msg = "Could not connect to ComfyUI. Please make sure the ComfyUI server is running and reachable at: " + configService.getComfyUIUrl();
                        }
                        final String finalMsg = msg;
                        SwingUtilities.invokeLater(() -> {
                            btnSendToComfy.setEnabled(true);
                            btnSendToComfy.setText("🚀 Send to ComfyUI");
                            JOptionPane.showMessageDialog(dlg,
                                finalMsg,
                                "Connection Error", JOptionPane.ERROR_MESSAGE);
                        });
                    }
                })
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        btnSendToComfy.setEnabled(true);
                        btnSendToComfy.setText("🚀 Send to ComfyUI");
                        JOptionPane.showMessageDialog(dlg,
                            "Failed to download workflow: " + ex.getCause().getMessage(),
                            "Download Error", JOptionPane.ERROR_MESSAGE);
                    });
                    return null;
                });
        });

        bottom.add(btnDlWorkflow);
        bottom.add(btnSendToComfy);
        bottom.add(btnClose);
        root.add(bottom, BorderLayout.SOUTH);

        dlg.setContentPane(root);
        dlg.setVisible(true);
    }

    /**
     * Downloads the entry preview (if remote) into a temp or cache file and opens
     * a self-contained video player dialog in the foreground on top of the parent window.
     */
    private void playVideoPreview(BlueprintEntry entry) {
        playVideoPreview(entry, null, null);
    }

    private void playVideoPreview(BlueprintEntry entry, Window parent, JButton triggerBtn) {
        String url = entry.previewPath;
        if (url == null || url.isEmpty()) return;

        if (triggerBtn != null) {
            triggerBtn.setEnabled(false);
            triggerBtn.setText("⏳ Loading Preview...");
        }

        Window actualOwner = parent != null ? parent : SwingUtilities.getWindowAncestor(this);

        CompletableFuture.supplyAsync(() -> {
            try {
                String ext = ".mp4";
                String lower = url.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".webm")) ext = ".webm";
                else if (lower.endsWith(".mov")) ext = ".mov";

                File cacheDir = getPreviewDiskCacheDir();
                File cachedFile = new File(cacheDir, hashUrl(url) + ext);

                if (cachedFile.exists() && cachedFile.length() > 0) {
                    return cachedFile;
                }

                // Download to a temp file or cache file so the player can stream from a local path.
                String nonce = java.util.UUID.randomUUID().toString().replaceAll("[0-9-]", "x");
                File tmp = new File(System.getProperty("java.io.tmpdir"), "blueprint_video_" + nonce + ext);
                tmp.deleteOnExit();

                try (java.io.InputStream is = new java.net.URL(url).openStream()) {
                    java.nio.file.Files.copy(is, tmp.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }

                // Also try saving to disk cache if possible
                try {
                    if (!cachedFile.exists()) {
                        java.nio.file.Files.copy(tmp.toPath(), cachedFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        return cachedFile;
                    }
                } catch (Exception ignored) {}

                return tmp;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).thenAccept(videoFile -> SwingUtilities.invokeLater(() -> {
            if (triggerBtn != null) {
                triggerBtn.setEnabled(true);
                triggerBtn.setText("▶  Play Preview");
            }
            VideoPreviewDialog previewDialog = new VideoPreviewDialog(
                    actualOwner,
                    entry.name,
                    videoFile,
                    configService.isDarkMode()
            );
            previewDialog.setLocationRelativeTo(actualOwner);
            previewDialog.setVisible(true);
            previewDialog.toFront();
            previewDialog.requestFocus();
        })).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> {
                if (triggerBtn != null) {
                    triggerBtn.setEnabled(true);
                    triggerBtn.setText("▶  Play Preview");
                }
                JOptionPane.showMessageDialog(
                        actualOwner != null ? actualOwner : this,
                        "Could not open video preview: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()),
                        "Video Preview Error",
                        JOptionPane.ERROR_MESSAGE
                );
            });
            return null;
        });
    }

    private void addRow(JPanel parent, String label, String value) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

        JLabel lbl = new JLabel(label + ":");
        lbl.setFont(new Font("SansSerif", Font.BOLD, 11));
        lbl.setForeground(textSecondary);
        lbl.setPreferredSize(new Dimension(120, 18));

        JLabel val = new JLabel(value);
        val.setFont(new Font("SansSerif", Font.PLAIN, 11));
        val.setForeground(textPrimary);

        row.add(lbl, BorderLayout.WEST);
        row.add(val, BorderLayout.CENTER);
        parent.add(row);
        parent.add(Box.createVerticalStrut(3));
    }

    // ── HELPERS ──────────────────────────────────────────────────────────────

    private static List<String> getAlternativeFolders(String folder) {
        List<String> list = new ArrayList<>();
        if (folder == null) return list;
        list.add(folder);
        
        String lower = folder.toLowerCase(Locale.ROOT);
        if (lower.equals("text_encoders") || lower.equals("clip")) {
            if (!lower.equals("text_encoders")) list.add("text_encoders");
            if (!lower.equals("clip")) list.add("clip");
        } else if (lower.equals("diffusion_models") || lower.equals("unet")) {
            if (!lower.equals("diffusion_models")) list.add("diffusion_models");
            if (!lower.equals("unet")) list.add("unet");
        } else if (lower.equals("loras") || lower.equals("lora")) {
            if (!lower.equals("loras")) list.add("loras");
            if (!lower.equals("lora")) list.add("lora");
        }
        return list;
    }

    private String resolveModelSize(ModelInfo info) {
        if (info == null) return "Unknown";
        if (info.getSize() != null && !info.getSize().isBlank() && !"Unknown".equalsIgnoreCase(info.getSize())) {
            return info.getSize();
        }

        String filename = info.getName();
        if (filename == null || filename.isBlank()) return "Unknown";

        String base = configService != null ? configService.getModelsPath() : null;
        String archive = configService != null ? configService.getArchivePath() : null;
        String type = info.getType() != null ? info.getType() : "checkpoints";
        String folder = info.getSave_path() != null ? info.getSave_path() : type;
        String normalizedFolder = archiveService != null ? archiveService.normalizeFolder(folder) : folder;

        if (base != null && !base.isBlank()) {
            java.nio.file.Path p = "root".equals(normalizedFolder)
                    ? java.nio.file.Paths.get(base, filename)
                    : java.nio.file.Paths.get(base, normalizedFolder, filename);
            if (java.nio.file.Files.exists(p) && java.nio.file.Files.isRegularFile(p)) {
                try {
                    return formatByteSize(java.nio.file.Files.size(p));
                } catch (Exception ignored) {}
            }
        }

        if (archive != null && !archive.isBlank()) {
            java.nio.file.Path p = "root".equals(normalizedFolder)
                    ? java.nio.file.Paths.get(archive, filename)
                    : java.nio.file.Paths.get(archive, normalizedFolder, filename);
            if (java.nio.file.Files.exists(p) && java.nio.file.Files.isRegularFile(p)) {
                try {
                    return formatByteSize(java.nio.file.Files.size(p));
                } catch (Exception ignored) {}
            }
        }

        if (localModelScanner != null) {
            try {
                java.util.Optional<java.nio.file.Path> cached = localModelScanner.findModelFromCache(filename);
                if (cached.isPresent()) {
                    return formatByteSize(java.nio.file.Files.size(cached.get()));
                }
            } catch (Exception ignored) {}
        }

        return "Unknown";
    }

    private String formatByteSize(long bytes) {
        if (bytes <= 0) return "Unknown";
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 1024.0) {
            return String.format(java.util.Locale.US, "%.2fGB", mb / 1024.0);
        } else {
            return String.format(java.util.Locale.US, "%.0fMB", mb);
        }
    }

    private String getModelStatus(ModelInfo info) {
        if (info == null || info.getName() == null || info.getName().isBlank()) return "Idle";
        String reqName = info.getName().trim();
        String lowerName = reqName.toLowerCase(Locale.ROOT);

        // 1. Fast in-memory check via LocalModelValidator
        if (localModelValidator != null) {
            if (localModelValidator.isModelActive(reqName)) {
                return "✅ Already exists";
            }
            if (localModelValidator.isModelArchived(reqName)) {
                return "📦 Archived";
            }
            Set<String> activeNames = localModelValidator.getActiveLocalModelNames();
            if (activeNames != null && (activeNames.contains(lowerName) || activeNames.contains(baseName(lowerName)))) {
                return "✅ Already exists";
            }
        }

        // 2. Closed source / Cloud API-based services that do not require local model files
        if (lowerName.equals("openai") || lowerName.equals("dall-e") || lowerName.equals("dalle") ||
            lowerName.equals("elevenlabs") || lowerName.equals("gemini") || lowerName.equals("anthropic") ||
            lowerName.equals("claude") || lowerName.equals("openrouter")) {
            return "✅ Already exists";
        }

        // 3. Fast in-memory lookup in LocalModelScanner
        if (localModelScanner != null) {
            Optional<java.nio.file.Path> cached = localModelScanner.findModelFromCache(reqName);
            if (cached.isPresent()) {
                return "✅ Already exists";
            }
        }

        // 4. Direct single-path file checks (without recursive tree walk)
        String base = configService.getModelsPath();
        String archive = configService.getArchivePath();
        if (base == null || base.isEmpty()) return "Idle";

        String type = info.getType() != null ? info.getType() : "checkpoints";
        String folder = info.getSave_path() != null ? info.getSave_path() : type;
        String normalizedFolder = archiveService.normalizeFolder(folder);

        java.nio.file.Path local = "root".equals(normalizedFolder) ? java.nio.file.Paths.get(base, reqName) : java.nio.file.Paths.get(base, normalizedFolder, reqName);
        if (java.nio.file.Files.exists(local) && java.nio.file.Files.isRegularFile(local)) {
            return "✅ Already exists";
        }

        if (archive != null && !archive.trim().isEmpty()) {
            java.nio.file.Path archivedPath = "root".equals(normalizedFolder) ? java.nio.file.Paths.get(archive, reqName) : java.nio.file.Paths.get(archive, normalizedFolder, reqName);
            if (java.nio.file.Files.exists(archivedPath) && java.nio.file.Files.isRegularFile(archivedPath)) {
                return "📦 Archived";
            }
        }

        if (info.getUrl() == null || info.getUrl().equals("MISSING")) {
            return "Idle";
        } else {
            return "✅ Known Good";
        }
    }

    private boolean isModelPresentDeep(ModelInfo req) {
        String status = getModelStatus(req);
        return status.equals("✅ Already exists");
    }


    private BlueprintStatus computeStatusInternal(BlueprintEntry entry) {
        int present = 0, missing = 0;
        List<String> missingNames = new ArrayList<>();

        for (ModelInfo req : entry.requiredModels) {
            if (req.getName() == null || req.getName().isBlank()) continue;
            if (isModelPresentDeep(req)) {
                present++;
            } else {
                missing++;
                missingNames.add(req.getName());
            }
        }
        return new BlueprintStatus(present, missing, missingNames);
    }

    private BlueprintStatus computeStatus(BlueprintEntry entry) {
        if (entry.status == null) {
            entry.status = computeStatusInternal(entry);
        }
        return entry.status;
    }

    private Color[] resolvePalette(BlueprintEntry entry) {
        String combined = (entry.category + " " + entry.name).toLowerCase(Locale.ROOT);
        for (String[] row : CAT_PALETTES) {
            if (combined.contains(row[0])) {
                return new Color[]{ hex(row[1]), hex(row[2]), hex(row[3]) };
            }
        }
        return new Color[]{ hex(DEFAULT_PALETTE[0]), hex(DEFAULT_PALETTE[1]), hex(DEFAULT_PALETTE[2]) };
    }

    private static Color hex(String h) {
        return Color.decode(h);
    }

    private static String categoryIcon(String category) {
        String c = category.toLowerCase(Locale.ROOT);
        if (c.contains("text to image"))     return "🖼";
        if (c.contains("text to video"))     return "🎬";
        if (c.contains("image to video"))    return "📽";
        if (c.contains("image edit"))        return "✏️";
        if (c.contains("inpaint"))           return "🎨";
        if (c.contains("outpaint"))          return "📐";
        if (c.contains("depth"))             return "🗺";
        if (c.contains("controlnet"))        return "🕹";
        if (c.contains("pose"))              return "🕴";
        if (c.contains("upscal"))            return "🔍";
        if (c.contains("segmentation"))      return "✂";
        if (c.contains("caption"))           return "💬";
        if (c.contains("background"))        return "🌄";
        if (c.contains("interpolat"))        return "⏩";
        if (c.contains("3d") || c.contains("model")) return "🎲";
        if (c.contains("audio"))             return "🎵";
        if (c.contains("video"))             return "🎞";
        if (c.contains("color"))             return "🌈";
        if (c.contains("blur"))              return "💧";
        if (c.contains("grain"))             return "🌾";
        if (c.contains("glow"))              return "✨";
        if (c.contains("sharpen"))           return "🔪";
        if (c.contains("brightness"))        return "☀";
        return "⚙";
    }

    private static String baseName(String nameOrPath) {
        if (nameOrPath == null) return "";
        String s = nameOrPath.replace('\\', '/');
        int slash = s.lastIndexOf('/');
        if (slash >= 0) s = s.substring(slash + 1);
        int dot = s.lastIndexOf('.');
        if (dot >= 0) s = s.substring(0, dot);
        return s.toLowerCase(Locale.ROOT).trim();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String inferTypeFromFilename(String filename) {
        if (filename == null) return "checkpoints";
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.contains("vae") || lower.contains("ae.safetensors") || lower.startsWith("ae")) return "vae";
        if (lower.contains("clip") || lower.contains("t5") || lower.contains("qwen") || lower.contains("encoder") || lower.contains("gemma") || lower.contains("llama") || lower.contains("text_encoder") || lower.contains("textencoder")) return "text_encoders";
        if (lower.contains("unet") || lower.contains("diffusion") || lower.contains("turbo") || lower.contains("transformer") || lower.contains("dit")) return "diffusion_models";
        if (lower.contains("lora")) return "loras";
        if (lower.contains("controlnet") || lower.contains("control")) return "controlnet";
        return "checkpoints";
    }

    private static boolean isSupportedModelFile(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".safetensors") || lower.endsWith(".sft") || lower.endsWith(".ckpt") ||
               lower.endsWith(".pt") || lower.endsWith(".pth") || lower.endsWith(".bin") || lower.endsWith(".onnx");
    }

    private static boolean hasActualModelFiles(List<ModelInfo> models) {
        if (models == null || models.isEmpty()) return false;
        for (ModelInfo m : models) {
            if (isSupportedModelFile(m.getName())) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<ModelInfo> extractModelInfos(Object obj, ObjectMapper mapper) {
        List<ModelInfo> result = new ArrayList<>();
        if (obj instanceof List) {
            for (Object item : (List<?>) obj) {
                if (item instanceof ModelInfo) {
                    result.add((ModelInfo) item);
                } else if (item instanceof Map) {
                    try {
                        ModelInfo info = mapper.convertValue(item, ModelInfo.class);
                        result.add(info);
                    } catch (Exception ignored) {}
                }
            }
        }
        return result;
    }

    public void updateTheme(boolean darkMode) {
        if (darkMode) {
            bgPage          = new Color(15, 16, 21);
            cardBg          = new Color(24, 26, 34);
            cardBorder      = new Color(48, 52, 68);
            cardHoverBorder = new Color(0, 210, 190);
            textPrimary     = new Color(228, 230, 240);
            textSecondary   = new Color(120, 126, 152);
            textDim         = new Color(80, 86, 110);
        } else {
            bgPage          = new Color(245, 247, 250);
            cardBg          = new Color(255, 255, 255);
            cardBorder      = new Color(210, 215, 225);
            cardHoverBorder = new Color(0, 150, 180);
            textPrimary     = new Color(30, 35, 45);
            textSecondary   = new Color(90, 100, 110);
            textDim         = new Color(140, 145, 160);
        }
        
        setBackground(bgPage);
        if (northPanel != null) {
            northPanel.setBackground(bgPage);
        }
        if (galleryWrapper != null) {
            galleryWrapper.setBackground(bgPage);
        }
        if (scroll != null) {
            scroll.setBackground(bgPage);
            scroll.getViewport().setBackground(bgPage);
        }

        if (lblTitle != null) {
            lblTitle.setForeground(textPrimary);
        }
        if (lblSummary != null) {
            lblSummary.setForeground(textSecondary);
        }
        if (lblStatus != null) {
            lblStatus.setForeground(textSecondary);
        }
        if (chkReadyOnly != null) {
            chkReadyOnly.setDarkMode(darkMode);
        }
        if (chkHideCloud != null) {
            chkHideCloud.setDarkMode(darkMode);
        }
        
        applyFilter();
        repaint();
    }

    // ── DATA CLASSES ──────────────────────────────────────────────────────────

    public java.util.List<BlueprintEntry> getAvailableBlueprints() {
        java.util.List<BlueprintEntry> list = new java.util.ArrayList<>();
        for (BlueprintEntry e : allEntries) {
            BlueprintStatus s = e.status;
            if (s != null && s.missingCount == 0) {
                if (e.registryWorkflow != null && e.registryWorkflow.isCloudOnly()) {
                    continue;
                }
                list.add(e);
            }
        }
        list.sort((e1, e2) -> {
            int r1 = getBlueprintMediaRank(e1);
            int r2 = getBlueprintMediaRank(e2);
            if (r1 != r2) return Integer.compare(r1, r2);
            int o1 = getCategoryOrderScore(e1.category);
            int o2 = getCategoryOrderScore(e2.category);
            if (o1 != o2) return Integer.compare(o1, o2);
            double pop1 = e1.registryWorkflow != null ? e1.registryWorkflow.getPopularScore() : 0.0;
            double pop2 = e2.registryWorkflow != null ? e2.registryWorkflow.getPopularScore() : 0.0;
            int popCmp = Double.compare(pop2, pop1);
            if (popCmp != 0) return popCmp;
            String n1 = e1.name != null ? e1.name : "";
            String n2 = e2.name != null ? e2.name : "";
            return n1.compareToIgnoreCase(n2);
        });
        return list;
    }

    /**
     * A self-painting checkbox that renders its own box and checkmark using Java2D,
     * completely bypassing FlatLaf's LAF checkbox icon. This guarantees correct
     * appearance in both dark and light themes without any putClientProperty hacks.
     */
    static class ThemedCheckBox extends JToggleButton {
        private boolean darkMode;

        // Colors
        private static final Color DARK_BOX_BG        = new Color(0x1C202B);
        private static final Color DARK_BOX_BORDER     = new Color(0x8E98B0);
        private static final Color DARK_HOVER_BG       = new Color(0x262B3A);
        private static final Color DARK_HOVER_BORDER   = new Color(0x00D2BE);
        private static final Color DARK_CHECKED_BG     = new Color(0x009688);
        private static final Color DARK_CHECKED_BORDER = new Color(0x00D2BE);

        private static final Color LIGHT_BOX_BG        = Color.WHITE;
        private static final Color LIGHT_BOX_BORDER     = new Color(0x6B7280);
        private static final Color LIGHT_HOVER_BG       = new Color(0xF3F4F6);
        private static final Color LIGHT_HOVER_BORDER   = new Color(0x009688);
        private static final Color LIGHT_CHECKED_BG     = new Color(0x009688);
        private static final Color LIGHT_CHECKED_BORDER = new Color(0x00796B);

        private static final Color CHECK_COLOR = Color.WHITE;
        private static final int   BOX_SIZE    = 14;
        private static final int   ARC         = 4;
        private static final int   GAP         = 6;

        private boolean hovered = false;

        ThemedCheckBox(String text, boolean darkMode) {
            super(text);
            this.darkMode = darkMode;
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFont(new Font("SansSerif", Font.BOLD, 11));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            applyColors();

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovered = true;  repaint(); }
                @Override public void mouseExited (MouseEvent e) { hovered = false; repaint(); }
            });
        }

        void setDarkMode(boolean dark) {
            this.darkMode = dark;
            applyColors();
            repaint();
        }

        private void applyColors() {
            setForeground(darkMode ? new Color(0xE2E8F0) : new Color(0x1F2937));
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            int textW = fm.stringWidth(getText());
            int textH = fm.getAscent() + fm.getDescent();
            int h = Math.max(BOX_SIZE, textH) + 6;
            return new Dimension(BOX_SIZE + GAP + textW + 4, h);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

            int h  = getHeight();
            int bY = (h - BOX_SIZE) / 2;

            // ── Box fill ───────────────────────────────────────────────────────
            Color boxBg;
            Color boxBorder;
            if (isSelected()) {
                boxBg     = darkMode ? DARK_CHECKED_BG     : LIGHT_CHECKED_BG;
                boxBorder = darkMode ? DARK_CHECKED_BORDER : LIGHT_CHECKED_BORDER;
            } else if (hovered) {
                boxBg     = darkMode ? DARK_HOVER_BG     : LIGHT_HOVER_BG;
                boxBorder = darkMode ? DARK_HOVER_BORDER : LIGHT_HOVER_BORDER;
            } else {
                boxBg     = darkMode ? DARK_BOX_BG     : LIGHT_BOX_BG;
                boxBorder = darkMode ? DARK_BOX_BORDER : LIGHT_BOX_BORDER;
            }

            RoundRectangle2D box = new RoundRectangle2D.Float(1, bY, BOX_SIZE - 2, BOX_SIZE - 2, ARC, ARC);
            g2.setColor(boxBg);
            g2.fill(box);
            g2.setColor(boxBorder);
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(box);

            // ── Checkmark ──────────────────────────────────────────────────────
            if (isSelected()) {
                g2.setColor(CHECK_COLOR);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int x0 = 3, y0 = bY + BOX_SIZE / 2;
                int x1 = 3 + (BOX_SIZE - 6) / 3, y1 = bY + BOX_SIZE - 4;
                int x2 = BOX_SIZE - 3, y2 = bY + 4;
                g2.drawLine(x0, y0, x1, y1);
                g2.drawLine(x1, y1, x2, y2);
            }

            // ── Label ──────────────────────────────────────────────────────────
            g2.setColor(getForeground());
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int textX = BOX_SIZE + GAP;
            int textY = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(getText(), textX, textY);

            g2.dispose();
        }
    }

    public static class BlueprintEntry {
        public final String name;
        public final String filename;
        public final String filePath;
        public final String category;
        public final String description;
        public final List<ModelInfo> requiredModels;
        public final ComfyRegistryWorkflow registryWorkflow;
        public String previewPath = "";
        public BlueprintStatus status = null;
        public boolean hasBeenValidated = false;

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
                for (String mName : workflow.getRequiredModels()) {
                    if (isSupportedModelFile(mName)) {
                        ModelInfo info = new ModelInfo();
                        info.setName(mName);
                        info.setType(inferTypeFromFilename(mName));
                        this.requiredModels.add(info);
                    }
                }
            }
        }
        
        @Override
        public String toString() {
            return this.name;
        }
    }

    public static class BlueprintStatus {
        public final int presentCount;
        public final int missingCount;
        public final List<String> missingNames;

        public BlueprintStatus(int presentCount, int missingCount, List<String> missingNames) {
            this.presentCount = presentCount;
            this.missingCount = missingCount;
            this.missingNames = missingNames;
        }
    }

    private static class ScrollablePanel extends JPanel implements Scrollable {
        public ScrollablePanel(LayoutManager layout) {
            super(layout);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 20;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 60;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
