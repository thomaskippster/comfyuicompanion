package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.util.PlatformUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Service managing path resolution, directories, and folder structures
 * for ComfyUI models, outputs, inputs, and archives.
 */
@Service
public class ConfigDirectoryManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigDirectoryManager.class);

    /**
     * Resolves the extra ComfyUI data directory.
     *
     * @param settings settings JSON
     * @param isTestEnvironment whether the runtime is in a test environment
     * @return absolute path to extra ComfyUI folder
     */
    public String resolveExtraComfyUIPath(JSONObject settings, boolean isTestEnvironment) {
        String path = settings.optString("extra_comfyui_path", "");
        if (path.isEmpty()) {
            if (isTestEnvironment) {
                return "";
            }
            String envPath = System.getenv("COMFYUI_DATA_PATH");
            if (envPath != null && !envPath.isBlank()) {
                return envPath.trim();
            }
            return Paths.get(System.getProperty("user.home"), "comfyuidata").toAbsolutePath().toString();
        }
        return path;
    }

    /**
     * Resolves the models directory from extra path, configured custom path, or ComfyUI root.
     *
     * @param settings settings JSON
     * @param extraComfyUIPath resolved extra ComfyUI path
     * @param comfyUIRoot resolved ComfyUI root directory
     * @return absolute path to models directory or empty string
     */
    public String resolveModelsPath(JSONObject settings, String extraComfyUIPath, String comfyUIRoot) {
        if (extraComfyUIPath != null && !extraComfyUIPath.isEmpty()) {
            Path p = Paths.get(extraComfyUIPath);
            if (p.getFileName() != null && p.getFileName().toString().equalsIgnoreCase("models")) {
                return p.toAbsolutePath().toString();
            }
            return p.resolve("models").toAbsolutePath().toString();
        }
        String customPath = settings.optString("models_path", "");
        if (!customPath.isEmpty()) {
            Path p = Paths.get(customPath);
            if (p.getFileName() != null && p.getFileName().toString().equalsIgnoreCase("models")) {
                return p.toAbsolutePath().toString();
            }
            return p.resolve("models").toAbsolutePath().toString();
        }
        if (comfyUIRoot != null && !comfyUIRoot.isEmpty()) {
            return Paths.get(comfyUIRoot).resolve("models").toAbsolutePath().toString();
        }
        return "";
    }

    /**
     * Resolves the detailed input directory for ComfyUI.
     *
     * @param extraComfyUIPath resolved extra ComfyUI path
     * @return absolute path to input directory
     */
    public String resolveInputDetailDir(String extraComfyUIPath) {
        return Paths.get(extraComfyUIPath).resolve("input").toAbsolutePath().toString();
    }

    /**
     * Ensures mandatory models, input, and output subdirectories exist in the extra ComfyUI directory.
     *
     * @param extraComfyUIPath base extra directory
     */
    public void ensureExtraComfyUIDirectories(String extraComfyUIPath) {
        try {
            if (extraComfyUIPath != null && !extraComfyUIPath.isEmpty()) {
                Path base = Paths.get(extraComfyUIPath);
                Path models = base.resolve("models");
                Path input = base.resolve("input");
                Path output = base.resolve("output");

                if (!Files.exists(models)) Files.createDirectories(models);
                if (!Files.exists(input)) Files.createDirectories(input);
                if (!Files.exists(output)) Files.createDirectories(output);
            }
        } catch (Exception e) {
            logger.warn("Failed to create extra ComfyUI directories: {}", e.getMessage(), e);
        }
    }

    /**
     * Resolves the models archive path.
     *
     * @param settings settings JSON
     * @param modelsPath resolved models path
     * @param pathResolver path resolver utility
     * @return absolute archive path
     */
    public String resolveArchivePath(JSONObject settings, String modelsPath, PathResolver pathResolver) {
        String path = settings.optString("archive_path", "");
        if (path.isEmpty()) {
            String userHome = System.getProperty("user.home");
            if (PlatformUtils.isWindows()) {
                return userHome + "\\ComfyUI-Archive";
            } else {
                return userHome + "/ComfyUI-Archive";
            }
        }
        Path modelsPathObj = Paths.get(modelsPath);
        return pathResolver.resolveArchivePath(modelsPathObj, path).toString();
    }

    /**
     * Resolves the output directory where generated media files are written.
     *
     * @param extraComfyUIPath resolved extra path
     * @param launchCmd configured launch command
     * @param modelsPath resolved models path
     * @param comfyUIRoot resolved ComfyUI root directory
     * @return absolute output directory path
     */
    public String resolveOutputDir(String extraComfyUIPath, String launchCmd, String modelsPath, String comfyUIRoot) {
        if (extraComfyUIPath != null && !extraComfyUIPath.isEmpty()) {
            return Paths.get(extraComfyUIPath).resolve("output").toAbsolutePath().toString();
        }

        if (launchCmd != null && !launchCmd.isEmpty()) {
            try {
                List<String> parsedCmd = PlatformUtils.parseCommandLine(launchCmd);
                for (int i = 0; i < parsedCmd.size() - 1; i++) {
                    if (parsedCmd.get(i).equals("--output-directory")) {
                        Path p = Paths.get(parsedCmd.get(i + 1));
                        if (Files.exists(p) && Files.isDirectory(p)) {
                            return p.toAbsolutePath().toString();
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        if (modelsPath != null && !modelsPath.isEmpty()) {
            try {
                Path p = Paths.get(modelsPath);
                if (p.getParent() != null) {
                    Path siblingOutput = p.getParent().resolve("output");
                    if (Files.exists(siblingOutput) && Files.isDirectory(siblingOutput)) {
                        return siblingOutput.toAbsolutePath().toString();
                    }
                }
            } catch (Exception ignored) {}
        }

        if (comfyUIRoot != null && !comfyUIRoot.isEmpty()) {
            return Paths.get(comfyUIRoot).resolve("output").toAbsolutePath().toString();
        }

        return Paths.get("output").toAbsolutePath().toString();
    }

    /**
     * Resolves the ComfyUI installation root directory.
     *
     * @param settings settings JSON
     * @param isTestEnvironment whether running in test environment
     * @return ComfyUI root directory path
     */
    public String resolveComfyUIPath(JSONObject settings, boolean isTestEnvironment) {
        String path = settings.optString("comfyui_path", "");
        if (path.isEmpty()) {
            if (isTestEnvironment) {
                return "";
            }
            String userHome = System.getProperty("user.home");
            File companionPath = new File(userHome, ".comfyui-companion/ComfyUI");
            if (companionPath.exists() && companionPath.isDirectory()) {
                return companionPath.getAbsolutePath();
            }
            if (PlatformUtils.isWindows()) {
                return userHome + "\\ComfyUI";
            } else {
                return userHome + "/ComfyUI";
            }
        }
        return path;
    }

    /**
     * Checks whether the application is running in portable mode (local configuration files in working directory).
     *
     * @param vaultFile  name of the vault file
     * @param configFile name of the settings file
     * @return {@code true} if portable mode is active
     */
    public boolean isPortableMode(String vaultFile, String configFile) {
        File userDir = new File(System.getProperty("user.dir"));
        return new File(userDir, ".portable").exists()
                || new File(userDir, vaultFile).exists()
                || new File(userDir, configFile).exists();
    }

    /**
     * Resolves the application data root path, supporting test overrides, portable mode, and unified home directory.
     *
     * @param vaultFile  name of the vault file
     * @param configFile name of the settings file
     * @return absolute path to application data directory
     */
    public String resolveAppDataPath(String vaultFile, String configFile) {
        String override = System.getProperty("comfyuicompanion.appdata");
        if (override != null && !override.isEmpty()) {
            return override;
        }
        String command = System.getProperty("sun.java.command", "");
        if (command.contains("surefire") || command.contains("junit") || command.contains("testng")) {
            return new File(System.getProperty("java.io.tmpdir"), "comfy_test_appdata_safe").getAbsolutePath();
        }
        if (!isPortableMode(vaultFile, configFile)) {
            Path homeVault = Paths.get(System.getProperty("user.home"), ".comfyui-companion", vaultFile);
            if (Files.exists(homeVault)) {
                return homeVault.getParent().toAbsolutePath().toString();
            }
        }
        return System.getProperty("user.dir");
    }
}

