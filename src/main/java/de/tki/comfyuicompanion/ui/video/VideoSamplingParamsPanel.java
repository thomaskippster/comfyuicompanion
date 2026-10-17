package de.tki.comfyuicompanion.ui.video;

import javax.swing.*;
import java.awt.*;

/**
 * Panel encapsulating controls for video output dimensions (width, height, duration)
 * and sampling generation parameters (steps, CFG scale, motion bucket).
 */
public class VideoSamplingParamsPanel extends JPanel {

    private final JSpinner videoWidthSpinner;
    private final JSpinner videoHeightSpinner;
    private final JSpinner videoDurationSpinner;
    private final JSpinner videoStepsSpinner;
    private final JSpinner videoCfgSpinner;
    private final JSpinner videoMotionSpinner;

    /**
     * Constructs a new VideoSamplingParamsPanel and initializes spinners and labels.
     */
    public VideoSamplingParamsPanel() {
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
}
