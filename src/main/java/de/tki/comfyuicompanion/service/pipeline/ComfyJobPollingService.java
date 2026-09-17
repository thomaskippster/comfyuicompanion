package de.tki.comfyuicompanion.service.pipeline;

import de.tki.comfyuicompanion.service.PromptBlueprintApiService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;

/**
 * Service responsible for submitting generation prompts to ComfyUI, polling
 * execution history, resolving output artifacts, and uploading media assets.
 */
@Service
public class ComfyJobPollingService {

    private static final Logger logger = LoggerFactory.getLogger(ComfyJobPollingService.class);

    /**
     * Immutable reference to an output artifact produced by ComfyUI.
     *
     * @param filename  the filename of the generated asset
     * @param subfolder optional subfolder containing the file
     * @param type      output storage type (e.g., "output", "temp")
     */
    public record ComfyOutputRef(String filename, String subfolder, String type) {
        public ComfyOutputRef(String filename, String subfolder, String type) {
            this.filename = filename;
            this.subfolder = subfolder != null ? subfolder : "";
            this.type = type != null ? type : "output";
        }
    }

    private final HttpClient httpClient;

    /**
     * Default constructor creating a standard HTTP client with a 15-second timeout.
     */
    public ComfyJobPollingService() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build());
    }

    /**
     * Constructor allowing injection of a custom {@link HttpClient}.
     *
     * @param httpClient custom HTTP client
     */
    @Autowired
    public ComfyJobPollingService(@Autowired(required = false) HttpClient httpClient) {
        this.httpClient = httpClient != null
                ? httpClient
                : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    /**
     * Submits a workflow prompt to the ComfyUI server.
     *
     * @param serverUrl    the base URL of the ComfyUI server
     * @param workflowJson the workflow definition as a JSON object
     * @return the assigned prompt ID
     * @throws IOException          if network communication fails
     * @throws InterruptedException if the operation is interrupted
     */
    public String submitPrompt(String serverUrl, JSONObject workflowJson) throws IOException, InterruptedException {
        JSONObject payload = workflowJson.has("prompt") ? workflowJson : new JSONObject().put("prompt", workflowJson);
        JSONObject promptObj = payload.optJSONObject("prompt");
        if (promptObj != null) {
            PromptBlueprintApiService.sanitizeAllSeedsInPrompt(promptObj);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/prompt"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            JSONObject res = new JSONObject(response.body());
            return res.getString("prompt_id");
        } else {
            throw new IOException("Failed to send prompt to ComfyUI. Status: " + response.statusCode() + ", Body: " + response.body());
        }
    }

    /**
     * Polls the ComfyUI history until the job identified by the prompt ID completes,
     * returning the primary output filename.
     *
     * @param serverUrl the base URL of the ComfyUI server
     * @param promptId  the prompt ID to poll
     * @return the output filename, or an empty string if none found
     * @throws IOException          if polling fails or times out
     * @throws InterruptedException if interrupted while polling
     */
    public String pollHistoryForFilename(String serverUrl, String promptId) throws IOException, InterruptedException {
        ComfyOutputRef ref = pollHistoryForOutput(serverUrl, promptId);
        return ref != null ? ref.filename() : "";
    }

    /**
     * Polls the ComfyUI history until the job identified by the prompt ID completes,
     * returning the {@link ComfyOutputRef} metadata.
     *
     * @param serverUrl the base URL of the ComfyUI server
     * @param promptId  the prompt ID to poll
     * @return the output artifact reference, or null if not resolved
     * @throws IOException          if polling fails, errors, or times out
     * @throws InterruptedException if interrupted while polling
     */
    public ComfyOutputRef pollHistoryForOutput(String serverUrl, String promptId) throws IOException, InterruptedException {
        int timeoutCount = 0;
        while (timeoutCount < 6000) {
            Thread.sleep(3000);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/history/" + promptId))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONObject history = new JSONObject(response.body());
                if (history.has(promptId)) {
                    JSONObject job = history.getJSONObject(promptId);
                    JSONObject status = job.optJSONObject("status");
                    if (status != null && "error".equalsIgnoreCase(status.optString("status_str", ""))) {
                        String errorDetail = extractHistoryError(status);
                        throw new IOException("ComfyUI generation failed: " + errorDetail);
                    }

                    JSONObject outputs = job.optJSONObject("outputs");
                    if (outputs != null) {
                        ComfyOutputRef ref = extractFirstOutput(outputs);
                        if (ref != null) {
                            return ref;
                        }
                    }
                    return null;
                }
            }
            timeoutCount++;
        }
        throw new IOException("Polling timeout waiting for ComfyUI generation job to complete.");
    }

    /**
     * Extracts the first available media artifact from the outputs map.
     *
     * @param outputs the outputs JSON object from ComfyUI history
     * @return the extracted {@link ComfyOutputRef}, or null if none
     */
    public ComfyOutputRef extractFirstOutput(JSONObject outputs) {
        String[] outputKeys = {"videos", "gifs", "images"};
        for (String nodeId : outputs.keySet()) {
            JSONObject outputNode = outputs.getJSONObject(nodeId);
            for (String mediaKey : outputKeys) {
                if (!outputNode.has(mediaKey)) {
                    continue;
                }
                JSONArray arr = outputNode.optJSONArray(mediaKey);
                if (arr == null) {
                    continue;
                }
                for (int i = 0; i < arr.length(); i++) {
                    Object itemObj = arr.get(i);
                    if (itemObj instanceof JSONObject item && item.has("filename")) {
                        return new ComfyOutputRef(
                                item.getString("filename"),
                                item.optString("subfolder", ""),
                                item.optString("type", "output")
                        );
                    }
                }
            }
            for (String nodeKey : outputNode.keySet()) {
                Object value = outputNode.get(nodeKey);
                if (value instanceof JSONArray arr) {
                    for (int i = 0; i < arr.length(); i++) {
                        Object itemObj = arr.get(i);
                        if (itemObj instanceof JSONObject item && item.has("filename")) {
                            return new ComfyOutputRef(
                                    item.getString("filename"),
                                    item.optString("subfolder", ""),
                                    item.optString("type", "output")
                            );
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Extracts readable error messages from the status JSON object.
     *
     * @param status the status JSON object
     * @return a aggregated human-readable error description
     */
    public String extractHistoryError(JSONObject status) {
        JSONArray messages = status.optJSONArray("messages");
        if (messages != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < messages.length(); i++) {
                Object entry = messages.get(i);
                if (entry instanceof JSONArray arr && arr.length() >= 2) {
                    if (sb.length() > 0) {
                        sb.append(" | ");
                    }
                    sb.append(arr.get(1));
                }
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        }
        return status.optString("status_str", "Unknown ComfyUI error");
    }

    /**
     * Resolves an output file on the local filesystem if it is directly accessible.
     *
     * @param comfyOutputPath the ComfyUI base output directory
     * @param outputRef       the output reference
     * @param sceneId         the ID of the scene
     * @return the local file if it exists, or null
     */
    public File resolveOutputFile(String comfyOutputPath, ComfyOutputRef outputRef, String sceneId) {
        if (outputRef != null && outputRef.filename() != null && !outputRef.filename().trim().isEmpty()) {
            File baseDir = outputRef.subfolder().isEmpty()
                    ? new File(comfyOutputPath)
                    : new File(comfyOutputPath, outputRef.subfolder());
            File candidate = new File(baseDir, outputRef.filename());
            if (candidate.exists()) {
                return candidate;
            }
        }

        File outputRoot = new File(comfyOutputPath);
        String prefix = "videoarchitect_" + sceneId;
        String[] suffixes = {".mp4", "_00001.mp4", ".webm", "_00001.webm"};
        for (String suffix : suffixes) {
            File direct = new File(outputRoot, prefix + suffix);
            if (direct.exists()) {
                return direct;
            }
            File nested = new File(new File(outputRoot, "video"), prefix + suffix);
            if (nested.exists()) {
                return nested;
            }
        }
        return null;
    }

    /**
     * Downloads an output artifact via HTTP from the ComfyUI /view endpoint.
     *
     * @param serverUrl the base URL of the ComfyUI server
     * @param outputRef the output reference to download
     * @param sceneId   the ID of the scene
     * @return the downloaded local file
     * @throws IOException          if the download fails
     * @throws InterruptedException if interrupted during download
     */
    public File downloadOutput(String serverUrl, ComfyOutputRef outputRef, String sceneId) throws IOException, InterruptedException {
        if (outputRef == null || outputRef.filename() == null || outputRef.filename().trim().isEmpty()) {
            throw new IOException("No output filename available for download.");
        }

        Path targetDir = Paths.get("").toAbsolutePath();
        String localFilename = "scene_" + sceneId + "_" + outputRef.filename();
        Path targetFile = targetDir.resolve(localFilename);

        StringBuilder downloadUrl = new StringBuilder(serverUrl)
                .append("/view?filename=")
                .append(URLEncoder.encode(outputRef.filename(), StandardCharsets.UTF_8))
                .append("&type=")
                .append(URLEncoder.encode(outputRef.type(), StandardCharsets.UTF_8));
        if (!outputRef.subfolder().isEmpty()) {
            downloadUrl.append("&subfolder=")
                    .append(URLEncoder.encode(outputRef.subfolder(), StandardCharsets.UTF_8));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl.toString()))
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() == 200) {
            try (InputStream is = response.body()) {
                Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
            logger.info("💾 [ComfyPipeline] Downloaded output to: {}", targetFile);
            return targetFile.toFile();
        } else {
            throw new IOException("Failed to download output file. Status: " + response.statusCode());
        }
    }

    /**
     * Uploads a local file to the ComfyUI server via multipart form data.
     *
     * @param serverUrl the base URL of the ComfyUI server
     * @param file      the local file to upload
     * @return the uploaded filename registered on the server
     * @throws IOException          if upload fails
     * @throws InterruptedException if interrupted during upload
     */
    public String uploadFile(String serverUrl, File file) throws IOException, InterruptedException {
        String boundary = "---" + System.currentTimeMillis() + "---";
        String mimeType = file.getName().endsWith(".wav") ? "audio/wav" : (file.getName().endsWith(".mp3") ? "audio/mpeg" : "image/png");

        byte[] header = ("--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"image\"; filename=\"" + file.getName() + "\"\r\n" +
                "Content-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] footer = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] fileBytes = Files.readAllBytes(file.toPath());

        List<byte[]> body = List.of(header, fileBytes, footer);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/upload/image"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArrays(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            JSONObject res = new JSONObject(response.body());
            return res.getString("name");
        } else {
            throw new IOException("Failed to upload file to ComfyUI. Status: " + response.statusCode() + ", Body: " + response.body());
        }
    }
}
