package de.tki.comfyuicompanion.service.pipeline;

import de.tki.comfyuicompanion.service.impl.ConfigService;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Locates and parses ComfyUI blueprint workflow files from the well-known
 * {@code companion_blueprints} directories, converting UI-format graphs to API format when needed.
 */
public class BlueprintWorkflowLoader {

    private static final Logger logger = LoggerFactory.getLogger(BlueprintWorkflowLoader.class);

    private final ConfigService configService;
    private final WorkflowTransformationService workflowTransformationService;

    /**
     * Creates a loader.
     *
     * @param configService                 configuration providing the ComfyUI installation path
     * @param workflowTransformationService service used to flatten and convert UI-format workflows
     */
    public BlueprintWorkflowLoader(ConfigService configService, WorkflowTransformationService workflowTransformationService) {
        this.configService = configService;
        this.workflowTransformationService = workflowTransformationService;
    }

    /**
     * Searches the candidate blueprint directories for a workflow file and parses the first valid match.
     *
     * @param filename the blueprint file name (simple name, resolved inside the candidate directories)
     * @return the parsed API-format workflow, or {@code null} if none was found or parseable
     */
    public JSONObject loadBlueprintWorkflow(String filename) {
        List<File> candidateDirs = new ArrayList<>();
        String comfyPath = configService.getComfyUIPath();
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

    private JSONObject parseBlueprintFile(File blueprintFile) {
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
                JSONObject flattened = workflowTransformationService != null
                        ? workflowTransformationService.flattenWorkflow(json)
                        : new WorkflowTransformationService(null).flattenWorkflow(json);
                return workflowTransformationService != null
                        ? workflowTransformationService.convertUiToApi(flattened)
                        : new WorkflowTransformationService(null).convertUiToApi(flattened);
            }
        } catch (Exception e) {
            logger.error("⚠️ [ScenePipelineRunner] Failed to load blueprint {}: {}", blueprintFile.getName(), e.getMessage());
        }
        return null;
    }
}
