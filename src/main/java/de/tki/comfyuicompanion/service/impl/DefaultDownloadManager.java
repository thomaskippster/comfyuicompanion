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

    /** Primary constructor with all dependencies injected. */
    @Autowired
    public DefaultDownloadManager(ConfigService cs, PathResolver pr, CivitaiService civ, ModelHashRegistry hr,
                                  IModelValidator mv, @Autowired(required = false) SafePathValidator spv,
                                  SegmentedDownloadEngine se, DownloadChecksumVerifier cv) {
        this.configService = cs; this.pathResolver = pr; this.civitaiService = civ; this.hashRegistry = hr; this.modelValidator = mv;
        this.safePathValidator = spv != null ? spv : new SafePathValidator();
        this.segmentedEngine = se != null ? se : new SegmentedDownloadEngine(cs);
        this.checksumVerifier = cv != null ? cv : new DownloadChecksumVerifier(hr, mv);
    }

    /** Overloaded constructor for callers without extracted components. */
    public DefaultDownloadManager(ConfigService cs, PathResolver pr, CivitaiService civ, ModelHashRegistry hr, IModelValidator mv, SafePathValidator spv) {
        this(cs, pr, civ, hr, mv, spv, null, null);
    }

    /** Backward-compatible constructor for testing. */
    public DefaultDownloadManager(ConfigService cs, PathResolver pr, CivitaiService civ, ModelHashRegistry hr, IModelValidator mv) {
        this(cs, pr, civ, hr, mv, null, null, null);
    }

    private void safeUpdateStatus(int index, String status, BiConsumer<Integer, String> statusUpdater) {
        statusMap.put(index, status);
        if (status.contains("✅ Finished") || status.contains("Stopped") || status.contains("Skipped") || status.contains("Already exists") || status.startsWith("❌ Error")) {
            completedIndices.add(index);
        }
        if (statusUpdater != null) statusUpdater.accept(index, status);
    }

    @Override public Map<Integer, String> getQueueStatus() { return new HashMap<>(statusMap); }

    @Override
    public void startQueue(List<ModelInfo> models, boolean[] selectedIndices, String baseDir,
                           BiConsumer<Integer, String> statusUpdater, Runnable onFinished) {
        isStopped = false; isPaused = false;
        this.currentSelection = selectedIndices != null ? Arrays.copyOf(selectedIndices, selectedIndices.length) : null;
        completedIndices.clear(); statusMap.clear();
        int max = configService != null ? configService.getMaxParallelDownloads() : 3;
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
                        futures[idx] = CompletableFuture.runAsync(() -> processModelDownload(models.get(idx), idx, baseDir, statusUpdater), executor);
                    }
                }
                CompletableFuture.allOf(futures).join();
            } finally {
                if (finished.compareAndSet(false, true) && onFinished != null) onFinished.run();
            }
        });
    }

    private void processModelDownload(ModelInfo info, int index, String baseDir, BiConsumer<Integer, String> statusUpdater) {
        if (isStopped || !isSelected(index)) return;
        try {
            String sub = (info.getSave_path() == null || info.getSave_path().isEmpty() || "default".equalsIgnoreCase(info.getSave_path())) ?
                    ModelFolder.fromString(info.getType()).getDefaultFolderName() : info.getSave_path();
            if (sub.startsWith("models/") || sub.startsWith("models\\")) {
                sub = sub.substring(7);
            }
            Path base = (baseDir != null && !baseDir.isEmpty()) ? pathResolver.resolve(baseDir) : pathResolver.resolve(configService != null ? configService.getModelsPath() : null);
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
            long existingSize = (file.exists() && !inArchive) ? file.length() : 0;
            String url = appendCivitaiTokenIfNeeded(info.getUrl());
            checkDiskSpace(targetFile, 0, index, statusUpdater);
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
            if (checkDiskSpace(targetFile, totalRemoteSize, index, statusUpdater)) return;

            if (existingSize > 0 && totalRemoteSize > 0 && existingSize == totalRemoteSize) {
                if (modelValidator != null && !modelValidator.validateFile(file).ok) {
                    if (retry < 1) { file.delete(); safeUpdateStatus(index, "🔄 Fixing Corrupted File...", statusUpdater); downloadWithResume(info, targetFile, index, statusUpdater, retry + 1); return; }
                    safeUpdateStatus(index, "❌ Validation failed: " + modelValidator.validateFile(file).message, statusUpdater); return;
                }
                safeUpdateStatus(index, "✅ Already exists", statusUpdater); return;
            }

            if (waitForPauseAndCheckSelection(index, statusUpdater)) return;
            int segments = configService != null ? configService.getSegmentsPerFile() : 1;
            String fileName = (info.getFilename() != null && !info.getFilename().isBlank()) ? info.getFilename() : info.getName();
            if (segmentedEngine.download(fileName, url, targetFile, totalRemoteSize, segments, ctrl(index), s -> safeUpdateStatus(index, s, statusUpdater))) {
                if (modelValidator != null) {
                    var val = modelValidator.validateFile(targetFile.toFile());
                    if (!val.ok) { targetFile.toFile().delete(); safeUpdateStatus(index, "❌ Validation failed: " + val.message, statusUpdater); return; }
                }
                checksumVerifier.registerModelHash(targetFile.toFile());
                if (civitaiService != null) civitaiService.downloadMetadataAndPreview(info.getUrl(), targetFile);
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

    private boolean checkDiskSpace(Path target, long req, int idx, BiConsumer<Integer, String> su) {
        try {
            long f = Files.getFileStore(target.getParent().getRoot()).getUsableSpace();
            if (req > 0 && f < (req + 1073741824L)) { safeUpdateStatus(idx, "❌ No Space (" + SegmentedDownloadEngine.formatSize(f) + " free < " + SegmentedDownloadEngine.formatSize(req) + " required)", su); return true; }
            if (req <= 0 && f < 15L * 1024 * 1024 * 1024) safeUpdateStatus(idx, "⚠️ Low Disk Space (" + SegmentedDownloadEngine.formatSize(f) + " free)", su);
        } catch (Exception ignored) {}
        return false;
    }

    private void handleDownloadError(Exception e, ModelInfo info, Path target, int idx, BiConsumer<Integer, String> su, int retry) {
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        if (isStopped || !isSelected(idx) || Thread.currentThread().isInterrupted()) {
            safeUpdateStatus(idx, !isSelected(idx) ? "Skipped (Unchecked)" : "Stopped", su);
        } else if (retry < 10 && isRetriableException(e)) {
            long delay = Math.min(30000L, 1000L * (1L << Math.min(retry, 4)));
            String msg = (e instanceof SegmentedDownloadEngine.PrematureStreamEndException pse) ?
                    "🔄 Stream ended (" + SegmentedDownloadEngine.formatSize(pse.getDownloaded()) + "/" + SegmentedDownloadEngine.formatSize(pse.getTotal()) + "), resuming (" + (retry + 1) + "/10)..." :
                    "🔄 Retrying in " + (delay / 1000) + "s (" + (retry + 1) + "/10)...";
            safeUpdateStatus(idx, msg, su);
            try { Thread.sleep(delay); } catch (InterruptedException ignored) {}
            downloadWithResume(info, target, idx, su, retry + 1);
        } else {
            safeUpdateStatus(idx, "❌ Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), su);
        }
    }

    private boolean isRetriableException(Exception e) {
        return !(e instanceof InterruptedException || e instanceof java.net.ConnectException || e instanceof java.net.UnknownHostException)
                && (e.getMessage() == null || (!e.getMessage().contains("Connection refused") && !e.getMessage().contains("401") && !e.getMessage().contains("403") && !e.getMessage().contains("404")));
    }

    private String appendCivitaiTokenIfNeeded(String url) {
        String key = (url != null && url.contains("civitai.com") && configService != null) ? configService.getCivitaiApiKey() : null;
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
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{\"force_reload\": " + reload + "}")).build(),
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
    @Override public void stop() { isStopped = true; isPaused = false; }
    @Override public boolean isPaused() { return isPaused; }

    @PreDestroy
    public void shutdown() {
        try {
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
            if (segmentedEngine != null) segmentedEngine.shutdown();
        } catch (Exception ignored) {}
    }
}