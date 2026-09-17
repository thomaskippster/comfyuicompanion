package de.tki.comfyuicompanion.service;

import de.tki.comfyuicompanion.domain.HardwareProfile;
import de.tki.comfyuicompanion.domain.VideoPresetConfig;

/**
 * Service-Schnittstelle zur System-Hardware-Erkennung, Profil-Einstufung
 * und Generierung optimaler Video-Architect-Konfigurationen.
 */
public interface IHardwareProfileService {

    /**
     * Ermittelt oder liefert das gecachte Hardwareprofil des aktuellen Systems.
     */
    HardwareProfile getHardwareProfile();

    /**
     * Liefert die für das erkannte System empfohlene Video-Architect-Konfiguration.
     */
    VideoPresetConfig getRecommendedVideoConfig();

    /**
     * Erzwingt eine erneute Hardware-Erkennung (z. B. nach GPU-Wechsel oder Treiber-Reload).
     */
    void refreshProfile();
}
