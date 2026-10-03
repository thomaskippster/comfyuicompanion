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
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Enterprise modal inspection dialog for Civitai models, analogous to the Blueprint Details dialog.
 * Resolves all models required for operational execution (base models, VAE, CLIP, LoRAs),
 * displays a live dependency status table, enables one-click batch downloading of missing models,
 * and allows immediate "Send to ComfyUI" execution and prompt queueing.
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

    private List<ModelInfo> requiredModels = new ArrayList<>();
    private DefaultTableModel tableModel;
    private JLabel summaryLabel;
    private JButton btnDownloadMissing;
    private JButton btnSendToComfy;
    private JTextArea promptArea;

    /**
     * Full-featured constructor with operational dependency resolution and ComfyUI generation dispatching.
     *
     * @param owner                   the parent window
     * @param entry                   the Civitai model entry
     * @param operationalModelService service resolving complementary base models and synthesizing workflows
     * @param downloadManager         download manager service
     * @param configService           application configuration service
     */
    public CivitaiModelDetailsDialog(Window owner,
                                     CivitaiModelEntry entry,
                                     ICivitaiOperationalModelService operationalModelService,
                                     IDownloadManager downloadManager,
                                     ConfigService configService) {
        super(owner, "Model Hub: " + entry.name(), ModalityType.APPLICATION_MODAL);
        this.entry = entry;
        this.operationalModelService = operationalModelService;
        this.downloadManager = downloadManager;
        this.configService = configService;
        this.fallbackDownloadAction = null;

        initDialog();
    }

    /**
     * Backward-compatible convenience constructor.
     *
     * @param owner       the parent window
     * @param entry       the Civitai model entry
     * @param isInstalled whether the model is locally present
     * @param onDownload  callback when user requests to download
     */
    public CivitaiModelDetailsDialog(Window owner,
                                     CivitaiModelEntry entry,
                                     boolean isInstalled,
                                     Consumer<CivitaiModelEntry> onDownload) {
        super(owner, "Model Hub: " + entry.name(), ModalityType.APPLICATION_MODAL);
        this.entry = entry;
        this.operationalModelService = null;
        this.downloadManager = null;
        this.configService = null;
        this.fallbackDownloadAction = onDownload;

        initDialog();
    }

    private void initDialog() {
        setSize(1080, 780);
        setMinimumSize(new Dimension(850, 600));
        setLocationRelativeTo(getOwner());
        setLayout(new BorderLayout(10, 10));

        if (operationalModelService != null) {
            requiredModels = operationalModelService.resolveRequiredModels(entry);
        }

        buildUi();
    }

    private void buildUi() {
        boolean darkMode = configService == null || configService.isDarkMode();
        Color bg = darkMode ? new Color(20, 22, 30) : new Color(245, 247, 250);
        Color textPrimary = darkMode ? new Color(228, 230, 240) : new Color(30, 35, 45);
        Color textSecondary = darkMode ? new Color(130, 136, 160) : new Color(90, 100, 110);

        getContentPane().setBackground(bg);

        // --- North Header ---
        JPanel headerPanel = new JPanel(new BorderLayout(15, 0));
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(16, 20, 10, 20));

        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.setOpaque(false);

        JLabel titleLbl = new JLabel(entry.name());
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 18));
        titleLbl.setForeground(textPrimary);
        titleBlock.add(titleLbl);
        titleBlock.add(Box.createVerticalStrut(4));

        String subText = String.format("By %s  •  Type: %s  •  Base Architecture: %s  •  Size: %s  •  ⭐ %.1f",
                entry.creator(), entry.type(),
                entry.baseModel() != null && !entry.baseModel().isBlank() ? entry.baseModel() : "General",
                entry.formattedSize(), entry.rating());
        JLabel subLbl = new JLabel(subText);
        subLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subLbl.setForeground(textSecondary);
        titleBlock.add(subLbl);
        headerPanel.add(titleBlock, BorderLayout.CENTER);

        JButton civitaiWebBtn = new JButton("Open on Civitai ↗");
        civitaiWebBtn.setFont(new Font("SansSerif", Font.PLAIN, 12));
        civitaiWebBtn.addActionListener(e -> openCivitaiWebPage());
        headerPanel.add(civitaiWebBtn, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // --- Center Content ---
        JPanel bodyPanel = new JPanel(new BorderLayout(16, 12));
        bodyPanel.setOpaque(false);
        bodyPanel.setBorder(new EmptyBorder(0, 20, 10, 20));

        // Left Preview Image
        JLabel previewLabel = new JLabel();
        previewLabel.setPreferredSize(new Dimension(280, 380));
        previewLabel.setHorizontalAlignment(SwingConstants.CENTER);
        previewLabel.setBorder(BorderFactory.createLineBorder(darkMode ? new Color(50, 54, 68) : new Color(210, 215, 225)));
        previewLabel.setText("Loading preview...");
        previewLabel.setForeground(textSecondary);
        loadAsyncPreview(entry.thumbnailUrl(), previewLabel);
        bodyPanel.add(previewLabel, BorderLayout.WEST);

        // Right Workspace (Tabs / Dependency Table / Inputs)
        JPanel rightCol = new JPanel();
        rightCol.setLayout(new BoxLayout(rightCol, BoxLayout.Y_AXIS));
        rightCol.setOpaque(false);

        // Generation Prompt Area
        JPanel promptBox = new JPanel(new BorderLayout(6, 4));
        promptBox.setOpaque(false);
        promptBox.setBorder(BorderFactory.createTitledBorder("Generation Prompt (Sent to ComfyUI)"));

        String initialPrompt;
        if (entry.triggerWords() != null && !entry.triggerWords().isEmpty()) {
            initialPrompt = String.join(", ", entry.triggerWords()) + ", masterpiece, high quality, highly detailed";
        } else {
            initialPrompt = entry.name() + ", masterpiece, 8k resolution, best quality";
        }
        promptArea = new JTextArea(initialPrompt, 2, 40);
        promptArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        JScrollPane promptScroll = new JScrollPane(promptArea);
        promptScroll.setPreferredSize(new Dimension(500, 55));
        promptBox.add(promptScroll, BorderLayout.CENTER);
        rightCol.add(promptBox);
        rightCol.add(Box.createVerticalStrut(8));

        // Trigger words (if LoRA)
        if (entry.triggerWords() != null && !entry.triggerWords().isEmpty()) {
            JPanel triggerBox = new JPanel(new BorderLayout(8, 4));
            triggerBox.setOpaque(false);
            triggerBox.setBorder(BorderFactory.createTitledBorder("Trigger Words (LoRA)"));

            String joined = String.join(", ", entry.triggerWords());
            JTextField txtTriggers = new JTextField(joined);
            txtTriggers.setEditable(false);
            txtTriggers.setFont(new Font("Monospaced", Font.PLAIN, 11));
            triggerBox.add(txtTriggers, BorderLayout.CENTER);

            JButton copyBtn = new JButton("Copy", SvgIconFactory.get(AppIcon.CHECK, 12));
            copyBtn.addActionListener(e -> {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(joined), null);
                copyBtn.setText("Copied! ✅");
                Timer t = new Timer(1500, evt -> copyBtn.setText("Copy"));
                t.setRepeats(false);
                t.start();
            });
            triggerBox.add(copyBtn, BorderLayout.EAST);
            rightCol.add(triggerBox);
            rightCol.add(Box.createVerticalStrut(8));
        }

        // Operational Models Status Table
        JPanel tablePanel = new JPanel(new BorderLayout(0, 6));
        tablePanel.setOpaque(false);
        tablePanel.setBorder(BorderFactory.createTitledBorder("Required Operational Models for ComfyUI"));

        String[] cols = {"Model Name", "Target Folder", "Size", "Status"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        populateTableModel();

        JTable table = new JTable(tableModel);
        table.setRowHeight(26);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFont(new Font("SansSerif", Font.PLAIN, 12));

        table.getColumnModel().getColumn(0).setPreferredWidth(320);
        table.getColumnModel().getColumn(1).setPreferredWidth(120);
        table.getColumnModel().getColumn(2).setPreferredWidth(100);
        table.getColumnModel().getColumn(3).setPreferredWidth(180);

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean isSel, boolean hasFoc, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, val, isSel, hasFoc, row, col);
                c.setBackground(row % 2 == 0 ? bg : new Color(Math.max(0, bg.getRed() - 6), Math.max(0, bg.getGreen() - 6), Math.max(0, bg.getBlue() - 6)));
                c.setForeground(textPrimary);

                if (col == 3 && val != null) {
                    String st = val.toString();
                    if (st.contains("Already exists") || st.contains("Installed") || st.contains("Finished")) {
                        c.setForeground(new Color(34, 197, 130)); // Green
                    } else if (st.contains("Downloading") || st.contains("Starting")) {
                        c.setForeground(new Color(245, 158, 11)); // Amber
                    } else if (st.contains("Missing")) {
                        c.setForeground(new Color(59, 130, 246)); // Blue
                    } else if (st.contains("Error") || st.contains("Failed")) {
                        c.setForeground(new Color(239, 68, 68)); // Red
                    }
                }
                setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
                return c;
            }
        });

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(500, 160));
        tablePanel.add(tableScroll, BorderLayout.CENTER);

        summaryLabel = new JLabel(" ");
        summaryLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        summaryLabel.setForeground(textSecondary);
        updateSummaryText();
        tablePanel.add(summaryLabel, BorderLayout.SOUTH);

        rightCol.add(tablePanel);
        bodyPanel.add(rightCol, BorderLayout.CENTER);
        add(bodyPanel, BorderLayout.CENTER);

        // --- South Actions Bar ---
        JPanel southBar = new JPanel(new BorderLayout(10, 0));
        southBar.setOpaque(false);
        southBar.setBorder(new EmptyBorder(10, 20, 16, 20));

        ModelFolder folder = ModelFolder.fromString(entry.type());
        String folderName = folder != null ? folder.getDefaultFolderName() : "models";
        JLabel targetInfo = new JLabel(String.format("Destination: models/%s/%s", folderName, entry.filename()));
        targetInfo.setFont(new Font("Monospaced", Font.PLAIN, 11));
        targetInfo.setForeground(textSecondary);
        southBar.add(targetInfo, BorderLayout.WEST);

        JPanel buttonGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonGroup.setOpaque(false);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        buttonGroup.add(closeBtn);

        btnDownloadMissing = new JButton("Download Required Models", SvgIconFactory.get(AppIcon.DOWNLOAD, 14));
        btnDownloadMissing.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnDownloadMissing.addActionListener(e -> startMissingModelsDownload());
        buttonGroup.add(btnDownloadMissing);

        btnSendToComfy = new JButton("Send to ComfyUI & Generate", SvgIconFactory.get(AppIcon.LAUNCH, 14));
        btnSendToComfy.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnSendToComfy.putClientProperty("JButton.buttonType", "accent");
        btnSendToComfy.addActionListener(e -> dispatchToComfyUI());
        buttonGroup.add(btnSendToComfy);

        updateButtonsState();

        southBar.add(buttonGroup, BorderLayout.EAST);
        add(southBar, BorderLayout.SOUTH);
    }

    private void populateTableModel() {
        tableModel.setRowCount(0);
        for (ModelInfo info : requiredModels) {
            String name = info.getFilename() != null && !info.getFilename().isBlank() ? info.getFilename() : info.getName();
            String type = info.getSave_path() != null ? info.getSave_path() : (info.getType() != null ? info.getType() : "checkpoints");
            String size = info.getSize() != null ? info.getSize() : "Unknown";
            String status = info.getDescription() != null ? info.getDescription() : "Missing";
            tableModel.addRow(new Object[]{name, type, size, status});
        }
    }

    private void updateSummaryText() {
        int total = requiredModels.size();
        int present = 0;
        int missing = 0;
        for (ModelInfo info : requiredModels) {
            if ("installed".equalsIgnoreCase(info.getPopularity()) || (info.getDescription() != null && info.getDescription().contains("Already exists"))) {
                present++;
            } else {
                missing++;
            }
        }
        if (summaryLabel != null) {
            summaryLabel.setText(String.format("Required models: %d  •  Available locally: %d  •  Missing: %d", total, present, missing));
        }
    }

    private void updateButtonsState() {
        int missingCount = getMissingCount();
        if (missingCount == 0) {
            btnDownloadMissing.setEnabled(true);
            btnDownloadMissing.setText("All Models Installed ✅");
            btnDownloadMissing.setIcon(SvgIconFactory.get(AppIcon.CHECK, 14));

            btnSendToComfy.setEnabled(true);
            btnSendToComfy.setToolTipText("Launch generation workflow directly into ComfyUI");
        } else {
            btnDownloadMissing.setEnabled(true);
            btnDownloadMissing.setText("Download Required Models (" + missingCount + " missing)");
            btnDownloadMissing.setIcon(SvgIconFactory.get(AppIcon.DOWNLOAD, 14));

            btnSendToComfy.setEnabled(false);
            btnSendToComfy.setToolTipText("Please download all required models before sending to ComfyUI.");
        }
    }

    private int getMissingCount() {
        int count = 0;
        for (ModelInfo info : requiredModels) {
            if (!"installed".equalsIgnoreCase(info.getPopularity()) && (info.getDescription() == null || !info.getDescription().contains("Already exists"))) {
                count++;
            }
        }
        return count;
    }

    private void startMissingModelsDownload() {
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

        btnDownloadMissing.setEnabled(false);
        btnDownloadMissing.setText("Downloading Models...");
        btnDownloadMissing.setIcon(SvgIconFactory.get(AppIcon.REFRESH, 14));

        boolean[] selection = new boolean[missing.size()];
        for (int i = 0; i < selection.length; i++) selection[i] = true;

        String baseDir = configService != null ? configService.getModelsPath() : "models";

        downloadManager.startQueue(
                missing,
                selection,
                baseDir,
                (index, status) -> SwingUtilities.invokeLater(() -> {
                    if (index >= 0 && index < missing.size()) {
                        ModelInfo currentInfo = missing.get(index);
                        for (int r = 0; r < tableModel.getRowCount(); r++) {
                            if (tableModel.getValueAt(r, 0).equals(currentInfo.getFilename()) ||
                                tableModel.getValueAt(r, 0).equals(currentInfo.getName())) {
                                tableModel.setValueAt(status, r, 3);
                                break;
                            }
                        }
                    }
                }),
                () -> SwingUtilities.invokeLater(() -> {
                    for (ModelInfo m : missing) {
                        m.setPopularity("installed");
                        m.setDescription("✅ Installed");
                    }
                    populateTableModel();
                    updateSummaryText();
                    updateButtonsState();
                    downloadManager.notifyComfyUI(true);
                    JOptionPane.showMessageDialog(this,
                            "All operational models successfully downloaded!\nYou can now send the workflow to ComfyUI.",
                            "Download Finished", JOptionPane.INFORMATION_MESSAGE);
                })
        );
    }

    private void dispatchToComfyUI() {
        if (operationalModelService == null) {
            return;
        }

        btnSendToComfy.setEnabled(false);
        btnSendToComfy.setText("Starting & Sending to ComfyUI...");
        btnSendToComfy.setIcon(SvgIconFactory.get(AppIcon.REFRESH, 14));

        String customPrompt = promptArea != null ? promptArea.getText().trim() : null;

        operationalModelService.sendToComfyUIAndGenerate(entry, requiredModels, customPrompt)
                .thenAccept(success -> SwingUtilities.invokeLater(() -> {
                    btnSendToComfy.setEnabled(true);
                    btnSendToComfy.setText("Send to ComfyUI & Generate");
                    btnSendToComfy.setIcon(SvgIconFactory.get(AppIcon.LAUNCH, 14));

                    JOptionPane.showMessageDialog(this,
                            "Workflow for '" + entry.name() + "' successfully loaded into ComfyUI!\nGeneration prompt queued and running.",
                            "ComfyUI Generation Started", JOptionPane.INFORMATION_MESSAGE);
                    dispose();
                }))
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        btnSendToComfy.setEnabled(true);
                        btnSendToComfy.setText("Send to ComfyUI & Generate");
                        btnSendToComfy.setIcon(SvgIconFactory.get(AppIcon.LAUNCH, 14));

                        String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                        JOptionPane.showMessageDialog(this,
                                "Failed to send workflow to ComfyUI:\n" + msg,
                                "ComfyUI Error", JOptionPane.ERROR_MESSAGE);
                    });
                    return null;
                });
    }

    private void openCivitaiWebPage() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI("https://civitai.com/models/" + entry.id()));
            }
        } catch (Exception ex) {
            logger.warn("Could not launch web browser: {}", ex.getMessage());
            JOptionPane.showMessageDialog(this, "URL: https://civitai.com/models/" + entry.id(), "Civitai URL", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void loadAsyncPreview(String url, JLabel target) {
        if (url == null || url.isBlank()) {
            target.setText("No preview image available");
            return;
        }

        Thread.ofVirtual().name("Civitai-DetailPreview").start(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(12)).GET().build();
                HttpResponse<InputStream> res = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (res.statusCode() == 200) {
                    try (InputStream in = res.body()) {
                        BufferedImage img = ImageIO.read(in);
                        if (img != null) {
                            int targetW = 280;
                            int targetH = 380;
                            Image scaled = img.getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);
                            SwingUtilities.invokeLater(() -> {
                                target.setText(null);
                                target.setIcon(new ImageIcon(scaled));
                            });
                        }
                    }
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> target.setText("Preview unavailable"));
            }
        });
    }
}
