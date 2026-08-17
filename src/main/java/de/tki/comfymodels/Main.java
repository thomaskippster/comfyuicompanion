package de.tki.comfymodels;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.IDownloadManager;
import de.tki.comfymodels.service.IModelAnalyzer;
import de.tki.comfymodels.service.IWorkflowService;
import de.tki.comfymodels.service.IModelSearchService;
import de.tki.comfymodels.service.IModelValidator;
import de.tki.comfymodels.service.impl.ConfigService;
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
import javax.imageio.ImageIO;
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
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Main.class);
    private static volatile org.springframework.context.ConfigurableApplicationContext appContext;
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
    private de.tki.comfymodels.service.IComfyTemplateService comfyTemplateService;



    @Autowired
    private de.tki.comfymodels.service.impl.LocalAIService localAIService;

    @Autowired
    private ModelListService modelListService;

    @Autowired
    private ModelHashRegistry hashRegistry;

    @Autowired
    private de.tki.comfymodels.service.impl.LocalModelScanner localScanner;

    @Autowired
    private de.tki.comfymodels.service.impl.PathResolver pathResolver;

    private final java.util.Map<Integer, String> apiWorkflowCache = new java.util.concurrent.ConcurrentHashMap<>();

    @Autowired
    private de.tki.comfymodels.service.impl.VersionService versionService;

    @Autowired(required = false)
    private de.tki.comfymodels.service.IModelArchitectureService modelArchitectureService;



    @Autowired
    private de.tki.comfymodels.service.impl.HardwareMonitorService hardwareMonitorService;

    @Autowired
    private de.tki.comfymodels.service.impl.UpdaterService updaterService;

    @Autowired
    private de.tki.comfymodels.ui.VideoArchitectTab videoArchitectTab;

    @Autowired
    private de.tki.comfymodels.ui.BlueprintGalleryTab blueprintGalleryTab;

    @Autowired
    private de.tki.comfymodels.service.impl.DependencyService dependencyService;

    @Autowired
    private de.tki.comfymodels.util.BackgroundExecutor backgroundExecutor;

    @Autowired
    private de.tki.comfymodels.service.impl.ProcessTracker processTracker;

    @Autowired
    private de.tki.comfymodels.service.PromptBlueprintApiService promptBlueprintApiService;

    @Autowired
    private de.tki.comfymodels.service.impl.ComfyApiClient comfyApiClient;

    @Autowired
    private de.tki.comfymodels.ui.PromptLabView promptLabView;

    @Autowired
    private de.tki.comfymodels.controller.PromptLabController promptLabController;

    @Autowired
    private de.tki.comfymodels.ui.DownloadManagerView downloadManagerView;

    @Autowired
    private de.tki.comfymodels.controller.DownloadManagerController downloadManagerController;

    @Autowired(required = false)
    private de.tki.comfymodels.service.IWorkflowDownloader workflowDownloader;


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
    private JCheckBox hideComfyuiCheck;
    private JLabel activeAiModelLabel;
    private JTextArea jsonInputArea;
    private JTextArea consoleOutput; 
    private DefaultTableModel tableModel;
    private JButton launchBtn;
    private JList<de.tki.comfymodels.domain.LaunchProfile> profileList;
    private JLabel statusLabel;
    private JButton downloadButton, pauseButton, stopButton;
    private boolean isDownloading = false;
    private List<ModelInfo> modelsToDownload = new ArrayList<>();
    private String currentFileName = "input.json";
    private Image appIcon;
    private JPanel blueprintPanel;
    private JProgressBar blueprintBar;
    private JLabel blueprintStatusLabel;

    private JTabbedPane mainTabs;
    private de.tki.comfymodels.ui.WorkflowGraphPanel workflowGraphPanel;
    private JTable modelTable;

    private JCheckBox promptLabCheck;
    private JCheckBox videoArchitectCheck;
    private JCheckBox blueprintGalleryCheck;

    private JPanel dashboardPanel;
    private JPanel downloadManagerPanel;
    private JPanel promptLabPanel;
    private de.tki.comfymodels.ui.OutputGalleryPanel galleryPanel;
    private JPanel videoArchitectWrapper;
    private JPanel blueprintGalleryWrapper;
    private JPanel settingsPanel;


    private final java.util.Set<String> comfyCheckpoints = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> comfyUnetModels = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> comfyClips = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> comfyVaes = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> comfyClipTypes = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> comfyUnetWeightDtypes = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private String lastActiveProfileId = null;
    private String cachedActiveProfileName = "None";
    private long lastProfileCacheUpdate = 0;
    private boolean cachedNeedsSetup = false;
    private long lastSetupCheckTime = 0;

    // Prompt Lab Fields
    private static class DropdownItem {
        private final String display;
        private final String value;
        public DropdownItem(String display, String value) {
            this.display = display;
            this.value = value;
        }
        @Override
        public String toString() {
            return display;
        }
        public String getValue() {
            return value;
        }
    }
    private JTextField promptSubjectField;
    private JComboBox<DropdownItem> promptEnvCombo;
    private JComboBox<String> promptModelCombo;
    private JSpinner promptWidthSpinner;
    private JSpinner promptHeightSpinner;
    private JSpinner promptStepsSpinner;
    private JSpinner promptCfgSpinner;
    private JTextField promptNegativeField;
    private JPanel promptImageInputPanel;
    private JTextField promptImageFileField;
    private File selectedInputImage;
    private JComboBox<String> promptSamplerCombo;
    private JComboBox<String> promptSchedulerCombo;
    private JSpinner promptDenoiseSpinner;
    private JSpinner promptBatchSizeSpinner;
    private JLabel promptPresetLabel;

    private JCheckBox chkPhotorealistic;
    private JCheckBox chkOil;
    private JCheckBox chkEngine;
    private JCheckBox chkAnime;
    private JCheckBox chkFantasy;
    private JCheckBox chkSketch;
    private JTextArea promptAssembleArea;
    private JTextArea promptJsonArea;
    private JTextArea promptLabConsole;
    private JButton btnSendToComfy;
    private JButton btnOptimizePrompt;

    private JButton btnSuggestSubject;
    private JPanel promptSubjectSuggestionsWrapper;
    private JPanel promptSubjectSuggestionsPanel;
    private JPanel promptLabLeftPanel;
    private JLabel promptImagePreviewLabel;
    private JProgressBar promptLabProgressBar;
    private Image currentPreviewImage;
    private JTabbedPane promptLabRightTabbedPane;

    /** Pre-loaded GUI-format JSON for the currently selected blueprint (null = not loaded yet). */
    private volatile String currentBlueprintGuiJson = null;


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
                refreshPromptLabModels();
                if (blueprintGalleryTab != null) {
                    blueprintGalleryTab.refreshAllData();
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
            System.setProperty("apple.awt.application.name", "Companion for ComfyUI");
            System.setProperty("apple.awt.application.appearance", "system");
        }

        // Initialize REST Bridge consumer EARLY
        restBridge.setWorkflowConsumer(workflowJson -> {
            SwingUtilities.invokeLater(() -> {
                logger.info("[Main] WorkflowConsumer triggered - bringing to front.");
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

        // Ensure server stops on exit. Daemon thread so the JVM does not block on it.
        Thread shutdownHook = new Thread(() -> {
            try {
                if (processController != null) {
                    processController.stop();
                }
                if (downloadManager != null) {
                    downloadManager.stop();
                }
                if (hardwareMonitorService != null) {
                    hardwareMonitorService.stop();
                }
                // Kill every tracked child process BEFORE we close the Spring context,
                // so the ProcessTracker bean is still alive to do the work.
                if (processTracker != null) {
                    processTracker.destroyAll();
                }
                // WSL --shutdown can hang on some hosts; run it asynchronously on a
                // daemon thread so the rest of cleanup is not blocked.
                Thread wslShutdown = new Thread(() -> {
                    try {
                        Runtime.getRuntime().exec(new String[]{"wsl", "--shutdown"});
                    } catch (Exception e) {
                        logger.error("Failed to execute wsl --shutdown: " + e.getMessage());
                    }
                }, "wsl-shutdown");
                wslShutdown.setDaemon(true);
                wslShutdown.start();
                // Close the Spring context last: this triggers @PreDestroy on every
                // bean (BackgroundExecutor, RestBridgeService, LocalGemmaService, ModelHashRegistry, ...).
                if (appContext != null) {
                    appContext.close();
                }
            } catch (Throwable t) {
                logger.error("Shutdown hook error: " + t);
            }
        }, "comfy-shutdown-hook");
        shutdownHook.setDaemon(true);
        Runtime.getRuntime().addShutdownHook(shutdownHook);

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
                if (blueprintGalleryTab != null) {
                    blueprintGalleryTab.refreshAllData();
                }
                scanAndVerifyComfyUIInstallation(false);
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Critical UI Error: " + e.getMessage());
            }
        });
    }

    public void setupTheme(boolean darkMode) {
        try {
            // Sync ThemeManager so GlassPanel/CardPanel paint with correct colors
            de.tki.comfymodels.ui.ThemeManager.setDarkMode(darkMode);
            // Global arcs for a sci-fi feel - ZERO rounded corners
            UIManager.put("Button.arc", 0);
            UIManager.put("Component.arc", 0);
            UIManager.put("TextComponent.arc", 0);
            UIManager.put("ProgressBar.arc", 0);
            UIManager.put("TitlePane.unifiedBackground", true);
            // Note: "CheckBox.iconSize" is not a valid FlatLaf style; use icon Dimension instead
            UIManager.put("CheckBox.icon.focusWidth", 1);

            // Clean, highly readable typography
            Font defaultFont = new Font("Segoe UI", Font.PLAIN, 13);
            for (String fontName : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
                if (fontName.equalsIgnoreCase("Inter") || fontName.equalsIgnoreCase("Roboto")) {
                    defaultFont = new Font(fontName, Font.PLAIN, 13);
                    break;
                }
            }
            UIManager.put("defaultFont", defaultFont);

            if (darkMode) {
                // SCI-FI CYBERSPACE Dark Palette - Redesign
                Color nodeBg = new javax.swing.plaf.ColorUIResource(10, 11, 14); // #0a0b0e (Deep blue-black background)
                Color comfySurface = new javax.swing.plaf.ColorUIResource(10, 11, 14); // #0a0b0e
                Color componentBg = new javax.swing.plaf.ColorUIResource(24, 27, 33); // #181b21 (Panels/Cards)
                Color comfyAccent = new javax.swing.plaf.ColorUIResource(30, 190, 170); // #1ebeaa (Subtle Teal/Cyan Accent)
                Color comfyText = new javax.swing.plaf.ColorUIResource(224, 248, 245); // #e0f8f5 (Soft cyan/white text)
                Color paleBlue = new javax.swing.plaf.ColorUIResource(112, 138, 144); // #708a90 (Dimmed text/inactive)
                Color comfyBorder = new javax.swing.plaf.ColorUIResource(42, 46, 56); // #2a2e38 (Subtle panel borders)

                UIManager.put("DefaultBackgroundColor", comfySurface);
                UIManager.put("Panel.background", nodeBg);
                UIManager.put("Table.background", componentBg);
                UIManager.put("TextArea.background", nodeBg); // Console area background
                UIManager.put("TextField.background", nodeBg); // Search field background
                UIManager.put("PasswordField.background", nodeBg);
                
                UIManager.put("Label.foreground", comfyText);
                UIManager.put("Table.foreground", comfyText);
                UIManager.put("TextArea.foreground", comfyText);
                UIManager.put("TextField.foreground", comfyText);
                
                UIManager.put("Table.selectionBackground", comfyAccent); 
                UIManager.put("Table.selectionForeground", nodeBg); // Dark text on cyan selection
                UIManager.put("List.selectionBackground", comfyAccent); 
                UIManager.put("List.selectionForeground", nodeBg); 
                UIManager.put("Component.focusedBorderColor", comfyAccent);
                UIManager.put("Component.borderColor", comfyBorder);
                UIManager.put("TextComponent.borderWidth", 1);
                UIManager.put("Separator.foreground", comfyBorder);
                
                UIManager.put("Button.background", componentBg);
                UIManager.put("Button.foreground", comfyText);
                UIManager.put("Button.focusedBackground", comfyAccent); 
                UIManager.put("Button.hoverBackground", comfyAccent); 
                UIManager.put("Button.hoverForeground", nodeBg); 
                UIManager.put("Button.pressedBackground", paleBlue);
                UIManager.put("Button.borderColor", comfyBorder);
                UIManager.put("Button.borderWidth", 1);
                
                UIManager.put("ScrollBar.track", comfySurface);
                UIManager.put("ScrollBar.thumb", comfyBorder);
                
                UIManager.put("TabbedPane.selectedBackground", nodeBg);
                UIManager.put("TabbedPane.selectedForeground", comfyAccent);
                UIManager.put("TabbedPane.foreground", paleBlue);
                UIManager.put("TabbedPane.underlineColor", comfyAccent);
                UIManager.put("TabbedPane.underlineHeight", 3);

                // ProgressBar custom styles
                UIManager.put("ProgressBar.foreground", comfyAccent);
                UIManager.put("ProgressBar.background", componentBg);
                UIManager.put("ProgressBar.arc", 0);

                // Card panel & UI styling variables
                UIManager.put("Card.background", componentBg); 
                UIManager.put("Card.border", comfyBorder); 
                UIManager.put("Card.placeholder", new Color(10, 11, 14, 200)); 
                UIManager.put("Card.placeholderBorder", comfyBorder);
                UIManager.put("Toolbar.customBg", componentBg);
                UIManager.put("SlimStat.titleForeground", comfyText);
                UIManager.put("SlimStat.valueForeground", comfyAccent);
                UIManager.put("SlimStat.barForeground", comfyAccent);
                UIManager.put("SlimStat.barBackground", nodeBg);
                UIManager.put("MainTabs.gradientStart", nodeBg);
                UIManager.put("MainTabs.gradientEnd", nodeBg);
                UIManager.put("MainTabs.glowStart", new Color(30, 190, 170, 10)); // Very faint subtle cyan glow
                UIManager.put("PromptLab.presetForeground", comfyAccent);
                
                UIManager.setLookAndFeel(new FlatDarkLaf());
            } else {
                // EXHAUSTIVE cleanup of custom overrides
                String[] keysToClear = {
                    "DefaultBackgroundColor", "Panel.background", "Table.background", "TextArea.background",
                    "TextField.background", "PasswordField.background", "Label.foreground",
                    "Table.foreground", "TextArea.foreground", "Table.selectionBackground",
                    "Table.selectionForeground", "List.selectionBackground", "List.selectionForeground", "Component.focusedBorderColor", "Separator.foreground",
                    "Button.background", "Button.foreground", "Button.focusedBackground",
                    "Button.hoverBackground", "Button.pressedBackground", "Button.borderColor",
                    "ScrollBar.track", "ScrollBar.thumb", "TabbedPane.selectedBackground",
                    "TabbedPane.selectedForeground", "ProgressBar.foreground", "ProgressBar.background"
                };
                for (String key : keysToClear) {
                    UIManager.put(key, null);
                }
                UIManager.setLookAndFeel(new FlatLightLaf());
                
                // Pronounced borders in light mode (white mode)
                java.awt.Color pronouncedBorder = new javax.swing.plaf.ColorUIResource(180, 185, 195);
                UIManager.put("Component.borderColor", pronouncedBorder);
                UIManager.put("Button.borderColor", pronouncedBorder);
                UIManager.put("Separator.foreground", new javax.swing.plaf.ColorUIResource(200, 205, 215));

                // Soft/Accent light tab styles
                UIManager.put("TabbedPane.selectedBackground", new javax.swing.plaf.ColorUIResource(new Color(0, 120, 150, 25)));
                UIManager.put("TabbedPane.selectedForeground", new Color(20, 30, 40));
                UIManager.put("TabbedPane.underlineColor", new Color(0, 120, 150));

                // Card panel & UI styling variables for light mode
                UIManager.put("Card.background", new Color(240, 243, 248, 180)); 
                UIManager.put("Card.border", new Color(0, 0, 0, 24)); 
                UIManager.put("Card.placeholder", new Color(230, 235, 242, 160)); 
                UIManager.put("Card.placeholderBorder", new Color(0, 0, 0, 18));
                UIManager.put("Toolbar.customBg", new Color(240, 243, 248, 128));
                UIManager.put("SlimStat.titleForeground", new Color(90, 100, 110));
                UIManager.put("SlimStat.valueForeground", new Color(0, 120, 150));
                UIManager.put("SlimStat.barForeground", new Color(0, 120, 150));
                UIManager.put("SlimStat.barBackground", new Color(225, 230, 240));
                UIManager.put("MainTabs.gradientStart", new Color(245, 247, 250));
                UIManager.put("MainTabs.gradientEnd", new Color(255, 255, 255));
                UIManager.put("MainTabs.glowStart", new Color(0, 120, 150, 8));
                UIManager.put("PromptLab.presetForeground", new Color(30, 100, 200));
            }
            
            // Re-apply global arcs which might be cleared by setLookAndFeel
            UIManager.put("Button.arc", 12);
            UIManager.put("Component.arc", 16);
            UIManager.put("TextComponent.arc", 12);
            UIManager.put("ProgressBar.arc", 999);

            // Update text area backgrounds / foregrounds dynamically if already instantiated
            if (consoleOutput != null) {
                if (darkMode) {
                    consoleOutput.setBackground(UIManager.getColor("TextArea.background"));
                    consoleOutput.setForeground(UIManager.getColor("TextArea.foreground"));
                } else {
                    consoleOutput.setBackground(new Color(245, 247, 250));
                    consoleOutput.setForeground(new Color(30, 30, 30));
                }
            }
            if (promptLabConsole != null) {
                if (darkMode) {
                    promptLabConsole.setBackground(UIManager.getColor("TextArea.background"));
                    promptLabConsole.setForeground(UIManager.getColor("TextArea.foreground"));
                } else {
                    promptLabConsole.setBackground(new Color(245, 247, 250));
                    promptLabConsole.setForeground(new Color(30, 30, 30));
                }
            }
            
            if (videoArchitectTab != null) {
                videoArchitectTab.updateTheme(darkMode);
            }
            if (blueprintGalleryTab != null) {
                blueprintGalleryTab.updateTheme(darkMode);
            }
            if (downloadManagerView != null) {
                SwingUtilities.updateComponentTreeUI(downloadManagerView);
            }
            if (promptLabView != null) {
                SwingUtilities.updateComponentTreeUI(promptLabView);
                promptLabView.updateTheme(darkMode);
            }
            
            FlatLaf.updateUI();
            SwingUtilities.updateComponentTreeUI(this);
        } catch (Exception e) {
            logger.error("Theme setup failed: " + e.getMessage());
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
            logger.error("Could not load app icon: " + e.getMessage());
        }
    }

    private void setupTrayIcon() {
        if (!de.tki.comfymodels.util.PlatformUtils.isSystemTraySupported()) {
            logger.error("[System-Tray] Not supported on this platform (e.g. Wayland). Background mode disabled.");
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

        TrayIcon trayIcon = new TrayIcon(trayImage, "Companion for ComfyUI", popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> setVisible(true));

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            logger.error("TrayIcon could not be added.");
        }
    }

    private boolean promptForPassword() {
        setupTheme(configService.isDarkMode());
        String defaultPass = "companion_default_vault_key";
        try {
            configService.unlock(defaultPass);
            if (configService.isVaultFresh()) {
                String url = "https://raw.githubusercontent.com/Comfy-Org/ComfyUI-Manager/main/model-list.json";
                modelListService.importFromUrl(url);
            }
            return true;
        } catch (Exception e) {
            logger.warn("Could not unlock vault with default password. Resetting vault to start clean...");
            try {
                configService.resetVault();
                configService.unlock(defaultPass);
                if (configService.isVaultFresh()) {
                    String url = "https://raw.githubusercontent.com/Comfy-Org/ComfyUI-Manager/main/model-list.json";
                    modelListService.importFromUrl(url);
                }
                return true;
            } catch (Exception ex) {
                logger.error("Failed to reset and unlock vault: {}", ex.getMessage(), ex);
                return false;
            }
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
            backgroundExecutor.execute(() -> {
                lifecycleService.stop();
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                startComfyAndReload();
            });
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
        if (hideComfyuiCheck != null) hideComfyuiCheck.setSelected(configService.isHideComfyUI());
        if (promptLabCheck != null) promptLabCheck.setSelected(configService.isPromptLabEnabled());
        if (videoArchitectCheck != null) videoArchitectCheck.setSelected(configService.isVideoArchitectEnabled());
        if (blueprintGalleryCheck != null) blueprintGalleryCheck.setSelected(configService.isBlueprintGalleryEnabled());
    }

    public void updateTabVisibility() {
        if (mainTabs == null) return;
        java.awt.Component selectedComp = mainTabs.getSelectedComponent();
        mainTabs.removeAll();
        
        if (dashboardPanel != null) {
            mainTabs.addTab("🏠 Dashboard", dashboardPanel);
        }
        if (downloadManagerPanel != null) {
            mainTabs.addTab("📥 Download Manager", downloadManagerPanel);
        }
        // Gallery comes after Download Manager and before Blueprint Gallery
        if (galleryPanel != null) {
            mainTabs.addTab("🖼️ Gallery", galleryPanel);
        }
        if (configService.isBlueprintGalleryEnabled() && blueprintGalleryWrapper != null) {
            mainTabs.addTab("📂 Blueprint Gallery", blueprintGalleryWrapper);
        }
        if (configService.isPromptLabEnabled() && promptLabPanel != null) {
            mainTabs.addTab("🖼️ Image Lab", promptLabPanel);
        }
        if (configService.isVideoArchitectEnabled() && videoArchitectWrapper != null) {
            mainTabs.addTab("🎬 Video Architect (Beta)", videoArchitectWrapper);
        }
        if (settingsPanel != null) {
            mainTabs.addTab("⚙️ Settings", settingsPanel);
        }
        
        if (selectedComp != null) {
            int index = mainTabs.indexOfComponent(selectedComp);
            if (index != -1) {
                mainTabs.setSelectedIndex(index);
            } else {
                mainTabs.setSelectedIndex(0);
            }
        }
    }

    private boolean isOllamaConfigured() {
        return false;
    }

    private void updateAiModelDisplay() {
        backgroundExecutor.execute(() -> {
            final boolean hasGemma = localAIService != null && localAIService.isLocalGemmaDownloaded();
            SwingUtilities.invokeLater(() -> {
                if (btnSuggestSubject != null) {
                    if (hasGemma) {
                        btnSuggestSubject.setToolTipText("Suggest creative expansions for this subject using local Gemma.");
                    } else {
                        btnSuggestSubject.setToolTipText("Download local Gemma model to unlock suggestions.");
                    }
                }
                if (activeAiModelLabel != null) {
                    activeAiModelLabel.setText(hasGemma ? "Active AI: Local Gemma" : "Active AI: None / Cloud");
                }
            });
        });
    }

    private void initUI() {
        loadIcon();
        setTitle("Companion for ComfyUI");
        setSize(1450, 950);
        setMinimumSize(new java.awt.Dimension(1200, 800));
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        getRootPane().putClientProperty("flatlaf.useWindowDecorations", true);
        
        // Define rootPanel that paints the premium background gradient and radial glow globally
        JPanel rootPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                
                Color start = UIManager.getColor("MainTabs.gradientStart");
                Color end = UIManager.getColor("MainTabs.gradientEnd");
                if (start == null) start = new Color(14, 15, 17);
                if (end == null) end = new Color(24, 28, 38);
                GradientPaint bgGrad = new GradientPaint(0, 0, start, 0, h, end);
                g2.setPaint(bgGrad);
                g2.fillRect(0, 0, w, h);
                
                Color glowStart = UIManager.getColor("MainTabs.glowStart");
                if (glowStart == null) glowStart = new Color(0, 240, 255, 12);
                float[] dist = {0.0f, 1.0f};
                Color[] colors = {glowStart, new Color(0, 0, 0, 0)};
                RadialGradientPaint glow = new RadialGradientPaint(
                    new java.awt.geom.Point2D.Float(w * 0.85f, h * 0.15f),
                    Math.max(w, h) * 0.45f,
                    dist,
                    colors
                );
                g2.setPaint(glow);
                g2.fillRect(0, 0, w, h);
                
                g2.dispose();
                super.paintComponent(g);
            }
        };
        rootPanel.setOpaque(true);

        // Header Panel with glassmorphism styling
        JPanel headerPanel = new JPanel(new BorderLayout(15, 10)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                
                boolean dark = configService.isDarkMode();
                // Deep dark background for header
                Color bgColor = dark ? new Color(10, 11, 14, 230) : new Color(255, 255, 255, 120);
                g2.setColor(bgColor);
                g2.fillRect(0, 0, w, h);
                
                // Vibrant cyan accent line for dark mode
                g2.setColor(dark ? new Color(38, 255, 223, 150) : new Color(0, 120, 150, 30));
                g2.fillRect(0, h - 1, w, 1);
                
                g2.dispose();
            }
        };
        headerPanel.setOpaque(false);
        headerPanel.setPreferredSize(new Dimension(0, 60));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        JPanel leftHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        leftHeader.setOpaque(false);
        JLabel logoLabel = new JLabel("🌀");
        logoLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 22));
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
        titleLabel.setForeground(configService.isDarkMode() ? new Color(30, 190, 170) : new Color(0, 102, 204));
        
        JLabel tagline = new JLabel("|  Unified AI Model Manager & Image Lab");
        tagline.setFont(new Font("SansSerif", Font.ITALIC, 11));
        tagline.setForeground(configService.isDarkMode() ? new Color(112, 138, 144) : Color.GRAY);
        
        leftHeader.add(logoLabel);
        leftHeader.add(titleLabel);
        leftHeader.add(tagline);
        headerPanel.add(leftHeader, BorderLayout.WEST);

        JPanel rightHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 5));
        rightHeader.setOpaque(false);
        
        // ── Blueprint Scan Progress Indicator (Header) ──
        JPanel headerBlueprintProgressPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        headerBlueprintProgressPanel.setOpaque(false);
        headerBlueprintProgressPanel.setVisible(modelArchitectureService != null && !modelArchitectureService.isBlueprintAnalysisCompleted());
        
        JLabel headerBlueprintLabel = new JLabel("🔄 Blueprints: 0%") {
            @Override
            public void updateUI() {
                super.updateUI();
                if (configService != null) {
                    setForeground(configService.isDarkMode() ? new Color(30, 190, 170) : new Color(0, 102, 204));
                }
            }
        };
        headerBlueprintLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        headerBlueprintLabel.setForeground(configService.isDarkMode() ? new Color(30, 190, 170) : new Color(0, 102, 204));
        headerBlueprintLabel.setToolTipText("Blueprint scanning in progress...");
        
        JProgressBar headerBlueprintBar = new JProgressBar(0, 100) {
            @Override
            public void updateUI() {
                super.updateUI();
                putClientProperty("FlatLaf.style", "arc: 999; foreground: $SlimStat.barForeground; background: $SlimStat.barBackground;");
            }
        };
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
                        headerBlueprintLabel.setText("🔄 Blueprints: " + percent + "%");
                        headerBlueprintLabel.setToolTipText(currentFileName != null && !currentFileName.isEmpty()
                            ? "Scanning: " + currentFileName
                            : "Scanning Blueprints...");
                        headerBlueprintBar.setValue(percent);
                        headerBlueprintProgressPanel.setVisible(true);
                    }
                    rightHeader.revalidate();
                    rightHeader.repaint();
                });
            });
        }
        
        JLabel activeProfileLabel = new JLabel("👤 Profile: Loading...");
        activeProfileLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        activeProfileLabel.setForeground(configService.isDarkMode() ? new Color(224, 248, 245) : Color.GRAY);
        
        JLabel globalStatusIndicator = new JLabel("Server: Offline 🔴");
        globalStatusIndicator.setFont(new Font("SansSerif", Font.BOLD, 12));
        globalStatusIndicator.setForeground(configService.isDarkMode() ? new Color(112, 138, 144) : Color.GRAY);
        
        JButton quickActionBtn = new JButton("▶ Start");
        quickActionBtn.putClientProperty("JButton.buttonType", "roundRect");
        quickActionBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        quickActionBtn.setFocusable(false);
        
        quickActionBtn.addActionListener(e -> {
            boolean running = processController.isRunning();
            if (running) {
                int confirm = JOptionPane.showConfirmDialog(this, "Stop ComfyUI server?", "Stop Server", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    processController.stop();
                }
            } else {
                String activeIdVal = configService.getActiveProfile();
                List<de.tki.comfymodels.domain.LaunchProfile> profilesVal = profileManager.loadProfiles();
                de.tki.comfymodels.domain.LaunchProfile activeProfileVal = profilesVal.stream()
                    .filter(p -> p.id().equals(activeIdVal))
                    .findFirst()
                    .orElse(null);
                if (activeProfileVal == null && !profilesVal.isEmpty()) activeProfileVal = profilesVal.get(0);
                
                if (activeProfileVal != null) {
                    startComfyUI(activeProfileVal, false, !configService.isHideComfyUI());
                } else {
                    JOptionPane.showMessageDialog(this, "No launch profile available to start ComfyUI.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        
        rightHeader.add(headerBlueprintProgressPanel);
        rightHeader.add(activeProfileLabel);
        rightHeader.add(globalStatusIndicator);
        rightHeader.add(quickActionBtn);
        headerPanel.add(rightHeader, BorderLayout.EAST);

        Timer headerTimer = new Timer(1000, e -> {
            boolean running = processController.isRunning();
            boolean starting = false;
            if (running) {
                if (processController.isProcessAlive() && !processController.isGuiLineShown()) {
                    starting = true;
                } else if (lifecycleService.isProcessAlive() && !lifecycleService.isGuiLineShown()) {
                    starting = true;
                }
            }
            
            if (running) {
                if (starting) {
                    globalStatusIndicator.setText("Server: Starting 🟡");
                    globalStatusIndicator.setForeground(new Color(200, 160, 0)); // More subtle yellow/amber
                    quickActionBtn.setText("⏹ Stop");
                } else {
                    globalStatusIndicator.setText("Server: Running 🟢");
                    globalStatusIndicator.setForeground(new Color(30, 190, 170)); // Subtle Cyan/Teal
                    quickActionBtn.setText("⏹ Stop");
                }
            } else {
                globalStatusIndicator.setText("Server: Offline 🔴");
                globalStatusIndicator.setForeground(configService.isDarkMode() ? new Color(112, 138, 144) : Color.GRAY);
                quickActionBtn.setText("▶ Start");
            }
            
            // Sync active profile name (cached to avoid disk I/O on EDT every second)
            String activeIdVal = configService.getActiveProfile();
            long now = System.currentTimeMillis();
            if (!java.util.Objects.equals(activeIdVal, lastActiveProfileId) || (now - lastProfileCacheUpdate > 10000)) {
                lastActiveProfileId = activeIdVal;
                lastProfileCacheUpdate = now;
                List<de.tki.comfymodels.domain.LaunchProfile> profilesVal = profileManager.loadProfiles();
                de.tki.comfymodels.domain.LaunchProfile activeProfileVal = profilesVal.stream()
                    .filter(p -> p.id().equals(activeIdVal))
                    .findFirst()
                    .orElse(null);
                if (activeProfileVal == null && !profilesVal.isEmpty()) activeProfileVal = profilesVal.get(0);
                cachedActiveProfileName = (activeProfileVal != null) ? activeProfileVal.name() : "None";
            }
            activeProfileLabel.setText("👤 Profile: " + cachedActiveProfileName);

            // Sync header blueprint scan progress
            if (modelArchitectureService != null) {
                if (!modelArchitectureService.isBlueprintAnalysisCompleted() || modelArchitectureService.isAnalyzing()) {
                    int pct = modelArchitectureService.getBlueprintProgressPercent();
                    String file = modelArchitectureService.getBlueprintProgressFileName();
                    headerBlueprintLabel.setText("🔄 Blueprints: " + pct + "%");
                    headerBlueprintLabel.setToolTipText(file != null && !file.isEmpty() ? "Scanning: " + file : "Scanning Blueprints...");
                    headerBlueprintBar.setValue(pct);
                    if (!headerBlueprintProgressPanel.isVisible()) {
                        headerBlueprintProgressPanel.setVisible(true);
                        rightHeader.revalidate();
                        rightHeader.repaint();
                    }
                } else if (headerBlueprintProgressPanel.isVisible()) {
                    headerBlueprintProgressPanel.setVisible(false);
                    rightHeader.revalidate();
                    rightHeader.repaint();
                }
            }
        });
        headerTimer.start();
        
        this.mainTabs = new JTabbedPane();
        mainTabs.setName("mainTabbedPane");
        mainTabs.setOpaque(false);
        mainTabs.setFont(new Font("SansSerif", Font.BOLD, 13));
        mainTabs.putClientProperty("JTabbedPane.tabType", "card");
        mainTabs.putClientProperty("JTabbedPane.showTabSeparators", true);
        mainTabs.putClientProperty("JTabbedPane.tabSeparatorsFullHeight", true);

        // TAB 1: DASHBOARD
        this.dashboardPanel = createDashboardPanel(mainTabs);

        // TAB 2: DOWNLOAD MANAGER
        this.downloadManagerPanel = createManagerPanel(mainTabs);

        // TAB 3: PROMPT LAB
        this.promptLabPanel = createPromptLabPanel();

        // TAB 4: GALLERY
        this.galleryPanel = new de.tki.comfymodels.ui.OutputGalleryPanel(configService);

        // TAB 5: VIDEO ARCHITECT
        this.videoArchitectWrapper = new JPanel(new BorderLayout());
        this.videoArchitectWrapper.setName("videoArchitectWrapper");
        this.videoArchitectWrapper.setOpaque(false);
        if (videoArchitectTab != null) {
            this.videoArchitectWrapper.add(videoArchitectTab, BorderLayout.CENTER);
        } else {
            JPanel fallback = new JPanel(new GridBagLayout());
            fallback.setOpaque(false);
            JLabel label = new JLabel("🎬 Video Architect (Beta) is disabled or initializing...");
            label.setFont(new Font("SansSerif", Font.BOLD, 14));
            label.setForeground(configService.isDarkMode() ? Color.LIGHT_GRAY : Color.DARK_GRAY);
            fallback.add(label);
            this.videoArchitectWrapper.add(fallback, BorderLayout.CENTER);
        }


        // TAB 6: MODEL MANAGER (Blueprint Gallery)
        this.blueprintGalleryWrapper = new JPanel(new BorderLayout());
        this.blueprintGalleryWrapper.setName("blueprintGalleryWrapper");
        this.blueprintGalleryWrapper.setOpaque(false);
        if (blueprintGalleryTab != null) {
            this.blueprintGalleryWrapper.add(blueprintGalleryTab, BorderLayout.CENTER);
            blueprintGalleryTab.setOnDataLoadedCallback(this::refreshPromptLabModels);
        } else {

            this.blueprintGalleryWrapper.add(new JPanel(), BorderLayout.CENTER);
        }

        // TAB 7: SETTINGS
        this.settingsPanel = createSettingsPanel();

        // Update active visible tabs based on persistent config flags
        updateTabVisibility();

        rootPanel.add(headerPanel, BorderLayout.NORTH);
        rootPanel.add(mainTabs, BorderLayout.CENTER);
        setContentPane(rootPanel);
    }

    private static final String DEFAULT_PROMPT_JSON = "{\n" +
            "  \"prompt\": {\n" +
            "    \"3\": {\n" +
            "      \"inputs\": {\n" +
            "        \"seed\": 42,\n" +
            "        \"steps\": 20,\n" +
            "        \"cfg\": 8.0,\n" +
            "        \"sampler_name\": \"euler\",\n" +
            "        \"scheduler\": \"normal\",\n" +
            "        \"denoise\": 1.0,\n" +
            "        \"model\": [\n" +
            "          \"4\",\n" +
            "          0\n" +
            "        ],\n" +
            "        \"positive\": [\n" +
            "          \"6\",\n" +
            "          0\n" +
            "        ],\n" +
            "        \"negative\": [\n" +
            "          \"7\",\n" +
            "          0\n" +
            "        ],\n" +
            "        \"latent_image\": [\n" +
            "          \"5\",\n" +
            "          0\n" +
            "        ]\n" +
            "      },\n" +
            "      \"class_type\": \"KSampler\"\n" +
            "    },\n" +
            "    \"4\": {\n" +
            "      \"inputs\": {\n" +
            "        \"ckpt_name\": \"v1-5-pruned-emaonly.safetensors\"\n" +
            "      },\n" +
            "      \"class_type\": \"CheckpointLoaderSimple\"\n" +
            "    },\n" +
            "    \"5\": {\n" +
            "      \"inputs\": {\n" +
            "        \"width\": 512,\n" +
            "        \"height\": 512,\n" +
            "        \"batch_size\": 1\n" +
            "      },\n" +
            "      \"class_type\": \"EmptyLatentImage\"\n" +
            "    },\n" +
            "    \"6\": {\n" +
            "      \"inputs\": {\n" +
            "        \"text\": \"cybernetic tiger, neon-lit alley, photorealistic, 8k, unreal engine 5 render\",\n" +
            "        \"clip\": [\n" +
            "          \"4\",\n" +
            "          1\n" +
            "        ]\n" +
            "      },\n" +
            "      \"class_type\": \"CLIPTextEncode\"\n" +
            "    },\n" +
            "    \"7\": {\n" +
            "      \"inputs\": {\n" +
            "        \"text\": \"bad hands, text, blurry, worst quality, low quality\",\n" +
            "        \"clip\": [\n" +
            "          \"4\",\n" +
            "          1\n" +
            "        ]\n" +
            "      },\n" +
            "      \"class_type\": \"CLIPTextEncode\"\n" +
            "    },\n" +
            "    \"8\": {\n" +
            "      \"inputs\": {\n" +
            "        \"samples\": [\n" +
            "          \"3\",\n" +
            "          0\n" +
            "        ],\n" +
            "        \"vae\": [\n" +
            "          \"4\",\n" +
            "          2\n" +
            "        ]\n" +
            "      },\n" +
            "      \"class_type\": \"VAEDecode\"\n" +
            "    },\n" +
            "    \"9\": {\n" +
            "      \"inputs\": {\n" +
            "        \"filename_prefix\": \"ComfyUI\",\n" +
            "        \"images\": [\n" +
            "          \"8\",\n" +
            "          0\n" +
            "        ]\n" +
            "      },\n" +
            "      \"class_type\": \"SaveImage\"\n" +
            "    }\n" +
            "  }\n" +
            "}";
    private JPanel createPromptLabPanel() {
        if (promptLabView != null && promptLabController != null) {
            promptLabController.setView(promptLabView);
            promptLabController.setBlueprintGalleryTab(blueprintGalleryTab);
            promptLabController.setSendPromptHandler(this::sendPromptToComfyUI);
            promptLabController.setSuggestSubjectHandler(this::suggestSubjectCompletions);
            syncPromptLabViewFields();
            promptLabController.loadPromptLabSession();
            promptLabController.updatePromptLabJson();
            return promptLabView;
        }
        return new JPanel();
    }

    private void syncPromptLabViewFields() {
        if (promptLabView == null) return;
        this.promptModelCombo = promptLabView.getPromptModelCombo();
        this.promptPresetLabel = promptLabView.getPromptPresetLabel();
        this.promptSubjectField = promptLabView.getPromptSubjectField();
        this.btnSuggestSubject = promptLabView.getBtnSuggestSubject();
        this.promptSubjectSuggestionsWrapper = promptLabView.getPromptSubjectSuggestionsWrapper();
        this.promptSubjectSuggestionsPanel = promptLabView.getPromptSubjectSuggestionsPanel();
        this.promptLabLeftPanel = promptLabView.getPromptLabLeftPanel();
        this.promptNegativeField = promptLabView.getPromptNegativeField();
        this.promptImageInputPanel = promptLabView.getPromptImageInputPanel();
        this.promptImageFileField = promptLabView.getPromptImageFileField();
        this.promptWidthSpinner = promptLabView.getPromptWidthSpinner();
        this.promptHeightSpinner = promptLabView.getPromptHeightSpinner();
        this.promptBatchSizeSpinner = promptLabView.getPromptBatchSizeSpinner();
        this.promptStepsSpinner = promptLabView.getPromptStepsSpinner();
        this.promptCfgSpinner = promptLabView.getPromptCfgSpinner();
        this.promptDenoiseSpinner = promptLabView.getPromptDenoiseSpinner();
        this.promptSamplerCombo = promptLabView.getPromptSamplerCombo();
        this.promptSchedulerCombo = promptLabView.getPromptSchedulerCombo();
        this.promptLabRightTabbedPane = promptLabView.getPromptLabRightTabbedPane();
        this.promptImagePreviewLabel = promptLabView.getPromptImagePreviewLabel();
        this.promptLabProgressBar = promptLabView.getPromptLabProgressBar();
        this.promptJsonArea = promptLabView.getPromptJsonArea();
        this.promptLabConsole = promptLabView.getPromptLabConsole();
        this.btnSendToComfy = promptLabView.getBtnSendToComfy();
        this.promptAssembleArea = promptLabView.getPromptAssembleArea();
        this.chkAnime = promptLabView.getChkAnime();
        this.chkFantasy = promptLabView.getChkFantasy();
    }

    private void applyModelPreset(String modelNameRaw) {
        if (promptLabController != null) promptLabController.applyModelPreset(modelNameRaw);
    }

    private String findExactUnetName(String selectedModel) {
        return promptLabController != null ? promptLabController.findExactUnetName(selectedModel) : "";
    }

    private String findExactCheckpointName(String selectedModel) {
        return promptLabController != null ? promptLabController.findExactCheckpointName(selectedModel) : "";
    }

    private String resolveClipType(String clipModel, String selectedModel) {
        if (promptLabController != null) {
            return promptLabController.resolveClipType(clipModel, selectedModel);
        }
        String lower = clipModel != null ? clipModel.toLowerCase() : "";
        if (lower.contains("gemma") || lower.contains("qwen_3_4b") || lower.contains("lumina2") || lower.contains("lumina-2")) {
            return "lumina2";
        } else if (lower.contains("wan") || lower.contains("qwen_2.5_vl") || lower.contains("umt5")) {
            return "wan";
        } else if (lower.contains("t5xxl") || lower.contains("t5-xxl") || lower.contains("t5_xxl") || lower.contains("t5_fp8") || lower.contains("t5_fp16")) {
            if (selectedModel != null && (selectedModel.toLowerCase().contains("sd3") || selectedModel.toLowerCase().contains("stable_diffusion_3"))) {
                return "sd3";
            }
            return "flux";
        } else if (lower.contains("mistral") || lower.contains("flux2") || lower.contains("klein")) {
            return "flux2";
        } else if (lower.contains("sd3") || lower.contains("stable_diffusion_3")) {
            return "sd3";
        } else if (lower.contains("clip_l") || lower.contains("clip_g") || lower.contains("vi-clip") || lower.contains("sd15") || lower.contains("sdxl")) {
            return "stable_diffusion";
        } else if (lower.contains("mochi")) {
            return "mochi";
        } else if (lower.contains("ltxv") || lower.contains("ltx")) {
            return "ltxv";
        } else if (lower.contains("cosmos")) {
            return "cosmos";
        }
        
        String candidate = "stable_diffusion";
        if (selectedModel != null) {
            String selLower = selectedModel.toLowerCase();
            if (selLower.contains("flux-2-klein") || selLower.contains("flux2-klein") || selLower.contains("klein")) {
                candidate = "flux2";
            } else if (selLower.contains("flux") || selLower.contains("schnell")) {
                candidate = "flux";
            } else if (selLower.contains("sd3") || selLower.contains("stable_diffusion_3")) {
                candidate = "sd3";
            } else if (selLower.contains("longcat") || selLower.contains("lumina") || selLower.contains("acestep") || selLower.contains("z_image") || selLower.contains("z-image")) {
                candidate = "lumina2";
            } else if (selLower.contains("wan")) {
                candidate = "wan";
            }
        }
        if (comfyClipTypes.contains(candidate)) {
            return candidate;
        }
        if (comfyClipTypes.contains("stable_diffusion") && candidate.equals("stable_diffusion")) {
            return "stable_diffusion";
        }
        if (!comfyClipTypes.isEmpty() && !comfyClipTypes.contains(candidate) && !candidate.equals("flux") && !candidate.equals("lumina2") && !candidate.equals("wan") && !candidate.equals("sd3") && !candidate.equals("flux2")) {
            return comfyClipTypes.iterator().next();
        }
        return candidate;
    }

    private boolean modelsMatch(String modelA, String modelB) {
        if (promptLabController != null) {
            return promptLabController.modelsMatch(modelA, modelB);
        }
        if (modelA == null || modelB == null) return false;
        String a = modelA.replace("\\", "/").toLowerCase();
        String b = modelB.replace("\\", "/").toLowerCase();
        if (a.startsWith("checkpoints/")) a = a.substring(12);
        if (a.startsWith("diffusion_models/")) a = a.substring(17);
        if (a.startsWith("unet/")) a = a.substring(5);
        if (b.startsWith("checkpoints/")) b = b.substring(12);
        if (b.startsWith("diffusion_models/")) b = b.substring(17);
        if (b.startsWith("unet/")) b = b.substring(5);
        for (String ext : new String[]{".safetensors", ".ckpt", ".pt", ".bin"}) {
            if (a.endsWith(ext)) a = a.substring(0, a.length() - ext.length());
            if (b.endsWith(ext)) b = b.substring(0, b.length() - ext.length());
        }
        return a.equals(b);
    }

    private boolean isDiffusionModel(String modelName) {
        if (promptLabController != null) {
            return promptLabController.isDiffusionModel(modelName);
        }
        if (modelName == null) return false;
        String lower = modelName.toLowerCase();
        return lower.contains("diffusion_models") || lower.contains("z_image") || lower.contains("z-image") || lower.contains("acestep") || lower.contains("flux") || lower.contains("lumina") || lower.contains("wan");
    }

    private String resolveClipForModel(String modelName) {
        if (promptLabController != null) {
            return promptLabController.resolveClipForModel(modelName);
        }
        if (modelName == null) return "qwen_3_4b.safetensors";
        String lower = modelName.toLowerCase();
        if (lower.contains("z_image") || lower.contains("z-image") || lower.contains("acestep") || lower.contains("longcat") || lower.contains("lumina")) {
            for (String clip : comfyClips) {
                String cLower = clip.toLowerCase();
                if (cLower.contains("gemma4") || cLower.contains("gemma-4") || cLower.contains("gemma_4") ||
                    cLower.contains("gemma2") || cLower.contains("gemma-2") || cLower.contains("gemma_2") ||
                    cLower.contains("gemma")) {
                    return clip;
                }
            }
            for (String clip : comfyClips) {
                String cLower = clip.toLowerCase();
                if (cLower.contains("qwen_3_4b") || cLower.contains("qwen-3-4b") || cLower.contains("qwen_3.4b") || cLower.contains("qwen3")) {
                    return clip;
                }
            }
            return "gemma4_e2b_it_bf16.safetensors";
        } else if (lower.contains("wan")) {
            for (String clip : comfyClips) {
                String cLower = clip.toLowerCase();
                if (cLower.contains("qwen_2.5_vl") || cLower.contains("umt5")) {
                    return clip;
                }
            }
            return "qwen/qwen_2.5_vl_7b_fp8_scaled.safetensors";
        } else if (lower.contains("flux-2-klein") || lower.contains("flux2-klein") || lower.contains("klein")) {
            for (String clip : comfyClips) {
                String cLower = clip.toLowerCase();
                if (cLower.contains("mistral") || cLower.contains("flux2") || cLower.contains("klein")) {
                    return clip;
                }
            }
            return "mistral_3_small_flux2_bf16.safetensors";
        } else if (lower.contains("flux") || lower.contains("schnell") || lower.contains("dev")) {
            for (String clip : comfyClips) {
                String cLower = clip.toLowerCase();
                if (cLower.contains("t5xxl") || cLower.contains("t5-xxl") || cLower.contains("t5_xxl") ||
                    cLower.contains("t5_fp8") || cLower.contains("t5_fp16") || cLower.contains("t5xxl_fp8")) {
                    return clip;
                }
            }
            for (String clip : comfyClips) {
                String cLower = clip.toLowerCase();
                if (cLower.contains("t5") && !cLower.contains("umt5") && !cLower.contains("gemma") && !cLower.contains("qwen")) {
                    return clip;
                }
            }
            return "t5xxl_fp8_e4m3fn.safetensors";
        } else if (lower.contains("sd3") || lower.contains("stable_diffusion_3")) {
            for (String clip : comfyClips) {
                String cLower = clip.toLowerCase();
                if (cLower.contains("t5xxl") || cLower.contains("t5-xxl") || cLower.contains("clip_g") || cLower.contains("sd3")) {
                    return clip;
                }
            }
            return "t5xxl_fp8_e4m3fn.safetensors";
        }
        for (String clip : comfyClips) {
            String cLower = clip.toLowerCase();
            if (cLower.contains("clip_l") || cLower.contains("sd15") || cLower.contains("sdxl")) {
                return clip;
            }
        }
        return "clip_l.safetensors";
    }

    private String resolveVaeForModel(String modelName) {
        if (promptLabController != null) {
            return promptLabController.resolveVaeForModel(modelName);
        }
        if (modelName == null) return "ae.safetensors";
        String expected = "FLUX1/ae.safetensors";
        String lower = modelName.toLowerCase();
        if (lower.contains("z_image") || lower.contains("z-image") || lower.contains("acestep") || lower.contains("longcat") || lower.contains("lumina")) {
            expected = "ae.safetensors";
        } else if (lower.contains("wan")) {
            expected = "wan_2.1_vae.safetensors";
        } else if (lower.contains("flux")) {
            expected = "FLUX1/ae.safetensors";
        }
        
        String expectedClean = expected.replace("\\", "/");
        for (String vae : comfyVaes) {
            String vaeClean = vae.replace("\\", "/");
            if (vaeClean.equalsIgnoreCase(expectedClean) || vaeClean.endsWith("/" + expectedClean)) {
                return vae;
            }
        }
        String expectedName = expectedClean.contains("/") ? expectedClean.substring(expectedClean.lastIndexOf('/') + 1) : expectedClean;
        for (String vae : comfyVaes) {
            String vaeClean = vae.replace("\\", "/");
            if (vaeClean.equalsIgnoreCase(expectedName) || vaeClean.endsWith("/" + expectedName)) {
                return vae;
            }
        }
        boolean isExpectedAeOrFlux = expectedName.contains("flux") || expectedName.replace("vae", "").contains("ae");
        if (isExpectedAeOrFlux) {
            for (String vae : comfyVaes) {
                String vaeLower = vae.toLowerCase();
                if (vaeLower.contains("flux") || vaeLower.replace("vae", "").contains("ae")) {
                    return vae;
                }
            }
        }
        return expected;
    }

    private String getActualModelForPromptLab(String comboSelection) {
        return promptLabController != null ? promptLabController.getActualModelForPromptLab(comboSelection) : comboSelection;
    }

    private String getBlueprintNameForModel(String modelName) {
        return promptLabController != null ? promptLabController.getBlueprintNameForModel(modelName) : modelName;
    }

    private void updatePromptLabJson() {
        if (promptLabController != null) promptLabController.updatePromptLabJson();
    }

    private void updateComfyModelSets(JSONObject info) {
        if (promptLabController != null) promptLabController.updateComfyModelSets(info);
    }

    private void onPromptBlueprintSelected(String selectedName) {
        if (promptLabController != null) promptLabController.onPromptBlueprintSelected(selectedName);
    }

    private boolean isBlueprintNameMatch(String name1, String name2) {
        if (name1 == null || name2 == null) return false;
        String clean1 = name1.toLowerCase().replaceAll("[^a-zA-Z0-9]", " ").replaceAll("\\s+", " ").trim();
        String clean2 = name2.toLowerCase().replaceAll("[^a-zA-Z0-9]", " ").replaceAll("\\s+", " ").trim();
        if (clean1.equals(clean2)) return true;

        String[] words1 = clean1.split(" ");
        String[] words2 = clean2.split(" ");

        java.util.Set<String> set1 = new java.util.HashSet<>(java.util.Arrays.asList(words1));
        java.util.Set<String> set2 = new java.util.HashSet<>(java.util.Arrays.asList(words2));

        return set1.equals(set2) || clean1.contains(clean2) || clean2.contains(clean1);
    }

    private void populateInputFieldsFromBlueprint(String jsonStr) {
        if (jsonStr == null || jsonStr.isBlank()) return;
        try {
            ExtractedBlueprintData data = parseBlueprintWorkflowValues(jsonStr);
            if (data == null) return;

            if (data.positivePrompt != null) {
                if (promptSubjectField != null) {
                    promptSubjectField.setText(data.positivePrompt);
                }
            }
            if (data.negativePrompt != null) {
                if (promptNegativeField != null) {
                    promptNegativeField.setText(data.negativePrompt);
                }
            }
            if (data.width != null && data.width > 0 && promptWidthSpinner != null) {
                promptWidthSpinner.setValue(data.width);
            }
            if (data.height != null && data.height > 0 && promptHeightSpinner != null) {
                promptHeightSpinner.setValue(data.height);
            }
            if (data.batchSize != null && data.batchSize > 0 && promptBatchSizeSpinner != null) {
                promptBatchSizeSpinner.setValue(data.batchSize);
            }
            if (data.steps != null && data.steps > 0 && promptStepsSpinner != null) {
                promptStepsSpinner.setValue(data.steps);
            }
            if (data.cfg != null && data.cfg >= 0 && promptCfgSpinner != null) {
                promptCfgSpinner.setValue(data.cfg);
            }
            if (data.denoise != null && data.denoise >= 0 && data.denoise <= 1.0 && promptDenoiseSpinner != null) {
                promptDenoiseSpinner.setValue(data.denoise);
            }
            if (data.samplerName != null && promptSamplerCombo != null) {
                selectComboItemIgnoreCase(promptSamplerCombo, data.samplerName);
            }
            if (data.scheduler != null && promptSchedulerCombo != null) {
                selectComboItemIgnoreCase(promptSchedulerCombo, data.scheduler);
            }
        } catch (Exception ex) {
            logger.warn("Could not populate UI fields from blueprint workflow JSON: " + ex.getMessage());
        }
    }

    private void selectComboItemIgnoreCase(JComboBox<String> combo, String target) {
        if (combo == null || target == null || target.isBlank()) return;
        for (int i = 0; i < combo.getItemCount(); i++) {
            String item = combo.getItemAt(i);
            if (item != null && item.equalsIgnoreCase(target.trim())) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }

    private static class ExtractedBlueprintData {
        String positivePrompt;
        String negativePrompt;
        Integer width;
        Integer height;
        Integer batchSize;
        Integer steps;
        Double cfg;
        String samplerName;
        String scheduler;
        Double denoise;
    }

    private ExtractedBlueprintData parseBlueprintWorkflowValues(String jsonStr) {
        if (jsonStr == null || jsonStr.isBlank()) return null;

        ExtractedBlueprintData data = new ExtractedBlueprintData();
        java.util.List<String> textPrompts = new java.util.ArrayList<>();

        try {
            org.json.JSONObject root = new org.json.JSONObject(jsonStr);

            // ── Format A: GUI format with "nodes" array ──
            if (root.has("nodes") && root.get("nodes") instanceof org.json.JSONArray) {
                java.util.List<org.json.JSONObject> nodes = collectAllNodes(root);
                
                // First pass: map proxy widgets for Group Nodes
                java.util.Map<String, String> overriddenTexts = new java.util.HashMap<>();
                for (org.json.JSONObject node : nodes) {
                    org.json.JSONObject props = node.optJSONObject("properties");
                    if (props != null && props.has("proxyWidgets")) {
                        org.json.JSONArray proxyWidgets = props.optJSONArray("proxyWidgets");
                        org.json.JSONArray wVals = node.optJSONArray("widgets_values");
                        if (proxyWidgets != null && wVals != null) {
                            for (int i = 0; i < proxyWidgets.length(); i++) {
                                org.json.JSONArray pw = proxyWidgets.optJSONArray(i);
                                if (pw != null && pw.length() >= 2) {
                                    String innerNodeId = pw.optString(0);
                                    String widgetName = pw.optString(1);
                                    if ("text".equals(widgetName) && wVals.length() > i) {
                                        Object v = wVals.opt(i);
                                        if (v instanceof String) {
                                            overriddenTexts.put(innerNodeId, (String) v);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                for (org.json.JSONObject node : nodes) {
                    String type = node.optString("type", node.optString("class_type", ""));
                    org.json.JSONArray widgetValues = node.optJSONArray("widgets_values");
                    org.json.JSONObject inputsObj = node.optJSONObject("inputs");

                    // CLIPTextEncode / Text nodes
                    if (type.equalsIgnoreCase("CLIPTextEncode") || type.contains("CLIPText") || type.contains("TextEncode")) {
                        String text = overriddenTexts.get(String.valueOf(node.optInt("id")));
                        
                        if (text == null) {
                            if (widgetValues != null && widgetValues.length() > 0 && widgetValues.get(0) instanceof String) {
                                text = widgetValues.getString(0);
                            } else if (inputsObj != null && inputsObj.has("text")) {
                                text = inputsObj.optString("text", "");
                            }
                        }

                        if (text != null && !text.isBlank()) {
                            textPrompts.add(text.trim());
                        }
                    }

                    // Latent Dimensions & Batch size
                    if (type.contains("Latent") || type.contains("Empty")) {
                        if (widgetValues != null && widgetValues.length() >= 2) {
                            if (widgetValues.get(0) instanceof Number && widgetValues.get(1) instanceof Number) {
                                if (data.width == null) data.width = widgetValues.getInt(0);
                                if (data.height == null) data.height = widgetValues.getInt(1);
                                if (widgetValues.length() >= 3 && widgetValues.get(2) instanceof Number && data.batchSize == null) {
                                    data.batchSize = widgetValues.getInt(2);
                                }
                            }
                        }
                        if (inputsObj != null) {
                            if (inputsObj.has("width") && data.width == null) data.width = inputsObj.optInt("width");
                            if (inputsObj.has("height") && data.height == null) data.height = inputsObj.optInt("height");
                            if (inputsObj.has("batch_size") && data.batchSize == null) data.batchSize = inputsObj.optInt("batch_size");
                        }
                    }

                    // Sampler / Scheduler / Steps / CFG
                    if (type.contains("KSampler") || type.contains("Sampler")) {
                        if (inputsObj != null) {
                            if (inputsObj.has("steps") && data.steps == null) data.steps = inputsObj.optInt("steps");
                            if (inputsObj.has("cfg") && data.cfg == null) data.cfg = inputsObj.optDouble("cfg");
                            if (inputsObj.has("sampler_name") && data.samplerName == null) data.samplerName = inputsObj.optString("sampler_name");
                            if (inputsObj.has("scheduler") && data.scheduler == null) data.scheduler = inputsObj.optString("scheduler");
                            if (inputsObj.has("denoise") && data.denoise == null) data.denoise = inputsObj.optDouble("denoise");
                        }
                        if (widgetValues != null) {
                            for (int w = 0; w < widgetValues.length(); w++) {
                                Object v = widgetValues.get(w);
                                if (v instanceof String sVal) {
                                    String sLower = sVal.toLowerCase();
                                    if (data.samplerName == null && (sLower.equals("euler") || sLower.contains("dpm") || sLower.equals("heun") || sLower.equals("lms") || sLower.equals("ddim") || sLower.equals("uni_pc") || sLower.equals("lcm"))) {
                                        data.samplerName = sVal;
                                    }
                                    if (data.scheduler == null && (sLower.equals("normal") || sLower.equals("karras") || sLower.equals("exponential") || sLower.equals("sgm_uniform") || sLower.equals("simple") || sLower.equals("ddim_uniform"))) {
                                        data.scheduler = sVal;
                                    }
                                } else if (v instanceof Number nVal) {
                                    if (w == 2 && data.steps == null && nVal.intValue() > 0 && nVal.intValue() <= 200) {
                                        data.steps = nVal.intValue();
                                    }
                                    if (w == 3 && data.cfg == null && nVal.doubleValue() >= 0.0 && nVal.doubleValue() <= 50.0) {
                                        data.cfg = nVal.doubleValue();
                                    }
                                    if (w == 6 && data.denoise == null && nVal.doubleValue() >= 0.0 && nVal.doubleValue() <= 1.0) {
                                        data.denoise = nVal.doubleValue();
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Format B: API format or object map (key -> node) ──
            else {
                for (String key : root.keySet()) {
                    org.json.JSONObject node = root.optJSONObject(key);
                    if (node == null) continue;
                    String ct = node.optString("class_type", "");
                    org.json.JSONObject inp = node.optJSONObject("inputs");
                    if (inp == null) continue;

                    if (ct.equalsIgnoreCase("CLIPTextEncode") || ct.contains("CLIPText") || ct.contains("TextEncode")) {
                        if (inp.has("text") && inp.get("text") instanceof String) {
                            String t = inp.getString("text").trim();
                            if (!t.isBlank()) textPrompts.add(t);
                        }
                    }

                    if ((ct.contains("Latent") || ct.contains("Empty")) && (inp.has("width") || inp.has("height"))) {
                        if (inp.has("width") && data.width == null) data.width = inp.optInt("width");
                        if (inp.has("height") && data.height == null) data.height = inp.optInt("height");
                        if (inp.has("batch_size") && data.batchSize == null) data.batchSize = inp.optInt("batch_size");
                    }

                    if (ct.contains("KSampler") || ct.contains("Sampler")) {
                        if (inp.has("steps") && data.steps == null) data.steps = inp.optInt("steps");
                        if (inp.has("cfg") && data.cfg == null) data.cfg = inp.optDouble("cfg");
                        if (inp.has("sampler_name") && data.samplerName == null) data.samplerName = inp.optString("sampler_name");
                        if (inp.has("scheduler") && data.scheduler == null) data.scheduler = inp.optString("scheduler");
                        if (inp.has("denoise") && data.denoise == null) data.denoise = inp.optDouble("denoise");
                    }
                }
            }

            // Classify positive & negative prompts from textPrompts list
            for (String prompt : textPrompts) {
                String lower = prompt.toLowerCase();
                boolean isNeg = lower.contains("blurry") || lower.contains("bad anatomy") || lower.contains("low quality") || lower.contains("worst quality") || lower.contains("watermark") || lower.contains("bad hands");
                if (isNeg && data.negativePrompt == null) {
                    data.negativePrompt = prompt;
                } else if (!isNeg && data.positivePrompt == null) {
                    data.positivePrompt = prompt;
                }
            }
            if (data.positivePrompt == null && !textPrompts.isEmpty()) {
                data.positivePrompt = textPrompts.get(0);
                if (textPrompts.size() > 1 && data.negativePrompt == null) {
                    data.negativePrompt = textPrompts.get(1);
                }
            }
        } catch (Exception ex) {
            logger.warn("Error parsing blueprint workflow JSON: " + ex.getMessage());
        }

        return data;
    }

    private java.util.List<org.json.JSONObject> collectAllNodes(org.json.JSONObject root) {
        java.util.List<org.json.JSONObject> allNodes = new java.util.ArrayList<>();
        if (root == null) return allNodes;

        // 1. Root level nodes
        if (root.has("nodes") && root.get("nodes") instanceof org.json.JSONArray) {
            org.json.JSONArray nodes = root.getJSONArray("nodes");
            for (int i = 0; i < nodes.length(); i++) {
                org.json.JSONObject node = nodes.optJSONObject(i);
                if (node != null) {
                    allNodes.add(node);
                }
            }
        }

        // 2. Subgraph nodes via "definitions -> subgraphs"
        if (root.has("definitions") && root.get("definitions") instanceof org.json.JSONObject) {
            org.json.JSONObject definitions = root.getJSONObject("definitions");
            if (definitions.has("subgraphs") && definitions.get("subgraphs") instanceof org.json.JSONArray) {
                org.json.JSONArray subgraphs = definitions.getJSONArray("subgraphs");
                for (int i = 0; i < subgraphs.length(); i++) {
                    org.json.JSONObject subgraph = subgraphs.optJSONObject(i);
                    if (subgraph != null && subgraph.has("nodes") && subgraph.get("nodes") instanceof org.json.JSONArray) {
                        org.json.JSONArray subnodes = subgraph.getJSONArray("nodes");
                        for (int j = 0; j < subnodes.length(); j++) {
                            org.json.JSONObject node = subnodes.optJSONObject(j);
                            if (node != null) {
                                allNodes.add(node);
                            }
                        }
                    }
                }
            }
        }

        // 3. Subgraph nodes via "extra_data -> subgraphs"
        if (root.has("extra_data") && root.get("extra_data") instanceof org.json.JSONObject) {
            org.json.JSONObject extraData = root.getJSONObject("extra_data");
            if (extraData.has("subgraphs") && extraData.get("subgraphs") instanceof org.json.JSONArray) {
                org.json.JSONArray subgraphs = extraData.getJSONArray("subgraphs");
                for (int i = 0; i < subgraphs.length(); i++) {
                    org.json.JSONObject subgraph = subgraphs.optJSONObject(i);
                    if (subgraph != null && subgraph.has("nodes") && subgraph.get("nodes") instanceof org.json.JSONArray) {
                        org.json.JSONArray subnodes = subgraph.getJSONArray("nodes");
                        for (int j = 0; j < subnodes.length(); j++) {
                            org.json.JSONObject node = subnodes.optJSONObject(j);
                            if (node != null) {
                                allNodes.add(node);
                            }
                        }
                    }
                }
            }
        }

        return allNodes;
    }

    private boolean isSupportedPromptLabBlueprint(de.tki.comfymodels.ui.BlueprintGalleryTab.BlueprintEntry entry) {
        if (entry == null) return false;

        // 1. Exclude Cloud-Only workflows
        if (entry.registryWorkflow != null && entry.registryWorkflow.isCloudOnly()) {
            return false;
        }

        String name = entry.name != null ? entry.name.toLowerCase() : "";
        String filename = entry.filename != null ? entry.filename.toLowerCase() : "";
        String cat = entry.category != null ? entry.category.toLowerCase() : "";
        String desc = entry.description != null ? entry.description.toLowerCase() : "";

        // Hide Cloud-Only blueprints by keyword
        if (cat.contains("cloud") || name.contains("cloud") || desc.contains("cloud api")
                || name.contains("replicate") || desc.contains("replicate")
                || name.contains("fal.ai") || name.contains("runware") || name.contains("dall-e")
                || name.contains("openai") || name.contains("midjourney")) {
            return false;
        }

        // 2. Exclude Video, Audio, 3D, Pose, Depth, Upscale, ControlNet
        if (cat.contains("video") || cat.contains("animate") || cat.contains("motion") 
                || cat.contains("i2v") || cat.contains("t2v") || cat.contains("image to video")
                || cat.contains("depth") || cat.contains("pose") || cat.contains("upscal")
                || cat.contains("3d") || cat.contains("audio") || cat.contains("controlnet")
                || cat.contains("outpaint")) {
            return false;
        }

        if (isVideoModel(name) || isVideoModel(filename)) {
            return false;
        }
        if (name.contains("controlnet") || name.contains("upscale")) {
            return false;
        }

        // Must be Text-to-Image, Image Edit, or general
        return cat.contains("text to image") || cat.contains("txt2img") || cat.contains("text-to-image")
                || name.contains("text to image") || name.contains("txt2img") 
                || cat.contains("image edit") || cat.contains("image to image") || cat.contains("img2img") || cat.contains("inpaint")
                || name.contains("img2img") || name.contains("i2i") || name.contains("inpaint") || name.contains("image edit")
                || cat.equals("general") || cat.isEmpty();
    }

    private boolean isImageEditBlueprint(de.tki.comfymodels.ui.BlueprintGalleryTab.BlueprintEntry entry) {
        if (entry == null) return false;
        String name = entry.name != null ? entry.name.toLowerCase() : "";
        String cat = entry.category != null ? entry.category.toLowerCase() : "";
        
        return cat.contains("image edit") || cat.contains("image to image") || cat.contains("img2img") || cat.contains("inpaint")
                || name.contains("img2img") || name.contains("i2i") || name.contains("inpaint") || name.contains("image edit");
    }

    private void refreshPromptLabModels() {
        if (promptLabController != null) promptLabController.refreshPromptLabModels();
    }

    private void scanModelsRecursively(java.io.File dir, String prefix, java.util.List<String> list) {
        java.io.File[] files = dir.listFiles();
        if (files != null) {
            for (java.io.File f : files) {
                if (f.isDirectory()) {
                    scanModelsRecursively(f, prefix.isEmpty() ? f.getName() : prefix + "/" + f.getName(), list);
                } else {
                    String name = f.getName().toLowerCase();
                    if (name.endsWith(".safetensors") || name.endsWith(".ckpt") || name.endsWith(".bin") || name.endsWith(".pt")) {
                        String relativePath = prefix.isEmpty() ? f.getName() : prefix + "/" + f.getName();
                        if (!isVideoModel(relativePath) && !list.contains(relativePath)) {
                            list.add(relativePath);
                        }
                    }
                }
            }
        }
    }

    private boolean isVideoModel(String modelPath) {
        if (modelPath == null) return false;
        String lower = modelPath.toLowerCase();
        return lower.contains("i2v") 
                || lower.contains("t2v") 
                || lower.contains("ti2v") 
                || lower.contains("video") 
                || lower.contains("svd") 
                || lower.contains("animatediff")
                || lower.contains("mochi") 
                || lower.contains("cogvideo")
                || lower.contains("ltx");
    }


    private void sendPromptToComfyUI() {
        final String comfyUrl = configService.getComfyUIUrl();
        final String assembledPrompt = (promptSubjectField != null) ? promptSubjectField.getText().trim() : "";
        final String negativePrompt  = (promptNegativeField != null) ? promptNegativeField.getText().trim() : "";
        final int    widthVal        = (promptWidthSpinner  != null) ? (int) promptWidthSpinner.getValue()                 : 512;
        final int    heightVal       = (promptHeightSpinner != null) ? (int) promptHeightSpinner.getValue()                : 512;
        final int    batchSizeVal    = (promptBatchSizeSpinner != null) ? (int) promptBatchSizeSpinner.getValue()          : 1;
        final int    stepsVal        = (promptStepsSpinner  != null) ? (int) promptStepsSpinner.getValue()                 : 20;
        final double cfgVal          = (promptCfgSpinner    != null) ? ((Number) promptCfgSpinner.getValue()).doubleValue() : 7.0;
        final double denoiseVal      = (promptDenoiseSpinner != null) ? ((Number) promptDenoiseSpinner.getValue()).doubleValue() : 1.0;
        final String samplerNameVal  = (promptSamplerCombo != null && promptSamplerCombo.getSelectedItem() != null) ? (String) promptSamplerCombo.getSelectedItem() : "Auto";
        final String schedulerVal    = (promptSchedulerCombo != null && promptSchedulerCombo.getSelectedItem() != null) ? (String) promptSchedulerCombo.getSelectedItem() : "Auto";

        if (promptImageInputPanel != null && promptImageInputPanel.isVisible()) {
            if (selectedInputImage == null || !selectedInputImage.exists()) {
                JOptionPane.showMessageDialog(this, 
                    "This workflow requires an input image.\nPlease select an image before generating.",
                    "Missing Input Image", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        btnSendToComfy.setEnabled(false);
        btnSendToComfy.setText("⏳ Generating...");
        promptLabConsole.append("🚀 Starting generation pipeline...\n");

        backgroundExecutor.execute(() -> {
            try {
                // ── Step 1: Ensure ComfyUI is online ──────────────────────────────
                java.net.http.HttpResponse<String> infoResponse = null;
                boolean connected = false;
                try {
                    infoResponse = comfyApiClient.fetchObjectInfoRaw(comfyUrl);
                    connected = infoResponse != null && infoResponse.statusCode() == 200;
                } catch (Exception ignored) {}

                if (!connected) {
                    // Auto-start ComfyUI with the active profile
                    String activeId = configService.getActiveProfile();
                    java.util.List<de.tki.comfymodels.domain.LaunchProfile> profiles = profileManager.loadProfiles();
                    de.tki.comfymodels.domain.LaunchProfile activeProfile = profiles.stream()
                            .filter(p -> p.id().equals(activeId)).findFirst().orElse(null);
                    if (activeProfile == null && !profiles.isEmpty()) activeProfile = profiles.get(0);

                    if (activeProfile != null) {
                        final de.tki.comfymodels.domain.LaunchProfile fp = activeProfile;
                        SwingUtilities.invokeLater(() -> promptLabConsole.append(
                                "🔌 ComfyUI offline — auto-starting with profile: " + fp.name() + "\n"));
                        startComfyUI(activeProfile, false, !configService.isHideComfyUI());

                        for (int i = 1; i <= 45 && !connected; i++) {
                            final int att = i;
                            SwingUtilities.invokeLater(() -> promptLabConsole.append(
                                    "⏳ Waiting for ComfyUI to start (attempt " + att + "/45)...\n"));
                            try {
                                Thread.sleep(1000);
                                infoResponse = comfyApiClient.fetchObjectInfoRaw(comfyUrl);
                                if (infoResponse != null && infoResponse.statusCode() == 200) {
                                    connected = true;
                                    SwingUtilities.invokeLater(() -> promptLabConsole.append("🟢 ComfyUI is online!\n"));
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }

                if (!connected) {
                    SwingUtilities.invokeLater(() -> {
                        btnSendToComfy.setEnabled(true);
                        btnSendToComfy.setText("🚀 Generate");
                        promptLabConsole.append("❌ Cannot connect to ComfyUI.\n\n");
                        JOptionPane.showMessageDialog(this, "Could not connect to ComfyUI. Please start it manually.",
                                "Connection Failed", JOptionPane.ERROR_MESSAGE);
                    });
                    return;
                }

                // ── Step 2: Update model sets (for path sanitization later) ──────
                if (infoResponse != null && infoResponse.statusCode() == 200) {
                    try { updateComfyModelSets(new JSONObject(infoResponse.body())); } catch (Exception ignored) {}
                }

                // ── Step 3: Get the GUI workflow JSON for the selected blueprint ──
                // We use currentBlueprintGuiJson if pre-loaded; otherwise fall back to promptJsonArea content
                String guiJsonStr = promptLabController != null ? promptLabController.getCurrentBlueprintGuiJson() : currentBlueprintGuiJson;
                if (guiJsonStr == null || guiJsonStr.isBlank()) {
                    // If no blueprint was pre-loaded, check if promptJsonArea already holds API JSON
                    guiJsonStr = (promptJsonArea != null) ? promptJsonArea.getText().trim() : null;
                }
                if (guiJsonStr == null || guiJsonStr.isBlank()) {
                    SwingUtilities.invokeLater(() -> {
                        btnSendToComfy.setEnabled(true);
                        btnSendToComfy.setText("🚀 Generate");
                        JOptionPane.showMessageDialog(this, "No workflow JSON available. Please select a blueprint first.",
                                "No Workflow", JOptionPane.WARNING_MESSAGE);
                    });
                    return;
                }

                // ── Step 4: Determine if workflow needs GUI→API conversion ────────
                final String guiJson = guiJsonStr;
                JSONObject parsedJson = new JSONObject(guiJson);
                boolean isGuiFormat = parsedJson.has("nodes"); // GUI format has "nodes" array
                if (!isGuiFormat && parsedJson.has("workflow")) {
                    try {
                        isGuiFormat = new JSONObject(parsedJson.get("workflow").toString()).has("nodes");
                    } catch (Exception ignored) {}
                }

                String convertedApiJson;

                if (isGuiFormat && apiWorkflowCache.containsKey(guiJson.hashCode())) {
                    convertedApiJson = apiWorkflowCache.get(guiJson.hashCode());
                    SwingUtilities.invokeLater(() -> promptLabConsole.append("⚡ Using cached API workflow (skipping browser conversion).\n"));
                } else if (isGuiFormat) {
                    // ── Step 4a: Convert GUI → API via browser or Java fallback ──
                    SwingUtilities.invokeLater(() -> promptLabConsole.append(
                            "🔄 Sending blueprint to browser for conversion (app.graphToPrompt())...\n"));

                    java.util.concurrent.CompletableFuture<String> conversionFuture = new java.util.concurrent.CompletableFuture<>();
                    restBridge.setWorkflowReadyConsumer(conversionFuture::complete);

                    java.net.http.HttpResponse<String> convResponse = null;
                    try {
                        convResponse = comfyApiClient.convertWorkflow(
                                comfyUrl, parsedJson, "http://127.0.0.1:12345/api/workflow-ready");
                    } catch (Exception ex) {
                        logger.warn("Bridge conversion request error: " + ex.getMessage());
                    }

                    if (convResponse == null || convResponse.statusCode() != 200) {
                        restBridge.setWorkflowReadyConsumer(null);
                        SwingUtilities.invokeLater(() -> promptLabConsole.append(
                                "⚙️ Bridge conversion unavailable. Converting workflow locally in Java...\n"));
                        try {
                            JSONObject flattened = de.tki.comfymodels.service.impl.ComfyPipelineService.flattenWorkflow(parsedJson);
                            JSONObject apiJson = de.tki.comfymodels.service.impl.ComfyPipelineService.convertUiToApi(flattened);
                            convertedApiJson = apiJson.toString();
                        } catch (Exception convEx) {
                            SwingUtilities.invokeLater(() -> {
                                btnSendToComfy.setEnabled(true);
                                btnSendToComfy.setText("🚀 Generate");
                                JOptionPane.showMessageDialog(this,
                                        "Workflow conversion failed: " + convEx.getMessage(),
                                        "Conversion Failed", JOptionPane.ERROR_MESSAGE);
                            });
                            return;
                        }
                    } else {
                        // Wait for browser conversion, auto-opening browser if tab isn't already active
                        String resultJson = null;
                        try {
                            resultJson = conversionFuture.get(2500, java.util.concurrent.TimeUnit.MILLISECONDS);
                        } catch (java.util.concurrent.TimeoutException te) {
                            SwingUtilities.invokeLater(() -> promptLabConsole.append(
                                    "🌐 Opening ComfyUI in your browser (http://127.0.0.1:8188) to process conversion...\n"));
                            try {
                                if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                                    java.awt.Desktop.getDesktop().browse(new java.net.URI(comfyUrl));
                                }
                            } catch (Exception ignored) {}

                            try {
                                Thread.sleep(1500);
                                comfyApiClient.convertWorkflow(comfyUrl, parsedJson, "http://127.0.0.1:12345/api/workflow-ready");
                            } catch (Exception ignored) {}

                            try {
                                resultJson = conversionFuture.get(10, java.util.concurrent.TimeUnit.SECONDS);
                            } catch (java.util.concurrent.TimeoutException te2) {
                                restBridge.setWorkflowReadyConsumer(null);
                                SwingUtilities.invokeLater(() -> promptLabConsole.append(
                                        "⚙️ Browser conversion timed out. Converting workflow locally in Java...\n"));
                                try {
                                    JSONObject flattened = de.tki.comfymodels.service.impl.ComfyPipelineService.flattenWorkflow(parsedJson);
                                    JSONObject apiJson = de.tki.comfymodels.service.impl.ComfyPipelineService.convertUiToApi(flattened);
                                    resultJson = apiJson.toString();
                                } catch (Exception convEx) {
                                    SwingUtilities.invokeLater(() -> {
                                        btnSendToComfy.setEnabled(true);
                                        btnSendToComfy.setText("🚀 Generate");
                                        JOptionPane.showMessageDialog(this,
                                                "Workflow conversion failed: " + convEx.getMessage(),
                                                "Conversion Failed", JOptionPane.ERROR_MESSAGE);
                                    });
                                    return;
                                }
                            }
                        }

                        convertedApiJson = resultJson;
                    }

                    // Check for browser-side conversion errors
                    if (convertedApiJson != null) {
                        try {
                            JSONObject receivedObj = new JSONObject(convertedApiJson);
                            if (receivedObj.has("error")) {
                                final String errMsg = receivedObj.getString("error");
                                SwingUtilities.invokeLater(() -> {
                                    btnSendToComfy.setEnabled(true);
                                    btnSendToComfy.setText("🚀 Generate");
                                    promptLabConsole.append("❌ Conversion error: " + errMsg + "\n\n");
                                    JOptionPane.showMessageDialog(this,
                                            "Workflow conversion failed:\n" + errMsg, "Conversion Error", JOptionPane.ERROR_MESSAGE);
                                });
                                return;
                            }
                        } catch (Exception ignored) {}
                    }

                    if (convertedApiJson != null && !convertedApiJson.isBlank()) {
                        apiWorkflowCache.put(guiJson.hashCode(), convertedApiJson);
                    }
                    SwingUtilities.invokeLater(() -> promptLabConsole.append("✅ Conversion successful.\n"));

                } else {
                    // Already API format — use as-is
                    convertedApiJson = guiJson;
                    SwingUtilities.invokeLater(() -> promptLabConsole.append("ℹ️ Workflow already in API format — skipping conversion.\n"));
                }

                // ── Step 5: Build the final API prompt object ────────────────────
                JSONObject mainObj = promptBlueprintApiService.extractApiPayload(convertedApiJson);
                JSONObject promptObj = mainObj.getJSONObject("prompt");

                // ── Step 5.5: Upload input image to ComfyUI input folder via API ───────────
                String inputImageFile = null;
                if (selectedInputImage != null && selectedInputImage.exists()) {
                    try {
                        inputImageFile = comfyApiClient.uploadInputImage(comfyUrl, selectedInputImage);
                        final String finalImageName = inputImageFile;
                        SwingUtilities.invokeLater(() -> promptLabConsole.append("🖼️ Uploaded input image to ComfyUI: " + finalImageName + "\n"));
                    } catch (Exception ex) {
                        logger.error("Failed to upload image to ComfyUI", ex);
                        SwingUtilities.invokeLater(() -> promptLabConsole.append("⚠️ Failed to upload input image: " + ex.getMessage() + "\n"));
                    }
                }

                // ── Step 6: Generically inject Prompt Lab values (no loader logic) ─
                long randomSeed = Math.abs(new java.util.Random().nextLong()) % 9007199254740991L;
                de.tki.comfymodels.service.PromptBlueprintApiService.PromptLabInputs labInputs =
                    new de.tki.comfymodels.service.PromptBlueprintApiService.PromptLabInputs(
                        assembledPrompt, negativePrompt, widthVal, heightVal, stepsVal, cfgVal, randomSeed,
                        samplerNameVal, schedulerVal, denoiseVal, batchSizeVal, inputImageFile);
                promptBlueprintApiService.injectLabInputs(promptObj, labInputs);

                // ── Step 7: Sanitize model file paths (slash / backslash) ────────
                JSONObject objectInfo = null;
                try {
                    // Force a refresh of ComfyUI's model list so our query returns the most up-to-date models
                    comfyApiClient.refreshModels(comfyUrl, true);
                    objectInfo = comfyApiClient.getObjectInfo(comfyUrl);
                } catch (Exception ignored) {
                    logger.warn("Failed to fetch object_info for generic model resolution", ignored);
                }
                sanitizeModelInputsInPrompt(promptObj, objectInfo);

                // ── Step 8: POST to ComfyUI /prompt ──────────────────────────────
                final String finalPayload = mainObj.toString(2);
                SwingUtilities.invokeLater(() -> {
                    promptJsonArea.setText(finalPayload);
                    promptLabConsole.append("📤 Sending prompt to ComfyUI...\n");
                });

                java.net.http.HttpResponse<String> response = comfyApiClient.postPrompt(comfyUrl, finalPayload);

                SwingUtilities.invokeLater(() -> {
                    btnSendToComfy.setEnabled(true);
                    btnSendToComfy.setText("🚀 Generate");
                    if (response.statusCode() == 200) {
                        promptLabConsole.append("✅ Successfully queued! Status: 200\n\n");
                        try {
                            JSONObject respObj = new JSONObject(response.body());
                            String promptId = respObj.optString("prompt_id");
                            if (promptId != null && !promptId.isEmpty()) {
                                startPollingPromptStatus(promptId);
                            }
                        } catch (Exception ignored) {}
                    } else if (response.statusCode() == 400) {
                        promptLabConsole.append("❌ Validation error (400):\n" + response.body() + "\n\n");
                        String errMsg = "ComfyUI validation error (400 Bad Request).\n";
                        try {
                            JSONObject errObj = new JSONObject(response.body());
                            if (errObj.has("error")) {
                                JSONObject subErr = errObj.getJSONObject("error");
                                errMsg += "\nType: "    + subErr.optString("type", "")
                                        + "\nMessage: " + subErr.optString("message", "");
                                if (subErr.has("details")) errMsg += "\nDetails: " + subErr.get("details");
                            } else {
                                errMsg += "\n" + response.body();
                            }
                        } catch (Exception ignored) { errMsg += "\n" + response.body(); }
                        JOptionPane.showMessageDialog(this, errMsg, "Validation Error (400)", JOptionPane.ERROR_MESSAGE);
                    } else {
                        promptLabConsole.append("❌ Error! Status: " + response.statusCode() + "\n" + response.body() + "\n\n");
                        JOptionPane.showMessageDialog(this,
                                "ComfyUI error: " + response.statusCode() + "\n" + response.body(),
                                "API Error", JOptionPane.ERROR_MESSAGE);
                    }
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    btnSendToComfy.setEnabled(true);
                    btnSendToComfy.setText("🚀 Generate");
                    promptLabConsole.append("❌ Error: " + ex.getMessage() + "\n\n");
                    JOptionPane.showMessageDialog(this,
                            "Generation failed:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private String findExactComfyModelOption(String classType, String inputKey, String targetValue) {
        if (targetValue == null || targetValue.isBlank()) return null;

        String targetClean = targetValue.replaceAll("[/\\\\]+", "/").toLowerCase();
        String targetFileName = targetClean.contains("/") ? targetClean.substring(targetClean.lastIndexOf('/') + 1) : targetClean;

        java.util.List<java.util.Set<String>> candidateLists = new java.util.ArrayList<>();
        String ikLower = inputKey != null ? inputKey.toLowerCase() : "";
        String ctLower = classType != null ? classType.toLowerCase() : "";

        if (ikLower.contains("vae") || ctLower.contains("vae")) {
            if (promptLabController != null) candidateLists.add(promptLabController.getComfyVaes());
        } else if (ikLower.contains("unet") || ctLower.contains("unet")) {
            if (promptLabController != null) candidateLists.add(promptLabController.getComfyUnetModels());
        } else if (ikLower.contains("clip") || ctLower.contains("clip")) {
            if (promptLabController != null) candidateLists.add(promptLabController.getComfyClips());
        } else if (ikLower.contains("ckpt") || ctLower.contains("checkpoint")) {
            if (promptLabController != null) candidateLists.add(promptLabController.getComfyCheckpoints());
        } else {
            // Fallback candidates for generic model keys
            if (promptLabController != null) {
                candidateLists.add(promptLabController.getComfyCheckpoints());
                candidateLists.add(promptLabController.getComfyUnetModels());
                candidateLists.add(promptLabController.getComfyClips());
                candidateLists.add(promptLabController.getComfyVaes());
            }
        }

        for (java.util.Set<String> options : candidateLists) {
            if (options == null || options.isEmpty()) continue;

            // 1. Try exact normalized match
            for (String opt : options) {
                String optClean = opt.replaceAll("[/\\\\]+", "/").toLowerCase();
                if (optClean.equals(targetClean)) {
                    return opt;
                }
            }

            // 2. Try filename-only match
            for (String opt : options) {
                String optClean = opt.replaceAll("[/\\\\]+", "/").toLowerCase();
                String optFileName = optClean.contains("/") ? optClean.substring(optClean.lastIndexOf('/') + 1) : optClean;
                if (optFileName.equals(targetFileName)) {
                    return opt;
                }
                    }

            // 3. Try suffix match
            for (String opt : options) {
                String optClean = opt.replaceAll("[/\\\\]+", "/").toLowerCase();
                if (optClean.endsWith("/" + targetFileName)) {
                    return opt;
                }
            }

            // 4. Try generic fuzzy match (ignore extensions, check if one contains the other)
            String targetNoExt = targetFileName.contains(".") ? targetFileName.substring(0, targetFileName.lastIndexOf('.')) : targetFileName;
            for (String opt : options) {
                String optClean = opt.replaceAll("[/\\\\]+", "/").toLowerCase();
                String optFileName = optClean.contains("/") ? optClean.substring(optClean.lastIndexOf('/') + 1) : optClean;
                String optNoExt = optFileName.contains(".") ? optFileName.substring(0, optFileName.lastIndexOf('.')) : optFileName;
                
                // Exclude very short strings from fuzzy matching to avoid false positives (like "ae")
                if (targetNoExt.length() > 3 && optNoExt.length() > 3) {
                    if (optNoExt.contains(targetNoExt) || targetNoExt.contains(optNoExt)) {
                        return opt;
                    }
                }
            }
            
            // 5. Absolute fallback
            if (!options.isEmpty()) {
                return options.iterator().next();
            }
        }

        return null;
    }

    private String findModelInObjectInfo(String classType, String inputKey, String targetValue, JSONObject objectInfo) {
        if (targetValue == null || targetValue.isBlank() || classType == null || objectInfo == null) return null;
        if (!objectInfo.has(classType)) return null;
        
        JSONObject nodeInfo = objectInfo.getJSONObject(classType);
        JSONObject input = nodeInfo.optJSONObject("input");
        if (input == null) return null;
        
        JSONObject required = input.optJSONObject("required");
        JSONObject optional = input.optJSONObject("optional");
        
        Object valObj = null;
        if (required != null && required.has(inputKey)) valObj = required.get(inputKey);
        else if (optional != null && optional.has(inputKey)) valObj = optional.get(inputKey);
        
        if (valObj instanceof org.json.JSONArray outerArray && outerArray.length() > 0) {
            Object firstElement = outerArray.get(0);
            org.json.JSONArray options = null;
            if (firstElement instanceof org.json.JSONArray) {
                options = (org.json.JSONArray) firstElement;
            } else if (firstElement instanceof String firstStr && firstStr.equalsIgnoreCase("COMBO") && outerArray.length() > 1 && outerArray.get(1) instanceof org.json.JSONObject configObj) {
                options = configObj.optJSONArray("options");
            } else if (firstElement instanceof String && !"COMBO".equalsIgnoreCase((String) firstElement)) {
                options = outerArray;
            }
            
            if (options != null) {
                java.util.Set<String> optSet = new java.util.HashSet<>();
                for (int i = 0; i < options.length(); i++) {
                    if (options.get(i) instanceof String s && !s.equalsIgnoreCase("COMBO")) optSet.add(s);
                }
                
                String targetClean = targetValue.replaceAll("[/\\\\]+", "/").toLowerCase();
                String targetFileName = targetClean.contains("/") ? targetClean.substring(targetClean.lastIndexOf('/') + 1) : targetClean;
                String targetNoExt = targetFileName.contains(".") ? targetFileName.substring(0, targetFileName.lastIndexOf('.')) : targetFileName;
                
                // 1. Exact match
                for (String opt : optSet) {
                    if (opt.replaceAll("[/\\\\]+", "/").toLowerCase().equals(targetClean)) return opt;
                }
                // 2. Filename match
                for (String opt : optSet) {
                    String optClean = opt.replaceAll("[/\\\\]+", "/").toLowerCase();
                    String optFileName = optClean.contains("/") ? optClean.substring(optClean.lastIndexOf('/') + 1) : optClean;
                    if (optFileName.equals(targetFileName)) return opt;
                }
                // 3. Suffix match
                for (String opt : optSet) {
                    String optClean = opt.replaceAll("[/\\\\]+", "/").toLowerCase();
                    if (optClean.endsWith("/" + targetFileName)) return opt;
                }
                // 4. Fuzzy match
                for (String opt : optSet) {
                    String optClean = opt.replaceAll("[/\\\\]+", "/").toLowerCase();
                    String optFileName = optClean.contains("/") ? optClean.substring(optClean.lastIndexOf('/') + 1) : optClean;
                    String optNoExt = optFileName.contains(".") ? optFileName.substring(0, optFileName.lastIndexOf('.')) : optFileName;
                    if (targetNoExt.length() > 3 && optNoExt.length() > 3) {
                        if (optNoExt.contains(targetNoExt) || targetNoExt.contains(optNoExt)) return opt;
                    }
                }
                
                // 5. Absolute fallback: if no match found, pick the first available option rather than failing ComfyUI validation.
                if (!optSet.isEmpty()) {
                    return optSet.iterator().next();
                }
            }
        }
        return null;
    }

    private void sanitizeModelInputsInPrompt(JSONObject promptObj, JSONObject objectInfo) {
        if (promptObj == null) return;
        for (String key : promptObj.keySet()) {
            JSONObject node = promptObj.optJSONObject(key);
            if (node == null) continue;
            JSONObject inp = node.optJSONObject("inputs");
            if (inp == null) continue;
            String classType = node.optString("class_type", "");

            // Handle KSamplerSelect node specifically
            if ("KSamplerSelect".equals(classType)) {
                String sn = inp.optString("sampler_name", "");
                if (sn.isBlank() || sn.equalsIgnoreCase("COMBO") || sn.equalsIgnoreCase("Auto")) {
                    String uiSampler = (promptSamplerCombo != null && promptSamplerCombo.getSelectedItem() != null) ? (String) promptSamplerCombo.getSelectedItem() : "Auto";
                    inp.put("sampler_name", (!uiSampler.equalsIgnoreCase("Auto") && !uiSampler.equalsIgnoreCase("COMBO")) ? uiSampler : "euler");
                }
            }

            for (String ik : new java.util.ArrayList<>(inp.keySet())) {
                Object val = inp.opt(ik);
                if (val instanceof String s && !s.isBlank()) {
                    // Clean duplicate slashes & backslashes
                    String cleaned = s.replaceAll("[/\\\\]+", "/").trim();
                    if (s.startsWith("/") || s.startsWith("\\")) {
                        cleaned = "/" + cleaned.replaceAll("^/+", "");
                    }

                    // Prevent placeholder COMBO or Auto from reaching ComfyUI
                    if (cleaned.equalsIgnoreCase("COMBO") || cleaned.equalsIgnoreCase("Auto")) {
                        if (ik.equals("sampler_name") || classType.contains("Sampler")) {
                            String uiSampler = (promptSamplerCombo != null && promptSamplerCombo.getSelectedItem() != null) ? (String) promptSamplerCombo.getSelectedItem() : "Auto";
                            inp.put(ik, (!uiSampler.equalsIgnoreCase("Auto") && !uiSampler.equalsIgnoreCase("COMBO")) ? uiSampler : "euler");
                            continue;
                        } else if (ik.equals("scheduler")) {
                            String uiScheduler = (promptSchedulerCombo != null && promptSchedulerCombo.getSelectedItem() != null) ? (String) promptSchedulerCombo.getSelectedItem() : "Auto";
                            inp.put(ik, (!uiScheduler.equalsIgnoreCase("Auto") && !uiScheduler.equalsIgnoreCase("COMBO")) ? uiScheduler : "simple");
                            continue;
                        }
                    }

                    boolean isModelKey = ik.endsWith("_name") || ik.endsWith("_path") || ik.equals("model") || ik.equals("vae") || ik.equals("clip") || ik.equals("unet");
                    boolean isModelFile = cleaned.endsWith(".safetensors") || cleaned.endsWith(".ckpt") || cleaned.endsWith(".pt") || cleaned.endsWith(".bin") || cleaned.endsWith(".onnx") || cleaned.endsWith(".sft");

                    if (isModelKey || isModelFile) {
                        String matchedOption = null;
                        if (objectInfo != null) {
                            matchedOption = findModelInObjectInfo(classType, ik, cleaned, objectInfo);
                        }
                        if (matchedOption == null) {
                            matchedOption = findExactComfyModelOption(classType, ik, cleaned);
                        }
                        if (matchedOption != null) {
                            inp.put(ik, matchedOption);
                        } else {
                            if (ik.equals("sampler_name") && cleaned.equalsIgnoreCase("COMBO")) {
                                inp.put(ik, "euler");
                            } else if (ik.equals("scheduler") && cleaned.equalsIgnoreCase("COMBO")) {
                                inp.put(ik, "normal");
                            } else {
                                inp.put(ik, cleaned);
                            }
                        }
                    }
                }
            }
        }
        de.tki.comfymodels.service.PromptBlueprintApiService.sanitizeAllSeedsInPrompt(promptObj);
    }


    private void downloadLocalGemmaModel() {
        if (btnSuggestSubject != null) {
            btnSuggestSubject.setEnabled(false);
            btnSuggestSubject.setText("⏳ Downloading...");
        }
        promptLabConsole.append("Starting download of Gemma-3-4B GGUF model (3 GB) from Hugging Face...\n");
        
        localAIService.getLocalGemmaService().downloadModel(
            (percent, status) -> SwingUtilities.invokeLater(() -> {
                promptLabConsole.append("Download: " + status + "\n");
            }),
            () -> SwingUtilities.invokeLater(() -> {
                if (btnSuggestSubject != null) {
                    btnSuggestSubject.setEnabled(true);
                    btnSuggestSubject.setText("✨ Suggest");
                }
                promptLabConsole.append("✅ Local Gemma model downloaded successfully!\n");
                JOptionPane.showMessageDialog(this,
                    "Local Gemma model downloaded successfully!",
                    "Download Complete", JOptionPane.INFORMATION_MESSAGE);
                updateAiModelDisplay();
            }),
            (errorMsg, ex) -> SwingUtilities.invokeLater(() -> {
                if (btnSuggestSubject != null) {
                    btnSuggestSubject.setEnabled(true);
                    btnSuggestSubject.setText("✨ Suggest");
                }
                promptLabConsole.append("❌ Download failed: " + errorMsg + "\n");
                JOptionPane.showMessageDialog(this,
                    "Failed to download Gemma model: " + errorMsg,
                    "Download Failed", JOptionPane.ERROR_MESSAGE);
            })
        );
    }

    private void suggestSubjectCompletions() {
        boolean hasGemma = localAIService != null && localAIService.isLocalGemmaDownloaded();

        if (!hasGemma) {
            int choice = JOptionPane.showConfirmDialog(this,
                "The local Gemma model is not downloaded.\n" +
                "Would you like to download the Gemma-3-4B GGUF model (approx. 3 GB) now to get suggestions?",
                "Download Local Gemma Model?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                downloadLocalGemmaModel();
            }
            return;
        }

        String currentSubject = promptSubjectField.getText().trim();
        if (currentSubject.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "Please enter a basic prompt or subject first.", 
                "Empty Prompt", JOptionPane.WARNING_MESSAGE);
            return;
        }

        btnSuggestSubject.setEnabled(false);
        btnSuggestSubject.setText("✨ Suggesting...");
        promptLabConsole.append("Generating prompt completion suggestions using local Gemma...\n");

        backgroundExecutor.execute(() -> {
            java.util.List<String> suggestions = null;
            StringBuilder errorLogs = new StringBuilder();
            try {
                suggestions = localAIService.getDirectGemmaCompletions(currentSubject);
            } catch (Throwable ex) {
                java.io.StringWriter sw = new java.io.StringWriter();
                ex.printStackTrace(new java.io.PrintWriter(sw));
                errorLogs.append("AI suggestions failed:\n").append(sw.toString()).append("\n");
            }

            final java.util.List<String> finalSuggestions = suggestions;
            final String finalErrors = errorLogs.toString();
            SwingUtilities.invokeLater(() -> {
                btnSuggestSubject.setEnabled(true);
                btnSuggestSubject.setText("✨ Suggest");
                
                if (finalSuggestions != null && !finalSuggestions.isEmpty()) {
                    promptSubjectSuggestionsPanel.removeAll();

                    for (String suggestion : finalSuggestions) {
                        JButton sugBtn = new JButton(suggestion);
                        sugBtn.setHorizontalAlignment(SwingConstants.LEFT);
                        sugBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
                        sugBtn.putClientProperty("FlatLaf.style", "arc: 6; background: $TextField.background; border: 4,8,4,8,$Card.border,1,6");
                        sugBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
                        sugBtn.setToolTipText("Click to use: " + suggestion);
                        sugBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
                        sugBtn.setPreferredSize(new Dimension(300, 30));
                        sugBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
                        sugBtn.addActionListener(e -> {
                            promptSubjectField.setText(suggestion);
                            promptSubjectSuggestionsWrapper.setVisible(false);
                            promptLabLeftPanel.revalidate();
                            promptLabLeftPanel.repaint();
                        });
                        promptSubjectSuggestionsPanel.add(sugBtn);
                        promptSubjectSuggestionsPanel.add(Box.createVerticalStrut(4));
                    }
                    promptSubjectSuggestionsWrapper.setVisible(true);
                    promptLabLeftPanel.revalidate();
                    promptLabLeftPanel.repaint();
                    promptLabConsole.append("Subject suggestions loaded successfully using local Gemma.\n");
                } else {
                    promptLabConsole.append("❌ Error: Could not generate suggestions.\n");
                    if (!finalErrors.isEmpty()) {
                        promptLabConsole.append(finalErrors + "\n");
                    }
                    JOptionPane.showMessageDialog(Main.this,
                        "Could not generate suggestions. Please ensure the local Gemma model is downloaded correctly.",
                        "Suggestions Failed", JOptionPane.WARNING_MESSAGE);
                }
            });
        });
    }

    private void startPollingPromptStatus(String promptId) {
        String comfyUrl = configService.getComfyUIUrl();
        SwingUtilities.invokeLater(() -> {
            if (promptLabRightTabbedPane != null) {
                promptLabRightTabbedPane.setSelectedIndex(0); // Auto-switch to Image Preview tab
            }
            if (promptLabProgressBar != null) {
                promptLabProgressBar.setVisible(true);
                promptLabProgressBar.setIndeterminate(true);
                promptLabProgressBar.setString("Queued...");
            }
            if (promptImagePreviewLabel != null) {
                promptImagePreviewLabel.setText("Generating image... Please wait.");
                promptImagePreviewLabel.setIcon(null);
            }
        });

        backgroundExecutor.execute(() -> {
            boolean done = false;
            int pollAttempts = 0;

            while (!done && pollAttempts < 180) { // Timeout after 3 minutes
                try {
                    Thread.sleep(1000);
                    pollAttempts++;

                    // 1. Check history
                    de.tki.comfymodels.service.impl.ComfyApiClient.ImageOutput imgOutput = comfyApiClient.fetchPromptHistoryImage(comfyUrl, promptId);
                    if (imgOutput != null) {
                        final String finalFilename = imgOutput.filename();
                        final String finalSubfolder = imgOutput.subfolder();
                        final String finalType = imgOutput.type();

                        SwingUtilities.invokeLater(() -> {
                            loadAndDisplayImage(finalFilename, finalSubfolder, finalType);
                            if (promptLabProgressBar != null) promptLabProgressBar.setVisible(false);
                            promptLabConsole.append("Image generated and loaded successfully!\n\n");
                        });
                        done = true;
                        break;
                    }

                    // 2. Check queue position
                    if (!done) {
                        de.tki.comfymodels.service.impl.ComfyApiClient.QueueInfo qInfo = comfyApiClient.fetchQueueStatus(comfyUrl, promptId);
                        if (qInfo.state() == de.tki.comfymodels.service.impl.ComfyApiClient.QueueState.RUNNING) {
                            SwingUtilities.invokeLater(() -> {
                                if (promptLabProgressBar != null) promptLabProgressBar.setString("Generating...");
                            });
                        } else if (qInfo.state() == de.tki.comfymodels.service.impl.ComfyApiClient.QueueState.PENDING) {
                            final int pos = qInfo.position();
                            SwingUtilities.invokeLater(() -> {
                                if (promptLabProgressBar != null) promptLabProgressBar.setString("Queued (Position: " + pos + ")");
                            });
                        }
                    }

                } catch (Exception e) {
                    logger.error("Error polling ComfyUI status: " + e.getMessage());
                }
            }

            if (!done) {
                SwingUtilities.invokeLater(() -> {
                    if (promptLabProgressBar != null) promptLabProgressBar.setVisible(false);
                    if (promptImagePreviewLabel != null) promptImagePreviewLabel.setText("Generation timed out or failed.");
                    promptLabConsole.append("❌ Generation timed out or failed to load image.\n\n");
                });
            }
        });
    }

    private void loadAndDisplayImage(String filename, String subfolder, String type) {
        String comfyUrl = configService.getComfyUIUrl();
        String imageUrl = comfyUrl + "/view?filename=" + filename + "&subfolder=" + subfolder + "&type=" + type;
        
        backgroundExecutor.execute(() -> {
            try {
                java.net.URL url = new java.net.URL(imageUrl);
                Image img = ImageIO.read(url);
                if (img != null) {
                    SwingUtilities.invokeLater(() -> {
                        scaleAndSetImage(img);
                    });
                }
            } catch (Exception ex) {
                logger.error("Failed to download image: " + ex.getMessage());
                SwingUtilities.invokeLater(() -> {
                    if (promptImagePreviewLabel != null) promptImagePreviewLabel.setText("Failed to load generated image.");
                });
            }
        });
    }

    private void scaleAndSetImage(Image img) {
        currentPreviewImage = img;
        if (promptLabView != null) {
            promptLabView.scaleAndSetImage(img);
        }
    }

    private void savePromptLabSession() {
        if (promptLabController != null) promptLabController.savePromptLabSession();
    }

    private void loadPromptLabSession() {
        if (promptLabController != null) promptLabController.loadPromptLabSession();
    }

    private JPanel createDashboardPanel(JTabbedPane tabs) {
        de.tki.comfymodels.ui.GlassPanel panel = new de.tki.comfymodels.ui.GlassPanel();
        panel.setLayout(new BorderLayout(20, 20));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // LEFT: Profile List (Card-like)
        de.tki.comfymodels.ui.GlassPanel leftPanel = new de.tki.comfymodels.ui.GlassPanel();
        leftPanel.setLayout(new BorderLayout(10, 10));
        leftPanel.setPreferredSize(new Dimension(320, 0));
        // FlatLaf overrides the background, so we remove the FlatLaf style property to let the GlassPanel shine
        leftPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel profilesHeader = new JLabel("Startprofile");
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
            Object[] message = { "Profile Name:", nameField, "Arguments (comma-separated):", argsField };
            int option = JOptionPane.showConfirmDialog(this, message, "Add Launch Profile", JOptionPane.OK_CANCEL_OPTION);
            if (option == JOptionPane.OK_OPTION && !nameField.getText().trim().isEmpty()) {
                List<String> args = java.util.Arrays.stream(argsField.getText().split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
                de.tki.comfymodels.domain.LaunchProfile newProfile = new de.tki.comfymodels.domain.LaunchProfile(
                    java.util.UUID.randomUUID().toString(), nameField.getText().trim(), "User-defined profile.",
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

        JLabel statusDisplay = new JLabel("Status: Offline");
        statusDisplay.setFont(new Font("SansSerif", Font.BOLD, 18));
        statusDisplay.setForeground(Color.GRAY);
        gbc.gridy = 1;
        rightPanel.add(statusDisplay, gbc);

        JLabel descLabel = new JLabel("Select a profile to view details.");
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
                descLabel.setText("Select a profile to view details.");
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
                JOptionPane.showMessageDialog(this, "Browser could not be opened: " + ex.getMessage());
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
        
        lblComfyVersion = new JLabel("ComfyUI: Loading...");
        lblPythonVersion = new JLabel("Python: Loading...");

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
                boolean starting = false;
                if (processController.isProcessAlive() && !processController.isGuiLineShown()) {
                    starting = true;
                } else if (lifecycleService.isProcessAlive() && !lifecycleService.isGuiLineShown()) {
                    starting = true;
                }

                if (starting) {
                    statusDisplay.setText("Status: Starting");
                    statusDisplay.setForeground(new Color(255, 204, 0));
                    launchBtn.setEnabled(false);
                    restartBtn.setEnabled(true);
                    stopBtn.setEnabled(true);
                    browserBtn.setEnabled(false);
                } else {
                    statusDisplay.setText("Status: Running");
                    statusDisplay.setForeground(new Color(0, 180, 0));
                    launchBtn.setEnabled(false);
                    restartBtn.setEnabled(true);
                    stopBtn.setEnabled(true);
                    browserBtn.setEnabled(true);
                }
            } else {
                statusDisplay.setText("Status: Offline");
                statusDisplay.setForeground(Color.GRAY);
                launchBtn.setEnabled(true);
                restartBtn.setEnabled(false);
                stopBtn.setEnabled(false);
                browserBtn.setEnabled(false);
            }
            long now = System.currentTimeMillis();
            if (now - lastSetupCheckTime > 10000) {
                lastSetupCheckTime = now;
                cachedNeedsSetup = configService.getComfyUIPath().isEmpty() || !new File(configService.getComfyUIPath(), "main.py").exists();
            }
            bootstrapBtn.putClientProperty("FlatLaf.style", cachedNeedsSetup ? "background: #646400; foreground: #fff" : "");
        });
        statusTimer.start();

        restartBtn.addActionListener(e -> {
            de.tki.comfymodels.domain.LaunchProfile selected = profileList.getSelectedValue();
            if (selected == null) { JOptionPane.showMessageDialog(this, "Please select a profile first."); return; }
            restartBtn.setEnabled(false);
            consoleOutput.append("\n🔄 Restarting ComfyUI...\n");
            backgroundExecutor.execute(() -> {
                processController.stop();
                try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                SwingUtilities.invokeLater(() -> {
                    startComfyUI(selected, false, true);
                });
            });
        });

        bootstrapBtn.addActionListener(e -> {
            de.tki.comfymodels.ui.EnvironmentInstallerDialog dialog = new de.tki.comfymodels.ui.EnvironmentInstallerDialog(
                this, 
                bootstrapper, 
                configService, 
                profileManager, 
                () -> {
                    syncBridgeFiles();
                    refreshVersions();
                }
            );
            dialog.setVisible(true);
        });

        launchBtn.addActionListener(e -> {
            de.tki.comfymodels.domain.LaunchProfile selected = profileList.getSelectedValue();
            if (selected == null) { JOptionPane.showMessageDialog(this, "Please select a launch profile."); return; }
            startComfyUI(selected, true, true);
        });

        stopBtn.addActionListener(e -> { consoleOutput.append("\n⏹ Stopping ComfyUI process...\n"); processController.stop(); });

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
        backgroundExecutor.execute(() -> {
            lifecycleService.start();
            // Wait for health
            for (int i = 0; i < 30; i++) {
                if (lifecycleService.isHealthy()) {
                    return;
                }
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
        });
    }

    /**
     * Scans for ComfyUI installation in the designated folder (~/.comfyui-companion/ComfyUI or configured path).
     * If not found, prompts the user with a dialog asking if they want to start the setup.
     * If yes, launches the EnvironmentInstallerDialog.
     *
     * @param showDialogIfFound whether to display an informational message if ComfyUI is verified
     * @return true if ComfyUI is installed; false otherwise
     */
    public boolean scanAndVerifyComfyUIInstallation(boolean showDialogIfFound) {
        if (configService != null) {
            configService.autoDiscoverPaths();
        }

        Path designatedPath = Paths.get(System.getProperty("user.home"), ".comfyui-companion", "ComfyUI");
        String currentComfyPath = configService != null ? configService.getComfyUIPath() : "";
        boolean installed = false;
        File verifiedDir = null;

        if (currentComfyPath != null && !currentComfyPath.trim().isEmpty()) {
            File comfyDir = new File(currentComfyPath);
            if (comfyDir.exists() && comfyDir.isDirectory() && new File(comfyDir, "main.py").exists()) {
                installed = true;
                verifiedDir = comfyDir;
            }
        }

        if (!installed && Files.exists(designatedPath)) {
            File designatedDir = designatedPath.toFile();
            if (designatedDir.exists() && designatedDir.isDirectory() && new File(designatedDir, "main.py").exists()) {
                installed = true;
                verifiedDir = designatedDir;
                if (configService != null) {
                    configService.setComfyUIPath(designatedDir.getAbsolutePath());
                }
            }
        }

        if (installed) {
            logger.info("✅ [Scan] ComfyUI installation verified at: " + (verifiedDir != null ? verifiedDir.getAbsolutePath() : currentComfyPath));
            if (showDialogIfFound) {
                JOptionPane.showMessageDialog(
                    this,
                    "✅ ComfyUI is installed and verified in designated folder:\n" + (verifiedDir != null ? verifiedDir.getAbsolutePath() : currentComfyPath),
                    "ComfyUI Verified",
                    JOptionPane.INFORMATION_MESSAGE
                );
            }
            return true;
        } else {
            logger.warn("⚠️ [Scan] ComfyUI is not installed in designated folder: " + designatedPath);
            int choice = JOptionPane.showConfirmDialog(
                this,
                "ComfyUI was not found in the designated folder (" + designatedPath + ").\n\nWould you like to start the ComfyUI installation setup now?",
                "ComfyUI Installation Required",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
            );

            if (choice == JOptionPane.YES_OPTION) {
                de.tki.comfymodels.ui.EnvironmentInstallerDialog dialog = new de.tki.comfymodels.ui.EnvironmentInstallerDialog(
                    this,
                    bootstrapper,
                    configService,
                    profileManager,
                    () -> {
                        syncBridgeFiles();
                        refreshVersions();
                    }
                );
                dialog.setVisible(true);
            }
            return false;
        }
    }

    private void showSettingsMenu(JButton parent) {
        JPopupMenu menu = new JPopupMenu();
        
        JMenuItem pathsItem = new JMenuItem("📁 Directories...");
        pathsItem.addActionListener(e -> showPathsDialog());
        
        JMenuItem scanItem = new JMenuItem("🔍 Scan ComfyUI Installation...");
        scanItem.addActionListener(e -> scanAndVerifyComfyUIInstallation(true));

        JMenuItem apiItem = new JMenuItem("🔑 AI & API Keys...");
        apiItem.addActionListener(e -> showApiKeysDialog());
        
        JMenuItem bridgeItem = new JMenuItem("🚀 ComfyUI Bridge...");
        bridgeItem.addActionListener(e -> showInstallationDialog());
        
        JMenuItem helpItem = new JMenuItem("ℹ Help...");
        helpItem.addActionListener(e -> showHelpDialog());

        JMenuItem exitItem = new JMenuItem("❌ Exit Application");
        exitItem.addActionListener(e -> performAppExit());

        menu.add(pathsItem);
        menu.add(scanItem);
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

        panel.add(new JLabel("Extra ComfyUI Path (contains models, input, output):"), gbc);
        
        gbc.gridy++;
        JPanel row1 = new JPanel(new BorderLayout(5, 0));
        JTextField field1 = new JTextField(configService.getExtraComfyUIPath());
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
        save.putClientProperty("JButton.buttonType", "accent");
        save.addActionListener(e -> {
            String oldModelsPath = configService.getExtraComfyUIPath();
            String oldComfyUIPath = configService.getComfyUIPath();
            
            String newModelsPath = field1.getText().trim();
            String newComfyUIPath = field3.getText().trim();
            
            configService.setModelsPath(newModelsPath);
            configService.setArchivePath(field2.getText().trim());
            configService.setComfyUIPath(newComfyUIPath);
            configService.setPythonPath(field4.getText().trim());
            configService.setUseSymlinksOnRestore(symlinkCheck.isSelected());
            configService.setComfyLaunchCommand(""); 
            configService.autoDiscoverPaths();
            syncBridgeFiles();
            statusLabel.setText("Settings updated.");
            analyzeJsonContent();
            dialog.dispose();
            
            boolean pathsChanged = !newModelsPath.equalsIgnoreCase(oldModelsPath) || !newComfyUIPath.equalsIgnoreCase(oldComfyUIPath);
            if (pathsChanged && lifecycleService != null && lifecycleService.isHealthy()) {
                backgroundExecutor.execute(() -> {
                    logger.info("🔄 [Lifecycle] Custom paths updated. Restarting ComfyUI server to apply changes...");
                    lifecycleService.stop();
                    lifecycleService.start();
                });
            }
        });
        buttons.add(cancel);
        buttons.add(save);

        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void showDownloadSettingsDialog() {
        de.tki.comfymodels.ui.StandardDialog dialog = new de.tki.comfymodels.ui.StandardDialog(this, "Download Settings");
        dialog.setSize(600, 480);
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
        gbc.insets = new Insets(0, 0, 5, 0);
        panel.add(new JLabel("Hugging Face Access Token (hf_... for gated models like LTX-2.5, Flux):"), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        JTextField hfTokenField = new JTextField(configService.getHfToken());
        panel.add(hfTokenField, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 15, 0);
        JLabel hfHint = new JLabel("<html><i>Requires accepting terms on huggingface.co/Lightricks/LTX-2.5 prior to download.</i></html>");
        hfHint.setFont(new Font("SansSerif", Font.PLAIN, 11));
        hfHint.setForeground(Color.GRAY);
        panel.add(hfHint, gbc);

        gbc.gridy++;
        gbc.weighty = 1.0;
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        panel.add(spacer, gbc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dialog.dispose());
        JButton save = new JButton("Save");
        save.putClientProperty("JButton.buttonType", "accent");
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
                configService.setHfToken(hfTokenField.getText().trim());

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

    private void showVideoArchitectAutoconfigDialog() {
        JDialog dialog = new JDialog(this, "Video Architect Autoconfig", true);
        dialog.setSize(500, 350);
        dialog.setLocationRelativeTo(this);
        
        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        JLabel header = new JLabel("Installing Video Architect Dependencies (FFmpeg, etc.)");
        header.setFont(new Font("SansSerif", Font.BOLD, 14));
        content.add(header, BorderLayout.NORTH);
        
        JTextArea progressArea = new JTextArea("Initializing...\n");
        progressArea.setEditable(false);
        JScrollPane scroll = new JScrollPane(progressArea);
        content.add(scroll, BorderLayout.CENTER);
        
        dialog.add(content);
        
        backgroundExecutor.execute(() -> {
            try {
                dependencyService.installFfmpeg(msg -> {
                    SwingUtilities.invokeLater(() -> {
                        progressArea.append(msg + "\n");
                        progressArea.setCaretPosition(progressArea.getDocument().getLength());
                    });
                });
                
                SwingUtilities.invokeLater(() -> {
                    progressArea.append("Dependencies successfully configured!\n");
                    JOptionPane.showMessageDialog(dialog, "Dependency installation completed successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    progressArea.append("Error: " + ex.getMessage() + "\n");
                    JOptionPane.showMessageDialog(dialog, "Failed to install dependencies: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
        
        dialog.setVisible(true);
    }

    private void showApiKeysDialog() {
        de.tki.comfymodels.ui.StandardDialog dialog = new de.tki.comfymodels.ui.StandardDialog(this, "Audio & TTS Settings");
        dialog.setSize(600, 520);
        dialog.setLocationRelativeTo(this);

        JPanel panel = dialog.createContentPanel();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0;

        panel.add(new JLabel("TTS Provider:"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(5, 0, 15, 0);
        JComboBox<String> ttsProviderCombo = new JComboBox<>(new String[]{"ComfyUI Qwen-TTS", "ComfyUI KokoroTTS", "ComfyUI ElevenLabs"});
        ttsProviderCombo.setSelectedItem(configService.getTtsProvider());
        panel.add(ttsProviderCombo, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(new JLabel("Qwen-TTS Voice (if using ComfyUI Qwen-TTS):"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(5, 0, 10, 0);
        String[] qwenVoices = new String[]{"Chelsie", "Ethan", "Serena", "Aiden", "Vivian"};
        JComboBox<String> qwenVoiceCombo = new JComboBox<>(qwenVoices);
        qwenVoiceCombo.setEditable(true);
        qwenVoiceCombo.setSelectedItem(configService.getQwenTtsVoice());
        panel.add(qwenVoiceCombo, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(new JLabel("Qwen-TTS Model Repo (Hugging Face id):"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(5, 0, 15, 0);
        JTextField qwenModelRepoField = new JTextField(configService.getQwenTtsModelRepo());
        panel.add(qwenModelRepoField, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 0, 0);
        JCheckBox qwenAutoSelectCheck = new JCheckBox("Auto-select best Qwen-TTS model for my hardware", configService.isQwenTtsModelAuto());
        panel.add(qwenAutoSelectCheck, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(5, 0, 15, 0);
        JPanel qwenAutoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        qwenAutoRow.setOpaque(false);
        JButton detectButton = new JButton("Detect VRAM & recommend model");
        JLabel qwenAutoStatus = new JLabel(" ");
        qwenAutoStatus.setFont(qwenAutoStatus.getFont().deriveFont(Font.PLAIN, 11f));
        qwenAutoRow.add(detectButton);
        qwenAutoRow.add(Box.createHorizontalStrut(10));
        qwenAutoRow.add(qwenAutoStatus);
        panel.add(qwenAutoRow, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(new JLabel("Piper TTS Binary Path (if using Local Piper):"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(5, 0, 10, 0);
        JTextField piperPathField = new JTextField(configService.getPiperPath());
        panel.add(piperPathField, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(new JLabel("Piper TTS ONNX Model Path (if using Local Piper):"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(5, 0, 15, 0);
        JTextField piperModelField = new JTextField(configService.getPiperModelPath());
        panel.add(piperModelField, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(new JLabel("ElevenLabs Voice ID (if using ComfyUI ElevenLabs):"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(5, 0, 15, 0);
        JTextField elevenLabsVoiceField = new JTextField(configService.getElevenLabsVoiceId());
        panel.add(elevenLabsVoiceField, gbc);

        // Dynamic UI enabling/disabling based on selected provider
        Runnable updateTtsVisibility = () -> {
            boolean isEleven = "ComfyUI ElevenLabs".equals(ttsProviderCombo.getSelectedItem());
            elevenLabsVoiceField.setEnabled(isEleven);
            
            boolean isPiper = false; // Piper support removed; fields stay disabled
            piperPathField.setEnabled(isPiper);
            piperModelField.setEnabled(isPiper);
            boolean isQwen = "ComfyUI Qwen-TTS".equals(ttsProviderCombo.getSelectedItem());
            qwenVoiceCombo.setEnabled(isQwen);
            qwenModelRepoField.setEnabled(isQwen);
        };
        ttsProviderCombo.addActionListener(e -> updateTtsVisibility.run());
        updateTtsVisibility.run();

        gbc.gridy++;
        gbc.weighty = 1.0;
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        panel.add(spacer, gbc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dialog.dispose());
        JButton save = new JButton("Save");
        save.putClientProperty("JButton.buttonType", "accent");
        save.addActionListener(e -> {
            configService.setTtsProvider((String) ttsProviderCombo.getSelectedItem());
            configService.setPiperPath(piperPathField.getText().trim());
            configService.setPiperModelPath(piperModelField.getText().trim());
            configService.setQwenTtsVoice((String) qwenVoiceCombo.getSelectedItem());
            configService.setQwenTtsModelRepo(qwenModelRepoField.getText().trim());
            configService.setQwenTtsModelAuto(qwenAutoSelectCheck.isSelected());
            configService.setElevenLabsVoiceId(elevenLabsVoiceField.getText().trim());
            statusLabel.setText("Settings updated.");
            updateAiModelDisplay();
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
        if (downloadManagerController != null) {
            downloadManagerController.startDownloadQueue();
        }
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
            "This installer will set up the Companion for ComfyUI bridge.\n\n" +
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
        installBtn.putClientProperty("JButton.buttonType", "accent");
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
                    logger.info("Cleaned up legacy script: " + conflictFile);
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
                logger.info("Removing existing bridge directory: " + oldDir);
                deleteDirectory(oldDir.toFile());
            }
        } catch (IOException e) {
            logger.error("Error during bridge cleanup: " + e.getMessage());
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
            logger.info("[Bridge-Sync] Successfully wrote config to: " + configFile.toAbsolutePath());
        } catch (Exception e) { 
            logger.error("[Bridge-Sync] Failed to write config: " + e.getMessage());
            e.printStackTrace(); 
        }
    }

    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) deleteDirectory(f);
        dir.delete();
    }

    private void loadFile(File file) {
        if (downloadManagerController != null) {
            downloadManagerController.loadFile(file);
        }
    }

    public void importWorkflow(File file) {
        if (downloadManagerController != null) {
            downloadManagerController.importWorkflow(file, mainTabs, downloadManagerPanel);
        }
    }

    private void analyzeJsonContent() {
        if (downloadManagerController != null) {
            downloadManagerController.analyzeJsonContent();
        }
    }

    private void searchMissingOnline() {
        searchMissingOnline(false);
    }

    private void searchMissingOnline(boolean manual) {
        if (downloadManagerController != null) {
            downloadManagerController.searchMissingOnline(manual);
        }
    }

    private void verifyLocalModels(boolean checkDuplicates) {
        if (downloadManagerController != null) {
            downloadManagerController.verifyLocalModels(checkDuplicates);
        }
    }

    private void showArchiveDialog() {
        if (downloadManagerController != null) {
            downloadManagerController.showArchiveDialog();
        }
    }

    private static class ArchiveWorkItem {
        int index;
        String folder, name, size;
        ArchiveWorkItem(int i, String f, String n, String s) { index = i; folder = f; name = n; size = s; }
    }

    private JPanel createManagerPanel(JTabbedPane tabs) {
        if (downloadManagerView != null) {
            // Give the controller a reference to the view for UI access,
            // but DON'T call setView() – that would also set the controller
            // as the listener, which the anonymous listener below needs to be.
            if (downloadManagerController != null) {
                downloadManagerController.setViewReference(downloadManagerView);
            }
            downloadManagerView.setListener(new de.tki.comfymodels.ui.DownloadManagerView.DownloadManagerListener() {
                @Override
                public void onVerifyLocalModels(boolean deepCheck) { verifyLocalModels(deepCheck); }
                @Override
                public void onShowArchiveDialog() { showArchiveDialog(); }
                @Override
                public void onRunDiagnostics() {
                    if (modelsToDownload == null || modelsToDownload.isEmpty()) {
                        JOptionPane.showMessageDialog(Main.this, "Please load a workflow first to perform diagnostics.");
                        return;
                    }
                    List<String> missing = diagnosticService.getMissingModels(modelsToDownload);
                    if (missing.isEmpty()) {
                        JOptionPane.showMessageDialog(Main.this, "✅ All workflow models are visible to ComfyUI!", "Diagnostics Successful", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        String list = String.join("\n- ", missing);
                        Object[] options = {"Switch to Overview (Restart)", "Ignore"};
                        int choice = JOptionPane.showOptionDialog(Main.this, 
                            "❌ ComfyUI still reports these models as missing:\n- " + list + "\n\n" +
                            "A restart is required for ComfyUI to recognize new models.", 
                            "Diagnostics Failed", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
                        if (choice == 0) tabs.setSelectedIndex(0);
                    }
                }
                @Override
                public void onLoadWorkflowFile(File file) { loadFile(file); }
                @Override
                public void onImportModelListFile(File file) {
                    try {
                        modelListService.importJson(file);
                        JOptionPane.showMessageDialog(Main.this, "Model list successfully imported! (" + modelListService.getModels().size() + " models)");
                        analyzeJsonContent();
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(Main.this, "Error: " + ex.getMessage());
                    }
                }
                @Override
                public void onAnalyzeJsonContent() { analyzeJsonContent(); searchMissingOnline(true); }
                @Override
                public void onShowCivitaiSearchDialog(int modelRowIndex) { showCivitaiSearchDialog(modelRowIndex); }
                @Override
                public void onUpdateDownloadManagerSelection() { updateDownloadManagerSelection(); }
                @Override
                public void onUpdateDownloadButtonsState() { updateDownloadButtonsState(); }
                @Override
                public void onStartDownloadQueue() { startDownloadQueue(); }
                @Override
                public void onTogglePause() {
                    downloadManager.togglePause();
                    if (pauseButton != null) pauseButton.setText(downloadManager.isPaused() ? "Resume" : "Pause");
                    updateDownloadButtonsState();
                }
                @Override
                public void onStopDownloadQueue() { downloadManager.stop(); }
            });

            syncDownloadManagerViewFields();
            return downloadManagerView;
        }
        return new JPanel();
    }

    private void syncDownloadManagerViewFields() {
        if (downloadManagerView == null) return;
        this.jsonInputArea = downloadManagerView.getJsonInputArea();
        this.tableModel = downloadManagerView.getTableModel();
        this.modelTable = downloadManagerView.getModelTable();
        this.workflowGraphPanel = downloadManagerView.getWorkflowGraphPanel();
        this.statusLabel = downloadManagerView.getStatusLabel();
        this.activeAiModelLabel = downloadManagerView.getActiveAiModelLabel();
        this.downloadButton = downloadManagerView.getDownloadButton();
        this.pauseButton = downloadManagerView.getPauseButton();
        this.stopButton = downloadManagerView.getStopButton();
    }

    private JPanel createSettingsPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false); // Let APP_BG_COLOR show
        panel.setLayout(new BorderLayout(15, 15));
        panel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));

        Font btnFont = new Font("SansSerif", Font.BOLD, 14);
        Font checkFont = new Font("SansSerif", Font.PLAIN, 14);

        JPanel grid = new JPanel(new GridLayout(1, 2, 25, 0));
        grid.setOpaque(false);
        
        // Left Column: General & Paths
        de.tki.comfymodels.ui.CardPanel left = new de.tki.comfymodels.ui.CardPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        JLabel pathsHeader = new JLabel("General & Paths");
        pathsHeader.putClientProperty("FlatLaf.styleClass", "h3");
        pathsHeader.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

        JButton pathsBtn = new JButton("📁 Configure Directories...");
        pathsBtn.setFont(btnFont);
        pathsBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        pathsBtn.setMaximumSize(new Dimension(360, 40));
        pathsBtn.addActionListener(e -> showPathsDialog());

        JButton scanComfyBtn = new JButton("🔍 Scan ComfyUI Installation...");
        scanComfyBtn.setFont(btnFont);
        scanComfyBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        scanComfyBtn.setMaximumSize(new Dimension(360, 40));
        scanComfyBtn.addActionListener(e -> scanAndVerifyComfyUIInstallation(true));

        JButton repairBtn = new JButton("🛠️ Repair Environment Automatically...");
        repairBtn.setFont(btnFont);
        repairBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        repairBtn.setMaximumSize(new Dimension(360, 40));
        repairBtn.addActionListener(e -> triggerEnvironmentRepair());

        JButton fixWslBtn = new JButton("🐧 Fix WSL [wsl-pip] Dependencies...");
        fixWslBtn.setFont(btnFont);
        fixWslBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        fixWslBtn.setMaximumSize(new Dimension(360, 40));
        fixWslBtn.addActionListener(e -> triggerWslDependencyFix());

        JButton downloadSettingsBtn = new JButton("📥 Download Settings...");
        downloadSettingsBtn.setFont(btnFont);
        downloadSettingsBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        downloadSettingsBtn.setMaximumSize(new Dimension(360, 40));
        downloadSettingsBtn.addActionListener(e -> showDownloadSettingsDialog());

        JButton bridgeBtn = new JButton("🚀 Install ComfyUI Bridge...");
        bridgeBtn.setFont(btnFont);
        bridgeBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        bridgeBtn.setMaximumSize(new Dimension(360, 40));
        bridgeBtn.addActionListener(e -> showInstallationDialog());

        JButton videoArchitectAutoconfigBtn = new JButton("🎬 Autoconfig Video Architect...");
        videoArchitectAutoconfigBtn.setFont(btnFont);
        videoArchitectAutoconfigBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        videoArchitectAutoconfigBtn.setMaximumSize(new Dimension(360, 40));
        videoArchitectAutoconfigBtn.addActionListener(e -> showVideoArchitectAutoconfigDialog());

        JPanel checksPanel = new JPanel();
        checksPanel.setOpaque(false);
        checksPanel.setLayout(new BoxLayout(checksPanel, BoxLayout.Y_AXIS));
        checksPanel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

        backgroundCheck = new JCheckBox("Run in background");
        backgroundCheck.setFont(checkFont);
        backgroundCheck.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        backgroundCheck.addActionListener(e -> configService.setBackgroundModeEnabled(backgroundCheck.isSelected()));
        
        shutdownCheck = new JCheckBox("Shutdown after completion");
        shutdownCheck.setFont(checkFont);
        shutdownCheck.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        shutdownCheck.addActionListener(e -> configService.setShutdownAfterDownloadEnabled(shutdownCheck.isSelected()));
        
        restartCheck = new JCheckBox("Restart after completion");
        restartCheck.setFont(checkFont);
        restartCheck.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        restartCheck.addActionListener(e -> configService.setRestartAfterDownloadEnabled(restartCheck.isSelected()));
        
        darkCheck = new JCheckBox("Dark Mode");
        darkCheck.setFont(checkFont);
        darkCheck.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        darkCheck.addActionListener(e -> {
            boolean isDark = darkCheck.isSelected();
            configService.setDarkMode(isDark);
            
            // 1. ThemeManager benachrichtigen für unsere Custom GlassPanels
            if (de.tki.comfymodels.ui.ThemeManager.isDarkMode() != isDark) {
                de.tki.comfymodels.ui.ThemeManager.toggleTheme();
            }

            // 2. FlatLaf Snapshot-Animation für den Rest der Applikation
            FlatAnimatedLafChange.showSnapshot();
            setupTheme(isDark);
            revalidate();
            repaint();
            FlatAnimatedLafChange.hideSnapshotWithAnimation();
        });

        fastHashCheck = new JCheckBox("Fast hashing (AutoV1)");
        fastHashCheck.setFont(checkFont);
        fastHashCheck.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        fastHashCheck.addActionListener(e -> configService.setFastHashEnabled(fastHashCheck.isSelected()));

        hideComfyuiCheck = new JCheckBox("Hide ComfyUI Web Client (Replacement Mode)");
        hideComfyuiCheck.setFont(checkFont);
        hideComfyuiCheck.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        hideComfyuiCheck.setSelected(configService.isHideComfyUI());
        hideComfyuiCheck.addActionListener(e -> configService.setHideComfyUI(hideComfyuiCheck.isSelected()));

        promptLabCheck = new JCheckBox("Enable Prompt Lab (Experimental)");
        promptLabCheck.setFont(checkFont);
        promptLabCheck.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        promptLabCheck.addActionListener(e -> {
            configService.setPromptLabEnabled(promptLabCheck.isSelected());
            updateTabVisibility();
        });

        videoArchitectCheck = new JCheckBox("Enable Video Architect (Beta)");
        videoArchitectCheck.setFont(checkFont);
        videoArchitectCheck.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        videoArchitectCheck.addActionListener(e -> {
            configService.setVideoArchitectEnabled(videoArchitectCheck.isSelected());
            updateTabVisibility();
        });

        blueprintGalleryCheck = new JCheckBox("Enable Blueprint Gallery (Experimental)");
        blueprintGalleryCheck.setFont(checkFont);
        blueprintGalleryCheck.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        blueprintGalleryCheck.addActionListener(e -> {
            configService.setBlueprintGalleryEnabled(blueprintGalleryCheck.isSelected());
            updateTabVisibility();
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

        // Right Column: AI & Help
        de.tki.comfymodels.ui.CardPanel right = new de.tki.comfymodels.ui.CardPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));

        JLabel aiHeader = new JLabel("AI & Support");
        aiHeader.putClientProperty("FlatLaf.styleClass", "h3");
        aiHeader.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

        JButton apiBtn = new JButton("🎙️ Audio & TTS Settings...");
        apiBtn.setFont(btnFont);
        apiBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        apiBtn.setMaximumSize(new Dimension(360, 40));
        apiBtn.addActionListener(e -> showApiKeysDialog());

        JButton helpBtn = new JButton("ℹ Show Help & Instructions");
        helpBtn.setFont(btnFont);
        helpBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        helpBtn.setMaximumSize(new Dimension(360, 40));
        helpBtn.addActionListener(e -> showHelpDialog());

        JButton resetSettingsBtn = new JButton("🔄 Reset Application Settings...");
        resetSettingsBtn.setFont(btnFont);
        resetSettingsBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        resetSettingsBtn.setMaximumSize(new Dimension(360, 40));
        resetSettingsBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, "This will reset all application configurations and profiles. Continue?", "Reset Settings", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                configService.resetVault();
                try {
                    configService.unlock("companion_default_vault_key");
                    JOptionPane.showMessageDialog(this, "Settings reset successfully. Please restart the application.", "Success", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    logger.error("Failed to unlock vault after reset: {}", ex.getMessage());
                }
            }
        });

        JButton parseBlueprintsBtn = new JButton("🔍 Parse blueprints");
        parseBlueprintsBtn.setFont(btnFont);
        parseBlueprintsBtn.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        parseBlueprintsBtn.setMaximumSize(new Dimension(360, 40));
        parseBlueprintsBtn.addActionListener(e -> {
            if (modelArchitectureService != null) {
                modelArchitectureService.runBlueprintAnalysis();
                JOptionPane.showMessageDialog(this, 
                    "Blueprint parsing started in the background. You can track progress below on this Settings tab.", 
                    "Blueprint Parsing", 
                    JOptionPane.INFORMATION_MESSAGE);
            }
        });

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
        blueprintPanel.setVisible(modelArchitectureService != null && !modelArchitectureService.isBlueprintAnalysisCompleted());
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
        
        backgroundExecutor.execute(() -> {
            updaterService.repairEnvironment(log -> SwingUtilities.invokeLater(() -> {
                if (consoleOutput != null) {
                    consoleOutput.append(log);
                    consoleOutput.setCaretPosition(consoleOutput.getDocument().getLength());
                }
            }));
        });
    }

    private void triggerWslDependencyFix() {
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
        
        String comfyPathStr = configService.getComfyUIPath();
        java.nio.file.Path comfyDir = (comfyPathStr != null && !comfyPathStr.isEmpty()) ? java.nio.file.Paths.get(comfyPathStr) : null;
        
        backgroundExecutor.execute(() -> {
            bootstrapper.fixWslDependencies(comfyDir, log -> SwingUtilities.invokeLater(() -> {
                if (consoleOutput != null) {
                    consoleOutput.append(log.endsWith("\n") ? log : log + "\n");
                    consoleOutput.setCaretPosition(consoleOutput.getDocument().getLength());
                }
            }));
        });
    }

    private void performFullServiceRestart() {
        backgroundExecutor.execute(() -> {
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("🚀 Requesting restart via Dashboard...");
            });
            
            // Core logic: Stop via process controller
            processController.stop();
            
            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
            
            // Switch to Dashboard and start ComfyUI again using active profile
            SwingUtilities.invokeLater(() -> {
                if (getContentPane() instanceof JTabbedPane) {
                    ((JTabbedPane) getContentPane()).setSelectedIndex(0);
                }
                
                de.tki.comfymodels.domain.LaunchProfile activeProfile = null;
                if (profileList != null) {
                    activeProfile = profileList.getSelectedValue();
                }
                if (activeProfile == null) {
                    String activeId = configService.getActiveProfile();
                    List<de.tki.comfymodels.domain.LaunchProfile> profiles = profileManager.loadProfiles();
                    activeProfile = profiles.stream()
                        .filter(p -> p.id().equals(activeId))
                        .findFirst()
                        .orElse(null);
                    if (activeProfile == null && !profiles.isEmpty()) {
                        activeProfile = profiles.get(0);
                    }
                }
                
                if (activeProfile != null) {
                    if (profileList != null) {
                        profileList.setSelectedValue(activeProfile, true);
                    }
                    if (consoleOutput != null) {
                        consoleOutput.append("\n🔄 Restarting ComfyUI...\n");
                    }
                    startComfyUI(activeProfile, false, true);
                } else {
                    statusLabel.setText("⚠️ No launch profile available to start ComfyUI.");
                }
            });
        });
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
            JLabel label = (JLabel) progressCpu.getClientProperty("valueLabel");
            if (label != null) {
                label.setText(String.format("%.1f%%", stats.cpuLoad));
            } else {
                progressCpu.setString(String.format("CPU: %.1f%%", stats.cpuLoad));
            }
        }
        if (progressRam != null) {
            double usedGb = stats.ramUsed / (1024.0 * 1024.0 * 1024.0);
            double totalGb = stats.ramTotal / (1024.0 * 1024.0 * 1024.0);
            int pct = stats.ramTotal > 0 ? (int) ((stats.ramUsed * 100) / stats.ramTotal) : 0;
            progressRam.setValue(pct);
            JLabel label = (JLabel) progressRam.getClientProperty("valueLabel");
            if (label != null) {
                label.setText(String.format("%.1f GB / %.1f GB (%d%%)", usedGb, totalGb, pct));
            } else {
                progressRam.setString(String.format("RAM: %.1f GB / %.1f GB (%d%%)", usedGb, totalGb, pct));
            }
        }
        if (stats.gpuName != null && !"N/A".equals(stats.gpuName)) {
            if (gpuPanel != null) gpuPanel.setVisible(true);
            if (progressGpu != null) {
                progressGpu.setValue(stats.gpuUtilization);
                JLabel label = (JLabel) progressGpu.getClientProperty("valueLabel");
                if (label != null) {
                    label.setText(String.format("%d%%", stats.gpuUtilization));
                } else {
                    progressGpu.setString(String.format("GPU Load: %d%%", stats.gpuUtilization));
                }
            }
            if (progressVram != null) {
                double usedGb = stats.vramUsed / (1024.0 * 1024.0 * 1024.0);
                double totalGb = stats.vramTotal / (1024.0 * 1024.0 * 1024.0);
                int pct = stats.vramTotal > 0 ? (int) ((stats.vramUsed * 100) / stats.vramTotal) : 0;
                progressVram.setValue(pct);
                JLabel label = (JLabel) progressVram.getClientProperty("valueLabel");
                if (label != null) {
                    if (stats.vramTotal > 0) {
                        label.setText(String.format("%.1f GB / %.1f GB (%d%%)", usedGb, totalGb, pct));
                    } else {
                        label.setText("N/A");
                    }
                } else {
                    if (stats.vramTotal > 0) {
                        progressVram.setString(String.format("VRAM: %.1f GB / %.1f GB (%d%%)", usedGb, totalGb, pct));
                    } else {
                        progressVram.setString("VRAM: N/A");
                    }
                }
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
                
                // Switch to Download Manager Tab (index 1)
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

    private void updateDownloadManagerSelection() {
        if (downloadManagerController != null) {
            downloadManagerController.updateDownloadManagerSelection();
        }
    }

    private void updateDownloadButtonsState() {
        if (downloadManagerController != null) {
            downloadManagerController.updateDownloadButtonsState();
        }
    }

    public void focusDownloadTabAndSelectModels(List<ModelInfo> missingModels) {
        if (missingModels == null || missingModels.isEmpty()) return;
        if (modelsToDownload == null) {
            modelsToDownload = new ArrayList<>();
        }

        // 1. Focus the Download Manager tab (Index 1)
        if (mainTabs != null) {
            mainTabs.setSelectedIndex(1);
        }

        String base = configService.getModelsPath();
        String archive = configService.getArchivePath();

        // 2. Select the models in the table model, or add them if they don't exist
        for (ModelInfo req : missingModels) {
            String name = req.getName();
            String url = req.getUrl();
            if (name == null || name.isBlank()) continue;

            // Determine correct status & folder
            String type = req.getType() != null ? req.getType() : de.tki.comfymodels.domain.ModelFolder.CHECKPOINTS.getDefaultFolderName();
            String folder = req.getSave_path() != null ? req.getSave_path() : type;
            String normalizedFolder = archiveService.normalizeFolder(folder);
            String sizeStr = req.getSize() != null ? req.getSize() : "Unknown";
            String pop = req.getPopularity() != null ? req.getPopularity() : "📂 BLUEPRINT REQUIRED";
            String dlUrl = (url != null) ? url : "MISSING";
            long byteSize = req.getByteSize();

            // 1. Primary path check (Standard Models Path)
            boolean exists = false;
            Path local = null;
            if (base != null && !base.isEmpty()) {
                local = "root".equals(normalizedFolder) ? Paths.get(base, name) : Paths.get(base, normalizedFolder, name);
                exists = Files.exists(local) && Files.isRegularFile(local);
            }

            // 2. Primary archive check (Standard Archive Path)
            boolean inArchive = false;
            Path archivedPath = null;
            if (archive != null && !archive.trim().isEmpty()) {
                archivedPath = "root".equals(normalizedFolder) ? Paths.get(archive, name) : Paths.get(archive, normalizedFolder, name);
                inArchive = Files.exists(archivedPath) && Files.isRegularFile(archivedPath);
            }
            
            boolean sizeMismatch = false;

            // 3. Robust existence and archive cross-check (Safety Guard)
            if (archive != null && !archive.isEmpty()) {
                try {
                    Path absArchive = Paths.get(archive).toAbsolutePath().normalize();
                    
                    // If 'local' is actually inside the archive, it's NOT a local active model
                    if (exists && local != null && local.toAbsolutePath().normalize().startsWith(absArchive)) {
                        exists = false;
                        inArchive = true;
                    }
                    
                    // If we found it in the primary archive location, verify size if known
                    if (inArchive && byteSize > 0 && archivedPath != null) {
                        if (Files.size(archivedPath) != byteSize) {
                            inArchive = false; // Size mismatch in archive doesn't count as "found"
                        }
                    }
                } catch (Exception ignored) {}
            }

            // 4. Fallback: Recursive search in ARCHIVE if not found at primary archive location
            if (!inArchive && archive != null && !archive.isEmpty()) {
                java.util.Optional<Path> foundInArchive = localScanner.findModelWithPrefSize(Paths.get(archive), name, byteSize);
                if (foundInArchive.isPresent()) {
                    archivedPath = foundInArchive.get();
                    inArchive = true;
                }
            }

            // 5. Fallback: Recursive search in LOCAL MODELS if not found OR size mismatch at primary location
            if ((!exists || sizeMismatch) && base != null && !base.isEmpty()) {
                java.util.Optional<Path> foundLocally = localScanner.findModelWithPrefSizeAndType(Paths.get(base), name, byteSize, type);
                if (foundLocally.isPresent()) {
                    Path potentialLocal = foundLocally.get();
                    try {
                        long potSize = Files.size(potentialLocal);
                        if (byteSize <= 0 || potSize == byteSize) {
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
                            req.setSave_path(normalizedFolder);
                        }
                    } catch (Exception ignored) {}
                }
            }

            // 6. Final Status Determination
            String status;
            boolean isSelected;
            if (exists) {
                status = "✅ Already exists";
                isSelected = false;
            } else if (inArchive) {
                status = "📦 Archived";
                isSelected = true;
            } else if (sizeMismatch) {
                status = "🔄 Size Mismatch";
                isSelected = true;
            } else if (dlUrl == null || dlUrl.equals("MISSING") || dlUrl.isBlank()) {
                status = "Queued";
                isSelected = true;
            } else {
                status = "✅ Known Good";
                isSelected = true;
            }

            // Search for existing row
            int foundRow = -1;
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                String rowName = (String) tableModel.getValueAt(i, 2);
                if (name.equalsIgnoreCase(rowName)) {
                    foundRow = i;
                    break;
                }
            }

            if (foundRow != -1) {
                // Update existing row
                tableModel.setValueAt(isSelected, foundRow, 0);
                if (url != null && !url.equals("MISSING")) {
                    tableModel.setValueAt(url, foundRow, 6);
                }
                tableModel.setValueAt("models/" + normalizedFolder, foundRow, 5);
                tableModel.setValueAt(status, foundRow, 7);

                if (foundRow < modelsToDownload.size()) {
                    ModelInfo existingInfo = modelsToDownload.get(foundRow);
                    existingInfo.setSave_path(normalizedFolder);
                    if (url != null && !url.equals("MISSING")) {
                        existingInfo.setUrl(url);
                    }
                }
            } else {
                // Add new row
                tableModel.addRow(new Object[]{
                    isSelected,                    // Selected
                    req.getType(),                 // Type
                    name,                          // Name
                    sizeStr,                       // Size
                    pop,                           // Popularity
                    "models/" + normalizedFolder,  // Path
                    dlUrl,                         // URL
                    status                         // Status
                });

                // Add corresponding ModelInfo entry to modelsToDownload to keep indices aligned
                ModelInfo newInfo = new ModelInfo();
                newInfo.setName(name);
                newInfo.setType(req.getType());
                newInfo.setSize(sizeStr);
                newInfo.setUrl(dlUrl);
                newInfo.setPopularity(pop);
                newInfo.setSave_path(normalizedFolder);
                newInfo.setByteSize(byteSize);
                modelsToDownload.add(newInfo);
            }
        }

        // Trigger updating the selection arrays in download manager
        updateDownloadManagerSelection();
        updateDownloadButtonsState();

        // Notify user
        JOptionPane.showMessageDialog(this,
            "Added " + missingModels.size() + " missing model(s) to the Download Manager queue.\n" +
            "Please click 'Start queue' to start.",
            "Downloads Queued",
            JOptionPane.INFORMATION_MESSAGE);
    }

    private void setupDragAndDrop(java.awt.Component area) {
        new DropTarget(area, new DropTargetListener() {
            public void dragEnter(DropTargetDragEvent dtde) {}
            public void dragOver(DropTargetDragEvent dtde) {}
            public void dropActionChanged(DropTargetDragEvent dtde) {}
            public void dragExit(DropTargetEvent dte) {}
            @SuppressWarnings("unchecked")
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    java.util.List<File> files = (java.util.List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) loadFile(files.get(0));
                } catch (Exception e) {}
            }
        });
    }

    private JPanel createSlimStatPanel(String title, JProgressBar bar) {
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
        
        // Save references in client properties of the progress bar so we can update the labels in updateHardwareUI
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
        
        java.awt.Color panelBg = javax.swing.UIManager.getColor("Panel.background");
        java.awt.Color labelFg = javax.swing.UIManager.getColor("Label.foreground");
        
        boolean darkMode = configService.isDarkMode();
        String bgColor = panelBg != null ? String.format("#%02x%02x%02x", panelBg.getRed(), panelBg.getGreen(), panelBg.getBlue()) : (darkMode ? "#121214" : "#f8fafc");
        String textColor = labelFg != null ? String.format("#%02x%02x%02x", labelFg.getRed(), labelFg.getGreen(), labelFg.getBlue()) : (darkMode ? "#e2e8f0" : "#1e293b");
        
        String accentColor = darkMode ? "#818cf8" : "#4f46e5";
        String boxBg = darkMode ? "#1a1a24" : "#ffffff";
        String boxBorder = darkMode ? "#2d2d3d" : "#e2e8f0";
        String tipBg = darkMode ? "#222230" : "#f1f5f9";

        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='padding: 25px; font-family: sans-serif; background-color: ").append(bgColor).append("; color: ").append(textColor).append(";'>");
        sb.append("<h1 style='color: ").append(accentColor).append("; text-align: center; margin-bottom: 25px;'>🚀 Companion for ComfyUI</h1>");
        
        sb.append("<div style='background-color: ").append(boxBg).append("; padding: 15px; border-radius: 8px; margin-bottom: 20px; border: 1px solid ").append(boxBorder).append(";'>");
        sb.append("<h2 style='color: ").append(accentColor).append("; margin-top: 0;'>🛠️ Getting Started (Setup)</h2>");
        sb.append("<ol>");
        sb.append("<li><b>Set Directories:</b> Navigate to <i>Settings -> Directories</i> and specify your ComfyUI root and 'models' paths. Choose a 'Cold Archive' path (e.g. on a high-capacity HDD) to offload unused models.</li>");
        sb.append("<li><b>Unlock Vault:</b> Establish a Master Password on first startup. This AES-256 encrypted vault securely stores your sensitive API keys.</li>");
        sb.append("<li><b>Configure Audio & TTS:</b> Go to <i>Settings -> Audio & TTS Settings</i> to configure text-to-speech output.</li>");
        sb.append("<li><b>Install Canvas Bridge:</b> Click <i>Settings -> ComfyUI Bridge...</i> to add the 🚀 floating button to your ComfyUI workspace for seamless 1-click canvas syncing.</li>");
        sb.append("</ol></div>");

        sb.append("<h2 style='color: ").append(accentColor).append(";'>✨ Core Features</h2>");
        
        sb.append("<p><b>📦 Archive Manager:</b> Reclaim SSD space. Move massive, rarely used models (SDXL checkpoints, Flux models, etc.) to your cold storage archive. The app automatically detects when a loaded workflow needs archived models and offers <b>one-click restoration</b>.</p>");
        
        sb.append("<p><b>🧼 Storage Optimizer:</b> Automatically scans model folders for duplicate files using SHA-256 and Fast AutoV1 Hashing. Clean up redundant copies or instantly merge duplicates into NTFS hardlinks to preserve folder structure while freeing up physical space.</p>");
        
        sb.append("<p><b>🎨 Prompt Lab & AI Optimization:</b> Draft and refine prompts, select styles, and pick optional environmental contexts (including 'None (Optional)'). Features a powerful <b>Optimize with AI</b> button which detects the target model architecture (SD 1.5, SDXL, Flux) and rewrites prompts to suit that target.</p>");

        sb.append("<p><b>🔍 Model Update Checker & Tracker:</b> Scans your local models and checks Civitai to see if newer versions or upgrades are available. Download and update your models with a single click.</p>");

        sb.append("<p><b>🖼️ Output Gallery:</b> Live monitoring of the ComfyUI output directory. Renders images and videos asynchronously with support for selection checkboxes, bulk deletion, and file actions without slowing down the UI.</p>");

        sb.append("<p><b>📊 Workflow Graph Preview:</b> Displays a visual diagram of the ComfyUI nodes and linkages directly in the companion app whenever a workflow is loaded or synced.</p>");

        sb.append("<p><b>⚡ System Monitoring & Server Lifecycle:</b> Keeps track of real-time CPU, RAM, and VRAM utilization. Automatically cleans up active ports to resolve server startup crashes, streams server logs, and refreshes the browser automatically upon reconnect.</p>");

        sb.append("<p><b>🔌 Automatic App & Node Updater:</b> Keeps ComfyUI and installed Custom Nodes up-to-date with 1-click updates that run `git pull` and set up virtual environments automatically.</p>");

        sb.append("<div style='background-color: ").append(tipBg).append("; padding: 15px; border-radius: 8px; margin-top: 20px; border: 1px solid ").append(boxBorder).append(";'>");
        sb.append("<b>💡 Pro Tips:</b><br>");
        sb.append("• <b>Drag & Drop:</b> Drag any workflow JSON or generated image (.png with metadata) directly into the app window to import its model list.<br>");
        sb.append("• <b>Overnight Queue:</b> Enable 'Shutdown after Queue' in the downloader to automatically turn off the computer when downloads are done.<br>");
        sb.append("• <b>System Tray:</b> Enable 'Stay in Background' so the app minimizes to the system tray instead of closing.<br>");
        sb.append("• <b>Launch Profiles:</b> Select from 10 optimized launch arguments (like CPU Mode, Low VRAM, Beast Mode, or FP8 Flux optimizations) matching your hardware.");
        sb.append("</div>");
        
        sb.append("<p style='margin-top: 30px; color: #888; font-style: italic; text-align: center;'>Version 1.1.0 - Open Source Software developed by TKI</p>");
        sb.append("</body></html>");

        editorPane.setText(sb.toString());
        editorPane.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(editorPane, 
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, 
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
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
            configService.updateExtraModelPathsYaml();
            logger.info("[Bridge-Sync] Successfully synchronized latest bridge code and token.");
        } catch (IOException e) {
            logger.error("[Bridge-Sync] Failed to sync code: " + e.getMessage());
        }
    }

    private void startComfyUI(de.tki.comfymodels.domain.LaunchProfile selected, boolean clearConsole, boolean openBrowser) {
        if (selected == null) return;
        if (launchBtn != null) launchBtn.setEnabled(false);
        if (clearConsole && consoleOutput != null) {
            consoleOutput.setText("");
        }
        java.util.concurrent.atomic.AtomicBoolean cudaError = new java.util.concurrent.atomic.AtomicBoolean(false);
        processController.start(selected, Paths.get(configService.getComfyUIPath()), configService.getPythonPath(), log -> {
            if (log.contains("Torch not compiled with CUDA enabled") || log.contains("AssertionError: Torch not compiled with CUDA enabled")) {
                if (!cudaError.getAndSet(true)) {
                    SwingUtilities.invokeLater(() -> {
                        if (promptLabConsole != null) {
                            promptLabConsole.append("\n❌ CUDA ERROR: PyTorch is not compiled with CUDA enabled in your Python environment!\n");
                            promptLabConsole.append("💡 PyTorch is running as a CPU-only build. Click 'Environment Repair' in Settings to reinstall CUDA PyTorch.\n\n");
                        }
                        int choice = JOptionPane.showConfirmDialog(this,
                            "ComfyUI / PyTorch reported a CUDA error:\n'Torch not compiled with CUDA enabled'\n\n" +
                            "Your Python environment has a CPU-only PyTorch build installed.\n" +
                            "Would you like to automatically reinstall PyTorch with CUDA support now?",
                            "CUDA PyTorch Error Detected", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);
                        if (choice == JOptionPane.YES_OPTION) {
                            triggerEnvironmentRepair();
                        } else if (choice == JOptionPane.NO_OPTION) {
                            switchToCpuModeProfile();
                        }
                    });
                }
            }
            if (log.contains("To see the GUI go to:")) {
                refreshPromptLabModels();
                int idx = log.indexOf("http://");
                if (idx == -1) idx = log.indexOf("https://");
                if (idx != -1) {
                    String url = log.substring(idx).trim();
                    backgroundExecutor.execute(() -> {
                        try {
                            if (Desktop.isDesktopSupported() && openBrowser) {
                                Desktop.getDesktop().browse(new java.net.URI(url));
                            }
                        } catch (Exception e) {
                            logger.error("Failed to open browser automatically: " + e.getMessage());
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
                        switchToCpuModeProfile();
                    }
                });
            }
        });
    }

    private void switchToCpuModeProfile() {
        List<de.tki.comfymodels.domain.LaunchProfile> profiles = profileManager.loadProfiles();
        de.tki.comfymodels.domain.LaunchProfile cpuProfile = profiles.stream()
            .filter(p -> p.id().equals("cpu_mode"))
            .findFirst().orElse(null);
        if (cpuProfile != null) {
            if (profileList != null) {
                profileList.setSelectedValue(cpuProfile, true);
            }
            startComfyUI(cpuProfile, true, true);
        } else {
            JOptionPane.showMessageDialog(this, "CPU Mode profile not found. Please add '--cpu' to your launch profile extra args in Settings.");
        }
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
        if (downloadManagerController != null) {
            downloadManagerController.showCivitaiSearchDialog(row);
        }
    }

    public static void main(String[] args) {
        FlatLaf.setUseNativeWindowDecorations(true);
        appContext = new AnnotationConfigApplicationContext(AppConfig.class);
        appContext.getBean(Main.class).launch(args);
    }

    @Configuration @ComponentScan("de.tki.comfymodels") public static class AppConfig {}
}
