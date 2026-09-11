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
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*", "http://[::1]:*"})
public class ComfyCompanionController {

    private final AgenticWorkflowOrchestrator orchestrator;
    private final ComfyHttpClient comfyHttpClient;
    private final SafePathValidator safePathValidator;

    private final com.thomaskippster.comfyuicompanion.service.inspector.TriggerWordService triggerWordService;
    private final com.thomaskippster.comfyuicompanion.service.provisioning.CustomNodeResolverService customNodeResolverService;

    public ComfyCompanionController(AgenticWorkflowOrchestrator orchestrator,
                                    ComfyHttpClient comfyHttpClient,
                                    SafePathValidator safePathValidator,
                                    com.thomaskippster.comfyuicompanion.service.inspector.TriggerWordService triggerWordService,
                                    com.thomaskippster.comfyuicompanion.service.provisioning.CustomNodeResolverService customNodeResolverService) {
        this.orchestrator = orchestrator;
        this.comfyHttpClient = comfyHttpClient;
        this.safePathValidator = safePathValidator;
        this.triggerWordService = triggerWordService;
        this.customNodeResolverService = customNodeResolverService;
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

    /**
     * Extracts trigger words, activation phrases, and architecture details for a local model.
     */
    @GetMapping(value = "/models/{filename}/metadata", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<org.springframework.http.ResponseEntity<com.thomaskippster.comfyuicompanion.service.inspector.TriggerWordService.ModelTriggerInfo>> getModelMetadata(
            @PathVariable String filename) {
        String safeName = safePathValidator.validateFilename(filename);
        return Mono.fromCallable(() -> triggerWordService.findModelTriggerInfo(safeName))
                .map(opt -> opt.map(org.springframework.http.ResponseEntity::ok)
                        .orElseGet(() -> org.springframework.http.ResponseEntity.notFound().build()));
    }

    /**
     * Analyzes a workflow to detect missing Custom Nodes and returns recommended community packages.
     */
    @PostMapping(value = "/workflow/nodes/analyze", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<com.thomaskippster.comfyuicompanion.service.provisioning.CustomNodeResolverService.NodeResolutionReport> analyzeWorkflowNodes(
            @RequestBody String workflowJson) {
        return customNodeResolverService.analyzeWorkflowNodes(workflowJson);
    }
}
