package de.tki.comfymodels.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tki.comfymodels.domain.ModelArchitecture;
import de.tki.comfymodels.service.IModelArchitectureService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfymodels.util.ComfyUIArchitectureClassifier;
import de.tki.comfymodels.domain.ModelInfo;
import java.util.Collections;

@Service
public class ModelArchitectureService implements IModelArchitectureService {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ModelArchitectureService.class);

    private final ConfigService configService;
    private final List<MappingRule> rules = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final Map<String, ModelArchitecture> architectureCache = new ConcurrentHashMap<>();

    private final List<BlueprintProgressListener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile int progressPercent = 0;
    private volatile String progressFileName = "";
    private volatile boolean progressCompleted = false;

    private ComfyUIArchitectureClassifier classifier;
    private ComfyModelAnalyzer modelAnalyzer;
    private final ModelListService modelListService;

    private final de.tki.comfymodels.service.IDefaultCacheBootstrapper cacheBootstrapper;

    public ModelArchitectureService() {
        this(null, null, null, null, null);
    }

    public ModelArchitectureService(ConfigService configService) {
        this(configService, null, null, null, null);
    }

    public ModelArchitectureService(ConfigService configService, ComfyUIArchitectureClassifier classifier) {
        this(configService, classifier, null, null, null);
    }

    @Autowired
    public ModelArchitectureService(
            @Autowired(required = false) ConfigService configService,
            @Autowired(required = false) ComfyUIArchitectureClassifier classifier,
            @Autowired(required = false) ComfyModelAnalyzer modelAnalyzer,
            @Autowired(required = false) ModelListService modelListService,
            @Autowired(required = false) de.tki.comfymodels.service.IDefaultCacheBootstrapper cacheBootstrapper) {
        this.configService = configService;
        this.classifier = classifier;
        this.modelAnalyzer = modelAnalyzer;
        this.modelListService = modelListService;
        this.cacheBootstrapper = cacheBootstrapper;
    }

    private final List<Map<String, Object>> blueprintScanResults = new java.util.concurrent.CopyOnWriteArrayList<>();

    private ComfyModelAnalyzer getModelAnalyzer() {
        if (modelAnalyzer == null) {
            modelAnalyzer = new ComfyModelAnalyzer();
        }
        return modelAnalyzer;
    }

    private volatile boolean isAnalyzing = false;

    public void setAnalyzing(boolean isAnalyzing) {
        this.isAnalyzing = isAnalyzing;
    }

    public boolean isAnalyzing() {
        return isAnalyzing;
    }

    private static class MappingRule {
        final Pattern pattern;
        final ModelArchitecture architecture;

        MappingRule(String regex, ModelArchitecture architecture) {
            this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            this.architecture = architecture;
        }
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
        List<MappingRule> loadedRules = new ArrayList<>();
        File configDir = new File(configService.getAppDataPath(), "templates/comfyui");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        File mappingFile = new File(configDir, "model_architecture_mapping.json");
        if (!mappingFile.exists()) {
            writeDefaultMappingFile(mappingFile);
        }

        try {
            String content = Files.readString(mappingFile.toPath(), StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(content);
            if (root.has("rules")) {
                JSONArray rulesArray = root.getJSONArray("rules");
                for (int i = 0; i < rulesArray.length(); i++) {
                    JSONObject ruleObj = rulesArray.getJSONObject(i);
                    String patternStr = ruleObj.getString("pattern");
                    String archStr = ruleObj.getString("architecture");
                    try {
                        ModelArchitecture arch = ModelArchitecture.valueOf(archStr);
                        loadedRules.add(new MappingRule(patternStr, arch));
                    } catch (IllegalArgumentException e) {
                        logger.error("⚠️ [ArchitectureService] Unknown architecture in mapping rule: " + archStr);
                    }
                }
            }
            rules.clear();
            rules.addAll(loadedRules);
        } catch (Exception e) {
            logger.error("❌ [ArchitectureService] Failed to load architecture mapping: " + e.getMessage());
            loadFallbackRules();
        }
    }

    @Override
    public ModelArchitecture detectArchitecture(String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            return ModelArchitecture.ARCH_UNKNOWN;
        }
        
        String filename = new File(modelName).getName();
        
        // Try Cache first
        if (architectureCache.containsKey(filename)) {
            return architectureCache.get(filename);
        }

        ModelArchitecture detected = ModelArchitecture.ARCH_UNKNOWN;

        // 1. Try rule mapping first (Instant regex matching)
        for (MappingRule rule : rules) {
            if (rule.pattern.matcher(filename).matches()) {
                detected = rule.architecture;
                break;
            }
        }

        // 2. Heuristic keyword check fallbacks in case config rules don't match
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
                    logger.error("⚠️ [ModelArchitectureService] Gemma classification failed: " + e.getMessage());
                }
            }
        }

        if (detected != ModelArchitecture.ARCH_UNKNOWN) {
            architectureCache.put(filename, detected);
        }

        return detected;
    }

    private void loadFallbackRules() {
        rules.clear();
        rules.add(new MappingRule(".*flux.*", ModelArchitecture.ARCH_FLUX));
        rules.add(new MappingRule(".*schnell.*", ModelArchitecture.ARCH_FLUX));
        rules.add(new MappingRule(".*sd[_-]?xl.*", ModelArchitecture.ARCH_SDXL));
        rules.add(new MappingRule(".*pony.*", ModelArchitecture.ARCH_SDXL));
        rules.add(new MappingRule(".*juggernaut.*", ModelArchitecture.ARCH_SDXL));
        rules.add(new MappingRule(".*sd[_-]?3.*", ModelArchitecture.ARCH_SD3));
        rules.add(new MappingRule(".*stable[_-]?diffusion[_-]?3.*", ModelArchitecture.ARCH_SD3));
        rules.add(new MappingRule(".*hunyuan.*", ModelArchitecture.ARCH_HUNYUAN));
        rules.add(new MappingRule(".*wan2.*", ModelArchitecture.ARCH_WAN));
        rules.add(new MappingRule(".*wan_.*", ModelArchitecture.ARCH_WAN));
        rules.add(new MappingRule(".*ltx.*", ModelArchitecture.ARCH_WAN));
        rules.add(new MappingRule(".*longcat.*", ModelArchitecture.ARCH_LUMINA2));
        rules.add(new MappingRule(".*lumina.*", ModelArchitecture.ARCH_LUMINA2));
        rules.add(new MappingRule(".*qwen.*", ModelArchitecture.ARCH_LUMINA2));
        rules.add(new MappingRule(".*firered.*", ModelArchitecture.ARCH_LUMINA2));
        rules.add(new MappingRule(".*z[_-]image.*", ModelArchitecture.ARCH_LUMINA2));
        rules.add(new MappingRule(".*acestep.*", ModelArchitecture.ARCH_LUMINA2));
        rules.add(new MappingRule(".*ernie.*", ModelArchitecture.ARCH_SDXL));
        rules.add(new MappingRule(".*1[_-]?5.*", ModelArchitecture.ARCH_SD15));
        rules.add(new MappingRule(".*sd[_-]?15.*", ModelArchitecture.ARCH_SD15));
    }

    private void writeDefaultMappingFile(File file) {
        String content = "{\n" +
                "  \"rules\": [\n" +
                "    { \"pattern\": \".*flux.*\", \"architecture\": \"ARCH_FLUX\" },\n" +
                "    { \"pattern\": \".*schnell.*\", \"architecture\": \"ARCH_FLUX\" },\n" +
                "    { \"pattern\": \".*sd[_-]?xl.*\", \"architecture\": \"ARCH_SDXL\" },\n" +
                "    { \"pattern\": \".*pony.*\", \"architecture\": \"ARCH_SDXL\" },\n" +
                "    { \"pattern\": \".*juggernaut.*\", \"architecture\": \"ARCH_SDXL\" },\n" +
                "    { \"pattern\": \".*sd[_-]?3.*\", \"architecture\": \"ARCH_SD3\" },\n" +
                "    { \"pattern\": \".*stable[_-]?diffusion[_-]?3.*\", \"architecture\": \"ARCH_SD3\" },\n" +
                "    { \"pattern\": \".*hunyuan.*\", \"architecture\": \"ARCH_HUNYUAN\" },\n" +
                "    { \"pattern\": \".*wan2.*\", \"architecture\": \"ARCH_WAN\" },\n" +
                "    { \"pattern\": \".*wan_.*\", \"architecture\": \"ARCH_WAN\" },\n" +
                "    { \"pattern\": \".*ltx.*\", \"architecture\": \"ARCH_WAN\" },\n" +
                "    { \"pattern\": \".*longcat.*\", \"architecture\": \"ARCH_LUMINA2\" },\n" +
                "    { \"pattern\": \".*lumina.*\", \"architecture\": \"ARCH_LUMINA2\" },\n" +
                "    { \"pattern\": \".*qwen.*\", \"architecture\": \"ARCH_LUMINA2\" },\n" +
                "    { \"pattern\": \".*firered.*\", \"architecture\": \"ARCH_LUMINA2\" },\n" +
                "    { \"pattern\": \".*z[_-]image.*\", \"architecture\": \"ARCH_LUMINA2\" },\n" +
                "    { \"pattern\": \".*acestep.*\", \"architecture\": \"ARCH_LUMINA2\" },\n" +
                "    { \"pattern\": \".*ernie.*\", \"architecture\": \"ARCH_SDXL\" },\n" +
                "    { \"pattern\": \".*1[_-]?5.*\", \"architecture\": \"ARCH_SD15\" },\n" +
                "    { \"pattern\": \".*sd[_-]?15.*\", \"architecture\": \"ARCH_SD15\" }\n" +
                "  ]\n" +
                "}";
        try {
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
            logger.info("💾 [ArchitectureService] Wrote default model architecture mapping config to: " + file.getAbsolutePath());
        } catch (IOException e) {
            logger.error("❌ [ArchitectureService] Failed to write default mapping config: " + e.getMessage());
        }
    }

    private final Map<String, ModelDefaults> resolvedDefaultsMap = new ConcurrentHashMap<>();

    private synchronized void notifyListeners(int percent, String currentFileName, boolean completed) {
        this.progressPercent = percent;
        this.progressFileName = currentFileName;
        this.progressCompleted = completed;
        for (BlueprintProgressListener listener : listeners) {
            try {
                listener.onProgress(percent, currentFileName, completed);
            } catch (Exception e) {
                // Ignore listener exceptions
            }
        }
    }

    private void migrateBlueprintsFolder(File oldBlueprints, File newBlueprints) {
        if (oldBlueprints.exists() && oldBlueprints.isDirectory()) {
            if (!newBlueprints.exists()) {
                boolean renamed = oldBlueprints.renameTo(newBlueprints);
                if (renamed) {
                    logger.info("🔄 [ArchitectureService] Migrated blueprints folder to companion_blueprints: " + oldBlueprints.getAbsolutePath());
                } else {
                    logger.error("⚠️ [ArchitectureService] Failed to rename blueprints folder to companion_blueprints: " + oldBlueprints.getAbsolutePath());
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

    private void analyzeBlueprintsAndSaveDefaults() {
        isAnalyzing = true;
        try {
            String comfyPath = configService.getComfyUIPath();
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
                logger.error("⚠️ [ArchitectureService] Blueprints directory not found: " + blueprintsDir.getAbsolutePath());
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

            // Fetch template media subtypes from ComfyUI to know correct preview formats
            Map<String, String> mediaSubtypes = new java.util.HashMap<>();
            String comfyUrl = configService.getComfyUIUrl();
            if (comfyUrl != null && !comfyUrl.isEmpty()) {
                try {
                    java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                            .connectTimeout(java.time.Duration.ofSeconds(3))
                            .build();
                    java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(comfyUrl + "/templates/index.json"))
                            .timeout(java.time.Duration.ofSeconds(5))
                            .GET().build();
                    java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        ObjectMapper om = new ObjectMapper();
                        JsonNode indexRoot = om.readTree(response.body());
                        parseSubtypesFromIndex(indexRoot, mediaSubtypes);
                    }
                } catch (Exception e) {
                    logger.error("⚠️ [ArchitectureService] Failed to fetch server templates index for previews: " + e.getMessage());
                }
            }

            notifyListeners(0, "Starting...", false);
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> scanList = new java.util.concurrent.CopyOnWriteArrayList<>();
            java.util.concurrent.atomic.AtomicInteger processedCount = new java.util.concurrent.atomic.AtomicInteger(0);

            java.util.Arrays.stream(files).parallel().forEach(file -> {
                String currentFileName = file.getName();
                try {
                    String baseName = currentFileName;
                    if (baseName.toLowerCase().endsWith(".json")) {
                        baseName = baseName.substring(0, baseName.length() - 5);
                    }

                    // Check if any preview file exists locally
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
                            if (ext.equals("mp4") || ext.equals("webm") || ext.equals("mov")) {
                                mediaType = "video";
                            }
                            break;
                        } else if (pFile2.exists()) {
                            hasLocalPreview = true;
                            previewPath = pFile2.getAbsolutePath();
                            mediaSubtype = ext;
                            if (ext.equals("mp4") || ext.equals("webm") || ext.equals("mov")) {
                                mediaType = "video";
                            }
                            break;
                        }
                    }

                    // Download missing preview from ComfyUI server if server is configured
                    if (!hasLocalPreview && comfyUrl != null && !comfyUrl.isEmpty()) {
                        String mediaSubtypeIdx = mediaSubtypes.get(baseName.toLowerCase().trim());
                        List<String> urlsToTry = new ArrayList<>();
                        if (mediaSubtypeIdx != null && !mediaSubtypeIdx.isEmpty()) {
                            urlsToTry.add(comfyUrl + "/templates/" + java.net.URLEncoder.encode(baseName + "-1." + mediaSubtypeIdx, StandardCharsets.UTF_8).replace("+", "%20"));
                            urlsToTry.add(comfyUrl + "/templates/" + java.net.URLEncoder.encode(baseName + "." + mediaSubtypeIdx, StandardCharsets.UTF_8).replace("+", "%20"));
                        }
                        for (String ext : new String[]{"webp", "png", "jpg", "mp4"}) {
                            if (mediaSubtypeIdx == null || !ext.equalsIgnoreCase(mediaSubtypeIdx)) {
                                urlsToTry.add(comfyUrl + "/templates/" + java.net.URLEncoder.encode(baseName + "-1." + ext, StandardCharsets.UTF_8).replace("+", "%20"));
                                urlsToTry.add(comfyUrl + "/templates/" + java.net.URLEncoder.encode(baseName + "." + ext, StandardCharsets.UTF_8).replace("+", "%20"));
                            }
                        }

                        for (String urlStr : urlsToTry) {
                            try {
                                java.net.http.HttpClient downloadClient = java.net.http.HttpClient.newBuilder()
                                        .connectTimeout(java.time.Duration.ofSeconds(2))
                                        .build();
                                java.net.http.HttpRequest downloadRequest = java.net.http.HttpRequest.newBuilder()
                                        .uri(java.net.URI.create(urlStr))
                                        .timeout(java.time.Duration.ofSeconds(5))
                                        .GET().build();
                                java.net.http.HttpResponse<java.io.InputStream> downloadResponse = downloadClient.send(
                                        downloadRequest, java.net.http.HttpResponse.BodyHandlers.ofInputStream());

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
                                    logger.info("📥 [ArchitectureService] Downloaded preview for " + baseName + " to " + targetPreviewFile.getName());
                                    previewPath = targetPreviewFile.getAbsolutePath();
                                    mediaSubtype = ext;
                                    if (ext.equals("mp4") || ext.equals("webm") || ext.equals("mov")) {
                                        mediaType = "video";
                                    }
                                    break;
                                }
                            } catch (Exception ignored) {}
                        }
                    }

                    String rawJson = Files.readString(file.toPath());
                    JsonNode root = mapper.readTree(rawJson);

                    // Category and description
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

                    // analyze required models using getModelAnalyzer()
                    List<ModelInfo> requiredModels;
                    try {
                        requiredModels = getModelAnalyzer().analyze(rawJson, file.getName());
                    } catch (Exception e) {
                        requiredModels = Collections.emptyList();
                    }

                    Map<String, Object> blueprintMap = new java.util.HashMap<>();
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

                    extractDefaultsFromWorkflow(rawJson, file.getName(), mapper);

                } catch (Exception e) {
                    logger.error("⚠️ [ArchitectureService] Failed to parse blueprint " + file.getName() + ": " + e.getMessage());
                } finally {
                    int done = processedCount.incrementAndGet();
                    int percent = (int) (((double) done / files.length) * 100);
                    notifyListeners(percent, currentFileName, false);
                }
            });

            // Discover and analyze server blueprints/templates from ComfyUI
            discoverServerBlueprints(scanList, mediaSubtypes, comfyUrl, mapper);

            saveResolvedDefaultsToFile();
            saveArchitectureCacheToFile();

            // Save blueprint_scan_results.json
            File scanResultsFile = new File(configService.getAppDataPath(), "templates/comfyui/blueprint_scan_results.json");
            try {
                mapper.writerWithDefaultPrettyPrinter().writeValue(scanResultsFile, scanList);
                blueprintScanResults.clear();
                blueprintScanResults.addAll(scanList);
                logger.info("💾 [ArchitectureService] Wrote blueprint scan results to cache: " + scanResultsFile.getAbsolutePath());
            } catch (Exception e) {
                logger.error("❌ [ArchitectureService] Failed to write blueprint scan results: " + e.getMessage());
            }

            notifyListeners(100, "Completed", true);
        } finally {
            isAnalyzing = false;
        }
    }

    private void extractVideoModels(JsonNode root) {
        if (root == null) return;
        
        boolean isVideoBlueprint = false;
        List<String> unets = new ArrayList<>();
        List<String> vaes = new ArrayList<>();
        List<String> clips = new ArrayList<>();
        List<String> loras = new ArrayList<>();
        
        // Find nodes in API format
        if (root.isObject() && !root.has("nodes")) {
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode node = field.getValue();
                if (node.isObject() && node.has("class_type")) {
                    String classType = node.path("class_type").asText();
                    if ("WanImageToVideo".equalsIgnoreCase(classType) || classType.toLowerCase().startsWith("ltxv")) {
                        isVideoBlueprint = true;
                    }
                    JsonNode inputs = node.path("inputs");
                    if (inputs.isObject()) {
                        if ("UNETLoader".equalsIgnoreCase(classType)) {
                            String unet = inputs.path("unet_name").asText();
                            if (!unet.isEmpty()) unets.add(unet);
                        } else if ("VAELoader".equalsIgnoreCase(classType)) {
                            String vae = inputs.path("vae_name").asText();
                            if (!vae.isEmpty()) vaes.add(vae);
                        } else if ("CLIPLoader".equalsIgnoreCase(classType)) {
                            String clip = inputs.path("clip_name").asText();
                            if (!clip.isEmpty()) clips.add(clip);
                        } else if ("LoraLoader".equalsIgnoreCase(classType)) {
                            String lora = inputs.path("lora_name").asText();
                            if (!lora.isEmpty()) loras.add(lora);
                        }
                    }
                }
            }
        }
        
        // Find nodes in UI/Workflow format
        JsonNode nodesArray = root.path("nodes");
        if (nodesArray.isArray()) {
            for (JsonNode node : nodesArray) {
                String type = node.path("type").asText();
                if ("WanImageToVideo".equalsIgnoreCase(type) || type.toLowerCase().startsWith("ltxv")) {
                    isVideoBlueprint = true;
                }
                JsonNode widgets = node.path("widgets_values");
                if (widgets.isArray() && widgets.size() > 0) {
                    if ("UNETLoader".equalsIgnoreCase(type)) {
                        String unet = widgets.get(0).asText();
                        if (!unet.isEmpty()) unets.add(unet);
                    } else if ("VAELoader".equalsIgnoreCase(type)) {
                        String vae = widgets.get(0).asText();
                        if (!vae.isEmpty()) vaes.add(vae);
                    } else if ("CLIPLoader".equalsIgnoreCase(type)) {
                        String clip = widgets.get(0).asText();
                        if (!clip.isEmpty()) clips.add(clip);
                    } else if ("LoraLoader".equalsIgnoreCase(type)) {
                        String lora = widgets.get(0).asText();
                        if (!lora.isEmpty()) loras.add(lora);
                    }
                }
            }
        }
        
        for (String unet : unets) {
            String lower = unet.toLowerCase();
            if (lower.contains("wan") || lower.contains("ltx")) {
                isVideoBlueprint = true;
                break;
            }
        }
        
        if (isVideoBlueprint) {
            boolean isWan = false;
            for (String unet : unets) {
                if (unet.toLowerCase().contains("wan")) {
                    isWan = true;
                    break;
                }
            }
            if (!isWan) {
                for (String lora : loras) {
                    if (lora.toLowerCase().contains("wan")) {
                        isWan = true;
                        break;
                    }
                }
            }
            
            if (isWan) {
                String highUnet = null;
                String lowUnet = null;
                for (String u : unets) {
                    if (u.toLowerCase().contains("high")) {
                        highUnet = u;
                    } else if (u.toLowerCase().contains("low")) {
                        lowUnet = u;
                    } else if (highUnet == null) {
                        highUnet = u;
                    } else if (lowUnet == null) {
                        lowUnet = u;
                    }
                }
                
                String highLora = null;
                String lowLora = null;
                for (String l : loras) {
                    if (l.toLowerCase().contains("high")) {
                        highLora = l;
                    } else if (l.toLowerCase().contains("low")) {
                        lowLora = l;
                    } else if (highLora == null) {
                        highLora = l;
                    } else if (lowLora == null) {
                        lowLora = l;
                    }
                }
                
                String clip = clips.isEmpty() ? null : clips.get(0);
                String vae = vaes.isEmpty() ? null : vaes.get(0);
                
                if (highUnet != null) {
                    resolvedDefaultsMap.put("video_wan_high_unet", new ModelDefaults(highUnet, null, null, null, "ARCH_WAN"));
                }
                if (lowUnet != null) {
                    resolvedDefaultsMap.put("video_wan_low_unet", new ModelDefaults(lowUnet, null, null, null, "ARCH_WAN"));
                }
                if (highLora != null) {
                    resolvedDefaultsMap.put("video_wan_high_lora", new ModelDefaults(highLora, null, null, null, "ARCH_WAN"));
                }
                if (lowLora != null) {
                    resolvedDefaultsMap.put("video_wan_low_lora", new ModelDefaults(lowLora, null, null, null, "ARCH_WAN"));
                }
                if (clip != null) {
                    resolvedDefaultsMap.put("video_wan_clip", new ModelDefaults(clip, null, null, null, "ARCH_WAN"));
                }
                if (vae != null) {
                    resolvedDefaultsMap.put("video_wan_vae", new ModelDefaults(vae, null, null, null, "ARCH_WAN"));
                }
                
                logger.info("📹 [ArchitectureService] Extracted Wan video defaults: HighUnet={}, LowUnet={}, HighLora={}, LowLora={}, Clip={}, Vae={}", 
                        highUnet, lowUnet, highLora, lowLora, clip, vae);
            }
        }
    }

    private void extractValues(JsonNode node, List<String> modelNames, List<String> vaeNames, List<String> clipTypes, List<String> schedulers, List<String> samplerNames) {
        if (node == null) return;
        if (node.isTextual()) {
            String val = node.asText().trim();
            String lower = val.toLowerCase();
            if (lower.endsWith(".safetensors") || lower.endsWith(".sft") || lower.endsWith(".ckpt")) {
                if (lower.contains("vae") || lower.equals("ae.safetensors") || lower.endsWith("/ae.safetensors") || lower.endsWith("\\ae.safetensors")) {
                    vaeNames.add(val);
                } else if (lower.contains("clip") || lower.contains("t5") || lower.contains("encoder")) {
                    // Skip clip models
                } else {
                    modelNames.add(val);
                }
            } else {
                if (List.of("longcat_image", "wan", "flux", "sd3", "lumina2", "ltxv", "cosmos", "mochi", "stable_diffusion").contains(lower)) {
                    clipTypes.add(val);
                } else if (List.of("simple", "normal", "karras", "exponential", "sgm_uniform", "ddim_uniform").contains(lower)) {
                    schedulers.add(val);
                } else if (List.of("euler", "euler_ancestral", "heun", "dpm_2", "uni_pc", "ddim", "ipndf", "deis").contains(lower)) {
                    samplerNames.add(val);
                }
            }
        } else if (node.isContainerNode()) {
            for (JsonNode child : node) {
                extractValues(child, modelNames, vaeNames, clipTypes, schedulers, samplerNames);
            }
        }
    }

    private void saveResolvedDefaultsToFile() {
        File targetFile = new File(configService.getAppDataPath(), "templates/comfyui/model_resolved_defaults.json");
        try {
            File parentDir = targetFile.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }
            ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter().writeValue(targetFile, resolvedDefaultsMap);
            logger.info("💾 [ArchitectureService] Wrote blueprint resolved model defaults to: " + targetFile.getAbsolutePath());
        } catch (Exception e) {
            logger.error("❌ [ArchitectureService] Failed to write model resolved defaults: " + e.getMessage());
        }
    }

    private void loadResolvedDefaultsFromFile() {
        File targetFile = new File(configService.getAppDataPath(), "templates/comfyui/model_resolved_defaults.json");
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
            logger.info("📂 [ArchitectureService] Loaded " + resolvedDefaultsMap.size() + " resolved model defaults.");
        } catch (Exception e) {
            logger.error("❌ [ArchitectureService] Failed to load model resolved defaults: " + e.getMessage());
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

    private ModelDefaults getStandardDefaultsForArchitecture(ModelArchitecture arch) {
        if (arch == null) arch = ModelArchitecture.ARCH_UNKNOWN;
        switch (arch) {
            case ARCH_FLUX:
                return new ModelDefaults("FLUX1/ae.safetensors", "flux", "normal", "euler", arch.name());
            case ARCH_SDXL:
                return new ModelDefaults("sdxl_vae.safetensors", "stable_diffusion", "normal", "euler", arch.name());
            case ARCH_SD3:
                return new ModelDefaults("sd3_vae.safetensors", "sd3", "normal", "euler", arch.name());
            case ARCH_LUMINA2:
                return new ModelDefaults("ae.safetensors", "longcat_image", "simple", "euler", arch.name());
            case ARCH_WAN:
                return new ModelDefaults("wan_2.1_vae.safetensors", "wan", "normal", "euler", arch.name());
            case ARCH_HUNYUAN:
                return new ModelDefaults("hunyuan_vae.safetensors", "hunyuan", "normal", "euler", arch.name());
            case ARCH_SD15:
            default:
                return new ModelDefaults("vae-ft-mse-840000-ema-pruned.safetensors", "stable_diffusion", "normal", "euler", arch.name());
        }
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
        File targetFile = new File(configService.getAppDataPath(), "templates/comfyui/model_architectures.json");
        try {
            File parentDir = targetFile.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }
            ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter().writeValue(targetFile, architectureCache);
            logger.info("💾 [ModelArchitectureService] Wrote architecture cache to: " + targetFile.getAbsolutePath());
        } catch (Exception e) {
            logger.error("❌ [ModelArchitectureService] Failed to write architecture cache: " + e.getMessage());
        }
    }

    private void loadArchitectureCacheFromFile() {
        File targetFile = new File(configService.getAppDataPath(), "templates/comfyui/model_architectures.json");
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
                    logger.error("⚠️ [ModelArchitectureService] Unknown architecture in cache file: " + val);
                }
            }
            logger.info("📂 [ModelArchitectureService] Loaded " + architectureCache.size() + " cached model architectures.");
        } catch (Exception e) {
            logger.error("❌ [ModelArchitectureService] Failed to load architecture cache: " + e.getMessage());
        }
    }

    private void parseSubtypesFromIndex(JsonNode node, Map<String, String> map) {
        if (node == null) return;
        if (node.isArray()) {
            for (JsonNode child : node) {
                parseSubtypesFromIndex(child, map);
            }
        } else if (node.isObject()) {
            if (node.has("templates") && node.get("templates").isArray()) {
                parseSubtypesFromIndex(node.get("templates"), map);
            }
            if (node.has("name")) {
                String name = node.path("name").asText("");
                String mediaSubtype = node.path("mediaSubtype").asText("");
                if (!name.isEmpty() && !mediaSubtype.isEmpty()) {
                    map.put(name.toLowerCase().trim(), mediaSubtype.toLowerCase().trim());
                }
            }
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (!field.getKey().equals("templates")) {
                    parseSubtypesFromIndex(field.getValue(), map);
                }
            }
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
        File targetFile = new File(configService.getAppDataPath(), "templates/comfyui/blueprint_scan_results.json");
        if (!targetFile.exists()) {
            return;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(targetFile);
            List<Map<String, Object>> list = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode node : root) {
                    Map<String, Object> entry = mapper.convertValue(node, Map.class);
                    list.add(entry);
                }
            }
            blueprintScanResults.clear();
            blueprintScanResults.addAll(list);
            logger.info("📂 [ModelArchitectureService] Loaded " + blueprintScanResults.size() + " scanned blueprint results from cache.");
        } catch (Exception e) {
            logger.error("❌ [ModelArchitectureService] Failed to load scanned blueprint results: " + e.getMessage());
        }
    }

    private static class ServerTemplateInfo {
        final String name;
        final String title;
        final String description;
        final String category;

        ServerTemplateInfo(String name, String title, String description, String category) {
            this.name = name;
            this.title = title;
            this.description = description;
            this.category = category;
        }
    }

    private void flattenTemplates(JsonNode node, List<ServerTemplateInfo> list, String currentCategory, String parentTrail) {
        if (node == null) return;
        if (node.isArray()) {
            for (JsonNode child : node) {
                flattenTemplates(child, list, currentCategory, parentTrail);
            }
        } else if (node.isObject()) {
            String groupTitle = node.path("title").asText("");
            JsonNode templatesNode = node.path("templates");
            
            if (templatesNode.isArray()) {
                String nextCategory = currentCategory.isEmpty() ? groupTitle : currentCategory;
                String nextTrail = parentTrail.isEmpty() ? groupTitle : parentTrail + " / " + groupTitle;
                flattenTemplates(templatesNode, list, nextCategory, nextTrail);
                return;
            }

            if (node.has("name") && (node.has("title") || node.has("description") || node.has("mediaType"))) {
                String name = node.path("name").asText("");
                String title = node.path("title").asText(name);
                String description = node.path("description").asText("");
                
                String category = currentCategory;
                if (category.isEmpty()) {
                    category = parentTrail;
                }
                if (category.isEmpty()) {
                    category = "Uncategorized";
                }
                
                list.add(new ServerTemplateInfo(name, title, description, category));
            }
            
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (!field.getKey().equals("templates")) {
                    flattenTemplates(field.getValue(), list, currentCategory, parentTrail);
                }
            }
        }
    }

    private void discoverServerBlueprints(List<Map<String, Object>> scanList, Map<String, String> mediaSubtypes, String comfyUrl, ObjectMapper mapper) {
        if (comfyUrl == null || comfyUrl.isEmpty()) return;

        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(3))
                    .build();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(comfyUrl + "/templates/index.json"))
                    .timeout(java.time.Duration.ofSeconds(5))
                    .GET().build();

            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = mapper.readTree(response.body());
                List<ServerTemplateInfo> templates = new ArrayList<>();
                flattenTemplates(root, templates, "", "");
                
                for (ServerTemplateInfo t : templates) {
                    if (t == null || t.name == null || t.name.isEmpty()) continue;
                    try {
                        String jsonFilename = t.name + ".json";
                        boolean existsLocally = false;
                        for (Map<String, Object> entry : scanList) {
                            String fName = (String) entry.get("filename");
                            if (fName != null && fName.equalsIgnoreCase(jsonFilename)) {
                                existsLocally = true;
                                break;
                            }
                        }
                        if (existsLocally) continue;

                        String workflowUrl = comfyUrl + "/templates/" + java.net.URLEncoder.encode(t.name + ".json", StandardCharsets.UTF_8).replace("+", "%20");
                        java.net.http.HttpRequest wfRequest = java.net.http.HttpRequest.newBuilder()
                                .uri(java.net.URI.create(workflowUrl))
                                .timeout(java.time.Duration.ofSeconds(5))
                                .GET().build();
                        java.net.http.HttpResponse<String> wfResponse = client.send(wfRequest, java.net.http.HttpResponse.BodyHandlers.ofString());
                        if (wfResponse.statusCode() == 200) {
                            String rawJson = wfResponse.body();
                            List<ModelInfo> requiredModels;
                            try {
                                requiredModels = getModelAnalyzer().analyze(rawJson, t.name + ".json");
                            } catch (Exception e) {
                                requiredModels = Collections.emptyList();
                            }
                            
                            String mediaSubtypeIdx = mediaSubtypes.get(t.name.toLowerCase().trim());
                            String previewPath = "";
                            String mediaType = "image";
                            String mediaSubtype = "";
                            if (mediaSubtypeIdx != null && !mediaSubtypeIdx.isEmpty()) {
                                mediaSubtype = mediaSubtypeIdx;
                                if (mediaSubtype.equals("mp4") || mediaSubtype.equals("webm") || mediaSubtype.equals("mov")) {
                                    mediaType = "video";
                                }
                                previewPath = comfyUrl + "/templates/" + java.net.URLEncoder.encode(t.name + "-1." + mediaSubtype, StandardCharsets.UTF_8).replace("+", "%20");
                            }

                            Map<String, Object> blueprintMap = new java.util.HashMap<>();
                            blueprintMap.put("name", t.title);
                            blueprintMap.put("filename", jsonFilename);
                            blueprintMap.put("filePath", "remote:" + t.name);
                            blueprintMap.put("category", t.category);
                            blueprintMap.put("description", t.description);
                            blueprintMap.put("mediaType", mediaType);
                            blueprintMap.put("mediaSubtype", mediaSubtype);
                            blueprintMap.put("previewPath", previewPath);
                            blueprintMap.put("requiredModels", requiredModels);
                            scanList.add(blueprintMap);

                            extractDefaultsFromWorkflow(rawJson, jsonFilename, mapper);
                        }
                    } catch (Exception ex) {
                        logger.error("⚠️ [ModelArchitectureService] Failed to fetch server workflow for " + t.name + ": " + ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("⚠️ [ModelArchitectureService] Failed server blueprint discovery: " + e.getMessage());
        }
    }

    private void extractDefaultsFromWorkflow(String rawJson, String filename, ObjectMapper mapper) {
        try {
            JsonNode root = mapper.readTree(rawJson);
            extractVideoModels(root);
            List<String> modelNames = new ArrayList<>();
            List<String> vaeNames = new ArrayList<>();
            List<String> clipTypes = new ArrayList<>();
            List<String> schedulers = new ArrayList<>();
            List<String> samplerNames = new ArrayList<>();

            extractValues(root, modelNames, vaeNames, clipTypes, schedulers, samplerNames);

            for (String modelName : modelNames) {
                String fName = new File(modelName).getName();
                String cleanModelName = fName.toLowerCase().trim();

                String vaeName = vaeNames.isEmpty() ? null : vaeNames.get(0);
                String clipType = clipTypes.isEmpty() ? null : clipTypes.get(0);
                String scheduler = schedulers.isEmpty() ? null : schedulers.get(0);
                String samplerName = samplerNames.isEmpty() ? null : samplerNames.get(0);

                ModelArchitecture arch = detectArchitecture(fName);

                ModelDefaults archDefaults = getStandardDefaultsForArchitecture(arch);
                if (vaeName == null) vaeName = archDefaults.vaeName;
                if (clipType == null) clipType = archDefaults.clipType;
                if (scheduler == null) scheduler = archDefaults.scheduler;
                if (samplerName == null) samplerName = archDefaults.samplerName;

                ModelDefaults defaults = new ModelDefaults(vaeName, clipType, scheduler, samplerName, arch.name());
                resolvedDefaultsMap.put(cleanModelName, defaults);
            }
        } catch (Exception e) {
            logger.error("⚠️ [ModelArchitectureService] Failed to extract defaults from workflow " + filename + ": " + e.getMessage());
        }
    }
}
