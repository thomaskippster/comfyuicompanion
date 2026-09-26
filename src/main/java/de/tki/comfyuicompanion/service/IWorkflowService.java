package de.tki.comfyuicompanion.service;

import java.io.File;
import java.io.IOException;

/**
 * Represents the i workflow service interface.
 */
public interface IWorkflowService {
    /**
     * Extracts the workflow JSON string from a file (JSON or PNG).
     */
    String extractWorkflow(File file) throws IOException;
}
