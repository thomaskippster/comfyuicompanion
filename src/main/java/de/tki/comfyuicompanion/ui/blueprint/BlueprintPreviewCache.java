package de.tki.comfyuicompanion.ui.blueprint;

import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintEntry;
import de.tki.comfyuicompanion.util.WorkflowImageLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages downloading, decoding, scaling, and disk/memory caching of workflow preview images and video frames.
 * Employs dual-key indexing for workflow paths and image URLs, delegating all decoding and frame extraction
 * to the enterprise {@link WorkflowImageLoader}.
 *
 * @author Senior Java Developer
 */
public class BlueprintPreviewCache {
    private static final Logger logger = LoggerFactory.getLogger(BlueprintPreviewCache.class);

    private static final ExecutorService imageLoadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private final ConfigService configService;
    private final Map<String, Image> previewCache = new ConcurrentHashMap<>();
    private final Map<String, Image> scaledPreviewCache = new ConcurrentHashMap<>();
    private final Set<String> loadingPaths = ConcurrentHashMap.newKeySet();
    private final Set<String> failedPaths = ConcurrentHashMap.newKeySet();
    private final Set<String> failedUrls = ConcurrentHashMap.newKeySet();

    public BlueprintPreviewCache(ConfigService configService) {
        this.configService = configService;
        if (configService != null && configService.getAppDataPath() != null) {
            WorkflowImageLoader.setCacheDirectory(
                    java.nio.file.Paths.get(configService.getAppDataPath(), "cache", "previews")
            );
        }
    }

    public Image getPreview(String key) {
        if (key == null || key.isBlank()) return null;
        return previewCache.get(key);
    }

    public void putPreview(String key, Image img) {
        if (key != null && !key.isBlank() && img != null) {
            previewCache.put(key, img);
        }
    }

    public Image getScaledPreview(String scaledKey) {
        return scaledPreviewCache.get(scaledKey);
    }

    public void putScaledPreview(String scaledKey, Image img) {
        if (scaledKey != null && img != null) {
            scaledPreviewCache.put(scaledKey, img);
        }
    }

    public void clearFailures() {
        failedPaths.clear();
        failedUrls.clear();
    }

    public void triggerImageLoad(BlueprintEntry entry, JComponent repaintTarget) {
        if (entry == null || entry.filePath == null) {
            return;
        }
        if (failedPaths.contains(entry.filePath)) {
            return;
        }
        if (loadingPaths.add(entry.filePath)) {
            CompletableFuture.supplyAsync(() -> loadPreviewImage(entry), imageLoadExecutor)
                    .thenAccept(img -> {
                        if (img != null) {
                            previewCache.put(entry.filePath, img);
                            if (entry.previewPath != null && !entry.previewPath.isBlank()) {
                                previewCache.put(entry.previewPath, img);
                            }
                            SwingUtilities.invokeLater(repaintTarget::repaint);
                        } else {
                            failedPaths.add(entry.filePath);
                        }
                        loadingPaths.remove(entry.filePath);
                    });
        }
    }

    public Image loadPreviewImage(BlueprintEntry entry) {
        if (entry == null) return null;

        // 1. Direct local file check from previewPath
        if (entry.previewPath != null && !entry.previewPath.isBlank()) {
            File localImg = new File(entry.previewPath);
            if (localImg.exists() && localImg.isFile() && localImg.length() > 0) {
                BufferedImage bi = WorkflowImageLoader.readImageFile(localImg);
                if (bi != null) return bi;
            }
        }

        // 2. Check sibling images next to the workflow file on disk
        if (entry.filePath != null && !entry.filePath.isBlank()) {
            File wfFile = new File(entry.filePath);
            if (wfFile.exists() && wfFile.isFile()) {
                File dir = wfFile.getParentFile();
                String name = wfFile.getName();
                int dot = name.lastIndexOf('.');
                String base = dot > 0 ? name.substring(0, dot) : name;
                for (String ext : List.of(".png", ".jpg", ".jpeg", ".webp", ".gif", ".mp4")) {
                    File sibling = new File(dir, base + ext);
                    if (sibling.exists() && sibling.isFile() && sibling.length() > 0) {
                        BufferedImage bi = WorkflowImageLoader.readImageFile(sibling);
                        if (bi != null) return bi;
                    }
                }
            }
        }

        // 3. Remote HTTP/HTTPS URL from previewPath
        if (entry.previewPath != null && !entry.previewPath.isBlank()) {
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

        // 4. Registry candidate URLs
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
        return WorkflowImageLoader.getPreviewCacheDir().toFile();
    }

    public String hashUrl(String url) {
        return WorkflowImageLoader.hashUrl(url);
    }

    public Image readImageNoJavaFX(String urlStr) {
        if (urlStr == null || urlStr.trim().isEmpty()) return null;
        if (failedUrls.contains(urlStr)) return null;

        BufferedImage img = WorkflowImageLoader.loadOrFetchCached(urlStr, Duration.ofSeconds(8));
        if (img != null) {
            return img;
        }

        failedUrls.add(urlStr);
        return null;
    }

    public Image readImageFromFile(File file, boolean isVideo, String originalUrl) {
        if (file == null || !file.exists() || file.length() == 0) return null;
        return WorkflowImageLoader.readImageFile(file);
    }

    public void paintBokehFallback(Graphics2D g2, int w, int h, Color[] palette, BlueprintEntry entry,
                                  Color cardBorder, Color textPrimary, String icon) {
        Color base = palette != null && palette.length > 0 ? palette[0] : new Color(30, 41, 59);
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

        if (icon != null && !icon.isBlank()) {
            Font iconFont = new Font("SansSerif", Font.BOLD, 26);
            g2.setFont(iconFont);
            FontMetrics fm = g2.getFontMetrics();
            int ix = (w - fm.stringWidth(icon)) / 2;
            int iy = h / 2 - 4;
            g2.setColor(new Color(textPrimary.getRed(), textPrimary.getGreen(), textPrimary.getBlue(), 210));
            g2.drawString(icon, ix, iy);
        }
    }
}
