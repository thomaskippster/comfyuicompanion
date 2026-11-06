package de.tki.comfyuicompanion.service.prompt.sanitization;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PromptSanitizationPipeline} verifying that unregistered custom nodes are
 * replaced purely based on ComfyUI {@code /object_info} type declarations, independent of node names.
 */
class PromptSanitizationPipelineTest {

    private PromptSanitizationPipeline pipeline;
    private JSONObject objectInfo;

    @BeforeEach
    void setUp() {
        pipeline = PromptSanitizationPipeline.createDefault();
        objectInfo = new JSONObject();
        objectInfo.put("KSampler", nodeDef(new String[]{"LATENT"}, false, new JSONObject()
                .put("model", spec("MODEL"))
                .put("seed", spec("INT", new JSONObject().put("default", 0).put("min", 0).put("max", Long.MAX_VALUE)))
                .put("steps", spec("INT", new JSONObject().put("default", 20).put("min", 1).put("max", 10000)))
                .put("sampler_name", new JSONArray().put(new JSONArray().put("euler").put("dpmpp_2m")))
                .put("latent_image", spec("LATENT"))));
        objectInfo.put("CheckpointLoaderSimple", nodeDef(new String[]{"MODEL", "CLIP", "VAE"}, false, new JSONObject()
                .put("ckpt_name", new JSONArray().put(new JSONArray().put("sd35.safetensors")))));
        objectInfo.put("EmptyLatentImage", latentFactory());
        objectInfo.put("EmptySD3LatentImage", latentFactory());
        objectInfo.put("VAEDecode", nodeDef(new String[]{"IMAGE"}, false, new JSONObject()
                .put("samples", spec("LATENT")).put("vae", spec("VAE"))));
        objectInfo.put("SaveImage", nodeDef(new String[]{}, true, new JSONObject().put("images", spec("IMAGE"))));
    }

    @Test
    @DisplayName("Inlines any missing pure INT provider into the consumer widget, independent of its name")
    void inlinesMissingIntProviderRegardlessOfName() {
        JSONObject prompt = basePrompt();
        prompt.put("57", node("Totally Unknown Number Thing", new JSONObject().put("value", 987654321L)));
        prompt.getJSONObject("3").getJSONObject("inputs").put("seed", link("57", 0));

        PromptSanitizationReport report = pipeline.sanitize(prompt, objectInfo);

        assertThat(prompt.has("57")).isFalse();
        assertThat(prompt.getJSONObject("3").getJSONObject("inputs").getLong("seed")).isEqualTo(987654321L);
        assertThat(report.isExecutable()).isTrue();
        assertThat(report.resolvedClassTypes()).containsExactly("Totally Unknown Number Thing");
    }

    @Test
    @DisplayName("Falls back to the consumer default when the provider value violates the declared range")
    void fallsBackToDeclaredDefaultWhenOutOfRange() {
        JSONObject prompt = basePrompt();
        prompt.put("57", node("Seed (rgthree)", new JSONObject().put("seed", -1)));
        prompt.getJSONObject("3").getJSONObject("inputs").put("seed", link("57", 0));

        pipeline.sanitize(prompt, objectInfo);

        assertThat(prompt.getJSONObject("3").getJSONObject("inputs").getLong("seed")).isZero();
    }

    @Test
    @DisplayName("Inlines COMBO selectors only with values contained in the consumer options")
    void inlinesComboSelectorWithValidOption() {
        JSONObject prompt = basePrompt();
        prompt.put("60", node("Sampler Selector (Custom)", new JSONObject().put("sampler", "dpmpp_2m")));
        prompt.getJSONObject("3").getJSONObject("inputs").put("sampler_name", link("60", 0));

        pipeline.sanitize(prompt, objectInfo);

        assertThat(prompt.getJSONObject("3").getJSONObject("inputs").getString("sampler_name")).isEqualTo("dpmpp_2m");
    }

    @Test
    @DisplayName("Replaces a missing latent provider with a discovered factory and parses the resolution label")
    void replacesMissingLatentProviderAndParsesResolution() {
        JSONObject prompt = basePrompt();
        prompt.put("56", node("Empty Latent Ratio Select SDXL",
                new JSONObject().put("ratio_selected", "2:3 [832x1216 portrait]").put("batch_size", 2)));
        prompt.getJSONObject("3").getJSONObject("inputs").put("latent_image", link("56", 0));

        PromptSanitizationReport report = pipeline.sanitize(prompt, objectInfo);

        JSONObject replaced = prompt.getJSONObject("56");
        assertThat(replaced.getString("class_type")).isEqualTo("EmptyLatentImage");
        assertThat(replaced.getJSONObject("inputs").getInt("width")).isEqualTo(832);
        assertThat(replaced.getJSONObject("inputs").getInt("height")).isEqualTo(1216);
        assertThat(replaced.getJSONObject("inputs").getInt("batch_size")).isEqualTo(2);
        assertThat(report.isExecutable()).isTrue();
    }

    @Test
    @DisplayName("Prefers a latent factory already used in the prompt and derives size from an aspect ratio")
    void prefersFactoryInUseAndDerivesSizeFromAspectRatio() {
        JSONObject prompt = basePrompt();
        prompt.put("90", node("EmptySD3LatentImage", new JSONObject().put("width", 512).put("height", 512).put("batch_size", 1)));
        prompt.put("56", node("Some Aspect Ratio Node", new JSONObject().put("aspect", "16:9")));
        prompt.getJSONObject("3").getJSONObject("inputs").put("latent_image", link("56", 0));

        pipeline.sanitize(prompt, objectInfo);

        JSONObject replaced = prompt.getJSONObject("56");
        assertThat(replaced.getString("class_type")).isEqualTo("EmptySD3LatentImage");
        assertThat(replaced.getJSONObject("inputs").getInt("width")).isEqualTo(1344);
        assertThat(replaced.getJSONObject("inputs").getInt("height")).isEqualTo(768);
    }

    @Test
    @DisplayName("Bypasses a missing routing node by rewiring to a type-compatible upstream producer")
    void bypassesMissingRoutingNode() {
        JSONObject prompt = basePrompt();
        prompt.put("70", node("Any Switch (rgthree)", new JSONObject().put("any_01", link("4", 0))));
        prompt.getJSONObject("3").getJSONObject("inputs").put("model", link("70", 0));

        pipeline.sanitize(prompt, objectInfo);

        assertThat(prompt.has("70")).isFalse();
        JSONArray modelLink = prompt.getJSONObject("3").getJSONObject("inputs").getJSONArray("model");
        assertThat(modelLink.getString(0)).isEqualTo("4");
        assertThat(modelLink.getInt(1)).isZero();
    }

    @Test
    @DisplayName("Prunes missing side-branch sinks while a registered output node remains")
    void prunesMissingDanglingSink() {
        JSONObject prompt = basePrompt();
        prompt.put("80", node("Image Comparer (rgthree)", new JSONObject().put("image_a", link("8", 0))));

        pipeline.sanitize(prompt, objectInfo);

        assertThat(prompt.has("80")).isFalse();
        assertThat(prompt.has("9")).isTrue();
    }

    @Test
    @DisplayName("Keeps functional custom nodes and reports them for installation")
    void reportsFunctionalNodesAsRemaining() {
        JSONObject prompt = basePrompt();
        prompt.put("95", node("FaceDetailer", new JSONObject()
                .put("image", link("8", 0)).put("model", link("4", 0)).put("guide_size", 512)));
        prompt.getJSONObject("9").getJSONObject("inputs").put("images", link("95", 0));

        PromptSanitizationReport report = pipeline.sanitize(prompt, objectInfo);

        assertThat(prompt.has("95")).isTrue();
        assertThat(report.isExecutable()).isFalse();
        assertThat(report.remainingMissingClassTypes()).containsExactly("FaceDetailer");
    }

    @Test
    @DisplayName("Leaves the prompt untouched when no node registry is available")
    void noOpWithoutRegistry() {
        JSONObject prompt = basePrompt();
        prompt.put("57", node("Seed (rgthree)", new JSONObject().put("seed", 5)));

        PromptSanitizationReport report = pipeline.sanitize(prompt, new JSONObject());

        assertThat(prompt.has("57")).isTrue();
        assertThat(report.resolvedClassTypes()).isEmpty();
    }

    @Test
    @DisplayName("Replaces missing custom image saver with core SaveImage preserving filename prefix")
    void replacesMissingCustomImageSaverWithCoreSaveImage() {
        JSONObject prompt = basePrompt();
        prompt.remove("9"); // remove base SaveImage
        prompt.put("10", node("SaveImageWebp", new JSONObject()
                .put("images", link("8", 0))
                .put("filename_prefix", "MyArt")
                .put("lossless", true)));

        PromptSanitizationReport report = pipeline.sanitize(prompt, objectInfo);

        assertThat(prompt.has("10")).isTrue();
        JSONObject replaced = prompt.getJSONObject("10");
        assertThat(replaced.getString("class_type")).isEqualTo("SaveImage");
        assertThat(replaced.getJSONObject("inputs").getString("filename_prefix")).isEqualTo("MyArt");
        assertThat(replaced.getJSONObject("inputs").getJSONArray("images").getString(0)).isEqualTo("8");
        assertThat(report.isExecutable()).isTrue();
        assertThat(report.resolvedClassTypes()).contains("SaveImageWebp");
    }

    @Test
    @DisplayName("Replaces missing custom tiled VAE decoder with core VAEDecode")
    void replacesMissingTiledVaeDecoderWithCoreVaeDecode() {
        JSONObject prompt = basePrompt();
        prompt.remove("8"); // remove base VAEDecode
        prompt.put("88", node("VAEDecodeTiled", new JSONObject()
                .put("samples", link("3", 0))
                .put("vae", link("4", 2))
                .put("tile_size", 512)));
        prompt.getJSONObject("9").getJSONObject("inputs").put("images", link("88", 0));

        PromptSanitizationReport report = pipeline.sanitize(prompt, objectInfo);

        assertThat(prompt.has("88")).isTrue();
        JSONObject replaced = prompt.getJSONObject("88");
        assertThat(replaced.getString("class_type")).isEqualTo("VAEDecode");
        assertThat(replaced.getJSONObject("inputs").getJSONArray("samples").getString(0)).isEqualTo("3");
        assertThat(replaced.getJSONObject("inputs").getJSONArray("vae").getString(0)).isEqualTo("4");
        assertThat(report.isExecutable()).isTrue();
        assertThat(report.resolvedClassTypes()).contains("VAEDecodeTiled");
    }

    @Test
    @DisplayName("Bypasses missing LoRA loader and connects model directly to consumer")
    void bypassesMissingLoraLoaderNode() {
        JSONObject prompt = basePrompt();
        prompt.put("20", node("Lora Loader (LoraManager)", new JSONObject()
                .put("model", link("4", 0))
                .put("lora_name", "detail_enhancer.safetensors")
                .put("strength_model", 1.0)));
        prompt.getJSONObject("3").getJSONObject("inputs").put("model", link("20", 0));

        PromptSanitizationReport report = pipeline.sanitize(prompt, objectInfo);

        assertThat(prompt.has("20")).isFalse();
        JSONArray modelLink = prompt.getJSONObject("3").getJSONObject("inputs").getJSONArray("model");
        assertThat(modelLink.getString(0)).isEqualTo("4");
        assertThat(modelLink.getInt(1)).isZero();
        assertThat(report.isExecutable()).isTrue();
    }

    @Test
    @DisplayName("Bypasses missing VRAM cleanup node between VAE decode and SaveImage")
    void bypassesMissingVramCleanupNode() {
        JSONObject prompt = basePrompt();
        prompt.put("50", node("VRAMCleanup", new JSONObject().put("anything", link("8", 0))));
        prompt.getJSONObject("9").getJSONObject("inputs").put("images", link("50", 0));

        PromptSanitizationReport report = pipeline.sanitize(prompt, objectInfo);

        assertThat(prompt.has("50")).isFalse();
        JSONArray imgLink = prompt.getJSONObject("9").getJSONObject("inputs").getJSONArray("images");
        assertThat(imgLink.getString(0)).isEqualTo("8");
        assertThat(report.isExecutable()).isTrue();
    }

    // --- fixtures -------------------------------------------------------------------------------

    private JSONObject basePrompt() {
        JSONObject prompt = new JSONObject();
        prompt.put("4", node("CheckpointLoaderSimple", new JSONObject().put("ckpt_name", "sd35.safetensors")));
        prompt.put("3", node("KSampler", new JSONObject()
                .put("model", link("4", 0)).put("seed", 1).put("steps", 20).put("sampler_name", "euler")));
        prompt.put("8", node("VAEDecode", new JSONObject().put("samples", link("3", 0)).put("vae", link("4", 2))));
        prompt.put("9", node("SaveImage", new JSONObject().put("images", link("8", 0))));
        return prompt;
    }

    private static JSONObject node(String classType, JSONObject inputs) {
        return new JSONObject().put("class_type", classType).put("inputs", inputs);
    }

    private static JSONArray link(String nodeId, int slot) {
        return new JSONArray().put(nodeId).put(slot);
    }

    private static JSONArray spec(String type) {
        return new JSONArray().put(type);
    }

    private static JSONArray spec(String type, JSONObject options) {
        return new JSONArray().put(type).put(options);
    }

    private static JSONObject latentFactory() {
        return nodeDef(new String[]{"LATENT"}, false, new JSONObject()
                .put("width", spec("INT", new JSONObject().put("default", 1024)))
                .put("height", spec("INT", new JSONObject().put("default", 1024)))
                .put("batch_size", spec("INT", new JSONObject().put("default", 1))));
    }

    private static JSONObject nodeDef(String[] outputs, boolean outputNode, JSONObject required) {
        return new JSONObject()
                .put("input", new JSONObject().put("required", required))
                .put("output", new JSONArray(outputs))
                .put("output_node", outputNode);
    }
}
