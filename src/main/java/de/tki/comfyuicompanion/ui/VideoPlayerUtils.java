package de.tki.comfyuicompanion.ui;

import javafx.scene.media.MediaPlayer;

/**
 * Enterprise utility class for JavaFX media player calculations, formatting, and boundary validation.
 * Encapsulates playback rate parsing, relative seek arithmetic, time formatting, and volume percentage resolution.
 */
public final class VideoPlayerUtils {

    private VideoPlayerUtils() {
        // Utility class - non-instantiable
    }

    /**
     * Formats a duration in milliseconds into a standard mm:ss format.
     *
     * @param millis duration in milliseconds
     * @return formatted time string, or "00:00" for invalid values
     */
    public static String formatTime(double millis) {
        if (Double.isNaN(millis) || millis < 0) {
            return "00:00";
        }
        long totalSeconds = (long) Math.floor(millis / 1000.0);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * Formats the remaining playback duration as a countdown string (e.g. "-00:28").
     * Safely clamps negative or overflow values and guards against NaN or zero boundaries.
     *
     * @param currentMillis current playback position in milliseconds
     * @param totalMillis total duration in milliseconds
     * @return formatted remaining time string prefixed with "-" (e.g. "-01:23"), or "-00:00" for invalid values
     */
    public static String formatRemainingTime(double currentMillis, double totalMillis) {
        if (Double.isNaN(currentMillis) || Double.isNaN(totalMillis) || totalMillis <= 0.0) {
            return "-00:00";
        }
        double safeCurrent = Math.max(0.0, currentMillis);
        double remainingMillis = Math.max(0.0, totalMillis - safeCurrent);
        long remainingSeconds = (long) Math.floor(remainingMillis / 1000.0);
        long minutes = remainingSeconds / 60;
        long seconds = remainingSeconds % 60;
        return String.format("-%02d:%02d", minutes, seconds);
    }

    /**
     * Clamps a media volume level within the valid range [0.0, 1.0].
     *
     * @param volume raw volume level
     * @return clamped volume between 0.0 and 1.0
     */
    public static double clampVolume(double volume) {
        if (Double.isNaN(volume) || volume <= 0.0) {
            return 0.0;
        }
        return Math.min(1.0, volume);
    }

    /**
     * Formats a volume fraction (0.0 to 1.0) and mute flag into an integer percentage string (e.g. "80%", "0%").
     *
     * @param volume volume level fraction between 0.0 and 1.0
     * @param muted whether the media audio is currently muted
     * @return formatted percentage label
     */
    public static String formatVolumePercent(double volume, boolean muted) {
        if (muted || Double.isNaN(volume) || volume <= 0.0) {
            return "0%";
        }
        int percent = (int) Math.round(clampVolume(volume) * 100.0);
        return percent + "%";
    }

    /**
     * Resolves the JavaFX MediaPlayer cycle count based on loop toggle state.
     *
     * @param loopEnabled whether continuous looping is active
     * @return {@link MediaPlayer#INDEFINITE} if true, else 1
     */
    public static int resolveCycleCount(boolean loopEnabled) {
        return loopEnabled ? MediaPlayer.INDEFINITE : 1;
    }

    /**
     * Parses a playback rate string (e.g. "0.5x", "1.0x", "2.0x") into a valid playback speed factor.
     * Guaranteed to return a safe positive multiplier clamped between 0.1 and 8.0, defaulting to 1.0.
     *
     * @param rateLabel textual representation of playback speed
     * @return validated double rate multiplier
     */
    public static double parsePlaybackRate(String rateLabel) {
        if (rateLabel == null || rateLabel.isBlank()) {
            return 1.0;
        }
        try {
            String sanitized = rateLabel.replace("x", "").replace("X", "").trim();
            double rate = Double.parseDouble(sanitized);
            if (Double.isNaN(rate) || Double.isInfinite(rate) || rate <= 0.0) {
                return 1.0;
            }
            return Math.clamp(rate, 0.1, 8.0);
        } catch (NumberFormatException ignored) {
            return 1.0;
        }
    }

    /**
     * Calculates the destination seek timestamp in milliseconds when jumping by a relative time offset.
     * Safely clamps the resulting position within [0.0, totalDurationMillis] and guards against NaN or negative boundaries.
     *
     * @param currentMillis current playback position in milliseconds
     * @param deltaSeconds relative time delta in seconds (positive to jump forward, negative to rewind)
     * @param totalDurationMillis total media duration in milliseconds, or 0 if unknown
     * @return validated and bounded destination timestamp in milliseconds
     */
    public static double calculateSeekTarget(double currentMillis, double deltaSeconds, double totalDurationMillis) {
        double safeCurrent = (Double.isNaN(currentMillis) || currentMillis < 0.0) ? 0.0 : currentMillis;
        double safeDelta = Double.isNaN(deltaSeconds) ? 0.0 : deltaSeconds;
        double target = safeCurrent + (safeDelta * 1000.0);
        if (Double.isNaN(target) || target <= 0.0) {
            return 0.0;
        }
        if (!Double.isNaN(totalDurationMillis) && totalDurationMillis > 0.0 && target > totalDurationMillis) {
            return totalDurationMillis;
        }
        return target;
    }

    /**
     * Extracts an uppercase format label (e.g. "MP4", "WEBM") from a filename or media path.
     * Defaults to "VIDEO" if the input is null, empty, or lacks a file extension.
     *
     * @param fileName file name or file path
     * @return uppercase format badge string
     */
    public static String extractFormatBadge(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "VIDEO";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex >= fileName.length() - 1) {
            return "VIDEO";
        }
        String ext = fileName.substring(dotIndex + 1).trim().toUpperCase();
        if (ext.isEmpty()) {
            return "VIDEO";
        }
        return ext.length() > 6 ? ext.substring(0, 6) : ext;
    }

    /**
     * Resolves human-readable status badge text with visual glyphs for JavaFX MediaPlayer status states.
     *
     * @param status current MediaPlayer status
     * @return formatted badge label text
     */
    public static String resolveStatusBadgeText(MediaPlayer.Status status) {
        if (status == null) {
            return "\u25CF READY";
        }
        return switch (status) {
            case PLAYING -> "\u25CF PLAYING";
            case PAUSED -> "\u23F8 PAUSED";
            case STOPPED -> "\u25A0 STOPPED";
            case STALLED -> "\u23F3 BUFFERING";
            default -> "\u25CF READY";
        };
    }

    /**
     * Resolves the CSS style class name corresponding to a MediaPlayer status state.
     *
     * @param status current MediaPlayer status
     * @return CSS style class name
     */
    public static String resolveStatusBadgeStyle(MediaPlayer.Status status) {
        if (status == null) {
            return "status-badge-ready";
        }
        return switch (status) {
            case PLAYING -> "status-badge-playing";
            case PAUSED -> "status-badge-paused";
            case STOPPED -> "status-badge-stopped";
            case STALLED -> "status-badge-buffering";
            default -> "status-badge-ready";
        };
    }
}
