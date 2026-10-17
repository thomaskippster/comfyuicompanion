package de.tki.comfyuicompanion.controller;

import de.tki.comfyuicompanion.domain.ComfyTemplate;
import de.tki.comfyuicompanion.service.IComfyTemplateService;
import de.tki.comfyuicompanion.service.IModelArchitectureService;
import de.tki.comfyuicompanion.ui.PromptLabView;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
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
    private final WorkflowGraphParameterExtractor parameterExtractor;

    /**
     * Constructs a new PromptLabPresetManager.
     *
     * @param modelArchitectureService Service to detect model architecture.
     * @param comfyTemplateService     Service to resolve workflow templates based on model architecture.
     * @param modelResolver            Resolver used to inject model paths into templates.
     */
    public PromptLabPresetManager(IModelArchitectureService modelArchitectureService,
                                  IComfyTemplateService comfyTemplateService,
                                  PromptLabModelResolver modelResolver) {
        this.modelArchitectureService = modelArchitectureService;
        this.comfyTemplateService = comfyTemplateService;
        this.modelResolver = modelResolver;
        this.parameterExtractor = new WorkflowGraphParameterExtractor();
    }

    /**
     * Applies default settings to the Prompt Lab UI based on the selected model.
     * Injects the model into the workflow template if provided.
     *
     * @param view                    The UI view containing the input controls.
     * @param modelNameRaw            The raw model name selected (may contain path separators).
     * @param modelName               The clean model name.
     * @param currentBlueprintGuiJson The current workflow JSON string, if any.
     * @param onJsonUpdated           Callback to handle the updated injected workflow JSON.
     */
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

    /**
     * Parses the workflow JSON to extract parameters (e.g. width, height, steps) and populates the UI.
     *
     * @param view    The PromptLabView to update.
     * @param jsonStr The JSON string representing the workflow.
     */
    public void populateUiFromWorkflow(PromptLabView view, String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty() || view == null) return;
        var p = parameterExtractor.extractFromWorkflow(jsonStr);

        if (p.width() != null && p.width() > 0 && view.getPromptWidthSpinner() != null) {
            view.getPromptWidthSpinner().setValue(p.width());
        }
        if (p.height() != null && p.height() > 0 && view.getPromptHeightSpinner() != null) {
            view.getPromptHeightSpinner().setValue(p.height());
        }
        if (p.batch() != null && p.batch() > 0 && view.getPromptBatchSizeSpinner() != null) {
            view.getPromptBatchSizeSpinner().setValue(p.batch());
        }
        if (p.steps() != null && p.steps() > 0 && view.getPromptStepsSpinner() != null) {
            view.getPromptStepsSpinner().setValue(p.steps());
        }
        if (p.cfg() != null && p.cfg() >= 0 && view.getPromptCfgSpinner() != null) {
            view.getPromptCfgSpinner().setValue(p.cfg());
        }
        if (p.denoise() != null && p.denoise() >= 0.0 && p.denoise() <= 1.0 && view.getPromptDenoiseSpinner() != null) {
            view.getPromptDenoiseSpinner().setValue(p.denoise());
        }
        if (p.sampler() != null && !p.sampler().isBlank() && view.getPromptSamplerCombo() != null) {
            selectOrAddComboItem(view.getPromptSamplerCombo(), p.sampler());
        }
        if (p.scheduler() != null && !p.scheduler().isBlank() && view.getPromptSchedulerCombo() != null) {
            selectOrAddComboItem(view.getPromptSchedulerCombo(), p.scheduler());
        }
    }

    /**
     * Collects all nodes from a workflow graph, including nodes inside subgraphs/groups.
     *
     * @param root The root JSONObject representing the workflow.
     * @return A list of all JSON objects representing nodes.
     */
    public List<JSONObject> collectAllNodes(JSONObject root) {
        return parameterExtractor.collectAllNodes(root);
    }

    /**
     * Selects an item in a JComboBox if it exists, otherwise adds it and then selects it.
     *
     * @param combo  The JComboBox to manipulate.
     * @param target The target string item to select or add.
     */
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
