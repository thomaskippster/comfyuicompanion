package de.tki.comfyuicompanion.service.pipeline;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.PromptBlueprintApiService;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Component responsible for loading blueprint workflows and injecting parameters,
 * prompts, narration audio, and speaker reference images into workflow graph nodes.
 */
@Component
public class SceneBlueprintInjector {
    private static final Logger logger = LoggerFactory.getLogger(SceneBlueprintInjector.class);

    private final WorkflowTransformationService workflowTransformationService;

    public SceneBlueprintInjector() {
        this(null);
    }

    @Autowired
    public SceneBlueprintInjector(@Autowired(required = false) WorkflowTransformationService workflowTransformationService) {
        this.workflowTransformationService = workflowTransformationService != null
                ? workflowTransformationService
                : new WorkflowTransformationService(null);
    }

    /**
     * Injects narration audio file reference into LoadAudio nodes in a workflow graph.
     *
     * @param workflowJson the workflow prompt JSON
     * @param filename the uploaded audio filename
     */
    public void injectAudio(JSONObject workflowJson, String filename) {
        JSONObject promptObj = workflowJson.has("prompt") ? workflowJson.getJSONObject("prompt") : workflowJson;
        boolean injected = false;
        for (String key : promptObj.keySet()) {
            JSONObject node = promptObj.optJSONObject(key);
            if (node == null) continue;
            String classType = node.optString("class_type", "");
            if ("LoadAudio".equals(classType) || "VHS_LoadAudio".equals(classType)) {
                JSONObject inputs = node.optJSONObject("inputs");
                if (inputs != null) {
                    inputs.put("audio", filename);
                    injected = true;
                    logger.info("🎵 [SceneBlueprintInjector] Injected audio '{}' into node ID: {}", filename, key);
                }
            }
        }
        if (!injected) {
            logger.info("ℹ️ [SceneBlueprintInjector] No LoadAudio/VHS_LoadAudio node found in workflow to inject audio.");
        }
    }

    /**
     * Injects speaker image reference into LoadImage nodes in a workflow graph.
     *
     * @param workflowJson the workflow prompt JSON
     * @param filename the uploaded speaker image filename
     */
    public void injectSpeakerImage(JSONObject workflowJson, String filename) {
        JSONObject promptObj = workflowJson.has("prompt") ? workflowJson.getJSONObject("prompt") : workflowJson;
        boolean injected = false;
        for (String key : promptObj.keySet()) {
            JSONObject node = promptObj.optJSONObject(key);
            if (node == null) continue;
            String classType = node.optString("class_type", "");
            if ("LoadImage".equals(classType)) {
                JSONObject inputs = node.optJSONObject("inputs");
                if (inputs != null) {
                    inputs.put("image", filename);
                    injected = true;
                    logger.info("📥 [SceneBlueprintInjector] Injected speaker image '{}' into LoadImage node ID: {}", filename, key);
                }
            }
        }
        if (!injected) {
            logger.info("ℹ️ [SceneBlueprintInjector] No LoadImage node found in workflow to inject speaker image.");
        }
    }

    /**
     * Injects textual prompt into text encoding nodes in a workflow graph.
     *
     * @param workflowJson the workflow prompt JSON
     * @param promptText the visual prompt description
     */
    public void injectPrompt(JSONObject workflowJson, String promptText) {
        JSONObject promptObj = workflowJson.has("prompt") ? workflowJson.getJSONObject("prompt") : workflowJson;
        boolean injected = false;

        for (String key : promptObj.keySet()) {
            JSONObject node = promptObj.optJSONObject(key);
            if (node == null) continue;

            String classType = node.optString("class_type", "");
            JSONObject inputs = node.optJSONObject("inputs");
            if (inputs == null) continue;

            if ("CLIPTextEncode".equals(classType)) {
                String text = inputs.optString("text", "");
                if (!text.toLowerCase().contains("blurry") && !text.toLowerCase().contains("bad hands")
                        && !text.toLowerCase().contains("worst quality") && !text.toLowerCase().contains("low quality")
                        && !text.toLowerCase().contains("静止") && !text.toLowerCase().contains("最差质量")) {
                    inputs.put("text", promptText);
                    injected = true;
                    logger.info("📥 [SceneBlueprintInjector] Injected visual_prompt into CLIPTextEncode node ID: {}", key);
                }
            } else if ("98ee9e5b-467b-40aa-a534-36033f27d0b4".equals(classType)
                    || "84e2cf3f-de93-40ef-ab22-b9375296917b".equals(classType)) {
                if (inputs.has("text")) {
                    inputs.put("text", promptText);
                } else {
                    inputs.put("value", promptText);
                }
                injected = true;
                logger.info("📥 [SceneBlueprintInjector] Injected visual_prompt into Video Gen Subgraph node ID: {}", key);
            } else if ("PrimitiveStringMultiline".equals(classType)) {
                inputs.put("value", promptText);
                injected = true;
                logger.info("📥 [SceneBlueprintInjector] Injected visual_prompt into PrimitiveStringMultiline node ID: {}", key);
            }
        }

        if (!injected) {
            throw new RuntimeException("Could not find suitable prompt input node in workflow template!");
        }
    }

    /**
     * Injects generation parameters (seeds, steps, cfg, duration) into a loaded blueprint workflow.
     *
     * @param workflowJson the blueprint workflow JSON
     * @param seed the random generation seed
     * @param filenamePrefix output filename prefix
     * @param scene the scene domain model
     */
    public void injectParamsIntoBlueprint(JSONObject workflowJson, long seed, String filenamePrefix, Scene scene) {
        JSONObject promptObj = workflowJson.has("prompt") ? workflowJson.getJSONObject("prompt") : workflowJson;
        double sceneDuration = (scene.getEndFrame() - scene.getStartFrame()) / 30.0;
        if (sceneDuration <= 0) {
            sceneDuration = 5.0;
        }

        for (String key : promptObj.keySet()) {
            JSONObject node = promptObj.optJSONObject(key);
            if (node == null) continue;

            String classType = node.optString("class_type", "");
            JSONObject inputs = node.optJSONObject("inputs");
            if (inputs == null) continue;

            if ("84e2cf3f-de93-40ef-ab22-b9375296917b".equals(classType)
                    || "98ee9e5b-467b-40aa-a534-36033f27d0b4".equals(classType)) {
                if (inputs.has("value_1")) {
                    inputs.put("value_1", sceneDuration);
                    logger.info("📥 [SceneBlueprintInjector] Injected scene duration {}s into video subgraph node ID: {}", sceneDuration, key);
                }
            }

            long clampedSeed = PromptBlueprintApiService.clampSeedForNode(classType, seed);
            if (inputs.has("seed")) {
                inputs.put("seed", clampedSeed);
            }
            if (inputs.has("noise_seed")) {
                inputs.put("noise_seed", clampedSeed);
            }

            if ("KSampler".equals(classType) || "KSamplerAdvanced".equals(classType)) {
                int wanSteps = scene.getSteps() > 0 ? scene.getSteps() : 4;
                if (wanSteps > 8) {
                    wanSteps = 4;
                }
                if (inputs.has("steps")) {
                    inputs.put("steps", wanSteps);
                }
                double wanCfg = scene.getCfgScale() > 0 ? scene.getCfgScale() : 2.0;
                if (wanCfg > 4.0) {
                    wanCfg = 3.0;
                }
                if (inputs.has("cfg")) {
                    inputs.put("cfg", wanCfg);
                }
            }

            if ("VHS_VideoCombine".equals(classType) || "SaveVideo".equals(classType)) {
                inputs.put("filename_prefix", filenamePrefix);
                if ("VHS_VideoCombine".equals(classType)) {
                    inputs.put("save_output", true);
                }
            }
        }
        PromptBlueprintApiService.sanitizeAllSeedsInPrompt(promptObj);
    }

    /**
     * Loads and parses a blueprint workflow file from candidate directories.
     *
     * @param filename blueprint filename
     * @param comfyPath ComfyUI root directory
     * @return parsed JSONObject or null
     */
    public JSONObject loadBlueprintWorkflow(String filename, String comfyPath) {
        List<File> candidateDirs = new ArrayList<>();
        if (comfyPath != null && !comfyPath.trim().isEmpty()) {
            candidateDirs.add(new File(comfyPath, "companion_blueprints"));
            candidateDirs.add(new File(comfyPath, "resources/ComfyUI/companion_blueprints"));
        }
        candidateDirs.add(new File("companion_blueprints"));
        candidateDirs.add(new File(System.getProperty("user.dir"), "companion_blueprints"));

        for (File blueprintsDir : candidateDirs) {
            if (blueprintsDir == null || !blueprintsDir.exists() || !blueprintsDir.isDirectory()) {
                continue;
            }
            File blueprintFile = new File(blueprintsDir, filename);
            JSONObject parsed = parseBlueprintFile(blueprintFile);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    /**
     * Parses a blueprint file from JSON, handling format conversion if necessary.
     *
     * @param blueprintFile blueprint file
     * @return parsed JSON object or null
     */
    public JSONObject parseBlueprintFile(File blueprintFile) {
        if (!blueprintFile.exists() || !blueprintFile.isFile()) {
            return null;
        }
        try {
            String content = Files.readString(blueprintFile.toPath(), StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(content);
            if (json.has("prompt") || json.keySet().stream().anyMatch(key -> {
                JSONObject node = json.optJSONObject(key);
                return node != null && node.has("class_type");
            })) {
                return json;
            }
            if (json.has("nodes")) {
                JSONObject flattened = workflowTransformationService.flattenWorkflow(json);
                return workflowTransformationService.convertUiToApi(flattened);
            }
        } catch (Exception e) {
            logger.error("⚠️ [SceneBlueprintInjector] Failed to load blueprint {}: {}", blueprintFile.getName(), e.getMessage());
        }
        return null;
    }
}
