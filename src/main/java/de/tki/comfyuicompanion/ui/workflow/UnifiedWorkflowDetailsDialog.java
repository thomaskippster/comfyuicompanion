package de.tki.comfyuicompanion.ui.workflow;

import com.formdev.flatlaf.FlatClientProperties;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.domain.VaultWorkflowMetadata;
import de.tki.comfyuicompanion.domain.WorkflowDescriptor;
import de.tki.comfyuicompanion.domain.WorkflowSourceType;
import de.tki.comfyuicompanion.service.IModelSearchService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.vault.WorkflowVaultCoordinator;
import de.tki.comfyuicompanion.service.workflow.IWorkflowSourceStrategy;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintEntry;
import de.tki.comfyuicompanion.ui.blueprint.BlueprintHardwareAdvisor;
import de.tki.comfyuicompanion.ui.blueprint.BlueprintModelTablePanel;
import de.tki.comfyuicompanion.ui.blueprint.WorkflowDialogDimensionHelper;
import de.tki.comfyuicompanion.ui.dialog.ApiKeysSettingsDialog;
import de.tki.comfyuicompanion.ui.dialog.CustomNodeVerificationPanel;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
import de.tki.comfyuicompanion.util.WorkflowImageLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.function.Consumer;

/**
 * Enterprise unified modal dialog for workflow inspection, model auditing, and execution actions.
 * Enforces consistent layout across all tabs and restricts model downloads exclusively to My Vault.
 *
 * @author Senior Java Developer
 */
public class UnifiedWorkflowDetailsDialog extends JDialog {

    private static final Logger log = LoggerFactory.getLogger(UnifiedWorkflowDetailsDialog.class);

    private final Color bgDark;
    private final Color textPrimary;
    private final Color textSecondary;
    private static final Color ACCENT_GREEN = new Color(34, 197, 94), ACCENT_AMBER = new Color(245, 158, 11);

    private final WorkflowDescriptor descriptor;
    private final IWorkflowSourceStrategy strategy;
    private final ConfigService configService;
    private final BlueprintHardwareAdvisor hardwareAdvisor;
    private final IModelSearchService modelSearchService;
    private final WorkflowVaultCoordinator vaultCoordinator;
    private final Consumer<String> onSendToComfyUI;
    private final Consumer<List<ModelInfo>> onDownloadMissing;

    public UnifiedWorkflowDetailsDialog(
            Window owner, WorkflowDescriptor descriptor, IWorkflowSourceStrategy strategy,
            ConfigService configService, BlueprintHardwareAdvisor hardwareAdvisor,
            IModelSearchService modelSearchService, WorkflowVaultCoordinator vaultCoordinator,
            Consumer<String> onSendToComfyUI, Consumer<List<ModelInfo>> onDownloadMissing) {
        super(owner, descriptor.title(), ModalityType.APPLICATION_MODAL);
        this.descriptor = descriptor; this.strategy = strategy;
        this.configService = configService; this.hardwareAdvisor = hardwareAdvisor;
        this.modelSearchService = modelSearchService; this.vaultCoordinator = vaultCoordinator;
        this.onSendToComfyUI = onSendToComfyUI; this.onDownloadMissing = onDownloadMissing;
        boolean dark = (configService != null ? configService.isDarkMode() : de.tki.comfyuicompanion.ui.ThemeManager.isDarkMode());
        this.bgDark = dark ? new Color(20, 24, 33) : new Color(245, 247, 250);
        this.textPrimary = dark ? new Color(241, 245, 249) : new Color(30, 41, 59);
        this.textSecondary = dark ? new Color(148, 163, 184) : new Color(71, 85, 105);
        initUI(owner);
    }

    private void initUI(Window owner) {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getContentPane().setBackground(bgDark);
        setLayout(new BorderLayout());
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(bgDark);
        root.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        root.add(buildHeaderPanel(), BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(bgDark);

        body.add(buildTopHeroSection());
        body.add(Box.createVerticalStrut(10));

        buildModelsTable(body);

        JScrollPane scrollPane = new JScrollPane(body);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(bgDark);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        root.add(scrollPane, BorderLayout.CENTER);

        root.add(buildFooterPanel(), BorderLayout.SOUTH);
        getContentPane().add(root, BorderLayout.CENTER);

        int modelCount = descriptor.requiredModels() != null ? descriptor.requiredModels().size() : 0;
        int metaCount = 8 + (descriptor.extendedAttributes() != null ? descriptor.extendedAttributes().size() : 0);
        WorkflowDialogDimensionHelper.applyOptimalSizeAndLocation(this, owner, true, metaCount, modelCount, false);
    }

    private JPanel buildHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel lblTitle = new JLabel(descriptor.title());
        lblTitle.setFont(new Font("SansSerif", Font.BOLD, 18));
        lblTitle.setForeground(textPrimary);
        header.add(lblTitle, BorderLayout.WEST);

        if (descriptor.sourceUrl() != null && !descriptor.sourceUrl().isBlank()) {
            JButton btnOpenWeb = new JButton("Open on " + descriptor.sourceType().getDisplayName(), SvgIconFactory.get(AppIcon.LINK, 12));
            btnOpenWeb.setFont(new Font("SansSerif", Font.PLAIN, 11));
            btnOpenWeb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btnOpenWeb.addActionListener(e -> {
                try {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                        Desktop.getDesktop().browse(new URI(descriptor.sourceUrl()));
                    }
                } catch (Exception ex) {
                    log.warn("Could not open web link: {}", descriptor.sourceUrl());
                }
            });
            header.add(btnOpenWeb, BorderLayout.EAST);
        }

        return header;
    }

    private JPanel buildTopHeroSection() {
        int modelCount = descriptor.requiredModels() != null ? descriptor.requiredModels().size() : 0;
        if (modelCount > 4) {
            JPanel hero = new JPanel(new BorderLayout(20, 0));
            hero.setOpaque(false);
            hero.setAlignmentX(LEFT_ALIGNMENT);

            JPanel banner = buildBannerPanel(340, 210);
            hero.add(banner, BorderLayout.WEST);

            JPanel metaPanel = new JPanel();
            metaPanel.setLayout(new BoxLayout(metaPanel, BoxLayout.Y_AXIS));
            metaPanel.setOpaque(false);
            buildMetadataRows(metaPanel);
            hero.add(metaPanel, BorderLayout.CENTER);

            return hero;
        } else {
            JPanel hero = new JPanel();
            hero.setLayout(new BoxLayout(hero, BoxLayout.Y_AXIS));
            hero.setOpaque(false);
            hero.setAlignmentX(LEFT_ALIGNMENT);

            hero.add(buildBannerPanel(800, 200));
            hero.add(Box.createVerticalStrut(10));
            JPanel metaPanel = new JPanel();
            metaPanel.setLayout(new BoxLayout(metaPanel, BoxLayout.Y_AXIS));
            metaPanel.setOpaque(false);
            metaPanel.setAlignmentX(LEFT_ALIGNMENT);
            buildMetadataRows(metaPanel);
            hero.add(metaPanel);

            return hero;
        }
    }

    private JPanel buildBannerPanel(int targetW, int targetH) {
        JPanel bannerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        bannerPanel.setOpaque(false);
        bannerPanel.setAlignmentX(LEFT_ALIGNMENT);

        JLabel bannerLabel = new JLabel("", SwingConstants.CENTER);
        Dimension initDim = new Dimension(Math.min(targetW, 340), targetH);
        bannerLabel.setPreferredSize(initDim);
        bannerLabel.setMaximumSize(initDim);
        boolean dark = (configService != null ? configService.isDarkMode() : de.tki.comfyuicompanion.ui.ThemeManager.isDarkMode());
        bannerLabel.setBackground(dark ? new Color(15, 23, 42) : new Color(241, 245, 249));
        bannerLabel.setOpaque(true);
        bannerLabel.putClientProperty(FlatClientProperties.STYLE, "arc: 8");
        bannerPanel.add(bannerLabel);

        Thread.ofVirtual().name("unified-details-banner").start(() -> {
            try {
                byte[] bytes = strategy != null ? strategy.fetchPreviewImageBytes(descriptor).join() : null;
                BufferedImage img = bytes != null && bytes.length > 0 ? WorkflowImageLoader.decodeImageBytes(bytes) : null;
                if (img == null && descriptor.previewPathOrUrl() != null) {
                    img = WorkflowImageLoader.loadOrFetchCached(descriptor.previewPathOrUrl());
                }

                if (img != null) {
                    int srcW = Math.max(1, img.getWidth());
                    int srcH = Math.max(1, img.getHeight());
                    double scale = Math.min((double) targetW / srcW, (double) targetH / srcH);
                    int bannerW = Math.max(1, (int) Math.round(srcW * scale));
                    int bannerH = Math.max(1, (int) Math.round(srcH * scale));
                    Image scaled = scaleImage(img, bannerW, bannerH);
                    SwingUtilities.invokeLater(() -> {
                        Dimension dim = new Dimension(bannerW, bannerH);
                        bannerLabel.setMinimumSize(dim);
                        bannerLabel.setPreferredSize(dim);
                        bannerLabel.setMaximumSize(dim);
                        bannerLabel.setIcon(new ImageIcon(scaled));
                        bannerPanel.revalidate();
                        bannerPanel.repaint();
                    });
                }
            } catch (Exception ex) {
                log.debug("Failed loading banner image: {}", ex.getMessage());
            }
        });

        return bannerPanel;
    }

    private void buildMetadataRows(JPanel body) {
        addRow(body, "Source Platform", descriptor.sourceType().getDisplayName(), descriptor.sourceType().getBadgeColor());
        addRow(body, "Category", descriptor.category(), textPrimary);
        addRow(body, "Architecture", descriptor.architecture(), textPrimary);
        addRow(body, "Author", descriptor.author(), textPrimary);

        if (descriptor.extendedAttributes() != null && !descriptor.extendedAttributes().isEmpty()) {
            for (java.util.Map.Entry<String, String> entry : descriptor.extendedAttributes().entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isBlank()) {
                    addRow(body, entry.getKey(), entry.getValue(), textPrimary);
                }
            }
        }

        int totalModels = descriptor.requiredModels() != null ? descriptor.requiredModels().size() : 0;
        int missing = computeActualMissingCount();
        int local = Math.max(0, totalModels - missing);

        addRow(body, "Required Models", String.valueOf(totalModels), textPrimary);
        addRow(body, "Available Locally", String.valueOf(local), ACCENT_GREEN);
        addRow(body, "Missing", String.valueOf(missing), missing > 0 ? ACCENT_AMBER : ACCENT_GREEN);
        addRow(body, "Hardware Check", missing > 0 ? "Requires download of missing models" : "Fits in VRAM - Standard requirements", missing > 0 ? ACCENT_AMBER : ACCENT_GREEN);

        if (configService != null) {
            CustomNodeVerificationPanel customNodePanel = new CustomNodeVerificationPanel(this, configService, textSecondary);
            addRowWithComponent(body, "Custom Nodes", customNodePanel);
            customNodePanel.verifyWorkflowAsync(() -> strategy.resolveWorkflowJson(descriptor).join());
        }
    }

    private void buildModelsTable(JPanel body) {
        if (descriptor.requiredModels() == null || descriptor.requiredModels().isEmpty()) {
            return;
        }

        JLabel lblSec = new JLabel("Required Models Status:");
        lblSec.setFont(new Font("SansSerif", Font.BOLD, 12));
        lblSec.setForeground(textPrimary);
        lblSec.setAlignmentX(LEFT_ALIGNMENT);
        body.add(lblSec);
        body.add(Box.createVerticalStrut(6));

        boolean dark = (configService != null ? configService.isDarkMode() : de.tki.comfyuicompanion.ui.ThemeManager.isDarkMode());
        Color tableBg = dark ? new Color(25, 30, 42) : Color.WHITE;
        BlueprintModelTablePanel tablePanel = new BlueprintModelTablePanel(
                descriptor.requiredModels(), tableBg, textPrimary, textSecondary,
                this::resolveModelStatusText, this::resolveModelSizeText, modelSearchService
        );
        tablePanel.setAlignmentX(LEFT_ALIGNMENT);
        body.add(tablePanel);
    }

    private int computeActualMissingCount() {
        List<ModelInfo> reqs = descriptor.requiredModels();
        if (reqs == null || reqs.isEmpty() || vaultCoordinator == null) {
            return descriptor.missingModelsCount();
        }
        int missing = 0;
        for (ModelInfo info : reqs) {
            String st = resolveModelStatusText(info);
            if ("Missing".equalsIgnoreCase(st) || "Archived".equalsIgnoreCase(st)) {
                missing++;
            }
        }
        return missing;
    }

    private String resolveModelStatusText(ModelInfo info) {
        if ("workflows".equalsIgnoreCase(info.getType()) || "workflows".equalsIgnoreCase(info.getSave_path())) {
            return "installed".equalsIgnoreCase(info.getPopularity()) ? "Already exists" : "Workflow Bundle (Auto-fetched)";
        }
        if (vaultCoordinator != null && vaultCoordinator.isModelPresent(info)) {
            return "Already exists";
        }
        if (vaultCoordinator != null && vaultCoordinator.isModelArchived(info)) {
            return "Archived";
        }
        return "Missing";
    }

    private String resolveModelSizeText(ModelInfo info) {
        if (vaultCoordinator != null) {
            String resolved = vaultCoordinator.resolveModelSize(info);
            if (!"Unknown".equalsIgnoreCase(resolved) && !"Workflow JSON".equalsIgnoreCase(resolved)) return resolved;
        }
        if (info.getSize() != null && !info.getSize().isBlank() && !"Unknown".equalsIgnoreCase(info.getSize()) && !"Workflow JSON".equalsIgnoreCase(info.getSize())) {
            return info.getSize();
        }
        long b = info.getByteSize();
        if (b >= 1024L * 1024 * 1024) return String.format(java.util.Locale.US, "%.2f GB", b / (1024.0 * 1024 * 1024));
        if (b >= 1024L * 1024) return String.format(java.util.Locale.US, "%.1f MB", b / (1024.0 * 1024));
        if (b > 0) return String.format(java.util.Locale.US, "%.1f KB", b / 1024.0);
        String name = (info.getFilename() != null && !info.getFilename().isBlank()) ? info.getFilename() : info.getName();
        String est = de.tki.comfyuicompanion.service.workflow.WorkflowModelExtractor.estimateKnownModelSize(name);
        return est != null ? est : "Unknown";
    }

    private JPanel buildFooterPanel() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        footer.setOpaque(false);

        int missingCount = computeActualMissingCount();
        if (strategy.canDownloadMissingModels() && missingCount > 0) {
            JButton btnDownload = new JButton("Download Missing Models (" + missingCount + ")", SvgIconFactory.get(AppIcon.DOWNLOAD, 14));
            btnDownload.setFont(new Font("SansSerif", Font.BOLD, 12));
            btnDownload.setBackground(ACCENT_AMBER);
            btnDownload.setForeground(Color.WHITE);
            btnDownload.putClientProperty(FlatClientProperties.STYLE, "arc: 8");
            btnDownload.addActionListener(e -> {
                dispose();
                List<ModelInfo> missingOnly = descriptor.requiredModels() != null
                        ? descriptor.requiredModels().stream().filter(m -> {
                            String st = resolveModelStatusText(m);
                            return "Missing".equalsIgnoreCase(st) || "Archived".equalsIgnoreCase(st);
                        }).toList()
                        : List.of();
                if (missingOnly.isEmpty() && descriptor.requiredModels() != null) missingOnly = descriptor.requiredModels();
                if (onDownloadMissing != null) {
                    onDownloadMissing.accept(missingOnly);
                } else {
                    strategy.downloadMissingModels(descriptor, missingOnly, getOwner());
                }
            });
            footer.add(btnDownload);
        }

        if (descriptor.sourceType() != WorkflowSourceType.VAULT) {
            boolean alreadySaved = vaultCoordinator != null && (
                    vaultCoordinator.isWorkflowInVault(descriptor.title()) ||
                    vaultCoordinator.isWorkflowInVault(descriptor.id())
            );
            JButton btnSaveVault = new JButton(
                    alreadySaved ? "Saved in My Vault" : "Save Workflow to My Vault",
                    SvgIconFactory.get(alreadySaved ? AppIcon.CHECK : AppIcon.FOLDER, 14)
            );
            btnSaveVault.setFont(new Font("SansSerif", Font.BOLD, 12));
            btnSaveVault.setBackground(alreadySaved ? new Color(13, 148, 136) : new Color(16, 185, 129));
            btnSaveVault.setForeground(Color.WHITE);
            btnSaveVault.putClientProperty(FlatClientProperties.STYLE, "arc: 8");
            btnSaveVault.setEnabled(!alreadySaved);
            if (!alreadySaved) btnSaveVault.addActionListener(e -> executeSaveToVault(btnSaveVault));
            footer.add(btnSaveVault);
        } else {
            JButton btnRemoveVault = new JButton("Remove from My Vault", SvgIconFactory.get(AppIcon.TRASH, 14));
            btnRemoveVault.setFont(new Font("SansSerif", Font.BOLD, 12));
            btnRemoveVault.setBackground(new Color(220, 38, 38));
            btnRemoveVault.setForeground(Color.WHITE);
            btnRemoveVault.putClientProperty(FlatClientProperties.STYLE, "arc: 8");
            btnRemoveVault.addActionListener(e -> executeRemoveFromVault());
            footer.add(btnRemoveVault);

            JButton btnSend = new JButton("Send to ComfyUI", SvgIconFactory.get(AppIcon.SEND, 14));
            btnSend.setFont(new Font("SansSerif", Font.BOLD, 12));
            boolean canSend = missingCount == 0;
            btnSend.setEnabled(canSend);
            btnSend.setBackground(canSend ? new Color(59, 130, 246) : new Color(71, 85, 105));
            btnSend.setForeground(canSend ? Color.WHITE : new Color(148, 163, 184));
            btnSend.putClientProperty(FlatClientProperties.STYLE, "arc: 8");
            if (!canSend) {
                btnSend.setToolTipText("Cannot send workflow to ComfyUI: " + missingCount + " model(s) are missing. Download them first.");
            } else {
                btnSend.setToolTipText("All models ready. Send workflow to ComfyUI canvas.");
            }
            btnSend.addActionListener(e -> executeSendToComfyUI());
            footer.add(btnSend);
        }

        JButton btnClose = new JButton("Close");
        btnClose.setFont(new Font("SansSerif", Font.PLAIN, 12));
        btnClose.putClientProperty(FlatClientProperties.STYLE, "arc: 8");
        btnClose.addActionListener(e -> dispose());
        footer.add(btnClose);

        return footer;
    }

    private void executeRemoveFromVault() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to remove '" + descriptor.title() + "' from your Vault?",
                "Remove from Vault", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            if (vaultCoordinator != null) {
                if (descriptor.rawSourceEntry() instanceof BlueprintEntry be) {
                    vaultCoordinator.removeWorkflow(be);
                } else if (descriptor.workflowFilePathOrUrl() != null) {
                    File wfFile = new File(descriptor.workflowFilePathOrUrl());
                    if (wfFile.exists()) wfFile.delete();
                }
            }
            dispose();
        }
    }

    private void executeSaveToVault(JButton btnSave) {
        if (vaultCoordinator == null) {
            JOptionPane.showMessageDialog(this, "Vault coordinator is not available.", "Vault Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        btnSave.setEnabled(false);
        btnSave.setText("Saving to Vault...");

        Thread.ofVirtual().name("unified-dialog-vault-save").start(() -> {
            try {
                String json = strategy.resolveWorkflowJson(descriptor).join();
                byte[] imgBytes = strategy.fetchPreviewImageBytes(descriptor).join();
                String ext = "png";
                String safeName = descriptor.title().replaceAll("[^a-zA-Z0-9_\\-.]", "_").replaceAll("_+", "_");
                VaultWorkflowMetadata meta = new VaultWorkflowMetadata(
                        safeName, descriptor.title(), descriptor.description(),
                        descriptor.category(), descriptor.architecture(),
                        descriptor.sourceType().name(), descriptor.sourceUrl(),
                        safeName + "." + ext, descriptor.requiredModels(), System.currentTimeMillis()
                );
                vaultCoordinator.saveToVault(safeName, json, imgBytes, ext, meta);
                SwingUtilities.invokeLater(() -> {
                    btnSave.setText("Saved in My Vault");
                    btnSave.setIcon(SvgIconFactory.get(AppIcon.CHECK, 14));
                    btnSave.setBackground(new Color(13, 148, 136));
                    btnSave.setEnabled(false);
                    JOptionPane.showMessageDialog(this, "Workflow '" + descriptor.title() + "' successfully saved to My Vault!", "Vault Saved", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception ex) {
                log.error("Failed saving workflow to Vault: {}", ex.getMessage(), ex);
                Throwable cause = ex;
                while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
                String msg = cause.getMessage() != null ? cause.getMessage() : ex.getMessage();
                SwingUtilities.invokeLater(() -> {
                    btnSave.setEnabled(true);
                    btnSave.setText("Save Workflow to My Vault");
                    if (msg != null && (msg.contains("401") || msg.contains("403") || msg.toLowerCase().contains("gated"))) { int res = JOptionPane.showOptionDialog(this, "<html><body style='width: 320px;'><b>Hugging Face Gated Repository</b><br><br>Access to this workflow is restricted. Please accept the model terms on HuggingFace.co and add your Hugging Face Access Token in <b>Settings &rarr; API Keys</b>.</body></html>", "Authentication Required", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new Object[]{"Open Settings", "Close"}, "Open Settings"); if (res == 0) { JFrame p = (JFrame) SwingUtilities.getWindowAncestor(this); ApiKeysSettingsDialog.showApiKeysDialog(p, configService, () -> {}); } } else { JOptionPane.showMessageDialog(this, "Failed to save workflow: " + msg, "Error", JOptionPane.ERROR_MESSAGE); }
                });
            }
        });
    }

    private void executeSendToComfyUI() {
        Thread.ofVirtual().name("unified-dialog-send").start(() -> {
            try {
                String json = strategy.resolveWorkflowJson(descriptor).join();
                if (onSendToComfyUI != null) {
                    onSendToComfyUI.accept(json);
                }
            } catch (Exception ex) {
                log.error("Failed sending workflow to ComfyUI: {}", ex.getMessage(), ex);
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(this, "Could not send workflow to ComfyUI: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE)
                );
            }
        });
    }

    private void addRow(JPanel parent, String label, String value, Color valColor) {
        JLabel val = new JLabel(value != null ? value : "-");
        val.setFont(new Font("SansSerif", Font.PLAIN, 11));
        val.setForeground(valColor);
        addRowWithComponent(parent, label, val);
    }

    private void addRowWithComponent(JPanel parent, String label, JComponent comp) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        JLabel lbl = new JLabel(label + ":");
        lbl.setFont(new Font("SansSerif", Font.BOLD, 11));
        lbl.setForeground(textSecondary);
        lbl.setPreferredSize(new Dimension(140, 20));
        row.add(lbl); row.add(comp);
        parent.add(row);
    }

    private Image scaleImage(BufferedImage src, int targetWidth, int targetHeight) {
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = resized.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(src, 0, 0, targetWidth, targetHeight, null);
        g2.dispose();
        return resized;
    }
}
