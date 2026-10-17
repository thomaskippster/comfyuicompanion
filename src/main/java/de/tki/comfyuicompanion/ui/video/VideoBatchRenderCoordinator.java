package de.tki.comfyuicompanion.ui.video;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import de.tki.comfyuicompanion.service.impl.ComfyPipelineService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.Video4jEditorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Coordinates batch rendering of scene video segments in ComfyUI,
 * retry mechanisms, disk resolution of generated clips, and master video stitching via FFmpeg.
 */
public class VideoBatchRenderCoordinator {

    private static final Logger logger = LoggerFactory.getLogger(VideoBatchRenderCoordinator.class);

    private final ConfigService configService;
    private final ComfyPipelineService comfyPipelineService;
    private final Video4jEditorService video4jEditorService;
    private final IComfyLifecycleService lifecycleService;

    /**
     * Constructs a new VideoBatchRenderCoordinator.
     *
     * @param configService        configuration service for output directory lookup
     * @param comfyPipelineService pipeline service executing ComfyUI scene generations
     * @param video4jEditorService editor service stitching video clips
     * @param lifecycleService     lifecycle service monitoring ComfyUI health
     */
    public VideoBatchRenderCoordinator(ConfigService configService,
                                       ComfyPipelineService comfyPipelineService,
                                       Video4jEditorService video4jEditorService,
                                       IComfyLifecycleService lifecycleService) {
        this.configService = configService;
        this.comfyPipelineService = comfyPipelineService;
        this.video4jEditorService = video4jEditorService;
        this.lifecycleService = lifecycleService;
    }

    /**
     * Executes video generation for all scenes currently present in the timeline.
     *
     * @param parent               parent component for dialogs
     * @param scenes               list of scenes to generate
     * @param speakerPath          optional start image path
     * @param width                video width
     * @param height               video height
     * @param timelineListView     timeline list component to repaint
     * @param progressBar          progress bar
     * @param buttonsDisabledSetter callback to enable/disable UI controls
     * @param consoleLogger        console output callback
     * @param previewUpdater       callback to update current scene preview
     */
    public void executeVideoGeneration(Component parent,
                                       List<Scene> scenes,
                                       String speakerPath,
                                       int width,
                                       int height,
                                       JList<Scene> timelineListView,
                                       JProgressBar progressBar,
                                       Consumer<Boolean> buttonsDisabledSetter,
                                       Consumer<String> consoleLogger,
                                       Consumer<Scene> previewUpdater) {
        if (scenes == null || scenes.isEmpty()) {
            consoleLogger.accept("Timeline is empty! Add scenes first.");
            return;
        }

        for (Scene sc : scenes) {
            sc.setWidth(width);
            sc.setHeight(height);
            sc.setSpeakerImagePath(speakerPath != null && !speakerPath.trim().isEmpty() ? speakerPath.trim() : null);
        }

        buttonsDisabledSetter.accept(true);
        progressBar.setValue(0);
        progressBar.setVisible(true);

        Thread.ofVirtual().start(() -> {
            try {
                consoleLogger.accept("Checking ComfyUI server status...");
                ensureComfyUiHealthy();

                for (int i = 0; i < scenes.size(); i++) {
                    Scene scene = scenes.get(i);
                    final int currentIdx = i + 1;
                    final int totalScenes = scenes.size();

                    File output = null;
                    int maxRetries = 3;
                    boolean success = false;

                    for (int attempt = 1; attempt <= maxRetries; attempt++) {
                        final int att = attempt;
                        SwingUtilities.invokeLater(() -> progressBar.setValue((int) (((double) currentIdx / totalScenes) * 100)));
                        consoleLogger.accept("Generating Scene " + currentIdx + "/" + totalScenes + ": " + scene.getSceneId() + (att > 1 ? " (Attempt " + att + ")" : ""));

                        try {
                            output = comfyPipelineService.generateSceneStrict(scene).join();
                            if (output != null && output.exists() && output.length() > 1024) {
                                success = true;
                                break;
                            } else {
                                Thread.sleep(2000);
                            }
                        } catch (Exception e) {
                            Thread.sleep(2000);
                        }
                    }

                    if (!success) {
                        throw new Exception("Could not generate video for Scene " + scene.getSceneId() + " after " + maxRetries + " attempts.");
                    }

                    if (output != null && output.exists()) {
                        scene.setVideoPath(output.getAbsolutePath());
                    }
                    SwingUtilities.invokeLater(() -> {
                        timelineListView.repaint();
                        previewUpdater.accept(scene);
                    });
                }

                SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(false);
                    buttonsDisabledSetter.accept(false);
                    consoleLogger.accept("All " + scenes.size() + " scene videos generated! Click 'Stitch Videos' to combine.");
                });
            } catch (Exception ex) {
                logger.error("Video generation failed", ex);
                SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(false);
                    buttonsDisabledSetter.accept(false);
                    consoleLogger.accept("Generation failed: " + ex.getMessage());
                    JOptionPane.showMessageDialog(parent, "Video generation failed:\n" + ex.getMessage(), "Generation Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    /**
     * Re-attaches missing scene video files from output folders or regenerates them,
     * then stitches all timeline videos into a master render file.
     *
     * @param parent               parent component for dialogs
     * @param scenes               list of scenes to stitch
     * @param progressBar          progress bar
     * @param buttonsDisabledSetter callback to enable/disable UI controls
     * @param consoleLogger        console output callback
     */
    public void executeMasterRender(Component parent,
                                    List<Scene> scenes,
                                    JProgressBar progressBar,
                                    Consumer<Boolean> buttonsDisabledSetter,
                                    Consumer<String> consoleLogger) {
        if (scenes == null || scenes.isEmpty()) {
            consoleLogger.accept("Timeline is empty!");
            return;
        }

        buttonsDisabledSetter.accept(true);
        progressBar.setValue(50);
        progressBar.setVisible(true);

        Thread.ofVirtual().start(() -> {
            try {
                consoleLogger.accept("Checking scene videos...");

                List<Scene> needsRegen = new ArrayList<>();
                for (Scene scene : scenes) {
                    File vf = scene.getVideoPath() == null ? null : new File(scene.getVideoPath());
                    if (vf == null || !vf.exists() || vf.length() < 1024) {
                        File[] roots = new File[]{
                                new File(System.getProperty("user.dir")),
                                new File(new File("").getAbsolutePath()),
                                new File(configService != null ? configService.getResolvedOutputDir() : "output"),
                                new File(new File(configService != null ? configService.getResolvedOutputDir() : "output"), "video")
                        };
                        String[] patterns = new String[]{
                                "scene_" + scene.getSceneId() + "_final.mp4",
                                "scene_" + scene.getSceneId() + "_simulated.mp4",
                                "videoarchitect_" + scene.getSceneId() + "_00001_.mp4",
                                "videoarchitect_" + scene.getSceneId() + ".mp4"
                        };
                        File found = null;
                        outer:
                        for (File r : roots) {
                            if (r == null || !r.isDirectory()) continue;
                            for (String p : patterns) {
                                File candidate = new File(r, p);
                                if (candidate.exists() && candidate.length() >= 1024) { found = candidate; break outer; }
                            }
                        }
                        if (found != null) {
                            logger.info("[VideoArchitect] Re-attached scene {} video: {}", scene.getSceneId(), found.getAbsolutePath());
                            scene.setVideoPath(found.getAbsolutePath());
                        } else {
                            needsRegen.add(scene);
                        }
                    }
                }

                if (!needsRegen.isEmpty()) {
                    consoleLogger.accept("Regenerating " + needsRegen.size() + " missing scene(s)...");
                    for (int ri = 0; ri < needsRegen.size(); ri++) {
                        Scene s = needsRegen.get(ri);
                        final int currRi = ri + 1;
                        consoleLogger.accept("Regenerating scene " + currRi + "/" + needsRegen.size() + ": " + s.getSceneId() + "...");
                        File regen = comfyPipelineService.generateSceneStrict(s).join();
                        if (regen != null && regen.exists() && regen.length() > 1024) {
                            s.setVideoPath(regen.getAbsolutePath());
                        } else {
                            throw new Exception("Could not regenerate video for scene: " + s.getSceneId());
                        }
                    }
                }

                consoleLogger.accept("Stitching video segments via FFmpeg...");
                File exportFile = video4jEditorService.executeMasterRender(scenes);

                SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(false);
                    buttonsDisabledSetter.accept(false);
                    consoleLogger.accept("Stitching Complete! Saved as " + exportFile.getName());
                    JOptionPane.showMessageDialog(parent, "Master render completed successfully!\nSaved to: " + exportFile.getAbsolutePath(), "Master Render Complete", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception ex) {
                logger.error("Master render task failed", ex);
                SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(false);
                    buttonsDisabledSetter.accept(false);
                    consoleLogger.accept("Render Failed: " + ex.getMessage());
                    JOptionPane.showMessageDialog(parent, "Master render failed:\n" + ex.getMessage(), "Render Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    /**
     * Ensures ComfyUI server is running and healthy, starting it if necessary.
     *
     * @throws Exception if ComfyUI fails to reach a healthy state within timeout
     */
    public void ensureComfyUiHealthy() throws Exception {
        if (lifecycleService != null && !lifecycleService.isHealthy()) {
            lifecycleService.start();
            int maxWait = 90;
            boolean healthy = false;
            for (int w = 0; w < maxWait; w++) {
                if (lifecycleService.isHealthy()) {
                    healthy = true;
                    break;
                }
                Thread.sleep(1000);
            }
            if (!healthy) {
                throw new Exception("ComfyUI server could not be started or is not healthy.");
            }
        }
    }
}
