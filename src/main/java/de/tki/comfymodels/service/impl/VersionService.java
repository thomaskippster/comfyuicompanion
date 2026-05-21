package de.tki.comfymodels.service.impl;

import org.json.JSONObject;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

@Service
public class VersionService {
    private static final String COMFY_VERSION_URL = "https://api.github.com/repos/comfyanonymous/ComfyUI/releases/latest";
    private static final String PYTHON_RELEASES_URL = "https://www.python.org/api/v2/downloads/python-3.12.json";
    
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    public java.util.concurrent.CompletableFuture<String> getRemoteComfyVersionAsync() {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(COMFY_VERSION_URL))
                        .header("Accept", "application/json")
                        .header("User-Agent", "ComfyUI-Companion-App")
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    JSONObject json = new JSONObject(response.body());
                    return json.optString("tag_name", "Unknown");
                } else {
                    String error = "Error (Code: " + response.statusCode() + ")";
                    System.err.println("GitHub API request failed with status: " + response.statusCode() + ". Body: " + response.body());
                    return error;
                }
            } catch (Exception e) {
                System.err.println("Failed to fetch remote ComfyUI version: " + e.getMessage());
                return "Offline";
            }
        });
    }

    public String getRemotePythonVersion() {
        return "3.12.9"; // Current stable fallback
    }

    public String getInstalledComfyVersion(String comfyPath) {
        if (comfyPath == null || comfyPath.isEmpty()) return "Unknown";
        File mainPy = new File(comfyPath, "main.py");
        if (!mainPy.exists()) return "Not Found";
        
        try {
            Path gitDir = Paths.get(comfyPath, ".git");
            if (Files.exists(gitDir)) {
                ProcessBuilder pb = new ProcessBuilder("git", "-C", comfyPath, "describe", "--tags", "--always");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line = reader.readLine();
                    if (p.waitFor() == 0 && line != null) return line;
                }
            }
        } catch (Exception e) { /* ignore git errors */ }
        
        return "Installed (v2.x)"; // Generic if not git
    }

    public String getInstalledPythonVersion(String pythonPath) {
        if (pythonPath == null || pythonPath.isEmpty() || pythonPath.equals("python")) return "System Python";
        try {
            File exe = new File(pythonPath);
            if (!exe.exists()) return "Not Found";
            
            ProcessBuilder pb = new ProcessBuilder(pythonPath, "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = reader.readLine();
                if (line != null) return line.replace("Python ", "").trim();
            }
        } catch (Exception e) { return "Error"; }
        return "Unknown";
    }
}
