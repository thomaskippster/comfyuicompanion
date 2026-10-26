package de.tki.comfyuicompanion.service.impl;

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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

/**
 * Enterprise unit tests verifying {@link CivitaiLocalModelScanner} handling of nested subdirectories,
 * exclusion of incomplete download fragments, and filename cleaning.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CivitaiLocalModelScannerTest {

    @Mock
    private IConfigService configService;

    @TempDir
    Path tempModelsDir;

    private CivitaiLocalModelScanner scanner;

    @BeforeEach
    void setUp() {
        lenient().when(configService.getModelsPath()).thenReturn(tempModelsDir.toString());
        scanner = new CivitaiLocalModelScanner(configService);
    }

    @Test
    @DisplayName("Should detect model in nested subfolder and update save_path accordingly")
    void shouldDetectNestedModelFileAndSetSavePath() throws IOException {
        Path fluxDir = tempModelsDir.resolve("vae").resolve("FLUX1");
        Files.createDirectories(fluxDir);
        Path aeFile = fluxDir.resolve("ae.safetensors");
        Files.writeString(aeFile, "sample vae content");

        ModelInfo info = new ModelInfo("vae", "ae.safetensors", "LOCAL");
        info.setFilename("ae.safetensors");

        boolean present = scanner.isModelFilePresent(info);

        assertThat(present).isTrue();
        assertThat(info.getSave_path()).isEqualTo("vae/FLUX1");
    }

    @Test
    @DisplayName("Should find local model matching substring in nested subdirectories")
    void shouldFindNestedModelMatchingPattern() throws IOException {
        Path fluxDir = tempModelsDir.resolve("vae").resolve("FLUX1");
        Files.createDirectories(fluxDir);
        Path aeFile = fluxDir.resolve("ae.safetensors");
        Files.writeString(aeFile, "sample vae content");

        ModelInfo found = scanner.findLocalModelMatching("ae.safetensors", "vae");

        assertThat(found).isNotNull();
        assertThat(found.getFilename()).isEqualTo("ae.safetensors");
        assertThat(found.getSave_path()).isEqualTo("vae/FLUX1");
        assertThat(found.getDescription()).isEqualTo("✅ Already exists");
        assertThat(found.getPopularity()).isEqualTo("installed");
    }

    @Test
    @DisplayName("Should strictly ignore incomplete download fragments (.cmfd, .part*)")
    void shouldIgnoreIncompleteDownloadFragments() throws IOException {
        Path clipDir = tempModelsDir.resolve("clip");
        Files.createDirectories(clipDir);
        Files.writeString(clipDir.resolve("t5xxl_fp8_e4m3fn.safetensors.cmfd"), "fragment");
        Files.writeString(clipDir.resolve("t5xxl_fp8_e4m3fn.safetensors.part0"), "chunk 0");
        Files.writeString(clipDir.resolve("t5xxl_fp8_e4m3fn.safetensors.part1"), "chunk 1");

        ModelInfo matched = scanner.findLocalModelMatching("t5xxl", "clip", "text_encoders");

        assertThat(matched).isNull();
        assertThat(CivitaiLocalModelScanner.isCompletedModelFile(clipDir.resolve("t5xxl_fp8_e4m3fn.safetensors.cmfd"))).isFalse();
        assertThat(CivitaiLocalModelScanner.isCompletedModelFile(clipDir.resolve("t5xxl_fp8_e4m3fn.safetensors.part0"))).isFalse();
    }

    @Test
    @DisplayName("Should correctly clean partial download extensions from filenames")
    void shouldCleanModelFileName() {
        assertThat(CivitaiLocalModelScanner.cleanModelFileName("t5xxl_fp8_e4m3fn.safetensors.cmfd"))
                .isEqualTo("t5xxl_fp8_e4m3fn.safetensors");
        assertThat(CivitaiLocalModelScanner.cleanModelFileName("flux_unet.safetensors.part3"))
                .isEqualTo("flux_unet.safetensors");
        assertThat(CivitaiLocalModelScanner.cleanModelFileName("model.safetensors.cmfd_merge"))
                .isEqualTo("model.safetensors");
        assertThat(CivitaiLocalModelScanner.cleanModelFileName("ae.safetensors"))
                .isEqualTo("ae.safetensors");
    }

    @Test
    @DisplayName("Should find checkpoints located in subfolders with relative save_path")
    void shouldFindCheckpointsInSubfolders() throws IOException {
        Path sdxlDir = tempModelsDir.resolve("checkpoints").resolve("SDXL");
        Files.createDirectories(sdxlDir);
        Path ckpt = sdxlDir.resolve("sd_xl_base_1.0.safetensors");
        Files.writeString(ckpt, "checkpoint data");

        Optional<ModelInfo> infoOpt = scanner.findFirstLocalCheckpointInfo();

        assertThat(infoOpt).isPresent();
        ModelInfo info = infoOpt.get();
        assertThat(info.getFilename()).isEqualTo("sd_xl_base_1.0.safetensors");
        assertThat(info.getSave_path()).isEqualTo("checkpoints/SDXL");
        assertThat(info.getDescription()).isEqualTo("✅ Already exists");
    }

    @Test
    @DisplayName("Should detect model in extra model path registered in PathResolver")
    void shouldFindModelInExtraModelPathRegisteredInPathResolver(@TempDir Path extraDir) throws IOException {
        Path extraVaeDir = extraDir.resolve("extra_vae").resolve("FLUX1");
        Files.createDirectories(extraVaeDir);
        Files.writeString(extraVaeDir.resolve("ae.safetensors"), "flux vae binary");

        PathResolver pathResolver = new PathResolver();
        pathResolver.addExtraModelPath("vae", extraDir.resolve("extra_vae"));

        CivitaiLocalModelScanner scannerWithExtras = new CivitaiLocalModelScanner(configService, pathResolver);

        ModelInfo matched = scannerWithExtras.findLocalModelMatching("ae.safetensors", "vae");
        assertThat(matched).isNotNull();
        assertThat(matched.getFilename()).isEqualTo("ae.safetensors");
        assertThat(matched.getSave_path()).isEqualTo("vae/FLUX1");
        assertThat(matched.getPopularity()).isEqualTo("installed");

        ModelInfo checkInfo = new ModelInfo("vae", "ae.safetensors", "REMOTE");
        assertThat(scannerWithExtras.isModelFilePresent(checkInfo)).isTrue();
    }

    @Test
    @DisplayName("Should detect workflow zip and json files and mark them as present")
    void shouldDetectWorkflowZipAndJsonFiles() throws IOException {
        Path workflowDir = tempModelsDir.resolve("workflows");
        Files.createDirectories(workflowDir);
        Path zipFile = workflowDir.resolve("seargeSDXLEVOLVEDV432OptimizedWorkflowForComfyui_v432.zip");
        Files.writeString(zipFile, "zip header content");

        ModelInfo zipInfo = new ModelInfo("workflows", "seargeSDXLEVOLVEDV432OptimizedWorkflowForComfyui_v432.zip", "REMOTE");
        zipInfo.setFilename("seargeSDXLEVOLVEDV432OptimizedWorkflowForComfyui_v432.zip");
        zipInfo.setSave_path("workflows");

        boolean present = scanner.isModelFilePresent(zipInfo);
        assertThat(present).isTrue();

        scanner.checkAndSetModelStatus(zipInfo);
        assertThat(zipInfo.getPopularity()).isEqualTo("installed");
        assertThat(zipInfo.getDescription()).isEqualTo("✅ Already exists");
    }

    @Test
    @DisplayName("Should strictly ignore zip and json files when resolving base checkpoints")
    void shouldIgnoreZipAndJsonWhenScanningForCheckpoints() throws IOException {
        Path ckptDir = tempModelsDir.resolve("checkpoints");
        Files.createDirectories(ckptDir);
        Files.writeString(ckptDir.resolve("workflow_bundle.zip"), "not a checkpoint");
        Files.writeString(ckptDir.resolve("config.json"), "{}");

        Optional<ModelInfo> result = scanner.findFirstLocalCheckpointInfo();
        assertThat(result).isEmpty();

        Files.writeString(ckptDir.resolve("real_model.safetensors"), "real weights");
        Optional<ModelInfo> validResult = scanner.findFirstLocalCheckpointInfo();
        assertThat(validResult).isPresent();
        assertThat(validResult.get().getFilename()).isEqualTo("real_model.safetensors");
    }
}