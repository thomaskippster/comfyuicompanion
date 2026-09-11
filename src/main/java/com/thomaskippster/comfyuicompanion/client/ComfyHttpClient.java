package com.thomaskippster.comfyuicompanion.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.thomaskippster.comfyuicompanion.domain.graph.ComfyWorkflow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Service
public class ComfyHttpClient {

    private final WebClient webClient;

    @org.springframework.beans.factory.annotation.Autowired
    public ComfyHttpClient(
            @Value("${comfyui.api.url:http://127.0.0.1:8188}") String baseUrl,
            @org.springframework.beans.factory.annotation.Autowired(required = false) de.tki.comfymodels.service.impl.ConfigService configService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) WebClient.Builder webClientBuilder) {
        String effectiveUrl = (configService != null && configService.getComfyUIUrl() != null && !configService.getComfyUIUrl().isBlank())
                ? configService.getComfyUIUrl()
                : baseUrl;
        WebClient.Builder builder = webClientBuilder != null ? webClientBuilder : WebClient.builder();
        this.webClient = builder.baseUrl(effectiveUrl).build();
    }

    public ComfyHttpClient(String baseUrl) {
        this(baseUrl, null, null);
    }

    /**
     * Triggers the workflow execution on ComfyUI.
     * 
     * @param workflow The ComfyUI workflow graph
     * @param clientId A unique client identifier for WebSocket telemetry
     * @return A Mono emitting the prompt_id as a String upon successful triggering
     */
    public Mono<String> triggerWorkflow(ComfyWorkflow workflow, String clientId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("prompt", workflow);
        payload.put("client_id", clientId);

        return webClient.post()
                .uri("/prompt")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> response.get("prompt_id").asText());
    }

    /**
     * Downloads the final generated asset from ComfyUI.
     * 
     * @param filename The name of the file to download
     * @return A Mono emitting the byte array of the asset
     */
    public Mono<byte[]> downloadAsset(String filename) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/view")
                        .queryParam("filename", filename)
                        .queryParam("type", "output")
                        .build())
                .retrieve()
                .bodyToMono(byte[].class);
    }

    /**
     * Retrieves the execution history for a given prompt ID.
     * This is required to parse the final filename of the generated asset.
     */
    public Mono<JsonNode> getHistory(String promptId) {
        return webClient.get()
                .uri("/history/{promptId}", promptId)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    /**
     * Zwingt den ComfyUI-Server, seine lokalen Modell-Ordner (Checkpoints, LoRAs, etc.)
     * neu einzulesen, ohne dass der Prozess neu gestartet werden muss.
     * Ideal für Hot-Reloading nach einem automatischen Download.
     */
    public Mono<Void> triggerModelRefresh() {
        return webClient.post()
                .uri("/cmfc/refresh-models")
                .header("Content-Type", "application/json")
                .bodyValue("{\"force_reload\": true}")
                .retrieve()
                .bodyToMono(Void.class);
    }

    /**
     * Prüft den aktuellen Systemstatus von ComfyUI (z.B. VRAM Nutzung, Geräte).
     */
    public Mono<JsonNode> getSystemStats() {
        return webClient.get()
                .uri("/system_stats")
                .retrieve()
                .bodyToMono(JsonNode.class);
    }
}
