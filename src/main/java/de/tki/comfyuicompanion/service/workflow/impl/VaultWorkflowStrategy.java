package de.tki.comfyuicompanion.service.workflow.impl;

import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.domain.VaultWorkflowMetadata;
import de.tki.comfyuicompanion.domain.WorkflowDescriptor;
import de.tki.comfyuicompanion.domain.WorkflowSourceType;
import de.tki.comfyuicompanion.service.IWorkflowVaultRepository;
import de.tki.comfyuicompanion.service.vault.WorkflowVaultCoordinator;
import de.tki.comfyuicompanion.service.workflow.IWorkflowSourceStrategy;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintEntry;
import de.tki.comfyuicompanion.util.WorkflowImageLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Window;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Strategy implementation for user-managed workflows stored in the local Vault.
 *
 * @author Senior Java Developer
 */
@Component
public class VaultWorkflowStrategy implements IWorkflowSourceStrategy {

    private static final Logger log = LoggerFactory.getLogger(VaultWorkflowStrategy.class);

    private final WorkflowVaultCoordinator vaultCoordinator;
    private final IWorkflowVaultRepository vaultRepository;

    @Autowired
    public VaultWorkflowStrategy(WorkflowVaultCoordinator vaultCoordinator,
                                @Autowired(required = false) IWorkflowVaultRepository vaultRepository) {
        this.vaultCoordinator = vaultCoordinator;
        this.vaultRepository = vaultRepository;
    }

    @Override
    public WorkflowSourceType getSourceType() {
        return WorkflowSourceType.VAULT;
    }

    @Override
    public CompletableFuture<String> resolveWorkflowJson(WorkflowDescriptor descriptor) {
        return CompletableFuture.supplyAsync(() -> {
            String path = descriptor.workflowFilePathOrUrl();
            if (path == null || path.isBlank()) {
                throw new IllegalStateException("Vault workflow file path is missing for " + descriptor.title());
            }

            try {
                Path p = Path.of(path);
                if (Files.exists(p)) {
                    return Files.readString(p);
                }
                throw new IllegalStateException("Vault workflow file not found: " + path);
            } catch (Exception e) {
                log.error("Failed to read Vault workflow JSON for {}: {}", descriptor.title(), e.getMessage());
                throw new RuntimeException("Error reading Vault workflow: " + e.getMessage(), e);
            }
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
        // Vault is the authorized hub for downloading missing models
        return true;
    }

    @Override
    public void downloadMissingModels(WorkflowDescriptor descriptor, List<ModelInfo> missingModels, Window parent) {
        if (descriptor == null || vaultCoordinator == null) return;

        BlueprintEntry entry = null;
        if (descriptor.rawSourceEntry() instanceof BlueprintEntry be) {
            entry = be;
        } else {
            entry = new BlueprintEntry(new de.tki.comfyuicompanion.domain.ComfyRegistryWorkflow());
        }

        BlueprintEntry targetEntry = entry;
        Thread.ofVirtual().name("vault-strategy-download").start(() -> {
            try {
                int queued = vaultCoordinator.downloadMissingModels(targetEntry, null);
                SwingUtilities.invokeLater(() -> {
                    String msg = queued > 0
                            ? "Queued " + queued + " missing model(s) in Download Manager."
                            : "All models are already downloaded.";
                    JOptionPane.showMessageDialog(parent, msg, "Download Manager", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception ex) {
                log.error("Failed downloading missing models for Vault workflow {}: {}", descriptor.title(), ex.getMessage());
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(parent, "Download failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE)
                );
            }
        });
    }

    @Override
    public WorkflowDescriptor toWorkflowDescriptor(Object sourceEntry) {
        if (sourceEntry instanceof BlueprintEntry entry) {
            int missingCount = (entry.status != null) ? entry.status.missingCount : 0;
            return WorkflowDescriptor.builder()
                    .id("vault-" + (entry.filename != null ? entry.filename : entry.name))
                    .title(entry.name)
                    .category(entry.category != null ? entry.category : "Vault Workflow")
                    .description(entry.description != null ? entry.description : "")
                    .sourceType(WorkflowSourceType.VAULT)
                    .previewPathOrUrl(entry.previewPath)
                    .workflowFilePathOrUrl(entry.filePath)
                    .requiredModels(entry.requiredModels != null ? entry.requiredModels : Collections.emptyList())
                    .missingModelsCount(missingCount)
                    .rawSourceEntry(entry)
                    .build();
        } else if (sourceEntry instanceof VaultWorkflowMetadata meta) {
            return WorkflowDescriptor.builder()
                    .id("vault-" + meta.id())
                    .title(meta.title())
                    .category(meta.category() != null ? meta.category() : "Vault Workflow")
                    .architecture(meta.architecture() != null ? meta.architecture() : "Unknown")
                    .description(meta.description() != null ? meta.description() : "")
                    .sourceType(WorkflowSourceType.VAULT)
                    .sourceUrl(meta.sourceUrl())
                    .previewPathOrUrl(meta.previewFile())
                    .workflowFilePathOrUrl(meta.id() + ".json")
                    .requiredModels(meta.requiredModels() != null ? meta.requiredModels() : Collections.emptyList())
                    .missingModelsCount(0)
                    .rawSourceEntry(meta)
                    .build();
        }

        throw new IllegalArgumentException("Unsupported source entry type for VaultWorkflowStrategy: "
                + (sourceEntry != null ? sourceEntry.getClass().getName() : "null"));
    }
}
