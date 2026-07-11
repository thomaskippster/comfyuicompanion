package de.tki.comfymodels.exception;

/**
 * Exception für Workflow-Verarbeitungsfehler.
 * Wird verwendet bei:
 * - Invaliden Workflow-JSONs
 * - Workflow-Parse-Fehlern
 * - Missing Model Detection
 */
public class WorkflowException extends CompanionException {
    
    private final String workflowId;
    
    public WorkflowException(String message) {
        super(message);
        this.workflowId = null;
    }
    
    public WorkflowException(String message, Throwable cause) {
        super(message, cause);
        this.workflowId = null;
    }
    
    public WorkflowException(String message, String workflowId) {
        super(message, "workflowId=" + workflowId);
        this.workflowId = workflowId;
    }
    
    public WorkflowException(String message, String workflowId, Throwable cause) {
        super(message, "workflowId=" + workflowId, cause);
        this.workflowId = workflowId;
    }
    
    public String getWorkflowId() {
        return workflowId;
    }
}
