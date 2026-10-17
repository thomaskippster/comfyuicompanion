package de.tki.comfyuicompanion.controller;

import de.tki.comfyuicompanion.domain.LaunchProfile;
import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import de.tki.comfyuicompanion.service.PromptBlueprintApiService;
import de.tki.comfyuicompanion.service.impl.ComfyApiClient;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.LocalAIService;
import de.tki.comfyuicompanion.service.impl.ProfileManager;
import de.tki.comfyuicompanion.ui.PromptLabView;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Handles workflow prompt submission to ComfyUI, response status polling,
 * result image fetching, and Gemma AI prompt suggestions.
 */
public class PromptLabExecutionHandler {
    private static final Logger logger = LoggerFactory.getLogger(PromptLabExecutionHandler.class);

    private final ConfigService configService;
    private final LocalAIService localAIService;
    private final IComfyLifecycleService lifecycleService;
    private final ComfyApiClient comfyApiClient;
    private final ProfileManager profileManager;
    private final PromptLabController promptLabController;
    private final ExecutorService backgroundExecutor;
    private final Consumer<LaunchProfile> startComfyAction;
    private final Consumer<Image> onPreviewImageChanged;
    private final Runnable onAiModelDisplayUpdated;
    private final PromptLabAiAssistant aiAssistant;
    private final PromptLabModelInputSanitizer modelInputSanitizer;
    private PromptLabView promptLabView;
    private Runnable onOpenPathsConfig;
    private java.util.function.BiConsumer<String, String> onDownloadMissingModels;

    /**
     * Sets the callback for opening the paths configuration dialog.
     *
     * @param onOpenPathsConfig callback to invoke
     */
    public void setOnOpenPathsConfig(Runnable onOpenPathsConfig) {
        this.onOpenPathsConfig = onOpenPathsConfig;
    }

    /**
     * Sets the callback for forwarding a workflow with missing models to the Download Manager.
     *
     * @param onDownloadMissingModels consumer accepting workflow name and JSON payload
     */
    public void setOnDownloadMissingModels(java.util.function.BiConsumer<String, String> onDownloadMissingModels) {
        this.onDownloadMissingModels = onDownloadMissingModels;
    }

    public PromptLabExecutionHandler(ConfigService configService,
                                     LocalAIService localAIService,
                                     IComfyLifecycleService lifecycleService,
                                     ComfyApiClient comfyApiClient,
                                     ProfileManager profileManager,
                                     PromptLabController promptLabController,
                                     ExecutorService backgroundExecutor,
                                     Consumer<LaunchProfile> startComfyAction,
                                     Consumer<Image> onPreviewImageChanged,
                                     Runnable onAiModelDisplayUpdated) {
        this(configService, localAIService, lifecycleService, comfyApiClient, profileManager,
                promptLabController, backgroundExecutor, startComfyAction, onPreviewImageChanged,
                onAiModelDisplayUpdated,
                new PromptLabAiAssistant(localAIService, backgroundExecutor, onAiModelDisplayUpdated),
                new PromptLabModelInputSanitizer());
    }

    public PromptLabExecutionHandler(ConfigService configService,
                                     LocalAIService localAIService,
                                     IComfyLifecycleService lifecycleService,
                                     ComfyApiClient comfyApiClient,
                                     ProfileManager profileManager,
                                     PromptLabController promptLabController,
                                     ExecutorService backgroundExecutor,
                                     Consumer<LaunchProfile> startComfyAction,
                                     Consumer<Image> onPreviewImageChanged,
                                     Runnable onAiModelDisplayUpdated,
                                     PromptLabAiAssistant aiAssistant,
                                     PromptLabModelInputSanitizer modelInputSanitizer) {
        this.configService = configService;
        this.localAIService = localAIService;
        this.lifecycleService = lifecycleService;
        this.comfyApiClient = comfyApiClient;
        this.profileManager = profileManager;
        this.promptLabController = promptLabController;
        this.backgroundExecutor = backgroundExecutor;
        this.startComfyAction = startComfyAction;
        this.onPreviewImageChanged = onPreviewImageChanged;
        this.onAiModelDisplayUpdated = onAiModelDisplayUpdated;
        this.aiAssistant = aiAssistant;
        this.modelInputSanitizer = modelInputSanitizer;
    }

    /**
     * Sets the view reference for this handler.
     *
     * @param view the prompt lab view
     */
    public void setPromptLabView(PromptLabView view) {
        this.promptLabView = view;
    }

    /**
     * Triggers sending the prompt to ComfyUI, using the currently selected input image if applicable.
     */
    public void sendPromptToComfyUI() {
        sendPromptToComfyUI(promptLabView, promptLabView != null ? promptLabView.getSelectedInputImage() : null);
    }

    /**
     * Triggers AI-assisted subject completions based on the current prompt.
     */
    public void suggestSubjectCompletions() {
        suggestSubjectCompletions(promptLabView);
    }

    /**
     * Prepares and sends the workflow prompt to ComfyUI.
     *
     * @param parentComponent    the parent component for dialogs
     * @param selectedInputImage the selected input image file, if any
     */
    public void sendPromptToComfyUI(Component parentComponent, File selectedInputImage) {
        if (promptLabView == null) return;

        final String comfyUrl = configService.getComfyUIUrl();
        final String assembledPrompt = promptLabView.getPromptSubjectField() != null ? promptLabView.getPromptSubjectField().getText().trim() : "";
        final String negativePrompt  = promptLabView.getPromptNegativeField() != null ? promptLabView.getPromptNegativeField().getText().trim() : "";
        final int widthVal = promptLabView.getPromptWidthSpinner() != null ? (int) promptLabView.getPromptWidthSpinner().getValue() : 1024;
        final int heightVal = promptLabView.getPromptHeightSpinner() != null ? (int) promptLabView.getPromptHeightSpinner().getValue() : 1024;
        final int batchSizeVal = promptLabView.getPromptBatchSizeSpinner() != null ? (int) promptLabView.getPromptBatchSizeSpinner().getValue() : 1;
        final int stepsVal = promptLabView.getPromptStepsSpinner() != null ? (int) promptLabView.getPromptStepsSpinner().getValue() : 20;
        final double cfgVal = promptLabView.getPromptCfgSpinner() != null ? ((Number) promptLabView.getPromptCfgSpinner().getValue()).doubleValue() : 7.0;
        final double denoiseVal = promptLabView.getPromptDenoiseSpinner() != null ? ((Number) promptLabView.getPromptDenoiseSpinner().getValue()).doubleValue() : 1.0;
        final String samplerNameVal = (promptLabView.getPromptSamplerCombo() != null && promptLabView.getPromptSamplerCombo().getSelectedItem() != null)
                ? (String) promptLabView.getPromptSamplerCombo().getSelectedItem() : "Auto";
        final String schedulerVal = (promptLabView.getPromptSchedulerCombo() != null && promptLabView.getPromptSchedulerCombo().getSelectedItem() != null)
                ? (String) promptLabView.getPromptSchedulerCombo().getSelectedItem() : "Auto";

        if (promptLabView.getPromptImageInputPanel() != null && promptLabView.getPromptImageInputPanel().isVisible()) {
            if (selectedInputImage == null || !selectedInputImage.exists()) {
                JOptionPane.showMessageDialog(parentComponent,
                        "This workflow requires an input image.\nPlease select an image before generating.",
                        "Missing Input Image", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        JButton btnSend = promptLabView.getBtnSendToComfy();
        JTextArea console = promptLabView.getPromptLabConsole();

        if (btnSend != null) {
            btnSend.setEnabled(false);
            btnSend.setText("Generating...");
            btnSend.setIcon(SvgIconFactory.get(AppIcon.REFRESH));
        }
        if (console != null) {
            console.append("🚀 Starting generation pipeline...\n");
        }

        backgroundExecutor.execute(() -> {
            try {
                boolean connected = false;
                try {
                    HttpResponse<String> infoResponse = comfyApiClient.fetchObjectInfoRaw(comfyUrl);
                    connected = infoResponse != null && infoResponse.statusCode() == 200;
                } catch (Exception ignored) {}

                if (!connected) {
                    String activeId = configService.getActiveProfile();
                    List<LaunchProfile> profiles = profileManager.loadProfiles();
                    LaunchProfile activeProfile = profiles.stream()
                            .filter(p -> p.id().equals(activeId)).findFirst().orElse(null);
                    if (activeProfile == null && !profiles.isEmpty()) activeProfile = profiles.get(0);

                    if (activeProfile != null) {
                        final LaunchProfile fp = activeProfile;
                        if (console != null) {
                            SwingUtilities.invokeLater(() -> console.append("🔌 ComfyUI offline — auto-starting with profile: " + fp.name() + "\n"));
                        }
                        if (startComfyAction != null) {
                            startComfyAction.accept(activeProfile);
                        }

                        int waitedSec = 0;
                        while (!connected && waitedSec < 45) {
                            Thread.sleep(2000);
                            waitedSec += 2;
                            try {
                                HttpResponse<String> check = comfyApiClient.fetchObjectInfoRaw(comfyUrl);
                                connected = check != null && check.statusCode() == 200;
                            } catch (Exception ignored) {}
                        }
                    }
                }

                if (!connected) {
                    SwingUtilities.invokeLater(() -> {
                        if (console != null) console.append("❌ Could not connect to ComfyUI server at: " + comfyUrl + "\n");
                        if (btnSend != null) {
                            btnSend.setEnabled(true);
                            btnSend.setText("Generate");
                            btnSend.setIcon(SvgIconFactory.get(AppIcon.IMAGE_LAB));
                        }
                    });
                    return;
                }

                JSONObject objectInfo = comfyApiClient.getObjectInfo(comfyUrl);
                if (objectInfo != null && promptLabController != null) {
                    promptLabController.updateComfyModelSets(objectInfo);
                }

                String promptJsonText = promptLabView.getPromptJsonArea().getText();
                JSONObject parsedPrompt = new JSONObject(promptJsonText);
                JSONObject promptToQueue;
                if (parsedPrompt.has("prompt")) {
                    promptToQueue = parsedPrompt.getJSONObject("prompt");
                } else if (parsedPrompt.has("nodes")) {
                    JSONObject flattened = de.tki.comfyuicompanion.service.impl.ComfyPipelineService.flattenWorkflow(parsedPrompt);
                    JSONObject apiPayload = de.tki.comfyuicompanion.service.impl.ComfyPipelineService.convertUiToApi(flattened);
                    promptToQueue = apiPayload.getJSONObject("prompt");
                } else {
                    promptToQueue = parsedPrompt;
                }
                PromptBlueprintApiService.cleanNonNodeKeys(promptToQueue);

                PromptBlueprintApiService.PromptLabInputs inputs = new PromptBlueprintApiService.PromptLabInputs(
                        assembledPrompt, negativePrompt, widthVal, heightVal, stepsVal, cfgVal,
                        System.currentTimeMillis(), samplerNameVal, schedulerVal, denoiseVal, batchSizeVal,
                        selectedInputImage != null ? selectedInputImage.getName() : null
                );

                PromptBlueprintApiService apiService = new PromptBlueprintApiService();
                apiService.injectLabInputs(promptToQueue, inputs);
                sanitizeModelInputsInPrompt(promptToQueue, objectInfo);

                List<String> missingModels = modelInputSanitizer.validateRequiredModelsInPrompt(promptToQueue, objectInfo);
                if (!missingModels.isEmpty()) {
                    String extraPath = configService.getExtraComfyUIPath();
                    String wfJson = promptToQueue.toString(2);
                    SwingUtilities.invokeLater(() -> modelInputSanitizer.handleMissingModels(
                            parentComponent, missingModels, extraPath, console,
                            () -> { if (onDownloadMissingModels != null) onDownloadMissingModels.accept("prompt_lab_workflow", wfJson); },
                            onOpenPathsConfig));
                    return;
                }

                JSONObject payload = new JSONObject();
                payload.put("prompt", promptToQueue);

                HttpResponse<String> queueResponse = comfyApiClient.postPrompt(comfyUrl, payload.toString());
                if (queueResponse != null && queueResponse.statusCode() == 200) {
                    JSONObject queueResult = new JSONObject(queueResponse.body());
                    String promptId = queueResult.optString("prompt_id", "");
                    if (console != null) {
                        SwingUtilities.invokeLater(() -> console.append("✅ Queued with ID: " + promptId + "\n"));
                    }
                    startPollingPromptStatus(promptId);
                } else {
                    String err = queueResponse != null ? queueResponse.body() : "No response";
                    if (console != null) {
                        SwingUtilities.invokeLater(() -> console.append("❌ Error queueing prompt: " + err + "\n"));
                    }
                }

            } catch (Exception ex) {
                logger.error("Error in prompt lab execution: " + ex.getMessage(), ex);
                if (console != null) {
                    SwingUtilities.invokeLater(() -> console.append("❌ Pipeline failed: " + ex.getMessage() + "\n"));
                }
            } finally {
                SwingUtilities.invokeLater(() -> {
                    if (btnSend != null) {
                        btnSend.setEnabled(true);
                        btnSend.setText("Generate");
                        btnSend.setIcon(SvgIconFactory.get(AppIcon.IMAGE_LAB));
                    }
                });
            }
        });
    }

    /**
     * Polls the ComfyUI API for the status of a queued prompt until completion.
     *
     * @param promptId the ID of the queued prompt
     */
    public void startPollingPromptStatus(String promptId) {
        if (promptLabView == null) return;
        String comfyUrl = configService.getComfyUIUrl();

        SwingUtilities.invokeLater(() -> {
            if (promptLabView.getPromptLabRightTabbedPane() != null) {
                promptLabView.getPromptLabRightTabbedPane().setSelectedIndex(0);
            }
            if (promptLabView.getPromptLabProgressBar() != null) {
                promptLabView.getPromptLabProgressBar().setVisible(true);
                promptLabView.getPromptLabProgressBar().setIndeterminate(true);
                promptLabView.getPromptLabProgressBar().setString("Queued...");
            }
            if (promptLabView.getPromptImagePreviewLabel() != null) {
                promptLabView.getPromptImagePreviewLabel().setText("Generating image... Please wait.");
                promptLabView.getPromptImagePreviewLabel().setIcon(null);
            }
        });

        backgroundExecutor.execute(() -> {
            boolean done = false;
            int pollAttempts = 0;

            while (!done && pollAttempts < 180) {
                try {
                    Thread.sleep(1000);
                    pollAttempts++;

                    ComfyApiClient.ImageOutput imgOutput = comfyApiClient.fetchPromptHistoryImage(comfyUrl, promptId);
                    if (imgOutput != null) {
                        final String finalFilename = imgOutput.filename();
                        final String finalSubfolder = imgOutput.subfolder();
                        final String finalType = imgOutput.type();

                        SwingUtilities.invokeLater(() -> {
                            loadAndDisplayImage(finalFilename, finalSubfolder, finalType);
                            if (promptLabView.getPromptLabProgressBar() != null) promptLabView.getPromptLabProgressBar().setVisible(false);
                            if (promptLabView.getPromptLabConsole() != null) promptLabView.getPromptLabConsole().append("Image generated and loaded successfully!\n\n");
                        });
                        done = true;
                        break;
                    }

                    if (!done) {
                        ComfyApiClient.QueueInfo qInfo = comfyApiClient.fetchQueueStatus(comfyUrl, promptId);
                        if (qInfo.state() == ComfyApiClient.QueueState.RUNNING) {
                            SwingUtilities.invokeLater(() -> {
                                if (promptLabView.getPromptLabProgressBar() != null) promptLabView.getPromptLabProgressBar().setString("Generating...");
                            });
                        } else if (qInfo.state() == ComfyApiClient.QueueState.PENDING) {
                            final int pos = qInfo.position();
                            SwingUtilities.invokeLater(() -> {
                                if (promptLabView.getPromptLabProgressBar() != null) promptLabView.getPromptLabProgressBar().setString("Queued (Position: " + pos + ")");
                            });
                        }
                    }

                } catch (Exception e) {
                    logger.error("Error polling ComfyUI status: " + e.getMessage());
                }
            }

            if (!done) {
                SwingUtilities.invokeLater(() -> {
                    if (promptLabView.getPromptLabProgressBar() != null) promptLabView.getPromptLabProgressBar().setVisible(false);
                    if (promptLabView.getPromptImagePreviewLabel() != null) promptLabView.getPromptImagePreviewLabel().setText("Generation timed out or failed.");
                    if (promptLabView.getPromptLabConsole() != null) promptLabView.getPromptLabConsole().append("❌ Generation timed out or failed to load image.\n\n");
                });
            }
        });
    }

    /**
     * Downloads and displays the generated image from ComfyUI.
     *
     * @param filename  the image filename
     * @param subfolder the subfolder containing the image
     * @param type      the image type
     */
    public void loadAndDisplayImage(String filename, String subfolder, String type) {
        String comfyUrl = configService.getComfyUIUrl();
        String imageUrl = comfyUrl + "/view?filename=" + filename + "&subfolder=" + subfolder + "&type=" + type;

        backgroundExecutor.execute(() -> {
            try {
                URL url = new URL(imageUrl);
                Image img = ImageIO.read(url);
                if (img != null) {
                    SwingUtilities.invokeLater(() -> scaleAndSetImage(img));
                }
            } catch (Exception ex) {
                logger.error("Failed to download image: " + ex.getMessage());
                SwingUtilities.invokeLater(() -> {
                    if (promptLabView != null && promptLabView.getPromptImagePreviewLabel() != null) {
                        promptLabView.getPromptImagePreviewLabel().setText("Failed to load generated image.");
                    }
                });
            }
        });
    }

    /**
     * Scales and sets the downloaded image on the UI preview panel.
     *
     * @param img the image to display
     */
    public void scaleAndSetImage(Image img) {
        if (onPreviewImageChanged != null) {
            onPreviewImageChanged.accept(img);
        }
        if (promptLabView != null) {
            promptLabView.scaleAndSetImage(img);
        }
    }

    /**
     * Downloads the local Gemma model from Hugging Face for offline AI suggestions.
     *
     * @param parent the parent component for dialogs
     */
    public void downloadLocalGemmaModel(Component parent) {
        aiAssistant.downloadLocalGemmaModel(parent, promptLabView);
    }

    /**
     * Uses the local Gemma model to generate subject completion suggestions based on the current prompt.
     *
     * @param parent the parent component for dialogs
     */
    public void suggestSubjectCompletions(Component parent) {
        aiAssistant.suggestSubjectCompletions(parent, promptLabView);
    }

    /**
     * Sanitizes and injects selected models into the ComfyUI workflow JSON before submission.
     *
     * @param promptObj  the root prompt JSON object
     * @param objectInfo the ComfyUI object info for validation
     */
    public void sanitizeModelInputsInPrompt(JSONObject promptObj, JSONObject objectInfo) {
        modelInputSanitizer.sanitizeModelInputsInPrompt(promptObj, objectInfo, promptLabView, promptLabController);
    }

    /**
     * Resolves an exact comfy model option given a target value.
     *
     * @param classType   the node class type
     * @param inputKey    the input property key
     * @param targetValue the target model value
     * @return the matched exact ComfyUI model name or null
     */
    public String findExactComfyModelOption(String classType, String inputKey, String targetValue) {
        return modelInputSanitizer.findExactComfyModelOption(classType, inputKey, targetValue, promptLabController);
    }

    /**
     * Resolves a model name directly against the ComfyUI object info payload.
     *
     * @param classType   the node class type
     * @param inputKey    the input property key
     * @param targetValue the target model value
     * @param objectInfo  the ComfyUI object info
     * @return the resolved model name
     */
    public String findModelInObjectInfo(String classType, String inputKey, String targetValue, JSONObject objectInfo) {
        return modelInputSanitizer.findModelInObjectInfo(classType, inputKey, targetValue, objectInfo);
    }
}
