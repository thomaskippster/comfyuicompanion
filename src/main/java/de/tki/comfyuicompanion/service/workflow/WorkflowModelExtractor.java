package de.tki.comfyuicompanion.service.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.impl.CivitaiLocalModelScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enterprise service component extracting all concrete model dependencies
 * (.safetensors, .gguf, .ckpt) directly from ComfyUI workflow JSON structures.
 * Supports both LiteGraph canvas schemas and ComfyUI API prompt graph representations,
 * including subgraphs and Comfy.org node properties.
 */
@Component
public class WorkflowModelExtractor {

    private static final Logger log = LoggerFactory.getLogger(WorkflowModelExtractor.class);

    private static final Pattern MODEL_FILE_PATTERN = Pattern.compile(
            "([a-zA-Z0-9_\\-\\.\\/\\\\\\:]+\\.(?:safetensors|sft|ckpt|pth|pt|bin|gguf))",
            Pattern.CASE_INSENSITIVE
    );

    private final CivitaiLocalModelScanner localModelScanner;

    @Autowired
    public WorkflowModelExtractor(@Autowired(required = false) CivitaiLocalModelScanner localModelScanner) {
        this.localModelScanner = localModelScanner;
    }

    /**
     * Extracts all unique model dependencies referenced across all nodes in the given workflow JSON.
     *
     * @param workflowRoot the parsed JSON root of the workflow
     * @return list of populated and locally validated {@link ModelInfo} records
     */
    public List<ModelInfo> extractModels(JsonNode workflowRoot) {
        if (workflowRoot == null || workflowRoot.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, ModelInfo> discoveredModels = new LinkedHashMap<>();

        if (workflowRoot.has("nodes") && workflowRoot.get("nodes").isArray()) {
            extractFromLiteGraph(workflowRoot.get("nodes"), discoveredModels);
        } else {
            extractFromApiGraph(workflowRoot, discoveredModels);
        }

        // Scan subgraphs in definitions.subgraphs (standard in modern ComfyUI workflows)
        JsonNode subgraphs = workflowRoot.path("definitions").path("subgraphs");
        if (subgraphs.isArray()) {
            for (JsonNode sg : subgraphs) {
                if (sg.has("nodes") && sg.get("nodes").isArray()) {
                    extractFromLiteGraph(sg.get("nodes"), discoveredModels);
                }
            }
        } else if (subgraphs.isObject()) {
            subgraphs.elements().forEachRemaining(sg -> {
                if (sg.has("nodes") && sg.get("nodes").isArray()) {
                    extractFromLiteGraph(sg.get("nodes"), discoveredModels);
                }
            });
        }

        List<ModelInfo> result = new ArrayList<>(discoveredModels.values());
        log.debug("Extracted {} concrete model dependencies from workflow", result.size());
        return result;
    }

    private void extractFromLiteGraph(JsonNode nodesArray, Map<String, ModelInfo> discovered) {
        for (JsonNode node : nodesArray) {
            String nodeType = node.path("type").asText("");
            String nodeTitle = node.path("title").asText("");

            // 1. Inspect properties.models (Comfy.org modern metadata with download URLs)
            JsonNode modelsProp = node.path("properties").path("models");
            if (modelsProp.isArray()) {
                for (JsonNode m : modelsProp) {
                    String mName = m.path("name").asText("");
                    String mUrl = m.path("url").asText(null);
                    String mDir = m.path("directory").asText("");
                    if (!mName.isBlank()) {
                        String simpleName = extractSimpleFilename(mName);
                        String folder = !mDir.isBlank() ? mDir : determineModelFolder(nodeType, nodeTitle, simpleName, mUrl);
                        registerDiscoveredModel(simpleName, folder, mUrl, discovered);
                    }
                }
            }

            // 2. Inspect widgets_values array
            JsonNode widgets = node.get("widgets_values");
            if (widgets != null && widgets.isArray()) {
                for (JsonNode w : widgets) {
                    scanTextForModels(w.asText(""), nodeType, nodeTitle, discovered);
                }
            }

            // 3. Inspect widgets_values_named map
            JsonNode namedWidgets = node.get("widgets_values_named");
            if (namedWidgets != null && namedWidgets.isObject()) {
                namedWidgets.properties().forEach(entry ->
                        scanTextForModels(entry.getValue().asText(""), nodeType, nodeTitle, discovered)
                );
            }

            // 4. Inspect inline subgraphs if nested on node
            if (node.has("subgraph") && node.get("subgraph").has("nodes") && node.get("subgraph").get("nodes").isArray()) {
                extractFromLiteGraph(node.get("subgraph").get("nodes"), discovered);
            }
        }
    }

    private void extractFromApiGraph(JsonNode apiRoot, Map<String, ModelInfo> discovered) {
        apiRoot.properties().forEach(entry -> {
            JsonNode nodeObj = entry.getValue();
            if (nodeObj != null && nodeObj.isObject()) {
                String nodeType = nodeObj.path("class_type").asText("");
                String nodeTitle = nodeObj.path("_meta").path("title").asText("");
                JsonNode inputs = nodeObj.get("inputs");
                if (inputs != null && inputs.isObject()) {
                    inputs.properties().forEach(inputEntry ->
                            scanTextForModels(inputEntry.getValue().asText(""), nodeType, nodeTitle, discovered)
                    );
                }
            }
        });
    }

    private void scanTextForModels(String text, String nodeType, String nodeTitle, Map<String, ModelInfo> discovered) {
        if (text == null || text.isBlank() || text.length() > 50000) {
            return;
        }

        Matcher matcher = MODEL_FILE_PATTERN.matcher(text);
        while (matcher.find()) {
            String rawMatch = matcher.group(1).trim();
            String simpleFilename = extractSimpleFilename(rawMatch);

            if (simpleFilename.equalsIgnoreCase("workflow.json")
                    || simpleFilename.endsWith(".png")
                    || simpleFilename.endsWith(".jpg")
                    || simpleFilename.endsWith(".zip")) {
                continue;
            }

            String downloadUrl = null;
            if (rawMatch.startsWith("http://") || rawMatch.startsWith("https://")) {
                downloadUrl = rawMatch;
            }

            String folder = determineModelFolder(nodeType, nodeTitle, simpleFilename, downloadUrl);
            registerDiscoveredModel(simpleFilename, folder, downloadUrl, discovered);
        }
    }

    private void registerDiscoveredModel(String simpleFilename, String folder, String downloadUrl, Map<String, ModelInfo> discovered) {
        String resolvedUrl = (downloadUrl != null && !downloadUrl.isBlank())
                ? downloadUrl
                : resolveFallbackDownloadUrl(simpleFilename, folder);
        if (resolvedUrl != null && resolvedUrl.contains("huggingface.co") && resolvedUrl.contains("/blob/")) {
            resolvedUrl = resolvedUrl.replace("/blob/", "/resolve/");
        }
        ModelInfo existing = discovered.get(simpleFilename);
        if (existing == null) {
            ModelInfo info = new ModelInfo(folder, simpleFilename, resolvedUrl);
            info.setFilename(simpleFilename);
            if (localModelScanner != null) {
                localModelScanner.checkAndSetModelStatus(info);
            }
            if (info.getSize() == null || "Unknown".equalsIgnoreCase(info.getSize()) || "Workflow JSON".equalsIgnoreCase(info.getSize())) {
                String estSize = estimateKnownModelSize(simpleFilename);
                if (estSize != null) info.setSize(estSize);
            }
            discovered.put(simpleFilename, info);
        } else {
            if (resolvedUrl != null && (existing.getUrl() == null || existing.getUrl().isBlank())) {
                existing.setUrl(resolvedUrl);
            }
            if ("checkpoints".equals(existing.getType()) && !"checkpoints".equals(folder)) {
                existing.setType(folder);
                if (localModelScanner != null) {
                    localModelScanner.checkAndSetModelStatus(existing);
                }
            }
            if (existing.getSize() == null || "Unknown".equalsIgnoreCase(existing.getSize()) || "Workflow JSON".equalsIgnoreCase(existing.getSize())) {
                String estSize = estimateKnownModelSize(simpleFilename);
                if (estSize != null) existing.setSize(estSize);
            }
        }
    }

    /**
     * Estimates a realistic model size string based on declarative rules in {@link ModelFallbackCatalog}.
     *
     * @param filename model filename
     * @return estimated formatted size (e.g., "2.5 GB") or null if uncategorized
     */
    public static String estimateKnownModelSize(String filename) {
        return ModelFallbackCatalog.estimateSize(filename);
    }

    /**
     * Resolves an authoritative Hugging Face download URL for well-known ComfyUI base models,
     * preprocessors, and text encoders via {@link ModelFallbackCatalog}.
     *
     * @param filename model filename
     * @param folder   target model folder
     * @return resolved remote URL or null if unknown
     */
    public static String resolveFallbackDownloadUrl(String filename, String folder) {
        return ModelFallbackCatalog.resolveUrl(filename);
    }

    private String extractSimpleFilename(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) {
            return "";
        }
        String cleaned = pathOrUrl.trim();
        int queryIdx = cleaned.indexOf('?');
        if (queryIdx >= 0) {
            cleaned = cleaned.substring(0, queryIdx);
        }
        int lastSlash = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'));
        return lastSlash >= 0 ? cleaned.substring(lastSlash + 1) : cleaned;
    }

    /**
     * Semantically determines the appropriate ComfyUI storage folder for an extracted model,
     * considering node semantics, filename tokens, and source download URLs.
     *
     * @param nodeType    the type name of the node hosting the model
     * @param nodeTitle   optional user title of the node
     * @param filename    the extracted model filename
     * @param downloadUrl optional URL source for the model
     * @return folder name (e.g., 'unet', 'clip', 'vae', 'checkpoints', 'loras')
     */
    public String determineModelFolder(String nodeType, String nodeTitle, String filename, String downloadUrl) {
        if (downloadUrl != null && !downloadUrl.isBlank()) {
            String lowerUrl = downloadUrl.toLowerCase(Locale.ROOT);
            if (lowerUrl.contains("/diffusion_models/") || lowerUrl.contains("/diffusion/")) return "unet";
            if (lowerUrl.contains("/vae/")) return "vae";
            if (lowerUrl.contains("/loras/") || lowerUrl.contains("/lora/")) return "loras";
            if (lowerUrl.contains("/text_encoders/") || lowerUrl.contains("/clip/")) return "clip";
            if (lowerUrl.contains("/checkpoints/")) return "checkpoints";
        }
        return determineModelFolder(nodeType, nodeTitle, filename);
    }

    /**
     * Semantically determines the appropriate ComfyUI storage folder for an extracted model.
     *
     * @param nodeType  the type name of the node hosting the model
     * @param nodeTitle optional user title of the node
     * @param filename  the extracted model filename
     * @return folder name (e.g., 'unet', 'clip', 'vae', 'checkpoints', 'loras')
     */
    public String determineModelFolder(String nodeType, String nodeTitle, String filename) {
        String nt = nodeType != null ? nodeType.toLowerCase(Locale.ROOT) : "";
        String fn = filename != null ? filename.toLowerCase(Locale.ROOT) : "";
        String ttl = nodeTitle != null ? nodeTitle.toLowerCase(Locale.ROOT) : "";

        // 1. Prioritize authoritative node type semantics
        if (nt.contains("vaeloader") || nt.equals("vae") || nt.contains("autoencoder")) {
            return "vae";
        }
        if (nt.contains("lora") || nt.contains("locon") || nt.contains("lycoris")) {
            return "loras";
        }
        if (nt.contains("controlnet") || nt.contains("t2i_adapter")) {
            return "controlnet";
        }
        if (nt.contains("cliploader") || nt.contains("text_encoder") || nt.contains("t5loader")) {
            return "clip";
        }
        if (nt.contains("unetloader") || nt.contains("diffusionloader") || nt.contains("unet")) {
            return "unet";
        }
        if (nt.contains("checkpointloader")) {
            return "checkpoints";
        }

        // 2. Prioritize filename markers for composite/subgraph nodes
        if (fn.equals("ae.safetensors") || fn.startsWith("ae.") || fn.contains("vae") || fn.contains("autoencoder")
                || fn.contains("decoder") || fn.contains("encoder_small")) {
            return "vae";
        }
        if (fn.contains("lora") || fn.contains("locon") || fn.contains("lycoris") || fn.contains("darkbrush")) {
            return "loras";
        }
        if (fn.contains("controlnet") || fn.contains("t2i_adapter")) {
            return "controlnet";
        }
        if (fn.contains("prompt_upsampler") || fn.contains("pe_t2i") || fn.contains("pe_i2i")
                || fn.contains("pe_") || fn.contains("vl_")
                || fn.contains("clip") || fn.contains("t5") || fn.contains("text_encoder")
                || fn.contains("llama") || fn.contains("bert") || fn.contains("gemma")) {
            return "clip";
        }
        if (fn.contains("unet") || fn.contains("diffusion") || fn.contains("dit")
                || fn.contains("transformer") || fn.contains("base") || fn.contains("klein")
                || fn.contains("turbo") || fn.contains("flux") || fn.contains("krea") || fn.contains("z_image")
                || fn.contains("convrot")) {
            return "unet";
        }
        if (fn.contains("qwen")) {
            return "clip";
        }

        // 3. Fallback to combined node heuristics
        String combined = (nt + " " + ttl + " " + fn);
        if (combined.contains("vae")) return "vae";
        if (combined.contains("lora")) return "loras";
        if (combined.contains("clip")) return "clip";
        if (combined.contains("unet")) return "unet";

        return "checkpoints";
    }
}
