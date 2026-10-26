package de.tki.comfyuicompanion.service;

import de.tki.comfyuicompanion.domain.HardwareProfile;
import de.tki.comfyuicompanion.domain.VideoPresetConfig;

/**
 * Service interface for hardware detection, performance tier classification,
 * and generating optimal Video Architect configurations.
 */
public interface IHardwareProfileService {

    /**
     * Detects or retrieves the cached hardware profile of the current system.
     *
     * @return the hardware profile
     */
    HardwareProfile getHardwareProfile();

    /**
     * Returns the recommended Video Architect preset configuration for the detected system.
     *
     * @return the recommended preset configuration
     */
    VideoPresetConfig getRecommendedVideoConfig();

    /**
     * Forces a hardware re-detection (e.g. after GPU swap or driver reload).
     */
    void refreshProfile();
}
