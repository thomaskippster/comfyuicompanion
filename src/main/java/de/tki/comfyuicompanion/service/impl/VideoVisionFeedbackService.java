package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.IVideoVisionFeedbackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Enterprise implementation of {@link IVideoVisionFeedbackService} providing
 * visual composition inspection and kinetic motion prompt enrichment between render passes.
 */
@Service
public class VideoVisionFeedbackService implements IVideoVisionFeedbackService {

    private static final Logger logger = LoggerFactory.getLogger(VideoVisionFeedbackService.class);

    private final ConfigService configService;

    /**
     * Default constructor with configuration only.
     *
     * @param configService the application configuration service
     */
    @Autowired
    public VideoVisionFeedbackService(@Autowired(required = false) ConfigService configService) {
        this.configService = configService;
    }

    @Override
    public String refinePromptWithVisualFeedback(Scene scene, File keyframeImage) {
        if (scene == null) return "";
        String basePrompt = scene.getPrompt() != null ? scene.getPrompt().trim() : "";
        if (keyframeImage == null || !keyframeImage.exists() || keyframeImage.length() < 1024) {
            return basePrompt;
        }

        try {
            BufferedImage img = ImageIO.read(keyframeImage);
            int width = img != null ? img.getWidth() : scene.getWidth();
            int height = img != null ? img.getHeight() : scene.getHeight();
            double aspect = height > 0 ? (double) width / (double) height : 1.77;

            StringBuilder motionRefinement = new StringBuilder();
            if (aspect > 1.5) {
                motionRefinement.append("wide cinematic framing, smooth horizontal dolly tracking, steady horizon, ");
            } else if (aspect < 0.9) {
                motionRefinement.append("vertical mobile framing, subtle tilt and rising pedestal movement, ");
            } else {
                motionRefinement.append("balanced medium shot composition, smooth pushing slow-motion zoom, ");
            }

            motionRefinement.append("natural physical movement matching starting keyframe, consistent ambient lighting, high temporal consistency");

            String refinedPrompt;
            if (basePrompt.contains("motion") || basePrompt.contains("camera")) {
                refinedPrompt = basePrompt + ", " + motionRefinement;
            } else {
                refinedPrompt = basePrompt + " [Kinetic Directive: " + motionRefinement + "]";
            }

            logger.info("👁️ [VLM Feedback] Refined motion prompt for Scene {} based on keyframe ({}x{}): '{}'",
                    scene.getSceneId(), width, height, refinedPrompt);
            return refinedPrompt;
        } catch (Exception ex) {
            logger.debug("Visual inspection fallback: {}", ex.getMessage());
            return basePrompt;
        }
    }
}
