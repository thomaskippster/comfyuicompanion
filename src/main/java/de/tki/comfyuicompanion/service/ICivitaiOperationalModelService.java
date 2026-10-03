package de.tki.comfyuicompanion.service;

import de.tki.comfyuicompanion.domain.CivitaiGenerationResult;
import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.domain.ModelInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service managing operational model dependency resolution, workflow synthesis,
 * and direct generation dispatching to ComfyUI for models browsed in the Model Hub.
 */
public interface ICivitaiOperationalModelService {

    /**
     * Resolves all models required to make the given Civitai model operational in ComfyUI.
     * Includes the primary model plus any necessary base checkpoints, text encoders, or VAEs.
     *
     * @param entry the Civitai model entry to resolve dependencies for
     * @return list of required {@link ModelInfo} instances with resolved local or remote status
     */
    List<ModelInfo> resolveRequiredModels(CivitaiModelEntry entry);

    /**
     * Checks whether all required models in the provided list exist on local storage.
     *
     * @param requiredModels list of required models
     * @return {@code true} if all models are present locally, {@code false} if at least one is missing
     */
    boolean areAllModelsInstalled(List<ModelInfo> requiredModels);

    /**
     * Checks if a single model file physically exists on local storage.
     *
     * @param info the model info to check
     * @return {@code true} if the file exists, {@code false} otherwise
     */
    boolean isModelFilePresent(ModelInfo info);

    /**
     * Filters the required models list and returns only those missing from local storage.
     *
     * @param requiredModels list of required models
     * @return list of missing models that need downloading
     */
    List<ModelInfo> getMissingModels(List<ModelInfo> requiredModels);

    /**
     * Generates a fully configured, runnable ComfyUI API workflow JSON for the given model.
     * Supports both checkpoint and LoRA injection into architecture-specific templates.
     *
     * @param entry          the model entry
     * @param requiredModels the list of operational models
     * @param userPrompt     optional custom positive prompt text (or trigger words fallback)
     * @return workflow JSON string ready for ComfyUI API execution
     */
    String generateWorkflowJson(CivitaiModelEntry entry, List<ModelInfo> requiredModels, String userPrompt);

    /**
     * Sends the synthesized workflow to ComfyUI (ensuring the server is started if offline),
     * loads it into the canvas UI via the bridge, queues the generation prompt, and awaits image completion.
     *
     * @param entry          the model entry
     * @param requiredModels the list of operational models
     * @param userPrompt     optional custom positive prompt text
     * @return a {@link CompletableFuture} completing with {@link CivitaiGenerationResult}
     */
    CompletableFuture<CivitaiGenerationResult> sendToComfyUIAndGenerate(CivitaiModelEntry entry, List<ModelInfo> requiredModels, String userPrompt);
}
