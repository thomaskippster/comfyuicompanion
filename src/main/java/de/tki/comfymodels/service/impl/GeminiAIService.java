package de.tki.comfymodels.service.impl;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class GeminiAIService {

    @Autowired
    private ConfigService configService;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private String activeModel = "gemini-1.5-flash";

    protected String getApiBaseUrl() {
        return "https://generativelanguage.googleapis.com";
    }

    public String getActiveModel() {
        return activeModel;
    }

    private static final List<String> MODEL_PRIORITY = Arrays.asList(
            "gemini-3.1-pro-preview", "gemini-3-flash-preview", "gemini-3.1-flash-lite-preview",
            "gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.5-flash-lite",
            "gemini-1.5-pro", "gemini-1.5-flash"
    );

    public String discoverBestModel() {
        String apiKey = configService.getGeminiApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) return "None";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getApiBaseUrl() + "/v1beta/models?key=" + apiKey))
                    .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONObject json = new JSONObject(response.body());
                JSONArray models = json.getJSONArray("models");
                List<String> available = new ArrayList<>();
                for (int i = 0; i < models.length(); i++) available.add(models.getJSONObject(i).getString("name").replace("models/", ""));
                for (String preferred : MODEL_PRIORITY) {
                    if (available.contains(preferred)) { activeModel = preferred; return activeModel; }
                }
            }
        } catch (Exception e) {}
        return activeModel;
    }

    public String discoverBestRepo(String modelName, String fileName, String metadataContext) {
        String apiKey = configService.getGeminiApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) return null;

        try {
            String context = metadataContext != null ? metadataContext : "No context";
            String shortContext = context.length() > 25000 ? context.substring(0, 25000) : context;

            String prompt = "Web search: Determine the official Hugging Face repository for the file '" + modelName + "'.\n\n" +
                    "CONTEXT:\n" +
                    "Workflow file: " + fileName + "\n" +
                    "Workflow data: " + shortContext + "\n\n" +
                    "INSTRUCTION:\n" +
                    "1. Identify the exact Hugging Face repository (e.g., black-forest-labs/FLUX.1-schnell).\n" +
                    "2. Respond ONLY with the repository ID (format: creator/repo) or 'UNKNOWN'.\n" +
                    "3. If you find a direct download link, output it instead.";

            JSONObject payload = new JSONObject();
            JSONArray contents = new JSONArray();
            contents.put(new JSONObject().put("role", "user")
                    .put("parts", new JSONArray().put(new JSONObject().put("text", prompt))));
            payload.put("contents", contents);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getApiBaseUrl() + "/v1beta/models/" + activeModel + ":generateContent?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String result = new JSONObject(response.body()).getJSONArray("candidates")
                        .getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                        .getJSONObject(0).getString("text").trim();
                return result;
            }
        } catch (Exception e) {}
        return null;
    }

    public String analyzeModel(String modelName) {
        String apiKey = configService.getGeminiApiKey();
        if (apiKey == null || apiKey.isEmpty()) return null;
        try {
            JSONObject payload = new JSONObject();
            JSONArray contents = new JSONArray();
            contents.put(new JSONObject().put("role", "user")
                    .put("parts", new JSONArray().put(new JSONObject().put("text", "Analyze: " + modelName + ". Return 'Creator | Arch'."))));
            payload.put("contents", contents);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getApiBaseUrl() + "/v1beta/models/" + activeModel + ":generateContent?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return new JSONObject(response.body()).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim();
            }
        } catch (Exception ignored) {}
        return null;
    }

    public byte[] generateImageNanoBanana(String prompt, byte[] inputImageBytes, String inputImageMimeType, int seed) throws Exception {
        String apiKey = configService.getGeminiApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("Gemini API Key is not set.");
        }

        JSONObject payload = new JSONObject();
        JSONArray parts = new JSONArray();
        
        // 1. Text Prompt
        parts.put(new JSONObject().put("text", prompt != null ? prompt : ""));
        
        // 2. Optional Input Image
        if (inputImageBytes != null && inputImageBytes.length > 0) {
            String mime = inputImageMimeType != null ? inputImageMimeType : "image/png";
            String base64Data = java.util.Base64.getEncoder().encodeToString(inputImageBytes);
            JSONObject inlineData = new JSONObject()
                    .put("mimeType", mime)
                    .put("data", base64Data);
            parts.put(new JSONObject().put("inlineData", inlineData));
        }
        
        JSONArray contents = new JSONArray();
        contents.put(new JSONObject().put("role", "user").put("parts", parts));
        payload.put("contents", contents);
        
        JSONObject genConfig = new JSONObject();
        JSONArray modalities = new JSONArray();
        modalities.put("IMAGE");
        genConfig.put("responseModalities", modalities);
        
        if (seed != -1) {
            genConfig.put("seed", seed);
        }
        payload.put("generationConfig", genConfig);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getApiBaseUrl() + "/v1beta/models/gemini-3.1-flash-image-preview:generateContent?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        int statusCode = response.statusCode();
        if (statusCode == 200) {
            JSONObject responseJson = new JSONObject(response.body());
            JSONArray candidates = responseJson.optJSONArray("candidates");
            if (candidates != null && candidates.length() > 0) {
                JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
                if (content != null) {
                    JSONArray resParts = content.optJSONArray("parts");
                    if (resParts != null) {
                        for (int i = 0; i < resParts.length(); i++) {
                            JSONObject part = resParts.getJSONObject(i);
                            JSONObject inlineData = part.optJSONObject("inlineData");
                            if (inlineData != null) {
                                String dataBase64 = inlineData.optString("data");
                                if (dataBase64 != null && !dataBase64.isEmpty()) {
                                    return java.util.Base64.getDecoder().decode(dataBase64);
                                }
                            }
                        }
                    }
                }
            }
            throw new IOException("Kein Bild in der API-Antwort gefunden.\nAntwort: " + response.body());
        } else if (statusCode == 429) {
            throw new IOException("Ratenbegrenzung überschritten (Resource Exhausted - HTTP 429).\n\n"
                    + "Bei kostenlosen Gemini API Keys (insbesondere für Preview-Modelle wie gemini-3.1-flash-image-preview) gelten sehr strenge Ratenbegrenzungen (oft nur 1-2 Bilder pro Minute).\n\n"
                    + "Bitte warte 1-2 Minuten und versuche es dann erneut.");
        } else {
            String errorMsg = response.body();
            try {
                JSONObject errObj = new JSONObject(errorMsg);
                if (errObj.has("error")) {
                    JSONObject innerErr = errObj.getJSONObject("error");
                    String msg = innerErr.optString("message");
                    String status = innerErr.optString("status");
                    if (status != null && !status.isEmpty()) {
                        errorMsg = status + ": " + msg;
                    } else if (msg != null && !msg.isEmpty()) {
                        errorMsg = msg;
                    }
                }
            } catch (Exception ignored) {}
            throw new IOException("Gemini API Error (status " + statusCode + "): " + errorMsg);
        }
    }
}

