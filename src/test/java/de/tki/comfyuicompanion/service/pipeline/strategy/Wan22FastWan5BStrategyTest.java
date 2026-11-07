package de.tki.comfyuicompanion.service.pipeline.strategy;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.pipeline.ComfyNodeInspectionClient;
import de.tki.comfyuicompanion.service.pipeline.FastWanWorkflowBuilder;
import de.tki.comfyuicompanion.service.pipeline.SceneBlueprintInjector;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit test suite verifying the behavior of {@link Wan22FastWan5BStrategy},
 * particularly ensuring robust custom node validation against ComfyUI servers
 * and automatic fallback to zero-dependency native ComfyUI graphs when custom nodes are missing.
 */
class Wan22FastWan5BStrategyTest {

    private FastWanWorkflowBuilder fastWanWorkflowBuilder;
    private SceneBlueprintInjector blueprintInjector;
    private ConfigService configService;
    private ComfyNodeInspectionClient inspectionClient;
    private Wan22FastWan5BStrategy strategy;

    @BeforeEach
    void setUp() {
        fastWanWorkflowBuilder = Mockito.mock(FastWanWorkflowBuilder.class);
        blueprintInjector = Mockito.mock(SceneBlueprintInjector.class);
        configService = Mockito.mock(ConfigService.class);
        inspectionClient = Mockito.mock(ComfyNodeInspectionClient.class);

        strategy = new Wan22FastWan5BStrategy(
                fastWanWorkflowBuilder,
                blueprintInjector,
                configService,
                inspectionClient
        );
    }

    @Test
    @DisplayName("Should bypass blueprint and use native builder when blueprint contains uninstalled custom nodes like GetNode")
    void shouldBypassBlueprintWhenCustomNodesMissingOnServer() throws Exception {
        Scene scene = new Scene();
        scene.setPrompt("A cybernetic rover exploring Titan");

        JSONObject blueprintWithMissingNodes = new JSONObject();
        blueprintWithMissingNodes.put("496", new JSONObject().put("class_type", "GetNode"));
        blueprintWithMissingNodes.put("500", new JSONObject().put("class_type", "WanImageToVideo"));

        when(blueprintInjector.findBlueprintByKeywords(any(), any(), any(), any()))
                .thenReturn(blueprintWithMissingNodes);

        // Server only has core nodes, NOT GetNode
        when(inspectionClient.fetchAvailableNodeClasses(anyString()))
                .thenReturn(Set.of("WanImageToVideo", "KSampler", "VAELoader"));

        // Blueprint injector reports not all nodes available
        when(blueprintInjector.areAllNodesAvailable(any(), any()))
                .thenReturn(false);

        JSONObject nativeFallbackWorkflow = new JSONObject().put("10", new JSONObject().put("class_type", "VAELoader"));
        when(fastWanWorkflowBuilder.generateFastWanWorkflowJson(any(), any(), any(), any(Long.class), any(), any(), any(), any(), any()))
                .thenReturn(nativeFallbackWorkflow);

        JSONObject result = strategy.buildWorkflow(
                "http://127.0.0.1:8188",
                scene,
                null,
                42L,
                "prefix",
                List.of("Wan2_2-TI2V-5B-FastWanFullAttn_bf16.safetensors"),
                List.of(),
                List.of("umt5_xxl_fp8_e4m3fn_scaled.safetensors"),
                List.of("wan_2.1_vae.safetensors")
        );

        assertNotNull(result);
        assertEquals(nativeFallbackWorkflow, result, "Should have fallen back to native FastWan workflow builder");
    }

    @Test
    @DisplayName("Should use blueprint when all declared node classes exist on the ComfyUI server")
    void shouldUseBlueprintWhenAllNodesAvailable() throws Exception {
        Scene scene = new Scene();
        scene.setPrompt("A peaceful Japanese garden");

        JSONObject compatibleBlueprint = new JSONObject();
        compatibleBlueprint.put("1", new JSONObject().put("class_type", "WanImageToVideo"));

        when(blueprintInjector.findBlueprintByKeywords(any(), any(), any(), any()))
                .thenReturn(compatibleBlueprint);

        when(inspectionClient.fetchAvailableNodeClasses(anyString()))
                .thenReturn(Set.of("WanImageToVideo"));

        when(blueprintInjector.areAllNodesAvailable(compatibleBlueprint, Set.of("WanImageToVideo")))
                .thenReturn(true);

        JSONObject result = strategy.buildWorkflow(
                "http://127.0.0.1:8188",
                scene,
                null,
                42L,
                "prefix",
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        assertNotNull(result);
        assertEquals(compatibleBlueprint, result, "Should have used the compatible blueprint");
    }
}
