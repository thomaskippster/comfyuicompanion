package com.thomaskippster.comfyuicompanion.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.util.concurrent.ExecutionException;

@Service
public class ComfyWebSocketClient {

    private final String baseUrl;
    private final ComfyWebSocketHandler handler;

    public ComfyWebSocketClient(
            @Value("${comfyui.ws.url:ws://127.0.0.1:8188/ws}") String baseUrl,
            ComfyWebSocketHandler handler) {
        this.baseUrl = baseUrl;
        this.handler = handler;
    }

    /**
     * Connects to the ComfyUI WebSocket endpoint using the provided client ID.
     *
     * @param clientId The unique client identifier used when triggering the workflow.
     */
    public void connect(String clientId) {
        StandardWebSocketClient client = new StandardWebSocketClient();
        String uriStr = baseUrl + "?clientId=" + clientId;
        try {
            // Establish the connection and block until connected
            client.execute(handler, uriStr).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to connect to ComfyUI WebSocket at " + uriStr, e);
        }
    }
}
