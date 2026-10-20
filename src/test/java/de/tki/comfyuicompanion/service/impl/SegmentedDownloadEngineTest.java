package de.tki.comfyuicompanion.service.impl;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Enterprise tests for SegmentedDownloadEngine verifying bounded streaming,
 * multi-segment stitching, fallback to single stream, and status reporting.
 */
public class SegmentedDownloadEngineTest {

    private WireMockServer wireMockServer;
    private SegmentedDownloadEngine engine;

    @BeforeEach
    public void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
        engine = new SegmentedDownloadEngine(null);
    }

    @AfterEach
    public void tearDown() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testStitchSegments(@TempDir Path tempDir) throws IOException {
        Path targetFile = tempDir.resolve("final_model.safetensors");
        Path part0 = tempDir.resolve("final_model.safetensors.part0");
        Path part1 = tempDir.resolve("final_model.safetensors.part1");

        Files.write(part0, new byte[]{1, 2, 3, 4});
        Files.write(part1, new byte[]{5, 6, 7, 8});

        engine.stitchSegments(new Path[]{part0, part1}, targetFile);

        assertTrue(Files.exists(targetFile));
        assertEquals(8, Files.size(targetFile));
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8}, Files.readAllBytes(targetFile));
        assertFalse(Files.exists(part0));
        assertFalse(Files.exists(part1));
    }

    @Test
    public void testFormatSize() {
        assertEquals("500 KB", SegmentedDownloadEngine.formatSize(500 * 1024));
        assertEquals("50.0 MB", SegmentedDownloadEngine.formatSize(50 * 1024 * 1024));
        assertEquals("1.99 GB", SegmentedDownloadEngine.formatSize(2133204097L));
    }

    @Test
    public void testDownloadSingleSegment(@TempDir Path tempDir) throws Exception {
        byte[] payload = new byte[]{10, 20, 30, 40, 50};
        wireMockServer.stubFor(get(urlEqualTo("/model.bin"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Length", String.valueOf(payload.length))
                        .withBody(payload)));

        Path target = tempDir.resolve("model.bin");
        List<String> statuses = new ArrayList<>();
        SegmentedDownloadEngine.DownloadControl ctrl = new SegmentedDownloadEngine.DownloadControl() {
            public boolean isStopped() { return false; }
            public boolean isPaused() { return false; }
            public boolean isSelected() { return true; }
        };

        boolean result = engine.downloadSingleSegment(
                "model.bin",
                wireMockServer.baseUrl() + "/model.bin",
                target,
                payload.length,
                ctrl,
                statuses::add
        );

        assertTrue(result);
        assertTrue(Files.exists(target));
        assertEquals(payload.length, Files.size(target));
        assertArrayEquals(payload, Files.readAllBytes(target));
    }

    @Test
    public void testDownloadMultiSegmentSuccess(@TempDir Path tempDir) throws Exception {
        byte[] part0Data = new byte[]{1, 2, 3, 4};
        byte[] part1Data = new byte[]{5, 6, 7, 8};
        long totalSize = 8;

        wireMockServer.stubFor(get(urlEqualTo("/segmented.bin"))
                .withHeader("Range", equalTo("bytes=0-3"))
                .willReturn(aResponse()
                        .withStatus(206)
                        .withHeader("Content-Range", "bytes 0-3/8")
                        .withHeader("Content-Length", "4")
                        .withBody(part0Data)));

        wireMockServer.stubFor(get(urlEqualTo("/segmented.bin"))
                .withHeader("Range", equalTo("bytes=4-7"))
                .willReturn(aResponse()
                        .withStatus(206)
                        .withHeader("Content-Range", "bytes 4-7/8")
                        .withHeader("Content-Length", "4")
                        .withBody(part1Data)));

        Path target = tempDir.resolve("segmented.bin");
        List<String> statuses = new ArrayList<>();
        SegmentedDownloadEngine.DownloadControl ctrl = new SegmentedDownloadEngine.DownloadControl() {
            public boolean isStopped() { return false; }
            public boolean isPaused() { return false; }
            public boolean isSelected() { return true; }
        };

        boolean result = engine.downloadMultiSegment(
                wireMockServer.baseUrl() + "/segmented.bin",
                target,
                totalSize,
                2,
                ctrl,
                statuses::add
        );

        assertTrue(result);
        assertTrue(Files.exists(target));
        assertEquals(8, Files.size(target));
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8}, Files.readAllBytes(target));
    }

    @Test
    public void testDownloadFallbackToSingleStreamWhenMultiSegmentFails(@TempDir Path tempDir) throws Exception {
        byte[] payload = new byte[1024];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte) (i % 128);

        // When requesting with range, server returns 500 error or ignores range, but plain GET succeeds
        wireMockServer.stubFor(get(urlEqualTo("/large_model.bin"))
                .withHeader("Range", matching("bytes=.*"))
                .willReturn(aResponse().withStatus(500)));

        wireMockServer.stubFor(get(urlEqualTo("/large_model.bin"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Length", String.valueOf(payload.length))
                        .withBody(payload)));

        Path target = tempDir.resolve("large_model.bin");
        List<String> statuses = new ArrayList<>();
        SegmentedDownloadEngine.DownloadControl ctrl = new SegmentedDownloadEngine.DownloadControl() {
            public boolean isStopped() { return false; }
            public boolean isPaused() { return false; }
            public boolean isSelected() { return true; }
        };

        // Trigger download with totalRemoteSize > 100MB threshold and numSegments > 1
        boolean result = engine.download(
                "large_model.bin",
                wireMockServer.baseUrl() + "/large_model.bin",
                target,
                150 * 1024 * 1024L,
                4,
                ctrl,
                statuses::add
        );

        assertTrue(result);
        assertTrue(Files.exists(target));
        assertTrue(statuses.stream().anyMatch(s -> s.contains("single stream") || s.contains("Downloading")));
    }
}
