package de.tki.comfyuicompanion.controller;

import de.tki.comfyuicompanion.service.impl.ComfyApiClient;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab;
import de.tki.comfyuicompanion.ui.PromptLabView;
import de.tki.comfyuicompanion.util.BackgroundExecutor;
import org.json.JSONObject;

import javax.swing.SwingUtilities;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Discovers and manages models available for Prompt Lab from ComfyUI API,
 * active blueprints, and local model folders.
 */
public class PromptLabModelDiscovery {

    private final ConfigService configService;
    private final PromptLabModelResolver modelResolver;
    private final BackgroundExecutor backgroundExecutor;

    /**
     * Constructs a new PromptLabModelDiscovery instance.
     *
     * @param configService      configuration service for paths and URLs
     * @param modelResolver      model resolver for updating checkpoints and unets
     * @param backgroundExecutor background executor for async tasks
     */
    public PromptLabModelDiscovery(ConfigService configService,
                                   PromptLabModelResolver modelResolver,
                                   BackgroundExecutor backgroundExecutor) {
        this.configService = configService;
        this.modelResolver = modelResolver;
        this.backgroundExecutor = backgroundExecutor;
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
        } else if (configService != null) {
            String modelsPath = configService.getModelsPath();
            if (modelsPath != null && !modelsPath.isEmpty()) {
                File checkpointsDir = new File(modelsPath, "checkpoints");
                if (checkpointsDir.exists() && checkpointsDir.isDirectory()) {
                    scanModelsRecursively(checkpointsDir, "", itemsToAdd);
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
            if ((selected == null || "No ready offline blueprints available".equals(selected)) && configService != null) {
                JSONObject session = configService.getPromptLabSession();
                if (session != null && session.has("model")) {
                    selected = session.optString("model", null);
                }
            }
            view.getPromptModelCombo().removeAllItems();

            if (itemsToAdd.isEmpty()) {
                view.getPromptModelCombo().addItem("No ready offline blueprints available");
                view.getPromptModelCombo().setEnabled(false);
                if (view.getBtnSendToComfy() != null) {
                    view.getBtnSendToComfy().setEnabled(false);
                    view.getBtnSendToComfy().setToolTipText("No ready blueprints or local models available. Download models or configure paths first.");
                }
                if (view.getPromptPresetLabel() != null) {
                    view.getPromptPresetLabel().setText("⚠️ No ready blueprints or local models detected");
                }
                if (view.getPromptJsonArea() != null) {
                    view.getPromptJsonArea().setText("{\n  \"prompt\": {}\n}");
                }
                if (view.getPromptLabConsole() != null) {
                    view.getPromptLabConsole().setText(
                            "⚠️ System: No ready blueprints or local models detected.\n"
                            + "💡 Blueprint selection and image generation are disabled until required models are downloaded or model paths are configured in Settings.\n");
                }
            } else {
                view.getPromptModelCombo().setEnabled(true);
                if (view.getBtnSendToComfy() != null) {
                    view.getBtnSendToComfy().setEnabled(true);
                    view.getBtnSendToComfy().setToolTipText("Send workflow prompt to ComfyUI");
                }
                if (view.getPromptLabConsole() != null && view.getPromptLabConsole().getText().contains("No ready blueprints")) {
                    view.getPromptLabConsole().setText("System: Prompt Lab ready.\n");
                }
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
