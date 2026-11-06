package de.tki.comfyuicompanion.service.provisioning;

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

    @Test
    @DisplayName("findKnownPackage: should resolve lip-sync community packages (LivePortrait, SadTalker, Wav2Lip)")
    void shouldFindLipSyncPackages() {
        var livePortrait = resolverService.findKnownPackage("LivePortraitCropper");
        assertThat(livePortrait).isPresent();
        assertThat(livePortrait.get().packageName()).isEqualTo("LivePortrait");
        assertThat(livePortrait.get().gitUrl()).isEqualTo("https://github.com/kijai/ComfyUI-LivePortrait");

        var sadTalker = resolverService.findKnownPackage("SadTalker");
        assertThat(sadTalker).isPresent();
        assertThat(sadTalker.get().packageName()).isEqualTo("SadTalker");

        var wav2Lip = resolverService.findKnownPackage("Wav2Lip");
        assertThat(wav2Lip).isPresent();
        assertThat(wav2Lip.get().packageName()).isEqualTo("Wav2Lip");

        var wan = resolverService.findKnownPackage("WanVideoSampler");
        assertThat(wan).isPresent();
        assertThat(wan.get().packageName()).isEqualTo("ComfyUI-WanVideoWrapper");

        var ue = resolverService.findKnownPackage("Anything Everywhere");
        assertThat(ue).isPresent();
        assertThat(ue.get().packageName()).isEqualTo("cg-use-everywhere");
    }

    @Test
    @DisplayName("analyzeWorkflowNodes: should detect missing custom nodes when ComfyUI is offline")
    void shouldDetectMissingNodesWhenOffline() {
        String workflow = """
                {
                  "nodes": [
                    { "type": "WanVideoSampler" },
                    { "type": "Anything Everywhere" },
                    { "type": "KSampler" }
                  ]
                }
                """;

        var report = resolverService.analyzeWorkflowNodes(workflow).block();

        assertThat(report).isNotNull();
        assertThat(report.missingNodeTypes()).contains("WanVideoSampler", "Anything Everywhere");
        assertThat(report.missingNodeTypes()).doesNotContain("KSampler");
        assertThat(report.recommendedPackages())
                .extracting(CustomNodeResolverService.CustomNodePackage::packageName)
                .contains("ComfyUI-WanVideoWrapper", "cg-use-everywhere");
    }

    @Test
    @DisplayName("analyzeWorkflowNodes: should recognize all core nodes and subgraphs without false missing reports")
    void shouldRecognizeAllCoreNodesAndSubgraphsInZImageTurbo() {
        String workflow = """
                {
                  "nodes": [
                    { "id": 35, "type": "MarkdownNote" },
                    { "id": 9, "type": "SaveImage" },
                    { "id": 57, "type": "f2fdebf6-dfaf-43b6-9eb2-7f70613cfdc1" }
                  ],
                  "definitions": {
                    "subgraphs": [
                      {
                        "id": "f2fdebf6-dfaf-43b6-9eb2-7f70613cfdc1",
                        "nodes": [
                          { "id": 30, "type": "CLIPLoader" },
                          { "id": 29, "type": "VAELoader" },
                          { "id": 33, "type": "ConditioningZeroOut" },
                          { "id": 8, "type": "VAEDecode" },
                          { "id": 28, "type": "UNETLoader" },
                          { "id": 27, "type": "CLIPTextEncode" },
                          { "id": 13, "type": "EmptySD3LatentImage" },
                          { "id": 11, "type": "ModelSamplingAuraFlow" },
                          { "id": 3, "type": "KSampler" }
                        ]
                      }
                    ]
                  }
                }
                """;

        var report = resolverService.analyzeWorkflowNodes(workflow).block();

        assertThat(report).isNotNull();
        assertThat(report.missingNodeTypes()).isEmpty();
        assertThat(report.recommendedPackages()).isEmpty();
    }

    @Test
    @DisplayName("analyzeWorkflowNodes: should resolve packages for Seed (rgthree) and Empty Latent Ratio Select SDXL")
    void shouldResolveMissingNodesForSd35EinfachWorkflow() {
        String workflow = """
                {
                  "nodes": [
                    { "id": 57, "type": "Seed (rgthree)" },
                    { "id": 56, "type": "Empty Latent Ratio Select SDXL" },
                    { "id": 4, "type": "CheckpointLoaderSimple" },
                    { "id": 3, "type": "KSampler" }
                  ]
                }
                """;

        var report = resolverService.analyzeWorkflowNodes(workflow).block();

        assertThat(report).isNotNull();
        assertThat(report.missingNodeTypes()).containsExactlyInAnyOrder("Seed (rgthree)", "Empty Latent Ratio Select SDXL");
        assertThat(report.recommendedPackages())
                .extracting(CustomNodeResolverService.CustomNodePackage::packageName)
                .contains("rgthree-comfy", "mikey_nodes");
        assertThat(report.recommendedPackages())
                .allMatch(pkg -> pkg.gitUrl() != null && pkg.gitUrl().startsWith("https://github.com/"));
    }
}
