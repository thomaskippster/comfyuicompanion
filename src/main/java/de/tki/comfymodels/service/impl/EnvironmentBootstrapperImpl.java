package de.tki.comfymodels.service.impl;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Service
public class EnvironmentBootstrapperImpl {
    private static final String COMFY_REPO_URL = "https://github.com/comfyanonymous/ComfyUI.git";
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    /**
     * Klont das ComfyUI Repository asynchron.
     * Wenn das Zielverzeichnis bereits ein gültiges Git-Repository enthält,
     * wird stattdessen ein 'git pull' ausgeführt.
     * Wenn das Verzeichnis existiert aber kein Git-Repo ist, wird es zuerst gelöscht.
     */
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
                
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(pythonUrl)).GET().build();
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
                        .GET().build();
                httpClient.send(request, HttpResponse.BodyHandlers.ofFile(getPipScript));

                // 3. get-pip.py ausführen
                progressCallback.accept("⚙️ Installing pip (this may take a moment)...");
                ProcessBuilder pb = new ProcessBuilder(pythonExe.toString(), getPipScript.toString());
                pb.directory(pythonExe.getParent().toFile());
                Process p = pb.start();
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
                        if (maxComputeCap >= 12.0) {
                            cudaVersion = "cu128";
                            cudaLogName = "CUDA 12.8 (Blackwell/Modern)";
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

                // 5. WSL-Abhängigkeiten installieren, falls WSL auf dem System vorhanden ist
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
                }

            } catch (Exception e) {
                throw new RuntimeException("Error installing dependencies: " + e.getMessage(), e);
            }
        });
    }

    private boolean isWslAvailable() {
        try {
            Process p = new ProcessBuilder("wsl", "echo", "1").start();
            boolean finished = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            return finished && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
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
            
            Process p = pb.start();
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
            
            Process p = pb.start();
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
}
