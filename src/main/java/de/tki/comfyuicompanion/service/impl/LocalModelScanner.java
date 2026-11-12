package de.tki.comfyuicompanion.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tki.comfyuicompanion.domain.ModelInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Service for scanning local directories for ComfyUI models using zero-overhead file tree traversal.
 */
@Service
public class LocalModelScanner {
    private static final Logger logger = LoggerFactory.getLogger(LocalModelScanner.class);

    private final ConfigService configService;
    private final PathResolver pathResolver;
    private final Map<String, Path> modelCache = new java.util.concurrent.ConcurrentHashMap<>();

    @Autowired
    public LocalModelScanner(ConfigService configService, PathResolver pathResolver) {
        this.configService = configService;
        this.pathResolver = pathResolver;
    }

    /**
     * Scans all configured models directories (standard and extra) for supported model files.
     *
     * @return list of discovered local model metadata entries
     */
    public List<ModelInfo> scanLocalModels() {
        List<ModelInfo> foundModels = new ArrayList<>();
        Set<String> seenPaths = new HashSet<>();
        Set<Path> scannedDirs = new HashSet<>();
        Map<String, Path> newCache = new java.util.concurrent.ConcurrentHashMap<>();
        Path resolvedArchivePath = resolveArchiveRootPath();

        // 1. Scan Standard Models Path
        Path primaryModelsRoot = null;
        String modelsPathStr = configService != null ? configService.getModelsPath() : null;
        if (modelsPathStr != null && !modelsPathStr.isEmpty()) {
            Path root = Paths.get(modelsPathStr);
            if (Files.exists(root) && Files.isDirectory(root)) {
                primaryModelsRoot = root.toAbsolutePath().normalize();
                scannedDirs.add(primaryModelsRoot);
                scanDirectory(primaryModelsRoot, foundModels, seenPaths, newCache, resolvedArchivePath, null);
            }
        }

        // 2. Scan Extra Model Paths (from extra_model_paths.yaml), skipping already scanned directories
        Map<String, List<Path>> extraPaths = pathResolver != null ? pathResolver.getAllExtraModelPaths() : Map.of();
        for (Map.Entry<String, List<Path>> entry : extraPaths.entrySet()) {
            String type = entry.getKey();
            for (Path extraPath : entry.getValue()) {
                if (extraPath == null || !Files.exists(extraPath) || !Files.isDirectory(extraPath)) {
                    continue;
                }
                Path absExtra = extraPath.toAbsolutePath().normalize();
                if (!scannedDirs.add(absExtra)) {
                    continue;
                }
                if (primaryModelsRoot != null && absExtra.getParent() != null
                        && absExtra.getParent().equals(primaryModelsRoot)) {
                    continue;
                }
                scanDirectory(absExtra, foundModels, seenPaths, newCache, resolvedArchivePath, type);
            }
        }

        modelCache.clear();
        modelCache.putAll(newCache);
        return foundModels;
    }

    private Path resolveArchiveRootPath() {
        try {
            String archivePathStr = configService != null ? configService.getArchivePath() : null;
            if (archivePathStr != null && !archivePathStr.isEmpty()) {
                return Paths.get(archivePathStr).toAbsolutePath().normalize();
            }
        } catch (Exception ignored) {
            logger.debug("Ignored non-fatal exception resolving archive path: {}", ignored.getMessage());
        }
        return null;
    }

    private record DiscoveredFile(Path path, long sizeBytes) {}

    private void scanDirectory(Path absRoot,
                               List<ModelInfo> foundModels,
                               Set<String> seenPaths,
                               Map<String, Path> targetCache,
                               Path resolvedArchivePath,
                               String forcedType) {
        if (!Files.exists(absRoot) || !Files.isDirectory(absRoot)) {
            return;
        }
        String rootName = absRoot.getFileName() != null ? absRoot.getFileName().toString().toLowerCase(Locale.ROOT) : "";
        boolean rootIsArchive = rootName.contains("archive") || rootName.contains("archived");

        List<DiscoveredFile> modelFiles = new ArrayList<>();
        Set<Path> imageFiles = new HashSet<>();

        try {
            Files.walkFileTree(absRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(absRoot) && isDirectoryIgnored(dir, rootIsArchive, resolvedArchivePath)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!attrs.isRegularFile()) {
                        return FileVisitResult.CONTINUE;
                    }
                    String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (isSupportedModelFileName(fileName)) {
                        modelFiles.add(new DiscoveredFile(file, attrs.size()));
                    } else if (fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                        imageFiles.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });

            for (DiscoveredFile discovered : modelFiles) {
                Path file = discovered.path();
                String absPath = file.toString().toLowerCase(Locale.ROOT);
                if (seenPaths.add(absPath)) {
                    String fnLower = file.getFileName().toString().toLowerCase(Locale.ROOT);
                    targetCache.put(fnLower, file);
                    int dotIdx = fnLower.lastIndexOf('.');
                    if (dotIdx > 0) {
                        String baseNoExt = fnLower.substring(0, dotIdx);
                        targetCache.putIfAbsent(baseNoExt, file);
                        String quantStem = normalizeQuantizationStem(baseNoExt);
                        if (!quantStem.isEmpty()) {
                            targetCache.putIfAbsent(quantStem, file);
                        }
                    }
                    foundModels.add(createModelInfo(file, absRoot, forcedType, discovered.sizeBytes(), imageFiles));
                }
            }
        } catch (IOException e) {
            logger.error("Error scanning directory {}: {}", absRoot, e.getMessage());
        }
    }

    /**
     * Fast in-memory lookup from scanned cache, including quantization stem fallback.
     *
     * @param filename the requested model filename
     * @return optional path to the local model file
     */
    public Optional<Path> findModelFromCache(String filename) {
        if (filename == null || filename.isBlank()) return Optional.empty();
        String lower = filename.toLowerCase(java.util.Locale.ROOT).trim();
        Path p = modelCache.get(lower);
        if (p != null && Files.exists(p)) return Optional.of(p);
        int dot = lower.lastIndexOf('.');
        if (dot > 0) {
            String baseNoExt = lower.substring(0, dot);
            p = modelCache.get(baseNoExt);
            if (p != null && Files.exists(p)) return Optional.of(p);
        }
        String quantStem = normalizeQuantizationStem(lower);
        if (!quantStem.isEmpty()) {
            p = modelCache.get(quantStem);
            if (p != null && Files.exists(p)) return Optional.of(p);
        }
        return Optional.empty();
    }

    /**
     * Normalizes a model filename or base name by stripping common quantization and precision suffixes
     * so equivalent weight variants (e.g., fp8_scaled vs int8_convrot vs fp16) resolve consistently.
     *
     * @param rawName the model file name or base name
     * @return normalized lowercase stem without extension or precision suffix
     */
    public static String normalizeQuantizationStem(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "";
        }
        String s = rawName.replace('\\', '/');
        int slash = s.lastIndexOf('/');
        if (slash >= 0) {
            s = s.substring(slash + 1);
        }
        int dot = s.lastIndexOf('.');
        if (dot > 0 && s.substring(dot + 1).matches("(?i)safetensors|sft|ckpt|pt|pth|bin|onnx|gguf")) {
            s = s.substring(0, dot);
        }
        s = s.toLowerCase(java.util.Locale.ROOT).trim().replace('-', '_').replaceAll("_+", "_");
        String stripped = s.replaceAll(
                "(?:[_.-](?:fp8_e4m3fn_scaled|fp8_e4m3fn|fp8_scaled|fp8mixed|fp4_mixed|nvfp4_awq|int8_convrot|int8|fp8|fp16|bf16|fp32|pruned|enc))+$",
                ""
        );
        return stripped.isEmpty() ? s : stripped;
    }

    /**
     * Finds a local model by its filename, searching across all configured paths.
     */
    public Optional<Path> findModel(String filename) {
        return findModelWithPrefSize(null, filename, -1);
    }

    /**
     * Finds a model by its filename recursively, searching all configured paths and prioritizing matches with the preferred size.
     */
    public Optional<Path> findModelWithPrefSize(Path specificRoot, String filename, long preferredSize) {
        return findModelWithPrefSizeAndType(specificRoot, filename, preferredSize, null);
    }

    /**
     * Handles the find model with pref size and type operation.
     * @param specificRoot the specificRoot
     * @param filename the filename
     * @param preferredSize the preferredSize
     * @param type the type
     * @return the Optional<Path> result
     */
    public Optional<Path> findModelWithPrefSizeAndType(Path specificRoot, String filename, long preferredSize, String type) {
        if (filename == null || filename.isBlank()) return Optional.empty();

        // 0. Instant in-memory check if no specific root is requested
        if (specificRoot == null) {
            Optional<Path> cached = findModelFromCache(filename);
            if (cached.isPresent()) {
                if (preferredSize <= 0) return cached;
                try {
                    if (Files.size(cached.get()) == preferredSize) return cached;
                } catch (Exception ignored) {
            logger.debug("Ignored non-fatal exception: {}", ignored.getMessage());
        }
            }
        }

        // 1. Search in specific root if provided
        if (specificRoot != null) {
            Optional<Path> found = searchInDirectory(specificRoot, filename, preferredSize);
            if (found.isPresent()) return found;
        }

        // 2. Search in Type-Specific Extra Paths (High Priority)
        if (type != null) {
            List<Path> typePaths = pathResolver.getModelPaths(type);
            for (Path p : typePaths) {
                Optional<Path> found = searchInDirectory(p, filename, preferredSize);
                if (found.isPresent()) return found;
            }
        }

        // 3. Search in Standard Models Path
        String modelsPathStr = configService.getModelsPath();
        if (modelsPathStr != null && !modelsPathStr.isEmpty()) {
            Optional<Path> found = searchInDirectory(Paths.get(modelsPathStr), filename, preferredSize);
            if (found.isPresent()) return found;
        }

        // 4. Search in ALL Extra Model Paths (Fallback)
        Map<String, List<Path>> extraPaths = pathResolver.getAllExtraModelPaths();
        for (List<Path> paths : extraPaths.values()) {
            for (Path p : paths) {
                Optional<Path> found = searchInDirectory(p, filename, preferredSize);
                if (found.isPresent()) return found;
            }
        }
        
        return Optional.empty();
    }

    private Optional<Path> searchInDirectory(Path root, String filename, long preferredSize) {
        if (root == null || !Files.exists(root)) return Optional.empty();
        Path absRoot = root.toAbsolutePath().normalize();
        String rootName = absRoot.getFileName() != null ? absRoot.getFileName().toString().toLowerCase(Locale.ROOT) : "";
        boolean rootIsArchive = rootName.contains("archive") || rootName.contains("archived");
        Path resolvedArchivePath = resolveArchiveRootPath();
        String targetStem = normalizeQuantizationStem(filename);

        List<DiscoveredFile> exactMatches = new ArrayList<>();
        List<DiscoveredFile> stemMatches = new ArrayList<>();

        try {
            Files.walkFileTree(absRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(absRoot) && isDirectoryIgnored(dir, rootIsArchive, resolvedArchivePath)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!attrs.isRegularFile()) {
                        return FileVisitResult.CONTINUE;
                    }
                    String fn = file.getFileName().toString();
                    if (fn.equalsIgnoreCase(filename)) {
                        exactMatches.add(new DiscoveredFile(file, attrs.size()));
                        if (preferredSize > 0 && attrs.size() == preferredSize) {
                            return FileVisitResult.TERMINATE;
                        }
                    } else if (targetStem.length() >= 4 && isSupportedModelFileName(fn.toLowerCase(Locale.ROOT))) {
                        if (normalizeQuantizationStem(fn).equals(targetStem)) {
                            stemMatches.add(new DiscoveredFile(file, attrs.size()));
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });

            List<DiscoveredFile> matches = !exactMatches.isEmpty() ? exactMatches : stemMatches;
            if (matches.isEmpty()) return Optional.empty();

            if (preferredSize > 0) {
                for (DiscoveredFile df : matches) {
                    if (df.sizeBytes() == preferredSize) return Optional.of(df.path());
                }
            }
            return Optional.of(matches.get(0).path());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private boolean isSupportedModelFileName(String lowerName) {
        return lowerName.endsWith(".safetensors") || lowerName.endsWith(".sft") || lowerName.endsWith(".ckpt") ||
               lowerName.endsWith(".pth") || lowerName.endsWith(".pt") || lowerName.endsWith(".bin") || lowerName.endsWith(".onnx");
    }

    private boolean isDirectoryIgnored(Path dir, boolean rootIsArchive, Path resolvedArchivePath) {
        if (rootIsArchive) {
            return false;
        }
        if (resolvedArchivePath != null && dir.startsWith(resolvedArchivePath)) {
            return true;
        }
        Path fileName = dir.getFileName();
        if (fileName == null) {
            return false;
        }
        String part = fileName.toString().toLowerCase(Locale.ROOT);
        return part.equals("archive") || part.equals("archived")
                || part.equals(".venv") || part.equals("venv")
                || part.equals(".git") || part.equals("node_modules") || part.equals("__pycache__");
    }

    private ModelInfo createModelInfo(Path file, Path rootPath, String forcedType, long sizeBytes, Set<Path> imageFiles) {
        String fileName = file.getFileName().toString();
        Path relativePath = rootPath.relativize(file);

        // Derive type from the first folder after root
        String type = (forcedType != null) ? forcedType : "unknown";
        if (forcedType == null && relativePath.getNameCount() > 1) {
            type = relativePath.getName(0).toString();
        }

        ModelInfo info = new ModelInfo(type, fileName, "LOCAL");

        // Set save_path to the relative parent path
        if (relativePath.getParent() != null) {
            info.setSave_path(relativePath.getParent().toString().replace("\\", "/"));
        }

        info.setFilename(fileName);

        // Find sibling preview images using in-memory discovered image set
        String nameWithoutExt = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        String[] previewExts = {".png", ".jpg", ".jpeg", ".preview.png"};
        Path parent = file.getParent();
        if (parent != null && !imageFiles.isEmpty()) {
            for (String ext : previewExts) {
                Path preview = parent.resolve(nameWithoutExt + ext);
                if (imageFiles.contains(preview)) {
                    info.setPreviewPath(preview.toAbsolutePath().toString());
                    break;
                }
            }
        }

        double mb = sizeBytes / (1024.0 * 1024.0);
        info.setSize(String.format("%.2f MB", mb));
        info.setByteSize(sizeBytes);

        return info;
    }
}
