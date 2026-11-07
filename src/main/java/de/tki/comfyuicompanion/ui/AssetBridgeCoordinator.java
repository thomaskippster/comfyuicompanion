package de.tki.comfyuicompanion.ui;

import de.tki.comfyuicompanion.controller.PromptLabImageDropHandler;
import de.tki.comfyuicompanion.domain.ComfyGenerationMetadata;
import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.PngMetadataReaderService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.dialog.GenerationMetadataDialog;
import de.tki.comfyuicompanion.ui.dialog.RecentGenerationsAssetPickerDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.swing.DefaultListModel;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Image;
import java.awt.Window;
import java.io.File;

/**
 * Central coordinator service mediating asset and parameter continuity between:
 * <ul>
 *     <li><b>Output Gallery:</b> image inspection, metadata extraction, send-to actions</li>
 *     <li><b>Image Lab / PromptLab:</b> preview loading, parameter restoration, remixing</li>
 *     <li><b>Video Architect:</b> keyframe assignment, start images, multi-scene continuity</li>
 * </ul>
 */
@Service
public class AssetBridgeCoordinator {

    private static final Logger log = LoggerFactory.getLogger(AssetBridgeCoordinator.class);

    private final ConfigService configService;
    private final PngMetadataReaderService metadataReaderService;

    private JTabbedPane mainTabs;
    private PromptLabView promptLabView;
    private PromptLabView.PromptLabController promptLabController;
    private VideoArchitectTab videoArchitectTab;

    @Autowired
    public AssetBridgeCoordinator(ConfigService configService, PngMetadataReaderService metadataReaderService) {
        this.configService = configService;
        this.metadataReaderService = metadataReaderService != null ? metadataReaderService : new PngMetadataReaderService();
    }

    public AssetBridgeCoordinator(ConfigService configService) {
        this(configService, new PngMetadataReaderService());
    }

    public void registerUiReferences(JTabbedPane mainTabs,
                                    PromptLabView promptLabView,
                                    PromptLabView.PromptLabController promptLabController,
                                    VideoArchitectTab videoArchitectTab) {
        this.mainTabs = mainTabs;
        this.promptLabView = promptLabView;
        this.promptLabController = promptLabController;
        this.videoArchitectTab = videoArchitectTab;
    }

    public PngMetadataReaderService getMetadataReaderService() {
        return metadataReaderService;
    }

    public ConfigService getConfigService() {
        return configService;
    }

    /**
     * Inspects the generation metadata embedded in a PNG file and displays the inspection dialog.
     *
     * @param owner  parent window
     * @param target image file to inspect
     */
    public void showMetadataDialog(Window owner, File target) {
        if (target == null || !target.exists()) return;
        ComfyGenerationMetadata meta = metadataReaderService.extractMetadata(target);
        GenerationMetadataDialog dlg = new GenerationMetadataDialog(owner, target, meta, m -> sendToImageLab(target, true));
        dlg.setVisible(true);
    }

    /**
     * Sends an image to Image Lab, updating the preview canvas and optionally restoring all
     * embedded sampling parameters, prompts, and blueprint graph.
     *
     * @param imageFile       target image file
     * @param applyParameters whether to restore prompts, seeds, models, and sampling parameters
     */
    public void sendToImageLab(File imageFile, boolean applyParameters) {
        if (imageFile == null || !imageFile.exists()) return;

        if (promptLabView != null) {
            try {
                Image img = ImageIO.read(imageFile);
                if (img != null) {
                    promptLabView.scaleAndSetImage(img);
                }
            } catch (Exception ex) {
                log.debug("Failed loading preview in Image Lab: {}", ex.getMessage());
            }

            if (applyParameters) {
                PromptLabImageDropHandler.importFromPngFile(imageFile, promptLabView, promptLabController, false);
            }
        }

        switchToTab("Image Lab");
    }

    /**
     * Sets the specified image as the global speaker / start keyframe in Video Architect.
     *
     * @param imageFile keyframe image file
     */
    public void sendToVideoArchitectAsSpeaker(File imageFile) {
        if (imageFile == null || !imageFile.exists()) return;

        if (videoArchitectTab != null && videoArchitectTab.getConfigPanel() != null) {
            String path = imageFile.getAbsolutePath();
            videoArchitectTab.getConfigPanel().getSpeakerImageField().setText(path);
            if (configService != null) {
                configService.setSpeakerImagePath(path);
            }
            if (videoArchitectTab.getWorkspacePanel() != null) {
                videoArchitectTab.getWorkspacePanel().logToConsole("🎬 Assigned start/speaker image: " + imageFile.getName());
            }
        }

        switchToTab("Video Architect");
    }

    /**
     * Appends a new scene to the Video Architect sequence with the specified image as keyframe/start image.
     *
     * @param imageFile  keyframe image file
     * @param promptHint optional initial prompt description
     */
    public void sendToVideoArchitectAsNewScene(File imageFile, String promptHint) {
        if (imageFile == null || !imageFile.exists()) return;

        if (videoArchitectTab != null && videoArchitectTab.getWorkspacePanel() != null) {
            DefaultListModel<Scene> model = videoArchitectTab.getWorkspacePanel().getTimelineListModel();
            if (model != null) {
                int nextIndex = model.size() + 1;
                Scene sc = new Scene();
                sc.setSceneId("S" + nextIndex);
                sc.setSpeakerImagePath(imageFile.getAbsolutePath());
                if (promptHint != null && !promptHint.isBlank()) {
                    sc.setPrompt(promptHint.trim());
                } else {
                    sc.setPrompt("Cinematic shot based on keyframe " + imageFile.getName());
                }
                sc.setStartFrame(0);
                sc.setEndFrame(5 * 24); // 5 seconds default
                model.addElement(sc);

                videoArchitectTab.getWorkspacePanel().logToConsole(
                        "🎬 Created Scene " + sc.getSceneId() + " with start keyframe: " + imageFile.getName());
            }
        }

        switchToTab("Video Architect");
    }

    /**
     * Assigns the given image as the start keyframe for the currently selected scene in Video Architect.
     *
     * @param imageFile keyframe image file
     */
    public void assignKeyframeToSelectedScene(File imageFile) {
        if (imageFile == null || !imageFile.exists()) return;

        if (videoArchitectTab != null && videoArchitectTab.getWorkspacePanel() != null) {
            Scene selected = videoArchitectTab.getWorkspacePanel().getTimelineListView() != null
                    ? videoArchitectTab.getWorkspacePanel().getTimelineListView().getSelectedValue() : null;

            if (selected != null) {
                selected.setSpeakerImagePath(imageFile.getAbsolutePath());
                videoArchitectTab.getWorkspacePanel().getTimelineListView().repaint();
                videoArchitectTab.getWorkspacePanel().logToConsole(
                        "🎬 Updated keyframe for Scene " + selected.getSceneId() + ": " + imageFile.getName());
                switchToTab("Video Architect");
            } else {
                // If no scene is selected, offer to add as new scene or set as global start image
                sendToVideoArchitectAsNewScene(imageFile, null);
            }
        } else {
            switchToTab("Video Architect");
        }
    }

    /**
     * Prompts the user with the visual recent generations picker dialog.
     *
     * @param owner parent window
     * @param title custom dialog title
     * @return selected file, or {@code null} if cancelled
     */
    public File pickRecentAsset(Window owner, String title) {
        RecentGenerationsAssetPickerDialog dlg = new RecentGenerationsAssetPickerDialog(owner, configService, title);
        dlg.setVisible(true);
        return dlg.getSelectedFile();
    }

    private void switchToTab(String titleFragment) {
        if (mainTabs == null) return;
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < mainTabs.getTabCount(); i++) {
                String title = mainTabs.getTitleAt(i);
                if (title != null && title.toLowerCase().contains(titleFragment.toLowerCase())) {
                    mainTabs.setSelectedIndex(i);
                    return;
                }
            }
        });
    }
}
