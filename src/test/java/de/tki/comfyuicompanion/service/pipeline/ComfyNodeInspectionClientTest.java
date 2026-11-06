package de.tki.comfyuicompanion.service.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComfyNodeInspectionClientTest {

    private ComfyNodeInspectionClient client;

    @BeforeEach
    void setUp() {
        client = new ComfyNodeInspectionClient();
    }

    @Test
    @DisplayName("Should find exact model name when present")
    void testExactMatch() {
        List<String> available = List.of("Wan2.2/wan2.2_i2v_high_noise_14B_fp8_scaled.safetensors", "flux1-dev.safetensors");
        String found = client.findExactModelName("Wan2.2\\wan2.2_i2v_high_noise_14B_fp8_scaled.safetensors", available);
        assertEquals("Wan2.2/wan2.2_i2v_high_noise_14B_fp8_scaled.safetensors", found);
    }

    @Test
    @DisplayName("Should find basename match regardless of subfolder")
    void testBasenameMatch() {
        List<String> available = List.of("diffusion_models/wan2.1_i2v_720p_14B_fp8.safetensors");
        String found = client.findExactModelName("wan2.1_i2v_720p_14B_fp8.safetensors", available);
        assertEquals("diffusion_models/wan2.1_i2v_720p_14B_fp8.safetensors", found);
    }

    @Test
    @DisplayName("Should fall back semantically to available Wan model if expected Wan 2.2 is missing")
    void testSemanticWanFallback() {
        List<String> available = List.of("wan2.1_i2v_720p_14B_fp8.safetensors", "sd_xl_base_1.0.safetensors");
        String found = client.findExactModelName("Wan2.2\\wan2.2_i2v_high_noise_14B_fp8_scaled.safetensors", available);
        assertEquals("wan2.1_i2v_720p_14B_fp8.safetensors", found);
    }

    @Test
    @DisplayName("Should fall back semantically to available UMT5/T5 clip model")
    void testSemanticClipFallback() {
        List<String> available = List.of("umt5_xxl_fp8.safetensors", "clip_l.safetensors");
        String found = client.findExactModelName("umt5_xxl_fp8_e4m3fn_scaled.safetensors", available);
        assertEquals("umt5_xxl_fp8.safetensors", found);
    }
}
