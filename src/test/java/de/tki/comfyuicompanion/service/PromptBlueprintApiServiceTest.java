package de.tki.comfyuicompanion.service;

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

    @Test
    void testInjectLabInputs_KSamplerSelect_replacesComboWithEuler() {
        String inputJson = """
        {
          "75:61": {
            "class_type": "KSamplerSelect",
            "inputs": {
              "sampler_name": "COMBO"
            }
          },
          "75:62": {
            "class_type": "Flux2Scheduler",
            "inputs": {
              "scheduler": "COMBO"
            }
          }
        }
        """;

        JSONObject promptObj = new JSONObject(inputJson);
        PromptBlueprintApiService.PromptLabInputs inputs = new PromptBlueprintApiService.PromptLabInputs(
                "test prompt", 1024, 1024, 20, 3.5, 12345L
        );

        service.injectLabInputs(promptObj, inputs);

        assertEquals("euler", promptObj.getJSONObject("75:61").getJSONObject("inputs").getString("sampler_name"));
        assertEquals("normal", promptObj.getJSONObject("75:62").getJSONObject("inputs").getString("scheduler"));
    }

    @Test
    void testCleanNonNodeKeys_removesMetadataAndPrimitiveFields() {
        JSONObject promptObj = new JSONObject();
        promptObj.put("last_node_id", 42);
        promptObj.put("last_link_id", 100);
        promptObj.put("version", 0.4);
        promptObj.put("some_string", "metadata");
        promptObj.put("nodes", new org.json.JSONArray());

        JSONObject validNode = new JSONObject();
        validNode.put("class_type", "KSampler");
        validNode.put("inputs", new JSONObject());
        promptObj.put("3", validNode);

        JSONObject nodeWithoutClassType = new JSONObject();
        nodeWithoutClassType.put("title", "orphan node");
        promptObj.put("99", nodeWithoutClassType);

        PromptBlueprintApiService.cleanNonNodeKeys(promptObj);

        assertEquals(1, promptObj.length());
        assertTrue(promptObj.has("3"));
        assertEquals("KSampler", promptObj.getJSONObject("3").getString("class_type"));
        assertFalse(promptObj.has("last_node_id"));
        assertFalse(promptObj.has("last_link_id"));
        assertFalse(promptObj.has("version"));
        assertFalse(promptObj.has("some_string"));
        assertFalse(promptObj.has("nodes"));
        assertFalse(promptObj.has("99"));
    }

    @Test
    void testFindModelInObjectInfo_resolvesSubfolderPathForVae() {
        String objectInfoJson = """
        {
          "VAELoader": {
            "input": {
              "required": {
                "vae_name": [
                  [
                    "FLUX1\\\\ae.safetensors",
                    "SD1.5\\\\vae-ft-mse-840000-ema-pruned.safetensors",
                    "flux2-vae.safetensors",
                    "pixel_space"
                  ]
                ]
              }
            }
          }
        }
        """;

        JSONObject objectInfo = new JSONObject(objectInfoJson);

        String matched = PromptBlueprintApiService.findModelInObjectInfo("VAELoader", "vae_name", "ae.safetensors", objectInfo);
        assertEquals("FLUX1\\ae.safetensors", matched);

        JSONObject promptObj = new JSONObject();
        JSONObject vaeNode = new JSONObject();
        vaeNode.put("class_type", "VAELoader");
        JSONObject inputs = new JSONObject();
        inputs.put("vae_name", "ae.safetensors");
        vaeNode.put("inputs", inputs);
        promptObj.put("10029", vaeNode);

        PromptBlueprintApiService.sanitizeModelInputs(promptObj, objectInfo);

        assertEquals("FLUX1\\ae.safetensors", promptObj.getJSONObject("10029").getJSONObject("inputs").getString("vae_name"));
    }
}