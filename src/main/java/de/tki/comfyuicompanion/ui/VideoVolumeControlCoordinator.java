package de.tki.comfyuicompanion.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

/**
 * Enterprise coordinator responsible for orchestrating the audio volume control subsystem
 * within {@link VideoPreviewDialog}.
 * Encapsulates mute toggling, dynamic warning styling, interactive hover tooltips,
 * and dual-layer progress bar synchronization.
 */
public class VideoVolumeControlCoordinator {

    private final MediaPlayer mediaPlayer;
    private final Button btnMute;
    private final ProgressBar volumeProgress;
    private final Slider volumeSlider;
    private final StackPane volumeSliderStack;
    private final Label volumePercentLabel;
    private final HBox volumeBox;

    /**
     * Constructs and initializes the volume control subsystem for the specified media player.
     *
     * @param mediaPlayer active JavaFX MediaPlayer instance, may be null in headless testing
     */
    public VideoVolumeControlCoordinator(MediaPlayer mediaPlayer) {
        this.mediaPlayer = mediaPlayer;

        double initialVolume = 0.8;
        if (mediaPlayer != null) {
            mediaPlayer.setVolume(initialVolume);
        }

        // Initialize mute button with contextual glyph and tooltip
        this.btnMute = new Button(VideoPlayerUtils.resolveVolumeGlyph(initialVolume, false));
        this.btnMute.getStyleClass().addAll("button", "icon-button");
        this.btnMute.setTooltip(new Tooltip(VideoPlayerUtils.resolveMuteTooltip(false)));

        // Dual-layer volume progress bar
        this.volumeProgress = new ProgressBar(initialVolume);
        this.volumeProgress.getStyleClass().add("volume-progress-bar");

        // Interactive volume slider
        this.volumeSlider = new Slider(0, 100, initialVolume * 100.0);
        this.volumeSlider.getStyleClass().add("volume-slider");

        // Dynamic interactive hover tooltip showing destination volume percentage
        Tooltip volumeTooltip = new Tooltip(VideoPlayerUtils.formatVolumePercent(initialVolume, false));
        volumeTooltip.setShowDelay(Duration.millis(50));
        this.volumeSlider.setTooltip(volumeTooltip);
        this.volumeSlider.setOnMouseMoved(e -> {
            double targetVol = VideoPlayerUtils.calculateVolumeFromClick(e.getX(), volumeSlider.getWidth());
            volumeTooltip.setText(VideoPlayerUtils.formatVolumePercent(targetVol, false));
        });

        this.volumeSliderStack = new StackPane(volumeProgress, volumeSlider);
        this.volumeSliderStack.getStyleClass().add("volume-slider-stack");
        this.volumeSliderStack.setAlignment(Pos.CENTER);

        // Volume percentage readout label with interactive click-to-mute
        this.volumePercentLabel = new Label(VideoPlayerUtils.formatVolumePercent(initialVolume, false));
        this.volumePercentLabel.getStyleClass().addAll("label-volume", "label-volume-interactive");
        this.volumePercentLabel.setTooltip(new Tooltip("Click to toggle mute (M)"));

        // Wire event handlers
        this.btnMute.setOnAction(e -> toggleMute());
        this.volumePercentLabel.setOnMouseClicked(e -> toggleMute());

        this.volumeSlider.setOnMouseClicked(e -> {
            double targetVol = VideoPlayerUtils.calculateVolumeFromClick(e.getX(), volumeSlider.getWidth());
            volumeSlider.setValue(targetVol * 100.0);
        });

        this.volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                double vol = VideoPlayerUtils.clampVolume(newVal.doubleValue() / 100.0);
                boolean isMuted = (vol == 0.0);
                if (mediaPlayer != null) {
                    mediaPlayer.setVolume(vol);
                    if (vol > 0 && mediaPlayer.isMute()) {
                        mediaPlayer.setMute(false);
                    } else if (vol == 0 && !mediaPlayer.isMute()) {
                        mediaPlayer.setMute(true);
                    }
                    isMuted = mediaPlayer.isMute();
                }
                applyMuteState(isMuted, vol);
            }
        });

        this.volumeBox = new HBox(6, btnMute, volumeSliderStack, volumePercentLabel);
        this.volumeBox.getStyleClass().add("volume-control-box");
        this.volumeBox.setAlignment(Pos.CENTER);
    }

    /**
     * Toggles the audio mute state on the media player and synchronizes UI components.
     */
    public void toggleMute() {
        if (mediaPlayer != null) {
            boolean nextMuted = !mediaPlayer.isMute();
            mediaPlayer.setMute(nextMuted);
            double vol = mediaPlayer.getVolume();
            applyMuteState(nextMuted, vol);
        } else {
            // Support headless / mock invocation
            boolean currentlyMuted = btnMute.getStyleClass().contains("mute-active");
            applyMuteState(!currentlyMuted, volumeSlider.getValue() / 100.0);
        }
    }

    /**
     * Adjusts the volume by a relative delta step, updating the slider position.
     *
     * @param delta relative volume offset between -1.0 and 1.0
     */
    public void adjustVolume(double delta) {
        double currentVol = volumeSlider.getValue() / 100.0;
        double newVol = VideoPlayerUtils.clampVolume(currentVol + delta);
        volumeSlider.setValue(newVol * 100.0);
    }

    /**
     * Applies the given mute and volume state across all related UI elements.
     *
     * @param muted whether audio is muted
     * @param volume volume level fraction between 0.0 and 1.0
     */
    public void applyMuteState(boolean muted, double volume) {
        volumeProgress.setProgress(muted ? 0.0 : volume);
        btnMute.setText(VideoPlayerUtils.resolveVolumeGlyph(volume, muted));
        btnMute.setTooltip(new Tooltip(VideoPlayerUtils.resolveMuteTooltip(muted)));

        btnMute.getStyleClass().remove("mute-active");
        if (muted) {
            btnMute.getStyleClass().add("mute-active");
        }

        volumePercentLabel.setText(VideoPlayerUtils.formatVolumePercent(volume, muted));
        volumePercentLabel.getStyleClass().remove("label-volume-muted");
        if (muted) {
            volumePercentLabel.getStyleClass().add("label-volume-muted");
        }
    }

    public Button getBtnMute() {
        return btnMute;
    }

    public ProgressBar getVolumeProgress() {
        return volumeProgress;
    }

    public Slider getVolumeSlider() {
        return volumeSlider;
    }

    public StackPane getVolumeSliderStack() {
        return volumeSliderStack;
    }

    public Label getVolumePercentLabel() {
        return volumePercentLabel;
    }

    public HBox getVolumeBox() {
        return volumeBox;
    }
}
