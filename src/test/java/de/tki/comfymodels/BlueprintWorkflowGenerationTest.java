package de.tki.comfymodels;

import de.tki.comfymodels.service.PromptBlueprintApiService;
import de.tki.comfymodels.service.impl.ComfyPipelineService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BlueprintWorkflowGenerationTest {

    private PromptBlueprintApiService promptBlueprintApiService;

    @BeforeEach
    void setUp() {
        promptBlueprintApiService = new PromptBlueprintApiService();
    }

    private JSONObject processWorkflow(File workflowFile, PromptBlueprintApiService.PromptLabInputs labInputs) throws Exception {
        assertTrue(workflowFile.exists(), "Workflow file must exist: " + workflowFile.getAbsolutePath());
        String rawContent = Files.readString(workflowFile.toPath(), StandardCharsets.UTF_8);
        JSONObject uiWorkflow = new JSONObject(rawContent);

        // 1. Flatten if has subgraphs
        JSONObject flattened = ComfyPipelineService.flattenWorkflow(uiWorkflow);
        assertNotNull(flattened);

        // 2. Convert UI to API
        JSONObject apiPayload = ComfyPipelineService.convertUiToApi(flattened);
        assertNotNull(apiPayload);
        assertTrue(apiPayload.has("prompt"), "Converted API JSON must have 'prompt'");

        // 3. Extract API payload
        JSONObject mainObj = promptBlueprintApiService.extractApiPayload(apiPayload.toString());
        JSONObject promptObj = mainObj.getJSONObject("prompt");
        assertNotNull(promptObj);

        // 4. Inject Prompt Lab inputs
        promptBlueprintApiService.injectLabInputs(promptObj, labInputs);

        // 5. Sanitize model inputs (mimicking Main.java sanitization)
        sanitizePromptInputs(promptObj);

        return mainObj;
    }

    private void sanitizePromptInputs(JSONObject promptObj) {
        for (String key : promptObj.keySet()) {
            JSONObject node = promptObj.optJSONObject(key);
            if (node == null) continue;
            JSONObject inp = node.optJSONObject("inputs");
            if (inp == null) continue;
            String classType = node.optString("class_type", "");

            if ("KSamplerSelect".equals(classType)) {
                String sn = inp.optString("sampler_name", "");
                if (sn.isBlank() || sn.equalsIgnoreCase("COMBO") || sn.equalsIgnoreCase("Auto")) {
                    inp.put("sampler_name", "euler");
                }
            }

            for (String ik : new java.util.ArrayList<>(inp.keySet())) {
                Object val = inp.opt(ik);
                if (val instanceof String s && !s.isBlank()) {
                    String cleaned = s.replaceAll("[/\\\\]+", "/").trim();
                    if (cleaned.equalsIgnoreCase("COMBO") || cleaned.equalsIgnoreCase("Auto")) {
                        if (ik.equals("sampler_name") || classType.contains("Sampler")) {
                            inp.put(ik, "euler");
                        } else if (ik.equals("scheduler")) {
                            inp.put(ik, "simple");
                        }
                    }
                }
            }
        }
    }

    private void assertValidApiWorkflow(JSONObject mainObj) {
        assertTrue(mainObj.has("prompt"), "Payload must contain 'prompt' object");
        JSONObject prompt = mainObj.getJSONObject("prompt");
        assertFalse(prompt.isEmpty(), "Prompt object must not be empty");

        for (String key : prompt.keySet()) {
            JSONObject node = prompt.getJSONObject(key);
            assertTrue(node.has("class_type"), "Node " + key + " must have class_type");
            String classType = node.getString("class_type");
            assertFalse(classType.isBlank(), "class_type cannot be blank");
            assertTrue(node.has("inputs"), "Node " + key + " must have inputs");
            JSONObject inputs = node.getJSONObject("inputs");

            for (String inputKey : inputs.keySet()) {
                Object inputVal = inputs.get(inputKey);
                // Ensure no raw "COMBO" placeholder is left in sampler_name or scheduler
                if ("sampler_name".equals(inputKey) || "scheduler".equals(inputKey)) {
                    if (inputVal instanceof String str) {
                        assertNotEquals("COMBO", str, "Input '" + inputKey + "' in node " + key + " (" + classType + ") cannot be 'COMBO'");
                        assertNotEquals("Auto", str, "Input '" + inputKey + "' in node " + key + " (" + classType + ") cannot be 'Auto'");
                    }
                }
            }
        }
    }

    @Test
    void testFlux2Klein9bTextToImage() throws Exception {
        File file = new File("workflows/flux2_klein_9b_text_to_image.json");
        PromptBlueprintApiService.PromptLabInputs inputs = new PromptBlueprintApiService.PromptLabInputs(
                "A vintage motorcycle parked in front of a retro diner at sunset",
                "blurry, distortion, low quality",
                1024, 1024, 20, 3.5, 424242L,
                "Auto", "Auto", 1.0, 1, null
        );

        JSONObject result = processWorkflow(file, inputs);
        assertValidApiWorkflow(result);

        JSONObject prompt = result.getJSONObject("prompt");
        // Verify UNETLoader / CLIPLoader / KSampler / CFGGuider / VAE nodes exist
        boolean hasUnetOrSampler = prompt.keySet().stream()
                .map(prompt::getJSONObject)
                .anyMatch(n -> n.getString("class_type").contains("UNET") || n.getString("class_type").contains("Sampler") || n.getString("class_type").contains("CFGGuider"));
        assertTrue(hasUnetOrSampler);
    }

    @Test
    void testFlux2Klein9bImageEdit() throws Exception {
        File file = new File("workflows/flux2_klein_9b_image_edit.json");
        PromptBlueprintApiService.PromptLabInputs inputs = new PromptBlueprintApiService.PromptLabInputs(
                "Transform the motorcycle into a futuristic hoverbike",
                "blurry, bad anatomy",
                1024, 1024, 20, 3.5, 77777L,
                "Auto", "Auto", 1.0, 1, "test_input_image.png"
        );

        JSONObject result = processWorkflow(file, inputs);
        assertValidApiWorkflow(result);
    }

    @Test
    void testZImageTurboTextToImage() throws Exception {
        File file = new File("workflows/z-image-turbo_text_to_image.json");
        PromptBlueprintApiService.PromptLabInputs inputs = new PromptBlueprintApiService.PromptLabInputs(
                "A majestic mountain landscape in 4k",
                "blurry, low quality",
                1024, 1024, 8, 1.0, 123456L,
                "Auto", "Auto", 1.0, 1, null
        );

        JSONObject result = processWorkflow(file, inputs);
        assertValidApiWorkflow(result);
    }

    @Test
    void testZImageTextToImage() throws Exception {
        File file = new File("workflows/z-image_text_to_image.json");
        PromptBlueprintApiService.PromptLabInputs inputs = new PromptBlueprintApiService.PromptLabInputs(
                "A beautiful cybernetic city portrait",
                "lowres, bad hands",
                1024, 1024, 20, 4.0, 654321L,
                "Auto", "Auto", 1.0, 1, null
        );

        JSONObject result = processWorkflow(file, inputs);
        assertValidApiWorkflow(result);
    }

    @Test
    void testQwenImageEdit2509() throws Exception {
        File file = new File("workflows/qwen_image_edit_2509.json");
        PromptBlueprintApiService.PromptLabInputs inputs = new PromptBlueprintApiService.PromptLabInputs(
                "Make the lighting dramatically sunset style",
                "blurry, bad quality",
                1024, 1024, 20, 4.0, 99999L,
                "Auto", "Auto", 1.0, 1, "input_photo.png"
        );

        JSONObject result = processWorkflow(file, inputs);
        assertValidApiWorkflow(result);
    }

    @Test
    void testAllWorkflowsInDirectoryBulk() throws Exception {
        File dir = new File("workflows");
        File[] jsonFiles = dir.listFiles((d, name) -> name.endsWith(".json") && !name.equals("workflow_library.json"));
        assertNotNull(jsonFiles);

        int count = 0;
        for (File file : jsonFiles) {
            PromptBlueprintApiService.PromptLabInputs inputs = new PromptBlueprintApiService.PromptLabInputs(
                    "High quality detailed render",
                    "blurry, low quality",
                    1024, 1024, 20, 3.5, 54321L,
                    "Auto", "Auto", 1.0, 1, "sample_input.png"
            );

            try {
                JSONObject result = processWorkflow(file, inputs);
                assertValidApiWorkflow(result);
                count++;
            } catch (Exception e) {
                fail("Failed validating workflow: " + file.getName() + " -> " + e.getMessage());
            }
        }
        assertTrue(count >= 30, "Should have successfully validated at least 30 workflows in directory");
    }
}
