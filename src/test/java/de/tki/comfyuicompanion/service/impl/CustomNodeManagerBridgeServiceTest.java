package de.tki.comfyuicompanion.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tki.comfyuicompanion.service.SafePathValidator;
import de.tki.comfyuicompanion.service.provisioning.CustomNodeResolverService;
import de.tki.comfyuicompanion.service.provisioning.CustomNodeResolverService.CustomNodePackage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying database loading, node resolution, and workflow analysis
 * for ComfyUI-Manager integrations.
 */
class CustomNodeManagerBridgeServiceTest {

    @TempDir
    Path tempDir;

    private Path cacheDir;
    private CustomNodeDatabaseLoader loader;
    private CustomNodeManagerBridgeService bridgeService;

    @BeforeEach
    void setUp() throws Exception {
        cacheDir = tempDir.resolve("cache");
        Files.createDirectories(cacheDir);

        // Write mock custom-node-list.json
        String customNodeListJson = """
                {
                  "custom_nodes": [
                    {
                      "name": "ComfyUI-Impact-Pack",
                      "reference": "https://github.com/ltdrdata/ComfyUI-Impact-Pack",
                      "title": "ComfyUI Impact Pack",
                      "pip": ["segment-anything"]
                    },
                    {
                      "name": "ComfyUI-Custom-Tools",
                      "reference": "https://github.com/example/ComfyUI-Custom-Tools",
                      "title": "Custom Tools",
                      "pip": []
                    }
                  ]
                }
                """;
        Files.writeString(cacheDir.resolve("custom-node-list.json"), customNodeListJson);

        // Write mock extension-node-map.json
        String extMapJson = """
                {
                  "ComfyUI-Impact-Pack": [
                    ["FaceDetailer", "FaceDetailer (Impact)"],
                    ["BboxDetectorSEGS", "BboxDetectorSEGS (Impact)"]
                  ],
                  "https://github.com/example/ComfyUI-Custom-Tools": [
                    ["MagicFilterNode", "Magic Filter"]
                  ]
                }
                """;
        Files.writeString(cacheDir.resolve("extension-node-map.json"), extMapJson);

        loader = new CustomNodeDatabaseLoader(new ObjectMapper(), cacheDir);
        bridgeService = new CustomNodeManagerBridgeService(null, loader, new CustomNodeInstaller(null, new SafePathValidator(), null));
    }

    @Test
    void testResolveNodePackageFromMockDatabase() {
        bridgeService.ensureDatabaseLoadedAsync().join();

        Optional<CustomNodePackage> impactPkg = bridgeService.resolveNodePackage("FaceDetailer");
        assertTrue(impactPkg.isPresent());
        assertEquals("ComfyUI-Impact-Pack", impactPkg.get().packageName());
        assertEquals("https://github.com/ltdrdata/ComfyUI-Impact-Pack", impactPkg.get().gitUrl());
        assertTrue(impactPkg.get().pipDependencies().contains("segment-anything"));

        Optional<CustomNodePackage> toolPkg = bridgeService.resolveNodePackage("MagicFilterNode");
        assertTrue(toolPkg.isPresent());
        assertEquals("https://github.com/example/ComfyUI-Custom-Tools", toolPkg.get().gitUrl());
    }

    @Test
    void testResolvePackagesForNodesGrouping() {
        bridgeService.ensureDatabaseLoadedAsync().join();

        Map<CustomNodePackage, List<String>> resolved = bridgeService.resolvePackagesForNodes(
                List.of("FaceDetailer", "BboxDetectorSEGS", "MagicFilterNode", "UnknownNonExistentNode")
        );

        assertEquals(2, resolved.size());
        boolean hasImpact = false;
        for (Map.Entry<CustomNodePackage, List<String>> entry : resolved.entrySet()) {
            if ("ComfyUI-Impact-Pack".equals(entry.getKey().packageName())) {
                hasImpact = true;
                assertEquals(2, entry.getValue().size());
                assertTrue(entry.getValue().contains("FaceDetailer"));
                assertTrue(entry.getValue().contains("BboxDetectorSEGS"));
            }
        }
        assertTrue(hasImpact);
    }

    @Test
    void testFallbackNodesWhenDatabaseEmpty() throws Exception {
        Path emptyCache = tempDir.resolve("empty_cache");
        Files.createDirectories(emptyCache);
        Files.writeString(emptyCache.resolve("custom-node-list.json"), "{\"custom_nodes\": []}");
        Files.writeString(emptyCache.resolve("extension-node-map.json"), "{}");

        CustomNodeDatabaseLoader emptyLoader = new CustomNodeDatabaseLoader(new ObjectMapper(), emptyCache);
        CustomNodeManagerBridgeService emptyBridge = new CustomNodeManagerBridgeService(null, emptyLoader, null);
        emptyBridge.ensureDatabaseLoadedAsync().join();

        // Should resolve built-in fallbacks like ReActorFaceSwap
        Optional<CustomNodePackage> reactor = emptyBridge.resolveNodePackage("ReActorFaceSwap");
        assertTrue(reactor.isPresent());
        assertEquals("https://github.com/Gourieff/comfyui-reactor-node", reactor.get().gitUrl());
    }

    @Test
    void testCustomNodeResolverServiceExtractNodeTypes() {
        CustomNodeResolverService resolver = new CustomNodeResolverService(
                new ObjectMapper(), WebClient.builder(), null, new SafePathValidator(), bridgeService
        );

        String sampleWorkflow = """
                {
                  "nodes": [
                    { "type": "KSampler" },
                    { "type": "FaceDetailer" },
                    { "type": "CheckpointLoaderSimple" }
                  ]
                }
                """;

        Set<String> extracted = resolver.extractNodeTypes(sampleWorkflow);
        assertEquals(3, extracted.size());
        assertTrue(extracted.contains("KSampler"));
        assertTrue(extracted.contains("FaceDetailer"));
        assertTrue(extracted.contains("CheckpointLoaderSimple"));
    }
}
