package de.tki.comfyuicompanion.dto.acl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AclDtoTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("BridgeActionAclResponse: should serialize success, accepted, and error properly")
    void testBridgeActionAclResponse() throws Exception {
        BridgeActionAclResponse success = BridgeActionAclResponse.success("Ready");
        assertThat(success.status()).isEqualTo("success");
        assertThat(success.message()).isEqualTo("Ready");
        String successJson = mapper.writeValueAsString(success);
        assertThat(successJson).contains("\"status\":\"success\"").contains("\"message\":\"Ready\"");

        BridgeActionAclResponse accepted = BridgeActionAclResponse.accepted();
        assertThat(accepted.status()).isEqualTo("accepted");
        assertThat(accepted.message()).isNull();
        String acceptedJson = mapper.writeValueAsString(accepted);
        assertThat(acceptedJson).contains("\"status\":\"accepted\"");
        assertThat(acceptedJson).doesNotContain("\"message\"");

        BridgeActionAclResponse error = BridgeActionAclResponse.error("Failure");
        assertThat(error.status()).isEqualTo("error");
        assertThat(error.message()).isEqualTo("Failure");

        assertThatThrownBy(() -> new BridgeActionAclResponse(null, "msg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BridgeActionAclResponse("   ", "msg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("WorkflowImportAclRequest: should parse and unwrap envelopes correctly")
    void testWorkflowImportAclRequest() {
        WorkflowImportAclRequest direct = WorkflowImportAclRequest.fromString("{\"nodes\":[{\"id\":1}]}");
        assertThat(direct.payload().has("nodes")).isTrue();
        assertThat(direct.toDomainWorkflowJson()).contains("\"id\":1");

        WorkflowImportAclRequest wrapped = WorkflowImportAclRequest.fromString("{\"workflow\":{\"nodes\":[{\"id\":2}]}}");
        assertThat(wrapped.payload().has("nodes")).isTrue();
        assertThat(wrapped.toDomainWorkflowJson()).contains("\"id\":2");

        assertThatThrownBy(() -> WorkflowImportAclRequest.fromString(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorkflowImportAclRequest.fromString("  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorkflowImportAclRequest.fromString("invalid json!"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorkflowImportAclRequest(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("WorkflowReadyAclRequest: should parse prompt payload correctly")
    void testWorkflowReadyAclRequest() {
        WorkflowReadyAclRequest req = WorkflowReadyAclRequest.fromString("{\"prompt\":{\"3\":{\"inputs\":{}}}}");
        assertThat(req.promptPayload().has("prompt")).isTrue();
        assertThat(req.toDomainPromptJson()).contains("\"inputs\":{}");

        assertThatThrownBy(() -> WorkflowReadyAclRequest.fromString(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorkflowReadyAclRequest.fromString("  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorkflowReadyAclRequest.fromString("not-json"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorkflowReadyAclRequest(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("WorkflowAnalysisAclRequest: should parse and unwrap envelopes correctly")
    void testWorkflowAnalysisAclRequest() {
        WorkflowAnalysisAclRequest direct = WorkflowAnalysisAclRequest.fromString("{\"nodes\":[{\"type\":\"KSampler\"}]}");
        assertThat(direct.workflow().has("nodes")).isTrue();
        assertThat(direct.toDomainWorkflowJson()).contains("\"type\":\"KSampler\"");

        WorkflowAnalysisAclRequest wrapped = WorkflowAnalysisAclRequest.fromString("{\"workflow\":{\"nodes\":[{\"type\":\"FaceDetailer\"}]}}");
        assertThat(wrapped.workflow().has("nodes")).isTrue();
        assertThat(wrapped.toDomainWorkflowJson()).contains("\"type\":\"FaceDetailer\"");

        assertThatThrownBy(() -> WorkflowAnalysisAclRequest.fromString(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorkflowAnalysisAclRequest.fromString(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorkflowAnalysisAclRequest.fromString("not json"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorkflowAnalysisAclRequest(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
