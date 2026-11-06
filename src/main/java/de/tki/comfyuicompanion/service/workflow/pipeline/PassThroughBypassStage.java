package de.tki.comfyuicompanion.service.workflow.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Pipeline stage rewiring missing pass-through and routing nodes directly to upstream producers.
 */
@Component
@Order(20)
public class PassThroughBypassStage implements WorkflowSanitizationStage {

    private static final Logger log = LoggerFactory.getLogger(PassThroughBypassStage.class);

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
                if (WorkflowGraphHelper.isNodeMissing(nodeObj, context.registeredNodes(), taintedSubgraphs) && canRewireAsPassThrough(nodeObj)) {
                    targets.add(nodeObj);
                }
            }
        }

        if (targets.isEmpty()) return false;

        for (ObjectNode target : targets) {
            rewirePassThroughAndRemove(workflow, target, context.objectMapper());
        }
        return true;
    }

    private boolean canRewireAsPassThrough(ObjectNode node) {
        String typeLower = node.path("type").asText("").toLowerCase(Locale.ROOT);
        if (typeLower.contains("lora") || typeLower.contains("pass") || typeLower.contains("bypass")
                || typeLower.contains("switch") || typeLower.contains("pulid") || typeLower.contains("ipadapter")
                || typeLower.contains("face") || typeLower.contains("preprocessor") || typeLower.contains("controlnet")) {
            JsonNode outputs = node.get("outputs");
            if (outputs != null && outputs.isArray()) {
                for (JsonNode out : outputs) {
                    String outType = out.path("type").asText("");
                    if (("MODEL".equalsIgnoreCase(outType) || "CLIP".equalsIgnoreCase(outType)
                            || "LATENT".equalsIgnoreCase(outType) || "IMAGE".equalsIgnoreCase(outType))
                            && WorkflowGraphHelper.findMatchingConnectedInputLink(node.get("inputs"), outType) >= 0) {
                        return true;
                    }
                }
            }
        }
        JsonNode inputs = node.get("inputs");
        JsonNode outputs = node.get("outputs");
        if (inputs == null || !inputs.isArray() || inputs.isEmpty() || outputs == null || !outputs.isArray() || outputs.isEmpty()) {
            return false;
        }
        for (JsonNode out : outputs) {
            JsonNode outLinks = out.get("links");
            if (outLinks != null && outLinks.isArray() && !outLinks.isEmpty()) {
                if (WorkflowGraphHelper.findMatchingConnectedInputLink(inputs, out.path("type").asText("")) >= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private void rewirePassThroughAndRemove(ObjectNode workflow, ObjectNode node, ObjectMapper objectMapper) {
        int nodeId = node.path("id").asInt(-1);
        JsonNode inputs = node.get("inputs");
        JsonNode outputs = node.get("outputs");
        ArrayNode allLinks = workflow.has("links") && workflow.get("links").isArray()
                ? (ArrayNode) workflow.get("links") : objectMapper.createArrayNode();

        if (outputs != null && outputs.isArray()) {
            for (JsonNode out : outputs) {
                String outType = out.path("type").asText("");
                JsonNode outLinks = out.get("links");
                if (outLinks == null || !outLinks.isArray() || outLinks.isEmpty()) continue;

                int inLinkId = WorkflowGraphHelper.findMatchingConnectedInputLink(inputs, outType);
                ArrayNode inLinkTuple = WorkflowGraphHelper.findLinkTuple(allLinks, inLinkId);
                List<Integer> outLinkIds = new ArrayList<>();
                outLinks.forEach(l -> outLinkIds.add(l.asInt(-1)));

                if (inLinkTuple != null && inLinkTuple.size() >= 6) {
                    int upNodeId = inLinkTuple.get(1).asInt();
                    int upSlot = inLinkTuple.get(2).asInt();
                    ObjectNode upNode = WorkflowGraphHelper.findNodeById(workflow, upNodeId);
                    for (int outLid : outLinkIds) {
                        ArrayNode outTuple = WorkflowGraphHelper.findLinkTuple(allLinks, outLid);
                        if (outTuple != null && outTuple.size() >= 6) {
                            outTuple.set(1, objectMapper.getNodeFactory().numberNode(upNodeId));
                            outTuple.set(2, objectMapper.getNodeFactory().numberNode(upSlot));
                            WorkflowGraphHelper.addLinkToNodeOutput(objectMapper, upNode, upSlot, outLid);
                        }
                    }
                } else {
                    outLinkIds.forEach(lid -> WorkflowGraphHelper.removeLinkById(workflow, lid));
                }
            }
        }
        WorkflowGraphHelper.removeNodeAndAllRemainingLinks(workflow, nodeId);
        log.info("Bypassed and rewired missing pass-through custom node {} ({}).", nodeId, node.path("type").asText());
    }
}
