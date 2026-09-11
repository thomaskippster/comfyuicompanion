package de.tki.comfymodels.service.impl;

import de.tki.comfymodels.domain.HardwareProfile;
import de.tki.comfymodels.domain.HardwareTier;
import de.tki.comfymodels.domain.VideoPresetConfig;
import de.tki.comfymodels.service.IHardwareProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;

/**
 * Enterprise-grade Spring Service zur Erkennung der System-Hardware,
 * Klassifizierung in Hardware-Tiers und Vorkonfiguration des Video Architects.
 */
@Service
public class HardwareProfileService implements IHardwareProfileService {
    private static final Logger logger = LoggerFactory.getLogger(HardwareProfileService.class);

    private static final long GIB = 1024L * 1024L * 1024L;

    private final HardwareMonitorService hardwareMonitorService;
    private volatile HardwareProfile cachedProfile;

    public HardwareProfileService() {
        this(null);
    }

    @Autowired
    public HardwareProfileService(@Autowired(required = false) HardwareMonitorService hardwareMonitorService) {
        this.hardwareMonitorService = hardwareMonitorService;
    }

    @Override
    public synchronized HardwareProfile getHardwareProfile() {
        if (cachedProfile == null) {
            cachedProfile = detectProfile();
        }
        return cachedProfile;
    }

    @Override
    public synchronized void refreshProfile() {
        this.cachedProfile = detectProfile();
    }

    @Override
    public VideoPresetConfig getRecommendedVideoConfig() {
        HardwareProfile profile = getHardwareProfile();
        HardwareTier tier = profile.tier();

        return switch (tier) {
            case CPU_ONLY -> new VideoPresetConfig(
                    "Text to Video (LTX-Video 0.9.5)",
                    tier.getRecommendedWidth(),
                    tier.getRecommendedHeight(),
                    tier.getRecommendedSteps(),
                    3.0, 127,
                    tier.getRecommendedProfileId(),
                    "CPU / Kein dedizierter GPU-VRAM erkannt. Minimale Auflösung & leichtgewichtiges LTX-Video vorkonfiguriert."
            );
            case BUDGET -> new VideoPresetConfig(
                    "Text to Video (LTX-Video 0.9.5)",
                    tier.getRecommendedWidth(),
                    tier.getRecommendedHeight(),
                    tier.getRecommendedSteps(),
                    3.0, 127,
                    tier.getRecommendedProfileId(),
                    "Budget GPU (" + profile.formattedVram() + " VRAM). VRAM-sparende Voreinstellung (640x360, LTX-Video) aktiv."
            );
            case MID_TIER -> new VideoPresetConfig(
                    "Text to Video (Wan 2.2)",
                    tier.getRecommendedWidth(),
                    tier.getRecommendedHeight(),
                    tier.getRecommendedSteps(),
                    3.0, 127,
                    tier.getRecommendedProfileId(),
                    "Mid-Tier GPU (" + profile.formattedVram() + " VRAM). Standard 480p Widescreen für Wan 2.2 / Hunyuan aktiv."
            );
            case HIGH_END -> new VideoPresetConfig(
                    "Text to Video (Wan 2.2)",
                    tier.getRecommendedWidth(),
                    tier.getRecommendedHeight(),
                    tier.getRecommendedSteps(),
                    3.0, 127,
                    tier.getRecommendedProfileId(),
                    "High-End GPU (" + profile.formattedVram() + " VRAM). 720p HD-Voreinstellung aktiv."
            );
        };
    }

    /**
     * Reine Klassifizierungsfunktion zur deterministischen Testbarkeit ohne Systemabhängigkeiten.
     */
    public static HardwareTier classifyTier(long vramBytes, boolean hasNvidia) {
        if (vramBytes <= 0 && !hasNvidia) {
            return HardwareTier.CPU_ONLY;
        }
        if (vramBytes < 12L * GIB) {
            return HardwareTier.BUDGET;
        }
        if (vramBytes < 20L * GIB) {
            return HardwareTier.MID_TIER;
        }
        return HardwareTier.HIGH_END;
    }

    private HardwareProfile detectProfile() {
        long vram = 0L;
        String gpuName = "Unbekannt";
        boolean hasNvidia = false;

        if (hardwareMonitorService != null) {
            vram = hardwareMonitorService.getVramBytes();
            gpuName = hardwareMonitorService.getGpuName();
            if (gpuName != null && !gpuName.trim().isEmpty() && !"N/A".equalsIgnoreCase(gpuName)) {
                String lower = gpuName.toLowerCase();
                hasNvidia = lower.contains("nvidia") || lower.contains("geforce") || lower.contains("rtx") || lower.contains("gtx");
            }
        }

        long sysRam = 0L;
        try {
            java.lang.management.OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
                sysRam = sunBean.getTotalPhysicalMemorySize();
            }
        } catch (Exception e) {
            logger.trace("Failed to query physical RAM: {}", e.getMessage());
        }

        int cpuCores = Runtime.getRuntime().availableProcessors();
        HardwareTier tier = classifyTier(vram, hasNvidia);

        HardwareProfile profile = new HardwareProfile(gpuName, vram, sysRam, cpuCores, hasNvidia, tier);
        logger.info("[HardwareProfileService] Detected GPU: '{}' | VRAM: {} | Tier: {}",
                profile.gpuName(), profile.formattedVram(), profile.tier());
        return profile;
    }
}
