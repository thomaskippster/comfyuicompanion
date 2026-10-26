package de.tki.comfyuicompanion.service;

import de.tki.comfyuicompanion.domain.HuggingFaceFileEntry;
import de.tki.comfyuicompanion.domain.HuggingFaceModelEntry;
import de.tki.comfyuicompanion.domain.ModelInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for querying, inspecting, filtering, and preparing downloads
 * from the Hugging Face Hub API.
 */
public interface IHuggingFaceHubService {

    /**
     * Searches Hugging Face model repositories matching a query and optional filters.
     *
     * @param query        search query string (e.g. "flux", "wan", "city96")
     * @param formatFilter optional format filter ("all", "gguf", "safetensors", "diffusers")
     * @param taskFilter   optional task filter ("all", "text-to-image", "image-to-video", "text-to-video")
     * @param sort         sort criteria ("trendingScore", "downloads", "likes", "lastModified")
     * @param limit        maximum number of repositories to return
     * @return CompletableFuture containing list of repository entries
     */
    CompletableFuture<List<HuggingFaceModelEntry>> searchRepositories(
            String query,
            String formatFilter,
            String taskFilter,
            String sort,
            int limit
    );

    /**
     * Retrieves the file tree and exact byte sizes for a specific repository.
     *
     * @param repoId repository ID (e.g. "city96/FLUX.1-dev-gguf")
     * @return CompletableFuture containing list of downloadable model files
     */
    CompletableFuture<List<HuggingFaceFileEntry>> getRepositoryFiles(String repoId);

    /**
     * Checks if a model file already exists locally in the designated folder.
     *
     * @param filename model filename
     * @param folder   target folder (e.g. "unet", "checkpoints", "vae")
     * @return true if installed on disk, false otherwise
     */
    boolean isFileInstalled(String filename, String folder);

    /**
     * Checks if a model file exists in the cold archive directory.
     *
     * @param filename model filename
     * @return true if found in archive, false otherwise
     */
    boolean isFileInArchive(String filename);

    /**
     * Constructs a {@link ModelInfo} object configured for the download pipeline.
     *
     * @param repo         the repository metadata
     * @param file         the specific model file entry
     * @param targetFolder the target ComfyUI folder
     * @return ModelInfo ready for download queueing
     */
    ModelInfo createModelInfoForDownload(HuggingFaceModelEntry repo, HuggingFaceFileEntry file, String targetFolder);
}
