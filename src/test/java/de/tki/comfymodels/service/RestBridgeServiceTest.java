package de.tki.comfymodels.service;

import de.tki.comfymodels.service.impl.RestBridgeService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestBridgeServiceTest {

    private RestBridgeService restBridge;
    private final int testPort = 12347;
    private final String testToken = "test-token";
    private final AtomicReference<String> receivedWorkflow = new AtomicReference<>();

    private HttpServer mockComfyServer;
    private final int mockComfyPort = 12348;

    @BeforeEach
    void setUp() {
        restBridge = new RestBridgeService();
        restBridge.setPort(testPort);
        restBridge.setApiToken(testToken);
        restBridge.setWorkflowConsumer(receivedWorkflow::set);
        
        restBridge.startServer();

        // Start Mock ComfyUI server
        try {
            mockComfyServer = HttpServer.create(new InetSocketAddress("127.0.0.1", mockComfyPort), 0);
            mockComfyServer.createContext("/templates/index.json", exchange -> {
                String response = "{\n" +
                        "  \"templates\": [\n" +
                        "    {\n" +
                        "      \"name\": \"flux_base_api\",\n" +
                        "      \"title\": \"FLUX.1 Base\",\n" +
                        "      \"description\": \"Base FLUX template\",\n" +
                        "      \"mediaType\": \"image\",\n" +
                        "      \"mediaSubtype\": \"png\"\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}";
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                exchange.close();
            });
            mockComfyServer.createContext("/templates/flux_base_api-1.png", exchange -> {
                byte[] bytes = "fake-image-bytes".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "image/png");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                exchange.close();
            });
            mockComfyServer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @AfterEach
    void tearDown() {
        restBridge.stopServer();
        if (mockComfyServer != null) {
            mockComfyServer.stop(0);
        }
    }

    @Test
    void testImportWorkflow() throws Exception {
        String testWorkflow = "{\"test\": \"workflow\"}";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + testPort + "/import"))
                .header("Authorization", "Bearer " + testToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(testWorkflow))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\": \"success\""));
        assertEquals(testWorkflow, receivedWorkflow.get());
    }

    @Test
    void testImportWorkflowUnauthorized() throws Exception {
        String testWorkflow = "{\"test\": \"workflow\"}";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + testPort + "/import"))
                .header("Authorization", "Bearer invalid-token")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(testWorkflow))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(401, response.statusCode());
    }

    @Test
    void testGetTemplates() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + testPort + "/api/templates?baseUrl=http://127.0.0.1:" + mockComfyPort))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("flux_base_api"));
        assertTrue(response.body().contains("/api/preview?url="));
    }

    @Test
    void testPreviewProxy() throws Exception {
        String comfyInternalUrl = "http://127.0.0.1:" + mockComfyPort + "/templates/flux_base_api-1.png";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + testPort + "/api/preview?url=" + java.net.URLEncoder.encode(comfyInternalUrl, "UTF-8")))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("image/png", response.headers().firstValue("Content-Type").orElse(null));
        assertEquals("fake-image-bytes", response.body());
    }

    @Test
    void testPreviewProxyBeebleWebp() throws Exception {
        // Set up the mock endpoint on the mock server
        mockComfyServer.createContext("/templates/api_beeble_switchx_video_edit-1.webp", exchange -> {
            byte[] bytes = "fake-webp-bytes".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "image/webp");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            exchange.close();
        });

        String comfyInternalUrl = "http://127.0.0.1:" + mockComfyPort + "/templates/api_beeble_switchx_video_edit-1.webp";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + testPort + "/api/preview?url=" + java.net.URLEncoder.encode(comfyInternalUrl, "UTF-8")))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("image/webp", response.headers().firstValue("Content-Type").orElse(null));
        assertEquals("fake-webp-bytes", response.body());
    }

    @Test
    void testPreviewProxyBeebleWebpFallback() throws Exception {
        // Set up the mock endpoint on the mock server WITHOUT the "-1" suffix
        mockComfyServer.createContext("/templates/api_beeble_switchx_video_edit.webp", exchange -> {
            byte[] bytes = "fallback-webp-bytes".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "image/webp");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            exchange.close();
        });

        // Request URL with the "-1" suffix
        String comfyInternalUrlWithSuffix = "http://127.0.0.1:" + mockComfyPort + "/templates/api_beeble_switchx_video_edit-1.webp";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + testPort + "/api/preview?url=" + java.net.URLEncoder.encode(comfyInternalUrlWithSuffix, "UTF-8")))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // Assert it fell back and retrieved the mock endpoint without "-1"
        assertEquals(200, response.statusCode());
        assertEquals("image/webp", response.headers().firstValue("Content-Type").orElse(null));
        assertEquals("fallback-webp-bytes", response.body());
    }

    @Test
    void testPreviewProxyInvalidProtocol() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + testPort + "/api/preview?url=ftp://127.0.0.1/something.png"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
    }

    @Test
    void testPreviewProxyNonLoopback() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + testPort + "/api/preview?url=http://example.com/something.png"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
    }
}
