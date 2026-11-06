package de.tki.comfyuicompanion.service.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfyuicompanion.controller.PromptLabModelInputSanitizer;
import de.tki.comfyuicompanion.service.PromptBlueprintApiService;
import de.tki.comfyuicompanion.service.gateway.ComfyUiGatewayImpl;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link WorkflowTransformationService}, {@link SubgraphFlattener},
 * {@link ComfyUiGatewayImpl}, and {@link PromptLabModelInputSanitizer}, verifying
 * generic subgraph flattening, named widget resolution, and {@code /object_info}
 * schema-driven default backfilling without hardcoded node class names.
 */
class WorkflowTransformationServiceTest {

    @Test
    void shouldPopulateComfySwitchAndModelAttentionBackendInMiniMaxSubgraphWithoutHardcoding() throws Exception {
        String rawJson;
        Path workflowPath = Path.of("user_workflows", "minimax_h3_text_to_image.json");
        if (Files.exists(workflowPath)) {
            rawJson = Files.readString(workflowPath, StandardCharsets.UTF_8);
        } else {
            try (var is = getClass().getResourceAsStream("/fixtures/minimax_h3_text_to_image.json")) {
                assertNotNull(is, "MiniMax H3 text-to-image workflow fixture should exist");
                rawJson = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        JSONObject uiWorkflow = new JSONObject(rawJson);

        WorkflowTransformationService service = new WorkflowTransformationService(null);
        JSONObject flattened = service.flattenWorkflow(uiWorkflow);
        JSONObject apiPayload = service.convertUiToApi(flattened);

        JSONObject prompt = apiPayload.getJSONObject("prompt");

        // Verify ComfySwitchNode (10052) receives required 'switch' boolean input via generic named/widget resolution
        assertTrue(prompt.has("10052"), "Flattened prompt must contain ComfySwitchNode 10052");
        JSONObject switchInputs = prompt.getJSONObject("10052").getJSONObject("inputs");
        assertTrue(switchInputs.has("switch"), "ComfySwitchNode 10052 must have 'switch' input populated");
        assertFalse(switchInputs.getBoolean("switch"), "Default switch value should be false");
        assertTrue(switchInputs.has("on_false"), "ComfySwitchNode 10052 must retain on_false link");
        assertTrue(switchInputs.has("on_true"), "ComfySwitchNode 10052 must retain on_true link");

        // Verify ModelAttentionBackend (10044) receives required 'attention' string input
        assertTrue(prompt.has("10044"), "Flattened prompt must contain ModelAttentionBackend 10044");
        JSONObject attnInputs = prompt.getJSONObject("10044").getJSONObject("inputs");
        assertEquals("comfy kitchen attention", attnInputs.optString("attention"),
                "ModelAttentionBackend 10044 must have 'attention' input populated");
        assertTrue(attnInputs.has("model"), "ModelAttentionBackend 10044 must retain model link");

        // Verify ResolutionSelector (79) receives its named widget parameters
        assertTrue(prompt.has("79"), "Prompt must contain ResolutionSelector 79");
        JSONObject resInputs = prompt.getJSONObject("79").getJSONObject("inputs");
        assertEquals("16:9 (Widescreen)", resInputs.optString("aspect_ratio"));
        assertEquals(8, resInputs.optInt("multiple"));
    }

    @Test
    void shouldBackfillMissingInputsFromSubgraphDefinitionsOnExistingApiPayload() {
        String brokenPayloadJson = """
                {
                  "prompt": {
                    "10052": {
                      "class_type": "ComfySwitchNode",
                      "inputs": {
                        "on_false": ["10006", 0],
                        "on_true": ["10041", 0]
                      }
                    },
                    "10044": {
                      "class_type": "ModelAttentionBackend",
                      "inputs": {
                        "model": ["10052", 0]
                      }
                    }
                  },
                  "definitions": {
                    "subgraphs": [{
                      "id": "26ae0bb9-8bee-49c8-bca0-2897ee957e33",
                      "nodes": [
                        {
                          "id": 52,
                          "type": "ComfySwitchNode",
                          "widgets_values_named": {"switch": false}
                        },
                        {
                          "id": 44,
                          "type": "ModelAttentionBackend",
                          "widgets_values_named": {"attention": "comfy kitchen attention"}
                        }
                      ]
                    }]
                  }
                }
                """;

        JSONObject parsed = new JSONObject(brokenPayloadJson);
        JSONObject promptToQueue = PromptBlueprintApiService.extractPromptToQueue(parsed);

        assertFalse(promptToQueue.getJSONObject("10052").getJSONObject("inputs").getBoolean("switch"));
        assertEquals("comfy kitchen attention",
                promptToQueue.getJSONObject("10044").getJSONObject("inputs").optString("attention"));
    }

    @Test
    void shouldGenericallyBackfillRequiredDefaultsFromComfyObjectInfoSchema() throws Exception {
        String objectInfoJson = """
                {
                  "ComfySwitchNode": {
                    "input": {
                      "required": {
                        "switch": ["BOOLEAN", {"default": false}],
                        "on_false": ["*", {"lazy": true}],
                        "on_true": ["*", {"lazy": true}]
                      }
                    }
                  },
                  "ModelAttentionBackend": {
                    "input": {
                      "required": {
                        "model": ["MODEL"],
                        "attention": ["COMBO", {"options": ["default", "comfy kitchen attention"], "default": "comfy kitchen attention"}]
                      }
                    }
                  }
                }
                """;

        ObjectMapper mapper = new ObjectMapper();
        var rootNode = mapper.readTree(objectInfoJson);
        assertFalse(ComfyUiGatewayImpl.isWidgetInputSpec(
                rootNode.path("ModelAttentionBackend").path("input").path("required").path("model")));
        assertTrue(ComfyUiGatewayImpl.isWidgetInputSpec(
                rootNode.path("ModelAttentionBackend").path("input").path("required").path("attention")));

        JSONObject prompt = new JSONObject("""
                {
                  "10052": {
                    "class_type": "ComfySwitchNode",
                    "inputs": {
                      "on_false": ["10006", 0],
                      "on_true": ["10041", 0]
                    }
                  },
                  "10044": {
                    "class_type": "ModelAttentionBackend",
                    "inputs": {
                      "model": ["10052", 0]
                    }
                  }
                }
                """);

        PromptLabModelInputSanitizer sanitizer = new PromptLabModelInputSanitizer();
        sanitizer.sanitizeModelInputsInPrompt(prompt, new JSONObject(objectInfoJson), null, null);

        assertFalse(prompt.getJSONObject("10052").getJSONObject("inputs").getBoolean("switch"));
        assertEquals("comfy kitchen attention",
                prompt.getJSONObject("10044").getJSONObject("inputs").optString("attention"));
    }
}
