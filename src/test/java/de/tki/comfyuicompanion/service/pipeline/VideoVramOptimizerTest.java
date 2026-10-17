package de.tki.comfyuicompanion.service.pipeline;

import de.tki.comfyuicompanion.domain.HardwareProfile;
import de.tki.comfyuicompanion.domain.HardwareTier;
import de.tki.comfyuicompanion.service.IHardwareProfileService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VideoVramOptimizer} verifying 16 GB VRAM AST workflow transformations,
 * CPU offloading for text encoders, tiled VAE decoding injection, and ComfyUI memory purges.
 */
class VideoVramOptimizerTest {

    private ConfigService configService;
    private IHardwareProfileService hardwareProfileService;
    private HttpClient httpClient;
    private VideoVramOptimizer optimizer;

    @BeforeEach
    void setUp() {
        configService = Mockito.mock(ConfigService.class);
        hardwareProfileService = Mockito.mock(IHardwareProfileService.class);
        httpClient = Mockito.mock(HttpClient.class);

        when(configService.is16GbVramOptimizationEnabled()).thenReturn(true);
        long vram16Gb = 16L * 1024L * 1024L * 1024L;
        HardwareProfile profile = new HardwareProfile("NVIDIA GeForce RTX 4080", vram16Gb, 32L * 1024L * 1024L * 1024L, 16, true, HardwareTier.MID_TIER);
        when(hardwareProfileService.getHardwareProfile()).thenReturn(profile);

        optimizer = new VideoVramOptimizer(configService, hardwareProfileService, httpClient);
    }

    @Test
    @DisplayName("Should detect 16 GB GPU optimization as active")
    void testIs16GbOptimizationActive() {
        assertTrue(optimizer.is16GbOptimizationActive());

        when(configService.is16GbVramOptimizationEnabled()).thenReturn(false);
        assertFalse(optimizer.is16GbOptimizationActive());
    }

    @Test
    @DisplayName("Should offload CLIPLoader to CPU and replace VAEDecode with VAEDecodeTiled")
    void testOptimizeWorkflowFor16Gb() {
        JSONObject workflow = new JSONObject();

        // 1. CLIPLoader node
        JSONObject clipNode = new JSONObject();
        clipNode.put("class_type", "CLIPLoader");
        JSONObject clipInputs = new JSONObject();
        clipInputs.put("clip_name", "umt5_xxl_fp8.safetensors");
        clipInputs.put("device", "default");
        clipNode.put("inputs", clipInputs);
        workflow.put("node_clip", clipNode);

        // 2. VAEDecode node
        JSONObject vaeNode = new JSONObject();
        vaeNode.put("class_type", "VAEDecode");
        JSONObject vaeInputs = new JSONObject();
        vaeInputs.put("samples", new JSONArray().put("sampler_node").put(0));
        vaeInputs.put("vae", new JSONArray().put("vae_node").put(0));
        vaeNode.put("inputs", vaeInputs);
        workflow.put("node_vae", vaeNode);

        // 3. UNETLoader node
        JSONObject unetNode = new JSONObject();
        unetNode.put("class_type", "UNETLoader");
        JSONObject unetInputs = new JSONObject();
        unetInputs.put("unet_name", "wan2.2_i2v_high_noise_14B_fp8.safetensors");
        unetInputs.put("weight_dtype", "default");
        unetNode.put("inputs", unetInputs);
        workflow.put("node_unet", unetNode);

        // Execute optimization
        optimizer.optimizeWorkflowFor16Gb(workflow);

        // Verify CLIPLoader device is now CPU
        assertEquals("cpu", workflow.getJSONObject("node_clip").getJSONObject("inputs").getString("device"));

        // Verify VAEDecode was replaced with VAEDecodeTiled with tiling parameters
        JSONObject tiledVae = workflow.getJSONObject("node_vae");
        assertEquals("VAEDecodeTiled", tiledVae.getString("class_type"));
        JSONObject tiledInputs = tiledVae.getJSONObject("inputs");
        assertEquals(512, tiledInputs.getInt("tile_size"));
        assertEquals(64, tiledInputs.getInt("overlap"));
        assertEquals(64, tiledInputs.getInt("temporal_size"));
        assertEquals(8, tiledInputs.getInt("temporal_overlap"));

        // Verify UNET weight_dtype is fp8_e4m3fn
        assertEquals("fp8_e4m3fn", workflow.getJSONObject("node_unet").getJSONObject("inputs").getString("weight_dtype"));
    }

    @Test
    @DisplayName("Should execute ComfyUI VRAM purge via POST /free")
    @SuppressWarnings("unchecked")
    void testPurgeComfyVram() throws Exception {
        HttpResponse<String> mockResponse = Mockito.mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"success\":true}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn((HttpResponse) mockResponse);

        boolean result = optimizer.purgeComfyVram("http://127.0.0.1:8188");
        assertTrue(result);
    }
}
