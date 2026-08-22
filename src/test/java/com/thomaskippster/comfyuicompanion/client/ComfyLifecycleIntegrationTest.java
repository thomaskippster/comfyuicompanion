package com.thomaskippster.comfyuicompanion.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.thomaskippster.comfyuicompanion.domain.graph.ComfyWorkflow;
import com.thomaskippster.comfyuicompanion.event.WorkflowCompletedEvent;
import com.thomaskippster.comfyuicompanion.service.graph.WorkflowBuilder;
import com.thomaskippster.comfyuicompanion.websocket.ComfyWebSocketClient;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.ContextConfiguration;
import org.junit.jupiter.api.extension.ExtendWith;

import org.junit.jupiter.api.Assumptions;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Live WebSocket integration test requiring running ComfyUI server and full WebSocket container")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {ComfyHttpClient.class, ComfyWebSocketClient.class, com.thomaskippster.comfyuicompanion.websocket.ComfyWebSocketHandler.class, com.thomaskippster.comfyuicompanion.config.JacksonConfig.class})
public class ComfyLifecycleIntegrationTest {

    @Autowired
    private ComfyHttpClient httpClient;

    @Autowired
    private ComfyWebSocketClient webSocketClient;

    private final CountDownLatch completionLatch = new CountDownLatch(1);
    private final AtomicBoolean workflowCompleted = new AtomicBoolean(false);

    @EventListener
    public void handleWorkflowCompleted(WorkflowCompletedEvent event) {
        workflowCompleted.set(true);
        completionLatch.countDown(); // Unblock the test thread
    }
    
    private boolean isServerRunning() {
        try {
            // Check if server is running by requesting system stats
            httpClient.getSystemStats().block();
            return true;
        } catch (WebClientRequestException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    public void testFullLifecycle() throws Exception {
        Assumptions.assumeTrue(isServerRunning(), "ComfyUI Server is not running. Skipping integration test.");

        // 1. Build a working Workflow (LTXV / T5XXL) that we verified exists locally
        WorkflowBuilder builder = new WorkflowBuilder();
        String checkpointId = builder.addNode("CheckpointLoaderSimple")
                .param("ckpt_name", "LTXV\\ltx-video-2b-v0.9.5.safetensors").getId();
                
        String clipId = builder.addNode("CLIPLoader")
                .param("clip_name", "t5\\t5xxl_fp16.safetensors")
                .param("type", "ltxv").getId();
        
        String positiveId = builder.addNode("CLIPTextEncode")
                .param("text", "a beautiful landscape").link("clip", clipId, 0).getId();
        
        String emptyLatentId = builder.addNode("EmptyLatentImage")
                .param("width", 512).param("height", 512).param("batch_size", 1).getId();
        
        String samplerId = builder.addNode("KSampler")
                .param("seed", 12345L).param("steps", 1).param("cfg", 8.0)
                .param("sampler_name", "euler").param("scheduler", "normal").param("denoise", 1.0)
                .link("model", checkpointId, 0).link("positive", positiveId, 0)
                .link("negative", positiveId, 0).link("latent_image", emptyLatentId, 0).getId();
        
        String vaeDecodeId = builder.addNode("VAEDecode")
                .link("samples", samplerId, 0).link("vae", checkpointId, 2).getId();
        
        String saveId = builder.addNode("SaveImage")
                .param("filename_prefix", "CompanionTest").link("images", vaeDecodeId, 0).getId();

        ComfyWorkflow workflow = builder.build();
        String clientId = UUID.randomUUID().toString();

        // 2. Connect WebSocket to listen for progress and completion
        webSocketClient.connect(clientId);

        // 3. Trigger the workflow via HTTP POST
        String promptId = httpClient.triggerWorkflow(workflow, clientId).block();
        assertNotNull(promptId, "Prompt ID should not be null");

        // 4. Block thread and wait for WebSocket to report completion via Spring Event
        boolean completedInTime = completionLatch.await(60, TimeUnit.SECONDS);
        assertTrue(completedInTime, "Workflow did not finish within timeout");
        assertTrue(workflowCompleted.get(), "Workflow completion flag should be true");

        // 5. Fetch History to extract the generated filename
        JsonNode history = httpClient.getHistory(promptId).block();
        assertNotNull(history, "History should not be null");
        
        // Extract filename from history json response (simplified parsing)
        JsonNode outputs = history.get(promptId).get("outputs").get(saveId);
        String filename = outputs.get("images").get(0).get("filename").asText();
        assertNotNull(filename, "Filename should have been parsed from history");

        // 6. Download the generated Asset
        byte[] imageBytes = httpClient.downloadAsset(filename).block();
        assertNotNull(imageBytes, "Image data should not be null");
        assertTrue(imageBytes.length > 0, "Image byte array should not be empty");
    }
}
