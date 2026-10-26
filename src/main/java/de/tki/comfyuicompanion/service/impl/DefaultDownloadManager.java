package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.domain.*;
import de.tki.comfyuicompanion.service.*;
import de.tki.comfyuicompanion.util.ConfigConstants;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.File;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/** Coordinates model download queues, pause/stop states, and status updates. */
@Service
public class DefaultDownloadManager implements IDownloadManager {
    private static final Logger logger = LoggerFactory.getLogger(DefaultDownloadManager.class);
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(3, 3, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    private final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(60)).build();
    private final Set<Integer> completedIndices = ConcurrentHashMap.newKeySet();
    private final Map<Integer, String> statusMap = new ConcurrentHashMap<>();
    private final List<ModelInfo> activeQueueModels = new CopyOnWriteArrayList<>();
    private final Map<String, String> activeStatusByFilename = new ConcurrentHashMap<>();
    private final List<DownloadProgressListener> progressListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean queueRunning = new AtomicBoolean(false);
    private volatile boolean isPaused = false, isStopped = false;
    private volatile boolean[] currentSelection;

    private final ConfigService configService;
    private final PathResolver pathResolver;
    private final CivitaiService civitaiService;
    private final ModelHashRegistry hashRegistry;
    private final IModelValidator modelValidator;
    private final SafePathValidator safePathValidator;
    private final SegmentedDownloadEngine segmentedEngine;
    private final DownloadChecksumVerifier checksumVerifier;
    private final de.tki.comfyuicompanion.service.ICustomNodePostDownloadHandler customNodePostDownloadHandler;

    /** Primary constructor with all dependencies injected. */
    @Autowired
    public DefaultDownloadManager(ConfigService cs, PathResolver pr, CivitaiService civ, ModelHashRegistry hr,
                                  IModelValidator mv, @Autowired(required = false) SafePathValidator spv,
                                  SegmentedDownloadEngine se, DownloadChecksumVerifier cv,
                                  @Autowired(required = false) de.tki.comfyuicompanion.service.ICustomNodePostDownloadHandler postHandler) {
        this.configService = cs; this.pathResolver = pr; this.civitaiService = civ; this.hashRegistry = hr; this.modelValidator = mv;
        this.safePathValidator = spv != null ? spv : new SafePathValidator();
        this.segmentedEngine = se != null ? se : new SegmentedDownloadEngine(cs);
        this.checksumVerifier = cv != null ? cv : new DownloadChecksumVerifier(hr, mv);
        this.customNodePostDownloadHandler = postHandler;
    }

    /** Overloaded constructor for callers without post-download handler. */
    public DefaultDownloadManager(ConfigService cs, PathResolver pr, CivitaiService civ, ModelHashRegistry hr,
                                  IModelValidator mv, SafePathValidator spv,
                                  SegmentedDownloadEngine se, DownloadChecksumVerifier cv) {
        this(cs, pr, civ, hr, mv, spv, se, cv, null);
    }

    /** Overloaded constructor for callers without extracted components. */
    public DefaultDownloadManager(ConfigService cs, PathResolver pr, CivitaiService civ, ModelHashRegistry hr, IModelValidator mv, SafePathValidator spv) {
        this(cs, pr, civ, hr, mv, spv, null, null, null);
    }

    /** Backward-compatible constructor for testing. */
    public DefaultDownloadManager(ConfigService cs, PathResolver pr, CivitaiService civ, ModelHashRegistry hr, IModelValidator mv) {
        this(cs, pr, civ, hr, mv, null, null, null, null);
    }

    private void safeUpdateStatus(int index, String status, BiConsumer<Integer, String> statusUpdater) {
        statusMap.put(index, status);
        if (status.contains("✅ Finished") || status.contains("Stopped") || status.contains("Skipped") || status.contains("Already exists") || status.startsWith("❌ Error")) {
            completedIndices.add(index);
        }
        if (index >= 0 && index < activeQueueModels.size()) {
            ModelInfo info = activeQueueModels.get(index);
            String fn = resolveModelKey(info);
            if (fn != null) {
                activeStatusByFilename.put(fn, status);
                notifyProgressListeners(fn, status);
            }
        }
        if (statusUpdater != null) statusUpdater.accept(index, status);
    }

    @Override public Map<Integer, String> getQueueStatus() { return new HashMap<>(statusMap); }

    @Override
    public void startQueue(List<ModelInfo> models, boolean[] selectedIndices, String baseDir,
                           BiConsumer<Integer, String> statusUpdater, Runnable onFinished) {
        if (isQueueRunning() && models != null && !models.isEmpty()) {
            logger.info("Download queue already running. Appending {} model(s) to active queue.", models.size());
            synchronized (this) {
                int oldLen = currentSelection != null ? currentSelection.length : 0;
                int newLen = oldLen + models.size();
                boolean[] expanded = new boolean[newLen];
                if (currentSelection != null) {
                    System.arraycopy(currentSelection, 0, expanded, 0, oldLen);
                }
                for (int i = oldLen; i < newLen; i++) expanded[i] = true;
                this.currentSelection = expanded;
            }
            for (ModelInfo info : models) {
                int newIdx = activeQueueModels.size();
                activeQueueModels.add(info);
                String fn = resolveModelKey(info);
                if (fn != null) {
                    activeStatusByFilename.put(fn, "Queued");
                    notifyProgressListeners(fn, "Queued");
                }
                CompletableFuture.runAsync(() -> processModelDownload(info, newIdx, baseDir, statusUpdater), executor)
                        .thenRun(() -> {
                            if (onFinished != null) onFinished.run();
                        });
            }
            return;
        }

        isStopped = false; isPaused = false;
        queueRunning.set(true);
        activeQueueModels.clear();
        activeStatusByFilename.clear();
        if (models != null) {
            activeQueueModels.addAll(models);
        }
        this.currentSelection = selectedIndices != null ? Arrays.copyOf(selectedIndices, selectedIndices.length) : null;
        completedIndices.clear(); statusMap.clear();
        int max = (configService != null && configService.getMaxParallelDownloads() > 0)
                ? configService.getMaxParallelDownloads()
                : 3;
        executor.setCorePoolSize(max); executor.setMaximumPoolSize(max);
        AtomicBoolean finished = new AtomicBoolean(false);
        Thread.ofVirtual().name("Download-Queue-Worker").start(() -> {
            try {
                CompletableFuture<?>[] futures = new CompletableFuture[models.size()];
                for (int i = 0; i < models.size(); i++) {
                    final int idx = i;
                    if (isStopped || !isSelected(idx)) {
                        if (!isSelected(idx)) safeUpdateStatus(idx, "Skipped (Not Selected)", statusUpdater);
                        futures[idx] = CompletableFuture.completedFuture(null);
                    } else {
                        safeUpdateStatus(idx, "Starting...", statusUpdater);
                        futures[idx] = CompletableFuture.runAsync(() -> processModelDownload(models.get(idx), idx, baseDir, statusUpdater), executor);
                    }
                }
                CompletableFuture.allOf(futures).join();
            } finally {
                queueRunning.set(false);
                if (finished.compareAndSet(false, true) && onFinished != null) onFinished.run();
            }
        });
    }

    private void processModelDownload(ModelInfo info, int index, String baseDir, BiConsumer<Integer, String> statusUpdater) {
        if (isStopped || !isSelected(index)) return;
        safeUpdateStatus(index, "Connecting...", statusUpdater);
        try {
            String url = info.getUrl();
            if (url == null || url.isBlank() || url.equalsIgnoreCase("LOCAL") || (!url.toLowerCase().startsWith("http://") && !url.toLowerCase().startsWith("https://"))) {
                logger.warn("Model [{}] does not have a valid remote download URL (url='{}'). Skipping download.", info.getName(), url);
                if (!isStopped && isSelected(index)) {
                    safeUpdateStatus(index, "❌ No download URL available", statusUpdater);
                }
                return;
            }
            String sub = (info.getSave_path() == null || info.getSave_path().isEmpty() || "default".equalsIgnoreCase(info.getSave_path())) ?
                    ModelFolder.fromString(info.getType()).getDefaultFolderName() : info.getSave_path();
            if (sub.startsWith("models/") || sub.startsWith("models\\")) {
                sub = sub.substring(7);
            }
            Path base = (baseDir != null && !baseDir.isEmpty()) ? pathResolver.resolve(baseDir) : pathResolver.resolve(configService != null ? configService.getModelsPath() : null);
            if (base == null) base = Path.of("models");
            String fileName = (info.getFilename() != null && !info.getFilename().isBlank()) ? info.getFilename() : info.getName();
            Path targetFile = base.resolve(sub).resolve(fileName);
            if (safePathValidator != null) safePathValidator.validateWithinBase(base, targetFile);
            Files.createDirectories(targetFile.getParent());
            logger.info("Initiating download for model [{}] to destination: {}", fileName, targetFile);
            downloadWithResume(info, targetFile, index, statusUpdater, 0);
        } catch (Exception e) {
            logger.error("Download processing failed for model [{}]: {}", info.getName(), e.getMessage(), e);
            if (!isStopped && isSelected(index)) safeUpdateStatus(index, "❌ Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), statusUpdater);
        }
    }

    private void downloadWithResume(ModelInfo info, Path targetFile, int index, BiConsumer<Integer, String> statusUpdater, int retry) {
        try {
            if (waitForPauseAndCheckSelection(index, statusUpdater)) return;
            File file = targetFile.toFile();
            String archive = configService != null ? configService.getArchivePath() : null;
            boolean inArchive = archive != null && !archive.isEmpty() && file.getAbsolutePath().toLowerCase().startsWith(new File(archive).getAbsolutePath().toLowerCase());
            if (file.exists() && (file.length() == 0 || inArchive) && !inArchive) file.delete();
            long existingSize = inArchive ? 0 : getExistingDownloadedBytes(targetFile);
            String url = appendCivitaiTokenIfNeeded(info.getUrl());
            if (checkDiskSpace(targetFile, 0, existingSize, index, statusUpdater)) return;
            if (waitForPauseAndCheckSelection(index, statusUpdater)) return;

            long totalRemoteSize = segmentedEngine.probeRemoteSize(url);
            if (totalRemoteSize == -1) {
                if (url.contains("civitai.com")) {
                    safeUpdateStatus(index, "❌ Civitai API Key Required (Auth Error)", statusUpdater);
                } else if (url.contains("huggingface.co")) {
                    safeUpdateStatus(index, "❌ HuggingFace Token Required (Auth Error)", statusUpdater);
                } else {
                    safeUpdateStatus(index, "❌ Auth Required (Token?)", statusUpdater);
                }
                return;
            }
            if (checkDiskSpace(targetFile, totalRemoteSize, existingSize, index, statusUpdater)) return;

            if (existingSize > 0 && totalRemoteSize > 0 && existingSize == totalRemoteSize && file.exists()) {
                if (modelValidator != null && !modelValidator.validateFile(file).ok) {
                    if (retry < 1) { file.delete(); safeUpdateStatus(index, "🔄 Fixing Corrupted File...", statusUpdater); downloadWithResume(info, targetFile, index, statusUpdater, retry + 1); return; }
                    safeUpdateStatus(index, "❌ Validation failed: " + modelValidator.validateFile(file).message, statusUpdater); return;
                }
                if (customNodePostDownloadHandler != null && customNodePostDownloadHandler.isCustomNodeArchive(targetFile)) {
                    customNodePostDownloadHandler.processDownloadedArchive(targetFile, s -> safeUpdateStatus(index, s, statusUpdater));
                }
                safeUpdateStatus(index, "✅ Already exists", statusUpdater); return;
            }

            if (waitForPauseAndCheckSelection(index, statusUpdater)) return;
            int segments = (retry > 0) ? 1 : (configService != null ? configService.getSegmentsPerFile() : 1);
            String fileName = (info.getFilename() != null && !info.getFilename().isBlank()) ? info.getFilename() : info.getName();
            if (segmentedEngine.download(fileName, url, targetFile, totalRemoteSize, segments, ctrl(index), s -> safeUpdateStatus(index, s, statusUpdater))) {
                if (modelValidator != null) {
                    var val = modelValidator.validateFile(targetFile.toFile());
                    if (!val.ok) { targetFile.toFile().delete(); safeUpdateStatus(index, "❌ Validation failed: " + val.message, statusUpdater); return; }
                }
                checksumVerifier.registerModelHash(targetFile.toFile());
                if (civitaiService != null) civitaiService.downloadMetadataAndPreview(info.getUrl(), targetFile);

                if (customNodePostDownloadHandler != null && customNodePostDownloadHandler.isCustomNodeArchive(targetFile)) {
                    safeUpdateStatus(index, "📦 Auto-installing Custom Node...", statusUpdater);
                    boolean installed = customNodePostDownloadHandler.processDownloadedArchive(targetFile, s -> safeUpdateStatus(index, s, statusUpdater));
                    if (installed) {
                        safeUpdateStatus(index, "✅ Finished (Custom Node Installed)", statusUpdater);
                        return;
                    }
                }

                safeUpdateStatus(index, "✅ Finished", statusUpdater);
            }
        } catch (Exception e) {
            handleDownloadError(e, info, targetFile, index, statusUpdater, retry);
        }
    }

    private SegmentedDownloadEngine.DownloadControl ctrl(int idx) {
        return new SegmentedDownloadEngine.DownloadControl() {
            public boolean isStopped() { return isStopped; }
            public boolean isPaused() { return isPaused; }
            public boolean isSelected() { return DefaultDownloadManager.this.isSelected(idx); }
        };
    }

    private long getExistingDownloadedBytes(Path target) {
        try {
            File f = target.toFile();
            if (f.exists()) return f.length();
            Path cmfd = target.resolveSibling(target.getFileName().toString() + ".cmfd");
            if (Files.exists(cmfd)) return Files.size(cmfd);
            long partSum = 0;
            for (int i = 0; i < 16; i++) {
                Path p = target.resolveSibling(target.getFileName().toString() + ".part" + i);
                if (Files.exists(p)) partSum += Files.size(p);
            }
            return partSum;
        } catch (Exception ignored) { return 0L; }
    }

    private boolean checkDiskSpace(Path target, long req, long existingBytes, int idx, BiConsumer<Integer, String> su) {
        try {
            Path parent = target.getParent() != null ? target.getParent() : Path.of(".");
            long f = Files.getFileStore(parent).getUsableSpace();
            long needed = Math.max(0L, req - existingBytes);
            if (needed > 0 && f < (needed + 524288000L)) {
                safeUpdateStatus(idx, "❌ No Space (" + SegmentedDownloadEngine.formatSize(f) + " free < " + SegmentedDownloadEngine.formatSize(needed) + " required)", su);
                return true;
            }
            if (needed <= 0 && f < 15L * 1024 * 1024 * 1024) safeUpdateStatus(idx, "⚠️ Low Disk Space (" + SegmentedDownloadEngine.formatSize(f) + " free)", su);
        } catch (Exception ignored) { logger.debug("Ignored non-fatal exception: {}", ignored.getMessage()); }
        return false;
    }

    private void handleDownloadError(Exception e, ModelInfo info, Path target, int idx, BiConsumer<Integer, String> su, int retry) {
        try { Thread.sleep(50); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
        if (isStopped || !isSelected(idx) || Thread.currentThread().isInterrupted()) {
            safeUpdateStatus(idx, !isSelected(idx) ? "Skipped (Unchecked)" : "Stopped", su);
        } else if (retry < 10 && isRetriableException(e)) {
            long delay = Math.min(30000L, 1000L * (1L << Math.min(retry, 4)));
            String msg = (e instanceof SegmentedDownloadEngine.PrematureStreamEndException pse) ?
                    "🔄 Stream ended (" + SegmentedDownloadEngine.formatSize(pse.getDownloaded()) + "/" + SegmentedDownloadEngine.formatSize(pse.getTotal()) + "), resuming (" + (retry + 1) + "/10)..." :
                    "🔄 Retrying in " + (delay / 1000) + "s (" + (retry + 1) + "/10)...";
            safeUpdateStatus(idx, msg, su);
            try { Thread.sleep(delay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
            downloadWithResume(info, target, idx, su, retry + 1);
        } else {
            safeUpdateStatus(idx, "❌ Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), su);
        }
    }

    private boolean isRetriableException(Exception e) {
        if (e instanceof InterruptedException || e instanceof java.net.UnknownHostException) return false;
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        if (msg.contains("refused") || msg.contains("404") || msg.contains("not found")) return false;
        if (msg.contains("401") || msg.contains("403") || msg.contains("auth required") || msg.contains("key required")) return false;
        if (msg.contains("lfs stub")) return false;
        return true;
    }

    private String appendCivitaiTokenIfNeeded(String url) {
        if (url == null || !url.contains("civitai.com") || url.contains("token=")) return url;
        String key = (configService != null) ? configService.getCivitaiApiKey() : null;
        return (key != null && !key.isBlank()) ? url + (url.contains("?") ? "&token=" : "?token=") + key.trim() : url;
    }

    private boolean waitForPauseAndCheckSelection(int idx, BiConsumer<Integer, String> su) {
        try {
            if (isPaused) safeUpdateStatus(idx, "Paused", su);
            while (isPaused && !isStopped && isSelected(idx)) Thread.sleep(200);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        if (isStopped || !isSelected(idx)) {
            safeUpdateStatus(idx, !isSelected(idx) ? "Skipped (Unchecked)" : "Stopped", su);
            return true;
        }
        return false;
    }

    @Override
    public boolean isQueueRunning() {
        return queueRunning.get() && !isStopped && completedIndices.size() < activeQueueModels.size();
    }

    @Override
    public boolean isDownloading(String filename) {
        return getActiveDownloadStatus(filename).isPresent();
    }

    @Override
    public Optional<String> getActiveDownloadStatus(String filename) {
        if (filename == null || filename.isBlank()) return Optional.empty();
        String clean = filename.trim();
        String status = activeStatusByFilename.get(clean);
        if (status == null) {
            for (Map.Entry<String, String> entry : activeStatusByFilename.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(clean)) {
                    status = entry.getValue();
                    break;
                }
            }
        }
        if (status != null && isActiveStatus(status)) {
            return Optional.of(status);
        }
        return Optional.empty();
    }

    private boolean isActiveStatus(String status) {
        if (status == null) return false;
        return !status.contains("Finished")
                && !status.contains("Stopped")
                && !status.contains("Skipped")
                && !status.contains("Already exists")
                && !status.startsWith("❌");
    }

    @Override
    public synchronized void stopDownload(String filename) {
        if (filename == null || filename.isBlank()) return;
        if (currentSelection == null) {
            currentSelection = new boolean[activeQueueModels.size()];
            Arrays.fill(currentSelection, true);
        }
        for (int i = 0; i < activeQueueModels.size(); i++) {
            ModelInfo m = activeQueueModels.get(i);
            String key = resolveModelKey(m);
            if (filename.equalsIgnoreCase(key) && i < currentSelection.length) {
                currentSelection[i] = false;
                safeUpdateStatus(i, "Stopped", null);
                break;
            }
        }
    }

    @Override
    public void addProgressListener(DownloadProgressListener listener) {
        if (listener != null && !progressListeners.contains(listener)) {
            progressListeners.add(listener);
        }
    }

    @Override
    public void removeProgressListener(DownloadProgressListener listener) {
        if (listener != null) {
            progressListeners.remove(listener);
        }
    }

    private void notifyProgressListeners(String filename, String status) {
        for (DownloadProgressListener listener : progressListeners) {
            try {
                listener.onProgress(filename, status);
            } catch (Exception ex) {
                logger.debug("Failed notifying download progress listener: {}", ex.getMessage());
            }
        }
    }

    private String resolveModelKey(ModelInfo info) {
        if (info == null) return null;
        if (info.getFilename() != null && !info.getFilename().isBlank()) {
            return info.getFilename().trim();
        }
        return info.getName() != null ? info.getName().trim() : null;
    }

    @Override public void notifyComfyUI() { notifyComfyUI(false); }
    @Override public void notifyComfyUI(boolean forceReload) {
        String url = configService != null ? configService.getComfyUIUrl() : ConfigConstants.DEFAULT_COMFYUI_URL;
        if (sendPing(url, forceReload)) return;
        for (int p : new int[]{8188, 8189, 8190, 8000, 3000, 8080}) {
            String test = "http://127.0.0.1:" + p;
            if (checkPort(test)) {
                if (configService != null) configService.setComfyUIUrl(test);
                sendPing(test, forceReload);
                return;
            }
        }
    }
    private boolean sendPing(String url, boolean reload) {
        try {
            return httpClient.send(HttpRequest.newBuilder().uri(URI.create(url + "/cmfc/refresh-models")).timeout(Duration.ofSeconds(2))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(de.tki.comfyuicompanion.dto.acl.RefreshModelsAclRequest.of(reload).toJson())).build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode() == 200;
        } catch (Exception ignored) { return false; }
    }
    private boolean checkPort(String url) {
        try {
            return httpClient.send(HttpRequest.newBuilder().uri(URI.create(url + "/system_stats")).timeout(Duration.ofMillis(300)).GET().build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode() == 200;
        } catch (Exception ignored) { return false; }
    }
    @Override public synchronized void updateSelection(boolean[] s) { this.currentSelection = s != null ? Arrays.copyOf(s, s.length) : null; }
    private synchronized boolean isSelected(int idx) { return currentSelection == null || (idx < currentSelection.length && currentSelection[idx]); }
    @Override public synchronized void togglePause() { isPaused = !isPaused; }
    @Override public void stop() {
        isStopped = true;
        isPaused = false;
        queueRunning.set(false);
        for (int i = 0; i < activeQueueModels.size(); i++) {
            if (!completedIndices.contains(i)) {
                safeUpdateStatus(i, "Stopped", null);
            }
        }
    }
    @Override public boolean isPaused() { return isPaused; }

    @PreDestroy
    public void shutdown() {
        try {
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
            if (segmentedEngine != null) segmentedEngine.shutdown();
        } catch (Exception ignored) {
            logger.debug("Ignored non-fatal exception: {}", ignored.getMessage());
        }
    }
}
