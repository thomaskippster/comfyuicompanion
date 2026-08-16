package com.thomaskippster.comfyuicompanion;

import com.fasterxml.jackson.databind.JsonNode;
import com.thomaskippster.comfyuicompanion.client.ComfyHttpClient;
import com.thomaskippster.comfyuicompanion.domain.graph.ComfyWorkflow;
import com.thomaskippster.comfyuicompanion.service.graph.WorkflowBuilder;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.net.Socket;
import java.util.UUID;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Disabled("End-to-end integration test requiring live ComfyUI instance with specific LTXV model")
public class ComfyUIEndToEndTest {

    @Test
    public void testWorkflowAndReceiveImage() throws Exception {
        assumeTrue(isComfyUiAvailable(), "ComfyUI is not available at 127.0.0.1:8188; skipping end-to-end test");

        // Build an LTXV / generic KSampler graph that we know works with the user's available models
        WorkflowBuilder builder = new WorkflowBuilder();
        
        String checkpointId = builder.addNode("CheckpointLoaderSimple")
                // Using the model we discovered is available in ComfyUI
                .param("ckpt_name", "LTXV\\ltx-video-2b-v0.9.5.safetensors")
                .getId();

        String clipId = builder.addNode("CLIPLoader")
                .param("clip_name", "t5\\t5xxl_fp16.safetensors")
                .param("type", "ltxv")
                .getId();

        String positiveId = builder.addNode("CLIPTextEncode")
                .param("text", "A cinematic portrait of a man holding a cardboard sign that says 'Hallo'. Photorealistic.")
                .link("clip", clipId, 0)
                .getId();

        String negativeId = builder.addNode("CLIPTextEncode")
                .param("text", "ugly, blurry, deformed, low quality")
                .link("clip", clipId, 0)
                .getId();

        String emptyLatentId = builder.addNode("EmptyLatentImage")
                .param("width", 1024).param("height", 1024).param("batch_size", 1).getId();

        String samplerId = builder.addNode("KSampler")
                .param("seed", System.currentTimeMillis() % 1000000)
                .param("steps", 20).param("cfg", 7.0)
                .param("sampler_name", "euler").param("scheduler", "normal").param("denoise", 1.0)
                .link("model", checkpointId, 0)
                .link("positive", positiveId, 0)
                .link("negative", negativeId, 0)
                .link("latent_image", emptyLatentId, 0).getId();

        String vaeDecodeId = builder.addNode("VAEDecode")
                .link("samples", samplerId, 0).link("vae", checkpointId, 2).getId();

        builder.addNode("SaveImage")
                .param("filename_prefix", "Cognitive_Router")
                .link("images", vaeDecodeId, 0);

        ComfyWorkflow workflow = builder.build();

        ComfyHttpClient client = new ComfyHttpClient("http://127.0.0.1:8188");
        String clientId = UUID.randomUUID().toString();
        
        System.out.println("Sende Workflow an ComfyUI...");
        String promptId = client.triggerWorkflow(workflow, clientId).block();
        System.out.println("Prompt ID erhalten: " + promptId);

        boolean done = false;
        while (!done) {
            JsonNode history = client.getHistory(promptId).block();
            if (history != null && history.has(promptId)) {
                JsonNode outputs = history.get(promptId).get("outputs");
                System.out.println("Generierung erfolgreich! Outputs: " + outputs);
                done = true;
            } else {
                System.out.println("Warte auf Abschluss...");
                Thread.sleep(2000);
            }
        }
    }

    private boolean isComfyUiAvailable() {
        try (Socket socket = new Socket("127.0.0.1", 8188)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
