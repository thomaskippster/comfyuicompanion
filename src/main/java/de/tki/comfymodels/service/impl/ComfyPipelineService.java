package de.tki.comfymodels.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tki.comfymodels.domain.Scene;
import de.tki.comfymodels.service.IComfyLifecycleService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class ComfyPipelineService {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ComfyPipelineService.class);

    public static final class ComfyOutputRef {
        public final String filename;
        public final String subfolder;
        public final String type;

        public ComfyOutputRef(String filename, String subfolder, String type) {
            this.filename = filename;
            this.subfolder = subfolder != null ? subfolder : "";
            this.type = type != null ? type : "output";
        }
    }

    private static final java.util.Set<String> ATTEMPTED_INSTALLS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final HttpClient httpClient;
    private final ConfigService configService;
    @Autowired(required = false)
    private ProcessTracker processTracker;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private IComfyLifecycleService lifecycleService;
    
    @Autowired(required = false)
    private de.tki.comfymodels.service.IModelArchitectureService modelArchitectureService;

    @Autowired(required = false)
    private EnvironmentBootstrapperImpl bootstrapper;

    // Fallback default API workflow if template is missing or invalid
    private static final String DEFAULT_API_TEMPLATE = "{\n" +
            "  \"prompt\": {\n" +
            "    \"3\": {\n" +
            "      \"inputs\": {\n" +
            "        \"seed\": 42,\n" +
            "        \"steps\": 20,\n" +
            "        \"cfg\": 8.0,\n" +
            "        \"sampler_name\": \"euler\",\n" +
            "        \"scheduler\": \"normal\",\n" +
            "        \"denoise\": 1.0,\n" +
            "        \"model\": [\"10\", 0],\n" +
            "        \"positive\": [\"6\", 0],\n" +
            "        \"negative\": [\"7\", 0],\n" +
            "        \"latent_image\": [\"5\", 0]\n" +
            "      },\n" +
            "      \"class_type\": \"KSampler\"\n" +
            "    },\n" +
            "    \"4\": {\n" +
            "      \"inputs\": {\n" +
            "        \"ckpt_name\": \"v1-5-pruned-emaonly.safetensors\"\n" +
            "      },\n" +
            "      \"class_type\": \"CheckpointLoaderSimple\"\n" +
            "    },\n" +
            "    \"5\": {\n" +
            "      \"inputs\": {\n" +
            "        \"width\": 512,\n" +
            "        \"height\": 512,\n" +
            "        \"batch_size\": 16\n" +
            "      },\n" +
            "      \"class_type\": \"EmptyLatentImage\"\n" +
            "    },\n" +
            "    \"6\": {\n" +
            "      \"inputs\": {\n" +
            "        \"text\": \"\",\n" +
            "        \"clip\": [\"4\", 1]\n" +
            "      },\n" +
            "      \"class_type\": \"CLIPTextEncode\"\n" +
            "    },\n" +
            "    \"7\": {\n" +
            "      \"inputs\": {\n" +
            "        \"text\": \"bad hands, text, blurry, worst quality, low quality\",\n" +
            "        \"clip\": [\"4\", 1]\n" +
            "      },\n" +
            "      \"class_type\": \"CLIPTextEncode\"\n" +
            "    },\n" +
            "    \"8\": {\n" +
            "      \"inputs\": {\n" +
            "        \"samples\": [\"3\", 0],\n" +
            "        \"vae\": [\"4\", 2]\n" +
            "      },\n" +
            "      \"class_type\": \"VAEDecode\"\n" +
            "    },\n" +
            "    \"9\": {\n" +
            "      \"inputs\": {\n" +
            "        \"frame_rate\": 8,\n" +
            "        \"loop_count\": 0,\n" +
            "        \"filename_prefix\": \"VideoArchitect\",\n" +
            "        \"format\": \"video/h264-mp4\",\n" +
            "        \"pix_fmt\": \"yuv420p\",\n" +
            "        \"crf\": 19,\n" +
            "        \"save_output\": true,\n" +
            "        \"pingpong\": false,\n" +
            "        \"images\": [\"8\", 0]\n" +
            "      },\n" +
            "      \"class_type\": \"VHS_VideoCombine\"\n" +
            "    },\n" +
            "    \"10\": {\n" +
            "      \"inputs\": {\n" +
            "        \"model_name\": \"v3_sd15_mm.ckpt\",\n" +
            "        \"beta_schedule\": \"sqrt_linear (AnimateDiff)\",\n" +
            "        \"model\": [\"4\", 0]\n" +
            "      },\n" +
            "      \"class_type\": \"AnimateDiffLoaderV1\"\n" +
            "    }\n" +
            "  }\n" +
            "}";

    @Autowired
    public ComfyPipelineService(ConfigService configService) {
        this.configService = configService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public ComfyPipelineService(ConfigService configService, HttpClient httpClient) {
        this.configService = configService;
        this.httpClient = httpClient;
    }

    private static final java.util.Map<String, String[]> WIDGET_MAP = new java.util.HashMap<>();
    static {
        WIDGET_MAP.put("CheckpointLoaderSimple", new String[]{"ckpt_name"});
        WIDGET_MAP.put("LoadImage", new String[]{"image", "upload"});
        WIDGET_MAP.put("LoadAudio", new String[]{"audio", "audioUI", "upload"});
        WIDGET_MAP.put("LoraLoaderModelOnly", new String[]{"lora_name", "strength_model"});
        WIDGET_MAP.put("LTXAVTextEncoderLoader", new String[]{"text_encoder", "ckpt_name", "device"});
        WIDGET_MAP.put("LatentUpscaleModelLoader", new String[]{"model_name"});
        WIDGET_MAP.put("EmptyLTXVLatentVideo", new String[]{"width", "height", "length", "batch_size"});
        WIDGET_MAP.put("CFGGuider", new String[]{"cfg"});
        WIDGET_MAP.put("RandomNoise", new String[]{"noise_seed", "control_after_generate"});
        WIDGET_MAP.put("KSamplerSelect", new String[]{"sampler_name"});
        WIDGET_MAP.put("ManualSigmas", new String[]{"sigmas"});
        WIDGET_MAP.put("VAEDecodeTiled", new String[]{"tile_size", "overlap", "temporal_size", "temporal_overlap"});
        WIDGET_MAP.put("LTXVPreprocess", new String[]{"img_compression"});
        WIDGET_MAP.put("ResizeImagesByLongerEdge", new String[]{"longer_edge"});
        WIDGET_MAP.put("SaveVideo", new String[]{"filename_prefix", "format", "codec"});
        WIDGET_MAP.put("VHS_VideoCombine", new String[]{"filename_prefix", "format", "frame_rate", "loop_count", "pix_fmt", "crf", "save_output", "pingpong"});
        WIDGET_MAP.put("SolidMask", new String[]{"value", "width", "height"});
        WIDGET_MAP.put("98ee9e5b-467b-40aa-a534-36033f27d0b4", new String[]{"value", "value_1", "value_2", "value_3", "value_4", "ckpt_name", "lora_name", "text_encoder", "model_name", "lora_name_1", "noise_seed"});
        WIDGET_MAP.put("ComfyMathExpression", new String[]{"expression"});
        WIDGET_MAP.put("LTXVImgToVideoInplace", new String[]{"strength", "bypass"});
        WIDGET_MAP.put("LTXVConditioning", new String[]{"frame_rate"});
        WIDGET_MAP.put("CLIPTextEncode", new String[]{"text"});
        WIDGET_MAP.put("LTXVAudioVAELoader", new String[]{"ckpt_name"});
        WIDGET_MAP.put("CreateVideo", new String[]{"fps"});
        WIDGET_MAP.put("PrimitiveInt", new String[]{"value"});
        WIDGET_MAP.put("PrimitiveFloat", new String[]{"value"});
        WIDGET_MAP.put("PrimitiveStringMultiline", new String[]{"value"});
        WIDGET_MAP.put("LTXVAudioVAEDecode", new String[]{});
        WIDGET_MAP.put("LTXVConcatAVLatent", new String[]{});
        WIDGET_MAP.put("LTXVSeparateAVLatent", new String[]{});
        WIDGET_MAP.put("LTXVLatentUpsampler", new String[]{});
        WIDGET_MAP.put("CLIPLoader", new String[]{"clip_name", "type", "device"});
        WIDGET_MAP.put("VAELoader", new String[]{"vae_name"});
        WIDGET_MAP.put("UNETLoader", new String[]{"unet_name", "weight_dtype"});
        WIDGET_MAP.put("ModelSamplingSD3", new String[]{"shift"});
        WIDGET_MAP.put("WanImageToVideo", new String[]{"width", "height", "length", "batch_size"});
        WIDGET_MAP.put("KSamplerAdvanced", new String[]{"add_noise", "noise_seed", "control_after_generate", "steps", "cfg", "sampler_name", "scheduler", "start_at_step", "end_at_step", "return_with_leftover_noise"});
    }

    public static JSONObject convertUiToApi(JSONObject uiWorkflow) {
        JSONObject apiPayload = new JSONObject();
        JSONObject apiPrompt = new JSONObject();
        apiPayload.put("prompt", apiPrompt);

        if (uiWorkflow.has("definitions")) {
            apiPayload.put("definitions", uiWorkflow.getJSONObject("definitions"));
        }

        JSONArray nodes = uiWorkflow.optJSONArray("nodes");
        if (nodes == null) return apiPayload;

        JSONArray linksArray = uiWorkflow.optJSONArray("links");
        java.util.Map<Integer, Object[]> linksMap = new java.util.HashMap<>();
        if (linksArray != null) {
            for (int i = 0; i < linksArray.length(); i++) {
                JSONArray link = linksArray.optJSONArray(i);
                if (link != null && link.length() >= 6) {
                    int linkId = link.getInt(0);
                    int originNodeId = link.getInt(1);
                    int originSlot = link.getInt(2);
                    int targetNodeId = link.getInt(3);
                    int targetSlot = link.getInt(4);
                    String type = link.getString(5);
                    linksMap.put(linkId, new Object[]{originNodeId, originSlot, type});
                }
            }
        }

        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            String type = node.getString("type");

            // Skip helper nodes like MarkdownNote
            if ("MarkdownNote".equals(type) || "Note".equals(type)) {
                continue;
            }

            String idStr = String.valueOf(node.getInt("id"));
            JSONObject apiNode = new JSONObject();
            apiNode.put("class_type", type);

            JSONObject apiInputs = new JSONObject();
            apiNode.put("inputs", apiInputs);

            // Resolve links
            JSONArray inputs = node.optJSONArray("inputs");
            if (inputs != null) {
                for (int j = 0; j < inputs.length(); j++) {
                    JSONObject input = inputs.getJSONObject(j);
                    String inputName = input.getString("name");
                    if (!input.isNull("link")) {
                        int linkId = input.getInt("link");
                        Object[] origin = linksMap.get(linkId);
                        if (origin != null) {
                            JSONArray linkRef = new JSONArray();
                            linkRef.put(String.valueOf(origin[0]));
                            linkRef.put(origin[1]);
                            apiInputs.put(inputName, linkRef);
                        }
                     }
                 }
             }

             // Resolve widgets
             JSONArray widgetsValues = node.optJSONArray("widgets_values");
             if (widgetsValues != null && WIDGET_MAP.containsKey(type)) {
                 String[] widgetNames = WIDGET_MAP.get(type);
                 for (int j = 0; j < widgetNames.length && j < widgetsValues.length(); j++) {
                     String widgetName = widgetNames[j];
                     if (!apiInputs.has(widgetName)) {
                         apiInputs.put(widgetName, widgetsValues.get(j));
                     }
                 }
             }

             apiPrompt.put(idStr, apiNode);
         }

         return apiPayload;
     }

    private String findExactModelName(String expected, List<String> available) {
        if (expected == null) return "";
        String expectedClean = expected.replace("\\", "/").toLowerCase();
        String expectedName = expectedClean.contains("/") ? expectedClean.substring(expectedClean.lastIndexOf('/') + 1) : expectedClean;
        
        // 1. Try exact match
        for (String av : available) {
            String avClean = av.replace("\\", "/").toLowerCase();
            if (avClean.equals(expectedClean)) {
                return av;
            }
        }
        // 2. Try match on filename only
        for (String av : available) {
            String avClean = av.replace("\\", "/").toLowerCase();
            String avName = avClean.contains("/") ? avClean.substring(avClean.lastIndexOf('/') + 1) : avClean;
            if (avName.equals(expectedName)) {
                return av;
            }
        }
        // 3. Try suffix-based/contains match
        for (String av : available) {
            String avClean = av.replace("\\", "/").toLowerCase();
            if (avClean.endsWith("/" + expectedName) || avClean.contains(expectedName)) {
                return av;
            }
        }
        return expected;
    }

    private List<String> fetchObjectInfoOptions(String serverUrl, String nodeClass, String inputName) {
        List<String> optionsList = new ArrayList<>();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/object_info"))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONObject info = new JSONObject(response.body());
                if (info.has(nodeClass)) {
                    JSONObject nodeInfo = info.getJSONObject(nodeClass);
                    JSONObject input = nodeInfo.optJSONObject("input");
                    if (input != null) {
                        JSONObject required = input.optJSONObject("required");
                        if (required != null) {
                            Object val = required.opt(inputName);
                            if (val instanceof JSONArray) {
                                JSONArray outerArray = (JSONArray) val;
                                if (outerArray.length() > 0) {
                                    Object firstElement = outerArray.get(0);
                                    if (firstElement instanceof JSONArray) {
                                        JSONArray options = (JSONArray) firstElement;
                                        for (int i = 0; i < options.length(); i++) {
                                            optionsList.add(options.getString(i));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("âš ï¸ [ComfyPipeline] Failed to fetch options for " + nodeClass + "/" + inputName + ": " + e.getMessage());
        }
        return optionsList;
    }

    private String resolveVideoModel(String key, String defaultVal, List<String> availableOptions) {
        String resolved = defaultVal;
        if (modelArchitectureService != null) {
            de.tki.comfymodels.service.IModelArchitectureService.ModelDefaults defaults = modelArchitectureService.getDefaultsForModel(key);
            if (defaults != null && defaults.vaeName != null && key.startsWith("video_")) {
                resolved = defaults.vaeName;
            }
        }
        return findExactModelName(resolved, availableOptions);
    }

    private JSONObject generateWanWorkflowJson(String serverUrl, Scene scene, String speakerImage, long seed, String filenamePrefix) {
        String promptText = scene.getPrompt();
        String cleanSpeakerImage = (speakerImage == null || speakerImage.trim().isEmpty()) ? null : speakerImage;
        JSONObject workflowJson = new JSONObject();

        List<String> availableUnets = fetchObjectInfoOptions(serverUrl, "UNETLoader", "unet_name");
        List<String> availableLoras = fetchObjectInfoOptions(serverUrl, "LoraLoader", "lora_name");
        List<String> availableClips = fetchObjectInfoOptions(serverUrl, "CLIPLoader", "clip_name");
        List<String> availableVaes = fetchObjectInfoOptions(serverUrl, "VAELoader", "vae_name");

        String highNoiseUnet = resolveVideoModel("video_wan_high_unet", "Wan2.2\\wan2.2_i2v_high_noise_14B_fp8_scaled.safetensors", availableUnets);
        String lowNoiseUnet = resolveVideoModel("video_wan_low_unet", "Wan2.2\\wan2.2_i2v_low_noise_14B_fp8_scaled.safetensors", availableUnets);
        String highNoiseLora = resolveVideoModel("video_wan_high_lora", "wan2.2_i2v_lightx2v_4steps_lora_v1_high_noise.safetensors", availableLoras);
        String lowNoiseLora = resolveVideoModel("video_wan_low_lora", "wan2.2_i2v_lightx2v_4steps_lora_v1_low_noise.safetensors", availableLoras);
        String clipName = resolveVideoModel("video_wan_clip", "umt5_xxl_fp8_e4m3fn_scaled.safetensors", availableClips);
        String vaeName = resolveVideoModel("video_wan_vae", "wan_2.1_vae.safetensors", availableVaes);

        int width = 640;
        int height = 640;
        // Compute duration in seconds from the scene frame range.
        // Convention: startFrame=0, endFrame=duration_seconds*24. A scene
        // with no frames set falls back to a 5-second default.
        float duration = 5.0f;
        if (scene.getEndFrame() > scene.getStartFrame()) {
            duration = Math.max(1.0f, (scene.getEndFrame() - scene.getStartFrame()) / 24.0f);
        }
        float fps = 16.0f;
        int userSteps = scene.getSteps() > 0 ? scene.getSteps() : 20;
        boolean enableLora = userSteps <= 4;
        int stepsNoLora = enableLora ? 20 : userSteps;
        int stepsLora = 4;
        int splitStepNoLora = Math.max(1, stepsNoLora / 2);
        int splitStepLora = Math.max(1, stepsLora / 2);
        double userCfg = scene.getCfgScale() > 0 ? scene.getCfgScale() : 3.5;
        if (userCfg > 3.0) userCfg = 3.0;
        double cfgNoLora = userCfg;
        double cfgLora = 1.0;

        String negativePrompt = "色调艳丽，过曝，静态，细节模糊不清，字幕，风格，作品，画作，画面，静止，整体发灰，最差质量，低质量，JPEG压缩残留，丑陋的，残缺的，多余的手指，画得不好的手部，画得不好的脸部，畸形的，毁容的，形态畸形的肢体，手指融合，静止不动的画面，杂乱的背景，三条腿，背景人很多，倒着走";

        workflowJson.put("129:90", makeNode("VAELoader", new JSONObject().put("vae_name", vaeName)));
        workflowJson.put("129:84", makeNode("CLIPLoader", new JSONObject().put("clip_name", clipName).put("type", "wan").put("device", "default")));
        workflowJson.put("129:95", makeNode("UNETLoader", new JSONObject().put("unet_name", highNoiseUnet).put("weight_dtype", "default")));
        workflowJson.put("129:96", makeNode("UNETLoader", new JSONObject().put("unet_name", lowNoiseUnet).put("weight_dtype", "default")));
        workflowJson.put("129:101", makeNode("LoraLoaderModelOnly", new JSONObject().put("lora_name", highNoiseLora).put("strength_model", 1.0).put("model", link("129:95", 0))));
        workflowJson.put("129:102", makeNode("LoraLoaderModelOnly", new JSONObject().put("lora_name", lowNoiseLora).put("strength_model", 1.0).put("model", link("129:96", 0))));
        workflowJson.put("129:131", makeNode("PrimitiveBoolean", new JSONObject().put("value", enableLora)));
        workflowJson.put("129:116", makeNode("ComfySwitchNode", new JSONObject().put("switch", link("129:131", 0)).put("on_false", link("129:95", 0)).put("on_true", link("129:101", 0))));
        workflowJson.put("129:117", makeNode("ComfySwitchNode", new JSONObject().put("switch", link("129:131", 0)).put("on_false", link("129:96", 0)).put("on_true", link("129:102", 0))));
        workflowJson.put("129:104", makeNode("ModelSamplingSD3", new JSONObject().put("shift", 5.0).put("model", link("129:116", 0))));
        workflowJson.put("129:103", makeNode("ModelSamplingSD3", new JSONObject().put("shift", 5.0).put("model", link("129:117", 0))));
        workflowJson.put("129:89", makeNode("CLIPTextEncode", new JSONObject().put("text", negativePrompt).put("clip", link("129:84", 0))));
        workflowJson.put("129:93", makeNode("CLIPTextEncode", new JSONObject().put("text", promptText).put("clip", link("129:84", 0))));
        workflowJson.put("129:161", makeNode("PrimitiveFloat", new JSONObject().put("value", duration)));
        workflowJson.put("129:162", makeNode("PrimitiveFloat", new JSONObject().put("value", fps)));
        workflowJson.put("129:163", makeNode("ComfyMathExpression", new JSONObject().put("expression", "floor (a * b + 1)").put("values.a", link("129:161", 0)).put("values.b", link("129:162", 0))));
        workflowJson.put("129:128", makeNode("PrimitiveInt", new JSONObject().put("value", stepsNoLora)));
        workflowJson.put("129:118", makeNode("PrimitiveInt", new JSONObject().put("value", stepsLora)));
        workflowJson.put("129:119", makeNode("ComfySwitchNode", new JSONObject().put("switch", link("129:131", 0)).put("on_false", link("129:128", 0)).put("on_true", link("129:118", 0))));
        workflowJson.put("129:126", makeNode("PrimitiveFloat", new JSONObject().put("value", cfgNoLora)));
        workflowJson.put("129:122", makeNode("PrimitiveFloat", new JSONObject().put("value", cfgLora)));
        workflowJson.put("129:120", makeNode("ComfySwitchNode", new JSONObject().put("switch", link("129:131", 0)).put("on_false", link("129:126", 0)).put("on_true", link("129:122", 0))));
        workflowJson.put("129:127", makeNode("PrimitiveInt", new JSONObject().put("value", splitStepNoLora)));
        workflowJson.put("129:124", makeNode("PrimitiveInt", new JSONObject().put("value", splitStepLora)));
        workflowJson.put("129:125", makeNode("ComfySwitchNode", new JSONObject().put("switch", link("129:131", 0)).put("on_false", link("129:127", 0)).put("on_true", link("129:124", 0))));

        String startImageNodeId;
        if (cleanSpeakerImage != null) {
            workflowJson.put("97", makeNode("LoadImage", new JSONObject().put("image", cleanSpeakerImage)));
            startImageNodeId = "97";
        } else {
            // No speaker image configured: use EmptyImage so the workflow is self-contained
            // and does not depend on a SD 1.5 checkpoint being installed (which previously
            // crashed with "clip input is invalid: None" when the user only had video
            // checkpoints like LTX-Video loaded). EmptyImage ships with stock ComfyUI.
            workflowJson.put("20", makeNode("EmptyImage", new JSONObject()
                    .put("width", width)
                    .put("height", height)
                    .put("batch_size", 1)
                    .put("color", 0)));
            startImageNodeId = "20";
        }

        workflowJson.put("129:98", makeNode("WanImageToVideo", new JSONObject().put("width", width).put("height", height).put("length", link("129:163", 1)).put("batch_size", 1).put("positive", link("129:93", 0)).put("negative", link("129:89", 0)).put("vae", link("129:90", 0)).put("start_image", link(startImageNodeId, 0))));
        workflowJson.put("129:86", makeNode("KSamplerAdvanced", new JSONObject().put("add_noise", "enable").put("noise_seed", seed).put("steps", link("129:119", 0)).put("cfg", link("129:120", 0)).put("sampler_name", "euler").put("scheduler", "simple").put("start_at_step", 0).put("end_at_step", link("129:125", 0)).put("return_with_leftover_noise", "enable").put("model", link("129:104", 0)).put("positive", link("129:98", 0)).put("negative", link("129:98", 1)).put("latent_image", link("129:98", 2))));
        workflowJson.put("129:85", makeNode("KSamplerAdvanced", new JSONObject().put("add_noise", "disable").put("noise_seed", 0).put("steps", link("129:119", 0)).put("cfg", link("129:120", 0)).put("sampler_name", "euler").put("scheduler", "simple").put("start_at_step", link("129:125", 0)).put("end_at_step", link("129:119", 0)).put("return_with_leftover_noise", "disable").put("model", link("129:103", 0)).put("positive", link("129:98", 0)).put("negative", link("129:98", 1)).put("latent_image", link("129:86", 0))));
        workflowJson.put("129:87", makeNode("VAEDecode", new JSONObject().put("samples", link("129:85", 0)).put("vae", link("129:90", 0))));
        workflowJson.put("129:94", makeNode("CreateVideo", new JSONObject().put("fps", link("129:162", 0)).put("images", link("129:87", 0))));
        workflowJson.put("108", makeNode("SaveVideo", new JSONObject().put("filename_prefix", "video/" + filenamePrefix).put("format", "auto").put("codec", "auto").put("video", link("129:94", 0))));

        return workflowJson;
    }

    private JSONObject makeNode(String classType, JSONObject inputs) {
        JSONObject node = new JSONObject();
        node.put("class_type", classType);
        node.put("inputs", inputs);
        return node;
    }

    private JSONArray link(String nodeId, int outputIndex) {
        return new JSONArray().put(nodeId).put(outputIndex);
    }
    private boolean isNodeClassAvailable(String serverUrl, String nodeClass) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/object_info"))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONObject info = new JSONObject(response.body());
                return info.has(nodeClass);
            }
        } catch (Exception e) {
            logger.error("âš ï¸ [ComfyPipeline] Failed to check node class availability: " + e.getMessage());
        }
        return false;
    }

    public CompletableFuture<File> generateScene(Scene scene) {
        return generateSceneInternal(scene, false);
    }

    public CompletableFuture<File> generateSceneStrict(Scene scene) {
        return generateSceneInternal(scene, true);
    }

    private CompletableFuture<File> generateSceneInternal(Scene scene, boolean strictMode) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                validateFfmpeg();

                // Auto-start ComfyUI if it's not healthy
                if (lifecycleService != null && !lifecycleService.isHealthy()) {
                    logger.info("ðŸ”„ [ComfyPipeline] ComfyUI server is offline. Attempting auto-start...");
                    lifecycleService.start();
                    
                    // Poll until healthy
                    int maxWaitSeconds = 90;
                    boolean started = false;
                    for (int i = 0; i < maxWaitSeconds; i++) {
                        if (lifecycleService.isHealthy()) {
                            started = true;
                            break;
                        }
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Auto-start interrupted", ie);
                        }
                    }
                    if (!started) {
                        throw new RuntimeException("Failed to auto-start ComfyUI. Server did not become healthy within " + maxWaitSeconds + " seconds.");
                    }
                    logger.info("âœ… [ComfyPipeline] ComfyUI successfully started and healthy.");
                }

                String serverUrl = configService.getComfyUIUrl();

                // Check and install VHS_VideoCombine if missing
                if (lifecycleService != null && lifecycleService.isHealthy() && bootstrapper != null) {
                    String comfyPath = configService.getComfyUIPath();
                    boolean folderExists = false;
                    if (comfyPath != null && !comfyPath.trim().isEmpty()) {
                        File videoHelperDir = new File(new File(comfyPath, "custom_nodes"), "ComfyUI-Video-Helper-Suite");
                        if (videoHelperDir.exists() && videoHelperDir.isDirectory()) {
                            folderExists = true;
                        }
                    }

                    boolean nodeAvailable = isNodeClassAvailable(serverUrl, "VHS_VideoCombine");
                    if (!nodeAvailable) {
                        if (ATTEMPTED_INSTALLS.contains("VHS_VideoCombine")) {
                            logger.info("âš ï¸ [ComfyPipeline] ComfyUI-Video-Helper-Suite installation/load was already attempted in this session. Skipping to avoid restart loop.");
                        } else {
                            ATTEMPTED_INSTALLS.add("VHS_VideoCombine");
                            if (!folderExists) {
                                logger.info("âš ï¸ [ComfyPipeline] VHS_VideoCombine is missing and folder does not exist. Installing ComfyUI-Video-Helper-Suite...");
                                logger.info("ðŸ”„ [ComfyPipeline] Stopping ComfyUI server to install custom nodes...");
                                lifecycleService.stop();
                                
                                String pythonPath = configService.getPythonPath();
                                if (comfyPath != null && !comfyPath.trim().isEmpty()) {
                                    Path comfyDir = Paths.get(comfyPath);
                                    Path pythonExe = (pythonPath != null && !pythonPath.trim().isEmpty()) ? Paths.get(pythonPath) : null;
                                    bootstrapper.ensureVideoHelperSuiteInstalled(comfyDir, pythonExe, System.out::println);
                                }
                                
                                logger.info("ðŸ”„ [ComfyPipeline] Restarting ComfyUI server after installation...");
                                lifecycleService.start();
                            } else {
                                logger.info("ðŸ”„ [ComfyPipeline] VHS_VideoCombine node is not active but folder exists. Restarting ComfyUI server to load it...");
                                lifecycleService.restart();
                            }
                            
                            // Poll until healthy
                            int maxWaitSeconds = 90;
                            boolean started = false;
                            for (int i = 0; i < maxWaitSeconds; i++) {
                                if (lifecycleService.isHealthy()) {
                                    started = true;
                                    break;
                                }
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    throw new RuntimeException("Restart interrupted", ie);
                                }
                            }
                            if (!started) {
                                throw new RuntimeException("Failed to restart ComfyUI. Server did not become healthy within " + maxWaitSeconds + " seconds.");
                            }
                            logger.info("âœ… [ComfyPipeline] ComfyUI successfully restarted and healthy.");
                        }
                    }
                }

                // 1. Generate workflow JSON dynamically in code
                long seed = Math.abs(new java.util.Random().nextLong());
                String filenamePrefix = "videoarchitect_" + scene.getSceneId();

                // Upload speaker image if present
                String speakerImage = null;
                String speakerPath = configService.getSpeakerImagePath();
                if (speakerPath != null && !speakerPath.trim().isEmpty()) {
                    File speakerFile = new File(speakerPath);
                    if (speakerFile.exists()) {
                        logger.info("ðŸ“¤ [ComfyPipeline] Uploading speaker image: " + speakerFile.getName());
                        speakerImage = uploadFile(serverUrl, speakerFile);
                    }
                }

                JSONObject workflowJson;
                boolean wanNodesAvailable = isNodeClassAvailable(serverUrl, "WanImageToVideo");
                if (wanNodesAvailable) {
                    workflowJson = generateWanWorkflowJson(serverUrl, scene, speakerImage, seed, filenamePrefix);
                } else if (speakerImage != null && !speakerImage.trim().isEmpty()) {
                    // Blueprint is image-to-video only; use it when a start image is provided and Wan nodes are missing
                    JSONObject blueprintJson = loadBlueprintWorkflow("Text to Video (Wan 2.2).json");
                    if (blueprintJson != null) {
                        logger.info("ðŸ“„ [ComfyPipeline] Wan nodes unavailable; using Wan 2.2 blueprint workflow with start image.");
                        injectPrompt(blueprintJson, scene.getPrompt());
                        injectParamsIntoBlueprint(blueprintJson, seed, filenamePrefix, scene);
                        injectSpeakerImage(blueprintJson, speakerImage);
                        workflowJson = blueprintJson;
                    } else {
                        throw new IOException("WanImageToVideo node is not available in ComfyUI and no blueprint workflow was found.");
                    }
                } else {
                    throw new IOException("WanImageToVideo node is not available in ComfyUI. Install Wan 2.2 custom nodes/models or provide a Global Start Image.");
                }

                sanitizeWorkflow(workflowJson, serverUrl);

                // 2. Send prompt to ComfyUI
                String promptId = submitPrompt(serverUrl, workflowJson);
                logger.info("ðŸš€ [ComfyPipeline] Submitted job. Prompt ID: " + promptId);

                // 3. Poll queue status via /history
                ComfyOutputRef outputRef = pollHistoryForOutput(serverUrl, promptId);
                String finishedFilename = outputRef != null ? outputRef.filename : null;
                logger.info("✨ [ComfyPipeline] Job finished. Filename: {}" + 
                        (outputRef != null && !outputRef.subfolder.isEmpty() ? " (subfolder: " + outputRef.subfolder + ")" : ""),
                        finishedFilename);

                // 4. Find the video file in the ComfyUI output directory
                String comfyOutputPath = configService.getResolvedOutputDir();
                if (comfyOutputPath == null || comfyOutputPath.trim().isEmpty()) {
                    comfyOutputPath = new File("output").getAbsolutePath();
                }

                File rawVideoFile = resolveOutputFile(comfyOutputPath, outputRef, scene.getSceneId());

                File finalVideoFile = new File(new File("").getAbsoluteFile(), "scene_" + scene.getSceneId() + "_final.mp4");

                if (rawVideoFile != null && rawVideoFile.exists()) {
                    // 5. Post-Processing: Audio-Muxing (ProcessBuilder)
                    String audioPath = scene.getAudioPath();
                    File audioFile = (audioPath != null && !audioPath.trim().isEmpty()) ? new File(audioPath) : null;

                    if (audioFile != null && audioFile.exists()) {
                        String ffmpegPath = configService.getFfmpegPath();
                        List<String> cmd = List.of(
                            ffmpegPath,
                            "-y",
                            "-i", rawVideoFile.getAbsolutePath(),
                            "-i", audioFile.getAbsolutePath(),
                            "-c:v", "copy",
                            "-c:a", "aac",
                            "-shortest",
                            finalVideoFile.getAbsolutePath()
                        );

                        logger.info("ðŸŽ¬ [ComfyPipeline] Running FFmpeg audio muxing command: " + String.join(" ", cmd));
                        ProcessBuilder pb = new ProcessBuilder(cmd);
                        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                        Process process = processTracker.start(pb);

                        // Read the error stream in a separate thread to prevent buffer deadlocks
                        Thread errorReaderThread = new Thread(() -> {
                            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                                    new java.io.InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    logger.info("[FFmpeg-Mux] " + line);
                                }
                            } catch (IOException e) {
                                logger.error("Error reading FFmpeg error stream: " + e.getMessage());
                            }
                        });
                        errorReaderThread.start();

                        int exitCode = process.waitFor();
                        errorReaderThread.join();

                        if (exitCode != 0) {
                            throw new IOException("FFmpeg audio muxing failed with exit code: " + exitCode);
                        }

                        // Cleanup the original toneless file
                        if (rawVideoFile.exists()) {
                            logger.info("ðŸ§¹ [ComfyPipeline] Cleaning up raw video: " + rawVideoFile.getAbsolutePath());
                            rawVideoFile.delete();
                        }
                    } else {
                        logger.info("â„¹ï¸ [ComfyPipeline] No audio file found or specified for scene. Copying raw video to final path: " + finalVideoFile.getAbsolutePath());
                        Files.copy(rawVideoFile.toPath(), finalVideoFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        if (rawVideoFile.exists()) {
                            rawVideoFile.delete();
                        }
                    }

                    scene.setVideoPath(finalVideoFile.getAbsolutePath());
                    return finalVideoFile;
                } else {
                    // Fallback/Mock scenario: download output if a filename was returned but not found locally (e.g. test environment)
                    if (finishedFilename != null && !finishedFilename.trim().isEmpty()) {
                        File downloadedFile = downloadOutput(serverUrl, outputRef, scene.getSceneId());
                        // Move or copy to finalVideoFile to keep name consistent
                        Files.copy(downloadedFile.toPath(), finalVideoFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        downloadedFile.delete();
                        scene.setVideoPath(finalVideoFile.getAbsolutePath());
                        return finalVideoFile;
                    } else {
                        if (strictMode) {
                            throw new Exception("Strict mode enabled: ComfyUI finished generation, but no output video file was returned or found locally.");
                        }
                        // Generate simulated video as fallback
                        File fallbackVideo = generateSimulatedVideo(scene);
                        Files.copy(fallbackVideo.toPath(), finalVideoFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        fallbackVideo.delete();
                        scene.setVideoPath(finalVideoFile.getAbsolutePath());
                        return finalVideoFile;
                    }
                }

            } catch (Exception e) {
                if (strictMode) {
                    logger.error("ðŸ”´ [ComfyPipeline] Failed generating scene via ComfyUI (Strict Mode): " + e.getMessage());
                    throw new RuntimeException("Strict mode generation failed: " + e.getMessage(), e);
                }
                logger.error("ðŸ”´ [ComfyPipeline] Failed generating scene via ComfyUI: " + e.getMessage() + ". Generating simulated fallback video.");
                try {
                    File fallbackVideo = generateSimulatedVideo(scene);
                    File finalVideoFile = new File(new File("").getAbsoluteFile(), "scene_" + scene.getSceneId() + "_final.mp4");
                    Files.copy(fallbackVideo.toPath(), finalVideoFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    fallbackVideo.delete();
                    scene.setVideoPath(finalVideoFile.getAbsolutePath());
                    return finalVideoFile;
                } catch (Exception fe) {
                    throw new RuntimeException("Failed generating scene and fallback also failed: " + fe.getMessage(), fe);
                }
            }
        });
    }

    public CompletableFuture<File> generateMontageScene(Scene scene) {
        return CompletableFuture.supplyAsync(() -> {
            String originalSpeaker = configService.getSpeakerImagePath();
            try {
                String sourceClip = scene.getSourceClipPath();
                if (sourceClip != null && !sourceClip.trim().isEmpty() && new File(sourceClip).exists()) {
                    logger.info("ðŸŽ¬ [ComfyPipeline] Montage mode: Using source clip as input: " + sourceClip);
                    configService.setSpeakerImagePath(sourceClip);
                }
                return generateScene(scene).join();
            } finally {
                configService.setSpeakerImagePath(originalSpeaker);
            }
        });
    }


    private void validateFfmpeg() throws java.io.FileNotFoundException {
        String ffmpegPath = configService.getFfmpegPath();
        File ffmpegFile = new File(ffmpegPath);
        
        boolean isGlobal = "ffmpeg".equals(ffmpegPath);
        boolean available = false;
        
        if (isGlobal) {
            try {
                Process p = processTracker.start(new ProcessBuilder("ffmpeg", "-version"));
;
                p.destroy();
                available = true;
            } catch (Exception e) {
                available = false;
            }
        } else {
            available = ffmpegFile.exists() && ffmpegFile.isFile() && ffmpegFile.canExecute();
        }
        
        if (!available) {
            throw new java.io.FileNotFoundException("FFmpeg executable was not found or is not executable (Path: " + ffmpegPath + "). " +
                "Please configure FFmpeg in settings or add it to your system PATH.");
        }
    }

    private File generateSimulatedVideo(Scene scene) throws Exception {
        validateFfmpeg();

        Path targetDir = Paths.get("").toAbsolutePath();
        String localFilename = "scene_" + scene.getSceneId() + "_simulated.mp4";
        Path targetFile = targetDir.resolve(localFilename);
        
        // Calculate duration: prefer audio-based if available, else frame-based
        int duration = 5;
        String audioPath = scene.getAudioPath();
        if (audioPath != null && !audioPath.trim().isEmpty() && new File(audioPath).exists()) {
            double audioDur = probeDurationSeconds(new File(audioPath));
            if (audioDur > 0) {
                duration = (int) Math.ceil(audioDur);
            }
        } else if (scene.getEndFrame() > scene.getStartFrame()) {
            duration = (scene.getEndFrame() - scene.getStartFrame()) / 30;
        }
        if (duration <= 0) {
            duration = 3;
        }
        
        // Build a readable prompt text for the placeholder overlay
        String promptText = scene.getPrompt();
        if (promptText == null || promptText.trim().isEmpty()) {
            promptText = "Scene " + scene.getSceneId();
        }
        // Escape for FFmpeg drawtext filter
        String escapedPrompt = promptText
            .replace("\\", "\\\\")
            .replace("'", "'\\\\''")
            .replace(":", "\\\\:")
            .replace("%", "%%")
            .replace("\n", " ");
        // Truncate very long prompts for readability
        if (escapedPrompt.length() > 120) {
            escapedPrompt = escapedPrompt.substring(0, 117) + "...";
        }
        String escapedSceneId = scene.getSceneId().replace(":", "\\\\:");

        // Generate a dark gradient background with scene info text overlay
        String drawFilter = 
            "drawtext=text='Scene " + escapedSceneId + "':fontsize=36:fontcolor=white:x=(w-tw)/2:y=h/4-th/2," +
            "drawtext=text='" + escapedPrompt + "':fontsize=20:fontcolor=0xCCCCCC:x=(w-tw)/2:y=h/2-th/2," +
            "drawtext=text='[ComfyUI Offline - Placeholder]':fontsize=16:fontcolor=0x888888:x=(w-tw)/2:y=3*h/4";

        logger.info("ðŸŽ¥ [ComfyPipeline] Generating placeholder video with scene info (Duration: " + duration + "s) to: " + targetFile);
        List<String> cmd = new java.util.ArrayList<>();
        cmd.add(configService.getFfmpegPath());
        cmd.add("-y");
        cmd.add("-f"); cmd.add("lavfi");
        cmd.add("-i"); cmd.add("color=c=0x1a1a2e:s=720x720:d=" + duration + ":r=30");
        cmd.add("-vf"); cmd.add(drawFilter);
        cmd.add("-pix_fmt"); cmd.add("yuv420p");
        cmd.add("-c:v"); cmd.add("libx264");
        cmd.add("-preset"); cmd.add("fast");
        cmd.add("-crf"); cmd.add("23");
        cmd.add(targetFile.toString());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = processTracker.start(pb);
        
        // Consume stream
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            while (r.readLine() != null) {}
        }
        
        int exitCode = process.waitFor();
        if (exitCode == 0 && Files.exists(targetFile) && Files.size(targetFile) > 1024) {
            logger.info("ðŸŽ¥ [ComfyPipeline] Generated placeholder video with scene overlay: " + targetFile);
            return targetFile.toFile();
        } else {
            // Fallback to minimal color source without text if drawtext fails (e.g. missing fonts)
            logger.info("âš ï¸ [ComfyPipeline] Drawtext failed (exit code " + exitCode + "). Falling back to plain color source.");
            ProcessBuilder pbFallback = new ProcessBuilder(
                configService.getFfmpegPath(), "-y",
                "-f", "lavfi",
                "-i", "color=c=0x1a1a2e:s=720x720:d=" + duration + ":r=30",
                "-pix_fmt", "yuv420p",
                "-c:v", "libx264",
                targetFile.toString()
            );
            pbFallback.redirectErrorStream(true);
            Process fallbackProcess = processTracker.start(pbFallback);
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(fallbackProcess.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                while (r.readLine() != null) {}
            }
            int fallbackExit = fallbackProcess.waitFor();
            if (fallbackExit == 0 && Files.exists(targetFile) && Files.size(targetFile) > 1024) {
                return targetFile.toFile();
            }
            throw new IOException("FFmpeg exited with code " + fallbackExit + " when generating simulation fallback.");
        }
    }

    /**
     * Probes the duration of a media file (audio or video) in seconds using ffprobe.
     */
    private double probeDurationSeconds(File mediaFile) {
        try {
            String ffmpegPathStr = configService.getFfmpegPath();
            String ffprobePathStr;
            if ("ffmpeg".equals(ffmpegPathStr)) {
                ffprobePathStr = "ffprobe";
            } else {
                File ffmpegFile = new File(ffmpegPathStr);
                File parent = ffmpegFile.getParentFile();
                if (parent != null) {
                    boolean isWin = System.getProperty("os.name").toLowerCase().contains("win");
                    ffprobePathStr = new File(parent, isWin ? "ffprobe.exe" : "ffprobe").getAbsolutePath();
                } else {
                    ffprobePathStr = ffmpegPathStr.replace("ffmpeg", "ffprobe").replace("ffmpeg.exe", "ffprobe.exe");
                }
            }

            ProcessBuilder pb = new ProcessBuilder(
                ffprobePathStr,
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "csv=p=0",
                mediaFile.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process p = processTracker.start(pb);
            String line;
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                line = r.readLine();
            }
            p.waitFor();
            if (line != null && !line.trim().isEmpty()) {
                return Double.parseDouble(line.trim());
            }
        } catch (Exception e) {
            logger.error("âš ï¸ [ComfyPipeline] Could not probe duration: " + e.getMessage());
        }
        return 0.0;
    }

    public static JSONObject flattenWorkflow(JSONObject uiWorkflow) {
        if (!uiWorkflow.has("definitions") || !uiWorkflow.getJSONObject("definitions").has("subgraphs")) {
            return uiWorkflow;
        }

        JSONArray subgraphs = uiWorkflow.getJSONObject("definitions").getJSONArray("subgraphs");
        java.util.Map<String, JSONObject> subgraphDefs = new java.util.HashMap<>();
        for (int i = 0; i < subgraphs.length(); i++) {
            JSONObject sg = subgraphs.getJSONObject(i);
            subgraphDefs.put(sg.getString("id"), sg);
        }

        JSONArray mainNodes = uiWorkflow.getJSONArray("nodes");
        JSONArray mainLinks = uiWorkflow.optJSONArray("links");
        if (mainLinks == null) {
            mainLinks = new JSONArray();
            uiWorkflow.put("links", mainLinks);
        }

        boolean flattenedAny = true;
        int safetyCounter = 0;
        int idOffset = 10000;

        while (flattenedAny && safetyCounter < 10) {
            flattenedAny = false;
            safetyCounter++;

            JSONArray newNodes = new JSONArray();
            for (int i = 0; i < mainNodes.length(); i++) {
                JSONObject node = mainNodes.getJSONObject(i);
                String type = node.getString("type");

                if (subgraphDefs.containsKey(type)) {
                    flattenedAny = true;
                    JSONObject sgDef = subgraphDefs.get(type);
                    int subgraphNodeId = node.getInt("id");

                    java.util.Map<Integer, Object[]> inputSlotLinks = new java.util.HashMap<>();
                    java.util.Map<Integer, List<Object[]>> outputSlotLinks = new java.util.HashMap<>();

                    for (int j = 0; j < mainLinks.length(); j++) {
                        JSONArray link = mainLinks.optJSONArray(j);
                        if (link == null || link.length() < 6) continue;
                        int linkId = link.getInt(0);
                        int originId = link.getInt(1);
                        int originSlot = link.getInt(2);
                        int targetId = link.getInt(3);
                        int targetSlot = link.getInt(4);
                        String linkType = link.getString(5);

                        if (targetId == subgraphNodeId) {
                            inputSlotLinks.put(targetSlot, new Object[]{originId, originSlot, linkType, linkId});
                        }
                        if (originId == subgraphNodeId) {
                            outputSlotLinks.computeIfAbsent(originSlot, k -> new ArrayList<>())
                                    .add(new Object[]{targetId, targetSlot, linkType, linkId});
                        }
                    }

                    JSONArray sgNodes = sgDef.getJSONArray("nodes");
                    java.util.Map<Integer, Integer> nodeIdMap = new java.util.HashMap<>();
                    for (int j = 0; j < sgNodes.length(); j++) {
                        JSONObject internalNode = new JSONObject(sgNodes.getJSONObject(j).toString());
                        int oldId = internalNode.getInt("id");
                        int newId = oldId + idOffset;
                        internalNode.put("id", newId);
                        nodeIdMap.put(oldId, newId);

                        JSONArray nodeInputs = internalNode.optJSONArray("inputs");
                        if (nodeInputs != null) {
                            for (int k = 0; k < nodeInputs.length(); k++) {
                                JSONObject inputObj = nodeInputs.getJSONObject(k);
                                if (inputObj.has("link") && !inputObj.isNull("link")) {
                                    int oldLink = inputObj.getInt("link");
                                    inputObj.put("link", oldLink + idOffset * 10);
                                }
                            }
                        }

                        newNodes.put(internalNode);
                    }

                    JSONArray sgLinks = sgDef.optJSONArray("links");
                    if (sgLinks != null) {
                        for (int j = 0; j < sgLinks.length(); j++) {
                            int linkId, originId, originSlot, targetId, targetSlot;
                            String linkType;
                            Object itemObj = sgLinks.get(j);
                            if (itemObj instanceof JSONArray) {
                                JSONArray link = (JSONArray) itemObj;
                                linkId = link.getInt(0);
                                originId = link.getInt(1);
                                originSlot = link.getInt(2);
                                targetId = link.getInt(3);
                                targetSlot = link.getInt(4);
                                linkType = link.getString(5);
                            } else if (itemObj instanceof JSONObject) {
                                JSONObject link = (JSONObject) itemObj;
                                linkId = link.getInt("id");
                                originId = link.getInt("origin_id");
                                originSlot = link.getInt("origin_slot");
                                targetId = link.getInt("target_id");
                                targetSlot = link.getInt("target_slot");
                                linkType = link.getString("type");
                            } else {
                                continue;
                            }

                            int newLinkId = linkId + idOffset * 10;
                            int newOriginId = originId == -10 ? -10 : (nodeIdMap.containsKey(originId) ? nodeIdMap.get(originId) : originId);
                            int newTargetId = targetId == -20 ? -20 : (nodeIdMap.containsKey(targetId) ? nodeIdMap.get(targetId) : targetId);

                            if (newOriginId == -10 && newTargetId == -20) {
                                continue;
                            }

                            JSONArray newLink = new JSONArray();
                            newLink.put(newLinkId);
                            newLink.put(newOriginId);
                            newLink.put(originSlot);
                            newLink.put(newTargetId);
                            newLink.put(targetSlot);
                            newLink.put(linkType);

                            mainLinks.put(newLink);
                        }
                    }

                    JSONArray sgInputs = sgDef.optJSONArray("inputs");
                    if (sgInputs != null) {
                        for (int slotIdx = 0; slotIdx < sgInputs.length(); slotIdx++) {
                            JSONObject sgInput = sgInputs.getJSONObject(slotIdx);
                            JSONArray linkIds = sgInput.optJSONArray("linkIds");
                            if (linkIds == null) continue;

                            Object[] extLinkInfo = inputSlotLinks.get(slotIdx);
                            if (extLinkInfo != null) {
                                int extOriginId = (int) extLinkInfo[0];
                                int extOriginSlot = (int) extLinkInfo[1];

                                for (int k = 0; k < linkIds.length(); k++) {
                                    int intLinkId = linkIds.getInt(k);
                                    int newIntLinkId = intLinkId + idOffset * 10;

                                    for (int m = 0; m < mainLinks.length(); m++) {
                                        JSONArray l = mainLinks.optJSONArray(m);
                                        if (l != null && l.length() >= 6 && l.getInt(0) == newIntLinkId) {
                                            l.put(1, extOriginId);
                                            l.put(2, extOriginSlot);
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    JSONArray sgOutputs = sgDef.optJSONArray("outputs");
                    if (sgOutputs != null) {
                        for (int slotIdx = 0; slotIdx < sgOutputs.length(); slotIdx++) {
                            JSONObject sgOutput = sgOutputs.getJSONObject(slotIdx);
                            JSONArray linkIds = sgOutput.optJSONArray("linkIds");
                            if (linkIds == null) continue;

                            List<Object[]> extLinkInfos = outputSlotLinks.get(slotIdx);
                            if (extLinkInfos != null) {
                                for (int k = 0; k < linkIds.length(); k++) {
                                    int intLinkId = linkIds.getInt(k);
                                    int newIntLinkId = intLinkId + idOffset * 10;

                                    int internalOriginId = -1;
                                    int internalOriginSlot = -1;
                                    for (int m = 0; m < mainLinks.length(); m++) {
                                        JSONArray l = mainLinks.optJSONArray(m);
                                        if (l != null && l.length() >= 6 && l.getInt(0) == newIntLinkId) {
                                            internalOriginId = l.getInt(1);
                                            internalOriginSlot = l.getInt(2);
                                            break;
                                        }
                                    }

                                    if (internalOriginId != -1) {
                                        for (Object[] extLinkInfo : extLinkInfos) {
                                            int extLinkId = (int) extLinkInfo[3];
                                            for (int m = 0; m < mainLinks.length(); m++) {
                                                JSONArray l = mainLinks.optJSONArray(m);
                                                if (l != null && l.length() >= 6 && l.getInt(0) == extLinkId) {
                                                    l.put(1, internalOriginId);
                                                    l.put(2, internalOriginSlot);
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    JSONArray cleanLinks = new JSONArray();
                    for (int j = 0; j < mainLinks.length(); j++) {
                        JSONArray l = mainLinks.optJSONArray(j);
                        if (l == null || l.length() < 6) continue;
                        int originId = l.getInt(1);
                        int targetId = l.getInt(3);
                        if (originId == subgraphNodeId || targetId == subgraphNodeId || originId == -10 || targetId == -20) {
                            continue;
                        }
                        cleanLinks.put(l);
                    }
                    mainLinks = cleanLinks;
                    uiWorkflow.put("links", mainLinks);

                    idOffset += 10000;
                } else {
                    newNodes.put(node);
                }
            }
            mainNodes = newNodes;
            uiWorkflow.put("nodes", mainNodes);
        }

        return uiWorkflow;
    }

    private String loadTemplate() {
        Path path = Paths.get("workflow.json");
        if (Files.exists(path)) {
            try {
                String content = Files.readString(path, StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(content);
                if (json.has("prompt") || json.keySet().stream().anyMatch(key -> {
                    JSONObject node = json.optJSONObject(key);
                    return node != null && node.has("class_type");
                })) {
                    return content;
                }
                if (json.has("nodes")) {
                    JSONObject flattened = flattenWorkflow(json);
                    JSONObject apiJson = convertUiToApi(flattened);
                    return apiJson.toString();
                }
            } catch (Exception e) {
                logger.error("âš ï¸ [ComfyPipeline] Failed to parse custom workflow.json: " + e);
            }
        }
        return DEFAULT_API_TEMPLATE;
    }

    private void injectPrompt(JSONObject workflowJson, String promptText) {
        JSONObject promptObj = workflowJson.has("prompt") ? workflowJson.getJSONObject("prompt") : workflowJson;
        boolean injected = false;
        
        for (String key : promptObj.keySet()) {
            JSONObject node = promptObj.optJSONObject(key);
            if (node == null) continue;
            
            String classType = node.optString("class_type", "");
            JSONObject inputs = node.optJSONObject("inputs");
            if (inputs == null) continue;
            
            if ("CLIPTextEncode".equals(classType)) {
                String text = inputs.optString("text", "");
                // Skip negative prompts
                if (!text.toLowerCase().contains("blurry") && !text.toLowerCase().contains("bad hands")
                    && !text.toLowerCase().contains("worst quality") && !text.toLowerCase().contains("low quality")
                    && !text.toLowerCase().contains("é™æ­¢") && !text.toLowerCase().contains("æœ€å·®è´¨é‡")) {
                    inputs.put("text", promptText);
                    injected = true;
                    logger.info("ðŸ“¥ [ComfyPipeline] Injected visual_prompt into CLIPTextEncode node ID: " + key);
                }
            } else if ("98ee9e5b-467b-40aa-a534-36033f27d0b4".equals(classType)
                    || "84e2cf3f-de93-40ef-ab22-b9375296917b".equals(classType)) {
                // Subgraph node for video generation
                if (inputs.has("text")) {
                    inputs.put("text", promptText);
                } else {
                    inputs.put("value", promptText);
                }
                injected = true;
                logger.info("ðŸ“¥ [ComfyPipeline] Injected visual_prompt into Video Gen Subgraph node ID: " + key);
            } else if ("PrimitiveStringMultiline".equals(classType)) {
                // Primitive multiline string inputs (often used as prompt nodes)
                inputs.put("value", promptText);
                injected = true;
                logger.info("ðŸ“¥ [ComfyPipeline] Injected visual_prompt into PrimitiveStringMultiline node ID: " + key);
            }
        }

        if (!injected) {
            throw new RuntimeException("Could not find suitable prompt input node in workflow template!");
        }
    }

    public void sanitizeWorkflow(JSONObject workflowJson, String serverUrl) {
        List<String> availableCkpts = fetchAvailableCheckpoints(serverUrl);
        if (availableCkpts.isEmpty()) {
            return; // No info, skip sanitization
        }

        JSONObject promptObj = workflowJson.has("prompt") ? workflowJson.getJSONObject("prompt") : workflowJson;
        for (String key : promptObj.keySet()) {
            JSONObject node = promptObj.optJSONObject(key);
            if (node != null && "CheckpointLoaderSimple".equals(node.optString("class_type"))) {
                JSONObject inputs = node.optJSONObject("inputs");
                if (inputs != null) {
                    String currentCkpt = inputs.optString("ckpt_name", "");
                    if (!availableCkpts.contains(currentCkpt)) {
                        if (!availableCkpts.isEmpty()) {
                            String replacement = availableCkpts.get(0);
                            inputs.put("ckpt_name", replacement);
                            logger.info("ðŸ”„ [ComfyPipeline] Replaced missing checkpoint '" + currentCkpt + "' with first available: '" + replacement + "' in node " + key);
                        } else {
                            logger.error("âš ï¸ [ComfyPipeline] Could not replace missing checkpoint because available checkpoint list is empty (ComfyUI may be offline).");
                        }
                    }
                }
            }
        }
    }

    private List<String> fetchAvailableCheckpoints(String serverUrl) {
        List<String> checkpoints = new ArrayList<>();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/object_info"))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONObject info = new JSONObject(response.body());
                if (info.has("CheckpointLoaderSimple")) {
                    JSONObject nodeInfo = info.getJSONObject("CheckpointLoaderSimple");
                    JSONObject input = nodeInfo.optJSONObject("input");
                    if (input != null) {
                        JSONObject required = input.optJSONObject("required");
                        if (required != null) {
                            Object val = required.opt("ckpt_name");
                            if (val instanceof JSONArray) {
                                JSONArray outerArray = (JSONArray) val;
                                if (outerArray.length() > 0) {
                                    Object firstElement = outerArray.get(0);
                                    if (firstElement instanceof JSONArray) {
                                        JSONArray options = (JSONArray) firstElement;
                                        for (int i = 0; i < options.length(); i++) {
                                            checkpoints.add(options.getString(i));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("âš ï¸ [ComfyPipeline] Failed to fetch checkpoints from ComfyUI: " + e);
        }
        return checkpoints;
    }

    private String submitPrompt(String serverUrl, JSONObject workflowJson) throws IOException, InterruptedException {
        // Wrap payload in 'prompt' block if it doesn't already have one
        JSONObject payload = workflowJson.has("prompt") ? workflowJson : new JSONObject().put("prompt", workflowJson);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/prompt"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            JSONObject res = new JSONObject(response.body());
            return res.getString("prompt_id");
        } else {
            throw new IOException("Failed to send prompt to ComfyUI. Status: " + response.statusCode() + ", Body: " + response.body());
        }
    }

    public String pollHistoryForFilename(String serverUrl, String promptId) throws IOException, InterruptedException {
        ComfyOutputRef ref = pollHistoryForOutput(serverUrl, promptId);
        return ref != null ? ref.filename : "";
    }

    public ComfyOutputRef pollHistoryForOutput(String serverUrl, String promptId) throws IOException, InterruptedException {
        int timeoutCount = 0;
        while (timeoutCount < 6000) { // Timeout after 5 hours (6000 * 3s)
            Thread.sleep(3000);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/history/" + promptId))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONObject history = new JSONObject(response.body());
                if (history.has(promptId)) {
                    JSONObject job = history.getJSONObject(promptId);
                    JSONObject status = job.optJSONObject("status");
                    if (status != null && "error".equalsIgnoreCase(status.optString("status_str", ""))) {
                        String errorDetail = extractHistoryError(status);
                        throw new IOException("ComfyUI generation failed: " + errorDetail);
                    }

                    JSONObject outputs = job.optJSONObject("outputs");
                    if (outputs != null) {
                        ComfyOutputRef ref = extractFirstOutput(outputs);
                        if (ref != null) {
                            return ref;
                        }
                    }
                    return null;
                }
            }
            timeoutCount++;
        }
        throw new IOException("Polling timeout waiting for ComfyUI generation job to complete.");
    }

    private ComfyOutputRef extractFirstOutput(JSONObject outputs) {
        String[] outputKeys = {"videos", "gifs", "images"};
        for (String nodeId : outputs.keySet()) {
            JSONObject outputNode = outputs.getJSONObject(nodeId);
            for (String mediaKey : outputKeys) {
                if (!outputNode.has(mediaKey)) {
                    continue;
                }
                JSONArray arr = outputNode.optJSONArray(mediaKey);
                if (arr == null) {
                    continue;
                }
                for (int i = 0; i < arr.length(); i++) {
                    Object itemObj = arr.get(i);
                    if (itemObj instanceof JSONObject item && item.has("filename")) {
                        return new ComfyOutputRef(
                                item.getString("filename"),
                                item.optString("subfolder", ""),
                                item.optString("type", "output")
                        );
                    }
                }
            }
            // Fallback: scan any array-valued output bucket
            for (String nodeKey : outputNode.keySet()) {
                Object value = outputNode.get(nodeKey);
                if (value instanceof JSONArray arr) {
                    for (int i = 0; i < arr.length(); i++) {
                        Object itemObj = arr.get(i);
                        if (itemObj instanceof JSONObject item && item.has("filename")) {
                            return new ComfyOutputRef(
                                    item.getString("filename"),
                                    item.optString("subfolder", ""),
                                    item.optString("type", "output")
                            );
                        }
                    }
                }
            }
        }
        return null;
    }

    private String extractHistoryError(JSONObject status) {
        JSONArray messages = status.optJSONArray("messages");
        if (messages != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < messages.length(); i++) {
                Object entry = messages.get(i);
                if (entry instanceof JSONArray arr && arr.length() >= 2) {
                    if (sb.length() > 0) {
                        sb.append(" | ");
                    }
                    sb.append(arr.get(1));
                }
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        }
        return status.optString("status_str", "Unknown ComfyUI error");
    }

    private File resolveOutputFile(String comfyOutputPath, ComfyOutputRef outputRef, String sceneId) {
        if (outputRef != null && outputRef.filename != null && !outputRef.filename.trim().isEmpty()) {
            File baseDir = outputRef.subfolder.isEmpty()
                    ? new File(comfyOutputPath)
                    : new File(comfyOutputPath, outputRef.subfolder);
            File candidate = new File(baseDir, outputRef.filename);
            if (candidate.exists()) {
                return candidate;
            }
        }

        File outputRoot = new File(comfyOutputPath);
        String prefix = "videoarchitect_" + sceneId;
        String[] suffixes = {".mp4", "_00001.mp4", ".webm", "_00001.webm"};
        for (String suffix : suffixes) {
            File direct = new File(outputRoot, prefix + suffix);
            if (direct.exists()) {
                return direct;
            }
            File nested = new File(new File(outputRoot, "video"), prefix + suffix);
            if (nested.exists()) {
                return nested;
            }
        }
        return null;
    }

    private File downloadOutput(String serverUrl, ComfyOutputRef outputRef, String sceneId) throws IOException, InterruptedException {
        if (outputRef == null || outputRef.filename == null || outputRef.filename.trim().isEmpty()) {
            throw new IOException("No output filename available for download.");
        }

        // Target project directory
        Path targetDir = Paths.get("").toAbsolutePath();
        String localFilename = "scene_" + sceneId + "_" + outputRef.filename;
        Path targetFile = targetDir.resolve(localFilename);

        StringBuilder downloadUrl = new StringBuilder(serverUrl)
                .append("/view?filename=")
                .append(java.net.URLEncoder.encode(outputRef.filename, StandardCharsets.UTF_8))
                .append("&type=")
                .append(java.net.URLEncoder.encode(outputRef.type, StandardCharsets.UTF_8));
        if (!outputRef.subfolder.isEmpty()) {
            downloadUrl.append("&subfolder=")
                    .append(java.net.URLEncoder.encode(outputRef.subfolder, StandardCharsets.UTF_8));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl.toString()))
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() == 200) {
            try (InputStream is = response.body()) {
                Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
            logger.info("ðŸ’¾ [ComfyPipeline] Downloaded output to: " + targetFile);
            return targetFile.toFile();
        } else {
            throw new IOException("Failed to download output file. Status: " + response.statusCode());
        }
    }

    private void injectSpeakerImage(JSONObject workflowJson, String filename) {
        JSONObject promptObj = workflowJson.has("prompt") ? workflowJson.getJSONObject("prompt") : workflowJson;
        boolean injected = false;
        for (String key : promptObj.keySet()) {
            JSONObject node = promptObj.optJSONObject(key);
            if (node == null) continue;
            String classType = node.optString("class_type", "");
            if ("LoadImage".equals(classType)) {
                JSONObject inputs = node.optJSONObject("inputs");
                if (inputs != null) {
                    inputs.put("image", filename);
                    injected = true;
                    logger.info("ðŸ“¥ [ComfyPipeline] Injected speaker image '" + filename + "' into LoadImage node ID: " + key);
                }
            }
        }
        if (!injected) {
            logger.info("â„¹ï¸ [ComfyPipeline] No LoadImage node found in workflow to inject speaker image.");
        }
    }

    private void injectAudio(JSONObject workflowJson, String filename) {
        JSONObject promptObj = workflowJson.has("prompt") ? workflowJson.getJSONObject("prompt") : workflowJson;
        boolean injected = false;
        for (String key : promptObj.keySet()) {
            JSONObject node = promptObj.optJSONObject(key);
            if (node == null) continue;
            String classType = node.optString("class_type", "");
            if ("LoadAudio".equals(classType)) {
                JSONObject inputs = node.optJSONObject("inputs");
                if (inputs != null) {
                    inputs.put("audio", filename);
                    injected = true;
                    logger.info("ðŸ“¥ [ComfyPipeline] Injected narration audio '" + filename + "' into LoadAudio node ID: " + key);
                }
            }
        }
        if (!injected) {
            logger.info("â„¹ï¸ [ComfyPipeline] No LoadAudio node found in workflow to inject narration audio.");
        }
    }

    public String uploadFile(String serverUrl, File file) throws IOException, InterruptedException {
        String boundary = "---" + System.currentTimeMillis() + "---";
        String mimeType = file.getName().endsWith(".wav") ? "audio/wav" : (file.getName().endsWith(".mp3") ? "audio/mpeg" : "image/png");
        
        byte[] header = ("--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"image\"; filename=\"" + file.getName() + "\"\r\n" +
                "Content-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] footer = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] fileBytes = Files.readAllBytes(file.toPath());
        
        java.util.List<byte[]> body = java.util.List.of(header, fileBytes, footer);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/upload/image"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArrays(body))
                .build();
                
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            JSONObject res = new JSONObject(response.body());
            return res.getString("name");
        } else {
            throw new IOException("Failed to upload file to ComfyUI. Status: " + response.statusCode() + ", Body: " + response.body());
        }
    }

    private JSONObject loadBlueprintWorkflow(String filename) {
        java.util.List<File> candidateDirs = new java.util.ArrayList<>();
        String comfyPath = configService.getComfyUIPath();
        if (comfyPath != null && !comfyPath.trim().isEmpty()) {
            candidateDirs.add(new File(comfyPath, "companion_blueprints"));
            candidateDirs.add(new File(comfyPath, "resources/ComfyUI/companion_blueprints"));
        }
        candidateDirs.add(new File("companion_blueprints"));
        candidateDirs.add(new File(System.getProperty("user.dir"), "companion_blueprints"));

        for (File blueprintsDir : candidateDirs) {
            if (blueprintsDir == null || !blueprintsDir.exists() || !blueprintsDir.isDirectory()) {
                continue;
            }
            File blueprintFile = new File(blueprintsDir, filename);
            JSONObject parsed = parseBlueprintFile(blueprintFile);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private JSONObject parseBlueprintFile(File blueprintFile) {
        if (!blueprintFile.exists() || !blueprintFile.isFile()) {
            return null;
        }
        try {
            String content = Files.readString(blueprintFile.toPath(), StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(content);
            if (json.has("prompt") || json.keySet().stream().anyMatch(key -> {
                JSONObject node = json.optJSONObject(key);
                return node != null && node.has("class_type");
            })) {
                return json;
            }
            if (json.has("nodes")) {
                JSONObject flattened = flattenWorkflow(json);
                return convertUiToApi(flattened);
            }
        } catch (Exception e) {
            logger.error("âš ï¸ [ComfyPipeline] Failed to load blueprint " + blueprintFile.getName() + ": " + e.getMessage());
        }
        return null;
    }

    private void injectParamsIntoBlueprint(JSONObject workflowJson, long seed, String filenamePrefix, Scene scene) {
        JSONObject promptObj = workflowJson.has("prompt") ? workflowJson.getJSONObject("prompt") : workflowJson;
        double sceneDuration = (scene.getEndFrame() - scene.getStartFrame()) / 30.0;
        if (sceneDuration <= 0) {
            sceneDuration = 5.0;
        }

        for (String key : promptObj.keySet()) {
            JSONObject node = promptObj.optJSONObject(key);
            if (node == null) continue;
            
            String classType = node.optString("class_type", "");
            JSONObject inputs = node.optJSONObject("inputs");
            if (inputs == null) continue;

            // Inject duration into Wan video subgraph wrappers
            if ("84e2cf3f-de93-40ef-ab22-b9375296917b".equals(classType)
                    || "98ee9e5b-467b-40aa-a534-36033f27d0b4".equals(classType)) {
                if (inputs.has("value_1")) {
                    inputs.put("value_1", sceneDuration);
                    logger.info("ðŸ“¥ [ComfyPipeline] Injected scene duration " + sceneDuration + "s into video subgraph node ID: " + key);
                }
            }
            
            // Inject seed and Wan-safe sampler params into KSamplers
            if ("KSampler".equals(classType) || "KSamplerAdvanced".equals(classType)) {
                if (inputs.has("seed")) {
                    inputs.put("seed", seed);
                }
                if (inputs.has("noise_seed")) {
                    inputs.put("noise_seed", seed);
                }
                int wanSteps = scene.getSteps() > 0 ? scene.getSteps() : 4;
                if (wanSteps > 8) {
                    wanSteps = 4;
                }
                if (inputs.has("steps")) {
                    inputs.put("steps", wanSteps);
                }
                double wanCfg = scene.getCfgScale() > 0 ? scene.getCfgScale() : 2.0;
                if (wanCfg > 4.0) {
                    wanCfg = 3.0;
                }
                if (inputs.has("cfg")) {
                    inputs.put("cfg", wanCfg);
                }
            }
            
            // Inject filename prefix into VideoCombine or SaveVideo
            if ("VHS_VideoCombine".equals(classType) || "SaveVideo".equals(classType)) {
                inputs.put("filename_prefix", filenamePrefix);
                if ("VHS_VideoCombine".equals(classType)) {
                    inputs.put("save_output", true);
                }
            }
        }
    }
}


