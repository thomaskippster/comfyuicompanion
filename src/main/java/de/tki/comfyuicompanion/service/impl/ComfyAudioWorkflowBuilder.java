package de.tki.comfyuicompanion.service.impl;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Builds ComfyUI prompt workflow JSON structures for text-to-audio and sound effect synthesis.
 * Supports Stable Audio Open, AudioCraft, ACE-Step, and generic ComfyUI audio nodes.
 */
public final class ComfyAudioWorkflowBuilder {

    private ComfyAudioWorkflowBuilder() {
        // Utility constructor
    }

    /**
     * Builds a Stable Audio Open workflow graph for text-to-audio generation.
     *
     * @param loaderClass node class for model loader (e.g. "StableAudioOpenLoader")
     * @param prompt      descriptive audio prompt
     * @param durationSec duration in seconds
     * @return ComfyUI workflow JSON
     */
    public static JSONObject buildStableAudioWorkflow(String loaderClass, String prompt, int durationSec) {
        JSONObject workflowJson = new JSONObject();
        JSONObject promptObj = new JSONObject();
        workflowJson.put("prompt", promptObj);

        // Node 1: Loader
        JSONObject loaderNode = new JSONObject();
        loaderNode.put("class_type", loaderClass != null ? loaderClass : "StableAudioOpenLoader");
        JSONObject loaderInputs = new JSONObject();
        loaderInputs.put("model_name", "stable-audio-open-1.0.safetensors");
        loaderNode.put("inputs", loaderInputs);
        promptObj.put("1", loaderNode);

        // Node 2: Text Encode / Sampler
        JSONObject samplerNode = new JSONObject();
        samplerNode.put("class_type", "StabilityTextToAudio");
        JSONObject samplerInputs = new JSONObject();
        samplerInputs.put("prompt", prompt);
        samplerInputs.put("seconds", Math.max(1, durationSec));
        samplerInputs.put("steps", 30);
        samplerInputs.put("cfg", 6.0);
        samplerInputs.put("model", new JSONArray().put("1").put(0));
        samplerNode.put("inputs", samplerInputs);
        promptObj.put("2", samplerNode);

        // Node 3: SaveAudio
        JSONObject saveNode = new JSONObject();
        saveNode.put("class_type", "SaveAudio");
        JSONObject saveInputs = new JSONObject();
        saveInputs.put("filename_prefix", "sfx_scene");
        saveInputs.put("audio", new JSONArray().put("2").put(0));
        saveNode.put("inputs", saveInputs);
        promptObj.put("3", saveNode);

        return workflowJson;
    }

    /**
     * Builds an ACE-Step or AudioCraft workflow graph for text-to-sound effects.
     *
     * @param nodeClass detected audio generation node class
     * @param prompt    sound effect prompt
     * @param seconds   audio duration
     * @return ComfyUI workflow JSON
     */
    public static JSONObject buildGenericAudioWorkflow(String nodeClass, String prompt, int seconds) {
        JSONObject workflowJson = new JSONObject();
        JSONObject promptObj = new JSONObject();
        workflowJson.put("prompt", promptObj);

        JSONObject audioNode = new JSONObject();
        audioNode.put("class_type", nodeClass);
        JSONObject audioInputs = new JSONObject();
        audioInputs.put("text", prompt);
        audioInputs.put("prompt", prompt);
        audioInputs.put("duration", Math.max(1, seconds));
        audioInputs.put("seconds", Math.max(1, seconds));
        audioNode.put("inputs", audioInputs);
        promptObj.put("1", audioNode);

        JSONObject saveNode = new JSONObject();
        saveNode.put("class_type", "SaveAudio");
        JSONObject saveInputs = new JSONObject();
        saveInputs.put("filename_prefix", "sfx_scene");
        saveInputs.put("audio", new JSONArray().put("1").put(0));
        saveNode.put("inputs", saveInputs);
        promptObj.put("2", saveNode);

        return workflowJson;
    }
}
