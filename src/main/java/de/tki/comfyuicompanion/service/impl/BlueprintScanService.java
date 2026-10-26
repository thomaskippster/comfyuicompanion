package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfyuicompanion.domain.ModelArchitecture;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import de.tki.comfyuicompanion.service.IModelArchitectureService.ModelDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Service managing scanning of ComfyUI blueprints, downloading remote previews,
 * and discovering server blueprints.
 */
@Service
public class BlueprintScanService {
    private static final Logger logger = LoggerFactory.getLogger(BlueprintScanService.class);
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Listener notification callback interface.
     */
    @FunctionalInterface
    public interface ProgressNotifier {
        /**
         * Dispatches progress updates to listeners.
         *
         * @param percent percent completed
         * @param currentFileName currently processed filename
         * @param completed true if scan completed
         */
        void notify(int percent, String currentFileName, boolean completed);
    }

    /**
     * Resolves and migrates legacy blueprints directories to companion_blueprints.
     *
     * @param comfyPath comfyui root directory
     * @return resolved blueprints directory or fallback
     */
    public File resolveBlueprintsDirectory(String comfyPath) {
        if (comfyPath != null && !comfyPath.trim().isEmpty()) {
            migrateBlueprintsFolder(new File(comfyPath, "blueprints"), new File(comfyPath, "companion_blueprints"));
            migrateBlueprintsFolder(new File(comfyPath, "resources/ComfyUI/blueprints"), new File(comfyPath, "resources/ComfyUI/companion_blueprints"));

            File directBlueprints = new File(comfyPath, "companion_blueprints");
            if (directBlueprints.exists() && directBlueprints.isDirectory()) {
                return directBlueprints;
            }
            File resourcesBlueprints = new File(comfyPath, "resources/ComfyUI/companion_blueprints");
            if (resourcesBlueprints.exists() && resourcesBlueprints.isDirectory()) {
                return resourcesBlueprints;
            }
            return directBlueprints;
        }
        return new File(System.getProperty("user.dir"), "companion_blueprints");
    }

    /**
     * Executes the blueprints analysis workflow.
     *
     * @param blueprintsDir blueprints directory
     * @param comfyUrl comfyui server url
     * @param lifecycleService comfy lifecycle service
     * @param modelAnalyzer analyzer for models
     * @param defaultsExtractor extractor for workflow defaults
     * @param serverDiscoveryService discovery service for server blueprints
     * @param architectureDetector detector function for model architecture
     * @param standardDefaultsProvider standard defaults function
     * @param resolvedDefaultsMap destination map for resolved model defaults
     * @param scanResults destination list for scanned blueprints
     * @param progressNotifier callback for progress notifications
     * @return true if scan was performed, false if directory/blueprints missing
     */
    public boolean executeBlueprintScan(
            File blueprintsDir,
            String comfyUrl,
            IComfyLifecycleService lifecycleService,
            ComfyModelAnalyzer modelAnalyzer,
            WorkflowDefaultsExtractor defaultsExtractor,
            ServerBlueprintDiscoveryService serverDiscoveryService,
            Function<String, ModelArchitecture> architectureDetector,
            Function<ModelArchitecture, ModelDefaults> standardDefaultsProvider,
            Map<String, ModelDefaults> resolvedDefaultsMap,
            List<Map<String, Object>> scanResults,
            ProgressNotifier progressNotifier) {

        if (!blueprintsDir.exists() || !blueprintsDir.isDirectory()) {
            logger.error("⚠️ [BlueprintScanService] Blueprints directory not found: {}", blueprintsDir.getAbsolutePath());
            progressNotifier.notify(100, "Done (No blueprints directory)", true);
            return false;
        }

        File[] files = blueprintsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
        if (files == null || files.length == 0) {
            progressNotifier.notify(100, "Done (No blueprints found)", true);
            return false;
        }

        boolean serverOnline = isComfyServerOnline(comfyUrl, lifecycleService);
        Map<String, String> mediaSubtypes = new HashMap<>();
        HttpClient sharedDownloadClient = null;

        if (serverOnline && comfyUrl != null && !comfyUrl.isEmpty()) {
            try {
                sharedDownloadClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(comfyUrl + "/templates/index.json")).timeout(Duration.ofSeconds(3)).GET().build();
                HttpResponse<String> response = sharedDownloadClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonNode indexRoot = mapper.readTree(response.body());
                    serverDiscoveryService.parseSubtypesFromIndex(indexRoot, mediaSubtypes);
                }
            } catch (Exception e) {
                logger.warn("⚠️ [BlueprintScanService] Failed to fetch server templates index for previews: {}", e.getMessage());
                serverOnline = false;
            }
        }

        final boolean isServerActive = serverOnline;
        final HttpClient downloadClient = sharedDownloadClient;

        progressNotifier.notify(0, "Starting...", false);
        List<Map<String, Object>> scanList = new CopyOnWriteArrayList<>();
        AtomicInteger processedCount = new AtomicInteger(0);

        Arrays.stream(files).parallel().forEach(file -> {
            String currentFileName = file.getName();
            try {
                processSingleBlueprintFile(file, isServerActive, downloadClient, comfyUrl, mediaSubtypes,
                        modelAnalyzer, defaultsExtractor, architectureDetector, standardDefaultsProvider,
                        resolvedDefaultsMap, scanList);
            } catch (Exception e) {
                logger.error("⚠️ [BlueprintScanService] Failed to parse blueprint {}: {}", file.getName(), e.getMessage());
            } finally {
                int done = processedCount.incrementAndGet();
                int percent = (int) (((double) done / files.length) * 100);
                progressNotifier.notify(percent, currentFileName, false);
            }
        });

        if (isServerActive) {
            serverDiscoveryService.discoverServerBlueprints(scanList, mediaSubtypes, comfyUrl, mapper,
                    modelAnalyzer, defaultsExtractor,
                    (rawJson, jsonFilename) -> defaultsExtractor.extractDefaultsFromWorkflow(
                            rawJson, jsonFilename, mapper, architectureDetector::apply,
                            standardDefaultsProvider::apply, resolvedDefaultsMap));
        }

        scanResults.clear();
        scanResults.addAll(scanList);
        progressNotifier.notify(100, "Completed", true);
        return true;
    }

    private void processSingleBlueprintFile(
            File file,
            boolean isServerActive,
            HttpClient downloadClient,
            String comfyUrl,
            Map<String, String> mediaSubtypes,
            ComfyModelAnalyzer modelAnalyzer,
            WorkflowDefaultsExtractor defaultsExtractor,
            Function<String, ModelArchitecture> architectureDetector,
            Function<ModelArchitecture, ModelDefaults> standardDefaultsProvider,
            Map<String, ModelDefaults> resolvedDefaultsMap,
            List<Map<String, Object>> scanList) throws Exception {

        String baseName = file.getName();
        if (baseName.toLowerCase().endsWith(".json")) {
            baseName = baseName.substring(0, baseName.length() - 5);
        }

        PreviewMetadata preview = resolveOrDownloadPreview(file, baseName, isServerActive, downloadClient, comfyUrl, mediaSubtypes);

        String rawJson = Files.readString(file.toPath());
        JsonNode root = mapper.readTree(rawJson);

        String category = "";
        String description = "";
        JsonNode extra = root.path("extra");
        if (!extra.isMissingNode()) {
            JsonNode groups = extra.path("groups");
            if (groups.isArray() && groups.size() > 0) {
                JsonNode group0 = groups.get(0);
                category = group0.path("category").asText("");
                description = group0.path("description").asText("");
            }
        }

        List<ModelInfo> requiredModels;
        try {
            requiredModels = modelAnalyzer.analyze(rawJson, file.getName());
        } catch (Exception e) {
            requiredModels = Collections.emptyList();
        }

        Map<String, Object> blueprintMap = new HashMap<>();
        blueprintMap.put("name", baseName);
        blueprintMap.put("filename", file.getName());
        blueprintMap.put("filePath", "companion_blueprints/" + file.getName());
        blueprintMap.put("category", category);
        blueprintMap.put("description", description);
        blueprintMap.put("mediaType", preview.mediaType());
        blueprintMap.put("mediaSubtype", preview.mediaSubtype());
        blueprintMap.put("previewPath", preview.previewPath() != null && preview.previewPath().startsWith("http") ? preview.previewPath() : "");
        blueprintMap.put("requiredModels", requiredModels);
        scanList.add(blueprintMap);

        defaultsExtractor.extractDefaultsFromWorkflow(rawJson, file.getName(), mapper,
                architectureDetector::apply, standardDefaultsProvider::apply, resolvedDefaultsMap);
    }

    private record PreviewMetadata(String previewPath, String mediaType, String mediaSubtype) {}

    private PreviewMetadata resolveOrDownloadPreview(
            File file,
            String baseName,
            boolean isServerActive,
            HttpClient downloadClient,
            String comfyUrl,
            Map<String, String> mediaSubtypes) {

        String[] extensions = {"webp", "png", "jpg", "jpeg", "gif", "mp4", "webm", "mov"};
        File parentDir = file.getParentFile();
        for (String ext : extensions) {
            File pFile1 = new File(parentDir, baseName + "-1." + ext);
            File pFile2 = new File(parentDir, baseName + "." + ext);
            if (pFile1.exists()) {
                String mediaType = ("mp4".equals(ext) || "webm".equals(ext) || "mov".equals(ext)) ? "video" : "image";
                return new PreviewMetadata(pFile1.getAbsolutePath(), mediaType, ext);
            } else if (pFile2.exists()) {
                String mediaType = ("mp4".equals(ext) || "webm".equals(ext) || "mov".equals(ext)) ? "video" : "image";
                return new PreviewMetadata(pFile2.getAbsolutePath(), mediaType, ext);
            }
        }

        if (isServerActive && downloadClient != null && comfyUrl != null && !comfyUrl.isEmpty()) {
            return downloadPreviewFromServer(parentDir, baseName, downloadClient, comfyUrl, mediaSubtypes);
        }

        return new PreviewMetadata("", "image", "");
    }

    private PreviewMetadata downloadPreviewFromServer(
            File parentDir,
            String baseName,
            HttpClient downloadClient,
            String comfyUrl,
            Map<String, String> mediaSubtypes) {

        String mediaSubtypeIdx = mediaSubtypes.get(baseName.toLowerCase().trim());
        List<String> urlsToTry = new ArrayList<>();
        if (mediaSubtypeIdx != null && !mediaSubtypeIdx.isEmpty()) {
            urlsToTry.add(comfyUrl + "/templates/" + java.net.URLEncoder.encode(baseName + "-1." + mediaSubtypeIdx, StandardCharsets.UTF_8).replace("+", "%20"));
            urlsToTry.add(comfyUrl + "/templates/" + java.net.URLEncoder.encode(baseName + "." + mediaSubtypeIdx, StandardCharsets.UTF_8).replace("+", "%20"));
        } else {
            for (String ext : new String[]{"webp", "png", "jpg", "mp4"}) {
                urlsToTry.add(comfyUrl + "/templates/" + java.net.URLEncoder.encode(baseName + "-1." + ext, StandardCharsets.UTF_8).replace("+", "%20"));
                urlsToTry.add(comfyUrl + "/templates/" + java.net.URLEncoder.encode(baseName + "." + ext, StandardCharsets.UTF_8).replace("+", "%20"));
            }
        }

        for (String urlStr : urlsToTry) {
            try {
                HttpRequest downloadRequest = HttpRequest.newBuilder().uri(URI.create(urlStr)).timeout(Duration.ofSeconds(3)).GET().build();
                HttpResponse<java.io.InputStream> downloadResponse = downloadClient.send(downloadRequest, HttpResponse.BodyHandlers.ofInputStream());

                if (downloadResponse.statusCode() == 200) {
                    String ext = "png";
                    String lowerUrl = urlStr.toLowerCase();
                    for (String possibleExt : new String[]{"webp", "png", "jpg", "jpeg", "gif", "mp4", "webm", "mov"}) {
                        if (lowerUrl.endsWith("." + possibleExt)) {
                            ext = possibleExt;
                            break;
                        }
                    }
                    File targetPreviewFile = new File(parentDir, baseName + "-1." + ext);
                    try (java.io.InputStream in = downloadResponse.body();
                         java.io.OutputStream out = new java.io.FileOutputStream(targetPreviewFile)) {
                        in.transferTo(out);
                    }
                    String mediaType = ("mp4".equals(ext) || "webm".equals(ext) || "mov".equals(ext)) ? "video" : "image";
                    return new PreviewMetadata(targetPreviewFile.getAbsolutePath(), mediaType, ext);
                }
            } catch (Exception ignored) {
            logger.debug("Ignored non-fatal exception: {}", ignored.getMessage());
        }
        }
        return new PreviewMetadata("", "image", "");
    }

    private void migrateBlueprintsFolder(File oldBlueprints, File newBlueprints) {
        if (oldBlueprints.exists() && oldBlueprints.isDirectory()) {
            if (!newBlueprints.exists()) {
                boolean renamed = oldBlueprints.renameTo(newBlueprints);
                if (renamed) {
                    logger.info("🔄 [BlueprintScanService] Migrated blueprints folder to companion_blueprints: {}", oldBlueprints.getAbsolutePath());
                } else {
                    logger.error("⚠️ [BlueprintScanService] Failed to rename blueprints folder to companion_blueprints: {}", oldBlueprints.getAbsolutePath());
                }
            } else {
                File[] oldFiles = oldBlueprints.listFiles();
                if (oldFiles != null) {
                    for (File f : oldFiles) {
                        File dest = new File(newBlueprints, f.getName());
                        if (!dest.exists()) {
                            f.renameTo(dest);
                        } else {
                            f.delete();
                        }
                    }
                }
                oldBlueprints.delete();
            }
        }
    }

    private boolean isComfyServerOnline(String comfyUrl, IComfyLifecycleService lifecycleService) {
        if (lifecycleService != null) {
            return lifecycleService.isHealthy();
        }
        if (comfyUrl == null || comfyUrl.isBlank()) {
            return false;
        }
        try {
            HttpClient probeClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(800))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(comfyUrl + "/system_stats"))
                    .timeout(Duration.ofMillis(1200))
                    .GET()
                    .build();
            HttpResponse<Void> resp = probeClient.send(request, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() >= 200 && resp.statusCode() < 500;
        } catch (Exception e) {
            return false;
        }
    }
}
