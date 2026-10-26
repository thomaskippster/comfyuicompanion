package de.tki.comfyuicompanion.ui.huggingface;

import de.tki.comfyuicompanion.domain.HuggingFaceFileEntry;
import de.tki.comfyuicompanion.service.impl.HuggingFaceFolderRouter;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Visual table panel displaying files, quantizations, sizes, target folder overrides,
 * VRAM recommendations, and local installation states for a Hugging Face repository.
 */
public class HuggingFaceFilesTablePanel extends JPanel {

    private final List<HuggingFaceFileEntry> files = new ArrayList<>();
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JLabel summaryLabel;
    private final Function<HuggingFaceFileEntry, String> statusChecker;

    private static final String[] FOLDER_OPTIONS = {
            "models/unet",
            "models/checkpoints",
            "models/loras",
            "models/vae",
            "models/clip",
            "models/controlnet",
            "models/upscale_models",
            "models/diffusion_models"
    };

    /**
     * Constructs a new HuggingFaceFilesTablePanel.
     *
     * @param statusChecker function returning "Installed", "In Archive", or "Missing" for a file
     * @param bg            background color matching theme
     * @param textPrimary   primary text color
     * @param textSecondary secondary text color
     */
    public HuggingFaceFilesTablePanel(
            Function<HuggingFaceFileEntry, String> statusChecker,
            Color bg,
            Color textPrimary,
            Color textSecondary) {
        super(new BorderLayout(0, 6));
        this.statusChecker = statusChecker != null ? statusChecker : f -> "Missing";
        setOpaque(false);
        setBorder(BorderFactory.createTitledBorder("Quantization Models & Repository Files"));

        String[] cols = {"File Name", "Target Folder", "Size", "VRAM Guidance", "Status"};
        this.tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return c == 1; // Only Target Folder is editable
            }
        };

        this.table = new JTable(tableModel);
        this.table.setRowHeight(28);
        this.table.setShowGrid(false);
        this.table.setIntercellSpacing(new Dimension(0, 0));
        this.table.setFont(new Font("SansSerif", Font.PLAIN, 12));
        this.table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Target folder dropdown editor
        JComboBox<String> folderCombo = new JComboBox<>(FOLDER_OPTIONS);
        folderCombo.setFont(new Font("SansSerif", Font.PLAIN, 11));
        this.table.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(folderCombo));

        // Column widths
        this.table.getColumnModel().getColumn(0).setPreferredWidth(260);
        this.table.getColumnModel().getColumn(0).setMinWidth(180);
        this.table.getColumnModel().getColumn(1).setPreferredWidth(140);
        this.table.getColumnModel().getColumn(1).setMinWidth(120);
        this.table.getColumnModel().getColumn(2).setPreferredWidth(85);
        this.table.getColumnModel().getColumn(2).setMinWidth(70);
        this.table.getColumnModel().getColumn(3).setPreferredWidth(140);
        this.table.getColumnModel().getColumn(3).setMinWidth(110);
        this.table.getColumnModel().getColumn(4).setPreferredWidth(140);
        this.table.getColumnModel().getColumn(4).setMinWidth(100);

        // Custom cell renderer with status colorization
        this.table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean isSel, boolean hasFoc, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, val, isSel, hasFoc, row, col);
                if (!isSel) {
                    c.setBackground(row % 2 == 0 ? bg : new Color(Math.max(0, bg.getRed() - 6), Math.max(0, bg.getGreen() - 6), Math.max(0, bg.getBlue() - 6)));
                }
                c.setForeground(textPrimary);

                if (col == 4 && val != null) {
                    String st = val.toString();
                    if (st.contains("Installed") || st.contains("Already exists") || st.contains("Finished")) {
                        c.setForeground(new Color(34, 197, 130)); // Green
                    } else if (st.contains("Downloading") || st.contains("Starting") || st.contains("In Archive")) {
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

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(680, 200));
        add(scrollPane, BorderLayout.CENTER);

        summaryLabel = new JLabel(" ");
        summaryLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        summaryLabel.setForeground(textSecondary);
        add(summaryLabel, BorderLayout.SOUTH);
    }

    /**
     * Updates the displayed list of files and refreshes the table rows.
     *
     * @param newFiles list of file entries from the repository
     */
    public void setFiles(List<HuggingFaceFileEntry> newFiles) {
        this.files.clear();
        if (newFiles != null) {
            this.files.addAll(newFiles);
        }
        populateTable();
    }

    private void populateTable() {
        tableModel.setRowCount(0);
        long totalBytes = 0;
        int installedCount = 0;

        for (HuggingFaceFileEntry file : files) {
            totalBytes += file.sizeBytes();
            String status = statusChecker.apply(file);
            if ("Installed".equalsIgnoreCase(status)) {
                installedCount++;
            }

            tableModel.addRow(new Object[]{
                    file.filename(),
                    file.suggestedFolder(),
                    file.formattedSize(),
                    file.vramHint(),
                    status
            });
        }

        if (!files.isEmpty() && table.getSelectedRow() < 0) {
            table.setRowSelectionInterval(0, 0);
        }

        String summary = String.format("Total: %d file(s) (%s)  •  %d Installed, %d Missing",
                files.size(), HuggingFaceFolderRouter.formatBytes(totalBytes), installedCount, files.size() - installedCount);
        summaryLabel.setText(summary);
    }

    /**
     * Retrieves the currently selected file entry, or null if none selected.
     *
     * @return selected HuggingFaceFileEntry or null
     */
    public HuggingFaceFileEntry getSelectedFile() {
        int row = table.getSelectedRow();
        if (row >= 0 && row < files.size()) {
            return files.get(row);
        }
        return files.isEmpty() ? null : files.get(0);
    }

    /**
     * Retrieves the target folder selected in the table for the chosen file.
     *
     * @return chosen ComfyUI target folder path
     */
    public String getSelectedTargetFolder() {
        int row = table.getSelectedRow();
        if (row >= 0) {
            Object val = tableModel.getValueAt(row, 1);
            if (val != null) return val.toString();
        }
        HuggingFaceFileEntry sel = getSelectedFile();
        return sel != null ? sel.suggestedFolder() : "models/unet";
    }

    /**
     * Updates the status text for a specific file by its filename.
     *
     * @param filename the name of the file
     * @param status   the status message (e.g. "45%" or "Installed")
     */
    public void updateStatusForFile(String filename, String status) {
        if (filename == null) return;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            Object rowName = tableModel.getValueAt(i, 0);
            if (rowName != null && filename.equalsIgnoreCase(rowName.toString())) {
                tableModel.setValueAt(status, i, 4);
                break;
            }
        }
    }

    /**
     * Adds a selection listener to the underlying table.
     *
     * @param listener selection listener
     */
    public void addSelectionListener(javax.swing.event.ListSelectionListener listener) {
        table.getSelectionModel().addListSelectionListener(listener);
    }
}
