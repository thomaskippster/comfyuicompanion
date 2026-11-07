package de.tki.comfyuicompanion.service.pipeline.strategy;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.pipeline.VideoWorkflowBuilder;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

public class VideoModelStrategyRegistryTest {

    private VideoWorkflowBuilder mockWorkflowBuilder;
    private VideoModelStrategyRegistry registry;
    private Wan22FastWan5BStrategy fastWanStrategy;
    private Wan22VideoStrategy wan22Strategy;
    private Wan21ImageToVideoStrategy wan21Strategy;
    private LtxVideoStrategy ltxStrategy;
    private HunyuanVideoStrategy hunyuanStrategy;
    private ImageDiffusionFallbackStrategy fallbackStrategy;

    @BeforeEach
    public void setUp() {
        mockWorkflowBuilder = Mockito.mock(VideoWorkflowBuilder.class);
        fastWanStrategy = new Wan22FastWan5BStrategy(null, null, null);
        wan22Strategy = new Wan22VideoStrategy(mockWorkflowBuilder, null, null);
        wan21Strategy = new Wan21ImageToVideoStrategy(mockWorkflowBuilder, null, null);
        ltxStrategy = new LtxVideoStrategy(mockWorkflowBuilder, null, null);
        hunyuanStrategy = new HunyuanVideoStrategy(mockWorkflowBuilder, null, null);
        fallbackStrategy = new ImageDiffusionFallbackStrategy(mockWorkflowBuilder);

        registry = new VideoModelStrategyRegistry(List.of(
                fallbackStrategy,
                ltxStrategy,
                wan22Strategy,
                hunyuanStrategy,
                wan21Strategy,
                fastWanStrategy
        ));
    }

    @Test
    public void testRegistrySortsByPriority() {
        List<IVideoModelStrategy> strategies = registry.getAllStrategies();
        assertEquals(6, strategies.size());
        assertEquals("wan-2.2-fastwan-5b", strategies.get(0).getModelId()); // Priority 110
        assertEquals("wan-2.2", strategies.get(1).getModelId()); // Priority 100
        assertEquals("wan-2.1-i2v", strategies.get(2).getModelId()); // Priority 90
        assertEquals("ltx-video", strategies.get(3).getModelId()); // Priority 70
        assertEquals("hunyuan-video", strategies.get(4).getModelId()); // Priority 60
        assertEquals("image-fallback", strategies.get(5).getModelId()); // Priority 0
    }

    @Test
    public void testFindByIdAndDisplayName() {
        assertTrue(registry.findById("wan-2.2-fastwan-5b").isPresent());
        assertTrue(registry.findById("FastWan 5B (Wan 2.2 - 16GB VRAM)").isPresent());
        assertTrue(registry.findById("wan-2.2").isPresent());
        assertTrue(registry.findById("wan-2.1-i2v").isPresent());
        assertTrue(registry.findById("Text to Video (Wan 2.2)").isPresent());
        assertTrue(registry.findById("Image to Video (Wan 2.1)").isPresent());
        assertTrue(registry.findById("ltx-video").isPresent());
        assertTrue(registry.findById("non-existent").isEmpty());
    }

    @Test
    public void testResolveBestStrategyWithFastWanAvailable() {
        IVideoModelStrategy resolved = registry.resolveBestStrategy(
                null,
                "http://127.0.0.1:8188",
                List.of("Wan2_2-TI2V-5B-FastWanFullAttn_bf16.safetensors"),
                List.of(),
                List.of("umt5_xxl_fp8.safetensors"),
                List.of("wan_2.1_vae.safetensors"),
                nodeClass -> true
        );

        assertNotNull(resolved);
        assertEquals("wan-2.2-fastwan-5b", resolved.getModelId());
    }

    @Test
    public void testResolveBestStrategyWithWanAvailable() {
        when(mockWorkflowBuilder.isWanSetupComplete(anyList(), anyList(), anyList())).thenReturn(true);

        IVideoModelStrategy resolved = registry.resolveBestStrategy(
                null,
                "http://127.0.0.1:8188",
                List.of("wan2.2_i2v_high_noise_14B_fp8_scaled.safetensors"),
                List.of(),
                List.of("umt5_xxl_fp8.safetensors"),
                List.of("wan_2.1_vae.safetensors"),
                nodeClass -> "WanImageToVideo".equals(nodeClass)
        );

        assertNotNull(resolved);
        assertEquals("wan-2.2", resolved.getModelId());
    }

    @Test
    public void testResolveBestStrategyWithRequestedModel() {
        when(mockWorkflowBuilder.isWanSetupComplete(anyList(), anyList(), anyList())).thenReturn(true);

        IVideoModelStrategy resolved = registry.resolveBestStrategy(
                "Image to Video (Wan 2.1)",
                "http://127.0.0.1:8188",
                List.of("wan2.1_i2v.safetensors"),
                List.of(),
                List.of("umt5.safetensors"),
                List.of("vae.safetensors"),
                nodeClass -> false
        );

        assertNotNull(resolved);
        assertEquals("wan-2.1-i2v", resolved.getModelId());
    }

    @Test
    public void testResolveFallbackWhenNoVideoModels() {
        when(mockWorkflowBuilder.isWanSetupComplete(anyList(), anyList(), anyList())).thenReturn(false);

        IVideoModelStrategy resolved = registry.resolveBestStrategy(
                null,
                "http://127.0.0.1:8188",
                List.of("sdxl_unet.safetensors"),
                List.of("sdxl_base.safetensors"),
                List.of("clip_l.safetensors"),
                List.of("sdxl_vae.safetensors"),
                nodeClass -> false
        );

        assertNotNull(resolved);
        assertEquals("image-fallback", resolved.getModelId());
    }

    @Test
    public void testBuildWorkflowViaStrategy() throws Exception {
        Scene scene = new Scene("S1", "Futuristic cyberpunk Tokyo in rain");
        JSONObject mockJson = new JSONObject().put("mockKey", "mockValue");
        when(mockWorkflowBuilder.generateWanWorkflowJson(anyString(), any(), any(), anyLong(), anyString()))
                .thenReturn(mockJson);

        JSONObject result = wan22Strategy.buildWorkflow(
                "http://127.0.0.1:8188", scene, null, 12345L, "test_prefix",
                List.of(), List.of(), List.of(), List.of()
        );

        assertNotNull(result);
        assertEquals("mockValue", result.getString("mockKey"));
    }
}
