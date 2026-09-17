package de.tki.comfyuicompanion.controller;

import de.tki.comfyuicompanion.domain.ComfyTemplate;
import de.tki.comfyuicompanion.service.IComfyTemplateService;
import de.tki.comfyuicompanion.service.IModelArchitectureService;
import de.tki.comfyuicompanion.service.IWorkflowDownloader;
import de.tki.comfyuicompanion.service.PromptBlueprintApiService;
import de.tki.comfyuicompanion.service.impl.ComfyApiClient;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab;
import de.tki.comfyuicompanion.ui.PromptLabView;
import de.tki.comfyuicompanion.util.BackgroundExecutor;

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

    private final PromptLabModelResolver modelResolver;
    private final PromptLabPresetManager presetManager;
    private final PromptLabSessionManager sessionManager;

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
        this.modelResolver = new PromptLabModelResolver(null);
        this.presetManager = new PromptLabPresetManager(null, null, this.modelResolver);
        this.sessionManager = new PromptLabSessionManager(null);
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
        this.modelResolver = new PromptLabModelResolver(modelArchitectureService);
        this.presetManager = new PromptLabPresetManager(modelArchitectureService, comfyTemplateService, this.modelResolver);
        this.sessionManager = new PromptLabSessionManager(configService);
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

    public Set<String> getComfyCheckpoints() { return modelResolver.getComfyCheckpoints(); }
    public Set<String> getComfyUnetModels() { return modelResolver.getComfyUnetModels(); }
    public Set<String> getComfyClips() { return modelResolver.getComfyClips(); }
    public Set<String> getComfyVaes() { return modelResolver.getComfyVaes(); }
    public Set<String> getComfyClipTypes() { return modelResolver.getComfyClipTypes(); }
    public Set<String> getComfyUnetWeightDtypes() { return modelResolver.getComfyUnetWeightDtypes(); }

    public void applyModelPreset(String modelNameRaw) {
        presetManager.applyModelPreset(view, modelNameRaw, getActualModelForPromptLab(modelNameRaw),
                currentBlueprintGuiJson, s -> this.currentBlueprintGuiJson = s);
    }

    public String injectModelsIntoTemplate(String templateJson, String modelName) {
        return modelResolver.injectModelsIntoTemplate(templateJson, modelName);
    }

    public void selectOrAddComboItem(JComboBox<String> combo, String target) {
        presetManager.selectOrAddComboItem(combo, target);
    }

    public List<JSONObject> collectAllNodes(JSONObject root) {
        return presetManager.collectAllNodes(root);
    }

    public void populateUiFromWorkflow(String jsonStr) {
        presetManager.populateUiFromWorkflow(view, jsonStr);
    }

    public String findExactUnetName(String selectedModel) {
        return modelResolver.findExactUnetName(selectedModel);
    }

    public String findExactCheckpointName(String selectedModel) {
        return modelResolver.findExactCheckpointName(selectedModel);
    }

    public String resolveClipType(String clipModel, String selectedModel) {
        return modelResolver.resolveClipType(clipModel, selectedModel);
    }

    public boolean modelsMatch(String modelA, String modelB) {
        return modelResolver.modelsMatch(modelA, modelB);
    }

    public String resolveClipForModel(String selectedModel) {
        return modelResolver.resolveClipForModel(selectedModel);
    }

    public String resolveVaeForModel(String modelName) {
        return modelResolver.resolveVaeForModel(modelName);
    }

    public boolean isDiffusionModel(String modelName) {
        return modelResolver.isDiffusionModel(modelName);
    }

    public void updateComfyModelSets(JSONObject info) {
        modelResolver.updateComfyModelSets(info);
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
                    for (String ckpt : getComfyCheckpoints()) apiModels.add(ckpt.replace("\\", "/"));
                    for (String unet : getComfyUnetModels()) apiModels.add(unet.replace("\\", "/"));
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
        sessionManager.saveSession(view);
    }

    public void loadPromptLabSession() {
        sessionManager.loadSession(view);
    }
}
