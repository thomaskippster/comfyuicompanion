package com.thomaskippster.comfyuicompanion.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.thomaskippster.comfyuicompanion.client.ComfyHttpClient;
import com.thomaskippster.comfyuicompanion.dto.GenerationRequest;
import com.thomaskippster.comfyuicompanion.dto.GenerationResponse;
import com.thomaskippster.comfyuicompanion.service.AgenticWorkflowOrchestrator;
import de.tki.comfymodels.service.SafePathValidator;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * REST controller for ComfyUI Companion generation, workflow status, and asset retrieval.
 */
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class ComfyCompanionController {

    private final AgenticWorkflowOrchestrator orchestrator;
    private final ComfyHttpClient comfyHttpClient;
    private final SafePathValidator safePathValidator;

    public ComfyCompanionController(AgenticWorkflowOrchestrator orchestrator,
                                    ComfyHttpClient comfyHttpClient,
                                    SafePathValidator safePathValidator) {
        this.orchestrator = orchestrator;
        this.comfyHttpClient = comfyHttpClient;
        this.safePathValidator = safePathValidator;
    }

    /**
     * Primary endpoint for generation requests. Validates incoming DTO,
     * orchestrates dynamic workflow construction, and dispatches execution to ComfyUI.
     */
    @PostMapping(value = "/generate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<GenerationResponse> generateImage(@RequestBody GenerationRequest request) {
        return orchestrator.orchestrateAndRun(request.prompt())
                .map(GenerationResponse::processing);
    }

    /**
     * Retrieves execution status and history for a given prompt ID.
     */
    @GetMapping(value = "/status/{promptId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<JsonNode> getStatus(@PathVariable String promptId) {
        return comfyHttpClient.getHistory(promptId);
    }

    /**
     * Retrieves generated assets while strictly enforcing Path Traversal guards.
     */
    @GetMapping(value = "/image/{filename}", produces = MediaType.IMAGE_PNG_VALUE)
    public Mono<byte[]> getImage(@PathVariable String filename) {
        String safeFilename = safePathValidator.validateFilename(filename);
        return comfyHttpClient.downloadAsset(safeFilename);
    }
}
