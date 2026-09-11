package com.thomaskippster.comfyuicompanion.service.provisioning;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CustomNodeResolverServiceTest {

    private CustomNodeResolverService resolverService;

    @BeforeEach
    void setUp() {
        resolverService = new CustomNodeResolverService(
                new ObjectMapper(),
                WebClient.builder(),
                null,
                null
        );
    }

    @Test
    @DisplayName("extractNodeTypes: should extract node types from GUI workflow format")
    void shouldExtractTypesFromGuiWorkflow() {
        String guiWorkflow = """
                {
                  "nodes": [
                    { "id": 1, "type": "KSampler" },
                    { "id": 2, "type": "FaceDetailer" },
                    { "id": 3, "type": "UltimateSDUpscale" }
                  ]
                }
                """;

        Set<String> types = resolverService.extractNodeTypes(guiWorkflow);

        assertThat(types).containsExactlyInAnyOrder("KSampler", "FaceDetailer", "UltimateSDUpscale");
    }

    @Test
    @DisplayName("extractNodeTypes: should extract node types from API prompt format")
    void shouldExtractTypesFromApiWorkflow() {
        String apiWorkflow = """
                {
                  "3": {
                    "class_type": "KSampler",
                    "inputs": {}
                  },
                  "4": {
                    "class_type": "ReActorFaceSwap",
                    "inputs": {}
                  }
                }
                """;

        Set<String> types = resolverService.extractNodeTypes(apiWorkflow);

        assertThat(types).containsExactlyInAnyOrder("KSampler", "ReActorFaceSwap");
    }

    @Test
    @DisplayName("analyzeWorkflowNodes: should classify known community packages when nodes are missing")
    void shouldClassifyKnownCommunityPackages() {
        String workflow = """
                {
                  "nodes": [
                    { "type": "FaceDetailer" },
                    { "type": "ReActorFaceSwap" },
                    { "type": "KSampler" }
                  ]
                }
                """;

        var report = resolverService.analyzeWorkflowNodes(workflow).block();

        assertThat(report).isNotNull();
        assertThat(report.requiredNodeTypes()).contains("FaceDetailer", "ReActorFaceSwap", "KSampler");
        // Core node KSampler should be ignored in missing list
        assertThat(report.missingNodeTypes()).doesNotContain("KSampler");
    }
}
