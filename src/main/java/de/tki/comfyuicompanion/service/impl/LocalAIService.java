package de.tki.comfyuicompanion.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.json.JSONArray;
import jakarta.annotation.PostConstruct;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;

/**
 * Service providing local AI heuristics, creator prediction, and local LLM-driven
 * visual prompt engineering and completion suggestions.
 */
@Service
public class LocalAIService {
    private static final Logger logger = LoggerFactory.getLogger(LocalAIService.class);
    private static final int MAX_SUBJECT_INPUT_CHARS = 500;
    private static final int SUGGESTION_MAX_TOKENS = 700;

    private final ConfigService configService;
    private final LocalGemmaService localGemmaService;
    private boolean gemmaEnabled = false;
    private final HttpClient client;
    private final Map<String, String[]> KNOWLEDGE_BASE = new LinkedHashMap<>();
    private final Map<String, Double> GLOBAL_IDF = new HashMap<>();

    public LocalAIService() {
        this(null, null);
    }

    @Autowired
    public LocalAIService(
            @Autowired(required = false) ConfigService configService,
            @Autowired(required = false) LocalGemmaService localGemmaService) {
        this.configService = configService;
        this.localGemmaService = localGemmaService;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @PostConstruct
    public void init() {
        add("black-forest-labs", "flux, flux1, flux2, bfl, schnell, dev, pro, mistral");
        add("stabilityai", "sdxl, sd15, sd21, sd3, svd, cascade, turbo, lightning, stable, diffusion");
        add("Wan-AI", "wan, wan2, wan2.1, wan2.2, ti2v, t2v");
        add("nvidia", "cosmos, world, tokenizer");
        add("genmo", "mochi");
        add("Comfy-Org", "repack, flux_text_encoders, wan_repackaged, mochi_repackaged, lumina");
        add("TencentARC", "hunyuan, hyt");
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
     * Predicts the model creator/provider from a filename.
     * @param fileName the model filename
     * @return the Prediction result
     */
    public Prediction predictProvider(String fileName) {
        return predict(fileName, false);
    }

    /**
     * Predicts the model creator/provider from a URL.
     * @param url the model URL
     * @return the Prediction result
     */
    public Prediction predictFromUrl(String url) {
        return predict(url, true);
    }

    private Prediction predict(String input, boolean isUrl) {
        if (input == null || input.isBlank()) {
            return new Prediction("community", 0.0);
        }
        String name = input.toLowerCase(Locale.ROOT);
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
                        if (isUrl && provider.toLowerCase().contains(token)) score += 2.0;
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
        if (isUrl && maxScore > 1.5) confidence = Math.max(confidence, 0.8);
        return new Prediction(bestProvider, confidence);
    }

    /**
     * Represents a provider prediction with confidence score.
     */
    public class Prediction {
        public final String provider;
        public final double confidence;
        public Prediction(String provider, double confidence) { this.provider = provider; this.confidence = confidence; }
        /**
         * Returns the formatted UI label for this prediction.
         * @return the formatted label
         */
        public String getLabel() {
            if (confidence > 0.75) return "🧠 AI Verified: " + provider;
            if (confidence > 0.35) return "🎯 AI Predicted: " + provider;
            return "👥 Community";
        }
    }

    /**
     * Returns prompt completions generated by the local LLM.
     * @param subjectText the raw subject text
     * @return list of prompt suggestions
     */
    public List<String> getGemmaCompletions(String subjectText) {
        return getDirectGemmaCompletions(subjectText);
    }

    /**
     * Optimizes a user prompt for a specific model architecture.
     * @param rawPrompt the raw user prompt
     * @param modelName the target model name
     * @return the optimized JSON payload string
     * @throws Exception if optimization fails
     */
    public String optimizePrompt(String rawPrompt, String modelName) throws Exception {
        try {
            return optimizePromptDirectly(rawPrompt, modelName);
        } catch (Throwable t) {
            if (t instanceof Exception ex) throw ex;
            throw new Exception(t);
        }
    }

    /** @return true if the active local LLM model is downloaded */
    public boolean isLocalGemmaDownloaded() {
        return localGemmaService != null && localGemmaService.isModelDownloaded();
    }

    /** @return true if Gemma provider prediction is enabled */
    public boolean isGemmaEnabled() { return gemmaEnabled; }

    /** @param gemmaEnabled whether to enable Gemma provider prediction */
    public void setGemmaEnabled(boolean gemmaEnabled) { this.gemmaEnabled = gemmaEnabled; }
    
    /** @return the underlying LocalGemmaService */
    public LocalGemmaService getLocalGemmaService() { return localGemmaService; }

    /**
     * Checks if a specific local LLM model preset is downloaded.
     * @param preset the preset to check
     * @return true if downloaded and valid
     */
    public boolean isLocalGemmaDownloaded(LocalGemmaService.LlmModelPreset preset) {
        return localGemmaService != null && localGemmaService.isModelDownloaded(preset);
    }
    
    /**
     * Generates 4 diverse, highly optimized, and visually rich prompt completions for text-to-image models.
     *
     * @param subjectText raw user subject input
     * @return list of 4 optimized prompt variations, or null if generation fails
     */
    public List<String> getDirectGemmaCompletions(String subjectText) {
        if (localGemmaService == null || !localGemmaService.isModelDownloaded()) {
            return null;
        }
        try {
            String normalizedSubject = subjectText != null ? subjectText.trim() : "";
            if (normalizedSubject.length() > MAX_SUBJECT_INPUT_CHARS) {
                normalizedSubject = normalizedSubject.substring(0, MAX_SUBJECT_INPUT_CHARS).trim();
            }

            String systemInstruction = """
                    You are an elite visual prompt engineer specializing in state-of-the-art text-to-image diffusion and flow-matching models (FLUX.1, SDXL, Midjourney).
                    Transform the user's raw subject into 4 distinct, highly optimized, and visually rich image generation prompts.

                    CRITICAL IMAGE GENERATION RULES:
                    1. VISUAL LAYERING: Detail tangible subject characteristics, specific lighting (e.g. dramatic chiaroscuro, golden hour rim lighting, volumetric rays through mist, moody neon ambient glow), environmental depth, camera optics (e.g. 35mm film still, 85mm f/1.4 portrait lens, shallow depth of field), and tactile textures.
                    2. NO EMPTY BUZZWORDS: Never use boilerplate words like "photorealistic", "hyperrealistic", "8k", "masterpiece", "trending on artstation", or "best quality".
                    3. MULTILINGUAL SUPPORT: If the input is in German or another language, seamlessly interpret its intent and generate evocative English descriptions.
                    4. CONCISE LENGTH: Keep each prompt between 35 and 60 words so all 4 prompts fit cleanly without truncation. Do NOT use double quotes (") inside any prompt text.
                    5. DIVERSE PERSPECTIVES:
                       - Variation 1: Cinematic realism with dramatic lighting and shallow depth of field.
                       - Variation 2: Atmospheric storytelling with rich environmental depth and weather mood.
                       - Variation 3: Stylized high-end editorial concept art with striking composition.
                       - Variation 4: Dynamic action, macro detail, or dramatic wide-angle perspective.

                    OUTPUT FORMAT:
                    Return EXCLUSIVELY a valid JSON array of 4 strings: ["Prompt 1...", "Prompt 2...", "Prompt 3...", "Prompt 4..."]
                    Do NOT wrap in markdown code fences. Do NOT add conversational text.""";

            String userPrompt = "Subject to expand: " + normalizedSubject;
            String resText = localGemmaService.generateCompletion(systemInstruction, userPrompt, 0.7f, SUGGESTION_MAX_TOKENS);
            if (resText != null) {
                List<String> parsed = parseCompletionsJsonArray(resText);
                if (!parsed.isEmpty()) {
                    return parsed;
                }
            }
        } catch (Throwable e) {
            logger.error("Direct local LLM prompt suggestions failed: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * Parses a JSON array of prompt completion strings resiliently, recovering completed
     * and partially truncated strings if the LLM output hit the token limit mid-generation.
     *
     * @param rawResponse raw text generated by the local LLM
     * @return list of extracted non-empty prompt strings
     */
    List<String> parseCompletionsJsonArray(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return List.of();
        }
        String cleaned = rawResponse.replace("\u0000", "")
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", " ")
                .trim();

        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
        }

        int start = cleaned.indexOf('[');
        int end = cleaned.lastIndexOf(']');
        if (start >= 0 && end > start) {
            String candidate = cleaned.substring(start, end + 1);
            try {
                JSONArray arr = new JSONArray(candidate);
                List<String> list = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    String s = arr.optString(i, "").trim();
                    if (!s.isEmpty()) list.add(s);
                }
                if (!list.isEmpty()) return list;
            } catch (Exception e) {
                logger.debug("Standard JSONArray parsing failed ({}), falling back to resilient extractor.", e.getMessage());
            }
        }

        return extractStringsResiliently(start >= 0 ? cleaned.substring(start) : cleaned);
    }

    private List<String> extractStringsResiliently(String arrayFragment) {
        List<String> extracted = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        int len = arrayFragment.length();

        for (int i = 0; i < len; i++) {
            char c = arrayFragment.charAt(i);
            if (!inString) {
                if (c == '"') {
                    inString = true;
                    current.setLength(0);
                } else if (c == ']') {
                    break;
                }
            } else {
                if (escaped) {
                    current.append((c == 'n' || c == 'r' || c == 't') ? ' ' : c);
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    if (isClosingQuote(arrayFragment, i + 1)) {
                        String val = current.toString().replaceAll("\\s+", " ").trim();
                        if (!val.isEmpty()) extracted.add(val);
                        inString = false;
                    } else {
                        current.append('\'');
                    }
                } else if (c == '\n' || c == '\r') {
                    current.append(' ');
                } else {
                    current.append(c);
                }
            }
        }

        if (inString && current.length() >= 25) {
            String salvaged = salvageTruncatedPrompt(current.toString());
            if (!salvaged.isEmpty()) extracted.add(salvaged);
        }
        return extracted;
    }

    private boolean isClosingQuote(String s, int nextIdx) {
        int i = nextIdx;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        if (i >= s.length()) return true;
        char next = s.charAt(i);
        return next == ',' || next == ']';
    }

    private String salvageTruncatedPrompt(String rawPartial) {
        String normalized = rawPartial.replaceAll("\\s+", " ").trim();
        int lastPunct = Math.max(normalized.lastIndexOf('.'), Math.max(normalized.lastIndexOf(','), normalized.lastIndexOf(';')));
        if (lastPunct >= 25) {
            String cut = normalized.substring(0, lastPunct).trim();
            return cut.endsWith(".") ? cut : cut + ".";
        }
        int lastSpace = normalized.lastIndexOf(' ');
        if (lastSpace >= 25) {
            return normalized.substring(0, lastSpace).trim() + ".";
        }
        return normalized;
    }
    
    /**
     * Optimizes a prompt directly using the local LLM for the specified model architecture.
     * @param rawPrompt the raw user prompt
     * @param modelName the target model name
     * @return the JSON response string
     * @throws Throwable if generation fails
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

        String promptGuide = switch (arch) {
            case "Lumina2" -> "The target model is Lumina-2 (Qwen text encoder). It performs best with rich, detailed visual language paragraphs (1-3 sentences) describing spatial arrangements, lighting, style, colors, and camera work. Do NOT use generic quality tags.";
            case "Wan" -> "The target model is Wan2.1 (T2I). It performs best with descriptive visual language descriptions of texture, scene depth, cinematic details, and atmosphere in natural flow.";
            case "Flux" -> "The target model is FLUX (flow-matching DiT). It performs best with a detailed descriptive paragraph in natural English describing subject, environment, composition, camera style, lighting, and textures.";
            case "SD3" -> "The target model is Stable Diffusion 3 / 3.5. It performs best with clear, descriptive visual language paragraphs detailing the composition, subject, and style.";
            case "SDXL" -> "The target model is Stable Diffusion XL (SDXL). It performs best with a clean descriptive sentence followed by clear style modifiers and camera keywords.";
            default -> "The target model is Stable Diffusion 1.5. It performs best with comma-separated tag/keyword lists starting with the main subject, followed by lighting, style, and quality modifiers.";
        };

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
     * Generates freeform text using the local LLM.
     * @param promptText the user prompt
     * @param temperature sampling temperature
     * @param maxTokens maximum tokens to predict
     * @return generated text or null
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
