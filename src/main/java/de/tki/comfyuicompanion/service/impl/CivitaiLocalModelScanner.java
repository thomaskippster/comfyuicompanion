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

    /**
     * Constructs a new CivitaiLocalModelScanner with the given configuration service and path resolver.
     *
     * @param configService the configuration service providing base model paths
     * @param pathResolver  the path resolver providing extra model paths from extra_model_paths.yaml
     */
    @Autowired
    public CivitaiLocalModelScanner(
            @Autowired(required = false) IConfigService configService,
            @Autowired(required = false) PathResolver pathResolver) {
        this.configService = configService;
        this.pathResolver = pathResolver;
    }

    /**
     * Backward-compatible constructor for tests and callers providing only the configuration service.
     *
     * @param configService configuration service
     */
    public CivitaiLocalModelScanner(IConfigService configService) {
        this(configService, null);
    }

    /**
     * Determines whether a given filesystem path points to a completed, valid model file,
     * filtering out temporary and partial download fragments (.cmfd, .part*, .tmp, .crdownload).
     *
     * @param p path to evaluate
     * @return true if valid and completed model file, false otherwise
     */
    public static boolean isCompletedModelFile(Path p) {
        if (p == null || !Files.isRegularFile(p)) {
            return false;
        }
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

    /**
     * Determines whether a given filesystem path points to a completed neural network weight checkpoint file,
     * excluding non-weight assets such as archives and workflow JSON files.
     *
     * @param p path to evaluate
     * @return true if valid completed weight file, false otherwise
     */
    public static boolean isCompletedWeightFile(Path p) {
        if (!isCompletedModelFile(p)) {
            return false;
        }
        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".safetensors") || name.endsWith(".gguf") || name.endsWith(".bin")
                || name.endsWith(".ckpt") || name.endsWith(".pt") || name.endsWith(".pth")
                || name.endsWith(".sft") || name.endsWith(".onnx");
    }

    /**
     * Cleans an incomplete download fragment suffix (.cmfd, .part*) from a filename if present.
     *
     * @param filename raw filename
     * @return cleaned model filename
     */
    public static String cleanModelFileName(String filename) {
        if (filename == null) {
            return null;
        }
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
        if (fileName == null || fileName.isBlank()) {
            return false;
        }

        List<Path> candidateDirs = resolveCandidateDirectories(folder);
        if (info.getType() != null && !info.getType().equalsIgnoreCase(folder)) {
            for (Path extraDir : resolveCandidateDirectories(info.getType())) {
                if (!candidateDirs.contains(extraDir)) {
                    candidateDirs.add(extraDir);
                }
            }
        }

        for (Path rootDir : candidateDirs) {
            // 1. Direct path check
            Path direct = rootDir.resolve(fileName);
            if (Files.exists(direct) && isCompletedModelFile(direct)) {
                return true;
            }

            // 2. Recursive check inside candidate directory (up to 4 levels deep)
            Optional<Path> found = findFileRecursively(rootDir, fileName);
            if (found.isPresent()) {
                Path file = found.get();
                Path relParent = rootDir.relativize(file.getParent());
                String sub = relParent.toString().replace('\\', '/');
                if (!sub.isEmpty()) {
                    String cleanFolder = folder.replace('\\', '/');
                    if (cleanFolder.startsWith("models/")) {
                        cleanFolder = cleanFolder.substring(7);
                    }
                    info.setSave_path(cleanFolder + "/" + sub);
                }
                info.setFilename(file.getFileName().toString());
                return true;
            }
        }

        return false;
    }

    private Optional<Path> findFileRecursively(Path rootDir, String targetFileName) {
        try (Stream<Path> stream = Files.walk(rootDir, 4)) {
            return stream
                    .filter(CivitaiLocalModelScanner::isCompletedModelFile)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(targetFileName))
                    .findFirst();
        } catch (IOException ex) {
            log.debug("Error while inspecting {} for {}: {}", rootDir, targetFileName, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Searches local model directories (checkpoints, diffusion_models, unet) for the first existing checkpoint file.
     *
     * @return an {@link Optional} containing the {@link ModelInfo} for the first found checkpoint, or empty if none exist
     */
    public Optional<ModelInfo> findFirstLocalCheckpointInfo() {
        String[] candidateDirs = {"checkpoints", "diffusion_models", "unet"};
        for (String sub : candidateDirs) {
            List<Path> searchDirs = resolveCandidateDirectories(sub);
            for (Path dir : searchDirs) {
                try (Stream<Path> stream = Files.walk(dir, 4)) {
                    Optional<Path> match = stream
                            .filter(CivitaiLocalModelScanner::isCompletedWeightFile)
                            .findFirst();

                    if (match.isPresent()) {
                        Path file = match.get();
                        String name = file.getFileName().toString();
                        long sizeBytes = Files.size(file);
                        String formattedSize = formatBytes(sizeBytes);

                        Path relParent = dir.relativize(file.getParent());
                        String subPath = relParent.toString().replace('\\', '/');
                        String savePath = subPath.isEmpty() ? sub : sub + "/" + subPath;

                        ModelInfo info = new ModelInfo(sub, name, "LOCAL");
                        info.setSave_path(savePath);
                        info.setFilename(name);
                        info.setSize(formattedSize);
                        info.setByteSize(sizeBytes);
                        info.setPopularity("installed");
                        info.setDescription("✅ Already exists");
                        return Optional.of(info);
                    }
                } catch (IOException ex) {
                    log.debug("Error while inspecting {} for local checkpoints: {}", dir, ex.getMessage());
                }
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
        return findFirstLocalCheckpointInfo().map(ModelInfo::getFilename);
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
        if (lowerPattern == null || lowerPattern.isBlank()) {
            return null;
        }
        String normalizedPattern = cleanModelFileName(lowerPattern).toLowerCase(Locale.ROOT);

        for (String folder : folders) {
            List<Path> candidateDirs = resolveCandidateDirectories(folder);
            for (Path dir : candidateDirs) {
                try (Stream<Path> stream = Files.walk(dir, 4)) {
                    Optional<Path> match = stream
                            .filter(CivitaiLocalModelScanner::isCompletedModelFile)
                            .filter(p -> {
                                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                                return name.contains(normalizedPattern) || normalizedPattern.contains(name);
                            })
                            .findFirst();

                    if (match.isPresent()) {
                        Path file = match.get();
                        String name = file.getFileName().toString();
                        long sizeBytes = Files.size(file);
                        String formattedSize = formatBytes(sizeBytes);

                        Path relParent = dir.relativize(file.getParent());
                        String sub = relParent.toString().replace('\\', '/');
                        String cleanFolder = folder.replace('\\', '/');
                        if (cleanFolder.startsWith("models/")) {
                            cleanFolder = cleanFolder.substring(7);
                        }
                        String savePath = sub.isEmpty() ? cleanFolder : cleanFolder + "/" + sub;

                        ModelInfo info = new ModelInfo(cleanFolder, name, "LOCAL");
                        info.setSave_path(savePath);
                        info.setFilename(name);
                        info.setSize(formattedSize);
                        info.setByteSize(sizeBytes);
                        info.setPopularity("installed");
                        info.setDescription("✅ Already exists");
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
     * Determines a fallback checkpoint filename when building workflows for LoRA models
     * that have no explicit base checkpoint attached in their dependencies.
     *
     * @param baseLower the lowercased base architecture string
     * @return a valid checkpoint filename
     */
    public String findFallbackCheckpointName(String baseLower) {
        List<Path> checkpointDirs = resolveCandidateDirectories("checkpoints");
        for (Path checkpointsDir : checkpointDirs) {
            try (Stream<Path> stream = Files.walk(checkpointsDir, 3)) {
                Optional<Path> match = stream
                        .filter(CivitaiLocalModelScanner::isCompletedModelFile)
                        .findFirst();
                if (match.isPresent()) {
                    return match.get().getFileName().toString();
                }
            } catch (IOException ex) {
                log.debug("Could not inspect checkpoints folder for fallback: {}", ex.getMessage());
            }
        }
        if (baseLower != null && baseLower.contains("flux")) {
            return "flux1-dev-fp8.safetensors";
        } else if (baseLower != null && (baseLower.contains("sdxl") || baseLower.contains("pony"))) {
            return "sd_xl_base_1.0.safetensors";
        }
        return "v1-5-pruned-emaonly.safetensors";
    }

    /**
     * Creates a {@link ModelInfo} descriptor for a remote model that needs to be downloaded.
     *
     * @param filename      target filename
     * @param folder        target destination folder
     * @param formattedSize human-readable size
     * @param url           remote download URL
     * @param desc          description or reference note
     * @return a new ModelInfo instance
     */
    public ModelInfo createRemoteModelInfo(String filename, String folder, String formattedSize, String url, String desc) {
        ModelInfo info = new ModelInfo(folder, filename, url);
        info.setFilename(filename);
        info.setSize(formattedSize);
        info.setDescription("Missing");
        info.setPopularity("missing");
        info.setReference(desc);
        return info;
    }

    /**
     * Formats byte size into human readable string (GB, MB, B).
     *
     * @param bytes byte size
     * @return formatted size string
     */
    public String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        } else if (bytes >= 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return bytes + " B";
        }
    }
}
