package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.service.IConfigService;
import de.tki.comfyuicompanion.util.ConfigConstants;
import de.tki.comfyuicompanion.util.ExecutableLocator;
import de.tki.comfyuicompanion.util.PlatformUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Service managing user configuration, encrypted vault credentials,
 * directory paths, and runtime settings for ComfyUI Companion.
 */
@Service
public class ConfigService implements IConfigService {
    private static final Logger logger = LoggerFactory.getLogger(ConfigService.class);

    private final String configFile = ConfigConstants.SETTINGS_FILE;
    private final String vaultFile = ConfigConstants.VAULT_FILE;
    private JSONObject settings = new JSONObject();
    private JSONObject persistentSettings = new JSONObject();
    private String masterPassword = null;
    private boolean vaultFresh = false;

    private final EncryptionUtils encryptionUtils;
    private final PathResolver pathResolver;
    private final ExtraModelPathsYamlSynchronizer yamlSynchronizer;
    private final ComfyAutoDiscoveryService autoDiscoveryService;
    private final VaultStorageService vaultStorageService;
    private final ConfigDirectoryManager directoryManager;

    public ConfigService(EncryptionUtils encryptionUtils, PathResolver pathResolver) {
        this(encryptionUtils, pathResolver, new ExtraModelPathsYamlSynchronizer(), new ComfyAutoDiscoveryService(),
                new VaultStorageService(), new ConfigDirectoryManager());
    }

    public ConfigService(EncryptionUtils encryptionUtils,
                         PathResolver pathResolver,
                         ExtraModelPathsYamlSynchronizer yamlSynchronizer,
                         ComfyAutoDiscoveryService autoDiscoveryService) {
        this(encryptionUtils, pathResolver, yamlSynchronizer, autoDiscoveryService,
                new VaultStorageService(), new ConfigDirectoryManager());
    }

    @Autowired
    public ConfigService(EncryptionUtils encryptionUtils,
                         PathResolver pathResolver,
                         @Autowired(required = false) ExtraModelPathsYamlSynchronizer yamlSynchronizer,
                         @Autowired(required = false) ComfyAutoDiscoveryService autoDiscoveryService,
                         @Autowired(required = false) VaultStorageService vaultStorageService,
                         @Autowired(required = false) ConfigDirectoryManager directoryManager) {
        this.encryptionUtils = encryptionUtils;
        this.pathResolver = pathResolver;
        this.yamlSynchronizer = yamlSynchronizer != null ? yamlSynchronizer : new ExtraModelPathsYamlSynchronizer();
        this.autoDiscoveryService = autoDiscoveryService != null ? autoDiscoveryService : new ComfyAutoDiscoveryService();
        this.vaultStorageService = vaultStorageService != null ? vaultStorageService : new VaultStorageService();
        this.directoryManager = directoryManager != null ? directoryManager : new ConfigDirectoryManager();
        loadPersistentSettings();
    }

    private void loadPersistentSettings() {
        this.persistentSettings = vaultStorageService.loadPersistentSettings(getFileInAppData(configFile));
    }

    private void savePersistentSettings() {
        vaultStorageService.savePersistentSettings(getFileInAppData(configFile), persistentSettings);
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        // Initialization deferred until vault is unlocked.
    }

    /**
     * Determines whether the current JVM process is running within a test suite.
     *
     * @return {@code true} if running in JUnit, Surefire, or custom test appdata
     */
    public boolean isTestEnvironment() {
        String override = System.getProperty("comfyuicompanion.appdata");
        if (override != null && !override.isEmpty()) {
            return true;
        }
        String command = System.getProperty("sun.java.command", "");
        return command.contains("surefire") || command.contains("junit") || command.contains("testng");
    }

    /**
     * Reloads extra model paths from YAML configuration into the path resolver.
     */
    public void loadExtraModelPaths() {
        yamlSynchronizer.loadExtraModelPaths(getComfyUIPath(), isTestEnvironment(), pathResolver);
    }

    /**
     * Automatically scans for ComfyUI roots, Python virtual environments, and working directories.
     */
    public void autoDiscoverPaths() {
        autoDiscoveryService.autoDiscover(settings);
        loadExtraModelPaths();
    }

    /**
     * Discovers the Python executable for a given ComfyUI root directory.
     *
     * @param comfyRoot ComfyUI root folder
     * @return Python path or executable name
     */
    public String discoverPython(String comfyRoot) {
        return autoDiscoveryService.discoverPython(comfyRoot);
    }

    /**
     * Checks whether the application is running in portable mode.
     *
     * @return {@code true} if portable mode is active
     */
    public boolean isPortableMode() {
        return directoryManager.isPortableMode(vaultFile, configFile);
    }

    /**
     * Obtains the application data root path, or a mock path during tests.
     *
     * @return absolute path to application data directory
     */
    public String getAppDataPath() {
        return directoryManager.resolveAppDataPath(vaultFile, configFile);
    }

    /**
     * Resolves a named file inside the application data directory.
     *
     * @param filename file name
     * @return file handle
     */
    public File getFileInAppData(String filename) {
        File appDataDir = new File(getAppDataPath());
        if (!appDataDir.exists()) {
            appDataDir.mkdirs();
        }
        return new File(appDataDir, filename);
    }

    @Override
    public File getUserWorkflowsDir() {
        File dir = new File(getAppDataPath(), "user_workflows");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    @Override
    public File getShippedWorkflowsDir() {
        File shipped = new File("workflows");
        if (!shipped.exists()) {
            shipped = new File(System.getProperty("user.dir"), "workflows");
        }
        return shipped;
    }

    /**
     * Unlocks the encrypted configuration vault using the master password.
     *
     * @param password master vault password
     * @throws Exception if password is wrong or vault is corrupted
     */
    public void unlock(String password) throws Exception {
        File vault = getFileInAppData(vaultFile);
        File legacyConfig = getFileInAppData(configFile);
        VaultStorageService.VaultUnlockResult result = vaultStorageService.unlock(vault, legacyConfig, password, encryptionUtils);
        this.settings = result.settings();
        this.masterPassword = result.masterPassword();
        this.vaultFresh = result.vaultFresh();

        autoDiscoverPaths();
        pathResolver.setComfyUIRoot(getComfyUIPath());
        ensureExtraComfyUIDirectories();
        updateExtraModelPathsYaml();
        loadExtraModelPaths();
    }

    /**
     * Returns whether the vault was freshly initialized.
     *
     * @return true if fresh
     */
    public boolean isVaultFresh() {
        return vaultFresh;
    }

    /**
     * Persists the currently loaded encrypted vault configuration.
     */
    public synchronized void save() {
        vaultStorageService.saveVault(getFileInAppData(vaultFile), settings, masterPassword, encryptionUtils);
    }

    public synchronized String getHfToken() { return settings.optString("hf_token", ""); }
    public synchronized void setHfToken(String token) { settings.put("hf_token", token); save(); }

    public synchronized String getCivitaiApiKey() { return settings.optString("civitai_api_key", ""); }
    public synchronized void setCivitaiApiKey(String key) { settings.put("civitai_api_key", key); save(); }

    public synchronized boolean isFastHashEnabled() { return settings.optBoolean("fast_hash", false); }
    public synchronized void setFastHashEnabled(boolean enabled) { settings.put("fast_hash", enabled); save(); }

    @Override
    public synchronized String getExtraComfyUIPath() {
        return directoryManager.resolveExtraComfyUIPath(settings, isTestEnvironment());
    }

    @Override
    public synchronized void setExtraComfyUIPath(String path) {
        settings.put("extra_comfyui_path", path != null ? path.trim() : "");
        ensureExtraComfyUIDirectories();
        updateExtraModelPathsYaml();
        loadExtraModelPaths();
        save();
    }

    @Override
    public synchronized String getModelsPath() {
        return directoryManager.resolveModelsPath(settings, getExtraComfyUIPath(), getComfyUIPath());
    }

    @Override
    public synchronized void setModelsPath(String path) {
        if (path != null && !path.trim().isEmpty()) {
            String trimmed = path.trim();
            Path p = Paths.get(trimmed);
            if (p.getFileName() != null && p.getFileName().toString().equalsIgnoreCase("models")) {
                Path parent = p.getParent();
                settings.put("extra_comfyui_path", parent != null ? parent.toAbsolutePath().toString() : trimmed);
            } else {
                settings.put("extra_comfyui_path", trimmed);
            }
            settings.put("models_path", trimmed);
        } else {
            settings.remove("models_path");
        }
        ensureExtraComfyUIDirectories();
        updateExtraModelPathsYaml();
        loadExtraModelPaths();
        save();
    }

    public synchronized String getResolvedInputDetailDir() {
        return directoryManager.resolveInputDetailDir(getExtraComfyUIPath());
    }

    /**
     * Ensures mandatory models, input, and output subdirectories exist in the extra ComfyUI directory.
     */
    public synchronized void ensureExtraComfyUIDirectories() {
        directoryManager.ensureExtraComfyUIDirectories(getExtraComfyUIPath());
    }

    public synchronized String getArchivePath() {
        return directoryManager.resolveArchivePath(settings, getModelsPath(), pathResolver);
    }
    public synchronized void setArchivePath(String path) { settings.put("archive_path", path); save(); }

    public synchronized String getResolvedOutputDir() {
        return directoryManager.resolveOutputDir(getExtraComfyUIPath(), getComfyLaunchCommand(), getModelsPath(), getComfyUIPath());
    }

    public synchronized boolean isBackgroundModeEnabled() { return settings.optBoolean("background_mode", false); }
    public synchronized void setBackgroundModeEnabled(boolean enabled) { settings.put("background_mode", enabled); save(); }

    public synchronized boolean isShutdownAfterDownloadEnabled() { return settings.optBoolean("shutdown_after_download", false); }
    public synchronized void setShutdownAfterDownloadEnabled(boolean enabled) { settings.put("shutdown_after_download", enabled); save(); }

    public synchronized boolean isDarkMode() { return persistentSettings.optBoolean("dark_mode", true); }
    public synchronized void setDarkMode(boolean enabled) {
        persistentSettings.put("dark_mode", enabled);
        savePersistentSettings();
    }

    public synchronized boolean isPromptLabEnabled() { return persistentSettings.optBoolean("enable_prompt_lab", false); }
    public synchronized void setPromptLabEnabled(boolean enabled) {
        persistentSettings.put("enable_prompt_lab", enabled);
        savePersistentSettings();
    }

    public synchronized boolean isVideoArchitectEnabled() { return persistentSettings.optBoolean("enable_video_architect", false); }
    public synchronized void setVideoArchitectEnabled(boolean enabled) {
        persistentSettings.put("enable_video_architect", enabled);
        savePersistentSettings();
    }

    public synchronized boolean isBlueprintGalleryEnabled() { return persistentSettings.optBoolean("enable_blueprint_gallery", false); }
    public synchronized void setBlueprintGalleryEnabled(boolean enabled) {
        persistentSettings.put("enable_blueprint_gallery", enabled);
        savePersistentSettings();
    }

    /**
     * Checks if the Civitai Model Hub gallery tab is enabled. Defaults to false.
     *
     * @return true if enabled, false otherwise
     */
    public synchronized boolean isCivitaiGalleryEnabled() { return persistentSettings.optBoolean("enable_civitai_gallery", false); }

    /**
     * Updates persistent configuration for the Civitai Model Hub gallery tab.
     *
     * @param enabled true to enable, false to disable
     */
    public synchronized void setCivitaiGalleryEnabled(boolean enabled) {
        persistentSettings.put("enable_civitai_gallery", enabled);
        savePersistentSettings();
    }

    public synchronized void savePromptLabSession(JSONObject data) {
        persistentSettings.put("prompt_lab_session", data);
        savePersistentSettings();
    }

    public synchronized JSONObject getPromptLabSession() {
        return persistentSettings.optJSONObject("prompt_lab_session");
    }

    public synchronized void savePendingDownloads(String json) {
        vaultStorageService.savePendingDownloads(getFileInAppData("pending_downloads.json"), json);
    }

    public synchronized String loadPendingDownloads() {
        return vaultStorageService.loadPendingDownloads(getFileInAppData("pending_downloads.json"));
    }

    public synchronized String getComfyUIPath() {
        return directoryManager.resolveComfyUIPath(settings, isTestEnvironment());
    }

    public synchronized void setComfyUIPath(String path) {
        settings.put("comfyui_path", path);
        pathResolver.setComfyUIRoot(path);
        save();
        updateExtraModelPathsYaml();
        loadExtraModelPaths();
    }

    public synchronized String getPythonPath() {
        String path = settings.optString("python_path", "");
        return path.isEmpty() ? discoverPython(getComfyUIPath()) : path;
    }
    public synchronized void setPythonPath(String path) { settings.put("python_path", path); save(); }

    public synchronized String getComfyLaunchCommand() { return settings.optString("comfy_launch_command", ""); }
    public synchronized void setComfyLaunchCommand(String cmd) { settings.put("comfy_launch_command", cmd); save(); }

    public synchronized String getActiveProfile() { return settings.optString("active_profile", ""); }
    public synchronized void setActiveProfile(String profileId) { settings.put("active_profile", profileId); save(); }

    public synchronized String getComfyWorkingDir() { return settings.optString("comfy_working_dir", ""); }
    public synchronized void setComfyWorkingDir(String dir) { settings.put("comfy_working_dir", dir); save(); }

    public synchronized boolean isRestartAfterDownloadEnabled() { return settings.optBoolean("restart_after_download", false); }
    public synchronized void setRestartAfterDownloadEnabled(boolean enabled) { settings.put("restart_after_download", enabled); save(); }

    public synchronized String getComfyUIUrl() {
        String url = settings.optString("comfyui_url", ConfigConstants.DEFAULT_COMFYUI_URL);
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
    public synchronized void setComfyUIUrl(String url) {
        if (url != null && !url.isEmpty()) {
            String sanitized = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
            settings.put("comfyui_url", sanitized);
            save();
        }
    }

    public synchronized String getApiToken() {
        return settings.optString("api_token", "");
    }

    public boolean isUnlocked() {
        return masterPassword != null;
    }

    public boolean hasVault() {
        return getFileInAppData(vaultFile).exists();
    }

    public synchronized int getMaxParallelDownloads() { return settings.optInt("max_parallel_downloads", 3); }
    public synchronized void setMaxParallelDownloads(int threads) { settings.put("max_parallel_downloads", Math.max(1, Math.min(10, threads))); save(); }

    public synchronized int getDownloadSpeedLimit() { return settings.optInt("download_speed_limit", 0); }
    public synchronized void setDownloadSpeedLimit(int kbps) { settings.put("download_speed_limit", Math.max(0, kbps)); save(); }

    public synchronized int getSegmentsPerFile() { return settings.optInt("segments_per_file", 4); }
    public synchronized void setSegmentsPerFile(int segments) { settings.put("segments_per_file", Math.max(1, Math.min(8, segments))); save(); }

    public synchronized boolean isHideComfyUI() { return settings.optBoolean("hide_comfyui", true); }
    public synchronized void setHideComfyUI(boolean enabled) { settings.put("hide_comfyui", enabled); save(); }

    public synchronized boolean isUseSymlinksOnRestore() { return settings.optBoolean("use_symlinks_on_restore", false); }
    public synchronized void setUseSymlinksOnRestore(boolean enabled) { settings.put("use_symlinks_on_restore", enabled); save(); }

    public synchronized String getXttsUrl() { return settings.optString("xtts_url", "http://localhost:8020/api/tts"); }
    public synchronized void setXttsUrl(String url) { settings.put("xtts_url", url); save(); }

    public synchronized String getPiperPath() {
        String defaultPath = PlatformUtils.isWindows() ? "./tools/tts/piper.exe" : "./tools/tts/piper";
        String configuredPath = settings.optString("piper_path", defaultPath);
        File binFile = new File(configuredPath);
        if (binFile.exists()) {
            return configuredPath;
        }
        File fallbackBin = new File(System.getProperty("user.dir"), configuredPath);
        if (fallbackBin.exists()) {
            return fallbackBin.getAbsolutePath();
        }
        String found = ExecutableLocator.findExecutablePath("piper", configuredPath, getPythonPath());
        return found != null ? found : configuredPath;
    }
    public synchronized void setPiperPath(String path) { settings.put("piper_path", path); save(); }

    public synchronized String getSpeakerImagePath() { return settings.optString("speaker_image", ""); }
    public synchronized void setSpeakerImagePath(String path) { settings.put("speaker_image", path); save(); }

    public synchronized String getPiperModelPath() {
        return settings.optString("piper_model_path", "./tools/tts/en_model.onnx");
    }
    public synchronized void setPiperModelPath(String path) { settings.put("piper_model_path", path); save(); }

    public synchronized String getTtsProvider() { return settings.optString("tts_provider", "ComfyUI Qwen-TTS"); }
    public synchronized void setTtsProvider(String provider) { settings.put("tts_provider", provider); save(); }

    public synchronized String getQwenTtsModelRepo() {
        return settings.optString("qwen_tts_model_repo", "Qwen/Qwen2-Audio-7B-Instruct");
    }
    public synchronized void setQwenTtsModelRepo(String repo) { settings.put("qwen_tts_model_repo", repo); save(); }

    public synchronized String getQwenTtsModelPath() {
        return settings.optString("qwen_tts_model_path", System.getProperty("user.home") + "/.cache/huggingface/hub");
    }
    public synchronized void setQwenTtsModelPath(String path) { settings.put("qwen_tts_model_path", path); save(); }

    public synchronized boolean isQwenTtsModelAuto() {
        return settings.optBoolean("qwen_tts_model_auto", true);
    }
    public synchronized void setQwenTtsModelAuto(boolean auto) {
        settings.put("qwen_tts_model_auto", auto);
        save();
    }

    public synchronized String getQwenTtsVoice() {
        return settings.optString("qwen_tts_voice", "Chelsie");
    }
    public synchronized void setQwenTtsVoice(String voice) { settings.put("qwen_tts_voice", voice); save(); }

    public synchronized String getElevenLabsApiKey() { return ""; }
    public synchronized void setElevenLabsApiKey(String apiKey) { settings.remove("elevenlabs_api_key"); save(); }

    public synchronized String getElevenLabsVoiceId() { return settings.optString("elevenlabs_voice_id", "21m00Tcm4TlvDq8ikWAM"); }
    public synchronized void setElevenLabsVoiceId(String voiceId) { settings.put("elevenlabs_voice_id", voiceId); save(); }

    public synchronized String getFfmpegPath() {
        String configured = settings.optString("ffmpeg_path", "");
        String found = ExecutableLocator.findExecutablePath("ffmpeg", configured, getPythonPath());
        return found != null ? found : "ffmpeg";
    }

    /**
     * Resets the encrypted vault and restores the fresh state.
     */
    public void resetVault() {
        vaultStorageService.resetVault(getFileInAppData(vaultFile));
        this.settings = new JSONObject();
        this.masterPassword = null;
        this.vaultFresh = true;
        logger.info("Vault has been reset.");
    }

    /**
     * Updates extra model paths YAML to reflect the companion configuration.
     */
    public void updateExtraModelPathsYaml() {
        yamlSynchronizer.updateExtraModelPathsYaml(getComfyUIPath(), getModelsPath());
    }
}
