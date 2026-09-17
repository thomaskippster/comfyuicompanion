package de.tki.comfyuicompanion.ui.dialog;

import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.IModelSearchService;
import de.tki.comfyuicompanion.service.impl.ArchiveService;
import de.tki.comfyuicompanion.ui.DownloadManagerView;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
import de.tki.comfyuicompanion.util.BackgroundExecutor;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Manages the archive and restore modal dialogs for models, including
 * interactive selection tables, sorting, and progress bar feedback.
 */
public class DownloadArchiveDialog {

    private final DownloadManagerView view;
    private final ArchiveService archiveService;
    private final IModelSearchService searchService;
    private final BackgroundExecutor backgroundExecutor;
    private final Runnable postOperationCallback;
    private final Runnable analysisRefreshCallback;

    public DownloadArchiveDialog(DownloadManagerView view,
                                 ArchiveService archiveService,
                                 IModelSearchService searchService,
                                 BackgroundExecutor backgroundExecutor,
                                 Runnable postOperationCallback,
                                 Runnable analysisRefreshCallback) {
        this.view = view;
        this.archiveService = archiveService;
        this.searchService = searchService;
        this.backgroundExecutor = backgroundExecutor != null ? backgroundExecutor : new BackgroundExecutor();
        this.postOperationCallback = postOperationCallback;
        this.analysisRefreshCallback = analysisRefreshCallback;
    }

    /**
     * Opens the Archive Manager dialog with tabs for archiving and restoring models.
     */
    public void showArchiveDialog() {
        JDialog dialog = new JDialog((JFrame) SwingUtilities.getWindowAncestor(view), "Archive Manager", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(1000, 700);
        dialog.setLocationRelativeTo(view);

        JTabbedPane tabs = new JTabbedPane();

        JPanel archivePanel = new JPanel(new BorderLayout(10, 10));
        archivePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        Map<String, List<ModelInfo>> groupedToArchive = archiveService.getModelsGroupedByFolder();
        String[] columns = {"Select", "Folder", "Name", "Size"};
        DefaultTableModel archiveTableModel = new DefaultTableModel(columns, 0) {
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : String.class; }
            @Override public boolean isCellEditable(int r, int c) { return c == 0; }
        };

        for (Map.Entry<String, List<ModelInfo>> entry : groupedToArchive.entrySet()) {
            for (ModelInfo model : entry.getValue()) {
                archiveTableModel.addRow(new Object[]{false, entry.getKey(), model.getName(), model.getSize()});
            }
        }

        Map<String, List<ModelInfo>> groupedToRestore = archiveService.getArchivedModelsGroupedByFolder();
        DefaultTableModel restoreTableModel = new DefaultTableModel(columns, 0) {
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : String.class; }
            @Override public boolean isCellEditable(int r, int c) { return c == 0; }
        };

        for (Map.Entry<String, List<ModelInfo>> entry : groupedToRestore.entrySet()) {
            for (ModelInfo model : entry.getValue()) {
                restoreTableModel.addRow(new Object[]{false, entry.getKey(), model.getName(), model.getSize()});
            }
        }

        JTable archiveTable = new JTable(archiveTableModel);
        archiveTable.setRowHeight(25);
        archiveTable.getColumnModel().getColumn(0).setMaxWidth(50);

        javax.swing.table.TableRowSorter<DefaultTableModel> archiveSorter = new javax.swing.table.TableRowSorter<>(archiveTableModel);
        archiveSorter.setComparator(3, (s1, s2) -> Long.compare(parseSizeToBytes((String) s1), parseSizeToBytes((String) s2)));
        archiveSorter.setSortable(0, false);
        archiveTable.setRowSorter(archiveSorter);

        archiveTable.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewIdx = archiveTable.columnAtPoint(e.getPoint());
                if (viewIdx == -1) return;
                int modelIdx = archiveTable.convertColumnIndexToModel(viewIdx);
                if (modelIdx == 0) {
                    boolean allSelected = true;
                    for (int i = 0; i < archiveTableModel.getRowCount(); i++) {
                        if (!(Boolean) archiveTableModel.getValueAt(i, 0)) {
                            allSelected = false;
                            break;
                        }
                    }
                    boolean newValue = !allSelected;
                    for (int i = 0; i < archiveTableModel.getRowCount(); i++) {
                        archiveTableModel.setValueAt(newValue, i, 0);
                    }
                }
            }
        });

        JScrollPane archiveScroll = new JScrollPane(archiveTable);
        archivePanel.add(archiveScroll, BorderLayout.CENTER);

        JPanel archiveBottom = new JPanel(new BorderLayout());
        JLabel archiveCountLabel = new JLabel("Selected: 0 models");
        archiveTableModel.addTableModelListener(e -> {
            int selected = 0;
            for (int i = 0; i < archiveTableModel.getRowCount(); i++) {
                if ((Boolean) archiveTableModel.getValueAt(i, 0)) selected++;
            }
            archiveCountLabel.setText("Selected: " + selected + " models");
        });

        JButton archiveNowBtn = new JButton("Move to Archive", SvgIconFactory.get(AppIcon.ARCHIVE));
        archiveNowBtn.putClientProperty("JButton.buttonType", "accent");
        archiveNowBtn.addActionListener(e -> {
            List<Integer> selectedRows = new ArrayList<>();
            for (int i = 0; i < archiveTableModel.getRowCount(); i++) {
                if ((Boolean) archiveTableModel.getValueAt(i, 0)) selectedRows.add(i);
            }
            if (selectedRows.isEmpty()) return;

            showModalProgressDialog("Archive", "Archiving", selectedRows, archiveTableModel, restoreTableModel, (folder, name, update) -> {
                try {
                    archiveService.moveToArchiveWithProgress(folder, name, update);
                    return true;
                } catch (IOException ex) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(dialog,
                                "Failed to archive '" + name + "':\n" + ex.getMessage(),
                                "Archive Error", JOptionPane.ERROR_MESSAGE);
                    });
                    return false;
                }
            });
        });

        JPanel archiveActionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        archiveActionPanel.add(archiveNowBtn);
        archiveBottom.add(archiveCountLabel, BorderLayout.WEST);
        archiveBottom.add(archiveActionPanel, BorderLayout.EAST);
        archivePanel.add(archiveBottom, BorderLayout.SOUTH);

        JPanel restorePanel = new JPanel(new BorderLayout(10, 10));
        restorePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JTable restoreTable = new JTable(restoreTableModel);
        restoreTable.setRowHeight(25);
        restoreTable.getColumnModel().getColumn(0).setMaxWidth(50);

        javax.swing.table.TableRowSorter<DefaultTableModel> restoreSorter = new javax.swing.table.TableRowSorter<>(restoreTableModel);
        restoreSorter.setComparator(3, (s1, s2) -> Long.compare(parseSizeToBytes((String) s1), parseSizeToBytes((String) s2)));
        restoreSorter.setSortable(0, false);
        restoreTable.setRowSorter(restoreSorter);

        restoreTable.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewIdx = restoreTable.columnAtPoint(e.getPoint());
                if (viewIdx == -1) return;
                int modelIdx = restoreTable.convertColumnIndexToModel(viewIdx);
                if (modelIdx == 0) {
                    boolean allSelected = true;
                    for (int i = 0; i < restoreTableModel.getRowCount(); i++) {
                        if (!(Boolean) restoreTableModel.getValueAt(i, 0)) {
                            allSelected = false;
                            break;
                        }
                    }
                    boolean newValue = !allSelected;
                    for (int i = 0; i < restoreTableModel.getRowCount(); i++) {
                        restoreTableModel.setValueAt(newValue, i, 0);
                    }
                }
            }
        });

        JScrollPane restoreScroll = new JScrollPane(restoreTable);
        restorePanel.add(restoreScroll, BorderLayout.CENTER);

        JPanel restoreBottom = new JPanel(new BorderLayout());
        JLabel restoreCountLabel = new JLabel("Selected: 0 models");
        restoreTableModel.addTableModelListener(e -> {
            int selected = 0;
            for (int i = 0; i < restoreTableModel.getRowCount(); i++) {
                if ((Boolean) restoreTableModel.getValueAt(i, 0)) selected++;
            }
            restoreCountLabel.setText("Selected: " + selected + " models");
        });

        JButton restoreNowBtn = new JButton("Restore from Archive", SvgIconFactory.get(AppIcon.LAUNCH));
        restoreNowBtn.putClientProperty("JButton.buttonType", "accent");
        restoreNowBtn.addActionListener(e -> {
            List<Integer> selectedRows = new ArrayList<>();
            for (int i = 0; i < restoreTableModel.getRowCount(); i++) {
                if ((Boolean) restoreTableModel.getValueAt(i, 0)) selectedRows.add(i);
            }
            if (selectedRows.isEmpty()) return;

            showModalProgressDialog("Restore", "Restoring", selectedRows, restoreTableModel, archiveTableModel,
                    archiveService::restoreFromArchiveWithProgress);
        });

        JPanel restoreActionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        restoreActionPanel.add(restoreNowBtn);
        restoreBottom.add(restoreCountLabel, BorderLayout.WEST);
        restoreBottom.add(restoreActionPanel, BorderLayout.EAST);
        restorePanel.add(restoreBottom, BorderLayout.SOUTH);

        tabs.addTab("Archive Models", SvgIconFactory.get(AppIcon.ARCHIVE), archivePanel);
        tabs.addTab("Restore Models", SvgIconFactory.get(AppIcon.LAUNCH), restorePanel);

        dialog.add(tabs, BorderLayout.CENTER);

        JPanel dialogButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dialog.dispose());
        dialogButtons.add(closeBtn);
        dialog.add(dialogButtons, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    private void showModalProgressDialog(String title, String statusPrefix, List<Integer> selectedRows,
                                         DefaultTableModel sourceModel, DefaultTableModel targetModel, TaskExecutor executor) {
        JDialog progressDialog = new JDialog((JFrame) SwingUtilities.getWindowAncestor(view), title, true);
        progressDialog.setLayout(new BorderLayout(10, 10));
        progressDialog.setSize(500, 180);
        progressDialog.setLocationRelativeTo(view);
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel label = new JLabel(statusPrefix + "...");
        label.setFont(new Font("SansSerif", Font.BOLD, 12));
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(400, 30));

        panel.add(label, BorderLayout.NORTH);
        panel.add(progressBar, BorderLayout.CENTER);
        progressDialog.add(panel, BorderLayout.CENTER);

        List<ArchiveWorkItem> workItems = new ArrayList<>();
        long totalBytes = 0;
        for (int row : selectedRows) {
            String sizeStr = (String) sourceModel.getValueAt(row, 3);
            workItems.add(new ArchiveWorkItem(row, (String) sourceModel.getValueAt(row, 1), (String) sourceModel.getValueAt(row, 2), sizeStr));
            totalBytes += parseSizeToBytes(sizeStr);
        }
        final long finalTotalBytes = totalBytes > 0 ? totalBytes : 1;

        backgroundExecutor.execute(() -> {
            int successCount = 0;
            List<Object[]> rowsToMove = new ArrayList<>();
            List<Integer> processedRowIndices = new ArrayList<>();
            long[] bytesProcessedTotal = {0};

            for (int i = 0; i < workItems.size(); i++) {
                ArchiveWorkItem item = workItems.get(i);
                final int fileIndex = i + 1;
                SwingUtilities.invokeLater(() -> label.setText(statusPrefix + " (" + fileIndex + "/" + workItems.size() + "): " + item.name()));

                if (executor.execute(item.folder(), item.name(), (bytesDelta) -> {
                    bytesProcessedTotal[0] += bytesDelta;
                    final long current = bytesProcessedTotal[0];
                    final int percent = (int) Math.min(100, (current * 100) / finalTotalBytes);
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(percent);
                        progressBar.setString(percent + "% (" + searchService.formatSize(current) + " / " + searchService.formatSize(finalTotalBytes) + ")");
                    });
                })) {
                    successCount++;
                    processedRowIndices.add(item.index());
                    rowsToMove.add(new Object[]{false, item.folder(), item.name(), item.size()});
                }
            }

            final int finalSuccess = successCount;
            SwingUtilities.invokeLater(() -> {
                progressDialog.dispose();
                processedRowIndices.sort((a, b) -> b.compareTo(a));
                for (int r : processedRowIndices) sourceModel.removeRow(r);
                for (Object[] rowData : rowsToMove) targetModel.addRow(rowData);

                if (analysisRefreshCallback != null) {
                    analysisRefreshCallback.run();
                }
                if (postOperationCallback != null) {
                    postOperationCallback.run();
                }

                JOptionPane.showMessageDialog(view, finalSuccess + " models successfully " + title.toLowerCase() + "ed.",
                        "Operation Complete", JOptionPane.INFORMATION_MESSAGE);
            });
        });

        progressDialog.setVisible(true);
    }

    public static long parseSizeToBytes(String sizeStr) {
        if (sizeStr == null || sizeStr.equals("Unknown") || sizeStr.isEmpty()) return 0;
        try {
            String[] parts = sizeStr.trim().split("\\s+");
            double val = Double.parseDouble(parts[0].replace(",", "."));
            if (parts.length < 2) return (long) val;
            String unit = parts[1].toUpperCase();
            if (unit.contains("GB")) return (long) (val * 1024 * 1024 * 1024);
            if (unit.contains("MB")) return (long) (val * 1024 * 1024);
            if (unit.contains("KB")) return (long) (val * 1024);
            return (long) val;
        } catch (Exception e) {
            return 0;
        }
    }

    @FunctionalInterface
    public interface TaskExecutor {
        boolean execute(String folder, String name, java.util.function.LongConsumer progressUpdate);
    }

    public record ArchiveWorkItem(int index, String folder, String name, String size) {
    }
}
