package de.tki.comfyuicompanion.util;

import javafx.embed.swing.SwingFXUtils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Enterprise utility for robust image downloading, caching, and decoding with automatic JavaFX WebP fallback
 * and OpenCV video frame extraction.
 * Solves standard Java ImageIO limitations when decoding modern WebP thumbnails, banners, and video workflows.
 *
 * @author Senior Java Developer
 */
public final class WorkflowImageLoader {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowImageLoader.class);

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static volatile Path cacheDirectory = resolveDefaultCacheDir();

    private WorkflowImageLoader() {
        // Utility class
    }

    private static Path resolveDefaultCacheDir() {
        String override = System.getProperty("comfyuicompanion.preview.cache");
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        Path primary = Paths.get(System.getProperty("user.home"), ".comfyui-companion", "cache", "previews");
        Path legacy = Paths.get(System.getProperty("user.home"), ".comfyuicompanion", "cache", "previews");
        if (Files.exists(legacy) && !Files.exists(primary)) {
            return legacy;
        }
        return primary;
    }

    /**
     * Returns the active preview disk cache directory, creating it if it does not yet exist.
     *
     * @return path to cache directory
     */
    public static Path getPreviewCacheDir() {
        try {
            if (!Files.exists(cacheDirectory)) {
                Files.createDirectories(cacheDirectory);
            }
        } catch (Exception e) {
            logger.debug("Failed creating preview cache directory: {}", e.getMessage());
        }
        return cacheDirectory;
    }

    /**
     * Sets a custom preview disk cache directory.
     *
     * @param dir custom cache directory
     */
    public static void setCacheDirectory(Path dir) {
        if (dir != null) {
            cacheDirectory = dir;
        }
    }

    /**
     * Computes a deterministic SHA-256 hex string for a given URL or cache key.
     *
     * @param url URL to hash
     * @return hex string representation of SHA-256 hash
     */
    public static String hashUrl(String url) {
        if (url == null || url.isBlank()) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(url.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(url.hashCode());
        }
    }

    /**
     * Computes legacy MD5 hex string for backward compatibility with existing disk caches.
     *
     * @param url URL to hash
     * @return hex string representation of MD5 hash
     */
    public static String hashUrlMd5(String url) {
        if (url == null || url.isBlank()) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(md.digest(url.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(url.hashCode());
        }
    }

    /**
     * Resolves the appropriate file extension (.png, .webp, .mp4, .webm, .gif, .jpg) from a URL or file path.
     *
     * @param pathOrUrl target URL or filename
     * @return file extension including leading dot, defaults to .png
     */
    public static String resolveExtension(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) return ".png";
        String clean = pathOrUrl.trim().toLowerCase(Locale.ROOT);
        int qIdx = clean.indexOf('?');
        if (qIdx > 0) clean = clean.substring(0, qIdx);
        if (clean.endsWith(".webp")) return ".webp";
        if (clean.endsWith(".mp4")) return ".mp4";
        if (clean.endsWith(".webm")) return ".webm";
        if (clean.endsWith(".mov")) return ".mov";
        if (clean.endsWith(".gif")) return ".gif";
        if (clean.endsWith(".jpg") || clean.endsWith(".jpeg")) return ".jpg";
        return ".png";
    }

    /**
     * Resolves the disk cache {@link File} for a remote URL.
     * Checks if a cached file with SHA-256 exists, or alternatively with legacy MD5 hash.
     *
     * @param urlStr remote URL
     * @return File handle pointing to cache location
     */
    public static File getCachedFile(String urlStr) {
        if (urlStr == null || urlStr.isBlank()) return null;
        Path cacheDir = getPreviewCacheDir();
        String ext = resolveExtension(urlStr);

        String shaHash = hashUrl(urlStr);
        File shaFile = cacheDir.resolve(shaHash + ext).toFile();
        if (shaFile.exists() && shaFile.length() > 0) {
            return shaFile;
        }

        String md5Hash = hashUrlMd5(urlStr);
        File md5File = cacheDir.resolve(md5Hash + ext).toFile();
        if (md5File.exists() && md5File.length() > 0) {
            return md5File;
        }

        return shaFile;
    }

    /**
     * Atomically writes raw bytes to the destination cache file via temporary file replacement.
     *
     * @param targetFile destination file
     * @param bytes      data to store
     */
    public static void writeToCache(File targetFile, byte[] bytes) {
        if (targetFile == null || bytes == null || bytes.length == 0) return;
        Path tempFile = null;
        try {
            Path parentDir = targetFile.toPath().getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            tempFile = Files.createTempFile(parentDir != null ? parentDir : getPreviewCacheDir(), "wf_cache_", ".tmp");
            Files.write(tempFile, bytes);
            try {
                Files.move(tempFile, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception e) {
                Files.move(tempFile, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            logger.debug("Failed writing to preview disk cache {}: {}", targetFile.getName(), e.getMessage());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Central loading method with default 10-second timeout.
     *
     * @param pathOrUrl local file path or remote HTTP(S) URL
     * @return decoded BufferedImage or null if unavailable
     */
    public static BufferedImage loadOrFetchCached(String pathOrUrl) {
        return loadOrFetchCached(pathOrUrl, Duration.ofSeconds(10));
    }

    /**
     * Central loading method: Loads an image either directly from a local path or from the disk cache.
     * If missing from the disk cache, downloads the bytes, saves them atomically to disk, and decodes.
     * Automatically extracts OpenCV video keyframes and applies JavaFX WebP fallback decoding.
     *
     * @param pathOrUrl local file path or remote HTTP(S) URL
     * @param timeout   download timeout if a remote fetch is needed
     * @return decoded BufferedImage or null if unavailable
     */
    public static BufferedImage loadOrFetchCached(String pathOrUrl, Duration timeout) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) return null;

        if (!pathOrUrl.startsWith("http://") && !pathOrUrl.startsWith("https://")) {
            File localFile = new File(pathOrUrl);
            return localFile.exists() && localFile.isFile() ? readImageFile(localFile) : null;
        }

        File cachedFile = getCachedFile(pathOrUrl);
        if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
            BufferedImage cachedImage = readImageFile(cachedFile);
            if (cachedImage != null) return cachedImage;
        }

        byte[] downloadedBytes = downloadImageBytes(pathOrUrl, timeout != null ? timeout : Duration.ofSeconds(10));
        if (downloadedBytes == null || downloadedBytes.length == 0) return null;

        if (cachedFile != null) {
            writeToCache(cachedFile, downloadedBytes);
            BufferedImage img = readImageFile(cachedFile);
            if (img != null) return img;
        }

        return decodeImageBytes(downloadedBytes);
    }

    /**
     * Decodes raw image bytes into a {@link BufferedImage}, first trying standard {@link ImageIO},
     * and falling back to JavaFX Image reader if standard readers cannot decode (e.g. WebP format).
     *
     * @param bytes the raw image byte array
     * @return decoded BufferedImage or null if decoding fails completely
     */
    public static BufferedImage decodeImageBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;

        try {
            BufferedImage standardImage = ImageIO.read(new ByteArrayInputStream(bytes));
            if (standardImage != null) return standardImage;
        } catch (Exception e) {
            logger.debug("Standard ImageIO failed to decode image bytes: {}", e.getMessage());
        }

        try {
            javafx.scene.image.Image fxImage = new javafx.scene.image.Image(new ByteArrayInputStream(bytes));
            if (!fxImage.isError() && fxImage.getWidth() > 0 && fxImage.getHeight() > 0) {
                BufferedImage bImg = SwingFXUtils.fromFXImage(fxImage, null);
                if (bImg != null) return bImg;
            }
        } catch (Throwable fxErr) {
            logger.debug("JavaFX WebP image decoding fallback failed: {}", fxErr.getMessage());
        }

        return null;
    }

    /**
     * Reads and decodes an image file from disk, using OpenCV video frame extraction for video workflows
     * and JavaFX fallback for WebP if needed.
     *
     * @param file the image or video file to read
     * @return decoded BufferedImage or null if failed
     */
    public static BufferedImage readImageFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) return null;

        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".mp4") || name.endsWith(".webm") || name.endsWith(".mov")) {
            BufferedImage frame = extractVideoFrame(file);
            if (frame != null) return frame;
        }

        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            return decodeImageBytes(bytes);
        } catch (Exception e) {
            logger.debug("Error reading image file {}: {}", file.getAbsolutePath(), e.getMessage());
            return null;
        }
    }

    /**
     * Extracts a keyframe from a local video file using OpenCV.
     *
     * @param file video file
     * @return extracted keyframe as BufferedImage, or null if failed
     */
    public static BufferedImage extractVideoFrame(File file) {
        if (file == null || !file.exists() || file.length() == 0) return null;

        File sidecarThumb = new File(file.getParentFile(), file.getName() + ".thumb.png");
        if (sidecarThumb.exists() && sidecarThumb.length() > 0) {
            try {
                BufferedImage cached = ImageIO.read(sidecarThumb);
                if (cached != null) return cached;
            } catch (Exception ignored) {}
        }

        BufferedImage ffmpegFrame = extractVideoFrameViaFfmpeg(file, sidecarThumb);
        if (ffmpegFrame != null) {
            return ffmpegFrame;
        }

        try {
            OpenCvLoader.load();
            VideoCapture cap = new VideoCapture(file.getAbsolutePath());
            if (cap.isOpened()) {
                Mat frame = new Mat();
                if (cap.read(frame) && !frame.empty()) {
                    MatOfByte buffer = new MatOfByte();
                    Imgcodecs.imencode(".png", frame, buffer);
                    byte[] pngBytes = buffer.toArray();
                    cap.release();
                    frame.release();
                    buffer.release();
                    try {
                        Files.write(sidecarThumb.toPath(), pngBytes);
                    } catch (Exception ignored) {}
                    return ImageIO.read(new ByteArrayInputStream(pngBytes));
                }
                cap.release();
            }
        } catch (Throwable t) {
            logger.debug("OpenCV frame extraction failed for {}: {}", file.getName(), t.getMessage());
        }
        return null;
    }

    private static BufferedImage extractVideoFrameViaFfmpeg(File videoFile, File sidecarThumb) {
        String ffmpeg = ExecutableLocator.findExecutablePath("ffmpeg", null, null);
        if (ffmpeg == null) return null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    ffmpeg, "-y", "-ss", "00:00:00.500", "-i", videoFile.getAbsolutePath(),
                    "-frames:v", "1", "-update", "1", sidecarThumb.getAbsolutePath()
            );
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            Process proc = pb.start();
            boolean finished = proc.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                return null;
            }
            if (proc.exitValue() == 0 && sidecarThumb.exists() && sidecarThumb.length() > 0) {
                return ImageIO.read(sidecarThumb);
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Downloads image bytes from a remote URL with appropriate browser User-Agent header.
     *
     * @param urlStr  source image URL
     * @param timeout maximum request timeout
     * @return raw byte array or null if download failed
     */
    public static byte[] downloadImageBytes(String urlStr, Duration timeout) {
        if (urlStr == null || urlStr.isBlank()) return null;

        try {
            String sanitizedUrl = urlStr.trim().replace(" ", "%20");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(sanitizedUrl))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(timeout != null ? timeout : Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) {
                return response.body();
            }
            logger.debug("HTTP {} error fetching image from {}", response.statusCode(), urlStr);
        } catch (Exception e) {
            logger.debug("Error downloading image from {}: {}", urlStr, e.getMessage());
        }
        return null;
    }

    /**
     * Downloads an image from a remote URL and decodes it, supporting WebP through JavaFX fallback.
     * Uses disk cache transparently.
     *
     * @param urlStr  source image URL
     * @param timeout maximum request timeout
     * @return decoded BufferedImage or null
     */
    public static BufferedImage downloadAndDecodeImage(String urlStr, Duration timeout) {
        return loadOrFetchCached(urlStr, timeout);
    }

    /**
     * Resolves and reads raw bytes for either a local filesystem path or an HTTP(S) URL.
     * Automatically extracts keyframe PNG bytes if the source target is a video.
     * Uses and populates the local disk cache for remote URLs.
     *
     * @param pathOrUrl absolute/relative file path or remote web URL
     * @return loaded image bytes or empty array if failed
     */
    public static byte[] loadImageBytes(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) return new byte[0];
        try {
            String lower = pathOrUrl.toLowerCase(Locale.ROOT);
            boolean isVideo = lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mov");

            if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
                File cachedFile = getCachedFile(pathOrUrl);
                if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
                    if (isVideo) {
                        File sidecar = new File(cachedFile.getParentFile(), cachedFile.getName() + ".thumb.png");
                        if (sidecar.exists() && sidecar.length() > 0) {
                            return Files.readAllBytes(sidecar.toPath());
                        }
                        BufferedImage frame = extractVideoFrame(cachedFile);
                        if (frame != null) {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            ImageIO.write(frame, "png", baos);
                            return baos.toByteArray();
                        }
                    }
                    return Files.readAllBytes(cachedFile.toPath());
                }

                byte[] bytes = downloadImageBytes(pathOrUrl, Duration.ofSeconds(15));
                if (bytes == null || bytes.length == 0) return new byte[0];

                if (cachedFile != null) {
                    writeToCache(cachedFile, bytes);
                }

                if (isVideo) {
                    File vidFile = (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) ? cachedFile : null;
                    File tmp = null;
                    if (vidFile == null) {
                        tmp = File.createTempFile("wf_vid_preview_", lower.substring(lower.lastIndexOf('.')));
                        tmp.deleteOnExit();
                        Files.write(tmp.toPath(), bytes);
                        vidFile = tmp;
                    }
                    try {
                        BufferedImage frame = extractVideoFrame(vidFile);
                        if (frame != null) {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            ImageIO.write(frame, "png", baos);
                            return baos.toByteArray();
                        }
                    } finally {
                        if (tmp != null) tmp.delete();
                    }
                }
                return bytes;
            } else {
                File f = new File(pathOrUrl);
                if (f.exists() && f.isFile()) {
                    if (isVideo) {
                        BufferedImage frame = extractVideoFrame(f);
                        if (frame != null) {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            ImageIO.write(frame, "png", baos);
                            return baos.toByteArray();
                        }
                    }
                    return Files.readAllBytes(f.toPath());
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to load image bytes from {}: {}", pathOrUrl, e.getMessage());
        }
        return new byte[0];
    }
}
