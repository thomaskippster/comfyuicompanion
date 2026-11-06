package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.service.IConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("LocalModelArchiveDetector Tests")
class LocalModelArchiveDetectorTest {

    @Test
    @DisplayName("Should detect model archive directory containing standard subdirectories")
    void shouldDetectArchiveDirectory(@TempDir Path tempDir) throws Exception {
        Path archiveRoot = tempDir.resolve("Comfy-Archive");
        Files.createDirectories(archiveRoot.resolve("text_encoders"));
        Files.createDirectories(archiveRoot.resolve("checkpoints"));
        Files.createDirectories(archiveRoot.resolve("upscale_models"));

        IConfigService config = mock(IConfigService.class);
        when(config.getModelsPath()).thenReturn(archiveRoot.toString());

        LocalModelArchiveDetector detector = new LocalModelArchiveDetector(config);
        List<Path> roots = detector.detectArchiveRoots();

        assertTrue(roots.contains(archiveRoot.toAbsolutePath().normalize()),
                "Must detect Comfy-Archive as a valid model archive root");

        List<Path> textEncoderDirs = detector.resolveArchiveDirectoriesForCategory("clip");
        assertFalse(textEncoderDirs.isEmpty(), "Must resolve 'clip' to 'text_encoders' alias folder in archive");
        assertTrue(textEncoderDirs.stream().anyMatch(p -> p.endsWith("text_encoders")),
                "Category resolution must include text_encoders");
    }
}
