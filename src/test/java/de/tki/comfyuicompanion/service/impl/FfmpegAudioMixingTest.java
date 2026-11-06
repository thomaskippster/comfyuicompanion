package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.IConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FfmpegAudioMixingTest {

    @Test
    void testMixAudioTracksCommandStructure() {
        File voice = new File("voice.wav");
        File sfx = new File("sfx.wav");
        File dest = new File("mixed.wav");

        List<String> cmd = FfmpegCommandBuilder.mixAudioTracks("ffmpeg", voice, sfx, 1.0, 0.5, dest);
        assertNotNull(cmd);
        assertTrue(cmd.contains("-filter_complex"));
        int filterIdx = cmd.indexOf("-filter_complex");
        String filter = cmd.get(filterIdx + 1);
        assertTrue(filter.contains("volume=1.00"));
        assertTrue(filter.contains("volume=0.50"));
        assertTrue(filter.contains("amix=inputs=2"));
        assertTrue(cmd.contains(dest.getAbsolutePath()));
    }

    @Test
    void testResolveAudioReturnsVoiceWhenOnlyVoicePresent(@TempDir Path tempDir) throws Exception {
        ConfigService mockConfig = Mockito.mock(ConfigService.class);
        FfmpegProcessRunner runner = new FfmpegProcessRunner(mockConfig);

        File voiceFile = tempDir.resolve("voice.wav").toFile();
        try (FileWriter fw = new FileWriter(voiceFile)) {
            fw.write("dummy audio content");
        }

        Scene scene = new Scene("S1", "Prompt");
        scene.setAudioPath(voiceFile.getAbsolutePath());

        File resolved = runner.resolveAudioForScene(scene, null);
        assertNotNull(resolved);
        assertEquals(voiceFile.getAbsolutePath(), resolved.getAbsolutePath());
    }

    @Test
    void testResolveAudioReturnsSfxWhenOnlySfxPresent(@TempDir Path tempDir) throws Exception {
        ConfigService mockConfig = Mockito.mock(ConfigService.class);
        FfmpegProcessRunner runner = new FfmpegProcessRunner(mockConfig);

        File sfxFile = tempDir.resolve("sfx.wav").toFile();
        try (FileWriter fw = new FileWriter(sfxFile)) {
            fw.write("dummy sfx content");
        }

        Scene scene = new Scene("S2", "Prompt");
        scene.setSfxAudioPath(sfxFile.getAbsolutePath());

        File resolved = runner.resolveAudioForScene(scene, null);
        assertNotNull(resolved);
        assertEquals(sfxFile.getAbsolutePath(), resolved.getAbsolutePath());
    }
}
