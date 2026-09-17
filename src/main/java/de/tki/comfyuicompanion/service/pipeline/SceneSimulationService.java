package de.tki.comfyuicompanion.service.pipeline;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.impl.ProcessTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Service component responsible for probing media durations and synthesizing placeholder
 * preview videos using FFmpeg when ComfyUI is unavailable or in offline testing mode.
 */
@Component
public class SceneSimulationService {
    private static final Logger logger = LoggerFactory.getLogger(SceneSimulationService.class);

    private final ProcessTracker processTracker;

    public SceneSimulationService() {
        this(null);
    }

    @Autowired
    public SceneSimulationService(@Autowired(required = false) ProcessTracker processTracker) {
        this.processTracker = processTracker;
    }

    private Process startProcess(ProcessBuilder pb) throws IOException {
        return processTracker != null ? processTracker.start(pb) : pb.start();
    }

    /**
     * Validates that the specified FFmpeg executable is present and runnable.
     *
     * @param ffmpegPath path or alias to ffmpeg
     * @throws FileNotFoundException if ffmpeg cannot be found or executed
     */
    public void validateFfmpeg(String ffmpegPath) throws FileNotFoundException {
        File ffmpegFile = new File(ffmpegPath);
        boolean isGlobal = "ffmpeg".equals(ffmpegPath);
        boolean available;

        if (isGlobal) {
            try {
                Process p = startProcess(new ProcessBuilder("ffmpeg", "-version"));
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
     * Probes media file duration in seconds using ffprobe.
     *
     * @param mediaFile  input audio or video file
     * @param ffmpegPath path to the ffmpeg binary
     * @return duration in seconds, or 0.0 on error
     */
    public double probeDurationSeconds(File mediaFile, String ffmpegPath) {
        try {
            String ffprobePathStr;
            if ("ffmpeg".equals(ffmpegPath)) {
                ffprobePathStr = "ffprobe";
            } else {
                File ffmpegFile = new File(ffmpegPath);
                File parent = ffmpegFile.getParentFile();
                if (parent != null) {
                    boolean isWin = System.getProperty("os.name").toLowerCase().contains("win");
                    ffprobePathStr = new File(parent, isWin ? "ffprobe.exe" : "ffprobe").getAbsolutePath();
                } else {
                    ffprobePathStr = ffmpegPath.replace("ffmpeg", "ffprobe").replace("ffmpeg.exe", "ffprobe.exe");
                }
            }

            ProcessBuilder pb = new ProcessBuilder(
                    ffprobePathStr,
                    "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "csv=p=0",
                    mediaFile.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process p = startProcess(pb);
            String line;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                line = r.readLine();
            }
            p.waitFor();
            if (line != null && !line.trim().isEmpty()) {
                return Double.parseDouble(line.trim());
            }
        } catch (Exception e) {
            logger.error("⚠️ [SceneSimulation] Could not probe duration: {}", e.getMessage());
        }
        return 0.0;
    }

    /**
     * Generates a simulated placeholder video with an informative overlay for the given scene.
     *
     * @param scene      the scene to visualize
     * @param ffmpegPath path to the active FFmpeg binary
     * @return generated placeholder video file
     * @throws Exception if generation fails
     */
    public File generateSimulatedVideo(Scene scene, String ffmpegPath) throws Exception {
        validateFfmpeg(ffmpegPath);

        Path targetDir = Paths.get("").toAbsolutePath();
        String localFilename = "scene_" + scene.getSceneId() + "_simulated.mp4";
        Path targetFile = targetDir.resolve(localFilename);

        int duration = 5;
        String audioPath = scene.getAudioPath();
        if (audioPath != null && !audioPath.trim().isEmpty() && new File(audioPath).exists()) {
            double audioDur = probeDurationSeconds(new File(audioPath), ffmpegPath);
            if (audioDur > 0) {
                duration = (int) Math.ceil(audioDur);
            }
        } else if (scene.getEndFrame() > scene.getStartFrame()) {
            duration = (scene.getEndFrame() - scene.getStartFrame()) / 30;
        }
        if (duration <= 0) {
            duration = 3;
        }

        String promptText = scene.getPrompt();
        if (promptText == null || promptText.trim().isEmpty()) {
            promptText = "Scene " + scene.getSceneId();
        }

        String escapedPrompt = promptText
                .replace("\\", "\\\\")
                .replace("'", "'\\\\''")
                .replace(":", "\\\\:")
                .replace("%", "%%")
                .replace("\n", " ");

        if (escapedPrompt.length() > 120) {
            escapedPrompt = escapedPrompt.substring(0, 117) + "...";
        }
        String escapedSceneId = scene.getSceneId().replace(":", "\\\\:");

        String drawFilter =
                "drawtext=text='Scene " + escapedSceneId + "':fontsize=36:fontcolor=white:x=(w-tw)/2:y=h/4-th/2," +
                "drawtext=text='" + escapedPrompt + "':fontsize=20:fontcolor=0xCCCCCC:x=(w-tw)/2:y=h/2-th/2," +
                "drawtext=text='[ComfyUI Offline - Placeholder]':fontsize=16:fontcolor=0x888888:x=(w-tw)/2:y=3*h/4";

        logger.info("🎥 [SceneSimulation] Generating placeholder video with scene info (Duration: {}s) to: {}", duration, targetFile);
        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpegPath);
        cmd.add("-y");
        cmd.add("-f"); cmd.add("lavfi");
        cmd.add("-i"); cmd.add("color=c=0x1a1a2e:s=720x720:d=" + duration + ":r=30");
        cmd.add("-vf"); cmd.add(drawFilter);
        cmd.add("-pix_fmt"); cmd.add("yuv420p");
        cmd.add("-c:v"); cmd.add("libx264");
        cmd.add("-preset"); cmd.add("fast");
        cmd.add("-crf"); cmd.add("23");
        cmd.add(targetFile.toString());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = startProcess(pb);

        try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            while (r.readLine() != null) {}
        }

        int exitCode = process.waitFor();
        if (exitCode == 0 && Files.exists(targetFile) && Files.size(targetFile) > 1024) {
            logger.info("🎥 [SceneSimulation] Generated placeholder video with scene overlay: {}", targetFile);
            return targetFile.toFile();
        } else {
            logger.info("⚠️ [SceneSimulation] Drawtext failed (exit code {}). Falling back to plain color source.", exitCode);
            ProcessBuilder pbFallback = new ProcessBuilder(
                    ffmpegPath, "-y",
                    "-f", "lavfi",
                    "-i", "color=c=0x1a1a2e:s=720x720:d=" + duration + ":r=30",
                    "-pix_fmt", "yuv420p",
                    "-c:v", "libx264",
                    targetFile.toString()
            );
            pbFallback.redirectErrorStream(true);
            Process fallbackProcess = startProcess(pbFallback);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(fallbackProcess.getInputStream(), StandardCharsets.UTF_8))) {
                while (r.readLine() != null) {}
            }
            int fallbackExit = fallbackProcess.waitFor();
            if (fallbackExit == 0 && Files.exists(targetFile) && Files.size(targetFile) > 1024) {
                return targetFile.toFile();
            }
            throw new IOException("FFmpeg exited with code " + fallbackExit + " when generating simulation fallback.");
        }
    }
}
