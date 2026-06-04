package de.tki.comfymodels.service;

import de.tki.comfymodels.Main;
import de.tki.comfymodels.service.IComfyLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ModelResolutionTest {

    private Main mainInstance;
    private Set<String> comfyClips;
    private Set<String> comfyVaes;
    private Set<String> comfyClipTypes;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // Create dummy lifecycle service to avoid NullPointerException in Main constructor
        IComfyLifecycleService dummyLifecycle = new IComfyLifecycleService() {
            @Override public void start() {}
            @Override public void stop() {}
            @Override public void restart() {}
            @Override public String getStatus() { return "OFFLINE"; }
            @Override public boolean isRunning() { return false; }
            @Override public boolean isProcessAlive() { return false; }
            @Override public boolean isHealthy() { return false; }
            @Override public void fixSetup() {}
            @Override public void setOnBrowserLaunched(Runnable r) {}
            @Override public boolean isGuiLineShown() { return false; }
        };

        // Instantiate Main with nulls and our dummy service
        mainInstance = new Main(null, null, null, null, null, null, null,
                dummyLifecycle, null, null, null, null, null, null);

        // Access internal lists to configure test state
        comfyClips = (Set<String>) ReflectionTestUtils.getField(mainInstance, "comfyClips");
        comfyVaes = (Set<String>) ReflectionTestUtils.getField(mainInstance, "comfyVaes");
        comfyClipTypes = (Set<String>) ReflectionTestUtils.getField(mainInstance, "comfyClipTypes");

        // Clear and mock clip types to support standard test options
        comfyClipTypes.clear();
        comfyClipTypes.add("stable_diffusion");
        comfyClipTypes.add("flux");
        comfyClipTypes.add("lumina2");
        comfyClipTypes.add("wan");
    }

    @Test
    void testResolveClipForModelFluxWithNoCompatibleClips() {
        // Arrange: environment only contains an incompatible text encoder (qwen)
        comfyClips.add("qwen_3_4b.safetensors");

        // Act
        String result = (String) ReflectionTestUtils.invokeMethod(mainInstance, "resolveClipForModel", "FLUX1/flux1-schnell-fp8.safetensors");

        // Assert: should NOT fall back to qwen, but return the expected flux clip
        assertEquals("t5xxl_fp8_e4m3fn.safetensors", result);
    }

    @Test
    void testResolveClipForModelFluxWithCompatibleClipOnDisk() {
        // Arrange: environment contains expected flux clip or a variation of it
        comfyClips.add("qwen_3_4b.safetensors");
        comfyClips.add("t5xxl_fp16.safetensors");

        // Act
        String result = (String) ReflectionTestUtils.invokeMethod(mainInstance, "resolveClipForModel", "FLUX1/flux1-schnell-fp8.safetensors");

        // Assert: should resolve to the compatible clip
        assertEquals("t5xxl_fp16.safetensors", result);
    }

    @Test
    void testResolveClipTypeWithContextualModelOverride() {
        // Act: Test with a generic t5xxl filename and a Flux selected model
        String type = (String) ReflectionTestUtils.invokeMethod(mainInstance, "resolveClipType", "t5xxl_fp8_e4m3fn.safetensors", "flux1-schnell-fp8.safetensors");

        // Assert: type should override to flux
        assertEquals("flux", type);
    }

    @Test
    void testResolveVaeForModelFluxWithNoCompatibleVaes() {
        // Arrange: only contains incompatible wan VAE on disk
        comfyVaes.add("wan_2.1_vae.safetensors");

        // Act
        String result = (String) ReflectionTestUtils.invokeMethod(mainInstance, "resolveVaeForModel", "FLUX1/flux1-schnell-fp8.safetensors");

        // Assert: should NOT fall back to wan, but return the expected flux VAE name
        assertEquals("FLUX1/ae.safetensors", result);
    }

    @Test
    void testResolveVaeForModelFluxWithCompatibleVae() {
        // Arrange: contains a compatible VAE and an incompatible one
        comfyVaes.add("wan_2.1_vae.safetensors");
        comfyVaes.add("FLUX1/ae.safetensors");

        // Act
        String result = (String) ReflectionTestUtils.invokeMethod(mainInstance, "resolveVaeForModel", "FLUX1/flux1-schnell-fp8.safetensors");

        // Assert: should resolve to compatible flux VAE
        assertEquals("FLUX1/ae.safetensors", result);
    }
}
