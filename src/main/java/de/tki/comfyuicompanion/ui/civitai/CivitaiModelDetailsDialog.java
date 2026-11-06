package de.tki.comfyuicompanion.ui.civitai;

import java.io.File;

import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.domain.ModelFolder;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.ICivitaiOperationalModelService;
import de.tki.comfyuicompanion.service.IDownloadManager;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.blueprint.WorkflowDialogDimensionHelper;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
import de.tki.comfyuicompanion.util.WorkflowImageLoader;
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
 * Enterprise modal dialog for Civitai workflows and models matching the visual layout, metadata rows,
 * and operational controls of official ComfyUI Blueprints.
 */
@Deprecated
public class CivitaiModelDetailsDialog extends JDialog {

    private static final Logger logger = LoggerFactory.getLogger(CivitaiModelDetailsDialog.class);

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final CivitaiModelEntry entry;
    private final ICivitaiOperationalModelService operationalModelService;
    private final IDownloadManager downloadManager;
    private final ConfigService configService;
    private final Consumer<List<ModelInfo>> onSwitchToDownloadManager;
    private final List<ModelInfo> requiredModels = new ArrayList<>();

    private JLabel previewImageLabel;
    private JTable modelTable;
    private DefaultTableModel modelTblModel;
    private JButton btnSendToComfy;

    public CivitaiModelDetailsDialog(Window owner,
                                     CivitaiModelEntry entry,
                                     ICivitaiOperationalModelService operationalModelService,
                                     IDownloadManager downloadManager,
                                     ConfigService configService,
                                     Consumer<List<ModelInfo>> onSwitchToDownloadManager) {
        super(owner instanceof Frame ? (Frame) owner : null, "Blueprint: " + entry.name(), true);
        this.entry = entry;
        this.operationalModelService = operationalModelService;
        this.downloadManager = downloadManager;
        this.configService = configService;
        this.onSwitchToDownloadManager = onSwitchToDownloadManager;

        resolveRequiredOperationalModels();

        int modelCount = requiredModels.size();
        WorkflowDialogDimensionHelper.applyOptimalSizeAndLocation(this, owner, true, 8, modelCount, false);

        initUI(owner);
        loadBannerImageAsync(owner);
    }

    public CivitaiModelDetailsDialog(Window owner,
                                     CivitaiModelEntry entry,
                                     ICivitaiOperationalModelService operationalModelService,
                                     IDownloadManager downloadManager,
                                     ConfigService configService) {
        this(owner, entry, operationalModelService, downloadManager, configService, null);
    }

    public CivitaiModelDetailsDialog(Window owner, CivitaiModelEntry entry, boolean isInstalled, Consumer<CivitaiModelEntry> onDownload) {
        this(owner, entry, null, null, null, null);
    }

    private void resolveRequiredOperationalModels() {
        if (operationalModelService != null) {
            List<ModelInfo> models = operationalModelService.resolveRequiredModels(entry);
            if (models != null && !models.isEmpty()) {
                requiredModels.addAll(models);
            }
        }

        if (requiredModels.isEmpty()) {
            ModelInfo primary = new ModelInfo();
            primary.setName(entry.filename() != null ? entry.filename() : entry.name());
            primary.setUrl(entry.downloadUrl());
            primary.setSize(entry.formattedSize());
            primary.setByteSize(entry.fileSizeBytes());
            String targetFolder = ModelFolder.fromString(entry.type()).getDefaultFolderName();
            primary.setType(targetFolder);
            primary.setSave_path(targetFolder);
            primary.setDescription("Missing");
            requiredModels.add(primary);
        }

        String baseDir = configService != null ? configService.getModelsPath() : null;
        CivitaiModelActionsHelper.checkAndFlagIncompleteModels(baseDir, requiredModels);
    }

    private void initUI(Window owner) {
        Color textPrimary = configService != null && configService.isDarkMode() ? new Color(228, 230, 240) : new Color(30, 35, 45);
        Color textSecondary = configService != null && configService.isDarkMode() ? new Color(120, 126, 152) : new Color(90, 100, 110);
        Color bg = configService != null && configService.isDarkMode() ? new Color(20, 22, 30) : new Color(245, 247, 250);

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(bg);
        root.setBorder(new EmptyBorder(18, 20, 16, 20));

        // 1. Header Bar
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);

        JLabel title = new JLabel(entry.name());
        title.setFont(new Font("SansSerif", Font.BOLD, 17));
        title.setForeground(textPrimary);
        header.add(title, BorderLayout.WEST);

        JButton btnOpenWeb = new JButton("Open on Civitai", SvgIconFactory.get(AppIcon.LINK, 13));
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

        addRow(body, "Type", entry.type(), textSecondary, textPrimary);
        if (entry.baseModel() != null && !entry.baseModel().isBlank()) {
            addRow(body, "Base Model", entry.baseModel(), textSecondary, textPrimary);
        }
        if (entry.creator() != null && !entry.creator().isBlank()) {
            addRow(body, "Creator", entry.creator(), textSecondary, textPrimary);
        }
        String stats = String.format(Locale.US, "Rating: %.1f  |  Downloads: %,d", entry.rating(), entry.downloadCount());
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
                ? "High VRAM / Large Model - Requires 16GB+ VRAM or ComfyUI --lowvram"
                : "Fits in VRAM - Standard requirements";
        Color hwColor = entry.fileSizeBytes() > 10L * 1024L * 1024L * 1024L ? new Color(239, 68, 68) : new Color(34, 197, 130);
        addRow(body, "Hardware Check", hwText, textSecondary, hwColor);

        // Custom Nodes Verification
        de.tki.comfyuicompanion.ui.dialog.CustomNodeVerificationPanel customNodesPanel =
                new de.tki.comfyuicompanion.ui.dialog.CustomNodeVerificationPanel(this, configService, textSecondary);
        addRowWithComponent(body, "Custom Nodes", customNodesPanel, textSecondary);
        if (operationalModelService != null) {
            customNodesPanel.verifyWorkflowAsync(() -> operationalModelService.generateGuiWorkflowJson(entry, requiredModels, null));
        }

        body.add(Box.createVerticalStrut(10));

        JLabel reqLbl = new JLabel("Required Models Status:");
        reqLbl.setFont(new Font("SansSerif", Font.BOLD, 11));
        reqLbl.setForeground(textPrimary);
        reqLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(reqLbl);
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

        JButton btnSaveToVault = new JButton("Save Workflow to My Vault", SvgIconFactory.get(AppIcon.ARCHIVE, 14));
        btnSaveToVault.setFont(new Font("SansSerif", Font.BOLD, 11));
        btnSaveToVault.putClientProperty("Button.arc", 999);
        btnSaveToVault.setBackground(new Color(0, 150, 136));
        btnSaveToVault.setForeground(Color.WHITE);

        File userWorkflowsDir = configService != null ? configService.getUserWorkflowsDir() : new File("user_workflows");
        File vaultDir = new File(userWorkflowsDir, "vault");
        String safeName = entry.name().replaceAll("[^a-zA-Z0-9._-]", "_");
        if (new File(vaultDir, safeName + ".json").exists()) {
            btnSaveToVault.setText("Saved in My Vault");
            btnSaveToVault.setIcon(SvgIconFactory.get(AppIcon.CHECK, 14));
            btnSaveToVault.setEnabled(false);
        }
        btnSaveToVault.addActionListener(e -> CivitaiModelActionsHelper.saveWorkflowToVault(this, entry, requiredModels, operationalModelService, btnSaveToVault));

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

        bottom.add(btnSaveToVault);
        bottom.add(btnSendToComfy);
        bottom.add(btnClose);
        root.add(bottom, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JPanel buildModelTablePanel(Color bg, Color textPrimary, Color textSecondary) {
        String[] columns = {"Model Name", "Type / Folder", "Size", "Status"};
        modelTblModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        for (ModelInfo info : requiredModels) {
            String name = info.getName();
            String folder = info.getSave_path() != null ? info.getSave_path() : (info.getType() != null ? info.getType() : "checkpoints");
            String size = info.getSize() != null && !info.getSize().isBlank() ? info.getSize() : "Unknown";
            String st = info.getDescription() != null && !info.getDescription().isBlank() ? info.getDescription() : "Missing";
            String nameLower = name != null ? name.toLowerCase(Locale.ROOT) : "";
            if ("installed".equalsIgnoreCase(info.getPopularity())) {
                st = "Already exists";
            } else if ("workflows".equalsIgnoreCase(folder) || nameLower.endsWith(".zip") || nameLower.endsWith(".json")) {
                st = "Workflow Bundle (Auto-fetched)";
            }
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
                    if (s.contains("Already") || s.contains("Installed") || s.contains("Workflow Bundle")) c.setForeground(new Color(0, 180, 160));
                    else if (s.contains("Incomplete") || s.contains("Missing")) c.setForeground(new Color(255, 153, 0));
                    else if (s.contains("Downloading")) c.setForeground(new Color(59, 130, 246));
                }
                setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
                return c;
            }
        });

        JScrollPane tblScroll = new JScrollPane(modelTable);
        int calcHeight = WorkflowDialogDimensionHelper.calculateTablePreferredHeight(requiredModels.size());
        tblScroll.setPreferredSize(new Dimension(1190, calcHeight));
        tblScroll.getViewport().setBackground(bg);
        tblScroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(tblScroll, BorderLayout.CENTER);
        return p;
    }

    private void loadBannerImageAsync(Window owner) {
        String url = entry.thumbnailUrl();
        if (url == null || url.isBlank()) return;

        Thread.ofVirtual().name("civitai-details-banner").start(() -> {
            try {
                BufferedImage img = WorkflowImageLoader.downloadAndDecodeImage(url, Duration.ofSeconds(15));
                if (img != null) {
                    SwingUtilities.invokeLater(() -> applyBannerImage(img, owner));
                }
            } catch (Exception e) {
                logger.debug("Failed to load Civitai banner: {}", e.getMessage());
            }
        });
    }

    private void applyBannerImage(BufferedImage img, Window owner) {
        int maxBannerH = WorkflowDialogDimensionHelper.calculateMaxBannerHeight(owner, 8, requiredModels.size(), false);
        int targetW = 880;
        int targetH = (int) (img.getHeight() * ((double) targetW / img.getWidth()));
        if (targetH > maxBannerH) {
            targetH = maxBannerH;
            targetW = (int) (img.getWidth() * ((double) targetH / img.getHeight()));
        }
        Image scaled = img.getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);
        previewImageLabel.setIcon(new ImageIcon(scaled));
        revalidate();
        repaint();
    }

    private void dispatchSendWorkflow() {
        CivitaiModelActionsHelper.dispatchSendWorkflow(this, entry, requiredModels, operationalModelService, btnSendToComfy, null, null, configService, this::dispose);
    }

    private void addRow(JPanel parent, String label, String value, Color labelColor, Color valueColor) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel l = new JLabel(label + ":");
        l.setFont(new Font("SansSerif", Font.BOLD, 11));
        l.setForeground(labelColor);
        l.setPreferredSize(new Dimension(140, 20));
        row.add(l, BorderLayout.WEST);

        JLabel v = new JLabel(value);
        v.setFont(new Font("SansSerif", Font.PLAIN, 11));
        v.setForeground(valueColor);
        row.add(v, BorderLayout.CENTER);
        parent.add(row);
    }

    private void addRowWithComponent(JPanel parent, String label, JComponent comp, Color labelColor) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel l = new JLabel(label + ":");
        l.setFont(new Font("SansSerif", Font.BOLD, 11));
        l.setForeground(labelColor);
        l.setPreferredSize(new Dimension(140, 20));
        row.add(l, BorderLayout.WEST);
        row.add(comp, BorderLayout.CENTER);
        parent.add(row);
    }

    private void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception e) {
            logger.warn("Could not browse URL: {}", url);
        }
    }
}
