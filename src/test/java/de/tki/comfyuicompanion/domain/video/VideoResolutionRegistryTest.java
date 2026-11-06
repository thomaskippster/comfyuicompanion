package de.tki.comfyuicompanion.domain.video;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class VideoResolutionRegistryTest {

    private VideoResolutionRegistry registry;

    @BeforeEach
    public void setUp() {
        registry = new VideoResolutionRegistry();
    }

    @Test
    public void testPresetsContainStandardAndVerticalFormats() {
        List<VideoResolutionPreset> presets = registry.getAllPresets();
        assertNotNull(presets);
        assertTrue(presets.size() >= 10);

        // Check TikTok 9:16 HD preset
        Optional<VideoResolutionPreset> tiktokHd = registry.findById("tiktok_9_16_hd");
        assertTrue(tiktokHd.isPresent());
        assertEquals(720, tiktokHd.get().width());
        assertEquals(1280, tiktokHd.get().height());
        assertTrue(tiktokHd.get().isVertical());
        assertEquals("9:16", tiktokHd.get().aspectRatio());

        // Check 720p 16:9 Landscape preset
        Optional<VideoResolutionPreset> landscapeHd = registry.findById("720p_16_9");
        assertTrue(landscapeHd.isPresent());
        assertEquals(1280, landscapeHd.get().width());
        assertEquals(720, landscapeHd.get().height());
        assertFalse(landscapeHd.get().isVertical());
    }

    @Test
    public void testFindMatchingDimensions() {
        VideoResolutionPreset match1 = registry.findMatching(1280, 720);
        assertEquals("720p_16_9", match1.id());

        VideoResolutionPreset match2 = registry.findMatching(720, 1280);
        assertEquals("tiktok_9_16_hd", match2.id());

        VideoResolutionPreset customMatch = registry.findMatching(999, 888);
        assertEquals("custom", customMatch.id());
    }

    @Test
    public void testFindIndex() {
        int idxHd = registry.findIndex(1280, 720);
        assertEquals(0, idxHd);

        int idxTiktok = registry.findIndex(720, 1280);
        assertEquals(1, idxTiktok);

        int idxUnknown = registry.findIndex(123, 456);
        assertEquals(registry.getAllPresets().size() - 1, idxUnknown);
    }

    @Test
    public void testDisplayNames() {
        String[] displayNames = registry.getDisplayNames();
        assertNotNull(displayNames);
        assertEquals(registry.getAllPresets().size(), displayNames.length);
        assertTrue(displayNames[0].contains("1280x720"));
        assertTrue(displayNames[1].contains("720x1280"));
    }
}
