package de.tki.comfyuicompanion.service;

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
    default String buildStoryboardSystemPrompt() {
        return buildStoryboardSystemPrompt(null, 0, 0, 5);
    }

    /**
     * Builds a contextual system prompt for LLMs tailored to a target video model,
     * aspect ratio, and required scene count.
     *
     * @param targetModel target video diffusion architecture (e.g. "Wan 2.1", "LTX-Video")
     * @param width       target video width
     * @param height      target video height
     * @param sceneCount  desired number of sequential scenes
     * @return the fully configured system prompt
     */
    String buildStoryboardSystemPrompt(String targetModel, int width, int height, int sceneCount);

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
