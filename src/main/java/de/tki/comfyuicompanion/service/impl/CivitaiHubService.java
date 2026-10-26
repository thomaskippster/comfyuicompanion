package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.domain.ModelFolder;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.ICivitaiHubService;
import de.tki.comfyuicompanion.service.ICivitaiMetadataService;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enterprise service providing high-level search, pagination, direct ID/hash lookup,
 * and download preparation for the Civitai Model Hub gallery.
 * Strictly enforces youth protection by filtering mature and NSFW content.
 */
@Service
public class CivitaiHubService implements ICivitaiHubService {

    private static final Logger log = LoggerFactory.getLogger(CivitaiHubService.class);
    private static final String API_BASE = "https://civitai.com/api/v1/models";

    private static final Pattern MODEL_URL_PATTERN = Pattern.compile("https?://civitai\\.com/models/(\\d+)");
    private static final Pattern NUMERIC_ID_PATTERN = Pattern.compile("^\\d{4,10}$");
    private static final Pattern HEX_HASH_PATTERN = Pattern.compile("^[0-9a-fA-F]{8,64}$");

    private final IConfigService configService;
    private final ModelListService modelListService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ICivitaiMetadataService metadataService;
    private final CivitaiModelJsonParser jsonParser;

    /**
     * Constructs a new CivitaiHubService with full dependency injection.
     *
     * @param configService    configuration service for tokens and storage paths
     * @param modelListService optional local model registry service
     * @param objectMapper     Jackson object mapper for JSON deserialization
     * @param metadataService  Civitai metadata service for dynamic enums
     * @param jsonParser       parser component for Civitai JSON entries
     */
    @Autowired
    public CivitaiHubService(
            @Autowired(required = false) IConfigService configService,
            @Autowired(required = false) ModelListService modelListService,
            ObjectMapper objectMapper,
            @Autowired(required = false) ICivitaiMetadataService metadataService,
            @Autowired(required = false) CivitaiModelJsonParser jsonParser) {
        this.configService = configService;
        this.modelListService = modelListService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.metadataService = metadataService != null
                ? metadataService
                : new CivitaiMetadataService(configService, this.objectMapper, this.httpClient);
        this.jsonParser = jsonParser != null ? jsonParser : new CivitaiModelJsonParser();
    }

    /**
     * Overloaded constructor for unit testing without Spring context.
     *
     * @param configService    configuration service
     * @param modelListService optional model registry
     * @param objectMapper     Jackson object mapper
     */
    public CivitaiHubService(
            IConfigService configService,
            ModelListService modelListService,
            ObjectMapper objectMapper) {
        this(configService, modelListService, objectMapper, null, null);
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

        String cleanQuery = query != null ? query.trim() : "";
        if (!cleanQuery.isEmpty()) {
            CompletableFuture<List<CivitaiModelEntry>> directFuture = tryDirectLookup(cleanQuery);
            if (directFuture != null) {
                return directFuture.thenCompose(directResults -> {
                    if (!directResults.isEmpty()) {
                        return CompletableFuture.completedFuture(directResults);
                    }
                    return executeCatalogQuery(cleanQuery, type, baseModel, sort, limit, page, allowNsfw);
                });
            }
        }

        return executeCatalogQuery(cleanQuery, type, baseModel, sort, limit, page, allowNsfw);
    }

    @Override
    public CompletableFuture<List<String>> getActiveBaseModels() {
        return metadataService.getActiveBaseModels();
    }

    @Override
    public CompletableFuture<List<String>> getModelTypes() {
        return metadataService.getModelTypes();
    }

    private CompletableFuture<List<CivitaiModelEntry>> tryDirectLookup(String cleanQuery) {
        Matcher urlMatcher = MODEL_URL_PATTERN.matcher(cleanQuery);
        if (urlMatcher.find()) {
            try {
                long modelId = Long.parseLong(urlMatcher.group(1));
                return fetchModelById(modelId);
            } catch (NumberFormatException ignored) {
                // Fall back to standard catalog search
            }
        }
        if (NUMERIC_ID_PATTERN.matcher(cleanQuery).matches()) {
            try {
                long modelId = Long.parseLong(cleanQuery);
                return fetchModelById(modelId);
            } catch (NumberFormatException ignored) {
                // Fall back to standard catalog search
            }
        }
        if (HEX_HASH_PATTERN.matcher(cleanQuery).matches()) {
            return fetchModelByHash(cleanQuery);
        }
        return null;
    }

    private CompletableFuture<List<CivitaiModelEntry>> fetchModelById(long modelId) {
        String url = API_BASE + "/" + modelId;
        HttpRequest request = buildAuthenticatedGetRequest(URI.create(url));
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonNode root = objectMapper.readTree(response.body());
                            CivitaiModelEntry entry = parseModelEntry(root);
                            if (entry != null) {
                                return List.of(entry);
                            }
                        } catch (Exception e) {
                            log.debug("Failed parsing direct Civitai model response: {}", e.getMessage());
                        }
                    }
                    return List.of();
                });
    }

    private CompletableFuture<List<CivitaiModelEntry>> fetchModelByHash(String hash) {
        String url = "https://civitai.com/api/v1/model-versions/by-hash/" + hash;
        HttpRequest request = buildAuthenticatedGetRequest(URI.create(url));
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenCompose(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonNode root = objectMapper.readTree(response.body());
                            long modelId = root.path("modelId").asLong(0);
                            if (modelId > 0) {
                                return fetchModelById(modelId);
                            }
                        } catch (Exception e) {
                            log.debug("Failed resolving version hash {}: {}", hash, e.getMessage());
                        }
                    }
                    return CompletableFuture.completedFuture(List.of());
                });
    }

    private CompletableFuture<List<CivitaiModelEntry>> executeCatalogQuery(
            String query,
            String type,
            String baseModel,
            String sort,
            int limit,
            int page,
            boolean allowNsfw) {

        URI uri = buildQueryUri(query, type, baseModel, sort, limit, page, allowNsfw);
        HttpRequest request = buildAuthenticatedGetRequest(uri);

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
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

    private HttpRequest buildAuthenticatedGetRequest(URI uri) {
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(uri)
                .header("Accept", "application/json")
                .header("User-Agent", "ComfyUI-Companion/1.1.1 (Desktop)")
                .timeout(Duration.ofSeconds(20))
                .GET();

        String apiKey = configService != null ? configService.getCivitaiApiKey() : null;
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            reqBuilder.header("Authorization", "Bearer " + apiKey.trim());
        }
        return reqBuilder.build();
    }

    /**
     * Constructs the target Civitai API URI with sanitized query parameters.
     * Civitai's API disallows combining the 'page' parameter with 'query' (which requires cursor-based pagination
     * and otherwise responds with HTTP 400). Hence, 'page' is only included when 'query' is blank.
     *
     * @param query     Civitai search query text
     * @param type      model category filter
     * @param baseModel target base model architecture
     * @param sort      sorting order
     * @param limit     maximum number of items to return
     * @param page      page number (used only when browsing without a query)
     * @param allowNsfw whether NSFW content is permitted
     * @return constructed URI
     */
    public URI buildQueryUri(
            String query,
            String type,
            String baseModel,
            String sort,
            int limit,
            int page,
            boolean allowNsfw) {

        StringBuilder urlBuilder = new StringBuilder(API_BASE);
        urlBuilder.append("?limit=").append(Math.clamp(limit, 1, 100));

        boolean hasQuery = query != null && !query.trim().isEmpty();
        if (hasQuery) {
            urlBuilder.append("&query=").append(URLEncoder.encode(query.trim(), StandardCharsets.UTF_8));
        } else {
            urlBuilder.append("&page=").append(Math.max(1, page));
        }

        if (type != null && !type.trim().isEmpty() && !type.equalsIgnoreCase("All") && !type.equalsIgnoreCase("All Types")) {
            urlBuilder.append("&types=").append(URLEncoder.encode(jsonParser.mapApiType(type.trim()), StandardCharsets.UTF_8));
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

        // Permanently enforce Safe-Search / nsfw=false across the entire application
        urlBuilder.append("&nsfw=false");

        return URI.create(urlBuilder.toString());
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
        if (entry.nsfw()) {
            throw new IllegalArgumentException("NSFW models are strictly prohibited and cannot be downloaded.");
        }

        ModelFolder folder = ModelFolder.fromString(entry.type());
        String folderName = folder.getDefaultFolderName();

        ModelInfo info = new ModelInfo(folderName, entry.name(), entry.downloadUrl());
        info.setSave_path(folderName);
        info.setFilename(entry.filename());
        info.setSize(entry.formattedSize());
        info.setByteSize(entry.fileSizeBytes());
        info.setBase(entry.baseModel());
        info.setDescription(entry.description());
        info.setPreviewPath(entry.thumbnailUrl());
        info.setPopularity(String.valueOf(entry.downloadCount()));

        return info;
    }

    /**
     * Parses a single Civitai model JSON node into a domain {@link CivitaiModelEntry}.
     * Delegates to {@link CivitaiModelJsonParser}.
     *
     * @param item the raw JSON node
     * @return the parsed model entry, or {@code null} if invalid or NSFW
     */
    public CivitaiModelEntry parseModelEntry(JsonNode item) {
        return jsonParser.parseModelEntry(item);
    }
}
