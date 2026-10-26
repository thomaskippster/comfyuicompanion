package de.tki.comfyuicompanion.service.impl.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.tki.comfyuicompanion.util.ConfigConstants;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Handles {@code GET /api/templates}: fetches the ComfyUI template catalog and returns a flattened,
 * preview-enriched representation.
 */
public class TemplatesHandler implements HttpHandler {
    private final BridgeHttpSupport http;
    private final TemplateCatalogMapper catalogMapper;
    private final Supplier<String> defaultBaseUrlSupplier;

    /**
     * Creates the handler.
     *
     * @param http                   shared HTTP helper
     * @param catalogMapper          catalog flattening helper
     * @param defaultBaseUrlSupplier supplies the configured ComfyUI URL (may return {@code null})
     */
    public TemplatesHandler(BridgeHttpSupport http, TemplateCatalogMapper catalogMapper,
                            Supplier<String> defaultBaseUrlSupplier) {
        this.http = http;
        this.catalogMapper = catalogMapper;
        this.defaultBaseUrlSupplier = defaultBaseUrlSupplier;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!http.handleCorsAndOptions(exchange)) return;
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String baseUrl = http.getQueryParam(exchange, "baseUrl");
            if (baseUrl == null || baseUrl.isEmpty()) {
                baseUrl = defaultBaseUrlSupplier.get();
            }
            if (baseUrl == null || baseUrl.isEmpty()) {
                baseUrl = ConfigConstants.DEFAULT_COMFYUI_URL;
            }

            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/templates/index.json"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            ObjectMapper mapper = new ObjectMapper();
            if (response.statusCode() != 200) {
                http.sendJsonResponse(exchange, response.statusCode(), de.tki.comfyuicompanion.dto.acl.BridgeActionAclResponse.error("Failed to fetch templates index from ComfyUI").toJson());
                return;
            }

            JsonNode rootNode = mapper.readTree(response.body());
            ObjectNode responseJson = catalogMapper.buildTemplatesResponse(rootNode, baseUrl, mapper);

            http.sendJsonResponse(exchange, 200, mapper.writeValueAsString(responseJson));
        } catch (Exception e) {
            http.sendJsonResponse(exchange, 500, de.tki.comfyuicompanion.dto.acl.BridgeActionAclResponse.error(e.getMessage() != null ? e.getMessage() : "Internal error").toJson());
        } finally {
            exchange.close();
        }
    }
}
