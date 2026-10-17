package de.tki.comfyuicompanion.controller;

import de.tki.comfyuicompanion.service.IModelArchitectureService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves models, CLIPs, and VAEs for Prompt Lab and injects model paths into templates.
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
     * Updates the internal sets of available models by parsing the ComfyUI object info.
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
    }

    private void extractOptions(JSONObject info, String nodeName, String propName, Set<String> target) {
        if (!info.has(nodeName)) return;
        JSONObject nodeInfo = info.getJSONObject(nodeName);
        if (!nodeInfo.has("input")) return;
        JSONObject input = nodeInfo.getJSONObject("input");
        if (!input.has("required")) return;
        JSONObject required = input.getJSONObject("required");
        if (!required.has(propName)) return;
        Object val = required.get(propName);
        if (val instanceof JSONArray outerArray && outerArray.length() > 0) {
            if (outerArray.get(0) instanceof JSONArray options) {
                for (int i = 0; i < options.length(); i++) {
                    target.add(options.getString(i));
                }
            }
        }
    }

    /**
     * Injects the selected model parameters (checkpoint, unet, clip, vae) into the workflow template JSON.
     *
     * @param templateJson the raw workflow template JSON
     * @param modelName    the selected model name to inject
     * @return the updated workflow template JSON string
     */
    public String injectModelsIntoTemplate(String templateJson, String modelName) {
        if (templateJson == null || templateJson.isBlank() || modelName == null || modelName.isBlank()) {
            return templateJson;
        }
        try {
            JSONObject rootObj = new JSONObject(templateJson);
            JSONObject promptObj = rootObj.has("prompt") ? rootObj.getJSONObject("prompt") : rootObj;

            String unetName = findExactUnetName(modelName);
            if (unetName.isEmpty()) unetName = modelName;
            String ckptName = findExactCheckpointName(modelName);
            if (ckptName.isEmpty()) ckptName = modelName;
            String resolvedClip = resolveClipForModel(modelName);
            String resolvedVae = resolveVaeForModel(modelName);
            String resolvedClipType = resolveClipType(resolvedClip, modelName);

            for (String key : promptObj.keySet()) {
                JSONObject node = promptObj.optJSONObject(key);
                if (node == null) continue;
                String classType = node.optString("class_type", "");
                JSONObject inputs = node.optJSONObject("inputs");
                if (inputs == null) continue;

                if ("CheckpointLoaderSimple".equals(classType)) {
                    inputs.put("ckpt_name", ckptName);
                } else if ("UNETLoader".equals(classType) || "UNetLoader".equals(classType)) {
                    inputs.put("unet_name", unetName);
                } else if ("CLIPLoader".equals(classType)) {
                    inputs.put("clip_name", resolvedClip);
                    inputs.put("type", resolvedClipType);
                } else if ("DualCLIPLoader".equals(classType)) {
                    inputs.put("clip_name1", "clip_l.safetensors");
                    inputs.put("clip_name2", resolvedClip);
                    inputs.put("type", resolvedClipType);
                } else if ("VAELoader".equals(classType)) {
                    inputs.put("vae_name", resolvedVae);
                }
            }
            return rootObj.toString(2);
        } catch (Exception e) {
            logger.warn("Failed to inject model parameters into template: {}", e.getMessage());
            return templateJson;
        }
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
