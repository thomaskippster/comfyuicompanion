package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.util.ExecutableLocator;
import de.tki.comfyuicompanion.util.PlatformUtils;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Enterprise manager component managing media generation settings,
 * text-to-speech engine options, video hardware optimizations, and audio lip-sync parameters.
 */
@Component
public class MediaConfigManager {

    /**
     * Retrieves the configured XTTS service URL.
     *
     * @param settings configuration json
     * @return XTTS endpoint url
     */
    public String getXttsUrl(JSONObject settings) {
        return settings.optString("xtts_url", "http://localhost:8020/api/tts");
    }

    /**
     * Updates the XTTS service URL and persists settings.
     *
     * @param settings configuration json
     * @param url endpoint url
     * @param saveCallback persistence callback
     */
    public void setXttsUrl(JSONObject settings, String url, Runnable saveCallback) {
        settings.put("xtts_url", url);
        saveCallback.run();
    }

    /**
     * Resolves the Piper TTS executable path across configured, relative, and python environments.
     *
     * @param settings configuration json
     * @param pythonPath discovered python executable path
     * @return resolved piper binary path
     */
    public String getPiperPath(JSONObject settings, String pythonPath) {
        String defaultPath = PlatformUtils.isWindows() ? "./tools/tts/piper.exe" : "./tools/tts/piper";
        String configuredPath = settings.optString("piper_path", defaultPath);
        File binFile = new File(configuredPath);
        if (binFile.exists()) return configuredPath;
        File fallbackBin = new File(System.getProperty("user.dir"), configuredPath);
        if (fallbackBin.exists()) return fallbackBin.getAbsolutePath();
        String found = ExecutableLocator.findExecutablePath("piper", configuredPath, pythonPath);
        return found != null ? found : configuredPath;
    }

    /**
     * Configures the Piper binary path.
     *
     * @param settings configuration json
     * @param path binary path
     * @param saveCallback persistence callback
     */
    public void setPiperPath(JSONObject settings, String path, Runnable saveCallback) {
        settings.put("piper_path", path);
        saveCallback.run();
    }

    /**
     * Retrieves the reference speaker image path for avatar consistency.
     *
     * @param settings configuration json
     * @return image path
     */
    public String getSpeakerImagePath(JSONObject settings) {
        return settings.optString("speaker_image", "");
    }

    /**
     * Updates the reference speaker image path.
     *
     * @param settings configuration json
     * @param path image path
     * @param saveCallback persistence callback
     */
    public void setSpeakerImagePath(JSONObject settings, String path, Runnable saveCallback) {
        settings.put("speaker_image", path);
        saveCallback.run();
    }

    /**
     * Retrieves the Piper voice model path.
     *
     * @param settings configuration json
     * @return onnx model path
     */
    public String getPiperModelPath(JSONObject settings) {
        return settings.optString("piper_model_path", "./tools/tts/en_model.onnx");
    }

    /**
     * Configures the Piper voice model path.
     *
     * @param settings configuration json
     * @param path onnx model path
     * @param saveCallback persistence callback
     */
    public void setPiperModelPath(JSONObject settings, String path, Runnable saveCallback) {
        settings.put("piper_model_path", path);
        saveCallback.run();
    }

    /**
     * Retrieves the active TTS provider.
     *
     * @param settings configuration json
     * @return provider name
     */
    public String getTtsProvider(JSONObject settings) {
        return settings.optString("tts_provider", "ComfyUI Qwen-TTS");
    }

    /**
     * Updates the active TTS provider.
     *
     * @param settings configuration json
     * @param provider provider identifier
     * @param saveCallback persistence callback
     */
    public void setTtsProvider(JSONObject settings, String provider, Runnable saveCallback) {
        settings.put("tts_provider", provider);
        saveCallback.run();
    }

    /**
     * Retrieves the Qwen TTS HuggingFace repository ID.
     *
     * @param settings configuration json
     * @return repo id
     */
    public String getQwenTtsModelRepo(JSONObject settings) {
        return settings.optString("qwen_tts_model_repo", "Qwen/Qwen2-Audio-7B-Instruct");
    }

    /**
     * Updates the Qwen TTS HuggingFace repository ID.
     *
     * @param settings configuration json
     * @param repo repo id
     * @param saveCallback persistence callback
     */
    public void setQwenTtsModelRepo(JSONObject settings, String repo, Runnable saveCallback) {
        settings.put("qwen_tts_model_repo", repo);
        saveCallback.run();
    }

    /**
     * Retrieves the local cache path for Qwen TTS models.
     *
     * @param settings configuration json
     * @return directory path
     */
    public String getQwenTtsModelPath(JSONObject settings) {
        return settings.optString("qwen_tts_model_path", System.getProperty("user.home") + "/.cache/huggingface/hub");
    }

    /**
     * Configures the local cache path for Qwen TTS models.
     *
     * @param settings configuration json
     * @param path directory path
     * @param saveCallback persistence callback
     */
    public void setQwenTtsModelPath(JSONObject settings, String path, Runnable saveCallback) {
        settings.put("qwen_tts_model_path", path);
        saveCallback.run();
    }

    /**
     * Checks if Qwen TTS model auto-download is enabled.
     *
     * @param settings configuration json
     * @return true if enabled
     */
    public boolean isQwenTtsModelAuto(JSONObject settings) {
        return settings.optBoolean("qwen_tts_model_auto", true);
    }

    /**
     * Sets whether Qwen TTS model auto-download is enabled.
     *
     * @param settings configuration json
     * @param auto auto flag
     * @param saveCallback persistence callback
     */
    public void setQwenTtsModelAuto(JSONObject settings, boolean auto, Runnable saveCallback) {
        settings.put("qwen_tts_model_auto", auto);
        saveCallback.run();
    }

    /**
     * Retrieves the preferred voice preset for Qwen TTS.
     *
     * @param settings configuration json
     * @return voice name
     */
    public String getQwenTtsVoice(JSONObject settings) {
        return settings.optString("qwen_tts_voice", "Chelsie");
    }

    /**
     * Configures the preferred voice preset for Qwen TTS.
     *
     * @param settings configuration json
     * @param voice voice name
     * @param saveCallback persistence callback
     */
    public void setQwenTtsVoice(JSONObject settings, String voice, Runnable saveCallback) {
        settings.put("qwen_tts_voice", voice);
        saveCallback.run();
    }

    /**
     * Checks if 16 GB VRAM optimizations are enabled.
     *
     * @param settings configuration json
     * @return true if enabled
     */
    public boolean is16GbVramOptimizationEnabled(JSONObject settings) {
        return settings.optBoolean("video_16gb_vram_opt", true);
    }

    /**
     * Configures 16 GB VRAM optimizations.
     *
     * @param settings configuration json
     * @param enabled enabled flag
     * @param saveCallback persistence callback
     */
    public void set16GbVramOptimizationEnabled(JSONObject settings, boolean enabled, Runnable saveCallback) {
        settings.put("video_16gb_vram_opt", enabled);
        saveCallback.run();
    }

    /**
     * Checks if continuous shot last-frame chaining is active.
     *
     * @param settings configuration json
     * @return true if enabled
     */
    public boolean isLastFrameChainingEnabled(JSONObject settings) {
        return settings.optBoolean("video_last_frame_chaining", false);
    }

    /**
     * Configures continuous shot last-frame chaining.
     *
     * @param settings configuration json
     * @param enabled enabled flag
     * @param saveCallback persistence callback
     */
    public void setLastFrameChainingEnabled(JSONObject settings, boolean enabled, Runnable saveCallback) {
        settings.put("video_last_frame_chaining", enabled);
        saveCallback.run();
    }

    /**
     * Checks if two-phase rendering (storyboard keyframe -> I2V) is active.
     *
     * @param settings configuration json
     * @return true if enabled
     */
    public boolean isTwoPhaseRenderingEnabled(JSONObject settings) {
        return settings.optBoolean("video_two_phase_rendering", false);
    }

    /**
     * Configures two-phase rendering.
     *
     * @param settings configuration json
     * @param enabled enabled flag
     * @param saveCallback persistence callback
     */
    public void setTwoPhaseRenderingEnabled(JSONObject settings, boolean enabled, Runnable saveCallback) {
        settings.put("video_two_phase_rendering", enabled);
        saveCallback.run();
    }

    /**
     * Retrieves the configured local script LLM model.
     *
     * @param settings configuration json
     * @return model identifier
     */
    public String getLocalLlmModel(JSONObject settings) {
        return settings.optString("local_llm_model", "gemma-3-4b");
    }

    /**
     * Configures the local script LLM model.
     *
     * @param settings configuration json
     * @param model model identifier
     * @param saveCallback persistence callback
     */
    public void setLocalLlmModel(JSONObject settings, String model, Runnable saveCallback) {
        settings.put("local_llm_model", model);
        saveCallback.run();
    }

    /**
     * Checks if audio-driven lip sync and head animation is enabled.
     *
     * @param settings configuration json
     * @return true if enabled
     */
    public boolean isAudioLipSyncEnabled(JSONObject settings) {
        return settings.optBoolean("video_audio_lip_sync_enabled", false);
    }

    /**
     * Enables or disables audio-driven lip sync and head animation.
     *
     * @param settings configuration json
     * @param enabled enabled flag
     * @param saveCallback persistence callback
     */
    public void setAudioLipSyncEnabled(JSONObject settings, boolean enabled, Runnable saveCallback) {
        settings.put("video_audio_lip_sync_enabled", enabled);
        saveCallback.run();
    }

    /**
     * Retrieves the preferred lip sync engine ("Auto", "LivePortrait", "SadTalker", "Wav2Lip").
     *
     * @param settings configuration json
     * @return engine name
     */
    public String getLipSyncEngine(JSONObject settings) {
        return settings.optString("video_lip_sync_engine", "Auto");
    }

    /**
     * Sets the preferred lip sync engine.
     *
     * @param settings configuration json
     * @param engine engine name
     * @param saveCallback persistence callback
     */
    public void setLipSyncEngine(JSONObject settings, String engine, Runnable saveCallback) {
        settings.put("video_lip_sync_engine", engine != null ? engine : "Auto");
        saveCallback.run();
    }

    /**
     * Retrieves the ElevenLabs API key.
     *
     * @param settings configuration json
     * @return empty string (legacy)
     */
    public String getElevenLabsApiKey(JSONObject settings) {
        return "";
    }

    /**
     * Sets the ElevenLabs API key.
     *
     * @param settings configuration json
     * @param apiKey api key
     * @param saveCallback persistence callback
     */
    public void setElevenLabsApiKey(JSONObject settings, String apiKey, Runnable saveCallback) {
        settings.remove("elevenlabs_api_key");
        saveCallback.run();
    }

    /**
     * Retrieves the ElevenLabs voice ID.
     *
     * @param settings configuration json
     * @return voice id
     */
    public String getElevenLabsVoiceId(JSONObject settings) {
        return settings.optString("elevenlabs_voice_id", "21m00Tcm4TlvDq8ikWAM");
    }

    /**
     * Sets the ElevenLabs voice ID.
     *
     * @param settings configuration json
     * @param voiceId voice id
     * @param saveCallback persistence callback
     */
    public void setElevenLabsVoiceId(JSONObject settings, String voiceId, Runnable saveCallback) {
        settings.put("elevenlabs_voice_id", voiceId);
        saveCallback.run();
    }
}
