package de.tki.comfymodels.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class Gemma4Service {

    private final LocalGemmaService localGemmaService;

    @Autowired
    public Gemma4Service(LocalGemmaService localGemmaService) {
        this.localGemmaService = localGemmaService;
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

    public String generateScript(String idea) throws Exception {
        if (localGemmaService == null || !localGemmaService.isModelDownloaded()) {
            throw new IllegalStateException("Local Gemma model is not downloaded. " +
                    "Please download the Gemma-3-4B model first to unlock AI storyboard deconstruction.");
        }

        String systemPrompt = "You are an expert Hollywood director, storyboard architect, and AI video prompt engineer.\n" +
                "Deconstruct the user's master video idea into sequential, cinematic visual scenes.\n" +
                "For EACH scene, you MUST generate an object in a JSON array with exactly these keys:\n" +
                "1. 'scene_id': sequential identifier ('S1', 'S2', 'S3', ...)\n" +
                "2. 'visual_prompt': a detailed, highly descriptive prompt capturing character actions, environment, cinematic camera angles, dynamic lighting, mood, and photorealistic textures for video generation (do not include any text, typography, letters, words, subtitles, captions, or overlays).\n" +
                "3. 'duration_seconds': integer duration in seconds (typically between 3 and 8 seconds).\n" +
                "4. 'narration_text': compelling voiceover or narration text matching the scene's visual flow.\n\n" +
                "Return ONLY the raw JSON array starting with '[' and ending with ']'. Do not wrap it in markdown code fences or explanatory text.";

        String userPrompt = "Master Video Script / Idea:\n" + idea;

        return localGemmaService.generateCompletion(systemPrompt, userPrompt, 0.3f, 2048);
    }
}
