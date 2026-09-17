package de.tki.comfyuicompanion.ui.video;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.IVideoPromptOptimizer;
import de.tki.comfyuicompanion.ui.ThemeManager;
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
        super(SwingUtilities.getWindowAncestor(parent),
                isNew ? "Add New Scene" : "Edit Scene: " + (targetScene != null ? targetScene.getSceneId() : ""),
                Dialog.ModalityType.APPLICATION_MODAL);

        setSize(520, 480);
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
        formPanel.add(new JLabel("Narration Text:"), gbc);
        gbc.gridx = 1;
        JTextArea narrationArea = new JTextArea(targetScene != null && targetScene.getNarrationText() != null ? targetScene.getNarrationText() : "", 2, 20);
        narrationArea.setLineWrap(true);
        narrationArea.setWrapStyleWord(true);
        formPanel.add(new JScrollPane(narrationArea), gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        formPanel.add(new JLabel("Duration (sec):"), gbc);
        gbc.gridx = 1;
        int currentDur = 5;
        if (targetScene != null && targetScene.getEndFrame() > targetScene.getStartFrame()) {
            currentDur = Math.max(1, (targetScene.getEndFrame() - targetScene.getStartFrame()) / 24);
        }
        JSpinner durationSpinner = new JSpinner(new SpinnerNumberModel(currentDur, 1, 60, 1));
        formPanel.add(durationSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        formPanel.add(new JLabel("CFG Scale:"), gbc);
        gbc.gridx = 1;
        double cfg = targetScene != null ? targetScene.getCfgScale() : 1.0;
        JSpinner cfgSpinner = new JSpinner(new SpinnerNumberModel(cfg, 0.5, 20.0, 0.5));
        formPanel.add(cfgSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 5;
        formPanel.add(new JLabel("Steps:"), gbc);
        gbc.gridx = 1;
        int steps = targetScene != null ? targetScene.getSteps() : 30;
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
            sc.setNarrationText(narrationArea.getText().trim());
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
