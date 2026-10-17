package de.tki.comfyuicompanion.ui;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;

/**
 * UI panel containing dimension, batching, sampler, and scheduler controls for Prompt Lab.
 */
public class PromptLabParametersPanel extends JPanel {

    private final JSpinner promptWidthSpinner;
    private final JSpinner promptHeightSpinner;
    private final JSpinner promptBatchSizeSpinner;
    private final JSpinner promptStepsSpinner;
    private final JSpinner promptCfgSpinner;
    private final JSpinner promptDenoiseSpinner;
    private final JComboBox<String> promptSamplerCombo;
    private final JComboBox<String> promptSchedulerCombo;

    /**
     * Constructs a new PromptLabParametersPanel and initializes parameter controls.
     */
    public PromptLabParametersPanel() {
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

        // Image Dimensions & Batch Size
        JLabel lblDimensions = new JLabel("Output Dimensions & Batching");
        lblDimensions.putClientProperty("FlatLaf.styleClass", "h4");
        lblDimensions.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        add(lblDimensions);
        add(Box.createVerticalStrut(5));

        JPanel sizeRow = new JPanel(new GridLayout(1, 3, 8, 0));
        sizeRow.setOpaque(false);
        sizeRow.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        sizeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

        JPanel widthPanel = new JPanel(new BorderLayout(0, 2));
        widthPanel.setOpaque(false);
        JLabel lblWidth = new JLabel("Width");
        lblWidth.setFont(new Font("SansSerif", Font.PLAIN, 11));
        promptWidthSpinner = new JSpinner(new SpinnerNumberModel(1024, 64, 4096, 64));
        if (promptWidthSpinner.getEditor() instanceof JSpinner.NumberEditor ne) ne.getFormat().setGroupingUsed(false);
        widthPanel.add(lblWidth, BorderLayout.NORTH);
        widthPanel.add(promptWidthSpinner, BorderLayout.CENTER);

        JPanel heightPanel = new JPanel(new BorderLayout(0, 2));
        heightPanel.setOpaque(false);
        JLabel lblHeight = new JLabel("Height");
        lblHeight.setFont(new Font("SansSerif", Font.PLAIN, 11));
        promptHeightSpinner = new JSpinner(new SpinnerNumberModel(1024, 64, 4096, 64));
        if (promptHeightSpinner.getEditor() instanceof JSpinner.NumberEditor ne) ne.getFormat().setGroupingUsed(false);
        heightPanel.add(lblHeight, BorderLayout.NORTH);
        heightPanel.add(promptHeightSpinner, BorderLayout.CENTER);

        JPanel batchPanel = new JPanel(new BorderLayout(0, 2));
        batchPanel.setOpaque(false);
        JLabel lblBatch = new JLabel("Batch Size");
        lblBatch.setFont(new Font("SansSerif", Font.PLAIN, 11));
        promptBatchSizeSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 16, 1));
        if (promptBatchSizeSpinner.getEditor() instanceof JSpinner.NumberEditor ne) ne.getFormat().setGroupingUsed(false);
        batchPanel.add(lblBatch, BorderLayout.NORTH);
        batchPanel.add(promptBatchSizeSpinner, BorderLayout.CENTER);

        sizeRow.add(widthPanel);
        sizeRow.add(heightPanel);
        sizeRow.add(batchPanel);
        add(sizeRow);
        add(Box.createVerticalStrut(15));

        // Sampler Parameters
        JLabel lblSampler = new JLabel("Generation & Sampling Parameters");
        lblSampler.putClientProperty("FlatLaf.styleClass", "h4");
        lblSampler.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        add(lblSampler);
        add(Box.createVerticalStrut(5));

        JPanel samplerRow1 = new JPanel(new GridLayout(1, 3, 8, 0));
        samplerRow1.setOpaque(false);
        samplerRow1.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        samplerRow1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

        JPanel stepsPanel = new JPanel(new BorderLayout(0, 2));
        stepsPanel.setOpaque(false);
        JLabel lblSteps = new JLabel("Steps");
        lblSteps.setFont(new Font("SansSerif", Font.PLAIN, 11));
        promptStepsSpinner = new JSpinner(new SpinnerNumberModel(20, 1, 100, 1));
        if (promptStepsSpinner.getEditor() instanceof JSpinner.NumberEditor ne) ne.getFormat().setGroupingUsed(false);
        stepsPanel.add(lblSteps, BorderLayout.NORTH);
        stepsPanel.add(promptStepsSpinner, BorderLayout.CENTER);

        JPanel cfgPanel = new JPanel(new BorderLayout(0, 2));
        cfgPanel.setOpaque(false);
        JLabel lblCfg = new JLabel("CFG Scale");
        lblCfg.setFont(new Font("SansSerif", Font.PLAIN, 11));
        promptCfgSpinner = new JSpinner(new SpinnerNumberModel(8.0, 0.0, 30.0, 0.5));
        if (promptCfgSpinner.getEditor() instanceof JSpinner.NumberEditor ne) ne.getFormat().setGroupingUsed(false);
        cfgPanel.add(lblCfg, BorderLayout.NORTH);
        cfgPanel.add(promptCfgSpinner, BorderLayout.CENTER);

        JPanel denoisePanel = new JPanel(new BorderLayout(0, 2));
        denoisePanel.setOpaque(false);
        JLabel lblDenoise = new JLabel("Denoise");
        lblDenoise.setFont(new Font("SansSerif", Font.PLAIN, 11));
        promptDenoiseSpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.0, 1.0, 0.05));
        if (promptDenoiseSpinner.getEditor() instanceof JSpinner.NumberEditor ne) ne.getFormat().setGroupingUsed(false);
        denoisePanel.add(lblDenoise, BorderLayout.NORTH);
        denoisePanel.add(promptDenoiseSpinner, BorderLayout.CENTER);

        samplerRow1.add(stepsPanel);
        samplerRow1.add(cfgPanel);
        samplerRow1.add(denoisePanel);
        add(samplerRow1);
        add(Box.createVerticalStrut(8));

        JPanel samplerRow2 = new JPanel(new GridLayout(1, 2, 8, 0));
        samplerRow2.setOpaque(false);
        samplerRow2.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        samplerRow2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

        JPanel samplerNamePanel = new JPanel(new BorderLayout(0, 2));
        samplerNamePanel.setOpaque(false);
        JLabel lblSamplerName = new JLabel("Sampler Name");
        lblSamplerName.setFont(new Font("SansSerif", Font.PLAIN, 11));
        promptSamplerCombo = new JComboBox<>(new String[]{
                "Auto", "euler", "euler_ancestral", "heun", "heunpp2", "dpmpp_2m",
                "dpmpp_sde", "dpmpp_2m_sde", "dpmpp_3m_sde", "ddim", "uni_pc", "lcm", "res_multistep"
        });
        samplerNamePanel.add(lblSamplerName, BorderLayout.NORTH);
        samplerNamePanel.add(promptSamplerCombo, BorderLayout.CENTER);

        JPanel schedulerPanel = new JPanel(new BorderLayout(0, 2));
        schedulerPanel.setOpaque(false);
        JLabel lblScheduler = new JLabel("Scheduler");
        lblScheduler.setFont(new Font("SansSerif", Font.PLAIN, 11));
        promptSchedulerCombo = new JComboBox<>(new String[]{
                "Auto", "normal", "karras", "exponential", "sgm_uniform", "simple", "ddim_uniform", "beta"
        });
        schedulerPanel.add(lblScheduler, BorderLayout.NORTH);
        schedulerPanel.add(promptSchedulerCombo, BorderLayout.CENTER);

        samplerRow2.add(samplerNamePanel);
        samplerRow2.add(schedulerPanel);
        add(samplerRow2);
        add(Box.createVerticalGlue());
    }

    /**
     * Attaches a change listener callback to all spinners and combo boxes.
     *
     * @param onUpdate change listener callback
     */
    public void attachChangeListener(Runnable onUpdate) {
        if (onUpdate == null) return;
        promptWidthSpinner.addChangeListener(e -> onUpdate.run());
        promptHeightSpinner.addChangeListener(e -> onUpdate.run());
        promptBatchSizeSpinner.addChangeListener(e -> onUpdate.run());
        promptStepsSpinner.addChangeListener(e -> onUpdate.run());
        promptCfgSpinner.addChangeListener(e -> onUpdate.run());
        promptDenoiseSpinner.addChangeListener(e -> onUpdate.run());
        promptSamplerCombo.addActionListener(e -> onUpdate.run());
        promptSchedulerCombo.addActionListener(e -> onUpdate.run());
    }

    public JSpinner getWidthSpinner() { return promptWidthSpinner; }
    public JSpinner getHeightSpinner() { return promptHeightSpinner; }
    public JSpinner getBatchSizeSpinner() { return promptBatchSizeSpinner; }
    public JSpinner getStepsSpinner() { return promptStepsSpinner; }
    public JSpinner getCfgSpinner() { return promptCfgSpinner; }
    public JSpinner getDenoiseSpinner() { return promptDenoiseSpinner; }
    public JComboBox<String> getSamplerCombo() { return promptSamplerCombo; }
    public JComboBox<String> getSchedulerCombo() { return promptSchedulerCombo; }
}
