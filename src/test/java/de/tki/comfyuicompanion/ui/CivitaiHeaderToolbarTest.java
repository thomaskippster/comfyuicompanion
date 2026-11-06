package de.tki.comfyuicompanion.ui;

import de.tki.comfyuicompanion.ui.civitai.CivitaiHeaderToolbar;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests verifying dynamic filter updates in CivitaiHeaderToolbar.
 */
class CivitaiHeaderToolbarTest {

    @Test
    void shouldUpdateBaseModelsDynamically_andPreserveDefault() throws Exception {
        CivitaiHeaderToolbar toolbar = new CivitaiHeaderToolbar();

        assertThat(toolbar.getSelectedBaseModel()).isEmpty();

        toolbar.setAvailableBaseModels(List.of("Flux.1 D", "Flux.3 Klein", "SDXL 1.0"));

        // Wait for SwingUtilities.invokeLater execution
        SwingUtilities.invokeAndWait(() -> {});

        assertThat(toolbar.getSelectedBaseModel()).isEmpty();
    }

    @Test
    void shouldUpdateTypesDynamically_andPreserveWorkflowsSelection() throws Exception {
        CivitaiHeaderToolbar toolbar = new CivitaiHeaderToolbar();
        assertThat(toolbar.getSelectedType()).isEqualTo("Workflows");

        toolbar.setAvailableTypes(List.of("Checkpoint", "LORA", "ControlNet"));

        SwingUtilities.invokeAndWait(() -> {});

        assertThat(toolbar.getSelectedType()).isEqualTo("Workflows");
    }
}
