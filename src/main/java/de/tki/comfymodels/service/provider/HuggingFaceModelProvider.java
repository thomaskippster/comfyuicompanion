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
 * Concrete ModelProviderStrategy implementation for HuggingFace.
 */
@Component
public class HuggingFaceModelProvider implements ModelProviderStrategy {

    private static final Logger logger = LoggerFactory.getLogger(HuggingFaceModelProvider.class);
    private static final String API_BASE = "https://huggingface.co/api";
    private static final String RESOLVE_BASE = "https://huggingface.co";

    private final ConfigService configService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public HuggingFaceModelProvider(ConfigService configService, ObjectMapper objectMapper) {
        this.configService = configService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String getProviderName() {
        return "HUGGINGFACE";
    }

    @Override
    public boolean supports(String source) {
        return source != null && (source.equalsIgnoreCase("HUGGINGFACE") || source.toLowerCase(Locale.ROOT).contains("huggingface"));
    }

    @Override
    public CompletableFuture<List<ModelInfo>> searchModels(String query) {
        if (query == null || query.trim().isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        String url = API_BASE + "/models?limit=20&full=full&search=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        HttpRequest request = createRequestBuilder(url).GET().timeout(Duration.ofSeconds(20)).build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    List<ModelInfo> results = new ArrayList<>();
                    if (response.statusCode() == 200) {
                        try {
                            JsonNode items = objectMapper.readTree(response.body());
                            if (items.isArray()) {
                                for (JsonNode item : items) {
                                    results.add(mapItemToModelInfo(item));
                                }
                            }
                        } catch (Exception e) {
                            logger.error("Failed to parse HuggingFace search response: {}", e.getMessage());
                        }
                    }
                    return results;
                });
    }

    @Override
    public Optional<String> findDownloadUrlByHash(String hash) {
        // HuggingFace does not currently expose a global hash query index like Civitai
        return Optional.empty();
    }

    @Override
    public Optional<ModelInfo> checkForUpdate(ModelInfo current) {
        return Optional.empty();
    }

    private ModelInfo mapItemToModelInfo(JsonNode item) {
        String id = item.path("id").asText("unknown");
        String name = id.contains("/") ? id.substring(id.lastIndexOf("/") + 1) : id;

        ModelInfo info = new ModelInfo("checkpoints", name, getProviderName());
        info.setDescription("Repo: " + id);
        info.setUrl(RESOLVE_BASE + "/" + id + "/resolve/main/" + name + ".safetensors");
        return info;
    }

    private HttpRequest.Builder createRequestBuilder(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .header("User-Agent", "ComfyCompanion-Java/2.0");

        String token = configService != null ? configService.getHfToken() : null;
        if (token != null && !token.trim().isEmpty()) {
            builder.header("Authorization", "Bearer " + token.trim());
        }
        return builder;
    }
}
