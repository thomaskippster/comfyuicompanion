package de.tki.comfymodels.domain;

/**
 * Typisierte Leistungsstufen für Hardware-Ressourcen (VRAM/GPU) zur
 * Bestimmung optimaler Video-Diffusions-Parameter.
 */
public enum HardwareTier {
    CPU_ONLY("CPU / Keine GPU", 512, 288, 15, "cpu_mode", 512L * 288L),
    BUDGET("Budget GPU (< 12 GB VRAM)", 640, 360, 20, "background_mode", 768L * 512L),
    MID_TIER("Mid-Range GPU (12 - 20 GB VRAM)", 832, 480, 20, "default", 960L * 544L),
    HIGH_END("High-End GPU (≥ 20 GB VRAM)", 1280, 720, 25, "beast_mode", 1920L * 1080L);

    private final String description;
    private final int recommendedWidth;
    private final int recommendedHeight;
    private final int recommendedSteps;
    private final String recommendedProfileId;
    private final long maxSafePixels;

    HardwareTier(String description, int recommendedWidth, int recommendedHeight,
                 int recommendedSteps, String recommendedProfileId, long maxSafePixels) {
        this.description = description;
        this.recommendedWidth = recommendedWidth;
        this.recommendedHeight = recommendedHeight;
        this.recommendedSteps = recommendedSteps;
        this.recommendedProfileId = recommendedProfileId;
        this.maxSafePixels = maxSafePixels;
    }

    public String getDescription() {
        return description;
    }

    public int getRecommendedWidth() {
        return recommendedWidth;
    }

    public int getRecommendedHeight() {
        return recommendedHeight;
    }

    public int getRecommendedSteps() {
        return recommendedSteps;
    }

    public String getRecommendedProfileId() {
        return recommendedProfileId;
    }

    public long getMaxSafePixels() {
        return maxSafePixels;
    }

    public boolean isWeakSystem() {
        return this == CPU_ONLY || this == BUDGET;
    }
}
