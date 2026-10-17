package de.tki.comfyuicompanion.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Component managing the installation, updates, and dependency setup
 * for required ComfyUI custom node extensions.
 */
@Component
public class CustomNodesInstaller {
    private static final Logger logger = LoggerFactory.getLogger(CustomNodesInstaller.class);

    private final GitRepositoryManager gitRepositoryManager;
    private final PipCommandRunner pipRunner;

    public CustomNodesInstaller() {
        this(null, null);
    }

    @Autowired
    public CustomNodesInstaller(
            @Autowired(required = false) GitRepositoryManager gitRepositoryManager,
            @Autowired(required = false) PipCommandRunner pipRunner) {
        this.gitRepositoryManager = gitRepositoryManager != null ? gitRepositoryManager : new GitRepositoryManager();
        this.pipRunner = pipRunner != null ? pipRunner : new PipCommandRunner();
    }

    /**
     * Ensures ComfyUI-Video-Helper-Suite is cloned and dependencies are installed.
     *
     * @param comfyDir ComfyUI root directory
     * @param pythonExe Python binary path
     * @param progressCallback progress sink
     */
    public void ensureVideoHelperSuiteInstalled(Path comfyDir, Path pythonExe, Consumer<String> progressCallback) {
        Path videoHelperSuiteDir = comfyDir.resolve("custom_nodes").resolve("ComfyUI-Video-Helper-Suite");
        boolean ok = gitRepositoryManager.cloneOrUpdateRepository(
                "https://github.com/Kosinkadink/ComfyUI-VideoHelperSuite.git",
                videoHelperSuiteDir,
                "main",
                progressCallback
        );
        if (!ok) {
            progressCallback.accept("❌ Failed to clone or update Video-Helper-Suite.");
            return;
        }

        Path videoHelperReq = videoHelperSuiteDir.resolve("requirements.txt");
        if (Files.exists(videoHelperReq) && pythonExe != null) {
            progressCallback.accept("🚀 Installing custom_nodes/ComfyUI-Video-Helper-Suite dependencies...");
            int exitCode = pipRunner.runPipCommand(pythonExe, videoHelperReq.getParent(), progressCallback, "install", "-r", "requirements.txt", "--no-warn-script-location");
            if (exitCode == 0) {
                progressCallback.accept("✅ custom_nodes/ComfyUI-Video-Helper-Suite dependencies successfully installed.");
            } else {
                progressCallback.accept("⚠️ pip finished with code " + exitCode + " on custom_nodes/ComfyUI-Video-Helper-Suite/requirements.txt.");
            }
        }

        if (pipRunner.isWslAvailable()) {
            progressCallback.accept("🚀 Installing custom_nodes/ComfyUI-Video-Helper-Suite dependencies in WSL...");
            int exitCode = pipRunner.runWslPipCommand(videoHelperSuiteDir, progressCallback, "install", "-r", "requirements.txt", "--break-system-packages");
            if (exitCode == 0) {
                progressCallback.accept("✅ custom_nodes/ComfyUI-Video-Helper-Suite dependencies in WSL successfully installed.");
            } else {
                progressCallback.accept("⚠️ WSL pip finished with code " + exitCode + " on custom_nodes/ComfyUI-Video-Helper-Suite/requirements.txt.");
            }
        }
    }

    /**
     * Ensures ComfyUI-KokoroTTS is cloned and dependencies are installed.
     *
     * @param comfyDir ComfyUI root directory
     * @param pythonExe Python binary path
     * @param progressCallback progress sink
     */
    public void ensureKokoroTtsInstalled(Path comfyDir, Path pythonExe, Consumer<String> progressCallback) {
        Path kokoroTtsDir = comfyDir.resolve("custom_nodes").resolve("ComfyUI-KokoroTTS");
        boolean ok = gitRepositoryManager.cloneOrUpdateRepository(
                "https://github.com/1038lab/ComfyUI-KokoroTTS.git",
                kokoroTtsDir,
                "main",
                progressCallback
        );
        if (!ok) {
            progressCallback.accept("❌ Failed to clone or update ComfyUI-KokoroTTS.");
            return;
        }

        Path reqFile = kokoroTtsDir.resolve("requirements.txt");
        if (Files.exists(reqFile) && pythonExe != null) {
            progressCallback.accept("🚀 Installing custom_nodes/ComfyUI-KokoroTTS dependencies...");
            int exitCode = pipRunner.runPipCommand(pythonExe, reqFile.getParent(), progressCallback, "install", "-r", "requirements.txt", "--no-warn-script-location");
            if (exitCode == 0) {
                progressCallback.accept("✅ custom_nodes/ComfyUI-KokoroTTS dependencies successfully installed.");
            } else {
                progressCallback.accept("⚠️ pip finished with code " + exitCode + " on requirements.txt.");
            }
        } else if (pythonExe != null) {
            progressCallback.accept("🚀 Installing kokoro-onnx soundfile...");
            pipRunner.runPipCommand(pythonExe, kokoroTtsDir, progressCallback, "install", "kokoro-onnx", "soundfile", "--no-warn-script-location");
        }

        if (pipRunner.isWslAvailable()) {
            progressCallback.accept("🚀 Installing custom_nodes/ComfyUI-KokoroTTS dependencies in WSL...");
            if (Files.exists(reqFile)) {
                pipRunner.runWslPipCommand(kokoroTtsDir, progressCallback, "install", "-r", "requirements.txt", "--break-system-packages");
            } else {
                pipRunner.runWslPipCommand(kokoroTtsDir, progressCallback, "install", "kokoro-onnx", "soundfile", "--break-system-packages");
            }
        }
    }

    /**
     * Ensures ComfyUI-Qwen-TTS is cloned and dependencies are installed.
     *
     * @param comfyDir ComfyUI root directory
     * @param pythonExe Python binary path
     * @param progressCallback progress sink
     */
    public void ensureQwenTtsInstalled(Path comfyDir, Path pythonExe, Consumer<String> progressCallback) {
        Path qwenTtsDir = comfyDir.resolve("custom_nodes").resolve("ComfyUI-Qwen-TTS");
        boolean ok = gitRepositoryManager.cloneOrUpdateRepository(
                "https://github.com/1038lab/ComfyUI-Qwen-TTS.git",
                qwenTtsDir,
                "main",
                progressCallback
        );
        if (!ok) {
            progressCallback.accept("❌ Failed to clone or update ComfyUI-Qwen-TTS.");
            return;
        }

        Path reqFile = qwenTtsDir.resolve("requirements.txt");
        if (Files.exists(reqFile) && pythonExe != null) {
            progressCallback.accept("🚀 Installing custom_nodes/ComfyUI-Qwen-TTS dependencies...");
            int exitCode = pipRunner.runPipCommand(pythonExe, reqFile.getParent(), progressCallback, "install", "-r", "requirements.txt", "--no-warn-script-location");
            if (exitCode == 0) {
                progressCallback.accept("✅ custom_nodes/ComfyUI-Qwen-TTS dependencies successfully installed.");
            } else {
                progressCallback.accept("⚠️ pip finished with code " + exitCode + " on requirements.txt.");
            }
        }

        if (pipRunner.isWslAvailable()) {
            progressCallback.accept("🚀 Installing custom_nodes/ComfyUI-Qwen-TTS dependencies in WSL...");
            if (Files.exists(reqFile)) {
                pipRunner.runWslPipCommand(qwenTtsDir, progressCallback, "install", "-r", "requirements.txt", "--break-system-packages");
            }
        }
    }
}
