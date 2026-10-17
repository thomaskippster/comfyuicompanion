package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.service.impl.ComfyPngMetadataReader.ExtractedPngWorkflow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ComfyPngMetadataReader} verifying streaming PNG chunk extraction
 * and ComfyUI workflow parameter restoration.
 */
public class ComfyPngMetadataReaderTest {

    @TempDir
    Path tempDir;

    @Test
    public void testReadComfyPngTextChunksAndExtractParameters() throws Exception {
        String promptJson = """
                {
                  "1": {
                    "class_type": "CheckpointLoaderSimple",
                    "inputs": { "ckpt_name": "juggernautXL_v9.safetensors" }
                  },
                  "2": {
                    "class_type": "CLIPTextEncode",
                    "inputs": { "text": "neon samurai in rain, cinematic lighting" }
                  },
                  "3": {
                    "class_type": "CLIPTextEncode",
                    "inputs": { "text": "blurry, watermark, lowres" }
                  },
                  "4": {
                    "class_type": "EmptyLatentImage",
                    "inputs": { "width": 1024, "height": 768, "batch_size": 2 }
                  },
                  "5": {
                    "class_type": "KSampler",
                    "inputs": {
                      "seed": 424242,
                      "steps": 28,
                      "cfg": 6.5,
                      "denoise": 0.85,
                      "sampler_name": "dpmpp_2m",
                      "scheduler": "karras",
                      "positive": ["2", 0],
                      "negative": ["3", 0]
                    }
                  }
                }
                """;

        File pngFile = tempDir.resolve("comfy_test.png").toFile();
        Files.write(pngFile.toPath(), buildMinimalPngWithTextChunk("prompt", promptJson));

        Optional<ExtractedPngWorkflow> opt = ComfyPngMetadataReader.readMetadata(pngFile);
        assertThat(opt).isPresent();
        ExtractedPngWorkflow wf = opt.get();

        assertThat(wf.positivePrompt()).isEqualTo("neon samurai in rain, cinematic lighting");
        assertThat(wf.negativePrompt()).isEqualTo("blurry, watermark, lowres");
        assertThat(wf.modelName()).isEqualTo("juggernautXL_v9.safetensors");
        assertThat(wf.seed()).isEqualTo(424242L);
        assertThat(wf.steps()).isEqualTo(28);
        assertThat(wf.cfg()).isEqualTo(6.5);
        assertThat(wf.denoise()).isEqualTo(0.85);
        assertThat(wf.width()).isEqualTo(1024);
        assertThat(wf.height()).isEqualTo(768);
        assertThat(wf.batchSize()).isEqualTo(2);
        assertThat(wf.samplerName()).isEqualTo("dpmpp_2m");
        assertThat(wf.scheduler()).isEqualTo("karras");
    }

    private static byte[] buildMinimalPngWithTextChunk(String keyword, String text) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        // PNG signature
        dos.write(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});

        // tEXt chunk
        ByteArrayOutputStream chunkData = new ByteArrayOutputStream();
        chunkData.write(keyword.getBytes(StandardCharsets.ISO_8859_1));
        chunkData.write(0);
        chunkData.write(text.getBytes(StandardCharsets.UTF_8));
        byte[] payload = chunkData.toByteArray();

        dos.writeInt(payload.length);
        byte[] type = "tEXt".getBytes(StandardCharsets.US_ASCII);
        dos.write(type);
        dos.write(payload);
        CRC32 crc = new CRC32();
        crc.update(type);
        crc.update(payload);
        dos.writeInt((int) crc.getValue());

        // IEND chunk
        dos.writeInt(0);
        dos.write("IEND".getBytes(StandardCharsets.US_ASCII));
        dos.writeInt(0xAE426082);
        dos.flush();
        return bos.toByteArray();
    }
}
