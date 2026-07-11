package de.tki.comfymodels.service.impl;

import de.tki.comfymodels.domain.Scene;
import io.metaloom.video4j.Video4j;
import io.metaloom.video4j.VideoFile;
import io.metaloom.video4j.VideoFrame;
import io.metaloom.video4j.Videos;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.videoio.VideoWriter;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class Video4jEditorService {

    private final ConfigService configService;


    @Autowired(required = false)
    private ProcessTracker processTracker;
    public Video4jEditorService(ConfigService configService) {
        this.configService = configService;
    }

    @PostConstruct
    public void init() {
        de.tki.comfymodels.util.OpenCvLoader.load();
    }

    private void validateFfmpeg() throws java.io.FileNotFoundException {
        String ffmpegPath = configService.getFfmpegPath();
        File ffmpegFile = new File(ffmpegPath);
        
        boolean isGlobal = "ffmpeg".equals(ffmpegPath);
        boolean available = false;
        
        if (isGlobal) {
            try {
                Process p = processTracker.start(new ProcessBuilder("ffmpeg", "-version"));
                p.destroy();
                available = true;
            } catch (Exception e) {
                available = false;
            }
        } else {
            available = ffmpegFile.exists() && ffmpegFile.isFile() && ffmpegFile.canExecute();
        }
        
        if (!available) {
            throw new java.io.FileNotFoundException("FFmpeg executable was not found or is not executable (Path: " + ffmpegPath + "). " +
                "Please configure FFmpeg in settings or add it to your system PATH.");
        }
    }



    private boolean convertImageToVideo(File imageFile, File destVideo, int duration) {
        System.out.println("ðŸ–¼ï¸ [Video4jEditorService] Converting static image " + imageFile.getName() + " to MP4 video (Duration: " + duration + "s)");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                configService.getFfmpegPath(), "-y",
                "-loop", "1",
                "-i", imageFile.getAbsolutePath(),
                "-c:v", "libx264",
                "-t", String.valueOf(duration),
                "-pix_fmt", "yuv420p",
                "-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2",
                destVideo.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process process = processTracker.start(pb);
            
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    System.out.println("   [FFmpeg Img2Vid] " + line);
                }
            }
            int exitCode = process.waitFor();
            if (exitCode == 0 && destVideo.exists() && destVideo.length() > 1024) {
                System.out.println("âœ… [Video4jEditorService] Successfully converted image to video: " + destVideo.getAbsolutePath());
                return true;
            } else {
                System.err.println("âŒ [Video4jEditorService] FFmpeg Img2Vid failed with exit code: " + exitCode);
            }
        } catch (Exception e) {
            System.err.println("âŒ [Video4jEditorService] Failed to run FFmpeg Img2Vid: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Try to find a usable on-disk video for a scene. Looks at the scene
     * configured videoPath first, then at the workspace root,
     * the configured ComfyUI output dir, and its "video" subfolder.
     * Returns the first file found, or null.
     */
    private File resolveSceneVideo(Scene scene) {
        java.util.List<String> candidates = new java.util.ArrayList<>();
        // 1. The scene configured videoPath (may be stale).
        if (scene.getVideoPath() != null && !scene.getVideoPath().isEmpty()) {
            candidates.add(scene.getVideoPath());
        }
        String sceneId = scene.getSceneId();
        // 2. Common well-known locations.
        String[] workspaceRoots = {
                System.getProperty("user.dir"),
                new java.io.File("").getAbsolutePath(),
                configService.getResolvedOutputDir()
        };
        String[] filenamePatterns = {
                "scene_" + sceneId + "_final.mp4",
                "scene_" + sceneId + "_simulated.mp4",
                "videoarchitect_" + sceneId + "_00001_.mp4",
                "videoarchitect_" + sceneId + ".mp4",
                sceneId + ".mp4"
        };
        for (String root : workspaceRoots) {
            if (root == null || root.isEmpty()) continue;
            for (String name : filenamePatterns) {
                candidates.add(new java.io.File(root, name).getAbsolutePath());
                candidates.add(new java.io.File(new java.io.File(root, "video"), name).getAbsolutePath());
                candidates.add(new java.io.File(new java.io.File(root, "output"), name).getAbsolutePath());
            }
        }
        for (String pathStr : candidates) {
            if (pathStr == null || pathStr.isEmpty()) continue;
            java.io.File f = new java.io.File(pathStr);
            if (f.exists() && f.length() >= 1024) {
                System.out.println("   -> Resolved scene " + sceneId + " video to: " + f.getAbsolutePath());
                return f;
            }
        }
        return null;
    }

    public File applyTrimming(Scene scene) throws Exception {
        validateFfmpeg();
        String videoPath = scene.getVideoPath();
        System.out.println("ðŸ” [Video4jEditorService] Trimming/Processing Scene " + scene.getSceneId());
        System.out.println("   -> Configured Video Path: " + videoPath);

        File tempFile = new File(System.getProperty("java.io.tmpdir"), "temp_scene_" + scene.getSceneId() + ".mp4");
        if (tempFile.exists()) {
            tempFile.delete();
        }

        if (videoPath == null || videoPath.trim().isEmpty()) {
            // No path set: try to find a candidate on disk.
            File resolved = resolveSceneVideo(scene);
            if (resolved != null) {
                videoPath = resolved.getAbsolutePath();
                scene.setVideoPath(videoPath);
            } else {
                throw new java.io.FileNotFoundException("Video path is empty and no candidate file found for Scene " + scene.getSceneId());
            }
        }

        File origFile = new File(videoPath);
        boolean exists = origFile.exists();
        long length = exists ? origFile.length() : 0;
        System.out.println("   -> File Exists: " + exists + ", Size: " + length + " bytes");

        if (!exists || length < 1024) {
            // Configured path is stale: look for the video elsewhere.
            File resolved = resolveSceneVideo(scene);
            if (resolved != null) {
                videoPath = resolved.getAbsolutePath();
                scene.setVideoPath(videoPath);
                origFile = resolved;
                exists = true;
                length = resolved.length();
                System.out.println("   -> Recovered stale videoPath; new path: " + videoPath);
            } else {
                throw new java.io.FileNotFoundException("Original video file does not exist or is too small: " + videoPath);
            }
        }

        // Handle static image input: convert to video first
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

                boolean ok = convertImageToVideo(origFile, tempImageVideo, duration);
                if (ok) {
                    videoPath = tempImageVideo.getAbsolutePath();
                } else {
                    throw new java.io.IOException("Failed to convert image to video for Scene " + scene.getSceneId());
                }
            }

            // Use FFmpeg for trimming - much more reliable than Video4j AVI/MJPG
            double fps = 30.0; // Default FPS
            // Probe actual FPS from the video file using ffmpeg -i
            try {
                String ffmpegPathStr = configService.getFfmpegPath();
                ProcessBuilder probePb = new ProcessBuilder(ffmpegPathStr, "-i", videoPath);
                probePb.redirectErrorStream(true);
                Process probeProcess = processTracker.start(probePb);
                try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(probeProcess.getInputStream()))) {
                    String line;
                    java.util.regex.Pattern fpsPattern = java.util.regex.Pattern.compile("([0-9.]+)\\s+fps");
                    while ((line = r.readLine()) != null) {
                        java.util.regex.Matcher m = fpsPattern.matcher(line);
                        if (m.find()) {
                            fps = Double.parseDouble(m.group(1));
                            break;
                        }
                    }
                }
                probeProcess.waitFor();
            } catch (Exception e) {
                System.out.println("   -> Could not probe FPS, using default 30.0: " + e.getMessage());
            }

            // Calculate time range from frame numbers
            // For scene-specific clips (generated per-scene), trim from start of file
            boolean relativeToZero = videoPath.contains("scene_" + scene.getSceneId())
                || videoPath.contains("videoarchitect_")
                || videoPath.contains("_simulated");
            int startFrame = relativeToZero ? 0 : scene.getStartFrame();
            int endFrame = relativeToZero ? (scene.getEndFrame() - scene.getStartFrame()) : scene.getEndFrame();

            double startTime = startFrame / fps;
            double duration = (endFrame - startFrame) / fps;
            if (duration <= 0) duration = 3.0;

            System.out.println("   -> Trimming: startTime=" + String.format(java.util.Locale.US, "%.2f", startTime) + "s, duration=" + String.format(java.util.Locale.US, "%.2f", duration) + "s (FPS: " + fps + ")");

            // Build FFmpeg trim command with subtitle burning via drawtext filter
            List<String> command = new java.util.ArrayList<>();
            command.add(configService.getFfmpegPath());
            command.add("-y");
            command.add("-ss");
            command.add(String.format(java.util.Locale.US, "%.3f", startTime));
            command.add("-i");
            command.add(videoPath);
            command.add("-t");
            command.add(String.format(java.util.Locale.US, "%.3f", duration));

            // Add subtitle overlay via drawtext if narration text exists
            String narration = scene.getNarrationText();
            if (narration != null && !narration.trim().isEmpty()) {
                // Escape special characters for FFmpeg drawtext filter
                String escapedText = narration
                    .replace("\\", "\\\\")
                    .replace("'", "'\\\\\''")
                    .replace(":", "\\\\:")
                    .replace("%", "%%");
                String drawtext = "drawtext=text='" + escapedText + "':fontsize=24:fontcolor=white:borderw=2:bordercolor=black:x=(w-tw)/2:y=h-th-40";
                command.add("-vf");
                command.add(drawtext);
                command.add("-c:v");
                command.add("libx264");
                command.add("-preset");
                command.add("fast");
                command.add("-crf");
                command.add("23");
            } else {
                command.add("-c:v");
                command.add("libx264");
                command.add("-preset");
                command.add("fast");
                command.add("-crf");
                command.add("23");
            }

            command.add("-pix_fmt");
            command.add("yuv420p");
            command.add("-an"); // Strip audio - will be added in concat
            command.add(tempFile.getAbsolutePath());

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = processTracker.start(pb);

            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    System.out.println("   [FFmpeg Trim] " + line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0 || !tempFile.exists() || tempFile.length() < 1024) {
                throw new IOException("FFmpeg trimming failed with exit code: " + exitCode + " for Scene " + scene.getSceneId());
            }

        } finally {
            if (tempImageVideo != null && tempImageVideo.exists()) {
                tempImageVideo.delete();
            }
        }

        System.out.println("ðŸŽ¬ [Video4jEditorService] Trimmed scene " + scene.getSceneId() + " successfully. Temp File: " + tempFile.getAbsolutePath());
        return tempFile;
    }

    public javafx.scene.image.Image applyBasicEnhancement(Scene scene) throws Exception {
        return applyBasicEnhancement(scene, 1.0, 0.0);
    }

    public javafx.scene.image.Image applyBasicEnhancement(Scene scene, double alpha, double beta) throws Exception {
        String videoPath = scene.getVideoPath();
        if (videoPath == null || videoPath.trim().isEmpty() || !new File(videoPath).exists()) {
            return null;
        }

        File origFile = new File(videoPath);
        if (origFile.length() < 1024) {
            return null;
        }

        try (VideoFile video = Videos.open(videoPath)) {
            VideoFrame selectedFrame = null;
            java.util.List<VideoFrame> frames = video.streamFrames()
                    .filter(f -> f.number() >= scene.getStartFrame())
                    .limit(1)
                    .collect(java.util.stream.Collectors.toList());
            if (!frames.isEmpty()) {
                selectedFrame = frames.get(0);
            }

            if (selectedFrame == null) {
                return null;
            }

            Mat mat = selectedFrame.mat();
            if (mat != null && !mat.empty()) {
                Mat dest = new Mat();
                org.opencv.core.Core.convertScaleAbs(mat, dest, alpha, beta);

                org.opencv.core.MatOfByte buffer = new org.opencv.core.MatOfByte();
                org.opencv.imgcodecs.Imgcodecs.imencode(".png", dest, buffer);
                byte[] bytes = buffer.toArray();
                return new javafx.scene.image.Image(new java.io.ByteArrayInputStream(bytes));
            }
        } catch (Throwable t) {
            System.err.println("âš ï¸ [Video4jEditorService] Failed to load/enhance video preview: " + t.getMessage());
        }
        return null;
    }

    public File executeMasterRender(List<Scene> scenes) throws Exception {
        validateFfmpeg();
        List<File> mergedSegments = new java.util.ArrayList<>();

        String ffmpegPath = configService.getFfmpegPath();

        // 1. For each scene: trim video, generate/use audio, merge into a single segment with audio
        for (int i = 0; i < scenes.size(); i++) {
            Scene scene = scenes.get(i);
            System.out.println("ðŸŽ¬ [Video4jEditorService] Processing segment " + (i + 1) + "/" + scenes.size() + ": " + scene.getSceneId());

            File trimmedVideo = applyTrimming(scene);

            // Resolve audio file
            File audioFile;
            String aPath = scene.getAudioPath();
            if (aPath != null && !aPath.trim().isEmpty() && new File(aPath).exists()) {
                audioFile = new File(aPath);
            } else {
                audioFile = new File(System.getProperty("java.io.tmpdir"), "dummy_" + scene.getSceneId() + ".wav");
                generateDummyWav(scene, audioFile);
            }

            // Probe audio duration to match video length
            double audioDuration = probeDuration(audioFile);
            if (audioDuration <= 0) {
                audioDuration = 5.0; // default fallback
            }

            // 2. Merge video + audio into a single segment, forcing matching duration
            // Scale to consistent 720x720, 30fps, yuv420p for concat compatibility
            File mergedSegment = new File(System.getProperty("java.io.tmpdir"), "merged_" + scene.getSceneId() + ".mp4");
            if (mergedSegment.exists()) {
                mergedSegment.delete();
            }

            List<String> mergeCmd = new java.util.ArrayList<>();
            mergeCmd.add(ffmpegPath);
            mergeCmd.add("-y");
            mergeCmd.add("-i"); mergeCmd.add(trimmedVideo.getAbsolutePath());
            mergeCmd.add("-i"); mergeCmd.add(audioFile.getAbsolutePath());
            mergeCmd.add("-t"); mergeCmd.add(String.format(java.util.Locale.US, "%.3f", audioDuration));
            mergeCmd.add("-vf"); mergeCmd.add("scale=720:720:force_original_aspect_ratio=decrease,pad=720:720:(ow-iw)/2:(oh-ih)/2,fps=30");
            mergeCmd.add("-c:v"); mergeCmd.add("libx264");
            mergeCmd.add("-preset"); mergeCmd.add("fast");
            mergeCmd.add("-crf"); mergeCmd.add("23");
            mergeCmd.add("-pix_fmt"); mergeCmd.add("yuv420p");
            mergeCmd.add("-c:a"); mergeCmd.add("aac");
            mergeCmd.add("-b:a"); mergeCmd.add("128k");
            mergeCmd.add("-ar"); mergeCmd.add("44100");
            mergeCmd.add("-ac"); mergeCmd.add("2");
            mergeCmd.add("-shortest");
            mergeCmd.add(mergedSegment.getAbsolutePath());

            System.out.println("   -> Merge cmd: " + String.join(" ", mergeCmd));
            ProcessBuilder mergePb = new ProcessBuilder(mergeCmd);
            mergePb.redirectErrorStream(true);
            Process mergeProcess = processTracker.start(mergePb);
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(mergeProcess.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    System.out.println("   [FFmpeg Merge] " + line);
                }
            }
            int mergeExit = mergeProcess.waitFor();

            // Cleanup trimmed temp
            if (trimmedVideo.exists()) {
                trimmedVideo.delete();
            }

            if (mergeExit != 0 || !mergedSegment.exists() || mergedSegment.length() < 1024) {
                throw new IOException("FFmpeg segment merge failed for Scene " + scene.getSceneId() + " (exit code: " + mergeExit + ")");
            }

            mergedSegments.add(mergedSegment);
        }

        // 3. Create concat demuxer file listing all segments
        File concatList = new File(System.getProperty("java.io.tmpdir"), "concat_list.txt");
        try (java.io.PrintWriter pw = new java.io.PrintWriter(concatList, java.nio.charset.StandardCharsets.UTF_8)) {
            for (File seg : mergedSegments) {
                pw.println("file '" + seg.getAbsolutePath().replace("\\", "/") + "'");
            }
        }

        File outputDir = new File("output");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        File exportFile = new File(outputDir, "master_export.mp4").getAbsoluteFile();

        try {
            // 4. Use FFmpeg concat demuxer - much more reliable than filter_complex concat
            List<String> concatCmd = new java.util.ArrayList<>();
            concatCmd.add(ffmpegPath);
            concatCmd.add("-y");
            concatCmd.add("-f"); concatCmd.add("concat");
            concatCmd.add("-safe"); concatCmd.add("0");
            concatCmd.add("-i"); concatCmd.add(concatList.getAbsolutePath());
            concatCmd.add("-c"); concatCmd.add("copy");
            concatCmd.add(exportFile.getAbsolutePath());

            System.out.println("ðŸŽ¬ [Video4jEditorService] Running concat: " + String.join(" ", concatCmd));
            ProcessBuilder pb = new ProcessBuilder(concatCmd);
            pb.redirectErrorStream(true);
            Process process = processTracker.start(pb);
            
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[FFmpeg Render] " + line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0 || !exportFile.exists() || exportFile.length() < 1024) {
                throw new IOException("FFmpeg concat exited with error code: " + exitCode);
            }
        } catch (Exception e) {
            System.err.println("âš ï¸ [Video4jEditorService] FFmpeg stitching failed: " + e.getMessage());
            throw new Exception("FFmpeg stitching process failed. " + e.getMessage() + 
                "\n\nPossible reasons:\n1. The 'ffmpeg' executable is not installed or not in your system environment PATH.\n2. One or more generated video or audio clips are corrupted or missing.", e);
        } finally {
            // Cleanup temp files
            for (File seg : mergedSegments) {
                if (seg.exists()) {
                    seg.delete();
                }
            }
            if (concatList.exists()) {
                concatList.delete();
            }
        }

        // 4b. If the user requested a non-default transition between scenes,
        // build an xfade filter chain. Otherwise the simple concat above is final.
        if (mergedSegments.size() > 1 && anySceneRequestsTransition(scenes)) {
            try {
                runXfadeConcat(mergedSegments, scenes, ffmpegPath, exportFile);
            } catch (Exception xfadeEx) {
                System.err.println("[Video4jEditorService] xfade concat failed, keeping simple concat result: " + xfadeEx.getMessage());
            }
        }

        // 5. Save final video as master_export.mp4 and open system folder containing it
        System.out.println("âœ… [Video4jEditorService] Master render complete: " + exportFile.getAbsolutePath());
        openSystemFolder(exportFile);

        return exportFile;
    }

    /**
     * Probes the duration of a media file using ffmpeg -i.
     */
    private double probeDuration(File mediaFile) {
        try {
            String ffmpegPathStr = configService.getFfmpegPath();
            ProcessBuilder pb = new ProcessBuilder(ffmpegPathStr, "-i", mediaFile.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process p = processTracker.start(pb);
            double duration = 0.0;
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                String line;
                java.util.regex.Pattern durPattern = java.util.regex.Pattern.compile("Duration:\\s+(\\d+):(\\d+):([0-9.]+)");
                while ((line = r.readLine()) != null) {
                    java.util.regex.Matcher m = durPattern.matcher(line);
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
            System.err.println("âš ï¸ [Video4jEditorService] Could not probe duration: " + e.getMessage());
        }
        return 0.0;
    }

    /**
     * Build the ffmpeg lavfi command that generates the dummy fallback audio for a
     * scene that has no narration TTS available. The source is silence (anullsrc)
     * so the master export no longer sounds like a continuous test-tone beep.
     */
    static java.util.List<String> buildDummyWavCommand(String ffmpegPath, int duration, File dest) {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(ffmpegPath);
        cmd.add("-y");
        cmd.add("-f"); cmd.add("lavfi");
        cmd.add("-i"); cmd.add("anullsrc=r=16000:cl=mono");
        cmd.add("-t"); cmd.add(String.valueOf(duration));
        cmd.add(dest.getAbsolutePath());
        return cmd;
    }

    private void generateDummyWav(Scene scene, File dest) throws Exception {
        validateFfmpeg();

        int duration = 5;
        if (scene.getEndFrame() > scene.getStartFrame()) {
            duration = (scene.getEndFrame() - scene.getStartFrame()) / 30;
        }
        if (duration <= 0) duration = 1;

        System.out.println("ðŸŽµ [Video4jEditorService] Generating silence WAV fallback for Scene " + scene.getSceneId() + " (Duration: " + duration + "s) to: " + dest.getAbsolutePath());
        ProcessBuilder pb = new ProcessBuilder(buildDummyWavCommand(configService.getFfmpegPath(), duration, dest));
        pb.redirectErrorStream(true);
        Process process = processTracker.start(pb);
        
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            while (r.readLine() != null) {}
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("FFmpeg silent WAV generator exited with code: " + exitCode);
        }
    }

    private void openSystemFolder(File targetFile) {
        // Skip opening explorer window during unit tests
        if (System.getProperty("surefire.real.class.path") != null || 
            System.getProperty("java.class.path").contains("junit") || 
            java.awt.GraphicsEnvironment.isHeadless()) {
            System.out.println("â„¹ï¸ Test or headless environment detected. Skipping opening system folder: " + targetFile.getAbsolutePath());
            return;
        }
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                processTracker.start(new ProcessBuilder("explorer.exe", "/select," + targetFile.getAbsolutePath()));
            } else if (os.contains("mac")) {
                processTracker.start(new ProcessBuilder("open", "-R", targetFile.getAbsolutePath()));
            } else {
                processTracker.start(new ProcessBuilder("xdg-open", targetFile.getParentFile().getAbsolutePath()));
            }
        } catch (Exception e) {
            System.err.println("âš ï¸ Could not open system folder: " + e.getMessage());
        }
    }

    private boolean anySceneRequestsTransition(java.util.List<Scene> scenes) {
        for (Scene s : scenes) {
            String t = s.getTransitionType();
            if (t != null && !t.isEmpty() && !"none".equalsIgnoreCase(t)) {
                return true;
            }
        }
        return false;
    }

    private void runXfadeConcat(java.util.List<File> segments, java.util.List<Scene> scenes,
                                String ffmpegPath, File exportFile) throws Exception {
        // Probe each segment duration
        double[] durations = new double[segments.size()];
        for (int i = 0; i < segments.size(); i++) {
            durations[i] = probeDuration(segments.get(i));
            if (durations[i] <= 0) durations[i] = 5.0;
        }
        // Build the xfade filter_complex: chain xfade nodes between segments.
        // Reference: https://trac.ffmpeg.org/wiki/Xfade
        StringBuilder filter = new StringBuilder();
        java.util.List<String> args = new java.util.ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            args.add("-i"); args.add(segments.get(i).getAbsolutePath());
        }
        // Track the most recent output label so each xfade can reference it.
        String lastLabel = "[0:v]";
        String lastAudioLabel = "[0:a]";
        double offset = durations[0];
        for (int i = 1; i < segments.size(); i++) {
            // Use scene[i-1] transition between segment i-1 and i.
            Scene s = scenes.get(i - 1);
            String transition = s.getTransitionType();
            if (transition == null || transition.isEmpty() || "none".equalsIgnoreCase(transition)) {
                transition = "fade";
            }
            double transDur = s.getTransitionDuration();
            if (transDur <= 0) transDur = 1.0;
            // Make sure the transition is not longer than the shortest segment.
            transDur = Math.min(transDur, Math.min(durations[i - 1], durations[i]) / 2.0);
            String outV = i == segments.size() - 1 ? "[v]" : ("[v" + i + "]");
            String outA = i == segments.size() - 1 ? "[a]" : ("[a" + i + "]");
            filter.append(lastLabel).append("[").append(i).append(":v]xfade=transition=")
                  .append(transition).append(":duration=").append(String.format(java.util.Locale.US, "%.3f", transDur))
                  .append(":offset=").append(String.format(java.util.Locale.US, "%.3f", Math.max(0.0, offset - transDur)))
                  .append(outV).append(";");
            filter.append(lastAudioLabel).append("[").append(i).append(":a]acrossfade=d=")
                  .append(String.format(java.util.Locale.US, "%.3f", transDur)).append(outA).append(";");
            lastLabel = outV;
            lastAudioLabel = outA;
            offset += durations[i] - transDur;
        }
        args.add("-filter_complex"); args.add(filter.toString());
        args.add("-map"); args.add(lastLabel);
        args.add("-map"); args.add(lastAudioLabel);
        args.add("-c:v"); args.add("libx264");
        args.add("-preset"); args.add("fast");
        args.add("-crf"); args.add("23");
        args.add("-pix_fmt"); args.add("yuv420p");
        args.add("-c:a"); args.add("aac");
        args.add("-b:a"); args.add("128k");
        args.add("-ar"); args.add("44100");
        args.add("-ac"); args.add("2");
        args.add("-y");
        args.add(exportFile.getAbsolutePath());

        System.out.println("[Video4jEditorService] Running xfade: " + String.join(" ", args));
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectErrorStream(true);
        Process p = processTracker.start(pb);
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                System.out.println("   [FFmpeg xFade] " + line);
            }
        }
        int exit = p.waitFor();
        if (exit != 0 || !exportFile.exists() || exportFile.length() < 1024) {
            throw new IOException("ffmpeg xfade failed with exit " + exit);
        }
    }
}
