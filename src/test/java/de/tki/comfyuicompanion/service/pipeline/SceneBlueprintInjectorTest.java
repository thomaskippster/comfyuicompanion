package de.tki.comfyuicompanion.service.pipeline;

import de.tki.comfyuicompanion.domain.Scene;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class SceneBlueprintInjectorTest {

    @Test
    void testInspectAndInjectFastWanPngNodes() {
        File file = new File("C:/Users/thoma/.comfyui-companion/ComfyUI/user/default/workflows/Wan2.2/WAN 2.2 FASTWAN 5B T2V.png");
        if (!file.exists()) {
            return;
        }

        SceneBlueprintInjector injector = new SceneBlueprintInjector();
        JSONObject blueprint = injector.parseBlueprintFile(file);
        assertNotNull(blueprint, "Parsed blueprint must not be null");

        // Verify prompt injection into WanVideoTextEncode
        String testPrompt = "A futuristic cinematic drone shot over neon city";
        assertDoesNotThrow(() -> injector.injectPrompt(blueprint, testPrompt));

        JSONObject promptObj = blueprint.has("prompt") ? blueprint.getJSONObject("prompt") : blueprint;
        JSONObject textEncodeNode = promptObj.optJSONObject("10016");
        assertNotNull(textEncodeNode);
        assertEquals("WanVideoTextEncode", textEncodeNode.optString("class_type"));
        assertEquals(testPrompt, textEncodeNode.getJSONObject("inputs").getString("positive_prompt"));

        // Verify parameter injection into WanVideoSampler
        Scene scene = new Scene();
        scene.setSceneId("1");
        scene.setPrompt(testPrompt);
        scene.setNegativePrompt("blurry, low quality");
        scene.setSteps(6);
        scene.setCfgScale(1.0);
        scene.setStartFrame(0);
        scene.setEndFrame(120);

        injector.injectParamsIntoBlueprint(blueprint, 42L, "test_prefix", scene);

        JSONObject samplerNode = promptObj.optJSONObject("308");
        assertNotNull(samplerNode);
        assertEquals("WanVideoSampler", samplerNode.optString("class_type"));
        assertEquals(6, samplerNode.getJSONObject("inputs").getInt("steps"));
        assertEquals(1.0, samplerNode.getJSONObject("inputs").getDouble("cfg"), 0.001);
        assertEquals(42L, samplerNode.getJSONObject("inputs").getLong("seed"));
    }

    @Test
    void testInspectFastWanI2VPngNodes() {
        File file = new File("C:/Users/thoma/.comfyui-companion/ComfyUI/user/default/workflows/Wan2.2/WAN 2.2 FASTWAN 5B I2V.png");
        if (!file.exists()) {
            return;
        }

        SceneBlueprintInjector injector = new SceneBlueprintInjector();
        JSONObject blueprint = injector.parseBlueprintFile(file);
        assertNotNull(blueprint, "Parsed blueprint must not be null");

        assertDoesNotThrow(() -> injector.injectPrompt(blueprint, "An animated portrait in motion"));
    }
}
