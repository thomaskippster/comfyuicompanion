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

    public VideoArchitectPresetCoordinator(IHardwareProfileService hardwareProfileService) {
        this.hardwareProfileService = hardwareProfileService;
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
                loggerFn.accept("⚡ Hardware-Vorkonfiguration angewendet: " + config.statusDescription());
            }
        }
    }

    /**
     * Updates model presets and spinner default values based on selected video model.
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
        boolean isLowVram = false;
        if (hardwareProfileService != null) {
            HardwareProfile profile = hardwareProfileService.getHardwareProfile();
            isLowVram = profile != null && profile.isWeakSystem();
        }

        if (selected.contains("LTX-Video")) {
            int w = isLowVram ? 1024 : 1280;
            int h = isLowVram ? 576 : 720;
            if (videoPresetLabel != null) videoPresetLabel.setText("Detected Preset: LTX-Video High-Speed (" + w + "x" + h + " | 24 fps)");
            if (videoWidthSpinner != null) videoWidthSpinner.setValue(w);
            if (videoHeightSpinner != null) videoHeightSpinner.setValue(h);
            if (videoStepsSpinner != null) videoStepsSpinner.setValue(30);
            if (videoCfgSpinner != null) videoCfgSpinner.setValue(3.0);
        } else if (selected.contains("Flux")) {
            int w = isLowVram ? 1024 : 1280;
            int h = isLowVram ? 576 : 720;
            if (videoPresetLabel != null) videoPresetLabel.setText("Detected Preset: Flux Widescreen (" + w + "x" + h + " | FluxGuidance 3.5)");
            if (videoWidthSpinner != null) videoWidthSpinner.setValue(w);
            if (videoHeightSpinner != null) videoHeightSpinner.setValue(h);
            if (videoStepsSpinner != null) videoStepsSpinner.setValue(22);
            if (videoCfgSpinner != null) videoCfgSpinner.setValue(1.0);
        } else if (selected.contains("SDXL") || selected.contains("Pony") || selected.contains("Illustrious")) {
            if (videoPresetLabel != null) videoPresetLabel.setText("Detected Preset: SDXL / Pony Widescreen (1024x576 | DPMPP_2M Karras)");
            if (videoWidthSpinner != null) videoWidthSpinner.setValue(1024);
            if (videoHeightSpinner != null) videoHeightSpinner.setValue(576);
            if (videoStepsSpinner != null) videoStepsSpinner.setValue(28);
            if (videoCfgSpinner != null) videoCfgSpinner.setValue(6.5);
        } else if (selected.contains("SD 1.5")) {
            if (videoPresetLabel != null) videoPresetLabel.setText("Detected Preset: SD 1.5 Upscale (768x432 -> 1280x720)");
            if (videoWidthSpinner != null) videoWidthSpinner.setValue(768);
            if (videoHeightSpinner != null) videoHeightSpinner.setValue(432);
            if (videoStepsSpinner != null) videoStepsSpinner.setValue(28);
            if (videoCfgSpinner != null) videoCfgSpinner.setValue(6.5);
        } else if (selected.contains("Hunyuan")) {
            int w = isLowVram ? 1024 : 1280;
            int h = isLowVram ? 576 : 720;
            if (videoPresetLabel != null) videoPresetLabel.setText("Detected Preset: Hunyuan Video 1.5 (" + w + "x" + h + " | 24 fps)");
            if (videoWidthSpinner != null) videoWidthSpinner.setValue(w);
            if (videoHeightSpinner != null) videoHeightSpinner.setValue(h);
            if (videoStepsSpinner != null) videoStepsSpinner.setValue(20);
            if (videoCfgSpinner != null) videoCfgSpinner.setValue(6.0);
        } else {
            // Wan 2.2 / Default
            int w = isLowVram ? 1024 : 1280;
            int h = isLowVram ? 576 : 720;
            if (videoPresetLabel != null) videoPresetLabel.setText("Detected Preset: Wan 2.2 Cinematic Video (" + w + "x" + h + " | 16 fps)");
            if (videoWidthSpinner != null) videoWidthSpinner.setValue(w);
            if (videoHeightSpinner != null) videoHeightSpinner.setValue(h);
            if (videoStepsSpinner != null) videoStepsSpinner.setValue(20);
            if (videoCfgSpinner != null) videoCfgSpinner.setValue(3.0);
        }

        if (hardwareProfileService != null) {
            HardwareProfile profile = hardwareProfileService.getHardwareProfile();
            if (profile != null && profile.isWeakSystem() && (selected.contains("Wan") || selected.contains("Hunyuan"))) {
                if (loggerFn != null) {
                    loggerFn.accept("⚠️ Notice: " + selected + " is very VRAM-intensive (14B). On your system (" + profile.formattedVram() + " VRAM), LTX-Video is recommended.");
                }
            }
        }

        if (loggerFn != null && videoWidthSpinner != null && videoHeightSpinner != null) {
            loggerFn.accept("Switched model preset: " + selected + " (" + videoWidthSpinner.getValue() + "x" + videoHeightSpinner.getValue() + ")");
        }
    }
}
