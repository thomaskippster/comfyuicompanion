package de.tki.comfymodels.service.impl;

import de.tki.comfymodels.domain.ComfyRegistryWorkflow;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class ComfyRegistryClientParseTest {

    private List<ComfyRegistryWorkflow> parse(String json) throws Exception {
        ComfyRegistryClient client = new ComfyRegistryClient();
        Method m = ComfyRegistryClient.class.getDeclaredMethod("parseWorkflowsJson", String.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ComfyRegistryWorkflow> result = (List<ComfyRegistryWorkflow>) m.invoke(client, json);
        return result;
    }

    @Test
    public void parsesThumbnailArrayEntry_andMarksVideo() throws Exception {
        String json = "[" +
            "{\"title\":\"Video\",\"templates\":[" +
            "  {" +
            "    \"name\":\"api_seedance2_0_r2v\"," +
            "    \"title\":\"Seedance 2.0: Reference to Video\"," +
            "    \"description\":\"x\"," +
            "    \"mediaType\":\"image\"," +
            "    \"mediaSubtype\":\"webp\"," +
            "    \"date\":\"2025-01-01\"," +
            "    \"username\":\"alice\"," +
            "    \"usage\":1.0," +
            "    \"models\":[]," +
            "    \"thumbnail\":[\"output/Seedance2.0_r2v_00006_.mp4\"]" +
            "  }" +
            "]}]";
        List<ComfyRegistryWorkflow> list = parse(json);
        assertEquals(1, list.size());
        ComfyRegistryWorkflow wf = list.get(0);
        assertEquals("Seedance 2.0: Reference to Video", wf.getTitle());
        assertEquals("https://raw.githubusercontent.com/Comfy-Org/workflow_templates/main/templates/output/Seedance2.0_r2v_00006_.mp4",
                wf.getThumbnailUrl());
        assertTrue(wf.isVideoPreview(), "Entry with .mp4 thumbnail must be marked as video preview");
    }

    @Test
    public void parsesImageEntry_andFallsBackToLegacyUrl() throws Exception {
        String json = "[" +
            "{\"title\":\"Image\",\"templates\":[" +
            "  {" +
            "    \"name\":\"api_flux_dev\"," +
            "    \"title\":\"Flux Dev\"," +
            "    \"description\":\"x\"," +
            "    \"mediaType\":\"image\"," +
            "    \"mediaSubtype\":\"webp\"," +
            "    \"date\":\"2025-01-01\"," +
            "    \"username\":\"bob\"," +
            "    \"usage\":0.0," +
            "    \"models\":[]" +
            "  }" +
            "]}]";
        List<ComfyRegistryWorkflow> list = parse(json);
        assertEquals(1, list.size());
        ComfyRegistryWorkflow wf = list.get(0);
        assertEquals("https://raw.githubusercontent.com/Comfy-Org/workflow_templates/main/templates/api_flux_dev-1.webp",
                wf.getThumbnailUrl());
        assertFalse(wf.isVideoPreview(), "Legacy image entry must not be reported as a video preview");
    }

    @Test
    public void parsesImageEntry_withImageThumbnail() throws Exception {
        String json = "[" +
            "{\"title\":\"Image\",\"templates\":[" +
            "  {" +
            "    \"name\":\"api_sdxl\"," +
            "    \"title\":\"SDXL\"," +
            "    \"description\":\"x\"," +
            "    \"mediaType\":\"image\"," +
            "    \"mediaSubtype\":\"webp\"," +
            "    \"date\":\"2025-01-01\"," +
            "    \"username\":\"carol\"," +
            "    \"usage\":0.0," +
            "    \"models\":[]," +
            "    \"thumbnail\":[\"thumbnail/api_sdxl.png\"]" +
            "  }" +
            "]}]";
        List<ComfyRegistryWorkflow> list = parse(json);
        assertEquals(1, list.size());
        ComfyRegistryWorkflow wf = list.get(0);
        assertEquals("https://raw.githubusercontent.com/Comfy-Org/workflow_templates/main/templates/thumbnail/api_sdxl.png",
                wf.getThumbnailUrl());
        assertFalse(wf.isVideoPreview(), "PNG thumbnail must not be reported as a video preview");
    }

    @Test
    public void parsesWebmThumbnail_asVideo() throws Exception {
        String json = "[" +
            "{\"title\":\"Video\",\"templates\":[" +
            "  {" +
            "    \"name\":\"api_webm_template\"," +
            "    \"title\":\"WebM\"," +
            "    \"description\":\"x\"," +
            "    \"mediaType\":\"image\"," +
            "    \"mediaSubtype\":\"webp\"," +
            "    \"date\":\"2025-01-01\"," +
            "    \"username\":\"dave\"," +
            "    \"usage\":0.0," +
            "    \"models\":[]," +
            "    \"thumbnail\":[\"output/foo.webm\"]" +
            "  }" +
            "]}]";
        List<ComfyRegistryWorkflow> list = parse(json);
        assertEquals(1, list.size());
        ComfyRegistryWorkflow wf = list.get(0);
        assertNotNull(wf.getThumbnailUrl());
        assertTrue(wf.getThumbnailUrl().endsWith("/output/foo.webm"));
        assertTrue(wf.isVideoPreview(), ".webm thumbnail must be reported as a video preview");
    }

    @Test
    public void parsesCategory_andAuthor() throws Exception {
        String json = "[" +
            "{\"title\":\"My Category\",\"templates\":[" +
            "  {" +
            "    \"name\":\"api_x\"," +
            "    \"title\":\"X\"," +
            "    \"description\":\"x\"," +
            "    \"mediaType\":\"image\"," +
            "    \"mediaSubtype\":\"webp\"," +
            "    \"date\":\"2025-01-01\"," +
            "    \"username\":\"eve\"," +
            "    \"usage\":2.0," +
            "    \"models\":[\"foo.safetensors\"]" +
            "  }" +
            "]}]";
        List<ComfyRegistryWorkflow> list = parse(json);
        assertEquals(1, list.size());
        ComfyRegistryWorkflow wf = list.get(0);
        assertEquals("My Category", wf.getCategory());
        assertEquals("eve", wf.getAuthor());
        assertEquals(2.0, wf.getPopularScore(), 0.0001);
    }
}