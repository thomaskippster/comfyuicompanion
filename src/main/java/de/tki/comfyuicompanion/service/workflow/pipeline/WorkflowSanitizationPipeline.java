package de.tki.comfyuicompanion.service.workflow.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Enterprise pipeline coordinator executing ordered sanitization stages on a LiteGraph workflow.
 * Executes fixed-point iterative passes until no stage performs further mutations or max iterations are reached.
 */
@Component
public class WorkflowSanitizationPipeline {

    private static final Logger log = LoggerFactory.getLogger(WorkflowSanitizationPipeline.class);
    private static final int MAX_ITERATIONS = 5;

    private final List<WorkflowSanitizationStage> stages;

    @Autowired
    public WorkflowSanitizationPipeline(List<WorkflowSanitizationStage> stages) {
        this.stages = stages != null ? List.copyOf(stages) : List.of();
    }

    /**
     * Executes the sequential sanitization pipeline on the provided context.
     *
     * @param context workflow context containing the AST and environment state
     * @return true if any mutations were made across all stages, false otherwise
     */
    public boolean execute(WorkflowSanitizationContext context) {
        if (context == null || context.workflow() == null) {
            return false;
        }

        boolean anyMutated = false;
        for (int pass = 1; pass <= MAX_ITERATIONS; pass++) {
            boolean passMutated = false;
            for (WorkflowSanitizationStage stage : stages) {
                try {
                    boolean mutated = stage.process(context);
                    if (mutated) {
                        passMutated = true;
                        anyMutated = true;
                    }
                } catch (Exception ex) {
                    log.error("Error executing sanitization stage '{}': {}", stage.getClass().getSimpleName(), ex.getMessage(), ex);
                }
            }
            if (!passMutated) {
                log.debug("Workflow sanitization pipeline converged after {} pass(es).", pass);
                break;
            }
        }
        return anyMutated;
    }
}
