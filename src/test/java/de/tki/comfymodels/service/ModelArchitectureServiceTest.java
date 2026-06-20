package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ModelArchitecture;
import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.ModelArchitectureService;
import de.tki.comfymodels.util.ComfyUIArchitectureClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ModelArchitectureServiceTest {

    @TempDir
    Path tempDir;

    private ModelArchitectureService architectureService;
    private ConfigService configService;

    @BeforeEach
    public void setup() {
        configService = new ConfigService(null, null) {
            @Override
            public String getAppDataPath() {
                return tempDir.toString();
            }
            @Override
            public String getComfyUIUrl() {
                return "";
            }
        };
        architectureService = new ModelArchitectureService(configService);
    }

    @Test
    public void testDetectArchitectureDefaultRules() {
        architectureService.init();

        assertEquals(ModelArchitecture.ARCH_FLUX, architectureService.detectArchitecture("flux1-dev-fp8.safetensors"));
        assertEquals(ModelArchitecture.ARCH_SDXL, architectureService.detectArchitecture("sd_xl_base_1.0.safetensors"));
        assertEquals(ModelArchitecture.ARCH_SDXL, architectureService.detectArchitecture("juggernaut_xl.safetensors"));
        assertEquals(ModelArchitecture.ARCH_LUMINA2, architectureService.detectArchitecture("longcat_image_bf16.safetensors"));
        assertEquals(ModelArchitecture.ARCH_SD15, architectureService.detectArchitecture("v1-5-pruned-emaonly.safetensors"));
        assertEquals(ModelArchitecture.ARCH_SD3, architectureService.detectArchitecture("sd3_medium.safetensors"));
        assertEquals(ModelArchitecture.ARCH_HUNYUAN, architectureService.detectArchitecture("hunyuan_video.safetensors"));
        assertEquals(ModelArchitecture.ARCH_WAN, architectureService.detectArchitecture("wan2.1_hybrid.safetensors"));
    }

    @Test
    public void testDetectArchitectureCustomRules() throws IOException {
        Path configDir = tempDir.resolve("templates/comfyui");
        Files.createDirectories(configDir);
        Path mappingFile = configDir.resolve("model_architecture_mapping.json");

        String customConfig = "{\n" +
                "  \"rules\": [\n" +
                "    { \"pattern\": \".*custom_flux_model.*\", \"architecture\": \"ARCH_FLUX\" },\n" +
                "    { \"pattern\": \".*legacy.*\", \"architecture\": \"ARCH_SD15\" }\n" +
                "  ]\n" +
                "}";
        Files.writeString(mappingFile, customConfig);

        architectureService.init();

        assertEquals(ModelArchitecture.ARCH_FLUX, architectureService.detectArchitecture("custom_flux_model.safetensors"));
        assertEquals(ModelArchitecture.ARCH_SD15, architectureService.detectArchitecture("legacy-model-v2.ckpt"));
        assertEquals(ModelArchitecture.ARCH_UNKNOWN, architectureService.detectArchitecture("unknown_model.safetensors"));
    }

    @Test
    public void testDetectArchitectureViaGemma() {
        ComfyUIArchitectureClassifier mockClassifier = Mockito.mock(ComfyUIArchitectureClassifier.class);
        Mockito.when(mockClassifier.classifyModel("new_exotic_model.safetensors"))
                .thenReturn(ModelArchitecture.ARCH_WAN);

        ModelArchitectureService customService = new ModelArchitectureService(configService, mockClassifier);
        customService.init();
        customService.setAnalyzing(true);

        // Should return ARCH_WAN detected via Gemma
        assertEquals(ModelArchitecture.ARCH_WAN, customService.detectArchitecture("new_exotic_model.safetensors"));

        // Subsequent call should hit cache (verify classifyModel was only called once)
        assertEquals(ModelArchitecture.ARCH_WAN, customService.detectArchitecture("new_exotic_model.safetensors"));
        Mockito.verify(mockClassifier, Mockito.times(1)).classifyModel("new_exotic_model.safetensors");
    }

    @Test
    public void testVideoBlueprintExtraction() throws Exception {
        Path comfyPath = tempDir.resolve("comfyui");
        Files.createDirectories(comfyPath.resolve("companion_blueprints"));
        
        ConfigService localConfig = new ConfigService(null, null) {
            @Override
            public String getAppDataPath() {
                return tempDir.toString();
            }
            @Override
            public String getComfyUIPath() {
                return comfyPath.toString();
            }
            @Override
            public String getComfyUIUrl() {
                return "";
            }
        };
        
        String videoBlueprint = "{\n" +
                "  \"3\": {\n" +
                "    \"inputs\": {\n" +
                "      \"unet_name\": \"Wan2.2\\\\wan2.2_i2v_high_noise_14B_fp8_scaled.safetensors\",\n" +
                "      \"weight_dtype\": \"default\"\n" +
                "    },\n" +
                "    \"class_type\": \"UNETLoader\"\n" +
                "  },\n" +
                "  \"4\": {\n" +
                "    \"inputs\": {\n" +
                "      \"unet_name\": \"Wan2.2\\\\wan2.2_i2v_low_noise_14B_fp8_scaled.safetensors\",\n" +
                "      \"weight_dtype\": \"default\"\n" +
                "    },\n" +
                "    \"class_type\": \"UNETLoader\"\n" +
                "  },\n" +
                "  \"5\": {\n" +
                "    \"inputs\": {\n" +
                "      \"clip_name\": \"umt5_xxl_fp8_e4m3fn_scaled.safetensors\"\n" +
                "    },\n" +
                "    \"class_type\": \"CLIPLoader\"\n" +
                "  },\n" +
                "  \"6\": {\n" +
                "    \"inputs\": {\n" +
                "      \"vae_name\": \"wan_2.1_vae.safetensors\"\n" +
                "    },\n" +
                "    \"class_type\": \"VAELoader\"\n" +
                "  },\n" +
                "  \"7\": {\n" +
                "    \"class_type\": \"WanImageToVideo\",\n" +
                "    \"inputs\": {}\n" +
                "  }\n" +
                "}";
        
        Files.writeString(comfyPath.resolve("companion_blueprints/wan_video.json"), videoBlueprint);
        
        ModelArchitectureService service = new ModelArchitectureService(localConfig);
        service.init();
        service.runBlueprintAnalysis();
        
        int count = 0;
        while (!service.isBlueprintAnalysisCompleted() && count < 50) {
            Thread.sleep(100);
            count++;
        }
        
        assertTrue(service.isBlueprintAnalysisCompleted());
        
        var highUnet = service.getDefaultsForModel("video_wan_high_unet");
        assertNotNull(highUnet);
        assertEquals("Wan2.2\\wan2.2_i2v_high_noise_14B_fp8_scaled.safetensors", highUnet.vaeName);
        
        var lowUnet = service.getDefaultsForModel("video_wan_low_unet");
        assertNotNull(lowUnet);
        assertEquals("Wan2.2\\wan2.2_i2v_low_noise_14B_fp8_scaled.safetensors", lowUnet.vaeName);

        var clip = service.getDefaultsForModel("video_wan_clip");
        assertNotNull(clip);
        assertEquals("umt5_xxl_fp8_e4m3fn_scaled.safetensors", clip.vaeName);

        var vae = service.getDefaultsForModel("video_wan_vae");
        assertNotNull(vae);
        assertEquals("wan_2.1_vae.safetensors", vae.vaeName);
    }
}
