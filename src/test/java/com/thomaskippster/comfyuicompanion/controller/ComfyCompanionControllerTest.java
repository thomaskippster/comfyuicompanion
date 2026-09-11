package com.thomaskippster.comfyuicompanion.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thomaskippster.comfyuicompanion.client.ComfyHttpClient;
import com.thomaskippster.comfyuicompanion.dto.GenerationRequest;
import com.thomaskippster.comfyuicompanion.dto.GenerationResponse;
import com.thomaskippster.comfyuicompanion.service.AgenticWorkflowOrchestrator;
import de.tki.comfymodels.service.SafePathValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComfyCompanionControllerTest {

    @Mock
    private AgenticWorkflowOrchestrator orchestrator;

    @Mock
    private ComfyHttpClient comfyHttpClient;

    @Mock
    private com.thomaskippster.comfyuicompanion.service.inspector.TriggerWordService triggerWordService;

    @Mock
    private com.thomaskippster.comfyuicompanion.service.provisioning.CustomNodeResolverService customNodeResolverService;

    private SafePathValidator safePathValidator;
    private ComfyCompanionController controller;

    @BeforeEach
    void setUp() {
        safePathValidator = new SafePathValidator();
        controller = new ComfyCompanionController(orchestrator, comfyHttpClient, safePathValidator, triggerWordService, customNodeResolverService);
    }

    @Test
    @DisplayName("generateImage: should return processing response with promptId when prompt is valid")
    void shouldGenerateImageSuccessfully() {
        GenerationRequest request = new GenerationRequest("A beautiful futuristic landscape, cinematic 8k");
        when(orchestrator.orchestrateAndRun(request.prompt()))
                .thenReturn(Mono.just("prompt-uuid-12345"));

        GenerationResponse response = controller.generateImage(request).block();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("processing");
        assertThat(response.promptId()).isEqualTo("prompt-uuid-12345");
        assertThat(response.message()).contains("erfolgreich");

        verify(orchestrator, times(1)).orchestrateAndRun("A beautiful futuristic landscape, cinematic 8k");
    }

    @Test
    @DisplayName("GenerationRequest: should reject empty or blank prompt")
    void shouldRejectEmptyPromptInDto() {
        assertThatThrownBy(() -> new GenerationRequest(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Prompt darf nicht leer sein");

        assertThatThrownBy(() -> new GenerationRequest("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Prompt darf nicht leer sein");

        assertThatThrownBy(() -> new GenerationRequest(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Prompt darf nicht leer sein");
    }

    @Test
    @DisplayName("getStatus: should return json node history from comfy client")
    void shouldGetStatusSuccessfully() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode mockHistory = mapper.readTree("{\"prompt_id\": \"test-123\", \"status\": \"completed\"}");
        when(comfyHttpClient.getHistory("test-123")).thenReturn(Mono.just(mockHistory));

        JsonNode result = controller.getStatus("test-123").block();

        assertThat(result).isNotNull();
        assertThat(result.get("prompt_id").asText()).isEqualTo("test-123");
        assertThat(result.get("status").asText()).isEqualTo("completed");
    }

    @Test
    @DisplayName("getImage: should download asset for valid filename")
    void shouldDownloadAssetForValidFilename() {
        byte[] expectedBytes = new byte[]{1, 2, 3, 4};
        when(comfyHttpClient.downloadAsset("result_001.png")).thenReturn(Mono.just(expectedBytes));

        byte[] result = controller.getImage("result_001.png").block();

        assertThat(result).isEqualTo(expectedBytes);
        verify(comfyHttpClient, times(1)).downloadAsset("result_001.png");
    }

    @Test
    @DisplayName("getImage: should throw SecurityException when path traversal is attempted")
    void shouldThrowSecurityExceptionOnPathTraversal() {
        assertThatThrownBy(() -> controller.getImage("../secret.png"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Path traversal attempt detected");

        assertThatThrownBy(() -> controller.getImage("sub/folder/file.png"))
                .isInstanceOf(SecurityException.class);

        assertThatThrownBy(() -> controller.getImage("..\\file.png"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("getModelMetadata: should return metadata when model is found")
    void shouldReturnModelMetadataWhenFound() {
        var info = new com.thomaskippster.comfyuicompanion.service.inspector.TriggerWordService.ModelTriggerInfo(
                "flux_dev.safetensors", "FLUX", "CHECKPOINT",
                java.util.List.of("realistic", "cinematic"), java.util.Map.of("portrait", 42)
        );
        when(triggerWordService.findModelTriggerInfo("flux_dev.safetensors"))
                .thenReturn(java.util.Optional.of(info));

        var response = controller.getModelMetadata("flux_dev.safetensors").block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().architecture()).isEqualTo("FLUX");
        assertThat(response.getBody().triggerWords()).contains("realistic", "cinematic");
    }

    @Test
    @DisplayName("analyzeWorkflowNodes: should return node resolution report")
    void shouldAnalyzeWorkflowNodesSuccessfully() {
        String workflow = "{\"nodes\": [{\"type\": \"FaceDetailer\"}]}";
        var report = new com.thomaskippster.comfyuicompanion.service.provisioning.CustomNodeResolverService.NodeResolutionReport(
                java.util.Set.of("FaceDetailer"),
                java.util.Set.of("FaceDetailer"),
                java.util.List.of(new com.thomaskippster.comfyuicompanion.service.provisioning.CustomNodeResolverService.CustomNodePackage(
                        "ImpactPack", "https://github.com/ltdrdata/ComfyUI-Impact-Pack", java.util.List.of("FaceDetailer")
                ))
        );
        when(customNodeResolverService.analyzeWorkflowNodes(workflow)).thenReturn(Mono.just(report));

        var result = controller.analyzeWorkflowNodes(workflow).block();

        assertThat(result).isNotNull();
        assertThat(result.missingNodeTypes()).contains("FaceDetailer");
        assertThat(result.recommendedPackages()).hasSize(1);
        assertThat(result.recommendedPackages().get(0).packageName()).isEqualTo("ImpactPack");
    }
}

