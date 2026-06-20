package de.tki.comfymodels.ui;

import de.tki.comfymodels.Main;
import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.IModelArchitectureService;
import de.tki.comfymodels.service.IComfyLifecycleService;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.stream.Stream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;

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
 * Loads every *.json workflow from the companion_blueprints folder,
 * extracts the category / description metadata, and renders a visual
 * card gallery with an overlay badge showing local model availability.
 *
 * Layout uses a dynamic column grid so cards never get clipped horizontally.
 */
@Component
public class BlueprintGalleryTab extends JPanel {

    // ── services ─────────────────────────────────────────────────────────────
    private final ConfigService configService;
    private final IModelArchitectureService modelArchitectureService;
    private final IComfyLifecycleService lifecycleService;
    private final ObjectMapper mapper = new ObjectMapper();

    // ── ui ────────────────────────────────────────────────────────────────────
    private JPanel galleryWrapper;   // holds the GridLayout gallery
    private JLabel lblSummary;
    private JLabel lblStatus;
    private JTextField txtSearch;
    private JLabel lblTitle;

    // ── data ──────────────────────────────────────────────────────────────────
    private final List<BlueprintEntry> allEntries = new CopyOnWriteArrayList<>();
    private final Set<String> localModelBaseNames  = ConcurrentHashMap.newKeySet();
    private final Map<String, Image> previewCache = new ConcurrentHashMap<>();
    private final Set<String> loadingPaths = ConcurrentHashMap.newKeySet();
    private boolean hasScanned = false;
    private volatile long lastServerConnectionFailureTime = 0;
    private static final long SERVER_FAILURE_COOLDOWN_MS = 10000;

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
    /** Maps a keyword found in category / filename to a gradient palette. */
    private static final List<String[]> CAT_PALETTES = List.of(
        // keyword, dark-bg, mid, accent
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

    // ════════════════════════════════════════════════════════════════════════
    @Autowired
    public BlueprintGalleryTab(ConfigService configService,
                               IModelArchitectureService modelArchitectureService,
                               IComfyLifecycleService lifecycleService) {
        this.configService = configService;
        this.modelArchitectureService = modelArchitectureService;
        this.lifecycleService = lifecycleService;

        setLayout(new BorderLayout());
        setOpaque(false);

        buildUI();
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                if (!hasScanned) {
                    refreshAllData();
                }
            }
        });

        // Register progress listener
        modelArchitectureService.addProgressListener((percent, currentFileName, completed) -> {
            SwingUtilities.invokeLater(() -> {
                if (completed) {
                    hasScanned = true;
                    lblStatus.setText("Scanning complete. Loading gallery...");
                    new Thread(() -> {
                        loadScanResultsFromCache();
                        scanLocalModels();
                        SwingUtilities.invokeLater(() -> {
                            applyFilter();
                            updateSummary();
                        });
                    }, "BlueprintGalleryReload").start();
                } else {
                    lblStatus.setText("⏳  Scanning blueprints (" + percent + "%): " + currentFileName);
                }
            });
        });
    }

    // ════════════════════════════════════════════════════════════════════════
    //  UI BUILD
    // ════════════════════════════════════════════════════════════════════════

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

        // Right: search + refresh
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controls.setOpaque(false);

        txtSearch = new JTextField(18);
        txtSearch.putClientProperty("JTextField.placeholderText", "Search blueprints…");
        txtSearch.setFont(new Font("SansSerif", Font.PLAIN, 12));
        txtSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { SwingUtilities.invokeLater(() -> applyFilter()); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { SwingUtilities.invokeLater(() -> applyFilter()); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { SwingUtilities.invokeLater(() -> applyFilter()); }
        });

        JButton btnRefresh = new JButton("🔄  Refresh");
        btnRefresh.setFont(new Font("SansSerif", Font.PLAIN, 12));
        btnRefresh.putClientProperty("Button.arc", 999);
        btnRefresh.addActionListener(e -> refreshAllData(true));

        controls.add(txtSearch);
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
        // galleryWrapper is a panel whose layout is dynamically adjusted to
        // fit the available width without horizontal scrolling.
        galleryWrapper = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                // Required so the scroll-pane reports correct height
                return super.getPreferredSize();
            }
        };
        galleryWrapper.setOpaque(false);
        galleryWrapper.setBorder(new EmptyBorder(10, 16, 20, 16));

        // Use a 1-column GridLayout initially; columns are recomputed on resize
        galleryWrapper.setLayout(new GridLayout(0, 3, CARD_GAP, CARD_GAP));

        // Recompute columns when the panel is resized
        galleryWrapper.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                recomputeColumns();
            }
        });

        JScrollPane scroll = new JScrollPane(galleryWrapper,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        add(scroll, BorderLayout.CENTER);
    }

    /** Adjusts the GridLayout column count to fill the available width. */
    private void recomputeColumns() {
        int available = galleryWrapper.getWidth()
                - galleryWrapper.getInsets().left
                - galleryWrapper.getInsets().right;
        if (available <= 0) return;
        int cols = Math.max(1, available / (CARD_W + CARD_GAP));
        if (galleryWrapper.getLayout() instanceof GridLayout gl) {
            if (gl.getColumns() != cols) {
                gl.setColumns(cols);
                galleryWrapper.revalidate();
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  DATA
    // ════════════════════════════════════════════════════════════════════════

    public void refreshAllData() {
        refreshAllData(false);
    }

    public void refreshAllData(boolean forceRefresh) {
        hasScanned = true;
        lblStatus.setText("⏳  Scanning blueprints and local models…");
        previewCache.clear();
        loadingPaths.clear();

        if (forceRefresh) {
            modelArchitectureService.runBlueprintAnalysis();
        } else {
            new Thread(() -> {
                boolean loadedFromCache = loadScanResultsFromCache();
                if (!loadedFromCache) {
                    modelArchitectureService.runBlueprintAnalysis();
                } else {
                    scanLocalModels();
                    SwingUtilities.invokeLater(() -> {
                        applyFilter();
                        updateSummary();
                    });
                }
            }, "BlueprintGalleryRefresh").start();
        }
    }

    private boolean loadScanResultsFromCache() {
        File cacheFile = new File(configService.getAppDataPath(), "templates/comfyui/blueprint_scan_results.json");
        if (!cacheFile.exists()) {
            return false;
        }
        try {
            allEntries.clear();
            String content = Files.readString(cacheFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
            JsonNode root = mapper.readTree(content);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    String name = node.path("name").asText("");
                    String filename = node.path("filename").asText("");
                    String filePath = node.path("filePath").asText("");
                    String category = node.path("category").asText("");
                    String description = node.path("description").asText("");
                    
                    List<ModelInfo> requiredModels = new ArrayList<>();
                    JsonNode modelsNode = node.path("requiredModels");
                    if (modelsNode.isArray()) {
                        for (JsonNode mNode : modelsNode) {
                            ModelInfo info = new ModelInfo();
                            info.setName(mNode.path("name").asText(null));
                            info.setType(mNode.path("type").asText(null));
                            info.setUrl(mNode.path("url").asText(null));
                            info.setSize(mNode.path("size").asText("Unknown"));
                            info.setByteSize(mNode.path("byteSize").asLong(-1));
                            info.setBase(mNode.path("base").asText(null));
                            info.setSave_path(mNode.path("save_path").asText(null));
                            info.setDescription(mNode.path("description").asText(null));
                            requiredModels.add(info);
                        }
                    }
                    
                    String mediaType = node.path("mediaType").asText("");
                    String mediaSubtype = node.path("mediaSubtype").asText("");
                    String previewPath = node.path("previewPath").asText("");
                    
                    BlueprintEntry entry = new BlueprintEntry(name, filename, filePath, category, description, requiredModels, mediaType, mediaSubtype, previewPath);
                    allEntries.add(entry);
                }
            }
            System.out.println("📂 [BlueprintGallery] Loaded " + allEntries.size() + " blueprints from scan results JSON.");
            return true;
        } catch (Exception e) {
            System.err.println("❌ [BlueprintGallery] Failed to load blueprints from cache: " + e.getMessage());
            return false;
        }
    }


    /** Walk the configured ComfyUI model directories and collect base file names. */
    private void scanLocalModels() {
        localModelBaseNames.clear();
        String comfyPath = configService.getComfyUIPath();
        if (comfyPath == null || comfyPath.isEmpty()) return;

        Path modelsDir = Paths.get(comfyPath, "models");
        if (!Files.exists(modelsDir)) return;

        try (Stream<Path> walk = Files.walk(modelsDir)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> isSupportedModel(p.getFileName().toString()))
                .forEach(p -> localModelBaseNames.add(baseName(p.getFileName().toString())));
        } catch (IOException ignored) {}
    }

    // ════════════════════════════════════════════════════════════════════════
    //  GALLERY RENDERING
    // ════════════════════════════════════════════════════════════════════════

    private void applyFilter() {
        String filter = txtSearch.getText().toLowerCase(Locale.ROOT).trim();
        galleryWrapper.removeAll();

        List<BlueprintEntry> visible = new ArrayList<>();
        for (BlueprintEntry e : allEntries) {
            if (filter.isEmpty()
                    || (e.name != null && e.name.toLowerCase(Locale.ROOT).contains(filter))
                    || (e.category != null && e.category.toLowerCase(Locale.ROOT).contains(filter))) {
                visible.add(e);
            }
        }

        // Sort: Ready first, then Partial, then Missing. Alphabetical by name within each group.
        visible.sort((e1, e2) -> {
            BlueprintStatus s1 = computeStatus(e1);
            BlueprintStatus s2 = computeStatus(e2);
            
            int score1 = (s1.missingCount == 0) ? 0 : ((s1.presentCount > 0) ? 1 : 2);
            int score2 = (s2.missingCount == 0) ? 0 : ((s2.presentCount > 0) ? 1 : 2);
            
            if (score1 != score2) {
                return Integer.compare(score1, score2);
            }
            String n1 = e1.name != null ? e1.name : "";
            String n2 = e2.name != null ? e2.name : "";
            return n1.compareToIgnoreCase(n2);
        });

        if (visible.isEmpty()) {
            JLabel empty = new JLabel("No blueprints match your search.");
            empty.setFont(new Font("SansSerif", Font.ITALIC, 13));
            empty.setForeground(textSecondary);
            galleryWrapper.add(empty);
        } else {
            for (BlueprintEntry entry : visible) {
                galleryWrapper.add(buildCard(entry));
            }
        }

        recomputeColumns();
        galleryWrapper.revalidate();
        galleryWrapper.repaint();

        updateSummary();
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
        lblSummary.setText(total + " blueprints  ·  "
                + ready + " ready  ·  "
                + partial + " partial  ·  "
                + missing + " not installed");
        lblStatus.setText(" ");
    }

    // ── Card builder ──────────────────────────────────────────────────────────

    private JPanel buildCard(BlueprintEntry entry) {
        BlueprintStatus status = computeStatus(entry);
        Color[] palette = resolvePalette(entry);

        // ── Outer card panel with custom painting ─────────────────────────
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

        // ── Preview panel ─────────────────────────────────────────────────
        JPanel preview = buildPreviewPanel(entry, palette, status, PREVIEW_H);
        card.add(preview, BorderLayout.NORTH);

        // ── Info area ─────────────────────────────────────────────────────
        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setOpaque(false);
        info.setBorder(new EmptyBorder(8, 10, 8, 10));

        // Blueprint name (possibly multi-line if long)
        JLabel lblName = new JLabel("<html><body style='width:180px'>" +
                escapeHtml(entry.name) + "</body></html>");
        lblName.setFont(new Font("SansSerif", Font.BOLD, 12));
        lblName.setForeground(textPrimary);
        info.add(lblName);
        info.add(Box.createVerticalStrut(3));

        // Category tag
        if (!entry.category.isEmpty()) {
            String cat = entry.category.contains("/")
                    ? entry.category.substring(entry.category.lastIndexOf('/') + 1)
                    : entry.category;
            JLabel lblCat = new JLabel(cat);
            lblCat.setFont(new Font("SansSerif", Font.PLAIN, 10));
            lblCat.setForeground(textDim);
            info.add(lblCat);
            info.add(Box.createVerticalStrut(4));
        }

        // Model status line
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

        if (status.missingCount > 0) {
            JButton btnCardDl = new JButton("⬇ Download");
            btnCardDl.setFont(new Font("SansSerif", Font.BOLD, 9));
            btnCardDl.putClientProperty("Button.arc", 999);
            btnCardDl.setMargin(new Insets(2, 6, 2, 6));
            btnCardDl.setBackground(new Color(30, 40, 55));
            btnCardDl.setForeground(AMBER_PARTIAL);
            btnCardDl.setFocusable(false);
            btnCardDl.addActionListener(e -> {
                Window ownerWin = SwingUtilities.getWindowAncestor(this);
                if (ownerWin instanceof Main mainApp) {
                    List<ModelInfo> missingModels = new ArrayList<>();
                    for (ModelInfo req : entry.requiredModels) {
                        if (req.getName() != null && status.missingNames.contains(req.getName())) {
                            missingModels.add(req);
                        }
                    }
                    mainApp.focusDownloadTabAndSelectModels(missingModels);
                }
            });
            statusRow.add(btnCardDl, BorderLayout.EAST);
        }
        info.add(statusRow);

        card.add(info, BorderLayout.CENTER);
        return card;
    }

    // ── Preview panel (Java2D generated) ─────────────────────────────────────

    // ── Preview panel (Java2D / Loaded Image) ─────────────────────────────────

    private JPanel buildPreviewPanel(BlueprintEntry entry, Color[] palette,
                                     BlueprintStatus status, int height) {
        return new JPanel() {
            {
                setPreferredSize(new Dimension(CARD_W, height));
                setOpaque(false);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,   RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING,      RenderingHints.VALUE_RENDER_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int w = getWidth(), h = getHeight();
                // Clip to rounded top of card
                RoundRectangle2D clip = new RoundRectangle2D.Float(0, 0, w, h + ARC, ARC, ARC);
                g2.setClip(clip);

                Image img = previewCache.get(entry.filePath);
                if (img != null) {
                    // Paint the loaded image scaled to fit/fill
                    int imgW = img.getWidth(null);
                    int imgH = img.getHeight(null);
                    if (imgW > 0 && imgH > 0) {
                        double scale = Math.max((double) w / imgW, (double) h / imgH);
                        int newW = (int) (imgW * scale);
                        int newH = (int) (imgH * scale);
                        int x = (w - newW) / 2;
                        int y = (h - newH) / 2;
                        g2.drawImage(img, x, y, newW, newH, null);
                    } else {
                        paintBokehFallback(g2, w, h, palette, entry);
                    }
                } else {
                    triggerImageLoad(entry, this);
                    paintBokehFallback(g2, w, h, palette, entry);
                }

                // Status badge – top right corner
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

                g2.dispose();
            }
        };
    }

    private void paintBokehFallback(Graphics2D g2, int w, int h, Color[] palette, BlueprintEntry entry) {
        // Gradient background
        GradientPaint bg = new GradientPaint(0, 0, palette[0], w, h, palette[1]);
        g2.setPaint(bg);
        g2.fillRect(0, 0, w, h);

        // Bokeh circles
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

        // Subtle grid
        g2.setColor(new Color(255, 255, 255, 10));
        g2.setStroke(new BasicStroke(0.5f));
        for (int x = 0; x < w; x += 20) g2.drawLine(x, 0, x, h);
        for (int y = 0; y < h; y += 20) g2.drawLine(0, y, w, y);

        // Category icon text in center
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
        if (loadingPaths.add(entry.filePath)) {
            CompletableFuture.supplyAsync(() -> loadPreviewImage(entry))
                .thenAccept(img -> {
                    if (img != null) {
                        previewCache.put(entry.filePath, img);
                        SwingUtilities.invokeLater(() -> panelToRepaint.repaint());
                    }
                    loadingPaths.remove(entry.filePath);
                });
        }
    }

    private Image loadPreviewImage(BlueprintEntry entry) {
        if (entry.previewPath != null && !entry.previewPath.isEmpty()) {
            String pathOrUrl = entry.previewPath;
            if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
                Image img = readImageNoJavaFX(pathOrUrl);
                if (img != null) return img;
            } else {
                File f = new File(pathOrUrl);
                if (f.exists()) {
                    Image img = readImageNoJavaFX(f.toURI().toString());
                    if (img != null) return img;
                }
            }
        }

        String baseName = entry.filename;
        if (baseName.toLowerCase().endsWith(".json")) {
            baseName = baseName.substring(0, baseName.length() - 5);
        }

        // 1. Try local files in same directory first
        if (entry.filePath != null && !entry.filePath.startsWith("remote:")) {
            try {
                Path jsonPath = Paths.get(entry.filePath);
                Path parentDir = jsonPath.getParent();
                if (parentDir != null && Files.isDirectory(parentDir)) {
                    String[] extensions = {"webp", "png", "jpg", "jpeg", "gif", "mp4", "webm", "mov"};
                    for (String ext : extensions) {
                        // name-1.ext
                        Path imgPath = parentDir.resolve(baseName + "-1." + ext);
                        if (Files.exists(imgPath)) {
                            Image img = readImageNoJavaFX(imgPath.toUri().toString());
                            if (img != null) return img;
                        }
                        // name.ext
                        imgPath = parentDir.resolve(baseName + "." + ext);
                        if (Files.exists(imgPath)) {
                            Image img = readImageNoJavaFX(imgPath.toUri().toString());
                            if (img != null) return img;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[BlueprintGallery] Error reading local image for " + baseName + ": " + e.getMessage());
            }
        }

        // 2. Try fetching from ComfyUI server templates endpoint
        if (lifecycleService != null && !lifecycleService.isRunning()) {
            return null;
        }
        String comfyUrl = configService.getComfyUIUrl();
        if (comfyUrl != null && !comfyUrl.isEmpty()) {
            try {
                // First check if we have the mediaSubtype in our fetched index
                String mediaSubtype = entry.mediaSubtype;
                if (mediaSubtype != null && !mediaSubtype.isEmpty()) {
                    String imgUrl = comfyUrl + "/templates/" + java.net.URLEncoder.encode(baseName + "-1." + mediaSubtype, "UTF-8").replace("+", "%20");
                    Image img = readImageNoJavaFX(imgUrl);
                    if (img != null) return img;
                    
                    String imgUrlDirect = comfyUrl + "/templates/" + java.net.URLEncoder.encode(baseName + "." + mediaSubtype, "UTF-8").replace("+", "%20");
                    img = readImageNoJavaFX(imgUrlDirect);
                    if (img != null) return img;
                }
                
                // Fallback: search for common extensions if not in index map
                String[] extensions = {"webp", "png", "jpg", "jpeg", "gif", "mp4", "webm", "mov"};
                for (String ext : extensions) {
                    String imgUrl = comfyUrl + "/templates/" + java.net.URLEncoder.encode(baseName + "-1." + ext, "UTF-8").replace("+", "%20");
                    Image img = readImageNoJavaFX(imgUrl);
                    if (img != null) return img;
                    
                    String imgUrlDirect = comfyUrl + "/templates/" + java.net.URLEncoder.encode(baseName + "." + ext, "UTF-8").replace("+", "%20");
                    img = readImageNoJavaFX(imgUrlDirect);
                    if (img != null) return img;
                }
            } catch (Exception e) {
                // Best effort
            }
        }

        return null;
    }

    private Image readImageNoJavaFX(String urlStr) {
        File file = null;
        File tempFile = null;
        if (urlStr.startsWith("file:")) {
            try {
                file = new File(URI.create(urlStr));
            } catch (Exception ignored) {}
        }

        if (file == null) {
            byte[] bytes = fetchBytes(urlStr);
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            // Try standard ImageIO first from memory
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
                if (img != null) {
                    return img;
                }
            } catch (Exception ignored) {}

            try {
                tempFile = File.createTempFile("preview_", ".tmp");
                tempFile.deleteOnExit();
                Files.write(tempFile.toPath(), bytes);
                file = tempFile;
            } catch (Exception e) {
                System.err.println("[BlueprintGallery] Failed to write temp file: " + e.getMessage());
                return null;
            }
        }

        if (file != null && file.exists()) {
            // Try standard ImageIO first from file
            try {
                BufferedImage img = ImageIO.read(file);
                if (img != null) {
                    return img;
                }
            } catch (Exception ignored) {}

            // Ensure OpenCV/Video4j is initialized
            try {
                nu.pattern.OpenCV.loadLocally();
            } catch (Throwable ignored) {}
            try {
                Video4j.init();
            } catch (Throwable ignored) {}

            String pathStr = file.getAbsolutePath();
            String lowerPath = pathStr.toLowerCase(Locale.ROOT);
            
            // If it's a video file, try Video4j/OpenCV video frame extraction
            if (lowerPath.endsWith(".mp4") || lowerPath.endsWith(".webm") || lowerPath.endsWith(".mov")) {
                try (VideoFile video = Videos.open(pathStr)) {
                    java.util.List<VideoFrame> frames = video.streamFrames()
                            .limit(1)
                            .collect(java.util.stream.Collectors.toList());
                    if (!frames.isEmpty()) {
                        Mat mat = frames.get(0).mat();
                        if (mat != null && !mat.empty()) {
                            MatOfByte buffer = new MatOfByte();
                            Imgcodecs.imencode(".png", mat, buffer);
                            return ImageIO.read(new ByteArrayInputStream(buffer.toArray()));
                        }
                    }
                } catch (Throwable t) {
                    System.err.println("[BlueprintGallery] Video4j failed to read video: " + t.getMessage());
                }
            }

            // Try OpenCV Imgcodecs for image reading (like WebP)
            try {
                Mat mat = Imgcodecs.imread(pathStr);
                if (mat != null && !mat.empty()) {
                    MatOfByte buffer = new MatOfByte();
                    Imgcodecs.imencode(".png", mat, buffer);
                    return ImageIO.read(new ByteArrayInputStream(buffer.toArray()));
                }
            } catch (Throwable t) {
                System.err.println("[BlueprintGallery] OpenCV failed to read image: " + t.getMessage());
            } finally {
                if (tempFile != null && tempFile.exists()) {
                    tempFile.delete();
                }
            }
        }

        return null;
    }

    private byte[] fetchBytes(String urlStr) {
        if (urlStr.startsWith("http://") || urlStr.startsWith("https://")) {
            if (System.currentTimeMillis() - lastServerConnectionFailureTime < SERVER_FAILURE_COOLDOWN_MS) {
                return null;
            }
        }
        try {
            if (urlStr.startsWith("file:")) {
                return Files.readAllBytes(Paths.get(URI.create(urlStr)));
            } else {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(2000))
                        .build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(urlStr))
                        .timeout(Duration.ofMillis(5000))
                        .GET().build();
                HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 200) {
                    return response.body();
                } else if (response.statusCode() == 404) {
                    return null;
                } else {
                    return null;
                }
            }
        } catch (Exception e) {
            if (urlStr.startsWith("http://") || urlStr.startsWith("https://")) {
                lastServerConnectionFailureTime = System.currentTimeMillis();
                System.err.println("[BlueprintGallery] Failed to fetch bytes from " + urlStr + " (" + e.getClass().getSimpleName() + "): " + e.getMessage());
            } else {
                System.err.println("[BlueprintGallery] Failed to fetch bytes from " + urlStr + " (" + e.getClass().getSimpleName() + "): " + e.getMessage());
            }
        }
        return null;
    }



    // ── Detail dialog ─────────────────────────────────────────────────────────

    private void showDetails(BlueprintEntry entry, BlueprintStatus status) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dlg = new JDialog(owner instanceof Frame ? (Frame) owner : null,
                "Blueprint: " + entry.name, true);
        dlg.setSize(520, 480);
        dlg.setLocationRelativeTo(this);

        Color bg = configService.isDarkMode() ? new Color(20, 22, 30) : new Color(245, 247, 250);

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(bg);
        root.setBorder(new EmptyBorder(22, 26, 18, 26));

        // Title
        JLabel title = new JLabel(entry.name);
        title.setFont(new Font("SansSerif", Font.BOLD, 17));
        title.setForeground(textPrimary);
        root.add(title, BorderLayout.NORTH);

        // Body
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(bg);

        Image previewImg = previewCache.get(entry.filePath);
        if (previewImg != null) {
            JLabel imgLabel = new JLabel();
            imgLabel.setAlignmentX(JComponent.CENTER_ALIGNMENT);
            int targetW = 440;
            int imgW = previewImg.getWidth(null);
            int imgH = previewImg.getHeight(null);
            if (imgW > 0 && imgH > 0) {
                int targetH = (int) (imgH * ((double) targetW / imgW));
                if (targetH > 240) {
                    targetH = 240;
                    targetW = (int) (imgW * ((double) targetH / imgH));
                }
                Image scaled = previewImg.getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);
                imgLabel.setIcon(new ImageIcon(scaled));
                
                JPanel imgPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
                imgPanel.setOpaque(false);
                imgPanel.setBorder(new EmptyBorder(0, 0, 14, 0));
                imgPanel.add(imgLabel);
                body.add(imgPanel);
                dlg.setSize(520, 640);
            }
        }

        if (!entry.category.isEmpty()) addRow(body, "Category", entry.category);
        if (!entry.description.isEmpty()) {
            JLabel desc = new JLabel("<html><body style='width:400px'>" +
                    escapeHtml(entry.description) + "</body></html>");
            desc.setFont(new Font("SansSerif", Font.PLAIN, 11));
            desc.setForeground(textSecondary);
            body.add(Box.createVerticalStrut(6));
            body.add(desc);
            body.add(Box.createVerticalStrut(10));
        }

        addRow(body, "Required models", String.valueOf(entry.requiredModels.size()));
        addRow(body, "Available locally", String.valueOf(status.presentCount));
        addRow(body, "Missing", String.valueOf(status.missingCount));

        if (!status.missingNames.isEmpty()) {
            body.add(Box.createVerticalStrut(10));
            JLabel hdr = new JLabel("Missing model files:");
            hdr.setFont(new Font("SansSerif", Font.BOLD, 11));
            hdr.setForeground(RED_MISSING);
            body.add(hdr);
            body.add(Box.createVerticalStrut(4));
            for (String mn : status.missingNames) {
                JLabel ml = new JLabel("  • " + mn);
                ml.setFont(new Font("SansSerif", Font.PLAIN, 11));
                ml.setForeground(AMBER_PARTIAL);
                body.add(ml);
            }
        } else if (!entry.requiredModels.isEmpty()) {
            body.add(Box.createVerticalStrut(10));
            JLabel ok = new JLabel("✅  All required models are present.");
            ok.setFont(new Font("SansSerif", Font.BOLD, 11));
            ok.setForeground(GREEN_READY);
            body.add(ok);
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
        if (status.missingCount > 0) {
            JButton btnDl = new JButton("⬇ Download missing models");
            btnDl.setFont(new Font("SansSerif", Font.BOLD, 11));
            btnDl.putClientProperty("Button.arc", 999);
            btnDl.setBackground(new Color(30, 40, 55));
            btnDl.setForeground(AMBER_PARTIAL);
            btnDl.addActionListener(e -> {
                dlg.dispose();
                Window ownerWin = SwingUtilities.getWindowAncestor(this);
                if (ownerWin instanceof Main mainApp) {
                    List<ModelInfo> missingModels = new ArrayList<>();
                    for (ModelInfo req : entry.requiredModels) {
                        if (req.getName() != null && status.missingNames.contains(req.getName())) {
                            missingModels.add(req);
                        }
                    }
                    mainApp.focusDownloadTabAndSelectModels(missingModels);
                }
            });
            bottom.add(btnDl);
        }
        bottom.add(btnClose);
        root.add(bottom, BorderLayout.SOUTH);

        dlg.setContentPane(root);
        dlg.setVisible(true);
    }

    private void addRow(JPanel parent, String label, String value) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
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

    // ════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════════════════

    private BlueprintStatus computeStatus(BlueprintEntry entry) {
        int present = 0, missing = 0;
        List<String> missingNames = new ArrayList<>();
        for (ModelInfo req : entry.requiredModels) {
            if (req.getName() == null || req.getName().isBlank()) continue;
            if (localModelBaseNames.contains(baseName(req.getName()))) {
                present++;
            } else {
                missing++;
                missingNames.add(req.getName());
            }
        }
        return new BlueprintStatus(present, missing, missingNames);
    }

    /** Returns a palette {dark, mid, accent} based on the blueprint category. */
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

    /** Maps a category string to a representative emoji icon. */
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

    private static boolean isSupportedModel(String name) {
        String lo = name.toLowerCase(Locale.ROOT);
        return lo.endsWith(".safetensors") || lo.endsWith(".sft") || lo.endsWith(".ckpt")
                || lo.endsWith(".bin") || lo.endsWith(".pt") || lo.endsWith(".pth")
                || lo.endsWith(".onnx");
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
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Public hook called by Main when theme changes. */
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
        
        if (lblTitle != null) {
            lblTitle.setForeground(textPrimary);
        }
        if (lblSummary != null) {
            lblSummary.setForeground(textSecondary);
        }
        if (lblStatus != null) {
            lblStatus.setForeground(textSecondary);
        }
        
        applyFilter();
        repaint();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  DATA CLASSES
    // ════════════════════════════════════════════════════════════════════════

    private static class BlueprintEntry {
        final String name;
        final String filename;
        final String filePath;
        final String category;
        final String description;
        final List<ModelInfo> requiredModels;
        String mediaType = "";
        String mediaSubtype = "";
        String previewPath = "";

        BlueprintEntry(String name, String filename, String filePath,
                       String category, String description, List<ModelInfo> requiredModels) {
            this.name           = name;
            this.filename       = filename;
            this.filePath       = filePath;
            this.category       = category;
            this.description    = description;
            this.requiredModels = requiredModels;
        }

        BlueprintEntry(String name, String filename, String filePath,
                       String category, String description, List<ModelInfo> requiredModels,
                       String mediaType, String mediaSubtype, String previewPath) {
            this.name           = name;
            this.filename       = filename;
            this.filePath       = filePath;
            this.category       = category;
            this.description    = description;
            this.requiredModels = requiredModels;
            this.mediaType      = mediaType;
            this.mediaSubtype   = mediaSubtype;
            this.previewPath    = previewPath;
        }
    }

    private static class BlueprintStatus {
        final int presentCount;
        final int missingCount;
        final List<String> missingNames;

        BlueprintStatus(int presentCount, int missingCount, List<String> missingNames) {
            this.presentCount = presentCount;
            this.missingCount = missingCount;
            this.missingNames = missingNames;
        }
    }
}
