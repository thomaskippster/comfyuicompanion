package de.tki.comfymodels.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfymodels.domain.ComfyRegistryWorkflow;
import de.tki.comfymodels.service.IComfyRegistryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class ComfyRegistryClient implements IComfyRegistryClient {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ComfyRegistryClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    // Default API Endpoint for the Comfy.org Registry/Workflows
    private static final String DEFAULT_API_URL = "https://raw.githubusercontent.com/Comfy-Org/workflow_templates/main/templates/index.json";

    public ComfyRegistryClient() {
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    @Override
    public CompletableFuture<List<ComfyRegistryWorkflow>> fetchWorkflowsAsync() {
        return fetchWorkflowsAsync(DEFAULT_API_URL);
    }

    public CompletableFuture<List<ComfyRegistryWorkflow>> fetchWorkflowsAsync(String apiUrl) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("Failed to fetch workflows: HTTP status " + response.statusCode());
                    }
                    return parseWorkflowsJson(response.body());
                })
                .exceptionally(ex -> {
                    logger.error("❌ [ComfyRegistryClient] Error fetching workflows from API: " + ex.getMessage());
                    return getFallbackWorkflows();
                });
    }

    @Override
    public CompletableFuture<byte[]> downloadFileAsync(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("Failed to download file: HTTP status " + response.statusCode());
                    }
                    return response.body();
                });
    }

    private List<ComfyRegistryWorkflow> parseWorkflowsJson(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            List<ComfyRegistryWorkflow> workflows = new ArrayList<>();

            if (root.isArray()) {
                for (JsonNode categoryNode : root) {
                    String categoryTitle = categoryNode.path("title").asText("General");
                    JsonNode templatesNode = categoryNode.path("templates");
                    if (templatesNode.isArray()) {
                        for (JsonNode tNode : templatesNode) {
                            ComfyRegistryWorkflow wf = new ComfyRegistryWorkflow();
                            String name = tNode.path("name").asText("");
                            String title = tNode.path("title").asText("");
                            String description = tNode.path("description").asText("");
                            String mediaType = tNode.path("mediaType").asText("image");
                            String mediaSubtype = tNode.path("mediaSubtype").asText("webp");
                            String date = tNode.path("date").asText("");
                            String username = tNode.path("username").asText("Unknown");
                            double usage = tNode.path("usage").asDouble(0.0);

                            // Required models
                            List<String> models = new ArrayList<>();
                            JsonNode modelsNode = tNode.path("models");
                            if (modelsNode.isArray()) {
                                for (JsonNode m : modelsNode) {
                                    models.add(m.asText());
                                }
                            }

                            wf.setId(name);
                            wf.setTitle(title);
                            wf.setDescription(description);
                            wf.setAuthor(username);
                            wf.setCreatedAt(date);
                            wf.setPopularScore(usage);
                            wf.setRequiredModels(models);
                            wf.setCategory(categoryTitle);

                            // Construct raw Github URLs for JSON and previews
                            // Encode path segments correctly to avoid URI syntax exceptions
                            String encodedName = encodeUrlPath(name);
                            wf.setJsonDownloadUrl("https://raw.githubusercontent.com/Comfy-Org/workflow_templates/main/templates/" + encodedName + ".json");
                            
                            // The registry returns the actual preview as a relative path inside
                            // the "thumbnail" array (e.g. "output/foo.mp4", "thumbnail/foo.png").
                            // mediaType/mediaSubtype are unreliable (mediaType is always "image" even
                            // for video templates). Use the file extension of the first thumbnail
                            // path to decide whether the preview is a video.
                            String rawBase = "https://raw.githubusercontent.com/Comfy-Org/workflow_templates/main/templates/";
                            String thumbRel = null;
                            JsonNode thumbNode = tNode.path("thumbnail");
                            if (thumbNode.isArray() && thumbNode.size() > 0 && thumbNode.get(0).isTextual()) {
                                thumbRel = thumbNode.get(0).asText();
                            }
                            String thumbnailExt = mediaSubtype.isEmpty() ? "webp" : mediaSubtype;
                            wf.setThumbnailUrl(rawBase + encodedName + "-1." + thumbnailExt);

                            if (thumbRel != null && !thumbRel.isEmpty()) {
                                String lower = thumbRel.toLowerCase();
                                if (lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mov")) {
                                    wf.setMediaType("video");
                                } else {
                                    wf.setMediaType(mediaType);
                                }
                            } else {
                                wf.setMediaType(mediaType);
                            }
                            wf.setMediaSubtype(mediaSubtype);

                            workflows.add(wf);
                        }
                    }
                }
            }
            logger.info("ℹ️ [ComfyRegistryClient] Parsed " + workflows.size() + " templates from GitHub index.json");
            return workflows;
        } catch (Exception e) {
            logger.error("❌ [ComfyRegistryClient] Failed to parse workflow JSON: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private String encodeUrlPath(String path) {
        if (path == null) return null;
        try {
            String[] parts = path.split("/");
            for (int i = 0; i < parts.length; i++) {
                parts[i] = java.net.URLEncoder.encode(parts[i], java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
            }
            return String.join("/", parts);
        } catch (Exception e) {
            return path.replace(" ", "%20");
        }
    }

    /**
     * Provides fallback/mock workflows if the API is unreachable, ensuring local testing remains fully operational.
     */
    private List<ComfyRegistryWorkflow> getFallbackWorkflows() {
        logger.info("ℹ️ [ComfyRegistryClient] Loading mock fallback workflows for development/testing.");
        List<ComfyRegistryWorkflow> fallbacks = new ArrayList<>();

        ComfyRegistryWorkflow fluxWorkflow = new ComfyRegistryWorkflow();
        fluxWorkflow.setId("flux-dev-basic");
        fluxWorkflow.setTitle("Flux Dev Basic Image Generator");
        fluxWorkflow.setDescription("A robust, standard text-to-image pipeline for Flux.1 Dev model. Excellent quality and detail.");
        fluxWorkflow.setAuthor("ComfyOrg");
        fluxWorkflow.setCreatedAt("2026-06-01T12:00:00Z");
        fluxWorkflow.setPopularScore(95.5);
        fluxWorkflow.setThumbnailUrl("https://api.comfy.org/thumbnails/flux-dev-basic.png");
        fluxWorkflow.setJsonDownloadUrl("https://api.comfy.org/workflows/flux-dev-basic.json");
        fluxWorkflow.setRequiredModels(List.of("flux1-dev-fp8.safetensors", "ae.safetensors"));
        fallbacks.add(fluxWorkflow);

        ComfyRegistryWorkflow sdXlWorkflow = new ComfyRegistryWorkflow();
        sdXlWorkflow.setId("sdxl-lightning");
        sdXlWorkflow.setTitle("SDXL Lightning 4-Step Generator");
        sdXlWorkflow.setDescription("Ultra-fast 4-step image generation utilizing SDXL Lightning technology. High throughput.");
        sdXlWorkflow.setAuthor("StableDiffusionCommunity");
        sdXlWorkflow.setCreatedAt("2026-05-15T08:30:00Z");
        sdXlWorkflow.setPopularScore(88.2);
        sdXlWorkflow.setThumbnailUrl("https://api.comfy.org/thumbnails/sdxl-lightning.png");
        sdXlWorkflow.setJsonDownloadUrl("https://api.comfy.org/workflows/sdxl-lightning.json");
        sdXlWorkflow.setRequiredModels(List.of("sdxl_lightning_4step.safetensors"));
        fallbacks.add(sdXlWorkflow);

        return fallbacks;
    }
}
