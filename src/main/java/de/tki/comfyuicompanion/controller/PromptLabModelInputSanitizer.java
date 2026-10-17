package de.tki.comfyuicompanion.controller;

import de.tki.comfyuicompanion.service.PromptBlueprintApiService;
import de.tki.comfyuicompanion.ui.PromptLabView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Sanitizer component validating and resolving model filenames, samplers, and schedulers
 * in ComfyUI prompt graphs against available server models.
 */
@Component
public class PromptLabModelInputSanitizer {

    private static final Set<String> MODEL_INPUT_KEYS = Set.of(
            "ckpt_name", "unet_name", "clip_name", "clip_name1", "clip_name2", "clip_name3",
            "vae_name", "lora_name", "control_net_name", "model_name", "upscale_model"
    );

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
     * Validates that all required model inputs in the prompt graph exist in ComfyUI's /object_info registry.
     *
     * @param promptObj  the prompt JSON object to validate
     * @param objectInfo the ComfyUI /object_info registry payload
     * @return list of missing model descriptions, or an empty list if all required models are available
     */
    public List<String> validateRequiredModelsInPrompt(JSONObject promptObj, JSONObject objectInfo) {
        if (promptObj == null || objectInfo == null || objectInfo.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> missing = new LinkedHashSet<>();
        for (String key : promptObj.keySet()) {
            JSONObject node = promptObj.optJSONObject(key);
            if (node == null) continue;
            JSONObject inp = node.optJSONObject("inputs");
            if (inp == null) continue;
            String classType = node.optString("class_type", "");
            if (classType.isBlank() || !objectInfo.has(classType)) continue;

            for (String ik : inp.keySet()) {
                Object val = inp.opt(ik);
                if (val instanceof String s && !s.isBlank()) {
                    boolean isModelLoaderKey = MODEL_INPUT_KEYS.contains(ik);
                    boolean isModelFile = s.endsWith(".safetensors") || s.endsWith(".ckpt")
                            || s.endsWith(".pt") || s.endsWith(".bin") || s.endsWith(".onnx") || s.endsWith(".sft");
                    if ((isModelLoaderKey || isModelFile) && isModelInputDefinedInObjectInfo(classType, ik, objectInfo)) {
                        String matched = findModelInObjectInfo(classType, ik, s, objectInfo);
                        if (matched == null) {
                            missing.add(classType + " (" + ik + "): " + s);
                        }
                    }
                }
            }
        }
        return new ArrayList<>(missing);
    }

    private boolean isModelInputDefinedInObjectInfo(String classType, String inputKey, JSONObject objectInfo) {
        JSONObject nodeInfo = objectInfo.optJSONObject(classType);
        if (nodeInfo == null) return false;
        JSONObject input = nodeInfo.optJSONObject("input");
        if (input == null) return false;
        JSONObject required = input.optJSONObject("required");
        JSONObject optional = input.optJSONObject("optional");
        Object valObj = null;
        if (required != null && required.has(inputKey)) valObj = required.get(inputKey);
        else if (optional != null && optional.has(inputKey)) valObj = optional.get(inputKey);
        if (valObj instanceof JSONArray outerArray && outerArray.length() > 0) {
            Object first = outerArray.get(0);
            if (first instanceof JSONArray) return true;
            if (first instanceof String str && str.equalsIgnoreCase("COMBO")) return true;
        }
        return false;
    }

    /**
     * Logs missing model errors to the console and prompts the user with recovery actions
     * (downloading missing models or configuring model paths).
     *
     * @param parent           the parent UI component for the modal dialog
     * @param missingModels    the list of missing model descriptions
     * @param extraModelsPath  the currently configured extra models path
     * @param console          the console text area to append log messages to
     * @param onDownloadModels callback invoked when the user chooses to download missing models
     * @param onConfigurePaths callback invoked when the user chooses to configure paths
     */
    public void handleMissingModels(java.awt.Component parent, List<String> missingModels, String extraModelsPath,
                                    JTextArea console, Runnable onDownloadModels, Runnable onConfigurePaths) {
        if (missingModels == null || missingModels.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        sb.append("❌ Missing required models in ComfyUI:\n");
        for (String m : missingModels) {
            sb.append("   • ").append(m).append("\n");
        }
        sb.append("💡 Tip: Download the missing models via the Download Manager or verify your Extra Models Path in Settings -> Configure Paths.\n");
        if (console != null) {
            console.append(sb.toString());
        }

        StringBuilder msg = new StringBuilder();
        msg.append("The selected workflow requires model files that are currently not available in ComfyUI:\n\n");
        for (String m : missingModels) {
            msg.append("  • ").append(m).append("\n");
        }
        if (extraModelsPath != null && !extraModelsPath.isBlank()) {
            msg.append("\nCurrent Extra Models Path: ").append(extraModelsPath).append("\n");
        }
        msg.append("\nHow would you like to proceed?\n")
           .append("• Click 'Download Missing Models' to open this workflow in the Download Manager.\n")
           .append("• Click 'Configure Paths...' if your models are stored in a different directory (restart ComfyUI after changing paths).");

        Object[] options = {"Download Missing Models", "Configure Paths...", "Cancel"};
        int choice = JOptionPane.showOptionDialog(
                parent,
                msg.toString(),
                "Missing Required Models",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[0]
        );
        if (choice == 0 && onDownloadModels != null) {
            onDownloadModels.run();
        } else if (choice == 1 && onConfigurePaths != null) {
            onConfigurePaths.run();
        }
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
