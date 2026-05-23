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

    public CompletableFuture<com.fasterxml.jackson.databind.JsonNode> searchModelsRaw(String query) {
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
                    try {
                        return mapper.readTree(response.body());
                    } catch (Exception e) {
                        System.err.println("Error parsing Civitai response: " + e.getMessage());
                        return null;
                    }
                });
    }

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

    public void downloadMetadataAndPreview(String url, java.nio.file.Path targetFile) {
        try {
            String versionId = null;
            if (url != null) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("civitai\\.com/api/download/models/(\\d+)").matcher(url);
                if (m.find()) {
                    versionId = m.group(1);
                } else {
                    java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("modelVersionId=(\\d+)").matcher(url);
                    if (m2.find()) {
                        versionId = m2.group(1);
                    }
                }
            }
            if (versionId == null) {
                return;
            }
            
            String metadataUrl = "https://civitai.com/api/v1/model-versions/" + versionId;
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(metadataUrl))
                    .header("Accept", "application/json")
                    .GET();
            String apiKey = configService != null ? configService.getCivitaiApiKey() : null;
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                builder.header("Authorization", "Bearer " + apiKey.trim());
            }
            
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = mapper.readTree(response.body());
                
                String baseName = targetFile.getFileName().toString();
                int extIdx = baseName.lastIndexOf('.');
                String prefix = extIdx > 0 ? baseName.substring(0, extIdx) : baseName;
                java.nio.file.Path infoFile = targetFile.getParent().resolve(prefix + ".civitai.info");
                
                java.util.Map<String, Object> infoData = new java.util.HashMap<>();
                infoData.put("id", root.path("id").asInt());
                infoData.put("modelId", root.path("modelId").asInt());
                infoData.put("name", root.path("name").asText(""));
                infoData.put("baseModel", root.path("baseModel").asText(""));
                infoData.put("description", root.path("description").asText(""));
                
                List<String> triggers = new ArrayList<>();
                JsonNode trainedWords = root.get("trainedWords");
                if (trainedWords != null && trainedWords.isArray()) {
                    for (JsonNode tw : trainedWords) {
                        triggers.add(tw.asText());
                    }
                }
                infoData.put("trainedWords", triggers);
                
                java.nio.file.Files.writeString(infoFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(infoData), java.nio.charset.StandardCharsets.UTF_8);
                System.out.println("💾 Saved Civitai metadata: " + infoFile);
                
                JsonNode images = root.get("images");
                if (images != null && images.isArray() && images.size() > 0) {
                    String imgUrl = images.get(0).path("url").asText(null);
                    if (imgUrl != null) {
                        java.nio.file.Path previewFile = targetFile.getParent().resolve(prefix + ".preview.png");
                        HttpRequest imgReq = HttpRequest.newBuilder().uri(URI.create(imgUrl)).GET().build();
                        HttpResponse<byte[]> imgRes = httpClient.send(imgReq, HttpResponse.BodyHandlers.ofByteArray());
                        if (imgRes.statusCode() == 200) {
                            java.nio.file.Files.write(previewFile, imgRes.body());
                            System.out.println("🖼️ Saved Civitai preview: " + previewFile);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to download Civitai metadata or preview: " + e.getMessage());
        }
    }

    public static class ModelUpdateInfo {
        public String localFileName;
        public String modelName;
        public String localVersionName;
        public String newVersionName;
        public String downloadUrl;
        public String updateDescription;
        public boolean hasUpdate;
    }

    public ModelUpdateInfo checkForUpdate(java.io.File modelFile, String hash) {
        if (hash == null || hash.isEmpty()) return null;
        
        try {
            // 1. Get version details by hash
            String hashUrl = "https://civitai.com/api/v1/model-versions/by-hash/" + hash;
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(hashUrl))
                    .header("Accept", "application/json")
                    .GET();
            String apiKey = configService != null ? configService.getCivitaiApiKey() : null;
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                builder.header("Authorization", "Bearer " + apiKey.trim());
            }
            
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null; // Not found or error
            }
            
            JsonNode root = mapper.readTree(response.body());
            int currentVersionId = root.path("id").asInt();
            int modelId = root.path("modelId").asInt();
            String modelName = root.path("model").path("name").asText("Unknown Model");
            String currentVersionName = root.path("name").asText("v1.x");
            
            // 2. Get all versions of this model
            String modelUrl = "https://civitai.com/api/v1/models/" + modelId;
            HttpRequest.Builder builder2 = HttpRequest.newBuilder()
                    .uri(URI.create(modelUrl))
                    .header("Accept", "application/json")
                    .GET();
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                builder2.header("Authorization", "Bearer " + apiKey.trim());
            }
            
            HttpResponse<String> response2 = httpClient.send(builder2.build(), HttpResponse.BodyHandlers.ofString());
            if (response2.statusCode() != 200) {
                return null;
            }
            
            JsonNode modelRoot = mapper.readTree(response2.body());
            JsonNode versions = modelRoot.path("modelVersions");
            if (versions.isArray() && versions.size() > 0) {
                // Latest version is at index 0
                JsonNode latestVersion = versions.get(0);
                int latestVersionId = latestVersion.path("id").asInt();
                
                if (latestVersionId != currentVersionId) {
                    ModelUpdateInfo info = new ModelUpdateInfo();
                    info.localFileName = modelFile.getName();
                    info.modelName = modelName;
                    info.localVersionName = currentVersionName;
                    info.newVersionName = latestVersion.path("name").asText("New Version");
                    info.downloadUrl = latestVersion.path("downloadUrl").asText("");
                    info.updateDescription = latestVersion.path("description").asText("");
                    info.hasUpdate = true;
                    return info;
                }
            }
        } catch (Exception e) {
            System.err.println("Error checking update for model " + modelFile.getName() + ": " + e.getMessage());
        }
        return null;
    }
}
