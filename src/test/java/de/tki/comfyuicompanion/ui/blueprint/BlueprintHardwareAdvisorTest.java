package de.tki.comfyuicompanion.ui.blueprint;

import de.tki.comfyuicompanion.domain.ComfyRegistryWorkflow;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintEntry;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BlueprintHardwareAdvisor} verifying GPU VRAM and disk space traffic-light badges.
 */
public class BlueprintHardwareAdvisorTest {

    private static final long GB = 1024L * 1024L * 1024L;

    @Test
    public void testFitsInVramOn16GbGpu() {
        ComfyRegistryWorkflow wf = new ComfyRegistryWorkflow();
        wf.setTitle("SDXL Base Workflow");
        ModelInfo sdxl = new ModelInfo("checkpoints", "sd_xl_base_1.0.safetensors", "");
        sdxl.setSize("6.46GB");
        wf.setRequiredModelInfos(List.of(sdxl));

        BlueprintEntry entry = new BlueprintEntry(wf);
        BlueprintStatus status = new BlueprintStatus(1, 0, List.of());

        BlueprintHardwareAdvisor.CompatibilityReport report =
                BlueprintHardwareAdvisor.evaluate(entry, status, 16L * GB, 100L * GB, null);

        assertThat(report.level()).isEqualTo(BlueprintHardwareAdvisor.CompatibilityLevel.FITS_IN_VRAM);
        assertThat(report.badgeText()).contains("🟢");
    }

    @Test
    public void testRequiresOffloadOn12GbGpuWithHeavyModel() {
        ComfyRegistryWorkflow wf = new ComfyRegistryWorkflow();
        wf.setTitle("Wan 2.1 14B Video");
        ModelInfo wan = new ModelInfo("diffusion_models", "wan2.1_t2v_14B_fp8.safetensors", "");
        wan.setSize("14.5GB");
        wf.setRequiredModelInfos(List.of(wan));

        BlueprintEntry entry = new BlueprintEntry(wf);
        BlueprintStatus status = new BlueprintStatus(1, 0, List.of());

        BlueprintHardwareAdvisor.CompatibilityReport report =
                BlueprintHardwareAdvisor.evaluate(entry, status, 12L * GB, 100L * GB, null);

        assertThat(report.level()).isEqualTo(BlueprintHardwareAdvisor.CompatibilityLevel.REQUIRES_OFFLOAD);
        assertThat(report.badgeText()).contains("🟡");
    }

    @Test
    public void testInsufficientDiskForMissingModel() {
        ComfyRegistryWorkflow wf = new ComfyRegistryWorkflow();
        wf.setTitle("Flux Dev Full");
        ModelInfo flux = new ModelInfo("unet", "flux1-dev.safetensors", "");
        flux.setSize("22.0GB");
        wf.setRequiredModelInfos(List.of(flux));

        BlueprintEntry entry = new BlueprintEntry(wf);
        BlueprintStatus status = new BlueprintStatus(0, 1, List.of("flux1-dev.safetensors"));

        BlueprintHardwareAdvisor.CompatibilityReport report =
                BlueprintHardwareAdvisor.evaluate(entry, status, 24L * GB, 5L * GB, null);

        assertThat(report.level()).isEqualTo(BlueprintHardwareAdvisor.CompatibilityLevel.INSUFFICIENT_RESOURCES);
        assertThat(report.badgeText()).contains("🔴");
    }
}
