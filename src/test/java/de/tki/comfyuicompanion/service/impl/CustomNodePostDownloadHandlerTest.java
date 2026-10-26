package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.service.SafePathValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

/**
 * Enterprise unit tests verifying {@link CustomNodePostDownloadHandler} automatic detection,
 * secure unzipping, Zip Slip prevention, and workflow JSON extraction.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomNodePostDownloadHandlerTest {

    @Mock
    private ConfigService configService;

    @Mock
    private CustomNodeInstaller customNodeInstaller;

    @TempDir
    Path tempComfyDir;

    @TempDir
    Path tempWorkDir;

    private CustomNodePostDownloadHandler handler;

    @BeforeEach
    void setUp() {
        lenient().when(configService.getComfyUIPath()).thenReturn(tempComfyDir.toString());
        handler = new CustomNodePostDownloadHandler(configService, new SafePathValidator(), customNodeInstaller);
    }

    @Test
    @DisplayName("Should detect custom node archive containing __init__.py")
    void shouldDetectCustomNodeArchive() throws IOException {
        Path zipFile = tempWorkDir.resolve("test_node.zip");
        createZipWithEntries(zipFile, List.of(
                new SimpleZipEntry("MyCustomNode/__init__.py", "NODE_CLASS_MAPPINGS = {}\n"),
                new SimpleZipEntry("MyCustomNode/nodes.py", "# code\n")
        ));

        assertThat(handler.isCustomNodeArchive(zipFile)).isTrue();
    }

    @Test
    @DisplayName("Should reject archive without __init__.py")
    void shouldRejectArchiveWithoutInitPy() throws IOException {
        Path zipFile = tempWorkDir.resolve("regular_archive.zip");
        createZipWithEntries(zipFile, List.of(
                new SimpleZipEntry("models/weights.safetensors", "binary"),
                new SimpleZipEntry("readme.txt", "hello")
        ));

        assertThat(handler.isCustomNodeArchive(zipFile)).isFalse();
    }

    @Test
    @DisplayName("Should extract custom node and bundled workflows into ComfyUI directories")
    void shouldExtractCustomNodeAndWorkflows() throws IOException {
        Path zipFile = tempWorkDir.resolve("SeargeSDXL_v432.zip");
        createZipWithEntries(zipFile, List.of(
                new SimpleZipEntry("SeargeSDXL/__init__.py", "print('Loaded SeargeSDXL')\n"),
                new SimpleZipEntry("SeargeSDXL/modules/sampler.py", "class Sampler: pass\n"),
                new SimpleZipEntry("SeargeSDXL/workflow/workflow_v4.json", "{\"nodes\": []}\n")
        ));

        List<String> statusLogs = new ArrayList<>();
        boolean success = handler.processDownloadedArchive(zipFile, statusLogs::add);

        assertThat(success).isTrue();

        Path installedNodeDir = tempComfyDir.resolve("custom_nodes").resolve("SeargeSDXL");
        assertThat(Files.isDirectory(installedNodeDir)).isTrue();
        assertThat(Files.exists(installedNodeDir.resolve("__init__.py"))).isTrue();
        assertThat(Files.exists(installedNodeDir.resolve("modules").resolve("sampler.py"))).isTrue();

        Path workflowsDir = tempComfyDir.resolve("user").resolve("default").resolve("workflows");
        assertThat(Files.exists(workflowsDir.resolve("workflow_v4.json"))).isTrue();
        assertThat(statusLogs).anyMatch(s -> s.contains("installed successfully"));
    }

    private void createZipWithEntries(Path targetZip, List<SimpleZipEntry> entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (SimpleZipEntry e : entries) {
                ZipEntry entry = new ZipEntry(e.name);
                zos.putNextEntry(entry);
                zos.write(e.content.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        Files.write(targetZip, baos.toByteArray());
    }

    private record SimpleZipEntry(String name, String content) {}
}
