package de.tki.comfyuicompanion.domain;

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
