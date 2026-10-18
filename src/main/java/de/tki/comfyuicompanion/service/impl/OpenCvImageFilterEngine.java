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
import org.opencv.core.MatOfDouble;
import org.opencv.core.Scalar;
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
import java.util.ArrayList;
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

    /**
     * Performs gentle Reinhard color transfer in the LAB color space from a reference image to a source image.
     * Matches channel means and standard deviations while blending with original colors to retain scene sharpness.
     *
     * @param sourceBgr    the source BGR image matrix to adjust
     * @param referenceBgr the reference BGR image matrix (e.g., Scene 1)
     * @param blendFactor  blending weight (recommended 0.35 - 0.50; 0.0 = original, 1.0 = fully transferred)
     * @return the color-harmonized BGR image matrix, or a clone of the source if reference is invalid
     */
    public Mat colorTransferLabReinhard(Mat sourceBgr, Mat referenceBgr, double blendFactor) {
        if (sourceBgr == null || sourceBgr.empty()) {
            return null;
        }
        if (referenceBgr == null || referenceBgr.empty()) {
            return sourceBgr.clone();
        }

        Mat sLab = new Mat();
        Mat rLab = new Mat();
        Imgproc.cvtColor(sourceBgr, sLab, Imgproc.COLOR_BGR2Lab);
        Imgproc.cvtColor(referenceBgr, rLab, Imgproc.COLOR_BGR2Lab);

        Mat sLabF = new Mat();
        Mat rLabF = new Mat();
        sLab.convertTo(sLabF, CvType.CV_32FC3);
        rLab.convertTo(rLabF, CvType.CV_32FC3);

        List<Mat> sChannels = new ArrayList<>(3);
        List<Mat> rChannels = new ArrayList<>(3);
        Core.split(sLabF, sChannels);
        Core.split(rLabF, rChannels);

        List<Mat> transChannels = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            MatOfDouble sMean = new MatOfDouble();
            MatOfDouble sStd = new MatOfDouble();
            Core.meanStdDev(sChannels.get(i), sMean, sStd);

            MatOfDouble rMean = new MatOfDouble();
            MatOfDouble rStd = new MatOfDouble();
            Core.meanStdDev(rChannels.get(i), rMean, rStd);

            double sM = sMean.get(0, 0)[0];
            double sS = sStd.get(0, 0)[0];
            double rM = rMean.get(0, 0)[0];
            double rS = rStd.get(0, 0)[0];

            Mat ch = new Mat();
            Core.subtract(sChannels.get(i), new Scalar(sM), ch);
            double scale = (sS > 1e-4) ? (rS / sS) : 1.0;
            Core.multiply(ch, new Scalar(scale), ch);
            Core.add(ch, new Scalar(rM), ch);
            transChannels.add(ch);

            sChannels.get(i).release();
            rChannels.get(i).release();
        }

        Mat transferredF = new Mat();
        Core.merge(transChannels, transferredF);
        for (Mat tc : transChannels) {
            tc.release();
        }

        double factor = Math.max(0.0, Math.min(1.0, blendFactor));
        Mat blendedF = new Mat();
        Core.addWeighted(transferredF, factor, sLabF, 1.0 - factor, 0.0, blendedF);

        Mat blended8U = new Mat();
        blendedF.convertTo(blended8U, CvType.CV_8UC3);

        Mat destBgr = new Mat();
        Imgproc.cvtColor(blended8U, destBgr, Imgproc.COLOR_Lab2BGR);

        sLab.release();
        rLab.release();
        sLabF.release();
        rLabF.release();
        transferredF.release();
        blendedF.release();
        blended8U.release();

        return destBgr;
    }

    /**
     * Harmonizes keyframe image color palettes by applying gentle LAB color transfer relative to a reference image.
     *
     * @param sourceImageFile    image to harmonize
     * @param referenceImageFile palette reference image (e.g., Scene 1)
     * @param outputFile         destination file for the harmonized keyframe
     * @param blendFactor        blending ratio between transferred palette and original colors
     * @return the output file, or the original file on failure
     */
    public File harmonizeKeyframeColorsLab(File sourceImageFile, File referenceImageFile, File outputFile, double blendFactor) {
        if (sourceImageFile == null || !sourceImageFile.exists()) {
            return sourceImageFile;
        }
        if (referenceImageFile == null || !referenceImageFile.exists() || sourceImageFile.equals(referenceImageFile)) {
            return sourceImageFile;
        }
        try {
            Mat srcMat = Imgcodecs.imread(sourceImageFile.getAbsolutePath());
            Mat refMat = Imgcodecs.imread(referenceImageFile.getAbsolutePath());
            if (srcMat.empty() || refMat.empty()) {
                if (!srcMat.empty()) srcMat.release();
                if (!refMat.empty()) refMat.release();
                return sourceImageFile;
            }

            Mat harmonized = colorTransferLabReinhard(srcMat, refMat, blendFactor);
            if (outputFile.getParentFile() != null && !outputFile.getParentFile().exists()) {
                outputFile.getParentFile().mkdirs();
            }
            Imgcodecs.imwrite(outputFile.getAbsolutePath(), harmonized);

            srcMat.release();
            refMat.release();
            harmonized.release();
            return outputFile;
        } catch (Throwable t) {
            logger.warn("⚠️ [OpenCvImageFilterEngine] Color transfer failed for '{}': {}", sourceImageFile.getName(), t.getMessage());
            return sourceImageFile;
        }
    }
}
