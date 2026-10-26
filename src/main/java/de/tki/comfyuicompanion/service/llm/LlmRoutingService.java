package de.tki.comfyuicompanion.service.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfyuicompanion.domain.llm.GenerationIntent;
import de.tki.comfyuicompanion.service.impl.LocalGemmaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Service that routes user generation requests through an embedded local LLM (Gemma),
 * translating free-form intent into structured generation instructions.
 */
@Service
public class LlmRoutingService implements ILlmRoutingService {

    private static final Logger logger = LoggerFactory.getLogger(LlmRoutingService.class);

    private final LocalGemmaService gemmaService;
    private final ObjectMapper objectMapper;

    // Strict system prompt enforcing schema-conformant JSON output
    private static final String SYSTEM_PROMPT = 
            "You are the orchestrator for ComfyUI. Analyze the user's request. " +
            "If they request precise text in the image, set requiresTextRendering=true and architecture=FLUX. " +
            "If they request people in wide shots, set requiresFaceDetailer=true. " +
            "Respond EXCLUSIVELY in valid JSON matching the following schema:\n" +
            "{\n" +
            "  \"architecture\": \"SDXL|FLUX|SD1.5\",\n" +
            "  \"optimizedPositivePrompt\": \"String\",\n" +
            "  \"targetAspectRatio\": \"String\",\n" +
            "  \"requiresFaceDetailer\": boolean,\n" +
            "  \"requiresTextRendering\": boolean,\n" +
            "  \"suggestedCheckpointKeyword\": \"String\"\n" +
            "}";

    public LlmRoutingService(LocalGemmaService gemmaService, ObjectMapper objectMapper) {
        this.gemmaService = gemmaService;
        this.objectMapper = objectMapper;
    }

    /**
     * Dispatches user intent to the embedded local Gemma model,
     * enforces JSON format via system prompt, and parses the response deterministically.
     *
     * @param userInput the raw user intent text
     * @return GenerationIntent containing structured architecture and prompt instructions
     */
    public GenerationIntent parseUserRequest(String userInput) {
        if (!gemmaService.isModelDownloaded()) {
            logger.warn("Local Gemma model is not downloaded. Using fallback intent.");
            return generateFallback(userInput);
        }

        try {
            // Instruct Gemma to generate structured JSON.
            // Max tokens set sufficiently for complete JSON payload (~300 tokens).
            // Very low temperature (0.1f) for deterministic structured output.
            String responseText = gemmaService.generateCompletion(SYSTEM_PROMPT, userInput, 0.1f, 300);

            // Sanitize: Strip markdown code blocks (```json) if returned by LLM
            String cleanJson = responseText.replaceAll("```json", "").replaceAll("```", "").trim();
            
            // Extract balanced JSON payload between outermost braces
            int startIndex = cleanJson.indexOf('{');
            int endIndex = cleanJson.lastIndexOf('}');
            
            if (startIndex >= 0 && endIndex > startIndex) {
                cleanJson = cleanJson.substring(startIndex, endIndex + 1);
                return objectMapper.readValue(cleanJson, GenerationIntent.class);
            } else {
                logger.error("Gemma did not return a valid JSON object: {}", responseText);
            }

        } catch (IOException e) {
            logger.error("IO error during local Gemma inference", e);
        } catch (Exception e) {
            logger.error("Error parsing Gemma response", e);
        }

        return generateFallback(userInput);
    }

    private GenerationIntent generateFallback(String userInput) {
        GenerationIntent fallback = new GenerationIntent();
        fallback.setArchitecture("SDXL"); // SDXL as safe modern baseline
        fallback.setOptimizedPositivePrompt(userInput);
        fallback.setRequiresFaceDetailer(false);
        fallback.setRequiresTextRendering(false);
        fallback.setTargetAspectRatio("1:1");
        return fallback;
    }
}
