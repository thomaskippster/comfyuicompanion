package de.tki.comfyuicompanion.ui.video;

import de.tki.comfyuicompanion.service.impl.ConfigService;

import javax.swing.*;
import java.awt.*;

/**
 * Panel encapsulating controls for video output dimensions (width, height, duration),
 * sampling generation parameters (steps, CFG scale, motion bucket), and 16 GB VRAM optimizations.
 */
public class VideoSamplingParamsPanel extends JPanel {

    private final JSpinner videoWidthSpinner;
    private final JSpinner videoHeightSpinner;
    private final JSpinner videoDurationSpinner;
    private final JSpinner videoStepsSpinner;
    private final JSpinner videoCfgSpinner;
    private final JSpinner videoMotionSpinner;
    private final JCheckBox chk16GbOpt;
    private final JCheckBox chkFrameChaining;
    private final JCheckBox chkTwoPhase;
    private final JComboBox<String> cmbLocalLlm;
    private final JCheckBox chkLipSync;
    private final JComboBox<String> cmbLipSyncEngine;

    /**
     * Constructs a new VideoSamplingParamsPanel with default configuration.
     */
    public VideoSamplingParamsPanel() {
        this(null);
    }

    /**
     * Constructs a new VideoSamplingParamsPanel bound to the provided configuration service.
     *
     * @param configService the configuration service
     */
    public VideoSamplingParamsPanel(ConfigService configService) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);

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

        videoWidthSpinner = new JSpinner(new SpinnerNumberModel(1280, 256, 3840, 16));
        videoHeightSpinner = new JSpinner(new SpinnerNumberModel(720, 256, 2160, 16));
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

        videoStepsSpinner = new JSpinner(new SpinnerNumberModel(28, 1, 100, 1));
        videoCfgSpinner = new JSpinner(new SpinnerNumberModel(6.5, 0.5, 20.0, 0.5));
        videoMotionSpinner = new JSpinner(new SpinnerNumberModel(127, 1, 255, 1));
        samplerRow.add(createSpinnerPanel("Steps", videoStepsSpinner));
        samplerRow.add(createSpinnerPanel("CFG Scale", videoCfgSpinner));
        samplerRow.add(createSpinnerPanel("Motion Bucket", videoMotionSpinner));
        add(samplerRow);
        add(Box.createVerticalStrut(16));

        // 6. 16 GB VRAM & Pipeline Optimization
        JLabel lblOpt = new JLabel("16 GB VRAM & Pipeline Optimization");
        lblOpt.putClientProperty("FlatLaf.styleClass", "h4");
        lblOpt.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(lblOpt);
        add(Box.createVerticalStrut(6));

        chk16GbOpt = new JCheckBox("16 GB Optimization (CPU CLIP & Tiled VAE)",
                configService != null ? configService.is16GbVramOptimizationEnabled() : true);
        chk16GbOpt.setToolTipText("Forces CLIP text encoders to CPU and applies VAEDecodeTiled to prevent OOM errors on 16 GB GPUs.");
        chk16GbOpt.setFont(new Font("SansSerif", Font.PLAIN, 12));
        chk16GbOpt.setAlignmentX(Component.LEFT_ALIGNMENT);
        chk16GbOpt.addActionListener(e -> {
            if (configService != null) {
                configService.set16GbVramOptimizationEnabled(chk16GbOpt.isSelected());
            }
        });
        add(chk16GbOpt);
        add(Box.createVerticalStrut(4));

        chkFrameChaining = new JCheckBox("Continuous Shot (Last-Frame Chaining)",
                configService != null ? configService.isLastFrameChainingEnabled() : false);
        chkFrameChaining.setToolTipText("Feeds the final frame of the previous scene as the starting keyframe for the next scene.");
        chkFrameChaining.setFont(new Font("SansSerif", Font.PLAIN, 12));
        chkFrameChaining.setAlignmentX(Component.LEFT_ALIGNMENT);
        chkFrameChaining.addActionListener(e -> {
            if (configService != null) {
                configService.setLastFrameChainingEnabled(chkFrameChaining.isSelected());
            }
        });
        add(chkFrameChaining);
        add(Box.createVerticalStrut(4));

        chkTwoPhase = new JCheckBox("Two-Phase Render (Storyboard Keyframe -> I2V)",
                configService != null ? configService.isTwoPhaseRenderingEnabled() : false);
        chkTwoPhase.setToolTipText("Renders starting keyframes first, purges VRAM, and then triggers video diffusion with visual feedback.");
        chkTwoPhase.setFont(new Font("SansSerif", Font.PLAIN, 12));
        chkTwoPhase.setAlignmentX(Component.LEFT_ALIGNMENT);
        chkTwoPhase.addActionListener(e -> {
            if (configService != null) {
                configService.setTwoPhaseRenderingEnabled(chkTwoPhase.isSelected());
            }
        });
        add(chkTwoPhase);
        add(Box.createVerticalStrut(6));

        JPanel llmRow = new JPanel(new BorderLayout(6, 0));
        llmRow.setOpaque(false);
        llmRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        llmRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        JLabel lblLlm = new JLabel("Script LLM:");
        lblLlm.setFont(new Font("SansSerif", Font.PLAIN, 11));
        cmbLocalLlm = new JComboBox<>(new String[]{"gemma-3-4b", "qwen-2.5-7b"});
        cmbLocalLlm.setFont(new Font("SansSerif", Font.PLAIN, 12));
        if (configService != null && configService.getLocalLlmModel() != null) {
            String curr = configService.getLocalLlmModel();
            if (curr.contains("qwen")) {
                cmbLocalLlm.setSelectedItem("qwen-2.5-7b");
            } else {
                cmbLocalLlm.setSelectedItem("gemma-3-4b");
            }
        }
        cmbLocalLlm.addActionListener(e -> {
            if (configService != null) {
                configService.setLocalLlmModel((String) cmbLocalLlm.getSelectedItem());
            }
        });
        llmRow.add(lblLlm, BorderLayout.WEST);
        llmRow.add(cmbLocalLlm, BorderLayout.CENTER);
        add(llmRow);
        add(Box.createVerticalStrut(6));

        // 7. Audio Lip-Sync & Facial Animation
        chkLipSync = new JCheckBox("Audio Lip-Sync (Audio-Driven Head Animation)",
                configService != null ? configService.isAudioLipSyncEnabled() : false);
        chkLipSync.setToolTipText("Animates facial expressions and synchronizes lip movement to scene narration audio using LivePortrait, SadTalker, or Wav2Lip.");
        chkLipSync.setFont(new Font("SansSerif", Font.PLAIN, 12));
        chkLipSync.setAlignmentX(Component.LEFT_ALIGNMENT);
        chkLipSync.addActionListener(e -> {
            if (configService != null) {
                configService.setAudioLipSyncEnabled(chkLipSync.isSelected());
            }
        });
        add(chkLipSync);
        add(Box.createVerticalStrut(4));

        JPanel lipSyncRow = new JPanel(new BorderLayout(6, 0));
        lipSyncRow.setOpaque(false);
        lipSyncRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        lipSyncRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        JLabel lblLipEngine = new JLabel("Lip-Sync Engine:");
        lblLipEngine.setFont(new Font("SansSerif", Font.PLAIN, 11));
        cmbLipSyncEngine = new JComboBox<>(new String[]{"Auto", "LivePortrait", "SadTalker", "Wav2Lip"});
        cmbLipSyncEngine.setFont(new Font("SansSerif", Font.PLAIN, 12));
        if (configService != null && configService.getLipSyncEngine() != null) {
            cmbLipSyncEngine.setSelectedItem(configService.getLipSyncEngine());
        }
        cmbLipSyncEngine.addActionListener(e -> {
            if (configService != null) {
                configService.setLipSyncEngine((String) cmbLipSyncEngine.getSelectedItem());
            }
        });
        lipSyncRow.add(lblLipEngine, BorderLayout.WEST);
        lipSyncRow.add(cmbLipSyncEngine, BorderLayout.CENTER);
        add(lipSyncRow);
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

    /** @return the video width spinner */
    public JSpinner getVideoWidthSpinner() { return videoWidthSpinner; }

    /** @return the video height spinner */
    public JSpinner getVideoHeightSpinner() { return videoHeightSpinner; }

    /** @return the video duration spinner */
    public JSpinner getVideoDurationSpinner() { return videoDurationSpinner; }

    /** @return the video steps spinner */
    public JSpinner getVideoStepsSpinner() { return videoStepsSpinner; }

    /** @return the video CFG scale spinner */
    public JSpinner getVideoCfgSpinner() { return videoCfgSpinner; }

    /** @return the motion bucket spinner */
    public JSpinner getVideoMotionSpinner() { return videoMotionSpinner; }

    /** @return the video width value */
    public int getVideoWidth() { return (Integer) videoWidthSpinner.getValue(); }

    /** @return the video height value */
    public int getVideoHeight() { return (Integer) videoHeightSpinner.getValue(); }

    /** @return the video duration value */
    public int getVideoDuration() { return (Integer) videoDurationSpinner.getValue(); }

    /** @return the video steps value */
    public int getVideoSteps() { return (Integer) videoStepsSpinner.getValue(); }

    /** @return the CFG scale value */
    public double getVideoCfg() { return ((Number) videoCfgSpinner.getValue()).doubleValue(); }

    /** @return the motion bucket value */
    public int getVideoMotion() { return (Integer) videoMotionSpinner.getValue(); }

    /** @return the audio lip-sync checkbox */
    public JCheckBox getChkLipSync() { return chkLipSync; }

    /** @return the lip-sync engine combobox */
    public JComboBox<String> getCmbLipSyncEngine() { return cmbLipSyncEngine; }

    /** @return true if lip sync is checked */
    public boolean isLipSyncSelected() { return chkLipSync.isSelected(); }

    /** @return selected lip sync engine identifier */
    public String getSelectedLipSyncEngine() { return (String) cmbLipSyncEngine.getSelectedItem(); }
}
