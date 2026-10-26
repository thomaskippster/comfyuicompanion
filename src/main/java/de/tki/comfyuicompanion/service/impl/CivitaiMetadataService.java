package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfyuicompanion.service.ICivitaiMetadataService;
import de.tki.comfyuicompanion.service.IConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Enterprise service responsible for fetching and caching dynamic enums and metadata
 * from the Civitai API (e.g. active base model architectures and model types).
 * Replaces hardcoded model lists with generic, dynamically resolved metadata.
 */
@Service
public class CivitaiMetadataService implements ICivitaiMetadataService {

    private static final Logger log = LoggerFactory.getLogger(CivitaiMetadataService.class);
    private static final String ENUMS_ENDPOINT = "https://civitai.com/api/v1/enums";

    private static final List<String> FALLBACK_BASE_MODELS = List.of(
            "Flux.1 D", "Flux.1 S", "Flux.3 Klein", "SDXL 1.0", "SD 1.5", "SD 3.5", "Wan 2.1", "Pony"
    );

    private static final List<String> FALLBACK_MODEL_TYPES = List.of(
            "Workflows", "Checkpoint", "LORA", "VAE", "ControlNet", "Upscaler", "TextualInversion"
    );

    private final IConfigService configService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private final AtomicReference<List<String>> cachedActiveBaseModels = new AtomicReference<>(null);
    private final AtomicReference<List<String>> cachedModelTypes = new AtomicReference<>(null);

    /**
     * Constructs a new CivitaiMetadataService with injected dependencies.
     *
     * @param configService optional configuration service for API tokens
     * @param objectMapper   Jackson object mapper for JSON parsing
     */
    @Autowired
    public CivitaiMetadataService(
            @Autowired(required = false) IConfigService configService,
            ObjectMapper objectMapper) {
        this(configService, objectMapper, null);
    }

    /**
     * Internal constructor allowing injection of a custom HttpClient for unit testing.
     *
     * @param configService optional configuration service
     * @param objectMapper  object mapper
     * @param httpClient    http client instance or null for default
     */
    public CivitaiMetadataService(
            IConfigService configService,
            ObjectMapper objectMapper,
            HttpClient httpClient) {
        this.configService = configService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.httpClient = httpClient != null ? httpClient : HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public CompletableFuture<List<String>> getActiveBaseModels() {
        List<String> cached = cachedActiveBaseModels.get();
        if (cached != null && !cached.isEmpty()) {
            return CompletableFuture.completedFuture(cached);
        }
        return fetchMetadata().thenApply(v -> {
            List<String> res = cachedActiveBaseModels.get();
            return (res != null && !res.isEmpty()) ? res : FALLBACK_BASE_MODELS;
        });
    }

    @Override
    public CompletableFuture<List<String>> getModelTypes() {
        List<String> cached = cachedModelTypes.get();
        if (cached != null && !cached.isEmpty()) {
            return CompletableFuture.completedFuture(cached);
        }
        return fetchMetadata().thenApply(v -> {
            List<String> res = cachedModelTypes.get();
            return (res != null && !res.isEmpty()) ? res : FALLBACK_MODEL_TYPES;
        });
    }

    @Override
    public CompletableFuture<Void> refreshMetadata() {
        return fetchMetadata();
    }

    private CompletableFuture<Void> fetchMetadata() {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(ENUMS_ENDPOINT))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "ComfyUI-Companion/2.0")
                .header("Accept", "application/json")
                .GET();

        if (configService != null) {
            String token = configService.getCivitaiApiKey();
            if (token != null && !token.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + token.trim());
            }
        }

        return httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        parseEnumsResponse(response.body());
                    } else {
                        log.warn("Civitai enums endpoint returned status {}. Using fallback metadata.", response.statusCode());
                        populateFallbacksIfEmpty();
                    }
                })
                .exceptionally(ex -> {
                    log.debug("Could not fetch metadata from Civitai enums endpoint: {}. Using fallback metadata.", ex.getMessage());
                    populateFallbacksIfEmpty();
                    return null;
                });
    }

    private void parseEnumsResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            // 1. Parse ActiveBaseModel list
            List<String> baseModels = extractStringArray(root, "ActiveBaseModel");
            if (baseModels.isEmpty()) {
                baseModels = extractStringArray(root, "BaseModel");
            }
            if (!baseModels.isEmpty()) {
                cachedActiveBaseModels.set(Collections.unmodifiableList(baseModels));
                log.info("Successfully discovered {} dynamic base models from Civitai metadata.", baseModels.size());
            } else {
                populateFallbacksIfEmpty();
            }

            // 2. Parse ModelType list
            List<String> modelTypes = extractStringArray(root, "ModelType");
            if (!modelTypes.isEmpty()) {
                cachedModelTypes.set(Collections.unmodifiableList(modelTypes));
                log.debug("Discovered {} dynamic model types from Civitai metadata.", modelTypes.size());
            }
        } catch (Exception e) {
            log.warn("Failed to parse Civitai enums response: {}. Falling back to default metadata.", e.getMessage());
            populateFallbacksIfEmpty();
        }
    }

    private List<String> extractStringArray(JsonNode root, String fieldName) {
        JsonNode arrayNode = root.path(fieldName);
        if (!arrayNode.isArray()) {
            return List.of();
        }
        List<String> list = new ArrayList<>();
        for (JsonNode elem : arrayNode) {
            if (elem.isTextual()) {
                String val = elem.asText().trim();
                if (!val.isBlank() && !list.contains(val)) {
                    list.add(val);
                }
            }
        }
        return list;
    }

    private void populateFallbacksIfEmpty() {
        cachedActiveBaseModels.compareAndSet(null, FALLBACK_BASE_MODELS);
        cachedModelTypes.compareAndSet(null, FALLBACK_MODEL_TYPES);
    }
}
