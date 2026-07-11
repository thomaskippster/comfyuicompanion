package de.tki.comfymodels.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tki.comfymodels.service.IComfyLifecycleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ComfyLifecycleService implements IComfyLifecycleService {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ComfyLifecycleService.class);

    @Autowired
    private ConfigService configService;

    @Autowired
    private ProfileManager profileManager;

    @Autowired
    private EnvironmentBootstrapperImpl bootstrapper;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private ComfyProcessController processController;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private de.tki.comfymodels.service.IDownloadManager downloadManager;

    private volatile Process comfyProcess;
    private final AtomicReference<String> status = new AtomicReference<>("Stopped");
    private final java.util.concurrent.atomic.AtomicBoolean stopping = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean browserLaunched = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build();
    private volatile Runnable onBrowserLaunched;
    private final java.util.concurrent.atomic.AtomicBoolean guiLineShown = new java.util.concurrent.atomic.AtomicBoolean(false);

    @Override
    public void setOnBrowserLaunched(Runnable callback) {
        this.onBrowserLaunched = callback;
    }

    @Override
    public synchronized void start() {
        if (isRunning()) {
            status.set("Already Running");
            return;
        }

        try {
            status.set("Starting...");
            guiLineShown.set(false);
            
            // Clean any orphaned process using the target port first to prevent port/DB locks
            killProcessOnPort();
            
            // Try to resolve the active launch profile
            String activeProfileId = configService.getActiveProfile();
            de.tki.comfymodels.domain.LaunchProfile activeProfile = null;
            if (activeProfileId != null && !activeProfileId.isEmpty()) {
                activeProfile = profileManager.loadProfiles().stream()
                        .filter(p -> p.id().equals(activeProfileId))
                        .findFirst().orElse(null);
            }
            if (activeProfile == null) {
                java.util.List<de.tki.comfymodels.domain.LaunchProfile> profiles = profileManager.loadProfiles();
                if (!profiles.isEmpty()) {
                    activeProfile = profiles.get(0);
                }
            }

            if (activeProfile != null) {
                String comfyPath = configService.getComfyUIPath();
                String pythonPath = configService.getPythonPath();
                
                logger.info("🚀 [Lifecycle] Starting ComfyUI via processController with profile: " + activeProfile.name());
                
                java.io.File logFile = new java.io.File("comfyui.log");
                try {
                    // Truncate/create log file
                    java.nio.file.Files.writeString(logFile.toPath(), "", java.nio.charset.StandardCharsets.UTF_8);
                } catch (java.io.IOException ignored) {}

                final de.tki.comfymodels.domain.LaunchProfile finalProfile = activeProfile;
                processController.start(finalProfile, java.nio.file.Paths.get(comfyPath), pythonPath, log -> {
                    if (log.contains("To see the GUI go to:")) {
                        guiLineShown.set(true);
                    }
                    // Append log to comfyui.log
                    try {
                        java.nio.file.Files.writeString(
                            logFile.toPath(), 
                            log + "\n", 
                            java.nio.charset.StandardCharsets.UTF_8, 
                            java.nio.file.StandardOpenOption.CREATE, 
                            java.nio.file.StandardOpenOption.APPEND
                        );
                    } catch (java.io.IOException ignored) {}
                }).thenAccept(exitCode -> {
                    status.set("Stopped (Exit Code: " + exitCode + ")");
                });

                // Monitor for readiness and launch browser
                new Thread(() -> {
                    try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                    boolean launched = false;
                    while (!launched && isRunning()) {
                        if (isHealthy()) {
                            String url = configService.getComfyUIUrl();
                            if (browserLaunched.compareAndSet(false, true)) {
                                logger.info("🌐 [Lifecycle] Health check passed. Launching browser: " + url);
                                try {
                                    if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                                        java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
                                    } else {
                                        String cmd = de.tki.comfymodels.util.PlatformUtils.isWindows() ? "cmd /c start " + url : "xdg-open " + url;
                                        logger.info("🌐 [Lifecycle] Desktop API not supported. Executing: " + cmd);
                                        Process p = Runtime.getRuntime().exec(cmd);
                                        if (p.waitFor() != 0) {
                                            logger.error("⚠️ [Lifecycle] Browser launch process failed with exit code: " + p.exitValue());
                                        }
                                    }
                                    if (onBrowserLaunched != null) {
                                        onBrowserLaunched.run();
                                    }
                                } catch (Exception e) {
                                    logger.error("❌ [Lifecycle] Failed to open browser: " + e.getMessage());
                                    e.printStackTrace();
                                }
                            } else {
                                logger.info("🌐 [Lifecycle] Health check passed. Browser already launched during this application run.");
                                if (onBrowserLaunched != null) {
                                    onBrowserLaunched.run();
                                }
                            }
                            launched = true;
                            triggerBrowserRefresh();
                        }
                        try { Thread.sleep(2000); } catch (InterruptedException e) { break; }
                    }
                }).start();

                status.set("Running (via Controller)");
            } else {
                status.set("Error: No launch profile found to start ComfyUI.");
            }
        } catch (Exception e) {
            status.set("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public synchronized void stop() {
        if (stopping.getAndSet(true)) {
            return;
        }
        try {
            status.set("Stopping...");
            guiLineShown.set(false);
            
            // 1. Kill internal process if managed
            if (comfyProcess != null) {
                try {
                    comfyProcess.descendants().forEach(ProcessHandle::destroyForcibly);
                    comfyProcess.destroyForcibly();
                    comfyProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception ignored) {}
                comfyProcess = null;
            }
            browserLaunched.set(false);

            // 2. Kill the other controller if running
            try {
                processController.stop();
            } catch (Exception e) {
                logger.error("Failed to stop processController: " + e.getMessage());
            }

            // 3. Kill by port fallback (for external/orphaned instances)
            killProcessOnPort();
            
            status.set("Stopped");
        } finally {
            stopping.set(false);
        }
    }

    private void killProcessOnPort() {
        try {
            String url = configService.getComfyUIUrl();
            int port = 8188;
            try {
                port = Integer.parseInt(url.substring(url.lastIndexOf(":") + 1));
            } catch (Exception ignored) {}

            if (de.tki.comfymodels.util.PlatformUtils.isWindows()) {
                // Find PID on port and kill it
                Process p = Runtime.getRuntime().exec("cmd /c netstat -ano | findstr :" + port);
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String upper = line.toUpperCase();
                        if (upper.contains("LISTENING") || upper.contains("ABH") || upper.contains("ESCUCHANDO") || upper.contains("L'ECOUTE") || line.contains("0.0.0.0:0") || line.contains("[::]:0")) {
                            String[] parts = line.trim().split("\\s+");
                            if (parts.length > 0) {
                                try {
                                    long pid = Long.parseLong(parts[parts.length - 1]);
                                    if (pid != ProcessHandle.current().pid() && pid > 0) {
                                        ProcessHandle.of(pid).ifPresent(ProcessHandle::destroyForcibly);
                                        try {
                                            Runtime.getRuntime().exec("taskkill /F /PID " + pid + " /T");
                                        } catch (Exception ignored) {}
                                        logger.info("💀 Killed process " + pid + " listening on port " + port);
                                    }
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                }
                // ALSO KILL WSL INSTANCES RUNNING ON PORT OR main.py
                try {
                    Runtime.getRuntime().exec(new String[]{"wsl", "pkill", "-f", "main.py"});
                    Runtime.getRuntime().exec(new String[]{"wsl", "fuser", "-k", port + "/tcp"});
                } catch (Exception ignored) {}
            } else {
                // Linux/Mac fallback
                Runtime.getRuntime().exec(new String[]{"sh", "-c", "fuser -k " + port + "/tcp"});
            }
            // Sleep a small duration to allow OS to release port & file locks
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        } catch (Exception e) {
            logger.error("Failed to kill process on port: " + e.getMessage());
        }
    }

    @Override
    public void restart() {
        stop();
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        
        // Reload extra paths in case they were changed before restart
        configService.loadExtraModelPaths();
        
        start();
        
        // Optional: Wait for health check in background
        new Thread(() -> {
            status.set("Restarting (Waiting for API...)");
            for (int i = 0; i < 30; i++) { // Wait up to 30 seconds
                if (isHealthy()) {
                    status.set("Running & Healthy");
                    return;
                }
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
            status.set("Running (Healthy Check Timeout)");
        }).start();
    }

    @Override
    public String getStatus() {
        String current = status.get();
        if (current.startsWith("Stopped") || current.startsWith("Error")) {
            if (isHealthy()) {
                return "Running (External Instance)";
            }
        }
        return current;
    }

    @Override
    public boolean isGuiLineShown() {
        return guiLineShown.get();
    }

    @Override
    public boolean isProcessAlive() {
        return comfyProcess != null && comfyProcess.isAlive();
    }

    @Override
    public boolean isRunning() {
        return isProcessAlive() || processController.isProcessAlive() || isHealthy();
    }

    @Override
    public boolean isHealthy() {
        String url = configService.getComfyUIUrl();
        if (url == null || url.isEmpty()) return false;
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/system_stats"))
                    .timeout(Duration.ofMillis(1000))
                    .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean ok = response.statusCode() == 200;
            if (!ok) logger.info("⚠️ [Lifecycle] Health check returned code " + response.statusCode() + " for " + url);
            return ok;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public synchronized void fixSetup() {
        status.set("Fixing setup...");
        stop();
        
        String workingDir = configService.getComfyWorkingDir();
        if (workingDir == null || workingDir.isEmpty()) {
            status.set("Error: No working dir defined for fix");
            return;
        }

        try {
            java.nio.file.Path path = java.nio.file.Paths.get(workingDir);
            java.nio.file.Path backup = java.nio.file.Paths.get(workingDir + "_backup_" + System.currentTimeMillis());
            
            if (java.nio.file.Files.exists(path)) {
                java.nio.file.Files.move(path, backup);
                logger.info("🔧 [Lifecycle] Moved old installation to " + backup);
            }

            // Re-bootstrap
            java.nio.file.Files.createDirectories(path);
            bootstrapper.cloneComfyUI(path, System.out::println)
                .thenCompose(v -> bootstrapper.downloadAndExtractPortablePython(path, System.out::println))
                .thenCompose(py -> bootstrapper.installPip(py, System.out::println)
                    .thenCompose(v2 -> bootstrapper.installRequirements(py, path, System.out::println)))
                .thenRun(() -> {
                    status.set("Fix completed, restarting...");
                    start();
                })
                .exceptionally(e -> {
                    status.set("Error during fix: " + e.getMessage());
                    e.printStackTrace();
                    return null;
                });

        } catch (IOException e) {
            status.set("Error: " + e.getMessage());
        }
    }

    private void triggerBrowserRefresh() {
        new Thread(() -> {
            try {
                Thread.sleep(1500);
                logger.info("🔄 [Lifecycle] Forcing browser refresh via ComfyUI bridge...");
                downloadManager.notifyComfyUI(true);
            } catch (Exception e) {
                logger.error("⚠️ [Lifecycle] Failed to force browser refresh: " + e.getMessage());
            }
        }).start();
    }
}
