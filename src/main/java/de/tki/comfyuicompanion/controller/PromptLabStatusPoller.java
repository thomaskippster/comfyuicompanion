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
                } catch (Exception ignored) {}
            }
        });
    }
}
