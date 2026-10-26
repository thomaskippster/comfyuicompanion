package de.tki.comfyuicompanion.domain;

/**
 * Immutable record representing a downloadable model file in a Hugging Face repository.
 *
 * @param rfilename       relative file path in the repository (e.g. "flux1-dev-Q4_K_M.gguf")
 * @param filename        base filename (e.g. "flux1-dev-Q4_K_M.gguf")
 * @param sizeBytes       exact file size in bytes
 * @param formattedSize   human-readable size string (e.g. "6.79 GB")
 * @param downloadUrl     direct download URL (https://huggingface.co/{repo_id}/resolve/main/{rfilename}?download=true)
 * @param suggestedFolder ComfyUI target directory name (e.g. "unet", "clip", "vae", "checkpoints")
 * @param vramHint        suggested hardware / VRAM requirement badge (e.g. "~8 GB VRAM", "~16 GB VRAM")
 * @param isLfs           whether the file is tracked via Git LFS
 */
public record HuggingFaceFileEntry(
        String rfilename,
        String filename,
        long sizeBytes,
        String formattedSize,
        String downloadUrl,
        String suggestedFolder,
        String vramHint,
        boolean isLfs
) {
    /**
     * Compact constructor ensuring non-null values for collections and labels.
     */
    public HuggingFaceFileEntry {
        if (filename == null && rfilename != null) {
            filename = rfilename.contains("/") ? rfilename.substring(rfilename.lastIndexOf('/') + 1) : rfilename;
        }
        if (formattedSize == null) {
            formattedSize = formatBytes(sizeBytes);
        }
        if (suggestedFolder == null) {
            suggestedFolder = "checkpoints";
        }
        if (vramHint == null) {
            vramHint = "Standard";
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0) return "Unknown";
        if (bytes >= 1024L * 1024L * 1024L) {
            return String.format(java.util.Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        } else if (bytes >= 1024L * 1024L) {
            return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return bytes + " B";
        }
    }
}
