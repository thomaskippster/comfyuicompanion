package com.thomaskippster.comfyuicompanion.service.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thomaskippster.comfyuicompanion.config.JacksonConfig;
import com.thomaskippster.comfyuicompanion.domain.graph.ComfyWorkflow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WorkflowBuilderTest {

    @Test
    public void testFluentBuilder() throws Exception {
        WorkflowBuilder builder = new WorkflowBuilder();

        String checkpointId = builder.addNode("CheckpointLoaderSimple")
                .param("ckpt_name", "juggernautXL.safetensors")
                .getId();

        String promptId = builder.addNode("CLIPTextEncode")
                .param("text", "epic viking portrait")
                .link("clip", checkpointId, 1)
                .getId();

        ComfyWorkflow workflow = builder.build();
        
        assertNotNull(workflow);
        assertNotNull(workflow.getNodes());
        assertEquals(2, workflow.getNodes().size());

        // Verify Checkpoint Node
        assertTrue(workflow.getNodes().containsKey(checkpointId));
        assertEquals("juggernautXL.safetensors", workflow.getNodes().get(checkpointId).getInputs().get("ckpt_name"));

        // Verify CLIP Node
        assertTrue(workflow.getNodes().containsKey(promptId));
        assertEquals("epic viking portrait", workflow.getNodes().get(promptId).getInputs().get("text"));
        
        // Verify link format
        Object clipLink = workflow.getNodes().get(promptId).getInputs().get("clip");
        assertTrue(clipLink instanceof List);
        List<?> linkList = (List<?>) clipLink;
        assertEquals(2, linkList.size());
        assertEquals(checkpointId, linkList.get(0));
        assertEquals(1, linkList.get(1));

        // Let's also check the JSON serialization works exactly as intended
        ObjectMapper mapper = new JacksonConfig().objectMapper();
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(workflow);
        System.out.println(json);
        assertTrue(json.contains("\"clip\" : [ \"" + checkpointId + "\", 1 ]"));
    }
}
