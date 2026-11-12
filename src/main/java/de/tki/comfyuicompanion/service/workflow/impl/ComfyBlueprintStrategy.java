package de.tki.comfyuicompanion.service.workflow.impl;

import de.tki.comfyuicompanion.domain.ComfyRegistryWorkflow;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.domain.WorkflowDescriptor;
import de.tki.comfyuicompanion.domain.WorkflowSourceType;
import de.tki.comfyuicompanion.service.workflow.IWorkflowSourceStrategy;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintEntry;
import de.tki.comfyuicompanion.util.WorkflowImageLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.Window;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Strategy implementation for official and registry ComfyUI workflows.
 *
 * @author Senior Java Developer
 */
@Component
public class ComfyBlueprintStrategy implements IWorkflowSourceStrategy {

    private static final Logger log = LoggerFactory.getLogger(ComfyBlueprintStrategy.class);
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public WorkflowSourceType getSourceType() {
        return WorkflowSourceType.COMFY_ORG;
    }

    @Override
    public CompletableFuture<String> resolveWorkflowJson(WorkflowDescriptor descriptor) {
        return CompletableFuture.supplyAsync(() -> {
            String target = descriptor.workflowFilePathOrUrl();
            if (target == null || target.isBlank()) {
                throw new IllegalStateException("Workflow file path or URL is not defined for " + descriptor.title());
            }

            try {
                if (target.startsWith("http://") || target.startsWith("https://")) {
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(target))
                            .header("User-Agent", "ComfyUICompanion/2.0")
                            .timeout(Duration.ofSeconds(15))
                            .GET()
                            .build();
                    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        return resp.body();
                    }
                    throw new IllegalStateException("HTTP " + resp.statusCode() + " fetching workflow JSON from " + target);
                } else {
                    Path p = Path.of(target);
                    if (Files.exists(p)) {
                        return Files.readString(p);
                    }
                    throw new IllegalStateException("Local file not found: " + target);
                }
            } catch (Exception e) {
                log.error("Failed to resolve workflow JSON for {}: {}", descriptor.title(), e.getMessage());
                throw new RuntimeException("Error resolving workflow JSON: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<byte[]> fetchPreviewImageBytes(WorkflowDescriptor descriptor) {
        return CompletableFuture.supplyAsync(() -> {
            byte[] bytes = WorkflowImageLoader.loadImageBytes(descriptor.previewPathOrUrl());
            if (bytes != null && bytes.length > 0) {
                return bytes;
            }

            if (descriptor.rawSourceEntry() instanceof BlueprintEntry entry && entry.registryWorkflow != null) {
                ComfyRegistryWorkflow wf = entry.registryWorkflow;
                if (wf.getPreviewCandidates() != null) {
                    for (String cand : wf.getPreviewCandidates()) {
                        byte[] candBytes = WorkflowImageLoader.loadImageBytes(cand);
                        if (candBytes != null && candBytes.length > 0) {
                            return candBytes;
                        }
                    }
                }

                String id = wf.getId();
                if (id != null && !id.isBlank()) {
                    String repoBase = "https://raw.githubusercontent.com/Comfy-Org/workflow_templates/main/";
                    String[] fallbackUrls = new String[]{
                            repoBase + "thumbnail/" + id + ".webp",
                            repoBase + "thumbnail/" + id + ".png",
                            repoBase + "output/" + id + ".png",
                            repoBase + "output/" + id + ".webp",
                            repoBase + "input/" + id + ".png"
                    };
                    for (String fb : fallbackUrls) {
                        byte[] fbBytes = WorkflowImageLoader.loadImageBytes(fb);
                        if (fbBytes != null && fbBytes.length > 0) {
                            return fbBytes;
                        }
                    }
                }
            }

            return new byte[0];
        });
    }

    @Override
    public boolean canDownloadMissingModels() {
        // Business Rule: Downloads are performed exclusively inside My Vault
        return false;
    }

    @Override
    public void downloadMissingModels(WorkflowDescriptor descriptor, List<ModelInfo> missingModels, Window parent) {
        log.warn("Download missing models is disabled in ComfyUI Blueprints tab. Save to Vault first.");
    }

    @Override
    public WorkflowDescriptor toWorkflowDescriptor(Object sourceEntry) {
        if (sourceEntry instanceof BlueprintEntry entry) {
            int missingCount = (entry.status != null) ? entry.status.missingCount : 0;
            return WorkflowDescriptor.builder()
                    .id(entry.filename != null ? entry.filename : entry.name)
                    .title(entry.name)
                    .category(entry.category != null ? entry.category : "General")
                    .description(entry.description != null ? entry.description : "")
                    .sourceType(WorkflowSourceType.COMFY_ORG)
                    .previewPathOrUrl(entry.previewPath)
                    .workflowFilePathOrUrl(entry.filePath)
                    .requiredModels(entry.requiredModels != null ? entry.requiredModels : Collections.emptyList())
                    .missingModelsCount(missingCount)
                    .rawSourceEntry(entry)
                    .build();
        } else if (sourceEntry instanceof ComfyRegistryWorkflow reg) {
            return toWorkflowDescriptor(new BlueprintEntry(reg));
        }

        throw new IllegalArgumentException("Unsupported source entry type for ComfyBlueprintStrategy: "
                + (sourceEntry != null ? sourceEntry.getClass().getName() : "null"));
    }
}
