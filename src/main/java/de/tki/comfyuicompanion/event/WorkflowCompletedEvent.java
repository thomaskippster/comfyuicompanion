package de.tki.comfyuicompanion.event;

/**
 * Event published when ComfyUI indicates that a workflow execution has completed.
 * Can be scoped to a specific promptId or clientId.
 */
public class WorkflowCompletedEvent {
    private final String promptId;
    private final String clientId;

    /**
     * Constructs a generic WorkflowCompletedEvent without prompt scoping.
     */
    public WorkflowCompletedEvent() {
        this(null, null);
    }

    /**
     * Constructs a scoped WorkflowCompletedEvent.
     *
     * @param promptId The identifier of the finished prompt
     * @param clientId The identifier of the requesting client
     */
    public WorkflowCompletedEvent(String promptId, String clientId) {
        this.promptId = promptId;
        this.clientId = clientId;
    }

    /**
     * Gets the identifier of the completed prompt.
     *
     * @return promptId or null
     */
    public String getPromptId() {
        return promptId;
    }

    /**
     * Gets the client identifier.
     *
     * @return clientId or null
     */
    public String getClientId() {
        return clientId;
    }
}
