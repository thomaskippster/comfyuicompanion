package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfyuicompanion.domain.ModelArchitecture;
import de.tki.comfyuicompanion.service.IModelArchitectureService.ModelDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Component handling caching and JSON persistence for model architectures,
 * resolved inference defaults, and blueprint scan results.
 */
@Component
public class BlueprintCacheStore {
    private static final Logger logger = LoggerFactory.getLogger(BlueprintCacheStore.class);
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Saves the architecture lookup cache to disk.
     *
     * @param baseDir the base configuration directory
     * @param architectureCache map of model filenames to architectures
     */
    public void saveArchitectureCache(File baseDir, Map<String, ModelArchitecture> architectureCache) {
        File targetFile = new File(baseDir, "templates/comfyui/model_architectures.json");
        try {
            ensureParentDirs(targetFile);
            mapper.writerWithDefaultPrettyPrinter().writeValue(targetFile, architectureCache);
            logger.info("💾 [BlueprintCacheStore] Wrote architecture cache to: {}", targetFile.getAbsolutePath());
        } catch (Exception e) {
            logger.error("❌ [BlueprintCacheStore] Failed to write architecture cache: {}", e.getMessage());
        }
    }

    /**
     * Loads the architecture cache from disk into the provided map.
     *
     * @param baseDir base configuration directory
     * @param targetMap destination map
     */
    public void loadArchitectureCache(File baseDir, Map<String, ModelArchitecture> targetMap) {
        File targetFile = new File(baseDir, "templates/comfyui/model_architectures.json");
        if (!targetFile.exists()) {
            return;
        }
        try {
            JsonNode root = mapper.readTree(targetFile);
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                try {
                    ModelArchitecture arch = ModelArchitecture.valueOf(field.getValue().asText());
                    targetMap.put(field.getKey(), arch);
                } catch (IllegalArgumentException e) {
                    logger.warn("⚠️ [BlueprintCacheStore] Unknown architecture in cache file: {}", field.getValue().asText());
                }
            }
            logger.info("📂 [BlueprintCacheStore] Loaded {} cached model architectures.", targetMap.size());
        } catch (Exception e) {
            logger.error("❌ [BlueprintCacheStore] Failed to load architecture cache: {}", e.getMessage());
        }
    }

    /**
     * Saves resolved defaults to disk.
     *
     * @param baseDir base configuration directory
     * @param resolvedDefaultsMap map of model names to defaults
     */
    public void saveResolvedDefaults(File baseDir, Map<String, ModelDefaults> resolvedDefaultsMap) {
        File targetFile = new File(baseDir, "templates/comfyui/model_resolved_defaults.json");
        try {
            ensureParentDirs(targetFile);
            mapper.writerWithDefaultPrettyPrinter().writeValue(targetFile, resolvedDefaultsMap);
            logger.info("💾 [BlueprintCacheStore] Wrote blueprint resolved model defaults to: {}", targetFile.getAbsolutePath());
        } catch (Exception e) {
            logger.error("❌ [BlueprintCacheStore] Failed to write model resolved defaults: {}", e.getMessage());
        }
    }

    /**
     * Loads resolved model defaults from disk into target map.
     *
     * @param baseDir base configuration directory
     * @param targetMap destination map
     */
    public void loadResolvedDefaults(File baseDir, Map<String, ModelDefaults> targetMap) {
        File targetFile = new File(baseDir, "templates/comfyui/model_resolved_defaults.json");
        if (!targetFile.exists()) {
            return;
        }
        try {
            JsonNode root = mapper.readTree(targetFile);
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey().toLowerCase().trim();
                JsonNode val = field.getValue();
                ModelDefaults defaults = new ModelDefaults(
                        val.path("vaeName").asText(null),
                        val.path("clipType").asText(null),
                        val.path("scheduler").asText(null),
                        val.path("samplerName").asText(null),
                        val.path("architecture").asText("ARCH_UNKNOWN")
                );
                targetMap.put(key, defaults);
            }
            logger.info("📂 [BlueprintCacheStore] Loaded {} resolved model defaults.", targetMap.size());
        } catch (Exception e) {
            logger.error("❌ [BlueprintCacheStore] Failed to load model resolved defaults: {}", e.getMessage());
        }
    }

    /**
     * Saves blueprint scan results to disk.
     *
     * @param baseDir base configuration directory
     * @param scanResults scan results list
     */
    public void saveBlueprintScanResults(File baseDir, List<Map<String, Object>> scanResults) {
        File scanResultsFile = new File(baseDir, "templates/comfyui/blueprint_scan_results.json");
        try {
            ensureParentDirs(scanResultsFile);
            mapper.writerWithDefaultPrettyPrinter().writeValue(scanResultsFile, scanResults);
            logger.info("💾 [BlueprintCacheStore] Wrote blueprint scan results to cache: {}", scanResultsFile.getAbsolutePath());
        } catch (Exception e) {
            logger.error("❌ [BlueprintCacheStore] Failed to write blueprint scan results: {}", e.getMessage());
        }
    }

    /**
     * Loads blueprint scan results from disk into target list.
     *
     * @param baseDir base configuration directory
     * @param targetList destination list
     */
    public void loadBlueprintScanResults(File baseDir, List<Map<String, Object>> targetList) {
        File targetFile = new File(baseDir, "templates/comfyui/blueprint_scan_results.json");
        if (!targetFile.exists()) {
            return;
        }
        try {
            JsonNode root = mapper.readTree(targetFile);
            List<Map<String, Object>> list = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode node : root) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> entry = mapper.convertValue(node, Map.class);
                    list.add(entry);
                }
            }
            targetList.clear();
            targetList.addAll(list);
            logger.info("📂 [BlueprintCacheStore] Loaded {} scanned blueprint results from cache.", targetList.size());
        } catch (Exception e) {
            logger.error("❌ [BlueprintCacheStore] Failed to load scanned blueprint results: {}", e.getMessage());
        }
    }

    private void ensureParentDirs(File file) {
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
    }
}
