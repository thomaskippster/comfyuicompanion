package de.tki.comfymodels.service.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfymodels.service.impl.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise implementation of ComfyUiGateway providing unified HTTP communication
 * and dynamic node widget introspection from /object_info.
 */
@Service
public class ComfyUiGatewayImpl implements ComfyUiGateway {

    private static final Logger logger = LoggerFactory.getLogger(ComfyUiGatewayImpl.class);

    private final ConfigService configService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private final Map<String, List<String>> widgetCache = new ConcurrentHashMap<>();
    private volatile JsonNode objectInfoCache = null;

    @Autowired
    public ComfyUiGatewayImpl(ConfigService configService, ObjectMapper objectMapper) {
        this.configService = configService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    private String getComfyBaseUrl() {
        return configService != null ? configService.getComfyUIUrl() : "http://127.0.0.1:8188";
    }

    @Override
    public boolean isServerOnline() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getComfyBaseUrl() + "/system_stats"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<Void> resp = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public CompletableFuture<JsonNode> getObjectInfo() {
        if (objectInfoCache != null) {
            return CompletableFuture.completedFuture(objectInfoCache);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getComfyBaseUrl() + "/object_info"))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonNode node = objectMapper.readTree(response.body());
                            this.objectInfoCache = node;
                            populateWidgetCache(node);
                            return node;
                        } catch (Exception e) {
                            logger.error("Failed to parse /object_info JSON: {}", e.getMessage());
                        }
                    }
                    return null;
                });
    }

    private void populateWidgetCache(JsonNode objectInfo) {
        if (objectInfo == null || !objectInfo.isObject()) return;

        Iterator<Map.Entry<String, JsonNode>> fields = objectInfo.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String classType = entry.getKey();
            JsonNode inputNode = entry.getValue().path("input");

            List<String> widgets = new ArrayList<>();
            extractWidgetNames(inputNode.path("required"), widgets);
            extractWidgetNames(inputNode.path("optional"), widgets);

            widgetCache.put(classType, widgets);
        }
        logger.info("Dynamically populated widget cache with {} node specifications from ComfyUI", widgetCache.size());
    }

    private void extractWidgetNames(JsonNode groupNode, List<String> target) {
        if (groupNode.isObject()) {
            Iterator<String> fieldNames = groupNode.fieldNames();
            while (fieldNames.hasNext()) {
                String name = fieldNames.next();
                JsonNode spec = groupNode.get(name);
                // In ComfyUI /object_info, widget inputs are tuples where the first element is a list of choices or a primitive type string
                if (spec != null && spec.isArray() && spec.size() > 0) {
                    target.add(name);
                }
            }
        }
    }

    @Override
    public List<String> getWidgetInputNames(String classType) {
        if (classType == null) return List.of();
        List<String> cached = widgetCache.get(classType);
        if (cached != null) return cached;

        // If not cached yet, attempt lazy fetch or return empty
        if (objectInfoCache == null) {
            try {
                getObjectInfo().get();
                List<String> refreshed = widgetCache.get(classType);
                if (refreshed != null) return refreshed;
            } catch (Exception ignored) {}
        }
        return List.of();
    }

    @Override
    public CompletableFuture<String> submitPrompt(String promptPayload) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getComfyBaseUrl() + "/prompt"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(promptPayload, StandardCharsets.UTF_8))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonNode json = objectMapper.readTree(response.body());
                            return json.path("prompt_id").asText();
                        } catch (Exception e) {
                            logger.error("Failed to parse prompt submission response: {}", e.getMessage());
                        }
                    }
                    throw new RuntimeException("Prompt submission rejected. Status: " + response.statusCode());
                });
    }

    @Override
    public CompletableFuture<String> uploadAsset(File file) {
        if (file == null || !file.exists()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("File does not exist"));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String boundary = "---ComfyGateway" + System.currentTimeMillis() + "---";
                String mimeType = file.getName().endsWith(".wav") ? "audio/wav" : "image/png";

                byte[] header = ("--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; name=\"image\"; filename=\"" + file.getName() + "\"\r\n" +
                        "Content-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
                byte[] footer = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
                byte[] fileBytes = Files.readAllBytes(file.toPath());

                HttpRequest uploadReq = HttpRequest.newBuilder()
                        .uri(URI.create(getComfyBaseUrl() + "/upload/image"))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArrays(List.of(header, fileBytes, footer)))
                        .build();

                HttpResponse<String> response = httpClient.send(uploadReq, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonNode json = objectMapper.readTree(response.body());
                    return json.path("name").asText(file.getName());
                }
                throw new RuntimeException("Upload failed with status: " + response.statusCode());
            } catch (Exception e) {
                throw new RuntimeException("Upload failed: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<byte[]> downloadAsset(String filename, String subfolder, String type) {
        String query = "?filename=" + filename +
                (subfolder != null && !subfolder.isEmpty() ? "&subfolder=" + subfolder : "") +
                (type != null ? "&type=" + type : "&type=output");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getComfyBaseUrl() + "/view" + query))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(HttpResponse::body);
    }

    @Override
    public CompletableFuture<JsonNode> getHistory(String promptId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getComfyBaseUrl() + "/history/" + promptId))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            return objectMapper.readTree(response.body());
                        } catch (Exception e) {
                            logger.error("Failed to parse history JSON: {}", e.getMessage());
                        }
                    }
                    return null;
                });
    }

    @Override
    public CompletableFuture<Void> triggerModelRefresh() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getComfyBaseUrl() + "/cmfc/refresh-models"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString("{\"force_reload\": true}"))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenAccept(res -> logger.info("Model refresh signal dispatched to ComfyUI (status {})", res.statusCode()));
    }

    @Override
    public CompletableFuture<JsonNode> getSystemStats() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getComfyBaseUrl() + "/system_stats"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            return objectMapper.readTree(response.body());
                        } catch (Exception ignored) {}
                    }
                    return null;
                });
    }
}
