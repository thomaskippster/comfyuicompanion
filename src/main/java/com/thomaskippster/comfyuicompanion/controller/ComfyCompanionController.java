package com.thomaskippster.comfyuicompanion.controller;

import com.thomaskippster.comfyuicompanion.service.AgenticWorkflowOrchestrator;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*") // Erlaubt den lokalen Zugriff vom Vite/React Frontend
public class ComfyCompanionController {

    private final AgenticWorkflowOrchestrator orchestrator;
    private final com.thomaskippster.comfyuicompanion.client.ComfyHttpClient comfyHttpClient;

    public ComfyCompanionController(AgenticWorkflowOrchestrator orchestrator,
                                    com.thomaskippster.comfyuicompanion.client.ComfyHttpClient comfyHttpClient) {
        this.orchestrator = orchestrator;
        this.comfyHttpClient = comfyHttpClient;
    }

    /**
     * Haupt-Endpunkt für das Frontend. Nimmt den rohen User-Prompt entgegen,
     * feuert den Cognitive Router (LLM) an, prüft Lücken via Auto-Provisioning,
     * baut den dynamischen Graphen und übergibt ihn an ComfyUI.
     */
    @PostMapping(value = "/generate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, String>> generateImage(@RequestBody Map<String, String> request) {
        String prompt = request.getOrDefault("prompt", "");
        
        if (prompt.trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException("Prompt darf nicht leer sein."));
        }

        return orchestrator.orchestrateAndRun(prompt)
                .map(promptId -> Map.of(
                        "status", "processing", 
                        "promptId", promptId, 
                        "message", "Agentischer Workflow wurde erfolgreich orchestriert und übergeben."
                ));
    }

    @GetMapping(value = "/status/{promptId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<com.fasterxml.jackson.databind.JsonNode> getStatus(@PathVariable String promptId) {
        // Zieht die History für diesen Prompt von ComfyUI
        return comfyHttpClient.getHistory(promptId);
    }

    @GetMapping(value = "/image/{filename}", produces = MediaType.IMAGE_PNG_VALUE)
    public Mono<byte[]> getImage(@PathVariable String filename) {
        return comfyHttpClient.downloadAsset(filename);
    }
}
