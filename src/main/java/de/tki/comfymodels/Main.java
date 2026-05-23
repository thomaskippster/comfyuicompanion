package de.tki.comfymodels;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.IDownloadManager;
import de.tki.comfymodels.service.IModelAnalyzer;
import de.tki.comfymodels.service.IWorkflowService;
import de.tki.comfymodels.service.IModelValidator;
import de.tki.comfymodels.service.IModelSearchService;
import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.GeminiAIService;
import de.tki.comfymodels.service.impl.ModelListService;
import de.tki.comfymodels.service.impl.ModelHashRegistry;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.event.TableModelListener;
import javax.swing.event.TableModelEvent;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;

@Component
public class Main extends JFrame {
    private final IModelAnalyzer analyzer;
    private final IDownloadManager downloadManager;
    private final IWorkflowService workflowService;
    private final IModelSearchService searchService;
    private final IModelValidator modelValidator;
    private final de.tki.comfymodels.service.impl.RestBridgeService restBridge;
    private final de.tki.comfymodels.service.impl.ArchiveService archiveService;
    private final de.tki.comfymodels.service.IComfyLifecycleService lifecycleService;
    private final de.tki.comfymodels.service.impl.ComfyDiagnosticService diagnosticService;
    private final de.tki.comfymodels.service.impl.ProfileManager profileManager;
    private final de.tki.comfymodels.service.impl.EnvironmentBootstrapperImpl bootstrapper;
    private final de.tki.comfymodels.service.impl.ComfyProcessController processController;
    private final de.tki.comfymodels.service.impl.CivitaiService civitaiService;
    private final de.tki.comfymodels.service.impl.HuggingFaceService huggingFaceService;
    private JLabel lblComfyVersion;
    private JLabel lblPythonVersion;
    private JLabel lifecycleStatusLabel;

    @Autowired
    private ConfigService configService;

    @Autowired
    private GeminiAIService geminiService;

    @Autowired
    private ModelListService modelListService;

    @Autowired
    private ModelHashRegistry hashRegistry;

    @Autowired
    private de.tki.comfymodels.service.impl.LocalModelScanner localScanner;

    @Autowired
    private de.tki.comfymodels.service.impl.PathResolver pathResolver;

    @Autowired
    private de.tki.comfymodels.service.impl.VersionService versionService;



    @Autowired
    private de.tki.comfymodels.service.impl.HardwareMonitorService hardwareMonitorService;

    @Autowired
    private de.tki.comfymodels.service.impl.UpdaterService updaterService;

    private JProgressBar progressCpu;
    private JProgressBar progressRam;
    private JProgressBar progressGpu;
    private JProgressBar progressVram;
    private JLabel lblGpuName;
    private JPanel gpuPanel;

    private JCheckBox backgroundCheck;
    private JCheckBox shutdownCheck;
    private JCheckBox restartCheck;
    private JCheckBox darkCheck;
    private JCheckBox fastHashCheck;
    private JLabel activeAiModelLabel;
    private JTextArea jsonInputArea;
    private JTextArea consoleOutput; 
    private DefaultTableModel tableModel;
    private JButton launchBtn;
    private JList<de.tki.comfymodels.domain.LaunchProfile> profileList;
    private JLabel statusLabel;
    private JButton downloadButton, pauseButton, stopButton;
    private List<ModelInfo> modelsToDownload;
    private String currentFileName = "input.json";
    private Image appIcon;

    private JTabbedPane mainTabs;
    private JPanel geminiGeneratorPanel;
    private boolean isGeminiTabAdded = false;
    private Boolean isGeminiGenerationSuccessful = null;
    private boolean isCheckingGemini = false;
    private String lastCheckedGeminiKey = null;
    private byte[] selectedImageBytes = null;
    private String selectedImageMimeType = null;
    private JLabel inputImagePreviewLabel;
    private JLabel selectedImageFileLabel;
    private JTextArea promptArea;
    private JPanel resultsContainer;
    private JProgressBar generatorProgressBar;
    private JButton btnGenerateImage;
    private JButton btnClearImage;

    public Main(IModelAnalyzer analyzer, IDownloadManager downloadManager,
                IWorkflowService workflowService, IModelSearchService searchService,
                IModelValidator modelValidator, de.tki.comfymodels.service.impl.RestBridgeService restBridge,   
                de.tki.comfymodels.service.impl.ArchiveService archiveService,
                de.tki.comfymodels.service.IComfyLifecycleService lifecycleService,
                de.tki.comfymodels.service.impl.ComfyDiagnosticService diagnosticService,
                de.tki.comfymodels.service.impl.ProfileManager profileManager,
                de.tki.comfymodels.service.impl.EnvironmentBootstrapperImpl bootstrapper,
                de.tki.comfymodels.service.impl.ComfyProcessController processController,
                de.tki.comfymodels.service.impl.CivitaiService civitaiService,
                de.tki.comfymodels.service.impl.HuggingFaceService huggingFaceService) {
        this.analyzer = analyzer;
        this.downloadManager = downloadManager;
        this.workflowService = workflowService;
        this.searchService = searchService;
        this.modelValidator = modelValidator;
        this.restBridge = restBridge;
        this.archiveService = archiveService;
        this.lifecycleService = lifecycleService;
        this.lifecycleService.setOnBrowserLaunched(() -> {
            SwingUtilities.invokeLater(() -> {
                if (mainTabs != null) {
                    for (int i = 0; i < mainTabs.getTabCount(); i++) {
                        if (mainTabs.getTitleAt(i).contains("Model Manager")) {
                            mainTabs.setSelectedIndex(i);
                            break;
                        }
                    }
                }
            });
        });
        this.diagnosticService = diagnosticService;
        this.profileManager = profileManager;
        this.bootstrapper = bootstrapper;
        this.processController = processController;
        this.civitaiService = civitaiService;
        this.huggingFaceService = huggingFaceService;
    }
    public void launch(String[] args) {
        // Initialize ProfileManager with app storage directory
        Path appData = Paths.get(System.getProperty("user.home"), ".comfyui-companion");
        try { Files.createDirectories(appData); } catch (Exception ignored) {}
        profileManager.init(appData);

        if (de.tki.comfymodels.util.PlatformUtils.isMac()) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("apple.awt.application.name", "ComfyUI Companion");
            System.setProperty("apple.awt.application.appearance", "system");
        }

        // Initialize REST Bridge consumer EARLY
        restBridge.setWorkflowConsumer(workflowJson -> {
            SwingUtilities.invokeLater(() -> {
                System.out.println("[Main] WorkflowConsumer triggered - bringing to front.");
                if (jsonInputArea != null) {
                    jsonInputArea.setText(workflowJson);
                    currentFileName = "remote_workflow.json";
                    analyzeJsonContent();
                    searchMissingOnline();
                }
                setVisible(true);
                setExtendedState(JFrame.NORMAL);
                toFront();
                requestFocus();
            });
        });
        
        // Load settings to get/generate the API Token
        if (configService.isUnlocked()) {
            restBridge.setApiToken(configService.getApiToken());
        }
        restBridge.startServer();

        // Ensure server stops on exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            processController.stop();
            downloadManager.stop();
            if (hardwareMonitorService != null) {
                hardwareMonitorService.stop();
            }
        }));

        if (!promptForPassword()) {
            System.exit(0);
        }

        // Apply theme before UI initialization
        setupTheme(configService.isDarkMode());
        
        SwingUtilities.invokeLater(() -> {
            try {
                initUI();
                setupTrayIcon();
                loadSettingsIntoUI(); 
                updateAiModelDisplay();
                
                if (hardwareMonitorService != null) {
                    hardwareMonitorService.start(stats -> SwingUtilities.invokeLater(() -> updateHardwareUI(stats)));
                }
                
                // Add WindowListener to handle background mode
                addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosing(java.awt.event.WindowEvent e) {
                        if (configService.isBackgroundModeEnabled()) {
                            setVisible(false);
                        } else {
                            processController.stop();
                            downloadManager.stop();
                            if (hardwareMonitorService != null) {
                                hardwareMonitorService.stop();
                            }
                            System.exit(0);
                        }
                    }
                });
                
                setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Critical UI Error: " + e.getMessage());
            }
        });
    }

    private void setupTheme(boolean darkMode) {
        try {
            // Global arcs for a modern feel
            UIManager.put("Button.arc", 8);
            UIManager.put("Component.arc", 8);
            UIManager.put("TextComponent.arc", 8);
            UIManager.put("ProgressBar.arc", 8);
            UIManager.put("TitlePane.unifiedBackground", true);

            if (darkMode) {
                // ComfyUI Dark Palette - using ColorUIResource to play nice with L&F
                Color nodeBg = new javax.swing.plaf.ColorUIResource(43, 43, 43); 
                Color comfySurface = new javax.swing.plaf.ColorUIResource(32, 32, 32); 
                Color comfyAccent = new javax.swing.plaf.ColorUIResource(255, 204, 0); 
                Color comfyText = new javax.swing.plaf.ColorUIResource(204, 204, 204); 
                Color comfyBorder = new javax.swing.plaf.ColorUIResource(55, 55, 55);

                UIManager.put("DefaultBackgroundColor", comfySurface);
                UIManager.put("Panel.background", nodeBg);
                UIManager.put("Table.background", comfySurface);
                UIManager.put("TextArea.background", comfySurface);
                UIManager.put("TextField.background", new javax.swing.plaf.ColorUIResource(40, 40, 40));
                UIManager.put("PasswordField.background", new javax.swing.plaf.ColorUIResource(40, 40, 40));
                
                UIManager.put("Label.foreground", comfyText);
                UIManager.put("Table.foreground", comfyText);
                UIManager.put("TextArea.foreground", comfyText);
                
                UIManager.put("Table.selectionBackground", new javax.swing.plaf.ColorUIResource(new Color(255, 204, 0, 60))); 
                UIManager.put("Table.selectionForeground", Color.WHITE);
                UIManager.put("Component.focusedBorderColor", comfyAccent);
                UIManager.put("Separator.foreground", comfyBorder);
                
                UIManager.put("Button.background", new javax.swing.plaf.ColorUIResource(50, 50, 50));
                UIManager.put("Button.foreground", comfyText);
                UIManager.put("Button.focusedBackground", new javax.swing.plaf.ColorUIResource(58, 117, 196)); 
                UIManager.put("Button.hoverBackground", new javax.swing.plaf.ColorUIResource(70, 130, 210));
                UIManager.put("Button.pressedBackground", new javax.swing.plaf.ColorUIResource(30, 30, 30));
                UIManager.put("Button.borderColor", comfyBorder);
                
                UIManager.put("ScrollBar.track", comfySurface);
                UIManager.put("ScrollBar.thumb", new javax.swing.plaf.ColorUIResource(70, 70, 70));
                
                UIManager.put("TabbedPane.selectedBackground", new javax.swing.plaf.ColorUIResource(58, 117, 196));
                UIManager.put("TabbedPane.selectedForeground", Color.WHITE);
                
                UIManager.setLookAndFeel(new FlatDarkLaf());
            } else {
                // EXHAUSTIVE cleanup of custom overrides
                String[] keysToClear = {
                    "DefaultBackgroundColor", "Panel.background", "Table.background", "TextArea.background",
                    "TextField.background", "PasswordField.background", "Label.foreground",
                    "Table.foreground", "TextArea.foreground", "Table.selectionBackground",
                    "Table.selectionForeground", "Component.focusedBorderColor", "Separator.foreground",
                    "Button.background", "Button.foreground", "Button.focusedBackground",
                    "Button.hoverBackground", "Button.pressedBackground", "Button.borderColor",
                    "ScrollBar.track", "ScrollBar.thumb", "TabbedPane.selectedBackground",
                    "TabbedPane.selectedForeground"
                };
                for (String key : keysToClear) {
                    UIManager.put(key, null);
                }
                UIManager.setLookAndFeel(new FlatLightLaf());
            }
            
            // Re-apply global arcs which might be cleared by setLookAndFeel
            UIManager.put("Button.arc", 8);
            UIManager.put("Component.arc", 8);
            UIManager.put("TextComponent.arc", 8);
            UIManager.put("ProgressBar.arc", 8);
            
            FlatLaf.updateUI();
            SwingUtilities.updateComponentTreeUI(this);
        } catch (Exception e) {
            System.err.println("Theme setup failed: " + e.getMessage());
        }
    }

    private void loadIcon() {
        try (InputStream is = getClass().getResourceAsStream("/icons/app_icon.jpg")) {
            if (is != null) {
                appIcon = javax.imageio.ImageIO.read(is);
                if (appIcon != null) {
                    setIconImage(appIcon);
                }
            }
        } catch (IOException e) {
            System.err.println("Could not load app icon: " + e.getMessage());
        }
    }

    private void setupTrayIcon() {
        if (!de.tki.comfymodels.util.PlatformUtils.isSystemTraySupported()) {
            System.err.println("[System-Tray] Not supported on this platform (e.g. Wayland). Background mode disabled.");
            if (backgroundCheck != null) {
                backgroundCheck.setSelected(false);
                backgroundCheck.setEnabled(false);
                backgroundCheck.setToolTipText("System Tray not supported on this OS.");
                configService.setBackgroundModeEnabled(false);
            }
            return;
        }

        SystemTray tray = SystemTray.getSystemTray();
        Image trayImage = appIcon;
        
        // Fallback if appIcon failed to load
        if (trayImage == null) {
            BufferedImage fallback = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = fallback.createGraphics();
            g2.setColor(new Color(255, 204, 0));
            g2.fillRect(0, 0, 16, 16);
            g2.dispose();
            trayImage = fallback;
        }

        PopupMenu popup = new PopupMenu();
        MenuItem showItem = new MenuItem("Show UI");
        showItem.addActionListener(e -> setVisible(true));
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> performAppExit());

        popup.add(showItem);
        popup.addSeparator();
        popup.add(exitItem);

        TrayIcon trayIcon = new TrayIcon(trayImage, "ComfyUI Companion", popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> setVisible(true));

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            System.err.println("TrayIcon could not be added.");
        }
    }

    private boolean promptForPassword() {
        // Read theme before applying
        setupTheme(configService.isDarkMode());

        while (true) {
            boolean vaultExists = configService.hasVault();
            String title = vaultExists ? "Vault Unlock" : "Vault Setup";
            String promptText = vaultExists ? "Enter Vault Password (to unlock API Keys):" : "Set Vault Password (to protect API Keys):";

            de.tki.comfymodels.ui.StandardDialog dialog = new de.tki.comfymodels.ui.StandardDialog(this, title);
            JPanel content = dialog.createContentPanel();
            
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0;
            
            content.add(new JLabel(promptText), gbc);
            
            JPasswordField pf = new JPasswordField();
            gbc.gridy++;
            gbc.insets = new Insets(10, 0, 10, 0);
            content.add(pf, gbc);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton btnAction = new JButton(vaultExists ? "Unlock" : "Set Password");
            JButton btnCancel = new JButton("Cancel");
            buttonPanel.add(btnAction);
            buttonPanel.add(btnCancel);
            
            final String[] resultAction = {"CANCEL"};

            if (vaultExists) {
                JButton btnReset = new JButton("Reset Vault");
                buttonPanel.add(btnReset);
                btnReset.addActionListener(e -> {
                    resultAction[0] = "RESET";
                    dialog.dispose();
                });
            }
            
            // Action to perform on unlock/set
            Runnable doAction = () -> {
                resultAction[0] = "OK";
                dialog.setVisible(false);
            };
            
            btnAction.addActionListener(e -> doAction.run());
            pf.addActionListener(e -> doAction.run()); // This handles 'Enter' key
            btnCancel.addActionListener(e -> {
                resultAction[0] = "CANCEL";
                dialog.dispose();
            });

            dialog.add(content, BorderLayout.CENTER);
            dialog.add(buttonPanel, BorderLayout.SOUTH);
            dialog.pack();
            dialog.setLocationRelativeTo(null); 
            
            SwingUtilities.invokeLater(() -> pf.requestFocusInWindow());
            dialog.setVisible(true);

            if ("CANCEL".equals(resultAction[0])) {
                return false;
            }

            if ("RESET".equals(resultAction[0])) {
                handleVaultReset();
                continue;
            }

            // If it is "OK"
            String pass = new String(pf.getPassword());
            dialog.dispose();

            if (pass.trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Password cannot be empty.", "Validation", JOptionPane.WARNING_MESSAGE);
                continue;
            }

            try {
                configService.unlock(pass);
                configService.autoDiscoverPaths();
                restBridge.setApiToken(configService.getApiToken());
                syncBridgeFiles();
                
                if (configService.isVaultFresh()) {
                    String url = "https://raw.githubusercontent.com/Comfy-Org/ComfyUI-Manager/main/model-list.json";
                    modelListService.importFromUrl(url);
                }
                
                return true;
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Unlock Failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);        
            }
        }
    }

    private void handleVaultReset() {
        int confirm = JOptionPane.showConfirmDialog(this,
            "Warning: Resetting the vault will delete all your stored API keys.\n" +
            "This action cannot be undone. Do you want to proceed?",
            "Confirm Vault Reset", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            configService.resetVault();
        }
    }

    private JDialog createDialog(String title) {
        JDialog dialog = new JDialog(this, title, true);
        dialog.setLayout(new BorderLayout());
        return dialog;
    }

    private JPanel createContentPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));
        return panel;
    }

    private void showLifecycleDialog() {
        JDialog dialog = createDialog("ComfyUI Control");
        dialog.setSize(880, 420);
        dialog.setLocationRelativeTo(this);

        JPanel content = createContentPanel();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0;

        JLabel titleLabel = new JLabel("ComfyUI Server Status");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        content.add(titleLabel, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(25, 0, 25, 0);
        lifecycleStatusLabel = new JLabel(lifecycleService.getStatus());
        lifecycleStatusLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        lifecycleStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        content.add(lifecycleStatusLabel, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(10, 0, 20, 0);
        JPanel controlButtons = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        JButton startBtn = new JButton("▶ Start");
        startBtn.setPreferredSize(new Dimension(110, 40));
        startBtn.addActionListener(e -> {
            configService.autoDiscoverPaths();
            startComfyAndReload();
        });
        
        JButton stopBtn = new JButton("⏹ Stop");
        stopBtn.setPreferredSize(new Dimension(110, 40));
        stopBtn.addActionListener(e -> lifecycleService.stop());
        
        JButton restartBtn = new JButton("🔄 Restart");
        restartBtn.setPreferredSize(new Dimension(110, 40));
        restartBtn.addActionListener(e -> {
            configService.autoDiscoverPaths();
            new Thread(() -> {
                lifecycleService.stop();
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                startComfyAndReload();
            }).start();
        });

        JButton openBrowserBtn = new JButton("🌐 Open Interface");
        openBrowserBtn.setPreferredSize(new Dimension(150, 40));
        openBrowserBtn.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new java.net.URI(configService.getComfyUIUrl()));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Could not open browser: " + ex.getMessage());
            }
        });
        
        controlButtons.add(startBtn);
        controlButtons.add(stopBtn);
        controlButtons.add(restartBtn);
        controlButtons.add(openBrowserBtn);
        content.add(controlButtons, gbc);

        // Timer to update status
        Timer timer = new Timer(1000, e -> {
            lifecycleStatusLabel.setText(lifecycleService.getStatus());
            if (lifecycleService.isRunning()) {
                lifecycleStatusLabel.setForeground(new Color(0, 150, 0));
                startBtn.setEnabled(false);
                stopBtn.setEnabled(true);
            } else {
                lifecycleStatusLabel.setForeground(Color.RED);
                startBtn.setEnabled(true);
                stopBtn.setEnabled(false);
            }
        });
        timer.start();
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { timer.stop(); }
        });

        gbc.gridy++;
        gbc.weighty = 1.0;
        content.add(new JPanel(), gbc);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        
        JButton close = new JButton("Close");
        close.addActionListener(e -> dialog.dispose());
        
        bottom.add(close);

        dialog.add(content, BorderLayout.CENTER);
        dialog.add(bottom, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void loadSettingsIntoUI() {
        if (backgroundCheck != null) backgroundCheck.setSelected(configService.isBackgroundModeEnabled());
        if (shutdownCheck != null) shutdownCheck.setSelected(configService.isShutdownAfterDownloadEnabled());
        if (restartCheck != null) restartCheck.setSelected(configService.isRestartAfterDownloadEnabled());
        if (darkCheck != null) darkCheck.setSelected(configService.isDarkMode());
        if (fastHashCheck != null) fastHashCheck.setSelected(configService.isFastHashEnabled());
    }

    private void updateAiModelDisplay() {
        new Thread(() -> {
            String model = geminiService.discoverBestModel();
            SwingUtilities.invokeLater(() -> activeAiModelLabel.setText("Active AI: " + model));
        }).start();
    }

    private void initUI() {
        loadIcon();
        setTitle("ComfyUI Companion");
        setSize(1450, 950);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        getRootPane().putClientProperty("flatlaf.useWindowDecorations", true);
        
        this.mainTabs = new JTabbedPane();
        mainTabs.setFont(new Font("SansSerif", Font.BOLD, 13));
        mainTabs.putClientProperty("JTabbedPane.tabType", "card");
        mainTabs.putClientProperty("JTabbedPane.showTabSeparators", true);
        mainTabs.putClientProperty("JTabbedPane.tabSeparatorsFullHeight", true);
        
        // Soften the tab accent color globally for this component
        UIManager.put("TabbedPane.selectedBackground", new Color(58, 117, 196, 40)); 
        UIManager.put("TabbedPane.underlineColor", new Color(58, 117, 196));

        // TAB 1: DASHBOARD
        mainTabs.addTab("🏠 Dashboard", createDashboardPanel(mainTabs));

        // TAB 2: MODEL MANAGER
        mainTabs.addTab("📥 Model Manager", createManagerPanel(mainTabs));



        // TAB 4: GALLERY
        String outputDir = Paths.get(configService.getComfyUIPath(), "output").toString();
        mainTabs.addTab("🖼️ Output Gallery", new de.tki.comfymodels.ui.OutputGalleryPanel(outputDir));

        // TAB 5: SETTINGS
        mainTabs.addTab("⚙️ Settings", createSettingsPanel());

        updateGeminiTabVisibility();

        setContentPane(mainTabs);
    }

    private boolean isGeminiKeyConfigured() {
        String key = configService.getGeminiApiKey();
        return key != null && !key.trim().isEmpty();
    }

    private void updateGeminiTabVisibility() {
        if (mainTabs == null) return;
        boolean hasKey = isGeminiKeyConfigured();
        String currentKey = configService.getGeminiApiKey();

        if (hasKey) {
            if (lastCheckedGeminiKey == null || !lastCheckedGeminiKey.equals(currentKey)) {
                isGeminiGenerationSuccessful = null;
                lastCheckedGeminiKey = currentKey;
            }

            if (isGeminiGenerationSuccessful == null) {
                if (!isCheckingGemini) {
                    isCheckingGemini = true;
                    new Thread(() -> {
                        try {
                            // Test validation with a simple tree prompt and seed 42
                            geminiService.generateImageNanoBanana("Ein Baum", null, null, 42);
                            isGeminiGenerationSuccessful = true;
                        } catch (Exception ex) {
                            System.err.println("Gemini startup test image generation failed: " + ex.getMessage());
                            isGeminiGenerationSuccessful = false;
                        } finally {
                            isCheckingGemini = false;
                            SwingUtilities.invokeLater(this::updateGeminiTabVisibility);
                        }
                    }).start();
                }

                if (isGeminiTabAdded) {
                    if (geminiGeneratorPanel != null) {
                        mainTabs.remove(geminiGeneratorPanel);
                    }
                    isGeminiTabAdded = false;
                }
            } else if (isGeminiGenerationSuccessful) {
                if (!isGeminiTabAdded) {
                    if (geminiGeneratorPanel == null) {
                        geminiGeneratorPanel = createGeminiGeneratorPanel();
                    }
                    int settingsIndex = -1;
                    for (int i = 0; i < mainTabs.getTabCount(); i++) {
                        if (mainTabs.getTitleAt(i).contains("Settings")) {
                            settingsIndex = i;
                            break;
                        }
                    }
                    if (settingsIndex != -1) {
                        mainTabs.insertTab("🎨 Gemini Generator", null, geminiGeneratorPanel, "Bilder mit Gemini 3 über Gemini erzeugen", settingsIndex);
                    } else {
                        mainTabs.addTab("🎨 Gemini Generator", geminiGeneratorPanel);
                    }
                    isGeminiTabAdded = true;
                }
            } else {
                if (isGeminiTabAdded) {
                    if (geminiGeneratorPanel != null) {
                        mainTabs.remove(geminiGeneratorPanel);
                    }
                    isGeminiTabAdded = false;
                }
            }
        } else {
            isGeminiGenerationSuccessful = null;
            lastCheckedGeminiKey = null;
            if (isGeminiTabAdded) {
                if (geminiGeneratorPanel != null) {
                    mainTabs.remove(geminiGeneratorPanel);
                }
                isGeminiTabAdded = false;
            }
        }
    }

    private JPanel createGeminiGeneratorPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel controlsPanel = new JPanel();
        controlsPanel.setLayout(new BoxLayout(controlsPanel, BoxLayout.Y_AXIS));
        controlsPanel.putClientProperty("FlatLaf.style", "arc: 15; background: lighten($Panel.background, 2%)");
        controlsPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel titleLabel = new JLabel("🎨 Gemini Image Generator (Gemini 3.1 Flash)");
        titleLabel.putClientProperty("FlatLaf.styleClass", "h2");
        titleLabel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        controlsPanel.add(titleLabel);
        controlsPanel.add(Box.createVerticalStrut(15));

        JLabel lblPrompt = new JLabel("Prompt (Bildbeschreibung):");
        lblPrompt.putClientProperty("FlatLaf.styleClass", "h4");
        lblPrompt.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        controlsPanel.add(lblPrompt);
        controlsPanel.add(Box.createVerticalStrut(5));

        promptArea = new JTextArea(4, 40);
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        promptArea.setFont(new Font("SansSerif", Font.PLAIN, 14));
        promptArea.setToolTipText("Beschreibe das Bild, das du generieren möchtest.");
        JScrollPane promptScroll = new JScrollPane(promptArea);
        promptScroll.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        controlsPanel.add(promptScroll);
        controlsPanel.add(Box.createVerticalStrut(15));

        JLabel lblInputImage = new JLabel("Referenzbild (Optional - Image-to-Image):");
        lblInputImage.putClientProperty("FlatLaf.styleClass", "h4");
        lblInputImage.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        controlsPanel.add(lblInputImage);
        controlsPanel.add(Box.createVerticalStrut(5));

        JPanel imgButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        imgButtonsPanel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        JButton btnSelectImage = new JButton("📁 Bild auswählen...");
        btnClearImage = new JButton("❌ Entfernen");
        btnClearImage.setEnabled(false);

        imgButtonsPanel.add(btnSelectImage);
        imgButtonsPanel.add(Box.createHorizontalStrut(10));
        imgButtonsPanel.add(btnClearImage);
        controlsPanel.add(imgButtonsPanel);
        controlsPanel.add(Box.createVerticalStrut(10));

        selectedImageFileLabel = new JLabel("Kein Bild ausgewählt");
        selectedImageFileLabel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        selectedImageFileLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
        controlsPanel.add(selectedImageFileLabel);
        controlsPanel.add(Box.createVerticalStrut(10));

        inputImagePreviewLabel = new JLabel();
        inputImagePreviewLabel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        inputImagePreviewLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        inputImagePreviewLabel.setPreferredSize(new Dimension(150, 150));
        inputImagePreviewLabel.setMinimumSize(new Dimension(150, 150));
        inputImagePreviewLabel.setMaximumSize(new Dimension(150, 150));
        inputImagePreviewLabel.setHorizontalAlignment(SwingConstants.CENTER);
        inputImagePreviewLabel.setVerticalAlignment(SwingConstants.CENTER);
        inputImagePreviewLabel.setText("Vorschau");
        controlsPanel.add(inputImagePreviewLabel);
        controlsPanel.add(Box.createVerticalStrut(20));

        btnGenerateImage = new JButton("🚀 Bild erzeugen");
        btnGenerateImage.setFont(new Font("SansSerif", Font.BOLD, 14));
        btnGenerateImage.putClientProperty("Button.background", new Color(255, 204, 0));
        btnGenerateImage.putClientProperty("Button.foreground", Color.BLACK);
        btnGenerateImage.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        btnGenerateImage.setMaximumSize(new Dimension(300, 45));
        controlsPanel.add(btnGenerateImage);
        controlsPanel.add(Box.createVerticalStrut(15));

        generatorProgressBar = new JProgressBar(0, 100);
        generatorProgressBar.setStringPainted(true);
        generatorProgressBar.setVisible(false);
        generatorProgressBar.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        generatorProgressBar.setMaximumSize(new Dimension(300, 25));
        controlsPanel.add(generatorProgressBar);

        btnSelectImage.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Image Files", "png", "jpg", "jpeg", "webp"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                try {
                    selectedImageBytes = Files.readAllBytes(file.toPath());
                    selectedImageFileLabel.setText(file.getName() + " (" + (selectedImageBytes.length / 1024) + " KB)");
                    
                    String name = file.getName().toLowerCase();
                    if (name.endsWith(".png")) selectedImageMimeType = "image/png";
                    else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) selectedImageMimeType = "image/jpeg";
                    else if (name.endsWith(".webp")) selectedImageMimeType = "image/webp";
                    else selectedImageMimeType = "image/png";

                    ImageIcon icon = new ImageIcon(selectedImageBytes);
                    Image img = icon.getImage().getScaledInstance(148, 148, Image.SCALE_SMOOTH);
                    inputImagePreviewLabel.setIcon(new ImageIcon(img));
                    inputImagePreviewLabel.setText("");
                    btnClearImage.setEnabled(true);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Fehler beim Laden des Bildes: " + ex.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        btnClearImage.addActionListener(e -> {
            selectedImageBytes = null;
            selectedImageMimeType = null;
            selectedImageFileLabel.setText("Kein Bild ausgewählt");
            inputImagePreviewLabel.setIcon(null);
            inputImagePreviewLabel.setText("Vorschau");
            btnClearImage.setEnabled(false);
        });

        btnGenerateImage.addActionListener(e -> startImageGeneration());

        resultsContainer = new JPanel(new GridLayout(0, 3, 20, 20));
        resultsContainer.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JScrollPane resultsScroll = new JScrollPane(resultsContainer);
        resultsScroll.putClientProperty("FlatLaf.style", "arc: 15");
        resultsScroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel leftWrapper = new JPanel(new BorderLayout());
        leftWrapper.setPreferredSize(new Dimension(350, 0));
        leftWrapper.add(controlsPanel, BorderLayout.CENTER);

        panel.add(leftWrapper, BorderLayout.WEST);
        panel.add(resultsScroll, BorderLayout.CENTER);

        return panel;
    }

    private void startImageGeneration() {
        String prompt = promptArea.getText().trim();
        if (prompt.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Bitte gib einen Prompt ein.", "Eingabe fehlt", JOptionPane.WARNING_MESSAGE);
            return;
        }

        btnGenerateImage.setEnabled(false);
        btnClearImage.setEnabled(false);
        promptArea.setEnabled(false);
        generatorProgressBar.setValue(0);
        generatorProgressBar.setString("Starte Generierung...");
        generatorProgressBar.setVisible(true);

        resultsContainer.removeAll();
        resultsContainer.revalidate();
        resultsContainer.repaint();

        new Thread(() -> {
            int totalImages = 1;
            java.util.concurrent.atomic.AtomicInteger completedCount = new java.util.concurrent.atomic.AtomicInteger(0);
            java.util.concurrent.atomic.AtomicInteger failedCount = new java.util.concurrent.atomic.AtomicInteger(0);
            
            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(totalImages);
            java.util.List<java.util.concurrent.CompletableFuture<Void>> futures = new ArrayList<>();

            for (int i = 0; i < totalImages; i++) {
                final int index = i;
                final int seed = java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 1000000);
                
                SwingUtilities.invokeLater(() -> {
                    JPanel placeholderCard = new JPanel(new BorderLayout());
                    placeholderCard.putClientProperty("FlatLaf.style", "arc: 12; background: lighten($Panel.background, 4%)");
                    placeholderCard.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                    placeholderCard.setPreferredSize(new Dimension(250, 320));
                    
                    JLabel loadingLbl = new JLabel("Generiere Bild " + (index + 1) + " (Seed: " + seed + ")...", SwingConstants.CENTER);
                    placeholderCard.add(loadingLbl, BorderLayout.CENTER);
                    
                    resultsContainer.add(placeholderCard);
                    resultsContainer.revalidate();
                    resultsContainer.repaint();
                });

                java.util.concurrent.CompletableFuture<Void> future = java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        byte[] imgBytes = geminiService.generateImageNanoBanana(prompt, selectedImageBytes, selectedImageMimeType, seed);
                        
                        java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(imgBytes);
                        BufferedImage img = javax.imageio.ImageIO.read(bais);
                        
                        SwingUtilities.invokeLater(() -> {
                            if (index < resultsContainer.getComponentCount()) {
                                JPanel card = (JPanel) resultsContainer.getComponent(index);
                                card.removeAll();
                                
                                JLabel imgLabel = new JLabel();
                                imgLabel.setHorizontalAlignment(SwingConstants.CENTER);
                                Image scaledImg = img.getScaledInstance(260, 260, Image.SCALE_SMOOTH);
                                imgLabel.setIcon(new ImageIcon(scaledImg));
                                card.add(imgLabel, BorderLayout.CENTER);
                                
                                JPanel infoPanel = new JPanel(new BorderLayout(5, 5));
                                infoPanel.setOpaque(false);
                                
                                JLabel seedLabel = new JLabel("Seed: " + seed);
                                seedLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
                                infoPanel.add(seedLabel, BorderLayout.WEST);
                                
                                JPanel actionBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
                                actionBtns.setOpaque(false);
                                
                                JButton btnSave = new JButton("Speichern");
                                btnSave.addActionListener(ev -> {
                                    JFileChooser saver = new JFileChooser();
                                    saver.setSelectedFile(new File("gemini_" + seed + ".png"));
                                    if (saver.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                                        try {
                                            Files.write(saver.getSelectedFile().toPath(), imgBytes);
                                            statusLabel.setText("Bild erfolgreich gespeichert.");
                                        } catch (Exception ex) {
                                            JOptionPane.showMessageDialog(this, "Fehler beim Speichern: " + ex.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
                                        }
                                    }
                                });
                                
                                JButton btnCopy = new JButton("Kopieren");
                                btnCopy.addActionListener(ev -> {
                                    try {
                                        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.Transferable() {
                                            @Override
                                            public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() {
                                                return new java.awt.datatransfer.DataFlavor[]{java.awt.datatransfer.DataFlavor.imageFlavor};
                                            }
                                            @Override
                                            public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor flavor) {
                                                return java.awt.datatransfer.DataFlavor.imageFlavor.equals(flavor);
                                            }
                                            @Override
                                            public Object getTransferData(java.awt.datatransfer.DataFlavor flavor) throws java.awt.datatransfer.UnsupportedFlavorException {
                                                if (isDataFlavorSupported(flavor)) return img;
                                                throw new java.awt.datatransfer.UnsupportedFlavorException(flavor);
                                            }
                                        }, null);
                                        statusLabel.setText("Bild in die Zwischenablage kopiert.");
                                    } catch (Exception ex) {
                                        JOptionPane.showMessageDialog(this, "Fehler beim Kopieren: " + ex.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
                                    }
                                });
                                
                                actionBtns.add(btnSave);
                                actionBtns.add(btnCopy);
                                infoPanel.add(actionBtns, BorderLayout.EAST);
                                
                                card.add(infoPanel, BorderLayout.SOUTH);
                                card.revalidate();
                                card.repaint();
                            }
                        });
                        completedCount.incrementAndGet();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        failedCount.incrementAndGet();
                        SwingUtilities.invokeLater(() -> {
                            if (index < resultsContainer.getComponentCount()) {
                                JPanel card = (JPanel) resultsContainer.getComponent(index);
                                card.removeAll();
                                String rawMsg = ex.getMessage();
                                if (rawMsg == null) {
                                    rawMsg = ex.toString();
                                }
                                String formattedMsg = rawMsg.replace("\n", "<br>");
                                JLabel errLabel = new JLabel("<html><body style='text-align: center; color: #ef4444; padding: 10px;'><b>Fehler bei der Generierung:</b><br><br>" + formattedMsg + "</body></html>", SwingConstants.CENTER);
                                card.add(errLabel, BorderLayout.CENTER);
                                card.revalidate();
                                card.repaint();
                            }
                        });
                    } finally {
                        int done = completedCount.get() + failedCount.get();
                        final int progress = (done * 100) / totalImages;
                        final String statusStr = "Fertig: " + completedCount.get() + " / Fehler: " + failedCount.get();
                        SwingUtilities.invokeLater(() -> {
                            generatorProgressBar.setValue(progress);
                            generatorProgressBar.setString(statusStr);
                        });
                    }
                }, executor);
                
                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            executor.shutdown();

            SwingUtilities.invokeLater(() -> {
                btnGenerateImage.setEnabled(true);
                btnClearImage.setEnabled(selectedImageBytes != null);
                promptArea.setEnabled(true);
                generatorProgressBar.setString("Generierung beendet. " + completedCount.get() + " Bilder erfolgreich.");
            });
        }).start();
    }

    private JPanel createDashboardPanel(JTabbedPane tabs) {
        JPanel panel = new JPanel(new BorderLayout(20, 20));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // LEFT: Profile List (Card-like)
        JPanel leftPanel = new JPanel(new BorderLayout(10, 10));
        leftPanel.setPreferredSize(new Dimension(320, 0));
        leftPanel.putClientProperty("FlatLaf.style", "arc: 15; background: lighten($Panel.background, 2%)");
        leftPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel profilesHeader = new JLabel("Launch Profiles");
        profilesHeader.putClientProperty("FlatLaf.styleClass", "h3");
        leftPanel.add(profilesHeader, BorderLayout.NORTH);

        DefaultListModel<de.tki.comfymodels.domain.LaunchProfile> profileListModel = new DefaultListModel<>();
        profileList = new JList<>(profileListModel);
        profileList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                de.tki.comfymodels.domain.LaunchProfile p = (de.tki.comfymodels.domain.LaunchProfile) value;
                java.awt.Component c = super.getListCellRendererComponent(list, " " + p.name(), index, isSelected, cellHasFocus);
                if (c instanceof JLabel) {
                    ((JLabel) c).setPreferredSize(new Dimension(0, 35));
                    ((JLabel) c).setFont(new Font("SansSerif", isSelected ? Font.BOLD : Font.PLAIN, 14));
                }
                return c;
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
            JTextField nameField = new JTextField();
            JTextField argsField = new JTextField("--listen, --port, 8188");
            Object[] message = { "Profile Name:", nameField, "Arguments (comma separated):", argsField };
            int option = JOptionPane.showConfirmDialog(this, message, "Add Launch Profile", JOptionPane.OK_CANCEL_OPTION);
            if (option == JOptionPane.OK_OPTION && !nameField.getText().trim().isEmpty()) {
                List<String> args = java.util.Arrays.stream(argsField.getText().split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
                de.tki.comfymodels.domain.LaunchProfile newProfile = new de.tki.comfymodels.domain.LaunchProfile(
                    java.util.UUID.randomUUID().toString(), nameField.getText().trim(), "Custom user profile.",
                    false, "python", args, new HashMap<>()
                );
                List<de.tki.comfymodels.domain.LaunchProfile> profiles = profileManager.loadProfiles();
                profiles.add(newProfile);
                try { profileManager.saveProfiles(profiles); refreshProfileList(profileListModel); } 
                catch (IOException ex) { JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage()); }
            }
        });

        removeProfileBtn.addActionListener(e -> {
            de.tki.comfymodels.domain.LaunchProfile selected = profileList.getSelectedValue();
            if (selected != null) {
                int confirm = JOptionPane.showConfirmDialog(this, "Remove profile '" + selected.name() + "'?", "Confirm", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    List<de.tki.comfymodels.domain.LaunchProfile> profiles = profileManager.loadProfiles();
                    profiles.removeIf(p -> p.id().equals(selected.id()));
                    try { profileManager.saveProfiles(profiles); refreshProfileList(profileListModel); } 
                    catch (IOException ex) { JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage()); }
                }
            }
        });

        // RIGHT: Control Center
        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.putClientProperty("FlatLaf.style", "arc: 15; background: lighten($Panel.background, 2%)");
        rightPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 15, 5);
        gbc.weightx = 1.0;

        JLabel titleLabel = new JLabel("Dashboard");
        titleLabel.putClientProperty("FlatLaf.styleClass", "h1");
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        rightPanel.add(titleLabel, gbc);

        JLabel statusDisplay = new JLabel("Status: Offline");
        statusDisplay.setFont(new Font("SansSerif", Font.BOLD, 18));
        statusDisplay.setForeground(Color.GRAY);
        gbc.gridy = 1;
        rightPanel.add(statusDisplay, gbc);

        JLabel descLabel = new JLabel("Select a profile to see details.");
        descLabel.setFont(new Font("SansSerif", Font.ITALIC, 14));
        descLabel.setForeground(Color.LIGHT_GRAY);
        gbc.gridy = 2;
        rightPanel.add(descLabel, gbc);

        profileList.addListSelectionListener(e -> {
            de.tki.comfymodels.domain.LaunchProfile selected = profileList.getSelectedValue();
            if (selected != null) {
                descLabel.setText("<html><body style='width: 500px;'>" + selected.description() + "</body></html>");
                configService.setActiveProfile(selected.id());
            } else {
                descLabel.setText("Select a profile to see details.");
            }
        });

        refreshProfileList(profileListModel);

        // One-row clean button layout (5 buttons)
        JPanel actionPanel = new JPanel(new GridLayout(1, 5, 12, 12));
        actionPanel.setOpaque(false);
        
        launchBtn = new JButton("🚀 Launch");
        launchBtn.putClientProperty("JButton.buttonType", "accent");
        launchBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        
        JButton restartBtn = new JButton("🔄 Restart");
        restartBtn.putClientProperty("JButton.buttonType", "roundRect");
        restartBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        restartBtn.setEnabled(false);

        JButton stopBtn = new JButton("⏹ Stop");
        stopBtn.putClientProperty("JButton.buttonType", "roundRect");
        stopBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        stopBtn.setEnabled(false);

        JButton browserBtn = new JButton("🌐 Browser");
        browserBtn.putClientProperty("JButton.buttonType", "roundRect");
        browserBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        browserBtn.setEnabled(false);
        browserBtn.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new java.net.URI(configService.getComfyUIUrl()));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Could not open browser: " + ex.getMessage());
            }
        });

        JButton bootstrapBtn = new JButton("🛠️ Setup");
        bootstrapBtn.putClientProperty("JButton.buttonType", "roundRect");
        bootstrapBtn.setFont(new Font("SansSerif", Font.BOLD, 14));

        actionPanel.add(launchBtn);
        actionPanel.add(restartBtn);
        actionPanel.add(stopBtn);
        actionPanel.add(browserBtn);
        actionPanel.add(bootstrapBtn);
        
        gbc.gridy = 3; gbc.gridheight = 1; gbc.insets = new Insets(20, 0, 20, 0);
        rightPanel.add(actionPanel, gbc);

        // Environment Versions section
        JPanel versionPanel = new JPanel();
        versionPanel.setOpaque(false);
        versionPanel.setLayout(new BoxLayout(versionPanel, BoxLayout.Y_AXIS));
        
        JLabel versionHeader = new JLabel("Environment Versions");
        versionHeader.putClientProperty("FlatLaf.styleClass", "h4");
        
        lblComfyVersion = new JLabel("ComfyUI: Fetching...");
        lblPythonVersion = new JLabel("Python: Fetching...");

        versionPanel.add(versionHeader);
        versionPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        versionPanel.add(lblComfyVersion);
        versionPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        versionPanel.add(lblPythonVersion);
        
        gbc.gridy = 5; gbc.insets = new Insets(10, 5, 15, 5);
        rightPanel.add(versionPanel, gbc);

        refreshVersions();

        // Console Output
        consoleOutput = new JTextArea();
        consoleOutput.setBackground(new Color(25, 25, 25));
        consoleOutput.setForeground(new Color(0, 220, 0));
        consoleOutput.setFont(new Font("Monospaced", Font.PLAIN, 13));
        consoleOutput.setEditable(false);
        consoleOutput.setMargin(new Insets(10, 10, 10, 10));
        
        JScrollPane consoleScroll = new JScrollPane(consoleOutput);
        consoleScroll.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60), 1));
        
        JPanel consoleContainer = new JPanel(new BorderLayout(5, 5));
        consoleContainer.setOpaque(false);
        
        JPanel consoleToolbar = new JPanel(new BorderLayout(5, 5));
        consoleToolbar.setOpaque(false);
        
        JTextField logSearchField = new JTextField();
        logSearchField.putClientProperty("JTextField.placeholderText", "🔍 Search console logs...");
        
        JButton clearConsoleBtn = new JButton("Clear Console");
        clearConsoleBtn.putClientProperty("JButton.buttonType", "roundRect");
        clearConsoleBtn.addActionListener(e -> consoleOutput.setText(""));
        
        consoleToolbar.add(logSearchField, BorderLayout.CENTER);
        consoleToolbar.add(clearConsoleBtn, BorderLayout.EAST);
        
        consoleContainer.add(consoleToolbar, BorderLayout.NORTH);
        consoleContainer.add(consoleScroll, BorderLayout.CENTER);

        logSearchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { highlight(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { highlight(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { highlight(); }

            private void highlight() {
                javax.swing.text.Highlighter h = consoleOutput.getHighlighter();
                h.removeAllHighlights();
                String text = logSearchField.getText();
                if (text == null || text.trim().isEmpty()) return;
                String content = consoleOutput.getText();
                if (content == null || content.isEmpty()) return;
                
                String pattern = text.toLowerCase();
                String lowerContent = content.toLowerCase();
                int index = lowerContent.indexOf(pattern);
                javax.swing.text.Highlighter.HighlightPainter painter = 
                    new javax.swing.text.DefaultHighlighter.DefaultHighlightPainter(new Color(255, 255, 100, 100));
                while (index >= 0) {
                    try {
                        h.addHighlight(index, index + pattern.length(), painter);
                    } catch (javax.swing.text.BadLocationException ex) {
                        // ignore
                    }
                    index = lowerContent.indexOf(pattern, index + pattern.length());
                }
            }
        });

        gbc.gridy = 6; gbc.weighty = 1.0; gbc.insets = new Insets(5, 5, 5, 5);
        rightPanel.add(consoleContainer, gbc);

        // Timer for status updates
        Timer statusTimer = new Timer(1000, e -> {
            boolean running = processController.isRunning();
            if (running) {
                statusDisplay.setText("Status: Running");
                statusDisplay.setForeground(new Color(0, 180, 0));
                launchBtn.setEnabled(false);
                restartBtn.setEnabled(true);
                stopBtn.setEnabled(true);
                browserBtn.setEnabled(true);
            } else {
                statusDisplay.setText("Status: Offline");
                statusDisplay.setForeground(Color.GRAY);
                launchBtn.setEnabled(true);
                restartBtn.setEnabled(false);
                stopBtn.setEnabled(false);
                browserBtn.setEnabled(false);
            }
            boolean needsSetup = configService.getComfyUIPath().isEmpty() || !new File(configService.getComfyUIPath(), "main.py").exists();
            bootstrapBtn.putClientProperty("FlatLaf.style", needsSetup ? "background: #646400; foreground: #fff" : "");
        });
        statusTimer.start();

        restartBtn.addActionListener(e -> {
            de.tki.comfymodels.domain.LaunchProfile selected = profileList.getSelectedValue();
            if (selected == null) { JOptionPane.showMessageDialog(this, "Please select a profile first."); return; }
            restartBtn.setEnabled(false);
            consoleOutput.append("\n🔄 Restarting ComfyUI...\n");
            new Thread(() -> {
                processController.stop();
                try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                SwingUtilities.invokeLater(() -> {
                    startComfyUI(selected, false);
                });
            }).start();
        });

        bootstrapBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, "This will clone ComfyUI and download Python. Proceed?", "Bootstrap", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                bootstrapBtn.setEnabled(false); launchBtn.setEnabled(false);
                consoleOutput.setText("⚙️ Starting Environment Bootstrap...\n");
                Path appRoot = Paths.get(System.getProperty("user.home"), ".comfyui-companion");
                Path comfyTarget = appRoot.resolve("ComfyUI");
                bootstrapper.downloadAndExtractPortablePython(appRoot, log -> SwingUtilities.invokeLater(() -> consoleOutput.append(log + "\n")))
                    .thenCompose(pythonPath -> {
                        configService.setPythonPath(pythonPath.toString());
                        return bootstrapper.installPip(pythonPath, log -> SwingUtilities.invokeLater(() -> consoleOutput.append(log + "\n")))
                            .thenCompose(v -> bootstrapper.cloneComfyUI(comfyTarget, log -> SwingUtilities.invokeLater(() -> consoleOutput.append(log + "\n"))))
                            .thenCompose(v -> bootstrapper.installRequirements(pythonPath, comfyTarget, log -> SwingUtilities.invokeLater(() -> consoleOutput.append(log + "\n"))));
                    }).thenRun(() -> SwingUtilities.invokeLater(() -> {
                        configService.setComfyUIPath(comfyTarget.toString()); configService.autoDiscoverPaths();
                        syncBridgeFiles();
                        consoleOutput.append("✅ Environment ready!\n");
                        bootstrapBtn.setEnabled(true); launchBtn.setEnabled(true);
                        refreshVersions(); JOptionPane.showMessageDialog(this, "Setup complete!");
                    })).exceptionally(ex -> {
                        SwingUtilities.invokeLater(() -> { consoleOutput.append("❌ Bootstrap failed: " + ex.getMessage() + "\n"); bootstrapBtn.setEnabled(true); });
                        return null;
                    });
            }
        });

        launchBtn.addActionListener(e -> {
            de.tki.comfymodels.domain.LaunchProfile selected = profileList.getSelectedValue();
            if (selected == null) { JOptionPane.showMessageDialog(this, "Please select a launch profile."); return; }
            startComfyUI(selected, true);
        });

        stopBtn.addActionListener(e -> { consoleOutput.append("\n⏹ Stopping ComfyUI process...\n"); processController.stop(); });

        // RIGHT: System Stats & Quick Actions
        JPanel rightPanelEast = new JPanel(new GridBagLayout());
        rightPanelEast.setPreferredSize(new Dimension(340, 0));
        rightPanelEast.putClientProperty("FlatLaf.style", "arc: 15; background: lighten($Panel.background, 2%)");
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
        eastGbc.insets = new Insets(5, 5, 5, 5);
        progressCpu = new JProgressBar(0, 100);
        progressCpu.setStringPainted(true);
        progressCpu.setString("CPU: --%");
        progressCpu.setPreferredSize(new Dimension(0, 24));
        rightPanelEast.add(progressCpu, eastGbc);

        // RAM
        eastGbc.gridy++;
        progressRam = new JProgressBar(0, 100);
        progressRam.setStringPainted(true);
        progressRam.setString("RAM: -- GB / -- GB");
        progressRam.setPreferredSize(new Dimension(0, 24));
        rightPanelEast.add(progressRam, eastGbc);

        // GPU Panel wrapper
        eastGbc.gridy++;
        gpuPanel = new JPanel(new GridBagLayout());
        gpuPanel.setOpaque(false);

        GridBagConstraints gpuGbc = new GridBagConstraints();
        gpuGbc.fill = GridBagConstraints.HORIZONTAL;
        gpuGbc.weightx = 1.0;
        gpuGbc.gridx = 0;
        gpuGbc.gridy = 0;
        gpuGbc.insets = new Insets(5, 0, 5, 0);

        lblGpuName = new JLabel("GPU: N/A");
        lblGpuName.setFont(new Font("SansSerif", Font.BOLD, 12));
        gpuPanel.add(lblGpuName, gpuGbc);

        gpuGbc.gridy++;
        progressGpu = new JProgressBar(0, 100);
        progressGpu.setStringPainted(true);
        progressGpu.setString("GPU Load: --%");
        progressGpu.setPreferredSize(new Dimension(0, 24));
        gpuPanel.add(progressGpu, gpuGbc);

        gpuGbc.gridy++;
        progressVram = new JProgressBar(0, 100);
        progressVram.setStringPainted(true);
        progressVram.setString("VRAM: -- GB / -- GB");
        progressVram.setPreferredSize(new Dimension(0, 24));
        gpuPanel.add(progressVram, gpuGbc);

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
            de.tki.comfymodels.ui.AutoUpdaterDialog dialog = new de.tki.comfymodels.ui.AutoUpdaterDialog(this, updaterService);
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
        });
        rightPanelEast.add(updateBtn, eastGbc);

        eastGbc.gridy++;
        JButton checkUpdatesBtn = new JButton("🔍 Check Model Upgrades");
        checkUpdatesBtn.putClientProperty("JButton.buttonType", "roundRect");
        checkUpdatesBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        checkUpdatesBtn.setPreferredSize(new Dimension(0, 35));
        checkUpdatesBtn.addActionListener(e -> {
            de.tki.comfymodels.ui.ModelUpdateCheckerDialog dialog = new de.tki.comfymodels.ui.ModelUpdateCheckerDialog(
                this, configService, hashRegistry, modelValidator, civitaiService, downloadManager
            );
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
        });
        rightPanelEast.add(checkUpdatesBtn, eastGbc);

        eastGbc.gridy++;
        JButton storageOptBtn = new JButton("🧼 Storage Optimizer");
        storageOptBtn.putClientProperty("JButton.buttonType", "roundRect");
        storageOptBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        storageOptBtn.setPreferredSize(new Dimension(0, 35));
        storageOptBtn.addActionListener(e -> {
            de.tki.comfymodels.ui.DeduplicationDialog dialog = new de.tki.comfymodels.ui.DeduplicationDialog(
                this, configService, modelValidator, hashRegistry
            );
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
        });
        rightPanelEast.add(storageOptBtn, eastGbc);

        // Add a vertical glue / weight filler
        eastGbc.gridy++;
        eastGbc.weighty = 1.0;
        eastGbc.fill = GridBagConstraints.BOTH;
        rightPanelEast.add(Box.createGlue(), eastGbc);

        panel.add(leftPanel, BorderLayout.WEST);
        panel.add(rightPanel, BorderLayout.CENTER);
        panel.add(rightPanelEast, BorderLayout.EAST);

        return panel;
    }

    private void startComfyAndReload() {
        new Thread(() -> {
            lifecycleService.start();
            // Wait for health
            for (int i = 0; i < 30; i++) {
                if (lifecycleService.isHealthy()) {
                    return;
                }
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
        }).start();
    }

    private void showSettingsMenu(JButton parent) {
        JPopupMenu menu = new JPopupMenu();
        
        JMenuItem pathsItem = new JMenuItem("📁 Directories...");
        pathsItem.addActionListener(e -> showPathsDialog());
        
        JMenuItem apiItem = new JMenuItem("🔑 AI & API Keys...");
        apiItem.addActionListener(e -> showApiKeysDialog());
        
        JMenuItem bridgeItem = new JMenuItem("🚀 ComfyUI Bridge...");
        bridgeItem.addActionListener(e -> showInstallationDialog());
        
        JMenuItem helpItem = new JMenuItem("ℹ Help...");
        helpItem.addActionListener(e -> showHelpDialog());

        JMenuItem exitItem = new JMenuItem("❌ Exit Application");
        exitItem.addActionListener(e -> performAppExit());

        menu.add(pathsItem);
        menu.add(apiItem);
        menu.addSeparator();
        menu.add(bridgeItem);
        menu.addSeparator();
        menu.add(helpItem);
        menu.add(new JSeparator());
        menu.add(exitItem);
        
        menu.show(parent, 0, parent.getHeight());
    }

    private void performAppExit() {
        lifecycleService.stop();
        downloadManager.stop();
        System.exit(0);
    }

    private void showPathsDialog() {
        de.tki.comfymodels.ui.StandardDialog dialog = new de.tki.comfymodels.ui.StandardDialog(this, "Directory Settings");
        dialog.setSize(600, 480);
        dialog.setLocationRelativeTo(this);

        JPanel panel = dialog.createContentPanel();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 5, 0);

        panel.add(new JLabel("ComfyUI Models Path (Target for downloads):"), gbc);
        
        gbc.gridy++;
        JPanel row1 = new JPanel(new BorderLayout(5, 0));
        JTextField field1 = new JTextField(configService.getModelsPath());
        JButton browse1 = new JButton("Browse...");
        browse1.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                field1.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        row1.add(field1, BorderLayout.CENTER);
        row1.add(browse1, BorderLayout.EAST);
        panel.add(row1, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(10, 0, 5, 0);
        panel.add(new JLabel("Archive Path (Offload storage):"), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        JPanel row2 = new JPanel(new BorderLayout(5, 0));
        JTextField field2 = new JTextField(configService.getArchivePath());
        JButton browse2 = new JButton("Browse...");
        browse2.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                field2.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        row2.add(field2, BorderLayout.CENTER);
        row2.add(browse2, BorderLayout.EAST);
        panel.add(row2, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(10, 0, 5, 0);
        panel.add(new JLabel("ComfyUI Main Directory (contains main.py):"), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        JPanel row3 = new JPanel(new BorderLayout(5, 0));
        JTextField field3 = new JTextField(configService.getComfyUIPath());
        JButton browse3 = new JButton("Browse...");
        row3.add(field3, BorderLayout.CENTER);
        row3.add(browse3, BorderLayout.EAST);
        panel.add(row3, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(10, 0, 5, 0);
        panel.add(new JLabel("Python Executable Path:"), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        JPanel row4 = new JPanel(new BorderLayout(5, 0));
        JTextField field4 = new JTextField(configService.getPythonPath());
        JButton browse4 = new JButton("Browse...");
        row4.add(field4, BorderLayout.CENTER);
        row4.add(browse4, BorderLayout.EAST);
        panel.add(row4, gbc);

        browse3.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                String path = chooser.getSelectedFile().getAbsolutePath();
                field3.setText(path);
                
                String discoveredPython = configService.discoverPython(path);
                if (discoveredPython != null && !discoveredPython.equals("python") && !discoveredPython.equals("python3")) {
                    field4.setText(discoveredPython);
                }
            }
        });

        browse4.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                field4.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });

        gbc.gridy++;
        gbc.insets = new Insets(10, 0, 5, 0);
        JCheckBox symlinkCheck = new JCheckBox("Use Symbolic Links on Restore (Saves SSD space)", configService.isUseSymlinksOnRestore());
        panel.add(symlinkCheck, gbc);

        gbc.gridy++;
        gbc.weighty = 1.0;
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        panel.add(spacer, gbc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dialog.dispose());
        JButton save = new JButton("Save");
        save.addActionListener(e -> {
            configService.setModelsPath(field1.getText().trim());
            configService.setArchivePath(field2.getText().trim());
            configService.setComfyUIPath(field3.getText().trim());
            configService.setPythonPath(field4.getText().trim());
            configService.setUseSymlinksOnRestore(symlinkCheck.isSelected());
            configService.setComfyLaunchCommand(""); 
            configService.autoDiscoverPaths();
            syncBridgeFiles();
            statusLabel.setText("Settings updated.");
            analyzeJsonContent();
            dialog.dispose();
        });
        buttons.add(cancel);
        buttons.add(save);

        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void showDownloadSettingsDialog() {
        de.tki.comfymodels.ui.StandardDialog dialog = new de.tki.comfymodels.ui.StandardDialog(this, "Download Settings");
        dialog.setSize(600, 380);
        dialog.setLocationRelativeTo(this);

        JPanel panel = dialog.createContentPanel();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 5, 0);

        panel.add(new JLabel("Max Parallel Downloads (Active downloads in queue):"), gbc);
        
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 15, 0);
        SpinnerNumberModel threadModel = new SpinnerNumberModel(configService.getMaxParallelDownloads(), 1, 10, 1);
        JSpinner threadSpinner = new JSpinner(threadModel);
        panel.add(threadSpinner, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        panel.add(new JLabel("Download Speed Limit (KB/s - 0 for unlimited):"), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 15, 0);
        JTextField speedField = new JTextField(String.valueOf(configService.getDownloadSpeedLimit()));
        panel.add(speedField, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        panel.add(new JLabel("Segments Per File (Multi-segment downloading, 1 to disable):"), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 15, 0);
        SpinnerNumberModel segmentModel = new SpinnerNumberModel(configService.getSegmentsPerFile(), 1, 8, 1);
        JSpinner segmentSpinner = new JSpinner(segmentModel);
        panel.add(segmentSpinner, gbc);

        gbc.gridy++;
        gbc.weighty = 1.0;
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        panel.add(spacer, gbc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dialog.dispose());
        JButton save = new JButton("Save");
        save.addActionListener(e -> {
            try {
                int threads = (Integer) threadSpinner.getValue();
                int segments = (Integer) segmentSpinner.getValue();
                int speedLimit = Integer.parseInt(speedField.getText().trim());
                if (speedLimit < 0) {
                    throw new NumberFormatException();
                }

                configService.setMaxParallelDownloads(threads);
                configService.setDownloadSpeedLimit(speedLimit);
                configService.setSegmentsPerFile(segments);

                statusLabel.setText("Download settings updated.");
                dialog.dispose();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "Please enter a valid positive integer for the speed limit.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
            }
        });
        buttons.add(cancel);
        buttons.add(save);

        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void showApiKeysDialog() {
        de.tki.comfymodels.ui.StandardDialog dialog = new de.tki.comfymodels.ui.StandardDialog(this, "AI & API Keys");
        dialog.setSize(600, 520);
        dialog.setLocationRelativeTo(this);

        JPanel panel = dialog.createContentPanel();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0;

        panel.add(new JLabel("Gemini AI API Key:"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(5, 0, 15, 0);
        JPasswordField geminiField = new JPasswordField(configService.getGeminiApiKey());
        panel.add(geminiField, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(new JLabel("Hugging Face Access Token:"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(5, 0, 15, 0);
        JPasswordField hfField = new JPasswordField(configService.getHfToken());
        panel.add(hfField, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(new JLabel("Civitai API Key:"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(5, 0, 15, 0);
        JPasswordField civitaiField = new JPasswordField(configService.getCivitaiApiKey());
        panel.add(civitaiField, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        JCheckBox ollamaCheck = new JCheckBox("Use Local Ollama LLM for Author Identification", configService.isUseOllama());
        panel.add(ollamaCheck, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 20, 5, 0);
        JPanel ollamaRow = new JPanel(new GridLayout(2, 2, 10, 5));
        ollamaRow.setOpaque(false);
        ollamaRow.add(new JLabel("Ollama Server URL:"));
        ollamaRow.add(new JLabel("Ollama Model:"));
        JTextField ollamaUrlField = new JTextField(configService.getOllamaUrl());
        JTextField ollamaModelField = new JTextField(configService.getOllamaModel());
        ollamaRow.add(ollamaUrlField);
        ollamaRow.add(ollamaModelField);
        panel.add(ollamaRow, gbc);

        ollamaUrlField.setEnabled(ollamaCheck.isSelected());
        ollamaModelField.setEnabled(ollamaCheck.isSelected());
        ollamaCheck.addActionListener(e -> {
            ollamaUrlField.setEnabled(ollamaCheck.isSelected());
            ollamaModelField.setEnabled(ollamaCheck.isSelected());
        });

        gbc.gridy++;
        gbc.weighty = 1.0;
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        panel.add(spacer, gbc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dialog.dispose());
        JButton save = new JButton("Save");
        save.addActionListener(e -> {
            configService.setGeminiApiKey(new String(geminiField.getPassword()).trim());
            configService.setHfToken(new String(hfField.getPassword()).trim());
            configService.setCivitaiApiKey(new String(civitaiField.getPassword()).trim());
            configService.setUseOllama(ollamaCheck.isSelected());
            configService.setOllamaUrl(ollamaUrlField.getText().trim());
            configService.setOllamaModel(ollamaModelField.getText().trim());
            statusLabel.setText("API keys updated.");
            updateAiModelDisplay();
            updateGeminiTabVisibility();
            analyzeJsonContent();
            dialog.dispose();
        });
        buttons.add(cancel);
        buttons.add(save);

        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void startDownloadQueue() {
        int rowCount = tableModel.getRowCount();
        if (rowCount == 0) return;
        boolean[] selected = new boolean[rowCount];
        
        for (int i = 0; i < rowCount; i++) {
            selected[i] = (Boolean) tableModel.getValueAt(i, 0);
            String url = (String) tableModel.getValueAt(i, 6);
            if (url != null && !url.equals("MISSING")) modelsToDownload.get(i).setUrl(url);
        }

        downloadButton.setEnabled(false);

        // Process archive restores FIRST as they are part of the visual queue
        new Thread(() -> {
            for (int i = 0; i < rowCount; i++) {
                if (selected[i]) {
                    String currentStatus = (String) tableModel.getValueAt(i, 7);
                    if ("📦 Archived".equals(currentStatus)) {
                        final int idx = i;
                        ModelInfo info = modelsToDownload.get(idx);
                        String folder = info.getSave_path() != null ? info.getSave_path() : (info.getType() != null ? info.getType() : de.tki.comfymodels.domain.ModelFolder.CHECKPOINTS.getDefaultFolderName());
                        String normalizedFolder = archiveService.normalizeFolder(folder);
                        
                        SwingUtilities.invokeLater(() -> tableModel.setValueAt("📦 Restoring...", idx, 7));
                        
                        long totalSize = info.getByteSize();
                        long[] currentBytes = {0};
                        long[] lastUpdate = {0};

                        boolean success = archiveService.restoreFromArchiveWithProgress(normalizedFolder, info.getName(), (delta) -> {
                            currentBytes[0] += delta;
                            long now = System.currentTimeMillis();
                            if (totalSize > 0 && (now - lastUpdate[0] > 200)) {
                                lastUpdate[0] = now;
                                int percent = (int) Math.min(100, (currentBytes[0] * 100) / totalSize);
                                SwingUtilities.invokeLater(() -> tableModel.setValueAt("📦 Restoring (" + percent + "%)...", idx, 7));
                            }
                        });
                        
                        SwingUtilities.invokeLater(() -> {
                            if (success) {
                                tableModel.setValueAt("✅ Already exists", idx, 7);
                                tableModel.setValueAt(false, idx, 0); // Unselect after restoration
                            } else {
                                tableModel.setValueAt("❌ Restore Failed - Downloading...", idx, 7);
                                // Stay selected for download fallback
                            }
                        });
                    }
                }
            }
            
            // After restores, start the actual download manager for the remaining models
            SwingUtilities.invokeLater(() -> {
                // Update selected array (some might have been unselected by restore)
                for (int i = 0; i < rowCount; i++) {
                    selected[i] = (Boolean) tableModel.getValueAt(i, 0);
                }

                downloadManager.startQueue(modelsToDownload, selected, configService.getModelsPath(),
                    (idx, status) -> SwingUtilities.invokeLater(() -> {
                        String current = (String) tableModel.getValueAt(idx, 7);
                        if (status.startsWith("Skipped") && current != null && (current.equals("✅ Already exists") || current.equals("✅ Finished"))) {
                            return; // Don't overwrite success with skip
                        }
                        tableModel.setValueAt(status, idx, 7);
                    }),
                    () -> SwingUtilities.invokeLater(() -> {
                        downloadButton.setEnabled(true);
                        statusLabel.setText("Queue finished.");
                        
                        triggerPostOperationActions();
                    })
                );
            });
        }).start();
    }

    private void showInstallationDialog() {
        JDialog dialog = new JDialog(this, "ComfyUI Bridge Installation", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(650, 360);
        dialog.setLocationRelativeTo(this);

        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 15, 0);

        // Explanation text
        JTextArea infoArea = new JTextArea(
            "This installer will set up the ComfyUI Companion bridge.\n\n" +
            "1. Select your ComfyUI main directory.\n" +
            "2. Old or conflicting bridge files will be cleaned up.\n" +
            "3. A link or copy of the UI extension will be created.\n\n" +
            "Important: Restart ComfyUI after the installation is finished."
        );
        infoArea.setEditable(false);
        infoArea.setFocusable(false);
        infoArea.setBackground(content.getBackground());
        infoArea.setLineWrap(true);
        infoArea.setWrapStyleWord(true);
        content.add(infoArea, gbc);

        // Path selection header
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        content.add(new JLabel("ComfyUI Main Directory:"), gbc);

        // Path selection row
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        JPanel pathRow = new JPanel(new BorderLayout(5, 0));
        JTextField pathField = new JTextField(configService.getComfyUIPath());
        JButton browseBtn = new JButton("Browse...");
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                pathField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        pathRow.add(pathField, BorderLayout.CENTER);
        pathRow.add(browseBtn, BorderLayout.EAST);
        content.add(pathRow, gbc);

        // Spacer to push everything to the top
        gbc.gridy++;
        gbc.weighty = 1.0;
        content.add(new JPanel(), gbc);

        // Action buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton syncBtn = new JButton("🔄 Repair / Sync Token");
        syncBtn.setToolTipText("Only updates the API token in your existing ComfyUI extension.");
        syncBtn.addActionListener(e -> {
            String selectedPath = pathField.getText().trim();
            if (selectedPath.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Please select a path first.");
                return;
            }
            configService.setComfyUIPath(selectedPath);
            syncBridgeFiles();
            JOptionPane.showMessageDialog(dialog, "API Token synchronized successfully.");
        });
        
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dialog.dispose());
        JButton installBtn = new JButton("🚀 Start Installation");
        installBtn.addActionListener(e -> {
            String selectedPath = pathField.getText().trim();
            if (selectedPath.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Please select a path first.");
                return;
            }
            installComfyUIBridge(selectedPath, dialog);
        });
        buttonPanel.add(syncBtn);
        buttonPanel.add(new JSeparator(JSeparator.VERTICAL));
        buttonPanel.add(cancelBtn);
        buttonPanel.add(installBtn);

        dialog.add(content, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void installComfyUIBridge(String comfyPath, JDialog parentDialog) {
        Path inputPath = Paths.get(comfyPath);
        if (!Files.exists(inputPath)) {
            JOptionPane.showMessageDialog(parentDialog, "The provided path does not exist: " + comfyPath, "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Systematically search for custom_nodes using PathResolver
        Path customNodesDir = pathResolver.findCustomNodes(inputPath);

        if (customNodesDir == null) {
            String msg = "Could not find 'custom_nodes' folder in the selected directory.\n\n" +
                         "Please ensure you select the folder that contains 'custom_nodes' or the main ComfyUI folder.";
            JOptionPane.showMessageDialog(parentDialog, msg, "Installation Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // --- IMPROVEMENT: THOROUGH CLEANUP ---
        // 1. Delete legacy single-file scripts that might conflict
        String[] conflictingFiles = {"comfyui_to_downloader.py", "comfyui-model-downloader.py"};
        for (String conflict : conflictingFiles) {
            Path conflictFile = customNodesDir.resolve(conflict);
            try {
                if (Files.exists(conflictFile)) {
                    Files.delete(conflictFile);
                    System.out.println("Cleaned up legacy script: " + conflictFile);
                }
            } catch (IOException ignored) {}
        }

        // 2. Search for and remove ANY folder named 'comfyui-model-downloader' to ensure a clean slate
        try (java.util.stream.Stream<Path> walk = Files.walk(customNodesDir, 2)) {
            List<Path> oldDirs = walk
                .filter(p -> Files.isDirectory(p) && (
                    p.getFileName().toString().equalsIgnoreCase("comfyui-model-downloader") || 
                    p.getFileName().toString().equalsIgnoreCase("comfyui-companion") ||
                    p.getFileName().toString().equalsIgnoreCase("comfyuicompanion") ||
                    p.getFileName().toString().equalsIgnoreCase("comfyuidownloader") ||
                    p.getFileName().toString().equalsIgnoreCase("comfymodeldownloader")
                ))
                .collect(Collectors.toList());
            
            for (Path oldDir : oldDirs) {
                System.out.println("Removing existing bridge directory: " + oldDir);
                deleteDirectory(oldDir.toFile());
            }
        } catch (IOException e) {
            System.err.println("Error during bridge cleanup: " + e.getMessage());
        }

        configService.setComfyUIPath(comfyPath);
        
        Path targetDir = customNodesDir.resolve("comfyuicompanion");
        
        try {
            Files.createDirectories(targetDir);

            // Extract from resources
            extractResource("/comfyui-bridge/__init__.py", targetDir.resolve("__init__.py").toFile());
            Path webDir = targetDir.resolve("web");
            Files.createDirectories(webDir);
            extractResource("/comfyui-bridge/web/downloader.js", webDir.resolve("downloader.js").toFile());

            // Force immediate config write with current token
            writeExtensionConfig(targetDir.toFile());
            
            if (Files.exists(targetDir.resolve("__init__.py"))) {
                String msg = "🚀 ComfyUI Bridge installed successfully!\n\n" +
                             "Location: " + targetDir.toAbsolutePath() + "\n\n" +
                             "Important:\n" +
                             "1. If ComfyUI is running, you MUST RESTART it.\n" +
                             "2. Check if a 'rocket' icon appears in the ComfyUI menu.";
                JOptionPane.showMessageDialog(parentDialog, msg, "Success", JOptionPane.INFORMATION_MESSAGE);
                parentDialog.dispose();
            } else {
                throw new Exception("Installation failed: __init__.py not found at target!");
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(parentDialog, "Critical failure during installation: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void extractResource(String resourcePath, File destination) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) throw new IOException("Resource not found: " + resourcePath);
            Files.copy(is, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private File findCustomNodesDeep(File dir, int depth) {
        if (depth > 2) return null; // Limit depth to avoid performance issues
        File[] files = dir.listFiles();
        if (files == null) return null;

        // Check immediate children first
        for (File f : files) {
            if (f.isDirectory() && f.getName().equalsIgnoreCase("custom_nodes")) {
                return f;
            }
        }

        // Recurse
        for (File f : files) {
            if (f.isDirectory() && !f.getName().startsWith(".") && !f.getName().equalsIgnoreCase("node_modules")) {
                File found = findCustomNodesDeep(f, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void writeExtensionConfig(File dir) {
        try {
            File webDir = new File(dir, "web");
            if (!webDir.exists()) webDir.mkdirs();
            JSONObject config = new JSONObject();
            config.put("token", configService.getApiToken());
            Path configFile = new File(webDir, "config.json").toPath();
            Files.writeString(configFile, config.toString(4));
            System.out.println("[Bridge-Sync] Successfully wrote config to: " + configFile.toAbsolutePath());
        } catch (Exception e) { 
            System.err.println("[Bridge-Sync] Failed to write config: " + e.getMessage());
            e.printStackTrace(); 
        }
    }

    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) deleteDirectory(f);
        dir.delete();
    }

    private void loadFile(File file) {
        try {
            currentFileName = file.getName();
            jsonInputArea.setText(workflowService.extractWorkflow(file));
            analyzeJsonContent();
        } catch (IOException ex) { JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage()); }
    }

    private void analyzeJsonContent() {
        String text = jsonInputArea.getText();
        if (text == null || text.isEmpty()) return;
        modelsToDownload = analyzer.analyze(text, currentFileName);
        tableModel.setRowCount(0);
        String base = configService.getModelsPath();
        String archive = configService.getArchivePath();
        
        for (int i = 0; i < modelsToDownload.size(); i++) {
            ModelInfo info = modelsToDownload.get(i);
            String type = info.getType() != null ? info.getType() : de.tki.comfymodels.domain.ModelFolder.CHECKPOINTS.getDefaultFolderName();
            String folder = info.getSave_path() != null ? info.getSave_path() : type;
            
            String normalizedFolder = archiveService.normalizeFolder(folder);

            // 1. Primary path check (Standard Models Path)
            Path local = "root".equals(normalizedFolder) ? Paths.get(base, info.getName()) : Paths.get(base, normalizedFolder, info.getName());
            boolean exists = Files.exists(local) && Files.isRegularFile(local);

            // 2. Primary archive check (Standard Archive Path)
            boolean inArchive = false;
            Path archivedPath = null;
            if (archive != null && !archive.trim().isEmpty()) {
                archivedPath = "root".equals(normalizedFolder) ? Paths.get(archive, info.getName()) : Paths.get(archive, normalizedFolder, info.getName());
                inArchive = Files.exists(archivedPath) && Files.isRegularFile(archivedPath);
            }
            
            boolean sizeMismatch = false;

            // 3. Robust existence and archive cross-check (Safety Guard)
            if (archive != null && !archive.isEmpty()) {
                try {
                    Path absArchive = Paths.get(archive).toAbsolutePath().normalize();
                    
                    // If 'local' is actually inside the archive, it's NOT a local active model
                    if (exists && local.toAbsolutePath().normalize().startsWith(absArchive)) {
                        exists = false;
                        inArchive = true;
                    }
                    
                    // If we found it in the primary archive location, verify size if known
                    if (inArchive && info.getByteSize() > 0) {
                        if (Files.size(archivedPath) != info.getByteSize()) {
                            inArchive = false; // Size mismatch in archive doesn't count as "found"
                        }
                    }
                } catch (Exception ignored) {}
            }

            // 4. Fallback: Recursive search in ARCHIVE if not found at primary archive location
            if (!inArchive && archive != null && !archive.isEmpty()) {
                java.util.Optional<Path> foundInArchive = localScanner.findModelWithPrefSize(Paths.get(archive), info.getName(), info.getByteSize());
                if (foundInArchive.isPresent()) {
                    archivedPath = foundInArchive.get();
                    inArchive = true;
                }
            }

            // 5. Fallback: Recursive search in LOCAL MODELS if not found OR size mismatch at primary location
            if ((!exists || sizeMismatch) && base != null && !base.isEmpty()) {
                java.util.Optional<Path> foundLocally = localScanner.findModelWithPrefSizeAndType(Paths.get(base), info.getName(), info.getByteSize(), type);
                if (foundLocally.isPresent()) {
                    Path potentialLocal = foundLocally.get();
                    try {
                        long potSize = Files.size(potentialLocal);
                        if (info.getByteSize() <= 0 || potSize == info.getByteSize()) {
                            local = potentialLocal;
                            exists = true;
                            sizeMismatch = false;
                            
                            Path root = Paths.get(base).toAbsolutePath().normalize();
                            Path absPotential = potentialLocal.toAbsolutePath().normalize();
                            
                            if (absPotential.startsWith(root)) {
                                Path rel = root.relativize(absPotential);
                                normalizedFolder = (rel.getParent() != null) ? rel.getParent().toString().replace("\\", "/") : "root";
                            } else {
                                normalizedFolder = "extra/" + potentialLocal.getParent().getFileName();
                            }
                            info.setSave_path(normalizedFolder);
                        }
                    } catch (Exception ignored) {}
                }
            }

            // 6. Final Status Determination
            String status;
            if (exists) {
                status = "✅ Already exists";
            } else if (inArchive) {
                status = "📦 Archived";
            } else if (sizeMismatch) {
                status = "🔄 Size Mismatch";
            } else if (info.getUrl().equals("MISSING")) {
                status = "Idle";
            } else {
                status = "✅ Known Good";
            }
            
            // Select for download/restore if NOT exists locally
            boolean isSelected = !exists;
            tableModel.addRow(new Object[]{isSelected, info.getType(), info.getName(), info.getSize(), info.getPopularity(), "models/" + normalizedFolder, info.getUrl(), status});
        }
        
        downloadButton.setEnabled(!modelsToDownload.isEmpty());
        
        // NEW: Automatically fetch missing sizes for remote models in background
        fetchMissingRemoteSizes();
    }

    private void fetchMissingRemoteSizes() {
        if (modelsToDownload == null) return;
        new Thread(() -> {
            for (int i = 0; i < modelsToDownload.size(); i++) {
                final int idx = i;
                ModelInfo info = modelsToDownload.get(idx);
                
                String status = (idx < tableModel.getRowCount()) ? (String) tableModel.getValueAt(idx, 7) : "";
                boolean needsCheck = "Unknown".equals(info.getSize()) || "🔄 Size Mismatch".equals(status);

                if (!info.getUrl().equals("MISSING") && needsCheck) {
                    long size = searchService.getRemoteSize(info.getUrl());
                    if (size > 0) {
                        info.setByteSize(size);
                        String formatted = searchService.formatSize(size);
                        info.setSize(formatted);
                        SwingUtilities.invokeLater(() -> {
                            if (idx < tableModel.getRowCount()) {
                                tableModel.setValueAt(formatted, idx, 3);
                                
                                // Re-verify status with new remote size info
                                String currentStatus = (String) tableModel.getValueAt(idx, 7);
                                if (currentStatus.contains("Already exists") || "🔄 Size Mismatch".equals(currentStatus) || "📦 Archived".equals(currentStatus)) {
                                    String type = info.getType() != null ? info.getType() : de.tki.comfymodels.domain.ModelFolder.CHECKPOINTS.getDefaultFolderName();
                                    String folder = info.getSave_path() != null ? info.getSave_path() : type;
                                    String base = configService.getModelsPath();
                                    String archive = configService.getArchivePath();
                                    
                                    // 1. Re-check local
                                    Path local = Paths.get(base, archiveService.normalizeFolder(folder), info.getName());
                                    if (!Files.exists(local)) {
                                        local = Paths.get(base, type, info.getName());
                                    }
                                    
                                    // If still not found at "standard" locations, try recursive local
                                    if (!Files.exists(local)) {
                                        java.util.Optional<Path> recursiveLocal = localScanner.findModelWithPrefSize(Paths.get(base), info.getName(), size);
                                        if (recursiveLocal.isPresent()) local = recursiveLocal.get();
                                    }

                                    boolean localExists = Files.exists(local);
                                    boolean localSizeMatch = false;
                                    if (localExists) {
                                        try {
                                            localSizeMatch = (Files.size(local) == size);
                                        } catch (IOException ignored) {}
                                    }

                                    // 2. Re-check archive
                                    boolean inArchive = false;
                                    if (archive != null && !archive.isEmpty()) {
                                        Path archived = Paths.get(archive, archiveService.normalizeFolder(folder), info.getName());
                                        try {
                                            if (Files.exists(archived) && Files.size(archived) == size) {
                                                inArchive = true;
                                            } else {
                                                inArchive = localScanner.findModelWithPrefSize(Paths.get(archive), info.getName(), size).isPresent();
                                            }
                                        } catch (IOException ignored) {}
                                    }

                                    String newStatus;
                                    boolean shouldSelect = false;

                                    if (localSizeMatch) {
                                        newStatus = "✅ Already exists";
                                        shouldSelect = false;
                                    } else if (localExists) {
                                        newStatus = "🔄 Size Mismatch";
                                        shouldSelect = true;
                                    } else if (inArchive) {
                                        newStatus = "📦 Archived";
                                        shouldSelect = true;
                                    } else {
                                        newStatus = "✅ Known Good";
                                        shouldSelect = true;
                                    }

                                    final String finalStatus = newStatus;
                                    final boolean finalSelect = shouldSelect;
                                    SwingUtilities.invokeLater(() -> {
                                        tableModel.setValueAt(finalStatus, idx, 7);
                                        tableModel.setValueAt(finalSelect, idx, 0);
                                    });
                                }
                            }
                        });
                    }
                }
            }
        }).start();
    }

    private void searchMissingOnline() {
        if (modelsToDownload == null) return;
        statusLabel.setText("Searching...");
        boolean[] selected = new boolean[tableModel.getRowCount()];
        for (int i = 0; i < selected.length; i++) selected[i] = (Boolean) tableModel.getValueAt(i, 0);
        searchService.searchOnline(modelsToDownload, selected, jsonInputArea.getText(), currentFileName,
            (idx, status) -> SwingUtilities.invokeLater(() -> {
                String current = (String) tableModel.getValueAt(idx, 7);
                // Protection: Don't overwrite successful detection (local or archive) with search progress/failure
                if (current != null && (current.contains("✅") || current.contains("📦"))) {
                    if (status.contains("No trusted match") || status.startsWith("🔍") || status.startsWith("✨")) {
                        return; 
                    }
                }
                tableModel.setValueAt(status, idx, 7);
            }),
            (idx, info) -> SwingUtilities.invokeLater(() -> {
                tableModel.setValueAt(info.getSize(), idx, 3);
                tableModel.setValueAt(info.getUrl(), idx, 6);
                
                String base = configService.getModelsPath();
                String archive = configService.getArchivePath();
                long size = info.getByteSize();

                // Re-verify local existence with potential new size info
                boolean exists = false;
                boolean inArchive = false;
                
                if (base != null && !base.isEmpty()) {
                    java.util.Optional<Path> foundLocally = localScanner.findModelWithPrefSize(Paths.get(base), info.getName(), size);
                    if (foundLocally.isPresent()) {
                        exists = true;
                        // Update target path display
                        try {
                            Path rel = Paths.get(base).relativize(foundLocally.get());
                            tableModel.setValueAt("models/" + rel.getParent().toString().replace("\\", "/"), idx, 5);
                        } catch (Exception ignored) {}
                    }
                }

                if (!exists && archive != null && !archive.isEmpty()) {
                    inArchive = localScanner.findModelWithPrefSize(Paths.get(archive), info.getName(), size).isPresent();
                }

                String newStatus;
                if (exists) {
                    newStatus = "✅ Already exists";
                } else if (inArchive) {
                    newStatus = "📦 Archived";
                } else {
                    newStatus = "✅ Known Good";
                }


                tableModel.setValueAt(newStatus, idx, 7);
                tableModel.setValueAt(!exists, idx, 0); // Keep selected for download/restore if NOT local
            }),
            () -> SwingUtilities.invokeLater(() -> statusLabel.setText("Search finished."))
        );
    }

    private void verifyLocalModels(boolean checkDuplicates) {
        String baseDir = configService.getModelsPath();
        if (baseDir == null || baseDir.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please set a models directory first.");
            return;
        }

        File root = new File(baseDir);
        if (!root.exists() || !root.isDirectory()) {
            JOptionPane.showMessageDialog(this, "Invalid models directory.");
            return;
        }

        String taskName = checkDuplicates ? "Verifying models & checking for duplicates" : "Verifying models (Fast Check)";
        statusLabel.setText(taskName + "... please wait.");
        new Thread(() -> {
            try {
                List<IModelValidator.ValidationResult> errors = new ArrayList<>();
                Map<String, List<Path>> hashToPaths = new HashMap<>();

                List<Path> allFiles = Files.walk(root.toPath())
                        .filter(Files::isRegularFile)
                        .filter(p -> {
                            String relPath = root.toPath().relativize(p).toString().toLowerCase();
                            return !relPath.contains(".venv") && !relPath.contains("archive");
                        })
                        .filter(p -> {
                            String n = p.getFileName().toString().toLowerCase();
                            return n.endsWith(".safetensors") || n.endsWith(".sft") || n.endsWith(".ckpt") || n.endsWith(".pth") || n.endsWith(".pt") || n.endsWith(".bin");
                        })
                        .collect(Collectors.toList());

                int total = allFiles.size();
                for (int i = 0; i < total; i++) {
                    Path p = allFiles.get(i);
                    final int current = i + 1;
                    SwingUtilities.invokeLater(() -> statusLabel.setText("Checking (" + current + "/" + total + "): " + p.getFileName()));

                    IModelValidator.ValidationResult res = modelValidator.validateFile(p.toFile());
                    if (!res.ok) {
                        errors.add(res);
                    } else if (checkDuplicates) {
                        String hash = hashRegistry.getOrCalculateHash(p.toFile());
                        if (hash != null) {
                            hashToPaths.computeIfAbsent(hash, k -> new ArrayList<>()).add(p);
                        }
                    }
                }

                Map<String, List<Path>> duplicates = checkDuplicates ? hashToPaths.entrySet().stream()
                        .filter(e -> e.getValue().size() > 1)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)) : new HashMap<>();

                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Scan finished. Found " + errors.size() + " issues and " + duplicates.size() + " duplicate sets.");

                    if (errors.isEmpty() && duplicates.isEmpty()) {
                        JOptionPane.showMessageDialog(this, "All " + total + " models verified successfully!", "Verification Complete", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }

                    if (!errors.isEmpty()) {
                        StringBuilder sb = new StringBuilder("The following " + errors.size() + " files appear to be corrupted or invalid:\n\n");
                        for (IModelValidator.ValidationResult err : errors) {
                            sb.append("- ").append(new File(err.filePath).getName())
                              .append(" (").append(err.message).append(")\n")
                              .append("  Path: ").append(err.filePath).append("\n\n");
                        }

                        JTextArea textArea = new JTextArea(sb.toString());
                        textArea.setEditable(false);
                        JScrollPane scrollPane = new JScrollPane(textArea);
                        scrollPane.setPreferredSize(new Dimension(800, 500));

                        Object[] options = {"OK", "Delete All Corrupted Files"};
                        int choice = JOptionPane.showOptionDialog(this, scrollPane, "Verification Results - Issues Found", 
                                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);

                        if (choice == 1) { // Delete All Corrupted Files
                            int confirm = JOptionPane.showConfirmDialog(this, 
                                "Are you sure you want to delete these " + errors.size() + " files?\nThis action cannot be undone.",
                                "Confirm Deletion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);   

                            if (confirm == JOptionPane.YES_OPTION) {
                                int deletedCount = 0;
                                for (IModelValidator.ValidationResult err : errors) {
                                    File f = new File(err.filePath);
                                    if (f.exists() && f.delete()) {
                                        deletedCount++;
                                    }
                                }
                                JOptionPane.showMessageDialog(this, "Deleted " + deletedCount + " files.");    
                                statusLabel.setText("Cleanup finished. Deleted " + deletedCount + " files.");  
                            }
                        }
                    }
                    
                    if (!duplicates.isEmpty()) {
                        showDuplicatesDialog(duplicates);
                    }
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Error during verification: " + e.getMessage()));
            }
        }).start();
    }

    private void showDuplicatesDialog(Map<String, List<Path>> duplicates) {
        StringBuilder sb = new StringBuilder("Storage Optimizer - Duplicate Models Found:\n\n");
        sb.append("The following files have identical content (SHA-256 match).\n");
        sb.append("You might want to delete redundant copies to save space.\n\n");

        for (Map.Entry<String, List<Path>> entry : duplicates.entrySet()) {
            sb.append("SHA-256: ").append(entry.getKey()).append("\n");
            for (Path p : entry.getValue()) {
                sb.append("  -> ").append(p.toAbsolutePath()).append("\n");
            }
            sb.append("\n");
        }

        JTextArea textArea = new JTextArea(sb.toString());
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(900, 600));
        JOptionPane.showMessageDialog(this, scrollPane, "Duplicates Found", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showArchiveDialog() {
        JDialog dialog = new JDialog(this, "Archive Manager", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(1000, 700);
        dialog.setLocationRelativeTo(this);

        JTabbedPane tabs = new JTabbedPane();
        
        // --- TAB 1: ARCHIVE (Models -> Archive) ---
        JPanel archivePanel = new JPanel(new BorderLayout(10, 10));
        archivePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        Map<String, List<ModelInfo>> groupedToArchive = archiveService.getModelsGroupedByFolder();
        String[] columns = {"Select", "Folder", "Name", "Size"};
        DefaultTableModel archiveTableModel = new DefaultTableModel(columns, 0) {
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : String.class; }
            @Override public boolean isCellEditable(int r, int c) { return c == 0; }
        };

        for (Map.Entry<String, List<ModelInfo>> entry : groupedToArchive.entrySet()) {
            for (ModelInfo model : entry.getValue()) {
                archiveTableModel.addRow(new Object[]{false, entry.getKey(), model.getName(), model.getSize()});
            }
        }

        Map<String, List<ModelInfo>> groupedToRestore = archiveService.getArchivedModelsGroupedByFolder();
        DefaultTableModel restoreTableModel = new DefaultTableModel(columns, 0) {
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : String.class; }
            @Override public boolean isCellEditable(int r, int c) { return c == 0; }
        };

        for (Map.Entry<String, List<ModelInfo>> entry : groupedToRestore.entrySet()) {
            for (ModelInfo model : entry.getValue()) {
                restoreTableModel.addRow(new Object[]{false, entry.getKey(), model.getName(), model.getSize()});
            }
        }

        JTable archiveTable = new JTable(archiveTableModel);
        archiveTable.setRowHeight(25);
        archiveTable.getColumnModel().getColumn(0).setMaxWidth(50);
        
        // Enable sorting with custom comparator for human-readable sizes
        javax.swing.table.TableRowSorter<DefaultTableModel> archiveSorter = new javax.swing.table.TableRowSorter<>(archiveTableModel);
        archiveSorter.setComparator(3, (s1, s2) -> Long.compare(parseSizeToBytes((String)s1), parseSizeToBytes((String)s2)));
        archiveSorter.setSortable(0, false); // Disable sorting for checkbox column to allow "Select All"
        archiveTable.setRowSorter(archiveSorter);

        // Add "Select All" functionality to header
        archiveTable.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewIdx = archiveTable.columnAtPoint(e.getPoint());
                if (viewIdx == -1) return;
                int modelIdx = archiveTable.convertColumnIndexToModel(viewIdx);
                if (modelIdx == 0) {
                    boolean allSelected = true;
                    for (int i = 0; i < archiveTableModel.getRowCount(); i++) {
                        if (!(Boolean) archiveTableModel.getValueAt(i, 0)) {
                            allSelected = false;
                            break;
                        }
                    }
                    boolean newValue = !allSelected;
                    for (int i = 0; i < archiveTableModel.getRowCount(); i++) {
                        archiveTableModel.setValueAt(newValue, i, 0);
                    }
                }
            }
        });

        JScrollPane archiveScroll = new JScrollPane(archiveTable);
        archivePanel.add(archiveScroll, BorderLayout.CENTER);

        JPanel archiveBottom = new JPanel(new BorderLayout());
        JLabel archiveCountLabel = new JLabel("Selected: 0 models");
        archiveTableModel.addTableModelListener(e -> {
            int selected = 0;
            for (int i = 0; i < archiveTableModel.getRowCount(); i++) {
                if ((Boolean) archiveTableModel.getValueAt(i, 0)) selected++;
            }
            archiveCountLabel.setText("Selected: " + selected + " models");
        });
        
        JButton archiveNowBtn = new JButton("📦 Move to Archive");
        archiveNowBtn.addActionListener(e -> {
            List<Integer> selectedRows = new ArrayList<>();
            for (int i = 0; i < archiveTableModel.getRowCount(); i++) if ((Boolean) archiveTableModel.getValueAt(i, 0)) selectedRows.add(i);
            if (selectedRows.isEmpty()) return;
            
            showModalProgressDialog("Archive", "Archiving", selectedRows, archiveTableModel, restoreTableModel, (folder, name, update) -> {
                try {
                    archiveService.moveToArchiveWithProgress(folder, name, update);
                    return true;
                } catch (IOException ex) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(dialog, 
                            "Failed to archive '" + name + "':\n" + ex.getMessage(), 
                            "Archive Error", JOptionPane.ERROR_MESSAGE);
                    });
                    return false;
                }
            });
        });

        JPanel archiveActionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        archiveActionPanel.add(archiveNowBtn);
        archiveBottom.add(archiveCountLabel, BorderLayout.WEST);
        archiveBottom.add(archiveActionPanel, BorderLayout.EAST);
        archivePanel.add(archiveBottom, BorderLayout.SOUTH);

        // --- TAB 2: RESTORE (Archive -> Models) ---
        JPanel restorePanel = new JPanel(new BorderLayout(10, 10));
        restorePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JTable restoreTable = new JTable(restoreTableModel);
        restoreTable.setRowHeight(25);
        restoreTable.getColumnModel().getColumn(0).setMaxWidth(50);
        
        // Enable sorting with custom comparator for human-readable sizes
        javax.swing.table.TableRowSorter<DefaultTableModel> restoreSorter = new javax.swing.table.TableRowSorter<>(restoreTableModel);
        restoreSorter.setComparator(3, (s1, s2) -> Long.compare(parseSizeToBytes((String)s1), parseSizeToBytes((String)s2)));
        restoreSorter.setSortable(0, false); // Disable sorting for checkbox column to allow "Select All"
        restoreTable.setRowSorter(restoreSorter);

        // Add "Select All" functionality to header
        restoreTable.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewIdx = restoreTable.columnAtPoint(e.getPoint());
                if (viewIdx == -1) return;
                int modelIdx = restoreTable.convertColumnIndexToModel(viewIdx);
                if (modelIdx == 0) {
                    boolean allSelected = true;
                    for (int i = 0; i < restoreTableModel.getRowCount(); i++) {
                        if (!(Boolean) restoreTableModel.getValueAt(i, 0)) {
                            allSelected = false;
                            break;
                        }
                    }
                    boolean newValue = !allSelected;
                    for (int i = 0; i < restoreTableModel.getRowCount(); i++) {
                        restoreTableModel.setValueAt(newValue, i, 0);
                    }
                }
            }
        });

        JScrollPane restoreScroll = new JScrollPane(restoreTable);
        restorePanel.add(restoreScroll, BorderLayout.CENTER);

        JPanel restoreBottom = new JPanel(new BorderLayout());
        JLabel restoreCountLabel = new JLabel("Selected: 0 models");
        restoreTableModel.addTableModelListener(e -> {
            int selected = 0;
            for (int i = 0; i < restoreTableModel.getRowCount(); i++) {
                if ((Boolean) restoreTableModel.getValueAt(i, 0)) selected++;
            }
            restoreCountLabel.setText("Selected: " + selected + " models");
        });

        JButton restoreNowBtn = new JButton("🚀 Restore from Archive");
        restoreNowBtn.addActionListener(e -> {
            List<Integer> selectedRows = new ArrayList<>();
            for (int i = 0; i < restoreTableModel.getRowCount(); i++) if ((Boolean) restoreTableModel.getValueAt(i, 0)) selectedRows.add(i);
            if (selectedRows.isEmpty()) return;
            
            showModalProgressDialog("Restore", "Restoring", selectedRows, restoreTableModel, archiveTableModel, (folder, name, update) -> {
                return archiveService.restoreFromArchiveWithProgress(folder, name, update);
            });
        });

        JPanel restoreActionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        restoreActionPanel.add(restoreNowBtn);
        restoreBottom.add(restoreCountLabel, BorderLayout.WEST);
        restoreBottom.add(restoreActionPanel, BorderLayout.EAST);
        restorePanel.add(restoreBottom, BorderLayout.SOUTH);

        tabs.addTab("📦 Archive Models", archivePanel);
        tabs.addTab("🚀 Restore Models", restorePanel);
        
        dialog.add(tabs, BorderLayout.CENTER);
        
        JPanel dialogButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dialog.dispose());
        dialogButtons.add(closeBtn);
        dialog.add(dialogButtons, BorderLayout.SOUTH);
        
        dialog.setVisible(true);
    }

    private static class ArchiveWorkItem {
        int index;
        String folder, name, size;
        ArchiveWorkItem(int i, String f, String n, String s) { index = i; folder = f; name = n; size = s; }
    }

    private JPanel createManagerPanel(JTabbedPane tabs) {
        JPanel managerPanel = new JPanel(new BorderLayout(10, 10));
        managerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        toolbar.putClientProperty("FlatLaf.style", "background: lighten($Panel.background, 1%)");
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")));

        JButton verifyBtn = new JButton("🔍 Fast Verify");
        verifyBtn.putClientProperty("JButton.buttonType", "roundRect");
        verifyBtn.addActionListener(e -> verifyLocalModels(false));
        
        JButton optimizeBtn = new JButton("👯 Storage Optimizer");
        optimizeBtn.putClientProperty("JButton.buttonType", "roundRect");
        optimizeBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, 
                "The Storage Optimizer calculates SHA-256 hashes for all local models.\n" +
                "This is EXTREMELY slow and resource-intensive for large libraries.\n\n" +
                "Do you want to proceed?", "Performance Warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                verifyLocalModels(true);
            }
        });

        JButton archiveBtn = new JButton("📦 Archive...");
        archiveBtn.putClientProperty("JButton.buttonType", "roundRect");
        archiveBtn.addActionListener(e -> showArchiveDialog());

        JButton diagnosticBtn = new JButton("🩺 Diagnostic");
        diagnosticBtn.putClientProperty("JButton.buttonType", "roundRect");
        diagnosticBtn.addActionListener(e -> {
            if (modelsToDownload == null || modelsToDownload.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Load a workflow first to perform a diagnostic check.");
                return;
            }
            List<String> missing = diagnosticService.getMissingModels(modelsToDownload);
            if (missing.isEmpty()) {
                JOptionPane.showMessageDialog(this, "✅ All workflow models are visible to ComfyUI!", "Diagnostic Passed", JOptionPane.INFORMATION_MESSAGE);
            } else {
                String list = String.join("\n- ", missing);
                Object[] options = {"Go to Dashboard (Restart)", "Ignore"};
                int choice = JOptionPane.showOptionDialog(this, 
                    "❌ ComfyUI still reports these models as missing:\n- " + list + "\n\n" +
                    "A restart is required for ComfyUI to detect new models.", 
                    "Diagnostic Failed", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
                
                if (choice == 0) tabs.setSelectedIndex(0);
            }
        });

        JLabel toolsLabel = new JLabel("Storage Tools: ");
        toolsLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        toolbar.add(toolsLabel);
        toolbar.add(verifyBtn);
        toolbar.add(optimizeBtn);
        toolbar.add(archiveBtn);
        
        JSeparator sep = new JSeparator(JSeparator.VERTICAL);
        sep.setPreferredSize(new Dimension(2, 20));
        toolbar.add(sep);
        
        toolbar.add(diagnosticBtn);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        JPanel jsonPanel = new JPanel(new BorderLayout());
        jsonPanel.setBorder(BorderFactory.createTitledBorder("Workflow (Drag & Drop JSON/PNG)"));
        jsonInputArea = new JTextArea();
        setupDragAndDrop(jsonInputArea);
        JScrollPane jsonScroll = new JScrollPane(jsonInputArea);
        JPanel jsonButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton loadJsonBtn = new JButton("Load Workflow...");
        loadJsonBtn.addActionListener(e -> {
            FileDialog fd = new FileDialog(this, "Select Workflow", FileDialog.LOAD);
            fd.setVisible(true);
            if (fd.getFile() != null) loadFile(new File(fd.getDirectory(), fd.getFile()));
        });

        JButton importModelListBtn = new JButton("Import Model List...");
        importModelListBtn.addActionListener(e -> {
            FileDialog fd = new FileDialog(this, "Select Model List JSON", FileDialog.LOAD);
            fd.setVisible(true);
            if (fd.getFile() != null) {
                try {
                    modelListService.importJson(new File(fd.getDirectory(), fd.getFile()));
                    JOptionPane.showMessageDialog(this, "Model list imported successfully! (" + modelListService.getModels().size() + " models)");
                    analyzeJsonContent();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
                }
            }
        });

        JButton analyzeBtn = new JButton("Deep Search");
        analyzeBtn.addActionListener(e -> { analyzeJsonContent(); searchMissingOnline(); });
        jsonButtons.add(loadJsonBtn);
        jsonButtons.add(importModelListBtn);
        jsonButtons.add(analyzeBtn);
        jsonPanel.add(jsonScroll, BorderLayout.CENTER);
        jsonPanel.add(jsonButtons, BorderLayout.SOUTH);

        String[] columnNames = {"Select", "Type", "Name", "Size", "AI Source", "Target Path", "URL", "Status"}; 
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : String.class; }   
            @Override public boolean isCellEditable(int r, int c) { 
                if (c == 0) {
                    String status = (String) getValueAt(r, 7);
                    return status != null && !status.contains("Already exists");
                }
                return false; 
            }
        };
        tableModel.addTableModelListener(e -> {
            if (e.getType() == TableModelEvent.UPDATE && e.getColumn() == 0) updateDownloadManagerSelection();
        });
        JTable modelTable = new JTable(tableModel);
        modelTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

        JPopupMenu modelTablePopup = new JPopupMenu();
        JMenuItem searchCivitaiItem = new JMenuItem("Search Civitai & Select Version...");
        modelTablePopup.add(searchCivitaiItem);

        modelTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                showPopup(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                showPopup(e);
            }
            private void showPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = modelTable.rowAtPoint(e.getPoint());
                    if (row >= 0 && row < modelTable.getRowCount()) {
                        modelTable.setRowSelectionInterval(row, row);
                        modelTablePopup.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });

        searchCivitaiItem.addActionListener(e -> {
            int selectedRow = modelTable.getSelectedRow();
            if (selectedRow >= 0) {
                int modelRow = modelTable.convertRowIndexToModel(selectedRow);
                showCivitaiSearchDialog(modelRow);
            }
        });
        JScrollPane tableScroll = new JScrollPane(modelTable);
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(BorderFactory.createTitledBorder("Identified Models"));
        tablePanel.add(tableScroll, BorderLayout.CENTER);

        splitPane.setTopComponent(jsonPanel);
        splitPane.setBottomComponent(tablePanel);
        splitPane.setDividerLocation(350);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("Ready");
        
        JPanel progressPanel = new JPanel(new GridLayout(2, 1));
        activeAiModelLabel = new JLabel("Active AI: Loading...");
        activeAiModelLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        activeAiModelLabel.setForeground(Color.GRAY);
        progressPanel.add(statusLabel);
        progressPanel.add(activeAiModelLabel);
        
        JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        downloadButton = new JButton("Start Queue");
        downloadButton.putClientProperty("JButton.buttonType", "accent");
        downloadButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        downloadButton.setEnabled(false);
        downloadButton.addActionListener(e -> startDownloadQueue());
        
        pauseButton = new JButton("Pause");
        pauseButton.putClientProperty("JButton.buttonType", "roundRect");
        pauseButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        pauseButton.addActionListener(e -> {
            downloadManager.togglePause();
            SwingUtilities.invokeLater(() -> pauseButton.setText(downloadManager.isPaused() ? "Resume" : "Pause"));
        });
        
        stopButton = new JButton("Stop");
        stopButton.putClientProperty("JButton.buttonType", "roundRect");
        stopButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        stopButton.addActionListener(e -> downloadManager.stop());
        
        actionButtons.add(downloadButton);
        actionButtons.add(pauseButton);
        actionButtons.add(stopButton);
        
        bottomPanel.add(progressPanel, BorderLayout.CENTER);
        bottomPanel.add(actionButtons, BorderLayout.EAST);

        managerPanel.add(toolbar, BorderLayout.NORTH);
        managerPanel.add(splitPane, BorderLayout.CENTER);
        managerPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        return managerPanel;
    }

    private JPanel createSettingsPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));

        JPanel grid = new JPanel(new GridLayout(1, 2, 25, 0));
        grid.setOpaque(false);
        
        // Left Column: General & Paths
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.putClientProperty("FlatLaf.style", "arc: 15; background: lighten($Panel.background, 2%)");
        left.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel pathsHeader = new JLabel("General & Paths");
        pathsHeader.putClientProperty("FlatLaf.styleClass", "h3");
        pathsHeader.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

        JButton pathsBtn = new JButton("📁 Configure Directories...");
        pathsBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        pathsBtn.setMaximumSize(new Dimension(300, 40));
        pathsBtn.addActionListener(e -> showPathsDialog());

        JButton repairBtn = new JButton("🛠️ Auto-Repair Environment...");
        repairBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        repairBtn.setMaximumSize(new Dimension(300, 40));
        repairBtn.addActionListener(e -> triggerEnvironmentRepair());

        JButton downloadSettingsBtn = new JButton("📥 Download Settings...");
        downloadSettingsBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        downloadSettingsBtn.setMaximumSize(new Dimension(300, 40));
        downloadSettingsBtn.addActionListener(e -> showDownloadSettingsDialog());

        JButton bridgeBtn = new JButton("🚀 ComfyUI Bridge Installer...");
        bridgeBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        bridgeBtn.setMaximumSize(new Dimension(300, 40));
        bridgeBtn.addActionListener(e -> showInstallationDialog());

        JPanel checksPanel = new JPanel();
        checksPanel.setOpaque(false);
        checksPanel.setLayout(new BoxLayout(checksPanel, BoxLayout.Y_AXIS));
        checksPanel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

        backgroundCheck = new JCheckBox("Stay in Background");
        backgroundCheck.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        backgroundCheck.addActionListener(e -> configService.setBackgroundModeEnabled(backgroundCheck.isSelected()));
        
        shutdownCheck = new JCheckBox("Shutdown after Queue");
        shutdownCheck.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        shutdownCheck.addActionListener(e -> configService.setShutdownAfterDownloadEnabled(shutdownCheck.isSelected()));
        
        restartCheck = new JCheckBox("Full Restart after Queue");
        restartCheck.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        restartCheck.addActionListener(e -> configService.setRestartAfterDownloadEnabled(restartCheck.isSelected()));
        
        darkCheck = new JCheckBox("Dark Mode");
        darkCheck.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        darkCheck.addActionListener(e -> {
            boolean isDark = darkCheck.isSelected();
            configService.setDarkMode(isDark);
            FlatAnimatedLafChange.showSnapshot();
            setupTheme(isDark);
            revalidate();
            repaint();
            FlatAnimatedLafChange.hideSnapshotWithAnimation();
        });

        fastHashCheck = new JCheckBox("Fast Hashing (AutoV1)");
        fastHashCheck.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        fastHashCheck.addActionListener(e -> configService.setFastHashEnabled(fastHashCheck.isSelected()));

        checksPanel.add(backgroundCheck);
        checksPanel.add(Box.createVerticalStrut(8));
        checksPanel.add(shutdownCheck);
        checksPanel.add(Box.createVerticalStrut(8));
        checksPanel.add(restartCheck);
        checksPanel.add(Box.createVerticalStrut(8));
        checksPanel.add(darkCheck);
        checksPanel.add(Box.createVerticalStrut(8));
        checksPanel.add(fastHashCheck);

        left.add(pathsHeader);
        left.add(Box.createVerticalStrut(20));
        left.add(pathsBtn);
        left.add(Box.createVerticalStrut(10));
        left.add(repairBtn);
        left.add(Box.createVerticalStrut(10));
        left.add(downloadSettingsBtn);
        left.add(Box.createVerticalStrut(10));
        left.add(bridgeBtn);
        left.add(Box.createVerticalStrut(25));
        left.add(checksPanel);
        left.add(Box.createVerticalGlue());

        // Right Column: AI & Help
        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.putClientProperty("FlatLaf.style", "arc: 15; background: lighten($Panel.background, 2%)");
        right.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel aiHeader = new JLabel("AI & Support");
        aiHeader.putClientProperty("FlatLaf.styleClass", "h3");
        aiHeader.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

        JButton apiBtn = new JButton("🔑 AI & API Keys...");
        apiBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        apiBtn.setMaximumSize(new Dimension(300, 40));
        apiBtn.addActionListener(e -> showApiKeysDialog());

        JButton helpBtn = new JButton("ℹ View Help & Guide");
        helpBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        helpBtn.setMaximumSize(new Dimension(300, 40));
        helpBtn.addActionListener(e -> showHelpDialog());

        JButton resetVaultBtn = new JButton("🔒 Reset Security Vault...");
        resetVaultBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        resetVaultBtn.setMaximumSize(new Dimension(300, 40));
        resetVaultBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, "This will delete all stored API keys. Continue?", "Vault Reset", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                configService.resetVault();
                updateGeminiTabVisibility();
            }
        });

        right.add(aiHeader);
        right.add(Box.createVerticalStrut(20));
        right.add(apiBtn);
        right.add(Box.createVerticalStrut(10));
        right.add(resetVaultBtn);
        right.add(Box.createVerticalStrut(25));
        right.add(helpBtn);
        right.add(Box.createVerticalGlue());

        grid.add(left);
        grid.add(right);
        panel.add(grid, BorderLayout.CENTER);
        
        return panel;
    }

    private void triggerEnvironmentRepair() {
        if (mainTabs != null) {
            mainTabs.setSelectedIndex(0);
        } else if (getContentPane() instanceof JTabbedPane) {
            ((JTabbedPane) getContentPane()).setSelectedIndex(0);
        } else {
            findAndSelectTab(getContentPane(), 0);
        }
        
        if (consoleOutput != null) {
            consoleOutput.setText("");
        }
        
        new Thread(() -> {
            updaterService.repairEnvironment(log -> SwingUtilities.invokeLater(() -> {
                if (consoleOutput != null) {
                    consoleOutput.append(log);
                    consoleOutput.setCaretPosition(consoleOutput.getDocument().getLength());
                }
            }));
        }).start();
    }

    private void performFullServiceRestart() {
        new Thread(() -> {
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("🚀 Requesting restart via Dashboard...");
            });
            
            // Core logic: Stop via process controller
            processController.stop();
            
            // Switch to Dashboard so user can pick profile and start again
            SwingUtilities.invokeLater(() -> {
                if (getContentPane() instanceof JTabbedPane) {
                    ((JTabbedPane) getContentPane()).setSelectedIndex(0);
                }
                statusLabel.setText("✅ ComfyUI stopped. Please restart via Dashboard.");
            });
        }).start();
    }

    private void triggerPostOperationActions() {
        SwingUtilities.invokeLater(() -> {
            int choice = JOptionPane.showConfirmDialog(this, 
                "Operation finished successfully.\nDo you want to restart ComfyUI now?", 
                "Restart ComfyUI?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            
            if (choice == JOptionPane.YES_OPTION) {
                performFullServiceRestart();
            }
            
            if (configService.isShutdownAfterDownloadEnabled()) performSystemShutdown();
        });
    }

    private void refreshProfileList(DefaultListModel<de.tki.comfymodels.domain.LaunchProfile> model) {
        model.clear();
        List<de.tki.comfymodels.domain.LaunchProfile> profiles = profileManager.loadProfiles();
        
        // Add a default local profile if none exist
        if (profiles.isEmpty()) {
            de.tki.comfymodels.domain.LaunchProfile def = new de.tki.comfymodels.domain.LaunchProfile(
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

        for (de.tki.comfymodels.domain.LaunchProfile p : profiles) {
            model.addElement(p);
        }

        // Restore active profile selection
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
        }
    }

    private void refreshVersions() {
        // Local checks (fast)
        String localComfy = versionService.getInstalledComfyVersion(configService.getComfyUIPath());
        String localPython = versionService.getInstalledPythonVersion(configService.getPythonPath());
        String remotePython = versionService.getRemotePythonVersion();

        lblComfyVersion.setText("ComfyUI: " + localComfy + " (Remote: Fetching...)");
        lblPythonVersion.setText("Python: " + localPython + " (Latest: " + remotePython + ")");

        // Remote async check
        versionService.getRemoteComfyVersionAsync().thenAccept(remoteComfy -> {
            SwingUtilities.invokeLater(() -> {
                if (lblComfyVersion != null) {
                    lblComfyVersion.setText("ComfyUI: " + localComfy + " (Remote: " + remoteComfy + ")");
                }
            });
        });
    }

    private void updateHardwareUI(de.tki.comfymodels.service.impl.HardwareMonitorService.HardwareStats stats) {
        if (progressCpu != null) {
            progressCpu.setValue((int) stats.cpuLoad);
            progressCpu.setString(String.format("CPU: %.1f%%", stats.cpuLoad));
        }
        if (progressRam != null) {
            double usedGb = stats.ramUsed / (1024.0 * 1024.0 * 1024.0);
            double totalGb = stats.ramTotal / (1024.0 * 1024.0 * 1024.0);
            int pct = stats.ramTotal > 0 ? (int) ((stats.ramUsed * 100) / stats.ramTotal) : 0;
            progressRam.setValue(pct);
            progressRam.setString(String.format("RAM: %.1f GB / %.1f GB (%d%%)", usedGb, totalGb, pct));
        }
        if (stats.hasNvidia) {
            if (gpuPanel != null) gpuPanel.setVisible(true);
            if (progressGpu != null) {
                progressGpu.setValue(stats.gpuUtilization);
                progressGpu.setString(String.format("GPU Load: %d%%", stats.gpuUtilization));
            }
            if (progressVram != null) {
                double usedGb = stats.vramUsed / (1024.0 * 1024.0 * 1024.0);
                double totalGb = stats.vramTotal / (1024.0 * 1024.0 * 1024.0);
                int pct = stats.vramTotal > 0 ? (int) ((stats.vramUsed * 100) / stats.vramTotal) : 0;
                progressVram.setValue(pct);
                progressVram.setString(String.format("VRAM: %.1f GB / %.1f GB (%d%%)", usedGb, totalGb, pct));
            }
            if (lblGpuName != null) {
                lblGpuName.setText("GPU: " + stats.gpuName);
            }
        } else {
            if (gpuPanel != null) gpuPanel.setVisible(false);
        }
    }

    public String getWorkspaceWorkflowJson() {
        return jsonInputArea != null ? jsonInputArea.getText() : "";
    }

    public void loadWorkflowExternal(String name, String json) {
        SwingUtilities.invokeLater(() -> {
            if (jsonInputArea != null) {
                jsonInputArea.setText(json);
                currentFileName = name != null ? name + ".json" : "library_workflow.json";
                analyzeJsonContent();
                searchMissingOnline();
                
                // Switch to Model Manager Tab (index 1)
                if (getContentPane() instanceof JTabbedPane) {
                    ((JTabbedPane) getContentPane()).setSelectedIndex(1);
                } else {
                    findAndSelectTab(getContentPane(), 1);
                }
            }
        });
    }

    private void findAndSelectTab(Container container, int index) {
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof JTabbedPane) {
                ((JTabbedPane) child).setSelectedIndex(index);
                return;
            } else if (child instanceof Container) {
                findAndSelectTab((Container) child, index);
            }
        }
    }

    private void showModalProgressDialog(String title, String statusPrefix, List<Integer> selectedRows, 
                                        DefaultTableModel sourceModel, DefaultTableModel targetModel, TaskExecutor executor) {
        JDialog progressDialog = new JDialog(this, title, true);
        progressDialog.setLayout(new BorderLayout(10, 10));
        progressDialog.setSize(500, 180);
        progressDialog.setLocationRelativeTo(this);
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel label = new JLabel(statusPrefix + "...");
        label.setFont(new Font("SansSerif", Font.BOLD, 12));
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(400, 30));

        panel.add(label, BorderLayout.NORTH);
        panel.add(progressBar, BorderLayout.CENTER);
        progressDialog.add(panel, BorderLayout.CENTER);

        // Pre-collect data on EDT
        List<ArchiveWorkItem> workItems = new ArrayList<>();
        long totalBytes = 0;
        for (int row : selectedRows) {
            String sizeStr = (String) sourceModel.getValueAt(row, 3);
            workItems.add(new ArchiveWorkItem(row, (String) sourceModel.getValueAt(row, 1), (String) sourceModel.getValueAt(row, 2), sizeStr));
            totalBytes += parseSizeToBytes(sizeStr);
        }
        final long finalTotalBytes = totalBytes > 0 ? totalBytes : 1; 

        new Thread(() -> {
            int successCount = 0;
            List<Object[]> rowsToMove = new ArrayList<>();
            List<Integer> processedRowIndices = new ArrayList<>();
            long[] bytesProcessedTotal = {0};

            for (int i = 0; i < workItems.size(); i++) {
                ArchiveWorkItem item = workItems.get(i);
                final int fileIndex = i + 1;
                SwingUtilities.invokeLater(() -> label.setText(statusPrefix + " (" + fileIndex + "/" + workItems.size() + "): " + item.name));

                if (executor.execute(item.folder, item.name, (bytesDelta) -> {
                    bytesProcessedTotal[0] += bytesDelta;
                    final long current = bytesProcessedTotal[0];
                    final int percent = (int) Math.min(100, (current * 100) / finalTotalBytes);
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(percent);
                        progressBar.setString(percent + "% (" + searchService.formatSize(current) + " / " + searchService.formatSize(finalTotalBytes) + ")");
                    });
                })) {
                    successCount++;
                    processedRowIndices.add(item.index);
                    rowsToMove.add(new Object[]{false, item.folder, item.name, item.size});
                }
            }

            final int finalSuccess = successCount;
            SwingUtilities.invokeLater(() -> {
                progressDialog.dispose();
                processedRowIndices.sort((a, b) -> b.compareTo(a));
                for (int r : processedRowIndices) sourceModel.removeRow(r);
                for (Object[] rowData : rowsToMove) targetModel.addRow(rowData);
                
                analyzeJsonContent();
                triggerPostOperationActions();
                
                JOptionPane.showMessageDialog(this, finalSuccess + " models successfully " + title.toLowerCase() + "ed.", 
                    "Operation Complete", JOptionPane.INFORMATION_MESSAGE);
            });
        }).start();

        progressDialog.setVisible(true);
    }

    private long parseSizeToBytes(String sizeStr) {
        if (sizeStr == null || sizeStr.equals("Unknown") || sizeStr.isEmpty()) return 0;
        try {
            String[] parts = sizeStr.trim().split("\\s+");
            double val = Double.parseDouble(parts[0].replace(",", "."));
            if (parts.length < 2) return (long) val;
            String unit = parts[1].toUpperCase();
            if (unit.contains("GB")) return (long) (val * 1024 * 1024 * 1024);
            if (unit.contains("MB")) return (long) (val * 1024 * 1024);
            if (unit.contains("KB")) return (long) (val * 1024);
            return (long) val;
        } catch (Exception e) { return 0; }
    }

    @FunctionalInterface
    interface TaskExecutor {
        boolean execute(String folder, String name, java.util.function.LongConsumer progressUpdate);
    }

    private void updateDownloadManagerSelection() {
        if (downloadManager == null || tableModel == null) return;
        boolean[] selected = new boolean[tableModel.getRowCount()];
        for (int i = 0; i < selected.length; i++) selected[i] = (Boolean) tableModel.getValueAt(i, 0);
        downloadManager.updateSelection(selected);
    }

    private void setupDragAndDrop(JTextArea area) {
        new DropTarget(area, new DropTargetListener() {
            public void dragEnter(DropTargetDragEvent dtde) {}
            public void dragOver(DropTargetDragEvent dtde) {}
            public void dropActionChanged(DropTargetDragEvent dtde) {}
            public void dragExit(DropTargetEvent dte) {}
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    java.util.List<File> files = (java.util.List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) loadFile(files.get(0));
                } catch (Exception e) {}
            }
        });
    }

    private void performSystemShutdown() {
        de.tki.comfymodels.util.PlatformUtils.shutdownSystem();
    }

    private void showHelpDialog() {
        JDialog helpDialog = new JDialog(this, "User Guide & Documentation", true);
        helpDialog.setLayout(new BorderLayout());
        helpDialog.setSize(950, 750);
        helpDialog.setLocationRelativeTo(this);

        JEditorPane editorPane = new JEditorPane();
        editorPane.setEditable(false);
        editorPane.setContentType("text/html");
        
        boolean darkMode = configService.isDarkMode();
        String bgColor = darkMode ? "#161616" : "#f8f9fa";
        String textColor = darkMode ? "#e6e6e6" : "#212529";
        String accentColor = darkMode ? "#ffff00" : "#0056b3";
        String boxBg = darkMode ? "#202020" : "#ffffff";
        String boxBorder = darkMode ? "#333" : "#dee2e6";
        String tipBg = darkMode ? "#333" : "#e9ecef";

        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='padding: 25px; font-family: sans-serif; background-color: ").append(bgColor).append("; color: ").append(textColor).append(";'>");
        sb.append("<h1 style='color: ").append(accentColor).append("; text-align: center; margin-bottom: 25px;'>🚀 ComfyUI Companion</h1>");
        
        sb.append("<div style='background-color: ").append(boxBg).append("; padding: 15px; border-radius: 8px; margin-bottom: 20px; border: 1px solid ").append(boxBorder).append(";'>");
        sb.append("<h2 style='color: ").append(accentColor).append("; margin-top: 0;'>🛠️ Getting Started (Setup)</h2>");
        sb.append("<ol>");
        sb.append("<li><b>Set Directories:</b> Go to <i>Settings -> Directories</i> and set your ComfyUI 'models' path. " +
                  "Additionally, choose an 'Archive' path (ideally on a large HDD) to offload models you aren't currently using.</li>");
        sb.append("<li><b>Unlock Vault:</b> On the first start, you set a master password. This securely encrypts your API keys on your disk.</li>");
        sb.append("<li><b>Store API Keys:</b> Go to <i>Settings -> AI & API Keys</i>. A <b>Gemini API Key</b> is highly recommended, " +
                  "as the AI reads workflows like a human and intelligently finds missing download links.</li>");
        sb.append("<li><b>Install Bridge:</b> Use <i>Settings -> ComfyUI Bridge</i> to add the 🚀 button directly into your ComfyUI interface.</li>");
        sb.append("</ol></div>");

        sb.append("<h2 style='color: ").append(accentColor).append(";'>✨ Core Features</h2>");
        
        sb.append("<p><b>📦 Archive Manager:</b> Models you don't need right now can be moved to the archive via <i>Archive...</i>. " +
                  "If a workflow needs them later, the Downloader detects them in the archive and offers a <b>one-click restoration</b>.</p>");
        
        sb.append("<p><b>👯 Storage Optimizer:</b> Scans your library for duplicates. " +
                  "Using SHA-256 hash comparison, it identifies identical files even if they have different names.</p>");
        
        sb.append("<p><b>🔍 Fast Verify:</b> Quickly checks if your model files are valid or have corrupted headers.</p>");

        sb.append("<p><b>🧠 AI Search:</b> When loading a workflow, the AI automatically tries to identify models. " +
                  "Use <i>Deep Search</i> to have the AI specifically search for download sources.</p>");

        sb.append("<div style='background-color: ").append(tipBg).append("; padding: 10px; border-radius: 5px; margin-top: 20px;'>");
        sb.append("<b>💡 Pro Tips:</b><br>");
        sb.append("• Use <b>Drag & Drop</b> for images (.png) to directly read their embedded workflow.<br>");
        sb.append("• Enable <b>Shutdown after Queue</b> for overnight downloads.<br>");
        sb.append("• The app minimizes to the <b>System Tray</b> (bottom right) if 'Stay in Background' is active.");
        sb.append("</div>");
        
        sb.append("<p style='margin-top: 30px; color: #888; font-style: italic; text-align: center;'>Version 1.0.5 - developed by TKI</p>");
        sb.append("</body></html>");

        editorPane.setText(sb.toString());
        editorPane.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(editorPane);
        scrollPane.setBorder(null);
        helpDialog.add(scrollPane, BorderLayout.CENTER);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> helpDialog.dispose());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.add(closeBtn);
        helpDialog.add(btnPanel, BorderLayout.SOUTH);

        helpDialog.setVisible(true);
    }

    private void syncBridgeFiles() {
        String comfyPath = configService.getComfyUIPath();
        if (comfyPath == null || comfyPath.isEmpty()) return;

        Path comfyDir = Paths.get(comfyPath);
        if (!Files.exists(comfyDir)) return;

        Path customNodesDir = pathResolver.findCustomNodes(comfyDir);
        if (customNodesDir == null) return;

        Path targetDir = customNodesDir.resolve("comfyuicompanion");
        try {
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }
            // Always sync latest code files from resources to target
            extractResource("/comfyui-bridge/__init__.py", targetDir.resolve("__init__.py").toFile());
            Path webDir = targetDir.resolve("web");
            Files.createDirectories(webDir);
            extractResource("/comfyui-bridge/web/downloader.js", webDir.resolve("downloader.js").toFile());
            
            // Also update config
            writeExtensionConfig(targetDir.toFile());
            System.out.println("[Bridge-Sync] Successfully synchronized latest bridge code and token.");
        } catch (IOException e) {
            System.err.println("[Bridge-Sync] Failed to sync code: " + e.getMessage());
        }
    }

    private void startComfyUI(de.tki.comfymodels.domain.LaunchProfile selected, boolean clearConsole) {
        if (selected == null) return;
        if (launchBtn != null) launchBtn.setEnabled(false);
        if (clearConsole && consoleOutput != null) {
            consoleOutput.setText("");
        }
        java.util.concurrent.atomic.AtomicBoolean cudaError = new java.util.concurrent.atomic.AtomicBoolean(false);
        processController.start(selected, Paths.get(configService.getComfyUIPath()), configService.getPythonPath(), log -> {
            if (log.contains("Torch not compiled with CUDA enabled") || log.contains("AssertionError: Torch not compiled with CUDA enabled")) {
                cudaError.set(true);
            }
            if (log.contains("To see the GUI go to:")) {
                int idx = log.indexOf("http://");
                if (idx == -1) idx = log.indexOf("https://");
                if (idx != -1) {
                    String url = log.substring(idx).trim();
                    new Thread(() -> {
                        try {
                            if (Desktop.isDesktopSupported()) {
                                Desktop.getDesktop().browse(new java.net.URI(url));
                            }
                        } catch (Exception e) {
                            System.err.println("Failed to open browser automatically: " + e.getMessage());
                        }
                    }).start();
                    SwingUtilities.invokeLater(() -> {
                        if (mainTabs != null) {
                            for (int i = 0; i < mainTabs.getTabCount(); i++) {
                                if (mainTabs.getTitleAt(i).contains("Model Manager")) {
                                    mainTabs.setSelectedIndex(i);
                                    break;
                                }
                            }
                        }
                    });
                }
            }
            SwingUtilities.invokeLater(() -> {
                consoleOutput.append(log + "\n");
                consoleOutput.setCaretPosition(consoleOutput.getDocument().getLength());
            });
        }).thenAccept(exitCode -> {
            if (exitCode != 0 && cudaError.get()) {
                SwingUtilities.invokeLater(() -> {
                    int choice = JOptionPane.showConfirmDialog(this,
                        "ComfyUI failed to start because CUDA is not enabled/supported on this PyTorch installation.\n" +
                        "Would you like to switch to 'CPU Mode' profile and start ComfyUI on your CPU?",
                        "CUDA Error Detected", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);
                    if (choice == JOptionPane.YES_OPTION) {
                        List<de.tki.comfymodels.domain.LaunchProfile> profiles = profileManager.loadProfiles();
                        de.tki.comfymodels.domain.LaunchProfile cpuProfile = profiles.stream()
                            .filter(p -> p.id().equals("cpu_mode"))
                            .findFirst().orElse(null);
                        if (cpuProfile != null) {
                            if (profileList != null) {
                                profileList.setSelectedValue(cpuProfile, true);
                            }
                            startComfyUI(cpuProfile, true);
                        } else {
                            JOptionPane.showMessageDialog(this, "CPU Mode profile not found. Please add or use a custom profile with '--cpu'.");
                        }
                    }
                });
            }
        });
    }

    private static class CivitaiModel {
        String name;
        String type;
        com.fasterxml.jackson.databind.JsonNode rawNode;
        CivitaiModel(String name, String type, com.fasterxml.jackson.databind.JsonNode rawNode) {
            this.name = name;
            this.type = type;
            this.rawNode = rawNode;
        }
        @Override public String toString() {
            return name + " (" + type + ")";
        }
    }

    private static class CivitaiVersion {
        String name;
        String downloadUrl;
        String size;
        long byteSize;
        String imageUrl;
        List<String> triggerWords;
        com.fasterxml.jackson.databind.JsonNode rawNode;
        CivitaiVersion(String name, String downloadUrl, String size, long byteSize, String imageUrl, List<String> triggerWords, com.fasterxml.jackson.databind.JsonNode rawNode) {
            this.name = name;
            this.downloadUrl = downloadUrl;
            this.size = size;
            this.byteSize = byteSize;
            this.imageUrl = imageUrl;
            this.triggerWords = triggerWords;
            this.rawNode = rawNode;
        }
        @Override public String toString() {
            return name;
        }
    }

    private void showCivitaiSearchDialog(int row) {
        String initialName = (String) tableModel.getValueAt(row, 2);
        String cleanedQuery = initialName.replaceAll("(?i)\\.(safetensors|ckpt|sft|pt|bin)$", "").trim();

        JDialog dialog = new JDialog(this, "Civitai Version Selector", true);
        dialog.setSize(950, 650);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel leftPanel = new JPanel(new BorderLayout(10, 10));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JPanel searchBar = new JPanel(new BorderLayout(5, 0));
        JTextField searchField = new JTextField(cleanedQuery);
        JButton searchBtn = new JButton("Search");
        searchBar.add(searchField, BorderLayout.CENTER);
        searchBar.add(searchBtn, BorderLayout.EAST);
        leftPanel.add(searchBar, BorderLayout.NORTH);
        
        DefaultListModel<CivitaiModel> modelListModel = new DefaultListModel<>();
        JList<CivitaiModel> modelList = new JList<>(modelListModel);
        modelList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane listScroll = new JScrollPane(modelList);
        leftPanel.add(listScroll, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout(10, 10));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JPanel versionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        versionPanel.add(new JLabel("Version:"));
        JComboBox<CivitaiVersion> versionCombo = new JComboBox<>();
        versionPanel.add(versionCombo);
        rightPanel.add(versionPanel, BorderLayout.NORTH);
        
        JPanel detailsPanel = new JPanel(new BorderLayout(5, 5));
        JTextArea infoText = new JTextArea();
        infoText.setEditable(false);
        infoText.setLineWrap(true);
        infoText.setWrapStyleWord(true);
        JScrollPane infoScroll = new JScrollPane(infoText);
        infoScroll.setPreferredSize(new Dimension(350, 180));
        detailsPanel.add(infoScroll, BorderLayout.NORTH);
        
        JLabel previewLabel = new JLabel("No Preview Image Available", JLabel.CENTER);
        previewLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        previewLabel.setPreferredSize(new Dimension(350, 350));
        detailsPanel.add(previewLabel, BorderLayout.CENTER);
        
        rightPanel.add(detailsPanel, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(420);
        dialog.add(splitPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dialog.dispose());
        JButton applyBtn = new JButton("Apply Selected Version");
        applyBtn.putClientProperty("JButton.buttonType", "accent");
        
        bottomPanel.add(cancelBtn);
        bottomPanel.add(applyBtn);
        dialog.add(bottomPanel, BorderLayout.SOUTH);

        Runnable doSearch = () -> {
            String q = searchField.getText().trim();
            if (q.isEmpty()) return;
            searchBtn.setEnabled(false);
            civitaiService.searchModelsRaw(q).thenAccept(root -> {
                SwingUtilities.invokeLater(() -> {
                    searchBtn.setEnabled(true);
                    modelListModel.clear();
                    if (root != null) {
                        com.fasterxml.jackson.databind.JsonNode items = root.get("items");
                        if (items != null && items.isArray()) {
                            for (com.fasterxml.jackson.databind.JsonNode item : items) {
                                String name = item.path("name").asText("Unknown");
                                String type = item.path("type").asText("Unknown");
                                modelListModel.addElement(new CivitaiModel(name, type, item));
                            }
                        }
                    }
                    if (modelListModel.isEmpty()) {
                        modelListModel.addElement(new CivitaiModel("No results found", "", null));
                    }
                });
            }).exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    searchBtn.setEnabled(true);
                    modelListModel.clear();
                    modelListModel.addElement(new CivitaiModel("Error: " + ex.getMessage(), "", null));
                });
                return null;
            });
        };

        searchBtn.addActionListener(e -> doSearch.run());
        searchField.addActionListener(e -> doSearch.run());

        modelList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            CivitaiModel selectedModel = modelList.getSelectedValue();
            versionCombo.removeAllItems();
            infoText.setText("");
            previewLabel.setIcon(null);
            previewLabel.setText("No Preview Image Available");
            
            if (selectedModel == null || selectedModel.rawNode == null) return;
            
            com.fasterxml.jackson.databind.JsonNode versions = selectedModel.rawNode.get("modelVersions");
            if (versions != null && versions.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode v : versions) {
                    String vName = v.path("name").asText("Unknown");
                    String downloadUrl = v.path("downloadUrl").asText("");
                    
                    long byteSize = 0;
                    String sizeStr = "Unknown";
                    com.fasterxml.jackson.databind.JsonNode files = v.get("files");
                    if (files != null && files.isArray() && files.size() > 0) {
                        double sizeKb = files.get(0).path("sizeKB").asDouble(0);
                        byteSize = (long) (sizeKb * 1024);
                        double sizeMb = sizeKb / 1024.0;
                        if (sizeMb > 1024) {
                            sizeStr = String.format("%.2f GB", sizeMb / 1024.0);
                        } else {
                            sizeStr = String.format("%.2f MB", sizeMb);
                        }
                    }
                    
                    String imgUrl = null;
                    com.fasterxml.jackson.databind.JsonNode images = v.get("images");
                    if (images != null && images.isArray() && images.size() > 0) {
                        imgUrl = images.get(0).path("url").asText(null);
                    }
                    
                    List<String> triggers = new ArrayList<>();
                    com.fasterxml.jackson.databind.JsonNode trainedWords = v.get("trainedWords");
                    if (trainedWords != null && trainedWords.isArray()) {
                        for (com.fasterxml.jackson.databind.JsonNode word : trainedWords) {
                            triggers.add(word.asText());
                        }
                    }
                    
                    versionCombo.addItem(new CivitaiVersion(vName, downloadUrl, sizeStr, byteSize, imgUrl, triggers, v));
                }
            }
        });

        versionCombo.addActionListener(e -> {
            CivitaiVersion selectedVersion = (CivitaiVersion) versionCombo.getSelectedItem();
            infoText.setText("");
            previewLabel.setIcon(null);
            previewLabel.setText("Loading Preview...");
            
            if (selectedVersion == null) {
                previewLabel.setText("No Preview Image Available");
                return;
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("File Size: ").append(selectedVersion.size).append("\n");
            sb.append("Download URL: ").append(selectedVersion.downloadUrl).append("\n\n");
            
            if (!selectedVersion.triggerWords.isEmpty()) {
                sb.append("Trigger Words: ").append(String.join(", ", selectedVersion.triggerWords)).append("\n\n");
            }
            
            String desc = selectedVersion.rawNode.path("description").asText("");
            if (!desc.isEmpty()) {
                desc = desc.replaceAll("<[^>]*>", "");
                sb.append("Description:\n").append(desc);
            }
            
            infoText.setText(sb.toString());
            infoText.setCaretPosition(0);
            
            String imgUrl = selectedVersion.imageUrl;
            if (imgUrl != null && !imgUrl.isEmpty()) {
                new Thread(() -> {
                    try {
                        java.net.URL url = new java.net.URL(imgUrl);
                        BufferedImage img = javax.imageio.ImageIO.read(url);
                        if (img != null) {
                            int lblWidth = previewLabel.getWidth();
                            int lblHeight = previewLabel.getHeight();
                            if (lblWidth <= 0) lblWidth = 350;
                            if (lblHeight <= 0) lblHeight = 350;
                            
                            double ratio = Math.min((double) lblWidth / img.getWidth(), (double) lblHeight / img.getHeight());
                            int w = (int) (img.getWidth() * ratio);
                            int h = (int) (img.getHeight() * ratio);
                            Image scaled = img.getScaledInstance(w, h, Image.SCALE_SMOOTH);
                            
                            SwingUtilities.invokeLater(() -> {
                                if (versionCombo.getSelectedItem() == selectedVersion) {
                                    previewLabel.setIcon(new ImageIcon(scaled));
                                    previewLabel.setText("");
                                }
                            });
                        } else {
                            SwingUtilities.invokeLater(() -> {
                                if (versionCombo.getSelectedItem() == selectedVersion) {
                                    previewLabel.setText("Failed to load image preview");
                                }
                            });
                        }
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(() -> {
                            if (versionCombo.getSelectedItem() == selectedVersion) {
                                previewLabel.setText("Preview Image unavailable");
                            }
                        });
                    }
                }).start();
            } else {
                previewLabel.setText("No Preview Image Available");
            }
        });

        applyBtn.addActionListener(e -> {
            CivitaiVersion selectedVersion = (CivitaiVersion) versionCombo.getSelectedItem();
            CivitaiModel selectedModel = modelList.getSelectedValue();
            if (selectedVersion == null || selectedModel == null) {
                JOptionPane.showMessageDialog(dialog, "Please select a model and a version first.", "No Selection", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            ModelInfo info = modelsToDownload.get(row);
            
            String targetFileName = "";
            com.fasterxml.jackson.databind.JsonNode files = selectedVersion.rawNode.get("files");
            if (files != null && files.isArray() && files.size() > 0) {
                targetFileName = files.get(0).path("name").asText("");
            }
            if (targetFileName.isEmpty()) {
                targetFileName = info.getName();
            }
            
            info.setName(targetFileName);
            info.setUrl(selectedVersion.downloadUrl);
            info.setSize(selectedVersion.size);
            info.setByteSize(selectedVersion.byteSize);
            
            tableModel.setValueAt(true, row, 0);
            tableModel.setValueAt(targetFileName, row, 2);
            tableModel.setValueAt(selectedVersion.size, row, 3);
            tableModel.setValueAt(selectedVersion.downloadUrl, row, 6);
            tableModel.setValueAt("✅ Known Good", row, 7);
            
            dialog.dispose();
            JOptionPane.showMessageDialog(this, "Model version applied! Ready to download.", "Version Applied", JOptionPane.INFORMATION_MESSAGE);
        });

        SwingUtilities.invokeLater(() -> doSearch.run());
        dialog.setVisible(true);
    }

    public static void main(String[] args) {
        FlatLaf.setUseNativeWindowDecorations(true);
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);   
        context.getBean(Main.class).launch(args);
    }

    @Configuration @ComponentScan("de.tki.comfymodels") public static class AppConfig {}
}
