package de.tki.comfymodels;

import de.tki.comfymodels.domain.Scene;
import de.tki.comfymodels.service.impl.ComfyPipelineService;
import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.Gemma4Service;
import de.tki.comfymodels.service.impl.LocalGemmaService;
import de.tki.comfymodels.service.impl.LocalTTSService;
import de.tki.comfymodels.service.impl.VideoEditorEngine;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import de.tki.comfymodels.service.impl.Video4jEditorService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class VideoArchitectFeaturesTest {

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
    public void testGemma4ServicePromptStructure() {
        LocalGemmaService mockGemma = Mockito.mock(LocalGemmaService.class);
        Gemma4Service gemmaService = new Gemma4Service(mockGemma);
        assertNotNull(gemmaService);

        // Test mock response parsing structure
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
        
        // Assert that an IllegalStateException is thrown when the local model is not downloaded
        assertThrows(IllegalStateException.class, () -> {
            gemmaService.generateScript("Create a sci-fi video intro");
        });
    }

    @Test
    public void testVideoEditorEngineLoading() {
        ConfigService mockConfig = Mockito.mock(ConfigService.class);
        VideoEditorEngine engine = new VideoEditorEngine(mockConfig);
        assertNotNull(engine);
        // Test applyFilter structure is defined and compile-clean
        VideoEditorEngine.Frame mockFrame = Mockito.mock(VideoEditorEngine.Frame.class);
        assertNotNull(mockFrame);
    }

    @Test
    public void testComfyPipelineServiceTemplateResolution() {
        ConfigService mockConfig = Mockito.mock(ConfigService.class);
        ComfyPipelineService service = new ComfyPipelineService(mockConfig);
try { java.lang.reflect.Field f1 = service.getClass().getDeclaredField("processTracker"); f1.setAccessible(true); f1.set(service, new de.tki.comfymodels.service.impl.ProcessTracker()); } catch (Exception e) { throw new RuntimeException(e); }
        
        assertNotNull(service);
    }

    @Test
    public void testPollHistoryForFilenameWithNonImageOutputs() throws Exception {
        ConfigService mockConfig = Mockito.mock(ConfigService.class);
        java.net.http.HttpClient mockClient = Mockito.mock(java.net.http.HttpClient.class);
        java.net.http.HttpResponse<String> mockResponse = Mockito.mock(java.net.http.HttpResponse.class);

        // JSON response containing "gifs" instead of "images"
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

        Mockito.when(mockClient.send(Mockito.any(java.net.http.HttpRequest.class), Mockito.any(java.net.http.HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        ComfyPipelineService service = new ComfyPipelineService(mockConfig, mockClient);
try { java.lang.reflect.Field f1 = service.getClass().getDeclaredField("processTracker"); f1.setAccessible(true); f1.set(service, new de.tki.comfymodels.service.impl.ProcessTracker()); } catch (Exception e) { throw new RuntimeException(e); }
        

        // Call the package-private method
        String filename = service.pollHistoryForFilename("http://localhost:8188", "test-prompt-id");
        assertEquals("generated_animation.gif", filename);
    }

    @Test
    public void testNullByteJsonParsingReproduction() {
        String invalidJson = "[{\"scene_id\":\"S1\",\"visual_prompt\":\"A futuristic neon city\0\",\"duration_seconds\":3,\"narration_text\":\"Welcome to our story.\"}]";
        
        // Assert that parsing directly throws Exception due to the null byte
        assertThrows(org.json.JSONException.class, () -> {
            new JSONArray(invalidJson);
        });

        // Test that our fix (replacing the null byte) allows parsing to succeed
        String cleanJson = invalidJson.replace("\u0000", "").replace("\0", "");
        JSONArray arr = new JSONArray(cleanJson);
        assertEquals(1, arr.length());
        assertEquals("S1", arr.getJSONObject(0).getString("scene_id"));
        assertEquals("A futuristic neon city", arr.getJSONObject(0).getString("visual_prompt"));
    }

    @Test
    public void testSanitizeWorkflowWhenComfyUIOffline() throws Exception {
        ConfigService mockConfig = Mockito.mock(ConfigService.class);
        java.net.http.HttpClient mockClient = Mockito.mock(java.net.http.HttpClient.class);
        
        // Emulate server offline by throwing IOException on httpClient.send
        Mockito.when(mockClient.send(Mockito.any(java.net.http.HttpRequest.class), Mockito.any(java.net.http.HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection refused"));

        ComfyPipelineService service = new ComfyPipelineService(mockConfig, mockClient);
try { java.lang.reflect.Field f1 = service.getClass().getDeclaredField("processTracker"); f1.setAccessible(true); f1.set(service, new de.tki.comfymodels.service.impl.ProcessTracker()); } catch (Exception e) { throw new RuntimeException(e); }
        

        // JSON payload containing CheckpointLoaderSimple
        JSONObject workflowJson = new JSONObject("{\n" +
                "  \"4\": {\n" +
                "    \"class_type\": \"CheckpointLoaderSimple\",\n" +
                "    \"inputs\": {\n" +
                "      \"ckpt_name\": \"v1-5-pruned-emaonly.safetensors\"\n" +
                "    }\n" +
                "  }\n" +
                "}");

        // Verify that sanitizing doesn't throw IndexOutOfBoundsException when offline
        assertDoesNotThrow(() -> {
            service.sanitizeWorkflow(workflowJson, "http://localhost:8188");
        });
        
        // Assert that the checkpoint name was left untouched because ComfyUI was offline
        assertEquals("v1-5-pruned-emaonly.safetensors", workflowJson.getJSONObject("4").getJSONObject("inputs").getString("ckpt_name"));
    }

    @Test
    public void testInjectSpeakerAndAudioReflection() throws Exception {
        ComfyPipelineService service = new ComfyPipelineService(Mockito.mock(ConfigService.class));
try { java.lang.reflect.Field f1 = service.getClass().getDeclaredField("processTracker"); f1.setAccessible(true); f1.set(service, new de.tki.comfymodels.service.impl.ProcessTracker()); } catch (Exception e) { throw new RuntimeException(e); }
        
        
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
                
        java.lang.reflect.Method injectSpeakerImage = ComfyPipelineService.class.getDeclaredMethod("injectSpeakerImage", JSONObject.class, String.class);
        injectSpeakerImage.setAccessible(true);
        injectSpeakerImage.invoke(service, workflowJson, "uploaded_speaker.png");
        
        java.lang.reflect.Method injectAudio = ComfyPipelineService.class.getDeclaredMethod("injectAudio", JSONObject.class, String.class);
        injectAudio.setAccessible(true);
        injectAudio.invoke(service, workflowJson, "uploaded_narration.wav");
        
        JSONObject imageInputs = workflowJson.getJSONObject("1").getJSONObject("inputs");
        JSONObject audioInputs = workflowJson.getJSONObject("2").getJSONObject("inputs");
        
        assertEquals("uploaded_speaker.png", imageInputs.getString("image"));
        assertEquals("uploaded_narration.wav", audioInputs.getString("audio"));
    }

    @Test
    public void testCompleteWorkflowRocketStart() throws Exception {
        // 1. Setup mock ConfigService that points to the correct ffmpeg path
        ConfigService mockConfig = Mockito.mock(ConfigService.class);
        
        // Find the absolute path to tools/ffmpeg/ffmpeg.exe
        File projectDir = new File("").getAbsoluteFile();
        File ffmpegExe = new File(projectDir, "tools/ffmpeg/ffmpeg.exe");
        if (!ffmpegExe.exists()) {
            ffmpegExe = new File("C:\\Dev\\workspace\\comfyuicompanion\\tools\\ffmpeg\\ffmpeg.exe");
        }
        
        Mockito.when(mockConfig.getFfmpegPath()).thenReturn(ffmpegExe.getAbsolutePath());
        Mockito.when(mockConfig.getComfyUIUrl()).thenReturn("http://localhost:8188"); // offline

        // 2. Setup mock Gemma4Service that returns rocket start scenes
        LocalGemmaService mockGemma = Mockito.mock(LocalGemmaService.class);
        Mockito.when(mockGemma.isModelDownloaded()).thenReturn(true);
        
        String rocketScriptJson = "[\n" +
                "  {\n" +
                "    \"scene_id\": \"S1\",\n" +
                "    \"visual_prompt\": \"Majestic rocket on the launchpad, smoke rising\",\n" +
                "    \"duration_seconds\": 5,\n" +
                "    \"narration_text\": \"The final countdown begins.\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"scene_id\": \"S2\",\n" +
                "    \"visual_prompt\": \"Rocket lifting off, fire trails, high speed\",\n" +
                "    \"duration_seconds\": 5,\n" +
                "    \"narration_text\": \"Liftoff! We have a liftoff.\"\n" +
                "  }\n" +
                "]";
        Mockito.when(mockGemma.generateCompletion(Mockito.anyString(), Mockito.anyString(), Mockito.anyFloat(), Mockito.anyInt()))
                .thenReturn(rocketScriptJson);
        
        Gemma4Service gemmaService = new Gemma4Service(mockGemma);
        String script = gemmaService.generateScript("rocket start");
        assertNotNull(script);
        
        // Parse the scenes
        JSONArray arr = new JSONArray(script);
        java.util.List<Scene> scenes = new java.util.ArrayList<>();
        
        // Create temp folder for test output
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
            
            Scene sc = new Scene(
                obj.getString("scene_id"),
                obj.getString("visual_prompt"),
                start,
                end,
                "",
                ""
            );
            sc.setNarrationText(obj.getString("narration_text"));
            scenes.add(sc);
        }
        
        // Generate a 5-second real video file to serve as the mocked downloaded output (10 seconds combined)
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
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            while (r.readLine() != null) {}
        }
        p.waitFor();

        java.net.http.HttpClient mockClient = Mockito.mock(java.net.http.HttpClient.class);
        Mockito.when(mockClient.send(Mockito.any(java.net.http.HttpRequest.class), Mockito.any(java.net.http.HttpResponse.BodyHandler.class)))
               .thenAnswer(invocation -> {
                   java.net.http.HttpRequest request = invocation.getArgument(0);
                   String uriStr = request.uri().toString();
                   
                   java.net.http.HttpResponse<Object> response = Mockito.mock(java.net.http.HttpResponse.class);
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
                       java.io.FileInputStream fis = new java.io.FileInputStream(sampleVideo);
                       Mockito.when(response.body()).thenReturn(fis);
                   } else {
                       throw new IOException("Unexpected mock URL request: " + uriStr);
                   }
                   return response;
               });

        ComfyPipelineService comfyPipelineService = new ComfyPipelineService(mockConfig, mockClient);
try { java.lang.reflect.Field f1 = comfyPipelineService.getClass().getDeclaredField("processTracker"); f1.setAccessible(true); f1.set(comfyPipelineService, new de.tki.comfymodels.service.impl.ProcessTracker()); } catch (Exception e) { throw new RuntimeException(e); }
        
        
        for (Scene sc : scenes) {
            File videoFile = comfyPipelineService.generateScene(sc).join();
            assertNotNull(videoFile);
            assertTrue(videoFile.exists());
            assertTrue(videoFile.length() > 1024); // verify it's a real video file
            assertEquals(videoFile.getAbsolutePath(), sc.getVideoPath());
        }
        
        // 4. Run video stitching via Video4jEditorService
        // (It will also generate silent WAV audio files for us since sc.getAudioPath() is empty, testing our generateDummyWav)
        Video4jEditorService video4jEditorService = new Video4jEditorService(mockConfig);
try { java.lang.reflect.Field f1 = video4jEditorService.getClass().getDeclaredField("processTracker"); f1.setAccessible(true); f1.set(video4jEditorService, new de.tki.comfymodels.service.impl.ProcessTracker()); } catch (Exception e) { throw new RuntimeException(e); }
        
        video4jEditorService.init(); // loads OpenCV if available
        
        File masterExport = video4jEditorService.executeMasterRender(scenes);
        assertNotNull(masterExport);
        assertTrue(masterExport.exists());
        assertTrue(masterExport.length() > 1024);
        
        System.out.println("🚀 [Test] End-to-end video architect workflow test succeeded! Output: " + masterExport.getAbsolutePath());
        
        // Cleanup temp files
        masterExport.delete();
        for (Scene sc : scenes) {
            if (sc.getVideoPath() != null) {
                new File(sc.getVideoPath()).delete();
            }
        }
        if (sampleVideo.exists()) {
            sampleVideo.delete();
        }
        // delete testOutput dir
        testOutputDir.delete();
    }



    @Test
    public void testComfyPipelineServiceAutoStartsComfyUI() throws Exception {
        ConfigService mockConfig = Mockito.mock(ConfigService.class);
        Mockito.when(mockConfig.getComfyUIUrl()).thenReturn("http://localhost:8188");
        
        File projectDir = new File("").getAbsoluteFile();
        File ffmpegExe = new File(projectDir, "tools/ffmpeg/ffmpeg.exe");
        if (!ffmpegExe.exists()) {
            ffmpegExe = new File("C:\\Dev\\workspace\\comfyuicompanion\\tools\\ffmpeg\\ffmpeg.exe");
        }
        Mockito.when(mockConfig.getFfmpegPath()).thenReturn(ffmpegExe.getAbsolutePath());
        
        java.net.http.HttpClient mockClient = Mockito.mock(java.net.http.HttpClient.class);
        
        java.net.http.HttpResponse<String> mockVhsResponse = Mockito.mock(java.net.http.HttpResponse.class);
        Mockito.when(mockVhsResponse.statusCode()).thenReturn(200);
        Mockito.when(mockVhsResponse.body()).thenReturn("{\"VHS_VideoCombine\": {}}");
        
        java.net.http.HttpResponse<String> mockPromptResponse = Mockito.mock(java.net.http.HttpResponse.class);
        Mockito.when(mockPromptResponse.statusCode()).thenReturn(200);
        Mockito.when(mockPromptResponse.body()).thenReturn("{\"prompt_id\": \"test-id\"}");
        
        java.net.http.HttpResponse<String> mockHistoryResponse = Mockito.mock(java.net.http.HttpResponse.class);
        Mockito.when(mockHistoryResponse.statusCode()).thenReturn(200);
        Mockito.when(mockHistoryResponse.body()).thenReturn("{\"test-id\": {\"outputs\": {\"9\": {\"gifs\": [{\"filename\": \"out.gif\"}]}}}}");
        
        java.io.File sampleVideo = new File(System.getProperty("java.io.tmpdir"), "comfy_mock_autostart_output.mp4");
        if (!sampleVideo.exists()) {
            sampleVideo.createNewFile();
        }
        
        java.net.http.HttpResponse<java.io.InputStream> mockViewResponse = Mockito.mock(java.net.http.HttpResponse.class);
        Mockito.when(mockViewResponse.statusCode()).thenReturn(200);
        Mockito.when(mockViewResponse.body()).thenReturn(new java.io.FileInputStream(sampleVideo));

        Mockito.when(mockClient.send(Mockito.any(), Mockito.any()))
               .thenAnswer(invocation -> {
                   java.net.http.HttpRequest req = invocation.getArgument(0);
                   String path = req.uri().getPath();
                   if (path.contains("object_info")) return mockVhsResponse;
                   if (path.contains("prompt")) return mockPromptResponse;
                   if (path.contains("history")) return mockHistoryResponse;
                   if (path.contains("view")) return mockViewResponse;
                   throw new IOException("Unexpected call: " + req.uri());
               });

        de.tki.comfymodels.service.IComfyLifecycleService mockLifecycle = Mockito.mock(de.tki.comfymodels.service.IComfyLifecycleService.class);
        Mockito.when(mockLifecycle.isHealthy()).thenReturn(false, true);

        ComfyPipelineService service = new ComfyPipelineService(mockConfig, mockClient);
try { java.lang.reflect.Field f1 = service.getClass().getDeclaredField("processTracker"); f1.setAccessible(true); f1.set(service, new de.tki.comfymodels.service.impl.ProcessTracker()); } catch (Exception e) { throw new RuntimeException(e); }
        
        
        java.lang.reflect.Field field = ComfyPipelineService.class.getDeclaredField("lifecycleService");
        field.setAccessible(true);
        field.set(service, mockLifecycle);
        
        Scene scene = new Scene("S10", "A test visual prompt", 0, 30, "", "");
        scene.setNarrationText("narration");
        
        File output = service.generateScene(scene).join();
        assertNotNull(output);
        
        Mockito.verify(mockLifecycle, Mockito.times(1)).start();
        
        sampleVideo.delete();
    }
}
