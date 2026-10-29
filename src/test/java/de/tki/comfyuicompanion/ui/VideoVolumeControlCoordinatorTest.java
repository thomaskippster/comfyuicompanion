package de.tki.comfyuicompanion.ui;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enterprise unit tests verifying component creation, state management,
 * and CSS class transitions in {@link VideoVolumeControlCoordinator}.
 */
public class VideoVolumeControlCoordinatorTest {

    @BeforeAll
    public static void initJavaFx() {
        new JFXPanel();
        Platform.setImplicitExit(false);
    }

    @Test
    @DisplayName("Verify default initialization of volume control subsystem")
    public void testDefaultInitialization() {
        VideoVolumeControlCoordinator coordinator = new VideoVolumeControlCoordinator(null);

        assertNotNull(coordinator.getVolumeBox(), "Volume container must not be null");
        assertTrue(coordinator.getVolumeBox().getStyleClass().contains("volume-control-box"));

        assertNotNull(coordinator.getBtnMute(), "Mute button must not be null");
        assertEquals("\uD83D\uDD0A", coordinator.getBtnMute().getText(), "Initial glyph must be high volume speaker");
        assertNotNull(coordinator.getBtnMute().getTooltip(), "Mute button must possess tooltip");
        assertEquals("Mute audio (M)", coordinator.getBtnMute().getTooltip().getText());
        assertFalse(coordinator.getBtnMute().getStyleClass().contains("mute-active"),
                "Mute button must not have active mute style when unmuted");

        assertNotNull(coordinator.getVolumeProgress(), "Volume progress bar must not be null");
        assertEquals(0.8, coordinator.getVolumeProgress().getProgress(), 0.001);

        assertNotNull(coordinator.getVolumeSlider(), "Volume slider must not be null");
        assertEquals(80.0, coordinator.getVolumeSlider().getValue(), 0.001);

        assertNotNull(coordinator.getVolumePercentLabel(), "Volume percentage label must not be null");
        assertEquals("80%", coordinator.getVolumePercentLabel().getText());
        assertTrue(coordinator.getVolumePercentLabel().getStyleClass().contains("label-volume-interactive"));
        assertFalse(coordinator.getVolumePercentLabel().getStyleClass().contains("label-volume-muted"));
    }

    @Test
    @DisplayName("Verify toggleMute flips state, updates glyph, tooltips and CSS classes")
    public void testToggleMute() {
        VideoVolumeControlCoordinator coordinator = new VideoVolumeControlCoordinator(null);

        // First toggle -> mute active
        coordinator.toggleMute();
        assertEquals("\uD83D\uDD07", coordinator.getBtnMute().getText(), "Muted glyph must be speaker with cancellation");
        assertEquals("Unmute audio (M)", coordinator.getBtnMute().getTooltip().getText());
        assertTrue(coordinator.getBtnMute().getStyleClass().contains("mute-active"),
                "Mute button must acquire mute-active style class when muted");
        assertEquals(0.0, coordinator.getVolumeProgress().getProgress(), 0.001);
        assertEquals("0%", coordinator.getVolumePercentLabel().getText());
        assertTrue(coordinator.getVolumePercentLabel().getStyleClass().contains("label-volume-muted"));

        // Second toggle -> restored unmuted state
        coordinator.toggleMute();
        assertEquals("\uD83D\uDD0A", coordinator.getBtnMute().getText());
        assertEquals("Mute audio (M)", coordinator.getBtnMute().getTooltip().getText());
        assertFalse(coordinator.getBtnMute().getStyleClass().contains("mute-active"),
                "Mute button must drop mute-active style class when unmuted");
        assertEquals(0.8, coordinator.getVolumeProgress().getProgress(), 0.001);
        assertEquals("80%", coordinator.getVolumePercentLabel().getText());
        assertFalse(coordinator.getVolumePercentLabel().getStyleClass().contains("label-volume-muted"));
    }

    @Test
    @DisplayName("Verify adjustVolume relative increments and bounds clamping")
    public void testAdjustVolume() {
        VideoVolumeControlCoordinator coordinator = new VideoVolumeControlCoordinator(null);

        coordinator.adjustVolume(0.1);
        assertEquals(90.0, coordinator.getVolumeSlider().getValue(), 0.001);

        coordinator.adjustVolume(0.3); // Clamps to 100%
        assertEquals(100.0, coordinator.getVolumeSlider().getValue(), 0.001);

        coordinator.adjustVolume(-1.5); // Clamps to 0%
        assertEquals(0.0, coordinator.getVolumeSlider().getValue(), 0.001);
    }
}
