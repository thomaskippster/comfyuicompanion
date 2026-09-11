package com.thomaskippster.comfyuicompanion.service.inspector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TriggerWordServiceTest {

    private TriggerWordService triggerWordService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        SafetensorsInspectorService inspector = new SafetensorsInspectorService(objectMapper);
        ModelArchitectureAnalyzer analyzer = new ModelArchitectureAnalyzer(objectMapper);

        triggerWordService = new TriggerWordService(
                inspector,
                analyzer,
                objectMapper,
                null,
                null,
                null
        );
    }

    @Test
    @DisplayName("extractTriggerInfo: should parse modelspec trigger phrase and tag frequencies")
    void shouldExtractTriggerPhraseAndTags() throws Exception {
        String mockHeaderJson = """
                {
                  "__metadata__": {
                    "modelspec.architecture": "sdxl",
                    "modelspec.trigger_phrase": "cyberpunk girl, neon lighting",
                    "ss_tag_frequency": "{\\"dataset_1\\": {\\"portrait\\": 50, \\"cyberpunk\\": 80, \\"neon\\": 30}}"
                  },
                  "lora_unet_down_blocks_0_attentions_0": {
                    "dtype": "F16",
                    "shape": [64, 64],
                    "data_offsets": [0, 8192]
                  }
                }
                """;

        // Create a valid binary .safetensors file with 8-byte little-endian header length
        byte[] jsonBytes = mockHeaderJson.getBytes(StandardCharsets.UTF_8);
        ByteBuffer lengthBuffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        lengthBuffer.putLong(jsonBytes.length);

        File modelFile = tempDir.resolve("test_lora.safetensors").toFile();
        try (FileOutputStream fos = new FileOutputStream(modelFile)) {
            fos.write(lengthBuffer.array());
            fos.write(jsonBytes);
            // Append dummy payload bytes
            fos.write(new byte[100]);
        }

        var triggerInfo = triggerWordService.extractTriggerInfo(modelFile.toPath());

        assertThat(triggerInfo).isNotNull();
        assertThat(triggerInfo.architecture()).isEqualTo("SDXL");
        assertThat(triggerInfo.modelType()).isEqualTo("LORA");
        assertThat(triggerInfo.triggerWords()).contains("cyberpunk girl, neon lighting");
        assertThat(triggerInfo.topTags()).containsEntry("cyberpunk", 80);
        assertThat(triggerInfo.topTags()).containsEntry("portrait", 50);
    }
}
