package de.tki.comfymodels.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class ComfyProcessController {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ComfyProcessController.class);
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private de.tki.comfymodels.service.IComfyLifecycleService lifecycleService;

    @org.springframework.beans.factory.annotation.Autowired
    private ConfigService configService;

    private volatile Process currentProcess;
    private final java.util.concurrent.atomic.AtomicBoolean stopping = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean guiLineShown = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final AtomicReference<String> detectedUrl = new AtomicReference<>();
    private final Pattern urlPattern = Pattern.compile("(https?://[\\d\\.]+(?::\\d+)?)");


    @Autowired(required = false)
    private ProcessTracker processTracker;
    public String getDetectedUrl() {
        return detectedUrl.get();
    }
    
    public boolean isGuiLineShown() {
        return guiLineShown.get();
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
                    
                    // Dynamically configure ._pth file if using embedded Python
                    if (pythonPath != null && !pythonPath.isEmpty()) {
                        try {
                            java.io.File exeFile = new java.io.File(pythonPath);
                            java.io.File parentDir = exeFile.getParentFile();
                            if (parentDir != null && parentDir.exists()) {
                                java.io.File[] pthFiles = parentDir.listFiles((dir, name) -> name.endsWith("._pth"));
                                if (pthFiles != null) {
                                    for (java.io.File pthFile : pthFiles) {
                                        String absoluteComfyPath = comfyDir.toAbsolutePath().toString();
                                        String normalizedPath = absoluteComfyPath.replace("\\", "/").toLowerCase();
                                        
                                        java.util.List<String> lines = new java.util.ArrayList<>();
                                        boolean hasSite = false;
                                        boolean hasComfy = false;
                                        boolean changed = false;
                                        
                                        for (String line : java.nio.file.Files.readAllLines(pthFile.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                                            String trimmed = line.trim();
                                            if (trimmed.equals("import site") || trimmed.equals("#import site")) {
                                                lines.add("import site");
                                                hasSite = true;
                                            } else {
                                                boolean isStaleComfy = false;
                                                try {
                                                    java.nio.file.Path p = java.nio.file.Paths.get(trimmed);
                                                    if (p.isAbsolute()) {
                                                        String lineNormalized = p.toAbsolutePath().toString().replace("\\", "/").toLowerCase();
                                                        if (lineNormalized.equals(normalizedPath)) {
                                                            hasComfy = true;
                                                            lines.add(trimmed);
                                                        } else {
                                                            java.io.File dir = p.toFile();
                                                            if (dir.exists() && dir.isDirectory() && new java.io.File(dir, "main.py").exists()) {
                                                                isStaleComfy = true;
                                                            } else {
                                                                lines.add(line);
                                                            }
                                                        }
                                                    } else {
                                                        lines.add(line);
                                                    }
                                                } catch (Exception ex) {
                                                    lines.add(line);
                                                }
                                                if (isStaleComfy) {
                                                    changed = true;
                                                    logConsumer.accept("🧹 Removing stale ComfyUI path from ._pth file: " + trimmed);
                                                }
                                            }
                                        }
                                        if (!hasSite) {
                                            lines.add("import site");
                                            changed = true;
                                        }
                                        if (!hasComfy) {
                                            lines.add(absoluteComfyPath);
                                            changed = true;
                                        }
                                        if (changed) {
                                            java.nio.file.Files.write(pthFile.toPath(), lines, java.nio.charset.StandardCharsets.UTF_8);
                                            logConsumer.accept("📝 Updated python _pth file: " + pthFile.getName());
                                        }
                                    }
                                }
                            }
                        } catch (Exception ex) {
                            logConsumer.accept("⚠️ Failed to update python _pth files: " + ex.getMessage());
                        }
                    }
                }
                
                // Dynamische Argument-Übergabe
                if (profile.cliArguments() != null) {
                    command.addAll(profile.cliArguments());
                }

                // Ensure standard server arguments are present if not already overridden by the profile
                boolean hasListen = false;
                boolean hasPort = false;
                boolean hasEnableManager = false;
                boolean hasExtraPathsConfig = false;
                for (String arg : command) {
                    if (arg.equals("--listen")) {
                        hasListen = true;
                    }
                    if (arg.equals("--port")) {
                        hasPort = true;
                    }
                    if (arg.equals("--enable-manager")) {
                        hasEnableManager = true;
                    }
                    if (arg.equals("--extra-model-paths-config")) {
                        hasExtraPathsConfig = true;
                    }
                }

                if (!hasListen) {
                    command.add("--listen");
                    command.add("127.0.0.1");
                }
                if (!hasPort) {
                    int port = 8188;
                    try {
                        String url = configService.getComfyUIUrl();
                        int colonIndex = url.lastIndexOf(":");
                        if (colonIndex != -1) {
                            port = Integer.parseInt(url.substring(colonIndex + 1));
                        }
                    } catch (Exception ignored) {}
                    command.add("--port");
                    command.add(String.valueOf(port));
                }
                if (!hasEnableManager) {
                    command.add("--enable-manager");
                }
                if (!hasExtraPathsConfig) {
                    java.nio.file.Path extraPathsFile = comfyDir.resolve("extra_model_paths.yaml");
                    if (java.nio.file.Files.exists(extraPathsFile)) {
                        command.add("--extra-model-paths-config");
                        command.add(extraPathsFile.toAbsolutePath().toString());
                    }
                }

                // Check if extra ComfyUI data path is set and pass input/output directories to ComfyUI if different from root
                boolean hasInputDirectory = false;
                boolean hasOutputDirectory = false;
                for (String arg : command) {
                    if (arg.equals("--input-directory")) {
                        hasInputDirectory = true;
                    }
                    if (arg.equals("--output-directory")) {
                        hasOutputDirectory = true;
                    }
                }
                String extraComfyUIPath = configService.getExtraComfyUIPath();
                String comfyRootStr = configService.getComfyUIPath();
                if (extraComfyUIPath != null && !extraComfyUIPath.isEmpty() && java.nio.file.Paths.get(extraComfyUIPath).isAbsolute()) {
                    boolean isDifferent = comfyRootStr == null || comfyRootStr.isEmpty() || 
                                          !java.nio.file.Paths.get(extraComfyUIPath).toAbsolutePath().toString().equalsIgnoreCase(java.nio.file.Paths.get(comfyRootStr).toAbsolutePath().toString());
                    
                    if (isDifferent) {
                        if (!hasInputDirectory) {
                            command.add("--input-directory");
                            command.add(configService.getResolvedInputDetailDir());
                        }
                        if (!hasOutputDirectory) {
                            command.add("--output-directory");
                            command.add(configService.getResolvedOutputDir());
                        }
                    }
                }

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(comfyDir.toFile());
                
                // Fix for OSError [Errno 22] on Windows: disable buffering and complex terminal features
                pb.environment().put("PYTHONUNBUFFERED", "1");
                pb.environment().put("PYTHONIOENCODING", "utf-8");
                pb.environment().put("TERM", "dumb");
                pb.environment().put("COLUMNS", "120");
                pb.environment().put("GIT_PYTHON_REFRESH", "quiet");
                
                if (profile.envVars() != null) {
                    pb.environment().putAll(profile.envVars());
                }

                String cmdStr = String.join(" ", command);
                logConsumer.accept("🚀 Launching: " + cmdStr);
                
                Process p = processTracker.start(pb);
                this.currentProcess = p; // Assign to volatile field

                // Enhanced log consumer to capture URL
                Consumer<String> enhancedLogger = line -> {
                    logConsumer.accept(line);
                    if (line.contains("To see the GUI go to:")) {
                        guiLineShown.set(true);
                    }
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
            guiLineShown.set(false);
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
                logger.error("Failed to stop lifecycleService: " + e.getMessage());
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
