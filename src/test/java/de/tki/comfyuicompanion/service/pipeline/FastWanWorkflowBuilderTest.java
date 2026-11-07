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
        assertTrue(workflow.has("50")); // Wan22ImageToVideoLatent (native Wan 2.2 start image anchor)
        assertTrue(workflow.has("60")); // KSampler
        assertTrue(workflow.has("70")); // VAEDecode
        assertTrue(workflow.has("80")); // CreateVideo
        assertTrue(workflow.has("90")); // SaveVideo

        assertEquals("Wan22ImageToVideoLatent", workflow.getJSONObject("50").getString("class_type"));
        assertEquals("input_speaker.png",
                workflow.getJSONObject("40").getJSONObject("inputs").getString("image"));
        // Wan22ImageToVideoLatent anchors start_image directly
        assertEquals("40", workflow.getJSONObject("50").getJSONObject("inputs").getJSONArray("start_image").getString(0));
        assertEquals("50", workflow.getJSONObject("60").getJSONObject("inputs").getJSONArray("latent_image").getString(0));
        assertEquals("21", workflow.getJSONObject("60").getJSONObject("inputs").getJSONArray("positive").getString(0));

        assertEquals("Wan2_2-TI2V-5B-FastWanFullAttn_bf16.safetensors",
                workflow.getJSONObject("12").getJSONObject("inputs").getString("unet_name"));
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
    void testVaeResolutionPrioritizesWan22VaeOverWan21() {
        FastWanWorkflowBuilder builder = new FastWanWorkflowBuilder(null, null);

        String vae = builder.resolveVae(List.of("ae.safetensors", "wan_2.1_vae.safetensors", "wan2.2_vae.safetensors"));
        assertEquals("wan2.2_vae.safetensors", vae, "FastWan 5B requires Wan 2.2 48-channel VAE");
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

    @Test
    void testFallbackToWanImageToVideoWhenWan22LatentNodeMissing() {
        ComfyNodeInspectionClient mockClient = org.mockito.Mockito.mock(ComfyNodeInspectionClient.class);
        org.mockito.Mockito.when(mockClient.isNodeClassAvailable("http://127.0.0.1:8188", "Wan22ImageToVideoLatent")).thenReturn(false);
        org.mockito.Mockito.when(mockClient.isNodeClassAvailable("http://127.0.0.1:8188", "WanImageToVideo")).thenReturn(true);

        FastWanWorkflowBuilder builder = new FastWanWorkflowBuilder(mockClient, null);

        Scene scene = new Scene();
        scene.setPrompt("A cybernetic rover exploring Titan");
        scene.setWidth(832);
        scene.setHeight(480);

        JSONObject workflow = builder.generateFastWanWorkflowJson(
                "http://127.0.0.1:8188",
                scene,
                "input_speaker.png",
                42L,
                "legacy_fallback",
                List.of("Wan2_2-TI2V-5B-FastWanFullAttn_bf16.safetensors"),
                List.of(),
                List.of("umt5_xxl_fp8_e4m3fn_scaled.safetensors"),
                List.of("wan2.2_vae.safetensors")
        );

        assertNotNull(workflow);
        assertEquals("WanImageToVideo", workflow.getJSONObject("50").getString("class_type"),
                "Should gracefully fall back to WanImageToVideo on older ComfyUI installations");
        assertEquals("50", workflow.getJSONObject("60").getJSONObject("inputs").getJSONArray("latent_image").getString(0));
        assertEquals(2, workflow.getJSONObject("60").getJSONObject("inputs").getJSONArray("latent_image").getInt(1));
    }
}
