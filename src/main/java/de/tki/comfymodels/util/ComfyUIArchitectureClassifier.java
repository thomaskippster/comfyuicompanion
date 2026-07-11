package de.tki.comfymodels.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfymodels.domain.ModelArchitecture;
import de.tki.comfymodels.service.impl.LocalGemmaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Utility and service class for classifying ComfyUI workflow JSONs 
 * and model filenames into model architectures using the local Gemma service.
 */
@Component
public class ComfyUIArchitectureClassifier {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ComfyUIArchitectureClassifier.class);

    private final LocalGemmaService localGemmaService;
    private final ObjectMapper objectMapper;

    public ComfyUIArchitectureClassifier() {
        this.localGemmaService = null;
        this.objectMapper = new ObjectMapper();
    }

    @Autowired
    public ComfyUIArchitectureClassifier(LocalGemmaService localGemmaService) {
        this.localGemmaService = localGemmaService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Extracts unique class_type values from a ComfyUI JSON string.
     */
    public Set<String> extractNodeTypes(String jsonString) throws JsonProcessingException {
        if (jsonString == null || jsonString.isBlank()) {
            return Collections.emptySet();
        }
        JsonNode root = objectMapper.readTree(jsonString);
        return extractNodeTypes(root);
    }

    /**
     * Extracts unique class_type values from a Jackson JsonNode.
     */
    public Set<String> extractNodeTypes(JsonNode rootNode) {
        Set<String> classTypes = new LinkedHashSet<>();
        if (rootNode == null) {
            return classTypes;
        }

        JsonNode promptNode = rootNode;
        if (rootNode.has("prompt") && rootNode.get("prompt").isObject()) {
            promptNode = rootNode.get("prompt");
        }

        Iterator<Map.Entry<String, JsonNode>> fields = promptNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode node = field.getValue();
            if (node.isObject() && node.has("class_type")) {
                JsonNode classTypeNode = node.get("class_type");
                if (classTypeNode.isTextual()) {
                    classTypes.add(classTypeNode.asText());
                }
            }
        }
        return classTypes;
    }

    /**
     * Prompt-Zusammenbau für das LLM - System Prompt
     */
    public String buildSystemPrompt() {
        return "Du bist ein technischer Klassifizierer für Stable Diffusion Workflows. " +
                "Analysiere die Liste der ComfyUI-Nodes und bestimme die Basis-Architektur. " +
                "Erlaubte Rückgabewerte für 'architecture': ARCH_SD15, ARCH_SDXL, ARCH_SD3, ARCH_FLUX, ARCH_WAN, ARCH_HUNYUAN, ARCH_LUMINA2, ARCH_HYBRID, ARCH_UNKNOWN. " +
                "Antworte AUSSCHLIESSLICH mit einem validen JSON-Objekt im Format: {\"architecture\": \"WERT\"}. " +
                "Keine Erklärungen.";
    }

    /**
     * Prompt-Zusammenbau für das LLM - User Prompt
     */
    public String buildUserPrompt(Set<String> nodeTypes) {
        String commaSeparated = String.join(", ", nodeTypes);
        return "In diesem Workflow existieren folgende Nodes: [" + commaSeparated + "]. Welche Basis-Architektur ist das?";
    }

    /**
     * LLM-API-Call (Local Gemma) - Asynchron
     */
    public CompletableFuture<ModelArchitecture> classifyWorkflowAsync(Set<String> nodeTypes) {
        if (nodeTypes == null || nodeTypes.isEmpty()) {
            return CompletableFuture.completedFuture(ModelArchitecture.ARCH_UNKNOWN);
        }
        if (localGemmaService == null || !localGemmaService.isModelDownloaded()) {
            return CompletableFuture.completedFuture(ModelArchitecture.ARCH_UNKNOWN);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String systemPrompt = buildSystemPrompt();
                String userPrompt = buildUserPrompt(nodeTypes);
                
                String responseText = localGemmaService.generateCompletion(systemPrompt, userPrompt, 0.1f, 128);
                return parseInnerArchitectureResponse(responseText);
            } catch (Exception e) {
                logger.error("Local Gemma workflow classification failed: " + e.getMessage());
                return ModelArchitecture.ARCH_UNKNOWN;
            }
        });
    }

    /**
     * Synchroner Wrapper für Workflow-Klassifizierung
     */
    public ModelArchitecture classifyWorkflow(Set<String> nodeTypes) {
        try {
            return classifyWorkflowAsync(nodeTypes)
                    .get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("Synchronous Gemma workflow classification timeout or error: " + e.getMessage());
            return ModelArchitecture.ARCH_UNKNOWN;
        }
    }

    /**
     * Classifies a model filename into a ModelArchitecture using the local Gemma service.
     */
    public CompletableFuture<ModelArchitecture> classifyModelAsync(String modelFilename) {
        if (modelFilename == null || modelFilename.isBlank()) {
            return CompletableFuture.completedFuture(ModelArchitecture.ARCH_UNKNOWN);
        }
        if (localGemmaService == null || !localGemmaService.isModelDownloaded()) {
            return CompletableFuture.completedFuture(ModelArchitecture.ARCH_UNKNOWN);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String systemPrompt = "Du bist ein technischer Klassifizierer für Machine-Learning-Modelle (Stable Diffusion, ComfyUI). " +
                        "Analysiere den Dateinamen des Modells und bestimme die Basis-Architektur. " +
                        "Erlaubte Rückgabewerte für 'architecture': ARCH_SD15, ARCH_SDXL, ARCH_SD3, ARCH_FLUX, ARCH_WAN, ARCH_HUNYUAN, ARCH_LUMINA2, ARCH_UNKNOWN. " +
                        "Antworte AUSSCHLIESSLICH mit einem validen JSON-Objekt im Format: {\"architecture\": \"WERT\"}. " +
                        "Keine Erklärungen.";

                String userPrompt = "Welche Basis-Architektur hat das Modell mit dem Dateinamen: '" + modelFilename + "'?";

                String responseText = localGemmaService.generateCompletion(systemPrompt, userPrompt, 0.1f, 128);
                return parseInnerArchitectureResponse(responseText);
            } catch (Exception e) {
                logger.error("Local Gemma model classification failed: " + e.getMessage());
                return ModelArchitecture.ARCH_UNKNOWN;
            }
        });
    }

    /**
     * Synchroner Wrapper für Modelldateiname-Klassifizierung
     */
    public ModelArchitecture classifyModel(String modelFilename) {
        try {
            return classifyModelAsync(modelFilename)
                    .get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("Synchronous Gemma model classification timeout or error: " + e.getMessage());
            return ModelArchitecture.ARCH_UNKNOWN;
        }
    }

    /**
     * Response Parsing for direct Gemma response (without Ollama envelope)
     */
    public ModelArchitecture parseInnerArchitectureResponse(String generatedText) {
        if (generatedText == null || generatedText.isBlank()) {
            return ModelArchitecture.ARCH_UNKNOWN;
        }

        String cleaned = generatedText.trim();
        // Remove markdown block backticks if present
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
        }

        try {
            JsonNode root = objectMapper.readTree(cleaned);
            if (root.has("architecture")) {
                String archVal = root.get("architecture").asText();
                return mapToArchitecture(archVal);
            }
        } catch (Exception e) {
            // Fallback to searching the raw text if it's not well-formed JSON
            return parseFromTextFallback(cleaned);
        }
        
        return parseFromTextFallback(cleaned);
    }

    private ModelArchitecture mapToArchitecture(String val) {
        if (val == null) {
            return ModelArchitecture.ARCH_UNKNOWN;
        }
        switch (val.toUpperCase().trim()) {
            case "ARCH_SD15":
                return ModelArchitecture.ARCH_SD15;
            case "ARCH_SDXL":
                return ModelArchitecture.ARCH_SDXL;
            case "ARCH_SD3":
                return ModelArchitecture.ARCH_SD3;
            case "ARCH_FLUX":
                return ModelArchitecture.ARCH_FLUX;
            case "ARCH_WAN":
                return ModelArchitecture.ARCH_WAN;
            case "ARCH_HUNYUAN":
                return ModelArchitecture.ARCH_HUNYUAN;
            case "ARCH_LUMINA2":
                return ModelArchitecture.ARCH_LUMINA2;
            case "ARCH_HYBRID":
            case "ARCH_UNKNOWN":
            default:
                return ModelArchitecture.ARCH_UNKNOWN;
        }
    }

    private ModelArchitecture parseFromTextFallback(String text) {
        if (text == null) {
            return ModelArchitecture.ARCH_UNKNOWN;
        }
        String upper = text.toUpperCase();
        if (upper.contains("ARCH_SD15")) {
            return ModelArchitecture.ARCH_SD15;
        } else if (upper.contains("ARCH_SDXL")) {
            return ModelArchitecture.ARCH_SDXL;
        } else if (upper.contains("ARCH_SD3")) {
            return ModelArchitecture.ARCH_SD3;
        } else if (upper.contains("ARCH_FLUX")) {
            return ModelArchitecture.ARCH_FLUX;
        } else if (upper.contains("ARCH_WAN")) {
            return ModelArchitecture.ARCH_WAN;
        } else if (upper.contains("ARCH_HUNYUAN")) {
            return ModelArchitecture.ARCH_HUNYUAN;
        } else if (upper.contains("ARCH_LUMINA2")) {
            return ModelArchitecture.ARCH_LUMINA2;
        }
        return ModelArchitecture.ARCH_UNKNOWN;
    }
}
