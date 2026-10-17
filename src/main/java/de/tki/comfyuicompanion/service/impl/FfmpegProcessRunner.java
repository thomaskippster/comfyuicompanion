package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.domain.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runner engine executing external FFmpeg commands and processes.
 * <p>
 * Handles executable validation, process management via {@link ProcessTracker},
 * video trimming, audio merging, motion filter generation for static images, and format probing.
 * Command-line construction is delegated to {@link FfmpegCommandBuilder}.
 */
@Component
public class FfmpegProcessRunner {

    private static final Logger logger = LoggerFactory.getLogger(FfmpegProcessRunner.class);
    private static final Pattern FPS_PATTERN = Pattern.compile("([0-9.]+)\\s+fps");
    private static final Pattern DIMENSION_PATTERN = Pattern.compile("([0-9]{3,4})x([0-9]{3,4})");

    private final ConfigService configService;
    private ProcessTracker processTracker;

    /**
     * Constructs an FfmpegProcessRunner with injected configuration and optional process tracker.
     *
     * @param configService  the application configuration service
     * @param processTracker optional process tracker for monitoring spawned processes
     */
    @Autowired
    public FfmpegProcessRunner(
            ConfigService configService,
            @Autowired(required = false) ProcessTracker processTracker) {
        this.configService = configService;
        this.processTracker = processTracker;
    }

    /**
     * Convenience constructor without process tracking.
     *
     * @param configService the application configuration service
     */
    public FfmpegProcessRunner(ConfigService configService) {
        this(configService, null);
    }

    /**
     * Sets or updates the active process tracker.
     *
     * @param processTracker the process tracker to set
     */
    public void setProcessTracker(ProcessTracker processTracker) {
        this.processTracker = processTracker;
    }

    /**
     * Returns the active process tracker.
     *
     * @return the process tracker, or {@code null} if not configured
     */
    public ProcessTracker getProcessTracker() {
        return processTracker;
    }

    /**
     * Starts an external process using the provided process builder and tracker.
     *
     * @param pb              the configured process builder
     * @param overrideTracker optional process tracker override; if null, uses the injected tracker
     * @return the started process
     * @throws IOException if process invocation fails
     */
    public Process startProcess(ProcessBuilder pb, ProcessTracker overrideTracker) throws IOException {
        ProcessTracker tracker = overrideTracker != null ? overrideTracker : this.processTracker;
        return tracker != null ? tracker.start(pb) : pb.start();
    }

    /**
     * Starts an external process using the configured process tracker.
     *
     * @param pb the configured process builder
     * @return the started process
     * @throws IOException if process invocation fails
     */
    public Process startProcess(ProcessBuilder pb) throws IOException {
        return startProcess(pb, this.processTracker);
    }

    /**
     * Validates that the configured FFmpeg executable is present and runnable.
     *
     * @throws FileNotFoundException if the executable is not found or not executable
     */
    public void validateFfmpeg() throws FileNotFoundException {
        String ffmpegPath = configService.getFfmpegPath();
        File ffmpegFile = new File(ffmpegPath);

        boolean isGlobal = "ffmpeg".equals(ffmpegPath);
        boolean available = false;

        if (isGlobal) {
            try {
                Process p = startProcess(new ProcessBuilder("ffmpeg", "-version"), this.processTracker);
                p.destroy();
                available = true;
            } catch (Exception e) {
                available = false;
            }
        } else {
            available = ffmpegFile.exists() && ffmpegFile.isFile() && ffmpegFile.canExecute();
        }

        if (!available) {
            throw new FileNotFoundException("FFmpeg executable was not found or is not executable (Path: " + ffmpegPath + "). " +
                "Please configure FFmpeg in settings or add it to your system PATH.");
        }
    }

    /**
     * Converts a static image file into an MP4 video clip with Ken Burns motion effect.
     *
     * @param imageFile the input image file
     * @param destVideo the output video destination
     * @param duration  duration of the resulting video in seconds
     * @param tracker   optional process tracker override
     * @return {@code true} if conversion was successful, {@code false} otherwise
     */
    public boolean convertImageToVideo(File imageFile, File destVideo, int duration, ProcessTracker tracker) {
        logger.info("🖼️ [FfmpegProcessRunner] Converting static image {} to MP4 video with Ken Burns motion (Duration: {}s)",
                imageFile.getName(), duration);
        try {
            List<String> cmd = FfmpegCommandBuilder.imageToVideo(configService.getFfmpegPath(), imageFile, destVideo, duration, 24);
            int exitCode = runLogged(cmd, tracker, "   [FFmpeg Img2Vid] {}");
            if (exitCode == 0 && destVideo.exists() && destVideo.length() > 1024) {
                logger.info("✅ [FfmpegProcessRunner] Successfully converted image to video: {}", destVideo.getAbsolutePath());
                return true;
            } else {
                logger.error("❌ [FfmpegProcessRunner] FFmpeg Img2Vid failed with exit code: {}", exitCode);
            }
        } catch (Exception e) {
            logger.error("❌ [FfmpegProcessRunner] Failed to run FFmpeg Img2Vid: {}", e.getMessage(), e);
        }
        return false;
    }

    /**
     * Probes the video frame rate (FPS) of a media file via FFmpeg.
     *
     * @param videoPath the path of the video file
     * @param tracker   optional process tracker override
     * @return the probed frame rate, or 30.0 as default fallback
     */
    public double probeFps(String videoPath, ProcessTracker tracker) {
        double fps = 30.0;
        try {
            ProcessBuilder probePb = new ProcessBuilder(FfmpegCommandBuilder.probe(configService.getFfmpegPath(), videoPath));
            probePb.redirectErrorStream(true);
            Process probeProcess = startProcess(probePb, tracker);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(probeProcess.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    Matcher m = FPS_PATTERN.matcher(line);
                    if (m.find()) {
                        fps = Double.parseDouble(m.group(1));
                        break;
                    }
                }
            }
            probeProcess.waitFor();
        } catch (Exception e) {
            logger.info("   -> Could not probe FPS, using default 30.0: {}", e.getMessage());
        }
        return fps;
    }

    /**
     * Probes native video resolution (width, height) using FFmpeg.
     *
     * @param videoFile the video file to inspect
     * @param tracker   optional process tracker override
     * @return an integer array containing [width, height], or {@code null} if probing fails
     */
    public int[] probeVideoDimensions(File videoFile, ProcessTracker tracker) {
        if (videoFile == null || !videoFile.exists()) {
            return null;
        }
        try {
            ProcessBuilder probePb = new ProcessBuilder(
                    FfmpegCommandBuilder.probe(configService.getFfmpegPath(), videoFile.getAbsolutePath()));
            probePb.redirectErrorStream(true);
            Process probeProcess = startProcess(probePb, tracker);
            int w = 0;
            int h = 0;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(probeProcess.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    Matcher m = DIMENSION_PATTERN.matcher(line);
                    if (m.find()) {
                        w = Integer.parseInt(m.group(1));
                        h = Integer.parseInt(m.group(2));
                        break;
                    }
                }
            }
            probeProcess.waitFor();
            if (w > 0 && h > 0) {
                return new int[]{w, h};
            }
        } catch (Exception e) {
            logger.debug("Could not probe video dimensions: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Determines target render dimensions from scenes or source media, aligning to even pixel counts.
     *
     * @param scenes  the scene list
     * @param tracker optional process tracker
     * @return int array [width, height]
     */
    public int[] determineTargetDimensions(List<Scene> scenes, ProcessTracker tracker) {
        int targetWidth = 1920;
        int targetHeight = 1080;
        if (scenes != null) {
            for (Scene s : scenes) {
                if (s.getWidth() > 0 && s.getHeight() > 0) {
                    targetWidth = s.getWidth();
                    targetHeight = s.getHeight();
                    break;
                }
                if (s.getVideoPath() != null && !s.getVideoPath().trim().isEmpty()) {
                    int[] probed = probeVideoDimensions(new File(s.getVideoPath()), tracker);
                    if (probed != null) {
                        targetWidth = probed[0];
                        targetHeight = probed[1];
                        break;
                    }
                }
            }
        }
        return new int[]{(targetWidth / 2) * 2, (targetHeight / 2) * 2};
    }

    /**
     * Prepares the synchronized audio file for a scene, generating dummy silent audio if absent.
     *
     * @param scene   the scene
     * @param tracker optional process tracker
     * @return the audio file
     * @throws Exception if dummy audio generation fails
     */
    public File resolveAudioForScene(Scene scene, ProcessTracker tracker) throws Exception {
        String aPath = scene.getAudioPath();
        if (aPath != null && !aPath.trim().isEmpty() && new File(aPath).exists()) {
            return new File(aPath);
        }
        File dummy = new File(System.getProperty("java.io.tmpdir"), "dummy_" + scene.getSceneId() + ".wav");
        generateDummyWav(scene, dummy, tracker);
        return dummy;
    }

    /**
     * Creates a temporary concat list file for FFmpeg demuxer.
     *
     * @param segments list of segment files
     * @return the concat list file
     * @throws IOException on write failure
     */
    public File createConcatListFile(List<File> segments) throws IOException {
        File concatList = new File(System.getProperty("java.io.tmpdir"), "concat_list.txt");
        try (PrintWriter pw = new PrintWriter(concatList, StandardCharsets.UTF_8)) {
            for (File seg : segments) {
                pw.println("file '" + seg.getAbsolutePath().replace("\\", "/") + "'");
            }
        }
        return concatList;
    }

    /**
     * Executes the FFmpeg trimming process for a scene, adjusting brightness/contrast and
     * converting static images if necessary.
     *
     * @param scene    the scene definition
     * @param tempFile the output temporary video file
     * @param tracker  optional process tracker override
     * @return the trimmed output file
     * @throws Exception if trimming fails
     */
    public File executeTrimScene(Scene scene, File tempFile, ProcessTracker tracker) throws Exception {
        validateFfmpeg();
        String videoPath = scene.getVideoPath();
        File origFile = new File(videoPath);

        File tempImageVideo = null;
        try {
            String lower = videoPath.toLowerCase();
            boolean isImage = lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp");
            if (isImage) {
                tempImageVideo = new File(System.getProperty("java.io.tmpdir"), "temp_image_convert_" + scene.getSceneId() + ".mp4");
                if (tempImageVideo.exists()) {
                    tempImageVideo.delete();
                }
                int duration = 5;
                if (scene.getEndFrame() > scene.getStartFrame()) {
                    duration = (scene.getEndFrame() - scene.getStartFrame()) / 30;
                }
                if (duration <= 0) duration = 3;

                boolean ok = convertImageToVideo(origFile, tempImageVideo, duration, tracker);
                if (ok) {
                    videoPath = tempImageVideo.getAbsolutePath();
                } else {
                    throw new IOException("Failed to convert image to video for Scene " + scene.getSceneId());
                }
            }

            double fps = probeFps(videoPath, tracker);

            boolean relativeToZero = videoPath.contains("scene_" + scene.getSceneId())
                || videoPath.contains("videoarchitect_")
                || videoPath.contains("_simulated");
            int startFrame = relativeToZero ? 0 : scene.getStartFrame();
            int endFrame = relativeToZero ? (scene.getEndFrame() - scene.getStartFrame()) : scene.getEndFrame();

            double startTime = startFrame / fps;
            double duration = (endFrame - startFrame) / fps;
            if (duration <= 0) duration = 3.0;

            logger.info("   -> Trimming: startTime={}s, duration={}s (FPS: {})",
                    String.format(Locale.US, "%.2f", startTime), String.format(Locale.US, "%.2f", duration), fps);

            String ffmpegPath = configService.getFfmpegPath();
            String colorFilter = FfmpegCommandBuilder.buildColorFilter(scene);
            int exitCode = runLogged(
                    FfmpegCommandBuilder.trim(ffmpegPath, videoPath, startTime, duration, colorFilter, tempFile),
                    tracker, "   [FFmpeg Trim] {}");
            if (exitCode != 0 || !tempFile.exists() || tempFile.length() < 1024) {
                if (colorFilter != null) {
                    logger.warn("⚠️ [FfmpegProcessRunner] Trimming with filters failed (exit code {}) for Scene {}. Retrying trimming without video filters as fallback...",
                            exitCode, scene.getSceneId());
                    int fbExit = runLogged(
                            FfmpegCommandBuilder.trim(ffmpegPath, videoPath, startTime, duration, null, tempFile),
                            tracker, "   [FFmpeg Trim Fallback] {}");
                    if (fbExit != 0 || !tempFile.exists() || tempFile.length() < 1024) {
                        throw new IOException("FFmpeg trimming failed with exit code: " + fbExit + " for Scene " + scene.getSceneId());
                    }
                } else {
                    throw new IOException("FFmpeg trimming failed with exit code: " + exitCode + " for Scene " + scene.getSceneId());
                }
            }
        } finally {
            if (tempImageVideo != null && tempImageVideo.exists()) {
                tempImageVideo.delete();
            }
        }
        return tempFile;
    }

    /**
     * Builds the FFmpeg lavfi command that generates dummy fallback audio (silence) for a scene.
     *
     * @param ffmpegPath path to the FFmpeg binary
     * @param duration   audio duration in seconds
     * @param dest       destination audio file
     * @return the command argument list
     */
    public static List<String> buildDummyWavCommand(String ffmpegPath, int duration, File dest) {
        return FfmpegCommandBuilder.dummyWav(ffmpegPath, duration, dest);
    }

    /**
     * Generates a silent WAV audio file to serve as a fallback for a scene without narration.
     *
     * @param scene   the scene definition
     * @param dest    destination WAV file
     * @param tracker optional process tracker override
     * @throws Exception if generation fails
     */
    public void generateDummyWav(Scene scene, File dest, ProcessTracker tracker) throws Exception {
        validateFfmpeg();

        int duration = 5;
        if (scene.getEndFrame() > scene.getStartFrame()) {
            duration = (scene.getEndFrame() - scene.getStartFrame()) / 30;
        }
        if (duration <= 0) duration = 1;

        logger.info("🎵 [FfmpegProcessRunner] Generating silence WAV fallback for Scene {} (Duration: {}s) to: {}",
                scene.getSceneId(), duration, dest.getAbsolutePath());
        int exitCode = runLogged(buildDummyWavCommand(configService.getFfmpegPath(), duration, dest), tracker, null);
        if (exitCode != 0) {
            throw new IOException("FFmpeg silent WAV generator exited with code: " + exitCode);
        }
    }

    /**
     * Merges a trimmed video clip with an audio file into a standardized intermediate segment.
     *
     * @param trimmedVideo  the trimmed input video
     * @param audioFile     the synchronized audio file
     * @param audioDuration duration in seconds
     * @param targetWidth   target video width in pixels
     * @param targetHeight  target video height in pixels
     * @param mergedSegment target output file
     * @param tracker       optional process tracker override
     * @throws IOException          if execution fails or output is invalid
     * @throws InterruptedException if interrupted during execution
     */
    public void mergeVideoAndAudioSegment(File trimmedVideo, File audioFile, double audioDuration,
                                          int targetWidth, int targetHeight, File mergedSegment,
                                          ProcessTracker tracker) throws IOException, InterruptedException {
        List<String> mergeCmd = FfmpegCommandBuilder.mergeSegment(configService.getFfmpegPath(), trimmedVideo,
                audioFile, audioDuration, targetWidth, targetHeight, mergedSegment);

        logger.info("   -> Merge cmd: {}", String.join(" ", mergeCmd));
        int mergeExit = runLogged(mergeCmd, tracker, "   [FFmpeg Merge] {}");
        if (mergeExit != 0 || !mergedSegment.exists() || mergedSegment.length() < 1024) {
            throw new IOException("FFmpeg segment merge failed (exit code: " + mergeExit + ")");
        }
    }

    /**
     * Stitches multiple video segments together using the FFmpeg concat demuxer.
     *
     * @param concatList text file listing all segments for concat demuxer
     * @param exportFile output video file
     * @param tracker    optional process tracker override
     * @throws IOException          if FFmpeg process fails
     * @throws InterruptedException if interrupted during execution
     */
    public void executeConcatDemuxer(File concatList, File exportFile, ProcessTracker tracker) throws IOException, InterruptedException {
        List<String> concatCmd = FfmpegCommandBuilder.concatDemuxer(configService.getFfmpegPath(), concatList, exportFile);

        logger.info("🎬 [FfmpegProcessRunner] Running concat: {}", String.join(" ", concatCmd));
        int exitCode = runLogged(concatCmd, tracker, "[FFmpeg Render] {}");
        if (exitCode != 0 || !exportFile.exists() || exportFile.length() < 1024) {
            throw new IOException("FFmpeg concat exited with error code: " + exitCode);
        }
    }

    /**
     * Runs a command with merged stdout/stderr, drains its output (logging each line when a format is given)
     * and waits for termination.
     *
     * @param command   the command argument list
     * @param tracker   optional process tracker override
     * @param logFormat SLF4J format with one placeholder for each output line, or {@code null} to discard output
     * @return the process exit code
     */
    private int runLogged(List<String> command, ProcessTracker tracker, String logFormat) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = startProcess(pb, tracker);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (logFormat != null) {
                    logger.info(logFormat, line);
                }
            }
        }
        return process.waitFor();
    }
}
