package de.tki.comfyuicompanion.service.torch;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Pipeline resolver that evaluates registered {@link ITorchBackendStrategy} implementations
 * in priority order to determine the optimal PyTorch build across NVIDIA CUDA, AMD ROCm,
 * Apple Silicon MPS, and Windows DirectML hardware.
 */
@Component
public class TorchBackendResolver {

    private final List<ITorchBackendStrategy> strategies;

    /**
     * Constructs a new resolver with the standard cross-vendor hardware strategies.
     */
    public TorchBackendResolver() {
        this.strategies = List.of(
                TorchBackendResolver::resolveNvidiaCuda,
                TorchBackendResolver::resolveAmdRocmLinux,
                TorchBackendResolver::resolveAppleSiliconMps,
                TorchBackendResolver::resolveWindowsDirectMl
        );
    }

    /**
     * Resolves the optimal {@link TorchInstallDescriptor} for the provided hardware telemetry.
     *
     * @param osName       operating system name
     * @param osArch       CPU architecture
     * @param gpuVendorRaw detected GPU name/vendor string
     * @param computeCap   NVIDIA CUDA compute capability (0.0 if none)
     * @return an {@link Optional} containing the matched descriptor
     */
    public Optional<TorchInstallDescriptor> resolveOptimalBackend(
            String osName, String osArch, String gpuVendorRaw, double computeCap) {
        String normOs = osName != null ? osName.toLowerCase(Locale.ROOT) : "";
        String normArch = osArch != null ? osArch.toLowerCase(Locale.ROOT) : "";
        String normVendor = gpuVendorRaw != null ? gpuVendorRaw.toLowerCase(Locale.ROOT) : "";

        for (ITorchBackendStrategy strategy : strategies) {
            Optional<TorchInstallDescriptor> match = strategy.resolve(normOs, normArch, normVendor, computeCap);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    private static Optional<TorchInstallDescriptor> resolveNvidiaCuda(
            String os, String arch, String vendor, double computeCap) {
        if (computeCap < 5.0 && !vendor.contains("nvidia")) {
            return Optional.empty();
        }
        if (computeCap > 0.0 && computeCap < 5.0) {
            return Optional.empty();
        }
        String cudaVer;
        String label;
        if (computeCap >= 10.0) {
            cudaVer = "cu128";
            label = "CUDA 12.8 (Blackwell RTX 50-series / Modern)";
        } else if (computeCap >= 8.9 || computeCap == 0.0) {
            cudaVer = "cu124";
            label = "CUDA 12.4 (Ada / Hopper Modern)";
        } else {
            cudaVer = "cu121";
            label = "CUDA 12.1 (Standard)";
        }
        return Optional.of(TorchInstallDescriptor.builder()
                .backendId(cudaVer)
                .displayName(label)
                .packages(List.of("torch", "torchvision", "torchaudio"))
                .indexUrl("https://download.pytorch.org/whl/" + cudaVer)
                .build());
    }

    private static Optional<TorchInstallDescriptor> resolveAmdRocmLinux(
            String os, String arch, String vendor, double computeCap) {
        boolean isLinux = os.contains("nux") || os.contains("nix");
        boolean isAmd = vendor.contains("amd") || vendor.contains("radeon") || vendor.contains("advanced micro devices");
        if (isLinux && isAmd) {
            return Optional.of(TorchInstallDescriptor.builder()
                    .backendId("rocm6.2")
                    .displayName("AMD ROCm 6.2 (Linux Native)")
                    .packages(List.of("torch", "torchvision", "torchaudio"))
                    .indexUrl("https://download.pytorch.org/whl/rocm6.2")
                    .build());
        }
        return Optional.empty();
    }

    private static Optional<TorchInstallDescriptor> resolveAppleSiliconMps(
            String os, String arch, String vendor, double computeCap) {
        boolean isMac = os.contains("mac") || os.contains("darwin");
        boolean isArm = arch.contains("aarch64") || arch.contains("arm64");
        if (isMac && (isArm || vendor.contains("apple"))) {
            return Optional.of(TorchInstallDescriptor.builder()
                    .backendId("mps")
                    .displayName("Apple Metal Performance Shaders (MPS)")
                    .packages(List.of("torch", "torchvision", "torchaudio"))
                    .indexUrl(null)
                    .build());
        }
        return Optional.empty();
    }

    private static Optional<TorchInstallDescriptor> resolveWindowsDirectMl(
            String os, String arch, String vendor, double computeCap) {
        boolean isWindows = os.contains("win");
        boolean isAmdOrIntel = vendor.contains("amd") || vendor.contains("radeon")
                || vendor.contains("intel") || vendor.contains("arc");
        if (isWindows && isAmdOrIntel && computeCap <= 0.0) {
            return Optional.of(TorchInstallDescriptor.builder()
                    .backendId("directml")
                    .displayName("Windows DirectML (AMD Radeon / Intel Arc)")
                    .packages(List.of("torch", "torchvision", "torchaudio", "torch-directml"))
                    .indexUrl(null)
                    .build());
        }
        return Optional.empty();
    }
}
