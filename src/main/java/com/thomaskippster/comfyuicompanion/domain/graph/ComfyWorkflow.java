package com.thomaskippster.comfyuicompanion.domain.graph;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonValue;

public class ComfyWorkflow {

    private Map<String, ComfyNode> nodes = new java.util.LinkedHashMap<>();

    public ComfyWorkflow() {
    }

    @JsonValue
    public Map<String, ComfyNode> getNodes() {
        return nodes;
    }

    public void setNodes(Map<String, ComfyNode> nodes) {
        this.nodes = nodes;
    }

    public void addNode(String id, ComfyNode node) {
        this.nodes.put(id, node);
    }

    public ComfyNode getNode(String id) {
        return this.nodes.get(id);
    }
}
