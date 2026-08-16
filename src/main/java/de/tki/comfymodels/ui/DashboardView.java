package de.tki.comfymodels.ui;

import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.URI;
import java.util.List;

/**
 * View component for the Dashboard / Overview tab.
 * Encapsulates profile selection, status indicators, control buttons,
 * environment version labels, system hardware stats, and console logs.
 */
@Component
public class DashboardView extends JPanel {

    public interface DashboardListener {
        void onAddProfile();
        void onRemoveProfile(de.tki.comfymodels.domain.LaunchProfile selected);
        void onProfileSelected(de.tki.comfymodels.domain.LaunchProfile selected);
        void onLaunch(de.tki.comfymodels.domain.LaunchProfile selected);
        void onRestart(de.tki.comfymodels.domain.LaunchProfile selected);
        void onStop();
        void onOpenBrowser();
        void onOpenBootstrap();
        void onOpenAutoUpdater();
        void onOpenModelUpdateChecker();
        void onOpenStorageOptimizer();
    }

    private DashboardListener listener;

    private JList<de.tki.comfymodels.domain.LaunchProfile> profileList;
    private DefaultListModel<de.tki.comfymodels.domain.LaunchProfile> profileListModel;
    private JLabel statusDisplay;
    private JLabel descLabel;
    private JButton launchBtn;
    private JButton restartBtn;
    private JButton stopBtn;
    private JButton browserBtn;
    private JButton bootstrapBtn;
    private JLabel lblComfyVersion;
    private JLabel lblPythonVersion;
    private JTextArea consoleOutput;
    private JTextField logSearchField;
    private JProgressBar progressCpu;
    private JProgressBar progressRam;
    private JProgressBar progressGpu;
    private JProgressBar progressVram;
    private JLabel lblGpuName;
    private JPanel gpuPanel;

    public DashboardView() {
        initUI();
    }

    public void setListener(DashboardListener listener) {
        this.listener = listener;
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setOpaque(false);

        GlassPanel container = new GlassPanel();
        container.setLayout(new BorderLayout(20, 20));
        container.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // LEFT: Profile List
        GlassPanel leftPanel = new GlassPanel();
        leftPanel.setLayout(new BorderLayout(10, 10));
        leftPanel.setPreferredSize(new Dimension(320, 0));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel profilesHeader = new JLabel("Startprofile");
        profilesHeader.putClientProperty("FlatLaf.styleClass", "h3");
        leftPanel.add(profilesHeader, BorderLayout.NORTH);

        profileListModel = new DefaultListModel<>();
        profileList = new JList<>(profileListModel);
        profileList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                de.tki.comfymodels.domain.LaunchProfile p = (de.tki.comfymodels.domain.LaunchProfile) value;
                java.awt.Component c = super.getListCellRendererComponent(list, " " + (p != null ? p.name() : ""), index, isSelected, cellHasFocus);
                if (c instanceof JLabel) {
                    ((JLabel) c).setPreferredSize(new Dimension(0, 35));
                    ((JLabel) c).setFont(new Font("SansSerif", isSelected ? Font.BOLD : Font.PLAIN, 14));
                }
                return c;
            }
        });

        profileList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            de.tki.comfymodels.domain.LaunchProfile selected = profileList.getSelectedValue();
            if (selected != null) {
                descLabel.setText("<html><body style='width: 500px;'>" + selected.description() + "</body></html>");
            } else {
                descLabel.setText("Select a profile to view details.");
            }
            if (listener != null) {
                listener.onProfileSelected(selected);
            }
        });

        JScrollPane profileScroll = new JScrollPane(profileList);
        profileScroll.setBorder(BorderFactory.createEmptyBorder());
        profileScroll.setViewportBorder(BorderFactory.createEmptyBorder());
        leftPanel.add(profileScroll, BorderLayout.CENTER);

        JPanel profileButtons = new JPanel(new GridLayout(1, 2, 8, 0));
        profileButtons.setOpaque(false);
        JButton addProfileBtn = new JButton("➕ Add");
        JButton removeProfileBtn = new JButton("➖ Remove");
        profileButtons.add(addProfileBtn);
        profileButtons.add(removeProfileBtn);
        leftPanel.add(profileButtons, BorderLayout.SOUTH);

        addProfileBtn.addActionListener(e -> {
            if (listener != null) listener.onAddProfile();
        });

        removeProfileBtn.addActionListener(e -> {
            if (listener != null) listener.onRemoveProfile(profileList.getSelectedValue());
        });

        // CENTER: Control Center & Console Output
        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setOpaque(false);
        rightPanel.putClientProperty("FlatLaf.style", "arc: 16; background: $Card.background; border: 20,20,20,20,$Card.border,1,16");
        rightPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 15, 5);
        gbc.weightx = 1.0;

        JLabel titleLabel = new JLabel("Overview");
        titleLabel.putClientProperty("FlatLaf.styleClass", "h1");
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        rightPanel.add(titleLabel, gbc);

        statusDisplay = new JLabel("Status: Offline");
        statusDisplay.setFont(new Font("SansSerif", Font.BOLD, 18));
        statusDisplay.setForeground(Color.GRAY);
        gbc.gridy = 1;
        rightPanel.add(statusDisplay, gbc);

        descLabel = new JLabel("Select a profile to view details.");
        descLabel.setFont(new Font("SansSerif", Font.ITALIC, 14));
        descLabel.setForeground(Color.LIGHT_GRAY);
        gbc.gridy = 2;
        rightPanel.add(descLabel, gbc);

        // One-row clean button layout (5 buttons)
        JPanel actionPanel = new JPanel(new GridLayout(1, 5, 12, 12));
        actionPanel.setOpaque(false);

        launchBtn = new JButton("🚀 Launch");
        launchBtn.putClientProperty("JButton.buttonType", "accent");
        launchBtn.setFont(new Font("SansSerif", Font.BOLD, 14));

        restartBtn = new JButton("🔄 Restart");
        restartBtn.putClientProperty("JButton.buttonType", "roundRect");
        restartBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        restartBtn.setEnabled(false);

        stopBtn = new JButton("⏹ Stop");
        stopBtn.putClientProperty("JButton.buttonType", "roundRect");
        stopBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        stopBtn.setEnabled(false);

        browserBtn = new JButton("🌐 Browser");
        browserBtn.putClientProperty("JButton.buttonType", "roundRect");
        browserBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        browserBtn.setEnabled(false);

        bootstrapBtn = new JButton("🛠️ Setup");
        bootstrapBtn.putClientProperty("JButton.buttonType", "roundRect");
        bootstrapBtn.setFont(new Font("SansSerif", Font.BOLD, 14));

        actionPanel.add(launchBtn);
        actionPanel.add(restartBtn);
        actionPanel.add(stopBtn);
        actionPanel.add(browserBtn);
        actionPanel.add(bootstrapBtn);

        launchBtn.addActionListener(e -> {
            if (listener != null) listener.onLaunch(profileList.getSelectedValue());
        });

        restartBtn.addActionListener(e -> {
            if (listener != null) listener.onRestart(profileList.getSelectedValue());
        });

        stopBtn.addActionListener(e -> {
            if (listener != null) listener.onStop();
        });

        browserBtn.addActionListener(e -> {
            if (listener != null) listener.onOpenBrowser();
        });

        bootstrapBtn.addActionListener(e -> {
            if (listener != null) listener.onOpenBootstrap();
        });

        gbc.gridy = 3; gbc.gridheight = 1; gbc.insets = new Insets(20, 0, 20, 0);
        rightPanel.add(actionPanel, gbc);

        // Environment Versions section
        JPanel versionPanel = new JPanel();
        versionPanel.setOpaque(false);
        versionPanel.setLayout(new BoxLayout(versionPanel, BoxLayout.Y_AXIS));

        JLabel versionHeader = new JLabel("Environment Versions");
        versionHeader.putClientProperty("FlatLaf.styleClass", "h4");

        lblComfyVersion = new JLabel("ComfyUI: Loading...");
        lblPythonVersion = new JLabel("Python: Loading...");

        versionPanel.add(versionHeader);
        versionPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        versionPanel.add(lblComfyVersion);
        versionPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        versionPanel.add(lblPythonVersion);

        gbc.gridy = 5; gbc.insets = new Insets(10, 5, 15, 5);
        rightPanel.add(versionPanel, gbc);

        // Console Output
        consoleOutput = new JTextArea();
        consoleOutput.setBackground(UIManager.getColor("TextArea.background"));
        consoleOutput.setForeground(UIManager.getColor("TextArea.foreground"));
        consoleOutput.setFont(new Font("Monospaced", Font.PLAIN, 13));
        consoleOutput.setEditable(false);
        consoleOutput.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane consoleScroll = new JScrollPane(consoleOutput) {
            @Override
            public void updateUI() {
                super.updateUI();
                Color c = UIManager.getColor("Component.borderColor");
                if (c != null) {
                    setBorder(BorderFactory.createLineBorder(c, 1));
                }
            }
        };
        Color bc = UIManager.getColor("Component.borderColor");
        consoleScroll.setBorder(BorderFactory.createLineBorder(bc != null ? bc : new Color(60, 60, 60), 1));

        JPanel consoleContainer = new JPanel(new BorderLayout(5, 5));
        consoleContainer.setOpaque(false);

        JPanel consoleToolbar = new JPanel(new BorderLayout(5, 5));
        consoleToolbar.setOpaque(false);

        logSearchField = new JTextField();
        logSearchField.putClientProperty("JTextField.placeholderText", "🔍 Search console logs...");

        JButton clearConsoleBtn = new JButton("Clear Console");
        clearConsoleBtn.putClientProperty("JButton.buttonType", "roundRect");
        clearConsoleBtn.addActionListener(e -> consoleOutput.setText(""));

        consoleToolbar.add(logSearchField, BorderLayout.CENTER);
        consoleToolbar.add(clearConsoleBtn, BorderLayout.EAST);

        consoleContainer.add(consoleToolbar, BorderLayout.NORTH);
        consoleContainer.add(consoleScroll, BorderLayout.CENTER);

        logSearchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { highlight(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { highlight(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { highlight(); }

            private void highlight() {
                Highlighter h = consoleOutput.getHighlighter();
                h.removeAllHighlights();
                String text = logSearchField.getText();
                if (text == null || text.trim().isEmpty()) return;
                String content = consoleOutput.getText();
                if (content == null || content.isEmpty()) return;

                String pattern = text.toLowerCase();
                String lowerContent = content.toLowerCase();
                int index = lowerContent.indexOf(pattern);

                Highlighter.HighlightPainter painter = 
                    new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 255, 100, 100));
                while (index >= 0) {
                    try {
                        h.addHighlight(index, index + pattern.length(), painter);
                    } catch (BadLocationException ex) {
                        // ignore
                    }
                    index = lowerContent.indexOf(pattern, index + pattern.length());
                }
            }
        });

        gbc.gridy = 6; gbc.weighty = 1.0; gbc.insets = new Insets(5, 5, 5, 5);
        rightPanel.add(consoleContainer, gbc);

        // RIGHT: System Stats & Quick Actions
        JPanel rightPanelEast = new JPanel(new GridBagLayout());
        rightPanelEast.setOpaque(false);
        rightPanelEast.setPreferredSize(new Dimension(340, 0));
        rightPanelEast.putClientProperty("FlatLaf.style", "arc: 16; background: $Card.background; border: 15,15,15,15,$Card.border,1,16");
        rightPanelEast.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        GridBagConstraints eastGbc = new GridBagConstraints();
        eastGbc.fill = GridBagConstraints.HORIZONTAL;
        eastGbc.weightx = 1.0;
        eastGbc.gridx = 0;
        eastGbc.gridy = 0;
        eastGbc.insets = new Insets(5, 5, 15, 5);

        JLabel statsHeader = new JLabel("System Stats");
        statsHeader.putClientProperty("FlatLaf.styleClass", "h3");
        rightPanelEast.add(statsHeader, eastGbc);

        // CPU
        eastGbc.gridy++;
        eastGbc.insets = new Insets(5, 5, 8, 5);
        progressCpu = new JProgressBar(0, 100);
        rightPanelEast.add(createSlimStatPanel("CPU Load", progressCpu), eastGbc);

        // RAM
        eastGbc.gridy++;
        progressRam = new JProgressBar(0, 100);
        rightPanelEast.add(createSlimStatPanel("RAM Usage", progressRam), eastGbc);

        // GPU Panel wrapper
        eastGbc.gridy++;
        gpuPanel = new JPanel(new GridBagLayout());
        gpuPanel.setOpaque(false);

        GridBagConstraints gpuGbc = new GridBagConstraints();
        gpuGbc.fill = GridBagConstraints.HORIZONTAL;
        gpuGbc.weightx = 1.0;
        gpuGbc.gridx = 0;
        gpuGbc.gridy = 0;
        gpuGbc.insets = new Insets(5, 0, 8, 0);

        lblGpuName = new JLabel("GPU: N/A");
        lblGpuName.setFont(new Font("SansSerif", Font.BOLD, 12));
        gpuPanel.add(lblGpuName, gpuGbc);

        gpuGbc.gridy++;
        progressGpu = new JProgressBar(0, 100);
        gpuPanel.add(createSlimStatPanel("GPU Load", progressGpu), gpuGbc);

        gpuGbc.gridy++;
        gpuGbc.insets = new Insets(5, 0, 5, 0);
        progressVram = new JProgressBar(0, 100);
        gpuPanel.add(createSlimStatPanel("VRAM Usage", progressVram), gpuGbc);

        rightPanelEast.add(gpuPanel, eastGbc);

        // Separator
        eastGbc.gridy++;
        eastGbc.insets = new Insets(15, 5, 15, 5);
        rightPanelEast.add(new JSeparator(), eastGbc);

        // Quick Actions
        eastGbc.gridy++;
        eastGbc.insets = new Insets(5, 5, 15, 5);
        JLabel actionsHeader = new JLabel("Quick Actions");
        actionsHeader.putClientProperty("FlatLaf.styleClass", "h3");
        rightPanelEast.add(actionsHeader, eastGbc);

        // Buttons
        eastGbc.gridy++;
        eastGbc.insets = new Insets(6, 5, 6, 5);
        JButton updateBtn = new JButton("📥 Update ComfyUI & Nodes");
        updateBtn.putClientProperty("JButton.buttonType", "roundRect");
        updateBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        updateBtn.setPreferredSize(new Dimension(0, 35));
        updateBtn.addActionListener(e -> {
            if (listener != null) listener.onOpenAutoUpdater();
        });
        rightPanelEast.add(updateBtn, eastGbc);

        eastGbc.gridy++;
        JButton checkUpdatesBtn = new JButton("🔍 Check Model Upgrades");
        checkUpdatesBtn.putClientProperty("JButton.buttonType", "roundRect");
        checkUpdatesBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        checkUpdatesBtn.setPreferredSize(new Dimension(0, 35));
        checkUpdatesBtn.addActionListener(e -> {
            if (listener != null) listener.onOpenModelUpdateChecker();
        });
        rightPanelEast.add(checkUpdatesBtn, eastGbc);

        eastGbc.gridy++;
        JButton storageOptBtn = new JButton("🧼 Storage Optimizer");
        storageOptBtn.putClientProperty("JButton.buttonType", "roundRect");
        storageOptBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        storageOptBtn.setPreferredSize(new Dimension(0, 35));
        storageOptBtn.addActionListener(e -> {
            if (listener != null) listener.onOpenStorageOptimizer();
        });
        rightPanelEast.add(storageOptBtn, eastGbc);

        eastGbc.gridy++;
        eastGbc.weighty = 1.0;
        eastGbc.fill = GridBagConstraints.BOTH;
        rightPanelEast.add(Box.createGlue(), eastGbc);

        container.add(leftPanel, BorderLayout.WEST);
        container.add(rightPanel, BorderLayout.CENTER);
        container.add(rightPanelEast, BorderLayout.EAST);

        add(container, BorderLayout.CENTER);
    }

    public JPanel createSlimStatPanel(String title, JProgressBar bar) {
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

    // --- Getters & UI State Modifiers ---

    public JList<de.tki.comfymodels.domain.LaunchProfile> getProfileList() { return profileList; }
    public DefaultListModel<de.tki.comfymodels.domain.LaunchProfile> getProfileListModel() { return profileListModel; }
    public JLabel getStatusDisplay() { return statusDisplay; }
    public JLabel getDescLabel() { return descLabel; }
    public JButton getLaunchBtn() { return launchBtn; }
    public JButton getRestartBtn() { return restartBtn; }
    public JButton getStopBtn() { return stopBtn; }
    public JButton getBrowserBtn() { return browserBtn; }
    public JButton getBootstrapBtn() { return bootstrapBtn; }
    public JLabel getLblComfyVersion() { return lblComfyVersion; }
    public JLabel getLblPythonVersion() { return lblPythonVersion; }
    public JTextArea getConsoleOutput() { return consoleOutput; }
    public JTextField getLogSearchField() { return logSearchField; }
    public JProgressBar getProgressCpu() { return progressCpu; }
    public JProgressBar getProgressRam() { return progressRam; }
    public JProgressBar getProgressGpu() { return progressGpu; }
    public JProgressBar getProgressVram() { return progressVram; }
    public JLabel getLblGpuName() { return lblGpuName; }
    public JPanel getGpuPanel() { return gpuPanel; }
}
