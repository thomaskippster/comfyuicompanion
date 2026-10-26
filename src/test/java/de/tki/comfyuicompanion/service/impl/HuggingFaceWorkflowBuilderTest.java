package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tki.comfyuicompanion.domain.HuggingFaceFileEntry;
import de.tki.comfyuicompanion.domain.HuggingFaceModelEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HuggingFaceWorkflowBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HuggingFaceWorkflowBuilder builder = new HuggingFaceWorkflowBuilder(objectMapper);

    @Test
    @DisplayName("Synthesizes valid LiteGraph GUI workflow for Flux GGUF model")
    void testBuildFluxGuiWorkflow() {
        HuggingFaceModelEntry repo = new HuggingFaceModelEntry(
                "city96/FLUX.1-dev-gguf", "city96", "FLUX.1-dev-gguf",
                15000, 450, "2026-08-01", "text-to-image",
                List.of("flux", "gguf"), "false", List.of()
        );
        HuggingFaceFileEntry file = new HuggingFaceFileEntry(
                "flux1-dev-Q4_K_M.gguf", "flux1-dev-Q4_K_M.gguf",
                12_000_000_000L, "11.4 GB", "https://hf.co/download",
                "models/unet", "🟢 Standard", true
        );

        ObjectNode guiWorkflow = builder.buildGuiWorkflow(repo, file, "a cinematic portrait of an astronaut");
        assertNotNull(guiWorkflow);
        assertTrue(guiWorkflow.has("nodes"));
        assertTrue(guiWorkflow.has("links"));
        assertEquals(0.4, guiWorkflow.path("version").asDouble());
        assertTrue(guiWorkflow.path("nodes").size() >= 8);
    }

    @Test
    @DisplayName("Synthesizes valid LiteGraph GUI workflow for standard Checkpoint model")
    void testBuildCheckpointGuiWorkflow() {
        HuggingFaceModelEntry repo = new HuggingFaceModelEntry(
                "stabilityai/stable-diffusion-xl-base-1.0", "stabilityai", "stable-diffusion-xl-base-1.0",
                50000, 1200, "2026-07-01", "text-to-image",
                List.of("sdxl"), "false", List.of()
        );
        HuggingFaceFileEntry file = new HuggingFaceFileEntry(
                "sd_xl_base_1.0.safetensors", "sd_xl_base_1.0.safetensors",
                6_900_000_000L, "6.4 GB", "https://hf.co/download",
                "models/checkpoints", "🟢 Standard", true
        );

        ObjectNode guiWorkflow = builder.buildGuiWorkflow(repo, file, "fantasy landscape");
        assertNotNull(guiWorkflow);
        assertTrue(guiWorkflow.path("nodes").size() >= 7);
    }

    @Test
    @DisplayName("Synthesizes API prompt JSON with injected model and user prompt")
    void testBuildApiWorkflowJson() {
        HuggingFaceModelEntry repo = new HuggingFaceModelEntry(
                "city96/FLUX.1-dev-gguf", "city96", "FLUX.1-dev-gguf",
                15000, 450, "2026-08-01", "text-to-image",
                List.of("flux", "gguf"), "false", List.of()
        );
        HuggingFaceFileEntry file = new HuggingFaceFileEntry(
                "flux1-dev-Q4_K_M.gguf", "flux1-dev-Q4_K_M.gguf",
                12_000_000_000L, "11.4 GB", "https://hf.co/download",
                "models/unet", "🟢 Standard", true
        );

        String apiJson = builder.buildApiWorkflowJson(repo, file, "a cute red panda");
        assertNotNull(apiJson);
        assertTrue(apiJson.contains("prompt"));
    }
}
