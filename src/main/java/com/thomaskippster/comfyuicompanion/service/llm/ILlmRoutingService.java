package com.thomaskippster.comfyuicompanion.service.llm;

import com.thomaskippster.comfyuicompanion.domain.llm.GenerationIntent;

/**
 * Enterprise interface for analyzing and routing user generation requests
 * into structured execution intents.
 */
public interface ILlmRoutingService {

    /**
     * Analyzes raw user input and resolves it into a structured GenerationIntent.
     *
     * @param userInput The raw prompt / wish from the user
     * @return The structured intent containing architecture, prompt and parameters
     */
    GenerationIntent parseUserRequest(String userInput);
}
