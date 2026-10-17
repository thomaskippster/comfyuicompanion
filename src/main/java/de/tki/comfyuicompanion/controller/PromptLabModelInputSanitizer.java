package de.tki.comfyuicompanion.controller;

import de.tki.comfyuicompanion.service.PromptBlueprintApiService;
import de.tki.comfyuicompanion.ui.PromptLabView;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Set;

/**
 * Sanitizer component validating and resolving model filenames, samplers, and schedulers
 * in ComfyUI prompt graphs against available server models.
 */
@Component
public class PromptLabModelInputSanitizer {

    /**
     * Sanitizes and injects selected models into the ComfyUI workflow JSON before submission.
     *
     * @param promptObj the root prompt JSON object
     * @param objectInfo the ComfyUI object info for validation
     * @param promptLabView the prompt lab view
     * @param promptLabController the prompt lab controller
     */
    public void sanitizeModelInputsInPrompt(JSONObject promptObj, JSONObject objectInfo,
                                           PromptLabView promptLabView, PromptLabController promptLabController) {
        if (promptObj == null) return;
        for (String key : promptObj.keySet()) {
            JSONObject node = promptObj.optJSONObject(key);
            if (node == null) continue;
            JSONObject inp = node.optJSONObject("inputs");
            if (inp == null) continue;
            String classType = node.optString("class_type", "");

            if ("KSamplerSelect".equals(classType)) {
                String sn = inp.optString("sampler_name", "");
                if (sn.isBlank() || sn.equalsIgnoreCase("COMBO") || sn.equalsIgnoreCase("Auto")) {
                    String uiSampler = (promptLabView != null && promptLabView.getPromptSamplerCombo() != null && promptLabView.getPromptSamplerCombo().getSelectedItem() != null)
                            ? (String) promptLabView.getPromptSamplerCombo().getSelectedItem() : "Auto";
                    inp.put("sampler_name", (!uiSampler.equalsIgnoreCase("Auto") && !uiSampler.equalsIgnoreCase("COMBO")) ? uiSampler : "euler");
                }
            }

            for (String ik : new ArrayList<>(inp.keySet())) {
                Object val = inp.opt(ik);
                if (val instanceof String s && !s.isBlank()) {
                    String cleaned = s.replaceAll("[/\\\\]+", "/").trim();
                    if (s.startsWith("/") || s.startsWith("\\")) {
                        cleaned = "/" + cleaned.replaceAll("^/+", "");
                    }

                    if (cleaned.equalsIgnoreCase("COMBO") || cleaned.equalsIgnoreCase("Auto")) {
                        if (ik.equals("sampler_name") || classType.contains("Sampler")) {
                            String uiSampler = (promptLabView != null && promptLabView.getPromptSamplerCombo() != null && promptLabView.getPromptSamplerCombo().getSelectedItem() != null)
                                    ? (String) promptLabView.getPromptSamplerCombo().getSelectedItem() : "Auto";
                            inp.put(ik, (!uiSampler.equalsIgnoreCase("Auto") && !uiSampler.equalsIgnoreCase("COMBO")) ? uiSampler : "euler");
                            continue;
                        } else if (ik.equals("scheduler")) {
                            String uiScheduler = (promptLabView != null && promptLabView.getPromptSchedulerCombo() != null && promptLabView.getPromptSchedulerCombo().getSelectedItem() != null)
                                    ? (String) promptLabView.getPromptSchedulerCombo().getSelectedItem() : "Auto";
                            inp.put(ik, (!uiScheduler.equalsIgnoreCase("Auto") && !uiScheduler.equalsIgnoreCase("COMBO")) ? uiScheduler : "simple");
                            continue;
                        }
                    }

                    boolean isModelKey = ik.endsWith("_name") || ik.endsWith("_path") || ik.equals("model") || ik.equals("vae") || ik.equals("clip") || ik.equals("unet");
                    boolean isModelFile = cleaned.endsWith(".safetensors") || cleaned.endsWith(".ckpt") || cleaned.endsWith(".pt") || cleaned.endsWith(".bin") || cleaned.endsWith(".onnx") || cleaned.endsWith(".sft");

                    if (isModelKey || isModelFile) {
                        String matchedOption = null;
                        if (objectInfo != null) {
                            matchedOption = findModelInObjectInfo(classType, ik, cleaned, objectInfo);
                        }
                        if (matchedOption == null && promptLabController != null) {
                            matchedOption = findExactComfyModelOption(classType, ik, cleaned, promptLabController);
                        }
                        if (matchedOption != null) {
                            inp.put(ik, matchedOption);
                        } else {
                            if (ik.equals("sampler_name") && cleaned.equalsIgnoreCase("COMBO")) {
                                inp.put(ik, "euler");
                            } else if (ik.equals("scheduler") && cleaned.equalsIgnoreCase("COMBO")) {
                                inp.put(ik, "normal");
                            } else {
                                inp.put(ik, cleaned);
                            }
                        }
                    }
                }
            }
        }
        PromptBlueprintApiService.sanitizeAllSeedsInPrompt(promptObj);
    }

    /**
     * Resolves an exact comfy model option given a target value.
     *
     * @param classType the node class type
     * @param inputKey the input property key
     * @param targetValue the target model value
     * @param promptLabController controller providing registered model sets
     * @return the matched exact ComfyUI model name or null
     */
    public String findExactComfyModelOption(String classType, String inputKey, String targetValue, PromptLabController promptLabController) {
        if (targetValue == null || targetValue.isBlank() || promptLabController == null) return targetValue;
        Set<String> options = null;

        if (inputKey.equals("unet_name")) {
            options = promptLabController.getComfyUnetModels();
        } else if (inputKey.equals("ckpt_name")) {
            options = promptLabController.getComfyCheckpoints();
        } else if (inputKey.equals("clip_name") || inputKey.equals("clip_name1") || inputKey.equals("clip_name2")) {
            options = promptLabController.getComfyClips();
        } else if (inputKey.equals("vae_name")) {
            options = promptLabController.getComfyVaes();
        } else if (inputKey.equals("type") && classType.contains("CLIPLoader")) {
            options = promptLabController.getComfyClipTypes();
        } else if (inputKey.equals("weight_dtype")) {
            options = promptLabController.getComfyUnetWeightDtypes();
        }

        if (options != null && !options.isEmpty()) {
            String normTarget = targetValue.replace('\\', '/').trim();
            for (String opt : options) {
                if (opt.replace('\\', '/').equalsIgnoreCase(normTarget)) {
                    return opt;
                }
            }
            String targetFileName = new File(normTarget).getName();
            for (String opt : options) {
                String optFileName = new File(opt.replace('\\', '/')).getName();
                if (optFileName.equalsIgnoreCase(targetFileName)) {
                    return opt;
                }
            }
        }
        return null;
    }

    /**
     * Resolves a model name directly against the ComfyUI object info payload.
     *
     * @param classType the node class type
     * @param inputKey the input property key
     * @param targetValue the target model value
     * @param objectInfo the ComfyUI object info
     * @return the resolved model name
     */
    public String findModelInObjectInfo(String classType, String inputKey, String targetValue, JSONObject objectInfo) {
        return PromptBlueprintApiService.findModelInObjectInfo(classType, inputKey, targetValue, objectInfo);
    }
}
