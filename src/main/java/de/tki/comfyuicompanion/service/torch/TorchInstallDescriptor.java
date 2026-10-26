package de.tki.comfyuicompanion.service.torch;

import java.util.List;
import java.util.Objects;

/**
 * Immutable Java 21 record describing a resolved PyTorch hardware acceleration backend
 * and the corresponding pip installation parameters.
 *
 * @param backendId   short identifier (e.g. "cu128", "cu124", "rocm6.2", "mps", "directml")
 * @param displayName human-readable backend description for progress logging
 * @param packages    pip packages to install (e.g. torch, torchvision, torchaudio, torch-directml)
 * @param indexUrl    optional custom PyTorch wheel index URL (null for default PyPI)
 */
public record TorchInstallDescriptor(
        String backendId,
        String displayName,
        List<String> packages,
        String indexUrl
) {
    /**
     * Compact constructor enforcing non-null invariants and defensive list copying.
     */
    public TorchInstallDescriptor {
        Objects.requireNonNull(backendId, "backendId must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        packages = (packages == null || packages.isEmpty())
                ? List.of("torch", "torchvision", "torchaudio")
                : List.copyOf(packages);
    }

    /**
     * Creates a new fluent builder for {@link TorchInstallDescriptor}.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for constructing {@link TorchInstallDescriptor} instances.
     */
    public static final class Builder {
        private String backendId;
        private String displayName;
        private List<String> packages = List.of("torch", "torchvision", "torchaudio");
        private String indexUrl;

        private Builder() {}

        /**
         * Sets the backend identifier.
         *
         * @param backendId backend id
         * @return this builder
         */
        public Builder backendId(String backendId) {
            this.backendId = backendId;
            return this;
        }

        /**
         * Sets the human-readable display name.
         *
         * @param displayName display name
         * @return this builder
         */
        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        /**
         * Sets the list of pip packages to install.
         *
         * @param packages pip package names
         * @return this builder
         */
        public Builder packages(List<String> packages) {
            this.packages = packages;
            return this;
        }

        /**
         * Sets the custom PyTorch wheel index URL.
         *
         * @param indexUrl wheel repository URL
         * @return this builder
         */
        public Builder indexUrl(String indexUrl) {
            this.indexUrl = indexUrl;
            return this;
        }

        /**
         * Builds the immutable {@link TorchInstallDescriptor}.
         *
         * @return constructed descriptor
         */
        public TorchInstallDescriptor build() {
            return new TorchInstallDescriptor(backendId, displayName, packages, indexUrl);
        }
    }
}
