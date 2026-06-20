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

    @Autowired(required = false)
    private LocalGemmaService localGemmaService;

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
            "gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash", "gemini-1.5-flash",
            "gemini-1.5-pro", "gemini-2.5-flash-lite", "gemini-1.5-flash-8b",
            "gemini-3.5-flash", "gemini-3.1-flash-lite-preview", "gemini-3-flash-preview",
            "gemini-3.5-pro", "gemini-3.1-pro-preview"
    );

    public String discoverBestModel() {
        String apiKey = configService.getGeminiApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) return "None";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getApiBaseUrl() + "/v1beta/models"))
                    .header("x-goog-api-key", apiKey)
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
            } else {
                System.err.println("❌ [Gemini] Failed to discover best model. HTTP Status: " + response.statusCode() + " - " + response.body());
            }
        } catch (Exception e) {
            System.err.println("❌ [Gemini] Failed to discover best model: " + e.getMessage());
        }
        return activeModel;
    }

    private String generateWithLocalGemma(String systemInstruction, String userPrompt, float temp, int maxTokens) {
        if (localGemmaService != null && localGemmaService.isModelDownloaded()) {
            try {
                System.out.println("ℹ️ [Gemini Fallback] Performing inference with local Gemma model...");
                return localGemmaService.generateCompletion(systemInstruction, userPrompt, temp, maxTokens);
            } catch (Exception e) {
                System.err.println("❌ [Gemma Fallback] Local Gemma inference failed: " + e.getMessage());
            }
        }
        return null;
    }

    public String discoverBestRepo(String modelName, String fileName, String metadataContext) {
        String apiKey = configService.getGeminiApiKey();
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

        if (apiKey == null || apiKey.trim().isEmpty()) {
            return generateWithLocalGemma("", prompt, 0.7f, 150);
        }

        try {
            JSONObject payload = new JSONObject();
            JSONArray contents = new JSONArray();
            contents.put(new JSONObject().put("role", "user")
                    .put("parts", new JSONArray().put(new JSONObject().put("text", prompt))));
            payload.put("contents", contents);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getApiBaseUrl() + "/v1beta/models/" + activeModel + ":generateContent"))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String result = new JSONObject(response.body()).getJSONArray("candidates")
                        .getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                        .getJSONObject(0).getString("text").trim();
                return result;
            } else {
                System.err.println("❌ [Gemini] Failed to discover best repo. HTTP Status: " + response.statusCode() + " - " + response.body());
                if (response.statusCode() == 429 || response.statusCode() == 503) {
                    return generateWithLocalGemma("", prompt, 0.7f, 150);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ [Gemini] Failed to discover best repo for model " + modelName + ": " + e.getMessage());
            return generateWithLocalGemma("", prompt, 0.7f, 150);
        }
        return null;
    }

    public String analyzeModel(String modelName) {
        String prompt = "Analyze: " + modelName + ". Return 'Creator | Arch'.";
        return generateWithLocalGemma("", prompt, 0.2f, 15);
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
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            return optimizePromptWithGeminiAPI(rawPrompt, modelName, apiKey.trim());
        }
        
        // Fallback to local Gemma immediately if downloaded
        if (localGemmaService != null && localGemmaService.isModelDownloaded()) {
            return optimizePromptWithLocalGemma(rawPrompt, modelName);
        }
        throw new IOException("Neither Gemini API key is configured nor local Gemma model is downloaded.");
    }


    private String optimizePromptWithLocalGemma(String rawPrompt, String modelName) throws IOException {
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

        return localGemmaService.generateCompletion(systemInstruction, "Original prompt: " + rawPrompt + "\n\nOptimized prompt:", 0.7f, 256);
    }

    public List<String> getGemmaCompletions(String subjectText) throws IOException {
        // ALWAYS use local Gemma immediately if downloaded
        if (localGemmaService != null && localGemmaService.isModelDownloaded()) {
            return getLocalGemmaCompletionsDirectly(subjectText);
        }
        throw new IOException("Local Gemma model is not downloaded/available.");
    }


    private List<String> getLocalGemmaCompletionsDirectly(String subjectText) throws IOException {
        String prompt = "You are a creative prompt engineer. Given a short core subject for an image generator, " +
                "provide 3 different detailed visual suggestions/completions that expand this subject. " +
                "Keep each suggestion to a single short descriptive sentence (maximum 15 words) focusing on visual details, textures, or character attributes. " +
                "Format the response ONLY as a JSON array of strings, for example: " +
                "[\"Suggestion one...\", \"Suggestion two...\", \"Suggestion three...\"]\n" +
                "Do NOT wrap in markdown code blocks like ```json. Do NOT include any other text.\n" +
                "Core subject: " + subjectText;

        String resText = localGemmaService.generateCompletion("You are a helpful assistant.", prompt, 0.7f, 150);
        if (resText != null) {
            resText = resText.trim();
            if (resText.startsWith("```")) {
                resText = resText.replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
            }
            JSONArray arr = new JSONArray(resText);
            List<String> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.getString(i));
            }
            return list;
        }
        throw new IOException("Local Gemma completion returned empty response.");
    }

    public String optimizePromptWithGemma(String rawPrompt, String modelName) throws IOException {
        // ALWAYS use local Gemma immediately if downloaded
        if (localGemmaService != null && localGemmaService.isModelDownloaded()) {
            return optimizePromptWithLocalGemma(rawPrompt, modelName);
        }
        throw new IOException("Local Gemma model is not downloaded/available.");
    }

    private String optimizePromptWithGeminiAPI(String rawPrompt, String modelName, String apiKey) throws IOException {
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

        try {
            JSONObject payload = new JSONObject();
            JSONArray contents = new JSONArray();
            contents.put(new JSONObject().put("role", "user")
                    .put("parts", new JSONArray().put(new JSONObject().put("text", "Original prompt: " + rawPrompt))));
            payload.put("contents", contents);
            
            JSONObject systemInstructionObj = new JSONObject();
            systemInstructionObj.put("parts", new JSONArray().put(new JSONObject().put("text", systemInstruction)));
            payload.put("systemInstruction", systemInstructionObj);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getApiBaseUrl() + "/v1beta/models/" + activeModel + ":generateContent"))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String result = new JSONObject(response.body()).getJSONArray("candidates")
                        .getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                        .getJSONObject(0).getString("text").trim();
                return result;
            } else {
                throw new IOException("Gemini API call failed with status: " + response.statusCode() + " - " + response.body());
            }
        } catch (Exception e) {
            throw new IOException("Gemini API prompt optimization failed: " + e.getMessage(), e);
        }
    }
}
