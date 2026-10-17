package de.tki.comfyuicompanion.ui.dialog;

import de.tki.comfyuicompanion.service.ICustomNodeSnapshotService;
import de.tki.comfyuicompanion.service.ICustomNodeSnapshotService.EnvironmentSnapshot;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Interactive dialog for managing environment snapshots and executing 1-click rollbacks
 * of custom nodes and Python packages.
 */
public class SnapshotRollbackDialog extends JDialog {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ICustomNodeSnapshotService snapshotService;
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JTextArea logArea;
    private final JCheckBox chkRestorePip;
    private final JButton btnRollback;
    private final JButton btnCreate;
    private final JButton btnDelete;

    private List<EnvironmentSnapshot> snapshots = new ArrayList<>();

    /**
     * Constructs a new SnapshotRollbackDialog using the default snapshot service.
     *
     * @param owner parent frame
     */
    public SnapshotRollbackDialog(Frame owner) {
        this(owner, new de.tki.comfyuicompanion.service.impl.CustomNodeSnapshotService());
    }

    /**
     * Constructs a new SnapshotRollbackDialog.
     *
     * @param owner parent frame
     * @param snapshotService service for capturing and restoring snapshots
     */
    public SnapshotRollbackDialog(Frame owner, ICustomNodeSnapshotService snapshotService) {
        super(owner, "Custom Node Snapshots & 1-Click Rollback", true);
        this.snapshotService = snapshotService != null ? snapshotService : new de.tki.comfyuicompanion.service.impl.CustomNodeSnapshotService();

        setSize(950, 620);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10, 10));

        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBorder(BorderFactory.createEmptyBorder(14, 16, 4, 16));

        JLabel titleLabel = new JLabel("Custom Node Snapshots & Rollback Protection");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        topPanel.add(titleLabel, BorderLayout.WEST);

        JPanel topActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        chkRestorePip = new JCheckBox("Restore pip packages (pip install -r freeze)", true);
        topActions.add(chkRestorePip);

        btnCreate = new JButton("Create Snapshot Now", SvgIconFactory.get(AppIcon.FILE_TEXT, 14));
        btnCreate.addActionListener(e -> triggerCreateSnapshot());
        topActions.add(btnCreate);

        btnRollback = new JButton("1-Click Rollback", SvgIconFactory.get(AppIcon.REFRESH, 14));
        btnRollback.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnRollback.setEnabled(false);
        btnRollback.addActionListener(e -> triggerRollback());
        topActions.add(btnRollback);

        btnDelete = new JButton("Delete", SvgIconFactory.get(AppIcon.DELETE, 14));
        btnDelete.setEnabled(false);
        btnDelete.addActionListener(e -> triggerDelete());
        topActions.add(btnDelete);

        topPanel.add(topActions, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        String[] cols = {"Date / Time", "Description", "Nodes Count", "Pip Freeze", "Snapshot ID"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        table = new JTable(tableModel);
        table.setRowHeight(26);
        table.getSelectionModel().addListSelectionListener(e -> {
            boolean hasSel = table.getSelectedRow() >= 0;
            btnRollback.setEnabled(hasSel);
            btnDelete.setEnabled(hasSel);
        });

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));

        logArea = new JTextArea(8, 20);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setText("Select a snapshot and click '1-Click Rollback' to restore custom nodes to a previous working state.\n");
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Rollback & Capture Console"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, logScroll);
        splitPane.setDividerLocation(300);
        splitPane.setBorder(BorderFactory.createEmptyBorder(0, 16, 10, 16));
        add(splitPane, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        JButton btnClose = new JButton("Close");
        btnClose.addActionListener(e -> dispose());
        footer.add(btnClose);
        add(footer, BorderLayout.SOUTH);

        refreshTable();
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        if (snapshotService == null) return;
        Thread.ofVirtual().name("snapshot-list").start(() -> {
            List<EnvironmentSnapshot> list = snapshotService.listSnapshots();
            SwingUtilities.invokeLater(() -> {
                this.snapshots = list;
                for (EnvironmentSnapshot s : list) {
                    tableModel.addRow(new Object[]{
                            TIME_FORMAT.format(s.timestamp()),
                            s.description(),
                            s.nodes().size() + " custom nodes",
                            s.hasPipFreeze() ? "✅ Saved" : "❌ None",
                            s.id()
                    });
                }
                btnRollback.setEnabled(false);
                btnDelete.setEnabled(false);
            });
        });
    }

    private void triggerCreateSnapshot() {
        String desc = JOptionPane.showInputDialog(this, "Enter a description for this snapshot:", "Create Snapshot", JOptionPane.PLAIN_MESSAGE);
        if (desc == null || desc.isBlank()) desc = "Manual user snapshot";

        final String finalDesc = desc;
        setButtonsEnabled(false);
        logArea.append("\n📸 Starting snapshot capture...\n");

        Thread.ofVirtual().name("snapshot-create").start(() -> {
            try {
                snapshotService.captureSnapshot(finalDesc, msg -> SwingUtilities.invokeLater(() -> {
                    logArea.append(msg);
                    logArea.setCaretPosition(logArea.getDocument().getLength());
                }));
            } finally {
                SwingUtilities.invokeLater(() -> {
                    setButtonsEnabled(true);
                    refreshTable();
                });
            }
        });
    }

    private void triggerRollback() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= snapshots.size()) return;
        EnvironmentSnapshot sel = snapshots.get(row);

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to rollback your custom nodes to snapshot:\n"
                        + sel.id() + " (" + TIME_FORMAT.format(sel.timestamp()) + ")?\n\n"
                        + "This will checkout " + sel.nodes().size() + " repositories to their exact previous commits.",
                "Confirm 1-Click Rollback",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirm != JOptionPane.YES_OPTION) return;

        setButtonsEnabled(false);
        boolean restorePip = chkRestorePip.isSelected();
        logArea.append("\n========================================\n");
        logArea.append("Initiating Rollback to: " + sel.id() + "\n");
        logArea.append("========================================\n");

        Thread.ofVirtual().name("snapshot-rollback").start(() -> {
            try {
                var res = snapshotService.rollbackToSnapshot(sel.id(), restorePip, msg -> SwingUtilities.invokeLater(() -> {
                    logArea.append(msg);
                    logArea.setCaretPosition(logArea.getDocument().getLength());
                }));
                SwingUtilities.invokeLater(() -> {
                    if (res.success()) {
                        JOptionPane.showMessageDialog(this, "Rollback completed successfully!\nRestored " + res.nodesRestored() + " custom nodes.", "Rollback Success", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this, "Rollback finished with warnings/errors. Please inspect the log console.", "Rollback Warning", JOptionPane.WARNING_MESSAGE);
                    }
                });
            } finally {
                SwingUtilities.invokeLater(() -> setButtonsEnabled(true));
            }
        });
    }

    private void triggerDelete() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= snapshots.size()) return;
        EnvironmentSnapshot sel = snapshots.get(row);

        int confirm = JOptionPane.showConfirmDialog(this, "Delete snapshot '" + sel.id() + "'?", "Delete Snapshot", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            snapshotService.deleteSnapshot(sel.id());
            refreshTable();
        }
    }

    private void setButtonsEnabled(boolean enabled) {
        btnCreate.setEnabled(enabled);
        btnRollback.setEnabled(enabled && table.getSelectedRow() >= 0);
        btnDelete.setEnabled(enabled && table.getSelectedRow() >= 0);
    }
}
