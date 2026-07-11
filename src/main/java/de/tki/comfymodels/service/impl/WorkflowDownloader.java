package de.tki.comfymodels.service.impl;

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

        // Step 1: Download JSON payload
        CompletableFuture<byte[]> jsonDownload = registryClient.downloadFileAsync(jsonUrl);
        
        // Step 2: Download Thumbnail (Optional fallback if not available)
        CompletableFuture<byte[]> thumbDownload = (thumbUrl != null && !thumbUrl.isEmpty() && !thumbUrl.startsWith("mock:"))
                ? registryClient.downloadFileAsync(thumbUrl)
                : CompletableFuture.completedFuture(new byte[0]);

        return jsonDownload.thenCombine(thumbDownload, (jsonBytes, thumbBytes) -> {
            try {
                // Save JSON File
                Files.write(jsonFile.toPath(), jsonBytes);
                System.out.println("💾 [WorkflowDownloader] Saved workflow JSON to: " + jsonFile.getAbsolutePath());

                // Save Thumbnail if downloaded successfully
                if (thumbBytes != null && thumbBytes.length > 0) {
                    String ext = extractExtension(thumbUrl, "png");
                    File thumbFile = new File(workflowsDir, baseName + "." + ext);
                    Files.write(thumbFile.toPath(), thumbBytes);
                    System.out.println("💾 [WorkflowDownloader] Saved thumbnail to: " + thumbFile.getAbsolutePath());
                }

                return jsonFile;
            } catch (IOException e) {
                throw new RuntimeException("Failed to save workflow files locally", e);
            }
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
