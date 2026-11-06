package de.tki.comfyuicompanion.ui.blueprint;

import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.domain.VaultWorkflowMetadata;
import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import de.tki.comfyuicompanion.service.ILocalModelValidator;
import de.tki.comfyuicompanion.service.IModelSearchService;
import de.tki.comfyuicompanion.service.IWorkflowDownloader;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintEntry;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintStatus;
import de.tki.comfyuicompanion.ui.dialog.CustomNodeVerificationPanel;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
import de.tki.comfyuicompanion.util.WorkflowImageLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Modal dialog for inspecting blueprint metadata, required local models,
 * custom nodes resolution, and launching the workflow in ComfyUI.
 */
@Deprecated
public class BlueprintDetailsDialog extends JDialog {
    private static final Logger logger = LoggerFactory.getLogger(BlueprintDetailsDialog.class);
    private static final ObjectMapper SHARED_MAPPER = new ObjectMapper();

    public BlueprintDetailsDialog(Window owner,
                                  BlueprintEntry entry,
                                  BlueprintStatus status,
                                  ConfigService configService,
                                  IWorkflowDownloader workflowDownloader,
                                  IComfyLifecycleService lifecycleService,
                                  ILocalModelValidator localModelValidator,
                                  IModelSearchService modelSearchService,
                                  BlueprintPreviewCache previewCache,
                                  BiConsumer<BlueprintEntry, JButton> playVideoAction,
                                  Function<ModelInfo, String> modelStatusResolver,
                                  Function<ModelInfo, String> modelSizeResolver,
                                  Consumer<File> onWorkflowImported,
                                  BiConsumer<BlueprintEntry, BlueprintStatus> reloadDetailsAction) {
        this(owner, entry, status, configService, workflowDownloader, lifecycleService,
                localModelValidator, modelSearchService, previewCache, playVideoAction,
                modelStatusResolver, modelSizeResolver, onWorkflowImported, reloadDetailsAction, null);
    }

    public BlueprintDetailsDialog(Window owner,
                                  BlueprintEntry entry,
                                  BlueprintStatus status,
                                  ConfigService configService,
                                  IWorkflowDownloader workflowDownloader,
                                  IComfyLifecycleService lifecycleService,
                                  ILocalModelValidator localModelValidator,
                                  IModelSearchService modelSearchService,
                                  BlueprintPreviewCache previewCache,
                                  BiConsumer<BlueprintEntry, JButton> playVideoAction,
                                  Function<ModelInfo, String> modelStatusResolver,
                                  Function<ModelInfo, String> modelSizeResolver,
                                  Consumer<File> onWorkflowImported,
                                  BiConsumer<BlueprintEntry, BlueprintStatus> reloadDetailsAction,
                                  Consumer<BlueprintEntry> onRemoveFromVault) {
        super(owner instanceof Frame ? (Frame) owner : null, "Blueprint: " + entry.name, true);
        boolean hasBanner = (previewCache != null && (previewCache.getPreview(entry.filePath) != null || previewCache.getPreview(entry.previewPath) != null))
                || (entry.previewPath != null && !entry.previewPath.isBlank());
        int metadataCount = 6 + (!entry.category.isEmpty() ? 1 : 0) + (!entry.description.isEmpty() ? 2 : 0);
        int modelCount = entry.requiredModels != null ? entry.requiredModels.size() : 0;
        WorkflowDialogDimensionHelper.applyOptimalSizeAndLocation(this, owner, hasBanner, metadataCount, modelCount, true);

        Color textPrimary = configService.isDarkMode() ? new Color(228, 230, 240) : new Color(30, 35, 45);
        Color textSecondary = configService.isDarkMode() ? new Color(120, 126, 152) : new Color(90, 100, 110);
        Color bg = configService.isDarkMode() ? new Color(20, 22, 30) : new Color(245, 247, 250);

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(bg);
        root.setBorder(new EmptyBorder(18, 20, 16, 20));

        JLabel title = new JLabel(entry.name);
        title.setFont(new Font("SansSerif", Font.BOLD, 17));
        title.setForeground(textPrimary);

        boolean isVideoPreview = entry.registryWorkflow != null && entry.registryWorkflow.isVideoPreview();
        if (isVideoPreview) {
            JPanel titleRow = new JPanel(new BorderLayout(8, 0));
            titleRow.setOpaque(false);
            titleRow.add(title, BorderLayout.WEST);

            JButton btnPlay = new JButton("Play Video Preview", SvgIconFactory.get(AppIcon.PLAY, 14));
            btnPlay.setFont(new Font("SansSerif", Font.BOLD, 11));
            btnPlay.putClientProperty("Button.arc", 999);
            btnPlay.setBackground(new Color(120, 60, 220));
            btnPlay.setForeground(Color.WHITE);
            btnPlay.addActionListener(e -> {
                if (playVideoAction != null) playVideoAction.accept(entry, btnPlay);
            });
            titleRow.add(btnPlay, BorderLayout.EAST);
            root.add(titleRow, BorderLayout.NORTH);
        } else {
            root.add(title, BorderLayout.NORTH);
        }

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);

        JLabel imgLabel = new JLabel("", SwingConstants.CENTER);
        imgLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        Image previewImg = null;
        if (previewCache != null) {
            previewImg = previewCache.getPreview(entry.filePath);
            if (previewImg == null && entry.previewPath != null) {
                previewImg = previewCache.getPreview(entry.previewPath);
            }
        }
        if (previewImg != null) {
            applyScaledBanner(imgLabel, previewImg, owner, metadataCount, modelCount);
            body.add(imgLabel);
            body.add(Box.createVerticalStrut(10));
        } else if (entry.previewPath != null && !entry.previewPath.isBlank()) {
            body.add(imgLabel);
            body.add(Box.createVerticalStrut(10));
            final String previewTarget = entry.previewPath;
            Thread.ofVirtual().name("banner-async-loader").start(() -> {
                Image loaded = WorkflowImageLoader.loadOrFetchCached(previewTarget, Duration.ofSeconds(10));
                if (loaded != null) {
                    final Image finalImg = loaded;
                    SwingUtilities.invokeLater(() -> {
                        applyScaledBanner(imgLabel, finalImg, owner, metadataCount, modelCount);
                        body.revalidate();
                        body.repaint();
                    });
                }
            });
        }

        if (!entry.category.isEmpty()) {
            addRow(body, "Category", entry.category, textSecondary, textPrimary);
        }
        if (!entry.description.isEmpty()) {
            addRow(body, "Description", entry.description, textSecondary, textPrimary);
        }

        int totalReq = entry.requiredModels != null ? entry.requiredModels.size() : 0;
        int present = status.presentCount;
        int missing = status.missingCount;

        addRow(body, "Required models", String.valueOf(totalReq), textSecondary, textPrimary);
        addRow(body, "Available locally", String.valueOf(present), textSecondary, textPrimary);
        addRow(body, "Missing", String.valueOf(missing), textSecondary, missing == 0 ? new Color(34, 197, 130) : new Color(255, 153, 0));

        BlueprintHardwareAdvisor.CompatibilityReport hwReport =
                BlueprintHardwareAdvisor.evaluate(entry, status, configService, null, modelSizeResolver);
        addRow(body, "Hardware Check", hwReport.detailSummary(), textSecondary, hwReport.level().getBadgeColor());

        CustomNodeVerificationPanel customNodeVerificationPanel = new CustomNodeVerificationPanel(this, configService, textSecondary);
        addRowWithComponent(body, "Custom Nodes", customNodeVerificationPanel, textSecondary);
        body.add(Box.createVerticalStrut(6));

        customNodeVerificationPanel.verifyWorkflowAsync(() -> readWorkflowJson(entry));

        if (entry.requiredModels.isEmpty()) {
            if (!entry.hasBeenValidated) {
                entry.hasBeenValidated = true;
                body.add(Box.createVerticalStrut(12));
                JLabel scanLbl = new JLabel("Extracting model dependencies from workflow JSON...");
                scanLbl.setFont(new Font("SansSerif", Font.ITALIC, 11));
                scanLbl.setForeground(textSecondary);
                scanLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
                body.add(scanLbl);

                Thread.ofVirtual().name("deep-scan").start(() -> {
                    try {
                        String json = readWorkflowJson(entry);
                        if (json != null && !json.isBlank()) {
                            if (entry.registryWorkflow.getRequiredModelInfos() != null) {
                                entry.requiredModels.addAll(entry.registryWorkflow.getRequiredModelInfos());
                            }
                            SwingUtilities.invokeLater(() -> {
                                if (reloadDetailsAction != null) {
                                    dispose();
                                    reloadDetailsAction.accept(entry, status);
                                }
                            });
                        }
                    } catch (Exception ex) {
                        logger.warn("Deep scan failed for {}: {}", entry.name, ex.getMessage());
                    }
                });
            }
        } else {
            JLabel reqLbl = new JLabel("Required Models Status:");
            reqLbl.setFont(new Font("SansSerif", Font.BOLD, 11));
            reqLbl.setForeground(textPrimary);
            reqLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(reqLbl);
            body.add(Box.createVerticalStrut(6));

            Color tableBg = configService.isDarkMode() ? bg : Color.WHITE;
            body.add(new BlueprintModelTablePanel(entry, tableBg, textPrimary, textSecondary, modelStatusResolver, modelSizeResolver, modelSearchService));
        }

        body.add(Box.createVerticalStrut(8));
        JLabel promptLbl = new JLabel("Custom Positive Prompt (Optional):");
        promptLbl.setFont(new Font("SansSerif", Font.BOLD, 11));
        promptLbl.setForeground(textPrimary);
        promptLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(promptLbl);
        body.add(Box.createVerticalStrut(4));

        JTextArea promptArea = new JTextArea(3, 20);
        promptArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        promptArea.setBackground(configService.isDarkMode() ? new Color(13, 14, 18) : new Color(255, 255, 255));
        promptArea.setForeground(textPrimary);
        promptArea.setCaretColor(textPrimary);
        promptArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(configService.isDarkMode() ? new Color(40, 44, 58) : new Color(210, 215, 225)),
                new EmptyBorder(6, 8, 6, 8)
        ));
        body.add(promptArea);

        JScrollPane scroll = new JScrollPane(body, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        root.add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        bottom.setOpaque(false);

        JButton btnDlWorkflow = new JButton("Save Workflow to My Vault", SvgIconFactory.get(AppIcon.ARCHIVE, 14));
        btnDlWorkflow.setFont(new Font("SansSerif", Font.BOLD, 11));
        btnDlWorkflow.putClientProperty("Button.arc", 999);
        btnDlWorkflow.setBackground(new Color(0, 150, 136));
        btnDlWorkflow.setForeground(Color.WHITE);

        File baseDir = configService != null ? configService.getUserWorkflowsDir() : new File("user_workflows");
        File vaultDir = new File(baseDir, "vault");
        if (!vaultDir.exists()) vaultDir.mkdirs();
        boolean isInVault = (entry.filePath != null && (entry.filePath.contains(File.separator + "vault" + File.separator) || entry.filePath.contains("/vault/")))
                || onRemoveFromVault != null;

        if (isInVault) {
            btnDlWorkflow.setText("Remove from My Vault");
            btnDlWorkflow.setIcon(SvgIconFactory.get(AppIcon.TRASH, 14));
            btnDlWorkflow.setBackground(new Color(220, 38, 38));
            btnDlWorkflow.setForeground(Color.WHITE);
            btnDlWorkflow.setEnabled(true);
            btnDlWorkflow.addActionListener(e -> {
                int confirm = JOptionPane.showConfirmDialog(
                        this,
                        "Are you sure you want to remove '" + entry.name + "' from your Vault?",
                        "Remove from Vault",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );
                if (confirm == JOptionPane.YES_OPTION) {
                    if (onRemoveFromVault != null) {
                        onRemoveFromVault.accept(entry);
                    } else if (entry.filePath != null) {
                        File wfFile = new File(entry.filePath);
                        if (wfFile.exists()) wfFile.delete();
                        String baseName = wfFile.getName().replace(".json", "");
                        new File(wfFile.getParentFile(), baseName + ".meta.json").delete();
                        new File(wfFile.getParentFile(), baseName + ".png").delete();
                        new File(wfFile.getParentFile(), baseName + ".jpg").delete();
                        new File(wfFile.getParentFile(), baseName + ".webp").delete();
                    }
                    dispose();
                }
            });
        } else {
            btnDlWorkflow.addActionListener(e -> {
                if (entry.filePath != null) {
                    File localFile = new File(entry.filePath);
                    if (localFile.exists() && localFile.isFile() && localFile.length() > 0) {
                        try {
                            File dest = new File(vaultDir, localFile.getName());
                            Files.copy(localFile.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            saveSiblingAndMetadataToVault(vaultDir, dest, entry, previewCache);
                            btnDlWorkflow.setText("Saved in My Vault");
                            btnDlWorkflow.setIcon(SvgIconFactory.get(AppIcon.CHECK, 14));
                            btnDlWorkflow.setEnabled(false);
                        } catch (Exception ex) {
                            logger.error("Failed saving to vault: {}", ex.getMessage());
                        }
                        return;
                    }
                }

                if (entry.registryWorkflow == null) {
                    return;
                }

                btnDlWorkflow.setEnabled(false);
                btnDlWorkflow.setText("Saving to Vault...");
                workflowDownloader.downloadWorkflowAsync(entry.registryWorkflow)
                        .thenAccept(file -> SwingUtilities.invokeLater(() -> {
                            try {
                                File dest = new File(vaultDir, file.getName());
                                Files.copy(file.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                saveSiblingAndMetadataToVault(vaultDir, dest, entry, previewCache);
                            } catch (Exception ex) {
                                logger.error("Failed copying downloaded workflow to vault: {}", ex.getMessage());
                            }
                            btnDlWorkflow.setText("Saved in My Vault");
                            btnDlWorkflow.setIcon(SvgIconFactory.get(AppIcon.CHECK, 14));
                            btnDlWorkflow.setEnabled(false);
                        }))
                        .exceptionally(ex -> {
                            SwingUtilities.invokeLater(() -> {
                                btnDlWorkflow.setEnabled(true);
                                btnDlWorkflow.setText("Save Workflow to My Vault");
                                btnDlWorkflow.setIcon(SvgIconFactory.get(AppIcon.ARCHIVE, 14));
                                JOptionPane.showMessageDialog(this,
                                         "Failed to download workflow: " + ex.getCause().getMessage(),
                                        "Download Error", JOptionPane.ERROR_MESSAGE);
                            });
                            return null;
                        });
            });
        }

        JButton btnSendToComfy = new JButton("Send to ComfyUI", SvgIconFactory.get(AppIcon.SEND));
        btnSendToComfy.setFont(new Font("SansSerif", Font.BOLD, 11));
        btnSendToComfy.putClientProperty("Button.arc", 999);
        btnSendToComfy.setBackground(new Color(33, 150, 243));
        btnSendToComfy.setForeground(Color.WHITE);
        if (status.missingCount > 0) {
            btnSendToComfy.setEnabled(false);
            btnSendToComfy.setToolTipText("Please download all missing models before launching this blueprint.");
        }
        btnSendToComfy.addActionListener(e -> {
            String customPrompt = promptArea.getText().trim();
            BlueprintComfySender.sendToComfyUI(entry, customPrompt.isEmpty() ? null : customPrompt,
                    workflowDownloader, lifecycleService, configService, null, this,
                    success -> {
                        if (Boolean.TRUE.equals(success) && onWorkflowImported != null && entry.filePath != null) {
                            onWorkflowImported.accept(new File(entry.filePath));
                        }
                    });
        });

        JButton btnClose = new JButton("Close");
        btnClose.setFont(new Font("SansSerif", Font.PLAIN, 11));
        btnClose.addActionListener(e -> dispose());

        bottom.add(btnDlWorkflow);
        bottom.add(btnSendToComfy);
        bottom.add(btnClose);
        root.add(bottom, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private String readWorkflowJson(BlueprintEntry entry) {
        if (entry.filePath != null && !entry.filePath.startsWith("http")) {
            File f = new File(entry.filePath);
            if (f.exists() && f.isFile()) {
                try {
                    return Files.readString(f.toPath());
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private void applyScaledBanner(JLabel imgLabel, Image img, Window owner, int metadataCount, int modelCount) {
        int origW = img.getWidth(null);
        int origH = img.getHeight(null);
        if (origW <= 0 || origH <= 0) return;
        int maxBannerH = WorkflowDialogDimensionHelper.calculateMaxBannerHeight(owner, metadataCount, modelCount, true);
        int targetW = Math.min(1100, origW);
        int targetH = (int) (origH * ((double) targetW / origW));
        if (targetH > maxBannerH) {
            targetH = maxBannerH;
            targetW = (int) (origW * ((double) targetH / origH));
        }
        Image scaled = img.getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);
        imgLabel.setIcon(new ImageIcon(scaled));
    }

    private void saveSiblingAndMetadataToVault(File vaultDir, File destJson, BlueprintEntry entry, BlueprintPreviewCache previewCache) {
        String base = destJson.getName().substring(0, Math.max(0, destJson.getName().lastIndexOf('.')));
        boolean imgSaved = false;
        File sourceDir = entry.filePath != null ? new File(entry.filePath).getParentFile() : null;
        if (sourceDir != null && sourceDir.exists()) {
            for (String ext : List.of(".png", ".jpg", ".jpeg", ".webp", ".gif")) {
                File img = new File(sourceDir, base + ext);
                if (img.exists()) {
                    try {
                        Files.copy(img.toPath(), new File(vaultDir, base + ext).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        imgSaved = true;
                        break;
                    } catch (Exception ignored) {}
                }
            }
        }

        if (!imgSaved && entry.previewPath != null && entry.previewPath.startsWith("http")) {
            byte[] imgBytes = WorkflowImageLoader.loadImageBytes(entry.previewPath);
            if (imgBytes != null && imgBytes.length > 0) {
                String ext = entry.previewPath.toLowerCase().endsWith(".webp") ? ".webp" : ".png";
                try {
                    Files.write(new File(vaultDir, base + ext).toPath(), imgBytes);
                    imgSaved = true;
                } catch (Exception ignored) {}
            }
        }

        if (!imgSaved && previewCache != null) {
            Image mi = previewCache.getPreview(entry.filePath);
            if (mi == null && entry.previewPath != null) mi = previewCache.getPreview(entry.previewPath);
            if (mi != null) {
                try {
                    BufferedImage bi = new BufferedImage(mi.getWidth(null), mi.getHeight(null), BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g2 = bi.createGraphics();
                    g2.drawImage(mi, 0, 0, null);
                    g2.dispose();
                    ImageIO.write(bi, "png", new File(vaultDir, base + ".png"));
                } catch (Exception ignored) {}
            }
        }

        try {
            VaultWorkflowMetadata meta = new VaultWorkflowMetadata(
                    base, entry.name, entry.description, "Text to Image",
                    entry.category != null ? entry.category : "General",
                    "COMFY_BLUEPRINT", entry.filePath, base + ".png",
                    entry.requiredModels != null ? entry.requiredModels : List.of(), System.currentTimeMillis()
            );
            SHARED_MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(vaultDir, base + ".meta.json"), meta);
        } catch (Exception ignored) {}
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
}
