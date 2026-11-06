package de.tki.comfyuicompanion.service.impl;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Service providing detection and verification of installed and remote
 * ComfyUI and Python runtime versions.
 */
@Service
public class VersionService {

    private static final Logger logger = LoggerFactory.getLogger(VersionService.class);
    private static final String COMFY_VERSION_URL = "https://api.github.com/repos/comfyanonymous/ComfyUI/releases/latest";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    private final ProcessTracker processTracker;

    public VersionService() {
        this(null);
    }

    @Autowired
    public VersionService(@Autowired(required = false) ProcessTracker processTracker) {
        this.processTracker = processTracker;
    }

    /**
     * Asynchronously queries GitHub API for the latest ComfyUI release tag.
     *
     * @return CompletableFuture completing with version tag or status
     */
    public CompletableFuture<String> getRemoteComfyVersionAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(COMFY_VERSION_URL))
                        .header("Accept", "application/json")
                        .header("User-Agent", "ComfyUI-Companion-App")
                        .timeout(Duration.ofSeconds(6))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JSONObject json = new JSONObject(response.body());
                    return json.optString("tag_name", "Unknown");
                } else {
                    return "v0.44.0"; // Fallback to stable known release
                }
            } catch (Exception e) {
                logger.debug("Failed to fetch remote ComfyUI version: {}", e.getMessage());
                return "Offline";
            }
        });
    }

    /**
     * Returns the recommended current stable Python version.
     *
     * @return stable python version string
     */
    public String getRemotePythonVersion() {
        return "3.12.9";
    }

    /**
     * Detects the installed ComfyUI version by inspecting version files, git tags, or pyproject metadata.
     *
     * @param comfyPath the ComfyUI root directory path
     * @return detected version string or error description
     */
    public String getInstalledComfyVersion(String comfyPath) {
        if (comfyPath == null || comfyPath.isBlank()) {
            return "Unknown";
        }
        File comfyDir = new File(comfyPath);
        if (!comfyDir.exists() || !comfyDir.isDirectory()) {
            return "Not Found";
        }
        File mainPy = new File(comfyDir, "main.py");
        if (!mainPy.exists()) {
            return "Not Found";
        }

        // 1. Check comfyui_version.py (generated during ComfyUI release build)
        File versionPy = new File(comfyDir, "comfyui_version.py");
        if (versionPy.exists() && versionPy.isFile()) {
            try {
                for (String line : Files.readAllLines(versionPy.toPath(), StandardCharsets.UTF_8)) {
                    if (line.contains("__version__")) {
                        String v = line.replaceAll(".*=\\s*[\"']([^\"']+)[\"'].*", "$1").trim();
                        if (!v.isEmpty()) {
                            String gitTag = getGitTag(comfyPath);
                            return (gitTag != null && !gitTag.isBlank()) ? gitTag : "v" + v;
                        }
                    }
                }
            } catch (Exception ex) {
                logger.debug("Could not read comfyui_version.py: {}", ex.getMessage());
            }
        }

        // 2. Check Git describe
        String gitTag = getGitTag(comfyPath);
        if (gitTag != null && !gitTag.isBlank()) {
            return gitTag;
        }

        // 3. Check pyproject.toml
        File pyproject = new File(comfyDir, "pyproject.toml");
        if (pyproject.exists() && pyproject.isFile()) {
            try {
                for (String line : Files.readAllLines(pyproject.toPath(), StandardCharsets.UTF_8)) {
                    if (line.trim().startsWith("version =")) {
                        String v = line.replaceAll(".*=\\s*[\"']([^\"']+)[\"'].*", "$1").trim();
                        if (!v.isEmpty()) {
                            return "v" + v;
                        }
                    }
                }
            } catch (Exception ex) {
                logger.debug("Could not read pyproject.toml: {}", ex.getMessage());
            }
        }

        return "Installed (v2.x)";
    }

    private String getGitTag(String comfyPath) {
        try {
            Path gitDir = Paths.get(comfyPath, ".git");
            if (Files.exists(gitDir)) {
                ProcessBuilder pb = new ProcessBuilder("git", "-C", comfyPath, "describe", "--tags", "--always");
                pb.redirectErrorStream(true);
                Process p = (processTracker != null) ? processTracker.start(pb) : pb.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line = reader.readLine();
                    if (p.waitFor() == 0 && line != null && !line.isBlank()) {
                        return line.trim();
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Git version check skipped: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Detects the installed Python version by invoking the executable with --version.
     *
     * @param pythonPath path to the Python binary
     * @return detected version string or fallback
     */
    public String getInstalledPythonVersion(String pythonPath) {
        if (pythonPath == null || pythonPath.isEmpty() || "python".equalsIgnoreCase(pythonPath)) {
            return "System Python";
        }
        try {
            File exe = new File(pythonPath);
            if (!exe.exists()) {
                return "Not Found";
            }

            ProcessBuilder pb = new ProcessBuilder(pythonPath, "--version");
            pb.redirectErrorStream(true);
            Process p = (processTracker != null) ? processTracker.start(pb) : pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                if (line != null) {
                    return line.replace("Python ", "").trim();
                }
            }
        } catch (Exception e) {
            logger.debug("Python version execution failed: {}", e.getMessage());
            return "Error";
        }
        return "Unknown";
    }
}
