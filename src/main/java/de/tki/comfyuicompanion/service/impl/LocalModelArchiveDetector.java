package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.service.IConfigService;
import de.tki.comfyuicompanion.service.ILocalModelArchiveDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service discovering external and secondary model archive roots on local filesystem drives.
 * Identifies folders containing standard ComfyUI model subdirectories (e.g. E:/Comfy-Archive)
 * to ensure models residing outside primary paths are seamlessly detected.
 */
@Component
public class LocalModelArchiveDetector implements ILocalModelArchiveDetector {

    private static final Logger log = LoggerFactory.getLogger(LocalModelArchiveDetector.class);

    private static final Set<String> MODEL_SUBDIRECTORIES = Set.of(
            "checkpoints", "unet", "diffusion_models", "clip",
            "text_encoders", "vae", "loras", "upscale_models", "controlnet"
    );

    private final IConfigService configService;
    private final List<Path> cachedRoots = new CopyOnWriteArrayList<>();
    private long lastScanTime = 0L;
    private static final long CACHE_TTL_MS = 60_000L; // 1 minute cache

    @Autowired
    public LocalModelArchiveDetector(@org.springframework.context.annotation.Lazy @Autowired(required = false) IConfigService configService) {
        this.configService = configService;
    }

    @Override
    public List<Path> detectArchiveRoots() {
        long now = System.currentTimeMillis();
        if (now - lastScanTime < CACHE_TTL_MS && !cachedRoots.isEmpty()) {
            return new ArrayList<>(cachedRoots);
        }

        Set<Path> discovered = new LinkedHashSet<>();

        // 1. Configured models path
        if (configService != null && configService.getModelsPath() != null) {
            String p = configService.getModelsPath();
            if (!p.isBlank()) {
                Path path = Paths.get(p);
                if (Files.isDirectory(path)) {
                    discovered.add(path.toAbsolutePath().normalize());
                }
            }
        }

        // 2. Discover archives across system drives (e.g. C:, D:, E:, etc.)
        File[] roots = File.listRoots();
        if (roots != null) {
            for (File root : roots) {
                if (root == null || !root.exists()) continue;
                inspectDriveForArchiveRoots(root, discovered);
            }
        }

        // 3. User home directory
        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isBlank()) {
            inspectPathCandidates(Paths.get(userHome), discovered);
        }

        cachedRoots.clear();
        cachedRoots.addAll(discovered);
        lastScanTime = now;

        log.info("Discovered {} local model archive root(s): {}", discovered.size(), discovered);
        return new ArrayList<>(cachedRoots);
    }

    private void inspectDriveForArchiveRoots(File driveRoot, Set<Path> discovered) {
        try {
            File[] topLevel = driveRoot.listFiles();
            if (topLevel == null) return;

            for (File dir : topLevel) {
                if (!dir.isDirectory()) continue;
                String name = dir.getName().toLowerCase(Locale.ROOT);
                if (isIgnoredDirectory(name)) continue;

                if (isModelArchiveDirectory(dir.toPath())) {
                    discovered.add(dir.toPath().toAbsolutePath().normalize());
                } else {
                    Path modelsSub = dir.toPath().resolve("models");
                    if (Files.isDirectory(modelsSub) && isModelArchiveDirectory(modelsSub)) {
                        discovered.add(modelsSub.toAbsolutePath().normalize());
                    }
                    if (name.contains("ai") || name.contains("comfy") || name.contains("sd")) {
                        inspectSubdirectoriesForArchiveRoots(dir, discovered);
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("Skipping drive root inspect for {}: {}", driveRoot, ex.getMessage());
        }
    }

    private void inspectSubdirectoriesForArchiveRoots(File parent, Set<Path> discovered) {
        try {
            File[] children = parent.listFiles();
            if (children == null) return;
            for (File child : children) {
                if (!child.isDirectory()) continue;
                String name = child.getName().toLowerCase(Locale.ROOT);
                if (isIgnoredDirectory(name)) continue;
                if (isModelArchiveDirectory(child.toPath())) {
                    discovered.add(child.toPath().toAbsolutePath().normalize());
                } else {
                    Path modelsSub = child.toPath().resolve("models");
                    if (Files.isDirectory(modelsSub) && isModelArchiveDirectory(modelsSub)) {
                        discovered.add(modelsSub.toAbsolutePath().normalize());
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void inspectPathCandidates(Path base, Set<Path> discovered) {
        String[] candidateNames = {"ComfyUI/models", "models", "AI/models", "AI/comfyuidata/models", "comfyuidata/models", "Comfy-Archive"};
        for (String c : candidateNames) {
            Path candidate = base.resolve(c);
            if (Files.isDirectory(candidate) && isModelArchiveDirectory(candidate)) {
                discovered.add(candidate.toAbsolutePath().normalize());
            }
        }
    }

    private boolean isIgnoredDirectory(String lowerName) {
        return lowerName.startsWith("$") || lowerName.startsWith(".")
                || lowerName.contains("windows") || lowerName.contains("program files")
                || lowerName.contains("node_modules") || lowerName.contains("temp")
                || lowerName.contains("cache") || lowerName.contains("recycle");
    }

    private boolean isModelArchiveDirectory(Path dir) {
        if (!Files.isDirectory(dir)) return false;
        int matchCount = 0;
        try (var stream = Files.list(dir)) {
            for (Path child : (Iterable<Path>) stream::iterator) {
                if (Files.isDirectory(child)) {
                    String subName = child.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (MODEL_SUBDIRECTORIES.contains(subName)) {
                        matchCount++;
                        if (matchCount >= 2) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    @Override
    public List<Path> resolveArchiveDirectoriesForCategory(String folderName) {
        if (folderName == null || folderName.isBlank()) return Collections.emptyList();
        String targetLower = folderName.toLowerCase(Locale.ROOT);
        List<Path> results = new ArrayList<>();

        for (Path root : detectArchiveRoots()) {
            Path direct = root.resolve(folderName);
            if (Files.isDirectory(direct)) {
                results.add(direct);
            }
            // Check common aliases (e.g. clip vs text_encoders, unet vs diffusion_models)
            if (targetLower.equals("clip") || targetLower.equals("text_encoders")) {
                Path alt1 = root.resolve("text_encoders");
                Path alt2 = root.resolve("clip");
                if (Files.isDirectory(alt1) && !results.contains(alt1)) results.add(alt1);
                if (Files.isDirectory(alt2) && !results.contains(alt2)) results.add(alt2);
            } else if (targetLower.equals("unet") || targetLower.equals("diffusion_models")) {
                Path alt1 = root.resolve("diffusion_models");
                Path alt2 = root.resolve("unet");
                if (Files.isDirectory(alt1) && !results.contains(alt1)) results.add(alt1);
                if (Files.isDirectory(alt2) && !results.contains(alt2)) results.add(alt2);
            }
        }
        return results;
    }
}
