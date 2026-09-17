package de.tki.comfyuicompanion.ui;

import de.tki.comfyuicompanion.domain.LaunchProfile;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.*;

/**
 * View component for the Dashboard / Overview tab.
 * Encapsulates profile selection, status indicators, control buttons,
 * environment version labels, system hardware stats, and console logs
 * by composing modular sub-panels.
 */
@Component
public class DashboardView extends JPanel {

    public interface DashboardListener {
        void onAddProfile();
        void onRemoveProfile(LaunchProfile selected);
        void onProfileSelected(LaunchProfile selected);
        void onLaunch(LaunchProfile selected);
        void onRestart(LaunchProfile selected);
        void onStop();
        void onOpenBrowser();
        void onOpenBootstrap();
        void onOpenAutoUpdater();
        void onOpenModelUpdateChecker();
        void onOpenStorageOptimizer();
    }

    private DashboardListener listener;

    private ProfileListPanel leftPanel;
    private SystemStatsPanel statsPanel;

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

    public DashboardView() {
        initUI();
    }

    public void setListener(DashboardListener listener) {
        this.listener = listener;
        if (statsPanel != null) {
            statsPanel.setListener(listener);
        }
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setOpaque(false);

        GlassPanel container = new GlassPanel();
        container.setLayout(new BorderLayout(20, 20));
        container.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // LEFT: Profile List
        leftPanel = new ProfileListPanel(
                selected -> {
                    if (selected != null) {
                        descLabel.setText("<html><body style='width: 500px;'>" + selected.description() + "</body></html>");
                    } else {
                        descLabel.setText("Select a profile to view details.");
                    }
                    if (listener != null) listener.onProfileSelected(selected);
                },
                () -> { if (listener != null) listener.onAddProfile(); },
                selected -> { if (listener != null) listener.onRemoveProfile(selected); }
        );

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

        launchBtn = new JButton("Launch", SvgIconFactory.get(AppIcon.LAUNCH));
        launchBtn.putClientProperty("JButton.buttonType", "accent");
        launchBtn.setFont(new Font("SansSerif", Font.BOLD, 14));

        restartBtn = new JButton("Restart", SvgIconFactory.get(AppIcon.RESTART));
        restartBtn.putClientProperty("JButton.buttonType", "roundRect");
        restartBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        restartBtn.setEnabled(false);

        stopBtn = new JButton("Stop", SvgIconFactory.get(AppIcon.CLOSE));
        stopBtn.putClientProperty("JButton.buttonType", "roundRect");
        stopBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        stopBtn.setEnabled(false);

        browserBtn = new JButton("Browser", SvgIconFactory.get(AppIcon.BROWSER));
        browserBtn.putClientProperty("JButton.buttonType", "roundRect");
        browserBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        browserBtn.setEnabled(false);

        bootstrapBtn = new JButton("Setup", SvgIconFactory.get(AppIcon.SETUP));
        bootstrapBtn.putClientProperty("JButton.buttonType", "roundRect");
        bootstrapBtn.setFont(new Font("SansSerif", Font.BOLD, 14));

        actionPanel.add(launchBtn);
        actionPanel.add(restartBtn);
        actionPanel.add(stopBtn);
        actionPanel.add(browserBtn);
        actionPanel.add(bootstrapBtn);

        launchBtn.addActionListener(e -> {
            if (listener != null) listener.onLaunch(leftPanel.getList().getSelectedValue());
        });
        restartBtn.addActionListener(e -> {
            if (listener != null) listener.onRestart(leftPanel.getList().getSelectedValue());
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
        logSearchField.putClientProperty("JTextField.leadingIcon", SvgIconFactory.get(AppIcon.SEARCH, 14));
        logSearchField.putClientProperty("JTextField.placeholderText", "Search console logs...");

        JButton clearConsoleBtn = new JButton("Clear Console", SvgIconFactory.get(AppIcon.TRASH, 14));
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
        statsPanel = new SystemStatsPanel(listener);

        container.add(leftPanel, BorderLayout.WEST);
        container.add(rightPanel, BorderLayout.CENTER);
        container.add(statsPanel, BorderLayout.EAST);

        add(container, BorderLayout.CENTER);
    }

    public JPanel createSlimStatPanel(String title, JProgressBar bar) {
        return SystemStatsPanel.createSlimStatPanel(title, bar);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        SwingUtilities.updateComponentTreeUI(this);
    }

    // --- Getters & UI State Modifiers ---

    public JList<LaunchProfile> getProfileList() { return leftPanel.getList(); }
    public DefaultListModel<LaunchProfile> getProfileListModel() { return leftPanel.getModel(); }
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
    public JProgressBar getProgressCpu() { return statsPanel.getProgressCpu(); }
    public JProgressBar getProgressRam() { return statsPanel.getProgressRam(); }
    public JProgressBar getProgressGpu() { return statsPanel.getProgressGpu(); }
    public JProgressBar getProgressVram() { return statsPanel.getProgressVram(); }
    public JLabel getLblGpuName() { return statsPanel.getLblGpuName(); }
    public JPanel getGpuPanel() { return statsPanel.getGpuPanel(); }
}
