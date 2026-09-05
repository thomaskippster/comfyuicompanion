package de.tki.comfymodels.ui;

import de.tki.comfymodels.domain.Scene;
import de.tki.comfymodels.service.IComfyLifecycleService;
import de.tki.comfymodels.service.impl.ComfyPipelineService;
import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.Gemma4Service;
import de.tki.comfymodels.service.impl.LocalTTSService;
import de.tki.comfymodels.service.impl.Video4jEditorService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

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

    // LEFT PANEL CONTROLS (Analogous to PromptLabView Left Panel)
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

    public VideoArchitectTab(ConfigService configService,
                             ComfyPipelineService comfyPipelineService,
                             Video4jEditorService video4jEditorService,
                             Gemma4Service gemma4Service,
                             IComfyLifecycleService lifecycleService) {
        this(configService, comfyPipelineService, video4jEditorService, gemma4Service, lifecycleService, null);
    }

    @Autowired
    public VideoArchitectTab(ConfigService configService,
                             ComfyPipelineService comfyPipelineService,
                             Video4jEditorService video4jEditorService,
                             Gemma4Service gemma4Service,
                             IComfyLifecycleService lifecycleService,
                             @Autowired(required = false) LocalTTSService ttsService) {
        this.configService = configService;
        this.comfyPipelineService = comfyPipelineService;
        this.video4jEditorService = video4jEditorService;
        this.gemma4Service = gemma4Service;
        this.lifecycleService = lifecycleService;
        this.ttsService = ttsService;

        if (configService != null) {
            configService.setSpeakerImagePath("");
        }

        initUI();
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
        modelRow.add(videoModelCombo, BorderLayout.CENTER);

        JButton btnRefreshModels = new JButton("🔄");
        btnRefreshModels.setToolTipText("Refresh video models list from ComfyUI");
        btnRefreshModels.addActionListener(e -> logToConsole("Refreshed video pipelines from ComfyUI."));
        modelRow.add(btnRefreshModels, BorderLayout.EAST);

        leftPanel.add(modelRow);
        leftPanel.add(Box.createVerticalStrut(6));

        videoPresetLabel = new JLabel("Detected Preset: Wan 2.2 Cinematic Video (720x720 | 24 fps)") {
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

        promptSubjectField = new JTextField("Ein Raketenstart von einer Startrampe in der Wüste. Personen beobachten den Start von einem Bahnhof.");
        promptSubjectField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subjectRow.add(promptSubjectField, BorderLayout.CENTER);

        btnSuggestSubject = new JButton("✨ Deconstruct");
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

        JButton clearSpeaker = new JButton("❌");
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

        JPanel widthPanel = new JPanel(new BorderLayout(0, 2));
        widthPanel.setOpaque(false);
        JLabel lblWidth = new JLabel("Width");
        lblWidth.setFont(new Font("SansSerif", Font.PLAIN, 11));
        videoWidthSpinner = new JSpinner(new SpinnerNumberModel(1920, 256, 3840, 64));
        if (videoWidthSpinner.getEditor() instanceof JSpinner.NumberEditor ne) ne.getFormat().setGroupingUsed(false);
        widthPanel.add(lblWidth, BorderLayout.NORTH);
        widthPanel.add(videoWidthSpinner, BorderLayout.CENTER);

        JPanel heightPanel = new JPanel(new BorderLayout(0, 2));
        heightPanel.setOpaque(false);
        JLabel lblHeight = new JLabel("Height");
        lblHeight.setFont(new Font("SansSerif", Font.PLAIN, 11));
        videoHeightSpinner = new JSpinner(new SpinnerNumberModel(1080, 256, 2160, 64));
        if (videoHeightSpinner.getEditor() instanceof JSpinner.NumberEditor ne) ne.getFormat().setGroupingUsed(false);
        heightPanel.add(lblHeight, BorderLayout.NORTH);
        heightPanel.add(videoHeightSpinner, BorderLayout.CENTER);

        JPanel durationPanel = new JPanel(new BorderLayout(0, 2));
        durationPanel.setOpaque(false);
        JLabel lblDur = new JLabel("Scene Sec");
        lblDur.setFont(new Font("SansSerif", Font.PLAIN, 11));
        videoDurationSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 60, 1));
        if (videoDurationSpinner.getEditor() instanceof JSpinner.NumberEditor ne) ne.getFormat().setGroupingUsed(false);
        durationPanel.add(lblDur, BorderLayout.NORTH);
        durationPanel.add(videoDurationSpinner, BorderLayout.CENTER);

        sizeRow.add(widthPanel);
        sizeRow.add(heightPanel);
        sizeRow.add(durationPanel);
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

        JPanel stepsPanel = new JPanel(new BorderLayout(0, 2));
        stepsPanel.setOpaque(false);
        JLabel lblSteps = new JLabel("Steps");
        lblSteps.setFont(new Font("SansSerif", Font.PLAIN, 11));
        videoStepsSpinner = new JSpinner(new SpinnerNumberModel(30, 10, 100, 1));
        if (videoStepsSpinner.getEditor() instanceof JSpinner.NumberEditor ne) ne.getFormat().setGroupingUsed(false);
        stepsPanel.add(lblSteps, BorderLayout.NORTH);
        stepsPanel.add(videoStepsSpinner, BorderLayout.CENTER);

        JPanel cfgPanel = new JPanel(new BorderLayout(0, 2));
        cfgPanel.setOpaque(false);
        JLabel lblCfg = new JLabel("CFG Scale");
        lblCfg.setFont(new Font("SansSerif", Font.PLAIN, 11));
        videoCfgSpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.5, 20.0, 0.5));
        if (videoCfgSpinner.getEditor() instanceof JSpinner.NumberEditor ne) ne.getFormat().setGroupingUsed(false);
        cfgPanel.add(lblCfg, BorderLayout.NORTH);
        cfgPanel.add(videoCfgSpinner, BorderLayout.CENTER);

        JPanel motionPanel = new JPanel(new BorderLayout(0, 2));
        motionPanel.setOpaque(false);
        JLabel lblMotion = new JLabel("Motion Bucket");
        lblMotion.setFont(new Font("SansSerif", Font.PLAIN, 11));
        videoMotionSpinner = new JSpinner(new SpinnerNumberModel(127, 1, 255, 1));
        if (videoMotionSpinner.getEditor() instanceof JSpinner.NumberEditor ne) ne.getFormat().setGroupingUsed(false);
        motionPanel.add(lblMotion, BorderLayout.NORTH);
        motionPanel.add(videoMotionSpinner, BorderLayout.CENTER);

        samplerRow.add(stepsPanel);
        samplerRow.add(cfgPanel);
        samplerRow.add(motionPanel);
        leftPanel.add(samplerRow);
        leftPanel.add(Box.createVerticalStrut(18));

        // 6. Autonomous Action
        btnAutoPilot = new JButton("🤖 Agent: Auto-Pilot (End-to-End)");
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

        addSceneBtn = new JButton("➕ Add Scene");
        addSceneBtn.addActionListener(e -> showEditSceneDialog(null, true));
        toolbar.add(addSceneBtn);

        editSceneBtn = new JButton("✏️ Edit");
        editSceneBtn.addActionListener(e -> {
            Scene selected = timelineListView.getSelectedValue();
            if (selected != null) {
                showEditSceneDialog(selected, false);
            } else {
                logToConsole("Select a scene to edit.");
            }
        });
        toolbar.add(editSceneBtn);

        visualFilterBtn = new JButton("🎨 Visual Filter (OpenCV)");
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

        playVideoBtn = new JButton("▶ Play Video");
        playVideoBtn.addActionListener(e -> handlePreviewSceneVideo());
        toolbar.add(playVideoBtn);

        moveUpBtn = new JButton("⬆ Up");
        moveUpBtn.addActionListener(e -> handleMoveScene(-1));
        toolbar.add(moveUpBtn);

        moveDownBtn = new JButton("⬇ Down");
        moveDownBtn.addActionListener(e -> handleMoveScene(1));
        toolbar.add(moveDownBtn);

        deleteBtn = new JButton("🗑 Delete");
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

        videoRightTabbedPane.addTab("🎞️ Timeline Sequence", timelineTabPanel);

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

        videoRightTabbedPane.addTab("🖼️ Scene Preview", previewTabPanel);

        // TAB 3: Storyboard JSON
        storyboardJsonArea = new JTextArea("[]");
        storyboardJsonArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        storyboardJsonArea.setEditable(false);
        JScrollPane jsonScroll = new JScrollPane(storyboardJsonArea);
        jsonScroll.setOpaque(false);
        jsonScroll.getViewport().setOpaque(false);
        jsonScroll.setBorder(BorderFactory.createEmptyBorder());
        videoRightTabbedPane.addTab("📝 Storyboard JSON", jsonScroll);

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

        btnGenerateScenes = new JButton("🚀 1. Generate All Scenes");
        btnGenerateScenes.setFont(new Font("SansSerif", Font.BOLD, 14));
        btnGenerateScenes.putClientProperty("Button.background", ThemeManager.getAccentColor());
        btnGenerateScenes.putClientProperty("Button.foreground", Color.WHITE);
        btnGenerateScenes.addActionListener(e -> handleVideoGeneration());

        btnMasterRender = new JButton("🎬 2. Stitch Videos (Master Render)");
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

    private void logToConsole(String message) {
        SwingUtilities.invokeLater(() -> {
            String time = LocalTime.now().format(TIME_FORMATTER);
            videoConsoleArea.append("[" + time + "] " + message + "\n");
            videoConsoleArea.setCaretPosition(videoConsoleArea.getDocument().getLength());
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
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "🎨 OpenCV Visual Enhancement - Scene " + scene.getSceneId(), Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setSize(680, 580);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel topHeader = new JPanel(new BorderLayout(5, 5));
        topHeader.setBorder(new EmptyBorder(12, 16, 6, 16));
        JLabel title = new JLabel("OpenCV Frame Enhancement: " + scene.getSceneId());
        title.setFont(new Font("SansSerif", Font.BOLD, 15));
        JLabel desc = new JLabel("Adjust real-time contrast (alpha) and brightness (beta) applied during trimming and master render.");
        desc.setFont(new Font("SansSerif", Font.PLAIN, 11));
        desc.setForeground(ThemeManager.getTextSecondaryColor());
        topHeader.add(title, BorderLayout.NORTH);
        topHeader.add(desc, BorderLayout.SOUTH);
        dialog.add(topHeader, BorderLayout.NORTH);

        JLabel previewLabel = new JLabel("Loading OpenCV Frame Preview...", SwingConstants.CENTER);
        previewLabel.setFont(new Font("SansSerif", Font.ITALIC, 13));
        previewLabel.setOpaque(true);
        previewLabel.setBackground(ThemeManager.isDarkMode() ? new Color(15, 17, 22) : new Color(235, 238, 242));
        previewLabel.setBorder(BorderFactory.createLineBorder(ThemeManager.getBorderColor(), 1));

        JScrollPane previewScroll = new JScrollPane(previewLabel);
        previewScroll.setBorder(new EmptyBorder(0, 16, 0, 16));
        dialog.add(previewScroll, BorderLayout.CENTER);

        CardPanel controlsCard = new CardPanel();
        controlsCard.setLayout(new BoxLayout(controlsCard, BoxLayout.Y_AXIS));
        controlsCard.setBorder(new EmptyBorder(12, 16, 14, 16));

        int initialAlphaInt = (int) Math.round(scene.getContrast() * 100.0);
        if (initialAlphaInt < 50 || initialAlphaInt > 250) initialAlphaInt = 100;
        JSlider contrastSlider = new JSlider(50, 250, initialAlphaInt);
        JLabel contrastValLbl = new JLabel(String.format(java.util.Locale.US, "Contrast: %.2fx", initialAlphaInt / 100.0));
        contrastValLbl.setFont(new Font("SansSerif", Font.BOLD, 12));

        JPanel contrastRow = new JPanel(new BorderLayout(8, 0));
        contrastRow.setOpaque(false);
        contrastRow.add(contrastValLbl, BorderLayout.WEST);
        contrastRow.add(contrastSlider, BorderLayout.CENTER);
        controlsCard.add(contrastRow);
        controlsCard.add(Box.createVerticalStrut(8));

        int initialBetaInt = (int) Math.round(scene.getBrightness());
        if (initialBetaInt < -100 || initialBetaInt > 100) initialBetaInt = 0;
        JSlider brightnessSlider = new JSlider(-100, 100, initialBetaInt);
        JLabel brightnessValLbl = new JLabel(String.format(java.util.Locale.US, "Brightness: %+d", initialBetaInt));
        brightnessValLbl.setFont(new Font("SansSerif", Font.BOLD, 12));

        JPanel brightnessRow = new JPanel(new BorderLayout(8, 0));
        brightnessRow.setOpaque(false);
        brightnessRow.add(brightnessValLbl, BorderLayout.WEST);
        brightnessRow.add(brightnessSlider, BorderLayout.CENTER);
        controlsCard.add(brightnessRow);
        controlsCard.add(Box.createVerticalStrut(12));

        Runnable updatePreview = () -> {
            double alpha = contrastSlider.getValue() / 100.0;
            double beta = brightnessSlider.getValue();
            contrastValLbl.setText(String.format(java.util.Locale.US, "Contrast: %.2fx", alpha));
            brightnessValLbl.setText(String.format(java.util.Locale.US, "Brightness: %+d", (int) beta));

            Thread.ofVirtual().start(() -> {
                try {
                    BufferedImage img = video4jEditorService.applyBasicEnhancementAwt(scene, alpha, beta);
                    SwingUtilities.invokeLater(() -> {
                        if (img != null) {
                            int targetW = Math.max(320, previewLabel.getWidth() - 20);
                            int targetH = Math.max(240, previewLabel.getHeight() - 20);
                            double scale = Math.min((double) targetW / img.getWidth(), (double) targetH / img.getHeight());
                            int sw = Math.max(1, (int) (img.getWidth() * scale));
                            int sh = Math.max(1, (int) (img.getHeight() * scale));
                            Image scaled = img.getScaledInstance(sw, sh, Image.SCALE_SMOOTH);
                            previewLabel.setIcon(new ImageIcon(scaled));
                            previewLabel.setText("");
                        } else {
                            previewLabel.setIcon(null);
                            previewLabel.setText("No video or image available yet for scene " + scene.getSceneId());
                        }
                    });
                } catch (Exception ex) {
                    logger.warn("Preview update error: {}", ex.getMessage());
                }
            });
        };

        contrastSlider.addChangeListener(e -> updatePreview.run());
        brightnessSlider.addChangeListener(e -> updatePreview.run());

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnRow.setOpaque(false);

        JButton btnReset = new JButton("Reset");
        btnReset.addActionListener(e -> {
            contrastSlider.setValue(100);
            brightnessSlider.setValue(0);
            updatePreview.run();
        });
        btnRow.add(btnReset);

        JButton btnCancel = new JButton("Cancel");
        btnCancel.addActionListener(e -> dialog.dispose());
        btnRow.add(btnCancel);

        JButton btnApply = new JButton("Apply to Scene");
        btnApply.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnApply.putClientProperty("Button.background", ThemeManager.getAccentColor());
        btnApply.putClientProperty("Button.foreground", Color.WHITE);
        btnApply.addActionListener(e -> {
            double finalAlpha = contrastSlider.getValue() / 100.0;
            double finalBeta = brightnessSlider.getValue();
            scene.setContrast(finalAlpha);
            scene.setBrightness(finalBeta);
            timelineListView.repaint();
            updateStoryboardJson();
            updatePreviewForScene(scene);
            logToConsole(String.format(java.util.Locale.US, "Applied OpenCV enhancement to %s: Contrast=%.2fx, Brightness=%+d",
                    scene.getSceneId(), finalAlpha, (int) finalBeta));
            dialog.dispose();
        });
        btnRow.add(btnApply);
        controlsCard.add(btnRow);

        dialog.add(controlsCard, BorderLayout.SOUTH);

        updatePreview.run();
        dialog.setVisible(true);
    }

    private void showEditSceneDialog(Scene targetScene, boolean isNew) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), isNew ? "Add New Scene" : "Edit Scene: " + targetScene.getSceneId(), Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setSize(520, 480);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(new EmptyBorder(16, 20, 10, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 6, 6, 6);

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.2;
        formPanel.add(new JLabel("Scene ID:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.8;
        JTextField idField = new JTextField(isNew ? "S" + (timelineListModel.size() + 1) : targetScene.getSceneId());
        formPanel.add(idField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        formPanel.add(new JLabel("Visual Prompt:"), gbc);
        gbc.gridx = 1;
        JTextArea promptArea = new JTextArea(targetScene != null && targetScene.getPrompt() != null ? targetScene.getPrompt() : "", 3, 20);
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        formPanel.add(new JScrollPane(promptArea), gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        formPanel.add(new JLabel("Narration Text:"), gbc);
        gbc.gridx = 1;
        JTextArea narrationArea = new JTextArea(targetScene != null && targetScene.getNarrationText() != null ? targetScene.getNarrationText() : "", 2, 20);
        narrationArea.setLineWrap(true);
        narrationArea.setWrapStyleWord(true);
        formPanel.add(new JScrollPane(narrationArea), gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        formPanel.add(new JLabel("Duration (sec):"), gbc);
        gbc.gridx = 1;
        int currentDur = 5;
        if (targetScene != null && targetScene.getEndFrame() > targetScene.getStartFrame()) {
            currentDur = Math.max(1, (targetScene.getEndFrame() - targetScene.getStartFrame()) / 24);
        }
        JSpinner durationSpinner = new JSpinner(new SpinnerNumberModel(currentDur, 1, 60, 1));
        formPanel.add(durationSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        formPanel.add(new JLabel("CFG Scale:"), gbc);
        gbc.gridx = 1;
        double cfg = targetScene != null ? targetScene.getCfgScale() : 1.0;
        JSpinner cfgSpinner = new JSpinner(new SpinnerNumberModel(cfg, 0.5, 20.0, 0.5));
        formPanel.add(cfgSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 5;
        formPanel.add(new JLabel("Steps:"), gbc);
        gbc.gridx = 1;
        int steps = targetScene != null ? targetScene.getSteps() : 30;
        JSpinner stepsSpinner = new JSpinner(new SpinnerNumberModel(steps, 10, 100, 1));
        formPanel.add(stepsSpinner, gbc);

        dialog.add(formPanel, BorderLayout.CENTER);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        btnRow.setBorder(new EmptyBorder(0, 0, 10, 20));
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dialog.dispose());
        btnRow.add(cancelBtn);

        JButton saveBtn = new JButton(isNew ? "Add" : "Save");
        saveBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        saveBtn.putClientProperty("Button.background", ThemeManager.getAccentColor());
        saveBtn.putClientProperty("Button.foreground", Color.WHITE);
        saveBtn.addActionListener(e -> {
            Scene sc = isNew ? new Scene() : targetScene;
            String sid = idField.getText().trim().isEmpty() ? "S1" : idField.getText().trim();
            sc.setSceneId(sid);
            sc.setPrompt(promptArea.getText().trim());
            sc.setNarrationText(narrationArea.getText().trim());
            int dur = (Integer) durationSpinner.getValue();
            sc.setStartFrame(0);
            sc.setEndFrame(dur * 24);
            sc.setCfgScale(((Number) cfgSpinner.getValue()).doubleValue());
            sc.setSteps((Integer) stepsSpinner.getValue());

            if (isNew) {
                sc.setWidth((Integer) videoWidthSpinner.getValue());
                sc.setHeight((Integer) videoHeightSpinner.getValue());
                timelineListModel.addElement(sc);
                logToConsole("Added new Scene: " + sc.getSceneId());
            } else {
                timelineListView.repaint();
                logToConsole("Updated Scene: " + sc.getSceneId());
            }
            updateStoryboardJson();
            dialog.dispose();
        });
        btnRow.add(saveBtn);

        dialog.add(btnRow, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void handleDeconstructScript() {
        String idea = promptSubjectField.getText().trim();
        if (idea.isEmpty()) {
            logToConsole("Master script is empty!");
            return;
        }

        if (gemma4Service != null && !gemma4Service.isGemmaAvailable()) {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "The local Gemma-3-4B AI model is not downloaded yet.\n\n" +
                            "Would you like to download the Gemma-3-4B model (~3 GB) now to unlock intelligent AI script deconstruction, visual prompt engineering, and scene timing?\n\n" +
                            "(Select 'No' to use standard rule-based segmentation)",
                    "Gemma AI Storyboard Deconstruction",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );

            if (choice == JOptionPane.YES_OPTION) {
                downloadGemmaAndDeconstruct(idea);
                return;
            } else if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
                return;
            }
        }

        executeDeconstructScriptTask(idea);
    }

    private void downloadGemmaAndDeconstruct(String idea) {
        setButtonsDisabled(true);
        videoProgressBar.setValue(0);
        videoProgressBar.setVisible(true);
        logToConsole("Downloading Gemma-3-4B model from Hugging Face...");

        gemma4Service.downloadGemmaModel(
                (percent, msg) -> SwingUtilities.invokeLater(() -> {
                    videoProgressBar.setValue((int) (percent * 100));
                    logToConsole(msg);
                }),
                () -> SwingUtilities.invokeLater(() -> {
                    videoProgressBar.setVisible(false);
                    logToConsole("✅ Gemma model downloaded successfully! Starting AI script deconstruction...");
                    executeDeconstructScriptTask(idea);
                }),
                (errorMsg, ex) -> SwingUtilities.invokeLater(() -> {
                    videoProgressBar.setVisible(false);
                    setButtonsDisabled(false);
                    logToConsole("❌ Gemma download failed: " + errorMsg);
                    JOptionPane.showMessageDialog(this, "Could not download Gemma AI model:\n" + errorMsg, "Gemma Download Failed", JOptionPane.ERROR_MESSAGE);
                })
        );
    }

    private void executeDeconstructScriptTask(String idea) {
        setButtonsDisabled(true);
        boolean usingGemma = gemma4Service != null && gemma4Service.isGemmaAvailable();
        logToConsole(usingGemma ? "🤖 Gemma AI: Analyzing script and generating storyboard..." : "Analyzing script...");

        Thread.ofVirtual().start(() -> {
            try {
                String jsonStr = null;
                if (gemma4Service != null && gemma4Service.isGemmaAvailable()) {
                    try {
                        jsonStr = gemma4Service.generateScript(idea);
                    } catch (Exception ex) {
                        logger.warn("Gemma script generation threw: {}. Falling back to rule-based parser.", ex.getMessage());
                    }
                }

                List<Scene> scenes = parseStoryboardJson(jsonStr, idea);
                int width = (Integer) videoWidthSpinner.getValue();
                int height = (Integer) videoHeightSpinner.getValue();
                double cfg = ((Number) videoCfgSpinner.getValue()).doubleValue();
                int steps = (Integer) videoStepsSpinner.getValue();
                int motion = (Integer) videoMotionSpinner.getValue();

                for (Scene sc : scenes) {
                    sc.setWidth(width);
                    sc.setHeight(height);
                    sc.setCfgScale(cfg);
                    sc.setSteps(steps);
                    sc.setMotionBucketId(motion);
                }

                SwingUtilities.invokeLater(() -> {
                    setButtonsDisabled(false);
                    timelineListModel.clear();
                    for (Scene sc : scenes) {
                        timelineListModel.addElement(sc);
                    }
                    updateStoryboardJson();
                    if (!scenes.isEmpty()) {
                        timelineListView.setSelectedIndex(0);
                        updatePreviewForScene(scenes.get(0));
                    }
                    boolean usedGemma = usingGemma && !scenes.isEmpty();
                    logToConsole(usedGemma ? "✅ Gemma AI: Successfully deconstructed script into " + scenes.size() + " scenes!"
                            : "Script deconstructed into " + scenes.size() + " scenes.");
                });
            } catch (Exception e) {
                logger.error("Deconstruct script task failed", e);
                SwingUtilities.invokeLater(() -> {
                    setButtonsDisabled(false);
                    logToConsole("Agent failed: " + e.getMessage());
                    JOptionPane.showMessageDialog(this, "Failed to deconstruct script:\n" + e.getMessage(), "Agent Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private void handleAutoPilot() {
        String idea = promptSubjectField.getText().trim();
        if (idea.isEmpty()) {
            logToConsole("Master script is empty! Agent needs a concept.");
            return;
        }

        setButtonsDisabled(true);
        videoProgressBar.setValue(0);
        videoProgressBar.setVisible(true);
        logToConsole("Agent Auto-Pilot engaged. Phase 1: Script Deconstruction...");

        Thread.ofVirtual().start(() -> {
            try {
                // PHASE 1: Director Agent
                logToConsole("Director Agent: Generating storyboard JSON...");
                String jsonStr = null;
                try {
                    jsonStr = gemma4Service.generateScript(idea);
                } catch (Exception ex) {
                    logger.warn("Gemma script generation threw: {}. Using fallback deconstruction.", ex.getMessage());
                }

                List<Scene> generatedScenes = parseStoryboardJson(jsonStr, idea);
                if (generatedScenes.isEmpty()) {
                    throw new Exception("Unable to deconstruct script into timeline scenes.");
                }

                int apWidth = (Integer) videoWidthSpinner.getValue();
                int apHeight = (Integer) videoHeightSpinner.getValue();
                double apCfg = ((Number) videoCfgSpinner.getValue()).doubleValue();
                int apSteps = (Integer) videoStepsSpinner.getValue();
                int apMotion = (Integer) videoMotionSpinner.getValue();

                String apSpeaker = speakerImageField.getText().trim();
                for (Scene sc : generatedScenes) {
                    sc.setWidth(apWidth);
                    sc.setHeight(apHeight);
                    sc.setCfgScale(apCfg);
                    sc.setSteps(apSteps);
                    sc.setMotionBucketId(apMotion);
                    sc.setSpeakerImagePath(apSpeaker.isEmpty() ? null : apSpeaker);
                }

                SwingUtilities.invokeLater(() -> {
                    timelineListModel.clear();
                    for (Scene sc : generatedScenes) {
                        timelineListModel.addElement(sc);
                    }
                    updateStoryboardJson();
                });

                // PHASE 1.5: Narration Agent (TTS)
                File narrationDir = new File("output", "narration");
                if (!narrationDir.exists()) narrationDir.mkdirs();
                int narrationOk = 0;
                for (int ni = 0; ni < generatedScenes.size(); ni++) {
                    Scene ns = generatedScenes.get(ni);
                    final int currNi = ni + 1;
                    logToConsole("Narration Agent (Scene " + currNi + "/" + generatedScenes.size() + "): " + ns.getSceneId());
                    if (generateNarrationAudio(ns, narrationDir)) narrationOk++;
                }
                logToConsole("Narration: " + narrationOk + "/" + generatedScenes.size() + " scenes have audio ready.");

                // PHASE 2: Generation Agent (ComfyUI)
                logToConsole("Checking ComfyUI server status...");
                if (lifecycleService != null && !lifecycleService.isHealthy()) {
                    logToConsole("ComfyUI server is offline. Attempting to start...");
                    lifecycleService.start();
                    int maxWait = 90;
                    boolean healthy = false;
                    for (int w = 0; w < maxWait; w++) {
                        if (lifecycleService.isHealthy()) {
                            healthy = true;
                            break;
                        }
                        Thread.sleep(1000);
                    }
                    if (!healthy) {
                        throw new Exception("ComfyUI server could not be started or is not healthy. Aborting Auto-Pilot.");
                    }
                }

                for (int i = 0; i < generatedScenes.size(); i++) {
                    Scene scene = generatedScenes.get(i);
                    final int currentIdx = i + 1;
                    final int totalScenes = generatedScenes.size();

                    File output = null;
                    int maxRetries = 3;
                    boolean success = false;

                    for (int attempt = 1; attempt <= maxRetries; attempt++) {
                        final int att = attempt;
                        SwingUtilities.invokeLater(() -> {
                            videoProgressBar.setValue((int) (((double) currentIdx / totalScenes) * 100));
                        });
                        logToConsole("Generation Agent (Scene " + currentIdx + "/" + totalScenes + "): " + scene.getSceneId() + (att > 1 ? " (Attempt " + att + ")" : ""));

                        try {
                            output = comfyPipelineService.generateSceneStrict(scene).join();
                            if (output != null && output.exists() && output.length() > 1024) {
                                success = true;
                                break;
                            } else {
                                Thread.sleep(2000);
                            }
                        } catch (Exception e) {
                            Thread.sleep(2000);
                        }
                    }

                    if (!success) {
                        throw new Exception("Generation Agent failed on Scene: " + scene.getSceneId() + " after " + maxRetries + " attempts.");
                    }

                    if (output != null && output.exists()) {
                        scene.setVideoPath(output.getAbsolutePath());
                    }
                    SwingUtilities.invokeLater(() -> {
                        timelineListView.repaint();
                        updatePreviewForScene(scene);
                    });
                }

                // PHASE 3: Editor Agent (FFmpeg)
                logToConsole("Editor Agent: Stitching video segments via FFmpeg...");
                File exportFile = video4jEditorService.executeMasterRender(generatedScenes);

                SwingUtilities.invokeLater(() -> {
                    videoProgressBar.setVisible(false);
                    setButtonsDisabled(false);
                    logToConsole("Auto-Pilot Complete! Master Video saved as: " + exportFile.getName());
                    JOptionPane.showMessageDialog(this, "Master video render complete!\nSaved to: " + exportFile.getAbsolutePath(), "Auto-Pilot Complete", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception ex) {
                logger.error("Auto-Pilot task failed", ex);
                SwingUtilities.invokeLater(() -> {
                    videoProgressBar.setVisible(false);
                    setButtonsDisabled(false);
                    logToConsole("Auto-Pilot Failed: " + ex.getMessage());
                    JOptionPane.showMessageDialog(this, "Auto-Pilot failed:\n" + ex.getMessage(), "Auto-Pilot Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private void handleVideoGeneration() {
        List<Scene> scenes = new ArrayList<>();
        for (int i = 0; i < timelineListModel.size(); i++) {
            scenes.add(timelineListModel.get(i));
        }
        if (scenes.isEmpty()) {
            logToConsole("Timeline is empty! Add scenes first.");
            return;
        }

        int vgWidth = (Integer) videoWidthSpinner.getValue();
        int vgHeight = (Integer) videoHeightSpinner.getValue();
        String vgSpeaker = speakerImageField.getText().trim();
        for (Scene sc : scenes) {
            sc.setWidth(vgWidth);
            sc.setHeight(vgHeight);
            sc.setSpeakerImagePath(vgSpeaker.isEmpty() ? null : vgSpeaker);
        }

        setButtonsDisabled(true);
        videoProgressBar.setValue(0);
        videoProgressBar.setVisible(true);

        Thread.ofVirtual().start(() -> {
            try {
                logToConsole("Checking ComfyUI server status...");
                if (lifecycleService != null && !lifecycleService.isHealthy()) {
                    logToConsole("ComfyUI server is offline. Starting...");
                    lifecycleService.start();
                    int maxWait = 90;
                    boolean healthy = false;
                    for (int w = 0; w < maxWait; w++) {
                        if (lifecycleService.isHealthy()) {
                            healthy = true;
                            break;
                        }
                        Thread.sleep(1000);
                    }
                    if (!healthy) {
                        throw new Exception("ComfyUI server could not be started or is not healthy.");
                    }
                }

                File narrationDir = new File("output", "narration");
                if (!narrationDir.exists()) narrationDir.mkdirs();
                for (int ni = 0; ni < scenes.size(); ni++) {
                    Scene s = scenes.get(ni);
                    final int currNi = ni + 1;
                    logToConsole("Generating narration (" + currNi + "/" + scenes.size() + "): " + s.getSceneId());
                    generateNarrationAudio(s, narrationDir);
                }

                for (int i = 0; i < scenes.size(); i++) {
                    Scene scene = scenes.get(i);
                    final int currentIdx = i + 1;
                    final int totalScenes = scenes.size();

                    File output = null;
                    int maxRetries = 3;
                    boolean success = false;
                    for (int attempt = 1; attempt <= maxRetries; attempt++) {
                        final int att = attempt;
                        SwingUtilities.invokeLater(() -> {
                            videoProgressBar.setValue((int) (((double) currentIdx / totalScenes) * 100));
                        });
                        logToConsole("Generating Scene (" + currentIdx + "/" + totalScenes + "): " + scene.getSceneId() + (att > 1 ? " (Attempt " + att + ")" : ""));

                        try {
                            output = comfyPipelineService.generateSceneStrict(scene).join();
                            if (output != null && output.exists() && output.length() > 1024) {
                                success = true;
                                break;
                            } else {
                                Thread.sleep(2000);
                            }
                        } catch (Exception e) {
                            Thread.sleep(2000);
                        }
                    }

                    if (!success) {
                        throw new Exception("Could not generate video for Scene " + scene.getSceneId() + " after " + maxRetries + " attempts.");
                    }

                    if (output != null && output.exists()) {
                        scene.setVideoPath(output.getAbsolutePath());
                    }
                    SwingUtilities.invokeLater(() -> {
                        timelineListView.repaint();
                        updatePreviewForScene(scene);
                    });
                }

                SwingUtilities.invokeLater(() -> {
                    videoProgressBar.setVisible(false);
                    setButtonsDisabled(false);
                    logToConsole("All " + scenes.size() + " scene videos generated! Click 'Stitch Videos' to combine.");
                });
            } catch (Exception ex) {
                logger.error("Video generation failed", ex);
                SwingUtilities.invokeLater(() -> {
                    videoProgressBar.setVisible(false);
                    setButtonsDisabled(false);
                    logToConsole("Generation failed: " + ex.getMessage());
                    JOptionPane.showMessageDialog(this, "Video generation failed:\n" + ex.getMessage(), "Generation Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private void handleMasterRender() {
        List<Scene> scenes = new ArrayList<>();
        for (int i = 0; i < timelineListModel.size(); i++) {
            scenes.add(timelineListModel.get(i));
        }
        if (scenes.isEmpty()) {
            logToConsole("Timeline is empty!");
            return;
        }

        setButtonsDisabled(true);
        videoProgressBar.setValue(50);
        videoProgressBar.setVisible(true);

        Thread.ofVirtual().start(() -> {
            try {
                logToConsole("Checking scene videos...");

                List<Scene> needsRegen = new ArrayList<>();
                for (Scene scene : scenes) {
                    File vf = scene.getVideoPath() == null ? null : new File(scene.getVideoPath());
                    if (vf == null || !vf.exists() || vf.length() < 1024) {
                        File[] roots = new File[]{
                                new File(System.getProperty("user.dir")),
                                new File(new File("").getAbsolutePath()),
                                new File(configService != null ? configService.getResolvedOutputDir() : "output"),
                                new File(new File(configService != null ? configService.getResolvedOutputDir() : "output"), "video")
                        };
                        String[] patterns = new String[]{
                                "scene_" + scene.getSceneId() + "_final.mp4",
                                "scene_" + scene.getSceneId() + "_simulated.mp4",
                                "videoarchitect_" + scene.getSceneId() + "_00001_.mp4",
                                "videoarchitect_" + scene.getSceneId() + ".mp4"
                        };
                        File found = null;
                        outer:
                        for (File r : roots) {
                            if (r == null || !r.isDirectory()) continue;
                            for (String p : patterns) {
                                File candidate = new File(r, p);
                                if (candidate.exists() && candidate.length() >= 1024) { found = candidate; break outer; }
                            }
                        }
                        if (found != null) {
                            logger.info("[VideoArchitect] Re-attached scene {} video: {}", scene.getSceneId(), found.getAbsolutePath());
                            scene.setVideoPath(found.getAbsolutePath());
                        } else {
                            needsRegen.add(scene);
                        }
                    }
                }

                if (!needsRegen.isEmpty()) {
                    logToConsole("Regenerating " + needsRegen.size() + " missing scene(s)...");
                    for (int ri = 0; ri < needsRegen.size(); ri++) {
                        Scene s = needsRegen.get(ri);
                        final int currRi = ri + 1;
                        logToConsole("Regenerating scene " + currRi + "/" + needsRegen.size() + ": " + s.getSceneId() + "...");
                        File regen = comfyPipelineService.generateSceneStrict(s).join();
                        if (regen != null && regen.exists() && regen.length() > 1024) {
                            s.setVideoPath(regen.getAbsolutePath());
                        } else {
                            throw new Exception("Could not regenerate video for scene: " + s.getSceneId());
                        }
                    }
                }

                logToConsole("Stitching video segments via FFmpeg...");
                File exportFile = video4jEditorService.executeMasterRender(scenes);

                SwingUtilities.invokeLater(() -> {
                    videoProgressBar.setVisible(false);
                    setButtonsDisabled(false);
                    logToConsole("Stitching Complete! Saved as " + exportFile.getName());
                    JOptionPane.showMessageDialog(this, "Master render completed successfully!\nSaved to: " + exportFile.getAbsolutePath(), "Master Render Complete", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception ex) {
                logger.error("Master render task failed", ex);
                SwingUtilities.invokeLater(() -> {
                    videoProgressBar.setVisible(false);
                    setButtonsDisabled(false);
                    logToConsole("Render Failed: " + ex.getMessage());
                    JOptionPane.showMessageDialog(this, "Master render failed:\n" + ex.getMessage(), "Render Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private boolean generateNarrationAudio(Scene scene, File outputDir) {
        if (ttsService == null) {
            return false;
        }
        String narration = scene.getNarrationText();
        if (narration == null || narration.trim().isEmpty()) {
            return false;
        }
        if (!outputDir.exists()) outputDir.mkdirs();
        File audioFile = new File(outputDir, "narration_" + scene.getSceneId() + ".wav");
        if (audioFile.exists() && audioFile.length() > 0) {
            scene.setAudioPath(audioFile.getAbsolutePath());
            return true;
        }
        try {
            ttsService.generateSpeech(narration, audioFile.getAbsolutePath());
            if (audioFile.exists() && audioFile.length() > 0) {
                scene.setAudioPath(audioFile.getAbsolutePath());
                return true;
            }
        } catch (Exception ttsEx) {
            logger.error("TTS generation failed for scene {}: {}", scene.getSceneId(), ttsEx.getMessage());
        }
        return false;
    }

    /**
     * Parses the Gemma / LLM storyboard response or gracefully falls back to text segmentation.
     * Reflection-tested in VideoArchitectFeaturesTest.
     */
    private List<Scene> parseStoryboardJson(String rawResponse, String originalIdea) {
        List<Scene> scenes = new ArrayList<>();
        if (rawResponse != null && !rawResponse.trim().isEmpty()) {
            try {
                String clean = rawResponse.replaceAll("```json", "").replaceAll("```", "").replace("\u0000", "").trim();
                int startArr = clean.indexOf('[');
                int endArr = clean.lastIndexOf(']');

                if (startArr >= 0 && endArr > startArr) {
                    clean = clean.substring(startArr, endArr + 1);
                    JSONArray arr = new JSONArray(clean);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        Scene scene = parseSceneObject(obj, i + 1);
                        if (scene != null) {
                            scenes.add(scene);
                        }
                    }
                } else {
                    int startObj = clean.indexOf('{');
                    int endObj = clean.lastIndexOf('}');
                    if (startObj >= 0 && endObj > startObj) {
                        JSONObject rootObj = new JSONObject(clean.substring(startObj, endObj + 1));
                        JSONArray arr = rootObj.optJSONArray("scenes");
                        if (arr == null) arr = rootObj.optJSONArray("storyboard");
                        if (arr == null) arr = rootObj.optJSONArray("timeline");
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject obj = arr.getJSONObject(i);
                                Scene scene = parseSceneObject(obj, i + 1);
                                if (scene != null) {
                                    scenes.add(scene);
                                }
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                logger.warn("JSON parsing failed on Gemma response: {}. Falling back to rule-based segmentation.", ex.getMessage());
            }
        }

        if (scenes.isEmpty() && originalIdea != null && !originalIdea.trim().isEmpty()) {
            scenes = splitScriptFallback(originalIdea);
        }

        return scenes;
    }

    private Scene parseSceneObject(JSONObject obj, int fallbackIndex) {
        String id = obj.optString("scene_id", obj.optString("id", "S" + fallbackIndex));
        String prompt = obj.optString("visual_prompt", obj.optString("prompt", obj.optString("description", "")));
        String narration = obj.optString("narration_text", obj.optString("narration", obj.optString("voiceover", "")));
        int dur = obj.optInt("duration_seconds", obj.optInt("duration", 5));
        if (dur <= 0) dur = 5;

        if (prompt.isEmpty() && narration.isEmpty()) return null;

        int targetW = (videoWidthSpinner != null) ? (Integer) videoWidthSpinner.getValue() : 1920;
        int targetH = (videoHeightSpinner != null) ? (Integer) videoHeightSpinner.getValue() : 1080;

        Scene scene = new Scene();
        scene.setSceneId(id);
        scene.setPrompt(prompt.isEmpty() ? narration : prompt);
        scene.setNarrationText(narration.isEmpty() ? "" : narration);
        scene.setStartFrame(0);
        scene.setEndFrame(dur * 24);
        scene.setWidth(targetW);
        scene.setHeight(targetH);
        scene.setCfgScale(1.0);
        scene.setSteps(30);
        return scene;
    }

    /**
     * Smart rule-based fallback that decomposes user text into coherent visual scenes.
     * Reflection-tested in VideoArchitectFeaturesTest.
     */
    private List<Scene> splitScriptFallback(String idea) {
        List<Scene> fallbackScenes = new ArrayList<>();
        String[] sentences = idea.split("(?<=[.!?\\n])\\s+");
        int targetW = (videoWidthSpinner != null) ? (Integer) videoWidthSpinner.getValue() : 1920;
        int targetH = (videoHeightSpinner != null) ? (Integer) videoHeightSpinner.getValue() : 1080;
        int count = 1;
        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.length() > 2) {
                Scene sc = new Scene();
                sc.setSceneId("S" + count++);
                sc.setPrompt("Cinematic shot, 4k resolution, high quality, " + trimmed);
                sc.setNarrationText(trimmed);
                int duration = Math.min(8, Math.max(3, (trimmed.split("\\s+").length / 2) + 2));
                sc.setStartFrame(0);
                sc.setEndFrame(duration * 24);
                sc.setWidth(targetW);
                sc.setHeight(targetH);
                sc.setCfgScale(1.0);
                sc.setSteps(30);
                fallbackScenes.add(sc);
            }
        }
        if (fallbackScenes.isEmpty() && !idea.trim().isEmpty()) {
            Scene single = new Scene("S1", "Cinematic shot, " + idea.trim(), 0, 120, "", "");
            single.setWidth(targetW);
            single.setHeight(targetH);
            single.setNarrationText(idea.trim());
            fallbackScenes.add(single);
        }
        return fallbackScenes;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        SwingUtilities.updateComponentTreeUI(this);
        updateTheme(ThemeManager.isDarkMode());
    }

    public void updateTheme(boolean darkMode) {
        SwingUtilities.updateComponentTreeUI(this);
        if (videoPresetLabel != null) {
            Color c = UIManager.getColor("PromptLab.presetForeground");
            if (c != null) videoPresetLabel.setForeground(c);
        }
        if (videoPreviewLabel != null) {
            videoPreviewLabel.setBackground(UIManager.getColor("TextField.background"));
        }
        if (timelineListView != null) {
            timelineListView.setBackground(darkMode ? new Color(15, 17, 22) : Color.WHITE);
        }
        if (timelineScroll != null && timelineScroll.getViewport() != null) {
            timelineScroll.getViewport().setBackground(darkMode ? new Color(15, 17, 22) : Color.WHITE);
        }
        if (videoConsoleArea != null) {
            if (darkMode) {
                videoConsoleArea.setBackground(UIManager.getColor("TextArea.background"));
                videoConsoleArea.setForeground(UIManager.getColor("TextArea.foreground"));
            } else {
                videoConsoleArea.setBackground(new Color(245, 247, 250));
                videoConsoleArea.setForeground(new Color(30, 30, 30));
            }
        }
        if (storyboardJsonArea != null) {
            if (darkMode) {
                storyboardJsonArea.setBackground(UIManager.getColor("TextArea.background"));
                storyboardJsonArea.setForeground(UIManager.getColor("TextArea.foreground"));
            } else {
                storyboardJsonArea.setBackground(new Color(245, 247, 250));
                storyboardJsonArea.setForeground(new Color(30, 30, 30));
            }
        }
        if (btnSuggestSubject != null) {
            btnSuggestSubject.putClientProperty("Button.background", ThemeManager.getAccentColor());
            btnSuggestSubject.putClientProperty("Button.foreground", Color.WHITE);
        }
        if (btnAutoPilot != null) {
            btnAutoPilot.putClientProperty("Button.background", new Color(255, 204, 0));
            btnAutoPilot.putClientProperty("Button.foreground", Color.BLACK);
        }
        if (btnGenerateScenes != null) {
            btnGenerateScenes.putClientProperty("Button.background", ThemeManager.getAccentColor());
            btnGenerateScenes.putClientProperty("Button.foreground", Color.WHITE);
        }
        if (btnMasterRender != null) {
            btnMasterRender.putClientProperty("Button.background", new Color(255, 204, 0));
            btnMasterRender.putClientProperty("Button.foreground", Color.BLACK);
        }
        revalidate();
        repaint();
    }

    private void setButtonsDisabled(boolean disabled) {
        SwingUtilities.invokeLater(() -> {
            if (btnSuggestSubject != null) btnSuggestSubject.setEnabled(!disabled);
            if (btnAutoPilot != null) btnAutoPilot.setEnabled(!disabled);
            if (btnGenerateScenes != null) btnGenerateScenes.setEnabled(!disabled);
            if (btnMasterRender != null) btnMasterRender.setEnabled(!disabled);
            if (addSceneBtn != null) addSceneBtn.setEnabled(!disabled);
            if (editSceneBtn != null) editSceneBtn.setEnabled(!disabled);
            if (moveUpBtn != null) moveUpBtn.setEnabled(!disabled);
            if (moveDownBtn != null) moveDownBtn.setEnabled(!disabled);
            if (visualFilterBtn != null) visualFilterBtn.setEnabled(!disabled);
            if (playVideoBtn != null) playVideoBtn.setEnabled(!disabled);
            if (deleteBtn != null) deleteBtn.setEnabled(!disabled);
            if (clearListBtn != null) clearListBtn.setEnabled(!disabled);
        });
    }

    /**
     * Custom renderer that renders scenes as modern Cards with badges, details, and prompts.
     */
    private static class SceneCardCellRenderer extends JPanel implements ListCellRenderer<Scene> {
        private final JLabel titleLabel = new JLabel();
        private final JLabel statusBadge = new JLabel();
        private final JLabel filterBadge = new JLabel();
        private final JLabel detailsLabel = new JLabel();
        private final JLabel promptLabel = new JLabel();
        private final JLabel narrationLabel = new JLabel();
        private final JLabel pathLabel = new JLabel();

        public SceneCardCellRenderer() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(new EmptyBorder(8, 12, 8, 12));
            setOpaque(true);

            titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
            statusBadge.setFont(new Font("SansSerif", Font.BOLD, 10));
            statusBadge.setOpaque(true);

            filterBadge.setFont(new Font("SansSerif", Font.BOLD, 10));
            filterBadge.setOpaque(true);

            detailsLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
            detailsLabel.setForeground(ThemeManager.getTextSecondaryColor());

            promptLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
            narrationLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
            narrationLabel.setForeground(ThemeManager.getTextSecondaryColor());

            pathLabel.setFont(new Font("Monospaced", Font.PLAIN, 10));
            pathLabel.setForeground(ThemeManager.getTextSecondaryColor());

            JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            topRow.setOpaque(false);
            topRow.add(titleLabel);
            topRow.add(statusBadge);
            topRow.add(filterBadge);
            topRow.add(detailsLabel);

            add(topRow);
            add(Box.createVerticalStrut(4));
            add(promptLabel);
            add(Box.createVerticalStrut(2));
            add(narrationLabel);
            add(Box.createVerticalStrut(3));
            add(pathLabel);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Scene> list, Scene scene, int index, boolean isSelected, boolean cellHasFocus) {
            if (scene == null) return this;

            titleLabel.setText("Scene: " + scene.getSceneId());
            titleLabel.setForeground(ThemeManager.getAccentColor());

            int durSecs = Math.max(1, (scene.getEndFrame() - scene.getStartFrame()) / 24);
            detailsLabel.setText(String.format("(%ds, %d frames | CFG: %.1f | Steps: %d)",
                    durSecs, (scene.getEndFrame() - scene.getStartFrame()), scene.getCfgScale(), scene.getSteps()));

            boolean hasVideo = scene.getVideoPath() != null && new File(scene.getVideoPath()).exists();
            boolean hasAudio = scene.getAudioPath() != null && new File(scene.getAudioPath()).exists();

            if (hasVideo && hasAudio) {
                statusBadge.setText(" 🎬 Video & Audio Ready ");
                statusBadge.setBackground(new Color(16, 185, 129, 45));
                statusBadge.setForeground(new Color(16, 185, 129));
                statusBadge.setBorder(BorderFactory.createLineBorder(new Color(16, 185, 129, 100), 1, true));
            } else if (hasVideo) {
                statusBadge.setText(" 🎬 Video Ready ");
                statusBadge.setBackground(new Color(16, 185, 129, 45));
                statusBadge.setForeground(new Color(16, 185, 129));
                statusBadge.setBorder(BorderFactory.createLineBorder(new Color(16, 185, 129, 100), 1, true));
            } else if (hasAudio) {
                statusBadge.setText(" 🎙️ Audio Ready ");
                statusBadge.setBackground(new Color(59, 130, 246, 45));
                statusBadge.setForeground(new Color(59, 130, 246));
                statusBadge.setBorder(BorderFactory.createLineBorder(new Color(59, 130, 246, 100), 1, true));
            } else {
                statusBadge.setText(" ⏳ Pending ");
                statusBadge.setBackground(new Color(112, 138, 144, 45));
                statusBadge.setForeground(ThemeManager.getTextSecondaryColor());
                statusBadge.setBorder(BorderFactory.createLineBorder(new Color(112, 138, 144, 100), 1, true));
            }

            if (Math.abs(scene.getContrast() - 1.0) > 0.01 || Math.abs(scene.getBrightness()) > 0.01) {
                filterBadge.setVisible(true);
                filterBadge.setText(String.format(java.util.Locale.US, " 🎨 OpenCV: %.2fx, %+d ", scene.getContrast(), (int) scene.getBrightness()));
                filterBadge.setBackground(new Color(168, 85, 247, 45));
                filterBadge.setForeground(new Color(168, 85, 247));
                filterBadge.setBorder(BorderFactory.createLineBorder(new Color(168, 85, 247, 100), 1, true));
            } else {
                filterBadge.setVisible(false);
            }

            promptLabel.setText("Visual: " + (scene.getPrompt() != null ? scene.getPrompt() : ""));
            promptLabel.setForeground(isSelected ? list.getSelectionForeground() : ThemeManager.getTextColor());

            String narration = scene.getNarrationText();
            if (narration != null && !narration.trim().isEmpty()) {
                narrationLabel.setText("Narration: \"" + narration + "\"");
                narrationLabel.setVisible(true);
            } else {
                narrationLabel.setVisible(false);
            }

            String audioInfo = hasAudio ? " | Audio: " + new File(scene.getAudioPath()).getName() : "";
            pathLabel.setText("File: " + (hasVideo ? scene.getVideoPath() : "Not generated") + audioInfo);

            if (isSelected) {
                setBackground(list.getSelectionBackground());
            } else {
                setBackground(index % 2 == 0 ? ThemeManager.getCardBackgroundColor()
                        : (ThemeManager.isDarkMode() ? new Color(20, 22, 28) : new Color(240, 243, 246)));
            }

            return this;
        }
    }
}
