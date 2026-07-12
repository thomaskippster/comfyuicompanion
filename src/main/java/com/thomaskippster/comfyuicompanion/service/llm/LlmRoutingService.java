package com.thomaskippster.comfyuicompanion.service.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thomaskippster.comfyuicompanion.domain.llm.GenerationIntent;
import de.tki.comfymodels.service.impl.LocalGemmaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class LlmRoutingService {

    private static final Logger logger = LoggerFactory.getLogger(LlmRoutingService.class);

    private final LocalGemmaService gemmaService;
    private final ObjectMapper objectMapper;

    // Harter System-Prompt mit strenger JSON-Formatierung
    private static final String SYSTEM_PROMPT = 
            "Du bist der Orchestrator für ComfyUI. Analysiere den Wunsch des Nutzers. " +
            "Wenn er präzisen Text im Bild fordert, setze requiresTextRendering=true und architecture=FLUX. " +
            "Wenn er Personen in der Totalen fordert, setze requiresFaceDetailer=true. " +
            "Antworte AUSSCHLIESSLICH in validem JSON passend zu folgendem Schema:\n" +
            "{\n" +
            "  \"architecture\": \"SDXL|FLUX|SD1.5\",\n" +
            "  \"optimizedPositivePrompt\": \"String\",\n" +
            "  \"targetAspectRatio\": \"String\",\n" +
            "  \"requiresFaceDetailer\": boolean,\n" +
            "  \"requiresTextRendering\": boolean,\n" +
            "  \"suggestedCheckpointKeyword\": \"String\"\n" +
            "}";

    public LlmRoutingService(LocalGemmaService gemmaService, ObjectMapper objectMapper) {
        // Wir nutzen den bereits im System etablierten LocalGemmaService via llama.cpp (Java Wrapper)
        this.gemmaService = gemmaService;
        this.objectMapper = objectMapper;
    }

    /**
     * Sendet den User-Input an das eingebettete lokale Gemma-Modell, 
     * erzwingt ein JSON-Format via System-Prompt und parst die Antwort deterministisch.
     * 
     * @param userInput Der rohe Text-Wunsch des Nutzers
     * @return GenerationIntent mit den strukturierten Architektur- und Prompt-Vorgaben
     */
    public GenerationIntent parseUserRequest(String userInput) {
        if (!gemmaService.isModelDownloaded()) {
            logger.warn("Das lokale Gemma Modell ist nicht heruntergeladen. Nutze Fallback-Intent.");
            return generateFallback(userInput);
        }

        try {
            // Wir lassen Gemma das strukturierte JSON generieren. 
            // Max Tokens hoch genug für das gesamte JSON (ca. 300 Tokens).
            // Temperatur sehr niedrig (0.1f) für deterministischen JSON Output.
            String responseText = gemmaService.generateCompletion(SYSTEM_PROMPT, userInput, 0.1f, 300);

            // Sanitize: Da LLMs manchmal Markdown Code-Blöcke (```json) drumherum packen
            String cleanJson = responseText.replaceAll("```json", "").replaceAll("```", "").trim();
            
            // Schneide alles ab, was vor der ersten oder nach der letzten geschweiften Klammer steht
            int startIndex = cleanJson.indexOf('{');
            int endIndex = cleanJson.lastIndexOf('}');
            
            if (startIndex >= 0 && endIndex > startIndex) {
                cleanJson = cleanJson.substring(startIndex, endIndex + 1);
                return objectMapper.readValue(cleanJson, GenerationIntent.class);
            } else {
                logger.error("Gemma hat kein valides JSON-Objekt zurückgegeben: {}", responseText);
            }

        } catch (IOException e) {
            logger.error("IO-Fehler bei der lokalen Gemma Inference", e);
        } catch (Exception e) {
            logger.error("Fehler beim Parsen der Gemma-Antwort", e);
        }

        return generateFallback(userInput);
    }

    private GenerationIntent generateFallback(String userInput) {
        GenerationIntent fallback = new GenerationIntent();
        fallback.setArchitecture("SDXL"); // SDXL als sicherer moderner Standard
        fallback.setOptimizedPositivePrompt(userInput);
        fallback.setRequiresFaceDetailer(false);
        fallback.setRequiresTextRendering(false);
        fallback.setTargetAspectRatio("1:1");
        return fallback;
    }
}
