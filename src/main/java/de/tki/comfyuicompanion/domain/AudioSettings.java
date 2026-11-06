package de.tki.comfyuicompanion.domain;

/**
 * Immutable value object encapsulating audio configuration, prompt descriptors, file references,
 * and track gain levels for a storyboard scene.
 *
 * @param narrationAudioPath path to the synthesized speech/voiceover audio file
 * @param sfxAudioPath       path to the ambient sound effect or atmospheric audio file
 * @param sfxPrompt          descriptive prompt for AI/procedural sound effect synthesis
 * @param voiceVolume        relative gain factor for the voiceover track (e.g., 1.0)
 * @param sfxVolume          relative gain factor for the SFX track (e.g., 0.5)
 */
public record AudioSettings(
        String narrationAudioPath,
        String sfxAudioPath,
        String sfxPrompt,
        double voiceVolume,
        double sfxVolume
) {
    public static final AudioSettings DEFAULT = new AudioSettings(null, null, "", 1.0, 0.5);

    /**
     * Returns a new AudioSettings instance with the specified narration audio path.
     *
     * @param path narration audio path
     * @return updated AudioSettings
     */
    public AudioSettings withNarrationAudioPath(String path) {
        return new AudioSettings(path, sfxAudioPath, sfxPrompt, voiceVolume, sfxVolume);
    }

    /**
     * Returns a new AudioSettings instance with the specified SFX audio path.
     *
     * @param path SFX audio path
     * @return updated AudioSettings
     */
    public AudioSettings withSfxAudioPath(String path) {
        return new AudioSettings(narrationAudioPath, path, sfxPrompt, voiceVolume, sfxVolume);
    }

    /**
     * Returns a new AudioSettings instance with the specified SFX prompt.
     *
     * @param prompt sound effect prompt
     * @return updated AudioSettings
     */
    public AudioSettings withSfxPrompt(String prompt) {
        return new AudioSettings(narrationAudioPath, sfxAudioPath, prompt != null ? prompt : "", voiceVolume, sfxVolume);
    }

    /**
     * Returns a new AudioSettings instance with the specified track volumes.
     *
     * @param voice gain factor for voice track
     * @param sfx   gain factor for SFX track
     * @return updated AudioSettings
     */
    public AudioSettings withVolumes(double voice, double sfx) {
        return new AudioSettings(narrationAudioPath, sfxAudioPath, sfxPrompt, voice, sfx);
    }
}
