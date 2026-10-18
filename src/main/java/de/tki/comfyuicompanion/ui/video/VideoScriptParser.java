package de.tki.comfyuicompanion.ui.video;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.IVideoPromptOptimizer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for parsing LLM storyboard responses into Scene sequences,
 * with rule-based sentence segmentation fallback.
 */
public class VideoScriptParser {
    private static final Logger logger = LoggerFactory.getLogger(VideoScriptParser.class);

    /**
     * Parses raw response JSON into scenes, falling back to rule-based segmentation if needed.
     */
    public List<Scene> parseStoryboardJson(String rawResponse,
                                           String originalIdea,
                                           int targetW,
                                           int targetH,
                                           IVideoPromptOptimizer promptOptimizer) {
        List<Scene> scenes = new ArrayList<>();
        if (rawResponse != null && !rawResponse.trim().isEmpty()) {
            try {
                String clean = rawResponse.replaceAll("```json", "").replaceAll("```", "").replace("\u0000", "").trim();
                int startObj = clean.indexOf('{');
                int endObj = clean.lastIndexOf('}');
                int startArr = clean.indexOf('[');
                int endArr = clean.lastIndexOf(']');

                // Prefer parsing root storyboard JSON object if it encompasses the response
                if (startObj >= 0 && endObj > startObj && (startArr < 0 || startObj < startArr)) {
                    JSONObject rootObj = new JSONObject(clean.substring(startObj, endObj + 1));
                    String globalStyleAnchor = rootObj.optString("style_anchor", rootObj.optString("styleAnchor", ""));
                    String globalNegativePrompt = rootObj.optString("negative_prompt", rootObj.optString("negativePrompt", ""));

                    JSONArray arr = rootObj.optJSONArray("scenes");
                    if (arr == null) arr = rootObj.optJSONArray("storyboard");
                    if (arr == null) arr = rootObj.optJSONArray("timeline");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            Scene scene = parseSceneObject(obj, i + 1, targetW, targetH, globalStyleAnchor, globalNegativePrompt);
                            if (scene != null) {
                                scenes.add(scene);
                            }
                        }
                    }
                } else if (startArr >= 0 && endArr > startArr) {
                    clean = clean.substring(startArr, endArr + 1);
                    JSONArray arr = new JSONArray(clean);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        Scene scene = parseSceneObject(obj, i + 1, targetW, targetH, "", "");
                        if (scene != null) {
                            scenes.add(scene);
                        }
                    }
                }
            } catch (Exception ex) {
                logger.warn("JSON parsing failed on LLM storyboard response: {}. Falling back to rule-based segmentation.", ex.getMessage());
            }
        }

        if (scenes.isEmpty() && originalIdea != null && !originalIdea.trim().isEmpty()) {
            scenes = splitScriptFallback(originalIdea, targetW, targetH, promptOptimizer);
        }

        if (promptOptimizer != null && scenes.size() > 1) {
            promptOptimizer.harmonizeSceneLighting(scenes);
        }

        return scenes;
    }

    /**
     * Decomposes user text into coherent visual scenes using sentence boundary analysis.
     */
    public List<Scene> splitScriptFallback(String idea,
                                           int targetW,
                                           int targetH,
                                           IVideoPromptOptimizer promptOptimizer) {
        List<Scene> fallbackScenes = new ArrayList<>();
        String[] sentences = idea.split("(?<=[.!?\\n])\\s+");
        int count = 1;
        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.length() > 2) {
                Scene sc = new Scene();
                sc.setSceneId("S" + count++);
                String enrichedPrompt = promptOptimizer != null
                        ? promptOptimizer.enrichFallbackPrompt(trimmed)
                        : ("Cinematic shot, 4k resolution, high quality, " + trimmed);
                sc.setPrompt(enrichedPrompt);
                sc.setNarrationText(trimmed);
                int duration = Math.min(8, Math.max(3, (trimmed.split("\\s+").length / 2) + 2));
                sc.setStartFrame(0);
                sc.setEndFrame(duration * 24);
                sc.setWidth(targetW);
                sc.setHeight(targetH);
                sc.setCfgScale(1.0);
                sc.setSteps(30);
                fallbackScenes.add(sc);
            }
        }
        if (fallbackScenes.isEmpty() && !idea.trim().isEmpty()) {
            String enrichedSingle = promptOptimizer != null
                    ? promptOptimizer.enrichFallbackPrompt(idea.trim())
                    : ("Cinematic shot, " + idea.trim());
            Scene single = new Scene("S1", enrichedSingle, 0, 120, "", "");
            single.setWidth(targetW);
            single.setHeight(targetH);
            single.setNarrationText(idea.trim());
            fallbackScenes.add(single);
        }
        return fallbackScenes;
    }

    private Scene parseSceneObject(JSONObject obj, int fallbackIndex, int targetW, int targetH,
                                   String globalStyleAnchor, String globalNegativePrompt) {
        String id = obj.optString("scene_id", obj.optString("id", "S" + fallbackIndex));
        String prompt = obj.optString("visual_prompt", obj.optString("prompt", obj.optString("description", "")));
        String narration = obj.optString("narration", obj.optString("narration_text", obj.optString("voiceover", "")));
        String cameraMotion = obj.optString("camera_motion", obj.optString("cameraMotion", ""));
        int dur = obj.optInt("duration_seconds", obj.optInt("duration", 5));
        if (dur <= 0) dur = 5;

        if (prompt.isEmpty() && narration.isEmpty()) return null;

        String styleAnchor = obj.optString("style_anchor", obj.optString("styleAnchor", globalStyleAnchor != null ? globalStyleAnchor : ""));
        String negativePrompt = obj.optString("negative_prompt", obj.optString("negativePrompt", globalNegativePrompt != null ? globalNegativePrompt : ""));

        String rawPrompt = prompt.isEmpty() ? narration : prompt;
        String finalPrompt = rawPrompt;
        if (styleAnchor != null && !styleAnchor.isBlank() && !finalPrompt.contains(styleAnchor.trim())) {
            finalPrompt = finalPrompt.trim().replaceAll("[,.]+$", "") + ", " + styleAnchor.trim();
        }

        Scene scene = new Scene();
        scene.setSceneId(id);
        scene.setPrompt(finalPrompt);
        scene.setStyleAnchor(styleAnchor);
        scene.setNegativePrompt(negativePrompt);
        scene.setCameraMotion(cameraMotion);
        scene.setNarrationText(narration.isEmpty() ? "" : narration);
        scene.setStartFrame(0);
        scene.setEndFrame(dur * 24);
        scene.setWidth(targetW);
        scene.setHeight(targetH);
        scene.setCfgScale(1.0);
        scene.setSteps(30);
        return scene;
    }
}
