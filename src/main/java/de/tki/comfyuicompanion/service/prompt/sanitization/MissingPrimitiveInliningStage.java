package de.tki.comfyuicompanion.service.prompt.sanitization;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Inlines missing pure value-provider nodes (seed generators, number/string/boolean primitives,
 * sampler/scheduler selectors, ...) into the widgets of their consumers.
 * <p>
 * A missing node qualifies if it has no linked inputs (it is a pure source) and every consumer input it
 * feeds is declared as a widget primitive ({@code INT, FLOAT, STRING, BOOLEAN, COMBO}) in
 * {@code /object_info}. The inlined value is taken from the node's own literal inputs when type- and
 * range-compatible, otherwise the consumer's declared default is used.
 */
@Component
@Order(20)
public class MissingPrimitiveInliningStage implements PromptSanitizationStage {

    private static final Logger log = LoggerFactory.getLogger(MissingPrimitiveInliningStage.class);

    @Override
    public boolean apply(ApiPromptGraph graph) {
        boolean mutated = false;
        for (String missingId : graph.missingNodeIds()) {
            if (!graph.linkedInputs(missingId).isEmpty()) continue;
            List<ApiPromptGraph.LinkRef> consumers = graph.consumersOf(missingId);
            if (consumers.isEmpty()) continue;

            Map<ApiPromptGraph.LinkRef, Object> resolved = resolveAll(graph, missingId, consumers);
            if (resolved == null) continue;

            resolved.forEach((ref, value) -> graph.inputs(ref.consumerId()).put(ref.inputName(), value));
            log.info("Inlined missing value-provider node '{}' (id {}) into {} consumer widget(s).",
                    graph.classType(missingId), missingId, resolved.size());
            graph.remove(missingId);
            mutated = true;
        }
        return mutated;
    }

    /**
     * Resolves a literal for every consumer link, returning {@code null} if any link is not a primitive
     * widget input, so that functional nodes are never partially dismantled.
     */
    private Map<ApiPromptGraph.LinkRef, Object> resolveAll(ApiPromptGraph graph, String missingId,
                                                           List<ApiPromptGraph.LinkRef> consumers) {
        Map<String, Object> literals = graph.literalInputs(missingId);
        Map<ApiPromptGraph.LinkRef, Object> resolved = new HashMap<>();
        for (ApiPromptGraph.LinkRef ref : consumers) {
            String consumerType = graph.classType(ref.consumerId());
            Optional<JSONArray> spec = graph.inputSpec(consumerType, ref.inputName());
            Optional<String> type = graph.inputType(consumerType, ref.inputName());
            if (spec.isEmpty() || type.isEmpty() || !ApiPromptGraph.isPrimitiveType(type.get())) {
                return null;
            }
            Object value = pickLiteral(literals, ref.inputName(), type.get(), spec.get())
                    .or(() -> declaredDefault(type.get(), spec.get()))
                    .orElse(null);
            if (value == null) return null;
            resolved.put(ref, value);
        }
        return resolved;
    }

    private Optional<Object> pickLiteral(Map<String, Object> literals, String preferredName,
                                         String type, JSONArray spec) {
        Object preferred = literals.get(preferredName);
        if (preferred != null && isAcceptable(preferred, type, spec)) return Optional.of(coerce(preferred, type));
        for (Object candidate : literals.values()) {
            if (isAcceptable(candidate, type, spec)) return Optional.of(coerce(candidate, type));
        }
        return Optional.empty();
    }

    private boolean isAcceptable(Object value, String type, JSONArray spec) {
        JSONObject opts = spec.optJSONObject(1);
        return switch (type) {
            case "INT" -> value instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue()) && inRange(n, opts);
            case "FLOAT" -> value instanceof Number n && inRange(n, opts);
            case "BOOLEAN" -> value instanceof Boolean;
            case "STRING" -> value instanceof String;
            case ApiPromptGraph.COMBO -> value instanceof String s && comboOptions(spec).map(o -> contains(o, s)).orElse(false);
            default -> false;
        };
    }

    private boolean inRange(Number n, JSONObject opts) {
        if (opts == null) return true;
        double v = n.doubleValue();
        return (!opts.has("min") || v >= opts.optDouble("min")) && (!opts.has("max") || v <= opts.optDouble("max"));
    }

    private Object coerce(Object value, String type) {
        if ("INT".equals(type) && value instanceof Number n) return n.longValue();
        if ("FLOAT".equals(type) && value instanceof Number n) return n.doubleValue();
        return value;
    }

    private Optional<Object> declaredDefault(String type, JSONArray spec) {
        JSONObject opts = spec.optJSONObject(1);
        if (opts != null && opts.has("default") && opts.opt("default") != JSONObject.NULL) {
            return Optional.of(coerce(opts.get("default"), type));
        }
        if (ApiPromptGraph.COMBO.equals(type)) {
            return comboOptions(spec).filter(o -> !o.isEmpty()).map(o -> o.get(0));
        }
        return Optional.empty();
    }

    /** Supports both the legacy {@code [[options], {...}]} and the newer {@code ["COMBO", {"options": [...]}]} layout. */
    private Optional<JSONArray> comboOptions(JSONArray spec) {
        if (spec.opt(0) instanceof JSONArray arr) return Optional.of(arr);
        JSONObject opts = spec.optJSONObject(1);
        return Optional.ofNullable(opts != null ? opts.optJSONArray("options") : null);
    }

    private boolean contains(JSONArray options, String value) {
        for (int i = 0; i < options.length(); i++) {
            if (value.equals(String.valueOf(options.get(i)))) return true;
        }
        return false;
    }
}
