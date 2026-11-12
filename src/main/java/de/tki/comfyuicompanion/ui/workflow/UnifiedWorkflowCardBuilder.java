package de.tki.comfyuicompanion.ui.workflow;



import com.formdev.flatlaf.FlatClientProperties;

import de.tki.comfyuicompanion.domain.WorkflowDescriptor;

import de.tki.comfyuicompanion.domain.WorkflowSourceType;

import de.tki.comfyuicompanion.ui.ThemeManager;

import de.tki.comfyuicompanion.ui.icons.AppIcon;

import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import de.tki.comfyuicompanion.util.WorkflowImageLoader;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;



import javax.swing.AbstractButton;

import javax.swing.BorderFactory;

import javax.swing.Box;

import javax.swing.BoxLayout;

import javax.swing.ImageIcon;

import javax.swing.JButton;

import javax.swing.JLabel;

import javax.swing.JPanel;

import javax.swing.SwingConstants;

import javax.swing.SwingUtilities;

import java.awt.BorderLayout;

import java.awt.Color;

import java.awt.Component;

import java.awt.Container;

import java.awt.Cursor;

import java.awt.Dimension;

import java.awt.FlowLayout;

import java.awt.Font;

import java.awt.Graphics;

import java.awt.Graphics2D;

import java.awt.GridLayout;

import java.awt.Image;

import java.awt.Point;

import java.awt.RenderingHints;

import java.awt.Window;

import java.awt.event.MouseAdapter;

import java.awt.event.MouseEvent;

import java.awt.image.BufferedImage;

import java.time.Duration;

import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;

import java.util.function.BiConsumer;

import java.util.function.Consumer;



/**

 * Enterprise unified UI Card Builder generating consistent, flicker-free interactive cards

 * for workflows across all provider ecosystems (ComfyUI Blueprints, Civitai, HuggingFace, My Vault).

 *

 * @author Senior Java Developer

 */

public final class UnifiedWorkflowCardBuilder {



    private static final Logger log = LoggerFactory.getLogger(UnifiedWorkflowCardBuilder.class);



    private static final int CARD_WIDTH = 220;

    private static final int CARD_HEIGHT = 280;

    private static final int THUMBNAIL_HEIGHT = 140;



    private static final Color GALLERY_SURFACE_BG = new Color(15, 16, 21);

    private static final Color CARD_BG = new Color(30, 41, 59);

    private static final Color CARD_HOVER_BG = new Color(40, 52, 74);

    private static final Color CARD_BORDER = new Color(51, 65, 85);

    private static final Color CARD_HOVER_BORDER = new Color(59, 130, 246);



    private static final Map<String, Image> THUMBNAIL_CACHE = new ConcurrentHashMap<>();
    private static final java.util.concurrent.Semaphore THUMBNAIL_SEMAPHORE = new java.util.concurrent.Semaphore(6);



    private UnifiedWorkflowCardBuilder() {

        // Factory utility class

    }



    public static JPanel buildCard(

            WorkflowDescriptor descriptor,

            Consumer<WorkflowDescriptor> onOpenDetails,

            Consumer<WorkflowDescriptor> onDownloadMissing

    ) {

        return buildCard(descriptor, onOpenDetails, onDownloadMissing, null, null);

    }



    public static JPanel buildCard(

            WorkflowDescriptor descriptor,

            Consumer<WorkflowDescriptor> onOpenDetails,

            Consumer<WorkflowDescriptor> onDownloadMissing,

            Consumer<WorkflowDescriptor> onRemove

    ) {

        return buildCard(descriptor, onOpenDetails, onDownloadMissing, onRemove, null);

    }



    public static JPanel buildCard(

            WorkflowDescriptor descriptor,

            Consumer<WorkflowDescriptor> onOpenDetails,

            Consumer<WorkflowDescriptor> onDownloadMissing,

            Consumer<WorkflowDescriptor> onRemove,

            BiConsumer<WorkflowDescriptor, Window> onPreviewVideo

    ) {

        CardPanel card = new CardPanel();

        card.setPreferredSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));

        card.setMinimumSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));

        card.setMaximumSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));

        card.setBorder(BorderFactory.createEmptyBorder(7, 7, 7, 7));

        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));



        JPanel thumbContainer = buildThumbnailContainer(descriptor);

        card.add(thumbContainer, BorderLayout.NORTH);



        JPanel infoContainer = buildInfoContainer(descriptor);

        card.add(infoContainer, BorderLayout.CENTER);



        JPanel actionContainer = buildActionContainer(descriptor, onOpenDetails, onDownloadMissing, onRemove, onPreviewVideo);

        card.add(actionContainer, BorderLayout.SOUTH);



        MouseAdapter unifiedMouseHandler = new MouseAdapter() {

            @Override

            public void mouseEntered(MouseEvent e) {

                card.setHovered(true);

            }



            @Override

            public void mouseExited(MouseEvent e) {

                Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), card);

                if (p.x < 0 || p.y < 0 || p.x >= card.getWidth() || p.y >= card.getHeight()) {

                    card.setHovered(false);

                }

            }



            @Override

            public void mouseClicked(MouseEvent e) {

                if (e.getComponent() instanceof AbstractButton) {

                    return;

                }

                if (e.getClickCount() == 2 && onPreviewVideo != null && isVideoWorkflow(descriptor)) {

                    onPreviewVideo.accept(descriptor, SwingUtilities.getWindowAncestor(card));

                } else if (onOpenDetails != null) {

                    onOpenDetails.accept(descriptor);

                }

            }

        };



        installHoverAndClickListeners(card, unifiedMouseHandler);

        return card;

    }



    private static void installHoverAndClickListeners(Component comp, MouseAdapter adapter) {

        comp.addMouseListener(adapter);

        if (comp instanceof Container container) {

            for (Component child : container.getComponents()) {

                installHoverAndClickListeners(child, adapter);

            }

        }

    }



    private static JPanel buildThumbnailContainer(WorkflowDescriptor descriptor) {
        JPanel container = new JPanel(new BorderLayout()) {
            @Override
            public void updateUI() {
                super.updateUI();
                setBackground(ThemeManager.isDarkMode() ? new Color(15, 23, 42) : new Color(241, 245, 249));
            }
        };
        container.setPreferredSize(new Dimension(CARD_WIDTH - 14, THUMBNAIL_HEIGHT));
        container.setBackground(ThemeManager.isDarkMode() ? new Color(15, 23, 42) : new Color(241, 245, 249));
        container.setOpaque(true);



        JLabel imgLabel = new JLabel("", SwingConstants.CENTER);

        imgLabel.setHorizontalAlignment(SwingConstants.CENTER);

        container.add(imgLabel, BorderLayout.CENTER);



        JPanel topBar = new JPanel(new BorderLayout());

        topBar.setOpaque(false);

        topBar.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));



        WorkflowSourceType src = descriptor.sourceType();

        JLabel srcBadge = createPillLabel(" " + src.getDisplayName() + " ", src.getBadgeColor(), Color.WHITE);

        topBar.add(srcBadge, BorderLayout.WEST);



        if (isVideoWorkflow(descriptor)) {
            JLabel vidBadge = new JLabel(" Video ", SvgIconFactory.get(AppIcon.SUGGEST, 10), SwingConstants.RIGHT) {
                @Override
                public void updateUI() {
                    super.updateUI();
                    setForeground(ThemeManager.isDarkMode() ? new Color(147, 197, 253) : new Color(29, 78, 216));
                }
            };
            vidBadge.setFont(new Font("SansSerif", Font.BOLD, 10));
            vidBadge.setForeground(ThemeManager.isDarkMode() ? new Color(147, 197, 253) : new Color(29, 78, 216));
            topBar.add(vidBadge, BorderLayout.EAST);
        }



        container.add(topBar, BorderLayout.NORTH);

        loadThumbnailAsync(descriptor.previewPathOrUrl(), imgLabel);

        return container;

    }



    private static void loadThumbnailAsync(String pathOrUrl, JLabel targetLabel) {

        if (pathOrUrl == null || pathOrUrl.isBlank()) {

            targetLabel.setIcon(SvgIconFactory.get(AppIcon.BLUEPRINT_GALLERY, 36));

            return;

        }



        Image cached = THUMBNAIL_CACHE.get(pathOrUrl);

        if (cached != null) {

            targetLabel.setIcon(new ImageIcon(cached));

            return;

        }



        targetLabel.setIcon(SvgIconFactory.get(AppIcon.BLUEPRINT_GALLERY, 32));



        Thread.ofVirtual().name("unified-card-thumb").start(() -> {
            boolean acquired = false;
            try {
                THUMBNAIL_SEMAPHORE.acquire();
                acquired = true;
                Image alreadyCached = THUMBNAIL_CACHE.get(pathOrUrl);
                if (alreadyCached != null) {
                    SwingUtilities.invokeLater(() -> targetLabel.setIcon(new ImageIcon(alreadyCached)));
                    return;
                }
                BufferedImage bImg = WorkflowImageLoader.loadOrFetchCached(pathOrUrl, Duration.ofSeconds(10));
                if (bImg != null) {
                    Image scaled = scaleToFit(bImg, CARD_WIDTH - 14, THUMBNAIL_HEIGHT);
                    bImg.flush();
                    THUMBNAIL_CACHE.put(pathOrUrl, scaled);
                    SwingUtilities.invokeLater(() -> targetLabel.setIcon(new ImageIcon(scaled)));
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                log.debug("Failed loading thumbnail for {}: {}", pathOrUrl, ex.getMessage());
            } finally {
                if (acquired) {
                    THUMBNAIL_SEMAPHORE.release();
                }
            }
        });

    }



    private static JPanel buildInfoContainer(WorkflowDescriptor descriptor) {

        JPanel info = new JPanel();

        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));

        info.setOpaque(false);

        info.setBorder(BorderFactory.createEmptyBorder(6, 4, 4, 4));



        JLabel lblTitle = new JLabel(descriptor.title()) {

            @Override

            public void updateUI() {

                super.updateUI();

                setForeground(ThemeManager.isDarkMode() ? new Color(241, 245, 249) : new Color(30, 41, 59));

            }

        };

        lblTitle.setFont(new Font("SansSerif", Font.BOLD, 12));

        lblTitle.setForeground(ThemeManager.isDarkMode() ? new Color(241, 245, 249) : new Color(30, 41, 59));

        info.add(lblTitle);



        info.add(Box.createVerticalStrut(2));



        String sub = !descriptor.architecture().equals("Unknown")

                ? descriptor.architecture()

                : descriptor.category();

        JLabel lblSub = new JLabel(sub) {

            @Override

            public void updateUI() {

                super.updateUI();

                setForeground(ThemeManager.isDarkMode() ? new Color(148, 163, 184) : new Color(100, 116, 139));

            }

        };

        lblSub.setFont(new Font("SansSerif", Font.PLAIN, 11));

        lblSub.setForeground(ThemeManager.isDarkMode() ? new Color(148, 163, 184) : new Color(100, 116, 139));

        info.add(lblSub);



        info.add(Box.createVerticalStrut(4));



        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));

        statusRow.setOpaque(false);



        String dlStatus = descriptor.extendedAttributes().get("downloadStatus");

        if (dlStatus != null && !dlStatus.isBlank()) {

            statusRow.add(createPillLabel(" " + dlStatus + " ", new Color(30, 58, 138), new Color(147, 197, 253)));

        } else if (descriptor.isReadyToExecute()) {

            statusRow.add(createPillLabel(" Ready ", new Color(20, 83, 45), new Color(74, 222, 128)));

        } else {

            statusRow.add(createPillLabel(" Missing (" + descriptor.missingModelsCount() + ") ", new Color(120, 53, 15), new Color(251, 191, 36)));

        }



        String statsSnippet = resolveStatsSnippet(descriptor);

        if (!statsSnippet.isEmpty()) {

            JLabel lblStats = new JLabel(statsSnippet);

            lblStats.setFont(new Font("SansSerif", Font.PLAIN, 10));

            lblStats.setForeground(new Color(100, 116, 139));

            statusRow.add(lblStats);

        }



        info.add(statusRow);

        return info;

    }



    private static JPanel buildActionContainer(

            WorkflowDescriptor descriptor,

            Consumer<WorkflowDescriptor> onOpenDetails,

            Consumer<WorkflowDescriptor> onDownloadMissing,

            Consumer<WorkflowDescriptor> onRemove,

            BiConsumer<WorkflowDescriptor, Window> onPreviewVideo

    ) {

        JPanel actions = new JPanel(new BorderLayout(4, 0));

        actions.setOpaque(false);

        actions.setBorder(BorderFactory.createEmptyBorder(4, 2, 2, 2));



        JButton btnDetails = new JButton("Details", SvgIconFactory.get(AppIcon.FILE_TEXT, 12)) {

            @Override

            public void updateUI() {

                super.updateUI();

                boolean dark = ThemeManager.isDarkMode();

                putClientProperty(FlatClientProperties.STYLE, dark

                        ? "arc: 6; background: #1e293b; foreground: #e2e8f0; hoverBackground: #334155; hoverForeground: #ffffff; borderColor: #475569"

                        : "arc: 6; background: #f1f5f9; foreground: #1e293b; hoverBackground: #e2e8f0; hoverForeground: #0f172a; borderColor: #cbd5e1");

            }

        };

        btnDetails.setFont(new Font("SansSerif", Font.PLAIN, 10));

        btnDetails.setFocusable(false);

        btnDetails.setFocusPainted(false);

        btnDetails.putClientProperty(FlatClientProperties.STYLE, ThemeManager.isDarkMode()

                ? "arc: 6; background: #1e293b; foreground: #e2e8f0; hoverBackground: #334155; hoverForeground: #ffffff; borderColor: #475569"

                : "arc: 6; background: #f1f5f9; foreground: #1e293b; hoverBackground: #e2e8f0; hoverForeground: #0f172a; borderColor: #cbd5e1");

        btnDetails.addActionListener(e -> {

            if (onOpenDetails != null) onOpenDetails.accept(descriptor);

        });



        if (descriptor.sourceType() == WorkflowSourceType.VAULT && !descriptor.isReadyToExecute() && onDownloadMissing != null) {

            JButton btnDownload = new JButton("Get (" + descriptor.missingModelsCount() + ")", SvgIconFactory.get(AppIcon.DOWNLOAD, 11));

            btnDownload.setFont(new Font("SansSerif", Font.BOLD, 10));

            btnDownload.setFocusable(false);

            btnDownload.setFocusPainted(false);

            btnDownload.setToolTipText("Download " + descriptor.missingModelsCount() + " missing model(s)");

            btnDownload.putClientProperty(FlatClientProperties.STYLE,

                    "arc: 6; background: #d97706; foreground: #ffffff; hoverBackground: #f59e0b; hoverForeground: #ffffff; borderColor: #b45309");

            btnDownload.addActionListener(e -> onDownloadMissing.accept(descriptor));



            JPanel centerButtons = new JPanel(new GridLayout(1, 2, 4, 0));

            centerButtons.setOpaque(false);

            centerButtons.add(btnDetails);

            centerButtons.add(btnDownload);

            actions.add(centerButtons, BorderLayout.CENTER);

        } else {

            actions.add(btnDetails, BorderLayout.CENTER);

        }



        if (isVideoWorkflow(descriptor) && onPreviewVideo != null) {
            JButton btnPlay = new JButton(SvgIconFactory.get(AppIcon.SUGGEST, 12)) {
                @Override
                public void updateUI() {
                    super.updateUI();
                    boolean dark = ThemeManager.isDarkMode();
                    putClientProperty(FlatClientProperties.STYLE, dark
                            ? "arc: 6; background: #1e293b; hoverBackground: #334155; borderColor: #475569"
                            : "arc: 6; background: #f1f5f9; hoverBackground: #e2e8f0; borderColor: #cbd5e1");
                }
            };
            btnPlay.setFocusable(false);
            btnPlay.setFocusPainted(false);
            btnPlay.putClientProperty(FlatClientProperties.STYLE, ThemeManager.isDarkMode()
                    ? "arc: 6; background: #1e293b; hoverBackground: #334155; borderColor: #475569"
                    : "arc: 6; background: #f1f5f9; hoverBackground: #e2e8f0; borderColor: #cbd5e1");
            btnPlay.setToolTipText("Play Video Preview");
            btnPlay.addActionListener(e -> onPreviewVideo.accept(descriptor, SwingUtilities.getWindowAncestor(btnPlay)));
            actions.add(btnPlay, BorderLayout.WEST);
        }

        if (descriptor.sourceType() == WorkflowSourceType.VAULT && onRemove != null) {
            JButton btnRemove = new JButton(SvgIconFactory.get(AppIcon.TRASH, 12)) {
                @Override
                public void updateUI() {
                    super.updateUI();
                    boolean dark = ThemeManager.isDarkMode();
                    putClientProperty(FlatClientProperties.STYLE, dark
                            ? "arc: 6; background: #1e293b; hoverBackground: #b91c1c; hoverForeground: #ffffff; borderColor: #475569"
                            : "arc: 6; background: #f1f5f9; hoverBackground: #fee2e2; hoverForeground: #b91c1c; borderColor: #cbd5e1");
                }
            };
            btnRemove.setFocusable(false);
            btnRemove.setFocusPainted(false);
            btnRemove.setPreferredSize(new Dimension(28, 24));
            btnRemove.putClientProperty(FlatClientProperties.STYLE, ThemeManager.isDarkMode()
                    ? "arc: 6; background: #1e293b; hoverBackground: #b91c1c; hoverForeground: #ffffff; borderColor: #475569"
                    : "arc: 6; background: #f1f5f9; hoverBackground: #fee2e2; hoverForeground: #b91c1c; borderColor: #cbd5e1");
            btnRemove.setToolTipText("Remove from My Vault");
            btnRemove.addActionListener(e -> onRemove.accept(descriptor));
            actions.add(btnRemove, BorderLayout.EAST);
        }



        return actions;

    }



    private static JLabel createPillLabel(String text, Color bg, Color fg) {

        JLabel lbl = new JLabel(text) {

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

        lbl.setFont(new Font("SansSerif", Font.BOLD, 10));

        lbl.setForeground(fg);

        lbl.setOpaque(false);

        return lbl;

    }



    private static boolean isVideoWorkflow(WorkflowDescriptor descriptor) {

        String path = descriptor.previewPathOrUrl() != null ? descriptor.previewPathOrUrl().toLowerCase() : "";

        String cat = descriptor.category() != null ? descriptor.category().toLowerCase() : "";

        return path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".mov") || cat.contains("video");

    }



    private static String resolveStatsSnippet(WorkflowDescriptor descriptor) {

        Map<String, String> attrs = descriptor.extendedAttributes();

        if (attrs.containsKey("rating") && attrs.containsKey("downloads")) {

            return attrs.get("rating") + " (" + attrs.get("downloads") + ")";

        } else if (attrs.containsKey("likes")) {

            return attrs.get("likes") + " likes";

        }

        return "";

    }



    private static Image scaleToFit(BufferedImage src, int targetWidth, int targetHeight) {

        double srcRatio = (double) src.getWidth() / Math.max(1, src.getHeight());

        double targetRatio = (double) targetWidth / Math.max(1, targetHeight);



        int finalWidth, finalHeight;

        if (srcRatio > targetRatio) {

            finalWidth = targetWidth;

            finalHeight = (int) (targetWidth / srcRatio);

        } else {

            finalHeight = targetHeight;

            finalWidth = (int) (targetHeight * srcRatio);

        }



        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2 = resized.createGraphics();

        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);



        int x = (targetWidth - finalWidth) / 2;

        int y = (targetHeight - finalHeight) / 2;

        g2.drawImage(src, x, y, finalWidth, finalHeight, null);

        g2.dispose();



        return resized;

    }



    /**

     * Self-contained opaque card panel painting its own anti-aliased rounded background and border

     * without triggering layout revalidation or parent repaint cascades on hover transitions.

     */

    private static final class CardPanel extends JPanel {

        private boolean hovered = false;



        CardPanel() {

            super(new BorderLayout(0, 0));

            setOpaque(true);

        }



        void setHovered(boolean value) {

            if (this.hovered != value) {

                this.hovered = value;

                repaint();

            }

        }



        @Override

        public void updateUI() {

            super.updateUI();

            repaint();

        }



        @Override

        protected void paintComponent(Graphics g) {

            Graphics2D g2 = (Graphics2D) g.create();

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);



            int w = getWidth();

            int h = getHeight();



            boolean dark = ThemeManager.isDarkMode();

            Color surfaceBg = dark ? GALLERY_SURFACE_BG : new Color(245, 247, 250);

            Color cardBg = dark ? (hovered ? CARD_HOVER_BG : CARD_BG) : (hovered ? new Color(238, 242, 248) : Color.WHITE);

            Color cardBorder = dark ? (hovered ? CARD_HOVER_BORDER : CARD_BORDER) : (hovered ? new Color(0, 120, 215) : new Color(218, 222, 230));



            g2.setColor(surfaceBg);

            g2.fillRect(0, 0, w, h);



            g2.setColor(cardBg);

            g2.fillRoundRect(0, 0, w - 1, h - 1, 12, 12);



            g2.setColor(cardBorder);

            g2.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);



            g2.dispose();

        }

    }

}

