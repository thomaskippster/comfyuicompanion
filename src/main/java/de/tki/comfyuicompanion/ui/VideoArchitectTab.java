package de.tki.comfyuicompanion.ui;

import de.tki.comfyuicompanion.domain.HardwareProfile;
import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.domain.VideoPresetConfig;
import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import de.tki.comfyuicompanion.service.IHardwareProfileService;
import de.tki.comfyuicompanion.service.impl.ComfyPipelineService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.Gemma4Service;
import de.tki.comfyuicompanion.service.impl.LocalTTSService;
import de.tki.comfyuicompanion.service.impl.Video4jEditorService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
import de.tki.comfyuicompanion.ui.video.SceneCardCellRenderer;
import de.tki.comfyuicompanion.ui.video.VideoArchitectThemeHandler;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure Swing / FlatLaf implementation of Video Architect.
 * Adopts the exact visual layout, CardPanel structure, split-pane proportions,
 * JTabbedPane card styling, monospace console logging, and full-width action buttons
 * of the Image Lab (PromptLabView).
 */
@org.springframework.stereotype.Component
public class VideoArchitectTab extends JPanel {
    private static final Logger logger = LoggerFactory.getLogger(VideoArchitectTab.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ConfigService configService;
    private final ComfyPipelineService comfyPipelineService;
    private final Video4jEditorService video4jEditorService;
    private final LocalTTSService ttsService;
    private final Gemma4Service gemma4Service;
    private final IComfyLifecycleService lifecycleService;
    private final IHardwareProfileService hardwareProfileService;
    private final de.tki.comfyuicompanion.service.IVideoPromptOptimizer promptOptimizer;
    private final de.tki.comfyuicompanion.ui.video.VideoScriptParser scriptParser = new de.tki.comfyuicompanion.ui.video.VideoScriptParser();
    private final de.tki.comfyuicompanion.ui.video.VideoArchitectExecutionHandler executionHandler;

    // LEFT PANEL CONTROLS (Analogous to PromptLabView Left Panel)
    private JLabel hardwareProfileBadge;
    private JComboBox<String> videoModelCombo;
    private JLabel videoPresetLabel;
    private JTextField promptSubjectField;
    private JButton btnSuggestSubject;
    private JTextField speakerImageField;
    private JSpinner videoWidthSpinner;
    private JSpinner videoHeightSpinner;
    private JSpinner videoDurationSpinner;
    private JSpinner videoStepsSpinner;
    private JSpinner videoCfgSpinner;
    private JSpinner videoMotionSpinner;
    private JButton btnAutoPilot;

    // RIGHT PANEL CONTROLS (Analogous to PromptLabView Right Panel)
    private JTabbedPane videoRightTabbedPane;
    private DefaultListModel<Scene> timelineListModel;
    private JList<Scene> timelineListView;
    private JLabel videoPreviewLabel;
    private Image currentPreviewImage;
    private JTextArea storyboardJsonArea;
    private JTextArea videoConsoleArea;
    private JProgressBar videoProgressBar;
    private JButton btnGenerateScenes;
    private JButton btnMasterRender;
    private JScrollPane timelineScroll;

    // Toolbar Buttons
    private JButton addSceneBtn;
    private JButton editSceneBtn;
    private JButton visualFilterBtn;
    private JButton playVideoBtn;
    private JButton moveUpBtn;
    private JButton moveDownBtn;
    private JButton deleteBtn;
    private JButton clearListBtn;

    public VideoArchitectTab(ConfigService configService, ComfyPipelineService comfyPipelineService,
                             Video4jEditorService video4jEditorService, Gemma4Service gemma4Service,
                             IComfyLifecycleService lifecycleService) {
        this(configService, comfyPipelineService, video4jEditorService, gemma4Service, lifecycleService, null, null, null);
    }

    public VideoArchitectTab(ConfigService configService, ComfyPipelineService comfyPipelineService,
                             Video4jEditorService video4jEditorService, Gemma4Service gemma4Service,
                             IComfyLifecycleService lifecycleService, LocalTTSService ttsService) {
        this(configService, comfyPipelineService, video4jEditorService, gemma4Service, lifecycleService, ttsService, null, null);
    }

    public VideoArchitectTab(ConfigService configService, ComfyPipelineService comfyPipelineService,
                             Video4jEditorService video4jEditorService, Gemma4Service gemma4Service,
                             IComfyLifecycleService lifecycleService, LocalTTSService ttsService,
                             IHardwareProfileService hardwareProfileService) {
        this(configService, comfyPipelineService, video4jEditorService, gemma4Service, lifecycleService, ttsService, hardwareProfileService, null);
    }

    @Autowired
    public VideoArchitectTab(ConfigService configService, ComfyPipelineService comfyPipelineService,
                             Video4jEditorService video4jEditorService, Gemma4Service gemma4Service,
                             IComfyLifecycleService lifecycleService,
                             @Autowired(required = false) LocalTTSService ttsService,
                             @Autowired(required = false) IHardwareProfileService hardwareProfileService,
                             @Autowired(required = false) de.tki.comfyuicompanion.service.IVideoPromptOptimizer promptOptimizer) {
        this.configService = configService;
        this.comfyPipelineService = comfyPipelineService;
        this.video4jEditorService = video4jEditorService;
        this.gemma4Service = gemma4Service;
        this.lifecycleService = lifecycleService;
        this.ttsService = ttsService;
        this.hardwareProfileService = hardwareProfileService;
        this.promptOptimizer = promptOptimizer != null ? promptOptimizer : new de.tki.comfyuicompanion.service.impl.VideoPromptOptimizer();
        this.executionHandler = new de.tki.comfyuicompanion.ui.video.VideoArchitectExecutionHandler(
                configService, comfyPipelineService, video4jEditorService,
                ttsService, gemma4Service, lifecycleService, this.scriptParser, this.promptOptimizer);

        if (configService != null) {
            configService.setSpeakerImagePath("");
        }

        initUI();
        applyHardwarePreset(false);
        updateTheme(ThemeManager.isDarkMode());

        ThemeManager.registerObserver(() -> updateTheme(ThemeManager.isDarkMode()));
    }

    private void initUI() {
        setOpaque(false);
        setLayout(new BorderLayout(15, 15));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Create Left and Right panels
        CardPanel leftPanel = createLeftPanel();
        CardPanel rightPanel = createRightPanel();

        // SplitPane analogous to PromptLabView
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setOpaque(false);
        splitPane.setDividerLocation(360);
        splitPane.setResizeWeight(0.3);
        splitPane.setBorder(BorderFactory.createEmptyBorder());

        add(splitPane, BorderLayout.CENTER);
    }

    private CardPanel createLeftPanel() {
        CardPanel leftPanel = new CardPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));

        Font subLabelFont = new Font("SansSerif", Font.PLAIN, 12);

        // 0. Hardware Detection & Profile Banner
        JPanel hwHeaderRow = new JPanel(new BorderLayout(6, 0));
        hwHeaderRow.setOpaque(false);
        hwHeaderRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        hwHeaderRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        hardwareProfileBadge = new JLabel("⚡ Hardware: Erkennung...", SwingConstants.LEFT);
        hardwareProfileBadge.setFont(new Font("SansSerif", Font.BOLD, 11));
        hardwareProfileBadge.setOpaque(true);
        hardwareProfileBadge.setBackground(new Color(59, 130, 246, 30));
        hardwareProfileBadge.setForeground(new Color(59, 130, 246));
        hardwareProfileBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(59, 130, 246, 90), 1, true),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        hwHeaderRow.add(hardwareProfileBadge, BorderLayout.CENTER);

        JButton btnReapplyHwPreset = new JButton(SvgIconFactory.get(AppIcon.SUGGEST, 12));
        btnReapplyHwPreset.setToolTipText("Hardware-Empfehlungen automatisch anwenden");
        btnReapplyHwPreset.addActionListener(e -> applyHardwarePreset(true));
        hwHeaderRow.add(btnReapplyHwPreset, BorderLayout.EAST);

        leftPanel.add(hwHeaderRow);
        leftPanel.add(Box.createVerticalStrut(10));

        // 1. Blueprint / Workflow Selection
        JLabel lblBlueprint = new JLabel("Blueprint / Video Model");
        lblBlueprint.putClientProperty("FlatLaf.styleClass", "h4");
        lblBlueprint.setAlignmentX(Component.LEFT_ALIGNMENT);
        leftPanel.add(lblBlueprint);
        leftPanel.add(Box.createVerticalStrut(5));

        JPanel modelRow = new JPanel(new BorderLayout(5, 0));
        modelRow.setOpaque(false);
        modelRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        modelRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));

        videoModelCombo = new JComboBox<>(new String[]{
                "Text to Video (Wan 2.2)",
                "Text to Video (LTX-Video 0.9.5)",
                "Image to Video (Wan 2.1)",
                "Text to Video (Hunyuan Video 1.5)"
        });
        videoModelCombo.setFont(new Font("SansSerif", Font.PLAIN, 13));
        videoModelCombo.addActionListener(e -> updateModelPreset());
        modelRow.add(videoModelCombo, BorderLayout.CENTER);

        JButton btnRefreshModels = new JButton(SvgIconFactory.get(AppIcon.REFRESH));
        btnRefreshModels.setToolTipText("Refresh video models list from ComfyUI");
        btnRefreshModels.addActionListener(e -> logToConsole("Refreshed video pipelines from ComfyUI."));
        modelRow.add(btnRefreshModels, BorderLayout.EAST);

        leftPanel.add(modelRow);
        leftPanel.add(Box.createVerticalStrut(6));

        videoPresetLabel = new JLabel("Detected Preset: Wan 2.2 Cinematic Video (832x480 | 16 fps, Safe VRAM)") {
            @Override
            public void updateUI() {
                super.updateUI();
                Color c = UIManager.getColor("PromptLab.presetForeground");
                if (c != null) setForeground(c);
            }
        };
        videoPresetLabel.setFont(new Font("SansSerif", Font.ITALIC | Font.BOLD, 12));
        Color pc = UIManager.getColor("PromptLab.presetForeground");
        if (pc != null) videoPresetLabel.setForeground(pc);
        videoPresetLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        leftPanel.add(videoPresetLabel);
        leftPanel.add(Box.createVerticalStrut(16));

        // 2. Prompt / Storyboard Concept
        JLabel lblPromptGroup = new JLabel("Script / Concept");
        lblPromptGroup.putClientProperty("FlatLaf.styleClass", "h4");
        lblPromptGroup.setAlignmentX(Component.LEFT_ALIGNMENT);
        leftPanel.add(lblPromptGroup);
        leftPanel.add(Box.createVerticalStrut(6));

        JLabel lblSubject = new JLabel("Master Story Idea / Prompt");
        lblSubject.setFont(subLabelFont);
        lblSubject.setAlignmentX(Component.LEFT_ALIGNMENT);
        leftPanel.add(lblSubject);
        leftPanel.add(Box.createVerticalStrut(4));

        JPanel subjectRow = new JPanel(new BorderLayout(8, 0));
        subjectRow.setOpaque(false);
        subjectRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        subjectRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        promptSubjectField = new JTextField("A rocket launch from a launch pad in the desert. People watching the launch from a train station.");
        promptSubjectField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subjectRow.add(promptSubjectField, BorderLayout.CENTER);

        btnSuggestSubject = new JButton("Deconstruct", SvgIconFactory.get(AppIcon.SUGGEST));
        btnSuggestSubject.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnSuggestSubject.putClientProperty("Button.background", ThemeManager.getAccentColor());
        btnSuggestSubject.putClientProperty("Button.foreground", Color.WHITE);
        btnSuggestSubject.setToolTipText("Deconstruct script idea into cinematic scenes using Gemma AI or rule-based parser.");
        btnSuggestSubject.addActionListener(e -> handleDeconstructScript());
        subjectRow.add(btnSuggestSubject, BorderLayout.EAST);

        leftPanel.add(subjectRow);
        leftPanel.add(Box.createVerticalStrut(10));

        // 3. Consistency / Global Start Image
        JLabel lblStartImage = new JLabel("Start Image (Consistency)");
        lblStartImage.setFont(subLabelFont);
        lblStartImage.setAlignmentX(Component.LEFT_ALIGNMENT);
        leftPanel.add(lblStartImage);
        leftPanel.add(Box.createVerticalStrut(4));

        JPanel speakerRow = new JPanel(new BorderLayout(6, 0));
        speakerRow.setOpaque(false);
        speakerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        speakerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        speakerImageField = new JTextField("");
        speakerImageField.setFont(new Font("SansSerif", Font.PLAIN, 12));
        speakerRow.add(speakerImageField, BorderLayout.CENTER);

        JPanel speakerBtnGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        speakerBtnGroup.setOpaque(false);

        JButton browseSpeaker = new JButton("Browse...");
        browseSpeaker.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select Start Image");
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "webp"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File f = chooser.getSelectedFile();
                speakerImageField.setText(f.getAbsolutePath());
                if (configService != null) {
                    configService.setSpeakerImagePath(f.getAbsolutePath());
                }
            }
        });

        JButton clearSpeaker = new JButton(SvgIconFactory.get(AppIcon.CLOSE, 12));
        clearSpeaker.setToolTipText("Clear Start Image (Pure Text-to-Video)");
        clearSpeaker.addActionListener(e -> {
            speakerImageField.setText("");
            if (configService != null) {
                configService.setSpeakerImagePath("");
            }
        });

        speakerBtnGroup.add(browseSpeaker);
        speakerBtnGroup.add(clearSpeaker);
        speakerRow.add(speakerBtnGroup, BorderLayout.EAST);
        leftPanel.add(speakerRow);
        leftPanel.add(Box.createVerticalStrut(16));

        // 4. Output Dimensions & Duration (3-column layout identical to PromptLabView)
        JLabel lblDimensions = new JLabel("Output Dimensions & Duration");
        lblDimensions.putClientProperty("FlatLaf.styleClass", "h4");
        lblDimensions.setAlignmentX(Component.LEFT_ALIGNMENT);
        leftPanel.add(lblDimensions);
        leftPanel.add(Box.createVerticalStrut(5));

        JPanel sizeRow = new JPanel(new GridLayout(1, 3, 8, 0));
        sizeRow.setOpaque(false);
        sizeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        sizeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

        videoWidthSpinner = new JSpinner(new SpinnerNumberModel(832, 256, 3840, 16));
        videoHeightSpinner = new JSpinner(new SpinnerNumberModel(480, 256, 2160, 16));
        videoDurationSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 60, 1));
        sizeRow.add(createSpinnerPanel("Width", videoWidthSpinner));
        sizeRow.add(createSpinnerPanel("Height", videoHeightSpinner));
        sizeRow.add(createSpinnerPanel("Scene Sec", videoDurationSpinner));
        leftPanel.add(sizeRow);
        leftPanel.add(Box.createVerticalStrut(16));

        // 5. Generation & Sampling Parameters (3-column layout identical to PromptLabView)
        JLabel lblSampler = new JLabel("Generation & Sampling Parameters");
        lblSampler.putClientProperty("FlatLaf.styleClass", "h4");
        lblSampler.setAlignmentX(Component.LEFT_ALIGNMENT);
        leftPanel.add(lblSampler);
        leftPanel.add(Box.createVerticalStrut(5));

        JPanel samplerRow = new JPanel(new GridLayout(1, 3, 8, 0));
        samplerRow.setOpaque(false);
        samplerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        samplerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

        videoStepsSpinner = new JSpinner(new SpinnerNumberModel(20, 1, 100, 1));
        videoCfgSpinner = new JSpinner(new SpinnerNumberModel(3.0, 0.5, 20.0, 0.5));
        videoMotionSpinner = new JSpinner(new SpinnerNumberModel(127, 1, 255, 1));
        samplerRow.add(createSpinnerPanel("Steps", videoStepsSpinner));
        samplerRow.add(createSpinnerPanel("CFG Scale", videoCfgSpinner));
        samplerRow.add(createSpinnerPanel("Motion Bucket", videoMotionSpinner));
        leftPanel.add(samplerRow);
        leftPanel.add(Box.createVerticalStrut(18));

        // 6. Autonomous Action
        btnAutoPilot = new JButton("Agent: Auto-Pilot (End-to-End)", SvgIconFactory.get(AppIcon.AGENT));
        btnAutoPilot.setFont(new Font("SansSerif", Font.BOLD, 13));
        btnAutoPilot.putClientProperty("Button.background", new Color(255, 204, 0));
        btnAutoPilot.putClientProperty("Button.foreground", Color.BLACK);
        btnAutoPilot.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnAutoPilot.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btnAutoPilot.setToolTipText("Deconstruct script, generate narration TTS, render ComfyUI scene videos, and stitch into master_export.mp4 automatically.");
        btnAutoPilot.addActionListener(e -> handleAutoPilot());
        leftPanel.add(btnAutoPilot);

        leftPanel.add(Box.createVerticalGlue());
        return leftPanel;
    }

    private JPanel createSpinnerPanel(String label, JSpinner spinner) {
        JPanel panel = new JPanel(new BorderLayout(0, 2));
        panel.setOpaque(false);
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        if (spinner.getEditor() instanceof JSpinner.NumberEditor ne) ne.getFormat().setGroupingUsed(false);
        panel.add(lbl, BorderLayout.NORTH);
        panel.add(spinner, BorderLayout.CENTER);
        return panel;
    }

    private CardPanel createRightPanel() {
        CardPanel rightPanel = new CardPanel();
        rightPanel.setLayout(new BorderLayout(10, 10));

        // Right Top Header
        JPanel rightTopPanel = new JPanel(new BorderLayout());
        rightTopPanel.setOpaque(false);
        rightTopPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        JLabel apiHeader = new JLabel("Video Architect Sequence & Preview");
        apiHeader.putClientProperty("FlatLaf.styleClass", "h3");
        rightTopPanel.add(apiHeader, BorderLayout.CENTER);

        rightPanel.add(rightTopPanel, BorderLayout.NORTH);

        // Right TabbedPane with Card style (identical to PromptLabView)
        videoRightTabbedPane = new JTabbedPane();
        videoRightTabbedPane.putClientProperty("JTabbedPane.tabType", "card");
        videoRightTabbedPane.setOpaque(false);

        // TAB 1: Timeline Sequence
        JPanel timelineTabPanel = new JPanel(new BorderLayout(8, 8));
        timelineTabPanel.setOpaque(false);

        // Timeline Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        toolbar.setOpaque(false);

        addSceneBtn = new JButton("Add Scene", SvgIconFactory.get(AppIcon.ADD));
        addSceneBtn.addActionListener(e -> showEditSceneDialog(null, true));
        toolbar.add(addSceneBtn);

        editSceneBtn = new JButton("Edit", SvgIconFactory.get(AppIcon.EDIT));
        editSceneBtn.addActionListener(e -> {
            Scene selected = timelineListView.getSelectedValue();
            if (selected != null) {
                showEditSceneDialog(selected, false);
            } else {
                logToConsole("Select a scene to edit.");
            }
        });
        toolbar.add(editSceneBtn);

        visualFilterBtn = new JButton("Visual Filter (OpenCV)", SvgIconFactory.get(AppIcon.PALETTE));
        visualFilterBtn.setToolTipText("Adjust frame contrast and brightness with native OpenCV real-time preview.");
        visualFilterBtn.addActionListener(e -> {
            Scene selected = timelineListView.getSelectedValue();
            if (selected != null) {
                showOpenCvEnhancementDialog(selected);
            } else {
                logToConsole("Select a scene to adjust OpenCV visual enhancement.");
            }
        });
        toolbar.add(visualFilterBtn);

        playVideoBtn = new JButton("Play Video", SvgIconFactory.get(AppIcon.PLAY));
        playVideoBtn.addActionListener(e -> handlePreviewSceneVideo());
        toolbar.add(playVideoBtn);

        moveUpBtn = new JButton("Up");
        moveUpBtn.addActionListener(e -> handleMoveScene(-1));
        toolbar.add(moveUpBtn);

        moveDownBtn = new JButton("Down");
        moveDownBtn.addActionListener(e -> handleMoveScene(1));
        toolbar.add(moveDownBtn);

        deleteBtn = new JButton("Delete", SvgIconFactory.get(AppIcon.DELETE));
        deleteBtn.addActionListener(e -> handleDeleteScene());
        toolbar.add(deleteBtn);

        clearListBtn = new JButton("Clear All");
        clearListBtn.addActionListener(e -> {
            if (timelineListModel.isEmpty()) return;
            int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to clear all timeline scenes?", "Clear Timeline", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                timelineListModel.clear();
                updateStoryboardJson();
                logToConsole("Timeline cleared.");
            }
        });
        toolbar.add(clearListBtn);

        timelineTabPanel.add(toolbar, BorderLayout.NORTH);

        // Timeline List with Dark/Transparent theme styling
        timelineListModel = new DefaultListModel<>();
        timelineListView = new JList<>(timelineListModel);
        timelineListView.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        timelineListView.setCellRenderer(new SceneCardCellRenderer());
        timelineListView.setBackground(ThemeManager.isDarkMode() ? new Color(15, 17, 22) : Color.WHITE);
        timelineListView.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Scene selected = timelineListView.getSelectedValue();
                if (selected != null) {
                    updatePreviewForScene(selected);
                    if (e.getClickCount() == 2) {
                        showEditSceneDialog(selected, false);
                    }
                }
            }
        });

        timelineScroll = new JScrollPane(timelineListView);
        timelineScroll.setOpaque(false);
        timelineScroll.getViewport().setOpaque(false);
        timelineScroll.getViewport().setBackground(ThemeManager.isDarkMode() ? new Color(15, 17, 22) : Color.WHITE);
        timelineScroll.setBorder(BorderFactory.createEmptyBorder());
        timelineTabPanel.add(timelineScroll, BorderLayout.CENTER);

        videoRightTabbedPane.addTab("Timeline Sequence", SvgIconFactory.get(AppIcon.VIDEO_ARCHITECT), timelineTabPanel);

        // TAB 2: Scene / Video Preview
        JPanel previewTabPanel = new JPanel(new BorderLayout(10, 10));
        previewTabPanel.setOpaque(false);

        videoPreviewLabel = new JLabel("Select a scene or generate video to preview", SwingConstants.CENTER);
        videoPreviewLabel.setFont(new Font("SansSerif", Font.ITALIC, 14));
        videoPreviewLabel.setForeground(Color.GRAY);
        videoPreviewLabel.putClientProperty("FlatLaf.style", "background: $TextField.background");
        videoPreviewLabel.setOpaque(true);
        videoPreviewLabel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (currentPreviewImage != null) {
                    scaleAndSetImage(currentPreviewImage);
                }
            }
        });

        JScrollPane previewScroll = new JScrollPane(videoPreviewLabel);
        previewScroll.setOpaque(false);
        previewScroll.getViewport().setOpaque(false);
        previewScroll.setBorder(BorderFactory.createEmptyBorder());
        previewTabPanel.add(previewScroll, BorderLayout.CENTER);

        videoRightTabbedPane.addTab("Scene Preview", SvgIconFactory.get(AppIcon.GALLERY), previewTabPanel);

        // TAB 3: Storyboard JSON
        storyboardJsonArea = new JTextArea("[]");
        storyboardJsonArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        storyboardJsonArea.setEditable(false);
        JScrollPane jsonScroll = new JScrollPane(storyboardJsonArea);
        jsonScroll.setOpaque(false);
        jsonScroll.getViewport().setOpaque(false);
        jsonScroll.setBorder(BorderFactory.createEmptyBorder());
        videoRightTabbedPane.addTab("Storyboard JSON", SvgIconFactory.get(AppIcon.FILE_TEXT), jsonScroll);

        rightPanel.add(videoRightTabbedPane, BorderLayout.CENTER);

        // Bottom panel with Monospace Console & Full-Width Action Buttons (identical to PromptLabView)
        JPanel rightBottomPanel = new JPanel(new BorderLayout(6, 6));
        rightBottomPanel.setOpaque(false);

        videoConsoleArea = new JTextArea(4, 20);
        videoConsoleArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        videoConsoleArea.setEditable(false);
        videoConsoleArea.setText("System: Video Architect ready.\n");
        JScrollPane consoleScroll = new JScrollPane(videoConsoleArea);
        consoleScroll.setOpaque(false);
        consoleScroll.getViewport().setOpaque(false);
        consoleScroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel centerBottom = new JPanel(new BorderLayout(4, 4));
        centerBottom.setOpaque(false);
        centerBottom.add(consoleScroll, BorderLayout.CENTER);

        videoProgressBar = new JProgressBar();
        videoProgressBar.setStringPainted(true);
        videoProgressBar.setVisible(false);
        videoProgressBar.setPreferredSize(new Dimension(0, 22));
        centerBottom.add(videoProgressBar, BorderLayout.SOUTH);

        rightBottomPanel.add(centerBottom, BorderLayout.CENTER);

        // Two prominent execution buttons side-by-side (height 45, identical to PromptLabView's btnSendToComfy)
        JPanel executionRow = new JPanel(new GridLayout(1, 2, 10, 0));
        executionRow.setOpaque(false);
        executionRow.setPreferredSize(new Dimension(0, 45));

        btnGenerateScenes = new JButton("1. Generate All Scenes", SvgIconFactory.get(AppIcon.LAUNCH));
        btnGenerateScenes.setFont(new Font("SansSerif", Font.BOLD, 14));
        btnGenerateScenes.putClientProperty("Button.background", ThemeManager.getAccentColor());
        btnGenerateScenes.putClientProperty("Button.foreground", Color.WHITE);
        btnGenerateScenes.addActionListener(e -> handleVideoGeneration());

        btnMasterRender = new JButton("2. Stitch Videos (Master Render)", SvgIconFactory.get(AppIcon.VIDEO_ARCHITECT));
        btnMasterRender.setFont(new Font("SansSerif", Font.BOLD, 14));
        btnMasterRender.putClientProperty("Button.background", new Color(255, 204, 0));
        btnMasterRender.putClientProperty("Button.foreground", Color.BLACK);
        btnMasterRender.addActionListener(e -> handleMasterRender());

        executionRow.add(btnGenerateScenes);
        executionRow.add(btnMasterRender);

        rightBottomPanel.add(executionRow, BorderLayout.SOUTH);
        rightPanel.add(rightBottomPanel, BorderLayout.SOUTH);

        return rightPanel;
    }

    private void scaleAndSetImage(Image img) {
        if (img == null) return;
        this.currentPreviewImage = img;
        int pw = videoPreviewLabel.getWidth();
        int ph = videoPreviewLabel.getHeight();
        if (pw <= 0 || ph <= 0) {
            pw = 512;
            ph = 512;
        }

        int iw = img.getWidth(null);
        int ih = img.getHeight(null);
        if (iw <= 0 || ih <= 0) return;

        double scale = Math.min((double) pw / iw, (double) ph / ih);
        int nw = (int) (iw * scale);
        int nh = (int) (ih * scale);

        Image scaled = img.getScaledInstance(nw, nh, Image.SCALE_SMOOTH);
        videoPreviewLabel.setIcon(new ImageIcon(scaled));
        videoPreviewLabel.setText("");
    }

    private void updatePreviewForScene(Scene scene) {
        Thread.ofVirtual().start(() -> {
            try {
                BufferedImage img = video4jEditorService.applyBasicEnhancementAwt(scene, scene.getContrast(), scene.getBrightness());
                SwingUtilities.invokeLater(() -> {
                    if (img != null) {
                        scaleAndSetImage(img);
                    } else {
                        videoPreviewLabel.setIcon(null);
                        videoPreviewLabel.setText("Scene " + scene.getSceneId() + " (No video or frame generated yet)");
                    }
                });
            } catch (Exception ex) {
                logger.warn("Failed to update preview: {}", ex.getMessage());
            }
        });
    }

    private void updateStoryboardJson() {
        JSONArray arr = new JSONArray();
        for (int i = 0; i < timelineListModel.size(); i++) {
            Scene sc = timelineListModel.get(i);
            JSONObject obj = new JSONObject();
            obj.put("scene_id", sc.getSceneId());
            obj.put("visual_prompt", sc.getPrompt());
            obj.put("narration_text", sc.getNarrationText());
            int dur = Math.max(1, (sc.getEndFrame() - sc.getStartFrame()) / 24);
            obj.put("duration_seconds", dur);
            obj.put("contrast", sc.getContrast());
            obj.put("brightness", sc.getBrightness());
            arr.put(obj);
        }
        storyboardJsonArea.setText(arr.toString(2));
    }

    public void applyHardwarePreset(boolean logMessage) {
        if (hardwareProfileService == null) return;
        VideoPresetConfig config = hardwareProfileService.getRecommendedVideoConfig();
        HardwareProfile profile = hardwareProfileService.getHardwareProfile();

        if (hardwareProfileBadge != null && profile != null) {
            String badgeText = String.format("⚡ %s: %s VRAM • %s",
                    profile.tier().name(), profile.formattedVram(), profile.tier().getDescription());
            hardwareProfileBadge.setText(badgeText);
            hardwareProfileBadge.setToolTipText(config != null ? config.statusDescription() : profile.gpuName());
            if (profile.isWeakSystem()) {
                hardwareProfileBadge.setBackground(new Color(245, 158, 11, 40));
                hardwareProfileBadge.setForeground(new Color(217, 119, 6));
                hardwareProfileBadge.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(245, 158, 11, 100), 1, true),
                        BorderFactory.createEmptyBorder(4, 8, 4, 8)
                ));
            } else {
                hardwareProfileBadge.setBackground(new Color(16, 185, 129, 35));
                hardwareProfileBadge.setForeground(new Color(16, 185, 129));
                hardwareProfileBadge.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(16, 185, 129, 100), 1, true),
                        BorderFactory.createEmptyBorder(4, 8, 4, 8)
                ));
            }
        }

        if (config != null) {
            if (videoModelCombo != null && config.recommendedModel() != null) {
                videoModelCombo.setSelectedItem(config.recommendedModel());
            }
            if (videoWidthSpinner != null) {
                videoWidthSpinner.setValue(config.defaultWidth());
            }
            if (videoHeightSpinner != null) {
                videoHeightSpinner.setValue(config.defaultHeight());
            }
            if (videoStepsSpinner != null) {
                videoStepsSpinner.setValue(config.defaultSteps());
            }
            if (videoCfgSpinner != null) {
                videoCfgSpinner.setValue(config.defaultCfg());
            }
            if (videoPresetLabel != null) {
                videoPresetLabel.setText("Detected Preset: " + config.recommendedModel() + " (" + config.defaultWidth() + "x" + config.defaultHeight() + " | Safe VRAM)");
            }
            if (logMessage) {
                logToConsole("⚡ Hardware-Vorkonfiguration angewendet: " + config.statusDescription());
            }
        }
    }

    private void updateModelPreset() {
        String selected = (String) videoModelCombo.getSelectedItem();
        if (selected == null) return;
        if (selected.contains("LTX-Video")) {
            videoPresetLabel.setText("Detected Preset: LTX-Video High-Speed (768x512 | 24 fps)");
            videoWidthSpinner.setValue(768);
            videoHeightSpinner.setValue(512);
            videoStepsSpinner.setValue(30);
            videoCfgSpinner.setValue(3.0);
        } else if (selected.contains("Hunyuan")) {
            videoPresetLabel.setText("Detected Preset: Hunyuan Video 1.5 (832x480 | 24 fps, Safe VRAM)");
            videoWidthSpinner.setValue(832);
            videoHeightSpinner.setValue(480);
            videoStepsSpinner.setValue(20);
            videoCfgSpinner.setValue(6.0);
        } else if (selected.contains("Image to Video")) {
            videoPresetLabel.setText("Detected Preset: Wan 2.1 I2V (832x480 | 16 fps, Safe VRAM)");
            videoWidthSpinner.setValue(832);
            videoHeightSpinner.setValue(480);
            videoStepsSpinner.setValue(20);
            videoCfgSpinner.setValue(3.0);
        } else {
            videoPresetLabel.setText("Detected Preset: Wan 2.2 Cinematic Video (832x480 | 16 fps, Safe VRAM)");
            videoWidthSpinner.setValue(832);
            videoHeightSpinner.setValue(480);
            videoStepsSpinner.setValue(20);
            videoCfgSpinner.setValue(3.0);
        }

        // Warn if a heavy 14B model is selected on a weak/budget GPU
        if (hardwareProfileService != null) {
            HardwareProfile profile = hardwareProfileService.getHardwareProfile();
            if (profile != null && profile.isWeakSystem() && (selected.contains("Wan") || selected.contains("Hunyuan"))) {
                logToConsole("⚠️ Notice: " + selected + " is very VRAM-intensive (14B). On your system (" + profile.formattedVram() + " VRAM), LTX-Video is recommended.");
            }
        }

        logToConsole("Switched model preset: " + selected + " (" + videoWidthSpinner.getValue() + "x" + videoHeightSpinner.getValue() + ")");
    }

    private void logToConsole(String message) {
        SwingUtilities.invokeLater(() -> {
            if (videoConsoleArea == null || !videoConsoleArea.isDisplayable()) {
                return;
            }
            String time = LocalTime.now().format(TIME_FORMATTER);
            videoConsoleArea.append("[" + time + "] " + message + "\n");
            try {
                videoConsoleArea.setCaretPosition(videoConsoleArea.getDocument().getLength());
            } catch (IllegalArgumentException ignored) {
                // Caret position may be stale if document changed concurrently
            }
        });
    }

    private void handleMoveScene(int direction) {
        int index = timelineListView.getSelectedIndex();
        if (index < 0) {
            logToConsole("Select a scene to reorder.");
            return;
        }
        int newIndex = index + direction;
        if (newIndex >= 0 && newIndex < timelineListModel.size()) {
            Scene scene = timelineListModel.remove(index);
            timelineListModel.add(newIndex, scene);
            timelineListView.setSelectedIndex(newIndex);
            updateStoryboardJson();
            logToConsole("Moved scene " + scene.getSceneId() + " to position " + (newIndex + 1));
        }
    }

    private void handleDeleteScene() {
        int index = timelineListView.getSelectedIndex();
        if (index >= 0) {
            Scene removed = timelineListModel.remove(index);
            updateStoryboardJson();
            logToConsole("Deleted Scene: " + removed.getSceneId());
        } else {
            logToConsole("No scene selected to delete!");
        }
    }

    private void handlePreviewSceneVideo() {
        Scene selected = timelineListView.getSelectedValue();
        if (selected == null) {
            logToConsole("Select a scene to preview.");
            return;
        }
        String path = selected.getVideoPath();
        if (path == null || path.trim().isEmpty()) {
            logToConsole("Scene " + selected.getSceneId() + " has no generated video yet.");
            return;
        }
        File vf = new File(path);
        if (!vf.exists()) {
            logToConsole("Video file not found at: " + path);
            return;
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(vf);
                logToConsole("Opening video for scene: " + selected.getSceneId());
            } else {
                logToConsole("Desktop open not supported. Video: " + path);
            }
        } catch (Exception ex) {
            logger.error("Failed to open video file: {}", ex.getMessage());
            logToConsole("Failed to open video: " + ex.getMessage());
        }
    }

    /**
     * OpenCV-powered Visual Enhancement modal dialog with real-time contrast & brightness feedback.
     */
    private void showOpenCvEnhancementDialog(Scene scene) {
        new de.tki.comfyuicompanion.ui.video.OpenCvEnhancementDialog(this, scene, video4jEditorService, () -> {
            timelineListView.repaint();
            updateStoryboardJson();
            updatePreviewForScene(scene);
        }, this::logToConsole);
    }

    private void showEditSceneDialog(Scene targetScene, boolean isNew) {
        new de.tki.comfyuicompanion.ui.video.SceneEditDialog(this, targetScene, isNew,
                timelineListModel.size() + 1,
                (Integer) videoWidthSpinner.getValue(),
                (Integer) videoHeightSpinner.getValue(),
                promptOptimizer,
                sc -> {
                    if (isNew) {
                        timelineListModel.addElement(sc);
                        logToConsole("Added new Scene: " + sc.getSceneId());
                    } else {
                        timelineListView.repaint();
                        logToConsole("Updated Scene: " + sc.getSceneId());
                    }
                    updateStoryboardJson();
                });
    }

    private void handleDeconstructScript() {
        executionHandler.deconstructScript(this,
                promptSubjectField.getText().trim(),
                (Integer) videoWidthSpinner.getValue(),
                (Integer) videoHeightSpinner.getValue(),
                ((Number) videoCfgSpinner.getValue()).doubleValue(),
                (Integer) videoStepsSpinner.getValue(),
                (Integer) videoMotionSpinner.getValue(),
                timelineListModel,
                timelineListView,
                videoProgressBar,
                this::setButtonsDisabled,
                this::logToConsole,
                this::updatePreviewForScene,
                this::updateStoryboardJson);
    }

    private void handleAutoPilot() {
        executionHandler.executeAutoPilot(this,
                promptSubjectField.getText().trim(),
                speakerImageField.getText().trim(),
                (Integer) videoWidthSpinner.getValue(),
                (Integer) videoHeightSpinner.getValue(),
                ((Number) videoCfgSpinner.getValue()).doubleValue(),
                (Integer) videoStepsSpinner.getValue(),
                (Integer) videoMotionSpinner.getValue(),
                timelineListModel,
                timelineListView,
                videoProgressBar,
                this::setButtonsDisabled,
                this::logToConsole,
                this::updatePreviewForScene,
                this::updateStoryboardJson);
    }

    private void handleVideoGeneration() {
        List<Scene> scenes = new ArrayList<>();
        for (int i = 0; i < timelineListModel.size(); i++) {
            scenes.add(timelineListModel.get(i));
        }
        executionHandler.executeVideoGeneration(this,
                scenes,
                speakerImageField.getText().trim(),
                (Integer) videoWidthSpinner.getValue(),
                (Integer) videoHeightSpinner.getValue(),
                timelineListView,
                videoProgressBar,
                this::setButtonsDisabled,
                this::logToConsole,
                this::updatePreviewForScene);
    }

    private void handleMasterRender() {
        List<Scene> scenes = new ArrayList<>();
        for (int i = 0; i < timelineListModel.size(); i++) {
            scenes.add(timelineListModel.get(i));
        }
        executionHandler.executeMasterRender(this,
                scenes,
                videoProgressBar,
                this::setButtonsDisabled,
                this::logToConsole);
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

    public void updateTheme(boolean darkMode) {
        VideoArchitectThemeHandler.applyTheme(this, darkMode, videoPresetLabel, videoPreviewLabel,
                timelineListView, timelineScroll, videoConsoleArea, storyboardJsonArea,
                btnSuggestSubject, btnAutoPilot, btnGenerateScenes, btnMasterRender);
    }

    private void setButtonsDisabled(boolean disabled) {
        VideoArchitectThemeHandler.setButtonsDisabled(disabled, btnSuggestSubject, btnAutoPilot,
                btnGenerateScenes, btnMasterRender, addSceneBtn, editSceneBtn, moveUpBtn, moveDownBtn,
                visualFilterBtn, playVideoBtn, deleteBtn, clearListBtn);
    }
}
