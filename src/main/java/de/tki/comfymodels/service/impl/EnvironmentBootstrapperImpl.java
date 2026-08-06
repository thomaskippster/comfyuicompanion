package de.tki.comfymodels.service.impl;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.time.Duration;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class EnvironmentBootstrapperImpl {
    private static final String COMFY_REPO_URL = "https://github.com/comfyanonymous/ComfyUI.git";
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Klont das ComfyUI Repository asynchron.
     * Wenn das Zielverzeichnis bereits ein gültiges Git-Repository enthält,
     * wird stattdessen ein 'git pull' ausgeführt.
     * Wenn das Verzeichnis existiert aber kein Git-Repo ist, wird es zuerst gelöscht.
     */

     @Autowired(required = false)
     private ProcessTracker processTracker;
    public CompletableFuture<Void> cloneComfyUI(Path targetDir, Consumer<String> progressCallback) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Check if the target directory already exists
                if (Files.exists(targetDir)) {
                    Path gitDir = targetDir.resolve(".git");
                    if (Files.exists(gitDir) && Files.isDirectory(gitDir)) {
                        boolean pullSuccess = false;
                        try (Git git = Git.open(targetDir.toFile())) {
                            git.pull()
                                .setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out) {
                                    @Override
                                    public void println(String x) { progressCallback.accept(x); }
                                }))
                                .call();
                            pullSuccess = true;
                        } catch (Exception e) {
                            progressCallback.accept("⚠️ Git pull failed: " + e.getMessage() + ". Removing and recloning...");
                            deleteDirectoryRecursively(targetDir);
                        }
                        if (pullSuccess) {
                            progressCallback.accept("✅ ComfyUI successfully updated.");
                            ensureVideoHelperSuiteInstalled(targetDir, null, progressCallback);
                            return;
                        }
                    } else {
                        // Exists but not a git repo (e.g. partial/failed install) → delete and reclone
                        progressCallback.accept("⚠️ Target directory exists but is not a git repo. Removing and recloning...");
                        deleteDirectoryRecursively(targetDir);
                    }
                }

                progressCallback.accept("Starting Git Clone of ComfyUI...");
                Git.cloneRepository()
                        .setURI(COMFY_REPO_URL)
                        .setDirectory(targetDir.toFile())
                        .setBranch("master")
                        .setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out) {
                            @Override
                            public void println(String x) { progressCallback.accept(x); }
                        }))
                        .call()
                        .close();
                progressCallback.accept("✅ Clone successfully completed.");
                ensureVideoHelperSuiteInstalled(targetDir, null, progressCallback);
            } catch (Exception e) {
                throw new RuntimeException("Git clone failed: " + e.getMessage(), e);
            }
        });
    }

    private void deleteDirectoryRecursively(Path path) throws java.io.IOException {
        if (!Files.exists(path)) return;
        Files.walk(path)
             .sorted(java.util.Comparator.reverseOrder())
             .forEach(p -> {
                 try { Files.delete(p); } catch (java.io.IOException ignored) {}
             });
    }

    /**
     * Lädt die portable Python-Umgebung herunter und entpackt sie.
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
     * Installiert pip in die portable Python-Umgebung und aktiviert site-packages.
     */
    public CompletableFuture<Void> installPip(Path pythonExe, Consumer<String> progressCallback) {
        return CompletableFuture.runAsync(() -> {
            try {
                progressCallback.accept("📦 Preparing pip installation...");
                
                // 1. ._pth Datei anpassen, um site-packages zu aktivieren
                Path pthFile = pythonExe.getParent().resolve("python311._pth");
                if (Files.exists(pthFile)) {
                    String content = Files.readString(pthFile);
                    if (content.contains("#import site")) {
                        content = content.replace("#import site", "import site");
                        Files.writeString(pthFile, content);
                        progressCallback.accept("🔧 site-packages enabled.");
                    }
                }

                // 2. get-pip.py herunterladen
                Path getPipScript = pythonExe.getParent().resolve("get-pip.py");
                progressCallback.accept("📥 Downloading get-pip.py...");
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://bootstrap.pypa.io/get-pip.py"))
                        .GET().timeout(Duration.ofSeconds(30)).build();
                httpClient.send(request, HttpResponse.BodyHandlers.ofFile(getPipScript));

                // 3. get-pip.py ausführen
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
     * Installiert die Requirements aus der requirements.txt, die CUDA PyTorch-Pakete und Manager-Requirements.
     */
    public CompletableFuture<Void> installRequirements(Path pythonExe, Path comfyDir, Consumer<String> progressCallback) {
        return CompletableFuture.runAsync(() -> {
            try {
                // 1. CUDA PyTorch-Installation für Windows bei vorhandener NVIDIA-GPU
                boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
                if (isWindows) {
                    boolean hasNvidia = false;
                    double maxComputeCap = 0.0;
                    try {
                        Process p = Runtime.getRuntime().exec(new String[]{"nvidia-smi", "--query-gpu=compute_cap", "--format=csv,noheader"});
                        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
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
                            cudaVersion = "cu124";
                            cudaLogName = "CUDA 12.4+ (Blackwell RTX 50-series / Modern)";
                        } else if (maxComputeCap >= 8.9) {
                            cudaVersion = "cu124";
                            cudaLogName = "CUDA 12.4 (Modern)";
                        } else if (maxComputeCap >= 5.0) {
                            cudaVersion = "cu121";
                            cudaLogName = "CUDA 12.1 (Standard)";
                        } else {
                            progressCallback.accept("🔍 NVIDIA GPU detected, but Compute Capability (" + maxComputeCap + ") is not supported by modern PyTorch CUDA (requires >= 5.0). Using default installation.");
                            hasNvidia = false;
                            cudaVersion = "";
                            cudaLogName = "";
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

                // 2. Core-Abhängigkeiten installieren
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

                // 3. ComfyUI-Manager-Abhängigkeiten installieren (falls vorhanden)
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

                // 4. Custom_nodes ComfyUI-Manager Abhängigkeiten installieren (falls vorhanden)
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

                // 5. Video-Helper-Suite requirements (if present)
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

                // 6. WSL-Abhängigkeiten installieren, falls WSL auf dem System vorhanden ist
                if (isWslAvailable()) {
                    progressCallback.accept("🚀 WSL support detected on system. Setting up WSL Python environment...");
                    
                    if (Files.exists(reqFile)) {
                        progressCallback.accept("🚀 Installing ComfyUI dependencies in WSL (wsl python3 -m pip install -r requirements.txt --break-system-packages)...");
                        int exitCode = runWslPipCommand(comfyDir, progressCallback, "install", "-r", "requirements.txt", "--break-system-packages");
                        if (exitCode == 0) {
                            progressCallback.accept("✅ ComfyUI dependencies in WSL successfully installed.");
                        } else {
                            progressCallback.accept("⚠️ WSL pip finished with code " + exitCode + " on requirements.txt.");
                        }
                    }

                    if (Files.exists(managerReq)) {
                        progressCallback.accept("🚀 Installing ComfyUI-Manager dependencies in WSL...");
                        int exitCode = runWslPipCommand(comfyDir, progressCallback, "install", "-r", "manager_requirements.txt", "--break-system-packages");
                        if (exitCode == 0) {
                            progressCallback.accept("✅ ComfyUI-Manager dependencies in WSL successfully installed.");
                        } else {
                            progressCallback.accept("⚠️ WSL pip finished with code " + exitCode + " on manager_requirements.txt.");
                        }
                    }

                    if (Files.exists(customManagerReq)) {
                        progressCallback.accept("🚀 Installing custom_nodes/ComfyUI-Manager dependencies in WSL...");
                        int exitCode = runWslPipCommand(customManagerReq.getParent(), progressCallback, "install", "-r", "requirements.txt", "--break-system-packages");
                        if (exitCode == 0) {
                            progressCallback.accept("✅ custom_nodes/ComfyUI-Manager dependencies in WSL successfully installed.");
                        } else {
                            progressCallback.accept("⚠️ WSL pip finished with code " + exitCode + " on custom_nodes/ComfyUI-Manager/requirements.txt.");
                        }
                    }

                    if (Files.exists(videoHelperReq)) {
                        progressCallback.accept("🚀 Installing custom_nodes/ComfyUI-Video-Helper-Suite dependencies in WSL...");
                        int exitCode = runWslPipCommand(videoHelperReq.getParent(), progressCallback, "install", "-r", "requirements.txt", "--break-system-packages");
                        if (exitCode == 0) {
                            progressCallback.accept("✅ custom_nodes/ComfyUI-Video-Helper-Suite dependencies in WSL successfully installed.");
                        } else {
                            progressCallback.accept("⚠️ WSL pip finished with code " + exitCode + " on custom_nodes/ComfyUI-Video-Helper-Suite/requirements.txt.");
                        }
                    }
                }

            } catch (Exception e) {
                throw new RuntimeException("Error installing dependencies: " + e.getMessage(), e);
            }
        });
    }

    private boolean isWslAvailable() {
        return false; // Disabled by user request: Do not use WSL.
    }

    private int runWslPipCommand(Path workingDir, Consumer<String> progressCallback, String... args) {
        try {
            java.util.List<String> command = new java.util.ArrayList<>();
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
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    progressCallback.accept("[wsl-pip] " + line);
                }
            }
            return p.waitFor();
        } catch (Exception e) {
            progressCallback.accept("❌ Error running WSL pip command: " + e.getMessage());
            return -1;
        }
    }

    private int runPipCommand(Path pythonExe, Path workingDir, Consumer<String> progressCallback, String... args) {
        try {
            java.util.List<String> command = new java.util.ArrayList<>();
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
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    progressCallback.accept("[pip] " + line);
                }
            }
            return p.waitFor();
        } catch (Exception e) {
            progressCallback.accept("❌ Error running pip command: " + e.getMessage());
            return -1;
        }
    }

    private void extractZip(Path zipFile, Path targetDir) throws java.io.IOException {
        Files.createDirectories(targetDir);
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new FileInputStream(zipFile.toFile()))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path newPath = targetDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(newPath);
                } else {
                    Files.createDirectories(newPath.getParent());
                    Files.copy(zis, newPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private void runSystemGitClone(String repoUrl, Path targetDir, String branch, Consumer<String> progressCallback) throws Exception {
        progressCallback.accept("Executing system git clone for " + repoUrl + "...");
        ProcessBuilder pb = new ProcessBuilder("git", "clone", "-b", branch, repoUrl, targetDir.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process p = processTracker.start(pb);
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                progressCallback.accept("[git] " + line);
            }
        }
        int exitCode = p.waitFor();
        if (exitCode != 0) {
            throw new IOException("System git clone failed with exit code " + exitCode);
        }
    }

    private void runSystemGitPull(Path targetDir, Consumer<String> progressCallback) throws Exception {
        progressCallback.accept("Executing system git pull in " + targetDir + "...");
        ProcessBuilder pb = new ProcessBuilder("git", "pull");
        pb.directory(targetDir.toFile());
        pb.redirectErrorStream(true);
        Process p = processTracker.start(pb);
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                progressCallback.accept("[git] " + line);
            }
        }
        int exitCode = p.waitFor();
        if (exitCode != 0) {
            throw new IOException("System git pull failed with exit code " + exitCode);
        }
    }

    public void ensureVideoHelperSuiteInstalled(Path comfyDir, Path pythonExe, Consumer<String> progressCallback) {
        Path videoHelperSuiteDir = comfyDir.resolve("custom_nodes").resolve("ComfyUI-Video-Helper-Suite");
        if (Files.exists(videoHelperSuiteDir)) {
            Path gitDir = videoHelperSuiteDir.resolve(".git");
            if (Files.exists(gitDir) && Files.isDirectory(gitDir)) {
                boolean success = false;
                try {
                    runSystemGitPull(videoHelperSuiteDir, progressCallback);
                    success = true;
                    progressCallback.accept("✅ ComfyUI-Video-Helper-Suite successfully updated via system git.");
                } catch (Exception e) {
                    progressCallback.accept("⚠️ System git pull failed: " + e.getMessage() + ". Trying JGit...");
                    try (Git git = Git.open(videoHelperSuiteDir.toFile())) {
                        git.pull()
                           .setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out) {
                               @Override
                               public void println(String x) { progressCallback.accept(x); }
                           }))
                           .call();
                        success = true;
                        progressCallback.accept("✅ ComfyUI-Video-Helper-Suite successfully updated via JGit.");
                    } catch (Exception ex) {
                        progressCallback.accept("⚠️ JGit pull failed: " + ex.getMessage() + ". Re-installing...");
                    }
                }
                if (!success) {
                    try {
                        deleteDirectoryRecursively(videoHelperSuiteDir);
                        cloneVideoHelperSuite(videoHelperSuiteDir, pythonExe, progressCallback);
                    } catch (Exception ex) {
                        progressCallback.accept("❌ Failed to re-clone Video-Helper-Suite: " + ex.getMessage());
                    }
                }
            } else {
                progressCallback.accept("⚠️ Video-Helper-Suite folder exists but is not a Git repo. Re-installing...");
                try {
                    deleteDirectoryRecursively(videoHelperSuiteDir);
                    cloneVideoHelperSuite(videoHelperSuiteDir, pythonExe, progressCallback);
                } catch (Exception ex) {
                    progressCallback.accept("❌ Failed to re-clone Video-Helper-Suite: " + ex.getMessage());
                }
            }
        } else {
            try {
                cloneVideoHelperSuite(videoHelperSuiteDir, pythonExe, progressCallback);
            } catch (Exception ex) {
                progressCallback.accept("❌ Failed to clone Video-Helper-Suite: " + ex.getMessage());
            }
        }
    }

    private void cloneVideoHelperSuite(Path videoHelperSuiteDir, Path pythonExe, Consumer<String> progressCallback) throws Exception {
        progressCallback.accept("Starting Clone of ComfyUI-Video-Helper-Suite...");
        boolean success = false;
        try {
            runSystemGitClone("https://github.com/Kosinkadink/ComfyUI-VideoHelperSuite.git", videoHelperSuiteDir, "main", progressCallback);
            success = true;
            progressCallback.accept("✅ ComfyUI-Video-Helper-Suite successfully cloned via system git.");
        } catch (Exception e) {
            progressCallback.accept("⚠️ System git clone failed or git is not in PATH: " + e.getMessage() + ". Falling back to JGit...");
            try (Git git = Git.cloneRepository()
                    .setURI("https://github.com/Kosinkadink/ComfyUI-VideoHelperSuite.git")
                    .setDirectory(videoHelperSuiteDir.toFile())
                    .setBranch("main")
                    .setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out) {
                        @Override
                        public void println(String x) { progressCallback.accept(x); }
                    }))
                    .call()) {
                success = true;
                progressCallback.accept("✅ ComfyUI-Video-Helper-Suite successfully cloned via JGit.");
            }
        }

        if (!success) {
            throw new IOException("Failed to clone ComfyUI-Video-Helper-Suite using both system Git and JGit.");
        }
        
        // Now install its requirements
        Path videoHelperReq = videoHelperSuiteDir.resolve("requirements.txt");
        if (Files.exists(videoHelperReq) && pythonExe != null) {
            progressCallback.accept("🚀 Installing custom_nodes/ComfyUI-Video-Helper-Suite dependencies...");
            int exitCode = runPipCommand(pythonExe, videoHelperReq.getParent(), progressCallback, "install", "-r", "requirements.txt", "--no-warn-script-location");
            if (exitCode == 0) {
                progressCallback.accept("✅ custom_nodes/ComfyUI-Video-Helper-Suite dependencies successfully installed.");
            } else {
                progressCallback.accept("⚠️ pip finished with code " + exitCode + " on custom_nodes/ComfyUI-Video-Helper-Suite/requirements.txt.");
            }
        }
        
        if (isWslAvailable()) {
            progressCallback.accept("🚀 Installing custom_nodes/ComfyUI-Video-Helper-Suite dependencies in WSL...");
            int exitCode = runWslPipCommand(videoHelperSuiteDir, progressCallback, "install", "-r", "requirements.txt", "--break-system-packages");
            if (exitCode == 0) {
                progressCallback.accept("✅ custom_nodes/ComfyUI-Video-Helper-Suite dependencies in WSL successfully installed.");
            } else {
                progressCallback.accept("⚠️ WSL pip finished with code " + exitCode + " on custom_nodes/ComfyUI-Video-Helper-Suite/requirements.txt.");
            }
        }
    }

    public void ensureKokoroTtsInstalled(Path comfyDir, Path pythonExe, Consumer<String> progressCallback) {
        Path kokoroTtsDir = comfyDir.resolve("custom_nodes").resolve("ComfyUI-KokoroTTS");
        if (Files.exists(kokoroTtsDir)) {
            Path gitDir = kokoroTtsDir.resolve(".git");
            if (Files.exists(gitDir) && Files.isDirectory(gitDir)) {
                boolean success = false;
                try {
                    runSystemGitPull(kokoroTtsDir, progressCallback);
                    success = true;
                    progressCallback.accept("✅ ComfyUI-KokoroTTS successfully updated via system git.");
                } catch (Exception e) {
                    progressCallback.accept("⚠️ System git pull failed: " + e.getMessage() + ". Trying JGit...");
                    try (Git git = Git.open(kokoroTtsDir.toFile())) {
                        git.pull()
                           .setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out) {
                               @Override
                               public void println(String x) { progressCallback.accept(x); }
                           }))
                           .call();
                        success = true;
                        progressCallback.accept("✅ ComfyUI-KokoroTTS successfully updated via JGit.");
                    } catch (Exception ex) {
                        progressCallback.accept("⚠️ JGit pull failed: " + ex.getMessage() + ". Re-installing...");
                    }
                }
                if (!success) {
                    try {
                        deleteDirectoryRecursively(kokoroTtsDir);
                        cloneKokoroTts(kokoroTtsDir, pythonExe, progressCallback);
                    } catch (Exception ex) {
                        progressCallback.accept("❌ Failed to re-clone ComfyUI-KokoroTTS: " + ex.getMessage());
                    }
                }
            } else {
                progressCallback.accept("⚠️ ComfyUI-KokoroTTS folder exists but is not a Git repo. Re-installing...");
                try {
                    deleteDirectoryRecursively(kokoroTtsDir);
                    cloneKokoroTts(kokoroTtsDir, pythonExe, progressCallback);
                } catch (Exception ex) {
                    progressCallback.accept("❌ Failed to re-clone ComfyUI-KokoroTTS: " + ex.getMessage());
                }
            }
        } else {
            try {
                cloneKokoroTts(kokoroTtsDir, pythonExe, progressCallback);
            } catch (Exception ex) {
                progressCallback.accept("❌ Failed to clone ComfyUI-KokoroTTS: " + ex.getMessage());
            }
        }
    }

    private void cloneKokoroTts(Path kokoroTtsDir, Path pythonExe, Consumer<String> progressCallback) throws Exception {
        progressCallback.accept("Starting Clone of ComfyUI-KokoroTTS...");
        boolean success = false;
        try {
            runSystemGitClone("https://github.com/1038lab/ComfyUI-KokoroTTS.git", kokoroTtsDir, "main", progressCallback);
            success = true;
            progressCallback.accept("✅ ComfyUI-KokoroTTS successfully cloned via system git.");
        } catch (Exception e) {
            progressCallback.accept("⚠️ System git clone failed or git is not in PATH: " + e.getMessage() + ". Falling back to JGit...");
            try (Git git = Git.cloneRepository()
                    .setURI("https://github.com/1038lab/ComfyUI-KokoroTTS.git")
                    .setDirectory(kokoroTtsDir.toFile())
                    .setBranch("main")
                    .setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out) {
                        @Override
                        public void println(String x) { progressCallback.accept(x); }
                    }))
                    .call()) {
                success = true;
                progressCallback.accept("✅ ComfyUI-KokoroTTS successfully cloned via JGit.");
            }
        }

        if (!success) {
            throw new IOException("Failed to clone ComfyUI-KokoroTTS using both system Git and JGit.");
        }

        Path reqFile = kokoroTtsDir.resolve("requirements.txt");
        if (Files.exists(reqFile) && pythonExe != null) {
            progressCallback.accept("🚀 Installing custom_nodes/ComfyUI-KokoroTTS dependencies...");
            int exitCode = runPipCommand(pythonExe, reqFile.getParent(), progressCallback, "install", "-r", "requirements.txt", "--no-warn-script-location");
            if (exitCode == 0) {
                progressCallback.accept("✅ custom_nodes/ComfyUI-KokoroTTS dependencies successfully installed.");
            } else {
                progressCallback.accept("⚠️ pip finished with code " + exitCode + " on requirements.txt.");
            }
        } else if (pythonExe != null) {
            progressCallback.accept("🚀 Installing kokoro-onnx soundfile...");
            runPipCommand(pythonExe, kokoroTtsDir, progressCallback, "install", "kokoro-onnx", "soundfile", "--no-warn-script-location");
        }

        if (isWslAvailable()) {
            progressCallback.accept("🚀 Installing custom_nodes/ComfyUI-KokoroTTS dependencies in WSL...");
            if (Files.exists(reqFile)) {
                runWslPipCommand(kokoroTtsDir, progressCallback, "install", "-r", "requirements.txt", "--break-system-packages");
            } else {
                runWslPipCommand(kokoroTtsDir, progressCallback, "install", "kokoro-onnx", "soundfile", "--break-system-packages");
            }
        }
    }

    /**
     * Ensure the ComfyUI-Qwen-TTS custom node is installed and its Python
     * dependencies are satisfied. Mirrors {@link #ensureKokoroTtsInstalled}:
     * if the folder exists and is a git repo, we try to pull; otherwise we
     * delete the folder and re-clone. The Qwen-TTS custom node is the
     * primary high-quality TTS backend for the application.
     */
    public void ensureQwenTtsInstalled(Path comfyDir, Path pythonExe, Consumer<String> progressCallback) {
        Path qwenTtsDir = comfyDir.resolve("custom_nodes").resolve("ComfyUI-Qwen-TTS");
        if (Files.exists(qwenTtsDir)) {
            Path gitDir = qwenTtsDir.resolve(".git");
            if (Files.exists(gitDir) && Files.isDirectory(gitDir)) {
                boolean success = false;
                try {
                    runSystemGitPull(qwenTtsDir, progressCallback);
                    success = true;
                    progressCallback.accept("✅ ComfyUI-Qwen-TTS successfully updated via system git.");
                } catch (Exception e) {
                    progressCallback.accept("⚠️ System git pull failed: " + e.getMessage() + ". Trying JGit...");
                    try (Git git = Git.open(qwenTtsDir.toFile())) {
                        git.pull()
                           .setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out) {
                               @Override
                               public void println(String x) { progressCallback.accept(x); }
                           }))
                           .call();
                        success = true;
                        progressCallback.accept("✅ ComfyUI-Qwen-TTS successfully updated via JGit.");
                    } catch (Exception ex) {
                        progressCallback.accept("⚠️ JGit pull failed: " + ex.getMessage() + ". Re-installing...");
                    }
                }
                if (!success) {
                    try {
                        deleteDirectoryRecursively(qwenTtsDir);
                        cloneQwenTts(qwenTtsDir, pythonExe, progressCallback);
                    } catch (Exception ex) {
                        progressCallback.accept("❌ Failed to re-clone ComfyUI-Qwen-TTS: " + ex.getMessage());
                    }
                }
            } else {
                progressCallback.accept("⚠️ ComfyUI-Qwen-TTS folder exists but is not a Git repo. Re-installing...");
                try {
                    deleteDirectoryRecursively(qwenTtsDir);
                    cloneQwenTts(qwenTtsDir, pythonExe, progressCallback);
                } catch (Exception ex) {
                    progressCallback.accept("❌ Failed to re-clone ComfyUI-Qwen-TTS: " + ex.getMessage());
                }
            }
        } else {
            try {
                cloneQwenTts(qwenTtsDir, pythonExe, progressCallback);
            } catch (Exception ex) {
                progressCallback.accept("❌ Failed to clone ComfyUI-Qwen-TTS: " + ex.getMessage());
            }
        }
    }

    private void cloneQwenTts(Path qwenTtsDir, Path pythonExe, Consumer<String> progressCallback) throws Exception {
        String repoUrl = "https://github.com/1038lab/ComfyUI-Qwen-TTS.git";
        progressCallback.accept("Starting Clone of ComfyUI-Qwen-TTS...");
        boolean success = false;
        try {
            runSystemGitClone(repoUrl, qwenTtsDir, "main", progressCallback);
            success = true;
            progressCallback.accept("✅ ComfyUI-Qwen-TTS successfully cloned via system git.");
        } catch (Exception e) {
            progressCallback.accept("⚠️ System git clone failed or git is not in PATH: " + e.getMessage() + ". Falling back to JGit...");
            try (Git git = Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(qwenTtsDir.toFile())
                    .setBranch("main")
                    .setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out) {
                        @Override
                        public void println(String x) { progressCallback.accept(x); }
                    }))
                    .call()) {
                success = true;
                progressCallback.accept("✅ ComfyUI-Qwen-TTS successfully cloned via JGit.");
            }
        }

        if (!success) {
            throw new IOException("Failed to clone ComfyUI-Qwen-TTS using both system Git and JGit.");
        }

        Path reqFile = qwenTtsDir.resolve("requirements.txt");
        if (Files.exists(reqFile) && pythonExe != null) {
            progressCallback.accept("🚀 Installing custom_nodes/ComfyUI-Qwen-TTS dependencies...");
            int exitCode = runPipCommand(pythonExe, reqFile.getParent(), progressCallback, "install", "-r", "requirements.txt", "--no-warn-script-location");
            if (exitCode == 0) {
                progressCallback.accept("✅ custom_nodes/ComfyUI-Qwen-TTS dependencies successfully installed.");
            } else {
                progressCallback.accept("⚠️ pip finished with code " + exitCode + " on requirements.txt.");
            }
        }

        if (isWslAvailable()) {
            progressCallback.accept("🚀 Installing custom_nodes/ComfyUI-Qwen-TTS dependencies in WSL...");
            if (Files.exists(reqFile)) {
                runWslPipCommand(qwenTtsDir, progressCallback, "install", "-r", "requirements.txt", "--break-system-packages");
            }
        }
    }
    public void fixWslDependencies(Path comfyDir, Consumer<String> progressCallback) {
        if (!isWslAvailable()) {
            progressCallback.accept("❌ WSL is not available or not enabled on this system.\n");
            return;
        }

        progressCallback.accept("⚙️ Starting WSL Dependency Fix...\n");

        // 1. Install libsndfile1 and libsndfile1-dev inside WSL using apt-get
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

        // 2. Re-install Python package dependencies inside WSL (pip install)
        if (comfyDir == null) {
            progressCallback.accept("⚠️ ComfyUI path not configured. Skipping Python dependencies installation.\n");
            return;
        }

        progressCallback.accept("🐍 Upgrading pip inside WSL...\n");
        runWslPipCommand(comfyDir, progressCallback, "install", "--upgrade", "pip", "--break-system-packages");

        // Reinstall all requirements
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

    private int runWslCommand(Consumer<String> progressCallback, String... args) {
        try {
            java.util.List<String> command = new java.util.ArrayList<>();
            command.add("wsl");
            for (String arg : args) {
                command.add(arg);
            }
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            
            Process p = processTracker.start(pb);
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    progressCallback.accept("[wsl] " + line);
                }
            }
            return p.waitFor();
        } catch (Exception e) {
            progressCallback.accept("❌ Error running WSL command: " + e.getMessage());
            return -1;
        }
    }
}
