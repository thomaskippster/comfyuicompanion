package de.tki.comfyuicompanion.domain;

/**
 * Kapselt erkannte Hardwarespezifikationen (GPU, VRAM, RAM, Kerne)
 * und die zugehörige Leistungseinstufung (HardwareTier).
 *
 * @param gpuName        the name of the GPU
 * @param vramBytes      the amount of VRAM in bytes
 * @param systemRamBytes the amount of system RAM in bytes
 * @param cpuCores       the number of CPU cores
 * @param hasNvidia      true if an NVIDIA GPU is detected
 * @param tier           the classified hardware performance tier
 */
public record HardwareProfile(
        String gpuName,
        long vramBytes,
        long systemRamBytes,
        int cpuCores,
        boolean hasNvidia,
        HardwareTier tier
) {
    /** @return the formatted VRAM string */
    public String formattedVram() {
        if (vramBytes <= 0) return "N/A (CPU)";
        double gib = vramBytes / (1024.0 * 1024.0 * 1024.0);
        return String.format(java.util.Locale.US, "%.1f GiB", gib);
    }

    /** @return the formatted RAM string */
    public String formattedRam() {
        if (systemRamBytes <= 0) return "Unbekannt";
        double gib = systemRamBytes / (1024.0 * 1024.0 * 1024.0);
        return String.format(java.util.Locale.US, "%.1f GiB", gib);
    }

    /** @return true if the system is considered weak */
    public boolean isWeakSystem() {
        return tier != null && tier.isWeakSystem();
    }
}
