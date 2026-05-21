package de.tki.comfymodels.service.impl;

import de.tki.comfymodels.domain.LaunchProfile;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ComfyProcessController {
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private de.tki.comfymodels.service.IComfyLifecycleService lifecycleService;

    private volatile Process currentProcess;
    private final java.util.concurrent.atomic.AtomicBoolean stopping = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final AtomicReference<String> detectedUrl = new AtomicReference<>();
    private final Pattern urlPattern = Pattern.compile("(https?://[\\d\\.]+(?::\\d+)?)");

    public String getDetectedUrl() {
        return detectedUrl.get();
    }

    public CompletableFuture<Integer> start(LaunchProfile profile, Path comfyDir, String globalPythonPath, Consumer<String> logConsumer) {
        if (isRunning()) {
            logConsumer.accept("⚠️ ComfyUI is already running. Spawning duplicate process blocked.");
            return CompletableFuture.completedFuture(-1);
        }
        detectedUrl.set(null);
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<String> command = new ArrayList<>();
                
                // OS-Weiche & Command Building
                if (profile.executeInWsl()) {
                    command.addAll(List.of("wsl", "python3", "main.py"));
                } else {
                    String pythonPath = (profile.pythonPath() == null || profile.pythonPath().equals("python")) 
                                        ? globalPythonPath : profile.pythonPath();
                    command.add(pythonPath);
                    command.add("main.py");
                }
                
                // Dynamische Argument-Übergabe
                if (profile.cliArguments() != null) {
                    command.addAll(profile.cliArguments());
                }

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(comfyDir.toFile());
                
                // Fix for OSError [Errno 22] on Windows: disable buffering and complex terminal features
                pb.environment().put("PYTHONUNBUFFERED", "1");
                pb.environment().put("PYTHONIOENCODING", "utf-8");
                pb.environment().put("TERM", "dumb");
                pb.environment().put("COLUMNS", "120");
                
                if (profile.envVars() != null) {
                    pb.environment().putAll(profile.envVars());
                }

                String cmdStr = String.join(" ", command);
                logConsumer.accept("🚀 Launching: " + cmdStr);
                
                Process p = pb.start();
                this.currentProcess = p; // Assign to volatile field

                // Enhanced log consumer to capture URL
                Consumer<String> enhancedLogger = line -> {
                    logConsumer.accept(line);
                    if (detectedUrl.get() == null && line.contains("To see the GUI go to:")) {
                        Matcher m = urlPattern.matcher(line);
                        if (m.find()) detectedUrl.set(m.group());
                    }
                };

                // Stream Gobblers starten, um Deadlocks zu vermeiden
                captureStream(p.getInputStream(), enhancedLogger);
                captureStream(p.getErrorStream(), enhancedLogger);

                int exitCode = p.waitFor();
                logConsumer.accept("ℹ️ Process exited with code: " + exitCode);
                return exitCode;
            } catch (IOException | InterruptedException e) {
                logConsumer.accept("❌ Critical error during startup: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    private void captureStream(java.io.InputStream is, Consumer<String> consumer) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    consumer.accept(line);
                }
            } catch (IOException ignored) {}
        });
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        if (stopping.getAndSet(true)) {
            return;
        }
        try {
            if (currentProcess != null && currentProcess.isAlive()) {
                currentProcess.destroy(); // Sanfter Stop (SIGTERM)
                try {
                    if (!currentProcess.waitFor(5, TimeUnit.SECONDS)) {
                        currentProcess.destroyForcibly(); // Harter Abbruch (SIGKILL)
                    }
                } catch (InterruptedException e) {
                    currentProcess.destroyForcibly();
                    Thread.currentThread().interrupt();
                }
            }

            try {
                lifecycleService.stop();
            } catch (Exception e) {
                System.err.println("Failed to stop lifecycleService: " + e.getMessage());
            }
        } finally {
            stopping.set(false);
        }
    }

    public boolean isProcessAlive() {
        return currentProcess != null && currentProcess.isAlive();
    }

    public boolean isRunning() {
        return isProcessAlive() || lifecycleService.isProcessAlive() || lifecycleService.isHealthy();
    }
}
