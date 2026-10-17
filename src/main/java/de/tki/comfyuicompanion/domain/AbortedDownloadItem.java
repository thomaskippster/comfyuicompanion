package de.tki.comfyuicompanion.domain;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Domain record representing an interrupted, incomplete, or orphaned model download artifact
 * (such as .cmfd, .cmfd_merge, or .part files) stored in the local model directory hierarchy.
 *
 * @param filePath          the absolute path to the partial or temporary file on disk
 * @param targetFileName    the expected final model file name (e.g., "dreamshaper_v8.safetensors")
 * @param folderName        the relative model folder (e.g., "checkpoints", "loras", "vae")
 * @param sizeBytes         the current physical byte size of the partial file
 * @param lastModified      the timestamp when the partial file was last updated
 * @param fileExtensionType the temporary file extension type (e.g., ".cmfd", ".part0", ".tmp")
 * @param downloadUrl       the remote download URL if known or resolved, nullable
 */
public record AbortedDownloadItem(
        Path filePath,
        String targetFileName,
        String folderName,
        long sizeBytes,
        Instant lastModified,
        String fileExtensionType,
        String downloadUrl
) {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    /**
     * Returns a human-readable representation of the partial file size (e.g., "1.42 GB" or "520 MB").
     *
     * @return formatted size string
     */
    public String getFormattedSize() {
        if (sizeBytes <= 0) return "0 B";
        if (sizeBytes < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", sizeBytes / 1024.0);
        } else if (sizeBytes < 1024 * 1024 * 1024) {
            return String.format(Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0));
        } else {
            return String.format(Locale.US, "%.2f GB", sizeBytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * Returns a formatted date string for the last modified timestamp.
     *
     * @return formatted date string
     */
    public String getFormattedDate() {
        if (lastModified == null) return "Unknown";
        return DATE_FORMATTER.format(lastModified);
    }
}
