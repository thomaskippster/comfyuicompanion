package de.tki.comfyuicompanion.service.prompt.sanitization;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Replaces missing pure latent providers (aspect ratio selectors, resolution presets, custom empty
 * latent generators, ...) with an empty latent factory registered in ComfyUI.
 * <p>
 * The factory is discovered dynamically from {@code /object_info}: any registered node with exactly one
 * {@code LATENT} output and exactly the required inputs {@code width, height, batch_size}. A factory
 * already used elsewhere in the prompt is preferred; otherwise the canonical {@code EmptyLatentImage}
 * is chosen, because ComfyUI's samplers adapt empty latents to the model's channel count
 * ({@code fix_empty_latent_channels}), which makes it valid for SD1.x, SDXL, SD3 and Flux alike.
 */
@Component
@Order(30)
public class MissingLatentSourceReplacementStage implements PromptSanitizationStage {

    private static final Logger log = LoggerFactory.getLogger(MissingLatentSourceReplacementStage.class);
    private static final String LATENT = "LATENT";
    private static final String CANONICAL_FACTORY = "EmptyLatentImage";
    private static final Set<String> FACTORY_INPUTS = Set.of("width", "height", "batch_size");

    @Override
    public boolean apply(ApiPromptGraph graph) {
        boolean mutated = false;
        for (String missingId : graph.missingNodeIds()) {
            if (!graph.linkedInputs(missingId).isEmpty() || !feedsOnlyLatents(graph, missingId)) continue;

            Optional<String> factory = selectFactory(graph);
            if (factory.isEmpty()) {
                log.debug("No empty latent factory registered in ComfyUI; cannot replace node {}.", missingId);
                return mutated;
            }
            replace(graph, missingId, factory.get());
            mutated = true;
        }
        return mutated;
    }

    private boolean feedsOnlyLatents(ApiPromptGraph graph, String missingId) {
        List<ApiPromptGraph.LinkRef> consumers = graph.consumersOf(missingId);
        if (consumers.isEmpty()) return false;
        for (ApiPromptGraph.LinkRef ref : consumers) {
            Optional<String> type = graph.inputType(graph.classType(ref.consumerId()), ref.inputName());
            if (type.isEmpty() || !LATENT.equals(type.get())) return false;
        }
        return true;
    }

    private Optional<String> selectFactory(ApiPromptGraph graph) {
        List<String> candidates = graph.registeredClassTypes().stream()
                .filter(ct -> graph.outputCount(ct) == 1)
                .filter(ct -> graph.outputType(ct, 0).map(LATENT::equals).orElse(false))
                .filter(ct -> graph.requiredInputNames(ct).equals(FACTORY_INPUTS))
                .sorted(Comparator.naturalOrder())
                .toList();
        if (candidates.isEmpty()) return Optional.empty();

        for (String id : graph.nodeIds()) {
            String used = graph.classType(id);
            if (candidates.contains(used)) return Optional.of(used);
        }
        return Optional.of(candidates.contains(CANONICAL_FACTORY) ? CANONICAL_FACTORY : candidates.get(0));
    }

    private void replace(ApiPromptGraph graph, String missingId, String factory) {
        String originalType = graph.classType(missingId);
        Map<String, Object> literals = graph.literalInputs(missingId);
        LatentDimensionResolver.Dimensions dims = LatentDimensionResolver.resolve(literals)
                .orElseGet(() -> new LatentDimensionResolver.Dimensions(
                        intDefault(graph, factory, "width", 1024), intDefault(graph, factory, "height", 1024)));
        int batch = literals.get("batch_size") instanceof Number n && n.intValue() > 0
                ? n.intValue() : intDefault(graph, factory, "batch_size", 1);

        JSONObject inputs = new JSONObject();
        inputs.put("width", dims.width());
        inputs.put("height", dims.height());
        inputs.put("batch_size", batch);
        JSONObject node = graph.node(missingId);
        node.put("class_type", factory);
        node.put("inputs", inputs);
        log.info("Replaced missing latent provider '{}' (id {}) with '{}' ({}x{}, batch {}).",
                originalType, missingId, factory, dims.width(), dims.height(), batch);
    }

    private int intDefault(ApiPromptGraph graph, String classType, String input, int fallback) {
        return graph.inputSpec(classType, input)
                .map(spec -> spec.optJSONObject(1))
                .map(opts -> opts.optInt("default", fallback))
                .orElse(fallback);
    }
}
