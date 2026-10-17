package de.tki.comfyuicompanion.dto.acl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Anti-Corruption Layer (ACL) request object for browser graphToPrompt conversion callbacks.
 * Enforces validation and protects internal consumers from malformed graph representations.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowReadyAclRequest(JsonNode promptPayload) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Compact constructor validating that the prompt payload is non-null.
     *
     * @param promptPayload the root JSON node of the prompt
     * @throws IllegalArgumentException if promptPayload is null or null node
     */
    public WorkflowReadyAclRequest {
        if (promptPayload == null || promptPayload.isNull()) {
            throw new IllegalArgumentException("Prompt payload must not be null or empty.");
        }
    }

    /**
     * Jackson JSON creator allowing root-level JSON binding.
     *
     * @param node the parsed JSON node
     * @return the constructed ACL request
     */
    @JsonCreator
    public static WorkflowReadyAclRequest fromJson(JsonNode node) {
        return new WorkflowReadyAclRequest(node);
    }

    /**
     * Factory method parsing a raw JSON string into a validated ACL request.
     *
     * @param json the raw prompt JSON string
     * @return the constructed ACL request
     */
    public static WorkflowReadyAclRequest fromString(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Prompt payload string must not be empty.");
        }
        try {
            return new WorkflowReadyAclRequest(MAPPER.readTree(json));
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed prompt JSON payload: " + e.getMessage(), e);
        }
    }

    /**
     * Translates this ACL request into a sanitized domain prompt JSON string.
     *
     * @return clean domain JSON string
     */
    public String toDomainPromptJson() {
        return promptPayload.toString();
    }
}
