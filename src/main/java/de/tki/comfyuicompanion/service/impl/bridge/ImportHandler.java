package de.tki.comfyuicompanion.service.impl.bridge;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Handles {@code POST /import}: forwards an imported workflow to the registered workflow consumer.
 */
public class ImportHandler implements HttpHandler {
    private final BridgeHttpSupport http;
    private final BridgeWorkflowChannel channel;

    /**
     * Creates the handler.
     *
     * @param http    shared HTTP helper
     * @param channel workflow consumer channel
     */
    public ImportHandler(BridgeHttpSupport http, BridgeWorkflowChannel channel) {
        this.http = http;
        this.channel = channel;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!http.handleSecurity(exchange, "POST")) return;
            try (InputStream is = exchange.getRequestBody()) {
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                if (channel.consumeWorkflow(body)) {
                    http.sendJsonResponse(exchange, 200, de.tki.comfyuicompanion.dto.acl.BridgeActionAclResponse.success("Workflow received").toJson());
                } else {
                    http.sendJsonResponse(exchange, 503, de.tki.comfyuicompanion.dto.acl.BridgeActionAclResponse.error("App not ready").toJson());
                }
            }
        } catch (Exception e) {
            http.sendJsonResponse(exchange, 500, de.tki.comfyuicompanion.dto.acl.BridgeActionAclResponse.error(e.getMessage() != null ? e.getMessage() : "Internal error").toJson());
        } finally {
            exchange.close();
        }
    }
}
