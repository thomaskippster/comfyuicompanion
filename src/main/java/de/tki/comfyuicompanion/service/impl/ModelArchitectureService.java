package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.domain.ModelArchitecture;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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

    private final ComfyUIArchitectureClassifier classifier;
    private ComfyModelAnalyzer modelAnalyzer;
    private final ModelListService modelListService;
    private final IDefaultCacheBootstrapper cacheBootstrapper;
    private final IComfyLifecycleService lifecycleService;
    private final WorkflowDefaultsExtractor defaultsExtractor;
    private final ServerBlueprintDiscoveryService serverDiscoveryService;
    private final ArchitectureMappingRepository mappingRepository;
    private final BlueprintScanService scanService;
    private final BlueprintCacheStore cacheStore;

    public ModelArchitectureService() {
        this(null, null, null, null, null, null);
    }

    public ModelArchitectureService(ConfigService configService) {
        this(configService, null, null, null, null, null);
    }

    public ModelArchitectureService(ConfigService configService, ComfyUIArchitectureClassifier classifier) {
        this(configService, classifier, null, null, null, null);
    }

    public ModelArchitectureService(
            ConfigService configService,
            ComfyUIArchitectureClassifier classifier,
            ComfyModelAnalyzer modelAnalyzer,
            ModelListService modelListService,
            IDefaultCacheBootstrapper cacheBootstrapper,
            IComfyLifecycleService lifecycleService) {
        this(configService, classifier, modelAnalyzer, modelListService, cacheBootstrapper, lifecycleService,
                new WorkflowDefaultsExtractor(), new ServerBlueprintDiscoveryService(), new ArchitectureMappingRepository(),
                new BlueprintScanService(), new BlueprintCacheStore());
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
        this(configService, classifier, modelAnalyzer, modelListService, cacheBootstrapper, lifecycleService,
                defaultsExtractor, serverDiscoveryService, mappingRepository,
                new BlueprintScanService(), new BlueprintCacheStore());
    }

    @Autowired
    public ModelArchitectureService(
            @Autowired(required = false) ConfigService configService,
            @Autowired(required = false) ComfyUIArchitectureClassifier classifier,
            @Autowired(required = false) ComfyModelAnalyzer modelAnalyzer,
            @Autowired(required = false) ModelListService modelListService,
            @Autowired(required = false) IDefaultCacheBootstrapper cacheBootstrapper,
            @Autowired(required = false) IComfyLifecycleService lifecycleService,
            @Autowired(required = false) WorkflowDefaultsExtractor defaultsExtractor,
            @Autowired(required = false) ServerBlueprintDiscoveryService serverDiscoveryService,
            @Autowired(required = false) ArchitectureMappingRepository mappingRepository,
            @Autowired(required = false) BlueprintScanService scanService,
            @Autowired(required = false) BlueprintCacheStore cacheStore) {
        this.configService = configService;
        this.classifier = classifier;
        this.modelAnalyzer = modelAnalyzer;
        this.modelListService = modelListService;
        this.cacheBootstrapper = cacheBootstrapper;
        this.lifecycleService = lifecycleService;
        this.defaultsExtractor = defaultsExtractor != null ? defaultsExtractor : new WorkflowDefaultsExtractor();
        this.serverDiscoveryService = serverDiscoveryService != null ? serverDiscoveryService : new ServerBlueprintDiscoveryService();
        this.mappingRepository = mappingRepository != null ? mappingRepository : new ArchitectureMappingRepository();
        this.scanService = scanService != null ? scanService : new BlueprintScanService();
        this.cacheStore = cacheStore != null ? cacheStore : new BlueprintCacheStore();
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
        File baseDir = getBaseConfigDir();
        cacheStore.loadArchitectureCache(baseDir, architectureCache);
        cacheStore.loadResolvedDefaults(baseDir, resolvedDefaultsMap);
        cacheStore.loadBlueprintScanResults(baseDir, blueprintScanResults);
        if (blueprintScanResults.isEmpty()) {
            runBlueprintAnalysis();
        } else {
            this.progressCompleted = true;
            this.progressPercent = 100;
        }
    }

    @Override
    public void runBlueprintAnalysis() {
        Thread.ofVirtual().name("BlueprintAnalyzer").start(this::analyzeBlueprintsAndSaveDefaults);
    }

    @Override
    public void reloadMappings() {
        File configDir = new File(getBaseConfigDir(), "templates/comfyui");
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

        for (ArchitectureMappingRepository.MappingRule rule : rules) {
            if (rule.pattern().matcher(filename).matches()) {
                detected = rule.architecture();
                break;
            }
        }

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
            } catch (Exception ignored) {
            logger.debug("Ignored non-fatal exception: {}", ignored.getMessage());
        }
        }
    }

    private void analyzeBlueprintsAndSaveDefaults() {
        isAnalyzing = true;
        try {
            String comfyPath = configService != null ? configService.getComfyUIPath() : "";
            File blueprintsDir = scanService.resolveBlueprintsDirectory(comfyPath);
            String comfyUrl = configService != null ? configService.getComfyUIUrl() : null;

            boolean scanPerformed = scanService.executeBlueprintScan(
                    blueprintsDir,
                    comfyUrl,
                    lifecycleService,
                    getModelAnalyzer(),
                    defaultsExtractor,
                    serverDiscoveryService,
                    this::detectArchitecture,
                    this::getStandardDefaultsForArchitecture,
                    resolvedDefaultsMap,
                    blueprintScanResults,
                    this::notifyListeners
            );

            if (scanPerformed) {
                File baseDir = getBaseConfigDir();
                cacheStore.saveResolvedDefaults(baseDir, resolvedDefaultsMap);
                cacheStore.saveArchitectureCache(baseDir, architectureCache);
                cacheStore.saveBlueprintScanResults(baseDir, blueprintScanResults);
            }
        } finally {
            isAnalyzing = false;
        }
    }

    private File getBaseConfigDir() {
        return new File(configService != null ? configService.getAppDataPath() : System.getProperty("user.dir"));
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
            cacheStore.loadResolvedDefaults(getBaseConfigDir(), resolvedDefaultsMap);
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

    /**
     * Obtains standard fallback inference parameter defaults for a recognized model architecture.
     *
     * @param arch target architecture
     * @return standard inference defaults
     */
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

    @Override
    public List<Map<String, Object>> getBlueprintScanResults() {
        if (blueprintScanResults.isEmpty()) {
            cacheStore.loadBlueprintScanResults(getBaseConfigDir(), blueprintScanResults);
        }
        return blueprintScanResults;
    }
}
