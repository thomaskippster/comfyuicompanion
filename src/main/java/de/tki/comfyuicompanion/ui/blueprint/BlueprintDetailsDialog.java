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

            String[] columns = {"Model Name", "Type", "Size", "Status"};
            DefaultTableModel modelTblModel = new DefaultTableModel(columns, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };

            for (int i = 0; i < entry.requiredModels.size(); i++) {
                ModelInfo info = entry.requiredModels.get(i);
                String name = info.getName();
                String type = info.getType() != null ? info.getType() : "checkpoints";
                String size = modelSizeResolver != null ? modelSizeResolver.apply(info) : "Unknown";
                String statusStr = modelStatusResolver != null ? modelStatusResolver.apply(info) : "Unknown";
                modelTblModel.addRow(new Object[]{name, type, size, statusStr});

                if ("Unknown".equalsIgnoreCase(size) && info.getUrl() != null && !info.getUrl().isBlank() && !"MISSING".equals(info.getUrl()) && modelSearchService != null) {
                    final int rowIndex = i;
                    CompletableFuture.supplyAsync(() -> {
                        long bytes = modelSearchService.getRemoteSize(info.getUrl());
                        return modelSearchService.formatSize(bytes);
                    }).thenAccept(resolvedSize -> {
                        if (!"Unknown".equalsIgnoreCase(resolvedSize) && !resolvedSize.contains("Auth")) {
                            info.setSize(resolvedSize);
                            SwingUtilities.invokeLater(() -> {
                                if (rowIndex < modelTblModel.getRowCount()) {
                                    modelTblModel.setValueAt(resolvedSize, rowIndex, 2);
                                }
                            });
                        }
                    }).exceptionally(ex -> {
                        logger.warn("Failed to fetch remote size for model: " + name, ex);
                        return null;
                    });
                }
            }

            JTable table = new JTable(modelTblModel);
            table.setFont(new Font("SansSerif", Font.PLAIN, 11));
            table.setRowHeight(24);
            table.setShowGrid(false);
            table.setIntercellSpacing(new Dimension(0, 0));
            table.setBackground(bg);
            table.setForeground(textPrimary);
            table.getTableHeader().setBackground(bg);
            table.getTableHeader().setForeground(textSecondary);
            table.getTableHeader().setFont(new Font("SansSerif", Font.PLAIN, 11));
            table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

            table.getColumnModel().getColumn(0).setPreferredWidth(620);
            table.getColumnModel().getColumn(0).setMinWidth(350);
            table.getColumnModel().getColumn(1).setPreferredWidth(150);
            table.getColumnModel().getColumn(1).setMinWidth(100);
            table.getColumnModel().getColumn(2).setPreferredWidth(120);
            table.getColumnModel().getColumn(2).setMinWidth(80);
            table.getColumnModel().getColumn(3).setPreferredWidth(280);
            table.getColumnModel().getColumn(3).setMinWidth(180);

            table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable t, Object val, boolean isSel, boolean hasFoc, int row, int col) {
                    Component c = super.getTableCellRendererComponent(t, val, isSel, hasFoc, row, col);
                    c.setBackground(row % 2 == 0 ? bg : new Color(Math.max(0, bg.getRed() - 5), Math.max(0, bg.getGreen() - 5), Math.max(0, bg.getBlue() - 5)));
                    c.setForeground(textPrimary);

                    if (col == 3 && val != null) {
                        String st = val.toString();
                        if ("✅ Already exists".equals(st)) {
                            c.setForeground(new Color(0, 180, 160));
                        } else if ("📦 Archived".equals(st)) {
                            c.setForeground(new Color(224, 169, 109));
                        } else if ("🔄 Size Mismatch".equals(st)) {
                            c.setForeground(new Color(226, 88, 88));
                        } else if ("Idle".equals(st)) {
                            c.setForeground(textSecondary);
                        } else {
                            c.setForeground(new Color(0, 150, 186));
                        }
                    }
                    setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
                    return c;
                }
            });

            JScrollPane tblScroll = new JScrollPane(table);
            int calcHeight = Math.min(220, Math.max(80, (entry.requiredModels.size() + 1) * 26 + 6));
            tblScroll.setPreferredSize(new Dimension(1190, calcHeight));
            tblScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, calcHeight));
            tblScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
            tblScroll.getViewport().setBackground(bg);
            body.add(tblScroll);
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
            btnSendToComfy.setText("Downloading...");
            workflowDownloader.downloadWorkflowAsync(entry.registryWorkflow)
                    .thenAccept(file -> {
                        try {
                            if (lifecycleService != null && !lifecycleService.isHealthy()) {
                                SwingUtilities.invokeLater(() -> btnSendToComfy.setText("Starting ComfyUI..."));
                                logger.info("🔌 [BlueprintGallery] ComfyUI server is offline. Starting lifecycle service...");
                                lifecycleService.start();
                                int maxWait = 90;
                                boolean healthy = false;
                                for (int w = 0; w < maxWait; w++) {
                                    if (lifecycleService.isHealthy()) {
                                        healthy = true;
                                        break;
                                    }
                                    try {
                                        Thread.sleep(1000);
                                    } catch (InterruptedException ie) {
                                        Thread.currentThread().interrupt();
                                        break;
                                    }
                                }
                                if (!healthy) {
                                    throw new Exception("ComfyUI server did not start within 90 seconds.");
                                }
                            }

                            SwingUtilities.invokeLater(() -> btnSendToComfy.setText("Sending..."));
                            String jsonContent = java.nio.file.Files.readString(file.toPath());
                            boolean isUIFormatDetected = false;
                            try {
                                org.json.JSONObject obj = new org.json.JSONObject(jsonContent);
                                if (obj.has("nodes") && obj.has("links")) {
                                    isUIFormatDetected = true;
                                }
                            } catch (Exception ignored) {}
                            final boolean isUIFormat = isUIFormatDetected;

                            String comfyUrl = configService.getComfyUIUrl();
                            if (comfyUrl.endsWith("/")) {
                                comfyUrl = comfyUrl.substring(0, comfyUrl.length() - 1);
                            }
                            String targetEndpoint = isUIFormat ? comfyUrl + "/bridge/load-workflow" : comfyUrl + "/prompt";

                            HttpRequest request = HttpRequest.newBuilder()
                                    .uri(URI.create(targetEndpoint))
                                    .header("Content-Type", "application/json")
                                    .POST(HttpRequest.BodyPublishers.ofString(jsonContent))
                                    .build();

                            HttpClient client = HttpClient.newHttpClient();
                            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                            SwingUtilities.invokeLater(() -> {
                                btnSendToComfy.setEnabled(true);
                                btnSendToComfy.setText("Send to ComfyUI");
                                btnSendToComfy.setIcon(SvgIconFactory.get(AppIcon.SEND));

                                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                                    if (isUIFormat) {
                                        JOptionPane.showMessageDialog(this,
                                                "Workflow successfully loaded into ComfyUI browser tab!",
                                                "Success", JOptionPane.INFORMATION_MESSAGE);
                                    } else {
                                        JOptionPane.showMessageDialog(this,
                                                "Workflow successfully sent to ComfyUI and queued!",
                                                "Success", JOptionPane.INFORMATION_MESSAGE);
                                    }
                                    dispose();
                                } else {
                                    if (isUIFormat && response.statusCode() == 404) {
                                        JOptionPane.showMessageDialog(this,
                                                "ComfyUI Bridge is not installed or outdated.\n" +
                                                        "Please install/update the ComfyUI Bridge in Settings -> Install ComfyUI Bridge.",
                                                "Bridge Not Found", JOptionPane.WARNING_MESSAGE);
                                    } else {
                                        JOptionPane.showMessageDialog(this,
                                                "Failed to send to ComfyUI.\nStatus: " + response.statusCode() + "\nResponse: " + response.body(),
                                                "Error", JOptionPane.ERROR_MESSAGE);
                                    }
                                }
                            });
                        } catch (Exception ex) {
                            String msg = ex.getMessage();
                            if (ex instanceof java.net.ConnectException || (ex.getCause() != null && ex.getCause() instanceof java.net.ConnectException)) {
                                msg = "Could not connect to ComfyUI. Please make sure the ComfyUI server is running and reachable at: " + configService.getComfyUIUrl();
                            }
                            final String finalMsg = msg;
                            SwingUtilities.invokeLater(() -> {
                                btnSendToComfy.setEnabled(true);
                                btnSendToComfy.setText("Send to ComfyUI");
                                btnSendToComfy.setIcon(SvgIconFactory.get(AppIcon.SEND));
                                JOptionPane.showMessageDialog(this, finalMsg, "Connection Error", JOptionPane.ERROR_MESSAGE);
                            });
                        }
                    })
                    .exceptionally(ex -> {
                        SwingUtilities.invokeLater(() -> {
                            btnSendToComfy.setEnabled(true);
                            btnSendToComfy.setText("Send to ComfyUI");
                            btnSendToComfy.setIcon(SvgIconFactory.get(AppIcon.SEND));
                            JOptionPane.showMessageDialog(this,
                                    "Failed to download workflow: " + ex.getCause().getMessage(),
                                    "Download Error", JOptionPane.ERROR_MESSAGE);
                        });
                        return null;
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
