package de.tki.comfyuicompanion.ui.blueprint;

import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintEntry;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintStatus;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Builds individual visual card panels for displaying workflows in the gallery grid.
 */
public class BlueprintCardBuilder {

    private static final Color GREEN_READY     = new Color(34, 197, 130);
    private static final Color AMBER_PARTIAL   = new Color(255, 153, 0);

    private static final int CARD_W     = 220;
    private static final int CARD_H     = 280;
    private static final int PREVIEW_H  = 140;

    public static JPanel buildCard(BlueprintEntry entry,
                                   BlueprintStatus status,
                                   Color[] palette,
                                   BlueprintPreviewCache previewCache,
                                   Color cardBg,
                                   Color cardBorder,
                                   Color cardHoverBorder,
                                   Color textPrimary,
                                   Color textDim,
                                   String icon,
                                   Consumer<BlueprintEntry> showDetailsAction,
                                   BiConsumer<BlueprintEntry, Window> playVideoAction) {

        JPanel card = new JPanel(new BorderLayout()) {
            private boolean hovered = false;

            {
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hovered = true;  repaint(); }
                    @Override public void mouseExited(MouseEvent e)  { hovered = false; repaint(); }
                    @Override public void mouseClicked(MouseEvent e) {
                        if (e.getClickCount() == 1 && showDetailsAction != null) {
                            showDetailsAction.accept(entry);
                        }
                    }
                });
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                g2.setColor(cardBg);
                g2.fillRect(0, 0, getWidth(), getHeight());

                g2.setStroke(new BasicStroke(hovered ? 2f : 1f));
                if (hovered) {
                    g2.setColor(new Color(cardHoverBorder.getRed(), cardHoverBorder.getGreen(), cardHoverBorder.getBlue(), 100));
                    g2.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
                    g2.drawRect(2, 2, getWidth() - 5, getHeight() - 5);
                }

                g2.setColor(hovered ? cardHoverBorder : cardBorder);
                g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                g2.dispose();
            }
        };

        card.setOpaque(false);
        card.setPreferredSize(new Dimension(CARD_W, CARD_H));
        card.setMinimumSize(new Dimension(160, CARD_H));
        card.setMaximumSize(new Dimension(400, CARD_H));

        JPanel preview = buildPreviewPanel(entry, palette, status, PREVIEW_H, previewCache,
                cardBorder, textPrimary, icon, showDetailsAction, playVideoAction);
        card.add(preview, BorderLayout.NORTH);

        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setOpaque(false);
        info.setBorder(new EmptyBorder(8, 10, 8, 10));

        JTextArea lblName = new JTextArea(entry.name);
        lblName.setFont(new Font("SansSerif", Font.BOLD, 12));
        lblName.setForeground(textPrimary);
        lblName.setLineWrap(true);
        lblName.setWrapStyleWord(true);
        lblName.setEditable(false);
        lblName.setFocusable(false);
        lblName.setOpaque(false);
        lblName.setBorder(null);
        lblName.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        lblName.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (showDetailsAction != null) showDetailsAction.accept(entry);
            }
        });
        info.add(lblName);
        info.add(Box.createVerticalStrut(3));

        if (!entry.category.isEmpty()) {
            JLabel lblCat = new JLabel(entry.category);
            lblCat.setFont(new Font("SansSerif", Font.PLAIN, 10));
            lblCat.setForeground(textDim);
            info.add(lblCat);
            info.add(Box.createVerticalStrut(4));
        }

        int totalReqs = entry.requiredModels.size();
        String reqText;
        Color reqColor;
        if (totalReqs == 0) {
            reqText  = "✔  No external models needed";
            reqColor = GREEN_READY;
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

        info.add(statusRow);
        card.add(info, BorderLayout.CENTER);

        return card;
    }

    private static JPanel buildPreviewPanel(BlueprintEntry entry,
                                            Color[] palette,
                                            BlueprintStatus status,
                                            int height,
                                            BlueprintPreviewCache previewCache,
                                            Color cardBorder,
                                            Color textPrimary,
                                            String icon,
                                            Consumer<BlueprintEntry> showDetailsAction,
                                            BiConsumer<BlueprintEntry, Window> playVideoAction) {
        return new JPanel() {
            {
                setPreferredSize(new Dimension(CARD_W, height));
                setOpaque(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) {
                        if (entry.registryWorkflow != null && entry.registryWorkflow.isVideoPreview()) {
                            if (e.getClickCount() == 2 && playVideoAction != null) {
                                playVideoAction.accept(entry, javax.swing.SwingUtilities.getWindowAncestor((java.awt.Component) e.getSource()));
                            } else if (e.getClickCount() == 1 && showDetailsAction != null) {
                                showDetailsAction.accept(entry);
                            }
                        } else if (e.getClickCount() == 1 && showDetailsAction != null) {
                            showDetailsAction.accept(entry);
                        }
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,   RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING,      RenderingHints.VALUE_RENDER_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int w = getWidth(), h = getHeight();
                if (w <= 0 || h <= 0) {
                    g2.dispose();
                    return;
                }
                g2.setClip(0, 0, w, h);

                Image img = previewCache != null ? previewCache.getPreview(entry.filePath) : null;
                if (img != null) {
                    String scaledKey = entry.filePath + "_" + w + "_" + h;
                    Image scaledImg = previewCache.getScaledPreview(scaledKey);
                    if (scaledImg == null) {
                        int imgW = img.getWidth(null);
                        int imgH = img.getHeight(null);
                        if (imgW > 0 && imgH > 0) {
                            double scale = Math.max((double) w / imgW, (double) h / imgH);
                            int newW = (int) (imgW * scale);
                            int newH = (int) (imgH * scale);
                            BufferedImage bufferedScaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                            Graphics2D bg2 = bufferedScaled.createGraphics();
                            bg2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                            bg2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                            bg2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                            int x = (w - newW) / 2;
                            int y = (h - newH) / 2;
                            bg2.drawImage(img, x, y, newW, newH, null);
                            bg2.dispose();
                            scaledImg = bufferedScaled;
                            previewCache.putScaledPreview(scaledKey, scaledImg);
                        }
                    }
                    if (scaledImg != null) {
                        g2.drawImage(scaledImg, 0, 0, null);
                    } else if (previewCache != null) {
                        previewCache.paintBokehFallback(g2, w, h, palette, entry, cardBorder, textPrimary, icon);
                    }
                } else if (previewCache != null) {
                    previewCache.triggerImageLoad(entry, this);
                    previewCache.paintBokehFallback(g2, w, h, palette, entry, cardBorder, textPrimary, icon);
                }

                // Status badge
                String badgeText = null;
                Color badgeColor = null;
                if (status.missingCount == 0) {
                    badgeText  = "✅  Ready";
                    badgeColor = new Color(34, 197, 130, 220);
                } else if (status.missingCount > 0) {
                    badgeText  = "⚠  " + status.missingCount + " missing";
                    badgeColor = new Color(255, 153, 0, 220);
                }

                if (badgeText != null) {
                    g2.setFont(new Font("SansSerif", Font.BOLD, 9));
                    FontMetrics fm = g2.getFontMetrics();
                    int bw = fm.stringWidth(badgeText) + 10;
                    int bh = 16;
                    int bx = w - bw - 6;
                    int by = 6;
                    g2.setColor(badgeColor);
                    g2.fillRect(bx, by, bw, bh);
                    g2.setColor(Color.WHITE);
                    g2.drawString(badgeText, bx + 5, by + bh - 4);
                }

                // Video preview badge
                if (entry.registryWorkflow != null && entry.registryWorkflow.isVideoPreview()) {
                    int pad = 6;
                    int size = 24;
                    int cx = pad + size / 2;
                    int cy = h - pad - size / 2;
                    g2.setColor(new Color(0, 0, 0, 110));
                    g2.fillOval(cx - size / 2, cy - size / 2, size, size);

                    int tx = cx - 4;
                    int ty = cy - 5;
                    int tw = 8;
                    int th = 10;
                    Polygon tri = new Polygon(new int[]{tx, tx, tx + tw}, new int[]{ty, ty + th, ty + th / 2}, 3);
                    g2.setColor(Color.WHITE);
                    g2.fill(tri);

                    g2.setFont(new Font("SansSerif", Font.BOLD, 8));
                    g2.setColor(new Color(0, 0, 0, 150));
                    g2.drawString("VIDEO", pad + 30, h - pad - 4);
                }

                g2.dispose();
            }
        };
    }
}
