package com.thomaskippster.comfyuicompanion.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Immutable Data Transfer Object representing an image generation request.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GenerationRequest(String prompt) {

    public GenerationRequest {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt darf nicht leer sein.");
        }
        prompt = prompt.trim();
    }
}
