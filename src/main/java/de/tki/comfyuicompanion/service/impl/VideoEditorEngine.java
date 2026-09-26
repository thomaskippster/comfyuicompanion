package de.tki.comfyuicompanion.service.impl;

import io.metaloom.video4j.Video4j;
import io.metaloom.video4j.VideoFile;
import io.metaloom.video4j.VideoFrame;
import io.metaloom.video4j.Videos;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.videoio.VideoWriter;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import org.springframework.beans.factory.annotation.Autowired;

@Service
/**
 * Represents the video editor engine class.
 */
public class VideoEditorEngine {

    private final ConfigService configService;


    private final ProcessTracker processTracker;

    @Autowired
    public VideoEditorEngine(
            ConfigService configService,
            @Autowired(required = false) ProcessTracker processTracker) {
        this.configService = configService;
        this.processTracker = processTracker;
    }

    public VideoEditorEngine(ConfigService configService) {
        this(configService, null);
    }

    // Define a wrapper class to satisfy Frame interface
    public static class Frame {
        private final VideoFrame videoFrame;

        public Frame(VideoFrame videoFrame) {
            this.videoFrame = videoFrame;
        }

        /**
         * Handles the mat operation.
         * @return the Mat result
         */
        public Mat mat() {
            return videoFrame.mat();
        }

        /**
         * Handles the number operation.
         * @return the long result
         */
        public long number() {
            return videoFrame.number();
        }
    }

    @PostConstruct
    public void init() {
        de.tki.comfyuicompanion.util.OpenCvLoader.load();
    }

    /**
     * Handles the process scene operation.
     * @param inputPath the inputPath
     * @param startFrame the startFrame
     * @param endFrame the endFrame
     * @return the File result
     * @throws Exception if an error occurs
     */
    public File processScene(String inputPath, int startFrame, int endFrame) throws Exception {
        File tempFile = File.createTempFile("scene_clip_", ".mp4");
        tempFile.deleteOnExit();

        try (VideoFile video = Videos.open(inputPath)) {
            double fps = video.fps();
            int width = video.width();
            int height = video.height();

            // Set up VideoWriter with OpenCV using MP4V format
            VideoWriter writer = new VideoWriter(
                tempFile.getAbsolutePath(),
                VideoWriter.fourcc('M', 'J', 'P', 'G'),
                fps,
                new Size(width, height),
                true
            );

            try {
                video.streamFrames()
                    .map(Frame::new)
                    .filter(frame -> frame.number() >= startFrame && frame.number() <= endFrame)
                    .forEach(frame -> {
                        // Apply Helligkeit/Kontrast OpenCV filter
                        applyFilter(frame);
                        // Write to the output video stream
                        writer.write(frame.mat());
                    });
            } finally {
                writer.release();
            }
        }

        return tempFile;
    }

    /**
     * Handles the apply filter operation.
     * @param frame the frame
     */
    public void applyFilter(Frame frame) {
        applyFilter(frame, 1.15, 10.0);
    }

    /**
     * Handles the apply filter operation.
     * @param frame the frame
     * @param alpha the alpha
     * @param beta the beta
     */
    public void applyFilter(Frame frame, double alpha, double beta) {
        Mat mat = frame.mat();
        if (mat != null && !mat.empty()) {
            mat.convertTo(mat, -1, alpha, beta);
        }
    }

    /**
     * Handles the merge audio and video operation.
     * @param videoPath the videoPath
     * @param audioPath the audioPath
     * @param outputPath the outputPath
     * @throws Exception if an error occurs
     */
    public void mergeAudioAndVideo(String videoPath, String audioPath, String outputPath) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            configService.getFfmpegPath(), "-y", "-i", videoPath, "-i", audioPath,
            "-c:v", "copy", "-c:a", "aac", "-shortest", outputPath
        );
        pb.redirectErrorStream(true);
        Process process = processTracker.start(pb);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("FFmpeg A/V merge failed with exit code: " + exitCode);
        }
    }
}
