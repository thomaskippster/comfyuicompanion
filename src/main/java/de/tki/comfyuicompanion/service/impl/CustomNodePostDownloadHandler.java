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
}
