package de.tki.comfyuicompanion.ui.blueprint;

import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import de.tki.comfyuicompanion.service.IWorkflowDownloader;
import de.tki.comfyuicompanion.service.impl.ComfyPipelineService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintEntry;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Handles asynchronous downloading of blueprint workflows, ensuring ComfyUI is running,
 * and dispatching workflows to the ComfyUI canvas bridge or execution prompt API.
 */
public final class BlueprintComfySender {

    private static final Logger logger = LoggerFactory.getLogger(BlueprintComfySender.class);
    private static final HttpClient SHARED_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private BlueprintComfySender() {
        // Utility constructor
    }

    /**
     * Executes the workflow download and dispatch to ComfyUI.
     *
     * @param entry the blueprint entry
     * @param workflowDownloader downloader service
     * @param lifecycleService ComfyUI lifecycle service
     * @param configService configuration service
     * @param statusUpdater callback to update button text or progress
     * @param parentComponent UI parent for dialog alerts
     * @param onComplete callback on completion with success flag
     */
    public static void sendToComfyUI(
            BlueprintEntry entry,
            IWorkflowDownloader workflowDownloader,
            IComfyLifecycleService lifecycleService,
            ConfigService configService,
            Consumer<String> statusUpdater,
            Component parentComponent,
            Consumer<Boolean> onComplete) {

        if (statusUpdater != null) statusUpdater.accept("Downloading...");

        workflowDownloader.downloadWorkflowAsync(entry.registryWorkflow)
                .thenAccept(file -> {
                    try {
                        if (lifecycleService != null && !lifecycleService.isHealthy()) {
                            if (statusUpdater != null) SwingUtilities.invokeLater(() -> statusUpdater.accept("Starting ComfyUI..."));
                            logger.info("🔌 [BlueprintComfySender] ComfyUI server is offline. Starting lifecycle service...");
                            lifecycleService.start();
                            int maxWait = 90;
                            boolean healthy = false;
                            for (int w = 0; w < maxWait; w++) {
                                if (lifecycleService.isHealthy()) {
                                    healthy = true;
                                    break;
                                }
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                            if (!healthy) {
                                throw new Exception("ComfyUI server did not start within 90 seconds.");
                            }
                        }

                        if (statusUpdater != null) SwingUtilities.invokeLater(() -> statusUpdater.accept("Sending..."));
                        String jsonContent = Files.readString(file.toPath());
                        boolean isUIFormatDetected = false;
                        JSONObject parsedObj = null;
                        try {
                            parsedObj = new JSONObject(jsonContent);
                            if (parsedObj.has("nodes") || parsedObj.has("last_node_id")) {
                                isUIFormatDetected = true;
                            }
                        } catch (Exception ignored) {
            logger.debug("Ignored non-fatal exception: {}", ignored.getMessage());
        }

                        String comfyUrl = configService.getComfyUIUrl();
                        if (comfyUrl.endsWith("/")) {
                            comfyUrl = comfyUrl.substring(0, comfyUrl.length() - 1);
                        }

                        HttpClient client = SHARED_CLIENT;
                        HttpResponse<String> response = null;
                        boolean loadedViaBridge = false;

                        if (isUIFormatDetected && parsedObj != null) {
                            try {
                                JSONObject bridgePayload = new JSONObject();
                                bridgePayload.put("workflow", parsedObj);

                                HttpRequest bridgeRequest = HttpRequest.newBuilder()
                                        .uri(URI.create(comfyUrl + "/cmfc/load-workflow"))
                                        .header("Content-Type", "application/json")
                                        .POST(HttpRequest.BodyPublishers.ofString(bridgePayload.toString()))
                                        .build();
                                response = client.send(bridgeRequest, HttpResponse.BodyHandlers.ofString());
                                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                                    loadedViaBridge = true;
                                }
                            } catch (Exception bridgeEx) {
                                logger.warn("ComfyUI bridge request failed, attempting direct prompt queue fallback: {}", bridgeEx.getMessage());
                            }
                        }

                        if (!loadedViaBridge && (response == null || response.statusCode() == 404 || response.statusCode() == 405 || !isUIFormatDetected)) {
                            JSONObject promptPayload = new JSONObject();
                            if (parsedObj != null) {
                                if (parsedObj.has("nodes")) {
                                    JSONObject flattened = ComfyPipelineService.flattenWorkflow(parsedObj);
                                    JSONObject apiPayload = ComfyPipelineService.convertUiToApi(flattened);
                                    promptPayload.put("prompt", apiPayload.getJSONObject("prompt"));
                                } else if (parsedObj.has("prompt")) {
                                    promptPayload.put("prompt", parsedObj.get("prompt"));
                                } else {
                                    promptPayload.put("prompt", parsedObj);
                                }
                            } else {
                                promptPayload.put("prompt", new JSONObject(jsonContent));
                            }

                            HttpRequest promptRequest = HttpRequest.newBuilder()
                                    .uri(URI.create(comfyUrl + "/prompt"))
                                    .header("Content-Type", "application/json")
                                    .POST(HttpRequest.BodyPublishers.ofString(promptPayload.toString()))
                                    .build();
                            response = client.send(promptRequest, HttpResponse.BodyHandlers.ofString());
                        }

                        HttpResponse<String> finalResp = response;
                        boolean finalLoaded = loadedViaBridge;
                        SwingUtilities.invokeLater(() -> {
                            if (finalLoaded || (finalResp != null && finalResp.statusCode() >= 200 && finalResp.statusCode() < 300)) {
                                JOptionPane.showMessageDialog(parentComponent,
                                        finalLoaded ? "Workflow loaded directly into ComfyUI canvas!"
                                                : "Workflow queued successfully in ComfyUI!",
                                        "ComfyUI Companion", JOptionPane.INFORMATION_MESSAGE);
                                if (onComplete != null) onComplete.accept(true);
                            } else {
                                String msg = finalResp != null ? finalResp.body() : "No response from ComfyUI.";
                                JOptionPane.showMessageDialog(parentComponent,
                                        "ComfyUI rejected workflow:\n" + msg,
                                        "ComfyUI Error", JOptionPane.ERROR_MESSAGE);
                                if (onComplete != null) onComplete.accept(false);
                            }
                        });

                    } catch (Exception ex) {
                        logger.error("Failed to send workflow to ComfyUI", ex);
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(parentComponent,
                                    "Failed to execute workflow: " + ex.getMessage(),
                                    "Execution Error", JOptionPane.ERROR_MESSAGE);
                            if (onComplete != null) onComplete.accept(false);
                        });
                    }
                })
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(parentComponent,
                                "Failed to download workflow: " + ex.getCause().getMessage(),
                                "Download Error", JOptionPane.ERROR_MESSAGE);
                        if (onComplete != null) onComplete.accept(false);
                    });
                    return null;
                });
    }
}
