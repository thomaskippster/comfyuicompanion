package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfyuicompanion.service.provisioning.CustomNodeResolverService.CustomNodePackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles fetching, local disk caching, and parsing of ComfyUI-Manager's
 * extension-node-map.json and custom-node-list.json databases.
 */
@Component
public class CustomNodeDatabaseLoader {

    private static final Logger logger = LoggerFactory.getLogger(CustomNodeDatabaseLoader.class);

    private static final String EXTENSION_NODE_MAP_URL =
            "https://raw.githubusercontent.com/ltdrdata/ComfyUI-Manager/main/extension-node-map.json";
    private static final String CUSTOM_NODE_LIST_URL =
            "https://raw.githubusercontent.com/ltdrdata/ComfyUI-Manager/main/custom-node-list.json";

    private final ObjectMapper objectMapper;
    private final Path cacheDir;
    private final HttpClient httpClient;

    public CustomNodeDatabaseLoader() {
        this(new ObjectMapper(), Paths.get(System.getProperty("user.home", "."), ".comfyuicompanion", "cache", "manager"));
    }

    public CustomNodeDatabaseLoader(ObjectMapper objectMapper, Path cacheDir) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.cacheDir = cacheDir;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        try {
            Files.createDirectories(this.cacheDir);
        } catch (Exception e) {
            logger.warn("Could not create manager database cache directory {}: {}", cacheDir, e.getMessage());
        }
    }

    /**
     * Loads node mappings by checking local ComfyUI files, disk cache, online sources, and built-in fallbacks.
     *
     * @param comfyUiPath root path of ComfyUI installation, if available
     * @return map from lowercase node class name to CustomNodePackage
     */
    public Map<String, CustomNodePackage> loadNodeClassMap(String comfyUiPath) {
        Map<String, CustomNodePackage> result = new ConcurrentHashMap<>();

        // 1. Seed with core built-in fallbacks
        seedFallbackNodes(result);

        // 2. Locate or download databases
        Path extMapFile = resolveOrFetchDatabase(comfyUiPath, "extension-node-map.json", EXTENSION_NODE_MAP_URL);
        Path nodeListFile = resolveOrFetchDatabase(comfyUiPath, "custom-node-list.json", CUSTOM_NODE_LIST_URL);

        // 3. Parse custom-node-list.json to get metadata (pip packages, git URLs)
        Map<String, PackageMetadata> metadataMap = parseCustomNodeList(nodeListFile);

        // 4. Parse extension-node-map.json and link to metadata
        if (extMapFile != null && Files.exists(extMapFile)) {
            parseExtensionNodeMap(extMapFile, metadataMap, result);
        }

        logger.info("Custom node database loaded with {} node class mappings", result.size());
        return result;
    }

    private Path resolveOrFetchDatabase(String comfyUiPath, String fileName, String remoteUrl) {
        // A) Check local ComfyUI-Manager directory
        if (comfyUiPath != null && !comfyUiPath.isBlank()) {
            Path localManagerFile = Paths.get(comfyUiPath, "custom_nodes", "ComfyUI-Manager", fileName);
            if (Files.exists(localManagerFile) && Files.isRegularFile(localManagerFile)) {
                logger.debug("Found local ComfyUI-Manager file: {}", localManagerFile);
                return localManagerFile;
            }
        }

        // B) Check cached file
        Path cachedFile = cacheDir.resolve(fileName);
        boolean cacheValid = false;
        if (Files.exists(cachedFile)) {
            try {
                long ageHours = Duration.between(Files.getLastModifiedTime(cachedFile).toInstant(), Instant.now()).toHours();
                if (ageHours < 24 * 7 && Files.size(cachedFile) > 0) {
                    cacheValid = true;
                }
            } catch (Exception ignored) {}
        }

        if (cacheValid) {
            return cachedFile;
        }

        // C) Fetch online and cache
        try {
            logger.info("Fetching ComfyUI-Manager database from {}...", remoteUrl);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(remoteUrl))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() == 200) {
                Files.copy(response.body(), cachedFile, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Successfully cached {}", cachedFile);
                return cachedFile;
            }
        } catch (Exception e) {
            logger.warn("Failed to fetch remote database {}: {}. Will use existing cache or fallbacks.", remoteUrl, e.getMessage());
        }

        return Files.exists(cachedFile) ? cachedFile : null;
    }

    private Map<String, PackageMetadata> parseCustomNodeList(Path file) {
        Map<String, PackageMetadata> map = new HashMap<>();
        if (file == null || !Files.exists(file)) return map;

        try {
            JsonNode root = objectMapper.readTree(file.toFile());
            JsonNode customNodes = root.has("custom_nodes") ? root.get("custom_nodes") : root;
            if (customNodes.isArray()) {
                for (JsonNode entry : customNodes) {
                    String title = entry.path("title").asText("");
                    String name = entry.path("name").asText(title);
                    String reference = entry.path("reference").asText("");
                    if (reference.isBlank() && entry.has("files") && entry.get("files").isArray() && entry.get("files").size() > 0) {
                        reference = entry.get("files").get(0).asText("");
                    }
                    List<String> pipList = new ArrayList<>();
                    if (entry.has("pip") && entry.get("pip").isArray()) {
                        for (JsonNode p : entry.get("pip")) pipList.add(p.asText());
                    }

                    PackageMetadata meta = new PackageMetadata(name, reference, title, pipList);
                    if (!name.isBlank()) map.put(name.toLowerCase(Locale.ROOT), meta);
                    if (!reference.isBlank()) map.put(reference.toLowerCase(Locale.ROOT), meta);
                    if (!title.isBlank()) map.put(title.toLowerCase(Locale.ROOT), meta);
                }
            }
        } catch (Exception e) {
            logger.warn("Could not parse custom-node-list.json: {}", e.getMessage());
        }
        return map;
    }

    private void parseExtensionNodeMap(Path file, Map<String, PackageMetadata> metadataMap, Map<String, CustomNodePackage> target) {
        try {
            JsonNode root = objectMapper.readTree(file.toFile());
            if (!root.isObject()) return;

            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String repoKey = entry.getKey();
                JsonNode nodesArray = entry.getValue();

                String gitUrl = repoKey;
                String packageName = repoKey;
                List<String> pipDeps = List.of();

                // Check if repoKey matches custom-node-list metadata
                PackageMetadata meta = metadataMap.get(repoKey.toLowerCase(Locale.ROOT));
                if (meta != null) {
                    if (!meta.gitUrl().isBlank()) gitUrl = meta.gitUrl();
                    if (!meta.name().isBlank()) packageName = meta.name();
                    pipDeps = meta.pipList();
                } else if (!gitUrl.startsWith("http://") && !gitUrl.startsWith("https://")) {
                    gitUrl = "https://github.com/comfyanonymous/" + repoKey;
                }

                // Extract all node names provided by this repo
                List<String> providedNodes = new ArrayList<>();
                if (nodesArray.isArray()) {
                    for (JsonNode n : nodesArray) {
                        if (n.isArray() && n.size() > 0) {
                            providedNodes.add(n.get(0).asText());
                        } else if (n.isTextual()) {
                            providedNodes.add(n.asText());
                        }
                    }
                }

                CustomNodePackage pkg = new CustomNodePackage(packageName, gitUrl, providedNodes, pipDeps);
                for (String nodeClass : providedNodes) {
                    if (!nodeClass.isBlank()) {
                        target.put(nodeClass.toLowerCase(Locale.ROOT), pkg);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Could not parse extension-node-map.json: {}", e.getMessage());
        }
    }

    private void seedFallbackNodes(Map<String, CustomNodePackage> target) {
        addFallback(target, "ImpactPack", "https://github.com/ltdrdata/ComfyUI-Impact-Pack",
                List.of("FaceDetailer", "BboxDetectorSEGS", "SAMDetectorCombined", "DetailerForEach", "ToDetailerPipe"));
        addFallback(target, "UltimateSDUpscale", "https://github.com/ssitu/ComfyUI_UltimateSDUpscale",
                List.of("UltimateSDUpscale", "UltimateSDUpscaleNoUpscale"));
        addFallback(target, "ReactorFaceSwap", "https://github.com/Gourieff/comfyui-reactor-node",
                List.of("ReActorFaceSwap", "ReActorFastFaceSwap", "ReActorRestoreFace"));
        addFallback(target, "AdvancedControlNet", "https://github.com/Kosinkadink/ComfyUI-Advanced-ControlNet",
                List.of("ControlNetApplyAdvanced", "TimestepKeyframe", "ControlNetLoaderAdvanced"));
        addFallback(target, "IPAdapterPlus", "https://github.com/cubiq/ComfyUI_IPAdapter_plus",
                List.of("IPAdapterUnifiedLoader", "IPAdapterApply", "IPAdapterFaceID", "IPAdapterAdvanced"));
        addFallback(target, "AnimateDiffEvolved", "https://github.com/Kosinkadink/ComfyUI-AnimateDiff-Evolved",
                List.of("AnimateDiffLoaderWithContext", "ADE_AnimateDiffLoaderWithContext", "ADE_StandardUniformViewOptions"));
        addFallback(target, "Comfyroll", "https://github.com/Suzie1/ComfyUI_Comfyroll_CustomNodes",
                List.of("CR Prompt Text", "CR Image Input Switch", "CR Text", "CR Conditioning Input Switch"));
        addFallback(target, "KJNodes", "https://github.com/kijai/ComfyUI-KJNodes",
                List.of("ColorMatch", "SetGetNode", "GetNode", "SetNode", "ImageBatchTest"));
        addFallback(target, "ControlNetAux", "https://github.com/Fannovel16/comfy_controlnet_preprocessors",
                List.of("LineArtPreprocessor", "DWPreprocessor", "Zoe-DepthMapPreprocessor", "CannyEdgePreprocessor"));
    }

    private void addFallback(Map<String, CustomNodePackage> target, String name, String url, List<String> nodes) {
        CustomNodePackage pkg = new CustomNodePackage(name, url, nodes, List.of());
        for (String n : nodes) {
            target.put(n.toLowerCase(Locale.ROOT), pkg);
        }
    }

    private record PackageMetadata(String name, String gitUrl, String title, List<String> pipList) {}
}
