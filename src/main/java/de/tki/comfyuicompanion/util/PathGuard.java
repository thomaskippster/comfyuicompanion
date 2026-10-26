package de.tki.comfyuicompanion.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Enterprise utility providing path sanitization, directory containment validation,
 * and proactive path traversal defense across all filesystem interactions.
 */
public final class PathGuard {

    private static final Logger logger = LoggerFactory.getLogger(PathGuard.class);

    private PathGuard() {
        // Utility class
    }

    /**
     * Sanitizes an untrusted filename or relative path segment by removing directory delimiters,
     * null bytes, and parent path navigation sequences.
     *
     * @param rawFileName untrusted filename from remote payloads or user input
     * @return sanitized base filename
     * @throws SecurityException if the filename is invalid or cannot be sanitized
     */
    public static String sanitizeFileName(String rawFileName) {
        if (rawFileName == null || rawFileName.isBlank()) {
            throw new SecurityException("Filename must not be null or blank.");
        }
        String fileName = new File(rawFileName).getName().replace("\0", "").trim();
        if (fileName.isEmpty() || fileName.equals(".") || fileName.equals("..")) {
            throw new SecurityException("Invalid filename after sanitization: " + rawFileName);
        }
        return fileName;
    }

    /**
     * Validates and resolves an untrusted relative path against a designated root directory,
     * verifying that the target does not escape the boundary of the root directory.
     *
     * @param baseDir       trusted base directory
     * @param untrustedPath untrusted relative path or filename
     * @return normalized, safe target Path
     * @throws SecurityException if path traversal outside the base directory is detected
     */
    public static Path resolveWithin(Path baseDir, String untrustedPath) {
        Objects.requireNonNull(baseDir, "Base directory must not be null");
        if (untrustedPath == null || untrustedPath.isBlank()) {
            throw new SecurityException("Untrusted path must not be null or blank");
        }
        return resolveWithin(baseDir, Paths.get(untrustedPath));
    }

    /**
     * Resolves an untrusted sub-path against a designated root directory, ensuring strict containment.
     *
     * @param baseDir       trusted base directory
     * @param untrustedPath candidate target path
     * @return normalized, validated target Path
     * @throws SecurityException if the target path escapes baseDir
     */
    public static Path resolveWithin(Path baseDir, Path untrustedPath) {
        Objects.requireNonNull(baseDir, "Base directory must not be null");
        Objects.requireNonNull(untrustedPath, "Untrusted path must not be null");

        Path normalizedBase = baseDir.toAbsolutePath().normalize();
        Path candidate = untrustedPath.isAbsolute()
                ? untrustedPath.normalize()
                : normalizedBase.resolve(untrustedPath).normalize();

        if (!candidate.startsWith(normalizedBase)) {
            String msg = "SECURITY ALERT: Path traversal attempt detected! Candidate [" + candidate
                    + "] escapes root [" + normalizedBase + "]";
            logger.warn(msg);
            throw new SecurityException(msg);
        }
        return candidate;
    }

    /**
     * Validates that an existing path resides within the expected base directory.
     *
     * @param baseDir   trusted base directory
     * @param candidate candidate target path
     * @return normalized candidate Path
     * @throws SecurityException if candidate escapes baseDir
     */
    public static Path requireWithin(Path baseDir, Path candidate) {
        return resolveWithin(baseDir, candidate);
    }

    /**
     * Non-throwing containment check.
     *
     * @param baseDir   trusted base directory
     * @param candidate candidate target path
     * @return true if candidate is strictly within baseDir, false otherwise
     */
    public static boolean isWithin(Path baseDir, Path candidate) {
        if (baseDir == null || candidate == null) {
            return false;
        }
        try {
            Path normBase = baseDir.toAbsolutePath().normalize();
            Path normCandidate = candidate.toAbsolutePath().normalize();
            return normCandidate.startsWith(normBase);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Normalizes and validates a path originating from command line arguments.
     *
     * @param cliPath raw path argument from CLI
     * @return normalized absolute Path
     * @throws SecurityException if path is malformed or blank
     */
    public static Path safeCommandLinePath(String cliPath) {
        if (cliPath == null || cliPath.isBlank()) {
            throw new SecurityException("Command line path must not be null or blank.");
        }
        return Paths.get(cliPath.trim().replace("\0", "")).toAbsolutePath().normalize();
    }
}
