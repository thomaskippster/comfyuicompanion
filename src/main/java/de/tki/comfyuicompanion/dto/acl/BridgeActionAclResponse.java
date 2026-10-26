package de.tki.comfyuicompanion.dto.acl;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Anti-Corruption Layer (ACL) response object for ComfyUI Canvas Bridge actions.
 * Encapsulates the standardized REST response payload, protecting clients from internal changes
 * and eliminating error-prone manual JSON string formatting.
 *
 * @param status  the outcome status of the bridge operation (e.g. "success", "accepted", "error")
 * @param message an optional descriptive message explaining the operation outcome
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BridgeActionAclResponse(String status, String message) {

    /**
     * Compact constructor validating that the status is non-null and non-blank.
     *
     * @param status  the outcome status
     * @param message an optional descriptive message
     * @throws IllegalArgumentException if status is null or blank
     */
    public BridgeActionAclResponse {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Response status must not be null or blank.");
        }
    }

    /**
     * Factory method creating a successful response with a descriptive message.
     *
     * @param message the success message
     * @return a new BridgeActionAclResponse instance with status "success"
     */
    public static BridgeActionAclResponse success(String message) {
        return new BridgeActionAclResponse("success", message);
    }

    /**
     * Factory method creating an accepted response (e.g., for asynchronous callbacks).
     *
     * @return a new BridgeActionAclResponse instance with status "accepted"
     */
    public static BridgeActionAclResponse accepted() {
        return new BridgeActionAclResponse("accepted", null);
    }

    /**
     * Factory method creating an error response with a descriptive reason.
     *
     * @param message the error reason
     * @return a new BridgeActionAclResponse instance with status "error"
     */
    public static BridgeActionAclResponse error(String message) {
        return new BridgeActionAclResponse("error", message);
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * Serializes this response object to a valid JSON string.
     *
     * @return serialized JSON payload
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return "{\"status\":\"" + status + "\"}";
        }
    }
}
