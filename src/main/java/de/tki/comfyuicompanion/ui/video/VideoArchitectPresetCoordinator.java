package de.tki.comfyuicompanion.ui.video;

import de.tki.comfyuicompanion.domain.HardwareProfile;
import de.tki.comfyuicompanion.domain.VideoPresetConfig;
import de.tki.comfyuicompanion.service.IHardwareProfileService;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import java.awt.Color;
import java.util.function.Consumer;

/**
 * Coordinator for managing hardware profiles, presets, and model parameter configuration
 * in Video Architect.
 */
public class VideoArchitectPresetCoordinator {

    private final IHardwareProfileService hardwareProfileService;
    private final de.tki.comfyuicompanion.service.pipeline.strategy.VideoModelStrategyRegistry strategyRegistry;

    public VideoArchitectPresetCoordinator(IHardwareProfileService hardwareProfileService) {
        this(hardwareProfileService, null);
    }

    public VideoArchitectPresetCoordinator(
            IHardwareProfileService hardwareProfileService,
            de.tki.comfyuicompanion.service.pipeline.strategy.VideoModelStrategyRegistry strategyRegistry) {
        this.hardwareProfileService = hardwareProfileService;
        this.strategyRegistry = strategyRegistry;
    }

    /**
     * Applies hardware preset configurations to UI controls.
     *
     * @param logMessage          whether to log a confirmation message
     * @param hardwareProfileBadge the badge label
     * @param videoModelCombo      the model combo box
     * @param videoWidthSpinner    the width spinner
     * @param videoHeightSpinner   the height spinner
     * @param videoStepsSpinner    the steps spinner
     * @param videoCfgSpinner      the CFG scale spinner
     * @param videoPresetLabel     the preset label
     * @param loggerFn             logging consumer
     */
    public void applyHardwarePreset(boolean logMessage,
                                   JLabel hardwareProfileBadge,
                                   JComboBox<String> videoModelCombo,
                                   JSpinner videoWidthSpinner,
                                   JSpinner videoHeightSpinner,
                                   JSpinner videoStepsSpinner,
                                   JSpinner videoCfgSpinner,
                                   JLabel videoPresetLabel,
                                   Consumer<String> loggerFn) {
        if (hardwareProfileService == null) return;
        VideoPresetConfig config = hardwareProfileService.getRecommendedVideoConfig();
        HardwareProfile profile = hardwareProfileService.getHardwareProfile();

        if (hardwareProfileBadge != null && profile != null) {
            String badgeText = String.format("⚡ %s: %s VRAM • %s",
                    profile.tier().name(), profile.formattedVram(), profile.tier().getDescription());
            hardwareProfileBadge.setText(badgeText);
            hardwareProfileBadge.setToolTipText(config != null ? config.statusDescription() : profile.gpuName());
            if (profile.isWeakSystem()) {
                hardwareProfileBadge.setBackground(new Color(245, 158, 11, 40));
                hardwareProfileBadge.setForeground(new Color(217, 119, 6));
                hardwareProfileBadge.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(245, 158, 11, 100), 1, true),
                        BorderFactory.createEmptyBorder(4, 8, 4, 8)
                ));
            } else {
                hardwareProfileBadge.setBackground(new Color(16, 185, 129, 35));
                hardwareProfileBadge.setForeground(new Color(16, 185, 129));
                hardwareProfileBadge.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(16, 185, 129, 100), 1, true),
                        BorderFactory.createEmptyBorder(4, 8, 4, 8)
                ));
            }
        }

        if (config != null) {
            if (videoModelCombo != null && config.recommendedModel() != null) {
                videoModelCombo.setSelectedItem(config.recommendedModel());
            }
            if (videoWidthSpinner != null) {
                videoWidthSpinner.setValue(config.defaultWidth());
            }
            if (videoHeightSpinner != null) {
                videoHeightSpinner.setValue(config.defaultHeight());
            }
            if (videoStepsSpinner != null) {
                videoStepsSpinner.setValue(config.defaultSteps());
            }
            if (videoCfgSpinner != null) {
                videoCfgSpinner.setValue(config.defaultCfg());
            }
            if (videoPresetLabel != null) {
                videoPresetLabel.setText("Detected Preset: " + config.recommendedModel() + " (" + config.defaultWidth() + "x" + config.defaultHeight() + " | Safe VRAM)");
            }
            if (logMessage && loggerFn != null) {
                loggerFn.accept("⚡ Hardware preset applied: " + config.statusDescription());
            }
        }
    }

    /**
     * Updates model presets and spinner default values based on selected video model.
     * Polymorphically queries the corresponding strategy from the registry.
     *
     * @param selected           the selected model string
     * @param videoPresetLabel   the label displaying preset info
     * @param videoWidthSpinner  width spinner
     * @param videoHeightSpinner height spinner
     * @param videoStepsSpinner  steps spinner
     * @param videoCfgSpinner    cfg spinner
     * @param loggerFn           logging consumer
     */
    public void updateModelPreset(String selected,
                                 JLabel videoPresetLabel,
                                 JSpinner videoWidthSpinner,
                                 JSpinner videoHeightSpinner,
                                 JSpinner videoStepsSpinner,
                                 JSpinner videoCfgSpinner,
                                 Consumer<String> loggerFn) {
        boolean isLowVram = hardwareProfileService != null
                && hardwareProfileService.getHardwareProfile() != null
                && hardwareProfileService.getHardwareProfile().isWeakSystem();

        var strategyOpt = strategyRegistry != null
                ? strategyRegistry.findById(selected)
                : java.util.Optional.<de.tki.comfyuicompanion.service.pipeline.strategy.IVideoModelStrategy>empty();

        VideoPresetConfig preset;
        boolean vramIntensive;

        if (strategyOpt.isPresent()) {
            var strategy = strategyOpt.get();
            preset = strategy.getDefaultPreset(isLowVram);
            vramIntensive = strategy.isVramIntensive();
        } else {
            int w = isLowVram ? 1024 : 1280;
            int h = isLowVram ? 576 : 720;
            preset = new VideoPresetConfig(selected, w, h, 20, 3.0, 127, "default", "Detected Preset: " + selected + " (" + w + "x" + h + ")");
            vramIntensive = false;
        }

        if (videoPresetLabel != null) videoPresetLabel.setText(preset.statusDescription());
        if (videoWidthSpinner != null) videoWidthSpinner.setValue(preset.defaultWidth());
        if (videoHeightSpinner != null) videoHeightSpinner.setValue(preset.defaultHeight());
        if (videoStepsSpinner != null) videoStepsSpinner.setValue(preset.defaultSteps());
        if (videoCfgSpinner != null) videoCfgSpinner.setValue(preset.defaultCfg());

        if (isLowVram && vramIntensive && loggerFn != null && hardwareProfileService != null) {
            HardwareProfile profile = hardwareProfileService.getHardwareProfile();
            if (profile != null) {
                loggerFn.accept("⚠️ Notice: " + selected + " is very VRAM-intensive. On your system (" + profile.formattedVram() + " VRAM), FastWan 5B or LTX-Video is recommended.");
            }
        }

        if (loggerFn != null && videoWidthSpinner != null && videoHeightSpinner != null) {
            loggerFn.accept("Switched model preset: " + selected + " (" + videoWidthSpinner.getValue() + "x" + videoHeightSpinner.getValue() + ")");
        }
    }
}
