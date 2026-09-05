package de.tki.comfymodels.controller;

import de.tki.comfymodels.domain.ComfyTemplate;
import de.tki.comfymodels.service.IComfyTemplateService;
import de.tki.comfymodels.service.IModelArchitectureService;
import de.tki.comfymodels.service.IWorkflowDownloader;
import de.tki.comfymodels.service.PromptBlueprintApiService;
import de.tki.comfymodels.service.impl.ComfyApiClient;
import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.ui.BlueprintGalleryTab;
import de.tki.comfymodels.ui.PromptLabView;
import de.tki.comfymodels.util.BackgroundExecutor;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controller component for managing Prompt Lab business logic, state variables,
 * model resolution, and session persistence.
 */
@Component
public class PromptLabController implements PromptLabView.PromptLabController {

    private static final Logger logger = LoggerFactory.getLogger(PromptLabController.class);

    private final ConfigService configService;
    private final PromptBlueprintApiService promptBlueprintApiService;
    private final ComfyApiClient comfyApiClient;
    private final IComfyTemplateService comfyTemplateService;
    private final IModelArchitectureService modelArchitectureService;
    private final IWorkflowDownloader workflowDownloader;
    private final BackgroundExecutor backgroundExecutor;

    private final Set<String> comfyCheckpoints = ConcurrentHashMap.newKeySet();
    private final Set<String> comfyUnetModels = ConcurrentHashMap.newKeySet();
    private final Set<String> comfyClips = ConcurrentHashMap.newKeySet();
    private final Set<String> comfyVaes = ConcurrentHashMap.newKeySet();
    private final Set<String> comfyClipTypes = ConcurrentHashMap.newKeySet();
    private final Set<String> comfyUnetWeightDtypes = ConcurrentHashMap.newKeySet();

    private volatile String currentBlueprintGuiJson = null;
    private final java.util.concurrent.atomic.AtomicBoolean isUpdatingCombo = new java.util.concurrent.atomic.AtomicBoolean(false);
    private PromptLabView view;
    private BlueprintGalleryTab blueprintGalleryTab;
    private Runnable sendPromptHandler;
    private Runnable suggestSubjectHandler;

    public PromptLabController() {
        this.configService = null;
        this.promptBlueprintApiService = new PromptBlueprintApiService();
        this.comfyApiClient = null;
        this.comfyTemplateService = null;
        this.modelArchitectureService = null;
        this.workflowDownloader = null;
        this.backgroundExecutor = null;
    }

    @Autowired
    public PromptLabController(ConfigService configService,
                               PromptBlueprintApiService promptBlueprintApiService,
                               ComfyApiClient comfyApiClient,
                               @Autowired(required = false) IComfyTemplateService comfyTemplateService,
                               @Autowired(required = false) IModelArchitectureService modelArchitectureService,
                               @Autowired(required = false) IWorkflowDownloader workflowDownloader,
                               @Autowired(required = false) BackgroundExecutor backgroundExecutor) {
        this.configService = configService;
        this.promptBlueprintApiService = promptBlueprintApiService;
        this.comfyApiClient = comfyApiClient;
        this.comfyTemplateService = comfyTemplateService;
        this.modelArchitectureService = modelArchitectureService;
        this.workflowDownloader = workflowDownloader;
        this.backgroundExecutor = backgroundExecutor;
    }

    public void setView(PromptLabView view) {
        this.view = view;
        if (this.view != null) {
            this.view.setController(this);
        }
    }

    public void setBlueprintGalleryTab(BlueprintGalleryTab blueprintGalleryTab) {
        this.blueprintGalleryTab = blueprintGalleryTab;
    }

    public void setSendPromptHandler(Runnable sendPromptHandler) {
        this.sendPromptHandler = sendPromptHandler;
    }

    public void setSuggestSubjectHandler(Runnable suggestSubjectHandler) {
        this.suggestSubjectHandler = suggestSubjectHandler;
    }

    @Override
    public void onRefreshModels() {
        refreshPromptLabModels();
    }

    @Override
    public void onSuggestSubject() {
        if (suggestSubjectHandler != null) {
            suggestSubjectHandler.run();
        }
    }

    @Override
    public void onBlueprintSelected(String selectedModel) {
        onPromptBlueprintSelected(selectedModel);
    }

    @Override
    public void onUpdatePromptLabJson() {
        updatePromptLabJson();
    }

    @Override
    public void onSendPromptToComfyUI() {
        if (sendPromptHandler != null) {
            sendPromptHandler.run();
        }
    }

    public String getCurrentBlueprintGuiJson() {
        return currentBlueprintGuiJson;
    }

    public void setCurrentBlueprintGuiJson(String currentBlueprintGuiJson) {
        this.currentBlueprintGuiJson = currentBlueprintGuiJson;
    }

    public Set<String> getComfyCheckpoints() { return comfyCheckpoints; }
    public Set<String> getComfyUnetModels() { return comfyUnetModels; }
    public Set<String> getComfyClips() { return comfyClips; }
    public Set<String> getComfyVaes() { return comfyVaes; }
    public Set<String> getComfyClipTypes() { return comfyClipTypes; }
    public Set<String> getComfyUnetWeightDtypes() { return comfyUnetWeightDtypes; }

    public void applyModelPreset(String modelNameRaw) {
        if (view == null || modelNameRaw == null || modelNameRaw.isEmpty()) return;
        
        String modelName = getActualModelForPromptLab(modelNameRaw);
        if (modelName == null) return;
        
        if (currentBlueprintGuiJson == null || currentBlueprintGuiJson.isBlank()) {
            if (comfyTemplateService != null) {
                ComfyTemplate template = comfyTemplateService.determineTemplateForModel(modelName);
                if (template != null) {
                    currentBlueprintGuiJson = injectModelsIntoTemplate(template.getContent(), modelName);
                    if (view.getPromptJsonArea() != null) {
                        view.getPromptJsonArea().setText(currentBlueprintGuiJson);
                    }
                }
            }
        }

        String rawLower = modelNameRaw.toLowerCase();
        String actualLower = modelName.toLowerCase();
        String comboLower = (rawLower + " " + actualLower);

        if (comboLower.contains("z_image-turbo") || comboLower.contains("z-image-turbo") || comboLower.contains("z_image_turbo") || (comboLower.contains("z-image") && comboLower.contains("turbo")) || (comboLower.contains("z_image") && comboLower.contains("turbo"))) {
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
            // Modern Generic Architecture Fallback (1024x1024 HD)
            de.tki.comfymodels.domain.ModelArchitecture arch = (modelArchitectureService != null)
                    ? modelArchitectureService.detectArchitecture(modelName)
                    : de.tki.comfymodels.domain.ModelArchitecture.ARCH_UNKNOWN;

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
        populateUiFromWorkflow(currentBlueprintGuiJson);
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
            logger.warn("Failed to inject model parameters into template: " + e.getMessage());
            return templateJson;
        }
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

    public void populateUiFromWorkflow(String jsonStr) {
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
                // GUI LiteGraph format
                List<JSONObject> allNodes = collectAllNodes(obj);

                for (JSONObject node : allNodes) {
                    String type = node.optString("type", "");
                    JSONArray widgets = node.optJSONArray("widgets_values");
                    JSONObject inputs = node.optJSONObject("inputs");

                    // 1. Latent / Dimensions / Batch
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

                    // 2. Flux2Scheduler
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

                    // 3. BasicScheduler / BetaScheduler
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

                    // 4. SDTurboScheduler
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

                    // 5. KSamplerSelect
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

                    // 6. CFGGuider / FluxGuidance
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

                    // 7. KSampler / KSamplerAdvanced
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
                // API format
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

            // Apply extracted values to UI controls
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
            logger.warn("Failed to parse workflow to extract defaults: " + e.getMessage());
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
        
        // 1. Identify clip type directly from the clipModel itself first
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
        
        // 2. If clipModel is generic or empty, infer from selectedModel
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
        
        for (String ext : new String[]{".safetensors", ".ckpt", ".pt", ".bin"}) {
            if (a.endsWith(ext)) a = a.substring(0, a.length() - ext.length());
            if (b.endsWith(ext)) b = b.substring(0, b.length() - ext.length());
        }
        
        return a.equals(b);
    }

    public boolean isDiffusionModel(String modelName) {
        if (modelName == null) return false;
        String clean = modelName.replace("\\", "/");
        if (clean.startsWith("diffusion_models/")) {
            clean = clean.substring(17);
        }
        for (String unet : comfyUnetModels) {
            if (modelsMatch(modelName, unet)) {
                return true;
            }
        }
        String lower = modelName.toLowerCase();
        return lower.contains("diffusion_models") || 
               lower.contains("z_image") || 
               lower.contains("z-image") || 
               lower.contains("acestep") || 
               lower.contains("flux-2-klein") || 
               lower.contains("longcat") || 
               lower.contains("lumina") || 
               lower.contains("wan2.1") || 
               lower.contains("wan2.2");
    }

    public String resolveClipForModel(String modelName) {
        if (modelName == null) return "qwen_3_4b.safetensors";
        String lower = modelName.toLowerCase();
        
        if (lower.contains("z_image") || lower.contains("z-image") || lower.contains("acestep") || lower.contains("longcat") || lower.contains("lumina")) {
            for (String clip : comfyClips) {
                String cLower = clip.toLowerCase();
                if (cLower.contains("gemma4") || cLower.contains("gemma-4") || cLower.contains("gemma_4") ||
                    cLower.contains("gemma2") || cLower.contains("gemma-2") || cLower.contains("gemma_2") ||
                    cLower.contains("gemma")) {
                    return clip;
                }
            }
            for (String clip : comfyClips) {
                String cLower = clip.toLowerCase();
                if (cLower.contains("qwen_3_4b") || cLower.contains("qwen-3-4b") || cLower.contains("qwen_3.4b") || cLower.contains("qwen3")) {
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

    public String getActualModelForPromptLab(String comboSelection) {
        if (comboSelection == null) return null;
        if (blueprintGalleryTab != null) {
            for (BlueprintGalleryTab.BlueprintEntry e : blueprintGalleryTab.getAvailableBlueprints()) {
                if (e.name.equals(comboSelection)) {
                    if (!e.requiredModels.isEmpty()) {
                        return e.requiredModels.get(0).getName();
                    }
                }
            }
        }
        return comboSelection;
    }

    public String getBlueprintNameForModel(String modelName) {
        if (modelName == null) return null;
        if (blueprintGalleryTab != null) {
            for (BlueprintGalleryTab.BlueprintEntry e : blueprintGalleryTab.getAvailableBlueprints()) {
                if (!e.requiredModels.isEmpty() && e.requiredModels.get(0).getName().equals(modelName)) {
                    return e.name;
                }
            }
        }
        return modelName;
    }

    public void updatePromptLabJson() {
        if (view == null) return;
        if (view.getPromptSubjectField() == null || view.getPromptAssembleArea() == null || view.getPromptJsonArea() == null) {
            return;
        }

        String subject = view.getPromptSubjectField().getText().trim();
        String negative = (view.getPromptNegativeField() != null) ? view.getPromptNegativeField().getText().trim() : "";

        view.getPromptAssembleArea().setText(subject);

        try {
            String currentJsonStr = view.getPromptJsonArea().getText();
            if (currentJsonStr == null || currentJsonStr.isBlank()) return;

            JSONObject mainObj = new JSONObject(currentJsonStr);
            if (!mainObj.has("prompt")) return;

            JSONObject promptObj = mainObj.getJSONObject("prompt");

            int width = (view.getPromptWidthSpinner() != null) ? (Integer) view.getPromptWidthSpinner().getValue() : 1024;
            int height = (view.getPromptHeightSpinner() != null) ? (Integer) view.getPromptHeightSpinner().getValue() : 1024;
            int batch = (view.getPromptBatchSizeSpinner() != null) ? (Integer) view.getPromptBatchSizeSpinner().getValue() : 1;
            int steps = (view.getPromptStepsSpinner() != null) ? (Integer) view.getPromptStepsSpinner().getValue() : 20;
            double cfg = (view.getPromptCfgSpinner() != null) ? ((Number) view.getPromptCfgSpinner().getValue()).doubleValue() : 7.0;
            double denoise = (view.getPromptDenoiseSpinner() != null) ? ((Number) view.getPromptDenoiseSpinner().getValue()).doubleValue() : 1.0;
            String samplerName = (view.getPromptSamplerCombo() != null && view.getPromptSamplerCombo().getSelectedItem() != null) ? (String) view.getPromptSamplerCombo().getSelectedItem() : "Auto";
            String scheduler = (view.getPromptSchedulerCombo() != null && view.getPromptSchedulerCombo().getSelectedItem() != null) ? (String) view.getPromptSchedulerCombo().getSelectedItem() : "Auto";

            String inputImage = (view.getPromptImageFileField() != null) ? view.getPromptImageFileField().getText() : null;
            PromptBlueprintApiService.PromptLabInputs previewInputs =
                new PromptBlueprintApiService.PromptLabInputs(
                    subject, negative, width, height, steps, cfg, 0L, samplerName, scheduler, denoise, batch, inputImage
                );
            promptBlueprintApiService.injectLabInputs(promptObj, previewInputs);

            view.getPromptJsonArea().setText(mainObj.toString(2));
        } catch (Exception ex) {
            // Ignore parse errors from user manual edits
        }
        savePromptLabSession();
    }

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

    public void onPromptBlueprintSelected(String selectedName) {
        if (selectedName == null || selectedName.isEmpty() || view == null) return;
        if (isUpdatingCombo.get()) return;

        BlueprintGalleryTab.BlueprintEntry matchedEntry = null;
        if (blueprintGalleryTab != null) {
            for (BlueprintGalleryTab.BlueprintEntry e : blueprintGalleryTab.getAvailableBlueprints()) {
                if (e.name.equalsIgnoreCase(selectedName) || e.filename.equalsIgnoreCase(selectedName)) {
                    matchedEntry = e;
                    break;
                }
            }
            if (matchedEntry == null) {
                for (BlueprintGalleryTab.BlueprintEntry e : blueprintGalleryTab.getAvailableBlueprints()) {
                    if (isBlueprintNameMatch(e.name, selectedName) || isBlueprintNameMatch(e.filename, selectedName)) {
                        matchedEntry = e;
                        break;
                    }
                }
            }
        }

        if (matchedEntry != null) {
            if (view.getPromptPresetLabel() != null) {
                view.getPromptPresetLabel().setText("Selected Blueprint: " + matchedEntry.name + " (" + matchedEntry.category + ")");
            }

            String nameLower = matchedEntry.name.toLowerCase();
            String catLower = matchedEntry.category.toLowerCase();
            if (nameLower.contains("z-image") || nameLower.contains("turbo") || nameLower.contains("schnell") || nameLower.contains("lightning") || nameLower.contains("hyper")) {
                if (view.getPromptWidthSpinner() != null) view.getPromptWidthSpinner().setValue(1024);
                if (view.getPromptHeightSpinner() != null) view.getPromptHeightSpinner().setValue(1024);
                if (view.getPromptStepsSpinner() != null) view.getPromptStepsSpinner().setValue(8);
                if (view.getPromptCfgSpinner() != null) view.getPromptCfgSpinner().setValue(1.0);
            } else if (nameLower.contains("flux") || catLower.contains("flux")) {
                if (view.getPromptWidthSpinner() != null) view.getPromptWidthSpinner().setValue(1024);
                if (view.getPromptHeightSpinner() != null) view.getPromptHeightSpinner().setValue(1024);
                if (view.getPromptStepsSpinner() != null) view.getPromptStepsSpinner().setValue(20);
                if (view.getPromptCfgSpinner() != null) view.getPromptCfgSpinner().setValue(1.0);
            } else if (nameLower.contains("sdxl") || catLower.contains("sdxl")) {
                if (view.getPromptWidthSpinner() != null) view.getPromptWidthSpinner().setValue(1024);
                if (view.getPromptHeightSpinner() != null) view.getPromptHeightSpinner().setValue(1024);
                if (view.getPromptStepsSpinner() != null) view.getPromptStepsSpinner().setValue(25);
                if (view.getPromptCfgSpinner() != null) view.getPromptCfgSpinner().setValue(7.0);
            } else if (nameLower.contains("wan") || nameLower.contains("video") || catLower.contains("video")) {
                if (view.getPromptWidthSpinner() != null) view.getPromptWidthSpinner().setValue(832);
                if (view.getPromptHeightSpinner() != null) view.getPromptHeightSpinner().setValue(480);
                if (view.getPromptStepsSpinner() != null) view.getPromptStepsSpinner().setValue(20);
                if (view.getPromptCfgSpinner() != null) view.getPromptCfgSpinner().setValue(6.0);
            } else {
                if (view.getPromptWidthSpinner() != null) view.getPromptWidthSpinner().setValue(1024);
                if (view.getPromptHeightSpinner() != null) view.getPromptHeightSpinner().setValue(1024);
                if (view.getPromptStepsSpinner() != null) view.getPromptStepsSpinner().setValue(20);
                if (view.getPromptCfgSpinner() != null) view.getPromptCfgSpinner().setValue(7.0);
            }

            if (view.getPromptImageInputPanel() != null) {
                boolean needsImage = isImageEditBlueprint(matchedEntry);
                view.getPromptImageInputPanel().setVisible(needsImage);
                if (!needsImage) {
                    view.getPromptImageFileField().setText("");
                    view.setSelectedInputImage(null);
                }
                if (view.getPromptLabLeftPanel() != null) {
                    view.getPromptLabLeftPanel().revalidate();
                    view.getPromptLabLeftPanel().repaint();
                }
            }

            BlueprintGalleryTab.BlueprintEntry targetEntry = matchedEntry;
            Runnable fetchTask = () -> {
                try {
                    String jsonContent = null;
                    if (targetEntry.filePath != null) {
                        File localFile = new File(targetEntry.filePath);
                        if (localFile.exists()) {
                            jsonContent = Files.readString(localFile.toPath(), StandardCharsets.UTF_8).trim();
                        }
                    }
                    // 1. Local-First: Check user workflows first, then fallback to shipped workflows folder before attempting remote network calls
                    if (jsonContent == null) {
                        List<File> searchDirs = new ArrayList<>();
                        if (configService != null) {
                            searchDirs.add(configService.getUserWorkflowsDir());
                            searchDirs.add(configService.getShippedWorkflowsDir());
                        } else {
                            searchDirs.add(new File("user_workflows"));
                            searchDirs.add(new File("workflows"));
                        }

                        for (File dir : searchDirs) {
                            if (dir != null && dir.exists() && dir.isDirectory()) {
                                if (targetEntry.registryWorkflow != null) {
                                    if (targetEntry.registryWorkflow.getId() != null) {
                                        File registryFile = new File(dir, targetEntry.registryWorkflow.getId() + ".json");
                                        if (registryFile.exists()) {
                                            jsonContent = Files.readString(registryFile.toPath(), StandardCharsets.UTF_8).trim();
                                            break;
                                        }
                                    }
                                    if (jsonContent == null && targetEntry.registryWorkflow.getTitle() != null) {
                                        String sanitized = targetEntry.registryWorkflow.getTitle().replaceAll("[^a-zA-Z0-9\\-_\\s]", "").trim().replaceAll("\\s+", "_").toLowerCase(java.util.Locale.ROOT);
                                        File titleFile = new File(dir, sanitized + ".json");
                                        if (titleFile.exists()) {
                                            jsonContent = Files.readString(titleFile.toPath(), StandardCharsets.UTF_8).trim();
                                            break;
                                        }
                                    }
                                }
                                if (jsonContent == null) {
                                    File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".json"));
                                    if (files != null) {
                                        for (File f : files) {
                                            if (isBlueprintNameMatch(targetEntry.name, f.getName().replace(".json", ""))
                                                    || (targetEntry.filename != null && f.getName().equalsIgnoreCase(targetEntry.filename))) {
                                                jsonContent = Files.readString(f.toPath(), StandardCharsets.UTF_8).trim();
                                                break;
                                            }
                                        }
                                    }
                                }
                                if (jsonContent != null) {
                                    break;
                                }
                            }
                        }
                    }
                    // 2. Remote Download fallback if not available on disk
                    if (jsonContent == null && targetEntry.registryWorkflow != null && workflowDownloader != null) {
                        try {
                            File downloaded = workflowDownloader.downloadWorkflowAsync(targetEntry.registryWorkflow).get();
                            if (downloaded != null && downloaded.exists()) {
                                jsonContent = Files.readString(downloaded.toPath(), StandardCharsets.UTF_8).trim();
                            }
                        } catch (Exception dlEx) {
                            logger.warn("Failed to download workflow for blueprint " + targetEntry.name + ": " + dlEx.getMessage());
                        }
                    }
                    // 3. Fallback to template generator if workflow cannot be found
                    if (jsonContent == null && comfyTemplateService != null) {
                        ComfyTemplate template = comfyTemplateService.determineTemplateForModel(targetEntry.name);
                        if (template != null) {
                            jsonContent = injectModelsIntoTemplate(template.getContent(), targetEntry.name);
                        }
                    }

                    if (jsonContent != null) {
                        final String finalJson = jsonContent;
                        SwingUtilities.invokeLater(() -> {
                            currentBlueprintGuiJson = finalJson;
                            if (view.getPromptJsonArea() != null) {
                                view.getPromptJsonArea().setText(currentBlueprintGuiJson);
                            }
                            applyModelPreset(targetEntry.name);
                            updatePromptLabJson();
                        });
                    }
                } catch (Exception ex) {
                    logger.error("Failed loading workflow JSON for blueprint " + targetEntry.name, ex);
                }
            };
            if (backgroundExecutor != null) {
                backgroundExecutor.execute(fetchTask);
            } else {
                new Thread(fetchTask).start();
            }
        } else {
            currentBlueprintGuiJson = null;
            if (comfyTemplateService != null) {
                ComfyTemplate template = comfyTemplateService.determineTemplateForModel(selectedName);
                if (template != null) {
                    currentBlueprintGuiJson = injectModelsIntoTemplate(template.getContent(), selectedName);
                    if (view.getPromptJsonArea() != null) view.getPromptJsonArea().setText(currentBlueprintGuiJson);
                }
            }
            applyModelPreset(selectedName);
            updatePromptLabJson();
        }
    }

    private boolean isBlueprintNameMatch(String name1, String name2) {
        if (name1 == null || name2 == null) return false;
        String clean1 = name1.toLowerCase().replaceAll("[^a-z0-9]", "");
        String clean2 = name2.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (clean1.equals(clean2) || clean1.contains(clean2) || clean2.contains(clean1)) return true;

        String[] words1 = name1.toLowerCase().replaceAll("[^a-zA-Z0-9]", " ").trim().split("\\s+");
        String[] words2 = name2.toLowerCase().replaceAll("[^a-zA-Z0-9]", " ").trim().split("\\s+");
        int matchCount = 0;
        for (String w1 : words1) {
            if (w1.length() < 3) continue;
            for (String w2 : words2) {
                if (w1.equals(w2)) {
                    matchCount++;
                    break;
                }
            }
        }
        return matchCount >= Math.min(words1.length, words2.length);
    }

    private boolean isSupportedPromptLabBlueprint(BlueprintGalleryTab.BlueprintEntry e) {
        if (e == null || e.category == null) return false;
        String cat = e.category.toLowerCase();
        String name = e.name != null ? e.name.toLowerCase() : "";
        String desc = e.description != null ? e.description.toLowerCase() : "";

        if (cat.contains("cloud") || name.contains("cloud") || desc.contains("cloud api")
                || name.contains("replicate") || name.contains("fal.ai") || name.contains("dall-e") || name.contains("openai")) {
            return false;
        }

        return cat.contains("text-to-image") || cat.contains("image lab") || cat.contains("flux") || cat.contains("sdxl") || cat.contains("sd 1.5")
                || name.contains("text to image") || name.contains("t2i") || name.contains("sdxl") || name.contains("flux") || name.contains("lumina")
                || name.contains("img2img") || name.contains("i2i") || name.contains("inpaint") || name.contains("image edit");
    }

    private boolean isImageEditBlueprint(BlueprintGalleryTab.BlueprintEntry entry) {
        if (entry == null) return false;
        String cat = entry.category != null ? entry.category.toLowerCase() : "";
        String name = entry.name != null ? entry.name.toLowerCase() : "";
        return cat.contains("img2img") || cat.contains("inpaint") || cat.contains("edit")
                || name.contains("img2img") || name.contains("inpaint") || name.contains("i2i") || name.contains("image edit");
    }

    private boolean isVideoModel(String modelName) {
        if (modelName == null) return false;
        String lower = modelName.toLowerCase();
        return lower.contains("video") || lower.contains("animatediff") || lower.contains("svd") || lower.contains("wan") || lower.contains("cogvideo") || lower.contains("ltx");
    }

    private void scanModelsRecursively(File dir, String prefix, List<String> result) {
        if (!dir.exists() || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                scanModelsRecursively(f, prefix.isEmpty() ? f.getName() : prefix + "/" + f.getName(), result);
            } else {
                String name = f.getName().toLowerCase();
                if (name.endsWith(".safetensors") || name.endsWith(".ckpt") || name.endsWith(".pt")) {
                    String relPath = prefix.isEmpty() ? f.getName() : prefix + "/" + f.getName();
                    if (!result.contains(relPath)) {
                        result.add(relPath);
                    }
                }
            }
        }
    }

    public void refreshPromptLabModels() {
        Runnable task = () -> {
            List<String> itemsToAdd = new ArrayList<>();
            if (blueprintGalleryTab != null) {
                List<BlueprintGalleryTab.BlueprintEntry> readyBlueprints = blueprintGalleryTab.getAvailableBlueprints();
                for (BlueprintGalleryTab.BlueprintEntry e : readyBlueprints) {
                    if (isSupportedPromptLabBlueprint(e)) {
                        if (e.name != null && !e.name.isBlank() && !itemsToAdd.contains(e.name)) {
                            itemsToAdd.add(e.name);
                        }
                    }
                }
            }

            if (itemsToAdd.isEmpty()) {
                String comfyUrl = configService.getComfyUIUrl();
                Set<String> apiModels = new TreeSet<>();
                try {
                    JSONObject info = comfyApiClient.getObjectInfo(comfyUrl);
                    updateComfyModelSets(info);
                    for (String ckpt : comfyCheckpoints) apiModels.add(ckpt.replace("\\", "/"));
                    for (String unet : comfyUnetModels) apiModels.add(unet.replace("\\", "/"));
                } catch (Exception ignored) {}

                for (String m : apiModels) {
                    String mLower = m.toLowerCase();
                    if (!isVideoModel(m) && !mLower.contains("cloud") && !itemsToAdd.contains(m)) {
                        itemsToAdd.add(m);
                    }
                }

                if (itemsToAdd.isEmpty()) {
                    String comfyPath = configService.getComfyUIPath();
                    if (comfyPath != null && !comfyPath.isEmpty()) {
                        File comfyModels = new File(comfyPath, "models");
                        if (comfyModels.exists() && comfyModels.isDirectory()) {
                            File checkpointsDir = new File(comfyModels, "checkpoints");
                            if (checkpointsDir.exists()) scanModelsRecursively(checkpointsDir, "", itemsToAdd);
                            File unetDir = new File(comfyModels, "unet");
                            if (unetDir.exists()) scanModelsRecursively(unetDir, "", itemsToAdd);
                        }
                    }
                }
            }

            itemsToAdd.removeIf(item -> {
                String lower = item.toLowerCase();
                return lower.contains("cloud") || lower.contains("replicate") || lower.contains("fal.ai") || lower.contains("dall-e") || lower.contains("openai");
            });

            SwingUtilities.invokeLater(() -> {
                if (view != null && view.getPromptModelCombo() != null) {
                    isUpdatingCombo.set(true);
                    try {
                        String selected = (String) view.getPromptModelCombo().getSelectedItem();
                        view.getPromptModelCombo().removeAllItems();

                        if (itemsToAdd.isEmpty()) {
                            view.getPromptModelCombo().addItem("No ready offline blueprints available");
                            view.getPromptModelCombo().setEnabled(false);
                        } else {
                            view.getPromptModelCombo().setEnabled(true);
                            for (String name : itemsToAdd) {
                                view.getPromptModelCombo().addItem(name);
                            }

                            if (selected != null && itemsToAdd.contains(selected)) {
                                view.getPromptModelCombo().setSelectedItem(selected);
                            } else {
                                view.getPromptModelCombo().setSelectedIndex(0);
                            }
                        }
                    } finally {
                        isUpdatingCombo.set(false);
                    }

                    String current = (String) view.getPromptModelCombo().getSelectedItem();
                    if (current != null && !current.equals("No ready offline blueprints available")) {
                        onPromptBlueprintSelected(current);
                    }
                }
            });
        };

        if (backgroundExecutor != null) {
            backgroundExecutor.execute(task);
        } else {
            new Thread(task).start();
        }
    }

    public void savePromptLabSession() {
        if (configService == null || view == null) return;
        try {
            JSONObject session = new JSONObject();
            if (view.getPromptEnvCombo() != null) {
                session.put("environment_index", view.getPromptEnvCombo().getSelectedIndex());
            }
            session.put("photorealistic", view.getChkPhotorealistic().isSelected());
            session.put("oil_painting", view.getChkOil().isSelected());
            session.put("unreal_engine", view.getChkEngine().isSelected());
            session.put("anime", view.getChkAnime().isSelected());
            session.put("dark_fantasy", view.getChkFantasy().isSelected());
            session.put("watercolor", view.getChkSketch().isSelected());
            if (view.getPromptModelCombo() != null && view.getPromptModelCombo().getSelectedItem() != null) {
                session.put("model", view.getPromptModelCombo().getSelectedItem());
            }
            if (view.getPromptWidthSpinner() != null) {
                session.put("width", view.getPromptWidthSpinner().getValue());
            }
            if (view.getPromptHeightSpinner() != null) {
                session.put("height", view.getPromptHeightSpinner().getValue());
            }
            if (view.getPromptStepsSpinner() != null) {
                session.put("steps", view.getPromptStepsSpinner().getValue());
            }
            if (view.getPromptCfgSpinner() != null) {
                session.put("cfg", view.getPromptCfgSpinner().getValue());
            }
            configService.savePromptLabSession(session);
        } catch (Exception e) {
            logger.error("Failed to save Prompt Lab session: " + e.getMessage());
        }
    }

    public void loadPromptLabSession() {
        if (configService == null || view == null) return;
        try {
            JSONObject session = configService.getPromptLabSession();
            if (session == null) return;

            if (session.has("environment_index") && view.getPromptEnvCombo() != null) {
                int idx = session.getInt("environment_index");
                if (idx >= 0 && idx < view.getPromptEnvCombo().getItemCount()) {
                    view.getPromptEnvCombo().setSelectedIndex(idx);
                }
            }
            if (session.has("photorealistic")) view.getChkPhotorealistic().setSelected(session.getBoolean("photorealistic"));
            if (session.has("oil_painting")) view.getChkOil().setSelected(session.getBoolean("oil_painting"));
            if (session.has("unreal_engine")) view.getChkEngine().setSelected(session.getBoolean("unreal_engine"));
            if (session.has("anime")) view.getChkAnime().setSelected(session.getBoolean("anime"));
            if (session.has("dark_fantasy")) view.getChkFantasy().setSelected(session.getBoolean("dark_fantasy"));
            if (session.has("watercolor")) view.getChkSketch().setSelected(session.getBoolean("watercolor"));

            if (session.has("model") && view.getPromptModelCombo() != null) {
                String model = session.getString("model");
                boolean found = false;
                for (int i = 0; i < view.getPromptModelCombo().getItemCount(); i++) {
                    if (model.equals(view.getPromptModelCombo().getItemAt(i))) {
                        view.getPromptModelCombo().setSelectedIndex(i);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    view.getPromptModelCombo().addItem(model);
                    view.getPromptModelCombo().setSelectedItem(model);
                }
            }
            if (session.has("width") && view.getPromptWidthSpinner() != null) {
                Object wVal = session.get("width");
                if (wVal instanceof Number) {
                    view.getPromptWidthSpinner().setValue(((Number) wVal).intValue());
                }
            }
            if (session.has("height") && view.getPromptHeightSpinner() != null) {
                Object hVal = session.get("height");
                if (hVal instanceof Number) {
                    view.getPromptHeightSpinner().setValue(((Number) hVal).intValue());
                }
            }
            if (session.has("steps") && view.getPromptStepsSpinner() != null) {
                Object stepsVal = session.get("steps");
                if (stepsVal instanceof Number) {
                    view.getPromptStepsSpinner().setValue(((Number) stepsVal).intValue());
                }
            }
            if (session.has("cfg") && view.getPromptCfgSpinner() != null) {
                Object cfgVal = session.get("cfg");
                if (cfgVal instanceof Number) {
                    view.getPromptCfgSpinner().setValue(((Number) cfgVal).doubleValue());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to load Prompt Lab session: " + e.getMessage());
        }
    }
}
