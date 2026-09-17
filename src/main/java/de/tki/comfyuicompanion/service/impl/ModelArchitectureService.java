package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfyuicompanion.domain.ModelArchitecture;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import de.tki.comfyuicompanion.service.IDefaultCacheBootstrapper;
import de.tki.comfyuicompanion.service.IModelArchitectureService;
import de.tki.comfyuicompanion.util.ComfyUIArchitectureClassifier;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core service managing model architecture recognition, workflow template analysis,
 * and inference parameter defaults.
 */
@Service
public class ModelArchitectureService implements IModelArchitectureService {
    private static final Logger logger = LoggerFactory.getLogger(ModelArchitectureService.class);

    private final ConfigService configService;
    private final List<ArchitectureMappingRepository.MappingRule> rules = new CopyOnWriteArrayList<>();
    private final Map<String, ModelArchitecture> architectureCache = new ConcurrentHashMap<>();
    private final Map<String, ModelDefaults> resolvedDefaultsMap = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> blueprintScanResults = new CopyOnWriteArrayList<>();
    private final List<BlueprintProgressListener> listeners = new CopyOnWriteArrayList<>();

    private volatile int progressPercent = 0;
    private volatile String progressFileName = "";
    private volatile boolean progressCompleted = false;
    private volatile boolean isAnalyzing = false;

    private ComfyUIArchitectureClassifier classifier;
    private ComfyModelAnalyzer modelAnalyzer;
    private final ModelListService modelListService;
    private final IDefaultCacheBootstrapper cacheBootstrapper;
    private final IComfyLifecycleService lifecycleService;
    private final WorkflowDefaultsExtractor defaultsExtractor;
    private final ServerBlueprintDiscoveryService serverDiscoveryService;
    private final ArchitectureMappingRepository mappingRepository;

    public ModelArchitectureService() {
        this(null, null, null, null, null, null);
    }

    public ModelArchitectureService(ConfigService configService) {
        this(configService, null, null, null, null, null);
    }

    public ModelArchitectureService(ConfigService configService, ComfyUIArchitectureClassifier classifier) {
        this(configService, classifier, null, null, null, null);
    }

    @Autowired
    public ModelArchitectureService(
            @Autowired(required = false) ConfigService configService,
            @Autowired(required = false) ComfyUIArchitectureClassifier classifier,
            @Autowired(required = false) ComfyModelAnalyzer modelAnalyzer,
            @Autowired(required = false) ModelListService modelListService,
            @Autowired(required = false) IDefaultCacheBootstrapper cacheBootstrapper,
            @Autowired(required = false) IComfyLifecycleService lifecycleService) {
        this(configService, classifier, modelAnalyzer, modelListService, cacheBootstrapper, lifecycleService,
                new WorkflowDefaultsExtractor(), new ServerBlueprintDiscoveryService(), new ArchitectureMappingRepository());
    }

    public ModelArchitectureService(
            ConfigService configService,
            ComfyUIArchitectureClassifier classifier,
            ComfyModelAnalyzer modelAnalyzer,
            ModelListService modelListService,
            IDefaultCacheBootstrapper cacheBootstrapper,
            IComfyLifecycleService lifecycleService,
            WorkflowDefaultsExtractor defaultsExtractor,
            ServerBlueprintDiscoveryService serverDiscoveryService,
            ArchitectureMappingRepository mappingRepository) {
        this.configService = configService;
        this.classifier = classifier;
        this.modelAnalyzer = modelAnalyzer;
        this.modelListService = modelListService;
        this.cacheBootstrapper = cacheBootstrapper;
        this.lifecycleService = lifecycleService;
        this.defaultsExtractor = defaultsExtractor != null ? defaultsExtractor : new WorkflowDefaultsExtractor();
        this.serverDiscoveryService = serverDiscoveryService != null ? serverDiscoveryService : new ServerBlueprintDiscoveryService();
        this.mappingRepository = mappingRepository != null ? mappingRepository : new ArchitectureMappingRepository();
    }

    private ComfyModelAnalyzer getModelAnalyzer() {
        if (modelAnalyzer == null) {
            modelAnalyzer = new ComfyModelAnalyzer();
        }
        return modelAnalyzer;
    }

    public void setAnalyzing(boolean isAnalyzing) {
        this.isAnalyzing = isAnalyzing;
    }

    @Override
    public boolean isAnalyzing() {
        return isAnalyzing;
    }

    @PostConstruct
    public void init() {
        if (cacheBootstrapper != null) {
            cacheBootstrapper.bootstrapDefaultCache();
        }
        reloadMappings();
        loadArchitectureCacheFromFile();
        loadResolvedDefaultsFromFile();
        loadBlueprintScanResultsFromFile();
        if (blueprintScanResults.isEmpty()) {
            runBlueprintAnalysis();
        } else {
            this.progressCompleted = true;
            this.progressPercent = 100;
        }
    }

    @Override
    public void runBlueprintAnalysis() {
        Thread t = new Thread(this::analyzeBlueprintsAndSaveDefaults, "BlueprintAnalyzer");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    @Override
    public void reloadMappings() {
        File configDir = new File(configService != null ? configService.getAppDataPath() : System.getProperty("user.dir"), "templates/comfyui");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        File mappingFile = new File(configDir, "model_architecture_mapping.json");
        List<ArchitectureMappingRepository.MappingRule> loaded = mappingRepository.loadRules(mappingFile);
        rules.clear();
        rules.addAll(loaded);
    }

    @Override
    public ModelArchitecture detectArchitecture(String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            return ModelArchitecture.ARCH_UNKNOWN;
        }

        String filename = new File(modelName).getName();

        if (architectureCache.containsKey(filename)) {
            return architectureCache.get(filename);
        }

        ModelArchitecture detected = ModelArchitecture.ARCH_UNKNOWN;

        // 1. Try rule mapping first (Instant regex matching)
        for (ArchitectureMappingRepository.MappingRule rule : rules) {
            if (rule.pattern().matcher(filename).matches()) {
                detected = rule.architecture();
                break;
            }
        }

        // 2. Heuristic keyword check fallbacks
        if (detected == ModelArchitecture.ARCH_UNKNOWN) {
            String lower = filename.toLowerCase();
            if (lower.contains("flux") || lower.contains("schnell") || lower.contains("dev")) {
                detected = ModelArchitecture.ARCH_FLUX;
            } else if (lower.contains("sdxl") || lower.contains("xl") || lower.contains("pony") || lower.contains("juggernaut") || lower.contains("ernie")) {
                detected = ModelArchitecture.ARCH_SDXL;
            } else if (lower.contains("sd3") || lower.contains("stable_diffusion_3")) {
                detected = ModelArchitecture.ARCH_SD3;
            } else if (lower.contains("hunyuan")) {
                detected = ModelArchitecture.ARCH_HUNYUAN;
            } else if (lower.contains("wan") || lower.contains("ltx")) {
                detected = ModelArchitecture.ARCH_WAN;
            } else if (lower.contains("longcat") || lower.contains("lumina") || lower.contains("qwen") || lower.contains("firered") || lower.contains("z_image") || lower.contains("acestep")) {
                detected = ModelArchitecture.ARCH_LUMINA2;
            } else if (lower.contains("sd15") || lower.contains("1.5") || lower.contains("v1-5")) {
                detected = ModelArchitecture.ARCH_SD15;
            }
        }

        // 3. Fallback to Gemma LLM detection ONLY if still UNKNOWN and not in local model list
        if (detected == ModelArchitecture.ARCH_UNKNOWN) {
            boolean isModelInProvidedList = modelListService != null && modelListService.findByFilename(filename).isPresent();
            if (!isModelInProvidedList && isAnalyzing && classifier != null) {
                try {
                    detected = classifier.classifyModel(filename);
                } catch (Exception e) {
                    logger.error("⚠️ [ModelArchitectureService] Gemma classification failed: {}", e.getMessage());
                }
            }
        }

        if (detected != ModelArchitecture.ARCH_UNKNOWN) {
            architectureCache.put(filename, detected);
        }

        return detected;
    }

    private synchronized void notifyListeners(int percent, String currentFileName, boolean completed) {
        this.progressPercent = percent;
        this.progressFileName = currentFileName;
        this.progressCompleted = completed;
        for (BlueprintProgressListener listener : listeners) {
            try {
                listener.onProgress(percent, currentFileName, completed);
            } catch (Exception ignored) {}
        }
    }

    private void migrateBlueprintsFolder(File oldBlueprints, File newBlueprints) {
        if (oldBlueprints.exists() && oldBlueprints.isDirectory()) {
            if (!newBlueprints.exists()) {
                boolean renamed = oldBlueprints.renameTo(newBlueprints);
                if (renamed) {
                    logger.info("🔄 [ArchitectureService] Migrated blueprints folder to companion_blueprints: {}", oldBlueprints.getAbsolutePath());
                } else {
                    logger.error("⚠️ [ArchitectureService] Failed to rename blueprints folder to companion_blueprints: {}", oldBlueprints.getAbsolutePath());
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

    private boolean isComfyServerOnline(String comfyUrl) {
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

    private void analyzeBlueprintsAndSaveDefaults() {
        isAnalyzing = true;
        try {
            String comfyPath = configService != null ? configService.getComfyUIPath() : "";
            if (comfyPath != null && !comfyPath.trim().isEmpty()) {
                migrateBlueprintsFolder(new File(comfyPath, "blueprints"), new File(comfyPath, "companion_blueprints"));
                migrateBlueprintsFolder(new File(comfyPath, "resources/ComfyUI/blueprints"), new File(comfyPath, "resources/ComfyUI/companion_blueprints"));
            }

            File blueprintsDir = null;
            if (comfyPath != null && !comfyPath.trim().isEmpty()) {
                File directBlueprints = new File(comfyPath, "companion_blueprints");
                if (directBlueprints.exists() && directBlueprints.isDirectory()) {
                    blueprintsDir = directBlueprints;
                } else {
                    File resourcesBlueprints = new File(comfyPath, "resources/ComfyUI/companion_blueprints");
                    if (resourcesBlueprints.exists() && resourcesBlueprints.isDirectory()) {
                        blueprintsDir = resourcesBlueprints;
                    }
                }
            }

            if (blueprintsDir == null) {
                blueprintsDir = new File(comfyPath != null ? comfyPath : "", "companion_blueprints");
            }

            if (!blueprintsDir.exists() || !blueprintsDir.isDirectory()) {
                logger.error("⚠️ [ArchitectureService] Blueprints directory not found: {}", blueprintsDir.getAbsolutePath());
                loadResolvedDefaultsFromFile();
                notifyListeners(100, "Done (No blueprints directory)", true);
                return;
            }

            logger.info("🔍 [ArchitectureService] Starting ComfyUI blueprints evaluation...");
            File[] files = blueprintsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
            if (files == null || files.length == 0) {
                loadResolvedDefaultsFromFile();
                notifyListeners(100, "Done (No blueprints found)", true);
                return;
            }

            String comfyUrl = configService != null ? configService.getComfyUIUrl() : null;
            boolean serverOnline = isComfyServerOnline(comfyUrl);
            Map<String, String> mediaSubtypes = new HashMap<>();
            HttpClient sharedDownloadClient = null;

            if (serverOnline && comfyUrl != null && !comfyUrl.isEmpty()) {
                try {
                    sharedDownloadClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
                    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(comfyUrl + "/templates/index.json")).timeout(Duration.ofSeconds(3)).GET().build();
                    HttpResponse<String> response = sharedDownloadClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        ObjectMapper om = new ObjectMapper();
                        JsonNode indexRoot = om.readTree(response.body());
                        serverDiscoveryService.parseSubtypesFromIndex(indexRoot, mediaSubtypes);
                    }
                } catch (Exception e) {
                    logger.warn("⚠️ [ArchitectureService] Failed to fetch server templates index for previews: {}", e.getMessage());
                    serverOnline = false;
                }
            } else {
                logger.info("ℹ️ [ArchitectureService] ComfyUI server is offline/unreachable. Skipping remote preview downloads and server blueprints discovery.");
            }

            final boolean isServerActive = serverOnline;
            final HttpClient downloadClient = sharedDownloadClient;

            notifyListeners(0, "Starting...", false);
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> scanList = new CopyOnWriteArrayList<>();
            AtomicInteger processedCount = new AtomicInteger(0);

            Arrays.stream(files).parallel().forEach(file -> {
                String currentFileName = file.getName();
                try {
                    String baseName = currentFileName;
                    if (baseName.toLowerCase().endsWith(".json")) {
                        baseName = baseName.substring(0, baseName.length() - 5);
                    }

                    boolean hasLocalPreview = false;
                    String previewPath = "";
                    String mediaType = "image";
                    String mediaSubtype = "";
                    String[] extensions = {"webp", "png", "jpg", "jpeg", "gif", "mp4", "webm", "mov"};
                    File parentDir = file.getParentFile();
                    for (String ext : extensions) {
                        File pFile1 = new File(parentDir, baseName + "-1." + ext);
                        File pFile2 = new File(parentDir, baseName + "." + ext);
                        if (pFile1.exists()) {
                            hasLocalPreview = true;
                            previewPath = pFile1.getAbsolutePath();
                            mediaSubtype = ext;
                            if ("mp4".equals(ext) || "webm".equals(ext) || "mov".equals(ext)) mediaType = "video";
                            break;
                        } else if (pFile2.exists()) {
                            hasLocalPreview = true;
                            previewPath = pFile2.getAbsolutePath();
                            mediaSubtype = ext;
                            if ("mp4".equals(ext) || "webm".equals(ext) || "mov".equals(ext)) mediaType = "video";
                            break;
                        }
                    }

                    if (isServerActive && downloadClient != null && !hasLocalPreview && comfyUrl != null && !comfyUrl.isEmpty()) {
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
                                    logger.info("📥 [ArchitectureService] Downloaded preview for {} to {}", baseName, targetPreviewFile.getName());
                                    previewPath = targetPreviewFile.getAbsolutePath();
                                    mediaSubtype = ext;
                                    if ("mp4".equals(ext) || "webm".equals(ext) || "mov".equals(ext)) mediaType = "video";
                                    break;
                                }
                            } catch (Exception ignored) {}
                        }
                    }

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
                        requiredModels = getModelAnalyzer().analyze(rawJson, file.getName());
                    } catch (Exception e) {
                        requiredModels = Collections.emptyList();
                    }

                    Map<String, Object> blueprintMap = new HashMap<>();
                    blueprintMap.put("name", baseName);
                    blueprintMap.put("filename", file.getName());
                    blueprintMap.put("filePath", "companion_blueprints/" + file.getName());
                    blueprintMap.put("category", category);
                    blueprintMap.put("description", description);
                    blueprintMap.put("mediaType", mediaType);
                    blueprintMap.put("mediaSubtype", mediaSubtype);
                    blueprintMap.put("previewPath", previewPath != null && previewPath.startsWith("http") ? previewPath : "");
                    blueprintMap.put("requiredModels", requiredModels);
                    scanList.add(blueprintMap);

                    defaultsExtractor.extractDefaultsFromWorkflow(rawJson, file.getName(), mapper,
                            this::detectArchitecture, this::getStandardDefaultsForArchitecture, resolvedDefaultsMap);

                } catch (Exception e) {
                    logger.error("⚠️ [ArchitectureService] Failed to parse blueprint {}: {}", file.getName(), e.getMessage());
                } finally {
                    int done = processedCount.incrementAndGet();
                    int percent = (int) (((double) done / files.length) * 100);
                    notifyListeners(percent, currentFileName, false);
                }
            });

            if (isServerActive) {
                serverDiscoveryService.discoverServerBlueprints(scanList, mediaSubtypes, comfyUrl, mapper,
                        getModelAnalyzer(), defaultsExtractor,
                        (rawJson, jsonFilename) -> defaultsExtractor.extractDefaultsFromWorkflow(
                                rawJson, jsonFilename, mapper, this::detectArchitecture,
                                this::getStandardDefaultsForArchitecture, resolvedDefaultsMap));
            }

            saveResolvedDefaultsToFile();
            saveArchitectureCacheToFile();

            File scanResultsFile = new File(configService != null ? configService.getAppDataPath() : System.getProperty("user.dir"), "templates/comfyui/blueprint_scan_results.json");
            try {
                mapper.writerWithDefaultPrettyPrinter().writeValue(scanResultsFile, scanList);
                blueprintScanResults.clear();
                blueprintScanResults.addAll(scanList);
                logger.info("💾 [ArchitectureService] Wrote blueprint scan results to cache: {}", scanResultsFile.getAbsolutePath());
            } catch (Exception e) {
                logger.error("❌ [ArchitectureService] Failed to write blueprint scan results: {}", e.getMessage());
            }

            notifyListeners(100, "Completed", true);
        } finally {
            isAnalyzing = false;
        }
    }

    private void saveResolvedDefaultsToFile() {
        File targetFile = new File(configService != null ? configService.getAppDataPath() : System.getProperty("user.dir"), "templates/comfyui/model_resolved_defaults.json");
        try {
            File parentDir = targetFile.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }
            ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter().writeValue(targetFile, resolvedDefaultsMap);
            logger.info("💾 [ArchitectureService] Wrote blueprint resolved model defaults to: {}", targetFile.getAbsolutePath());
        } catch (Exception e) {
            logger.error("❌ [ArchitectureService] Failed to write model resolved defaults: {}", e.getMessage());
        }
    }

    private void loadResolvedDefaultsFromFile() {
        File targetFile = new File(configService != null ? configService.getAppDataPath() : System.getProperty("user.dir"), "templates/comfyui/model_resolved_defaults.json");
        if (!targetFile.exists()) {
            return;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(targetFile);
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey().toLowerCase().trim();
                JsonNode val = field.getValue();
                ModelDefaults defaults = new ModelDefaults(
                        val.path("vaeName").asText(null),
                        val.path("clipType").asText(null),
                        val.path("scheduler").asText(null),
                        val.path("samplerName").asText(null),
                        val.path("architecture").asText("ARCH_UNKNOWN")
                );
                resolvedDefaultsMap.put(key, defaults);
            }
            logger.info("📂 [ArchitectureService] Loaded {} resolved model defaults.", resolvedDefaultsMap.size());
        } catch (Exception e) {
            logger.error("❌ [ArchitectureService] Failed to load model resolved defaults: {}", e.getMessage());
        }
    }

    @Override
    public ModelDefaults getDefaultsForModel(String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            return getStandardDefaultsForArchitecture(ModelArchitecture.ARCH_UNKNOWN);
        }

        String filename = new File(modelName).getName();
        String cleanName = filename.toLowerCase().trim();

        if (resolvedDefaultsMap.containsKey(cleanName)) {
            return resolvedDefaultsMap.get(cleanName);
        }

        if (resolvedDefaultsMap.isEmpty()) {
            loadResolvedDefaultsFromFile();
            if (resolvedDefaultsMap.containsKey(cleanName)) {
                return resolvedDefaultsMap.get(cleanName);
            }
        }

        if (cleanName.startsWith("video_")) {
            return null;
        }

        ModelArchitecture arch = detectArchitecture(filename);
        ModelDefaults defaults = getStandardDefaultsForArchitecture(arch);
        resolvedDefaultsMap.put(cleanName, defaults);
        return defaults;
    }

    public ModelDefaults getStandardDefaultsForArchitecture(ModelArchitecture arch) {
        if (arch == null) arch = ModelArchitecture.ARCH_UNKNOWN;
        return switch (arch) {
            case ARCH_FLUX -> new ModelDefaults("FLUX1/ae.safetensors", "flux", "normal", "euler", arch.name());
            case ARCH_SDXL -> new ModelDefaults("sdxl_vae.safetensors", "stable_diffusion", "normal", "euler", arch.name());
            case ARCH_SD3 -> new ModelDefaults("sd3_vae.safetensors", "sd3", "normal", "euler", arch.name());
            case ARCH_LUMINA2 -> new ModelDefaults("ae.safetensors", "longcat_image", "simple", "euler", arch.name());
            case ARCH_WAN -> new ModelDefaults("wan_2.1_vae.safetensors", "wan", "normal", "euler", arch.name());
            case ARCH_SD15 -> new ModelDefaults("vae-ft-mse-840000-ema-pruned.safetensors", "stable_diffusion", "normal", "euler", arch.name());
            default -> new ModelDefaults("vae-ft-mse-840000-ema-pruned.safetensors", "stable_diffusion", "normal", "euler", arch.name());
        };
    }

    @Override
    public synchronized void addProgressListener(BlueprintProgressListener listener) {
        listeners.add(listener);
        listener.onProgress(progressPercent, progressFileName, progressCompleted);
    }

    @Override
    public synchronized void removeProgressListener(BlueprintProgressListener listener) {
        listeners.remove(listener);
    }

    @Override
    public synchronized int getBlueprintProgressPercent() {
        return progressPercent;
    }

    @Override
    public synchronized String getBlueprintProgressFileName() {
        return progressFileName;
    }

    @Override
    public synchronized boolean isBlueprintAnalysisCompleted() {
        return progressCompleted;
    }

    private void saveArchitectureCacheToFile() {
        File targetFile = new File(configService != null ? configService.getAppDataPath() : System.getProperty("user.dir"), "templates/comfyui/model_architectures.json");
        try {
            File parentDir = targetFile.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }
            ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter().writeValue(targetFile, architectureCache);
            logger.info("💾 [ModelArchitectureService] Wrote architecture cache to: {}", targetFile.getAbsolutePath());
        } catch (Exception e) {
            logger.error("❌ [ModelArchitectureService] Failed to write architecture cache: {}", e.getMessage());
        }
    }

    private void loadArchitectureCacheFromFile() {
        File targetFile = new File(configService != null ? configService.getAppDataPath() : System.getProperty("user.dir"), "templates/comfyui/model_architectures.json");
        if (!targetFile.exists()) {
            return;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(targetFile);
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey();
                String val = field.getValue().asText();
                try {
                    ModelArchitecture arch = ModelArchitecture.valueOf(val);
                    architectureCache.put(key, arch);
                } catch (IllegalArgumentException e) {
                    logger.error("⚠️ [ModelArchitectureService] Unknown architecture in cache file: {}", val);
                }
            }
            logger.info("📂 [ModelArchitectureService] Loaded {} cached model architectures.", architectureCache.size());
        } catch (Exception e) {
            logger.error("❌ [ModelArchitectureService] Failed to load architecture cache: {}", e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> getBlueprintScanResults() {
        if (blueprintScanResults.isEmpty()) {
            loadBlueprintScanResultsFromFile();
        }
        return blueprintScanResults;
    }

    private void loadBlueprintScanResultsFromFile() {
        File targetFile = new File(configService != null ? configService.getAppDataPath() : System.getProperty("user.dir"), "templates/comfyui/blueprint_scan_results.json");
        if (!targetFile.exists()) {
            return;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(targetFile);
            List<Map<String, Object>> list = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode node : root) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> entry = mapper.convertValue(node, Map.class);
                    list.add(entry);
                }
            }
            blueprintScanResults.clear();
            blueprintScanResults.addAll(list);
            logger.info("📂 [ModelArchitectureService] Loaded {} scanned blueprint results from cache.", blueprintScanResults.size());
        } catch (Exception e) {
            logger.error("❌ [ModelArchitectureService] Failed to load scanned blueprint results: {}", e.getMessage());
        }
    }
}
