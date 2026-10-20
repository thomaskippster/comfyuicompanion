package de.tki.comfyuicompanion.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/**
 * Engine responsible for performing segmented and single-stream HTTP downloads with resume support,
 * speed throttling, virtual thread workers, and NIO.2 segment stitching.
 */
@Component
public class SegmentedDownloadEngine {
    private static final Logger logger = LoggerFactory.getLogger(SegmentedDownloadEngine.class);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int BUFFER_SIZE = 262144; // 256 KB

    private final ConfigService configService;
    private final HttpClient httpClient;
    private final ExecutorService segmentExecutor;

    /** Control interface providing pause, stop and selection state. */
    public interface DownloadControl {
        boolean isStopped();
        boolean isPaused();
        boolean isSelected();
    }

    /** Exception thrown when a download stream terminates before all bytes were received. */
    public static class PrematureStreamEndException extends IOException {
        private final long downloaded, total;
        public PrematureStreamEndException(long d, long t) { super("Stream ended (" + d + "/" + t + ")"); this.downloaded = d; this.total = t; }
        public long getDownloaded() { return downloaded; }
        public long getTotal() { return total; }
    }

    @Autowired
    public SegmentedDownloadEngine(@Autowired(required = false) ConfigService configService) {
        this.configService = configService;
        this.httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(60)).build();
        this.segmentExecutor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("Download-Segment-", 0).factory());
    }

    /** Probes remote Content-Length via HTTP HEAD request, returning -1 if auth required. */
    public long probeRemoteSize(String url) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", USER_AGENT)
                .method("HEAD", HttpRequest.BodyPublishers.noBody()).timeout(Duration.ofSeconds(60));
        applyAuthHeaders(b, url);
        HttpResponse<Void> res = httpClient.send(b.build(), HttpResponse.BodyHandlers.discarding());
        if (res.statusCode() == 401 || res.statusCode() == 403) return -1;
        return res.headers().firstValueAsLong("Content-Length").orElse(0L);
    }

    /** Executes either a multi-segment or single-stream download based on segment configuration. */
    public boolean download(String fileName, String downloadUrl, Path targetFile, long totalRemoteSize,
                            int numSegments, DownloadControl control, Consumer<String> statusUpdater) throws Exception {
        if (numSegments > 1 && totalRemoteSize > 100 * 1024 * 1024) {
            try {
                return downloadMultiSegment(downloadUrl, targetFile, totalRemoteSize, numSegments, control, statusUpdater);
            } catch (Exception e) {
                if (control.isStopped() || !control.isSelected()) {
                    throw e;
                }
                logger.warn("Multi-segment download failed ({}), falling back to single stream...", e.getMessage());
                statusUpdater.accept("Retrying with single stream...");
                return downloadSingleSegment(fileName, downloadUrl, targetFile, totalRemoteSize, control, statusUpdater);
            }
        }
        return downloadSingleSegment(fileName, downloadUrl, targetFile, totalRemoteSize, control, statusUpdater);
    }

    /** Downloads a file in a single stream with HTTP Range resume support and speed throttling. */
    public boolean downloadSingleSegment(String fileName, String downloadUrl, Path targetFile, long totalRemoteSize,
                                         DownloadControl control, Consumer<String> statusUpdater) throws Exception {
        Path partFile = targetFile.resolveSibling(targetFile.getFileName().toString() + ".cmfd");
        File pFile = partFile.toFile();
        long existingSize = pFile.exists() ? pFile.length() : 0;

        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(downloadUrl)).header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(120));
        applyAuthHeaders(builder, downloadUrl);
        if (existingSize > 0) builder.header("Range", "bytes=" + existingSize + "-");

        HttpResponse<InputStream> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        int code = response.statusCode();
        if (code == 416) {
            if (existingSize == totalRemoteSize) {
                Files.move(partFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                return true;
            }
            statusUpdater.accept("✅ Already exists");
            return false;
        }
        if (code == 401 || code == 403) {
            String msg = downloadUrl.contains("civitai.com") ? "❌ Civitai API Key Required" : "❌ Auth Required (Token?)";
            statusUpdater.accept(msg);
            return false;
        }

        long contentLen = response.headers().firstValueAsLong("Content-Length").orElse(0L);
        long totalBytes = (code == 206) ? contentLen + existingSize : contentLen;
        if (totalBytes > 0 && totalBytes < 5000 && fileName.endsWith(".safetensors")) {
            statusUpdater.accept("❌ LFS Stub detected (Token?)");
            return false;
        }
        if (code == 200) existingSize = 0;

        long limitBytesSec = (configService != null ? configService.getDownloadSpeedLimit() : 0) * 1024L;
        long[] progress = new long[]{existingSize, System.currentTimeMillis(), 0L, existingSize}; // downloaded, lastSpeedTime, lastUpdate, lastDownloaded

        try (InputStream is = response.body(); RandomAccessFile raf = new RandomAccessFile(pFile, "rw")) {
            raf.seek(existingSize);
            boolean ok = streamBytes(is, raf, new byte[BUFFER_SIZE], control, limitBytesSec, -1L, statusUpdater, read -> {
                progress[0] += read;
                long now = System.currentTimeMillis();
                if (now - progress[2] > 800) {
                    double speed = (now > progress[1]) ? ((progress[0] - progress[3]) / (1024.0 * 1024.0)) / ((now - progress[1]) / 1000.0) : 0;
                    String speedStr = speed >= 1.0 ? String.format(Locale.US, "%.1f MB/s", speed) : String.format(Locale.US, "%.0f KB/s", speed * 1024);
                    String pct = totalBytes > 0 ? (progress[0] * 100 / totalBytes) + "%" : "?%";
                    String size = totalBytes > 0 ? formatSize(progress[0]) + " / " + formatSize(totalBytes) : formatSize(progress[0]);
                    statusUpdater.accept("Downloading: " + pct + " (" + size + ") - " + speedStr + formatEta(totalBytes - progress[0], speed));
                    progress[2] = now; progress[1] = now; progress[3] = progress[0];
                }
            });
            if (!ok) {
                statusUpdater.accept(!control.isSelected() ? "Skipped (Unchecked)" : "Stopped");
                return false;
            }
        }

        long finalSize = pFile.length();
        if (totalBytes > 0 && finalSize < totalBytes) throw new PrematureStreamEndException(finalSize, totalBytes);
        Files.move(partFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    /** Downloads a file using concurrent HTTP Range segments via virtual thread workers and stitches them together. */
    public boolean downloadMultiSegment(String downloadUrl, Path targetFile, long totalRemoteSize,
                                        int numSegments, DownloadControl control, Consumer<String> statusUpdater) throws Exception {
        long segmentSize = totalRemoteSize / numSegments;
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        List<InputStream> openStreams = Collections.synchronizedList(new ArrayList<>());
        Path[] partFiles = new Path[numSegments];
        long initialDownloaded = 0;
        for (int i = 0; i < numSegments; i++) {
            partFiles[i] = targetFile.resolveSibling(targetFile.getFileName().toString() + ".part" + i);
            File f = partFiles[i].toFile();
            if (f.exists()) {
                final long start = i * segmentSize;
                final long end = (i == numSegments - 1) ? totalRemoteSize - 1 : (start + segmentSize - 1);
                long expectedSegmentBytes = end - start + 1;
                if (f.length() > expectedSegmentBytes) {
                    try { Files.deleteIfExists(partFiles[i]); } catch (Exception ignored) {}
                } else {
                    initialDownloaded += f.length();
                }
            }
        }

        var total = new AtomicLong(initialDownloaded);
        var failed = new AtomicBoolean(false);
        var errorMsg = new AtomicReference<>("");
        long limitSec = (configService != null ? configService.getDownloadSpeedLimit() : 0) * 1024L / numSegments;

        for (int i = 0; i < numSegments; i++) {
            final int segIdx = i;
            final long start = i * segmentSize;
            final long end = (i == numSegments - 1) ? totalRemoteSize - 1 : (start + segmentSize - 1);
            final long expectedSegmentBytes = end - start + 1;
            final Path partPath = partFiles[i];

            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    File pFile = partPath.toFile();
                    long existing = pFile.exists() ? pFile.length() : 0;
                    if (existing >= expectedSegmentBytes) {
                        return;
                    }
                    if (control.isStopped() || failed.get()) {
                        return;
                    }

                    long bytesToRead = expectedSegmentBytes - existing;
                    HttpRequest.Builder b = HttpRequest.newBuilder()
                            .uri(URI.create(downloadUrl))
                            .header("User-Agent", USER_AGENT)
                            .header("Range", "bytes=" + (start + existing) + "-" + end)
                            .timeout(Duration.ofSeconds(120));
                    applyAuthHeaders(b, downloadUrl);

                    HttpResponse<InputStream> res = httpClient.send(b.build(), HttpResponse.BodyHandlers.ofInputStream());
                    if (res.statusCode() != 206) {
                        throw new IOException("Server returned HTTP " + res.statusCode() + " instead of 206 Partial Content for segment " + segIdx);
                    }

                    try (InputStream is = res.body(); RandomAccessFile raf = new RandomAccessFile(pFile, "rw")) {
                        openStreams.add(is);
                        raf.seek(existing);
                        boolean ok = streamBytes(is, raf, new byte[BUFFER_SIZE], control, limitSec, bytesToRead, null, read -> total.addAndGet(read));
                        if (!ok && (control.isStopped() || !control.isSelected())) {
                            failed.set(true);
                        }
                    } finally {
                        openStreams.remove(res.body());
                    }

                    if (pFile.length() < expectedSegmentBytes && !control.isStopped() && !failed.get()) {
                        throw new PrematureStreamEndException(pFile.length(), expectedSegmentBytes);
                    }
                } catch (Exception e) {
                    failed.set(true);
                    errorMsg.set(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                }
            }, segmentExecutor));
        }

        long lastUpdate = 0;
        while (!futures.stream().allMatch(CompletableFuture::isDone)) {
            if (failed.get() || control.isStopped() || !control.isSelected()) {
                failed.set(true);
                for (CompletableFuture<Void> f : futures) {
                    f.cancel(true);
                }
                synchronized (openStreams) {
                    for (InputStream is : openStreams) {
                        try { is.close(); } catch (Exception ignored) {}
                    }
                }
                break;
            }
            long now = System.currentTimeMillis();
            if (now - lastUpdate > 800) {
                long cur = Math.min(total.get(), totalRemoteSize);
                String pct = totalRemoteSize > 0 ? (cur * 100 / totalRemoteSize) + "%" : "?%";
                statusUpdater.accept("Downloading (Segmented): " + pct + " (" + formatSize(cur) + ")");
                lastUpdate = now;
            }
            Thread.sleep(200);
        }
        try { CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join(); } catch (Exception ignored) {}

        if (failed.get()) {
            if (!control.isStopped() && control.isSelected()) {
                for (Path p : partFiles) { try { Files.deleteIfExists(p); } catch (Exception ignored) {} }
            }
            throw new IOException("Segment download failed: " + errorMsg.get());
        }
        if (control.isStopped() || !control.isSelected()) {
            statusUpdater.accept(!control.isSelected() ? "Skipped (Unchecked)" : "Stopped");
            return false;
        }

        for (int i = 0; i < numSegments; i++) {
            final long start = i * segmentSize;
            final long end = (i == numSegments - 1) ? totalRemoteSize - 1 : (start + segmentSize - 1);
            long expectedBytes = end - start + 1;
            File f = partFiles[i].toFile();
            if (!f.exists() || f.length() != expectedBytes) {
                for (Path p : partFiles) { try { Files.deleteIfExists(p); } catch (Exception ignored) {} }
                throw new IOException("Segment " + i + " size mismatch: expected " + expectedBytes + ", found " + (f.exists() ? f.length() : "missing"));
            }
        }

        statusUpdater.accept("Merging segments...");
        stitchSegments(partFiles, targetFile);
        return true;
    }

    /** Stitches temporary segment files into the destination file using zero-copy NIO.2 FileChannel transfer. */
    public void stitchSegments(Path[] partFiles, Path targetFile) throws IOException {
        Path tempMerge = targetFile.resolveSibling(targetFile.getFileName().toString() + ".cmfd_merge");
        try {
            try (FileChannel out = FileChannel.open(tempMerge, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (Path part : partFiles) {
                    if (Files.exists(part)) {
                        try (FileChannel in = FileChannel.open(part, StandardOpenOption.READ)) {
                            long size = in.size();
                            long pos = 0;
                            while (pos < size) {
                                long transferred = in.transferTo(pos, size - pos, out);
                                if (transferred <= 0) break;
                                pos += transferred;
                            }
                        }
                        Files.deleteIfExists(part);
                    }
                }
            }
            Files.move(tempMerge, targetFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(tempMerge);
            for (Path part : partFiles) { try { Files.deleteIfExists(part); } catch (IOException ignored) {} }
            throw new IOException("Failed to merge segments: " + e.getMessage(), e);
        }
    }

    private boolean streamBytes(InputStream is, RandomAccessFile raf, byte[] buf, DownloadControl control,
                                long limitBytesSec, Consumer<String> statusUpdater, java.util.function.LongConsumer onRead)
            throws IOException, InterruptedException {
        return streamBytes(is, raf, buf, control, limitBytesSec, -1L, statusUpdater, onRead);
    }

    private boolean streamBytes(InputStream is, RandomAccessFile raf, byte[] buf, DownloadControl control,
                                long limitBytesSec, long maxBytesToRead, Consumer<String> statusUpdater, java.util.function.LongConsumer onRead)
            throws IOException, InterruptedException {
        long startTime = System.currentTimeMillis(), written = 0;
        int read;
        while (true) {
            if (control.isStopped() || !control.isSelected() || Thread.currentThread().isInterrupted()) return false;
            if (control.isPaused()) {
                while (control.isPaused() && !control.isStopped() && control.isSelected()) {
                    if (statusUpdater != null) statusUpdater.accept("Paused");
                    Thread.sleep(200);
                }
                if (statusUpdater != null && !control.isStopped() && control.isSelected()) statusUpdater.accept("Resuming...");
            }

            int toRead = buf.length;
            if (maxBytesToRead > 0) {
                long remaining = maxBytesToRead - written;
                if (remaining <= 0) {
                    break;
                }
                if (remaining < toRead) {
                    toRead = (int) remaining;
                }
            }

            read = is.read(buf, 0, toRead);
            if (read == -1) {
                break;
            }

            raf.write(buf, 0, read);
            if (onRead != null) {
                onRead.accept(read);
            }
            written += read;

            if (maxBytesToRead > 0 && written >= maxBytesToRead) {
                break;
            }

            if (limitBytesSec > 0) {
                long elapsed = System.currentTimeMillis() - startTime;
                long sleep = (written * 1000L) / limitBytesSec - elapsed;
                if (sleep > 0) Thread.sleep(sleep);
                if (elapsed > 5000) { startTime = System.currentTimeMillis(); written = 0; }
            }
        }
        return true;
    }

    /** Formats bytes into a human-readable size string. */
    public static String formatSize(long bytes) {
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024 * 1024 * 1024) return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        if (bytes < 1024L * 1024 * 1024 * 1024) return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        return String.format(Locale.US, "%.2f TB", bytes / (1024.0 * 1024.0 * 1024.0 * 1024.0));
    }

    private static String formatEta(long remainingBytes, double speedMBs) {
        if (remainingBytes <= 0 || speedMBs <= 0.02) return "";
        long s = (long) (remainingBytes / (speedMBs * 1024 * 1024));
        if (s > 3600) return " (ETA: " + (s / 3600) + "h " + ((s % 3600) / 60) + "m)";
        if (s > 60) return " (ETA: " + (s / 60) + "m " + (s % 60) + "s)";
        return " (ETA: " + s + "s)";
    }

    /**
     * Injects appropriate authorization headers (Civitai API key or HuggingFace token) into the request builder.
     *
     * @param builder the HTTP request builder
     * @param url     the destination URL
     */
    private void applyAuthHeaders(HttpRequest.Builder builder, String url) {
        if (configService == null || url == null) return;
        String hfToken = configService.getHfToken();
        if (url.contains("huggingface.co") && hfToken != null && !hfToken.isBlank()) {
            builder.header("Authorization", "Bearer " + hfToken.trim());
        }
        String civitaiKey = configService.getCivitaiApiKey();
        if (url.contains("civitai.com") && civitaiKey != null && !civitaiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + civitaiKey.trim());
        }
    }

    /** Shuts down the segment executor pool. */
    @PreDestroy
    public void shutdown() {
        try {
            segmentExecutor.shutdownNow();
            segmentExecutor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
