package de.tki.comfyuicompanion.domain;

import java.util.Collections;
import java.util.List;

/**
 * Immutable record representing a model repository listing from Hugging Face Hub.
 *
 * @param repoId       unique repository identifier (e.g. "city96/FLUX.1-dev-gguf")
 * @param author       author or organization username (e.g. "city96")
 * @param modelName    base repository name without author prefix
 * @param downloads    total download count
 * @param likes        number of repository likes / stars
 * @param lastModified ISO-8601 or formatted last update timestamp
 * @param pipelineTag  primary task or pipeline tag (e.g. "text-to-image", "image-to-video")
 * @param tags         tags associated with the repository
 * @param gated        gating or access agreement requirements (e.g. "auto", "manual", "false")
 * @param files        list of downloadable model file entries
 * @param thumbnailUrl optional remote preview image URL
 */
public record HuggingFaceModelEntry(
        String repoId,
        String author,
        String modelName,
        int downloads,
        int likes,
        String lastModified,
        String pipelineTag,
        List<String> tags,
        String gated,
        List<HuggingFaceFileEntry> files,
        String thumbnailUrl
) {
    /**
     * Backward-compatible 10-parameter constructor defaulting {@code thumbnailUrl} to null.
     */
    public HuggingFaceModelEntry(
            String repoId,
            String author,
            String modelName,
            int downloads,
            int likes,
            String lastModified,
            String pipelineTag,
            List<String> tags,
            String gated,
            List<HuggingFaceFileEntry> files) {
        this(repoId, author, modelName, downloads, likes, lastModified, pipelineTag, tags, gated, files, null);
    }

    /**
     * Compact constructor normalizing collections and default names.
     */
    public HuggingFaceModelEntry {
        if (tags == null) {
            tags = Collections.emptyList();
        }
        if (files == null) {
            files = Collections.emptyList();
        }
        if ((author == null || author.isBlank()) && repoId != null && repoId.contains("/")) {
            author = repoId.substring(0, repoId.indexOf('/'));
        }
        if ((modelName == null || modelName.isBlank()) && repoId != null) {
            modelName = repoId.contains("/") ? repoId.substring(repoId.indexOf('/') + 1) : repoId;
        }
    }

    /**
     * Checks if this repository is gated (requires agreement or Hugging Face token approval).
     *
     * @return true if gated, false otherwise
     */
    public boolean isGated() {
        return gated != null && !gated.isBlank() && !gated.equalsIgnoreCase("false");
    }
}
