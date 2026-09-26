package de.tki.comfyuicompanion.domain;

/**
 * Defines the supported model architectures within the ComfyUI Companion.
 */
public enum ModelArchitecture {
    /** FLUX model architecture. */
    ARCH_FLUX("FLUX"),
    /** Stable Diffusion 1.5 architecture. */
    ARCH_SD15("SD 1.5"),
    /** Stable Diffusion XL architecture. */
    ARCH_SDXL("SDXL"),
    /** Stable Diffusion 3 architecture. */
    ARCH_SD3("SD3"),
    /** Hunyuan model architecture. */
    ARCH_HUNYUAN("Hunyuan"),
    /** Wan model architecture. */
    ARCH_WAN("Wan"),
    /** Lumina2 model architecture. */
    ARCH_LUMINA2("Lumina2"),
    /** Unknown or unsupported model architecture. */
    ARCH_UNKNOWN("Unknown");

    private final String displayName;

    /**
     * Constructs a ModelArchitecture with its display name.
     *
     * @param displayName the human-readable display name for this architecture
     */
    ModelArchitecture(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the human-readable display name of the architecture.
     *
     * @return the display name string
     */
    public String getDisplayName() {
        return displayName;
    }
}
