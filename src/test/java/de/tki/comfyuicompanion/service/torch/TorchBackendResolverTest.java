package de.tki.comfyuicompanion.service.torch;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests verifying cross-vendor GPU acceleration resolution across NVIDIA CUDA,
 * AMD ROCm, Apple Silicon MPS, and Windows DirectML strategies.
 */
class TorchBackendResolverTest {

    private final TorchBackendResolver resolver = new TorchBackendResolver();

    @Test
    void shouldSelectCuda128ForBlackwellRtx50Series() {
        Optional<TorchInstallDescriptor> desc = resolver.resolveOptimalBackend(
                "Windows 11", "amd64", "NVIDIA GeForce RTX 5080", 12.0
        );
        assertThat(desc).isPresent();
        assertThat(desc.get().backendId()).isEqualTo("cu128");
        assertThat(desc.get().indexUrl()).isEqualTo("https://download.pytorch.org/whl/cu128");
    }

    @Test
    void shouldSelectCuda124ForAdaLovelaceRtx40Series() {
        Optional<TorchInstallDescriptor> desc = resolver.resolveOptimalBackend(
                "Windows 11", "amd64", "NVIDIA GeForce RTX 4090", 8.9
        );
        assertThat(desc).isPresent();
        assertThat(desc.get().backendId()).isEqualTo("cu124");
    }

    @Test
    void shouldSelectRocmForAmdOnLinux() {
        Optional<TorchInstallDescriptor> desc = resolver.resolveOptimalBackend(
                "Linux", "amd64", "AMD Radeon RX 7900 XTX", 0.0
        );
        assertThat(desc).isPresent();
        assertThat(desc.get().backendId()).isEqualTo("rocm6.2");
        assertThat(desc.get().indexUrl()).isEqualTo("https://download.pytorch.org/whl/rocm6.2");
    }

    @Test
    void shouldSelectMpsForAppleSiliconMac() {
        Optional<TorchInstallDescriptor> desc = resolver.resolveOptimalBackend(
                "Mac OS X", "aarch64", "Apple M3 Max", 0.0
        );
        assertThat(desc).isPresent();
        assertThat(desc.get().backendId()).isEqualTo("mps");
        assertThat(desc.get().indexUrl()).isNull();
    }

    @Test
    void shouldSelectDirectMlForAmdOrIntelArcOnWindows() {
        Optional<TorchInstallDescriptor> desc = resolver.resolveOptimalBackend(
                "Windows 11", "amd64", "AMD Radeon RX 7800 XT", 0.0
        );
        assertThat(desc).isPresent();
        assertThat(desc.get().backendId()).isEqualTo("directml");
        assertThat(desc.get().packages()).contains("torch-directml");
    }
}
