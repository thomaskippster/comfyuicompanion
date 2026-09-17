package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfyuicompanion.domain.ModelInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Service component responsible for querying ComfyUI remote server templates,
 * parsing template hierarchies, and acquiring preview assets and workflow descriptions.
 */
@Component
public class ServerBlueprintDiscoveryService {
    private static final Logger logger = LoggerFactory.getLogger(ServerBlueprintDiscoveryService.class);

    /**
     * Record representing template metadata returned by the ComfyUI server.
     */
    public record ServerTemplateInfo(String name, String title, String description, String category) {}

    /**
     * Traverses the template tree from ComfyUI index to extract media subtype mappings.
     *
     * @param node JSON node from index
     * @param map  target map from template name to media subtype
     */
    public void parseSubtypesFromIndex(JsonNode node, Map<String, String> map) {
        if (node == null) return;
        if (node.isArray()) {
            for (JsonNode child : node) {
                parseSubtypesFromIndex(child, map);
            }
        } else if (node.isObject()) {
            if (node.has("templates") && node.get("templates").isArray()) {
                parseSubtypesFromIndex(node.get("templates"), map);
            }
            if (node.has("name")) {
                String name = node.path("name").asText("");
                String mediaSubtype = node.path("mediaSubtype").asText("");
                if (!name.isEmpty() && !mediaSubtype.isEmpty()) {
                    map.put(name.toLowerCase().trim(), mediaSubtype.toLowerCase().trim());
                }
            }
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (!"templates".equals(field.getKey())) {
                    parseSubtypesFromIndex(field.getValue(), map);
                }
            }
        }
    }

    /**
     * Flattens nested template categories and groups from the ComfyUI templates index.
     *
     * @param node            root or child JSON node
     * @param list            target list of template info items
     * @param currentCategory current resolved category
     * @param parentTrail     breadcrumb trail of category groups
     */
    public void flattenTemplates(JsonNode node, List<ServerTemplateInfo> list, String currentCategory, String parentTrail) {
        if (node == null) return;
        if (node.isArray()) {
            for (JsonNode child : node) {
                flattenTemplates(child, list, currentCategory, parentTrail);
            }
        } else if (node.isObject()) {
            String groupTitle = node.path("title").asText("");
            JsonNode templatesNode = node.path("templates");

            if (templatesNode.isArray()) {
                String nextCategory = currentCategory.isEmpty() ? groupTitle : currentCategory;
                String nextTrail = parentTrail.isEmpty() ? groupTitle : parentTrail + " / " + groupTitle;
                flattenTemplates(templatesNode, list, nextCategory, nextTrail);
                return;
            }

            if (node.has("name") && (node.has("title") || node.has("description") || node.has("mediaType"))) {
                String name = node.path("name").asText("");
                String title = node.path("title").asText(name);
                String description = node.path("description").asText("");

                String category = currentCategory;
                if (category.isEmpty()) {
                    category = parentTrail;
                }
                if (category.isEmpty()) {
                    category = "Uncategorized";
                }

                list.add(new ServerTemplateInfo(name, title, description, category));
            }

            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (!"templates".equals(field.getKey())) {
                    flattenTemplates(field.getValue(), list, currentCategory, parentTrail);
                }
            }
        }
    }

    /**
     * Discovers remote blueprints from an active ComfyUI server and incorporates them into the scan list.
     *
     * @param scanList          destination list of blueprint maps
     * @param mediaSubtypes     mapping of template names to media formats
     * @param comfyUrl          base ComfyUI server URL
     * @param mapper            Jackson ObjectMapper
     * @param modelAnalyzer     analyzer for extracting required model references
     * @param defaultsExtractor extractor for deriving model defaults from workflow
     * @param onWorkflowLoaded  callback when raw workflow JSON is fetched
     */
    public void discoverServerBlueprints(
            List<Map<String, Object>> scanList,
            Map<String, String> mediaSubtypes,
            String comfyUrl,
            ObjectMapper mapper,
            ComfyModelAnalyzer modelAnalyzer,
            WorkflowDefaultsExtractor defaultsExtractor,
            java.util.function.BiConsumer<String, String> onWorkflowLoaded) {
        if (comfyUrl == null || comfyUrl.isEmpty()) return;

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(comfyUrl + "/templates/index.json"))
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = mapper.readTree(response.body());
                List<ServerTemplateInfo> templates = new ArrayList<>();
                flattenTemplates(root, templates, "", "");

                for (ServerTemplateInfo t : templates) {
                    if (t == null || t.name() == null || t.name().isEmpty()) continue;
                    try {
                        String jsonFilename = t.name() + ".json";
                        boolean existsLocally = false;
                        for (Map<String, Object> entry : scanList) {
                            String fName = (String) entry.get("filename");
                            if (fName != null && fName.equalsIgnoreCase(jsonFilename)) {
                                existsLocally = true;
                                break;
                            }
                        }
                        if (existsLocally) continue;

                        String workflowUrl = comfyUrl + "/templates/" + URLEncoder.encode(t.name() + ".json", StandardCharsets.UTF_8).replace("+", "%20");
                        HttpRequest wfRequest = HttpRequest.newBuilder()
                                .uri(URI.create(workflowUrl))
                                .timeout(Duration.ofSeconds(5))
                                .GET().build();
                        HttpResponse<String> wfResponse = client.send(wfRequest, HttpResponse.BodyHandlers.ofString());
                        if (wfResponse.statusCode() == 200) {
                            String rawJson = wfResponse.body();
                            List<ModelInfo> requiredModels;
                            try {
                                requiredModels = modelAnalyzer != null ? modelAnalyzer.analyze(rawJson, t.name() + ".json") : Collections.emptyList();
                            } catch (Exception e) {
                                requiredModels = Collections.emptyList();
                            }

                            String mediaSubtypeIdx = mediaSubtypes.get(t.name().toLowerCase().trim());
                            String previewPath = "";
                            String mediaType = "image";
                            String mediaSubtype = "";
                            if (mediaSubtypeIdx != null && !mediaSubtypeIdx.isEmpty()) {
                                mediaSubtype = mediaSubtypeIdx;
                                if ("mp4".equals(mediaSubtype) || "webm".equals(mediaSubtype) || "mov".equals(mediaSubtype)) {
                                    mediaType = "video";
                                }
                                previewPath = comfyUrl + "/templates/" + URLEncoder.encode(t.name() + "-1." + mediaSubtype, StandardCharsets.UTF_8).replace("+", "%20");
                            }

                            Map<String, Object> blueprintMap = new HashMap<>();
                            blueprintMap.put("name", t.title());
                            blueprintMap.put("filename", jsonFilename);
                            blueprintMap.put("filePath", "remote:" + t.name());
                            blueprintMap.put("category", t.category());
                            blueprintMap.put("description", t.description());
                            blueprintMap.put("mediaType", mediaType);
                            blueprintMap.put("mediaSubtype", mediaSubtype);
                            blueprintMap.put("previewPath", previewPath);
                            blueprintMap.put("requiredModels", requiredModels);
                            scanList.add(blueprintMap);

                            if (onWorkflowLoaded != null) {
                                onWorkflowLoaded.accept(rawJson, jsonFilename);
                            }
                        }
                    } catch (Exception ex) {
                        logger.error("⚠️ [ServerBlueprintDiscovery] Failed to fetch server workflow for {}: {}", t.name(), ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("⚠️ [ServerBlueprintDiscovery] Failed server blueprint discovery: {}", e.getMessage());
        }
    }
}
