package de.tki.comfyuicompanion.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfyuicompanion.domain.ComfyRegistryWorkflow;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.IComfyRegistryClient;
import de.tki.comfyuicompanion.service.ILocalModelValidator;
import de.tki.comfyuicompanion.service.IModelAnalyzer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
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

/**
 * Validates availability of required model weights on local storage and manages model caches.
 */
@Service
public class LocalModelValidator implements ILocalModelValidator {
    private static final Logger logger = LoggerFactory.getLogger(LocalModelValidator.class);

    private final ConfigService configService;
    private final IComfyRegistryClient registryClient;
    private final LocalModelScanner localModelScanner;
    private final IModelAnalyzer modelAnalyzer;
    private final LocalModelCacheManager cacheManager;

    private final Set<String> localModelNames = ConcurrentHashMap.newKeySet();
    private final Set<String> activeLocalModelNames = ConcurrentHashMap.newKeySet();
    private final Set<String> archivedModelNames = ConcurrentHashMap.newKeySet();

    private final Semaphore downloadSemaphore = new Semaphore(2);
    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "WorkflowDownloadValidator");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "safetensors", "sft", "ckpt", "bin", "pt", "pth", "onnx"
    );

    /**
     * Constructs a new validator without explicitly injected cache bootstrapper.
     *
     * @param configService service providing configuration paths
     * @param registryClient HTTP client for ComfyUI registry downloads
     * @param localModelScanner scanner for local directory model indexing
     * @param modelAnalyzer workflow JSON structure analyzer
     */
    public LocalModelValidator(ConfigService configService,
                               IComfyRegistryClient registryClient,
                               LocalModelScanner localModelScanner,
                               IModelAnalyzer modelAnalyzer) {
        this(configService, registryClient, localModelScanner, modelAnalyzer, null);
    }

    /**
     * Primary constructor with optional cache bootstrapper for initial setups.
     *
     * @param configService configuration service
     * @param registryClient ComfyUI registry API client
     * @param localModelScanner scanner for discovering local models
     * @param modelAnalyzer analyzer extracting model references from graphs
     * @param cacheBootstrapper optional initial cache seeder
     */
    @Autowired
    public LocalModelValidator(ConfigService configService,
                               IComfyRegistryClient registryClient,
                               LocalModelScanner localModelScanner,
                               IModelAnalyzer modelAnalyzer,
                               @Autowired(required = false) de.tki.comfyuicompanion.service.IDefaultCacheBootstrapper cacheBootstrapper) {
        this.configService = configService;
        this.registryClient = registryClient;
        this.localModelScanner = localModelScanner;
        this.modelAnalyzer = modelAnalyzer;
        this.cacheManager = new LocalModelCacheManager(new ObjectMapper());

        if (cacheBootstrapper != null) {
            cacheBootstrapper.bootstrapDefaultCache();
        }
        String appDir = (configService != null && configService.getAppDataPath() != null)
                ? configService.getAppDataPath()
                : (System.getProperty("user.home") + File.separator + ".comfyui-companion");
        this.cacheManager.initializeCache(appDir);
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
                tempNames.add(lowerName);
                tempNames.add(baseName(lowerName));
                tempActive.add(lowerName);
                tempActive.add(baseName(lowerName));
            }
        }

        // Scan Archive Directory and add archived models
        String archivePathStr = configService != null ? configService.getArchivePath() : null;
        if (archivePathStr != null && !archivePathStr.trim().isEmpty()) {
            try {
                Path archiveRoot = Paths.get(archivePathStr).toAbsolutePath().normalize();
                if (Files.exists(archiveRoot) && Files.isDirectory(archiveRoot)) {
                    try (Stream<Path> walk = Files.walk(archiveRoot)) {
                        walk.filter(Files::isRegularFile)
                            .filter(p -> {
                                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
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
                logger.error("⚠️ [LocalModelValidator] Error scanning archive: {}", e.getMessage());
            }
        }

        localModelNames.clear();
        localModelNames.addAll(tempNames);
        activeLocalModelNames.clear();
        activeLocalModelNames.addAll(tempActive);
        archivedModelNames.clear();
        archivedModelNames.addAll(tempArchived);
    }

    /**
     * Checks if a model name exists in active local model directories.
     *
     * @param modelName the file or model identifier
     * @return true if currently available locally
     */
    public boolean isModelActive(String modelName) {
        if (modelName == null || modelName.isBlank()) return false;
        String lower = modelName.toLowerCase(Locale.ROOT).trim();
        return activeLocalModelNames.contains(lower) || activeLocalModelNames.contains(baseName(lower));
    }

    /**
     * Checks if a model name exists in the configured archive storage.
     *
     * @param modelName the file or model identifier
     * @return true if archived
     */
    public boolean isModelArchived(String modelName) {
        if (modelName == null || modelName.isBlank()) return false;
        String lower = modelName.toLowerCase(Locale.ROOT).trim();
        return archivedModelNames.contains(lower) || archivedModelNames.contains(baseName(lower));
    }

    @Override
    public CompletableFuture<Void> validateWorkflowModelsAsync(ComfyRegistryWorkflow workflow) {
        if (localModelNames.isEmpty()) {
            scanLocalModels();
        }

        String wfId = workflow.getId();
        File localWorkflowFile = findLocalWorkflowFile(workflow);
        if (wfId != null && cacheManager.contains(wfId)) {
            List<ModelInfo> cached = cacheManager.get(wfId);
            boolean isCloud = cacheManager.isCloudOnly(wfId);
            if ((cached != null && !cached.isEmpty()) || (isCloud && (localWorkflowFile == null || !localWorkflowFile.exists()))) {
                workflow.setRequiredModelInfos(cached != null ? cached : new ArrayList<>());
                workflow.setCloudOnly(isCloud);
                List<String> names = new ArrayList<>();
                if (cached != null) {
                    for (ModelInfo info : cached) {
                        if (info.getName() != null) names.add(info.getName());
                    }
                }
                workflow.setRequiredModels(names);
                boolean allPresent = !names.isEmpty() && checkModelsPresent(names);
                workflow.setHasAllModelsLocal(allPresent);
                return CompletableFuture.completedFuture(null);
            }
        }

        if (workflow.getRequiredModels() != null && !workflow.getRequiredModels().isEmpty() && hasActualModelFiles(workflow.getRequiredModels())) {
            List<ModelInfo> infos = new ArrayList<>();
            for (String name : workflow.getRequiredModels()) {
                ModelInfo info = new ModelInfo();
                info.setName(name);
                info.setType(LocalModelCacheManager.inferTypeFromFilename(name));
                infos.add(info);
            }
            workflow.setRequiredModelInfos(infos);
            boolean allPresent = checkModelsPresent(workflow.getRequiredModels());
            workflow.setHasAllModelsLocal(allPresent);
            if (wfId != null) {
                cacheManager.putAndSave(wfId, infos, false);
            }
            return CompletableFuture.completedFuture(null);
        }

        if (localWorkflowFile != null && localWorkflowFile.exists()) {
            try {
                String jsonText = Files.readString(localWorkflowFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                List<ModelInfo> analyzedInfo = modelAnalyzer.analyze(jsonText, localWorkflowFile.getName());
                if (analyzedInfo != null && !analyzedInfo.isEmpty()) {
                    workflow.setRequiredModelInfos(analyzedInfo);
                    boolean isCloud = WorkflowCloudDetector.checkCloudOnly(jsonText, analyzedInfo);
                    workflow.setCloudOnly(isCloud);
                    List<String> parsedModels = new ArrayList<>();
                    for (ModelInfo info : analyzedInfo) {
                        if (info.getName() != null && !info.getName().isBlank()) {
                            parsedModels.add(info.getName());
                        }
                    }
                    workflow.setRequiredModels(parsedModels);
                    if (wfId != null) {
                        cacheManager.putAndSave(wfId, analyzedInfo, isCloud);
                    }
                    workflow.setHasAllModelsLocal(checkModelsPresent(parsedModels));
                    return CompletableFuture.completedFuture(null);
                }
            } catch (Exception ignored) {}
        }

        String downloadUrl = workflow.getJsonDownloadUrl();
        if (downloadUrl == null || downloadUrl.isEmpty() || downloadUrl.startsWith("mock:")) {
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

                boolean isCloud = WorkflowCloudDetector.checkCloudOnly(jsonText, analyzedInfo);
                workflow.setCloudOnly(isCloud);

                List<String> parsedModels = new ArrayList<>();
                for (ModelInfo info : analyzedInfo) {
                    if (info.getName() != null && !info.getName().isBlank()) {
                        parsedModels.add(info.getName());
                    }
                }
                workflow.setRequiredModels(parsedModels);

                if (wfId != null) {
                    cacheManager.putAndSave(wfId, analyzedInfo, isCloud);
                }

                boolean allPresent = checkModelsPresent(parsedModels);
                workflow.setHasAllModelsLocal(allPresent);
            } catch (Exception e) {
                logger.error("❌ [LocalModelValidator] Error parsing downloaded workflow JSON: {}", e.getMessage());
                workflow.setHasAllModelsLocal(false);
            }
        })
        .exceptionally(ex -> {
            if (!(ex.getCause() instanceof InterruptedException)) {
                logger.error("❌ [LocalModelValidator] Failed to validate models from JSON download for: {}", workflow.getTitle());
            }
            workflow.setHasAllModelsLocal(false);
            return null;
        });
    }

    @Override
    public Set<String> getLocalModelBaseNames() {
        return Collections.unmodifiableSet(localModelNames);
    }

    @Override
    public Set<String> getActiveLocalModelNames() {
        return Collections.unmodifiableSet(activeLocalModelNames);
    }

    @Override
    public List<ModelInfo> getCachedModelsForWorkflow(String idOrTitle) {
        return cacheManager.getCachedModels(idOrTitle);
    }

    private boolean checkModelsPresent(List<String> requiredModels) {
        if (requiredModels == null || requiredModels.isEmpty()) {
            return true;
        }
        for (String reqModel : requiredModels) {
            if (isSupportedModelFile(reqModel)) {
                String cleanReq = reqModel.toLowerCase(Locale.ROOT).trim();
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

    private File findLocalWorkflowFile(ComfyRegistryWorkflow workflow) {
        if (workflow == null) return null;
        List<File> dirs = new ArrayList<>();
        if (configService != null) {
            if (configService.getUserWorkflowsDir() != null) dirs.add(configService.getUserWorkflowsDir());
            if (configService.getShippedWorkflowsDir() != null) dirs.add(configService.getShippedWorkflowsDir());
        } else {
            dirs.add(new File("user_workflows"));
            dirs.add(new File("workflows"));
        }
        String id = workflow.getId();
        String title = workflow.getTitle();
        String sanitizedTitle = title != null
                ? title.replaceAll("[^a-zA-Z0-9\\-_\\s]", "").trim().replaceAll("\\s+", "_").toLowerCase(Locale.ROOT)
                : null;
        for (File dir : dirs) {
            if (dir == null || !dir.exists() || !dir.isDirectory()) continue;
            if (id != null && !id.isBlank()) {
                File f1 = new File(dir, id + ".json");
                if (f1.exists()) return f1;
                if (id.startsWith("image_")) {
                    File f2 = new File(dir, id.substring(6) + ".json");
                    if (f2.exists()) return f2;
                }
            }
            if (sanitizedTitle != null && !sanitizedTitle.isBlank()) {
                File f3 = new File(dir, sanitizedTitle + ".json");
                if (f3.exists()) return f3;
            }
        }
        return null;
    }

    /**
     * Determines whether a given workflow JSON definition operates purely in the cloud.
     *
     * @param jsonText the raw JSON string of the workflow
     * @param requiredModels list of models parsed or associated with the workflow
     * @return true if the workflow is cloud-only
     */
    public static boolean checkCloudOnly(String jsonText, List<ModelInfo> requiredModels) {
        return WorkflowCloudDetector.checkCloudOnly(jsonText, requiredModels);
    }
}
