package com.thomaskippster.comfyuicompanion.service;

import com.thomaskippster.comfyuicompanion.client.ComfyHttpClient;
import com.thomaskippster.comfyuicompanion.domain.graph.ComfyNode;
import com.thomaskippster.comfyuicompanion.domain.graph.ComfyWorkflow;
import com.thomaskippster.comfyuicompanion.domain.llm.GenerationIntent;
import com.thomaskippster.comfyuicompanion.service.inspector.SafetensorsInspectorService;
import com.thomaskippster.comfyuicompanion.service.llm.LlmRoutingService;
import com.thomaskippster.comfyuicompanion.service.provisioning.ResourceGapAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AgenticWorkflowOrchestratorTest {

    @Mock
    private LlmRoutingService llmRoutingService;

    @Mock
    private SafetensorsInspectorService inspectorService;

    @Mock
    private ComfyHttpClient comfyHttpClient;

    @Mock
    private ResourceGapAnalyzer gapAnalyzer;

    @InjectMocks
    private AgenticWorkflowOrchestrator orchestrator;

    @BeforeEach
    public void setUp() {
        // Da der Orchestrator nun den gapAnalyzer anruft, mocken wir diesen.
        when(gapAnalyzer.resolveGapAndExecute(any(GenerationIntent.class), any(ComfyWorkflow.class), anyString()))
                .thenReturn(Mono.just("mock_prompt_id"));
    }

    @Test
    public void testConditionalRoutingForFluxAndFaceDetailer() {
        // Arrange
        String userInput = "Ein Porträt eines Mannes, der ein Schild mit der Aufschrift 'Hallo' hält";

        // Mocken des LLM-Verhaltens
        GenerationIntent mockIntent = new GenerationIntent();
        mockIntent.setArchitecture("FLUX");
        mockIntent.setOptimizedPositivePrompt("A cinematic portrait of a man holding a cardboard sign that says 'Hallo'. Photorealistic.");
        mockIntent.setRequiresTextRendering(true);
        mockIntent.setRequiresFaceDetailer(true); // <--- Dieser Trigger entscheidet!
        mockIntent.setTargetAspectRatio("1:1");
        mockIntent.setSuggestedCheckpointKeyword("flux1-dev");

        when(llmRoutingService.parseUserRequest(userInput)).thenReturn(mockIntent);

        // Act
        // Since we refactored it to be reactive, we must block to execute the pipeline in the test
        orchestrator.orchestrateAndRun(userInput).block();

        // Assert: ArgumentCaptor greift den Graphen ab, bevor er an die Auto-Provisioning Engine geht
        ArgumentCaptor<ComfyWorkflow> workflowCaptor = ArgumentCaptor.forClass(ComfyWorkflow.class);
        verify(gapAnalyzer).resolveGapAndExecute(any(GenerationIntent.class), workflowCaptor.capture(), anyString());

        ComfyWorkflow generatedWorkflow = workflowCaptor.getValue();

        // Verifikation der dynamischen Graphen-Struktur
        boolean hasFaceDetailer = false;
        boolean hasBboxDetector = false;
        boolean loadsFluxModel = false;

        for (ComfyNode node : generatedWorkflow.getNodes().values()) {
            if ("FaceDetailer".equals(node.getClassType())) {
                hasFaceDetailer = true;
            }
            if ("BboxDetectorSEGS".equals(node.getClassType())) {
                hasBboxDetector = true;
            }
            if ("CheckpointLoaderSimple".equals(node.getClassType())) {
                String ckptName = (String) node.getInputs().get("ckpt_name");
                if (ckptName != null && ckptName.contains("flux1-dev") && ckptName.contains("FLUX")) {
                    loadsFluxModel = true;
                }
            }
        }

        assertTrue(hasFaceDetailer, "Der Workflow muss zwingend den FaceDetailer enthalten, wenn der Intent es verlangt.");
        assertTrue(hasBboxDetector, "Der Workflow benötigt den BboxDetector für den FaceDetailer.");
        assertTrue(loadsFluxModel, "Die Checkpoint-Suche muss auf das FLUX-Modell angesprungen sein.");
    }
}
