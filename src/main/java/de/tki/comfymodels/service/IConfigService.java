package de.tki.comfymodels.service;

import org.json.JSONObject;

/**
 * Interface für Konfigurations- und Settings-Verwaltung.
 * Trennt Interface von Implementation für bessere Testbarkeit.
 */
public interface IConfigService {
    
    // ========================================
    // VAULT & ENCRYPTION
    // ========================================
    
    void unlock(String password) throws Exception;
    boolean isUnlocked();
    boolean hasVault();
    void resetVault();
    boolean isVaultFresh();
    
    // ========================================
    // API KEYS & TOKENS
    // ========================================
    

    String getHfToken();
    void setHfToken(String token);
    
    String getCivitaiApiKey();
    void setCivitaiApiKey(String key);
    
    String getApiToken();
    
    String getElevenLabsApiKey();
    void setElevenLabsApiKey(String apiKey);
    
    String getElevenLabsVoiceId();
    void setElevenLabsVoiceId(String voiceId);
    
    // ========================================
    // PATHS & DIRECTORIES
    // ========================================
    
    String getComfyUIPath();
    void setComfyUIPath(String path);
    
    String getExtraComfyUIPath();
    void setExtraComfyUIPath(String path);
    
    String getModelsPath();
    void setModelsPath(String path);
    
    String getArchivePath();
    void setArchivePath(String path);
    
    String getComfyUIUrl();
    void setComfyUIUrl(String url);
    
    String getPythonPath();
    void setPythonPath(String path);
    
    String getComfyWorkingDir();
    void setComfyWorkingDir(String dir);
    
    String getResolvedInputDetailDir();
    String getResolvedOutputDir();
    
    // ========================================
    // LAUNCH & PROCESS
    // ========================================
    
    String getComfyLaunchCommand();
    void setComfyLaunchCommand(String cmd);
    
    String getActiveProfile();
    void setActiveProfile(String profileId);
    
    // ========================================
    // DOWNLOAD SETTINGS
    // ========================================
    
    int getMaxParallelDownloads();
    void setMaxParallelDownloads(int threads);
    
    int getDownloadSpeedLimit();
    void setDownloadSpeedLimit(int kbps);
    
    int getSegmentsPerFile();
    void setSegmentsPerFile(int segments);
    
    // ========================================
    // FEATURE TOGGLES
    // ========================================
    
    boolean isFastHashEnabled();
    void setFastHashEnabled(boolean enabled);
    
    boolean isBackgroundModeEnabled();
    void setBackgroundModeEnabled(boolean enabled);
    
    boolean isShutdownAfterDownloadEnabled();
    void setShutdownAfterDownloadEnabled(boolean enabled);
    
    boolean isRestartAfterDownloadEnabled();
    void setRestartAfterDownloadEnabled(boolean enabled);
    
    boolean isDarkMode();
    void setDarkMode(boolean enabled);
    
    boolean isPromptLabEnabled();
    void setPromptLabEnabled(boolean enabled);
    
    boolean isVideoArchitectEnabled();
    void setVideoArchitectEnabled(boolean enabled);
    
    boolean isBlueprintGalleryEnabled();
    void setBlueprintGalleryEnabled(boolean enabled);
    
    boolean isHideComfyUI();
    void setHideComfyUI(boolean enabled);
    
    boolean isUseSymlinksOnRestore();
    void setUseSymlinksOnRestore(boolean enabled);
    
    // ========================================
    // AI & LLM SETTINGS
    // ========================================
    
    boolean isUseOllama();
    void setUseOllama(boolean enabled);
    
    String getOllamaUrl();
    void setOllamaUrl(String url);
    
    String getOllamaModel();
    void setOllamaModel(String model);
    
    String getQwenTtsModelRepo();
    void setQwenTtsModelRepo(String repo);
    
    String getQwenTtsModelPath();
    void setQwenTtsModelPath(String path);
    
    boolean isQwenTtsModelAuto();
    void setQwenTtsModelAuto(boolean auto);
    
    String getQwenTtsVoice();
    void setQwenTtsVoice(String voice);
    
    String getTtsProvider();
    void setTtsProvider(String provider);
    
    String getPiperPath();
    void setPiperPath(String path);
    
    String getPiperModelPath();
    void setPiperModelPath(String path);
    
    String getSpeakerImagePath();
    void setSpeakerImagePath(String path);
    
    String getXttsUrl();
    void setXttsUrl(String url);
    
    String getFfmpegPath();
    
    // ========================================
    // PERSISTENCE & STORAGE
    // ========================================
    
    void save();
    void savePromptLabSession(JSONObject data);
    JSONObject getPromptLabSession();
    
    void savePendingDownloads(String json);
    String loadPendingDownloads();
    
    // ========================================
    // AUTO-DISCOVERY & INITIALIZATION
    // ========================================
    
    void autoDiscoverPaths();
    void loadExtraModelPaths();
    void ensureExtraComfyUIDirectories();
    void updateExtraModelPathsYaml();
}
