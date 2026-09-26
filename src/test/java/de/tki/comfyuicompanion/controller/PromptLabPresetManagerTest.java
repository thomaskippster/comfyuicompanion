package de.tki.comfyuicompanion.controller;

import de.tki.comfyuicompanion.service.IComfyTemplateService;
import de.tki.comfyuicompanion.service.IModelArchitectureService;
import de.tki.comfyuicompanion.ui.PromptLabView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.swing.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PromptLabPresetManager} verifying diffusion model presets
 * and resolution/steps auto-configuration.
 */
@ExtendWith(MockitoExtension.class)
class PromptLabPresetManagerTest {

    @Mock
    private IModelArchitectureService modelArchitectureService;

    @Mock
    private IComfyTemplateService comfyTemplateService;

    @Mock
    private PromptLabModelResolver modelResolver;

    @Mock
    private PromptLabView promptLabView;

    private PromptLabPresetManager presetManager;

    private JLabel presetLabel;
    private JSpinner widthSpinner;
    private JSpinner heightSpinner;
    private JSpinner stepsSpinner;
    private JSpinner cfgSpinner;
    private JComboBox<String> samplerCombo;
    private JComboBox<String> schedulerCombo;

    @BeforeEach
    void setUp() {
        presetLabel = new JLabel();
        widthSpinner = new JSpinner(new SpinnerNumberModel(512, 64, 4096, 64));
        heightSpinner = new JSpinner(new SpinnerNumberModel(512, 64, 4096, 64));
        stepsSpinner = new JSpinner(new SpinnerNumberModel(20, 1, 150, 1));
        cfgSpinner = new JSpinner(new SpinnerNumberModel(7.0, 0.0, 30.0, 0.5));
        samplerCombo = new JComboBox<>();
        schedulerCombo = new JComboBox<>();

        presetManager = new PromptLabPresetManager(modelArchitectureService, comfyTemplateService, modelResolver);
    }

    private void wireMockView() {
        org.mockito.Mockito.lenient().when(promptLabView.getPromptPresetLabel()).thenReturn(presetLabel);
        org.mockito.Mockito.lenient().when(promptLabView.getPromptWidthSpinner()).thenReturn(widthSpinner);
        org.mockito.Mockito.lenient().when(promptLabView.getPromptHeightSpinner()).thenReturn(heightSpinner);
        org.mockito.Mockito.lenient().when(promptLabView.getPromptStepsSpinner()).thenReturn(stepsSpinner);
        org.mockito.Mockito.lenient().when(promptLabView.getPromptCfgSpinner()).thenReturn(cfgSpinner);
        org.mockito.Mockito.lenient().when(promptLabView.getPromptSamplerCombo()).thenReturn(samplerCombo);
        org.mockito.Mockito.lenient().when(promptLabView.getPromptSchedulerCombo()).thenReturn(schedulerCombo);
    }

    @Test
    @DisplayName("applyModelPreset: should detect and configure FLUX.1 Schnell fast 4-step preset")
    void shouldConfigureFluxSchnellPreset() {
        wireMockView();
        presetManager.applyModelPreset(promptLabView, "flux1-schnell.safetensors", "flux1-schnell.safetensors", null, null);

        assertThat(presetLabel.getText()).contains("FLUX.1 Schnell (Fast 4-Step)");
        assertThat(widthSpinner.getValue()).isEqualTo(1024);
        assertThat(heightSpinner.getValue()).isEqualTo(1024);
        assertThat(stepsSpinner.getValue()).isEqualTo(4);
        assertThat(cfgSpinner.getValue()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("applyModelPreset: should detect and configure SDXL preset")
    void shouldConfigureSdxlPreset() {
        wireMockView();
        presetManager.applyModelPreset(promptLabView, "juggernautXL_v9.safetensors", "juggernautXL_v9.safetensors", null, null);

        assertThat(presetLabel.getText()).contains("Stable Diffusion XL (SDXL)");
        assertThat(widthSpinner.getValue()).isEqualTo(1024);
        assertThat(heightSpinner.getValue()).isEqualTo(1024);
        assertThat(stepsSpinner.getValue()).isEqualTo(30);
        assertThat(cfgSpinner.getValue()).isEqualTo(6.0);
    }

    @Test
    @DisplayName("applyModelPreset: should detect and configure SD 1.5 preset")
    void shouldConfigureSd15Preset() {
        wireMockView();
        presetManager.applyModelPreset(promptLabView, "v1-5-pruned-emaonly.safetensors", "v1-5-pruned-emaonly.safetensors", null, null);

        assertThat(presetLabel.getText()).contains("Stable Diffusion 1.5 (SD 1.5)");
        assertThat(widthSpinner.getValue()).isEqualTo(512);
        assertThat(heightSpinner.getValue()).isEqualTo(512);
        assertThat(stepsSpinner.getValue()).isEqualTo(20);
        assertThat(cfgSpinner.getValue()).isEqualTo(7.0);
    }

    @Test
    @DisplayName("applyModelPreset: should safely ignore null view or modelName")
    void shouldHandleNullInputsGracefully() {
        presetManager.applyModelPreset(null, "flux", "flux", null, null);
        presetManager.applyModelPreset(promptLabView, null, "flux", null, null);
        presetManager.applyModelPreset(promptLabView, "flux", null, null, null);
    }
}
