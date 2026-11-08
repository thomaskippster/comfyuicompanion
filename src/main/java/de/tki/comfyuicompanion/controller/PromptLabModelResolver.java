package de.tki.comfyuicompanion.controller;

import de.tki.comfyuicompanion.service.IModelArchitectureService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves models, CLIPs, and VAEs for Prompt Lab and injects model paths into templates
 * using generic input key and slot inspection.
 */
public class PromptLabModelResolver {

    private static final Logger logger = LoggerFactory.getLogger(PromptLabModelResolver.class);

    private final ClipVaeResolver clipVaeResolver;

    private final Set<String> comfyCheckpoints = ConcurrentHashMap.newKeySet();
    private final Set<String> comfyUnetModels = ConcurrentHashMap.newKeySet();
    private final Set<String> comfyClips = ConcurrentHashMap.newKeySet();
    private final Set<String> comfyVaes = ConcurrentHashMap.newKeySet();
    private final Set<String> comfyClipTypes = ConcurrentHashMap.newKeySet();
    private final Set<String> comfyUnetWeightDtypes = ConcurrentHashMap.newKeySet();

    /**
     * Constructs a new resolver using the provided architecture service.
     *
     * @param modelArchitectureService model architecture detection service
     */
    public PromptLabModelResolver(IModelArchitectureService modelArchitectureService) {
        this.clipVaeResolver = new ClipVaeResolver(modelArchitectureService);
    }

    /** @return the set of available Comfy checkpoints */
    public Set<String> getComfyCheckpoints() { return comfyCheckpoints; }

    /** @return the set of available Comfy UNET models */
    public Set<String> getComfyUnetModels() { return comfyUnetModels; }

    /** @return the set of available Comfy CLIP models */
    public Set<String> getComfyClips() { return comfyClips; }

    /** @return the set of available Comfy VAE models */
    public Set<String> getComfyVaes() { return comfyVaes; }

    /** @return the set of available Comfy CLIP types */
    public Set<String> getComfyClipTypes() { return comfyClipTypes; }

    /** @return the set of available Comfy UNET weight data types */
    public Set<String> getComfyUnetWeightDtypes() { return comfyUnetWeightDtypes; }

    /**
     * Updates the internal sets of available models by parsing the ComfyUI {@code /object_info} registry
     * generically across all registered loader nodes.
     *
     * @param info the JSON object containing node information from ComfyUI
     */
    public void updateComfyModelSets(JSONObject info) {
        if (info == null) return;
        comfyCheckpoints.clear();
        comfyUnetModels.clear();
        comfyClips.clear();
        comfyVaes.clear();
        comfyClipTypes.clear();
        comfyUnetWeightDtypes.clear();

        extractOptions(info, "CheckpointLoaderSimple", "ckpt_name", comfyCheckpoints);
        extractOptions(info, "UNETLoader", "unet_name", comfyUnetModels);
        extractOptions(info, "UNETLoader", "weight_dtype", comfyUnetWeightDtypes);
        extractOptions(info, "CLIPLoader", "clip_name", comfyClips);
        extractOptions(info, "CLIPLoader", "type", comfyClipTypes);
        extractOptions(info, "VAELoader", "vae_name", comfyVaes);

        // Generically harvest model combo options from any custom or multi-slot loader nodes in /object_info
        for (String nodeName : info.keySet()) {
            JSONObject nodeInfo = info.optJSONObject(nodeName);
            if (nodeInfo == null) continue;
            JSONObject input = nodeInfo.optJSONObject("input");
            if (input == null) continue;
            JSONObject required = input.optJSONObject("required");
            if (required == null) continue;
            for (String propName : required.keySet()) {
                String lowerProp = propName.toLowerCase(Locale.ROOT);
                if (lowerProp.startsWith("ckpt_name")) {
                    extractOptions(info, nodeName, propName, comfyCheckpoints);
                } else if (lowerProp.startsWith("unet_name")) {
                    extractOptions(info, nodeName, propName, comfyUnetModels);
                } else if (lowerProp.startsWith("clip_name")) {
                    extractOptions(info, nodeName, propName, comfyClips);
                } else if (lowerProp.startsWith("vae_name")) {
                    extractOptions(info, nodeName, propName, comfyVaes);
                }
            }
        }
    }

    private void extractOptions(JSONObject info, String nodeName, String propName, Set<String> target) {
        if (!info.has(nodeName)) return;
        JSONObject nodeInfo = info.optJSONObject(nodeName);
        if (nodeInfo == null || !nodeInfo.has("input")) return;
        JSONObject input = nodeInfo.optJSONObject("input");
        if (input == null || !input.has("required")) return;
        JSONObject required = input.optJSONObject("required");
        if (required == null || !required.has(propName)) return;
        Object val = required.get(propName);
        if (val instanceof JSONArray outerArray && outerArray.length() > 0) {
            if (outerArray.get(0) instanceof JSONArray options) {
                for (int i = 0; i < options.length(); i++) {
                    String opt = options.optString(i, null);
                    if (opt != null && !opt.isBlank()) {
                        target.add(opt);
                    }
                }
            }
        }
    }

    /**
     * Injects the selected model parameters (checkpoint, unet, clip, vae) into the workflow template JSON
     * using generic input-slot discovery rather than hardcoded loader class cascades.
     *
     * @param templateJson the raw workflow template JSON
     * @param modelName    the selected model name to inject
     * @return the updated workflow template JSON string
     */
    public String injectModelsIntoTemplate(String templateJson, String modelName) {
        if (templateJson == null || modelName == null || modelName.isEmpty()) return templateJson;
        try {
            JSONObject rootObj = new JSONObject(templateJson);
            JSONObject promptObj = rootObj.has("prompt") ? rootObj.getJSONObject("prompt") : rootObj;

            String ckptName = findExactCheckpointName(modelName);
            String unetName = findExactUnetName(modelName);
            String resolvedClip = resolveClipForModel(modelName);
            String resolvedVae = resolveVaeForModel(modelName);
            String resolvedClipType = resolveClipType(resolvedClip, modelName);

            for (String key : promptObj.keySet()) {
                JSONObject node = promptObj.optJSONObject(key);
                if (node == null) continue;
                String classType = node.optString("class_type", "");
                JSONObject inputs = node.optJSONObject("inputs");
                if (inputs == null) continue;

                // 1. Generic Checkpoint / UNET / VAE injection by input key or standard loader class
                if ("CheckpointLoaderSimple".equals(classType) || inputs.has("ckpt_name")) {
                    inputs.put("ckpt_name", ckptName);
                }
                if ("UNETLoader".equals(classType) || "UNetLoader".equals(classType) || inputs.has("unet_name")) {
                    inputs.put("unet_name", unetName);
                }
                if ("VAELoader".equals(classType) || inputs.has("vae_name")) {
                    inputs.put("vae_name", resolvedVae);
                }

                // 2. Generic slot-aware CLIP injection for single, dual, triple, or N-slot CLIP loaders
                List<String> clipInputKeys = resolveClipInputKeys(classType, inputs);
                if (!clipInputKeys.isEmpty()) {
                    int totalSlots = clipInputKeys.size();
                    for (int slotIdx = 0; slotIdx < totalSlots; slotIdx++) {
                        String slotKey = clipInputKeys.get(slotIdx);
                        String slotClip = clipVaeResolver.resolveClipForSlot(modelName, slotIdx, totalSlots, comfyClips);
                        inputs.put(slotKey, findExactClipName(slotClip));
                    }
                    if ("CLIPLoader".equals(classType) || "DualCLIPLoader".equals(classType) || inputs.has("type")) {
                        inputs.put("type", resolvedClipType);
                    }
                }
            }
            return rootObj.toString(2);
        } catch (Exception e) {
            logger.warn("Failed to inject model parameters into template: {}", e.getMessage());
            return templateJson;
        }
    }

    private List<String> resolveClipInputKeys(String classType, JSONObject inputs) {
        List<String> keys = new ArrayList<>();
        for (String k : inputs.keySet()) {
            if (k.toLowerCase(Locale.ROOT).startsWith("clip_name")) {
                keys.add(k);
            }
        }
        if (!keys.isEmpty()) {
            Collections.sort(keys);
            return keys;
        }
        return switch (classType) {
            case "CLIPLoader" -> List.of("clip_name");
            case "DualCLIPLoader" -> List.of("clip_name1", "clip_name2");
            case "TripleCLIPLoader" -> List.of("clip_name1", "clip_name2", "clip_name3");
            case "QuadrupleCLIPLoader" -> List.of("clip_name1", "clip_name2", "clip_name3", "clip_name4");
            default -> List.of();
        };
    }

    /**
     * Finds the exact UNET model name matching the given selected model.
     *
     * @param selectedModel the selected model name
     * @return the matched exact UNET model name, or a cleaned version of the selected model
     */
    public String findExactUnetName(String selectedModel) {
        if (selectedModel == null) return "";
        for (String unet : comfyUnetModels) {
            if (modelsMatch(selectedModel, unet)) {
                return unet;
            }
        }
        String clean = selectedModel.replace("\\", "/");
        if (clean.startsWith("diffusion_models/")) {
            clean = clean.substring(17);
        }
        return clean;
    }

    /**
     * Finds the exact checkpoint name matching the given selected model.
     *
     * @param selectedModel the selected model name
     * @return the matched exact checkpoint name, or a cleaned version of the selected model
     */
    public String findExactCheckpointName(String selectedModel) {
        if (selectedModel == null) return "";
        for (String ckpt : comfyCheckpoints) {
            if (modelsMatch(selectedModel, ckpt)) {
                return ckpt;
            }
        }
        String clean = selectedModel.replace("\\", "/");
        if (clean.startsWith("checkpoints/")) {
            clean = clean.substring(12);
        }
        return clean;
    }

    /**
     * Finds the exact CLIP model name matching the given target CLIP filename.
     *
     * @param targetClip the target CLIP model filename
     * @return the matched exact CLIP model name from ComfyUI, or the target filename if not found
     */
    public String findExactClipName(String targetClip) {
        if (targetClip == null) return "";
        for (String clip : comfyClips) {
            if (modelsMatch(targetClip, clip)) {
                return clip;
            }
        }
        return targetClip;
    }

    /**
     * Resolves the appropriate CLIP type string based on the given clip model name and selected model.
     *
     * @param clipModel     the clip model name
     * @param selectedModel the selected model name
     * @return the resolved CLIP type string
     */
    public String resolveClipType(String clipModel, String selectedModel) {
        return clipVaeResolver.resolveClipType(clipModel, selectedModel, comfyClipTypes);
    }

    /**
     * Checks whether two model names refer to the same logical model file, ignoring directory prefixes.
     *
     * @param modelA the first model name
     * @param modelB the second model name
     * @return true if the models match
     */
    public boolean modelsMatch(String modelA, String modelB) {
        return clipVaeResolver.modelsMatch(modelA, modelB);
    }

    /**
     * Resolves the appropriate CLIP model filename for the given selected model.
     *
     * @param selectedModel the selected model name
     * @return the resolved CLIP model filename
     */
    public String resolveClipForModel(String selectedModel) {
        return clipVaeResolver.resolveClipForModel(selectedModel, comfyClips);
    }

    /**
     * Resolves the appropriate VAE model filename for the given selected model.
     *
     * @param modelName the selected model name
     * @return the resolved VAE model filename
     */
    public String resolveVaeForModel(String modelName) {
        return clipVaeResolver.resolveVaeForModel(modelName, comfyVaes);
    }

    /**
     * Determines whether the given model name refers to a diffusion model.
     *
     * @param modelName name or path of the model
     * @return true if it is a diffusion model
     */
    public boolean isDiffusionModel(String modelName) {
        return clipVaeResolver.isDiffusionModel(modelName);
    }
}
