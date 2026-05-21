package de.tki.comfymodels.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfymodels.domain.ModelInfo;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class HuggingFaceService {
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public CompletableFuture<List<ModelInfo>> searchModels(String query) {
        // HF Search API: https://huggingface.co/api/models?search=...&limit=20&full=full
        String url = "https://huggingface.co/api/models?limit=20&full=full&search=" + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    List<ModelInfo> results = new ArrayList<>();
                    try {
                        JsonNode items = mapper.readTree(response.body());
                        if (items != null && items.isArray()) {
                            for (JsonNode item : items) {
                                results.add(mapToModelInfo(item));
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error parsing HuggingFace response: " + e.getMessage());
                    }
                    return results;
                });
    }

    private ModelInfo mapToModelInfo(JsonNode item) {
        String id = item.get("id").asText();
        String name = id.contains("/") ? id.substring(id.lastIndexOf("/") + 1) : id;
        
        ModelInfo info = new ModelInfo("checkpoints", name, "HUGGINGFACE");
        info.setDescription("Repo: " + id);
        
        // HF Download URL construction (simplified, usually safetensors)
        info.setUrl("https://huggingface.co/" + id + "/resolve/main/" + name + ".safetensors");
        
        // Use a generic HF icon or if we find a preview image in metadata
        // For now, no preview for HF to keep it simple, or use a placeholder
        
        return info;
    }
}
