package de.tki.comfyuicompanion.service.workflow.pipeline;

/**
 * Interface representing a distinct, decoupled transformation stage within the workflow sanitization pipeline.
 * Each stage encapsulates a single transformation responsibility (e.g. image saver conversion, parameter inlining)
 * and mutates the LiteGraph visual workflow tree within the provided context.
 */
public interface WorkflowSanitizationStage {

    /**
     * Executes the sanitization or transformation stage on the supplied workflow context.
     *
     * @param context the context encapsulating the AST, registered ComfyUI nodes, installed models, and mapper
     * @return {@code true} if the workflow graph was mutated by this stage, {@code false} otherwise
     */
    boolean process(WorkflowSanitizationContext context);
}
