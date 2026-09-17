package de.tki.comfyuicompanion.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests verifying directory management and clone workflows in {@link GitRepositoryManager}.
 */
public class GitRepositoryManagerTest {

    @Test
    void testDeleteDirectoryRecursively(@TempDir Path tempDir) throws IOException {
        Path subDir = tempDir.resolve("nested").resolve("sub");
        Files.createDirectories(subDir);
        Path file1 = subDir.resolve("test.txt");
        Files.writeString(file1, "Hello World");

        GitRepositoryManager manager = new GitRepositoryManager(null);
        assertTrue(Files.exists(file1));

        manager.deleteDirectoryRecursively(tempDir.resolve("nested"));
        assertFalse(Files.exists(subDir));
        assertFalse(Files.exists(file1));
    }

    @Test
    void testCloneOrUpdate_cleansCorruptedDirectory(@TempDir Path tempDir) throws IOException {
        Path corruptTarget = tempDir.resolve("corrupt_repo");
        Files.createDirectories(corruptTarget);
        Files.writeString(corruptTarget.resolve("dummy.txt"), "Not a git repo");

        GitRepositoryManager manager = new GitRepositoryManager(null);
        AtomicBoolean logged = new AtomicBoolean(false);

        // Clones an invalid URI to verify cleanup happened and error was gracefully captured
        boolean result = manager.cloneOrUpdateRepository("https://invalid.example.com/repo.git", corruptTarget, "main", msg -> {
            if (msg.contains("Cleaning and recloning")) {
                logged.set(true);
            }
        });

        assertFalse(result);
        assertTrue(logged.get());
    }
}
