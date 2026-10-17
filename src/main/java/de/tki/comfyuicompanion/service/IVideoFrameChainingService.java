package de.tki.comfyuicompanion.service;

import de.tki.comfyuicompanion.domain.Scene;

import java.io.File;
import java.util.List;

/**
 * Service interface for continuous scene chaining by extracting the final frame
 * of a preceding video clip and feeding it as the Image-to-Video start image for the subsequent scene.
 */
public interface IVideoFrameChainingService {

    /**
     * Extracts the final video frame from an existing MP4/video file and saves it as a high-quality image.
     *
     * @param videoFile        the source video file
     * @param targetImageFile  the destination image file (typically PNG or JPG)
     * @return the extracted image file, or null if extraction failed
     */
    File extractLastFrame(File videoFile, File targetImageFile);

    /**
     * Resolves the appropriate start image for the scene at {@code sceneIndex}.
     * If frame chaining is active and a preceding scene has a rendered video, extracts its last frame.
     * Otherwise, falls back to the scene's explicit start image or the default speaker image.
     *
     * @param scenes               the complete sequence of scenes
     * @param sceneIndex           the index of the current scene to resolve
     * @param defaultSpeakerImage  the fallback global speaker/start image
     * @param outputDirectory      directory to store chained frame artifacts
     * @return path to the resolved start image, or null if none is available
     */
    String resolveChainedStartImage(List<Scene> scenes, int sceneIndex, String defaultSpeakerImage, File outputDirectory);
}
