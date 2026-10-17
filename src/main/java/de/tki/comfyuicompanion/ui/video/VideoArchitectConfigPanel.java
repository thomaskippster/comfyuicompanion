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
    private final VideoSamplingParamsPanel samplingParamsPanel;
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
        this.samplingParamsPanel = new VideoSamplingParamsPanel();
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
        initStartImageRow(subLabelFont);

        // 4 & 5. Sampling Parameters Panel
        add(samplingParamsPanel);
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

    private void initStartImageRow(Font subLabelFont) {
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
        browseSpeaker.addActionListener(e -> onBrowseSpeakerImage());

        clearSpeaker = new JButton(SvgIconFactory.get(AppIcon.CLOSE, 12));
        clearSpeaker.setToolTipText("Clear Start Image (Pure Text-to-Video)");
        clearSpeaker.addActionListener(e -> onClearSpeakerImage());

        speakerBtnGroup.add(browseSpeaker);
        speakerBtnGroup.add(clearSpeaker);
        speakerRow.add(speakerBtnGroup, BorderLayout.EAST);
        add(speakerRow);
        add(Box.createVerticalStrut(16));
    }

    private void onBrowseSpeakerImage() {
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
    }

    private void onClearSpeakerImage() {
        speakerImageField.setText("");
        if (configService != null) {
            configService.setSpeakerImagePath("");
        }
    }

    /**
     * Applies the recommended hardware-specific preset for the current GPU capabilities.
     *
     * @param logMessage true to print a confirmation log to the UI console, false otherwise
     */
    public void applyHardwarePreset(boolean logMessage) {
        if (presetCoordinator != null) {
            presetCoordinator.applyHardwarePreset(logMessage, hardwareProfileBadge, videoModelCombo,
                    samplingParamsPanel.getVideoWidthSpinner(), samplingParamsPanel.getVideoHeightSpinner(),
                    samplingParamsPanel.getVideoStepsSpinner(), samplingParamsPanel.getVideoCfgSpinner(),
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
                    samplingParamsPanel.getVideoWidthSpinner(), samplingParamsPanel.getVideoHeightSpinner(),
                    samplingParamsPanel.getVideoStepsSpinner(), samplingParamsPanel.getVideoCfgSpinner(),
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
    public JButton getBtnSuggestSubject() { return btnSuggestSubject; }
    public JTextField getSpeakerImageField() { return speakerImageField; }
    public JSpinner getVideoWidthSpinner() { return samplingParamsPanel.getVideoWidthSpinner(); }
    public JSpinner getVideoHeightSpinner() { return samplingParamsPanel.getVideoHeightSpinner(); }
    public JSpinner getVideoDurationSpinner() { return samplingParamsPanel.getVideoDurationSpinner(); }
    public JSpinner getVideoStepsSpinner() { return samplingParamsPanel.getVideoStepsSpinner(); }
    public JSpinner getVideoCfgSpinner() { return samplingParamsPanel.getVideoCfgSpinner(); }
    public JSpinner getVideoMotionSpinner() { return samplingParamsPanel.getVideoMotionSpinner(); }
    public JButton getBtnAutoPilot() { return btnAutoPilot; }

    public String getPromptSubject() { return promptSubjectField != null ? promptSubjectField.getText().trim() : ""; }
    public String getSelectedModel() { return videoModelCombo != null ? (String) videoModelCombo.getSelectedItem() : ""; }
    public String getSpeakerImagePath() { return speakerImageField != null ? speakerImageField.getText().trim() : ""; }

    public int getVideoWidth() { return samplingParamsPanel.getVideoWidth(); }
    public int getVideoHeight() { return samplingParamsPanel.getVideoHeight(); }
    public int getVideoDuration() { return samplingParamsPanel.getVideoDuration(); }
    public int getVideoSteps() { return samplingParamsPanel.getVideoSteps(); }
    public double getVideoCfg() { return samplingParamsPanel.getVideoCfg(); }
    public int getVideoMotion() { return samplingParamsPanel.getVideoMotion(); }
}
