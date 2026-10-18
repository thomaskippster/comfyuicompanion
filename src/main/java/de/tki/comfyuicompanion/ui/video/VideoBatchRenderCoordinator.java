package de.tki.comfyuicompanion.ui.video;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import de.tki.comfyuicompanion.service.IVideoFrameChainingService;
import de.tki.comfyuicompanion.service.IVideoVisionFeedbackService;
import de.tki.comfyuicompanion.service.IVideoVramOptimizer;
import de.tki.comfyuicompanion.service.impl.ComfyPipelineService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.Video4jEditorService;
import de.tki.comfyuicompanion.service.impl.VideoFrameChainingService;
import de.tki.comfyuicompanion.service.impl.VideoVisionFeedbackService;
import de.tki.comfyuicompanion.service.pipeline.VideoVramOptimizer;
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
    private final IVideoFrameChainingService frameChainingService;
    private final IVideoVramOptimizer vramOptimizer;
    private final IVideoVisionFeedbackService visionFeedbackService;

    /**
     * Constructs a new VideoBatchRenderCoordinator with default supplementary services.
     */
    public VideoBatchRenderCoordinator(ConfigService configService,
                                       ComfyPipelineService comfyPipelineService,
                                       Video4jEditorService video4jEditorService,
                                       IComfyLifecycleService lifecycleService) {
        this(configService, comfyPipelineService, video4jEditorService, lifecycleService,
                new VideoFrameChainingService(configService),
                new VideoVramOptimizer(configService, null, null),
                new VideoVisionFeedbackService(configService));
    }

    /**
     * Constructs a new VideoBatchRenderCoordinator with explicit supplementary services.
     */
    public VideoBatchRenderCoordinator(ConfigService configService,
                                       ComfyPipelineService comfyPipelineService,
                                       Video4jEditorService video4jEditorService,
                                       IComfyLifecycleService lifecycleService,
                                       IVideoFrameChainingService frameChainingService,
                                       IVideoVramOptimizer vramOptimizer,
                                       IVideoVisionFeedbackService visionFeedbackService) {
        this.configService = configService;
        this.comfyPipelineService = comfyPipelineService;
        this.video4jEditorService = video4jEditorService;
        this.lifecycleService = lifecycleService;
        this.frameChainingService = frameChainingService != null ? frameChainingService : new VideoFrameChainingService(configService);
        this.vramOptimizer = vramOptimizer != null ? vramOptimizer : new VideoVramOptimizer(configService, null, null);
        this.visionFeedbackService = visionFeedbackService != null ? visionFeedbackService : new VideoVisionFeedbackService(configService);
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
            if (speakerPath != null && !speakerPath.trim().isEmpty() && (sc.getSpeakerImagePath() == null || sc.getSpeakerImagePath().trim().isEmpty())) {
                sc.setSpeakerImagePath(speakerPath.trim());
            }
        }

        buttonsDisabledSetter.accept(true);
        progressBar.setValue(0);
        progressBar.setVisible(true);

        Thread.ofVirtual().start(() -> {
            try {
                consoleLogger.accept("Checking ComfyUI server status...");
                ensureComfyUiHealthy();

                String serverUrl = configService != null ? configService.getComfyUIUrl() : "http://127.0.0.1:8188";
                boolean twoPhase = configService != null && configService.isTwoPhaseRenderingEnabled();
                boolean frameChaining = configService != null && configService.isLastFrameChainingEnabled();
                File outputDir = new File(configService != null && configService.getResolvedOutputDir() != null && !configService.getResolvedOutputDir().isBlank()
                        ? configService.getResolvedOutputDir() : "output");

                // Enforce shared base seed across storyboard scenes for visual continuity
                long sharedBaseSeed = Math.abs(new java.security.SecureRandom().nextLong());
                for (Scene sc : scenes) {
                    if (sc.getSeed() == null || sc.getSeed() <= 0) {
                        sc.setSeed(sharedBaseSeed);
                    }
                }

                // ==========================================
                // PASS 1: Storyboard / Keyframe Phase (if enabled)
                // ==========================================
                if (twoPhase) {
                    consoleLogger.accept("🎨 [Pass 1/2] Storyboard Phase: Generating keyframes for timeline scenes...");
                    for (int i = 0; i < scenes.size(); i++) {
                        Scene sc = scenes.get(i);
                        final int currentIdx = i + 1;
                        final int totalScenes = scenes.size();

                        if (sc.getSpeakerImagePath() == null || sc.getSpeakerImagePath().trim().isEmpty() || !new File(sc.getSpeakerImagePath()).exists()) {
                            SwingUtilities.invokeLater(() -> progressBar.setValue((int) (((double) currentIdx / (totalScenes * 2)) * 100)));
                            consoleLogger.accept("🎨 [Pass 1] Rendering Keyframe " + currentIdx + "/" + totalScenes + ": " + sc.getSceneId());

                            try {
                                File keyframe = comfyPipelineService.generateKeyframe(sc).join();
                                if (keyframe != null && keyframe.exists()) {
                                    // Gentle LAB color transfer against Scene 1 palette
                                    if (i > 0 && scenes.get(0).getSpeakerImagePath() != null) {
                                        File refKeyframe = new File(scenes.get(0).getSpeakerImagePath());
                                        if (refKeyframe.exists()) {
                                            File harmonized = new File(outputDir, "harmonized_keyframe_" + sc.getSceneId() + ".png");
                                            File matched = video4jEditorService.harmonizeKeyframeColorsLab(keyframe, refKeyframe, harmonized, 0.40);
                                            if (matched != null && matched.exists()) {
                                                keyframe = matched;
                                            }
                                        }
                                    }
                                    sc.setSpeakerImagePath(keyframe.getAbsolutePath());
                                    String refinedPrompt = visionFeedbackService.refinePromptWithVisualFeedback(sc, keyframe);
                                    if (refinedPrompt != null && !refinedPrompt.isBlank()) {
                                        sc.setPrompt(refinedPrompt);
                                    }
                                    SwingUtilities.invokeLater(() -> {
                                        timelineListView.repaint();
                                        previewUpdater.accept(sc);
                                    });
                                }
                            } catch (Exception kfEx) {
                                logger.warn("⚠️ [Pass 1] Keyframe generation for scene {} skipped: {}", sc.getSceneId(), kfEx.getMessage());
                            }
                        }
                    }

                    consoleLogger.accept("🧹 [Pass 1 Complete] Purging ComfyUI VRAM prior to video generation...");
                    if (vramOptimizer != null) {
                        vramOptimizer.purgeComfyVram(serverUrl);
                    }
                }

                // ==========================================
                // PASS 2: Video Diffusion / Motion Phase
                // ==========================================
                consoleLogger.accept(twoPhase ? "🎬 [Pass 2/2] Motion Phase: Rendering video clips..." : "🎬 Rendering video clips...");
                for (int i = 0; i < scenes.size(); i++) {
                    Scene scene = scenes.get(i);
                    final int currentIdx = i + 1;
                    final int totalScenes = scenes.size();

                    if (frameChaining && i > 0 && frameChainingService != null) {
                        String chained = frameChainingService.resolveChainedStartImage(scenes, i, speakerPath, outputDir);
                        if (chained != null && !chained.isBlank() && new File(chained).exists()) {
                            scene.setSpeakerImagePath(chained);
                            consoleLogger.accept("🔗 Chained Scene " + currentIdx + " to last frame of Scene " + i);
                        }
                    }

                    File output = null;
                    int maxRetries = 3;
                    boolean success = false;

                    for (int attempt = 1; attempt <= maxRetries; attempt++) {
                        final int att = attempt;
                        int progressBase = twoPhase ? 50 : 0;
                        int progressScale = twoPhase ? 50 : 100;
                        SwingUtilities.invokeLater(() -> progressBar.setValue(progressBase + (int) (((double) currentIdx / totalScenes) * progressScale)));
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
