package de.tki.comfyuicompanion.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manages the installation, file extraction, directory clean-up,
 * and synchronization of the ComfyUI Companion bridge extension.
 */
public class ComfyBridgeManager {
    private static final Logger logger = LoggerFactory.getLogger(ComfyBridgeManager.class);

    private final ConfigService configService;
    private final PathResolver pathResolver;

    public ComfyBridgeManager(ConfigService configService, PathResolver pathResolver) {
        this.configService = configService;
        this.pathResolver = pathResolver;
    }

    public void installComfyUIBridge(String comfyPath, JDialog parentDialog) {
        Path inputPath = Paths.get(comfyPath);
        if (!Files.exists(inputPath)) {
            JOptionPane.showMessageDialog(parentDialog, "The provided path does not exist: " + comfyPath, "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Path customNodesDir = pathResolver.findCustomNodes(inputPath);

        if (customNodesDir == null) {
            String msg = "Could not find 'custom_nodes' folder in the selected directory.\n\n" +
                    "Please ensure you select the folder that contains 'custom_nodes' or the main ComfyUI folder.";
            JOptionPane.showMessageDialog(parentDialog, msg, "Installation Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String[] conflictingFiles = {"comfyui_to_downloader.py", "comfyui-model-downloader.py"};
        for (String conflict : conflictingFiles) {
            Path conflictFile = customNodesDir.resolve(conflict);
            try {
                if (Files.exists(conflictFile)) {
                    Files.delete(conflictFile);
                    logger.info("Cleaned up legacy script: " + conflictFile);
                }
            } catch (IOException ignored) {}
        }

        try (Stream<Path> walk = Files.walk(customNodesDir, 2)) {
            List<Path> oldDirs = walk
                    .filter(p -> Files.isDirectory(p) && (
                            p.getFileName().toString().equalsIgnoreCase("comfyui-model-downloader") ||
                                    p.getFileName().toString().equalsIgnoreCase("comfyui-companion") ||
                                    p.getFileName().toString().equalsIgnoreCase("comfyuicompanion") ||
                                    p.getFileName().toString().equalsIgnoreCase("comfyuidownloader") ||
                                    p.getFileName().toString().equalsIgnoreCase("comfymodeldownloader")
                    ))
                    .collect(Collectors.toList());

            for (Path oldDir : oldDirs) {
                logger.info("Removing existing bridge directory: " + oldDir);
                deleteDirectory(oldDir.toFile());
            }
        } catch (IOException e) {
            logger.error("Error during bridge cleanup: " + e.getMessage());
        }

        configService.setComfyUIPath(comfyPath);

        Path targetDir = customNodesDir.resolve("comfyuicompanion");

        try {
            Files.createDirectories(targetDir);

            extractResource("/comfyui-bridge/__init__.py", targetDir.resolve("__init__.py").toFile());
            Path webDir = targetDir.resolve("web");
            Files.createDirectories(webDir);
            extractResource("/comfyui-bridge/web/downloader.js", webDir.resolve("downloader.js").toFile());

            writeExtensionConfig(targetDir.toFile());
            configService.updateExtraModelPathsYaml();

            String successMsg = "Bridge successfully installed!\n\n" +
                    "Target directory: " + targetDir.toAbsolutePath() + "\n\n" +
                    "Please restart your ComfyUI server now to load the changes.";
            JOptionPane.showMessageDialog(parentDialog, successMsg, "Installation Successful", JOptionPane.INFORMATION_MESSAGE);
            if (parentDialog != null) parentDialog.dispose();

        } catch (IOException e) {
            logger.error("Error installing bridge: " + e.getMessage(), e);
            JOptionPane.showMessageDialog(parentDialog, "Failed to copy files: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void syncBridgeFiles() {
        String comfyPath = configService.getComfyUIPath();
        if (comfyPath == null || comfyPath.isEmpty()) return;

        Path comfyDir = Paths.get(comfyPath);
        if (!Files.exists(comfyDir)) return;

        Path customNodesDir = pathResolver.findCustomNodes(comfyDir);
        if (customNodesDir == null) return;

        Path targetDir = customNodesDir.resolve("comfyuicompanion");
        try {
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }
            extractResource("/comfyui-bridge/__init__.py", targetDir.resolve("__init__.py").toFile());
            Path webDir = targetDir.resolve("web");
            Files.createDirectories(webDir);
            extractResource("/comfyui-bridge/web/downloader.js", webDir.resolve("downloader.js").toFile());

            writeExtensionConfig(targetDir.toFile());
            configService.updateExtraModelPathsYaml();
            logger.info("[Bridge-Sync] Successfully synchronized latest bridge code and token.");
        } catch (IOException e) {
            logger.error("[Bridge-Sync] Failed to sync code: " + e.getMessage());
        }
    }

    public static void extractResource(String resourcePath, File destination) throws IOException {
        try (InputStream in = ComfyBridgeManager.class.getResourceAsStream(resourcePath);
             FileOutputStream out = new FileOutputStream(destination)) {
            if (in == null) {
                throw new IOException("Resource not found in classpath: " + resourcePath);
            }
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }

    public static File findCustomNodesDeep(File dir, int depth) {
        if (depth > 2 || dir == null || !dir.exists()) return null;
        File target = new File(dir, "custom_nodes");
        if (target.exists() && target.isDirectory()) {
            return target;
        }
        File[] files = dir.listFiles(File::isDirectory);
        if (files != null) {
            for (File sub : files) {
                File found = findCustomNodesDeep(sub, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    public void writeExtensionConfig(File dir) {
        try {
            File webDir = new File(dir, "web");
            if (!webDir.exists()) webDir.mkdirs();
            org.json.JSONObject config = new org.json.JSONObject();
            config.put("token", configService.getApiToken());
            Path configFile = new File(webDir, "config.json").toPath();
            Files.writeString(configFile, config.toString(4));
            logger.info("[Bridge-Sync] Successfully wrote config to: " + configFile.toAbsolutePath());
        } catch (Exception e) {
            logger.error("[Bridge-Sync] Failed to write config: " + e.getMessage(), e);
        }
    }

    public static void deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteDirectory(f);
                } else {
                    f.delete();
                }
            }
        }
        dir.delete();
    }
}
