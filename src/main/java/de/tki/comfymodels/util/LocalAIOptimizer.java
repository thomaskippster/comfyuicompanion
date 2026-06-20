package de.tki.comfymodels.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfymodels.service.impl.LocalGemmaService;

public class LocalAIOptimizer {

    private final LocalGemmaService localGemmaService;
    private final ObjectMapper objectMapper;

    public LocalAIOptimizer(LocalGemmaService localGemmaService) {
        this.localGemmaService = localGemmaService;
        this.objectMapper = new ObjectMapper();
    }

    public AIConfiguration getOptimizedConfig(String feedback) throws Exception {
        // System-Prompt to instruct Gemma to reply with structured JSON only
        String systemPrompt = "You are a test agent for a ComfyUI video app. You must respond ONLY with a valid JSON object. " +
                "Do NOT wrap the response in markdown blocks like ```json or use other text. " +
                "The JSON object must contain exactly: 'prompt' (String), 'cfgScale' (Int between 1 and 20), " +
                "'steps' (Int between 10 and 50), and 'motionBucketId' (Int between 1 and 255).";

        String userPrompt = "Feedback from the previous run:\n" + feedback;

        String rawResponse = null;
        if (localGemmaService != null && localGemmaService.isModelDownloaded()) {
            try {
                System.out.println("🤖 Asking Local Gemma for optimized video parameters...");
                rawResponse = localGemmaService.generateCompletion(systemPrompt, userPrompt, 0.4f, 256);
            } catch (Exception e) {
                System.err.println("⚠️ Local Gemma generation failed, using structured fallback: " + e.getMessage());
            }
        }

        // Fallback scenario when model is not downloaded or failed
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            System.out.println("🤖 [Gemma Optimizer Fallback] Generating parameter optimization fallback...");
            
            // Adjust parameter values based on feedback clues to simulate a real optimization loop
            int cfg = 7;
            int steps = 20;
            int motionBucket = 127;
            String prompt = "A fluid, coherent camera pan through a cyberpunk city street, highly detailed, neon lights, night, cyberpunk aesthetic, high quality video";

            if (feedback.contains("steps")) {
                steps = 15;
            }
            if (feedback.contains("CFG")) {
                cfg = 6;
            }
            if (feedback.contains("MotionBucket")) {
                motionBucket = 100;
            }

            rawResponse = String.format(
                "{\"prompt\": \"%s\", \"cfgScale\": %d, \"steps\": %d, \"motionBucketId\": %d}",
                prompt, cfg, steps, motionBucket
            );
        }

        // Clean any potential markdown formatting
        String cleanJson = rawResponse.replaceAll("```json", "").replaceAll("```", "").trim();
        if (cleanJson.startsWith("\"") && cleanJson.endsWith("\"") && cleanJson.length() > 1) {
            cleanJson = cleanJson.substring(1, cleanJson.length() - 1);
        }

        return objectMapper.readValue(cleanJson, AIConfiguration.class);
    }
}
