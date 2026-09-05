package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ComfyRegistryWorkflow;
import java.io.File;
import java.util.concurrent.CompletableFuture;

public interface IWorkflowDownloader {
    /**
     * Downloads the workflow JSON and its thumbnail, saving them in the isolated user_workflows/ directory.
     * The files will be saved with the same sanitized name matching the workflow's title.
     *
     * @param workflow the workflow to download
     * @return a CompletableFuture resolving to the downloaded JSON file
     */
    CompletableFuture<File> downloadWorkflowAsync(ComfyRegistryWorkflow workflow);
}
