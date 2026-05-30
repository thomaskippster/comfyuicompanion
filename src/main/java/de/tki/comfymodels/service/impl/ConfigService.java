package de.tki.comfymodels.service.impl;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Service
public class ConfigService {
    private final String CONFIG_FILE = "app_settings.json";
    private final String VAULT_FILE = "settings.vault";
    private JSONObject settings = new JSONObject();
    private JSONObject persistentSettings = new JSONObject(); // Plain settings (not in vault)
    private String masterPassword = null;
    private boolean vaultFresh = false;

    private final EncryptionUtils encryptionUtils;
    private final PathResolver pathResolver;

    @Autowired
    public ConfigService(EncryptionUtils encryptionUtils, PathResolver pathResolver) {
        this.encryptionUtils = encryptionUtils;
        this.pathResolver = pathResolver;
        loadPersistentSettings();
    }

    private void loadPersistentSettings() {
        File file = getFileInAppData(CONFIG_FILE);
        if (file.exists()) {
            try {
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                persistentSettings = new JSONObject(content);
            } catch (Exception e) {
                System.err.println("Error loading persistent settings: " + e.getMessage());
            }
        }
    }

    private void savePersistentSettings() {
        try {
            Files.writeString(getFileInAppData(CONFIG_FILE).toPath(), persistentSettings.toString(4), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("Error saving persistent settings: " + e.getMessage());
        }
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        autoDiscoverPaths();
        pathResolver.setComfyUIRoot(getComfyUIPath());
        loadExtraModelPaths();
        ensureExtraComfyUIDirectories();
    }

    public void loadExtraModelPaths() {
        pathResolver.clearExtraModelPaths();
        String comfyRoot = getComfyUIPath();
        if (comfyRoot == null || comfyRoot.isEmpty()) return;

        Path extraPathsFile = Paths.get(comfyRoot).resolve("extra_model_paths.yaml");
        if (!Files.exists(extraPathsFile)) {
            // Check AppData common location as provided in user hint
            String userHome = System.getProperty("user.home");
            extraPathsFile = Paths.get(userHome, "AppData/Roaming/ComfyUI/extra_models_config.yaml");
        }

        if (Files.exists(extraPathsFile)) {
            System.out.println("📄 [Config] Loading extra model paths from: " + extraPathsFile.toAbsolutePath());
            try (InputStream is = new FileInputStream(extraPathsFile.toFile())) {
                Yaml yaml = new Yaml();
                Map<String, Object> data = yaml.load(is);
                if (data != null) {
                    for (Map.Entry<String, Object> entry : data.entrySet()) {
                        if (entry.getValue() instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> section = (Map<String, Object>) entry.getValue();
                            String basePathStr = (String) section.get("base_path");
                            if (basePathStr == null) continue;
                            
                            // base_path can be relative to the YAML file
                            Path yamlDir = extraPathsFile.getParent();
                            Path basePath = Paths.get(basePathStr);
                            if (!basePath.isAbsolute()) {
                                basePath = yamlDir.resolve(basePath).toAbsolutePath().normalize();
                            }
                            
                            System.out.println("📂 [Config] Section '" + entry.getKey() + "' base_path: " + basePath);

                            for (Map.Entry<String, Object> config : section.entrySet()) {
                                if (config.getKey().equals("base_path")) continue;
                                
                                String type = config.getKey();
                                Object value = config.getValue();
                                
                                if (value instanceof String) {
                                    String[] folders = ((String) value).split("\\R");
                                    for (String folder : folders) {
                                        String trimmed = folder.trim();
                                        if (!trimmed.isEmpty()) {
                                            Path fullPath = basePath.resolve(trimmed).toAbsolutePath().normalize();
                                            if (!Files.exists(fullPath)) {
                                                System.err.println("   ⚠️ [Config] Warning: Path does not exist: " + fullPath);
                                            }
                                            pathResolver.addExtraModelPath(type, fullPath);
                                            System.out.println("   -> Mapping [" + type + "] to: " + fullPath);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ [Config] Error parsing extra_model_paths.yaml: " + e.getMessage());
            }
        }
    }

    public void autoDiscoverPaths() {
        String root = settings.optString("comfyui_path", "");
        String userHome = System.getProperty("user.home");
        String currentDir = System.getProperty("user.dir");

        System.out.println("🔍 [Config] Starting auto-discovery. Stored Root: " + root);

        // 1. VALIDATE AND DISCOVER COMFYUI ROOT
        boolean rootValid = !root.isEmpty() && new File(root, "main.py").exists();
        
        if (!rootValid && !root.isEmpty()) {
            System.out.println("⚠️ [Config] main.py not found in current root. Checking subdirectories...");
            // Check for nested Pinokio structure: resources/ComfyUI
            File nested = new File(root, "resources/ComfyUI");
            if (new File(nested, "main.py").exists()) {
                root = nested.getAbsolutePath();
                rootValid = true;
                System.out.println("✨ [Config] Found main.py in nested Pinokio path: " + root);
                setComfyUIPath(root);
            } else {
                // Current root is definitively wrong, reset it to allow re-discovery
                System.out.println("🚫 [Config] Current root is invalid. Resetting for re-discovery.");
                root = "";
            }
        }

        if (root.isEmpty()) {
            // Priority 1: Environment Variables
            String[] envVars = {"COMFYUI_PATH", "COMFYUI_HOME", "PINOKIO_BIN"};
            for (String var : envVars) {
                String val = System.getenv(var);
                if (val != null && !val.isEmpty()) {
                    File f = new File(val);
                    if (new File(f, "main.py").exists()) {
                        root = f.getAbsolutePath();
                        System.out.println("✨ [Config] Found Root via Env Var " + var + ": " + root);
                        break;
                    }
                }
            }

            // Priority 2: Check if we are running INSIDE a ComfyUI directory
            if (root.isEmpty()) {
                File possibleRoot = findMainPyNearby(new File(currentDir));
                if (possibleRoot != null) {
                    root = possibleRoot.getAbsolutePath();
                    System.out.println("✨ [Config] Found Root via Nearby Search: " + root);
                }
            }
            
            // Priority 3: Check common Pinokio/Home locations
            if (root.isEmpty()) {
                java.util.List<String> patterns = new java.util.ArrayList<>();
                if (de.tki.comfymodels.util.PlatformUtils.isWindows()) {
                    patterns.add(userHome + "\\ComfyUI");
                    patterns.add(userHome + "\\AppData\\Roaming\\pinokio\\bin\\ComfyUI");
                } else {
                    patterns.add(userHome + "/ComfyUI");
                    patterns.add(userHome + "/pinokio/bin/ComfyUI");
                    patterns.add(userHome + "/.local/share/pinokio/bin/ComfyUI");
                }
                for (String p : patterns) {
                    File f = new File(p);
                    if (new File(f, "main.py").exists()) {
                        root = f.getAbsolutePath();
                        System.out.println("✨ [Config] Found Root via Pattern: " + root);
                        break;
                    } else {
                        // Try nested resources/ComfyUI for patterns too
                        File nested = new File(f, "resources/ComfyUI");
                        if (new File(nested, "main.py").exists()) {
                            root = nested.getAbsolutePath();
                            System.out.println("✨ [Config] Found Root via Nested Pattern: " + root);
                            break;
                        }
                    }
                }
            }

            if (!root.isEmpty()) {
                setComfyUIPath(root);
            }
        }

        // 2. DISCOVER MODELS DIR
        String currentModelsPath = settings.optString("models_path", "");
        if (currentModelsPath.isEmpty() || currentModelsPath.equals(PathResolver.MODELS_DIR)) {
            // Check for Pinokio data structure: sibling of ComfyUI's main installation folder
            // e.g. ComfyUI (binary) -> comfyuidata/models (data)
            File comfyFolder = new File(root);
            while (comfyFolder != null) {
                File parent = comfyFolder.getParentFile();
                if (parent != null) {
                    File dataModels = new File(parent, "comfyuidata/models");
                    if (dataModels.exists() && dataModels.isDirectory()) {
                        System.out.println("✨ [Config] Found models via Pinokio Data Path: " + dataModels.getAbsolutePath());
                        setModelsPath(dataModels.getAbsolutePath());
                        break;
                    }
                    // Try case-insensitive variant or simplified name
                    File altData = new File(parent, "data/models");
                    if (altData.exists() && altData.isDirectory()) {
                        System.out.println("✨ [Config] Found models via Data Path: " + altData.getAbsolutePath());
                        setModelsPath(altData.getAbsolutePath());
                        break;
                    }
                }
                comfyFolder = comfyFolder.getParentFile();
                if (comfyFolder != null && comfyFolder.getName().equalsIgnoreCase("AI")) break; // Don't go too high
            }
        }

        // 3. DISCOVER WORKING DIR
        if ((getComfyWorkingDir().isEmpty() || !rootValid) && !root.isEmpty()) {
            setComfyWorkingDir(root);
            System.out.println("📁 [Config] Set Working Dir: " + root);
        }

        // 3. DISCOVER PYTHON & CONSTRUCT COMMAND
        // Always re-validate command if root changed or command is empty
        String python = getPythonPath();
        if (!isPythonValid(python)) {
            python = discoverPython(root);
            if (isPythonValid(python) && !python.equals("python") && !python.equals("python3")) {
                setPythonPath(python);
            }
        }

        if ((getComfyLaunchCommand().isEmpty() || !rootValid) && !root.isEmpty()) {
            File mainPy = new File(root, "main.py");
            
            if (mainPy.exists()) {
                int port = 8188;
                try {
                    String url = getComfyUIUrl();
                    int colonIndex = url.lastIndexOf(":");
                    if (colonIndex != -1) {
                        port = Integer.parseInt(url.substring(colonIndex + 1));
                    }
                } catch (Exception ignored) {}
                String cmd = String.format("\"%s\" \"%s\" --listen 127.0.0.1 --port %d --enable-manager --extra-model-paths-config \"%s\"", 
                    python, mainPy.getAbsolutePath(), port, new File(root, "extra_model_paths.yaml").getAbsolutePath());
                setComfyLaunchCommand(cmd);
                System.out.println("🚀 [Config] Generated Launch Command: " + cmd);
            } else {
                System.err.println("❌ [Config] Could not find main.py in " + root);
            }
        } else if (!getComfyLaunchCommand().isEmpty()) {
            System.out.println("✅ [Config] Launch command already present and likely valid: " + getComfyLaunchCommand());
        }

        // 4. RELOAD EXTRA PATHS
        loadExtraModelPaths();
    }

    private boolean isPythonValid(String python) {
        if (python == null || python.isEmpty()) return false;
        if (python.equalsIgnoreCase("python") || python.equalsIgnoreCase("python3")) return true;
        return new File(python).exists();
    }

    private File findMainPyNearby(File start) {
        File current = start;
        while (current != null) {
            if (new File(current, "main.py").exists()) return current;
            if (current.getName().equalsIgnoreCase("custom_nodes")) {
                File parent = current.getParentFile();
                if (parent != null && new File(parent, "main.py").exists()) return parent;
            }
            current = current.getParentFile();
        }
        return null;
    }

    public String discoverPython(String comfyRoot) {
        System.out.println("🐍 [Config] Searching for Python in/near: " + comfyRoot);
        // Priority 1: Check for .venv in comfy root or parent (Pinokio style)
        File[] venvLocations = {
            new File(comfyRoot, ".venv"),
            new File(new File(comfyRoot).getParentFile(), ".venv"),
            new File(new File(comfyRoot).getParentFile().getParentFile(), ".venv") // Try 2 levels up
        };

        for (File venv : venvLocations) {
            if (venv != null && venv.exists()) {
                System.out.println("📂 [Config] Found venv at: " + venv.getAbsolutePath());
                boolean isWin = isWindows();
                File bin = new File(venv, isWin ? "Scripts/python.exe" : "bin/python3");
                if (bin.exists()) return bin.getAbsolutePath();
                bin = new File(venv, isWin ? "Scripts/python" : "bin/python");
                if (bin.exists()) return bin.getAbsolutePath();
            }
        }
        
        // Priority 2: Check for python_embeded (Portable ComfyUI style)
        File portablePython = new File(new File(comfyRoot).getParentFile(), "python_embeded/python.exe");
        if (portablePython.exists()) {
            System.out.println("📂 [Config] Found portable python at: " + portablePython.getAbsolutePath());
            return portablePython.getAbsolutePath();
        }

        System.out.println("⚠️ [Config] No venv found, falling back to system python.");
        return isWindows() ? "python" : "python3";
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /**
     * Use the current working directory for application data, or override via system property for tests.
     */
    public String getAppDataPath() {
        String override = System.getProperty("comfyuicompanion.appdata");
        if (override != null && !override.isEmpty()) {
            return override;
        }
        return System.getProperty("user.dir");
    }

    public File getFileInAppData(String filename) {
        File appDataDir = new File(getAppDataPath());
        if (!appDataDir.exists()) {
            appDataDir.mkdirs();
        }
        return new File(appDataDir, filename);
    }

    public void unlock(String password) throws Exception {
        File vault = getFileInAppData(VAULT_FILE);
        System.out.println("Attempting to unlock vault at: " + vault.getAbsolutePath());
        
        if (vault.exists()) {
            String encrypted = Files.readString(vault.toPath(), StandardCharsets.UTF_8);
            try {
                String decrypted = encryptionUtils.decrypt(encrypted, password);
                JSONObject decryptedJson = new JSONObject(decrypted);
                this.settings = decryptedJson;
                this.masterPassword = password;
                System.out.println("Vault unlocked successfully. Keys found: " + settings.keySet());
                updateExtraModelPathsYaml();
            } catch (Exception e) {
                System.err.println("Failed to unlock vault: " + e.getMessage());
                throw new Exception("Wrong password or corrupted vault!");
            }
        } else {
            this.masterPassword = password;
            File oldFile = getFileInAppData(CONFIG_FILE);
            boolean migrated = false;
            if (oldFile.exists()) {
                try {
                    String content = Files.readString(oldFile.toPath(), StandardCharsets.UTF_8);
                    JSONObject oldJson = new JSONObject(content);
                    
                    JSONObject legacyVaultSettings = new JSONObject();
                    String[] vaultKeys = {
                        "gemini_api_key", "hf_token", "models_path", "archive_path",
                        "background_mode", "shutdown_after_download", "comfyui_path",
                        "python_path", "comfy_launch_command", "comfy_working_dir",
                        "restart_after_download", "comfyui_url", "api_token"
                    };
                    
                    for (String key : vaultKeys) {
                        if (oldJson.has(key)) {
                            legacyVaultSettings.put(key, oldJson.get(key));
                            oldJson.remove(key);
                        }
                    }
                    
                    if (!legacyVaultSettings.keySet().isEmpty()) {
                        this.settings = legacyVaultSettings;
                        save();
                        if (oldJson.keySet().isEmpty()) {
                            oldFile.delete();
                        } else {
                            Files.writeString(oldFile.toPath(), oldJson.toString(4), StandardCharsets.UTF_8);
                        }
                        System.out.println("Migrated old settings to encrypted vault.");
                        migrated = true;
                    }
                } catch (Exception e) {
                    System.err.println("Failed to migrate old settings: " + e.getMessage());
                }
            }
            
            if (!migrated) {
                System.out.println("No vault found, initialized new empty vault at: " + vault.getAbsolutePath());
                this.vaultFresh = true;
                save();
            }
        }
    }

    public boolean isVaultFresh() {
        return vaultFresh;
    }

    public void save() {
        if (masterPassword == null) {
            System.err.println("Cannot save: Vault not unlocked.");
            return;
        }
        try {
            String encrypted = encryptionUtils.encrypt(settings.toString(), masterPassword);
            Files.writeString(getFileInAppData(VAULT_FILE).toPath(), encrypted, StandardCharsets.UTF_8);
            System.out.println("Vault saved successfully to: " + getFileInAppData(VAULT_FILE).getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Error saving vault: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public String getGeminiApiKey() { return settings.optString("gemini_api_key", ""); }
    public void setGeminiApiKey(String key) { settings.put("gemini_api_key", key); save(); }

    public String getHfToken() { return settings.optString("hf_token", ""); }
    public void setHfToken(String token) { settings.put("hf_token", token); save(); }

    public String getCivitaiApiKey() { return settings.optString("civitai_api_key", ""); }
    public void setCivitaiApiKey(String key) { settings.put("civitai_api_key", key); save(); }

    public boolean isFastHashEnabled() { return settings.optBoolean("fast_hash", false); }
    public void setFastHashEnabled(boolean enabled) { settings.put("fast_hash", enabled); save(); }

    public String getExtraComfyUIPath() {
        String path = settings.optString("models_path", PathResolver.MODELS_DIR);
        Path p = Paths.get(path);
        
        // If absolute path
        if (p.isAbsolute()) {
            if (p.getFileName() != null && p.getFileName().toString().equalsIgnoreCase("models")) {
                Path parent = p.getParent();
                return parent != null ? parent.toString() : p.toString();
            }
            return p.toString();
        }
        
        // If relative path
        String comfyRoot = getComfyUIPath();
        if (comfyRoot != null && !comfyRoot.isEmpty()) {
            return comfyRoot;
        }
        return Paths.get(".").toAbsolutePath().normalize().toString();
    }

    public String getModelsPath() { 
        String extraPath = getExtraComfyUIPath();
        return Paths.get(extraPath).resolve("models").toAbsolutePath().toString();
    }
    
    public void setModelsPath(String path) { 
        settings.put("models_path", path); 
        save(); 
        ensureExtraComfyUIDirectories();
        updateExtraModelPathsYaml(); 
    }

    public String getResolvedInputDetailDir() {
        String extraPath = getExtraComfyUIPath();
        return Paths.get(extraPath).resolve("input").toAbsolutePath().toString();
    }

    public void ensureExtraComfyUIDirectories() {
        try {
            String extraPath = getExtraComfyUIPath();
            if (extraPath != null && !extraPath.isEmpty()) {
                Path base = Paths.get(extraPath);
                Path models = base.resolve("models");
                Path input = base.resolve("input");
                Path output = base.resolve("output");
                
                if (!Files.exists(models)) Files.createDirectories(models);
                if (!Files.exists(input)) Files.createDirectories(input);
                if (!Files.exists(output)) Files.createDirectories(output);
            }
        } catch (Exception e) {
            System.err.println("⚠️ [Config] Failed to create extra ComfyUI directories: " + e.getMessage());
        }
    }

    public String getArchivePath() { 
        String path = settings.optString("archive_path", "");
        if (path.isEmpty()) {
            String userHome = System.getProperty("user.home");
            if (de.tki.comfymodels.util.PlatformUtils.isWindows()) {
                return userHome + "\\ComfyUI-Archive";
            } else {
                return userHome + "/ComfyUI-Archive";
            }
        }
        Path modelsPath = Paths.get(getModelsPath());
        return pathResolver.resolveArchivePath(modelsPath, path).toString();
    }
    public void setArchivePath(String path) { settings.put("archive_path", path); save(); }

    public String getResolvedOutputDir() {
        // Option 1: Use the extra ComfyUI path's "output" subfolder if resolved
        String extraPath = getExtraComfyUIPath();
        if (extraPath != null && !extraPath.isEmpty()) {
            return Paths.get(extraPath).resolve("output").toAbsolutePath().toString();
        }

        // Candidate 1: Check if specified in CLI launch command via --output-directory
        String launchCmd = getComfyLaunchCommand();
        if (launchCmd != null && !launchCmd.isEmpty()) {
            try {
                java.util.List<String> parsedCmd = de.tki.comfymodels.util.PlatformUtils.parseCommandLine(launchCmd);
                for (int i = 0; i < parsedCmd.size() - 1; i++) {
                    if (parsedCmd.get(i).equals("--output-directory")) {
                        Path p = Paths.get(parsedCmd.get(i + 1));
                        if (Files.exists(p) && Files.isDirectory(p)) {
                            return p.toAbsolutePath().toString();
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // Candidate 2: Sibling of the configured models directory (e.g. if models is comfyuidata/models, output could be comfyuidata/output)
        String modelsPathStr = getModelsPath();
        if (modelsPathStr != null && !modelsPathStr.isEmpty()) {
            try {
                Path modelsPath = Paths.get(modelsPathStr);
                if (modelsPath.getParent() != null) {
                    Path siblingOutput = modelsPath.getParent().resolve("output");
                    if (Files.exists(siblingOutput) && Files.isDirectory(siblingOutput)) {
                        return siblingOutput.toAbsolutePath().toString();
                    }
                }
            } catch (Exception ignored) {}
        }

        // Candidate 3: Read extra_model_paths.yaml for standard "comfyui" section base_path
        String comfyRoot = getComfyUIPath();
        if (comfyRoot != null && !comfyRoot.isEmpty()) {
            try {
                Path extraPathsFile = Paths.get(comfyRoot).resolve("extra_model_paths.yaml");
                if (!Files.exists(extraPathsFile)) {
                    String userHome = System.getProperty("user.home");
                    extraPathsFile = Paths.get(userHome, "AppData/Roaming/ComfyUI/extra_models_config.yaml");
                }
                if (Files.exists(extraPathsFile)) {
                    try (InputStream is = new FileInputStream(extraPathsFile.toFile())) {
                        Yaml yaml = new Yaml();
                        Map<String, Object> data = yaml.load(is);
                        if (data != null && data.get("comfyui") instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> comfySec = (Map<String, Object>) data.get("comfyui");
                            String basePathStr = (String) comfySec.get("base_path");
                            if (basePathStr != null) {
                                Path basePath = Paths.get(basePathStr);
                                if (!basePath.isAbsolute()) {
                                    basePath = extraPathsFile.getParent().resolve(basePath).toAbsolutePath().normalize();
                                }
                                Path yamlOutput = basePath.resolve("output");
                                if (Files.exists(yamlOutput) && Files.isDirectory(yamlOutput)) {
                                    return yamlOutput.toAbsolutePath().toString();
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // Candidate 4: Default ComfyUI/output folder
        if (comfyRoot != null && !comfyRoot.isEmpty()) {
            return Paths.get(comfyRoot).resolve("output").toAbsolutePath().toString();
        }
        
        return Paths.get("output").toAbsolutePath().toString();
    }

    public boolean isBackgroundModeEnabled() { return settings.optBoolean("background_mode", false); }
    public void setBackgroundModeEnabled(boolean enabled) { settings.put("background_mode", enabled); save(); }

    public boolean isShutdownAfterDownloadEnabled() { return settings.optBoolean("shutdown_after_download", false); }
    public void setShutdownAfterDownloadEnabled(boolean enabled) { settings.put("shutdown_after_download", enabled); save(); }

    public boolean isDarkMode() { return persistentSettings.optBoolean("dark_mode", true); }
    public void setDarkMode(boolean enabled) { 
        persistentSettings.put("dark_mode", enabled); 
        savePersistentSettings();
    }

    public void savePendingDownloads(String json) {
        try {
            Files.writeString(getFileInAppData("pending_downloads.json").toPath(), json, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("Error saving pending downloads: " + e.getMessage());
        }
    }

    public String loadPendingDownloads() {
        try {
            File file = getFileInAppData("pending_downloads.json");
            if (file.exists()) {
                return Files.readString(file.toPath(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            System.err.println("Error loading pending downloads: " + e.getMessage());
        }
        return null;
    }

    public String getComfyUIPath() {
        String path = settings.optString("comfyui_path", "");
        if (path.isEmpty()) {
            String userHome = System.getProperty("user.home");
            if (de.tki.comfymodels.util.PlatformUtils.isWindows()) {
                return userHome + "\\ComfyUI";
            } else {
                return userHome + "/ComfyUI";
            }
        }
        return path;
    }
    public void setComfyUIPath(String path) { 
        settings.put("comfyui_path", path); 
        pathResolver.setComfyUIRoot(path);
        save(); 
        loadExtraModelPaths(); // Ensure extra paths are reloaded when root changes
        updateExtraModelPathsYaml();
    }

    public String getPythonPath() {
        String path = settings.optString("python_path", "");
        if (path.isEmpty()) {
            return discoverPython(getComfyUIPath());
        }
        return path;
    }
    public void setPythonPath(String path) { settings.put("python_path", path); save(); }

    public String getComfyLaunchCommand() { return settings.optString("comfy_launch_command", ""); }
    public void setComfyLaunchCommand(String cmd) { settings.put("comfy_launch_command", cmd); save(); }

    public String getActiveProfile() { return settings.optString("active_profile", ""); }
    public void setActiveProfile(String profileId) { settings.put("active_profile", profileId); save(); }

    public String getComfyWorkingDir() { return settings.optString("comfy_working_dir", ""); }
    public void setComfyWorkingDir(String dir) { settings.put("comfy_working_dir", dir); save(); }

    public boolean isRestartAfterDownloadEnabled() { return settings.optBoolean("restart_after_download", false); }
    public void setRestartAfterDownloadEnabled(boolean enabled) { settings.put("restart_after_download", enabled); save(); }

    public String getComfyUIUrl() {
        String url = settings.optString("comfyui_url", "http://127.0.0.1:8188");
        
        // Force upgrade legacy 8000 port to 8188
        if (url.contains(":8000")) {
            System.out.println("🔧 [Config] Upgrading legacy ComfyUI port 8000 to 8188.");
            url = url.replace(":8000", ":8188");
            setComfyUIUrl(url);
        }
        
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
    public void setComfyUIUrl(String url) { 
        if (url != null && !url.isEmpty()) {
            String sanitized = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
            settings.put("comfyui_url", sanitized); 
            save(); 
        }
    }

    public String getApiToken() {
        String token = settings.optString("api_token", "");
        if (token.isEmpty() && isUnlocked()) {
            token = java.util.UUID.randomUUID().toString().replace("-", "");
            settings.put("api_token", token);
            save();
        }
        return token;
    }

    public boolean isUnlocked() {
        return masterPassword != null;
    }

    public boolean hasVault() {
        return getFileInAppData(VAULT_FILE).exists();
    }

    public int getMaxParallelDownloads() { return settings.optInt("max_parallel_downloads", 3); }
    public void setMaxParallelDownloads(int threads) { settings.put("max_parallel_downloads", Math.max(1, Math.min(10, threads))); save(); }

    public int getDownloadSpeedLimit() { return settings.optInt("download_speed_limit", 0); }
    public void setDownloadSpeedLimit(int kbps) { settings.put("download_speed_limit", Math.max(0, kbps)); save(); }

    public int getSegmentsPerFile() { return settings.optInt("segments_per_file", 4); }
    public void setSegmentsPerFile(int segments) { settings.put("segments_per_file", Math.max(1, Math.min(8, segments))); save(); }

    public boolean isUseOllama() { return settings.optBoolean("use_ollama", false); }
    public void setUseOllama(boolean enabled) { settings.put("use_ollama", enabled); save(); }

    public String getOllamaUrl() { return settings.optString("ollama_url", "http://localhost:11434"); }
    public void setOllamaUrl(String url) { settings.put("ollama_url", url); save(); }

    public String getOllamaModel() { return settings.optString("ollama_model", "llama3"); }
    public void setOllamaModel(String model) { settings.put("ollama_model", model); save(); }

    public boolean isUseSymlinksOnRestore() { return settings.optBoolean("use_symlinks_on_restore", false); }
    public void setUseSymlinksOnRestore(boolean enabled) { settings.put("use_symlinks_on_restore", enabled); save(); }

    public void resetVault() {
        File vault = getFileInAppData(VAULT_FILE);
        if (vault.exists()) {
            vault.delete();
        }
        this.settings = new JSONObject();
        this.masterPassword = null;
        this.vaultFresh = true;
        System.out.println("Vault has been reset.");
    }

    public void updateExtraModelPathsYaml() {
        String comfyRoot = getComfyUIPath();
        String modelsPath = getModelsPath();
        if (modelsPath == null || modelsPath.isEmpty()) return;
        
        // Normalize backslashes to forward slashes to prevent escape sequence parsing issues in YAML
        modelsPath = modelsPath.replace("\\", "/");

        List<Path> targetYamlFiles = new java.util.ArrayList<>();
        if (comfyRoot != null && !comfyRoot.isEmpty()) {
            File comfyDir = new File(comfyRoot);
            if (comfyDir.exists() && comfyDir.isDirectory()) {
                targetYamlFiles.add(Paths.get(comfyRoot).resolve("extra_model_paths.yaml"));
            }
        }

        // Also check AppData location for ComfyUI Desktop configuration file
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            Path appDataDir = Paths.get(userHome, "AppData", "Roaming", "ComfyUI");
            if (Files.exists(appDataDir) && Files.isDirectory(appDataDir)) {
                targetYamlFiles.add(appDataDir.resolve("extra_models_config.yaml"));
            }
        }

        if (targetYamlFiles.isEmpty()) return;

        for (Path yamlPath : targetYamlFiles) {
            Map<String, Object> data = null;
            try {
                Yaml yaml = new Yaml();
                if (Files.exists(yamlPath)) {
                    try (InputStream is = new FileInputStream(yamlPath.toFile())) {
                        data = yaml.load(is);
                    } catch (Exception ex) {
                        System.err.println("⚠️ [Config] Failed to load existing YAML at " + yamlPath + ": " + ex.getMessage());
                    }
                }
                
                if (data == null) {
                    data = new java.util.LinkedHashMap<>();
                }

                Map<String, Object> companionSection = new java.util.LinkedHashMap<>();
                companionSection.put("base_path", modelsPath);
                companionSection.put("checkpoints", "checkpoints");
                companionSection.put("configs", "configs");
                companionSection.put("vae", "vae");
                companionSection.put("loras", "loras");
                companionSection.put("upscale_models", "upscale_models");
                companionSection.put("controlnet", "controlnet");
                companionSection.put("clip", "clip");
                companionSection.put("clip_vision", "clip_vision");
                companionSection.put("style_models", "style_models");
                companionSection.put("hypernetworks", "hypernetworks");
                companionSection.put("embeddings", "embeddings");
                companionSection.put("diffusers", "diffusers");
                companionSection.put("gligen", "gligen");
                companionSection.put("unet", "unet");
                companionSection.put("audio_encoders", "audio_encoders");
                
                // Add remaining model folders
                companionSection.put("vae_approx", "vae_approx");
                companionSection.put("photomaker", "photomaker");
                companionSection.put("ipadapter", "ipadapter");
                companionSection.put("onnx", "onnx");
                companionSection.put("llm", "llm");
                companionSection.put("diffusion_models", "diffusion_models");
                companionSection.put("text_encoders", "text_encoders");
                companionSection.put("model_patches", "model_patches");
                companionSection.put("latent_upscale_models", "latent_upscale_models");

                data.put("comfyui_companion", companionSection);

                org.yaml.snakeyaml.DumperOptions options = new org.yaml.snakeyaml.DumperOptions();
                options.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
                options.setPrettyFlow(true);
                Yaml yamlDump = new Yaml(options);

                String yamlContent = yamlDump.dump(data);
                Files.writeString(yamlPath, yamlContent, StandardCharsets.UTF_8);
                System.out.println("📄 [Config] Successfully updated YAML at: " + yamlPath.toAbsolutePath());
            } catch (Exception e) {
                System.err.println("❌ [Config] Failed to update YAML at " + yamlPath + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
