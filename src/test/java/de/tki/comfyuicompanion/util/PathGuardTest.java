package de.tki.comfyuicompanion.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests validating path traversal protection and file name sanitization in {@link PathGuard}.
 */
public class PathGuardTest {

    @TempDir
    Path tempDir;

    @Test
    void testResolveWithinValidPath() {
        Path resolved = PathGuard.resolveWithin(tempDir, "subfolder/model.safetensors");
        assertNotNull(resolved);
        assertTrue(resolved.startsWith(tempDir.toAbsolutePath().normalize()));
        assertEquals(tempDir.resolve("subfolder/model.safetensors").toAbsolutePath().normalize(), resolved);
    }

    @Test
    void testResolveWithinTraversalThrowsSecurityException() {
        assertThrows(SecurityException.class, () ->
                PathGuard.resolveWithin(tempDir, "../../etc/passwd")
        );

        assertThrows(SecurityException.class, () ->
                PathGuard.resolveWithin(tempDir, "..\\..\\windows\\system32\\cmd.exe")
        );
    }

    @Test
    void testSanitizeFileName() {
        assertEquals("test_model.safetensors", PathGuard.sanitizeFileName("../../test_model.safetensors"));
        assertEquals("valid.ckpt", PathGuard.sanitizeFileName("C:\\models\\valid.ckpt"));
        assertThrows(SecurityException.class, () -> PathGuard.sanitizeFileName("   "));
        assertThrows(SecurityException.class, () -> PathGuard.sanitizeFileName(null));
    }

    @Test
    void testIsWithin() {
        Path inside = tempDir.resolve("child");
        Path outside = tempDir.getParent();

        assertTrue(PathGuard.isWithin(tempDir, inside));
        assertFalse(PathGuard.isWithin(tempDir, outside));
        assertFalse(PathGuard.isWithin(null, inside));
    }

    @Test
    void testSafeCommandLinePath() {
        Path safe = PathGuard.safeCommandLinePath("output");
        assertNotNull(safe);
        assertTrue(safe.isAbsolute());
        assertThrows(SecurityException.class, () -> PathGuard.safeCommandLinePath("  "));
    }
}
