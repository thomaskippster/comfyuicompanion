package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import de.tki.comfyuicompanion.service.impl.bridge.BridgeHttpSupport;
import de.tki.comfyuicompanion.service.impl.bridge.BridgeWorkflowChannel;
import de.tki.comfyuicompanion.service.impl.bridge.ImportHandler;
import de.tki.comfyuicompanion.service.impl.bridge.MediaContentTypeResolver;
import de.tki.comfyuicompanion.service.impl.bridge.PreviewHandler;
import de.tki.comfyuicompanion.service.impl.bridge.ProxyUrlValidator;
import de.tki.comfyuicompanion.service.impl.bridge.TemplateCatalogMapper;
import de.tki.comfyuicompanion.service.impl.bridge.TemplatesHandler;
import de.tki.comfyuicompanion.service.impl.bridge.WorkflowReadyHandler;
import de.tki.comfyuicompanion.util.ConfigConstants;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Service managing the embedded HTTP REST bridge and reactive template proxies
 * for interacting with ComfyUI browser instances and extensions.
 */
@Service
public class RestBridgeService {
    private static final Logger logger = LoggerFactory.getLogger(RestBridgeService.class);

    private HttpServer server;
    private ExecutorService serverExecutor;
    private int port = 12345;
    private String expectedApiToken;

    private ConfigService configService;
    private final BridgeWorkflowChannel workflowChannel = new BridgeWorkflowChannel();
    private final TemplateCatalogMapper templateCatalogMapper = new TemplateCatalogMapper();
    private final ProxyUrlValidator proxyUrlValidator = new ProxyUrlValidator();
    private final MediaContentTypeResolver contentTypeResolver = new MediaContentTypeResolver();
    private final BridgeHttpSupport httpSupport;

    @Autowired
    public RestBridgeService(@Autowired(required = false) ConfigService configService) {
        this.configService = configService;
        this.httpSupport = new BridgeHttpSupport(this::getExpectedApiToken);
    }

    public RestBridgeService() {
        this(null);
    }

    /**
     * Sets the configuration service reference.
     *
     * @param configService configuration service
     */
    public void setConfigService(ConfigService configService) {
        this.configService = configService;
    }

    /**
     * Configures the listening port for the embedded bridge server.
     *
     * @param port target port
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Configures the expected bearer authorization token.
     *
     * @param token bearer token
     */
    public void setApiToken(String token) {
        this.expectedApiToken = token;
    }

    /**
     * Registers a persistent consumer for incoming workflows from the browser.
     *
     * @param consumer workflow JSON consumer
     */
    public void setWorkflowConsumer(Consumer<String> consumer) {
        this.workflowChannel.setWorkflowConsumer(consumer);
    }

    /**
     * Dispatches workflow payload to the registered consumer.
     *
     * @param workflowJson workflow JSON string
     * @return true if consumed
     */
    public boolean consumeWorkflow(String workflowJson) {
        return this.workflowChannel.consumeWorkflow(workflowJson);
    }

    /**
     * Registers a one-shot consumer for the next browser-converted workflow payload.
     *
     * @param consumer one-shot consumer
     */
    public void setWorkflowReadyConsumer(Consumer<String> consumer) {
        this.workflowChannel.setWorkflowReadyConsumer(consumer);
    }

    /**
     * Dispatches an API-format payload to the one-shot consumer and clears it immediately.
     *
     * @param apiJson converted workflow JSON
     * @return true if accepted
     */
    public boolean consumeWorkflowReady(String apiJson) {
        return this.workflowChannel.consumeWorkflowReady(apiJson);
    }

    /**
     * Returns the currently expected API bearer token.
     *
     * @return expected token or null
     */
    public String getExpectedApiToken() {
        return expectedApiToken;
    }

    /**
     * Starts the embedded HTTP server if port is not served natively by WebFlux.
     */
    public void startServer() {
        if (server != null) return;
        if (port == 12345) {
            logger.info("REST Bridge endpoints are actively served by Spring WebFlux on port 12345.");
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            serverExecutor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("RestBridge-Worker-", 0).factory()
            );
            server.setExecutor(serverExecutor);
            server.createContext("/import", new ImportHandler(httpSupport, workflowChannel));
            server.createContext("/api/templates", new TemplatesHandler(httpSupport, templateCatalogMapper, this::resolveComfyUIUrl));
            server.createContext("/api/preview", new PreviewHandler(httpSupport, proxyUrlValidator, contentTypeResolver));
            server.createContext("/api/workflow-ready", new WorkflowReadyHandler(httpSupport, workflowChannel));
            server.start();
            logger.info("REST Bridge started on port " + port);
        } catch (IOException e) {
            logger.error("Failed to start REST Bridge: " + e.getMessage());
        }
    }

    private String resolveComfyUIUrl() {
        return configService != null ? configService.getComfyUIUrl() : ConfigConstants.DEFAULT_COMFYUI_URL;
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

    /**
     * Fetches the template catalog from ComfyUI and enriches each template with internal preview URLs.
     *
     * @param requestedBaseUrl optional ComfyUI base URL requested by caller
     * @param webClient reactive WebClient instance
     * @param objectMapper Jackson ObjectMapper instance
     * @return Mono publishing the structured templates JsonNode
     */
    public Mono<JsonNode> getTemplatesIndex(String requestedBaseUrl, WebClient webClient, ObjectMapper objectMapper) {
        String baseUrl = (requestedBaseUrl != null && !requestedBaseUrl.isBlank())
                ? requestedBaseUrl
                : resolveComfyUIUrl();

        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = ConfigConstants.DEFAULT_COMFYUI_URL;
        }

        final String effectiveBaseUrl = baseUrl.replaceAll("/+$", "");
        final ObjectMapper mapper = objectMapper != null ? objectMapper : new ObjectMapper();

        return webClient.get()
                .uri(effectiveBaseUrl + "/templates/index.json")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(10))
                .map(rootNode -> (JsonNode) templateCatalogMapper.buildTemplatesResponse(rootNode, effectiveBaseUrl, mapper));
    }

    /**
     * Extracts and flattens template definitions from a hierarchical ComfyUI templates JSON tree.
     *
     * @param rootNode The root JSON node of the template catalog
     * @param mapper The Jackson ObjectMapper to construct nodes
     * @return List of flattened ObjectNodes representing individual templates
     */
    public List<ObjectNode> extractFlattenedTemplates(JsonNode rootNode, ObjectMapper mapper) {
        return templateCatalogMapper.extractFlattenedTemplates(rootNode, mapper);
    }
}
