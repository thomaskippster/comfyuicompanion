package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.domain.HardwareProfile;
import de.tki.comfyuicompanion.domain.HardwareTier;
import de.tki.comfyuicompanion.domain.VideoPresetConfig;
import de.tki.comfyuicompanion.service.IHardwareProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;

/**
 * Enterprise-grade Spring Service for detecting system hardware,
 * classifying hardware performance tiers, and configuring Video Architect defaults.
 */
@Service
/**
 * Represents the hardware profile service class.
 */
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
        if (cachedProfile == null || (cachedProfile.tier() == HardwareTier.CPU_ONLY && hardwareMonitorService != null && hardwareMonitorService.getVramBytes() > 0)) {
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
                    "CPU / No dedicated GPU VRAM detected. Minimal resolution & lightweight LTX-Video preconfigured."
            );
            case BUDGET -> new VideoPresetConfig(
                    "Text to Video (LTX-Video 0.9.5)",
                    tier.getRecommendedWidth(),
                    tier.getRecommendedHeight(),
                    tier.getRecommendedSteps(),
                    3.0, 127,
                    tier.getRecommendedProfileId(),
                    "Budget GPU (" + profile.formattedVram() + " VRAM). Low-VRAM preset (640x360, LTX-Video) active."
            );
            case MID_TIER -> new VideoPresetConfig(
                    "Text to Video (Wan 2.2)",
                    tier.getRecommendedWidth(),
                    tier.getRecommendedHeight(),
                    tier.getRecommendedSteps(),
                    3.0, 127,
                    tier.getRecommendedProfileId(),
                    "Mid-Tier GPU (" + profile.formattedVram() + " VRAM). Standard 480p widescreen for Wan 2.2 / Hunyuan active."
            );
            case HIGH_END -> new VideoPresetConfig(
                    "Text to Video (Wan 2.2)",
                    tier.getRecommendedWidth(),
                    tier.getRecommendedHeight(),
                    tier.getRecommendedSteps(),
                    3.0, 127,
                    tier.getRecommendedProfileId(),
                    "High-End GPU (" + profile.formattedVram() + " VRAM). 720p HD preset active."
            );
        };
    }

    /**
     * Pure classification function designed for deterministic testability without system dependencies.
     */
    public static HardwareTier classifyTier(long vramBytes, boolean hasNvidia) {
        if (vramBytes <= 0 && !hasNvidia) {
            return HardwareTier.CPU_ONLY;
        }
        if (vramBytes < 12L * GIB) {
            return HardwareTier.BUDGET;
        }
        if (vramBytes < 15L * GIB) {
            return HardwareTier.MID_TIER;
        }
        return HardwareTier.HIGH_END;
    }

    private HardwareProfile detectProfile() {
        long vram = 0L;
        String gpuName = "Unknown";
        boolean hasNvidia = false;

        if (hardwareMonitorService != null) {
            vram = hardwareMonitorService.getVramBytes();
            gpuName = hardwareMonitorService.getGpuName();
            if (gpuName != null && !gpuName.trim().isEmpty() && !"N/A".equalsIgnoreCase(gpuName)) {
                String lower = gpuName.toLowerCase();
                hasNvidia = lower.contains("nvidia") || lower.contains("geforce") || lower.contains("rtx") || lower.contains("gtx");
            }
        }

        if (vram <= 0) {
            try {
                ProcessBuilder pb = new ProcessBuilder("nvidia-smi", "--query-gpu=name,memory.total", "--format=csv,noheader,nounits");
                Process p = pb.start();
                try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line = r.readLine();
                    if (line != null && line.contains(",")) {
                        String[] parts = line.split(",");
                        gpuName = parts[0].trim();
                        vram = Long.parseLong(parts[1].trim()) * 1024L * 1024L;
                        hasNvidia = true;
                    }
                }
                p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception ignored) {
                logger.trace("Direct nvidia-smi fallback query bypassed: {}", ignored.getMessage());
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
