package de.tki.comfyuicompanion.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import javafx.geometry.Pos;
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
            public void windowClosing(java.awt.event.WindowEvent e) {
                stopPlayer();
            }
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                stopPlayer();
            }
        });

        Platform.runLater(() -> {
            try {
                URI uri = videoFile.toURI();
                Media media = new Media(uri.toString());
                mediaPlayer = new MediaPlayer(media);
                mediaPlayer.setAutoPlay(true);
                mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);

                MediaView mediaView = new MediaView(mediaPlayer);
                mediaView.setPreserveRatio(true);
                mediaView.setFitWidth(680);
                mediaView.setFitHeight(420);

                Label statusLabel = new Label("Loading preview...");
                statusLabel.getStyleClass().add("label-title");

                HBox header = new HBox(statusLabel);
                header.getStyleClass().add("header-panel");
                header.setAlignment(Pos.CENTER_LEFT);

                Label currentTimeLabel = new Label("00:00");
                currentTimeLabel.getStyleClass().add("label-time");

                Slider timeSlider = new Slider(0, 100, 0);
                timeSlider.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(timeSlider, Priority.ALWAYS);

                Label totalTimeLabel = new Label("00:00");
                totalTimeLabel.getStyleClass().add("label-time");

                HBox timeline = new HBox(10, currentTimeLabel, timeSlider, totalTimeLabel);
                timeline.setAlignment(Pos.CENTER);

                final boolean[] isSeeking = new boolean[]{false};
                timeSlider.setOnMousePressed(e -> isSeeking[0] = true);
                timeSlider.setOnMouseReleased(e -> {
                    isSeeking[0] = false;
                    if (mediaPlayer != null && mediaPlayer.getTotalDuration() != null) {
                        mediaPlayer.seek(Duration.millis(timeSlider.getValue()));
                    }
                });

                mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                    if (!isSeeking[0] && newTime != null) {
                        currentTimeLabel.setText(formatTime(newTime.toMillis()));
                        if (mediaPlayer.getTotalDuration() != null && !mediaPlayer.getTotalDuration().isUnknown()) {
                            timeSlider.setValue(newTime.toMillis());
                        }
                    }
                });

                mediaPlayer.totalDurationProperty().addListener((obs, oldDur, newDur) -> {
                    if (newDur != null && !newDur.isUnknown()) {
                        timeSlider.setMax(newDur.toMillis());
                        totalTimeLabel.setText(formatTime(newDur.toMillis()));
                    }
                });

                mediaPlayer.statusProperty().addListener((obs, oldStatus, newStatus) -> {
                    javafx.scene.media.MediaPlayer.Status st = newStatus;
                    if (st == javafx.scene.media.MediaPlayer.Status.READY
                            || st == javafx.scene.media.MediaPlayer.Status.PLAYING) {
                        statusLabel.setText("Playing: " + videoFile.getName());
                        if (mediaPlayer.getTotalDuration() != null && !mediaPlayer.getTotalDuration().isUnknown()) {
                            timeSlider.setMax(mediaPlayer.getTotalDuration().toMillis());
                            totalTimeLabel.setText(formatTime(mediaPlayer.getTotalDuration().toMillis()));
                        }
                    } else if (st == javafx.scene.media.MediaPlayer.Status.PAUSED) {
                        statusLabel.setText("Paused: " + videoFile.getName());
                    } else if (st == javafx.scene.media.MediaPlayer.Status.STALLED) {
                        statusLabel.setText("Buffering...");
                    } else if (st == javafx.scene.media.MediaPlayer.Status.STOPPED) {
                        statusLabel.setText("Stopped");
                    } else if (st == javafx.scene.media.MediaPlayer.Status.HALTED) {
                        statusLabel.setText("Cannot play this media (codec missing or format unsupported).");
                    }
                });
                mediaPlayer.setOnError(() -> {
                    String err = mediaPlayer.getError() != null
                        ? mediaPlayer.getError().getMessage() : "Unknown media error";
                    statusLabel.setText("Preview error: " + err);
                });

                Button btnPlay = new Button("\u25B6 Play");
                btnPlay.getStyleClass().addAll("button", "primary-button");

                Button btnPause = new Button("\u23F8 Pause");
                btnPause.getStyleClass().add("button");

                Button btnStop = new Button("\u25A0 Stop");
                btnStop.getStyleClass().add("button");

                ToggleButton btnLoop = new ToggleButton("\uD83D\uDD01 Loop");
                btnLoop.getStyleClass().add("toggle-button");
                btnLoop.setSelected(true);
                btnLoop.setTooltip(new Tooltip("Toggle continuous playback loop"));
                btnLoop.setOnAction(e -> {
                    if (mediaPlayer != null) {
                        mediaPlayer.setCycleCount(resolveCycleCount(btnLoop.isSelected()));
                    }
                });

                ComboBox<String> speedCombo = new ComboBox<>(FXCollections.observableArrayList(
                        "0.25x", "0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x"));
                speedCombo.setValue("1.0x");
                speedCombo.getStyleClass().add("speed-combo-box");
                speedCombo.setTooltip(new Tooltip("Playback speed multiplier"));
                speedCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
                    if (mediaPlayer != null) {
                        mediaPlayer.setRate(parsePlaybackRate(newVal));
                    }
                });

                mediaPlayer.setOnEndOfMedia(() -> {
                    if (!btnLoop.isSelected()) {
                        mediaPlayer.stop();
                        timeSlider.setValue(0);
                        currentTimeLabel.setText("00:00");
                        statusLabel.setText("Finished: " + videoFile.getName());
                    }
                });

                btnPlay.setOnAction(e -> {
                    if (mediaPlayer != null) {
                        if (mediaPlayer.getStatus() == javafx.scene.media.MediaPlayer.Status.STOPPED) {
                            mediaPlayer.seek(Duration.ZERO);
                        }
                        mediaPlayer.play();
                    }
                });
                btnPause.setOnAction(e -> mediaPlayer.pause());
                btnStop.setOnAction(e -> {
                    mediaPlayer.stop();
                    timeSlider.setValue(0);
                    currentTimeLabel.setText("00:00");
                });

                Button btnClose = new Button("Close");
                btnClose.getStyleClass().addAll("button", "danger-button");
                btnClose.setOnAction(e -> {
                    stopPlayer();
                    dispose();
                });

                // Volume and mute controls
                Button btnMute = new Button("\uD83D\uDD0A");
                btnMute.getStyleClass().addAll("button", "icon-button");
                btnMute.setTooltip(new Tooltip("Mute / Unmute"));

                Slider volumeSlider = new Slider(0, 100, 80);
                volumeSlider.getStyleClass().add("volume-slider");
                volumeSlider.setTooltip(new Tooltip("Volume"));
                mediaPlayer.setVolume(0.8);

                btnMute.setOnAction(e -> {
                    if (mediaPlayer != null) {
                        boolean muted = !mediaPlayer.isMute();
                        mediaPlayer.setMute(muted);
                        btnMute.setText(muted ? "\uD83D\uDD07" : "\uD83D\uDD0A");
                    }
                });

                volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                    if (mediaPlayer != null) {
                        double vol = clampVolume(newVal.doubleValue() / 100.0);
                        mediaPlayer.setVolume(vol);
                        if (vol > 0 && mediaPlayer.isMute()) {
                            mediaPlayer.setMute(false);
                            btnMute.setText("\uD83D\uDD0A");
                        } else if (vol == 0 && !mediaPlayer.isMute()) {
                            mediaPlayer.setMute(true);
                            btnMute.setText("\uD83D\uDD07");
                        }
                    }
                });

                HBox volumeBox = new HBox(6, btnMute, volumeSlider);
                volumeBox.getStyleClass().add("volume-control-box");
                volumeBox.setAlignment(Pos.CENTER);

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                HBox controls = new HBox(8, btnPlay, btnPause, btnStop, btnLoop, speedCombo, spacer, volumeBox, btnClose);
                controls.setAlignment(Pos.CENTER_LEFT);

                VBox bottomContainer = new VBox(8, timeline, controls);
                bottomContainer.getStyleClass().add("media-controls-panel");

                StackPane videoContainer = new StackPane(mediaView);
                videoContainer.getStyleClass().add("video-viewport");

                BorderPane root = new BorderPane();
                root.getStyleClass().add("root");
                if (!darkMode) {
                    root.getStyleClass().add("light-theme");
                }
                root.setTop(header);
                root.setCenter(videoContainer);
                root.setBottom(bottomContainer);

                Scene scene = new Scene(root, Color.BLACK);
                URL cssUrl = getClass().getResource("/css/video-architect.css");
                if (cssUrl != null) {
                    scene.getStylesheets().add(cssUrl.toExternalForm());
                }

                fxPanel.setScene(scene);

                SwingUtilities.invokeLater(() -> {
                    toFront();
                    requestFocus();
                });
            } catch (Exception ex) {
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
                    try {
                        mediaPlayer.stop();
                    } catch (Exception ignored) {
                        logger.debug("Ignored non-fatal exception: {}", ignored.getMessage());
                    }
                    try {
                        mediaPlayer.dispose();
                    } catch (Exception ignored) {
                        logger.debug("Ignored non-fatal exception: {}", ignored.getMessage());
                    }
                });
            } catch (Exception ignored) {
                logger.debug("Ignored non-fatal exception: {}", ignored.getMessage());
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
        if (Double.isNaN(millis) || millis < 0) {
            return "00:00";
        }
        long totalSeconds = (long) Math.floor(millis / 1000.0);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * Clamps a media volume level within the valid range [0.0, 1.0].
     *
     * @param volume raw volume level
     * @return clamped volume between 0.0 and 1.0
     */
    static double clampVolume(double volume) {
        if (Double.isNaN(volume) || volume <= 0.0) {
            return 0.0;
        }
        return Math.min(1.0, volume);
    }

    /**
     * Resolves the JavaFX MediaPlayer cycle count based on loop toggle state.
     *
     * @param loopEnabled whether continuous looping is active
     * @return {@link MediaPlayer#INDEFINITE} if true, else 1
     */
    static int resolveCycleCount(boolean loopEnabled) {
        return loopEnabled ? MediaPlayer.INDEFINITE : 1;
    }

    /**
     * Parses a playback rate string (e.g. "0.5x", "1.0x", "2.0x") into a valid playback speed factor.
     * Guaranteed to return a safe positive multiplier clamped between 0.1 and 8.0, defaulting to 1.0.
     *
     * @param rateLabel textual representation of playback speed
     * @return validated double rate multiplier
     */
    static double parsePlaybackRate(String rateLabel) {
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

    @Override
    public void dispose() {
        stopPlayer();
        super.dispose();
    }
}
