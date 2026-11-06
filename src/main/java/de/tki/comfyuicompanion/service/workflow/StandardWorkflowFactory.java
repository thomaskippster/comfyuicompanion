package de.tki.comfyuicompanion.service.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tki.comfyuicompanion.domain.ModelInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Enterprise factory synthesizing standardized LiteGraph visual canvas workflows
 * using a single generic workflow builder driven by declarative architecture profiles.
 */
@Service
public class StandardWorkflowFactory {

    private static final Logger log = LoggerFactory.getLogger(StandardWorkflowFactory.class);

    private final ArchitectureProfileCatalog profileCatalog;
    private final GenericWorkflowBuilder workflowBuilder;

    @Autowired
    public StandardWorkflowFactory(ArchitectureProfileCatalog profileCatalog, GenericWorkflowBuilder workflowBuilder) {
        this.profileCatalog = profileCatalog != null ? profileCatalog : new ArchitectureProfileCatalog();
        this.workflowBuilder = workflowBuilder != null ? workflowBuilder : new GenericWorkflowBuilder(new ObjectMapper());
    }

    public StandardWorkflowFactory(ObjectMapper objectMapper) {
        this(new ArchitectureProfileCatalog(), new GenericWorkflowBuilder(objectMapper));
    }

    /**
     * Resolves the matching architecture profile and synthesizes a standard LiteGraph workflow.
     *
     * @param modelInfo  model metadata
     * @param userPrompt custom user prompt text
     * @return an {@link ObjectNode} representing the complete LiteGraph diagram
     */
    public ObjectNode createWorkflow(ModelInfo modelInfo, String userPrompt) {
        String archKey = resolveArchitectureKey(modelInfo);
        ArchitectureProfile profile = profileCatalog.resolveProfile(archKey);
        log.debug("Resolved generic architecture profile '{}' for model '{}'",
                profile.id(), modelInfo != null ? modelInfo.getName() : "unknown");

        return workflowBuilder.buildWorkflow(profile, modelInfo, userPrompt);
    }

    private String resolveArchitectureKey(ModelInfo info) {
        if (info == null) return "sdxl";
        if (info.getBase() != null && !info.getBase().isBlank()) {
            return info.getBase().toLowerCase(Locale.ROOT);
        }
        String name = (info.getFilename() != null && !info.getFilename().isBlank() ? info.getFilename() : info.getName());
        return name != null ? name.toLowerCase(Locale.ROOT) : "sdxl";
    }
}
