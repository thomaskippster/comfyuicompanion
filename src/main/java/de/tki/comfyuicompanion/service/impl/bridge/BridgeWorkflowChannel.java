package de.tki.comfyuicompanion.service.impl.bridge;

import java.util.function.Consumer;

/**
 * Thread-safe holder of the consumers that receive workflows pushed through the REST bridge:
 * a persistent consumer for imported workflows and a one-shot consumer for browser-converted
 * API payloads.
 */
public class BridgeWorkflowChannel {
    private volatile Consumer<String> workflowConsumer;
    /** One-shot consumer: receives the API JSON returned by app.graphToPrompt() and clears itself. */
    private volatile Consumer<String> workflowReadyConsumer;

    /**
     * Registers the persistent consumer for imported workflows.
     *
     * @param consumer the consumer, or {@code null} to unregister
     */
    public void setWorkflowConsumer(Consumer<String> consumer) {
        this.workflowConsumer = consumer;
    }

    /**
     * Registers a one-shot consumer for the next browser-converted workflow payload.
     *
     * @param consumer the consumer, or {@code null} to unregister
     */
    public void setWorkflowReadyConsumer(Consumer<String> consumer) {
        this.workflowReadyConsumer = consumer;
    }

    /**
     * Dispatches a workflow to the persistent consumer.
     *
     * @param workflowJson the workflow payload
     * @return {@code true} if a consumer was registered and notified
     */
    public boolean consumeWorkflow(String workflowJson) {
        Consumer<String> consumer = workflowConsumer;
        if (consumer != null) {
            consumer.accept(workflowJson);
            return true;
        }
        return false;
    }

    /**
     * Dispatches an API-format payload to the one-shot consumer and clears it immediately.
     *
     * @param apiJson the API-format workflow payload
     * @return {@code true} if a consumer was pending and notified
     */
    public boolean consumeWorkflowReady(String apiJson) {
        Consumer<String> consumer = workflowReadyConsumer;
        workflowReadyConsumer = null; // one-shot: clear immediately
        if (consumer != null) {
            consumer.accept(apiJson);
            return true;
        }
        return false;
    }
}
