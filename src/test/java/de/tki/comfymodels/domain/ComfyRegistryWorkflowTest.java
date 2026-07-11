package de.tki.comfymodels.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ComfyRegistryWorkflowTest {

    @Test
    public void isVideoPreview_true_whenMediaTypeIsVideo() {
        ComfyRegistryWorkflow wf = new ComfyRegistryWorkflow();
        wf.setMediaType("video");
        wf.setMediaSubtype("mp4");
        wf.setThumbnailUrl("https://example.com/whatever-1.webp");
        assertTrue(wf.isVideoPreview(), "mediaType=video must mark the preview as a video");
    }

    @Test
    public void isVideoPreview_true_whenMediaTypeIsMIMEStyle() {
        ComfyRegistryWorkflow wf = new ComfyRegistryWorkflow();
        wf.setMediaType("video/mp4");
        wf.setThumbnailUrl("https://example.com/whatever-1.webp");
        assertTrue(wf.isVideoPreview(), "MIME-style mediaType=video/mp4 must mark the preview as a video");
    }

    @Test
    public void isVideoPreview_true_whenThumbnailUrlEndsWithMp4() {
        ComfyRegistryWorkflow wf = new ComfyRegistryWorkflow();
        wf.setMediaType(null);
        wf.setMediaSubtype(null);
        wf.setThumbnailUrl("https://raw.githubusercontent.com/x/y/templates/foo-1.mp4");
        assertTrue(wf.isVideoPreview(), "URL ending in .mp4 should be detected as a video preview");
    }

    @Test
    public void isVideoPreview_false_forPlainImage() {
        ComfyRegistryWorkflow wf = new ComfyRegistryWorkflow();
        wf.setMediaType("image");
        wf.setMediaSubtype("webp");
        wf.setThumbnailUrl("https://raw.githubusercontent.com/x/y/templates/foo-1.webp");
        assertFalse(wf.isVideoPreview(), "Plain image previews must not be reported as videos");
    }

    @Test
    public void isVideoPreview_false_whenNoPreview() {
        ComfyRegistryWorkflow wf = new ComfyRegistryWorkflow();
        wf.setMediaType(null);
        wf.setMediaSubtype(null);
        wf.setThumbnailUrl(null);
        assertFalse(wf.isVideoPreview(), "Empty workflow must not be reported as a video");
    }
}
