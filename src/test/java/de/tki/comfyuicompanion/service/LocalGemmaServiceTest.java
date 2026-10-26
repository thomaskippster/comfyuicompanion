package de.tki.comfyuicompanion.service;

import de.tki.comfyuicompanion.service.impl.ChatTemplate;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.LocalGemmaService;
import de.tki.comfyuicompanion.service.impl.LocalGemmaService.LlmModelPreset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests verifying local LLM model preset resolution, file path calculation,
 * template formatting, and download state checks.
 */
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
        assertFalse(localGemmaService.isModelDownloaded(LlmModelPreset.QWEN_2_5_1_5B));
    }

    @Test
    public void testPresetResolution() {
        assertEquals(LlmModelPreset.QWEN_2_5_1_5B, LlmModelPreset.fromId("qwen-2.5-1.5b"));
        assertEquals(LlmModelPreset.LLAMA_3_2_3B, LlmModelPreset.fromId("llama-3.2-3b"));
        assertEquals(LlmModelPreset.PHI_3_5_MINI, LlmModelPreset.fromId("phi-3.5-mini"));
        assertEquals(LlmModelPreset.GEMMA_3_4B, LlmModelPreset.fromId("gemma-3-4b"));
        assertEquals(LlmModelPreset.QWEN_2_5_7B, LlmModelPreset.fromId("qwen-2.5-7b"));
        assertEquals(LlmModelPreset.GEMMA_3_4B, LlmModelPreset.fromId(null));
        assertEquals(LlmModelPreset.GEMMA_3_4B, LlmModelPreset.fromId("unknown-model"));
    }

    @Test
    public void testChatTemplateFormatting() {
        String sys = "You are a prompt engineer.";
        String user = "A dragon flying over mountains.";

        String chatml = ChatTemplate.CHATML.format(sys, user);
        assertTrue(chatml.contains("<|im_start|>system\n" + sys));
        assertTrue(chatml.contains("<|im_start|>user\n" + user));
        assertTrue(chatml.endsWith("<|im_start|>assistant\n"));

        String llama = ChatTemplate.LLAMA3.format(sys, user);
        assertTrue(llama.startsWith("<|begin_of_text|><|start_header_id|>system<|end_header_id|>"));
        assertTrue(llama.contains(sys));
        assertTrue(llama.contains("<|start_header_id|>user<|end_header_id|>"));
        assertTrue(llama.contains(user));
        assertTrue(llama.endsWith("<|start_header_id|>assistant<|end_header_id|>\n\n"));

        String gemma = ChatTemplate.GEMMA.format(sys, user);
        assertTrue(gemma.startsWith("<start_of_turn>user\n" + sys));
        assertTrue(gemma.contains(user));
        assertTrue(gemma.endsWith("<start_of_turn>model\n"));
    }

    @Test
    public void testGenerateCompletionWithoutModelThrowsException() {
        when(configService.getModelsPath()).thenReturn("C:\\nonexistent_directory_123");
        assertThrows(IOException.class, () -> {
            localGemmaService.generateCompletion("System instruction", "User prompt", 0.7f, 50);
        });
    }
}
