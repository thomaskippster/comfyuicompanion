package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.service.IConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalAudioServiceTest {

    @Test
    void testProceduralAudioSynthesisRocketKeyword(@TempDir Path tempDir) {
        IConfigService mockConfig = Mockito.mock(IConfigService.class);
        Mockito.when(mockConfig.getFfmpegPath()).thenReturn("ffmpeg");

        File ffmpegExe = new File("C:/AI/ffmpeg/bin/ffmpeg.exe");
        if (ffmpegExe.exists()) {
            Mockito.when(mockConfig.getFfmpegPath()).thenReturn(ffmpegExe.getAbsolutePath());
        }

        LocalAudioService audioService = new LocalAudioService(mockConfig);
        File dest = tempDir.resolve("rocket_sfx.wav").toFile();

        if (ffmpegExe.exists()) {
            boolean success = audioService.generateSoundEffect(
                    "Extreme rocket thruster ignition, deafening bass rumble", 2, dest);
            assertTrue(success);
            assertTrue(dest.exists());
            assertTrue(dest.length() > 512);
        } else {
            // In headless/CI environments without local ffmpeg, verify null-safe execution
            assertFalse(audioService.generateSoundEffect(null, 2, null));
        }
    }

    @Test
    void testNeuralAudioAvailabilityWhenOffline() {
        IConfigService mockConfig = Mockito.mock(IConfigService.class);
        Mockito.when(mockConfig.getComfyUIUrl()).thenReturn("http://localhost:9999");

        LocalAudioService audioService = new LocalAudioService(mockConfig);
        assertFalse(audioService.isNeuralAudioAvailable());
    }

    @Test
    void testNullTargetFileHandledGracefully() {
        IConfigService mockConfig = Mockito.mock(IConfigService.class);
        LocalAudioService audioService = new LocalAudioService(mockConfig);
        assertFalse(audioService.generateSoundEffect("Rocket rumble", 3, null));
    }
}
