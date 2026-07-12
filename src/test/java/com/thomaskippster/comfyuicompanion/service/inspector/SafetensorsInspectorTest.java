package com.thomaskippster.comfyuicompanion.service.inspector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SafetensorsInspectorTest {

    private VramPredictor vramPredictor;
    private ModelArchitectureAnalyzer architectureAnalyzer;

    @BeforeEach
    public void setUp() {
        // We can just use a plain ObjectMapper since no complex configurations are needed for the test
        ObjectMapper objectMapper = new ObjectMapper();
        this.vramPredictor = new VramPredictor(objectMapper);
        this.architectureAnalyzer = new ModelArchitectureAnalyzer(objectMapper);
    }

    @Test
    public void testVramPredictionExactBytes() throws Exception {
        // Mock JSON mimicking a single F16 tensor (like a large UNet layer)
        String mockHeader = "{\n" +
                "  \"__metadata__\": {},\n" +
                "  \"mock_tensor\": {\n" +
                "    \"dtype\": \"F16\",\n" +
                "    \"shape\": [49152, 576],\n" +
                "    \"data_offsets\": [0, 56623104]\n" +
                "  }\n" +
                "}";

        long predictedBytes = vramPredictor.calculateWeightsSizeInBytes(mockHeader);
        
        // Mathematically: 49152 * 576 elements * 2 bytes per element (F16) = 56623104
        assertEquals(56623104L, predictedBytes, "The VRAM predictor should calculate the exact mathematically correct byte size.");
    }

    @Test
    public void testArchitectureAnalyzerSdxl() throws Exception {
        // Mock JSON with specific metadata triggers for SDXL
        String mockHeader = "{\n" +
                "  \"__metadata__\": {\n" +
                "    \"ss_base_model_version\": \"sdxl_1.0\"\n" +
                "  },\n" +
                "  \"some_tensor\": {\n" +
                "    \"dtype\": \"F16\",\n" +
                "    \"shape\": [1024, 1024],\n" +
                "    \"data_offsets\": [0, 2097152]\n" +
                "  }\n" +
                "}";

        ModelMetadata metadata = architectureAnalyzer.analyze(mockHeader);
        
        assertEquals("SDXL", metadata.getArchitectureType(), "The analyzer must classify ss_base_model_version: 'sdxl' as SDXL architecture.");
        assertEquals("CHECKPOINT", metadata.getModelType(), "Model should be classified as a generic Checkpoint");
    }
}
