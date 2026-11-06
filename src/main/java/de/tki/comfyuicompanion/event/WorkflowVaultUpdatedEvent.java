package de.tki.comfyuicompanion.event;

import org.springframework.context.ApplicationEvent;

/**
 * Spring Application Event fired whenever a workflow is added, updated, or removed
 * in My Workflow Vault, allowing reactive UI updates across Blueprint Gallery and Image Lab.
 */
public class WorkflowVaultUpdatedEvent extends ApplicationEvent {

    private final String workflowName;
    private final ChangeType changeType;

    /**
     * Enumerates the types of mutations possible in My Workflow Vault.
     */
    public enum ChangeType {
        ADDED,
        REMOVED,
        MODELS_UPDATED,
        REFRESHED
    }

    /**
     * Constructs a new WorkflowVaultUpdatedEvent.
     *
     * @param source       the object on which the event initially occurred
     * @param workflowName name or identifier of the affected workflow
     * @param changeType   type of mutation performed
     */
    public WorkflowVaultUpdatedEvent(Object source, String workflowName, ChangeType changeType) {
        super(source);
        this.workflowName = workflowName;
        this.changeType = changeType;
    }

    /**
     * Returns the name of the affected workflow.
     *
     * @return workflow name or identifier
     */
    public String getWorkflowName() {
        return workflowName;
    }

    /**
     * Returns the mutation type.
     *
     * @return change type enum
     */
    public ChangeType getChangeType() {
        return changeType;
    }
}
