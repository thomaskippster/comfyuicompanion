package de.tki.comfyuicompanion.service.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tki.comfyuicompanion.domain.ModelInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StandardWorkflowFactory Generic Pipeline Tests")
class StandardWorkflowFactoryTest {

    private StandardWorkflowFactory factory;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        factory = new StandardWorkflowFactory(objectMapper);
    }

    @Test
    @DisplayName("Synthesizes valid SD 1.5 LiteGraph workflow with prompt")
    void shouldCreateSd15Workflow() {
        ModelInfo model = new ModelInfo("checkpoints", "v1-5-pruned.safetensors", "http://example.com/v1-5.safetensors");
        model.setBase("SD 1.5");
        model.setFilename("v1-5-pruned.safetensors");

        ObjectNode workflow = factory.createWorkflow(model, "cyberpunk girl in neon street");

        assertNotNull(workflow);
        assertTrue(workflow.has("nodes"));
        assertTrue(workflow.has("links"));
        assertEquals(7, workflow.get("nodes").size());

        // Verify positive prompt node text
        ObjectNode promptNode = (ObjectNode) workflow.get("nodes").get(1);
        assertEquals("CLIPTextEncode", promptNode.get("type").asText());
        assertEquals("cyberpunk girl in neon street", promptNode.get("widgets_values").get(0).asText());

        // Verify empty latent resolution 512x512
        ObjectNode latentNode = (ObjectNode) workflow.get("nodes").get(3);
        assertEquals("EmptyLatentImage", latentNode.get("type").asText());
        assertEquals(512, latentNode.get("widgets_values").get(0).asInt());
        assertEquals(512, latentNode.get("widgets_values").get(1).asInt());
    }

    @Test
    @DisplayName("Synthesizes valid SDXL LiteGraph workflow with 1024x1024 resolution")
    void shouldCreateSdxlWorkflow() {
        ModelInfo model = new ModelInfo("checkpoints", "sd_xl_base_1.0.safetensors", "http://example.com/sdxl.safetensors");
        model.setBase("SDXL 1.0");

        ObjectNode workflow = factory.createWorkflow(model, "astronaut floating in outer space");

        assertNotNull(workflow);
        ObjectNode latentNode = (ObjectNode) workflow.get("nodes").get(3);
        assertEquals(1024, latentNode.get("widgets_values").get(0).asInt());
        assertEquals(1024, latentNode.get("widgets_values").get(1).asInt());
    }

    @Test
    @DisplayName("Synthesizes valid Flux.1 workflow with UNET, DualCLIP, and VAE loaders")
    void shouldCreateFlux1Workflow() {
        ModelInfo model = new ModelInfo("unet", "flux1-dev-fp8.safetensors", "http://example.com/flux1.safetensors");
        model.setBase("Flux.1 Dev");
        model.setFilename("flux1-dev-fp8.safetensors");

        ObjectNode workflow = factory.createWorkflow(model, "majestic dragon above mountains");

        assertNotNull(workflow);
        assertEquals(9, workflow.get("nodes").size());
        assertEquals("UNETLoader", workflow.get("nodes").get(0).get("type").asText());
        assertEquals("DualCLIPLoader", workflow.get("nodes").get(1).get("type").asText());
        assertEquals("VAELoader", workflow.get("nodes").get(2).get("type").asText());

        // Verify KSampler uses CFG=1.0 for Flux
        ObjectNode samplerNode = (ObjectNode) workflow.get("nodes").get(6);
        assertEquals(1.0, samplerNode.get("widgets_values").get(3).asDouble());
        assertEquals("simple", samplerNode.get("widgets_values").get(5).asText());
    }

    @Test
    @DisplayName("Synthesizes valid Flux.2 / Klein workflow")
    void shouldCreateFlux2KleinWorkflow() {
        ModelInfo model = new ModelInfo("unet", "flux2-klein-base-4b-full.safetensors", "http://example.com/flux2.safetensors");
        model.setBase("Flux.2 Klein");
        model.setFilename("flux2-klein-base-4b-full.safetensors");

        ObjectNode workflow = factory.createWorkflow(model, "portrait of a wizard");

        assertNotNull(workflow);
        assertEquals("CLIPLoader", workflow.get("nodes").get(1).get("type").asText());
    }

    @Test
    @DisplayName("Synthesizes valid Wan 2.1 workflow with 832x480 native aspect ratio")
    void shouldCreateWan21Workflow() {
        ModelInfo model = new ModelInfo("diffusion_models", "wan2.1_t2v_1.3B_bf16.safetensors", "http://example.com/wan.safetensors");
        model.setBase("Wan 2.1");

        ObjectNode workflow = factory.createWorkflow(model, "flowing water in sunset");

        assertNotNull(workflow);
        ObjectNode latentNode = (ObjectNode) workflow.get("nodes").get(5);
        assertEquals(832, latentNode.get("widgets_values").get(0).asInt());
        assertEquals(480, latentNode.get("widgets_values").get(1).asInt());
    }
}
