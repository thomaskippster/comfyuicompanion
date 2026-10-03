package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.ICivitaiHubService;
import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import de.tki.comfyuicompanion.service.IModelArchitectureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Enterprise unit tests verifying {@link CivitaiOperationalModelService} dependency resolution,
 * local disk scanning, ComfyUI workflow synthesis, and dispatching.
 */
@ExtendWith(MockitoExtension.class)
class CivitaiOperationalModelServiceTest {

    @Mock
    private ConfigService configService;

    @Mock
    private ICivitaiHubService civitaiHubService;

    @Mock
    private IModelArchitectureService architectureService;

    @Mock
    private ComfyApiClient comfyApiClient;

    @Mock
    private IComfyLifecycleService lifecycleService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempModelsDir;

    private CivitaiOperationalModelService service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(configService.getModelsPath()).thenReturn(tempModelsDir.toString());
        service = new CivitaiOperationalModelService(
                configService,
                civitaiHubService,
                architectureService,
                comfyApiClient,
                lifecycleService,
                objectMapper,
                new CivitaiGuiWorkflowBuilder(objectMapper)
        );
    }

    @Test
    @DisplayName("Should resolve full complement of operational models for a Flux LoRA")
    void shouldResolveRequiredModels_forFluxLora() {
        CivitaiModelEntry entry = new CivitaiModelEntry(
                101L,
                "Neon Cyberpunk Style",
                "LORA",
                "CreatorA",
                "Flux.1 D",
                "Sample description",
                "https://example.com/thumb.jpg",
                "https://example.com/download/101",
                "neon_cyberpunk.safetensors",
                "240 MB",
                250000000L,
                500,
                4.9,
                List.of("neon style", "cyberpunk"),
                false
        );

        ModelInfo primary = new ModelInfo("loras", "neon_cyberpunk.safetensors", "https://example.com/download/101");
        primary.setFilename("neon_cyberpunk.safetensors");
        when(civitaiHubService.createModelInfoForDownload(entry)).thenReturn(primary);

        List<ModelInfo> required = service.resolveRequiredModels(entry);

        // Expect: 1 primary LoRA + 1 base UNET + 1 VAE + 1 CLIP-L + 1 T5-XXL = 5 models
        assertThat(required).hasSize(5);
        assertThat(required.get(0).getFilename()).isEqualTo("neon_cyberpunk.safetensors");
        assertThat(required).extracting(ModelInfo::getFilename)
                .contains("neon_cyberpunk.safetensors", "flux1-dev-fp8.safetensors", "ae.safetensors", "clip_l.safetensors", "t5xxl_fp8_e4m3fn.safetensors");
    }

    @Test
    @DisplayName("Should reuse existing local models when available instead of requesting remote downloads")
    void shouldReuseExistingLocalModels_whenPresentOnDisk() throws IOException {
        // Create an existing base checkpoint on disk
        Path checkpointsDir = tempModelsDir.resolve("checkpoints");
        Files.createDirectories(checkpointsDir);
        Files.writeString(checkpointsDir.resolve("sd_xl_base_1.0.safetensors"), "dummy weights");

        CivitaiModelEntry entry = new CivitaiModelEntry(
                202L,
                "Cinematic Lighting SDXL",
                "LORA",
                "ArtistB",
                "SDXL 1.0",
                "Description",
                "https://example.com/thumb.jpg",
                "https://example.com/download/202",
                "cinematic_sdxl.safetensors",
                "150 MB",
                150000000L,
                1200,
                4.8,
                List.of("cinematic lighting"),
                false
        );

        ModelInfo primary = new ModelInfo("loras", "cinematic_sdxl.safetensors", "https://example.com/download/202");
        primary.setFilename("cinematic_sdxl.safetensors");
        when(civitaiHubService.createModelInfoForDownload(entry)).thenReturn(primary);

        List<ModelInfo> required = service.resolveRequiredModels(entry);

        assertThat(required).hasSize(2);
        ModelInfo baseModel = required.get(1);
        assertThat(baseModel.getFilename()).isEqualTo("sd_xl_base_1.0.safetensors");
        assertThat(baseModel.getDescription()).contains("Already exists");
        assertThat(baseModel.getPopularity()).isEqualTo("installed");
    }

    @Test
    @DisplayName("Should detect when all operational models are installed")
    void shouldReportAllModelsInstalled_whenFilesExist() throws IOException {
        Path lorasDir = tempModelsDir.resolve("loras");
        Path ckptDir = tempModelsDir.resolve("checkpoints");
        Files.createDirectories(lorasDir);
        Files.createDirectories(ckptDir);

        Files.writeString(lorasDir.resolve("my_lora.safetensors"), "lora bytes");
        Files.writeString(ckptDir.resolve("v1-5-pruned-emaonly.safetensors"), "ckpt bytes");

        ModelInfo loraInfo = new ModelInfo("loras", "my_lora.safetensors", "LOCAL");
        loraInfo.setFilename("my_lora.safetensors");

        ModelInfo baseInfo = new ModelInfo("checkpoints", "v1-5-pruned-emaonly.safetensors", "LOCAL");
        baseInfo.setFilename("v1-5-pruned-emaonly.safetensors");

        List<ModelInfo> required = List.of(loraInfo, baseInfo);

        assertThat(service.areAllModelsInstalled(required)).isTrue();
        assertThat(service.getMissingModels(required)).isEmpty();
    }

    @Test
    @DisplayName("Should correctly identify missing models that need downloading")
    void shouldIdentifyMissingModels() {
        ModelInfo m1 = new ModelInfo("loras", "missing_lora.safetensors", "https://example.com/lora");
        m1.setFilename("missing_lora.safetensors");

        ModelInfo m2 = new ModelInfo("checkpoints", "missing_base.safetensors", "https://example.com/base");
        m2.setFilename("missing_base.safetensors");

        List<ModelInfo> required = List.of(m1, m2);

        assertThat(service.areAllModelsInstalled(required)).isFalse();
        assertThat(service.getMissingModels(required)).hasSize(2);
    }

    @Test
    @DisplayName("Should generate valid ComfyUI workflow JSON injecting LoraLoader and trigger words")
    void shouldGenerateWorkflowJson_withLoraInjection() throws Exception {
        CivitaiModelEntry entry = new CivitaiModelEntry(
                303L,
                "Anime Flat Style",
                "LORA",
                "StyleMaster",
                "SD 1.5",
                "Flat style LoRA",
                "https://example.com/anime.jpg",
                "https://example.com/download/303",
                "flat_anime_v2.safetensors",
                "80 MB",
                80000000L,
                3400,
                4.95,
                List.of("flat anime", "cel shading"),
                false
        );

        ModelInfo base = new ModelInfo("checkpoints", "v1-5-pruned-emaonly.safetensors", "LOCAL");
        base.setFilename("v1-5-pruned-emaonly.safetensors");

        String workflowJson = service.generateWorkflowJson(entry, List.of(base), "cute girl smiling, sunset");

        assertThat(workflowJson).isNotBlank();
        JsonNode tree = objectMapper.readTree(workflowJson);
        assertThat(tree.has("prompt")).isTrue();
        JsonNode prompt = tree.get("prompt");

        // Verify LoraLoader was injected
        boolean hasLoraLoader = false;
        String injectedLoraName = null;
        for (JsonNode node : prompt) {
            if ("LoraLoader".equals(node.path("class_type").asText())) {
                hasLoraLoader = true;
                injectedLoraName = node.path("inputs").path("lora_name").asText();
                break;
            }
        }
        assertThat(hasLoraLoader).isTrue();
        assertThat(injectedLoraName).isEqualTo("flat_anime_v2.safetensors");
    }

    @Test
    @DisplayName("Should generate valid LiteGraph GUI workflow JSON")
    void shouldGenerateGuiWorkflowJson_usingGuiWorkflowBuilder() throws Exception {
        CivitaiModelEntry entry = new CivitaiModelEntry(
                505L,
                "AnimErge v5.0",
                "Checkpoint",
                "AnimeAuthor",
                "SD 1.5",
                "Top anime checkpoint",
                "https://example.com/thumb.jpg",
                "https://example.com/download/505",
                "animerge_v50.safetensors",
                "2.0 GB",
                2000000000L,
                1500,
                4.9,
                List.of("masterpiece", "anime style"),
                false
        );

        String guiJson = service.generateGuiWorkflowJson(entry, List.of());
        assertThat(guiJson).isNotBlank();

        JsonNode root = objectMapper.readTree(guiJson);
        assertThat(root.has("nodes")).isTrue();
        assertThat(root.has("links")).isTrue();
        assertThat(root.get("nodes").size()).isEqualTo(7);
        assertThat(root.path("last_node_id").asInt()).isEqualTo(9);
    }

    @Test
    @DisplayName("Should send workflow and trigger generation via ComfyApiClient")
    @SuppressWarnings("unchecked")
    void shouldSendToComfyUIAndGenerate_whenServerIsHealthy() throws Exception {
        when(lifecycleService.isHealthy()).thenReturn(true);
        when(configService.getComfyUIUrl()).thenReturn("http://localhost:8188");

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"prompt_id\": \"test-prompt-123\"}");
        when(comfyApiClient.postPrompt(anyString(), anyString())).thenReturn(mockResponse);
        when(comfyApiClient.fetchPromptHistoryImage(anyString(), eq("test-prompt-123")))
                .thenReturn(new ComfyApiClient.ImageOutput("landscape_001.png", "", "output"));

        CivitaiModelEntry entry = new CivitaiModelEntry(
                404L,
                "Landscape Master",
                "Checkpoint",
                "NatureArtist",
                "SDXL 1.0",
                "Landscapes",
                "https://example.com/nature.jpg",
                "https://example.com/download/404",
                "landscape_xl.safetensors",
                "6.5 GB",
                6500000000L,
                800,
                4.85,
                List.of(),
                false
        );

        CompletableFuture<de.tki.comfyuicompanion.domain.CivitaiGenerationResult> future = service.sendToComfyUIAndGenerate(entry, List.of(), "alpine mountains, majestic peaks");
        de.tki.comfyuicompanion.domain.CivitaiGenerationResult result = future.get();

        assertThat(result.success()).isTrue();
        assertThat(result.promptId()).isEqualTo("test-prompt-123");
        assertThat(result.filename()).isEqualTo("landscape_001.png");
    }
}
