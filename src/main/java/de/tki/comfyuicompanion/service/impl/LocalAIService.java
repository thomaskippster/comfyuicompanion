package de.tki.comfyuicompanion.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
/**
 * Represents the local ai service class.
 */
public class LocalAIService {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LocalAIService.class);

    private final ConfigService configService;
    private final LocalGemmaService localGemmaService;

    private boolean gemmaEnabled = false;

    private final HttpClient client;

    public LocalAIService() {
        this(null, null);
    }

    @Autowired
    public LocalAIService(
            @Autowired(required = false) ConfigService configService,
            @Autowired(required = false) LocalGemmaService localGemmaService) {
        this.configService = configService;
        this.localGemmaService = localGemmaService;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

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

    /**
     * Handles the predict provider operation.
     * @param fileName the fileName
     * @return the Prediction result
     */
    public Prediction predictProvider(String fileName) {
        return predict(fileName, false);
    }

    /**
     * Handles the predict from url operation.
     * @param url the url
     * @return the Prediction result
     */
    public Prediction predictFromUrl(String url) {
        return predict(url, true);
    }

    private Prediction predict(String input, boolean isUrl) {
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

        if (maxScore > 0.5 && !bestProvider.equals("community")) {
            double confidence = Math.min(0.99, maxScore / 3.5);
            if (isUrl && maxScore > 1.5) confidence = Math.max(confidence, 0.8);
            return new Prediction(bestProvider, confidence);
        }

        // Fallback to local Gemma if TF-IDF heuristics yielded no confident result
        if (gemmaEnabled && !javax.swing.SwingUtilities.isEventDispatchThread() && localGemmaService != null && localGemmaService.isModelDownloaded()) {
            try {
                String prompt = "Identify the creator or architecture. Respond ONLY with the creator name (e.g. 'black-forest-labs', 'stabilityai', 'PonyDiffusion', etc.). If you cannot determine it, respond 'community'.\nInput: " + input;
                String resText = localGemmaService.generateCompletion("You are a model metadata analyzer for ComfyUI. Respond with ONLY the single creator name or 'community', no formatting, no sentences.", prompt, 0.2f, 15);
                resText = resText.replaceAll("[\"'\\.`\\*\\n\\r]", "").trim();
                if (!resText.isEmpty() && !resText.equalsIgnoreCase("community")) {
                    return new Prediction(resText, 0.95);
                }
            } catch (Exception e) {
                logger.error("Local Gemma prediction failed: " + e.getMessage());
            }
        }

        double confidence = Math.min(0.99, maxScore / 3.5);
        if (isUrl && maxScore > 1.5) confidence = Math.max(confidence, 0.8); // Higher confidence from URLs

        return new Prediction(bestProvider, confidence);
    }

    /**
     * Represents the prediction class.
     */
    public class Prediction {
        public final String provider;
        public final double confidence;
        public Prediction(String provider, double confidence) { this.provider = provider; this.confidence = confidence; }
        /**
         * Handles the get label operation.
         * @return the String result
         */
        public String getLabel() {
            if (confidence > 0.75) return "🧠 AI Verified: " + provider;
            if (confidence > 0.35) return "🎯 AI Predicted: " + provider;
            return "👥 Community";
        }
    }

    /**
     * Handles the get gemma completions operation.
     * @param subjectText the subjectText
     * @return the List<String> result
     */
    public List<String> getGemmaCompletions(String subjectText) {
        return getDirectGemmaCompletions(subjectText);
    }

    /**
     * Handles the optimize prompt operation.
     * @param rawPrompt the rawPrompt
     * @param modelName the modelName
     * @return the String result
     * @throws Exception if an error occurs
     */
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

    /**
     * Handles the is local gemma downloaded operation.
     * @return the boolean result
     */
    public boolean isLocalGemmaDownloaded() {
        return localGemmaService != null && localGemmaService.isModelDownloaded();
    }

    /**
     * Handles the is gemma enabled operation.
     * @return the boolean result
     */
    public boolean isGemmaEnabled() {
        return gemmaEnabled;
    }

    /**
     * Handles the set gemma enabled operation.
     * @param gemmaEnabled the gemmaEnabled
     */
    public void setGemmaEnabled(boolean gemmaEnabled) {
        this.gemmaEnabled = gemmaEnabled;
    }
    
    /**
     * Handles the get local gemma service operation.
     * @return the LocalGemmaService result
     */
    public LocalGemmaService getLocalGemmaService() {
        return localGemmaService;
    }

    /**
     * Checks if a specific local LLM model preset is downloaded.
     *
     * @param preset the preset to check
     * @return true if downloaded and valid
     */
    public boolean isLocalGemmaDownloaded(LocalGemmaService.LlmModelPreset preset) {
        return localGemmaService != null && localGemmaService.isModelDownloaded(preset);
    }
    
    /**
     * Generates 4 diverse, highly optimized, and visually rich prompt completions for text-to-image models.
     * Formulates scene composition, cinematic lighting, optical depth, camera lenses, and textures.
     *
     * @param subjectText raw user subject input
     * @return list of 4 optimized prompt variations, or null if generation fails
     */
    public List<String> getDirectGemmaCompletions(String subjectText) {
        if (localGemmaService == null || !localGemmaService.isModelDownloaded()) {
            return null;
        }
        try {
            String systemInstruction = """
                    You are an elite visual prompt engineer specializing in state-of-the-art text-to-image diffusion and flow-matching models (FLUX.1, SDXL, Midjourney).
                    Transform the user's raw subject into 4 distinct, highly optimized, and visually rich image generation prompts.

                    CRITICAL IMAGE GENERATION RULES:
                    1. VISUAL LAYERING: Detail tangible subject characteristics, specific lighting (e.g. dramatic chiaroscuro, golden hour rim lighting, volumetric rays through mist, moody neon ambient glow), environmental depth, camera optics (e.g. 35mm film still, 85mm f/1.4 portrait lens, shallow depth of field, anamorphic bokeh), and tactile textures (weathered brass, raw silk, wet cobblestone).
                    2. NO EMPTY BUZZWORDS: Never use empty boilerplate words like "photorealistic", "hyperrealistic", "8k", "masterpiece", "trending on artstation", or "best quality". Quality must be evoked through concrete visual reality.
                    3. MULTILINGUAL SUPPORT: If the input is in German or another language, seamlessly interpret its intent and generate rich, evocative English descriptions.
                    4. DIVERSE PERSPECTIVES: Produce 4 contrasting artistic interpretations:
                       - Variation 1: Cinematic photorealism with dramatic lighting and shallow depth of field.
                       - Variation 2: Atmospheric storytelling with rich environmental depth and weather mood.
                       - Variation 3: Stylized / High-end editorial concept art with striking composition and textures.
                       - Variation 4: Dynamic action, intimate macro detail, or dramatic wide-angle perspective.

                    OUTPUT FORMAT:
                    Return EXCLUSIVELY a valid JSON array of exactly 4 strings.
                    Example: ["Prompt 1...", "Prompt 2...", "Prompt 3...", "Prompt 4..."]
                    Do NOT wrap in markdown code fences (no ```json). Do NOT add introductory or conversational text.""";

            String userPrompt = "Subject to expand: " + subjectText;
            String resText = localGemmaService.generateCompletion(systemInstruction, userPrompt, 0.7f, 480);
            if (resText != null) {
                resText = resText.trim();
                if (resText.startsWith("```")) {
                    resText = resText.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
                }
                int start = resText.indexOf('[');
                int end = resText.lastIndexOf(']');
                if (start >= 0 && end > start) {
                    resText = resText.substring(start, end + 1);
                }
                JSONArray arr = new JSONArray(resText);
                List<String> list = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    String s = arr.getString(i).trim();
                    if (!s.isEmpty()) {
                        list.add(s);
                    }
                }
                return list;
            }
        } catch (Throwable e) {
            logger.error("Direct local LLM prompt suggestions failed: {}", e.getMessage(), e);
        }
        return null;
    }
    
    /**
     * Handles the optimize prompt directly operation.
     * @param rawPrompt the rawPrompt
     * @param modelName the modelName
     * @return the String result
     * @throws Throwable if an error occurs
     */
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

        String systemInstruction = """
                You are an expert prompt engineer and configuration advisor for ComfyUI text-to-image models. Your task is to translate the user's raw input prompt into an optimized JSON payload containing the optimized positive prompt, negative prompt, steps, and CFG scale tailored for the specific model architecture.
                
                GUIDELINE FOR TARGET MODEL ARCHITECTURE (%s):
                %s
                
                Instructions:
                1. Translate non-English concepts to English and optimize the positive prompt according to the guideline.
                2. Recommend an appropriate negative prompt (empty for models like Flux if they don't use negative prompts, or containing common negative keywords like 'blurry, low quality' for SDXL/SD1.5).
                3. Recommend dynamic steps (e.g. 4 for Flux Schnell, 20-30 for Flux Dev / SDXL, 20 for SD 1.5) and CFG scale (1.0 for Flux, 3.0-4.5 for Lumina2, 4.0-8.0 for SDXL/SD 1.5) based on the target architecture and prompt mood.
                4. Respond ONLY with a valid JSON object. Do NOT use markdown code blocks (no ```json). Do NOT write any introduction, notes, or explanation.
                
                JSON Structure:
                {
                  "positive_prompt": "string",
                  "negative_prompt": "string",
                  "cfg": float,
                  "steps": integer
                }""".formatted(arch, promptGuide);
                
        return localGemmaService.generateCompletion(systemInstruction, "Original prompt: " + rawPrompt + "\n\nJSON:", 0.7f, 384);
    }

    /**
     * Handles the generate text operation.
     * @param promptText the promptText
     * @param temperature the temperature
     * @param maxTokens the maxTokens
     * @return the String result
     */
    public String generateText(String promptText, float temperature, int maxTokens) {
        if (localGemmaService != null && localGemmaService.isModelDownloaded()) {
            try {
                return localGemmaService.generateCompletion("You are a helpful assistant.", promptText, temperature, maxTokens);
            } catch (Throwable t) {
                logger.error("Local Gemma generation failed: " + t.getMessage());
            }
        }
        return null;
    }
}
