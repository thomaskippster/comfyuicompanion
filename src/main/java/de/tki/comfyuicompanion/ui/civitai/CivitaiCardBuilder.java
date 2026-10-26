package de.tki.comfyuicompanion.ui.civitai;

import de.tki.comfyuicompanion.domain.CivitaiModelEntry;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Builds individual visual cards for displaying Civitai models in the gallery grid,
 * matching the dimensions, responsive hover effects, status indicators, and design language of the Blueprint Gallery.
 */
public class CivitaiCardBuilder {

    private static final int CARD_WIDTH = 220;
    private static final int CARD_HEIGHT = 280;
    private static final int THUMBNAIL_HEIGHT = 140;

    private static final Color GREEN_READY = new Color(34, 197, 130);
    private static final Color AMBER_PARTIAL = new Color(255, 153, 0);
    private static final Color BLUE_DOWNLOADING = new Color(59, 130, 246);

    private static final Map<String, Image> IMAGE_CACHE = new ConcurrentHashMap<>();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Constructs and returns a styled card panel for the provided Civitai model entry.
     *
     * @param entry            the Civitai model metadata entry
     * @param isInstalled      whether this model is already installed locally
     * @param isDownloading    whether this model is currently downloading
     * @param downloadProgress current download progress string (or empty)
     * @param darkMode         true if dark mode theme is active
     * @param onDownload       callback triggered when user clicks Download
     * @param onDetails        callback triggered when user clicks Details or card
     * @return fully configured JPanel card component
     */
    public static JPanel buildCard(CivitaiModelEntry entry,
                                   boolean isInstalled,
                                   boolean isDownloading,
                                   String downloadProgress,
                                   boolean darkMode,
                                   Consumer<CivitaiModelEntry> onDownload,
                                   Consumer<CivitaiModelEntry> onDetails) {

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

        // Title
        JLabel titleLabel = new JLabel(truncate(entry.name(), 34));
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        titleLabel.setForeground(textPrimary);
        titleLabel.setToolTipText(entry.name());
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(titleLabel);
        info.add(Box.createVerticalStrut(3));

        // Subtitle (Type & Base Model)
        String subtitleText = (entry.baseModel() != null && !entry.baseModel().isBlank())
                ? entry.type() + " • " + entry.baseModel()
                : entry.type();
        JLabel subtitleLabel = new JLabel(subtitleText);
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
            statusText = "⚠  Needs download";
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

        String statsText = String.format("⭐ %.1f • ⬇ %s", entry.rating(), formatCount(entry.downloadCount()));
        JLabel statsLabel = new JLabel(statsText);
        statsLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        statsLabel.setForeground(textDim);
        statsRow.add(statsLabel, BorderLayout.WEST);

        JLabel sizeLabel = new JLabel(entry.formattedSize());
        sizeLabel.setFont(new Font("SansSerif", Font.BOLD, 10));
        sizeLabel.setForeground(textDim);
        statsRow.add(sizeLabel, BorderLayout.EAST);

        info.add(statsRow);
        card.add(info, BorderLayout.CENTER);

        return card;
    }

    private static JPanel buildThumbnailPanel(CivitaiModelEntry entry, boolean darkMode, Consumer<CivitaiModelEntry> onDetails) {
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

                // Overlay badge for model type in top-left
                JPanel badgeOverlay = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
                badgeOverlay.setOpaque(false);
                badgeOverlay.add(createPill(entry.type(), new Color(79, 70, 229, 210), Color.WHITE));
                if (entry.poi()) {
                    badgeOverlay.add(createPill("POI", new Color(217, 119, 6, 220), Color.WHITE));
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

                // Fallback gradient background
                Color startGrad = darkMode ? new Color(30, 34, 48) : new Color(225, 230, 240);
                Color endGrad = darkMode ? new Color(18, 20, 28) : new Color(200, 208, 225);
                g2.setPaint(new GradientPaint(0, 0, startGrad, 0, h, endGrad));
                g2.fillRect(0, 0, w, h);

                String url = entry.thumbnailUrl();
                if (url != null && !url.isBlank()) {
                    Image cached = IMAGE_CACHE.get(url);
                    if (cached != null) {
                        int imgW = cached.getWidth(null);
                        int imgH = cached.getHeight(null);
                        if (imgW > 0 && imgH > 0) {
                            double scale = Math.max((double) w / imgW, (double) h / imgH);
                            int nw = (int) (imgW * scale);
                            int nh = (int) (imgH * scale);
                            int x = (w - nw) / 2;
                            int y = (h - nh) / 2;
                            g2.setClip(0, 0, w, h);
                            g2.drawImage(cached, x, y, nw, nh, null);
                        }
                    } else {
                        triggerAsyncImageLoad(url, this);
                    }
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
    }

    private static void triggerAsyncImageLoad(String url, JComponent repaintTarget) {
        Thread.ofVirtual().name("CivitaiThumbLoad").start(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", "ComfyUICompanion/1.0")
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() == 200) {
                    try (InputStream in = response.body()) {
                        BufferedImage img = ImageIO.read(in);
                        if (img != null) {
                            IMAGE_CACHE.put(url, img);
                            SwingUtilities.invokeLater(repaintTarget::repaint);
                        }
                    }
                }
            } catch (Exception ignored) {
                // Ignore transient network thumbnail errors
            }
        });
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
