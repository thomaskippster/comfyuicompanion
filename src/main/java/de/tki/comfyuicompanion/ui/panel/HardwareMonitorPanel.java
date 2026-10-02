package de.tki.comfyuicompanion.ui.panel;

import de.tki.comfyuicompanion.service.impl.HardwareMonitorService;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;

/**
 * Panel displaying real-time system hardware statistics (CPU, RAM, GPU, VRAM)
 * and providing integration with {@link HardwareMonitorService}.
 */
public class HardwareMonitorPanel extends JPanel {

    private final JProgressBar progressCpu = new JProgressBar(0, 100);
    private final JProgressBar progressRam = new JProgressBar(0, 100);
    private final JProgressBar progressGpu = new JProgressBar(0, 100);
    private final JProgressBar progressVram = new JProgressBar(0, 100);
    private final JLabel lblGpuName = new JLabel("GPU: N/A");
    private final JPanel gpuPanel = new JPanel(new GridBagLayout());

    private HardwareMonitorService hardwareMonitorService;

    /**
     * Constructs a new HardwareMonitorPanel without an attached monitor service.
     */
    public HardwareMonitorPanel() {
        this(null);
    }

    /**
     * Constructs a new HardwareMonitorPanel with the given monitor service.
     *
     * @param hardwareMonitorService the hardware monitor service to poll
     */
    public HardwareMonitorPanel(HardwareMonitorService hardwareMonitorService) {
        this.hardwareMonitorService = hardwareMonitorService;
        initUI();
    }

    private void initUI() {
        setLayout(new GridBagLayout());
        setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 12, 0);

        JLabel statsHeader = new JLabel("System Stats");
        statsHeader.putClientProperty("FlatLaf.styleClass", "h3");
        add(statsHeader, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(4, 0, 8, 0);
        add(createSlimStatPanel("CPU Load", progressCpu), gbc);

        gbc.gridy++;
        add(createSlimStatPanel("RAM Usage", progressRam), gbc);

        gbc.gridy++;
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

        add(gpuPanel, gbc);
    }

    /**
     * Starts hardware monitoring updates from the attached service.
     */
    public void startMonitoring() {
        if (hardwareMonitorService != null) {
            hardwareMonitorService.start(stats -> SwingUtilities.invokeLater(() -> updateHardwareUI(stats)));
        }
    }

    /**
     * Stops hardware monitoring updates.
     */
    public void stopMonitoring() {
        if (hardwareMonitorService != null) {
            hardwareMonitorService.stop();
        }
    }

    /**
     * Updates the hardware monitoring progress bars and value metrics.
     *
     * @param stats current system hardware utilization statistics
     */
    public void updateHardwareUI(HardwareMonitorService.HardwareStats stats) {
        if (stats == null) return;

        if (progressCpu != null) {
            progressCpu.setValue((int) stats.cpuLoad);
            if (progressCpu.getClientProperty("valueLabel") instanceof JLabel valLbl) {
                valLbl.setText(String.format(Locale.GERMANY, "%.1f%%", stats.cpuLoad));
            }
        }
        if (progressRam != null) {
            int pct = stats.ramTotal > 0 ? (int) ((stats.ramUsed * 100) / stats.ramTotal) : 0;
            progressRam.setValue(pct);
            if (progressRam.getClientProperty("valueLabel") instanceof JLabel valLbl) {
                double usedGb = stats.ramUsed / (1024.0 * 1024.0 * 1024.0);
                double totalGb = stats.ramTotal / (1024.0 * 1024.0 * 1024.0);
                valLbl.setText(String.format(Locale.GERMANY, "%.1f GB / %.1f GB (%d%%)", usedGb, totalGb, pct));
            }
        }
        if (stats.gpuName != null && !"N/A".equals(stats.gpuName)) {
            if (gpuPanel != null) gpuPanel.setVisible(true);
            if (lblGpuName != null) lblGpuName.setText("GPU: " + stats.gpuName);
            if (progressGpu != null) {
                progressGpu.setValue(stats.gpuUtilization);
                if (progressGpu.getClientProperty("valueLabel") instanceof JLabel valLbl) {
                    valLbl.setText(stats.gpuUtilization + "%");
                }
            }
            if (progressVram != null) {
                int pct = stats.vramTotal > 0 ? (int) ((stats.vramUsed * 100) / stats.vramTotal) : 0;
                progressVram.setValue(pct);
                if (progressVram.getClientProperty("valueLabel") instanceof JLabel valLbl) {
                    double usedGb = stats.vramUsed / (1024.0 * 1024.0 * 1024.0);
                    double totalGb = stats.vramTotal / (1024.0 * 1024.0 * 1024.0);
                    valLbl.setText(String.format(Locale.GERMANY, "%.1f GB / %.1f GB (%d%%)", usedGb, totalGb, pct));
                }
            }
        } else {
            if (gpuPanel != null) gpuPanel.setVisible(false);
        }
    }

    /**
     * Creates a sleek, themed hardware progress bar container with title and value indicators.
     *
     * @param title the metric title (e.g. CPU Load, RAM Usage)
     * @param bar the configured progress bar component
     * @return the assembled panel
     */
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

    /**
     * Gets the CPU progress bar.
     *
     * @return the CPU progress bar
     */
    public JProgressBar getProgressCpu() { return progressCpu; }

    /**
     * Gets the RAM progress bar.
     *
     * @return the RAM progress bar
     */
    public JProgressBar getProgressRam() { return progressRam; }

    /**
     * Gets the GPU progress bar.
     *
     * @return the GPU progress bar
     */
    public JProgressBar getProgressGpu() { return progressGpu; }

    /**
     * Gets the VRAM progress bar.
     *
     * @return the VRAM progress bar
     */
    public JProgressBar getProgressVram() { return progressVram; }

    /**
     * Gets the GPU name label.
     *
     * @return the GPU name label
     */
    public JLabel getLblGpuName() { return lblGpuName; }

    /**
     * Gets the GPU container panel.
     *
     * @return the GPU panel
     */
    public JPanel getGpuPanel() { return gpuPanel; }

    /**
     * Sets the hardware monitor service.
     *
     * @param service the hardware monitor service
     */
    public void setHardwareMonitorService(HardwareMonitorService service) {
        this.hardwareMonitorService = service;
    }
}
