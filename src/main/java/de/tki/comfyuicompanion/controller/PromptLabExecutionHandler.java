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
    private PromptLabView promptLabView;

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
    }

    public void setPromptLabView(PromptLabView view) {
        this.promptLabView = view;
    }

    public void sendPromptToComfyUI() {
        sendPromptToComfyUI(promptLabView, promptLabView != null ? promptLabView.getSelectedInputImage() : null);
    }

    public void suggestSubjectCompletions() {
        suggestSubjectCompletions(promptLabView);
    }

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

                String promptJsonText = promptLabView.getPromptJsonArea().getText();
                JSONObject parsedPrompt = new JSONObject(promptJsonText);
                JSONObject promptToQueue;

                if (parsedPrompt.has("prompt")) {
                    promptToQueue = parsedPrompt.getJSONObject("prompt");
                } else if (parsedPrompt.has("nodes")) {
                    promptToQueue = parsedPrompt;
                } else {
                    promptToQueue = parsedPrompt;
                }

                PromptBlueprintApiService.PromptLabInputs inputs = new PromptBlueprintApiService.PromptLabInputs(
                        assembledPrompt, negativePrompt, widthVal, heightVal, stepsVal, cfgVal,
                        System.currentTimeMillis(), samplerNameVal, schedulerVal, denoiseVal, batchSizeVal,
                        selectedInputImage != null ? selectedInputImage.getName() : null
                );

                PromptBlueprintApiService apiService = new PromptBlueprintApiService();
                apiService.injectLabInputs(promptToQueue, inputs);
                sanitizeModelInputsInPrompt(promptToQueue, objectInfo);

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

    public void scaleAndSetImage(Image img) {
        if (onPreviewImageChanged != null) {
            onPreviewImageChanged.accept(img);
        }
        if (promptLabView != null) {
            promptLabView.scaleAndSetImage(img);
        }
    }

    public void downloadLocalGemmaModel(Component parent) {
        if (promptLabView == null) return;
        JButton btnSuggest = promptLabView.getBtnSuggestSubject();
        JTextArea console = promptLabView.getPromptLabConsole();

        if (btnSuggest != null) {
            btnSuggest.setEnabled(false);
            btnSuggest.setText("Downloading...");
            btnSuggest.setIcon(SvgIconFactory.get(AppIcon.REFRESH));
        }
        if (console != null) {
            console.append("Starting download of Gemma-3-4B GGUF model (3 GB) from Hugging Face...\n");
        }

        localAIService.getLocalGemmaService().downloadModel(
                (percent, status) -> SwingUtilities.invokeLater(() -> {
                    if (console != null) console.append("Download: " + status + "\n");
                }),
                () -> SwingUtilities.invokeLater(() -> {
                    if (btnSuggest != null) {
                        btnSuggest.setEnabled(true);
                        btnSuggest.setText("Suggest");
                        btnSuggest.setIcon(SvgIconFactory.get(AppIcon.SUGGEST));
                    }
                    if (console != null) console.append("✅ Local Gemma model downloaded successfully!\n");
                    JOptionPane.showMessageDialog(parent,
                            "Local Gemma model downloaded successfully!",
                            "Download Complete", JOptionPane.INFORMATION_MESSAGE);
                    if (onAiModelDisplayUpdated != null) onAiModelDisplayUpdated.run();
                }),
                (errorMsg, ex) -> SwingUtilities.invokeLater(() -> {
                    if (btnSuggest != null) {
                        btnSuggest.setEnabled(true);
                        btnSuggest.setText("Suggest");
                        btnSuggest.setIcon(SvgIconFactory.get(AppIcon.SUGGEST));
                    }
                    if (console != null) console.append("❌ Download failed: " + errorMsg + "\n");
                    JOptionPane.showMessageDialog(parent,
                            "Failed to download Gemma model: " + errorMsg,
                            "Download Failed", JOptionPane.ERROR_MESSAGE);
                })
        );
    }

    public void suggestSubjectCompletions(Component parent) {
        if (promptLabView == null) return;

        boolean hasGemma = localAIService != null && localAIService.isLocalGemmaDownloaded();
        if (!hasGemma) {
            int choice = JOptionPane.showConfirmDialog(parent,
                    "The local Gemma model is not downloaded.\n" +
                            "Would you like to download the Gemma-3-4B GGUF model (approx. 3 GB) now to get suggestions?",
                    "Download Local Gemma Model?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                downloadLocalGemmaModel(parent);
            }
            return;
        }

        String currentSubject = promptLabView.getPromptSubjectField() != null ? promptLabView.getPromptSubjectField().getText().trim() : "";
        if (currentSubject.isEmpty()) {
            JOptionPane.showMessageDialog(parent,
                    "Please enter a basic prompt or subject first.",
                    "Empty Prompt", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JButton btnSuggest = promptLabView.getBtnSuggestSubject();
        JTextArea console = promptLabView.getPromptLabConsole();

        if (btnSuggest != null) {
            btnSuggest.setEnabled(false);
            btnSuggest.setText("Suggesting...");
            btnSuggest.setIcon(SvgIconFactory.get(AppIcon.REFRESH));
        }
        if (console != null) {
            console.append("Generating prompt completion suggestions using local Gemma...\n");
        }

        backgroundExecutor.execute(() -> {
            List<String> suggestions = null;
            StringBuilder errorLogs = new StringBuilder();
            try {
                suggestions = localAIService.getDirectGemmaCompletions(currentSubject);
            } catch (Throwable ex) {
                StringWriter sw = new StringWriter();
                ex.printStackTrace(new PrintWriter(sw));
                errorLogs.append("AI suggestions failed:\n").append(sw).append("\n");
            }

            final List<String> finalSuggestions = suggestions;
            final String finalErrors = errorLogs.toString();
            SwingUtilities.invokeLater(() -> {
                if (btnSuggest != null) {
                    btnSuggest.setEnabled(true);
                    btnSuggest.setText("Suggest");
                    btnSuggest.setIcon(SvgIconFactory.get(AppIcon.SUGGEST));
                }

                if (finalSuggestions != null && !finalSuggestions.isEmpty()) {
                    JPanel suggPanel = promptLabView.getPromptSubjectSuggestionsPanel();
                    if (suggPanel != null) {
                        suggPanel.removeAll();
                        for (String suggestion : finalSuggestions) {
                            JButton sugBtn = new JButton(suggestion);
                            sugBtn.setHorizontalAlignment(SwingConstants.LEFT);
                            sugBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
                            sugBtn.putClientProperty("FlatLaf.style", "arc: 6; background: $TextField.background; border: 4,8,4,8,$Card.border,1,6");
                            sugBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
                            sugBtn.setToolTipText("Click to use: " + suggestion);
                            sugBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
                            sugBtn.setPreferredSize(new Dimension(300, 30));
                            sugBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
                            sugBtn.addActionListener(e -> {
                                promptLabView.getPromptSubjectField().setText(suggestion);
                                promptLabView.getPromptSubjectSuggestionsWrapper().setVisible(false);
                                promptLabView.getPromptLabLeftPanel().revalidate();
                                promptLabView.getPromptLabLeftPanel().repaint();
                            });
                            suggPanel.add(sugBtn);
                            suggPanel.add(Box.createVerticalStrut(4));
                        }
                    }
                    promptLabView.getPromptSubjectSuggestionsWrapper().setVisible(true);
                    promptLabView.getPromptLabLeftPanel().revalidate();
                    promptLabView.getPromptLabLeftPanel().repaint();
                    if (console != null) console.append("Subject suggestions loaded successfully using local Gemma.\n");
                } else {
                    if (console != null) {
                        console.append("❌ Error: Could not generate suggestions.\n");
                        if (!finalErrors.isEmpty()) console.append(finalErrors + "\n");
                    }
                    JOptionPane.showMessageDialog(parent,
                            "Could not generate suggestions. Please ensure the local Gemma model is downloaded correctly.",
                            "Suggestions Failed", JOptionPane.WARNING_MESSAGE);
                }
            });
        });
    }

    public void sanitizeModelInputsInPrompt(JSONObject promptObj, JSONObject objectInfo) {
        if (promptObj == null) return;
        for (String key : promptObj.keySet()) {
            JSONObject node = promptObj.optJSONObject(key);
            if (node == null) continue;
            JSONObject inp = node.optJSONObject("inputs");
            if (inp == null) continue;
            String classType = node.optString("class_type", "");

            if ("KSamplerSelect".equals(classType)) {
                String sn = inp.optString("sampler_name", "");
                if (sn.isBlank() || sn.equalsIgnoreCase("COMBO") || sn.equalsIgnoreCase("Auto")) {
                    String uiSampler = (promptLabView.getPromptSamplerCombo() != null && promptLabView.getPromptSamplerCombo().getSelectedItem() != null)
                            ? (String) promptLabView.getPromptSamplerCombo().getSelectedItem() : "Auto";
                    inp.put("sampler_name", (!uiSampler.equalsIgnoreCase("Auto") && !uiSampler.equalsIgnoreCase("COMBO")) ? uiSampler : "euler");
                }
            }

            for (String ik : new ArrayList<>(inp.keySet())) {
                Object val = inp.opt(ik);
                if (val instanceof String s && !s.isBlank()) {
                    String cleaned = s.replaceAll("[/\\\\]+", "/").trim();
                    if (s.startsWith("/") || s.startsWith("\\")) {
                        cleaned = "/" + cleaned.replaceAll("^/+", "");
                    }

                    if (cleaned.equalsIgnoreCase("COMBO") || cleaned.equalsIgnoreCase("Auto")) {
                        if (ik.equals("sampler_name") || classType.contains("Sampler")) {
                            String uiSampler = (promptLabView.getPromptSamplerCombo() != null && promptLabView.getPromptSamplerCombo().getSelectedItem() != null)
                                    ? (String) promptLabView.getPromptSamplerCombo().getSelectedItem() : "Auto";
                            inp.put(ik, (!uiSampler.equalsIgnoreCase("Auto") && !uiSampler.equalsIgnoreCase("COMBO")) ? uiSampler : "euler");
                            continue;
                        } else if (ik.equals("scheduler")) {
                            String uiScheduler = (promptLabView.getPromptSchedulerCombo() != null && promptLabView.getPromptSchedulerCombo().getSelectedItem() != null)
                                    ? (String) promptLabView.getPromptSchedulerCombo().getSelectedItem() : "Auto";
                            inp.put(ik, (!uiScheduler.equalsIgnoreCase("Auto") && !uiScheduler.equalsIgnoreCase("COMBO")) ? uiScheduler : "simple");
                            continue;
                        }
                    }

                    boolean isModelKey = ik.endsWith("_name") || ik.endsWith("_path") || ik.equals("model") || ik.equals("vae") || ik.equals("clip") || ik.equals("unet");
                    boolean isModelFile = cleaned.endsWith(".safetensors") || cleaned.endsWith(".ckpt") || cleaned.endsWith(".pt") || cleaned.endsWith(".bin") || cleaned.endsWith(".onnx") || cleaned.endsWith(".sft");

                    if (isModelKey || isModelFile) {
                        String matchedOption = null;
                        if (objectInfo != null) {
                            matchedOption = findModelInObjectInfo(classType, ik, cleaned, objectInfo);
                        }
                        if (matchedOption == null) {
                            matchedOption = findExactComfyModelOption(classType, ik, cleaned);
                        }
                        if (matchedOption != null) {
                            inp.put(ik, matchedOption);
                        } else {
                            if (ik.equals("sampler_name") && cleaned.equalsIgnoreCase("COMBO")) {
                                inp.put(ik, "euler");
                            } else if (ik.equals("scheduler") && cleaned.equalsIgnoreCase("COMBO")) {
                                inp.put(ik, "normal");
                            } else {
                                inp.put(ik, cleaned);
                            }
                        }
                    }
                }
            }
        }
        PromptBlueprintApiService.sanitizeAllSeedsInPrompt(promptObj);
    }

    public String findExactComfyModelOption(String classType, String inputKey, String targetValue) {
        if (targetValue == null || targetValue.isBlank()) return targetValue;
        Set<String> options = null;

        if (inputKey.equals("unet_name")) {
            options = promptLabController.getComfyUnetModels();
        } else if (inputKey.equals("ckpt_name")) {
            options = promptLabController.getComfyCheckpoints();
        } else if (inputKey.equals("clip_name") || inputKey.equals("clip_name1") || inputKey.equals("clip_name2")) {
            options = promptLabController.getComfyClips();
        } else if (inputKey.equals("vae_name")) {
            options = promptLabController.getComfyVaes();
        } else if (inputKey.equals("type") && classType.contains("CLIPLoader")) {
            options = promptLabController.getComfyClipTypes();
        } else if (inputKey.equals("weight_dtype")) {
            options = promptLabController.getComfyUnetWeightDtypes();
        }

        if (options != null && !options.isEmpty()) {
            String normTarget = targetValue.replace('\\', '/').trim();
            for (String opt : options) {
                if (opt.replace('\\', '/').equalsIgnoreCase(normTarget)) {
                    return opt;
                }
            }
            String targetFileName = new File(normTarget).getName();
            for (String opt : options) {
                String optFileName = new File(opt.replace('\\', '/')).getName();
                if (optFileName.equalsIgnoreCase(targetFileName)) {
                    return opt;
                }
            }
        }
        return null;
    }

    public String findModelInObjectInfo(String classType, String inputKey, String targetValue, JSONObject objectInfo) {
        if (objectInfo == null || !objectInfo.has(classType)) return null;
        JSONObject nodeDef = objectInfo.optJSONObject(classType);
        if (nodeDef == null) return null;
        JSONObject inputDef = nodeDef.optJSONObject("input");
        if (inputDef == null) return null;
        JSONObject reqInputs = inputDef.optJSONObject("required");
        JSONObject optInputs = inputDef.optJSONObject("optional");

        JSONObject targetInput = null;
        if (reqInputs != null && reqInputs.has(inputKey)) {
            targetInput = reqInputs.optJSONObject(inputKey);
        } else if (optInputs != null && optInputs.has(inputKey)) {
            targetInput = optInputs.optJSONObject(inputKey);
        }

        if (targetInput != null) {
            org.json.JSONArray choices = targetInput.optJSONArray("0");
            if (choices != null) {
                String normTarget = targetValue.replace('\\', '/').trim();
                for (int i = 0; i < choices.length(); i++) {
                    String opt = choices.optString(i, "");
                    if (opt.replace('\\', '/').equalsIgnoreCase(normTarget)) {
                        return opt;
                    }
                }
                String targetFileName = new File(normTarget).getName();
                for (int i = 0; i < choices.length(); i++) {
                    String opt = choices.optString(i, "");
                    String optFileName = new File(opt.replace('\\', '/')).getName();
                    if (optFileName.equalsIgnoreCase(targetFileName)) {
                        return opt;
                    }
                }
            }
        }
        return null;
    }
}
