package de.tki.comfyuicompanion.domain;

/**
 * Pre-configuration parameters for Video Architect based on
 * detected hardware capabilities.
 *
 * @param recommendedModel     the recommended model to use
 * @param defaultWidth         the default width in pixels
 * @param defaultHeight        the default height in pixels
 * @param defaultSteps         the default generation steps
 * @param defaultCfg           the default CFG scale
 * @param motionBucket         the motion bucket ID
 * @param recommendedProfileId the recommended profile ID for execution
 * @param statusDescription    the status description of the preset
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
