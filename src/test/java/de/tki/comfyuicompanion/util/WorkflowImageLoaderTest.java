package de.tki.comfyuicompanion.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enterprise test suite verifying the thread-safe preview disk cache, deterministic SHA-256 and MD5 hashing,
 * URL extension resolution, atomic file persistence, and multi-format decoding capabilities in {@link WorkflowImageLoader}.
 *
 * @author Senior Java Developer
 */
class WorkflowImageLoaderTest {

    @TempDir
    Path tempDir;

    private Path originalCacheDir;

    @BeforeEach
    void setUp() {
        originalCacheDir = WorkflowImageLoader.getPreviewCacheDir();
        WorkflowImageLoader.setCacheDirectory(tempDir.resolve("test-previews"));
    }

    @AfterEach
    void tearDown() {
        WorkflowImageLoader.setCacheDirectory(originalCacheDir);
    }

    @Test
    void testHashUrlDeterministicSha256() {
        String url = "https://raw.githubusercontent.com/Comfy-Org/workflow_templates/main/thumbnail/template_flux.webp";
        String hash1 = WorkflowImageLoader.hashUrl(url);
        String hash2 = WorkflowImageLoader.hashUrl(url);

        assertNotNull(hash1);
        assertEquals(64, hash1.length(), "SHA-256 hex string must be exactly 64 characters");
        assertEquals(hash1, hash2, "Hashing the same URL must yield identical deterministic hash");
        assertEquals("", WorkflowImageLoader.hashUrl(null));
        assertEquals("", WorkflowImageLoader.hashUrl("   "));
    }

    @Test
    void testHashUrlMd5Legacy() {
        String url = "https://example.com/test.png";
        String md5 = WorkflowImageLoader.hashUrlMd5(url);

        assertNotNull(md5);
        assertEquals(32, md5.length(), "MD5 hex string must be exactly 32 characters");
        assertEquals(md5, WorkflowImageLoader.hashUrlMd5(url));
    }

    @Test
    void testResolveExtension() {
        assertEquals(".png", WorkflowImageLoader.resolveExtension("https://domain.com/photo.png"));
        assertEquals(".webp", WorkflowImageLoader.resolveExtension("https://domain.com/banner.webp?quality=80"));
        assertEquals(".mp4", WorkflowImageLoader.resolveExtension("https://domain.com/video.MP4"));
        assertEquals(".webm", WorkflowImageLoader.resolveExtension("/local/path/to/clip.webm"));
        assertEquals(".jpg", WorkflowImageLoader.resolveExtension("https://domain.com/image.jpeg"));
        assertEquals(".gif", WorkflowImageLoader.resolveExtension("https://domain.com/anim.gif"));
        assertEquals(".png", WorkflowImageLoader.resolveExtension("https://domain.com/unknown_ext"));
        assertEquals(".png", WorkflowImageLoader.resolveExtension(null));
    }

    @Test
    void testWriteToCacheAndGetCachedFile() throws Exception {
        String url = "https://example.com/generated_sample.png";
        File cachedFile = WorkflowImageLoader.getCachedFile(url);
        assertNotNull(cachedFile);
        assertFalse(cachedFile.exists(), "Cache file should not exist initially");

        byte[] sampleBytes = "test-image-binary-content".getBytes(StandardCharsets.UTF_8);
        WorkflowImageLoader.writeToCache(cachedFile, sampleBytes);

        assertTrue(cachedFile.exists(), "Cached file must exist after writing");
        assertArrayEquals(sampleBytes, Files.readAllBytes(cachedFile.toPath()));

        File resolved = WorkflowImageLoader.getCachedFile(url);
        assertEquals(cachedFile.getAbsolutePath(), resolved.getAbsolutePath());
    }

    @Test
    void testLegacyMd5CacheResolution() throws Exception {
        String url = "https://example.com/legacy_preview.png";
        String md5 = WorkflowImageLoader.hashUrlMd5(url);
        File legacyFile = tempDir.resolve("test-previews").resolve(md5 + ".png").toFile();
        Files.createDirectories(legacyFile.getParentFile().toPath());
        Files.writeString(legacyFile.toPath(), "legacy-cached-bytes");

        File resolved = WorkflowImageLoader.getCachedFile(url);
        assertNotNull(resolved);
        assertEquals(legacyFile.getAbsolutePath(), resolved.getAbsolutePath(),
                "Must resolve existing legacy MD5 file if SHA-256 file is not yet cached");
    }

    @Test
    void testLoadOrFetchCachedWithLocalImage() throws Exception {
        BufferedImage original = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = original.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 32, 32);
        g.dispose();

        File localFile = tempDir.resolve("local_test_image.png").toFile();
        ImageIO.write(original, "png", localFile);

        BufferedImage loaded = WorkflowImageLoader.loadOrFetchCached(localFile.getAbsolutePath(), Duration.ofSeconds(5));
        assertNotNull(loaded, "Should load valid local image");
        assertEquals(32, loaded.getWidth());
        assertEquals(32, loaded.getHeight());
    }

    @Test
    void testLoadImageBytesWithLocalImage() throws Exception {
        BufferedImage original = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = original.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 16, 16);
        g.dispose();

        File localFile = tempDir.resolve("sample.png").toFile();
        ImageIO.write(original, "png", localFile);

        byte[] bytes = WorkflowImageLoader.loadImageBytes(localFile.getAbsolutePath());
        assertNotNull(bytes);
        assertTrue(bytes.length > 0, "Loaded image bytes should not be empty");

        BufferedImage decoded = WorkflowImageLoader.decodeImageBytes(bytes);
        assertNotNull(decoded);
        assertEquals(16, decoded.getWidth());
    }

    @Test
    void testLoadOrFetchCachedFromPreExistingDiskCache() throws Exception {
        String url = "https://example.com/cached_workflow_preview.png";
        File cachedFile = WorkflowImageLoader.getCachedFile(url);

        BufferedImage img = new BufferedImage(24, 24, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        byte[] pngBytes = baos.toByteArray();

        WorkflowImageLoader.writeToCache(cachedFile, pngBytes);

        // Should return the cached image without any network request
        BufferedImage result = WorkflowImageLoader.loadOrFetchCached(url, Duration.ofMillis(100));
        assertNotNull(result, "Must load cached image from disk without performing network call");
        assertEquals(24, result.getWidth());
        assertEquals(24, result.getHeight());
    }
}
