package de.tki.comfyuicompanion.service.workflow.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pipeline stage inlining missing primitive and parameter-selector nodes into downstream core widgets.
 */
@Component
@Order(40)
public class ParameterInliningStage implements WorkflowSanitizationStage {

    private static final Logger log = LoggerFactory.getLogger(ParameterInliningStage.class);

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
                if (WorkflowGraphHelper.isNodeMissing(nodeObj, context.registeredNodes(), taintedSubgraphs) && isEligibleParameterNode(nodeObj)) {
                    targets.add(nodeObj);
                }
            }
        }

        if (targets.isEmpty()) return false;

        for (ObjectNode target : targets) {
            inlineWidgetsAndRemoveNode(workflow, target);
        }
        return true;
    }

    private boolean isEligibleParameterNode(ObjectNode node) {
        String type = node.path("type").asText("");
        if (type.isBlank() || de.tki.comfyuicompanion.service.workflow.ComfyCoreNodesCatalog.isUuid(type)) {
            return false;
        }
        String typeLower = type.toLowerCase(Locale.ROOT);
        if (typeLower.contains("resolution") || typeLower.contains("grid") || typeLower.contains("overlay")
                || typeLower.contains("switch") || typeLower.contains("loader") || typeLower.contains("ksampler")
                || typeLower.contains("samplercustom") || typeLower.contains("decode") || typeLower.contains("encode")
                || typeLower.contains("save") || typeLower.contains("preview") || typeLower.contains("controlnet")) {
            return false;
        }
        if (typeLower.contains("selector") || typeLower.contains("generator")) {
            return true;
        }
        return typeLower.startsWith("primitive") || typeLower.startsWith("easy ") || typeLower.contains("string")
                || typeLower.contains("int") || typeLower.contains("float") || typeLower.contains("boolean")
                || typeLower.contains("seed");
    }

    private void inlineWidgetsAndRemoveNode(ObjectNode workflow, ObjectNode node) {
        int nodeId = node.path("id").asInt(-1);
        JsonNode wv = node.get("widgets_values");
        JsonNode primaryVal = (wv != null && wv.isArray() && !wv.isEmpty()) ? wv.get(0) : null;

        ArrayNode allLinks = workflow.has("links") && workflow.get("links").isArray()
                ? (ArrayNode) workflow.get("links") : null;

        if (allLinks != null && primaryVal != null && !primaryVal.isNull() && !primaryVal.isObject() && !primaryVal.isArray()) {
            for (JsonNode l : allLinks) {
                if (l.isArray() && l.size() >= 6 && l.get(1).asInt() == nodeId) {
                    int targetNodeId = l.get(3).asInt();
                    int targetInSlot = l.get(4).asInt(-1);
                    ObjectNode targetNode = WorkflowGraphHelper.findNodeById(workflow, targetNodeId);
                    if (targetNode != null && targetNode.has("widgets_values") && targetNode.get("widgets_values").isArray()) {
                        ArrayNode targetWv = (ArrayNode) targetNode.get("widgets_values");
                        int widgetIdx = resolveTargetWidgetIndex(targetNode, targetInSlot, targetWv.size());
                        if (widgetIdx >= 0 && widgetIdx < targetWv.size()) {
                            JsonNode currentVal = targetWv.get(widgetIdx);
                            if (isTypeCompatible(currentVal, primaryVal)) {
                                targetWv.set(widgetIdx, primaryVal.deepCopy());
                            } else {
                                log.warn("Skipping widget inlining on node {} slot {}: incompatible type for '{}' to '{}'",
                                        targetNodeId, targetInSlot, primaryVal, currentVal);
                            }
                        }
                    }
                }
            }
        }
        WorkflowGraphHelper.removeNodeAndAllRemainingLinks(workflow, nodeId);
        log.info("Inlined widgets from missing custom node {} ({}) and removed node.", nodeId, node.path("type").asText());
    }

    private boolean isTypeCompatible(JsonNode currentVal, JsonNode newVal) {
        if (currentVal == null || newVal == null || currentVal.isNull() || newVal.isNull()) {
            return true;
        }
        if (currentVal.isNumber()) {
            if (newVal.isNumber()) return true;
            if (newVal.isTextual()) {
                try {
                    Double.parseDouble(newVal.asText());
                    return true;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
            return false;
        }
        if (currentVal.isBoolean()) {
            return newVal.isBoolean() || "true".equalsIgnoreCase(newVal.asText()) || "false".equalsIgnoreCase(newVal.asText());
        }
        return true;
    }

    private int resolveTargetWidgetIndex(ObjectNode targetNode, int targetInSlot, int widgetCount) {
        if (widgetCount <= 0 || targetInSlot < 0) return -1;
        JsonNode inputs = targetNode.get("inputs");
        if (inputs != null && inputs.isArray() && targetInSlot < inputs.size()) {
            JsonNode targetInput = inputs.get(targetInSlot);
            String inName = targetInput.path("name").asText("").toLowerCase(Locale.ROOT);
            String targetType = targetNode.path("type").asText("");
            if (targetType.contains("KSampler")) {
                if (inName.contains("seed")) return 0;
                if (inName.contains("step")) return Math.min(2, widgetCount - 1);
                if (inName.contains("cfg")) return Math.min(3, widgetCount - 1);
                if (inName.contains("sampler")) return Math.min(4, widgetCount - 1);
                if (inName.contains("schedul")) return Math.min(5, widgetCount - 1);
                if (inName.contains("denoise")) return Math.min(6, widgetCount - 1);
            }
            int wIdx = 0;
            for (int i = 0; i <= targetInSlot; i++) {
                if (inputs.get(i).has("widget")) {
                    if (i == targetInSlot) return wIdx;
                    wIdx++;
                }
            }
            if (targetInput.has("widget")) {
                return Math.min(targetInSlot, widgetCount - 1);
            }
        }
        return -1;
    }
}
