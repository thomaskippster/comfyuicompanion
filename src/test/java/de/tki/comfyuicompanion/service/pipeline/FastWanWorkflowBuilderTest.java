package de.tki.comfyuicompanion.service.pipeline;

import de.tki.comfyuicompanion.domain.Scene;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FastWanWorkflowBuilderTest {

    @Test
    void testFastWanWorkflowGenerationOutputsNativeComfyNodes() {
        FastWanWorkflowBuilder builder = new FastWanWorkflowBuilder(null, null);

        Scene scene = new Scene();
        scene.setPrompt("A cinematic shot of a rocket launch in the desert.");
        scene.setNegativePrompt("blurry, low quality");
        scene.setWidth(832);
        scene.setHeight(480);
        scene.setSteps(6);
        scene.setCfgScale(1.0);
        scene.setStartFrame(0);
        scene.setEndFrame(96);

        JSONObject workflow = builder.generateFastWanWorkflowJson(
                "http://127.0.0.1:8188",
                scene,
                "input_speaker.png",
                42L,
                "test_fastwan",
                List.of("Wan2_2-TI2V-5B-FastWanFullAttn_bf16.safetensors"),
                List.of(),
                List.of("umt5_xxl_fp8_e4m3fn_scaled.safetensors"),
                List.of("wan_2.1_vae.safetensors")
        );

        assertNotNull(workflow);
        assertTrue(workflow.has("10")); // VAELoader
        assertTrue(workflow.has("11")); // CLIPLoader
        assertTrue(workflow.has("12")); // UNETLoader
        assertTrue(workflow.has("20")); // Negative CLIPTextEncode
        assertTrue(workflow.has("21")); // Positive CLIPTextEncode
        assertTrue(workflow.has("40")); // LoadImage
        assertTrue(workflow.has("50")); // WanImageToVideo
        assertTrue(workflow.has("60")); // KSampler
        assertTrue(workflow.has("70")); // VAEDecode
        assertTrue(workflow.has("80")); // CreateVideo
        assertTrue(workflow.has("90")); // SaveVideo

        assertEquals("Wan2_2-TI2V-5B-FastWanFullAttn_bf16.safetensors",
                workflow.getJSONObject("12").getJSONObject("inputs").getString("unet_name"));
        assertEquals("input_speaker.png",
                workflow.getJSONObject("40").getJSONObject("inputs").getString("image"));
        assertEquals(6,
                workflow.getJSONObject("60").getJSONObject("inputs").getInt("steps"));
        assertEquals(1.0,
                workflow.getJSONObject("60").getJSONObject("inputs").getDouble("cfg"), 0.01);
    }

    @Test
    void testModelResolutionSelectsFastWanSafetensors() {
        FastWanWorkflowBuilder builder = new FastWanWorkflowBuilder(null, null);

        String resolved = builder.resolveFastWanModel(
                List.of("flux1-dev.safetensors", "Wan2_2-TI2V-5B-FastWanFullAttn_bf16.safetensors"),
                List.of("sdxl.safetensors")
        );
        assertEquals("Wan2_2-TI2V-5B-FastWanFullAttn_bf16.safetensors", resolved);
    }

    @Test
    void testTextToVideoOmitsStartImageWhenNoSpeakerImageProvided() {
        FastWanWorkflowBuilder builder = new FastWanWorkflowBuilder(null, null);

        Scene scene = new Scene();
        scene.setPrompt("A futuristic cybernetic city at sunrise.");
        scene.setWidth(832);
        scene.setHeight(480);

        JSONObject workflow = builder.generateFastWanWorkflowJson(
                "http://127.0.0.1:8188",
                scene,
                null,
                123L,
                "t2v_test",
                List.of("Wan2_2-TI2V-5B-FastWanFullAttn_bf16.safetensors"),
                List.of(),
                List.of("umt5_xxl_fp8_e4m3fn_scaled.safetensors"),
                List.of("wan_2.1_vae.safetensors")
        );

        assertNotNull(workflow);
        assertFalse(workflow.has("40"), "Should not contain LoadImage or EmptyImage node when no start image provided");
        JSONObject wanNodeInputs = workflow.getJSONObject("50").getJSONObject("inputs");
        assertFalse(wanNodeInputs.has("start_image"), "WanImageToVideo should not have start_image input in pure text-to-video mode");
    }
}
