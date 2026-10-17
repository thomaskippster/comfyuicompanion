package de.tki.comfyuicompanion.dto.acl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Anti-Corruption Layer (ACL) request object for workflow custom node analysis.
 * Validates incoming JSON graphs before delegating to internal resolution services.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowAnalysisAclRequest(JsonNode workflow) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Compact constructor validating that the workflow is non-null.
     *
     * @param workflow the root JSON node of the workflow graph
     * @throws IllegalArgumentException if workflow is null or null node
     */
    public WorkflowAnalysisAclRequest {
        if (workflow == null || workflow.isNull()) {
            throw new IllegalArgumentException("Workflow payload must not be null or empty.");
        }
    }

    /**
     * Jackson JSON creator allowing root-level JSON binding.
     *
     * @param node the parsed JSON node
     * @return the constructed ACL request
     */
    @JsonCreator
    public static WorkflowAnalysisAclRequest fromJson(JsonNode node) {
        if (node != null && node.has("workflow")) {
            JsonNode inner = node.get("workflow");
            if (inner.isTextual()) {
                try {
                    return new WorkflowAnalysisAclRequest(MAPPER.readTree(inner.asText()));
                } catch (Exception ignored) {
                    return new WorkflowAnalysisAclRequest(inner);
                }
            } else if (inner.isObject()) {
                return new WorkflowAnalysisAclRequest(inner);
            }
        }
        return new WorkflowAnalysisAclRequest(node);
    }

    /**
     * Factory method parsing a raw JSON string into a validated ACL request.
     *
     * @param json the raw workflow JSON string
     * @return the constructed ACL request
     */
    public static WorkflowAnalysisAclRequest fromString(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Workflow JSON string must not be empty.");
        }
        try {
            return fromJson(MAPPER.readTree(json));
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed workflow JSON payload: " + e.getMessage(), e);
        }
    }

    /**
     * Translates this ACL request into a sanitized domain workflow JSON string.
     *
     * @return clean domain JSON string
     */
    public String toDomainWorkflowJson() {
        return workflow.toString();
    }
}
