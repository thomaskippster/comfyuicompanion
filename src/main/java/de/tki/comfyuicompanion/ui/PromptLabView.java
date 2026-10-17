package de.tki.comfyuicompanion.ui;

import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
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

    private final PromptLabParametersPanel parametersPanel = new PromptLabParametersPanel();

    private JTabbedPane promptLabRightTabbedPane;
    private JLabel promptImagePreviewLabel;
    private JProgressBar promptLabProgressBar;
    private Image currentPreviewImage;
    private JTextArea promptJsonArea;
    private JTextArea promptLabConsole;
    private JButton btnSendToComfy;

    // Dummy objects for backward compatibility with unused fields
    private final JTextArea promptAssembleArea = new JTextArea();
    @SuppressWarnings("rawtypes") private final JComboBox promptEnvCombo = new JComboBox();
    private final JCheckBox chkPhotorealistic = new JCheckBox(), chkOil = new JCheckBox(), chkEngine = new JCheckBox();
    private final JCheckBox chkAnime = new JCheckBox(), chkFantasy = new JCheckBox(), chkSketch = new JCheckBox();

    /** Constructs a new PromptLabView and initializes the UI components. */
    public PromptLabView() { initUI(); }

    public void setController(PromptLabController controller) { this.controller = controller; }
    public PromptLabController getController() { return controller; }

    private void initUI() {
        setOpaque(false);
        setLayout(new BorderLayout(15, 15));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // LEFT: Image Lab controls (Blueprint selection + Generic execution parameters)
        CardPanel leftPanel = new CardPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setMinimumSize(new Dimension(320, 200));

        // Blueprint / Workflow Selection
        JLabel lblBlueprint = new JLabel("Blueprint / Workflow");
        lblBlueprint.putClientProperty("FlatLaf.styleClass", "h4");
        lblBlueprint.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        leftPanel.add(lblBlueprint);
        leftPanel.add(Box.createVerticalStrut(5));

        JPanel modelRow = new JPanel(new BorderLayout(5, 0));
        modelRow.setOpaque(false);
        modelRow.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        modelRow.setPreferredSize(new Dimension(100, 35));
        modelRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));

        promptModelCombo = new JComboBox<>(new String[]{"No ready offline blueprints available"});
        promptModelCombo.setEditable(false);
        promptModelCombo.setEnabled(false);
        promptModelCombo.setFont(new Font("SansSerif", Font.PLAIN, 14));
        promptModelCombo.setMinimumSize(new Dimension(80, 32));
        promptModelCombo.setPreferredSize(new Dimension(100, 32));
        modelRow.add(promptModelCombo, BorderLayout.CENTER);

        JButton btnRefreshModels = new JButton(SvgIconFactory.get(AppIcon.REFRESH));
        btnRefreshModels.setToolTipText("Refresh models list from ComfyUI");
        btnRefreshModels.addActionListener(e -> {
            if (controller != null) controller.onRefreshModels();
        });
        modelRow.add(btnRefreshModels, BorderLayout.EAST);

        leftPanel.add(modelRow);
        leftPanel.add(Box.createVerticalStrut(6));

        promptPresetLabel = new JLabel("⚠️ No ready blueprints or local models detected") {
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
        promptPresetLabel.setMinimumSize(new Dimension(80, 20));
        promptPresetLabel.setPreferredSize(new Dimension(100, 20));
        promptPresetLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
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
        subjectRow.setPreferredSize(new Dimension(100, 38));
        subjectRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        promptSubjectField = new JTextField("cybernetic tiger, detailed, 8k");
        promptSubjectField.setFont(new Font("SansSerif", Font.PLAIN, 14));
        promptSubjectField.setMinimumSize(new Dimension(80, 34));
        promptSubjectField.setPreferredSize(new Dimension(100, 34));
        subjectRow.add(promptSubjectField, BorderLayout.CENTER);

        btnSuggestSubject = new JButton("Suggest", SvgIconFactory.get(AppIcon.SUGGEST));
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

        JButton btnCloseSug = new JButton(SvgIconFactory.get(AppIcon.CLOSE, 10));
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
        promptNegativeField.setMinimumSize(new Dimension(80, 32));
        promptNegativeField.setPreferredSize(new Dimension(100, 32));
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

        // Generation, Dimensions & Sampling Parameters
        leftPanel.add(parametersPanel);

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

        parametersPanel.attachChangeListener(() -> {
            if (controller != null) controller.onUpdatePromptLabJson();
        });

        // RIGHT: ComfyUI integration / API payload view
        CardPanel rightPanel = new CardPanel();
        rightPanel.setLayout(new BorderLayout(10, 10));

        JPanel rightTopPanel = new JPanel(new BorderLayout(8, 0));
        rightTopPanel.setOpaque(false);
        rightTopPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        JLabel apiHeader = new JLabel("ComfyUI API Integration (workflow_api.json)");
        apiHeader.putClientProperty("FlatLaf.styleClass", "h3");
        rightTopPanel.add(apiHeader, BorderLayout.CENTER);

        JPanel rightHeaderActions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
        rightHeaderActions.setOpaque(false);

        JButton btnRemixPng = new JButton("Remix PNG...", SvgIconFactory.get(AppIcon.GALLERY, 14));
        btnRemixPng.setFont(new Font("SansSerif", Font.PLAIN, 12));
        btnRemixPng.setToolTipText("Load prompt, seed, and workflow from a ComfyUI PNG image (or Drag & Drop a PNG onto the Image Lab)");
        btnRemixPng.addActionListener(e -> de.tki.comfyuicompanion.controller.PromptLabImageDropHandler.promptAndImportPng(this, controller));
        rightHeaderActions.add(btnRemixPng);

        JButton btnMatrixGrid = new JButton("Matrix / XY Grid", SvgIconFactory.get(AppIcon.IMAGE_LAB, 14));
        btnMatrixGrid.setFont(new Font("SansSerif", Font.PLAIN, 12));
        btnMatrixGrid.setToolTipText("Open Batch / Prompt Matrix XY-Grid to compare {a|b} prompt variations and CFG sweeps side-by-side");
        btnMatrixGrid.addActionListener(e -> {
            if (onOpenMatrixGrid != null) onOpenMatrixGrid.run();
        });
        rightHeaderActions.add(btnMatrixGrid);
        rightTopPanel.add(rightHeaderActions, BorderLayout.EAST);

        rightPanel.add(rightTopPanel, BorderLayout.NORTH);

        promptLabRightTabbedPane = new JTabbedPane();
        promptLabRightTabbedPane.putClientProperty("JTabbedPane.tabType", "card");
        promptLabRightTabbedPane.setOpaque(false);

        // TAB A: Image Preview
        JPanel imagePreviewTabPanel = new JPanel(new BorderLayout(10, 10));
        imagePreviewTabPanel.setOpaque(false);

        promptImagePreviewLabel = new JLabel("Your generated image will appear here (or Drag & Drop a ComfyUI PNG to remix)", SwingConstants.CENTER);
        promptImagePreviewLabel.setFont(new Font("SansSerif", Font.ITALIC, 14));
        promptImagePreviewLabel.setForeground(Color.GRAY);
        promptImagePreviewLabel.putClientProperty("FlatLaf.style", "background: $TextField.background");
        promptImagePreviewLabel.setOpaque(true);
        promptImagePreviewLabel.setMinimumSize(new Dimension(64, 64));
        promptImagePreviewLabel.setPreferredSize(new Dimension(64, 64));

        promptImagePreviewLabel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (currentPreviewImage != null) {
                    scaleAndSetImage(currentPreviewImage);
                }
            }
        });
        imagePreviewTabPanel.add(promptImagePreviewLabel, BorderLayout.CENTER);

        promptLabProgressBar = new JProgressBar();
        promptLabProgressBar.setStringPainted(true);
        promptLabProgressBar.setVisible(false);
        promptLabProgressBar.setPreferredSize(new Dimension(0, 25));
        imagePreviewTabPanel.add(promptLabProgressBar, BorderLayout.SOUTH);

        promptLabRightTabbedPane.addTab("Image Preview", SvgIconFactory.get(AppIcon.GALLERY), imagePreviewTabPanel);

        // TAB B: JSON Workflow
        promptJsonArea = new JTextArea("{\n  \"prompt\": {}\n}");
        promptJsonArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane jsonScroll = new JScrollPane(promptJsonArea);
        jsonScroll.setOpaque(false);
        jsonScroll.getViewport().setOpaque(false);
        jsonScroll.setBorder(BorderFactory.createEmptyBorder());
        promptLabRightTabbedPane.addTab("JSON Workflow", SvgIconFactory.get(AppIcon.FILE_TEXT), jsonScroll);

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

        btnSendToComfy = new JButton("Send to ComfyUI", SvgIconFactory.get(AppIcon.SEND));
        btnSendToComfy.setFont(new Font("SansSerif", Font.BOLD, 14));
        btnSendToComfy.putClientProperty("Button.background", new Color(255, 204, 0));
        btnSendToComfy.putClientProperty("Button.foreground", Color.BLACK);
        btnSendToComfy.setPreferredSize(new Dimension(0, 45));
        btnSendToComfy.setEnabled(false);
        btnSendToComfy.setToolTipText("No ready blueprints or local models available. Download models or configure paths first.");
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
        de.tki.comfyuicompanion.controller.PromptLabImageDropHandler.installDropTargets(this, this.controller);
    }

    private Runnable onOpenMatrixGrid;

    /** Sets callback for opening the Batch / Prompt Matrix XY-Grid dialog. */
    public void setOnOpenMatrixGrid(Runnable onOpenMatrixGrid) { this.onOpenMatrixGrid = onOpenMatrixGrid; }

    /**
     * Scales the provided image to fit within the preview label while maintaining aspect ratio,
     * and sets it as the label's icon.
     *
     * @param img the image to scale and display
     */
    public void scaleAndSetImage(Image img) {
        if (img == null) return;
        boolean sameImage = (this.currentPreviewImage == img);
        this.currentPreviewImage = img;
        int pw = Math.max(64, promptImagePreviewLabel.getWidth() > 8 ? promptImagePreviewLabel.getWidth() - 8 : 512);
        int ph = Math.max(64, promptImagePreviewLabel.getHeight() > 8 ? promptImagePreviewLabel.getHeight() - 8 : 512);
        int iw = img.getWidth(null);
        int ih = img.getHeight(null);
        if (iw <= 0 || ih <= 0) return;

        double scale = Math.min((double) pw / iw, (double) ph / ih);
        int tw = Math.max(1, (int) (iw * scale));
        int th = Math.max(1, (int) (ih * scale));
        if (sameImage && promptImagePreviewLabel.getIcon() instanceof ImageIcon existing
                && Math.abs(existing.getIconWidth() - tw) <= 2 && Math.abs(existing.getIconHeight() - th) <= 2) {
            return;
        }
        Image scaled = img.getScaledInstance(tw, th, Image.SCALE_SMOOTH);
        promptImagePreviewLabel.setIcon(new ImageIcon(scaled));
        promptImagePreviewLabel.setText("");
    }

    @Override
    public void addNotify() {
        super.addNotify();
        SwingUtilities.updateComponentTreeUI(this);
        updateTheme(ThemeManager.isDarkMode());
    }

    /**
     * Updates the UI components to reflect the active theme (light/dark).
     *
     * @param darkMode true if dark mode is active, false otherwise
     */
    public void updateTheme(boolean darkMode) {
        if (promptPresetLabel != null) {
            Color c = UIManager.getColor("PromptLab.presetForeground");
            if (c != null) promptPresetLabel.setForeground(c);
        }
        if (promptImagePreviewLabel != null) {
            promptImagePreviewLabel.setBackground(UIManager.getColor("TextField.background"));
        }
    }

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
    public JSpinner getPromptWidthSpinner() { return parametersPanel.getWidthSpinner(); }
    public JSpinner getPromptHeightSpinner() { return parametersPanel.getHeightSpinner(); }
    public JSpinner getPromptBatchSizeSpinner() { return parametersPanel.getBatchSizeSpinner(); }
    public JSpinner getPromptStepsSpinner() { return parametersPanel.getStepsSpinner(); }
    public JSpinner getPromptCfgSpinner() { return parametersPanel.getCfgSpinner(); }
    public JSpinner getPromptDenoiseSpinner() { return parametersPanel.getDenoiseSpinner(); }
    public JComboBox<String> getPromptSamplerCombo() { return parametersPanel.getSamplerCombo(); }
    public JComboBox<String> getPromptSchedulerCombo() { return parametersPanel.getSchedulerCombo(); }
    public JTabbedPane getPromptLabRightTabbedPane() { return promptLabRightTabbedPane; }
    public JLabel getPromptImagePreviewLabel() { return promptImagePreviewLabel; }
    public JProgressBar getPromptLabProgressBar() { return promptLabProgressBar; }
    public Image getCurrentPreviewImage() { return currentPreviewImage; }
    public void setCurrentPreviewImage(Image currentPreviewImage) { this.currentPreviewImage = currentPreviewImage; }
    public JTextArea getPromptJsonArea() { return promptJsonArea; }
    public JTextArea getPromptLabConsole() { return promptLabConsole; }
    public JButton getBtnSendToComfy() { return btnSendToComfy; }
    public JTextArea getPromptAssembleArea() { return promptAssembleArea; }
    @SuppressWarnings("rawtypes") public JComboBox getPromptEnvCombo() { return promptEnvCombo; }
    public JCheckBox getChkPhotorealistic() { return chkPhotorealistic; }
    public JCheckBox getChkOil() { return chkOil; }
    public JCheckBox getChkEngine() { return chkEngine; }
    public JCheckBox getChkAnime() { return chkAnime; }
    public JCheckBox getChkFantasy() { return chkFantasy; }
    public JCheckBox getChkSketch() { return chkSketch; }
}
