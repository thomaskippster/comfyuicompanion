package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfyuicompanion.domain.ModelArchitecture;
import de.tki.comfyuicompanion.service.IArchitectureClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Enterprise Service implementation for classifying ComfyUI workflow JSON structures
 * and model filenames using local Gemma LLM inference.
 */
@Service
public class ArchitectureClassificationService implements IArchitectureClassifier {

    private static final Logger logger = LoggerFactory.getLogger(ArchitectureClassificationService.class);

    private final LocalGemmaService localGemmaService;
    private final ObjectMapper objectMapper;

    public ArchitectureClassificationService() {
        this(null);
    }

    @Autowired
    public ArchitectureClassificationService(@Autowired(required = false) LocalGemmaService localGemmaService) {
        this.localGemmaService = localGemmaService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Set<String> extractNodeTypes(String jsonString) throws JsonProcessingException {
        if (jsonString == null || jsonString.isBlank()) {
            return Collections.emptySet();
        }
        JsonNode root = objectMapper.readTree(jsonString);
        return extractNodeTypes(root);
    }

    @Override
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

    public String buildSystemPrompt() {
        return "You are a technical classifier for Stable Diffusion workflows. " +
                "Analyze the list of ComfyUI nodes and determine the base architecture. " +
                "Allowed return values for 'architecture': ARCH_SD15, ARCH_SDXL, ARCH_SD3, ARCH_FLUX, ARCH_WAN, ARCH_HUNYUAN, ARCH_LUMINA2, ARCH_HYBRID, ARCH_UNKNOWN. " +
                "Respond EXCLUSIVELY with a valid JSON object in the format: {\"architecture\": \"VALUE\"}. " +
                "No explanations.";
    }

    public String buildUserPrompt(Set<String> nodeTypes) {
        String commaSeparated = String.join(", ", nodeTypes);
        return "The following nodes exist in this workflow: [" + commaSeparated + "]. Which base architecture is this?";
    }

    @Override
    public CompletableFuture<ModelArchitecture> classifyArchitectureAsync(Set<String> nodeTypes) {
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
                logger.error("Local Gemma workflow classification failed: {}", e.getMessage());
                return ModelArchitecture.ARCH_UNKNOWN;
            }
        });
    }

    @Override
    public ModelArchitecture classifySync(String jsonWorkflow) {
        try {
            Set<String> nodeTypes = extractNodeTypes(jsonWorkflow);
            return classifyArchitectureAsync(nodeTypes).get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("Synchronous Gemma workflow classification timeout or error: {}", e.getMessage());
            return ModelArchitecture.ARCH_UNKNOWN;
        }
    }

    @Override
    public CompletableFuture<ModelArchitecture> classifyModelFileName(String modelFilename) {
        if (modelFilename == null || modelFilename.isBlank()) {
            return CompletableFuture.completedFuture(ModelArchitecture.ARCH_UNKNOWN);
        }
        if (localGemmaService == null || !localGemmaService.isModelDownloaded()) {
            return CompletableFuture.completedFuture(ModelArchitecture.ARCH_UNKNOWN);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String systemPrompt = "You are a technical classifier for machine learning models (Stable Diffusion, ComfyUI). " +
                        "Analyze the model filename and determine the base architecture. " +
                        "Allowed return values for 'architecture': ARCH_SD15, ARCH_SDXL, ARCH_SD3, ARCH_FLUX, ARCH_WAN, ARCH_HUNYUAN, ARCH_LUMINA2, ARCH_UNKNOWN. " +
                        "Respond EXCLUSIVELY with a valid JSON object in the format: {\"architecture\": \"VALUE\"}. " +
                        "No explanations.";

                String userPrompt = "Which base architecture does the model with filename: '" + modelFilename + "' have?";

                String responseText = localGemmaService.generateCompletion(systemPrompt, userPrompt, 0.1f, 128);
                return parseInnerArchitectureResponse(responseText);
            } catch (Exception e) {
                logger.error("Local Gemma model classification failed: {}", e.getMessage());
                return ModelArchitecture.ARCH_UNKNOWN;
            }
        });
    }

    @Override
    public ModelArchitecture classifyFilenameSync(String modelFilename) {
        try {
            return classifyModelFileName(modelFilename).get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("Synchronous Gemma model classification timeout or error: {}", e.getMessage());
            return ModelArchitecture.ARCH_UNKNOWN;
        }
    }

    public ModelArchitecture parseInnerArchitectureResponse(String generatedText) {
        if (generatedText == null || generatedText.isBlank()) {
            return ModelArchitecture.ARCH_UNKNOWN;
        }

        String cleaned = generatedText.trim();
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
            return parseFromTextFallback(cleaned);
        }

        return parseFromTextFallback(cleaned);
    }

    private ModelArchitecture mapToArchitecture(String val) {
        if (val == null) {
            return ModelArchitecture.ARCH_UNKNOWN;
        }
        return switch (val.toUpperCase().trim()) {
            case "ARCH_SD15" -> ModelArchitecture.ARCH_SD15;
            case "ARCH_SDXL" -> ModelArchitecture.ARCH_SDXL;
            case "ARCH_SD3" -> ModelArchitecture.ARCH_SD3;
            case "ARCH_FLUX" -> ModelArchitecture.ARCH_FLUX;
            case "ARCH_WAN" -> ModelArchitecture.ARCH_WAN;
            case "ARCH_HUNYUAN" -> ModelArchitecture.ARCH_HUNYUAN;
            case "ARCH_LUMINA2" -> ModelArchitecture.ARCH_LUMINA2;
            default -> ModelArchitecture.ARCH_UNKNOWN;
        };
    }

    private ModelArchitecture parseFromTextFallback(String text) {
        if (text == null) {
            return ModelArchitecture.ARCH_UNKNOWN;
        }
        String upper = text.toUpperCase();
        if (upper.contains("ARCH_SD15")) return ModelArchitecture.ARCH_SD15;
        if (upper.contains("ARCH_SDXL")) return ModelArchitecture.ARCH_SDXL;
        if (upper.contains("ARCH_SD3")) return ModelArchitecture.ARCH_SD3;
        if (upper.contains("ARCH_FLUX")) return ModelArchitecture.ARCH_FLUX;
        if (upper.contains("ARCH_WAN")) return ModelArchitecture.ARCH_WAN;
        if (upper.contains("ARCH_HUNYUAN")) return ModelArchitecture.ARCH_HUNYUAN;
        if (upper.contains("ARCH_LUMINA2")) return ModelArchitecture.ARCH_LUMINA2;
        return ModelArchitecture.ARCH_UNKNOWN;
    }
}
