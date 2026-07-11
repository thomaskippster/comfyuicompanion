package de.tki.comfymodels.service.impl;

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
public class VideoEditorEngine {

    private final ConfigService configService;


    @Autowired(required = false)
    private ProcessTracker processTracker;
    public VideoEditorEngine(ConfigService configService) {
        this.configService = configService;
    }

    // Define a wrapper class to satisfy Frame interface
    public static class Frame {
        private final VideoFrame videoFrame;

        public Frame(VideoFrame videoFrame) {
            this.videoFrame = videoFrame;
        }

        public Mat mat() {
            return videoFrame.mat();
        }

        public long number() {
            return videoFrame.number();
        }
    }

    @PostConstruct
    public void init() {
        de.tki.comfymodels.util.OpenCvLoader.load();
    }

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

    public void applyFilter(Frame frame) {
        Mat mat = frame.mat();
        if (mat != null && !mat.empty()) {
            // Apply OpenCV operation: Adjust Contrast (alpha = 1.15) and Brightness (beta = 10)
            mat.convertTo(mat, -1, 1.15, 10.0);
        }
    }

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
