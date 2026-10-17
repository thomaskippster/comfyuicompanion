package de.tki.comfyuicompanion.ui.civitai;

import de.tki.comfyuicompanion.domain.ModelInfo;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * UI panel displaying the operational models status table and summary text
 * in the Civitai model details dialog.
 */
public class CivitaiOperationalTablePanel extends JPanel {

    private final List<ModelInfo> requiredModels;
    private final DefaultTableModel tableModel;
    private final JLabel summaryLabel;

    /**
     * Constructs a new CivitaiOperationalTablePanel for the given operational models list.
     *
     * @param requiredModels list of models required to operate the selected model
     * @param bg             background color matching theme
     * @param textPrimary    primary text color
     * @param textSecondary  secondary text color
     */
    public CivitaiOperationalTablePanel(List<ModelInfo> requiredModels, Color bg, Color textPrimary, Color textSecondary) {
        super(new BorderLayout(0, 6));
        this.requiredModels = requiredModels;
        setOpaque(false);
        setBorder(BorderFactory.createTitledBorder("Required Operational Models for ComfyUI"));

        String[] cols = {"Model Name", "Target Folder", "Size", "Status"};
        this.tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        populateTableModel();

        JTable table = new JTable(tableModel);
        table.setRowHeight(26);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFont(new Font("SansSerif", Font.PLAIN, 12));

        table.getColumnModel().getColumn(0).setPreferredWidth(280);
        table.getColumnModel().getColumn(0).setMinWidth(200);
        table.getColumnModel().getColumn(1).setPreferredWidth(110);
        table.getColumnModel().getColumn(1).setMinWidth(90);
        table.getColumnModel().getColumn(2).setPreferredWidth(90);
        table.getColumnModel().getColumn(2).setMinWidth(75);
        table.getColumnModel().getColumn(3).setPreferredWidth(340);
        table.getColumnModel().getColumn(3).setMinWidth(280);

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean isSel, boolean hasFoc, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, val, isSel, hasFoc, row, col);
                c.setBackground(row % 2 == 0 ? bg : new Color(Math.max(0, bg.getRed() - 6), Math.max(0, bg.getGreen() - 6), Math.max(0, bg.getBlue() - 6)));
                c.setForeground(textPrimary);

                if (col == 3 && val != null) {
                    String st = val.toString();
                    if (st.contains("Already exists") || st.contains("Installed") || st.contains("Finished")) {
                        c.setForeground(new Color(34, 197, 130)); // Green
                    } else if (st.contains("Downloading") || st.contains("Starting")) {
                        c.setForeground(new Color(245, 158, 11)); // Amber
                    } else if (st.contains("Missing")) {
                        c.setForeground(new Color(59, 130, 246)); // Blue
                    } else if (st.contains("Error") || st.contains("Failed")) {
                        c.setForeground(new Color(239, 68, 68)); // Red
                    }
                }
                setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
                return c;
            }
        });

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(680, 160));
        add(tableScroll, BorderLayout.CENTER);

        summaryLabel = new JLabel(" ");
        summaryLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        summaryLabel.setForeground(textSecondary);
        updateSummaryText();
        add(summaryLabel, BorderLayout.SOUTH);
    }

    /**
     * Re-populates the table rows from the underlying required operational models list.
     */
    public void populateTableModel() {
        tableModel.setRowCount(0);
        for (ModelInfo info : requiredModels) {
            String name = info.getFilename() != null && !info.getFilename().isBlank() ? info.getFilename() : info.getName();
            String type = info.getSave_path() != null ? info.getSave_path() : (info.getType() != null ? info.getType() : "checkpoints");
            String size = info.getSize() != null ? info.getSize() : "Unknown";
            String status = info.getDescription() != null ? info.getDescription() : "Missing";
            tableModel.addRow(new Object[]{name, type, size, status});
        }
    }

    /**
     * Updates the status text of a specific model in the table row.
     *
     * @param info   the model metadata
     * @param status status text to display
     */
    public void setStatusForModel(ModelInfo info, String status) {
        String filename = info.getFilename();
        String name = info.getName();
        for (int r = 0; r < tableModel.getRowCount(); r++) {
            Object rowVal = tableModel.getValueAt(r, 0);
            if ((filename != null && filename.equals(rowVal)) || (name != null && name.equals(rowVal))) {
                tableModel.setValueAt(status, r, 3);
                break;
            }
        }
    }

    /**
     * Recalculates and updates the summary statistics text.
     */
    public void updateSummaryText() {
        int total = requiredModels.size();
        int present = 0;
        int missing = 0;
        for (ModelInfo info : requiredModels) {
            if ("installed".equalsIgnoreCase(info.getPopularity()) || (info.getDescription() != null && info.getDescription().contains("Already exists"))) {
                present++;
            } else {
                missing++;
            }
        }
        summaryLabel.setText(String.format("Required models: %d  •  Available locally: %d  •  Missing: %d", total, present, missing));
    }

    /**
     * Returns the count of missing models.
     *
     * @return missing models count
     */
    public int getMissingCount() {
        int count = 0;
        for (ModelInfo info : requiredModels) {
            if (!"installed".equalsIgnoreCase(info.getPopularity()) && (info.getDescription() == null || !info.getDescription().contains("Already exists"))) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns the underlying table model.
     *
     * @return the table model
     */
    public DefaultTableModel getTableModel() {
        return tableModel;
    }
}
