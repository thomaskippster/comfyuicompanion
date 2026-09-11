package de.tki.comfymodels.domain;

/**
 * Kapselt erkannte Hardwarespezifikationen (GPU, VRAM, RAM, Kerne)
 * und die zugehörige Leistungseinstufung (HardwareTier).
 */
public record HardwareProfile(
        String gpuName,
        long vramBytes,
        long systemRamBytes,
        int cpuCores,
        boolean hasNvidia,
        HardwareTier tier
) {
    public String formattedVram() {
        if (vramBytes <= 0) return "N/A (CPU)";
        double gib = vramBytes / (1024.0 * 1024.0 * 1024.0);
        return String.format(java.util.Locale.US, "%.1f GiB", gib);
    }

    public String formattedRam() {
        if (systemRamBytes <= 0) return "Unbekannt";
        double gib = systemRamBytes / (1024.0 * 1024.0 * 1024.0);
        return String.format(java.util.Locale.US, "%.1f GiB", gib);
    }

    public boolean isWeakSystem() {
        return tier != null && tier.isWeakSystem();
    }
}
