package de.tki.comfyuicompanion.ui.blueprint;

import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import de.tki.comfyuicompanion.service.ILocalModelValidator;
import de.tki.comfyuicompanion.service.IModelSearchService;
import de.tki.comfyuicompanion.service.IWorkflowDownloader;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintEntry;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintStatus;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Modal dialog for inspecting blueprint metadata, required local models,
 * downloading missing models, and launching the workflow in ComfyUI.
 */
public class BlueprintDetailsDialog extends JDialog {
    private static final Logger logger = LoggerFactory.getLogger(BlueprintDetailsDialog.class);

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
        super(owner instanceof Frame ? (Frame) owner : null, "Blueprint: " + entry.name, true);
        setSize(1250, 720);
        setLocationRelativeTo(owner);

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
            JPanel header = new JPanel(new BorderLayout(8, 0));
            header.setOpaque(false);
            header.add(title, BorderLayout.WEST);
            JButton btnPlayPreview = new JButton("Play Preview", SvgIconFactory.get(AppIcon.PLAY, 14));
            btnPlayPreview.setFont(new Font("SansSerif", Font.BOLD, 11));
            btnPlayPreview.setBackground(new Color(0, 150, 136));
            btnPlayPreview.setForeground(Color.WHITE);
            btnPlayPreview.setFocusPainted(false);
            btnPlayPreview.putClientProperty("Button.arc", 999);
            btnPlayPreview.addActionListener(evt -> {
                if (playVideoAction != null) playVideoAction.accept(entry, btnPlayPreview);
            });
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

        Image previewImg = previewCache != null ? previewCache.getPreview(entry.filePath) : null;
        if (previewImg != null) {
            JLabel imgLabel = new JLabel();
            imgLabel.setAlignmentX(JComponent.CENTER_ALIGNMENT);
            int targetW = 1100;
            int imgW = previewImg.getWidth(null);
            int imgH = previewImg.getHeight(null);
            if (imgW > 0 && imgH > 0) {
                int targetH = (int) (imgH * ((double) targetW / imgW));
                if (targetH > 280) {
                    targetH = 280;
                    targetW = (int) (imgW * ((double) targetH / imgH));
                }
                BufferedImage bImg = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = bImg.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
                g2.drawImage(previewImg, 0, 0, targetW, targetH, null);
                g2.dispose();
                imgLabel.setIcon(new ImageIcon(bImg));

                JPanel imgPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
                imgPanel.setOpaque(false);
                imgPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                imgPanel.setBorder(new EmptyBorder(0, 0, 14, 0));
                imgPanel.add(imgLabel);
                body.add(imgPanel);
                setSize(1250, 860);
            }
        }

        if (!entry.category.isEmpty()) addRow(body, "Category", entry.category, textSecondary, textPrimary);
        if (!entry.description.isEmpty()) {
            JTextArea desc = new JTextArea(entry.description);
            desc.setFont(new Font("SansSerif", Font.PLAIN, 11));
            desc.setForeground(textSecondary);
            desc.setLineWrap(true);
            desc.setWrapStyleWord(true);
            desc.setEditable(false);
            desc.setFocusable(false);
            desc.setOpaque(false);
            desc.setBorder(null);
            desc.setAlignmentX(Component.LEFT_ALIGNMENT);
            desc.setMaximumSize(new Dimension(1150, Integer.MAX_VALUE));

            body.add(Box.createVerticalStrut(6));
            body.add(desc);
            body.add(Box.createVerticalStrut(10));
        }

        addRow(body, "Required models", String.valueOf(entry.requiredModels.size()), textSecondary, textPrimary);
        addRow(body, "Available locally", String.valueOf(status.presentCount), textSecondary, textPrimary);
        addRow(body, "Missing", String.valueOf(status.missingCount), textSecondary, textPrimary);

        if (entry.requiredModels.isEmpty()) {
            if (!entry.hasBeenValidated) {
                entry.hasBeenValidated = true;
                body.add(Box.createVerticalStrut(12));
                JLabel loadingLbl = new JLabel("Analyzing workflow JSON for required models...", SvgIconFactory.get(AppIcon.REFRESH, 14), SwingConstants.LEFT);
                loadingLbl.setFont(new Font("SansSerif", Font.ITALIC, 12));
                loadingLbl.setForeground(textSecondary);
                body.add(loadingLbl);
                if (localModelValidator != null) {
                    localModelValidator.validateWorkflowModelsAsync(entry.registryWorkflow).thenAccept(v -> {
                        SwingUtilities.invokeLater(() -> {
                            entry.requiredModels.clear();
                            if (entry.registryWorkflow.getRequiredModelInfos() != null) {
                                entry.requiredModels.addAll(entry.registryWorkflow.getRequiredModelInfos());
                            }
                            dispose();
                            if (reloadDetailsAction != null) {
                                reloadDetailsAction.accept(entry, null);
                            }
                        });
                    });
                }
            } else {
                body.add(Box.createVerticalStrut(12));
                JLabel noModelsLbl = new JLabel("No external models required for this workflow.", SvgIconFactory.get(AppIcon.CHECK, 14), SwingConstants.LEFT);
                noModelsLbl.setFont(new Font("SansSerif", Font.ITALIC, 12));
                noModelsLbl.setForeground(new Color(34, 197, 130));
                body.add(noModelsLbl);
            }
        } else {
            body.add(Box.createVerticalStrut(12));
            JLabel hdr = new JLabel("Required Models Status:");
            hdr.setFont(new Font("SansSerif", Font.PLAIN, 12));
            hdr.setForeground(textPrimary);
            hdr.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(hdr);
            body.add(Box.createVerticalStrut(6));

            body.add(new BlueprintModelTablePanel(entry, bg, textPrimary, textSecondary,
                    modelStatusResolver, modelSizeResolver, modelSearchService));
        }

        JScrollPane scroll = new JScrollPane(body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        root.add(scroll, BorderLayout.CENTER);

        JButton btnClose = new JButton("Close");
        btnClose.addActionListener(e -> dispose());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        bottom.setBackground(bg);

        JButton btnDlWorkflow = new JButton("Download Workflow", SvgIconFactory.get(AppIcon.DOWNLOAD, 14));
        btnDlWorkflow.setFont(new Font("SansSerif", Font.BOLD, 11));
        btnDlWorkflow.putClientProperty("Button.arc", 999);
        btnDlWorkflow.setBackground(new Color(0, 150, 136));
        btnDlWorkflow.setForeground(Color.WHITE);
        btnDlWorkflow.addActionListener(e -> {
            if (entry.filePath != null) {
                File localFile = new File(entry.filePath);
                if (localFile.exists() && localFile.isFile() && localFile.length() > 0) {
                    dispose();
                    if (onWorkflowImported != null) onWorkflowImported.accept(localFile);
                    return;
                }
            }

            if (entry.registryWorkflow == null) {
                return;
            }

            btnDlWorkflow.setEnabled(false);
            btnDlWorkflow.setText("Downloading...");
            workflowDownloader.downloadWorkflowAsync(entry.registryWorkflow)
                    .thenAccept(file -> SwingUtilities.invokeLater(() -> {
                        btnDlWorkflow.setText("Downloaded");
                        btnDlWorkflow.setIcon(SvgIconFactory.get(AppIcon.CHECK));
                        dispose();
                        if (onWorkflowImported != null) onWorkflowImported.accept(file);
                    }))
                    .exceptionally(ex -> {
                        SwingUtilities.invokeLater(() -> {
                            btnDlWorkflow.setEnabled(true);
                            btnDlWorkflow.setText("Download Workflow");
                            btnDlWorkflow.setIcon(SvgIconFactory.get(AppIcon.DOWNLOAD));
                            JOptionPane.showMessageDialog(this,
                                    "Failed to download workflow: " + ex.getCause().getMessage(),
                                    "Download Error", JOptionPane.ERROR_MESSAGE);
                        });
                        return null;
                    });
        });

        JButton btnSendToComfy = new JButton("Send to ComfyUI", SvgIconFactory.get(AppIcon.SEND));
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
            BlueprintComfySender.sendToComfyUI(entry, workflowDownloader, lifecycleService, configService,
                    statusText -> btnSendToComfy.setText(statusText), this, success -> {
                        btnSendToComfy.setEnabled(true);
                        btnSendToComfy.setText("Send to ComfyUI");
                        btnSendToComfy.setIcon(SvgIconFactory.get(AppIcon.SEND));
                        if (Boolean.TRUE.equals(success)) {
                            dispose();
                        }
                    });
        });

        bottom.add(btnDlWorkflow);
        bottom.add(btnSendToComfy);
        bottom.add(btnClose);
        root.add(bottom, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private static void addRow(JPanel parent, String label, String value, Color textSecondary, Color textPrimary) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
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
}
