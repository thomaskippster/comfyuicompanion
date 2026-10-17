package de.tki.comfyuicompanion.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfyuicompanion.service.impl.RestBridgeService;
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

        var request = de.tki.comfyuicompanion.dto.acl.WorkflowImportAclRequest.fromString("{\"workflow\": 1}");
        ResponseEntity<de.tki.comfyuicompanion.dto.acl.BridgeActionAclResponse> response =
                controller.importWorkflow(request, "Bearer secret123");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("success");
        assertThat(response.getBody().message()).isEqualTo("Workflow received");
        assertThat(received.get()).contains("1");
    }

    @Test
    @DisplayName("importWorkflow: should reject when auth header is wrong")
    void shouldRejectInvalidToken() {
        var request = de.tki.comfyuicompanion.dto.acl.WorkflowImportAclRequest.fromString("{}");
        ResponseEntity<de.tki.comfyuicompanion.dto.acl.BridgeActionAclResponse> response =
                controller.importWorkflow(request, "Bearer wrongToken");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("error");
        assertThat(response.getBody().message()).isEqualTo("Unauthorized");
    }

    @Test
    @DisplayName("importWorkflow: should return 503 when consumer is not ready")
    void shouldReturnServiceUnavailableWhenConsumerNotReady() {
        var request = de.tki.comfyuicompanion.dto.acl.WorkflowImportAclRequest.fromString("{\"workflow\": 1}");
        ResponseEntity<de.tki.comfyuicompanion.dto.acl.BridgeActionAclResponse> response =
                controller.importWorkflow(request, "Bearer secret123");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("error");
        assertThat(response.getBody().message()).isEqualTo("App not ready");
    }

    @Test
    @DisplayName("workflowReady: should deliver API JSON to one-shot consumer")
    void shouldDeliverWorkflowReady() {
        AtomicReference<String> delivered = new AtomicReference<>();
        restBridgeService.setWorkflowReadyConsumer(delivered::set);

        var request = de.tki.comfyuicompanion.dto.acl.WorkflowReadyAclRequest.fromString("{\"prompt\": {\"3\": {}}}");
        ResponseEntity<de.tki.comfyuicompanion.dto.acl.BridgeActionAclResponse> response =
                controller.workflowReady(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("accepted");
        assertThat(delivered.get()).contains("prompt");
    }

    @Test
    @DisplayName("workflowReady: should return 409 conflict when no consumer pending")
    void shouldReturnConflictWhenNoConsumerPending() {
        var request = de.tki.comfyuicompanion.dto.acl.WorkflowReadyAclRequest.fromString("{\"prompt\": {\"3\": {}}}");
        ResponseEntity<de.tki.comfyuicompanion.dto.acl.BridgeActionAclResponse> response =
                controller.workflowReady(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("error");
        assertThat(response.getBody().message()).isEqualTo("No pending conversion request");
    }

    @Test
    @DisplayName("proxyPreview: should reject non-loopback URLs (SSRF protection)")
    void shouldRejectExternalUrlsForPreview() {
        var response = controller.proxyPreview("http://google.com/image.png").block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("getTemplates: should handle connection error gracefully with BAD_GATEWAY")
    void shouldHandleTemplatesConnectionError() {
        var response = controller.getTemplates("http://127.0.0.1:9999").block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().has("error")).isTrue();
    }
}
