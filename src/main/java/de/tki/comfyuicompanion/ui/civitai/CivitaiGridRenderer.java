package de.tki.comfyuicompanion.ui.civitai;

import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.domain.WorkflowDescriptor;
import de.tki.comfyuicompanion.service.workflow.impl.CivitaiWorkflowStrategy;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
import de.tki.comfyuicompanion.ui.workflow.UnifiedWorkflowCardBuilder;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Manages responsive multi-column rendering of Civitai model cards using the consolidated
 * {@link UnifiedWorkflowCardBuilder}, handling scrolling, loading, and dynamic column layouts.
 *
 * @author Senior Java Developer
 */
public class CivitaiGridRenderer {

    private static final int CARD_GAP = 14;
    private static final int TARGET_CARD_WIDTH = 220;

    private final ScrollablePanel contentWrapper;
    private final JScrollPane scrollPane;
    private final CivitaiWorkflowStrategy strategy;
    private JPanel gridPanel;

    private List<CivitaiModelEntry> currentEntries = Collections.emptyList();
    private Predicate<CivitaiModelEntry> installedChecker = e -> false;
    private final Map<Long, String> downloadingStatuses = new ConcurrentHashMap<>();
    private final Set<Long> downloadingIds = ConcurrentHashMap.newKeySet();

    private Consumer<CivitaiModelEntry> downloadAction;
    private Consumer<CivitaiModelEntry> detailsAction;
    private boolean darkMode = true;
    private int lastColumns = -1;

    /**
     * Constructs a new CivitaiGridRenderer with default operational service.
     */
    public CivitaiGridRenderer() {
        this((de.tki.comfyuicompanion.service.ICivitaiOperationalModelService) null);
    }

    /**
     * Constructs a new CivitaiGridRenderer with the injected operational model service.
     *
     * @param operationalModelService service resolving multi-model workflow requirements
     */
    public CivitaiGridRenderer(de.tki.comfyuicompanion.service.ICivitaiOperationalModelService operationalModelService) {
        this.strategy = new CivitaiWorkflowStrategy(operationalModelService);
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
                recomputeLayout(false);
            }
        });
    }

    public JScrollPane getScrollPane() {
        return scrollPane;
    }

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

    public void showEmpty(String query) {
        contentWrapper.removeAll();
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);

        String msg = (query == null || query.isBlank())
                ? "No models found matching current criteria."
                : "No models found matching '" + query + "'.";

        JLabel emptyLbl = new JLabel(msg, SvgIconFactory.get(AppIcon.HELP, 20), SwingConstants.CENTER);
        emptyLbl.setFont(new Font("SansSerif", Font.PLAIN, 14));
        emptyLbl.setForeground(darkMode ? Color.GRAY : Color.DARK_GRAY);
        center.add(emptyLbl);

        contentWrapper.add(center, BorderLayout.CENTER);
        contentWrapper.revalidate();
        contentWrapper.repaint();
    }

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

        recomputeLayout(true);
    }

    public void updateDownloadStatus(long modelId, String statusMessage) {
        if (statusMessage.contains("Finished") || statusMessage.contains("Already exists")) {
            downloadingIds.remove(modelId);
            downloadingStatuses.remove(modelId);
        } else {
            downloadingIds.add(modelId);
            downloadingStatuses.put(modelId, statusMessage);
        }
        recomputeLayout(true);
    }

    private int calculateColumns() {
        int availableWidth = scrollPane.getWidth() - 52;
        if (availableWidth <= 0) availableWidth = 1000;
        return Math.max(1, availableWidth / (TARGET_CARD_WIDTH + CARD_GAP));
    }

    private void recomputeLayout(boolean forceRebuild) {
        if (currentEntries.isEmpty()) return;

        int cols = calculateColumns();
        if (!forceRebuild && cols == lastColumns && gridPanel != null) {
            return;
        }
        lastColumns = cols;

        contentWrapper.removeAll();

        gridPanel = new JPanel(new GridLayout(0, cols, CARD_GAP, CARD_GAP));
        gridPanel.setOpaque(false);

        for (CivitaiModelEntry entry : currentEntries) {
            boolean isInstalled = installedChecker.test(entry);
            boolean isDownloading = downloadingIds.contains(entry.id());
            String progress = downloadingStatuses.get(entry.id());

            WorkflowDescriptor desc = strategy.toWorkflowDescriptor(entry);
            Map<String, String> extended = new HashMap<>(desc.extendedAttributes());
            if (isDownloading && progress != null) {
                extended.put("downloadStatus", progress);
            }
            int missingCount = isInstalled ? 0 : Math.max(desc.missingModelsCount(), 1);
            desc = WorkflowDescriptor.builder()
                    .id(desc.id())
                    .title(desc.title())
                    .category(desc.category())
                    .architecture(desc.architecture())
                    .author(desc.author())
                    .description(desc.description())
                    .sourceType(desc.sourceType())
                    .sourceUrl(desc.sourceUrl())
                    .previewPathOrUrl(desc.previewPathOrUrl())
                    .workflowFilePathOrUrl(desc.workflowFilePathOrUrl())
                    .requiredModels(desc.requiredModels())
                    .missingModelsCount(missingCount)
                    .rawSourceEntry(desc.rawSourceEntry())
                    .extendedAttributes(extended)
                    .build();

            JPanel card = UnifiedWorkflowCardBuilder.buildCard(
                    desc,
                    d -> { if (detailsAction != null) detailsAction.accept(entry); },
                    d -> { if (downloadAction != null) downloadAction.accept(entry); }
            );
            gridPanel.add(card);
        }

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

    public void updateTheme(boolean darkMode) {
        this.darkMode = darkMode;
        if (contentWrapper != null) {
            contentWrapper.setBackground(darkMode ? new Color(15, 16, 21) : new Color(245, 247, 250));
            contentWrapper.repaint();
        }
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
