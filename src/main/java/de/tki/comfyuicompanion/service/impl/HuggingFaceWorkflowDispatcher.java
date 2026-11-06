package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tki.comfyuicompanion.domain.CivitaiGenerationResult;
import de.tki.comfyuicompanion.domain.HuggingFaceFileEntry;
import de.tki.comfyuicompanion.domain.HuggingFaceModelEntry;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.dto.acl.RefreshModelsAclRequest;
import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import org.json.JSONObject;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Dispatcher responsible for sending Hugging Face workflows (either real repository workflow JSONs
 * or synthesized ComfyUI workflows) to the ComfyUI canvas via REST bridge or direct `/prompt` queue execution.
 */
@Component
public class HuggingFaceWorkflowDispatcher {

    private static final Logger log = LoggerFactory.getLogger(HuggingFaceWorkflowDispatcher.class);

    private final ConfigService configService;
    private final IComfyLifecycleService lifecycleService;
    private final ComfyApiClient comfyApiClient;
    private final HuggingFaceWorkflowBuilder workflowBuilder;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /**
     * Constructs a new HuggingFaceWorkflowDispatcher with required dependencies.
     *
     * @param configService    configuration service
     * @param lifecycleService ComfyUI lifecycle service
     * @param comfyApiClient   client for ComfyUI REST endpoints
     * @param workflowBuilder  builder for HF workflows
     * @param objectMapper     JSON object mapper
     */
    @Autowired
    public HuggingFaceWorkflowDispatcher(
            ConfigService configService,
            @Autowired(required = false) IComfyLifecycleService lifecycleService,
            ComfyApiClient comfyApiClient,
            HuggingFaceWorkflowBuilder workflowBuilder,
            ObjectMapper objectMapper) {
        this.configService = configService;
        this.lifecycleService = lifecycleService;
        this.comfyApiClient = comfyApiClient;
        this.workflowBuilder = workflowBuilder != null ? workflowBuilder : new HuggingFaceWorkflowBuilder(objectMapper);
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(10))
                .executor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor())
                .build();
    }

    /**
     * Sends a Hugging Face workflow into the running ComfyUI canvas, or falls back to
     * direct `/prompt` execution if the canvas bridge is unavailable (matching Comfy.org Blueprints).
     *
     * @param repo         the Hugging Face repository entry
     * @param selectedFile the selected model or workflow file
     * @param userPrompt   custom positive prompt text
     * @return future resolving to true upon successful delivery or execution queueing
     */
    public CompletableFuture<Boolean> sendWorkflowToComfyUI(
            HuggingFaceModelEntry repo,
            HuggingFaceFileEntry selectedFile,
            String userPrompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ensureComfyUiHealthy();

                String comfyUrl = getSanitizedComfyUrl();
                triggerModelRefresh(comfyUrl);

                HuggingFaceFileEntry effectiveFile = resolveEffectiveModelFile(repo, selectedFile);
                ObjectNode workflowNode = resolveOrSynthesizeWorkflow(repo, selectedFile, effectiveFile, userPrompt);
                ComfyWorkflowCompatibilitySanitizer sanitizer = new ComfyWorkflowCompatibilitySanitizer(objectMapper);
                sanitizer.sanitizeWorkflow(workflowNode, sanitizer.fetchRegisteredNodes(comfyUrl, httpClient));
                saveWorkflowToUserWorkflows(repo, effectiveFile, workflowNode);

                boolean isUiFormat = workflowNode.has("nodes") || workflowNode.has("last_node_id");
                if (isUiFormat) {
                    try {
                        ObjectNode bridgePayload = objectMapper.createObjectNode();
                        bridgePayload.set("workflow", workflowNode);

                        HttpRequest bridgeReq = HttpRequest.newBuilder()
                                .uri(URI.create(comfyUrl + "/cmfc/load-workflow"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(bridgePayload.toString()))
                                .timeout(Duration.ofSeconds(10))
                                .build();

                        HttpResponse<String> response = httpClient.send(bridgeReq, HttpResponse.BodyHandlers.ofString());
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            log.info("Successfully loaded Hugging Face workflow for '{}' into ComfyUI canvas.", repo.repoId());
                            return true;
                        }
                        log.warn("ComfyUI bridge returned HTTP {} for /cmfc/load-workflow; falling back to /prompt.", response.statusCode());
                    } catch (Exception bridgeEx) {
                        log.warn("ComfyUI bridge request failed ({}), attempting direct /prompt fallback.", bridgeEx.getMessage());
                    }
                }

                // Fallback: convert UI workflow to API prompt (or use synthesized API workflow) and submit to /prompt
                String promptPayloadJson = buildPromptPayloadForFallback(repo, effectiveFile, userPrompt, workflowNode);
                HttpResponse<String> promptResp = submitPromptPayload(comfyUrl, promptPayloadJson);
                if (promptResp != null && promptResp.statusCode() >= 200 && promptResp.statusCode() < 300) {
                    log.info("Successfully queued Hugging Face workflow for '{}' via ComfyUI /prompt fallback.", repo.repoId());
                    return true;
                }
                int code = promptResp != null ? promptResp.statusCode() : 0;
                String body = promptResp != null ? promptResp.body() : "No response";
                throw new IOException("ComfyUI rejected workflow submission (HTTP " + code + "): " + body);
            } catch (Exception e) {
                log.error("Failed sending HF workflow to ComfyUI: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, runnable -> Thread.ofVirtual().name("HF-SendWorkflowWorker").start(runnable));
    }

    /**
     * Submits an API prompt workflow to ComfyUI and polls for the finished output.
     *
     * @param repo         the model entry
     * @param selectedFile the selected model file
     * @param userPrompt   custom positive prompt
     * @return future with the generation result
     */
    public CompletableFuture<CivitaiGenerationResult> sendToComfyUIAndGenerate(
            HuggingFaceModelEntry repo,
            HuggingFaceFileEntry selectedFile,
            String userPrompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ensureComfyUiHealthy();

                String comfyUrl = getSanitizedComfyUrl();
                HuggingFaceFileEntry effectiveFile = resolveEffectiveModelFile(repo, selectedFile);
                ObjectNode workflowNode = resolveOrSynthesizeWorkflow(repo, selectedFile, effectiveFile, userPrompt);
                ComfyWorkflowCompatibilitySanitizer sanitizer = new ComfyWorkflowCompatibilitySanitizer(objectMapper);
                sanitizer.sanitizeWorkflow(workflowNode, sanitizer.fetchRegisteredNodes(comfyUrl, httpClient));

                // Optional canvas sync
                if (workflowNode.has("nodes") || workflowNode.has("last_node_id")) {
                    try {
                        ObjectNode bridgePayload = objectMapper.createObjectNode();
                        bridgePayload.set("workflow", workflowNode);

                        HttpRequest bridgeReq = HttpRequest.newBuilder()
                                .uri(URI.create(comfyUrl + "/cmfc/load-workflow"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(bridgePayload.toString()))
                                .timeout(Duration.ofSeconds(6))
                                .build();
                        httpClient.send(bridgeReq, HttpResponse.BodyHandlers.ofString());
                    } catch (Exception bridgeEx) {
                        log.debug("Bridge canvas sync bypassed for HF generation: {}", bridgeEx.getMessage());
                    }
                }

                String workflowJson = buildPromptPayloadForFallback(repo, effectiveFile, userPrompt, workflowNode);
                HttpResponse<String> promptResponse = submitPromptPayload(comfyUrl, workflowJson);
                if (promptResponse == null || promptResponse.statusCode() < 200 || promptResponse.statusCode() >= 300) {
                    int status = promptResponse != null ? promptResponse.statusCode() : 0;
                    String body = promptResponse != null ? promptResponse.body() : "No response";
                    throw new IOException("ComfyUI /prompt rejected submission (HTTP " + status + "): " + body);
                }

                JsonNode resNode = objectMapper.readTree(promptResponse.body());
                String promptId = resNode.path("prompt_id").asText("");
                log.info("Successfully queued HF generation with prompt_id {} for {}", promptId, effectiveFile.filename());

                ComfyApiClient.ImageOutput imgOutput = pollForOutput(comfyUrl, promptId);

                if (imgOutput != null) {
                    String imgUrl = comfyUrl + "/view?filename=" + imgOutput.filename() + "&subfolder=" + imgOutput.subfolder() + "&type=" + imgOutput.type();
                    log.info("HF generation finished successfully: {}", imgOutput.filename());
                    return CivitaiGenerationResult.success(promptId, imgOutput.filename(), imgOutput.subfolder(), imgOutput.type(), imgUrl);
                }

                return CivitaiGenerationResult.success(promptId, null, null, null, null);
            } catch (Exception e) {
                log.error("Failed executing HF generation in ComfyUI: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, runnable -> Thread.ofVirtual().name("HF-SendComfyWorker").start(runnable));
    }

    /**
     * Returns the configured and sanitized ComfyUI server base URL.
     *
     * @return sanitized ComfyUI URL
     */
    public String getSanitizedComfyUrl() {
        String comfyUrl = configService != null ? configService.getComfyUIUrl() : "http://127.0.0.1:8188";
        if (comfyUrl == null || comfyUrl.isBlank()) {
            comfyUrl = "http://127.0.0.1:8188";
        }
        if (comfyUrl.endsWith("/")) {
            comfyUrl = comfyUrl.substring(0, comfyUrl.length() - 1);
        }
        return comfyUrl;
    }

    private HuggingFaceFileEntry resolveEffectiveModelFile(HuggingFaceModelEntry repo, HuggingFaceFileEntry selectedFile) {
        if (selectedFile != null && !HuggingFaceWorkflowAnalyzer.isWorkflowFile(selectedFile.rfilename())) {
            return selectedFile;
        }
        if (repo != null && repo.files() != null) {
            for (HuggingFaceFileEntry f : repo.files()) {
                if (!HuggingFaceWorkflowAnalyzer.isWorkflowFile(f.rfilename())) {
                    return f;
                }
            }
        }
        if (selectedFile != null) {
            return selectedFile;
        }
        String fallbackName = (repo != null ? repo.modelName() : "model") + ".safetensors";
        return new HuggingFaceFileEntry(fallbackName, fallbackName, 0L, "Unknown", "", "checkpoints", "Standard", false);
    }

    private ObjectNode resolveOrSynthesizeWorkflow(
            HuggingFaceModelEntry repo,
            HuggingFaceFileEntry selectedFile,
            HuggingFaceFileEntry effectiveModelFile,
            String userPrompt) {
        String hfToken = configService != null ? configService.getHfToken() : null;
        Optional<ObjectNode> remoteWorkflow = HuggingFaceWorkflowAnalyzer.tryFetchRemoteWorkflowJson(
                repo, selectedFile, httpClient, hfToken, objectMapper);
        if (remoteWorkflow.isPresent()) {
            ObjectNode realNode = remoteWorkflow.get();
            injectCustomPromptIfPresent(realNode, userPrompt);
            adaptModelReferencesToLocalInstall(realNode, repo);
            return realNode;
        }
        return workflowBuilder.buildGuiWorkflow(repo, effectiveModelFile, userPrompt);
    }

    /**
     * Maps the repository's model files onto locally installed ones and splits missing
     * All-in-One checkpoints into UNET/CLIP/VAE loaders, mirroring the Civitai behavior.
     */
    private void adaptModelReferencesToLocalInstall(ObjectNode workflow, HuggingFaceModelEntry repo) {
        if (repo == null || repo.files() == null || repo.files().isEmpty() || configService == null) {
            return;
        }
        try {
            List<ModelInfo> models = new ArrayList<>();
            for (HuggingFaceFileEntry f : repo.files()) {
                if (f.suggestedFolder() == null || HuggingFaceWorkflowAnalyzer.isWorkflowFile(f.rfilename())) {
                    continue;
                }
                ModelInfo info = new ModelInfo(f.suggestedFolder(), f.filename(), f.downloadUrl());
                info.setFilename(f.filename());
                models.add(info);
            }
            new CivitaiWorkflowFileResolver(objectMapper, configService, null).adaptModelReferences(workflow, models);
        } catch (Exception ex) {
            log.debug("Model reference adaptation skipped for {}: {}", repo.repoId(), ex.getMessage());
        }
    }

    private void injectCustomPromptIfPresent(ObjectNode workflowNode, String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank() || !workflowNode.has("nodes")) {
            return;
        }
        JsonNode nodes = workflowNode.get("nodes");
        if (!nodes.isArray()) return;
        for (JsonNode n : nodes) {
            String type = n.path("type").asText("");
            if ("CLIPTextEncode".equalsIgnoreCase(type) && n.has("widgets_values") && n.get("widgets_values").isArray()) {
                ArrayNode wv = (ArrayNode) n.get("widgets_values");
                if (!wv.isEmpty() && wv.get(0).isTextual()) {
                    String current = wv.get(0).asText("");
                    if (!current.toLowerCase(Locale.ROOT).contains("ugly")
                            && !current.toLowerCase(Locale.ROOT).contains("bad hands")
                            && !current.toLowerCase(Locale.ROOT).contains("worst quality")) {
                        wv.set(0, userPrompt.trim());
                        break;
                    }
                }
            }
        }
    }

    private String buildPromptPayloadForFallback(
            HuggingFaceModelEntry repo,
            HuggingFaceFileEntry effectiveFile,
            String userPrompt,
            ObjectNode workflowNode) {
        try {
            JSONObject parsedObj = new JSONObject(workflowNode.toString());
            if (parsedObj.has("nodes")) {
                JSONObject flattened = ComfyPipelineService.flattenWorkflow(parsedObj);
                JSONObject apiPayload = ComfyPipelineService.convertUiToApi(flattened);
                if (apiPayload.has("prompt") && apiPayload.getJSONObject("prompt").length() > 0) {
                    JSONObject wrapper = new JSONObject();
                    wrapper.put("prompt", apiPayload.getJSONObject("prompt"));
                    return wrapper.toString();
                }
            } else if (parsedObj.has("prompt")) {
                return parsedObj.toString();
            } else if (!parsedObj.isEmpty()) {
                JSONObject wrapper = new JSONObject();
                wrapper.put("prompt", parsedObj);
                return wrapper.toString();
            }
        } catch (Exception ex) {
            log.debug("UI-to-API conversion fallback triggered synthesized API workflow: {}", ex.getMessage());
        }
        return workflowBuilder.buildApiWorkflowJson(repo, effectiveFile, userPrompt);
    }

    private HttpResponse<String> submitPromptPayload(String comfyUrl, String promptPayloadJson) throws IOException, InterruptedException {
        if (comfyApiClient != null) {
            return comfyApiClient.postPrompt(comfyUrl, promptPayloadJson);
        }
        HttpRequest promptReq = HttpRequest.newBuilder()
                .uri(URI.create(comfyUrl + "/prompt"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(promptPayloadJson))
                .timeout(Duration.ofSeconds(15))
                .build();
        return httpClient.send(promptReq, HttpResponse.BodyHandlers.ofString());
    }

    private void triggerModelRefresh(String comfyUrl) {
        try {
            HttpRequest refreshReq = HttpRequest.newBuilder()
                    .uri(URI.create(comfyUrl + "/cmfc/refresh-models"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(RefreshModelsAclRequest.NORMAL.toJson()))
                    .timeout(Duration.ofSeconds(5))
                    .build();
            httpClient.send(refreshReq, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ex) {
            log.debug("ComfyUI model refresh notification skipped: {}", ex.getMessage());
        }
    }

    private void saveWorkflowToUserWorkflows(HuggingFaceModelEntry repo, HuggingFaceFileEntry file, ObjectNode workflow) {
        try {
            Path userWorkflowsDir = Path.of("user_workflows");
            if (!Files.exists(userWorkflowsDir)) {
                Files.createDirectories(userWorkflowsDir);
            }
            String safeName = (repo.modelName() + "_" + file.filename())
                    .toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9_-]+", "_")
                    .replaceAll("_+", "_");
            Path targetFile = userWorkflowsDir.resolve("hf_" + safeName + ".json");
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
            try {
                Thread.sleep(500);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private void ensureComfyUiHealthy() throws Exception {
        if (lifecycleService == null) return;
        if (!lifecycleService.isHealthy()) {
            log.info("ComfyUI is not currently healthy/running. Requesting boot and awaiting readiness...");
            lifecycleService.start();
            int maxWaitSeconds = 90;
            for (int i = 0; i < maxWaitSeconds; i++) {
                if (lifecycleService.isHealthy()) {
                    log.info("ComfyUI is now healthy and ready.");
                    return;
                }
                Thread.sleep(1000);
            }
            throw new IllegalStateException("ComfyUI failed to become healthy within " + maxWaitSeconds + " seconds.");
        }
    }
}
