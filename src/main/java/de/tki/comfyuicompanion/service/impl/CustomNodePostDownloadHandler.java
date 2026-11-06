package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.service.ICustomNodePostDownloadHandler;
import de.tki.comfyuicompanion.service.SafePathValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Enterprise post-download handler that automatically detects ComfyUI custom nodes
 * inside downloaded archives (.zip), extracts them securely into ComfyUI's custom_nodes folder,
 * installs any required Python dependencies, and deploys bundled workflow JSON files.
 */
@Service
public class CustomNodePostDownloadHandler implements ICustomNodePostDownloadHandler {

    private static final Logger logger = LoggerFactory.getLogger(CustomNodePostDownloadHandler.class);

    private final ConfigService configService;
    private final SafePathValidator safePathValidator;
    private final CustomNodeInstaller customNodeInstaller;

    /**
     * Constructs a new CustomNodePostDownloadHandler with necessary service dependencies.
     *
     * @param configService       configuration service for ComfyUI and models paths
     * @param safePathValidator   validator ensuring path security and Zip Slip prevention
     * @param customNodeInstaller installer for automated Python requirements provisioning
     */
    @Autowired
    public CustomNodePostDownloadHandler(
            ConfigService configService,
            SafePathValidator safePathValidator,
            @Autowired(required = false) CustomNodeInstaller customNodeInstaller) {
        this.configService = configService;
        this.safePathValidator = safePathValidator != null ? safePathValidator : new SafePathValidator();
        this.customNodeInstaller = customNodeInstaller;
    }

    @Override
    public boolean isCustomNodeArchive(Path archivePath) {
        if (archivePath == null || !Files.isRegularFile(archivePath)) {
            return false;
        }
        String fileName = archivePath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".zip")) {
            return false;
        }

        try (ZipFile zipFile = new ZipFile(archivePath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/').toLowerCase(Locale.ROOT);
                if (name.endsWith("__init__.py")) {
                    return true;
                }
            }
        } catch (Exception e) {
            logger.debug("Failed inspecting archive {} for custom node entries: {}", archivePath, e.getMessage());
        }
        return false;
    }

    @Override
    public boolean processDownloadedArchive(Path targetFile, Consumer<String> statusConsumer) {
        Consumer<String> out = statusConsumer != null ? statusConsumer : s -> {};
        if (targetFile == null || !Files.isRegularFile(targetFile)) {
            return false;
        }

        if (!isCustomNodeArchive(targetFile)) {
            return false;
        }

        if (configService == null) {
            out.accept("⚠️ Configuration service is unavailable. Skipping auto-installation.");
            return false;
        }

        String comfyPath = configService.getComfyUIPath();
        if (comfyPath == null || comfyPath.isBlank()) {
            out.accept("⚠️ ComfyUI path is not configured. Skipping custom node auto-installation.");
            return false;
        }

        Path customNodesDir = Paths.get(comfyPath, "custom_nodes");
        try {
            Files.createDirectories(customNodesDir);
            safePathValidator.validateWithinBase(customNodesDir, customNodesDir);

            try (ZipFile zipFile = new ZipFile(targetFile.toFile())) {
                String nodePrefix = findNodePrefix(zipFile);
                String folderName = resolveTargetFolderName(targetFile, nodePrefix);
                Path targetDir = customNodesDir.resolve(folderName);
                safePathValidator.validateWithinBase(customNodesDir, targetDir);

                out.accept("📦 Auto-installing custom node [" + folderName + "] to ComfyUI...");
                logger.info("Extracting custom node archive {} to {}", targetFile, targetDir);

                extractEntries(zipFile, targetDir, nodePrefix);

                // Patch legacy node imports for ComfyUI 0.42+ compatibility (e.g. SeargeSDXL)
                patchLegacyCustomNodeCompatibility(targetDir);

                // Extract bundled workflows into ComfyUI workflow storage
                extractBundledWorkflows(zipFile, comfyPath, out);

                // Install Python dependencies if requirements.txt exists
                if (customNodeInstaller != null && Files.exists(targetDir.resolve("requirements.txt"))) {
                    out.accept("🐍 Installing dependencies for " + folderName + "...");
                    customNodeInstaller.installPythonRequirements(targetDir, null, out);
                }

                out.accept("✅ Custom node [" + folderName + "] installed successfully.");
                logger.info("Successfully installed custom node {} into {}", folderName, targetDir);
                return true;
            }
        } catch (Exception e) {
            logger.error("Failed to automatically install custom node from archive {}: {}", targetFile, e.getMessage(), e);
            out.accept("⚠️ Auto-installation failed: " + e.getMessage());
            return false;
        }
    }

    private String findNodePrefix(ZipFile zipFile) {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName().replace('\\', '/');
            if (name.toLowerCase(Locale.ROOT).endsWith("__init__.py")) {
                int slash = name.lastIndexOf("__init__.py");
                if (slash > 0) {
                    String sub = name.substring(0, slash);
                    if (sub.endsWith("/")) {
                        sub = sub.substring(0, sub.length() - 1);
                    }
                    int firstSlash = sub.indexOf('/');
                    return firstSlash > 0 ? sub.substring(0, firstSlash) : sub;
                }
            }
        }
        return null;
    }

    private String resolveTargetFolderName(Path archivePath, String nodePrefix) {
        if (nodePrefix != null && !nodePrefix.isBlank()) {
            return nodePrefix;
        }
        String fileName = archivePath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;
        return baseName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private void extractEntries(ZipFile zipFile, Path targetDir, String nodePrefix) throws Exception {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String rawName = entry.getName().replace('\\', '/');
            String relPath = rawName;

            if (nodePrefix != null && relPath.startsWith(nodePrefix + "/")) {
                relPath = relPath.substring(nodePrefix.length() + 1);
            } else if (nodePrefix != null && relPath.equals(nodePrefix)) {
                continue;
            }

            if (relPath.isBlank()) {
                continue;
            }

            Path destPath = targetDir.resolve(relPath).normalize();
            safePathValidator.validateWithinBase(targetDir, destPath);

            if (entry.isDirectory()) {
                Files.createDirectories(destPath);
            } else {
                if (destPath.getParent() != null) {
                    Files.createDirectories(destPath.getParent());
                }
                try (InputStream in = zipFile.getInputStream(entry)) {
                    Files.copy(in, destPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void extractBundledWorkflows(ZipFile zipFile, String comfyPath, Consumer<String> out) {
        Path userWorkflowsDir = Paths.get(comfyPath, "user", "default", "workflows");
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName().replace('\\', '/');
            if (!entry.isDirectory() && name.toLowerCase(Locale.ROOT).endsWith(".json")
                    && (name.toLowerCase(Locale.ROOT).contains("workflow") || name.startsWith("workflow/"))) {
                try {
                    Files.createDirectories(userWorkflowsDir);
                    String jsonFileName = Paths.get(name).getFileName().toString();
                    Path targetWf = userWorkflowsDir.resolve(jsonFileName);
                    safePathValidator.validateWithinBase(userWorkflowsDir, targetWf);

                    try (InputStream in = zipFile.getInputStream(entry)) {
                        Files.copy(in, targetWf, StandardCopyOption.REPLACE_EXISTING);
                    }
                    out.accept("📑 Extracted workflow: " + jsonFileName + " -> ComfyUI workflows");
                } catch (Exception e) {
                    logger.debug("Failed to extract workflow JSON {}: {}", name, e.getMessage());
                }
            }
        }
    }

    /**
     * Patches legacy custom nodes (such as SeargeSDXL) to remain compatible with
     * ComfyUI 0.42+ where sampler helper methods moved from comfy.sample to comfy.sampler_helpers.
     *
     * @param targetDir custom node installation directory
     */
    private void patchLegacyCustomNodeCompatibility(Path targetDir) {
        if (targetDir == null || !Files.isDirectory(targetDir)) {
            return;
        }

        try {
            Path initPy = targetDir.resolve("__init__.py");
            if (Files.isRegularFile(initPy)) {
                String content = Files.readString(initPy);
                if (!content.contains("sampler_helpers")) {
                    String shim = "# --- ComfyUI Companion Legacy Compatibility Layer ---\n" +
                            "try:\n" +
                            "    import comfy.sample\n" +
                            "    import comfy.sampler_helpers as _cmfc_sh\n" +
                            "    for _cmfc_attr in ['prepare_mask', 'get_additional_models', 'convert_cond', 'cleanup_additional_models']:\n" +
                            "        if not hasattr(comfy.sample, _cmfc_attr) and hasattr(_cmfc_sh, _cmfc_attr):\n" +
                            "            setattr(comfy.sample, _cmfc_attr, getattr(_cmfc_sh, _cmfc_attr))\n" +
                            "except Exception:\n" +
                            "    pass\n" +
                            "# ----------------------------------------------------\n\n";
                    Files.writeString(initPy, shim + content);
                    logger.info("Applied legacy compatibility shim to {}", initPy);
                }
            }

            // Also check for specific imports like 'from comfy.sample import prepare_mask'
            try (Stream<Path> walk = Files.walk(targetDir, 5)) {
                walk.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".py"))
                        .forEach(pyFile -> {
                            try {
                                String pyContent = Files.readString(pyFile);
                                if (pyContent.contains("from comfy.sample import prepare_mask")) {
                                    String patched = pyContent.replace(
                                            "from comfy.sample import prepare_mask",
                                            "try:\n    from comfy.sample import prepare_mask\nexcept ImportError:\n    from comfy.sampler_helpers import prepare_mask"
                                    );
                                    Files.writeString(pyFile, patched);
                                    logger.info("Patched prepare_mask import in legacy node file {}", pyFile);
                                }
                            } catch (Exception ex) {
                                logger.debug("Could not inspect/patch python file {}: {}", pyFile, ex.getMessage());
                            }
                        });
            }
        } catch (Exception e) {
            logger.warn("Could not apply legacy compatibility patches to {}: {}", targetDir, e.getMessage());
        }
    }
}
