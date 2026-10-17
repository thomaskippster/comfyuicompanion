package de.tki.comfyuicompanion.service.impl;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Builds ComfyUI prompt workflow JSON structures for various TTS engines
 * such as KokoroTTS, ElevenLabs, and Qwen-TTS.
 */
public final class ComfyTtsWorkflowBuilder {

    private ComfyTtsWorkflowBuilder() {
        // Utility constructor
    }

    /**
     * Constructs a workflow for KokoroTTS.
     *
     * @param nodeClass the detected Kokoro node class name
     * @param text the speech text
     * @return the workflow JSON object
     */
    public static JSONObject buildKokoroWorkflow(String nodeClass, String text) {
        JSONObject workflowJson = new JSONObject();
        JSONObject promptObj = new JSONObject();
        workflowJson.put("prompt", promptObj);

        JSONObject kokoroNode = new JSONObject();
        kokoroNode.put("class_type", nodeClass);
        JSONObject kokoroInputs = new JSONObject();
        kokoroInputs.put("text", text);
        kokoroInputs.put("voice", "American Female (Bella)");
        kokoroInputs.put("speed", 1.0);
        kokoroNode.put("inputs", kokoroInputs);
        promptObj.put("1", kokoroNode);

        JSONObject saveNode = new JSONObject();
        saveNode.put("class_type", "SaveAudio");
        JSONObject saveInputs = new JSONObject();
        saveInputs.put("filename_prefix", "tts_scene");
        saveInputs.put("audio", new JSONArray().put("1").put(0));
        saveNode.put("inputs", saveInputs);
        promptObj.put("2", saveNode);

        return workflowJson;
    }

    /**
     * Constructs a workflow for ElevenLabs TTS.
     *
     * @param apiKey the ElevenLabs API key
     * @param voiceId the selected voice ID
     * @param text the speech text
     * @return the workflow JSON object
     */
    public static JSONObject buildElevenLabsWorkflow(String apiKey, String voiceId, String text) {
        JSONObject workflowJson = new JSONObject();
        JSONObject promptObj = new JSONObject();
        workflowJson.put("prompt", promptObj);

        JSONObject elevenNode = new JSONObject();
        elevenNode.put("class_type", "ElevenLabsTextToSpeech");
        JSONObject elevenInputs = new JSONObject();
        elevenInputs.put("api_key", apiKey);
        elevenInputs.put("text", text);
        elevenInputs.put("voice_id", voiceId == null || voiceId.isEmpty() ? "21m00Tcm4TlvDq8ikWAM" : voiceId);
        elevenInputs.put("model_id", "eleven_multilingual_v2");
        elevenNode.put("inputs", elevenInputs);
        promptObj.put("1", elevenNode);

        JSONObject saveNode = new JSONObject();
        saveNode.put("class_type", "SaveAudio");
        JSONObject saveInputs = new JSONObject();
        saveInputs.put("filename_prefix", "tts_scene");
        saveInputs.put("audio", new JSONArray().put("1").put(0));
        saveNode.put("inputs", saveInputs);
        promptObj.put("2", saveNode);

        return workflowJson;
    }

    /**
     * Constructs a workflow for Qwen-TTS based on available node definitions.
     *
     * @param info object info from ComfyUI
     * @param nodeClass Qwen TTS node class name
     * @param text speech text
     * @param qwenRepo Hugging Face repository ID
     * @param qwenPath local model directory path
     * @param qwenVoice selected voice name
     * @return the workflow JSON object
     */
    public static JSONObject buildQwenWorkflow(JSONObject info, String nodeClass, String text,
                                               String qwenRepo, String qwenPath, String qwenVoice) {
        JSONObject workflowJson = new JSONObject();
        JSONObject promptObj = new JSONObject();
        workflowJson.put("prompt", promptObj);

        boolean hasLoader = info.has("QwenModelLoader") || info.has("LoadQwenModel") || info.has("QwenTTSModelLoader");
        if (hasLoader) {
            String loaderClass = info.has("QwenModelLoader") ? "QwenModelLoader"
                    : info.has("LoadQwenModel") ? "LoadQwenModel" : "QwenTTSModelLoader";
            JSONObject loader = new JSONObject();
            loader.put("class_type", loaderClass);
            JSONObject loaderInputs = new JSONObject();
            loaderInputs.put("model_path", qwenPath);
            loaderInputs.put("repo_id", qwenRepo);
            loader.put("inputs", loaderInputs);
            promptObj.put("1", loader);

            JSONObject qwen = new JSONObject();
            qwen.put("class_type", nodeClass);
            JSONObject qwenInputs = new JSONObject();
            qwenInputs.put("text", text);
            qwenInputs.put("voice", qwenVoice);
            qwenInputs.put("speed", 1.0);
            qwenInputs.put("model", new JSONArray().put("1").put(0));
            qwen.put("inputs", qwenInputs);
            promptObj.put("2", qwen);

            JSONObject save = new JSONObject();
            save.put("class_type", "SaveAudio");
            JSONObject saveInputs2 = new JSONObject();
            saveInputs2.put("filename_prefix", "tts_scene");
            saveInputs2.put("audio", new JSONArray().put("2").put(0));
            save.put("inputs", saveInputs2);
            promptObj.put("3", save);
        } else {
            JSONObject qwen = new JSONObject();
            qwen.put("class_type", nodeClass);
            JSONObject qwenInputs = new JSONObject();
            qwenInputs.put("text", text);
            qwenInputs.put("voice", qwenVoice);
            qwenInputs.put("speed", 1.0);
            qwenInputs.put("model_path", qwenPath);
            qwenInputs.put("repo_id", qwenRepo);
            qwen.put("inputs", qwenInputs);
            promptObj.put("1", qwen);

            JSONObject save = new JSONObject();
            save.put("class_type", "SaveAudio");
            JSONObject saveInputs2 = new JSONObject();
            saveInputs2.put("filename_prefix", "tts_scene");
            saveInputs2.put("audio", new JSONArray().put("1").put(0));
            save.put("inputs", saveInputs2);
            promptObj.put("2", save);
        }

        return workflowJson;
    }
}
