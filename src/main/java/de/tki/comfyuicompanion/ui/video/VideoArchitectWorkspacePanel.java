package de.tki.comfyuicompanion.ui.video;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.IVideoPromptOptimizer;
import de.tki.comfyuicompanion.service.impl.Video4jEditorService;
import de.tki.comfyuicompanion.ui.CardPanel;
import de.tki.comfyuicompanion.ui.ThemeManager;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Right workspace panel for Video Architect.
 * Encapsulates the tabbed sequence timeline, scene/video preview area, storyboard JSON editor,
 * execution console log output, and master render pipeline buttons.
 */
public class VideoArchitectWorkspacePanel extends CardPanel {

    private static final Logger logger = LoggerFactory.getLogger(VideoArchitectWorkspacePanel.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final VideoArchitectTimelineCoordinator timelineCoordinator;
    private final Video4jEditorService video4jEditorService;
    private final IVideoPromptOptimizer promptOptimizer;

    private Supplier<JSpinner> widthSpinnerSupplier;
    private Supplier<JSpinner> heightSpinnerSupplier;

    private JTabbedPane videoRightTabbedPane;
    private VideoArchitectSequencePanel sequencePanel;
    private JLabel videoPreviewLabel;
    private Image currentPreviewImage;
    private JTextArea storyboardJsonArea;
    private JTextArea videoConsoleArea;
    private JProgressBar videoProgressBar;
    private JButton btnGenerateScenes;
    private JButton btnMasterRender;

    /**
     * Constructs a new VideoArchitectWorkspacePanel.
     *
     * @param timelineCoordinator  the timeline coordinator for managing scene reordering and dialogs
     * @param video4jEditorService the video editor backend for frame enhancement and preview
     * @param promptOptimizer      the video prompt optimizer
     */
    public VideoArchitectWorkspacePanel(VideoArchitectTimelineCoordinator timelineCoordinator,
                                       Video4jEditorService video4jEditorService,
                                       IVideoPromptOptimizer promptOptimizer) {
        this.timelineCoordinator = timelineCoordinator != null ? timelineCoordinator : new VideoArchitectTimelineCoordinator();
        this.video4jEditorService = video4jEditorService;
        this.promptOptimizer = promptOptimizer;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));

        // Right Top Header
        JPanel rightTopPanel = new JPanel(new BorderLayout());
        rightTopPanel.setOpaque(false);
        rightTopPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        JLabel apiHeader = new JLabel("Video Architect Sequence & Preview");
        apiHeader.putClientProperty("FlatLaf.styleClass", "h3");
        rightTopPanel.add(apiHeader, BorderLayout.CENTER);
        add(rightTopPanel, BorderLayout.NORTH);

        // Right TabbedPane with Card style
        videoRightTabbedPane = new JTabbedPane();
        videoRightTabbedPane.putClientProperty("JTabbedPane.tabType", "card");
        videoRightTabbedPane.setOpaque(false);

        // TAB 1: Timeline Sequence
        sequencePanel = new VideoArchitectSequencePanel(
                () -> showEditSceneDialog(null, true),
                selected -> {
                    if (selected != null) {
                        showEditSceneDialog(selected, false);
                    } else {
                        logToConsole("Select a scene to edit.");
                    }
                },
                selected -> {
                    if (selected != null) {
                        showOpenCvEnhancementDialog(selected);
                    } else {
                        logToConsole("Select a scene to adjust OpenCV visual enhancement.");
                    }
                },
                this::handlePreviewSceneVideo,
                this::handleMoveScene,
                this::handleDeleteScene,
                () -> {
                    if (getTimelineListModel().isEmpty()) return;
                    int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to clear all timeline scenes?", "Clear Timeline", JOptionPane.YES_NO_OPTION);
                    if (confirm == JOptionPane.YES_OPTION) {
                        getTimelineListModel().clear();
                        updateStoryboardJson();
                        logToConsole("Timeline cleared.");
                    }
                },
                this::updatePreviewForScene
        );
        videoRightTabbedPane.addTab("Timeline Sequence", SvgIconFactory.get(AppIcon.VIDEO_ARCHITECT), sequencePanel);

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

        add(videoRightTabbedPane, BorderLayout.CENTER);

        // Bottom Console & Execution Action Panel
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

        // Execution buttons
        JPanel executionRow = new JPanel(new GridLayout(1, 2, 10, 0));
        executionRow.setOpaque(false);
        executionRow.setPreferredSize(new Dimension(0, 45));

        btnGenerateScenes = new JButton("1. Generate All Scenes", SvgIconFactory.get(AppIcon.LAUNCH));
        btnGenerateScenes.setFont(new Font("SansSerif", Font.BOLD, 14));
        btnGenerateScenes.putClientProperty("Button.background", ThemeManager.getAccentColor());
        btnGenerateScenes.putClientProperty("Button.foreground", Color.WHITE);

        btnMasterRender = new JButton("2. Stitch Videos (Master Render)", SvgIconFactory.get(AppIcon.VIDEO_ARCHITECT));
        btnMasterRender.setFont(new Font("SansSerif", Font.BOLD, 14));
        btnMasterRender.putClientProperty("Button.background", new Color(255, 204, 0));
        btnMasterRender.putClientProperty("Button.foreground", Color.BLACK);

        executionRow.add(btnGenerateScenes);
        executionRow.add(btnMasterRender);

        rightBottomPanel.add(executionRow, BorderLayout.SOUTH);
        add(rightBottomPanel, BorderLayout.SOUTH);
    }

    /**
     * Configures suppliers for target width and height spinners used during scene creation.
     *
     * @param widthSpinnerSupplier  supplier for the width spinner
     * @param heightSpinnerSupplier supplier for the height spinner
     */
    public void setSpinnerSuppliers(Supplier<JSpinner> widthSpinnerSupplier, Supplier<JSpinner> heightSpinnerSupplier) {
        this.widthSpinnerSupplier = widthSpinnerSupplier;
        this.heightSpinnerSupplier = heightSpinnerSupplier;
    }

    private JSpinner getWidthSpinner() {
        if (widthSpinnerSupplier != null && widthSpinnerSupplier.get() != null) {
            return widthSpinnerSupplier.get();
        }
        return new JSpinner(new SpinnerNumberModel(832, 256, 3840, 16));
    }

    private JSpinner getHeightSpinner() {
        if (heightSpinnerSupplier != null && heightSpinnerSupplier.get() != null) {
            return heightSpinnerSupplier.get();
        }
        return new JSpinner(new SpinnerNumberModel(480, 256, 2160, 16));
    }

    /**
     * Appends a timestamped log line to the console text area.
     *
     * @param message the message to log
     */
    public void logToConsole(String message) {
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

    /**
     * Asynchronously loads and renders an enhanced preview frame for the given scene.
     *
     * @param scene the scene to preview
     */
    public void updatePreviewForScene(Scene scene) {
        if (scene == null) return;
        Thread.ofVirtual().start(() -> {
            try {
                BufferedImage img = video4jEditorService != null
                        ? video4jEditorService.applyBasicEnhancementAwt(scene, scene.getContrast(), scene.getBrightness())
                        : null;
                SwingUtilities.invokeLater(() -> {
                    if (img != null) {
                        scaleAndSetImage(img);
                    } else {
                        videoPreviewLabel.setIcon(null);
                        videoPreviewLabel.setText("Scene " + scene.getSceneId() + " (No video or frame generated yet)");
                    }
                });
            } catch (RuntimeException ex) {
                logger.warn("Failed to update preview: {}", ex.getMessage());
            }
        });
    }

    /**
     * Scales an image proportionally to fit within the preview label bounds.
     *
     * @param img the source image
     */
    public void scaleAndSetImage(Image img) {
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

    /**
     * Serializes the current timeline list into the storyboard JSON text area.
     */
    public void updateStoryboardJson() {
        timelineCoordinator.updateStoryboardJson(getTimelineListModel(), storyboardJsonArea);
    }

    /**
     * Reorders a scene up or down in the sequence.
     *
     * @param direction -1 to move up, +1 to move down
     */
    public void handleMoveScene(int direction) {
        timelineCoordinator.handleMoveScene(direction, getTimelineListView(), getTimelineListModel(),
                this::logToConsole, this::updateStoryboardJson);
    }

    /**
     * Deletes the currently selected scene from the timeline.
     */
    public void handleDeleteScene() {
        timelineCoordinator.handleDeleteScene(getTimelineListView(), getTimelineListModel(),
                this::logToConsole, this::updateStoryboardJson);
    }

    /**
     * Plays the rendered video of the selected scene using the system default player.
     */
    public void handlePreviewSceneVideo() {
        timelineCoordinator.handlePreviewSceneVideo(getTimelineListView(), this::logToConsole);
    }

    /**
     * Opens the OpenCV contrast/brightness adjustment modal dialog.
     *
     * @param scene the scene to enhance
     */
    public void showOpenCvEnhancementDialog(Scene scene) {
        timelineCoordinator.showOpenCvEnhancementDialog(this, scene, video4jEditorService,
                getTimelineListView(), () -> {
                    updateStoryboardJson();
                    updatePreviewForScene(scene);
                }, this::logToConsole);
    }

    /**
     * Opens the scene edit dialog to modify an existing scene or create a new one.
     *
     * @param targetScene the scene to edit, or null to create new
     * @param isNew       true if creating a new scene
     */
    public void showEditSceneDialog(Scene targetScene, boolean isNew) {
        timelineCoordinator.showEditSceneDialog(this, targetScene, isNew,
                getTimelineListModel(), getTimelineListView(), getWidthSpinner(), getHeightSpinner(),
                promptOptimizer, this::updateStoryboardJson, this::logToConsole);
    }

    /**
     * Returns a snapshot list containing all scenes in the current timeline.
     *
     * @return list of scenes
     */
    public List<Scene> getAllScenes() {
        List<Scene> scenes = new ArrayList<>();
        DefaultListModel<Scene> model = getTimelineListModel();
        if (model != null) {
            for (int i = 0; i < model.size(); i++) {
                scenes.add(model.get(i));
            }
        }
        return scenes;
    }

    /**
     * Sets the action callback when Generate All Scenes button is clicked.
     *
     * @param action the callback to execute
     */
    public void setOnGenerateScenes(Runnable action) {
        if (btnGenerateScenes != null && action != null) {
            btnGenerateScenes.addActionListener(e -> action.run());
        }
    }

    /**
     * Sets the action callback when Master Render button is clicked.
     *
     * @param action the callback to execute
     */
    public void setOnMasterRender(Runnable action) {
        if (btnMasterRender != null && action != null) {
            btnMasterRender.addActionListener(e -> action.run());
        }
    }

    /**
     * Disables or enables interactive workspace buttons during execution.
     *
     * @param disabled true to disable buttons, false to enable
     */
    public void setButtonsDisabled(boolean disabled) {
        VideoArchitectThemeHandler.setButtonsDisabled(disabled, btnGenerateScenes, btnMasterRender,
                getAddSceneBtn(), getEditSceneBtn(), getMoveUpBtn(), getMoveDownBtn(),
                getVisualFilterBtn(), getPlayVideoBtn(), getDeleteBtn(), getClearListBtn());
    }

    public DefaultListModel<Scene> getTimelineListModel() { return sequencePanel != null ? sequencePanel.getTimelineListModel() : null; }
    public JList<Scene> getTimelineListView() { return sequencePanel != null ? sequencePanel.getTimelineListView() : null; }
    public JScrollPane getTimelineScroll() { return sequencePanel != null ? sequencePanel.getTimelineScroll() : null; }
    public JLabel getVideoPreviewLabel() { return videoPreviewLabel; }
    public JTextArea getStoryboardJsonArea() { return storyboardJsonArea; }
    public JTextArea getVideoConsoleArea() { return videoConsoleArea; }
    public JProgressBar getVideoProgressBar() { return videoProgressBar; }
    public JButton getBtnGenerateScenes() { return btnGenerateScenes; }
    public JButton getBtnMasterRender() { return btnMasterRender; }
    public JButton getAddSceneBtn() { return sequencePanel != null ? sequencePanel.getAddSceneBtn() : null; }
    public JButton getEditSceneBtn() { return sequencePanel != null ? sequencePanel.getEditSceneBtn() : null; }
    public JButton getVisualFilterBtn() { return sequencePanel != null ? sequencePanel.getVisualFilterBtn() : null; }
    public JButton getPlayVideoBtn() { return sequencePanel != null ? sequencePanel.getPlayVideoBtn() : null; }
    public JButton getMoveUpBtn() { return sequencePanel != null ? sequencePanel.getMoveUpBtn() : null; }
    public JButton getMoveDownBtn() { return sequencePanel != null ? sequencePanel.getMoveDownBtn() : null; }
    public JButton getDeleteBtn() { return sequencePanel != null ? sequencePanel.getDeleteBtn() : null; }
    public JButton getClearListBtn() { return sequencePanel != null ? sequencePanel.getClearListBtn() : null; }
}
