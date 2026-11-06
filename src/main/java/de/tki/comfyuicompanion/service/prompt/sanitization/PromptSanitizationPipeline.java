package de.tki.comfyuicompanion.service.prompt.sanitization;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Chain-of-Responsibility coordinator that removes unregistered custom nodes from a ComfyUI API prompt
 * by applying type-driven {@link PromptSanitizationStage}s until a fixed point is reached.
 * <p>
 * Nodes that cannot be replaced without changing the workflow's semantics remain in the prompt and are
 * reported in {@link PromptSanitizationReport#remainingMissingClassTypes()} so the caller can offer an
 * installation of the corresponding custom node packages.
 */
@Component
public class PromptSanitizationPipeline {

    private static final Logger log = LoggerFactory.getLogger(PromptSanitizationPipeline.class);
    private static final int MAX_PASSES = 8;

    private final List<PromptSanitizationStage> stages;

    /**
     * Spring constructor receiving all stages ordered by their {@code @Order} annotation.
     *
     * @param stages ordered sanitization stages
     */
    @Autowired
    public PromptSanitizationPipeline(List<PromptSanitizationStage> stages) {
        this.stages = stages != null ? List.copyOf(stages) : List.of();
    }

    /**
     * Creates a pipeline with the default stage order for non-Spring contexts and tests.
     *
     * @return a fully configured pipeline
     */
    public static PromptSanitizationPipeline createDefault() {
        return new PromptSanitizationPipeline(List.of(
                new MissingPassThroughBypassStage(),
                new MissingPrimitiveInliningStage(),
                new MissingOutputNodeReplacementStage(),
                new MissingVaeConverterReplacementStage(),
                new MissingLatentSourceReplacementStage(),
                new MissingDanglingSinkPruningStage()));
    }

    /**
     * Sanitizes the prompt in place.
     *
     * @param prompt     mutable API prompt dictionary
     * @param objectInfo ComfyUI {@code /object_info} registry; without it no node can be classified
     * @return report describing resolved and still missing class types
     */
    public PromptSanitizationReport sanitize(JSONObject prompt, JSONObject objectInfo) {
        ApiPromptGraph graph = new ApiPromptGraph(prompt, objectInfo);
        if (prompt == null || !graph.hasRegistry()) {
            return new PromptSanitizationReport(List.of(), Set.of());
        }

        Set<String> before = graph.missingClassTypes();
        if (before.isEmpty()) return new PromptSanitizationReport(List.of(), Set.of());

        for (int pass = 1; pass <= MAX_PASSES && !graph.missingNodeIds().isEmpty(); pass++) {
            boolean mutated = false;
            for (PromptSanitizationStage stage : stages) {
                try {
                    mutated |= stage.apply(graph);
                } catch (RuntimeException ex) {
                    log.error("Prompt sanitization stage '{}' failed: {}", stage.getClass().getSimpleName(), ex.getMessage(), ex);
                }
            }
            if (!mutated) break;
        }

        Set<String> remaining = graph.missingClassTypes();
        List<String> resolved = new ArrayList<>(before);
        resolved.removeAll(remaining);
        if (!resolved.isEmpty()) {
            log.info("Replaced missing custom nodes with ComfyUI core constructs: {}", resolved);
        }
        if (!remaining.isEmpty()) {
            log.warn("Custom nodes still missing after sanitization: {}", remaining);
        }
        return new PromptSanitizationReport(resolved, remaining);
    }
}
