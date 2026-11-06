package de.tki.comfyuicompanion.service.prompt.sanitization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Prunes missing side-branch sink nodes (image comparers, previewers, debug displays, ...) that feed no
 * other node, provided the prompt still contains at least one registered output node so that the
 * remaining graph keeps producing results.
 * <p>
 * Removing such sinks is safe because ComfyUI only executes nodes reachable backwards from output nodes;
 * the pruned branch would not influence any surviving output.
 */
@Component
@Order(40)
public class MissingDanglingSinkPruningStage implements PromptSanitizationStage {

    private static final Logger log = LoggerFactory.getLogger(MissingDanglingSinkPruningStage.class);

    @Override
    public boolean apply(ApiPromptGraph graph) {
        if (!hasRegisteredOutput(graph)) return false;
        boolean mutated = false;
        for (String missingId : graph.missingNodeIds()) {
            if (graph.consumersOf(missingId).isEmpty()) {
                log.info("Pruned missing sink node '{}' (id {}) without downstream consumers.",
                        graph.classType(missingId), missingId);
                graph.remove(missingId);
                mutated = true;
            }
        }
        return mutated;
    }

    private boolean hasRegisteredOutput(ApiPromptGraph graph) {
        for (String id : graph.nodeIds()) {
            String ct = graph.classType(id);
            if (graph.isRegistered(ct) && graph.isOutputNode(ct)) return true;
        }
        return false;
    }
}
