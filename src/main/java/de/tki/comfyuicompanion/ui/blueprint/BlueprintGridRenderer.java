package de.tki.comfyuicompanion.ui.blueprint;







import de.tki.comfyuicompanion.domain.WorkflowDescriptor;



import de.tki.comfyuicompanion.service.workflow.impl.ComfyBlueprintStrategy;



import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintEntry;



import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintStatus;



import de.tki.comfyuicompanion.ui.workflow.UnifiedWorkflowCardBuilder;







import javax.swing.Box;



import javax.swing.BoxLayout;



import javax.swing.JComponent;



import javax.swing.JLabel;



import javax.swing.JPanel;



import javax.swing.JProgressBar;



import javax.swing.JScrollPane;



import javax.swing.ScrollPaneConstants;



import javax.swing.Scrollable;



import javax.swing.SwingUtilities;



import javax.swing.border.EmptyBorder;



import java.awt.BorderLayout;



import java.awt.Color;



import java.awt.Dimension;



import java.awt.Font;



import java.awt.GridBagLayout;



import java.awt.GridLayout;



import java.awt.LayoutManager;



import java.awt.Rectangle;



import java.awt.Window;



import java.awt.event.ComponentAdapter;



import java.awt.event.ComponentEvent;



import java.util.ArrayList;



import java.util.List;



import java.util.Map;



import java.util.function.BiConsumer;



import java.util.function.Consumer;



import java.util.function.Function;







/**



 * Manages responsive multi-column layout rendering for blueprint workflow cards using



 * the consolidated {@link UnifiedWorkflowCardBuilder}.



 *



 * @author Senior Java Developer



 */



public class BlueprintGridRenderer {







    private static final int CARD_GAP = 14;



    private static final int TARGET_CARD_WIDTH = 220;







    private final ScrollablePanel galleryWrapper;



    private final JScrollPane scroll;



    private final List<JPanel> categoryGridPanels = new ArrayList<>();



    private final List<JLabel> categoryTitleLabels = new ArrayList<>();



    private final ComfyBlueprintStrategy strategy = new ComfyBlueprintStrategy();







    private BlueprintPreviewCache previewCache;



    private Color textPrimary = new Color(228, 230, 240);



    private Color textSecondary = new Color(120, 126, 152);







    /**



     * Constructs a BlueprintGridRenderer initializing the scroll pane, wrapper, and resize listeners.



     *



     * @param previewCache cache instance for previews



     */



    public BlueprintGridRenderer(BlueprintPreviewCache previewCache) {



        this.previewCache = previewCache;







        galleryWrapper = new ScrollablePanel(new BorderLayout());



        galleryWrapper.setLayout(new BoxLayout(galleryWrapper, BoxLayout.Y_AXIS));



        galleryWrapper.setOpaque(true);



        galleryWrapper.setBackground(de.tki.comfyuicompanion.ui.ThemeManager.isDarkMode() ? new Color(15, 16, 21) : new Color(245, 247, 250));



        galleryWrapper.setBorder(new EmptyBorder(10, 15, 20, 15));







        scroll = new JScrollPane(galleryWrapper);



        scroll.setBorder(null);



        scroll.setOpaque(false);



        scroll.getViewport().setOpaque(false);



        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);



        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);



        scroll.getVerticalScrollBar().setUnitIncrement(18);



        scroll.putClientProperty("JScrollPane.smoothScrolling", true);







        scroll.addComponentListener(new ComponentAdapter() {



            @Override



            public void componentResized(ComponentEvent e) {



                recomputeColumns();



            }



        });



    }







    public void setPreviewCache(BlueprintPreviewCache previewCache) {



        this.previewCache = previewCache;



    }







    public JScrollPane getScrollPane() {



        return scroll;



    }







    public JComponent getComponent() {



        return scroll;



    }







    public JPanel getGalleryWrapper() {



        return galleryWrapper;



    }







    public int getScrollValue() {



        return scroll != null ? scroll.getVerticalScrollBar().getValue() : 0;



    }







    public void restoreScroll(int scrollVal) {



        if (scroll != null && scrollVal > 0) {



            SwingUtilities.invokeLater(() -> {



                int max = scroll.getVerticalScrollBar().getMaximum() - scroll.getVerticalScrollBar().getModel().getExtent();



                scroll.getVerticalScrollBar().setValue(Math.min(scrollVal, max));



            });



        }



    }







    public void showLoading() {



        galleryWrapper.removeAll();



        categoryGridPanels.clear();







        JPanel loadingPanel = new JPanel(new GridBagLayout());



        loadingPanel.setOpaque(false);



        loadingPanel.setPreferredSize(new Dimension(800, 400));







        JProgressBar spinner = new JProgressBar();



        spinner.setIndeterminate(true);



        spinner.setPreferredSize(new Dimension(240, 6));







        JLabel loadingLabel = new JLabel("Loading Blueprint Gallery...");



        loadingLabel.setFont(new Font("SansSerif", Font.BOLD, 14));



        loadingLabel.setForeground(textSecondary);







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







    public void clear() {



        galleryWrapper.removeAll();



        categoryGridPanels.clear();



        galleryWrapper.revalidate();



        galleryWrapper.repaint();



    }







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







            int remainder = items.size() % cols;



            if (remainder > 0) {



                int emptySlots = cols - remainder;



                for (int i = 0; i < emptySlots; i++) {



                    JPanel placeholder = new JPanel();



                    placeholder.setOpaque(false);



                    gridPanel.add(placeholder);



                }



            }







            categoryGridPanels.add(gridPanel);



            galleryWrapper.add(gridPanel);



        }







        recomputeColumns();



        galleryWrapper.revalidate();



        galleryWrapper.repaint();



    }







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







    public void updateTheme(boolean darkMode) {



        Color bgPage = darkMode ? new Color(15, 16, 21) : new Color(245, 247, 250);



        textPrimary = darkMode ? new Color(228, 230, 240) : new Color(30, 35, 45);



        textSecondary = darkMode ? new Color(120, 126, 152) : new Color(90, 100, 110);







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







    public int calculateColumns() {



        int availableWidth = scroll.getWidth() - 52;



        if (availableWidth <= 0) availableWidth = 1000;



        return Math.max(1, availableWidth / (TARGET_CARD_WIDTH + CARD_GAP));



    }







    private JPanel buildCategoryHeader(String categoryName) {



        JPanel headerPanel = new JPanel(new BorderLayout());



        headerPanel.setOpaque(false);



        headerPanel.setBorder(new EmptyBorder(14, 4, 6, 4));







        JLabel titleLabel = new JLabel(categoryName);



        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 15));



        titleLabel.setForeground(textPrimary);



        headerPanel.add(titleLabel, BorderLayout.WEST);







        JPanel line = new JPanel();



        line.setOpaque(true);



        line.setBackground(de.tki.comfyuicompanion.ui.ThemeManager.isDarkMode() ? new Color(255, 255, 255, 20) : new Color(0, 0, 0, 20));



        line.setPreferredSize(new Dimension(1, 1));



        headerPanel.add(line, BorderLayout.SOUTH);







        return headerPanel;



    }







    private JPanel buildCard(BlueprintEntry entry, BlueprintStatus status,



                             Consumer<BlueprintEntry> onCardClicked,



                             BiConsumer<BlueprintEntry, Window> onPreviewVideo) {



        entry.status = status;



        WorkflowDescriptor desc = strategy.toWorkflowDescriptor(entry);



        return UnifiedWorkflowCardBuilder.buildCard(



                desc,



                d -> { if (onCardClicked != null) onCardClicked.accept(entry); },



                null,



                null,



                (d, win) -> { if (onPreviewVideo != null) onPreviewVideo.accept(entry, win); }



        );



    }







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



