package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.util.OpenCvLoader;
import io.metaloom.video4j.VideoFile;
import io.metaloom.video4j.VideoFrame;
import io.metaloom.video4j.Videos;
import jakarta.annotation.PostConstruct;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Specialized engine for OpenCV-based image filtering, frame extraction, and visual enhancement.
 * <p>
 * Encapsulates matrix operations such as contrast and brightness adjustments, sharpening convolutions,
 * gaussian blurs, and conversions between OpenCV {@link Mat}, AWT {@link BufferedImage}, and JavaFX {@link javafx.scene.image.Image}.
 */
@Component
public class OpenCvImageFilterEngine {

    private static final Logger logger = LoggerFactory.getLogger(OpenCvImageFilterEngine.class);

    /**
     * Initializes the native OpenCV runtime binaries.
     */
    @PostConstruct
    public void init() {
        OpenCvLoader.load();
    }

    /**
     * Adjusts the brightness and contrast of a given OpenCV {@link Mat}.
     *
     * @param src   the source image matrix
     * @param alpha the contrast multiplier (1.0 = unchanged)
     * @param beta  the brightness offset (0.0 = unchanged)
     * @return the transformed matrix, or {@code null} if the input is empty or null
     */
    public Mat adjustBrightnessContrast(Mat src, double alpha, double beta) {
        if (src == null || src.empty()) {
            return null;
        }
        Mat dest = new Mat();
        Core.convertScaleAbs(src, dest, alpha, beta);
        return dest;
    }

    /**
     * Applies a 3x3 sharpening convolution kernel to enhance edges and frame clarity.
     *
     * @param src the source image matrix
     * @return a new sharpened image matrix, or {@code null} if the source is null or empty
     */
    public Mat applySharpen(Mat src) {
        if (src == null || src.empty()) {
            return null;
        }
        Mat dest = new Mat();
        Mat kernel = new Mat(3, 3, CvType.CV_32F);
        float[] kernelData = {
             0.0f, -1.0f,  0.0f,
            -1.0f,  5.0f, -1.0f,
             0.0f, -1.0f,  0.0f
        };
        kernel.put(0, 0, kernelData);
        Imgproc.filter2D(src, dest, -1, kernel);
        return dest;
    }

    /**
     * Applies a Gaussian blur filter to smooth or de-noise the image matrix.
     *
     * @param src    the source image matrix
     * @param ksize  the Gaussian kernel size (width, height must be positive and odd)
     * @param sigmaX Gaussian kernel standard deviation in X direction
     * @return the blurred image matrix, or {@code null} if input is invalid
     */
    public Mat applyGaussianBlur(Mat src, Size ksize, double sigmaX) {
        if (src == null || src.empty()) {
            return null;
        }
        Mat dest = new Mat();
        Imgproc.GaussianBlur(src, dest, ksize, sigmaX);
        return dest;
    }

    /**
     * Converts an OpenCV {@link Mat} into a Java AWT {@link BufferedImage}.
     *
     * @param mat the matrix to encode and convert
     * @return the converted BufferedImage, or {@code null} on failure
     */
    public BufferedImage matToBufferedImage(Mat mat) {
        if (mat == null || mat.empty()) {
            return null;
        }
        try {
            MatOfByte buffer = new MatOfByte();
            Imgcodecs.imencode(".png", mat, buffer);
            return ImageIO.read(new ByteArrayInputStream(buffer.toArray()));
        } catch (IOException e) {
            logger.error("Failed to convert OpenCV Mat to BufferedImage: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Converts an OpenCV {@link Mat} into a JavaFX {@link javafx.scene.image.Image}.
     *
     * @param mat the matrix to encode and convert
     * @return the converted JavaFX Image, or {@code null} on failure
     */
    public javafx.scene.image.Image matToFxImage(Mat mat) {
        if (mat == null || mat.empty()) {
            return null;
        }
        try {
            MatOfByte buffer = new MatOfByte();
            Imgcodecs.imencode(".png", mat, buffer);
            byte[] bytes = buffer.toArray();
            return new javafx.scene.image.Image(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            logger.error("Failed to convert OpenCV Mat to JavaFX Image: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extracts an individual video frame or reads a static image file as an OpenCV {@link Mat}.
     *
     * @param mediaPath   the absolute path to the video or image file
     * @param frameNumber the target frame index (0-based)
     * @return a cloned {@link Mat} instance, or {@code null} if unavailable
     */
    public Mat extractFrameMat(String mediaPath, int frameNumber) {
        if (mediaPath == null || mediaPath.trim().isEmpty()) {
            return null;
        }
        File file = new File(mediaPath);
        if (!file.exists() || file.length() < 100) {
            return null;
        }

        String lower = mediaPath.toLowerCase();
        boolean isImage = lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp");
        if (isImage) {
            Mat mat = Imgcodecs.imread(mediaPath);
            return (mat != null && !mat.empty()) ? mat : null;
        }

        try (VideoFile video = Videos.open(mediaPath)) {
            List<VideoFrame> frames = video.streamFrames()
                    .filter(f -> f.number() >= frameNumber)
                    .limit(1)
                    .toList();
            if (!frames.isEmpty() && frames.get(0).mat() != null && !frames.get(0).mat().empty()) {
                return frames.get(0).mat().clone();
            }
        } catch (Throwable t) {
            logger.error("Failed to extract frame {} from media '{}': {}", frameNumber, mediaPath, t.getMessage());
        }
        return null;
    }

    /**
     * Extracts a preview frame for the given scene and applies brightness and contrast enhancements,
     * returning the result as an AWT {@link BufferedImage}.
     *
     * @param scene the scene containing video/image path and frame parameters
     * @param alpha contrast multiplier (1.0 = unchanged)
     * @param beta  brightness offset (0.0 = unchanged)
     * @return the enhanced preview BufferedImage, or {@code null} if loading fails
     */
    public BufferedImage applyBasicEnhancementAwt(Scene scene, double alpha, double beta) {
        if (scene == null) {
            return null;
        }
        String videoPath = scene.getVideoPath();
        if (videoPath == null || videoPath.trim().isEmpty()) {
            videoPath = scene.getSourceClipPath();
        }
        if (videoPath == null || videoPath.trim().isEmpty() || !new File(videoPath).exists()) {
            return null;
        }
        if (new File(videoPath).length() < 100) {
            return null;
        }

        try {
            Mat mat = extractFrameMat(videoPath, scene.getStartFrame());
            if (mat != null && !mat.empty()) {
                Mat enhanced = adjustBrightnessContrast(mat, alpha, beta);
                return matToBufferedImage(enhanced);
            }
        } catch (Throwable t) {
            logger.error("Failed to load/enhance video preview in AWT: {}", t.getMessage());
        }
        return null;
    }

    /**
     * Extracts a preview frame for the given scene and applies brightness and contrast enhancements,
     * returning the result as a JavaFX {@link javafx.scene.image.Image}.
     *
     * @param scene the scene containing video path and frame parameters
     * @param alpha contrast multiplier (1.0 = unchanged)
     * @param beta  brightness offset (0.0 = unchanged)
     * @return the enhanced JavaFX Image, or {@code null} if loading fails
     */
    public javafx.scene.image.Image applyBasicEnhancementFx(Scene scene, double alpha, double beta) {
        if (scene == null) {
            return null;
        }
        String videoPath = scene.getVideoPath();
        if (videoPath == null || videoPath.trim().isEmpty() || !new File(videoPath).exists()) {
            return null;
        }
        if (new File(videoPath).length() < 1024) {
            return null;
        }

        try {
            Mat mat = extractFrameMat(videoPath, scene.getStartFrame());
            if (mat != null && !mat.empty()) {
                Mat enhanced = adjustBrightnessContrast(mat, alpha, beta);
                return matToFxImage(enhanced);
            }
        } catch (Throwable t) {
            logger.error("Failed to load/enhance video preview in JavaFX: {}", t.getMessage());
        }
        return null;
    }
}
