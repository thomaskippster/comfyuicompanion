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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
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

                MediaView mediaView = new MediaView(mediaPlayer);
                mediaView.setPreserveRatio(true);
                mediaView.setFitWidth(680);
                mediaView.setFitHeight(420);

                Label statusLabel = new Label("Loading preview...");
                statusLabel.getStyleClass().add("label-title");

                HBox header = new HBox(statusLabel);
                header.getStyleClass().add("header-panel");
                header.setAlignment(Pos.CENTER_LEFT);

                ProgressBar progressBar = new ProgressBar(0);
                progressBar.setMaxWidth(Double.MAX_VALUE);
                progressBar.getStyleClass().add("progress-bar");

                mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                    if (mediaPlayer.getTotalDuration() != null && !mediaPlayer.getTotalDuration().isUnknown()) {
                        double progress = newTime.toMillis() / mediaPlayer.getTotalDuration().toMillis();
                        progressBar.setProgress(progress);
                    }
                });

                mediaPlayer.statusProperty().addListener((obs, oldStatus, newStatus) -> {
                    javafx.scene.media.MediaPlayer.Status st = newStatus;
                    if (st == javafx.scene.media.MediaPlayer.Status.READY
                            || st == javafx.scene.media.MediaPlayer.Status.PLAYING) {
                        statusLabel.setText("Playing: " + videoFile.getName());
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

                btnPlay.setOnAction(e -> mediaPlayer.play());
                btnPause.setOnAction(e -> mediaPlayer.pause());
                btnStop.setOnAction(e -> mediaPlayer.stop());

                Button btnClose = new Button("Close");
                btnClose.getStyleClass().addAll("button", "danger-button");
                btnClose.setOnAction(e -> {
                    stopPlayer();
                    dispose();
                });

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                HBox controls = new HBox(8, btnPlay, btnPause, btnStop, spacer, btnClose);
                controls.setAlignment(Pos.CENTER_LEFT);
                controls.getStyleClass().add("button-bar");

                VBox bottomContainer = new VBox(progressBar, controls);

                BorderPane root = new BorderPane();
                root.getStyleClass().add("root");
                if (!darkMode) {
                    root.getStyleClass().add("light-theme");
                }
                root.setTop(header);
                root.setCenter(mediaView);
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

    @Override
    public void dispose() {
        stopPlayer();
        super.dispose();
    }
}
