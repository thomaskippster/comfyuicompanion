package de.tki.comfymodels.service;

/**
 * Interface defining strategies for video prompt optimization.
 * Injects camera trajectories, kinetic action verbs, and environmental physics
 * into prompts to prevent local video diffusion models (Wan 2.1, Hunyuan Video, LTX-Video)
 * from freezing or generating static images.
 */
public interface IVideoPromptOptimizer {

    /**
     * Builds the system prompt for LLMs (e.g. Gemma 3) instructing them
     * to deconstruct scripts into kinetically dynamic video scenes.
     */
    String buildStoryboardSystemPrompt();

    /**
     * Analyzes and optimizes a video prompt. Replaces static photography terminology
     * (e.g., stationary tripods, fading light) and injects camera motion and environmental kinetics
     * if absent.
     *
     * @param rawPrompt the original visual prompt
     * @return the kinetically enriched prompt
     */
    String optimizeVideoPrompt(String rawPrompt);

    /**
     * Transforms a simple sentence or concept into a fully articulated, motion-driven video prompt
     * for rule-based storyboard segmentation fallbacks.
     *
     * @param sentence the base narrative sentence
     * @return cinematic prompt with explicit camera and physical motion
     */
    String enrichFallbackPrompt(String sentence);

    /**
     * Checks if a prompt contains sufficient camera movement or physical motion directives.
     *
     * @param prompt the prompt to check
     * @return true if explicit motion keywords are present
     */
    boolean hasMotionDirectives(String prompt);
}
