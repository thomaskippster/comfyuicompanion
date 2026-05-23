package de.tki.comfymodels.service.impl;

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

    private Process comfyProcess;
    private final AtomicReference<String> status = new AtomicReference<>("Stopped");
    private final java.util.concurrent.atomic.AtomicBoolean stopping = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean browserLaunched = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build();

    @Override
    public synchronized void start() {
        if (isRunning()) {
            status.set("Already Running");
            return;
        }

        String command = configService.getComfyLaunchCommand();
        String workingDir = configService.getComfyWorkingDir();

        if (command == null || command.trim().isEmpty()) {
            status.set("Error: No launch command configured");
            return;
        }

        try {
            status.set("Starting...");
            
            java.util.List<String> commandList = de.tki.comfymodels.util.PlatformUtils.parseCommandLine(command);
            
            // Apply active profile arguments if present
            String activeProfileId = configService.getActiveProfile();
            de.tki.comfymodels.domain.LaunchProfile activeProfile = null;
            if (activeProfileId != null && !activeProfileId.isEmpty()) {
                activeProfile = profileManager.loadProfiles().stream()
                        .filter(p -> p.id().equals(activeProfileId))
                        .findFirst().orElse(null);
            }
            if (activeProfile != null && activeProfile.cliArguments() != null) {
                for (String arg : activeProfile.cliArguments()) {
                    if (!commandList.contains(arg)) {
                        commandList.add(arg);
                    }
                }
            }

            System.out.println("🚀 [Lifecycle] Starting command: " + String.join(" ", commandList));
            if (workingDir != null) System.out.println("📁 [Lifecycle] In directory: " + workingDir);

            ProcessBuilder pb = new ProcessBuilder(commandList);
            if (workingDir != null && !workingDir.isEmpty()) {
                pb.directory(new File(workingDir));
            }
            pb.redirectErrorStream(true);

            // Fix buffering and terminal environment setup
            pb.environment().put("PYTHONUNBUFFERED", "1");
            pb.environment().put("PYTHONIOENCODING", "utf-8");
            pb.environment().put("TERM", "dumb");
            pb.environment().put("COLUMNS", "120");
            if (activeProfile != null && activeProfile.envVars() != null) {
                pb.environment().putAll(activeProfile.envVars());
            }

            comfyProcess = pb.start();

            // Quietly consume output to prevent process buffer stalls
            new Thread(() -> {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(comfyProcess.getInputStream()))) {
                    while (reader.readLine() != null) {
                        // Just consume, don't print
                    }
                } catch (java.io.IOException ignored) {}
            }).start();

            // Monitor for readiness and launch browser
            new Thread(() -> {
                try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                boolean launched = false;
                while (!launched && isRunning()) {
                    if (isHealthy()) {
                        String url = configService.getComfyUIUrl();
                        if (browserLaunched.compareAndSet(false, true)) {
                            System.out.println("🌐 [Lifecycle] Health check passed. Launching browser: " + url);
                            try {
                                if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                                    java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
                                } else {
                                    String cmd = de.tki.comfymodels.util.PlatformUtils.isWindows() ? "cmd /c start " + url : "xdg-open " + url;
                                    System.out.println("🌐 [Lifecycle] Desktop API not supported. Executing: " + cmd);
                                    Process p = Runtime.getRuntime().exec(cmd);
                                    if (p.waitFor() != 0) {
                                        System.err.println("⚠️ [Lifecycle] Browser launch process failed with exit code: " + p.exitValue());
                                    }
                                }
                            } catch (Exception e) {
                                System.err.println("❌ [Lifecycle] Failed to open browser: " + e.getMessage());
                                e.printStackTrace();
                            }
                        } else {
                            System.out.println("🌐 [Lifecycle] Health check passed. Browser already launched during this application run.");
                        }
                        launched = true;
                        triggerBrowserRefresh();
                    }
                    try { Thread.sleep(2000); } catch (InterruptedException e) { break; }
                }
            }).start();
            status.set("Running (PID: " + comfyProcess.pid() + ")");
            
            // Background thread to wait for exit
            new Thread(() -> {
                try {
                    int exitCode = comfyProcess.waitFor();
                    status.set("Stopped (Exit Code: " + exitCode + ")");
                    comfyProcess = null;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();

        } catch (IOException e) {
            status.set("Error: " + e.getMessage());
        }
    }

    @Override
    public synchronized void stop() {
        if (stopping.getAndSet(true)) {
            return;
        }
        try {
            status.set("Stopping...");
            
            // 1. Kill internal process if managed
            if (comfyProcess != null) {
                try {
                    comfyProcess.descendants().forEach(ProcessHandle::destroyForcibly);
                    comfyProcess.destroyForcibly();
                    comfyProcess.waitFor();
                } catch (Exception ignored) {}
                comfyProcess = null;
            }

            // 2. Kill the other controller if running
            try {
                processController.stop();
            } catch (Exception e) {
                System.err.println("Failed to stop processController: " + e.getMessage());
            }

            // 3. Kill by port fallback (for external/orphaned instances)
            if (isHealthy()) {
                killProcessOnPort();
            }
            
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
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
                String line = reader.readLine();
                if (line != null) {
                    String[] parts = line.trim().split("\\s+");
                    long pid = Long.parseLong(parts[parts.length - 1]);
                    ProcessHandle.of(pid).ifPresent(ProcessHandle::destroyForcibly);
                }
            } else {
                // Linux/Mac fallback
                Runtime.getRuntime().exec("sh -c fuser -k " + port + "/tcp");
            }
        } catch (Exception e) {
            System.err.println("Failed to kill process on port: " + e.getMessage());
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
            if (!ok) System.out.println("⚠️ [Lifecycle] Health check returned code " + response.statusCode() + " for " + url);
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
                System.out.println("🔧 [Lifecycle] Moved old installation to " + backup);
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
                System.out.println("🔄 [Lifecycle] Forcing browser refresh via ComfyUI bridge...");
                downloadManager.notifyComfyUI(true);
            } catch (Exception e) {
                System.err.println("⚠️ [Lifecycle] Failed to force browser refresh: " + e.getMessage());
            }
        }).start();
    }
}
