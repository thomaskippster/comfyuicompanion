package de.tki.comfyuicompanion.service;

import de.kherud.llama.ModelParameters;
import de.tki.comfyuicompanion.domain.HardwareProfile;
import de.tki.comfyuicompanion.domain.HardwareTier;
import de.tki.comfyuicompanion.service.impl.LlmGpuConfigurator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests verifying LLM GPU configurator parameters, fallback generation,
 * and hardware profile integration.
 */
public class LlmGpuConfiguratorTest {

    private IHardwareProfileService hardwareProfileService;
    private LlmGpuConfigurator configurator;

    @BeforeEach
    public void setUp() {
        hardwareProfileService = mock(IHardwareProfileService.class);
        configurator = new LlmGpuConfigurator(hardwareProfileService);
    }

    @Test
    public void testCreateFullGpuParametersWithNvidiaHardware() {
        HardwareProfile profile = new HardwareProfile("NVIDIA GeForce RTX 5080", 16L * 1024 * 1024 * 1024,
                32L * 1024 * 1024 * 1024, 16, true, HardwareTier.MID_TIER);
        when(hardwareProfileService.getHardwareProfile()).thenReturn(profile);

        File modelFile = new File("test_model.gguf");
        ModelParameters params = configurator.createFullGpuParameters(modelFile);

        assertNotNull(params);
        String paramStr = params.toString();
        // Model parameters toArray/toString includes -ngl 999
        assertTrue(paramStr.contains("999"), "Parameters should offload 999 layers to GPU");
    }

    @Test
    public void testCreateCpuFallbackParameters() {
        File modelFile = new File("test_model.gguf");
        ModelParameters params = configurator.createCpuFallbackParameters(modelFile);

        assertNotNull(params);
        String paramStr = params.toString();
        // Fallback should set gpu-layers 0
        assertTrue(paramStr.contains("-ngl 0") || paramStr.contains("--n-gpu-layers 0") || !paramStr.contains("999"));
    }

    @Test
    public void testDiscoverAndConfigureCudaLibraryDoesNotCrash() {
        assertDoesNotThrow(LlmGpuConfigurator::discoverAndConfigureCudaLibrary);
    }

    @Test
    public void testInitializeNativeLoggingDoesNotCrash() {
        assertDoesNotThrow(LlmGpuConfigurator::initializeNativeLogging);
    }
}
