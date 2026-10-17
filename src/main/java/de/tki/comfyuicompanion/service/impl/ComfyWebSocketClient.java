package de.tki.comfyuicompanion.service.impl;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Real-time WebSocket client for ComfyUI (/ws) providing live step-by-step progress,
 * active node notifications, and binary latent preview image streaming.
 */
@Component
public class ComfyWebSocketClient {
    private static final Logger logger = LoggerFactory.getLogger(ComfyWebSocketClient.class);

    private final HttpClient httpClient;
    private final String clientId;

    /**
     * Constructs a new ComfyWebSocketClient with a unique client identifier.
     */
    public ComfyWebSocketClient() {
        this.clientId = UUID.randomUUID().toString();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Returns the unique client ID used to correlate ComfyUI prompt executions with WebSocket events.
     *
     * @return the client ID string
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Opens an asynchronous WebSocket session to the ComfyUI server to listen for progress,
     * executing node changes, and binary latent previews for a specific prompt.
     *
     * @param comfyHttpUrl       the base HTTP URL of ComfyUI (e.g. http://127.0.0.1:8188)
     * @param targetPromptId     the prompt ID to filter events for (or null for all events)
     * @param onProgress         callback receiving (currentStep, maxSteps)
     * @param onExecutingNode    callback receiving the currently executing node ID (or null when finished)
     * @param onPreviewImage     callback receiving decoded latent preview images
     * @return an AutoCloseable handle to close the WebSocket session when finished
     */
    public AutoCloseable connect(String comfyHttpUrl,
                                 String targetPromptId,
                                 BiConsumer<Integer, Integer> onProgress,
                                 Consumer<String> onExecutingNode,
                                 Consumer<Image> onPreviewImage) {
        if (comfyHttpUrl == null || comfyHttpUrl.isBlank()) {
            return () -> {};
        }
        try {
            String wsUrl = toWebSocketUrl(comfyHttpUrl, clientId);
            WebSocketListener listener = new WebSocketListener(targetPromptId, onProgress, onExecutingNode, onPreviewImage);
            WebSocket ws = httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .buildAsync(URI.create(wsUrl), listener)
                    .join();
            return () -> {
                try {
                    ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
                } catch (Exception ignored) {}
            };
        } catch (Exception e) {
            logger.debug("WebSocket connection to ComfyUI unavailable (falling back to HTTP polling): {}", e.getMessage());
            return () -> {};
        }
    }

    /**
     * Converts a ComfyUI HTTP base URL into its corresponding WebSocket endpoint URL.
     *
     * @param httpUrl  the base HTTP/HTTPS URL
     * @param clientId the client identifier query parameter
     * @return the formatted ws:// or wss:// URL
     */
    public static String toWebSocketUrl(String httpUrl, String clientId) {
        String trimmed = httpUrl.trim().replaceAll("/+$", "");
        String wsBase;
        if (trimmed.startsWith("https://")) {
            wsBase = "wss://" + trimmed.substring("https://".length());
        } else if (trimmed.startsWith("http://")) {
            wsBase = "ws://" + trimmed.substring("http://".length());
        } else {
            wsBase = "ws://" + trimmed;
        }
        return wsBase + "/ws?clientId=" + clientId;
    }

    private static class WebSocketListener implements WebSocket.Listener {
        private final String targetPromptId;
        private final BiConsumer<Integer, Integer> onProgress;
        private final Consumer<String> onExecutingNode;
        private final Consumer<Image> onPreviewImage;
        private final StringBuilder textBuffer = new StringBuilder();
        private final ByteArrayOutputStream binaryBuffer = new ByteArrayOutputStream();

        WebSocketListener(String targetPromptId,
                          BiConsumer<Integer, Integer> onProgress,
                          Consumer<String> onExecutingNode,
                          Consumer<Image> onPreviewImage) {
            this.targetPromptId = targetPromptId;
            this.onProgress = onProgress;
            this.onExecutingNode = onExecutingNode;
            this.onPreviewImage = onPreviewImage;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                String msg = textBuffer.toString();
                textBuffer.setLength(0);
                handleJsonMessage(msg);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] chunk = new byte[data.remaining()];
            data.get(chunk);
            binaryBuffer.write(chunk, 0, chunk.length);
            if (last) {
                byte[] fullBytes = binaryBuffer.toByteArray();
                binaryBuffer.reset();
                handleBinaryPreview(fullBytes);
            }
            webSocket.request(1);
            return null;
        }

        private void handleJsonMessage(String rawJson) {
            try {
                JSONObject root = new JSONObject(rawJson);
                String type = root.optString("type", "");
                JSONObject data = root.optJSONObject("data");
                if (data == null) return;

                String msgPromptId = data.optString("prompt_id", "");
                if (targetPromptId != null && !targetPromptId.isEmpty()
                        && !msgPromptId.isEmpty() && !targetPromptId.equals(msgPromptId)) {
                    return;
                }

                if ("progress".equals(type) && onProgress != null) {
                    int val = data.optInt("value", 0);
                    int max = data.optInt("max", 0);
                    if (max > 0) {
                        onProgress.accept(val, max);
                    }
                } else if ("executing".equals(type) && onExecutingNode != null) {
                    Object nodeObj = data.opt("node");
                    String nodeId = (nodeObj != null && !JSONObject.NULL.equals(nodeObj)) ? nodeObj.toString() : null;
                    onExecutingNode.accept(nodeId);
                }
            } catch (Exception ignored) {}
        }

        private void handleBinaryPreview(byte[] bytes) {
            // ComfyUI binary preview frame has an 8-byte header (2 big-endian uint32 values: event type = 1, image format = 1/2)
            if (onPreviewImage == null || bytes == null || bytes.length <= 8) return;
            try {
                Image img = ImageIO.read(new ByteArrayInputStream(bytes, 8, bytes.length - 8));
                if (img != null) {
                    onPreviewImage.accept(img);
                }
            } catch (Exception ignored) {}
        }
    }
}
