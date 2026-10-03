package de.tki.comfyuicompanion;

import com.formdev.flatlaf.FlatLaf;
import de.tki.comfyuicompanion.config.AppConfig;
import de.tki.comfyuicompanion.controller.DownloadManagerController;
import de.tki.comfyuicompanion.controller.PromptLabController;
import de.tki.comfyuicompanion.controller.PromptLabModelResolver;
import de.tki.comfyuicompanion.domain.LaunchProfile;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.*;
import de.tki.comfyuicompanion.service.impl.*;
import de.tki.comfyuicompanion.ui.*;
import de.tki.comfyuicompanion.ui.civitai.CivitaiGalleryTab;
import de.tki.comfyuicompanion.ui.dialog.MainDialogCoordinator;
import de.tki.comfyuicompanion.util.BackgroundExecutor;
import de.tki.comfyuicompanion.util.PlatformUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
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
    private CivitaiGalleryTab civitaiGalleryTab;
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
    private ComfyInstallationScanner comfyInstallationScanner;

    private MainWindowBuilder.MainWindowComponents uiComponents;
    private MainWindowLifecycleHandler lifecycleHandler;
    private ComfyBridgeManager comfyBridgeManager;
    private MainDialogCoordinator dialogCoordinator;

    private Image appIcon;
    private String currentFileName = "input.json";

    // Legacy test/reflection access fields
    JLabel statusLabel;
    JTextArea jsonInputArea;
    JButton downloadButton;
    JButton pauseButton;
    JButton stopButton;
    DefaultTableModel tableModel;
    JLabel activeAiModelLabel;
    boolean isDownloading;

    final Set<String> comfyCheckpoints = ConcurrentHashMap.newKeySet();
    final Set<String> comfyUnetModels = ConcurrentHashMap.newKeySet();
    final Set<String> comfyClips = ConcurrentHashMap.newKeySet();
    final Set<String> comfyVaes = ConcurrentHashMap.newKeySet();
    final Set<String> comfyClipTypes = ConcurrentHashMap.newKeySet();
    final Set<String> comfyUnetWeightDtypes = ConcurrentHashMap.newKeySet();

    /**
     * Backward-compatible alias for the external AppConfig.
     */
    public static class AppConfig extends de.tki.comfyuicompanion.config.AppConfig {}

    /**
     * Legacy 14-parameter constructor for tests and minimalist instantiation.
     */
    public Main(IModelAnalyzer analyzer, IDownloadManager downloadManager, IWorkflowService workflowService,
                IModelSearchService searchService, IModelValidator modelValidator, RestBridgeService restBridge,
                ArchiveService archiveService, IComfyLifecycleService lifecycleService, ComfyDiagnosticService diagnosticService,
                ProfileManager profileManager, EnvironmentBootstrapperImpl bootstrapper, ComfyProcessController processController,
                CivitaiService civitaiService, HuggingFaceService huggingFaceService) {
        this(analyzer, downloadManager, workflowService, searchService, modelValidator, restBridge,
             archiveService, lifecycleService, diagnosticService, profileManager, bootstrapper, processController,
             civitaiService, huggingFaceService,
             null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

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
                @Autowired(required = false) CivitaiGalleryTab civitaiGalleryTab,
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
        
        this.analyzer = analyzer;
        this.downloadManager = downloadManager;
        this.workflowService = workflowService;
        this.searchService = searchService;
        this.modelValidator = modelValidator;
        this.restBridge = restBridge;
        this.archiveService = archiveService;
        this.lifecycleService = lifecycleService;
        this.diagnosticService = diagnosticService;
        this.profileManager = profileManager;
        this.bootstrapper = bootstrapper;
        this.processController = processController;
        this.civitaiService = civitaiService;
        this.huggingFaceService = huggingFaceService;
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
        this.civitaiGalleryTab = civitaiGalleryTab;
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
        this.comfyInstallationScanner = comfyInstallationScanner != null ? comfyInstallationScanner : 
            new ComfyInstallationScanner(configService, bootstrapper, profileManager);

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
    }

    public void launch(String[] args) {
        if (defaultCacheBootstrapper != null) defaultCacheBootstrapper.bootstrapDefaultCache();

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

        if (configService != null && configService.isUnlocked()) restBridge.setApiToken(configService.getApiToken());
        restBridge.startServer();

        lifecycleHandler = new MainWindowLifecycleHandler(this, configService, downloadManager, lifecycleService, 
            processController, appLifecycleManager, appContext, null);
        lifecycleHandler.registerShutdownHooks();

        boolean unlocked = appLifecycleManager != null ? appLifecycleManager.unlockDefaultVault() : promptForPassword();
        if (!unlocked) System.exit(0);

        setupTheme(configService != null && configService.isDarkMode());

        SwingUtilities.invokeLater(() -> {
            try {
                initUI();
                setupTheme(configService != null && configService.isDarkMode());

                if (lifecycleHandler != null) {
                    lifecycleHandler.setupTrayIcon(appIcon);
                    lifecycleHandler.setupWindowListener();
                }
                
                if (uiComponents.settingsPanelCoordinator != null) {
                    uiComponents.settingsPanelCoordinator.loadSettingsIntoUI();
                }
                updateAiModelDisplay();

                if (hardwareMonitorService != null) {
                    hardwareMonitorService.start(stats -> SwingUtilities.invokeLater(() -> {
                        if (uiComponents.dashboardPanelCoordinator != null) {
                            uiComponents.dashboardPanelCoordinator.updateHardwareUI(stats);
                        }
                    }));
                }

                setVisible(true);
                if (blueprintGalleryTab != null) blueprintGalleryTab.refreshAllData();
                scanAndVerifyComfyUIInstallation(false);
            } catch (Exception e) {
                logger.error("Critical UI Error", e);
                JOptionPane.showMessageDialog(null, "Critical UI Error: " + e.getMessage());
            }
        });
    }

    /**
     * Initializes the user interface components using MainWindowBuilder.
     */
    private void initUI() {
        loadIcon();
        uiComponents = new MainWindowBuilder.MainWindowComponents();
        MainWindowBuilder builder = new MainWindowBuilder(this, configService, profileManager, lifecycleService,
                updaterService, hashRegistry, modelValidator, civitaiService, downloadManager,
                modelArchitectureService, processController, backgroundExecutor, localAIService,
                comfyApiClient, blueprintGalleryTab, civitaiGalleryTab, videoArchitectTab, promptLabView,
                promptLabController, downloadManagerView, downloadManagerController);
        
        builder.buildUI(uiComponents, 
            p -> startComfyUI(p, false, configService != null && !configService.isHideComfyUI()),
            this::performFullServiceRestart,
            this::showInstallationDialog,
            this::showPathsDialog,
            () -> scanAndVerifyComfyUIInstallation(true),
            this::triggerEnvironmentRepair,
            this::triggerWslDependencyFix,
            this::showDownloadSettingsDialog,
            this::showVideoArchitectAutoconfigDialog,
            this::showApiKeysDialog,
            this::showHelpDialog,
            this::resetSettings);

        this.statusLabel = uiComponents.statusLabel;
        this.jsonInputArea = uiComponents.jsonInputArea;
        this.downloadButton = uiComponents.downloadButton;
        this.pauseButton = uiComponents.pauseButton;
        this.stopButton = uiComponents.stopButton;
        this.tableModel = uiComponents.tableModel;
        this.activeAiModelLabel = uiComponents.activeAiModelLabel;
    }

    public void setupTheme(boolean darkMode) {
        if (uiComponents != null) {
            MainWindowBuilder.setupTheme(darkMode, uiComponents, this, videoArchitectTab, blueprintGalleryTab, civitaiGalleryTab, downloadManagerView, promptLabView);
        } else {
            try {
                ThemeManager.applyTheme(darkMode);
                FlatLaf.updateUI();
                SwingUtilities.updateComponentTreeUI(this);
            } catch (Exception e) {
                logger.error("Theme setup failed: " + e.getMessage());
            }
        }
    }

    private void loadIcon() {
        try (InputStream is = getClass().getResourceAsStream("/icons/app_icon.jpg")) {
            if (is != null) {
                appIcon = ImageIO.read(is);
                setIconImage(appIcon);
            }
        } catch (IOException e) {
            logger.error("Could not load app icon: " + e.getMessage());
        }
    }

    private boolean promptForPassword() {
        if (configService == null) return false;
        setupTheme(configService.isDarkMode());
        String defaultPass = System.getProperty("user.name", "default") + "@" + getHostName();
        try {
            configService.unlock(defaultPass);
            if (configService.isVaultFresh() && modelListService != null) {
                modelListService.importFromUrl("https://raw.githubusercontent.com/Comfy-Org/ComfyUI-Manager/main/model-list.json");
            }
            return true;
        } catch (Exception e) {
            try {
                configService.resetVault();
                configService.unlock(defaultPass);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }

    private String getHostName() {
        try { return java.net.InetAddress.getLocalHost().getHostName(); }
        catch (Exception e) { return "localhost"; }
    }

    public void updateTabVisibility() {
        if (uiComponents == null || uiComponents.mainTabs == null || configService == null) return;
        java.awt.Component selectedComp = uiComponents.mainTabs.getSelectedComponent();
        uiComponents.mainTabs.removeAll();

        if (uiComponents.dashboardPanel != null)
            uiComponents.mainTabs.addTab("Dashboard", de.tki.comfyuicompanion.ui.icons.SvgIconFactory.get(de.tki.comfyuicompanion.ui.icons.AppIcon.DASHBOARD, 16), uiComponents.dashboardPanel);
        if (uiComponents.downloadManagerPanel != null)
            uiComponents.mainTabs.addTab("Download Manager", de.tki.comfyuicompanion.ui.icons.SvgIconFactory.get(de.tki.comfyuicompanion.ui.icons.AppIcon.DOWNLOAD_MANAGER, 16), uiComponents.downloadManagerPanel);
        if (uiComponents.galleryPanel != null)
            uiComponents.mainTabs.addTab("Gallery", de.tki.comfyuicompanion.ui.icons.SvgIconFactory.get(de.tki.comfyuicompanion.ui.icons.AppIcon.GALLERY, 16), uiComponents.galleryPanel);
        if (configService.isBlueprintGalleryEnabled() && uiComponents.blueprintGalleryWrapper != null)
            uiComponents.mainTabs.addTab("Blueprint Gallery", de.tki.comfyuicompanion.ui.icons.SvgIconFactory.get(de.tki.comfyuicompanion.ui.icons.AppIcon.BLUEPRINT_GALLERY, 16), uiComponents.blueprintGalleryWrapper);
        if (configService.isCivitaiGalleryEnabled() && uiComponents.civitaiHubWrapper != null)
            uiComponents.mainTabs.addTab("Model Hub", de.tki.comfyuicompanion.ui.icons.SvgIconFactory.get(de.tki.comfyuicompanion.ui.icons.AppIcon.MODEL_HUB, 16), uiComponents.civitaiHubWrapper);
        if (configService.isPromptLabEnabled() && uiComponents.promptLabPanel != null)
            uiComponents.mainTabs.addTab("Image Lab", de.tki.comfyuicompanion.ui.icons.SvgIconFactory.get(de.tki.comfyuicompanion.ui.icons.AppIcon.IMAGE_LAB, 16), uiComponents.promptLabPanel);
        if (configService.isVideoArchitectEnabled() && uiComponents.videoArchitectWrapper != null)
            uiComponents.mainTabs.addTab("Video Architect (Beta)", de.tki.comfyuicompanion.ui.icons.SvgIconFactory.get(de.tki.comfyuicompanion.ui.icons.AppIcon.VIDEO_ARCHITECT, 16), uiComponents.videoArchitectWrapper);
        if (uiComponents.settingsPanel != null)
            uiComponents.mainTabs.addTab("Settings", de.tki.comfyuicompanion.ui.icons.SvgIconFactory.get(de.tki.comfyuicompanion.ui.icons.AppIcon.SETTINGS, 16), uiComponents.settingsPanel);

        if (selectedComp != null) {
            int index = uiComponents.mainTabs.indexOfComponent(selectedComp);
            uiComponents.mainTabs.setSelectedIndex(index != -1 ? index : 0);
        }
    }

    public void updateAiModelDisplay() {
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
                if (uiComponents != null) {
                    if (uiComponents.activeAiModelLabel != null) uiComponents.activeAiModelLabel.setText(activeText);
                }
                if (downloadManagerView != null && downloadManagerView.getActiveAiModelLabel() != null) {
                    downloadManagerView.getActiveAiModelLabel().setText(activeText);
                }
            });
        });
    }

    public void startComfyUI(LaunchProfile selected, boolean clearConsole, boolean openBrowser) {
        if (selected == null) return;
        if (clearConsole && uiComponents != null && uiComponents.dashboardPanelCoordinator != null && uiComponents.dashboardPanelCoordinator.getComfyConsoleArea() != null) {
            uiComponents.dashboardPanelCoordinator.getComfyConsoleArea().setText("");
        }
        AtomicBoolean cudaError = new AtomicBoolean(false);
        processController.start(selected, Paths.get(configService.getComfyUIPath()), configService.getPythonPath(), log -> {
            if (log.contains("Torch not compiled with CUDA enabled") || log.contains("AssertionError: Torch not compiled with CUDA enabled")) {
                if (!cudaError.getAndSet(true)) {
                    SwingUtilities.invokeLater(() -> {
                        int choice = JOptionPane.showConfirmDialog(this,
                                "ComfyUI / PyTorch reported a CUDA error:\n'Torch not compiled with CUDA enabled'\n\nWould you like to automatically reinstall PyTorch with CUDA support now?",
                                "CUDA PyTorch Error Detected", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);
                        if (choice == JOptionPane.YES_OPTION) triggerEnvironmentRepair();
                        else if (choice == JOptionPane.NO_OPTION) switchToCpuModeProfile();
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
                        } catch (Exception e) {}
                    });
                }
            }
            appendToConsole(log);
        }).thenAccept(exitCode -> {
            if (exitCode != 0 && cudaError.get()) {
                SwingUtilities.invokeLater(() -> {
                    int choice = JOptionPane.showConfirmDialog(this,
                            "ComfyUI failed to start because CUDA is not enabled/supported on this PyTorch installation.\nWould you like to switch to 'CPU Mode' profile and start ComfyUI on your CPU?",
                            "CUDA Error Detected", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);
                    if (choice == JOptionPane.YES_OPTION) switchToCpuModeProfile();
                });
            }
        });
    }

    private void switchToCpuModeProfile() {
        List<LaunchProfile> profiles = profileManager.loadProfiles();
        LaunchProfile cpuProfile = profiles.stream().filter(p -> p.id().equals("cpu_mode")).findFirst().orElse(null);
        if (cpuProfile != null) {
            if (uiComponents != null && uiComponents.dashboardPanelCoordinator != null && uiComponents.dashboardPanelCoordinator.getProfileList() != null) {
                uiComponents.dashboardPanelCoordinator.getProfileList().setSelectedValue(cpuProfile, true);
            }
            startComfyUI(cpuProfile, true, configService == null || !configService.isHideComfyUI());
        }
    }

    public void performFullServiceRestart() {
        backgroundExecutor.execute(() -> {
            processController.stop();
            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
            SwingUtilities.invokeLater(() -> {
                if (uiComponents != null && uiComponents.mainTabs != null) uiComponents.mainTabs.setSelectedIndex(0);
                LaunchProfile activeProfile = null;
                if (uiComponents != null && uiComponents.dashboardPanelCoordinator != null && uiComponents.dashboardPanelCoordinator.getProfileList() != null) {
                    activeProfile = uiComponents.dashboardPanelCoordinator.getProfileList().getSelectedValue();
                }
                if (activeProfile == null && configService != null) {
                    String activeId = configService.getActiveProfile();
                    List<LaunchProfile> profiles = profileManager.loadProfiles();
                    activeProfile = profiles.stream().filter(p -> p.id().equals(activeId)).findFirst().orElse(null);
                    if (activeProfile == null && !profiles.isEmpty()) activeProfile = profiles.get(0);
                }
                if (activeProfile != null) {
                    if (uiComponents != null && uiComponents.dashboardPanelCoordinator != null) {
                        if (uiComponents.dashboardPanelCoordinator.getProfileList() != null) uiComponents.dashboardPanelCoordinator.getProfileList().setSelectedValue(activeProfile, true);
                        if (uiComponents.dashboardPanelCoordinator.getComfyConsoleArea() != null) uiComponents.dashboardPanelCoordinator.getComfyConsoleArea().append("\n🔄 Restarting ComfyUI...\n");
                    }
                    startComfyUI(activeProfile, false, configService == null || !configService.isHideComfyUI());
                }
            });
        });
    }

    private void appendToConsole(String log) {
        SwingUtilities.invokeLater(() -> {
            if (uiComponents != null && uiComponents.dashboardPanelCoordinator != null && uiComponents.dashboardPanelCoordinator.getComfyConsoleArea() != null) {
                JTextArea area = uiComponents.dashboardPanelCoordinator.getComfyConsoleArea();
                area.append(log.endsWith("\n") ? log : log + "\n");
                area.setCaretPosition(area.getDocument().getLength());
            }
        });
    }

    public boolean scanAndVerifyComfyUIInstallation(boolean showDialogIfFound) {
        return comfyInstallationScanner.scanAndVerifyInstallation(this, showDialogIfFound, () -> {
            syncBridgeFiles();
            if (uiComponents != null && uiComponents.dashboardPanelCoordinator != null) {
                uiComponents.dashboardPanelCoordinator.refreshVersions(versionService, configService);
            }
        });
    }

    public void syncBridgeFiles() {
        if (comfyBridgeManager == null) comfyBridgeManager = new ComfyBridgeManager(configService, pathResolver);
        comfyBridgeManager.syncBridgeFiles();
    }

    private MainDialogCoordinator getDialogCoordinator() {
        if (dialogCoordinator == null) {
            dialogCoordinator = new MainDialogCoordinator(this, configService, lifecycleService, dependencyService, backgroundExecutor != null ? backgroundExecutor.getExecutor() : null);
        }
        return dialogCoordinator;
    }

    private void showInstallationDialog() { getDialogCoordinator().showInstallationDialog(this::syncBridgeFiles, dlg -> {
        if (comfyBridgeManager == null) comfyBridgeManager = new ComfyBridgeManager(configService, pathResolver);
        comfyBridgeManager.installComfyUIBridge(configService != null ? configService.getComfyUIPath() : "", dlg);
    }); }
    private void showPathsDialog() { getDialogCoordinator().showPathsDialog(() -> {
        if (uiComponents != null && uiComponents.dashboardPanelCoordinator != null) uiComponents.dashboardPanelCoordinator.refreshVersions(versionService, configService);
    }, this::syncBridgeFiles); }
    private void showDownloadSettingsDialog() { getDialogCoordinator().showDownloadSettingsDialog(); }
    private void showVideoArchitectAutoconfigDialog() { getDialogCoordinator().showVideoArchitectAutoconfigDialog(); }
    private void showApiKeysDialog() { getDialogCoordinator().showApiKeysDialog(this::updateAiModelDisplay); }
    private void showHelpDialog() { getDialogCoordinator().showHelpDialog(); }
    private void resetSettings() {
        getDialogCoordinator().resetSettings(() -> {
            if (uiComponents != null && uiComponents.settingsPanelCoordinator != null) uiComponents.settingsPanelCoordinator.loadSettingsIntoUI();
            updateTabVisibility();
            setupTheme(configService != null && configService.isDarkMode());
        });
    }

    public void importWorkflow(File file) {
        if (downloadManagerController != null && uiComponents != null) downloadManagerController.importWorkflow(file, uiComponents.mainTabs, uiComponents.downloadManagerPanel);
    }
    public void focusDownloadTabAndSelectModels(List<ModelInfo> missingModels) {
        if (uiComponents != null && uiComponents.mainTabs != null) uiComponents.mainTabs.setSelectedIndex(1);
        if (downloadManagerController != null) downloadManagerController.focusDownloadTabAndSelectModels(missingModels);
    }
    public String getWorkspaceWorkflowJson() {
        return (uiComponents != null && uiComponents.jsonInputArea != null) ? uiComponents.jsonInputArea.getText() : "";
    }
    public void loadWorkflowExternal(String name, String json) {
        SwingUtilities.invokeLater(() -> {
            if (uiComponents != null && uiComponents.jsonInputArea != null) {
                uiComponents.jsonInputArea.setText(json);
                currentFileName = name != null ? name + ".json" : "library_workflow.json";
                if (downloadManagerController != null) {
                    downloadManagerController.analyzeJsonContent();
                    downloadManagerController.searchMissingOnline(false);
                }
                if (uiComponents.mainTabs != null) uiComponents.mainTabs.setSelectedIndex(1);
            }
        });
    }

    public void refreshPromptLabModels() {
        if (promptLabController != null) promptLabController.refreshPromptLabModels();
    }

    public void triggerEnvironmentRepair() {
        if (uiComponents != null && uiComponents.mainTabs != null) uiComponents.mainTabs.setSelectedIndex(0);
        if (uiComponents != null && uiComponents.dashboardPanelCoordinator != null && uiComponents.dashboardPanelCoordinator.getComfyConsoleArea() != null) {
            uiComponents.dashboardPanelCoordinator.getComfyConsoleArea().setText("");
        }
        backgroundExecutor.execute(() -> updaterService.repairEnvironment(this::appendToConsole));
    }

    public void triggerWslDependencyFix() {
        if (uiComponents != null && uiComponents.mainTabs != null) uiComponents.mainTabs.setSelectedIndex(0);
        if (uiComponents != null && uiComponents.dashboardPanelCoordinator != null && uiComponents.dashboardPanelCoordinator.getComfyConsoleArea() != null) {
            uiComponents.dashboardPanelCoordinator.getComfyConsoleArea().setText("");
        }
        String comfyPathStr = configService != null ? configService.getComfyUIPath() : null;
        Path comfyDir = (comfyPathStr != null && !comfyPathStr.isEmpty()) ? Paths.get(comfyPathStr) : null;
        backgroundExecutor.execute(() -> bootstrapper.fixWslDependencies(comfyDir, this::appendToConsole));
    }

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
    private String resolveClipForModel(String selectedModel) { return createModelResolver().resolveClipForModel(selectedModel); }
    private String resolveClipType(String clipModel, String selectedModel) { return createModelResolver().resolveClipType(clipModel, selectedModel); }
    private String resolveVaeForModel(String modelName) { return createModelResolver().resolveVaeForModel(modelName); }
    private void analyzeJsonContent() { if (downloadManagerController != null) downloadManagerController.analyzeJsonContent(); }

    public static void main(String[] args) {
        FlatLaf.setUseNativeWindowDecorations(true);
        ThemeManager.applyTheme(true);
        appContext = new AnnotationConfigApplicationContext(AppConfig.class);
        appContext.getBean(Main.class).launch(args);
    }
}
