package de.tki.comfyuicompanion.websocket;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Represents a WebSocket event received from the ComfyUI server.
 * Contains the event type and an arbitrary JSON data payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ComfyEvent {
    private String type;
    private JsonNode data;

    /**
     * Returns the type of the event (e.g., "progress", "executing").
     *
     * @return the event type
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the type of the event.
     *
     * @param type the event type to set
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Returns the JSON payload data associated with the event.
     *
     * @return the event data as a JSON node
     */
    public JsonNode getData() {
        return data;
    }

    /**
     * Sets the JSON payload data for the event.
     *
     * @param data the JSON node containing event data
     */
    public void setData(JsonNode data) {
        this.data = data;
    }
}
