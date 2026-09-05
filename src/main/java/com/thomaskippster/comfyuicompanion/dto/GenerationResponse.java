package com.thomaskippster.comfyuicompanion.dto;

/**
 * Immutable Data Transfer Object representing the response of a generation request.
 */
public record GenerationResponse(
        String status,
        String promptId,
        String message
) {
    public static GenerationResponse processing(String promptId) {
        return new GenerationResponse(
                "processing",
                promptId,
                "Agentischer Workflow wurde erfolgreich orchestriert und übergeben."
        );
    }
}
