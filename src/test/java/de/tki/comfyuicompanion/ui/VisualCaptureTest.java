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

                Label formatBadge = new Label("MP4");
                formatBadge.getStyleClass().addAll("badge-pill", "badge-format");

                Label statusBadge = new Label("\u25CF PLAYING");
                statusBadge.getStyleClass().addAll("badge-pill", "status-badge", "status-badge-playing");

                HBox headerBadges = new HBox(8, formatBadge, statusBadge);
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

    /**
     * Verifies cycle count resolution between continuous loop and single-play modes.
     */
    @Test
    @DisplayName("Verify cycle count resolution for loop playback")
    public void testResolveCycleCount() {
        assertEquals(javafx.scene.media.MediaPlayer.INDEFINITE, VideoPreviewDialog.resolveCycleCount(true));
        assertEquals(1, VideoPreviewDialog.resolveCycleCount(false));
    }

    /**
     * Verifies playback rate parsing including boundary, null, malformed, and suffix inputs.
     */
    @Test
    @DisplayName("Verify playback rate parsing with boundary and fallback values")
    public void testParsePlaybackRate() {
        assertEquals(1.0, VideoPreviewDialog.parsePlaybackRate(null));
        assertEquals(1.0, VideoPreviewDialog.parsePlaybackRate(""));
        assertEquals(1.0, VideoPreviewDialog.parsePlaybackRate("   "));
        assertEquals(1.0, VideoPreviewDialog.parsePlaybackRate("invalid"));
        assertEquals(1.0, VideoPreviewDialog.parsePlaybackRate("-0.5x"));
        assertEquals(1.0, VideoPreviewDialog.parsePlaybackRate("0.0x"));
        assertEquals(0.25, VideoPreviewDialog.parsePlaybackRate("0.25x"));
        assertEquals(0.5, VideoPreviewDialog.parsePlaybackRate("0.5x"));
        assertEquals(0.75, VideoPreviewDialog.parsePlaybackRate("0.75X"));
        assertEquals(1.0, VideoPreviewDialog.parsePlaybackRate("1.0"));
        assertEquals(1.0, VideoPreviewDialog.parsePlaybackRate("1.0x"));
        assertEquals(1.5, VideoPreviewDialog.parsePlaybackRate("1.5x"));
        assertEquals(2.0, VideoPreviewDialog.parsePlaybackRate("2.0x"));
        assertEquals(8.0, VideoPreviewDialog.parsePlaybackRate("10.0x"));
        assertEquals(0.1, VideoPreviewDialog.parsePlaybackRate("0.05x"));
    }

    /**
     * Verifies seek destination calculation with positive/negative offsets, zero bounds, and total duration clamping.
     */
    @Test
    @DisplayName("Verify seek target calculation with deltas, boundaries, and clamping")
    public void testCalculateSeekTarget() {
        // Rewind beyond 0 returns 0.0
        assertEquals(0.0, VideoPreviewDialog.calculateSeekTarget(2000.0, -5.0, 30000.0));
        assertEquals(0.0, VideoPreviewDialog.calculateSeekTarget(0.0, -5.0, 30000.0));
        assertEquals(0.0, VideoPreviewDialog.calculateSeekTarget(-1000.0, -5.0, 30000.0));
        assertEquals(0.0, VideoPreviewDialog.calculateSeekTarget(Double.NaN, -5.0, 30000.0));

        // Normal rewind
        assertEquals(10000.0, VideoPreviewDialog.calculateSeekTarget(15000.0, -5.0, 30000.0));

        // Normal fast-forward
        assertEquals(20000.0, VideoPreviewDialog.calculateSeekTarget(15000.0, 5.0, 30000.0));

        // Fast-forward clamped to total duration
        assertEquals(30000.0, VideoPreviewDialog.calculateSeekTarget(28000.0, 5.0, 30000.0));
        assertEquals(30000.0, VideoPreviewDialog.calculateSeekTarget(30000.0, 5.0, 30000.0));

        // Unknown / 0 total duration allows forward seeking without upper bound clamping
        assertEquals(20000.0, VideoPreviewDialog.calculateSeekTarget(15000.0, 5.0, 0.0));
        assertEquals(20000.0, VideoPreviewDialog.calculateSeekTarget(15000.0, 5.0, Double.NaN));

        // Neutral offset returns current position
        assertEquals(12000.0, VideoPreviewDialog.calculateSeekTarget(12000.0, 0.0, 30000.0));
        assertEquals(12000.0, VideoPreviewDialog.calculateSeekTarget(12000.0, Double.NaN, 30000.0));
    }

    /**
     * Verifies volume percentage formatting with boundary, negative, NaN, clamping, and mute states.
     */
    @Test
    @DisplayName("Verify volume percentage string formatting and mute overrides")
    public void testFormatVolumePercent() {
        assertEquals("0%", VideoPreviewDialog.formatVolumePercent(0.8, true));
        assertEquals("0%", VideoPreviewDialog.formatVolumePercent(0.0, false));
        assertEquals("0%", VideoPreviewDialog.formatVolumePercent(-0.2, false));
        assertEquals("0%", VideoPreviewDialog.formatVolumePercent(Double.NaN, false));
        assertEquals("50%", VideoPreviewDialog.formatVolumePercent(0.5, false));
        assertEquals("80%", VideoPreviewDialog.formatVolumePercent(0.8, false));
        assertEquals("100%", VideoPreviewDialog.formatVolumePercent(1.0, false));
        assertEquals("100%", VideoPreviewDialog.formatVolumePercent(1.5, false));

        // Also verify direct utility class equivalence
        assertEquals("80%", VideoPlayerUtils.formatVolumePercent(0.8, false));
        assertEquals("0%", VideoPlayerUtils.formatVolumePercent(0.8, true));
    }

    /**
     * Verifies file format badge extraction from filenames including edge cases.
     */
    @Test
    @DisplayName("Verify file format badge extraction from various filename extensions")
    public void testExtractFormatBadge() {
        assertEquals("VIDEO", VideoPreviewDialog.extractFormatBadge(null));
        assertEquals("VIDEO", VideoPreviewDialog.extractFormatBadge(""));
        assertEquals("VIDEO", VideoPreviewDialog.extractFormatBadge("   "));
        assertEquals("VIDEO", VideoPreviewDialog.extractFormatBadge("no_extension"));
        assertEquals("VIDEO", VideoPreviewDialog.extractFormatBadge("trailing_dot."));
        assertEquals("MP4", VideoPreviewDialog.extractFormatBadge("output.mp4"));
        assertEquals("WEBM", VideoPreviewDialog.extractFormatBadge("test_video.webm"));
        assertEquals("MKV", VideoPreviewDialog.extractFormatBadge("sample.MKV"));
        assertEquals("GIF", VideoPreviewDialog.extractFormatBadge("animation.gif"));
        assertEquals("CUSTOM", VideoPreviewDialog.extractFormatBadge("file.customlongextension"));

        // Direct utility equivalence
        assertEquals("MP4", VideoPlayerUtils.extractFormatBadge("my_render.mp4"));
        assertEquals("VIDEO", VideoPlayerUtils.extractFormatBadge(null));
    }

    /**
     * Verifies status badge text resolution for all MediaPlayer status states.
     */
    @Test
    @DisplayName("Verify status badge text resolution with glyphs")
    public void testResolveStatusBadgeText() {
        assertEquals("\u25CF READY", VideoPreviewDialog.resolveStatusBadgeText(null));
        assertEquals("\u25CF READY", VideoPreviewDialog.resolveStatusBadgeText(javafx.scene.media.MediaPlayer.Status.READY));
        assertEquals("\u25CF PLAYING", VideoPreviewDialog.resolveStatusBadgeText(javafx.scene.media.MediaPlayer.Status.PLAYING));
        assertEquals("\u23F8 PAUSED", VideoPreviewDialog.resolveStatusBadgeText(javafx.scene.media.MediaPlayer.Status.PAUSED));
        assertEquals("\u25A0 STOPPED", VideoPreviewDialog.resolveStatusBadgeText(javafx.scene.media.MediaPlayer.Status.STOPPED));
        assertEquals("\u23F3 BUFFERING", VideoPreviewDialog.resolveStatusBadgeText(javafx.scene.media.MediaPlayer.Status.STALLED));

        // Direct utility equivalence
        assertEquals("\u25CF PLAYING", VideoPlayerUtils.resolveStatusBadgeText(javafx.scene.media.MediaPlayer.Status.PLAYING));
    }

    /**
     * Verifies status badge CSS style class mapping for all MediaPlayer status states.
     */
    @Test
    @DisplayName("Verify status badge CSS style class mapping")
    public void testResolveStatusBadgeStyle() {
        assertEquals("status-badge-ready", VideoPreviewDialog.resolveStatusBadgeStyle(null));
        assertEquals("status-badge-ready", VideoPreviewDialog.resolveStatusBadgeStyle(javafx.scene.media.MediaPlayer.Status.READY));
        assertEquals("status-badge-playing", VideoPreviewDialog.resolveStatusBadgeStyle(javafx.scene.media.MediaPlayer.Status.PLAYING));
        assertEquals("status-badge-paused", VideoPreviewDialog.resolveStatusBadgeStyle(javafx.scene.media.MediaPlayer.Status.PAUSED));
        assertEquals("status-badge-stopped", VideoPreviewDialog.resolveStatusBadgeStyle(javafx.scene.media.MediaPlayer.Status.STOPPED));
        assertEquals("status-badge-buffering", VideoPreviewDialog.resolveStatusBadgeStyle(javafx.scene.media.MediaPlayer.Status.STALLED));

        // Direct utility equivalence
        assertEquals("status-badge-playing", VideoPlayerUtils.resolveStatusBadgeStyle(javafx.scene.media.MediaPlayer.Status.PLAYING));
    }

    /**
     * Verifies remaining time formatting including negative offsets, overruns, zero bounds, and standard countdowns.
     */
    @Test
    @DisplayName("Verify mm:ss remaining time countdown formatting edge cases")
    public void testFormatRemainingTimeEdgeCases() {
        assertEquals("-00:00", VideoPreviewDialog.formatRemainingTime(Double.NaN, 30000.0));
        assertEquals("-00:00", VideoPreviewDialog.formatRemainingTime(10000.0, Double.NaN));
        assertEquals("-00:00", VideoPreviewDialog.formatRemainingTime(10000.0, 0.0));
        assertEquals("-00:00", VideoPreviewDialog.formatRemainingTime(10000.0, -100.0));
        assertEquals("-00:00", VideoPreviewDialog.formatRemainingTime(35000.0, 30000.0)); // Overrun
        assertEquals("-00:43", VideoPreviewDialog.formatRemainingTime(0.0, 43000.0));
        assertEquals("-00:28", VideoPreviewDialog.formatRemainingTime(15000.0, 43000.0));
        assertEquals("-01:15", VideoPreviewDialog.formatRemainingTime(5000.0, 80000.0));

        // Direct utility equivalence
        assertEquals("-00:28", VideoPlayerUtils.formatRemainingTime(15000.0, 43000.0));
        assertEquals("-00:00", VideoPlayerUtils.formatRemainingTime(Double.NaN, 0.0));
    }

    /**
     * Verifies volume glyph resolution across boundary thresholds and mute states.
     */
    @Test
    @DisplayName("Verify dynamic volume glyph resolution thresholds")
    public void testResolveVolumeGlyph() {
        assertEquals("\uD83D\uDD07", VideoPreviewDialog.resolveVolumeGlyph(0.8, true));
        assertEquals("\uD83D\uDD07", VideoPreviewDialog.resolveVolumeGlyph(0.0, false));
        assertEquals("\uD83D\uDD07", VideoPreviewDialog.resolveVolumeGlyph(-0.2, false));
        assertEquals("\uD83D\uDD07", VideoPreviewDialog.resolveVolumeGlyph(Double.NaN, false));
        assertEquals("\uD83D\uDD08", VideoPreviewDialog.resolveVolumeGlyph(0.2, false));
        assertEquals("\uD83D\uDD09", VideoPreviewDialog.resolveVolumeGlyph(0.5, false));
        assertEquals("\uD83D\uDD0A", VideoPreviewDialog.resolveVolumeGlyph(0.8, false));
        assertEquals("\uD83D\uDD0A", VideoPreviewDialog.resolveVolumeGlyph(1.0, false));

        // Direct utility equivalence
        assertEquals("\uD83D\uDD0A", VideoPlayerUtils.resolveVolumeGlyph(0.85, false));
        assertEquals("\uD83D\uDD07", VideoPlayerUtils.resolveVolumeGlyph(0.85, true));
    }

    /**
     * Verifies progress fraction calculation with clamping and invalid value handling.
     */
    @Test
    @DisplayName("Verify progress fraction calculation boundary values")
    public void testCalculateProgress() {
        assertEquals(0.0, VideoPreviewDialog.calculateProgress(Double.NaN, 1000.0));
        assertEquals(0.0, VideoPreviewDialog.calculateProgress(500.0, Double.NaN));
        assertEquals(0.0, VideoPreviewDialog.calculateProgress(500.0, 0.0));
        assertEquals(0.0, VideoPreviewDialog.calculateProgress(-100.0, 1000.0));
        assertEquals(0.0, VideoPreviewDialog.calculateProgress(0.0, 1000.0));
        assertEquals(0.35, VideoPreviewDialog.calculateProgress(350.0, 1000.0), 0.001);
        assertEquals(0.5, VideoPreviewDialog.calculateProgress(500.0, 1000.0), 0.001);
        assertEquals(1.0, VideoPreviewDialog.calculateProgress(1000.0, 1000.0), 0.001);
        assertEquals(1.0, VideoPreviewDialog.calculateProgress(1500.0, 1000.0), 0.001); // Overrun

        // Direct utility equivalence
        assertEquals(0.5, VideoPlayerUtils.calculateProgress(50.0, 100.0), 0.001);
    }

    /**
     * Verifies timeline track click destination seek calculation.
     */
    @Test
    @DisplayName("Verify timeline track click seek calculation")
    public void testCalculateSeekFromClick() {
        assertEquals(0.0, VideoPreviewDialog.calculateSeekFromClick(0.0, 200.0, 60000.0));
        assertEquals(0.0, VideoPreviewDialog.calculateSeekFromClick(-50.0, 200.0, 60000.0));
        assertEquals(0.0, VideoPreviewDialog.calculateSeekFromClick(100.0, 0.0, 60000.0));
        assertEquals(0.0, VideoPreviewDialog.calculateSeekFromClick(100.0, 200.0, 0.0));
        assertEquals(0.0, VideoPreviewDialog.calculateSeekFromClick(Double.NaN, 200.0, 60000.0));
        assertEquals(30000.0, VideoPreviewDialog.calculateSeekFromClick(100.0, 200.0, 60000.0), 0.001);
        assertEquals(60000.0, VideoPreviewDialog.calculateSeekFromClick(200.0, 200.0, 60000.0), 0.001);
        assertEquals(60000.0, VideoPreviewDialog.calculateSeekFromClick(250.0, 200.0, 60000.0), 0.001);

        // Direct utility equivalence
        assertEquals(15000.0, VideoPlayerUtils.calculateSeekFromClick(50.0, 200.0, 60000.0), 0.001);
    }

    /**
     * Verifies volume track click volume calculation.
     */
    @Test
    @DisplayName("Verify volume track click volume calculation")
    public void testCalculateVolumeFromClick() {
        assertEquals(0.0, VideoPreviewDialog.calculateVolumeFromClick(0.0, 60.0));
        assertEquals(0.0, VideoPreviewDialog.calculateVolumeFromClick(-10.0, 60.0));
        assertEquals(0.0, VideoPreviewDialog.calculateVolumeFromClick(30.0, 0.0));
        assertEquals(0.0, VideoPreviewDialog.calculateVolumeFromClick(30.0, -50.0));
        assertEquals(0.0, VideoPreviewDialog.calculateVolumeFromClick(Double.NaN, 60.0));
        assertEquals(0.0, VideoPreviewDialog.calculateVolumeFromClick(30.0, Double.NaN));
        assertEquals(0.5, VideoPreviewDialog.calculateVolumeFromClick(30.0, 60.0), 0.001);
        assertEquals(0.8, VideoPreviewDialog.calculateVolumeFromClick(48.0, 60.0), 0.001);
        assertEquals(1.0, VideoPreviewDialog.calculateVolumeFromClick(60.0, 60.0), 0.001);
        assertEquals(1.0, VideoPreviewDialog.calculateVolumeFromClick(80.0, 60.0), 0.001);

        // Direct utility equivalence
        assertEquals(0.5, VideoPlayerUtils.calculateVolumeFromClick(25.0, 50.0), 0.001);
    }
}
