package de.tki.comfymodels.service.impl;

import de.tki.comfymodels.service.impl.ConfigService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;

/**
 * Service providing HTTP API interaction with the ComfyUI backend server,
 * encapsulating endpoint calls, payload submissions, and polling functions.
 */
@Service
public class ComfyApiClient {

    private static final Logger logger = LoggerFactory.getLogger(ComfyApiClient.class);

    private final ConfigService configService;
    private final HttpClient httpClient;
    private final de.tki.comfymodels.service.gateway.ComfyUiGateway comfyUiGateway;

    @Autowired
    public ComfyApiClient(ConfigService configService, @Autowired(required = false) de.tki.comfymodels.service.gateway.ComfyUiGateway comfyUiGateway) {
        this.configService = configService;
        this.comfyUiGateway = comfyUiGateway;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Checks if the ComfyUI server is reachable and responding with 200 OK on /object_info.
     */
    public boolean isServerOnline() {
        if (comfyUiGateway != null) {
            return comfyUiGateway.isServerOnline();
        }
        return isServerOnline(configService.getComfyUIUrl());
    }

    public boolean isServerOnline(String comfyUrl) {
        try {
            HttpResponse<String> response = fetchObjectInfoRaw(comfyUrl);
            return response != null && response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Fetches raw response from /object_info endpoint.
     */
    public HttpResponse<String> fetchObjectInfoRaw(String comfyUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(comfyUrl + "/object_info"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Fetches and parses /object_info as a JSONObject.
     */
    public JSONObject getObjectInfo() throws IOException, InterruptedException {
        return getObjectInfo(configService.getComfyUIUrl());
    }

    public JSONObject getObjectInfo(String comfyUrl) throws IOException, InterruptedException {
        HttpResponse<String> response = fetchObjectInfoRaw(comfyUrl);
        if (response != null && response.statusCode() == 200) {
            return new JSONObject(response.body());
        }
        throw new IOException("Failed to fetch object_info. Status: " + (response != null ? response.statusCode() : "null"));
    }

    /**
     * Sends a GUI workflow for conversion to API format via /cmfc/convert-workflow.
     */
    public HttpResponse<String> convertWorkflow(JSONObject workflowJson, String callbackUrl) throws IOException, InterruptedException {
        return convertWorkflow(configService.getComfyUIUrl(), workflowJson, callbackUrl);
    }

    public HttpResponse<String> convertWorkflow(String comfyUrl, JSONObject workflowJson, String callbackUrl) throws IOException, InterruptedException {
        JSONObject convRequest = new JSONObject();
        convRequest.put("workflow", workflowJson.has("nodes") ? workflowJson : workflowJson.optJSONObject("workflow"));
        convRequest.put("callbackUrl", callbackUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(comfyUrl + "/cmfc/convert-workflow"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(convRequest.toString()))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Uploads an input image or audio file to the ComfyUI input directory via /upload/image.
     * @return The filename returned by ComfyUI.
     */
    public String uploadInputImage(File imageFile) throws IOException, InterruptedException {
        if (comfyUiGateway != null) {
            try {
                return comfyUiGateway.uploadAsset(imageFile).join();
            } catch (Exception e) {
                logger.warn("Gateway upload failed, falling back to direct client: {}", e.getMessage());
            }
        }
        return uploadInputImage(configService.getComfyUIUrl(), imageFile);
    }

    public String uploadInputImage(String comfyUrl, File imageFile) throws IOException, InterruptedException {
        if (imageFile == null || !imageFile.exists()) {
            throw new IllegalArgumentException("File does not exist or is null");
        }
        String boundary = "---" + System.currentTimeMillis() + "---";
        String mimeType = imageFile.getName().endsWith(".wav") ? "audio/wav" : (imageFile.getName().endsWith(".mp3") ? "audio/mpeg" : "image/png");

        byte[] header = ("--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"image\"; filename=\"" + imageFile.getName() + "\"\r\n" +
                "Content-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] footer = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] fileBytes = Files.readAllBytes(imageFile.toPath());

        List<byte[]> body = List.of(header, fileBytes, footer);

        HttpRequest uploadReq = HttpRequest.newBuilder()
                .uri(URI.create(comfyUrl + "/upload/image"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArrays(body))
                .build();

        HttpResponse<String> uploadRes = httpClient.send(uploadReq, HttpResponse.BodyHandlers.ofString());
        if (uploadRes.statusCode() == 200) {
            JSONObject resObj = new JSONObject(uploadRes.body());
            return resObj.getString("name");
        } else {
            throw new IOException("Upload failed. Status: " + uploadRes.statusCode() + " Body: " + uploadRes.body());
        }
    }

    /**
     * Posts prompt JSON payload to /prompt.
     */
    public HttpResponse<String> postPrompt(String jsonPayload) throws IOException, InterruptedException {
        return postPrompt(configService.getComfyUIUrl(), jsonPayload);
    }

    public HttpResponse<String> postPrompt(String comfyUrl, String jsonPayload) throws IOException, InterruptedException {
        String safePayload = jsonPayload;
        try {
            org.json.JSONObject obj = new org.json.JSONObject(jsonPayload);
            org.json.JSONObject promptObj = obj.has("prompt") ? obj.optJSONObject("prompt") : obj;
            if (promptObj != null) {
                de.tki.comfymodels.service.PromptBlueprintApiService.sanitizeAllSeedsInPrompt(promptObj);
                safePayload = obj.toString();
            }
        } catch (Exception ignored) {}

        HttpRequest promptReq = HttpRequest.newBuilder()
                .uri(URI.create(comfyUrl + "/prompt"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(safePayload))
                .build();

        return httpClient.send(promptReq, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Forces ComfyUI to refresh its models list.
     */
    public HttpResponse<String> refreshModels(String comfyUrl, boolean forceReload) throws IOException, InterruptedException {
        String json = "{\"force_reload\": " + forceReload + "}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(comfyUrl + "/cmfc/refresh-models"))
                .timeout(java.time.Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Checks prompt execution history from /history/{promptId}.
     * Returns ImageOutput if an output image is available, or null if not yet ready.
     */
    public ImageOutput fetchPromptHistoryImage(String promptId) throws IOException, InterruptedException {
        return fetchPromptHistoryImage(configService.getComfyUIUrl(), promptId);
    }

    public ImageOutput fetchPromptHistoryImage(String comfyUrl, String promptId) throws IOException, InterruptedException {
        HttpRequest histReq = HttpRequest.newBuilder()
                .uri(URI.create(comfyUrl + "/history/" + promptId))
                .GET()
                .build();
        HttpResponse<String> histResp = httpClient.send(histReq, HttpResponse.BodyHandlers.ofString());

        if (histResp.statusCode() == 200) {
            JSONObject histObj = new JSONObject(histResp.body());
            if (histObj.has(promptId)) {
                JSONObject promptHistory = histObj.getJSONObject(promptId);
                JSONObject outputs = promptHistory.optJSONObject("outputs");
                if (outputs != null) {
                    for (String nodeKey : outputs.keySet()) {
                        JSONObject nodeOutputs = outputs.getJSONObject(nodeKey);
                        if (nodeOutputs.has("images")) {
                            JSONArray images = nodeOutputs.getJSONArray("images");
                            if (images.length() > 0) {
                                JSONObject img = images.getJSONObject(0);
                                String filename = img.optString("filename");
                                String subfolder = img.optString("subfolder", "");
                                String type = img.optString("type", "output");
                                return new ImageOutput(filename, subfolder, type);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Fetches current queue status from /queue and checks status for promptId.
     */
    public QueueInfo fetchQueueStatus(String promptId) throws IOException, InterruptedException {
        return fetchQueueStatus(configService.getComfyUIUrl(), promptId);
    }

    public QueueInfo fetchQueueStatus(String comfyUrl, String promptId) throws IOException, InterruptedException {
        HttpRequest qReq = HttpRequest.newBuilder()
                .uri(URI.create(comfyUrl + "/queue"))
                .GET()
                .build();
        HttpResponse<String> qResp = httpClient.send(qReq, HttpResponse.BodyHandlers.ofString());
        if (qResp.statusCode() == 200) {
            JSONObject qObj = new JSONObject(qResp.body());
            JSONArray running = qObj.optJSONArray("queue_running");
            JSONArray pending = qObj.optJSONArray("queue_pending");

            if (running != null) {
                for (int i = 0; i < running.length(); i++) {
                    JSONArray item = running.getJSONArray(i);
                    if (item.length() > 1 && promptId.equals(item.getString(1))) {
                        return new QueueInfo(QueueState.RUNNING, 0);
                    }
                }
            }
            if (pending != null) {
                for (int i = 0; i < pending.length(); i++) {
                    JSONArray item = pending.getJSONArray(i);
                    if (item.length() > 1 && promptId.equals(item.getString(1))) {
                        return new QueueInfo(QueueState.PENDING, i + 1);
                    }
                }
            }
        }
        return new QueueInfo(QueueState.UNKNOWN, -1);
            }

    public record ImageOutput(String filename, String subfolder, String type) {}

    public enum QueueState {
        RUNNING, PENDING, UNKNOWN
    }

    public record QueueInfo(QueueState state, int position) {}
}
