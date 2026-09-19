package de.tki.comfyuicompanion.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class Gemma4Service {

    private final LocalGemmaService localGemmaService;
    private final de.tki.comfyuicompanion.service.IVideoPromptOptimizer promptOptimizer;

    public Gemma4Service(LocalGemmaService localGemmaService) {
        this(localGemmaService, new VideoPromptOptimizer());
    }

    @Autowired
    public Gemma4Service(LocalGemmaService localGemmaService,
                         @Autowired(required = false) de.tki.comfyuicompanion.service.IVideoPromptOptimizer promptOptimizer) {
        this.localGemmaService = localGemmaService;
        this.promptOptimizer = promptOptimizer != null ? promptOptimizer : new VideoPromptOptimizer();
    }

    public boolean isGemmaAvailable() {
        return localGemmaService != null && localGemmaService.isModelDownloaded();
    }

    public void downloadGemmaModel(java.util.function.BiConsumer<Double, String> progressListener, Runnable onFinished, java.util.function.BiConsumer<String, Exception> onError) {
        if (localGemmaService != null) {
            localGemmaService.downloadModel(progressListener, onFinished, onError);
        } else if (onError != null) {
            onError.accept("LocalGemmaService is unavailable.", new IllegalStateException("Service null"));
        }
    }

    public LocalGemmaService getLocalGemmaService() {
        return localGemmaService;
    }

    public de.tki.comfyuicompanion.service.IVideoPromptOptimizer getPromptOptimizer() {
        return promptOptimizer;
    }

    /**
     * Generates a sequence of cinematic video scenes from a master script or concept,
     * defaulting to 5 scenes, Wan 2.1 architecture, and 832x480 resolution.
     *
     * @param idea the raw concept or master script
     * @return the raw JSON array string produced by Gemma
     * @throws Exception if generation fails or model is missing
     */
    public String generateScript(String idea) throws Exception {
        return generateScript(idea, "Wan 2.1", 832, 480, 5);
    }

    /**
     * Generates a sequence of cinematic video scenes tailored to a specific video diffusion model,
     * target resolution, and scene count.
     *
     * @param idea        the master script or concept
     * @param targetModel the target video diffusion model (e.g. "Wan 2.1", "LTX-Video")
     * @param width       the target video width
     * @param height      the target video height
     * @param sceneCount  the requested number of sequential scenes
     * @return the raw JSON array string produced by Gemma
     * @throws Exception if generation fails or model is not downloaded
     */
    public String generateScript(String idea, String targetModel, int width, int height, int sceneCount) throws Exception {
        if (localGemmaService == null || !localGemmaService.isModelDownloaded()) {
            throw new IllegalStateException("Local Gemma model is not downloaded. " +
                    "Please download the Gemma-3-4B model first to unlock AI storyboard deconstruction.");
        }

        int count = sceneCount > 0 ? sceneCount : 5;
        String modelStr = (targetModel != null && !targetModel.isBlank()) ? targetModel : "Wan 2.1";
        String systemPrompt = promptOptimizer.buildStoryboardSystemPrompt(modelStr, width, height, count);
        String userPrompt = String.format("""
                Master Video Concept:
                %s

                Context Directives:
                - Target Video Diffusion Architecture: %s
                - Video Dimensions: %dx%d
                - Scene Count Required: Exactly %d sequential scenes (S1 to S%d)
                """, idea, modelStr, width > 0 ? width : 832, height > 0 ? height : 480, count, count);

        // Use 0.65f temperature to prevent phrase-repetition loops while maintaining strict JSON adherence
        return localGemmaService.generateCompletion(systemPrompt, userPrompt, 0.65f, 2048);
    }
}
