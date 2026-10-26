package de.tki.comfyuicompanion.ui;

import com.formdev.flatlaf.FlatLaf;
import de.tki.comfyuicompanion.Main;
import de.tki.comfyuicompanion.controller.DownloadManagerController;
import de.tki.comfyuicompanion.controller.PromptLabController;
import de.tki.comfyuicompanion.controller.PromptLabExecutionHandler;
import de.tki.comfyuicompanion.domain.LaunchProfile;
import de.tki.comfyuicompanion.service.*;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.ProfileManager;
import de.tki.comfyuicompanion.service.impl.UpdaterService;
import de.tki.comfyuicompanion.service.impl.ModelHashRegistry;
import de.tki.comfyuicompanion.service.impl.CivitaiService;
import de.tki.comfyuicompanion.service.impl.ComfyProcessController;
import de.tki.comfyuicompanion.service.impl.LocalAIService;
import de.tki.comfyuicompanion.service.impl.ComfyApiClient;
import de.tki.comfyuicompanion.ui.civitai.CivitaiGalleryTab;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
import de.tki.comfyuicompanion.ui.panel.MainDashboardPanel;
import de.tki.comfyuicompanion.ui.panel.MainSettingsPanel;
import de.tki.comfyuicompanion.util.BackgroundExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Responsible for constructing the main JFrame UI, including tabs, menu bars,
 * status bars, and themes.
 */
public class MainWindowBuilder {
    private static final Logger logger = LoggerFactory.getLogger(MainWindowBuilder.class);

    private final Main main;
    private final ConfigService configService;
    private final ProfileManager profileManager;
    private final IComfyLifecycleService lifecycleService;
    private final UpdaterService updaterService;
    private final ModelHashRegistry hashRegistry;
    private final IModelValidator modelValidator;
    private final CivitaiService civitaiService;
    private final IDownloadManager downloadManager;
    private final IModelArchitectureService modelArchitectureService;
    private final ComfyProcessController processController;
    private final BackgroundExecutor backgroundExecutor;
    private final LocalAIService localAIService;
    private final ComfyApiClient comfyApiClient;
    
    // Controllers and Views
    private final BlueprintGalleryTab blueprintGalleryTab;
    private final CivitaiGalleryTab civitaiGalleryTab;
    private final VideoArchitectTab videoArchitectTab;
    private final PromptLabView promptLabView;
    private final PromptLabController promptLabController;
    private final DownloadManagerView downloadManagerView;
    private final DownloadManagerController downloadManagerController;

    public MainWindowBuilder(Main main, ConfigService configService, ProfileManager profileManager,
                             IComfyLifecycleService lifecycleService, UpdaterService updaterService,
                             ModelHashRegistry hashRegistry, IModelValidator modelValidator,
                             CivitaiService civitaiService, IDownloadManager downloadManager,
                             IModelArchitectureService modelArchitectureService, ComfyProcessController processController,
                             BackgroundExecutor backgroundExecutor, LocalAIService localAIService,
                             ComfyApiClient comfyApiClient, BlueprintGalleryTab blueprintGalleryTab,
                             VideoArchitectTab videoArchitectTab, PromptLabView promptLabView,
                             PromptLabController promptLabController, DownloadManagerView downloadManagerView,
                             DownloadManagerController downloadManagerController) {
        this(main, configService, profileManager, lifecycleService, updaterService, hashRegistry, modelValidator,
             civitaiService, downloadManager, modelArchitectureService, processController, backgroundExecutor,
             localAIService, comfyApiClient, blueprintGalleryTab, null, videoArchitectTab, promptLabView,
             promptLabController, downloadManagerView, downloadManagerController);
    }

    public MainWindowBuilder(Main main, ConfigService configService, ProfileManager profileManager,
                             IComfyLifecycleService lifecycleService, UpdaterService updaterService,
                             ModelHashRegistry hashRegistry, IModelValidator modelValidator,
                             CivitaiService civitaiService, IDownloadManager downloadManager,
                             IModelArchitectureService modelArchitectureService, ComfyProcessController processController,
                             BackgroundExecutor backgroundExecutor, LocalAIService localAIService,
                             ComfyApiClient comfyApiClient, BlueprintGalleryTab blueprintGalleryTab,
                             CivitaiGalleryTab civitaiGalleryTab,
                             VideoArchitectTab videoArchitectTab, PromptLabView promptLabView,
                             PromptLabController promptLabController, DownloadManagerView downloadManagerView,
                             DownloadManagerController downloadManagerController) {
        this.main = main;
        this.configService = configService;
        this.profileManager = profileManager;
        this.lifecycleService = lifecycleService;
        this.updaterService = updaterService;
        this.hashRegistry = hashRegistry;
        this.modelValidator = modelValidator;
        this.civitaiService = civitaiService;
        this.downloadManager = downloadManager;
        this.modelArchitectureService = modelArchitectureService;
        this.processController = processController;
        this.backgroundExecutor = backgroundExecutor;
        this.localAIService = localAIService;
        this.comfyApiClient = comfyApiClient;
        this.blueprintGalleryTab = blueprintGalleryTab;
        this.civitaiGalleryTab = civitaiGalleryTab;
        this.videoArchitectTab = videoArchitectTab;
        this.promptLabView = promptLabView;
        this.promptLabController = promptLabController;
        this.downloadManagerView = downloadManagerView;
        this.downloadManagerController = downloadManagerController;
    }

    /**
     * Builds and configures the main UI components.
     * 
     * @param components the mutable components container to populate
     * @param startComfyHandler callback to start ComfyUI
     * @param fullServiceRestartHandler callback for full restart
     * @param installDialogHandler callback to show installation dialog
     * @param pathsDialogHandler callback to show paths dialog
     * @param verifyInstallHandler callback to verify installation
     * @param repairEnvHandler callback to repair environment
     * @param wslFixHandler callback to fix WSL dependencies
     * @param dlSettingsHandler callback for download settings
     * @param videoArchitectHandler callback for video architect setup
     * @param apiKeysHandler callback for API keys dialog
     * @param helpDialogHandler callback for help dialog
     * @param resetSettingsHandler callback for settings reset
     */
    public void buildUI(MainWindowComponents components,
                        Consumer<LaunchProfile> startComfyHandler,
                        Runnable fullServiceRestartHandler,
                        Runnable installDialogHandler,
                        Runnable pathsDialogHandler,
                        Runnable verifyInstallHandler,
                        Runnable repairEnvHandler,
                        Runnable wslFixHandler,
                        Runnable dlSettingsHandler,
                        Runnable videoArchitectHandler,
                        Runnable apiKeysHandler,
                        Runnable helpDialogHandler,
                        Runnable resetSettingsHandler) {
        
        main.setTitle("Companion for ComfyUI");
        main.setSize(1450, 950);
        main.setMinimumSize(new Dimension(1200, 800));
        main.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        main.getRootPane().putClientProperty("flatlaf.useWindowDecorations", true);

        JPanel rootPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                paintWindowBackground(g, getWidth(), getHeight());
                super.paintComponent(g);
            }
        };
        rootPanel.setOpaque(true);

        components.headerBar = new MainHeaderBar(main, configService, modelArchitectureService,
                processController, lifecycleService, profileManager, startComfyHandler);

        components.mainTabs = new JTabbedPane();

        // Dashboard
        components.dashboardPanelCoordinator = new MainDashboardPanel(main, configService, profileManager,
                lifecycleService, updaterService, hashRegistry, modelValidator, civitaiService, downloadManager);
        components.dashboardPanelCoordinator.setStartComfyHandler(startComfyHandler);
        components.dashboardPanelCoordinator.setFullServiceRestartHandler(fullServiceRestartHandler);
        components.dashboardPanelCoordinator.setOpenBootstrapHandler(installDialogHandler);
        components.dashboardPanel = components.dashboardPanelCoordinator.createDashboardPanel(components.mainTabs);

        // Download Manager
        components.downloadManagerPanel = createManagerPanel(components, components.mainTabs);

        // Gallery
        components.galleryPanel = new OutputGalleryPanel(configService);

        // Blueprint Gallery
        components.blueprintGalleryWrapper = new JPanel(new BorderLayout());
        components.blueprintGalleryWrapper.setName("blueprintGalleryWrapper");
        components.blueprintGalleryWrapper.setOpaque(true);
        if (blueprintGalleryTab != null) {
            components.blueprintGalleryWrapper.add(blueprintGalleryTab, BorderLayout.CENTER);
            blueprintGalleryTab.setOnDataLoadedCallback(main::refreshPromptLabModels);
            blueprintGalleryTab.setOnWorkflowImportRequested(main::importWorkflow);
            blueprintGalleryTab.setOnSwitchToDownloadManager(main::focusDownloadTabAndSelectModels);
            if (civitaiGalleryTab != null) {
                blueprintGalleryTab.setCivitaiGalleryTab(civitaiGalleryTab);
            }
        } else {
            components.blueprintGalleryWrapper.add(new JPanel(), BorderLayout.CENTER);
        }

        // Civitai Model Hub (embedded inside BlueprintGalleryTab)
        components.civitaiHubWrapper = new JPanel(new BorderLayout());
        components.civitaiHubWrapper.setName("civitaiHubWrapper");
        components.civitaiHubWrapper.setOpaque(true);
        if (civitaiGalleryTab != null) {
            civitaiGalleryTab.setOnSwitchToDownloadManager(main::focusDownloadTabAndSelectModels);
        }

        // Prompt Lab
        components.promptLabExecutionHandler = new PromptLabExecutionHandler(configService, localAIService,
                lifecycleService, comfyApiClient, profileManager, promptLabController,
                backgroundExecutor != null ? backgroundExecutor.getExecutor() : null,
                startComfyHandler,
                img -> { if (promptLabView != null) promptLabView.scaleAndSetImage(img); },
                main::updateAiModelDisplay);
        components.promptLabExecutionHandler.setOnOpenPathsConfig(pathsDialogHandler);
        components.promptLabExecutionHandler.setOnDownloadMissingModels(main::loadWorkflowExternal);
        components.promptLabPanel = createPromptLabPanel(components);

        // Video Architect
        components.videoArchitectWrapper = new JPanel(new BorderLayout());
        components.videoArchitectWrapper.setName("videoArchitectWrapper");
        components.videoArchitectWrapper.setOpaque(true);
        if (videoArchitectTab != null) {
            components.videoArchitectWrapper.add(videoArchitectTab, BorderLayout.CENTER);
        } else {
            JPanel fallback = new JPanel(new GridBagLayout());
            fallback.setOpaque(false);
            JLabel label = new JLabel("Video Architect (Beta) is disabled or initializing...", SvgIconFactory.get(AppIcon.VIDEO_ARCHITECT, 20), SwingConstants.CENTER);
            label.setFont(new Font("SansSerif", Font.BOLD, 14));
            label.setForeground(configService != null && configService.isDarkMode() ? Color.LIGHT_GRAY : Color.DARK_GRAY);
            fallback.add(label);
            components.videoArchitectWrapper.add(fallback, BorderLayout.CENTER);
        }

        // Settings
        components.settingsPanelCoordinator = new MainSettingsPanel(main, configService, modelArchitectureService);
        components.settingsPanelCoordinator.setOnTabVisibilityChanged(main::updateTabVisibility);
        components.settingsPanelCoordinator.setOnThemeChanged(main::setupTheme);
        components.settingsPanel = components.settingsPanelCoordinator.createSettingsPanel(
                pathsDialogHandler, verifyInstallHandler, repairEnvHandler, wslFixHandler,
                dlSettingsHandler, installDialogHandler, videoArchitectHandler, apiKeysHandler,
                helpDialogHandler, resetSettingsHandler);

        main.updateTabVisibility();

        rootPanel.add(components.headerBar, BorderLayout.NORTH);
        rootPanel.add(components.mainTabs, BorderLayout.CENTER);
        main.setContentPane(rootPanel);
    }

    private JPanel createPromptLabPanel(MainWindowComponents components) {
        if (promptLabView != null && promptLabController != null) {
            promptLabController.setView(promptLabView);
            promptLabController.setBlueprintGalleryTab(blueprintGalleryTab);
            promptLabController.setSendPromptHandler(components.promptLabExecutionHandler::sendPromptToComfyUI);
            promptLabController.setSuggestSubjectHandler(components.promptLabExecutionHandler::suggestSubjectCompletions);
            components.promptLabExecutionHandler.setPromptLabView(promptLabView);
            promptLabController.loadPromptLabSession();
            promptLabController.refreshPromptLabModels();
            promptLabController.updatePromptLabJson();
            return promptLabView;
        }
        return new JPanel();
    }

    private JPanel createManagerPanel(MainWindowComponents components, JTabbedPane tabs) {
        if (downloadManagerView != null && downloadManagerController != null) {
            downloadManagerController.setView(downloadManagerView);
            components.tableModel = downloadManagerView.getTableModel();
            components.jsonInputArea = downloadManagerView.getJsonInputArea();
            components.statusLabel = downloadManagerView.getStatusLabel();
            components.downloadButton = downloadManagerView.getDownloadButton();
            components.pauseButton = downloadManagerView.getPauseButton();
            components.stopButton = downloadManagerView.getStopButton();
            components.activeAiModelLabel = downloadManagerView.getActiveAiModelLabel();
            return downloadManagerView;
        }
        return new JPanel();
    }

    public static void setupTheme(boolean darkMode, MainWindowComponents components, Main main,
                                  VideoArchitectTab videoArchitectTab, BlueprintGalleryTab blueprintGalleryTab,
                                  DownloadManagerView downloadManagerView, PromptLabView promptLabView) {
        setupTheme(darkMode, components, main, videoArchitectTab, blueprintGalleryTab, null, downloadManagerView, promptLabView);
    }

    public static void setupTheme(boolean darkMode, MainWindowComponents components, Main main,
                                  VideoArchitectTab videoArchitectTab, BlueprintGalleryTab blueprintGalleryTab,
                                  CivitaiGalleryTab civitaiGalleryTab,
                                  DownloadManagerView downloadManagerView, PromptLabView promptLabView) {
        try {
            ThemeManager.applyTheme(darkMode);

            if (components.dashboardPanelCoordinator != null && components.dashboardPanelCoordinator.getComfyConsoleArea() != null) {
                JTextArea area = components.dashboardPanelCoordinator.getComfyConsoleArea();
                if (darkMode) {
                    area.setBackground(UIManager.getColor("TextArea.background"));
                    area.setForeground(UIManager.getColor("TextArea.foreground"));
                } else {
                    area.setBackground(new Color(245, 247, 250));
                    area.setForeground(new Color(30, 30, 30));
                }
            }

            if (components.dashboardPanel != null) SwingUtilities.updateComponentTreeUI(components.dashboardPanel);
            if (videoArchitectTab != null) {
                SwingUtilities.updateComponentTreeUI(videoArchitectTab);
                videoArchitectTab.updateTheme(darkMode);
            }
            if (blueprintGalleryTab != null) {
                SwingUtilities.updateComponentTreeUI(blueprintGalleryTab);
                blueprintGalleryTab.updateTheme(darkMode);
            }
            if (civitaiGalleryTab != null) {
                SwingUtilities.updateComponentTreeUI(civitaiGalleryTab);
                civitaiGalleryTab.updateTheme(darkMode);
            }
            if (downloadManagerView != null) SwingUtilities.updateComponentTreeUI(downloadManagerView);
            if (components.downloadManagerPanel != null) SwingUtilities.updateComponentTreeUI(components.downloadManagerPanel);
            if (promptLabView != null) {
                SwingUtilities.updateComponentTreeUI(promptLabView);
                promptLabView.updateTheme(darkMode);
            }
            if (components.galleryPanel != null) SwingUtilities.updateComponentTreeUI(components.galleryPanel);
            if (components.settingsPanel != null) SwingUtilities.updateComponentTreeUI(components.settingsPanel);

            FlatLaf.updateUI();
            SwingUtilities.updateComponentTreeUI(main);
        } catch (Exception e) {
            logger.error("Theme setup failed: " + e.getMessage());
        }
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

    public static class MainWindowComponents {
        public MainHeaderBar headerBar;
        public JTabbedPane mainTabs;
        public MainDashboardPanel dashboardPanelCoordinator;
        public JPanel dashboardPanel;
        public JPanel downloadManagerPanel;
        public OutputGalleryPanel galleryPanel;
        public JPanel blueprintGalleryWrapper;
        public JPanel civitaiHubWrapper;
        public PromptLabExecutionHandler promptLabExecutionHandler;
        public JPanel promptLabPanel;
        public JPanel videoArchitectWrapper;
        public MainSettingsPanel settingsPanelCoordinator;
        public JPanel settingsPanel;
        public javax.swing.table.DefaultTableModel tableModel;
        public JTextArea jsonInputArea;
        public JLabel statusLabel;
        public JButton downloadButton;
        public JButton pauseButton;
        public JButton stopButton;
        public JLabel activeAiModelLabel;
    }
}
