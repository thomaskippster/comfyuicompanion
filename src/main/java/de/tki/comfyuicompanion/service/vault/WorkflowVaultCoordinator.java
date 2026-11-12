package de.tki.comfyuicompanion.service.vault;

import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.domain.VaultWorkflowMetadata;
import de.tki.comfyuicompanion.event.WorkflowVaultUpdatedEvent;
import de.tki.comfyuicompanion.service.*;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintEntry;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Enterprise Coordinator / Facade managing My Workflow Vault business logic,
 * model readiness computations, asynchronous model downloading, and synchronization with Image Lab.
 */
@Component
public class WorkflowVaultCoordinator {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowVaultCoordinator.class);

    private final IWorkflowVaultRepository vaultRepository;
    private final ILocalModelValidator localModelValidator;
    private final IDownloadManager downloadManager;
    private final IModelSearchService modelSearchService;
    private final de.tki.comfyuicompanion.service.impl.CivitaiLocalModelScanner localModelScanner;

    private final List<BlueprintEntry> cachedVaultEntries = new CopyOnWriteArrayList<>();
    private final Map<String, BlueprintStatus> statusCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final List<Runnable> vaultChangeListeners = new CopyOnWriteArrayList<>();
    private final java.util.concurrent.atomic.AtomicBoolean refreshInProgress = new java.util.concurrent.atomic.AtomicBoolean(false);
    private volatile boolean initialized = false;

    @Autowired
    public WorkflowVaultCoordinator(
            IWorkflowVaultRepository vaultRepository,
            @Autowired(required = false) ILocalModelValidator localModelValidator,
            @Autowired(required = false) IDownloadManager downloadManager,
            @Autowired(required = false) IModelSearchService modelSearchService,
            @Autowired(required = false) de.tki.comfyuicompanion.service.impl.CivitaiLocalModelScanner localModelScanner) {
        this.vaultRepository = vaultRepository;
        this.localModelValidator = localModelValidator;
        this.downloadManager = downloadManager;
        this.modelSearchService = modelSearchService;
        this.localModelScanner = localModelScanner;
    }

    public WorkflowVaultCoordinator(
            IWorkflowVaultRepository vaultRepository,
            ILocalModelValidator localModelValidator,
            IDownloadManager downloadManager,
            IModelSearchService modelSearchService) {
        this(vaultRepository, localModelValidator, downloadManager, modelSearchService, null);
    }

    /**
     * Refreshes all entries from the vault directory and recalculates model availability.
     *
     * @return updated list of vault entries
     */
    public synchronized List<BlueprintEntry> refreshVaultEntries() {
        refreshInProgress.set(true);
        try {
            if (localModelValidator != null) {
                localModelValidator.scanLocalModels();
            }
            List<BlueprintEntry> loaded = vaultRepository != null ? vaultRepository.loadAllVaultEntries() : Collections.emptyList();
            cachedVaultEntries.clear();
            cachedVaultEntries.addAll(loaded);

            statusCache.clear();
            for (BlueprintEntry e : loaded) {
                computeStatus(e);
            }
            initialized = true;
        } finally {
            refreshInProgress.set(false);
        }
        for (Runnable r : vaultChangeListeners) {
            try {
                r.run();
            } catch (Exception ex) {
                logger.debug("Vault change listener error: {}", ex.getMessage());
            }
        }
        return new ArrayList<>(cachedVaultEntries);
    }

    /**
     * Returns currently cached vault entries, loading from repository if uninitialized.
     * Avoids blocking the Swing Event Dispatch Thread if an asynchronous refresh is needed.
     *
     * @return list of vault entries
     */
    public List<BlueprintEntry> getCachedVaultEntries() {
        if (!initialized && !refreshInProgress.get()) {
            if (javax.swing.SwingUtilities.isEventDispatchThread()) {
                if (refreshInProgress.compareAndSet(false, true)) {
                    Thread.ofVirtual().name("VaultInitialLoad").start(this::refreshVaultEntries);
                }
                return new ArrayList<>(cachedVaultEntries);
            }
            return refreshVaultEntries();
        }
        return new ArrayList<>(cachedVaultEntries);
    }

    /**
     * Computes the availability status of required models for a given blueprint entry.
     *
     * @param entry the blueprint entry
     * @return the computed BlueprintStatus
     */
    public BlueprintStatus computeStatus(BlueprintEntry entry) {
        if (entry == null) {
            return new BlueprintStatus(0, 0, Collections.emptyList());
        }
        if (statusCache.containsKey(entry.name)) {
            return statusCache.get(entry.name);
        }

        if (entry.requiredModels == null || entry.requiredModels.isEmpty()) {
            BlueprintStatus st = new BlueprintStatus(0, 0, Collections.emptyList());
            statusCache.put(entry.name, st);
            entry.status = st;
            return st;
        }

        int present = 0;
        int missing = 0;
        List<String> missingNames = new ArrayList<>();

        for (ModelInfo info : entry.requiredModels) {
            boolean isPresent = isModelPresent(info);
            if (isPresent) {
                present++;
            } else {
                missing++;
                missingNames.add(info.getName());
            }
        }

        BlueprintStatus status = new BlueprintStatus(present, missing, missingNames);
        statusCache.put(entry.name, status);
        entry.status = status;
        return status;
    }

    public boolean isModelPresent(ModelInfo info) {
        if (info == null) {
            return false;
        }
        if ("workflows".equalsIgnoreCase(info.getType()) || "workflows".equalsIgnoreCase(info.getSave_path())) {
            return true;
        }
        String name = info.getName() != null ? info.getName().trim() : "";
        String filename = info.getFilename() != null ? info.getFilename().trim() : "";
        if (name.isEmpty() && filename.isEmpty()) {
            return false;
        }

        String lowerName = (filename.isEmpty() ? name : filename).toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".zip") || lowerName.endsWith(".json") ||
            lowerName.equals("openai") || lowerName.equals("dall-e") || lowerName.equals("dalle") ||
            lowerName.equals("elevenlabs") || lowerName.equals("gemini") || lowerName.equals("anthropic") ||
            lowerName.equals("claude") || lowerName.equals("openrouter")) {
            return true;
        }

        String[] candidates = buildModelCandidates(name, filename);

        if (localModelValidator != null) {
            for (String cand : candidates) {
                if (cand.isEmpty()) continue;
                if (localModelValidator.isModelActive(cand) || localModelValidator.findLocalModelPath(cand).isPresent()) {
                    return true;
                }
            }
        }

        if (localModelScanner != null) {
            return localModelScanner.isModelFilePresent(info);
        }

        if (localModelValidator != null && localModelValidator.getLocalModelBaseNames() != null
                && localModelValidator.getLocalModelBaseNames().isEmpty()
                && !javax.swing.SwingUtilities.isEventDispatchThread()) {
            localModelValidator.scanLocalModels();
            for (String cand : candidates) {
                if (cand.isEmpty()) continue;
                if (localModelValidator.isModelActive(cand) || localModelValidator.findLocalModelPath(cand).isPresent()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks whether the specified model exists in the configured archive storage.
     *
     * @param info model metadata
     * @return true if found in archive storage
     */
    public boolean isModelArchived(ModelInfo info) {
        if (info == null || localModelValidator == null) {
            return false;
        }
        String name = info.getName() != null ? info.getName().trim() : "";
        String filename = info.getFilename() != null ? info.getFilename().trim() : "";
        for (String cand : buildModelCandidates(name, filename)) {
            if (!cand.isEmpty() && localModelValidator.isModelArchived(cand)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves a human-readable size string for the given model, checking local disk storage when unknown.
     *
     * @param info model metadata
     * @return formatted size string or "Unknown"
     */
    public String resolveModelSize(ModelInfo info) {
        if (info == null) {
            return "Unknown";
        }
        if (info.getSize() != null && !info.getSize().isBlank() && !"Unknown".equalsIgnoreCase(info.getSize())) {
            return info.getSize();
        }
        long bytes = info.getByteSize();
        if (bytes <= 0 && localModelValidator != null) {
            String name = info.getName() != null ? info.getName().trim() : "";
            String filename = info.getFilename() != null ? info.getFilename().trim() : "";
            for (String cand : buildModelCandidates(name, filename)) {
                if (cand.isEmpty()) continue;
                Optional<java.nio.file.Path> pathOpt = localModelValidator.findLocalModelPath(cand);
                if (pathOpt.isPresent()) {
                    try {
                        bytes = java.nio.file.Files.size(pathOpt.get());
                        if (bytes > 0) break;
                    } catch (Exception ignored) {
                        logger.debug("Ignored file size read error: {}", ignored.getMessage());
                    }
                }
            }
        }
        if (bytes >= 1024L * 1024 * 1024) return String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
        if (bytes >= 1024L * 1024) return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024));
        if (bytes > 0) return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
        return "Unknown";
    }

    private String[] buildModelCandidates(String name, String filename) {
        String baseName = extractBaseName(name);
        String baseFilename = extractBaseName(filename);
        return new String[] {
            name,
            filename,
            baseName,
            baseFilename,
            stripFileExtension(baseName),
            stripFileExtension(baseFilename)
        };
    }

    private String extractBaseName(String path) {
        if (path == null) return "";
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private String stripFileExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /**
     * Returns all vault blueprints where all required models are locally installed and ready.
     * This method serves as the exclusive source of truth for the Image Lab.
     *
     * @return list of ready vault blueprints
     */
    public List<BlueprintEntry> getReadyVaultBlueprints() {
        List<BlueprintEntry> all = getCachedVaultEntries();
        List<BlueprintEntry> ready = new ArrayList<>();
        for (BlueprintEntry e : all) {
            BlueprintStatus st = computeStatus(e);
            if (st.missingCount == 0) {
                ready.add(e);
            }
        }
        return ready;
    }

    /**
     * Imports an external file (.json or .png/.webp with embedded ComfyUI metadata) into the Vault.
     *
     * @param file source file
     * @return imported BlueprintEntry
     * @throws Exception if import fails
     */
    public BlueprintEntry importFile(File file) throws Exception {
        BlueprintEntry entry = vaultRepository.importFileToVault(file);
        refreshVaultEntries();
        return entry;
    }

    /**
     * Saves a workflow to the vault with full metadata.
     *
     * @param baseName    target base filename
     * @param jsonContent workflow json string
     * @param imageBytes  preview image bytes
     * @param imageExt    image extension
     * @param metadata    sidecar metadata
     * @return created BlueprintEntry
     */
    public BlueprintEntry saveToVault(String baseName, String jsonContent, byte[] imageBytes, String imageExt, VaultWorkflowMetadata metadata) {
        BlueprintEntry entry = vaultRepository.saveWorkflowToVault(baseName, jsonContent, imageBytes, imageExt, metadata);
        refreshVaultEntries();
        return entry;
    }

    /**
     * Removes an entry from the vault.
     *
     * @param entry blueprint entry to remove
     * @return true if successfully deleted
     */
    public boolean removeWorkflow(BlueprintEntry entry) {
        if (entry == null || vaultRepository == null) return false;
        boolean removed = false;
        if (entry.filePath != null && !entry.filePath.isBlank()) {
            removed = vaultRepository.removeWorkflowFromVault(entry.filePath);
        }
        if (!removed && entry.registryWorkflow != null && entry.registryWorkflow.getId() != null) {
            removed = vaultRepository.removeWorkflowFromVault(entry.registryWorkflow.getId());
        }
        if (!removed && entry.filename != null && !entry.filename.isBlank()) {
            removed = vaultRepository.removeWorkflowFromVault(entry.filename);
        }
        if (!removed && entry.name != null && !entry.name.isBlank()) {
            removed = vaultRepository.removeWorkflowFromVault(entry.name);
        }
        refreshVaultEntries();
        return removed;
    }

    /**
     * Checks whether a workflow with the specified title or identifier already exists in the Vault.
     *
     * @param titleOrName workflow title or filename
     * @return true if currently present in the vault
     */
    public boolean isWorkflowInVault(String titleOrName) {
        if (titleOrName == null || titleOrName.isBlank()) {
            return false;
        }
        return vaultRepository != null && vaultRepository.isWorkflowInVault(titleOrName);
    }

    /**
     * Queues missing models for download for a given blueprint entry.
     *
     * @param entry              blueprint entry
     * @param onProgressCallback callback for progress updates
     * @return number of models queued
     */
    public int downloadMissingModels(BlueprintEntry entry, Consumer<String> onProgressCallback) {
        if (entry == null || downloadManager == null || entry.requiredModels == null) {
            return 0;
        }

        List<ModelInfo> missing = new ArrayList<>();
        for (ModelInfo m : entry.requiredModels) {
            if (!isModelPresent(m)) {
                missing.add(m);
            }
        }

        if (missing.isEmpty()) {
            if (onProgressCallback != null) onProgressCallback.accept("All models already downloaded!");
            return 0;
        }

        int queued = 0;
        if (downloadManager != null && !missing.isEmpty()) {
            boolean[] selected = new boolean[missing.size()];
            java.util.Arrays.fill(selected, true);
            downloadManager.startQueue(missing, selected, null, (idx, status) -> {
                if (onProgressCallback != null) onProgressCallback.accept(status);
            }, () -> {
                if (onProgressCallback != null) onProgressCallback.accept("Download queue completed.");
                refreshVaultEntries();
            });
            queued = missing.size();
        }

        if (onProgressCallback != null) {
            onProgressCallback.accept("Queued " + queued + " missing model(s) for download.");
        }
        return queued;
    }

    /**
     * Registers a change listener to be invoked whenever vault entries are modified or refreshed.
     *
     * @param listener change listener
     */
    public void addVaultChangeListener(Runnable listener) {
        if (listener != null) {
            vaultChangeListeners.add(listener);
        }
    }

    /**
     * Listens for vault update events and automatically invalidates internal caches.
     *
     * @param event the vault update event
     */
    @EventListener
    public void onVaultUpdated(WorkflowVaultUpdatedEvent event) {
        logger.info("[WorkflowVaultCoordinator] Handling vault event: {} on {}", event.getChangeType(), event.getWorkflowName());
        refreshVaultEntries();
    }
}
