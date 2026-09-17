package de.tki.comfyuicompanion.ui.blueprint;

import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintEntry;
import io.metaloom.video4j.VideoFile;
import io.metaloom.video4j.Videos;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages downloading, decoding, scaling, and disk/memory caching of workflow preview images and video frames.
 */
public class BlueprintPreviewCache {
    private static final Logger logger = LoggerFactory.getLogger(BlueprintPreviewCache.class);

    private static final HttpClient SHARED_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    private static final ExecutorService imageLoadExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "BlueprintPreviewLoader");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private final ConfigService configService;
    private final Map<String, Image> previewCache = new ConcurrentHashMap<>();
    private final Map<String, Image> scaledPreviewCache = new ConcurrentHashMap<>();
    private final Set<String> loadingPaths = ConcurrentHashMap.newKeySet();
    private final Set<String> failedPaths = ConcurrentHashMap.newKeySet();
    private final Set<String> failedUrls = ConcurrentHashMap.newKeySet();

    public BlueprintPreviewCache(ConfigService configService) {
        this.configService = configService;
    }

    public Image getPreview(String filePath) {
        return previewCache.get(filePath);
    }

    public Image getScaledPreview(String scaledKey) {
        return scaledPreviewCache.get(scaledKey);
    }

    public void putScaledPreview(String scaledKey, Image img) {
        scaledPreviewCache.put(scaledKey, img);
    }

    public void triggerImageLoad(BlueprintEntry entry, JComponent repaintTarget) {
        if (failedPaths.contains(entry.filePath)) {
            return;
        }
        if (loadingPaths.add(entry.filePath)) {
            CompletableFuture.supplyAsync(() -> loadPreviewImage(entry), imageLoadExecutor)
                    .thenAccept(img -> {
                        if (img != null) {
                            previewCache.put(entry.filePath, img);
                            SwingUtilities.invokeLater(repaintTarget::repaint);
                        } else {
                            failedPaths.add(entry.filePath);
                        }
                        loadingPaths.remove(entry.filePath);
                    });
        }
    }

    public Image loadPreviewImage(BlueprintEntry entry) {
        if (entry.previewPath != null && !entry.previewPath.isEmpty()) {
            String pathOrUrl = entry.previewPath;
            if (pathOrUrl.contains("/main/templates/input/")) {
                pathOrUrl = pathOrUrl.replace("/main/templates/input/", "/main/input/");
            } else if (pathOrUrl.contains("/main/templates/output/")) {
                pathOrUrl = pathOrUrl.replace("/main/templates/output/", "/main/output/");
            } else if (pathOrUrl.contains("/main/templates/thumbnail/")) {
                pathOrUrl = pathOrUrl.replace("/main/templates/thumbnail/", "/main/thumbnail/");
            }

            if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
                Image img = readImageNoJavaFX(pathOrUrl);
                if (img != null) return img;

                if (!pathOrUrl.endsWith(".webp")) {
                    int lastDot = pathOrUrl.lastIndexOf('.');
                    if (lastDot > 0) {
                        String webpUrl = pathOrUrl.substring(0, lastDot) + ".webp";
                        img = readImageNoJavaFX(webpUrl);
                        if (img != null) return img;
                    }
                }

                if (pathOrUrl.contains("-1.")) {
                    String fallbackUrl = pathOrUrl.replace("-1.", ".");
                    img = readImageNoJavaFX(fallbackUrl);
                    if (img != null) return img;

                    if (!fallbackUrl.endsWith(".webp")) {
                        int lastDot = fallbackUrl.lastIndexOf('.');
                        if (lastDot > 0) {
                            String webpFallbackUrl = fallbackUrl.substring(0, lastDot) + ".webp";
                            img = readImageNoJavaFX(webpFallbackUrl);
                            if (img != null) return img;
                        }
                    }
                }
            }
        }

        if (entry.registryWorkflow != null && entry.registryWorkflow.getId() != null) {
            String id = entry.registryWorkflow.getId();
            String repoBase = "https://raw.githubusercontent.com/Comfy-Org/workflow_templates/main/";
            String[] candidates = new String[] {
                    repoBase + "thumbnail/" + id + "_thumbnail.mp4",
                    repoBase + "thumbnail/" + id + "_preview.mp4",
                    repoBase + "thumbnail/" + id + ".png",
                    repoBase + "thumbnail/" + id + ".webp",
                    repoBase + "output/" + id + ".mp4",
                    repoBase + "output/" + id + ".png",
                    repoBase + "output/" + id + ".webp",
                    repoBase + "input/" + id + "_input_image.png",
                    repoBase + "input/" + id + ".png"
            };
            for (String cand : candidates) {
                if (cand.equals(entry.previewPath)) continue;
                Image img = readImageNoJavaFX(cand);
                if (img != null) return img;
            }

            if (entry.registryWorkflow.getPreviewCandidates() != null) {
                for (String cand : entry.registryWorkflow.getPreviewCandidates()) {
                    if (cand != null && !cand.equals(entry.previewPath)) {
                        Image img = readImageNoJavaFX(cand);
                        if (img != null) return img;
                    }
                }
            }
        }

        return null;
    }

    public File getPreviewDiskCacheDir() {
        File dir = new File(configService != null ? configService.getAppDataPath() : System.getProperty("user.home") + File.separator + ".comfyuicompanion", "cache" + File.separator + "previews");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public String hashUrl(String url) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(url.hashCode());
        }
    }

    public Image readImageNoJavaFX(String urlStr) {
        if (urlStr == null || urlStr.trim().isEmpty()) return null;
        if (failedUrls.contains(urlStr)) return null;

        String cleanUrl = urlStr.toLowerCase();
        int qIdx = cleanUrl.indexOf('?');
        if (qIdx > 0) cleanUrl = cleanUrl.substring(0, qIdx);

        String ext = ".png";
        if (cleanUrl.endsWith(".webp")) ext = ".webp";
        else if (cleanUrl.endsWith(".mp4")) ext = ".mp4";
        else if (cleanUrl.endsWith(".webm")) ext = ".webm";
        else if (cleanUrl.endsWith(".gif")) ext = ".gif";

        boolean isVideo = ext.equals(".mp4") || ext.equals(".webm");

        File cacheDir = getPreviewDiskCacheDir();
        File cachedFile = new File(cacheDir, hashUrl(urlStr) + ext);

        if (cachedFile.exists() && cachedFile.length() > 0) {
            if (!isVideo) {
                try {
                    BufferedImage img = ImageIO.read(cachedFile);
                    if (img != null) return img;
                } catch (Exception ignored) {}
            }
            return readImageFromFile(cachedFile, isVideo, urlStr);
        }

        int maxRetries = 2;
        int delayMs = 300;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(urlStr))
                        .header("User-Agent", "ComfyUI-Companion")
                        .timeout(Duration.ofSeconds(4))
                        .GET()
                        .build();

                HttpResponse<byte[]> response = SHARED_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 200) {
                    byte[] bytes = response.body();
                    try {
                        java.nio.file.Files.write(cachedFile.toPath(), bytes);
                    } catch (Exception e) {
                        logger.debug("Failed to write to preview cache for {}: {}", urlStr, e.getMessage());
                    }

                    if (!isVideo) {
                        try {
                            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
                            if (img != null) return img;
                        } catch (Exception ignored) {}
                    }

                    return readImageFromFile(cachedFile, isVideo, urlStr);
                } else if (response.statusCode() == 404) {
                    failedUrls.add(urlStr);
                    break;
                }
            } catch (Exception e) {
                logger.debug("⚠️ [BlueprintGallery] Network error for {}: {}", urlStr, e.getMessage());
            }

            if (attempt < maxRetries) {
                try {
                    Thread.sleep(delayMs * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        failedUrls.add(urlStr);
        return null;
    }

    public Image readImageFromFile(File file, boolean isVideo, String originalUrl) {
        try {
            try {
                de.tki.comfyuicompanion.util.OpenCvLoader.load();
            } catch (Throwable ignored) {}

            if (isVideo) {
                try {
                    try (VideoFile vid = Videos.open(file.getAbsolutePath())) {
                        vid.seekToFrame(0);
                        BufferedImage img = vid.frameToImage();
                        if (img != null) return img;
                    }
                } catch (Throwable ex) {
                    logger.debug("Video4j fallback failed for " + originalUrl, ex);
                }
            }

            Mat mat = Imgcodecs.imread(file.getAbsolutePath());
            if (mat != null && !mat.empty()) {
                MatOfByte buffer = new MatOfByte();
                Imgcodecs.imencode(".png", mat, buffer);
                return ImageIO.read(new ByteArrayInputStream(buffer.toArray()));
            } else {
                try {
                    org.opencv.videoio.VideoCapture cap = new org.opencv.videoio.VideoCapture(file.getAbsolutePath(), org.opencv.videoio.Videoio.CAP_FFMPEG);
                    if (!cap.isOpened()) {
                        cap = new org.opencv.videoio.VideoCapture(file.getAbsolutePath());
                    }
                    if (cap.isOpened()) {
                        Mat frame = new Mat();
                        if (cap.read(frame) && !frame.empty()) {
                            MatOfByte buffer = new MatOfByte();
                            Imgcodecs.imencode(".png", frame, buffer);
                            cap.release();
                            return ImageIO.read(new ByteArrayInputStream(buffer.toArray()));
                        }
                        cap.release();
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            logger.debug("OpenCV/Video processing failed for preview: " + originalUrl, t);
        }
        return null;
    }

    public void paintBokehFallback(Graphics2D g2, int w, int h, Color[] palette, BlueprintEntry entry,
                                  Color cardBorder, Color textPrimary, String icon) {
        Color base = palette[0];
        Color dark = new Color(
                Math.max(0, (int) (base.getRed()   * 0.25f)),
                Math.max(0, (int) (base.getGreen() * 0.25f)),
                Math.max(0, (int) (base.getBlue()  * 0.25f))
        );
        g2.setColor(dark);
        g2.fillRect(0, 0, w, h);

        g2.setColor(new Color(cardBorder.getRed(), cardBorder.getGreen(), cardBorder.getBlue(), 40));
        g2.setStroke(new BasicStroke(0.5f));
        for (int x = 0; x < w; x += 20) g2.drawLine(x, 0, x, h);
        for (int y = 0; y < h; y += 20) g2.drawLine(0, y, w, y);

        Font iconFont = new Font("SansSerif", Font.BOLD, 26);
        g2.setFont(iconFont);
        FontMetrics fm = g2.getFontMetrics();
        int ix = (w - fm.stringWidth(icon)) / 2;
        int iy = h / 2 - 4;
        g2.setColor(new Color(textPrimary.getRed(), textPrimary.getGreen(), textPrimary.getBlue(), 210));
        g2.drawString(icon, ix, iy);
    }
}
