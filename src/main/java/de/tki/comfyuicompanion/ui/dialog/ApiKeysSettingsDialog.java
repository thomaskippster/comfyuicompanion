package de.tki.comfyuicompanion.ui.dialog;

import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.StandardDialog;

import javax.swing.*;
import java.awt.*;

/**
 * Modal dialog for configuring external API credentials (Civitai, Hugging Face, ElevenLabs)
 * as well as Text-To-Speech (TTS) engine options.
 */
public final class ApiKeysSettingsDialog {

    private ApiKeysSettingsDialog() {
        // Utility class
    }

    /**
     * Displays the centralized API keys, cloud tokens, and audio service configuration dialog.
     *
     * @param parent        the parent JFrame
     * @param configService configuration service for reading and saving credentials
     * @param onSaved       callback executed when settings are successfully saved
     */
    public static void showApiKeysDialog(JFrame parent, ConfigService configService, Runnable onSaved) {
        StandardDialog dialog = new StandardDialog(parent, "API Keys & Services");
        dialog.setSize(640, 720);
        dialog.setLocationRelativeTo(parent);

        JPanel panel = dialog.createContentPanel();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;

        // --- Model Hub & Repository Credentials ---
        JLabel hubHeader = new JLabel("Model Hub & Repository Credentials");
        hubHeader.setFont(new Font("SansSerif", Font.BOLD, 13));
        panel.add(hubHeader, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(10, 0, 5, 0);
        panel.add(new JLabel("Civitai API Key (for gated models, member access & high-speed downloads):"), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        JTextField civitaiApiKeyField = new JTextField(configService.getCivitaiApiKey());
        panel.add(civitaiApiKeyField, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 15, 0);
        JLabel civitaiHint = new JLabel("<html><i>Get your key on civitai.com -> Account Settings -> API Keys. Stored in AES-256 encrypted vault.</i></html>");
        civitaiHint.setFont(new Font("SansSerif", Font.PLAIN, 11));
        civitaiHint.setForeground(Color.GRAY);
        panel.add(civitaiHint, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        panel.add(new JLabel("Hugging Face Access Token (hf_... for gated models like LTX-2.5, Flux):"), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        JTextField hfTokenField = new JTextField(configService.getHfToken());
        panel.add(hfTokenField, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 15, 0);
        JLabel hfHint = new JLabel("<html><i>Requires accepting terms on huggingface.co prior to downloading gated repositories.</i></html>");
        hfHint.setFont(new Font("SansSerif", Font.PLAIN, 11));
        hfHint.setForeground(Color.GRAY);
        panel.add(hfHint, gbc);

        // --- Separator ---
        gbc.gridy++;
        gbc.insets = new Insets(5, 0, 15, 0);
        panel.add(new JSeparator(), gbc);

        // --- Audio & TTS Services ---
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        JLabel ttsHeader = new JLabel("Audio & Text-to-Speech (TTS)");
        ttsHeader.setFont(new Font("SansSerif", Font.BOLD, 13));
        panel.add(ttsHeader, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        panel.add(new JLabel("TTS Provider:"), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 12, 0);
        JComboBox<String> ttsProviderCombo = new JComboBox<>(new String[]{"ComfyUI Qwen-TTS", "ComfyUI KokoroTTS", "ComfyUI ElevenLabs"});
        ttsProviderCombo.setSelectedItem(configService.getTtsProvider());
        panel.add(ttsProviderCombo, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        panel.add(new JLabel("ElevenLabs API Key (if using ComfyUI ElevenLabs):"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        JTextField elevenLabsApiKeyField = new JTextField(configService.getElevenLabsApiKey());
        panel.add(elevenLabsApiKeyField, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        panel.add(new JLabel("ElevenLabs Voice ID (if using ComfyUI ElevenLabs):"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 12, 0);
        JTextField elevenLabsVoiceField = new JTextField(configService.getElevenLabsVoiceId());
        panel.add(elevenLabsVoiceField, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        panel.add(new JLabel("Qwen-TTS Voice (if using ComfyUI Qwen-TTS):"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        String[] qwenVoices = new String[]{"Chelsie", "Ethan", "Serena", "Aiden", "Vivian"};
        JComboBox<String> qwenVoiceCombo = new JComboBox<>(qwenVoices);
        qwenVoiceCombo.setEditable(true);
        qwenVoiceCombo.setSelectedItem(configService.getQwenTtsVoice());
        panel.add(qwenVoiceCombo, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        panel.add(new JLabel("Qwen-TTS Model Repo (Hugging Face id):"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        JTextField qwenModelRepoField = new JTextField(configService.getQwenTtsModelRepo());
        panel.add(qwenModelRepoField, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        JCheckBox qwenAutoSelectCheck = new JCheckBox("Auto-select best Qwen-TTS model for my hardware", configService.isQwenTtsModelAuto());
        panel.add(qwenAutoSelectCheck, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(5, 0, 12, 0);
        JPanel qwenAutoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        qwenAutoRow.setOpaque(false);
        JButton detectButton = new JButton("Detect VRAM & recommend model");
        JLabel qwenAutoStatus = new JLabel(" ");
        qwenAutoStatus.setFont(qwenAutoStatus.getFont().deriveFont(Font.PLAIN, 11f));
        qwenAutoRow.add(detectButton);
        qwenAutoRow.add(Box.createHorizontalStrut(10));
        qwenAutoRow.add(qwenAutoStatus);
        panel.add(qwenAutoRow, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        panel.add(new JLabel("Piper TTS Binary Path (if using Local Piper):"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        JTextField piperPathField = new JTextField(configService.getPiperPath());
        panel.add(piperPathField, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        panel.add(new JLabel("Piper TTS ONNX Model Path (if using Local Piper):"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 15, 0);
        JTextField piperModelField = new JTextField(configService.getPiperModelPath());
        panel.add(piperModelField, gbc);

        Runnable updateTtsVisibility = () -> {
            boolean isEleven = "ComfyUI ElevenLabs".equals(ttsProviderCombo.getSelectedItem());
            elevenLabsApiKeyField.setEnabled(isEleven);
            elevenLabsVoiceField.setEnabled(isEleven);

            boolean isPiper = false;
            piperPathField.setEnabled(isPiper);
            piperModelField.setEnabled(isPiper);
            boolean isQwen = "ComfyUI Qwen-TTS".equals(ttsProviderCombo.getSelectedItem());
            qwenVoiceCombo.setEnabled(isQwen);
            qwenModelRepoField.setEnabled(isQwen);
        };
        ttsProviderCombo.addActionListener(e -> updateTtsVisibility.run());
        updateTtsVisibility.run();

        gbc.gridy++;
        gbc.weighty = 1.0;
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        panel.add(spacer, gbc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dialog.dispose());
        JButton save = new JButton("Save");
        save.putClientProperty("JButton.buttonType", "accent");
        save.addActionListener(e -> {
            configService.setCivitaiApiKey(civitaiApiKeyField.getText().trim());
            configService.setHfToken(hfTokenField.getText().trim());
            configService.setElevenLabsApiKey(elevenLabsApiKeyField.getText().trim());
            configService.setElevenLabsVoiceId(elevenLabsVoiceField.getText().trim());
            configService.setTtsProvider((String) ttsProviderCombo.getSelectedItem());
            configService.setPiperPath(piperPathField.getText().trim());
            configService.setPiperModelPath(piperModelField.getText().trim());
            configService.setQwenTtsVoice((String) qwenVoiceCombo.getSelectedItem());
            configService.setQwenTtsModelRepo(qwenModelRepoField.getText().trim());
            configService.setQwenTtsModelAuto(qwenAutoSelectCheck.isSelected());

            if (onSaved != null) onSaved.run();
            dialog.dispose();
        });
        buttons.add(cancel);
        buttons.add(save);

        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }
}
