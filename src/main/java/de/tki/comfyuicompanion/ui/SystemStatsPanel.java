package de.tki.comfyuicompanion.ui;

import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import javax.swing.*;
import java.awt.*;

/**
 * Dedicated UI panel displaying live system hardware statistics (CPU, RAM, GPU, VRAM)
 * and quick maintenance action buttons.
 */
public class SystemStatsPanel extends JPanel {

    private final JProgressBar progressCpu = new JProgressBar(0, 100);
    private final JProgressBar progressRam = new JProgressBar(0, 100);
    private final JProgressBar progressGpu = new JProgressBar(0, 100);
    private final JProgressBar progressVram = new JProgressBar(0, 100);
    private final JLabel lblGpuName = new JLabel("GPU: N/A");
    private final JPanel gpuPanel = new JPanel(new GridBagLayout());
    private DashboardView.DashboardListener listener;

    public SystemStatsPanel(DashboardView.DashboardListener listener) {
        this.listener = listener;
        setLayout(new GridBagLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        setPreferredSize(new Dimension(300, 0));

        GridBagConstraints eastGbc = new GridBagConstraints();
        eastGbc.fill = GridBagConstraints.HORIZONTAL;
        eastGbc.weightx = 1.0;
        eastGbc.gridx = 0;
        eastGbc.gridy = 0;
        eastGbc.insets = new Insets(5, 5, 10, 5);

        JLabel statsHeader = new JLabel("System Stats");
        statsHeader.putClientProperty("FlatLaf.styleClass", "h3");
        add(statsHeader, eastGbc);

        eastGbc.gridy++;
        eastGbc.insets = new Insets(4, 5, 6, 5);
        add(createSlimStatPanel("CPU Usage", progressCpu), eastGbc);

        eastGbc.gridy++;
        add(createSlimStatPanel("RAM Usage", progressRam), eastGbc);

        // GPU Box
        eastGbc.gridy++;
        eastGbc.insets = new Insets(10, 5, 5, 5);
        gpuPanel.setOpaque(false);

        GridBagConstraints gpuGbc = new GridBagConstraints();
        gpuGbc.fill = GridBagConstraints.HORIZONTAL;
        gpuGbc.weightx = 1.0;
        gpuGbc.gridx = 0;
        gpuGbc.gridy = 0;
        gpuGbc.insets = new Insets(5, 0, 8, 0);

        lblGpuName.setFont(new Font("SansSerif", Font.BOLD, 12));
        gpuPanel.add(lblGpuName, gpuGbc);

        gpuGbc.gridy++;
        gpuPanel.add(createSlimStatPanel("GPU Load", progressGpu), gpuGbc);

        gpuGbc.gridy++;
        gpuGbc.insets = new Insets(5, 0, 5, 0);
        gpuPanel.add(createSlimStatPanel("VRAM Usage", progressVram), gpuGbc);

        add(gpuPanel, eastGbc);

        // Separator
        eastGbc.gridy++;
        eastGbc.insets = new Insets(15, 5, 15, 5);
        add(new JSeparator(), eastGbc);

        // Quick Actions
        eastGbc.gridy++;
        eastGbc.insets = new Insets(5, 5, 15, 5);
        JLabel actionsHeader = new JLabel("Quick Actions");
        actionsHeader.putClientProperty("FlatLaf.styleClass", "h3");
        add(actionsHeader, eastGbc);

        eastGbc.gridy++;
        eastGbc.insets = new Insets(6, 5, 6, 5);
        JButton updateBtn = new JButton("Update ComfyUI & Nodes", SvgIconFactory.get(AppIcon.DOWNLOAD));
        updateBtn.putClientProperty("JButton.buttonType", "roundRect");
        updateBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        updateBtn.setPreferredSize(new Dimension(0, 35));
        updateBtn.addActionListener(e -> {
            if (listener != null) listener.onOpenAutoUpdater();
        });
        add(updateBtn, eastGbc);

        eastGbc.gridy++;
        JButton checkUpdatesBtn = new JButton("Check Model Upgrades", SvgIconFactory.get(AppIcon.SEARCH));
        checkUpdatesBtn.putClientProperty("JButton.buttonType", "roundRect");
        checkUpdatesBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        checkUpdatesBtn.setPreferredSize(new Dimension(0, 35));
        checkUpdatesBtn.addActionListener(e -> {
            if (listener != null) listener.onOpenModelUpdateChecker();
        });
        add(checkUpdatesBtn, eastGbc);

        eastGbc.gridy++;
        JButton storageOptBtn = new JButton("Storage Optimizer", SvgIconFactory.get(AppIcon.STORAGE_OPTIMIZER));
        storageOptBtn.putClientProperty("JButton.buttonType", "roundRect");
        storageOptBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        storageOptBtn.setPreferredSize(new Dimension(0, 35));
        storageOptBtn.addActionListener(e -> {
            if (listener != null) listener.onOpenStorageOptimizer();
        });
        add(storageOptBtn, eastGbc);

        eastGbc.gridy++;
        eastGbc.weighty = 1.0;
        eastGbc.fill = GridBagConstraints.BOTH;
        add(Box.createGlue(), eastGbc);
    }

    public static JPanel createSlimStatPanel(String title, JProgressBar bar) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);

        JLabel titleLbl = new JLabel(title) {
            @Override
            public void updateUI() {
                super.updateUI();
                Color c = UIManager.getColor("SlimStat.titleForeground");
                if (c != null) {
                    setForeground(c);
                }
            }
        };
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        Color tc = UIManager.getColor("SlimStat.titleForeground");
        if (tc != null) titleLbl.setForeground(tc);

        JLabel valLbl = new JLabel("--") {
            @Override
            public void updateUI() {
                super.updateUI();
                Color c = UIManager.getColor("SlimStat.valueForeground");
                if (c != null) {
                    setForeground(c);
                }
            }
        };
        valLbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        Color vc = UIManager.getColor("SlimStat.valueForeground");
        if (vc != null) valLbl.setForeground(vc);

        bar.putClientProperty("titleLabel", titleLbl);
        bar.putClientProperty("valueLabel", valLbl);

        JPanel labelRow = new JPanel(new BorderLayout());
        labelRow.setOpaque(false);
        labelRow.add(titleLbl, BorderLayout.WEST);
        labelRow.add(valLbl, BorderLayout.EAST);

        bar.setStringPainted(false);
        bar.setPreferredSize(new Dimension(0, 6));
        bar.putClientProperty("FlatLaf.style", "arc: 999; foreground: $SlimStat.barForeground; background: $SlimStat.barBackground;");

        p.add(labelRow, BorderLayout.NORTH);
        p.add(bar, BorderLayout.CENTER);
        return p;
    }

    public void setListener(DashboardView.DashboardListener listener) {
        this.listener = listener;
    }

    public JProgressBar getProgressCpu() { return progressCpu; }
    public JProgressBar getProgressRam() { return progressRam; }
    public JProgressBar getProgressGpu() { return progressGpu; }
    public JProgressBar getProgressVram() { return progressVram; }
    public JLabel getLblGpuName() { return lblGpuName; }
    public JPanel getGpuPanel() { return gpuPanel; }
}
