package de.tki.comfyuicompanion.service;

import java.io.File;

/**
 * Service contract for generating atmospheric sound effects and ambient audio tracks.
 * Provides adaptive generation leveraging ComfyUI neural audio models when available
 * with automatic fallback to native cinematic audio synthesis.
 */
public interface ILocalAudioService {

    /**
     * Synthesizes an atmospheric sound effect or ambient track matching the prompt and duration.
     *
     * @param sfxPrompt       descriptive prompt for the sound effect (e.g. "rocket ignition roar, deep rumble")
     * @param durationSeconds desired duration of the generated audio in seconds
     * @param targetFile      destination output audio file (e.g., .wav)
     * @return true if the audio was generated and saved successfully, false otherwise
     */
    boolean generateSoundEffect(String sfxPrompt, int durationSeconds, File targetFile);

    /**
     * Checks whether high-fidelity neural audio generation is available in the connected ComfyUI instance.
     *
     * @return true if neural audio nodes are detected and ready
     */
    boolean isNeuralAudioAvailable();
}
