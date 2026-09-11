package com.thomaskippster.comfyuicompanion.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfymodels.service.impl.RestBridgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RestBridgeControllerTest {

    private RestBridgeService restBridgeService;
    private RestBridgeController controller;

    @BeforeEach
    void setUp() {
        restBridgeService = new RestBridgeService();
        restBridgeService.setApiToken("secret123");

        controller = new RestBridgeController(
                restBridgeService,
                null,
                new ObjectMapper(),
                WebClient.builder()
        );
    }

    @Test
    @DisplayName("importWorkflow: should accept workflow when token is valid and consumer is present")
    void shouldAcceptWorkflowWithValidToken() {
        AtomicReference<String> received = new AtomicReference<>();
        restBridgeService.setWorkflowConsumer(received::set);

        ResponseEntity<String> response = controller.importWorkflow("{\"workflow\": 1}", "Bearer secret123");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(received.get()).isEqualTo("{\"workflow\": 1}");
    }

    @Test
    @DisplayName("importWorkflow: should reject when auth header is wrong")
    void shouldRejectInvalidToken() {
        ResponseEntity<String> response = controller.importWorkflow("{}", "Bearer wrongToken");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("workflowReady: should deliver API JSON to one-shot consumer")
    void shouldDeliverWorkflowReady() {
        AtomicReference<String> delivered = new AtomicReference<>();
        restBridgeService.setWorkflowReadyConsumer(delivered::set);

        ResponseEntity<String> response = controller.workflowReady("{\"prompt\": {\"3\": {}}}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(delivered.get()).contains("prompt");
    }

    @Test
    @DisplayName("proxyPreview: should reject non-loopback URLs (SSRF protection)")
    void shouldRejectExternalUrlsForPreview() {
        var response = controller.proxyPreview("http://google.com/image.png").block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
