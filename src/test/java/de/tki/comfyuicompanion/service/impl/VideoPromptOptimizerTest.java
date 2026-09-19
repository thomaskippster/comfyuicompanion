package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.service.IVideoPromptOptimizer;
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

    @Test
    void testBuildStoryboardSystemPromptWithContextDirectives() {
        String prompt = optimizer.buildStoryboardSystemPrompt("Wan 2.1 (T2V / I2V 14B)", 832, 480, 5);
        assertNotNull(prompt);
        assertTrue(prompt.contains("Wan 2.1 (T2V / I2V 14B)"), "Must include model target");
        assertTrue(prompt.contains("832x480"), "Must include resolution");
        assertTrue(prompt.contains("exactly 5 sequential"), "Must enforce scene count");
        assertTrue(prompt.contains("DIRECTOR'S 5-BEAT DRAMATIC ARC"), "Must provide dramatic arc structure");
        assertTrue(prompt.contains("DUAL-ANCHOR FORMULA"), "Must provide style consistency anchor");
        assertTrue(prompt.contains("MANDATORY TEMPORAL & LIGHTING CONTINUITY"), "Must enforce temporal and lighting continuity");
        assertTrue(prompt.contains("HIGH-OCTANE MACROSCOPIC MOTION"), "Must enforce macroscopic physics");
        assertTrue(prompt.contains("STRICT NEGATIVE DIRECTIVES"), "Must forbid countdowns and abstract adjectives");
    }

    @Test
    void testHarmonizeSceneLightingPreventsShifts() {
        de.tki.comfyuicompanion.domain.Scene s1 = new de.tki.comfyuicompanion.domain.Scene();
        s1.setSceneId("S1");
        s1.setPrompt("Camera slowly pushes forward over the rocket launchpad, dusty golden hour sunset backlight.");

        de.tki.comfyuicompanion.domain.Scene s2 = new de.tki.comfyuicompanion.domain.Scene();
        s2.setSceneId("S2");
        s2.setPrompt("Low-angle tracking shot of the crowd cheering wildly, pitch-black night silhouette.");

        java.util.List<de.tki.comfyuicompanion.domain.Scene> scenes = java.util.List.of(s1, s2);
        optimizer.harmonizeSceneLighting(scenes);

        // s2's conflicting night prompt should be stripped and harmonized with s1's golden hour lighting
        assertFalse(s2.getPrompt().contains("pitch-black night silhouette"), "Conflicting night trigger must be removed");
        assertTrue(s2.getPrompt().toLowerCase().contains("golden hour"), "Must inherit Scene 1's golden hour lighting anchor");
    }

    @Test
    void testDefaultTransitionIsNone() {
        de.tki.comfyuicompanion.domain.Scene scene = new de.tki.comfyuicompanion.domain.Scene();
        assertEquals("none", scene.getTransitionType(), "Default transition must be 'none' (hard cut)");
        assertEquals(0.0, scene.getTransitionDuration(), 0.001, "Default transition duration must be 0.0s");
    }
}
