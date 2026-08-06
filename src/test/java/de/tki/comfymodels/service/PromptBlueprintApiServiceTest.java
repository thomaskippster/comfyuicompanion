package de.tki.comfymodels.service;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromptBlueprintApiServiceTest {

    private PromptBlueprintApiService service;

    @BeforeEach
    void setUp() {
        service = new PromptBlueprintApiService();
    }

    @Test
    void testInjectLabInputs_positivePromptAndDimensionsAndSampler() {
        String inputJson = """
        {
          "3": {
            "class_type": "KSampler",
            "inputs": {
              "steps": 20,
              "cfg": 8.0,
              "seed": 12345
            }
          },
          "5": {
            "class_type": "EmptyLatentImage",
            "inputs": {
              "width": 512,
              "height": 512
            }
          },
          "6": {
            "class_type": "CLIPTextEncode",
            "inputs": {
              "text": "old prompt"
            }
          },
          "7": {
            "class_type": "CLIPTextEncode",
            "inputs": {
              "text": "bad quality, blurry"
            }
          }
        }
        """;

        JSONObject promptObj = new JSONObject(inputJson);
        PromptBlueprintApiService.PromptLabInputs inputs = new PromptBlueprintApiService.PromptLabInputs(
                "cyberpunk cat in neon city",
                1024,
                768,
                30,
                5.5,
                999999L
        );

        service.injectLabInputs(promptObj, inputs);

        // Verify positive CLIPTextEncode updated, but negative left untouched
        assertEquals("cyberpunk cat in neon city", promptObj.getJSONObject("6").getJSONObject("inputs").getString("text"));
        assertEquals("bad quality, blurry", promptObj.getJSONObject("7").getJSONObject("inputs").getString("text"));

        // Verify Latent Image dimensions updated
        assertEquals(1024, promptObj.getJSONObject("5").getJSONObject("inputs").getInt("width"));
        assertEquals(768, promptObj.getJSONObject("5").getJSONObject("inputs").getInt("height"));

        // Verify KSampler updated
        assertEquals(30, promptObj.getJSONObject("3").getJSONObject("inputs").getInt("steps"));
        assertEquals(5.5, promptObj.getJSONObject("3").getJSONObject("inputs").getDouble("cfg"));
        assertEquals(999999L, promptObj.getJSONObject("3").getJSONObject("inputs").getLong("seed"));
    }

    @Test
    void testBuildApiPayload_wrapsAndInjectsCorrectly() {
        String inputPayload = """
        {
          "prompt": {
            "10": {
              "class_type": "FluxGuidance",
              "inputs": {
                "guidance": 3.5,
                "noise_seed": 42
              }
            }
          }
        }
        """;

        PromptBlueprintApiService.PromptLabInputs inputs = new PromptBlueprintApiService.PromptLabInputs(
                "", 512, 512, 20, 2.5, 777L
        );

        String resultJson = service.buildApiPayload(inputPayload, inputs);
        JSONObject resultObj = new JSONObject(resultJson);

        assertTrue(resultObj.has("prompt"));
        JSONObject guidanceInputs = resultObj.getJSONObject("prompt").getJSONObject("10").getJSONObject("inputs");
        assertEquals(2.5, guidanceInputs.getDouble("guidance"));
        assertEquals(777L, guidanceInputs.getLong("noise_seed"));
    }

    @Test
    void testExtractApiPayload_fromGraphToPromptOutput() {
        String graphToPromptOutput = """
        {
          "output": {
            "3": {
              "class_type": "KSampler",
              "inputs": { "steps": 20 }
            }
          },
          "workflow": {
            "nodes": [{ "id": 3, "type": "KSampler" }]
          }
        }
        """;

        JSONObject result = service.extractApiPayload(graphToPromptOutput);

        assertTrue(result.has("prompt"));
        JSONObject promptObj = result.getJSONObject("prompt");
        assertFalse(promptObj.has("output"));
        assertFalse(promptObj.has("workflow"));
        assertTrue(promptObj.has("3"));
        assertEquals("KSampler", promptObj.getJSONObject("3").getString("class_type"));

        assertTrue(result.has("extra_data"));
        JSONObject extraPngInfo = result.getJSONObject("extra_data").getJSONObject("extra_pnginfo");
        assertTrue(extraPngInfo.has("workflow"));
    }
}