package de.tki.comfyuicompanion.controller;

import de.tki.comfyuicompanion.service.IComfyTemplateService;
import de.tki.comfyuicompanion.service.IModelArchitectureService;
import de.tki.comfyuicompanion.service.IWorkflowDownloader;
import de.tki.comfyuicompanion.service.PromptBlueprintApiService;
import de.tki.comfyuicompanion.service.impl.ComfyApiClient;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab;
import de.tki.comfyuicompanion.ui.PromptLabView;
import de.tki.comfyuicompanion.util.BackgroundExecutor;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.swing.JComboBox;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Controller component for managing Prompt Lab UI interactions, coordinating blueprint
 * selections, prompt synchronization, model resolution, and session persistence.
 */
@Component
public class PromptLabController implements PromptLabView.PromptLabController {

    private static final Logger logger = LoggerFactory.getLogger(PromptLabController.class);

    private final ComfyApiClient comfyApiClient;
    private final PromptLabModelResolver modelResolver;
    private final PromptLabPresetManager presetManager;
    private final PromptLabSessionManager sessionManager;
    private final PromptLabBlueprintCoordinator blueprintCoordinator;
    private final PromptLabJsonSynchronizer jsonSynchronizer;
    private final de.tki.comfyuicompanion.service.IPromptHistoryService promptHistoryService;

    private volatile String currentBlueprintGuiJson;
    private final AtomicBoolean isUpdatingCombo = new AtomicBoolean(false);
    private PromptLabView view;
    private BlueprintGalleryTab blueprintGalleryTab;
    private Runnable sendPromptHandler;
    private Runnable suggestSubjectHandler;

    /**
     * Default constructor for standalone initialization and testing.
     */
    public PromptLabController() {
        this.comfyApiClient = null;
        this.modelResolver = new PromptLabModelResolver(null);
        this.presetManager = new PromptLabPresetManager(null, null, this.modelResolver);
        this.sessionManager = new PromptLabSessionManager(null);
        this.blueprintCoordinator = new PromptLabBlueprintCoordinator(null, null, null, null, this.modelResolver);
        this.jsonSynchronizer = new PromptLabJsonSynchronizer(new PromptBlueprintApiService());
        this.promptHistoryService = new de.tki.comfyuicompanion.service.impl.PromptHistoryService((ConfigService) null);
    }

    /**
     * Spring autowired constructor with dependent services and executors.
     */
    @Autowired
    public PromptLabController(ConfigService configService,
                               PromptBlueprintApiService promptBlueprintApiService,
                               ComfyApiClient comfyApiClient,
                               @Autowired(required = false) IComfyTemplateService comfyTemplateService,
                               @Autowired(required = false) IModelArchitectureService modelArchitectureService,
                               @Autowired(required = false) IWorkflowDownloader workflowDownloader,
                               @Autowired(required = false) BackgroundExecutor backgroundExecutor,
                               @Autowired(required = false) de.tki.comfyuicompanion.service.IPromptHistoryService promptHistoryService) {
        this.comfyApiClient = comfyApiClient;
        this.modelResolver = new PromptLabModelResolver(modelArchitectureService);
        this.presetManager = new PromptLabPresetManager(modelArchitectureService, comfyTemplateService, this.modelResolver);
        this.sessionManager = new PromptLabSessionManager(configService);
        this.blueprintCoordinator = new PromptLabBlueprintCoordinator(configService, comfyTemplateService,
                workflowDownloader, backgroundExecutor, this.modelResolver);
        this.jsonSynchronizer = new PromptLabJsonSynchronizer(promptBlueprintApiService);
        this.promptHistoryService = promptHistoryService != null ? promptHistoryService : new de.tki.comfyuicompanion.service.impl.PromptHistoryService(configService);
    }

    /** Sets the PromptLabView instance to bind. */
    public void setView(PromptLabView view) {
        this.view = view;
        if (this.view != null) {
            this.view.setController(this);
        }
    }

    /** Sets the blueprint gallery tab component. */
    public void setBlueprintGalleryTab(BlueprintGalleryTab blueprintGalleryTab) {
        this.blueprintGalleryTab = blueprintGalleryTab;
    }

    /** Sets the handler invoked when sending a prompt to ComfyUI. */
    public void setSendPromptHandler(Runnable sendPromptHandler) {
        this.sendPromptHandler = sendPromptHandler;
    }

    /** Sets the handler invoked when requesting subject suggestions. */
    public void setSuggestSubjectHandler(Runnable suggestSubjectHandler) {
        this.suggestSubjectHandler = suggestSubjectHandler;
    }

    @Override
    public void onRefreshModels() {
        refreshPromptLabModels();
    }

    @Override
    public void onSuggestSubject() {
        if (suggestSubjectHandler != null) suggestSubjectHandler.run();
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
        recordCurrentPromptHistory();
        if (sendPromptHandler != null) sendPromptHandler.run();
    }

    @Override
    public void openPromptHistoryDialog(java.awt.Window parentWindow) {
        if (promptHistoryService != null) {
            de.tki.comfyuicompanion.ui.dialog.PromptHistoryDialog dialog =
                    new de.tki.comfyuicompanion.ui.dialog.PromptHistoryDialog(parentWindow, promptHistoryService, this::recallPrompt);
            dialog.setVisible(true);
        }
    }

    /**
     * Records the active prompt and sampling parameters into the persistent history service.
     */
    public void recordCurrentPromptHistory() {
        if (promptHistoryService == null || view == null) return;
        try {
            String pos = view.getPromptSubjectField() != null ? view.getPromptSubjectField().getText() : "";
            String neg = view.getPromptNegativeField() != null ? view.getPromptNegativeField().getText() : "";
            String model = (view.getPromptModelCombo() != null && view.getPromptModelCombo().getSelectedItem() != null)
                    ? view.getPromptModelCombo().getSelectedItem().toString() : "";
            int w = view.getPromptWidthSpinner() != null ? ((Number) view.getPromptWidthSpinner().getValue()).intValue() : 512;
            int h = view.getPromptHeightSpinner() != null ? ((Number) view.getPromptHeightSpinner().getValue()).intValue() : 512;
            int steps = view.getPromptStepsSpinner() != null ? ((Number) view.getPromptStepsSpinner().getValue()).intValue() : 20;
            double cfg = view.getPromptCfgSpinner() != null ? ((Number) view.getPromptCfgSpinner().getValue()).doubleValue() : 7.0;
            String sampler = (view.getPromptSamplerCombo() != null && view.getPromptSamplerCombo().getSelectedItem() != null)
                    ? view.getPromptSamplerCombo().getSelectedItem().toString() : "euler";
            String scheduler = (view.getPromptSchedulerCombo() != null && view.getPromptSchedulerCombo().getSelectedItem() != null)
                    ? view.getPromptSchedulerCombo().getSelectedItem().toString() : "normal";

            promptHistoryService.recordPrompt(new de.tki.comfyuicompanion.domain.PromptHistoryEntry(
                    System.currentTimeMillis(), pos, neg, model, w, h, steps, cfg, sampler, scheduler
            ));
        } catch (Exception e) {
            logger.warn("Failed to record prompt history: {}", e.getMessage());
        }
    }

    /**
     * Restores an earlier prompt snapshot from history into the UI controls and updates JSON.
     *
     * @param entry the history snapshot to restore
     */
    public void recallPrompt(de.tki.comfyuicompanion.domain.PromptHistoryEntry entry) {
        if (entry == null || view == null) return;
        if (view.getPromptSubjectField() != null && entry.positivePrompt() != null) {
            view.getPromptSubjectField().setText(entry.positivePrompt());
        }
        if (view.getPromptNegativeField() != null && entry.negativePrompt() != null) {
            view.getPromptNegativeField().setText(entry.negativePrompt());
        }
        if (view.getPromptWidthSpinner() != null && entry.width() > 0) {
            view.getPromptWidthSpinner().setValue(entry.width());
        }
        if (view.getPromptHeightSpinner() != null && entry.height() > 0) {
            view.getPromptHeightSpinner().setValue(entry.height());
        }
        if (view.getPromptStepsSpinner() != null && entry.steps() > 0) {
            view.getPromptStepsSpinner().setValue(entry.steps());
        }
        if (view.getPromptCfgSpinner() != null && entry.cfg() > 0) {
            view.getPromptCfgSpinner().setValue(entry.cfg());
        }
        if (view.getPromptSamplerCombo() != null && entry.sampler() != null) {
            view.getPromptSamplerCombo().setSelectedItem(entry.sampler());
        }
        if (view.getPromptSchedulerCombo() != null && entry.scheduler() != null) {
            view.getPromptSchedulerCombo().setSelectedItem(entry.scheduler());
        }
        if (entry.model() != null && !entry.model().isBlank() && view.getPromptModelCombo() != null) {
            for (int i = 0; i < view.getPromptModelCombo().getItemCount(); i++) {
                if (entry.model().equals(view.getPromptModelCombo().getItemAt(i))) {
                    view.getPromptModelCombo().setSelectedIndex(i);
                    break;
                }
            }
        }
        updatePromptLabJson();
    }

    /** @return the current blueprint GUI JSON string */
    public String getCurrentBlueprintGuiJson() { return currentBlueprintGuiJson; }

    /** @param currentBlueprintGuiJson the blueprint GUI JSON string to set */
    public void setCurrentBlueprintGuiJson(String currentBlueprintGuiJson) { this.currentBlueprintGuiJson = currentBlueprintGuiJson; }

    /** @return the set of available Comfy checkpoints */
    public Set<String> getComfyCheckpoints() { return modelResolver.getComfyCheckpoints(); }
    /** @return the set of available Comfy UNET models */
    public Set<String> getComfyUnetModels() { return modelResolver.getComfyUnetModels(); }
    /** @return the set of available Comfy CLIP models */
    public Set<String> getComfyClips() { return modelResolver.getComfyClips(); }
    /** @return the set of available Comfy VAE models */
    public Set<String> getComfyVaes() { return modelResolver.getComfyVaes(); }
    /** @return the set of available Comfy CLIP types */
    public Set<String> getComfyClipTypes() { return modelResolver.getComfyClipTypes(); }
    /** @return the set of available Comfy UNET weight data types */
    public Set<String> getComfyUnetWeightDtypes() { return modelResolver.getComfyUnetWeightDtypes(); }

    /** Applies default settings to the Prompt Lab UI based on the selected model. */
    public void applyModelPreset(String modelNameRaw) {
        presetManager.applyModelPreset(view, modelNameRaw, getActualModelForPromptLab(modelNameRaw),
                currentBlueprintGuiJson, s -> this.currentBlueprintGuiJson = s);
    }

    /** Injects the selected model parameters into the workflow template JSON. */
    public String injectModelsIntoTemplate(String templateJson, String modelName) {
        return modelResolver.injectModelsIntoTemplate(templateJson, modelName);
    }

    /** Selects an item in a JComboBox if it exists, otherwise adds it and then selects it. */
    public void selectOrAddComboItem(JComboBox<String> combo, String target) {
        presetManager.selectOrAddComboItem(combo, target);
    }

    /** Collects all nodes from a workflow graph. */
    public List<JSONObject> collectAllNodes(JSONObject root) {
        return presetManager.collectAllNodes(root);
    }

    /** Parses the workflow JSON to extract parameters and populates the UI. */
    public void populateUiFromWorkflow(String jsonStr) {
        presetManager.populateUiFromWorkflow(view, jsonStr);
    }

    /** Finds the exact UNET model name matching the given selected model. */
    public String findExactUnetName(String selectedModel) {
        return modelResolver.findExactUnetName(selectedModel);
    }

    /** Finds the exact checkpoint name matching the given selected model. */
    public String findExactCheckpointName(String selectedModel) {
        return modelResolver.findExactCheckpointName(selectedModel);
    }

    /** Resolves the appropriate CLIP type string based on the given clip model and selected model. */
    public String resolveClipType(String clipModel, String selectedModel) {
        return modelResolver.resolveClipType(clipModel, selectedModel);
    }

    /** Checks whether two model names refer to the same logical model file. */
    public boolean modelsMatch(String modelA, String modelB) {
        return modelResolver.modelsMatch(modelA, modelB);
    }

    /** Resolves the appropriate CLIP model filename for the given selected model. */
    public String resolveClipForModel(String selectedModel) {
        return modelResolver.resolveClipForModel(selectedModel);
    }

    /** Resolves the appropriate VAE model filename for the given selected model. */
    public String resolveVaeForModel(String modelName) {
        return modelResolver.resolveVaeForModel(modelName);
    }

    /** Determines if a model is a standard diffusion model based on its name. */
    public boolean isDiffusionModel(String modelName) {
        return modelResolver.isDiffusionModel(modelName);
    }

    /** Updates the internal sets of available models by parsing the ComfyUI object info. */
    public void updateComfyModelSets(JSONObject info) {
        modelResolver.updateComfyModelSets(info);
    }

    /** Derives the actual model name required by the blueprint from the combo box selection. */
    public String getActualModelForPromptLab(String comboSelection) {
        return blueprintCoordinator.getActualModelForPromptLab(comboSelection, blueprintGalleryTab);
    }

    /** Finds the blueprint name corresponding to a given model name. */
    public String getBlueprintNameForModel(String modelName) {
        return blueprintCoordinator.getBlueprintNameForModel(modelName, blueprintGalleryTab);
    }

    /** Synchronizes current UI parameters into the prompt JSON area and saves the session. */
    public void updatePromptLabJson() {
        jsonSynchronizer.updatePromptLabJson(view);
        savePromptLabSession();
    }

    /** Handles selection of a blueprint preset from the UI combo box. */
    public void onPromptBlueprintSelected(String selectedName) {
        blueprintCoordinator.onPromptBlueprintSelected(
                selectedName,
                view,
                blueprintGalleryTab,
                isUpdatingCombo,
                json -> this.currentBlueprintGuiJson = json,
                this::applyModelPreset,
                this::updatePromptLabJson
        );
    }

    /** Refreshes the models available for selection in the Prompt Lab combo box. */
    public void refreshPromptLabModels() {
        blueprintCoordinator.refreshPromptLabModels(view, blueprintGalleryTab, comfyApiClient, isUpdatingCombo,
                this::onPromptBlueprintSelected);
    }

    /** Persists the current state of the Prompt Lab UI to settings. */
    public void savePromptLabSession() {
        sessionManager.saveSession(view);
    }

    /**
     * Executes the given UI action while suppressing combo box selection listeners
     * so programmatic model updates (such as PNG Remix) do not reload blueprint templates.
     *
     * @param action the UI runnable to execute
     */
    public void runWithComboUpdateSuppressed(Runnable action) {
        if (action == null) return;
        boolean prev = isUpdatingCombo.getAndSet(true);
        try {
            action.run();
        } finally {
            isUpdatingCombo.set(prev);
        }
    }

    /** Restores the Prompt Lab UI state from persisted settings. */
    public void loadPromptLabSession() {
        sessionManager.loadSession(view);
    }
}
