package com.thomaskippster.comfyuicompanion.domain.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class ComfyWorkflowSerializationTest {

    @Test
    public void testSerialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        // Mimic our JacksonConfig
        mapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);

        ComfyWorkflow workflow = new ComfyWorkflow();
        Map<String, ComfyNode> nodes = new HashMap<>();

        ComfyNode node3 = new ComfyNode();
        node3.setClassType("KSampler");
        Map<String, Object> inputs3 = new HashMap<>();
        inputs3.put("seed", 12345);
        inputs3.put("model", Arrays.asList("4", 0));
        node3.setInputs(inputs3);

        ComfyNode node4 = new ComfyNode();
        node4.setClassType("CheckpointLoaderSimple");
        Map<String, Object> inputs4 = new HashMap<>();
        inputs4.put("ckpt_name", "v1-5-pruned-emaonly.safetensors");
        node4.setInputs(inputs4);

        nodes.put("3", node3);
        nodes.put("4", node4);
        workflow.setNodes(nodes);

        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(workflow);

        System.out.println("Serialized workflow_api.json:");
        System.out.println(json);

        // Assertions to verify the structure
        assertTrue(json.contains("\"3\""));
        assertTrue(json.contains("\"4\""));
        assertTrue(json.contains("\"class_type\" : \"KSampler\""));
        assertTrue(json.contains("\"class_type\" : \"CheckpointLoaderSimple\""));
        assertFalse(json.contains("\"nodes\" : {")); // Ensure we don't have a "nodes" root wrapper
    }
}
