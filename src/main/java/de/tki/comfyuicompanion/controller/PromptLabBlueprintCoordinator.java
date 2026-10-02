package de.tki.comfyuicompanion.controller;

import de.tki.comfyuicompanion.domain.ComfyTemplate;
import de.tki.comfyuicompanion.service.IComfyTemplateService;
import de.tki.comfyuicompanion.service.IWorkflowDownloader;
import de.tki.comfyuicompanion.service.impl.ComfyApiClient;
import de.tki.comfyuicompanion.service.impl.ComfyPipelineService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab;
import de.tki.comfyuicompanion.ui.PromptLabView;
import de.tki.comfyuicompanion.util.BackgroundExecutor;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Coordinates blueprint presets, architecture defaults, workflow file resolution,
 * and model name mapping for Prompt Lab.
 */
public class PromptLabBlueprintCoordinator {

    private static final Logger logger = LoggerFactory.getLogger(PromptLabBlueprintCoordinator.class);

    private final ConfigService configService;
    private final IComfyTemplateService comfyTemplateService;
    private final IWorkflowDownloader workflowDownloader;
    private final BackgroundExecutor backgroundExecutor;
    private final PromptLabModelResolver modelResolver;

    /**
     * Constructs a new PromptLabBlueprintCoordinator with the required services and resolvers.
     *
     * @param configService        configuration service for file paths and ComfyUI URLs
     * @param comfyTemplateService service to determine workflow templates for models
     * @param workflowDownloader   service to download remote workflow files when needed
     * @param backgroundExecutor   executor for running asynchronous fetch tasks
     * @param modelResolver        resolver for model injection and checkpoint/unet sets
     */
    public PromptLabBlueprintCoordinator(ConfigService configService,
                                         IComfyTemplateService comfyTemplateService,
                                         IWorkflowDownloader workflowDownloader,
                                         BackgroundExecutor backgroundExecutor,
                                         PromptLabModelResolver modelResolver) {
        this.configService = configService;
        this.comfyTemplateService = comfyTemplateService;
        this.workflowDownloader = workflowDownloader;
        this.backgroundExecutor = backgroundExecutor;
        this.modelResolver = modelResolver;
    }

    /**
     * Handles selection of a blueprint by matching the selected name against available blueprints,
     * applying architectural dimension and sampling defaults, loading workflow JSON, and triggering UI updates.
     *
     * @param selectedName             the selected blueprint or model name
     * @param view                     the PromptLabView UI instance
     * @param blueprintGalleryTab      the gallery tab providing available blueprint entries
     * @param isUpdatingCombo          guard flag to prevent re-entrant combo updates
     * @param blueprintJsonConsumer    callback to record the active blueprint GUI JSON
     * @param applyModelPresetCallback callback to apply model preset configurations
     * @param updateJsonCallback       callback to synchronize Prompt Lab JSON
     */
    public void onPromptBlueprintSelected(String selectedName,
                                          PromptLabView view,
                                          BlueprintGalleryTab blueprintGalleryTab,
                                          AtomicBoolean isUpdatingCombo,
                                          Consumer<String> blueprintJsonConsumer,
                                          Consumer<String> applyModelPresetCallback,
                                          Runnable updateJsonCallback) {
        if (selectedName == null || selectedName.isEmpty() || view == null) {
            return;
        }
        if (isUpdatingCombo != null && isUpdatingCombo.get()) {
            return;
        }

        BlueprintGalleryTab.BlueprintEntry matchedEntry = findBlueprintMatch(blueprintGalleryTab, selectedName);

        if (matchedEntry != null) {
            if (view.getPromptPresetLabel() != null) {
                view.getPromptPresetLabel().setText("Selected Blueprint: " + matchedEntry.name + " (" + matchedEntry.category + ")");
            }

            applyDimensionDefaults(view, matchedEntry);

            BlueprintGalleryTab.BlueprintEntry targetEntry = matchedEntry;
            Runnable fetchTask = () -> {
                try {
                    String jsonContent = resolveBlueprintWorkflowJson(targetEntry);
                    if (jsonContent != null) {
                        String preparedJson = convertUiToApiIfNeeded(jsonContent);
                        final String finalJson = preparedJson;
                        SwingUtilities.invokeLater(() -> {
                            if (blueprintJsonConsumer != null) {
                                blueprintJsonConsumer.accept(finalJson);
                            }
                            if (view.getPromptJsonArea() != null) {
                                view.getPromptJsonArea().setText(finalJson);
                            }
                            if (applyModelPresetCallback != null) {
                                applyModelPresetCallback.accept(targetEntry.name);
                            }
                            if (updateJsonCallback != null) {
                                updateJsonCallback.run();
                            }
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
            String fallbackJson = null;
            if (comfyTemplateService != null && modelResolver != null) {
                ComfyTemplate template = comfyTemplateService.determineTemplateForModel(selectedName);
                if (template != null) {
                    fallbackJson = modelResolver.injectModelsIntoTemplate(template.getContent(), selectedName);
                    if (view.getPromptJsonArea() != null) {
                        view.getPromptJsonArea().setText(fallbackJson);
                    }
                }
            }
            if (blueprintJsonConsumer != null) {
                blueprintJsonConsumer.accept(fallbackJson);
            }
            if (applyModelPresetCallback != null) {
                applyModelPresetCallback.accept(selectedName);
            }
            if (updateJsonCallback != null) {
                updateJsonCallback.run();
            }
        }
    }

    /**
     * Resolves the workflow JSON content for a target blueprint from local disks,
     * remote registry download, or template generation.
     *
     * @param targetEntry the blueprint entry to resolve
     * @return the resolved JSON content string, or null if unresolvable
     */
    public String resolveBlueprintWorkflowJson(BlueprintGalleryTab.BlueprintEntry targetEntry) {
        if (targetEntry == null) {
            return null;
        }
        try {
            String jsonContent = null;
            if (targetEntry.filePath != null) {
                File localFile = new File(targetEntry.filePath);
                if (localFile.exists()) {
                    jsonContent = Files.readString(localFile.toPath(), StandardCharsets.UTF_8).trim();
                }
            }

            // 1. Local-First: Check user workflows first, then fallback to shipped workflows folder
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
                                String sanitized = targetEntry.registryWorkflow.getTitle()
                                        .replaceAll("[^a-zA-Z0-9\\-_\\s]", "")
                                        .trim()
                                        .replaceAll("\\s+", "_")
                                        .toLowerCase(Locale.ROOT);
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
                    logger.warn("Failed to download workflow for blueprint {}: {}", targetEntry.name, dlEx.getMessage());
                }
            }

            // 3. Fallback to template generator if workflow cannot be found
            if (jsonContent == null && comfyTemplateService != null && modelResolver != null) {
                ComfyTemplate template = comfyTemplateService.determineTemplateForModel(targetEntry.name);
                if (template != null) {
                    jsonContent = modelResolver.injectModelsIntoTemplate(template.getContent(), targetEntry.name);
                }
            }

            return jsonContent;
        } catch (Exception ex) {
            logger.error("Error resolving workflow JSON for blueprint: " + targetEntry.name, ex);
            return null;
        }
    }

    /**
     * Converts a UI-formatted workflow JSON graph to API prompt format if nodes are present.
     *
     * @param jsonContent the raw workflow JSON string
     * @return the API-formatted JSON string or original string if already formatted
     */
    public String convertUiToApiIfNeeded(String jsonContent) {
        if (jsonContent == null) {
            return null;
        }
        try {
            JSONObject rawObj = new JSONObject(jsonContent);
            if (rawObj.has("nodes")) {
                JSONObject flattened = ComfyPipelineService.flattenWorkflow(rawObj);
                JSONObject apiPayload = ComfyPipelineService.convertUiToApi(flattened);
                return apiPayload.toString(2);
            }
        } catch (Exception ignored) {
            // Keep raw content if JSON parse fails
        }
        return jsonContent;
    }

    /**
     * Applies default resolution, sampling steps, and CFG values according to the blueprint architecture.
     *
     * @param view  the PromptLabView instance
     * @param entry the matched blueprint entry
     */
    public void applyDimensionDefaults(PromptLabView view, BlueprintGalleryTab.BlueprintEntry entry) {
        if (view == null || entry == null) {
            return;
        }
        String nameLower = entry.name != null ? entry.name.toLowerCase(Locale.ROOT) : "";
        String catLower = entry.category != null ? entry.category.toLowerCase(Locale.ROOT) : "";

        if (nameLower.contains("z-image") || nameLower.contains("turbo") || nameLower.contains("schnell")
                || nameLower.contains("lightning") || nameLower.contains("hyper")) {
            setDimensionAndSamplingValues(view, 1024, 1024, 8, 1.0);
        } else if (nameLower.contains("flux") || catLower.contains("flux")) {
            setDimensionAndSamplingValues(view, 1024, 1024, 20, 1.0);
        } else if (nameLower.contains("sdxl") || catLower.contains("sdxl")) {
            setDimensionAndSamplingValues(view, 1024, 1024, 25, 7.0);
        } else if (nameLower.contains("wan") || nameLower.contains("video") || catLower.contains("video")) {
            setDimensionAndSamplingValues(view, 832, 480, 20, 6.0);
        } else {
            setDimensionAndSamplingValues(view, 1024, 1024, 20, 7.0);
        }

        updateImageInputVisibility(view, entry);
    }

    private void setDimensionAndSamplingValues(PromptLabView view, int width, int height, int steps, double cfg) {
        if (view.getPromptWidthSpinner() != null) view.getPromptWidthSpinner().setValue(width);
        if (view.getPromptHeightSpinner() != null) view.getPromptHeightSpinner().setValue(height);
        if (view.getPromptStepsSpinner() != null) view.getPromptStepsSpinner().setValue(steps);
        if (view.getPromptCfgSpinner() != null) view.getPromptCfgSpinner().setValue(cfg);
    }

    private void updateImageInputVisibility(PromptLabView view, BlueprintGalleryTab.BlueprintEntry entry) {
        if (view.getPromptImageInputPanel() != null) {
            boolean needsImage = isImageEditBlueprint(entry);
            view.getPromptImageInputPanel().setVisible(needsImage);
            if (!needsImage) {
                if (view.getPromptImageFileField() != null) {
                    view.getPromptImageFileField().setText("");
                }
                view.setSelectedInputImage(null);
            }
            if (view.getPromptLabLeftPanel() != null) {
                view.getPromptLabLeftPanel().revalidate();
                view.getPromptLabLeftPanel().repaint();
            }
        }
    }

    /**
     * Finds a matching blueprint entry in the gallery tab matching the given selected name.
     *
     * @param blueprintGalleryTab the blueprint gallery tab
     * @param selectedName        the selected model or blueprint name
     * @return the matched BlueprintEntry, or null if no match was found
     */
    public BlueprintGalleryTab.BlueprintEntry findBlueprintMatch(BlueprintGalleryTab blueprintGalleryTab, String selectedName) {
        if (blueprintGalleryTab == null || selectedName == null || selectedName.isBlank()) {
            return null;
        }
        List<BlueprintGalleryTab.BlueprintEntry> blueprints = blueprintGalleryTab.getAvailableBlueprints();
        for (BlueprintGalleryTab.BlueprintEntry e : blueprints) {
            if (e.name.equalsIgnoreCase(selectedName) || (e.filename != null && e.filename.equalsIgnoreCase(selectedName))) {
                return e;
            }
        }
        for (BlueprintGalleryTab.BlueprintEntry e : blueprints) {
            if (isBlueprintNameMatch(e.name, selectedName) || (e.filename != null && isBlueprintNameMatch(e.filename, selectedName))) {
                return e;
            }
        }
        return null;
    }

    /**
     * Compares two blueprint names using fuzzy matching rules.
     *
     * @param name1 first name
     * @param name2 second name
     * @return true if both names refer to the same blueprint
     */
    public boolean isBlueprintNameMatch(String name1, String name2) {
        if (name1 == null || name2 == null) return false;
        String clean1 = name1.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        String clean2 = name2.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (clean1.equals(clean2) || clean1.contains(clean2) || clean2.contains(clean1)) return true;

        String[] words1 = name1.toLowerCase(Locale.ROOT).replaceAll("[^a-zA-Z0-9]", " ").trim().split("\\s+");
        String[] words2 = name2.toLowerCase(Locale.ROOT).replaceAll("[^a-zA-Z0-9]", " ").trim().split("\\s+");
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

    /**
     * Determines whether a given blueprint entry is suitable for the Prompt Lab.
     *
     * @param e the blueprint entry
     * @return true if the blueprint is supported in Prompt Lab
     */
    public boolean isSupportedPromptLabBlueprint(BlueprintGalleryTab.BlueprintEntry e) {
        if (e == null || e.category == null) return false;
        String cat = e.category.toLowerCase(Locale.ROOT);
        String name = e.name != null ? e.name.toLowerCase(Locale.ROOT) : "";
        String desc = e.description != null ? e.description.toLowerCase(Locale.ROOT) : "";

        if (cat.contains("cloud") || name.contains("cloud") || desc.contains("cloud api")
                || name.contains("replicate") || name.contains("fal.ai") || name.contains("dall-e") || name.contains("openai")) {
            return false;
        }

        return cat.contains("text-to-image") || cat.contains("image lab") || cat.contains("flux") || cat.contains("sdxl") || cat.contains("sd 1.5")
                || name.contains("text to image") || name.contains("t2i") || name.contains("sdxl") || name.contains("flux") || name.contains("lumina")
                || name.contains("img2img") || name.contains("i2i") || name.contains("inpaint") || name.contains("image edit");
    }

    /**
     * Determines whether a blueprint entry represents an image-to-image or editing pipeline requiring an input image.
     *
     * @param entry the blueprint entry
     * @return true if an input image is needed
     */
    public boolean isImageEditBlueprint(BlueprintGalleryTab.BlueprintEntry entry) {
        if (entry == null) return false;
        String cat = entry.category != null ? entry.category.toLowerCase(Locale.ROOT) : "";
        String name = entry.name != null ? entry.name.toLowerCase(Locale.ROOT) : "";
        return cat.contains("img2img") || cat.contains("inpaint") || cat.contains("edit")
                || name.contains("img2img") || name.contains("inpaint") || name.contains("i2i") || name.contains("image edit");
    }

    /**
     * Checks whether a model name refers to a video model.
     *
     * @param modelName the model name
     * @return true if it is a video model
     */
    public boolean isVideoModel(String modelName) {
        if (modelName == null) return false;
        String lower = modelName.toLowerCase(Locale.ROOT);
        return lower.contains("video") || lower.contains("animatediff") || lower.contains("svd")
                || lower.contains("wan") || lower.contains("cogvideo") || lower.contains("ltx");
    }

    /**
     * Scans a directory recursively for model weight files (.safetensors, .ckpt, .pt).
     *
     * @param dir    the root directory to scan
     * @param prefix current relative path prefix
     * @param result output list to append discovered relative paths to
     */
    public void scanModelsRecursively(File dir, String prefix, List<String> result) {
        if (!dir.exists() || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                scanModelsRecursively(f, prefix.isEmpty() ? f.getName() : prefix + "/" + f.getName(), result);
            } else {
                String name = f.getName().toLowerCase(Locale.ROOT);
                if (name.endsWith(".safetensors") || name.endsWith(".ckpt") || name.endsWith(".pt")) {
                    String relPath = prefix.isEmpty() ? f.getName() : prefix + "/" + f.getName();
                    if (!result.contains(relPath)) {
                        result.add(relPath);
                    }
                }
            }
        }
    }

    /**
     * Derives the actual model name required by the blueprint from the combo box selection.
     *
     * @param comboSelection      the selected combo box item
     * @param blueprintGalleryTab the blueprint gallery tab
     * @return the actual model name
     */
    public String getActualModelForPromptLab(String comboSelection, BlueprintGalleryTab blueprintGalleryTab) {
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

    /**
     * Finds the blueprint name corresponding to a given model name.
     *
     * @param modelName           the model name
     * @param blueprintGalleryTab the blueprint gallery tab
     * @return the matching blueprint name, or the model name if not found
     */
    public String getBlueprintNameForModel(String modelName, BlueprintGalleryTab blueprintGalleryTab) {
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

    /**
     * Discovers all available Prompt Lab models from gallery blueprints, ComfyUI API, and local model folders.
     *
     * @param blueprintGalleryTab the blueprint gallery tab
     * @param comfyApiClient      API client to fetch object info from ComfyUI
     * @return list of discovered model and blueprint names
     */
    public List<String> discoverPromptLabModels(BlueprintGalleryTab blueprintGalleryTab, ComfyApiClient comfyApiClient) {
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
            Set<String> apiModels = new TreeSet<>();
            if (comfyApiClient != null && configService != null) {
                String comfyUrl = configService.getComfyUIUrl();
                try {
                    JSONObject info = comfyApiClient.getObjectInfo(comfyUrl);
                    if (modelResolver != null) {
                        modelResolver.updateComfyModelSets(info);
                        for (String ckpt : modelResolver.getComfyCheckpoints()) apiModels.add(ckpt.replace("\\", "/"));
                        for (String unet : modelResolver.getComfyUnetModels()) apiModels.add(unet.replace("\\", "/"));
                    }
                } catch (Exception ignored) {}
            }

            for (String m : apiModels) {
                String mLower = m.toLowerCase(Locale.ROOT);
                if (!isVideoModel(m) && !mLower.contains("cloud") && !itemsToAdd.contains(m)) {
                    itemsToAdd.add(m);
                }
            }

            if (itemsToAdd.isEmpty() && configService != null) {
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
            String lower = item.toLowerCase(Locale.ROOT);
            return lower.contains("cloud") || lower.contains("replicate") || lower.contains("fal.ai")
                    || lower.contains("dall-e") || lower.contains("openai");
        });

        return itemsToAdd;
    }

    /**
     * Refreshes the models available for selection in the Prompt Lab combo box asynchronously.
     *
     * @param view                the PromptLabView UI instance
     * @param blueprintGalleryTab the blueprint gallery tab
     * @param comfyApiClient      API client for ComfyUI
     * @param isUpdatingCombo     guard flag to prevent re-entrant combo updates
     * @param onSelectedCallback  callback when a model/blueprint is selected
     */
    public void refreshPromptLabModels(PromptLabView view,
                                       BlueprintGalleryTab blueprintGalleryTab,
                                       ComfyApiClient comfyApiClient,
                                       AtomicBoolean isUpdatingCombo,
                                       Consumer<String> onSelectedCallback) {
        Runnable task = () -> {
            List<String> itemsToAdd = discoverPromptLabModels(blueprintGalleryTab, comfyApiClient);
            SwingUtilities.invokeLater(() -> updateModelCombo(view, itemsToAdd, isUpdatingCombo, onSelectedCallback));
        };
        if (backgroundExecutor != null) {
            backgroundExecutor.execute(task);
        } else {
            new Thread(task).start();
        }
    }

    private void updateModelCombo(PromptLabView view,
                                  List<String> itemsToAdd,
                                  AtomicBoolean isUpdatingCombo,
                                  Consumer<String> onSelectedCallback) {
        if (view == null || view.getPromptModelCombo() == null) {
            return;
        }
        if (isUpdatingCombo != null) {
            isUpdatingCombo.set(true);
        }
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
            if (isUpdatingCombo != null) {
                isUpdatingCombo.set(false);
            }
        }

        String current = (String) view.getPromptModelCombo().getSelectedItem();
        if (current != null && !current.equals("No ready offline blueprints available") && onSelectedCallback != null) {
            onSelectedCallback.accept(current);
        }
    }
}
