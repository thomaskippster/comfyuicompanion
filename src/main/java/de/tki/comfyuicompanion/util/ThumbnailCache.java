package de.tki.comfyuicompanion.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Thread-safe LRU in-memory thumbnail cache for local image files.
 * Caches scaled icons to avoid repeated disk reads and heavy decoding of full-resolution images.
 */
public final class ThumbnailCache {

    private static final Logger logger = LoggerFactory.getLogger(ThumbnailCache.class);
    private static final int MAX_ENTRIES = 300;

    private static final Map<String, ImageIcon> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ImageIcon> eldest) {
                    return size() > MAX_ENTRIES;
                }
            }
    );

    private ThumbnailCache() {
        // Utility class
    }

    /**
     * Retrieves a cached thumbnail icon or generates and caches one synchronously.
     *
     * @param file    the source image file
     * @param targetW target thumbnail width
     * @param targetH target thumbnail height
     * @return the thumbnail ImageIcon, or null if loading fails
     */
    public static ImageIcon getOrCreate(File file, int targetW, int targetH) {
        if (file == null || !file.exists() || targetW <= 0 || targetH <= 0) {
            return null;
        }

        String key = buildKey(file, targetW, targetH);
        ImageIcon cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        try {
            BufferedImage orig = ImageIO.read(file);
            if (orig != null) {
                BufferedImage thumb = FastImageScaler.scale(orig, targetW, targetH, true);
                if (thumb != null) {
                    ImageIcon icon = new ImageIcon(thumb);
                    CACHE.put(key, icon);
                    return icon;
                }
            }
        } catch (Exception ex) {
            logger.debug("Failed to generate thumbnail for {}: {}", file.getName(), ex.getMessage());
        }
        return null;
    }

    /**
     * Asynchronously retrieves or generates a thumbnail on a virtual thread and delivers it to the consumer on the EDT.
     *
     * @param file     the source image file
     * @param targetW  target thumbnail width
     * @param targetH  target thumbnail height
     * @param onLoaded callback invoked on the Swing EDT with the loaded icon
     */
    public static void loadAsync(File file, int targetW, int targetH, Consumer<ImageIcon> onLoaded) {
        if (file == null || !file.exists() || onLoaded == null) {
            return;
        }

        String key = buildKey(file, targetW, targetH);
        ImageIcon cached = CACHE.get(key);
        if (cached != null) {
            SwingUtilities.invokeLater(() -> onLoaded.accept(cached));
            return;
        }

        Thread.ofVirtual().name("thumb-loader-" + file.getName()).start(() -> {
            ImageIcon icon = getOrCreate(file, targetW, targetH);
            if (icon != null) {
                SwingUtilities.invokeLater(() -> onLoaded.accept(icon));
            }
        });
    }

    /**
     * Clears all cached thumbnails from memory.
     */
    public static void clear() {
        CACHE.clear();
    }

    private static String buildKey(File file, int w, int h) {
        return file.getAbsolutePath() + "_" + file.lastModified() + "_" + w + "x" + h;
    }
}
