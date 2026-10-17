package de.tki.comfyuicompanion.service.impl.bridge;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Receives the API-format JSON produced by the browser's {@code app.graphToPrompt()} call
 * and dispatches it to the one-shot workflow-ready consumer.
 */
public class WorkflowReadyHandler implements HttpHandler {
    private final BridgeHttpSupport http;
    private final BridgeWorkflowChannel channel;

    /**
     * Creates the handler.
     *
     * @param http    shared HTTP helper
     * @param channel workflow consumer channel
     */
    public WorkflowReadyHandler(BridgeHttpSupport http, BridgeWorkflowChannel channel) {
        this.http = http;
        this.channel = channel;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            http.applyWorkflowReadyCors(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            try (InputStream is = exchange.getRequestBody()) {
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                if (channel.consumeWorkflowReady(body)) {
                    http.sendJsonResponse(exchange, 200, "{\"status\": \"accepted\"}");
                } else {
                    http.sendJsonResponse(exchange, 409, "{\"status\": \"error\", \"message\": \"No pending conversion request\"}");
                }
            }
        } catch (Exception e) {
            try { http.sendJsonResponse(exchange, 500, "{\"status\": \"error\"}"); } catch (Exception ignored) {}
        } finally {
            exchange.close();
        }
    }
}
