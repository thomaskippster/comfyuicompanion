package com.thomaskippster.comfyuicompanion.service.inspector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfymodels.service.SafePathValidator;
import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.PathResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise Service for extracting trigger words, activation phrases,
 * and top-ranked training tags from Safetensors model headers.
 */
@Service
public class TriggerWordService {

    private static final Logger logger = LoggerFactory.getLogger(TriggerWordService.class);

    private final SafetensorsInspectorService inspectorService;
    private final ModelArchitectureAnalyzer architectureAnalyzer;
    private final ObjectMapper objectMapper;
    private final ConfigService configService;
    private final PathResolver pathResolver;
    private final SafePathValidator safePathValidator;

    private final Map<String, ModelTriggerInfo> cache = new ConcurrentHashMap<>();

    public record ModelTriggerInfo(
            String modelName,
            String architecture,
            String modelType,
            List<String> triggerWords,
            Map<String, Integer> topTags
    ) {}

    @Autowired
    public TriggerWordService(SafetensorsInspectorService inspectorService,
                              ModelArchitectureAnalyzer architectureAnalyzer,
                              ObjectMapper objectMapper,
                              @Autowired(required = false) ConfigService configService,
                              @Autowired(required = false) PathResolver pathResolver,
                              @Autowired(required = false) SafePathValidator safePathValidator) {
        this.inspectorService = inspectorService;
        this.architectureAnalyzer = architectureAnalyzer;
        this.objectMapper = objectMapper;
        this.configService = configService;
        this.pathResolver = pathResolver;
        this.safePathValidator = safePathValidator != null ? safePathValidator : new SafePathValidator();
    }

    /**
     * Extracts trigger words and metadata from the given safetensors model file.
     */
    public ModelTriggerInfo extractTriggerInfo(Path modelPath) throws IOException {
        String cacheKey = modelPath.toAbsolutePath().normalize().toString();
        ModelTriggerInfo cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        String filename = modelPath.getFileName().toString();
        if (!filename.endsWith(".safetensors") && !filename.endsWith(".sft")) {
            return new ModelTriggerInfo(filename, "UNKNOWN", "CHECKPOINT", Collections.emptyList(), Collections.emptyMap());
        }

        String headerJson = inspectorService.readHeaderJson(modelPath);
        ModelMetadata meta = architectureAnalyzer.analyze(headerJson);
        SafetensorsHeader header = inspectorService.parseHeader(headerJson);

        List<String> triggers = new ArrayList<>();
        Map<String, Integer> tagMap = new LinkedHashMap<>();

        Map<String, String> rawMeta = header.getMetadata();
        if (rawMeta != null) {
            // 1. Explicit trigger phrase
            String triggerPhrase = rawMeta.get("modelspec.trigger_phrase");
            if (triggerPhrase != null && !triggerPhrase.isBlank()) {
                triggers.add(triggerPhrase.trim());
            }

            // 2. Tag frequency analysis
            String tagFrequency = rawMeta.get("ss_tag_frequency");
            if (tagFrequency != null && !tagFrequency.isBlank()) {
                try {
                    JsonNode rootNode = objectMapper.readTree(tagFrequency);
                    if (rootNode.isObject()) {
                        Iterator<Map.Entry<String, JsonNode>> datasets = rootNode.fields();
                        while (datasets.hasNext()) {
                            JsonNode datasetTags = datasets.next().getValue();
                            if (datasetTags.isObject()) {
                                Iterator<Map.Entry<String, JsonNode>> tagEntries = datasetTags.fields();
                                while (tagEntries.hasNext()) {
                                    Map.Entry<String, JsonNode> entry = tagEntries.next();
                                    tagMap.merge(entry.getKey(), entry.getValue().asInt(0), Integer::sum);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Failed to parse ss_tag_frequency for {}: {}", filename, e.getMessage());
                }
            }
        }

        // Add detected trigger words from ModelMetadata if not already included
        if (meta.getTriggerWords() != null) {
            for (String tw : meta.getTriggerWords()) {
                if (!triggers.contains(tw)) {
                    triggers.add(tw);
                }
            }
        }

        // Sort top tags by frequency descending
        Map<String, Integer> sortedTags = new LinkedHashMap<>();
        tagMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(20)
                .forEach(e -> sortedTags.put(e.getKey(), e.getValue()));

        ModelTriggerInfo result = new ModelTriggerInfo(
                filename,
                meta.getArchitectureType() != null ? meta.getArchitectureType() : "SD1.5",
                meta.getModelType() != null ? meta.getModelType() : "CHECKPOINT",
                Collections.unmodifiableList(triggers),
                Collections.unmodifiableMap(sortedTags)
        );

        cache.put(cacheKey, result);
        return result;
    }

    /**
     * Resolves a model name to its local path and extracts trigger info.
     */
    public Optional<ModelTriggerInfo> findModelTriggerInfo(String modelName) {
        if (modelName == null || modelName.isBlank() || configService == null) {
            return Optional.empty();
        }

        String safeName = safePathValidator.sanitizeFilename(modelName);
        Path modelsBase = pathResolver != null 
                ? pathResolver.resolve(configService.getModelsPath())
                : Paths.get(configService.getModelsPath());

        // Search in modelsBase recursively
        try (var walk = Files.walk(modelsBase, 4)) {
            Optional<Path> found = walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(safeName))
                    .findFirst();

            if (found.isPresent()) {
                return Optional.of(extractTriggerInfo(found.get()));
            }
        } catch (Exception e) {
            logger.error("Error searching model file for trigger info: {}", e.getMessage());
        }

        return Optional.empty();
    }
}
