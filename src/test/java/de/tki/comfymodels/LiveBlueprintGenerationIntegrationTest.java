package de.tki.comfymodels;

import de.tki.comfymodels.service.PromptBlueprintApiService;
import de.tki.comfymodels.service.impl.ComfyPipelineService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import static org.junit.jupiter.api.Assertions.*;

@Disabled("Live integration test requiring active ComfyUI server with specific checkpoint models installed")
public class LiveBlueprintGenerationIntegrationTest {

    private final String serverUrl = "http://127.0.0.1:8188";
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private PromptBlueprintApiService promptBlueprintApiService;
    private JSONObject objectInfo;

    @BeforeEach
    void setUp() {
        promptBlueprintApiService = new PromptBlueprintApiService();

        try {
            // Fetch live object_info from ComfyUI
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/object_info"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            org.junit.jupiter.api.Assumptions.assumeTrue(response.statusCode() == 200, "ComfyUI returned non-200 status on /object_info");
            objectInfo = new JSONObject(response.body());
        } catch (Exception e) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "ComfyUI server is offline at " + serverUrl + " - skipping live integration test: " + e.getMessage());
        }
    }

    private JSONObject preparePayload(File workflowFile, PromptBlueprintApiService.PromptLabInputs labInputs) throws Exception {
        String rawContent = Files.readString(workflowFile.toPath(), StandardCharsets.UTF_8);
        JSONObject uiWorkflow = new JSONObject(rawContent);

        // Flatten subgraphs
        JSONObject flattened = ComfyPipelineService.flattenWorkflow(uiWorkflow);

        // Convert UI to API
        JSONObject apiPayload = ComfyPipelineService.convertUiToApi(flattened);

        // Extract API payload
        JSONObject mainObj = promptBlueprintApiService.extractApiPayload(apiPayload.toString());
        JSONObject promptObj = mainObj.getJSONObject("prompt");

        // Inject Lab Inputs
        promptBlueprintApiService.injectLabInputs(promptObj, labInputs);

        // Sanitize model inputs using live objectInfo
        sanitizeModelInputsInPrompt(promptObj, objectInfo, labInputs);

        return mainObj;
    }

    private void sanitizeModelInputsInPrompt(JSONObject promptObj, JSONObject objInfo, PromptBlueprintApiService.PromptLabInputs inputs) {
        if (promptObj == null) return;
        for (String key : promptObj.keySet()) {
            JSONObject node = promptObj.optJSONObject(key);
            if (node == null) continue;
            JSONObject inp = node.optJSONObject("inputs");
            if (inp == null) continue;
            String classType = node.optString("class_type", "");

            if ("KSamplerSelect".equals(classType)) {
                String sn = inp.optString("sampler_name", "");
                if (sn.isBlank() || sn.equalsIgnoreCase("COMBO") || sn.equalsIgnoreCase("Auto")) {
                    String uiSampler = inputs.samplerName();
                    inp.put("sampler_name", (uiSampler != null && !uiSampler.equalsIgnoreCase("Auto") && !uiSampler.equalsIgnoreCase("COMBO")) ? uiSampler : "euler");
                }
            }

            for (String ik : new java.util.ArrayList<>(inp.keySet())) {
                Object val = inp.opt(ik);
                if (val instanceof String s && !s.isBlank()) {
                    String cleaned = s.replaceAll("[/\\\\]+", "/").trim();
                    if (s.startsWith("/") || s.startsWith("\\")) {
                        cleaned = "/" + cleaned.replaceAll("^/+", "");
                    }

                    if (cleaned.equalsIgnoreCase("COMBO") || cleaned.equalsIgnoreCase("Auto")) {
                        if (ik.equals("sampler_name") || classType.contains("Sampler")) {
                            String uiSampler = inputs.samplerName();
                            inp.put(ik, (uiSampler != null && !uiSampler.equalsIgnoreCase("Auto") && !uiSampler.equalsIgnoreCase("COMBO")) ? uiSampler : "euler");
                            continue;
                        } else if (ik.equals("scheduler")) {
                            String uiScheduler = inputs.scheduler();
                            inp.put(ik, (uiScheduler != null && !uiScheduler.equalsIgnoreCase("Auto") && !uiScheduler.equalsIgnoreCase("COMBO")) ? uiScheduler : "simple");
                            continue;
                        }
                    }

                    boolean isModelKey = ik.endsWith("_name") || ik.endsWith("_path") || ik.equals("model") || ik.equals("vae") || ik.equals("clip") || ik.equals("unet") || ik.equals("lora_name");
                    boolean isModelFile = cleaned.endsWith(".safetensors") || cleaned.endsWith(".ckpt") || cleaned.endsWith(".pt") || cleaned.endsWith(".bin") || cleaned.endsWith(".onnx") || cleaned.endsWith(".sft");

                    if (isModelKey || isModelFile) {
                        String matched = findModelInObjectInfo(classType, ik, cleaned, objInfo);
                        if (matched != null) {
                            inp.put(ik, matched);
                        } else {
                            if (ik.equals("sampler_name") && cleaned.equalsIgnoreCase("COMBO")) {
                                inp.put(ik, "euler");
                            } else if (ik.equals("scheduler") && cleaned.equalsIgnoreCase("COMBO")) {
                                inp.put(ik, "normal");
                            } else {
                                inp.put(ik, cleaned);
                            }
                        }
                    }
                }
            }
        }
    }

    private String findModelInObjectInfo(String classType, String inputKey, String targetValue, JSONObject objectInfo) {
        if (targetValue == null || targetValue.isBlank() || classType == null || objectInfo == null) return null;
        if (!objectInfo.has(classType)) return null;

        JSONObject nodeInfo = objectInfo.getJSONObject(classType);
        JSONObject input = nodeInfo.optJSONObject("input");
        if (input == null) return null;

        JSONObject required = input.optJSONObject("required");
        JSONObject optional = input.optJSONObject("optional");

        Object valObj = null;
        if (required != null && required.has(inputKey)) valObj = required.get(inputKey);
        else if (optional != null && optional.has(inputKey)) valObj = optional.get(inputKey);

        if (valObj instanceof JSONArray outerArray && outerArray.length() > 0) {
            Object firstElement = outerArray.get(0);
            JSONArray options = null;
            if (firstElement instanceof JSONArray) {
                options = (JSONArray) firstElement;
            } else if (firstElement instanceof String firstStr && firstStr.equalsIgnoreCase("COMBO") && outerArray.length() > 1 && outerArray.get(1) instanceof JSONObject configObj) {
                options = configObj.optJSONArray("options");
            } else if (firstElement instanceof String && !"COMBO".equalsIgnoreCase((String) firstElement)) {
                options = outerArray;
            }

            if (options != null) {
                java.util.Set<String> optSet = new java.util.HashSet<>();
                for (int i = 0; i < options.length(); i++) {
                    if (options.get(i) instanceof String s && !s.equalsIgnoreCase("COMBO")) optSet.add(s);
                }

                String targetClean = targetValue.replaceAll("[/\\\\]+", "/").toLowerCase();
                String targetFileName = targetClean.contains("/") ? targetClean.substring(targetClean.lastIndexOf('/') + 1) : targetClean;
                String targetNoExt = targetFileName.contains(".") ? targetFileName.substring(0, targetFileName.lastIndexOf('.')) : targetFileName;

                for (String opt : optSet) {
                    if (opt.replaceAll("[/\\\\]+", "/").toLowerCase().equals(targetClean)) return opt;
                }
                for (String opt : optSet) {
                    String optClean = opt.replaceAll("[/\\\\]+", "/").toLowerCase();
                    String optFileName = optClean.contains("/") ? optClean.substring(optClean.lastIndexOf('/') + 1) : optClean;
                    if (optFileName.equals(targetFileName)) return opt;
                }
                for (String opt : optSet) {
                    String optClean = opt.replaceAll("[/\\\\]+", "/").toLowerCase();
                    if (optClean.endsWith("/" + targetFileName)) return opt;
                }
                for (String opt : optSet) {
                    String optClean = opt.replaceAll("[/\\\\]+", "/").toLowerCase();
                    String optFileName = optClean.contains("/") ? optClean.substring(optClean.lastIndexOf('/') + 1) : optClean;
                    String optNoExt = optFileName.contains(".") ? optFileName.substring(0, optFileName.lastIndexOf('.')) : optFileName;
                    if (targetNoExt.length() > 3 && optNoExt.length() > 3) {
                        if (optNoExt.contains(targetNoExt) || targetNoExt.contains(optNoExt)) return opt;
                    }
                }
                if (!optSet.isEmpty()) {
                    return optSet.iterator().next();
                }
            }
        }
        return null;
    }

    private String queuePromptAndAwait(JSONObject payload, int timeoutSeconds) throws Exception {
        System.out.println("PAYLOAD:\n" + payload.toString(2));
        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/prompt"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> postResponse = httpClient.send(postRequest, HttpResponse.BodyHandlers.ofString());
        if (postResponse.statusCode() != 200) {
            fail("ComfyUI /prompt rejected with status " + postResponse.statusCode() + ": " + postResponse.body());
        }

        JSONObject respJson = new JSONObject(postResponse.body());
        String promptId = respJson.getString("prompt_id");
        assertNotNull(promptId);
        System.out.println("🚀 Queued prompt: " + promptId);

        // Poll history until complete
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutSeconds * 1000L) {
            Thread.sleep(2000);
            HttpRequest histRequest = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/history/" + promptId))
                    .GET()
                    .build();
            HttpResponse<String> histResponse = httpClient.send(histRequest, HttpResponse.BodyHandlers.ofString());
            if (histResponse.statusCode() == 200) {
                JSONObject histObj = new JSONObject(histResponse.body());
                if (histObj.has(promptId)) {
                    JSONObject taskData = histObj.getJSONObject(promptId);
                    JSONObject status = taskData.optJSONObject("status");
                    if (status != null) {
                        boolean completed = status.optBoolean("completed", false);
                        String statusStr = status.optString("status_str", "");
                        if ("success".equalsIgnoreCase(statusStr) || completed) {
                            System.out.println("✅ Execution finished successfully for prompt: " + promptId);
                            JSONObject outputs = taskData.optJSONObject("outputs");
                            System.out.println("🖼️ Outputs generated: " + (outputs != null ? outputs.keySet() : "none"));
                            return promptId;
                        } else if ("error".equalsIgnoreCase(statusStr)) {
                            JSONArray messages = status.optJSONArray("messages");
                            fail("Prompt execution failed with error: " + (messages != null ? messages.toString() : "unknown"));
                        }
                    }
                }
            }
        }
        fail("Timed out waiting for prompt execution: " + promptId);
        return promptId;
    }

    @Test
    void testLiveGeneration_Flux2Klein9bTextToImage() throws Exception {
        System.out.println("\n========================================================");
        System.out.println("TESTING LIVE: 1/5 Flux.2 [Klein] 9B: Text to Image");
        System.out.println("========================================================");

        File file = new File("workflows/flux2_klein_9b_text_to_image.json");
        PromptBlueprintApiService.PromptLabInputs inputs = new PromptBlueprintApiService.PromptLabInputs(
                "A vivid glowing neon cyber fox in a night alley, hyperrealistic, sharp focus",
                "blurry, low quality, distortion",
                512, 512, 4, 3.5, 888123L,
                "euler", "simple", 1.0, 1, null
        );

        JSONObject payload = preparePayload(file, inputs);
        queuePromptAndAwait(payload, 180);
    }

    @Test
    void testLiveGeneration_ZImageTurboTextToImage() throws Exception {
        System.out.println("\n========================================================");
        System.out.println("TESTING LIVE: 2/5 Z-Image-Turbo: Text to Image");
        System.out.println("========================================================");

        File file = new File("workflows/z-image-turbo_text_to_image.json");
        PromptBlueprintApiService.PromptLabInputs inputs = new PromptBlueprintApiService.PromptLabInputs(
                "A crystal clear turquoise alpine lake surrounded by snow-capped peaks, golden hour sunlight",
                "blurry, watermark, distorted",
                512, 512, 8, 1.0, 444555L,
                "euler", "simple", 1.0, 1, null
        );

        JSONObject payload = preparePayload(file, inputs);
        queuePromptAndAwait(payload, 180);
    }

    @Test
    void testLiveGeneration_ZImageTextToImage() throws Exception {
        System.out.println("\n========================================================");
        System.out.println("TESTING LIVE: 3/5 Z-Image: Text to Image");
        System.out.println("========================================================");

        File file = new File("workflows/z-image_text_to_image.json");
        PromptBlueprintApiService.PromptLabInputs inputs = new PromptBlueprintApiService.PromptLabInputs(
                "An astronaut standing on Mars observing a solar eclipse, retro sci-fi art style",
                "blurry, ugly, bad hands",
                512, 512, 10, 4.0, 123987L,
                "euler", "simple", 1.0, 1, null
        );

        JSONObject payload = preparePayload(file, inputs);
        queuePromptAndAwait(payload, 180);
    }

    @Test
    void testLiveGeneration_QwenImageEdit2509() throws Exception {
        System.out.println("\n========================================================");
        System.out.println("TESTING LIVE: 4/5 Qwen Image Edit 2509");
        System.out.println("========================================================");

        File file = new File("workflows/qwen_image_edit_2509.json");
        PromptBlueprintApiService.PromptLabInputs inputs = new PromptBlueprintApiService.PromptLabInputs(
                "Add glowing stars and aurora borealis to the background sky",
                "blurry, distortion",
                512, 512, 4, 3.5, 666777L,
                "euler", "simple", 1.0, 1, "test_companion_input.png"
        );

        JSONObject payload = preparePayload(file, inputs);
        queuePromptAndAwait(payload, 180);
    }

    @Test
    void testLiveGeneration_Flux2Klein9bImageEdit() throws Exception {
        System.out.println("\n========================================================");
        System.out.println("TESTING LIVE: 5/5 Flux.2 [Klein] 9B: Image Edit");
        System.out.println("========================================================");

        File file = new File("workflows/flux2_klein_9b_image_edit.json");
        PromptBlueprintApiService.PromptLabInputs inputs = new PromptBlueprintApiService.PromptLabInputs(
                "Turn the golden circle into a glowing magical orb with energy sparks",
                "blurry, low quality",
                512, 512, 4, 3.5, 999111L,
                "euler", "simple", 1.0, 1, "test_companion_input.png"
        );

        JSONObject payload = preparePayload(file, inputs);
        queuePromptAndAwait(payload, 180);
    }

    @Test
    void testLiveSceneGeneration_VideoArchitect() throws Exception {
        System.out.println("\n========================================================");
        System.out.println("TESTING LIVE: Video Architect Scene Generation");
        System.out.println("========================================================");

        de.tki.comfymodels.service.impl.ConfigService configService = org.mockito.Mockito.mock(de.tki.comfymodels.service.impl.ConfigService.class);
        org.mockito.Mockito.when(configService.getComfyUIUrl()).thenReturn(serverUrl);
        org.mockito.Mockito.when(configService.getFfmpegPath()).thenReturn("tools/ffmpeg/ffmpeg.exe");
        org.mockito.Mockito.when(configService.getResolvedOutputDir()).thenReturn(new File("output").getAbsolutePath());

        System.out.println("Live UNETs: " + objectInfo.optJSONObject("UNETLoader"));
        System.out.println("Live CLIPs: " + objectInfo.optJSONObject("CLIPLoader"));
        System.out.println("Live VAEs: " + objectInfo.optJSONObject("VAELoader"));
        System.out.println("Live Checkpoints: " + objectInfo.optJSONObject("CheckpointLoaderSimple"));

        ComfyPipelineService pipelineService = new ComfyPipelineService(configService, httpClient);
        try {
            java.lang.reflect.Field f1 = pipelineService.getClass().getDeclaredField("processTracker");
            f1.setAccessible(true);
            f1.set(pipelineService, new de.tki.comfymodels.service.impl.ProcessTracker());
        } catch (Exception ignored) {}

        de.tki.comfymodels.domain.Scene scene = new de.tki.comfymodels.domain.Scene(
                "S1_LIVE_TEST",
                "A majestic soaring eagle over mist-covered pine mountains at sunrise, ultra high quality cinematic lighting, 8k",
                0,
                150,
                null,
                null
        );
        scene.setSteps(8);
        scene.setCfgScale(1.0);

        File videoOutput = pipelineService.generateSceneStrict(scene).join();
        assertNotNull(videoOutput, "Video output must not be null");
        assertTrue(videoOutput.exists(), "Video output file must exist");
        assertTrue(videoOutput.length() > 1024, "Video output must be larger than 1KB, size: " + videoOutput.length());
        System.out.println("✅ Live Video Architect Scene generated successfully: " + videoOutput.getAbsolutePath() + " (Size: " + videoOutput.length() + " bytes)");
    }
}
