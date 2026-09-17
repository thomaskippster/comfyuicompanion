package de.tki.comfyuicompanion.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests verifying value object encapsulation and backward compatibility for {@link Scene}.
 */
public class SceneValueObjectsTest {

    @Test
    void testRenderSettingsDefaultsAndMutation() {
        Scene scene = new Scene();
        assertEquals(1.0, scene.getCfgScale());
        assertEquals(30, scene.getSteps());
        assertEquals(127, scene.getMotionBucketId());

        scene.setRenderSettings(new Scene.RenderSettings(2.5, 45, 180));
        assertEquals(2.5, scene.getCfgScale());
        assertEquals(45, scene.getSteps());
        assertEquals(180, scene.getMotionBucketId());

        scene.setSteps(50);
        assertEquals(50, scene.getRenderSettings().steps());
        assertEquals(2.5, scene.getRenderSettings().cfgScale());
    }

    @Test
    void testTransitionSettingsDefaultsAndMutation() {
        Scene scene = new Scene();
        assertEquals("crossfade", scene.getTransitionType());
        assertEquals(1.0, scene.getTransitionDuration());
        assertFalse(scene.isEnhanceWithLtx());
        assertEquals(0.85, scene.getLtxStrength());

        scene.setTransitionSettings(new Scene.TransitionSettings("/path/clip.mp4", "wipe", 2.0, true, 0.9));
        assertEquals("/path/clip.mp4", scene.getSourceClipPath());
        assertEquals("wipe", scene.getTransitionType());
        assertEquals(2.0, scene.getTransitionDuration());
        assertTrue(scene.isEnhanceWithLtx());
        assertEquals(0.9, scene.getLtxStrength());

        scene.setTransitionType("dissolve");
        assertEquals("dissolve", scene.getTransitionSettings().transitionType());
    }

    @Test
    void testVisualEnhancementsDefaultsAndMutation() {
        Scene scene = new Scene();
        assertEquals(1.0, scene.getContrast());
        assertEquals(0.0, scene.getBrightness());

        scene.setVisualEnhancements(new Scene.VisualEnhancements(1.2, 0.15));
        assertEquals(1.2, scene.getContrast());
        assertEquals(0.15, scene.getBrightness());

        scene.setContrast(0.9);
        assertEquals(0.9, scene.getVisualEnhancements().contrast());
        assertEquals(0.15, scene.getVisualEnhancements().brightness());
    }

    @Test
    void testDimensionsDefaultsAndMutation() {
        Scene scene = new Scene();
        assertEquals(1920, scene.getWidth());
        assertEquals(1080, scene.getHeight());

        scene.setDimensions(new Scene.SceneDimensions(1280, 720));
        assertEquals(1280, scene.getWidth());
        assertEquals(720, scene.getHeight());

        scene.setWidth(3840);
        assertEquals(3840, scene.getDimensions().width());
        assertEquals(720, scene.getDimensions().height());
    }
}
