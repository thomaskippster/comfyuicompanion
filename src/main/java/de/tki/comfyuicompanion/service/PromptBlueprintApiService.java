package de.tki.comfyuicompanion.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Handles generic injection of Prompt Lab UI values (positive/negative prompt text, dimensions, sampler params,
 * seed, sampler_name, scheduler, denoise, batch_size) into an already-converted ComfyUI API-format prompt object.
 * Never touches model loaders, class_type fields, or graph wiring.
 */
@Service
public class PromptBlueprintApiService {

    public record PromptLabInputs(
            String positivePrompt,
            String negativePrompt,
            int    width,
            int    height,
            int    steps,
            double cfg,
            long   seed,
            String samplerName,
            String scheduler,
            double denoise,
            int    batchSize,
            String inputImageFile
    ) {
        // Overloaded constructor for backward compatibility
        public PromptLabInputs(String positivePrompt, int width, int height, int steps, double cfg, long seed) {
            this(positivePrompt, "", width, height, steps, cfg, seed, "Auto", "Auto", 1.0, 1, null);
        }
    }

    /**
     * Injects inputs into promptObj (the "prompt" sub-object) without modifying any loader
     * or graph-topology fields.
     */
    public void injectLabInputs(JSONObject promptObj, PromptLabInputs inputs) {
        if (promptObj == null || inputs == null) return;

        // 1. Pre-scan for negative conditioning node links from samplers
        Set<String> negativeNodeIds = new HashSet<>();
        for (String key : promptObj.keySet()) {
            JSONObject node = promptObj.optJSONObject(key);
            if (node == null) continue;
            JSONObject inp = node.optJSONObject("inputs");
            if (inp == null) continue;

            if (inp.has("negative") && inp.get("negative") instanceof JSONArray) {
                JSONArray negLink = inp.getJSONArray("negative");
                if (negLink.length() > 0) {
                    negativeNodeIds.add(String.valueOf(negLink.get(0)));
                }
            }
        }

        for (String key : promptObj.keySet()) {
            JSONObject node = promptObj.optJSONObject(key);
            if (node == null) continue;
            JSONObject inp = node.optJSONObject("inputs");
            if (inp == null) continue;
            String ct = node.optString("class_type", "");

            // Positive & Negative text encoders (CLIPTextEncode, TextEncodeQwenImageEditPlus, etc.)
            if ("CLIPTextEncode".equals(ct) || ct.contains("TextEncode") || ct.contains("CLIPText")) {
                String textField = inp.has("prompt") ? "prompt" : "text";
                if (inp.has(textField) || "TextEncodeQwenImageEditPlus".equals(ct)) {
                    String tv = inp.optString(textField, "").toLowerCase();
                    boolean isNegByLink = negativeNodeIds.contains(key);
                    boolean isNegByText = tv.contains("bad") || tv.contains("blurry")
                            || tv.contains("low quality") || tv.contains("worst");

                    if (isNegByLink || isNegByText) {
                        if (inputs.negativePrompt() != null && !inputs.negativePrompt().isBlank()) {
                            inp.put(textField, inputs.negativePrompt());
                        }
                    } else if (inputs.positivePrompt() != null && !inputs.positivePrompt().isBlank()) {
                        inp.put(textField, inputs.positivePrompt());
                    }
                }
            }

            // SaveImage / SaveImageAdvanced filename prefix
            if ("SaveImageAdvanced".equals(ct) || "SaveImage".equals(ct)) {
                if (!inp.has("filename_prefix") || inp.optString("filename_prefix").isBlank()) {
                    inp.put("filename_prefix", "ComfyCompanion");
                }
            }

            // Latent image dimensions & batch size
            if (ct.contains("Empty") || ct.contains("Latent")) {
                if (inp.has("width") || "EmptyFlux2LatentImage".equals(ct) || "EmptyLatentImage".equals(ct) || "EmptySD3LatentImage".equals(ct)) {
                    inp.put("width", inputs.width());
                }
                if (inp.has("height") || "EmptyFlux2LatentImage".equals(ct) || "EmptyLatentImage".equals(ct) || "EmptySD3LatentImage".equals(ct)) {
                    inp.put("height", inputs.height());
                }
                if (inp.has("batch_size") || "EmptyFlux2LatentImage".equals(ct) || "EmptyLatentImage".equals(ct) || "EmptySD3LatentImage".equals(ct)) {
                    inp.put("batch_size", inputs.batchSize() > 0 ? inputs.batchSize() : 1);
                }
            }

            // Sampler / scheduler parameters
            if ("KSampler".equals(ct) || "KSamplerAdvanced".equals(ct)) {
                inp.put("steps", inputs.steps());
                inp.put("cfg",   inputs.cfg());
                if (inputs.denoise() >= 0.0 && inputs.denoise() <= 1.0) {
                    inp.put("denoise", inputs.denoise());
                }
            } else if ("BasicScheduler".equals(ct) || "BetaScheduler".equals(ct) || "Flux2Scheduler".equals(ct) || "SDTurboScheduler".equals(ct)) {
                inp.put("steps", inputs.steps());
            } else if ("FluxGuidance".equals(ct)) {
                inp.put("guidance", inputs.cfg());
            } else if ("CFGGuider".equals(ct)) {
                if (inputs.cfg() > 0) {
                    inp.put("cfg", inputs.cfg());
                }
            }

            if (inp.has("sampler_name")) {
                if (inputs.samplerName() != null && !inputs.samplerName().equalsIgnoreCase("Auto") && !inputs.samplerName().equalsIgnoreCase("COMBO")) {
                    inp.put("sampler_name", inputs.samplerName());
                } else if (inp.optString("sampler_name").equalsIgnoreCase("COMBO") || inp.optString("sampler_name").equalsIgnoreCase("Auto") || inp.optString("sampler_name").isBlank()) {
                    inp.put("sampler_name", "euler");
                }
            }

            if ("KSamplerSelect".equals(ct)) {
                if (inputs.samplerName() != null && !inputs.samplerName().equalsIgnoreCase("Auto") && !inputs.samplerName().equalsIgnoreCase("COMBO")) {
                    inp.put("sampler_name", inputs.samplerName());
                } else if (!inp.has("sampler_name") || inp.optString("sampler_name").equalsIgnoreCase("COMBO") || inp.optString("sampler_name").equalsIgnoreCase("Auto") || inp.optString("sampler_name").isBlank()) {
                    inp.put("sampler_name", "euler");
                }
            }

            if (inp.has("scheduler")) {
                if (inputs.scheduler() != null && !inputs.scheduler().equalsIgnoreCase("Auto") && !inputs.scheduler().equalsIgnoreCase("COMBO")) {
                    inp.put("scheduler", inputs.scheduler());
                } else if (inp.optString("scheduler").equalsIgnoreCase("COMBO") || inp.optString("scheduler").equalsIgnoreCase("Auto") || inp.optString("scheduler").isBlank()) {
                    inp.put("scheduler", "normal");
                }
            }

            // Image input injection for Image Edit / Img2Img
            if ("LoadImage".equals(ct)) {
                if (inputs.inputImageFile() != null && !inputs.inputImageFile().isBlank()) {
                    inp.put("image", inputs.inputImageFile());
                }
            }

            // Seed in any node that carries a numeric seed field
            for (String ik : new ArrayList<>(inp.keySet())) {
                String lk = ik.toLowerCase(Locale.ROOT);
                boolean isSeedKey = lk.equals("seed") || lk.equals("noise_seed")
                        || lk.endsWith("_seed") || lk.startsWith("seed_")
                        || lk.equals("seed_value");
                if (isSeedKey && (inp.get(ik) instanceof Number || inp.get(ik) instanceof String)) {
                    long clampedSeed = clampSeedForNode(ct, inputs.seed());
                    inp.put(ik, clampedSeed);
                }
            }
        }
        sanitizeAllSeedsInPrompt(promptObj);
    }

    public static boolean is32BitNode(String classType) {
        if (classType == null) return false;
        String lower = classType.toLowerCase(Locale.ROOT);
        return lower.contains("bytedance") || lower.contains("seedance") || lower.contains("seedream")
                || lower.contains("cogvideo") || lower.contains("kling") || lower.contains("tencent")
                || lower.contains("minimax") || lower.contains("kimi") || lower.contains("vidu")
                || lower.contains("luma") || lower.contains("runway") || lower.contains("animatediff")
                || lower.contains("svd");
    }

    public static long clampSeedForNode(String classType, long seed) {
        if (is32BitNode(classType)) {
            return Math.abs(seed) % 2147483647L;
        }
        return Math.abs(seed);
    }

    public static void sanitizeAllSeedsInPrompt(JSONObject promptDict) {
        if (promptDict == null) return;
        for (String key : promptDict.keySet()) {
            JSONObject node = promptDict.optJSONObject(key);
            if (node == null) continue;
            JSONObject inp = node.optJSONObject("inputs");
            if (inp == null) continue;
            String ct = node.optString("class_type", "");
            boolean is32Bit = is32BitNode(ct);

            for (String ik : new ArrayList<>(inp.keySet())) {
                String lk = ik.toLowerCase(Locale.ROOT);
                boolean isSeedKey = lk.equals("seed") || lk.equals("noise_seed")
                        || lk.endsWith("_seed") || lk.startsWith("seed_")
                        || lk.equals("seed_value");
                if (isSeedKey) {
                    Object val = inp.get(ik);
                    if (val instanceof Number num) {
                        long numVal = num.longValue();
                        if (is32Bit && numVal > 2147483647L) {
                            inp.put(ik, Math.abs(numVal) % 2147483647L);
                        }
                    } else if (val instanceof String s) {
                        try {
                            long numVal = Long.parseLong(s.trim());
                            if (is32Bit && numVal > 2147483647L) {
                                inp.put(ik, Math.abs(numVal) % 2147483647L);
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }
    }

    /**
     * Cleans non-node metadata fields (e.g. "last_node_id", "last_link_id", "version", "extra")
     * from a prompt dictionary to prevent ComfyUI server crashes (such as TypeError: argument of type 'int' is not iterable).
     *
     * @param promptDict the prompt JSON dictionary containing node mappings
     */
    public static void cleanNonNodeKeys(JSONObject promptDict) {
        if (promptDict == null) return;
        List<String> keysToRemove = new ArrayList<>();
        for (String key : promptDict.keySet()) {
            Object val = promptDict.get(key);
            if (!(val instanceof JSONObject node) || !node.has("class_type")) {
                keysToRemove.add(key);
            }
        }
        keysToRemove.forEach(promptDict::remove);
    }

    /**
     * Searches ComfyUI's /object_info registry for an existing model option matching the requested target.
     * Supports subfolders (e.g. 'FLUX1\\ae.safetensors' for 'ae.safetensors'), normalized paths, and extension-insensitive matches.
     *
     * @param classType   the node class name (e.g. "VAELoader", "UNETLoader", "CLIPLoader")
     * @param inputKey    the input parameter name (e.g. "vae_name", "unet_name", "clip_name")
     * @param targetValue the target model filename or identifier
     * @param objectInfo  the ComfyUI /object_info JSON object
     * @return the exact matching model path registered in ComfyUI, or null if not found
     */
    public static String findModelInObjectInfo(String classType, String inputKey, String targetValue, JSONObject objectInfo) {
        if (targetValue == null || targetValue.isBlank() || classType == null || objectInfo == null) return null;
        if (!objectInfo.has(classType)) return null;

        JSONObject nodeInfo = objectInfo.optJSONObject(classType);
        if (nodeInfo == null) return null;
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
            if (firstElement instanceof JSONArray jsonArray) {
                options = jsonArray;
            } else if (firstElement instanceof String firstStr && firstStr.equalsIgnoreCase("COMBO")
                    && outerArray.length() > 1 && outerArray.get(1) instanceof JSONObject configObj) {
                options = configObj.optJSONArray("options");
            } else if (firstElement instanceof String && !"COMBO".equalsIgnoreCase((String) firstElement)) {
                options = outerArray;
            }

            if (options != null) {
                Set<String> optSet = new LinkedHashSet<>();
                for (int i = 0; i < options.length(); i++) {
                    if (options.get(i) instanceof String s && !s.equalsIgnoreCase("COMBO")) {
                        optSet.add(s);
                    }
                }

                String targetClean = targetValue.replaceAll("[/\\\\]+", "/").trim().toLowerCase(Locale.ROOT);
                String targetFileName = targetClean.contains("/") ? targetClean.substring(targetClean.lastIndexOf('/') + 1) : targetClean;
                String targetNoExt = targetFileName.contains(".") ? targetFileName.substring(0, targetFileName.lastIndexOf('.')) : targetFileName;

                // 1. Exact match (case-insensitive, normalized slashes)
                for (String opt : optSet) {
                    if (opt.replaceAll("[/\\\\]+", "/").trim().toLowerCase(Locale.ROOT).equals(targetClean)) {
                        return opt;
                    }
                }
                // 2. Match by filename (ignoring subfolder like FLUX1/)
                for (String opt : optSet) {
                    String optClean = opt.replaceAll("[/\\\\]+", "/").trim().toLowerCase(Locale.ROOT);
                    String optFileName = optClean.contains("/") ? optClean.substring(optClean.lastIndexOf('/') + 1) : optClean;
                    if (optFileName.equals(targetFileName)) {
                        return opt;
                    }
                }
                // 3. Match by filename without extension
                for (String opt : optSet) {
                    String optClean = opt.replaceAll("[/\\\\]+", "/").trim().toLowerCase(Locale.ROOT);
                    String optFileName = optClean.contains("/") ? optClean.substring(optClean.lastIndexOf('/') + 1) : optClean;
                    String optNoExt = optFileName.contains(".") ? optFileName.substring(0, optFileName.lastIndexOf('.')) : optFileName;
                    if (optNoExt.equals(targetNoExt)) {
                        return opt;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Generic model input sanitizer that adjusts model filenames across all prompt nodes
     * using ComfyUI's /object_info registry specifications.
     *
     * @param promptObj  the prompt JSON dictionary containing node mappings
     * @param objectInfo the ComfyUI /object_info registry
     */
    public static void sanitizeModelInputs(JSONObject promptObj, JSONObject objectInfo) {
        if (promptObj == null || objectInfo == null) return;
        for (String key : promptObj.keySet()) {
            JSONObject node = promptObj.optJSONObject(key);
            if (node == null) continue;
            JSONObject inp = node.optJSONObject("inputs");
            if (inp == null) continue;
            String classType = node.optString("class_type", "");

            for (String ik : new ArrayList<>(inp.keySet())) {
                Object val = inp.opt(ik);
                if (val instanceof String s && !s.isBlank()) {
                    boolean isModelKey = ik.endsWith("_name") || ik.endsWith("_path")
                            || ik.equals("model") || ik.equals("vae") || ik.equals("clip") || ik.equals("unet");
                    boolean isModelFile = s.endsWith(".safetensors") || s.endsWith(".ckpt")
                            || s.endsWith(".pt") || s.endsWith(".bin") || s.endsWith(".onnx") || s.endsWith(".sft");

                    if (isModelKey || isModelFile) {
                        String matched = findModelInObjectInfo(classType, ik, s, objectInfo);
                        if (matched != null) {
                            inp.put(ik, matched);
                        }
                    }
                }
            }
        }
    }

    /**
     * Extracts and normalizes the API prompt dictionary and workflow metadata
     * from raw conversion JSON returned by ComfyUI's app.graphToPrompt().
     */
    public JSONObject extractApiPayload(String convertedApiJsonStr) {
        if (convertedApiJsonStr == null || convertedApiJsonStr.isBlank()) {
            return new JSONObject().put("prompt", new JSONObject());
        }

        JSONObject converted = new JSONObject(convertedApiJsonStr);
        JSONObject promptObj = null;
        JSONObject workflowObj = null;

        if (converted.has("output") && converted.get("output") instanceof JSONObject) {
            promptObj = new JSONObject(converted.getJSONObject("output").toString());
            if (converted.has("workflow") && converted.get("workflow") instanceof JSONObject) {
                workflowObj = converted.getJSONObject("workflow");
            }
        } else if (converted.has("prompt") && converted.get("prompt") instanceof JSONObject) {
            JSONObject p = converted.getJSONObject("prompt");
            if (p.has("output") && p.get("output") instanceof JSONObject) {
                promptObj = new JSONObject(p.getJSONObject("output").toString());
                if (p.has("workflow") && p.get("workflow") instanceof JSONObject) {
                    workflowObj = p.getJSONObject("workflow");
                }
            } else {
                promptObj = new JSONObject(p.toString());
            }
            if (converted.has("extra_data") && converted.get("extra_data") instanceof JSONObject) {
                JSONObject ed = converted.getJSONObject("extra_data");
                if (ed.has("extra_pnginfo") && ed.get("extra_pnginfo") instanceof JSONObject) {
                    JSONObject ep = ed.getJSONObject("extra_pnginfo");
                    if (ep.has("workflow")) {
                        workflowObj = ep.optJSONObject("workflow");
                    }
                }
            }
        } else {
            promptObj = new JSONObject(converted.toString());
        }

        // Clean non-node keys (including last_node_id, last_link_id, etc.) if present in promptObj
        cleanNonNodeKeys(promptObj);

        JSONObject mainObj = new JSONObject().put("prompt", promptObj);
        if (workflowObj != null) {
            JSONObject extraData = new JSONObject();
            JSONObject extraPngInfo = new JSONObject();
            extraPngInfo.put("workflow", workflowObj);
            extraData.put("extra_pnginfo", extraPngInfo);
            mainObj.put("extra_data", extraData);
        }

        return mainObj;
    }

    /**
     * Injects inputs into the full API payload string and returns the modified string.
     */
    public String buildApiPayload(String apiJsonStr, PromptLabInputs inputs) {
        if (inputs == null || apiJsonStr == null || apiJsonStr.isBlank()) return apiJsonStr;
        try {
            JSONObject mainObj = extractApiPayload(apiJsonStr);
            JSONObject promptObj = mainObj.getJSONObject("prompt");
            injectLabInputs(promptObj, inputs);
            return mainObj.toString(2);
        } catch (Exception e) {
            return apiJsonStr;
        }
    }
}