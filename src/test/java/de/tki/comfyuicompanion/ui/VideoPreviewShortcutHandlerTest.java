package de.tki.comfyuicompanion.ui;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Slider;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enterprise unit tests verifying key event handling in {@link VideoPreviewShortcutHandler}.
 */
public class VideoPreviewShortcutHandlerTest {

    @BeforeAll
    public static void initJavaFx() {
        new JFXPanel();
        Platform.setImplicitExit(false);
    }

    @Test
    @DisplayName("Verify fullscreen and close callbacks are triggered on respective shortcut key events")
    public void testShortcutCallbacks() {
        AtomicBoolean fullscreenTriggered = new AtomicBoolean(false);
        AtomicBoolean closeTriggered = new AtomicBoolean(false);

        Button btnMute = new Button();
        Slider volumeSlider = new Slider(0, 100, 80);
        Label volumeLabel = new Label("80%");
        ProgressBar volumeProgress = new ProgressBar(0.8);
        Button btnFullscreen = new Button();
        Slider timeSlider = new Slider(0, 100, 0);
        ProgressBar timelineProgress = new ProgressBar(0.0);
        Label timeLabel = new Label("00:00");

        VideoPreviewShortcutHandler handler = new VideoPreviewShortcutHandler(
                null, btnMute, volumeSlider, volumeLabel, volumeProgress,
                btnFullscreen, timeSlider, timelineProgress, timeLabel,
                () -> fullscreenTriggered.set(true),
                () -> closeTriggered.set(true)
        );

        KeyEvent fKey = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F, false, false, false, false);
        handler.handle(fKey);
        assertTrue(fullscreenTriggered.get(), "F key must trigger fullscreen action");

        fullscreenTriggered.set(false);
        KeyEvent f11Key = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F11, false, false, false, false);
        handler.handle(f11Key);
        assertTrue(fullscreenTriggered.get(), "F11 key must trigger fullscreen action");

        KeyEvent escKey = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false);
        handler.handle(escKey);
        assertTrue(closeTriggered.get(), "Escape key must trigger close action");
    }

    @Test
    @DisplayName("Verify loop toggle and speed step shortcuts")
    public void testLoopAndSpeedShortcuts() {
        javafx.scene.control.ToggleButton btnLoop = new javafx.scene.control.ToggleButton("Loop");
        btnLoop.setSelected(true);

        javafx.scene.control.ComboBox<String> speedCombo = new javafx.scene.control.ComboBox<>(
                javafx.collections.FXCollections.observableArrayList("0.5x", "1.0x", "1.5x", "2.0x"));
        speedCombo.setValue("1.0x");

        VideoPreviewShortcutHandler handler = new VideoPreviewShortcutHandler(
                null, new Button(), new Slider(), new Label(), new ProgressBar(),
                new Button(), new Slider(), new ProgressBar(), new Label(),
                btnLoop, speedCombo, () -> {}, () -> {}
        );

        // Test L key toggles loop
        KeyEvent lKey = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.L, false, false, false, false);
        handler.handle(lKey);
        org.junit.jupiter.api.Assertions.assertFalse(btnLoop.isSelected(), "L key must toggle loop off");

        handler.handle(lKey);
        assertTrue(btnLoop.isSelected(), "L key must toggle loop back on");

        // Test [ key steps speed down
        KeyEvent speedDownKey = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.OPEN_BRACKET, false, false, false, false);
        handler.handle(speedDownKey);
        org.junit.jupiter.api.Assertions.assertEquals("0.75x", speedCombo.getValue(), "[ key must step speed down from 1.0x to 0.75x");

        // Test ] key steps speed up
        KeyEvent speedUpKey = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.CLOSE_BRACKET, false, false, false, false);
        handler.handle(speedUpKey);
        org.junit.jupiter.api.Assertions.assertEquals("1.0x", speedCombo.getValue(), "] key must step speed up from 0.75x to 1.0x");

        handler.handle(speedUpKey);
        org.junit.jupiter.api.Assertions.assertEquals("1.25x", speedCombo.getValue(), "] key must step speed up from 1.0x to 1.25x");
    }
}
