package de.tki.comfymodels.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
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
            int    batchSize
    ) {
        // Overloaded constructor for backward compatibility
        public PromptLabInputs(String positivePrompt, int width, int height, int steps, double cfg, long seed) {
            this(positivePrompt, "", width, height, steps, cfg, seed, "Auto", "Auto", 1.0, 1);
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

            // Positive & Negative CLIPTextEncode
            if ("CLIPTextEncode".equals(ct)) {
                if (inp.has("text")) {
                    String tv = inp.optString("text", "").toLowerCase();
                    boolean isNegByLink = negativeNodeIds.contains(key);
                    boolean isNegByText = tv.contains("bad") || tv.contains("blurry")
                            || tv.contains("low quality") || tv.contains("worst");

                    if (isNegByLink || isNegByText) {
                        if (inputs.negativePrompt() != null && !inputs.negativePrompt().isBlank()) {
                            inp.put("text", inputs.negativePrompt());
                        }
                    } else if (inputs.positivePrompt() != null && !inputs.positivePrompt().isBlank()) {
                        inp.put("text", inputs.positivePrompt());
                    }
                }
            }

            // Latent image dimensions & batch size
            if ((ct.contains("Empty") || ct.contains("Latent")) && (inp.has("width") || inp.has("height") || inp.has("batch_size"))) {
                if (inp.has("width")) inp.put("width", inputs.width());
                if (inp.has("height")) inp.put("height", inputs.height());
                if (inp.has("batch_size") && inputs.batchSize() > 0) {
                    inp.put("batch_size", inputs.batchSize());
                }
            }

            // Sampler / scheduler parameters
            if ("KSampler".equals(ct) || "KSamplerAdvanced".equals(ct)) {
                inp.put("steps", inputs.steps());
                inp.put("cfg",   inputs.cfg());
                if (inputs.samplerName() != null && !inputs.samplerName().equalsIgnoreCase("Auto")) {
                    inp.put("sampler_name", inputs.samplerName());
                }
                if (inputs.scheduler() != null && !inputs.scheduler().equalsIgnoreCase("Auto")) {
                    inp.put("scheduler", inputs.scheduler());
                }
                if (inputs.denoise() >= 0.0 && inputs.denoise() <= 1.0) {
                    inp.put("denoise", inputs.denoise());
                }
            } else if ("BasicScheduler".equals(ct) || "BetaScheduler".equals(ct)) {
                inp.put("steps", inputs.steps());
                if (inputs.scheduler() != null && !inputs.scheduler().equalsIgnoreCase("Auto")) {
                    inp.put("scheduler", inputs.scheduler());
                }
            } else if ("KSamplerSelect".equals(ct)) {
                if (inputs.samplerName() != null && !inputs.samplerName().equalsIgnoreCase("Auto")) {
                    inp.put("sampler_name", inputs.samplerName());
                }
            } else if ("FluxGuidance".equals(ct)) {
                inp.put("guidance", inputs.cfg());
            }

            // Seed in any node that carries a numeric seed field
            for (String ik : new ArrayList<>(inp.keySet())) {
                String lk = ik.toLowerCase();
                boolean isSeedKey = lk.equals("seed") || lk.equals("noise_seed")
                        || lk.endsWith("_seed") || lk.startsWith("seed_");
                if (isSeedKey && inp.get(ik) instanceof Number) {
                    inp.put(ik, inputs.seed());
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

        // Clean non-node keys if present in promptObj
        promptObj.remove("output");
        promptObj.remove("workflow");
        promptObj.remove("extra_data");
        promptObj.remove("id");

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