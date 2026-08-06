package de.tki.comfymodels.service.impl;

import de.tki.comfymodels.util.ConfigConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class RestBridgeService {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(RestBridgeService.class);

    private HttpServer server;
    private ExecutorService serverExecutor;
    private Consumer<String> workflowConsumer;
    /** One-shot consumer: receives the API JSON returned by app.graphToPrompt() and clears itself. */
    private volatile Consumer<String> workflowReadyConsumer;
    private int port = 12345;
    private String expectedApiToken;

    @Autowired(required = false)
    private ConfigService configService;

    public void setConfigService(ConfigService configService) {
        this.configService = configService;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setApiToken(String token) {
        this.expectedApiToken = token;
    }

    public void setWorkflowConsumer(Consumer<String> consumer) {
        this.workflowConsumer = consumer;
    }

    /** Registers a one-shot consumer for the next browser-converted workflow payload. */
    public void setWorkflowReadyConsumer(Consumer<String> consumer) {
        this.workflowReadyConsumer = consumer;
    }

    public void startServer() {
        if (server != null) return;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            serverExecutor = Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "RestBridge-Worker");
                t.setDaemon(true);
                return t;
            });
            server.setExecutor(serverExecutor);
            server.createContext("/import", new ImportHandler());
            server.createContext("/api/templates", new TemplatesHandler());
            server.createContext("/api/preview", new PreviewHandler());
            server.createContext("/api/workflow-ready", new WorkflowReadyHandler());
            server.start();
            logger.info("REST Bridge started on port " + port);
        } catch (IOException e) {
            logger.error("Failed to start REST Bridge: " + e.getMessage());
        }
    }

    @PreDestroy
    public void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (serverExecutor != null) {
            serverExecutor.shutdown();
            try {
                if (!serverExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    serverExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                serverExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            serverExecutor = null;
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

    /**
     * Receives the API-format JSON produced by the browser's {@code app.graphToPrompt()} call
     * and dispatches it to the one-shot {@link #workflowReadyConsumer}.
     */
    private class WorkflowReadyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
                exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
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
                    Consumer<String> consumer = workflowReadyConsumer;
                    workflowReadyConsumer = null; // one-shot: clear immediately
                    if (consumer != null) {
                        consumer.accept(body);
                        sendJsonResponse(exchange, 200, "{\"status\": \"accepted\"}");
                    } else {
                        sendJsonResponse(exchange, 409, "{\"status\": \"error\", \"message\": \"No pending conversion request\"}");
                    }
                }
            } catch (Exception e) {
                try { sendJsonResponse(exchange, 500, "{\"status\": \"error\"}"); } catch (Exception ignored) {}
            } finally {
                exchange.close();
            }
        }
    }

    private String getQueryParam(HttpExchange exchange, String name) {
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
                        return java.net.URLDecoder.decode(val, StandardCharsets.UTF_8.name());
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

    private boolean isValidProxyUrl(String urlStr) {
        if (urlStr == null || urlStr.isEmpty()) {
            return false;
        }
        URI uri;
        try {
            uri = new URI(urlStr);
        } catch (Exception e) {
            return false;
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            return false;
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            return false;
        }
        // Only allow IP literals in the loopback range. We refuse DNS lookups
        // entirely so a domain cannot be used for DNS-rebinding SSRF.
        if (!isLoopbackLiteral(host)) {
            return false;
        }
        // Confirm the literal really is loopback (defense against weird inputs
        // that just happen to start with "127." but are not real IPv4 literals).
        try {
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private boolean isLoopbackLiteral(String host) {
        if (host == null) {
            return false;
        }
        if (host.equals("::1") || host.equals("0:0:0:0:0:0:0:1")) {
            return true;
        }
        return host.startsWith("127.");
    }

    private boolean handleCorsAndOptions(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return false;
        }
        return true;
    }

    private void flattenTemplatesNode(JsonNode node, List<ObjectNode> list, String currentCategory, String parentTrail, ObjectMapper mapper) {
        if (node == null) return;
        if (node.isArray()) {
            for (JsonNode child : node) {
                flattenTemplatesNode(child, list, currentCategory, parentTrail, mapper);
            }
        } else if (node.isObject()) {
            String groupTitle = node.path("title").asText("");
            JsonNode templatesNode = node.path("templates");
            
            if (templatesNode.isArray()) {
                String nextCategory = currentCategory.isEmpty() ? groupTitle : currentCategory;
                String nextTrail = parentTrail.isEmpty() ? groupTitle : parentTrail + " / " + groupTitle;
                flattenTemplatesNode(templatesNode, list, nextCategory, nextTrail, mapper);
                return;
            }

            if (node.has("name") && (node.has("title") || node.has("description") || node.has("mediaType"))) {
                String name = node.path("name").asText("");
                String title = node.path("title").asText(name);
                String description = node.path("description").asText("");
                String mediaSubtype = node.path("mediaSubtype").asText("");
                
                String category = currentCategory;
                if (category.isEmpty()) {
                    category = parentTrail;
                }
                if (category.isEmpty()) {
                    category = "Uncategorized";
                }
                
                ObjectNode templateObj = mapper.createObjectNode();
                templateObj.put("name", name);
                templateObj.put("title", title);
                templateObj.put("description", description);
                templateObj.put("category", category);
                templateObj.put("mediaSubtype", mediaSubtype);
                
                list.add(templateObj);
            }
            
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (!field.getKey().equals("templates")) {
                    flattenTemplatesNode(field.getValue(), list, currentCategory, parentTrail, mapper);
                }
            }
        }
    }

    private class TemplatesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!handleCorsAndOptions(exchange)) return;
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                
                String baseUrl = getQueryParam(exchange, "baseUrl");
                if (baseUrl == null || baseUrl.isEmpty()) {
                    if (configService != null) {
                        baseUrl = configService.getComfyUIUrl();
                    }
                }
                if (baseUrl == null || baseUrl.isEmpty()) {
                    baseUrl = ConfigConstants.DEFAULT_COMFYUI_URL;
                }
                
                java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(5))
                    .build();

                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(baseUrl + "/templates/index.json"))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .GET()
                    .build();

                java.net.http.HttpResponse<String> response = client.send(
                    request, java.net.http.HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    sendJsonResponse(exchange, response.statusCode(), "{\"error\": \"Failed to fetch templates index from ComfyUI\"}");
                    return;
                }

                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootNode = mapper.readTree(response.body());
                
                List<ObjectNode> flattened = new ArrayList<>();
                flattenTemplatesNode(rootNode, flattened, "", "", mapper);
                
                ArrayNode templatesArray = mapper.createArrayNode();
                for (ObjectNode templateNode : flattened) {
                    String templateName = templateNode.path("name").asText("");
                    String mediaSubtype = templateNode.path("mediaSubtype").asText("");
                    
                    if (!mediaSubtype.isEmpty()) {
                        String comfyuiInternalUrl = baseUrl + "/templates/" + java.net.URLEncoder.encode(templateName, StandardCharsets.UTF_8.name()).replace("+", "%20") + "-1." + mediaSubtype;
                        String clientPreviewUrl = "/api/preview?url=" + java.net.URLEncoder.encode(comfyuiInternalUrl, StandardCharsets.UTF_8.name()).replace("+", "%20");
                        templateNode.put("previewUrl", clientPreviewUrl);
                    }
                    templatesArray.add(templateNode);
                }
                
                ObjectNode responseJson = mapper.createObjectNode();
                responseJson.set("templates", templatesArray);
                
                sendJsonResponse(exchange, 200, mapper.writeValueAsString(responseJson));
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"error\": \"" + e.getMessage() + "\"}");
            } finally {
                exchange.close();
            }
        }
    }

    private class PreviewHandler implements HttpHandler {
        private String getFallbackUrl(String url) {
            int lastDot = url.lastIndexOf('.');
            if (lastDot != -1) {
                String base = url.substring(0, lastDot);
                String ext = url.substring(lastDot);
                if (base.endsWith("-1")) {
                    return base.substring(0, base.length() - 2) + ext;
                }
            }
            return null;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!handleCorsAndOptions(exchange)) return;
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }

                String remoteUrl = getQueryParam(exchange, "url");
                if (remoteUrl == null || remoteUrl.isEmpty() || !isValidProxyUrl(remoteUrl)) {
                    exchange.sendResponseHeaders(400, -1);
                    return;
                }

                java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(5))
                    .build();

                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(remoteUrl))
                    .header("User-Agent", "ComfyFS-Java/0.1")
                    .timeout(java.time.Duration.ofSeconds(30))
                    .GET()
                    .build();

                java.net.http.HttpResponse<java.io.InputStream> response = client.send(
                    request, java.net.http.HttpResponse.BodyHandlers.ofInputStream());

                int statusCode = response.statusCode();
                if (statusCode != 200) {
                    // Try fallback if url contains "-1." before extension
                    String fallbackUrl = getFallbackUrl(remoteUrl);
                    if (fallbackUrl != null && isValidProxyUrl(fallbackUrl)) {
                        java.net.http.HttpRequest fallbackRequest = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(fallbackUrl))
                            .header("User-Agent", "ComfyFS-Java/0.1")
                            .timeout(java.time.Duration.ofSeconds(30))
                            .GET()
                            .build();
                        java.net.http.HttpResponse<java.io.InputStream> fallbackResponse = client.send(
                            fallbackRequest, java.net.http.HttpResponse.BodyHandlers.ofInputStream());
                        if (fallbackResponse.statusCode() == 200) {
                            response = fallbackResponse;
                            statusCode = 200;
                            remoteUrl = fallbackUrl;
                        }
                    }
                }

                if (statusCode != 200) {
                    exchange.sendResponseHeaders(statusCode, -1);
                    return;
                }

                String contentType = response.headers().firstValue("Content-Type").orElse(null);
                if (contentType == null || contentType.trim().isEmpty() || contentType.equalsIgnoreCase("application/octet-stream")) {
                    String lowerUrl = remoteUrl.toLowerCase();
                    if (lowerUrl.contains(".png")) {
                        contentType = "image/png";
                    } else if (lowerUrl.contains(".jpg") || lowerUrl.contains(".jpeg")) {
                        contentType = "image/jpeg";
                    } else if (lowerUrl.contains(".webp")) {
                        contentType = "image/webp";
                    } else if (lowerUrl.contains(".gif")) {
                        contentType = "image/gif";
                    } else if (lowerUrl.contains(".mp4")) {
                        contentType = "video/mp4";
                    } else if (lowerUrl.contains(".webm")) {
                        contentType = "video/webm";
                    } else if (lowerUrl.contains(".mov")) {
                        contentType = "video/quicktime";
                    } else {
                        contentType = "application/octet-stream";
                    }
                }

                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.getResponseHeaders().set("Cache-Control", "public, max-age=3600");

                long contentLength = -1;
                Optional<String> lenOpt = response.headers().firstValue("Content-Length");
                if (lenOpt.isPresent()) {
                    try {
                        contentLength = Long.parseLong(lenOpt.get());
                    } catch (NumberFormatException ignored) {}
                }

                exchange.sendResponseHeaders(200, contentLength > 0 ? contentLength : 0);

                try (InputStream in = response.body();
                     OutputStream out = exchange.getResponseBody()) {
                    in.transferTo(out);
                }
            } catch (Exception e) {
                logger.error("REST Bridge: Error fetching preview: " + e.getMessage());
                e.printStackTrace();
                exchange.sendResponseHeaders(500, -1);
            } finally {
                exchange.close();
            }
        }
    }
}
