package de.tki.comfymodels.service;

import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.LocalGemmaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class LocalGemmaServiceTest {

    private ConfigService configService;
    private LocalGemmaService localGemmaService;

    @BeforeEach
    public void setUp() {
        configService = mock(ConfigService.class);
        localGemmaService = new LocalGemmaService(configService);
    }

    @Test
    public void testModelFileLocation() {
        when(configService.getModelsPath()).thenReturn("C:\\test\\models");
        File modelFile = localGemmaService.getModelFile();
        assertNotNull(modelFile);
        assertTrue(modelFile.getAbsolutePath().contains("llm"));
        assertTrue(modelFile.getName().endsWith(".gguf"));
    }

    @Test
    public void testModelFileFallbackLocation() {
        when(configService.getModelsPath()).thenReturn("");
        when(configService.getAppDataPath()).thenReturn("C:\\appdata");
        File modelFile = localGemmaService.getModelFile();
        assertNotNull(modelFile);
        assertTrue(modelFile.getAbsolutePath().startsWith("C:\\appdata"));
    }

    @Test
    public void testIsModelDownloadedWhenNotExist() {
        when(configService.getModelsPath()).thenReturn("C:\\nonexistent_directory_123");
        assertFalse(localGemmaService.isModelDownloaded());
    }

    @Test
    public void testGenerateCompletionWithoutModelThrowsException() {
        when(configService.getModelsPath()).thenReturn("C:\\nonexistent_directory_123");
        assertThrows(IOException.class, () -> {
            localGemmaService.generateCompletion("System instruction", "User prompt", 0.7f, 50);
        });
    }
}
