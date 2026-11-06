package de.tki.comfyuicompanion.ui.workflow;

import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.domain.WorkflowDescriptor;
import de.tki.comfyuicompanion.domain.WorkflowSourceType;
import de.tki.comfyuicompanion.service.workflow.impl.CivitaiWorkflowStrategy;
import de.tki.comfyuicompanion.service.workflow.impl.ComfyBlueprintStrategy;
import de.tki.comfyuicompanion.service.workflow.impl.HuggingFaceWorkflowStrategy;
import de.tki.comfyuicompanion.service.workflow.impl.VaultWorkflowStrategy;
import de.tki.comfyuicompanion.ui.blueprint.WorkflowDialogDimensionHelper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying behavior, button constraints, and layout calculations of {@link UnifiedWorkflowDetailsDialog}.
 *
 * @author Senior Java Developer
 */
class UnifiedWorkflowDetailsDialogTest {

    @Test
    void testDownloadMissingModelsOnlyAllowedInVault() {
        VaultWorkflowStrategy vaultStrategy = new VaultWorkflowStrategy(null, null);
        CivitaiWorkflowStrategy civitaiStrategy = new CivitaiWorkflowStrategy(null);
        HuggingFaceWorkflowStrategy hfStrategy = new HuggingFaceWorkflowStrategy();
        ComfyBlueprintStrategy comfyStrategy = new ComfyBlueprintStrategy();

        assertTrue(vaultStrategy.canDownloadMissingModels(), "Vault strategy must allow download of missing models");
        assertFalse(civitaiStrategy.canDownloadMissingModels(), "Civitai strategy must not allow direct download of missing models");
        assertFalse(hfStrategy.canDownloadMissingModels(), "HuggingFace strategy must not allow direct download of missing models");
        assertFalse(comfyStrategy.canDownloadMissingModels(), "ComfyBlueprint strategy must not allow direct download of missing models");
    }

    @Test
    void testDialogButtonsForVaultSource() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());

        List<ModelInfo> models = createSampleModels(3);
        WorkflowDescriptor desc = WorkflowDescriptor.builder()
                .id("vault-wf-1")
                .title("My Custom Vault Workflow")
                .category("Image")
                .sourceType(WorkflowSourceType.VAULT)
                .requiredModels(models)
                .missingModelsCount(2)
                .build();

        VaultWorkflowStrategy strategy = new VaultWorkflowStrategy(null, null);
        UnifiedWorkflowDetailsDialog dialog = new UnifiedWorkflowDetailsDialog(
                null, desc, strategy, null, null, null, null, null, null
        );

        List<JButton> buttons = findAllButtons(dialog);

        boolean hasDownloadMissing = buttons.stream().anyMatch(b -> b.getText() != null && b.getText().contains("Download Missing Models"));
        boolean hasRemoveFromVault = buttons.stream().anyMatch(b -> b.getText() != null && b.getText().contains("Remove from My Vault"));
        boolean hasSaveToVault = buttons.stream().anyMatch(b -> b.getText() != null && b.getText().contains("Save Workflow to My Vault"));
        boolean hasSendToComfy = buttons.stream().anyMatch(b -> b.getText() != null && b.getText().contains("Send to ComfyUI"));
        boolean hasClose = buttons.stream().anyMatch(b -> b.getText() != null && b.getText().contains("Close"));

        assertTrue(hasDownloadMissing, "Vault dialog must include Download Missing Models button when models are missing");
        assertTrue(hasRemoveFromVault, "Vault dialog must include Remove from My Vault button");
        assertFalse(hasSaveToVault, "Vault dialog must not include Save Workflow to My Vault button");
        assertTrue(hasSendToComfy, "Vault dialog must include Send to ComfyUI button");
        assertTrue(hasClose, "Vault dialog must include Close button");

        dialog.dispose();
    }

    @Test
    void testDialogButtonsForCivitaiSourceNeverShowsDownloadMissingModels() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());

        List<ModelInfo> models = createSampleModels(9);
        WorkflowDescriptor desc = WorkflowDescriptor.builder()
                .id("civitai-wf-1")
                .title("Civitai Searge SDXL Workflow")
                .category("Workflows")
                .sourceType(WorkflowSourceType.CIVITAI)
                .requiredModels(models)
                .missingModelsCount(9)
                .build();

        CivitaiWorkflowStrategy strategy = new CivitaiWorkflowStrategy(null);
        UnifiedWorkflowDetailsDialog dialog = new UnifiedWorkflowDetailsDialog(
                null, desc, strategy, null, null, null, null, null, null
        );

        List<JButton> buttons = findAllButtons(dialog);

        boolean hasDownloadMissing = buttons.stream().anyMatch(b -> b.getText() != null && b.getText().contains("Download Missing Models"));
        boolean hasSaveToVault = buttons.stream().anyMatch(b -> b.getText() != null && b.getText().contains("Save Workflow to My Vault"));
        boolean hasRemoveFromVault = buttons.stream().anyMatch(b -> b.getText() != null && b.getText().contains("Remove from My Vault"));
        boolean hasSendToComfy = buttons.stream().anyMatch(b -> b.getText() != null && b.getText().contains("Send to ComfyUI"));
        boolean hasClose = buttons.stream().anyMatch(b -> b.getText() != null && b.getText().contains("Close"));

        assertFalse(hasDownloadMissing, "Civitai dialog must NEVER include Download Missing Models button; downloads are restricted to My Vault");
        assertTrue(hasSaveToVault, "Civitai dialog must include Save Workflow to My Vault button");
        assertFalse(hasRemoveFromVault, "Civitai dialog must not include Remove from My Vault button");
        assertFalse(hasSendToComfy, "Civitai dialog must NOT include Send to ComfyUI button; sending is restricted to My Vault");
        assertTrue(hasClose, "Civitai dialog must include Close button");

        dialog.dispose();
    }

    @Test
    void testOptimalDialogDimensionAdaptsForManyModels() {
        Dimension sizeFew = WorkflowDialogDimensionHelper.calculateOptimalDialogSize(null, true, 8, 2, false);
        Dimension sizeMany = WorkflowDialogDimensionHelper.calculateOptimalDialogSize(null, true, 8, 14, false);

        assertNotNull(sizeFew);
        assertNotNull(sizeMany);
        java.awt.Rectangle screen = WorkflowDialogDimensionHelper.getUsableScreenBounds(null);
        int expectedWidth = Math.min((int) (screen.width * 0.95), Math.max(1260, 1320));
        assertEquals(expectedWidth, sizeFew.width, "Dialog width should match calculated optimal width");
        assertEquals(expectedWidth, sizeMany.width, "Dialog width should match calculated optimal width");
        assertTrue(sizeMany.height >= sizeFew.height, "Dialog height should scale to accommodate more model table rows");
    }

    @Test
    void testModelTablePanelRendersCrispColorsInLightMode() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());

        List<ModelInfo> models = createSampleModels(2);
        Color lightBg = java.awt.Color.WHITE;
        Color textPrimary = new Color(30, 41, 59);
        Color textSecondary = new Color(71, 85, 105);

        de.tki.comfyuicompanion.ui.blueprint.BlueprintModelTablePanel tablePanel =
                new de.tki.comfyuicompanion.ui.blueprint.BlueprintModelTablePanel(
                        models, lightBg, textPrimary, textSecondary,
                        m -> "Already exists", m -> "2.0 GB", null
                );

        assertNotNull(tablePanel);
        // Find JTable inside tablePanel
        javax.swing.JTable table = null;
        for (Component c : tablePanel.getComponents()) {
            if (c instanceof javax.swing.JScrollPane sp && sp.getViewport().getView() instanceof javax.swing.JTable tbl) {
                table = tbl;
                break;
            }
        }
        assertNotNull(table, "JTable must be present inside scroll pane");
        assertEquals(lightBg, table.getBackground(), "Table background must match white in light mode");
        assertEquals(textPrimary, table.getForeground(), "Table foreground must be crisp dark text in light mode");
    }

    private List<ModelInfo> createSampleModels(int count) {
        List<ModelInfo> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ModelInfo info = new ModelInfo();
            info.setName("sample_model_" + i + ".safetensors");
            info.setType("checkpoints");
            info.setSize("2.0 GB");
            list.add(info);
        }
        return list;
    }

    private List<JButton> findAllButtons(Container container) {
        List<JButton> list = new ArrayList<>();
        for (Component comp : container.getComponents()) {
            if (comp instanceof JButton btn) {
                list.add(btn);
            } else if (comp instanceof Container childContainer) {
                list.addAll(findAllButtons(childContainer));
            }
        }
        return list;
    }
}
