package de.tki.comfyuicompanion.service;

import de.tki.comfyuicompanion.domain.Scene;

import java.io.File;

/**
 * Service interface for multimodal vision feedback loops (VLM) that inspect
 * generated Keyframe images from Pass 1 and refine kinetic motion prompts for Pass 2 (Image-to-Video).
 */
public interface IVideoVisionFeedbackService {

    /**
     * Inspects a generated keyframe image and refines the scene's kinetic prompt
     * to match the exact visual framing, subject placement, and lighting composition.
     *
     * @param scene         the scene to refine
     * @param keyframeImage the high-resolution start image generated in Pass 1
     * @return the refined kinetic motion prompt
     */
    String refinePromptWithVisualFeedback(Scene scene, File keyframeImage);
}
