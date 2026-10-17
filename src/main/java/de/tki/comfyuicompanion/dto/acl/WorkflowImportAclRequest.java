package de.tki.comfyuicompanion.dto.acl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Anti-Corruption Layer (ACL) request object for importing external ComfyUI workflows.
 * Insulates the internal domain model from arbitrary external payload variations,
 * validating input structure and providing a normalized domain representation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowImportAclRequest(JsonNode payload) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Compact constructor validating that the incoming payload is non-null.
     *
     * @param payload the root JSON node of the workflow
     * @throws IllegalArgumentException if payload is null or null node
     */
    public WorkflowImportAclRequest {
        if (payload == null || payload.isNull()) {
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
    public static WorkflowImportAclRequest fromJson(JsonNode node) {
        if (node != null && node.has("workflow")) {
            JsonNode inner = node.get("workflow");
            if (inner.isTextual()) {
                try {
                    return new WorkflowImportAclRequest(MAPPER.readTree(inner.asText()));
                } catch (Exception ignored) {
                    return new WorkflowImportAclRequest(inner);
                }
            } else if (inner.isObject()) {
                return new WorkflowImportAclRequest(inner);
            }
        }
        return new WorkflowImportAclRequest(node);
    }

    /**
     * Factory method parsing a raw JSON string into a validated ACL request.
     *
     * @param json the raw workflow JSON string
     * @return the constructed ACL request
     */
    public static WorkflowImportAclRequest fromString(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Workflow payload string must not be empty.");
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
        return payload.toString();
    }
}
