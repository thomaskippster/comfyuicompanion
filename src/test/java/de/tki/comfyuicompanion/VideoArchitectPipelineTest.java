package de.tki.comfyuicompanion;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import de.tki.comfyuicompanion.service.impl.ComfyPipelineService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.Gemma4Service;
import de.tki.comfyuicompanion.service.impl.LocalGemmaService;
import de.tki.comfyuicompanion.service.impl.ProcessTracker;
import de.tki.comfyuicompanion.service.impl.Video4jEditorService;
import de.tki.comfyuicompanion.service.impl.VideoEditorEngine;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.opencv.core.Mat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests verifying Video Architect ComfyUI pipeline workflows, Wan video node injections,
 * FFmpeg trimming, and video editing processes.
 */
public class VideoArchitectPipelineTest {

    @Test
    public void testVideoEditorEngineLoading() {
        ConfigService mockConfig = Mockito.mock(ConfigService.class);
        VideoEditorEngine engine = new VideoEditorEngine(mockConfig);
        assertNotNull(engine);
        VideoEditorEngine.Frame mockFrame = Mockito.mock(VideoEditorEngine.Frame.class);
        assertNotNull(mockFrame);
    }

    @Test
    public void testComfyPipelineServiceTemplateResolution() {
        ConfigService mockConfig = Mockito.mock(ConfigService.class);
        ComfyPipelineService service = new ComfyPipelineService(mockConfig);
        injectProcessTracker(service);
        assertNotNull(service);
    }

    @Test
    public void testPollHistoryForFilenameWithNonImageOutputs() throws Exception {
        ConfigService mockConfig = Mockito.mock(ConfigService.class);
        HttpClient mockClient = Mockito.mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = Mockito.mock(HttpResponse.class);

        JSONObject historyJson = new JSONObject();
        JSONObject jobJson = new JSONObject();
        JSONObject outputsJson = new JSONObject();
        JSONObject nodeOutput = new JSONObject();
        JSONArray gifsArray = new JSONArray();
        JSONObject gifItem = new JSONObject();
        gifItem.put("filename", "generated_animation.gif");
        gifsArray.put(gifItem);
        nodeOutput.put("gifs", gifsArray);
        outputsJson.put("12", nodeOutput);
        jobJson.put("outputs", outputsJson);
        historyJson.put("test-prompt-id", jobJson);

        Mockito.when(mockResponse.statusCode()).thenReturn(200);
        Mockito.when(mockResponse.body()).thenReturn(historyJson.toString());

        Mockito.when(mockClient.send(Mockito.any(HttpRequest.class), Mockito.any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        ComfyPipelineService service = new ComfyPipelineService(mockConfig, mockClient);
        injectProcessTracker(service);

        String filename = service.pollHistoryForFilename("http://localhost:8188", "test-prompt-id");
        assertEquals("generated_animation.gif", filename);
    }

    @Test
    public void testNullByteJsonParsingReproduction() {
        String invalidJson = "[{\"scene_id\":\"S1\",\"visual_prompt\":\"A futuristic neon city\0\",\"duration_seconds\":3,\"narration_text\":\"Welcome to our story.\"}]";

        assertThrows(org.json.JSONException.class, () -> new JSONArray(invalidJson));

        String cleanJson = invalidJson.replace("\u0000", "").replace("\0", "");
        JSONArray arr = new JSONArray(cleanJson);
        assertEquals(1, arr.length());
        assertEquals("S1", arr.getJSONObject(0).getString("scene_id"));
        assertEquals("A futuristic neon city", arr.getJSONObject(0).getString("visual_prompt"));
    }

    @Test
    public void testSanitizeWorkflowWhenComfyUIOffline() throws Exception {
        ConfigService mockConfig = Mockito.mock(ConfigService.class);
        HttpClient mockClient = Mockito.mock(HttpClient.class);

        Mockito.when(mockClient.send(Mockito.any(HttpRequest.class), Mockito.any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection refused"));

        ComfyPipelineService service = new ComfyPipelineService(mockConfig, mockClient);
        injectProcessTracker(service);

        JSONObject workflowJson = new JSONObject("{\n" +
                "  \"4\": {\n" +
                "    \"class_type\": \"CheckpointLoaderSimple\",\n" +
                "    \"inputs\": {\n" +
                "      \"ckpt_name\": \"v1-5-pruned-emaonly.safetensors\"\n" +
                "    }\n" +
                "  }\n" +
                "}");

        assertDoesNotThrow(() -> service.sanitizeWorkflow(workflowJson, "http://localhost:8188"));
        assertEquals("v1-5-pruned-emaonly.safetensors", workflowJson.getJSONObject("4").getJSONObject("inputs").getString("ckpt_name"));
    }

    @Test
    public void testInjectSpeakerAndAudioReflection() throws Exception {
        ComfyPipelineService service = new ComfyPipelineService(Mockito.mock(ConfigService.class));
        injectProcessTracker(service);

        JSONObject workflowJson = new JSONObject("{\n" +
                "  \"1\": {\n" +
                "    \"class_type\": \"LoadImage\",\n" +
                "    \"inputs\": {\n" +
                "      \"image\": \"default.png\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"2\": {\n" +
                "    \"class_type\": \"LoadAudio\",\n" +
                "    \"inputs\": {\n" +
                "      \"audio\": \"default.wav\"\n" +
                "    }\n" +
                "  }\n" +
                "}");

        Method injectSpeakerImage = ComfyPipelineService.class.getDeclaredMethod("injectSpeakerImage", JSONObject.class, String.class);
        injectSpeakerImage.setAccessible(true);
        injectSpeakerImage.invoke(service, workflowJson, "uploaded_speaker.png");

        Method injectAudio = ComfyPipelineService.class.getDeclaredMethod("injectAudio", JSONObject.class, String.class);
        injectAudio.setAccessible(true);
        injectAudio.invoke(service, workflowJson, "uploaded_narration.wav");

        JSONObject imageInputs = workflowJson.getJSONObject("1").getJSONObject("inputs");
        JSONObject audioInputs = workflowJson.getJSONObject("2").getJSONObject("inputs");

        assertEquals("uploaded_speaker.png", imageInputs.getString("image"));
        assertEquals("uploaded_narration.wav", audioInputs.getString("audio"));
    }

    @Test
    public void testCompleteWorkflowRocketStart() throws Exception {
        ConfigService mockConfig = Mockito.mock(ConfigService.class);

        File projectDir = new File("").getAbsoluteFile();
        File ffmpegExe = new File(projectDir, "tools/ffmpeg/ffmpeg.exe");
        assumeTrue(ffmpegExe.exists(), "ffmpeg not found at " + ffmpegExe.getAbsolutePath());

        Mockito.when(mockConfig.getFfmpegPath()).thenReturn(ffmpegExe.getAbsolutePath());
        Mockito.when(mockConfig.getComfyUIUrl()).thenReturn("http://localhost:8188");

        LocalGemmaService mockGemma = Mockito.mock(LocalGemmaService.class);
        Mockito.when(mockGemma.isModelDownloaded()).thenReturn(true);

        String rocketScriptJson = "[" +
                "{\"scene_id\":\"S1\",\"visual_prompt\":\"Majestic rocket on the launchpad\",\"duration_seconds\":5,\"narration_text\":\"The final countdown begins.\"}," +
                "{\"scene_id\":\"S2\",\"visual_prompt\":\"Rocket lifting off, fire trails\",\"duration_seconds\":5,\"narration_text\":\"Liftoff! We have a liftoff.\"}]";
        Mockito.when(mockGemma.generateCompletion(Mockito.anyString(), Mockito.anyString(), Mockito.anyFloat(), Mockito.anyInt()))
                .thenReturn(rocketScriptJson);

        Gemma4Service gemmaService = new Gemma4Service(mockGemma);
        String script = gemmaService.generateScript("rocket start");
        assertNotNull(script);

        JSONArray arr = new JSONArray(script);
        List<Scene> scenes = new ArrayList<>();
        File testOutputDir = new File(System.getProperty("java.io.tmpdir"), "rocket_test_output");
        if (!testOutputDir.exists()) {
            testOutputDir.mkdirs();
        }

        int frameCounter = 0;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            int duration = obj.getInt("duration_seconds");
            int start = frameCounter;
            int end = start + (duration * 30);
            frameCounter = end;

            Scene sc = new Scene(obj.getString("scene_id"), obj.getString("visual_prompt"), start, end, "", "");
            sc.setNarrationText(obj.getString("narration_text"));
            scenes.add(sc);
        }

        File sampleVideo = new File(System.getProperty("java.io.tmpdir"), "comfy_mock_output.mp4");
        if (sampleVideo.exists()) {
            sampleVideo.delete();
        }
        ProcessBuilder pb = new ProcessBuilder(
                ffmpegExe.getAbsolutePath(), "-y",
                "-f", "lavfi",
                "-i", "testsrc=duration=5:size=512x512:rate=30",
                "-pix_fmt", "yuv420p",
                "-vcodec", "libx264",
                sampleVideo.getAbsolutePath()
        );
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            while (r.readLine() != null) {}
        }
        p.waitFor();

        HttpClient mockClient = Mockito.mock(HttpClient.class);
        Mockito.when(mockClient.send(Mockito.any(HttpRequest.class), Mockito.any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    String uriStr = request.uri().toString();

                    @SuppressWarnings("unchecked")
                    HttpResponse<Object> response = Mockito.mock(HttpResponse.class);
                    Mockito.when(response.statusCode()).thenReturn(200);

                    if (uriStr.contains("/object_info")) {
                        String json = "{\"CheckpointLoaderSimple\": {\"input\": {\"required\": {\"ckpt_name\": [[\"v1-5-pruned-emaonly.safetensors\"]]}}}, \"VHS_VideoCombine\": {}}";
                        Mockito.when(response.body()).thenReturn(json);
                    } else if (uriStr.contains("/prompt")) {
                        String json = "{\"prompt_id\": \"test-prompt-id\"}";
                        Mockito.when(response.body()).thenReturn(json);
                    } else if (uriStr.contains("/history/")) {
                        String json = "{\"test-prompt-id\": {\"outputs\": {\"9\": {\"gifs\": [{\"filename\": \"mock_rocket.gif\"}]}}}}";
                        Mockito.when(response.body()).thenReturn(json);
                    } else if (uriStr.contains("/view")) {
                        FileInputStream fis = new FileInputStream(sampleVideo);
                        Mockito.when(response.body()).thenReturn(fis);
                    } else {
                        throw new IOException("Unexpected mock URL request: " + uriStr);
                    }
                    return response;
                });

        ComfyPipelineService comfyPipelineService = new ComfyPipelineService(mockConfig, mockClient);
        injectProcessTracker(comfyPipelineService);

        for (Scene sc : scenes) {
            File videoFile = comfyPipelineService.generateScene(sc).join();
            assertNotNull(videoFile);
            assertTrue(videoFile.exists());
            assertTrue(videoFile.length() > 1024);
            assertEquals(videoFile.getAbsolutePath(), sc.getVideoPath());
        }

        Video4jEditorService video4jEditorService = new Video4jEditorService(mockConfig);
        injectProcessTracker(video4jEditorService);
        video4jEditorService.init();

        File masterExport = video4jEditorService.executeMasterRender(scenes);
        assertNotNull(masterExport);
        assertTrue(masterExport.exists());
        assertTrue(masterExport.length() > 1024);

        masterExport.delete();
        for (Scene sc : scenes) {
            if (sc.getVideoPath() != null) {
                new File(sc.getVideoPath()).delete();
            }
        }
        if (sampleVideo.exists()) {
            sampleVideo.delete();
        }
        testOutputDir.delete();
    }

    @Test
    public void testComfyPipelineServiceAutoStartsComfyUI() throws Exception {
        ConfigService mockConfig = Mockito.mock(ConfigService.class);
        Mockito.when(mockConfig.getComfyUIUrl()).thenReturn("http://localhost:8188");

        File projectDir = new File("").getAbsoluteFile();
        File ffmpegExe = new File(projectDir, "tools/ffmpeg/ffmpeg.exe");
        assumeTrue(ffmpegExe.exists(), "ffmpeg not found at " + ffmpegExe.getAbsolutePath());
        Mockito.when(mockConfig.getFfmpegPath()).thenReturn(ffmpegExe.getAbsolutePath());

        HttpClient mockClient = Mockito.mock(HttpClient.class);

        @SuppressWarnings("unchecked")
        HttpResponse<String> mockVhsResponse = Mockito.mock(HttpResponse.class);
        Mockito.when(mockVhsResponse.statusCode()).thenReturn(200);
        Mockito.when(mockVhsResponse.body()).thenReturn("{\"VHS_VideoCombine\": {}}");

        @SuppressWarnings("unchecked")
        HttpResponse<String> mockPromptResponse = Mockito.mock(HttpResponse.class);
        Mockito.when(mockPromptResponse.statusCode()).thenReturn(200);
        Mockito.when(mockPromptResponse.body()).thenReturn("{\"prompt_id\": \"test-id\"}");

        @SuppressWarnings("unchecked")
        HttpResponse<String> mockHistoryResponse = Mockito.mock(HttpResponse.class);
        Mockito.when(mockHistoryResponse.statusCode()).thenReturn(200);
        Mockito.when(mockHistoryResponse.body()).thenReturn("{\"test-id\": {\"outputs\": {\"9\": {\"gifs\": [{\"filename\": \"out.gif\"}]}}}}");

        File sampleVideo = new File(System.getProperty("java.io.tmpdir"), "comfy_mock_autostart_output.mp4");
        if (!sampleVideo.exists()) {
            sampleVideo.createNewFile();
        }

        @SuppressWarnings("unchecked")
        HttpResponse<InputStream> mockViewResponse = Mockito.mock(HttpResponse.class);
        Mockito.when(mockViewResponse.statusCode()).thenReturn(200);
        Mockito.when(mockViewResponse.body()).thenReturn(new FileInputStream(sampleVideo));

        Mockito.when(mockClient.send(Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> {
                    HttpRequest req = invocation.getArgument(0);
                    String path = req.uri().getPath();
                    if (path.contains("object_info")) return mockVhsResponse;
                    if (path.contains("prompt")) return mockPromptResponse;
                    if (path.contains("history")) return mockHistoryResponse;
                    if (path.contains("view")) return mockViewResponse;
                    throw new IOException("Unexpected call: " + req.uri());
                });

        IComfyLifecycleService mockLifecycle = Mockito.mock(IComfyLifecycleService.class);
        Mockito.when(mockLifecycle.isHealthy()).thenReturn(false, true);

        ComfyPipelineService service = new ComfyPipelineService(mockConfig, mockClient);
        injectProcessTracker(service);

        Field field = ComfyPipelineService.class.getDeclaredField("lifecycleService");
        field.setAccessible(true);
        field.set(service, mockLifecycle);

        Scene scene = new Scene("S10", "A test visual prompt", 0, 30, "", "");
        scene.setNarrationText("narration");

        File output = service.generateScene(scene).join();
        assertNotNull(output);
        Mockito.verify(mockLifecycle, Mockito.times(1)).start();

        sampleVideo.delete();
    }

    @Test
    public void testVideoEditorEngineParametricFilter() {
        ConfigService mockConfig = Mockito.mock(ConfigService.class);
        VideoEditorEngine engine = new VideoEditorEngine(mockConfig);

        VideoEditorEngine.Frame mockFrame = Mockito.mock(VideoEditorEngine.Frame.class);
        Mat mockMat = Mockito.mock(Mat.class);
        Mockito.when(mockFrame.mat()).thenReturn(mockMat);
        Mockito.when(mockMat.empty()).thenReturn(false);

        engine.applyFilter(mockFrame, 1.25, 15.0);
        Mockito.verify(mockMat, Mockito.times(1)).convertTo(mockMat, -1, 1.25, 15.0);
    }

    @Test
    public void testVideo4jEditorServiceEnhancementAwtGracefulFallback() {
        ConfigService mockConfig = Mockito.mock(ConfigService.class);
        Video4jEditorService service = new Video4jEditorService(mockConfig);

        Scene scene = new Scene("S1", "Empty test", 0, 90, "", "");
        java.awt.image.BufferedImage result = service.applyBasicEnhancementAwt(scene, 1.0, 0.0);
        assertNull(result);
    }

    @Test
    public void testApplyTrimmingWithPunctuationAndQuotesInNarrationText() throws Exception {
        ConfigService mockConfig = Mockito.mock(ConfigService.class);
        File projectDir = new File("").getAbsoluteFile();
        File ffmpegExe = new File(projectDir, "tools/ffmpeg/ffmpeg.exe");
        assumeTrue(ffmpegExe.exists(), "ffmpeg not found at " + ffmpegExe.getAbsolutePath());
        Mockito.when(mockConfig.getFfmpegPath()).thenReturn(ffmpegExe.getAbsolutePath());

        File testVideo = new File(System.getProperty("java.io.tmpdir"), "test_trim_input.mp4");
        if (testVideo.exists()) {
            testVideo.delete();
        }
        ProcessBuilder pb = new ProcessBuilder(
                ffmpegExe.getAbsolutePath(), "-y",
                "-f", "lavfi",
                "-i", "color=c=black:s=320x240:d=2:r=30",
                "-pix_fmt", "yuv420p",
                "-c:v", "libx264",
                testVideo.getAbsolutePath()
        );
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            while (r.readLine() != null) {}
        }
        p.waitFor();
        assertTrue(testVideo.exists() && testVideo.length() > 500);

        Video4jEditorService service = new Video4jEditorService(mockConfig);
        injectProcessTracker(service);

        Scene scene = new Scene("S1", "Cyborg prompt", 0, 60, "", "");
        scene.setVideoPath(testVideo.getAbsolutePath());
        scene.setNarrationText("A warrior's sword, 8k resolution, cinematic: ultra-detailed");

        File trimmed = null;
        try {
            trimmed = service.applyTrimming(scene);
            assertNotNull(trimmed);
            assertTrue(trimmed.exists());
            assertTrue(trimmed.length() > 500);
        } finally {
            if (trimmed != null && trimmed.exists()) {
                trimmed.delete();
            }
            if (testVideo.exists()) {
                testVideo.delete();
            }
        }
    }

    @Test
    public void testWanWorkflowPureTextToVideoUsesEmptyImageAndResolution() throws Exception {
        ConfigService mockConfig = Mockito.mock(ConfigService.class);
        ComfyPipelineService service = new ComfyPipelineService(mockConfig);

        Scene scene = new Scene("S1", "Cyberpunk skyline in fog", 0, 72, "", "");
        scene.setWidth(1280);
        scene.setHeight(720);

        Method method = ComfyPipelineService.class.getDeclaredMethod(
                "generateWanWorkflowJson", String.class, Scene.class, String.class, long.class, String.class);
        method.setAccessible(true);

        JSONObject workflow = (JSONObject) method.invoke(service, "http://127.0.0.1:8188", scene, null, 12345L, "test_prefix");

        assertNotNull(workflow);
        assertTrue(workflow.has("20"), "EmptyImage node 20 should exist for pure Text-to-Video");
        assertFalse(workflow.has("97"), "LoadImage node 97 must not be present when no speaker image is provided");

        JSONObject emptyImageInputs = workflow.getJSONObject("20").getJSONObject("inputs");
        assertEquals(1280, emptyImageInputs.getInt("width"));
        assertEquals(720, emptyImageInputs.getInt("height"));

        JSONObject wanInputs = workflow.getJSONObject("129:98").getJSONObject("inputs");
        assertEquals(1280, wanInputs.getInt("width"));
        assertEquals(720, wanInputs.getInt("height"));
    }

    @Test
    public void testWanWorkflowWithSpeakerImageUsesLoadImage() throws Exception {
        ConfigService mockConfig = Mockito.mock(ConfigService.class);
        ComfyPipelineService service = new ComfyPipelineService(mockConfig);

        Scene scene = new Scene("S1", "Cyberpunk skyline with speaker", 0, 72, "", "");
        scene.setWidth(1920);
        scene.setHeight(1080);

        Method method = ComfyPipelineService.class.getDeclaredMethod(
                "generateWanWorkflowJson", String.class, Scene.class, String.class, long.class, String.class);
        method.setAccessible(true);

        JSONObject workflow = (JSONObject) method.invoke(service, "http://127.0.0.1:8188", scene, "explicit_speaker.png", 12345L, "test_prefix");

        assertNotNull(workflow);
        assertTrue(workflow.has("97"), "LoadImage node 97 must be present when speaker image is supplied");
        assertFalse(workflow.has("20"), "EmptyImage node 20 must not be present when speaker image is supplied");

        JSONObject loadImageInputs = workflow.getJSONObject("97").getJSONObject("inputs");
        assertEquals("explicit_speaker.png", loadImageInputs.getString("image"));

        JSONObject wanInputs = workflow.getJSONObject("129:98").getJSONObject("inputs");
        assertEquals(1920, wanInputs.getInt("width"));
        assertEquals(1072, wanInputs.getInt("height"), "Height should be aligned to 16px multiple (1072 for 1080p)");
    }

    private void injectProcessTracker(Object target) {
        try {
            Field f1 = target.getClass().getDeclaredField("processTracker");
            f1.setAccessible(true);
            f1.set(target, new ProcessTracker());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
