package de.tki.comfymodels.ui;

import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;

/**
 * View component for the Prompt Lab tab.
 * Preserves 1:1 original layout, FlatLaf styles, borders, spacing, transparency,
 * dynamic theme bindings, and event listeners that delegate to a controller.
 */
@Component
public class PromptLabView extends JPanel {

    public interface PromptLabController {
        void onRefreshModels();
        void onSuggestSubject();
        void onBlueprintSelected(String selectedModel);
        void onUpdatePromptLabJson();
        void onSendPromptToComfyUI();
    }

    private PromptLabController controller;

    private JComboBox<String> promptModelCombo;
    private JLabel promptPresetLabel;
    private JTextField promptSubjectField;
    private JButton btnSuggestSubject;
    private JPanel promptSubjectSuggestionsWrapper;
    private JPanel promptSubjectSuggestionsPanel;
    private CardPanel promptLabLeftPanel;
    private JTextField promptNegativeField;
    private JPanel promptImageInputPanel;
    private JTextField promptImageFileField;
    private File selectedInputImage;

    private JSpinner promptWidthSpinner;
    private JSpinner promptHeightSpinner;
    private JSpinner promptBatchSizeSpinner;
    private JSpinner promptStepsSpinner;
    private JSpinner promptCfgSpinner;
    private JSpinner promptDenoiseSpinner;
    private JComboBox<String> promptSamplerCombo;
    private JComboBox<String> promptSchedulerCombo;

    private JTabbedPane promptLabRightTabbedPane;
    private JLabel promptImagePreviewLabel;
    private JProgressBar promptLabProgressBar;
    private Image currentPreviewImage;
    private JTextArea promptJsonArea;
    private JTextArea promptLabConsole;
    private JButton btnSendToComfy;

    // Dummy objects for backward compatibility with unused fields
    private JTextArea promptAssembleArea = new JTextArea();
    @SuppressWarnings("rawtypes")
    private JComboBox promptEnvCombo = new JComboBox();
    private JCheckBox chkPhotorealistic = new JCheckBox();
    private JCheckBox chkOil = new JCheckBox();
    private JCheckBox chkEngine = new JCheckBox();
    private JCheckBox chkAnime = new JCheckBox();
    private JCheckBox chkFantasy = new JCheckBox();
    private JCheckBox chkSketch = new JCheckBox();

    public PromptLabView() {
        initUI();
    }

    public void setController(PromptLabController controller) {
        this.controller = controller;
    }

    private void initUI() {
        setOpaque(false);
        setLayout(new BorderLayout(15, 15));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // LEFT: Image Lab controls (Blueprint selection + Generic execution parameters)
        CardPanel leftPanel = new CardPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));

        // Header Title removed as it's redundant with the tab name

        // Blueprint / Workflow Selection
        JLabel lblBlueprint = new JLabel("Blueprint / Workflow");
        lblBlueprint.putClientProperty("FlatLaf.styleClass", "h4");
        lblBlueprint.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        leftPanel.add(lblBlueprint);
        leftPanel.add(Box.createVerticalStrut(5));

        JPanel modelRow = new JPanel(new BorderLayout(5, 0));
        modelRow.setOpaque(false);
        modelRow.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        modelRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));

        promptModelCombo = new JComboBox<>(new String[]{"v1-5-pruned-emaonly.safetensors"});
        promptModelCombo.setEditable(false);
        promptModelCombo.setFont(new Font("SansSerif", Font.PLAIN, 14));
        modelRow.add(promptModelCombo, BorderLayout.CENTER);

        JButton btnRefreshModels = new JButton("🔄");
        btnRefreshModels.setToolTipText("Refresh models list from ComfyUI");
        btnRefreshModels.addActionListener(e -> {
            if (controller != null) controller.onRefreshModels();
        });
        modelRow.add(btnRefreshModels, BorderLayout.EAST);

        leftPanel.add(modelRow);
        leftPanel.add(Box.createVerticalStrut(6));

        promptPresetLabel = new JLabel("Detected Preset: Stable Diffusion 1.5 (SD 1.5)") {
            @Override
            public void updateUI() {
                super.updateUI();
                Color c = UIManager.getColor("PromptLab.presetForeground");
                if (c != null) setForeground(c);
            }
        };
        promptPresetLabel.setFont(new Font("SansSerif", Font.ITALIC | Font.BOLD, 12));
        Color pc = UIManager.getColor("PromptLab.presetForeground");
        if (pc != null) promptPresetLabel.setForeground(pc);
        promptPresetLabel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        leftPanel.add(promptPresetLabel);
        leftPanel.add(Box.createVerticalStrut(18));

        // Prompt Group Header & Positive/Negative Prompts
        JLabel lblPromptGroup = new JLabel("Prompt");
        lblPromptGroup.putClientProperty("FlatLaf.styleClass", "h4");
        lblPromptGroup.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        leftPanel.add(lblPromptGroup);
        leftPanel.add(Box.createVerticalStrut(6));

        Font promptSubLabelFont = new Font("SansSerif", Font.PLAIN, 12);

        JLabel lblSubject = new JLabel("Positive Prompt");
        lblSubject.setFont(promptSubLabelFont);
        lblSubject.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        leftPanel.add(lblSubject);
        leftPanel.add(Box.createVerticalStrut(4));

        JPanel subjectRow = new JPanel(new BorderLayout(8, 0));
        subjectRow.setOpaque(false);
        subjectRow.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        subjectRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        promptSubjectField = new JTextField("cybernetic tiger, detailed, 8k");
        promptSubjectField.setFont(new Font("SansSerif", Font.PLAIN, 14));
        subjectRow.add(promptSubjectField, BorderLayout.CENTER);

        btnSuggestSubject = new JButton("✨ Suggest");
        btnSuggestSubject.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnSuggestSubject.putClientProperty("Button.background", ThemeManager.getAccentColor());
        btnSuggestSubject.putClientProperty("Button.foreground", Color.WHITE);
        btnSuggestSubject.setToolTipText("Suggest creative completions for this prompt using local Gemma AI.");
        btnSuggestSubject.addActionListener(e -> {
            if (controller != null) controller.onSuggestSubject();
        });
        subjectRow.add(btnSuggestSubject, BorderLayout.EAST);

        leftPanel.add(subjectRow);
        leftPanel.add(Box.createVerticalStrut(5));

        // Hidden suggestions container
        promptSubjectSuggestionsWrapper = new JPanel(new BorderLayout(5, 5));
        promptSubjectSuggestionsWrapper.setOpaque(false);
        promptSubjectSuggestionsWrapper.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        promptSubjectSuggestionsWrapper.setVisible(false);
        promptSubjectSuggestionsWrapper.setBorder(BorderFactory.createEmptyBorder(5, 0, 10, 0));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        JLabel sugTitle = new JLabel("AI Suggestions:");
        sugTitle.setFont(new Font("SansSerif", Font.BOLD, 11));
        sugTitle.setForeground(Color.GRAY);
        headerPanel.add(sugTitle, BorderLayout.CENTER);

        JButton btnCloseSug = new JButton("✕");
        btnCloseSug.setFont(new Font("SansSerif", Font.PLAIN, 10));
        btnCloseSug.addActionListener(e -> {
            promptSubjectSuggestionsWrapper.setVisible(false);
            if (promptLabLeftPanel != null) {
                promptLabLeftPanel.revalidate();
                promptLabLeftPanel.repaint();
            }
        });
        headerPanel.add(btnCloseSug, BorderLayout.EAST);
        promptSubjectSuggestionsWrapper.add(headerPanel, BorderLayout.NORTH);

        promptSubjectSuggestionsPanel = new JPanel();
        promptSubjectSuggestionsPanel.setLayout(new BoxLayout(promptSubjectSuggestionsPanel, BoxLayout.Y_AXIS));
        promptSubjectSuggestionsPanel.setOpaque(false);

        JScrollPane sugScrollPane = new JScrollPane(promptSubjectSuggestionsPanel);
        sugScrollPane.setOpaque(false);
        sugScrollPane.getViewport().setOpaque(false);
        sugScrollPane.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        sugScrollPane.setPreferredSize(new Dimension(0, 120));
        sugScrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        sugScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sugScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        promptSubjectSuggestionsWrapper.add(sugScrollPane, BorderLayout.CENTER);
        promptSubjectSuggestionsWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 155));
        leftPanel.add(promptSubjectSuggestionsWrapper);

        promptLabLeftPanel = leftPanel;

        // Negative Prompt Field
        leftPanel.add(Box.createVerticalStrut(8));
        JLabel lblNegative = new JLabel("Negative Prompt");
        lblNegative.setFont(promptSubLabelFont);
        lblNegative.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        leftPanel.add(lblNegative);
        leftPanel.add(Box.createVerticalStrut(4));

        promptNegativeField = new JTextField("blurry, low quality, distortion, bad anatomy");
        promptNegativeField.setFont(new Font("SansSerif", Font.PLAIN, 12));
        promptNegativeField.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        promptNegativeField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        leftPanel.add(promptNegativeField);
        leftPanel.add(Box.createVerticalStrut(10));

        // Image Input Field (Hidden by default, shown for img2img/inpaint)
        promptImageInputPanel = new JPanel();
        promptImageInputPanel.setLayout(new BoxLayout(promptImageInputPanel, BoxLayout.Y_AXIS));
        promptImageInputPanel.setOpaque(false);
        promptImageInputPanel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        promptImageInputPanel.setVisible(false);

        JLabel lblInputImage = new JLabel("Input Image (Required)");
        lblInputImage.setFont(promptSubLabelFont);
        lblInputImage.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        promptImageInputPanel.add(lblInputImage);
        promptImageInputPanel.add(Box.createVerticalStrut(4));

        JPanel imageFileRow = new JPanel(new BorderLayout(8, 0));
        imageFileRow.setOpaque(false);
        imageFileRow.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        imageFileRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        promptImageFileField = new JTextField();
        promptImageFileField.setEditable(false);
        promptImageFileField.setFont(new Font("SansSerif", Font.PLAIN, 12));
        imageFileRow.add(promptImageFileField, BorderLayout.CENTER);

        JButton btnBrowseImage = new JButton("Browse...");
        btnBrowseImage.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select Input Image");
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Images", "jpg", "png", "jpeg", "webp"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                selectedInputImage = chooser.getSelectedFile();
                promptImageFileField.setText(selectedInputImage.getAbsolutePath());
            }
        });
        imageFileRow.add(btnBrowseImage, BorderLayout.EAST);

        promptImageInputPanel.add(imageFileRow);
        leftPanel.add(promptImageInputPanel);
        leftPanel.add(Box.createVerticalStrut(15));

        // Image Dimensions & Batch Size
        JLabel lblDimensions = new JLabel("Output Dimensions & Batching");
        lblDimensions.putClientProperty("FlatLaf.styleClass", "h4");
        lblDimensions.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        leftPanel.add(lblDimensions);
        leftPanel.add(Box.createVerticalStrut(5));

        JPanel sizeRow = new JPanel(new GridLayout(1, 3, 8, 0));
        sizeRow.setOpaque(false);
        sizeRow.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        sizeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

        JPanel widthPanel = new JPanel(new BorderLayout(0, 2));
        widthPanel.setOpaque(false);
        JLabel lblWidth = new JLabel("Width");
        lblWidth.setFont(new Font("SansSerif", Font.PLAIN, 11));
        promptWidthSpinner = new JSpinner(new SpinnerNumberModel(512, 64, 4096, 64));
        if (promptWidthSpinner.getEditor() instanceof JSpinner.NumberEditor ne) ne.getFormat().setGroupingUsed(false);
        widthPanel.add(lblWidth, BorderLayout.NORTH);
        widthPanel.add(promptWidthSpinner, BorderLayout.CENTER);

        JPanel heightPanel = new JPanel(new BorderLayout(0, 2));
        heightPanel.setOpaque(false);
        JLabel lblHeight = new JLabel("Height");
        lblHeight.setFont(new Font("SansSerif", Font.PLAIN, 11));
        promptHeightSpinner = new JSpinner(new SpinnerNumberModel(512, 64, 4096, 64));
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
        leftPanel.add(sizeRow);
        leftPanel.add(Box.createVerticalStrut(15));

        // Sampler Parameters
        JLabel lblSampler = new JLabel("Generation & Sampling Parameters");
        lblSampler.putClientProperty("FlatLaf.styleClass", "h4");
        lblSampler.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        leftPanel.add(lblSampler);
        leftPanel.add(Box.createVerticalStrut(5));

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
        leftPanel.add(samplerRow1);
        leftPanel.add(Box.createVerticalStrut(8));

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
        leftPanel.add(samplerRow2);
        leftPanel.add(Box.createVerticalGlue());

        // Event Listeners
        DocumentListener updateJsonDocListener = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { if (controller != null) controller.onUpdatePromptLabJson(); }
            public void removeUpdate(DocumentEvent e) { if (controller != null) controller.onUpdatePromptLabJson(); }
            public void changedUpdate(DocumentEvent e) { if (controller != null) controller.onUpdatePromptLabJson(); }
        };
        promptSubjectField.getDocument().addDocumentListener(updateJsonDocListener);
        promptNegativeField.getDocument().addDocumentListener(updateJsonDocListener);

        promptModelCombo.addActionListener(e -> {
            String selected = (String) promptModelCombo.getSelectedItem();
            if (selected != null && controller != null) {
                controller.onBlueprintSelected(selected);
            }
        });

        promptWidthSpinner.addChangeListener(e -> { if (controller != null) controller.onUpdatePromptLabJson(); });
        promptHeightSpinner.addChangeListener(e -> { if (controller != null) controller.onUpdatePromptLabJson(); });
        promptBatchSizeSpinner.addChangeListener(e -> { if (controller != null) controller.onUpdatePromptLabJson(); });
        promptStepsSpinner.addChangeListener(e -> { if (controller != null) controller.onUpdatePromptLabJson(); });
        promptCfgSpinner.addChangeListener(e -> { if (controller != null) controller.onUpdatePromptLabJson(); });
        promptDenoiseSpinner.addChangeListener(e -> { if (controller != null) controller.onUpdatePromptLabJson(); });
        promptSamplerCombo.addActionListener(e -> { if (controller != null) controller.onUpdatePromptLabJson(); });
        promptSchedulerCombo.addActionListener(e -> { if (controller != null) controller.onUpdatePromptLabJson(); });

        // RIGHT: ComfyUI integration / API payload view
        CardPanel rightPanel = new CardPanel();
        rightPanel.setLayout(new BorderLayout(10, 10));

        JPanel rightTopPanel = new JPanel(new BorderLayout());
        rightTopPanel.setOpaque(false);
        rightTopPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        JLabel apiHeader = new JLabel("ComfyUI API Integration (workflow_api.json)");
        apiHeader.putClientProperty("FlatLaf.styleClass", "h3");
        rightTopPanel.add(apiHeader, BorderLayout.CENTER);

        rightPanel.add(rightTopPanel, BorderLayout.NORTH);

        promptLabRightTabbedPane = new JTabbedPane();
        promptLabRightTabbedPane.putClientProperty("JTabbedPane.tabType", "card");
        promptLabRightTabbedPane.setOpaque(false);

        // TAB A: Image Preview
        JPanel imagePreviewTabPanel = new JPanel(new BorderLayout(10, 10));
        imagePreviewTabPanel.setOpaque(false);

        promptImagePreviewLabel = new JLabel("Your generated image will appear here", SwingConstants.CENTER);
        promptImagePreviewLabel.setFont(new Font("SansSerif", Font.ITALIC, 14));
        promptImagePreviewLabel.setForeground(Color.GRAY);
        promptImagePreviewLabel.putClientProperty("FlatLaf.style", "background: $TextField.background");
        promptImagePreviewLabel.setOpaque(true);

        promptImagePreviewLabel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (currentPreviewImage != null) {
                    scaleAndSetImage(currentPreviewImage);
                }
            }
        });

        JScrollPane previewScroll = new JScrollPane(promptImagePreviewLabel);
        previewScroll.setOpaque(false);
        previewScroll.getViewport().setOpaque(false);
        previewScroll.setBorder(BorderFactory.createEmptyBorder());
        imagePreviewTabPanel.add(previewScroll, BorderLayout.CENTER);

        promptLabProgressBar = new JProgressBar();
        promptLabProgressBar.setStringPainted(true);
        promptLabProgressBar.setVisible(false);
        promptLabProgressBar.setPreferredSize(new Dimension(0, 25));
        imagePreviewTabPanel.add(promptLabProgressBar, BorderLayout.SOUTH);

        promptLabRightTabbedPane.addTab("🖼️ Image Preview", imagePreviewTabPanel);

        // TAB B: JSON Workflow
        promptJsonArea = new JTextArea("{\n  \"prompt\": {}\n}");
        promptJsonArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane jsonScroll = new JScrollPane(promptJsonArea);
        jsonScroll.setOpaque(false);
        jsonScroll.getViewport().setOpaque(false);
        jsonScroll.setBorder(BorderFactory.createEmptyBorder());
        promptLabRightTabbedPane.addTab("📝 JSON Workflow", jsonScroll);

        rightPanel.add(promptLabRightTabbedPane, BorderLayout.CENTER);

        // Bottom panel
        JPanel rightBottomPanel = new JPanel(new BorderLayout(5, 5));
        rightBottomPanel.setOpaque(false);

        promptLabConsole = new JTextArea(4, 20);
        promptLabConsole.setFont(new Font("Monospaced", Font.PLAIN, 12));
        promptLabConsole.setEditable(false);
        promptLabConsole.setText("System: Prompt Lab ready.\n");
        JScrollPane consoleScroll = new JScrollPane(promptLabConsole);
        consoleScroll.setOpaque(false);
        consoleScroll.getViewport().setOpaque(false);
        consoleScroll.setBorder(BorderFactory.createEmptyBorder());
        rightBottomPanel.add(consoleScroll, BorderLayout.CENTER);

        btnSendToComfy = new JButton("🚀 Send to ComfyUI");
        btnSendToComfy.setFont(new Font("SansSerif", Font.BOLD, 14));
        btnSendToComfy.putClientProperty("Button.background", new Color(255, 204, 0));
        btnSendToComfy.putClientProperty("Button.foreground", Color.BLACK);
        btnSendToComfy.setPreferredSize(new Dimension(0, 45));
        btnSendToComfy.addActionListener(e -> {
            if (controller != null) controller.onSendPromptToComfyUI();
        });

        rightBottomPanel.add(btnSendToComfy, BorderLayout.SOUTH);
        rightPanel.add(rightBottomPanel, BorderLayout.SOUTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setOpaque(false);
        splitPane.setDividerLocation(360);
        splitPane.setResizeWeight(0.3);

        add(splitPane, BorderLayout.CENTER);
    }

    public void scaleAndSetImage(Image img) {
        if (img == null) return;
        this.currentPreviewImage = img;
        int pw = promptImagePreviewLabel.getWidth();
        int ph = promptImagePreviewLabel.getHeight();
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
        promptImagePreviewLabel.setIcon(new ImageIcon(scaled));
        promptImagePreviewLabel.setText("");
    }

    public void updateTheme(boolean darkMode) {
        if (promptPresetLabel != null) {
            Color c = UIManager.getColor("PromptLab.presetForeground");
            if (c != null) promptPresetLabel.setForeground(c);
        }
        if (promptImagePreviewLabel != null) {
            promptImagePreviewLabel.setBackground(UIManager.getColor("TextField.background"));
        }
    }

    // Getters & Setters
    public JComboBox<String> getPromptModelCombo() { return promptModelCombo; }
    public JLabel getPromptPresetLabel() { return promptPresetLabel; }
    public JTextField getPromptSubjectField() { return promptSubjectField; }
    public JButton getBtnSuggestSubject() { return btnSuggestSubject; }
    public JPanel getPromptSubjectSuggestionsWrapper() { return promptSubjectSuggestionsWrapper; }
    public JPanel getPromptSubjectSuggestionsPanel() { return promptSubjectSuggestionsPanel; }
    public CardPanel getPromptLabLeftPanel() { return promptLabLeftPanel; }
    public JTextField getPromptNegativeField() { return promptNegativeField; }
    public JPanel getPromptImageInputPanel() { return promptImageInputPanel; }
    public JTextField getPromptImageFileField() { return promptImageFileField; }
    public File getSelectedInputImage() { return selectedInputImage; }
    public void setSelectedInputImage(File selectedInputImage) { this.selectedInputImage = selectedInputImage; }

    public JSpinner getPromptWidthSpinner() { return promptWidthSpinner; }
    public JSpinner getPromptHeightSpinner() { return promptHeightSpinner; }
    public JSpinner getPromptBatchSizeSpinner() { return promptBatchSizeSpinner; }
    public JSpinner getPromptStepsSpinner() { return promptStepsSpinner; }
    public JSpinner getPromptCfgSpinner() { return promptCfgSpinner; }
    public JSpinner getPromptDenoiseSpinner() { return promptDenoiseSpinner; }
    public JComboBox<String> getPromptSamplerCombo() { return promptSamplerCombo; }
    public JComboBox<String> getPromptSchedulerCombo() { return promptSchedulerCombo; }

    public JTabbedPane getPromptLabRightTabbedPane() { return promptLabRightTabbedPane; }
    public JLabel getPromptImagePreviewLabel() { return promptImagePreviewLabel; }
    public JProgressBar getPromptLabProgressBar() { return promptLabProgressBar; }
    public Image getCurrentPreviewImage() { return currentPreviewImage; }
    public void setCurrentPreviewImage(Image currentPreviewImage) { this.currentPreviewImage = currentPreviewImage; }
    public JTextArea getPromptJsonArea() { return promptJsonArea; }
    public JTextArea getPromptLabConsole() { return promptLabConsole; }
    public JButton getBtnSendToComfy() { return btnSendToComfy; }

    public JTextArea getPromptAssembleArea() { return promptAssembleArea; }
    @SuppressWarnings("rawtypes")
    public JComboBox getPromptEnvCombo() { return promptEnvCombo; }
    public JCheckBox getChkPhotorealistic() { return chkPhotorealistic; }
    public JCheckBox getChkOil() { return chkOil; }
    public JCheckBox getChkEngine() { return chkEngine; }
    public JCheckBox getChkAnime() { return chkAnime; }
    public JCheckBox getChkFantasy() { return chkFantasy; }
    public JCheckBox getChkSketch() { return chkSketch; }
}
