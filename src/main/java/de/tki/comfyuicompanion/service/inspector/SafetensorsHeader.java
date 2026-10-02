package de.tki.comfyuicompanion.service.inspector;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the header of a Safetensors file, holding metadata and tensor information.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SafetensorsHeader {

    @JsonProperty("__metadata__")
    private Map<String, String> metadata;

    private Map<String, TensorInfo> tensors = new HashMap<>();

    /**
     * Gets the metadata from the safetensors header.
     * @return The metadata map.
     */
    public Map<String, String> getMetadata() { return metadata; }

    /**
     * Sets the metadata for the safetensors header.
     * @param metadata The metadata map.
     */
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }

    /**
     * Adds a tensor to the safetensors header if the name is not "__metadata__".
     * @param name The name of the tensor.
     * @param info The tensor information.
     */
    @JsonAnySetter
    public void addTensor(String name, TensorInfo info) {
        if (!"__metadata__".equals(name) && info != null) {
            tensors.put(name, info);
        }
    }

    /**
     * Gets the map of all tensors.
     * @return The tensor map.
     */
    public Map<String, TensorInfo> getTensors() { return tensors; }
}
