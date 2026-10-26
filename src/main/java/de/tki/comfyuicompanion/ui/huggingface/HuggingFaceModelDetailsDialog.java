package de.tki.comfyuicompanion.ui.huggingface;

import de.tki.comfyuicompanion.domain.HuggingFaceFileEntry;
import de.tki.comfyuicompanion.domain.HuggingFaceModelEntry;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.IDownloadManager;
import de.tki.comfyuicompanion.service.IHuggingFaceHubService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.HuggingFaceWorkflowDispatcher;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Enterprise modal details dialog for inspecting Hugging Face repositories,
 * verifying all required local model weights, and dispatching workflows to ComfyUI.
 * Fully aligned with Comfy.org blueprints and {@link de.tki.comfyuicompanion.ui.blueprint.BlueprintDetailsDialog}.
 */
public class HuggingFaceModelDetailsDialog extends JDialog {

    private static final Logger logger = LoggerFactory.getLogger(HuggingFaceModelDetailsDialog.class);

    private final HuggingFaceModelEntry repo;
    private final IHuggingFaceHubService hubService;
    private final IDownloadManager downloadManager;
    private final ConfigService configService;
    private final HuggingFaceWorkflowDispatcher workflowDispatcher;
    private final Function<HuggingFaceFileEntry, String> statusChecker;
    private final Consumer<List<ModelInfo>> onSwitchToDownloadManager;

    private List<HuggingFaceFileEntry> files = new ArrayList<>();
    private DefaultTableModel filesTableModel;
    private JTable filesTable;
    private JButton btnDownloadModel;
    private JButton btnSendToComfy;
    private JLabel previewImageLabel;
    private JLabel lblAvailableFilesCount;
    private JLabel lblInstalledCount;
    private JLabel lblMissingCount;
    private JLabel lblSelectedFileName;
    private JLabel lblSelectedFileStatus;
    private JLabel lblHardwareCheck;

    /**
     * Constructs a new unified HuggingFaceModelDetailsDialog.
     *
     * @param owner                     parent window
     * @param repo                      Hugging Face repository entry
     * @param hubService                Hugging Face service for fetching file trees
     * @param downloadManager           download manager service
     * @param configService             application configuration service
     * @param workflowDispatcher        dispatcher for sending workflows to ComfyUI
     * @param statusChecker             function resolving local status for a file entry
     * @param onSwitchToDownloadManager callback to switch to Download Manager tab
     */
    public HuggingFaceModelDetailsDialog(
            Window owner,
            HuggingFaceModelEntry repo,
            IHuggingFaceHubService hubService,
            IDownloadManager downloadManager,
            ConfigService configService,
            HuggingFaceWorkflowDispatcher workflowDispatcher,
            Function<HuggingFaceFileEntry, String> statusChecker,
            Consumer<List<ModelInfo>> onSwitchToDownloadManager) {
        super(owner instanceof Frame f ? f : null, "Blueprint: " + repo.repoId(), true);
        this.repo = repo;
        this.hubService = hubService;
        this.downloadManager = downloadManager;
        this.configService = configService;
        this.workflowDispatcher = workflowDispatcher;
        this.statusChecker = statusChecker != null ? statusChecker : (f -> "Missing");
        this.onSwitchToDownloadManager = onSwitchToDownloadManager;

        setSize(1250, 760);
        setLocationRelativeTo(owner);

        boolean darkMode = configService == null || configService.isDarkMode();
        Color textPrimary = darkMode ? new Color(228, 230, 240) : new Color(30, 35, 45);
        Color textSecondary = darkMode ? new Color(120, 126, 152) : new Color(90, 100, 110);
        Color bg = darkMode ? new Color(20, 22, 30) : new Color(245, 247, 250);

        if (repo.files() != null && !repo.files().isEmpty()) {
            this.files = new ArrayList<>(repo.files());
            this.files.sort((a, b) -> Boolean.compare(!isPrimaryModelFile(a), !isPrimaryModelFile(b)));
        }

        // Initialize action buttons early
        btnDownloadModel = new JButton("Download Missing Models", SvgIconFactory.get(AppIcon.DOWNLOAD, 14));
        btnDownloadModel.setFont(new Font("SansSerif", Font.BOLD, 11));
        btnDownloadModel.putClientProperty("Button.arc", 999);
        btnDownloadModel.setBackground(new Color(0, 150, 136));
        btnDownloadModel.setForeground(Color.WHITE);
        btnDownloadModel.addActionListener(e -> startMissingDownload());

        btnSendToComfy = new JButton("Send to ComfyUI", SvgIconFactory.get(AppIcon.SEND, 14));
        btnSendToComfy.setFont(new Font("SansSerif", Font.BOLD, 11));
        btnSendToComfy.putClientProperty("Button.arc", 999);
        btnSendToComfy.setBackground(new Color(33, 150, 243));
        btnSendToComfy.setForeground(Color.WHITE);
        btnSendToComfy.setEnabled(false);
        btnSendToComfy.setToolTipText("Please download all missing models before sending to ComfyUI.");
        btnSendToComfy.addActionListener(e -> dispatchSendWorkflow());

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(bg);
        root.setBorder(new EmptyBorder(18, 20, 16, 20));

        // 1. Header (Title + Open External Link)
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);

        JLabel title = new JLabel(repo.repoId());
        title.setFont(new Font("SansSerif", Font.BOLD, 17));
        title.setForeground(textPrimary);
        header.add(title, BorderLayout.WEST);

        JButton btnOpenWeb = new JButton("Open on Hugging Face ↗", SvgIconFactory.get(AppIcon.LINK, 13));
        btnOpenWeb.setFont(new Font("SansSerif", Font.PLAIN, 11));
        btnOpenWeb.putClientProperty("Button.arc", 999);
        btnOpenWeb.addActionListener(e -> HuggingFaceDetailsActionsHandler.openUrl("https://huggingface.co/" + repo.repoId()));
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
        HuggingFaceDetailsActionsHandler.loadAsyncPreviewBanner(repo, darkMode, previewImageLabel);

        // Metadata Rows
        HuggingFaceDetailsActionsHandler.addRow(body, "Repository", repo.repoId(), textSecondary, textPrimary);
        HuggingFaceDetailsActionsHandler.addRow(body, "Author", repo.author(), textSecondary, textPrimary);
        String taskStr = repo.pipelineTag() != null && !repo.pipelineTag().isBlank() ? repo.pipelineTag() : "Weights";
        HuggingFaceDetailsActionsHandler.addRow(body, "Pipeline Task", taskStr, textSecondary, textPrimary);
        String stats = String.format(Locale.US, "❤️ %,d Likes  •  📥 %,d Downloads", repo.likes(), repo.downloads());
        HuggingFaceDetailsActionsHandler.addRow(body, "Community Stats", stats, textSecondary, textPrimary);

        lblAvailableFilesCount = new JLabel(String.valueOf(files.size()));
        lblAvailableFilesCount.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lblAvailableFilesCount.setForeground(textPrimary);
        HuggingFaceDetailsActionsHandler.addRowWithComponent(body, "Required / Available files", lblAvailableFilesCount, textSecondary);

        lblInstalledCount = new JLabel("0");
        lblInstalledCount.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lblInstalledCount.setForeground(textPrimary);
        HuggingFaceDetailsActionsHandler.addRowWithComponent(body, "Available locally", lblInstalledCount, textSecondary);

        lblMissingCount = new JLabel("0");
        lblMissingCount.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lblMissingCount.setForeground(new Color(255, 153, 0));
        HuggingFaceDetailsActionsHandler.addRowWithComponent(body, "Missing", lblMissingCount, textSecondary);

        lblSelectedFileName = new JLabel("Select a file in the table below");
        lblSelectedFileName.setFont(new Font("Monospaced", Font.PLAIN, 11));
        lblSelectedFileName.setForeground(textPrimary);
        HuggingFaceDetailsActionsHandler.addRowWithComponent(body, "Selected File", lblSelectedFileName, textSecondary);

        lblSelectedFileStatus = new JLabel("—");
        lblSelectedFileStatus.setFont(new Font("SansSerif", Font.BOLD, 11));
        lblSelectedFileStatus.setForeground(textSecondary);
        HuggingFaceDetailsActionsHandler.addRowWithComponent(body, "File Status", lblSelectedFileStatus, textSecondary);

        lblHardwareCheck = new JLabel("🟢 Standard VRAM requirements");
        lblHardwareCheck.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lblHardwareCheck.setForeground(new Color(34, 197, 130));
        HuggingFaceDetailsActionsHandler.addRowWithComponent(body, "Hardware Check", lblHardwareCheck, textSecondary);

        // Files Table
        body.add(Box.createVerticalStrut(12));
        JLabel tblHdr = new JLabel("Required Models Status:");
        tblHdr.setFont(new Font("SansSerif", Font.PLAIN, 12));
        tblHdr.setForeground(textPrimary);
        tblHdr.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(tblHdr);
        body.add(Box.createVerticalStrut(6));

        body.add(buildFilesTablePanel(bg, textPrimary, textSecondary));

        JScrollPane scroll = new JScrollPane(body, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        root.add(scroll, BorderLayout.CENTER);

        // 3. Bottom Actions Bar
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        bottom.setBackground(bg);

        JButton btnClose = new JButton("Close");
        btnClose.setFont(new Font("SansSerif", Font.PLAIN, 11));
        btnClose.addActionListener(e -> dispose());

        bottom.add(btnDownloadModel);
        bottom.add(btnSendToComfy);
        bottom.add(btnClose);
        root.add(bottom, BorderLayout.SOUTH);

        setContentPane(root);

        selectDefaultRow();
        updateSummaryCounts();
        loadFilesAsync();
    }

    private static boolean isPrimaryModelFile(HuggingFaceFileEntry f) {
        if (f == null || f.suggestedFolder() == null) return true;
        String folder = f.suggestedFolder().toLowerCase(Locale.ROOT);
        return !folder.equals("vae") && !folder.equals("clip") && !folder.equals("text_encoders");
    }

    private JPanel buildFilesTablePanel(Color bg, Color textPrimary, Color textSecondary) {
        String[] columns = {"Model Name", "Type / Folder", "Size", "Status"};
        filesTableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        populateTableRows();

        filesTable = new JTable(filesTableModel);
        filesTable.setFont(new Font("SansSerif", Font.PLAIN, 11));
        filesTable.setRowHeight(24);
        filesTable.setShowGrid(false);
        filesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        filesTable.setBackground(bg);
        filesTable.setForeground(textPrimary);
        filesTable.getTableHeader().setBackground(bg);
        filesTable.getTableHeader().setForeground(textSecondary);
        filesTable.getTableHeader().setFont(new Font("SansSerif", Font.PLAIN, 11));

        filesTable.getColumnModel().getColumn(0).setPreferredWidth(580);
        filesTable.getColumnModel().getColumn(1).setPreferredWidth(160);
        filesTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        filesTable.getColumnModel().getColumn(3).setPreferredWidth(260);

        filesTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean isSel, boolean hasFoc, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, val, isSel, hasFoc, row, col);
                if (!isSel) {
                    c.setBackground(row % 2 == 0 ? bg : new Color(Math.max(0, bg.getRed() - 5), Math.max(0, bg.getGreen() - 5), Math.max(0, bg.getBlue() - 5)));
                }
                c.setForeground(textPrimary);
                if (col == 3 && val != null) {
                    String s = val.toString();
                    if (s.contains("Installed") || s.contains("Already")) c.setForeground(new Color(0, 180, 160));
                    else if (s.contains("Missing")) c.setForeground(new Color(255, 153, 0));
                    else if (s.contains("Downloading")) c.setForeground(new Color(59, 130, 246));
                }
                setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
                return c;
            }
        });

        filesTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateSelectionState();
            }
        });

        // Context menu for downloading individual files to Download Manager
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem itemDownloadSingle = new JMenuItem("Download selected file to Download Manager");
        itemDownloadSingle.addActionListener(e -> {
            int sel = filesTable.getSelectedRow();
            if (sel >= 0 && sel < files.size()) {
                HuggingFaceFileEntry f = files.get(sel);
                HuggingFaceDetailsActionsHandler.executeDownloadMissing(
                        this, List.of(f), onSwitchToDownloadManager, downloadManager, configService, this::dispose
                );
            }
        });
        popupMenu.add(itemDownloadSingle);

        filesTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showPopup(e);
            }
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showPopup(e);
            }
            private void showPopup(MouseEvent e) {
                int r = filesTable.rowAtPoint(e.getPoint());
                if (r >= 0 && r < filesTable.getRowCount()) {
                    filesTable.setRowSelectionInterval(r, r);
                    popupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });

        JScrollPane tblScroll = new JScrollPane(filesTable);
        int calcHeight = Math.min(220, Math.max(80, (files.size() + 1) * 26 + 6));
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

    private void selectDefaultRow() {
        if (filesTable == null || filesTable.getRowCount() == 0) {
            updateSelectionState();
            return;
        }
        int bestRow = 0;
        for (int i = 0; i < files.size(); i++) {
            HuggingFaceFileEntry f = files.get(i);
            if (isPrimaryModelFile(f) && "Installed".equalsIgnoreCase(statusChecker.apply(f))) {
                bestRow = i;
                break;
            }
        }
        filesTable.setRowSelectionInterval(bestRow, bestRow);
        updateSelectionState();
    }

    private List<HuggingFaceFileEntry> getMissingFiles() {
        return files.stream()
                .filter(f -> !"Installed".equalsIgnoreCase(statusChecker.apply(f)))
                .toList();
    }

    private void updateSummaryCounts() {
        if (lblAvailableFilesCount == null) return;
        int total = files.size();
        List<HuggingFaceFileEntry> missingList = getMissingFiles();
        int missing = missingList.size();
        int installed = Math.max(0, total - missing);

        lblAvailableFilesCount.setText(String.valueOf(total));
        lblInstalledCount.setText(String.valueOf(installed));
        lblMissingCount.setText(String.valueOf(missing));
        lblMissingCount.setForeground(missing == 0 ? new Color(34, 197, 130) : new Color(255, 153, 0));

        updateActionButtonsState(missing);
    }

    private void updateActionButtonsState(int missing) {
        if (btnDownloadModel == null || btnSendToComfy == null) return;

        if (missing == 0 && !files.isEmpty()) {
            btnDownloadModel.setText("All Models Installed ✅");
            btnDownloadModel.setEnabled(false);
            btnSendToComfy.setEnabled(true);
            btnSendToComfy.setToolTipText(null);
        } else {
            btnDownloadModel.setText(missing > 0 ? "Download Missing Models (" + missing + ")" : "Download Missing Models");
            btnDownloadModel.setEnabled(missing > 0);
            btnSendToComfy.setEnabled(false);
            btnSendToComfy.setToolTipText("Please download all missing models before sending to ComfyUI.");
        }
    }

    private void populateTableRows() {
        if (filesTableModel == null) return;
        filesTableModel.setRowCount(0);
        for (HuggingFaceFileEntry file : files) {
            String status = statusChecker.apply(file);
            boolean isDownloading = downloadManager != null && downloadManager.isDownloading(file.filename());
            String badge = isDownloading ? "⏳ Downloading..." : ("Installed".equalsIgnoreCase(status) ? "✅ Already exists" : "⚠ Missing");
            filesTableModel.addRow(new Object[]{
                    file.filename(),
                    file.suggestedFolder(),
                    file.formattedSize(),
                    badge
            });
        }
    }

    private void loadFilesAsync() {
        boolean needsBlobDetails = files.isEmpty() || files.stream().allMatch(f -> f.sizeBytes() <= 0);
        if (!needsBlobDetails || hubService == null) return;
        Thread.ofVirtual().name("HF-FetchDialogFiles").start(() -> {
            try {
                List<HuggingFaceFileEntry> fetched = hubService.getRepositoryFiles(repo.repoId()).join();
                if (fetched != null && !fetched.isEmpty()) {
                    List<HuggingFaceFileEntry> sorted = new ArrayList<>(fetched);
                    sorted.sort((a, b) -> Boolean.compare(!isPrimaryModelFile(a), !isPrimaryModelFile(b)));
                    this.files = sorted;
                    SwingUtilities.invokeLater(() -> {
                        populateTableRows();
                        selectDefaultRow();
                        updateSummaryCounts();
                    });
                }
            } catch (Exception ex) {
                logger.error("Failed to load file tree for {}: {}", repo.repoId(), ex.getMessage());
            }
        });
    }

    private void updateSelectionState() {
        if (lblSelectedFileName == null || lblSelectedFileStatus == null) return;
        int selRow = filesTable != null ? filesTable.getSelectedRow() : -1;
        if (selRow < 0 || selRow >= files.size()) {
            lblSelectedFileName.setText("No file selected");
            lblSelectedFileStatus.setText("—");
            return;
        }

        HuggingFaceFileEntry file = files.get(selRow);
        lblSelectedFileName.setText(file.filename() + " (models/" + file.suggestedFolder() + ")");

        if (lblHardwareCheck != null) {
            String hint = file.vramHint() != null && !file.vramHint().isBlank() ? file.vramHint() : "🟢 Standard VRAM requirements";
            lblHardwareCheck.setText(hint + " — " + file.formattedSize());
            lblHardwareCheck.setForeground(hint.contains("🔴") || hint.contains("🟣") ? new Color(239, 68, 68) : new Color(34, 197, 130));
        }

        String status = statusChecker.apply(file);
        boolean isInstalled = "Installed".equalsIgnoreCase(status);
        boolean isDownloading = downloadManager != null && downloadManager.isDownloading(file.filename());

        if (isDownloading) {
            lblSelectedFileStatus.setText("⏳ Downloading...");
            lblSelectedFileStatus.setForeground(new Color(59, 130, 246));
        } else if (isInstalled) {
            lblSelectedFileStatus.setText("✔ Installed & Ready");
            lblSelectedFileStatus.setForeground(new Color(34, 197, 130));
        } else {
            lblSelectedFileStatus.setText("⚠ Missing (Download required)");
            lblSelectedFileStatus.setForeground(new Color(255, 153, 0));
        }

        // Action buttons are driven by the overall blueprint readiness (missingCount)
        List<HuggingFaceFileEntry> missingList = getMissingFiles();
        updateActionButtonsState(missingList.size());
    }

    private void startMissingDownload() {
        List<HuggingFaceFileEntry> missingFiles = getMissingFiles();
        HuggingFaceDetailsActionsHandler.executeDownloadMissing(
                this, missingFiles, onSwitchToDownloadManager, downloadManager, configService, this::dispose
        );
    }

    private void dispatchSendWorkflow() {
        int selRow = filesTable != null ? filesTable.getSelectedRow() : -1;
        if (selRow < 0 || selRow >= files.size() || workflowDispatcher == null) return;
        HuggingFaceFileEntry file = files.get(selRow);

        HuggingFaceDetailsActionsHandler.dispatchSendWorkflow(
                this, repo, file, "", workflowDispatcher, btnSendToComfy, this::dispose
        );
    }
}
