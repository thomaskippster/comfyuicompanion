package de.tki.comfyuicompanion.domain.graph;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents a single node within a ComfyUI workflow graph.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ComfyNode {

    @JsonProperty("class_type")
    private String classType;
    
    private Map<String, Object> inputs;

    /**
     * Constructs an empty ComfyNode.
     */
    public ComfyNode() {
    }

    /** @return the node class type */
    public String getClassType() {
        return classType;
    }

    /** @param classType the node class type to set */
    public void setClassType(String classType) {
        this.classType = classType;
    }

    /** @return the map of inputs for this node */
    public Map<String, Object> getInputs() {
        return inputs;
    }

    /** @param inputs the map of inputs to set */
    public void setInputs(Map<String, Object> inputs) {
        this.inputs = inputs;
    }
}
