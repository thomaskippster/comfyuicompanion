package de.tki.comfymodels.ui;

import de.tki.comfymodels.service.impl.UpdaterService;
import de.tki.comfymodels.service.impl.UpdaterService.RepoStatus;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AutoUpdaterDialog extends JDialog {
    private final UpdaterService updaterService;
    private final JTable table;
    private final DefaultTableModel tableModel;
    private final JTextArea logArea;
    private final JButton scanBtn;
    private final JButton updateBtn;
    private List<RepoStatus> repoList = new ArrayList<>();

    public AutoUpdaterDialog(Frame owner, UpdaterService updaterService) {
        super(owner, "ComfyUI & Custom Nodes Auto-Updater", true);
        this.updaterService = updaterService;

        setSize(1000, 650);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(15, 15));

        // Top Action Bar
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 0, 15));

        JLabel titleLabel = new JLabel("Manage Updates for ComfyUI and Custom Nodes");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        topPanel.add(titleLabel, BorderLayout.WEST);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        scanBtn = new JButton("🔍 Scan for Updates");
        updateBtn = new JButton("📥 Update Selected");
        updateBtn.setEnabled(false);
        btnPanel.add(scanBtn);
        btnPanel.add(updateBtn);
        topPanel.add(btnPanel, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        // Center Panel: Table (top) and Console Log (bottom)
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setDividerLocation(250);
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
        table.getColumnModel().getColumn(5).setPreferredWidth(150);

        JScrollPane tableScroll = new JScrollPane(table);
        splitPane.setTopComponent(tableScroll);

        // Log Console
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setBackground(new Color(25, 25, 25));
        logArea.setForeground(new Color(220, 220, 220));
        logArea.setMargin(new Insets(10, 10, 10, 10));
        
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Console Log"));
        splitPane.setBottomComponent(logScroll);

        add(splitPane, BorderLayout.CENTER);

        logArea.setText("Press 'Scan for Updates' to fetch current git repository states.\n");

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
            } catch (Exception ex) {
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
        logArea.setText("📥 Starting update process...\n");

        new Thread(() -> {
            try {
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
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    logArea.append("❌ Update process error: " + ex.getMessage() + "\n");
                    scanBtn.setEnabled(true);
                    updateBtn.setEnabled(true);
                });
            }
        }).start();
    }
}
