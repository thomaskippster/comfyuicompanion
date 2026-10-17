package de.tki.comfyuicompanion.ui;

import de.tki.comfyuicompanion.service.ICustomNodeSnapshotService;
import de.tki.comfyuicompanion.service.ICustomNodeSnapshotService.EnvironmentSnapshot;
import de.tki.comfyuicompanion.service.impl.CustomNodeSnapshotService;
import de.tki.comfyuicompanion.service.impl.UpdaterService;
import de.tki.comfyuicompanion.service.impl.UpdaterService.RepoStatus;
import de.tki.comfyuicompanion.ui.dialog.SnapshotRollbackDialog;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Enterprise Dialog for managing automatic updates of ComfyUI and installed Custom Nodes.
 * Automatically takes a safety snapshot of all Git commits and Python dependencies before updating,
 * and provides a 1-click rollback dialog to restore previous known-good working states.
 */
public class AutoUpdaterDialog extends JDialog {
    private final UpdaterService updaterService;
    private final ICustomNodeSnapshotService snapshotService;
    private final JTable table;
    private final DefaultTableModel tableModel;
    private final JTextArea logArea;
    private final JButton scanBtn;
    private final JButton updateBtn;
    private final JButton rollbackBtn;
    private List<RepoStatus> repoList = new ArrayList<>();

    /**
     * Constructs a new AutoUpdaterDialog with a default snapshot service.
     *
     * @param owner the owner frame
     * @param updaterService the service for updates
     */
    public AutoUpdaterDialog(Frame owner, UpdaterService updaterService) {
        this(owner, updaterService, new CustomNodeSnapshotService(updaterService != null ? updaterService.getConfigService() : null));
    }

    /**
     * Constructs a new AutoUpdaterDialog with an explicit snapshot service.
     *
     * @param owner the owner frame
     * @param updaterService the service for updates
     * @param snapshotService the service for environment snapshots and rollbacks
     */
    public AutoUpdaterDialog(Frame owner, UpdaterService updaterService, ICustomNodeSnapshotService snapshotService) {
        super(owner, "ComfyUI & Custom Nodes Auto-Updater", true);
        this.updaterService = updaterService;
        this.snapshotService = snapshotService != null ? snapshotService : new CustomNodeSnapshotService(updaterService != null ? updaterService.getConfigService() : null);

        setSize(1020, 680);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(15, 15));

        // Top Action Bar
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 0, 15));

        JLabel titleLabel = new JLabel("Manage Updates for ComfyUI and Custom Nodes");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        topPanel.add(titleLabel, BorderLayout.WEST);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rollbackBtn = new JButton("Snapshots & Rollback", SvgIconFactory.get(AppIcon.REFRESH));
        rollbackBtn.setToolTipText("View previous environment snapshots and perform 1-click rollbacks");
        rollbackBtn.addActionListener(e -> new SnapshotRollbackDialog(owner, this.snapshotService).setVisible(true));

        scanBtn = new JButton("Scan for Updates", SvgIconFactory.get(AppIcon.SEARCH));
        updateBtn = new JButton("Update Selected", SvgIconFactory.get(AppIcon.DOWNLOAD));
        updateBtn.setEnabled(false);

        btnPanel.add(rollbackBtn);
        btnPanel.add(scanBtn);
        btnPanel.add(updateBtn);
        topPanel.add(btnPanel, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        // Center Panel: Table (top) and Console Log (bottom)
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setDividerLocation(260);
        splitPane.setBorder(BorderFactory.createEmptyBorder(0, 15, 15, 15));

        // Table
        String[] columns = {"Select", "Repository", "Branch", "Current Commit", "Remote Commit", "Status"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 0 ? Boolean.class : String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }
        };

        table = new JTable(tableModel);
        table.setRowHeight(28);
        table.setFont(new Font("SansSerif", Font.PLAIN, 13));
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(1).setPreferredWidth(250);
        table.getColumnModel().getColumn(5).setPreferredWidth(160);

        JScrollPane tableScroll = new JScrollPane(table);
        splitPane.setTopComponent(tableScroll);

        // Log Console
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        Color bg = UIManager.getColor("TextArea.background");
        Color fg = UIManager.getColor("TextArea.foreground");
        if (bg != null) logArea.setBackground(bg);
        if (fg != null) logArea.setForeground(fg);
        logArea.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Console Log"));
        splitPane.setBottomComponent(logScroll);

        add(splitPane, BorderLayout.CENTER);

        logArea.setText("Press 'Scan for Updates' to fetch current git repository states.\n"
                + "Safety snapshots are automatically captured before applying updates.\n");

        scanBtn.addActionListener(e -> startScan());
        updateBtn.addActionListener(e -> runUpdates());
    }

    private void startScan() {
        scanBtn.setEnabled(false);
        updateBtn.setEnabled(false);
        tableModel.setRowCount(0);
        repoList.clear();
        logArea.setText("🔍 Scanning repositories...\n");

        new Thread(() -> {
            try {
                List<RepoStatus> repos = updaterService.scanRepositories();
                SwingUtilities.invokeLater(() -> {
                    repoList = repos;
                    for (RepoStatus r : repos) {
                        String statusStr = "Up-to-date";
                        if (r.error != null) {
                            statusStr = "⚠️ " + r.error;
                        } else if (r.updateAvailable) {
                            statusStr = "📥 Update Available (" + r.behindCount + " behind)";
                        }

                        tableModel.addRow(new Object[]{
                                r.updateAvailable, // Checkbox checked if updates are available
                                r.name,
                                r.currentBranch,
                                r.currentCommit,
                                r.remoteCommit,
                                statusStr
                        });
                    }
                    logArea.append("✅ Scan finished. Found " + repos.size() + " repositories.\n");
                    scanBtn.setEnabled(true);
                    updateBtn.setEnabled(!repos.isEmpty());
                });
            } catch (RuntimeException ex) {
                SwingUtilities.invokeLater(() -> {
                    logArea.append("❌ Scan failed: " + ex.getMessage() + "\n");
                    scanBtn.setEnabled(true);
                });
            }
        }).start();
    }

    private void runUpdates() {
        List<RepoStatus> toUpdate = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            Boolean selected = (Boolean) tableModel.getValueAt(i, 0);
            if (selected != null && selected) {
                toUpdate.add(repoList.get(i));
            }
        }

        if (toUpdate.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select at least one repository to update.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        scanBtn.setEnabled(false);
        updateBtn.setEnabled(false);
        rollbackBtn.setEnabled(false);
        logArea.setText("📥 Preparing update process...\n");

        new Thread(() -> {
            try {
                // Auto safety snapshot before updating
                if (snapshotService != null) {
                    SwingUtilities.invokeLater(() -> logArea.append("📸 Creating safety snapshot of custom nodes and Python environment...\n"));
                    try {
                        EnvironmentSnapshot snap = snapshotService.captureSnapshot(
                                "Auto-snapshot before updating " + toUpdate.size() + " repositories",
                                msg -> SwingUtilities.invokeLater(() -> {
                                    logArea.append(msg + "\n");
                                    logArea.setCaretPosition(logArea.getDocument().getLength());
                                })
                        );
                        SwingUtilities.invokeLater(() -> logArea.append("✅ Safety snapshot saved: " + snap.id() + " (" + snap.nodes().size() + " nodes tracked)\n\n"));
                    } catch (Exception snapEx) {
                        SwingUtilities.invokeLater(() -> logArea.append("⚠️ Could not create pre-update snapshot: " + snapEx.getMessage() + " (continuing with update)\n\n"));
                    }
                }

                for (RepoStatus r : toUpdate) {
                    updaterService.updateRepository(r, msg -> SwingUtilities.invokeLater(() -> {
                        logArea.append(msg);
                        logArea.setCaretPosition(logArea.getDocument().getLength());
                    }));
                }
                SwingUtilities.invokeLater(() -> {
                    logArea.append("\n🎉 All updates finished! Restart ComfyUI if running to apply changes.\n");
                    scanBtn.setEnabled(true);
                    updateBtn.setEnabled(true);
                    rollbackBtn.setEnabled(true);
                });
            } catch (RuntimeException ex) {
                SwingUtilities.invokeLater(() -> {
                    logArea.append("❌ Update process error: " + ex.getMessage() + "\n");
                    scanBtn.setEnabled(true);
                    updateBtn.setEnabled(true);
                    rollbackBtn.setEnabled(true);
                });
            }
        }).start();
    }
}
