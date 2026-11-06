package de.tki.comfyuicompanion.service.workflow;

import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.domain.WorkflowDescriptor;
import de.tki.comfyuicompanion.domain.WorkflowSourceType;

import java.awt.Window;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Strategy interface defining ecosystem-specific operations for ComfyUI workflows.
 * Encapsulates workflow serialization, image retrieval, model download provisioning,
 * and descriptor transformation following the GoF Strategy Pattern.
 *
 * @author Senior Java Developer
 */
public interface IWorkflowSourceStrategy {

    /**
     * Identifies the specific workflow source ecosystem handled by this strategy.
     *
     * @return source type enumeration
     */
    WorkflowSourceType getSourceType();

    /**
     * Asynchronously retrieves the raw JSON content of the workflow.
     *
     * @param descriptor the workflow descriptor
     * @return CompletableFuture resolving to the workflow JSON string
     */
    CompletableFuture<String> resolveWorkflowJson(WorkflowDescriptor descriptor);

    /**
     * Asynchronously loads raw image bytes for preview and banner displays.
     *
     * @param descriptor the workflow descriptor
     * @return CompletableFuture resolving to image bytes, or empty array if unavailable
     */
    CompletableFuture<byte[]> fetchPreviewImageBytes(WorkflowDescriptor descriptor);

    /**
     * Determines whether direct downloading of missing model dependencies is supported
     * from this source context.
     * <p>
     * Per business rule, this is only allowed within {@link WorkflowSourceType#VAULT}.
     * </p>
     *
     * @return true if download of missing models is permissible
     */
    boolean canDownloadMissingModels();

    /**
     * Initiates downloading of missing models required by the workflow.
     *
     * @param descriptor the workflow descriptor
     * @param missingModels list of models that are not available locally
     * @param parent window context for progress dialogs
     */
    void downloadMissingModels(WorkflowDescriptor descriptor, List<ModelInfo> missingModels, Window parent);

    /**
     * Converts a provider-specific domain model into the unified {@link WorkflowDescriptor}.
     *
     * @param sourceEntry raw model object (e.g. ComfyRegistryWorkflow, CivitaiModelEntry, etc.)
     * @return normalized workflow descriptor
     */
    WorkflowDescriptor toWorkflowDescriptor(Object sourceEntry);
}
