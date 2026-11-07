package de.tki.comfyuicompanion.service.pipeline;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class VideoWorkflowBuilderTest {

    @Test
    void testExactAndSemanticModelMatchingForT5AndClip() {
        ComfyNodeInspectionClient client = new ComfyNodeInspectionClient(null);
        List<String> available = List.of(
                "FLUX1/flux1-schnell-fp8.safetensors",
                "clip_l.safetensors",
                "t5xxl_fp8_e4m3fn.safetensors",
                "wan_2.1_vae.safetensors"
        );

        // Exact match
        assertEquals("clip_l.safetensors", client.findExactModelName("clip_l.safetensors", available));

        // Semantic match: t5xxl_fp16 requested, but only t5xxl_fp8 available
        assertEquals("t5xxl_fp8_e4m3fn.safetensors", client.findExactModelName("t5xxl_fp16.safetensors", available));
    }

    @Test
    void testSanitizeDualClipLoader() {
        ComfyNodeInspectionClient client = new ComfyNodeInspectionClient(null) {
            @Override
            public List<String> fetchObjectInfoOptions(String serverUrl, String nodeClass, String inputName) {
                if ("CLIPLoader".equals(nodeClass) || "DualCLIPLoader".equals(nodeClass)) {
                    return List.of("clip_l.safetensors", "t5xxl_fp8_e4m3fn.safetensors");
                }
                return List.of();
            }

            @Override
            public List<String> fetchAvailableCheckpoints(String serverUrl) {
                return List.of();
            }
        };

        VideoWorkflowBuilder builder = new VideoWorkflowBuilder(client);

        JSONObject workflow = new JSONObject();
        JSONObject dualClipNode = new JSONObject();
        dualClipNode.put("class_type", "DualCLIPLoader");
        JSONObject inputs = new JSONObject();
        inputs.put("clip_name1", "clip_l.safetensors");
        inputs.put("clip_name2", "t5xxl_fp16.safetensors");
        dualClipNode.put("inputs", inputs);
        workflow.put("10049", dualClipNode);

        builder.sanitizeWorkflow(workflow, "http://localhost:8188");

        assertEquals("clip_l.safetensors", inputs.getString("clip_name1"));
        assertEquals("t5xxl_fp8_e4m3fn.safetensors", inputs.getString("clip_name2"),
                "t5xxl_fp16 must be sanitized to available t5xxl_fp8_e4m3fn");
    }
}
