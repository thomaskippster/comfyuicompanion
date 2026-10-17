package de.tki.comfyuicompanion.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Analyzes local model storage to identify duplicate model files across folders
 * (using fast file-size pre-filtering and cached SHA-256 hashes) as well as
 * "cold" models unused for more than a configurable number of days.
 */
@Service
public class ModelStorageCleanerService {
    private static final Logger logger = LoggerFactory.getLogger(ModelStorageCleanerService.class);
    private static final Set<String> MODEL_EXTENSIONS = Set.of(
            ".safetensors", ".sft", ".ckpt", ".gguf", ".pth", ".pt", ".bin", ".onnx"
    );

    /**
     * Classification of a storage cleanup candidate.
     */
    public enum CandidateType {
        /** Redundant copy of a model file present in multiple folders. */
        DUPLICATE("Duplicate Copy"),
        /** Model file not accessed or modified within the cold threshold period. */
        COLD_MODEL("Cold Model (>30d unused)");

        private final String displayName;

        CandidateType(String displayName) {
            this.displayName = displayName;
        }

        /**
         * Returns the human-readable category label.
         *
         * @return display name
         */
        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Represents a single model file identified for archiving or duplicate cleanup.
     *
     * @param type         cleanup classification ({@link CandidateType#DUPLICATE} or {@link CandidateType#COLD_MODEL})
     * @param fileName     model file name
     * @param relativeFolder folder relative to models root (e.g. {@code "checkpoints"} or {@code "unet"})
     * @param fullPath     absolute path on disk
     * @param sizeBytes    file size in bytes
     * @param daysSinceUse days since last access or modification
     * @param reason       explanation of why this file was flagged (e.g. primary copy location)
     */
    public record CleanupCandidate(
            CandidateType type,
            String fileName,
            String relativeFolder,
            Path fullPath,
            long sizeBytes,
            long daysSinceUse,
            String reason
    ) {}

    /**
     * Aggregated scan report for local model storage cleanup.
     *
     * @param candidates            all identified duplicate and cold model candidates
     * @param duplicateCount        number of redundant duplicate files
     * @param coldModelCount        number of cold models unused beyond the threshold
     * @param reclaimableBytesTotal total bytes reclaimable if all candidates are archived/cleaned
     */
    public record StorageScanReport(
            List<CleanupCandidate> candidates,
            int duplicateCount,
            int coldModelCount,
            long reclaimableBytesTotal
    ) {}

    /**
     * Scans the given models root directory for duplicate models and cold models unused for {@code coldDaysThreshold} days.
     * Uses fast byte-size pre-filtering so SHA-256 hashing is only invoked when two files have identical byte sizes.
     *
     * @param modelsRootPath    root models directory path
     * @param coldDaysThreshold minimum days since last access/modification to classify a model as cold (e.g. 30)
     * @param hashRegistry      optional hash registry for verifying same-size duplicate files
     * @param activeUsedNames   optional set of lowercase model filenames actively used in recent blueprints
     * @return storage scan report
     */
    public StorageScanReport scanModelsDirectory(Path modelsRootPath,
                                                 int coldDaysThreshold,
                                                 ModelHashRegistry hashRegistry,
                                                 Set<String> activeUsedNames) {
        List<CleanupCandidate> candidates = new ArrayList<>();
        if (modelsRootPath == null || !Files.exists(modelsRootPath) || !Files.isDirectory(modelsRootPath)) {
            return new StorageScanReport(candidates, 0, 0, 0L);
        }

        Path normalizedRoot = modelsRootPath.toAbsolutePath().normalize();
        List<Path> modelFiles = new ArrayList<>();

        try (Stream<Path> walk = Files.walk(normalizedRoot)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> !isExcludedPath(normalizedRoot, p))
                .filter(this::isSupportedModelFile)
                .forEach(modelFiles::add);
        } catch (IOException e) {
            logger.warn("Error walking models directory {}: {}", normalizedRoot, e.getMessage());
            return new StorageScanReport(candidates, 0, 0, 0L);
        }

        // Group by byte size for O(1) duplicate pre-filtering
        Map<Long, List<Path>> bySize = new HashMap<>();
        Map<Path, Long> fileSizes = new HashMap<>();
        Map<Path, Long> daysOldMap = new HashMap<>();
        long nowMillis = Instant.now().toEpochMilli();

        for (Path p : modelFiles) {
            try {
                BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                long size = attrs.size();
                if (size <= 0) continue;
                fileSizes.put(p, size);
                bySize.computeIfAbsent(size, k -> new ArrayList<>()).add(p);

                long lastTouch = Math.max(attrs.lastModifiedTime().toMillis(), attrs.lastAccessTime().toMillis());
                long days = Math.max(0L, ChronoUnit.DAYS.between(Instant.ofEpochMilli(lastTouch), Instant.ofEpochMilli(nowMillis)));
                daysOldMap.put(p, days);
            } catch (IOException ignored) {}
        }

        Set<Path> flaggedAsDuplicate = new java.util.HashSet<>();
        int dupCount = 0;
        int coldCount = 0;
        long reclaimable = 0L;

        // 1. Identify duplicate copies among same-size files
        for (Map.Entry<Long, List<Path>> entry : bySize.entrySet()) {
            List<Path> sameSizeFiles = entry.getValue();
            if (sameSizeFiles.size() < 2) continue;

            Map<String, List<Path>> byIdentity = new HashMap<>();
            for (Path p : sameSizeFiles) {
                String key;
                if (hashRegistry != null && entry.getKey() <= 256L * 1024 * 1024) {
                    String h = hashRegistry.getOrCalculateHash(p.toFile());
                    key = (h != null && !h.isBlank()) ? "sha256:" + h : "name_size:" + p.getFileName().toString().toLowerCase(Locale.ROOT) + ":" + entry.getKey();
                } else {
                    key = "name_size:" + p.getFileName().toString().toLowerCase(Locale.ROOT) + ":" + entry.getKey();
                }
                byIdentity.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
            }

            for (List<Path> dupGroup : byIdentity.values()) {
                if (dupGroup.size() < 2) continue;
                Path primary = dupGroup.get(0);
                String primaryRel = normalizedRoot.relativize(primary).toString().replace("\\", "/");
                for (int i = 1; i < dupGroup.size(); i++) {
                    Path redundant = dupGroup.get(i);
                    flaggedAsDuplicate.add(redundant);
                    long sz = fileSizes.getOrDefault(redundant, 0L);
                    long days = daysOldMap.getOrDefault(redundant, 0L);
                    String relFolder = resolveRelativeFolder(normalizedRoot, redundant);
                    candidates.add(new CleanupCandidate(
                            CandidateType.DUPLICATE,
                            redundant.getFileName().toString(),
                            relFolder,
                            redundant,
                            sz,
                            days,
                            "Duplicate of " + primaryRel
                    ));
                    dupCount++;
                    reclaimable += sz;
                }
            }
        }

        // 2. Identify cold models (> coldDaysThreshold days old and not already flagged as duplicate)
        for (Path p : modelFiles) {
            if (flaggedAsDuplicate.contains(p)) continue;
            long days = daysOldMap.getOrDefault(p, 0L);
            String lowerName = p.getFileName().toString().toLowerCase(Locale.ROOT);
            boolean activelyUsed = activeUsedNames != null && activeUsedNames.contains(lowerName);

            if (days >= coldDaysThreshold && !activelyUsed) {
                long sz = fileSizes.getOrDefault(p, 0L);
                String relFolder = resolveRelativeFolder(normalizedRoot, p);
                candidates.add(new CleanupCandidate(
                        CandidateType.COLD_MODEL,
                        p.getFileName().toString(),
                        relFolder,
                        p,
                        sz,
                        days,
                        "Unused for " + days + " days"
                ));
                coldCount++;
                reclaimable += sz;
            }
        }

        return new StorageScanReport(candidates, dupCount, coldCount, reclaimable);
    }

    private boolean isExcludedPath(Path root, Path file) {
        try {
            Path rel = root.relativize(file);
            for (Path part : rel) {
                String s = part.toString().toLowerCase(Locale.ROOT);
                if (s.startsWith(".") || s.equals("venv") || s.equals("archive") || s.equals("archived")) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean isSupportedModelFile(Path p) {
        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String ext : MODEL_EXTENSIONS) {
            if (name.endsWith(ext)) return true;
        }
        return false;
    }

    private String resolveRelativeFolder(Path root, Path file) {
        Path rel = root.relativize(file);
        return rel.getParent() != null ? rel.getParent().toString().replace("\\", "/") : "root";
    }

    /**
     * Formats a byte count into a human-readable GB/MB string.
     *
     * @param bytes byte count
     * @return formatted size string
     */
    public static String formatSize(long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 1024.0) {
            return String.format(Locale.US, "%.2f GB", mb / 1024.0);
        }
        return String.format(Locale.US, "%.1f MB", mb);
    }
}
