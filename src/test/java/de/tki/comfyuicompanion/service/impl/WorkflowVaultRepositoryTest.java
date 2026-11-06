package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.workflow.WorkflowModelExtractor;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("WorkflowVaultRepository Tests")
class WorkflowVaultRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should merge concrete workflow JSON nodes with meta models and exclude zip bundles")
    void shouldExtractAllWorkflowModelsAndMergeWithMeta() throws Exception {
        Path vaultDir = tempDir.resolve("vault");
        Files.createDirectories(vaultDir);

        ConfigService configService = mock(ConfigService.class);
        when(configService.getUserWorkflowsDir()).thenReturn(tempDir.toFile());

        // Create Hunyuan-style workflow JSON with UNETLoader, DualCLIPLoader, VAELoader
        String workflowJson = """
        {
          "last_node_id": 9,
          "nodes": [
            { "id": 1, "type": "UNETLoader", "widgets_values": ["flux3KleinGGUF_v10_q4KM.gguf", "default"] },
            { "id": 8, "type": "DualCLIPLoader", "widgets_values": ["clip_l.safetensors", "t5xxl_fp8_e4m3fn.safetensors", "flux", "default"] },
            { "id": 9, "type": "VAELoader", "widgets_values": ["ae.safetensors"] }
          ]
        }
        """;
        Files.writeString(vaultDir.resolve("Hunyuan_Test.json"), workflowJson);

        // Create meta.json containing zip bundle and partial checkpoint info
        String metaJson = """
        {
          "id": "Hunyuan_Test",
          "title": "Hunyuan Test AllInOne",
          "category": "Civitai Workflows",
          "architecture": "Hunyuan Video",
          "sourcePlatform": "CIVITAI",
          "requiredModels": [
            {
              "type": "workflows",
              "name": "Hunyuan Test AllInOne",
              "filename": "hunyuanAllinone_AllVersions.zip",
              "size": "162.9 KB",
              "byteSize": 166827
            },
            {
              "type": "checkpoints",
              "name": "flux3KleinGGUF_v10_q4KM.gguf",
              "filename": "flux3KleinGGUF_v10_q4KM.gguf",
              "size": "2.91 GB",
              "byteSize": 3122328352,
              "url": "https://civitai.com/api/download/models/flux3klein"
            }
          ]
        }
        """;
        Files.writeString(vaultDir.resolve("Hunyuan_Test.meta.json"), metaJson);

        WorkflowModelExtractor modelExtractor = new WorkflowModelExtractor(null);
        WorkflowVaultRepositoryImpl repository = new WorkflowVaultRepositoryImpl(
                configService, null, null, modelExtractor, null, null
        );

        List<BlueprintEntry> entries = repository.loadAllVaultEntries();
        assertThat(entries).hasSize(1);

        BlueprintEntry entry = entries.get(0);
        assertThat(entry.name).isEqualTo("Hunyuan Test AllInOne");
        assertThat(entry.requiredModels).isNotEmpty();

        // Must contain all 4 models from the actual workflow nodes
        List<String> modelNames = entry.requiredModels.stream().map(ModelInfo::getName).toList();
        assertThat(modelNames).containsExactlyInAnyOrder(
                "flux3KleinGGUF_v10_q4KM.gguf",
                "clip_l.safetensors",
                "t5xxl_fp8_e4m3fn.safetensors",
                "ae.safetensors"
        );

        // Must NOT contain the .zip bundle
        assertThat(modelNames).noneMatch(name -> name.endsWith(".zip"));

        // Must have enriched URL and size for the checkpoint from meta.json
        ModelInfo ckpt = entry.requiredModels.stream()
                .filter(m -> m.getName().equals("flux3KleinGGUF_v10_q4KM.gguf"))
                .findFirst().orElseThrow();
        assertThat(ckpt.getUrl()).isEqualTo("https://civitai.com/api/download/models/flux3klein");
        assertThat(ckpt.getSize()).isEqualTo("2.91 GB");
    }
}
