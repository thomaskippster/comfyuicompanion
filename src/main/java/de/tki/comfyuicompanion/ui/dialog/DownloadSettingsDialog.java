package de.tki.comfyuicompanion.ui.dialog;

import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.StandardDialog;

import javax.swing.*;
import java.awt.*;

/**
 * Modal dialog for editing download behaviour (parallelism, speed limit, segments)
 * and the download-related access tokens (Civitai, Hugging Face).
 */
final class DownloadSettingsDialog {

    private DownloadSettingsDialog() {
    }

    /**
     * Displays the download settings dialog.
     *
     * @param parent        the parent frame
     * @param configService configuration service used to read and persist the settings
     * @param onSaved       callback executed after a successful save (may be null)
     */
    static void show(JFrame parent, ConfigService configService, Runnable onSaved) {
        StandardDialog dialog = new StandardDialog(parent, "Download Settings");
        dialog.setSize(600, 560);
        dialog.setLocationRelativeTo(parent);

        JPanel panel = dialog.createContentPanel();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 5, 0);

        panel.add(new JLabel("Max Parallel Downloads (Active downloads in queue):"), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 15, 0);
        SpinnerNumberModel threadModel = new SpinnerNumberModel(configService.getMaxParallelDownloads(), 1, 10, 1);
        JSpinner threadSpinner = new JSpinner(threadModel);
        panel.add(threadSpinner, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        panel.add(new JLabel("Download Speed Limit (KB/s - 0 for unlimited):"), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 15, 0);
        JTextField speedField = new JTextField(String.valueOf(configService.getDownloadSpeedLimit()));
        panel.add(speedField, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        panel.add(new JLabel("Segments Per File (Multi-segment downloading, 1 to disable):"), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 15, 0);
        SpinnerNumberModel segmentModel = new SpinnerNumberModel(configService.getSegmentsPerFile(), 1, 8, 1);
        JSpinner segmentSpinner = new JSpinner(segmentModel);
        panel.add(segmentSpinner, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        panel.add(new JLabel("Civitai API Key (for gated models, member access & high-speed downloads):"), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        JTextField civitaiApiKeyField = new JTextField(configService.getCivitaiApiKey());
        panel.add(civitaiApiKeyField, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 15, 0);
        JLabel civitaiHint = new JLabel("<html><i>Get your key on civitai.com -> Account Settings -> API Keys. Stored securely in AES-256 vault.</i></html>");
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
        JLabel hfHint = new JLabel("<html><i>Requires accepting terms on huggingface.co/Lightricks/LTX-2.5 prior to download.</i></html>");
        hfHint.setFont(new Font("SansSerif", Font.PLAIN, 11));
        hfHint.setForeground(Color.GRAY);
        panel.add(hfHint, gbc);

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
            try {
                int threads = (Integer) threadSpinner.getValue();
                int segments = (Integer) segmentSpinner.getValue();
                int speedLimit = Integer.parseInt(speedField.getText().trim());
                if (speedLimit < 0) {
                    throw new NumberFormatException();
                }

                configService.setMaxParallelDownloads(threads);
                configService.setDownloadSpeedLimit(speedLimit);
                configService.setSegmentsPerFile(segments);
                configService.setCivitaiApiKey(civitaiApiKeyField.getText().trim());
                configService.setHfToken(hfTokenField.getText().trim());

                if (onSaved != null) onSaved.run();
                dialog.dispose();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "Please enter a valid positive integer for the speed limit.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
            }
        });
        buttons.add(cancel);
        buttons.add(save);

        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }
}
