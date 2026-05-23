package de.tki.comfymodels.ui;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.IDownloadManager;
import de.tki.comfymodels.service.IModelValidator;
import de.tki.comfymodels.service.impl.CivitaiService;
import de.tki.comfymodels.service.impl.CivitaiService.ModelUpdateInfo;
import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.ModelHashRegistry;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ModelUpdateCheckerDialog extends JDialog {
    private final ConfigService configService;
    private final ModelHashRegistry hashRegistry;
    private final IModelValidator modelValidator;
    private final CivitaiService civitaiService;
    private final IDownloadManager downloadManager;

    private final JTable table;
    private final DefaultTableModel tableModel;
    private final JTextArea logArea;
    private final JButton scanBtn;
    private final JButton downloadBtn;
    private final List<ModelUpdateInfo> updatesList = new ArrayList<>();

    public ModelUpdateCheckerDialog(Frame owner, ConfigService configService, ModelHashRegistry hashRegistry,
                                     IModelValidator modelValidator, CivitaiService civitaiService, IDownloadManager downloadManager) {
        super(owner, "Civitai Model Version Tracker & Upgrade Alerts", true);
        this.configService = configService;
        this.hashRegistry = hashRegistry;
        this.modelValidator = modelValidator;
        this.civitaiService = civitaiService;
        this.downloadManager = downloadManager;

        setSize(1000, 600);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(15, 15));

        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 0, 15));

        JLabel titleLabel = new JLabel("Model Updates & Upgrade Checker");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        topPanel.add(titleLabel, BorderLayout.WEST);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        scanBtn = new JButton("🔍 Scan for Model Updates");
        downloadBtn = new JButton("📥 Download Selected Upgrades");
        downloadBtn.setEnabled(false);
        btnPanel.add(scanBtn);
        btnPanel.add(downloadBtn);
        topPanel.add(btnPanel, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setDividerLocation(250);
        splitPane.setBorder(BorderFactory.createEmptyBorder(0, 15, 15, 15));

        String[] columns = {"Select", "Model Name", "Local Filename", "Local Version", "New Version", "Status", "Type", "Download URL"};
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
        table.getColumnModel().getColumn(1).setPreferredWidth(180);
        table.getColumnModel().getColumn(2).setPreferredWidth(180);
        table.getColumnModel().getColumn(7).setPreferredWidth(0);
        table.getColumnModel().getColumn(7).setMinWidth(0);
        table.getColumnModel().getColumn(7).setMaxWidth(0); // Hide download URL column but keep it in data model

        JScrollPane tableScroll = new JScrollPane(table);
        splitPane.setTopComponent(tableScroll);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setBackground(new Color(25, 25, 25));
        logArea.setForeground(new Color(220, 220, 220));
        logArea.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Status Console"));
        splitPane.setBottomComponent(logScroll);

        add(splitPane, BorderLayout.CENTER);

        logArea.setText("Press 'Scan for Model Updates' to compare local model hashes against Civitai library.\n");

        scanBtn.addActionListener(e -> startScan());
        downloadBtn.addActionListener(e -> downloadUpgrades());
    }

    private void startScan() {
        scanBtn.setEnabled(false);
        downloadBtn.setEnabled(false);
        tableModel.setRowCount(0);
        updatesList.clear();
        logArea.setText("🔍 Starting model update scanning...\n");

        new Thread(() -> {
            try {
                String baseDir = configService.getModelsPath();
                if (baseDir == null || baseDir.isEmpty()) {
                    updateLog("❌ Models directory is not set in Settings.");
                    SwingUtilities.invokeLater(() -> scanBtn.setEnabled(true));
                    return;
                }

                File modelsRoot = new File(baseDir);
                if (!modelsRoot.exists() || !modelsRoot.isDirectory()) {
                    updateLog("❌ Models directory does not exist: " + baseDir);
                    SwingUtilities.invokeLater(() -> scanBtn.setEnabled(true));
                    return;
                }

                updateLog("📂 Searching model files...");
                List<File> files = new ArrayList<>();
                findModelFiles(modelsRoot, files);
                updateLog("Found " + files.size() + " model files. Checking version compatibility on Civitai...");

                int count = 0;
                for (File f : files) {
                    count++;
                    final int currentIdx = count;
                    final int total = files.size();

                    String hash = hashRegistry.getOrCalculateHash(f);
                    if (hash != null && hash.length() < 64) {
                        SwingUtilities.invokeLater(() -> logArea.append(String.format("[%d/%d] Calculating full SHA-256 for %s...\n", currentIdx, total, f.getName())));
                        hash = modelValidator.calculateFullSha256(f);
                    } else {
                        SwingUtilities.invokeLater(() -> logArea.append(String.format("[%d/%d] Checking %s...\n", currentIdx, total, f.getName())));
                    }

                    if (hash != null && hash.length() == 64) {
                        // Throttle requests slightly
                        Thread.sleep(300);
                        ModelUpdateInfo updateInfo = civitaiService.checkForUpdate(f, hash);
                        if (updateInfo != null && updateInfo.hasUpdate) {
                            String type = determineModelTypeFromFolder(f, modelsRoot);
                            SwingUtilities.invokeLater(() -> {
                                updatesList.add(updateInfo);
                                tableModel.addRow(new Object[]{
                                        true,
                                        updateInfo.modelName,
                                        updateInfo.localFileName,
                                        updateInfo.localVersionName,
                                        updateInfo.newVersionName,
                                        "📥 Update Available",
                                        type,
                                        updateInfo.downloadUrl
                                });
                            });
                        }
                    }
                }
                updateLog("✅ Completed scanning. Found " + tableModel.getRowCount() + " upgradable models.");
            } catch (Exception ex) {
                updateLog("❌ Scanning failed: " + ex.getMessage());
            } finally {
                SwingUtilities.invokeLater(() -> {
                    scanBtn.setEnabled(true);
                    downloadBtn.setEnabled(tableModel.getRowCount() > 0);
                });
            }
        }).start();
    }

    private void downloadUpgrades() {
        List<ModelInfo> modelsToDownload = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            Boolean selected = (Boolean) tableModel.getValueAt(i, 0);
            if (selected != null && selected) {
                String modelName = (String) tableModel.getValueAt(i, 1);
                String newVersion = (String) tableModel.getValueAt(i, 4);
                String type = (String) tableModel.getValueAt(i, 6);
                String downloadUrl = (String) tableModel.getValueAt(i, 7);

                ModelInfo info = new ModelInfo(type, modelName + " (" + newVersion + ")", "CIVITAI");
                info.setUrl(downloadUrl);
                info.setSave_path(type);
                modelsToDownload.add(info);
            }
        }

        if (modelsToDownload.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select at least one upgrade to download.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        scanBtn.setEnabled(false);
        downloadBtn.setEnabled(false);
        logArea.setText("📥 Enqueuing downloads in background...\n");

        new Thread(() -> {
            try {
                boolean[] selected = new boolean[modelsToDownload.size()];
                java.util.Arrays.fill(selected, true);

                downloadManager.startQueue(
                        modelsToDownload,
                        selected,
                        configService.getModelsPath(),
                        (idx, status) -> SwingUtilities.invokeLater(() -> {
                            updateLog(String.format("Download [%s]: %s", modelsToDownload.get(idx).getName(), status));
                        }),
                        () -> SwingUtilities.invokeLater(() -> {
                            updateLog("✅ All upgrades downloaded successfully!");
                            scanBtn.setEnabled(true);
                            downloadBtn.setEnabled(true);
                        })
                );
            } catch (Exception ex) {
                updateLog("❌ Failed to start downloads: " + ex.getMessage());
                SwingUtilities.invokeLater(() -> {
                    scanBtn.setEnabled(true);
                    downloadBtn.setEnabled(true);
                });
            }
        }).start();
    }

    private void findModelFiles(File dir, List<File> files) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                findModelFiles(child, files);
            } else {
                String name = child.getName().toLowerCase();
                if (name.endsWith(".safetensors") || name.endsWith(".ckpt") || name.endsWith(".sft") || name.endsWith(".pth") || name.endsWith(".pt")) {
                    files.add(child);
                }
            }
        }
    }

    private String determineModelTypeFromFolder(File file, File root) {
        try {
            String relative = root.toURI().relativize(file.getParentFile().toURI()).getPath();
            if (relative == null || relative.isEmpty()) return "checkpoints";
            String firstSegment = relative.split("/")[0];
            return firstSegment;
        } catch (Exception e) {
            return "checkpoints";
        }
    }

    private void updateLog(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}
