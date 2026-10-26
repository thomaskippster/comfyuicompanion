package de.tki.comfyuicompanion.ui.civitai;

import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.domain.ModelFolder;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.ICivitaiOperationalModelService;
import de.tki.comfyuicompanion.service.IDownloadManager;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Enterprise modal dialog for Civitai models matching the visual layout, metadata rows,
 * required model status table, and launch mechanics of the Comfy.org BlueprintDetailsDialog.
 */
public class CivitaiModelDetailsDialog extends JDialog {

    private static final Logger logger = LoggerFactory.getLogger(CivitaiModelDetailsDialog.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final CivitaiModelEntry entry;
    private final ICivitaiOperationalModelService operationalModelService;
    private final IDownloadManager downloadManager;
    private final ConfigService configService;
    private final Consumer<CivitaiModelEntry> fallbackDownloadAction;
    private final Consumer<List<ModelInfo>> onSwitchToDownloadManager;

    private List<ModelInfo> requiredModels = new ArrayList<>();
    private DefaultTableModel modelTblModel;
    private JTable modelTable;
    private JLabel previewImageLabel;
    private JButton btnDownloadMissing;
    private JButton btnSendToComfy;

    /**
     * Constructs a new dialog matching BlueprintDetailsDialog conventions.
     */
    public CivitaiModelDetailsDialog(Window owner,
                                     CivitaiModelEntry entry,
                                     ICivitaiOperationalModelService operationalModelService,
                                     IDownloadManager downloadManager,
                                     ConfigService configService,
                                     Consumer<List<ModelInfo>> onSwitchToDownloadManager) {
        super(owner instanceof Frame ? (Frame) owner : null, "Civitai Model: " + entry.name(), true);
        this.entry = entry;
        this.operationalModelService = operationalModelService;
        this.downloadManager = downloadManager;
        this.configService = configService;
        this.fallbackDownloadAction = null;
        this.onSwitchToDownloadManager = onSwitchToDownloadManager;

        initDialog();
    }

    /**
     * Backward-compatible convenience constructor.
     */
    public CivitaiModelDetailsDialog(Window owner,
                                     CivitaiModelEntry entry,
                                     ICivitaiOperationalModelService operationalModelService,
                                     IDownloadManager downloadManager,
                                     ConfigService configService) {
        this(owner, entry, operationalModelService, downloadManager, configService, null);
    }

    /**
     * Minimal fallback constructor for legacy unit tests.
     */
    public CivitaiModelDetailsDialog(Window owner, CivitaiModelEntry entry, boolean isInstalled, Consumer<CivitaiModelEntry> onDownload) {
        super(owner instanceof Frame ? (Frame) owner : null, "Civitai Model: " + entry.name(), true);
        this.entry = entry;
        this.operationalModelService = null;
        this.downloadManager = null;
        this.configService = null;
        this.fallbackDownloadAction = onDownload;
        this.onSwitchToDownloadManager = null;

        initDialog();
    }

    private void initDialog() {
        setSize(1250, 760);
        setLocationRelativeTo(getOwner());
        setResizable(true);

        boolean darkMode = configService == null || configService.isDarkMode();
        Color bg = darkMode ? new Color(20, 22, 30) : new Color(245, 247, 250);
        Color textPrimary = darkMode ? new Color(228, 230, 240) : new Color(30, 35, 45);
        Color textSecondary = darkMode ? new Color(120, 126, 152) : new Color(90, 100, 110);

        if (operationalModelService != null) {
            this.requiredModels = operationalModelService.resolveRequiredModels(entry);
        } else {
            ModelFolder folder = ModelFolder.fromString(entry.type());
            ModelInfo info = new ModelInfo(folder.getDefaultFolderName(), entry.name(), entry.downloadUrl());
            info.setFilename(entry.filename());
            info.setSize(entry.formattedSize());
            info.setByteSize(entry.fileSizeBytes());
            info.setDescription("Missing");
            this.requiredModels = new ArrayList<>(List.of(info));
        }

        String baseDir = configService != null ? configService.getModelsPath() : "models";
        CivitaiModelActionsHelper.checkAndFlagIncompleteModels(baseDir, requiredModels);

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(bg);
        root.setBorder(new EmptyBorder(18, 20, 16, 20));

        // 1. Header (Title + Open External Link)
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);

        JLabel title = new JLabel(entry.name());
        title.setFont(new Font("SansSerif", Font.BOLD, 17));
        title.setForeground(textPrimary);
        header.add(title, BorderLayout.WEST);

        JButton btnOpenWeb = new JButton("Open on Civitai ↗", SvgIconFactory.get(AppIcon.LINK, 13));
        btnOpenWeb.setFont(new Font("SansSerif", Font.PLAIN, 11));
        btnOpenWeb.putClientProperty("Button.arc", 999);
        btnOpenWeb.addActionListener(e -> openUrl("https://civitai.com/models/" + entry.id()));
        header.add(btnOpenWeb, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // 2. Scrollable Body
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(bg);

        // Preview Image Banner
        previewImageLabel = new JLabel();
        previewImageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        JPanel imgPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        imgPanel.setOpaque(false);
        imgPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        imgPanel.setBorder(new EmptyBorder(0, 0, 14, 0));
        imgPanel.add(previewImageLabel);
        body.add(imgPanel);
        loadAsyncPreviewBanner(entry.thumbnailUrl(), previewImageLabel);

        // Metadata Rows
        addRow(body, "Type", entry.type(), textSecondary, textPrimary);
        if (entry.baseModel() != null && !entry.baseModel().isBlank()) {
            addRow(body, "Base Model", entry.baseModel(), textSecondary, textPrimary);
        }
        if (entry.creator() != null && !entry.creator().isBlank()) {
            addRow(body, "Creator", entry.creator(), textSecondary, textPrimary);
        }
        String stats = String.format(Locale.US, "⭐ %.1f  •  ⬇ %,d", entry.rating(), entry.downloadCount());
        addRow(body, "Community Stats", stats, textSecondary, textPrimary);

        if (entry.triggerWords() != null && !entry.triggerWords().isEmpty()) {
            addRow(body, "Trigger Words", String.join(", ", entry.triggerWords()), textSecondary, textPrimary);
        }

        int totalReq = requiredModels.size();
        int missingCount = operationalModelService != null
                ? operationalModelService.getMissingModels(requiredModels).size()
                : (entry.filename() != null ? 1 : 0);
        int presentCount = Math.max(0, totalReq - missingCount);

        addRow(body, "Required models", String.valueOf(totalReq), textSecondary, textPrimary);
        addRow(body, "Available locally", String.valueOf(presentCount), textSecondary, textPrimary);
        addRow(body, "Missing", String.valueOf(missingCount), textSecondary, missingCount == 0 ? new Color(34, 197, 130) : new Color(255, 153, 0));

        // Hardware Compatibility Check
        String hwText = entry.fileSizeBytes() > 10L * 1024L * 1024L * 1024L
                ? "🔴 High VRAM / Large Model — Requires 16GB+ VRAM or ComfyUI --lowvram"
                : "🟢 Fits in VRAM — Standard requirements";
        Color hwColor = entry.fileSizeBytes() > 10L * 1024L * 1024L * 1024L ? new Color(239, 68, 68) : new Color(34, 197, 130);
        addRow(body, "Hardware Check", hwText, textSecondary, hwColor);

        // Required Models Table
        body.add(Box.createVerticalStrut(12));
        JLabel tblHdr = new JLabel("Required Models Status:");
        tblHdr.setFont(new Font("SansSerif", Font.PLAIN, 12));
        tblHdr.setForeground(textPrimary);
        tblHdr.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(tblHdr);
        body.add(Box.createVerticalStrut(6));

        body.add(buildModelTablePanel(bg, textPrimary, textSecondary));

        JScrollPane scroll = new JScrollPane(body, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        root.add(scroll, BorderLayout.CENTER);

        // 3. Bottom Actions Bar
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        bottom.setBackground(bg);

        btnDownloadMissing = new JButton("Download Missing Models", SvgIconFactory.get(AppIcon.DOWNLOAD, 14));
        btnDownloadMissing.setFont(new Font("SansSerif", Font.BOLD, 11));
        btnDownloadMissing.putClientProperty("Button.arc", 999);
        btnDownloadMissing.setBackground(new Color(0, 150, 136));
        btnDownloadMissing.setForeground(Color.WHITE);
        if (missingCount == 0) {
            btnDownloadMissing.setText("All Models Installed ✅");
            btnDownloadMissing.setEnabled(false);
        }
        btnDownloadMissing.addActionListener(e -> startMissingDownload());

        btnSendToComfy = new JButton("Send to ComfyUI", SvgIconFactory.get(AppIcon.SEND, 14));
        btnSendToComfy.setFont(new Font("SansSerif", Font.BOLD, 11));
        btnSendToComfy.putClientProperty("Button.arc", 999);
        btnSendToComfy.setBackground(new Color(33, 150, 243));
        btnSendToComfy.setForeground(Color.WHITE);
        if (missingCount > 0) {
            btnSendToComfy.setEnabled(false);
            btnSendToComfy.setToolTipText("Please download all missing models before sending to ComfyUI.");
        }
        btnSendToComfy.addActionListener(e -> dispatchSendWorkflow());

        JButton btnClose = new JButton("Close");
        btnClose.setFont(new Font("SansSerif", Font.PLAIN, 11));
        btnClose.addActionListener(e -> dispose());

        bottom.add(btnDownloadMissing);
        bottom.add(btnSendToComfy);
        bottom.add(btnClose);
        root.add(bottom, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JPanel buildModelTablePanel(Color bg, Color textPrimary, Color textSecondary) {
        String[] columns = {"Model Name", "Type / Folder", "Size", "Status"};
        modelTblModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        for (ModelInfo info : requiredModels) {
            String name = info.getFilename() != null && !info.getFilename().isBlank() ? info.getFilename() : info.getName();
            String folder = info.getSave_path() != null ? info.getSave_path() : (info.getType() != null ? info.getType() : "checkpoints");
            String size = info.getSize() != null && !info.getSize().isBlank() ? info.getSize() : "Unknown";
            String st = info.getDescription() != null && !info.getDescription().isBlank() ? info.getDescription() : "Missing";
            if ("installed".equalsIgnoreCase(info.getPopularity())) st = "✅ Already exists";
            modelTblModel.addRow(new Object[]{name, folder, size, st});
        }

        modelTable = new JTable(modelTblModel);
        modelTable.setFont(new Font("SansSerif", Font.PLAIN, 11));
        modelTable.setRowHeight(24);
        modelTable.setShowGrid(false);
        modelTable.setBackground(bg);
        modelTable.setForeground(textPrimary);
        modelTable.getTableHeader().setBackground(bg);
        modelTable.getTableHeader().setForeground(textSecondary);
        modelTable.getTableHeader().setFont(new Font("SansSerif", Font.PLAIN, 11));

        modelTable.getColumnModel().getColumn(0).setPreferredWidth(580);
        modelTable.getColumnModel().getColumn(1).setPreferredWidth(160);
        modelTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        modelTable.getColumnModel().getColumn(3).setPreferredWidth(260);

        modelTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean isSel, boolean hasFoc, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, val, isSel, hasFoc, row, col);
                c.setBackground(row % 2 == 0 ? bg : new Color(Math.max(0, bg.getRed() - 5), Math.max(0, bg.getGreen() - 5), Math.max(0, bg.getBlue() - 5)));
                c.setForeground(textPrimary);
                if (col == 3 && val != null) {
                    String s = val.toString();
                    if (s.contains("Already") || s.contains("Installed")) c.setForeground(new Color(0, 180, 160));
                    else if (s.contains("Incomplete") || s.contains("Missing")) c.setForeground(new Color(255, 153, 0));
                    else if (s.contains("Downloading")) c.setForeground(new Color(59, 130, 246));
                }
                setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
                return c;
            }
        });

        JScrollPane tblScroll = new JScrollPane(modelTable);
        int calcHeight = Math.min(220, Math.max(80, (requiredModels.size() + 1) * 26 + 6));
        tblScroll.setPreferredSize(new Dimension(1190, calcHeight));
        tblScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, calcHeight));
        tblScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        tblScroll.getViewport().setBackground(bg);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(tblScroll, BorderLayout.CENTER);
        return panel;
    }

    private void startMissingDownload() {
        if (downloadManager == null) {
            if (fallbackDownloadAction != null) {
                fallbackDownloadAction.accept(entry);
                dispose();
            }
            return;
        }

        List<ModelInfo> missing = operationalModelService != null
                ? operationalModelService.getMissingModels(requiredModels)
                : requiredModels;

        if (missing.isEmpty()) {
            JOptionPane.showMessageDialog(this, "All required models are already installed locally!", "Information", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        if (onSwitchToDownloadManager != null) {
            dispose();
            onSwitchToDownloadManager.accept(missing);
        } else {
            CivitaiModelActionsHelper.executeDownloadQueue(
                    this, missing, downloadManager, configService, operationalModelService,
                    (info, status) -> {}, () -> {}
            );
        }
    }

    private void dispatchSendWorkflow() {
        if (operationalModelService == null) return;
        btnSendToComfy.setEnabled(false);
        btnSendToComfy.setText("Sending...");
        String prompt = entry.triggerWords() != null && !entry.triggerWords().isEmpty() ? entry.triggerWords().get(0) : "";
        CivitaiModelActionsHelper.dispatchSendWorkflow(
                this, entry, requiredModels, prompt, operationalModelService, configService,
                btnSendToComfy, null, this::dispose
        );
    }

    private static void loadAsyncPreviewBanner(String url, JLabel target) {
        if (url == null || url.isBlank()) return;
        Thread.ofVirtual().name("CivitaiBannerLoader").start(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
                HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() == 200) {
                    try (InputStream in = response.body()) {
                        BufferedImage orig = ImageIO.read(in);
                        if (orig != null) {
                            int targetW = Math.min(1100, orig.getWidth());
                            int targetH = (int) (orig.getHeight() * ((double) targetW / orig.getWidth()));
                            if (targetH > 280) {
                                targetH = 280;
                                targetW = (int) (orig.getWidth() * ((double) targetH / orig.getHeight()));
                            }
                            Image scaled = orig.getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);
                            SwingUtilities.invokeLater(() -> target.setIcon(new ImageIcon(scaled)));
                        }
                    }
                }
            } catch (Exception ignored) {
                logger.debug("Failed loading banner image: {}", ignored.getMessage());
            }
        });
    }

    private static void addRow(JPanel parent, String label, String value, Color textSecondary, Color textPrimary) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

        JLabel lbl = new JLabel(label + ":");
        lbl.setFont(new Font("SansSerif", Font.BOLD, 11));
        lbl.setForeground(textSecondary);
        lbl.setPreferredSize(new Dimension(140, 18));

        JLabel val = new JLabel(value);
        val.setFont(new Font("SansSerif", Font.PLAIN, 11));
        val.setForeground(textPrimary);

        row.add(lbl, BorderLayout.WEST);
        row.add(val, BorderLayout.CENTER);
        parent.add(row);
        parent.add(Box.createVerticalStrut(3));
    }

    private static void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception e) {
            logger.warn("Could not open external URL {}: {}", url, e.getMessage());
        }
    }
}
