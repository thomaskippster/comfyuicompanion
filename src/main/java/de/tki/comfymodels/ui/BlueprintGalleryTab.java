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

import io.metaloom.video4j.Video4j;
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
    private final ObjectMapper mapper = new ObjectMapper();

    // ── ui ────────────────────────────────────────────────────────────────────
    private JPanel galleryWrapper;   // holds the GridLayout gallery
    private JLabel lblSummary;
    private JLabel lblStatus;
    private JTextField txtSearch;
    private JComboBox<String> cbCategory;
    private JCheckBox chkReadyOnly;
    private JCheckBox chkHideCloud;
    private JLabel lblTitle;
    private JScrollPane scroll;

    private static final ExecutorService imageLoadExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "BlueprintPreviewLoader");
        t.setDaemon(true);
        return t;
    });

    // ── data ──────────────────────────────────────────────────────────────────
    private final List<BlueprintEntry> allEntries = new CopyOnWriteArrayList<>();
    private final javax.swing.Timer filterDebounceTimer = new javax.swing.Timer(250, e -> {
        applyFilter();
        updateSummary();
    });
    private final List<JPanel> categoryGridPanels = new CopyOnWriteArrayList<>();
    private final Map<String, Image> previewCache = new ConcurrentHashMap<>();
    private final Map<String, Image> scaledPreviewCache = new ConcurrentHashMap<>();
    private final Set<String> loadingPaths = ConcurrentHashMap.newKeySet();
    private boolean hasScanned = false;
    private boolean initialLoadComplete = false;
    private boolean hasSetInitialDefaultCategory = false;
    private final Set<String> failedPaths = ConcurrentHashMap.newKeySet();
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
    private static final Color AMBER_PARTIAL   = new Color(245, 158, 11);
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
        this.processTracker = processTracker;
        this.filterDebounceTimer.setRepeats(false);

        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(bgPage);

        buildUI();
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
        JPanel topBar = new JPanel(new BorderLayout(12, 0));
        topBar.setOpaque(false);
        topBar.setBorder(new EmptyBorder(16, 22, 10, 22));

        // Left: title + summary
        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.setOpaque(false);

        lblTitle = new JLabel("Blueprint Gallery");
        lblTitle.setFont(new Font("SansSerif", Font.BOLD, 22));
        lblTitle.setForeground(textPrimary);

        lblSummary = new JLabel(" ");
        lblSummary.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lblSummary.setForeground(textSecondary);

        titleBlock.add(lblTitle);
        titleBlock.add(Box.createVerticalStrut(2));
        titleBlock.add(lblSummary);
        topBar.add(titleBlock, BorderLayout.WEST);

        // Right: controls (Search + Sort + Filter + Refresh)
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        controls.setOpaque(false);

        txtSearch = new JTextField(12);
        txtSearch.putClientProperty("JTextField.placeholderText", "Search blueprints…");
        txtSearch.setFont(new Font("SansSerif", Font.PLAIN, 12));
        txtSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { SwingUtilities.invokeLater(() -> applyFilter()); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { SwingUtilities.invokeLater(() -> applyFilter()); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { SwingUtilities.invokeLater(() -> applyFilter()); }
        });

        cbCategory = new JComboBox<>(new String[]{"All Categories"});
        cbCategory.setFont(new Font("SansSerif", Font.PLAIN, 12));
        cbCategory.addActionListener(e -> applyFilter());

        chkReadyOnly = new JCheckBox("Only workflows with no missing models");
        chkReadyOnly.setFont(new Font("SansSerif", Font.BOLD, 12));
        chkReadyOnly.setForeground(textSecondary);
        chkReadyOnly.setOpaque(false);
        chkReadyOnly.addActionListener(e -> {
            chkReadyOnly.revalidate();
            chkReadyOnly.repaint();
            applyFilter();
        });

        chkHideCloud = new JCheckBox("Hide cloud-only workflows");
        chkHideCloud.setFont(new Font("SansSerif", Font.BOLD, 12));
        chkHideCloud.setForeground(textSecondary);
        chkHideCloud.setOpaque(false);
        chkHideCloud.setSelected(true);
        chkHideCloud.addActionListener(e -> {
            chkHideCloud.revalidate();
            chkHideCloud.repaint();
            applyFilter();
        });

        JButton btnRefresh = new JButton("🔄  Refresh");
        btnRefresh.setFont(new Font("SansSerif", Font.PLAIN, 12));
        btnRefresh.putClientProperty("Button.arc", 999);
        btnRefresh.addActionListener(e -> refreshAllData(true));

        controls.add(txtSearch);
        controls.add(cbCategory);
        controls.add(chkReadyOnly);
        controls.add(chkHideCloud);
        controls.add(btnRefresh);
        topBar.add(controls, BorderLayout.EAST);

        // Status line
        lblStatus = new JLabel(" ");
        lblStatus.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lblStatus.setForeground(textSecondary);
        lblStatus.setBorder(new EmptyBorder(0, 22, 8, 22));

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.setOpaque(false);
        northPanel.add(topBar,    BorderLayout.NORTH);
        northPanel.add(lblStatus, BorderLayout.SOUTH);
        add(northPanel, BorderLayout.NORTH);

        // ── GALLERY AREA ─────────────────────────────────────────────────────
        galleryWrapper = new ScrollablePanel(null);
        galleryWrapper.setOpaque(false);
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
        scroll.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
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
        hasScanned = true;
        lblStatus.setText("⏳  Fetching workflows and checking local models…");
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

        new Thread(() -> {
            try {
                // 1. Scan local models
                localModelValidator.scanLocalModels();

                // 2. Fetch cloud workflows from Comfy.org API
                List<ComfyRegistryWorkflow> workflows = registryClient.fetchWorkflowsAsync().get();

                // 3. Map to internal entries (skipping full JSON parsing of all entries on startup)
                allEntries.clear();
                List<Map<String, Object>> scanResults = Collections.emptyList();
                if (modelArchitectureService != null) {
                    try {
                        List<Map<String, Object>> res = modelArchitectureService.getBlueprintScanResults();
                        if (res != null) {
                            scanResults = res;
                        }
                    } catch (Exception ignored) {}
                }

                for (ComfyRegistryWorkflow wf : workflows) {
                    BlueprintEntry entry = new BlueprintEntry(wf);
                    
                    // Match with scanned results from ModelArchitectureService
                    Map<String, Object> matchedScan = null;
                    String wfId = wf.getId();
                    String wfTitle = wf.getTitle();
                    for (Map<String, Object> scan : scanResults) {
                        String scanFilename = (String) scan.get("filename");
                        String scanName = (String) scan.get("name");
                        
                        // Check match by title
                        if (scanName != null && wfTitle != null && scanName.equalsIgnoreCase(wfTitle)) {
                            matchedScan = scan;
                            break;
                        }
                        // Check match by ID / filename
                        if (scanFilename != null && wfId != null && 
                            (scanFilename.equalsIgnoreCase(wfId + ".json") || scanFilename.equalsIgnoreCase(wfId))) {
                            matchedScan = scan;
                            break;
                        }
                        if (scanName != null && wfId != null && scanName.equalsIgnoreCase(wfId)) {
                            matchedScan = scan;
                            break;
                        }
                    }

                    if (matchedScan != null) {
                        Object reqObj = matchedScan.get("requiredModels");
                        List<ModelInfo> resolved = extractModelInfos(reqObj, mapper);
                        if (!resolved.isEmpty()) {
                            entry.requiredModels.clear();
                            entry.requiredModels.addAll(resolved);
                        }
                    } else if (wf.getRequiredModelInfos() != null && !wf.getRequiredModelInfos().isEmpty()) {
                        entry.requiredModels.clear();
                        entry.requiredModels.addAll(wf.getRequiredModelInfos());
                    } else if (wf.getRequiredModels() != null && !wf.getRequiredModels().isEmpty()) {
                        // Populate entry.requiredModels with high-level models from index.json directly
                        for (String mName : wf.getRequiredModels()) {
                            ModelInfo info = new ModelInfo();
                            info.setName(mName);
                            info.setType(inferTypeFromFilename(mName));
                            entry.requiredModels.add(info);
                        }
                    }

                    entry.status = computeStatusInternal(entry);
                    allEntries.add(entry);

                    // If empty requiredModels OR no actual model files (high-level fallback), download/validate async in background
                    boolean needDetailedValidation = entry.requiredModels.isEmpty() || !hasActualModelFiles(entry.requiredModels);
                    if (needDetailedValidation && wf.getJsonDownloadUrl() != null) {
                        localModelValidator.validateWorkflowModelsAsync(wf).thenRun(() -> {
                            entry.requiredModels.clear();
                            if (wf.getRequiredModelInfos() != null && !wf.getRequiredModelInfos().isEmpty()) {
                                entry.requiredModels.addAll(wf.getRequiredModelInfos());
                            } else if (wf.getRequiredModels() != null) {
                                for (String mName : wf.getRequiredModels()) {
                                    ModelInfo info = new ModelInfo();
                                    info.setName(mName);
                                    info.setType(inferTypeFromFilename(mName));
                                    entry.requiredModels.add(info);
                                }
                            }
                            entry.status = computeStatusInternal(entry);
                            SwingUtilities.invokeLater(() -> {
                                filterDebounceTimer.restart();
                            });
                        });
                    }
                }

                SwingUtilities.invokeLater(() -> {
                    updateCategoryComboBox();
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
                logger.error("❌ [BlueprintGallery] Refresh failed: " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("❌ Failed to fetch registry data: " + e.getMessage());
                });
            }
        }, "BlueprintGalleryRefresh").start();
    }

    private void updateStatusAsync() {
        new Thread(() -> {
            try {
                localModelValidator.scanLocalModels();
                for (BlueprintEntry entry : allEntries) {
                    entry.status = computeStatusInternal(entry);
                }
                SwingUtilities.invokeLater(() -> {
                    applyFilter();
                });
            } catch (Exception e) {
                logger.error("❌ [BlueprintGallery] Status update failed: " + e.getMessage());
            }
        }, "BlueprintGalleryStatusUpdate").start();
    }

    // ── GALLERY RENDERING ─────────────────────────────────────────────────────

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

        Set<String> categories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (BlueprintEntry entry : allEntries) {
            String cat = entry.category != null && !entry.category.isEmpty() ? entry.category : "General";
            categories.add(cat);
        }

        for (String cat : categories) {
            cbCategory.addItem(cat);
        }

        if (!hasSetInitialDefaultCategory) {
            String imageCat = null;
            for (String cat : categories) {
                if ("Image".equalsIgnoreCase(cat)) {
                    imageCat = cat;
                    break;
                }
            }
            if (imageCat != null) {
                selected = imageCat;
                hasSetInitialDefaultCategory = true;
            }
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

        scaledPreviewCache.clear();
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

        // Sort implementation: Runnable workflows first, then by popularity/popularScore
        visible.sort((e1, e2) -> {
            boolean run1 = computeStatus(e1).missingCount == 0;
            boolean run2 = computeStatus(e2).missingCount == 0;
            if (run1 != run2) {
                return run1 ? -1 : 1;
            }
            return Double.compare(e2.registryWorkflow.getPopularScore(), e1.registryWorkflow.getPopularScore());
        });

        if (visible.isEmpty()) {
            JLabel empty = new JLabel("No blueprints match your filter criteria.");
            empty.setFont(new Font("SansSerif", Font.ITALIC, 13));
            empty.setForeground(textSecondary);
            empty.setBorder(new EmptyBorder(10, 4, 10, 4));
            galleryWrapper.add(empty);
        } else {
            // Group by category (preserving original order via LinkedHashMap)
            Map<String, List<BlueprintEntry>> grouped = new LinkedHashMap<>();
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

        String emoji = "⚙️";
        String lower = categoryName.toLowerCase();
        if (lower.contains("image")) emoji = "🖼️";
        else if (lower.contains("video")) emoji = "🎬";
        else if (lower.contains("audio") || lower.contains("sound")) emoji = "🎵";
        else if (lower.contains("3d") || lower.contains("mesh")) emoji = "🎲";
        else if (lower.contains("use cases") || lower.contains("star")) emoji = "⭐";

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
                g2.setColor(cardBg);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), ARC, ARC));
                g2.setStroke(new BasicStroke(hovered ? 1.5f : 1f));
                g2.setColor(hovered ? cardHoverBorder : cardBorder);
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth()-1, getHeight()-1, ARC, ARC));
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
            reqText  = "No model requirements";
            reqColor = textDim;
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
                                playVideoPreview(entry);
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
                RoundRectangle2D clip = new RoundRectangle2D.Float(0, 0, w, h + ARC, ARC, ARC);
                g2.setClip(clip);

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
                if (status.missingCount == 0 && !entry.requiredModels.isEmpty()) {
                    badgeText  = "✅  Ready";
                    badgeColor = new Color(34, 197, 130, 200);
                } else if (status.missingCount > 0) {
                    badgeText  = "⚠  " + status.missingCount + " missing";
                    badgeColor = new Color(245, 158, 11, 200);
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
                    g2.fillRoundRect(bx, by, bw, bh, 6, 6);
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
        GradientPaint bg = new GradientPaint(0, 0, palette[0], w, h, palette[1]);
        g2.setPaint(bg);
        g2.fillRect(0, 0, w, h);

        long seed = entry.name.hashCode() & 0xFFFFFFFFL;
        Random rng = new Random(seed);
        for (int i = 0; i < 6; i++) {
            int cx = (int)(rng.nextDouble() * w);
            int cy = (int)(rng.nextDouble() * h);
            int r  = 20 + rng.nextInt(50);
            Color bc = i % 2 == 0 ? palette[2] : palette[1];
            g2.setColor(new Color(bc.getRed(), bc.getGreen(), bc.getBlue(),
                    30 + rng.nextInt(60)));
            g2.fillOval(cx - r, cy - r, r * 2, r * 2);
        }

        g2.setColor(new Color(255, 255, 255, 10));
        g2.setStroke(new BasicStroke(0.5f));
        for (int x = 0; x < w; x += 20) g2.drawLine(x, 0, x, h);
        for (int y = 0; y < h; y += 20) g2.drawLine(0, y, w, y);

        String icon = categoryIcon(entry.category);
        Font iconFont = new Font("SansSerif", Font.BOLD, 26);
        g2.setFont(iconFont);
        FontMetrics fm = g2.getFontMetrics();
        int ix = (w - fm.stringWidth(icon)) / 2;
        int iy = h / 2 - 4;
        g2.setColor(new Color(0, 0, 0, 80));
        g2.drawString(icon, ix + 1, iy + 1);
        g2.setColor(new Color(palette[2].getRed(), palette[2].getGreen(),
                palette[2].getBlue(), 210));
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


    private Image readImageNoJavaFX(String urlStr) {
        int maxRetries = 3;
        int delayMs = 500;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(2000))
                        .build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(urlStr))
                        .timeout(Duration.ofSeconds(15))
                        .GET().build();
                java.net.http.HttpResponse<byte[]> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
                
                if (response.statusCode() == 200) {
                    byte[] bytes = response.body();
                    
                    String cleanUrl = urlStr.toLowerCase();
                    int qIdx = cleanUrl.indexOf('?');
                    if (qIdx > 0) cleanUrl = cleanUrl.substring(0, qIdx);
                    
                    boolean isVideo = cleanUrl.endsWith(".mp4") || cleanUrl.endsWith(".webm") || cleanUrl.endsWith(".mov");
                    
                    if (!isVideo) {
                        try {
                            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
                            if (img != null) return img;
                        } catch (Exception ignored) {}
                    }

                    // Fallback via Temp File (for OpenCV reading/WebP or Video frames)
                    String ext = ".tmp";
                    if (cleanUrl.endsWith(".mp4")) ext = ".mp4";
                    else if (cleanUrl.endsWith(".webm")) ext = ".webm";
                    else if (cleanUrl.endsWith(".gif")) ext = ".gif";
                    else if (cleanUrl.endsWith(".webp")) ext = ".webp";
                    
                    // Generate a filename without numbers to prevent OpenCV's CV_IMAGES backend from treating it as an image sequence
                    String nonce = UUID.randomUUID().toString().replaceAll("[0-9-]", "x");
                    File tempFile = new File(System.getProperty("java.io.tmpdir"), "blueprint_preview_" + nonce + ext);
                    tempFile.deleteOnExit();
                    Files.write(tempFile.toPath(), bytes);
                    
                    try {
                        // Ensure OpenCV native binaries are loaded before Video4j / VideoCapture
                        try {
                            de.tki.comfymodels.util.OpenCvLoader.load();
                        } catch (Throwable ignored) {}

                        if (isVideo) {
                            try {
                                Video4j.init();
                                try (VideoFile vid = Videos.open(tempFile.getAbsolutePath())) {
                                    vid.seekToFrame(0);
                                    BufferedImage img = vid.frameToImage();
                                    if (img != null) {
                                        return img;
                                    }
                                }
                            } catch (Exception ex) {
                                logger.debug("Video4j fallback failed for " + urlStr, ex);
                            }
                        }

                        Mat mat = Imgcodecs.imread(tempFile.getAbsolutePath());

                        if (mat != null && !mat.empty()) {
                            MatOfByte buffer = new MatOfByte();
                            Imgcodecs.imencode(".png", mat, buffer);
                            return ImageIO.read(new ByteArrayInputStream(buffer.toArray()));
                        } else {
                            try {
                                org.opencv.videoio.VideoCapture cap = new org.opencv.videoio.VideoCapture(tempFile.getAbsolutePath(), org.opencv.videoio.Videoio.CAP_FFMPEG);
                                if (!cap.isOpened()) {
                                    // Fallback to default if FFMPEG is missing
                                    cap = new org.opencv.videoio.VideoCapture(tempFile.getAbsolutePath());
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
                            } catch (Exception ignored) {}
                        }
                    } finally {
                        tempFile.delete();
                    }
                } else if (response.statusCode() == 404) {
                    // HTTP 404 means the file does not exist, fail immediately without retry
                    break;
                } else {
                    logger.error("⚠️ [BlueprintGallery] HTTP " + response.statusCode() + " for: " + urlStr + " (Attempt " + attempt + ")");
                }
            } catch (java.net.ConnectException | java.net.http.HttpTimeoutException e) {
                logger.error("⚠️ [BlueprintGallery] Connection error for: " + urlStr + " (Attempt " + attempt + "): " + e.getMessage());
            } catch (Exception e) {
                logger.error("⚠️ [BlueprintGallery] Error loading image: " + urlStr + " (Attempt " + attempt + "): " + e.getMessage());
            }

            if (attempt < maxRetries) {
                try {
                    Thread.sleep(delayMs * attempt); // Exponential backoff: 500ms, 1000ms
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return null;
    }

    // ── DETAIL DIALOG ─────────────────────────────────────────────────────────

    private void showDetails(BlueprintEntry entry, BlueprintStatus status) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dlg = new JDialog(owner instanceof Frame ? (Frame) owner : null,
                "Blueprint: " + entry.name, true);
        dlg.setSize(840, 680);
        dlg.setLocationRelativeTo(this);

        Color bg = configService.isDarkMode() ? new Color(20, 22, 30) : new Color(245, 247, 250);

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(bg);
        root.setBorder(new EmptyBorder(22, 26, 18, 26));

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
            btnPlayPreview.addActionListener(evt -> playVideoPreview(entry));
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
            int targetW = 740;
            int imgW = previewImg.getWidth(null);
            int imgH = previewImg.getHeight(null);
            if (imgW > 0 && imgH > 0) {
                int targetH = (int) (imgH * ((double) targetW / imgW));
                if (targetH > 280) {
                    targetH = 280;
                    targetW = (int) (imgW * ((double) targetH / imgH));
                }
                Image scaled = previewImg.getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);
                imgLabel.setIcon(new ImageIcon(scaled));
                
                JPanel imgPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
                imgPanel.setOpaque(false);
                imgPanel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
                imgPanel.setBorder(new EmptyBorder(0, 0, 14, 0));
                imgPanel.add(imgLabel);
                body.add(imgPanel);
                dlg.setSize(840, 820);
            }
        }

        if (!entry.category.isEmpty()) addRow(body, "Category", entry.category);
        if (!entry.description.isEmpty()) {
            JLabel desc = new JLabel("<html><body style='width:740px'>" +
                    escapeHtml(entry.description) + "</body></html>");
            desc.setFont(new Font("SansSerif", Font.PLAIN, 11));
            desc.setForeground(textSecondary);
            desc.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            body.add(Box.createVerticalStrut(6));
            body.add(desc);
            body.add(Box.createVerticalStrut(10));
        }

        addRow(body, "Required models", String.valueOf(entry.requiredModels.size()));
        addRow(body, "Available locally", String.valueOf(status.presentCount));
        addRow(body, "Missing", String.valueOf(status.missingCount));

        if (!entry.requiredModels.isEmpty()) {
            body.add(Box.createVerticalStrut(12));
            JLabel hdr = new JLabel("Required Models Status:");
            hdr.setFont(new Font("SansSerif", Font.BOLD, 12));
            hdr.setForeground(textPrimary);
            hdr.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            body.add(hdr);
            body.add(Box.createVerticalStrut(6));

            String[] columns = {"Model Name", "Type", "Size", "Status"};
            javax.swing.table.DefaultTableModel modelTblModel = new javax.swing.table.DefaultTableModel(columns, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };

            for (ModelInfo info : entry.requiredModels) {
                String name = info.getName();
                String type = info.getType() != null ? info.getType() : "checkpoints";
                String size = resolveModelSize(info);
                String statusStr = getModelStatus(info);
                modelTblModel.addRow(new Object[]{name, type, size, statusStr});
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
            table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 11));
            table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

            // Set column width distribution to ratio 6 : 2 : 1 : 1 (Model Name 6, Type 2, Size 1, Status 1)
            table.getColumnModel().getColumn(0).setPreferredWidth(450); // Model Name (6x ratio)
            table.getColumnModel().getColumn(1).setPreferredWidth(150); // Type (2x ratio)
            table.getColumnModel().getColumn(2).setPreferredWidth(80);  // Size (1x ratio)
            table.getColumnModel().getColumn(3).setPreferredWidth(100); // Status (1x ratio)

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
            tblScroll.setPreferredSize(new Dimension(740, 180));
            tblScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
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
                    SwingUtilities.invokeLater(() -> btnSendToComfy.setText("Sending..."));
                    try {
                        String fileContent = Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8).trim();
                        JSONObject json = new JSONObject(fileContent);
                        
                        String comfyUrl = configService.getComfyUIUrl();
                        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                                .connectTimeout(java.time.Duration.ofSeconds(5))
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
     * Downloads the entry preview (if remote) into a temp file and opens
     * a self-contained video player dialog. For image previews this method
     * just opens the URL in the OS default viewer.
     */
    private void playVideoPreview(BlueprintEntry entry) {
        String url = entry.previewPath;
        if (url == null || url.isEmpty()) return;
        // Download to a temp file so the player can stream from a local path.
        try {
            String ext = ".mp4";
            String lower = url.toLowerCase();
            if (lower.endsWith(".webm")) ext = ".webm";
            else if (lower.endsWith(".mov")) ext = ".mov";
            String nonce = java.util.UUID.randomUUID().toString().replaceAll("[0-9-]", "x");
            File tmp = new File(System.getProperty("java.io.tmpdir"),
                "blueprint_video_" + nonce + ext);
            tmp.deleteOnExit();
            try (java.io.InputStream is = new java.net.URL(url).openStream()) {
                java.nio.file.Files.copy(is, tmp.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            // Launch a media player dialog.
            Window owner = SwingUtilities.getWindowAncestor(this);
            new VideoPreviewDialog(owner instanceof Frame ? (Frame) owner : null,
                    entry.name, tmp, configService.isDarkMode()).setVisible(true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not open video preview: " + ex.getMessage(),
                    "Video Preview Error", JOptionPane.ERROR_MESSAGE);
        }
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

    private boolean isHighLevelModelPresentDeep(String reqName, Set<String> localBaseNames) {
        if (reqName == null || reqName.isBlank()) return true;
        String clean = reqName.toLowerCase(Locale.ROOT).trim();
        
        // Closed source / API-based models don't require local files
        if (clean.contains("api") || clean.contains("seedance") || clean.contains("elevenlabs") ||
            clean.contains("openai") || clean.contains("dall-e") || clean.contains("gpt-image") ||
            clean.contains("luma") || clean.contains("kling") || clean.contains("runway") ||
            clean.contains("vidu") || clean.contains("minimax") || clean.contains("happyhorse") ||
            clean.contains("dream") || clean.contains("sonilo") || clean.contains("sustain") ||
            clean.contains("midjourney") || clean.contains("google") || clean.contains("gemini") ||
            clean.contains("anthropic") || clean.contains("claude") || clean.contains("openrouter")) {
            return true;
        }

        // Fuzzy matching logic for open-source model families:
        if (clean.contains("flux")) {
            for (String local : localBaseNames) {
                if (local.contains("flux")) return true;
            }
            return false;
        }
        if (clean.contains("sdxl")) {
            for (String local : localBaseNames) {
                if (local.contains("sdxl")) return true;
            }
            return false;
        }
        if (clean.contains("sd3") || clean.contains("stable diffusion 3")) {
            for (String local : localBaseNames) {
                if (local.contains("sd3") || local.contains("sd_3")) return true;
            }
            return false;
        }
        if (clean.contains("sd1.5") || clean.contains("sd 1.5") || clean.contains("sd15")) {
            for (String local : localBaseNames) {
                if (local.contains("sd15") || local.contains("sd1.5") || local.contains("v1-5")) return true;
            }
            return false;
        }
        if (clean.contains("wan")) {
            for (String local : localBaseNames) {
                if (local.contains("wan")) return true;
            }
            return false;
        }
        if (clean.contains("hunyuan")) {
            for (String local : localBaseNames) {
                if (local.contains("hunyuan")) return true;
            }
            return false;
        }
        if (clean.contains("qwen")) {
            for (String local : localBaseNames) {
                if (local.contains("qwen")) return true;
            }
            return false;
        }
        if (clean.contains("ltx")) {
            for (String local : localBaseNames) {
                if (local.contains("ltx")) return true;
            }
            return false;
        }
        if (clean.equals("vae") || clean.equals("ae") || clean.contains("autoencoder")) {
            for (String local : localBaseNames) {
                if (local.contains("vae") || local.contains("ae") || local.contains("autoencoder")) return true;
            }
            return false;
        }
        if (clean.contains("lora")) {
            for (String local : localBaseNames) {
                if (local.contains("lora")) return true;
            }
            return false;
        }
        if (clean.contains("clip") || clean.contains("t5") || clean.contains("encoder") || clean.contains("text_encoder")) {
            for (String local : localBaseNames) {
                if (local.contains("clip") || local.contains("t5") || local.contains("encoder") || local.contains("text_encoder")) return true;
            }
            return false;
        }
        if (clean.contains("unet") || clean.contains("diffusion")) {
            for (String local : localBaseNames) {
                if (local.contains("unet") || local.contains("diffusion")) return true;
            }
            return false;
        }

        // General fallback
        String[] parts = clean.split("[\\s\\-\\.\\_\\/]+");
        if (parts.length > 0) {
            String bestPart = "";
            for (String part : parts) {
                if (part.length() > bestPart.length() && !part.equals("model") && !part.equals("text") && !part.equals("image") && !part.equals("edit") && !part.equals("generation")) {
                    bestPart = part;
                }
            }
            if (bestPart.length() >= 3) {
                for (String local : localBaseNames) {
                    if (local.contains(bestPart)) return true;
                }
            }
        }
        return false;
    }

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

        if (base != null && !base.isBlank() && localModelScanner != null) {
            try {
                java.util.Optional<java.nio.file.Path> found = localModelScanner.findModelWithPrefSizeAndType(
                        java.nio.file.Paths.get(base), filename, 0L, type);
                if (found.isPresent()) {
                    return formatByteSize(java.nio.file.Files.size(found.get()));
                }
            } catch (Exception ignored) {}
        }

        if (archive != null && !archive.isBlank() && localModelScanner != null) {
            try {
                java.util.Optional<java.nio.file.Path> found = localModelScanner.findModelWithPrefSize(
                        java.nio.file.Paths.get(archive), filename, 0L);
                if (found.isPresent()) {
                    return formatByteSize(java.nio.file.Files.size(found.get()));
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
        
        // If it is a high level model name (no extension), fuzzy match it using localModelValidator cache
        if (!isSupportedModelFile(info.getName())) {
            Set<String> localBaseNames = localModelValidator.getLocalModelBaseNames();
            boolean present = isHighLevelModelPresentDeep(info.getName(), localBaseNames);
            return present ? "✅ Already exists" : "Idle";
        }

        String base = configService.getModelsPath();
        String archive = configService.getArchivePath();
        if (base == null || base.isEmpty()) return "Idle";

        String type = info.getType() != null ? info.getType() : "checkpoints";
        String folder = info.getSave_path() != null ? info.getSave_path() : type;
        String normalizedFolder = archiveService.normalizeFolder(folder);

        // 1. Primary path check (Standard Models Path)
        java.nio.file.Path local = "root".equals(normalizedFolder) ? java.nio.file.Paths.get(base, info.getName()) : java.nio.file.Paths.get(base, normalizedFolder, info.getName());
        boolean exists = java.nio.file.Files.exists(local) && java.nio.file.Files.isRegularFile(local);

        // 2. Primary archive check (Standard Archive Path)
        boolean inArchive = false;
        java.nio.file.Path archivedPath = null;
        if (archive != null && !archive.trim().isEmpty()) {
            archivedPath = "root".equals(normalizedFolder) ? java.nio.file.Paths.get(archive, info.getName()) : java.nio.file.Paths.get(archive, normalizedFolder, info.getName());
            inArchive = java.nio.file.Files.exists(archivedPath) && java.nio.file.Files.isRegularFile(archivedPath);
        }
        
        boolean sizeMismatch = false;

        // 3. Robust existence and archive cross-check (Safety Guard)
        if (archive != null && !archive.isEmpty()) {
            try {
                java.nio.file.Path absArchive = java.nio.file.Paths.get(archive).toAbsolutePath().normalize();
                
                // If 'local' is actually inside the archive, it's NOT a local active model
                if (exists && local.toAbsolutePath().normalize().startsWith(absArchive)) {
                    exists = false;
                    inArchive = true;
                }
                
                // If we found it in the primary archive location, verify size if known
                if (inArchive && info.getByteSize() > 0) {
                    if (java.nio.file.Files.size(archivedPath) != info.getByteSize()) {
                        inArchive = false; // Size mismatch doesn't count
                    }
                }
            } catch (Exception ignored) {}
        }

        // 4. Fallback: Recursive search in ARCHIVE if not found at primary archive location
        if (!inArchive && archive != null && !archive.isEmpty()) {
            java.util.Optional<java.nio.file.Path> foundInArchive = localModelScanner.findModelWithPrefSize(java.nio.file.Paths.get(archive), info.getName(), info.getByteSize());
            if (foundInArchive.isPresent()) {
                archivedPath = foundInArchive.get();
                inArchive = true;
            }
        }

        // 5. Fallback: Recursive search in LOCAL MODELS if not found OR size mismatch at primary location
        if ((!exists || sizeMismatch) && base != null && !base.isEmpty()) {
            java.util.Optional<java.nio.file.Path> foundLocally = localModelScanner.findModelWithPrefSizeAndType(java.nio.file.Paths.get(base), info.getName(), info.getByteSize(), type);
            if (foundLocally.isPresent()) {
                java.nio.file.Path potentialLocal = foundLocally.get();
                try {
                    long potSize = java.nio.file.Files.size(potentialLocal);
                    if (info.getByteSize() <= 0 || potSize == info.getByteSize()) {
                        local = potentialLocal;
                        exists = true;
                        sizeMismatch = false;
                        
                        java.nio.file.Path root = java.nio.file.Paths.get(base).toAbsolutePath().normalize();
                        java.nio.file.Path absPotential = potentialLocal.toAbsolutePath().normalize();
                        
                        if (absPotential.startsWith(root)) {
                            java.nio.file.Path rel = root.relativize(absPotential);
                            normalizedFolder = (rel.getParent() != null) ? rel.getParent().toString().replace("\\", "/") : "root";
                        } else {
                            normalizedFolder = "extra/" + potentialLocal.getParent().getFileName();
                        }
                        info.setSave_path(normalizedFolder);
                    }
                } catch (Exception ignored) {}
            }
        }

        // 6. Final Status Determination
        if (exists) {
            return "✅ Already exists";
        } else if (inArchive) {
            return "📦 Archived";
        } else if (sizeMismatch) {
            return "🔄 Size Mismatch";
        } else if (info.getUrl() == null || info.getUrl().equals("MISSING")) {
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
            chkReadyOnly.setForeground(textSecondary);
            if (darkMode) {
                chkReadyOnly.putClientProperty("FlatLaf.style", 
                    "iconSize: 20,20; " +
                    "icon.borderColor: #FFFFFF; " +
                    "icon.selectedBorderColor: #FFFFFF; " +
                    "icon.checkmarkColor: #121318; " +
                    "icon.focusWidth: 3; " +
                    "icon.selectedBackground: #FFFFFF"
                );
            } else {
                chkReadyOnly.putClientProperty("FlatLaf.style", 
                    "iconSize: 20,20; " +
                    "icon.borderColor: #121318; " +
                    "icon.selectedBorderColor: #009688; " +
                    "icon.checkmarkColor: #FFFFFF; " +
                    "icon.focusWidth: 3; " +
                    "icon.selectedBackground: #009688"
                );
            }
            chkReadyOnly.revalidate();
            chkReadyOnly.repaint();
        }
        if (chkHideCloud != null) {
            chkHideCloud.setForeground(textSecondary);
            if (darkMode) {
                chkHideCloud.putClientProperty("FlatLaf.style", 
                    "iconSize: 20,20; " +
                    "icon.borderColor: #FFFFFF; " +
                    "icon.selectedBorderColor: #FFFFFF; " +
                    "icon.checkmarkColor: #121318; " +
                    "icon.focusWidth: 3; " +
                    "icon.selectedBackground: #FFFFFF"
                );
            } else {
                chkHideCloud.putClientProperty("FlatLaf.style", 
                    "iconSize: 20,20; " +
                    "icon.borderColor: #121318; " +
                    "icon.selectedBorderColor: #009688; " +
                    "icon.checkmarkColor: #FFFFFF; " +
                    "icon.focusWidth: 3; " +
                    "icon.selectedBackground: #009688"
                );
            }
            chkHideCloud.revalidate();
            chkHideCloud.repaint();
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
        return list;
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
