package de.tki.comfyuicompanion.ui.blueprint;

import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.IModelSearchService;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Panel that renders the table of required model weights, their folder targets,
 * sizes, and installation statuses for a blueprint workflow.
 */
public class BlueprintModelTablePanel extends JPanel {

    private static final Logger logger = LoggerFactory.getLogger(BlueprintModelTablePanel.class);

    /**
     * Constructs a new table panel.
     *
     * @param entry the blueprint entry
     * @param bg background color
     * @param textPrimary primary text color
     * @param textSecondary secondary text color
     * @param modelStatusResolver status resolving function
     * @param modelSizeResolver size resolving function
     * @param modelSearchService optional service for fetching remote model file sizes
     */
    public BlueprintModelTablePanel(
            BlueprintEntry entry,
            Color bg,
            Color textPrimary,
            Color textSecondary,
            Function<ModelInfo, String> modelStatusResolver,
            Function<ModelInfo, String> modelSizeResolver,
            IModelSearchService modelSearchService) {

        super(new BorderLayout());
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);

        if (entry.requiredModels == null || entry.requiredModels.isEmpty()) {
            return;
        }

        String[] columns = {"Model Name", "Type", "Size", "Status"};
        DefaultTableModel modelTblModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        for (int i = 0; i < entry.requiredModels.size(); i++) {
            ModelInfo info = entry.requiredModels.get(i);
            String name = info.getName();
            String type = info.getType() != null ? info.getType() : "checkpoints";
            String size = modelSizeResolver != null ? modelSizeResolver.apply(info) : "Unknown";
            String statusStr = modelStatusResolver != null ? modelStatusResolver.apply(info) : "Unknown";
            modelTblModel.addRow(new Object[]{name, type, size, statusStr});

            if ("Unknown".equalsIgnoreCase(size) && info.getUrl() != null && !info.getUrl().isBlank()
                    && !"MISSING".equals(info.getUrl()) && modelSearchService != null) {
                final int rowIndex = i;
                CompletableFuture.supplyAsync(() -> {
                    long bytes = modelSearchService.getRemoteSize(info.getUrl());
                    return modelSearchService.formatSize(bytes);
                }).thenAccept(resolvedSize -> {
                    if (!"Unknown".equalsIgnoreCase(resolvedSize) && !resolvedSize.contains("Auth")) {
                        info.setSize(resolvedSize);
                        SwingUtilities.invokeLater(() -> {
                            if (rowIndex < modelTblModel.getRowCount()) {
                                modelTblModel.setValueAt(resolvedSize, rowIndex, 2);
                            }
                        });
                    }
                }).exceptionally(ex -> {
                    logger.warn("Failed to fetch remote size for model: {}", name, ex);
                    return null;
                });
            }
        }

        JTable table = new JTable(modelTblModel);
        table.setFont(new Font("SansSerif", Font.PLAIN, 11));
        table.setRowHeight(24);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setBackground(bg);
        table.setForeground(textPrimary);
        table.getTableHeader().setBackground(bg);
        table.getTableHeader().setForeground(textSecondary);
        table.getTableHeader().setFont(new Font("SansSerif", Font.PLAIN, 11));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        table.getColumnModel().getColumn(0).setPreferredWidth(620);
        table.getColumnModel().getColumn(0).setMinWidth(350);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.getColumnModel().getColumn(1).setMinWidth(100);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.getColumnModel().getColumn(2).setMinWidth(80);
        table.getColumnModel().getColumn(3).setPreferredWidth(280);
        table.getColumnModel().getColumn(3).setMinWidth(180);

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean isSel, boolean hasFoc, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, val, isSel, hasFoc, row, col);
                c.setBackground(row % 2 == 0 ? bg : new Color(Math.max(0, bg.getRed() - 5), Math.max(0, bg.getGreen() - 5), Math.max(0, bg.getBlue() - 5)));
                c.setForeground(textPrimary);

                if (col == 3 && val != null) {
                    String st = val.toString();
                    if ("✅ Already exists".equals(st)) {
                        c.setForeground(new Color(0, 180, 160));
                    } else if ("📦 Archived".equals(st)) {
                        c.setForeground(new Color(224, 169, 109));
                    } else if ("🔄 Size Mismatch".equals(st)) {
                        c.setForeground(new Color(226, 88, 88));
                    } else if ("Idle".equals(st)) {
                        c.setForeground(textSecondary);
                    } else {
                        c.setForeground(new Color(0, 150, 186));
                    }
                }
                setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
                return c;
            }
        });

        JScrollPane tblScroll = new JScrollPane(table);
        int calcHeight = Math.min(220, Math.max(80, (entry.requiredModels.size() + 1) * 26 + 6));
        tblScroll.setPreferredSize(new Dimension(1190, calcHeight));
        tblScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, calcHeight));
        tblScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        tblScroll.getViewport().setBackground(bg);
        add(tblScroll, BorderLayout.CENTER);
    }
}
