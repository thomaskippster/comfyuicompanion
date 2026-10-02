package de.tki.comfyuicompanion.ui.blueprint;

import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintEntry;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintStatus;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Renders the multi-column blueprint card grid, category sections,
 * empty state, and loading progress indicators inside a smooth-scrolling container.
 */
public class BlueprintGridRenderer {

    private static final int CARD_GAP = 14;
    private static final int CARD_W   = 220;

    private static final List<String[]> CAT_PALETTES = List.of(
            new String[]{"text to image",     "#0F172A", "#1E3A8A", "#3B82F6"},
            new String[]{"text to video",     "#1A0A2A", "#4A154B", "#A21CAF"},
            new String[]{"image to video",    "#0A1A2A", "#1E40AF", "#06B6D4"},
            new String[]{"image edit",        "#1A1A0A", "#4D3800", "#F59E0B"},
            new String[]{"inpaint",           "#0A1A14", "#065F46", "#10B981"},
            new String[]{"upscal",            "#1A0A14", "#701A75", "#EC4899"},
            new String[]{"controlnet",        "#140A2A", "#3730A3", "#818CF8"},
            new String[]{"depth",             "#0A1420", "#164E63", "#06B6D4"},
            new String[]{"pose",              "#1A140A", "#78350F", "#D97706"},
            new String[]{"segmentation",      "#0D1A1A", "#134E4A", "#14B8A6"},
            new String[]{"caption",           "#1A102A", "#581C87", "#A855F7"},
            new String[]{"3d",                "#1C140A", "#7C2D12", "#F97316"},
            new String[]{"audio",             "#0A1A1C", "#155E75", "#22D3EE"}
    );
    private static final String[] DEFAULT_PALETTE = {"#0C0E16", "#2A3060", "#5060A0"};

    private final ScrollablePanel galleryWrapper;
    private final JScrollPane scroll;
    private final List<JPanel> categoryGridPanels = new CopyOnWriteArrayList<>();
    private final BlueprintPreviewCache previewCache;

    private Color cardBg          = new Color(24, 26, 34);
    private Color cardBorder      = new Color(48, 52, 68);
    private Color cardHoverBorder = new Color(0, 210, 190);
    private Color textPrimary     = new Color(228, 230, 240);
    private Color textSecondary   = new Color(120, 126, 152);
    private Color textDim         = new Color(80, 86, 110);

    /**
     * Constructs a new grid renderer backed by the specified preview cache.
     *
     * @param previewCache the blueprint preview image/video cache
     */
    public BlueprintGridRenderer(BlueprintPreviewCache previewCache) {
        this.previewCache = previewCache;
        this.galleryWrapper = new ScrollablePanel(new BorderLayout());
        this.galleryWrapper.setLayout(new BoxLayout(this.galleryWrapper, BoxLayout.Y_AXIS));
        this.galleryWrapper.setOpaque(false);
        this.galleryWrapper.setBorder(new EmptyBorder(10, 20, 24, 20));

        this.scroll = new JScrollPane(galleryWrapper);
        this.scroll.setBorder(null);
        this.scroll.setOpaque(false);
        this.scroll.getViewport().setOpaque(false);
        this.scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        this.scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        this.scroll.getVerticalScrollBar().setUnitIncrement(16);
        this.scroll.putClientProperty("JScrollPane.smoothScrolling", true);
        this.scroll.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                recomputeColumns();
            }
        });
    }

    /**
     * Returns the scroll pane wrapping the gallery grid.
     *
     * @return the scroll pane component
     */
    public JScrollPane getScrollPane() {
        return scroll;
    }

    /**
     * Returns the wrapper panel containing all rendered gallery components.
     *
     * @return the gallery wrapper panel
     */
    public JPanel getComponent() {
        return galleryWrapper;
    }

    /**
     * Gets the current vertical scroll position.
     *
     * @return vertical scroll bar position in pixels
     */
    public int getScrollValue() {
        return scroll != null ? scroll.getVerticalScrollBar().getValue() : 0;
    }

    /**
     * Asynchronously restores the vertical scroll bar position clamped to bounds.
     *
     * @param scrollVal target scroll bar value
     */
    public void restoreScroll(int scrollVal) {
        if (scroll != null) {
            SwingUtilities.invokeLater(() -> {
                int max = scroll.getVerticalScrollBar().getMaximum() - scroll.getVerticalScrollBar().getModel().getExtent();
                scroll.getVerticalScrollBar().setValue(Math.min(scrollVal, max));
            });
        }
    }

    /**
     * Displays a progress spinner indicating blueprints are being loaded.
     */
    public void showLoading() {
        galleryWrapper.removeAll();
        categoryGridPanels.clear();

        JPanel loadingPanel = new JPanel(new GridBagLayout());
        loadingPanel.setOpaque(false);
        loadingPanel.setPreferredSize(new Dimension(800, 400));

        JProgressBar spinner = new JProgressBar();
        spinner.setIndeterminate(true);
        spinner.setPreferredSize(new Dimension(240, 6));
        spinner.putClientProperty("FlatLaf.style", "arc: 999; foreground: $SlimStat.barForeground; background: $SlimStat.barBackground;");

        JLabel loadingLabel = new JLabel("Loading Blueprint Gallery...");
        loadingLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        loadingLabel.setForeground(textSecondary);
        loadingLabel.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);

        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setOpaque(false);
        inner.add(loadingLabel);
        inner.add(Box.createVerticalStrut(12));
        inner.add(spinner);

        loadingPanel.add(inner);
        galleryWrapper.add(loadingPanel);
        galleryWrapper.revalidate();
        galleryWrapper.repaint();
    }

    /**
     * Displays an empty state message indicating no blueprints match current filter criteria.
     */
    public void showEmpty() {
        galleryWrapper.removeAll();
        categoryGridPanels.clear();

        JLabel empty = new JLabel("No blueprints match your filter criteria.");
        empty.setFont(new Font("SansSerif", Font.ITALIC, 13));
        empty.setForeground(textSecondary);
        empty.setBorder(new EmptyBorder(10, 4, 10, 4));

        galleryWrapper.add(empty);
        galleryWrapper.revalidate();
        galleryWrapper.repaint();
    }

    /**
     * Clears all rendered category sections and cards.
     */
    public void clear() {
        galleryWrapper.removeAll();
        categoryGridPanels.clear();
        galleryWrapper.revalidate();
        galleryWrapper.repaint();
    }

    /**
     * Renders visible blueprint entries, handling grouping, headers, empty state, and scroll restoration.
     *
     * @param visible the filtered and sorted blueprint entries
     * @param statusSupplier function to resolve or compute entry status
     * @param onCardClicked callback invoked when a blueprint card is clicked
     * @param onPreviewVideo callback invoked when preview video playback is requested
     */
    public void renderVisibleEntries(List<BlueprintEntry> visible,
                                     Function<BlueprintEntry, BlueprintStatus> statusSupplier,
                                     Consumer<BlueprintEntry> onCardClicked,
                                     BiConsumer<BlueprintEntry, Window> onPreviewVideo) {
        int scrollVal = getScrollValue();
        if (visible == null || visible.isEmpty()) {
            showEmpty();
        } else {
            Map<String, List<BlueprintEntry>> grouped = BlueprintFilterController.groupByCategory(visible);
            renderCategories(grouped, statusSupplier, onCardClicked, onPreviewVideo);
        }
        restoreScroll(scrollVal);
    }

    /**
     * Renders blueprint entries grouped by category into multi-column grid sections.
     *
     * @param grouped map of category name to list of blueprint entries
     * @param statusSupplier function to resolve or compute entry status
     * @param onCardClicked callback invoked when a blueprint card is clicked
     * @param onPreviewVideo callback invoked when preview video playback is requested
     */
    public void renderCategories(Map<String, List<BlueprintEntry>> grouped,
                                 Function<BlueprintEntry, BlueprintStatus> statusSupplier,
                                 Consumer<BlueprintEntry> onCardClicked,
                                 BiConsumer<BlueprintEntry, Window> onPreviewVideo) {
        galleryWrapper.removeAll();
        categoryGridPanels.clear();

        int cols = calculateColumns();

        for (Map.Entry<String, List<BlueprintEntry>> groupEntry : grouped.entrySet()) {
            String categoryName = groupEntry.getKey();
            List<BlueprintEntry> items = groupEntry.getValue();

            galleryWrapper.add(buildCategoryHeader(categoryName));

            JPanel gridPanel = new JPanel();
            gridPanel.setOpaque(false);
            gridPanel.setBorder(new EmptyBorder(8, 4, 14, 4));
            gridPanel.setLayout(new GridLayout(0, cols, CARD_GAP, CARD_GAP));

            for (BlueprintEntry entry : items) {
                BlueprintStatus status = statusSupplier.apply(entry);
                gridPanel.add(buildCard(entry, status, onCardClicked, onPreviewVideo));
            }

            categoryGridPanels.add(gridPanel);
            galleryWrapper.add(gridPanel);
        }

        recomputeColumns();
        galleryWrapper.revalidate();
        galleryWrapper.repaint();
    }

    /**
     * Recomputes grid column count dynamically based on the current wrapper width.
     */
    public void recomputeColumns() {
        if (galleryWrapper == null || categoryGridPanels.isEmpty()) {
            return;
        }
        int cols = calculateColumns();
        for (JPanel gridPanel : categoryGridPanels) {
            if (gridPanel.getLayout() instanceof GridLayout gl) {
                if (gl.getColumns() != cols) {
                    gl.setColumns(cols);
                    gridPanel.revalidate();
                }
            }
        }
    }

    /**
     * Updates theme colors for cards, headers, borders, scroll pane, and text.
     *
     * @param darkMode true if dark mode is active, false otherwise
     */
    public void updateTheme(boolean darkMode) {
        Color bgPage;
        if (darkMode) {
            bgPage          = new Color(15, 16, 21);
            cardBg          = new Color(24, 26, 34);
            cardBorder      = new Color(48, 52, 68);
            cardHoverBorder = new Color(0, 210, 190);
            textPrimary     = new Color(228, 230, 240);
            textSecondary   = new Color(120, 126, 152);
            textDim         = new Color(80, 86, 110);
        } else {
            bgPage          = new Color(245, 247, 250);
            cardBg          = new Color(255, 255, 255);
            cardBorder      = new Color(210, 215, 225);
            cardHoverBorder = new Color(0, 150, 180);
            textPrimary     = new Color(30, 35, 45);
            textSecondary   = new Color(90, 100, 110);
            textDim         = new Color(140, 145, 160);
        }
        if (galleryWrapper != null) {
            galleryWrapper.setBackground(bgPage);
        }
        if (scroll != null) {
            scroll.setOpaque(false);
            scroll.getViewport().setOpaque(false);
            scroll.setBorder(null);
            SwingUtilities.updateComponentTreeUI(scroll);
        }
    }

    private int calculateColumns() {
        int availableWidth = galleryWrapper.getWidth() - 40;
        return Math.max(1, availableWidth / (CARD_W + CARD_GAP));
    }

    private JPanel buildCategoryHeader(String categoryName) {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(12, 4, 6, 4));
        headerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        String emoji = categoryIcon(categoryName);

        JLabel lblHeader = new JLabel(emoji + "  " + categoryName);
        lblHeader.setFont(new Font("SansSerif", Font.BOLD, 15));
        lblHeader.setForeground(textPrimary);
        headerPanel.add(lblHeader, BorderLayout.WEST);

        JPanel line = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(cardBorder);
                g.drawLine(0, 0, getWidth(), 0);
            }
        };
        line.setPreferredSize(new Dimension(0, 1));
        headerPanel.add(line, BorderLayout.SOUTH);

        return headerPanel;
    }

    private JPanel buildCard(BlueprintEntry entry, BlueprintStatus status,
                             Consumer<BlueprintEntry> onCardClicked,
                             BiConsumer<BlueprintEntry, Window> onPreviewVideo) {
        Color[] palette = resolvePalette(entry);
        return BlueprintCardBuilder.buildCard(
                entry,
                status,
                palette,
                previewCache,
                cardBg,
                cardBorder,
                cardHoverBorder,
                textPrimary,
                textDim,
                categoryIcon(entry.category),
                onCardClicked::accept,
                onPreviewVideo::accept
        );
    }

    private Color[] resolvePalette(BlueprintEntry entry) {
        String combined = (entry.category + " " + entry.name).toLowerCase(Locale.ROOT);
        for (String[] row : CAT_PALETTES) {
            if (combined.contains(row[0])) {
                return new Color[]{ hex(row[1]), hex(row[2]), hex(row[3]) };
            }
        }
        return new Color[]{ hex(DEFAULT_PALETTE[0]), hex(DEFAULT_PALETTE[1]), hex(DEFAULT_PALETTE[2]) };
    }

    private static Color hex(String h) {
        return Color.decode(h);
    }

    private static String categoryIcon(String category) {
        String c = category != null ? category.toLowerCase(Locale.ROOT) : "";
        if (c.contains("text to image"))     return "🖼";
        if (c.contains("text to video"))     return "🎬";
        if (c.contains("image to video"))    return "📽";
        if (c.contains("image edit"))        return "✏️";
        if (c.contains("inpaint"))           return "🎨";
        if (c.contains("outpaint"))          return "📐";
        if (c.contains("depth"))             return "🗺";
        if (c.contains("controlnet"))        return "🕹";
        if (c.contains("pose"))              return "🕴";
        if (c.contains("upscal"))            return "🔍";
        if (c.contains("segmentation"))      return "✂";
        if (c.contains("caption"))           return "💬";
        if (c.contains("background"))        return "🌄";
        if (c.contains("interpolat"))        return "⏩";
        if (c.contains("3d") || c.contains("model")) return "🎲";
        if (c.contains("audio"))             return "🎵";
        if (c.contains("video"))             return "🎞";
        if (c.contains("color"))             return "🌈";
        if (c.contains("blur"))              return "💧";
        if (c.contains("grain"))             return "🌾";
        if (c.contains("glow"))              return "✨";
        if (c.contains("sharpen"))           return "🔪";
        if (c.contains("brightness"))        return "☀";
        return "⚙";
    }

    /**
     * Panel supporting smart vertical-only scrolling within viewport bounds.
     */
    private static class ScrollablePanel extends JPanel implements Scrollable {
        public ScrollablePanel(LayoutManager layout) {
            super(layout);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 20;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 60;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
