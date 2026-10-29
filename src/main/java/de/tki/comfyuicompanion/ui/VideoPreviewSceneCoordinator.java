package de.tki.comfyuicompanion.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.io.File;
import java.net.URL;

/**
 * Enterprise coordinator responsible for constructing and orchestrating the JavaFX
 * scene inside {@link VideoPreviewDialog}.
 * Manages responsive viewport sizing, playback controls, keyboard shortcut dispatching,
 * and fullscreen toggle transitions.
 */
public class VideoPreviewSceneCoordinator {

    private final JDialog dialog;
    private final File videoFile;
    private final MediaPlayer mediaPlayer;
    private final boolean darkMode;
    private final Runnable closeHandler;

    private boolean isFullscreen = false;
    private Rectangle normalBounds;
    private boolean showRemainingTime = false;

    /**
     * Constructs a new scene coordinator.
     *
     * @param dialog parent preview dialog
     * @param videoFile video media file
     * @param mediaPlayer active JavaFX MediaPlayer instance
     * @param darkMode whether dark theme styling tokens are enabled
     * @param closeHandler callback invoked when user triggers a close action
     */
    public VideoPreviewSceneCoordinator(JDialog dialog, File videoFile, MediaPlayer mediaPlayer,
                                        boolean darkMode, Runnable closeHandler) {
        this.dialog = dialog;
        this.videoFile = videoFile;
        this.mediaPlayer = mediaPlayer;
        this.darkMode = darkMode;
        this.closeHandler = closeHandler;
    }

    /**
     * Builds and returns the configured JavaFX preview scene.
     *
     * @return fully initialized and styled {@link Scene}
     */
    public Scene buildScene() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");
        if (!darkMode) {
            root.getStyleClass().add("light-theme");
        }

        // Header
        Label titleLabel = new Label("\uD83C\uDFAC " + videoFile.getName());
        titleLabel.getStyleClass().add("label-title");

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        Label resolutionBadge = new Label("");
        resolutionBadge.getStyleClass().addAll("badge-pill", "badge-resolution");
        resolutionBadge.setVisible(false);
        resolutionBadge.setManaged(false);

        Label formatBadge = new Label(VideoPlayerUtils.extractFormatBadge(videoFile.getName()));
        formatBadge.getStyleClass().addAll("badge-pill", "badge-format");

        Label statusBadge = new Label(VideoPlayerUtils.resolveStatusBadgeText(MediaPlayer.Status.READY));
        statusBadge.getStyleClass().addAll("badge-pill", "status-badge", "status-badge-ready");

        HBox headerBadges = new HBox(8, resolutionBadge, formatBadge, statusBadge);
        headerBadges.setAlignment(Pos.CENTER_RIGHT);

        HBox header = new HBox(12, titleLabel, headerSpacer, headerBadges);
        header.getStyleClass().add("header-panel");
        header.setAlignment(Pos.CENTER_LEFT);
        root.setTop(header);

        // Center Viewport with responsive MediaView binding
        MediaView mediaView = new MediaView(mediaPlayer);
        mediaView.setPreserveRatio(true);
        mediaView.setSmooth(true);

        StackPane videoContainer = new StackPane(mediaView);
        videoContainer.getStyleClass().add("video-viewport");
        mediaView.fitWidthProperty().bind(videoContainer.widthProperty().subtract(16));
        mediaView.fitHeightProperty().bind(videoContainer.heightProperty().subtract(16));
        root.setCenter(videoContainer);

        // Timeline controls with dual-layer progress bar and interactive seeking slider
        Label currentTimeLabel = new Label("00:00");
        currentTimeLabel.getStyleClass().add("label-time");

        ProgressBar timelineProgress = new ProgressBar(0.0);
        timelineProgress.getStyleClass().add("timeline-progress-bar");
        timelineProgress.setMaxWidth(Double.MAX_VALUE);

        Slider timeSlider = new Slider(0, 100, 0);
        timeSlider.getStyleClass().add("timeline-slider");
        timeSlider.setMaxWidth(Double.MAX_VALUE);

        StackPane timelineStack = new StackPane(timelineProgress, timeSlider);
        timelineStack.setAlignment(Pos.CENTER);
        HBox.setHgrow(timelineStack, Priority.ALWAYS);

        Tooltip seekTooltip = new Tooltip("00:00");
        seekTooltip.setShowDelay(Duration.millis(50));
        timeSlider.setTooltip(seekTooltip);
        timeSlider.setOnMouseMoved(e -> {
            Duration tot = mediaPlayer.getTotalDuration();
            if (tot != null && !tot.isUnknown()) {
                double target = VideoPlayerUtils.calculateSeekFromClick(e.getX(), timeSlider.getWidth(), tot.toMillis());
                seekTooltip.setText(VideoPlayerUtils.formatTime(target));
            }
        });

        Label totalTimeLabel = new Label("00:00");
        totalTimeLabel.getStyleClass().addAll("label-time", "label-time-interactive");
        totalTimeLabel.setTooltip(new Tooltip("Click to toggle remaining time"));

        HBox timeline = new HBox(10, currentTimeLabel, timelineStack, totalTimeLabel);
        timeline.setAlignment(Pos.CENTER);

        final boolean[] isSeeking = new boolean[]{false};
        timeSlider.setOnMousePressed(e -> isSeeking[0] = true);
        timeSlider.setOnMouseReleased(e -> {
            isSeeking[0] = false;
            if (mediaPlayer.getTotalDuration() != null) {
                mediaPlayer.seek(Duration.millis(timeSlider.getValue()));
            }
        });
        timeSlider.setOnMouseClicked(e -> {
            Duration tot = mediaPlayer.getTotalDuration();
            if (tot != null && !tot.isUnknown()) {
                double target = VideoPlayerUtils.calculateSeekFromClick(e.getX(), timeSlider.getWidth(), tot.toMillis());
                timeSlider.setValue(target);
                mediaPlayer.seek(Duration.millis(target));
                currentTimeLabel.setText(VideoPlayerUtils.formatTime(target));
                timelineProgress.setProgress(VideoPlayerUtils.calculateProgress(target, tot.toMillis()));
            }
        });
        timeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (isSeeking[0] && newVal != null) {
                currentTimeLabel.setText(VideoPlayerUtils.formatTime(newVal.doubleValue()));
                Duration tot = mediaPlayer.getTotalDuration();
                if (tot != null && !tot.isUnknown()) {
                    timelineProgress.setProgress(VideoPlayerUtils.calculateProgress(newVal.doubleValue(), tot.toMillis()));
                }
            }
        });

        Runnable updateDurationLabel = () -> {
            Duration tot = mediaPlayer.getTotalDuration();
            Duration cur = mediaPlayer.getCurrentTime();
            if (tot != null && !tot.isUnknown()) {
                double totMs = tot.toMillis();
                double curMs = cur != null ? cur.toMillis() : 0.0;
                totalTimeLabel.setText(showRemainingTime
                        ? VideoPlayerUtils.formatRemainingTime(curMs, totMs)
                        : VideoPlayerUtils.formatTime(totMs));
            }
        };

        totalTimeLabel.setOnMouseClicked(e -> {
            showRemainingTime = !showRemainingTime;
            updateDurationLabel.run();
        });

        mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
            if (!isSeeking[0] && newTime != null) {
                currentTimeLabel.setText(VideoPlayerUtils.formatTime(newTime.toMillis()));
                if (mediaPlayer.getTotalDuration() != null && !mediaPlayer.getTotalDuration().isUnknown()) {
                    timeSlider.setValue(newTime.toMillis());
                    timelineProgress.setProgress(VideoPlayerUtils.calculateProgress(newTime.toMillis(), mediaPlayer.getTotalDuration().toMillis()));
                    if (showRemainingTime) {
                        updateDurationLabel.run();
                    }
                }
            }
        });

        mediaPlayer.totalDurationProperty().addListener((obs, oldDur, newDur) -> {
            if (newDur != null && !newDur.isUnknown()) {
                timeSlider.setMax(newDur.toMillis());
                updateDurationLabel.run();
            }
        });

        Runnable updateResolution = () -> {
            if (mediaPlayer.getMedia() != null) {
                int w = mediaPlayer.getMedia().getWidth();
                int h = mediaPlayer.getMedia().getHeight();
                String res = VideoPlayerUtils.formatResolutionBadge(w, h);
                if (!res.isEmpty()) {
                    resolutionBadge.setText(res);
                    resolutionBadge.setVisible(true);
                    resolutionBadge.setManaged(true);
                }
            }
        };
        mediaPlayer.setOnReady(updateResolution);

        // Playback buttons
        Button btnPlay = new Button("\u25B6 Play");
        btnPlay.getStyleClass().addAll("button", "primary-button");
        btnPlay.setTooltip(new Tooltip("Play video (Space)"));
        btnPlay.setOnAction(e -> {
            if (mediaPlayer.getStatus() == MediaPlayer.Status.STOPPED) {
                mediaPlayer.seek(Duration.ZERO);
            }
            mediaPlayer.play();
        });

        Button btnPause = new Button("\u23F8 Pause");
        btnPause.getStyleClass().add("button");
        btnPause.setTooltip(new Tooltip("Pause (Space)"));
        btnPause.setOnAction(e -> mediaPlayer.pause());

        Button btnStop = new Button("\u25A0 Stop");
        btnStop.getStyleClass().add("button");
        btnStop.setTooltip(new Tooltip("Stop playback"));
        btnStop.setOnAction(e -> {
            mediaPlayer.stop();
            timeSlider.setValue(0);
            timelineProgress.setProgress(0.0);
            currentTimeLabel.setText("00:00");
            updatePlayPauseButtonState(MediaPlayer.Status.STOPPED, btnPlay, btnPause);
        });

        mediaPlayer.statusProperty().addListener((obs, oldStatus, newStatus) -> {
            statusBadge.setText(VideoPlayerUtils.resolveStatusBadgeText(newStatus));
            statusBadge.getStyleClass().removeIf(s -> s.startsWith("status-badge-"));
            statusBadge.getStyleClass().add(VideoPlayerUtils.resolveStatusBadgeStyle(newStatus));

            updatePlayPauseButtonState(newStatus, btnPlay, btnPause);

            if (newStatus == MediaPlayer.Status.READY || newStatus == MediaPlayer.Status.PLAYING) {
                updateResolution.run();
                if (mediaPlayer.getTotalDuration() != null && !mediaPlayer.getTotalDuration().isUnknown()) {
                    timeSlider.setMax(mediaPlayer.getTotalDuration().toMillis());
                    updateDurationLabel.run();
                }
            } else if (newStatus == MediaPlayer.Status.HALTED) {
                titleLabel.setText("\u26A0\uFE0F Unsupported media codec or format");
            }
        });
        mediaPlayer.setOnError(() -> {
            String err = mediaPlayer.getError() != null ? mediaPlayer.getError().getMessage() : "Unknown media error";
            titleLabel.setText("\u26A0\uFE0F Error: " + err);
        });

        Button btnRewind = new Button("\u23EA");
        btnRewind.getStyleClass().addAll("button", "icon-button");
        btnRewind.setTooltip(new Tooltip("Rewind 5 seconds (\u2190)"));
        btnRewind.setOnAction(e -> seekRelative(-5.0, timeSlider, timelineProgress, currentTimeLabel));

        Button btnForward = new Button("\u23E9");
        btnForward.getStyleClass().addAll("button", "icon-button");
        btnForward.setTooltip(new Tooltip("Fast-forward 5 seconds (\u2192)"));
        btnForward.setOnAction(e -> seekRelative(5.0, timeSlider, timelineProgress, currentTimeLabel));

        ToggleButton btnLoop = new ToggleButton("\uD83D\uDD01 Loop");
        btnLoop.getStyleClass().add("toggle-button");
        btnLoop.setSelected(true);
        btnLoop.setTooltip(new Tooltip("Toggle continuous playback loop"));
        btnLoop.setOnAction(e -> mediaPlayer.setCycleCount(VideoPlayerUtils.resolveCycleCount(btnLoop.isSelected())));

        ComboBox<String> speedCombo = new ComboBox<>(FXCollections.observableArrayList(
                "0.25x", "0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x"));
        speedCombo.setValue("1.0x");
        speedCombo.getStyleClass().add("speed-combo-box");
        speedCombo.setTooltip(new Tooltip("Playback speed multiplier"));
        speedCombo.valueProperty().addListener((obs, oldVal, newVal) -> mediaPlayer.setRate(VideoPlayerUtils.parsePlaybackRate(newVal)));

        mediaPlayer.setOnEndOfMedia(() -> {
            if (!btnLoop.isSelected()) {
                mediaPlayer.stop();
                timeSlider.setValue(0);
                timelineProgress.setProgress(0.0);
                currentTimeLabel.setText("00:00");
                statusBadge.setText(VideoPlayerUtils.resolveStatusBadgeText(MediaPlayer.Status.STOPPED));
                statusBadge.getStyleClass().removeIf(s -> s.startsWith("status-badge-"));
                statusBadge.getStyleClass().add(VideoPlayerUtils.resolveStatusBadgeStyle(MediaPlayer.Status.STOPPED));
            }
        });

        // Volume controls with dynamic acoustic level glyphs and dual-layer progress bar
        Button btnMute = new Button(VideoPlayerUtils.resolveVolumeGlyph(0.8, false));
        btnMute.getStyleClass().addAll("button", "icon-button");
        btnMute.setTooltip(new Tooltip("Mute / Unmute (M)"));

        ProgressBar volumeProgress = new ProgressBar(0.8);
        volumeProgress.getStyleClass().add("volume-progress-bar");

        Slider volumeSlider = new Slider(0, 100, 80);
        volumeSlider.getStyleClass().add("volume-slider");
        volumeSlider.setTooltip(new Tooltip("Volume (\u2191/\u2193)"));
        mediaPlayer.setVolume(0.8);

        StackPane volumeSliderStack = new StackPane(volumeProgress, volumeSlider);
        volumeSliderStack.getStyleClass().add("volume-slider-stack");
        volumeSliderStack.setAlignment(Pos.CENTER);

        Label volumePercentLabel = new Label(VideoPlayerUtils.formatVolumePercent(0.8, false));
        volumePercentLabel.getStyleClass().add("label-volume");
        volumePercentLabel.setTooltip(new Tooltip("Current volume level"));

        btnMute.setOnAction(e -> toggleMute(btnMute, volumePercentLabel, volumeProgress));

        volumeSlider.setOnMouseClicked(e -> {
            double targetVol = VideoPlayerUtils.calculateVolumeFromClick(e.getX(), volumeSlider.getWidth());
            volumeSlider.setValue(targetVol * 100.0);
            mediaPlayer.setVolume(targetVol);
            if (targetVol > 0 && mediaPlayer.isMute()) {
                mediaPlayer.setMute(false);
            }
            volumeProgress.setProgress(mediaPlayer.isMute() ? 0.0 : targetVol);
            btnMute.setText(VideoPlayerUtils.resolveVolumeGlyph(targetVol, mediaPlayer.isMute()));
            volumePercentLabel.setText(VideoPlayerUtils.formatVolumePercent(targetVol, mediaPlayer.isMute()));
        });

        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double vol = VideoPlayerUtils.clampVolume(newVal.doubleValue() / 100.0);
            mediaPlayer.setVolume(vol);
            if (vol > 0 && mediaPlayer.isMute()) {
                mediaPlayer.setMute(false);
            } else if (vol == 0 && !mediaPlayer.isMute()) {
                mediaPlayer.setMute(true);
            }
            volumeProgress.setProgress(mediaPlayer.isMute() ? 0.0 : vol);
            btnMute.setText(VideoPlayerUtils.resolveVolumeGlyph(vol, mediaPlayer.isMute()));
            volumePercentLabel.setText(VideoPlayerUtils.formatVolumePercent(vol, mediaPlayer.isMute()));
        });

        HBox volumeBox = new HBox(6, btnMute, volumeSliderStack, volumePercentLabel);
        volumeBox.getStyleClass().add("volume-control-box");
        volumeBox.setAlignment(Pos.CENTER);

        Separator separatorVolume = new Separator(Orientation.VERTICAL);
        separatorVolume.getStyleClass().add("media-separator");

        // Fullscreen button
        Button btnFullscreen = new Button(VideoPlayerUtils.resolveFullscreenGlyph(false));
        btnFullscreen.getStyleClass().addAll("button", "icon-button", "fullscreen-button");
        btnFullscreen.setTooltip(new Tooltip(VideoPlayerUtils.resolveFullscreenTooltip(false)));
        btnFullscreen.setOnAction(e -> toggleFullscreen(btnFullscreen));

        Separator separatorClose = new Separator(Orientation.VERTICAL);
        separatorClose.getStyleClass().add("media-separator");

        Button btnClose = new Button("Close");
        btnClose.getStyleClass().addAll("button", "danger-button");
        btnClose.setTooltip(new Tooltip("Close dialog (Esc)"));
        btnClose.setOnAction(e -> closeHandler.run());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox controls = new HBox(5, btnPlay, btnPause, btnStop, btnRewind, btnForward, btnLoop, speedCombo,
                spacer, volumeBox, separatorVolume, btnFullscreen, separatorClose, btnClose);
        controls.setAlignment(Pos.CENTER_LEFT);

        VBox bottomContainer = new VBox(8, timeline, controls);
        bottomContainer.getStyleClass().add("media-controls-panel");
        root.setBottom(bottomContainer);

        Scene scene = new Scene(root, Color.BLACK);
        URL cssUrl = getClass().getResource("/css/video-architect.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        // Keyboard shortcuts delegated to specialized handler
        scene.setOnKeyPressed(new VideoPreviewShortcutHandler(
                mediaPlayer, btnMute, volumeSlider, volumePercentLabel, volumeProgress,
                btnFullscreen, timeSlider, timelineProgress, currentTimeLabel,
                () -> toggleFullscreen(btnFullscreen),
                () -> {
                    if (isFullscreen) {
                        toggleFullscreen(btnFullscreen);
                    } else {
                        closeHandler.run();
                    }
                }
        ));

        // Double click on video container toggles fullscreen
        videoContainer.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                toggleFullscreen(btnFullscreen);
            }
        });

        return scene;
    }

    private void seekRelative(double deltaSeconds, Slider timeSlider, ProgressBar timelineProgress, Label currentTimeLabel) {
        if (mediaPlayer != null) {
            double current = mediaPlayer.getCurrentTime() != null ? mediaPlayer.getCurrentTime().toMillis() : 0.0;
            double total = mediaPlayer.getTotalDuration() != null && !mediaPlayer.getTotalDuration().isUnknown()
                    ? mediaPlayer.getTotalDuration().toMillis() : 0.0;
            double target = VideoPlayerUtils.calculateSeekTarget(current, deltaSeconds, total);
            mediaPlayer.seek(Duration.millis(target));
            timeSlider.setValue(target);
            timelineProgress.setProgress(VideoPlayerUtils.calculateProgress(target, total));
            currentTimeLabel.setText(VideoPlayerUtils.formatTime(target));
        }
    }

    private void toggleMute(Button btnMute, Label volumePercentLabel, ProgressBar volumeProgress) {
        if (mediaPlayer != null) {
            boolean muted = !mediaPlayer.isMute();
            mediaPlayer.setMute(muted);
            double vol = mediaPlayer.getVolume();
            volumeProgress.setProgress(muted ? 0.0 : vol);
            btnMute.setText(VideoPlayerUtils.resolveVolumeGlyph(vol, muted));
            volumePercentLabel.setText(VideoPlayerUtils.formatVolumePercent(vol, muted));
        }
    }

    private void toggleFullscreen(Button btnFullscreen) {
        SwingUtilities.invokeLater(() -> {
            if (!isFullscreen) {
                normalBounds = dialog.getBounds();
                Rectangle maxBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
                dialog.setBounds(maxBounds);
                isFullscreen = true;
            } else {
                if (normalBounds != null) {
                    dialog.setBounds(normalBounds);
                } else {
                    dialog.setSize(720, 540);
                    dialog.setLocationRelativeTo(dialog.getOwner());
                }
                isFullscreen = false;
            }
            Platform.runLater(() -> {
                btnFullscreen.setText(VideoPlayerUtils.resolveFullscreenGlyph(isFullscreen));
                btnFullscreen.setTooltip(new Tooltip(VideoPlayerUtils.resolveFullscreenTooltip(isFullscreen)));
                if (isFullscreen && !btnFullscreen.getStyleClass().contains("fullscreen-active")) {
                    btnFullscreen.getStyleClass().add("fullscreen-active");
                } else if (!isFullscreen) {
                    btnFullscreen.getStyleClass().remove("fullscreen-active");
                }
            });
        });
    }

    private void updatePlayPauseButtonState(MediaPlayer.Status status, Button btnPlay, Button btnPause) {
        if (VideoPlayerUtils.isPausePrimaryAction(status)) {
            btnPlay.getStyleClass().remove("primary-button");
            if (!btnPause.getStyleClass().contains("primary-button")) {
                btnPause.getStyleClass().add("primary-button");
            }
        } else {
            btnPause.getStyleClass().remove("primary-button");
            if (!btnPlay.getStyleClass().contains("primary-button")) {
                btnPlay.getStyleClass().add("primary-button");
            }
        }
        btnPlay.setTooltip(new Tooltip(VideoPlayerUtils.resolvePlayButtonTooltip(status)));
        btnPause.setTooltip(new Tooltip(VideoPlayerUtils.resolvePauseButtonTooltip(status)));
    }
}
