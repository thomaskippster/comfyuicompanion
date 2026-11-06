package de.tki.comfyuicompanion.ui.blueprint;

import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.IModelSearchService;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintEntry;

import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Enterprise reusable panel encapsulating the Required Models JTable, custom cell renderers,
 * alternating row striping, status color highlights, and asynchronous file-size resolution.
 */
public class BlueprintModelTablePanel extends JPanel {

    /**
     * Constructs a new table panel from a blueprint entry.
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
        this(entry != null ? entry.requiredModels : Collections.emptyList(),
                bg, textPrimary, textSecondary, modelStatusResolver, modelSizeResolver, modelSearchService);
    }

    /**
     * Constructs a new table panel directly from a list of model dependencies.
     *
     * @param requiredModels list of model dependencies
     * @param bg background color
     * @param textPrimary primary text color
     * @param textSecondary secondary text color
     * @param modelStatusResolver status resolving function
     * @param modelSizeResolver size resolving function
     * @param modelSearchService optional service for fetching remote model file sizes
     */
    public BlueprintModelTablePanel(
            List<ModelInfo> requiredModels,
            Color bg,
            Color textPrimary,
            Color textSecondary,
            Function<ModelInfo, String> modelStatusResolver,
            Function<ModelInfo, String> modelSizeResolver,
            IModelSearchService modelSearchService) {

        super(new BorderLayout());
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);

        if (requiredModels == null || requiredModels.isEmpty()) {
            return;
        }

        String[] columns = {"Model Name", "Type", "Size", "Status"};
        DefaultTableModel modelTblModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        for (int i = 0; i < requiredModels.size(); i++) {
            ModelInfo info = requiredModels.get(i);
            String name = (info.getFilename() != null && !info.getFilename().isBlank()) ? info.getFilename() : info.getName();
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
                        SwingUtilities.invokeLater(() -> {
                            if (rowIndex < modelTblModel.getRowCount()) {
                                modelTblModel.setValueAt(resolvedSize, rowIndex, 2);
                            }
                        });
                    }
                });
            }
        }

        boolean isLight = (bg.getRed() * 299 + bg.getGreen() * 587 + bg.getBlue() * 114) / 1000 > 150;
        Color headerBg = isLight ? new Color(241, 245, 249) : bg;
        Color headerFg = isLight ? new Color(71, 85, 105) : textSecondary;
        Color rowAltBg = isLight ? new Color(248, 250, 252) : new Color(Math.max(0, bg.getRed() - 5), Math.max(0, bg.getGreen() - 5), Math.max(0, bg.getBlue() - 5));
        Color selBg = isLight ? new Color(224, 231, 255) : new Color(40, 48, 65);
        Color selFg = isLight ? new Color(15, 23, 42) : Color.WHITE;
        Color tableBorder = isLight ? new Color(226, 232, 240) : new Color(40, 48, 65);

        JTable table = new JTable(modelTblModel);
        table.setFont(new Font("SansSerif", Font.PLAIN, 12));
        table.setRowHeight(26);
        table.setShowGrid(isLight);
        table.setShowVerticalLines(false);
        if (isLight) {
            table.setGridColor(tableBorder);
        }
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setBackground(bg);
        table.setForeground(textPrimary);
        table.setSelectionBackground(selBg);
        table.setSelectionForeground(selFg);
        table.getTableHeader().setBackground(headerBg);
        table.getTableHeader().setForeground(headerFg);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, tableBorder));

        table.getColumnModel().getColumn(0).setPreferredWidth(550);
        table.getColumnModel().getColumn(0).setMinWidth(250);
        table.getColumnModel().getColumn(1).setPreferredWidth(140);
        table.getColumnModel().getColumn(1).setMinWidth(100);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.getColumnModel().getColumn(2).setMinWidth(80);
        table.getColumnModel().getColumn(3).setPreferredWidth(280);
        table.getColumnModel().getColumn(3).setMinWidth(180);

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean isSel, boolean hasFoc, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, val, isSel, hasFoc, row, col);
                if (!isSel) {
                    c.setBackground(row % 2 == 0 ? bg : rowAltBg);
                    c.setForeground(textPrimary);
                } else {
                    c.setBackground(selBg);
                    c.setForeground(selFg);
                }

                if (col == 3 && val != null) {
                    String st = val.toString();
                    if ("Already exists".equals(st)) {
                        c.setForeground(isLight ? new Color(16, 149, 106) : new Color(0, 180, 160));
                    } else if ("Archived".equals(st) || "Missing".equals(st)) {
                        c.setForeground(isLight ? new Color(194, 65, 12) : new Color(224, 169, 109));
                    } else if ("Size Mismatch".equals(st)) {
                        c.setForeground(isLight ? new Color(220, 38, 38) : new Color(226, 88, 88));
                    } else if ("Idle".equals(st)) {
                        c.setForeground(headerFg);
                    } else {
                        c.setForeground(isLight ? new Color(2, 132, 199) : new Color(0, 150, 186));
                    }
                }
                setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                return c;
            }
        });

        JScrollPane tblScroll = new JScrollPane(table);
        int calcHeight = WorkflowDialogDimensionHelper.calculateTablePreferredHeight(requiredModels.size());
        tblScroll.setPreferredSize(new Dimension(1190, calcHeight));
        tblScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        tblScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        tblScroll.getViewport().setBackground(bg);
        tblScroll.setBorder(BorderFactory.createLineBorder(tableBorder));
        add(tblScroll, BorderLayout.CENTER);
    }
}
