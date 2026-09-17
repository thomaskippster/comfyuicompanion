package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.util.PlatformUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Service component responsible for auto-discovering ComfyUI installations,
 * virtual environments, Python interpreters, and generating default launch commands.
 */
@Component
public class ComfyAutoDiscoveryService {
    private static final Logger logger = LoggerFactory.getLogger(ComfyAutoDiscoveryService.class);

    /**
     * Discovers a Python executable within or near the specified ComfyUI root directory.
     *
     * @param comfyRoot the ComfyUI installation folder
     * @return the resolved path to Python, or a default command ("python"/"python3")
     */
    public String discoverPython(String comfyRoot) {
        logger.info("🐍 [Config] Searching for Python in/near: {}", comfyRoot);
        boolean isWin = PlatformUtils.isWindows();
        if (comfyRoot == null || comfyRoot.trim().isEmpty()) {
            return isWin ? "python" : "python3";
        }
        File comfyRootFile = new File(comfyRoot);
        File parent1 = comfyRootFile.getParentFile();
        File parent2 = parent1 != null ? parent1.getParentFile() : null;

        File[] venvLocations = {
            new File(comfyRootFile, ".venv"),
            parent1 != null ? new File(parent1, ".venv") : null,
            parent2 != null ? new File(parent2, ".venv") : null
        };

        for (File venv : venvLocations) {
            if (venv != null && venv.exists()) {
                logger.info("📂 [Config] Found venv at: {}", venv.getAbsolutePath());
                File bin = new File(venv, isWin ? "Scripts/python.exe" : "bin/python3");
                if (bin.exists()) return bin.getAbsolutePath();
                bin = new File(venv, isWin ? "Scripts/python" : "bin/python");
                if (bin.exists()) return bin.getAbsolutePath();
            }
        }

        // Priority 2: Check for python_embeded (Portable ComfyUI style)
        File portablePython = new File(new File(comfyRoot).getParentFile(), "python_embeded/python.exe");
        if (portablePython.exists()) {
            logger.info("📂 [Config] Found portable python at: {}", portablePython.getAbsolutePath());
            return portablePython.getAbsolutePath();
        }

        logger.info("⚠️ [Config] No venv found, falling back to system python.");
        return isWin ? "python" : "python3";
    }

    /**
     * Checks if the given Python path points to a valid file or recognized executable alias.
     *
     * @param python the Python path or executable name
     * @return {@code true} if valid
     */
    public boolean isPythonValid(String python) {
        if (python == null || python.isEmpty()) return false;
        if (python.equalsIgnoreCase("python") || python.equalsIgnoreCase("python3")) return true;
        return new File(python).exists();
    }

    /**
     * Auto-discovers the ComfyUI root directory, models path, Python executable, and launch command,
     * mutating the provided settings JSON accordingly.
     *
     * @param settings the active settings container
     */
    public void autoDiscover(JSONObject settings) {
        String root = settings.optString("comfyui_path", "");
        String userHome = System.getProperty("user.home");
        String currentDir = System.getProperty("user.dir");

        logger.info("🔍 [Config] Starting auto-discovery. Stored Root: {}", root);

        // 1. VALIDATE AND DISCOVER COMFYUI ROOT
        boolean rootValid = !root.isEmpty() && new File(root, "main.py").exists();

        if (!rootValid && !root.isEmpty()) {
            logger.info("⚠️ [Config] main.py not found in current root. Checking subdirectories...");
            File nested = new File(root, "resources/ComfyUI");
            if (new File(nested, "main.py").exists()) {
                root = nested.getAbsolutePath();
                rootValid = true;
                logger.info("✨ [Config] Found main.py in nested Pinokio path: {}", root);
                settings.put("comfyui_path", root);
            } else {
                logger.info("🚫 [Config] Current root is invalid. Resetting for re-discovery.");
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
                        logger.info("✨ [Config] Found Root via Env Var {}: {}", var, root);
                        break;
                    }
                }
            }

            // Priority 2: Check nearby directories
            if (root.isEmpty()) {
                File possibleRoot = findMainPyNearby(new File(currentDir));
                if (possibleRoot != null) {
                    root = possibleRoot.getAbsolutePath();
                    logger.info("✨ [Config] Found Root via Nearby Search: {}", root);
                }
            }

            // Priority 3: Check common Pinokio/Home locations
            if (root.isEmpty()) {
                List<String> patterns = new ArrayList<>();
                if (PlatformUtils.isWindows()) {
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
                        logger.info("✨ [Config] Found Root via Pattern: {}", root);
                        break;
                    } else {
                        File nested = new File(f, "resources/ComfyUI");
                        if (new File(nested, "main.py").exists()) {
                            root = nested.getAbsolutePath();
                            logger.info("✨ [Config] Found Root via Nested Pattern: {}", root);
                            break;
                        }
                    }
                }
            }

            if (!root.isEmpty()) {
                settings.put("comfyui_path", root);
                rootValid = true;
            }
        }

        // 2. DISCOVER MODELS DIR
        String currentExtraPath = settings.optString("extra_comfyui_path", "");
        String currentModelsPath = settings.optString("models_path", "");
        if (currentExtraPath.isEmpty() && (currentModelsPath.isEmpty() || currentModelsPath.equals(PathResolver.MODELS_DIR))) {
            File comfyFolder = new File(root);
            while (comfyFolder != null) {
                File parent = comfyFolder.getParentFile();
                if (parent != null) {
                    File dataModels = new File(parent, "comfyuidata/models");
                    if (dataModels.exists() && dataModels.isDirectory()) {
                        logger.info("✨ [Config] Found models via Pinokio Data Path: {}", dataModels.getAbsolutePath());
                        settings.put("extra_comfyui_path", dataModels.getParentFile().getAbsolutePath());
                        break;
                    }
                    File altData = new File(parent, "data/models");
                    if (altData.exists() && altData.isDirectory()) {
                        logger.info("✨ [Config] Found models via Data Path: {}", altData.getAbsolutePath());
                        settings.put("extra_comfyui_path", altData.getParentFile().getAbsolutePath());
                        break;
                    }
                }
                comfyFolder = comfyFolder.getParentFile();
                if (comfyFolder != null && comfyFolder.getName().equalsIgnoreCase("AI")) break;
            }
        }

        // 3. DISCOVER WORKING DIR
        if ((settings.optString("comfy_working_dir", "").isEmpty() || !rootValid) && !root.isEmpty()) {
            settings.put("comfy_working_dir", root);
            logger.info("📁 [Config] Set Working Dir: {}", root);
        }

        // 4. DISCOVER PYTHON & CONSTRUCT LAUNCH COMMAND
        String python = settings.optString("python_path", "");
        if (!isPythonValid(python)) {
            python = discoverPython(root);
            if (isPythonValid(python) && !python.equals("python") && !python.equals("python3")) {
                settings.put("python_path", python);
            }
        }

        String launchCmd = settings.optString("comfy_launch_command", "");
        if ((launchCmd.isEmpty() || !rootValid) && !root.isEmpty()) {
            File mainPy = new File(root, "main.py");
            if (mainPy.exists()) {
                int port = 8188;
                try {
                    String url = settings.optString("comfyui_url", "");
                    int colonIndex = url.lastIndexOf(":");
                    if (colonIndex != -1) {
                        port = Integer.parseInt(url.substring(colonIndex + 1));
                    }
                } catch (Exception ignored) {}
                String cmd = String.format("\"%s\" \"%s\" --listen 127.0.0.1 --port %d --enable-manager --extra-model-paths-config \"%s\"",
                        python, mainPy.getAbsolutePath(), port, new File(root, "extra_model_paths.yaml").getAbsolutePath());
                settings.put("comfy_launch_command", cmd);
                logger.info("🚀 [Config] Generated Launch Command: {}", cmd);
            } else {
                logger.error("Could not find main.py in {}", root);
            }
        }
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
}
