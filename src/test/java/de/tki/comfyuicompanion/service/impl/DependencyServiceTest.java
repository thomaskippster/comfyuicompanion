package de.tki.comfyuicompanion.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests verifying FFmpeg dependency detection, installation checks,
 * and null-safety in {@link DependencyService}.
 */
public class DependencyServiceTest {

    @TempDir
    Path tempDir;

    private ConfigService mockConfig;
    private ProcessTracker mockTracker;

    @BeforeEach
    void setUp() {
        mockConfig = Mockito.mock(ConfigService.class);
        mockTracker = Mockito.mock(ProcessTracker.class);
    }

    @Test
    @DisplayName("isFfmpegInstalled returns true when executable exists on configured path")
    void testIsFfmpegInstalled_ExistingFile() throws IOException {
        File fakeFfmpeg = tempDir.resolve("ffmpeg.exe").toFile();
        assertTrue(fakeFfmpeg.createNewFile());

        when(mockConfig.getFfmpegPath()).thenReturn(fakeFfmpeg.getAbsolutePath());

        DependencyService service = new DependencyService(mockConfig, mockTracker);
        assertTrue(service.isFfmpegInstalled(), "Should return true for an existing ffmpeg binary");
    }

    @Test
    @DisplayName("isFfmpegInstalled returns false when configured path does not exist")
    void testIsFfmpegInstalled_NonExistingFile() {
        File missingFile = tempDir.resolve("non_existent_ffmpeg.exe").toFile();

        when(mockConfig.getFfmpegPath()).thenReturn(missingFile.getAbsolutePath());

        DependencyService service = new DependencyService(mockConfig, mockTracker);
        assertFalse(service.isFfmpegInstalled(), "Should return false for non-existent binary");
    }

    @Test
    @DisplayName("isFfmpegInstalled works safely without ProcessTracker (Null Safety)")
    void testIsFfmpegInstalled_NullProcessTracker() throws IOException {
        File fakeFfmpeg = tempDir.resolve("ffmpeg.exe").toFile();
        assertTrue(fakeFfmpeg.createNewFile());

        when(mockConfig.getFfmpegPath()).thenReturn(fakeFfmpeg.getAbsolutePath());

        DependencyService service = new DependencyService(mockConfig, null);
        assertDoesNotThrow(service::isFfmpegInstalled);
        assertTrue(service.isFfmpegInstalled());
    }

    @Test
    @DisplayName("installFfmpeg returns immediately when already present")
    void testEnsureFfmpegInstalled_AlreadyInstalled() throws Exception {
        File fakeFfmpeg = tempDir.resolve("ffmpeg.exe").toFile();
        assertTrue(fakeFfmpeg.createNewFile());

        when(mockConfig.getFfmpegPath()).thenReturn(fakeFfmpeg.getAbsolutePath());

        DependencyService service = new DependencyService(mockConfig, null);
        AtomicBoolean progressReported = new AtomicBoolean(false);

        service.installFfmpeg(msg -> progressReported.set(true));
        assertTrue(progressReported.get(), "Progress should be reported when checking ffmpeg");
    }
}
