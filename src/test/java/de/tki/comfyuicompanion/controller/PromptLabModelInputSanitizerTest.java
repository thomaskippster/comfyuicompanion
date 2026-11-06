package de.tki.comfyuicompanion.controller;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PromptLabModelInputSanitizer} validating automatic sanitization
 * of missing custom utility nodes such as seed generators and empty latent selectors.
 */
class PromptLabModelInputSanitizerTest {

    private PromptLabModelInputSanitizer sanitizer;
    private JSONObject objectInfo;

    @BeforeEach
    void setUp() {
        sanitizer = new PromptLabModelInputSanitizer();
        objectInfo = new JSONObject();
        // Core nodes known by ComfyUI
        objectInfo.put("KSampler", new JSONObject());
        objectInfo.put("EmptyLatentImage", new JSONObject());
        objectInfo.put("EmptySD3LatentImage", new JSONObject());
        objectInfo.put("TripleCLIPLoader", new JSONObject());
    }

    @Test
    @DisplayName("Should inline missing 'Seed (rgthree)' into KSampler seed input and prune the seed node")
    void shouldInlineMissingSeedRgthreeNode() {
        JSONObject prompt = new JSONObject();

        // Node 57: Seed (rgthree) - missing from objectInfo
        JSONObject seedNode = new JSONObject();
        seedNode.put("class_type", "Seed (rgthree)");
        JSONObject seedInputs = new JSONObject();
        seedInputs.put("seed", 987654321L);
        seedNode.put("inputs", seedInputs);
        prompt.put("57", seedNode);

        // Node 3: KSampler referencing Node 57 for seed
        JSONObject ksampler = new JSONObject();
        ksampler.put("class_type", "KSampler");
        JSONObject ksamplerInputs = new JSONObject();
        ksamplerInputs.put("seed", new JSONArray().put("57").put(0));
        ksampler.put("inputs", ksamplerInputs);
        prompt.put("3", ksampler);

        sanitizer.sanitizeMissingUtilityNodesInPrompt(prompt, objectInfo);

        // Node 57 should be removed
        assertThat(prompt.has("57")).isFalse();
        // KSampler seed input should now be the integer/long 987654321
        assertThat(prompt.getJSONObject("3").getJSONObject("inputs").get("seed")).isEqualTo(987654321L);
    }

    @Test
    @DisplayName("Should convert missing 'Empty Latent Ratio Select SDXL' to 'EmptySD3LatentImage' for SD3 workflows")
    void shouldConvertMissingEmptyLatentRatioSelectSdxlToSd3() {
        JSONObject prompt = new JSONObject();

        // Trigger SD3 detection with TripleCLIPLoader
        JSONObject clipNode = new JSONObject();
        clipNode.put("class_type", "TripleCLIPLoader");
        prompt.put("1", clipNode);

        // Node 56: Empty Latent Ratio Select SDXL - missing from objectInfo
        JSONObject ratioNode = new JSONObject();
        ratioNode.put("class_type", "Empty Latent Ratio Select SDXL");
        JSONObject ratioInputs = new JSONObject();
        ratioInputs.put("batch_size", 1);
        ratioNode.put("inputs", ratioInputs);
        prompt.put("56", ratioNode);

        sanitizer.sanitizeMissingUtilityNodesInPrompt(prompt, objectInfo);

        // Class type should be converted to EmptySD3LatentImage
        JSONObject convertedNode = prompt.getJSONObject("56");
        assertThat(convertedNode.getString("class_type")).isEqualTo("EmptySD3LatentImage");
        JSONObject inputs = convertedNode.getJSONObject("inputs");
        assertThat(inputs.getInt("width")).isEqualTo(1024);
        assertThat(inputs.getInt("height")).isEqualTo(1024);
        assertThat(inputs.getInt("batch_size")).isEqualTo(1);
    }

    @Test
    @DisplayName("Should preserve nodes that are registered in objectInfo")
    void shouldPreserveInstalledNodes() {
        // Register Seed (rgthree) in objectInfo
        objectInfo.put("Seed (rgthree)", new JSONObject());

        JSONObject prompt = new JSONObject();
        JSONObject seedNode = new JSONObject();
        seedNode.put("class_type", "Seed (rgthree)");
        JSONObject seedInputs = new JSONObject();
        seedInputs.put("seed", 42L);
        seedNode.put("inputs", seedInputs);
        prompt.put("57", seedNode);

        sanitizer.sanitizeMissingUtilityNodesInPrompt(prompt, objectInfo);

        // Node 57 must remain untouched because it is installed
        assertThat(prompt.has("57")).isTrue();
        assertThat(prompt.getJSONObject("57").getString("class_type")).isEqualTo("Seed (rgthree)");
    }
}
