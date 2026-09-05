package de.tki.comfymodels.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tki.comfymodels.domain.Scene;
import de.tki.comfymodels.service.IComfyLifecycleService;
import de.tki.comfymodels.domain.ComfyTemplate;
import de.tki.comfymodels.service.IComfyTemplateService;
import de.tki.comfymodels.service.PromptBlueprintApiService;
import de.tki.comfymodels.service.pipeline.MediaTranscodingService;
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
    private final ProcessTracker processTracker;
    private final IComfyLifecycleService lifecycleService;
    private final de.tki.comfymodels.service.IModelArchitectureService modelArchitectureService;
    private final EnvironmentBootstrapperImpl bootstrapper;
    private final PromptBlueprintApiService promptBlueprintApiService;
    private final IComfyTemplateService comfyTemplateService;
    private final de.tki.comfymodels.service.pipeline.MediaTranscodingService mediaTranscodingService;
    private final de.tki.comfymodels.service.pipeline.WorkflowTransformationService workflowTransformationService;

    private static final de.tki.comfymodels.service.pipeline.WorkflowTransformationService DEFAULT_TRANSFORMATION_SERVICE =
            new de.tki.comfymodels.service.pipeline.WorkflowTransformationService(null);

    public ComfyPipelineService(ConfigService configService) {
        this(configService, null, null, null, null, null, null, null, null);
    }

    public ComfyPipelineService(ConfigService configService, HttpClient httpClient) {
        this(configService, httpClient, null, null, null, null, null, null, null, null);
    }

    @Autowired
    public ComfyPipelineService(
            ConfigService configService,
            @Autowired(required = false) ProcessTracker processTracker,
            @Autowired(required = false) @org.springframework.context.annotation.Lazy IComfyLifecycleService lifecycleService,
            @Autowired(required = false) de.tki.comfymodels.service.IModelArchitectureService modelArchitectureService,
            @Autowired(required = false) EnvironmentBootstrapperImpl bootstrapper,
            @Autowired(required = false) PromptBlueprintApiService promptBlueprintApiService,
            @Autowired(required = false) IComfyTemplateService comfyTemplateService,
            @Autowired(required = false) de.tki.comfymodels.service.pipeline.MediaTranscodingService mediaTranscodingService,
            @Autowired(required = false) de.tki.comfymodels.service.pipeline.WorkflowTransformationService workflowTransformationService) {
        this(configService, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(),
                processTracker, lifecycleService, modelArchitectureService, bootstrapper,
                promptBlueprintApiService, comfyTemplateService, mediaTranscodingService, workflowTransformationService);
    }

    public ComfyPipelineService(
            ConfigService configService,
            HttpClient httpClient,
            ProcessTracker processTracker,
            IComfyLifecycleService lifecycleService,
            de.tki.comfymodels.service.IModelArchitectureService modelArchitectureService,
            EnvironmentBootstrapperImpl bootstrapper,
            PromptBlueprintApiService promptBlueprintApiService,
            IComfyTemplateService comfyTemplateService,
            de.tki.comfymodels.service.pipeline.MediaTranscodingService mediaTranscodingService,
            de.tki.comfymodels.service.pipeline.WorkflowTransformationService workflowTransformationService) {
        this.configService = configService;
        this.httpClient = httpClient != null ? httpClient : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        this.processTracker = processTracker;
        this.lifecycleService = lifecycleService;
        this.modelArchitectureService = modelArchitectureService;
        this.bootstrapper = bootstrapper;
        this.promptBlueprintApiService = promptBlueprintApiService;
        this.comfyTemplateService = comfyTemplateService;
        this.mediaTranscodingService = mediaTranscodingService;
        this.workflowTransformationService = workflowTransformationService;
    }

    private Process startProcess(ProcessBuilder pb) throws IOException {
        return processTracker != null ? processTracker.start(pb) : pb.start();
    }

    public static JSONObject convertUiToApi(JSONObject uiWorkflow) {
        return DEFAULT_TRANSFORMATION_SERVICE.convertUiToApi(uiWorkflow);
    }

    private String findExactModelName(String expected, List<String> available) {
        if (expected == null) return "";
        String expectedClean = expected.replaceAll("[/\\\\]+", "/").toLowerCase();
        String expectedName = expectedClean.contains("/") ? expectedClean.substring(expectedClean.lastIndexOf('/') + 1) : expectedClean;
        
        if (available != null) {
            // 1. Try exact match
            for (String av : available) {
                String avClean = av.replaceAll("[/\\\\]+", "/").toLowerCase();
                if (avClean.equals(expectedClean)) {
                    return av;
                }
            }
            // 2. Try match on filename only
            for (String av : available) {
                String avClean = av.replaceAll("[/\\\\]+", "/").toLowerCase();
                String avName = avClean.contains("/") ? avClean.substring(avClean.lastIndexOf('/') + 1) : avClean;
                if (avName.equals(expectedName)) {
                    return av;
                }
            }
            // 3. Try suffix-based match
            for (String av : available) {
                String avClean = av.replaceAll("[/\\\\]+", "/").toLowerCase();
                if (avClean.endsWith("/" + expectedName) || avClean.contains(expectedName)) {
                    return av;
                }
            }
        }
        return expected.replaceAll("[/\\\\]+", "/");
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
                            if (val instanceof JSONArray outerArray && outerArray.length() > 0) {
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
                                    for (int i = 0; i < options.length(); i++) {
                                        Object optVal = options.get(i);
                                        if (optVal instanceof String s && !s.equalsIgnoreCase("COMBO")) {
                                            optionsList.add(s);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("⚠️ [ComfyPipeline] Failed to fetch options for " + nodeClass + "/" + inputName + ": " + e.getMessage());
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

        int width = scene.getWidth() > 0 ? scene.getWidth() : 1280;
        int height = scene.getHeight() > 0 ? scene.getHeight() : 720;
        // Wan 2.2 requires dimensions to be divisible by 16
        width = Math.max(256, (width / 16) * 16);
        height = Math.max(256, (height / 16) * 16);
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

        String negativePrompt = "色调艳丽, 过曝, 静态, 细节模糊不清, 字幕, 风格, 作品, 画作, 画面, 静止, 整体发灰, 最差质量, 低质量, JPEG压缩残留, 丑陋的, 残缺的, 多余的手指, 变形的手指, 畸形, 水印, low quality, worst quality, deformed, distorted, blurry, static, text, watermark, signature";

        workflowJson.put("129:90", makeNode("VAELoader", new JSONObject().put("vae_name", vaeName)));
        workflowJson.put("129:84", makeNode("CLIPLoader", new JSONObject().put("clip_name", clipName).put("type", "wan").put("device", "default")));
        workflowJson.put("129:95", makeNode("UNETLoader", new JSONObject().put("unet_name", highNoiseUnet).put("weight_dtype", "default")));
        workflowJson.put("129:96", makeNode("UNETLoader", new JSONObject().put("unet_name", lowNoiseUnet).put("weight_dtype", "default")));
        boolean hasHighLora = availableLoras.stream().anyMatch(l -> l.toLowerCase().contains("wan") && l.toLowerCase().contains("high"));
        boolean hasLowLora = availableLoras.stream().anyMatch(l -> l.toLowerCase().contains("wan") && l.toLowerCase().contains("low"));
        boolean useLora = enableLora && hasHighLora && hasLowLora;

        workflowJson.put("129:131", makeNode("PrimitiveBoolean", new JSONObject().put("value", useLora)));
        if (useLora) {
            workflowJson.put("129:101", makeNode("LoraLoaderModelOnly", new JSONObject().put("lora_name", highNoiseLora).put("strength_model", 1.0).put("model", link("129:95", 0))));
            workflowJson.put("129:102", makeNode("LoraLoaderModelOnly", new JSONObject().put("lora_name", lowNoiseLora).put("strength_model", 1.0).put("model", link("129:96", 0))));
            workflowJson.put("129:116", makeNode("ComfySwitchNode", new JSONObject().put("switch", link("129:131", 0)).put("on_false", link("129:95", 0)).put("on_true", link("129:101", 0))));
            workflowJson.put("129:117", makeNode("ComfySwitchNode", new JSONObject().put("switch", link("129:131", 0)).put("on_false", link("129:96", 0)).put("on_true", link("129:102", 0))));
            workflowJson.put("129:104", makeNode("ModelSamplingSD3", new JSONObject().put("shift", 5.0).put("model", link("129:116", 0))));
            workflowJson.put("129:103", makeNode("ModelSamplingSD3", new JSONObject().put("shift", 5.0).put("model", link("129:117", 0))));
        } else {
            workflowJson.put("129:104", makeNode("ModelSamplingSD3", new JSONObject().put("shift", 5.0).put("model", link("129:95", 0))));
            workflowJson.put("129:103", makeNode("ModelSamplingSD3", new JSONObject().put("shift", 5.0).put("model", link("129:96", 0))));
        }

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

    public File convertImageToVideo(File imageFile, File audioFile, float durationSeconds, float fps, File outputFile) throws Exception {
        return convertImageToVideo(imageFile, audioFile, durationSeconds, fps, outputFile, 1920, 1080);
    }

    public File convertImageToVideo(File imageFile, File audioFile, float durationSeconds, float fps, File outputFile, int targetWidth, int targetHeight) throws Exception {
        if (mediaTranscodingService != null) {
            return mediaTranscodingService.convertImageToVideo(imageFile, audioFile, durationSeconds, fps, outputFile, targetWidth, targetHeight);
        }
        MediaTranscodingService fallbackTranscoder = new MediaTranscodingService(configService);
        return fallbackTranscoder.convertImageToVideo(imageFile, audioFile, durationSeconds, fps, outputFile, targetWidth, targetHeight);
    }

    private boolean isWanSetupComplete(List<String> unets, List<String> clips, List<String> vaes) {
        boolean hasWanUnet = !unets.isEmpty() && unets.stream().anyMatch(u -> {
            String lower = u.toLowerCase();
            return lower.contains("wan2") || lower.contains("wan_2") || lower.contains("wan-2") || lower.contains("wan.2") || (lower.contains("wan") && (lower.contains("i2v") || lower.contains("t2v") || lower.contains("14b") || lower.contains("1.3b")));
        });
        boolean hasWanClip = !clips.isEmpty() && clips.stream().anyMatch(c -> {
            String lower = c.toLowerCase();
            return lower.contains("umt5") || lower.contains("wan");
        });
        boolean hasWanVae = !vaes.isEmpty() && vaes.stream().anyMatch(v -> {
            String lower = v.toLowerCase();
            return lower.contains("wan");
        });
        return hasWanUnet && hasWanClip && hasWanVae;
    }

    private JSONObject generateSceneImageWorkflow(String serverUrl, Scene scene, String speakerImage, long seed, String filenamePrefix,
                                                  List<String> availableUnets, List<String> availableCheckpoints,
                                                  List<String> availableClips, List<String> availableVaes) throws Exception {
        File blueprintFile = null;
        File[] candidateFiles = new File[]{
            new File("workflows/z-image-turbo_text_to_image.json"),
            new File("workflows/z-image_text_to_image.json"),
            new File("workflows/flux2_klein_9b_text_to_image.json"),
            new File("workflows/qwen_image_edit_2509.json")
        };

        for (File bf : candidateFiles) {
            if (bf.exists()) {
                String name = bf.getName().toLowerCase();
                if (name.contains("turbo") && availableUnets.stream().anyMatch(u -> u.toLowerCase().contains("turbo"))) {
                    blueprintFile = bf;
                    break;
                } else if (name.contains("z-image") && availableUnets.stream().anyMatch(u -> u.toLowerCase().contains("z_image"))) {
                    blueprintFile = bf;
                    break;
                } else if (name.contains("flux") && availableUnets.stream().anyMatch(u -> u.toLowerCase().contains("flux"))) {
                    blueprintFile = bf;
                    break;
                }
            }
        }

        if (blueprintFile == null) {
            for (File bf : candidateFiles) {
                if (bf.exists()) {
                    blueprintFile = bf;
                    break;
                }
            }
        }

        JSONObject workflowJson = null;
        if (blueprintFile != null && blueprintFile.exists()) {
            String content = Files.readString(blueprintFile.toPath(), StandardCharsets.UTF_8);
            JSONObject parsed = new JSONObject(content);
            JSONObject flattened = flattenWorkflow(parsed);
            JSONObject apiPayload = convertUiToApi(flattened);
            PromptBlueprintApiService pbService = (this.promptBlueprintApiService != null) ? this.promptBlueprintApiService : new PromptBlueprintApiService();
            JSONObject mainObj = pbService.extractApiPayload(apiPayload.toString());
            JSONObject promptObj = mainObj.getJSONObject("prompt");

            int imgWidth = scene.getWidth() > 0 ? scene.getWidth() : 1920;
            int imgHeight = scene.getHeight() > 0 ? scene.getHeight() : 1080;
            PromptBlueprintApiService.PromptLabInputs inputs = new PromptBlueprintApiService.PromptLabInputs(
                scene.getPrompt(),
                "blurry, low quality, distortion, bad anatomy, deformed",
                imgWidth, imgHeight,
                (scene.getSteps() > 0 ? scene.getSteps() : 8),
                (scene.getCfgScale() > 0 ? scene.getCfgScale() : 1.0),
                seed, "euler", "simple", 1.0, 1, speakerImage
            );

            pbService.injectLabInputs(promptObj, inputs);
            workflowJson = mainObj;
        } else if (!availableCheckpoints.isEmpty() && comfyTemplateService != null) {
            ComfyTemplate template = comfyTemplateService.determineTemplateForModel(availableCheckpoints.get(0));
            if (template != null) {
                workflowJson = new JSONObject(template.getContent());
                injectPrompt(workflowJson, scene.getPrompt());
            }
        }

        if (workflowJson == null) {
            throw new IOException("No suitable image or video workflow template could be found for available ComfyUI models.");
        }

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
            logger.error("⚠️ [ComfyPipeline] Failed to check node class availability: " + e.getMessage());
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
                    logger.info("🔄 [ComfyPipeline] ComfyUI server is offline. Attempting auto-start...");
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
                    logger.info("✅ [ComfyPipeline] ComfyUI successfully started and healthy.");
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
                            logger.info("⚠️ [ComfyPipeline] ComfyUI-Video-Helper-Suite installation/load was already attempted in this session. Skipping to avoid restart loop.");
                        } else {
                            ATTEMPTED_INSTALLS.add("VHS_VideoCombine");
                            if (!folderExists) {
                                logger.info("⚠️ [ComfyPipeline] VHS_VideoCombine is missing and folder does not exist. Installing ComfyUI-Video-Helper-Suite...");
                                logger.info("🔄 [ComfyPipeline] Stopping ComfyUI server to install custom nodes...");
                                lifecycleService.stop();
                                
                                String pythonPath = configService.getPythonPath();
                                if (comfyPath != null && !comfyPath.trim().isEmpty()) {
                                    Path comfyDir = Paths.get(comfyPath);
                                    Path pythonExe = (pythonPath != null && !pythonPath.trim().isEmpty()) ? Paths.get(pythonPath) : null;
                                    bootstrapper.ensureVideoHelperSuiteInstalled(comfyDir, pythonExe, System.out::println);
                                }
                                
                                logger.info("🔄 [ComfyPipeline] Restarting ComfyUI server after installation...");
                                lifecycleService.start();
                            } else {
                                logger.info("🔄 [ComfyPipeline] VHS_VideoCombine node is not active but folder exists. Restarting ComfyUI server to load it...");
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
                            logger.info("✅ [ComfyPipeline] ComfyUI successfully restarted and healthy.");
                        }
                    }
                }

                // 1. Generate workflow JSON dynamically in code
                long seed = Math.abs(new java.util.Random().nextLong());
                String filenamePrefix = "videoarchitect_" + scene.getSceneId();

                // Upload speaker image only if explicitly present and valid for this scene
                String speakerImage = null;
                String speakerPath = (scene != null && scene.getSpeakerImagePath() != null && !scene.getSpeakerImagePath().trim().isEmpty())
                        ? scene.getSpeakerImagePath().trim() : null;
                if (speakerPath != null && !speakerPath.trim().isEmpty()) {
                    File speakerFile = new File(speakerPath.trim());
                    if (speakerFile.exists() && speakerFile.isFile()) {
                        logger.info("📤 [ComfyPipeline] Uploading speaker image for scene " + scene.getSceneId() + ": " + speakerFile.getName());
                        speakerImage = uploadFile(serverUrl, speakerFile);
                    } else {
                        logger.warn("⚠️ [ComfyPipeline] Speaker image path invalid or not found: " + speakerPath + ", proceeding with pure Text-to-Video.");
                    }
                }

                List<String> availableUnets = fetchObjectInfoOptions(serverUrl, "UNETLoader", "unet_name");
                List<String> availableCheckpoints = fetchAvailableCheckpoints(serverUrl);
                List<String> availableClips = fetchObjectInfoOptions(serverUrl, "CLIPLoader", "clip_name");
                List<String> availableVaes = fetchObjectInfoOptions(serverUrl, "VAELoader", "vae_name");

                boolean wanNodesAvailable = isNodeClassAvailable(serverUrl, "WanImageToVideo");
                boolean hasWanModels = isWanSetupComplete(availableUnets, availableClips, availableVaes);

                JSONObject workflowJson = null;
                if (wanNodesAvailable && hasWanModels) {
                    logger.info("🎬 [ComfyPipeline] Generating Wan 2.2 native video workflow...");
                    workflowJson = generateWanWorkflowJson(serverUrl, scene, speakerImage, seed, filenamePrefix);
                } else if (speakerImage != null && !speakerImage.trim().isEmpty() && hasWanModels) {
                    JSONObject blueprintJson = loadBlueprintWorkflow("Text to Video (Wan 2.2).json");
                    if (blueprintJson != null) {
                        logger.info("📄 [ComfyPipeline] Using Wan 2.2 blueprint workflow with start image.");
                        injectPrompt(blueprintJson, scene.getPrompt());
                        injectParamsIntoBlueprint(blueprintJson, seed, filenamePrefix, scene);
                        injectSpeakerImage(blueprintJson, speakerImage);
                        workflowJson = blueprintJson;
                    }
                }

                if (workflowJson == null) {
                    logger.info("🖼️ [ComfyPipeline] Dedicated video diffusion models not found; generating scene visuals via available image model...");
                    workflowJson = generateSceneImageWorkflow(serverUrl, scene, speakerImage, seed, filenamePrefix, availableUnets, availableCheckpoints, availableClips, availableVaes);
                }

                sanitizeWorkflow(workflowJson, serverUrl);

                // 2. Send prompt to ComfyUI
                ComfyOutputRef outputRef = null;
                boolean isWanAttempt = (wanNodesAvailable && hasWanModels);

                try {
                    String promptId = submitPrompt(serverUrl, workflowJson);
                    logger.info("🚀 [ComfyPipeline] Submitted job. Prompt ID: " + promptId);
                    outputRef = pollHistoryForOutput(serverUrl, promptId);
                } catch (Exception ex) {
                    if (isWanAttempt) {
                        logger.warn("⚠️ [ComfyPipeline] Video model execution failed ({}); falling back to available image diffusion model synthesis...", ex.getMessage());
                        JSONObject fallbackWorkflow = generateSceneImageWorkflow(serverUrl, scene, speakerImage, seed, filenamePrefix, availableUnets, availableCheckpoints, availableClips, availableVaes);
                        sanitizeWorkflow(fallbackWorkflow, serverUrl);
                        String fallbackPromptId = submitPrompt(serverUrl, fallbackWorkflow);
                        logger.info("🚀 [ComfyPipeline] Submitted fallback job. Prompt ID: " + fallbackPromptId);
                        outputRef = pollHistoryForOutput(serverUrl, fallbackPromptId);
                    } else {
                        throw ex;
                    }
                }

                String finishedFilename = outputRef != null ? outputRef.filename : null;
                logger.info("✅ [ComfyPipeline] Job finished. Filename: {}" + 
                        (outputRef != null && !outputRef.subfolder.isEmpty() ? " (subfolder: " + outputRef.subfolder + ")" : ""),
                        finishedFilename);

                // 4. Find the video/image file in the ComfyUI output directory
                String comfyOutputPath = configService.getResolvedOutputDir();
                if (comfyOutputPath == null || comfyOutputPath.trim().isEmpty()) {
                    comfyOutputPath = new File("output").getAbsolutePath();
                }

                File rawOutputFile = resolveOutputFile(comfyOutputPath, outputRef, scene.getSceneId());
                if (rawOutputFile == null && finishedFilename != null && !finishedFilename.trim().isEmpty()) {
                    rawOutputFile = downloadOutput(serverUrl, outputRef, scene.getSceneId());
                }

                File finalVideoFile = new File(new File("").getAbsoluteFile(), "scene_" + scene.getSceneId() + "_final.mp4");

                float duration = 5.0f;
                if (scene.getEndFrame() > scene.getStartFrame()) {
                    duration = Math.max(1.0f, (scene.getEndFrame() - scene.getStartFrame()) / 24.0f);
                }
                float fps = 24.0f;

                if (rawOutputFile != null && rawOutputFile.exists()) {
                    boolean isImage = rawOutputFile.getName().toLowerCase().matches(".*\\.(png|jpg|jpeg|webp)$");
                    String audioPath = scene.getAudioPath();
                    File audioFile = (audioPath != null && !audioPath.trim().isEmpty()) ? new File(audioPath) : null;

                    if (isImage) {
                        logger.info("🖼️ [ComfyPipeline] Raw output is an image. Converting to MP4 scene video...");
                        convertImageToVideo(rawOutputFile, audioFile, duration, fps, finalVideoFile, scene.getWidth(), scene.getHeight());
                    } else if (audioFile != null && audioFile.exists()) {
                        String ffmpegPath = configService.getFfmpegPath();
                        List<String> cmd = List.of(
                            ffmpegPath,
                            "-y",
                            "-i", rawOutputFile.getAbsolutePath(),
                            "-i", audioFile.getAbsolutePath(),
                            "-c:v", "copy",
                            "-c:a", "aac",
                            "-shortest",
                            finalVideoFile.getAbsolutePath()
                        );

                        logger.info("🎬 [ComfyPipeline] Running FFmpeg audio muxing command: " + String.join(" ", cmd));
                        ProcessBuilder pb = new ProcessBuilder(cmd);
                        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                        Process process = startProcess(pb);

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

                        if (rawOutputFile.exists() && !rawOutputFile.getAbsolutePath().equals(finalVideoFile.getAbsolutePath())) {
                            rawOutputFile.delete();
                        }
                    } else {
                        logger.info("ℹ️ [ComfyPipeline] No audio file found or specified for scene. Copying raw video to final path: " + finalVideoFile.getAbsolutePath());
                        Files.copy(rawOutputFile.toPath(), finalVideoFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }

                    scene.setVideoPath(finalVideoFile.getAbsolutePath());
                    return finalVideoFile;
                } else {
                    if (strictMode) {
                        throw new Exception("Strict mode enabled: ComfyUI finished generation, but no output video or image file was returned or found locally.");
                    }
                    File fallbackVideo = generateSimulatedVideo(scene);
                    Files.copy(fallbackVideo.toPath(), finalVideoFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    fallbackVideo.delete();
                    scene.setVideoPath(finalVideoFile.getAbsolutePath());
                    return finalVideoFile;
                }

            } catch (Exception e) {
                if (strictMode) {
                    logger.error("🔴 [ComfyPipeline] Failed generating scene via ComfyUI (Strict Mode): " + e.getMessage());
                    throw new RuntimeException("Strict mode generation failed: " + e.getMessage(), e);
                }
                logger.error("🔴 [ComfyPipeline] Failed generating scene via ComfyUI: " + e.getMessage() + ". Generating simulated fallback video.");
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
            String sourceClip = scene.getSourceClipPath();
            if (sourceClip != null && !sourceClip.trim().isEmpty() && new File(sourceClip).exists()) {
                logger.info("🎬 [ComfyPipeline] Montage mode: Using source clip as input: " + sourceClip);
                scene.setSpeakerImagePath(sourceClip);
            }
            return generateScene(scene).join();
        });
    }


    private void validateFfmpeg() throws java.io.FileNotFoundException {
        String ffmpegPath = configService.getFfmpegPath();
        File ffmpegFile = new File(ffmpegPath);
        
        boolean isGlobal = "ffmpeg".equals(ffmpegPath);
        boolean available = false;
        
        if (isGlobal) {
            try {
                Process p = startProcess(new ProcessBuilder("ffmpeg", "-version"));
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

        logger.info("🎥 [ComfyPipeline] Generating placeholder video with scene info (Duration: " + duration + "s) to: " + targetFile);
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
        Process process = startProcess(pb);
        
        // Consume stream
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            while (r.readLine() != null) {}
        }
        
        int exitCode = process.waitFor();
        if (exitCode == 0 && Files.exists(targetFile) && Files.size(targetFile) > 1024) {
            logger.info("🎥 [ComfyPipeline] Generated placeholder video with scene overlay: " + targetFile);
            return targetFile.toFile();
        } else {
            // Fallback to minimal color source without text if drawtext fails (e.g. missing fonts)
            logger.info("⚠️ [ComfyPipeline] Drawtext failed (exit code " + exitCode + "). Falling back to plain color source.");
            ProcessBuilder pbFallback = new ProcessBuilder(
                configService.getFfmpegPath(), "-y",
                "-f", "lavfi",
                "-i", "color=c=0x1a1a2e:s=720x720:d=" + duration + ":r=30",
                "-pix_fmt", "yuv420p",
                "-c:v", "libx264",
                targetFile.toString()
            );
            pbFallback.redirectErrorStream(true);
            Process fallbackProcess = startProcess(pbFallback);
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
            Process p = startProcess(pb);
            String line;
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                line = r.readLine();
            }
            p.waitFor();
            if (line != null && !line.trim().isEmpty()) {
                return Double.parseDouble(line.trim());
            }
        } catch (Exception e) {
            logger.error("⚠️ [ComfyPipeline] Could not probe duration: " + e.getMessage());
        }
        return 0.0;
    }

    public static JSONObject flattenWorkflow(JSONObject uiWorkflow) {
        return DEFAULT_TRANSFORMATION_SERVICE.flattenWorkflow(uiWorkflow);
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
                logger.error("⚠️ [ComfyPipeline] Failed to parse custom workflow.json: " + e);
            }
        }
        return "{}";
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
                    && !text.toLowerCase().contains("静止") && !text.toLowerCase().contains("最差质量")) {
                    inputs.put("text", promptText);
                    injected = true;
                    logger.info("📥 [ComfyPipeline] Injected visual_prompt into CLIPTextEncode node ID: " + key);
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
                logger.info("📥 [ComfyPipeline] Injected visual_prompt into Video Gen Subgraph node ID: " + key);
            } else if ("PrimitiveStringMultiline".equals(classType)) {
                // Primitive multiline string inputs (often used as prompt nodes)
                inputs.put("value", promptText);
                injected = true;
                logger.info("📥 [ComfyPipeline] Injected visual_prompt into PrimitiveStringMultiline node ID: " + key);
            }
        }

        if (!injected) {
            throw new RuntimeException("Could not find suitable prompt input node in workflow template!");
        }
    }

    public void sanitizeWorkflow(JSONObject workflowJson, String serverUrl) {
        List<String> availableCkpts = fetchAvailableCheckpoints(serverUrl);
        List<String> availableUnets = fetchObjectInfoOptions(serverUrl, "UNETLoader", "unet_name");
        List<String> availableClips = fetchObjectInfoOptions(serverUrl, "CLIPLoader", "clip_name");
        List<String> availableVaes = fetchObjectInfoOptions(serverUrl, "VAELoader", "vae_name");
        List<String> availableLoras = fetchObjectInfoOptions(serverUrl, "LoraLoader", "lora_name");

        JSONObject promptObj = workflowJson.has("prompt") ? workflowJson.getJSONObject("prompt") : workflowJson;
        for (String key : new ArrayList<>(promptObj.keySet())) {
            JSONObject node = promptObj.optJSONObject(key);
            if (node == null) continue;
            String classType = node.optString("class_type", "");
            JSONObject inputs = node.optJSONObject("inputs");
            if (inputs == null) continue;

            if ("CheckpointLoaderSimple".equals(classType)) {
                String currentCkpt = inputs.optString("ckpt_name", "");
                if (!availableCkpts.isEmpty() && !availableCkpts.contains(currentCkpt)) {
                    String match = findExactModelName(currentCkpt, availableCkpts);
                    if (!availableCkpts.contains(match)) match = availableCkpts.get(0);
                    inputs.put("ckpt_name", match);
                    logger.info("🔄 [ComfyPipeline] Sanitized checkpoint '{}' -> '{}' in node {}", currentCkpt, match, key);
                }
            } else if ("UNETLoader".equals(classType) || "UNetLoader".equals(classType)) {
                String currentUnet = inputs.optString("unet_name", "");
                if (!availableUnets.isEmpty() && !availableUnets.contains(currentUnet)) {
                    String match = findExactModelName(currentUnet, availableUnets);
                    if (!availableUnets.contains(match)) match = availableUnets.get(0);
                    inputs.put("unet_name", match);
                    logger.info("🔄 [ComfyPipeline] Sanitized UNET '{}' -> '{}' in node {}", currentUnet, match, key);
                }
            } else if ("CLIPLoader".equals(classType)) {
                String currentClip = inputs.optString("clip_name", "");
                if (!availableClips.isEmpty() && !availableClips.contains(currentClip)) {
                    String match = findExactModelName(currentClip, availableClips);
                    if (!availableClips.contains(match)) match = availableClips.get(0);
                    inputs.put("clip_name", match);
                    logger.info("🔄 [ComfyPipeline] Sanitized CLIP '{}' -> '{}' in node {}", currentClip, match, key);
                }
            } else if ("VAELoader".equals(classType)) {
                String currentVae = inputs.optString("vae_name", "");
                if (!availableVaes.isEmpty() && !availableVaes.contains(currentVae)) {
                    String match = findExactModelName(currentVae, availableVaes);
                    if (!availableVaes.contains(match)) match = availableVaes.get(0);
                    inputs.put("vae_name", match);
                    logger.info("🔄 [ComfyPipeline] Sanitized VAE '{}' -> '{}' in node {}", currentVae, match, key);
                }
            } else if ("LoraLoaderModelOnly".equals(classType) || "LoraLoader".equals(classType)) {
                String currentLora = inputs.optString("lora_name", "");
                if (!availableLoras.isEmpty() && !availableLoras.contains(currentLora)) {
                    String match = findExactModelName(currentLora, availableLoras);
                    if (availableLoras.contains(match)) {
                        inputs.put("lora_name", match);
                    }
                }
            } else if ("KSamplerSelect".equals(classType)) {
                String sn = inputs.optString("sampler_name", "");
                if (sn.isBlank() || sn.equalsIgnoreCase("COMBO") || sn.equalsIgnoreCase("Auto")) {
                    inputs.put("sampler_name", "euler");
                }
            }

            for (String ik : new ArrayList<>(inputs.keySet())) {
                Object val = inputs.opt(ik);
                if (val instanceof String s && !s.isBlank()) {
                    if (s.equalsIgnoreCase("COMBO") || s.equalsIgnoreCase("Auto")) {
                        if (ik.contains("sampler")) {
                            inputs.put(ik, "euler");
                        } else if (ik.contains("scheduler")) {
                            inputs.put(ik, "simple");
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
                            if (val instanceof JSONArray outerArray && outerArray.length() > 0) {
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
                                    for (int i = 0; i < options.length(); i++) {
                                        Object optVal = options.get(i);
                                        if (optVal instanceof String s && !s.equalsIgnoreCase("COMBO")) {
                                            checkpoints.add(s);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("⚠️ [ComfyPipeline] Failed to fetch checkpoints from ComfyUI: " + e);
        }
        return checkpoints;
    }

    private String submitPrompt(String serverUrl, JSONObject workflowJson) throws IOException, InterruptedException {
        // Wrap payload in 'prompt' block if it doesn't already have one
        JSONObject payload = workflowJson.has("prompt") ? workflowJson : new JSONObject().put("prompt", workflowJson);
        JSONObject promptObj = payload.optJSONObject("prompt");
        if (promptObj != null) {
            de.tki.comfymodels.service.PromptBlueprintApiService.sanitizeAllSeedsInPrompt(promptObj);
        }

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
            logger.info("💾 [ComfyPipeline] Downloaded output to: " + targetFile);
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
                    logger.info("📥 [ComfyPipeline] Injected speaker image '" + filename + "' into LoadImage node ID: " + key);
                }
            }
        }
        if (!injected) {
            logger.info("ℹ️ [ComfyPipeline] No LoadImage node found in workflow to inject speaker image.");
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
                    logger.info("📥 [ComfyPipeline] Injected narration audio '" + filename + "' into LoadAudio node ID: " + key);
                }
            }
        }
        if (!injected) {
            logger.info("ℹ️ [ComfyPipeline] No LoadAudio node found in workflow to inject narration audio.");
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
            logger.error("⚠️ [ComfyPipeline] Failed to load blueprint " + blueprintFile.getName() + ": " + e.getMessage());
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
                    logger.info("📥 [ComfyPipeline] Injected scene duration " + sceneDuration + "s into video subgraph node ID: " + key);
                }
            }
            
            // Inject seed and Wan-safe sampler params into KSamplers or video nodes
            long clampedSeed = de.tki.comfymodels.service.PromptBlueprintApiService.clampSeedForNode(classType, seed);
            if (inputs.has("seed")) {
                inputs.put("seed", clampedSeed);
            }
            if (inputs.has("noise_seed")) {
                inputs.put("noise_seed", clampedSeed);
            }

            if ("KSampler".equals(classType) || "KSamplerAdvanced".equals(classType)) {
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
        de.tki.comfymodels.service.PromptBlueprintApiService.sanitizeAllSeedsInPrompt(promptObj);
    }
}


