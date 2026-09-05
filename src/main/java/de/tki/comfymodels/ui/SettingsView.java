package de.tki.comfymodels.ui;

import org.springframework.stereotype.Component;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * View component for the Settings tab.
 * Encapsulates all Settings UI layout, delegating actions back to callbacks set by the controller.
 */
@Component
public class SettingsView extends JPanel {

    public interface SettingsListener {
        void onConfigureDirectories();
        void onScanComfyUI();
        void onRepairEnvironment();
        void onFixWslDependencies();
        void onDownloadSettings();
        void onInstallBridge();
        void onVideoArchitectAutoconfig();
        void onAudioTtsSettings();
        void onShowHelp();
        void onResetSettings();
        void onParseBlueprints();
        void onBackgroundModeChanged(boolean enabled);
        void onShutdownAfterDownloadChanged(boolean enabled);
        void onRestartAfterDownloadChanged(boolean enabled);
        void onDarkModeChanged(boolean enabled);
        void onFastHashChanged(boolean enabled);
        void onHideComfyUIChanged(boolean enabled);
        void onPromptLabEnabledChanged(boolean enabled);
        void onVideoArchitectEnabledChanged(boolean enabled);
        void onBlueprintGalleryEnabledChanged(boolean enabled);
    }

    private SettingsListener listener;

    private JCheckBox backgroundCheck;
    private JCheckBox shutdownCheck;
    private JCheckBox restartCheck;
    private JCheckBox darkCheck;
    private JCheckBox fastHashCheck;
    private JCheckBox hideComfyuiCheck;
    private JCheckBox promptLabCheck;
    private JCheckBox videoArchitectCheck;
    private JCheckBox blueprintGalleryCheck;

    private JPanel blueprintPanel;
    private JLabel blueprintStatusLabel;
    private JProgressBar blueprintBar;

    public SettingsView() {
        initUI();
    }

    public void setListener(SettingsListener listener) {
        this.listener = listener;
    }

    private void initUI() {
        setOpaque(false);
        setLayout(new BorderLayout(15, 15));
        setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));

        Font btnFont = new Font("SansSerif", Font.BOLD, 14);
        Font checkFont = new Font("SansSerif", Font.PLAIN, 14);

        JPanel grid = new JPanel(new GridLayout(1, 2, 25, 0));
        grid.setOpaque(false);

        // ---- LEFT COLUMN: General & Paths ----
        CardPanel left = new CardPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        JLabel pathsHeader = new JLabel("General & Paths");
        pathsHeader.putClientProperty("FlatLaf.styleClass", "h3");
        pathsHeader.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

        JButton pathsBtn = new JButton("📁 Configure Directories...");
        pathsBtn.setFont(btnFont);
        pathsBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        pathsBtn.setMaximumSize(new Dimension(360, 40));
        pathsBtn.addActionListener(e -> { if (listener != null) listener.onConfigureDirectories(); });

        JButton scanComfyBtn = new JButton("🔍 Scan ComfyUI Installation...");
        scanComfyBtn.setFont(btnFont);
        scanComfyBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        scanComfyBtn.setMaximumSize(new Dimension(360, 40));
        scanComfyBtn.addActionListener(e -> { if (listener != null) listener.onScanComfyUI(); });

        JButton repairBtn = new JButton("🛠️ Repair Environment Automatically...");
        repairBtn.setFont(btnFont);
        repairBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        repairBtn.setMaximumSize(new Dimension(360, 40));
        repairBtn.addActionListener(e -> { if (listener != null) listener.onRepairEnvironment(); });

        JButton fixWslBtn = new JButton("🐧 Fix WSL [wsl-pip] Dependencies...");
        fixWslBtn.setFont(btnFont);
        fixWslBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        fixWslBtn.setMaximumSize(new Dimension(360, 40));
        fixWslBtn.addActionListener(e -> { if (listener != null) listener.onFixWslDependencies(); });

        JButton downloadSettingsBtn = new JButton("📥 Download Settings...");
        downloadSettingsBtn.setFont(btnFont);
        downloadSettingsBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        downloadSettingsBtn.setMaximumSize(new Dimension(360, 40));
        downloadSettingsBtn.addActionListener(e -> { if (listener != null) listener.onDownloadSettings(); });

        JButton bridgeBtn = new JButton("🚀 Install ComfyUI Bridge...");
        bridgeBtn.setFont(btnFont);
        bridgeBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        bridgeBtn.setMaximumSize(new Dimension(360, 40));
        bridgeBtn.addActionListener(e -> { if (listener != null) listener.onInstallBridge(); });

        JButton videoArchitectAutoconfigBtn = new JButton("🎬 Autoconfig Video Architect...");
        videoArchitectAutoconfigBtn.setFont(btnFont);
        videoArchitectAutoconfigBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        videoArchitectAutoconfigBtn.setMaximumSize(new Dimension(360, 40));
        videoArchitectAutoconfigBtn.addActionListener(e -> { if (listener != null) listener.onVideoArchitectAutoconfig(); });

        // Checkboxes
        JPanel checksPanel = new JPanel();
        checksPanel.setOpaque(false);
        checksPanel.setLayout(new BoxLayout(checksPanel, BoxLayout.Y_AXIS));
        checksPanel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

        backgroundCheck = new JCheckBox("Run in background");
        backgroundCheck.setFont(checkFont);
        backgroundCheck.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        backgroundCheck.addActionListener(e -> { if (listener != null) listener.onBackgroundModeChanged(backgroundCheck.isSelected()); });

        shutdownCheck = new JCheckBox("Shutdown after completion");
        shutdownCheck.setFont(checkFont);
        shutdownCheck.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        shutdownCheck.addActionListener(e -> { if (listener != null) listener.onShutdownAfterDownloadChanged(shutdownCheck.isSelected()); });

        restartCheck = new JCheckBox("Restart after completion");
        restartCheck.setFont(checkFont);
        restartCheck.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        restartCheck.addActionListener(e -> { if (listener != null) listener.onRestartAfterDownloadChanged(restartCheck.isSelected()); });

        darkCheck = new JCheckBox("Dark Mode");
        darkCheck.setFont(checkFont);
        darkCheck.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        darkCheck.addActionListener(e -> { if (listener != null) listener.onDarkModeChanged(darkCheck.isSelected()); });

        fastHashCheck = new JCheckBox("Fast hashing (AutoV1)");
        fastHashCheck.setFont(checkFont);
        fastHashCheck.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        fastHashCheck.addActionListener(e -> { if (listener != null) listener.onFastHashChanged(fastHashCheck.isSelected()); });

        hideComfyuiCheck = new JCheckBox("Hide ComfyUI Web Client (Replacement Mode)");
        hideComfyuiCheck.setFont(checkFont);
        hideComfyuiCheck.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        hideComfyuiCheck.addActionListener(e -> { if (listener != null) listener.onHideComfyUIChanged(hideComfyuiCheck.isSelected()); });

        promptLabCheck = new JCheckBox("Enable Prompt Lab (Experimental)");
        promptLabCheck.setFont(checkFont);
        promptLabCheck.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        promptLabCheck.addActionListener(e -> { if (listener != null) listener.onPromptLabEnabledChanged(promptLabCheck.isSelected()); });

        videoArchitectCheck = new JCheckBox("Enable Video Architect (Beta)");
        videoArchitectCheck.setFont(checkFont);
        videoArchitectCheck.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        videoArchitectCheck.addActionListener(e -> { if (listener != null) listener.onVideoArchitectEnabledChanged(videoArchitectCheck.isSelected()); });

        blueprintGalleryCheck = new JCheckBox("Enable Blueprint Gallery (Experimental)");
        blueprintGalleryCheck.setFont(checkFont);
        blueprintGalleryCheck.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        blueprintGalleryCheck.addActionListener(e -> { if (listener != null) listener.onBlueprintGalleryEnabledChanged(blueprintGalleryCheck.isSelected()); });

        checksPanel.add(backgroundCheck);
        checksPanel.add(Box.createVerticalStrut(8));
        checksPanel.add(shutdownCheck);
        checksPanel.add(Box.createVerticalStrut(8));
        checksPanel.add(restartCheck);
        checksPanel.add(Box.createVerticalStrut(8));
        checksPanel.add(darkCheck);
        checksPanel.add(Box.createVerticalStrut(8));
        checksPanel.add(fastHashCheck);
        checksPanel.add(Box.createVerticalStrut(8));
        checksPanel.add(hideComfyuiCheck);
        checksPanel.add(Box.createVerticalStrut(8));
        checksPanel.add(promptLabCheck);
        checksPanel.add(Box.createVerticalStrut(8));
        checksPanel.add(videoArchitectCheck);
        checksPanel.add(Box.createVerticalStrut(8));
        checksPanel.add(blueprintGalleryCheck);

        left.add(pathsHeader);
        left.add(Box.createVerticalStrut(20));
        left.add(pathsBtn);
        left.add(Box.createVerticalStrut(10));
        left.add(scanComfyBtn);
        left.add(Box.createVerticalStrut(10));
        left.add(repairBtn);
        left.add(Box.createVerticalStrut(10));
        left.add(fixWslBtn);
        left.add(Box.createVerticalStrut(10));
        left.add(downloadSettingsBtn);
        left.add(Box.createVerticalStrut(10));
        left.add(bridgeBtn);
        left.add(Box.createVerticalStrut(10));
        left.add(videoArchitectAutoconfigBtn);
        left.add(Box.createVerticalStrut(25));
        left.add(checksPanel);
        left.add(Box.createVerticalGlue());

        // ---- RIGHT COLUMN: AI & Help ----
        CardPanel right = new CardPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));

        JLabel aiHeader = new JLabel("AI & Support");
        aiHeader.putClientProperty("FlatLaf.styleClass", "h3");
        aiHeader.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

        JButton apiBtn = new JButton("🎙️ Audio & TTS Settings...");
        apiBtn.setFont(btnFont);
        apiBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        apiBtn.setMaximumSize(new Dimension(360, 40));
        apiBtn.addActionListener(e -> { if (listener != null) listener.onAudioTtsSettings(); });

        JButton helpBtn = new JButton("ℹ Show Help & Instructions");
        helpBtn.setFont(btnFont);
        helpBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        helpBtn.setMaximumSize(new Dimension(360, 40));
        helpBtn.addActionListener(e -> { if (listener != null) listener.onShowHelp(); });

        JButton resetSettingsBtn = new JButton("🔄 Reset Application Settings...");
        resetSettingsBtn.setFont(btnFont);
        resetSettingsBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        resetSettingsBtn.setMaximumSize(new Dimension(360, 40));
        resetSettingsBtn.addActionListener(e -> { if (listener != null) listener.onResetSettings(); });

        JButton parseBlueprintsBtn = new JButton("🔍 Parse blueprints");
        parseBlueprintsBtn.setFont(btnFont);
        parseBlueprintsBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        parseBlueprintsBtn.setMaximumSize(new Dimension(360, 40));
        parseBlueprintsBtn.addActionListener(e -> { if (listener != null) listener.onParseBlueprints(); });

        // Blueprint Analysis Progress section
        blueprintPanel = new JPanel(new BorderLayout(10, 5)) {
            @Override
            public void updateUI() {
                super.updateUI();
                putClientProperty("FlatLaf.style", "arc: 12; background: $Card.background; border: 12,12,12,12,$Card.border,1,12");
            }
        };
        blueprintPanel.setOpaque(false);
        blueprintPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        blueprintPanel.setVisible(false); // controller will show when needed
        blueprintPanel.setMaximumSize(new Dimension(360, 80));
        blueprintPanel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

        JLabel blueprintTitle = new JLabel("Blueprint Parsing") {
            @Override
            public void updateUI() {
                super.updateUI();
                Color c = UIManager.getColor("SlimStat.titleForeground");
                if (c != null) setForeground(c);
            }
        };
        blueprintTitle.setFont(new Font("SansSerif", Font.BOLD, 12));
        Color tc = UIManager.getColor("SlimStat.titleForeground");
        if (tc != null) blueprintTitle.setForeground(tc);

        blueprintStatusLabel = new JLabel("Initializing...") {
            @Override
            public void updateUI() {
                super.updateUI();
                Color c = UIManager.getColor("SlimStat.valueForeground");
                if (c != null) setForeground(c);
            }
        };
        blueprintStatusLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        Color vc = UIManager.getColor("SlimStat.valueForeground");
        if (vc != null) blueprintStatusLabel.setForeground(vc);

        blueprintBar = new JProgressBar(0, 100) {
            @Override
            public void updateUI() {
                super.updateUI();
                putClientProperty("FlatLaf.style", "arc: 999; foreground: $SlimStat.barForeground; background: $SlimStat.barBackground;");
            }
        };
        blueprintBar.setPreferredSize(new Dimension(0, 8));
        blueprintBar.setStringPainted(true);

        JPanel textPanel = new JPanel(new BorderLayout(5, 2));
        textPanel.setOpaque(false);
        textPanel.add(blueprintTitle, BorderLayout.WEST);
        textPanel.add(blueprintStatusLabel, BorderLayout.EAST);

        blueprintPanel.add(textPanel, BorderLayout.NORTH);
        blueprintPanel.add(blueprintBar, BorderLayout.CENTER);

        right.add(aiHeader);
        right.add(Box.createVerticalStrut(20));
        right.add(apiBtn);
        right.add(Box.createVerticalStrut(10));
        right.add(resetSettingsBtn);
        right.add(Box.createVerticalStrut(10));
        right.add(parseBlueprintsBtn);
        right.add(Box.createVerticalStrut(20));
        right.add(blueprintPanel);
        right.add(Box.createVerticalStrut(25));
        right.add(helpBtn);
        right.add(Box.createVerticalGlue());

        grid.add(left);
        grid.add(right);
        add(grid, BorderLayout.CENTER);
    }

    // --- Blueprint progress wiring (called by controller) ---

    public void setBlueprintProgressVisible(boolean visible) {
        blueprintPanel.setVisible(visible);
        blueprintPanel.revalidate();
        blueprintPanel.repaint();
    }

    public void setBlueprintProgress(int percent, String status) {
        blueprintBar.setValue(percent);
        blueprintStatusLabel.setText(status);
    }

    // --- Checkbox state setters (called during init from controller) ---

    public void setBackgroundCheck(boolean selected) { backgroundCheck.setSelected(selected); }
    public void setShutdownCheck(boolean selected) { shutdownCheck.setSelected(selected); }
    public void setRestartCheck(boolean selected) { restartCheck.setSelected(selected); }
    public void setDarkCheck(boolean selected) { darkCheck.setSelected(selected); }
    public void setFastHashCheck(boolean selected) { fastHashCheck.setSelected(selected); }
    public void setHideComfyuiCheck(boolean selected) { hideComfyuiCheck.setSelected(selected); }
    public void setPromptLabCheck(boolean selected) { promptLabCheck.setSelected(selected); }
    public void setVideoArchitectCheck(boolean selected) { videoArchitectCheck.setSelected(selected); }
    public void setBlueprintGalleryCheck(boolean selected) { blueprintGalleryCheck.setSelected(selected); }

    // --- Getters ---

    public JCheckBox getBackgroundCheck() { return backgroundCheck; }
    public JCheckBox getShutdownCheck() { return shutdownCheck; }
    public JCheckBox getRestartCheck() { return restartCheck; }
    public JCheckBox getDarkCheck() { return darkCheck; }
    public JCheckBox getFastHashCheck() { return fastHashCheck; }
    public JCheckBox getHideComfyuiCheck() { return hideComfyuiCheck; }
    public JCheckBox getPromptLabCheck() { return promptLabCheck; }
    public JCheckBox getVideoArchitectCheck() { return videoArchitectCheck; }
    public JCheckBox getBlueprintGalleryCheck() { return blueprintGalleryCheck; }
    public JPanel getBlueprintPanel() { return blueprintPanel; }
    public JLabel getBlueprintStatusLabel() { return blueprintStatusLabel; }
    public JProgressBar getBlueprintBar() { return blueprintBar; }

    @Override
    public void addNotify() {
        super.addNotify();
        SwingUtilities.updateComponentTreeUI(this);
    }
}
