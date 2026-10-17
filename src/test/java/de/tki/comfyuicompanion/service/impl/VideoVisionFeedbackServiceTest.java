package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.domain.Scene;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link VideoVisionFeedbackService} verifying multimodal visual feedback loops
 * and kinetic prompt enrichment based on image aspect ratios and compositions.
 */
class VideoVisionFeedbackServiceTest {

    private VideoVisionFeedbackService feedbackService;

    @BeforeEach
    void setUp() {
        feedbackService = new VideoVisionFeedbackService(null);
    }

    @Test
    @DisplayName("Should return unchanged prompt when keyframe image is null or missing")
    void testRefinePromptWithNullOrMissingImage() {
        Scene scene = new Scene("S1", "A futuristic train arriving at dusk", 0, 80, null, null);
        String prompt = feedbackService.refinePromptWithVisualFeedback(scene, null);
        assertEquals("A futuristic train arriving at dusk", prompt);

        File missing = new File("nonexistent_keyframe.png");
        String prompt2 = feedbackService.refinePromptWithVisualFeedback(scene, missing);
        assertEquals("A futuristic train arriving at dusk", prompt2);
    }

    @Test
    @DisplayName("Should enrich motion prompt with wide cinematic directives for 16:9 widescreen keyframe")
    void testRefinePromptWithWidescreenKeyframe(@TempDir Path tempDir) throws IOException {
        File imageFile = new File(tempDir.toFile(), "test_wide.png");
        BufferedImage img = new BufferedImage(832, 480, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setColor(Color.BLUE);
        g2d.fillRect(0, 0, 832, 480);
        g2d.dispose();
        ImageIO.write(img, "png", imageFile);

        Scene scene = new Scene("S1", "Rocket standing on desert launchpad", 0, 80, null, null);
        String refined = feedbackService.refinePromptWithVisualFeedback(scene, imageFile);

        assertNotNull(refined);
        assertTrue(refined.contains("Rocket standing on desert launchpad"));
        assertTrue(refined.contains("wide cinematic framing") || refined.contains("Kinetic Directive"));
    }

    @Test
    @DisplayName("Should enrich motion prompt with vertical framing for portrait keyframe")
    void testRefinePromptWithVerticalKeyframe(@TempDir Path tempDir) throws IOException {
        File imageFile = new File(tempDir.toFile(), "test_portrait.png");
        BufferedImage img = new BufferedImage(480, 832, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setColor(Color.RED);
        g2d.fillRect(0, 0, 480, 832);
        g2d.dispose();
        ImageIO.write(img, "png", imageFile);

        Scene scene = new Scene("S2", "Close up portrait of a cyberpunk hacker", 0, 80, null, null);
        String refined = feedbackService.refinePromptWithVisualFeedback(scene, imageFile);

        assertNotNull(refined);
        assertTrue(refined.contains("Close up portrait of a cyberpunk hacker"));
        assertTrue(refined.contains("vertical mobile framing"));
    }
}
