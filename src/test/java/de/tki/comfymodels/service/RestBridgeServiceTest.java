package de.tki.comfymodels.service;

import de.tki.comfymodels.service.impl.RestBridgeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestBridgeServiceTest {

    private RestBridgeService restBridge;
    private final int testPort = 12347;
    private final String testToken = "test-token";
    private final AtomicReference<String> receivedWorkflow = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        restBridge = new RestBridgeService();
        restBridge.setPort(testPort);
        restBridge.setApiToken(testToken);
        restBridge.setWorkflowConsumer(receivedWorkflow::set);
        
        restBridge.startServer();
    }

    @AfterEach
    void tearDown() {
        restBridge.stopServer();
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
}
