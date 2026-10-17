package de.tki.comfyuicompanion.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.json.JSONObject;
import org.json.JSONArray;

import de.tki.comfyuicompanion.service.IComfyLifecycleService;

/**
 * Service responsible for synthesizing audio files using local ComfyUI TTS nodes
 * or cloud TTS providers.
 */
@Service
public class LocalTTSService {
    private static final Logger logger = LoggerFactory.getLogger(LocalTTSService.class);

    private final ConfigService configService;
    private final IComfyLifecycleService lifecycleService;
    private final ProcessTracker processTracker;
    private final ComfyTtsNodeBootstrapper nodeBootstrapper;
    private HardwareMonitorService hardwareMonitorService;

    /**
     * Setter for tests that need to inject a mock HardwareMonitorService.
     *
     * @param svc the hardware monitor service
     */
    public void setHardwareMonitorService(HardwareMonitorService svc) {
        this.hardwareMonitorService = svc;
    }

    /**
     * Minimal constructor for testing or basic configuration.
     *
     * @param configService the configuration service
     */
    public LocalTTSService(ConfigService configService) {
        this(configService, null, null, null, null);
    }

    /**
     * Full dependency injection constructor.
     *
     * @param configService configuration service
     * @param bootstrapper environment bootstrapper
     * @param lifecycleService ComfyUI lifecycle service
     * @param processTracker process tracker
     * @param hardwareMonitorService hardware monitor service
     */
    @Autowired
    public LocalTTSService(
            ConfigService configService,
            @Autowired(required = false) EnvironmentBootstrapperImpl bootstrapper,
            @Autowired(required = false) IComfyLifecycleService lifecycleService,
            @Autowired(required = false) ProcessTracker processTracker,
            @Autowired(required = false) HardwareMonitorService hardwareMonitorService) {
        this.configService = configService;
        this.lifecycleService = lifecycleService;
        this.processTracker = processTracker;
        this.hardwareMonitorService = hardwareMonitorService;
        this.nodeBootstrapper = new ComfyTtsNodeBootstrapper(configService, bootstrapper, lifecycleService);
    }

    /**
     * If the user has {@code qwen_tts_model_auto} enabled, query the detected
     * VRAM, ask the {@link QwenTtsModelRecommender} for the best model, and
     * persist it as the active {@code qwen_tts_model_repo}. Idempotent.
     */
    void applyAutoSelectedModelIfEnabled() {
        if (!configService.isQwenTtsModelAuto()) return;
        if (hardwareMonitorService == null) return;
        try {
            long vram = hardwareMonitorService.getVramBytes();
            QwenTtsModelRecommender.ModelSpec spec = QwenTtsModelRecommender.recommend(vram);
            String current = configService.getQwenTtsModelRepo();
            if (current != null && current.equals(spec.hfRepo)) {
                return;
            }
            logger.info("[LocalTTSService] Auto-selecting Qwen-TTS model for {} VRAM: {}",
                    QwenTtsModelRecommender.formatVram(vram), spec);
            configService.setQwenTtsModelRepo(spec.hfRepo);
        } catch (Exception ex) {
            logger.error("[LocalTTSService] Auto-select failed: {}", ex.getMessage());
        }
    }

    /**
     * Synthesizes speech from text and outputs to the designated file path.
     *
     * @param text text to synthesize
     * @param outputPath target audio file path
     * @throws Exception if generation fails
     */
    public void generateSpeech(String text, String outputPath) throws Exception {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Text cannot be empty for TTS generation");
        }

        File outFile = new File(outputPath);
        File parentDir = outFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        String provider = configService.getTtsProvider();
        if (provider == null || provider.isEmpty()) {
            provider = "ComfyUI Qwen-TTS";
        }
        if (!provider.startsWith("ComfyUI ")) {
            logger.info("[LocalTTSService] Provider '{}' is no longer supported. Switching to ComfyUI Qwen-TTS.", provider);
            provider = "ComfyUI Qwen-TTS";
        }
        logger.info("[LocalTTSService] Generating TTS via {}...", provider);
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

            if ("ComfyUI KokoroTTS".equals(provider)) {
                nodeBootstrapper.ensureKokoroNodeAvailable(client, comfyUrl);
            } else if ("ComfyUI Qwen-TTS".equals(provider)) {
                nodeBootstrapper.ensureQwenNodeAvailable(client, comfyUrl);
            }

            HttpRequest infoRequest = HttpRequest.newBuilder()
                    .uri(URI.create(comfyUrl + "/object_info"))
                    .GET()
                    .build();

            HttpResponse<String> infoResponse = client.send(infoRequest, HttpResponse.BodyHandlers.ofString());
            if (infoResponse.statusCode() != 200) {
                logger.info("⚠️ [LocalTTSService] ComfyUI server returned status {}.", infoResponse.statusCode());
                return false;
            }

            JSONObject info = new JSONObject(infoResponse.body());
            JSONObject workflowJson = null;

            if ("ComfyUI KokoroTTS".equals(provider)) {
                String nodeClass = info.has("KokoroTTS") ? "KokoroTTS"
                        : info.has("Kokoro TTS") ? "Kokoro TTS"
                        : info.has("GeekyKokoroTTS") ? "GeekyKokoroTTS" : "";
                if (nodeClass.isEmpty()) {
                    logger.info("⚠️ [LocalTTSService] No Kokoro TTS custom node class found in ComfyUI.");
                    return false;
                }
                workflowJson = ComfyTtsWorkflowBuilder.buildKokoroWorkflow(nodeClass, text);
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
                workflowJson = ComfyTtsWorkflowBuilder.buildElevenLabsWorkflow(apiKey, voiceId, text);
            } else if ("ComfyUI Qwen-TTS".equals(provider)) {
                String nodeClass = info.has("QwenTTS") ? "QwenTTS"
                        : info.has("Qwen2TTS") ? "Qwen2TTS"
                        : info.has("Qwen Audio TTS") ? "Qwen Audio TTS"
                        : info.has("QwenTTSNode") ? "QwenTTSNode" : "";
                if (nodeClass.isEmpty()) {
                    logger.info("[LocalTTSService] No Qwen-TTS custom node class found in ComfyUI.");
                    return false;
                }
                workflowJson = ComfyTtsWorkflowBuilder.buildQwenWorkflow(info, nodeClass, text,
                        configService.getQwenTtsModelRepo(),
                        configService.getQwenTtsModelPath(),
                        configService.getQwenTtsVoice());
            }

            if (workflowJson == null) {
                return false;
            }

            return executeAndDownloadAudio(client, comfyUrl, workflowJson, outputPath);
        } catch (Exception e) {
            logger.info("⚠️ [LocalTTSService] ComfyUI TTS failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean executeAndDownloadAudio(HttpClient client, String comfyUrl, JSONObject workflowJson, String outputPath) throws Exception {
        logger.info("🚀 [LocalTTSService] Submitting TTS workflow to ComfyUI...");
        HttpRequest promptRequest = HttpRequest.newBuilder()
                .uri(URI.create(comfyUrl + "/prompt"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(workflowJson.toString()))
                .build();

        HttpResponse<String> promptResponse = client.send(promptRequest, HttpResponse.BodyHandlers.ofString());
        if (promptResponse.statusCode() != 200) {
            logger.info("⚠️ [LocalTTSService] Failed to send prompt to ComfyUI: {}", promptResponse.body());
            return false;
        }

        JSONObject respObj = new JSONObject(promptResponse.body());
        String promptId = respObj.getString("prompt_id");

        logger.info("⏳ [LocalTTSService] Polling ComfyUI history for prompt {}...", promptId);
        String finishedFilename = pollForAudioFilename(client, comfyUrl, promptId);

        if (finishedFilename == null || finishedFilename.trim().isEmpty()) {
            logger.info("⚠️ [LocalTTSService] Polling timed out or did not return audio filename.");
            return false;
        }

        logger.info("📥 [LocalTTSService] Downloading generated audio: {}", finishedFilename);
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
            logger.info("🎵 [LocalTTSService] Generated ComfyUI audio saved to: {}", outFile.getAbsolutePath());
            return true;
        } else {
            logger.info("⚠️ [LocalTTSService] Failed to download audio. Status: {}", dlResponse.statusCode());
            return false;
        }
    }

    private String pollForAudioFilename(HttpClient client, String comfyUrl, String promptId) throws Exception {
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
                                if (value instanceof JSONArray arr) {
                                    for (int i = 0; i < arr.length(); i++) {
                                        Object itemObj = arr.get(i);
                                        if (itemObj instanceof JSONObject item && item.has("filename")) {
                                            return item.getString("filename");
                                        }
                                    }
                                }
                            }
                        }
                    }
                    break;
                }
            }
            timeoutCount++;
        }
        return null;
    }
}
