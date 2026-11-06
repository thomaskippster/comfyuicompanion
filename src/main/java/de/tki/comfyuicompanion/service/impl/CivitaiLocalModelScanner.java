package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.IConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Service component responsible for scanning, verifying, and matching local model files
 * (checkpoints, diffusion models, UNETs, VAEs, text encoders) on the filesystem,
 * integrating both primary base directories and extra model paths.
 */
@Component
public class CivitaiLocalModelScanner {

    private static final Logger log = LoggerFactory.getLogger(CivitaiLocalModelScanner.class);

    private final IConfigService configService;
    private final PathResolver pathResolver;
    private final de.tki.comfyuicompanion.service.ILocalModelArchiveDetector archiveDetector;

    /** Constructs a new CivitaiLocalModelScanner with the given configuration service and path resolver. */
    @Autowired
    public CivitaiLocalModelScanner(
            @Autowired(required = false) IConfigService configService,
            @Autowired(required = false) PathResolver pathResolver,
            @Autowired(required = false) de.tki.comfyuicompanion.service.ILocalModelArchiveDetector archiveDetector) {
        this.configService = configService;
        this.pathResolver = pathResolver;
        this.archiveDetector = archiveDetector;
    }

    public CivitaiLocalModelScanner(IConfigService configService, PathResolver pathResolver) {
        this(configService, pathResolver, null);
    }

    public CivitaiLocalModelScanner(IConfigService configService) {
        this(configService, null, null);
    }

    /** Determines whether a given filesystem path points to a completed, valid model file. */
    public static boolean isCompletedModelFile(Path p) {
        if (p == null || !Files.isRegularFile(p)) return false;
        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.startsWith(".") || name.endsWith(".tmp") || name.endsWith(".temp")
                || name.endsWith(".cmfd") || name.endsWith(".cmfd_merge")
                || name.endsWith(".crdownload") || name.endsWith(".download")
                || name.endsWith(".downloading") || name.endsWith(".aria2")
                || name.endsWith(".!ut") || name.endsWith(".incomplete")
                || name.matches(".*\\.part\\d*$")) {
            return false;
        }
        return name.endsWith(".safetensors") || name.endsWith(".gguf") || name.endsWith(".bin")
                || name.endsWith(".ckpt") || name.endsWith(".pt") || name.endsWith(".pth")
                || name.endsWith(".sft") || name.endsWith(".onnx") || name.endsWith(".engine")
                || name.endsWith(".patch") || name.endsWith(".zip") || name.endsWith(".json");
    }

    /** Determines whether a given filesystem path points to a completed neural network weight checkpoint file. */
    public static boolean isCompletedWeightFile(Path p) {
        if (!isCompletedModelFile(p)) return false;
        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.contains("easynegative") || name.contains("easy_negative") || name.contains("remacri")) return false;
        return name.endsWith(".safetensors") || name.endsWith(".gguf") || name.endsWith(".bin")
                || name.endsWith(".ckpt") || name.endsWith(".pt") || name.endsWith(".pth")
                || name.endsWith(".sft") || name.endsWith(".onnx");
    }

    /** Determines whether a path is a valid CheckpointLoaderSimple model (.safetensors, .ckpt). */
    public static boolean isCheckpointedWeightFile(Path p) {
        if (!isCompletedModelFile(p)) return false;
        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.contains("easynegative") || name.contains("easy_negative") || name.contains("remacri")) return false;
        return name.endsWith(".safetensors") || name.endsWith(".ckpt");
    }

    /** Cleans an incomplete download fragment suffix (.cmfd, .part*) from a filename if present. */
    public static String cleanModelFileName(String filename) {
        if (filename == null) return null;
        String trimmed = filename.trim();
        if (trimmed.toLowerCase(Locale.ROOT).endsWith(".cmfd")) {
            return trimmed.substring(0, trimmed.length() - 5);
        }
        if (trimmed.toLowerCase(Locale.ROOT).endsWith(".cmfd_merge")) {
            return trimmed.substring(0, trimmed.length() - 11);
        }
        if (trimmed.matches("(?i).*\\.part\\d+$")) {
            int lastDot = trimmed.lastIndexOf('.');
            return trimmed.substring(0, lastDot);
        }
        return trimmed;
    }

    /**
     * Checks if the physical file for the given {@link ModelInfo} exists on disk and sets its status.
     *
     * @param info the model info to inspect and update
     */
    public void checkAndSetModelStatus(ModelInfo info) {
        boolean present = isModelFilePresent(info);
        info.setPopularity(present ? "installed" : "missing");
        info.setDescription(present ? "✅ Already exists" : "Missing");
    }

    /**
     * Resolves all possible filesystem directories for a given model folder category,
     * aggregating paths from {@link PathResolver} (e.g. extra_model_paths.yaml) and
     * the base models path from {@link IConfigService}.
     *
     * @param folder folder category (e.g. "vae", "checkpoints", "unet", "clip")
     * @return list of existing candidate directories on the filesystem
     */
    public List<Path> resolveCandidateDirectories(String folder) {
        if (folder == null || folder.isBlank()) {
            return Collections.emptyList();
        }
        Set<Path> candidates = new LinkedHashSet<>();
        String clean = folder.replace('\\', '/').trim();
        if (clean.startsWith("models/")) {
            clean = clean.substring(7);
        }
        clean = de.tki.comfyuicompanion.domain.ModelFolder.fromString(clean).getDefaultFolderName();

        // 1. Extra model paths registered in PathResolver (e.g. from extra_model_paths.yaml)
        if (pathResolver != null) {
            List<Path> extras = pathResolver.getModelPaths(clean);
            if (extras != null) {
                for (Path p : extras) {
                    if (p != null && Files.exists(p) && Files.isDirectory(p)) {
                        candidates.add(p.toAbsolutePath().normalize());
                    }
                }
            }
        }

        // 1b. Discovered external model archive directories
        if (archiveDetector != null) {
            for (Path ap : archiveDetector.resolveArchiveDirectoriesForCategory(clean)) {
                if (Files.isDirectory(ap)) candidates.add(ap.toAbsolutePath().normalize());
            }
        }

        // 2. Base directory from ConfigService
        String baseDir = configService != null ? configService.getModelsPath() : "models";
        Path modelsBase = Path.of(baseDir != null && !baseDir.isBlank() ? baseDir : "models");
        Path directBase = modelsBase.resolve(clean);
        if (Files.exists(directBase) && Files.isDirectory(directBase)) {
            candidates.add(directBase.toAbsolutePath().normalize());
        }

        // 3. Fallback to ComfyUI installation models folder if known
        if (pathResolver != null && pathResolver.getComfyUIRoot() != null && !pathResolver.getComfyUIRoot().isBlank()) {
            Path comfyModels = Path.of(pathResolver.getComfyUIRoot(), "models", clean);
            if (Files.exists(comfyModels) && Files.isDirectory(comfyModels)) {
                candidates.add(comfyModels.toAbsolutePath().normalize());
            }
        }

        // 4. External download directories for workflows
        if ("workflows".equalsIgnoreCase(clean)) {
            try {
                Path userDl = Path.of(System.getProperty("user.home"), "Downloads");
                if (Files.isDirectory(userDl)) candidates.add(userDl.toAbsolutePath().normalize());
            } catch (Exception ignored) {}
            for (char drive = 'c'; drive <= 'z'; drive++) {
                Path d = Path.of(drive + ":\\Downloads");
                if (Files.isDirectory(d)) candidates.add(d.toAbsolutePath().normalize());
            }
        }

        return new ArrayList<>(candidates);
    }

    /**
     * Determines whether the physical model file described by {@link ModelInfo} exists on disk,
     * including scanning nested subdirectories (e.g. vae/FLUX1/ae.safetensors) across all
     * candidate directories configured in ComfyUI.
     *
     * @param info the model metadata
     * @return {@code true} if the file exists and is a valid completed model file, {@code false} otherwise
     */
    public boolean isModelFilePresent(ModelInfo info) {
        if (info == null) {
            return false;
        }
        String folder = info.getSave_path() != null && !info.getSave_path().isBlank()
                ? info.getSave_path()
                : (info.getType() != null ? info.getType() : "checkpoints");

        String rawName = info.getFilename() != null && !info.getFilename().isBlank() ? info.getFilename() : info.getName();
        String fileName = cleanModelFileName(rawName);
        if (fileName == null || fileName.isBlank()) return false;
        if ("ae.safetensors".equalsIgnoreCase(fileName)) {
            folder = "vae";
            info.setSave_path("vae");
        }
        List<Path> candidateDirs = resolveCandidateDirectories(folder);
        if (info.getType() != null && !info.getType().equalsIgnoreCase(folder)) {
            for (Path extraDir : resolveCandidateDirectories(info.getType())) {
                if (!candidateDirs.contains(extraDir)) candidateDirs.add(extraDir);
            }
        }
        if ("vae".equalsIgnoreCase(folder)) {
            for (Path dir : candidateDirs) reconcileVaeRoot(dir);
        }
        for (Path rootDir : candidateDirs) {
            Path direct = rootDir.resolve(fileName);
            if (Files.exists(direct) && isCompletedModelFile(direct)) {
                applyFileSize(info, direct);
                if (info.getSave_path() == null || info.getSave_path().isBlank()) info.setSave_path(folder);
                return true;
            }

            Optional<Path> found = findFileRecursively(rootDir, fileName);
            if (found.isPresent()) {
                Path file = found.get();
                applyFileSize(info, file);
                Path relParent = rootDir.relativize(file.getParent());
                String sub = relParent.toString().replace('\\', '/');
                if (!sub.isEmpty()) {
                    String baseCategory = (info.getType() != null && !info.getType().isBlank() ? info.getType() : folder).replace('\\', '/');
                    if (baseCategory.startsWith("models/")) baseCategory = baseCategory.substring(7);
                    int slash = baseCategory.indexOf('/');
                    if (slash > 0) baseCategory = baseCategory.substring(0, slash);
                    if ("vae".equalsIgnoreCase(baseCategory) && fileName.toLowerCase(Locale.ROOT).startsWith("ae.")) {
                        info.setSave_path("vae");
                    } else {
                        info.setSave_path(baseCategory + "/" + sub);
                    }
                }
                info.setFilename(file.getFileName().toString());
                return true;
            }
        }

        return false;
    }

    private void reconcileVaeRoot(Path vaeDir) {
        if (vaeDir == null || !Files.isDirectory(vaeDir)) return;
        Path rootAe = vaeDir.resolve("ae.safetensors");
        try { if (Files.exists(rootAe) && Files.size(rootAe) > 1000) return; } catch (Exception ignored) {}
        for (String sub : List.of("FLUX1", "flux1", "FLUX.1", "flux")) {
            Path nested = vaeDir.resolve(sub).resolve("ae.safetensors");
            try {
                if (Files.isRegularFile(nested) && Files.size(nested) > 1000) {
                    Files.deleteIfExists(rootAe);
                    try { Files.createLink(rootAe, nested); }
                    catch (Exception ex) { Files.copy(nested, rootAe, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
                    log.info("Reconciled root VAE ae.safetensors from nested subfolder: {}", nested);
                    break;
                }
            } catch (Exception ignored) {}
        }
    }

    private void applyFileSize(ModelInfo info, Path path) {
        try {
            long bytes = Files.size(path);
            if (bytes > 0) {
                info.setByteSize(bytes);
                info.setSize(formatBytes(bytes));
            }
        } catch (Exception ignored) {}
    }

    private Optional<Path> findFileRecursively(Path rootDir, String targetFileName) {
        String targetStem = LocalModelScanner.normalizeQuantizationStem(targetFileName);
        try (Stream<Path> stream = Files.walk(rootDir, 4)) {
            List<Path> candidates = stream.filter(CivitaiLocalModelScanner::isCompletedModelFile).toList();
            Optional<Path> exact = candidates.stream()
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(targetFileName))
                    .findFirst();
            if (exact.isPresent() || targetStem.length() < 4) {
                return exact;
            }
            return candidates.stream()
                    .filter(CivitaiLocalModelScanner::isCompletedWeightFile)
                    .filter(p -> LocalModelScanner.normalizeQuantizationStem(p.getFileName().toString()).equals(targetStem))
                    .findFirst();
        } catch (IOException ex) {
            log.debug("Error while inspecting {} for {}: {}", rootDir, targetFileName, ex.getMessage());
            return Optional.empty();
        }
    }

    /** Searches local model directories for the first existing checkpoint file. */
    public Optional<ModelInfo> findFirstLocalCheckpointInfo() {
        for (Path dir : resolveCandidateDirectories("checkpoints")) {
            try (Stream<Path> stream = Files.walk(dir, 4)) {
                Optional<Path> match = stream.filter(CivitaiLocalModelScanner::isCheckpointedWeightFile).findFirst();
                if (match.isPresent()) {
                    Path file = match.get();
                    String name = file.getFileName().toString();
                    long sizeBytes = Files.size(file);
                    Path relParent = dir.relativize(file.getParent());
                    String subPath = relParent.toString().replace('\\', '/');
                    String savePath = subPath.isEmpty() ? "checkpoints" : "checkpoints/" + subPath;
                    ModelInfo info = new ModelInfo("checkpoints", name, "LOCAL");
                    info.setSave_path(savePath);
                    info.setFilename(name);
                    info.setSize(formatBytes(sizeBytes));
                    info.setByteSize(sizeBytes);
                    info.setPopularity("installed");
                    info.setDescription("✅ Already exists");
                    return Optional.of(info);
                }
            } catch (IOException ex) {
                log.debug("Error while inspecting {} for local checkpoints: {}", dir, ex.getMessage());
            }
        }
        return Optional.empty();
    }

    /**
     * Resolves the filename of the first locally installed checkpoint, or empty if none are found.
     *
     * @return optional containing the filename of the installed checkpoint
     */
    public Optional<String> findFirstLocalCheckpointName() {
        return findFirstLocalCheckpointInfo().map(info ->
                findComfyRelativeModelPath(info.getFilename(), "checkpoints").orElse(info.getFilename())
        );
    }

    /**
     * Finds a local model file in any of the specified directories matching the given name substring,
     * properly resolving subdirectories and excluding partial/incomplete download files.
     *
     * @param lowerPattern search substring
     * @param folders candidate subfolders inside models directory (e.g. "vae", "text_encoders")
     * @return ModelInfo if found, null otherwise
     */
    public ModelInfo findLocalModelMatching(String lowerPattern, String... folders) {
        if (lowerPattern == null || lowerPattern.isBlank()) return null;
        String normalizedPattern = cleanModelFileName(lowerPattern).toLowerCase(Locale.ROOT);

        for (String folder : folders) {
            boolean isCkpt = "checkpoints".equalsIgnoreCase(folder);
            for (Path dir : resolveCandidateDirectories(folder)) {
                try (Stream<Path> stream = Files.walk(dir, 4)) {
                    Optional<Path> match = stream
                            .filter(isCkpt ? CivitaiLocalModelScanner::isCheckpointedWeightFile : CivitaiLocalModelScanner::isCompletedModelFile)
                            .filter(p -> {
                                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                                return name.contains(normalizedPattern) || normalizedPattern.contains(name);
                            })
                            .findFirst();

                    if (match.isPresent()) {
                        Path file = match.get();
                        String name = file.getFileName().toString();
                        long sizeBytes = Files.size(file);
                        Path relParent = dir.relativize(file.getParent());
                        String sub = relParent.toString().replace('\\', '/');
                        String cleanFolder = folder.replace('\\', '/');
                        if (cleanFolder.startsWith("models/")) cleanFolder = cleanFolder.substring(7);
                        String savePath = sub.isEmpty() ? cleanFolder : cleanFolder + "/" + sub;
                        ModelInfo info = new ModelInfo(cleanFolder, name, "LOCAL");
                        info.setSave_path(savePath); info.setFilename(name); info.setSize(formatBytes(sizeBytes));
                        info.setByteSize(sizeBytes); info.setPopularity("installed"); info.setDescription("✅ Already exists");
                        return info;
                    }
                } catch (IOException ignored) {
                    log.debug("Ignored non-fatal exception: {}", ignored.getMessage());
                }
            }
        }
        return null;
    }

    /**
     * Resolves the ComfyUI relative path for a model file if it resides in a subfolder
     * (e.g. "qwen\\qwen_2.5_vl_7b_fp8_scaled.safetensors").
     *
     * @param modelFileName target file name (e.g. "qwen_2.5_vl_7b_fp8_scaled.safetensors")
     * @param folders       candidate ComfyUI subfolders to search (defaults to all standard model folders)
     * @return relative path suitable for ComfyUI widgets, or empty if not in a subfolder
     */
    public Optional<String> findComfyRelativeModelPath(String modelFileName, String... folders) {
        if (modelFileName == null || modelFileName.isBlank()) return Optional.empty();
        String cleanName = cleanModelFileName(modelFileName);
        if ("ae.safetensors".equalsIgnoreCase(cleanName)) return Optional.empty();
        String[] searchFolders = (folders != null && folders.length > 0)
                ? folders
                : new String[]{"text_encoders", "clip", "checkpoints", "diffusion_models", "unet", "vae", "loras"};

        for (String folder : searchFolders) {
            List<Path> candidateDirs = resolveCandidateDirectories(folder);
            for (Path dir : candidateDirs) {
                try (Stream<Path> stream = Files.walk(dir, 5)) {
                    Optional<Path> match = stream
                            .filter(CivitaiLocalModelScanner::isCompletedModelFile)
                            .filter(p -> p.getFileName().toString().equalsIgnoreCase(cleanName))
                            .findFirst();

                    if (match.isPresent()) {
                        Path rel = dir.relativize(match.get());
                        if (rel.getNameCount() > 1) {
                            return Optional.of(rel.toString());
                        }
                    }
                } catch (IOException ignored) {
                    log.debug("Ignored exception during relative model path inspection: {}", ignored.getMessage());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Checks whether a model with the exact filename (case-insensitive) exists as a completed file
     * in any of the specified model directories.
     *
     * @param modelFileName target file name
     * @param folders       candidate ComfyUI subfolders to search
     * @return true if the exact model file exists locally
     */
    public boolean isExactModelInstalled(String modelFileName, String... folders) {
        if (modelFileName == null || modelFileName.isBlank()) {
            return false;
        }
        String cleanName = cleanModelFileName(modelFileName);
        String[] searchFolders = (folders != null && folders.length > 0)
                ? folders
                : new String[]{"text_encoders", "clip", "checkpoints", "diffusion_models", "unet", "vae", "loras"};

        for (String folder : searchFolders) {
            for (Path dir : resolveCandidateDirectories(folder)) {
                try (Stream<Path> stream = Files.walk(dir, 5)) {
                    boolean found = stream
                            .filter(CivitaiLocalModelScanner::isCompletedModelFile)
                            .anyMatch(p -> p.getFileName().toString().equalsIgnoreCase(cleanName));
                    if (found) {
                        return true;
                    }
                } catch (IOException ignored) {
                    log.debug("Ignored exception during exact model inspection: {}", ignored.getMessage());
                }
            }
        }
        return false;
    }

    /**
     * Determines a fallback checkpoint filename when building workflows for LoRA models
     * that have no explicit base checkpoint attached in their dependencies.
     *
     * @param baseLower the lowercased base architecture string
     * @return a valid checkpoint filename
     */
    public String findFallbackCheckpointName(String baseLower) {
        if (baseLower != null && !baseLower.isBlank()) {
            String bl = baseLower.toLowerCase(Locale.ROOT);
            if (bl.contains("ltx")) {
                ModelInfo m = findLocalModelMatching("ltx", "checkpoints");
                return m != null ? findComfyRelativeModelPath(m.getFilename(), "checkpoints").orElse(m.getFilename()) : "ltx-2.3-22b-dev-nvfp4.safetensors";
            } else if (bl.contains("hunyuan")) {
                ModelInfo m = findLocalModelMatching("hunyuan", "checkpoints");
                return m != null ? findComfyRelativeModelPath(m.getFilename(), "checkpoints").orElse(m.getFilename()) : "hunyuan_video_720_cfgdistill_bf16.safetensors";
            } else if (bl.contains("sdxl") || bl.contains("pony")) {
                ModelInfo m = findLocalModelMatching("sd_xl", "checkpoints");
                if (m == null) m = findLocalModelMatching("juggernaut", "checkpoints");
                return m != null ? findComfyRelativeModelPath(m.getFilename(), "checkpoints").orElse(m.getFilename()) : "sd_xl_base_1.0.safetensors";
            } else if (bl.contains("sd 1.5") || bl.contains("sd1.5") || bl.contains("1.5")) {
                ModelInfo m = findLocalModelMatching("v1-5", "checkpoints");
                if (m == null) m = findLocalModelMatching("dreamshaper", "checkpoints");
                return m != null ? findComfyRelativeModelPath(m.getFilename(), "checkpoints").orElse(m.getFilename()) : "v1-5-pruned-emaonly.safetensors";
            }
        }
        return findFirstLocalCheckpointName().orElseGet(() -> {
            if (baseLower != null && baseLower.toLowerCase(Locale.ROOT).contains("flux")) return "flux1-dev-fp8.safetensors";
            if (baseLower != null && (baseLower.toLowerCase(Locale.ROOT).contains("sdxl") || baseLower.toLowerCase(Locale.ROOT).contains("pony"))) return "sd_xl_base_1.0.safetensors";
            return "v1-5-pruned-emaonly.safetensors";
        });
    }

    public ModelInfo createRemoteModelInfo(String filename, String folder, String formattedSize, String url, String desc) {
        ModelInfo info = new ModelInfo(folder, filename, url);
        info.setFilename(filename); info.setSize(formattedSize); info.setDescription("Missing");
        info.setPopularity("missing"); info.setReference(desc);
        return info;
    }

    public String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) return String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        if (bytes >= 1024L * 1024L) return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
        return bytes + " B";
    }
}
