package de.tki.comfymodels.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class Gemma4Service {

    private final LocalGemmaService localGemmaService;
    private final de.tki.comfymodels.service.IVideoPromptOptimizer promptOptimizer;

    public Gemma4Service(LocalGemmaService localGemmaService) {
        this(localGemmaService, new VideoPromptOptimizer());
    }

    @Autowired
    public Gemma4Service(LocalGemmaService localGemmaService,
                         @Autowired(required = false) de.tki.comfymodels.service.IVideoPromptOptimizer promptOptimizer) {
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

    public de.tki.comfymodels.service.IVideoPromptOptimizer getPromptOptimizer() {
        return promptOptimizer;
    }

    public String generateScript(String idea) throws Exception {
        if (localGemmaService == null || !localGemmaService.isModelDownloaded()) {
            throw new IllegalStateException("Local Gemma model is not downloaded. " +
                    "Please download the Gemma-3-4B model first to unlock AI storyboard deconstruction.");
        }

        String systemPrompt = promptOptimizer.buildStoryboardSystemPrompt();
        String userPrompt = "Master Video Script / Idea:\n" + idea;

        return localGemmaService.generateCompletion(systemPrompt, userPrompt, 0.3f, 2048);
    }
}
