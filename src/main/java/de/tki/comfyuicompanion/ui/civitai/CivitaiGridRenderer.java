package de.tki.comfyuicompanion.ui.civitai;

import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Manages responsive multi-column rendering of Civitai model cards,
 * handling smooth scrolling, loading progress, empty states, and theme updates.
 */
public class CivitaiGridRenderer {

    private static final int CARD_GAP = 14;
    private static final int TARGET_CARD_WIDTH = 240;

    private final ScrollablePanel contentWrapper;
    private final JScrollPane scrollPane;
    private JPanel gridPanel;

    private List<CivitaiModelEntry> currentEntries = Collections.emptyList();
    private Predicate<CivitaiModelEntry> installedChecker = e -> false;
    private final Map<Long, String> downloadingStatuses = new ConcurrentHashMap<>();
    private final Set<Long> downloadingIds = ConcurrentHashMap.newKeySet();

    private Consumer<CivitaiModelEntry> downloadAction;
    private Consumer<CivitaiModelEntry> detailsAction;
    private boolean darkMode = true;

    /**
     * Constructs a new CivitaiGridRenderer and initializes its scrollable hierarchy.
     */
    public CivitaiGridRenderer() {
        contentWrapper = new ScrollablePanel(new BorderLayout());
        contentWrapper.setOpaque(false);
        contentWrapper.setBorder(new EmptyBorder(10, 15, 20, 15));

        scrollPane = new JScrollPane(contentWrapper);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        scrollPane.putClientProperty("JScrollPane.smoothScrolling", true);

        scrollPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                recomputeLayout();
            }
        });
    }

    /**
     * Retrieves the root JScrollPane containing the gallery grid.
     *
     * @return the scroll pane component
     */
    public JScrollPane getScrollPane() {
        return scrollPane;
    }

    /**
     * Displays a centered loading indicator while models are being fetched.
     */
    public void showLoading() {
        contentWrapper.removeAll();
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);

        JLabel loadingLbl = new JLabel("Connecting to Civitai & fetching models...", SvgIconFactory.get(AppIcon.REFRESH, 20), SwingConstants.CENTER);
        loadingLbl.setFont(new Font("SansSerif", Font.PLAIN, 14));
        loadingLbl.setForeground(darkMode ? Color.LIGHT_GRAY : Color.DARK_GRAY);
        center.add(loadingLbl);

        contentWrapper.add(center, BorderLayout.CENTER);
        contentWrapper.revalidate();
        contentWrapper.repaint();
    }

    /**
     * Displays an empty state message when no models match the query.
     *
     * @param query the search query that yielded no results
     */
    public void showEmpty(String query) {
        contentWrapper.removeAll();
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);

        String message = (query == null || query.isBlank())
                ? "No models found on Civitai."
                : "No models found matching: \"" + query + "\"";
        JLabel emptyLbl = new JLabel(message, SvgIconFactory.get(AppIcon.SEARCH, 20), SwingConstants.CENTER);
        emptyLbl.setFont(new Font("SansSerif", Font.PLAIN, 14));
        emptyLbl.setForeground(Color.GRAY);
        center.add(emptyLbl);

        contentWrapper.add(center, BorderLayout.CENTER);
        contentWrapper.revalidate();
        contentWrapper.repaint();
    }

    /**
     * Displays an error state if the API request fails.
     *
     * @param errorMessage the error message to display
     */
    public void showError(String errorMessage) {
        contentWrapper.removeAll();
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);

        JLabel errLbl = new JLabel("Failed to load models: " + errorMessage, SvgIconFactory.get(AppIcon.CLOSE, 20), SwingConstants.CENTER);
        errLbl.setFont(new Font("SansSerif", Font.PLAIN, 14));
        errLbl.setForeground(new Color(239, 68, 68));
        center.add(errLbl);

        contentWrapper.add(center, BorderLayout.CENTER);
        contentWrapper.revalidate();
        contentWrapper.repaint();
    }

    /**
     * Renders a list of Civitai model entries into the grid.
     *
     * @param entries          the model entries to render
     * @param installedChecker predicate indicating whether a model is locally present
     * @param onDownload       download callback
     * @param onDetails        details dialog callback
     */
    public void renderEntries(List<CivitaiModelEntry> entries,
                              Predicate<CivitaiModelEntry> installedChecker,
                              Consumer<CivitaiModelEntry> onDownload,
                              Consumer<CivitaiModelEntry> onDetails) {
        this.currentEntries = entries != null ? entries : Collections.emptyList();
        this.installedChecker = installedChecker != null ? installedChecker : e -> false;
        this.downloadAction = onDownload;
        this.detailsAction = onDetails;

        if (currentEntries.isEmpty()) {
            showEmpty("");
            return;
        }

        recomputeLayout();
    }

    /**
     * Updates download progress for a specific model ID.
     *
     * @param modelId       the Civitai model ID
     * @param statusMessage the progress message (e.g. "45%" or "Finished")
     */
    public void updateDownloadStatus(long modelId, String statusMessage) {
        if (statusMessage.contains("Finished") || statusMessage.contains("Already exists")) {
            downloadingIds.remove(modelId);
            downloadingStatuses.remove(modelId);
        } else {
            downloadingIds.add(modelId);
            downloadingStatuses.put(modelId, statusMessage);
        }
        recomputeLayout();
    }

    private void recomputeLayout() {
        if (currentEntries.isEmpty()) return;

        contentWrapper.removeAll();

        int availableWidth = scrollPane.getViewport().getWidth() - 35;
        if (availableWidth <= 0) availableWidth = 1000;

        int cols = Math.max(1, availableWidth / (TARGET_CARD_WIDTH + CARD_GAP));

        gridPanel = new JPanel(new GridLayout(0, cols, CARD_GAP, CARD_GAP));
        gridPanel.setOpaque(false);

        for (CivitaiModelEntry entry : currentEntries) {
            boolean isInstalled = installedChecker.test(entry);
            boolean isDownloading = downloadingIds.contains(entry.id());
            String progress = downloadingStatuses.get(entry.id());

            JPanel card = CivitaiCardBuilder.buildCard(
                    entry,
                    isInstalled,
                    isDownloading,
                    progress,
                    darkMode,
                    downloadAction,
                    detailsAction
            );
            gridPanel.add(card);
        }

        // Fill remaining slots in the last row to maintain grid sizing
        int remainder = currentEntries.size() % cols;
        if (remainder > 0) {
            int emptySlots = cols - remainder;
            for (int i = 0; i < emptySlots; i++) {
                JPanel placeholder = new JPanel();
                placeholder.setOpaque(false);
                gridPanel.add(placeholder);
            }
        }

        contentWrapper.add(gridPanel, BorderLayout.NORTH);
        contentWrapper.revalidate();
        contentWrapper.repaint();
    }

    /**
     * Updates the dark mode theme across the renderer and re-renders visible cards.
     *
     * @param darkMode true for dark theme, false for light theme
     */
    public void updateTheme(boolean darkMode) {
        this.darkMode = darkMode;
        if (!currentEntries.isEmpty()) {
            recomputeLayout();
        }
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
