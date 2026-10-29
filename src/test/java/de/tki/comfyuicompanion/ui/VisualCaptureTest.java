package de.tki.comfyuicompanion.ui;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
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

                Label titleLabel = new Label("\uD83C\uDFAC companion_for_comfyui_explainer_video.mp4");
                titleLabel.getStyleClass().add("label-title");

                Region headerSpacer = new Region();
                HBox.setHgrow(headerSpacer, Priority.ALWAYS);

                Label resolutionBadge = new Label("1920\u00D71080");
                resolutionBadge.getStyleClass().addAll("badge-pill", "badge-resolution");

                Label formatBadge = new Label("MP4");
                formatBadge.getStyleClass().addAll("badge-pill", "badge-format");

                Label statusBadge = new Label("\u25CF PLAYING");
                statusBadge.getStyleClass().addAll("badge-pill", "status-badge", "status-badge-playing");

                HBox headerBadges = new HBox(8, resolutionBadge, formatBadge, statusBadge);
                headerBadges.setAlignment(Pos.CENTER_RIGHT);

                HBox headerBox = new HBox(12, titleLabel, headerSpacer, headerBadges);
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

                // Playback timeline with dual-layer progress bar, time labels, and interactive slider
                Label currentTimeLabel = new Label("00:15");
                currentTimeLabel.getStyleClass().add("label-time");

                ProgressBar timelineProgress = new ProgressBar(0.35);
                timelineProgress.getStyleClass().add("timeline-progress-bar");
                timelineProgress.setMaxWidth(Double.MAX_VALUE);

                Slider timeSlider = new Slider(0, 100, 35);
                timeSlider.getStyleClass().add("timeline-slider");
                timeSlider.setMaxWidth(Double.MAX_VALUE);

                StackPane timelineStack = new StackPane(timelineProgress, timeSlider);
                timelineStack.setAlignment(Pos.CENTER);
                HBox.setHgrow(timelineStack, Priority.ALWAYS);

                Label totalTimeLabel = new Label("00:43");
                totalTimeLabel.getStyleClass().addAll("label-time", "label-time-interactive");

                HBox timeline = new HBox(10, currentTimeLabel, timelineStack, totalTimeLabel);
                timeline.setAlignment(Pos.CENTER);

                // Playback control buttons
                Button btnPlay = new Button("\u25B6 Play");
                btnPlay.getStyleClass().addAll("button", "primary-button");

                Button btnPause = new Button("\u23F8 Pause");
                btnPause.getStyleClass().add("button");

                Button btnStop = new Button("\u25A0 Stop");
                btnStop.getStyleClass().add("button");

                Button btnRewind = new Button("\u23EA");
                btnRewind.getStyleClass().addAll("button", "icon-button");

                Button btnForward = new Button("\u23E9");
                btnForward.getStyleClass().addAll("button", "icon-button");

                ToggleButton btnLoop = new ToggleButton("\uD83D\uDD01 Loop");
                btnLoop.getStyleClass().add("toggle-button");
                btnLoop.setSelected(true);

                ComboBox<String> speedCombo = new ComboBox<>(FXCollections.observableArrayList(
                        "0.25x", "0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x"));
                speedCombo.setValue("1.0x");
                speedCombo.getStyleClass().add("speed-combo-box");

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                // Volume, mute controls, and percentage label with dual-layer progress bar
                Button btnMute = new Button(VideoPlayerUtils.resolveVolumeGlyph(0.8, false));
                btnMute.getStyleClass().addAll("button", "icon-button");

                ProgressBar volumeProgress = new ProgressBar(0.8);
                volumeProgress.getStyleClass().add("volume-progress-bar");

                Slider volumeSlider = new Slider(0, 100, 80);
                volumeSlider.getStyleClass().add("volume-slider");

                StackPane volumeSliderStack = new StackPane(volumeProgress, volumeSlider);
                volumeSliderStack.getStyleClass().add("volume-slider-stack");
                volumeSliderStack.setAlignment(Pos.CENTER);

                Label volumePercentLabel = new Label("80%");
                volumePercentLabel.getStyleClass().add("label-volume");

                HBox volumeBox = new HBox(6, btnMute, volumeSliderStack, volumePercentLabel);
                volumeBox.getStyleClass().add("volume-control-box");
                volumeBox.setAlignment(Pos.CENTER);

                Button btnFullscreen = new Button("\u26F6");
                btnFullscreen.getStyleClass().addAll("button", "icon-button", "fullscreen-button");

                Separator separator = new Separator(javafx.geometry.Orientation.VERTICAL);
                separator.getStyleClass().add("media-separator");

                Button btnClose = new Button("Close");
                btnClose.getStyleClass().addAll("button", "danger-button");

                HBox controls = new HBox(6, btnPlay, btnPause, btnStop, btnRewind, btnForward, btnLoop, speedCombo, spacer, volumeBox, btnFullscreen, separator, btnClose);
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
}
