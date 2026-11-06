package de.tki.comfyuicompanion.service.impl;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComfyAudioWorkflowBuilderTest {

    @Test
    void testBuildStableAudioWorkflow() {
        JSONObject workflow = ComfyAudioWorkflowBuilder.buildStableAudioWorkflow(
                "StableAudioOpenLoader", "Rocket thruster roar and bass rumble", 5);

        assertNotNull(workflow);
        assertTrue(workflow.has("prompt"));
        JSONObject prompt = workflow.getJSONObject("prompt");

        // Verify loader node
        assertTrue(prompt.has("1"));
        assertEquals("StableAudioOpenLoader", prompt.getJSONObject("1").getString("class_type"));

        // Verify sampler node
        assertTrue(prompt.has("2"));
        JSONObject sampler = prompt.getJSONObject("2");
        assertEquals("StabilityTextToAudio", sampler.getString("class_type"));
        assertEquals("Rocket thruster roar and bass rumble", sampler.getJSONObject("inputs").getString("prompt"));
        assertEquals(5, sampler.getJSONObject("inputs").getInt("seconds"));

        // Verify SaveAudio node
        assertTrue(prompt.has("3"));
        assertEquals("SaveAudio", prompt.getJSONObject("3").getString("class_type"));
    }

    @Test
    void testBuildGenericAudioWorkflow() {
        JSONObject workflow = ComfyAudioWorkflowBuilder.buildGenericAudioWorkflow(
                "AudioCraft", "Atmospheric storm wind", 4);

        assertNotNull(workflow);
        JSONObject prompt = workflow.getJSONObject("prompt");
        assertTrue(prompt.has("1"));
        assertEquals("AudioCraft", prompt.getJSONObject("1").getString("class_type"));
        assertEquals("Atmospheric storm wind", prompt.getJSONObject("1").getJSONObject("inputs").getString("prompt"));
        assertTrue(prompt.has("2"));
        assertEquals("SaveAudio", prompt.getJSONObject("2").getString("class_type"));
    }
}
