package de.tki.comfymodels.ui;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.geometry.Pos;
import javafx.geometry.Insets;

import javax.swing.JDialog;
import javax.swing.JFrame;
import java.awt.Frame;
import javax.swing.WindowConstants;
import java.io.File;
import java.net.URI;

/**
 * Self-contained video preview dialog. Embeds a JavaFX MediaView inside a
 * JFXPanel so it runs alongside the rest of the Swing UI. The MediaPlayer
 * is fully released on close to avoid leaking native resources.
 */
public class VideoPreviewDialog extends JDialog {

    private MediaPlayer mediaPlayer;

    public VideoPreviewDialog(Frame owner, String title, File videoFile, boolean darkMode) {
        super(owner, "Preview: " + title, false);
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
                mediaView.setFitHeight(440);

                Label statusLabel = new Label("Loading preview...");
                statusLabel.setTextFill(darkMode ? Color.web("#e2e8f0") : Color.web("#1e293b"));
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
                Button btnPause = new Button("\u23F8 Pause");
                Button btnStop = new Button("\u25A0 Stop");
                btnPlay.setOnAction(e -> mediaPlayer.play());
                btnPause.setOnAction(e -> mediaPlayer.pause());
                btnStop.setOnAction(e -> mediaPlayer.stop());

                Button btnClose = new Button("Close");
                btnClose.setOnAction(e -> {
                    stopPlayer();
                    dispose();
                });

                HBox controls = new HBox(8,
                        btnPlay, btnPause, btnStop,
                        new javafx.scene.layout.Region(),
                        btnClose);
                controls.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(controls.getChildren().get(3), javafx.scene.layout.Priority.ALWAYS);
                controls.setPadding(new Insets(8, 8, 8, 8));
                controls.setStyle(darkMode
                        ? "-fx-background-color: #1a1d27;"
                        : "-fx-background-color: #e5e7eb;");

                BorderPane root = new BorderPane();
                root.setCenter(mediaView);
                root.setBottom(controls);
                root.setTop(statusLabel);
                BorderPane.setMargin(statusLabel, new Insets(6, 8, 0, 8));
                root.setStyle(darkMode
                        ? "-fx-background-color: #0f1117;"
                        : "-fx-background-color: #f8fafc;");

                Scene scene = new Scene(root, Color.BLACK);
                fxPanel.setScene(scene);
            } catch (Exception ex) {
                javafx.scene.control.Label error = new javafx.scene.control.Label(
                        "Could not open video preview: " + ex.getMessage());
                error.setTextFill(Color.RED);
                BorderPane errRoot = new BorderPane(error);
                errRoot.setStyle("-fx-padding: 20; -fx-background-color: #1a1d27;");
                fxPanel.setScene(new Scene(errRoot));
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
                    }
                    try {
                        mediaPlayer.dispose();
                    } catch (Exception ignored) {
                    }
                });
            } catch (Exception ignored) {
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
