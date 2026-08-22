package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ComfyTemplate;
import de.tki.comfymodels.service.impl.ComfyTemplateService;
import de.tki.comfymodels.service.impl.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ComfyTemplateServiceTest {

    @TempDir
    Path tempDir;

    private ComfyTemplateService templateService;
    private ConfigService configService;

    @BeforeEach
    public void setup() {
        configService = new ConfigService(null, null) {
            @Override
            public String getAppDataPath() {
                return tempDir.toString();
            }
        };
        templateService = new ComfyTemplateService(configService, null);
    }

    @Test
    public void testInitWritesDefaultsAndLoads() {
        templateService.init();

        List<ComfyTemplate> templates = templateService.getTemplates();
        assertFalse(templates.isEmpty(), "Templates list should not be empty after initialization");
        
        assertNotNull(templateService.getTemplateByFilename("sd15_base_api.json"));
        assertNotNull(templateService.getTemplateByFilename("sdxl_base_api.json"));
        assertNotNull(templateService.getTemplateByFilename("flux_base_api.json"));
        assertNotNull(templateService.getTemplateByFilename("lumina2_base_api.json"));

        ComfyTemplate sd15 = templateService.getTemplateByFilename("sd15_base_api.json");
        assertEquals("SD 1.5 Base", sd15.getName());

        ComfyTemplate lumina = templateService.getTemplateByFilename("lumina2_base_api.json");
        assertEquals("Lumina2 Base", lumina.getName());
    }

    @Test
    public void testDetermineTemplateForModel() {
        templateService.init();

        ComfyTemplate t1 = templateService.determineTemplateForModel("flux_sd_model.safetensors");
        assertEquals("flux_base_api.json", t1.getFilename());

        ComfyTemplate t2 = templateService.determineTemplateForModel("sdxl_juggernaut_v2.safetensors");
        assertEquals("sdxl_base_api.json", t2.getFilename());

        ComfyTemplate t3 = templateService.determineTemplateForModel("my_custom_sd15_model.safetensors");
        assertEquals("sd15_base_api.json", t3.getFilename());

        ComfyTemplate t4 = templateService.determineTemplateForModel("longcat_image_bf16.safetensors");
        assertEquals("lumina2_base_api.json", t4.getFilename());

        ComfyTemplate t5 = templateService.determineTemplateForModel("z_image_turbo_bf16.safetensors");
        assertEquals("lumina2_base_api.json", t5.getFilename());

        ComfyTemplate t6 = templateService.determineTemplateForModel("acestep_v1.5_turbo.safetensors");
        assertEquals("lumina2_base_api.json", t6.getFilename());

        ComfyTemplate t7 = templateService.determineTemplateForModel("wan2.1_i2v_720p.safetensors");
        assertEquals("template_wan_api.json", t7.getFilename());
    }

    @Test
    public void testModifyPayload() {
        templateService.init();

        ComfyTemplate sd15 = templateService.getTemplateByFilename("sd15_base_api.json");
        assertNotNull(sd15);

        String modified = templateService.modifyPayload(
                sd15.getContent(),
                "test_checkpoint.safetensors",
                "a portrait of a developer",
                "ugly, blurry"
        );

        assertNotNull(modified);
        assertTrue(modified.contains("test_checkpoint.safetensors"));
        assertTrue(modified.contains("a portrait of a developer"));
        assertTrue(modified.contains("ugly, blurry"));
    }

    @Test
    public void testModifyPayloadFlux() {
        templateService.init();

        ComfyTemplate flux = templateService.getTemplateByFilename("flux_base_api.json");
        assertNotNull(flux);
        assertTrue(flux.getContent().contains("UNETLoader"));
        assertTrue(flux.getContent().contains("DualCLIPLoader"));

        String modified = templateService.modifyPayload(
                flux.getContent(),
                "my_flux_unet.safetensors",
                "high tech lab",
                ""
        );

        assertNotNull(modified);
        assertTrue(modified.contains("my_flux_unet.safetensors"));
        assertTrue(modified.contains("high tech lab"));
    }

    @Test
    public void testModifyPayloadLumina2() {
        templateService.init();

        ComfyTemplate lumina = templateService.getTemplateByFilename("lumina2_base_api.json");
        assertNotNull(lumina);
        assertTrue(lumina.getContent().contains("UNETLoader"));
        assertTrue(lumina.getContent().contains("EmptySD3LatentImage"));

        String modified = templateService.modifyPayload(
                lumina.getContent(),
                "longcat_image_bf16.safetensors",
                "cybernetic tiger in neon alley",
                ""
        );

        assertNotNull(modified);
        assertTrue(modified.contains("longcat_image_bf16.safetensors"));
        assertTrue(modified.contains("cybernetic tiger in neon alley"));
    }

    @Test
    public void testLoadTemplateForArchitecture() throws Exception {
        templateService.init();

        for (de.tki.comfymodels.domain.ModelArchitecture arch : de.tki.comfymodels.domain.ModelArchitecture.values()) {
            if (arch == de.tki.comfymodels.domain.ModelArchitecture.ARCH_UNKNOWN) continue;
            com.fasterxml.jackson.databind.JsonNode node = templateService.loadTemplateForArchitecture(arch);
            assertNotNull(node);
            assertTrue(node.has("prompt"));
        }
    }

    @Test
    public void testInjectParameters() throws Exception {
        templateService.init();

        com.fasterxml.jackson.databind.JsonNode template = templateService.loadTemplateForArchitecture(de.tki.comfymodels.domain.ModelArchitecture.ARCH_SD15);
        assertNotNull(template);

        com.fasterxml.jackson.databind.JsonNode injected = templateService.injectParameters(
                template,
                "test_model.safetensors",
                "high-tech neon cat",
                "extra limbs"
        );

        assertNotNull(injected);
        
        // Check model name updated
        com.fasterxml.jackson.databind.JsonNode promptObj = injected.has("prompt") ? injected.get("prompt") : injected;
        boolean modelUpdated = false;
        boolean positiveUpdated = false;
        boolean negativeUpdated = false;

        java.util.Iterator<String> fields = promptObj.fieldNames();
        while (fields.hasNext()) {
            String id = fields.next();
            com.fasterxml.jackson.databind.JsonNode node = promptObj.get(id);
            String classType = node.path("class_type").asText();
            if ("CheckpointLoaderSimple".equals(classType)) {
                assertEquals("test_model.safetensors", node.path("inputs").path("ckpt_name").asText());
                modelUpdated = true;
            } else if ("CLIPTextEncode".equals(classType)) {
                String text = node.path("inputs").path("text").asText();
                if ("high-tech neon cat".equals(text)) {
                    positiveUpdated = true;
                } else if ("extra limbs".equals(text)) {
                    negativeUpdated = true;
                }
            }
        }

        assertTrue(modelUpdated);
        assertTrue(positiveUpdated);
        assertTrue(negativeUpdated);
    }

    @Test
    public void testGeneratePayload() throws Exception {
        templateService.init();

        com.fasterxml.jackson.databind.JsonNode template = templateService.loadTemplateForArchitecture(de.tki.comfymodels.domain.ModelArchitecture.ARCH_SD15);
        assertNotNull(template);

        String payload = templateService.generatePayload(template);
        assertNotNull(payload);
        
        com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload);
        assertTrue(root.has("prompt"));
        assertTrue(root.get("prompt").has("3")); // Nodes inside prompt
    }
}
