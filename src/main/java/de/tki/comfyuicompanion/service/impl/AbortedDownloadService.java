package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.domain.AbortedDownloadItem;
import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.domain.ModelFolder;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.IAbortedDownloadService;
import de.tki.comfyuicompanion.service.ICivitaiHubService;
import de.tki.comfyuicompanion.service.IDownloadManager;
import de.tki.comfyuicompanion.service.IHuggingFaceHubService;
import de.tki.comfyuicompanion.service.SafePathValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Service implementation managing aborted, interrupted, and partial model downloads.
 * Detects temporary download fragments (.cmfd, .cmfd_merge, .part*, .tmp, .crdownload),
 * reclaims disk space safely, and facilitates download resumption.
 */
@Service
public class AbortedDownloadService implements IAbortedDownloadService {

    private static final Logger log = LoggerFactory.getLogger(AbortedDownloadService.class);
    private static final Pattern PART_REGEX = Pattern.compile("^(.*?)\\.part(\\d+)$");
    private static final Pattern CMFD_REGEX = Pattern.compile("^(.*?)\\.cmfd(_merge)?$");
    private static final Pattern MISC_TEMP_REGEX = Pattern.compile("^(.*?)\\.(tmp|crdownload|downloading)$");

    private final ConfigService configService;
    private final PathResolver pathResolver;
    private final SafePathValidator safePathValidator;
    private final IDownloadManager downloadManager;
    private final ICivitaiHubService civitaiHubService;
    private final CivitaiService civitaiService;
    private final IHuggingFaceHubService huggingFaceHubService;

    /**
     * Constructs a new AbortedDownloadService with required and optional dependencies.
     *
     * @param configService     the configuration service providing model paths
     * @param pathResolver      the path resolver for platform-independent paths
     * @param safePathValidator the validator guarding against directory traversal
     * @param downloadManager   the download manager handling download queues
     * @param civitaiHubService the Civitai hub service for model queries
     * @param civitaiService    legacy or direct civitai service for hash/name lookups
     */
    @Autowired
    public AbortedDownloadService(
            ConfigService configService,
            PathResolver pathResolver,
            @Autowired(required = false) SafePathValidator safePathValidator,
            IDownloadManager downloadManager,
            @Autowired(required = false) ICivitaiHubService civitaiHubService,
            @Autowired(required = false) CivitaiService civitaiService,
            @Autowired(required = false) IHuggingFaceHubService huggingFaceHubService) {
        this.configService = configService;
        this.pathResolver = pathResolver;
        this.safePathValidator = safePathValidator != null ? safePathValidator : new SafePathValidator();
        this.downloadManager = downloadManager;
        this.civitaiHubService = civitaiHubService;
        this.civitaiService = civitaiService;
        this.huggingFaceHubService = huggingFaceHubService;
    }

    /**
     * Backward-compatible constructor for existing tests and callers without HuggingFaceHubService.
     */
    public AbortedDownloadService(
            ConfigService configService,
            PathResolver pathResolver,
            SafePathValidator safePathValidator,
            IDownloadManager downloadManager,
            ICivitaiHubService civitaiHubService,
            CivitaiService civitaiService) {
        this(configService, pathResolver, safePathValidator, downloadManager, civitaiHubService, civitaiService, null);
    }

    @Override
    public List<AbortedDownloadItem> scanAbortedDownloads(String baseModelsDir) {
        Path base = resolveBaseModelsPath(baseModelsDir);
        if (base == null || !Files.exists(base) || !Files.isDirectory(base)) {
            log.debug("Base models directory does not exist for aborted download scan: {}", base);
            return Collections.emptyList();
        }

        Map<Path, List<Path>> groupedArtifacts = new HashMap<>();
        try (Stream<Path> stream = Files.walk(base, 4)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                String name = path.getFileName().toString();
                if (isPartialArtifact(name)) {
                    Path targetPath = deriveTargetPath(path);
                    groupedArtifacts.computeIfAbsent(targetPath, k -> new ArrayList<>()).add(path);
                }
            });
        } catch (IOException e) {
            log.error("Failed to scan for aborted downloads in {}: {}", base, e.getMessage());
            return Collections.emptyList();
        }

        List<AbortedDownloadItem> results = new ArrayList<>();
        for (Map.Entry<Path, List<Path>> entry : groupedArtifacts.entrySet()) {
            Path targetPath = entry.getKey();
            String targetFileName = targetPath.getFileName().toString();

            // Exclude models that are currently actively being downloaded
            if (downloadManager != null && downloadManager.isDownloading(targetFileName)) {
                log.debug("Excluding currently active download from aborted list: {}", targetFileName);
                continue;
            }

            List<Path> fragments = entry.getValue();
            if (fragments.isEmpty()) continue;

            long totalBytes = 0;
            Instant maxModified = Instant.EPOCH;
            String extensionType = ".cmfd";

            for (Path frag : fragments) {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(frag, BasicFileAttributes.class);
                    totalBytes += attrs.size();
                    Instant mod = attrs.lastModifiedTime().toInstant();
                    if (mod.isAfter(maxModified)) {
                        maxModified = mod;
                        extensionType = extractExtensionType(frag.getFileName().toString());
                    }
                } catch (IOException ignored) {
            log.debug("Ignored non-fatal exception: {}", ignored.getMessage());
        }
            }

            if (fragments.size() > 1) {
                extensionType = fragments.size() + " segments";
            }

            String folderName = deriveFolderName(base, targetPath);
            Path primaryPath = fragments.get(0);
            results.add(new AbortedDownloadItem(
                    primaryPath,
                    targetPath.getFileName().toString(),
                    folderName,
                    totalBytes,
                    maxModified,
                    extensionType,
                    null
            ));
        }

        results.sort(Comparator.comparing(AbortedDownloadItem::lastModified, Comparator.nullsLast(Comparator.reverseOrder())));
        return results;
    }

    @Override
    public long deleteAbortedDownloads(List<AbortedDownloadItem> items) {
        if (items == null || items.isEmpty()) return 0L;
        Path base = resolveBaseModelsPath(null);
        long totalFreedBytes = 0L;

        for (AbortedDownloadItem item : items) {
            if (item == null || item.filePath() == null) continue;
            Path file = item.filePath();
            try {
                if (base != null) safePathValidator.validateWithinBase(base, file);
                Path dir = file.getParent();
                String targetName = item.targetFileName();

                // Guard against deleting partial fragments of an active download
                if (downloadManager != null && downloadManager.isDownloading(targetName)) {
                    log.warn("Cannot delete partial artifacts for model [{}] because it is currently actively downloading", targetName);
                    continue;
                }

                // Delete the primary file
                if (Files.exists(file)) {
                    totalFreedBytes += Files.size(file);
                    Files.deleteIfExists(file);
                    log.info("Deleted aborted download artifact: {}", file);
                }

                // Clean up any remaining sibling segments or temporary merge files for this target
                if (dir != null && Files.exists(dir)) {
                    try (DirectoryStream<Path> siblings = Files.newDirectoryStream(dir, targetName + ".*")) {
                        for (Path sib : siblings) {
                            if (isPartialArtifact(sib.getFileName().toString())) {
                                if (base != null) safePathValidator.validateWithinBase(base, sib);
                                totalFreedBytes += Files.size(sib);
                                Files.deleteIfExists(sib);
                                log.info("Deleted associated partial segment: {}", sib);
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                log.error("Failed to delete aborted download item {}: {}", file, ex.getMessage());
            }
        }
        return totalFreedBytes;
    }

    @Override
    public boolean hasAbortedDownload(String baseModelsDir, String folder, String targetFileName) {
        return findAbortedDownload(baseModelsDir, folder, targetFileName).isPresent();
    }

    @Override
    public Optional<AbortedDownloadItem> findAbortedDownload(String baseModelsDir, String folder, String targetFileName) {
        if (targetFileName == null || targetFileName.isBlank()) return Optional.empty();
        Path base = resolveBaseModelsPath(baseModelsDir);
        if (base == null || !Files.exists(base)) return Optional.empty();

        String sub = (folder != null && !folder.isBlank()) ? folder : ModelFolder.CHECKPOINTS.getDefaultFolderName();
        if (sub.startsWith("models/") || sub.startsWith("models\\")) sub = sub.substring(7);

        Path modelDir = base.resolve(sub);
        if (!Files.exists(modelDir)) return Optional.empty();

        List<Path> matchingFragments = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(modelDir, targetFileName + ".*")) {
            for (Path p : ds) {
                if (isPartialArtifact(p.getFileName().toString())) {
                    matchingFragments.add(p);
                }
            }
        } catch (IOException e) {
            return Optional.empty();
        }

        if (matchingFragments.isEmpty()) return Optional.empty();

        long totalSize = 0L;
        Instant maxMod = Instant.EPOCH;
        String extType = ".cmfd";

        for (Path frag : matchingFragments) {
            try {
                BasicFileAttributes attrs = Files.readAttributes(frag, BasicFileAttributes.class);
                totalSize += attrs.size();
                Instant m = attrs.lastModifiedTime().toInstant();
                if (m.isAfter(maxMod)) {
                    maxMod = m;
                    extType = extractExtensionType(frag.getFileName().toString());
                }
            } catch (IOException ignored) {
            log.debug("Ignored non-fatal exception: {}", ignored.getMessage());
        }
        }

        if (matchingFragments.size() > 1) extType = matchingFragments.size() + " segments";

        return Optional.of(new AbortedDownloadItem(
                matchingFragments.get(0),
                targetFileName,
                sub,
                totalSize,
                maxMod,
                extType,
                null
        ));
    }

    @Override
    public CompletableFuture<Boolean> resumeDownload(AbortedDownloadItem item, Consumer<String> progressConsumer) {
        if (item == null) return CompletableFuture.completedFuture(false);
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        Thread.ofVirtual().name("ResumeDownload-" + item.targetFileName()).start(() -> {
            try {
                String downloadUrl = item.downloadUrl();
                if (downloadUrl == null || downloadUrl.isBlank()) {
                    progressConsumer.accept("Resolving download URL for " + item.targetFileName() + "...");
                    downloadUrl = resolveDownloadUrlForModel(item.targetFileName());
                }

                if (downloadUrl == null || downloadUrl.isBlank()) {
                    progressConsumer.accept("❌ Could not resolve download URL for " + item.targetFileName() + ". Please download from Civitai Model Hub.");
                    future.complete(false);
                    return;
                }

                progressConsumer.accept("Resuming download for " + item.targetFileName() + "...");
                ModelInfo info = new ModelInfo(item.folderName(), item.targetFileName(), downloadUrl);
                info.setFilename(item.targetFileName());
                info.setSave_path(item.folderName());

                Path base = resolveBaseModelsPath(null);
                String baseDirStr = base != null ? base.toString() : "models";

                downloadManager.startQueue(
                        List.of(info),
                        new boolean[]{true},
                        baseDirStr,
                        (idx, status) -> progressConsumer.accept(status),
                        () -> {
                            boolean completed = Files.exists(Path.of(baseDirStr, item.folderName(), item.targetFileName()));
                            if (completed) {
                                downloadManager.notifyComfyUI(true);
                                progressConsumer.accept("✅ Download completed and resumed successfully");
                            }
                            future.complete(completed);
                        }
                );
            } catch (Exception ex) {
                log.error("Failed to resume download for {}: {}", item.targetFileName(), ex.getMessage(), ex);
                progressConsumer.accept("❌ Resume failed: " + ex.getMessage());
                future.complete(false);
            }
        });

        return future;
    }

        private String resolveDownloadUrlForModel(String targetFileName) {
        if (targetFileName == null || targetFileName.isBlank()) return null;
        try {
            // 1. Try CivitaiHubService (exact match or clean name match)
            if (civitaiHubService != null) {
                String cleanQuery = targetFileName.replaceAll("\\.(safetensors|ckpt|pt|bin|gguf)$", "")
                        .replace("_", " ")
                        .replace("-", " ")
                        .trim();
                List<CivitaiModelEntry> entries = civitaiHubService.queryModels(cleanQuery, null, null, null, 10, 1, false).join();
                if (entries != null) {
                    for (CivitaiModelEntry entry : entries) {
                        if (targetFileName.equalsIgnoreCase(entry.filename()) && entry.downloadUrl() != null && !entry.downloadUrl().isBlank()) {
                            return entry.downloadUrl();
                        }
                    }
                    String targetBase = targetFileName.replaceAll("\\.(safetensors|ckpt|pt|bin|gguf)$", "");
                    for (CivitaiModelEntry entry : entries) {
                        String entryBase = entry.filename() != null ? entry.filename().replaceAll("\\.(safetensors|ckpt|pt|bin|gguf)$", "") : "";
                        if (targetBase.equalsIgnoreCase(entryBase) && entry.downloadUrl() != null && !entry.downloadUrl().isBlank()) {
                            return entry.downloadUrl();
                        }
                    }
                }
            }

            // 2. Try HuggingFaceHubService if not found on Civitai
            if (huggingFaceHubService != null) {
                String cleanQuery = targetFileName.replaceAll("\\.(safetensors|ckpt|pt|bin|gguf)$", "")
                        .replace("_", " ")
                        .replace("-", " ")
                        .trim();
                List<de.tki.comfyuicompanion.domain.HuggingFaceModelEntry> repos =
                        huggingFaceHubService.searchRepositories(cleanQuery, "all", "all", "downloads", 5).join();
                if (repos != null) {
                    for (de.tki.comfyuicompanion.domain.HuggingFaceModelEntry repo : repos) {
                        List<de.tki.comfyuicompanion.domain.HuggingFaceFileEntry> files =
                                huggingFaceHubService.getRepositoryFiles(repo.repoId()).join();
                        if (files != null) {
                            for (de.tki.comfyuicompanion.domain.HuggingFaceFileEntry file : files) {
                                if (targetFileName.equalsIgnoreCase(file.filename())
                                        || file.rfilename().endsWith("/" + targetFileName)
                                        || file.rfilename().equalsIgnoreCase(targetFileName)) {
                                    return file.downloadUrl();
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to resolve download URL for {}: {}", targetFileName, ex.getMessage());
        }
        return null;
    }

    private boolean isPartialArtifact(String filename) {
        if (filename == null) return false;
        return CMFD_REGEX.matcher(filename).matches()
                || PART_REGEX.matcher(filename).matches()
                || MISC_TEMP_REGEX.matcher(filename).matches()
                || filename.endsWith(".cmfd")
                || filename.endsWith(".cmfd_merge")
                || filename.endsWith(".crdownload")
                || filename.endsWith(".tmp");
    }

    private Path deriveTargetPath(Path partialFile) {
        String name = partialFile.getFileName().toString();
        Matcher m1 = CMFD_REGEX.matcher(name);
        if (m1.matches()) return partialFile.resolveSibling(m1.group(1));

        Matcher m2 = PART_REGEX.matcher(name);
        if (m2.matches()) return partialFile.resolveSibling(m2.group(1));

        Matcher m3 = MISC_TEMP_REGEX.matcher(name);
        if (m3.matches()) return partialFile.resolveSibling(m3.group(1));

        return partialFile;
    }

    private String extractExtensionType(String filename) {
        Matcher m = PART_REGEX.matcher(filename);
        if (m.matches()) return ".part" + m.group(2);
        if (filename.endsWith(".cmfd_merge")) return ".cmfd_merge";
        if (filename.endsWith(".cmfd")) return ".cmfd";
        if (filename.endsWith(".crdownload")) return ".crdownload";
        if (filename.endsWith(".tmp")) return ".tmp";
        return ".part";
    }

    private String deriveFolderName(Path base, Path targetPath) {
        try {
            Path rel = base.relativize(targetPath.getParent());
            String res = rel.toString().replace('\\', '/');
            return res.isEmpty() ? ModelFolder.CHECKPOINTS.getDefaultFolderName() : res;
        } catch (Exception e) {
            return ModelFolder.CHECKPOINTS.getDefaultFolderName();
        }
    }

    private Path resolveBaseModelsPath(String baseModelsDir) {
        if (baseModelsDir != null && !baseModelsDir.isBlank()) {
            return pathResolver != null ? pathResolver.resolve(baseModelsDir) : Path.of(baseModelsDir);
        }
        String configured = configService != null ? configService.getModelsPath() : null;
        return pathResolver != null ? pathResolver.resolve(configured != null ? configured : "models") : Path.of("models");
    }
}
