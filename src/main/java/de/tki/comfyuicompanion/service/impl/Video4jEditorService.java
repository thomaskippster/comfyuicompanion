package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.domain.Scene;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * High-level orchestration service for non-linear video editing, scene trimming, and master export.
 * <p>
 * Coordinates visual enhancements via {@link OpenCvImageFilterEngine}, command execution via
 * {@link FfmpegProcessRunner}, and multi-scene transitions/subtitles via {@link VideoPostProductionEngine}.
 */
@Service
public class Video4jEditorService {

    private static final Logger logger = LoggerFactory.getLogger(Video4jEditorService.class);

    private final ConfigService configService;
    private ProcessTracker processTracker;
    private final VideoPostProductionEngine postProductionEngine;
    private final OpenCvImageFilterEngine imageFilterEngine;
    private final FfmpegProcessRunner ffmpegProcessRunner;

    /**
     * Primary constructor injecting all collaborating services and engines.
     *
     * @param configService        configuration service for output directories and tool paths
     * @param processTracker       optional tracker for external processes
     * @param postProductionEngine specialized engine for transitions and subtitles
     * @param imageFilterEngine    specialized engine for OpenCV image and frame operations
     * @param ffmpegProcessRunner  specialized runner for FFmpeg command execution
     */
    @Autowired
    public Video4jEditorService(
            ConfigService configService,
            @Autowired(required = false) ProcessTracker processTracker,
            @Autowired(required = false) VideoPostProductionEngine postProductionEngine,
            @Autowired(required = false) OpenCvImageFilterEngine imageFilterEngine,
            @Autowired(required = false) FfmpegProcessRunner ffmpegProcessRunner) {
        this.configService = configService;
        this.processTracker = processTracker;
        this.postProductionEngine = postProductionEngine != null ? postProductionEngine : new VideoPostProductionEngine();
        this.imageFilterEngine = imageFilterEngine != null ? imageFilterEngine : new OpenCvImageFilterEngine();
        this.ffmpegProcessRunner = ffmpegProcessRunner != null ? ffmpegProcessRunner : new FfmpegProcessRunner(configService, processTracker);
    }

    /** Convenience constructor with configuration, tracker, and post-production engine. */
    public Video4jEditorService(ConfigService cs, ProcessTracker pt, VideoPostProductionEngine pe) {
        this(cs, pt, pe, null, null);
    }

    /** Convenience constructor with configuration and tracker. */
    public Video4jEditorService(ConfigService cs, ProcessTracker pt) {
        this(cs, pt, null, null, null);
    }

    /** Convenience constructor with configuration only. */
    public Video4jEditorService(ConfigService cs) {
        this(cs, null, null, null, null);
    }

    /**
     * Initializes OpenCV runtime bindings.
     */
    @PostConstruct
    public void init() {
        imageFilterEngine.init();
    }

    private Process startProcess(ProcessBuilder pb) throws IOException {
        return ffmpegProcessRunner.startProcess(pb, this.processTracker);
    }

    private void validateFfmpeg() throws FileNotFoundException {
        ffmpegProcessRunner.validateFfmpeg();
    }

    /**
     * Resolves an accessible on-disk video file for the given scene.
     */
    private File resolveSceneVideo(Scene scene) {
        List<String> candidates = new ArrayList<>();
        if (scene.getVideoPath() != null && !scene.getVideoPath().isEmpty()) candidates.add(scene.getVideoPath());
        String id = scene.getSceneId();
        String[] roots = {System.getProperty("user.dir"), new File("").getAbsolutePath(), configService.getResolvedOutputDir()};
        String[] patterns = {"scene_" + id + "_final.mp4", "scene_" + id + "_simulated.mp4", "videoarchitect_" + id + "_00001_.mp4", "videoarchitect_" + id + ".mp4", id + ".mp4"};
        for (String root : roots) {
            if (root == null || root.isEmpty()) continue;
            for (String p : patterns) {
                candidates.add(new File(root, p).getAbsolutePath());
                candidates.add(new File(new File(root, "video"), p).getAbsolutePath());
                candidates.add(new File(new File(root, "output"), p).getAbsolutePath());
            }
        }
        for (String c : candidates) {
            if (c != null && !c.isEmpty()) {
                File f = new File(c);
                if (f.exists() && f.length() >= 1024) {
                    logger.info("   -> Resolved scene {} video to: {}", id, f.getAbsolutePath());
                    return f;
                }
            }
        }
        return null;
    }

    /**
     * Trims and processes an individual scene video based on configured frame boundaries.
     *
     * @param scene the scene to trim
     * @return the resulting trimmed temporary video file
     * @throws Exception if trimming or file resolution fails
     */
    public File applyTrimming(Scene scene) throws Exception {
        validateFfmpeg();
        String videoPath = scene.getVideoPath();
        logger.info("🔍 [Video4jEditorService] Trimming/Processing Scene {}", scene.getSceneId());
        File tempFile = new File(System.getProperty("java.io.tmpdir"), "temp_scene_" + scene.getSceneId() + ".mp4");
        if (tempFile.exists()) tempFile.delete();

        if (videoPath == null || videoPath.trim().isEmpty() || !new File(videoPath).exists() || new File(videoPath).length() < 1024) {
            File resolved = resolveSceneVideo(scene);
            if (resolved != null) {
                videoPath = resolved.getAbsolutePath();
                scene.setVideoPath(videoPath);
                logger.info("   -> Resolved/Recovered videoPath: {}", videoPath);
            } else {
                throw new FileNotFoundException("Video file missing or too small for Scene " + scene.getSceneId());
            }
        }

        File trimmed = ffmpegProcessRunner.executeTrimScene(scene, tempFile, this.processTracker);
        logger.info("🎬 [Video4jEditorService] Trimmed scene {} successfully to {}", scene.getSceneId(), trimmed.getAbsolutePath());
        return trimmed;
    }

    /**
     * Extracts a frame and applies brightness and contrast enhancement for Swing/AWT display.
     *
     * @param scene the scene containing media references
     * @param alpha contrast multiplier
     * @param beta  brightness offset
     * @return the enhanced BufferedImage, or {@code null} on failure
     */
    public BufferedImage applyBasicEnhancementAwt(Scene scene, double alpha, double beta) {
        return imageFilterEngine.applyBasicEnhancementAwt(scene, alpha, beta);
    }

    /**
     * Extracts an unadjusted default preview frame for JavaFX display.
     *
     * @param scene the scene containing media references
     * @return the preview JavaFX Image, or {@code null} on failure
     * @throws Exception if extraction encounters an error
     */
    public javafx.scene.image.Image applyBasicEnhancement(Scene scene) throws Exception {
        return applyBasicEnhancement(scene, 1.0, 0.0);
    }

    /**
     * Extracts and enhances a preview frame for JavaFX display.
     *
     * @param scene the scene containing media references
     * @param alpha contrast multiplier
     * @param beta  brightness offset
     * @return the enhanced JavaFX Image, or {@code null} on failure
     * @throws Exception if extraction encounters an error
     */
    public javafx.scene.image.Image applyBasicEnhancement(Scene scene, double alpha, double beta) throws Exception {
        return imageFilterEngine.applyBasicEnhancementFx(scene, alpha, beta);
    }

    /**
     * Executes the master rendering pipeline to stitch scenes, audio, transitions, and subtitles into a final video.
     *
     * @param scenes list of scenes to assemble
     * @return the rendered master video file
     * @throws Exception if the rendering pipeline fails
     */
    public File executeMasterRender(List<Scene> scenes) throws Exception {
        validateFfmpeg();
        List<File> mergedSegments = new ArrayList<>();
        String ffmpegPath = configService.getFfmpegPath();
        int[] dims = ffmpegProcessRunner.determineTargetDimensions(scenes, this.processTracker);

        for (int i = 0; i < scenes.size(); i++) {
            Scene scene = scenes.get(i);
            logger.info("🎬 [Video4jEditorService] Processing segment {}/{}: {}", (i + 1), scenes.size(), scene.getSceneId());
            File trimmedVideo = applyTrimming(scene);
            File audioFile = ffmpegProcessRunner.resolveAudioForScene(scene, this.processTracker);
            double audioDuration = postProductionEngine.probeDuration(audioFile, ffmpegPath, this::startProcess);
            if (audioDuration <= 0) audioDuration = 5.0;

            File mergedSegment = new File(System.getProperty("java.io.tmpdir"), "merged_" + scene.getSceneId() + ".mp4");
            if (mergedSegment.exists()) mergedSegment.delete();
            ffmpegProcessRunner.mergeVideoAndAudioSegment(trimmedVideo, audioFile, audioDuration,
                    dims[0], dims[1], mergedSegment, this.processTracker);
            if (trimmedVideo.exists()) trimmedVideo.delete();
            mergedSegments.add(mergedSegment);
        }

        File concatList = ffmpegProcessRunner.createConcatListFile(mergedSegments);
        File exportFile = new File(new File("output"), "master_export.mp4").getAbsoluteFile();
        if (exportFile.getParentFile() != null) exportFile.getParentFile().mkdirs();

        try {
            ffmpegProcessRunner.executeConcatDemuxer(concatList, exportFile, this.processTracker);
            if (mergedSegments.size() > 1 && postProductionEngine.anySceneRequestsTransition(scenes)) {
                try {
                    postProductionEngine.runXfadeConcat(mergedSegments, scenes, ffmpegPath, exportFile, this::startProcess);
                } catch (Exception xfadeEx) {
                    logger.error("[Video4jEditorService] xfade concat failed: {}", xfadeEx.getMessage());
                }
            }
            postProductionEngine.applyMasterSubtitles(exportFile, scenes, ffmpegPath, this::startProcess);
        } catch (Exception e) {
            logger.error("⚠️ [Video4jEditorService] FFmpeg stitching failed: {}", e.getMessage());
            throw new Exception("FFmpeg stitching process failed. " + e.getMessage(), e);
        } finally {
            mergedSegments.forEach(File::delete);
            concatList.delete();
        }

        logger.info("✅ [Video4jEditorService] Master render complete: {}", exportFile.getAbsolutePath());
        openSystemFolder(exportFile);
        return exportFile;
    }

    /**
     * Builds the dummy fallback audio command using a silent audio source.
     *
     * @param ffmpegPath path to FFmpeg binary
     * @param duration   audio duration in seconds
     * @param dest       destination file
     * @return command line arguments
     */
    static List<String> buildDummyWavCommand(String ffmpegPath, int duration, File dest) {
        return FfmpegProcessRunner.buildDummyWavCommand(ffmpegPath, duration, dest);
    }

    /**
     * Harmonizes keyframe color palettes using Reinhard LAB color transfer relative to a reference image.
     *
     * @param sourceImageFile    image to harmonize
     * @param referenceImageFile palette reference image (e.g. Scene 1)
     * @param outputFile         output destination file
     * @param blendFactor        blending ratio (0.35 - 0.50)
     * @return harmonized image file
     */
    public File harmonizeKeyframeColorsLab(File sourceImageFile, File referenceImageFile, File outputFile, double blendFactor) {
        return imageFilterEngine.harmonizeKeyframeColorsLab(sourceImageFile, referenceImageFile, outputFile, blendFactor);
    }

    private void openSystemFolder(File targetFile) {
        if (System.getProperty("surefire.real.class.path") != null || System.getProperty("java.class.path").contains("junit") || java.awt.GraphicsEnvironment.isHeadless()) {
            return;
        }
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                startProcess(new ProcessBuilder("explorer.exe", "/select," + targetFile.getAbsolutePath()));
            } else if (os.contains("mac")) {
                startProcess(new ProcessBuilder("open", "-R", targetFile.getAbsolutePath()));
            } else {
                startProcess(new ProcessBuilder("xdg-open", targetFile.getParentFile().getAbsolutePath()));
            }
        } catch (Exception e) {
            logger.error("⚠️ Could not open system folder: {}", e.getMessage());
        }
    }
}
