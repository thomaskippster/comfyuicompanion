package de.tki.comfymodels.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.json.JSONObject;
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

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private String queryOllama(String queryText) {
        if (configService == null || !configService.isUseOllama()) return null;
        try {
            String baseUrl = configService.getOllamaUrl();
            String model = configService.getOllamaModel();
            String prompt = "You are a model metadata analyzer for ComfyUI. Given a model filename or download URL, identify its creator or architecture. " +
                            "Respond ONLY with the author or project name (e.g. 'black-forest-labs', 'stabilityai', 'PonyDiffusion', 'city96', 'Kijai', 'lllyasviel', etc.). " +
                            "If you cannot determine it, respond 'community'. " +
                            "Input: " + queryText + "\n" +
                            "Response:";
            JSONObject payload = new JSONObject();
            payload.put("model", model);
            payload.put("prompt", prompt);
            payload.put("stream", false);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/generate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONObject json = new JSONObject(response.body());
                String resText = json.optString("response", "").trim();
                resText = resText.replaceAll("[\"'\\.`\\*\\n\\r]", "").trim();
                if (!resText.isEmpty()) {
                    return resText;
                }
            }
        } catch (Exception e) {
            System.err.println("Ollama prediction failed: " + e.getMessage());
        }
        return null;
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

    public Prediction predictProvider(String fileName) {
        return predict(fileName, false);
    }

    public Prediction predictFromUrl(String url) {
        return predict(url, true);
    }

    private Prediction predict(String input, boolean isUrl) {
        if (configService != null && configService.isUseOllama()) {
            String ollamaRes = queryOllama(input);
            if (ollamaRes != null && !ollamaRes.equalsIgnoreCase("community") && !ollamaRes.isEmpty()) {
                return new Prediction(ollamaRes, 0.95);
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
}
