package de.tki.comfymodels.service.impl;

import de.tki.comfymodels.service.IVideoPromptOptimizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VideoPromptOptimizerTest {

    private IVideoPromptOptimizer optimizer;

    @BeforeEach
    void setUp() {
        optimizer = new VideoPromptOptimizer();
    }

    @Test
    void testBuildStoryboardSystemPromptContainsCriticalDirectives() {
        String systemPrompt = optimizer.buildStoryboardSystemPrompt();
        assertNotNull(systemPrompt);
        assertTrue(systemPrompt.contains("MANDATORY CAMERA TRAJECTORY"), "System prompt must mandate camera movement");
        assertTrue(systemPrompt.contains("ENVIRONMENTAL KINETICS & PHYSICS"), "System prompt must mandate environmental physics");
        assertTrue(systemPrompt.contains("SUBJECT ACTION & MOVEMENT"), "System prompt must mandate subject kinetics");
        assertTrue(systemPrompt.contains("AVOID STATIC PHOTO TRIGGERS"), "System prompt must ban static photo terms");
    }

    @Test
    void testOptimizeStaticPromptSanitizesAndEnriches() {
        // User's exact scenario: static landscape with 85mm lens, dust motes, and fading light
        String staticPrompt = "Wide establishing shot, 85mm lens. Sand dunes of the Mojave Desert, launchpad and station. Dust motes dance in the air, fading orange light.";

        String optimized = optimizer.optimizeVideoPrompt(staticPrompt);
        assertNotNull(optimized);

        // Verify that problematic terms causing frozen video or fade-in artifacts are sanitized
        assertFalse(optimized.contains("wide establishing shot"), "Wide establishing shot should be replaced");
        assertFalse(optimized.contains("fading orange light"), "Fading light should be replaced to avoid temporal fade-in");
        assertFalse(optimized.contains("dust motes dance in the air"), "Sub-pixel dust motes should be replaced with macroscopic wind/dust");

        // Verify motion cues
        assertTrue(optimizer.hasMotionDirectives(optimized), "Optimized prompt must contain motion directives");
    }

    @Test
    void testOptimizeStaticObjectPromptInjectsCameraMotion() {
        String staticObject = "A rusty vintage car in an abandoned field";

        String optimized = optimizer.optimizeVideoPrompt(staticObject);

        // Prepend camera motion
        assertTrue(optimized.startsWith("Cinematic camera glides forward through ") ||
                optimized.startsWith("Camera slowly pushes forward over "), "Should inject active camera motion");
        assertTrue(optimizer.hasMotionDirectives(optimized));
    }

    @Test
    void testPreservesAlreadyDynamicPrompt() {
        String dynamicPrompt = "Camera slowly pushes forward over vast undulating sand dunes. Strong winds whip heavy dust across the ground, dark clouds moving rapidly.";

        String optimized = optimizer.optimizeVideoPrompt(dynamicPrompt);

        // Should not duplicate camera motion if already present
        assertTrue(optimized.startsWith("Camera slowly pushes forward"));
        assertTrue(optimizer.hasMotionDirectives(optimized));
    }

    @Test
    void testEnrichFallbackPrompt() {
        String sentence = "The explorer discovers an ancient temple entrance.";

        String enriched = optimizer.enrichFallbackPrompt(sentence);

        assertNotNull(enriched);
        assertTrue(enriched.contains("The explorer discovers an ancient temple entrance"));
        assertTrue(enriched.toLowerCase().contains("camera"));
        assertTrue(optimizer.hasMotionDirectives(enriched));
    }

    @Test
    void testHasMotionDirectives() {
        assertFalse(optimizer.hasMotionDirectives(null));
        assertFalse(optimizer.hasMotionDirectives(""));
        assertFalse(optimizer.hasMotionDirectives("A wooden table in a room"));
        assertTrue(optimizer.hasMotionDirectives("Camera slowly pushes forward"));
        assertTrue(optimizer.hasMotionDirectives("Strong winds blow sand across the dunes"));
        assertTrue(optimizer.hasMotionDirectives("A person running down the street"));
    }
}
