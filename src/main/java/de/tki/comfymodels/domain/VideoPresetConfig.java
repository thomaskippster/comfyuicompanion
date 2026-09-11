package de.tki.comfymodels.domain;

/**
 * Vorkonfigurations-Parameter für den Video Architect basierend auf
 * den erkannten Hardwarefähigkeiten.
 */
public record VideoPresetConfig(
        String recommendedModel,
        int defaultWidth,
        int defaultHeight,
        int defaultSteps,
        double defaultCfg,
        int motionBucket,
        String recommendedProfileId,
        String statusDescription
) {}
