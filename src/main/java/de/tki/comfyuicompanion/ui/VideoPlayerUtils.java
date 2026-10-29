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
     * Formats video pixel dimensions into a standard resolution badge string (e.g. "1920×1080").
     * Safely guards against non-positive dimensions, returning an empty string if resolution is unavailable.
     *
     * @param width video width in pixels
     * @param height video height in pixels
     * @return formatted resolution string (e.g. "1920×1080", "1280×720"), or empty string for non-positive dimensions
     */
    public static String formatResolutionBadge(int width, int height) {
        if (width <= 0 || height <= 0) {
            return "";
        }
        return width + "\u00D7" + height;
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
     * Resolves an expressive Unicode speaker glyph corresponding to audio volume level and mute state.
     *
     * @param volume volume level fraction between 0.0 and 1.0
     * @param muted whether media audio is currently muted
     * @return Unicode speaker glyph (\uD83D\uDD07 for mute/0%, \uD83D\uDD08 for low, \uD83D\uDD09 for mid, \uD83D\uDD0A for high)
     */
    public static String resolveVolumeGlyph(double volume, boolean muted) {
        if (muted || Double.isNaN(volume) || volume <= 0.0) {
            return "\uD83D\uDD07"; // 🔇
        }
        if (volume < 0.35) {
            return "\uD83D\uDD08"; // 🔈
        }
        if (volume < 0.70) {
            return "\uD83D\uDD09"; // 🔉
        }
        return "\uD83D\uDD0A"; // 🔊
    }

    /**
     * Calculates the normalized progress ratio [0.0, 1.0] from current playback position and total duration.
     * Guards against division by zero, negative offsets, and NaN values.
     *
     * @param currentMillis current playback position in milliseconds
     * @param totalMillis total media duration in milliseconds
     * @return clamped progress fraction between 0.0 and 1.0
     */
    public static double calculateProgress(double currentMillis, double totalMillis) {
        if (Double.isNaN(currentMillis) || Double.isNaN(totalMillis) || totalMillis <= 0.0 || currentMillis <= 0.0) {
            return 0.0;
        }
        double progress = currentMillis / totalMillis;
        return Math.clamp(progress, 0.0, 1.0);
    }

    /**
     * Calculates the seek destination timestamp in milliseconds from a mouse click coordinate along the timeline track.
     * Clamps the target between 0.0 and total duration.
     *
     * @param clickX horizontal click position in local coordinates
     * @param trackWidth width of the slider or timeline component
     * @param totalMillis total media duration in milliseconds
     * @return clamped destination timestamp in milliseconds
     */
    public static double calculateSeekFromClick(double clickX, double trackWidth, double totalMillis) {
        if (Double.isNaN(clickX) || Double.isNaN(trackWidth) || Double.isNaN(totalMillis)
                || trackWidth <= 0.0 || totalMillis <= 0.0) {
            return 0.0;
        }
        double ratio = Math.clamp(clickX / trackWidth, 0.0, 1.0);
        return ratio * totalMillis;
    }

    /**
     * Calculates the normalized audio volume fraction [0.0, 1.0] from a mouse click coordinate along the volume track.
     * Safely clamps boundary overruns and guards against zero widths or invalid numerical values.
     *
     * @param clickX horizontal click position in local coordinates
     * @param trackWidth width of the volume slider track
     * @return clamped volume fraction between 0.0 and 1.0
     */
    public static double calculateVolumeFromClick(double clickX, double trackWidth) {
        if (Double.isNaN(clickX) || Double.isNaN(trackWidth) || trackWidth <= 0.0) {
            return 0.0;
        }
        double ratio = clickX / trackWidth;
        return Math.clamp(ratio, 0.0, 1.0);
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

    /**
     * Resolves the Unicode glyph for the fullscreen toggle button based on current fullscreen state.
     *
     * @param isFullscreen whether the window is currently in fullscreen or maximized mode
     * @return Unicode glyph string (\u26F6 for windowed, \u27C6 for active fullscreen)
     */
    public static String resolveFullscreenGlyph(boolean isFullscreen) {
        return isFullscreen ? "\u27C6" : "\u26F6";
    }

    /**
     * Resolves the descriptive tooltip text for the fullscreen toggle button.
     *
     * @param isFullscreen whether the window is currently in fullscreen mode
     * @return tooltip string
     */
    public static String resolveFullscreenTooltip(boolean isFullscreen) {
        return isFullscreen ? "Exit Fullscreen (Esc / F11)" : "Toggle Fullscreen / Maximize (F11)";
    }

    /**
     * Determines whether the Pause action should be highlighted as the primary button.
     * When playback is actively running (PLAYING), pausing is the primary user interaction.
     *
     * @param status current MediaPlayer status
     * @return true if status is {@link MediaPlayer.Status#PLAYING}, false otherwise
     */
    public static boolean isPausePrimaryAction(MediaPlayer.Status status) {
        return status == MediaPlayer.Status.PLAYING;
    }

    /**
     * Resolves the descriptive tooltip text for the Play button based on playback state.
     *
     * @param status current MediaPlayer status
     * @return tooltip string
     */
    public static String resolvePlayButtonTooltip(MediaPlayer.Status status) {
        return (status == MediaPlayer.Status.PLAYING) ? "Playing" : "Play video (Space)";
    }

    /**
     * Resolves the descriptive tooltip text for the Pause button based on playback state.
     *
     * @param status current MediaPlayer status
     * @return tooltip string
     */
    public static String resolvePauseButtonTooltip(MediaPlayer.Status status) {
        return (status == MediaPlayer.Status.PLAYING) ? "Pause playback (Space)" : "Pause";
    }
}
