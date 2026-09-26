package de.tki.comfyuicompanion.domain;

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

    /**
     * Constructs a HardwareTier with associated recommended parameters.
     *
     * @param description          the description of the tier
     * @param recommendedWidth     the recommended image width
     * @param recommendedHeight    the recommended image height
     * @param recommendedSteps     the recommended generation steps
     * @param recommendedProfileId the recommended execution profile ID
     * @param maxSafePixels        the maximum safe resolution in pixels
     */
    HardwareTier(String description, int recommendedWidth, int recommendedHeight,
                 int recommendedSteps, String recommendedProfileId, long maxSafePixels) {
        this.description = description;
        this.recommendedWidth = recommendedWidth;
        this.recommendedHeight = recommendedHeight;
        this.recommendedSteps = recommendedSteps;
        this.recommendedProfileId = recommendedProfileId;
        this.maxSafePixels = maxSafePixels;
    }

    /** @return the description of this tier */
    public String getDescription() {
        return description;
    }

    /** @return the recommended width */
    public int getRecommendedWidth() {
        return recommendedWidth;
    }

    /** @return the recommended height */
    public int getRecommendedHeight() {
        return recommendedHeight;
    }

    /** @return the recommended steps */
    public int getRecommendedSteps() {
        return recommendedSteps;
    }

    /** @return the recommended profile ID */
    public String getRecommendedProfileId() {
        return recommendedProfileId;
    }

    /** @return the maximum safe pixels */
    public long getMaxSafePixels() {
        return maxSafePixels;
    }

    /** @return true if this tier represents a weak system */
    public boolean isWeakSystem() {
        return this == CPU_ONLY || this == BUDGET;
    }
}
