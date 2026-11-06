package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import de.tki.comfyuicompanion.service.IConfigService;
import de.tki.comfyuicompanion.service.ILocalAudioService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Enterprise implementation of {@link ILocalAudioService}.
 * Executes high-fidelity text-to-audio synthesis using connected ComfyUI neural audio models,
 * with graceful fallback to native procedural cinematic audio synthesis via FFmpeg filters.
 */
@Service
public class LocalAudioService implements ILocalAudioService {

    private static final Logger logger = LoggerFactory.getLogger(LocalAudioService.class);

    private final IConfigService configService;
    private final IComfyLifecycleService lifecycleService;
    private final ProcessTracker processTracker;

    /**
     * Minimal constructor for testing or basic injection.
     *
     * @param configService configuration service
     */
    public LocalAudioService(IConfigService configService) {
        this(configService, null, null);
    }

    /**
     * Dependency injection constructor.
     *
     * @param configService    configuration service
     * @param lifecycleService ComfyUI lifecycle monitor
     * @param processTracker   process tracker
     */
    @Autowired
    public LocalAudioService(IConfigService configService,
                             @Autowired(required = false) IComfyLifecycleService lifecycleService,
                             @Autowired(required = false) ProcessTracker processTracker) {
        this.configService = configService;
        this.lifecycleService = lifecycleService;
        this.processTracker = processTracker;
    }

    @Override
    public boolean generateSoundEffect(String sfxPrompt, int durationSeconds, File targetFile) {
        if (targetFile == null) {
            logger.warn("[LocalAudioService] Target file cannot be null");
            return false;
        }

        int duration = durationSeconds > 0 ? durationSeconds : 4;
        String prompt = (sfxPrompt != null && !sfxPrompt.isBlank())
                ? sfxPrompt.trim()
                : "Cinematic atmospheric soundscape";

        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        // 1. Attempt ComfyUI Neural Audio Generation
        if (isNeuralAudioAvailable()) {
            try {
                logger.info("🎵 [LocalAudioService] Attempting neural audio synthesis via ComfyUI for: '{}'", prompt);
                boolean comfySuccess = executeComfyUiAudioGeneration(prompt, duration, targetFile);
                if (comfySuccess && targetFile.exists() && targetFile.length() > 1024) {
                    logger.info("✅ [LocalAudioService] ComfyUI neural audio generated successfully: {}", targetFile.getName());
                    return true;
                }
            } catch (Exception ex) {
                logger.warn("⚠️ [LocalAudioService] ComfyUI neural audio failed: {}. Falling back to procedural audio synthesis.", ex.getMessage());
            }
        }

        // 2. Procedural Audio Synthesis via FFmpeg lavfi filters
        logger.info("🎬 [LocalAudioService] Synthesizing cinematic SFX track via FFmpeg: '{}' ({}s)", prompt, duration);
        return executeProceduralSynthesis(prompt, duration, targetFile);
    }

    @Override
    public boolean isNeuralAudioAvailable() {
        if (lifecycleService != null && !lifecycleService.isHealthy()) {
            return false;
        }
        String comfyUrl = configService.getComfyUIUrl();
        if (comfyUrl == null || comfyUrl.isBlank()) {
            return false;
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(comfyUrl + "/object_info"))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONObject info = new JSONObject(response.body());
                return info.has("StabilityTextToAudio")
                        || info.has("StableAudioOpenLoader")
                        || info.has("AudioCraft")
                        || info.has("TextEncodeAceStepAudio")
                        || info.has("MMAudio");
            }
        } catch (Exception ignored) {
            // Non-fatal availability check
        }
        return false;
    }

    /**
     * Executes procedural cinematic sound synthesis using FFmpeg's advanced audio filtering.
     * Selects acoustic profiles based on semantic keyword analysis (e.g., rocket, engine, blast, wind, sci-fi).
     *
     * @param prompt      descriptive sound effect prompt
     * @param durationSec duration in seconds
     * @param targetFile  output destination
     * @return true on success
     */
    boolean executeProceduralSynthesis(String prompt, int durationSec, File targetFile) {
        String ffmpegPath = configService.getFfmpegPath();
        String lower = prompt.toLowerCase(Locale.ROOT);

        String lavfiExpression;
        if (lower.contains("rocket") || lower.contains("thruster") || lower.contains("ignition")
                || lower.contains("blast") || lower.contains("engine") || lower.contains("launch")
                || lower.contains("rumble") || lower.contains("explosion") || lower.contains("fire")) {
            // Deep sub-bass rocket roar with brown noise and slow resonant modulation
            lavfiExpression = String.format(Locale.US,
                    "anoisesrc=d=%d:c=brown:r=44100:a=0.85,lowpass=f=260,flanger=delay=12:depth=4:speed=0.35,volume=1.6",
                    durationSec);
        } else if (lower.contains("wind") || lower.contains("storm") || lower.contains("sand")
                || lower.contains("desert") || lower.contains("breeze") || lower.contains("haze")) {
            // Atmospheric wind noise with dynamic tremolo
            lavfiExpression = String.format(Locale.US,
                    "anoisesrc=d=%d:c=pink:r=44100:a=0.6,bandpass=f=450:width_type=h:w=320,tremolo=f=0.35:d=0.7",
                    durationSec);
        } else if (lower.contains("ocean") || lower.contains("sea") || lower.contains("water")
                || lower.contains("waves") || lower.contains("rain") || lower.contains("river")) {
            // Oceanic ambient wash
            lavfiExpression = String.format(Locale.US,
                    "anoisesrc=d=%d:c=pink:r=44100:a=0.7,lowpass=f=1100,tremolo=f=0.22:d=0.55",
                    durationSec);
        } else if (lower.contains("space") || lower.contains("alien") || lower.contains("sci-fi")
                || lower.contains("cyber") || lower.contains("electric") || lower.contains("visor")) {
            // Cinematic sub-drone with flanger modulation
            lavfiExpression = String.format(Locale.US,
                    "sine=f=75:d=%d,flanger=delay=10:depth=3:speed=0.25,volume=1.1",
                    durationSec);
        } else {
            // General cinematic sub-bass atmospheric drone
            lavfiExpression = String.format(Locale.US,
                    "anoisesrc=d=%d:c=brown:r=44100:a=0.55,lowpass=f=340,volume=1.3",
                    durationSec);
        }

        List<String> cmd = List.of(
                ffmpegPath, "-y",
                "-f", "lavfi",
                "-i", lavfiExpression,
                "-t", String.valueOf(durationSec),
                "-c:a", "pcm_s16le",
                "-ar", "44100",
                "-ac", "2",
                targetFile.getAbsolutePath()
        );

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            Process p = processTracker != null ? processTracker.start(pb) : pb.start();
            int exit = p.waitFor();
            return exit == 0 && targetFile.exists() && targetFile.length() > 512;
        } catch (Exception ex) {
            logger.error("❌ [LocalAudioService] Procedural audio synthesis failed: {}", ex.getMessage());
            return false;
        }
    }

    private boolean executeComfyUiAudioGeneration(String prompt, int durationSec, File targetFile) throws Exception {
        String comfyUrl = configService.getComfyUIUrl();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .build();

        JSONObject info = fetchObjectInfo(client, comfyUrl);
        if (info == null) return false;

        JSONObject workflow;
        if (info.has("StabilityTextToAudio") || info.has("StableAudioOpenLoader")) {
            workflow = ComfyAudioWorkflowBuilder.buildStableAudioWorkflow("StableAudioOpenLoader", prompt, durationSec);
        } else if (info.has("AudioCraft")) {
            workflow = ComfyAudioWorkflowBuilder.buildGenericAudioWorkflow("AudioCraft", prompt, durationSec);
        } else {
            return false;
        }

        HttpRequest promptRequest = HttpRequest.newBuilder()
                .uri(URI.create(comfyUrl + "/prompt"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(workflow.toString()))
                .build();

        HttpResponse<String> promptResponse = client.send(promptRequest, HttpResponse.BodyHandlers.ofString());
        if (promptResponse.statusCode() != 200) {
            return false;
        }

        String promptId = new JSONObject(promptResponse.body()).getString("prompt_id");
        String finishedFilename = pollForAudioFilename(client, comfyUrl, promptId);
        if (finishedFilename == null || finishedFilename.isBlank()) {
            return false;
        }

        String downloadUrl = comfyUrl + "/view?filename=" + finishedFilename + "&type=output";
        HttpRequest dlReq = HttpRequest.newBuilder().uri(URI.create(downloadUrl)).GET().build();
        HttpResponse<InputStream> dlResp = client.send(dlReq, HttpResponse.BodyHandlers.ofInputStream());

        if (dlResp.statusCode() == 200) {
            try (InputStream is = dlResp.body()) {
                Files.copy(is, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        }
        return false;
    }

    private JSONObject fetchObjectInfo(HttpClient client, String comfyUrl) {
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(comfyUrl + "/object_info")).GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                return new JSONObject(resp.body());
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String pollForAudioFilename(HttpClient client, String comfyUrl, String promptId) throws Exception {
        int timeoutCount = 0;
        while (timeoutCount < 40) {
            Thread.sleep(2500);
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
