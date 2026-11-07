package de.tki.comfyuicompanion.service;

import de.tki.comfyuicompanion.domain.ComfyGenerationMetadata;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

class PngMetadataReaderServiceTest {

    private PngMetadataReaderService readerService;

    @BeforeEach
    void setUp() {
        readerService = new PngMetadataReaderService();
    }

    @Test
    void testExtractMetadataFromComfyUiPromptGraph(@TempDir Path tempDir) throws IOException {
        JSONObject promptGraph = new JSONObject();

        // 1. Positive CLIP Text
        JSONObject clipPos = new JSONObject();
        clipPos.put("class_type", "CLIPTextEncode");
        clipPos.put("inputs", new JSONObject().put("text", "masterpiece, a majestic cybernetic dragon soaring over neon Tokyo"));
        promptGraph.put("6", clipPos);

        // 2. Negative CLIP Text
        JSONObject clipNeg = new JSONObject();
        clipNeg.put("class_type", "CLIPTextEncode");
        clipNeg.put("inputs", new JSONObject().put("text", "blurry, low quality, oversaturated"));
        promptGraph.put("7", clipNeg);

        // 3. Model Loader
        JSONObject modelLoader = new JSONObject();
        modelLoader.put("class_type", "CheckpointLoaderSimple");
        modelLoader.put("inputs", new JSONObject().put("ckpt_name", "v1-5-pruned-emaonly.safetensors"));
        promptGraph.put("4", modelLoader);

        // 4. Empty Latent (dimensions)
        JSONObject latent = new JSONObject();
        latent.put("class_type", "EmptyLatentImage");
        latent.put("inputs", new JSONObject().put("width", 1024).put("height", 768).put("batch_size", 1));
        promptGraph.put("5", latent);

        // 5. KSampler
        JSONObject sampler = new JSONObject();
        sampler.put("class_type", "KSampler");
        JSONObject samplerInputs = new JSONObject();
        samplerInputs.put("seed", 4281928472L);
        samplerInputs.put("steps", 28);
        samplerInputs.put("cfg", 6.5);
        samplerInputs.put("sampler_name", "dpmpp_2m");
        samplerInputs.put("scheduler", "karras");
        samplerInputs.put("positive", new JSONArray().put("6").put(0));
        samplerInputs.put("negative", new JSONArray().put("7").put(0));
        sampler.put("inputs", samplerInputs);
        promptGraph.put("3", sampler);

        File testPng = tempDir.resolve("comfy_dragon.png").toFile();
        writeTestPng(testPng, Map.of(
                "prompt", promptGraph.toString(),
                "workflow", "{\"nodes\": []}"
        ));

        ComfyGenerationMetadata meta = readerService.extractMetadata(testPng);

        assertNotNull(meta);
        assertTrue(meta.hasParameters());
        assertEquals("masterpiece, a majestic cybernetic dragon soaring over neon Tokyo", meta.positivePrompt());
        assertEquals("blurry, low quality, oversaturated", meta.negativePrompt());
        assertEquals("v1-5-pruned-emaonly.safetensors", meta.modelName());
        assertEquals(4281928472L, meta.seed());
        assertEquals(28, meta.steps());
        assertEquals(6.5, meta.cfg(), 0.001);
        assertEquals("dpmpp_2m", meta.sampler());
        assertEquals("karras", meta.scheduler());
        assertEquals(1024, meta.width());
        assertEquals(768, meta.height());
        assertNotNull(meta.rawPromptJson());
        assertNotNull(meta.rawWorkflowJson());
    }

    @Test
    void testExtractMetadataFromCivitaiParametersBlock(@TempDir Path tempDir) throws IOException {
        String civitaiParams = "cyberpunk street market in the rain at midnight, reflections, neon lighting\n" +
                "Negative prompt: deformed, cartoon, lowres, extra limbs\n" +
                "Steps: 30, Sampler: Euler a, CFG scale: 7.5, Seed: 99887766, Size: 832x1216, Model: dreamshaperXL";

        File testPng = tempDir.resolve("civitai_sample.png").toFile();
        writeTestPng(testPng, Map.of("parameters", civitaiParams));

        ComfyGenerationMetadata meta = readerService.extractMetadata(testPng);

        assertNotNull(meta);
        assertTrue(meta.hasParameters());
        assertEquals("cyberpunk street market in the rain at midnight, reflections, neon lighting", meta.positivePrompt());
        assertEquals("deformed, cartoon, lowres, extra limbs", meta.negativePrompt());
        assertEquals("dreamshaperXL", meta.modelName());
        assertEquals(99887766L, meta.seed());
        assertEquals(30, meta.steps());
        assertEquals(7.5, meta.cfg(), 0.001);
        assertEquals("Euler a", meta.sampler());
        assertEquals(832, meta.width());
        assertEquals(1216, meta.height());
    }

    @Test
    void testExtractMetadataFromComfyUiWorkflowUiGraph(@TempDir Path tempDir) throws IOException {
        JSONObject workflowObj = new JSONObject();
        JSONArray nodes = new JSONArray();

        JSONObject clipNode = new JSONObject();
        clipNode.put("type", "CLIPTextEncode");
        clipNode.put("title", "CLIP Text Encode (Prompt)");
        clipNode.put("widgets_values", new JSONArray().put("epic cinematic portrait of a knight"));
        nodes.put(clipNode);

        JSONObject ksamplerNode = new JSONObject();
        ksamplerNode.put("type", "KSampler");
        ksamplerNode.put("widgets_values", new JSONArray()
                .put(123456789L)
                .put("randomize")
                .put(25)
                .put(7.0)
                .put("euler_ancestral")
                .put("karras")
                .put(1.0));
        nodes.put(ksamplerNode);

        JSONObject modelNode = new JSONObject();
        modelNode.put("type", "CheckpointLoaderSimple");
        modelNode.put("widgets_values", new JSONArray().put("sd_xl_base_1.0.safetensors"));
        nodes.put(modelNode);

        JSONObject latentNode = new JSONObject();
        latentNode.put("type", "EmptyLatentImage");
        latentNode.put("widgets_values", new JSONArray().put(832).put(1216).put(1));
        nodes.put(latentNode);

        workflowObj.put("nodes", nodes);

        File testPng = tempDir.resolve("ui_workflow_sample.png").toFile();
        writeTestPng(testPng, Map.of("workflow", workflowObj.toString()));

        ComfyGenerationMetadata meta = readerService.extractMetadata(testPng);

        assertNotNull(meta);
        assertTrue(meta.hasParameters());
        assertEquals("epic cinematic portrait of a knight", meta.positivePrompt());
        assertEquals("sd_xl_base_1.0.safetensors", meta.modelName());
        assertEquals(123456789L, meta.seed());
        assertEquals(25, meta.steps());
        assertEquals(7.0, meta.cfg(), 0.001);
        assertEquals("euler_ancestral", meta.sampler());
        assertEquals("karras", meta.scheduler());
        assertEquals(832, meta.width());
        assertEquals(1216, meta.height());
    }

    @Test
    void testNonPngAndMissingFilesHandledSafely(@TempDir Path tempDir) throws IOException {
        File txtFile = tempDir.resolve("not_an_image.txt").toFile();
        try (FileOutputStream fos = new FileOutputStream(txtFile)) {
            fos.write("hello world".getBytes(StandardCharsets.UTF_8));
        }

        ComfyGenerationMetadata meta1 = readerService.extractMetadata(txtFile);
        assertNotNull(meta1);
        assertFalse(meta1.hasParameters());

        ComfyGenerationMetadata meta2 = readerService.extractMetadata(new File("non_existent_file_xyz.png"));
        assertNotNull(meta2);
        assertFalse(meta2.hasParameters());

        ComfyGenerationMetadata metaNull = readerService.extractMetadata(null);
        assertNotNull(metaNull);
        assertFalse(metaNull.hasParameters());
    }

    private void writeTestPng(File outFile, Map<String, String> textChunks) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // PNG signature
        dos.write(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});

        // Dummy IHDR chunk (13 bytes data)
        writeChunk(dos, "IHDR", new byte[]{
                0, 0, 0, 1, // width = 1
                0, 0, 0, 1, // height = 1
                8, 2, 0, 0, 0
        });

        // Write tEXt chunks
        for (Map.Entry<String, String> entry : textChunks.entrySet()) {
            byte[] keyBytes = entry.getKey().getBytes(StandardCharsets.ISO_8859_1);
            byte[] valBytes = entry.getValue().getBytes(StandardCharsets.UTF_8);
            byte[] chunkData = new byte[keyBytes.length + 1 + valBytes.length];
            System.arraycopy(keyBytes, 0, chunkData, 0, keyBytes.length);
            chunkData[keyBytes.length] = 0; // null separator
            System.arraycopy(valBytes, 0, chunkData, keyBytes.length + 1, valBytes.length);

            writeChunk(dos, "tEXt", chunkData);
        }

        // IEND chunk
        writeChunk(dos, "IEND", new byte[0]);

        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            fos.write(baos.toByteArray());
        }
    }

    private void writeChunk(DataOutputStream dos, String type, byte[] data) throws IOException {
        dos.writeInt(data.length);
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        dos.write(typeBytes);
        dos.write(data);

        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        dos.writeInt((int) crc.getValue());
    }
}
