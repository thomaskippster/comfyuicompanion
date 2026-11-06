package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.service.IConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

/**
 * Enterprise unit tests verifying {@link CivitaiWorkflowFileResolver} resolving of
 * workflows from JSON files, embedded PNG metadata, and ZIP archives.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CivitaiWorkflowFileResolverTest {

    @Mock
    private IConfigService configService;

    @TempDir
    Path tempModelsDir;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CivitaiWorkflowFileResolver resolver;

    @BeforeEach
    void setUp() {
        lenient().when(configService.getModelsPath()).thenReturn(tempModelsDir.toString());
        resolver = new CivitaiWorkflowFileResolver(objectMapper, configService, null);
    }

    @Test
    @DisplayName("Should load real LiteGraph workflow from JSON file inside workflows directory")
    void shouldLoadJsonWorkflow() throws IOException {
        Path wfDir = tempModelsDir.resolve("workflows");
        Files.createDirectories(wfDir);
        Path jsonFile = wfDir.resolve("my_workflow.json");

        ObjectNode sampleGraph = objectMapper.createObjectNode();
        ArrayNode nodes = objectMapper.createArrayNode();
        ObjectNode node1 = objectMapper.createObjectNode();
        node1.put("id", 1);
        node1.put("type", "KSampler");
        nodes.add(node1);
        sampleGraph.set("nodes", nodes);

        Files.writeString(jsonFile, sampleGraph.toString());

        CivitaiModelEntry entry = new CivitaiModelEntry(
                123L, "My Workflow", "Workflows", "creator", "SDXL", "desc",
                "http://img", "http://download", "my_workflow.json", "10 MB", 10000000L,
                100, 5.0, List.of(), false
        );

        Optional<ObjectNode> result = resolver.tryResolveRealWorkflow(entry);
        assertThat(result).isPresent();
        assertThat(result.get().path("nodes").get(0).path("type").asText()).isEqualTo("KSampler");
    }

    @Test
    @DisplayName("Should extract and load real LiteGraph workflow from a ZIP archive containing workflow JSON")
    void shouldLoadWorkflowFromZip() throws IOException {
        Path wfDir = tempModelsDir.resolve("workflows");
        Files.createDirectories(wfDir);
        Path zipFile = wfDir.resolve("wan_bundle.zip");

        ObjectNode innerWf = objectMapper.createObjectNode();
        ArrayNode nodes = objectMapper.createArrayNode();
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", 42);
        node.put("type", "WanVideoSampler");
        nodes.add(node);
        innerWf.set("nodes", nodes);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("workflow/wan22_workflow.json");
            zos.putNextEntry(entry);
            zos.write(innerWf.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        Files.write(zipFile, baos.toByteArray());

        CivitaiModelEntry entry = new CivitaiModelEntry(
                456L, "Wan Bundle", "Workflows", "pgc", "Wan Video", "desc",
                "http://img", "http://download", "wan_bundle.zip", "64 MB", 64000000L,
                500, 5.0, List.of(), false
        );

        Optional<ObjectNode> result = resolver.tryResolveRealWorkflow(entry);
        assertThat(result).isPresent();
        assertThat(result.get().path("nodes").get(0).path("type").asText()).isEqualTo("WanVideoSampler");
    }

    @Test
    @DisplayName("Test resolving actual workflow from Downloads")
    void testResolveUserWorkflowDirectly() {
        CivitaiModelEntry entry = new CivitaiModelEntry(
                999L, "Flux3 Klein Pro Grade Character", "Workflows", "lonecatone23", "Flux.3 Klein", "desc",
                "http://img", "http://download", "flux3KleinProGradeCharacter_v10.json", "0.3 MB", 300000L,
                100, 5.0, List.of(), false
        );
        Optional<ObjectNode> res = resolver.tryResolveRealWorkflow(entry);
        System.out.println("RESOLVER TEST RESULT: " + res.isPresent());
    }

    @Test
    @DisplayName("Should inject user prompt into CLIPTextEncode node in LiteGraph workflow")
    void shouldInjectUserPrompt() {
        ObjectNode graph = objectMapper.createObjectNode();
        ArrayNode nodes = objectMapper.createArrayNode();

        ObjectNode textNode = objectMapper.createObjectNode();
        textNode.put("id", 6);
        textNode.put("type", "CLIPTextEncode");
        ArrayNode widgets = objectMapper.createArrayNode();
        widgets.add("original prompt");
        textNode.set("widgets_values", widgets);
        nodes.add(textNode);
        graph.set("nodes", nodes);

        resolver.injectUserPrompt(graph, "a cinematic view of a futuristic train");

        assertThat(graph.path("nodes").get(0).path("widgets_values").get(0).asText())
                .isEqualTo("a cinematic view of a futuristic train");
    }

    @Test
    @DisplayName("Should adapt model references to installed local checkpoints")
    void shouldAdaptModelReferences() {
        ObjectNode graph = objectMapper.createObjectNode();
        ArrayNode nodes = objectMapper.createArrayNode();

        ObjectNode loaderNode = objectMapper.createObjectNode();
        loaderNode.put("id", 609);
        loaderNode.put("type", "SeargeModelSelector");
        ArrayNode widgets = objectMapper.createArrayNode();
        widgets.add("sd_xl_base_1.0_0.9vae.safetensors");
        widgets.add("sd_xl_refiner_1.0_0.9vae.safetensors");
        widgets.add("sdxl_vae.safetensors");
        loaderNode.set("widgets_values", widgets);
        nodes.add(loaderNode);
        graph.set("nodes", nodes);

        de.tki.comfyuicompanion.domain.ModelInfo installedSdxl = new de.tki.comfyuicompanion.domain.ModelInfo(
                "checkpoints", "sd_xl_base_1.0.safetensors", "LOCAL"
        );
        installedSdxl.setFilename("sd_xl_base_1.0.safetensors");

        resolver.adaptModelReferences(graph, List.of(installedSdxl));

        assertThat(graph.path("nodes").get(0).path("widgets_values").get(0).asText())
                .isEqualTo("sd_xl_base_1.0.safetensors");
    }

    @Test
    @DisplayName("Should adapt VAELoader flux2-vae.safetensors to subfolder ae.safetensors without substring false-positive")
    void shouldAdaptVaeLoaderWithoutSubstringFalsePositive() throws IOException {
        Path vaeSubDir = tempModelsDir.resolve("vae").resolve("FLUX1");
        Files.createDirectories(vaeSubDir);
        Path aeFile = vaeSubDir.resolve("ae.safetensors");
        // Write > 10MB or mocked valid model file? Note: isCompletedModelFile checks size >= 10MB (10_485_760 bytes)
        byte[] dummyTenMb = new byte[10_500_000];
        Files.write(aeFile, dummyTenMb);

        ObjectNode graph = objectMapper.createObjectNode();
        ArrayNode nodes = objectMapper.createArrayNode();

        ObjectNode vaeNode = objectMapper.createObjectNode();
        vaeNode.put("id", 3);
        vaeNode.put("type", "VAELoader");
        ArrayNode widgets = objectMapper.createArrayNode();
        widgets.add("flux2-vae.safetensors");
        vaeNode.set("widgets_values", widgets);
        nodes.add(vaeNode);
        graph.set("nodes", nodes);

        de.tki.comfyuicompanion.domain.ModelInfo installedVae = new de.tki.comfyuicompanion.domain.ModelInfo(
                "vae", "ae.safetensors", "LOCAL"
        );
        installedVae.setFilename("ae.safetensors");

        resolver.adaptModelReferences(graph, List.of(installedVae));

        String adapted = graph.path("nodes").get(0).path("widgets_values").get(0).asText();
        assertThat(adapted.replace('\\', '/')).isEqualTo("ae.safetensors");
    }

    @Test
    @DisplayName("Should split missing All-in-One CheckpointLoaderSimple into UNETLoader, DualCLIPLoader, and VAELoader when split models are installed")
    void shouldSplitMissingAioCheckpointLoaderIntoSplitLoaders() {
        ObjectNode graph = objectMapper.createObjectNode();
        graph.put("last_node_id", 101);
        ArrayNode nodes = objectMapper.createArrayNode();
        ArrayNode links = objectMapper.createArrayNode();

        ObjectNode ckptNode = objectMapper.createObjectNode();
        ckptNode.put("id", 101);
        ckptNode.put("type", "CheckpointLoaderSimple");
        ArrayNode wv = objectMapper.createArrayNode();
        wv.add("FLUX.2/flux2KleinAIO_flux24BDistilledAIO.safetensors");
        ckptNode.set("widgets_values", wv);
        nodes.add(ckptNode);

        // Link 1: MODEL (slot 0), Link 2: CLIP (slot 1), Link 3: VAE (slot 2)
        links.add(objectMapper.createArrayNode().add(1).add(101).add(0).add(50).add(0).add("MODEL"));
        links.add(objectMapper.createArrayNode().add(2).add(101).add(1).add(51).add(0).add("CLIP"));
        links.add(objectMapper.createArrayNode().add(3).add(101).add(2).add(52).add(0).add("VAE"));

        graph.set("nodes", nodes);
        graph.set("links", links);

        de.tki.comfyuicompanion.domain.ModelInfo unet = new de.tki.comfyuicompanion.domain.ModelInfo("unet", "flux-2-klein-base-4b-fp8.safetensors", "LOCAL");
        unet.setFilename("flux-2-klein-base-4b-fp8.safetensors");
        de.tki.comfyuicompanion.domain.ModelInfo vae = new de.tki.comfyuicompanion.domain.ModelInfo("vae", "ae.safetensors", "LOCAL");
        vae.setFilename("ae.safetensors");
        de.tki.comfyuicompanion.domain.ModelInfo clipL = new de.tki.comfyuicompanion.domain.ModelInfo("clip", "clip_l.safetensors", "LOCAL");
        clipL.setFilename("clip_l.safetensors");
        de.tki.comfyuicompanion.domain.ModelInfo t5 = new de.tki.comfyuicompanion.domain.ModelInfo("clip", "t5xxl_fp8_e4m3fn.safetensors", "LOCAL");
        t5.setFilename("t5xxl_fp8_e4m3fn.safetensors");

        resolver.adaptModelReferences(graph, List.of(unet, vae, clipL, t5));

        assertThat(graph.path("nodes")).hasSize(3);
        assertThat(graph.path("nodes").get(0).path("type").asText()).isEqualTo("UNETLoader");
        assertThat(graph.path("nodes").get(0).path("widgets_values").get(0).asText()).isEqualTo("flux-2-klein-base-4b-fp8.safetensors");
        assertThat(graph.path("nodes").get(1).path("type").asText()).isEqualTo("DualCLIPLoader");
        assertThat(graph.path("nodes").get(2).path("type").asText()).isEqualTo("VAELoader");
    }

    @Test
    @DisplayName("Should sanitize missing custom nodes (LoraManager, Image Saver Simple, easy int, VRAMCleanup) into core nodes")
    void shouldSanitizeMissingCustomNodesIntoCoreNodes() throws Exception {
        String rawWorkflow = """
                {
                  "last_node_id": 100,
                  "last_link_id": 10,
                  "nodes": [
                    {
                      "id": 1,
                      "type": "UNETLoader",
                      "properties": { "cnr_id": "comfy-core" },
                      "outputs": [{ "name": "MODEL", "type": "MODEL", "links": [1] }],
                      "widgets_values": ["flux.safetensors", "default"]
                    },
                    {
                      "id": 2,
                      "type": "Lora Loader (LoraManager)",
                      "properties": { "cnr_id": "comfyui-lora-manager" },
                      "inputs": [{ "name": "model", "type": "MODEL", "link": 1 }],
                      "outputs": [{ "name": "MODEL", "type": "MODEL", "links": [2] }]
                    },
                    {
                      "id": 3,
                      "type": "easy int",
                      "properties": { "cnr_id": "comfyui-easy-use" },
                      "outputs": [{ "name": "int", "type": "INT", "links": [3] }],
                      "widgets_values": [25]
                    },
                    {
                      "id": 4,
                      "type": "Flux2Scheduler",
                      "properties": { "cnr_id": "comfy-core" },
                      "inputs": [{ "name": "steps", "type": "INT", "widget": { "name": "steps" }, "link": 3 }],
                      "outputs": [{ "name": "SIGMAS", "type": "SIGMAS", "links": [] }],
                      "widgets_values": [20, 1024, 1024]
                    },
                    {
                      "id": 5,
                      "type": "VAEDecode",
                      "properties": { "cnr_id": "comfy-core" },
                      "inputs": [{ "name": "samples", "type": "LATENT", "link": null }],
                      "outputs": [{ "name": "IMAGE", "type": "IMAGE", "links": [4, 5] }]
                    },
                    {
                      "id": 6,
                      "type": "VRAMCleanup",
                      "properties": { "cnr_id": "comfyui_memory_cleanup" },
                      "inputs": [{ "name": "anything", "type": "*", "link": 4 }],
                      "outputs": [{ "name": "output", "type": "*", "links": [] }]
                    },
                    {
                      "id": 7,
                      "type": "Image Saver Simple",
                      "properties": { "cnr_id": "comfyui-image-saver" },
                      "inputs": [
                        { "name": "images", "type": "IMAGE", "link": 5 },
                        { "name": "filename", "type": "STRING", "link": null }
                      ],
                      "outputs": [{ "name": "images", "type": "IMAGE", "links": [] }],
                      "widgets_values": ["FLUX", "output", "png", 100, true, false]
                    }
                  ],
                  "links": [
                    [1, 1, 0, 2, 0, "MODEL"],
                    [2, 2, 0, 4, 0, "MODEL"],
                    [3, 3, 0, 4, 0, "INT"],
                    [4, 5, 0, 6, 0, "*"],
                    [5, 5, 0, 7, 0, "IMAGE"]
                  ]
                }
                """;
        ObjectNode wf = (ObjectNode) objectMapper.readTree(rawWorkflow);
        ComfyWorkflowCompatibilitySanitizer sanitizer = new ComfyWorkflowCompatibilitySanitizer(objectMapper);
        sanitizer.sanitizeWorkflow(wf, java.util.Collections.emptySet());

        List<String> remainingTypes = new java.util.ArrayList<>();
        wf.path("nodes").forEach(n -> remainingTypes.add(n.path("type").asText()));

        assertThat(remainingTypes).containsExactlyInAnyOrder("UNETLoader", "Flux2Scheduler", "VAEDecode", "SaveImage");
        // Check that easy int value (25) was inlined into Flux2Scheduler steps widget
        for (var n : wf.path("nodes")) {
            if ("Flux2Scheduler".equals(n.path("type").asText())) {
                assertThat(n.path("widgets_values").get(0).asInt()).isEqualTo(25);
                assertThat(n.path("inputs").get(0).get("link").isNull()).isTrue();
            }
        }
    }

    @Test
    @DisplayName("Should sanitize full Flux2k_4B_AIO_T2I_v1.0 workflow into 100% comfy-core nodes and split CheckpointLoaderSimple")
    void shouldSanitizeFullFlux2kAioWorkflow() throws Exception {
        Path existingWf = Path.of("user_workflows", "model_comfyui_beginner_friendly_flux_2_klein_4b_all-in-one_text-to-image_workflow_with_easy_prompt_saver_by_sarcastic_tofu.json");
        if (!Files.exists(existingWf)) {
            return;
        }
        ObjectNode wf = (ObjectNode) objectMapper.readTree(existingWf.toFile());

        de.tki.comfyuicompanion.domain.ModelInfo unet = new de.tki.comfyuicompanion.domain.ModelInfo("unet", "flux-2-klein-base-4b-fp8.safetensors", "LOCAL");
        unet.setFilename("flux-2-klein-base-4b-fp8.safetensors");
        de.tki.comfyuicompanion.domain.ModelInfo vae = new de.tki.comfyuicompanion.domain.ModelInfo("vae", "ae.safetensors", "LOCAL");
        vae.setFilename("ae.safetensors");
        de.tki.comfyuicompanion.domain.ModelInfo clipL = new de.tki.comfyuicompanion.domain.ModelInfo("clip", "clip_l.safetensors", "LOCAL");
        clipL.setFilename("clip_l.safetensors");
        de.tki.comfyuicompanion.domain.ModelInfo t5 = new de.tki.comfyuicompanion.domain.ModelInfo("clip", "t5xxl_fp8_e4m3fn.safetensors", "LOCAL");
        t5.setFilename("t5xxl_fp8_e4m3fn.safetensors");

        resolver.adaptModelReferences(wf, List.of(unet, vae, clipL, t5));
        ComfyWorkflowCompatibilitySanitizer sanitizer = new ComfyWorkflowCompatibilitySanitizer(objectMapper);
        sanitizer.sanitizeWorkflow(wf, java.util.Collections.emptySet());

        List<String> nodeTypes = new java.util.ArrayList<>();
        wf.path("nodes").forEach(n -> nodeTypes.add(n.path("type").asText()));

        assertThat(nodeTypes)
                .doesNotContain("CheckpointLoaderSimple", "Lora Loader (LoraManager)", "Image Saver Simple",
                        "Seed Generator (Image Saver)", "easy int", "Sampler Selector", "VRAMCleanup",
                        "RAMCleanup", "SimpleReadableMetadataSaveTextSG", "JoinStringMulti")
                .contains("UNETLoader", "DualCLIPLoader", "VAELoader", "SaveImage", "SamplerCustomAdvanced", "VAEDecode");
    }

    @Test
    @DisplayName("Should extract models from workflow with Markdown URLs without crashing on Windows")
    void shouldExtractModelsFromWorkflowWithMarkdownUrlsWithoutCrashing() {
        ObjectNode wf = objectMapper.createObjectNode();
        ArrayNode nodes = objectMapper.createArrayNode();

        ObjectNode noteNode = objectMapper.createObjectNode();
        noteNode.put("id", 10);
        noteNode.put("type", "MarkdownNote");
        ArrayNode noteWidgets = objectMapper.createArrayNode();
        noteWidgets.add("[qwen3.5_4b_bf16.safetensors](https://huggingface.co/Comfy-Org/Qwen3.5/resolve/main/text_encoders/qwen3.5_4b_bf16.safetensors)");
        noteNode.set("widgets_values", noteWidgets);
        nodes.add(noteNode);

        ObjectNode unetNode = objectMapper.createObjectNode();
        unetNode.put("id", 11);
        unetNode.put("type", "UNETLoader");
        ArrayNode unetWidgets = objectMapper.createArrayNode();
        unetWidgets.add("flux3_image_klein_sd_int8_convrot.safetensors");
        unetNode.set("widgets_values", unetWidgets);
        nodes.add(unetNode);

        wf.set("nodes", nodes);

        de.tki.comfyuicompanion.service.workflow.WorkflowModelExtractor extractor =
                new de.tki.comfyuicompanion.service.workflow.WorkflowModelExtractor(null);
        List<de.tki.comfyuicompanion.domain.ModelInfo> models = extractor.extractModels(wf);

        assertThat(models).hasSize(2);
        assertThat(models).extracting(de.tki.comfyuicompanion.domain.ModelInfo::getFilename)
                .containsExactlyInAnyOrder("qwen3.5_4b_bf16.safetensors", "flux3_image_klein_sd_int8_convrot.safetensors");
        assertThat(models.stream().filter(m -> m.getFilename().equals("qwen3.5_4b_bf16.safetensors")).findFirst().get().getUrl())
                .isEqualTo("https://huggingface.co/Comfy-Org/Qwen3.5/resolve/main/text_encoders/qwen3.5_4b_bf16.safetensors");
    }
}
