package de.tki.comfymodels.service.impl;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

@Service
public class RestBridgeService {

    private HttpServer server;
    private Consumer<String> workflowConsumer;
    private int port = 12345;
    private String expectedApiToken;

    public void setPort(int port) {
        this.port = port;
    }

    public void setApiToken(String token) {
        this.expectedApiToken = token;
    }

    public void setWorkflowConsumer(Consumer<String> consumer) {
        this.workflowConsumer = consumer;
    }

    public void startServer() {
        if (server != null) return;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            server.createContext("/import", new ImportHandler());
            server.setExecutor(null);
            server.start();
            System.out.println("REST Bridge started on port " + port);
        } catch (IOException e) {
            System.err.println("Failed to start REST Bridge: " + e.getMessage());
        }
    }

    @PreDestroy
    public void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private boolean handleSecurity(HttpExchange exchange, String method) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return false;
        }

        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (expectedApiToken != null && !expectedApiToken.isEmpty()) {
            String expected = "Bearer " + expectedApiToken.trim();
            if (authHeader == null || !authHeader.trim().equals(expected)) {
                System.err.println("REST Bridge: 401 Unauthorized request from " + exchange.getRemoteAddress());
                exchange.sendResponseHeaders(401, -1);
                return false;
            }
        }

        if (!method.equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return false;
        }
        return true;
    }

    private void sendJsonResponse(HttpExchange exchange, int code, String json) throws IOException {
        byte[] responseBytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private class ImportHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!handleSecurity(exchange, "POST")) return;
                try (InputStream is = exchange.getRequestBody()) {
                    String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    if (workflowConsumer != null) {
                        workflowConsumer.accept(body);
                        sendJsonResponse(exchange, 200, "{\"status\": \"success\", \"message\": \"Workflow received\"}");
                    } else {
                        sendJsonResponse(exchange, 503, "{\"status\": \"error\", \"message\": \"App not ready\"}");
                    }
                }
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"status\": \"error\", \"message\": \"" + e.getMessage() + "\"}");
            } finally {
                exchange.close();
            }
        }
    }
}
