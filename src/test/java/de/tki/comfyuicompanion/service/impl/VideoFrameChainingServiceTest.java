package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.domain.Scene;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VideoFrameChainingService} verifying seamless continuous shot
 * frame chaining resolution and terminal frame extraction handling.
 */
class VideoFrameChainingServiceTest {

    private ConfigService configService;
    private VideoFrameChainingService chainingService;

    @BeforeEach
    void setUp() {
        configService = Mockito.mock(ConfigService.class);
        when(configService.getFfmpegPath()).thenReturn("ffmpeg");
        chainingService = new VideoFrameChainingService(configService);
    }

    @Test
    @DisplayName("Should return default or initial speaker image for the first scene (index 0)")
    void testResolveChainedStartImageFirstScene(@TempDir Path tempDir) {
        List<Scene> scenes = new ArrayList<>();
        Scene scene1 = new Scene("S1", "A cyberpunk alleyway", 0, 80, null, null);
        scenes.add(scene1);

        String result = chainingService.resolveChainedStartImage(scenes, 0, "default_speaker.png", tempDir.toFile());
        assertEquals("default_speaker.png", result);

        scene1.setSpeakerImagePath("explicit_start.png");
        String explicitResult = chainingService.resolveChainedStartImage(scenes, 0, "default_speaker.png", tempDir.toFile());
        assertEquals("explicit_start.png", explicitResult);
    }

    @Test
    @DisplayName("Should handle missing previous video gracefully and return fallback for subsequent scenes")
    void testResolveChainedStartImageMissingPreviousVideo(@TempDir Path tempDir) {
        List<Scene> scenes = new ArrayList<>();
        Scene scene1 = new Scene("S1", "Scene 1", 0, 80, null, null);
        Scene scene2 = new Scene("S2", "Scene 2", 81, 160, null, null);
        scenes.add(scene1);
        scenes.add(scene2);

        // Previous scene has no video path
        String result = chainingService.resolveChainedStartImage(scenes, 1, "fallback.png", tempDir.toFile());
        assertEquals("fallback.png", result);
    }

    @Test
    @DisplayName("Should fail gracefully and return null when extracting from nonexistent video")
    void testExtractLastFrameNonExistentVideo(@TempDir Path tempDir) {
        File fakeVideo = new File(tempDir.toFile(), "nonexistent.mp4");
        File targetImage = new File(tempDir.toFile(), "frame.png");

        File result = chainingService.extractLastFrame(fakeVideo, targetImage);
        assertNull(result);
    }
}
