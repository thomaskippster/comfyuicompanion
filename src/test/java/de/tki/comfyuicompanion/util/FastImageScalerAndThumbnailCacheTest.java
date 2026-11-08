package de.tki.comfyuicompanion.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests verifying the high-performance FastImageScaler and ThumbnailCache.
 */
public class FastImageScalerAndThumbnailCacheTest {

    @Test
    @DisplayName("FastImageScaler correctly scales BufferedImage with high quality and fast modes")
    void testFastImageScaler() {
        BufferedImage original = new BufferedImage(500, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = original.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 500, 300);
        g.dispose();

        // Smooth mode
        BufferedImage scaledSmooth = FastImageScaler.scale(original, 100, 60, true);
        assertNotNull(scaledSmooth);
        assertEquals(100, scaledSmooth.getWidth());
        assertEquals(60, scaledSmooth.getHeight());

        // Fast mode
        BufferedImage scaledFast = FastImageScaler.scale(original, 50, 30, false);
        assertNotNull(scaledFast);
        assertEquals(50, scaledFast.getWidth());
        assertEquals(30, scaledFast.getHeight());

        // Null and invalid dimensions safety
        assertNull(FastImageScaler.scale(null, 100, 100, true));
        assertNull(FastImageScaler.scale(original, 0, 100, true));
        assertNull(FastImageScaler.scale(original, 100, -5, true));
    }

    @Test
    @DisplayName("ThumbnailCache caches thumbnails in memory and returns identical instances")
    void testThumbnailCache(@TempDir Path tempDir) throws IOException, InterruptedException {
        File sampleImg = tempDir.resolve("cache_test.png").toFile();
        BufferedImage img = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 200, 200);
        g.dispose();
        assertTrue(ImageIO.write(img, "png", sampleImg));

        ThumbnailCache.clear();

        ImageIcon first = ThumbnailCache.getOrCreate(sampleImg, 64, 64);
        assertNotNull(first);
        assertEquals(64, first.getIconWidth());
        assertEquals(64, first.getIconHeight());

        // Second call should return cached instance immediately
        ImageIcon second = ThumbnailCache.getOrCreate(sampleImg, 64, 64);
        assertSame(first, second, "Should return cached ImageIcon instance");

        // Async load
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ImageIcon> asyncResult = new AtomicReference<>();
        ThumbnailCache.loadAsync(sampleImg, 64, 64, icon -> {
            asyncResult.set(icon);
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertSame(first, asyncResult.get());
    }
}
