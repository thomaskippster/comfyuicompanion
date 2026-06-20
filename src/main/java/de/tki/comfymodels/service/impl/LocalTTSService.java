package de.tki.comfymodels.service.impl;

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

@Service
public class LocalTTSService {

    private static final java.util.Set<String> ATTEMPTED_INSTALLS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final ConfigService configService;

    @Autowired(required = false)
    private EnvironmentBootstrapperImpl bootstrapper;

    @Autowired(required = false)
    private IComfyLifecycleService lifecycleService;

    @Autowired
    public LocalTTSService(ConfigService configService) {
        this.configService = configService;
    }

    public void generateSpeech(String text, String outputPath) throws Exception {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Text cannot be empty for TTS generation");
        }

        String provider = configService.getTtsProvider();
        if ("ComfyUI KokoroTTS".equals(provider) || "ComfyUI ElevenLabs".equals(provider)) {
            System.out.println("ℹ️ [LocalTTSService] Attempting to generate TTS via " + provider + "...");
            boolean success = generateSpeechViaComfyUI(provider, text, outputPath);
            if (success) {
                return;
            }
            System.out.println("⚠️ [LocalTTSService] ComfyUI TTS generation failed. Falling back to Standalone Local Piper...");
        }

        File outFile = new File(outputPath);
        File parentDir = outFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        String piperPath = configService.getPiperPath();
        String modelPath = configService.getPiperModelPath();

        // Try downloading/setting up if missing
        if (!new File(piperPath).exists() && !new File(System.getProperty("user.dir"), piperPath).exists()) {
            downloadPiperAndModelIfMissing(piperPath, modelPath);
        } else if (!new File(modelPath).exists() && !new File(System.getProperty("user.dir"), modelPath).exists()) {
            downloadPiperAndModelIfMissing(piperPath, modelPath);
        }

        // Validate paths
        File piperBin = new File(piperPath);
        if (!piperBin.exists()) {
            File fallbackBin = new File(System.getProperty("user.dir"), piperPath);
            if (fallbackBin.exists()) {
                piperPath = fallbackBin.getAbsolutePath();
            } else {
                throw new java.io.IOException("Piper TTS binary not found at: " + piperBin.getAbsolutePath() + 
                    ". Please configure the correct path in settings.");
            }
        }

        File modelFile = new File(modelPath);
        if (!modelFile.exists()) {
            File fallbackModel = new File(System.getProperty("user.dir"), modelPath);
            if (fallbackModel.exists()) {
                modelPath = fallbackModel.getAbsolutePath();
            } else {
                throw new java.io.IOException("Piper ONNX model not found at: " + modelFile.getAbsolutePath() + 
                    ". Please download the model.");
            }
        }

        ProcessBuilder pb = new ProcessBuilder(
            piperPath,
            "--model", modelPath,
            "--output_file", outFile.getAbsolutePath()
        );

        Process process = pb.start();

        // Write text directly to stdin of the started process
        try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
            writer.write(text);
            writer.write("\n");
            writer.flush();
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            StringBuilder errorLog = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errorLog.append(line).append("\n");
                }
            }
            String errorMsg = "Piper TTS exited with code " + exitCode + ". Error: " + errorLog.toString().trim();
            System.err.println("❌ " + errorMsg);
            throw new java.io.IOException(errorMsg);
        }

        System.out.println("🎵 [LocalTTSService] Generated audio: " + outFile.getAbsolutePath());
    }

    private void downloadPiperAndModelIfMissing(String piperPath, String modelPath) {
        try {
            File piperBin = new File(piperPath);
            File toolsDir = piperBin.getParentFile();
            if (toolsDir == null) {
                toolsDir = new File(System.getProperty("user.dir"), "tools/tts");
            }
            if (!toolsDir.exists()) {
                toolsDir.mkdirs();
            }

            // 1. Download Piper Binary if missing
            if (!piperBin.exists() && !new File(System.getProperty("user.dir"), piperPath).exists()) {
                System.out.println("📥 [LocalTTSService] Piper binary not found. Attempting to download...");
                String osName = System.getProperty("os.name").toLowerCase();
                if (osName.contains("win")) {
                    String zipUrl = "https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_windows_amd64.zip";
                    downloadAndUnzip(zipUrl, toolsDir);
                } else {
                    System.out.println("⚠️ [LocalTTSService] Automated download only supported on Windows. Please install Piper manually on Linux/Mac.");
                }
            }

            // 2. Download Model if missing
            File modelFile = new File(modelPath);
            File modelFileFallback = new File(System.getProperty("user.dir"), modelPath);
            if (!modelFile.exists() && !modelFileFallback.exists()) {
                System.out.println("📥 [LocalTTSService] Piper model not found. Downloading en_US-lessac-medium.onnx...");
                File targetModelFile = modelFile.isAbsolute() ? modelFile : new File(System.getProperty("user.dir"), modelPath);
                File parentDir = targetModelFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }
                
                downloadFile("https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/en/en_US/lessac/medium/en_US-lessac-medium.onnx", targetModelFile);
                
                File targetModelJsonFile = new File(targetModelFile.getAbsolutePath() + ".json");
                if (!targetModelJsonFile.exists()) {
                    System.out.println("📥 [LocalTTSService] Downloading en_US-lessac-medium.onnx.json...");
                    downloadFile("https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/en/en_US/lessac/medium/en_US-lessac-medium.onnx.json", targetModelJsonFile);
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ [LocalTTSService] Failed to download Piper/model automatically: " + e.getMessage());
        }
    }

    private java.io.InputStream openUrlStreamWithUserAgent(String urlStr) throws IOException {
        java.net.URL url = new java.net.URL(urlStr);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        conn.setInstanceFollowRedirects(true);
        int status = conn.getResponseCode();
        if (status == java.net.HttpURLConnection.HTTP_MOVED_TEMP 
            || status == java.net.HttpURLConnection.HTTP_MOVED_PERM 
            || status == 307 
            || status == 308) {
            String newUrl = conn.getHeaderField("Location");
            return openUrlStreamWithUserAgent(newUrl);
        }
        if (status != java.net.HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP error code: " + status + " for URL: " + urlStr);
        }
        return conn.getInputStream();
    }

    private void downloadFile(String urlStr, File targetFile) throws IOException {
        try (java.io.InputStream in = openUrlStreamWithUserAgent(urlStr)) {
            java.nio.file.Files.copy(in, targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void downloadAndUnzip(String urlStr, File destDir) throws IOException {
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(openUrlStreamWithUserAgent(urlStr))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith("piper/")) {
                    name = name.substring(6);
                }
                if (name.isEmpty()) {
                    continue;
                }
                File file = new File(destDir, name);
                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    File parent = file.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private boolean generateSpeechViaComfyUI(String provider, String text, String outputPath) {
        try {
            if (lifecycleService != null && !lifecycleService.isHealthy()) {
                System.out.println("🔄 [LocalTTSService] ComfyUI server is offline. Attempting auto-start...");
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
                    System.out.println("⚠️ [LocalTTSService] Failed to auto-start ComfyUI.");
                    return false;
                }
                System.out.println("✅ [LocalTTSService] ComfyUI successfully started and healthy.");
            }

            String comfyUrl = configService.getComfyUIUrl();
            if (comfyUrl == null || comfyUrl.trim().isEmpty()) {
                System.out.println("⚠️ [LocalTTSService] ComfyUI URL is empty.");
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
                        System.out.println("⚠️ [LocalTTSService] ComfyUI-KokoroTTS installation/load was already attempted in this session. Skipping to avoid restart loop.");
                    } else {
                        ATTEMPTED_INSTALLS.add("ComfyUI-KokoroTTS");
                        if (!folderExists) {
                            System.out.println("⚠️ [LocalTTSService] KokoroTTS is missing and folder does not exist. Installing ComfyUI-KokoroTTS...");
                            System.out.println("🔄 [LocalTTSService] Stopping ComfyUI server to install custom nodes...");
                            lifecycleService.stop();
                            
                            String pythonPath = configService.getPythonPath();
                            if (comfyPath != null && !comfyPath.trim().isEmpty()) {
                                java.nio.file.Path comfyDir = java.nio.file.Paths.get(comfyPath);
                                java.nio.file.Path pythonExe = (pythonPath != null && !pythonPath.trim().isEmpty()) ? java.nio.file.Paths.get(pythonPath) : null;
                                bootstrapper.ensureKokoroTtsInstalled(comfyDir, pythonExe, System.out::println);
                            }
                            
                            System.out.println("🔄 [LocalTTSService] Restarting ComfyUI server after installation...");
                            lifecycleService.start();
                        } else {
                            System.out.println("🔄 [LocalTTSService] KokoroTTS node is not active but folder exists. Restarting ComfyUI server to load it...");
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
                        System.out.println("✅ [LocalTTSService] ComfyUI successfully restarted and healthy.");
                    }
                }
            }

            HttpRequest infoRequest = HttpRequest.newBuilder()
                    .uri(URI.create(comfyUrl + "/object_info"))
                    .GET()
                    .build();

            HttpResponse<String> infoResponse = client.send(infoRequest, HttpResponse.BodyHandlers.ofString());
            if (infoResponse.statusCode() != 200) {
                System.out.println("⚠️ [LocalTTSService] ComfyUI server returned status " + infoResponse.statusCode() + ".");
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
                    System.out.println("⚠️ [LocalTTSService] No Kokoro TTS custom node class found in ComfyUI.");
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
                    System.out.println("⚠️ [LocalTTSService] ElevenLabsTextToSpeech node not found in ComfyUI.");
                    return false;
                }

                String apiKey = configService.getElevenLabsApiKey();
                String voiceId = configService.getElevenLabsVoiceId();
                if (apiKey.isEmpty()) {
                    System.out.println("⚠️ [LocalTTSService] ElevenLabs API Key is not configured.");
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
            } else {
                return false;
            }

            System.out.println("🚀 [LocalTTSService] Submitting TTS workflow to ComfyUI...");
            HttpRequest promptRequest = HttpRequest.newBuilder()
                    .uri(URI.create(comfyUrl + "/prompt"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(workflowJson.toString()))
                    .build();

            HttpResponse<String> promptResponse = client.send(promptRequest, HttpResponse.BodyHandlers.ofString());
            if (promptResponse.statusCode() != 200) {
                System.out.println("⚠️ [LocalTTSService] Failed to send prompt to ComfyUI: " + promptResponse.body());
                return false;
            }

            JSONObject respObj = new JSONObject(promptResponse.body());
            String promptId = respObj.getString("prompt_id");

            System.out.println("⏳ [LocalTTSService] Polling ComfyUI history for prompt " + promptId + "...");
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
                System.out.println("⚠️ [LocalTTSService] Polling timed out or did not return audio filename.");
                return false;
            }

            System.out.println("📥 [LocalTTSService] Downloading generated audio: " + finishedFilename);
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
                System.out.println("🎵 [LocalTTSService] Generated ComfyUI audio saved to: " + outFile.getAbsolutePath());
                return true;
            } else {
                System.out.println("⚠️ [LocalTTSService] Failed to download audio. Status: " + dlResponse.statusCode());
                return false;
            }

        } catch (Exception e) {
            System.out.println("⚠️ [LocalTTSService] ComfyUI TTS failed: " + e.getMessage());
            return false;
        }
    }
}
