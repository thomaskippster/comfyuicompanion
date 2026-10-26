package de.tki.comfyuicompanion.service.impl.bridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Transforms the hierarchical ComfyUI {@code templates/index.json} catalog into a flat list of
 * templates and enriches each entry with an internal preview-proxy URL.
 */
public class TemplateCatalogMapper {
    private static final Logger logger = LoggerFactory.getLogger(TemplateCatalogMapper.class);

    /**
     * Extracts and flattens template definitions from a hierarchical ComfyUI templates JSON tree.
     *
     * @param rootNode The root JSON node of the template catalog
     * @param mapper   The Jackson ObjectMapper to construct nodes (a default one is used if {@code null})
     * @return List of flattened ObjectNodes representing individual templates
     */
    public List<ObjectNode> extractFlattenedTemplates(JsonNode rootNode, ObjectMapper mapper) {
        List<ObjectNode> flattened = new ArrayList<>();
        flattenTemplatesNode(rootNode, flattened, "", "", mapper != null ? mapper : new ObjectMapper());
        return flattened;
    }

    /**
     * Builds the client-facing response ({@code {"templates": [...]}}) from a raw catalog.
     *
     * @param rootNode      the raw catalog root
     * @param baseUrl       the ComfyUI base URL (without trailing slash) used to compose preview source URLs
     * @param mapper        the ObjectMapper used to create nodes
     * @return the structured response node
     */
    public ObjectNode buildTemplatesResponse(JsonNode rootNode, String baseUrl, ObjectMapper mapper) {
        List<ObjectNode> flattened = extractFlattenedTemplates(rootNode, mapper);

        ArrayNode templatesArray = mapper.createArrayNode();
        for (ObjectNode templateNode : flattened) {
            String templateName = templateNode.path("name").asText("");
            String mediaSubtype = templateNode.path("mediaSubtype").asText("");

            if (!mediaSubtype.isEmpty()) {
                try {
                    String comfyuiInternalUrl = baseUrl + "/templates/"
                            + URLEncoder.encode(templateName, StandardCharsets.UTF_8).replace("+", "%20")
                            + "-1." + mediaSubtype;
                    String clientPreviewUrl = "/api/preview?url="
                            + URLEncoder.encode(comfyuiInternalUrl, StandardCharsets.UTF_8).replace("+", "%20");
                    templateNode.put("previewUrl", clientPreviewUrl);
                } catch (Exception ignored) {
            logger.debug("Ignored non-fatal exception: {}", ignored.getMessage());
        }
            }
            templatesArray.add(templateNode);
        }

        ObjectNode responseJson = mapper.createObjectNode();
        responseJson.set("templates", templatesArray);
        return responseJson;
    }

    private void flattenTemplatesNode(JsonNode node, List<ObjectNode> list, String currentCategory, String parentTrail, ObjectMapper mapper) {
        if (node == null) return;
        if (node.isArray()) {
            for (JsonNode child : node) {
                flattenTemplatesNode(child, list, currentCategory, parentTrail, mapper);
            }
        } else if (node.isObject()) {
            String groupTitle = node.path("title").asText("");
            JsonNode templatesNode = node.path("templates");

            if (templatesNode.isArray()) {
                String nextCategory = currentCategory.isEmpty() ? groupTitle : currentCategory;
                String nextTrail = parentTrail.isEmpty() ? groupTitle : parentTrail + " / " + groupTitle;
                flattenTemplatesNode(templatesNode, list, nextCategory, nextTrail, mapper);
                return;
            }

            if (node.has("name") && (node.has("title") || node.has("description") || node.has("mediaType"))) {
                String name = node.path("name").asText("");
                String title = node.path("title").asText(name);
                String description = node.path("description").asText("");
                String mediaSubtype = node.path("mediaSubtype").asText("");

                String category = currentCategory;
                if (category.isEmpty()) {
                    category = parentTrail;
                }
                if (category.isEmpty()) {
                    category = "Uncategorized";
                }

                ObjectNode templateObj = mapper.createObjectNode();
                templateObj.put("name", name);
                templateObj.put("title", title);
                templateObj.put("description", description);
                templateObj.put("category", category);
                templateObj.put("mediaSubtype", mediaSubtype);

                list.add(templateObj);
            }

            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (!field.getKey().equals("templates")) {
                    flattenTemplatesNode(field.getValue(), list, currentCategory, parentTrail, mapper);
                }
            }
        }
    }
}
