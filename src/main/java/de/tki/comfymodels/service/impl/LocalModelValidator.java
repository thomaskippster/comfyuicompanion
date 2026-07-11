package de.tki.comfymodels.service.impl;

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

    private final ConfigService configService;
    private final IComfyRegistryClient registryClient;
    private final LocalModelScanner localModelScanner;
    private final IModelAnalyzer modelAnalyzer;
    private final ObjectMapper objectMapper;

    // Fast lookup set containing both full filenames and basenames (lowercase, trimmed)
    private final Set<String> localModelNames = ConcurrentHashMap.newKeySet();

    private final Map<String, List<ModelInfo>> registryModelsCache = new ConcurrentHashMap<>();
    private final Map<String, Boolean> cloudOnlyCache = new ConcurrentHashMap<>();
    private final Semaphore downloadSemaphore = new Semaphore(3);
    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(3);
    private File cacheFile;

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "safetensors", "sft", "ckpt", "bin", "pt", "pth", "onnx"
    );

    @Autowired
    public LocalModelValidator(ConfigService configService,
                               IComfyRegistryClient registryClient,
                               LocalModelScanner localModelScanner,
                               IModelAnalyzer modelAnalyzer) {
        this.configService = configService;
        this.registryClient = registryClient;
        this.localModelScanner = localModelScanner;
        this.modelAnalyzer = modelAnalyzer;
        this.objectMapper = new ObjectMapper();
        initializeCache();
    }

    private void initializeCache() {
        try {
            String appDir = System.getProperty("user.home") + File.separator + ".gemini" + File.separator + "antigravity-cli";
            File dir = new File(appDir);
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
                System.out.println("ℹ️ [LocalModelValidator] Loaded " + registryModelsCache.size() + " cached registry models from disk.");
            }
        } catch (Exception e) {
            System.err.println("⚠️ [LocalModelValidator] Failed to initialize cache: " + e.getMessage());
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
            System.err.println("⚠️ [LocalModelValidator] Failed to save cache: " + e.getMessage());
        }
    }

    @Override
    public void scanLocalModels() {
        Set<String> tempNames = ConcurrentHashMap.newKeySet();
        
        if (localModelScanner == null) {
            addDummyLocalModelsToSet(tempNames);
            localModelNames.clear();
            localModelNames.addAll(tempNames);
            return;
        }

        List<ModelInfo> localModels = localModelScanner.scanLocalModels();
        for (ModelInfo info : localModels) {
            String name = info.getName();
            if (name != null) {
                String lowerName = name.toLowerCase(Locale.ROOT).trim();
                tempNames.add(lowerName); // Full name: e.g. "flux1-dev-fp8.safetensors"
                tempNames.add(baseName(lowerName)); // Base name: e.g. "flux1-dev-fp8"
            }
        }

        // Scan Archive Directory and add archived models as present/local
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
                            });
                    }
                }
            } catch (Exception e) {
                System.err.println("⚠️ [LocalModelValidator] Error scanning archive: " + e.getMessage());
            }
        }

        // If no local models found, add dummies so that offline/local validation can still be demonstrated
        if (tempNames.isEmpty()) {
            addDummyLocalModelsToSet(tempNames);
        }

        localModelNames.clear();
        localModelNames.addAll(tempNames);
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
                System.err.println("❌ [LocalModelValidator] Error parsing downloaded workflow JSON: " + e.getMessage());
                workflow.setHasAllModelsLocal(false);
            }
        })
        .exceptionally(ex -> {
            if (!(ex.getCause() instanceof InterruptedException)) {
                System.err.println("❌ [LocalModelValidator] Failed to validate models from JSON download for: " + workflow.getTitle());
            }
            workflow.setHasAllModelsLocal(false);
            return null;
        });
    }

    @Override
    public Set<String> getLocalModelBaseNames() {
        return localModelNames;
    }

    private boolean checkModelsPresent(List<String> requiredModels) {
        if (requiredModels == null || requiredModels.isEmpty()) {
            return true;
        }
        for (String reqModel : requiredModels) {
            if (isSupportedModelFile(reqModel)) {
                String cleanReq = reqModel.toLowerCase(Locale.ROOT).trim();
                // Check both full match and base name match
                if (!localModelNames.contains(cleanReq) && !localModelNames.contains(baseName(cleanReq))) {
                    return false;
                }
            } else {
                if (!checkHighLevelModelPresent(reqModel)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean checkHighLevelModelPresent(String reqModel) {
        if (reqModel == null || reqModel.isBlank()) return true;
        String clean = reqModel.toLowerCase(Locale.ROOT).trim();
        
        // Skip API-based / closed source models as they don't require local files
        if (clean.contains("api") || clean.contains("seedance") || clean.contains("elevenlabs") ||
            clean.contains("openai") || clean.contains("dall-e") || clean.contains("gpt-image") ||
            clean.contains("luma") || clean.contains("kling") || clean.contains("runway") ||
            clean.contains("vidu") || clean.contains("minimax") || clean.contains("happyhorse") ||
            clean.contains("dream") || clean.contains("sonilo") || clean.contains("sustain") ||
            clean.contains("midjourney") || clean.contains("google") || clean.contains("gemini") ||
            clean.contains("anthropic") || clean.contains("claude") || clean.contains("openrouter")) {
            return true;
        }

        // Fuzzy matching logic for open-source model families:
        if (clean.contains("flux")) {
            for (String local : localModelNames) {
                if (local.contains("flux")) return true;
            }
            return false;
        }
        if (clean.contains("sdxl")) {
            for (String local : localModelNames) {
                if (local.contains("sdxl")) return true;
            }
            return false;
        }
        if (clean.contains("sd3") || clean.contains("stable diffusion 3")) {
            for (String local : localModelNames) {
                if (local.contains("sd3") || local.contains("sd_3")) return true;
            }
            return false;
        }
        if (clean.contains("sd1.5") || clean.contains("sd 1.5") || clean.contains("sd15")) {
            for (String local : localModelNames) {
                if (local.contains("sd15") || local.contains("sd1.5") || local.contains("v1-5")) return true;
            }
            return false;
        }
        if (clean.contains("wan")) {
            for (String local : localModelNames) {
                if (local.contains("wan")) return true;
            }
            return false;
        }
        if (clean.contains("hunyuan")) {
            for (String local : localModelNames) {
                if (local.contains("hunyuan")) return true;
            }
            return false;
        }
        if (clean.contains("qwen")) {
            for (String local : localModelNames) {
                if (local.contains("qwen")) return true;
            }
            return false;
        }
        if (clean.contains("ltx")) {
            for (String local : localModelNames) {
                if (local.contains("ltx")) return true;
            }
            return false;
        }
        if (clean.equals("vae") || clean.equals("ae") || clean.contains("autoencoder")) {
            for (String local : localModelNames) {
                if (local.contains("vae") || local.contains("ae") || local.contains("autoencoder")) return true;
            }
            return false;
        }
        if (clean.contains("lora")) {
            for (String local : localModelNames) {
                if (local.contains("lora")) return true;
            }
            return false;
        }
        if (clean.contains("clip") || clean.contains("t5") || clean.contains("encoder") || clean.contains("text_encoder")) {
            for (String local : localModelNames) {
                if (local.contains("clip") || local.contains("t5") || local.contains("encoder") || local.contains("text_encoder")) return true;
            }
            return false;
        }
        if (clean.contains("unet") || clean.contains("diffusion")) {
            for (String local : localModelNames) {
                if (local.contains("unet") || local.contains("diffusion")) return true;
            }
            return false;
        }

        // General fallback
        String[] parts = clean.split("[\\s\\-\\.\\_\\/]+");
        if (parts.length > 0) {
            String bestPart = "";
            for (String part : parts) {
                if (part.length() > bestPart.length() && !part.equals("model") && !part.equals("text") && !part.equals("image") && !part.equals("edit") && !part.equals("generation")) {
                    bestPart = part;
                }
            }
            if (bestPart.length() >= 3) {
                for (String local : localModelNames) {
                    if (local.contains(bestPart)) return true;
                }
            }
        }
        return false;
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

    /**
     * Seeds dummy model names for local testing.
     */
    private void addDummyLocalModelsToSet(Set<String> set) {
        set.add("flux1-dev-fp8.safetensors");
        set.add("flux1-dev-fp8");
        set.add("ae.safetensors");
        set.add("ae");
        // We leave sdxl_lightning_4step.safetensors out to simulate a missing model
    }
}
