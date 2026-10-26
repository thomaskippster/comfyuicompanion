package de.tki.comfyuicompanion.controller;

import de.tki.comfyuicompanion.service.impl.ComfyApiClient;
import de.tki.comfyuicompanion.service.impl.ComfyWebSocketClient;
import de.tki.comfyuicompanion.ui.PromptLabView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.Image;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Coordinates real-time WebSocket progress events and fallback HTTP history polling
 * for queued ComfyUI prompts in the Image Lab.
 */
public class PromptLabStatusPoller {
    private static final Logger logger = LoggerFactory.getLogger(PromptLabStatusPoller.class);

    private final ComfyApiClient comfyApiClient;
    private final ComfyWebSocketClient webSocketClient;
    private final ExecutorService backgroundExecutor;
    private final Consumer<Image> onPreviewImageChanged;

    /**
     * Constructs a new PromptLabStatusPoller.
     *
     * @param comfyApiClient        the ComfyUI HTTP API client
     * @param webSocketClient       the ComfyUI WebSocket client for real-time updates
     * @param backgroundExecutor    executor for asynchronous polling tasks
     * @param onPreviewImageChanged callback invoked when a latent preview image is received
     */
    public PromptLabStatusPoller(ComfyApiClient comfyApiClient,
                                 ComfyWebSocketClient webSocketClient,
                                 ExecutorService backgroundExecutor,
                                 Consumer<Image> onPreviewImageChanged) {
        this.comfyApiClient = comfyApiClient;
        this.webSocketClient = webSocketClient != null ? webSocketClient : new ComfyWebSocketClient();
        this.backgroundExecutor = backgroundExecutor;
        this.onPreviewImageChanged = onPreviewImageChanged;
    }

    /**
     * Returns the WebSocket client ID to include in prompt submissions.
     *
     * @return the client ID string
     */
    public String getClientId() {
        return webSocketClient.getClientId();
    }

    /**
     * Monitors the execution status of a queued prompt using real-time WebSocket events
     * combined with HTTP history polling until the final output image is available.
     *
     * @param comfyUrl       the ComfyUI server URL
     * @param promptId       the queued prompt identifier
     * @param promptLabView  the Image Lab view to update
     * @param onImageReady   callback accepting the completed ImageOutput
     */
    public void pollPromptStatus(String comfyUrl,
                                 String promptId,
                                 PromptLabView promptLabView,
                                 Consumer<ComfyApiClient.ImageOutput> onImageReady) {
        if (promptLabView == null) return;

        SwingUtilities.invokeLater(() -> {
            if (promptLabView.getPromptLabRightTabbedPane() != null) {
                promptLabView.getPromptLabRightTabbedPane().setSelectedIndex(0);
            }
            JProgressBar bar = promptLabView.getPromptLabProgressBar();
            if (bar != null) {
                bar.setVisible(true);
                bar.setIndeterminate(true);
                bar.setString("Queued...");
            }
            if (promptLabView.getPromptImagePreviewLabel() != null) {
                promptLabView.getPromptImagePreviewLabel().setText("Generating image... Please wait.");
                promptLabView.getPromptImagePreviewLabel().setIcon(null);
            }
        });

        backgroundExecutor.execute(() -> {
            AtomicBoolean wsReceivedStepProgress = new AtomicBoolean(false);
            AutoCloseable wsSession = webSocketClient.connect(
                    comfyUrl,
                    promptId,
                    (step, max) -> {
                        wsReceivedStepProgress.set(true);
                        int pct = max > 0 ? (int) Math.round((step * 100.0) / max) : 0;
                        SwingUtilities.invokeLater(() -> {
                            JProgressBar bar = promptLabView.getPromptLabProgressBar();
                            if (bar != null) {
                                bar.setIndeterminate(false);
                                bar.setMaximum(max);
                                bar.setValue(Math.min(step, max));
                                bar.setString("Step " + step + " / " + max + " (" + pct + "%)");
                            }
                        });
                    },
                    nodeId -> {
                        if (nodeId != null && !wsReceivedStepProgress.get()) {
                            SwingUtilities.invokeLater(() -> {
                                JProgressBar bar = promptLabView.getPromptLabProgressBar();
                                if (bar != null) {
                                    bar.setString("Executing Node #" + nodeId + "...");
                                }
                            });
                        }
                    },
                    previewImg -> {
                        if (onPreviewImageChanged != null) {
                            SwingUtilities.invokeLater(() -> onPreviewImageChanged.accept(previewImg));
                        }
                    }
            );

            try {
                boolean done = false;
                int pollAttempts = 0;

                while (!done && pollAttempts < 180) {
                    try {
                        Thread.sleep(1000);
                        pollAttempts++;

                        ComfyApiClient.ImageOutput imgOutput = comfyApiClient.fetchPromptHistoryImage(comfyUrl, promptId);
                        if (imgOutput != null) {
                            SwingUtilities.invokeLater(() -> {
                                if (onImageReady != null) {
                                    onImageReady.accept(imgOutput);
                                }
                                if (promptLabView.getPromptLabProgressBar() != null) {
                                    promptLabView.getPromptLabProgressBar().setVisible(false);
                                }
                                if (promptLabView.getPromptLabConsole() != null) {
                                    promptLabView.getPromptLabConsole().append("Image generated and loaded successfully!\n\n");
                                }
                            });
                            done = true;
                            break;
                        }

                        if (!done && !wsReceivedStepProgress.get()) {
                            ComfyApiClient.QueueInfo qInfo = comfyApiClient.fetchQueueStatus(comfyUrl, promptId);
                            if (qInfo.state() == ComfyApiClient.QueueState.RUNNING) {
                                SwingUtilities.invokeLater(() -> {
                                    if (promptLabView.getPromptLabProgressBar() != null) {
                                        promptLabView.getPromptLabProgressBar().setString("Generating...");
                                    }
                                });
                            } else if (qInfo.state() == ComfyApiClient.QueueState.PENDING) {
                                final int pos = qInfo.position();
                                SwingUtilities.invokeLater(() -> {
                                    if (promptLabView.getPromptLabProgressBar() != null) {
                                        promptLabView.getPromptLabProgressBar().setString("Queued (Position: " + pos + ")");
                                    }
                                });
                            }
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception ex) {
                        logger.error("Error polling history: {}", ex.getMessage());
                    }
                }

                if (!done) {
                    SwingUtilities.invokeLater(() -> {
                        if (promptLabView.getPromptLabProgressBar() != null) {
                            promptLabView.getPromptLabProgressBar().setVisible(false);
                        }
                        if (promptLabView.getPromptImagePreviewLabel() != null) {
                            promptLabView.getPromptImagePreviewLabel().setText("Timed out waiting for image generation.");
                        }
                    });
                }
            } finally {
                try {
                    wsSession.close();
                } catch (Exception ignored) {
            logger.debug("Ignored non-fatal exception: {}", ignored.getMessage());
        }
            }
        });
    }

    /**
     * Opens the interactive Prompt Matrix XY-Grid dialog and executes matrix cells sequentially.
     *
     * @param parent           parent component
     * @param view             the PromptLabView instance
     * @param comfyUrl         ComfyUI server base URL
     * @param controller       the PromptLabController instance
     * @param sanitizer        the model input sanitizer
     * @param autoStartRun     whether to immediately trigger a matrix run on open
     */
    public void openMatrixDialog(java.awt.Component parent,
                                 PromptLabView view,
                                 String comfyUrl,
                                 PromptLabController controller,
                                 PromptLabModelInputSanitizer sanitizer,
                                 boolean autoStartRun) {
        openMatrixDialog(parent, view, comfyUrl, controller, sanitizer, autoStartRun, null);
    }

    /**
     * Opens the interactive Prompt Matrix XY-Grid dialog and executes matrix cells sequentially,
     * automatically starting ComfyUI if it is currently offline.
     *
     * @param parent           parent component
     * @param view             the PromptLabView instance
     * @param comfyUrl         ComfyUI server base URL
     * @param controller       the PromptLabController instance
     * @param sanitizer        the model input sanitizer
     * @param autoStartRun     whether to immediately trigger a matrix run on open
     * @param comfyConnector   callback that ensures ComfyUI is online (auto-starting if needed) and reports progress
     */
    public void openMatrixDialog(java.awt.Component parent,
                                 PromptLabView view,
                                 String comfyUrl,
                                 PromptLabController controller,
                                 PromptLabModelInputSanitizer sanitizer,
                                 boolean autoStartRun,
                                 java.util.function.Function<Consumer<String>, Boolean> comfyConnector) {
        if (view == null) return;
        String initPrompt = view.getPromptSubjectField() != null ? view.getPromptSubjectField().getText() : "";
        double defCfg = view.getPromptCfgSpinner() != null ? ((Number) view.getPromptCfgSpinner().getValue()).doubleValue() : 7.0;
        if (view.getPromptJsonArea() != null && view.getPromptJsonArea().getText() != null
                && view.getPromptJsonArea().getText().contains("ConditioningZeroOut")) {
            defCfg = 1.0;
        }
        int defSteps = view.getPromptStepsSpinner() != null ? (int) view.getPromptStepsSpinner().getValue() : 20;

        java.awt.Window owner = parent != null ? SwingUtilities.getWindowAncestor(parent) : SwingUtilities.getWindowAncestor(view);
        final de.tki.comfyuicompanion.ui.dialog.PromptMatrixGridDialog[] dlgHolder = new de.tki.comfyuicompanion.ui.dialog.PromptMatrixGridDialog[1];
        java.util.function.BiConsumer<java.util.List<PromptLabMatrixEngine.MatrixCell>, java.util.List<de.tki.comfyuicompanion.ui.dialog.PromptMatrixGridDialog.CellView>> runner =
                (cells, cellViews) -> backgroundExecutor.execute(() ->
                        executeMatrixBatch(comfyUrl, view, controller, sanitizer, comfyConnector, dlgHolder[0], cells, cellViews));

        dlgHolder[0] = new de.tki.comfyuicompanion.ui.dialog.PromptMatrixGridDialog(
                owner,
                initPrompt,
                defCfg,
                defSteps,
                runner,
                img -> {
                    if (onPreviewImageChanged != null) onPreviewImageChanged.accept(img);
                    view.scaleAndSetImage(img);
                }
        );
        dlgHolder[0].setVisible(true);
        if (autoStartRun) {
            dlgHolder[0].triggerMatrixRun(defCfg, defSteps, runner);
        }
    }

    private void executeMatrixBatch(String comfyUrl,
                                    PromptLabView view,
                                    PromptLabController controller,
                                    PromptLabModelInputSanitizer sanitizer,
                                    java.util.function.Function<Consumer<String>, Boolean> comfyConnector,
                                    de.tki.comfyuicompanion.ui.dialog.PromptMatrixGridDialog dlg,
                                    java.util.List<PromptLabMatrixEngine.MatrixCell> cells,
                                    java.util.List<de.tki.comfyuicompanion.ui.dialog.PromptMatrixGridDialog.CellView> cellViews) {
        if (dlg != null) {
            dlg.setRunningState(true);
        }
        try {
            boolean connected = comfyApiClient.isServerOnline(comfyUrl);
            if (!connected && comfyConnector != null) {
                connected = Boolean.TRUE.equals(comfyConnector.apply(statusMsg -> {
                    if (dlg != null) dlg.setStatusText(statusMsg);
                    for (var cv : cellViews) cv.setStatus(statusMsg);
                }));
            }

            if (!connected) {
                String offlineMsg = "❌ ComfyUI is offline (" + comfyUrl + "). Please start ComfyUI on the Dashboard.";
                if (dlg != null) dlg.setStatusText(offlineMsg);
                for (var cv : cellViews) cv.setStatus("❌ ComfyUI Offline");
                return;
            }

            if (dlg != null) dlg.setStatusText("Connected to ComfyUI. Fetching available models and nodes...");
            org.json.JSONObject objectInfo = comfyApiClient.getObjectInfo(comfyUrl);
            if (objectInfo != null && controller != null) {
                controller.updateComfyModelSets(objectInfo);
            }

            String baseJsonText = view.getPromptJsonArea() != null ? view.getPromptJsonArea().getText().trim() : "";
            if ((baseJsonText.isEmpty() || "{}".equals(baseJsonText)) && controller != null) {
                SwingUtilities.invokeAndWait(controller::updatePromptLabJson);
                baseJsonText = view.getPromptJsonArea() != null ? view.getPromptJsonArea().getText().trim() : "{}";
            }

            String negPrompt = view.getPromptNegativeField() != null ? view.getPromptNegativeField().getText().trim() : "";
            int width = view.getPromptWidthSpinner() != null ? (int) view.getPromptWidthSpinner().getValue() : 1024;
            int height = view.getPromptHeightSpinner() != null ? (int) view.getPromptHeightSpinner().getValue() : 1024;
            double denoise = view.getPromptDenoiseSpinner() != null ? ((Number) view.getPromptDenoiseSpinner().getValue()).doubleValue() : 1.0;
            String sampler = (view.getPromptSamplerCombo() != null && view.getPromptSamplerCombo().getSelectedItem() != null)
                    ? view.getPromptSamplerCombo().getSelectedItem().toString() : "Auto";
            String scheduler = (view.getPromptSchedulerCombo() != null && view.getPromptSchedulerCombo().getSelectedItem() != null)
                    ? view.getPromptSchedulerCombo().getSelectedItem().toString() : "Auto";

            de.tki.comfyuicompanion.service.PromptBlueprintApiService apiService = new de.tki.comfyuicompanion.service.PromptBlueprintApiService();
            int successCount = 0;

            for (int i = 0; i < cells.size(); i++) {
                PromptLabMatrixEngine.MatrixCell cell = cells.get(i);
                de.tki.comfyuicompanion.ui.dialog.PromptMatrixGridDialog.CellView cv = cellViews.get(i);
                if (dlg != null) {
                    dlg.setStatusText("Generating variation " + (i + 1) + " of " + cells.size() + " — " + cell.label());
                }
                cv.setStatus("🚀 Submitting...");

                org.json.JSONObject parsed = new org.json.JSONObject(baseJsonText);
                org.json.JSONObject promptToQueue;
                if (parsed.has("prompt")) {
                    promptToQueue = parsed.getJSONObject("prompt");
                } else if (parsed.has("nodes")) {
                    org.json.JSONObject flattened = de.tki.comfyuicompanion.service.impl.ComfyPipelineService.flattenWorkflow(parsed);
                    org.json.JSONObject apiPayload = de.tki.comfyuicompanion.service.impl.ComfyPipelineService.convertUiToApi(flattened);
                    promptToQueue = apiPayload.getJSONObject("prompt");
                } else {
                    promptToQueue = parsed;
                }
                de.tki.comfyuicompanion.service.PromptBlueprintApiService.cleanNonNodeKeys(promptToQueue);

                int liveSteps = view.getPromptStepsSpinner() != null
                        ? (int) view.getPromptStepsSpinner().getValue()
                        : cell.steps();
                if (promptToQueue.toString().contains("ConditioningZeroOut") && liveSteps < 8) {
                    liveSteps = 8;
                }

                var inputs = new de.tki.comfyuicompanion.service.PromptBlueprintApiService.PromptLabInputs(
                        cell.positivePrompt(), negPrompt, width, height, liveSteps, cell.cfg(),
                        cell.seed(), sampler, scheduler, denoise, 1,
                        view.getSelectedInputImage() != null ? view.getSelectedInputImage().getName() : null
                );
                apiService.injectLabInputs(promptToQueue, inputs);
                if (sanitizer != null) {
                    sanitizer.sanitizeModelInputsInPrompt(promptToQueue, objectInfo, view, controller);
                    java.util.List<String> missingModels = sanitizer.validateRequiredModelsInPrompt(promptToQueue, objectInfo);
                    if (!missingModels.isEmpty()) {
                        String missingMsg = "❌ Missing model in ComfyUI: " + missingModels.get(0);
                        if (dlg != null) dlg.setStatusText(missingMsg);
                        for (int j = i; j < cellViews.size(); j++) {
                            cellViews.get(j).setStatus("❌ Missing Model");
                        }
                        return;
                    }
                }

                org.json.JSONObject payload = new org.json.JSONObject();
                payload.put("prompt", promptToQueue);
                payload.put("client_id", getClientId());

                java.net.http.HttpResponse<String> resp = comfyApiClient.postPrompt(comfyUrl, payload.toString());
                if (resp != null && resp.statusCode() == 200) {
                    String pid = new org.json.JSONObject(resp.body()).optString("prompt_id", "");
                    cv.setStatus("🚀 Generating...");
                    Image resultImg = awaitSinglePromptImage(comfyUrl, pid, cv);
                    if (resultImg != null) {
                        cv.setCompletedImage(resultImg);
                        successCount++;
                        if (onPreviewImageChanged != null) {
                            SwingUtilities.invokeLater(() -> onPreviewImageChanged.accept(resultImg));
                        }
                    } else {
                        cv.setStatus("❌ Failed / Timeout");
                    }
                } else {
                    String errBody = resp != null ? resp.body() : "No response";
                    logger.warn("Matrix cell queue error: {}", errBody);
                    cv.setStatus("❌ Queue Error");
                    if (dlg != null) {
                        dlg.setStatusText("❌ ComfyUI rejected prompt: " + (errBody.length() > 90 ? errBody.substring(0, 90) + "..." : errBody));
                    }
                }
            }

            if (dlg != null && successCount > 0) {
                dlg.setStatusText("✅ Completed " + successCount + " of " + cells.size() + " variations. Click any image to send to main preview.");
            }
        } catch (Exception e) {
            logger.error("Error running matrix batch: {}", e.getMessage(), e);
            if (dlg != null) {
                dlg.setStatusText("❌ Error: " + e.getMessage());
            }
            for (var cv : cellViews) {
                cv.setStatus("❌ Error: " + (e.getMessage() != null ? e.getMessage() : "Failed"));
            }
        } finally {
            if (dlg != null) {
                dlg.setRunningState(false);
            }
        }
    }

    private Image awaitSinglePromptImage(String comfyUrl,
                                         String promptId,
                                         de.tki.comfyuicompanion.ui.dialog.PromptMatrixGridDialog.CellView cellView) {
        AutoCloseable wsSession = webSocketClient.connect(
                comfyUrl,
                promptId,
                (step, max) -> {
                    int pct = max > 0 ? (int) Math.round((step * 100.0) / max) : 0;
                    if (cellView != null) {
                        cellView.setStatus("🚀 Step " + step + "/" + max + " (" + pct + "%)");
                    }
                },
                nodeId -> {
                    if (nodeId != null && cellView != null) {
                        cellView.setStatus("⚙️ Node #" + nodeId + "...");
                    }
                },
                null
        );
        try {
            for (int attempt = 0; attempt < 180; attempt++) {
                try {
                    Thread.sleep(1000);
                    ComfyApiClient.ImageOutput out = comfyApiClient.fetchPromptHistoryImage(comfyUrl, promptId);
                    if (out != null) {
                        String imageUrl = comfyUrl + "/view?filename=" + out.filename() + "&subfolder=" + out.subfolder() + "&type=" + out.type();
                        return javax.imageio.ImageIO.read(java.net.URI.create(imageUrl).toURL());
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                } catch (Exception e) {
                    logger.debug("Polling attempt failed: {}", e.getMessage());
                }
            }
            return null;
        } finally {
            try {
                wsSession.close();
            } catch (Exception e) {
                logger.debug("Failed to close WebSocket session: {}", e.getMessage());
            }
        }
    }
}
