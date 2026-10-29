package de.tki.comfyuicompanion.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.geometry.Insets;

import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import java.awt.Frame;
import java.awt.Window;
import javax.swing.WindowConstants;
import java.io.File;
import java.net.URI;
import java.net.URL;

/**
 * Self-contained video preview dialog. Embeds a JavaFX MediaView inside a
 * JFXPanel so it runs alongside the rest of the Swing UI. The MediaPlayer
 * is fully released on close to avoid leaking native resources.
 * Scene composition, layout controls, and shortcuts are coordinated via
 * {@link VideoPreviewSceneCoordinator}.
 */
public class VideoPreviewDialog extends JDialog {
    private static final Logger logger = LoggerFactory.getLogger(VideoPreviewDialog.class);

    private MediaPlayer mediaPlayer;

    /**
     * Constructs a VideoPreviewDialog with an AWT Frame owner.
     *
     * @param owner parent frame
     * @param title dialog title
     * @param videoFile video file to play
     * @param darkMode whether dark theme tokens should be applied
     */
    public VideoPreviewDialog(Frame owner, String title, File videoFile, boolean darkMode) {
        this((Window) owner, title, videoFile, darkMode);
    }

    /**
     * Constructs a VideoPreviewDialog with a Window owner.
     *
     * @param owner parent window
     * @param title dialog title
     * @param videoFile video file to play
     * @param darkMode whether dark theme tokens should be applied
     */
    public VideoPreviewDialog(Window owner, String title, File videoFile, boolean darkMode) {
        super(owner, "Preview: " + title, ModalityType.MODELESS);
        setSize(720, 540);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JFXPanel fxPanel = new JFXPanel();
        getContentPane().add(fxPanel);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) { stopPlayer(); }
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) { stopPlayer(); }
        });

        Platform.runLater(() -> {
            try {
                URI uri = videoFile.toURI();
                Media media = new Media(uri.toString());
                mediaPlayer = new MediaPlayer(media);
                mediaPlayer.setAutoPlay(true);
                mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);

                VideoPreviewSceneCoordinator coordinator = new VideoPreviewSceneCoordinator(
                        this,
                        videoFile,
                        mediaPlayer,
                        darkMode,
                        () -> {
                            stopPlayer();
                            dispose();
                        }
                );

                Scene scene = coordinator.buildScene();
                fxPanel.setScene(scene);

                SwingUtilities.invokeLater(() -> {
                    toFront();
                    requestFocus();
                });
            } catch (Exception ex) {
                logger.error("Failed to initialize video preview scene for file: {}", videoFile.getName(), ex);
                Label error = new Label("Could not open video preview: " + ex.getMessage());
                error.getStyleClass().add("label-title");
                error.setTextFill(Color.RED);
                BorderPane errRoot = new BorderPane(error);
                errRoot.getStyleClass().add("root");
                errRoot.setPadding(new Insets(20));
                Scene errScene = new Scene(errRoot);
                URL cssUrl = getClass().getResource("/css/video-architect.css");
                if (cssUrl != null) {
                    errScene.getStylesheets().add(cssUrl.toExternalForm());
                }
                fxPanel.setScene(errScene);
            }
        });
    }

    private void stopPlayer() {
        if (mediaPlayer != null) {
            try {
                Platform.runLater(() -> {
                    try { mediaPlayer.stop(); } catch (Exception ignored) { logger.debug("Ignored: {}", ignored.getMessage()); }
                    try { mediaPlayer.dispose(); } catch (Exception ignored) { logger.debug("Ignored: {}", ignored.getMessage()); }
                });
            } catch (Exception ignored) {
                logger.debug("Ignored: {}", ignored.getMessage());
            }
            mediaPlayer = null;
        }
    }

    /**
     * Formats a duration in milliseconds into a standard mm:ss format.
     *
     * @param millis duration in milliseconds
     * @return formatted time string, or "00:00" for invalid values
     */
    static String formatTime(double millis) {
        return VideoPlayerUtils.formatTime(millis);
    }

    /**
     * Formats the remaining playback duration as a countdown string (e.g. "-00:28").
     *
     * @param currentMillis current playback position in milliseconds
     * @param totalMillis total duration in milliseconds
     * @return formatted remaining time string prefixed with "-" (e.g. "-01:23"), or "-00:00" for invalid values
     */
    static String formatRemainingTime(double currentMillis, double totalMillis) {
        return VideoPlayerUtils.formatRemainingTime(currentMillis, totalMillis);
    }

    /**
     * Clamps a media volume level within the valid range [0.0, 1.0].
     *
     * @param volume raw volume level
     * @return clamped volume between 0.0 and 1.0
     */
    static double clampVolume(double volume) {
        return VideoPlayerUtils.clampVolume(volume);
    }

    /**
     * Formats a volume fraction (0.0 to 1.0) and mute flag into an integer percentage string (e.g. "80%", "0%").
     *
     * @param volume volume level fraction between 0.0 and 1.0
     * @param muted whether the media audio is currently muted
     * @return formatted percentage label
     */
    static String formatVolumePercent(double volume, boolean muted) {
        return VideoPlayerUtils.formatVolumePercent(volume, muted);
    }

    /**
     * Resolves the JavaFX MediaPlayer cycle count based on loop toggle state.
     *
     * @param loopEnabled whether continuous looping is active
     * @return {@link MediaPlayer#INDEFINITE} if true, else 1
     */
    static int resolveCycleCount(boolean loopEnabled) {
        return VideoPlayerUtils.resolveCycleCount(loopEnabled);
    }

    /**
     * Parses a playback rate string (e.g. "0.5x", "1.0x", "2.0x") into a valid playback speed factor.
     * Guaranteed to return a safe positive multiplier clamped between 0.1 and 8.0, defaulting to 1.0.
     *
     * @param rateLabel textual representation of playback speed
     * @return validated double rate multiplier
     */
    static double parsePlaybackRate(String rateLabel) {
        return VideoPlayerUtils.parsePlaybackRate(rateLabel);
    }

    /**
     * Determines whether the specified playback rate represents a non-standard multiplier.
     *
     * @param rateLabel textual representation of playback speed
     * @return true if rate differs from 1.0x, false otherwise
     */
    static boolean isCustomPlaybackRate(String rateLabel) {
        return VideoPlayerUtils.isCustomPlaybackRate(rateLabel);
    }

    /**
     * Steps the playback speed up or down to the nearest preset.
     *
     * @param currentRate current rate string (e.g. "1.0x")
     * @param stepUp true to step up, false to step down
     * @return adjacent rate preset string
     */
    static String stepPlaybackRate(String currentRate, boolean stepUp) {
        return VideoPlayerUtils.stepPlaybackRate(currentRate, stepUp);
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
    static double calculateSeekTarget(double currentMillis, double deltaSeconds, double totalDurationMillis) {
        return VideoPlayerUtils.calculateSeekTarget(currentMillis, deltaSeconds, totalDurationMillis);
    }

    /**
     * Extracts an uppercase format label (e.g. "MP4", "WEBM") from a file name.
     *
     * @param fileName file name
     * @return uppercase format badge string
     */
    static String extractFormatBadge(String fileName) {
        return VideoPlayerUtils.extractFormatBadge(fileName);
    }

    /**
     * Formats video pixel dimensions into a standard resolution badge string (e.g. "1920×1080").
     *
     * @param width video width in pixels
     * @param height video height in pixels
     * @return formatted resolution string, or empty string for non-positive dimensions
     */
    static String formatResolutionBadge(int width, int height) {
        return VideoPlayerUtils.formatResolutionBadge(width, height);
    }

    /**
     * Resolves human-readable status badge text with visual glyphs for MediaPlayer status.
     *
     * @param status current status
     * @return formatted badge label text
     */
    static String resolveStatusBadgeText(MediaPlayer.Status status) {
        return VideoPlayerUtils.resolveStatusBadgeText(status);
    }

    /**
     * Resolves the CSS style class name corresponding to a MediaPlayer status.
     *
     * @param status current status
     * @return CSS style class name
     */
    static String resolveStatusBadgeStyle(MediaPlayer.Status status) {
        return VideoPlayerUtils.resolveStatusBadgeStyle(status);
    }

    /**
     * Resolves an expressive Unicode speaker glyph corresponding to audio volume level and mute state.
     *
     * @param volume volume level fraction between 0.0 and 1.0
     * @param muted whether media audio is currently muted
     * @return Unicode speaker glyph
     */
    static String resolveVolumeGlyph(double volume, boolean muted) {
        return VideoPlayerUtils.resolveVolumeGlyph(volume, muted);
    }

    /**
     * Calculates the normalized progress ratio [0.0, 1.0] from current playback position and total duration.
     *
     * @param currentMillis current playback position in milliseconds
     * @param totalMillis total media duration in milliseconds
     * @return clamped progress fraction between 0.0 and 1.0
     */
    static double calculateProgress(double currentMillis, double totalMillis) {
        return VideoPlayerUtils.calculateProgress(currentMillis, totalMillis);
    }

    /**
     * Calculates the seek destination timestamp in milliseconds from a mouse click coordinate along the timeline track.
     *
     * @param clickX horizontal click position in local coordinates
     * @param trackWidth width of the slider or timeline component
     * @param totalMillis total media duration in milliseconds
     * @return clamped destination timestamp in milliseconds
     */
    static double calculateSeekFromClick(double clickX, double trackWidth, double totalMillis) {
        return VideoPlayerUtils.calculateSeekFromClick(clickX, trackWidth, totalMillis);
    }

    /**
     * Calculates the normalized audio volume fraction [0.0, 1.0] from a mouse click coordinate along the volume track.
     *
     * @param clickX horizontal click position in local coordinates
     * @param trackWidth width of the volume slider track
     * @return clamped volume fraction between 0.0 and 1.0
     */
    static double calculateVolumeFromClick(double clickX, double trackWidth) {
        return VideoPlayerUtils.calculateVolumeFromClick(clickX, trackWidth);
    }

    /**
     * Resolves the Unicode glyph for the fullscreen toggle button based on current fullscreen state.
     *
     * @param isFullscreen whether the window is currently in fullscreen mode
     * @return Unicode glyph string
     */
    static String resolveFullscreenGlyph(boolean isFullscreen) {
        return VideoPlayerUtils.resolveFullscreenGlyph(isFullscreen);
    }

    /**
     * Resolves the descriptive tooltip text for the fullscreen toggle button.
     *
     * @param isFullscreen whether the window is currently in fullscreen mode
     * @return tooltip string
     */
    static String resolveFullscreenTooltip(boolean isFullscreen) {
        return VideoPlayerUtils.resolveFullscreenTooltip(isFullscreen);
    }

    @Override
    public void dispose() {
        stopPlayer();
        super.dispose();
    }
}
