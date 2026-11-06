package de.tki.comfyuicompanion.service.workflow.impl;

import de.tki.comfyuicompanion.domain.HuggingFaceFileEntry;
import de.tki.comfyuicompanion.domain.HuggingFaceModelEntry;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.domain.WorkflowDescriptor;
import de.tki.comfyuicompanion.domain.WorkflowSourceType;
import de.tki.comfyuicompanion.service.IHuggingFaceHubService;
import de.tki.comfyuicompanion.service.impl.HuggingFaceWorkflowAnalyzer;
import de.tki.comfyuicompanion.service.workflow.IWorkflowSourceStrategy;
import de.tki.comfyuicompanion.service.workflow.WorkflowModelExtractor;
import de.tki.comfyuicompanion.util.WorkflowImageLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.awt.Window;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Strategy implementation for Hugging Face Hub workflows and repositories.
 *
 * @author Senior Java Developer
 */
@Component
public class HuggingFaceWorkflowStrategy implements IWorkflowSourceStrategy {

    private static final Logger log = LoggerFactory.getLogger(HuggingFaceWorkflowStrategy.class);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final IHuggingFaceHubService huggingFaceHubService;
    private final de.tki.comfyuicompanion.service.impl.ConfigService configService;

    public HuggingFaceWorkflowStrategy() {
        this(null, null);
    }

    public HuggingFaceWorkflowStrategy(IHuggingFaceHubService huggingFaceHubService) {
        this(huggingFaceHubService, null);
    }

    @Autowired
    public HuggingFaceWorkflowStrategy(
            @Autowired(required = false) IHuggingFaceHubService huggingFaceHubService,
            @Autowired(required = false) de.tki.comfyuicompanion.service.impl.ConfigService configService) {
        this.huggingFaceHubService = huggingFaceHubService;
        this.configService = configService;
    }

    @Override
    public WorkflowSourceType getSourceType() {
        return WorkflowSourceType.HUGGING_FACE;
    }

    @Override
    public CompletableFuture<String> resolveWorkflowJson(WorkflowDescriptor descriptor) {
        return CompletableFuture.supplyAsync(() -> {
            String url = descriptor.workflowFilePathOrUrl();
            if (url == null || url.isBlank()) {
                throw new IllegalStateException("HuggingFace workflow URL is not defined for " + descriptor.title());
            }

            try {
                if (!url.toLowerCase(Locale.ROOT).startsWith("http://") && !url.toLowerCase(Locale.ROOT).startsWith("https://")) {
                    java.nio.file.Path localPath = java.nio.file.Path.of(url);
                    if (java.nio.file.Files.exists(localPath)) {
                        return java.nio.file.Files.readString(localPath);
                    }
                }

                String cleanUrl = url.replace(" ", "%20");
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(cleanUrl))
                        .header("User-Agent", "ComfyUICompanion/2.0")
                        .timeout(Duration.ofSeconds(20))
                        .GET();

                String token = resolveHfToken();
                if (token != null && !token.isBlank()) {
                    reqBuilder.header("Authorization", "Bearer " + token.trim());
                }

                HttpResponse<String> resp = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    return resp.body();
                }

                // Check local fallback workflow if Hugging Face repository is gated (401/403) or missing (404)
                java.util.Optional<String> localFallback = findLocalFallbackWorkflow(descriptor.title(), url);
                if (localFallback.isPresent()) {
                    log.info("Hugging Face returned HTTP {}. Successfully resolved local fallback workflow for '{}'", resp.statusCode(), descriptor.title());
                    return localFallback.get();
                }

                if (resp.statusCode() == 401 || resp.statusCode() == 403) {
                    throw new IllegalStateException("Hugging Face repository is gated/restricted (HTTP " + resp.statusCode()
                            + "). Please add your Hugging Face Access Token in Settings -> API Keys (or set the HF_TOKEN environment variable) and accept the license on HuggingFace.co.");
                }

                throw new IllegalStateException("HTTP " + resp.statusCode() + " fetching HuggingFace workflow JSON from " + url);
            } catch (Exception e) {
                log.error("Failed to resolve Hugging Face workflow JSON for {}: {}", descriptor.title(), e.getMessage());
                throw new RuntimeException(e.getMessage(), e);
            }
        });
    }

    private String resolveHfToken() {
        if (configService != null && configService.getHfToken() != null && !configService.getHfToken().isBlank()) {
            return configService.getHfToken().trim();
        }
        String envToken = System.getenv("HF_TOKEN");
        if (envToken != null && !envToken.isBlank()) return envToken.trim();
        String hubToken = System.getenv("HUGGING_FACE_HUB_TOKEN");
        if (hubToken != null && !hubToken.isBlank()) return hubToken.trim();
        return null;
    }

    private java.util.Optional<String> findLocalFallbackWorkflow(String title, String url) {
        List<java.nio.file.Path> scanDirs = List.of(
                java.nio.file.Path.of("user_workflows"),
                java.nio.file.Path.of("workflows"),
                java.nio.file.Path.of("templates")
        );
        String cleanTitle = title != null ? title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "") : "";
        String urlFile = url != null && url.contains("/") ? url.substring(url.lastIndexOf('/') + 1) : "";
        int query = urlFile.indexOf('?');
        if (query > 0) urlFile = urlFile.substring(0, query);
        String cleanUrlFile = urlFile.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");

        for (java.nio.file.Path dir : scanDirs) {
            if (!java.nio.file.Files.isDirectory(dir)) continue;
            try (var stream = java.nio.file.Files.list(dir)) {
                for (java.nio.file.Path file : stream.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".json")).toList()) {
                    String fn = file.getFileName().toString().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                    if ((!cleanUrlFile.isEmpty() && fn.contains(cleanUrlFile))
                            || (!cleanTitle.isEmpty() && (fn.contains(cleanTitle) || (cleanTitle.length() > 6 && cleanTitle.contains(fn))))) {
                        return java.util.Optional.of(java.nio.file.Files.readString(file));
                    }
                    if (cleanTitle.contains("flux") && cleanTitle.contains("3") && cleanTitle.contains("klein")
                            && fn.contains("flux") && fn.contains("3") && fn.contains("klein")) {
                        return java.util.Optional.of(java.nio.file.Files.readString(file));
                    }
                }
            } catch (Exception ignored) {}
        }
        return java.util.Optional.empty();
    }

    @Override
    public CompletableFuture<byte[]> fetchPreviewImageBytes(WorkflowDescriptor descriptor) {
        return CompletableFuture.supplyAsync(() ->
                WorkflowImageLoader.loadImageBytes(descriptor.previewPathOrUrl())
        );
    }

    @Override
    public boolean canDownloadMissingModels() {
        // Business Rule: HuggingFace tab only browses; downloads are restricted to My Vault
        return false;
    }

    @Override
    public void downloadMissingModels(WorkflowDescriptor descriptor, List<ModelInfo> missingModels, Window parent) {
        log.warn("Download missing models is disabled in Hugging Face tab. Save to Vault first.");
    }

    @Override
    public WorkflowDescriptor toWorkflowDescriptor(Object sourceEntry) {
        if (sourceEntry instanceof HuggingFaceModelEntry repo) {
            String sourceUrl = "https://huggingface.co/" + repo.repoId();
            Map<String, String> attrs = Map.of(
                    "repoId", repo.repoId(),
                    "downloads", String.valueOf(repo.downloads()),
                    "likes", String.valueOf(repo.likes()),
                    "pipelineTag", repo.pipelineTag() != null ? repo.pipelineTag() : "generic"
            );

            List<HuggingFaceFileEntry> repoFiles = repo.files() != null ? repo.files() : Collections.emptyList();
            if (huggingFaceHubService != null && repoFiles.stream().anyMatch(f -> f.sizeBytes() <= 0)) {
                try {
                    List<HuggingFaceFileEntry> detailed = huggingFaceHubService.getRepositoryFiles(repo.repoId())
                            .get(5, java.util.concurrent.TimeUnit.SECONDS);
                    if (detailed != null && !detailed.isEmpty()) {
                        repoFiles = detailed;
                    }
                } catch (Exception ex) {
                    log.debug("Failed enriching Hugging Face repository file sizes for {}: {}", repo.repoId(), ex.getMessage());
                }
            }

            // Find default workflow file if present (preferring UI workflow over .api.json)
            String workflowUrl = null;
            for (HuggingFaceFileEntry f : repoFiles) {
                if (f.rfilename() != null && f.rfilename().toLowerCase(Locale.ROOT).endsWith(".json")
                        && !f.rfilename().toLowerCase(Locale.ROOT).endsWith(".api.json")) {
                    workflowUrl = f.downloadUrl() != null && !f.downloadUrl().isBlank()
                            ? f.downloadUrl()
                            : "https://huggingface.co/" + repo.repoId() + "/resolve/main/" + f.rfilename().replace(" ", "%20");
                    break;
                }
            }
            if (workflowUrl == null) {
                for (HuggingFaceFileEntry f : repoFiles) {
                    if (f.rfilename() != null && f.rfilename().toLowerCase(Locale.ROOT).endsWith(".json")) {
                        workflowUrl = f.downloadUrl() != null && !f.downloadUrl().isBlank()
                                ? f.downloadUrl()
                                : "https://huggingface.co/" + repo.repoId() + "/resolve/main/" + f.rfilename().replace(" ", "%20");
                        break;
                    }
                }
            }

            List<HuggingFaceFileEntry> operationalFiles = HuggingFaceWorkflowAnalyzer.selectOperationalFilesForSingleRun(
                    repoFiles,
                    null,
                    f -> (huggingFaceHubService != null && huggingFaceHubService.isFileInstalled(f.filename(), f.suggestedFolder()))
                            ? "Installed"
                            : "Missing"
            );

            List<ModelInfo> reqModels = new ArrayList<>();
            int missingCount = 0;
            for (HuggingFaceFileEntry f : operationalFiles) {
                ModelInfo mi = new ModelInfo();
                mi.setName(f.filename());
                mi.setFilename(f.filename());
                mi.setType(f.suggestedFolder());
                mi.setSave_path(f.suggestedFolder());
                mi.setByteSize(f.sizeBytes());
                String sizeStr = f.formattedSize();
                if (sizeStr == null || sizeStr.isBlank() || "Unknown".equalsIgnoreCase(sizeStr) || "Workflow JSON".equalsIgnoreCase(sizeStr)) {
                    String est = WorkflowModelExtractor.estimateKnownModelSize(f.filename());
                    sizeStr = est != null ? est : "Unknown";
                }
                mi.setSize(sizeStr);
                mi.setUrl(f.downloadUrl());
                boolean installed = huggingFaceHubService != null
                        && huggingFaceHubService.isFileInstalled(f.filename(), f.suggestedFolder());
                mi.setPopularity(installed ? "installed" : "missing");
                mi.setDescription(installed ? "Already exists" : "Missing");
                if (!installed) {
                    missingCount++;
                }
                reqModels.add(mi);
            }

            return WorkflowDescriptor.builder()
                    .id("hf-" + repo.repoId().replace('/', '_'))
                    .title(repo.modelName() != null ? repo.modelName() : repo.repoId())
                    .category(repo.pipelineTag() != null ? repo.pipelineTag() : "Model Repository")
                    .architecture("HuggingFace Hub")
                    .author(repo.author() != null ? repo.author() : "HuggingFace")
                    .description("Hugging Face Repository: " + repo.repoId())
                    .sourceType(WorkflowSourceType.HUGGING_FACE)
                    .sourceUrl(sourceUrl)
                    .previewPathOrUrl(repo.thumbnailUrl())
                    .workflowFilePathOrUrl(workflowUrl)
                    .requiredModels(reqModels)
                    .missingModelsCount(missingCount)
                    .rawSourceEntry(repo)
                    .extendedAttributes(attrs)
                    .build();
        }

        throw new IllegalArgumentException("Unsupported source entry type for HuggingFaceWorkflowStrategy: "
                + (sourceEntry != null ? sourceEntry.getClass().getName() : "null"));
    }
}
