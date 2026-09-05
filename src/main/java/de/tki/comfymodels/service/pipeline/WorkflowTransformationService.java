package de.tki.comfymodels.service.pipeline;

import de.tki.comfymodels.service.gateway.ComfyUiGateway;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for ComfyUI graph and workflow transformations:
 * - UI-to-API conversion (resolving links and mapping widgets)
 * - Subgraph flattening (inlining nested subgraphs with ID offsets and slot remapping)
 * - Dynamic introspection via ComfyUiGateway with static fallbacks
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
        FALLBACK_WIDGET_MAP.put("KSamplerAdvanced", List.of("add_noise", "noise_seed", "control_after_generate", "steps", "cfg", "sampler_name", "scheduler", "start_at_step", "end_at_step", "return_with_leftover_noise"));
    }

    private final ComfyUiGateway comfyUiGateway;

    @Autowired
    public WorkflowTransformationService(@Autowired(required = false) ComfyUiGateway comfyUiGateway) {
        this.comfyUiGateway = comfyUiGateway;
    }

    /**
     * Converts a ComfyUI web-interface graph JSON into an execution payload accepted by /prompt API.
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

            // 2. Resolve widget values (dynamic gateway introspection with static fallback)
            JSONArray widgetsValues = node.optJSONArray("widgets_values");
            if (widgetsValues != null && widgetsValues.length() > 0) {
                List<String> widgetNames = resolveWidgetNames(type);
                for (int j = 0; j < widgetNames.size() && j < widgetsValues.length(); j++) {
                    String widgetName = widgetNames.get(j);
                    if (!apiInputs.has(widgetName)) {
                        apiInputs.put(widgetName, widgetsValues.get(j));
                    }
                }
            }

            apiPrompt.put(idStr, apiNode);
        }

        return apiPayload;
    }

    private List<String> resolveWidgetNames(String classType) {
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
        return FALLBACK_WIDGET_MAP.getOrDefault(classType, Collections.emptyList());
    }

    /**
     * Recursively flattens subgraphs embedded in workflow JSON into top-level nodes and links.
     */
    public JSONObject flattenWorkflow(JSONObject uiWorkflow) {
        if (uiWorkflow == null || !uiWorkflow.has("definitions") || !uiWorkflow.getJSONObject("definitions").has("subgraphs")) {
            return uiWorkflow;
        }

        JSONArray subgraphs = uiWorkflow.getJSONObject("definitions").getJSONArray("subgraphs");
        Map<String, JSONObject> subgraphDefs = new HashMap<>();
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

                    Map<Integer, Object[]> inputSlotLinks = new HashMap<>();
                    Map<Integer, List<Object[]>> outputSlotLinks = new HashMap<>();

                    JSONArray nodeInputsArr = node.optJSONArray("inputs");
                    JSONArray nodeOutputsArr = node.optJSONArray("outputs");
                    JSONArray sgInputsArr = sgDef.optJSONArray("inputs");
                    JSONArray sgOutputsArr = sgDef.optJSONArray("outputs");

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
                            int targetSgSlot = targetSlot;
                            if (nodeInputsArr != null && targetSlot < nodeInputsArr.length()) {
                                String slotName = nodeInputsArr.getJSONObject(targetSlot).optString("name", "");
                                if (sgInputsArr != null) {
                                    for (int s = 0; s < sgInputsArr.length(); s++) {
                                        if (slotName.equals(sgInputsArr.getJSONObject(s).optString("name", ""))) {
                                            targetSgSlot = s;
                                            break;
                                        }
                                    }
                                }
                            }
                            inputSlotLinks.put(targetSgSlot, new Object[]{originId, originSlot, linkType, linkId});
                        }
                        if (originId == subgraphNodeId) {
                            int originSgSlot = originSlot;
                            if (nodeOutputsArr != null && originSlot < nodeOutputsArr.length()) {
                                String slotName = nodeOutputsArr.getJSONObject(originSlot).optString("name", "");
                                if (sgOutputsArr != null) {
                                    for (int s = 0; s < sgOutputsArr.length(); s++) {
                                        if (slotName.equals(sgOutputsArr.getJSONObject(s).optString("name", ""))) {
                                            originSgSlot = s;
                                            break;
                                        }
                                    }
                                }
                            }
                            outputSlotLinks.computeIfAbsent(originSgSlot, k -> new ArrayList<>())
                                    .add(new Object[]{targetId, targetSlot, linkType, linkId});
                        }
                    }

                    JSONArray sgNodes = sgDef.getJSONArray("nodes");
                    Map<Integer, Integer> nodeIdMap = new HashMap<>();
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
                            if (itemObj instanceof JSONArray link) {
                                linkId = link.getInt(0);
                                originId = link.getInt(1);
                                originSlot = link.getInt(2);
                                targetId = link.getInt(3);
                                targetSlot = link.getInt(4);
                                linkType = link.getString(5);
                            } else if (itemObj instanceof JSONObject link) {
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
                            int newOriginId = originId == -10 ? -10 : (nodeIdMap.getOrDefault(originId, originId));
                            int newTargetId = targetId == -20 ? -20 : (nodeIdMap.getOrDefault(targetId, targetId));

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
}
