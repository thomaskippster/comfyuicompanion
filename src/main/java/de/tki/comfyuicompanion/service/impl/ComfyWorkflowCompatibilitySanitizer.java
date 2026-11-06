package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.workflow.pipeline.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Enterprise facade providing workflow sanitization, checkpoint splitting, and runtime node compatibility inspection.
 * Coordinates execution via {@link WorkflowSanitizationPipeline} and interfaces directly with ComfyUI's bridge services.
 */
@Component
public class ComfyWorkflowCompatibilitySanitizer {

    private static final Logger log = LoggerFactory.getLogger(ComfyWorkflowCompatibilitySanitizer.class);

    private final ObjectMapper objectMapper;
    private final WorkflowSanitizationPipeline pipeline;
    private final CheckpointSplitSanitizationStage splitStage;

    /**
     * Spring-managed constructor injecting the modular sanitization pipeline.
     */
    @Autowired
    public ComfyWorkflowCompatibilitySanitizer(
            ObjectMapper objectMapper,
            WorkflowSanitizationPipeline pipeline,
            CheckpointSplitSanitizationStage splitStage
    ) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.splitStage = splitStage != null ? splitStage : new CheckpointSplitSanitizationStage();
        this.pipeline = pipeline != null ? pipeline : createDefaultPipeline(this.splitStage);
    }

    /**
     * Standalone constructor for unit tests without full Spring context.
     */
    public ComfyWorkflowCompatibilitySanitizer(ObjectMapper objectMapper) {
        this(objectMapper, null, null);
    }

    private static WorkflowSanitizationPipeline createDefaultPipeline(CheckpointSplitSanitizationStage splitStage) {
        return new WorkflowSanitizationPipeline(List.of(
                splitStage != null ? splitStage : new CheckpointSplitSanitizationStage(),
                new ImageSaverSanitizationStage(),
                new PassThroughBypassStage(),
                new ParameterInliningStage(),
                new DanglingCleanupStage()
        ));
    }

    /**
     * Queries ComfyUI's {@code /object_info} endpoint to retrieve all currently installed node class names.
     */
    public Set<String> fetchRegisteredNodes(String comfyUrl, HttpClient httpClient) {
        if (comfyUrl == null || comfyUrl.isBlank() || httpClient == null) return Collections.emptySet();
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(comfyUrl + "/object_info")).timeout(Duration.ofSeconds(4)).GET().build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                JsonNode root = objectMapper.readTree(resp.body());
                if (root != null && root.isObject()) {
                    Set<String> names = new HashSet<>();
                    root.fieldNames().forEachRemaining(names::add);
                    return names;
                }
            }
        } catch (Exception ex) {
            log.debug("Could not fetch /object_info from {}: {}", comfyUrl, ex.getMessage());
        }
        return Collections.emptySet();
    }

    /**
     * Queries the ComfyUI Companion Python bridge endpoint {@code /cmfc/inspect-workflow}
     * to identify missing custom nodes directly from ComfyUI's internal runtime registry.
     */
    public WorkflowInspectionResult inspectWorkflowLive(String comfyUrl, HttpClient httpClient, ObjectNode workflow) {
        if (comfyUrl == null || comfyUrl.isBlank() || httpClient == null || workflow == null) {
            return new WorkflowInspectionResult(false, 0, 0, List.of());
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.set("workflow", workflow);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(comfyUrl + "/cmfc/inspect-workflow"))
                    .timeout(Duration.ofSeconds(4))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                JsonNode root = objectMapper.readTree(resp.body());
                int installed = root.path("installed_count").asInt(0);
                int missingCount = root.path("missing_count").asInt(0);
                List<MissingNodeInfo> missingList = new ArrayList<>();
                JsonNode missingArr = root.path("missing_nodes");
                if (missingArr.isArray()) {
                    for (JsonNode m : missingArr) {
                        missingList.add(new MissingNodeInfo(
                                m.path("id").asInt(-1),
                                m.path("type").asText(""),
                                m.path("title").asText("")
                        ));
                    }
                }
                return new WorkflowInspectionResult(true, installed, missingCount, missingList);
            }
        } catch (Exception ex) {
            log.debug("Bridge /cmfc/inspect-workflow unavailable on {}: {}", comfyUrl, ex.getMessage());
        }
        return new WorkflowInspectionResult(false, 0, 0, List.of());
    }

    /**
     * Sanitizes a LiteGraph workflow in-place using the pipeline.
     */
    public void sanitizeWorkflow(ObjectNode workflow, Set<String> registeredNodes) {
        sanitizeWorkflow(workflow, registeredNodes, List.of());
    }

    /**
     * Sanitizes a LiteGraph workflow in-place including model component resolution using the pipeline.
     */
    public void sanitizeWorkflow(ObjectNode workflow, Set<String> registeredNodes, List<ModelInfo> installedModels) {
        WorkflowSanitizationContext ctx = new WorkflowSanitizationContext(workflow, registeredNodes, installedModels, objectMapper);
        pipeline.execute(ctx);
    }

    /**
     * Splits a {@code CheckpointLoaderSimple} node into UNET, CLIP, and VAE loaders.
     */
    public void splitCheckpointLoaderIntoComponentLoaders(
            ObjectNode workflow, ObjectNode ckptNode, String installedUnet, List<String> installedClips, String installedVae) {
        splitStage.splitCheckpoint(workflow, ckptNode, installedUnet, installedClips, installedVae, objectMapper);
    }

    /**
     * ACL Record capturing live workflow inspection results.
     */
    public record WorkflowInspectionResult(boolean success, int installedCount, int missingCount, List<MissingNodeInfo> missingNodes) {}

    /**
     * ACL Record capturing individual missing node details.
     */
    public record MissingNodeInfo(int id, String type, String title) {}
}
