package de.tki.comfymodels.domain;

public enum ModelArchitecture {
    ARCH_FLUX("FLUX"),
    ARCH_SD15("SD 1.5"),
    ARCH_SDXL("SDXL"),
    ARCH_SD3("SD3"),
    ARCH_HUNYUAN("Hunyuan"),
    ARCH_WAN("Wan"),
    ARCH_LUMINA2("Lumina2"),
    ARCH_UNKNOWN("Unknown");

    private final String displayName;

    ModelArchitecture(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
