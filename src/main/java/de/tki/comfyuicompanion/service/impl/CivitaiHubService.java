package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.domain.ModelFolder;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.ICivitaiHubService;
import de.tki.comfyuicompanion.service.IConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Enterprise service providing high-level search, pagination, and download preparation
 * for the Civitai Model Hub gallery.
 */
@Service
public class CivitaiHubService implements ICivitaiHubService {

    private static final Logger log = LoggerFactory.getLogger(CivitaiHubService.class);
    private static final String API_BASE = "https://civitai.com/api/v1/models";

    private final IConfigService configService;
    private final ModelListService modelListService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /**
     * Constructs a new CivitaiHubService with injected dependencies.
     *
     * @param configService    configuration service for tokens and storage paths
     * @param modelListService optional local model registry service
     * @param objectMapper     Jackson object mapper for JSON deserialization
     */
    @Autowired
    public CivitaiHubService(
            @Autowired(required = false) IConfigService configService,
            @Autowired(required = false) ModelListService modelListService,
            ObjectMapper objectMapper) {
        this.configService = configService;
        this.modelListService = modelListService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public CompletableFuture<List<CivitaiModelEntry>> queryModels(
            String query,
            String type,
            String baseModel,
            String sort,
            int limit,
            int page,
            boolean allowNsfw) {

        StringBuilder urlBuilder = new StringBuilder(API_BASE);
        urlBuilder.append("?limit=").append(Math.clamp(limit, 1, 100));
        urlBuilder.append("&page=").append(Math.max(1, page));

        if (query != null && !query.trim().isEmpty()) {
            urlBuilder.append("&query=").append(URLEncoder.encode(query.trim(), StandardCharsets.UTF_8));
        }

        if (type != null && !type.trim().isEmpty() && !type.equalsIgnoreCase("All") && !type.equalsIgnoreCase("All Types")) {
            urlBuilder.append("&types=").append(URLEncoder.encode(mapApiType(type.trim()), StandardCharsets.UTF_8));
        }

        if (baseModel != null && !baseModel.trim().isEmpty() && !baseModel.equalsIgnoreCase("All") && !baseModel.equalsIgnoreCase("All Bases")) {
            urlBuilder.append("&baseModels=").append(URLEncoder.encode(baseModel.trim(), StandardCharsets.UTF_8));
        }

        if (sort != null && !sort.trim().isEmpty()) {
            String apiSort = switch (sort.trim().toLowerCase()) {
                case "most downloaded", "downloads" -> "Most Downloaded";
                case "highest rated", "rating" -> "Highest Rated";
                case "newest", "latest" -> "Newest";
                default -> "Highest Rated";
            };
            urlBuilder.append("&sort=").append(URLEncoder.encode(apiSort, StandardCharsets.UTF_8));
        }

        if (!allowNsfw) {
            urlBuilder.append("&nsfw=false");
        }

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(urlBuilder.toString()))
                .header("Accept", "application/json")
                .header("User-Agent", "ComfyUI-Companion/1.1.1 (Desktop)")
                .timeout(Duration.ofSeconds(20))
                .GET();

        String apiKey = configService != null ? configService.getCivitaiApiKey() : null;
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            reqBuilder.header("Authorization", "Bearer " + apiKey.trim());
        }

        return httpClient.sendAsync(reqBuilder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    List<CivitaiModelEntry> entries = new ArrayList<>();
                    if (response.statusCode() != 200) {
                        log.warn("Civitai API responded with HTTP status {}", response.statusCode());
                        return entries;
                    }
                    try {
                        JsonNode root = objectMapper.readTree(response.body());
                        JsonNode items = root.path("items");
                        if (items.isArray()) {
                            for (JsonNode item : items) {
                                CivitaiModelEntry entry = parseModelEntry(item);
                                if (entry != null) {
                                    entries.add(entry);
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.error("Failed parsing Civitai API response: {}", e.getMessage(), e);
                    }
                    return entries;
                });
    }

    @Override
    public boolean isModelInstalled(String filename, String modelType) {
        if (filename == null || filename.isBlank()) {
            return false;
        }

        // Check via model list service if available
        if (modelListService != null && modelListService.findByFilename(filename).isPresent()) {
            return true;
        }

        if (configService == null) {
            return false;
        }

        String baseModelsPath = configService.getModelsPath();
        if (baseModelsPath == null || baseModelsPath.isBlank()) {
            return false;
        }

        ModelFolder folder = ModelFolder.fromString(modelType);
        File targetDir = new File(baseModelsPath, folder.getDefaultFolderName());
        File modelFile = new File(targetDir, filename);
        if (modelFile.exists()) {
            return true;
        }

        // Check common checkpoints and loras fallback
        File ckptFile = new File(new File(baseModelsPath, "checkpoints"), filename);
        if (ckptFile.exists()) return true;

        File loraFile = new File(new File(baseModelsPath, "loras"), filename);
        return loraFile.exists();
    }

    @Override
    public ModelInfo createModelInfoForDownload(CivitaiModelEntry entry) {
        ModelFolder folder = ModelFolder.fromString(entry.type());
        String folderName = folder.getDefaultFolderName();

        ModelInfo info = new ModelInfo(folderName, entry.name(), entry.downloadUrl());
        info.setFilename(entry.filename());
        info.setSize(entry.formattedSize());
        info.setByteSize(entry.fileSizeBytes());
        info.setBase(entry.baseModel());
        info.setDescription(entry.description());
        info.setPreviewPath(entry.thumbnailUrl());
        info.setPopularity(String.valueOf(entry.downloadCount()));

        return info;
    }

    private CivitaiModelEntry parseModelEntry(JsonNode item) {
        try {
            long id = item.path("id").asLong(0);
            String name = item.path("name").asText("Unnamed Model");
            String type = item.path("type").asText("Checkpoint");
            String creator = item.path("creator").path("username").asText("Civitai Creator");
            String description = item.path("description").asText("");
            boolean nsfw = item.path("nsfw").asBoolean(false);
            int downloadCount = item.path("stats").path("downloadCount").asInt(0);
            double rating = item.path("stats").path("rating").asDouble(0.0);

            JsonNode versions = item.path("modelVersions");
            if (!versions.isArray() || versions.isEmpty()) {
                return null;
            }

            JsonNode primaryVersion = versions.get(0);
            String baseModel = primaryVersion.path("baseModel").asText("Unknown");
            String downloadUrl = primaryVersion.path("downloadUrl").asText("");

            List<String> triggerWords = new ArrayList<>();
            JsonNode trainedWords = primaryVersion.path("trainedWords");
            if (trainedWords.isArray()) {
                for (JsonNode tw : trainedWords) {
                    if (tw.isTextual() && !tw.asText().isBlank()) {
                        triggerWords.add(tw.asText().trim());
                    }
                }
            }

            String thumbnailUrl = null;
            JsonNode images = primaryVersion.path("images");
            if (images.isArray() && !images.isEmpty()) {
                thumbnailUrl = images.get(0).path("url").asText(null);
            }

            String filename = name.replaceAll("[^a-zA-Z0-9_.-]", "_") + ".safetensors";
            long fileSizeBytes = 0;
            String formattedSize = "Unknown";

            JsonNode files = primaryVersion.path("files");
            if (files.isArray() && !files.isEmpty()) {
                JsonNode primaryFile = files.get(0);
                String actualName = primaryFile.path("name").asText("");
                if (!actualName.isBlank()) {
                    filename = actualName;
                }
                double sizeKb = primaryFile.path("sizeKB").asDouble(0.0);
                fileSizeBytes = (long) (sizeKb * 1024.0);
                if (sizeKb >= 1024.0 * 1024.0) {
                    formattedSize = String.format("%.2f GB", sizeKb / (1024.0 * 1024.0));
                } else if (sizeKb > 0) {
                    formattedSize = String.format("%.1f MB", sizeKb / 1024.0);
                }
            }

            return new CivitaiModelEntry(
                    id,
                    name,
                    type,
                    creator,
                    baseModel,
                    description,
                    thumbnailUrl,
                    downloadUrl,
                    filename,
                    formattedSize,
                    fileSizeBytes,
                    downloadCount,
                    rating,
                    triggerWords,
                    nsfw
            );
        } catch (Exception e) {
            log.debug("Skipping unparseable Civitai item: {}", e.getMessage());
            return null;
        }
    }

    private String mapApiType(String inputType) {
        return switch (inputType.toLowerCase()) {
            case "checkpoint", "checkpoints" -> "Checkpoint";
            case "lora", "loras", "locon" -> "LORA";
            case "vae" -> "VAE";
            case "controlnet" -> "Controlnet";
            case "upscaler" -> "Upscaler";
            case "textualinversion", "embeddings" -> "TextualInversion";
            default -> inputType;
        };
    }
}
