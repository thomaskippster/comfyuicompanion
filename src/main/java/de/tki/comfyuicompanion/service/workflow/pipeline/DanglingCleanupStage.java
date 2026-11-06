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
import java.util.Set;

/**
 * Pipeline stage purging dangling cleanup utilities (e.g. RAMCleanup, VRAMCleanup) and broken subgraphs.
 */
@Component
@Order(40)
public class DanglingCleanupStage implements WorkflowSanitizationStage {

    private static final Logger log = LoggerFactory.getLogger(DanglingCleanupStage.class);

    @Override
    public boolean process(WorkflowSanitizationContext context) {
        ObjectNode workflow = context.workflow();
        if (workflow == null) return false;

        boolean mutated = false;
        Set<String> taintedSubgraphs = WorkflowGraphHelper.findSubgraphsWithMissingNodes(workflow, context.registeredNodes());
        if (!taintedSubgraphs.isEmpty()) {
            JsonNode subgraphs = workflow.path("definitions").path("subgraphs");
            if (subgraphs.isArray()) {
                ArrayNode arr = (ArrayNode) subgraphs;
                for (int i = arr.size() - 1; i >= 0; i--) {
                    if (taintedSubgraphs.contains(arr.get(i).path("id").asText(""))) {
                        arr.remove(i);
                        mutated = true;
                    }
                }
            }
        }

        ArrayNode nodes = workflow.has("nodes") && workflow.get("nodes").isArray()
                ? (ArrayNode) workflow.get("nodes") : null;
        if (nodes != null) {
            List<Integer> cleanupNodeIds = new ArrayList<>();
            for (JsonNode n : nodes) {
                if (n.isObject()) {
                    String type = n.path("type").asText("").toLowerCase(Locale.ROOT);
                    if (type.contains("ramcleanup") || type.contains("vramcleanup") || type.contains("memory_cleanup")) {
                        cleanupNodeIds.add(n.path("id").asInt(-1));
                    }
                }
            }
            for (int id : cleanupNodeIds) {
                if (id >= 0) {
                    WorkflowGraphHelper.removeNodeAndAllRemainingLinks(workflow, id);
                    log.info("Purged unnecessary cleanup node {}.", id);
                    mutated = true;
                }
            }
        }

        return mutated;
    }
}
