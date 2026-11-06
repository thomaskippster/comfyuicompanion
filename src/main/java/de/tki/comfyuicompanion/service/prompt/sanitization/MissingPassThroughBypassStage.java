package de.tki.comfyuicompanion.service.prompt.sanitization;

import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Bypasses missing routing nodes (reroutes, switches, pipes, any-type relays) by connecting each
 * consumer directly to an upstream producer whose declared output type matches the type the consumer
 * expects.
 * <p>
 * Type compatibility is evaluated exclusively from {@code /object_info}: the consumer input type and the
 * producer output type are both known because both neighbors are registered core or installed nodes.
 */
@Component
@Order(10)
public class MissingPassThroughBypassStage implements PromptSanitizationStage {

    private static final Logger log = LoggerFactory.getLogger(MissingPassThroughBypassStage.class);

    @Override
    public boolean apply(ApiPromptGraph graph) {
        boolean mutated = false;
        for (String missingId : graph.missingNodeIds()) {
            if (!isEligibleRoutingNode(graph.classType(missingId))) continue;
            Map<String, JSONArray> upstream = graph.linkedInputs(missingId);
            if (upstream.isEmpty()) continue;

            List<ApiPromptGraph.LinkRef> consumers = graph.consumersOf(missingId);
            int rewired = 0;
            for (ApiPromptGraph.LinkRef ref : consumers) {
                Optional<String> expected = graph.inputType(graph.classType(ref.consumerId()), ref.inputName());
                if (expected.isEmpty()) continue;
                Optional<JSONArray> source = findTypedSource(graph, upstream, expected.get(), ref.inputName());
                if (source.isPresent()) {
                    graph.rewire(ref, String.valueOf(source.get().get(0)), source.get().getInt(1));
                    rewired++;
                }
            }

            if (rewired > 0 && rewired == consumers.size()) {
                log.info("Bypassed missing pass-through node '{}' (id {}) by rewiring {} consumer link(s).",
                        graph.classType(missingId), missingId, rewired);
                graph.remove(missingId);
                mutated = true;
            } else if (rewired > 0) {
                mutated = true;
            }
        }
        return mutated;
    }

    private boolean isEligibleRoutingNode(String classType) {
        if (classType == null || classType.isBlank()) return false;
        String lower = classType.toLowerCase(Locale.ROOT);
        return lower.contains("reroute") || lower.contains("switch") || lower.contains("relay")
                || lower.contains("bypass") || lower.contains("pipe") || lower.contains("bus")
                || lower.contains("wire") || lower.contains("passthrough") || lower.contains("pass through");
    }

    /**
     * Picks the upstream link whose producer output type equals the expected type, preferring an input
     * with the same name as the consumer input to keep the original data flow intent.
     */
    private Optional<JSONArray> findTypedSource(ApiPromptGraph graph, Map<String, JSONArray> upstream,
                                                String expectedType, String preferredName) {
        JSONArray fallback = null;
        for (Map.Entry<String, JSONArray> entry : upstream.entrySet()) {
            JSONArray link = entry.getValue();
            String producerId = String.valueOf(link.get(0));
            String producerType = graph.classType(producerId);
            if (!graph.isRegistered(producerType)) continue;
            Optional<String> outType = graph.outputType(producerType, link.getInt(1));
            if (outType.isPresent() && outType.get().equals(expectedType)) {
                if (entry.getKey().equals(preferredName)) return Optional.of(link);
                if (fallback == null) fallback = link;
            }
        }
        return Optional.ofNullable(fallback);
    }
}
