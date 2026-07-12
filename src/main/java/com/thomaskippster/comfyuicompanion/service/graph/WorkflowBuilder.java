package com.thomaskippster.comfyuicompanion.service.graph;

import com.thomaskippster.comfyuicompanion.domain.graph.ComfyNode;
import com.thomaskippster.comfyuicompanion.domain.graph.ComfyWorkflow;

import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class WorkflowBuilder {

    private final ComfyWorkflow workflow;
    private final AtomicInteger idGenerator;

    public WorkflowBuilder() {
        this.workflow = new ComfyWorkflow();
        this.workflow.setNodes(new HashMap<>());
        // ID Generator starts at 1, matching common ComfyUI node ID generation
        this.idGenerator = new AtomicInteger(1);
    }

    /**
     * Creates a new node in the workflow and returns a builder to configure it.
     *
     * @param classType The class_type of the ComfyUI node (e.g., "CheckpointLoaderSimple")
     * @return A NodeBuilder instance for fluent configuration
     */
    public NodeBuilder addNode(String classType) {
        String nodeId = String.valueOf(idGenerator.getAndIncrement());
        ComfyNode node = new ComfyNode();
        node.setClassType(classType);
        node.setInputs(new HashMap<>());
        workflow.addNode(nodeId, node);
        return new NodeBuilder(nodeId, node);
    }

    /**
     * Builds and returns the final ComfyWorkflow object.
     */
    public ComfyWorkflow build() {
        return this.workflow;
    }

    /**
     * Inner builder class to allow fluent chaining for a specific node.
     */
    public class NodeBuilder {
        private final String nodeId;
        private final ComfyNode node;

        public NodeBuilder(String nodeId, ComfyNode node) {
            this.nodeId = nodeId;
            this.node = node;
        }

        /**
         * Sets a primitive parameter for the node.
         *
         * @param key   The parameter name (e.g., "seed", "text")
         * @param value The value
         * @return The current NodeBuilder
         */
        public NodeBuilder param(String key, Object value) {
            this.node.getInputs().put(key, value);
            return this;
        }

        /**
         * Links an input of this node to the output of another node.
         * Formats it to the ComfyUI API standard: ["nodeId", outputIndex]
         *
         * @param inputKey          The input parameter name (e.g., "clip", "model")
         * @param sourceNodeId      The ID of the source node
         * @param sourceOutputIndex The index of the output from the source node
         * @return The current NodeBuilder
         */
        public NodeBuilder link(String inputKey, String sourceNodeId, int sourceOutputIndex) {
            this.node.getInputs().put(inputKey, Arrays.asList(sourceNodeId, sourceOutputIndex));
            return this;
        }

        /**
         * Returns the generated ID of this node, ending the fluent chain for this node.
         *
         * @return The node ID as String
         */
        public String getId() {
            return this.nodeId;
        }
    }
}
