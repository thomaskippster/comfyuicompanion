package de.tki.comfyuicompanion.service.provisioning;

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
     * Searches Hugging Face for high-quality models matching the keyword
     * and target architecture, providing ready-to-use download URLs.
     *
     * @param keyword The keyword suggested by the LLM (e.g. "juggernaut")
     * @param architecture The target architecture (e.g. "SDXL")
     */
    public reactor.core.publisher.Mono<List<ModelRecommendation>> searchModelsReactive(String keyword, String architecture) {
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

        logger.info("Searching models on Hugging Face with keyword: '{}' and tags: '{}'", keyword, finalFilter);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/models")
                        .queryParam("search", keyword)
                        .queryParam("filter", finalFilter)
                        .queryParam("sort", "downloads")
                        .queryParam("direction", "-1")
                        .queryParam("limit", 20) // Increased limit to compensate for potential NSFW filtering
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::parseRecommendations)
                .onErrorResume(e -> {
                    logger.error("Error communicating with Hugging Face API: {}", e.getMessage());
                    return reactor.core.publisher.Mono.just(new ArrayList<>());
                });
    }

    /**
     * Synchronous convenience method for existing non-reactive callers.
     */
    public List<ModelRecommendation> searchModels(String keyword, String architecture) {
        List<ModelRecommendation> result = searchModelsReactive(keyword, architecture).block();
        return result != null ? result : new ArrayList<>();
    }

    private List<ModelRecommendation> parseRecommendations(JsonNode responseArray) {
        List<ModelRecommendation> recommendations = new ArrayList<>();
        if (responseArray != null && responseArray.isArray()) {
            for (JsonNode node : responseArray) {
                // Check for NSFW tag to enforce youth protection
                boolean isNsfw = false;
                if (node.has("tags") && node.get("tags").isArray()) {
                    for (JsonNode tagNode : node.get("tags")) {
                        if ("nsfw".equalsIgnoreCase(tagNode.asText())) {
                            isNsfw = true;
                            break;
                        }
                    }
                }
                
                if (isNsfw) {
                    continue; // Skip NSFW models
                }

                String repoId = node.has("id") ? node.get("id").asText() : "";
                int downloads = node.has("downloads") ? node.get("downloads").asInt() : 0;

                if (!repoId.isEmpty()) {
                    ModelRecommendation rec = new ModelRecommendation();
                    rec.setRepositoryId(repoId);
                    rec.setDownloads(downloads);

                    String[] parts = repoId.split("/");
                    String modelName = parts.length > 1 ? parts[1] : repoId;
                    rec.setName(modelName);

                    String downloadUrl = String.format("https://huggingface.co/%s/resolve/main/%s.safetensors", repoId, modelName);
                    rec.setDownloadUrl(downloadUrl);

                    recommendations.add(rec);
                    
                    if (recommendations.size() >= 5) {
                        break; // Keep the original limit of 5 results
                    }
                }
            }
        }
        return recommendations;
    }
}
