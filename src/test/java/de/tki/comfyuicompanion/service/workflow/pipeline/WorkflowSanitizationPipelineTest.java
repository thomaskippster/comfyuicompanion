package de.tki.comfyuicompanion.service.workflow.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tki.comfyuicompanion.domain.ModelInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WorkflowSanitizationPipeline Tests")
class WorkflowSanitizationPipelineTest {

    private ObjectMapper mapper;
    private WorkflowSanitizationPipeline pipeline;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        pipeline = new WorkflowSanitizationPipeline(List.of(
                new CheckpointSplitSanitizationStage(),
                new ImageSaverSanitizationStage(),
                new PassThroughBypassStage(),
                new ParameterInliningStage(),
                new DanglingCleanupStage()
        ));
    }

    @Test
    @DisplayName("Should convert third-party image saver to SaveImage")
    void shouldConvertImageSaverToCoreSaveImage() {
        ObjectNode workflow = mapper.createObjectNode();
        ArrayNode nodes = mapper.createArrayNode();
        ArrayNode links = mapper.createArrayNode();
        workflow.set("nodes", nodes);
        workflow.set("links", links);

        ObjectNode saver = mapper.createObjectNode()
                .put("id", 10)
                .put("type", "CustomImageSaverAdvanced");
        ArrayNode inputs = mapper.createArrayNode();
        inputs.add(mapper.createObjectNode().put("name", "image").put("type", "IMAGE").put("link", 1));
        saver.set("inputs", inputs);
        nodes.add(saver);

        links.add(mapper.createArrayNode().add(1).add(1).add(0).add(10).add(0).add("IMAGE"));

        WorkflowSanitizationContext ctx = new WorkflowSanitizationContext(workflow, Set.of("SaveImage"), List.of(), mapper);
        boolean mutated = pipeline.execute(ctx);

        assertTrue(mutated);
        assertEquals("SaveImage", saver.path("type").asText());
        assertEquals("comfy-core", saver.path("properties").path("cnr_id").asText());
        assertEquals(1, saver.path("inputs").size());
        assertEquals("images", saver.path("inputs").get(0).path("name").asText());
    }

    @Test
    @DisplayName("Should bypass pass-through LoRA loader node and connect upstream to downstream")
    void shouldBypassPassThroughNode() {
        ObjectNode workflow = mapper.createObjectNode();
        ArrayNode nodes = mapper.createArrayNode();
        ArrayNode links = mapper.createArrayNode();
        workflow.set("nodes", nodes);
        workflow.set("links", links);

        // Upstream Loader (ID 1)
        ObjectNode loader = mapper.createObjectNode().put("id", 1).put("type", "CheckpointLoaderSimple");
        ArrayNode loaderOutputs = mapper.createArrayNode();
        loaderOutputs.add(mapper.createObjectNode().put("name", "MODEL").put("type", "MODEL").set("links", mapper.createArrayNode().add(101)));
        loader.set("outputs", loaderOutputs);
        nodes.add(loader);

        // Pass-through Lora Loader (ID 2)
        ObjectNode lora = mapper.createObjectNode().put("id", 2).put("type", "LoraLoaderManager");
        ArrayNode loraInputs = mapper.createArrayNode();
        loraInputs.add(mapper.createObjectNode().put("name", "model").put("type", "MODEL").put("link", 101));
        lora.set("inputs", loraInputs);
        ArrayNode loraOutputs = mapper.createArrayNode();
        loraOutputs.add(mapper.createObjectNode().put("name", "MODEL").put("type", "MODEL").set("links", mapper.createArrayNode().add(102)));
        lora.set("outputs", loraOutputs);
        nodes.add(lora);

        // Downstream Sampler (ID 3)
        ObjectNode sampler = mapper.createObjectNode().put("id", 3).put("type", "KSampler");
        ArrayNode samplerInputs = mapper.createArrayNode();
        samplerInputs.add(mapper.createObjectNode().put("name", "model").put("type", "MODEL").put("link", 102));
        sampler.set("inputs", samplerInputs);
        nodes.add(sampler);

        // Links: 101 (1 -> 2), 102 (2 -> 3)
        links.add(mapper.createArrayNode().add(101).add(1).add(0).add(2).add(0).add("MODEL"));
        links.add(mapper.createArrayNode().add(102).add(2).add(0).add(3).add(0).add("MODEL"));

        WorkflowSanitizationContext ctx = new WorkflowSanitizationContext(workflow, Set.of("CheckpointLoaderSimple", "KSampler"), List.of(), mapper);
        boolean mutated = pipeline.execute(ctx);

        assertTrue(mutated);
        // LoraLoaderManager must be removed
        assertEquals(2, nodes.size());
        assertNull(WorkflowGraphHelper.findNodeById(workflow, 2));

        // Link 102 should now originate from node 1 slot 0
        ArrayNode rewiredLink = WorkflowGraphHelper.findLinkTuple(links, 102);
        assertNotNull(rewiredLink);
        assertEquals(1, rewiredLink.get(1).asInt());
        assertEquals(0, rewiredLink.get(2).asInt());
    }

    @Test
    @DisplayName("Should inline primitive int value into KSampler steps widget")
    void shouldInlinePrimitiveIntoWidget() {
        ObjectNode workflow = mapper.createObjectNode();
        ArrayNode nodes = mapper.createArrayNode();
        ArrayNode links = mapper.createArrayNode();
        workflow.set("nodes", nodes);
        workflow.set("links", links);

        // Primitive int node (ID 20) with value 28
        ObjectNode intNode = mapper.createObjectNode().put("id", 20).put("type", "easy int");
        intNode.set("widgets_values", mapper.createArrayNode().add(28));
        nodes.add(intNode);

        // Target KSampler (ID 5)
        ObjectNode sampler = mapper.createObjectNode().put("id", 5).put("type", "KSampler");
        ArrayNode sInputs = mapper.createArrayNode();
        sInputs.add(mapper.createObjectNode().put("name", "model").put("type", "MODEL"));
        sInputs.add(mapper.createObjectNode().put("name", "positive").put("type", "CONDITIONING"));
        sInputs.add(mapper.createObjectNode().put("name", "negative").put("type", "CONDITIONING"));
        sInputs.add(mapper.createObjectNode().put("name", "latent_image").put("type", "LATENT"));
        sInputs.add(mapper.createObjectNode().put("name", "steps").put("type", "INT").put("link", 501));
        sampler.set("inputs", sInputs);
        ArrayNode sWidgets = mapper.createArrayNode().add(12345L).add("randomize").add(20).add(7.0).add("euler").add("normal").add(1.0);
        sampler.set("widgets_values", sWidgets);
        nodes.add(sampler);

        links.add(mapper.createArrayNode().add(501).add(20).add(0).add(5).add(4).add("INT"));

        WorkflowSanitizationContext ctx = new WorkflowSanitizationContext(workflow, Set.of("KSampler"), List.of(), mapper);
        boolean mutated = pipeline.execute(ctx);

        assertTrue(mutated);
        // Node 20 removed
        assertNull(WorkflowGraphHelper.findNodeById(workflow, 20));
        // KSampler steps widget (index 2) updated to 28
        assertEquals(28, sampler.path("widgets_values").get(2).asInt());
    }

    @Test
    @DisplayName("Should split missing CheckpointLoaderSimple when local component models are present")
    void shouldSplitCheckpointWhenComponentsPresent() {
        ObjectNode workflow = mapper.createObjectNode();
        ArrayNode nodes = mapper.createArrayNode();
        ArrayNode links = mapper.createArrayNode();
        workflow.put("last_node_id", 10);
        workflow.set("nodes", nodes);
        workflow.set("links", links);

        ObjectNode ckpt = mapper.createObjectNode().put("id", 1).put("type", "CheckpointLoaderSimple");
        ckpt.set("widgets_values", mapper.createArrayNode().add("flux2KleinAIO_v1.safetensors"));
        ArrayNode ckptOut = mapper.createArrayNode();
        ckptOut.add(mapper.createObjectNode().put("name", "MODEL").put("type", "MODEL").set("links", mapper.createArrayNode().add(11)));
        ckptOut.add(mapper.createObjectNode().put("name", "CLIP").put("type", "CLIP").set("links", mapper.createArrayNode().add(12)));
        ckptOut.add(mapper.createObjectNode().put("name", "VAE").put("type", "VAE").set("links", mapper.createArrayNode().add(13)));
        ckpt.set("outputs", ckptOut);
        nodes.add(ckpt);

        links.add(mapper.createArrayNode().add(11).add(1).add(0).add(5).add(0).add("MODEL"));
        links.add(mapper.createArrayNode().add(12).add(1).add(1).add(2).add(0).add("CLIP"));
        links.add(mapper.createArrayNode().add(13).add(1).add(2).add(6).add(1).add("VAE"));

        ModelInfo unet = new ModelInfo();
        unet.setName("flux2-klein-base-4b");
        unet.setFilename("diffusion_models/flux2-klein-base-4b.safetensors");
        unet.setType("diffusion_models");

        ModelInfo clip = new ModelInfo();
        clip.setName("qwen-2.5-7b");
        clip.setFilename("text_encoders/qwen-2.5-7b.safetensors");
        clip.setType("text_encoders");

        ModelInfo vae = new ModelInfo();
        vae.setName("ae");
        vae.setFilename("vae/FLUX.1/ae.safetensors");
        vae.setType("vae");

        WorkflowSanitizationContext ctx = new WorkflowSanitizationContext(workflow, Set.of("UNETLoader", "CLIPLoader", "VAELoader"), List.of(unet, clip, vae), mapper);
        boolean mutated = pipeline.execute(ctx);

        assertTrue(mutated);
        // Original node 1 is now UNETLoader
        assertEquals("UNETLoader", ckpt.path("type").asText());
        assertEquals("diffusion_models/flux2-klein-base-4b.safetensors", ckpt.path("widgets_values").get(0).asText());

        // CLIPLoader and VAELoader were generated
        ObjectNode clipNode = WorkflowGraphHelper.findNodeById(workflow, 11);
        assertNotNull(clipNode);
        assertEquals("CLIPLoader", clipNode.path("type").asText());

        ObjectNode vaeNode = WorkflowGraphHelper.findNodeById(workflow, 12);
        assertNotNull(vaeNode);
        assertEquals("VAELoader", vaeNode.path("type").asText());
    }

    @Test
    @DisplayName("Should preserve healthy UUID subgraphs and ResolutionSelector without corrupting downstream ImageGrid")
    void shouldPreserveSubgraphsAndResolutionSelector() {
        ObjectNode workflow = mapper.createObjectNode();
        ArrayNode nodes = mapper.createArrayNode();
        ArrayNode links = mapper.createArrayNode();
        workflow.set("nodes", nodes);
        workflow.set("links", links);

        // Define a healthy subgraph
        String subgraphId = "3499543d-e357-4b0f-bcf2-d3e8cace54c6";
        ObjectNode definitions = mapper.createObjectNode();
        ArrayNode subgraphs = mapper.createArrayNode();
        ObjectNode sgDef = mapper.createObjectNode();
        sgDef.put("id", subgraphId);
        ArrayNode sgNodes = mapper.createArrayNode();
        sgNodes.add(mapper.createObjectNode().put("id", 1).put("type", "KSampler"));
        sgDef.set("nodes", sgNodes);
        subgraphs.add(sgDef);
        definitions.set("subgraphs", subgraphs);
        workflow.set("definitions", definitions);

        // Top-level Subgraph node (ID 940) producing IMAGE
        ObjectNode sgNode = mapper.createObjectNode().put("id", 940).put("type", subgraphId);
        ArrayNode sgOut = mapper.createArrayNode();
        sgOut.add(mapper.createObjectNode().put("name", "IMAGE").put("type", "IMAGE").set("links", mapper.createArrayNode().add(1001)));
        sgNode.set("outputs", sgOut);
        nodes.add(sgNode);

        // ResolutionSelector (ID 978) producing INT
        ObjectNode resNode = mapper.createObjectNode().put("id", 978).put("type", "ResolutionSelector");
        resNode.set("widgets_values", mapper.createArrayNode().add("1:1 (Square)").add(1).add(16));
        ArrayNode resOut = mapper.createArrayNode();
        resOut.add(mapper.createObjectNode().put("name", "width").put("type", "INT").set("links", mapper.createArrayNode().add(1002)));
        resNode.set("outputs", resOut);
        nodes.add(resNode);

        // Downstream ImageGrid (ID 974)
        ObjectNode gridNode = mapper.createObjectNode().put("id", 974).put("type", "ImageGrid");
        ArrayNode gridInputs = mapper.createArrayNode();
        gridInputs.add(mapper.createObjectNode().put("name", "images").put("type", "IMAGE").put("link", 1001));
        gridInputs.add(mapper.createObjectNode().put("name", "cell_width").put("type", "INT").put("link", 1002));
        gridNode.set("inputs", gridInputs);
        gridNode.set("widgets_values", mapper.createArrayNode().add(2).add(1024).add(1024).add(0));
        nodes.add(gridNode);

        links.add(mapper.createArrayNode().add(1001).add(940).add(0).add(974).add(0).add("IMAGE"));
        links.add(mapper.createArrayNode().add(1002).add(978).add(0).add(974).add(1).add("INT"));

        Set<String> registered = Set.of("KSampler", "ResolutionSelector", "ImageGrid");
        WorkflowSanitizationContext ctx = new WorkflowSanitizationContext(workflow, registered, List.of(), mapper);
        boolean mutated = pipeline.execute(ctx);

        // Neither healthy subgraph nor registered ResolutionSelector should be removed
        assertNotNull(WorkflowGraphHelper.findNodeById(workflow, 940), "Subgraph node 940 must be preserved");
        assertNotNull(WorkflowGraphHelper.findNodeById(workflow, 978), "ResolutionSelector node 978 must be preserved");
        assertNotNull(WorkflowGraphHelper.findNodeById(workflow, 974), "ImageGrid node 974 must be preserved");

        // ImageGrid integer widgets must not be corrupted with "1:1 (Square)"
        assertEquals(2, gridNode.path("widgets_values").get(0).asInt(), "Grid columns widget must remain integer 2");
        assertEquals(1024, gridNode.path("widgets_values").get(1).asInt(), "Grid cell_width widget must remain integer 1024");
    }
}
