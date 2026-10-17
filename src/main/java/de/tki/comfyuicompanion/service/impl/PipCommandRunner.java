package de.tki.comfyuicompanion.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Component managing execution of Python pip commands, CUDA detection for PyTorch,
 * and requirements installation across native and WSL environments.
 */
@Component
public class PipCommandRunner {
    private static final Logger logger = LoggerFactory.getLogger(PipCommandRunner.class);

    private final ProcessTracker processTracker;

    public PipCommandRunner() {
        this(null);
    }

    @Autowired
    public PipCommandRunner(@Autowired(required = false) ProcessTracker processTracker) {
        this.processTracker = processTracker != null ? processTracker : new ProcessTracker();
    }

    /**
     * Executes a pip command with the given arguments using the specified Python binary.
     *
     * @param pythonExe path to python executable
     * @param workingDir working directory
     * @param progressCallback progress output sink
     * @param args command arguments
     * @return process exit code
     */
    public int runPipCommand(Path pythonExe, Path workingDir, Consumer<String> progressCallback, String... args) {
        try {
            List<String> command = new ArrayList<>();
            command.add(pythonExe.toString());
            command.add("-m");
            command.add("pip");
            for (String arg : args) {
                command.add(arg);
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            if (workingDir != null) {
                pb.directory(workingDir.toFile());
            }
            pb.redirectErrorStream(true);

            Process p = processTracker.start(pb);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    progressCallback.accept("[pip] " + line);
                }
            }
            return p.waitFor();
        } catch (Exception e) {
            progressCallback.accept("❌ Error running pip command: " + e.getMessage());
            logger.error("Error running pip command: {}", e.getMessage(), e);
            return -1;
        }
    }

    /**
     * Checks if WSL is supported and enabled.
     *
     * @return true if WSL available
     */
    public boolean isWslAvailable() {
        return false; // Disabled by user request: Do not use WSL.
    }

    /**
     * Runs a pip command inside WSL.
     *
     * @param workingDir working directory
     * @param progressCallback progress sink
     * @param args command arguments
     * @return exit code
     */
    public int runWslPipCommand(Path workingDir, Consumer<String> progressCallback, String... args) {
        try {
            List<String> command = new ArrayList<>();
            command.add("wsl");
            command.add("python3");
            command.add("-m");
            command.add("pip");
            for (String arg : args) {
                command.add(arg);
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            if (workingDir != null) {
                pb.directory(workingDir.toFile());
            }
            pb.redirectErrorStream(true);

            Process p = processTracker.start(pb);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    progressCallback.accept("[wsl-pip] " + line);
                }
            }
            return p.waitFor();
        } catch (Exception e) {
            progressCallback.accept("❌ Error running WSL pip command: " + e.getMessage());
            logger.error("Error running WSL pip command: {}", e.getMessage(), e);
            return -1;
        }
    }

    /**
     * Runs an arbitrary command inside WSL.
     *
     * @param progressCallback progress sink
     * @param args arguments
     * @return exit code
     */
    public int runWslCommand(Consumer<String> progressCallback, String... args) {
        try {
            List<String> command = new ArrayList<>();
            command.add("wsl");
            for (String arg : args) {
                command.add(arg);
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Process p = processTracker.start(pb);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    progressCallback.accept("[wsl] " + line);
                }
            }
            return p.waitFor();
        } catch (Exception e) {
            progressCallback.accept("❌ Error running WSL command: " + e.getMessage());
            logger.error("Error running WSL command: {}", e.getMessage(), e);
            return -1;
        }
    }

    /**
     * Detects NVIDIA GPU compute capability and installs matching CUDA PyTorch wheels on Windows.
     *
     * @param pythonExe path to python executable
     * @param comfyDir path to comfyui root directory
     * @param progressCallback progress sink
     */
    public void installPyTorchCudaIfAvailable(Path pythonExe, Path comfyDir, Consumer<String> progressCallback) {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        if (!isWindows) {
            return;
        }

        boolean hasNvidia = false;
        double maxComputeCap = 0.0;
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"nvidia-smi", "--query-gpu=compute_cap", "--format=csv,noheader"});
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        hasNvidia = true;
                        try {
                            double cap = Double.parseDouble(line.trim());
                            if (cap > maxComputeCap) {
                                maxComputeCap = cap;
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
            p.waitFor();
        } catch (Exception ignored) {}

        if (hasNvidia) {
            String cudaVersion;
            String cudaLogName;
            if (maxComputeCap >= 10.0) {
                cudaVersion = "cu128";
                cudaLogName = "CUDA 12.8 (Blackwell RTX 50-series / Modern)";
            } else if (maxComputeCap >= 8.9) {
                cudaVersion = "cu124";
                cudaLogName = "CUDA 12.4 (Modern)";
            } else if (maxComputeCap >= 5.0) {
                cudaVersion = "cu121";
                cudaLogName = "CUDA 12.1 (Standard)";
            } else {
                progressCallback.accept("🔍 NVIDIA GPU detected, but Compute Capability (" + maxComputeCap + ") is not supported by modern PyTorch CUDA (requires >= 5.0). Using default installation.");
                return;
            }

            progressCallback.accept("🔍 NVIDIA GPU detected (" + cudaLogName + "). Preparing PyTorch installation...");
            progressCallback.accept("🗑️ Uninstalling old PyTorch packages (torch, torchvision, torchaudio) to avoid conflicts...");
            runPipCommand(pythonExe, comfyDir, progressCallback, "uninstall", "torch", "torchvision", "torchaudio", "-y");

            progressCallback.accept("📥 Installing PyTorch with " + cudaLogName + " support (this may take a few minutes)...");
            int exitCode = runPipCommand(pythonExe, comfyDir, progressCallback,
                    "install", "torch", "torchvision", "torchaudio",
                    "--index-url", "https://download.pytorch.org/whl/" + cudaVersion,
                    "--no-warn-script-location"
            );
            if (exitCode == 0) {
                progressCallback.accept("✅ PyTorch with " + cudaLogName + " successfully installed.");
            } else {
                progressCallback.accept("⚠️ PyTorch CUDA installation finished with code " + exitCode + ". Trying default installation.");
            }
        } else {
            progressCallback.accept("🔍 No NVIDIA GPU detected or nvidia-smi not available. Using default installation.");
        }
    }

    /**
     * Fixes WSL system packages and python dependencies.
     *
     * @param comfyDir ComfyUI directory
     * @param progressCallback progress sink
     */
    public void fixWslDependencies(Path comfyDir, Consumer<String> progressCallback) {
        if (!isWslAvailable()) {
            progressCallback.accept("❌ WSL is not available or not enabled on this system.\n");
            return;
        }

        progressCallback.accept("⚙️ Starting WSL Dependency Fix...\n");
        progressCallback.accept("📦 Updating system package lists in WSL (wsl sudo apt-get update)... (this may take a moment)\n");
        int updateExit = runWslCommand(progressCallback, "sudo", "apt-get", "update");
        if (updateExit != 0) {
            progressCallback.accept("⚠️ wsl sudo apt-get update returned exit code: " + updateExit + "\n");
        }

        progressCallback.accept("📦 Installing libsndfile1 inside WSL (wsl sudo apt-get install -y libsndfile1 libsndfile1-dev)... (this may take a moment)\n");
        int installExit = runWslCommand(progressCallback, "sudo", "apt-get", "install", "-y", "libsndfile1", "libsndfile1-dev");
        if (installExit == 0) {
            progressCallback.accept("✅ libsndfile1 and libsndfile1-dev successfully installed in WSL.\n");
        } else {
            progressCallback.accept("⚠️ wsl sudo apt-get install returned exit code: " + installExit + ". If sudo requires a password, please configure WSL to be passwordless for sudo, or run it manually inside WSL.\n");
        }

        if (comfyDir == null) {
            progressCallback.accept("⚠️ ComfyUI path not configured. Skipping Python dependencies installation.\n");
            return;
        }

        progressCallback.accept("🐍 Upgrading pip inside WSL...\n");
        runWslPipCommand(comfyDir, progressCallback, "install", "--upgrade", "pip", "--break-system-packages");

        Path reqFile = comfyDir.resolve("requirements.txt");
        if (Files.exists(reqFile)) {
            progressCallback.accept("🚀 Installing ComfyUI core dependencies in WSL...\n");
            runWslPipCommand(comfyDir, progressCallback, "install", "-r", "requirements.txt", "--break-system-packages");
        }

        Path managerReq = comfyDir.resolve("manager_requirements.txt");
        if (Files.exists(managerReq)) {
            progressCallback.accept("🚀 Installing ComfyUI-Manager dependencies in WSL...\n");
            runWslPipCommand(comfyDir, progressCallback, "install", "-r", "manager_requirements.txt", "--break-system-packages");
        }

        Path kokoroTtsDir = comfyDir.resolve("custom_nodes").resolve("ComfyUI-KokoroTTS");
        Path kokoroReqFile = kokoroTtsDir.resolve("requirements.txt");
        if (Files.exists(kokoroTtsDir)) {
            progressCallback.accept("🚀 Installing ComfyUI-KokoroTTS dependencies in WSL...\n");
            if (Files.exists(kokoroReqFile)) {
                runWslPipCommand(kokoroTtsDir, progressCallback, "install", "-r", "requirements.txt", "--break-system-packages");
            } else {
                runWslPipCommand(kokoroTtsDir, progressCallback, "install", "kokoro-onnx", "soundfile", "--break-system-packages");
            }
        }

        Path videoHelperSuiteDir = comfyDir.resolve("custom_nodes").resolve("ComfyUI-Video-Helper-Suite");
        Path videoHelperReq = videoHelperSuiteDir.resolve("requirements.txt");
        if (Files.exists(videoHelperSuiteDir)) {
            progressCallback.accept("🚀 Installing ComfyUI-Video-Helper-Suite dependencies in WSL...\n");
            if (Files.exists(videoHelperReq)) {
                runWslPipCommand(videoHelperSuiteDir, progressCallback, "install", "-r", "requirements.txt", "--break-system-packages");
            }
        }

        progressCallback.accept("✅ WSL dependencies fix completed!\n");
    }

    /**
     * Installs ComfyUI requirements, CUDA PyTorch packages, and node dependencies.
     *
     * @param pythonExe Python binary path
     * @param comfyDir ComfyUI root directory
     * @param progressCallback progress sink
     */
    public void installRequirements(Path pythonExe, Path comfyDir, Consumer<String> progressCallback) {
        try {
            installPyTorchCudaIfAvailable(pythonExe, comfyDir, progressCallback);

            Path reqFile = comfyDir.resolve("requirements.txt");
            if (Files.exists(reqFile)) {
                progressCallback.accept("🚀 Installing ComfyUI dependencies (pip install -r requirements.txt)...");
                int exitCode = runPipCommand(pythonExe, comfyDir, progressCallback, "install", "-r", "requirements.txt", "--no-warn-script-location");
                if (exitCode == 0) {
                    progressCallback.accept("✅ ComfyUI dependencies successfully installed.");
                } else {
                    progressCallback.accept("⚠️ pip finished with code " + exitCode + " on requirements.txt.");
                }
            } else {
                progressCallback.accept("⚠️ No requirements.txt found.");
            }

            Path managerReq = comfyDir.resolve("manager_requirements.txt");
            if (Files.exists(managerReq)) {
                progressCallback.accept("🚀 Installing ComfyUI-Manager dependencies (pip install -r manager_requirements.txt)...");
                int exitCode = runPipCommand(pythonExe, comfyDir, progressCallback, "install", "-r", "manager_requirements.txt", "--no-warn-script-location");
                if (exitCode == 0) {
                    progressCallback.accept("✅ ComfyUI-Manager dependencies successfully installed.");
                } else {
                    progressCallback.accept("⚠️ pip finished with code " + exitCode + " on manager_requirements.txt.");
                }
            }

            Path customManagerReq = comfyDir.resolve("custom_nodes").resolve("ComfyUI-Manager").resolve("requirements.txt");
            if (Files.exists(customManagerReq)) {
                progressCallback.accept("🚀 Installing custom_nodes/ComfyUI-Manager dependencies (pip install -r ...)...");
                int exitCode = runPipCommand(pythonExe, customManagerReq.getParent(), progressCallback, "install", "-r", "requirements.txt", "--no-warn-script-location");
                if (exitCode == 0) {
                    progressCallback.accept("✅ custom_nodes/ComfyUI-Manager dependencies successfully installed.");
                } else {
                    progressCallback.accept("⚠️ pip finished with code " + exitCode + " on custom_nodes/ComfyUI-Manager/requirements.txt.");
                }
            }

            progressCallback.accept("🚀 Installing pygit2 for Git support in embedded Python...");
            int pygit2Exit = runPipCommand(pythonExe, comfyDir, progressCallback, "install", "pygit2", "--no-warn-script-location");
            if (pygit2Exit == 0) {
                progressCallback.accept("✅ pygit2 successfully installed.");
            } else {
                progressCallback.accept("⚠️ pip finished with code " + pygit2Exit + " on pygit2.");
            }

            Path videoHelperReq = comfyDir.resolve("custom_nodes").resolve("ComfyUI-Video-Helper-Suite").resolve("requirements.txt");
            if (Files.exists(videoHelperReq)) {
                progressCallback.accept("🚀 Installing custom_nodes/ComfyUI-Video-Helper-Suite dependencies...");
                int exitCode = runPipCommand(pythonExe, videoHelperReq.getParent(), progressCallback, "install", "-r", "requirements.txt", "--no-warn-script-location");
                if (exitCode == 0) {
                    progressCallback.accept("✅ custom_nodes/ComfyUI-Video-Helper-Suite dependencies successfully installed.");
                } else {
                    progressCallback.accept("⚠️ pip finished with code " + exitCode + " on custom_nodes/ComfyUI-Video-Helper-Suite/requirements.txt.");
                }
            }

            if (isWslAvailable()) {
                progressCallback.accept("🚀 WSL support detected on system. Setting up WSL Python environment...");
                if (Files.exists(reqFile)) {
                    runWslPipCommand(comfyDir, progressCallback, "install", "-r", "requirements.txt", "--break-system-packages");
                }
                if (Files.exists(managerReq)) {
                    runWslPipCommand(comfyDir, progressCallback, "install", "-r", "manager_requirements.txt", "--break-system-packages");
                }
                if (Files.exists(customManagerReq)) {
                    runWslPipCommand(customManagerReq.getParent(), progressCallback, "install", "-r", "requirements.txt", "--break-system-packages");
                }
                if (Files.exists(videoHelperReq)) {
                    runWslPipCommand(videoHelperReq.getParent(), progressCallback, "install", "-r", "requirements.txt", "--break-system-packages");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error installing dependencies: " + e.getMessage(), e);
        }
    }
}
