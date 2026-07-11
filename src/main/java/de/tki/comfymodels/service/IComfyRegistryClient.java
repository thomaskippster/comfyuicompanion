package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ComfyRegistryWorkflow;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface IComfyRegistryClient {
    /**
     * Asynchronously fetches the list of workflows from the comfy.org registry API.
     *
     * @return a CompletableFuture containing the list of workflows
     */
    CompletableFuture<List<ComfyRegistryWorkflow>> fetchWorkflowsAsync();

    /**
     * Asynchronously downloads bytes from a given URL (e.g. JSON workflow or thumbnail).
     *
     * @param url the URL to download from
     * @return a CompletableFuture containing the downloaded bytes
     */
    CompletableFuture<byte[]> downloadFileAsync(String url);
}
