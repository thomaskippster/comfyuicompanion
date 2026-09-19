package de.tki.comfyuicompanion.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link ComfyBridgeManager} resource extraction capabilities.
 */
public class ComfyBridgeManagerTest {

    @Test
    @DisplayName("Should extract README.md containing GitHub and Pinokio links")
    void testExtractBridgeReadme(@TempDir Path tempDir) throws IOException {
        File targetFile = tempDir.resolve("README.md").toFile();

        ComfyBridgeManager.extractResource("/comfyui-bridge/README.md", targetFile);

        assertTrue(targetFile.exists(), "Extracted README.md must exist on filesystem");
        String content = Files.readString(targetFile.toPath());
        assertFalse(content.isBlank(), "Extracted README.md content must not be blank");
        assertTrue(content.contains("Pinokio"), "Extracted README.md must mention Pinokio");
        assertTrue(content.contains("JAR"), "Extracted README.md must mention pre-compiled JAR");
        assertTrue(content.contains("github.com/thomaskippster/comfyuicompanion"), "Extracted README.md must contain repository link");
    }

    @Test
    @DisplayName("Should extract __init__.py containing distribution notice")
    void testExtractBridgeInitPy(@TempDir Path tempDir) throws IOException {
        File targetFile = tempDir.resolve("__init__.py").toFile();

        ComfyBridgeManager.extractResource("/comfyui-bridge/__init__.py", targetFile);

        assertTrue(targetFile.exists(), "Extracted __init__.py must exist");
        String content = Files.readString(targetFile.toPath());
        assertTrue(content.contains("Pinokio"), "Extracted __init__.py must contain Pinokio reference");
    }
}