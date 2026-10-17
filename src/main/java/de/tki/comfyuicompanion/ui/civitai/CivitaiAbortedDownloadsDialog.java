package de.tki.comfyuicompanion.ui.civitai;

import de.tki.comfyuicompanion.domain.AbortedDownloadItem;
import de.tki.comfyuicompanion.service.IAbortedDownloadService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Enterprise modal dialog providing inspection, disk space reclamation, and resumption
 * for interrupted or orphaned model downloads across ComfyUI model directories.
 */
public class CivitaiAbortedDownloadsDialog extends JDialog {

    private final IAbortedDownloadService abortedDownloadService;
    private final ConfigService configService;
    private final Runnable onCleanupCompleted;

    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JLabel summaryLabel = new JLabel("Scanning for incomplete downloads...");
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton btnResume;
    private final JButton btnDeleteSelected;
    private final JButton btnCleanAll;
    private final JButton btnRefresh;

    private List<AbortedDownloadItem> currentItems = new ArrayList<>();

    /**
     * Constructs and initializes the CivitaiAbortedDownloadsDialog.
     *
     * @param owner                  parent window owner
     * @param abortedDownloadService service managing aborted download scanning and deletion
     * @param configService          configuration service for theme and paths
     * @param onCleanupCompleted     optional callback invoked when cleanup operations finish
     */
    public CivitaiAbortedDownloadsDialog(
            Window owner,
            IAbortedDownloadService abortedDownloadService,
            ConfigService configService,
            Runnable onCleanupCompleted) {
        super(owner, "Incomplete Downloads & Storage Optimizer", ModalityType.APPLICATION_MODAL);
        this.abortedDownloadService = abortedDownloadService;
        this.configService = configService;
        this.onCleanupCompleted = onCleanupCompleted;

        setSize(880, 540);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(0, 10));

        boolean darkMode = configService == null || configService.isDarkMode();
        Color bg = darkMode ? new Color(24, 26, 34) : new Color(248, 249, 252);
        Color textPrimary = darkMode ? new Color(230, 235, 245) : new Color(30, 35, 45);
        Color textSecondary = darkMode ? new Color(150, 158, 175) : new Color(100, 110, 125);
        getContentPane().setBackground(bg);

        // Header Panel
        JPanel headerPanel = new JPanel(new BorderLayout(10, 4));
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(16, 20, 8, 20));

        JLabel titleLbl = new JLabel("Incomplete Downloads & Storage Optimizer");
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 17));
        titleLbl.setForeground(textPrimary);
        headerPanel.add(titleLbl, BorderLayout.NORTH);

        JLabel descLbl = new JLabel("<html>Interrupted or canceled downloads (.cmfd, .part, .tmp) can hold gigabytes of disk space.<br/>" +
                "You can safely resume unfinished models or delete residual fragments to reclaim storage.</html>");
        descLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        descLbl.setForeground(textSecondary);
        headerPanel.add(descLbl, BorderLayout.CENTER);

        summaryLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        summaryLabel.setForeground(new Color(245, 158, 11)); // Amber highlight
        headerPanel.add(summaryLabel, BorderLayout.SOUTH);
        add(headerPanel, BorderLayout.NORTH);

        // Table Panel
        String[] columns = {"Select", "Model Target File", "Category Folder", "Partial Size", "Fragment Type", "Last Modified"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 0 ? Boolean.class : String.class;
            }

            @Override
            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return columnIndex == 0;
            }
        };

        table = new JTable(tableModel);
        table.setRowHeight(26);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFont(new Font("SansSerif", Font.PLAIN, 12));
        table.getColumnModel().getColumn(0).setPreferredWidth(60);
        table.getColumnModel().getColumn(1).setPreferredWidth(260);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.getColumnModel().getColumn(3).setPreferredWidth(100);
        table.getColumnModel().getColumn(4).setPreferredWidth(110);
        table.getColumnModel().getColumn(5).setPreferredWidth(140);

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean isSel, boolean hasFoc, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, val, isSel, hasFoc, row, col);
                c.setBackground(row % 2 == 0 ? bg : new Color(Math.max(0, bg.getRed() - 5), Math.max(0, bg.getGreen() - 5), Math.max(0, bg.getBlue() - 5)));
                c.setForeground(textPrimary);
                setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
                new EmptyBorder(0, 20, 0, 20),
                BorderFactory.createLineBorder(darkMode ? new Color(50, 54, 68) : new Color(210, 215, 225))
        ));
        add(scrollPane, BorderLayout.CENTER);

        // Footer Actions Panel
        JPanel footer = new JPanel();
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(8, 20, 14, 20));

        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setForeground(textSecondary);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        footer.add(statusLabel);
        footer.add(Box.createVerticalStrut(8));

        JPanel buttonBar = new JPanel(new BorderLayout(10, 0));
        buttonBar.setOpaque(false);
        buttonBar.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftActions.setOpaque(false);
        JButton btnSelectAll = new JButton("Select All");
        btnSelectAll.addActionListener(e -> setAllSelection(true));
        JButton btnDeselectAll = new JButton("Deselect All");
        btnDeselectAll.addActionListener(e -> setAllSelection(false));
        btnRefresh = new JButton("Refresh", SvgIconFactory.get(AppIcon.REFRESH, 14));
        btnRefresh.addActionListener(e -> refreshData());

        leftActions.add(btnSelectAll);
        leftActions.add(btnDeselectAll);
        leftActions.add(btnRefresh);
        buttonBar.add(leftActions, BorderLayout.WEST);

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightActions.setOpaque(false);

        btnResume = new JButton("Resume Selected", SvgIconFactory.get(AppIcon.PLAY, 14));
        btnResume.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnResume.addActionListener(e -> handleResumeSelected());

        btnDeleteSelected = new JButton("Delete Selected", SvgIconFactory.get(AppIcon.TRASH, 14));
        btnDeleteSelected.setFont(new Font("SansSerif", Font.PLAIN, 12));
        btnDeleteSelected.addActionListener(e -> handleDeleteSelected());

        btnCleanAll = new JButton("Clean All", SvgIconFactory.get(AppIcon.DELETE, 14));
        btnCleanAll.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnCleanAll.addActionListener(e -> handleCleanAll());

        JButton btnClose = new JButton("Close");
        btnClose.addActionListener(e -> dispose());

        rightActions.add(btnResume);
        rightActions.add(btnDeleteSelected);
        rightActions.add(btnCleanAll);
        rightActions.add(btnClose);
        buttonBar.add(rightActions, BorderLayout.EAST);

        footer.add(buttonBar);
        add(footer, BorderLayout.SOUTH);

        refreshData();
    }

    /**
     * Refreshes the scanned incomplete download items asynchronously on a virtual thread.
     */
    public void refreshData() {
        btnRefresh.setEnabled(false);
        statusLabel.setText("Scanning model directories for partial download artifacts...");
        Thread.ofVirtual().name("AbortedDownloads-ScanWorker").start(() -> {
            List<AbortedDownloadItem> items = abortedDownloadService.scanAbortedDownloads(null);
            SwingUtilities.invokeLater(() -> {
                this.currentItems = items;
                tableModel.setRowCount(0);
                long totalBytes = 0;
                for (AbortedDownloadItem item : items) {
                    tableModel.addRow(new Object[]{
                            false,
                            item.targetFileName(),
                            item.folderName(),
                            item.getFormattedSize(),
                            item.fileExtensionType(),
                            item.getFormattedDate()
                    });
                    totalBytes += item.sizeBytes();
                }

                if (items.isEmpty()) {
                    summaryLabel.setText("✅ No incomplete or aborted downloads found. Model storage is clean.");
                    summaryLabel.setForeground(new Color(34, 197, 130));
                    btnResume.setEnabled(false);
                    btnDeleteSelected.setEnabled(false);
                    btnCleanAll.setEnabled(false);
                } else {
                    String totalFormatted = formatBytes(totalBytes);
                    summaryLabel.setText(String.format(Locale.US,
                            "Found %d incomplete download(s) occupying %s of reclaimable disk space.",
                            items.size(), totalFormatted));
                    summaryLabel.setForeground(new Color(245, 158, 11));
                    btnResume.setEnabled(true);
                    btnDeleteSelected.setEnabled(true);
                    btnCleanAll.setEnabled(true);
                }
                statusLabel.setText("Scan completed.");
                btnRefresh.setEnabled(true);
            });
        });
    }

    private void handleResumeSelected() {
        List<AbortedDownloadItem> selected = getSelectedItems();
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select at least one incomplete model to resume.", "Information", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        AbortedDownloadItem target = selected.get(0);
        btnResume.setEnabled(false);
        statusLabel.setText("Initiating resumption for " + target.targetFileName() + "...");

        abortedDownloadService.resumeDownload(target, status -> SwingUtilities.invokeLater(() -> statusLabel.setText(status)))
                .thenAccept(success -> SwingUtilities.invokeLater(() -> {
                    btnResume.setEnabled(true);
                    if (success) {
                        JOptionPane.showMessageDialog(this, "Download completed for: " + target.targetFileName(), "Download Finished", JOptionPane.INFORMATION_MESSAGE);
                        refreshData();
                        if (onCleanupCompleted != null) onCleanupCompleted.run();
                    }
                }));
    }

    private void handleDeleteSelected() {
        List<AbortedDownloadItem> selected = getSelectedItems();
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select the items you wish to delete.", "Information", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        long sumBytes = selected.stream().mapToLong(AbortedDownloadItem::sizeBytes).sum();
        int confirm = JOptionPane.showConfirmDialog(
                this,
                String.format(Locale.US, "Are you sure you want to permanently delete %d partial download file(s)?\nThis will reclaim %s of storage space.",
                        selected.size(), formatBytes(sumBytes)),
                "Confirm Deletion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) return;

        long freed = abortedDownloadService.deleteAbortedDownloads(selected);
        statusLabel.setText(String.format(Locale.US, "Deleted %d item(s). Reclaimed %s!", selected.size(), formatBytes(freed)));
        refreshData();
        if (onCleanupCompleted != null) onCleanupCompleted.run();
    }

    private void handleCleanAll() {
        if (currentItems.isEmpty()) return;

        long sumBytes = currentItems.stream().mapToLong(AbortedDownloadItem::sizeBytes).sum();
        int confirm = JOptionPane.showConfirmDialog(
                this,
                String.format(Locale.US, "Are you sure you want to clean ALL %d incomplete download(s)?\nTotal disk space to be reclaimed: %s",
                        currentItems.size(), formatBytes(sumBytes)),
                "Confirm Clean All",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) return;

        long freed = abortedDownloadService.deleteAbortedDownloads(currentItems);
        statusLabel.setText(String.format(Locale.US, "Storage cleanup finished. Reclaimed %s!", formatBytes(freed)));
        refreshData();
        if (onCleanupCompleted != null) onCleanupCompleted.run();
    }

    private List<AbortedDownloadItem> getSelectedItems() {
        List<AbortedDownloadItem> selected = new ArrayList<>();
        for (int r = 0; r < tableModel.getRowCount(); r++) {
            Boolean checked = (Boolean) tableModel.getValueAt(r, 0);
            if (Boolean.TRUE.equals(checked) && r < currentItems.size()) {
                selected.add(currentItems.get(r));
            }
        }
        return selected;
    }

    private void setAllSelection(boolean select) {
        for (int r = 0; r < tableModel.getRowCount(); r++) {
            tableModel.setValueAt(select, r, 0);
        }
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
