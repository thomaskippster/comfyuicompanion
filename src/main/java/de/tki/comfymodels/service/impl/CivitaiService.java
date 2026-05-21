package de.tki.comfymodels.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfymodels.domain.ModelInfo;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class CivitaiService {
    @Autowired
    private ConfigService configService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public CompletableFuture<List<ModelInfo>> searchModels(String query) {
        String url = "https://civitai.com/api/v1/models?limit=20&query=" + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
        
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .GET();
                
        String apiKey = configService != null ? configService.getCivitaiApiKey() : null;
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            builder.header("Authorization", "Bearer " + apiKey.trim());
        }

        HttpRequest request = builder.build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    List<ModelInfo> results = new ArrayList<>();
                    try {
                        JsonNode root = mapper.readTree(response.body());
                        JsonNode items = root.get("items");
                        if (items != null && items.isArray()) {
                            for (JsonNode item : items) {
                                results.add(mapToModelInfo(item));
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error parsing Civitai response: " + e.getMessage());
                    }
                    return results;
                });
    }

    private ModelInfo mapToModelInfo(JsonNode item) {
        String name = item.get("name").asText();
        String type = item.get("type").asText();
        
        ModelInfo info = new ModelInfo(mapType(type), name, "CIVITAI");
        info.setDescription(item.path("description").asText(""));
        
        // Get first version for download link and image
        JsonNode versions = item.get("modelVersions");
        if (versions != null && versions.isArray() && versions.size() > 0) {
            JsonNode v = versions.get(0);
            info.setUrl(v.path("downloadUrl").asText("MISSING"));
            
            JsonNode images = v.get("images");
            if (images != null && images.isArray() && images.size() > 0) {
                info.setPreviewPath(images.get(0).path("url").asText(null));
            }

            JsonNode files = v.get("files");
            if (files != null && files.isArray() && files.size() > 0) {
                double sizeMb = files.get(0).path("sizeKB").asDouble(0) / 1024.0;
                info.setSize(String.format("%.1f MB", sizeMb));
            }
        }
        
        return info;
    }

    private String mapType(String civitaiType) {
        return switch (civitaiType.toLowerCase()) {
            case "checkpoint" -> "checkpoints";
            case "lora", "locon" -> "loras";
            case "controlnet" -> "controlnet";
            case "upscaler" -> "upscale_models";
            case "vae" -> "vae";
            case "embeddings" -> "embeddings";
            default -> "unknown";
        };
    }
}
