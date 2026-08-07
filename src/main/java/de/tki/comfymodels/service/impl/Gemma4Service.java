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

    public String generateScript(String idea) throws Exception {
        if (localGemmaService == null || !localGemmaService.isModelDownloaded()) {
            throw new IllegalStateException("Local Gemma model is not downloaded. " +
                    "Please navigate to the Image Lab tab and click 'Download local Gemma' to download the model first.");
        }

        String systemPrompt = "You are a professional video scriptwriter and director. " +
                "You MUST generate a JSON array of objects representing the timeline scenes. " +
                "Each object MUST contain exactly these fields and nothing else:\n" +
                "1. 'scene_id': unique identifier (e.g. S1, S2, S3)\n" +
                "2. 'visual_prompt': a detailed, descriptive prompt containing characters, lighting, camera angle, style, and visual action (perfect for ComfyUI generation). IMPORTANT: Do NOT include any text, typography, letters, words, subtitles, captions, or overlays in this visual description. The scene should be purely visual.\n" +
                "3. 'duration_seconds': integer duration (e.g., 3, 5, 8)\n" +
                "4. 'narration_text': the voiceover or narrative description for this scene\n\n" +
                "Return ONLY the raw JSON array. Do not wrap it in markdown code block formatting (like ```json).";

        String userPrompt = "Video Idea: " + idea;

        return localGemmaService.generateCompletion(systemPrompt, userPrompt, 0.5f, 2048);
    }
}
