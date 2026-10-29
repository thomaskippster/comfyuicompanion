package de.tki.comfyuicompanion.ui;

import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Slider;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

/**
 * Autonomous keyboard shortcut event handler for the JavaFX video preview dialog.
 * Dispatches standard media hotkeys including Space (Play/Pause), M (Mute),
 * Left/Right (Seek jump), Up/Down (Volume), F/F11 (Fullscreen), and Esc (Exit/Close).
 */
public class VideoPreviewShortcutHandler implements EventHandler<KeyEvent> {

    private final MediaPlayer mediaPlayer;
    private final Button btnMute;
    private final Slider volumeSlider;
    private final Label volumePercentLabel;
    private final ProgressBar volumeProgress;
    private final Button btnFullscreen;
    private final Slider timeSlider;
    private final ProgressBar timelineProgress;
    private final Label currentTimeLabel;
    private final Runnable toggleFullscreenAction;
    private final Runnable closeAction;

    /**
     * Constructs a new keyboard shortcut handler with referenced UI controls and actions.
     *
     * @param mediaPlayer active MediaPlayer instance
     * @param btnMute mute/unmute button
     * @param volumeSlider volume slider
     * @param volumePercentLabel volume percentage indicator label
     * @param volumeProgress volume progress bar
     * @param btnFullscreen fullscreen toggle button
     * @param timeSlider timeline seek slider
     * @param timelineProgress timeline progress bar
     * @param currentTimeLabel current playback time label
     * @param toggleFullscreenAction callback invoked to toggle fullscreen mode
     * @param closeAction callback invoked to exit fullscreen or close the dialog
     */
    public VideoPreviewShortcutHandler(MediaPlayer mediaPlayer, Button btnMute, Slider volumeSlider,
                                      Label volumePercentLabel, ProgressBar volumeProgress,
                                      Button btnFullscreen, Slider timeSlider, ProgressBar timelineProgress,
                                      Label currentTimeLabel, Runnable toggleFullscreenAction,
                                      Runnable closeAction) {
        this.mediaPlayer = mediaPlayer;
        this.btnMute = btnMute;
        this.volumeSlider = volumeSlider;
        this.volumePercentLabel = volumePercentLabel;
        this.volumeProgress = volumeProgress;
        this.btnFullscreen = btnFullscreen;
        this.timeSlider = timeSlider;
        this.timelineProgress = timelineProgress;
        this.currentTimeLabel = currentTimeLabel;
        this.toggleFullscreenAction = toggleFullscreenAction;
        this.closeAction = closeAction;
    }

    @Override
    public void handle(KeyEvent event) {
        if (event == null || event.getCode() == null) {
            return;
        }

        KeyCode code = event.getCode();
        if (code == KeyCode.SPACE) {
            handleSpaceKey();
            event.consume();
        } else if (code == KeyCode.M) {
            handleMuteKey();
            event.consume();
        } else if (code == KeyCode.LEFT) {
            seekRelative(-5.0);
            event.consume();
        } else if (code == KeyCode.RIGHT) {
            seekRelative(5.0);
            event.consume();
        } else if (code == KeyCode.UP) {
            adjustVolume(0.05);
            event.consume();
        } else if (code == KeyCode.DOWN) {
            adjustVolume(-0.05);
            event.consume();
        } else if (code == KeyCode.F || code == KeyCode.F11) {
            if (toggleFullscreenAction != null) {
                toggleFullscreenAction.run();
            }
            event.consume();
        } else if (code == KeyCode.ESCAPE) {
            if (closeAction != null) {
                closeAction.run();
            }
            event.consume();
        }
    }

    private void handleSpaceKey() {
        if (mediaPlayer == null) {
            return;
        }
        if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            mediaPlayer.pause();
        } else {
            if (mediaPlayer.getStatus() == MediaPlayer.Status.STOPPED) {
                mediaPlayer.seek(Duration.ZERO);
            }
            mediaPlayer.play();
        }
    }

    private void handleMuteKey() {
        if (mediaPlayer == null) {
            return;
        }
        boolean muted = !mediaPlayer.isMute();
        mediaPlayer.setMute(muted);
        double vol = mediaPlayer.getVolume();
        if (volumeProgress != null) {
            volumeProgress.setProgress(muted ? 0.0 : vol);
        }
        if (btnMute != null) {
            btnMute.setText(VideoPlayerUtils.resolveVolumeGlyph(vol, muted));
        }
        if (volumePercentLabel != null) {
            volumePercentLabel.setText(VideoPlayerUtils.formatVolumePercent(vol, muted));
        }
    }

    private void seekRelative(double deltaSeconds) {
        if (mediaPlayer == null) {
            return;
        }
        double current = mediaPlayer.getCurrentTime() != null ? mediaPlayer.getCurrentTime().toMillis() : 0.0;
        double total = mediaPlayer.getTotalDuration() != null && !mediaPlayer.getTotalDuration().isUnknown()
                ? mediaPlayer.getTotalDuration().toMillis() : 0.0;
        double target = VideoPlayerUtils.calculateSeekTarget(current, deltaSeconds, total);
        mediaPlayer.seek(Duration.millis(target));
        if (timeSlider != null) {
            timeSlider.setValue(target);
        }
        if (timelineProgress != null) {
            timelineProgress.setProgress(VideoPlayerUtils.calculateProgress(target, total));
        }
        if (currentTimeLabel != null) {
            currentTimeLabel.setText(VideoPlayerUtils.formatTime(target));
        }
    }

    private void adjustVolume(double delta) {
        if (mediaPlayer == null || volumeSlider == null) {
            return;
        }
        double newVol = VideoPlayerUtils.clampVolume(mediaPlayer.getVolume() + delta);
        volumeSlider.setValue(newVol * 100.0);
    }
}
