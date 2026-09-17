package de.tki.comfyuicompanion.client;

import com.fasterxml.jackson.databind.JsonNode;
import de.tki.comfyuicompanion.domain.graph.ComfyWorkflow;
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
            @org.springframework.beans.factory.annotation.Autowired(required = false) de.tki.comfyuicompanion.service.impl.ConfigService configService,
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

    private static final java.time.Duration RETRY_BACKOFF = java.time.Duration.ofMillis(300);
    private static final int MAX_RETRIES = 3;

    private reactor.util.retry.Retry retryPolicy() {
        return reactor.util.retry.Retry.backoff(MAX_RETRIES, RETRY_BACKOFF)
                .maxBackoff(java.time.Duration.ofSeconds(2))
                .filter(t -> !(t instanceof org.springframework.web.reactive.function.client.WebClientResponseException.NotFound));
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
                .map(response -> {
                    if (response.has("prompt_id")) {
                        return response.get("prompt_id").asText();
                    }
                    throw new de.tki.comfyuicompanion.exception.ComfyApiException("Response missing prompt_id: " + response, 200, "/prompt");
                })
                .retryWhen(retryPolicy())
                .onErrorMap(e -> e instanceof de.tki.comfyuicompanion.exception.ComfyApiException ? e :
                        new de.tki.comfyuicompanion.exception.ComfyApiException("Failed to trigger workflow on ComfyUI", null, "/prompt", e));
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
                .bodyToMono(byte[].class)
                .retryWhen(retryPolicy())
                .onErrorMap(e -> new de.tki.comfyuicompanion.exception.ComfyApiException("Failed to download asset: " + filename, null, "/view", e));
    }

    /**
     * Retrieves the execution history for a given prompt ID.
     * This is required to parse the final filename of the generated asset.
     */
    public Mono<JsonNode> getHistory(String promptId) {
        return webClient.get()
                .uri("/history/{promptId}", promptId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .retryWhen(retryPolicy())
                .onErrorMap(e -> new de.tki.comfyuicompanion.exception.ComfyApiException("Failed to fetch history for promptId: " + promptId, null, "/history/" + promptId, e));
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
                .bodyToMono(Void.class)
                .retryWhen(retryPolicy())
                .onErrorMap(e -> new de.tki.comfyuicompanion.exception.ComfyApiException("Failed to refresh models on ComfyUI", null, "/cmfc/refresh-models", e));
    }

    /**
     * Prüft den aktuellen Systemstatus von ComfyUI (z.B. VRAM Nutzung, Geräte).
     */
    public Mono<JsonNode> getSystemStats() {
        return webClient.get()
                .uri("/system_stats")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .retryWhen(retryPolicy())
                .onErrorMap(e -> new de.tki.comfyuicompanion.exception.ComfyApiException("Failed to get system stats from ComfyUI", null, "/system_stats", e));
    }
}
