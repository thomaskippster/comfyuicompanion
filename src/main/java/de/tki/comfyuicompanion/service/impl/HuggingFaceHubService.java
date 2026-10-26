package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfyuicompanion.domain.HuggingFaceFileEntry;
import de.tki.comfyuicompanion.domain.HuggingFaceModelEntry;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.IConfigService;
import de.tki.comfyuicompanion.service.IHuggingFaceHubService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Enterprise service implementation of {@link IHuggingFaceHubService}.
 * Communicates with the official Hugging Face Hub REST API, applies Bearer authentication
 * from the encrypted vault, enforces youth protection by filtering NSFW tags,
 * and coordinates intelligent target folder routing and local installation checks.
 */
@Service
public class HuggingFaceHubService implements IHuggingFaceHubService {

    private static final Logger log = LoggerFactory.getLogger(HuggingFaceHubService.class);
    private static final String API_BASE = "https://huggingface.co/api";

    private final IConfigService configService;
    private final ObjectMapper objectMapper;
    private final HuggingFaceFolderRouter folderRouter;
    private final CivitaiLocalModelScanner localModelScanner;
    private final HttpClient httpClient;

    /**
     * Constructs a new HuggingFaceHubService with required dependencies.
     *
     * @param configService      configuration service for tokens and paths
     * @param objectMapper       Jackson mapper for JSON serialization
     * @param folderRouter       heuristic router for target directories and VRAM hints
     * @param localModelScanner  scanner for local file checks
     */
    @Autowired
    public HuggingFaceHubService(
            @Autowired(required = false) IConfigService configService,
            ObjectMapper objectMapper,
            HuggingFaceFolderRouter folderRouter,
            @Autowired(required = false) CivitaiLocalModelScanner localModelScanner) {
        this.configService = configService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.folderRouter = folderRouter != null ? folderRouter : new HuggingFaceFolderRouter();
        this.localModelScanner = localModelScanner != null ? localModelScanner : new CivitaiLocalModelScanner(configService);
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public CompletableFuture<List<HuggingFaceModelEntry>> searchRepositories(
            String query,
            String formatFilter,
            String taskFilter,
            String sort,
            int limit) {

        StringBuilder urlBuilder = new StringBuilder(API_BASE)
                .append("/models?full=true&direction=-1")
                .append("&limit=").append(limit > 0 ? Math.min(limit, 100) : 30);

        if (query != null && !query.isBlank()) {
            urlBuilder.append("&search=").append(URLEncoder.encode(query.trim(), StandardCharsets.UTF_8));
        }

        // Build combined filter parameters
        List<String> filters = new ArrayList<>();
        if (formatFilter != null && !formatFilter.equalsIgnoreCase("all") && !formatFilter.equalsIgnoreCase("all formats") && !formatFilter.isBlank()) {
            if (formatFilter.equalsIgnoreCase("comfyui workflows") || formatFilter.equalsIgnoreCase("workflows")) {
                filters.add("comfyui");
            } else {
                filters.add(formatFilter.toLowerCase(Locale.ROOT));
            }
        }
        if (taskFilter != null && !taskFilter.equalsIgnoreCase("all") && !taskFilter.isBlank()) {
            filters.add(taskFilter.toLowerCase(Locale.ROOT));
        }
        if (!filters.isEmpty()) {
            urlBuilder.append("&filter=").append(URLEncoder.encode(String.join(",", filters), StandardCharsets.UTF_8));
        }

        // Sorting
        String sortParam = switch (sort != null ? sort.toLowerCase(Locale.ROOT) : "") {
            case "downloads", "most downloaded" -> "downloads";
            case "likes", "most likes" -> "likes";
            case "lastmodified", "newest", "recently updated" -> "lastModified";
            default -> "trendingScore";
        };
        urlBuilder.append("&sort=").append(sortParam);

        HttpRequest request = createAuthenticatedRequestBuilder(urlBuilder.toString())
                .GET()
                .timeout(Duration.ofSeconds(20))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        log.warn("Hugging Face API search returned status: {}", response.statusCode());
                        return Collections.<HuggingFaceModelEntry>emptyList();
                    }
                    return parseModelEntries(response.body());
                })
                .exceptionally(ex -> {
                    log.error("Failed querying Hugging Face repositories: {}", ex.getMessage(), ex);
                    return Collections.<HuggingFaceModelEntry>emptyList();
                });
    }

    @Override
    public CompletableFuture<List<HuggingFaceFileEntry>> getRepositoryFiles(String repoId) {
        if (repoId == null || repoId.isBlank()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        String url = API_BASE + "/models/" + repoId.trim() + "?blobs=true";
        HttpRequest request = createAuthenticatedRequestBuilder(url)
                .GET()
                .timeout(Duration.ofSeconds(20))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        log.warn("Hugging Face API repo blobs returned status {} for {}", response.statusCode(), repoId);
                        return Collections.<HuggingFaceFileEntry>emptyList();
                    }
                    return parseRepositoryFiles(repoId, response.body());
                })
                .exceptionally(ex -> {
                    log.error("Failed fetching files for Hugging Face repo {}: {}", repoId, ex.getMessage(), ex);
                    return Collections.<HuggingFaceFileEntry>emptyList();
                });
    }

    @Override
    public boolean isFileInstalled(String filename, String folder) {
        if (filename == null || filename.isBlank()) return false;
        ModelInfo probe = new ModelInfo(folder != null ? folder : "checkpoints", filename, "LOCAL");
        probe.setFilename(filename);
        probe.setSave_path(folder);
        return localModelScanner.isModelFilePresent(probe);
    }

    @Override
    public boolean isFileInArchive(String filename) {
        if (filename == null || filename.isBlank() || configService == null) return false;
        String archivePathStr = configService.getArchivePath();
        if (archivePathStr == null || archivePathStr.isBlank()) return false;

        Path archiveDir = Paths.get(archivePathStr);
        if (!Files.exists(archiveDir) || !Files.isDirectory(archiveDir)) return false;

        try (Stream<Path> walk = Files.walk(archiveDir, 4)) {
            return walk
                    .filter(Files::isRegularFile)
                    .anyMatch(p -> p.getFileName().toString().equalsIgnoreCase(filename));
        } catch (IOException e) {
            log.debug("Archive check inspection error: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public ModelInfo createModelInfoForDownload(HuggingFaceModelEntry repo, HuggingFaceFileEntry file, String targetFolder) {
        if (repo == null || file == null) {
            throw new IllegalArgumentException("Repo and file entry must not be null.");
        }
        String folder = (targetFolder != null && !targetFolder.isBlank()) ? targetFolder : file.suggestedFolder();
        ModelInfo info = new ModelInfo(folder, file.filename(), file.downloadUrl());
        info.setFilename(file.filename());
        info.setSave_path(folder);
        info.setSize(file.formattedSize());
        info.setByteSize(file.sizeBytes());
        info.setDescription("Hugging Face: " + repo.repoId() + " (" + file.vramHint() + ")");
        info.setPopularity(String.valueOf(repo.downloads()));
        info.setBase(repo.pipelineTag());
        return info;
    }

    private List<HuggingFaceModelEntry> parseModelEntries(String jsonBody) {
        List<HuggingFaceModelEntry> entries = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(jsonBody);
            if (root != null && root.isArray()) {
                for (JsonNode item : root) {
                    List<String> tags = new ArrayList<>();
                    boolean isNsfw = false;
                    if (item.has("tags") && item.get("tags").isArray()) {
                        for (JsonNode t : item.get("tags")) {
                            String tagText = t.asText("");
                            tags.add(tagText);
                            if ("nsfw".equalsIgnoreCase(tagText) || "erotica".equalsIgnoreCase(tagText) || "not-for-all-audiences".equalsIgnoreCase(tagText)) {
                                isNsfw = true;
                            }
                        }
                    }
                    if (isNsfw) {
                        continue; // Strictly enforce youth protection
                    }

                    String repoId = item.path("id").asText("");
                    if (repoId.isBlank()) continue;

                    String author = item.path("author").asText("");
                    String modelName = repoId.contains("/") ? repoId.substring(repoId.lastIndexOf('/') + 1) : repoId;
                    int downloads = item.path("downloads").asInt(0);
                    int likes = item.path("likes").asInt(0);
                    String lastModified = item.path("lastModified").asText("");
                    String pipelineTag = item.path("pipeline_tag").asText("");
                    String gated = item.has("gated") ? item.get("gated").asText() : null;

                    List<HuggingFaceFileEntry> siblingsList = new ArrayList<>();
                    String previewUrl = null;
                    if (item.has("siblings") && item.get("siblings").isArray()) {
                        for (JsonNode sib : item.get("siblings")) {
                            String rfn = sib.path("rfilename").asText("");
                            if (isModelFile(rfn)) {
                                String fn = rfn.contains("/") ? rfn.substring(rfn.lastIndexOf('/') + 1) : rfn;
                                siblingsList.add(new HuggingFaceFileEntry(rfn, fn, 0L, "Unknown",
                                        "https://huggingface.co/" + repoId + "/resolve/main/" + rfn + "?download=true",
                                        folderRouter.determineTargetFolder(rfn, tags, repoId),
                                        "Standard",
                                        false
                                ));
                            } else if (previewUrl == null && isImageFile(rfn)) {
                                previewUrl = "https://huggingface.co/" + repoId + "/resolve/main/" + rfn;
                            }
                        }
                    }

                    entries.add(new HuggingFaceModelEntry(repoId, author, modelName,
                            downloads,
                            likes,
                            lastModified,
                            pipelineTag,
                            tags,
                            gated,
                            siblingsList,
                            previewUrl
                    ));
                }
            }
        } catch (Exception ex) {
            log.error("Failed parsing Hugging Face model entries: {}", ex.getMessage(), ex);
        }
        return entries;
    }

    private List<HuggingFaceFileEntry> parseRepositoryFiles(String repoId, String jsonBody) {
        List<HuggingFaceFileEntry> files = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(jsonBody);
            List<String> tags = new ArrayList<>();
            if (root.has("tags") && root.get("tags").isArray()) {
                for (JsonNode t : root.get("tags")) {
                    tags.add(t.asText());
                }
            }

            if (root.has("siblings") && root.get("siblings").isArray()) {
                for (JsonNode sib : root.get("siblings")) {
                    String rfilename = sib.path("rfilename").asText("");
                    if (!isModelFile(rfilename)) {
                        continue;
                    }

                    long size = sib.path("size").asLong(0L);
                    boolean isLfs = false;
                    if (sib.has("lfs") && sib.get("lfs").has("size")) {
                        size = sib.get("lfs").path("size").asLong(size);
                        isLfs = true;
                    }

                    String fn = rfilename.contains("/") ? rfilename.substring(rfilename.lastIndexOf('/') + 1) : rfilename;
                    String suggestedFolder = folderRouter.determineTargetFolder(rfilename, tags, repoId);
                    String vramHint = folderRouter.determineVramHint(fn, size);
                    String downloadUrl = "https://huggingface.co/" + repoId + "/resolve/main/" + rfilename + "?download=true";

                    files.add(new HuggingFaceFileEntry(
                            rfilename,
                            fn,
                            size,
                            null,
                            downloadUrl,
                            suggestedFolder,
                            vramHint,
                            isLfs
                    ));
                }
            }
        } catch (Exception ex) {
            log.error("Failed parsing repository files for {}: {}", repoId, ex.getMessage(), ex);
        }
        return files;
    }

    private boolean isModelFile(String rfilename) {
        if (rfilename == null || rfilename.isBlank()) return false;
        String lower = rfilename.toLowerCase(Locale.ROOT);
        if (lower.startsWith(".") || lower.endsWith(".md") || lower.endsWith(".txt")
                || lower.endsWith(".json") || lower.endsWith(".gitattributes")
                || lower.endsWith(".py") || lower.endsWith(".png") || lower.endsWith(".jpg")) {
            return false;
        }
        return lower.endsWith(".safetensors") || lower.endsWith(".gguf")
                || lower.endsWith(".ckpt") || lower.endsWith(".pt")
                || lower.endsWith(".bin") || lower.endsWith(".pth");
    }

    private boolean isImageFile(String rfilename) {
        if (rfilename == null || rfilename.isBlank()) return false;
        String lower = rfilename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".webp") || lower.endsWith(".gif");
    }

    private HttpRequest.Builder createAuthenticatedRequestBuilder(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .header("User-Agent", "ComfyCompanion-Java/2.0");

        String hfToken = configService != null ? configService.getHfToken() : null;
        if (hfToken != null && !hfToken.isBlank()) {
            builder.header("Authorization", "Bearer " + hfToken.trim());
        }
        return builder;
    }
}


