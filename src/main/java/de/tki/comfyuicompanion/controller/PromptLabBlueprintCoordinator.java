package de.tki.comfyuicompanion.controller;

import de.tki.comfyuicompanion.domain.ComfyTemplate;
import de.tki.comfyuicompanion.service.IComfyTemplateService;
import de.tki.comfyuicompanion.service.IWorkflowDownloader;
import de.tki.comfyuicompanion.service.impl.ComfyApiClient;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab;
import de.tki.comfyuicompanion.ui.PromptLabView;
import de.tki.comfyuicompanion.util.BackgroundExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import java.io.File;
import java.util.List;
import java.util.Locale;
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
    private final PromptLabWorkflowResolver workflowResolver;
    private final PromptLabModelDiscovery modelDiscovery;

    /**
     * Constructs a new PromptLabBlueprintCoordinator with default helper components.
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
        this(configService, comfyTemplateService, workflowDownloader, backgroundExecutor, modelResolver,
                new PromptLabWorkflowResolver(configService, comfyTemplateService, workflowDownloader, modelResolver),
                new PromptLabModelDiscovery(configService, modelResolver, backgroundExecutor));
    }

    /**
     * Constructs a new PromptLabBlueprintCoordinator with injected helper components.
     *
     * @param configService        configuration service for file paths and ComfyUI URLs
     * @param comfyTemplateService service to determine workflow templates for models
     * @param workflowDownloader   service to download remote workflow files when needed
     * @param backgroundExecutor   executor for running asynchronous fetch tasks
     * @param modelResolver        resolver for model injection and checkpoint/unet sets
     * @param workflowResolver     resolver for loading and converting workflow JSONs
     * @param modelDiscovery       component discovering models from ComfyUI and local directories
     */
    public PromptLabBlueprintCoordinator(ConfigService configService,
                                         IComfyTemplateService comfyTemplateService,
                                         IWorkflowDownloader workflowDownloader,
                                         BackgroundExecutor backgroundExecutor,
                                         PromptLabModelResolver modelResolver,
                                         PromptLabWorkflowResolver workflowResolver,
                                         PromptLabModelDiscovery modelDiscovery) {
        this.configService = configService;
        this.comfyTemplateService = comfyTemplateService;
        this.workflowDownloader = workflowDownloader;
        this.backgroundExecutor = backgroundExecutor;
        this.modelResolver = modelResolver;
        this.workflowResolver = workflowResolver;
        this.modelDiscovery = modelDiscovery;
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
     * Resolves the workflow JSON content for a target blueprint.
     *
     * @param targetEntry the blueprint entry to resolve
     * @return the resolved JSON content string, or null if unresolvable
     */
    public String resolveBlueprintWorkflowJson(BlueprintGalleryTab.BlueprintEntry targetEntry) {
        return workflowResolver.resolveBlueprintWorkflowJson(targetEntry);
    }

    /**
     * Converts a UI-formatted workflow JSON graph to API prompt format if nodes are present.
     *
     * @param jsonContent the raw workflow JSON string
     * @return the API-formatted JSON string or original string if already formatted
     */
    public String convertUiToApiIfNeeded(String jsonContent) {
        return workflowResolver.convertUiToApiIfNeeded(jsonContent);
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
        return workflowResolver.isBlueprintNameMatch(name1, name2);
    }

    /**
     * Determines whether a given blueprint entry is suitable for the Prompt Lab.
     *
     * @param e the blueprint entry
     * @return true if the blueprint is supported in Prompt Lab
     */
    public boolean isSupportedPromptLabBlueprint(BlueprintGalleryTab.BlueprintEntry e) {
        return modelDiscovery.isSupportedPromptLabBlueprint(e);
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
        return modelDiscovery.isVideoModel(modelName);
    }

    /**
     * Scans a directory recursively for model weight files (.safetensors, .ckpt, .pt).
     *
     * @param dir    the root directory to scan
     * @param prefix current relative path prefix
     * @param result output list to append discovered relative paths to
     */
    public void scanModelsRecursively(File dir, String prefix, List<String> result) {
        modelDiscovery.scanModelsRecursively(dir, prefix, result);
    }

    /**
     * Derives the actual model name required by the blueprint from the combo box selection.
     *
     * @param comboSelection      the selected combo box item
     * @param blueprintGalleryTab the blueprint gallery tab
     * @return the actual model name
     */
    public String getActualModelForPromptLab(String comboSelection, BlueprintGalleryTab blueprintGalleryTab) {
        return modelDiscovery.getActualModelForPromptLab(comboSelection, blueprintGalleryTab);
    }

    /**
     * Finds the blueprint name corresponding to a given model name.
     *
     * @param modelName           the model name
     * @param blueprintGalleryTab the blueprint gallery tab
     * @return the matching blueprint name, or the model name if not found
     */
    public String getBlueprintNameForModel(String modelName, BlueprintGalleryTab blueprintGalleryTab) {
        return modelDiscovery.getBlueprintNameForModel(modelName, blueprintGalleryTab);
    }

    /**
     * Discovers all available Prompt Lab models from gallery blueprints, ComfyUI API, and local model folders.
     *
     * @param blueprintGalleryTab the blueprint gallery tab
     * @param comfyApiClient      API client to fetch object info from ComfyUI
     * @return list of discovered model and blueprint names
     */
    public List<String> discoverPromptLabModels(BlueprintGalleryTab blueprintGalleryTab, ComfyApiClient comfyApiClient) {
        return modelDiscovery.discoverPromptLabModels(blueprintGalleryTab, comfyApiClient);
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
        modelDiscovery.refreshPromptLabModels(view, blueprintGalleryTab, comfyApiClient, isUpdatingCombo, onSelectedCallback);
    }
}
