package de.tki.comfyuicompanion.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Service managing ComfyUI environment bootstrapping, portable Python downloads,
 * pip installation, and dependency orchestration.
 */
@Service
public class EnvironmentBootstrapperImpl {
    private static final String COMFY_REPO_URL = "https://github.com/comfyanonymous/ComfyUI.git";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ProcessTracker processTracker;
    private final GitRepositoryManager gitRepositoryManager;
    private final PipCommandRunner pipRunner;
    private final CustomNodesInstaller customNodesInstaller;

    public EnvironmentBootstrapperImpl() {
        this(null, null, null, null);
    }

    public EnvironmentBootstrapperImpl(ProcessTracker processTracker) {
        this(processTracker, null, null, null);
    }

    public EnvironmentBootstrapperImpl(
            ProcessTracker processTracker,
            GitRepositoryManager gitRepositoryManager) {
        this(processTracker, gitRepositoryManager, null, null);
    }

    @Autowired
    public EnvironmentBootstrapperImpl(
            @Autowired(required = false) ProcessTracker processTracker,
            @Autowired(required = false) GitRepositoryManager gitRepositoryManager,
            @Autowired(required = false) PipCommandRunner pipRunner,
            @Autowired(required = false) CustomNodesInstaller customNodesInstaller) {
        this.processTracker = processTracker != null ? processTracker : new ProcessTracker();
        this.gitRepositoryManager = gitRepositoryManager != null
                ? gitRepositoryManager
                : new GitRepositoryManager(this.processTracker);
        this.pipRunner = pipRunner != null
                ? pipRunner
                : new PipCommandRunner(this.processTracker);
        this.customNodesInstaller = customNodesInstaller != null
                ? customNodesInstaller
                : new CustomNodesInstaller(this.gitRepositoryManager, this.pipRunner);
    }

    /**
     * Clones or pulls the ComfyUI repository asynchronously.
     *
     * @param targetDir target installation directory
     * @param progressCallback progress sink
     * @return completion future
     */
    public CompletableFuture<Void> cloneComfyUI(Path targetDir, Consumer<String> progressCallback) {
        return CompletableFuture.runAsync(() -> {
            boolean success = gitRepositoryManager.cloneOrUpdateRepository(COMFY_REPO_URL, targetDir, "master", progressCallback);
            if (!success) {
                throw new RuntimeException("Git clone or update of ComfyUI failed.");
            }
            ensureVideoHelperSuiteInstalled(targetDir, null, progressCallback);
        });
    }

    /**
     * Downloads and extracts the portable Python archive.
     *
     * @param targetDir destination folder
     * @param progressCallback progress sink
     * @return future containing path to python.exe
     */
    public CompletableFuture<Path> downloadAndExtractPortablePython(Path targetDir, Consumer<String> progressCallback) {
        String pythonUrl = "https://www.python.org/ftp/python/3.11.9/python-3.11.9-embed-amd64.zip";
        Path zipFile = targetDir.resolve("python_portable.zip");
        Path extractDir = targetDir.resolve("python_embeded");

        return CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(targetDir);
                progressCallback.accept("📥 Downloading portable Python...");

                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(pythonUrl)).GET().timeout(Duration.ofSeconds(30)).build();
                httpClient.send(request, HttpResponse.BodyHandlers.ofFile(zipFile));

                progressCallback.accept("📦 Extracting Python environment...");
                extractZip(zipFile, extractDir);

                Files.deleteIfExists(zipFile);
                progressCallback.accept("✅ Portable Python set up.");
            } catch (Exception e) {
                throw new RuntimeException("Error setting up Python: " + e.getMessage(), e);
            }
        }).thenApply(v -> extractDir.resolve("python.exe"));
    }

    /**
     * Installs pip into portable Python and activates site-packages.
     *
     * @param pythonExe path to python.exe
     * @param progressCallback progress sink
     * @return completion future
     */
    public CompletableFuture<Void> installPip(Path pythonExe, Consumer<String> progressCallback) {
        return CompletableFuture.runAsync(() -> {
            try {
                progressCallback.accept("📦 Preparing pip installation...");

                Path pthFile = pythonExe.getParent().resolve("python311._pth");
                if (Files.exists(pthFile)) {
                    String content = Files.readString(pthFile);
                    if (content.contains("#import site")) {
                        content = content.replace("#import site", "import site");
                        Files.writeString(pthFile, content);
                        progressCallback.accept("🔧 site-packages enabled.");
                    }
                }

                Path getPipScript = pythonExe.getParent().resolve("get-pip.py");
                progressCallback.accept("📥 Downloading get-pip.py...");
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://bootstrap.pypa.io/get-pip.py"))
                        .GET().timeout(Duration.ofSeconds(30)).build();
                httpClient.send(request, HttpResponse.BodyHandlers.ofFile(getPipScript));

                progressCallback.accept("⚙️ Installing pip (this may take a moment)...");
                ProcessBuilder pb = new ProcessBuilder(pythonExe.toString(), getPipScript.toString());
                pb.directory(pythonExe.getParent().toFile());
                Process p = processTracker.start(pb);
                p.waitFor();

                Files.deleteIfExists(getPipScript);
                progressCallback.accept("✅ pip successfully installed.");
            } catch (Exception e) {
                throw new RuntimeException("Error during pip installation: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Installs ComfyUI requirements, CUDA PyTorch packages, and node dependencies.
     *
     * @param pythonExe path to python.exe
     * @param comfyDir ComfyUI root directory
     * @param progressCallback progress sink
     * @return completion future
     */
    public CompletableFuture<Void> installRequirements(Path pythonExe, Path comfyDir, Consumer<String> progressCallback) {
        return CompletableFuture.runAsync(() -> pipRunner.installRequirements(pythonExe, comfyDir, progressCallback));
    }

    private void extractZip(Path zipFile, Path targetDir) throws java.io.IOException {
        Files.createDirectories(targetDir);
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path newPath = targetDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(newPath);
                } else {
                    Files.createDirectories(newPath.getParent());
                    Files.copy(zis, newPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * Ensures Video-Helper-Suite custom node is installed.
     *
     * @param comfyDir ComfyUI directory
     * @param pythonExe python binary
     * @param progressCallback progress sink
     */
    public void ensureVideoHelperSuiteInstalled(Path comfyDir, Path pythonExe, Consumer<String> progressCallback) {
        customNodesInstaller.ensureVideoHelperSuiteInstalled(comfyDir, pythonExe, progressCallback);
    }

    /**
     * Ensures Kokoro-TTS custom node is installed.
     *
     * @param comfyDir ComfyUI directory
     * @param pythonExe python binary
     * @param progressCallback progress sink
     */
    public void ensureKokoroTtsInstalled(Path comfyDir, Path pythonExe, Consumer<String> progressCallback) {
        customNodesInstaller.ensureKokoroTtsInstalled(comfyDir, pythonExe, progressCallback);
    }

    /**
     * Ensures Qwen-TTS custom node is installed.
     *
     * @param comfyDir ComfyUI directory
     * @param pythonExe python binary
     * @param progressCallback progress sink
     */
    public void ensureQwenTtsInstalled(Path comfyDir, Path pythonExe, Consumer<String> progressCallback) {
        customNodesInstaller.ensureQwenTtsInstalled(comfyDir, pythonExe, progressCallback);
    }

    /**
     * Fixes WSL dependencies and system packages.
     *
     * @param comfyDir ComfyUI directory
     * @param progressCallback progress sink
     */
    public void fixWslDependencies(Path comfyDir, Consumer<String> progressCallback) {
        pipRunner.fixWslDependencies(comfyDir, progressCallback);
    }
}
