package de.tki.comfyuicompanion.ui.video;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.IVideoPromptOptimizer;
import de.tki.comfyuicompanion.service.impl.Video4jEditorService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import java.awt.Desktop;
import java.io.File;
import java.util.function.Consumer;

/**
 * Coordinator for Video Architect timeline interactions, including scene reordering,
 * deletion, desktop video previews, storyboard JSON synchronization, and dialog invocations.
 */
public class VideoArchitectTimelineCoordinator {

    private static final Logger logger = LoggerFactory.getLogger(VideoArchitectTimelineCoordinator.class);

    /**
     * Reorders a scene up or down in the timeline list model.
     *
     * @param direction         -1 to move up, +1 to move down
     * @param timelineListView  the JList showing scenes
     * @param timelineListModel the underlying list model
     * @param loggerFn          logging consumer for console messages
     * @param onUpdated         callback invoked when the timeline changes
     */
    public void handleMoveScene(int direction, JList<Scene> timelineListView,
                                DefaultListModel<Scene> timelineListModel,
                                Consumer<String> loggerFn, Runnable onUpdated) {
        int index = timelineListView.getSelectedIndex();
        if (index < 0) {
            loggerFn.accept("Select a scene to reorder.");
            return;
        }
        int newIndex = index + direction;
        if (newIndex >= 0 && newIndex < timelineListModel.size()) {
            Scene scene = timelineListModel.remove(index);
            timelineListModel.add(newIndex, scene);
            timelineListView.setSelectedIndex(newIndex);
            if (onUpdated != null) onUpdated.run();
            loggerFn.accept("Moved scene " + scene.getSceneId() + " to position " + (newIndex + 1));
        }
    }

    /**
     * Deletes the currently selected scene from the timeline list.
     *
     * @param timelineListView  the JList showing scenes
     * @param timelineListModel the underlying list model
     * @param loggerFn          logging consumer for console messages
     * @param onUpdated         callback invoked when the timeline changes
     */
    public void handleDeleteScene(JList<Scene> timelineListView,
                                  DefaultListModel<Scene> timelineListModel,
                                  Consumer<String> loggerFn, Runnable onUpdated) {
        int index = timelineListView.getSelectedIndex();
        if (index >= 0) {
            Scene removed = timelineListModel.remove(index);
            if (onUpdated != null) onUpdated.run();
            loggerFn.accept("Deleted Scene: " + removed.getSceneId());
        } else {
            loggerFn.accept("No scene selected to delete!");
        }
    }

    /**
     * Opens the rendered video of the currently selected scene in the system default media player.
     *
     * @param timelineListView the JList showing scenes
     * @param loggerFn         logging consumer for console messages
     */
    public void handlePreviewSceneVideo(JList<Scene> timelineListView, Consumer<String> loggerFn) {
        Scene selected = timelineListView.getSelectedValue();
        if (selected == null) {
            loggerFn.accept("Select a scene to preview.");
            return;
        }
        String path = selected.getVideoPath();
        if (path == null || path.trim().isEmpty()) {
            loggerFn.accept("Scene " + selected.getSceneId() + " has no generated video yet.");
            return;
        }
        File vf = new File(path);
        if (!vf.exists()) {
            loggerFn.accept("Video file not found at: " + path);
            return;
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(vf);
                loggerFn.accept("Opening video for scene: " + selected.getSceneId());
            } else {
                loggerFn.accept("Desktop open not supported. Video: " + path);
            }
        } catch (Exception ex) {
            logger.error("Failed to open video file: {}", ex.getMessage());
            loggerFn.accept("Failed to open video: " + ex.getMessage());
        }
    }

    /**
     * Displays the OpenCV visual enhancement modal dialog.
     *
     * @param parent               the parent panel
     * @param scene                the target scene to enhance
     * @param video4jEditorService the video editor backend
     * @param timelineListView     the list view to repaint
     * @param onUpdated            callback to sync storyboard and preview
     * @param loggerFn             logging consumer
     */
    public void showOpenCvEnhancementDialog(JPanel parent, Scene scene,
                                           Video4jEditorService video4jEditorService,
                                           JList<Scene> timelineListView,
                                           Runnable onUpdated, Consumer<String> loggerFn) {
        new OpenCvEnhancementDialog(parent, scene, video4jEditorService, () -> {
            timelineListView.repaint();
            if (onUpdated != null) onUpdated.run();
        }, loggerFn);
    }

    /**
     * Displays the scene edit modal dialog.
     *
     * @param parent            the parent panel
     * @param targetScene       the scene to edit, or null if creating new
     * @param isNew             true if creating a new scene
     * @param timelineListModel the list model
     * @param timelineListView  the list view
     * @param videoWidthSpinner spinner providing target width
     * @param videoHeightSpinner spinner providing target height
     * @param promptOptimizer   the prompt optimizer
     * @param onUpdated         callback when saved
     * @param loggerFn          logging consumer
     */
    public void showEditSceneDialog(JPanel parent, Scene targetScene, boolean isNew,
                                   DefaultListModel<Scene> timelineListModel,
                                   JList<Scene> timelineListView,
                                   JSpinner videoWidthSpinner,
                                   JSpinner videoHeightSpinner,
                                   IVideoPromptOptimizer promptOptimizer,
                                   Runnable onUpdated, Consumer<String> loggerFn) {
        new SceneEditDialog(parent, targetScene, isNew,
                timelineListModel.size() + 1,
                (Integer) videoWidthSpinner.getValue(),
                (Integer) videoHeightSpinner.getValue(),
                promptOptimizer,
                sc -> {
                    if (isNew) {
                        timelineListModel.addElement(sc);
                        loggerFn.accept("Added new Scene: " + sc.getSceneId());
                    } else {
                        timelineListView.repaint();
                        loggerFn.accept("Updated Scene: " + sc.getSceneId());
                    }
                    if (onUpdated != null) onUpdated.run();
                });
    }

    /**
     * Serializes all scenes in the list model into formatted storyboard JSON in the text area.
     *
     * @param timelineListModel  the list model of scenes
     * @param storyboardJsonArea the target text area
     */
    public void updateStoryboardJson(DefaultListModel<Scene> timelineListModel, JTextArea storyboardJsonArea) {
        if (storyboardJsonArea == null) return;
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
}
