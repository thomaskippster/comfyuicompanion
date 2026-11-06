package de.tki.comfyuicompanion.service.workflow.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tki.comfyuicompanion.domain.ModelInfo;

import java.util.List;
import java.util.Set;

/**
 * Immutable context record capturing all parameters required across workflow sanitization pipeline stages.
 *
 * @param workflow        root LiteGraph canvas ObjectNode
 * @param registeredNodes set of node class names currently registered in ComfyUI
 * @param installedModels list of locally detected model files for component splitting
 * @param objectMapper    JSON object mapper for AST mutations
 */
public record WorkflowSanitizationContext(
        ObjectNode workflow,
        Set<String> registeredNodes,
        List<ModelInfo> installedModels,
        ObjectMapper objectMapper
) {
    /**
     * Compact constructor guaranteeing non-null collections.
     */
    public WorkflowSanitizationContext {
        registeredNodes = registeredNodes != null ? registeredNodes : Set.of();
        installedModels = installedModels != null ? installedModels : List.of();
    }
}
