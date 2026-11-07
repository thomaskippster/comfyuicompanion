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
import org.springframework.beans.factory.annotation.Autowired;

import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Coordinates batch rendering of scene video segments in ComfyUI,
 * retry mechanisms, disk resolution of generated clips, and master video stitching via FFmpeg.
 */
@org.springframework.stereotype.Component
public class VideoBatchRenderCoordinator {

    private static final Logger logger = LoggerFactory.getLogger(VideoBatchRenderCoordinator.class);

    private final ConfigService configService;
    private final ComfyPipelineService comfyPipelineService;
    private final Video4jEditorService video4jEditorService;
    private final IComfyLifecycleService lifecycleService;
    private final IVideoFrameChainingService frameChainingService;
    private final IVideoVramOptimizer vramOptimizer;
    private final IVideoVisionFeedbackService visionFeedbackService;
    private final AtomicBoolean cancellationRequested = new AtomicBoolean(false);
    private volatile Thread activeWorkerThread;

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
    @Autowired
    public VideoBatchRenderCoordinator(ConfigService configService,
                                       ComfyPipelineService comfyPipelineService,
                                       Video4jEditorService video4jEditorService,
                                       IComfyLifecycleService lifecycleService,
                                       @Autowired(required = false) IVideoFrameChainingService frameChainingService,
                                       @Autowired(required = false) IVideoVramOptimizer vramOptimizer,
                                       @Autowired(required = false) IVideoVisionFeedbackService visionFeedbackService) {
        this.configService = configService;
        this.comfyPipelineService = comfyPipelineService;
        this.video4jEditorService = video4jEditorService;
        this.lifecycleService = lifecycleService;
        this.frameChainingService = frameChainingService != null ? frameChainingService : new VideoFrameChainingService(configService);
        this.vramOptimizer = vramOptimizer != null ? vramOptimizer : new VideoVramOptimizer(configService, null, null);
        this.visionFeedbackService = visionFeedbackService != null ? visionFeedbackService : new VideoVisionFeedbackService(configService);
    }

    /**
     * Signals cancellation of running video tasks, immediately sending an /interrupt request
     * to ComfyUI, terminating retry attempts, and interrupting background worker threads.
     */
    public void cancelExecution() {
        cancellationRequested.set(true);
        if (comfyPipelineService != null) {
            comfyPipelineService.interruptActiveExecution();
        }
        Thread worker = activeWorkerThread;
        if (worker != null && worker.isAlive()) {
            worker.interrupt();
        }
    }

    /**
     * Checks whether execution cancellation has been requested.
     *
     * @return true if cancellation requested
     */
    public boolean isCancellationRequested() {
        return cancellationRequested.get();
    }

    /**
     * Executes video generation for all scenes currently present in the timeline.
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

        cancellationRequested.set(false);

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

        Thread worker = Thread.ofVirtual().start(() -> {
            try {
                executeVideoGenerationSync(parent, scenes, speakerPath, width, height,
                        timelineListView, progressBar, buttonsDisabledSetter, consoleLogger, previewUpdater);
                SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(false);
                    buttonsDisabledSetter.accept(false);
                    if (cancellationRequested.get()) {
                        consoleLogger.accept("🛑 Video generation aborted by user.");
                    } else {
                        consoleLogger.accept("All " + scenes.size() + " scene videos generated! Click 'Stitch Videos' to combine.");
                    }
                });
            } catch (Exception ex) {
                logger.warn("Video generation terminated: {}", ex.getMessage());
                SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(false);
                    buttonsDisabledSetter.accept(false);
                    if (cancellationRequested.get()) {
                        consoleLogger.accept("🛑 Video generation aborted by user.");
                    } else {
                        consoleLogger.accept("Generation failed: " + ex.getMessage());
                        if (!de.tki.comfyuicompanion.util.PlatformUtils.isUnattended()) {
                            JOptionPane.showMessageDialog(parent, "Video generation failed:\n" + ex.getMessage(), "Generation Error", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                });
            } finally {
                activeWorkerThread = null;
            }
        });
        this.activeWorkerThread = worker;
    }

    /**
     * Executes the video generation pipeline synchronously on the current thread.
     */
    public void executeVideoGenerationSync(Component parent,
                                           List<Scene> scenes,
                                           String speakerPath,
                                           int width,
                                           int height,
                                           JList<Scene> timelineListView,
                                           JProgressBar progressBar,
                                           Consumer<Boolean> buttonsDisabledSetter,
                                           Consumer<String> consoleLogger,
                                           Consumer<Scene> previewUpdater) throws Exception {
        consoleLogger.accept("Checking ComfyUI server status...");
        ensureComfyUiHealthy(consoleLogger);

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
                if (cancellationRequested.get()) {
                    consoleLogger.accept("🛑 Storyboard keyframe phase aborted by user.");
                    return;
                }
                Scene sc = scenes.get(i);
                final int currentIdx = i + 1;
                final int totalScenes = scenes.size();

                if (sc.getSpeakerImagePath() == null || sc.getSpeakerImagePath().trim().isEmpty() || !new File(sc.getSpeakerImagePath()).exists()) {
                    SwingUtilities.invokeLater(() -> progressBar.setValue((int) (((double) currentIdx / (totalScenes * 2)) * 100)));
                    consoleLogger.accept("🎨 [Pass 1] Rendering Keyframe " + currentIdx + "/" + totalScenes + ": " + sc.getSceneId());

                    try {
                        File keyframe = comfyPipelineService.generateKeyframe(sc).join();
                        if (cancellationRequested.get()) {
                            consoleLogger.accept("🛑 Keyframe generation aborted by user.");
                            return;
                        }
                        if (keyframe != null && keyframe.exists()) {
                            if (i > 0 && scenes.get(0).getSpeakerImagePath() != null) {
                                keyframe = harmonizeKeyframe(sc, scenes.get(0), keyframe, outputDir);
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
                        if (cancellationRequested.get()) {
                            consoleLogger.accept("🛑 Keyframe generation aborted by user.");
                            return;
                        }
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
            if (cancellationRequested.get()) {
                consoleLogger.accept("🛑 Video motion phase aborted by user.");
                return;
            }
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
                if (cancellationRequested.get()) {
                    consoleLogger.accept("🛑 Abort requested: stopping scene " + scene.getSceneId() + " immediately (no retries).");
                    return;
                }
                final int att = attempt;
                int progressBase = twoPhase ? 50 : 0;
                int progressScale = twoPhase ? 50 : 100;
                SwingUtilities.invokeLater(() -> progressBar.setValue(progressBase + (int) (((double) currentIdx / totalScenes) * progressScale)));
                consoleLogger.accept("Generating Scene " + currentIdx + "/" + totalScenes + ": " + scene.getSceneId() + (att > 1 ? " (Attempt " + att + ")" : ""));

                try {
                    output = comfyPipelineService.generateSceneStrict(scene).join();
                    if (cancellationRequested.get()) {
                        consoleLogger.accept("🛑 Generation aborted for scene " + scene.getSceneId() + ". Retries suppressed.");
                        return;
                    }
                    if (output != null && output.exists() && output.length() > 1024) {
                        success = true;
                        break;
                    } else {
                        if (cancellationRequested.get()) {
                            consoleLogger.accept("🛑 Generation aborted for scene " + scene.getSceneId() + ". Retries suppressed.");
                            return;
                        }
                        Thread.sleep(2000);
                    }
                } catch (Exception e) {
                    if (cancellationRequested.get()) {
                        consoleLogger.accept("🛑 Generation aborted for scene " + scene.getSceneId() + ". Retries suppressed.");
                        return;
                    }
                    Thread.sleep(2000);
                }
            }

            if (cancellationRequested.get()) {
                return;
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
    }

    /**
     * Re-attaches missing scene video files from output folders or regenerates them,
     * then stitches all timeline videos into a master render file.
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

        cancellationRequested.set(false);
        buttonsDisabledSetter.accept(true);
        progressBar.setValue(50);
        progressBar.setVisible(true);

        Thread worker = Thread.ofVirtual().start(() -> {
            try {
                consoleLogger.accept("Checking scene videos...");

                List<Scene> needsRegen = new ArrayList<>();
                for (Scene scene : scenes) {
                    if (cancellationRequested.get()) {
                        consoleLogger.accept("🛑 Master render aborted by user.");
                        return;
                    }
                    File vf = scene.getVideoPath() == null ? null : new File(scene.getVideoPath());
                    if (vf == null || !vf.exists() || vf.length() < 1024) {
                        File found = findExistingSceneVideo(scene);
                        if (found != null) {
                            scene.setVideoPath(found.getAbsolutePath());
                        } else {
                            needsRegen.add(scene);
                        }
                    }
                }

                if (!needsRegen.isEmpty()) {
                    consoleLogger.accept("Regenerating " + needsRegen.size() + " missing scene(s)...");
                    for (int ri = 0; ri < needsRegen.size(); ri++) {
                        if (cancellationRequested.get()) {
                            consoleLogger.accept("🛑 Master render aborted by user.");
                            return;
                        }
                        Scene s = needsRegen.get(ri);
                        final int currRi = ri + 1;
                        consoleLogger.accept("Regenerating scene " + currRi + "/" + needsRegen.size() + ": " + s.getSceneId() + "...");
                        File regen = comfyPipelineService.generateSceneStrict(s).join();
                        if (cancellationRequested.get()) {
                            consoleLogger.accept("🛑 Master render aborted by user.");
                            return;
                        }
                        if (regen != null && regen.exists() && regen.length() > 1024) {
                            s.setVideoPath(regen.getAbsolutePath());
                        } else {
                            throw new Exception("Could not regenerate video for scene: " + s.getSceneId());
                        }
                    }
                }

                if (cancellationRequested.get()) {
                    consoleLogger.accept("🛑 Master render aborted by user.");
                    return;
                }

                consoleLogger.accept("Stitching video segments via FFmpeg...");
                File exportFile = video4jEditorService.executeMasterRender(scenes);

                SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(false);
                    buttonsDisabledSetter.accept(false);
                    if (cancellationRequested.get()) {
                        consoleLogger.accept("🛑 Master render aborted by user.");
                    } else {
                        consoleLogger.accept("Stitching Complete! Saved as " + exportFile.getName());
                        if (!de.tki.comfyuicompanion.util.PlatformUtils.isUnattended()) {
                            JOptionPane.showMessageDialog(parent, "Master render completed successfully!\nSaved to: " + exportFile.getAbsolutePath(), "Master Render Complete", JOptionPane.INFORMATION_MESSAGE);
                        }
                    }
                });
            } catch (Exception ex) {
                logger.warn("Master render ended: {}", ex.getMessage());
                SwingUtilities.invokeLater(() -> {
                    progressBar.setVisible(false);
                    buttonsDisabledSetter.accept(false);
                    if (cancellationRequested.get()) {
                        consoleLogger.accept("🛑 Master render aborted by user.");
                    } else {
                        consoleLogger.accept("Render Failed: " + ex.getMessage());
                        if (!de.tki.comfyuicompanion.util.PlatformUtils.isUnattended()) {
                            JOptionPane.showMessageDialog(parent, "Master render failed:\n" + ex.getMessage(), "Render Error", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                });
            } finally {
                activeWorkerThread = null;
            }
        });
        this.activeWorkerThread = worker;
    }

    private File findExistingSceneVideo(Scene scene) {
        String baseOut = configService != null ? configService.getResolvedOutputDir() : "output";
        for (String dir : List.of(".", baseOut, baseOut + "/video")) {
            for (String p : List.of("scene_" + scene.getSceneId() + "_final.mp4", "scene_" + scene.getSceneId() + "_simulated.mp4", "videoarchitect_" + scene.getSceneId() + ".mp4", "videoarchitect_" + scene.getSceneId() + "_00001_.mp4")) {
                File candidate = new File(dir, p);
                if (candidate.exists() && candidate.length() >= 1024) return candidate;
            }
        }
        return null;
    }

    private File harmonizeKeyframe(Scene current, Scene first, File keyframe, File outputDir) {
        if (first == null || first.getSpeakerImagePath() == null) return keyframe;
        File refKeyframe = new File(first.getSpeakerImagePath());
        if (!refKeyframe.exists()) return keyframe;
        File harmonized = new File(outputDir, "harmonized_keyframe_" + current.getSceneId() + ".png");
        File matched = video4jEditorService.harmonizeKeyframeColorsLab(keyframe, refKeyframe, harmonized, 0.40);
        return (matched != null && matched.exists()) ? matched : keyframe;
    }

    /**
     * Ensures ComfyUI server is running and healthy, starting it if necessary.
     */
    public void ensureComfyUiHealthy() throws Exception {
        ensureComfyUiHealthy(null);
    }

    /**
     * Ensures ComfyUI server is running and healthy, starting it if necessary and logging progress.
     *
     * @param statusLogger optional logger callback for UI/console updates
     * @throws Exception if ComfyUI fails to reach a healthy state within timeout
     */
    public void ensureComfyUiHealthy(Consumer<String> statusLogger) throws Exception {
        if (lifecycleService == null || lifecycleService.isHealthy()) {
            if (statusLogger != null) statusLogger.accept("✅ ComfyUI server is online and healthy.");
            return;
        }
        if (statusLogger != null) statusLogger.accept("⏳ ComfyUI server is offline. Starting ComfyUI server automatically...");
        lifecycleService.start();
        int maxWait = 90;
        for (int w = 0; w < maxWait; w++) {
            if (lifecycleService.isHealthy()) {
                if (statusLogger != null) statusLogger.accept("✅ ComfyUI server is online and ready!");
                return;
            }
            if (statusLogger != null && (w % 5 == 0 || w == 1)) {
                statusLogger.accept("⏳ Waiting for ComfyUI to boot (" + w + "s/" + maxWait + "s)...");
            }
            Thread.sleep(1000);
        }
        throw new Exception("ComfyUI server could not be started within " + maxWait + " seconds.");
    }
}
