package de.tki.comfymodels.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfymodels.domain.ComfyRegistryWorkflow;
import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.IComfyRegistryClient;
import de.tki.comfymodels.service.ILocalModelValidator;
import de.tki.comfymodels.service.IModelAnalyzer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.stream.Stream;

@Service
public class LocalModelValidator implements ILocalModelValidator {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LocalModelValidator.class);

    private final ConfigService configService;
    private final IComfyRegistryClient registryClient;
    private final LocalModelScanner localModelScanner;
    private final IModelAnalyzer modelAnalyzer;
    private final ObjectMapper objectMapper;

    // Fast lookup set containing both full filenames and basenames (lowercase, trimmed)
    private final Set<String> localModelNames = ConcurrentHashMap.newKeySet();
    private final Set<String> activeLocalModelNames = ConcurrentHashMap.newKeySet();
    private final Set<String> archivedModelNames = ConcurrentHashMap.newKeySet();

    private final Map<String, List<ModelInfo>> registryModelsCache = new ConcurrentHashMap<>();
    private final Map<String, Boolean> cloudOnlyCache = new ConcurrentHashMap<>();
    private final Semaphore downloadSemaphore = new Semaphore(2);
    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "WorkflowDownloadValidator");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private File cacheFile;

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "safetensors", "sft", "ckpt", "bin", "pt", "pth", "onnx"
    );

    public LocalModelValidator(ConfigService configService,
                               IComfyRegistryClient registryClient,
                               LocalModelScanner localModelScanner,
                               IModelAnalyzer modelAnalyzer) {
        this(configService, registryClient, localModelScanner, modelAnalyzer, null);
    }

    @Autowired
    public LocalModelValidator(ConfigService configService,
                               IComfyRegistryClient registryClient,
                               LocalModelScanner localModelScanner,
                               IModelAnalyzer modelAnalyzer,
                               @Autowired(required = false) de.tki.comfymodels.service.IDefaultCacheBootstrapper cacheBootstrapper) {
        this.configService = configService;
        this.registryClient = registryClient;
        this.localModelScanner = localModelScanner;
        this.modelAnalyzer = modelAnalyzer;
        this.objectMapper = new ObjectMapper();
        if (cacheBootstrapper != null) {
            cacheBootstrapper.bootstrapDefaultCache();
        }
        initializeCache();
    }

    private void initializeCache() {
        try {
            String appDir = (configService != null && configService.getAppDataPath() != null)
                    ? configService.getAppDataPath()
                    : (System.getProperty("user.home") + File.separator + ".comfyui-companion");
            File dir = new File(appDir, "templates/comfyui");
            if (!dir.exists()) dir.mkdirs();
            cacheFile = new File(dir, "registry_models_cache.json");

            File cloudCacheFile = new File(dir, "cloud_only_cache.json");
            if (cloudCacheFile.exists()) {
                JsonNode cloudRoot = objectMapper.readTree(cloudCacheFile);
                if (cloudRoot.isObject()) {
                    cloudRoot.fields().forEachRemaining(entry -> {
                        cloudOnlyCache.put(entry.getKey(), entry.getValue().asBoolean());
                    });
                }
            }

            if (cacheFile.exists()) {
                JsonNode root = objectMapper.readTree(cacheFile);
                if (root.isObject()) {
                    root.fields().forEachRemaining(entry -> {
                        List<ModelInfo> list = new ArrayList<>();
                        if (entry.getValue().isArray()) {
                            entry.getValue().forEach(node -> {
                                try {
                                    if (node.isObject()) {
                                        list.add(objectMapper.treeToValue(node, ModelInfo.class));
                                    } else {
                                        ModelInfo info = new ModelInfo();
                                        info.setName(node.asText());
                                        info.setType(inferTypeFromFilename(node.asText()));
                                        list.add(info);
                                    }
                                } catch (Exception ignored) {}
                            });
                        }
                        if (!list.isEmpty() || cloudOnlyCache.getOrDefault(entry.getKey(), false)) {
                            registryModelsCache.put(entry.getKey(), list);
                        }
                    });
                }
            }

            // Also preload from blueprint_scan_results.json if present
            File scanResultsFile = new File(dir, "blueprint_scan_results.json");
            if (scanResultsFile.exists()) {
                JsonNode scanRoot = objectMapper.readTree(scanResultsFile);
                if (scanRoot.isArray()) {
                    for (JsonNode scanNode : scanRoot) {
                        String scanName = scanNode.path("name").asText("");
                        String scanFilename = scanNode.path("filename").asText("");
                        JsonNode reqNode = scanNode.path("requiredModels");
                        if (reqNode.isArray() && reqNode.size() > 0) {
                            List<ModelInfo> list = new ArrayList<>();
                            reqNode.forEach(mNode -> {
                                try {
                                    if (mNode.isObject()) {
                                        list.add(objectMapper.treeToValue(mNode, ModelInfo.class));
                                    }
                                } catch (Exception ignored) {}
                            });
                            if (!list.isEmpty()) {
                                if (!scanName.isEmpty()) registryModelsCache.putIfAbsent(scanName, list);
                                if (!scanFilename.isEmpty()) {
                                    registryModelsCache.putIfAbsent(scanFilename, list);
                                    if (scanFilename.endsWith(".json")) {
                                        registryModelsCache.putIfAbsent(scanFilename.substring(0, scanFilename.length() - 5), list);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            logger.info("ℹ️ [LocalModelValidator] Loaded " + registryModelsCache.size() + " cached registry models from disk.");
        } catch (Exception e) {
            logger.error("⚠️ [LocalModelValidator] Failed to initialize cache: " + e.getMessage());
        }
    }

    private static String inferTypeFromFilename(String filename) {
        if (filename == null) return "checkpoints";
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.contains("vae") || lower.contains("ae.safetensors") || lower.startsWith("ae")) return "vae";
        if (lower.contains("clip") || lower.contains("t5") || lower.contains("qwen") || lower.contains("encoder") || lower.contains("gemma") || lower.contains("llama") || lower.contains("text_encoder") || lower.contains("textencoder")) return "text_encoders";
        if (lower.contains("unet") || lower.contains("diffusion") || lower.contains("turbo") || lower.contains("transformer") || lower.contains("dit")) return "diffusion_models";
        if (lower.contains("lora")) return "loras";
        if (lower.contains("controlnet") || lower.contains("control")) return "controlnet";
        return "checkpoints";
    }

    private synchronized void saveCache() {
        if (cacheFile == null) return;
        try {
            objectMapper.writeValue(cacheFile, registryModelsCache);
            File cloudCacheFile = new File(cacheFile.getParentFile(), "cloud_only_cache.json");
            objectMapper.writeValue(cloudCacheFile, cloudOnlyCache);
        } catch (Exception e) {
            logger.error("⚠️ [LocalModelValidator] Failed to save cache: " + e.getMessage());
        }
    }

    @Override
    public void scanLocalModels() {
        Set<String> tempNames = ConcurrentHashMap.newKeySet();
        Set<String> tempActive = ConcurrentHashMap.newKeySet();
        Set<String> tempArchived = ConcurrentHashMap.newKeySet();
        
        if (localModelScanner == null) {
            localModelNames.clear();
            activeLocalModelNames.clear();
            archivedModelNames.clear();
            return;
        }

        List<ModelInfo> localModels = localModelScanner.scanLocalModels();
        for (ModelInfo info : localModels) {
            String name = info.getName();
            if (name != null) {
                String lowerName = name.toLowerCase(Locale.ROOT).trim();
                tempNames.add(lowerName); // Full name: e.g. "flux1-dev-fp8.safetensors"
                tempNames.add(baseName(lowerName)); // Base name: e.g. "flux1-dev-fp8"
                tempActive.add(lowerName);
                tempActive.add(baseName(lowerName));
            }
        }

        // Scan Archive Directory and add archived models
        String archivePathStr = configService.getArchivePath();
        if (archivePathStr != null && !archivePathStr.trim().isEmpty()) {
            try {
                Path archiveRoot = Paths.get(archivePathStr).toAbsolutePath().normalize();
                if (Files.exists(archiveRoot) && Files.isDirectory(archiveRoot)) {
                    try (Stream<Path> walk = Files.walk(archiveRoot)) {
                        walk.filter(Files::isRegularFile)
                            .filter(p -> {
                                String n = p.getFileName().toString().toLowerCase();
                                return n.endsWith(".safetensors") || n.endsWith(".sft") || n.endsWith(".ckpt") || 
                                       n.endsWith(".pth") || n.endsWith(".pt") || n.endsWith(".bin") || n.endsWith(".onnx");
                            })
                            .forEach(p -> {
                                String name = p.getFileName().toString();
                                String lowerName = name.toLowerCase(Locale.ROOT).trim();
                                tempNames.add(lowerName);
                                tempNames.add(baseName(lowerName));
                                tempArchived.add(lowerName);
                                tempArchived.add(baseName(lowerName));
                            });
                    }
                }
            } catch (Exception e) {
                logger.error("⚠️ [LocalModelValidator] Error scanning archive: " + e.getMessage());
            }
        }

        localModelNames.clear();
        localModelNames.addAll(tempNames);
        activeLocalModelNames.clear();
        activeLocalModelNames.addAll(tempActive);
        archivedModelNames.clear();
        archivedModelNames.addAll(tempArchived);
    }

    public boolean isModelActive(String modelName) {
        if (modelName == null || modelName.isBlank()) return false;
        String lower = modelName.toLowerCase(Locale.ROOT).trim();
        return activeLocalModelNames.contains(lower) || activeLocalModelNames.contains(baseName(lower));
    }

    public boolean isModelArchived(String modelName) {
        if (modelName == null || modelName.isBlank()) return false;
        String lower = modelName.toLowerCase(Locale.ROOT).trim();
        return archivedModelNames.contains(lower) || archivedModelNames.contains(baseName(lower));
    }

    @Override
    public CompletableFuture<Void> validateWorkflowModelsAsync(ComfyRegistryWorkflow workflow) {
        // Ensure local models are scanned at least once
        if (localModelNames.isEmpty()) {
            scanLocalModels();
        }

        String wfId = workflow.getId();
        if (wfId != null && registryModelsCache.containsKey(wfId)) {
            List<ModelInfo> cached = registryModelsCache.get(wfId);
            boolean isCloud = cloudOnlyCache.getOrDefault(wfId, false);
            if ((cached != null && !cached.isEmpty()) || isCloud) {
                workflow.setRequiredModelInfos(cached != null ? cached : new ArrayList<>());
                workflow.setCloudOnly(isCloud);
                List<String> names = new ArrayList<>();
                if (cached != null) {
                    for (ModelInfo info : cached) {
                        if (info.getName() != null) names.add(info.getName());
                    }
                }
                workflow.setRequiredModels(names);
                boolean allPresent = checkModelsPresent(names);
                workflow.setHasAllModelsLocal(allPresent);
                return CompletableFuture.completedFuture(null);
            }
        }

        // Weg A: Check if required models are directly specified in DTO and have actual model file extensions
        if (workflow.getRequiredModels() != null && !workflow.getRequiredModels().isEmpty() && hasActualModelFiles(workflow.getRequiredModels())) {
            List<ModelInfo> infos = new ArrayList<>();
            for (String name : workflow.getRequiredModels()) {
                ModelInfo info = new ModelInfo();
                info.setName(name);
                info.setType(inferTypeFromFilename(name));
                infos.add(info);
            }
            workflow.setRequiredModelInfos(infos);
            boolean allPresent = checkModelsPresent(workflow.getRequiredModels());
            workflow.setHasAllModelsLocal(allPresent);
            if (wfId != null) {
                registryModelsCache.put(wfId, infos);
                saveCache();
            }
            return CompletableFuture.completedFuture(null);
        }

        // Weg B: Fallback - Download and parse the workflow JSON
        String downloadUrl = workflow.getJsonDownloadUrl();
        if (downloadUrl == null || downloadUrl.isEmpty() || downloadUrl.startsWith("mock:")) {
            // No download URL or mocked URL; do a mock check or default to false
            workflow.setHasAllModelsLocal(false);
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                downloadSemaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            return null;
        }, downloadExecutor)
        .thenCompose(v -> registryClient.downloadFileAsync(downloadUrl))
        .whenComplete((res, ex) -> downloadSemaphore.release())
        .thenAccept(bytes -> {
            try {
                String jsonText = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                List<ModelInfo> analyzedInfo = modelAnalyzer.analyze(jsonText, workflow.getTitle() + ".json");
                workflow.setRequiredModelInfos(analyzedInfo);
                
                boolean isCloud = checkCloudOnly(jsonText, analyzedInfo);
                workflow.setCloudOnly(isCloud);
                
                List<String> parsedModels = new ArrayList<>();
                for (ModelInfo info : analyzedInfo) {
                    if (info.getName() != null && !info.getName().isBlank()) {
                        parsedModels.add(info.getName());
                    }
                }
                // Save the extracted models to the workflow DTO for future references
                workflow.setRequiredModels(parsedModels);
                
                if (wfId != null) {
                    registryModelsCache.put(wfId, analyzedInfo);
                    cloudOnlyCache.put(wfId, isCloud);
                    saveCache();
                }
                
                boolean allPresent = checkModelsPresent(parsedModels);
                workflow.setHasAllModelsLocal(allPresent);
            } catch (Exception e) {
                logger.error("❌ [LocalModelValidator] Error parsing downloaded workflow JSON: " + e.getMessage());
                workflow.setHasAllModelsLocal(false);
            }
        })
        .exceptionally(ex -> {
            if (!(ex.getCause() instanceof InterruptedException)) {
                logger.error("❌ [LocalModelValidator] Failed to validate models from JSON download for: " + workflow.getTitle());
            }
            workflow.setHasAllModelsLocal(false);
            return null;
        });
    }

    @Override
    public Set<String> getLocalModelBaseNames() {
        return localModelNames;
    }

    @Override
    public Set<String> getActiveLocalModelNames() {
        return activeLocalModelNames;
    }

    @Override
    public List<ModelInfo> getCachedModelsForWorkflow(String idOrTitle) {
        if (idOrTitle == null || idOrTitle.isBlank()) return Collections.emptyList();
        List<ModelInfo> list = registryModelsCache.get(idOrTitle);
        if (list != null && !list.isEmpty()) return list;
        list = registryModelsCache.get(idOrTitle.toLowerCase(Locale.ROOT));
        if (list != null && !list.isEmpty()) return list;
        return Collections.emptyList();
    }

    private boolean checkModelsPresent(List<String> requiredModels) {
        if (requiredModels == null || requiredModels.isEmpty()) {
            return true;
        }
        for (String reqModel : requiredModels) {
            if (isSupportedModelFile(reqModel)) {
                String cleanReq = reqModel.toLowerCase(Locale.ROOT).trim();
                // Check both full match and base name match strictly in active local models
                if (!activeLocalModelNames.contains(cleanReq) && !activeLocalModelNames.contains(baseName(cleanReq))) {
                    return false;
                }
            } else {
                if (!isCloudApi(reqModel)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isCloudApi(String reqName) {
        if (reqName == null || reqName.isBlank()) return false;
        String clean = reqName.toLowerCase(Locale.ROOT).trim();
        return clean.equals("openai") || clean.equals("dall-e") || clean.equals("dalle") ||
               clean.equals("elevenlabs") || clean.equals("gemini") || clean.equals("anthropic") ||
               clean.equals("claude") || clean.equals("openrouter");
    }

    private boolean hasActualModelFiles(List<String> models) {
        if (models == null || models.isEmpty()) return false;
        for (String m : models) {
            if (isSupportedModelFile(m)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSupportedModelFile(String fileName) {
        if (fileName == null) return false;
        int dotIdx = fileName.lastIndexOf('.');
        if (dotIdx <= 0 || dotIdx == fileName.length() - 1) return false;
        String ext = fileName.substring(dotIdx + 1).toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS.contains(ext);
    }

    private String baseName(String name) {
        if (name == null) return "";
        String s = name.replace('\\', '/');
        int slash = s.lastIndexOf('/');
        if (slash >= 0) s = s.substring(slash + 1);
        int dot = s.lastIndexOf('.');
        if (dot >= 0) s = s.substring(0, dot);
        return s.toLowerCase(Locale.ROOT).trim();
    }

    public static boolean checkCloudOnly(String jsonText, List<ModelInfo> requiredModels) {
        if (jsonText == null || jsonText.trim().isEmpty()) {
            return false;
        }
        
        String lowerJson = jsonText.toLowerCase(Locale.ROOT);

        // 1. Cloud-specific Node Names
        boolean hasCloudNodes = lowerJson.contains("openai") || lowerJson.contains("gemini") 
                || lowerJson.contains("claude") || lowerJson.contains("anthropic") 
                || lowerJson.contains("midjourney") || lowerJson.contains("replicate") 
                || lowerJson.contains("runpod") || lowerJson.contains("chatgpt")
                || lowerJson.contains("geminiapi") || lowerJson.contains("openaisampler")
                || lowerJson.contains("replicatesampler");

        // 2. Request for API Keys, Secrets, or Tokens
        boolean requiresApiKey = false;
        java.util.regex.Pattern keyPattern = java.util.regex.Pattern.compile(
            "\"(api[-_\\s]?key|secret[-_\\s]?key|token|auth[-_\\s]?token|access[-_\\s]?token)\"", 
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        if (keyPattern.matcher(lowerJson).find()) {
            requiresApiKey = true;
        }

        // 3. Model designations vs File Extensions
        boolean hasLocalModelFiles = false;
        if (requiredModels != null && !requiredModels.isEmpty()) {
            for (ModelInfo info : requiredModels) {
                if (info.getName() != null) {
                    String nameLower = info.getName().toLowerCase(Locale.ROOT);
                    if (nameLower.endsWith(".safetensors") || nameLower.endsWith(".sft") 
                            || nameLower.endsWith(".ckpt") || nameLower.endsWith(".pth") 
                            || nameLower.endsWith(".pt") || nameLower.endsWith(".bin") 
                            || nameLower.endsWith(".onnx")) {
                        hasLocalModelFiles = true;
                        break;
                    }
                }
            }
        }

        // 4. Missing standard local processing nodes
        boolean hasLocalNodes = lowerJson.contains("\"ksampler\"") 
                || lowerJson.contains("\"checkpointloadersimple\"") 
                || lowerJson.contains("\"checkpointloader\"") 
                || lowerJson.contains("\"vaedecode\"")
                || lowerJson.contains("\"cliptextencode\"");

        return hasCloudNodes || requiresApiKey || (!hasLocalModelFiles && !hasLocalNodes);
    }
}
