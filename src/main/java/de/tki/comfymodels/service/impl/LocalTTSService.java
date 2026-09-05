package de.tki.comfymodels.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.json.JSONObject;
import org.json.JSONArray;

import de.tki.comfymodels.service.IComfyLifecycleService;
import de.tki.comfymodels.service.impl.HardwareMonitorService;

@Service
public class LocalTTSService {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LocalTTSService.class);

    private static final java.util.Set<String> ATTEMPTED_INSTALLS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final ConfigService configService;
    private final EnvironmentBootstrapperImpl bootstrapper;
    private final IComfyLifecycleService lifecycleService;
    private final ProcessTracker processTracker;
    private HardwareMonitorService hardwareMonitorService;

    /** Setter for tests that need to inject a mock HardwareMonitorService. */
    public void setHardwareMonitorService(HardwareMonitorService svc) { this.hardwareMonitorService = svc; }

    public LocalTTSService(ConfigService configService) {
        this(configService, null, null, null, null);
    }

    @Autowired
    public LocalTTSService(
            ConfigService configService,
            @Autowired(required = false) EnvironmentBootstrapperImpl bootstrapper,
            @Autowired(required = false) IComfyLifecycleService lifecycleService,
            @Autowired(required = false) ProcessTracker processTracker,
            @Autowired(required = false) HardwareMonitorService hardwareMonitorService) {
        this.configService = configService;
        this.bootstrapper = bootstrapper;
        this.lifecycleService = lifecycleService;
        this.processTracker = processTracker;
        this.hardwareMonitorService = hardwareMonitorService;
    }


    /**
     * If the user has {@code qwen_tts_model_auto} enabled, query the detected
     * VRAM, ask the {@link QwenTtsModelRecommender} for the best model, and
     * persist it as the active {@code qwen_tts_model_repo}. Idempotent: once
     * a model has been auto-applied in this JVM lifetime, the recommendation
     * is not re-applied (so the user can still tweak the setting manually).
     */
    void applyAutoSelectedModelIfEnabled() {
        if (!configService.isQwenTtsModelAuto()) return;
        if (hardwareMonitorService == null) return;
        try {
            long vram = hardwareMonitorService.getVramBytes();
            QwenTtsModelRecommender.ModelSpec spec = QwenTtsModelRecommender.recommend(vram);
            String current = configService.getQwenTtsModelRepo();
            if (current != null && current.equals(spec.hfRepo)) {
                return; // already on the recommended model
            }
            logger.info("[LocalTTSService] Auto-selecting Qwen-TTS model for " + QwenTtsModelRecommender.formatVram(vram) + " VRAM: " + spec);
            configService.setQwenTtsModelRepo(spec.hfRepo);
        } catch (Exception ex) {
            logger.error("[LocalTTSService] Auto-select failed: " + ex.getMessage());
        }
    }
    public void generateSpeech(String text, String outputPath) throws Exception {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Text cannot be empty for TTS generation");
        }

        File outFile = new File(outputPath);
        File parentDir = outFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        // All TTS now goes through ComfyUI (Qwen-TTS by default).
        // The custom node and the Qwen model are auto-installed/downloaded
        // on first use by generateSpeechViaComfyUI().
        String provider = configService.getTtsProvider();
        if (provider == null || provider.isEmpty()) {
            provider = "ComfyUI Qwen-TTS";
        }
        if (!provider.startsWith("ComfyUI ")) {
            // Allow users who still have a stale Piper entry in their settings
            // to fall back to ComfyUI without re-saving the config.
            logger.info("[LocalTTSService] Provider '" + provider + "' is no longer supported. Switching to ComfyUI Qwen-TTS.");
            provider = "ComfyUI Qwen-TTS";
        }
        logger.info("[LocalTTSService] Generating TTS via " + provider + "...");
        boolean success = generateSpeechViaComfyUI(provider, text, outputPath);
        if (!success) {
            throw new java.io.IOException("ComfyUI TTS generation failed for provider: " + provider);
        }
    }



    private boolean generateSpeechViaComfyUI(String provider, String text, String outputPath) {
        try {
            if (lifecycleService != null && !lifecycleService.isHealthy()) {
                logger.info("🔄 [LocalTTSService] ComfyUI server is offline. Attempting auto-start...");
                lifecycleService.start();
                
                int maxWaitSeconds = 90;
                boolean started = false;
                for (int i = 0; i < maxWaitSeconds; i++) {
                    if (lifecycleService.isHealthy()) {
                        started = true;
                        break;
                    }
                    Thread.sleep(1000);
                }
                if (!started) {
                    logger.info("⚠️ [LocalTTSService] Failed to auto-start ComfyUI.");
                    return false;
                }
                logger.info("✅ [LocalTTSService] ComfyUI successfully started and healthy.");
            }

            String comfyUrl = configService.getComfyUIUrl();
            if (comfyUrl == null || comfyUrl.trim().isEmpty()) {
                logger.info("⚠️ [LocalTTSService] ComfyUI URL is empty.");
                return false;
            }

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(4))
                    .build();

            if ("ComfyUI KokoroTTS".equals(provider) && lifecycleService != null && lifecycleService.isHealthy() && bootstrapper != null) {
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
                } catch (Exception ignored) {}

                if (!nodeAvailable) {
                    if (ATTEMPTED_INSTALLS.contains("ComfyUI-KokoroTTS")) {
                        logger.info("⚠️ [LocalTTSService] ComfyUI-KokoroTTS installation/load was already attempted in this session. Skipping to avoid restart loop.");
                    } else {
                        ATTEMPTED_INSTALLS.add("ComfyUI-KokoroTTS");
                        if (!folderExists) {
                            logger.info("⚠️ [LocalTTSService] KokoroTTS is missing and folder does not exist. Installing ComfyUI-KokoroTTS...");
                            logger.info("🔄 [LocalTTSService] Stopping ComfyUI server to install custom nodes...");
                            lifecycleService.stop();
                            
                            String pythonPath = configService.getPythonPath();
                            if (comfyPath != null && !comfyPath.trim().isEmpty()) {
                                java.nio.file.Path comfyDir = java.nio.file.Paths.get(comfyPath);
                                java.nio.file.Path pythonExe = (pythonPath != null && !pythonPath.trim().isEmpty()) ? java.nio.file.Paths.get(pythonPath) : null;
                                bootstrapper.ensureKokoroTtsInstalled(comfyDir, pythonExe, System.out::println);
                            }
                            
                            logger.info("🔄 [LocalTTSService] Restarting ComfyUI server after installation...");
                            lifecycleService.start();
                        } else {
                            logger.info("🔄 [LocalTTSService] KokoroTTS node is not active but folder exists. Restarting ComfyUI server to load it...");
                            lifecycleService.restart();
                        }
                        
                        int maxWaitSeconds = 90;
                        boolean started = false;
                        for (int i = 0; i < maxWaitSeconds; i++) {
                            if (lifecycleService.isHealthy()) {
                                started = true;
                                break;
                            }
                            Thread.sleep(1000);
                        }
                        if (!started) {
                            throw new RuntimeException("Failed to restart ComfyUI after KokoroTTS installation.");
                        }
                        logger.info("✅ [LocalTTSService] ComfyUI successfully restarted and healthy.");
                    }
                }
            }


            if ("ComfyUI Qwen-TTS".equals(provider) && lifecycleService != null && lifecycleService.isHealthy() && bootstrapper != null) {
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
                            .GET().build();
                    HttpResponse<String> qResp = client.send(qCheck, HttpResponse.BodyHandlers.ofString());
                    if (qResp.statusCode() == 200) {
                        JSONObject info2 = new JSONObject(qResp.body());
                        qwenNodeAvailable = info2.has("QwenTTS") || info2.has("Qwen2TTS")
                                || info2.has("Qwen Audio TTS") || info2.has("QwenTTSNode");
                    }
                } catch (Exception ignored) {}

                if (!qwenNodeAvailable) {
                    if (ATTEMPTED_INSTALLS.contains("ComfyUI-Qwen-TTS")) {
                        logger.info("[LocalTTSService] ComfyUI-Qwen-TTS installation/load was already attempted in this session. Skipping to avoid restart loop.");
                    } else {
                        ATTEMPTED_INSTALLS.add("ComfyUI-Qwen-TTS");
                        if (!qwenFolderExists) {
                            logger.info("[LocalTTSService] Qwen-TTS is missing and folder does not exist. Installing ComfyUI-Qwen-TTS...");
                            logger.info("[LocalTTSService] Stopping ComfyUI server to install custom node...");
                            lifecycleService.stop();
                            String pythonPathQ = configService.getPythonPath();
                            if (comfyPathQ != null && !comfyPathQ.trim().isEmpty()) {
                                java.nio.file.Path comfyDirQ = java.nio.file.Paths.get(comfyPathQ);
                                java.nio.file.Path pythonExeQ = (pythonPathQ != null && !pythonPathQ.trim().isEmpty()) ? java.nio.file.Paths.get(pythonPathQ) : null;
                                bootstrapper.ensureQwenTtsInstalled(comfyDirQ, pythonExeQ, System.out::println);
                            }
                            logger.info("[LocalTTSService] Restarting ComfyUI server after Qwen-TTS installation...");
                            lifecycleService.start();
                        } else {
                            logger.info("[LocalTTSService] Qwen-TTS folder exists but node not active. Restarting ComfyUI to load it...");
                            lifecycleService.restart();
                        }
                        int maxWait = 90; boolean startedQ = false;
                        for (int i = 0; i < maxWait; i++) {
                            if (lifecycleService.isHealthy()) { startedQ = true; break; }
                            Thread.sleep(1000);
                        }
                        if (!startedQ) throw new RuntimeException("Failed to restart ComfyUI after Qwen-TTS installation.");
                        logger.info("[LocalTTSService] ComfyUI successfully restarted with Qwen-TTS.");
                    }
                }
            }

            HttpRequest infoRequest = HttpRequest.newBuilder()
                    .uri(URI.create(comfyUrl + "/object_info"))
                    .GET()
                    .build();

            HttpResponse<String> infoResponse = client.send(infoRequest, HttpResponse.BodyHandlers.ofString());
            if (infoResponse.statusCode() != 200) {
                logger.info("⚠️ [LocalTTSService] ComfyUI server returned status " + infoResponse.statusCode() + ".");
                return false;
            }

            JSONObject info = new JSONObject(infoResponse.body());
            String nodeClass = "";
            JSONObject workflowJson = new JSONObject();
            JSONObject promptObj = new JSONObject();
            workflowJson.put("prompt", promptObj);

            if ("ComfyUI KokoroTTS".equals(provider)) {
                if (info.has("KokoroTTS")) {
                    nodeClass = "KokoroTTS";
                } else if (info.has("Kokoro TTS")) {
                    nodeClass = "Kokoro TTS";
                } else if (info.has("GeekyKokoroTTS")) {
                    nodeClass = "GeekyKokoroTTS";
                }

                if (nodeClass.isEmpty()) {
                    logger.info("⚠️ [LocalTTSService] No Kokoro TTS custom node class found in ComfyUI.");
                    return false;
                }

                JSONObject kokoroNode = new JSONObject();
                kokoroNode.put("class_type", nodeClass);
                JSONObject kokoroInputs = new JSONObject();
                kokoroInputs.put("text", text);
                kokoroInputs.put("voice", "American Female (Bella)");
                kokoroInputs.put("speed", 1.0);
                kokoroNode.put("inputs", kokoroInputs);
                promptObj.put("1", kokoroNode);

                JSONObject saveNode = new JSONObject();
                saveNode.put("class_type", "SaveAudio");
                JSONObject saveInputs = new JSONObject();
                saveInputs.put("filename_prefix", "tts_scene");
                saveInputs.put("audio", new JSONArray().put("1").put(0));
                saveNode.put("inputs", saveInputs);
                promptObj.put("2", saveNode);

            } else if ("ComfyUI ElevenLabs".equals(provider)) {
                if (!info.has("ElevenLabsTextToSpeech")) {
                    logger.info("⚠️ [LocalTTSService] ElevenLabsTextToSpeech node not found in ComfyUI.");
                    return false;
                }

                String apiKey = configService.getElevenLabsApiKey();
                String voiceId = configService.getElevenLabsVoiceId();
                if (apiKey.isEmpty()) {
                    logger.info("⚠️ [LocalTTSService] ElevenLabs API Key is not configured.");
                    return false;
                }

                JSONObject elevenNode = new JSONObject();
                elevenNode.put("class_type", "ElevenLabsTextToSpeech");
                JSONObject elevenInputs = new JSONObject();
                elevenInputs.put("api_key", apiKey);
                elevenInputs.put("text", text);
                elevenInputs.put("voice_id", voiceId.isEmpty() ? "21m00Tcm4TlvDq8ikWAM" : voiceId);
                elevenInputs.put("model_id", "eleven_multilingual_v2");
                elevenNode.put("inputs", elevenInputs);
                promptObj.put("1", elevenNode);

                JSONObject saveNode = new JSONObject();
                saveNode.put("class_type", "SaveAudio");
                JSONObject saveInputs = new JSONObject();
                saveInputs.put("filename_prefix", "tts_scene");
                saveInputs.put("audio", new JSONArray().put("1").put(0));
                saveNode.put("inputs", saveInputs);
                promptObj.put("2", saveNode);
            } else if ("ComfyUI Qwen-TTS".equals(provider)) {
                if (info.has("QwenTTS")) { nodeClass = "QwenTTS"; }
                else if (info.has("Qwen2TTS")) { nodeClass = "Qwen2TTS"; }
                else if (info.has("Qwen Audio TTS")) { nodeClass = "Qwen Audio TTS"; }
                else if (info.has("QwenTTSNode")) { nodeClass = "QwenTTSNode"; }

                if (nodeClass.isEmpty()) {
                    logger.info("[LocalTTSService] No Qwen-TTS custom node class found in ComfyUI.");
                    return false;
                }

                String qwenRepo = configService.getQwenTtsModelRepo();
                String qwenPath = configService.getQwenTtsModelPath();
                String qwenVoice = configService.getQwenTtsVoice();

                boolean hasLoader = info.has("QwenModelLoader") || info.has("LoadQwenModel") || info.has("QwenTTSModelLoader");
                if (hasLoader) {
                    String loaderClass = info.has("QwenModelLoader") ? "QwenModelLoader"
                            : info.has("LoadQwenModel") ? "LoadQwenModel" : "QwenTTSModelLoader";
                    JSONObject loader = new JSONObject();
                    loader.put("class_type", loaderClass);
                    JSONObject loaderInputs = new JSONObject();
                    loaderInputs.put("model_path", qwenPath);
                    loaderInputs.put("repo_id", qwenRepo);
                    loader.put("inputs", loaderInputs);
                    promptObj.put("1", loader);

                    JSONObject qwen = new JSONObject();
                    qwen.put("class_type", nodeClass);
                    JSONObject qwenInputs = new JSONObject();
                    qwenInputs.put("text", text);
                    qwenInputs.put("voice", qwenVoice);
                    qwenInputs.put("speed", 1.0);
                    qwenInputs.put("model", new JSONArray().put("1").put(0));
                    qwen.put("inputs", qwenInputs);
                    promptObj.put("2", qwen);

                    JSONObject save = new JSONObject();
                    save.put("class_type", "SaveAudio");
                    JSONObject saveInputs2 = new JSONObject();
                    saveInputs2.put("filename_prefix", "tts_scene");
                    saveInputs2.put("audio", new JSONArray().put("2").put(0));
                    save.put("inputs", saveInputs2);
                    promptObj.put("3", save);
                } else {
                    JSONObject qwen = new JSONObject();
                    qwen.put("class_type", nodeClass);
                    JSONObject qwenInputs = new JSONObject();
                    qwenInputs.put("text", text);
                    qwenInputs.put("voice", qwenVoice);
                    qwenInputs.put("speed", 1.0);
                    qwenInputs.put("model_path", qwenPath);
                    qwenInputs.put("repo_id", qwenRepo);
                    qwen.put("inputs", qwenInputs);
                    promptObj.put("1", qwen);

                    JSONObject save = new JSONObject();
                    save.put("class_type", "SaveAudio");
                    JSONObject saveInputs2 = new JSONObject();
                    saveInputs2.put("filename_prefix", "tts_scene");
                    saveInputs2.put("audio", new JSONArray().put("1").put(0));
                    save.put("inputs", saveInputs2);
                    promptObj.put("2", save);
                }
            } else {
                return false;
            }
            logger.info("🚀 [LocalTTSService] Submitting TTS workflow to ComfyUI...");
            HttpRequest promptRequest = HttpRequest.newBuilder()
                    .uri(URI.create(comfyUrl + "/prompt"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(workflowJson.toString()))
                    .build();

            HttpResponse<String> promptResponse = client.send(promptRequest, HttpResponse.BodyHandlers.ofString());
            if (promptResponse.statusCode() != 200) {
                logger.info("⚠️ [LocalTTSService] Failed to send prompt to ComfyUI: " + promptResponse.body());
                return false;
            }

            JSONObject respObj = new JSONObject(promptResponse.body());
            String promptId = respObj.getString("prompt_id");

            logger.info("⏳ [LocalTTSService] Polling ComfyUI history for prompt " + promptId + "...");
            String finishedFilename = null;
            int timeoutCount = 0;
            while (timeoutCount < 40) {
                Thread.sleep(3000);

                HttpRequest histRequest = HttpRequest.newBuilder()
                        .uri(URI.create(comfyUrl + "/history/" + promptId))
                        .GET()
                        .build();

                HttpResponse<String> histResponse = client.send(histRequest, HttpResponse.BodyHandlers.ofString());
                if (histResponse.statusCode() == 200) {
                    JSONObject history = new JSONObject(histResponse.body());
                    if (history.has(promptId)) {
                        JSONObject job = history.getJSONObject(promptId);
                        JSONObject outputs = job.optJSONObject("outputs");
                        if (outputs != null) {
                            for (String key : outputs.keySet()) {
                                JSONObject outputNode = outputs.getJSONObject(key);
                                for (String nodeKey : outputNode.keySet()) {
                                    Object value = outputNode.get(nodeKey);
                                    if (value instanceof JSONArray) {
                                        JSONArray arr = (JSONArray) value;
                                        for (int i = 0; i < arr.length(); i++) {
                                            Object itemObj = arr.get(i);
                                            if (itemObj instanceof JSONObject) {
                                                JSONObject item = (JSONObject) itemObj;
                                                if (item.has("filename")) {
                                                    finishedFilename = item.getString("filename");
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                    if (finishedFilename != null) break;
                                }
                                if (finishedFilename != null) break;
                            }
                        }
                        break;
                    }
                }
                timeoutCount++;
            }

            if (finishedFilename == null || finishedFilename.trim().isEmpty()) {
                logger.info("⚠️ [LocalTTSService] Polling timed out or did not return audio filename.");
                return false;
            }

            logger.info("📥 [LocalTTSService] Downloading generated audio: " + finishedFilename);
            String downloadUrl = comfyUrl + "/view?filename=" + finishedFilename + "&type=output";
            HttpRequest dlRequest = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .GET()
                    .build();

            HttpResponse<java.io.InputStream> dlResponse = client.send(dlRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (dlResponse.statusCode() == 200) {
                File outFile = new File(outputPath);
                File parentDir = outFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }

                try (java.io.InputStream is = dlResponse.body()) {
                    java.nio.file.Files.copy(is, outFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                logger.info("🎵 [LocalTTSService] Generated ComfyUI audio saved to: " + outFile.getAbsolutePath());
                return true;
            } else {
                logger.info("⚠️ [LocalTTSService] Failed to download audio. Status: " + dlResponse.statusCode());
                return false;
            }

        } catch (Exception e) {
            logger.info("⚠️ [LocalTTSService] ComfyUI TTS failed: " + e.getMessage());
            return false;
        }
    }
}

