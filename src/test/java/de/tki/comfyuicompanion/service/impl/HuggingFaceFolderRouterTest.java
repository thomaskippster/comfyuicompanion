package de.tki.comfyuicompanion.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enterprise unit tests verifying {@link HuggingFaceFolderRouter} directory heuristics
 * and VRAM recommendation rules.
 */
class HuggingFaceFolderRouterTest {

    private HuggingFaceFolderRouter router;

    @BeforeEach
    void setUp() {
        router = new HuggingFaceFolderRouter();
    }

    @Test
    @DisplayName("Should route VAE models to vae directory")
    void shouldRouteVaeModels() {
        assertThat(router.determineTargetFolder("vae/flux2-vae.safetensors", List.of(), "black-forest-labs/flux-3-image-klein-comfy"))
                .isEqualTo("vae");
        assertThat(router.determineTargetFolder("ae.safetensors", List.of("vae"), "black-forest-labs/FLUX.1-dev-vae"))
                .isEqualTo("vae");
    }

    @Test
    @DisplayName("Should route text encoders and CLIP files to clip directory")
    void shouldRouteClipAndTextEncoders() {
        assertThat(router.determineTargetFolder("text_encoders/t5xxl_fp8_e4m3fn.safetensors", List.of(), "comfyanonymous/flux_text_encoders"))
                .isEqualTo("clip");
        assertThat(router.determineTargetFolder("clip_l.safetensors", List.of(), "repo/clip"))
                .isEqualTo("clip");
        assertThat(router.determineTargetFolder("qwen3vl_4b_bf16.safetensors", List.of(), "org/qwen-encoder"))
                .isEqualTo("clip");
        assertThat(router.determineTargetFolder("umt5_xxl.safetensors", List.of(), "google/umt5"))
                .isEqualTo("clip");
    }

    @Test
    @DisplayName("Should route LoRA models to loras directory")
    void shouldRouteLoraModels() {
        assertThat(router.determineTargetFolder("cyberpunk_style_v2.safetensors", List.of("lora", "flux"), "author/cyberpunk-lora"))
                .isEqualTo("loras");
    }

    @Test
    @DisplayName("Should route ControlNet models to controlnet directory")
    void shouldRouteControlNetModels() {
        assertThat(router.determineTargetFolder("diffusion_pytorch_model.safetensors", List.of("controlnet"), "xinsir/controlnet-union-sdxl"))
                .isEqualTo("controlnet");
    }

    @Test
    @DisplayName("Should route GGUF and UNET models to unet directory")
    void shouldRouteGgufAndUnet() {
        assertThat(router.determineTargetFolder("flux1-dev-Q4_K_M.gguf", List.of("gguf"), "city96/FLUX.1-dev-gguf"))
                .isEqualTo("unet");
        assertThat(router.determineTargetFolder("diffusion_models/wan2.1_t2v_1.3B_bf16.safetensors", List.of(), "Comfy-Org/Wan_2.1"))
                .isEqualTo("unet");
    }

    @Test
    @DisplayName("Should fallback to checkpoints for standard complete models")
    void shouldRouteCheckpoints() {
        assertThat(router.determineTargetFolder("sd_xl_base_1.0.safetensors", List.of(), "stabilityai/stable-diffusion-xl-base-1.0"))
                .isEqualTo("checkpoints");
    }

    @Test
    @DisplayName("Should determine VRAM recommendation hints accurately")
    void shouldDetermineVramHints() {
        assertThat(router.determineVramHint("model_Q4_K_M.gguf", 6800000000L))
                .contains("Standard");
        assertThat(router.determineVramHint("model_Q8_0.gguf", 12700000000L))
                .contains("High");
        assertThat(router.determineVramHint("model_bf16.safetensors", 23800000000L))
                .contains("Full Precision");
        assertThat(router.determineVramHint("small_clip.safetensors", 246000000L))
                .contains("Low VRAM");
    }
}
