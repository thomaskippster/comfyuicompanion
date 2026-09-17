package de.tki.comfyuicompanion.service.pipeline;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests verifying output artifact extraction and error parsing in {@link ComfyJobPollingService}.
 */
public class ComfyJobPollingServiceTest {

    private ComfyJobPollingService service;

    @BeforeEach
    void setUp() {
        service = new ComfyJobPollingService(null);
    }

    @Test
    void testExtractFirstOutput_withVideoArray() {
        JSONObject outputs = new JSONObject();
        JSONObject nodeOut = new JSONObject();
        JSONArray videos = new JSONArray();
        JSONObject videoObj = new JSONObject()
                .put("filename", "scene_001.mp4")
                .put("subfolder", "render")
                .put("type", "output");
        videos.put(videoObj);
        nodeOut.put("videos", videos);
        outputs.put("9", nodeOut);

        ComfyJobPollingService.ComfyOutputRef ref = service.extractFirstOutput(outputs);

        assertNotNull(ref);
        assertEquals("scene_001.mp4", ref.filename());
        assertEquals("render", ref.subfolder());
        assertEquals("output", ref.type());
    }

    @Test
    void testExtractFirstOutput_withImagesArray() {
        JSONObject outputs = new JSONObject();
        JSONObject nodeOut = new JSONObject();
        JSONArray images = new JSONArray();
        JSONObject imgObj = new JSONObject()
                .put("filename", "image_001.png");
        images.put(imgObj);
        nodeOut.put("images", images);
        outputs.put("12", nodeOut);

        ComfyJobPollingService.ComfyOutputRef ref = service.extractFirstOutput(outputs);

        assertNotNull(ref);
        assertEquals("image_001.png", ref.filename());
        assertEquals("", ref.subfolder());
        assertEquals("output", ref.type());
    }

    @Test
    void testExtractFirstOutput_emptyOutputs() {
        JSONObject outputs = new JSONObject();
        ComfyJobPollingService.ComfyOutputRef ref = service.extractFirstOutput(outputs);
        assertNull(ref);
    }

    @Test
    void testExtractHistoryError_withMessages() {
        JSONObject status = new JSONObject();
        JSONArray messages = new JSONArray();
        JSONArray err1 = new JSONArray().put("execution_error").put("Out of memory on GPU");
        messages.put(err1);
        status.put("messages", messages);

        String error = service.extractHistoryError(status);
        assertEquals("Out of memory on GPU", error);
    }

    @Test
    void testExtractHistoryError_fallbackStatusStr() {
        JSONObject status = new JSONObject().put("status_str", "Execution interrupted");
        String error = service.extractHistoryError(status);
        assertEquals("Execution interrupted", error);
    }

    @Test
    void testResolveOutputFile_nullCandidate() {
        File resolved = service.resolveOutputFile("non_existent_dir", null, "S1");
        assertNull(resolved);
    }
}
