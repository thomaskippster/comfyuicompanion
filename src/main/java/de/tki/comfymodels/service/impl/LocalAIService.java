package de.tki.comfymodels.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.json.JSONObject;
import org.json.JSONArray;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Service
public class LocalAIService {
    @Autowired
    private ConfigService configService;

    @Autowired
    private LocalGemmaService localGemmaService;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final Map<String, String[]> KNOWLEDGE_BASE = new LinkedHashMap<>();
    private final Map<String, Double> GLOBAL_IDF = new HashMap<>();

    @PostConstruct
    public void init() {
        // --- TOP RESEARCH LABS ---
        add("black-forest-labs", "flux, flux1, flux2, bfl, schnell, dev, pro, mistral");
        add("stabilityai", "sdxl, sd15, sd21, sd3, svd, cascade, turbo, lightning, stable, diffusion");
        add("Wan-AI", "wan, wan2, wan2.1, wan2.2, ti2v, t2v");
        add("nvidia", "cosmos, world, tokenizer");
        add("genmo", "mochi");
        add("Comfy-Org", "repack, flux_text_encoders, wan_repackaged, mochi_repackaged, lumina");
        add("TencentARC", "hunyuan, hyt");
        
        // --- TOP COMMUNITY CREATORS ---
        add("Lykon", "dreamshaper, absolute, reality, anytime, alys");
        add("PonyDiffusion", "pony, v6, v7, score_9, rating_explicit");
        add("XpucT", "deliberate, real-vision");
        add("city96", "gguf, quantized");
        add("Kijai", "kj, nodes, flux-fp8");
        add("6tZ", "aesthetic, flux_lora");
        add("Peli86", "realism, enhance");
        add("lllyasviel", "controlnet, adapter, annotator, depth, canny, scribble");
        
        calculateIdf();
    }

    private void add(String provider, String keywords) {
        KNOWLEDGE_BASE.put(provider, keywords.split(", "));
    }

    private void calculateIdf() {
        Set<String> allTerms = new HashSet<>();
        for (String[] terms : KNOWLEDGE_BASE.values()) allTerms.addAll(Arrays.asList(terms));
        int docCount = KNOWLEDGE_BASE.size();
        for (String term : allTerms) {
            long count = KNOWLEDGE_BASE.values().stream().filter(list -> Arrays.asList(list).contains(term)).count();
            GLOBAL_IDF.put(term, Math.log((double) docCount / (1.0 + count)));
        }
    }

    public Prediction predictProvider(String fileName) {
        return predict(fileName, false);
    }

    public Prediction predictFromUrl(String url) {
        return predict(url, true);
    }

    private Prediction predict(String input, boolean isUrl) {
        if (localGemmaService != null && localGemmaService.isModelDownloaded()) {
            try {
                String prompt = "Identify the creator or architecture. Respond ONLY with the creator name (e.g. 'black-forest-labs', 'stabilityai', 'PonyDiffusion', etc.). If you cannot determine it, respond 'community'.\nInput: " + input;
                String resText = localGemmaService.generateCompletion("You are a model metadata analyzer for ComfyUI. Respond with ONLY the single creator name or 'community', no formatting, no sentences.", prompt, 0.2f, 15);
                resText = resText.replaceAll("[\"'\\.`\\*\\n\\r]", "").trim();
                if (!resText.isEmpty() && !resText.equalsIgnoreCase("community")) {
                    return new Prediction(resText, 0.95);
                }
            } catch (Exception e) {
                System.err.println("Local Gemma prediction failed: " + e.getMessage());
            }
        }

        String name = input.toLowerCase();
        if (!isUrl) {
            if (name.contains("mistral") && name.contains("flux2")) return new Prediction("black-forest-labs", 0.95);
            if (name.contains("pony") || name.contains("v6")) return new Prediction("PonyDiffusion", 0.85);
        }

        List<String> queryTokens = Arrays.asList(name.split("[_\\- \\.\\/\\?=\\&]"));
        Map<String, Double> similarityScores = new HashMap<>();

        for (Map.Entry<String, String[]> entry : KNOWLEDGE_BASE.entrySet()) {
            String provider = entry.getKey();
            double score = 0.0;
            for (String token : queryTokens) {
                if (token.length() < 2) continue;
                for (String docTerm : entry.getValue()) {
                    if (token.equals(docTerm) || (token.contains(docTerm) && docTerm.length() > 3)) {
                        score += GLOBAL_IDF.getOrDefault(docTerm, 1.0) * (docTerm.length() / 4.0);
                        if (isUrl && provider.toLowerCase().contains(token)) score += 2.0; // Bonus for provider name in URL
                    }
                }
            }
            if (score > 0) similarityScores.put(provider, score);
        }

        String bestProvider = "community";
        double maxScore = 0.0;
        for (Map.Entry<String, Double> entry : similarityScores.entrySet()) {
            if (entry.getValue() > maxScore) { maxScore = entry.getValue(); bestProvider = entry.getKey(); }
        }

        double confidence = Math.min(0.99, maxScore / 3.5);
        if (isUrl && maxScore > 1.5) confidence = Math.max(confidence, 0.8); // Higher confidence from URLs

        return new Prediction(bestProvider, confidence);
    }

    public class Prediction {
        public final String provider;
        public final double confidence;
        public Prediction(String provider, double confidence) { this.provider = provider; this.confidence = confidence; }
        public String getLabel() {
            if (confidence > 0.75) return "🧠 AI Verified: " + provider;
            if (confidence > 0.35) return "🎯 AI Predicted: " + provider;
            return "👥 Community";
        }
    }

    public List<String> getGemmaCompletions(String subjectText) {
        return getDirectGemmaCompletions(subjectText);
    }

    public String optimizePrompt(String rawPrompt, String modelName) throws Exception {
        try {
            return optimizePromptDirectly(rawPrompt, modelName);
        } catch (Throwable t) {
            if (t instanceof Exception) {
                throw (Exception) t;
            }
            throw new Exception(t);
        }
    }

    public boolean isLocalGemmaDownloaded() {
        return localGemmaService != null && localGemmaService.isModelDownloaded();
    }
    
    public LocalGemmaService getLocalGemmaService() {
        return localGemmaService;
    }
    
    public List<String> getDirectGemmaCompletions(String subjectText) {
        if (localGemmaService == null || !localGemmaService.isModelDownloaded()) return null;
        try {
            String systemInstruction = "You are a creative prompt engineer. Given a short core subject for an image generator, " +
                    "provide 3 different detailed visual suggestions/completions that expand this subject. " +
                    "Keep each suggestion to a single short descriptive sentence (maximum 15 words) focusing on visual details, textures, or character attributes. " +
                    "Format the response ONLY as a JSON array of strings, for example: " +
                    "[\"Suggestion one...\", \"Suggestion two...\", \"Suggestion three...\"]\n" +
                    "Do NOT wrap in markdown code blocks like ```json. Do NOT include any other text.";
            String userPrompt = "Core subject: " + subjectText;
            
            String resText = localGemmaService.generateCompletion(systemInstruction, userPrompt, 0.7f, 150);
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
        } catch (Throwable e) {
            System.err.println("Direct local Gemma completions failed: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
    
    public String optimizePromptDirectly(String rawPrompt, String modelName) throws Throwable {
        if (localGemmaService == null || !localGemmaService.isModelDownloaded()) {
            throw new Exception("Direct local Gemma is not downloaded.");
        }
        
        String arch = "SD15";
        if (modelName != null) {
            String lower = modelName.toLowerCase();
            if (lower.contains("z_image_turbo") || lower.contains("acestep") || lower.contains("longcat") || lower.contains("lumina") || lower.contains("qwen") || lower.contains("firered")) {
                arch = "Lumina2";
            } else if (lower.contains("wan") || lower.contains("ltx")) {
                arch = "Wan";
            } else if (lower.contains("flux")) {
                arch = "Flux";
            } else if (lower.contains("sd3") || lower.contains("stable_diffusion_3")) {
                arch = "SD3";
            } else if (lower.contains("xl") || lower.contains("sdxl") || lower.contains("ernie")) {
                arch = "SDXL";
            }
        }

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
                
        return localGemmaService.generateCompletion(systemInstruction, "Original prompt: " + rawPrompt, 0.7f, 256);
    }

    public String generateText(String promptText, float temperature, int maxTokens) {
        if (localGemmaService != null && localGemmaService.isModelDownloaded()) {
            try {
                return localGemmaService.generateCompletion("You are a helpful assistant.", promptText, temperature, maxTokens);
            } catch (Throwable t) {
                System.err.println("Local Gemma generation failed: " + t.getMessage());
            }
        }
        return null;
    }
}
