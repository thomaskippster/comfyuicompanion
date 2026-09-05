package de.tki.comfymodels.service.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.impl.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Concrete ModelProviderStrategy implementation for Civitai.
 */
@Component
public class CivitaiModelProvider implements ModelProviderStrategy {

    private static final Logger logger = LoggerFactory.getLogger(CivitaiModelProvider.class);
    private static final String BASE_URL = "https://civitai.com/api/v1";

    private final ConfigService configService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public CivitaiModelProvider(ConfigService configService, ObjectMapper objectMapper) {
        this.configService = configService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String getProviderName() {
        return "CIVITAI";
    }

    @Override
    public boolean supports(String source) {
        return source != null && (source.equalsIgnoreCase("CIVITAI") || source.toLowerCase(Locale.ROOT).contains("civitai"));
    }

    @Override
    public CompletableFuture<List<ModelInfo>> searchModels(String query) {
        if (query == null || query.trim().isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        String url = BASE_URL + "/models?limit=20&query=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        HttpRequest request = createRequestBuilder(url).GET().timeout(Duration.ofSeconds(20)).build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    List<ModelInfo> results = new ArrayList<>();
                    if (response.statusCode() == 200) {
                        try {
                            JsonNode root = objectMapper.readTree(response.body());
                            JsonNode items = root.get("items");
                            if (items != null && items.isArray()) {
                                for (JsonNode item : items) {
                                    results.add(mapItemToModelInfo(item));
                                }
                            }
                        } catch (Exception e) {
                            logger.error("Failed to parse Civitai search response: {}", e.getMessage());
                        }
                    }
                    return results;
                });
    }

    @Override
    public Optional<String> findDownloadUrlByHash(String hash) {
        if (hash == null || hash.trim().isEmpty()) return Optional.empty();

        try {
            String url = BASE_URL + "/model-versions/by-hash/" + hash;
            HttpRequest request = createRequestBuilder(url).GET().timeout(Duration.ofSeconds(15)).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode files = root.get("files");
                if (files != null && files.isArray()) {
                    for (JsonNode file : files) {
                        if (file.has("downloadUrl")) {
                            return Optional.of(file.get("downloadUrl").asText());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Civitai hash lookup failed for hash {}: {}", hash, e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public Optional<ModelInfo> checkForUpdate(ModelInfo current) {
        // Implemented via model-version API
        return Optional.empty();
    }

    private ModelInfo mapItemToModelInfo(JsonNode item) {
        String name = item.path("name").asText("unknown");
        String type = item.path("type").asText("Checkpoint");
        ModelInfo info = new ModelInfo("checkpoints", name, getProviderName());
        info.setDescription(item.path("description").asText(""));

        JsonNode versions = item.path("modelVersions");
        if (versions.isArray() && versions.size() > 0) {
            JsonNode firstVersion = versions.get(0);
            JsonNode files = firstVersion.path("files");
            if (files.isArray() && files.size() > 0) {
                JsonNode firstFile = files.get(0);
                info.setUrl(firstFile.path("downloadUrl").asText(""));
                long sizeKb = firstFile.path("sizeKB").asLong(0);
                info.setByteSize(sizeKb * 1024);
            }
        }
        return info;
    }

    private HttpRequest.Builder createRequestBuilder(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .header("User-Agent", "ComfyCompanion-Java/2.0");

        String apiKey = configService != null ? configService.getCivitaiApiKey() : null;
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            builder.header("Authorization", "Bearer " + apiKey.trim());
        }
        return builder;
    }
}
