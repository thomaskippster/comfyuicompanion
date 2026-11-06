package de.tki.comfyuicompanion.service.workflow.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tki.comfyuicompanion.service.workflow.ComfyCoreNodesCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pipeline stage converting third-party and custom image saver nodes into native ComfyUI {@code SaveImage} nodes.
 */
@Component
@Order(20)
public class ImageSaverSanitizationStage implements WorkflowSanitizationStage {

    private static final Logger log = LoggerFactory.getLogger(ImageSaverSanitizationStage.class);

    @Override
    public boolean process(WorkflowSanitizationContext context) {
        ObjectNode workflow = context.workflow();
        if (workflow == null || !workflow.has("nodes") || !workflow.get("nodes").isArray()) {
            return false;
        }

        ArrayNode nodes = (ArrayNode) workflow.get("nodes");
        List<ObjectNode> targets = new ArrayList<>();
        var taintedSubgraphs = WorkflowGraphHelper.findSubgraphsWithMissingNodes(workflow, context.registeredNodes());

        for (JsonNode n : nodes) {
            if (n.isObject()) {
                ObjectNode nodeObj = (ObjectNode) n;
                if (WorkflowGraphHelper.isNodeMissing(nodeObj, context.registeredNodes(), taintedSubgraphs) && isImageSaverNode(nodeObj)) {
                    targets.add(nodeObj);
                }
            }
        }

        if (targets.isEmpty()) return false;

        for (ObjectNode target : targets) {
            convertToCoreSaveImage(workflow, target, context.objectMapper());
        }
        return true;
    }

    private boolean isImageSaverNode(ObjectNode node) {
        String type = node.path("type").asText("");
        if ("SaveImage".equals(type) || "PreviewImage".equals(type) || ComfyCoreNodesCatalog.isUuid(type)) {
            return false;
        }
        String typeLower = type.toLowerCase(Locale.ROOT);
        if (typeLower.contains("saver") || typeLower.contains("save image") || typeLower.contains("metadata")) {
            return true;
        }
        JsonNode outputs = node.get("outputs");
        boolean hasOutputs = outputs != null && outputs.isArray() && !outputs.isEmpty();
        if (!hasOutputs && (typeLower.contains("save") || typeLower.contains("export") || typeLower.contains("output"))) {
            return true;
        }
        return false;
    }

    private void convertToCoreSaveImage(ObjectNode workflow, ObjectNode node, ObjectMapper objectMapper) {
        int nodeId = node.path("id").asInt(-1);
        ObjectNode retainedImageInput = null;
        JsonNode inputs = node.get("inputs");

        List<Integer> linksToRemove = new ArrayList<>();
        if (inputs != null && inputs.isArray()) {
            for (JsonNode in : inputs) {
                if (in.isObject()) {
                    int linkId = in.path("link").asInt(-1);
                    if (retainedImageInput == null && "IMAGE".equalsIgnoreCase(in.path("type").asText("")) && linkId >= 0) {
                        retainedImageInput = ((ObjectNode) in).deepCopy();
                        retainedImageInput.put("name", "images").remove("widget");
                    } else if (linkId >= 0) {
                        linksToRemove.add(linkId);
                    }
                }
            }
        }

        JsonNode outputs = node.get("outputs");
        if (outputs != null && outputs.isArray()) {
            for (JsonNode out : outputs) {
                if (out.path("links").isArray()) {
                    out.get("links").forEach(l -> linksToRemove.add(l.asInt(-1)));
                }
            }
        }

        linksToRemove.forEach(l -> WorkflowGraphHelper.removeLinkById(workflow, l));

        node.put("type", "SaveImage");
        ObjectNode props = node.has("properties") && node.get("properties").isObject()
                ? (ObjectNode) node.get("properties") : node.putObject("properties");
        props.put("cnr_id", "comfy-core").put("Node name for S&R", "SaveImage");

        ArrayNode newInputs = objectMapper.createArrayNode();
        if (retainedImageInput != null) {
            newInputs.add(retainedImageInput);
        }
        node.set("inputs", newInputs);
        node.set("outputs", objectMapper.createArrayNode());
        node.set("widgets_values", objectMapper.createArrayNode().add("ComfyUI"));
        log.info("Converted missing custom image saver node {} to core SaveImage.", nodeId);
    }
}
