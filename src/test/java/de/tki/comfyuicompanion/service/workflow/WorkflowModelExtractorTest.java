package de.tki.comfyuicompanion.service.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tki.comfyuicompanion.domain.ModelInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WorkflowModelExtractor Tests")
class WorkflowModelExtractorTest {

    private ObjectMapper objectMapper;
    private WorkflowModelExtractor extractor;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        extractor = new WorkflowModelExtractor(null);
    }

    @Test
    @DisplayName("Should extract concrete model dependencies from LiteGraph JSON with accurate folders")
    void shouldExtractModelsFromLiteGraph() {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode nodes = objectMapper.createArrayNode();

        // Node 1: Checkpoint
        ObjectNode n1 = objectMapper.createObjectNode();
        n1.put("id", 1);
        n1.put("type", "CheckpointLoaderSimple");
        ArrayNode w1 = objectMapper.createArrayNode();
        w1.add("sd_xl_base_1.0.safetensors");
        n1.set("widgets_values", w1);
        nodes.add(n1);

        // Node 2: Subgraph / Custom Node with multiple Flux 3 models
        ObjectNode n2 = objectMapper.createObjectNode();
        n2.put("id", 950);
        n2.put("type", "cb7011dc-0eef-4c9c-bdf8-1e7caec149d6");
        n2.put("title", "Flux 3 Stage");
        ArrayNode w2 = objectMapper.createArrayNode();
        w2.add("flux3_image_klein_sd_int8_convrot.safetensors");
        w2.add("qwen3vl_4b_int8_convrot.safetensors");
        w2.add("flux2-vae.safetensors");
        w2.add("qwen3.5_9b_flux3_prompt_upsampler_int8_convrot.safetensors");
        n2.set("widgets_values", w2);
        nodes.add(n2);

        root.set("nodes", nodes);

        List<ModelInfo> result = extractor.extractModels(root);

        assertThat(result).hasSize(5);

        // Verify Checkpoint
        ModelInfo ckpt = result.stream().filter(m -> m.getName().equals("sd_xl_base_1.0.safetensors")).findFirst().orElseThrow();
        assertThat(ckpt.getType()).isEqualTo("checkpoints");

        // Verify Flux 3 UNET
        ModelInfo unet = result.stream().filter(m -> m.getName().equals("flux3_image_klein_sd_int8_convrot.safetensors")).findFirst().orElseThrow();
        assertThat(unet.getType()).isEqualTo("unet");

        // Verify VAE
        ModelInfo vae = result.stream().filter(m -> m.getName().equals("flux2-vae.safetensors")).findFirst().orElseThrow();
        assertThat(vae.getType()).isEqualTo("vae");

        // Verify Text Encoders / LLM
        ModelInfo clip = result.stream().filter(m -> m.getName().equals("qwen3vl_4b_int8_convrot.safetensors")).findFirst().orElseThrow();
        assertThat(clip.getType()).isEqualTo("clip");
    }

    @Test
    @DisplayName("Should extract model references from ComfyUI API prompt format")
    void shouldExtractModelsFromApiPrompt() {
        ObjectNode apiRoot = objectMapper.createObjectNode();

        ObjectNode n1 = objectMapper.createObjectNode();
        n1.put("class_type", "UNETLoader");
        ObjectNode inputs = objectMapper.createObjectNode();
        inputs.put("unet_name", "flux-2-klein-base-9b-fp8.safetensors");
        n1.set("inputs", inputs);
        apiRoot.set("1", n1);

        List<ModelInfo> result = extractor.extractModels(apiRoot);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("flux-2-klein-base-9b-fp8.safetensors");
        assertThat(result.get(0).getType()).isEqualTo("unet");
    }

    @Test
    @DisplayName("Should extract models defined inside definitions.subgraphs")
    void shouldExtractModelsFromDefinitionsSubgraphs() {
        ObjectNode root = objectMapper.createObjectNode();
        root.putArray("nodes"); // empty top-level nodes

        ObjectNode definitions = root.putObject("definitions");
        ArrayNode subgraphs = definitions.putArray("subgraphs");

        ObjectNode sg1 = subgraphs.addObject();
        sg1.put("id", "cb7011dc-0eef-4c9c-bdf8-1e7caec149d6");
        sg1.put("name", "Flux 3 Subgraph");
        ArrayNode sgNodes = sg1.putArray("nodes");

        ObjectNode unetNode = sgNodes.addObject();
        unetNode.put("type", "UNETLoader");
        ArrayNode unetWidgets = unetNode.putArray("widgets_values");
        unetWidgets.add("flux3_image_klein_sd_int8_convrot.safetensors");

        ObjectNode vaeNode = sgNodes.addObject();
        vaeNode.put("type", "VAELoader");
        ArrayNode vaeWidgets = vaeNode.putArray("widgets_values");
        vaeWidgets.add("flux2-vae.safetensors");

        List<ModelInfo> result = extractor.extractModels(root);

        assertThat(result).hasSize(2);
        assertThat(result.stream().map(ModelInfo::getName))
                .containsExactlyInAnyOrder("flux3_image_klein_sd_int8_convrot.safetensors", "flux2-vae.safetensors");
        ModelInfo unet = result.stream().filter(m -> m.getName().contains("flux3")).findFirst().orElseThrow();
        assertThat(unet.getType()).isEqualTo("unet");
        ModelInfo vae = result.stream().filter(m -> m.getName().contains("vae")).findFirst().orElseThrow();
        assertThat(vae.getType()).isEqualTo("vae");
    }

    @Test
    @DisplayName("Debug extract models from user uploaded JSON")
    void debugExtractUserWorkflow() throws Exception {
        java.io.File file = new java.io.File("C:/Users/thoma/.gemini/antigravity/brain/a1ab1bea-a74a-4e27-a9a2-78679fb9fa71/.user_uploaded/media_1793383265995_dfb8028a.json");
        if (file.exists()) {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(file);
            List<ModelInfo> models = extractor.extractModels(root);
            System.out.println("DEBUG EXTRACTED MODELS 1 (dfb8028a): " + models.size());
            for (ModelInfo m : models) {
                System.out.println("  -> " + m.getName() + " (" + m.getType() + ") url=" + m.getUrl());
            }
        }

        java.io.File file3 = new java.io.File("C:/AI/comfyuidata/models/workflows/flux3KleinEDITWithPromptEnchancer100Of_v10.json");
        if (file3.exists()) {
            com.fasterxml.jackson.databind.JsonNode root3 = objectMapper.readTree(file3);
            List<ModelInfo> models3 = extractor.extractModels(root3);
            System.out.println("DEBUG EXTRACTED MODELS 3 (flux3KleinEDIT): " + models3.size());
            for (ModelInfo m : models3) {
                System.out.println("  -> " + m.getName() + " (" + m.getType() + ") size=" + m.getSize() + " url=" + m.getUrl());
            }
        }
    }
}
