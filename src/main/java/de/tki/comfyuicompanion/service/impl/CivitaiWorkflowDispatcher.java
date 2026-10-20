package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tki.comfyuicompanion.domain.CivitaiGenerationResult;
import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Dispatcher responsible for sending synthesized workflows to ComfyUI canvas via REST bridge,
 * triggering execution queues, and polling for generated image results.
 */
@Component
public class CivitaiWorkflowDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CivitaiWorkflowDispatcher.class);

    private final ConfigService configService;
    private final IComfyLifecycleService lifecycleService;
    private final ComfyApiClient comfyApiClient;
    private final CivitaiGuiWorkflowBuilder guiWorkflowBuilder;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /**
     * Constructs a new CivitaiWorkflowDispatcher with required dependencies.
     *
     * @param configService configuration service
     * @param lifecycleService ComfyUI lifecycle management service
     * @param comfyApiClient client for ComfyUI REST endpoints
     * @param guiWorkflowBuilder builder for GUI workflows
     * @param objectMapper JSON object mapper
     */
    @Autowired
    public CivitaiWorkflowDispatcher(
            ConfigService configService,
            @Autowired(required = false) IComfyLifecycleService lifecycleService,
            ComfyApiClient comfyApiClient,
            @Autowired(required = false) CivitaiGuiWorkflowBuilder guiWorkflowBuilder,
            ObjectMapper objectMapper) {
        this.configService = configService;
        this.lifecycleService = lifecycleService;
        this.comfyApiClient = comfyApiClient;
        this.guiWorkflowBuilder = guiWorkflowBuilder != null ? guiWorkflowBuilder : new CivitaiGuiWorkflowBuilder(objectMapper, configService);
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Sends the visual GUI LiteGraph workflow into the running ComfyUI canvas.
     *
     * @param entry the model entry
     * @param requiredModels list of required model infos
     * @param userPrompt custom user prompt text
     * @param missingModels list of missing model files (must be empty to proceed)
     * @return future resolving to true if successfully received
     */
    public CompletableFuture<Boolean> sendWorkflowToComfyUI(
            CivitaiModelEntry entry,
            List<ModelInfo> requiredModels,
            String userPrompt,
            List<ModelInfo> missingModels) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!missingModels.isEmpty()) {
                    List<String> missingNames = missingModels.stream()
                            .map(m -> m.getFilename() != null && !m.getFilename().isBlank() ? m.getFilename() : m.getName())
                            .toList();
                    throw new IllegalStateException("Cannot send workflow because required model files are missing on disk: " + missingNames);
                }

                ensureComfyUiHealthy();

                String comfyUrl = getSanitizedComfyUrl();
                triggerModelRefresh(comfyUrl);

                ObjectNode guiGraphNode = guiWorkflowBuilder.buildGuiWorkflow(entry, requiredModels, userPrompt);
                saveWorkflowToUserWorkflows(entry, guiGraphNode);

                ObjectNode bridgePayload = objectMapper.createObjectNode();
                bridgePayload.set("workflow", guiGraphNode);

                HttpRequest bridgeReq = HttpRequest.newBuilder()
                        .uri(URI.create(comfyUrl + "/cmfc/load-workflow"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(bridgePayload.toString()))
                        .timeout(Duration.ofSeconds(10))
                        .build();

                HttpResponse<String> response = httpClient.send(bridgeReq, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    log.info("Successfully loaded GUI workflow for '{}' into ComfyUI canvas via bridge.", entry.name());
                    return true;
                } else {
                    log.warn("ComfyUI bridge rejected /cmfc/load-workflow (HTTP {}): {}", response.statusCode(), response.body());
                    throw new IOException("ComfyUI bridge returned HTTP " + response.statusCode() + ": " + response.body());
                }
            } catch (Exception e) {
                log.error("Failed sending GUI workflow to ComfyUI: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, runnable -> Thread.ofVirtual().name("Civitai-SendWorkflowWorker").start(runnable));
    }

    /**
     * Submits an API prompt workflow to ComfyUI and polls for the finished output.
     *
     * @param entry the model entry
     * @param workflowJson the API workflow JSON string
     * @return future with the generation result
     */
    public CompletableFuture<CivitaiGenerationResult> sendToComfyUIAndGenerate(
            CivitaiModelEntry entry,
            String workflowJson) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ensureComfyUiHealthy();

                String comfyUrl = getSanitizedComfyUrl();

                // Optional canvas sync
                try {
                    JsonNode parsedTree = objectMapper.readTree(workflowJson);
                    ObjectNode bridgePayload = objectMapper.createObjectNode();
                    bridgePayload.set("workflow", parsedTree);

                    HttpRequest bridgeReq = HttpRequest.newBuilder()
                            .uri(URI.create(comfyUrl + "/cmfc/load-workflow"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(bridgePayload.toString()))
                            .timeout(Duration.ofSeconds(6))
                            .build();
                    httpClient.send(bridgeReq, HttpResponse.BodyHandlers.ofString());
                } catch (Exception bridgeEx) {
                    log.debug("Bridge canvas sync bypassed: {}", bridgeEx.getMessage());
                }

                HttpResponse<String> promptResponse = comfyApiClient.postPrompt(comfyUrl, workflowJson);
                if (promptResponse == null || promptResponse.statusCode() < 200 || promptResponse.statusCode() >= 300) {
                    int status = promptResponse != null ? promptResponse.statusCode() : 0;
                    String body = promptResponse != null ? promptResponse.body() : "No response";
                    throw new IOException("ComfyUI /prompt rejected submission (HTTP " + status + "): " + body);
                }

                JsonNode resNode = objectMapper.readTree(promptResponse.body());
                String promptId = resNode.path("prompt_id").asText("");
                log.info("Successfully queued Civitai generation with prompt_id {} for {}", promptId, entry.name());

                ComfyApiClient.ImageOutput imgOutput = pollForOutput(comfyUrl, promptId);

                if (imgOutput != null) {
                    String imgUrl = comfyUrl + "/view?filename=" + imgOutput.filename() + "&subfolder=" + imgOutput.subfolder() + "&type=" + imgOutput.type();
                    log.info("Civitai generation finished successfully: {}", imgOutput.filename());
                    return CivitaiGenerationResult.success(promptId, imgOutput.filename(), imgOutput.subfolder(), imgOutput.type(), imgUrl);
                }

                return CivitaiGenerationResult.success(promptId, null, null, null, null);

            } catch (Exception e) {
                log.error("Failed sending workflow to ComfyUI: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, runnable -> Thread.ofVirtual().name("Civitai-SendComfyWorker").start(runnable));
    }

    private String getSanitizedComfyUrl() {
        String comfyUrl = configService != null ? configService.getComfyUIUrl() : "http://localhost:8188";
        if (comfyUrl.endsWith("/")) {
            comfyUrl = comfyUrl.substring(0, comfyUrl.length() - 1);
        }
        return comfyUrl;
    }

    private void triggerModelRefresh(String comfyUrl) {
        try {
            HttpRequest refreshReq = HttpRequest.newBuilder()
                    .uri(URI.create(comfyUrl + "/cmfc/refresh-models"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"force_reload\":false}"))
                    .timeout(Duration.ofSeconds(5))
                    .build();
            httpClient.send(refreshReq, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ex) {
            log.debug("ComfyUI model refresh notification skipped: {}", ex.getMessage());
        }
    }

    private void saveWorkflowToUserWorkflows(CivitaiModelEntry entry, ObjectNode workflow) {
        try {
            Path userWorkflowsDir = Path.of("user_workflows");
            if (!Files.exists(userWorkflowsDir)) {
                Files.createDirectories(userWorkflowsDir);
            }
            String safeName = entry.name()
                    .toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9_-]+", "_")
                    .replaceAll("_+", "_");
            Path targetFile = userWorkflowsDir.resolve("model_" + safeName + ".json");
            Files.writeString(targetFile, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(workflow));
        } catch (Exception ex) {
            log.warn("Could not save workflow backup copy to user_workflows: {}", ex.getMessage());
        }
    }

    private ComfyApiClient.ImageOutput pollForOutput(String comfyUrl, String promptId) throws InterruptedException, IOException {
        int maxAttempts = 180;
        for (int i = 0; i < maxAttempts; i++) {
            ComfyApiClient.ImageOutput imgOutput = comfyApiClient.fetchPromptHistoryImage(comfyUrl, promptId);
            if (imgOutput != null) {
                return imgOutput;
            }
            Thread.sleep(500);
        }
        return null;
    }

    private void ensureComfyUiHealthy() throws Exception {
        if (lifecycleService == null) return;
        if (!lifecycleService.isHealthy()) {
            log.info("ComfyUI is not currently healthy/running. Requesting boot and awaiting readiness...");
            lifecycleService.start();
            int maxWaitSeconds = 90;
            boolean healthy = false;
            for (int w = 0; w < maxWaitSeconds; w++) {
                if (lifecycleService.isHealthy()) {
                    healthy = true;
                    break;
                }
                Thread.sleep(1000);
            }
            if (!healthy) {
                throw new IOException("ComfyUI server could not be started or did not reach a healthy state within 90s.");
            }
            log.info("ComfyUI server is now running and healthy. Proceeding with workflow dispatch.");
        }
    }
}
