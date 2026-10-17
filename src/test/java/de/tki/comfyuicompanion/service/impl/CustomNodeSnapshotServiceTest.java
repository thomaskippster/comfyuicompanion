package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.service.ICustomNodeSnapshotService.EnvironmentSnapshot;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests verifying environment snapshot capture, listing, restoration, and deletion.
 */
class CustomNodeSnapshotServiceTest {

    @TempDir
    Path tempDir;

    private Path comfyDir;
    private Path snapshotsDir;
    private ConfigService configService;
    private CustomNodeSnapshotService snapshotService;

    @BeforeEach
    void setUp() throws Exception {
        comfyDir = tempDir.resolve("ComfyUI");
        snapshotsDir = tempDir.resolve("snapshots");
        Files.createDirectories(comfyDir.resolve("custom_nodes"));
        Files.createDirectories(snapshotsDir);

        configService = Mockito.mock(ConfigService.class);
        when(configService.getComfyUIPath()).thenReturn(comfyDir.toString());
        when(configService.getPythonPath()).thenReturn("");

        snapshotService = new CustomNodeSnapshotService(configService, null, snapshotsDir);
    }

    @Test
    void testCaptureAndListSnapshot() throws Exception {
        // Create dummy git repo inside custom_nodes
        Path sampleNodeDir = comfyDir.resolve("custom_nodes").resolve("ComfyUI-Sample-Node");
        Files.createDirectories(sampleNodeDir);
        try (Git git = Git.init().setDirectory(sampleNodeDir.toFile()).call()) {
            File dummyFile = new File(sampleNodeDir.toFile(), "node.py");
            Files.writeString(dummyFile.toPath(), "print('hello node')");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("Initial commit").call();
        }

        List<String> logs = new ArrayList<>();
        EnvironmentSnapshot snapshot = snapshotService.captureSnapshot("Test Snapshot", logs::add);

        assertNotNull(snapshot);
        assertTrue(snapshot.id().startsWith("snapshot_"));
        assertEquals("Test Snapshot", snapshot.description());
        assertEquals(1, snapshot.nodes().size());
        assertEquals("ComfyUI-Sample-Node", snapshot.nodes().get(0).folderName());

        List<EnvironmentSnapshot> list = snapshotService.listSnapshots();
        assertEquals(1, list.size());
        assertEquals(snapshot.id(), list.get(0).id());
    }

    @Test
    void testRestoreSnapshot() throws Exception {
        Path sampleNodeDir = comfyDir.resolve("custom_nodes").resolve("ComfyUI-Sample-Node");
        Files.createDirectories(sampleNodeDir);
        try (Git git = Git.init().setDirectory(sampleNodeDir.toFile()).call()) {
            File dummyFile = new File(sampleNodeDir.toFile(), "node.py");
            Files.writeString(dummyFile.toPath(), "v1");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("v1").call();

            Files.writeString(dummyFile.toPath(), "v2");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("v2").call();
        }

        EnvironmentSnapshot snap = snapshotService.captureSnapshot("v2 snapshot", null);
        assertNotNull(snap);

        List<String> restoreLogs = new ArrayList<>();
        var result = snapshotService.rollbackToSnapshot(snap.id(), false, restoreLogs::add);
        assertTrue(result.success());
    }

    @Test
    void testDeleteSnapshot() {
        EnvironmentSnapshot snap = snapshotService.captureSnapshot("To Delete", null);
        assertNotNull(snap);
        assertEquals(1, snapshotService.listSnapshots().size());

        boolean deleted = snapshotService.deleteSnapshot(snap.id());
        assertTrue(deleted);
        assertEquals(0, snapshotService.listSnapshots().size());
    }
}
