package de.tki.comfymodels.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.IDownloadManager;
import de.tki.comfymodels.service.IModelValidator;
import de.tki.comfymodels.service.SafePathValidator;
import de.tki.comfymodels.util.ConfigConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URI;
import java.time.Duration;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

@Service
public class DefaultDownloadManager implements IDownloadManager {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DefaultDownloadManager.class);
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            3, 3, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>()
    );
    private final ExecutorService segmentExecutor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("Download-Segment-", 0).factory()
    );
    private final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(60)).build();
    private volatile boolean isPaused = false;
    private volatile boolean isStopped = false;
    private volatile boolean[] currentSelection;
    private final java.util.Set<Integer> completedIndices = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Map<Integer, String> statusMap = new java.util.concurrent.ConcurrentHashMap<>();

    private final ConfigService configService;
    private final PathResolver pathResolver;
    private final CivitaiService civitaiService;
    private final ModelHashRegistry hashRegistry;
    private final IModelValidator modelValidator;
    private final SafePathValidator safePathValidator;

    @Autowired
    public DefaultDownloadManager(ConfigService configService,
                                  PathResolver pathResolver,
                                  CivitaiService civitaiService,
                                  ModelHashRegistry hashRegistry,
                                  IModelValidator modelValidator,
                                  @Autowired(required = false) SafePathValidator safePathValidator) {
        this.configService = configService;
        this.pathResolver = pathResolver;
        this.civitaiService = civitaiService;
        this.hashRegistry = hashRegistry;
        this.modelValidator = modelValidator;
        this.safePathValidator = safePathValidator != null ? safePathValidator : new SafePathValidator();
    }

    public DefaultDownloadManager(ConfigService configService,
                                  PathResolver pathResolver,
                                  CivitaiService civitaiService,
                                  ModelHashRegistry hashRegistry,
                                  IModelValidator modelValidator) {
        this(configService, pathResolver, civitaiService, hashRegistry, modelValidator, new SafePathValidator());
    }

    private void safeUpdateStatus(int index, String status, BiConsumer<Integer, String> statusUpdater) {
        statusMap.put(index, status);
        // Only mark as completed if it's a final state
        boolean isFinal = status.contains("✅ Finished") || 
                         status.contains("Stopped") || 
                         status.contains("Skipped") || 
                         status.contains("Already exists") ||
                         status.startsWith("❌ Error");
        
        if (isFinal) {
            completedIndices.add(index);
        }
        statusUpdater.accept(index, status);
    }

    @Override
    public java.util.Map<Integer, String> getQueueStatus() {
        return new java.util.HashMap<>(statusMap);
    }

    @Override
    public void startQueue(List<ModelInfo> models, boolean[] selectedIndices, String baseDir, BiConsumer<Integer, String> statusUpdater, Runnable onFinished) {
        isStopped = false;
        isPaused = false;
        this.currentSelection = selectedIndices != null ? java.util.Arrays.copyOf(selectedIndices, selectedIndices.length) : null;
        completedIndices.clear();
        statusMap.clear();

        // Dynamically adjust parallel download thread count
        int maxParallel = configService != null ? configService.getMaxParallelDownloads() : 3;
        if (executor.getMaximumPoolSize() < maxParallel) {
            executor.setMaximumPoolSize(maxParallel);
            executor.setCorePoolSize(maxParallel);
        } else {
            executor.setCorePoolSize(maxParallel);
            executor.setMaximumPoolSize(maxParallel);
        }
        
        java.util.concurrent.atomic.AtomicBoolean finishedCalled = new java.util.concurrent.atomic.AtomicBoolean(false);
        
        Thread.ofVirtual().name("Download-Queue-Worker").unstarted(() -> {
            try {
                int size = models.size();
                java.util.concurrent.CompletableFuture<?>[] futures = new java.util.concurrent.CompletableFuture[size];
                
                for (int i = 0; i < size; i++) {
                    final int index = i;
                    
                    if (isStopped) {
                        futures[index] = java.util.concurrent.CompletableFuture.completedFuture(null);
                        continue;
                    }

                    if (!isSelected(index)) { 
                        safeUpdateStatus(index, "Skipped (Not Selected)", statusUpdater);
                        futures[index] = java.util.concurrent.CompletableFuture.completedFuture(null);
                        continue;
                    }

                    ModelInfo info = models.get(index);
                    futures[index] = java.util.concurrent.CompletableFuture.runAsync(() -> {
                        if (isStopped || !isSelected(index)) return;
                        
                        try {
                            String subPath = info.getSave_path();
                            if (subPath == null || subPath.isEmpty() || "default".equalsIgnoreCase(subPath)) {  
                                subPath = de.tki.comfymodels.domain.ModelFolder.fromString(info.getType()).getDefaultFolderName();
                            }

                            Path modelsBase = (baseDir != null && !baseDir.isEmpty()) ? 
                                    pathResolver.resolve(baseDir) : 
                                    pathResolver.resolve(configService != null ? configService.getModelsPath() : null);
                            Path targetDir = modelsBase.resolve(subPath);
                            Path targetFile = targetDir.resolve(info.getName());
                            if (safePathValidator != null) {
                                safePathValidator.validateWithinBase(modelsBase, targetFile);
                            }
                            Files.createDirectories(targetDir);
                            downloadWithResume(info, targetFile, index, statusUpdater);  
                        } catch (Exception e) { 
                            if (!isStopped && isSelected(index)) {
                                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                                safeUpdateStatus(index, "❌ Error: " + msg, statusUpdater);
                            }
                        }
                    }, executor);
                }
                
                // Wait for all tasks to complete (including skipped ones)
                try {
                    java.util.concurrent.CompletableFuture.allOf(futures).join();
                } catch (Exception ignored) {}
                
            } finally {
                if (finishedCalled.compareAndSet(false, true)) {
                    onFinished.run();
                }
            }
        }).start();
    }

    // NEU: Benachrichtigt ComfyUI
    @Override
    public void notifyComfyUI() {
        notifyComfyUI(false);
    }

    @Override
    public void notifyComfyUI(boolean forceReload) {
        String comfyUrl = configService != null ? configService.getComfyUIUrl() : ConfigConstants.DEFAULT_COMFYUI_URL;
        if (sendRefreshPing(comfyUrl, forceReload)) {
            return;
        }

        // Auto-Discovery: If configured URL fails, search for ComfyUI on common ports
        logger.info("🔍 [Companion] ComfyUI not reached at " + comfyUrl + ". Searching automatically...");
        String discoveredUrl = discoverComfyUrl();
        if (discoveredUrl != null) {
            logger.info("✨ [Companion] ComfyUI automatically found at: " + discoveredUrl);
            if (configService != null) configService.setComfyUIUrl(discoveredUrl);
            sendRefreshPing(discoveredUrl, forceReload);
        } else {
            logger.error("❌ [Companion] ComfyUI could not be found automatically. Please make sure it is running.");
        }
    }

    private boolean sendRefreshPing(String url, boolean forceReload) {
        try {
            String json = "{\"force_reload\": " + forceReload + "}";
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "/cmfc/refresh-models"))
                .timeout(java.time.Duration.ofSeconds(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                logger.info("✅ [Companion] ComfyUI (" + url + ") successfully notified (Force: " + forceReload + ").");
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private String discoverComfyUrl() {
        // Scan typical ComfyUI ports, prioritizing 8188
        int[] commonPorts = {8188, 8189, 8190, 8000, 3000, 8080};
        for (int port : commonPorts) {
            if (checkPort(port)) return "http://127.0.0.1:" + port;
        }

        // Expanded range scan
        for (int port = 8191; port <= 8199; port++) {
            if (checkPort(port)) return "http://127.0.0.1:" + port;
        }
        return null;
    }

    private boolean checkPort(int port) {
        String url = "http://127.0.0.1:" + port;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "/system_stats"))
                .timeout(java.time.Duration.ofMillis(300))
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && response.body().contains("system");
        } catch (Exception ignored) {}
        return false;
    }

    private synchronized boolean isSelected(int index) {
        return currentSelection == null || (index < currentSelection.length && currentSelection[index]);
    }

    @Override
    public synchronized void updateSelection(boolean[] selectedIndices) {
        this.currentSelection = selectedIndices != null ? java.util.Arrays.copyOf(selectedIndices, selectedIndices.length) : null;
    }

    private String appendCivitaiTokenIfNeeded(String url) {
        if (url != null && url.contains("civitai.com")) {
            String apiKey = configService != null ? configService.getCivitaiApiKey() : null;
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                if (url.contains("?")) {
                    return url + "&token=" + apiKey.trim();
                } else {
                    return url + "?token=" + apiKey.trim();
                }
            }
        }
        return url;
    }

    private void downloadWithResume(ModelInfo info, Path targetFile, int index, BiConsumer<Integer, String> statusUpdater) {
        downloadWithResumeInternal(info, targetFile, index, statusUpdater, 0);
    }

    private void downloadWithResumeInternal(ModelInfo info, Path targetFile, int index, BiConsumer<Integer, String> statusUpdater, int retryCount) {
        try {
            if (waitForPauseAndCheckSelection(index, statusUpdater)) return;

            File file = targetFile.toFile();
            String archivePath = configService != null ? configService.getArchivePath() : null;
            boolean isActuallyInArchive = archivePath != null && !archivePath.isEmpty() && 
                                         file.getAbsolutePath().toLowerCase().startsWith(new File(archivePath).getAbsolutePath().toLowerCase());

            String hfToken = configService != null ? configService.getHfToken() : null;
            String downloadUrl = appendCivitaiTokenIfNeeded(info.getUrl());

            // Check disk space before starting (15GB buffer warning)
            try {
                long usableSpace = Files.getFileStore(targetFile.getParent().getRoot()).getUsableSpace();
                if (usableSpace < 15L * 1024 * 1024 * 1024) {
                    safeUpdateStatus(index, "⚠️ Low Disk Space (" + formatSize(usableSpace) + " free)", statusUpdater);
                }
            } catch (Exception ignored) {}

            if (file.exists() && (file.length() == 0 || isActuallyInArchive)) {
                // If it's in the archive, we don't treat it as "local existing model" for the download manager.
                if (!isActuallyInArchive) file.delete();
            }

            long existingFileSize = (file.exists() && !isActuallyInArchive) ? file.length() : 0;
            
            Path partFile = targetFile.resolveSibling(targetFile.getFileName().toString() + ".cmfd");
            File pFile = partFile.toFile();
            long existingPartSize = pFile.exists() ? pFile.length() : 0;

            if (waitForPauseAndCheckSelection(index, statusUpdater)) return;
            
            HttpRequest.Builder headBuilder = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .method("HEAD", HttpRequest.BodyPublishers.noBody());
                
            if (downloadUrl.contains("huggingface.co") && hfToken != null && !hfToken.isEmpty()) {
                headBuilder.header("Authorization", "Bearer " + hfToken);
            }

            HttpResponse<Void> headResponse = httpClient.send(headBuilder.timeout(Duration.ofSeconds(60)).build(), HttpResponse.BodyHandlers.discarding());
            
            if (headResponse.statusCode() == 401 || headResponse.statusCode() == 403) {
                safeUpdateStatus(index, "❌ Auth Required (Token?)", statusUpdater);
                return;
            }

            long totalRemoteSize = headResponse.headers().firstValueAsLong("Content-Length").orElse(0L);

            // Double Check Disk Space against total size (+1GB safety margin)
            try {
                long usableSpace = Files.getFileStore(targetFile.getParent().getRoot()).getUsableSpace();
                if (totalRemoteSize > 0 && usableSpace < (totalRemoteSize + 1073741824L)) {
                    safeUpdateStatus(index, "❌ No Space (" + formatSize(usableSpace) + " free < " + formatSize(totalRemoteSize) + " required)", statusUpdater);
                    return;
                }
            } catch (Exception ignored) {}

            if (existingFileSize > 0 && totalRemoteSize > 0 && existingFileSize == totalRemoteSize) {
                // Verification of existing file using modelValidator
                if (modelValidator != null) {
                    IModelValidator.ValidationResult valResult = modelValidator.validateFile(file);
                    if (!valResult.ok) {
                        if (retryCount < 1) {
                            safeUpdateStatus(index, "🔄 Fixing Corrupted File...", statusUpdater);
                            file.delete();
                            downloadWithResumeInternal(info, targetFile, index, statusUpdater, retryCount + 1);
                            return;
                        } else {
                            safeUpdateStatus(index, "❌ Validation failed: " + valResult.message, statusUpdater);
                            return;
                        }
                    }
                }
                safeUpdateStatus(index, "✅ Already exists", statusUpdater);
                return;
            }

            if (waitForPauseAndCheckSelection(index, statusUpdater)) return;

            // Multi-segment download is disabled: many CDN providers (Civitai, HuggingFace mirrors)
            // do not reliably support Range requests (returning 200 instead of 206), which causes
            // corrupt merged files, stale .partN temp files, and CDN rate-limiting from parallel
            // connections. The single-segment path below already handles resume, retry and validation.
            int segments = 1; // configService != null ? configService.getSegmentsPerFile() : 1;

            downloadSingleSegment(info, targetFile, index, statusUpdater, totalRemoteSize, downloadUrl, retryCount, pFile, partFile, existingPartSize);

        } catch (Exception e) {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            
            if (isStopped || !isSelected(index) || Thread.currentThread().isInterrupted()) {
                safeUpdateStatus(index, !isSelected(index) ? "Skipped (Unchecked)" : "Stopped", statusUpdater);
            } else {
                if (retryCount < 10 && isRetriableException(e)) {
                    long delayMs = Math.min(30000L, 1000L * (1L << Math.min(retryCount, 4))); // 1s, 2s, 4s, 8s, 16s... up to 30s
                    logger.warn("Download interrupted for " + info.getName() + " (" + e.getMessage() + "). Retrying in " + (delayMs / 1000) + "s with resume (" + (retryCount + 1) + "/10)...");
                    safeUpdateStatus(index, "🔄 Retrying in " + (delayMs / 1000) + "s (" + (retryCount + 1) + "/10)...", statusUpdater);
                    try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
                    downloadWithResumeInternal(info, targetFile, index, statusUpdater, retryCount + 1);
                    return;
                }
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                safeUpdateStatus(index, "Error: " + msg, statusUpdater);
            }
        }
    }

    private boolean isRetriableException(Exception e) {
        if (e instanceof InterruptedException) return false;
        if (e instanceof java.net.ConnectException) return false;
        if (e instanceof java.net.UnknownHostException) return false;
        String msg = e.getMessage();
        if (msg != null && (msg.contains("Connection refused") || msg.contains("401") || msg.contains("403") || msg.contains("404"))) {
            return false;
        }
        return true;
    }

    private void downloadSingleSegment(ModelInfo info, Path targetFile, int index, BiConsumer<Integer, String> statusUpdater,
                                       long totalRemoteSize, String downloadUrl, int retryCount, File pFile, Path partFile, long existingPartSize) throws Exception {
        String hfToken = configService != null ? configService.getHfToken() : null;
        HttpRequest.Builder downloadBuilder = HttpRequest.newBuilder()
            .uri(URI.create(downloadUrl))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        if (downloadUrl.contains("huggingface.co") && hfToken != null && !hfToken.isEmpty()) {
            downloadBuilder.header("Authorization", "Bearer " + hfToken);
        }

        if (existingPartSize > 0) downloadBuilder.header("Range", "bytes=" + existingPartSize + "-");

        HttpResponse<InputStream> response = httpClient.send(downloadBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
        int statusCode = response.statusCode();

        if (statusCode == 416) { 
             if (existingPartSize == totalRemoteSize) {
                 Files.move(partFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                 
                 // Deep validation using modelValidator
                 if (modelValidator != null) {
                     IModelValidator.ValidationResult valResult = modelValidator.validateFile(targetFile.toFile());
                     if (!valResult.ok) {
                         targetFile.toFile().delete(); // Clean up corrupted file
                         safeUpdateStatus(index, "❌ Validation failed: " + valResult.message, statusUpdater);
                         return;
                     }
                 }
                 
                 // Pre-compute and register the file hash in registry cache
                 if (hashRegistry != null) {
                     hashRegistry.getOrCalculateHash(targetFile.toFile());
                 }
                 
                 onDownloadComplete(info, targetFile);
                 safeUpdateStatus(index, "✅ Finished", statusUpdater);
             } else {
                 safeUpdateStatus(index, "✅ Already exists", statusUpdater);
             }
             return;
        }

        if (statusCode == 401 || statusCode == 403) {
            safeUpdateStatus(index, "❌ Auth Required (Token?)", statusUpdater);
            return;
        }

        long contentLen = response.headers().firstValueAsLong("Content-Length").orElse(0L);
        long totalBytes = (statusCode == 206) ? contentLen + existingPartSize : contentLen;

        if (totalBytes > 0 && totalBytes < 5000 && info.getName().endsWith(".safetensors")) {
            safeUpdateStatus(index, "❌ LFS Stub detected (Token?)", statusUpdater);
            return;
        }

        if (statusCode == 200) existingPartSize = 0;

        long speedLimitKb = configService != null ? configService.getDownloadSpeedLimit() : 0;
        long limitBytesPerSec = speedLimitKb * 1024L;
        long startTime = System.currentTimeMillis();
        long bytesWrittenInWindow = 0;
        long lastUpdate = 0;
        long lastSpeedTime = System.currentTimeMillis();
        long lastDownloaded = existingPartSize;

        try (InputStream is = response.body(); RandomAccessFile raf = new RandomAccessFile(pFile, "rw")) {       
            raf.seek(existingPartSize);
            byte[] buffer = new byte[262144]; // 256 KB buffer for high-throughput disk I/O on 40GB+ models
            long downloaded = existingPartSize;
            int read;
            while ((read = is.read(buffer)) != -1) {
                if (isStopped || !isSelected(index) || Thread.currentThread().isInterrupted()) {
                    safeUpdateStatus(index, !isSelected(index) ? "Skipped (Unchecked)" : "Stopped", statusUpdater);
                    return;
                }

                if (isPaused) {
                    if (waitForPauseAndCheckSelection(index, statusUpdater)) return;
                    safeUpdateStatus(index, "Resuming...", statusUpdater);
                }

                raf.write(buffer, 0, read);
                downloaded += read;
                bytesWrittenInWindow += read;

                if (limitBytesPerSec > 0) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    long expectedTime = (bytesWrittenInWindow * 1000L) / limitBytesPerSec;
                    if (expectedTime > elapsed) {
                        long sleepTime = expectedTime - elapsed;
                        if (sleepTime > 0) {
                            try {
                                Thread.sleep(sleepTime);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    }
                    if (elapsed > 5000) {
                        startTime = System.currentTimeMillis();
                        bytesWrittenInWindow = 0;
                    }
                }
                
                long now = System.currentTimeMillis();
                if (now - lastUpdate > 800) {
                    long downloadedInWindow = downloaded - lastDownloaded;
                    long timeInWindow = now - lastSpeedTime;
                    double speedMBs = timeInWindow > 0 ? (downloadedInWindow / (1024.0 * 1024.0)) / (timeInWindow / 1000.0) : 0.0;
                    String speedStr = speedMBs >= 1.0 ? String.format(java.util.Locale.US, "%.1f MB/s", speedMBs) : String.format(java.util.Locale.US, "%.0f KB/s", speedMBs * 1024);
                    
                    String etaStr = "";
                    if (totalBytes > downloaded && speedMBs > 0.02) {
                        long remainingBytes = totalBytes - downloaded;
                        long secondsLeft = (long) (remainingBytes / (speedMBs * 1024 * 1024));
                        if (secondsLeft > 3600) {
                            etaStr = " (ETA: " + (secondsLeft / 3600) + "h " + ((secondsLeft % 3600) / 60) + "m)";
                        } else if (secondsLeft > 60) {
                            etaStr = " (ETA: " + (secondsLeft / 60) + "m " + (secondsLeft % 60) + "s)";
                        } else {
                            etaStr = " (ETA: " + secondsLeft + "s)";
                        }
                    }

                    String progressPercent = totalBytes > 0 ? (downloaded * 100 / totalBytes) + "%" : "?%";
                    String sizeProgress = totalBytes > 0 ? formatSize(downloaded) + " / " + formatSize(totalBytes) : formatSize(downloaded);
                    safeUpdateStatus(index, "Downloading: " + progressPercent + " (" + sizeProgress + ") - " + speedStr + etaStr, statusUpdater);
                    lastUpdate = now;
                    lastSpeedTime = now;
                    lastDownloaded = downloaded;
                }
            }
        }
        
        if (isStopped || !isSelected(index)) {
            safeUpdateStatus(index, !isSelected(index) ? "Skipped (Unchecked)" : "Stopped", statusUpdater);
        } else {
            long finalSize = pFile.length();
            boolean sizeMismatch = totalBytes > 0 && finalSize < totalBytes;

            if (sizeMismatch && retryCount < 10) {
                long delayMs = Math.min(30000L, 1000L * (1L << Math.min(retryCount, 4)));
                logger.warn("Download prematurely ended for " + info.getName() + " (" + formatSize(finalSize) + " of " + formatSize(totalBytes) + "). Resuming in " + (delayMs / 1000) + "s (" + (retryCount + 1) + "/10)...");
                safeUpdateStatus(index, "🔄 Stream ended (" + formatSize(finalSize) + "/" + formatSize(totalBytes) + "), resuming (" + (retryCount + 1) + "/10)...", statusUpdater);
                try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
                downloadWithResumeInternal(info, targetFile, index, statusUpdater, retryCount + 1);
            } else if (sizeMismatch) {
                safeUpdateStatus(index, "❌ Incomplete (" + formatSize(finalSize) + "/" + formatSize(totalBytes) + ") - Resumable", statusUpdater);
                // Do NOT delete pFile! Leave .cmfd intact so it can be resumed
            } else {
                Files.move(partFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                
                // Deep validation using modelValidator
                if (modelValidator != null) {
                    IModelValidator.ValidationResult valResult = modelValidator.validateFile(targetFile.toFile());
                    if (!valResult.ok) {
                        targetFile.toFile().delete(); // Clean up corrupted file
                        safeUpdateStatus(index, "❌ Validation failed: " + valResult.message, statusUpdater);
                        return;
                    }
                }
                
                // Pre-compute and register the file hash in registry cache
                if (hashRegistry != null) {
                    hashRegistry.getOrCalculateHash(targetFile.toFile());
                }
                
                onDownloadComplete(info, targetFile);
                safeUpdateStatus(index, "✅ Finished", statusUpdater);
            }
        }
    }

    private void downloadMultiSegment(ModelInfo info, Path targetFile, int index, BiConsumer<Integer, String> statusUpdater, long totalRemoteSize, String downloadUrl) throws Exception {
        int numSegments = configService != null ? configService.getSegmentsPerFile() : 4;
        if (numSegments <= 1 || totalRemoteSize <= 100 * 1024 * 1024) {
            Path partFile = targetFile.resolveSibling(targetFile.getFileName().toString() + ".cmfd");
            downloadSingleSegment(info, targetFile, index, statusUpdater, totalRemoteSize, downloadUrl, 0, partFile.toFile(), partFile, 0);
            return;
        }

        long segmentSize = totalRemoteSize / numSegments;
        List<CompletableFuture<Void>> segmentFutures = new ArrayList<>();
        
        Path[] partFiles = new Path[numSegments];
        long[] startBytes = new long[numSegments];
        long[] endBytes = new long[numSegments];
        
        long initialDownloaded = 0;
        for (int i = 0; i < numSegments; i++) {
            partFiles[i] = targetFile.resolveSibling(targetFile.getFileName().toString() + ".part" + i);
            startBytes[i] = i * segmentSize;
            endBytes[i] = (i == numSegments - 1) ? totalRemoteSize - 1 : (startBytes[i] + segmentSize - 1);
            
            File pFile = partFiles[i].toFile();
            if (pFile.exists()) {
                initialDownloaded += pFile.length();
            }
        }
        
        java.util.concurrent.atomic.AtomicLong totalDownloadedBytes = new java.util.concurrent.atomic.AtomicLong(initialDownloaded);
        String hfToken = configService != null ? configService.getHfToken() : null;
        java.util.concurrent.atomic.AtomicBoolean segmentFailed = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicReference<String> errorMessage = new java.util.concurrent.atomic.AtomicReference<>("");

        for (int i = 0; i < numSegments; i++) {
            final int segmentIdx = i;
            final long start = startBytes[i];
            final long end = endBytes[i];
            final Path partFile = partFiles[i];
            
            segmentFutures.add(CompletableFuture.runAsync(() -> {
                try {
                    File pFile = partFile.toFile();
                    long existingSize = pFile.exists() ? pFile.length() : 0;
                    long segmentTotalSize = end - start + 1;
                    
                    if (existingSize >= segmentTotalSize) {
                        return;
                    }
                    
                    if (isStopped || segmentFailed.get()) return;
                    
                    long rangeStart = start + existingSize;
                    
                    HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(downloadUrl))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .header("Range", "bytes=" + rangeStart + "-" + end);
                    
                    if (downloadUrl.contains("huggingface.co") && hfToken != null && !hfToken.isEmpty()) {
                        reqBuilder.header("Authorization", "Bearer " + hfToken);
                    }
                    
                    HttpResponse<InputStream> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
                    int status = response.statusCode();
                    
                    if (status != 206 && (status != 200 || rangeStart != start)) {
                        throw new IOException("Server returned status " + status + " instead of expected partial content (206) for segment " + segmentIdx);
                    }
                    
                    long speedLimitKb = configService != null ? configService.getDownloadSpeedLimit() : 0;
                    long segmentSpeedLimitBytesSec = speedLimitKb > 0 ? (speedLimitKb * 1024L) / numSegments : 0;
                    long startTime = System.currentTimeMillis();
                    long bytesWrittenInWindow = 0;
                    
                    try (InputStream is = response.body(); RandomAccessFile raf = new RandomAccessFile(pFile, "rw")) {
                        raf.seek(existingSize);
                        byte[] buffer = new byte[16384];
                        int read;
                        while ((read = is.read(buffer)) != -1) {
                            if (isStopped || segmentFailed.get() || !isSelected(index)) {
                                return;
                            }
                            
                            while (isPaused && !isStopped && isSelected(index)) {
                                Thread.sleep(200);
                            }
                            
                            raf.write(buffer, 0, read);
                            totalDownloadedBytes.addAndGet(read);
                            bytesWrittenInWindow += read;
                            
                            if (segmentSpeedLimitBytesSec > 0) {
                                long elapsed = System.currentTimeMillis() - startTime;
                                long expectedTime = (bytesWrittenInWindow * 1000L) / segmentSpeedLimitBytesSec;
                                if (expectedTime > elapsed) {
                                    long sleep = expectedTime - elapsed;
                                    if (sleep > 0) Thread.sleep(sleep);
                                }
                                if (elapsed > 5000) {
                                    startTime = System.currentTimeMillis();
                                    bytesWrittenInWindow = 0;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    segmentFailed.set(true);
                    errorMessage.set(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                }
            }, segmentExecutor));
        }
        
        long lastUpdate = 0;
        while (!segmentFutures.stream().allMatch(CompletableFuture::isDone)) {
            if (isStopped || !isSelected(index)) {
                segmentFailed.set(true);
                break;
            }
            
            long now = System.currentTimeMillis();
            if (now - lastUpdate > 800) {
                long currentDownloaded = totalDownloadedBytes.get();
                if (currentDownloaded > totalRemoteSize) currentDownloaded = totalRemoteSize;
                safeUpdateStatus(index, "Downloading (Segmented): " + 
                    (totalRemoteSize > 0 ? (currentDownloaded * 100 / totalRemoteSize) : "?") + 
                    "% (" + formatSize(currentDownloaded) + ")", statusUpdater);
                lastUpdate = now;
            }
            Thread.sleep(200);
        }
        
        try {
            CompletableFuture.allOf(segmentFutures.toArray(new CompletableFuture[0])).join();
        } catch (Exception ignored) {}
        
        if (segmentFailed.get()) {
            if (!isStopped && isSelected(index)) {
                // Delete all part files on actual failure (not stopped or unselected)
                for (int i = 0; i < numSegments; i++) {
                    try { Files.deleteIfExists(partFiles[i]); } catch (Exception ignored) {}
                }
            }
            throw new IOException("Segment download failed: " + errorMessage.get());
        }
        
        if (isStopped || !isSelected(index)) {
            safeUpdateStatus(index, !isSelected(index) ? "Skipped (Unchecked)" : "Stopped", statusUpdater);
            return;
        }
        
        safeUpdateStatus(index, "Merging segments...", statusUpdater);
        Path tempMergeFile = targetFile.resolveSibling(targetFile.getFileName().toString() + ".cmfd_merge");
        try {
            try (java.nio.channels.FileChannel outChannel = new java.io.FileOutputStream(tempMergeFile.toFile()).getChannel()) {
                for (int i = 0; i < numSegments; i++) {
                    try (java.nio.channels.FileChannel inChannel = new java.io.FileInputStream(partFiles[i].toFile()).getChannel()) {
                        inChannel.transferTo(0, inChannel.size(), outChannel);
                    }
                    Files.delete(partFiles[i]);
                }
            }
            
            // Deep validation using modelValidator on the merged temp file
            if (modelValidator != null) {
                IModelValidator.ValidationResult valResult = modelValidator.validateFile(tempMergeFile.toFile());
                if (!valResult.ok) {
                    tempMergeFile.toFile().delete(); // Clean up corrupted file
                    safeUpdateStatus(index, "❌ Validation failed: " + valResult.message, statusUpdater);
                    return;
                }
            }
            
            // Atomically move the temp file to the final destination
            Files.move(tempMergeFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            
            // Pre-compute and register the file hash in registry cache
            if (hashRegistry != null) {
                hashRegistry.getOrCalculateHash(targetFile.toFile());
            }
            
            onDownloadComplete(info, targetFile);
            safeUpdateStatus(index, "✅ Finished", statusUpdater);
        } catch (Exception e) {
            try { Files.deleteIfExists(tempMergeFile); } catch (Exception ignored) {}
            for (int i = 0; i < numSegments; i++) {
                try { Files.deleteIfExists(partFiles[i]); } catch (Exception ignored) {}
            }
            throw new IOException("Failed to merge segments: " + e.getMessage(), e);
        }
    }

    private boolean waitForPauseAndCheckSelection(int index, BiConsumer<Integer, String> statusUpdater) {
        try {
            if (isPaused) {
                safeUpdateStatus(index, "Paused", statusUpdater);
            }
            while (isPaused && !isStopped && isSelected(index)) {
                Thread.sleep(200);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        if (isStopped || !isSelected(index)) {
            safeUpdateStatus(index, !isSelected(index) ? "Skipped (Unchecked)" : "Stopped", statusUpdater);
            return true;
        }
        return false;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024 * 1024 * 1024) return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        if (bytes < 1024L * 1024 * 1024 * 1024) return String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        return String.format(java.util.Locale.US, "%.2f TB", bytes / (1024.0 * 1024.0 * 1024.0 * 1024.0));
    }

    private void onDownloadComplete(ModelInfo info, Path targetFile) {
        if (civitaiService != null) {
            try {
                civitaiService.downloadMetadataAndPreview(info.getUrl(), targetFile);
            } catch (Exception e) {
                logger.error("Error downloading Civitai metadata: " + e.getMessage());
            }
        }
    }

    @jakarta.annotation.PreDestroy
    public void shutdown() {
        logger.info("Stopping DownloadManager executors...");
        try {
            executor.shutdownNow();
            segmentExecutor.shutdownNow();
            executor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS);
            segmentExecutor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ignored) {}
    }

    @Override public synchronized void togglePause() { isPaused = !isPaused; }
    @Override public void stop() { isStopped = true; isPaused = false; }
    @Override public boolean isPaused() { return isPaused; }
}