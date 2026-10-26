package de.tki.comfyuicompanion.service;

import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.LocalAIService;
import de.tki.comfyuicompanion.service.impl.LocalGemmaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LocalAIService validating prompt completion parsing,
 * markdown fence stripping, and fallback handling.
 */
public class LocalAIServiceTest {

    private ConfigService configService;
    private LocalGemmaService localGemmaService;
    private LocalAIService localAIService;

    @BeforeEach
    public void setUp() {
        configService = mock(ConfigService.class);
        localGemmaService = mock(LocalGemmaService.class);
        localAIService = new LocalAIService(configService, localGemmaService);
    }

    @Test
    public void testGetDirectGemmaCompletionsSuccess() throws IOException {
        when(localGemmaService.isModelDownloaded()).thenReturn(true);
        String mockResponse = "[\"Cinematic 35mm photograph of cyber tiger\", \"Moody atmospheric nocturnal hunt\", \"Editorial stylized concept art\", \"Close-up detailed macro lens portrait\"]";
        when(localGemmaService.generateCompletion(anyString(), anyString(), anyFloat(), anyInt())).thenReturn(mockResponse);

        List<String> results = localAIService.getDirectGemmaCompletions("cyber tiger");

        assertNotNull(results);
        assertEquals(4, results.size());
        assertEquals("Cinematic 35mm photograph of cyber tiger", results.get(0));
    }

    @Test
    public void testGetDirectGemmaCompletionsWithMarkdownFences() throws IOException {
        when(localGemmaService.isModelDownloaded()).thenReturn(true);
        String fencedResponse = "```json\n[\"Option 1\", \"Option 2\", \"Option 3\", \"Option 4\"]\n```";
        when(localGemmaService.generateCompletion(anyString(), anyString(), anyFloat(), anyInt())).thenReturn(fencedResponse);

        List<String> results = localAIService.getDirectGemmaCompletions("neon city");

        assertNotNull(results);
        assertEquals(4, results.size());
        assertEquals("Option 1", results.get(0));
    }

    @Test
    public void testGetDirectGemmaCompletionsWhenModelNotDownloaded() {
        when(localGemmaService.isModelDownloaded()).thenReturn(false);

        List<String> results = localAIService.getDirectGemmaCompletions("cyber tiger");

        assertNull(results);
    }
}
