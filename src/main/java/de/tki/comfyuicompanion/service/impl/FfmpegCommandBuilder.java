package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.domain.Scene;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Stateless factory for FFmpeg command-line argument lists.
 * <p>
 * Contains no process or I/O logic, which keeps command construction deterministic and
 * easily unit-testable. Execution is handled by {@link FfmpegProcessRunner}.
 */
public final class FfmpegCommandBuilder {

    private FfmpegCommandBuilder() {
    }

    /**
     * Builds the command that converts a still image into an MP4 clip with a Ken Burns zoom effect.
     *
     * @param ffmpegPath path to the FFmpeg binary
     * @param imageFile  the input image
     * @param destVideo  the output video
     * @param duration   clip duration in seconds
     * @param fps        output frame rate
     * @return the command argument list
     */
    public static List<String> imageToVideo(String ffmpegPath, File imageFile, File destVideo, int duration, int fps) {
        String motionFilter = String.format(Locale.US,
                "scale=trunc(iw/2)*2:trunc(ih/2)*2,zoompan=z='min(zoom+0.0008,1.12)':d=1:x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':fps=%d", fps);
        return List.of(
                ffmpegPath, "-y",
                "-loop", "1",
                "-i", imageFile.getAbsolutePath(),
                "-c:v", "libx264",
                "-t", String.valueOf(duration),
                "-pix_fmt", "yuv420p",
                "-r", String.valueOf(fps),
                "-vf", motionFilter,
                destVideo.getAbsolutePath());
    }

    /**
     * Builds a probe command ({@code ffmpeg -i <media>}) whose stderr/stdout carries stream metadata.
     *
     * @param ffmpegPath path to the FFmpeg binary
     * @param mediaPath  the media file to inspect
     * @return the command argument list
     */
    public static List<String> probe(String ffmpegPath, String mediaPath) {
        return List.of(ffmpegPath, "-i", mediaPath);
    }

    /**
     * Builds the optional {@code eq} video filter chain for a scene's brightness/contrast settings.
     *
     * @param scene the scene definition
     * @return the filter expression, or {@code null} if the scene needs no color correction
     */
    public static String buildColorFilter(Scene scene) {
        if (Math.abs(scene.getContrast() - 1.0) > 0.01 || Math.abs(scene.getBrightness()) > 0.01) {
            return String.format(Locale.US, "eq=contrast=%.2f:brightness=%.2f",
                    scene.getContrast(), scene.getBrightness() / 100.0);
        }
        return null;
    }

    /**
     * Builds the scene trimming command (video only, re-encoded to H.264).
     *
     * @param ffmpegPath path to the FFmpeg binary
     * @param videoPath  the input video path
     * @param startTime  trim start in seconds
     * @param duration   trim duration in seconds
     * @param videoFilter optional {@code -vf} expression; {@code null} or empty to omit
     * @param tempFile   the output file
     * @return the command argument list
     */
    public static List<String> trim(String ffmpegPath, String videoPath, double startTime, double duration,
                                    String videoFilter, File tempFile) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        command.add("-ss");
        command.add(String.format(Locale.US, "%.3f", startTime));
        command.add("-i");
        command.add(videoPath);
        command.add("-t");
        command.add(String.format(Locale.US, "%.3f", duration));
        if (videoFilter != null && !videoFilter.isEmpty()) {
            command.add("-vf");
            command.add(videoFilter);
        }
        command.addAll(List.of(
                "-c:v", "libx264",
                "-preset", "medium",
                "-crf", "18",
                "-pix_fmt", "yuv420p",
                "-an",
                tempFile.getAbsolutePath()));
        return command;
    }

    /**
     * Builds the FFmpeg lavfi command that generates dummy fallback audio (silence) for a scene.
     *
     * @param ffmpegPath path to the FFmpeg binary
     * @param duration   audio duration in seconds
     * @param dest       destination audio file
     * @return the command argument list
     */
    public static List<String> dummyWav(String ffmpegPath, int duration, File dest) {
        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpegPath);
        cmd.add("-y");
        cmd.add("-f"); cmd.add("lavfi");
        cmd.add("-i"); cmd.add("anullsrc=r=16000:cl=mono");
        cmd.add("-t"); cmd.add(String.valueOf(duration));
        cmd.add(dest.getAbsolutePath());
        return cmd;
    }

    /**
     * Builds the command merging a trimmed video and an audio track into a standardized segment.
     *
     * @param ffmpegPath    path to the FFmpeg binary
     * @param trimmedVideo  the trimmed input video
     * @param audioFile     the synchronized audio file
     * @param audioDuration duration in seconds
     * @param targetWidth   target video width in pixels
     * @param targetHeight  target video height in pixels
     * @param mergedSegment target output file
     * @return the command argument list
     */
    public static List<String> mergeSegment(String ffmpegPath, File trimmedVideo, File audioFile, double audioDuration,
                                            int targetWidth, int targetHeight, File mergedSegment) {
        return List.of(
                ffmpegPath, "-y",
                "-i", trimmedVideo.getAbsolutePath(),
                "-i", audioFile.getAbsolutePath(),
                "-t", String.format(Locale.US, "%.3f", audioDuration),
                "-vf", String.format(Locale.US, "scale=%d:%d:force_original_aspect_ratio=decrease,pad=%d:%d:(ow-iw)/2:(oh-ih)/2,fps=30",
                        targetWidth, targetHeight, targetWidth, targetHeight),
                "-c:v", "libx264",
                "-preset", "slow",
                "-crf", "17",
                "-pix_fmt", "yuv420p",
                "-c:a", "aac",
                "-b:a", "192k",
                "-ar", "44100",
                "-ac", "2",
                "-shortest",
                mergedSegment.getAbsolutePath());
    }

    /**
     * Builds the concat demuxer command that stitches segments listed in a text file.
     *
     * @param ffmpegPath path to the FFmpeg binary
     * @param concatList the concat list file
     * @param exportFile the output video
     * @return the command argument list
     */
    public static List<String> concatDemuxer(String ffmpegPath, File concatList, File exportFile) {
        return List.of(
                ffmpegPath, "-y",
                "-f", "concat",
                "-safe", "0",
                "-i", concatList.getAbsolutePath(),
                "-c", "copy",
                exportFile.getAbsolutePath());
    }
}
