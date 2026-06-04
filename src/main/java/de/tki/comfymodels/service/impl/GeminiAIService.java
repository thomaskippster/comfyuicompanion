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
            "gemini-2.5-flash-lite", "gemini-1.5-flash-8b", "gemini-3.1-flash-lite-preview",
            "gemini-1.5-flash", "gemini-2.5-flash", "gemini-3-flash-preview",
            "gemini-2.5-pro", "gemini-1.5-pro", "gemini-3.1-pro-preview"
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


    private String detectModelArchitecture(String modelName) {
        if (modelName == null) return "SD15";
        String lower = modelName.toLowerCase();
        if (lower.contains("z_image_turbo") || lower.contains("acestep") || lower.contains("longcat") || lower.contains("lumina")) {
            return "Lumina2";
        }
        if (lower.contains("wan")) {
            return "Wan";
        }
        if (lower.contains("flux")) {
            return "Flux";
        }
        if (lower.contains("sd3") || lower.contains("stable_diffusion_3")) {
            return "SD3";
        }
        if (lower.contains("xl") || lower.contains("sdxl")) {
            return "SDXL";
        }
        return "SD15";
    }

    public String optimizePrompt(String rawPrompt) throws IOException {
        return optimizePrompt(rawPrompt, null);
    }

    public String optimizePrompt(String rawPrompt, String modelName) throws IOException {
        String apiKey = configService.getGeminiApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IOException("Gemini API key is not configured.");
        }

        try {
            String arch = detectModelArchitecture(modelName);
            String promptGuide = "";
            switch (arch) {
                case "Lumina2":
                    promptGuide = "The target model is Lumina-2 (Qwen text encoder). "
                            + "It performs best with rich, detailed natural language paragraphs (1-3 sentences) describing the scene. "
                            + "Focus on spatial arrangements, lighting, style, colors, and camera work. "
                            + "Do NOT use comma-separated keyword lists or generic quality tags like 'masterpiece', '8k', 'best quality', 'photorealistic'.";
                    break;
                case "Wan":
                    promptGuide = "The target model is Wan2.1 (T2I). "
                            + "It performs best with descriptive, detailed natural language descriptions of the scene. "
                            + "Focus on texture, scene depth, cinematic details, and atmosphere in natural flow. "
                            + "Avoid keyword lists or boilerplate quality tags.";
                    break;
                case "Flux":
                    promptGuide = "The target model is FLUX (flow-matching DiT). "
                            + "It has supreme prompt adherence and performs best with a highly detailed, descriptive paragraph in natural English. "
                            + "Describe the subject, clothing, environment, composition, camera style, lighting, and textures in detail as if explaining a scene to a photographer. "
                            + "Do NOT write tag/keyword lists, and do NOT use boilerplate quality words like 'hyperrealistic', '8k', 'masterpiece'.";
                    break;
                case "SD3":
                    promptGuide = "The target model is Stable Diffusion 3 / 3.5. "
                            + "It uses T5XXL and CLIP encoders. It performs best with clear, descriptive natural language paragraphs detailing the composition, subject, and style. "
                            + "Avoid keyword salads or excessive tags.";
                    break;
                case "SDXL":
                    promptGuide = "The target model is Stable Diffusion XL (SDXL). "
                            + "It performs best with a balanced mix: a clean descriptive sentence followed by clear style modifiers and camera keywords. "
                            + "Avoid extremely long paragraphs, but do not fall into pure keyword lists. Make it concise and high-impact.";
                    break;
                default: // SD15
                    promptGuide = "The target model is Stable Diffusion 1.5. "
                            + "It performs best with comma-separated tag/keyword lists. "
                            + "Start with the main subject, followed by detailed descriptions, lighting keywords, art medium/styles, and quality modifiers "
                            + "(e.g., 'masterpiece, best quality, highly detailed, sharp focus, 8k resolution, volumetric lighting, by [artist]').";
                    break;
            }

            String systemInstruction = "You are an expert prompt engineer for text-to-image models. "
                    + "Your task is to optimize a simple prompt into a highly effective English image generation prompt tailored for the specific model architecture.\n\n"
                    + "GUIDELINE FOR TARGET MODEL:\n" + promptGuide + "\n\n"
                    + "Instructions:\n"
                    + "1. Optimize the original prompt following the guideline above.\n"
                    + "2. Translate any non-English concepts to English.\n"
                    + "3. Respond ONLY with the optimized prompt text. Do not use explanations, annotations, markdown code blocks, or quotes.";

            String promptText = systemInstruction + "\n\nOriginal prompt: " + rawPrompt + "\n\nOptimized prompt:";

            JSONObject payload = new JSONObject();
            JSONArray contents = new JSONArray();
            contents.put(new JSONObject().put("role", "user")
                    .put("parts", new JSONArray().put(new JSONObject().put("text", promptText))));
            payload.put("contents", contents);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getApiBaseUrl() + "/v1beta/models/" + activeModel + ":generateContent?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode == 200) {
                String result = new JSONObject(response.body()).getJSONArray("candidates")
                        .getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                        .getJSONObject(0).getString("text").trim();
                return result;
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
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Error communicating with Gemini API: " + e.getMessage(), e);
        }
    }

    public List<String> getGemmaCompletions(String subjectText) throws IOException {
        String apiKey = configService.getGeminiApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IOException("Gemini API key is not configured.");
        }

        // Try gemma-2-9b-it, fall back to gemma-2-2b-it, then fall back to the active model
        String[] modelsToTry = {"gemma-2-9b-it", "gemma-2-2b-it", activeModel};
        Exception lastException = null;

        for (String modelName : modelsToTry) {
            try {
                String prompt = "You are a creative prompt engineer. Given a short core subject for an image generator, " +
                        "provide 3 different detailed visual suggestions/completions that expand this subject. " +
                        "Keep each suggestion to a single short descriptive sentence (maximum 15 words) focusing on visual details, textures, or character attributes. " +
                        "Format the response ONLY as a JSON array of strings, for example: " +
                        "[\"Suggestion one...\", \"Suggestion two...\", \"Suggestion three...\"]\n" +
                        "Do NOT wrap in markdown code blocks like ```json. Do NOT include any other text.\n" +
                        "Core subject: " + subjectText;

                JSONObject payload = new JSONObject();
                JSONArray contents = new JSONArray();
                contents.put(new JSONObject().put("role", "user")
                        .put("parts", new JSONArray().put(new JSONObject().put("text", prompt))));
                payload.put("contents", contents);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(getApiBaseUrl() + "/v1beta/models/" + modelName + ":generateContent?key=" + apiKey))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                int statusCode = response.statusCode();
                if (statusCode == 200) {
                    String text = new JSONObject(response.body()).getJSONArray("candidates")
                            .getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                            .getJSONObject(0).getString("text").trim();
                    
                    if (text.startsWith("```")) {
                        text = text.replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
                    }
                    
                    JSONArray arr = new JSONArray(text);
                    List<String> list = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        list.add(arr.getString(i));
                    }
                    if (!list.isEmpty()) {
                        return list;
                    }
                }
            } catch (Exception e) {
                lastException = e;
            }
        }

        // Final local fallback if all API calls fail
        return Arrays.asList(
            subjectText + " with intricate details",
            "A cinematic shot of " + subjectText,
            "A photorealistic " + subjectText + " in vibrant lighting"
        );
    }

    public String optimizePromptWithGemma(String rawPrompt, String modelName) throws IOException {
        String apiKey = configService.getGeminiApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IOException("Gemini API key is not configured.");
        }

        String[] modelsToTry = {"gemma-2-9b-it", "gemma-2-2b-it", activeModel};
        Exception lastException = null;

        for (String modelNameForApi : modelsToTry) {
            try {
                String arch = detectModelArchitecture(modelName);
                String promptGuide = "";
                switch (arch) {
                    case "Lumina2":
                        promptGuide = "The target model is Lumina-2 (Qwen text encoder). "
                                + "It performs best with rich, detailed visual language paragraphs (1-3 sentences) describing the scene. "
                                + "Focus on spatial arrangements, lighting, style, colors, and camera work. "
                                + "Do NOT use comma-separated keyword lists or generic quality tags like 'masterpiece', '8k', 'best quality', 'photorealistic'.";
                        break;
                    case "Wan":
                        promptGuide = "The target model is Wan2.1 (T2I). "
                                + "It performs best with descriptive, detailed visual language descriptions of the scene. "
                                + "Focus on texture, scene depth, cinematic details, and atmosphere in natural flow. "
                                + "Avoid keyword lists or boilerplate quality tags.";
                        break;
                    case "Flux":
                        promptGuide = "The target model is FLUX (flow-matching DiT). "
                                + "It has supreme prompt adherence and performs best with a highly detailed, descriptive paragraph in natural English. "
                                + "Describe the subject, clothing, environment, composition, camera style, lighting, and textures in detail as if explaining a scene to a photographer. "
                                + "Do NOT write tag/keyword lists, and do NOT use boilerplate quality words like 'hyperrealistic', '8k', 'masterpiece'.";
                        break;
                    case "SD3":
                        promptGuide = "The target model is Stable Diffusion 3 / 3.5. "
                                + "It uses T5XXL and CLIP encoders. It performs best with clear, descriptive visual language paragraphs detailing the composition, subject, and style. "
                                + "Avoid keyword salads or excessive tags.";
                        break;
                    case "SDXL":
                        promptGuide = "The target model is Stable Diffusion XL (SDXL). "
                                + "It performs best with a balanced mix: a clean descriptive sentence followed by clear style modifiers and camera keywords. "
                                + "Avoid extremely long paragraphs, but do not fall into pure keyword lists. Make it concise and high-impact.";
                        break;
                    default: // SD15
                        promptGuide = "The target model is Stable Diffusion 1.5. "
                                + "It performs best with comma-separated tag/keyword lists. "
                                + "Start with the main subject, followed by detailed descriptions, lighting keywords, art medium/styles, and quality modifiers "
                                + "(e.g., 'masterpiece, best quality, highly detailed, sharp focus, 8k resolution, volumetric lighting, by [artist]').";
                        break;
                }

                String systemInstruction = "You are an expert prompt engineer for text-to-image models. "
                        + "Your task is to optimize a simple prompt into a highly effective English image generation prompt tailored for the specific model architecture.\n\n"
                        + "GUIDELINE FOR TARGET MODEL:\n" + promptGuide + "\n\n"
                        + "Instructions:\n"
                        + "1. Optimize the original prompt following the guideline above.\n"
                        + "2. Translate any non-English concepts to English.\n"
                        + "3. Respond ONLY with the optimized prompt text. Do not use explanations, annotations, markdown code blocks, or quotes.";

                String promptText = systemInstruction + "\n\nOriginal prompt: " + rawPrompt + "\n\nOptimized prompt:";

                JSONObject payload = new JSONObject();
                JSONArray contents = new JSONArray();
                contents.put(new JSONObject().put("role", "user")
                        .put("parts", new JSONArray().put(new JSONObject().put("text", promptText))));
                payload.put("contents", contents);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(getApiBaseUrl() + "/v1beta/models/" + modelNameForApi + ":generateContent?key=" + apiKey))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                int statusCode = response.statusCode();
                if (statusCode == 200) {
                    String result = new JSONObject(response.body()).getJSONArray("candidates")
                            .getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                            .getJSONObject(0).getString("text").trim();
                    if (result.startsWith("```")) {
                        result = result.replaceAll("^```(text|json|markdown)?", "").replaceAll("```$", "").trim();
                    }
                    if (result.startsWith("\"") && result.endsWith("\"") && result.length() > 1) {
                        result = result.substring(1, result.length() - 1);
                    }
                    return result;
                } else {
                    lastException = new IOException("Gemini API Error (status " + statusCode + "): " + response.body());
                }
            } catch (Exception e) {
                lastException = e;
            }
        }
        if (lastException != null) {
            if (lastException instanceof IOException) throw (IOException) lastException;
            throw new IOException(lastException);
        }
        throw new IOException("Remote Gemma optimization failed.");
    }
}

