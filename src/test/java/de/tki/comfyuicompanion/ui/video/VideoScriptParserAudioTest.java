package de.tki.comfyuicompanion.ui.video;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.impl.VideoPromptOptimizer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VideoScriptParserAudioTest {

    @Test
    void testParseStoryboardJsonWithExplicitSfxPrompt() {
        String json = """
                {
                  "scenes": [
                    {
                      "scene_id": "S1",
                      "duration_seconds": 4,
                      "visual_prompt": "Extreme close-up of a weathered astronaut helmet visor reflecting a bright rocket ignition",
                      "narration": "The countdown begins, anticipation hangs heavy.",
                      "sfx_prompt": "Deafening rocket thruster ignition roar, explosive low-end bass rumble"
                    }
                  ]
                }
                """;

        VideoScriptParser parser = new VideoScriptParser();
        VideoPromptOptimizer optimizer = new VideoPromptOptimizer();
        List<Scene> scenes = parser.parseStoryboardJson(json, "Rocket concept", 832, 480, optimizer);

        assertNotNull(scenes);
        assertEquals(1, scenes.size());
        Scene s1 = scenes.get(0);
        assertEquals("S1", s1.getSceneId());
        assertEquals("The countdown begins, anticipation hangs heavy.", s1.getNarrationText());
        assertEquals("Deafening rocket thruster ignition roar, explosive low-end bass rumble", s1.getSfxPrompt());
    }

    @Test
    void testParseStoryboardJsonInfersSfxWhenMissing() {
        String json = """
                [
                  {
                    "scene_id": "1",
                    "duration_seconds": 4,
                    "visual_prompt": "Extreme close-up of a weathered astronaut helmet visor reflecting a bright rocket ignition",
                    "narration_text": "The countdown begins, anticipation hangs heavy."
                  }
                ]
                """;

        VideoScriptParser parser = new VideoScriptParser();
        VideoPromptOptimizer optimizer = new VideoPromptOptimizer();
        List<Scene> scenes = parser.parseStoryboardJson(json, "Rocket launch", 832, 480, optimizer);

        assertNotNull(scenes);
        assertEquals(1, scenes.size());
        Scene s1 = scenes.get(0);
        assertNotNull(s1.getSfxPrompt());
        assertTrue(s1.getSfxPrompt().toLowerCase().contains("rocket") || s1.getSfxPrompt().toLowerCase().contains("ignition"));
    }
}
