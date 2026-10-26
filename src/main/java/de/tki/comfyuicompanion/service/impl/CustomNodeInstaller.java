package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.service.SafePathValidator;
import de.tki.comfyuicompanion.service.provisioning.CustomNodeResolverService.CustomNodePackage;
import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Service component responsible for securely cloning custom node Git repositories
 * into ComfyUI's custom_nodes folder and executing pip installation for requirements.
 */
@Component
public class CustomNodeInstaller {

    private static final Logger logger = LoggerFactory.getLogger(CustomNodeInstaller.class);

    private final ConfigService configService;
    private final SafePathValidator safePathValidator;
    private final ProcessTracker processTracker;

    @Autowired
    public CustomNodeInstaller(ConfigService configService,
                               SafePathValidator safePathValidator,
                               @Autowired(required = false) ProcessTracker processTracker) {
        this.configService = configService;
        this.safePathValidator = safePathValidator != null ? safePathValidator : new SafePathValidator();
        this.processTracker = processTracker;
    }

    /**
     * Installs a single custom node package and installs its python dependencies.
     *
     * @param pkg custom node package definition
     * @param progress live progress consumer
     * @return true if installation succeeded
     */
    public boolean installPackage(CustomNodePackage pkg, Consumer<String> progress) {
        Objects.requireNonNull(pkg, "CustomNodePackage cannot be null");
        Consumer<String> out = progress != null ? progress : s -> {};

        if (configService == null) {
            out.accept("❌ Configuration service is unavailable.");
            return false;
        }

        String comfyPath = configService.getComfyUIPath();
        if (comfyPath == null || comfyPath.isBlank()) {
            out.accept("❌ ComfyUI path is not configured.");
            return false;
        }

        Path customNodesDir = Paths.get(comfyPath, "custom_nodes");
        try {
            Files.createDirectories(customNodesDir);
            safePathValidator.validateWithinBase(customNodesDir, customNodesDir);

            String folderName = extractFolderName(pkg.gitUrl(), pkg.packageName());
            Path targetDir = customNodesDir.resolve(folderName);
            safePathValidator.validateWithinBase(customNodesDir, targetDir);

            if (Files.exists(targetDir)) {
                out.accept("ℹ️ Custom node directory '" + folderName + "' already exists. Skipping clone.");
            } else {
                out.accept("📦 Cloning " + pkg.gitUrl() + " into " + folderName + "...");
                logger.info("Cloning {} to {}", pkg.gitUrl(), targetDir);

                try (Git git = Git.cloneRepository()
                        .setURI(pkg.gitUrl())
                        .setDirectory(targetDir.toFile())
                        .call()) {
                    out.accept("✅ Repository cloned successfully.");
                }
            }

            // Install Python requirements
            installPythonRequirements(targetDir, pkg.pipDependencies(), out);
            out.accept("🎉 Successfully finished setup for " + pkg.packageName() + ".\n");
            return true;
        } catch (Exception e) {
            logger.error("Failed to install custom node package {}: {}", pkg.packageName(), e.getMessage(), e);
            out.accept("❌ Failed to install " + pkg.packageName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Installs Python requirements for an existing custom node directory using ComfyUI's configured Python environment.
     *
     * @param targetDir       custom node directory containing requirements.txt
     * @param pipDependencies additional explicit pip package dependencies (optional)
     * @param out             progress feedback consumer
     */
    public void installPythonRequirements(Path targetDir, List<String> pipDependencies, Consumer<String> out) {
        String pythonPath = configService.getPythonPath();
        if (pythonPath == null || pythonPath.isBlank() || !new File(pythonPath).exists()) {
            out.accept("⚠️ Python executable not configured or not found. Skipping pip install.");
            return;
        }

        Path reqFile = targetDir.resolve("requirements.txt");
        if (Files.exists(reqFile)) {
            out.accept("🐍 Installing dependencies from requirements.txt...");
            runPip(pythonPath, List.of("install", "-r", reqFile.toAbsolutePath().toString()), targetDir, out);
        }

        if (pipDependencies != null && !pipDependencies.isEmpty()) {
            out.accept("🐍 Installing explicit package dependencies: " + String.join(", ", pipDependencies) + "...");
            List<String> args = new ArrayList<>();
            args.add("install");
            args.addAll(pipDependencies);
            runPip(pythonPath, args, targetDir, out);
        }
    }

    private boolean runPip(String pythonPath, List<String> pipArgs, Path workingDir, Consumer<String> out) {
        List<String> command = new ArrayList<>();
        command.add(pythonPath);
        command.add("-m");
        command.add("pip");
        command.addAll(pipArgs);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workingDir.toFile());
            pb.redirectErrorStream(true);

            Process process = processTracker != null ? processTracker.start(pb) : pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.accept("   " + line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                out.accept("⚠️ Pip completed with non-zero exit code: " + exitCode);
                return false;
            }
            return true;
        } catch (Exception e) {
            logger.warn("Pip execution error: {}", e.getMessage());
            out.accept("⚠️ Error running pip: " + e.getMessage());
            return false;
        }
    }

    private String extractFolderName(String gitUrl, String fallbackName) {
        if (gitUrl != null && !gitUrl.isBlank()) {
            String clean = gitUrl.trim();
            if (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
            if (clean.endsWith(".git")) clean = clean.substring(0, clean.length() - 4);
            int lastSlash = clean.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < clean.length() - 1) {
                return clean.substring(lastSlash + 1);
            }
        }
        return fallbackName != null && !fallbackName.isBlank() ? fallbackName : "custom_node_" + System.currentTimeMillis();
    }
}
