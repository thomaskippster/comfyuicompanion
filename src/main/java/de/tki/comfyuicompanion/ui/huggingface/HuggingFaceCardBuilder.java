package de.tki.comfyuicompanion.ui.huggingface;

import de.tki.comfyuicompanion.domain.HuggingFaceModelEntry;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Builds individual visual cards for Hugging Face repositories in the gallery grid,
 * matching the dimensions, hover mechanics, status indicators, and responsive layout of the Blueprint Gallery.
 */
public class HuggingFaceCardBuilder {

    private static final int CARD_WIDTH = 220;
    private static final int CARD_HEIGHT = 280;
    private static final int THUMBNAIL_HEIGHT = 140;

    private static final Color GREEN_READY = new Color(34, 197, 130);
    private static final Color AMBER_PARTIAL = new Color(255, 153, 0);
    private static final Color BLUE_DOWNLOADING = new Color(59, 130, 246);

    /**
     * Constructs and returns a styled card panel for the provided Hugging Face model repository.
     *
     * @param entry            the repository metadata entry
     * @param isInstalled      whether any or all models are installed locally
     * @param isDownloading    whether a download is currently active for this repo
     * @param downloadProgress current progress string (e.g. "45%") or null
     * @param darkMode         true if dark mode theme is active
     * @param onDownload       callback triggered when user clicks Download
     * @param onDetails        callback triggered when user clicks Details or card
     * @return fully configured JPanel card component
     */
    public static JPanel buildCard(
            HuggingFaceModelEntry entry,
            boolean isInstalled,
            boolean isDownloading,
            String downloadProgress,
            boolean darkMode,
            Consumer<HuggingFaceModelEntry> onDownload,
            Consumer<HuggingFaceModelEntry> onDetails) {

        Color cardBg = darkMode ? new Color(24, 26, 34) : new Color(255, 255, 255);
        Color cardBorder = darkMode ? new Color(48, 52, 68) : new Color(220, 224, 232);
        Color hoverBorder = new Color(0, 210, 190);
        Color textPrimary = darkMode ? new Color(228, 230, 240) : new Color(30, 32, 40);
        Color textDim = darkMode ? new Color(130, 136, 160) : new Color(100, 105, 120);

        JPanel card = new JPanel(new BorderLayout()) {
            private boolean hovered = false;

            {
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hovered = true;
                        repaint();
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hovered = false;
                        repaint();
                    }

                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (e.getClickCount() == 1 && onDetails != null) {
                            onDetails.accept(entry);
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
                    g2.setColor(new Color(hoverBorder.getRed(), hoverBorder.getGreen(), hoverBorder.getBlue(), 100));
                    g2.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
                    g2.drawRect(2, 2, getWidth() - 5, getHeight() - 5);
                }

                g2.setColor(hovered ? hoverBorder : cardBorder);
                g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                g2.dispose();
            }
        };

        card.setOpaque(false);
        card.setPreferredSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
        card.setMinimumSize(new Dimension(160, CARD_HEIGHT));
        card.setMaximumSize(new Dimension(400, CARD_HEIGHT));

        // 1. Thumbnail Panel (North)
        JPanel thumbnailPanel = buildThumbnailPanel(entry, darkMode, onDetails);
        card.add(thumbnailPanel, BorderLayout.NORTH);

        // 2. Info / Content Panel (Center)
        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setOpaque(false);
        info.setBorder(new EmptyBorder(8, 10, 8, 10));

        // Model Title
        String displayName = entry.modelName() != null && !entry.modelName().isBlank() ? entry.modelName() : entry.repoId();
        JLabel titleLabel = new JLabel(truncate(displayName, 34));
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        titleLabel.setForeground(textPrimary);
        titleLabel.setToolTipText(entry.repoId());
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(titleLabel);
        info.add(Box.createVerticalStrut(3));

        // Subtitle (Author • Task)
        String taskStr = (entry.pipelineTag() != null && !entry.pipelineTag().isBlank())
                ? entry.pipelineTag()
                : "Weights";
        JLabel subtitleLabel = new JLabel(entry.author() + " • " + taskStr);
        subtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        subtitleLabel.setForeground(textDim);
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(subtitleLabel);
        info.add(Box.createVerticalStrut(6));

        // Status Row
        String statusText;
        Color statusColor;
        if (isDownloading) {
            statusText = "⏳  " + (downloadProgress != null && !downloadProgress.isBlank() ? downloadProgress : "Downloading...");
            statusColor = BLUE_DOWNLOADING;
        } else if (isInstalled) {
            statusText = "✔  Ready to use";
            statusColor = GREEN_READY;
        } else {
            statusText = "⚠  Missing models";
            statusColor = AMBER_PARTIAL;
        }

        JLabel statusLabel = new JLabel(statusText);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        statusLabel.setForeground(statusColor);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(statusLabel);
        info.add(Box.createVerticalStrut(6));

        // Meta / Stats Row
        JPanel statsRow = new JPanel(new BorderLayout());
        statsRow.setOpaque(false);
        statsRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        String statsText = String.format("❤️ %s • 📥 %s", formatCount(entry.likes()), formatCount(entry.downloads()));
        JLabel statsLabel = new JLabel(statsText);
        statsLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        statsLabel.setForeground(textDim);
        statsRow.add(statsLabel, BorderLayout.WEST);

        String filesCountText = (entry.files() != null && !entry.files().isEmpty())
                ? entry.files().size() + " files"
                : "Quantizations";
        JLabel filesLabel = new JLabel(filesCountText);
        filesLabel.setFont(new Font("SansSerif", Font.BOLD, 10));
        filesLabel.setForeground(textDim);
        statsRow.add(filesLabel, BorderLayout.EAST);

        info.add(statsRow);
        card.add(info, BorderLayout.CENTER);

        return card;
    }

    private static JPanel buildThumbnailPanel(HuggingFaceModelEntry entry, boolean darkMode, Consumer<HuggingFaceModelEntry> onDetails) {
        return new JPanel(new BorderLayout()) {
            {
                setPreferredSize(new Dimension(CARD_WIDTH, THUMBNAIL_HEIGHT));
                setOpaque(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (onDetails != null) onDetails.accept(entry);
                    }
                });

                // Top Badge Overlay
                JPanel badgeOverlay = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
                badgeOverlay.setOpaque(false);
                badgeOverlay.add(createPill(entry.author(), new Color(79, 70, 229, 210), Color.WHITE));
                if (isGgufRepo(entry)) {
                    badgeOverlay.add(createPill("GGUF", new Color(16, 185, 129, 210), Color.WHITE));
                }
                add(badgeOverlay, BorderLayout.NORTH);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                int w = getWidth();
                int h = getHeight();

                Image thumb = HuggingFaceThumbnailGenerator.getThumbnail(entry, darkMode, this);
                if (thumb != null) {
                    int imgW = thumb.getWidth(null);
                    int imgH = thumb.getHeight(null);
                    if (imgW > 0 && imgH > 0) {
                        double scale = Math.max((double) w / imgW, (double) h / imgH);
                        int nw = (int) (imgW * scale);
                        int nh = (int) (imgH * scale);
                        int x = (w - nw) / 2;
                        int y = (h - nh) / 2;
                        g2.setClip(0, 0, w, h);
                        g2.drawImage(thumb, x, y, nw, nh, null);
                    }
                } else {
                    Color startGrad = darkMode ? new Color(30, 34, 48) : new Color(225, 230, 240);
                    Color endGrad = darkMode ? new Color(18, 20, 28) : new Color(200, 208, 225);
                    g2.setPaint(new GradientPaint(0, 0, startGrad, 0, h, endGrad));
                    g2.fillRect(0, 0, w, h);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
    }

    private static boolean isGgufRepo(HuggingFaceModelEntry entry) {
        String repoLower = entry.repoId() != null ? entry.repoId().toLowerCase(Locale.ROOT) : "";
        String nameLower = entry.modelName() != null ? entry.modelName().toLowerCase(Locale.ROOT) : "";
        if (repoLower.contains("gguf") || nameLower.contains("gguf")) {
            return true;
        }
        if (entry.tags() != null) {
            for (String tag : entry.tags()) {
                if ("gguf".equalsIgnoreCase(tag)) return true;
            }
        }
        return false;
    }

    private static JLabel createPill(String text, Color bg, Color fg) {
        JLabel pill = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        pill.setFont(new Font("SansSerif", Font.BOLD, 9));
        pill.setForeground(fg);
        pill.setBorder(new EmptyBorder(2, 6, 2, 6));
        pill.setOpaque(false);
        return pill;
    }

    private static String formatCount(int count) {
        if (count >= 1_000_000) return String.format("%.1fM", count / 1_000_000.0);
        if (count >= 1_000) return String.format("%.1fk", count / 1_000.0);
        return String.valueOf(count);
    }

    private static String truncate(String text, int maxChars) {
        if (text == null) return "";
        return text.length() <= maxChars ? text : text.substring(0, maxChars - 1) + "…";
    }
}



