package de.tki.comfyuicompanion.ui.video;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.IVideoPromptOptimizer;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.ThemeManager;
import de.tki.comfyuicompanion.ui.dialog.RecentGenerationsAssetPickerDialog;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.function.Consumer;

/**
 * Modal dialog for creating or editing a storyboard scene.
 */
public class SceneEditDialog extends JDialog {

    public SceneEditDialog(Component parent,
                           Scene targetScene,
                           boolean isNew,
                           int defaultSceneIndex,
                           int defaultWidth,
                           int defaultHeight,
                           IVideoPromptOptimizer promptOptimizer,
                           Consumer<Scene> onSaved) {
        this(parent, targetScene, isNew, defaultSceneIndex, defaultWidth, defaultHeight, promptOptimizer, onSaved, null);
    }

    public SceneEditDialog(Component parent,
                           Scene targetScene,
                           boolean isNew,
                           int defaultSceneIndex,
                           int defaultWidth,
                           int defaultHeight,
                           IVideoPromptOptimizer promptOptimizer,
                           Consumer<Scene> onSaved,
                           ConfigService configService) {
        super(SwingUtilities.getWindowAncestor(parent),
                isNew ? "Add New Scene" : "Edit Scene: " + (targetScene != null ? targetScene.getSceneId() : ""),
                Dialog.ModalityType.APPLICATION_MODAL);

        setSize(580, 640);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(new EmptyBorder(16, 20, 10, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 6, 6, 6);

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.2;
        formPanel.add(new JLabel("Scene ID:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.8;
        JTextField idField = new JTextField(isNew ? "S" + defaultSceneIndex : (targetScene != null ? targetScene.getSceneId() : "S1"));
        formPanel.add(idField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        formPanel.add(new JLabel("Visual Prompt:"), gbc);
        gbc.gridx = 1;
        JPanel promptContainer = new JPanel(new BorderLayout(0, 4));
        promptContainer.setOpaque(false);
        JTextArea promptArea = new JTextArea(targetScene != null && targetScene.getPrompt() != null ? targetScene.getPrompt() : "", 3, 20);
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        promptContainer.add(new JScrollPane(promptArea), BorderLayout.CENTER);

        JButton btnEnhanceMotion = new JButton("✨ Enhance Motion", SvgIconFactory.get(AppIcon.SUGGEST, 12));
        btnEnhanceMotion.setFont(new Font("SansSerif", Font.PLAIN, 11));
        btnEnhanceMotion.setToolTipText("Enriches the prompt with camera trajectory, kinetics, and environmental physics to prevent freezing.");
        btnEnhanceMotion.addActionListener(e -> {
            String current = promptArea.getText();
            String enhanced = promptOptimizer != null ? promptOptimizer.optimizeVideoPrompt(current) : current;
            promptArea.setText(enhanced);
        });
        promptContainer.add(btnEnhanceMotion, BorderLayout.SOUTH);
        formPanel.add(promptContainer, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        formPanel.add(new JLabel("Camera Motion:"), gbc);
        gbc.gridx = 1;
        javax.swing.JComboBox<String> cmbCameraMotion = new javax.swing.JComboBox<>(new String[]{
                "Auto / None",
                "Slow Push-In (Forward Dolly)",
                "Tracking Shot (Pan Left to Right)",
                "Tracking Shot (Pan Right to Left)",
                "Low-Angle Orbit (Dramatic 360)",
                "Drone Flyover (Overhead Wide Sweep)",
                "Crane Up (Ground to Sky Reveal)",
                "Steadicam Glide (Follow Motion)",
                "Static Hero Focus (Subtle Parallax)"
        });
        if (targetScene != null && targetScene.getCameraMotion() != null && !targetScene.getCameraMotion().isBlank()) {
            String cm = targetScene.getCameraMotion().toLowerCase();
            for (int i = 0; i < cmbCameraMotion.getItemCount(); i++) {
                if (cmbCameraMotion.getItemAt(i).toLowerCase().contains(cm) || cm.contains(cmbCameraMotion.getItemAt(i).toLowerCase())) {
                    cmbCameraMotion.setSelectedIndex(i);
                    break;
                }
            }
        }
        formPanel.add(cmbCameraMotion, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        formPanel.add(new JLabel("Start Keyframe Image:"), gbc);
        gbc.gridx = 1;
        JPanel keyframePanel = new JPanel(new BorderLayout(4, 0));
        keyframePanel.setOpaque(false);
        JTextField keyframeField = new JTextField(targetScene != null && targetScene.getSpeakerImagePath() != null ? targetScene.getSpeakerImagePath() : "");
        keyframePanel.add(keyframeField, BorderLayout.CENTER);

        JPanel keyframeBtnGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        keyframeBtnGroup.setOpaque(false);

        JButton btnGalleryKf = new JButton("✨ Gallery...", SvgIconFactory.get(AppIcon.GALLERY, 12));
        btnGalleryKf.setFont(new Font("SansSerif", Font.PLAIN, 11));
        btnGalleryKf.setToolTipText("Select image from recent ComfyUI outputs");
        btnGalleryKf.addActionListener(e -> {
            RecentGenerationsAssetPickerDialog dlg = new RecentGenerationsAssetPickerDialog(SceneEditDialog.this, configService, "Select Scene Start Keyframe");
            dlg.setVisible(true);
            File f = dlg.getSelectedFile();
            if (f != null) {
                keyframeField.setText(f.getAbsolutePath());
            }
        });
        keyframeBtnGroup.add(btnGalleryKf);

        JButton btnBrowseKf = new JButton("Browse...");
        btnBrowseKf.setFont(new Font("SansSerif", Font.PLAIN, 11));
        btnBrowseKf.addActionListener(e -> {
            javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
            chooser.setDialogTitle("Select Scene Start Keyframe Image");
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Images (*.png, *.jpg, *.jpeg, *.webp)", "png", "jpg", "jpeg", "webp"));
            if (chooser.showOpenDialog(SceneEditDialog.this) == javax.swing.JFileChooser.APPROVE_OPTION) {
                keyframeField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        keyframeBtnGroup.add(btnBrowseKf);

        JButton btnClearKf = new JButton(SvgIconFactory.get(AppIcon.CLOSE, 11));
        btnClearKf.setToolTipText("Clear Keyframe (Pure Text-to-Video)");
        btnClearKf.addActionListener(e -> keyframeField.setText(""));
        keyframeBtnGroup.add(btnClearKf);

        keyframePanel.add(keyframeBtnGroup, BorderLayout.EAST);
        formPanel.add(keyframePanel, gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        formPanel.add(new JLabel("Narration Text:"), gbc);
        gbc.gridx = 1;
        JTextArea narrationArea = new JTextArea(targetScene != null && targetScene.getNarrationText() != null ? targetScene.getNarrationText() : "", 2, 20);
        narrationArea.setLineWrap(true);
        narrationArea.setWrapStyleWord(true);
        formPanel.add(new JScrollPane(narrationArea), gbc);

        gbc.gridx = 0; gbc.gridy = 5;
        formPanel.add(new JLabel("SFX / Audio Prompt:"), gbc);
        gbc.gridx = 1;
        JPanel sfxContainer = new JPanel(new BorderLayout(0, 4));
        sfxContainer.setOpaque(false);
        JTextArea sfxPromptArea = new JTextArea(targetScene != null && targetScene.getSfxPrompt() != null ? targetScene.getSfxPrompt() : "", 2, 20);
        sfxPromptArea.setLineWrap(true);
        sfxPromptArea.setWrapStyleWord(true);
        sfxContainer.add(new JScrollPane(sfxPromptArea), BorderLayout.CENTER);

        JButton btnSuggestSfx = new JButton("✨ Suggest SFX", SvgIconFactory.get(AppIcon.SUGGEST, 12));
        btnSuggestSfx.setFont(new Font("SansSerif", Font.PLAIN, 11));
        btnSuggestSfx.setToolTipText("Infers matching sound effects and atmospheric audio cues from visual and narrative prompts.");
        btnSuggestSfx.addActionListener(e -> {
            if (promptOptimizer != null) {
                sfxPromptArea.setText(promptOptimizer.inferSfxPrompt(promptArea.getText(), narrationArea.getText()));
            }
        });
        sfxContainer.add(btnSuggestSfx, BorderLayout.SOUTH);
        formPanel.add(sfxContainer, gbc);

        gbc.gridx = 0; gbc.gridy = 6;
        formPanel.add(new JLabel("SFX Audio File:"), gbc);
        gbc.gridx = 1;
        JPanel sfxFilePanel = new JPanel(new BorderLayout(4, 0));
        sfxFilePanel.setOpaque(false);
        JTextField sfxFileField = new JTextField(targetScene != null && targetScene.getSfxAudioPath() != null ? targetScene.getSfxAudioPath() : "");
        sfxFilePanel.add(sfxFileField, BorderLayout.CENTER);
        JButton btnBrowseSfx = new JButton("Browse...");
        btnBrowseSfx.setFont(new Font("SansSerif", Font.PLAIN, 11));
        btnBrowseSfx.addActionListener(e -> {
            javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
            chooser.setDialogTitle("Select Sound Effect / Ambient Audio");
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Audio Files (*.wav, *.mp3, *.ogg, *.flac)", "wav", "mp3", "ogg", "flac"));
            if (chooser.showOpenDialog(SceneEditDialog.this) == javax.swing.JFileChooser.APPROVE_OPTION) {
                sfxFileField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        sfxFilePanel.add(btnBrowseSfx, BorderLayout.EAST);
        formPanel.add(sfxFilePanel, gbc);

        gbc.gridx = 0; gbc.gridy = 7;
        formPanel.add(new JLabel("Duration (sec):"), gbc);
        gbc.gridx = 1;
        int currentDur = 5;
        if (targetScene != null && targetScene.getEndFrame() > targetScene.getStartFrame()) {
            currentDur = Math.max(1, (targetScene.getEndFrame() - targetScene.getStartFrame()) / 24);
        }
        JSpinner durationSpinner = new JSpinner(new SpinnerNumberModel(currentDur, 1, 60, 1));
        formPanel.add(durationSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 8;
        formPanel.add(new JLabel("CFG Scale:"), gbc);
        gbc.gridx = 1;
        double cfg = targetScene != null ? targetScene.getCfgScale() : 3.5;
        JSpinner cfgSpinner = new JSpinner(new SpinnerNumberModel(cfg, 0.5, 20.0, 0.5));
        formPanel.add(cfgSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 9;
        formPanel.add(new JLabel("Steps:"), gbc);
        gbc.gridx = 1;
        int steps = targetScene != null ? targetScene.getSteps() : 28;
        JSpinner stepsSpinner = new JSpinner(new SpinnerNumberModel(steps, 10, 100, 1));
        formPanel.add(stepsSpinner, gbc);

        add(formPanel, BorderLayout.CENTER);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        btnRow.setBorder(new EmptyBorder(0, 0, 10, 20));
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        btnRow.add(cancelBtn);

        JButton saveBtn = new JButton(isNew ? "Add" : "Save");
        saveBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        saveBtn.putClientProperty("Button.background", ThemeManager.getAccentColor());
        saveBtn.putClientProperty("Button.foreground", Color.WHITE);
        saveBtn.addActionListener(e -> {
            Scene sc = isNew ? new Scene() : targetScene;
            String sid = idField.getText().trim().isEmpty() ? "S1" : idField.getText().trim();
            sc.setSceneId(sid);
            sc.setPrompt(promptArea.getText().trim());
            String kf = keyframeField.getText().trim();
            sc.setSpeakerImagePath(kf.isEmpty() ? null : kf);
            sc.setNarrationText(narrationArea.getText().trim());
            sc.setSfxPrompt(sfxPromptArea.getText().trim());
            String customSfx = sfxFileField.getText().trim();
            if (!customSfx.isEmpty()) {
                sc.setSfxAudioPath(customSfx);
            }
            int camIdx = cmbCameraMotion.getSelectedIndex();
            if (camIdx > 0) {
                sc.setCameraMotion((String) cmbCameraMotion.getSelectedItem());
            } else {
                sc.setCameraMotion(null);
            }
            int dur = (Integer) durationSpinner.getValue();
            sc.setStartFrame(0);
            sc.setEndFrame(dur * 24);
            sc.setCfgScale(((Number) cfgSpinner.getValue()).doubleValue());
            sc.setSteps((Integer) stepsSpinner.getValue());

            if (isNew) {
                sc.setWidth(defaultWidth);
                sc.setHeight(defaultHeight);
            }

            if (onSaved != null) {
                onSaved.accept(sc);
            }
            dispose();
        });
        btnRow.add(saveBtn);

        add(btnRow, BorderLayout.SOUTH);
    }
}
