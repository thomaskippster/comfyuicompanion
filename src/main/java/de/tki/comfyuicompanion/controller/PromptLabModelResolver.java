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

    private final IModelArchitectureService modelArchitectureService;

    private final Set<String> comfyCheckpoints = ConcurrentHashMap.newKeySet();
    private final Set<String> comfyUnetModels = ConcurrentHashMap.newKeySet();
    private final Set<String> comfyClips = ConcurrentHashMap.newKeySet();
    private final Set<String> comfyVaes = ConcurrentHashMap.newKeySet();
    private final Set<String> comfyClipTypes = ConcurrentHashMap.newKeySet();
    private final Set<String> comfyUnetWeightDtypes = ConcurrentHashMap.newKeySet();

    public PromptLabModelResolver(IModelArchitectureService modelArchitectureService) {
        this.modelArchitectureService = modelArchitectureService;
    }

    public Set<String> getComfyCheckpoints() { return comfyCheckpoints; }
    public Set<String> getComfyUnetModels() { return comfyUnetModels; }
    public Set<String> getComfyClips() { return comfyClips; }
    public Set<String> getComfyVaes() { return comfyVaes; }
    public Set<String> getComfyClipTypes() { return comfyClipTypes; }
    public Set<String> getComfyUnetWeightDtypes() { return comfyUnetWeightDtypes; }

    public void updateComfyModelSets(JSONObject info) {
        if (info == null) return;
        comfyCheckpoints.clear();
        comfyUnetModels.clear();
        comfyClips.clear();
        comfyVaes.clear();
        comfyClipTypes.clear();
        comfyUnetWeightDtypes.clear();

        if (info.has("CheckpointLoaderSimple")) {
            JSONObject nodeInfo = info.getJSONObject("CheckpointLoaderSimple");
            if (nodeInfo.has("input")) {
                JSONObject input = nodeInfo.getJSONObject("input");
                if (input.has("required")) {
                    JSONObject required = input.getJSONObject("required");
                    if (required.has("ckpt_name")) {
                        Object val = required.get("ckpt_name");
                        if (val instanceof JSONArray outerArray) {
                            if (outerArray.length() > 0 && outerArray.get(0) instanceof JSONArray options) {
                                for (int i = 0; i < options.length(); i++) {
                                    comfyCheckpoints.add(options.getString(i));
                                }
                            }
                        }
                    }
                }
            }
        }

        if (info.has("UNETLoader")) {
            JSONObject nodeInfo = info.getJSONObject("UNETLoader");
            if (nodeInfo.has("input")) {
                JSONObject input = nodeInfo.getJSONObject("input");
                if (input.has("required")) {
                    JSONObject required = input.getJSONObject("required");
                    if (required.has("unet_name")) {
                        Object val = required.get("unet_name");
                        if (val instanceof JSONArray outerArray) {
                            if (outerArray.length() > 0 && outerArray.get(0) instanceof JSONArray options) {
                                for (int i = 0; i < options.length(); i++) {
                                    comfyUnetModels.add(options.getString(i));
                                }
                            }
                        }
                    }
                    if (required.has("weight_dtype")) {
                        Object val = required.get("weight_dtype");
                        if (val instanceof JSONArray outerArray) {
                            if (outerArray.length() > 0 && outerArray.get(0) instanceof JSONArray options) {
                                for (int i = 0; i < options.length(); i++) {
                                    comfyUnetWeightDtypes.add(options.getString(i));
                                }
                            }
                        }
                    }
                }
            }
        }

        if (info.has("CLIPLoader")) {
            JSONObject nodeInfo = info.getJSONObject("CLIPLoader");
            if (nodeInfo.has("input")) {
                JSONObject input = nodeInfo.getJSONObject("input");
                if (input.has("required")) {
                    JSONObject required = input.getJSONObject("required");
                    if (required.has("clip_name")) {
                        Object val = required.get("clip_name");
                        if (val instanceof JSONArray outerArray) {
                            if (outerArray.length() > 0 && outerArray.get(0) instanceof JSONArray options) {
                                for (int i = 0; i < options.length(); i++) {
                                    comfyClips.add(options.getString(i));
                                }
                            }
                        }
                    }
                    if (required.has("type")) {
                        Object val = required.get("type");
                        if (val instanceof JSONArray outerArray) {
                            if (outerArray.length() > 0 && outerArray.get(0) instanceof JSONArray options) {
                                for (int i = 0; i < options.length(); i++) {
                                    comfyClipTypes.add(options.getString(i));
                                }
                            }
                        }
                    }
                }
            }
        }

        if (info.has("VAELoader")) {
            JSONObject nodeInfo = info.getJSONObject("VAELoader");
            if (nodeInfo.has("input")) {
                JSONObject input = nodeInfo.getJSONObject("input");
                if (input.has("required")) {
                    JSONObject required = input.getJSONObject("required");
                    if (required.has("vae_name")) {
                        Object val = required.get("vae_name");
                        if (val instanceof JSONArray outerArray) {
                            if (outerArray.length() > 0 && outerArray.get(0) instanceof JSONArray options) {
                                for (int i = 0; i < options.length(); i++) {
                                    comfyVaes.add(options.getString(i));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

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

    public String resolveClipType(String clipModel, String selectedModel) {
        String lower = clipModel != null ? clipModel.toLowerCase() : "";

        if (lower.contains("gemma") || lower.contains("qwen_3_4b") || lower.contains("lumina2") || lower.contains("lumina-2")) {
            return "lumina2";
        } else if (lower.contains("wan") || lower.contains("qwen_2.5_vl") || lower.contains("umt5")) {
            return "wan";
        } else if (lower.contains("t5xxl") || lower.contains("t5-xxl") || lower.contains("t5_xxl") || lower.contains("t5_fp8") || lower.contains("t5_fp16")) {
            if (selectedModel != null && (selectedModel.toLowerCase().contains("sd3") || selectedModel.toLowerCase().contains("stable_diffusion_3"))) {
                return "sd3";
            }
            return "flux";
        } else if (lower.contains("mistral") || lower.contains("flux2") || lower.contains("klein")) {
            return "flux2";
        } else if (lower.contains("sd3") || lower.contains("stable_diffusion_3")) {
            return "sd3";
        } else if (lower.contains("clip_l") || lower.contains("clip_g") || lower.contains("vi-clip") || lower.contains("sd15") || lower.contains("sdxl")) {
            return "stable_diffusion";
        } else if (lower.contains("mochi")) {
            return "mochi";
        } else if (lower.contains("ltxv") || lower.contains("ltx")) {
            return "ltxv";
        } else if (lower.contains("cosmos")) {
            return "cosmos";
        }

        String candidate = "stable_diffusion";
        if (selectedModel != null) {
            String selLower = selectedModel.toLowerCase();
            if (selLower.contains("flux-2-klein") || selLower.contains("flux2-klein") || selLower.contains("klein")) {
                candidate = "flux2";
            } else if (selLower.contains("flux") || selLower.contains("schnell")) {
                candidate = "flux";
            } else if (selLower.contains("sd3") || selLower.contains("stable_diffusion_3")) {
                candidate = "sd3";
            } else if (selLower.contains("longcat") || selLower.contains("lumina") || selLower.contains("acestep") || selLower.contains("z_image") || selLower.contains("z-image")) {
                candidate = "lumina2";
            } else if (selLower.contains("wan")) {
                candidate = "wan";
            } else if (modelArchitectureService != null) {
                IModelArchitectureService.ModelDefaults defaults = modelArchitectureService.getDefaultsForModel(selectedModel);
                if (defaults != null && defaults.clipType != null) {
                    candidate = defaults.clipType;
                }
            }
        }

        if (comfyClipTypes.contains(candidate)) {
            return candidate;
        }
        if (comfyClipTypes.contains("stable_diffusion") && candidate.equals("stable_diffusion")) {
            return "stable_diffusion";
        }
        if (!comfyClipTypes.isEmpty() && !comfyClipTypes.contains(candidate) && !candidate.equals("flux") && !candidate.equals("lumina2") && !candidate.equals("wan") && !candidate.equals("sd3") && !candidate.equals("flux2")) {
            return comfyClipTypes.iterator().next();
        }
        return candidate;
    }

    public boolean modelsMatch(String modelA, String modelB) {
        if (modelA == null || modelB == null) return false;
        String a = modelA.replace("\\", "/").toLowerCase();
        String b = modelB.replace("\\", "/").toLowerCase();

        if (a.startsWith("checkpoints/")) a = a.substring(12);
        if (a.startsWith("diffusion_models/")) a = a.substring(17);
        if (a.startsWith("unet/")) a = a.substring(5);

        if (b.startsWith("checkpoints/")) b = b.substring(12);
        if (b.startsWith("diffusion_models/")) b = b.substring(17);
        if (b.startsWith("unet/")) b = b.substring(5);

        if (a.equals(b)) return true;
        if (a.endsWith("/" + b) || b.endsWith("/" + a)) return true;

        String nameA = a.contains("/") ? a.substring(a.lastIndexOf('/') + 1) : a;
        String nameB = b.contains("/") ? b.substring(b.lastIndexOf('/') + 1) : b;
        return nameA.equalsIgnoreCase(nameB);
    }

    public String resolveClipForModel(String selectedModel) {
        if (selectedModel == null) return "clip_l.safetensors";
        if (modelArchitectureService != null) {
            IModelArchitectureService.ModelDefaults defaults = modelArchitectureService.getDefaultsForModel(selectedModel);
            if (defaults != null && defaults.clipType != null) {
                String candidate = defaults.clipType;
                for (String clip : comfyClips) {
                    String clipClean = clip.replace("\\", "/");
                    if (clipClean.equalsIgnoreCase(candidate) || clipClean.endsWith("/" + candidate)) {
                        return clip;
                    }
                }
            }
        }

        String lower = selectedModel.toLowerCase();
        if (lower.contains("z_image") || lower.contains("z-image") || lower.contains("acestep") || lower.contains("longcat") || lower.contains("lumina")) {
            for (String clip : comfyClips) {
                String cLower = clip.toLowerCase();
                if (cLower.contains("gemma") || cLower.contains("qwen_3_4b") || cLower.contains("lumina")) {
                    return clip;
                }
            }
            return "gemma4_e2b_it_bf16.safetensors";
        } else if (lower.contains("wan")) {
            for (String clip : comfyClips) {
                String cLower = clip.toLowerCase();
                if (cLower.contains("qwen_2.5_vl") || cLower.contains("umt5")) {
                    return clip;
                }
            }
            return "qwen/qwen_2.5_vl_7b_fp8_scaled.safetensors";
        } else if (lower.contains("flux-2-klein") || lower.contains("flux2-klein") || lower.contains("klein")) {
            for (String clip : comfyClips) {
                String cLower = clip.toLowerCase();
                if (cLower.contains("mistral") || cLower.contains("flux2") || cLower.contains("klein")) {
                    return clip;
                }
            }
            return "mistral_3_small_flux2_bf16.safetensors";
        } else if (lower.contains("flux") || lower.contains("schnell") || lower.contains("dev")) {
            for (String clip : comfyClips) {
                String cLower = clip.toLowerCase();
                if (cLower.contains("t5xxl") || cLower.contains("t5-xxl") || cLower.contains("t5_xxl") ||
                        cLower.contains("t5_fp8") || cLower.contains("t5_fp16") || cLower.contains("t5xxl_fp8")) {
                    return clip;
                }
            }
            for (String clip : comfyClips) {
                String cLower = clip.toLowerCase();
                if (cLower.contains("t5") && !cLower.contains("umt5") && !cLower.contains("gemma") && !cLower.contains("qwen")) {
                    return clip;
                }
            }
            return "t5xxl_fp8_e4m3fn.safetensors";
        } else if (lower.contains("sd3") || lower.contains("stable_diffusion_3")) {
            for (String clip : comfyClips) {
                String cLower = clip.toLowerCase();
                if (cLower.contains("t5xxl") || cLower.contains("t5-xxl") || cLower.contains("clip_g") || cLower.contains("sd3")) {
                    return clip;
                }
            }
            return "t5xxl_fp8_e4m3fn.safetensors";
        }

        for (String clip : comfyClips) {
            String cLower = clip.toLowerCase();
            if (cLower.contains("clip_l") || cLower.contains("sd15") || cLower.contains("sdxl")) {
                return clip;
            }
        }
        return "clip_l.safetensors";
    }

    public String resolveVaeForModel(String modelName) {
        if (modelName == null) return "ae.safetensors";
        String expected = "FLUX1/ae.safetensors";
        if (modelArchitectureService != null) {
            IModelArchitectureService.ModelDefaults defaults = modelArchitectureService.getDefaultsForModel(modelName);
            if (defaults != null && defaults.vaeName != null) {
                expected = defaults.vaeName;
            }
        } else {
            String lower = modelName.toLowerCase();
            if (lower.contains("z_image") || lower.contains("z-image") || lower.contains("acestep") || lower.contains("longcat") || lower.contains("lumina")) {
                expected = "ae.safetensors";
            } else if (lower.contains("wan")) {
                expected = "wan_2.1_vae.safetensors";
            } else if (lower.contains("flux")) {
                expected = "FLUX1/ae.safetensors";
            }
        }

        String expectedClean = expected.replace("\\", "/");
        for (String vae : comfyVaes) {
            String vaeClean = vae.replace("\\", "/");
            if (vaeClean.equalsIgnoreCase(expectedClean) || vaeClean.endsWith("/" + expectedClean)) {
                return vae;
            }
        }
        String expectedName = expectedClean.contains("/") ? expectedClean.substring(expectedClean.lastIndexOf('/') + 1) : expectedClean;
        for (String vae : comfyVaes) {
            String vaeClean = vae.replace("\\", "/");
            if (vaeClean.equalsIgnoreCase(expectedName) || vaeClean.endsWith("/" + expectedName)) {
                return vae;
            }
        }
        boolean isExpectedAeOrFlux = expectedName.contains("flux") || expectedName.replace("vae", "").contains("ae");
        if (isExpectedAeOrFlux) {
            for (String vae : comfyVaes) {
                String vaeLower = vae.toLowerCase();
                if (vaeLower.contains("flux") || vaeLower.replace("vae", "").contains("ae")) {
                    return vae;
                }
            }
        }
        if (!comfyVaes.isEmpty()) {
            String candidate = comfyVaes.iterator().next();
            String cLower = candidate.toLowerCase();
            if (expectedName.contains("wan") && cLower.contains("wan")) {
                return candidate;
            }
            if (isExpectedAeOrFlux && (cLower.contains("flux") || cLower.replace("vae", "").contains("ae"))) {
                return candidate;
            }
        }
        return expected;
    }

    /**
     * Determines whether the given model name refers to a diffusion model.
     *
     * @param modelName name or path of the model
     * @return true if it is a diffusion model
     */
    public boolean isDiffusionModel(String modelName) {
        if (modelName == null) return false;
        String lower = modelName.toLowerCase();
        return lower.contains("diffusion_models") || lower.contains("z_image") || lower.contains("z-image")
                || lower.contains("acestep") || lower.contains("flux") || lower.contains("lumina") || lower.contains("wan");
    }
}
