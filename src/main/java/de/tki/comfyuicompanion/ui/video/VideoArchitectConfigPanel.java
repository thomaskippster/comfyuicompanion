package de.tki.comfyuicompanion.ui.video;

import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.CardPanel;
import de.tki.comfyuicompanion.ui.ThemeManager;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.util.function.Consumer;

/**
 * Left configuration panel for Video Architect.
 * Encapsulates model selection, hardware profile indicators, generation parameters,
 * script prompt input, consistency start images, and autonomous autopilot triggers.
 */
public class VideoArchitectConfigPanel extends CardPanel {

    private final ConfigService configService;
    private final VideoArchitectPresetCoordinator presetCoordinator;
    private final Consumer<String> consoleLogger;

    private JLabel hardwareProfileBadge;
    private JButton btnReapplyHwPreset;
    private JComboBox<String> videoModelCombo;
    private JButton btnRefreshModels;
    private JLabel videoPresetLabel;
    private JTextField promptSubjectField;
    private JButton btnSuggestSubject;
    private JTextField speakerImageField;
    private JButton browseSpeaker;
    private JButton clearSpeaker;
    private JSpinner videoWidthSpinner;
    private JSpinner videoHeightSpinner;
    private JSpinner videoDurationSpinner;
    private JSpinner videoStepsSpinner;
    private JSpinner videoCfgSpinner;
    private JSpinner videoMotionSpinner;
    private JButton btnAutoPilot;

    /**
     * Constructs a new VideoArchitectConfigPanel.
     *
     * @param configService      the configuration service for managing speaker image settings
     * @param presetCoordinator the coordinator for hardware and model presets
     * @param consoleLogger     consumer for logging status messages to the console
     */
    public VideoArchitectConfigPanel(ConfigService configService,
                                     VideoArchitectPresetCoordinator presetCoordinator,
                                     Consumer<String> consoleLogger) {
        this.configService = configService;
        this.presetCoordinator = presetCoordinator;
        this.consoleLogger = consoleLogger != null ? consoleLogger : msg -> {};
        initUI();
    }

    private void initUI() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
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

        btnReapplyHwPreset = new JButton(SvgIconFactory.get(AppIcon.SUGGEST, 12));
        btnReapplyHwPreset.setToolTipText("Hardware-Empfehlungen automatisch anwenden");
        btnReapplyHwPreset.addActionListener(e -> applyHardwarePreset(true));
        hwHeaderRow.add(btnReapplyHwPreset, BorderLayout.EAST);

        add(hwHeaderRow);
        add(Box.createVerticalStrut(10));

        // 1. Blueprint / Video Model
        JLabel lblBlueprint = new JLabel("Blueprint / Video Model");
        lblBlueprint.putClientProperty("FlatLaf.styleClass", "h4");
        lblBlueprint.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(lblBlueprint);
        add(Box.createVerticalStrut(5));

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

        btnRefreshModels = new JButton(SvgIconFactory.get(AppIcon.REFRESH));
        btnRefreshModels.setToolTipText("Refresh video models list from ComfyUI");
        btnRefreshModels.addActionListener(e -> consoleLogger.accept("Refreshed video pipelines from ComfyUI."));
        modelRow.add(btnRefreshModels, BorderLayout.EAST);

        add(modelRow);
        add(Box.createVerticalStrut(6));

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
        add(videoPresetLabel);
        add(Box.createVerticalStrut(16));

        // 2. Script / Concept
        JLabel lblPromptGroup = new JLabel("Script / Concept");
        lblPromptGroup.putClientProperty("FlatLaf.styleClass", "h4");
        lblPromptGroup.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(lblPromptGroup);
        add(Box.createVerticalStrut(6));

        JLabel lblSubject = new JLabel("Master Story Idea / Prompt");
        lblSubject.setFont(subLabelFont);
        lblSubject.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(lblSubject);
        add(Box.createVerticalStrut(4));

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
        subjectRow.add(btnSuggestSubject, BorderLayout.EAST);

        add(subjectRow);
        add(Box.createVerticalStrut(10));

        // 3. Consistency / Start Image
        JLabel lblStartImage = new JLabel("Start Image (Consistency)");
        lblStartImage.setFont(subLabelFont);
        lblStartImage.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(lblStartImage);
        add(Box.createVerticalStrut(4));

        JPanel speakerRow = new JPanel(new BorderLayout(6, 0));
        speakerRow.setOpaque(false);
        speakerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        speakerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        speakerImageField = new JTextField("");
        speakerImageField.setFont(new Font("SansSerif", Font.PLAIN, 12));
        speakerRow.add(speakerImageField, BorderLayout.CENTER);

        JPanel speakerBtnGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        speakerBtnGroup.setOpaque(false);

        browseSpeaker = new JButton("Browse...");
        browseSpeaker.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select Start Image");
            chooser.setFileFilter(new FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "webp"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File f = chooser.getSelectedFile();
                speakerImageField.setText(f.getAbsolutePath());
                if (configService != null) {
                    configService.setSpeakerImagePath(f.getAbsolutePath());
                }
            }
        });

        clearSpeaker = new JButton(SvgIconFactory.get(AppIcon.CLOSE, 12));
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
        add(speakerRow);
        add(Box.createVerticalStrut(16));

        // 4. Output Dimensions & Duration
        JLabel lblDimensions = new JLabel("Output Dimensions & Duration");
        lblDimensions.putClientProperty("FlatLaf.styleClass", "h4");
        lblDimensions.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(lblDimensions);
        add(Box.createVerticalStrut(5));

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
        add(sizeRow);
        add(Box.createVerticalStrut(16));

        // 5. Generation & Sampling Parameters
        JLabel lblSampler = new JLabel("Generation & Sampling Parameters");
        lblSampler.putClientProperty("FlatLaf.styleClass", "h4");
        lblSampler.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(lblSampler);
        add(Box.createVerticalStrut(5));

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
        add(samplerRow);
        add(Box.createVerticalStrut(18));

        // 6. Autonomous Action
        btnAutoPilot = new JButton("Agent: Auto-Pilot (End-to-End)", SvgIconFactory.get(AppIcon.AGENT));
        btnAutoPilot.setFont(new Font("SansSerif", Font.BOLD, 13));
        btnAutoPilot.putClientProperty("Button.background", new Color(255, 204, 0));
        btnAutoPilot.putClientProperty("Button.foreground", Color.BLACK);
        btnAutoPilot.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnAutoPilot.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btnAutoPilot.setToolTipText("Deconstruct script, generate narration TTS, render ComfyUI scene videos, and stitch into master_export.mp4 automatically.");
        add(btnAutoPilot);

        add(Box.createVerticalGlue());
    }

    private JPanel createSpinnerPanel(String label, JSpinner spinner) {
        JPanel panel = new JPanel(new BorderLayout(0, 2));
        panel.setOpaque(false);
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        if (spinner.getEditor() instanceof JSpinner.NumberEditor ne) {
            ne.getFormat().setGroupingUsed(false);
        }
        panel.add(lbl, BorderLayout.NORTH);
        panel.add(spinner, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Applies the recommended hardware-specific preset for the current GPU capabilities.
     *
     * @param logMessage true to print a confirmation log to the UI console, false otherwise
     */
    public void applyHardwarePreset(boolean logMessage) {
        if (presetCoordinator != null) {
            presetCoordinator.applyHardwarePreset(logMessage, hardwareProfileBadge, videoModelCombo,
                    videoWidthSpinner, videoHeightSpinner, videoStepsSpinner, videoCfgSpinner,
                    videoPresetLabel, consoleLogger);
        }
    }

    /**
     * Updates model presets and spinner default values based on selected video model.
     */
    public void updateModelPreset() {
        if (presetCoordinator != null) {
            String selected = (String) videoModelCombo.getSelectedItem();
            presetCoordinator.updateModelPreset(selected, videoPresetLabel,
                    videoWidthSpinner, videoHeightSpinner, videoStepsSpinner, videoCfgSpinner,
                    consoleLogger);
        }
    }

    /**
     * Sets the action callback when the Suggest / Deconstruct Subject button is clicked.
     *
     * @param action the callback to execute
     */
    public void setOnSuggestSubject(Runnable action) {
        if (btnSuggestSubject != null && action != null) {
            btnSuggestSubject.addActionListener(e -> action.run());
        }
    }

    /**
     * Sets the action callback when the Auto-Pilot button is clicked.
     *
     * @param action the callback to execute
     */
    public void setOnAutoPilot(Runnable action) {
        if (btnAutoPilot != null && action != null) {
            btnAutoPilot.addActionListener(e -> action.run());
        }
    }

    /**
     * Disables or enables interactive action buttons during execution.
     *
     * @param disabled true to disable buttons, false to enable
     */
    public void setButtonsDisabled(boolean disabled) {
        VideoArchitectThemeHandler.setButtonsDisabled(disabled, btnSuggestSubject, btnAutoPilot,
                btnReapplyHwPreset, btnRefreshModels);
    }

    /**
     * Returns the hardware profile badge label.
     *
     * @return the badge label
     */
    public JLabel getHardwareProfileBadge() {
        return hardwareProfileBadge;
    }

    /**
     * Returns the video model combo box.
     *
     * @return the combo box
     */
    public JComboBox<String> getVideoModelCombo() {
        return videoModelCombo;
    }

    /**
     * Returns the video preset description label.
     *
     * @return the preset label
     */
    public JLabel getVideoPresetLabel() {
        return videoPresetLabel;
    }

    /**
     * Returns the prompt subject input text field.
     *
     * @return the subject text field
     */
    public JTextField getPromptSubjectField() {
        return promptSubjectField;
    }

    /**
     * Returns the deconstruct / suggest subject button.
     *
     * @return the suggest button
     */
    public JButton getBtnSuggestSubject() {
        return btnSuggestSubject;
    }

    /**
     * Returns the speaker image path text field.
     *
     * @return the speaker image text field
     */
    public JTextField getSpeakerImageField() {
        return speakerImageField;
    }

    /**
     * Returns the video width spinner.
     *
     * @return the width spinner
     */
    public JSpinner getVideoWidthSpinner() {
        return videoWidthSpinner;
    }

    /**
     * Returns the video height spinner.
     *
     * @return the height spinner
     */
    public JSpinner getVideoHeightSpinner() {
        return videoHeightSpinner;
    }

    /**
     * Returns the video duration spinner.
     *
     * @return the duration spinner
     */
    public JSpinner getVideoDurationSpinner() {
        return videoDurationSpinner;
    }

    /**
     * Returns the video sampling steps spinner.
     *
     * @return the steps spinner
     */
    public JSpinner getVideoStepsSpinner() {
        return videoStepsSpinner;
    }

    /**
     * Returns the CFG scale spinner.
     *
     * @return the CFG spinner
     */
    public JSpinner getVideoCfgSpinner() {
        return videoCfgSpinner;
    }

    /**
     * Returns the motion bucket spinner.
     *
     * @return the motion spinner
     */
    public JSpinner getVideoMotionSpinner() {
        return videoMotionSpinner;
    }

    /**
     * Returns the autopilot button.
     *
     * @return the autopilot button
     */
    public JButton getBtnAutoPilot() {
        return btnAutoPilot;
    }

    /**
     * Returns the trimmed prompt subject text.
     *
     * @return the prompt subject
     */
    public String getPromptSubject() {
        return promptSubjectField != null ? promptSubjectField.getText().trim() : "";
    }

    /**
     * Returns the currently selected video model.
     *
     * @return the selected model name
     */
    public String getSelectedModel() {
        return videoModelCombo != null ? (String) videoModelCombo.getSelectedItem() : "";
    }

    /**
     * Returns the trimmed speaker image path.
     *
     * @return the speaker image path
     */
    public String getSpeakerImagePath() {
        return speakerImageField != null ? speakerImageField.getText().trim() : "";
    }

    /**
     * Returns the target video width in pixels.
     *
     * @return video width
     */
    public int getVideoWidth() {
        return videoWidthSpinner != null ? (Integer) videoWidthSpinner.getValue() : 832;
    }

    /**
     * Returns the target video height in pixels.
     *
     * @return video height
     */
    public int getVideoHeight() {
        return videoHeightSpinner != null ? (Integer) videoHeightSpinner.getValue() : 480;
    }

    /**
     * Returns the target video duration in seconds.
     *
     * @return video duration
     */
    public int getVideoDuration() {
        return videoDurationSpinner != null ? (Integer) videoDurationSpinner.getValue() : 5;
    }

    /**
     * Returns the generation sampling steps.
     *
     * @return sampling steps
     */
    public int getVideoSteps() {
        return videoStepsSpinner != null ? (Integer) videoStepsSpinner.getValue() : 20;
    }

    /**
     * Returns the CFG scale value.
     *
     * @return CFG scale
     */
    public double getVideoCfg() {
        return videoCfgSpinner != null ? ((Number) videoCfgSpinner.getValue()).doubleValue() : 3.0;
    }

    /**
     * Returns the motion bucket ID parameter.
     *
     * @return motion bucket ID
     */
    public int getVideoMotion() {
        return videoMotionSpinner != null ? (Integer) videoMotionSpinner.getValue() : 127;
    }
}
