package de.tki.comfyuicompanion.domain.graph;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Represents a complete ComfyUI workflow consisting of multiple nodes.
 */
public class ComfyWorkflow {

    private Map<String, ComfyNode> nodes = new java.util.LinkedHashMap<>();

    /**
     * Constructs an empty ComfyWorkflow.
     */
    public ComfyWorkflow() {
    }

    /** @return the map of all nodes in this workflow */
    @JsonValue
    public Map<String, ComfyNode> getNodes() {
        return nodes;
    }

    /** @param nodes the map of nodes to set */
    public void setNodes(Map<String, ComfyNode> nodes) {
        this.nodes = nodes;
    }

    /**
     * Adds a node to the workflow.
     *
     * @param id   the unique node identifier
     * @param node the ComfyNode instance
     */
    public void addNode(String id, ComfyNode node) {
        this.nodes.put(id, node);
    }

    /**
     * Retrieves a node by its identifier.
     *
     * @param id the node identifier
     * @return the corresponding ComfyNode, or null if not found
     */
    public ComfyNode getNode(String id) {
        return this.nodes.get(id);
    }
}
