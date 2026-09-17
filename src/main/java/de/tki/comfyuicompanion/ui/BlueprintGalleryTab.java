package de.tki.comfyuicompanion.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.domain.ComfyRegistryWorkflow;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.ProcessTracker;
import de.tki.comfyuicompanion.service.impl.LocalModelScanner;
import de.tki.comfyuicompanion.service.impl.ArchiveService;
import de.tki.comfyuicompanion.service.impl.LocalAIService;
import de.tki.comfyuicompanion.service.IModelArchitectureService;
import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import de.tki.comfyuicompanion.service.IComfyRegistryClient;
import de.tki.comfyuicompanion.service.ILocalModelValidator;
import de.tki.comfyuicompanion.service.IWorkflowDownloader;
import de.tki.comfyuicompanion.service.IModelSearchService;
import de.tki.comfyuicompanion.ui.blueprint.BlueprintCardBuilder;
import de.tki.comfyuicompanion.ui.blueprint.BlueprintDataCoordinator;
import de.tki.comfyuicompanion.ui.blueprint.BlueprintDetailsDialog;
import de.tki.comfyuicompanion.ui.blueprint.BlueprintPreviewCache;
import de.tki.comfyuicompanion.ui.blueprint.ThemedCheckBox;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Blueprint Gallery tab for exploring, filtering, inspecting, and launching
 * workflow templates fetched from Comfy.org or bundled blueprints.
 */
@Component
public class BlueprintGalleryTab extends JPanel {
    private static final Logger logger = LoggerFactory.getLogger(BlueprintGalleryTab.class);

    // ── services ─────────────────────────────────────────────────────────────
    private final ConfigService configService;
    private final IModelArchitectureService modelArchitectureService;
    private final IComfyLifecycleService lifecycleService;
    private final IComfyRegistryClient registryClient;
    private final ILocalModelValidator localModelValidator;
    private final IWorkflowDownloader workflowDownloader;
    private final LocalModelScanner localModelScanner;
    private final ArchiveService archiveService;
    private final LocalAIService localAIService;
    private final ProcessTracker processTracker;
    private final IModelSearchService modelSearchService;

    // ── extracted helpers ─────────────────────────────────────────────────────
    private final BlueprintPreviewCache previewCache;
    private final BlueprintDataCoordinator dataCoordinator;

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

    // ── data ──────────────────────────────────────────────────────────────────
    private final List<BlueprintEntry> allEntries = new CopyOnWriteArrayList<>();
    private final AtomicBoolean isRefreshing = new AtomicBoolean(false);
    private final javax.swing.Timer filterDebounceTimer = new javax.swing.Timer(250, e -> applyFilter());
    private final List<JPanel> categoryGridPanels = new CopyOnWriteArrayList<>();
    private boolean hasScanned = false;
    private boolean initialLoadComplete = false;
    private Runnable onDataLoadedCallback;
    private java.util.function.Consumer<File> onWorkflowImportRequested;

    /**
     * Registers a callback invoked when the user requests to import a workflow file.
     *
     * @param onWorkflowImportRequested the callback consumer
     */
    public void setOnWorkflowImportRequested(java.util.function.Consumer<File> onWorkflowImportRequested) {
        this.onWorkflowImportRequested = onWorkflowImportRequested;
    }

    // ── design ────────────────────────────────────────────────────────────────
    private Color bgPage          = new Color(15, 16, 21);
    private Color cardBg          = new Color(24, 26, 34);
    private Color cardBorder      = new Color(48, 52, 68);
    private Color cardHoverBorder = new Color(0, 210, 190);
    private Color textPrimary     = new Color(228, 230, 240);
    private Color textSecondary   = new Color(120, 126, 152);
    private Color textDim         = new Color(80, 86, 110);

    private static final int CARD_GAP = 14;
    private static final int CARD_W   = 220;

    private static final List<String[]> CAT_PALETTES = List.of(
            new String[]{"text to image",     "#0F172A", "#1E3A8A", "#3B82F6"},
            new String[]{"text to video",     "#1A0A2A", "#4A154B", "#A21CAF"},
            new String[]{"image to video",    "#0A1A2A", "#1E40AF", "#06B6D4"},
            new String[]{"image edit",        "#1A1A0A", "#4D3800", "#F59E0B"},
            new String[]{"inpaint",           "#0A1A14", "#065F46", "#10B981"},
            new String[]{"upscal",            "#1A0A14", "#701A75", "#EC4899"},
            new String[]{"controlnet",        "#140A2A", "#3730A3", "#818CF8"},
            new String[]{"depth",             "#0A1420", "#164E63", "#06B6D4"},
            new String[]{"pose",              "#1A140A", "#78350F", "#D97706"},
            new String[]{"segmentation",      "#0D1A1A", "#134E4A", "#14B8A6"},
            new String[]{"caption",           "#1A102A", "#581C87", "#A855F7"},
            new String[]{"3d",                "#1C140A", "#7C2D12", "#F97316"},
            new String[]{"audio",             "#0A1A1C", "#155E75", "#22D3EE"}
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
                               LocalAIService localAIService,
                               IModelSearchService modelSearchService,
                               @Autowired(required = false) ProcessTracker processTracker) {
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

        this.previewCache = new BlueprintPreviewCache(configService);
        this.dataCoordinator = new BlueprintDataCoordinator(
                configService,
                localModelScanner,
                archiveService,
                localModelValidator,
                modelArchitectureService,
                registryClient
        );

        this.filterDebounceTimer.setRepeats(false);

        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(bgPage);

        buildUI();
        boolean darkMode = configService != null ? configService.isDarkMode() : true;
        updateTheme(darkMode);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                if (!hasScanned) {
                    refreshAllData();
                } else {
                    updateStatusAsync();
                }
            }
        });
    }

    public void requestFilterUpdate() {
        if (SwingUtilities.isEventDispatchThread()) {
            filterDebounceTimer.restart();
        } else {
            SwingUtilities.invokeLater(filterDebounceTimer::restart);
        }
    }

    public void setOnDataLoadedCallback(Runnable callback) {
        this.onDataLoadedCallback = callback;
    }

    private void buildUI() {
        JPanel topBar = new JPanel();
        topBar.setLayout(new BoxLayout(topBar, BoxLayout.Y_AXIS));
        topBar.setOpaque(false);
        topBar.setBorder(new EmptyBorder(14, 22, 6, 22));

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

        txtSearch = new JTextField(15);
        txtSearch.putClientProperty("JTextField.placeholderText", "Search blueprints...");
        txtSearch.putClientProperty("JTextField.leadingIcon", SvgIconFactory.get(AppIcon.SEARCH));
        txtSearch.addKeyListener(new KeyAdapter() {
            @Override public void keyReleased(KeyEvent e) { applyFilter(); }
        });

        cbCategory = new JComboBox<>();
        cbCategory.addItem("All Categories");
        cbCategory.addActionListener(e -> applyFilter());

        btnRefresh = new JButton("Refresh", SvgIconFactory.get(AppIcon.REFRESH));
        btnRefresh.setFont(new Font("SansSerif", Font.PLAIN, 12));
        btnRefresh.addActionListener(e -> refreshAllData(true));

        mainControls.add(txtSearch);
        mainControls.add(cbCategory);
        mainControls.add(btnRefresh);
        row1.add(mainControls, BorderLayout.EAST);

        topBar.add(row1);
        topBar.add(Box.createVerticalStrut(10));

        JPanel row2 = new JPanel(new BorderLayout());
        row2.setOpaque(false);

        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        optionsPanel.setOpaque(false);

        boolean initialDark = configService != null ? configService.isDarkMode() : true;
        chkReadyOnly = new ThemedCheckBox("Ready to Run only", initialDark);
        chkReadyOnly.setFont(new Font("SansSerif", Font.PLAIN, 12));
        chkReadyOnly.addActionListener(e -> applyFilter());

        chkHideCloud = new ThemedCheckBox("Hide Cloud Workflows", initialDark);
        chkHideCloud.setFont(new Font("SansSerif", Font.PLAIN, 12));
        chkHideCloud.setSelected(true);
        chkHideCloud.addActionListener(e -> applyFilter());

        optionsPanel.add(chkReadyOnly);
        optionsPanel.add(chkHideCloud);

        lblStatus = new JLabel(" ");
        lblStatus.setFont(new Font("SansSerif", Font.ITALIC, 11));
        lblStatus.setForeground(textDim);

        row2.add(optionsPanel, BorderLayout.WEST);
        row2.add(lblStatus, BorderLayout.EAST);

        topBar.add(row2);
        topBar.add(Box.createVerticalStrut(6));

        northPanel = topBar;
        add(topBar, BorderLayout.NORTH);

        galleryWrapper = new ScrollablePanel(new BoxLayout(null, BoxLayout.Y_AXIS));
        galleryWrapper.setLayout(new BoxLayout(galleryWrapper, BoxLayout.Y_AXIS));
        galleryWrapper.setOpaque(false);
        galleryWrapper.setBorder(new EmptyBorder(10, 20, 24, 20));

        scroll = new JScrollPane(galleryWrapper);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        scroll.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { recomputeColumns(); }
        });

        add(scroll, BorderLayout.CENTER);
    }

    private void recomputeColumns() {
        if (galleryWrapper == null || categoryGridPanels.isEmpty()) return;
        int availableWidth = galleryWrapper.getWidth() - 40;
        int cols = Math.max(1, availableWidth / (CARD_W + CARD_GAP));
        for (JPanel gridPanel : categoryGridPanels) {
            if (gridPanel.getLayout() instanceof GridLayout gl) {
                if (gl.getColumns() != cols) {
                    gl.setColumns(cols);
                    gridPanel.revalidate();
                }
            }
        }
    }

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
                btnRefresh.setText("Refreshing…");
            }
            if (allEntries.isEmpty()) {
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
                List<BlueprintEntry> loaded = dataCoordinator.fetchAndIndexEntries(cb -> requestFilterUpdate());
                allEntries.clear();
                allEntries.addAll(loaded);

                SwingUtilities.invokeLater(() -> {
                    updateCategoryComboBox();
                    if (btnRefresh != null) {
                        btnRefresh.setEnabled(true);
                        btnRefresh.setText("Refresh");
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
                        btnRefresh.setText("Refresh");
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
        return name.contains("text to video") || name.contains("image to video") || name.contains("video to video")
                || name.contains("video") || name.contains("animatediff") || name.contains("cogvideo")
                || name.contains("wan 2") || name.contains("hunyuanvideo") || name.contains("ltx-video")
                || desc.contains("generates video") || desc.contains("text-to-video") || desc.contains("image-to-video");
    }

    public static int getBlueprintMediaRank(BlueprintEntry entry) {
        if (entry == null) return 2;
        if (isVideoBlueprint(entry)) {
            return 1;
        }
        String cat = entry.category != null ? entry.category.toLowerCase(Locale.ROOT) : "";
        if (cat.contains("audio") || cat.contains("sound") || cat.contains("music") || cat.contains("voice")) {
            return 2;
        }
        if (cat.contains("3d") || cat.contains("mesh")) {
            return 2;
        }
        return 0;
    }

    public static int getCategoryOrderScore(String categoryName) {
        if (categoryName == null || categoryName.trim().isEmpty()) return 500;
        String cat = categoryName.toLowerCase(Locale.ROOT).trim();

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

        if (cat.contains("text to video") || cat.contains("t2v") || cat.contains("text-to-video")) return 110;
        if (cat.contains("image to video") || cat.contains("i2v") || cat.contains("image-to-video")) return 120;
        if (cat.contains("video to video") || cat.contains("v2v") || cat.contains("video-to-video")) return 130;
        if (cat.contains("video") || cat.contains("animate") || cat.contains("motion")
                || cat.contains("interpolat") || cat.contains("frame")) return 140;

        if (cat.contains("3d") || cat.contains("mesh")) return 210;
        if (cat.contains("audio") || cat.contains("sound") || cat.contains("music") || cat.contains("voice")) return 220;
        if (cat.contains("use case") || cat.contains("star")) return 230;

        return 300;
    }

    private void updateCategoryComboBox() {
        if (cbCategory == null) return;

        ActionListener[] listeners = cbCategory.getActionListeners();
        for (ActionListener l : listeners) {
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
            if (o1 != o2) return Integer.compare(o1, o2);
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

        for (ActionListener l : listeners) {
            cbCategory.addActionListener(l);
        }
    }

    private void applyFilter() {
        if (txtSearch == null || cbCategory == null) return;

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

            if (chkReadyOnly.isSelected() && computeStatus(e).missingCount > 0) {
                continue;
            }

            if (chkHideCloud.isSelected() && e.registryWorkflow.isCloudOnly()) {
                continue;
            }

            if (selectedCategory != null && !"All Categories".equals(selectedCategory)) {
                String entryCat = e.category != null && !e.category.isEmpty() ? e.category : "General";
                if (!selectedCategory.equalsIgnoreCase(entryCat)) {
                    continue;
                }
            }

            visible.add(e);
        }

        visible.sort((e1, e2) -> {
            int rank1 = getBlueprintMediaRank(e1);
            int rank2 = getBlueprintMediaRank(e2);
            if (rank1 != rank2) return Integer.compare(rank1, rank2);

            int catOrder1 = getCategoryOrderScore(e1.category);
            int catOrder2 = getCategoryOrderScore(e2.category);
            if (catOrder1 != catOrder2) return Integer.compare(catOrder1, catOrder2);

            boolean run1 = computeStatus(e1).missingCount == 0;
            boolean run2 = computeStatus(e2).missingCount == 0;
            if (run1 != run2) return run1 ? -1 : 1;

            double pop1 = e1.registryWorkflow != null ? e1.registryWorkflow.getPopularScore() : 0.0;
            double pop2 = e2.registryWorkflow != null ? e2.registryWorkflow.getPopularScore() : 0.0;
            int popCmp = Double.compare(pop2, pop1);
            if (popCmp != 0) return popCmp;

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
            Map<String, List<BlueprintEntry>> grouped = new TreeMap<>((c1, c2) -> {
                int o1 = getCategoryOrderScore(c1);
                int o2 = getCategoryOrderScore(c2);
                if (o1 != o2) return Integer.compare(o1, o2);
                return c1.compareToIgnoreCase(c2);
            });

            for (BlueprintEntry entry : visible) {
                String cat = entry.category != null && !entry.category.isEmpty() ? entry.category : "General";
                grouped.computeIfAbsent(cat, k -> new ArrayList<>()).add(entry);
            }

            for (Map.Entry<String, List<BlueprintEntry>> groupEntry : grouped.entrySet()) {
                String categoryName = groupEntry.getKey();
                List<BlueprintEntry> items = groupEntry.getValue();

                galleryWrapper.add(buildCategoryHeader(categoryName));

                JPanel gridPanel = new JPanel();
                gridPanel.setOpaque(false);
                gridPanel.setBorder(new EmptyBorder(8, 4, 14, 4));
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
        return BlueprintCardBuilder.buildCard(
                entry,
                status,
                palette,
                previewCache,
                cardBg,
                cardBorder,
                cardHoverBorder,
                textPrimary,
                textDim,
                categoryIcon(entry.category),
                e -> showDetails(e, computeStatus(e)),
                (e, w) -> dataCoordinator.playVideoPreview(e, w, null)
        );
    }

    private void showDetails(BlueprintEntry entry, BlueprintStatus status) {
        BlueprintDetailsDialog dlg = new BlueprintDetailsDialog(
                SwingUtilities.getWindowAncestor(this),
                entry,
                status,
                configService,
                workflowDownloader,
                lifecycleService,
                localModelValidator,
                modelSearchService,
                previewCache,
                (e, btn) -> dataCoordinator.playVideoPreview(e, SwingUtilities.getWindowAncestor(btn), btn),
                dataCoordinator::getModelStatus,
                dataCoordinator::resolveModelSize,
                file -> {
                    if (onWorkflowImportRequested != null) {
                        onWorkflowImportRequested.accept(file);
                    }
                },
                this::showDetails
        );
        dlg.setVisible(true);
    }

    private String getModelStatus(ModelInfo info) {
        return dataCoordinator.getModelStatus(info);
    }

    private boolean isModelPresentDeep(ModelInfo req) {
        return dataCoordinator.isModelPresentDeep(req);
    }

    private BlueprintStatus computeStatusInternal(BlueprintEntry entry) {
        return dataCoordinator.computeStatusInternal(entry);
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
            scroll.setOpaque(false);
            scroll.getViewport().setOpaque(false);
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

    public List<BlueprintEntry> getAvailableBlueprints() {
        List<BlueprintEntry> list = new ArrayList<>();
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
                    if (BlueprintDataCoordinator.isSupportedModelFile(mName)) {
                        ModelInfo info = new ModelInfo();
                        info.setName(mName);
                        info.setType(BlueprintDataCoordinator.inferTypeFromFilename(mName));
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
