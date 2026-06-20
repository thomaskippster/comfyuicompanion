package de.tki.comfymodels.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import de.tki.comfymodels.domain.ModelArchitecture;
import de.tki.comfymodels.service.impl.LocalGemmaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

public class ComfyUIArchitectureClassifierTest {

    private ComfyUIArchitectureClassifier classifier;
    private LocalGemmaService mockGemmaService;

    @BeforeEach
    void setUp() {
        mockGemmaService = Mockito.mock(LocalGemmaService.class);
        classifier = new ComfyUIArchitectureClassifier(mockGemmaService);
    }

    @Test
    void testExtractNodeTypes_withPromptEnvelope() throws JsonProcessingException {
        String json = "{"
                + "  \"prompt\": {"
                + "    \"1\": { \"class_type\": \"CheckpointLoaderSimple\" },"
                + "    \"2\": { \"class_type\": \"KSampler\" },"
                + "    \"3\": { \"class_type\": \"CheckpointLoaderSimple\" }"
                + "  }"
                + "}";
        Set<String> nodes = classifier.extractNodeTypes(json);
        assertEquals(2, nodes.size());
        assertTrue(nodes.contains("CheckpointLoaderSimple"));
        assertTrue(nodes.contains("KSampler"));
    }

    @Test
    void testExtractNodeTypes_withoutPromptEnvelope() throws JsonProcessingException {
        String json = "{"
                + "  \"1\": { \"class_type\": \"UNETLoader\" },"
                + "  \"2\": { \"class_type\": \"CLIPTextEncode\" }"
                + "}";
        Set<String> nodes = classifier.extractNodeTypes(json);
        assertEquals(2, nodes.size());
        assertTrue(nodes.contains("UNETLoader"));
        assertTrue(nodes.contains("CLIPTextEncode"));
    }

    @Test
    void testExtractNodeTypes_emptyOrInvalid() throws JsonProcessingException {
        Set<String> empty = classifier.extractNodeTypes("{}");
        assertTrue(empty.isEmpty());

        Set<String> nullResult = classifier.extractNodeTypes((String) null);
        assertTrue(nullResult.isEmpty());
    }

    @Test
    void testBuildPrompts() {
        String systemPrompt = classifier.buildSystemPrompt();
        assertNotNull(systemPrompt);
        assertTrue(systemPrompt.contains("ARCH_FLUX"));

        Set<String> nodes = Set.of("KSampler", "EmptyLatentImage");
        String userPrompt = classifier.buildUserPrompt(nodes);
        assertTrue(userPrompt.contains("KSampler"));
        assertTrue(userPrompt.contains("EmptyLatentImage"));
    }

    @Test
    void testParseInnerArchitectureResponse_validJson() {
        String json = "{\"architecture\": \"ARCH_FLUX\"}";
        ModelArchitecture arch = classifier.parseInnerArchitectureResponse(json);
        assertEquals(ModelArchitecture.ARCH_FLUX, arch);
    }

    @Test
    void testParseInnerArchitectureResponse_markdownWrappedJson() {
        String json = "```json\n{\"architecture\": \"ARCH_WAN\"}\n```";
        ModelArchitecture arch = classifier.parseInnerArchitectureResponse(json);
        assertEquals(ModelArchitecture.ARCH_WAN, arch);
    }

    @Test
    void testParseInnerArchitectureResponse_fallbackText() {
        String rawTextResponse = "Der Workflow verwendet FLUX Nodes, daher ist es ARCH_FLUX.";
        ModelArchitecture arch = classifier.parseInnerArchitectureResponse(rawTextResponse);
        assertEquals(ModelArchitecture.ARCH_FLUX, arch);
    }

    @Test
    void testParseInnerArchitectureResponse_completelyBroken() {
        ModelArchitecture arch = classifier.parseInnerArchitectureResponse("broken json");
        assertEquals(ModelArchitecture.ARCH_UNKNOWN, arch);

        ModelArchitecture archNull = classifier.parseInnerArchitectureResponse(null);
        assertEquals(ModelArchitecture.ARCH_UNKNOWN, archNull);
    }

    @Test
    void testClassifyWorkflow_success() throws IOException {
        when(mockGemmaService.isModelDownloaded()).thenReturn(true);
        when(mockGemmaService.generateCompletion(anyString(), anyString(), anyFloat(), anyInt()))
                .thenReturn("{\"architecture\": \"ARCH_SDXL\"}");

        Set<String> nodes = Set.of("CheckpointLoaderSimple", "KSampler");
        ModelArchitecture result = classifier.classifyWorkflow(nodes);
        assertEquals(ModelArchitecture.ARCH_SDXL, result);
    }

    @Test
    void testClassifyWorkflow_modelNotDownloaded() {
        when(mockGemmaService.isModelDownloaded()).thenReturn(false);

        Set<String> nodes = Set.of("CheckpointLoaderSimple", "KSampler");
        ModelArchitecture result = classifier.classifyWorkflow(nodes);
        assertEquals(ModelArchitecture.ARCH_UNKNOWN, result);
    }

    @Test
    void testClassifyModel_success() throws IOException {
        when(mockGemmaService.isModelDownloaded()).thenReturn(true);
        when(mockGemmaService.generateCompletion(anyString(), anyString(), anyFloat(), anyInt()))
                .thenReturn("{\"architecture\": \"ARCH_FLUX\"}");

        ModelArchitecture result = classifier.classifyModel("my-fancy-flux-model.safetensors");
        assertEquals(ModelArchitecture.ARCH_FLUX, result);
    }

    @Test
    void testClassifyModel_exceptionFallback() throws IOException {
        when(mockGemmaService.isModelDownloaded()).thenReturn(true);
        when(mockGemmaService.generateCompletion(anyString(), anyString(), anyFloat(), anyInt()))
                .thenThrow(new IOException("Inference error"));

        ModelArchitecture result = classifier.classifyModel("some-model.safetensors");
        assertEquals(ModelArchitecture.ARCH_UNKNOWN, result);
    }
}
