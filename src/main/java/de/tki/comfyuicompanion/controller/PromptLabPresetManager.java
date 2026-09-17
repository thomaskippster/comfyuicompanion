package de.tki.comfyuicompanion.controller;

import de.tki.comfyuicompanion.domain.ComfyTemplate;
import de.tki.comfyuicompanion.service.IComfyTemplateService;
import de.tki.comfyuicompanion.service.IModelArchitectureService;
import de.tki.comfyuicompanion.ui.PromptLabView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Handles parameter preset selection, hardware/model defaults configuration,
 * and workflow graph extraction for Prompt Lab UI controls.
 */
public class PromptLabPresetManager {

    private static final Logger logger = LoggerFactory.getLogger(PromptLabPresetManager.class);

    private final IModelArchitectureService modelArchitectureService;
    private final IComfyTemplateService comfyTemplateService;
    private final PromptLabModelResolver modelResolver;

    public PromptLabPresetManager(IModelArchitectureService modelArchitectureService,
                                  IComfyTemplateService comfyTemplateService,
                                  PromptLabModelResolver modelResolver) {
        this.modelArchitectureService = modelArchitectureService;
        this.comfyTemplateService = comfyTemplateService;
        this.modelResolver = modelResolver;
    }

    public void applyModelPreset(PromptLabView view, String modelNameRaw, String modelName,
                                 String currentBlueprintGuiJson, Consumer<String> onJsonUpdated) {
        if (view == null || modelNameRaw == null || modelNameRaw.isEmpty() || modelName == null) return;

        if (currentBlueprintGuiJson == null || currentBlueprintGuiJson.isBlank()) {
            if (comfyTemplateService != null && modelResolver != null) {
                ComfyTemplate template = comfyTemplateService.determineTemplateForModel(modelName);
                if (template != null) {
                    String injected = modelResolver.injectModelsIntoTemplate(template.getContent(), modelName);
                    if (onJsonUpdated != null) {
                        onJsonUpdated.accept(injected);
                    }
                    if (view.getPromptJsonArea() != null) {
                        view.getPromptJsonArea().setText(injected);
                    }
                }
            }
        }

        String rawLower = modelNameRaw.toLowerCase();
        String actualLower = modelName.toLowerCase();
        String comboLower = (rawLower + " " + actualLower);

        if (comboLower.contains("z_image-turbo") || comboLower.contains("z-image-turbo") || comboLower.contains("z_image_turbo")
                || (comboLower.contains("z-image") && comboLower.contains("turbo")) || (comboLower.contains("z_image") && comboLower.contains("turbo"))) {
            view.getPromptPresetLabel().setText("Detected Preset: Z-Image Turbo (1024x1024)");
            view.getPromptWidthSpinner().setValue(1024);
            view.getPromptHeightSpinner().setValue(1024);
            view.getPromptStepsSpinner().setValue(8);
            view.getPromptCfgSpinner().setValue(1.0);
            selectOrAddComboItem(view.getPromptSamplerCombo(), "res_multistep");
            selectOrAddComboItem(view.getPromptSchedulerCombo(), "simple");
        } else if (comboLower.contains("z_image") || comboLower.contains("z-image") || comboLower.contains("acestep")) {
            view.getPromptPresetLabel().setText("Detected Preset: Z-Image (1024x1024)");
            view.getPromptWidthSpinner().setValue(1024);
            view.getPromptHeightSpinner().setValue(1024);
            view.getPromptStepsSpinner().setValue(25);
            view.getPromptCfgSpinner().setValue(4.0);
            selectOrAddComboItem(view.getPromptSamplerCombo(), "res_multistep");
            selectOrAddComboItem(view.getPromptSchedulerCombo(), "simple");
        } else if (comboLower.contains("qwen") && (comboLower.contains("edit") || comboLower.contains("2509"))) {
            view.getPromptPresetLabel().setText("Detected Preset: Qwen Image Edit 2509 (Fast 4-Step)");
            view.getPromptWidthSpinner().setValue(1024);
            view.getPromptHeightSpinner().setValue(1024);
            view.getPromptStepsSpinner().setValue(4);
            view.getPromptCfgSpinner().setValue(1.0);
            selectOrAddComboItem(view.getPromptSamplerCombo(), "euler");
            selectOrAddComboItem(view.getPromptSchedulerCombo(), "simple");
        } else if (comboLower.contains("flux1-schnell") || comboLower.contains("flux_schnell") || comboLower.contains("schnell")) {
            view.getPromptPresetLabel().setText("Detected Preset: FLUX.1 Schnell (Fast 4-Step)");
            view.getPromptWidthSpinner().setValue(1024);
            view.getPromptHeightSpinner().setValue(1024);
            view.getPromptStepsSpinner().setValue(4);
            view.getPromptCfgSpinner().setValue(1.0);
            selectOrAddComboItem(view.getPromptSamplerCombo(), "euler");
            selectOrAddComboItem(view.getPromptSchedulerCombo(), "simple");
        } else if (comboLower.contains("flux.2") || comboLower.contains("flux2") || comboLower.contains("klein")) {
            if (comboLower.contains("edit")) {
                view.getPromptPresetLabel().setText("Detected Preset: FLUX.2 [Klein] 9B: Image Edit");
            } else {
                view.getPromptPresetLabel().setText("Detected Preset: FLUX.2 [Klein] 9B: Text to Image");
            }
            view.getPromptWidthSpinner().setValue(1024);
            view.getPromptHeightSpinner().setValue(1024);
            view.getPromptStepsSpinner().setValue(20);
            view.getPromptCfgSpinner().setValue(5.0);
            selectOrAddComboItem(view.getPromptSamplerCombo(), "euler");
            selectOrAddComboItem(view.getPromptSchedulerCombo(), "simple");
        } else if (comboLower.contains("flux")) {
            view.getPromptPresetLabel().setText("Detected Preset: FLUX.1 (High Quality)");
            view.getPromptWidthSpinner().setValue(1024);
            view.getPromptHeightSpinner().setValue(1024);
            view.getPromptStepsSpinner().setValue(20);
            view.getPromptCfgSpinner().setValue(1.0);
            selectOrAddComboItem(view.getPromptSamplerCombo(), "euler");
            selectOrAddComboItem(view.getPromptSchedulerCombo(), "simple");
        } else if (comboLower.contains("xl") || comboLower.contains("juggernaut") || comboLower.contains("pony")) {
            view.getPromptPresetLabel().setText("Detected Preset: Stable Diffusion XL (SDXL)");
            view.getPromptWidthSpinner().setValue(1024);
            view.getPromptHeightSpinner().setValue(1024);
            view.getPromptStepsSpinner().setValue(30);
            view.getPromptCfgSpinner().setValue(6.0);
        } else if (comboLower.contains("longcat") || comboLower.contains("lumina")) {
            view.getPromptPresetLabel().setText("Detected Preset: Lumina2 / LongCat (1024x1024)");
            view.getPromptWidthSpinner().setValue(1024);
            view.getPromptHeightSpinner().setValue(1024);
            view.getPromptStepsSpinner().setValue(20);
            view.getPromptCfgSpinner().setValue(4.0);
        } else if (comboLower.contains("ltx")) {
            view.getPromptPresetLabel().setText("Detected Preset: LTX-Video / Image Transformer");
            view.getPromptWidthSpinner().setValue(768);
            view.getPromptHeightSpinner().setValue(512);
            view.getPromptStepsSpinner().setValue(20);
            view.getPromptCfgSpinner().setValue(3.0);
        } else if (comboLower.contains("hunyuan")) {
            view.getPromptPresetLabel().setText("Detected Preset: Hunyuan 3D / Video");
            view.getPromptWidthSpinner().setValue(1024);
            view.getPromptHeightSpinner().setValue(1024);
            view.getPromptStepsSpinner().setValue(30);
            view.getPromptCfgSpinner().setValue(5.0);
        } else if (comboLower.contains("turbo")) {
            view.getPromptPresetLabel().setText("Detected Preset: SD Turbo / fast inference");
            view.getPromptWidthSpinner().setValue(512);
            view.getPromptHeightSpinner().setValue(512);
            view.getPromptStepsSpinner().setValue(8);
            view.getPromptCfgSpinner().setValue(1.5);
        } else if (comboLower.contains("sd1") || comboLower.contains("sd 1") || comboLower.contains("v1-5") || comboLower.contains("v1.5") || comboLower.contains("sd-1-5")) {
            view.getPromptPresetLabel().setText("Detected Preset: Stable Diffusion 1.5 (SD 1.5)");
            view.getPromptWidthSpinner().setValue(512);
            view.getPromptHeightSpinner().setValue(512);
            view.getPromptStepsSpinner().setValue(20);
            view.getPromptCfgSpinner().setValue(7.0);
        } else {
            de.tki.comfyuicompanion.domain.ModelArchitecture arch = (modelArchitectureService != null)
                    ? modelArchitectureService.detectArchitecture(modelName)
                    : de.tki.comfyuicompanion.domain.ModelArchitecture.ARCH_UNKNOWN;

            switch (arch) {
                case ARCH_FLUX -> {
                    view.getPromptPresetLabel().setText("Detected Preset: FLUX (1024x1024)");
                    view.getPromptWidthSpinner().setValue(1024);
                    view.getPromptHeightSpinner().setValue(1024);
                    view.getPromptStepsSpinner().setValue(20);
                    view.getPromptCfgSpinner().setValue(1.0);
                    selectOrAddComboItem(view.getPromptSamplerCombo(), "euler");
                    selectOrAddComboItem(view.getPromptSchedulerCombo(), "simple");
                }
                case ARCH_SDXL -> {
                    view.getPromptPresetLabel().setText("Detected Preset: SDXL (1024x1024)");
                    view.getPromptWidthSpinner().setValue(1024);
                    view.getPromptHeightSpinner().setValue(1024);
                    view.getPromptStepsSpinner().setValue(30);
                    view.getPromptCfgSpinner().setValue(6.0);
                }
                case ARCH_SD3 -> {
                    view.getPromptPresetLabel().setText("Detected Preset: Stable Diffusion 3 / 3.5 (1024x1024)");
                    view.getPromptWidthSpinner().setValue(1024);
                    view.getPromptHeightSpinner().setValue(1024);
                    view.getPromptStepsSpinner().setValue(28);
                    view.getPromptCfgSpinner().setValue(4.5);
                }
                case ARCH_WAN -> {
                    view.getPromptPresetLabel().setText("Detected Preset: Wan 2.1 / 2.2 (1024x1024)");
                    view.getPromptWidthSpinner().setValue(1024);
                    view.getPromptHeightSpinner().setValue(1024);
                    view.getPromptStepsSpinner().setValue(30);
                    view.getPromptCfgSpinner().setValue(5.0);
                }
                case ARCH_HUNYUAN -> {
                    view.getPromptPresetLabel().setText("Detected Preset: Hunyuan (1024x1024)");
                    view.getPromptWidthSpinner().setValue(1024);
                    view.getPromptHeightSpinner().setValue(1024);
                    view.getPromptStepsSpinner().setValue(30);
                    view.getPromptCfgSpinner().setValue(5.0);
                }
                case ARCH_LUMINA2 -> {
                    view.getPromptPresetLabel().setText("Detected Preset: Lumina2 (1024x1024)");
                    view.getPromptWidthSpinner().setValue(1024);
                    view.getPromptHeightSpinner().setValue(1024);
                    view.getPromptStepsSpinner().setValue(20);
                    view.getPromptCfgSpinner().setValue(4.0);
                }
                case ARCH_SD15 -> {
                    view.getPromptPresetLabel().setText("Detected Preset: Stable Diffusion 1.5 (SD 1.5)");
                    view.getPromptWidthSpinner().setValue(512);
                    view.getPromptHeightSpinner().setValue(512);
                    view.getPromptStepsSpinner().setValue(20);
                    view.getPromptCfgSpinner().setValue(7.0);
                }
                default -> {
                    view.getPromptPresetLabel().setText("Detected Preset: Modern Diffusion (1024x1024)");
                    view.getPromptWidthSpinner().setValue(1024);
                    view.getPromptHeightSpinner().setValue(1024);
                    view.getPromptStepsSpinner().setValue(20);
                    view.getPromptCfgSpinner().setValue(4.0);
                }
            }
        }
        populateUiFromWorkflow(view, currentBlueprintGuiJson);
    }

    public void populateUiFromWorkflow(PromptLabView view, String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty() || view == null) return;
        try {
            JSONObject obj = new JSONObject(jsonStr);

            Integer extractedWidth = null;
            Integer extractedHeight = null;
            Integer extractedBatch = null;
            Integer extractedSteps = null;
            Double extractedCfg = null;
            Double extractedDenoise = null;
            String extractedSampler = null;
            String extractedScheduler = null;

            if (obj.has("nodes")) {
                List<JSONObject> allNodes = collectAllNodes(obj);

                for (JSONObject node : allNodes) {
                    String type = node.optString("type", "");
                    JSONArray widgets = node.optJSONArray("widgets_values");
                    JSONObject inputs = node.optJSONObject("inputs");

                    if (type.contains("Empty") || type.contains("Latent")) {
                        if (widgets != null && widgets.length() >= 2) {
                            if (widgets.get(0) instanceof Number num && extractedWidth == null) extractedWidth = num.intValue();
                            if (widgets.get(1) instanceof Number num && extractedHeight == null) extractedHeight = num.intValue();
                            if (widgets.length() >= 3 && widgets.get(2) instanceof Number num && extractedBatch == null) extractedBatch = num.intValue();
                        }
                        if (inputs != null) {
                            if (inputs.has("width") && extractedWidth == null) extractedWidth = inputs.optInt("width");
                            if (inputs.has("height") && extractedHeight == null) extractedHeight = inputs.optInt("height");
                            if (inputs.has("batch_size") && extractedBatch == null) extractedBatch = inputs.optInt("batch_size");
                        }
                    }

                    if ("Flux2Scheduler".equals(type)) {
                        if (widgets != null && widgets.length() > 0 && widgets.get(0) instanceof Number num && extractedSteps == null) {
                            extractedSteps = num.intValue();
                        }
                        if (widgets != null && widgets.length() > 1 && widgets.get(1) instanceof Number num && extractedWidth == null) {
                            extractedWidth = num.intValue();
                        }
                        if (widgets != null && widgets.length() > 2 && widgets.get(2) instanceof Number num && extractedHeight == null) {
                            extractedHeight = num.intValue();
                        }
                        if (inputs != null) {
                            if (inputs.has("steps") && extractedSteps == null) extractedSteps = inputs.optInt("steps");
                            if (inputs.has("width") && extractedWidth == null) extractedWidth = inputs.optInt("width");
                            if (inputs.has("height") && extractedHeight == null) extractedHeight = inputs.optInt("height");
                        }
                        if (extractedScheduler == null) extractedScheduler = "simple";
                    }

                    if ("BasicScheduler".equals(type) || "BetaScheduler".equals(type)) {
                        if (widgets != null && widgets.length() > 0 && widgets.get(0) instanceof String s && extractedScheduler == null) {
                            if (!s.isBlank() && !s.equalsIgnoreCase("COMBO")) extractedScheduler = s;
                        }
                        if (widgets != null && widgets.length() > 1 && widgets.get(1) instanceof Number num && extractedSteps == null) {
                            extractedSteps = num.intValue();
                        }
                        if (widgets != null && widgets.length() > 2 && widgets.get(2) instanceof Number num && extractedDenoise == null) {
                            extractedDenoise = num.doubleValue();
                        }
                        if (inputs != null) {
                            if (inputs.has("scheduler") && extractedScheduler == null && !inputs.optString("scheduler").equalsIgnoreCase("COMBO")) {
                                extractedScheduler = inputs.optString("scheduler");
                            }
                            if (inputs.has("steps") && extractedSteps == null) extractedSteps = inputs.optInt("steps");
                            if (inputs.has("denoise") && extractedDenoise == null) extractedDenoise = inputs.optDouble("denoise");
                        }
                    }

                    if ("SDTurboScheduler".equals(type)) {
                        if (widgets != null && widgets.length() > 0 && widgets.get(0) instanceof Number num && extractedSteps == null) {
                            extractedSteps = num.intValue();
                        }
                        if (widgets != null && widgets.length() > 1 && widgets.get(1) instanceof Number num && extractedDenoise == null) {
                            extractedDenoise = num.doubleValue();
                        }
                        if (inputs != null) {
                            if (inputs.has("steps") && extractedSteps == null) extractedSteps = inputs.optInt("steps");
                            if (inputs.has("denoise") && extractedDenoise == null) extractedDenoise = inputs.optDouble("denoise");
                        }
                    }

                    if ("KSamplerSelect".equals(type)) {
                        if (widgets != null && widgets.length() > 0 && extractedSampler == null) {
                            Object first = widgets.get(0);
                            String s = (first instanceof String str) ? str : first.toString();
                            if (!s.isBlank() && !s.equalsIgnoreCase("COMBO")) extractedSampler = s;
                        }
                        if (inputs != null && inputs.has("sampler_name") && extractedSampler == null && !inputs.optString("sampler_name").equalsIgnoreCase("COMBO")) {
                            extractedSampler = inputs.optString("sampler_name");
                        }
                    }

                    if ("CFGGuider".equals(type)) {
                        if (widgets != null && widgets.length() > 0 && widgets.get(0) instanceof Number num && extractedCfg == null) {
                            extractedCfg = num.doubleValue();
                        }
                        if (inputs != null && inputs.has("cfg") && extractedCfg == null) extractedCfg = inputs.optDouble("cfg");
                    } else if ("FluxGuidance".equals(type)) {
                        if (widgets != null && widgets.length() > 0 && widgets.get(0) instanceof Number num && extractedCfg == null) {
                            extractedCfg = num.doubleValue();
                        }
                        if (inputs != null && inputs.has("guidance") && extractedCfg == null) extractedCfg = inputs.optDouble("guidance");
                    }

                    if ("KSampler".equals(type)) {
                        if (widgets != null) {
                            if (widgets.length() > 2 && widgets.get(2) instanceof Number num && extractedSteps == null) extractedSteps = num.intValue();
                            if (widgets.length() > 3 && widgets.get(3) instanceof Number num && extractedCfg == null) extractedCfg = num.doubleValue();
                            if (widgets.length() > 4 && widgets.get(4) instanceof String s && extractedSampler == null) {
                                if (!s.isBlank() && !s.equalsIgnoreCase("COMBO")) extractedSampler = s;
                            }
                            if (widgets.length() > 5 && widgets.get(5) instanceof String s && extractedScheduler == null) {
                                if (!s.isBlank() && !s.equalsIgnoreCase("COMBO")) extractedScheduler = s;
                            }
                            if (widgets.length() > 6 && widgets.get(6) instanceof Number num && extractedDenoise == null) extractedDenoise = num.doubleValue();
                        }
                        if (inputs != null) {
                            if (inputs.has("steps") && extractedSteps == null) extractedSteps = inputs.optInt("steps");
                            if (inputs.has("cfg") && extractedCfg == null) extractedCfg = inputs.optDouble("cfg");
                            if (inputs.has("sampler_name") && extractedSampler == null && !inputs.optString("sampler_name").equalsIgnoreCase("COMBO")) extractedSampler = inputs.optString("sampler_name");
                            if (inputs.has("scheduler") && extractedScheduler == null && !inputs.optString("scheduler").equalsIgnoreCase("COMBO")) extractedScheduler = inputs.optString("scheduler");
                            if (inputs.has("denoise") && extractedDenoise == null) extractedDenoise = inputs.optDouble("denoise");
                        }
                    } else if ("KSamplerAdvanced".equals(type)) {
                        if (widgets != null) {
                            if (widgets.length() > 3 && widgets.get(3) instanceof Number num && extractedSteps == null) extractedSteps = num.intValue();
                            if (widgets.length() > 4 && widgets.get(4) instanceof Number num && extractedCfg == null) extractedCfg = num.doubleValue();
                            if (widgets.length() > 5 && widgets.get(5) instanceof String s && extractedSampler == null) {
                                if (!s.isBlank() && !s.equalsIgnoreCase("COMBO")) extractedSampler = s;
                            }
                            if (widgets.length() > 6 && widgets.get(6) instanceof String s && extractedScheduler == null) {
                                if (!s.isBlank() && !s.equalsIgnoreCase("COMBO")) extractedScheduler = s;
                            }
                        }
                        if (inputs != null) {
                            if (inputs.has("steps") && extractedSteps == null) extractedSteps = inputs.optInt("steps");
                            if (inputs.has("cfg") && extractedCfg == null) extractedCfg = inputs.optDouble("cfg");
                            if (inputs.has("sampler_name") && extractedSampler == null && !inputs.optString("sampler_name").equalsIgnoreCase("COMBO")) extractedSampler = inputs.optString("sampler_name");
                            if (inputs.has("scheduler") && extractedScheduler == null && !inputs.optString("scheduler").equalsIgnoreCase("COMBO")) extractedScheduler = inputs.optString("scheduler");
                        }
                    }
                }
            } else {
                JSONObject promptObj = obj.has("prompt") ? obj.getJSONObject("prompt") : obj;
                for (String key : promptObj.keySet()) {
                    JSONObject node = promptObj.optJSONObject(key);
                    if (node == null) continue;
                    String classType = node.optString("class_type", "");
                    JSONObject inputs = node.optJSONObject("inputs");
                    if (inputs == null) continue;

                    if (classType.contains("KSampler")) {
                        if (inputs.has("steps") && extractedSteps == null) extractedSteps = inputs.optInt("steps");
                        if (inputs.has("cfg") && extractedCfg == null) extractedCfg = inputs.optDouble("cfg");
                        if (inputs.has("sampler_name") && extractedSampler == null && !inputs.optString("sampler_name").equalsIgnoreCase("COMBO")) extractedSampler = inputs.optString("sampler_name");
                        if (inputs.has("scheduler") && extractedScheduler == null && !inputs.optString("scheduler").equalsIgnoreCase("COMBO")) extractedScheduler = inputs.optString("scheduler");
                        if (inputs.has("denoise") && extractedDenoise == null) extractedDenoise = inputs.optDouble("denoise");
                    } else if ("KSamplerSelect".equals(classType)) {
                        if (inputs.has("sampler_name") && extractedSampler == null && !inputs.optString("sampler_name").equalsIgnoreCase("COMBO")) extractedSampler = inputs.optString("sampler_name");
                    } else if ("Flux2Scheduler".equals(classType)) {
                        if (inputs.has("steps") && extractedSteps == null) extractedSteps = inputs.optInt("steps");
                        if (inputs.has("width") && extractedWidth == null) extractedWidth = inputs.optInt("width");
                        if (inputs.has("height") && extractedHeight == null) extractedHeight = inputs.optInt("height");
                        if (extractedScheduler == null) extractedScheduler = "simple";
                    } else if ("BasicScheduler".equals(classType) || "BetaScheduler".equals(classType)) {
                        if (inputs.has("scheduler") && extractedScheduler == null && !inputs.optString("scheduler").equalsIgnoreCase("COMBO")) extractedScheduler = inputs.optString("scheduler");
                        if (inputs.has("steps") && extractedSteps == null) extractedSteps = inputs.optInt("steps");
                        if (inputs.has("denoise") && extractedDenoise == null) extractedDenoise = inputs.optDouble("denoise");
                    } else if ("SDTurboScheduler".equals(classType)) {
                        if (inputs.has("steps") && extractedSteps == null) extractedSteps = inputs.optInt("steps");
                        if (inputs.has("denoise") && extractedDenoise == null) extractedDenoise = inputs.optDouble("denoise");
                    } else if ("CFGGuider".equals(classType)) {
                        if (inputs.has("cfg") && extractedCfg == null) extractedCfg = inputs.optDouble("cfg");
                    } else if ("FluxGuidance".equals(classType)) {
                        if (inputs.has("guidance") && extractedCfg == null) extractedCfg = inputs.optDouble("guidance");
                    } else if (classType.contains("Empty") || classType.contains("Latent")) {
                        if (inputs.has("width") && extractedWidth == null) extractedWidth = inputs.optInt("width");
                        if (inputs.has("height") && extractedHeight == null) extractedHeight = inputs.optInt("height");
                        if (inputs.has("batch_size") && extractedBatch == null) extractedBatch = inputs.optInt("batch_size");
                    }
                }
            }

            if (extractedWidth != null && extractedWidth > 0 && view.getPromptWidthSpinner() != null) {
                view.getPromptWidthSpinner().setValue(extractedWidth);
            }
            if (extractedHeight != null && extractedHeight > 0 && view.getPromptHeightSpinner() != null) {
                view.getPromptHeightSpinner().setValue(extractedHeight);
            }
            if (extractedBatch != null && extractedBatch > 0 && view.getPromptBatchSizeSpinner() != null) {
                view.getPromptBatchSizeSpinner().setValue(extractedBatch);
            }
            if (extractedSteps != null && extractedSteps > 0 && view.getPromptStepsSpinner() != null) {
                view.getPromptStepsSpinner().setValue(extractedSteps);
            }
            if (extractedCfg != null && extractedCfg >= 0 && view.getPromptCfgSpinner() != null) {
                view.getPromptCfgSpinner().setValue(extractedCfg);
            }
            if (extractedDenoise != null && extractedDenoise >= 0.0 && extractedDenoise <= 1.0 && view.getPromptDenoiseSpinner() != null) {
                view.getPromptDenoiseSpinner().setValue(extractedDenoise);
            }
            if (extractedSampler != null && !extractedSampler.isBlank() && view.getPromptSamplerCombo() != null) {
                selectOrAddComboItem(view.getPromptSamplerCombo(), extractedSampler);
            }
            if (extractedScheduler != null && !extractedScheduler.isBlank() && view.getPromptSchedulerCombo() != null) {
                selectOrAddComboItem(view.getPromptSchedulerCombo(), extractedScheduler);
            }
        } catch (Exception e) {
            logger.warn("Failed to parse workflow to extract defaults: {}", e.getMessage());
        }
    }

    public List<JSONObject> collectAllNodes(JSONObject root) {
        List<JSONObject> allNodes = new ArrayList<>();
        if (root == null) return allNodes;

        if (root.has("nodes") && root.get("nodes") instanceof JSONArray nodes) {
            for (int i = 0; i < nodes.length(); i++) {
                JSONObject node = nodes.optJSONObject(i);
                if (node != null) {
                    allNodes.add(node);
                }
            }
        }

        if (root.has("definitions") && root.get("definitions") instanceof JSONObject defs) {
            if (defs.has("subgraphs") && defs.get("subgraphs") instanceof JSONArray subgraphs) {
                for (int i = 0; i < subgraphs.length(); i++) {
                    JSONObject sg = subgraphs.optJSONObject(i);
                    if (sg != null && sg.has("nodes") && sg.get("nodes") instanceof JSONArray sgNodes) {
                        for (int j = 0; j < sgNodes.length(); j++) {
                            JSONObject sn = sgNodes.optJSONObject(j);
                            if (sn != null) {
                                allNodes.add(sn);
                            }
                        }
                    }
                }
            }
        }
        return allNodes;
    }

    public void selectOrAddComboItem(JComboBox<String> combo, String target) {
        if (combo == null || target == null || target.isBlank()) return;
        String cleanTarget = target.trim();
        if (cleanTarget.equalsIgnoreCase("COMBO")) return;
        for (int i = 0; i < combo.getItemCount(); i++) {
            String item = combo.getItemAt(i);
            if (item != null && item.equalsIgnoreCase(cleanTarget)) {
                combo.setSelectedIndex(i);
                return;
            }
        }
        combo.addItem(cleanTarget);
        combo.setSelectedItem(cleanTarget);
    }
}
