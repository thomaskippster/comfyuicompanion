package de.tki.comfyuicompanion.service.pipeline;

import de.tki.comfyuicompanion.domain.ComfyTemplate;
import de.tki.comfyuicompanion.domain.HardwareProfile;
import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.IComfyTemplateService;
import de.tki.comfyuicompanion.service.IHardwareProfileService;
import de.tki.comfyuicompanion.service.IModelArchitectureService;
import de.tki.comfyuicompanion.service.PromptBlueprintApiService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Service component responsible for constructing ComfyUI execution graphs,
 * including Wan 2.2 video workflows, fallback text-to-image workflows, and model sanitization.
 */
@Component
public class VideoWorkflowBuilder {
    private static final Logger logger = LoggerFactory.getLogger(VideoWorkflowBuilder.class);

    private final ComfyNodeInspectionClient inspectionClient;
    private final IModelArchitectureService modelArchitectureService;
    private final IHardwareProfileService hardwareProfileService;
    private final PromptBlueprintApiService promptBlueprintApiService;
    private final IComfyTemplateService comfyTemplateService;
    private final WorkflowTransformationService workflowTransformationService;

    public VideoWorkflowBuilder(ComfyNodeInspectionClient inspectionClient) {
        this(inspectionClient, null, null, null, null, null);
    }

    @Autowired
    public VideoWorkflowBuilder(
            ComfyNodeInspectionClient inspectionClient,
            @Autowired(required = false) IModelArchitectureService modelArchitectureService,
            @Autowired(required = false) IHardwareProfileService hardwareProfileService,
            @Autowired(required = false) PromptBlueprintApiService promptBlueprintApiService,
            @Autowired(required = false) IComfyTemplateService comfyTemplateService,
            @Autowired(required = false) WorkflowTransformationService workflowTransformationService) {
        this.inspectionClient = inspectionClient != null ? inspectionClient : new ComfyNodeInspectionClient();
        this.modelArchitectureService = modelArchitectureService;
        this.hardwareProfileService = hardwareProfileService;
        this.promptBlueprintApiService = promptBlueprintApiService != null ? promptBlueprintApiService : new PromptBlueprintApiService();
        this.comfyTemplateService = comfyTemplateService;
        this.workflowTransformationService = workflowTransformationService != null ? workflowTransformationService
                : new WorkflowTransformationService(null);
    }

    public static JSONObject makeNode(String classType, JSONObject inputs) {
        JSONObject node = new JSONObject();
        node.put("class_type", classType);
        node.put("inputs", inputs);
        return node;
    }

    public static JSONArray link(String nodeId, int outputIndex) {
        return new JSONArray().put(nodeId).put(outputIndex);
    }

    public boolean isWanSetupComplete(List<String> unets, List<String> clips, List<String> vaes) {
        boolean hasWanUnet = !unets.isEmpty() && unets.stream().anyMatch(u -> {
            String lower = u.toLowerCase();
            return lower.contains("wan2") || lower.contains("wan_2") || lower.contains("wan-2") || lower.contains("wan.2")
                    || (lower.contains("wan") && (lower.contains("i2v") || lower.contains("t2v") || lower.contains("14b") || lower.contains("1.3b")));
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

    private String resolveVideoModel(String key, String defaultVal, List<String> availableOptions) {
        String resolved = defaultVal;
        if (modelArchitectureService != null) {
            IModelArchitectureService.ModelDefaults defaults = modelArchitectureService.getDefaultsForModel(key);
            if (defaults != null && defaults.vaeName != null && key.startsWith("video_")) {
                resolved = defaults.vaeName;
            }
        }
        return inspectionClient.findExactModelName(resolved, availableOptions);
    }

    /**
     * Constructs a specialized Wan 2.2 native video execution graph.
     *
     * @param serverUrl      active ComfyUI URL
     * @param scene          scene specification
     * @param speakerImage   uploaded speaker image reference (or null)
     * @param seed           sampling seed
     * @param filenamePrefix prefix for output video
     * @return workflow JSON
     */
    public JSONObject generateWanWorkflowJson(String serverUrl, Scene scene, String speakerImage, long seed, String filenamePrefix) {
        String promptText = scene.getPrompt();
        String cleanSpeakerImage = (speakerImage == null || speakerImage.trim().isEmpty()) ? null : speakerImage;
        JSONObject workflowJson = new JSONObject();

        List<String> availableUnets = inspectionClient.fetchObjectInfoOptions(serverUrl, "UNETLoader", "unet_name");
        List<String> availableLoras = inspectionClient.fetchObjectInfoOptions(serverUrl, "LoraLoader", "lora_name");
        List<String> availableClips = inspectionClient.fetchObjectInfoOptions(serverUrl, "CLIPLoader", "clip_name");
        List<String> availableVaes = inspectionClient.fetchObjectInfoOptions(serverUrl, "VAELoader", "vae_name");

        String highNoiseUnet = resolveVideoModel("video_wan_high_unet", "Wan2.2\\wan2.2_i2v_high_noise_14B_fp8_scaled.safetensors", availableUnets);
        String lowNoiseUnet = resolveVideoModel("video_wan_low_unet", "Wan2.2\\wan2.2_i2v_low_noise_14B_fp8_scaled.safetensors", availableUnets);
        String highNoiseLora = resolveVideoModel("video_wan_high_lora", "wan2.2_i2v_lightx2v_4steps_lora_v1_high_noise.safetensors", availableLoras);
        String lowNoiseLora = resolveVideoModel("video_wan_low_lora", "wan2.2_i2v_lightx2v_4steps_lora_v1_low_noise.safetensors", availableLoras);
        String clipName = resolveVideoModel("video_wan_clip", "umt5_xxl_fp8_e4m3fn_scaled.safetensors", availableClips);
        String vaeName = resolveVideoModel("video_wan_vae", "wan_2.1_vae.safetensors", availableVaes);

        int width = scene.getWidth() > 0 ? scene.getWidth() : 832;
        int height = scene.getHeight() > 0 ? scene.getHeight() : 480;

        if (hardwareProfileService != null) {
            HardwareProfile profile = hardwareProfileService.getHardwareProfile();
            if (profile != null && profile.tier() != null && profile.isWeakSystem()) {
                long maxSafe = profile.tier().getMaxSafePixels();
                if ((long) width * height > maxSafe) {
                    double aspect = (double) width / (double) height;
                    int targetW = profile.tier().getRecommendedWidth();
                    int targetH = (int) Math.round(targetW / aspect);
                    logger.warn("⚠️ [VideoWorkflowBuilder] Resolution {}x{} exceeds safe budget. Clamping to {}x{}.", width, height, targetW, targetH);
                    width = targetW;
                    height = targetH;
                }
            }
        }

        width = Math.max(256, (width / 16) * 16);
        height = Math.max(256, (height / 16) * 16);

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
            workflowJson.put("20", makeNode("EmptyImage", new JSONObject().put("width", width).put("height", height).put("batch_size", 1).put("color", 0)));
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

    /**
     * Synthesizes an image-based workflow when dedicated video models are absent.
     */
    public JSONObject generateSceneImageWorkflow(String serverUrl, Scene scene, String speakerImage, long seed, String filenamePrefix,
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
            JSONObject flattened = workflowTransformationService.flattenWorkflow(parsed);
            JSONObject apiPayload = workflowTransformationService.convertUiToApi(flattened);
            JSONObject mainObj = promptBlueprintApiService.extractApiPayload(apiPayload.toString());
            JSONObject promptObj = mainObj.getJSONObject("prompt");

            int imgWidth = scene.getWidth() > 0 ? scene.getWidth() : 832;
            int imgHeight = scene.getHeight() > 0 ? scene.getHeight() : 480;
            PromptBlueprintApiService.PromptLabInputs inputs = new PromptBlueprintApiService.PromptLabInputs(
                    scene.getPrompt(),
                    "blurry, low quality, distortion, bad anatomy, deformed",
                    imgWidth, imgHeight,
                    (scene.getSteps() > 0 ? scene.getSteps() : 8),
                    (scene.getCfgScale() > 0 ? scene.getCfgScale() : 1.0),
                    seed, "euler", "simple", 1.0, 1, speakerImage
            );

            promptBlueprintApiService.injectLabInputs(promptObj, inputs);
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
                if (!text.toLowerCase().contains("blurry") && !text.toLowerCase().contains("bad hands")
                        && !text.toLowerCase().contains("worst quality") && !text.toLowerCase().contains("low quality")
                        && !text.toLowerCase().contains("静止") && !text.toLowerCase().contains("最差质量")) {
                    inputs.put("text", promptText);
                    injected = true;
                }
            } else if ("PrimitiveStringMultiline".equals(classType)) {
                inputs.put("value", promptText);
                injected = true;
            }
        }

        if (!injected) {
            throw new RuntimeException("Could not find suitable prompt input node in workflow template!");
        }
    }

    /**
     * Sanitizes node inputs against server availability to prevent runtime crashes.
     */
    public void sanitizeWorkflow(JSONObject workflowJson, String serverUrl) {
        List<String> availableCkpts = inspectionClient.fetchAvailableCheckpoints(serverUrl);
        List<String> availableUnets = inspectionClient.fetchObjectInfoOptions(serverUrl, "UNETLoader", "unet_name");
        List<String> availableClips = inspectionClient.fetchObjectInfoOptions(serverUrl, "CLIPLoader", "clip_name");
        List<String> availableVaes = inspectionClient.fetchObjectInfoOptions(serverUrl, "VAELoader", "vae_name");
        List<String> availableLoras = inspectionClient.fetchObjectInfoOptions(serverUrl, "LoraLoader", "lora_name");

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
                    String match = inspectionClient.findExactModelName(currentCkpt, availableCkpts);
                    if (!availableCkpts.contains(match)) match = availableCkpts.get(0);
                    inputs.put("ckpt_name", match);
                    logger.info("🔄 [VideoWorkflowBuilder] Sanitized checkpoint '{}' -> '{}' in node {}", currentCkpt, match, key);
                }
            } else if ("UNETLoader".equals(classType) || "UNetLoader".equals(classType)) {
                String currentUnet = inputs.optString("unet_name", "");
                if (!availableUnets.isEmpty() && !availableUnets.contains(currentUnet)) {
                    String match = inspectionClient.findExactModelName(currentUnet, availableUnets);
                    if (!availableUnets.contains(match)) match = availableUnets.get(0);
                    inputs.put("unet_name", match);
                    logger.info("🔄 [VideoWorkflowBuilder] Sanitized UNET '{}' -> '{}' in node {}", currentUnet, match, key);
                }
            } else if ("CLIPLoader".equals(classType)) {
                String currentClip = inputs.optString("clip_name", "");
                if (!availableClips.isEmpty() && !availableClips.contains(currentClip)) {
                    String match = inspectionClient.findExactModelName(currentClip, availableClips);
                    if (!availableClips.contains(match)) match = availableClips.get(0);
                    inputs.put("clip_name", match);
                    logger.info("🔄 [VideoWorkflowBuilder] Sanitized CLIP '{}' -> '{}' in node {}", currentClip, match, key);
                }
            } else if ("VAELoader".equals(classType)) {
                String currentVae = inputs.optString("vae_name", "");
                if (!availableVaes.isEmpty() && !availableVaes.contains(currentVae)) {
                    String match = inspectionClient.findExactModelName(currentVae, availableVaes);
                    if (!availableVaes.contains(match)) match = availableVaes.get(0);
                    inputs.put("vae_name", match);
                    logger.info("🔄 [VideoWorkflowBuilder] Sanitized VAE '{}' -> '{}' in node {}", currentVae, match, key);
                }
            } else if ("LoraLoaderModelOnly".equals(classType) || "LoraLoader".equals(classType)) {
                String currentLora = inputs.optString("lora_name", "");
                if (!availableLoras.isEmpty() && !availableLoras.contains(currentLora)) {
                    String match = inspectionClient.findExactModelName(currentLora, availableLoras);
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
}
