package de.tki.comfyuicompanion.ui.blueprint;

import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.HardwareMonitorService;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintEntry;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintStatus;

import java.awt.Color;
import java.io.File;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates hardware compatibility (GPU VRAM capacity and available disk space)
 * for a given workflow blueprint and produces traffic-light indicators.
 */
public class BlueprintHardwareAdvisor {

    private static final long GB = 1024L * 1024L * 1024L;
    private static final long MB = 1024L * 1024L;
    private static final Pattern SIZE_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*(GB|MB|KB|B)", Pattern.CASE_INSENSITIVE);

    /**
     * Traffic-light hardware compatibility classification.
     */
    public enum CompatibilityLevel {
        /** Models fit comfortably inside dedicated GPU VRAM. */
        FITS_IN_VRAM("🟢 Fits in VRAM", new Color(34, 197, 130)),
        /** Models exceed comfortable VRAM margin and require ComfyUI weight offloading / --lowvram. */
        REQUIRES_OFFLOAD("🟡 Offload / --lowvram", new Color(245, 158, 11)),
        /** Insufficient free disk space for missing models or extreme VRAM deficit. */
        INSUFFICIENT_RESOURCES("🔴 High VRAM / Low Disk", new Color(239, 68, 68)),
        /** Cloud API or lightweight workflow requiring minimal local GPU memory. */
        CLOUD_OR_LIGHT("🟢 Lightweight / Cloud", new Color(34, 197, 130));

        private final String label;
        private final Color badgeColor;

        CompatibilityLevel(String label, Color badgeColor) {
            this.label = label;
            this.badgeColor = badgeColor;
        }

        /**
         * Returns the human-readable badge text.
         *
         * @return badge text
         */
        public String getLabel() {
            return label;
        }

        /**
         * Returns the traffic-light color associated with this compatibility level.
         *
         * @return badge color
         */
        public Color getBadgeColor() {
            return badgeColor;
        }
    }

    /**
     * Immutable hardware compatibility evaluation result for a blueprint.
     *
     * @param level              the traffic-light compatibility level
     * @param badgeText          compact badge text for blueprint cards
     * @param detailSummary      detailed explanation for tooltips and dialogs
     * @param estimatedVramBytes estimated peak VRAM required in bytes
     * @param missingDiskBytes   estimated download size of missing models in bytes
     * @param availableVramBytes detected total GPU VRAM in bytes
     * @param freeDiskBytes      available free disk space in models directory in bytes
     */
    public record CompatibilityReport(
            CompatibilityLevel level,
            String badgeText,
            String detailSummary,
            long estimatedVramBytes,
            long missingDiskBytes,
            long availableVramBytes,
            long freeDiskBytes
    ) {}

    /**
     * Evaluates hardware compatibility for a blueprint using live system metrics.
     *
     * @param entry             the blueprint entry to evaluate
     * @param status            current local model status of the blueprint
     * @param configService     configuration service for resolving models directory free space
     * @param hardwareMonitor   hardware monitor service for querying GPU VRAM (nullable)
     * @param modelSizeResolver optional function resolving formatted model size strings (nullable)
     * @return hardware compatibility report
     */
    public static CompatibilityReport evaluate(BlueprintEntry entry,
                                               BlueprintStatus status,
                                               ConfigService configService,
                                               HardwareMonitorService hardwareMonitor,
                                               Function<ModelInfo, String> modelSizeResolver) {
        long vramBytes = hardwareMonitor != null ? hardwareMonitor.getVramBytes() : HardwareMonitorService.getLastKnownVramBytes();
        long freeDisk = resolveFreeDiskBytes(configService);
        return evaluate(entry, status, vramBytes, freeDisk, modelSizeResolver);
    }

    /**
     * Evaluates hardware compatibility for a blueprint against explicit VRAM and disk capacities.
     *
     * @param entry             the blueprint entry to evaluate
     * @param status            current local model status of the blueprint
     * @param availableVramBytes total GPU VRAM in bytes (0 if unknown)
     * @param freeDiskBytes     available disk space in bytes (Long.MAX_VALUE if unknown)
     * @param modelSizeResolver optional function resolving formatted model size strings
     * @return hardware compatibility report
     */
    public static CompatibilityReport evaluate(BlueprintEntry entry,
                                               BlueprintStatus status,
                                               long availableVramBytes,
                                               long freeDiskBytes,
                                               Function<ModelInfo, String> modelSizeResolver) {
        if (entry == null || entry.requiredModels == null || entry.requiredModels.isEmpty()) {
            return new CompatibilityReport(
                    CompatibilityLevel.CLOUD_OR_LIGHT,
                    CompatibilityLevel.CLOUD_OR_LIGHT.getLabel(),
                    "Lightweight or Cloud API workflow (minimal local VRAM required).",
                    0L, 0L, availableVramBytes, freeDiskBytes
            );
        }

        long totalModelBytes = 0L;
        long missingBytes = 0L;

        for (ModelInfo info : entry.requiredModels) {
            if (info == null || info.getName() == null || info.getName().isBlank()) continue;
            long modelBytes = estimateModelBytes(info, modelSizeResolver);
            totalModelBytes += modelBytes;
            if (status != null && status.missingNames != null && status.missingNames.contains(info.getName())) {
                missingBytes += modelBytes;
            }
        }

        // Add ~1.5 GB activation/latent overhead for inference
        long estimatedVram = totalModelBytes > 0 ? totalModelBytes + (long) (1.5 * GB) : 0L;

        // Check disk space first when models are missing
        if (missingBytes > 0 && freeDiskBytes > 0 && freeDiskBytes < missingBytes) {
            String summary = String.format(Locale.US,
                    "Insufficient disk space: Requires ~%.1f GB for missing models, but only %.1f GB is free.",
                    missingBytes / (double) GB, freeDiskBytes / (double) GB);
            return new CompatibilityReport(
                    CompatibilityLevel.INSUFFICIENT_RESOURCES,
                    "🔴 Insufficient Disk",
                    summary,
                    estimatedVram, missingBytes, availableVramBytes, freeDiskBytes
            );
        }

        // If GPU VRAM is unknown (0), fall back to heuristic threshold (<= 10 GB fits, <= 18 GB offload, > 18 GB high VRAM)
        long effectiveVram = availableVramBytes > 0 ? availableVramBytes : 12L * GB;
        String gpuDesc = availableVramBytes > 0
                ? String.format(Locale.US, "%.1f GB GPU VRAM", availableVramBytes / (double) GB)
                : "standard GPU VRAM";

        if (estimatedVram <= (long) (effectiveVram * 0.90)) {
            String summary = String.format(Locale.US,
                    "Fits in VRAM: Estimated ~%.1f GB required on %s.",
                    estimatedVram / (double) GB, gpuDesc);
            return new CompatibilityReport(
                    CompatibilityLevel.FITS_IN_VRAM,
                    CompatibilityLevel.FITS_IN_VRAM.getLabel(),
                    summary,
                    estimatedVram, missingBytes, availableVramBytes, freeDiskBytes
            );
        } else if (estimatedVram <= (long) (effectiveVram * 1.85)) {
            String summary = String.format(Locale.US,
                    "Requires --lowvram / RAM offloading: Estimated ~%.1f GB required on %s.",
                    estimatedVram / (double) GB, gpuDesc);
            return new CompatibilityReport(
                    CompatibilityLevel.REQUIRES_OFFLOAD,
                    CompatibilityLevel.REQUIRES_OFFLOAD.getLabel(),
                    summary,
                    estimatedVram, missingBytes, availableVramBytes, freeDiskBytes
            );
        } else {
            String summary = String.format(Locale.US,
                    "Insufficient VRAM: Estimated ~%.1f GB required on %s (heavy swapping likely).",
                    estimatedVram / (double) GB, gpuDesc);
            return new CompatibilityReport(
                    CompatibilityLevel.INSUFFICIENT_RESOURCES,
                    CompatibilityLevel.INSUFFICIENT_RESOURCES.getLabel(),
                    summary,
                    estimatedVram, missingBytes, availableVramBytes, freeDiskBytes
            );
        }
    }

    /**
     * Estimates the byte size of a model using its explicit size metadata, resolver callback, or filename heuristics.
     *
     * @param info              model metadata
     * @param modelSizeResolver optional resolver callback
     * @return estimated size in bytes
     */
    public static long estimateModelBytes(ModelInfo info, Function<ModelInfo, String> modelSizeResolver) {
        if (info == null) return 0L;

        String rawSize = info.getSize();
        if ((rawSize == null || rawSize.isBlank() || "Unknown".equalsIgnoreCase(rawSize)) && modelSizeResolver != null) {
            try {
                rawSize = modelSizeResolver.apply(info);
            } catch (Exception ignored) {}
        }

        long parsed = parseFormattedSize(rawSize);
        if (parsed > 0) {
            return parsed;
        }

        String name = info.getName() != null ? info.getName().toLowerCase(Locale.ROOT) : "";
        String type = info.getType() != null ? info.getType().toLowerCase(Locale.ROOT) : "";

        if (name.contains("flux1-dev") || name.contains("flux1-schnell")) {
            return name.contains("fp8") || name.contains("gguf") || name.contains("q4") || name.contains("q8")
                    ? (long) (11.5 * GB) : (long) (22.0 * GB);
        }
        if (name.contains("wan2.1") || name.contains("hunyuan_video") || name.contains("mochi") || name.contains("cosmos")) {
            return name.contains("14b") ? (long) (14.5 * GB) : (long) (9.5 * GB);
        }
        if (name.contains("ltx") || name.contains("svd") || name.contains("qwen")) {
            return (long) (8.5 * GB);
        }
        if (name.contains("t5xxl")) {
            return name.contains("fp8") ? (long) (4.9 * GB) : (long) (9.5 * GB);
        }
        if (name.contains("sdxl") || name.contains("sd3") || name.contains("pony") || name.contains("juggernaut")) {
            return (long) (6.5 * GB);
        }
        if (type.contains("vae") || name.contains("vae") || name.equals("ae.safetensors")) {
            return 335L * MB;
        }
        if (type.contains("lora") || name.contains("lora")) {
            return 220L * MB;
        }
        if (type.contains("clip") || type.contains("text_encoder") || name.contains("clip")) {
            return 600L * MB;
        }
        if (type.contains("controlnet") || name.contains("controlnet")) {
            return (long) (1.4 * GB);
        }
        if (type.contains("upscale")) {
            return 67L * MB;
        }
        return (long) (4.2 * GB);
    }

    /**
     * Parses human-readable size strings such as {@code "6.46GB"} or {@code "335MB"} into bytes.
     *
     * @param formatted formatted size string
     * @return byte count, or {@code 0L} if unparseable
     */
    public static long parseFormattedSize(String formatted) {
        if (formatted == null || formatted.isBlank() || "Unknown".equalsIgnoreCase(formatted)) {
            return 0L;
        }
        Matcher m = SIZE_PATTERN.matcher(formatted.trim());
        if (m.find()) {
            try {
                double val = Double.parseDouble(m.group(1));
                String unit = m.group(2).toUpperCase(Locale.ROOT);
                return switch (unit) {
                    case "GB" -> (long) (val * GB);
                    case "MB" -> (long) (val * MB);
                    case "KB" -> (long) (val * 1024L);
                    default -> (long) val;
                };
            } catch (NumberFormatException ignored) {}
        }
        return 0L;
    }

    private static long resolveFreeDiskBytes(ConfigService configService) {
        if (configService == null) return Long.MAX_VALUE;
        try {
            String modelsPath = configService.getModelsPath();
            File f = (modelsPath != null && !modelsPath.isBlank())
                    ? new File(modelsPath)
                    : new File(System.getProperty("user.home"));
            while (f != null && !f.exists()) {
                f = f.getParentFile();
            }
            if (f != null && f.exists()) {
                return f.getUsableSpace();
            }
        } catch (Exception ignored) {}
        return Long.MAX_VALUE;
    }
}
