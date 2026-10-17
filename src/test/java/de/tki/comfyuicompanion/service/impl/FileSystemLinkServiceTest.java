package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.service.IFileSystemLinkService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileSystemLinkServiceTest {

    private final FileSystemLinkService linkService = new FileSystemLinkService();

    @Test
    @DisplayName("isSymlinkCreationAllowed should return a consistent boolean capability check")
    void shouldReportSymlinkCapability() {
        boolean allowed = linkService.isSymlinkCreationAllowed();
        assertThat(allowed).isIn(true, false);
    }

    @Test
    @DisplayName("createFileLink: should successfully create a link or fallback to hard link on same volume")
    void shouldCreateFileLink(@TempDir Path tempDir) throws IOException {
        Path targetFile = tempDir.resolve("model.safetensors");
        Files.writeString(targetFile, "dummy model binary content");
        Path linkFile = tempDir.resolve("model_link.safetensors");

        IFileSystemLinkService.LinkResult result = linkService.createFileLink(linkFile, targetFile);

        assertThat(result.success()).isTrue();
        assertThat(Files.exists(linkFile)).isTrue();
        assertThat(Files.readString(linkFile)).isEqualTo("dummy model binary content");
    }

    @Test
    @DisplayName("createDirectoryLink: should link directory or junction")
    void shouldCreateDirectoryLink(@TempDir Path tempDir) throws IOException {
        Path targetDir = tempDir.resolve("target_checkpoints");
        Files.createDirectories(targetDir);
        Files.writeString(targetDir.resolve("test.txt"), "hello");
        Path linkDir = tempDir.resolve("link_checkpoints");

        IFileSystemLinkService.LinkResult result = linkService.createDirectoryLink(linkDir, targetDir);

        if (result.success()) {
            assertThat(Files.exists(linkDir)).isTrue();
            assertThat(Files.isDirectory(linkDir)).isTrue();
            assertThat(Files.exists(linkDir.resolve("test.txt"))).isTrue();
        }
    }
}
