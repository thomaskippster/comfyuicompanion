package com.thomaskippster.comfyuicompanion.service.provisioning;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

@Service
public class HuggingFaceClient {

    private static final Logger logger = LoggerFactory.getLogger(HuggingFaceClient.class);
    private final WebClient webClient;

    @org.springframework.beans.factory.annotation.Autowired
    public HuggingFaceClient(@org.springframework.beans.factory.annotation.Autowired(required = false) WebClient.Builder webClientBuilder) {
        WebClient.Builder builder = webClientBuilder != null ? webClientBuilder : WebClient.builder();
        this.webClient = builder.baseUrl("https://huggingface.co/api").build();
    }

    /**
     * Sucht auf Hugging Face nach qualitativ hochwertigen Modellen, die das Keyword
     * und die Zielarchitektur abdecken, und liefert fertige Download-URLs.
     *
     * @param keyword Das vom LLM vorgeschlagene Schlagwort (z. B. "juggernaut")
     * @param architecture Die Zielarchitektur (z. B. "SDXL")
     * @return Eine nach Downloads sortierte Liste von Empfehlungen
     */
    public List<ModelRecommendation> searchModels(String keyword, String architecture) {
        List<ModelRecommendation> recommendations = new ArrayList<>();

        // Mappe unsere interne Architektur auf Hugging Face Tags
        String archTag = "";
        if ("SDXL".equalsIgnoreCase(architecture)) {
            archTag = "stable-diffusion-xl";
        } else if ("SD1.5".equalsIgnoreCase(architecture)) {
            archTag = "stable-diffusion";
        }
        
        String tempFilter = "safetensors";
        if (!archTag.isEmpty()) {
            tempFilter += "," + archTag;
        }
        final String finalFilter = tempFilter;

        logger.info("Suche Modelle auf Hugging Face mit Keyword: '{}' und Tags: '{}'", keyword, finalFilter);

        try {
            JsonNode responseArray = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/models")
                            .queryParam("search", keyword)
                            .queryParam("filter", finalFilter)
                            .queryParam("sort", "downloads")
                            .queryParam("direction", "-1") // Absteigend (Beliebteste zuerst)
                            .queryParam("limit", 5) // Wir benötigen nur die Top-Ergebnisse
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (responseArray != null && responseArray.isArray()) {
                for (JsonNode node : responseArray) {
                    String repoId = node.has("id") ? node.get("id").asText() : "";
                    int downloads = node.has("downloads") ? node.get("downloads").asInt() : 0;
                    
                    if (!repoId.isEmpty()) {
                        ModelRecommendation rec = new ModelRecommendation();
                        rec.setRepositoryId(repoId);
                        rec.setDownloads(downloads);
                        
                        // Extrahiere den reinen Modell-Namen ohne Autor/Organisation
                        String[] parts = repoId.split("/");
                        String modelName = parts.length > 1 ? parts[1] : repoId;
                        rec.setName(modelName);
                        
                        // Konstruktion der direkten URL zur .safetensors Datei im Main-Branch
                        // Format: https://huggingface.co/{author}/{model}/resolve/main/{model}.safetensors
                        String downloadUrl = String.format("https://huggingface.co/%s/resolve/main/%s.safetensors", repoId, modelName);
                        rec.setDownloadUrl(downloadUrl);
                        
                        recommendations.add(rec);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Fehler bei der Kommunikation mit der Hugging Face API: {}", e.getMessage());
        }

        return recommendations;
    }
}
