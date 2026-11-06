package de.tki.comfyuicompanion.ui.vault;



import de.tki.comfyuicompanion.domain.VaultWorkflowMetadata;

import de.tki.comfyuicompanion.domain.ModelInfo;

import de.tki.comfyuicompanion.service.IComfyLifecycleService;

import de.tki.comfyuicompanion.service.ILocalModelValidator;

import de.tki.comfyuicompanion.service.IModelSearchService;

import de.tki.comfyuicompanion.service.IWorkflowDownloader;

import de.tki.comfyuicompanion.service.impl.ConfigService;

import de.tki.comfyuicompanion.service.vault.WorkflowVaultCoordinator;

import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintEntry;

import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintStatus;

import de.tki.comfyuicompanion.ui.blueprint.BlueprintComfySender;

import de.tki.comfyuicompanion.ui.blueprint.BlueprintDetailsDialog;

import de.tki.comfyuicompanion.ui.blueprint.BlueprintPreviewCache;

import de.tki.comfyuicompanion.domain.WorkflowDescriptor;

import de.tki.comfyuicompanion.service.workflow.IWorkflowSourceStrategy;

import de.tki.comfyuicompanion.service.workflow.impl.VaultWorkflowStrategy;

import de.tki.comfyuicompanion.ui.workflow.UnifiedWorkflowCardBuilder;

import de.tki.comfyuicompanion.ui.workflow.UnifiedWorkflowDetailsDialog;



import org.slf4j.Logger;

import org.slf4j.LoggerFactory;



import javax.swing.*;

import javax.swing.border.EmptyBorder;

import java.awt.*;

import java.awt.event.ComponentAdapter;

import java.awt.event.ComponentEvent;

import java.io.File;

import java.util.*;

import java.util.List;

import java.util.concurrent.CopyOnWriteArrayList;

import java.util.function.Consumer;



/**

 * Enterprise View component for My Workflow Vault.

 * Houses the top Drag & Drop / File Chooser ingestion zone, the ecosystem filter toolbar,

 * and the responsive multi-column card grid for user-curated workflows.

 */

public class WorkflowVaultPanel extends JPanel {



    private static final Logger logger = LoggerFactory.getLogger(WorkflowVaultPanel.class);

    private static final int CARD_GAP = 14;



    private final WorkflowVaultCoordinator vaultCoordinator;

    private final BlueprintPreviewCache previewCache;

    private final ConfigService configService;

    private final IWorkflowDownloader workflowDownloader;

    private final IComfyLifecycleService lifecycleService;

    private final ILocalModelValidator localModelValidator;

    private final IModelSearchService modelSearchService;



    private final VaultIngestionDropZonePanel dropZonePanel;
    private final WorkflowVaultToolbar toolbar;
    private final JPanel northWrapper;
    private final JPanel galleryWrapper;
    private final JScrollPane scrollPane;
    private final List<JLabel> sectionHeaderLabels = new CopyOnWriteArrayList<>();



    private final List<BlueprintEntry> allVaultEntries = new CopyOnWriteArrayList<>();

    private final List<JPanel> categoryGridPanels = new CopyOnWriteArrayList<>();

    private final java.util.concurrent.atomic.AtomicBoolean isRefreshingVault = new java.util.concurrent.atomic.AtomicBoolean(false);

    private int lastColumns = -1;



    private Consumer<File> onWorkflowImportRequested;

    private Consumer<List<ModelInfo>> onSwitchToDownloadManager;



    /**

     * Constructs a new WorkflowVaultPanel with injected enterprise dependencies.

     */

    public WorkflowVaultPanel(

            WorkflowVaultCoordinator vaultCoordinator,

            BlueprintPreviewCache previewCache,

            ConfigService configService,

            IWorkflowDownloader workflowDownloader,

            IComfyLifecycleService lifecycleService,

            ILocalModelValidator localModelValidator,

            IModelSearchService modelSearchService) {



        super(new BorderLayout(0, 8));

        this.vaultCoordinator = vaultCoordinator;

        this.previewCache = previewCache;

        this.configService = configService;

        this.workflowDownloader = workflowDownloader;

        this.lifecycleService = lifecycleService;

        this.localModelValidator = localModelValidator;

        this.modelSearchService = modelSearchService;



        Color surfaceBg = getSurfaceBg();
        setOpaque(true);
        setBackground(surfaceBg);

        // 1. North Wrapper: Ingestion Drop Zone + Filter Toolbar
        northWrapper = new JPanel();
        northWrapper.setLayout(new BoxLayout(northWrapper, BoxLayout.Y_AXIS));
        northWrapper.setOpaque(true);
        northWrapper.setBackground(surfaceBg);
        northWrapper.setBorder(new EmptyBorder(4, 20, 4, 20));

        dropZonePanel = new VaultIngestionDropZonePanel(this::handleFileImport, this::handleRawJsonPasted);
        toolbar = new WorkflowVaultToolbar(configService == null || configService.isDarkMode());

        toolbar.setOnFilterChanged(this::applyFilter);

        toolbar.setOnBatchDownloadClicked(this::handleBatchDownload);



        northWrapper.add(dropZonePanel);

        northWrapper.add(Box.createVerticalStrut(10));

        northWrapper.add(toolbar);

        add(northWrapper, BorderLayout.NORTH);



        // 2. Center: Scrolling Card Grid

        galleryWrapper = new ScrollableVaultPanel();

        galleryWrapper.setLayout(new BoxLayout(galleryWrapper, BoxLayout.Y_AXIS));

        galleryWrapper.setOpaque(true);

        galleryWrapper.setBackground(surfaceBg);

        galleryWrapper.setBorder(new EmptyBorder(6, 20, 20, 20));



        scrollPane = new JScrollPane(galleryWrapper);

        scrollPane.setBorder(null);

        scrollPane.setOpaque(true);

        scrollPane.setBackground(surfaceBg);

        scrollPane.getViewport().setOpaque(true);

        scrollPane.getViewport().setBackground(surfaceBg);

        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        scrollPane.addComponentListener(new ComponentAdapter() {

            @Override

            public void componentResized(ComponentEvent e) {

                recomputeColumns();

            }

        });



        add(scrollPane, BorderLayout.CENTER);



        if (this.vaultCoordinator != null) {

            this.vaultCoordinator.addVaultChangeListener(() -> SwingUtilities.invokeLater(() -> {

                allVaultEntries.clear();

                allVaultEntries.addAll(this.vaultCoordinator.getCachedVaultEntries());

                updateArchitecturePills();

                applyFilter();

            }));

        }



        addComponentListener(new ComponentAdapter() {

            @Override

            public void componentShown(ComponentEvent e) {

                refreshVaultData();

            }

        });

    }



    public void setOnWorkflowImportRequested(Consumer<File> onWorkflowImportRequested) {

        this.onWorkflowImportRequested = onWorkflowImportRequested;

    }



    public void setOnSwitchToDownloadManager(Consumer<List<ModelInfo>> onSwitchToDownloadManager) {

        this.onSwitchToDownloadManager = onSwitchToDownloadManager;

    }



    /**

     * Refreshes all entries from the vault directory asynchronously.

     */

    public void refreshVaultData() {

        if (!isRefreshingVault.compareAndSet(false, true)) {

            return;

        }

        Thread.ofVirtual().name("WorkflowVaultRefresh").start(() -> {

            try {

                if (vaultCoordinator != null) {

                    vaultCoordinator.refreshVaultEntries();

                }

            } catch (Exception ex) {

                logger.error("Failed refreshing vault data: {}", ex.getMessage(), ex);

            } finally {

                isRefreshingVault.set(false);

            }

        });

    }



    private void updateArchitecturePills() {

        Set<String> archSet = new LinkedHashSet<>();

        archSet.add("All");

        for (BlueprintEntry e : allVaultEntries) {

            String arch = inferArchitecture(e);

            if (!arch.isBlank()) archSet.add(arch);

        }

        toolbar.updateArchitecturePills(new ArrayList<>(archSet), toolbar.getSelectedArchitecture());

    }



    private String inferArchitecture(BlueprintEntry entry) {

        String cat = entry.category != null ? entry.category.toLowerCase(Locale.ROOT) : "";

        String name = entry.name != null ? entry.name.toLowerCase(Locale.ROOT) : "";

        if (cat.contains("flux") || name.contains("flux")) return "Flux";

        if (cat.contains("qwen") || name.contains("qwen")) return "Qwen";

        if (cat.contains("sdxl") || name.contains("sdxl")) return "SDXL";

        if (cat.contains("sd 1.5") || name.contains("sd15")) return "SD 1.5";

        if (cat.contains("wan") || name.contains("wan")) return "Wan";

        if (cat.contains("krea") || name.contains("krea")) return "Krea / Style";

        if (cat.contains("z-image") || name.contains("z_image")) return "Z-Image";

        return "General";

    }



    /**

     * Filters and renders visible vault entries.

     */

    public void applyFilter() {
        galleryWrapper.removeAll();
        categoryGridPanels.clear();
        sectionHeaderLabels.clear();



        String search = toolbar.getSearchText().trim().toLowerCase(Locale.ROOT);

        String selectedArch = toolbar.getSelectedArchitecture();

        boolean readyOnly = toolbar.isReadyOnly();



        List<BlueprintEntry> filtered = new ArrayList<>();

        int totalMissingModels = 0;



        for (BlueprintEntry e : allVaultEntries) {

            BlueprintStatus st = vaultCoordinator.computeStatus(e);

            if (st.missingCount > 0) totalMissingModels += st.missingCount;



            if (readyOnly && st.missingCount > 0) continue;

            if (!selectedArch.equalsIgnoreCase("All") && !inferArchitecture(e).equalsIgnoreCase(selectedArch)) continue;

            if (!search.isEmpty() && !e.name.toLowerCase(Locale.ROOT).contains(search)

                    && !e.category.toLowerCase(Locale.ROOT).contains(search)) continue;



            filtered.add(e);

        }



        toolbar.updateMissingCount(totalMissingModels);



        if (filtered.isEmpty()) {

            JLabel emptyLbl = new JLabel(allVaultEntries.isEmpty()
                    ? "Your Workflow Vault is currently empty. Drop .json/.png workflows above or save from Comfy.org/Civitai/Hugging Face!"
                    : "No vault workflows match your current filter criteria.") {
                @Override
                public void updateUI() {
                    super.updateUI();
                    boolean d = configService != null ? configService.isDarkMode() : de.tki.comfyuicompanion.ui.ThemeManager.isDarkMode();
                    setForeground(d ? new Color(130, 138, 165) : new Color(100, 116, 139));
                }
            };
            emptyLbl.setFont(new Font("SansSerif", Font.ITALIC, 13));
            boolean dark = configService != null ? configService.isDarkMode() : de.tki.comfyuicompanion.ui.ThemeManager.isDarkMode();
            emptyLbl.setForeground(dark ? new Color(130, 138, 165) : new Color(100, 116, 139));

            emptyLbl.setBorder(new EmptyBorder(20, 10, 20, 10));

            galleryWrapper.add(emptyLbl);

        } else {

            renderGroupedEntries(filtered);

        }



        galleryWrapper.revalidate();

        galleryWrapper.repaint();

    }



    private void renderGroupedEntries(List<BlueprintEntry> entries) {

        Map<String, List<BlueprintEntry>> grouped = new LinkedHashMap<>();

        for (BlueprintEntry e : entries) {

            String arch = inferArchitecture(e);

            grouped.computeIfAbsent(arch, k -> new ArrayList<>()).add(e);

        }



        int cols = calculateColumns();

        lastColumns = cols;

        for (Map.Entry<String, List<BlueprintEntry>> group : grouped.entrySet()) {

            galleryWrapper.add(buildSectionHeader(group.getKey() + " Architecture (" + group.getValue().size() + ")"));



            JPanel grid = new JPanel(new GridLayout(0, cols, CARD_GAP, CARD_GAP));
            grid.setOpaque(true);
            grid.setBackground(getSurfaceBg());

            grid.setBorder(new EmptyBorder(8, 0, 16, 0));



            for (BlueprintEntry entry : group.getValue()) {

                BlueprintStatus status = vaultCoordinator.computeStatus(entry);

                WorkflowDescriptor desc = WorkflowDescriptor.builder()

                        .id("vault-" + (entry.filename != null ? entry.filename : entry.name))

                        .title(entry.name)

                        .category(entry.category != null ? entry.category : "Vault Workflow")

                        .description(entry.description != null ? entry.description : "")

                        .sourceType(de.tki.comfyuicompanion.domain.WorkflowSourceType.VAULT)

                        .previewPathOrUrl(entry.previewPath)

                        .workflowFilePathOrUrl(entry.filePath)

                        .requiredModels(entry.requiredModels != null ? entry.requiredModels : Collections.emptyList())

                        .missingModelsCount(status != null ? status.missingCount : 0)

                        .rawSourceEntry(entry)

                        .build();

                grid.add(UnifiedWorkflowCardBuilder.buildCard(

                        desc,

                        d -> showDetails(entry),

                        d -> handleDownloadMissing(entry),

                        d -> handleRemoveEntry(entry)

                ));

            }



            int remainder = group.getValue().size() % cols;

            if (remainder > 0) {

                int emptySlots = cols - remainder;

                for (int i = 0; i < emptySlots; i++) {

                    JPanel placeholder = new JPanel();

                    placeholder.setOpaque(false);

                    grid.add(placeholder);

                }

            }



            categoryGridPanels.add(grid);

            galleryWrapper.add(grid);

        }

    }



    private JPanel buildSectionHeader(String title) {
        JPanel hdr = new JPanel(new BorderLayout());
        hdr.setOpaque(false);
        hdr.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        hdr.setBorder(new EmptyBorder(6, 4, 2, 4));
        JLabel lbl = new JLabel(title.toUpperCase(Locale.ROOT)) {
            @Override
            public void updateUI() {
                super.updateUI();
                boolean d = configService != null ? configService.isDarkMode() : de.tki.comfyuicompanion.ui.ThemeManager.isDarkMode();
                setForeground(d ? new Color(0, 210, 190) : new Color(13, 148, 136));
            }
        };
        lbl.setFont(new Font("SansSerif", Font.BOLD, 11));
        boolean dark = configService != null ? configService.isDarkMode() : de.tki.comfyuicompanion.ui.ThemeManager.isDarkMode();
        lbl.setForeground(dark ? new Color(0, 210, 190) : new Color(13, 148, 136));
        sectionHeaderLabels.add(lbl);
        hdr.add(lbl, BorderLayout.WEST);
        return hdr;
    }

    private Color getSurfaceBg() {
        boolean dark = configService != null ? configService.isDarkMode() : de.tki.comfyuicompanion.ui.ThemeManager.isDarkMode();
        return dark ? new Color(15, 16, 21) : new Color(245, 247, 250);
    }



    private int calculateColumns() {

        int w = scrollPane != null && scrollPane.getWidth() > 0 ? scrollPane.getWidth() : getWidth();

        if (w <= 0) w = 1000;

        return Math.max(1, Math.min(6, (w - 56) / (220 + CARD_GAP)));

    }



    private void recomputeColumns() {

        int cols = calculateColumns();

        if (cols != lastColumns && !allVaultEntries.isEmpty()) {

            applyFilter();

        }

    }



    private void handleFileImport(File file) {

        Thread.ofVirtual().name("VaultFileImport").start(() -> {

            try {

                BlueprintEntry entry = vaultCoordinator.importFile(file);

                SwingUtilities.invokeLater(() -> {

                    JOptionPane.showMessageDialog(this,

                            "Successfully imported '" + entry.name + "' into My Workflow Vault!",

                            "Workflow Vault", JOptionPane.INFORMATION_MESSAGE);

                    refreshVaultData();

                });

            } catch (Exception ex) {

                logger.error("Failed importing file to vault: {}", ex.getMessage());

                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,

                        "Failed to import workflow: " + ex.getMessage(),

                        "Import Error", JOptionPane.ERROR_MESSAGE));

            }

        });

    }



    private void handleRawJsonPasted(String json) {

        Thread.ofVirtual().name("VaultPastedJson").start(() -> {

            try {

                String baseName = "pasted_workflow_" + System.currentTimeMillis();

                VaultWorkflowMetadata meta = new VaultWorkflowMetadata(

                        baseName, "Pasted Workflow", "Imported via Clipboard Paste",

                        "Text to Image", "General", "CLIPBOARD", "", "", List.of(), System.currentTimeMillis()

                );

                vaultCoordinator.saveToVault(baseName, json, null, null, meta);

                SwingUtilities.invokeLater(this::refreshVaultData);

            } catch (Exception ex) {

                logger.error("Failed saving pasted JSON: {}", ex.getMessage());

            }

        });

    }



    private void handleDownloadMissing(BlueprintEntry entry) {

        if (entry == null || entry.requiredModels == null) return;

        List<ModelInfo> missing = new ArrayList<>();

        for (ModelInfo m : entry.requiredModels) {

            if (!vaultCoordinator.isModelPresent(m)) {

                missing.add(m);

            }

        }

        if (missing.isEmpty()) {

            JOptionPane.showMessageDialog(this, "All models for this workflow are already downloaded!", "Models Ready", JOptionPane.INFORMATION_MESSAGE);

            return;

        }



        if (onSwitchToDownloadManager != null) {

            onSwitchToDownloadManager.accept(missing);

        } else {

            Thread.ofVirtual().name("VaultDownloadMissing").start(() -> {

                int queued = vaultCoordinator.downloadMissingModels(entry, null);

                if (queued > 0) {

                    SwingUtilities.invokeLater(() -> {

                        JOptionPane.showMessageDialog(this, "Queued " + queued + " missing model(s) in Download Manager.", "Download Manager", JOptionPane.INFORMATION_MESSAGE);

                        refreshVaultData();

                    });

                }

            });

        }

    }



    private void handleBatchDownload() {

        int totalQueued = 0;

        for (BlueprintEntry e : allVaultEntries) {

            BlueprintStatus st = vaultCoordinator.computeStatus(e);

            if (st.missingCount > 0) {

                totalQueued += vaultCoordinator.downloadMissingModels(e, null);

            }

        }

        if (totalQueued > 0) {

            JOptionPane.showMessageDialog(this,

                    "Queued " + totalQueued + " missing model(s) for download across your Vault workflows.",

                    "Download Manager", JOptionPane.INFORMATION_MESSAGE);

            refreshVaultData();

        }

    }



    private void handleRemoveEntry(BlueprintEntry entry) {

        int confirm = JOptionPane.showConfirmDialog(this,

                "Are you sure you want to remove '" + entry.name + "' from My Workflow Vault?",

                "Confirm Remove", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {

            vaultCoordinator.removeWorkflow(entry);

            refreshVaultData();

        }

    }



    private void showDetails(BlueprintEntry entry) {

        if (entry == null) return;

        BlueprintStatus status = vaultCoordinator != null ? vaultCoordinator.computeStatus(entry) : null;

        IWorkflowSourceStrategy strategy = new VaultWorkflowStrategy(vaultCoordinator, null);

        WorkflowDescriptor desc = strategy.toWorkflowDescriptor(entry);

        new UnifiedWorkflowDetailsDialog(

                SwingUtilities.getWindowAncestor(this),

                desc,

                strategy,

                configService,

                null,

                modelSearchService,

                vaultCoordinator,

                json -> sendWorkflowEntryToComfy(entry),

                onSwitchToDownloadManager

        ).setVisible(true);

    }



    private void sendWorkflowEntryToComfy(BlueprintEntry entry) {

        BlueprintComfySender.sendToComfyUI(

                entry, null, workflowDownloader, lifecycleService, configService,

                null, this, success -> {

                    if (Boolean.TRUE.equals(success)) {

                        logger.info("Workflow '{}' successfully dispatched to ComfyUI.", entry.name);

                    }

                }

        );

    }



    public void updateTheme(boolean darkMode) {
        Color surfaceBg = darkMode ? new Color(15, 16, 21) : new Color(245, 247, 250);
        setBackground(surfaceBg);
        if (northWrapper != null) {
            northWrapper.setBackground(surfaceBg);
        }
        if (dropZonePanel != null) {
            dropZonePanel.updateTheme(darkMode);
        }
        if (toolbar != null) {
            toolbar.updateTheme(darkMode);
        }
        if (galleryWrapper != null) {
            galleryWrapper.setBackground(surfaceBg);
        }
        if (scrollPane != null) {
            scrollPane.setBackground(surfaceBg);
            if (scrollPane.getViewport() != null) {
                scrollPane.getViewport().setBackground(surfaceBg);
            }
        }
        if (categoryGridPanels != null) {
            for (JPanel grid : categoryGridPanels) {
                if (grid != null) {
                    grid.setBackground(surfaceBg);
                }
            }
        }
        if (sectionHeaderLabels != null) {
            Color hdrColor = darkMode ? new Color(0, 210, 190) : new Color(13, 148, 136);
            for (JLabel lbl : sectionHeaderLabels) {
                if (lbl != null) {
                    lbl.setForeground(hdrColor);
                }
            }
        }
        repaint();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        boolean dark = configService != null ? configService.isDarkMode() : de.tki.comfyuicompanion.ui.ThemeManager.isDarkMode();
        updateTheme(dark);
    }



    private static final class ScrollableVaultPanel extends JPanel implements Scrollable {

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

