package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.domain.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Specialized engine for video post-production tasks including multi-scene transition
 * concatenation (xfade/acrossfade), media duration probing, and subtitle burning via FFmpeg.
 */
@Component
public class VideoPostProductionEngine {

    private static final Logger logger = LoggerFactory.getLogger(VideoPostProductionEngine.class);
    private static final Pattern DURATION_PATTERN = Pattern.compile("Duration:\\s+(\\d+):(\\d+):([0-9.]+)");

    /**
     * Functional interface for process execution, enabling tracking and mocking.
     */
    @FunctionalInterface
    public interface ProcessLauncher {
        /**
         * Starts the given process.
         *
         * @param pb the process builder to launch
         * @return the started process
         * @throws IOException if process creation fails
         */
        Process start(ProcessBuilder pb) throws IOException;
    }

    /**
     * Checks if any scene in the provided list requests a non-empty visual transition.
     *
     * @param scenes the list of scenes to check
     * @return true if at least one scene specifies a transition, false otherwise
     */
    public boolean anySceneRequestsTransition(List<Scene> scenes) {
        if (scenes == null) return false;
        for (Scene s : scenes) {
            String t = s.getTransitionType();
            if (t != null && !t.isEmpty() && !"none".equalsIgnoreCase(t)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Probes the duration of a media file in seconds using FFmpeg.
     *
     * @param mediaFile  the media file to probe
     * @param ffmpegPath the path to the FFmpeg binary
     * @param launcher   the process launcher
     * @return duration in seconds, or 0.0 on failure
     */
    public double probeDuration(File mediaFile, String ffmpegPath, ProcessLauncher launcher) {
        if (mediaFile == null || !mediaFile.exists()) return 0.0;
        try {
            ProcessBuilder pb = new ProcessBuilder(ffmpegPath, "-i", mediaFile.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process p = launcher.start(pb);
            double duration = 0.0;
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    Matcher m = DURATION_PATTERN.matcher(line);
                    if (m.find()) {
                        double hours = Double.parseDouble(m.group(1));
                        double minutes = Double.parseDouble(m.group(2));
                        double seconds = Double.parseDouble(m.group(3));
                        duration = hours * 3600 + minutes * 60 + seconds;
                        break;
                    }
                }
            }
            p.waitFor();
            if (duration > 0) return duration;
        } catch (Exception e) {
            logger.error("⚠️ [VideoPostProductionEngine] Could not probe duration: {}", e.getMessage());
        }
        return 0.0;
    }

    /**
     * Assembles multiple video segments into a single export file using FFmpeg xfade and acrossfade filters.
     *
     * @param segments   the ordered list of video files
     * @param scenes     the ordered list of scenes corresponding to each segment
     * @param ffmpegPath the path to the FFmpeg executable
     * @param exportFile the destination video file
     * @param launcher   the process launcher
     * @throws Exception if xfade assembly fails
     */
    public void runXfadeConcat(List<File> segments, List<Scene> scenes,
                               String ffmpegPath, File exportFile,
                               ProcessLauncher launcher) throws Exception {
        double[] durations = new double[segments.size()];
        for (int i = 0; i < segments.size(); i++) {
            durations[i] = probeDuration(segments.get(i), ffmpegPath, launcher);
            if (durations[i] <= 0) durations[i] = 5.0;
        }

        StringBuilder filter = new StringBuilder();
        List<String> args = new ArrayList<>();
        args.add(ffmpegPath);
        for (int i = 0; i < segments.size(); i++) {
            args.add("-i");
            args.add(segments.get(i).getAbsolutePath());
        }

        String lastLabel = "[0:v]";
        String lastAudioLabel = "[0:a]";
        double offset = durations[0];
        for (int i = 1; i < segments.size(); i++) {
            Scene s = scenes.get(i - 1);
            String transition = s.getTransitionType();
            if (transition == null || transition.isEmpty() || "none".equalsIgnoreCase(transition) || "crossfade".equalsIgnoreCase(transition)) {
                transition = "fade";
            }
            double transDur = s.getTransitionDuration();
            if (transDur <= 0) transDur = 1.0;
            transDur = Math.min(transDur, Math.min(durations[i - 1], durations[i]) / 2.0);
            String outV = i == segments.size() - 1 ? "[v]" : ("[v" + i + "]");
            String outA = i == segments.size() - 1 ? "[a]" : ("[a" + i + "]");
            filter.append(lastLabel).append("[").append(i).append(":v]xfade=transition=")
                    .append(transition).append(":duration=").append(String.format(Locale.US, "%.3f", transDur))
                    .append(":offset=").append(String.format(Locale.US, "%.3f", Math.max(0.0, offset - transDur)))
                    .append(outV).append(";");
            filter.append(lastAudioLabel).append("[").append(i).append(":a]acrossfade=d=")
                    .append(String.format(Locale.US, "%.3f", transDur)).append(outA).append(";");
            lastLabel = outV;
            lastAudioLabel = outA;
            offset += durations[i] - transDur;
        }
        args.add("-filter_complex");
        args.add(filter.toString());
        args.add("-map");
        args.add(lastLabel);
        args.add("-map");
        args.add(lastAudioLabel);
        args.add("-c:v");
        args.add("libx264");
        args.add("-preset");
        args.add("medium");
        args.add("-crf");
        args.add("18");
        args.add("-pix_fmt");
        args.add("yuv420p");
        args.add("-c:a");
        args.add("aac");
        args.add("-b:a");
        args.add("192k");
        args.add("-ar");
        args.add("44100");
        args.add("-ac");
        args.add("2");
        args.add("-y");
        args.add(exportFile.getAbsolutePath());

        logger.info("[VideoPostProductionEngine] Running xfade: {}", String.join(" ", args));
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectErrorStream(true);
        Process p = launcher.start(pb);
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                logger.info("   [FFmpeg xFade] {}", line);
            }
        }
        int exit = p.waitFor();
        if (exit != 0 || !exportFile.exists() || exportFile.length() < 1024) {
            throw new IOException("ffmpeg xfade failed with exit " + exit);
        }
    }

    /**
     * Overlays subtitles across the master stitched video in a dedicated post-production pass.
     * Uses timestamp-based drawtext filters so subtitles change instantaneously without ghosting.
     *
     * @param masterVideo the assembled master video file
     * @param scenes      the ordered list of scenes
     * @param ffmpegPath  path to FFmpeg executable
     * @param launcher    the process launcher
     */
    public void applyMasterSubtitles(File masterVideo, List<Scene> scenes, String ffmpegPath, ProcessLauncher launcher) {
        if (masterVideo == null || !masterVideo.exists() || scenes == null || scenes.isEmpty()) {
            return;
        }

        boolean hasAnyNarration = scenes.stream().anyMatch(s -> s.getNarrationText() != null && !s.getNarrationText().trim().isEmpty());
        if (!hasAnyNarration) {
            return;
        }

        List<File> tempSubFiles = new ArrayList<>();
        File tempOutput = new File(masterVideo.getParentFile(), "temp_subbed_" + masterVideo.getName());

        try {
            List<String> drawtextFilters = new ArrayList<>();
            double currentOffset = 0.0;
            boolean hasTransitions = scenes.size() > 1 && anySceneRequestsTransition(scenes);

            for (int i = 0; i < scenes.size(); i++) {
                Scene s = scenes.get(i);
                double dur = (s.getEndFrame() > s.getStartFrame()) ? (s.getEndFrame() - s.getStartFrame()) / 30.0 : 5.0;
                if (dur <= 0) dur = 5.0;

                double startSec = currentOffset;
                double endSec = currentOffset + dur;

                if (hasTransitions && i > 0) {
                    double transDur = s.getTransitionDuration() > 0 ? s.getTransitionDuration() : 1.0;
                    transDur = Math.min(transDur, dur / 2.0);
                    startSec = Math.max(0.0, startSec - transDur);
                    endSec = Math.max(startSec + 0.5, endSec - transDur);
                }

                String narration = s.getNarrationText();
                if (narration != null && !narration.trim().isEmpty()) {
                    File subFile = File.createTempFile("sub_master_" + s.getSceneId() + "_", ".txt");
                    Files.writeString(subFile.toPath(), narration.trim(), StandardCharsets.UTF_8);
                    tempSubFiles.add(subFile);

                    String escapedSubPath = subFile.getAbsolutePath()
                            .replace("\\", "/")
                            .replace(":", "\\:")
                            .replace("'", "\\'");

                    String drawtext = String.format(Locale.US,
                            "drawtext=textfile='%s':enable='between(t,%.2f,%.2f)':fontsize=24:fontcolor=white:borderw=2:bordercolor=black:x=(w-tw)/2:y=h-th-40",
                            escapedSubPath, Math.max(0.0, startSec), endSec);
                    drawtextFilters.add(drawtext);
                }

                currentOffset = endSec;
            }

            if (!drawtextFilters.isEmpty()) {
                List<String> cmd = new ArrayList<>();
                cmd.add(ffmpegPath);
                cmd.add("-y");
                cmd.add("-i");
                cmd.add(masterVideo.getAbsolutePath());
                cmd.add("-vf");
                cmd.add(String.join(",", drawtextFilters));
                cmd.add("-c:v");
                cmd.add("libx264");
                cmd.add("-preset");
                cmd.add("medium");
                cmd.add("-crf");
                cmd.add("18");
                cmd.add("-pix_fmt");
                cmd.add("yuv420p");
                cmd.add("-c:a");
                cmd.add("copy");
                cmd.add(tempOutput.getAbsolutePath());

                logger.info("📝 [VideoPostProductionEngine] Applying master post-production subtitles...");
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                Process p = launcher.start(pb);
                try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        logger.info("   [FFmpeg MasterSub] {}", line);
                    }
                }
                int exit = p.waitFor();
                if (exit == 0 && tempOutput.exists() && tempOutput.length() > 1024) {
                    masterVideo.delete();
                    boolean renamed = tempOutput.renameTo(masterVideo);
                    if (!renamed) {
                        Files.copy(tempOutput.toPath(), masterVideo.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        tempOutput.delete();
                    }
                    logger.info("✅ [VideoPostProductionEngine] Master subtitles applied successfully without ghosting.");
                } else {
                    logger.warn("⚠️ [VideoPostProductionEngine] Master subtitle burn exited with {}; retaining clean video.", exit);
                    if (tempOutput.exists()) tempOutput.delete();
                }
            }
        } catch (Exception e) {
            logger.error("⚠️ [VideoPostProductionEngine] Failed applying master subtitles: {}", e.getMessage(), e);
            if (tempOutput.exists()) tempOutput.delete();
        } finally {
            for (File tf : tempSubFiles) {
                if (tf.exists()) tf.delete();
            }
        }
    }
}
