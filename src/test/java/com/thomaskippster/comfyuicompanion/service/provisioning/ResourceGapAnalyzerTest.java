package com.thomaskippster.comfyuicompanion.service.provisioning;

import com.thomaskippster.comfyuicompanion.client.ComfyHttpClient;
import com.thomaskippster.comfyuicompanion.domain.graph.ComfyNode;
import com.thomaskippster.comfyuicompanion.domain.graph.ComfyWorkflow;
import com.thomaskippster.comfyuicompanion.domain.llm.GenerationIntent;
import com.thomaskippster.comfyuicompanion.service.graph.GraphMutationService;
import com.thomaskippster.comfyuicompanion.service.inspector.ModelArchitectureAnalyzer;
import com.thomaskippster.comfyuicompanion.service.inspector.SafetensorsInspectorService;
import de.tki.comfymodels.service.SafePathValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class ResourceGapAnalyzerTest {

    private SafetensorsInspectorService inspectorService;
    private ModelArchitectureAnalyzer architectureAnalyzer;
    private HuggingFaceClient huggingFaceClient;
    private LargeFileDownloadService downloadService;
    private ComfyHttpClient comfyHttpClient;
    private GraphMutationService graphMutationService;
    private SafePathValidator safePathValidator;

    private ResourceGapAnalyzer gapAnalyzer;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        inspectorService = mock(SafetensorsInspectorService.class);
        architectureAnalyzer = mock(ModelArchitectureAnalyzer.class);
        huggingFaceClient = mock(HuggingFaceClient.class);
        downloadService = mock(LargeFileDownloadService.class);
        comfyHttpClient = mock(ComfyHttpClient.class);
        graphMutationService = mock(GraphMutationService.class);
        safePathValidator = new SafePathValidator();

        gapAnalyzer = new ResourceGapAnalyzer(
                inspectorService,
                architectureAnalyzer,
                huggingFaceClient,
                downloadService,
                comfyHttpClient,
                graphMutationService,
                safePathValidator
        );

        ReflectionTestUtils.setField(gapAnalyzer, "modelsBasePath", tempDir.toString());
    }

    @Test
    void testResolveGapAndExecuteWhenNoMissingResource() {
        GenerationIntent intent = new GenerationIntent(); // no keyword -> not missing
        ComfyWorkflow workflow = new ComfyWorkflow();

        when(comfyHttpClient.triggerWorkflow(workflow, "client-123")).thenReturn(Mono.just("prompt-456"));

        Mono<String> result = gapAnalyzer.resolveGapAndExecute(intent, workflow, "client-123");
        String promptId = result.block(Duration.ofSeconds(5));
        assertEquals("prompt-456", promptId);

        verify(comfyHttpClient).triggerWorkflow(workflow, "client-123");
        verifyNoInteractions(huggingFaceClient, downloadService);
    }

    @Test
    void testResolveGapAndExecuteWithAutoProvisioning() {
        GenerationIntent intent = new GenerationIntent();
        intent.setSuggestedCheckpointKeyword("flux-dev");
        intent.setArchitecture("FLUX");

        ComfyWorkflow workflow = new ComfyWorkflow();
        Map<String, ComfyNode> nodes = new HashMap<>();
        ComfyNode loader = new ComfyNode();
        loader.setClassType("CheckpointLoaderSimple");
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("ckpt_name", "old_checkpoint.safetensors");
        loader.setInputs(inputs);
        nodes.put("1", loader);
        workflow.setNodes(nodes);

        ModelRecommendation rec = new ModelRecommendation();
        rec.setName("flux-dev-v1");
        rec.setRepositoryId("black-forest-labs/flux-dev");
        rec.setDownloadUrl("https://huggingface.co/mock/flux-dev.safetensors");

        when(huggingFaceClient.searchModelsReactive("flux-dev", "FLUX"))
                .thenReturn(Mono.just(List.of(rec)));
        when(downloadService.downloadModel(eq(rec.getDownloadUrl()), any(java.nio.file.Path.class)))
                .thenReturn(Flux.just(50.0, 100.0));
        when(comfyHttpClient.triggerModelRefresh())
                .thenReturn(Mono.empty());
        when(comfyHttpClient.triggerWorkflow(eq(workflow), eq("client-abc")))
                .thenReturn(Mono.just("prompt-789"));

        Mono<String> result = gapAnalyzer.resolveGapAndExecute(intent, workflow, "client-abc");
        String promptId = result.block(Duration.ofSeconds(5));
        assertEquals("prompt-789", promptId);

        verify(huggingFaceClient).searchModelsReactive("flux-dev", "FLUX");
        verify(downloadService).downloadModel(eq(rec.getDownloadUrl()), any(java.nio.file.Path.class));
        verify(comfyHttpClient).triggerModelRefresh();
        verify(graphMutationService).swapCheckpoint(eq(workflow), anyString());
        verify(comfyHttpClient).triggerWorkflow(workflow, "client-abc");
    }
}
