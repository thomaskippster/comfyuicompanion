package de.tki.comfyuicompanion.service.torch;

import java.util.Optional;

/**
 * Strategy interface for resolving the optimal PyTorch hardware acceleration backend
 * based on the host operating system, CPU architecture, and detected GPU capabilities.
 */
public interface ITorchBackendStrategy {

    /**
     * Evaluates whether this hardware acceleration backend is applicable for the given environment.
     *
     * @param osName       operating system name (lowercase)
     * @param osArch       CPU architecture name (lowercase)
     * @param gpuVendorRaw detected GPU vendor or model description
     * @param computeCap   detected NVIDIA CUDA compute capability (0.0 if not applicable)
     * @return an {@link Optional} containing the resolved {@link TorchInstallDescriptor} if supported
     */
    Optional<TorchInstallDescriptor> resolve(String osName, String osArch, String gpuVendorRaw, double computeCap);
}
