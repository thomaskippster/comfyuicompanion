package com.thomaskippster.comfyuicompanion.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.RestBridgeService;
import de.tki.comfymodels.util.ConfigConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Enterprise Reactive Controller consolidating ComfyUI Canvas Bridge communication:
 * 1. /import -> Direct 1-click workflow import from the floating Rocket button
 * 2. /api/workflow-ready -> Browser graphToPrompt conversion callback
 * 3. /api/templates -> Proxied template catalog from ComfyUI
 * 4. /api/preview -> SSRF-hardened thumbnail preview proxy
 */
@RestController
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*", "http://[::1]:*"})
public class RestBridgeController {

    private static final Logger logger = LoggerFactory.getLogger(RestBridgeController.class);

    private final RestBridgeService restBridgeService;
    private final ConfigService configService;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    @Autowired
    public RestBridgeController(RestBridgeService restBridgeService,
                                @Autowired(required = false) ConfigService configService,
                                ObjectMapper objectMapper,
                                WebClient.Builder webClientBuilder) {
        this.restBridgeService = restBridgeService;
        this.configService = configService;
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder.build();
    }

    @PostMapping(value = "/import", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> importWorkflow(@RequestBody String workflowJson,
                                                 @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        if (!isAuthorized(authHeader)) {
            logger.warn("REST Bridge /import: Unauthorized request.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("{\"status\": \"error\", \"message\": \"Unauthorized\"}");
        }

        boolean accepted = restBridgeService.consumeWorkflow(workflowJson);
        if (accepted) {
            return ResponseEntity.ok("{\"status\": \"success\", \"message\": \"Workflow received\"}");
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("{\"status\": \"error\", \"message\": \"App not ready\"}");
        }
    }

    @PostMapping(value = "/api/workflow-ready", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> workflowReady(@RequestBody String convertedApiJson) {
        boolean accepted = restBridgeService.consumeWorkflowReady(convertedApiJson);
        if (accepted) {
            return ResponseEntity.ok("{\"status\": \"accepted\"}");
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("{\"status\": \"error\", \"message\": \"No pending conversion request\"}");
        }
    }

    @GetMapping(value = "/api/templates", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<JsonNode>> getTemplates(@RequestParam(value = "baseUrl", required = false) String requestedBaseUrl) {
        String baseUrl = (requestedBaseUrl != null && !requestedBaseUrl.isBlank())
                ? requestedBaseUrl
                : (configService != null ? configService.getComfyUIUrl() : ConfigConstants.DEFAULT_COMFYUI_URL);

        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = ConfigConstants.DEFAULT_COMFYUI_URL;
        }

        final String effectiveBaseUrl = baseUrl.replaceAll("/+$", "");

        return webClient.get()
                .uri(effectiveBaseUrl + "/templates/index.json")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(10))
                .map(rootNode -> {
                    List<ObjectNode> flattened = new ArrayList<>();
                    flattenTemplatesNode(rootNode, flattened, "", "", objectMapper);

                    ArrayNode templatesArray = objectMapper.createArrayNode();
                    for (ObjectNode templateNode : flattened) {
                        String templateName = templateNode.path("name").asText("");
                        String mediaSubtype = templateNode.path("mediaSubtype").asText("");

                        if (!mediaSubtype.isEmpty()) {
                            try {
                                String comfyuiInternalUrl = effectiveBaseUrl + "/templates/"
                                        + URLEncoder.encode(templateName, StandardCharsets.UTF_8).replace("+", "%20")
                                        + "-1." + mediaSubtype;
                                String clientPreviewUrl = "/api/preview?url="
                                        + URLEncoder.encode(comfyuiInternalUrl, StandardCharsets.UTF_8).replace("+", "%20");
                                templateNode.put("previewUrl", clientPreviewUrl);
                            } catch (Exception ignored) {}
                        }
                        templatesArray.add(templateNode);
                    }

                    ObjectNode responseJson = objectMapper.createObjectNode();
                    responseJson.set("templates", templatesArray);
                    return ResponseEntity.ok((JsonNode) responseJson);
                })
                .onErrorResume(ex -> {
                    logger.error("Failed to fetch templates from ComfyUI {}: {}", effectiveBaseUrl, ex.getMessage());
                    ObjectNode errNode = objectMapper.createObjectNode();
                    errNode.put("error", "Failed to fetch templates index from ComfyUI: " + ex.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errNode));
                });
    }

    @GetMapping("/api/preview")
    public Mono<ResponseEntity<byte[]>> proxyPreview(@RequestParam("url") String remoteUrl) {
        if (remoteUrl == null || !isValidProxyUrl(remoteUrl)) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return webClient.get()
                .uri(remoteUrl)
                .header(HttpHeaders.USER_AGENT, "ComfyCompanion-ReactiveBridge/1.0")
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        MediaType contentType = response.headers().contentType().orElse(MediaType.IMAGE_PNG);
                        return response.bodyToMono(byte[].class)
                                .map(bytes -> ResponseEntity.ok().contentType(contentType).body(bytes));
                    }

                    String fallback = getFallbackUrl(remoteUrl);
                    if (fallback != null && isValidProxyUrl(fallback)) {
                        return webClient.get()
                                .uri(fallback)
                                .header(HttpHeaders.USER_AGENT, "ComfyCompanion-ReactiveBridge/1.0")
                                .exchangeToMono(fbResponse -> {
                                    if (fbResponse.statusCode().is2xxSuccessful()) {
                                        MediaType fbType = fbResponse.headers().contentType().orElse(MediaType.IMAGE_PNG);
                                        return fbResponse.bodyToMono(byte[].class)
                                                .map(bytes -> ResponseEntity.ok().contentType(fbType).body(bytes));
                                    }
                                    return Mono.just(ResponseEntity.status(fbResponse.statusCode()).<byte[]>build());
                                });
                    }
                    return Mono.just(ResponseEntity.status(response.statusCode()).<byte[]>build());
                })
                .onErrorResume(ex -> {
                    logger.warn("Preview fetch error for {}: {}", remoteUrl, ex.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY).<byte[]>build());
                });
    }

    private boolean isAuthorized(String authHeader) {
        String token = restBridgeService.getExpectedApiToken();
        if (token == null || token.isBlank()) {
            return true;
        }
        String expected = "Bearer " + token.trim();
        return authHeader != null && authHeader.trim().equals(expected);
    }

    private boolean isValidProxyUrl(String urlStr) {
        if (urlStr == null || urlStr.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(urlStr);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return false;
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return false;
            }
            if (!isLoopbackLiteral(host)) {
                return false;
            }
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isLoopbackLiteral(String host) {
        if (host == null) return false;
        if (host.equals("::1") || host.equals("0:0:0:0:0:0:0:1") || host.equalsIgnoreCase("localhost")) {
            return true;
        }
        return host.startsWith("127.");
    }

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
}
