package de.tki.comfyuicompanion.ui;

import de.tki.comfyuicompanion.domain.LaunchProfile;
import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import de.tki.comfyuicompanion.service.IModelArchitectureService;
import de.tki.comfyuicompanion.service.impl.ComfyProcessController;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.ProfileManager;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Encapsulates the application header bar including the logo, title,
 * blueprint scanning progress, server status indicator, and quick start/stop button.
 */
public class MainHeaderBar extends JPanel {

    private final ConfigService configService;
    private final IModelArchitectureService modelArchitectureService;
    private final ComfyProcessController processController;
    private final IComfyLifecycleService lifecycleService;
    private final ProfileManager profileManager;
    private final Consumer<LaunchProfile> startComfyAction;

    private JLabel globalStatusIndicator;
    private JButton quickActionBtn;
    private JLabel activeProfileLabel;
    private JPanel headerBlueprintProgressPanel;
    private JLabel headerBlueprintLabel;
    private JProgressBar headerBlueprintBar;

    private String lastActiveProfileId = null;
    private String cachedActiveProfileName = "None";
    private long lastProfileCacheUpdate = 0;
    private Timer headerTimer;

    public MainHeaderBar(JFrame parentFrame,
                         ConfigService configService,
                         IModelArchitectureService modelArchitectureService,
                         ComfyProcessController processController,
                         IComfyLifecycleService lifecycleService,
                         ProfileManager profileManager,
                         Consumer<LaunchProfile> startComfyAction) {
        super(new BorderLayout(15, 10));
        this.configService = configService;
        this.modelArchitectureService = modelArchitectureService;
        this.processController = processController;
        this.lifecycleService = lifecycleService;
        this.profileManager = profileManager;
        this.startComfyAction = startComfyAction;

        setOpaque(false);
        setPreferredSize(new Dimension(0, 60));
        setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        buildHeader(parentFrame);
        startTimer();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();

        boolean dark = configService != null && configService.isDarkMode();
        Color bgColor = dark ? new Color(10, 11, 14, 230) : new Color(255, 255, 255, 120);
        g2.setColor(bgColor);
        g2.fillRect(0, 0, w, h);

        g2.setColor(dark ? new Color(38, 255, 223, 150) : new Color(0, 120, 150, 30));
        g2.fillRect(0, h - 1, w, 1);
        g2.dispose();
    }

    private void buildHeader(JFrame parentFrame) {
        JPanel leftHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        leftHeader.setOpaque(false);
        JLabel logoLabel = new JLabel(SvgIconFactory.get(AppIcon.IMAGE_LAB, 24));
        JLabel titleLabel = new JLabel("Companion for ComfyUI") {
            @Override
            public void updateUI() {
                super.updateUI();
                if (configService != null) {
                    setForeground(configService.isDarkMode() ? new Color(30, 190, 170) : new Color(0, 102, 204));
                }
            }
        };
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        titleLabel.setForeground(configService != null && configService.isDarkMode() ? new Color(30, 190, 170) : new Color(0, 102, 204));

        JLabel tagline = new JLabel("|  Unified AI Model Manager & Image Lab");
        tagline.setFont(new Font("SansSerif", Font.ITALIC, 11));
        tagline.setForeground(configService != null && configService.isDarkMode() ? new Color(112, 138, 144) : Color.GRAY);

        leftHeader.add(logoLabel);
        leftHeader.add(titleLabel);
        leftHeader.add(tagline);
        add(leftHeader, BorderLayout.WEST);

        JPanel rightHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 5));
        rightHeader.setOpaque(false);

        headerBlueprintProgressPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        headerBlueprintProgressPanel.setOpaque(false);
        headerBlueprintProgressPanel.setVisible(modelArchitectureService != null && !modelArchitectureService.isBlueprintAnalysisCompleted());

        headerBlueprintLabel = new JLabel("Blueprints: 0%", SvgIconFactory.get(AppIcon.REFRESH, 14), SwingConstants.LEFT);
        headerBlueprintLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        headerBlueprintLabel.setForeground(configService != null && configService.isDarkMode() ? new Color(30, 190, 170) : new Color(0, 102, 204));

        headerBlueprintBar = new JProgressBar(0, 100);
        headerBlueprintBar.setPreferredSize(new Dimension(65, 6));
        headerBlueprintBar.putClientProperty("FlatLaf.style", "arc: 999; foreground: $SlimStat.barForeground; background: $SlimStat.barBackground;");
        headerBlueprintBar.setValue(modelArchitectureService != null ? modelArchitectureService.getBlueprintProgressPercent() : 0);

        headerBlueprintProgressPanel.add(headerBlueprintLabel);
        headerBlueprintProgressPanel.add(headerBlueprintBar);

        if (modelArchitectureService != null) {
            modelArchitectureService.addProgressListener((percent, currentFileName, completed) -> {
                SwingUtilities.invokeLater(() -> {
                    if (completed || percent >= 100) {
                        headerBlueprintProgressPanel.setVisible(false);
                    } else {
                        headerBlueprintLabel.setText("Blueprints: " + percent + "%");
                        headerBlueprintLabel.setToolTipText(currentFileName != null && !currentFileName.isEmpty()
                                ? "Scanning: " + currentFileName : "Scanning Blueprints...");
                        headerBlueprintBar.setValue(percent);
                        headerBlueprintProgressPanel.setVisible(true);
                    }
                    rightHeader.revalidate();
                    rightHeader.repaint();
                });
            });
        }

        activeProfileLabel = new JLabel("Profile: Loading...", SvgIconFactory.get(AppIcon.USER, 14), SwingConstants.LEFT);
        activeProfileLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        activeProfileLabel.setForeground(configService != null && configService.isDarkMode() ? new Color(224, 248, 245) : Color.GRAY);

        globalStatusIndicator = new JLabel("Status: Offline", SvgIconFactory.createStatusDot(new Color(220, 50, 50), 10), SwingConstants.LEFT);
        globalStatusIndicator.setFont(new Font("SansSerif", Font.BOLD, 12));
        globalStatusIndicator.setForeground(configService != null && configService.isDarkMode() ? new Color(112, 138, 144) : Color.GRAY);

        quickActionBtn = new JButton("Start", SvgIconFactory.get(AppIcon.PLAY, 12));
        quickActionBtn.putClientProperty("JButton.buttonType", "roundRect");
        quickActionBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        quickActionBtn.setFocusable(false);

        quickActionBtn.addActionListener(e -> {
            boolean running = processController != null && processController.isRunning();
            if (running) {
                int confirm = JOptionPane.showConfirmDialog(parentFrame, "Stop ComfyUI server?", "Stop Server", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    processController.stop();
                }
            } else {
                String activeIdVal = configService != null ? configService.getActiveProfile() : null;
                List<LaunchProfile> profilesVal = profileManager != null ? profileManager.loadProfiles() : List.of();
                LaunchProfile activeProfileVal = profilesVal.stream()
                        .filter(p -> p.id().equals(activeIdVal)).findFirst().orElse(null);
                if (activeProfileVal == null && !profilesVal.isEmpty()) activeProfileVal = profilesVal.get(0);

                if (activeProfileVal != null && startComfyAction != null) {
                    startComfyAction.accept(activeProfileVal);
                } else {
                    JOptionPane.showMessageDialog(parentFrame, "No launch profile available to start ComfyUI.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        rightHeader.add(headerBlueprintProgressPanel);
        rightHeader.add(activeProfileLabel);
        rightHeader.add(globalStatusIndicator);
        rightHeader.add(quickActionBtn);
        add(rightHeader, BorderLayout.EAST);
    }

    private void startTimer() {
        headerTimer = new Timer(1000, e -> {
            boolean running = processController != null && processController.isRunning();
            boolean starting = false;
            if (running) {
                if (processController.isProcessAlive() && !processController.isGuiLineShown()) {
                    starting = true;
                } else if (lifecycleService != null && lifecycleService.isProcessAlive() && !lifecycleService.isGuiLineShown()) {
                    starting = true;
                }
            }

            if (running) {
                if (starting) {
                    globalStatusIndicator.setText("Status: Starting");
                    globalStatusIndicator.setIcon(SvgIconFactory.createStatusDot(new Color(255, 204, 0), 10));
                    globalStatusIndicator.setForeground(new Color(255, 204, 0));
                    quickActionBtn.setText("Stop");
                    quickActionBtn.setIcon(SvgIconFactory.get(AppIcon.CLOSE, 12));
                } else {
                    globalStatusIndicator.setText("Status: Running");
                    globalStatusIndicator.setIcon(SvgIconFactory.createStatusDot(new Color(30, 190, 170), 10));
                    globalStatusIndicator.setForeground(new Color(30, 190, 170));
                    quickActionBtn.setText("Stop");
                    quickActionBtn.setIcon(SvgIconFactory.get(AppIcon.CLOSE, 12));
                }
            } else {
                globalStatusIndicator.setText("Status: Offline");
                globalStatusIndicator.setIcon(SvgIconFactory.createStatusDot(new Color(220, 50, 50), 10));
                globalStatusIndicator.setForeground(configService != null && configService.isDarkMode() ? new Color(112, 138, 144) : Color.GRAY);
                quickActionBtn.setText("Start");
                quickActionBtn.setIcon(SvgIconFactory.get(AppIcon.PLAY, 12));
            }

            String activeIdVal = configService != null ? configService.getActiveProfile() : null;
            long now = System.currentTimeMillis();
            if (!Objects.equals(activeIdVal, lastActiveProfileId) || (now - lastProfileCacheUpdate > 10000)) {
                lastActiveProfileId = activeIdVal;
                lastProfileCacheUpdate = now;
                List<LaunchProfile> profilesVal = profileManager != null ? profileManager.loadProfiles() : List.of();
                LaunchProfile activeProfileVal = profilesVal.stream()
                        .filter(p -> p.id().equals(activeIdVal)).findFirst().orElse(null);
                if (activeProfileVal == null && !profilesVal.isEmpty()) activeProfileVal = profilesVal.get(0);
                cachedActiveProfileName = (activeProfileVal != null) ? activeProfileVal.name() : "None";
            }
            activeProfileLabel.setText("Profile: " + cachedActiveProfileName);
        });
        headerTimer.start();
    }
}
