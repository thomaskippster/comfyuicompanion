package de.tki.comfyuicompanion;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import de.tki.comfyuicompanion.service.impl.ComfyPipelineService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.Gemma4Service;
import de.tki.comfyuicompanion.service.impl.LocalGemmaService;
import de.tki.comfyuicompanion.service.impl.LocalTTSService;
import de.tki.comfyuicompanion.service.impl.Video4jEditorService;
import de.tki.comfyuicompanion.ui.VideoArchitectTab;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying Video Architect scene domain models, storyboard parsing,
 * LLM script generation, and user interface initialization.
 */
public class VideoArchitectSceneTest {

    @Test
    public void testSceneDomainModel() {
        Scene scene = new Scene("S1", "Futuristic neon city", 0, 90, "audio.wav", "video.mp4");
        assertEquals("S1", scene.getSceneId());
        assertEquals("Futuristic neon city", scene.getPrompt());
        assertEquals(0, scene.getStartFrame());
        assertEquals(90, scene.getEndFrame());
        assertEquals("audio.wav", scene.getAudioPath());
        assertEquals("video.mp4", scene.getVideoPath());

        scene.setSceneId("S2");
        scene.setPrompt("New prompt");
        scene.setStartFrame(10);
        scene.setEndFrame(50);
        scene.setAudioPath("new_audio.wav");
        scene.setVideoPath("new_video.mp4");

        assertEquals("S2", scene.getSceneId());
        assertEquals("New prompt", scene.getPrompt());
        assertEquals(10, scene.getStartFrame());
        assertEquals(50, scene.getEndFrame());
        assertEquals("new_audio.wav", scene.getAudioPath());
        assertEquals("new_video.mp4", scene.getVideoPath());
    }

    @Test
    public void testSceneContrastAndBrightness() {
        Scene scene = new Scene("S1", "Cyberpunk rain", 0, 90, "", "");
        assertEquals(1.0, scene.getContrast(), 0.001);
        assertEquals(0.0, scene.getBrightness(), 0.001);

        scene.setContrast(1.35);
        scene.setBrightness(25.0);
        assertEquals(1.35, scene.getContrast(), 0.001);
        assertEquals(25.0, scene.getBrightness(), 0.001);
    }

    @Test
    public void testSceneDimensionsDefaultAndCustom() {
        Scene scene = new Scene();
        assertEquals(1920, scene.getWidth(), "Default scene width should be 1920 (FHD)");
        assertEquals(1080, scene.getHeight(), "Default scene height should be 1080 (FHD)");

        scene.setWidth(1280);
        scene.setHeight(720);
        assertEquals(1280, scene.getWidth());
        assertEquals(720, scene.getHeight());
    }

    @Test
    public void testGemma4ServicePromptStructure() {
        LocalGemmaService mockGemma = Mockito.mock(LocalGemmaService.class);
        Gemma4Service gemmaService = new Gemma4Service(mockGemma);
        assertNotNull(gemmaService);

        String mockResponse = "[\n" +
                "  {\n" +
                "    \"scene_id\": \"S1\",\n" +
                "    \"visual_prompt\": \"Cinematic close-up of a tiger in neon light\",\n" +
                "    \"duration_seconds\": 4,\n" +
                "    \"narration_text\": \"The wild tiger roams the cyberpunk alley.\"\n" +
                "  }\n" +
                "]";

        JSONArray arr = new JSONArray(mockResponse);
        assertEquals(1, arr.length());
        JSONObject obj = arr.getJSONObject(0);
        assertEquals("S1", obj.getString("scene_id"));
        assertEquals("Cinematic close-up of a tiger in neon light", obj.getString("visual_prompt"));
        assertEquals(4, obj.getInt("duration_seconds"));
        assertEquals("The wild tiger roams the cyberpunk alley.", obj.getString("narration_text"));
    }

    @Test
    public void testLocalTTSServicePathValidation() {
        ConfigService mockConfig = Mockito.mock(ConfigService.class);
        Mockito.when(mockConfig.getPiperPath()).thenReturn("nonexistent_piper.exe");
        Mockito.when(mockConfig.getPiperModelPath()).thenReturn("nonexistent_model.onnx");

        LocalTTSService localTTSService = new LocalTTSService(mockConfig);

        assertThrows(IOException.class, () -> {
            localTTSService.generateSpeech("Hello", "output/test.wav");
        });
    }

    @Test
    public void testGemma4ServiceThrowsExceptionWhenModelNotDownloaded() {
        LocalGemmaService mockLocalGemma = Mockito.mock(LocalGemmaService.class);
        Mockito.when(mockLocalGemma.isModelDownloaded()).thenReturn(false);

        Gemma4Service gemmaService = new Gemma4Service(mockLocalGemma);
        assertFalse(gemmaService.isGemmaAvailable());

        assertThrows(IllegalStateException.class, () -> {
            gemmaService.generateScript("Create a sci-fi video intro");
        });
    }

    @Test
    public void testGemma4ServiceSuccessfulScriptGeneration() throws Exception {
        LocalGemmaService mockLocalGemma = Mockito.mock(LocalGemmaService.class);
        Mockito.when(mockLocalGemma.isModelDownloaded()).thenReturn(true);
        Mockito.when(mockLocalGemma.generateCompletion(Mockito.anyString(), Mockito.anyString(), Mockito.anyFloat(), Mockito.anyInt()))
                .thenReturn("[{\"scene_id\":\"S1\",\"visual_prompt\":\"Neon car\",\"duration_seconds\":5,\"narration_text\":\"Speeding through the night\"}]");

        Gemma4Service gemmaService = new Gemma4Service(mockLocalGemma);
        assertTrue(gemmaService.isGemmaAvailable());

        String script = gemmaService.generateScript("A neon race");
        assertNotNull(script);
        assertTrue(script.contains("Neon car"));
    }

    @Test
    public void testStoryboardParsingWithMessyLlmResponse() throws Exception {
        String messyResponse = "Here is the storyboard for your video:\n" +
                "```json\n" +
                "[\n" +
                "  {\n" +
                "    \"id\": \"S1\",\n" +
                "    \"description\": \"A sleek sports car racing along a coastal cliff during sunset\",\n" +
                "    \"duration\": 4,\n" +
                "    \"voiceover\": \"Speed is not just a number, it is an emotion.\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"scene_id\": \"S2\",\n" +
                "    \"visual_prompt\": \"Close up of the speedometer hitting 200 km/h\",\n" +
                "    \"duration_seconds\": 3,\n" +
                "    \"narration_text\": \"Pushed to the absolute limit.\"\n" +
                "  }\n" +
                "]\n" +
                "```\n" +
                "I hope this matches your creative vision!";

        ConfigService mockConfig = Mockito.mock(ConfigService.class);
        ComfyPipelineService mockPipeline = Mockito.mock(ComfyPipelineService.class);
        Video4jEditorService mockEditor = Mockito.mock(Video4jEditorService.class);
        Gemma4Service mockGemma = Mockito.mock(Gemma4Service.class);
        IComfyLifecycleService mockLifecycle = Mockito.mock(IComfyLifecycleService.class);

        VideoArchitectTab tab = new VideoArchitectTab(
                mockConfig, mockPipeline, mockEditor, mockGemma, mockLifecycle
        );

        Method parseMethod = VideoArchitectTab.class.getDeclaredMethod("parseStoryboardJson", String.class, String.class);
        parseMethod.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Scene> scenes = (List<Scene>) parseMethod.invoke(tab, messyResponse, "A sports car racing along a cliff.");

        assertNotNull(scenes);
        assertEquals(2, scenes.size());
        assertEquals("S1", scenes.get(0).getSceneId());
        assertEquals("A sleek sports car racing along a coastal cliff during sunset", scenes.get(0).getPrompt());
        assertEquals("Speed is not just a number, it is an emotion.", scenes.get(0).getNarrationText());
        assertEquals(96, scenes.get(0).getEndFrame());

        assertEquals("S2", scenes.get(1).getSceneId());
        assertEquals("Close up of the speedometer hitting 200 km/h", scenes.get(1).getPrompt());
        assertEquals("Pushed to the absolute limit.", scenes.get(1).getNarrationText());
        assertEquals(72, scenes.get(1).getEndFrame());
    }

    @Test
    public void testStoryboardFallbackWhenLlmFails() throws Exception {
        String script = "A spaceship approaches a distant glowing exoplanet. The crew prepares for atmospheric entry. The thrusters fire as clouds envelop the cockpit.";

        ConfigService mockConfig = Mockito.mock(ConfigService.class);
        ComfyPipelineService mockPipeline = Mockito.mock(ComfyPipelineService.class);
        Video4jEditorService mockEditor = Mockito.mock(Video4jEditorService.class);
        Gemma4Service mockGemma = Mockito.mock(Gemma4Service.class);
        IComfyLifecycleService mockLifecycle = Mockito.mock(IComfyLifecycleService.class);

        VideoArchitectTab tab = new VideoArchitectTab(
                mockConfig, mockPipeline, mockEditor, mockGemma, mockLifecycle
        );

        Method splitFallback = VideoArchitectTab.class.getDeclaredMethod("splitScriptFallback", String.class);
        splitFallback.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Scene> scenes = (List<Scene>) splitFallback.invoke(tab, script);

        assertNotNull(scenes);
        assertEquals(3, scenes.size());
        assertEquals("S1", scenes.get(0).getSceneId());
        assertTrue(scenes.get(0).getPrompt().contains("A spaceship approaches a distant glowing exoplanet"));
        assertEquals("A spaceship approaches a distant glowing exoplanet.", scenes.get(0).getNarrationText());
        assertTrue(scenes.get(0).getEndFrame() > 0);

        assertEquals("S2", scenes.get(1).getSceneId());
        assertTrue(scenes.get(1).getPrompt().contains("The crew prepares for atmospheric entry"));

        assertEquals("S3", scenes.get(2).getSceneId());
        assertTrue(scenes.get(2).getPrompt().contains("The thrusters fire as clouds envelop the cockpit"));
    }

    @Test
    public void testVideoArchitectTabIsSwingJPanel() {
        ConfigService mockConfig = Mockito.mock(ConfigService.class);
        ComfyPipelineService mockPipeline = Mockito.mock(ComfyPipelineService.class);
        Video4jEditorService mockEditor = Mockito.mock(Video4jEditorService.class);
        Gemma4Service mockGemma = Mockito.mock(Gemma4Service.class);
        IComfyLifecycleService mockLifecycle = Mockito.mock(IComfyLifecycleService.class);

        VideoArchitectTab tab = new VideoArchitectTab(
                mockConfig, mockPipeline, mockEditor, mockGemma, mockLifecycle
        );

        assertTrue(tab instanceof JPanel);
        assertEquals(1, tab.getComponentCount());
        assertTrue(tab.getComponent(0) instanceof JSplitPane);
    }

    @Test
    public void testVideoArchitectTabInitialTheming() {
        ConfigService mockConfig = Mockito.mock(ConfigService.class);
        ComfyPipelineService mockPipeline = Mockito.mock(ComfyPipelineService.class);
        Video4jEditorService mockEditor = Mockito.mock(Video4jEditorService.class);
        Gemma4Service mockGemma = Mockito.mock(Gemma4Service.class);
        IComfyLifecycleService mockLifecycle = Mockito.mock(IComfyLifecycleService.class);

        VideoArchitectTab tab = new VideoArchitectTab(
                mockConfig, mockPipeline, mockEditor, mockGemma, mockLifecycle
        );

        assertNotNull(tab);
        tab.updateTheme(true);
        tab.updateTheme(false);
        tab.addNotify();
        assertNotNull(tab.getComponent(0));
    }

    @Test
    public void testVideoArchitectTabInitialStateHasNoDefaultSpeakerImageAndHDSpinners() throws Exception {
        ConfigService mockConfig = Mockito.mock(ConfigService.class);
        Mockito.when(mockConfig.getSpeakerImagePath()).thenReturn("old_persisted_image.png");
        ComfyPipelineService mockPipeline = Mockito.mock(ComfyPipelineService.class);
        Video4jEditorService mockEditor = Mockito.mock(Video4jEditorService.class);
        Gemma4Service mockGemma = Mockito.mock(Gemma4Service.class);
        IComfyLifecycleService mockLifecycle = Mockito.mock(IComfyLifecycleService.class);

        VideoArchitectTab tab = new VideoArchitectTab(
                mockConfig, mockPipeline, mockEditor, mockGemma, mockLifecycle
        );

        Field speakerField = VideoArchitectTab.class.getDeclaredField("speakerImageField");
        speakerField.setAccessible(true);
        JTextField textField = (JTextField) speakerField.get(tab);
        assertTrue(textField.getText().isEmpty(), "Start image field must be empty by default (no stale default image)");

        Field widthSpinnerField = VideoArchitectTab.class.getDeclaredField("videoWidthSpinner");
        widthSpinnerField.setAccessible(true);
        JSpinner widthSpinner = (JSpinner) widthSpinnerField.get(tab);
        assertEquals(832, widthSpinner.getValue(), "Default width should be 832 (safe 480p VRAM preset)");

        Field heightSpinnerField = VideoArchitectTab.class.getDeclaredField("videoHeightSpinner");
        heightSpinnerField.setAccessible(true);
        JSpinner heightSpinner = (JSpinner) heightSpinnerField.get(tab);
        assertEquals(480, heightSpinner.getValue(), "Default height should be 480 (safe 480p VRAM preset)");
    }
}
