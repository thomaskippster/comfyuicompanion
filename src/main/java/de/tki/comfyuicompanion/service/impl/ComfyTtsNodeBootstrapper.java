package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles detection, installation, and server restarts for ComfyUI TTS custom nodes
 * (such as KokoroTTS and Qwen-TTS).
 */
public class ComfyTtsNodeBootstrapper {

    private static final Logger logger = LoggerFactory.getLogger(ComfyTtsNodeBootstrapper.class);
    private static final Set<String> ATTEMPTED_INSTALLS = ConcurrentHashMap.newKeySet();

    private final ConfigService configService;
    private final EnvironmentBootstrapperImpl bootstrapper;
    private final IComfyLifecycleService lifecycleService;

    /**
     * Constructs a new bootstrapper for TTS custom nodes.
     *
     * @param configService the configuration service
     * @param bootstrapper the environment bootstrapper
     * @param lifecycleService the ComfyUI process lifecycle service
     */
    public ComfyTtsNodeBootstrapper(ConfigService configService,
                                    EnvironmentBootstrapperImpl bootstrapper,
                                    IComfyLifecycleService lifecycleService) {
        this.configService = configService;
        this.bootstrapper = bootstrapper;
        this.lifecycleService = lifecycleService;
    }

    /**
     * Ensures KokoroTTS custom node is installed and loaded in ComfyUI.
     *
     * @param client the HTTP client
     * @param comfyUrl the ComfyUI base URL
     * @throws Exception if restart or installation fails
     */
    public void ensureKokoroNodeAvailable(HttpClient client, String comfyUrl) throws Exception {
        if (lifecycleService == null || !lifecycleService.isHealthy() || bootstrapper == null) {
            return;
        }

        String comfyPath = configService.getComfyUIPath();
        boolean folderExists = false;
        if (comfyPath != null && !comfyPath.trim().isEmpty()) {
            File kokoroDir = new File(new File(comfyPath, "custom_nodes"), "ComfyUI-KokoroTTS");
            if (kokoroDir.exists() && kokoroDir.isDirectory()) {
                folderExists = true;
            }
        }

        boolean nodeAvailable = false;
        try {
            HttpRequest checkRequest = HttpRequest.newBuilder()
                    .uri(URI.create(comfyUrl + "/object_info"))
                    .GET()
                    .build();
            HttpResponse<String> checkResponse = client.send(checkRequest, HttpResponse.BodyHandlers.ofString());
            if (checkResponse.statusCode() == 200) {
                JSONObject tempInfo = new JSONObject(checkResponse.body());
                nodeAvailable = tempInfo.has("KokoroTTS") || tempInfo.has("Kokoro TTS") || tempInfo.has("GeekyKokoroTTS");
            }
        } catch (Exception ignored) {
            logger.debug("Ignored non-fatal exception: {}", ignored.getMessage());
        }

        if (!nodeAvailable) {
            if (ATTEMPTED_INSTALLS.contains("ComfyUI-KokoroTTS")) {
                logger.info("⚠️ [ComfyTtsNodeBootstrapper] KokoroTTS install was already attempted in this session. Skipping.");
                return;
            }
            ATTEMPTED_INSTALLS.add("ComfyUI-KokoroTTS");
            if (!folderExists) {
                logger.info("⚠️ [ComfyTtsNodeBootstrapper] KokoroTTS is missing. Installing ComfyUI-KokoroTTS...");
                lifecycleService.stop();

                String pythonPath = configService.getPythonPath();
                if (comfyPath != null && !comfyPath.trim().isEmpty()) {
                    Path comfyDir = Paths.get(comfyPath);
                    Path pythonExe = (pythonPath != null && !pythonPath.trim().isEmpty()) ? Paths.get(pythonPath) : null;
                    bootstrapper.ensureKokoroTtsInstalled(comfyDir, pythonExe, msg -> logger.info("[Bootstrap] {}", msg));
                }
                lifecycleService.start();
            } else {
                logger.info("🔄 [ComfyTtsNodeBootstrapper] KokoroTTS folder exists but node inactive. Restarting ComfyUI...");
                lifecycleService.restart();
            }

            waitForHealthy();
        }
    }

    /**
     * Ensures Qwen-TTS custom node is installed and loaded in ComfyUI.
     *
     * @param client the HTTP client
     * @param comfyUrl the ComfyUI base URL
     * @throws Exception if restart or installation fails
     */
    public void ensureQwenNodeAvailable(HttpClient client, String comfyUrl) throws Exception {
        if (lifecycleService == null || !lifecycleService.isHealthy() || bootstrapper == null) {
            return;
        }

        String comfyPathQ = configService.getComfyUIPath();
        File qwenDir = null;
        if (comfyPathQ != null && !comfyPathQ.trim().isEmpty()) {
            qwenDir = new File(new File(comfyPathQ, "custom_nodes"), "ComfyUI-Qwen-TTS");
        }
        boolean qwenFolderExists = qwenDir != null && qwenDir.exists() && qwenDir.isDirectory();

        boolean qwenNodeAvailable = false;
        try {
            HttpRequest qCheck = HttpRequest.newBuilder()
                    .uri(URI.create(comfyUrl + "/object_info"))
                    .GET()
                    .build();
            HttpResponse<String> qResp = client.send(qCheck, HttpResponse.BodyHandlers.ofString());
            if (qResp.statusCode() == 200) {
                JSONObject info2 = new JSONObject(qResp.body());
                qwenNodeAvailable = info2.has("QwenTTS") || info2.has("Qwen2TTS")
                        || info2.has("Qwen Audio TTS") || info2.has("QwenTTSNode");
            }
        } catch (Exception ignored) {
            logger.debug("Ignored non-fatal exception: {}", ignored.getMessage());
        }

        if (!qwenNodeAvailable) {
            if (ATTEMPTED_INSTALLS.contains("ComfyUI-Qwen-TTS")) {
                logger.info("[ComfyTtsNodeBootstrapper] Qwen-TTS install was already attempted in this session. Skipping.");
                return;
            }
            ATTEMPTED_INSTALLS.add("ComfyUI-Qwen-TTS");
            if (!qwenFolderExists) {
                logger.info("[ComfyTtsNodeBootstrapper] Qwen-TTS is missing. Installing ComfyUI-Qwen-TTS...");
                lifecycleService.stop();
                String pythonPathQ = configService.getPythonPath();
                if (comfyPathQ != null && !comfyPathQ.trim().isEmpty()) {
                    Path comfyDirQ = Paths.get(comfyPathQ);
                    Path pythonExeQ = (pythonPathQ != null && !pythonPathQ.trim().isEmpty()) ? Paths.get(pythonPathQ) : null;
                    bootstrapper.ensureQwenTtsInstalled(comfyDirQ, pythonExeQ, msg -> logger.info("[Bootstrap] {}", msg));
                }
                lifecycleService.start();
            } else {
                logger.info("[ComfyTtsNodeBootstrapper] Qwen-TTS folder exists but node inactive. Restarting ComfyUI...");
                lifecycleService.restart();
            }

            waitForHealthy();
        }
    }

    private void waitForHealthy() throws Exception {
        int maxWait = 90;
        boolean started = false;
        for (int i = 0; i < maxWait; i++) {
            if (lifecycleService.isHealthy()) {
                started = true;
                break;
            }
            Thread.sleep(1000);
        }
        if (!started) {
            throw new RuntimeException("Failed to restart ComfyUI after TTS node installation.");
        }
        logger.info("✅ [ComfyTtsNodeBootstrapper] ComfyUI successfully restarted and healthy.");
    }
}
