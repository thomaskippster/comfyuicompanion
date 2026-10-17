package de.tki.comfyuicompanion.ui.civitai;

import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.domain.ModelFolder;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.ICivitaiOperationalModelService;
import de.tki.comfyuicompanion.service.IDownloadManager;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.StandardDialog;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Enterprise modal dialog displaying granular Civitai model details, description,
 * trigger words, operational model dependencies table, and direct actions to download,
 * send LiteGraph GUI workflows to the ComfyUI canvas, or trigger image generations.
 */
public class CivitaiModelDetailsDialog extends JDialog {

    private static final Logger logger = LoggerFactory.getLogger(CivitaiModelDetailsDialog.class);

    private final CivitaiModelEntry entry;
    private final ICivitaiOperationalModelService operationalModelService;
    private final IDownloadManager downloadManager;
    private final ConfigService configService;
    private final Consumer<CivitaiModelEntry> fallbackDownloadAction;

    private List<ModelInfo> requiredModels = new ArrayList<>();
    private CivitaiOperationalTablePanel tablePanel;
    private CivitaiLegalLicensePanel legalLicensePanel;
    private JLabel previewLabel;
    private JButton btnDownloadMissing;
    private JButton btnSendToComfy;
    private JButton btnSendAndGenerate;
    private JButton btnCleanPartial;
    private CivitaiPromptTriggerPanel promptTriggerPanel;

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
     * @param onDownload  callback when user requests download
     */
    public CivitaiModelDetailsDialog(Window owner, CivitaiModelEntry entry, boolean isInstalled, Consumer<CivitaiModelEntry> onDownload) {
        super(owner, "Model Hub: " + entry.name(), ModalityType.APPLICATION_MODAL);
        this.entry = entry;
        this.operationalModelService = null;
        this.downloadManager = null;
        this.configService = null;
        this.fallbackDownloadAction = onDownload;

        initDialog();
    }

    private void initDialog() {
        setSize(1200, 750);
        setMinimumSize(new Dimension(1080, 680));
        setResizable(true);
        setLocationRelativeTo(getOwner());
        setLayout(new BorderLayout(0, 10));

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
        checkAndFlagIncompleteModels();

        boolean darkMode = configService != null ? configService.isDarkMode() : true;
        Color bg = darkMode ? new Color(30, 33, 44) : new Color(248, 249, 252);
        Color textPrimary = darkMode ? new Color(230, 235, 245) : new Color(30, 35, 45);
        Color textSecondary = darkMode ? new Color(150, 158, 175) : new Color(100, 110, 125);

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
        civitaiWebBtn.addActionListener(e -> CivitaiActionLauncher.openCivitaiWebPage(entry.id(), this));
        headerPanel.add(civitaiWebBtn, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // --- Center Content ---
        JPanel bodyPanel = new JPanel(new BorderLayout(16, 12));
        bodyPanel.setOpaque(false);
        bodyPanel.setBorder(new EmptyBorder(0, 20, 10, 20));

        // Left Preview Image
        previewLabel = new JLabel();
        previewLabel.setPreferredSize(new Dimension(280, 380));
        previewLabel.setHorizontalAlignment(SwingConstants.CENTER);
        previewLabel.setBorder(BorderFactory.createLineBorder(darkMode ? new Color(50, 54, 68) : new Color(210, 215, 225)));
        previewLabel.setText("Loading preview...");
        previewLabel.setForeground(textSecondary);
        CivitaiActionLauncher.loadAsyncPreview(entry.thumbnailUrl(), previewLabel);
        bodyPanel.add(previewLabel, BorderLayout.WEST);

        // Right Workspace (Prompt Area / Trigger Words / Dependency Table)
        JPanel rightCol = new JPanel();
        rightCol.setLayout(new BoxLayout(rightCol, BoxLayout.Y_AXIS));
        rightCol.setOpaque(false);

        // Generation Prompt and Trigger Words Panel
        promptTriggerPanel = new CivitaiPromptTriggerPanel(entry);
        rightCol.add(promptTriggerPanel);
        rightCol.add(Box.createVerticalStrut(8));

        // Legal & Licensing Panel
        legalLicensePanel = new CivitaiLegalLicensePanel(entry, this, darkMode);
        rightCol.add(legalLicensePanel);
        rightCol.add(Box.createVerticalStrut(8));

        // Operational Models Status Table Panel
        tablePanel = new CivitaiOperationalTablePanel(requiredModels, bg, textPrimary, textSecondary);
        rightCol.add(tablePanel);
        bodyPanel.add(rightCol, BorderLayout.CENTER);
        add(bodyPanel, BorderLayout.CENTER);

        // --- South Actions Bar (Stacked 2-row layout prevents button overlap) ---
        JPanel southBar = new JPanel();
        southBar.setLayout(new BoxLayout(southBar, BoxLayout.Y_AXIS));
        southBar.setOpaque(false);
        southBar.setBorder(new EmptyBorder(6, 20, 14, 20));

        JPanel destinationRow = new JPanel(new BorderLayout(10, 0));
        destinationRow.setOpaque(false);
        destinationRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        ModelFolder folder = ModelFolder.fromString(entry.type());
        String folderName = folder != null ? folder.getDefaultFolderName() : "models";
        JLabel targetInfo = new JLabel("📁 Destination: models/" + folderName + "/" + entry.filename());
        targetInfo.setFont(new Font("Monospaced", Font.PLAIN, 11));
        targetInfo.setForeground(textSecondary);
        destinationRow.add(targetInfo, BorderLayout.WEST);

        btnCleanPartial = new JButton("Clean Incomplete Fragments", SvgIconFactory.get(AppIcon.TRASH, 12));
        btnCleanPartial.setFont(new Font("SansSerif", Font.PLAIN, 11));
        btnCleanPartial.setVisible(false);
        btnCleanPartial.addActionListener(e -> cleanIncompleteFragments());
        destinationRow.add(btnCleanPartial, BorderLayout.EAST);

        southBar.add(destinationRow);
        southBar.add(Box.createVerticalStrut(8));

        JPanel buttonRow = new JPanel(new BorderLayout(10, 0));
        buttonRow.setOpaque(false);
        buttonRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        buttonRow.add(closeBtn, BorderLayout.WEST);

        btnDownloadMissing = new JButton("Download Required Models", SvgIconFactory.get(AppIcon.DOWNLOAD, 14));
        btnDownloadMissing.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnDownloadMissing.addActionListener(e -> startMissingModelsDownload());

        btnSendToComfy = new JButton("Send to ComfyUI", SvgIconFactory.get(AppIcon.SEND, 14));
        btnSendToComfy.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnSendToComfy.addActionListener(e -> dispatchSendWorkflowToComfyUI());

        btnSendAndGenerate = new JButton("Send & Generate", SvgIconFactory.get(AppIcon.LAUNCH, 14));
        btnSendAndGenerate.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnSendAndGenerate.putClientProperty("JButton.buttonType", "accent");
        btnSendAndGenerate.addActionListener(e -> dispatchSendAndGenerate());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        actions.add(btnDownloadMissing);
        actions.add(btnSendToComfy);
        actions.add(btnSendAndGenerate);
        buttonRow.add(actions, BorderLayout.EAST);

        updateButtonsState();

        southBar.add(buttonRow);
        add(southBar, BorderLayout.SOUTH);
    }

    private void updateButtonsState() {
        if (entry.nsfw()) {
            btnDownloadMissing.setEnabled(false);
            btnDownloadMissing.setText("Blocked (NSFW)");
            btnSendToComfy.setEnabled(false);
            btnSendAndGenerate.setEnabled(false);
            return;
        }

        boolean hasIncomplete = requiredModels.stream().anyMatch(m -> m.getDescription() != null && m.getDescription().contains("Incomplete"));
        if (btnCleanPartial != null) {
            btnCleanPartial.setVisible(hasIncomplete);
        }

        int missingCount = tablePanel != null ? tablePanel.getMissingCount() : 0;
        if (missingCount == 0) {
            btnDownloadMissing.setEnabled(true);
            btnDownloadMissing.setText("All Models Installed ✅");
            btnDownloadMissing.setIcon(SvgIconFactory.get(AppIcon.CHECK, 14));

            btnSendToComfy.setEnabled(true);
            btnSendToComfy.setToolTipText("Load GUI workflow into ComfyUI canvas (with your prompt pre-filled)");

            btnSendAndGenerate.setEnabled(true);
            btnSendAndGenerate.setToolTipText("Send prompt to ComfyUI queue and preview generated image here");
        } else {
            btnDownloadMissing.setEnabled(true);
            String btnText = hasIncomplete ? "Resume Download (" + missingCount + " missing)" : "Download Required Models (" + missingCount + " missing)";
            btnDownloadMissing.setText(btnText);
            btnDownloadMissing.setIcon(SvgIconFactory.get(hasIncomplete ? AppIcon.PLAY : AppIcon.DOWNLOAD, 14));

            btnSendToComfy.setEnabled(false);
            btnSendToComfy.setToolTipText("Please download all required models before sending to ComfyUI.");

            btnSendAndGenerate.setEnabled(false);
            btnSendAndGenerate.setToolTipText("Please download all required models before generating.");
        }
    }

    private void startMissingModelsDownload() {
        if (legalLicensePanel != null && !legalLicensePanel.isReadyToProceed()) {
            return;
        }

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
                        currentInfo.setDescription(status);
                        tablePanel.setStatusForModel(currentInfo, status);
                    }
                }),
                () -> SwingUtilities.invokeLater(() -> {
                    boolean allInstalled = true;
                    boolean authFailure = false;
                    for (ModelInfo m : missing) {
                        boolean present = operationalModelService != null && operationalModelService.isModelFilePresent(m);
                        if (present) {
                            m.setPopularity("installed");
                            m.setDescription("✅ Installed");
                        } else {
                            allInstalled = false;
                            String desc = m.getDescription();
                            if (desc != null && (desc.contains("Auth") || desc.contains("Token") || desc.contains("401") || desc.contains("API Key"))) {
                                authFailure = true;
                            }
                        }
                    }
                    tablePanel.populateTableModel();
                    tablePanel.updateSummaryText();
                    updateButtonsState();
                    if (allInstalled) {
                        downloadManager.notifyComfyUI(true);
                        JOptionPane.showMessageDialog(this,
                                "All operational models successfully downloaded!\nYou can now send the workflow to ComfyUI.",
                                "Download Finished", JOptionPane.INFORMATION_MESSAGE);
                    } else if (authFailure) {
                        CivitaiActionLauncher.showAuthFailureMessage(this);
                    } else {
                        checkAndFlagIncompleteModels();
                        tablePanel.populateTableModel();
                        updateButtonsState();
                        JOptionPane.showMessageDialog(this,
                                "Some models could not be downloaded. Incomplete fragments can be resumed or cleaned.",
                                "Download Incomplete", JOptionPane.WARNING_MESSAGE);
                    }
                })
        );
    }

    private void dispatchSendWorkflowToComfyUI() {
        if (operationalModelService == null || (legalLicensePanel != null && !legalLicensePanel.isReadyToProceed())) {
            return;
        }
        String prompt = promptTriggerPanel != null ? promptTriggerPanel.getPromptText() : null;
        CivitaiModelActionsHelper.dispatchSendWorkflow(
                this, entry, requiredModels, prompt, operationalModelService, configService,
                btnSendToComfy, btnSendAndGenerate, this::dispose
        );
    }

    private void dispatchSendAndGenerate() {
        if (operationalModelService == null || (legalLicensePanel != null && !legalLicensePanel.isReadyToProceed())) {
            return;
        }
        String prompt = promptTriggerPanel != null ? promptTriggerPanel.getPromptText() : null;
        CivitaiModelActionsHelper.dispatchSendAndGenerate(
                this, entry, requiredModels, prompt, operationalModelService, configService,
                btnSendToComfy, btnSendAndGenerate, previewLabel
        );
    }

    private void cleanIncompleteFragments() {
        String baseDir = configService != null ? configService.getModelsPath() : "models";
        CivitaiModelActionsHelper.cleanIncompleteFragments(this, baseDir, requiredModels, () -> {
            tablePanel.populateTableModel();
            updateButtonsState();
        });
    }

    private void checkAndFlagIncompleteModels() {
        String baseDir = configService != null ? configService.getModelsPath() : "models";
        CivitaiModelActionsHelper.checkAndFlagIncompleteModels(baseDir, requiredModels);
    }
}
