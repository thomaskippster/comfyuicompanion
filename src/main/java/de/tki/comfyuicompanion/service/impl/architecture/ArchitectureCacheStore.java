package de.tki.comfyuicompanion.service.impl.architecture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfyuicompanion.domain.ModelArchitecture;
import de.tki.comfyuicompanion.service.IModelArchitectureService.ModelDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * File based persistence for the model architecture caches (detected architectures,
 * resolved model defaults and blueprint scan results). All files live in
 * {@code <appData>/templates/comfyui}.
 */
public class ArchitectureCacheStore {
    private static final Logger logger = LoggerFactory.getLogger(ArchitectureCacheStore.class);

    private static final String SUB_DIR = "templates/comfyui";
    private static final String DEFAULTS_FILE = "model_resolved_defaults.json";
    private static final String ARCHITECTURES_FILE = "model_architectures.json";
    private static final String SCAN_RESULTS_FILE = "blueprint_scan_results.json";

    private final Supplier<String> baseDirSupplier;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Creates a store.
     *
     * @param baseDirSupplier supplies the application data directory lazily on every access,
     *                        so that runtime changes of the directory are honoured
     */
    public ArchitectureCacheStore(Supplier<String> baseDirSupplier) {
        this.baseDirSupplier = baseDirSupplier;
    }

    /**
     * Resolves the template directory that hosts all cache files.
     *
     * @return the (possibly not yet existing) {@code templates/comfyui} directory
     */
    public File templatesDir() {
        return new File(baseDirSupplier.get(), SUB_DIR);
    }

    /**
     * Writes the resolved model defaults to disk. Failures are logged, never thrown.
     *
     * @param defaults the defaults to persist
     */
    public void saveResolvedDefaults(Map<String, ModelDefaults> defaults) {
        write(new File(templatesDir(), DEFAULTS_FILE), defaults, "[ArchitectureService]", "resolved model defaults");
    }

    /**
     * Loads resolved model defaults from disk into the given map (keys are lower-cased and trimmed).
     *
     * @param target map receiving the loaded entries
     */
    public void loadResolvedDefaults(Map<String, ModelDefaults> target) {
        File file = new File(templatesDir(), DEFAULTS_FILE);
        if (!file.exists()) {
            return;
        }
        try {
            Iterator<Map.Entry<String, JsonNode>> fields = mapper.readTree(file).fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode val = field.getValue();
                target.put(field.getKey().toLowerCase().trim(), new ModelDefaults(
                        val.path("vaeName").asText(null),
                        val.path("clipType").asText(null),
                        val.path("scheduler").asText(null),
                        val.path("samplerName").asText(null),
                        val.path("architecture").asText("ARCH_UNKNOWN")));
            }
            logger.info("📂 [ArchitectureService] Loaded {} resolved model defaults.", target.size());
        } catch (Exception e) {
            logger.error("❌ [ArchitectureService] Failed to load model resolved defaults: {}", e.getMessage());
        }
    }

    /**
     * Writes the detected architecture cache to disk. Failures are logged, never thrown.
     *
     * @param cache file name to architecture mapping
     */
    public void saveArchitectureCache(Map<String, ModelArchitecture> cache) {
        write(new File(templatesDir(), ARCHITECTURES_FILE), cache, "[ModelArchitectureService]", "architecture cache");
    }

    /**
     * Loads the architecture cache from disk into the given map; unknown enum values are skipped.
     *
     * @param target map receiving the loaded entries
     */
    public void loadArchitectureCache(Map<String, ModelArchitecture> target) {
        File file = new File(templatesDir(), ARCHITECTURES_FILE);
        if (!file.exists()) {
            return;
        }
        try {
            Iterator<Map.Entry<String, JsonNode>> fields = mapper.readTree(file).fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String val = field.getValue().asText();
                try {
                    target.put(field.getKey(), ModelArchitecture.valueOf(val));
                } catch (IllegalArgumentException e) {
                    logger.error("⚠️ [ModelArchitectureService] Unknown architecture in cache file: {}", val);
                }
            }
            logger.info("📂 [ModelArchitectureService] Loaded {} cached model architectures.", target.size());
        } catch (Exception e) {
            logger.error("❌ [ModelArchitectureService] Failed to load architecture cache: {}", e.getMessage());
        }
    }

    /**
     * Persists the blueprint scan results.
     *
     * @param scanResults scan result entries
     * @return {@code true} if the file was written successfully
     */
    public boolean saveScanResults(List<Map<String, Object>> scanResults) {
        return write(new File(templatesDir(), SCAN_RESULTS_FILE), scanResults, "[ArchitectureService]", "blueprint scan results");
    }

    /**
     * Loads the persisted blueprint scan results.
     *
     * @return the loaded entries, or an empty list if the file is missing or unreadable
     */
    public List<Map<String, Object>> loadScanResults() {
        List<Map<String, Object>> list = new ArrayList<>();
        File file = new File(templatesDir(), SCAN_RESULTS_FILE);
        if (!file.exists()) {
            return list;
        }
        try {
            JsonNode root = mapper.readTree(file);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> entry = mapper.convertValue(node, Map.class);
                    list.add(entry);
                }
            }
            logger.info("📂 [ModelArchitectureService] Loaded {} scanned blueprint results from cache.", list.size());
        } catch (Exception e) {
            logger.error("❌ [ModelArchitectureService] Failed to load scanned blueprint results: {}", e.getMessage());
            list.clear();
        }
        return list;
    }

    private boolean write(File target, Object value, String logTag, String description) {
        try {
            File parentDir = target.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(target, value);
            logger.info("💾 {} Wrote {} to: {}", logTag, description, target.getAbsolutePath());
            return true;
        } catch (Exception e) {
            logger.error("❌ {} Failed to write {}: {}", logTag, description, e.getMessage());
            return false;
        }
    }
}
