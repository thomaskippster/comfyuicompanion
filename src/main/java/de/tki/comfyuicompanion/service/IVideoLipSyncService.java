package de.tki.comfyuicompanion.service;

import de.tki.comfyuicompanion.domain.Scene;
import org.json.JSONObject;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Enterprise service interface for audio-driven head animation, neural lip-sync synthesis,
 * and audio-visual facial alignment in the Video Architect pipeline.
 */
public interface IVideoLipSyncService {

    /**
     * Checks if lip-sync animation processing is available via ComfyUI nodes or local fallback.
     *
     * @param serverUrl ComfyUI server URL
     * @return {@code true} if at least one lip-sync engine is supported
     */
    boolean isLipSyncAvailable(String serverUrl);

    /**
     * Retrieves the list of available or detected lip-sync engines.
     *
     * @param serverUrl ComfyUI server URL
     * @return list of engine identifiers (e.g. "Auto", "LivePortrait", "SadTalker", "Wav2Lip")
     */
    List<String> getAvailableLipSyncEngines(String serverUrl);

    /**
     * Applies audio lip-sync animation using the configured engine in user preferences.
     *
     * @param mediaFile  source image or video file
     * @param audioFile  source narration audio file
     * @param outputFile destination video file
     * @param scene      scene domain model containing duration and dimensions
     * @return CompletableFuture resolving to the synthesized lip-synced video file
     */
    CompletableFuture<File> applyLipSync(File mediaFile, File audioFile, File outputFile, Scene scene);

    /**
     * Applies audio lip-sync animation using an explicit engine override.
     *
     * @param mediaFile      source image or video file
     * @param audioFile      source narration audio file
     * @param outputFile     destination video file
     * @param scene          scene domain model containing duration and dimensions
     * @param engineOverride explicit engine identifier (e.g. "LivePortrait", "SadTalker", "Wav2Lip")
     * @return CompletableFuture resolving to the synthesized lip-synced video file
     */
    CompletableFuture<File> applyLipSync(File mediaFile, File audioFile, File outputFile, Scene scene, String engineOverride);

    /**
     * Builds the ComfyUI API prompt JSON graph for the specified lip-sync engine.
     *
     * @param engine         target engine name ("LivePortrait", "SadTalker", "Wav2Lip")
     * @param inputMediaName uploaded or resolved input image/video filename in ComfyUI
     * @param audioFileName  uploaded or resolved audio filename in ComfyUI
     * @param scene          scene domain model
     * @return JSON graph payload ready for the {@code /prompt} endpoint
     */
    JSONObject buildLipSyncPrompt(String engine, String inputMediaName, String audioFileName, Scene scene);
}
