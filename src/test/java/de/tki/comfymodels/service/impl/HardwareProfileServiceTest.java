package de.tki.comfymodels.service.impl;

import de.tki.comfymodels.domain.HardwareProfile;
import de.tki.comfymodels.domain.HardwareTier;
import de.tki.comfymodels.domain.Scene;
import de.tki.comfymodels.domain.VideoPresetConfig;
import de.tki.comfymodels.service.IHardwareProfileService;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

public class HardwareProfileServiceTest {

    private static final long GIB = 1024L * 1024L * 1024L;

    @Test
    public void testClassifyTierDeterministically() {
        // CPU Only
        assertEquals(HardwareTier.CPU_ONLY, HardwareProfileService.classifyTier(0L, false));
        assertEquals(HardwareTier.CPU_ONLY, HardwareProfileService.classifyTier(-1L, false));

        // Budget GPU (< 12 GB)
        assertEquals(HardwareTier.BUDGET, HardwareProfileService.classifyTier(4L * GIB, true));
        assertEquals(HardwareTier.BUDGET, HardwareProfileService.classifyTier(8L * GIB, true));
        assertEquals(HardwareTier.BUDGET, HardwareProfileService.classifyTier(11L * GIB, true));

        // Mid-Tier GPU (12 - 20 GB)
        assertEquals(HardwareTier.MID_TIER, HardwareProfileService.classifyTier(12L * GIB, true));
        assertEquals(HardwareTier.MID_TIER, HardwareProfileService.classifyTier(16L * GIB, true));

        // High-End GPU (>= 20 GB)
        assertEquals(HardwareTier.HIGH_END, HardwareProfileService.classifyTier(24L * GIB, true));
        assertEquals(HardwareTier.HIGH_END, HardwareProfileService.classifyTier(48L * GIB, true));
    }

    @Test
    public void testHardwareProfileFormattingsAndWeakSystemCheck() {
        HardwareProfile cpuProfile = new HardwareProfile("N/A", 0L, 16L * GIB, 8, false, HardwareTier.CPU_ONLY);
        assertTrue(cpuProfile.isWeakSystem());
        assertEquals("N/A (CPU)", cpuProfile.formattedVram());
        assertEquals("16.0 GiB", cpuProfile.formattedRam());

        HardwareProfile budgetProfile = new HardwareProfile("NVIDIA GeForce RTX 3060", 8L * GIB, 32L * GIB, 16, true, HardwareTier.BUDGET);
        assertTrue(budgetProfile.isWeakSystem());
        assertEquals("8.0 GiB", budgetProfile.formattedVram());

        HardwareProfile midProfile = new HardwareProfile("NVIDIA GeForce RTX 4080", 16L * GIB, 64L * GIB, 24, true, HardwareTier.MID_TIER);
        assertFalse(midProfile.isWeakSystem());
        assertEquals("16.0 GiB", midProfile.formattedVram());

        HardwareProfile highProfile = new HardwareProfile("NVIDIA GeForce RTX 4090", 24L * GIB, 64L * GIB, 32, true, HardwareTier.HIGH_END);
        assertFalse(highProfile.isWeakSystem());
        assertEquals("24.0 GiB", highProfile.formattedVram());
    }

    @Test
    public void testRecommendedConfigsPerTier() {
        HardwareMonitorService mockMonitor = Mockito.mock(HardwareMonitorService.class);
        HardwareProfileService service = new HardwareProfileService(mockMonitor);

        // 1. When monitor reports Budget 8GB
        Mockito.when(mockMonitor.getVramBytes()).thenReturn(8L * GIB);
        Mockito.when(mockMonitor.getGpuName()).thenReturn("NVIDIA GeForce RTX 3060");
        service.refreshProfile();

        VideoPresetConfig budgetConfig = service.getRecommendedVideoConfig();
        assertNotNull(budgetConfig);
        assertEquals("Text to Video (LTX-Video 0.9.5)", budgetConfig.recommendedModel(), "Budget system should recommend lightweight LTX-Video");
        assertEquals(640, budgetConfig.defaultWidth());
        assertEquals(360, budgetConfig.defaultHeight());
        assertEquals("background_mode", budgetConfig.recommendedProfileId(), "Budget systems should recommend lowvram profile");

        // 2. When monitor reports Mid-tier 16GB
        Mockito.when(mockMonitor.getVramBytes()).thenReturn(16L * GIB);
        Mockito.when(mockMonitor.getGpuName()).thenReturn("NVIDIA GeForce RTX 4080");
        service.refreshProfile();

        VideoPresetConfig midConfig = service.getRecommendedVideoConfig();
        assertNotNull(midConfig);
        assertEquals(832, midConfig.defaultWidth());
        assertEquals(480, midConfig.defaultHeight());

        // 3. When monitor reports High-end 24GB
        Mockito.when(mockMonitor.getVramBytes()).thenReturn(24L * GIB);
        Mockito.when(mockMonitor.getGpuName()).thenReturn("NVIDIA GeForce RTX 4090");
        service.refreshProfile();

        VideoPresetConfig highConfig = service.getRecommendedVideoConfig();
        assertNotNull(highConfig);
        assertEquals(1280, highConfig.defaultWidth());
        assertEquals(720, highConfig.defaultHeight());
        assertEquals("beast_mode", highConfig.recommendedProfileId());
    }

    @Test
    public void testPipelineClampsResolutionOnWeakHardware() throws Exception {
        IHardwareProfileService mockHwService = Mockito.mock(IHardwareProfileService.class);
        HardwareProfile budgetProfile = new HardwareProfile("NVIDIA GeForce GTX 1660", 6L * GIB, 16L * GIB, 8, true, HardwareTier.BUDGET);
        Mockito.when(mockHwService.getHardwareProfile()).thenReturn(budgetProfile);

        ConfigService mockConfig = Mockito.mock(ConfigService.class);
        ComfyPipelineService pipeline = new ComfyPipelineService(
                mockConfig, null, null, null, null, null, null, null, null, null, mockHwService
        );

        Scene scene = new Scene("S1", "Rocket launch", 0, 72, "", "");
        scene.setWidth(1920);
        scene.setHeight(1080); // Oversized 1080p on budget GPU

        java.lang.reflect.Method method = ComfyPipelineService.class.getDeclaredMethod(
                "generateWanWorkflowJson", String.class, Scene.class, String.class, long.class, String.class
        );
        method.setAccessible(true);

        JSONObject workflow = (JSONObject) method.invoke(pipeline, "http://127.0.0.1:8188", scene, null, 12345L, "test_prefix");
        assertNotNull(workflow);

        JSONObject emptyImgInputs = workflow.getJSONObject("20").getJSONObject("inputs");
        assertEquals(640, emptyImgInputs.getInt("width"), "Resolution should be automatically clamped to budget tier width");
        assertEquals(352, emptyImgInputs.getInt("height"), "Height should be downscaled proportionally and aligned to 16px");
    }
}
