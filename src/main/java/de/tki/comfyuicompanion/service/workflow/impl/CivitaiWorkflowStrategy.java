package de.tki.comfyuicompanion.service.workflow.impl;

import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.domain.WorkflowDescriptor;
import de.tki.comfyuicompanion.domain.WorkflowSourceType;
import de.tki.comfyuicompanion.service.ICivitaiOperationalModelService;
import de.tki.comfyuicompanion.service.workflow.IWorkflowSourceStrategy;
import de.tki.comfyuicompanion.util.WorkflowImageLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.awt.Window;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Strategy implementation for Civitai workflows and model entries.
 *
 * @author Senior Java Developer
 */
@Component
public class CivitaiWorkflowStrategy implements IWorkflowSourceStrategy {

    private static final Logger log = LoggerFactory.getLogger(CivitaiWorkflowStrategy.class);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final ICivitaiOperationalModelService operationalModelService;

    @Autowired
    public CivitaiWorkflowStrategy(@Autowired(required = false) ICivitaiOperationalModelService operationalModelService) {
        this.operationalModelService = operationalModelService;
    }

    @Override
    public WorkflowSourceType getSourceType() {
        return WorkflowSourceType.CIVITAI;
    }

    @Override
    public CompletableFuture<String> resolveWorkflowJson(WorkflowDescriptor descriptor) {
        return CompletableFuture.supplyAsync(() -> {
            if (descriptor.rawSourceEntry() instanceof CivitaiModelEntry entry && operationalModelService != null) {
                try {
                    String json = operationalModelService.generateGuiWorkflowJson(entry, descriptor.requiredModels(), null);
                    if (json != null && !json.isBlank()) {
                        return json;
                    }
                } catch (Exception e) {
                    log.warn("Operational model service failed to generate workflow for Civitai model {}: {}", descriptor.id(), e.getMessage());
                }
            }

            String url = descriptor.workflowFilePathOrUrl();
            if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                try {
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("User-Agent", "ComfyUICompanion/2.0")
                            .timeout(Duration.ofSeconds(15))
                            .GET()
                            .build();
                    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        return resp.body();
                    }
                } catch (Exception ex) {
                    log.error("Failed fetching Civitai workflow JSON from {}: {}", url, ex.getMessage());
                }
            }

            throw new IllegalStateException("Unable to resolve Civitai workflow JSON for " + descriptor.title());
        });
    }

    @Override
    public CompletableFuture<byte[]> fetchPreviewImageBytes(WorkflowDescriptor descriptor) {
        return CompletableFuture.supplyAsync(() ->
                WorkflowImageLoader.loadImageBytes(descriptor.previewPathOrUrl())
        );
    }

    @Override
    public boolean canDownloadMissingModels() {
        // Business Rule: Civitai tab only browses; downloads are restricted to My Vault
        return false;
    }

    @Override
    public void downloadMissingModels(WorkflowDescriptor descriptor, List<ModelInfo> missingModels, Window parent) {
        log.warn("Download missing models is disabled in CivitAI tab. Save to Vault first.");
    }

    @Override
    public WorkflowDescriptor toWorkflowDescriptor(Object sourceEntry) {
        if (sourceEntry instanceof CivitaiModelEntry entry) {
            String sourceUrl = "https://civitai.com/models/" + entry.id();
            Map<String, String> attrs = Map.of(
                    "creator", entry.creator() != null ? entry.creator() : "Unknown",
                    "downloads", String.valueOf(entry.downloadCount()),
                    "rating", String.valueOf(entry.rating()),
                    "baseModel", entry.baseModel() != null ? entry.baseModel() : "Unknown"
            );

            List<ModelInfo> reqModels = operationalModelService != null
                    ? operationalModelService.resolveRequiredModels(entry)
                    : Collections.emptyList();
            int missing = operationalModelService != null
                    ? operationalModelService.getMissingModels(reqModels).size()
                    : 0;

            return WorkflowDescriptor.builder()
                    .id("civitai-" + entry.id())
                    .title(entry.name())
                    .category("Civitai " + (entry.type() != null ? entry.type() : "Model"))
                    .architecture(entry.baseModel() != null ? entry.baseModel() : "Unknown")
                    .author(entry.creator() != null ? entry.creator() : "Civitai")
                    .description(entry.description() != null ? entry.description() : "")
                    .sourceType(WorkflowSourceType.CIVITAI)
                    .sourceUrl(sourceUrl)
                    .previewPathOrUrl(entry.thumbnailUrl())
                    .workflowFilePathOrUrl(entry.downloadUrl())
                    .requiredModels(reqModels)
                    .missingModelsCount(missing)
                    .rawSourceEntry(entry)
                    .extendedAttributes(attrs)
                    .build();
        }

        throw new IllegalArgumentException("Unsupported source entry type for CivitaiWorkflowStrategy: "
                + (sourceEntry != null ? sourceEntry.getClass().getName() : "null"));
    }
}
