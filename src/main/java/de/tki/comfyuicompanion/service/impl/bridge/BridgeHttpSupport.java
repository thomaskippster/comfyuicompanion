package de.tki.comfyuicompanion.service.impl.bridge;

import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

/**
 * Shared HTTP plumbing for the embedded REST bridge handlers: CORS headers, bearer-token
 * authorization, JSON responses and raw query-string parsing.
 */
public class BridgeHttpSupport {
    private static final Logger logger = LoggerFactory.getLogger(BridgeHttpSupport.class);

    private final Supplier<String> apiTokenSupplier;

    /**
     * Creates the support helper.
     *
     * @param apiTokenSupplier supplies the currently expected API token (may return {@code null} or empty
     *                         to disable authorization); evaluated on every request so token changes apply live
     */
    public BridgeHttpSupport(Supplier<String> apiTokenSupplier) {
        this.apiTokenSupplier = apiTokenSupplier;
    }

    /**
     * Applies CORS headers, answers pre-flight requests, validates the bearer token and the HTTP method.
     * When this method returns {@code false} the response has already been sent.
     *
     * @param exchange the current exchange
     * @param method   the only permitted HTTP method
     * @return {@code true} if processing should continue
     * @throws IOException if sending an error response fails
     */
    public boolean handleSecurity(HttpExchange exchange, String method) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Private-Network", "true");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return false;
        }

        String expectedApiToken = apiTokenSupplier.get();
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (expectedApiToken != null && !expectedApiToken.isEmpty()) {
            String expected = "Bearer " + expectedApiToken.trim();
            if (authHeader == null || !authHeader.trim().equals(expected)) {
                logger.error("REST Bridge: 401 Unauthorized request from " + exchange.getRemoteAddress());
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

    /**
     * Applies permissive CORS headers and answers pre-flight requests.
     *
     * @param exchange the current exchange
     * @return {@code true} if processing should continue, {@code false} if the request was an OPTIONS call
     * @throws IOException if sending the pre-flight response fails
     */
    public boolean handleCorsAndOptions(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return false;
        }
        return true;
    }

    /**
     * Applies the CORS headers used by the workflow-ready endpoint (POST/OPTIONS only).
     *
     * @param exchange the current exchange
     */
    public void applyWorkflowReadyCors(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Private-Network", "true");
    }

    /**
     * Sends a UTF-8 JSON response with the given status code.
     *
     * @param exchange the current exchange
     * @param code     HTTP status code
     * @param json     serialized JSON body
     * @throws IOException if writing the response fails
     */
    public void sendJsonResponse(HttpExchange exchange, int code, String json) throws IOException {
        byte[] responseBytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    /**
     * Reads and URL-decodes a single query parameter from the raw request query string.
     *
     * @param exchange the current exchange
     * @param name     parameter name
     * @return decoded value, empty string for a valueless parameter, or {@code null} if absent
     */
    public String getQueryParam(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) return null;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int eqIdx = pair.indexOf('=');
            if (eqIdx > 0) {
                String key = pair.substring(0, eqIdx);
                if (key.equals(name)) {
                    String val = pair.substring(eqIdx + 1);
                    try {
                        return URLDecoder.decode(val, StandardCharsets.UTF_8.name());
                    } catch (Exception e) {
                        return val;
                    }
                }
            } else if (eqIdx == -1 && pair.equals(name)) {
                return "";
            }
        }
        return null;
    }
}
