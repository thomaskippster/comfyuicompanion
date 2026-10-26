package de.tki.comfyuicompanion.dto.acl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Anti-Corruption Layer (ACL) request object for refreshing or reloading models in ComfyUI.
 * Encapsulates the JSON contract required by the ComfyUI model refresh endpoint.
 *
 * @param forceReload whether to force a complete cache invalidation and reload
 */
public record RefreshModelsAclRequest(
        @JsonProperty("force_reload") boolean forceReload
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Singleton instance requesting a forced cache reload.
     */
    public static final RefreshModelsAclRequest FORCE = new RefreshModelsAclRequest(true);

    /**
     * Singleton instance requesting a standard model reload.
     */
    public static final RefreshModelsAclRequest NORMAL = new RefreshModelsAclRequest(false);

    /**
     * Factory method creating a request for the specified reload behavior.
     *
     * @param force whether reload should be forced
     * @return typed ACL request record
     */
    public static RefreshModelsAclRequest of(boolean force) {
        return force ? FORCE : NORMAL;
    }

    /**
     * Serializes this request record to a valid JSON string.
     *
     * @return JSON string payload
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize RefreshModelsAclRequest to JSON", e);
        }
    }
}
