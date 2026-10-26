package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.domain.ModelInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests validating {@link CivitaiGuiWorkflowBuilder} generation of LiteGraph format workflows.
 */
class CivitaiGuiWorkflowBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CivitaiGuiWorkflowBuilder builder;

    @BeforeEach
    void setUp() {
        CivitaiLocalModelScanner scanner = org.mockito.Mockito.mock(CivitaiLocalModelScanner.class);
        org.mockito.Mockito.when(scanner.findFallbackCheckpointName(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("v1-5-pruned-emaonly.safetensors");
        builder = new CivitaiGuiWorkflowBuilder(objectMapper, null, null, scanner);
    }

    @Test
    @DisplayName("Should build valid LiteGraph GUI workflow for standard Checkpoint")
    void shouldBuildGuiWorkflow_forCheckpointModel() {
        CivitaiModelEntry entry = new CivitaiModelEntry(
                1001L,
                "DreamShaper XL",
                "Checkpoint",
                "Lykon",
                "SDXL 1.0",
                "Versatile SDXL model",
                "https://example.com/thumb.jpg",
                "https://example.com/dl",
                "dreamshaperXL_v21TurboDPMSDE.safetensors",
                "6.46 GB",
                6500000000L,
                1000,
                4.9,
                List.of(),
                false
        );

        ObjectNode workflow = builder.buildGuiWorkflow(entry, List.of());

        assertThat(workflow.has("nodes")).isTrue();
        assertThat(workflow.has("links")).isTrue();
        assertThat(workflow.path("last_node_id").asInt()).isEqualTo(9);
        assertThat(workflow.path("last_link_id").asInt()).isEqualTo(9);

        JsonNode nodes = workflow.get("nodes");
        assertThat(nodes.size()).isEqualTo(7);

        // Verify CheckpointLoaderSimple node (id 4)
        JsonNode ckptNode = findNodeById(nodes, 4);
        assertThat(ckptNode).isNotNull();
        assertThat(ckptNode.path("type").asText()).isEqualTo("CheckpointLoaderSimple");
        assertThat(ckptNode.path("widgets_values").get(0).asText()).isEqualTo("dreamshaperXL_v21TurboDPMSDE.safetensors");

        // Verify EmptyLatentImage node (id 5) has 1024x1024 for SDXL
        JsonNode latentNode = findNodeById(nodes, 5);
        assertThat(latentNode).isNotNull();
        assertThat(latentNode.path("widgets_values").get(0).asInt()).isEqualTo(1024);
        assertThat(latentNode.path("widgets_values").get(1).asInt()).isEqualTo(1024);

        // Verify KSampler node (id 3)
        JsonNode ksampler = findNodeById(nodes, 3);
        assertThat(ksampler).isNotNull();
        assertThat(ksampler.path("type").asText()).isEqualTo("KSampler");
        assertThat(ksampler.path("inputs").size()).isEqualTo(4);

        // Verify links count
        JsonNode links = workflow.get("links");
        assertThat(links.size()).isEqualTo(9);
    }

    @Test
    @DisplayName("Should build valid LiteGraph GUI workflow with LoraLoader node for LoRA model")
    void shouldBuildGuiWorkflow_forLoraModel() {
        CivitaiModelEntry loraEntry = new CivitaiModelEntry(
                2002L,
                "Cyberpunk Neon Glow",
                "LORA",
                "GlowCreator",
                "SD 1.5",
                "Cyberpunk style LoRA",
                "https://example.com/thumb.jpg",
                "https://example.com/dl",
                "cyberpunk_neon_v1.safetensors",
                "140 MB",
                140000000L,
                2500,
                4.95,
                List.of("cyberpunk neon", "glowing lights"),
                false
        );

        ModelInfo baseCkpt = new ModelInfo("checkpoints", "v1-5-pruned-emaonly.safetensors", "LOCAL");
        baseCkpt.setFilename("v1-5-pruned-emaonly.safetensors");

        ObjectNode workflow = builder.buildGuiWorkflow(loraEntry, List.of(baseCkpt));

        assertThat(workflow.path("last_node_id").asInt()).isEqualTo(10);
        assertThat(workflow.path("last_link_id").asInt()).isEqualTo(12);

        JsonNode nodes = workflow.get("nodes");
        assertThat(nodes.size()).isEqualTo(8); // 7 standard + 1 LoraLoader

        // Verify CheckpointLoaderSimple has resolved base checkpoint
        JsonNode ckptNode = findNodeById(nodes, 4);
        assertThat(ckptNode).isNotNull();
        assertThat(ckptNode.path("widgets_values").get(0).asText()).isEqualTo("v1-5-pruned-emaonly.safetensors");

        // Verify LoraLoader node (id 10)
        JsonNode loraNode = findNodeById(nodes, 10);
        assertThat(loraNode).isNotNull();
        assertThat(loraNode.path("type").asText()).isEqualTo("LoraLoader");
        assertThat(loraNode.path("widgets_values").get(0).asText()).isEqualTo("cyberpunk_neon_v1.safetensors");
        assertThat(loraNode.path("widgets_values").get(1).asDouble()).isEqualTo(1.0);
        assertThat(loraNode.path("widgets_values").get(2).asDouble()).isEqualTo(1.0);

        // Verify Positive prompt has trigger words pre-populated
        JsonNode posNode = findNodeById(nodes, 6);
        assertThat(posNode).isNotNull();
        assertThat(posNode.path("widgets_values").get(0).asText()).contains("cyberpunk neon, glowing lights");

        // Verify EmptyLatentImage node has 512x512 for SD 1.5
        JsonNode latentNode = findNodeById(nodes, 5);
        assertThat(latentNode).isNotNull();
        assertThat(latentNode.path("widgets_values").get(0).asInt()).isEqualTo(512);
        assertThat(latentNode.path("widgets_values").get(1).asInt()).isEqualTo(512);

        // Verify links count
        JsonNode links = workflow.get("links");
        assertThat(links.size()).isEqualTo(11);
    }

    @Test
    @DisplayName("Should inject custom user prompt into Positive Prompt node widgets_values")
    void shouldInjectCustomUserPrompt_whenSpecified() {
        CivitaiModelEntry entry = new CivitaiModelEntry(
                3003L,
                "Anime Character",
                "Checkpoint",
                "ArtistC",
                "SD 1.5",
                "Anime style",
                "https://example.com/thumb.jpg",
                "https://example.com/dl",
                "anime_char.safetensors",
                "2.0 GB",
                2000000000L,
                800,
                4.9,
                List.of("default trigger"),
                false
        );

        String customPrompt = "1girl, silver hair, blue eyes, magical aura, detailed anime portrait";
        ObjectNode workflow = builder.buildGuiWorkflow(entry, List.of(), customPrompt);

        JsonNode posNode = findNodeById(workflow.get("nodes"), 6);
        assertThat(posNode).isNotNull();
        assertThat(posNode.path("widgets_values").get(0).asText()).isEqualTo(customPrompt);
    }

    @Test
    @DisplayName("Should use fallback checkpoint for LoRA when requiredModels contains no checkpoint")
    void shouldFallbackToValidCheckpointForLora_whenRequiredModelsIsEmpty() {
        CivitaiModelEntry loraEntry = new CivitaiModelEntry(
                4004L,
                "Cyberpunk Anime Style",
                "LORA",
                "AnimeCreator",
                "ZImageTurbo",
                "LoRA style",
                "https://example.com/thumb.jpg",
                "https://example.com/dl",
                "ZIT_CPA_FREE.safetensors",
                "324.4 MB",
                340000000L,
                4200,
                4.92,
                List.of("cyberpunk anime"),
                false
        );

        ObjectNode workflow = builder.buildGuiWorkflow(loraEntry, List.of());

        JsonNode ckptNode = findNodeById(workflow.get("nodes"), 4);
        assertThat(ckptNode).isNotNull();
        assertThat(ckptNode.path("widgets_values").get(0).asText()).isNotEqualTo("ZIT_CPA_FREE.safetensors");
        assertThat(ckptNode.path("widgets_values").get(0).asText()).isEqualTo("v1-5-pruned-emaonly.safetensors");

        JsonNode loraNode = findNodeById(workflow.get("nodes"), 10);
        assertThat(loraNode).isNotNull();
        assertThat(loraNode.path("widgets_values").get(0).asText()).isEqualTo("ZIT_CPA_FREE.safetensors");
    }

    private JsonNode findNodeById(JsonNode nodes, int id) {
        for (JsonNode n : nodes) {
            if (n.path("id").asInt() == id) {
                return n;
            }
        }
        return null;
    }
}
