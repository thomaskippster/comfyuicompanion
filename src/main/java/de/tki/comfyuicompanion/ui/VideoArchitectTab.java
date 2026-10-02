package de.tki.comfyuicompanion.ui;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import de.tki.comfyuicompanion.service.IHardwareProfileService;
import de.tki.comfyuicompanion.service.IVideoPromptOptimizer;
import de.tki.comfyuicompanion.service.impl.ComfyPipelineService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.Gemma4Service;
import de.tki.comfyuicompanion.service.impl.LocalTTSService;
import de.tki.comfyuicompanion.service.impl.Video4jEditorService;
import de.tki.comfyuicompanion.service.impl.VideoPromptOptimizer;
import de.tki.comfyuicompanion.ui.video.VideoArchitectConfigPanel;
import de.tki.comfyuicompanion.ui.video.VideoArchitectExecutionHandler;
import de.tki.comfyuicompanion.ui.video.VideoArchitectPresetCoordinator;
import de.tki.comfyuicompanion.ui.video.VideoArchitectThemeHandler;
import de.tki.comfyuicompanion.ui.video.VideoArchitectTimelineCoordinator;
import de.tki.comfyuicompanion.ui.video.VideoArchitectWorkspacePanel;
import de.tki.comfyuicompanion.ui.video.VideoScriptParser;
import org.springframework.beans.factory.annotation.Autowired;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.util.List;

/**
 * Main coordinator panel for Video Architect.
 * Integrates {@link VideoArchitectConfigPanel} and {@link VideoArchitectWorkspacePanel}
 * in a responsive split pane, orchestrating execution pipelines, timeline management,
 * and theme synchronization.
 */
@org.springframework.stereotype.Component
public class VideoArchitectTab extends JPanel {

    private final ConfigService configService;
    private final VideoArchitectExecutionHandler executionHandler;
    private final VideoArchitectTimelineCoordinator timelineCoordinator = new VideoArchitectTimelineCoordinator();
    private final VideoArchitectPresetCoordinator presetCoordinator;
    private final VideoScriptParser scriptParser = new VideoScriptParser();
    private final IVideoPromptOptimizer promptOptimizer;

    private VideoArchitectConfigPanel configPanel;
    private VideoArchitectWorkspacePanel workspacePanel;

    // Component references maintained for reflection-based test compatibility
    private JTextField speakerImageField;
    private JSpinner videoWidthSpinner;
    private JSpinner videoHeightSpinner;

    /**
     * Constructs a new VideoArchitectTab with default core services.
     */
    public VideoArchitectTab(ConfigService configService, ComfyPipelineService comfyPipelineService,
                             Video4jEditorService video4jEditorService, Gemma4Service gemma4Service,
                             IComfyLifecycleService lifecycleService) {
        this(configService, comfyPipelineService, video4jEditorService, gemma4Service, lifecycleService, null, null, null);
    }

    /**
     * Constructs a new VideoArchitectTab with TTS support.
     */
    public VideoArchitectTab(ConfigService configService, ComfyPipelineService comfyPipelineService,
                             Video4jEditorService video4jEditorService, Gemma4Service gemma4Service,
                             IComfyLifecycleService lifecycleService, LocalTTSService ttsService) {
        this(configService, comfyPipelineService, video4jEditorService, gemma4Service, lifecycleService, ttsService, null, null);
    }

    /**
     * Constructs a new VideoArchitectTab with TTS and hardware profile support.
     */
    public VideoArchitectTab(ConfigService configService, ComfyPipelineService comfyPipelineService,
                             Video4jEditorService video4jEditorService, Gemma4Service gemma4Service,
                             IComfyLifecycleService lifecycleService, LocalTTSService ttsService,
                             IHardwareProfileService hardwareProfileService) {
        this(configService, comfyPipelineService, video4jEditorService, gemma4Service, lifecycleService, ttsService, hardwareProfileService, null);
    }

    /**
     * Constructs a new VideoArchitectTab with all dependencies fully injected.
     *
     * @param configService          the configuration service
     * @param comfyPipelineService   the ComfyUI pipeline integration service
     * @param video4jEditorService   the video editor backend
     * @param gemma4Service          the local LLM service for script parsing
     * @param lifecycleService       the ComfyUI lifecycle monitor
     * @param ttsService             the local TTS engine
     * @param hardwareProfileService the hardware monitor service
     * @param promptOptimizer        the video prompt optimizer
     */
    @Autowired
    public VideoArchitectTab(ConfigService configService, ComfyPipelineService comfyPipelineService,
                             Video4jEditorService video4jEditorService, Gemma4Service gemma4Service,
                             IComfyLifecycleService lifecycleService,
                             @Autowired(required = false) LocalTTSService ttsService,
                             @Autowired(required = false) IHardwareProfileService hardwareProfileService,
                             @Autowired(required = false) IVideoPromptOptimizer promptOptimizer) {
        this.configService = configService;
        this.promptOptimizer = promptOptimizer != null ? promptOptimizer : new VideoPromptOptimizer();
        this.presetCoordinator = new VideoArchitectPresetCoordinator(hardwareProfileService);
        this.executionHandler = new VideoArchitectExecutionHandler(
                configService, comfyPipelineService, video4jEditorService,
                ttsService, gemma4Service, lifecycleService, this.scriptParser, this.promptOptimizer);

        if (configService != null) {
            configService.setSpeakerImagePath("");
        }

        initUI(video4jEditorService);
        applyHardwarePreset(false);
        updateTheme(ThemeManager.isDarkMode());
        ThemeManager.registerObserver(() -> updateTheme(ThemeManager.isDarkMode()));
    }

    private void initUI(Video4jEditorService video4jEditorService) {
        setOpaque(false);
        setLayout(new BorderLayout(15, 15));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        configPanel = new VideoArchitectConfigPanel(configService, presetCoordinator, msg -> {
            if (workspacePanel != null) workspacePanel.logToConsole(msg);
        });
        workspacePanel = new VideoArchitectWorkspacePanel(timelineCoordinator, video4jEditorService, promptOptimizer);
        workspacePanel.setSpinnerSuppliers(configPanel::getVideoWidthSpinner, configPanel::getVideoHeightSpinner);

        configPanel.setOnSuggestSubject(this::handleDeconstructScript);
        configPanel.setOnAutoPilot(this::handleAutoPilot);
        workspacePanel.setOnGenerateScenes(this::handleVideoGeneration);
        workspacePanel.setOnMasterRender(this::handleMasterRender);

        this.speakerImageField = configPanel.getSpeakerImageField();
        this.videoWidthSpinner = configPanel.getVideoWidthSpinner();
        this.videoHeightSpinner = configPanel.getVideoHeightSpinner();

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, configPanel, workspacePanel);
        splitPane.setOpaque(false);
        splitPane.setDividerLocation(360);
        splitPane.setResizeWeight(0.3);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        add(splitPane, BorderLayout.CENTER);
    }

    /**
     * Applies the recommended hardware-specific preset for the current GPU capabilities.
     *
     * @param logMessage true to print a confirmation log to the UI console, false otherwise
     */
    public void applyHardwarePreset(boolean logMessage) {
        if (configPanel != null) {
            configPanel.applyHardwarePreset(logMessage);
        }
    }

    private void handleDeconstructScript() {
        executionHandler.deconstructScript(this, configPanel.getPromptSubject(), configPanel.getSelectedModel(),
                configPanel.getVideoWidth(), configPanel.getVideoHeight(), configPanel.getVideoCfg(),
                configPanel.getVideoSteps(), configPanel.getVideoMotion(),
                workspacePanel.getTimelineListModel(), workspacePanel.getTimelineListView(),
                workspacePanel.getVideoProgressBar(), this::setButtonsDisabled,
                workspacePanel::logToConsole, workspacePanel::updatePreviewForScene,
                workspacePanel::updateStoryboardJson);
    }

    private void handleAutoPilot() {
        executionHandler.executeAutoPilot(this, configPanel.getPromptSubject(), configPanel.getSelectedModel(),
                configPanel.getSpeakerImagePath(), configPanel.getVideoWidth(), configPanel.getVideoHeight(),
                configPanel.getVideoCfg(), configPanel.getVideoSteps(), configPanel.getVideoMotion(),
                workspacePanel.getTimelineListModel(), workspacePanel.getTimelineListView(),
                workspacePanel.getVideoProgressBar(), this::setButtonsDisabled,
                workspacePanel::logToConsole, workspacePanel::updatePreviewForScene,
                workspacePanel::updateStoryboardJson);
    }

    private void handleVideoGeneration() {
        executionHandler.executeVideoGeneration(this, workspacePanel.getAllScenes(),
                configPanel.getSpeakerImagePath(), configPanel.getVideoWidth(), configPanel.getVideoHeight(),
                workspacePanel.getTimelineListView(), workspacePanel.getVideoProgressBar(),
                this::setButtonsDisabled, workspacePanel::logToConsole, workspacePanel::updatePreviewForScene);
    }

    private void handleMasterRender() {
        executionHandler.executeMasterRender(this, workspacePanel.getAllScenes(),
                workspacePanel.getVideoProgressBar(), this::setButtonsDisabled, workspacePanel::logToConsole);
    }

    private void setButtonsDisabled(boolean disabled) {
        if (configPanel != null) configPanel.setButtonsDisabled(disabled);
        if (workspacePanel != null) workspacePanel.setButtonsDisabled(disabled);
    }

    /**
     * Parses the Gemma / LLM storyboard response or gracefully falls back to text segmentation.
     * Reflection-tested in VideoArchitectFeaturesTest.
     */
    private List<Scene> parseStoryboardJson(String rawResponse, String originalIdea) {
        int targetW = (videoWidthSpinner != null) ? (Integer) videoWidthSpinner.getValue() : 832;
        int targetH = (videoHeightSpinner != null) ? (Integer) videoHeightSpinner.getValue() : 480;
        return scriptParser.parseStoryboardJson(rawResponse, originalIdea, targetW, targetH, promptOptimizer);
    }

    /**
     * Smart rule-based fallback that decomposes user text into coherent visual scenes.
     * Reflection-tested in VideoArchitectFeaturesTest.
     */
    private List<Scene> splitScriptFallback(String idea) {
        int targetW = (videoWidthSpinner != null) ? (Integer) videoWidthSpinner.getValue() : 832;
        int targetH = (videoHeightSpinner != null) ? (Integer) videoHeightSpinner.getValue() : 480;
        return scriptParser.splitScriptFallback(idea, targetW, targetH, promptOptimizer);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        SwingUtilities.updateComponentTreeUI(this);
        updateTheme(ThemeManager.isDarkMode());
    }

    /**
     * Updates the UI components to reflect the current light or dark theme.
     *
     * @param darkMode true if dark mode is active, false for light mode
     */
    public void updateTheme(boolean darkMode) {
        VideoArchitectThemeHandler.applyTheme(this, darkMode,
                configPanel != null ? configPanel.getVideoPresetLabel() : null,
                workspacePanel != null ? workspacePanel.getVideoPreviewLabel() : null,
                workspacePanel != null ? workspacePanel.getTimelineListView() : null,
                workspacePanel != null ? workspacePanel.getTimelineScroll() : null,
                workspacePanel != null ? workspacePanel.getVideoConsoleArea() : null,
                workspacePanel != null ? workspacePanel.getStoryboardJsonArea() : null,
                configPanel != null ? configPanel.getBtnSuggestSubject() : null,
                configPanel != null ? configPanel.getBtnAutoPilot() : null,
                workspacePanel != null ? workspacePanel.getBtnGenerateScenes() : null,
                workspacePanel != null ? workspacePanel.getBtnMasterRender() : null);
    }

    /**
     * Returns the left configuration panel.
     *
     * @return the configuration panel
     */
    public VideoArchitectConfigPanel getConfigPanel() {
        return configPanel;
    }

    /**
     * Returns the right workspace panel.
     *
     * @return the workspace panel
     */
    public VideoArchitectWorkspacePanel getWorkspacePanel() {
        return workspacePanel;
    }

    /**
     * Returns the execution handler.
     *
     * @return the execution handler
     */
    public VideoArchitectExecutionHandler getExecutionHandler() {
        return executionHandler;
    }
}
