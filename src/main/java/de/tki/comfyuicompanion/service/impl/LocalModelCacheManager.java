package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfyuicompanion.domain.ModelInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the persistence and in-memory cache of workflow registry model mappings
 * and cloud-only execution flags.
 */
public class LocalModelCacheManager {

    private static final Logger logger = LoggerFactory.getLogger(LocalModelCacheManager.class);

    private final ObjectMapper objectMapper;
    private final Map<String, List<ModelInfo>> registryModelsCache = new ConcurrentHashMap<>();
    private final Map<String, Boolean> cloudOnlyCache = new ConcurrentHashMap<>();
    private File cacheFile;

    /**
     * Constructs a new cache manager using the given Jackson {@link ObjectMapper}.
     *
     * @param objectMapper the object mapper used for JSON serialization
     */
    public LocalModelCacheManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Initializes the cache from disk storage within the configured application directory.
     *
     * @param appDir the path to the application data directory
     */
    public void initializeCache(String appDir) {
        try {
            File dir = new File(appDir, "templates/comfyui");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            cacheFile = new File(dir, "registry_models_cache.json");

            File cloudCacheFile = new File(dir, "cloud_only_cache.json");
            if (cloudCacheFile.exists()) {
                JsonNode cloudRoot = objectMapper.readTree(cloudCacheFile);
                if (cloudRoot.isObject()) {
                    cloudRoot.fields().forEachRemaining(entry -> {
                        cloudOnlyCache.put(entry.getKey(), entry.getValue().asBoolean());
                    });
                }
            }

            if (cacheFile.exists()) {
                JsonNode root = objectMapper.readTree(cacheFile);
                if (root.isObject()) {
                    root.fields().forEachRemaining(entry -> {
                        List<ModelInfo> list = new ArrayList<>();
                        if (entry.getValue().isArray()) {
                            entry.getValue().forEach(node -> {
                                try {
                                    if (node.isObject()) {
                                        list.add(objectMapper.treeToValue(node, ModelInfo.class));
                                    } else {
                                        ModelInfo info = new ModelInfo();
                                        info.setName(node.asText());
                                        info.setType(inferTypeFromFilename(node.asText()));
                                        list.add(info);
                                    }
                                } catch (Exception ignored) {
                                }
                            });
                        }
                        if (!list.isEmpty() || cloudOnlyCache.getOrDefault(entry.getKey(), false)) {
                            registryModelsCache.put(entry.getKey(), list);
                        }
                    });
                }
            }

            // Also preload from blueprint_scan_results.json if present
            File scanResultsFile = new File(dir, "blueprint_scan_results.json");
            if (scanResultsFile.exists()) {
                JsonNode scanRoot = objectMapper.readTree(scanResultsFile);
                if (scanRoot.isArray()) {
                    for (JsonNode scanNode : scanRoot) {
                        String scanName = scanNode.path("name").asText("");
                        String scanFilename = scanNode.path("filename").asText("");
                        JsonNode reqNode = scanNode.path("requiredModels");
                        if (reqNode.isArray() && reqNode.size() > 0) {
                            List<ModelInfo> list = new ArrayList<>();
                            reqNode.forEach(mNode -> {
                                try {
                                    if (mNode.isObject()) {
                                        list.add(objectMapper.treeToValue(mNode, ModelInfo.class));
                                    }
                                } catch (Exception ignored) {
                                }
                            });
                            if (!list.isEmpty()) {
                                if (!scanName.isEmpty()) registryModelsCache.putIfAbsent(scanName, list);
                                if (!scanFilename.isEmpty()) {
                                    registryModelsCache.putIfAbsent(scanFilename, list);
                                    if (scanFilename.endsWith(".json")) {
                                        registryModelsCache.putIfAbsent(scanFilename.substring(0, scanFilename.length() - 5), list);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            logger.info("ℹ️ [LocalModelCacheManager] Loaded {} cached registry models from disk.", registryModelsCache.size());
        } catch (Exception e) {
            logger.error("⚠️ [LocalModelCacheManager] Failed to initialize cache: {}", e.getMessage());
        }
    }

    /**
     * Persists in-memory caches to disk synchronously.
     */
    public synchronized void saveCache() {
        if (cacheFile == null) return;
        try {
            objectMapper.writeValue(cacheFile, registryModelsCache);
            File cloudCacheFile = new File(cacheFile.getParentFile(), "cloud_only_cache.json");
            objectMapper.writeValue(cloudCacheFile, cloudOnlyCache);
        } catch (Exception e) {
            logger.error("⚠️ [LocalModelCacheManager] Failed to save cache: {}", e.getMessage());
        }
    }

    /**
     * Infers a standard ComfyUI model folder type from the file name.
     *
     * @param filename the file name to inspect
     * @return the inferred model category folder
     */
    public static String inferTypeFromFilename(String filename) {
        if (filename == null) return "checkpoints";
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.contains("vae") || lower.contains("ae.safetensors") || lower.startsWith("ae")) return "vae";
        if (lower.contains("clip") || lower.contains("t5") || lower.contains("qwen") || lower.contains("encoder")
                || lower.contains("gemma") || lower.contains("llama") || lower.contains("text_encoder") || lower.contains("textencoder")) {
            return "text_encoders";
        }
        if (lower.contains("unet") || lower.contains("diffusion") || lower.contains("turbo") || lower.contains("transformer") || lower.contains("dit")) {
            return "diffusion_models";
        }
        if (lower.contains("lora")) return "loras";
        if (lower.contains("controlnet") || lower.contains("control")) return "controlnet";
        return "checkpoints";
    }

    /**
     * Retrieves cached model metadata for a given workflow identifier or title.
     *
     * @param idOrTitle the workflow ID or name
     * @return list of associated {@link ModelInfo} items, or empty list if none found
     */
    public List<ModelInfo> getCachedModels(String idOrTitle) {
        if (idOrTitle == null || idOrTitle.isBlank()) return Collections.emptyList();
        List<ModelInfo> list = registryModelsCache.get(idOrTitle);
        if (list != null && !list.isEmpty()) return list;
        list = registryModelsCache.get(idOrTitle.toLowerCase(Locale.ROOT));
        if (list != null && !list.isEmpty()) return list;
        return Collections.emptyList();
    }

    /**
     * Checks whether an identifier exists in the registry cache.
     *
     * @param id the workflow ID
     * @return true if present
     */
    public boolean contains(String id) {
        return registryModelsCache.containsKey(id);
    }

    /**
     * Returns the cached list of models for an ID.
     *
     * @param id the workflow ID
     * @return model list
     */
    public List<ModelInfo> get(String id) {
        return registryModelsCache.get(id);
    }

    /**
     * Checks if a workflow ID is flagged as cloud only.
     *
     * @param id the workflow ID
     * @return true if cloud only
     */
    public boolean isCloudOnly(String id) {
        return cloudOnlyCache.getOrDefault(id, false);
    }

    /**
     * Stores workflow model metadata and persists to cache file.
     *
     * @param id the workflow ID
     * @param models model information list
     * @param isCloud whether workflow runs in the cloud
     */
    public void putAndSave(String id, List<ModelInfo> models, boolean isCloud) {
        if (id != null) {
            registryModelsCache.put(id, models);
            cloudOnlyCache.put(id, isCloud);
            saveCache();
        }
    }
}
