package de.tki.comfymodels.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tki.comfymodels.domain.ComfyRegistryWorkflow;
import de.tki.comfymodels.service.IComfyRegistryClient;
import de.tki.comfymodels.service.IWorkflowDownloader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@Service
public class WorkflowDownloader implements IWorkflowDownloader {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(WorkflowDownloader.class);

    private final IComfyRegistryClient registryClient;

    @Autowired
    public WorkflowDownloader(IComfyRegistryClient registryClient) {
        this.registryClient = registryClient;
    }

    @Override
    public CompletableFuture<File> downloadWorkflowAsync(ComfyRegistryWorkflow workflow) {
        String jsonUrl = workflow.getJsonDownloadUrl();
        String thumbUrl = workflow.getThumbnailUrl();

        if (jsonUrl == null || jsonUrl.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Workflow does not have a JSON download URL"));
        }

        // Sanitize name for files
        String baseName = sanitizeFileName(workflow.getTitle());

        File workflowsDir = new File("workflows");
        if (!workflowsDir.exists()) {
            workflowsDir.mkdirs();
        }

        File jsonFile = new File(workflowsDir, baseName + ".json");

        // Fast-path: If the workflow JSON already exists locally, return it immediately without network roundtrip
        if (jsonFile.exists() && jsonFile.length() > 0) {
            logger.info("⚡ [WorkflowDownloader] Using local cached workflow JSON: " + jsonFile.getAbsolutePath());
            ensureThumbnailCachedAsync(workflowsDir, baseName, thumbUrl);
            return CompletableFuture.completedFuture(jsonFile);
        }

        // Step 1: Download JSON payload immediately
        CompletableFuture<byte[]> jsonDownload = registryClient.downloadFileAsync(jsonUrl);
        
        // Step 2: Download Thumbnail in background (fire-and-forget, does not block workflow JSON readiness)
        ensureThumbnailCachedAsync(workflowsDir, baseName, thumbUrl);

        return jsonDownload.thenApply(jsonBytes -> {
            try {
                Files.write(jsonFile.toPath(), jsonBytes);
                logger.info("💾 [WorkflowDownloader] Saved workflow JSON to: " + jsonFile.getAbsolutePath());
                return jsonFile;
            } catch (IOException e) {
                throw new RuntimeException("Failed to save workflow files locally", e);
            }
        });
    }

    private void ensureThumbnailCachedAsync(File workflowsDir, String baseName, String thumbUrl) {
        if (thumbUrl == null || thumbUrl.isEmpty() || thumbUrl.startsWith("mock:")) {
            return;
        }
        String ext = extractExtension(thumbUrl, "png");
        File thumbFile = new File(workflowsDir, baseName + "." + ext);
        if (thumbFile.exists() && thumbFile.length() > 0) {
            return;
        }

        registryClient.downloadFileAsync(thumbUrl)
                .thenAccept(thumbBytes -> {
                    if (thumbBytes != null && thumbBytes.length > 0) {
                        try {
                            Files.write(thumbFile.toPath(), thumbBytes);
                            logger.info("💾 [WorkflowDownloader] Background saved thumbnail to: " + thumbFile.getAbsolutePath());
                        } catch (IOException e) {
                            logger.warn("Failed saving background thumbnail: " + e.getMessage());
                        }
                    }
                })
                .exceptionally(ex -> {
                    logger.debug("Thumbnail background download skipped or failed: " + ex.getMessage());
                    return null;
                });
    }

    private String sanitizeFileName(String title) {
        if (title == null || title.isEmpty()) {
            return "workflow_" + System.currentTimeMillis();
        }
        return title.replaceAll("[^a-zA-Z0-9\\-_\\s]", "")
                .trim()
                .replaceAll("\\s+", "_")
                .toLowerCase(Locale.ROOT);
    }

    private String extractExtension(String url, String defaultExt) {
        if (url == null || !url.contains(".")) {
            return defaultExt;
        }
        String ext = url.substring(url.lastIndexOf('.') + 1);
        if (ext.contains("?")) {
            ext = ext.substring(0, ext.indexOf('?'));
        }
        if (ext.contains("#")) {
            ext = ext.substring(0, ext.indexOf('#'));
        }
        ext = ext.toLowerCase(Locale.ROOT).trim();
        return ext.isEmpty() ? defaultExt : ext;
    }
}
