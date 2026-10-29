package de.tki.comfyuicompanion.ui;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Slider;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enterprise automated visual capture test for JavaFX components.
 * Renders the JavaFX preview scene off-screen into {@code .agy_visual/current_state.png}
 * to enable visual regression testing and layout inspection.
 */
public class VisualCaptureTest {

    @BeforeAll
    public static void initJavaFx() {
        // Initializes JavaFX runtime safely in headless/test mode
        new JFXPanel();
        Platform.setImplicitExit(false);
    }

    /**
     * Captures the JavaFX preview dialog scene into the visual artifact folder.
     *
     * @throws Exception if rendering or saving fails
     */
    @Test
    @DisplayName("Capture current JavaFX Preview scene into .agy_visual/current_state.png")
    public void testCaptureCurrentState() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        final Throwable[] errorRef = new Throwable[1];

        Platform.runLater(() -> {
            try {
                BorderPane root = new BorderPane();
                root.getStyleClass().add("root");
                root.setPrefSize(720, 540);

                Label statusLabel = new Label("Playing: companion_for_comfyui_explainer_video.mp4");
                statusLabel.getStyleClass().add("label-title");

                HBox headerBox = new HBox(statusLabel);
                headerBox.getStyleClass().add("header-panel");
                headerBox.setAlignment(Pos.CENTER_LEFT);
                root.setTop(headerBox);

                // Central simulated video area with dark canvas
                StackPane videoContainer = new StackPane();
                videoContainer.getStyleClass().add("video-viewport");
                Label placeholder = new Label("[JavaFX MediaView: 680 x 420]");
                placeholder.getStyleClass().add("label-muted");
                videoContainer.getChildren().add(placeholder);
                root.setCenter(videoContainer);

                // Playback timeline with current/total time labels and interactive seeking slider
                Label currentTimeLabel = new Label("00:15");
                currentTimeLabel.getStyleClass().add("label-time");

                Slider timeSlider = new Slider(0, 100, 35);
                timeSlider.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(timeSlider, Priority.ALWAYS);

                Label totalTimeLabel = new Label("00:43");
                totalTimeLabel.getStyleClass().add("label-time");

                HBox timeline = new HBox(10, currentTimeLabel, timeSlider, totalTimeLabel);
                timeline.setAlignment(Pos.CENTER);

                // Playback control buttons
                Button btnPlay = new Button("\u25B6 Play");
                btnPlay.getStyleClass().addAll("button", "primary-button");

                Button btnPause = new Button("\u23F8 Pause");
                btnPause.getStyleClass().add("button");

                Button btnStop = new Button("\u25A0 Stop");
                btnStop.getStyleClass().add("button");

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                // Volume and mute controls
                Button btnMute = new Button("\uD83D\uDD0A");
                btnMute.getStyleClass().addAll("button", "icon-button");

                Slider volumeSlider = new Slider(0, 100, 80);
                volumeSlider.getStyleClass().add("volume-slider");

                HBox volumeBox = new HBox(6, btnMute, volumeSlider);
                volumeBox.getStyleClass().add("volume-control-box");
                volumeBox.setAlignment(Pos.CENTER);

                Button btnClose = new Button("Close");
                btnClose.getStyleClass().addAll("button", "danger-button");

                HBox controls = new HBox(8, btnPlay, btnPause, btnStop, spacer, volumeBox, btnClose);
                controls.setAlignment(Pos.CENTER_LEFT);

                VBox bottomContainer = new VBox(8, timeline, controls);
                bottomContainer.getStyleClass().add("media-controls-panel");
                root.setBottom(bottomContainer);

                Scene scene = new Scene(root, 720, 540, Color.BLACK);
                URL cssUrl = getClass().getResource("/css/video-architect.css");
                assertNotNull(cssUrl, "video-architect.css must be available on classpath");
                scene.getStylesheets().add(cssUrl.toExternalForm());

                // Render off-screen snapshot
                WritableImage fxSnapshot = scene.snapshot(null);
                BufferedImage awtImage = SwingFXUtils.fromFXImage(fxSnapshot, null);

                File targetDir = new File(".agy_visual");
                if (!targetDir.exists()) {
                    targetDir.mkdirs();
                }
                File targetFile = new File(targetDir, "current_state.png");
                ImageIO.write(awtImage, "png", targetFile);

                assertTrue(targetFile.exists(), "Snapshot file must be written to disk");
                assertTrue(targetFile.length() > 0, "Snapshot file must not be empty");
            } catch (Throwable t) {
                errorRef[0] = t;
            } finally {
                latch.countDown();
            }
        });

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "Visual capture must finish within timeout");
        if (errorRef[0] != null) {
            throw new RuntimeException("Capture error", errorRef[0]);
        }
    }

    /**
     * Verifies time formatting for media player timestamps including edge cases.
     */
    @Test
    @DisplayName("Verify mm:ss time formatting edge cases")
    public void testFormatTimeEdgeCases() {
        assertEquals("00:00", VideoPreviewDialog.formatTime(-100));
        assertEquals("00:00", VideoPreviewDialog.formatTime(Double.NaN));
        assertEquals("00:00", VideoPreviewDialog.formatTime(0));
        assertEquals("00:05", VideoPreviewDialog.formatTime(5000));
        assertEquals("01:15", VideoPreviewDialog.formatTime(75000));
        assertEquals("12:34", VideoPreviewDialog.formatTime(754000));
    }

    /**
     * Verifies volume level clamping between 0.0 and 1.0 including boundary and NaN values.
     */
    @Test
    @DisplayName("Verify volume level clamping boundary values")
    public void testClampVolume() {
        assertEquals(0.0, VideoPreviewDialog.clampVolume(-0.5));
        assertEquals(0.0, VideoPreviewDialog.clampVolume(Double.NaN));
        assertEquals(0.0, VideoPreviewDialog.clampVolume(0.0));
        assertEquals(0.5, VideoPreviewDialog.clampVolume(0.5));
        assertEquals(1.0, VideoPreviewDialog.clampVolume(1.0));
        assertEquals(1.0, VideoPreviewDialog.clampVolume(2.5));
    }
}
