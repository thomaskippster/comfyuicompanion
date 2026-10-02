package de.tki.comfyuicompanion.controller;

import de.tki.comfyuicompanion.service.PromptBlueprintApiService;
import de.tki.comfyuicompanion.service.impl.ComfyPipelineService;
import de.tki.comfyuicompanion.ui.PromptLabView;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Synchronizes UI controls from the Prompt Lab view into the ComfyUI workflow API JSON.
 * Assembles prompt text and injects generation parameters (dimensions, steps, CFG, sampler,
 * scheduler, denoise, batch size, input image) into the JSON payload.
 */
public class PromptLabJsonSynchronizer {

    private static final Logger logger = LoggerFactory.getLogger(PromptLabJsonSynchronizer.class);

    private final PromptBlueprintApiService promptBlueprintApiService;

    /**
     * Constructs a new PromptLabJsonSynchronizer with the required blueprint API service.
     *
     * @param promptBlueprintApiService service used to inject lab inputs into API JSON payloads
     */
    public PromptLabJsonSynchronizer(PromptBlueprintApiService promptBlueprintApiService) {
        this.promptBlueprintApiService = promptBlueprintApiService != null
                ? promptBlueprintApiService
                : new PromptBlueprintApiService();
    }

    /**
     * Reads UI parameters from the given view, updates the prompt assemble area,
     * injects generation parameters into the workflow JSON, and updates the prompt JSON area.
     *
     * @param view the PromptLabView containing input controls and display areas
     */
    public void updatePromptLabJson(PromptLabView view) {
        if (view == null) {
            return;
        }
        if (view.getPromptSubjectField() == null
                || view.getPromptAssembleArea() == null
                || view.getPromptJsonArea() == null) {
            return;
        }

        String subject = view.getPromptSubjectField().getText().trim();
        String negative = (view.getPromptNegativeField() != null)
                ? view.getPromptNegativeField().getText().trim()
                : "";

        view.getPromptAssembleArea().setText(subject);

        try {
            String currentJsonStr = view.getPromptJsonArea().getText();
            if (currentJsonStr == null || currentJsonStr.isBlank()) {
                return;
            }

            JSONObject mainObj = new JSONObject(currentJsonStr);
            mainObj = ensureApiFormat(mainObj);
            if (mainObj == null || !mainObj.has("prompt")) {
                return;
            }

            JSONObject promptObj = mainObj.getJSONObject("prompt");
            PromptBlueprintApiService.PromptLabInputs previewInputs = extractInputs(view, subject, negative);
            promptBlueprintApiService.injectLabInputs(promptObj, previewInputs);

            view.getPromptJsonArea().setText(mainObj.toString(2));
        } catch (Exception ex) {
            // Ignore parse errors from user manual edits while typing in the JSON area
            logger.debug("Failed to synchronize Prompt Lab JSON: {}", ex.getMessage());
        }
    }

    /**
     * Ensures that the provided workflow JSON object is in API prompt format,
     * flattening and converting UI node graphs if necessary.
     *
     * @param root the workflow root JSON object
     * @return the API-formatted JSONObject containing a "prompt" property, or null if invalid
     */
    public JSONObject ensureApiFormat(JSONObject root) {
        if (root == null) {
            return null;
        }
        if (root.has("prompt")) {
            return root;
        }
        if (root.has("nodes")) {
            JSONObject flattened = ComfyPipelineService.flattenWorkflow(root);
            return ComfyPipelineService.convertUiToApi(flattened);
        }
        return null;
    }

    /**
     * Extracts generation parameters from the UI controls into a {@link PromptBlueprintApiService.PromptLabInputs} record.
     *
     * @param view     the PromptLabView containing input controls
     * @param subject  the positive prompt text
     * @param negative the negative prompt text
     * @return the assembled inputs record
     */
    public PromptBlueprintApiService.PromptLabInputs extractInputs(PromptLabView view, String subject, String negative) {
        int width = (view.getPromptWidthSpinner() != null) ? (Integer) view.getPromptWidthSpinner().getValue() : 1024;
        int height = (view.getPromptHeightSpinner() != null) ? (Integer) view.getPromptHeightSpinner().getValue() : 1024;
        int batch = (view.getPromptBatchSizeSpinner() != null) ? (Integer) view.getPromptBatchSizeSpinner().getValue() : 1;
        int steps = (view.getPromptStepsSpinner() != null) ? (Integer) view.getPromptStepsSpinner().getValue() : 20;
        double cfg = (view.getPromptCfgSpinner() != null) ? ((Number) view.getPromptCfgSpinner().getValue()).doubleValue() : 7.0;
        double denoise = (view.getPromptDenoiseSpinner() != null) ? ((Number) view.getPromptDenoiseSpinner().getValue()).doubleValue() : 1.0;
        String samplerName = (view.getPromptSamplerCombo() != null && view.getPromptSamplerCombo().getSelectedItem() != null)
                ? (String) view.getPromptSamplerCombo().getSelectedItem()
                : "Auto";
        String scheduler = (view.getPromptSchedulerCombo() != null && view.getPromptSchedulerCombo().getSelectedItem() != null)
                ? (String) view.getPromptSchedulerCombo().getSelectedItem()
                : "Auto";
        String inputImage = (view.getPromptImageFileField() != null) ? view.getPromptImageFileField().getText() : null;

        return new PromptBlueprintApiService.PromptLabInputs(
                subject, negative, width, height, steps, cfg, 0L, samplerName, scheduler, denoise, batch, inputImage
        );
    }
}
