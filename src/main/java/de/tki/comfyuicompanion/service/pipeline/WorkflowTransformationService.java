package de.tki.comfyuicompanion.service.pipeline;

import de.tki.comfyuicompanion.service.gateway.ComfyUiGateway;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for ComfyUI graph and workflow transformations:
 * - UI-to-API conversion (resolving links and mapping widgets)
 * - Subgraph flattening (inlining nested subgraphs with ID offsets and slot remapping)
 * - Dynamic introspection via ComfyUiGateway with static and node-level fallbacks
 */
@Service
public class WorkflowTransformationService {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowTransformationService.class);

    private static final Map<String, List<String>> FALLBACK_WIDGET_MAP = new HashMap<>();
    static {
        FALLBACK_WIDGET_MAP.put("CheckpointLoaderSimple", List.of("ckpt_name"));
        FALLBACK_WIDGET_MAP.put("LoadImage", List.of("image", "upload"));
        FALLBACK_WIDGET_MAP.put("LoadAudio", List.of("audio", "audioUI", "upload"));
        FALLBACK_WIDGET_MAP.put("LoraLoaderModelOnly", List.of("lora_name", "strength_model"));
        FALLBACK_WIDGET_MAP.put("LTXAVTextEncoderLoader", List.of("text_encoder", "ckpt_name", "device"));
        FALLBACK_WIDGET_MAP.put("LatentUpscaleModelLoader", List.of("model_name"));
        FALLBACK_WIDGET_MAP.put("EmptyLTXVLatentVideo", List.of("width", "height", "length", "batch_size"));
        FALLBACK_WIDGET_MAP.put("CFGGuider", List.of("cfg"));
        FALLBACK_WIDGET_MAP.put("RandomNoise", List.of("noise_seed", "control_after_generate"));
        FALLBACK_WIDGET_MAP.put("KSamplerSelect", List.of("sampler_name"));
        FALLBACK_WIDGET_MAP.put("ManualSigmas", List.of("sigmas"));
        FALLBACK_WIDGET_MAP.put("VAEDecodeTiled", List.of("tile_size", "overlap", "temporal_size", "temporal_overlap"));
        FALLBACK_WIDGET_MAP.put("LTXVPreprocess", List.of("img_compression"));
        FALLBACK_WIDGET_MAP.put("ResizeImagesByLongerEdge", List.of("longer_edge"));
        FALLBACK_WIDGET_MAP.put("SaveVideo", List.of("filename_prefix", "format", "codec"));
        FALLBACK_WIDGET_MAP.put("VHS_VideoCombine", List.of("filename_prefix", "format", "frame_rate", "loop_count", "pix_fmt", "crf", "save_output", "pingpong"));
        FALLBACK_WIDGET_MAP.put("SolidMask", List.of("value", "width", "height"));
        FALLBACK_WIDGET_MAP.put("98ee9e5b-467b-40aa-a534-36033f27d0b4", List.of("value", "value_1", "value_2", "value_3", "value_4", "ckpt_name", "lora_name", "text_encoder", "model_name", "lora_name_1", "noise_seed"));
        FALLBACK_WIDGET_MAP.put("ComfyMathExpression", List.of("expression"));
        FALLBACK_WIDGET_MAP.put("LTXVImgToVideoInplace", List.of("strength", "bypass"));
        FALLBACK_WIDGET_MAP.put("LTXVConditioning", List.of("frame_rate"));
        FALLBACK_WIDGET_MAP.put("CLIPTextEncode", List.of("text"));
        FALLBACK_WIDGET_MAP.put("LTXVAudioVAELoader", List.of("ckpt_name"));
        FALLBACK_WIDGET_MAP.put("CreateVideo", List.of("fps"));
        FALLBACK_WIDGET_MAP.put("PrimitiveInt", List.of("value"));
        FALLBACK_WIDGET_MAP.put("PrimitiveFloat", List.of("value"));
        FALLBACK_WIDGET_MAP.put("PrimitiveStringMultiline", List.of("value"));
        FALLBACK_WIDGET_MAP.put("LTXVAudioVAEDecode", List.of());
        FALLBACK_WIDGET_MAP.put("LTXVConcatAVLatent", List.of());
        FALLBACK_WIDGET_MAP.put("LTXVSeparateAVLatent", List.of());
        FALLBACK_WIDGET_MAP.put("LTXVLatentUpsampler", List.of());
        FALLBACK_WIDGET_MAP.put("CLIPLoader", List.of("clip_name", "type", "device"));
        FALLBACK_WIDGET_MAP.put("DualCLIPLoader", List.of("clip_name1", "clip_name2", "type", "device"));
        FALLBACK_WIDGET_MAP.put("VAELoader", List.of("vae_name"));
        FALLBACK_WIDGET_MAP.put("UNETLoader", List.of("unet_name", "weight_dtype"));
        FALLBACK_WIDGET_MAP.put("ModelSamplingSD3", List.of("shift"));
        FALLBACK_WIDGET_MAP.put("ModelSamplingAuraFlow", List.of("shift"));
        FALLBACK_WIDGET_MAP.put("ModelSamplingFlux", List.of("max_shift", "base_shift", "width", "height"));
        FALLBACK_WIDGET_MAP.put("ModelSamplingContinuousEDM", List.of("sampling", "sigma_max", "sigma_min"));
        FALLBACK_WIDGET_MAP.put("FluxGuidance", List.of("guidance"));
        FALLBACK_WIDGET_MAP.put("CFGNorm", List.of("strength", "pre_cfg"));
        FALLBACK_WIDGET_MAP.put("BasicScheduler", List.of("scheduler", "steps", "denoise"));
        FALLBACK_WIDGET_MAP.put("EmptyLatentImage", List.of("width", "height", "batch_size"));
        FALLBACK_WIDGET_MAP.put("EmptySD3LatentImage", List.of("width", "height", "batch_size"));
        FALLBACK_WIDGET_MAP.put("KSampler", List.of("seed", "control_after_generate", "steps", "cfg", "sampler_name", "scheduler", "denoise"));
        FALLBACK_WIDGET_MAP.put("SaveImage", List.of("filename_prefix"));
        FALLBACK_WIDGET_MAP.put("PreviewImage", List.of());
        FALLBACK_WIDGET_MAP.put("ConditioningZeroOut", List.of());
        FALLBACK_WIDGET_MAP.put("SaveImageAdvanced", List.of("filename_prefix", "format", "format.bit_depth", "format.input_color_space"));
        FALLBACK_WIDGET_MAP.put("PrimitiveBoolean", List.of("value"));
        FALLBACK_WIDGET_MAP.put("TextEncodeQwenImageEditPlus", List.of("prompt"));
        FALLBACK_WIDGET_MAP.put("ImageScaleToTotalPixels", List.of("upscale_method", "megapixels", "resolution_steps"));
        FALLBACK_WIDGET_MAP.put("EmptyFlux2LatentImage", List.of("width", "height", "batch_size"));
        FALLBACK_WIDGET_MAP.put("Flux2Scheduler", List.of("steps", "width", "height"));
        FALLBACK_WIDGET_MAP.put("SamplerCustomAdvanced", List.of());
        FALLBACK_WIDGET_MAP.put("SDTurboScheduler", List.of("steps", "denoise"));
        FALLBACK_WIDGET_MAP.put("BetaScheduler", List.of("scheduler", "steps", "denoise"));
        FALLBACK_WIDGET_MAP.put("LoraLoader", List.of("lora_name", "strength_model", "strength_clip"));
        FALLBACK_WIDGET_MAP.put("WanImageToVideo", List.of("width", "height", "length", "batch_size"));
        FALLBACK_WIDGET_MAP.put("Wan22ImageToVideoLatent", List.of("width", "height", "length", "batch_size"));
        FALLBACK_WIDGET_MAP.put("KSamplerAdvanced", List.of("add_noise", "noise_seed", "control_after_generate", "steps", "cfg", "sampler_name", "scheduler", "start_at_step", "end_at_step", "return_with_leftover_noise"));
    }

    private final ComfyUiGateway comfyUiGateway;

    /**
     * Constructs a new WorkflowTransformationService with optional gateway introspection.
     *
     * @param comfyUiGateway optional gateway for live ComfyUI node widget lookup
     */
    @Autowired
    public WorkflowTransformationService(@Autowired(required = false) ComfyUiGateway comfyUiGateway) {
        this.comfyUiGateway = comfyUiGateway;
    }

    /**
     * Converts a ComfyUI web-interface graph JSON into an execution payload accepted by /prompt API.
     *
     * @param uiWorkflow the UI-format workflow JSON object
     * @return the API-format payload containing {@code prompt} and optional {@code definitions}
     */
    public JSONObject convertUiToApi(JSONObject uiWorkflow) {
        if (uiWorkflow != null && uiWorkflow.has("definitions") && uiWorkflow.getJSONObject("definitions").has("subgraphs")) {
            uiWorkflow = flattenWorkflow(uiWorkflow);
        }
        JSONObject apiPayload = new JSONObject();
        JSONObject apiPrompt = new JSONObject();
        apiPayload.put("prompt", apiPrompt);

        if (uiWorkflow != null && uiWorkflow.has("definitions")) {
            apiPayload.put("definitions", uiWorkflow.getJSONObject("definitions"));
        }

        if (uiWorkflow == null) return apiPayload;

        JSONArray nodes = uiWorkflow.optJSONArray("nodes");
        if (nodes == null) return apiPayload;

        JSONArray linksArray = uiWorkflow.optJSONArray("links");
        Map<Integer, Object[]> linksMap = new HashMap<>();
        if (linksArray != null) {
            for (int i = 0; i < linksArray.length(); i++) {
                JSONArray link = linksArray.optJSONArray(i);
                if (link != null && link.length() >= 6) {
                    int linkId = link.getInt(0);
                    int originNodeId = link.getInt(1);
                    int originSlot = link.getInt(2);
                    String type = link.getString(5);
                    linksMap.put(linkId, new Object[]{originNodeId, originSlot, type});
                }
            }
        }

        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            String type = node.getString("type");

            // Helper and comment nodes are excluded from API execution
            if ("MarkdownNote".equals(type) || "Note".equals(type)) {
                continue;
            }

            String idStr = String.valueOf(node.getInt("id"));
            JSONObject apiNode = new JSONObject();
            apiNode.put("class_type", type);

            JSONObject apiInputs = new JSONObject();
            apiNode.put("inputs", apiInputs);

            // 1. Resolve connected links
            JSONArray inputs = node.optJSONArray("inputs");
            if (inputs != null) {
                for (int j = 0; j < inputs.length(); j++) {
                    JSONObject input = inputs.getJSONObject(j);
                    String inputName = input.getString("name");
                    if (!input.isNull("link")) {
                        int linkId = input.getInt("link");
                        Object[] origin = linksMap.get(linkId);
                        if (origin != null) {
                            if (origin[0] instanceof Integer oId && oId == -10) {
                                // Skip pseudo origin -10
                            } else {
                                JSONArray linkRef = new JSONArray();
                                linkRef.put(String.valueOf(origin[0]));
                                linkRef.put(origin[1]);
                                apiInputs.put(inputName, linkRef);
                            }
                        }
                    }
                }
            }

            // 2. Resolve explicit named widget values (ComfyUI 1.0+ format & subgraph nodes)
            JSONObject namedWidgets = node.optJSONObject("widgets_values_named");
            if (namedWidgets != null) {
                for (String wk : namedWidgets.keySet()) {
                    if (!apiInputs.has(wk) && !namedWidgets.isNull(wk)) {
                        apiInputs.put(wk, namedWidgets.get(wk));
                    }
                }
            }

            // 3. Resolve positional widget values (dynamic gateway introspection with static/input fallback)
            JSONArray widgetsValues = node.optJSONArray("widgets_values");
            if (widgetsValues != null && widgetsValues.length() > 0) {
                List<String> widgetNames = resolveWidgetNames(node, type);
                for (int j = 0; j < widgetNames.size() && j < widgetsValues.length(); j++) {
                    String widgetName = widgetNames.get(j);
                    if (!apiInputs.has(widgetName) && !widgetsValues.isNull(j)) {
                        apiInputs.put(widgetName, widgetsValues.get(j));
                    }
                }
            }

            apiPrompt.put(idStr, apiNode);
        }

        return apiPayload;
    }

    private List<String> resolveWidgetNames(JSONObject node, String classType) {
        if (comfyUiGateway != null) {
            try {
                List<String> dynamic = comfyUiGateway.getWidgetInputNames(classType);
                if (dynamic != null && !dynamic.isEmpty()) {
                    return dynamic;
                }
            } catch (Exception e) {
                logger.debug("Gateway widget lookup failed for {}: {}", classType, e.getMessage());
            }
        }
        List<String> fallback = FALLBACK_WIDGET_MAP.get(classType);
        if (fallback != null) {
            return fallback;
        }
        if (node != null) {
            JSONArray inputs = node.optJSONArray("inputs");
            if (inputs != null) {
                List<String> inferred = new ArrayList<>();
                for (int i = 0; i < inputs.length(); i++) {
                    JSONObject inp = inputs.optJSONObject(i);
                    if (inp != null && inp.has("widget") && !inp.isNull("widget")) {
                        JSONObject widgetObj = inp.optJSONObject("widget");
                        String wName = widgetObj != null ? widgetObj.optString("name", "") : "";
                        if (wName.isEmpty()) {
                            wName = inp.optString("name", "");
                        }
                        if (!wName.isEmpty()) {
                            inferred.add(wName);
                        }
                    }
                }
                if (!inferred.isEmpty()) {
                    return inferred;
                }
            }
        }
        return Collections.emptyList();
    }

    /**
     * Recursively flattens subgraphs embedded in workflow JSON into top-level nodes and links.
     *
     * @param uiWorkflow the UI-format workflow JSON object
     * @return the flattened UI-format workflow JSON object
     */
    public JSONObject flattenWorkflow(JSONObject uiWorkflow) {
        return SubgraphFlattener.flattenWorkflow(uiWorkflow);
    }
}
