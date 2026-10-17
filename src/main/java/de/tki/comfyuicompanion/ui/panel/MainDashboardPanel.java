package de.tki.comfyuicompanion.ui.panel;

import de.tki.comfyuicompanion.domain.LaunchProfile;
import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import de.tki.comfyuicompanion.service.IDownloadManager;
import de.tki.comfyuicompanion.service.IModelValidator;
import de.tki.comfyuicompanion.service.impl.CivitaiService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.HardwareMonitorService;
import de.tki.comfyuicompanion.service.impl.ModelHashRegistry;
import de.tki.comfyuicompanion.service.impl.ProfileManager;
import de.tki.comfyuicompanion.service.impl.UpdaterService;
import de.tki.comfyuicompanion.service.impl.VersionService;
import de.tki.comfyuicompanion.service.impl.CustomNodeSnapshotService;
import de.tki.comfyuicompanion.ui.AutoUpdaterDialog;
import de.tki.comfyuicompanion.ui.DeduplicationDialog;
import de.tki.comfyuicompanion.ui.GlassPanel;
import de.tki.comfyuicompanion.ui.ModelUpdateCheckerDialog;
import de.tki.comfyuicompanion.ui.dialog.SnapshotRollbackDialog;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.util.function.Consumer;

/**
 * Builds and coordinates the Dashboard panel containing launch profiles,
 * ComfyUI lifecycle actions, live console output, and hardware monitors.
 */
public class MainDashboardPanel {

    private final JFrame parentFrame;
    private final ConfigService configService;
    private final ProfileManager profileManager;
    private final IComfyLifecycleService lifecycleService;
    private final UpdaterService updaterService;
    private final ModelHashRegistry hashRegistry;
    private final IModelValidator modelValidator;
    private final CivitaiService civitaiService;
    private final IDownloadManager downloadManager;
    private LaunchProfilePanel profilePanel;
    private DashboardConsolePanel consolePanel;
    private HardwareMonitorPanel monitorPanel;
    private JLabel statusDisplay, descLabel, lblComfyVersion, lblPythonVersion;
    private JButton launchBtn, restartBtn, stopBtn, browserBtn, bootstrapBtn;
    private Timer statusTimer;
    private Consumer<LaunchProfile> startComfyHandler;
    private Runnable fullServiceRestartHandler, openBootstrapHandler;

    /** Constructs a new coordinator with required application services. */
    public MainDashboardPanel(JFrame parentFrame, ConfigService configService, ProfileManager profileManager,
                              IComfyLifecycleService lifecycleService, UpdaterService updaterService,
                              ModelHashRegistry hashRegistry, IModelValidator modelValidator,
                              CivitaiService civitaiService, IDownloadManager downloadManager) {
        this.parentFrame = parentFrame; this.configService = configService; this.profileManager = profileManager;
        this.lifecycleService = lifecycleService; this.updaterService = updaterService; this.hashRegistry = hashRegistry;
        this.modelValidator = modelValidator; this.civitaiService = civitaiService; this.downloadManager = downloadManager;
    }

    /**
     * Creates and configures the main dashboard UI panel.
     * @param tabs parent tabbed pane reference
     * @return the assembled dashboard panel
     */
    public JPanel createDashboardPanel(JTabbedPane tabs) {
        GlassPanel panel = new GlassPanel();
        panel.setLayout(new BorderLayout(20, 20));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.add(profilePanel = new LaunchProfilePanel(parentFrame, configService, profileManager,
                this::updateSelectedProfileInfo), BorderLayout.WEST);
        panel.add(createCenterPanel(), BorderLayout.CENTER);
        JScrollPane eastScroll = new JScrollPane(createEastPanel(),
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        eastScroll.setBorder(null);
        eastScroll.setOpaque(false);
        eastScroll.getViewport().setOpaque(false);
        panel.add(eastScroll, BorderLayout.EAST);
        profilePanel.refreshProfiles();
        updateSelectedProfileInfo(profilePanel.getSelectedProfile());
        statusTimer = new Timer(1000, e -> updateStatusUI());
        statusTimer.start();
        updateStatusUI();
        return panel;
    }

    private JPanel createCenterPanel() {
        GlassPanel center = new GlassPanel();
        center.setLayout(new GridBagLayout());
        center.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.BOTH; g.weightx = 1.0; g.gridx = 0; g.gridy = 0; g.insets = new Insets(5, 5, 15, 5);

        JLabel title = new JLabel("Overview");
        title.putClientProperty("FlatLaf.styleClass", "h1");
        center.add(title, g);

        statusDisplay = new JLabel("Status: Offline");
        statusDisplay.setFont(new Font("SansSerif", Font.BOLD, 18));
        statusDisplay.setForeground(Color.GRAY);
        g.gridy = 1; center.add(statusDisplay, g);

        descLabel = new JLabel("Select a profile to view details.");
        descLabel.setFont(new Font("SansSerif", Font.ITALIC, 14));
        descLabel.setForeground(Color.LIGHT_GRAY);
        g.gridy = 2; center.add(descLabel, g);

        g.gridy = 3; g.insets = new Insets(20, 0, 20, 0); center.add(createActionButtons(), g);
        g.gridy = 4; g.insets = new Insets(10, 5, 15, 5); center.add(createVersionPanel(), g);

        consolePanel = new DashboardConsolePanel();
        consolePanel.setOnClearAction(() -> {
            if (lifecycleService != null && lifecycleService.isRunning()) consolePanel.clear();
            else showInitialConsoleState(profilePanel.getSelectedProfile());
        });
        g.gridy = 5; g.weighty = 1.0; g.insets = new Insets(5, 5, 5, 5); center.add(consolePanel, g);
        return center;
    }

    private JPanel createActionButtons() {
        JPanel bar = new JPanel(new GridLayout(1, 5, 12, 12));
        bar.setOpaque(false);
        bar.add(launchBtn = createBtn("Launch", AppIcon.LAUNCH, "accent", true, e -> {
            LaunchProfile p = profilePanel != null ? profilePanel.getSelectedProfile() : null;
            if (p != null && startComfyHandler != null) { configService.setActiveProfile(p.id()); startComfyHandler.accept(p); updateStatusUI(); }
            else JOptionPane.showMessageDialog(parentFrame, "Please select a launch profile first.");
        }));
        bar.add(restartBtn = createBtn("Restart", AppIcon.RESTART, "roundRect", false, e -> { if (fullServiceRestartHandler != null) { fullServiceRestartHandler.run(); updateStatusUI(); } }));
        bar.add(stopBtn = createBtn("Stop", AppIcon.CLOSE, "roundRect", false, e -> {
            if (lifecycleService != null) { lifecycleService.stop(); if (consolePanel != null) consolePanel.appendLog("\n[Companion] ComfyUI process stopped by user.\n"); updateStatusUI(); }
        }));
        bar.add(browserBtn = createBtn("Browser", AppIcon.BROWSER, "roundRect", false, e -> {
            try { Desktop.getDesktop().browse(URI.create(configService.getComfyUIUrl())); } catch (Exception ex) { JOptionPane.showMessageDialog(parentFrame, "Could not open browser: " + ex.getMessage()); }
        }));
        bar.add(bootstrapBtn = createBtn("Setup", AppIcon.SETUP, "roundRect", true, e -> { if (openBootstrapHandler != null) openBootstrapHandler.run(); }));
        return bar;
    }

    private JButton createBtn(String text, AppIcon icon, String style, boolean enabled, java.awt.event.ActionListener l) {
        JButton b = new JButton(text, SvgIconFactory.get(icon));
        b.putClientProperty("JButton.buttonType", style);
        b.setFont(new Font("SansSerif", Font.BOLD, 14));
        b.setEnabled(enabled);
        b.addActionListener(l);
        return b;
    }

    private JPanel createVersionPanel() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel header = new JLabel("Environment Versions");
        header.putClientProperty("FlatLaf.styleClass", "h4");
        p.add(header); p.add(Box.createRigidArea(new Dimension(0, 8)));
        p.add(lblComfyVersion = new JLabel("ComfyUI: Loading...")); p.add(Box.createRigidArea(new Dimension(0, 4)));
        p.add(lblPythonVersion = new JLabel("Python: Loading..."));
        return p;
    }

    private JPanel createEastPanel() {
        GlassPanel east = new GlassPanel();
        east.setLayout(new GridBagLayout());
        east.setPreferredSize(new Dimension(340, 0));
        east.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        GridBagConstraints eastGbc = new GridBagConstraints();
        eastGbc.fill = GridBagConstraints.HORIZONTAL;
        eastGbc.weightx = 1.0;
        eastGbc.gridx = 0; eastGbc.gridy = 0;
        eastGbc.insets = new Insets(0, 0, 0, 0);

        monitorPanel = new HardwareMonitorPanel();
        east.add(monitorPanel, eastGbc);

        eastGbc.gridy++;
        eastGbc.insets = new Insets(8, 5, 8, 5);
        east.add(new JSeparator(), eastGbc);

        eastGbc.gridy++;
        eastGbc.insets = new Insets(2, 5, 4, 5);
        JLabel actionsHeader = new JLabel("Quick Actions");
        actionsHeader.putClientProperty("FlatLaf.styleClass", "h3");
        east.add(actionsHeader, eastGbc);

        eastGbc.gridy++;
        eastGbc.insets = new Insets(3, 5, 3, 5);
        east.add(createQuickActionBtn("Update ComfyUI & Nodes", AppIcon.DOWNLOAD, () -> new AutoUpdaterDialog(parentFrame, updaterService).setVisible(true)), eastGbc);

        eastGbc.gridy++;
        east.add(createQuickActionBtn("Snapshots & Rollback", AppIcon.REFRESH, () -> new SnapshotRollbackDialog(parentFrame, new CustomNodeSnapshotService(configService)).setVisible(true)), eastGbc);

        eastGbc.gridy++;
        east.add(createQuickActionBtn("Check Model Upgrades", AppIcon.SEARCH, () -> new ModelUpdateCheckerDialog(parentFrame, configService, hashRegistry, modelValidator, civitaiService, downloadManager).setVisible(true)), eastGbc);

        eastGbc.gridy++;
        east.add(createQuickActionBtn("Storage Optimizer", AppIcon.STORAGE_OPTIMIZER, () -> new DeduplicationDialog(parentFrame, configService, modelValidator, hashRegistry).setVisible(true)), eastGbc);

        eastGbc.gridy++;
        eastGbc.insets = new Insets(8, 5, 8, 5);
        east.add(new JSeparator(), eastGbc);

        eastGbc.gridy++;
        eastGbc.insets = new Insets(0, 0, 0, 0);
        east.add(new DashboardLegalNoticePanel(parentFrame, configService), eastGbc);

        eastGbc.gridy++;
        eastGbc.weighty = 1.0;
        east.add(Box.createVerticalGlue(), eastGbc);

        return east;
    }

    private JButton createQuickActionBtn(String title, AppIcon icon, Runnable action) {
        JButton b = createBtn(title, icon, "roundRect", true, e -> action.run());
        b.setFont(new Font("SansSerif", Font.BOLD, 13));
        b.setPreferredSize(new Dimension(0, 35));
        return b;
    }

    /** Updates status display and button enabled states. */
    public void updateStatusUI() {
        boolean r = lifecycleService != null && lifecycleService.isRunning();
        boolean s = r && lifecycleService.isProcessAlive() && !lifecycleService.isGuiLineShown();
        if (statusDisplay != null) {
            statusDisplay.setText(r ? (s ? "Status: Starting" : "Status: Running") : "Status: Offline");
            statusDisplay.setForeground(r ? (s ? new Color(255, 204, 0) : new Color(30, 190, 170)) : Color.GRAY);
        }
        if (launchBtn != null) launchBtn.setEnabled(!r); if (restartBtn != null) restartBtn.setEnabled(r);
        if (stopBtn != null) stopBtn.setEnabled(r); if (browserBtn != null) browserBtn.setEnabled(r);
    }

    /** Refreshes ComfyUI and Python version labels. */
    public void refreshVersions(VersionService vs, ConfigService cs) {
        if (vs == null || cs == null) return;
        String comfy = vs.getInstalledComfyVersion(cs.getComfyUIPath());
        String python = vs.getInstalledPythonVersion(cs.getPythonPath());
        if (lblComfyVersion != null) lblComfyVersion.setText("ComfyUI: " + comfy + " (Remote: Fetching...)");
        if (lblPythonVersion != null) lblPythonVersion.setText("Python: " + python + " (Latest: " + vs.getRemotePythonVersion() + ")");
        vs.getRemoteComfyVersionAsync().thenAccept(rc -> SwingUtilities.invokeLater(() -> {
            if (lblComfyVersion != null) lblComfyVersion.setText("ComfyUI: " + comfy + " (Remote: " + rc + ")");
        }));
    }

    /** Shows the initial offline banner in console. */
    public void showInitialConsoleState(LaunchProfile p) { if (consolePanel != null) consolePanel.showInitialBanner(p); }
    /** Updates selected profile description and console banner. */
    public void updateSelectedProfileInfo(LaunchProfile p) {
        if (descLabel != null) {
            descLabel.setText(p != null && p.description() != null && !p.description().isBlank() ? p.description() : "Select a profile to view details.");
        }
        if (profilePanel != null) profilePanel.updateSelectedProfileInfo(p);
        if (lifecycleService == null || !lifecycleService.isRunning()) showInitialConsoleState(p);
    }
    /** Updates hardware monitoring progress bars. */
    public void updateHardwareUI(HardwareMonitorService.HardwareStats s) { if (monitorPanel != null) monitorPanel.updateHardwareUI(s); }
    /** Refreshes launch profiles in the list. */
    public void refreshProfileList(DefaultListModel<LaunchProfile> m) { if (profilePanel != null) profilePanel.refreshProfiles(); }
    /** Creates a slim stat progress bar panel. */
    public static JPanel createSlimStatPanel(String t, JProgressBar b) { return HardwareMonitorPanel.createSlimStatPanel(t, b); }

    /** @return the launch profiles panel */ public LaunchProfilePanel getLaunchProfilePanel() { return profilePanel; }
    /** @return the live console panel */ public DashboardConsolePanel getDashboardConsolePanel() { return consolePanel; }
    /** @return the hardware monitor panel */ public HardwareMonitorPanel getHardwareMonitorPanel() { return monitorPanel; }
    /** @return the profile selection list */ public JList<LaunchProfile> getProfileList() { return profilePanel != null ? profilePanel.getProfileList() : null; }
    /** @return the console text area */ public JTextArea getComfyConsoleArea() { return consolePanel != null ? consolePanel.getConsoleArea() : null; }
    /** @return the console search field */ public JTextField getLogSearchField() { return consolePanel != null ? consolePanel.getLogSearchField() : null; }
    /** @return the status display label */ public JLabel getStatusDisplay() { return statusDisplay; }
    /** @return the profile description label */ public JLabel getDescLabel() { return descLabel; }
    /** @return the launch button */ public JButton getLaunchBtn() { return launchBtn; }
    /** @return the restart button */ public JButton getRestartBtn() { return restartBtn; }
    /** @return the stop button */ public JButton getStopBtn() { return stopBtn; }
    /** @return the browser button */ public JButton getBrowserBtn() { return browserBtn; }
    /** @return the setup button */ public JButton getBootstrapBtn() { return bootstrapBtn; }
    /** @return the ComfyUI version label */ public JLabel getLblComfyVersion() { return lblComfyVersion; }
    /** @return the Python version label */ public JLabel getLblPythonVersion() { return lblPythonVersion; }
    /** @return the CPU progress bar */ public JProgressBar getProgressCpu() { return monitorPanel != null ? monitorPanel.getProgressCpu() : null; }
    /** @return the RAM progress bar */ public JProgressBar getProgressRam() { return monitorPanel != null ? monitorPanel.getProgressRam() : null; }
    /** @return the GPU progress bar */ public JProgressBar getProgressGpu() { return monitorPanel != null ? monitorPanel.getProgressGpu() : null; }
    /** @return the VRAM progress bar */ public JProgressBar getProgressVram() { return monitorPanel != null ? monitorPanel.getProgressVram() : null; }
    /** @return the GPU name label */ public JLabel getLblGpuName() { return monitorPanel != null ? monitorPanel.getLblGpuName() : null; }
    /** @return the GPU container panel */ public JPanel getGpuPanel() { return monitorPanel != null ? monitorPanel.getGpuPanel() : null; }
    /** @return the periodic status timer */ public Timer getStatusTimer() { return statusTimer; }
    /** @param t timer to set */ public void setStatusTimer(Timer t) { this.statusTimer = t; }
    /** @param h handler for launching ComfyUI */ public void setStartComfyHandler(Consumer<LaunchProfile> h) { this.startComfyHandler = h; }
    /** @return handler for launching ComfyUI */ public Consumer<LaunchProfile> getStartComfyHandler() { return startComfyHandler; }
    /** @param h handler for full service restart */ public void setFullServiceRestartHandler(Runnable h) { this.fullServiceRestartHandler = h; }
    /** @return handler for full service restart */ public Runnable getFullServiceRestartHandler() { return fullServiceRestartHandler; }
    /** @param h handler for opening bootstrap setup */ public void setOpenBootstrapHandler(Runnable h) { this.openBootstrapHandler = h; }
    /** @return handler for opening bootstrap setup */ public Runnable getOpenBootstrapHandler() { return openBootstrapHandler; }
}
