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

    @Test
    @DisplayName("Should filter out pure model weight repositories without ComfyUI workflow JSON files")
    void shouldFilterOutPureModelDumpsWhenOnlyWorkflowsEnabled() {
        String apiJson = """
                [
                  {
                    "id": "Winnougan/MiniMax-H3-INT4_Convrot_ComfyUI",
                    "author": "Winnougan",
                    "downloads": 50,
                    "likes": 39,
                    "pipeline_tag": "image-to-video",
                    "tags": ["comfyui"],
                    "siblings": [
                      {"rfilename": "MMH3-FLA-TurboV1.2-int8-convrot-simple.safetensors"},
                      {"rfilename": "qwen3vl_32b_minimax_h3-int4_convrot.safetensors"},
                      {"rfilename": "config.json"}
                    ]
                  },
                  {
                    "id": "Comfy-Org/Wan_2.1_ComfyUI_Workflows",
                    "author": "Comfy-Org",
                    "downloads": 12000,
                    "likes": 450,
                    "pipeline_tag": "image-to-video",
                    "tags": ["comfyui"],
                    "siblings": [
                      {"rfilename": "workflows/wan21_i2v_workflow.json"},
                      {"rfilename": "wan2.1_i2v_480p_14B_fp8.safetensors"}
                    ]
                  }
                ]
                """;

        List<HuggingFaceModelEntry> workflowRepos = service.parseModelEntries(apiJson, true);

        assertThat(workflowRepos).hasSize(1);
        assertThat(workflowRepos.get(0).repoId()).isEqualTo("Comfy-Org/Wan_2.1_ComfyUI_Workflows");
        assertThat(workflowRepos.get(0).files()).extracting(HuggingFaceFileEntry::filename)
                .contains("wan21_i2v_workflow.json", "wan2.1_i2v_480p_14B_fp8.safetensors");
    }

    @Test
    @DisplayName("Should deduplicate multi-quantization files by functional role for a single workflow run")
    void shouldDeduplicateMultiQuantizationFilesForSingleRun() {
        List<HuggingFaceFileEntry> sevenFiles = List.of(
                new HuggingFaceFileEntry("ckpt1.safetensors", "ckpt1.safetensors", 19000000000L, "19 GB", "http://dl/1", "checkpoints", "Mid", true),
                new HuggingFaceFileEntry("ckpt2.safetensors", "ckpt2.safetensors", 21000000000L, "21 GB", "http://dl/2", "checkpoints", "High", true),
                new HuggingFaceFileEntry("ckpt3.safetensors", "ckpt3.safetensors", 12000000000L, "12 GB", "http://dl/3", "checkpoints", "Std", true),
                new HuggingFaceFileEntry("clip1.safetensors", "clip1.safetensors", 13000000000L, "13 GB", "http://dl/4", "clip", "Mid", true),
                new HuggingFaceFileEntry("clip2.safetensors", "clip2.safetensors", 14000000000L, "14 GB", "http://dl/5", "clip", "Mid", true),
                new HuggingFaceFileEntry("workflows/video_wf.json", "video_wf.json", 12000L, "Workflow JSON", "http://dl/wf", "workflows", "Workflow", false)
        );

        List<HuggingFaceFileEntry> required = HuggingFaceWorkflowAnalyzer.selectOperationalFilesForSingleRun(
                sevenFiles,
                sevenFiles.get(2),
                f -> "ckpt3.safetensors".equals(f.filename()) || "clip1.safetensors".equals(f.filename()) ? "Installed" : "Missing"
        );

        // Only 1 primary checkpoint + 1 CLIP encoder are required for the workflow run
        assertThat(required).hasSize(2);
        assertThat(required).extracting(HuggingFaceFileEntry::filename)
                .containsExactly("ckpt3.safetensors", "clip1.safetensors");
    }
}
