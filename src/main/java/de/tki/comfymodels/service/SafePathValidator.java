package de.tki.comfymodels.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;

/**
 * Service to guard file operations against Path Traversal and illegal directory escapes.
 */
@Service
public class SafePathValidator {

    private static final Logger logger = LoggerFactory.getLogger(SafePathValidator.class);

    /**
     * Ensures that a resolved target path does not escape the designated base directory.
     *
     * @param baseDir    The trusted root/base directory
     * @param targetPath The resolved path to check
     * @return The normalized, safe path
     * @throws SecurityException If the target path escapes baseDir
     */
    public Path validateWithinBase(Path baseDir, Path targetPath) {
        Objects.requireNonNull(baseDir, "Base directory must not be null");
        Objects.requireNonNull(targetPath, "Target path must not be null");

        Path normalizedBase = baseDir.toAbsolutePath().normalize();
        Path normalizedTarget = targetPath.toAbsolutePath().normalize();

        if (!normalizedTarget.startsWith(normalizedBase)) {
            String errorMsg = String.format("Path traversal attempt detected! Target '%s' is outside base directory '%s'",
                    normalizedTarget, normalizedBase);
            logger.warn("SECURITY ALERT: {}", errorMsg);
            throw new SecurityException(errorMsg);
        }

        return normalizedTarget;
    }

    /**
     * Checks whether a target path is allowed within any of the provided base directories.
     */
    public boolean isWithinAnyBase(Collection<Path> allowedBases, Path targetPath) {
        if (targetPath == null || allowedBases == null || allowedBases.isEmpty()) {
            return false;
        }

        Path normalizedTarget = targetPath.toAbsolutePath().normalize();
        for (Path base : allowedBases) {
            if (base != null) {
                Path normalizedBase = base.toAbsolutePath().normalize();
                if (normalizedTarget.startsWith(normalizedBase)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Sanitizes a relative sub-path or filename by removing directory traversal tokens and null bytes.
     */
    public String sanitizeFilename(String filename) {
        if (filename == null) {
            return "";
        }
        // Disallow null bytes and carriage returns
        String cleaned = filename.replace("\0", "").replace("\r", "").replace("\n", "");
        // Normalize slashes
        cleaned = cleaned.replace('\\', '/');
        // Prevent path traversal sequences
        while (cleaned.contains("../")) {
            cleaned = cleaned.replace("../", "");
        }
        if (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        return cleaned.trim();
    }

    /**
     * Strictly validates that a filename contains no Path Traversal or directory separator characters.
     *
     * @param filename Filename to check
     * @return Validated filename
     * @throws SecurityException if traversal characters are detected
     * @throws IllegalArgumentException if filename is empty
     */
    public String validateFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Dateiname darf nicht leer sein.");
        }
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\") || filename.contains("\0")) {
            String errorMsg = "Path traversal attempt detected in filename: " + filename;
            logger.warn("SECURITY ALERT: {}", errorMsg);
            throw new SecurityException(errorMsg);
        }
        return filename;
    }
}
