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
import de.tki.comfyuicompanion.ui.AutoUpdaterDialog;
import de.tki.comfyuicompanion.ui.DeduplicationDialog;
import de.tki.comfyuicompanion.ui.GlassPanel;
import de.tki.comfyuicompanion.ui.ModelUpdateCheckerDialog;
import de.tki.comfyuicompanion.ui.ThemeManager;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Builds and coordinates the Dashboard panel containing launch profiles,
 * ComfyUI lifecycle actions, live console output, and hardware monitors.
 */
public class MainDashboardPanel {
    private static final Logger logger = LoggerFactory.getLogger(MainDashboardPanel.class);

    private final JFrame parentFrame;
    private final ConfigService configService;
    private final ProfileManager profileManager;
    private final IComfyLifecycleService lifecycleService;
    private final UpdaterService updaterService;
    private final ModelHashRegistry hashRegistry;
    private final IModelValidator modelValidator;
    private final CivitaiService civitaiService;
    private final IDownloadManager downloadManager;

    private JList<LaunchProfile> profileList;
    private JTextArea comfyConsoleArea;
    private JLabel statusDisplay;
    private JLabel descLabel;
    private JButton launchBtn;
    private JButton restartBtn;
    private JButton stopBtn;
    private JButton browserBtn;
    private JButton bootstrapBtn;
    private JLabel lblComfyVersion;
    private JLabel lblPythonVersion;
    private JTextField logSearchField;
    private JProgressBar progressCpu;
    private JProgressBar progressRam;
    private JProgressBar progressGpu;
    private JProgressBar progressVram;
    private JLabel lblGpuName;
    private JPanel gpuPanel;
    private Timer statusTimer;

    private Consumer<LaunchProfile> startComfyHandler;
    private Runnable fullServiceRestartHandler;
    private Runnable openBootstrapHandler;

    public MainDashboardPanel(JFrame parentFrame,
                              ConfigService configService,
                              ProfileManager profileManager,
                              IComfyLifecycleService lifecycleService,
                              UpdaterService updaterService,
                              ModelHashRegistry hashRegistry,
                              IModelValidator modelValidator,
                              CivitaiService civitaiService,
                              IDownloadManager downloadManager) {
        this.parentFrame = parentFrame;
        this.configService = configService;
        this.profileManager = profileManager;
        this.lifecycleService = lifecycleService;
        this.updaterService = updaterService;
        this.hashRegistry = hashRegistry;
        this.modelValidator = modelValidator;
        this.civitaiService = civitaiService;
        this.downloadManager = downloadManager;
    }

    /**
     * Sets the handler for launching ComfyUI with the selected profile.
     *
     * @param startComfyHandler consumer invoked with the launch profile
     */
    public void setStartComfyHandler(Consumer<LaunchProfile> startComfyHandler) {
        this.startComfyHandler = startComfyHandler;
    }

    /**
     * Sets the handler for executing a full service restart.
     *
     * @param fullServiceRestartHandler runnable executed on restart
     */
    public void setFullServiceRestartHandler(Runnable fullServiceRestartHandler) {
        this.fullServiceRestartHandler = fullServiceRestartHandler;
    }

    /**
     * Sets the handler for opening the installation / setup dialog.
     *
     * @param openBootstrapHandler runnable opening setup
     */
    public void setOpenBootstrapHandler(Runnable openBootstrapHandler) {
        this.openBootstrapHandler = openBootstrapHandler;
    }

    public JPanel createDashboardPanel(JTabbedPane tabs) {
        GlassPanel panel = new GlassPanel();
        panel.setLayout(new BorderLayout(20, 20));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // LEFT: Profile List
        GlassPanel leftPanel = new GlassPanel();
        leftPanel.setLayout(new BorderLayout(10, 10));
        leftPanel.setPreferredSize(new Dimension(320, 0));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel profilesHeader = new JLabel("Startprofile");
        profilesHeader.putClientProperty("FlatLaf.styleClass", "h3");
        leftPanel.add(profilesHeader, BorderLayout.NORTH);

        DefaultListModel<LaunchProfile> profileListModel = new DefaultListModel<>();
        profileList = new JList<>(profileListModel);
        profileList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                LaunchProfile p = (LaunchProfile) value;
                Component c = super.getListCellRendererComponent(list, " " + p.name(), index, isSelected, cellHasFocus);
                if (c instanceof JLabel label) {
                    label.setPreferredSize(new Dimension(0, 35));
                    label.setFont(new Font("SansSerif", isSelected ? Font.BOLD : Font.PLAIN, 14));
                }
                return c;
            }
        });

        profileList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            LaunchProfile selected = profileList.getSelectedValue();
            if (selected != null) {
                configService.setActiveProfile(selected.id());
                updateSelectedProfileInfo(selected);
            }
        });

        JScrollPane profileScroll = new JScrollPane(profileList);
        profileScroll.setBorder(BorderFactory.createEmptyBorder());
        profileScroll.setViewportBorder(BorderFactory.createEmptyBorder());
        leftPanel.add(profileScroll, BorderLayout.CENTER);

        JPanel profileButtons = new JPanel(new GridLayout(1, 2, 8, 0));
        profileButtons.setOpaque(false);
        JButton addProfileBtn = new JButton("Add", SvgIconFactory.get(AppIcon.ADD));
        JButton removeProfileBtn = new JButton("Remove", SvgIconFactory.get(AppIcon.REMOVE));
        profileButtons.add(addProfileBtn);
        profileButtons.add(removeProfileBtn);
        leftPanel.add(profileButtons, BorderLayout.SOUTH);

        addProfileBtn.addActionListener(e -> {
            JTextField nameField = new JTextField();
            JTextField argsField = new JTextField("--listen, --port, 8188");
            Object[] message = { "Profile Name:", nameField, "Arguments (comma-separated):", argsField };
            int option = JOptionPane.showConfirmDialog(parentFrame, message, "Add Launch Profile", JOptionPane.OK_CANCEL_OPTION);
            if (option == JOptionPane.OK_OPTION && !nameField.getText().trim().isEmpty()) {
                List<String> args = java.util.Arrays.stream(argsField.getText().split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
                LaunchProfile newProfile = new LaunchProfile(
                        UUID.randomUUID().toString(), nameField.getText().trim(), "User-defined profile.",
                        false, "python", args, new HashMap<>()
                );
                List<LaunchProfile> profiles = profileManager.loadProfiles();
                profiles.add(newProfile);
                try {
                    profileManager.saveProfiles(profiles);
                    refreshProfileList(profileListModel);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(parentFrame, "Error: " + ex.getMessage());
                }
            }
        });

        removeProfileBtn.addActionListener(e -> {
            LaunchProfile selected = profileList.getSelectedValue();
            if (selected != null) {
                if ("default".equals(selected.id())) {
                    JOptionPane.showMessageDialog(parentFrame, "Cannot delete default profiles.");
                    return;
                }
                int confirm = JOptionPane.showConfirmDialog(parentFrame, "Delete profile '" + selected.name() + "'?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    List<LaunchProfile> profiles = profileManager.loadProfiles();
                    profiles.removeIf(p -> p.id().equals(selected.id()));
                    try {
                        profileManager.saveProfiles(profiles);
                        refreshProfileList(profileListModel);
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(parentFrame, "Error: " + ex.getMessage());
                    }
                }
            }
        });

        // CENTER: Overview, Controls, Versions & Console
        GlassPanel rightPanel = new GlassPanel();
        rightPanel.setLayout(new GridBagLayout());
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
            LaunchProfile selected = profileList != null ? profileList.getSelectedValue() : null;
            if (selected != null && startComfyHandler != null) {
                configService.setActiveProfile(selected.id());
                startComfyHandler.accept(selected);
                updateStatusUI();
            } else {
                JOptionPane.showMessageDialog(parentFrame, "Please select a launch profile first.");
            }
        });

        restartBtn.addActionListener(e -> {
            if (fullServiceRestartHandler != null) {
                fullServiceRestartHandler.run();
                updateStatusUI();
            }
        });

        stopBtn.addActionListener(e -> {
            if (lifecycleService != null) {
                lifecycleService.stop();
                if (comfyConsoleArea != null) {
                    comfyConsoleArea.append("\n[Companion] ComfyUI process stopped by user.\n");
                }
                updateStatusUI();
            }
        });

        browserBtn.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(URI.create(configService.getComfyUIUrl()));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(parentFrame, "Could not open browser: " + ex.getMessage());
            }
        });

        bootstrapBtn.addActionListener(e -> {
            if (openBootstrapHandler != null) {
                openBootstrapHandler.run();
            }
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
        comfyConsoleArea = new JTextArea();
        Color consoleBg = UIManager.getColor("TextArea.background");
        Color consoleFg = UIManager.getColor("TextArea.foreground");
        comfyConsoleArea.setBackground(consoleBg != null ? consoleBg : new Color(15, 17, 26));
        comfyConsoleArea.setForeground(consoleFg != null ? consoleFg : new Color(220, 220, 220));
        comfyConsoleArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        comfyConsoleArea.setEditable(false);
        comfyConsoleArea.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane consoleScroll = new JScrollPane(comfyConsoleArea) {
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
        clearConsoleBtn.addActionListener(e -> {
            if (lifecycleService != null && lifecycleService.isRunning()) {
                if (comfyConsoleArea != null) comfyConsoleArea.setText("");
            } else {
                showInitialConsoleState(profileList != null ? profileList.getSelectedValue() : null);
            }
        });

        consoleToolbar.add(logSearchField, BorderLayout.CENTER);
        consoleToolbar.add(clearConsoleBtn, BorderLayout.EAST);

        consoleContainer.add(consoleToolbar, BorderLayout.NORTH);
        consoleContainer.add(consoleScroll, BorderLayout.CENTER);

        logSearchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { highlight(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { highlight(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { highlight(); }

            private void highlight() {
                Highlighter h = comfyConsoleArea.getHighlighter();
                h.removeAllHighlights();
                String text = logSearchField.getText();
                if (text == null || text.trim().isEmpty()) return;
                String content = comfyConsoleArea.getText();
                if (content == null || content.isEmpty()) return;

                String pattern = text.toLowerCase();
                String lowerContent = content.toLowerCase();
                int index = lowerContent.indexOf(pattern);

                Highlighter.HighlightPainter painter = 
                        new DefaultHighlighter.DefaultHighlightPainter(new Color(30, 190, 170, 120));
                while (index >= 0) {
                    try {
                        h.addHighlight(index, index + pattern.length(), painter);
                    } catch (BadLocationException ex) {
                        // ignore out of bounds exceptions
                    }
                    index = lowerContent.indexOf(pattern, index + pattern.length());
                }
            }
        });

        gbc.gridy = 6; gbc.weighty = 1.0; gbc.insets = new Insets(5, 5, 5, 5);
        rightPanel.add(consoleContainer, gbc);

        // EAST: Hardware stats & Quick actions
        GlassPanel rightPanelEast = new GlassPanel();
        rightPanelEast.setLayout(new GridBagLayout());
        rightPanelEast.setPreferredSize(new Dimension(340, 0));
        rightPanelEast.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        GridBagConstraints eastGbc = new GridBagConstraints();
        eastGbc.fill = GridBagConstraints.HORIZONTAL;
        eastGbc.weightx = 1.0;
        eastGbc.gridx = 0; eastGbc.gridy = 0;
        eastGbc.insets = new Insets(5, 5, 15, 5);

        JLabel statsHeader = new JLabel("System Stats");
        statsHeader.putClientProperty("FlatLaf.styleClass", "h3");
        rightPanelEast.add(statsHeader, eastGbc);

        eastGbc.gridy++;
        eastGbc.insets = new Insets(5, 5, 8, 5);
        progressCpu = new JProgressBar(0, 100);
        rightPanelEast.add(createSlimStatPanel("CPU Load", progressCpu), eastGbc);

        eastGbc.gridy++;
        progressRam = new JProgressBar(0, 100);
        rightPanelEast.add(createSlimStatPanel("RAM Usage", progressRam), eastGbc);

        eastGbc.gridy++;
        gpuPanel = new JPanel(new GridBagLayout());
        gpuPanel.setOpaque(false);

        GridBagConstraints gpuGbc = new GridBagConstraints();
        gpuGbc.fill = GridBagConstraints.HORIZONTAL;
        gpuGbc.weightx = 1.0;
        gpuGbc.gridx = 0; gpuGbc.gridy = 0;
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

        eastGbc.gridy++;
        eastGbc.insets = new Insets(15, 5, 15, 5);
        rightPanelEast.add(new JSeparator(), eastGbc);

        eastGbc.gridy++;
        eastGbc.insets = new Insets(5, 5, 15, 5);
        JLabel actionsHeader = new JLabel("Quick Actions");
        actionsHeader.putClientProperty("FlatLaf.styleClass", "h3");
        rightPanelEast.add(actionsHeader, eastGbc);

        eastGbc.gridy++;
        eastGbc.insets = new Insets(6, 5, 6, 5);
        JButton updateBtn = new JButton("Update ComfyUI & Nodes", SvgIconFactory.get(AppIcon.DOWNLOAD));
        updateBtn.putClientProperty("JButton.buttonType", "roundRect");
        updateBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        updateBtn.setPreferredSize(new Dimension(0, 35));
        updateBtn.addActionListener(e -> {
            AutoUpdaterDialog dialog = new AutoUpdaterDialog(parentFrame, updaterService);
            dialog.setLocationRelativeTo(parentFrame);
            dialog.setVisible(true);
        });
        rightPanelEast.add(updateBtn, eastGbc);

        eastGbc.gridy++;
        JButton checkUpdatesBtn = new JButton("Check Model Upgrades", SvgIconFactory.get(AppIcon.SEARCH));
        checkUpdatesBtn.putClientProperty("JButton.buttonType", "roundRect");
        checkUpdatesBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        checkUpdatesBtn.setPreferredSize(new Dimension(0, 35));
        checkUpdatesBtn.addActionListener(e -> {
            ModelUpdateCheckerDialog dialog = new ModelUpdateCheckerDialog(
                    parentFrame, configService, hashRegistry, modelValidator, civitaiService, downloadManager
            );
            dialog.setLocationRelativeTo(parentFrame);
            dialog.setVisible(true);
        });
        rightPanelEast.add(checkUpdatesBtn, eastGbc);

        eastGbc.gridy++;
        JButton storageOptBtn = new JButton("Storage Optimizer", SvgIconFactory.get(AppIcon.STORAGE_OPTIMIZER));
        storageOptBtn.putClientProperty("JButton.buttonType", "roundRect");
        storageOptBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        storageOptBtn.setPreferredSize(new Dimension(0, 35));
        storageOptBtn.addActionListener(e -> {
            DeduplicationDialog dialog = new DeduplicationDialog(
                    parentFrame, configService, modelValidator, hashRegistry
            );
            dialog.setLocationRelativeTo(parentFrame);
            dialog.setVisible(true);
        });
        rightPanelEast.add(storageOptBtn, eastGbc);

        eastGbc.gridy++;
        eastGbc.weighty = 1.0;
        eastGbc.fill = GridBagConstraints.BOTH;
        rightPanelEast.add(Box.createGlue(), eastGbc);

        panel.add(leftPanel, BorderLayout.WEST);
        panel.add(rightPanel, BorderLayout.CENTER);
        panel.add(rightPanelEast, BorderLayout.EAST);

        refreshProfileList(profileListModel);

        statusTimer = new Timer(1000, e -> updateStatusUI());
        statusTimer.start();
        updateStatusUI();

        return panel;
    }

    /**
     * Updates the status text and buttons depending on whether ComfyUI is currently starting, running, or offline.
     */
    public void updateStatusUI() {
        boolean running = lifecycleService != null && lifecycleService.isRunning();
        boolean starting = false;
        if (running) {
            if (lifecycleService.isProcessAlive() && !lifecycleService.isGuiLineShown()) {
                starting = true;
            }
        }

        if (statusDisplay != null) {
            if (running) {
                if (starting) {
                    statusDisplay.setText("Status: Starting");
                    statusDisplay.setForeground(new Color(255, 204, 0));
                } else {
                    statusDisplay.setText("Status: Running");
                    statusDisplay.setForeground(new Color(30, 190, 170));
                }
            } else {
                statusDisplay.setText("Status: Offline");
                statusDisplay.setForeground(Color.GRAY);
            }
        }

        if (launchBtn != null) launchBtn.setEnabled(!running);
        if (restartBtn != null) restartBtn.setEnabled(running);
        if (stopBtn != null) stopBtn.setEnabled(running);
        if (browserBtn != null) browserBtn.setEnabled(running);
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

    public void refreshProfileList(DefaultListModel<LaunchProfile> model) {
        model.clear();
        List<LaunchProfile> profiles = profileManager.loadProfiles();

        if (profiles.isEmpty()) {
            LaunchProfile def = new LaunchProfile(
                    "default",
                    "Default (Local)",
                    "Starts ComfyUI with standard local settings (--listen 127.0.0.1).",
                    false, "python",
                    List.of("--listen", "127.0.0.1", "--port", "8188"), new HashMap<>()
            );
            profiles.add(def);
            try {
                profileManager.saveProfiles(profiles);
            } catch (IOException ignored) {}
        }

        for (LaunchProfile p : profiles) {
            model.addElement(p);
        }

        String activeId = configService.getActiveProfile();
        int targetIdx = -1;
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i).id().equals(activeId)) {
                targetIdx = i;
                break;
            }
        }
        if (targetIdx == -1 && !model.isEmpty()) {
            targetIdx = 0;
        }
        if (profileList != null && targetIdx != -1) {
            profileList.setSelectedIndex(targetIdx);
            updateSelectedProfileInfo(model.get(targetIdx));
        }
    }

    /**
     * Updates the header description and initial console banner when a launch profile is selected.
     *
     * @param selected the selected {@link LaunchProfile}
     */
    public void updateSelectedProfileInfo(LaunchProfile selected) {
        if (selected == null) return;
        if (descLabel != null) {
            String desc = selected.description();
            if (desc == null || desc.isBlank()) {
                desc = "No description provided for this profile.";
            }
            descLabel.setText("<html><body style='width: 500px;'>" + desc + "</body></html>");
        }
        if (lifecycleService == null || !lifecycleService.isRunning()) {
            showInitialConsoleState(selected);
        }
    }

    /**
     * Populates the console text area with an informative ready banner when offline.
     *
     * @param profile the active {@link LaunchProfile}
     */
    public void showInitialConsoleState(LaunchProfile profile) {
        if (comfyConsoleArea == null) return;
        String profileName = profile != null ? profile.name() : "Standard Mode (Default)";
        String args = profile != null && profile.cliArguments() != null && !profile.cliArguments().isEmpty()
                ? String.join(" ", profile.cliArguments())
                : "--listen 127.0.0.1 --port 8188";

        StringBuilder sb = new StringBuilder();
        sb.append("========================================================================================\n");
        sb.append("  ComfyUI Companion | Live Process Console\n");
        sb.append("========================================================================================\n");
        sb.append("  Status        : Offline\n");
        sb.append("  Active Profile: ").append(profileName).append("\n");
        sb.append("  Arguments     : ").append(args).append("\n\n");
        sb.append("  Ready to launch. Click 'Launch' above to launch the ComfyUI server.\n");
        sb.append("========================================================================================\n");

        comfyConsoleArea.setText(sb.toString());
        comfyConsoleArea.setCaretPosition(0);
    }

    /**
     * Queries and refreshes installed and remote ComfyUI and Python versions.
     *
     * @param versionService service resolving version strings
     * @param configService service providing installation directory paths
     */
    public void refreshVersions(VersionService versionService, ConfigService configService) {
        if (versionService == null || configService == null) return;
        String localComfy = versionService.getInstalledComfyVersion(configService.getComfyUIPath());
        String localPython = versionService.getInstalledPythonVersion(configService.getPythonPath());
        String remotePython = versionService.getRemotePythonVersion();

        if (lblComfyVersion != null) lblComfyVersion.setText("ComfyUI: " + localComfy + " (Remote: Fetching...)");
        if (lblPythonVersion != null) lblPythonVersion.setText("Python: " + localPython + " (Latest: " + remotePython + ")");

        versionService.getRemoteComfyVersionAsync().thenAccept(remoteComfy -> {
            SwingUtilities.invokeLater(() -> {
                if (lblComfyVersion != null) {
                    lblComfyVersion.setText("ComfyUI: " + localComfy + " (Remote: " + remoteComfy + ")");
                }
            });
        });
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
     * Gets the profile selection list.
     *
     * @return the profile list component
     */
    public JList<LaunchProfile> getProfileList() { return profileList; }

    /**
     * Gets the process console text area.
     *
     * @return the console text area
     */
    public JTextArea getComfyConsoleArea() { return comfyConsoleArea; }

    /**
     * Gets the status display label.
     *
     * @return the status label
     */
    public JLabel getStatusDisplay() { return statusDisplay; }

    /**
     * Gets the profile description label.
     *
     * @return the description label
     */
    public JLabel getDescLabel() { return descLabel; }

    /**
     * Gets the launch action button.
     *
     * @return the launch button
     */
    public JButton getLaunchBtn() { return launchBtn; }

    /**
     * Gets the restart action button.
     *
     * @return the restart button
     */
    public JButton getRestartBtn() { return restartBtn; }

    /**
     * Gets the stop action button.
     *
     * @return the stop button
     */
    public JButton getStopBtn() { return stopBtn; }

    /**
     * Gets the open browser action button.
     *
     * @return the browser button
     */
    public JButton getBrowserBtn() { return browserBtn; }

    /**
     * Gets the setup configuration button.
     *
     * @return the setup button
     */
    public JButton getBootstrapBtn() { return bootstrapBtn; }

    /**
     * Gets the console search input field.
     *
     * @return the search text field
     */
    public JTextField getLogSearchField() { return logSearchField; }

    /**
     * Gets the ComfyUI version label.
     *
     * @return the ComfyUI version label
     */
    public JLabel getLblComfyVersion() { return lblComfyVersion; }

    /**
     * Gets the Python version label.
     *
     * @return the Python version label
     */
    public JLabel getLblPythonVersion() { return lblPythonVersion; }

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
}
