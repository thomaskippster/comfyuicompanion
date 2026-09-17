package de.tki.comfyuicompanion;

import com.formdev.flatlaf.FlatLaf;
import de.tki.comfyuicompanion.controller.DownloadManagerController;
import de.tki.comfyuicompanion.controller.PromptLabController;
import de.tki.comfyuicompanion.controller.PromptLabExecutionHandler;
import de.tki.comfyuicompanion.domain.LaunchProfile;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.*;
import de.tki.comfyuicompanion.service.impl.*;
import de.tki.comfyuicompanion.ui.*;
import de.tki.comfyuicompanion.ui.dialog.ComfyLifecycleDialog;
import de.tki.comfyuicompanion.ui.dialog.MainSettingsDialogs;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
import de.tki.comfyuicompanion.ui.panel.MainDashboardPanel;
import de.tki.comfyuicompanion.ui.panel.MainSettingsPanel;
import de.tki.comfyuicompanion.util.BackgroundExecutor;
import de.tki.comfyuicompanion.util.PlatformUtils;
import de.tki.comfyuicompanion.controller.PromptLabModelResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.config.EnableWebFlux;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main application window and coordinator for Companion for ComfyUI.
 */
@Component
public class Main extends JFrame {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static volatile ConfigurableApplicationContext appContext;

    private final IModelAnalyzer analyzer;
    private final IDownloadManager downloadManager;
    private final IWorkflowService workflowService;
    private final IModelSearchService searchService;
    private final IModelValidator modelValidator;
    private final RestBridgeService restBridge;
    private final ArchiveService archiveService;
    private final IComfyLifecycleService lifecycleService;
    private final ComfyDiagnosticService diagnosticService;
    private final ProfileManager profileManager;
    private final EnvironmentBootstrapperImpl bootstrapper;
    private final ComfyProcessController processController;
    private final CivitaiService civitaiService;
    private final HuggingFaceService huggingFaceService;

    private ConfigService configService;
    private IComfyTemplateService comfyTemplateService;
    private LocalAIService localAIService;
    private ModelListService modelListService;
    private ModelHashRegistry hashRegistry;
    private LocalModelScanner localScanner;
    private PathResolver pathResolver;
    private VersionService versionService;
    private IModelArchitectureService modelArchitectureService;
    private HardwareMonitorService hardwareMonitorService;
    private UpdaterService updaterService;
    private VideoArchitectTab videoArchitectTab;
    private BlueprintGalleryTab blueprintGalleryTab;
    private DependencyService dependencyService;
    private BackgroundExecutor backgroundExecutor;
    private ProcessTracker processTracker;
    private PromptBlueprintApiService promptBlueprintApiService;
    private ComfyApiClient comfyApiClient;
    private PromptLabView promptLabView;
    private PromptLabController promptLabController;
    private DownloadManagerView downloadManagerView;
    private DownloadManagerController downloadManagerController;
    private IWorkflowDownloader workflowDownloader;
    private AppLifecycleManager appLifecycleManager;
    private IDefaultCacheBootstrapper defaultCacheBootstrapper;

    // Extracted coordinators & handlers
    private ComfyBridgeManager comfyBridgeManager;
    private PromptLabExecutionHandler promptLabExecutionHandler;
    private MainDashboardPanel dashboardPanelCoordinator;
    private MainSettingsPanel settingsPanelCoordinator;
    private MainHeaderBar headerBar;
    private ComfyInstallationScanner comfyInstallationScanner;

    // UI tab wrappers & components
    private JTabbedPane mainTabs;
    private JPanel dashboardPanel;
    private JPanel downloadManagerPanel;
    private JPanel promptLabPanel;
    private OutputGalleryPanel galleryPanel;
    private JPanel videoArchitectWrapper;
    private JPanel blueprintGalleryWrapper;
    private JPanel settingsPanel;
    private Image appIcon;
    private String currentFileName = "input.json";

    // Legacy test/reflection access fields
    final Set<String> comfyCheckpoints = ConcurrentHashMap.newKeySet();
    final Set<String> comfyUnetModels = ConcurrentHashMap.newKeySet();
    final Set<String> comfyClips = ConcurrentHashMap.newKeySet();
    final Set<String> comfyVaes = ConcurrentHashMap.newKeySet();
    final Set<String> comfyClipTypes = ConcurrentHashMap.newKeySet();
    final Set<String> comfyUnetWeightDtypes = ConcurrentHashMap.newKeySet();
    DefaultTableModel tableModel;
    JTextArea jsonInputArea;
    JLabel statusLabel;
    JButton downloadButton;
    JButton pauseButton;
    JButton stopButton;
    JLabel activeAiModelLabel;
    boolean isDownloading;

    @Autowired
    public Main(IModelAnalyzer analyzer, IDownloadManager downloadManager, IWorkflowService workflowService,
                IModelSearchService searchService, IModelValidator modelValidator, RestBridgeService restBridge,
                ArchiveService archiveService, IComfyLifecycleService lifecycleService, ComfyDiagnosticService diagnosticService,
                ProfileManager profileManager, EnvironmentBootstrapperImpl bootstrapper, ComfyProcessController processController,
                CivitaiService civitaiService, HuggingFaceService huggingFaceService,
                @Autowired(required = false) ConfigService configService,
                @Autowired(required = false) IComfyTemplateService comfyTemplateService,
                @Autowired(required = false) LocalAIService localAIService,
                @Autowired(required = false) ModelListService modelListService,
                @Autowired(required = false) ModelHashRegistry hashRegistry,
                @Autowired(required = false) LocalModelScanner localScanner,
                @Autowired(required = false) PathResolver pathResolver,
                @Autowired(required = false) VersionService versionService,
                @Autowired(required = false) IModelArchitectureService modelArchitectureService,
                @Autowired(required = false) HardwareMonitorService hardwareMonitorService,
                @Autowired(required = false) UpdaterService updaterService,
                @Autowired(required = false) VideoArchitectTab videoArchitectTab,
                @Autowired(required = false) BlueprintGalleryTab blueprintGalleryTab,
                @Autowired(required = false) DependencyService dependencyService,
                @Autowired(required = false) BackgroundExecutor backgroundExecutor,
                @Autowired(required = false) ProcessTracker processTracker,
                @Autowired(required = false) PromptBlueprintApiService promptBlueprintApiService,
                @Autowired(required = false) ComfyApiClient comfyApiClient,
                @Autowired(required = false) PromptLabView promptLabView,
                @Autowired(required = false) PromptLabController promptLabController,
                @Autowired(required = false) DownloadManagerView downloadManagerView,
                @Autowired(required = false) DownloadManagerController downloadManagerController,
                @Autowired(required = false) IWorkflowDownloader workflowDownloader,
                @Autowired(required = false) AppLifecycleManager appLifecycleManager,
                @Autowired(required = false) IDefaultCacheBootstrapper defaultCacheBootstrapper,
                @Autowired(required = false) ComfyInstallationScanner comfyInstallationScanner) {
        this(analyzer, downloadManager, workflowService, searchService, modelValidator, restBridge, archiveService,
             lifecycleService, diagnosticService, profileManager, bootstrapper, processController, civitaiService, huggingFaceService);
        this.configService = configService;
        this.comfyTemplateService = comfyTemplateService;
        this.localAIService = localAIService;
        this.modelListService = modelListService;
        this.hashRegistry = hashRegistry;
        this.localScanner = localScanner;
        this.pathResolver = pathResolver;
        this.versionService = versionService;
        this.modelArchitectureService = modelArchitectureService;
        this.hardwareMonitorService = hardwareMonitorService;
        this.updaterService = updaterService;
        this.videoArchitectTab = videoArchitectTab;
        this.blueprintGalleryTab = blueprintGalleryTab;
        this.dependencyService = dependencyService;
        this.backgroundExecutor = backgroundExecutor;
        this.processTracker = processTracker;
        this.promptBlueprintApiService = promptBlueprintApiService;
        this.comfyApiClient = comfyApiClient;
        this.promptLabView = promptLabView;
        this.promptLabController = promptLabController;
        this.downloadManagerView = downloadManagerView;
        this.downloadManagerController = downloadManagerController;
        this.workflowDownloader = workflowDownloader;
        this.appLifecycleManager = appLifecycleManager;
        this.defaultCacheBootstrapper = defaultCacheBootstrapper;
        this.comfyInstallationScanner = comfyInstallationScanner;
    }

    public Main(IModelAnalyzer analyzer, IDownloadManager downloadManager, IWorkflowService workflowService,
                IModelSearchService searchService, IModelValidator modelValidator, RestBridgeService restBridge,
                ArchiveService archiveService, IComfyLifecycleService lifecycleService, ComfyDiagnosticService diagnosticService,
                ProfileManager profileManager, EnvironmentBootstrapperImpl bootstrapper, ComfyProcessController processController,
                CivitaiService civitaiService, HuggingFaceService huggingFaceService) {
        this.analyzer = analyzer;
        this.downloadManager = downloadManager;
        this.workflowService = workflowService;
        this.searchService = searchService;
        this.modelValidator = modelValidator;
        this.restBridge = restBridge;
        this.archiveService = archiveService;
        this.lifecycleService = lifecycleService;
        if (this.lifecycleService != null) {
            this.lifecycleService.setOnBrowserLaunched(() -> {
                SwingUtilities.invokeLater(() -> {
                    refreshPromptLabModels();
                    if (blueprintGalleryTab != null) {
                        blueprintGalleryTab.refreshAllData();
                    }
                });
            });
        }
        this.diagnosticService = diagnosticService;
        this.profileManager = profileManager;
        this.bootstrapper = bootstrapper;
        this.processController = processController;
        this.civitaiService = civitaiService;
        this.huggingFaceService = huggingFaceService;
    }

    public void launch(String[] args) {
        if (defaultCacheBootstrapper != null) {
            defaultCacheBootstrapper.bootstrapDefaultCache();
        }

        Path appData = Paths.get(System.getProperty("user.home"), ".comfyui-companion");
        try {
            Files.createDirectories(appData);
        } catch (Exception ex) {
            logger.error("FATAL: Cannot create application data directory: " + appData + " - " + ex.getMessage());
            JOptionPane.showMessageDialog(null, "Cannot create application data directory:\n" + appData + "\n\n" + ex.getMessage(), "Startup Error", JOptionPane.ERROR_MESSAGE);
        }
        profileManager.init(appData);

        if (PlatformUtils.isMac()) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("apple.awt.application.name", "Companion for ComfyUI");
            System.setProperty("apple.awt.application.appearance", "system");
        }

        restBridge.setWorkflowConsumer(workflowJson -> {
            SwingUtilities.invokeLater(() -> {
                logger.info("[Main] WorkflowConsumer triggered - bringing to front.");
                loadWorkflowExternal("remote_workflow", workflowJson);
                setVisible(true);
                setExtendedState(JFrame.NORMAL);
                toFront();
                requestFocus();
            });
        });

        if (configService != null && configService.isUnlocked()) {
            restBridge.setApiToken(configService.getApiToken());
        }
        restBridge.startServer();

        if (appLifecycleManager != null) {
            appLifecycleManager.registerShutdownHook(appContext);
        } else {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (processController != null) processController.stop();
                if (downloadManager != null) downloadManager.stop();
            }));
        }

        boolean unlocked = appLifecycleManager != null ? appLifecycleManager.unlockDefaultVault() : promptForPassword();
        if (!unlocked) {
            System.exit(0);
        }

        setupTheme(configService != null && configService.isDarkMode());

        SwingUtilities.invokeLater(() -> {
            try {
                initUI();
                setupTrayIcon();
                loadSettingsIntoUI();
                updateAiModelDisplay();

                if (hardwareMonitorService != null) {
                    hardwareMonitorService.start(stats -> SwingUtilities.invokeLater(() -> updateHardwareUI(stats)));
                }

                addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        if (configService != null && configService.isBackgroundModeEnabled()) {
                            setVisible(false);
                        } else {
                            performAppExit();
                        }
                    }
                });

                setVisible(true);
                if (blueprintGalleryTab != null) {
                    blueprintGalleryTab.refreshAllData();
                }
                scanAndVerifyComfyUIInstallation(false);
            } catch (Exception e) {
                logger.error("Critical UI Error", e);
                JOptionPane.showMessageDialog(null, "Critical UI Error: " + e.getMessage());
            }
        });
    }

    private void initUI() {
        loadIcon();
        setTitle("Companion for ComfyUI");
        setSize(1450, 950);
        setMinimumSize(new Dimension(1200, 800));
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        getRootPane().putClientProperty("flatlaf.useWindowDecorations", true);

        JPanel rootPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                paintWindowBackground(g, getWidth(), getHeight());
                super.paintComponent(g);
            }
        };
        rootPanel.setOpaque(true);

        headerBar = new MainHeaderBar(this, configService, modelArchitectureService,
                processController, lifecycleService, profileManager,
                p -> startComfyUI(p, false, !configService.isHideComfyUI()));

        mainTabs = new JTabbedPane();

        // Tab 1: Dashboard
        dashboardPanelCoordinator = new MainDashboardPanel(this, configService, profileManager,
                lifecycleService, updaterService, hashRegistry, modelValidator, civitaiService, downloadManager);
        dashboardPanelCoordinator.setStartComfyHandler(p -> startComfyUI(p, false, !configService.isHideComfyUI()));
        dashboardPanelCoordinator.setFullServiceRestartHandler(this::performFullServiceRestart);
        dashboardPanelCoordinator.setOpenBootstrapHandler(this::showInstallationDialog);
        dashboardPanel = dashboardPanelCoordinator.createDashboardPanel(mainTabs);
        refreshVersions();

        // Tab 2: Download Manager
        downloadManagerPanel = createManagerPanel(mainTabs);

        // Tab 3: Output Gallery
        galleryPanel = new OutputGalleryPanel(configService);

        // Tab 4: Blueprint Gallery
        blueprintGalleryWrapper = new JPanel(new BorderLayout());
        blueprintGalleryWrapper.setName("blueprintGalleryWrapper");
        blueprintGalleryWrapper.setOpaque(true);
        if (blueprintGalleryTab != null) {
            blueprintGalleryWrapper.add(blueprintGalleryTab, BorderLayout.CENTER);
            blueprintGalleryTab.setOnDataLoadedCallback(this::refreshPromptLabModels);
            blueprintGalleryTab.setOnWorkflowImportRequested(this::importWorkflow);
        } else {
            blueprintGalleryWrapper.add(new JPanel(), BorderLayout.CENTER);
        }

        // Tab 5: Image Lab (Prompt Lab)
        promptLabExecutionHandler = new PromptLabExecutionHandler(configService, localAIService,
                lifecycleService, comfyApiClient, profileManager, promptLabController,
                backgroundExecutor != null ? backgroundExecutor.getExecutor() : null,
                p -> startComfyUI(p, false, !configService.isHideComfyUI()),
                img -> {
                    if (promptLabView != null) promptLabView.scaleAndSetImage(img);
                },
                this::updateAiModelDisplay);
        promptLabPanel = createPromptLabPanel();

        // Tab 6: Video Architect (Beta)
        videoArchitectWrapper = new JPanel(new BorderLayout());
        videoArchitectWrapper.setName("videoArchitectWrapper");
        videoArchitectWrapper.setOpaque(true);
        if (videoArchitectTab != null) {
            videoArchitectWrapper.add(videoArchitectTab, BorderLayout.CENTER);
        } else {
            JPanel fallback = new JPanel(new GridBagLayout());
            fallback.setOpaque(false);
            JLabel label = new JLabel("Video Architect (Beta) is disabled or initializing...", SvgIconFactory.get(AppIcon.VIDEO_ARCHITECT, 20), SwingConstants.CENTER);
            label.setFont(new Font("SansSerif", Font.BOLD, 14));
            label.setForeground(configService != null && configService.isDarkMode() ? Color.LIGHT_GRAY : Color.DARK_GRAY);
            fallback.add(label);
            videoArchitectWrapper.add(fallback, BorderLayout.CENTER);
        }

        // Tab 7: Settings
        settingsPanelCoordinator = new MainSettingsPanel(this, configService, modelArchitectureService);
        settingsPanelCoordinator.setOnTabVisibilityChanged(this::updateTabVisibility);
        settingsPanelCoordinator.setOnThemeChanged(this::setupTheme);
        settingsPanel = settingsPanelCoordinator.createSettingsPanel(
                this::showPathsDialog, () -> scanAndVerifyComfyUIInstallation(true),
                this::triggerEnvironmentRepair, this::triggerWslDependencyFix,
                this::showDownloadSettingsDialog, this::showInstallationDialog,
                this::showVideoArchitectAutoconfigDialog, this::showApiKeysDialog,
                this::showHelpDialog, this::resetSettings);

        updateTabVisibility();

        rootPanel.add(headerBar, BorderLayout.NORTH);
        rootPanel.add(mainTabs, BorderLayout.CENTER);
        setContentPane(rootPanel);
    }

    private JPanel createPromptLabPanel() {
        if (promptLabView != null && promptLabController != null) {
            promptLabController.setView(promptLabView);
            promptLabController.setBlueprintGalleryTab(blueprintGalleryTab);
            promptLabController.setSendPromptHandler(promptLabExecutionHandler::sendPromptToComfyUI);
            promptLabController.setSuggestSubjectHandler(promptLabExecutionHandler::suggestSubjectCompletions);
            promptLabExecutionHandler.setPromptLabView(promptLabView);
            promptLabController.loadPromptLabSession();
            promptLabController.updatePromptLabJson();
            return promptLabView;
        }
        return new JPanel();
    }

    private JPanel createManagerPanel(JTabbedPane tabs) {
        if (downloadManagerView != null && downloadManagerController != null) {
            downloadManagerController.setView(downloadManagerView);
            this.tableModel = downloadManagerView.getTableModel();
            this.jsonInputArea = downloadManagerView.getJsonInputArea();
            this.statusLabel = downloadManagerView.getStatusLabel();
            this.downloadButton = downloadManagerView.getDownloadButton();
            this.pauseButton = downloadManagerView.getPauseButton();
            this.stopButton = downloadManagerView.getStopButton();
            this.activeAiModelLabel = downloadManagerView.getActiveAiModelLabel();
            return downloadManagerView;
        }
        return new JPanel();
    }

    public void setupTheme(boolean darkMode) {
        try {
            ThemeManager.applyTheme(darkMode);

            if (dashboardPanelCoordinator != null && dashboardPanelCoordinator.getComfyConsoleArea() != null) {
                JTextArea area = dashboardPanelCoordinator.getComfyConsoleArea();
                if (darkMode) {
                    area.setBackground(UIManager.getColor("TextArea.background"));
                    area.setForeground(UIManager.getColor("TextArea.foreground"));
                } else {
                    area.setBackground(new Color(245, 247, 250));
                    area.setForeground(new Color(30, 30, 30));
                }
            }

            if (dashboardPanel != null) SwingUtilities.updateComponentTreeUI(dashboardPanel);
            if (videoArchitectTab != null) {
                SwingUtilities.updateComponentTreeUI(videoArchitectTab);
                videoArchitectTab.updateTheme(darkMode);
            }
            if (blueprintGalleryTab != null) {
                SwingUtilities.updateComponentTreeUI(blueprintGalleryTab);
                blueprintGalleryTab.updateTheme(darkMode);
            }
            if (downloadManagerView != null) SwingUtilities.updateComponentTreeUI(downloadManagerView);
            if (downloadManagerPanel != null) SwingUtilities.updateComponentTreeUI(downloadManagerPanel);
            if (promptLabView != null) {
                SwingUtilities.updateComponentTreeUI(promptLabView);
                promptLabView.updateTheme(darkMode);
            }
            if (galleryPanel != null) SwingUtilities.updateComponentTreeUI(galleryPanel);
            if (settingsPanel != null) SwingUtilities.updateComponentTreeUI(settingsPanel);

            FlatLaf.updateUI();
            SwingUtilities.updateComponentTreeUI(this);
        } catch (Exception e) {
            logger.error("Theme setup failed: " + e.getMessage());
        }
    }

    private void loadIcon() {
        try (InputStream is = getClass().getResourceAsStream("/icons/app_icon.jpg")) {
            if (is != null) {
                appIcon = ImageIO.read(is);
                if (appIcon != null) {
                    setIconImage(appIcon);
                }
            }
        } catch (IOException e) {
            logger.error("Could not load app icon: " + e.getMessage());
        }
    }

    private void setupTrayIcon() {
        MainSystemTrayHandler.setupTrayIcon(
                this,
                appIcon,
                configService,
                settingsPanelCoordinator != null ? settingsPanelCoordinator.getBackgroundCheck() : null,
                this::performAppExit
        );
    }

    private void paintWindowBackground(Graphics g, int w, int h) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color start = UIManager.getColor("MainTabs.gradientStart");
        Color end = UIManager.getColor("MainTabs.gradientEnd");
        g2.setPaint(new GradientPaint(0, 0, start != null ? start : new Color(14, 15, 17), 0, h, end != null ? end : new Color(24, 28, 38)));
        g2.fillRect(0, 0, w, h);
        Color glowStart = UIManager.getColor("MainTabs.glowStart");
        g2.setPaint(new RadialGradientPaint(new java.awt.geom.Point2D.Float(w * 0.85f, h * 0.15f), Math.max(w, h) * 0.45f,
                new float[]{0.0f, 1.0f}, new Color[]{glowStart != null ? glowStart : new Color(0, 240, 255, 12), new Color(0, 0, 0, 0)}));
        g2.fillRect(0, 0, w, h);
        g2.dispose();
    }

    private void appendToConsole(String log) {
        SwingUtilities.invokeLater(() -> {
            if (dashboardPanelCoordinator != null && dashboardPanelCoordinator.getComfyConsoleArea() != null) {
                JTextArea area = dashboardPanelCoordinator.getComfyConsoleArea();
                area.append(log.endsWith("\n") ? log : log + "\n");
                area.setCaretPosition(area.getDocument().getLength());
            }
        });
    }

    private boolean promptForPassword() {
        if (configService == null) return false;
        setupTheme(configService.isDarkMode());
        String defaultPass = System.getProperty("user.name", "default") + "@" + getHostName();
        try {
            configService.unlock(defaultPass);
            initVaultIfFresh();
            return true;
        } catch (Exception e) {
            logger.warn("Could not unlock vault with default password. Resetting vault to start clean...");
            try {
                configService.resetVault();
                configService.unlock(defaultPass);
                initVaultIfFresh();
                return true;
            } catch (Exception ex) {
                logger.error("Failed to reset and unlock vault: {}", ex.getMessage(), ex);
                return false;
            }
        }
    }

    private void initVaultIfFresh() {
        if (configService.isVaultFresh() && modelListService != null) {
            modelListService.importFromUrl("https://raw.githubusercontent.com/Comfy-Org/ComfyUI-Manager/main/model-list.json");
        }
    }

    private String getHostName() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "localhost";
        }
    }

    public void updateTabVisibility() {
        if (mainTabs == null || configService == null) return;
        java.awt.Component selectedComp = mainTabs.getSelectedComponent();
        mainTabs.removeAll();

        if (dashboardPanel != null) {
            mainTabs.addTab("Dashboard", SvgIconFactory.get(AppIcon.DASHBOARD, SvgIconFactory.SIZE_TAB), dashboardPanel);
        }
        if (downloadManagerPanel != null) {
            mainTabs.addTab("Download Manager", SvgIconFactory.get(AppIcon.DOWNLOAD_MANAGER, SvgIconFactory.SIZE_TAB), downloadManagerPanel);
        }
        if (galleryPanel != null) {
            mainTabs.addTab("Gallery", SvgIconFactory.get(AppIcon.GALLERY, SvgIconFactory.SIZE_TAB), galleryPanel);
        }
        if (configService.isBlueprintGalleryEnabled() && blueprintGalleryWrapper != null) {
            mainTabs.addTab("Blueprint Gallery", SvgIconFactory.get(AppIcon.BLUEPRINT_GALLERY, SvgIconFactory.SIZE_TAB), blueprintGalleryWrapper);
        }
        if (configService.isPromptLabEnabled() && promptLabPanel != null) {
            mainTabs.addTab("Image Lab", SvgIconFactory.get(AppIcon.IMAGE_LAB, SvgIconFactory.SIZE_TAB), promptLabPanel);
        }
        if (configService.isVideoArchitectEnabled() && videoArchitectWrapper != null) {
            mainTabs.addTab("Video Architect (Beta)", SvgIconFactory.get(AppIcon.VIDEO_ARCHITECT, SvgIconFactory.SIZE_TAB), videoArchitectWrapper);
        }
        if (settingsPanel != null) {
            mainTabs.addTab("Settings", SvgIconFactory.get(AppIcon.SETTINGS, SvgIconFactory.SIZE_TAB), settingsPanel);
        }

        if (selectedComp != null) {
            int index = mainTabs.indexOfComponent(selectedComp);
            mainTabs.setSelectedIndex(index != -1 ? index : 0);
        }
    }

    private void loadSettingsIntoUI() {
        if (settingsPanelCoordinator != null) {
            settingsPanelCoordinator.loadSettingsIntoUI();
        }
    }

    private void updateAiModelDisplay() {
        if (backgroundExecutor == null) return;
        backgroundExecutor.execute(() -> {
            final boolean hasGemma = localAIService != null && localAIService.isLocalGemmaDownloaded();
            SwingUtilities.invokeLater(() -> {
                if (promptLabView != null && promptLabView.getBtnSuggestSubject() != null) {
                    promptLabView.getBtnSuggestSubject().setToolTipText(hasGemma
                            ? "Suggest creative expansions for this subject using local Gemma."
                            : "Download local Gemma model to unlock suggestions.");
                }
                String activeText = hasGemma ? "Active AI: Local Gemma" : "Active AI: None / Cloud";
                if (activeAiModelLabel != null) {
                    activeAiModelLabel.setText(activeText);
                }
                if (downloadManagerView != null && downloadManagerView.getActiveAiModelLabel() != null) {
                    downloadManagerView.getActiveAiModelLabel().setText(activeText);
                }
            });
        });
    }

    public void startComfyUI(LaunchProfile selected, boolean clearConsole, boolean openBrowser) {
        if (selected == null) return;
        if (clearConsole && dashboardPanelCoordinator != null && dashboardPanelCoordinator.getComfyConsoleArea() != null) {
            dashboardPanelCoordinator.getComfyConsoleArea().setText("");
        }
        AtomicBoolean cudaError = new AtomicBoolean(false);
        processController.start(selected, Paths.get(configService.getComfyUIPath()), configService.getPythonPath(), log -> {
            if (log.contains("Torch not compiled with CUDA enabled") || log.contains("AssertionError: Torch not compiled with CUDA enabled")) {
                if (!cudaError.getAndSet(true)) {
                    SwingUtilities.invokeLater(() -> {
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
                            boolean shouldOpen = openBrowser && (configService == null || !configService.isHideComfyUI());
                            if (Desktop.isDesktopSupported() && shouldOpen) {
                                Desktop.getDesktop().browse(new java.net.URI(url));
                            }
                        } catch (Exception e) {
                            logger.error("Failed to open browser automatically: " + e.getMessage());
                        }
                    });
                }
            }
            appendToConsole(log);
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
        List<LaunchProfile> profiles = profileManager.loadProfiles();
        LaunchProfile cpuProfile = profiles.stream()
                .filter(p -> p.id().equals("cpu_mode"))
                .findFirst().orElse(null);
        if (cpuProfile != null) {
            if (dashboardPanelCoordinator != null && dashboardPanelCoordinator.getProfileList() != null) {
                dashboardPanelCoordinator.getProfileList().setSelectedValue(cpuProfile, true);
            }
            startComfyUI(cpuProfile, true, configService == null || !configService.isHideComfyUI());
        } else {
            JOptionPane.showMessageDialog(this, "CPU Mode profile not found. Please add '--cpu' to your launch profile extra args in Settings.");
        }
    }

    public void performFullServiceRestart() {
        backgroundExecutor.execute(() -> {
            processController.stop();
            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}

            SwingUtilities.invokeLater(() -> {
                if (mainTabs != null) {
                    mainTabs.setSelectedIndex(0);
                }

                LaunchProfile activeProfile = null;
                if (dashboardPanelCoordinator != null && dashboardPanelCoordinator.getProfileList() != null) {
                    activeProfile = dashboardPanelCoordinator.getProfileList().getSelectedValue();
                }
                if (activeProfile == null && configService != null) {
                    String activeId = configService.getActiveProfile();
                    List<LaunchProfile> profiles = profileManager.loadProfiles();
                    activeProfile = profiles.stream()
                            .filter(p -> p.id().equals(activeId))
                            .findFirst()
                            .orElse(null);
                    if (activeProfile == null && !profiles.isEmpty()) {
                        activeProfile = profiles.get(0);
                    }
                }

                if (activeProfile != null) {
                    if (dashboardPanelCoordinator != null && dashboardPanelCoordinator.getProfileList() != null) {
                        dashboardPanelCoordinator.getProfileList().setSelectedValue(activeProfile, true);
                    }
                    if (dashboardPanelCoordinator != null && dashboardPanelCoordinator.getComfyConsoleArea() != null) {
                        dashboardPanelCoordinator.getComfyConsoleArea().append("\n🔄 Restarting ComfyUI...\n");
                    }
                    startComfyUI(activeProfile, false, configService == null || !configService.isHideComfyUI());
                }
            });
        });
    }

    public boolean scanAndVerifyComfyUIInstallation(boolean showDialogIfFound) {
        if (comfyInstallationScanner == null) {
            comfyInstallationScanner = new ComfyInstallationScanner(configService, bootstrapper, profileManager);
        }
        return comfyInstallationScanner.scanAndVerifyInstallation(this, showDialogIfFound, () -> {
            syncBridgeFiles();
            refreshVersions();
        });
    }

    private void startComfyAndReload() {
        backgroundExecutor.execute(() -> {
            lifecycleService.start();
            for (int i = 0; i < 30; i++) {
                if (lifecycleService.isHealthy()) {
                    return;
                }
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
        });
    }

    public void installComfyUIBridge(String comfyPath, JDialog parentDialog) {
        if (comfyBridgeManager == null) {
            comfyBridgeManager = new ComfyBridgeManager(configService, pathResolver);
        }
        comfyBridgeManager.installComfyUIBridge(comfyPath, parentDialog);
    }

    public void syncBridgeFiles() {
        if (comfyBridgeManager == null) {
            comfyBridgeManager = new ComfyBridgeManager(configService, pathResolver);
        }
        comfyBridgeManager.syncBridgeFiles();
    }

    private void refreshVersions() {
        if (dashboardPanelCoordinator != null) {
            dashboardPanelCoordinator.refreshVersions(versionService, configService);
        }
    }

    private void showLifecycleDialog() {
        ComfyLifecycleDialog.showDialog(this, configService, lifecycleService,
                backgroundExecutor != null ? backgroundExecutor.getExecutor() : null, this::startComfyAndReload);
    }

    private void showPathsDialog() {
        MainSettingsDialogs.showPathsDialog(this, configService, lifecycleService,
                backgroundExecutor != null ? backgroundExecutor.getExecutor() : null,
                this::refreshVersions, this::syncBridgeFiles);
    }

    private void showDownloadSettingsDialog() {
        MainSettingsDialogs.showDownloadSettingsDialog(this, configService, null);
    }

    private void showVideoArchitectAutoconfigDialog() {
        MainSettingsDialogs.showVideoArchitectAutoconfigDialog(this, dependencyService,
                backgroundExecutor != null ? backgroundExecutor.getExecutor() : null);
    }

    private void showApiKeysDialog() {
        MainSettingsDialogs.showApiKeysDialog(this, configService, this::updateAiModelDisplay);
    }

    private void showInstallationDialog() {
        MainSettingsDialogs.showInstallationDialog(this, configService, this::syncBridgeFiles, dlg -> installComfyUIBridge(configService != null ? configService.getComfyUIPath() : "", dlg));
    }

    private void showHelpDialog() {
        MainSettingsDialogs.showHelpDialog(this, configService);
    }

    private void resetSettings() {
        int opt = JOptionPane.showConfirmDialog(this, "Reset all settings to default values?", "Confirm Reset", JOptionPane.YES_NO_OPTION);
        if (opt == JOptionPane.YES_OPTION && configService != null) {
            configService.resetVault();
            loadSettingsIntoUI();
            updateTabVisibility();
            setupTheme(configService.isDarkMode());
        }
    }

    public void importWorkflow(File file) {
        if (downloadManagerController != null) {
            downloadManagerController.importWorkflow(file, mainTabs, downloadManagerPanel);
        }
    }

    public void focusDownloadTabAndSelectModels(List<ModelInfo> missingModels) {
        if (mainTabs != null) {
            mainTabs.setSelectedIndex(1);
        }
        if (downloadManagerController != null) {
            downloadManagerController.focusDownloadTabAndSelectModels(missingModels);
        }
    }

    public String getWorkspaceWorkflowJson() {
        return (downloadManagerView != null && downloadManagerView.getJsonInputArea() != null)
                ? downloadManagerView.getJsonInputArea().getText() : "";
    }

    public void loadWorkflowExternal(String name, String json) {
        SwingUtilities.invokeLater(() -> {
            if (downloadManagerView != null && downloadManagerView.getJsonInputArea() != null) {
                downloadManagerView.getJsonInputArea().setText(json);
                currentFileName = name != null ? name + ".json" : "library_workflow.json";
                if (downloadManagerController != null) {
                    downloadManagerController.analyzeJsonContent();
                    downloadManagerController.searchMissingOnline(false);
                }
                if (mainTabs != null) {
                    mainTabs.setSelectedIndex(1);
                }
            }
        });
    }

    public void refreshPromptLabModels() {
        if (promptLabController != null) {
            promptLabController.refreshPromptLabModels();
        }
    }

    public void triggerEnvironmentRepair() {
        if (mainTabs != null) mainTabs.setSelectedIndex(0);
        if (dashboardPanelCoordinator != null && dashboardPanelCoordinator.getComfyConsoleArea() != null) {
            dashboardPanelCoordinator.getComfyConsoleArea().setText("");
        }
        backgroundExecutor.execute(() -> updaterService.repairEnvironment(this::appendToConsole));
    }

    public void triggerWslDependencyFix() {
        if (mainTabs != null) mainTabs.setSelectedIndex(0);
        if (dashboardPanelCoordinator != null && dashboardPanelCoordinator.getComfyConsoleArea() != null) {
            dashboardPanelCoordinator.getComfyConsoleArea().setText("");
        }
        String comfyPathStr = configService != null ? configService.getComfyUIPath() : null;
        Path comfyDir = (comfyPathStr != null && !comfyPathStr.isEmpty()) ? Paths.get(comfyPathStr) : null;
        backgroundExecutor.execute(() -> bootstrapper.fixWslDependencies(comfyDir, this::appendToConsole));
    }

    // --- Legacy Reflection & Resolution Hooks for Test Support ---

    private PromptLabModelResolver createModelResolver() {
        PromptLabModelResolver resolver = new PromptLabModelResolver(modelArchitectureService);
        resolver.getComfyClips().addAll(this.comfyClips);
        resolver.getComfyVaes().addAll(this.comfyVaes);
        resolver.getComfyClipTypes().addAll(this.comfyClipTypes);
        resolver.getComfyCheckpoints().addAll(this.comfyCheckpoints);
        resolver.getComfyUnetModels().addAll(this.comfyUnetModels);
        resolver.getComfyUnetWeightDtypes().addAll(this.comfyUnetWeightDtypes);
        return resolver;
    }

    private String resolveClipForModel(String selectedModel) {
        return createModelResolver().resolveClipForModel(selectedModel);
    }

    private String resolveClipType(String clipModel, String selectedModel) {
        return createModelResolver().resolveClipType(clipModel, selectedModel);
    }

    private String resolveVaeForModel(String modelName) {
        return createModelResolver().resolveVaeForModel(modelName);
    }

    private void analyzeJsonContent() {
        if (downloadManagerController != null) {
            downloadManagerController.analyzeJsonContent();
        }
    }

    public void performAppExit() {
        if (lifecycleService != null) lifecycleService.stop();
        if (downloadManager != null) downloadManager.stop();
        System.exit(0);
    }

    public void performSystemShutdown() {
        PlatformUtils.shutdownSystem();
    }

    private void updateHardwareUI(HardwareMonitorService.HardwareStats stats) {
        if (dashboardPanelCoordinator != null) {
            dashboardPanelCoordinator.updateHardwareUI(stats);
        }
    }

    public static void main(String[] args) {
        FlatLaf.setUseNativeWindowDecorations(true);
        appContext = new AnnotationConfigApplicationContext(AppConfig.class);
        appContext.getBean(Main.class).launch(args);
    }

    @Configuration
    @EnableWebFlux
    @ComponentScan(basePackages = {"de.tki.comfyuicompanion"})
    public static class AppConfig {
        @org.springframework.context.annotation.Bean
        public org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder() {
            return org.springframework.web.reactive.function.client.WebClient.builder();
        }
    }
}
