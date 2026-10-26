package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfyuicompanion.domain.HuggingFaceFileEntry;
import de.tki.comfyuicompanion.domain.HuggingFaceModelEntry;
import de.tki.comfyuicompanion.domain.ModelInfo;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

/**
 * Enterprise unit tests verifying {@link HuggingFaceHubService} mapping,
 * download ModelInfo synthesis, and local status checks.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HuggingFaceHubServiceTest {

    @Mock
    private IConfigService configService;

    @Mock
    private CivitaiLocalModelScanner localModelScanner;

    @TempDir
    Path tempArchiveDir;

    private HuggingFaceHubService service;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HuggingFaceFolderRouter folderRouter = new HuggingFaceFolderRouter();

    @BeforeEach
    void setUp() {
        lenient().when(configService.getHfToken()).thenReturn("hf_test_token_12345");
        lenient().when(configService.getArchivePath()).thenReturn(tempArchiveDir.toString());
        service = new HuggingFaceHubService(configService, objectMapper, folderRouter, localModelScanner);
    }

    @Test
    @DisplayName("Should create properly configured ModelInfo for downloading a Hugging Face file")
    void shouldCreateModelInfoForDownload() {
        HuggingFaceModelEntry repo = new HuggingFaceModelEntry(
                "city96/FLUX.1-dev-gguf",
                "city96",
                "FLUX.1-dev-gguf",
                200000,
                1500,
                "2024-08-18T08:14:09.000Z",
                "text-to-image",
                List.of("gguf", "flux"),
                "false",
                List.of()
        );

        HuggingFaceFileEntry file = new HuggingFaceFileEntry(
                "flux1-dev-Q4_K_M.gguf",
                "flux1-dev-Q4_K_M.gguf",
                6800000000L,
                "6.33 GB",
                "https://huggingface.co/city96/FLUX.1-dev-gguf/resolve/main/flux1-dev-Q4_K_M.gguf?download=true",
                "unet",
                "🟢 Standard (~8-12 GB)",
                true
        );

        ModelInfo info = service.createModelInfoForDownload(repo, file, "unet");

        assertThat(info).isNotNull();
        assertThat(info.getName()).isEqualTo("flux1-dev-Q4_K_M.gguf");
        assertThat(info.getFilename()).isEqualTo("flux1-dev-Q4_K_M.gguf");
        assertThat(info.getType()).isEqualTo("unet");
        assertThat(info.getSave_path()).isEqualTo("unet");
        assertThat(info.getUrl()).contains("city96/FLUX.1-dev-gguf");
        assertThat(info.getUrl()).endsWith("?download=true");
        assertThat(info.getByteSize()).isEqualTo(6800000000L);
    }

    @Test
    @DisplayName("Should detect model existing in cold archive directory")
    void shouldDetectModelInColdArchive() throws IOException {
        Path archivedFile = tempArchiveDir.resolve("flux1-dev-Q4_K_M.gguf");
        Files.writeString(archivedFile, "sample archived model");

        boolean inArchive = service.isFileInArchive("flux1-dev-Q4_K_M.gguf");
        boolean notInArchive = service.isFileInArchive("missing_model.safetensors");

        assertThat(inArchive).isTrue();
        assertThat(notInArchive).isFalse();
    }
}
