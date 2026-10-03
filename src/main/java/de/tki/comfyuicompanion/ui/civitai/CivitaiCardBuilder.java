package de.tki.comfyuicompanion.ui.civitai;

import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

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
 * Builds individual visual cards for displaying Civitai models in the Model Hub grid.
 * Features asynchronous thumbnail rendering, metadata badges, rating displays, and download triggers.
 */
public class CivitaiCardBuilder {

    private static final int CARD_WIDTH = 240;
    private static final int CARD_HEIGHT = 330;
    private static final int THUMBNAIL_HEIGHT = 150;

    private static final Color COLOR_INSTALLED = new Color(34, 197, 130);
    private static final Color COLOR_PRIMARY = new Color(59, 130, 246);
    private static final Color COLOR_NSFW = new Color(239, 68, 68);

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
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);

                g2.setColor(hovered ? hoverBorder : cardBorder);
                g2.setStroke(new BasicStroke(hovered ? 2f : 1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
            }
        };

        card.setOpaque(false);
        card.setPreferredSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
        card.setMinimumSize(new Dimension(200, CARD_HEIGHT));
        card.setMaximumSize(new Dimension(360, CARD_HEIGHT));

        // Thumbnail component (North)
        JPanel thumbnailPanel = buildThumbnailPanel(entry, darkMode, onDetails);
        card.add(thumbnailPanel, BorderLayout.NORTH);

        // Content Area (Center)
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);
        contentPanel.setBorder(new EmptyBorder(8, 10, 8, 10));

        // Model Title
        JLabel titleLabel = new JLabel(truncate(entry.name(), 40));
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        titleLabel.setForeground(textPrimary);
        titleLabel.setToolTipText(entry.name());
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(titleLabel);

        contentPanel.add(Box.createVerticalStrut(4));

        // Badges: Type and Base Model
        JPanel badgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        badgeRow.setOpaque(false);
        badgeRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        badgeRow.add(createPill(entry.type(), new Color(79, 70, 229, 210), Color.WHITE));
        if (entry.baseModel() != null && !entry.baseModel().isBlank()) {
            badgeRow.add(createPill(entry.baseModel(), new Color(14, 116, 144, 210), Color.WHITE));
        }
        contentPanel.add(badgeRow);

        contentPanel.add(Box.createVerticalStrut(6));

        // Stats: Rating, Downloads, and Creator
        JPanel statsRow = new JPanel(new BorderLayout());
        statsRow.setOpaque(false);
        statsRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        String statsText = String.format("⭐ %.1f  •  ⬇ %s", entry.rating(), formatCount(entry.downloadCount()));
        JLabel statsLabel = new JLabel(statsText);
        statsLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        statsLabel.setForeground(textDim);
        statsRow.add(statsLabel, BorderLayout.WEST);

        JLabel sizeLabel = new JLabel(entry.formattedSize());
        sizeLabel.setFont(new Font("SansSerif", Font.BOLD, 10));
        sizeLabel.setForeground(textDim);
        statsRow.add(sizeLabel, BorderLayout.EAST);

        contentPanel.add(statsRow);
        card.add(contentPanel, BorderLayout.CENTER);

        // Actions Row (South)
        JPanel actionsRow = new JPanel(new BorderLayout(6, 0));
        actionsRow.setOpaque(false);
        actionsRow.setBorder(new EmptyBorder(0, 10, 10, 10));

        JButton detailsBtn = new JButton("Details");
        detailsBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        detailsBtn.addActionListener(e -> {
            if (onDetails != null) onDetails.accept(entry);
        });
        actionsRow.add(detailsBtn, BorderLayout.WEST);

        JButton actionBtn;
        if (isDownloading) {
            actionBtn = new JButton(downloadProgress != null ? downloadProgress : "Downloading...");
            actionBtn.setEnabled(false);
            actionBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        } else if (isInstalled) {
            actionBtn = new JButton("Installed", SvgIconFactory.get(AppIcon.CHECK, 14));
            actionBtn.setForeground(COLOR_INSTALLED);
            actionBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
            actionBtn.setToolTipText("Already present in local models directory. Click to re-download.");
            actionBtn.addActionListener(e -> {
                if (onDownload != null) onDownload.accept(entry);
            });
        } else {
            actionBtn = new JButton("Download", SvgIconFactory.get(AppIcon.DOWNLOAD, 14));
            actionBtn.setBackground(COLOR_PRIMARY);
            actionBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
            actionBtn.addActionListener(e -> {
                if (onDownload != null) onDownload.accept(entry);
            });
        }
        actionsRow.add(actionBtn, BorderLayout.CENTER);

        card.add(actionsRow, BorderLayout.SOUTH);

        return card;
    }

    private static JPanel buildThumbnailPanel(CivitaiModelEntry entry, boolean darkMode, Consumer<CivitaiModelEntry> onDetails) {
        return new JPanel() {
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
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                int w = getWidth();
                int h = getHeight();

                // Draw fallback gradient background
                Color startGrad = darkMode ? new Color(30, 34, 48) : new Color(225, 230, 240);
                Color endGrad = darkMode ? new Color(18, 20, 28) : new Color(200, 208, 225);
                g2.setPaint(new GradientPaint(0, 0, startGrad, 0, h, endGrad));
                g2.fillRoundRect(0, 0, w, h, 8, 8);

                // Fetch or trigger image load
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
                        drawTypePlaceholder(g2, w, h, entry.type(), darkMode);
                    }
                } else {
                    drawTypePlaceholder(g2, w, h, entry.type(), darkMode);
                }

                // NSFW badge overlay
                if (entry.nsfw()) {
                    g2.setColor(COLOR_NSFW);
                    g2.fillRoundRect(w - 46, 6, 40, 16, 4, 4);
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("SansSerif", Font.BOLD, 9));
                    g2.drawString("NSFW", w - 40, 18);
                }

                g2.dispose();
            }
        };
    }

    private static void drawTypePlaceholder(Graphics2D g2, int w, int h, String type, boolean darkMode) {
        g2.setColor(darkMode ? new Color(120, 130, 160) : new Color(80, 90, 120));
        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        FontMetrics fm = g2.getFontMetrics();
        String display = type != null ? type.toUpperCase() : "MODEL";
        int textW = fm.stringWidth(display);
        g2.drawString(display, (w - textW) / 2, h / 2 + 5);
    }

    private static void triggerAsyncImageLoad(String url, Component repaintTarget) {
        if (IMAGE_CACHE.containsKey(url)) return;

        Thread.ofVirtual().name("Civitai-ImageLoad").start(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(12))
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
                // Keep placeholder on image fetch error
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

    private static String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength - 1) + "…" : text;
    }
}
