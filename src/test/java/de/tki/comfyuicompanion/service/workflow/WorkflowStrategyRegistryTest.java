package de.tki.comfyuicompanion.service.workflow;

import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.domain.ComfyRegistryWorkflow;
import de.tki.comfyuicompanion.domain.HuggingFaceModelEntry;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.domain.WorkflowDescriptor;
import de.tki.comfyuicompanion.domain.WorkflowSourceType;
import de.tki.comfyuicompanion.service.workflow.impl.CivitaiWorkflowStrategy;
import de.tki.comfyuicompanion.service.workflow.impl.ComfyBlueprintStrategy;
import de.tki.comfyuicompanion.service.workflow.impl.HuggingFaceWorkflowStrategy;
import de.tki.comfyuicompanion.service.workflow.impl.VaultWorkflowStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link WorkflowStrategyRegistry} and workflow strategy implementations.
 * Verifies uniform domain mapping and strict enforcement of vault-exclusive download rules.
 */
class WorkflowStrategyRegistryTest {

    private WorkflowStrategyRegistry registry;
    private ComfyBlueprintStrategy comfyStrategy;
    private CivitaiWorkflowStrategy civitaiStrategy;
    private HuggingFaceWorkflowStrategy hfStrategy;
    private VaultWorkflowStrategy vaultStrategy;

    @BeforeEach
    void setUp() {
        comfyStrategy = new ComfyBlueprintStrategy();
        civitaiStrategy = new CivitaiWorkflowStrategy(null);
        hfStrategy = new HuggingFaceWorkflowStrategy();
        vaultStrategy = new VaultWorkflowStrategy(null, null);

        registry = new WorkflowStrategyRegistry(List.of(
                comfyStrategy,
                civitaiStrategy,
                hfStrategy,
                vaultStrategy
        ));
    }

    @Test
    @DisplayName("Registry should resolve strategy for each WorkflowSourceType")
    void testResolveAllStrategies() {
        assertThat(registry.getStrategy(WorkflowSourceType.COMFY_ORG)).isSameAs(comfyStrategy);
        assertThat(registry.getStrategy(WorkflowSourceType.CIVITAI)).isSameAs(civitaiStrategy);
        assertThat(registry.getStrategy(WorkflowSourceType.HUGGING_FACE)).isSameAs(hfStrategy);
        assertThat(registry.getStrategy(WorkflowSourceType.VAULT)).isSameAs(vaultStrategy);
    }

    @Test
    @DisplayName("Enforce business rule: only Vault allows direct missing model downloads")
    void testDownloadMissingModelsPermissionRule() {
        assertThat(comfyStrategy.canDownloadMissingModels()).isFalse();
        assertThat(civitaiStrategy.canDownloadMissingModels()).isFalse();
        assertThat(hfStrategy.canDownloadMissingModels()).isFalse();
        assertThat(vaultStrategy.canDownloadMissingModels()).isTrue();
    }

    @Test
    @DisplayName("Civitai strategy correctly converts CivitaiModelEntry to WorkflowDescriptor")
    void testCivitaiDescriptorConversion() {
        CivitaiModelEntry entry = new CivitaiModelEntry(
                12345L, "Test SDXL Model", "Checkpoint", "Artist", "SDXL 1.0",
                "Description", "https://example.com/thumb.jpg", "https://example.com/model.safetensors",
                "model.safetensors", "6.5 GB", 6979321856L, 100, 4.8,
                List.of("tag1"), false, false, "All", true, true
        );

        WorkflowDescriptor desc = civitaiStrategy.toWorkflowDescriptor(entry);

        assertThat(desc.id()).isEqualTo("civitai-12345");
        assertThat(desc.title()).isEqualTo("Test SDXL Model");
        assertThat(desc.sourceType()).isEqualTo(WorkflowSourceType.CIVITAI);
        assertThat(desc.sourceUrl()).isEqualTo("https://civitai.com/models/12345");
        assertThat(desc.isReadyToExecute()).isTrue();
    }

    @Test
    @DisplayName("Hugging Face strategy correctly converts HuggingFaceModelEntry to WorkflowDescriptor")
    void testHuggingFaceDescriptorConversion() {
        HuggingFaceModelEntry repo = new HuggingFaceModelEntry(
                "author/flux-dev", "author", "flux-dev",
                500, 120, "2026-10-31", "text-to-image",
                List.of("diffusers"), "false", Collections.emptyList(), "https://hf.co/thumb.png"
        );

        WorkflowDescriptor desc = hfStrategy.toWorkflowDescriptor(repo);

        assertThat(desc.id()).isEqualTo("hf-author_flux-dev");
        assertThat(desc.title()).isEqualTo("flux-dev");
        assertThat(desc.author()).isEqualTo("author");
        assertThat(desc.sourceType()).isEqualTo(WorkflowSourceType.HUGGING_FACE);
        assertThat(desc.sourceUrl()).isEqualTo("https://huggingface.co/author/flux-dev");
    }

    @Test
    @DisplayName("WorkflowDescriptor builder calculates missing model count correctly")
    void testWorkflowDescriptorMissingCountCalculation() {
        ModelInfo m1 = new ModelInfo("checkpoints", "model1.safetensors", "http://m1");
        ModelInfo m2 = new ModelInfo("loras", "model2.safetensors", "http://m2");

        WorkflowDescriptor desc = WorkflowDescriptor.builder()
                .id("test-wf")
                .title("Test WF")
                .sourceType(WorkflowSourceType.VAULT)
                .requiredModels(List.of(m1, m2))
                .calculateMissingModels(info -> "model1.safetensors".equals(info.getName()))
                .build();

        assertThat(desc.missingModelsCount()).isEqualTo(1);
        assertThat(desc.isReadyToExecute()).isFalse();
    }
}
