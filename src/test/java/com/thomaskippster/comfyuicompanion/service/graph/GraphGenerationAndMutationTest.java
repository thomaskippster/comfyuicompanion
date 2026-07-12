package com.thomaskippster.comfyuicompanion.service.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thomaskippster.comfyuicompanion.config.JacksonConfig;
import com.thomaskippster.comfyuicompanion.domain.graph.ComfyWorkflow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GraphGenerationAndMutationTest {

    @Test
    public void testCompleteTextToImageGraph() throws Exception {
        // --- 1. BUILD THE GRAPH ---
        WorkflowBuilder builder = new WorkflowBuilder();

        String checkpointId = builder.addNode("CheckpointLoaderSimple")
                .param("ckpt_name", "v1-5-pruned-emaonly.safetensors")
                .getId();

        String positiveId = builder.addNode("CLIPTextEncode")
                .param("text", "a beautiful landscape")
                .link("clip", checkpointId, 1)
                .getId();

        String negativeId = builder.addNode("CLIPTextEncode")
                .param("text", "blurry, text")
                .link("clip", checkpointId, 1)
                .getId();

        String emptyLatentId = builder.addNode("EmptyLatentImage")
                .param("width", 512)
                .param("height", 512)
                .param("batch_size", 1)
                .getId();

        String samplerId = builder.addNode("KSampler")
                .param("seed", 12345L)
                .param("steps", 20)
                .param("cfg", 8.0)
                .param("sampler_name", "euler")
                .param("scheduler", "normal")
                .param("denoise", 1.0)
                .link("model", checkpointId, 0)
                .link("positive", positiveId, 0)
                .link("negative", negativeId, 0)
                .link("latent_image", emptyLatentId, 0)
                .getId();

        String vaeDecodeId = builder.addNode("VAEDecode")
                .link("samples", samplerId, 0)
                .link("vae", checkpointId, 2)
                .getId();

        String saveImageId = builder.addNode("SaveImage")
                .param("filename_prefix", "ComfyUI")
                .link("images", vaeDecodeId, 0)
                .getId();

        ComfyWorkflow workflow = builder.build();

        ObjectMapper mapper = new JacksonConfig().objectMapper();
        String initialJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(workflow);
        System.out.println("--- INITIAL GRAPH ---");
        System.out.println(initialJson);

        // --- 2. MUTATE THE GRAPH ---
        GraphMutationService mutationService = new GraphMutationService();
        mutationService.swapCheckpoint(workflow, "juggernautXL.safetensors");
        mutationService.updatePrompt(workflow, "cyberpunk city, neon lights", "ugly, bad anatomy");
        mutationService.setSeed(workflow, 9999999L);

        String mutatedJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(workflow);
        System.out.println("\n--- MUTATED GRAPH ---");
        System.out.println(mutatedJson);

        // --- 3. VERIFY MUTATIONS ---
        assertEquals("juggernautXL.safetensors", workflow.getNodes().get(checkpointId).getInputs().get("ckpt_name"));
        assertEquals("cyberpunk city, neon lights", workflow.getNodes().get(positiveId).getInputs().get("text"));
        assertEquals("ugly, bad anatomy", workflow.getNodes().get(negativeId).getInputs().get("text"));
        assertEquals(9999999L, workflow.getNodes().get(samplerId).getInputs().get("seed"));
    }
}
