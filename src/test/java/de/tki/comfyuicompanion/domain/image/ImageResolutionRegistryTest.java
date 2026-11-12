package de.tki.comfyuicompanion.domain.image;

import de.tki.comfyuicompanion.ui.PromptLabParametersPanel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ImageResolutionRegistryTest {

    private ImageResolutionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = ImageResolutionRegistry.getInstance();
    }

    @Test
    void testPresetsAreConfigured() {
        List<ImageResolutionPreset> presets = registry.getAllPresets();
        assertNotNull(presets);
        assertFalse(presets.isEmpty());

        assertTrue(presets.contains(ImageResolutionPreset.SQUARE_1024));
        assertTrue(presets.contains(ImageResolutionPreset.LANDSCAPE_16_9));
        assertTrue(presets.contains(ImageResolutionPreset.PORTRAIT_9_16));
        assertTrue(presets.contains(ImageResolutionPreset.CUSTOM));
    }

    @Test
    void testFindMatching() {
        assertEquals(ImageResolutionPreset.SQUARE_1024, registry.findMatching(1024, 1024));
        assertEquals(ImageResolutionPreset.LANDSCAPE_16_9, registry.findMatching(1344, 768));
        assertEquals(ImageResolutionPreset.PORTRAIT_9_16, registry.findMatching(768, 1344));
        assertEquals(ImageResolutionPreset.SD15_SQUARE, registry.findMatching(512, 512));
        assertEquals(ImageResolutionPreset.FHD_16_9, registry.findMatching(1920, 1080));
        assertEquals(ImageResolutionPreset.CUSTOM, registry.findMatching(1234, 567));
    }

    @Test
    void testFindById() {
        assertTrue(registry.findById("16_9_1344").isPresent());
        assertEquals(1344, registry.findById("16_9_1344").get().width());
        assertEquals(768, registry.findById("16_9_1344").get().height());

        assertTrue(registry.findById("non_existent").isEmpty());
    }

    @Test
    void testUiParametersPanelTwoWayBinding() {
        PromptLabParametersPanel panel = new PromptLabParametersPanel();

        // 1. Selecting preset overrides spinners
        panel.getResolutionPresetCombo().setSelectedItem(ImageResolutionPreset.LANDSCAPE_16_9);
        assertEquals(1344, ((Number) panel.getWidthSpinner().getValue()).intValue());
        assertEquals(768, ((Number) panel.getHeightSpinner().getValue()).intValue());

        // 2. Modifying spinners updates preset
        panel.getWidthSpinner().setValue(1024);
        panel.getHeightSpinner().setValue(1024);
        assertEquals(ImageResolutionPreset.SQUARE_1024, panel.getResolutionPresetCombo().getSelectedItem());

        // 3. Custom spinners set preset to CUSTOM
        panel.getWidthSpinner().setValue(800);
        panel.getHeightSpinner().setValue(600);
        assertEquals(ImageResolutionPreset.CUSTOM, panel.getResolutionPresetCombo().getSelectedItem());

        // 4. Selecting another preset overrides back
        panel.getResolutionPresetCombo().setSelectedItem(ImageResolutionPreset.PORTRAIT_9_16);
        assertEquals(768, ((Number) panel.getWidthSpinner().getValue()).intValue());
        assertEquals(1344, ((Number) panel.getHeightSpinner().getValue()).intValue());
    }
}
