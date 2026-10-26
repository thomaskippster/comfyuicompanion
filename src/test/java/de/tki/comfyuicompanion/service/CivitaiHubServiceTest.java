package de.tki.comfyuicompanion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.impl.CivitaiHubService;
import de.tki.comfyuicompanion.service.impl.ModelListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests verifying CivitaiHubService model extraction, conversion,
 * direct resolution, and youth protection installation validation.
 */
@ExtendWith(MockitoExtension.class)
class CivitaiHubServiceTest {

    @Mock
    private IConfigService configService;

    @Mock
    private ModelListService modelListService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CivitaiHubService hubService;

    @BeforeEach
    void setUp() {
        hubService = new CivitaiHubService(configService, modelListService, objectMapper);
    }

    @Test
    void shouldCreateModelInfoForDownload_whenLoRaEntryProvided() {
        CivitaiModelEntry entry = new CivitaiModelEntry(
                12345L,
                "Cyberpunk Detailer",
                "LORA",
                "TestArtist",
                "Flux.1 D",
                "<p>Great LoRA</p>",
                "https://image.civitai.com/preview.jpg",
                "https://civitai.com/api/download/models/12345",
                "cyberpunk_v1.safetensors",
                "240.5 MB",
                252182528L,
                1500,
                4.95,
                List.of("cyberpunk", "neon"),
                false
        );

        ModelInfo info = hubService.createModelInfoForDownload(entry);

        assertThat(info).isNotNull();
        assertThat(info.getName()).isEqualTo("Cyberpunk Detailer");
        assertThat(info.getType()).isEqualTo("loras");
        assertThat(info.getFilename()).isEqualTo("cyberpunk_v1.safetensors");
        assertThat(info.getSize()).isEqualTo("240.5 MB");
        assertThat(info.getByteSize()).isEqualTo(252182528L);
        assertThat(info.getBase()).isEqualTo("Flux.1 D");
        assertThat(info.getUrl()).isEqualTo("https://civitai.com/api/download/models/12345");
        assertThat(info.getPreviewPath()).isEqualTo("https://image.civitai.com/preview.jpg");
        assertThat(info.getPopularity()).isEqualTo("1500");
    }

    @Test
    void shouldCreateModelInfoForDownload_whenCheckpointEntryProvided() {
        CivitaiModelEntry entry = new CivitaiModelEntry(
                99999L,
                "Realistic Vision V6",
                "Checkpoint",
                "SG161222",
                "SD 1.5",
                "Photo realism model",
                "https://image.civitai.com/rv6.jpg",
                "https://civitai.com/api/download/models/99999",
                "realisticVisionV60B1_v51VAE.safetensors",
                "2.13 GB",
                2286900000L,
                45000,
                4.8,
                List.of(),
                false
        );

        ModelInfo info = hubService.createModelInfoForDownload(entry);

        assertThat(info).isNotNull();
        assertThat(info.getType()).isEqualTo("checkpoints");
        assertThat(info.getSave_path()).isEqualTo("checkpoints");
        assertThat(info.getFilename()).isEqualTo("realisticVisionV60B1_v51VAE.safetensors");
        assertThat(info.getSize()).isEqualTo("2.13 GB");
    }

    @Test
    void shouldRouteTextualInversionToEmbeddingsFolder_whenCreatedForDownload() {
        CivitaiModelEntry entry = new CivitaiModelEntry(
                112233L,
                "Bad Hands Embedding",
                "TextualInversion",
                "NegativeArtist",
                "SD 1.5",
                "Fixes bad anatomy",
                "https://image.civitai.com/badhands.jpg",
                "https://civitai.com/api/download/models/112233",
                "bad_hands_v5.pt",
                "25.0 KB",
                25600L,
                12000,
                4.7,
                List.of("badhands"),
                false
        );

        ModelInfo info = hubService.createModelInfoForDownload(entry);

        assertThat(info).isNotNull();
        assertThat(info.getType()).isEqualTo("embeddings");
        assertThat(info.getSave_path()).isEqualTo("embeddings");
        assertThat(info.getFilename()).isEqualTo("bad_hands_v5.pt");
    }

    @Test
    void shouldRouteWorkflowsToWorkflowsFolder_andBuildCorrectQueryUri() {
        CivitaiModelEntry entry = new CivitaiModelEntry(
                445566L,
                "Flux Realistic Canvas Workflow",
                "Workflows",
                "WorkflowMaster",
                "Flux.1 D",
                "Full ComfyUI Graph",
                "https://image.civitai.com/fluxwf.jpg",
                "https://civitai.com/api/download/models/445566",
                "flux_canvas_workflow.json",
                "45.2 KB",
                46284L,
                8900,
                4.95,
                List.of(),
                false
        );

        ModelInfo info = hubService.createModelInfoForDownload(entry);

        assertThat(info).isNotNull();
        assertThat(info.getType()).isEqualTo("workflows");
        assertThat(info.getSave_path()).isEqualTo("workflows");
        assertThat(info.getFilename()).isEqualTo("flux_canvas_workflow.json");

        URI queryUri = hubService.buildQueryUri("flux", "Workflows", "Flux.1 D", "Highest Rated", 20, 1, false);
        assertThat(queryUri.toString()).contains("types=Workflows");
        assertThat(queryUri.toString()).contains("baseModels=Flux.1+D");
        assertThat(queryUri.toString()).contains("query=flux");
    }

    @Test
    void shouldReturnTrue_whenModelExistsInModelListService() {
        when(modelListService.findByFilename("test_model.safetensors"))
                .thenReturn(Optional.of(new ModelInfo()));

        boolean installed = hubService.isModelInstalled("test_model.safetensors", "Checkpoint");

        assertThat(installed).isTrue();
    }

    @Test
    void shouldReturnFalse_whenFilenameIsBlankOrNull() {
        assertThat(hubService.isModelInstalled(null, "Checkpoint")).isFalse();
        assertThat(hubService.isModelInstalled("   ", "LORA")).isFalse();
    }

    @Test
    void shouldDetectLocalInstallation_whenFileExistsOnDisk() throws IOException {
        Path tempDir = Files.createTempDirectory("civitai_test_models");
        Path checkpointsDir = Files.createDirectory(tempDir.resolve("checkpoints"));
        Path modelFile = Files.createFile(checkpointsDir.resolve("my_checkpoint.safetensors"));

        when(modelListService.findByFilename("my_checkpoint.safetensors")).thenReturn(Optional.empty());
        when(configService.getModelsPath()).thenReturn(tempDir.toString());

        boolean installed = hubService.isModelInstalled("my_checkpoint.safetensors", "Checkpoint");

        assertThat(installed).isTrue();

        // Cleanup
        Files.deleteIfExists(modelFile);
        Files.deleteIfExists(checkpointsDir);
        Files.deleteIfExists(tempDir);
    }

    @Test
    void shouldReturnFalse_whenFileDoesNotExistOnDisk() throws IOException {
        Path tempDir = Files.createTempDirectory("civitai_test_empty");

        when(modelListService.findByFilename("missing.safetensors")).thenReturn(Optional.empty());
        when(configService.getModelsPath()).thenReturn(tempDir.toString());

        boolean installed = hubService.isModelInstalled("missing.safetensors", "LORA");

        assertThat(installed).isFalse();

        Files.deleteIfExists(tempDir);
    }

    @Test
    void shouldOmitPageParam_whenQueryIsPresent() {
        URI uri = hubService.buildQueryUri("flux", "Checkpoint", "Flux.1 D", "Most Downloaded", 30, 2, false);

        assertThat(uri.getQuery()).contains("query=flux");
        assertThat(uri.getQuery()).doesNotContain("page=");
        assertThat(uri.getQuery()).contains("types=Checkpoint");
        assertThat(uri.getQuery()).contains("baseModels=Flux.1+D");
        assertThat(uri.getQuery()).contains("sort=Most+Downloaded");
        assertThat(uri.getQuery()).contains("nsfw=false");
    }

    @Test
    void shouldIncludePageParamAndEnforceSafeSearch_whenQueryIsBlank() {
        URI uri = hubService.buildQueryUri("", "All Types", "All Bases", "Highest Rated", 20, 3, true);

        assertThat(uri.getQuery()).contains("page=3");
        assertThat(uri.getQuery()).doesNotContain("query=");
        assertThat(uri.getQuery()).doesNotContain("types=");
        assertThat(uri.getQuery()).doesNotContain("baseModels=");
        assertThat(uri.getQuery()).contains("nsfw=false");
    }

    @Test
    void shouldRejectNsfwModel_inParserAndDownload() throws Exception {
        String nsfwJson = """
                {
                    "id": 88888,
                    "name": "Adult Explicit Model",
                    "type": "LORA",
                    "nsfw": true
                }
                """;
        JsonNode node = new ObjectMapper().readTree(nsfwJson);
        CivitaiModelEntry entry = hubService.parseModelEntry(node);
        assertThat(entry).isNull();
    }

    @Test
    void shouldCalculateThumbsRatingFallback_whenRatingIsZero() throws Exception {
        String json = """
                {
                    "id": 55555,
                    "name": "Thumbs Model",
                    "type": "LORA",
                    "stats": {
                        "downloadCount": 120,
                        "rating": 0.0,
                        "thumbsUpCount": 9,
                        "thumbsDownCount": 1
                    },
                    "modelVersions": [
                        {
                            "id": 111,
                            "baseModel": "SDXL 1.0",
                            "downloadUrl": "https://civitai.com/download/111",
                            "files": [
                                { "name": "thumbs_lora.safetensors", "sizeKB": 51200.0 }
                            ]
                        }
                    ]
                }
                """;

        CivitaiModelEntry entry = hubService.parseModelEntry(objectMapper.readTree(json));

        assertThat(entry).isNotNull();
        assertThat(entry.rating()).isEqualTo(4.5);
    }

    @Test
    void shouldFallbackToRootImages_whenVersionImagesAreMissing() throws Exception {
        String json = """
                {
                    "id": 77777,
                    "name": "Root Image Model",
                    "type": "Checkpoint",
                    "images": [
                        { "url": "https://image.civitai.com/root_preview.jpg" }
                    ],
                    "modelVersions": [
                        {
                            "id": 222,
                            "baseModel": "Flux.1 D",
                            "downloadUrl": "https://civitai.com/download/222",
                            "images": [],
                            "files": [
                                { "name": "root_img.safetensors", "sizeKB": 102400.0 }
                            ]
                        }
                    ]
                }
                """;

        CivitaiModelEntry entry = hubService.parseModelEntry(objectMapper.readTree(json));

        assertThat(entry).isNotNull();
        assertThat(entry.thumbnailUrl()).isEqualTo("https://image.civitai.com/root_preview.jpg");
    }

    @Test
    void shouldAcceptSfwModel_whenNsfwLevelIs3_PG13Bitmask() throws Exception {
        // Flux 3 Klein model case: nsfw is false, nsfwLevel is 3 (PG=1 | PG-13=2)
        String sfwJson = """
                {
                    "id": 3003490,
                    "name": "Flux 3 Klein INT8/INT6/INT4",
                    "type": "Checkpoint",
                    "nsfw": false,
                    "nsfwLevel": 3,
                    "modelVersions": [
                        {
                            "id": 3407393,
                            "name": "Flux 3 Klein INT8",
                            "baseModel": "Flux.3 Klein-base",
                            "downloadUrl": "https://civitai.com/download/3407393",
                            "images": [
                                { "url": "https://image.civitai.com/angel_preview.jpg", "nsfwLevel": 1 }
                            ],
                            "files": [
                                { "name": "flux3KleinINT8.safetensors", "sizeKB": 5672960.0 }
                            ]
                        }
                    ]
                }
                """;
        JsonNode node = objectMapper.readTree(sfwJson);
        CivitaiModelEntry entry = hubService.parseModelEntry(node);

        assertThat(entry).isNotNull();
        assertThat(entry.id()).isEqualTo(3003490L);
        assertThat(entry.name()).isEqualTo("Flux 3 Klein INT8/INT6/INT4");
        assertThat(entry.thumbnailUrl()).isEqualTo("https://image.civitai.com/angel_preview.jpg");
        assertThat(entry.nsfw()).isFalse();
    }

    @Test
    void shouldRejectMatureModel_whenNsfwLevelIs4_R() throws Exception {
        String rJson = """
                {
                    "id": 44444,
                    "name": "Mature R-Rated Model",
                    "type": "Checkpoint",
                    "nsfw": false,
                    "nsfwLevel": 4,
                    "modelVersions": [
                        { "id": 1, "downloadUrl": "https://civitai.com/download/1" }
                    ]
                }
                """;
        CivitaiModelEntry entry = hubService.parseModelEntry(objectMapper.readTree(rJson));
        assertThat(entry).isNull();
    }

    @Test
    void shouldRejectAdultModel_whenNsfwLevelIs8_X() throws Exception {
        String xJson = """
                {
                    "id": 88888,
                    "name": "Adult X-Rated Model",
                    "type": "LORA",
                    "nsfw": false,
                    "nsfwLevel": 8,
                    "modelVersions": [
                        { "id": 2, "downloadUrl": "https://civitai.com/download/2" }
                    ]
                }
                """;
        CivitaiModelEntry entry = hubService.parseModelEntry(objectMapper.readTree(xJson));
        assertThat(entry).isNull();
    }

    @Test
    void shouldRejectExplicitModel_whenNsfwLevelIs16_XXX() throws Exception {
        String xxxJson = """
                {
                    "id": 99999,
                    "name": "Explicit XXX-Rated Model",
                    "type": "LORA",
                    "nsfw": false,
                    "nsfwLevel": 16,
                    "modelVersions": [
                        { "id": 3, "downloadUrl": "https://civitai.com/download/3" }
                    ]
                }
                """;
        CivitaiModelEntry entry = hubService.parseModelEntry(objectMapper.readTree(xxxJson));
        assertThat(entry).isNull();
    }
}
