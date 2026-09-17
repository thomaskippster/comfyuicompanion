package de.tki.comfyuicompanion.ui.panel;

import de.tki.comfyuicompanion.service.IModelArchitectureService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.CardPanel;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Encapsulates the visual presentation and action wiring for the Settings tab.
 */
public class MainSettingsPanel {
    private static final Logger logger = LoggerFactory.getLogger(MainSettingsPanel.class);

    private final JFrame parentFrame;
    private final ConfigService configService;
    private final IModelArchitectureService modelArchitectureService;

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

    private Runnable onTabVisibilityChanged;
    private Consumer<Boolean> onThemeChanged;

    public MainSettingsPanel(JFrame parentFrame,
                             ConfigService configService,
                             IModelArchitectureService modelArchitectureService) {
        this.parentFrame = parentFrame;
        this.configService = configService;
        this.modelArchitectureService = modelArchitectureService;
    }

    /**
     * Sets the callback triggered when tab-enabling settings change.
     *
     * @param onTabVisibilityChanged runnable updating tab visibility
     */
    public void setOnTabVisibilityChanged(Runnable onTabVisibilityChanged) {
        this.onTabVisibilityChanged = onTabVisibilityChanged;
    }

    /**
     * Sets the callback triggered when the theme setting changes.
     *
     * @param onThemeChanged consumer receiving the new dark mode state
     */
    public void setOnThemeChanged(Consumer<Boolean> onThemeChanged) {
        this.onThemeChanged = onThemeChanged;
    }

    public JPanel createSettingsPanel(Runnable onShowPathsDialog,
                                      Runnable onScanComfy,
                                      Runnable onTriggerEnvironmentRepair,
                                      Runnable onTriggerWslFix,
                                      Runnable onShowDownloadSettings,
                                      Runnable onShowInstallationDialog,
                                      Runnable onShowVideoArchitectAutoconfig,
                                      Runnable onShowApiKeysDialog,
                                      Runnable onShowHelpDialog,
                                      Runnable onResetSettings) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BorderLayout(15, 15));
        panel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));

        Font btnFont = new Font("SansSerif", Font.BOLD, 14);
        Font checkFont = new Font("SansSerif", Font.PLAIN, 14);

        JPanel grid = new JPanel(new GridLayout(1, 2, 25, 0));
        grid.setOpaque(false);

        // Left Column: General & Paths
        CardPanel left = new CardPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        JLabel pathsHeader = new JLabel("General & Paths");
        pathsHeader.putClientProperty("FlatLaf.styleClass", "h3");
        pathsHeader.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton pathsBtn = new JButton("Configure Directories...", SvgIconFactory.get(AppIcon.FOLDER));
        pathsBtn.setFont(btnFont);
        pathsBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        pathsBtn.setMaximumSize(new Dimension(360, 40));
        pathsBtn.addActionListener(e -> { if (onShowPathsDialog != null) onShowPathsDialog.run(); });

        JButton scanComfyBtn = new JButton("Scan ComfyUI Installation...", SvgIconFactory.get(AppIcon.SEARCH));
        scanComfyBtn.setFont(btnFont);
        scanComfyBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        scanComfyBtn.setMaximumSize(new Dimension(360, 40));
        scanComfyBtn.addActionListener(e -> { if (onScanComfy != null) onScanComfy.run(); });

        JButton repairBtn = new JButton("Repair Environment Automatically...", SvgIconFactory.get(AppIcon.SETUP));
        repairBtn.setFont(btnFont);
        repairBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        repairBtn.setMaximumSize(new Dimension(360, 40));
        repairBtn.addActionListener(e -> { if (onTriggerEnvironmentRepair != null) onTriggerEnvironmentRepair.run(); });

        JButton fixWslBtn = new JButton("Fix WSL [wsl-pip] Dependencies...", SvgIconFactory.get(AppIcon.LINUX));
        fixWslBtn.setFont(btnFont);
        fixWslBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        fixWslBtn.setMaximumSize(new Dimension(360, 40));
        fixWslBtn.addActionListener(e -> { if (onTriggerWslFix != null) onTriggerWslFix.run(); });

        JButton downloadSettingsBtn = new JButton("Download Settings...", SvgIconFactory.get(AppIcon.DOWNLOAD));
        downloadSettingsBtn.setFont(btnFont);
        downloadSettingsBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        downloadSettingsBtn.setMaximumSize(new Dimension(360, 40));
        downloadSettingsBtn.addActionListener(e -> { if (onShowDownloadSettings != null) onShowDownloadSettings.run(); });

        JButton bridgeBtn = new JButton("Install ComfyUI Bridge...", SvgIconFactory.get(AppIcon.LAUNCH));
        bridgeBtn.setFont(btnFont);
        bridgeBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        bridgeBtn.setMaximumSize(new Dimension(360, 40));
        bridgeBtn.addActionListener(e -> { if (onShowInstallationDialog != null) onShowInstallationDialog.run(); });

        JButton videoArchitectAutoconfigBtn = new JButton("Autoconfig Video Architect...", SvgIconFactory.get(AppIcon.VIDEO_ARCHITECT));
        videoArchitectAutoconfigBtn.setFont(btnFont);
        videoArchitectAutoconfigBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        videoArchitectAutoconfigBtn.setMaximumSize(new Dimension(360, 40));
        videoArchitectAutoconfigBtn.addActionListener(e -> { if (onShowVideoArchitectAutoconfig != null) onShowVideoArchitectAutoconfig.run(); });

        JPanel checksPanel = new JPanel();
        checksPanel.setOpaque(false);
        checksPanel.setLayout(new BoxLayout(checksPanel, BoxLayout.Y_AXIS));
        checksPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        backgroundCheck = new JCheckBox("Run in background");
        backgroundCheck.setFont(checkFont);
        backgroundCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        backgroundCheck.addActionListener(e -> configService.setBackgroundModeEnabled(backgroundCheck.isSelected()));

        shutdownCheck = new JCheckBox("Shutdown after completion");
        shutdownCheck.setFont(checkFont);
        shutdownCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        shutdownCheck.addActionListener(e -> configService.setShutdownAfterDownloadEnabled(shutdownCheck.isSelected()));

        restartCheck = new JCheckBox("Restart after completion");
        restartCheck.setFont(checkFont);
        restartCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        restartCheck.addActionListener(e -> configService.setRestartAfterDownloadEnabled(restartCheck.isSelected()));

        darkCheck = new JCheckBox("Dark Mode");
        darkCheck.setFont(checkFont);
        darkCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        darkCheck.addActionListener(e -> {
            boolean dark = darkCheck.isSelected();
            configService.setDarkMode(dark);
            if (onThemeChanged != null) {
                onThemeChanged.accept(dark);
            }
        });

        fastHashCheck = new JCheckBox("Fast hashing (AutoV1)");
        fastHashCheck.setFont(checkFont);
        fastHashCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        fastHashCheck.addActionListener(e -> configService.setFastHashEnabled(fastHashCheck.isSelected()));

        hideComfyuiCheck = new JCheckBox("Hide ComfyUI Web Client (Replacement Mode)");
        hideComfyuiCheck.setFont(checkFont);
        hideComfyuiCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        hideComfyuiCheck.setToolTipText("When enabled, ComfyUI Companion replaces the web UI and suppresses opening the browser window when ComfyUI starts.");
        hideComfyuiCheck.addActionListener(e -> configService.setHideComfyUI(hideComfyuiCheck.isSelected()));

        promptLabCheck = new JCheckBox("Enable Prompt Lab (Experimental)");
        promptLabCheck.setFont(checkFont);
        promptLabCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        promptLabCheck.addActionListener(e -> {
            configService.setPromptLabEnabled(promptLabCheck.isSelected());
            if (onTabVisibilityChanged != null) {
                onTabVisibilityChanged.run();
            }
        });

        videoArchitectCheck = new JCheckBox("Enable Video Architect (Beta)");
        videoArchitectCheck.setFont(checkFont);
        videoArchitectCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        videoArchitectCheck.addActionListener(e -> {
            configService.setVideoArchitectEnabled(videoArchitectCheck.isSelected());
            if (onTabVisibilityChanged != null) {
                onTabVisibilityChanged.run();
            }
        });

        blueprintGalleryCheck = new JCheckBox("Enable Blueprint Gallery (Experimental)");
        blueprintGalleryCheck.setFont(checkFont);
        blueprintGalleryCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        blueprintGalleryCheck.addActionListener(e -> {
            configService.setBlueprintGalleryEnabled(blueprintGalleryCheck.isSelected());
            if (onTabVisibilityChanged != null) {
                onTabVisibilityChanged.run();
            }
        });

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

        // Right Column: AI & Support
        CardPanel right = new CardPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));

        JLabel aiHeader = new JLabel("AI & Support");
        aiHeader.putClientProperty("FlatLaf.styleClass", "h3");
        aiHeader.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton apiBtn = new JButton("Audio & TTS Settings...", SvgIconFactory.get(AppIcon.AUDIO));
        apiBtn.setFont(btnFont);
        apiBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        apiBtn.setMaximumSize(new Dimension(360, 40));
        apiBtn.addActionListener(e -> { if (onShowApiKeysDialog != null) onShowApiKeysDialog.run(); });

        JButton resetSettingsBtn = new JButton("Reset Application Settings...", SvgIconFactory.get(AppIcon.RESTART));
        resetSettingsBtn.setFont(btnFont);
        resetSettingsBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        resetSettingsBtn.setMaximumSize(new Dimension(360, 40));
        resetSettingsBtn.addActionListener(e -> { if (onResetSettings != null) onResetSettings.run(); });

        JButton parseBlueprintsBtn = new JButton("Parse blueprints", SvgIconFactory.get(AppIcon.BLUEPRINT_GALLERY));
        parseBlueprintsBtn.setFont(btnFont);
        parseBlueprintsBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        parseBlueprintsBtn.setMaximumSize(new Dimension(360, 40));
        parseBlueprintsBtn.addActionListener(e -> {
            if (modelArchitectureService != null) {
                modelArchitectureService.runBlueprintAnalysis();
                JOptionPane.showMessageDialog(parentFrame,
                        "Blueprint parsing started in the background. You can track progress below on this Settings tab.",
                        "Blueprint Parsing",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        });

        JButton helpBtn = new JButton("Show Help & Instructions", SvgIconFactory.get(AppIcon.HELP));
        helpBtn.setFont(btnFont);
        helpBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        helpBtn.setMaximumSize(new Dimension(360, 40));
        helpBtn.addActionListener(e -> { if (onShowHelpDialog != null) onShowHelpDialog.run(); });

        blueprintPanel = new JPanel(new BorderLayout(10, 5)) {
            @Override
            public void updateUI() {
                super.updateUI();
                putClientProperty("FlatLaf.style", "arc: 12; background: $Card.background; border: 12,12,12,12,$Card.border,1,12");
            }
        };
        blueprintPanel.setOpaque(false);
        blueprintPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        blueprintPanel.setVisible(modelArchitectureService != null && !modelArchitectureService.isBlueprintAnalysisCompleted());
        blueprintPanel.setMaximumSize(new Dimension(360, 80));
        blueprintPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

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

        if (modelArchitectureService != null) {
            modelArchitectureService.addProgressListener((percent, currentFileName, completed) -> {
                SwingUtilities.invokeLater(() -> {
                    blueprintBar.setValue(percent);
                    if (completed) {
                        blueprintStatusLabel.setText("Completed");
                        blueprintPanel.setVisible(false);
                    } else {
                        blueprintStatusLabel.setText(currentFileName);
                        blueprintPanel.setVisible(true);
                    }
                    blueprintPanel.revalidate();
                    blueprintPanel.repaint();
                });
            });
        }

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
        panel.add(grid, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Synchronizes the UI checkbox components with stored persistent configurations.
     */
    public void loadSettingsIntoUI() {
        if (backgroundCheck != null) backgroundCheck.setSelected(configService.isBackgroundModeEnabled());
        if (shutdownCheck != null) shutdownCheck.setSelected(configService.isShutdownAfterDownloadEnabled());
        if (restartCheck != null) restartCheck.setSelected(configService.isRestartAfterDownloadEnabled());
        if (darkCheck != null) darkCheck.setSelected(configService.isDarkMode());
        if (fastHashCheck != null) fastHashCheck.setSelected(configService.isFastHashEnabled());
        if (hideComfyuiCheck != null) hideComfyuiCheck.setSelected(configService.isHideComfyUI());
        if (promptLabCheck != null) promptLabCheck.setSelected(configService.isPromptLabEnabled());
        if (videoArchitectCheck != null) videoArchitectCheck.setSelected(configService.isVideoArchitectEnabled());
        if (blueprintGalleryCheck != null) blueprintGalleryCheck.setSelected(configService.isBlueprintGalleryEnabled());
    }

    public JCheckBox getBackgroundCheck() { return backgroundCheck; }
    public JCheckBox getShutdownCheck() { return shutdownCheck; }
    public JCheckBox getRestartCheck() { return restartCheck; }
    public JCheckBox getDarkCheck() { return darkCheck; }
    public JCheckBox getFastHashCheck() { return fastHashCheck; }
    public JCheckBox getHideComfyuiCheck() { return hideComfyuiCheck; }
    public JCheckBox getHideTerminalCheck() { return hideComfyuiCheck; }
    public JCheckBox getPromptLabCheck() { return promptLabCheck; }
    public JCheckBox getVideoArchitectCheck() { return videoArchitectCheck; }
    public JCheckBox getBlueprintGalleryCheck() { return blueprintGalleryCheck; }
    public JPanel getBlueprintPanel() { return blueprintPanel; }
    public JLabel getBlueprintStatusLabel() { return blueprintStatusLabel; }
    public JProgressBar getBlueprintBar() { return blueprintBar; }
}
