package de.tki.comfyuicompanion.service;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests verifying CivitaiHubService model extraction, conversion,
 * and installation validation.
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
        assertThat(info.getFilename()).isEqualTo("realisticVisionV60B1_v51VAE.safetensors");
        assertThat(info.getSize()).isEqualTo("2.13 GB");
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
}
