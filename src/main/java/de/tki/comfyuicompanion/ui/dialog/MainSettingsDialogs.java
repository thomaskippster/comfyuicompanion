package de.tki.comfyuicompanion.ui.dialog;

import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.DependencyService;
import de.tki.comfyuicompanion.ui.StandardDialog;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Encapsulates modal dialogs for application settings, directory paths,
 * download limits, audio/TTS configuration, bridge installation, and help.
 */
public class MainSettingsDialogs {
    private static final Logger logger = LoggerFactory.getLogger(MainSettingsDialogs.class);

    /**
     * Displays the directory settings dialog by delegating to {@link PathsSettingsDialog}.
     *
     * @param parent             the parent JFrame
     * @param configService      the configuration service
     * @param lifecycleService   the ComfyUI lifecycle service
     * @param backgroundExecutor background executor service
     * @param onSettingsUpdated  callback on settings update
     * @param onSyncBridge       callback on sync bridge
     */
    public static void showPathsDialog(JFrame parent,
                                       ConfigService configService,
                                       IComfyLifecycleService lifecycleService,
                                       ExecutorService backgroundExecutor,
                                       Runnable onSettingsUpdated,
                                       Runnable onSyncBridge) {
        PathsSettingsDialog.showPathsDialog(parent, configService, lifecycleService, backgroundExecutor, onSettingsUpdated, onSyncBridge);
    }

    /**
     * Displays the download settings dialog.
     *
     * @param parent        the parent JFrame
     * @param configService the configuration service
     * @param onSaved       callback when settings are saved
     */
    public static void showDownloadSettingsDialog(JFrame parent, ConfigService configService, Runnable onSaved) {
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

    /**
     * Displays a background installation dialog for Video Architect dependencies (e.g. FFmpeg).
     *
     * @param parent             the parent JFrame
     * @param dependencyService  the dependency service to run dependency installation
     * @param backgroundExecutor executor for background tasks
     */
    public static void showVideoArchitectAutoconfigDialog(JFrame parent,
                                                          DependencyService dependencyService,
                                                          ExecutorService backgroundExecutor) {
        JDialog dialog = new JDialog(parent, "Video Architect Autoconfig", true);
        dialog.setSize(500, 350);
        dialog.setLocationRelativeTo(parent);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel header = new JLabel("Installing Video Architect Dependencies (FFmpeg, etc.)");
        header.setFont(new Font("SansSerif", Font.BOLD, 14));
        content.add(header, BorderLayout.NORTH);

        JTextArea progressArea = new JTextArea("Initializing...\n");
        progressArea.setEditable(false);
        JScrollPane scroll = new JScrollPane(progressArea);
        content.add(scroll, BorderLayout.CENTER);

        dialog.add(content);

        backgroundExecutor.execute(() -> {
            try {
                dependencyService.installFfmpeg(msg -> SwingUtilities.invokeLater(() -> {
                    progressArea.append(msg + "\n");
                    progressArea.setCaretPosition(progressArea.getDocument().getLength());
                }));

                SwingUtilities.invokeLater(() -> {
                    progressArea.append("Dependencies successfully configured!\n");
                    JOptionPane.showMessageDialog(dialog, "Dependency installation completed successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    progressArea.append("Error: " + ex.getMessage() + "\n");
                    JOptionPane.showMessageDialog(dialog, "Failed to install dependencies: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        });

        dialog.setVisible(true);
    }

    /**
     * Displays the centralized API keys, cloud tokens, and audio service configuration dialog
     * by delegating to {@link ApiKeysSettingsDialog}.
     *
     * @param parent        the parent JFrame
     * @param configService configuration service for reading and saving credentials
     * @param onSaved       callback executed when settings are successfully saved
     */
    public static void showApiKeysDialog(JFrame parent, ConfigService configService, Runnable onSaved) {
        ApiKeysSettingsDialog.showApiKeysDialog(parent, configService, onSaved);
    }

    /**
     * Displays the ComfyUI Bridge installation dialog.
     *
     * @param parent          the parent JFrame
     * @param configService   configuration service
     * @param onSyncBridge    callback to sync bridge tokens
     * @param onInstallBridge consumer to trigger bridge installation
     */
    public static void showInstallationDialog(JFrame parent,
                                              ConfigService configService,
                                              Runnable onSyncBridge,
                                              Consumer<JDialog> onInstallBridge) {
        JDialog dialog = new JDialog(parent, "ComfyUI Bridge Installation", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(650, 360);
        dialog.setLocationRelativeTo(parent);

        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 15, 0);

        JTextArea infoArea = new JTextArea(
                "This installer will set up the Companion for ComfyUI bridge.\n\n" +
                "1. Select your ComfyUI main directory.\n" +
                "2. Old or conflicting bridge files will be cleaned up.\n" +
                "3. A link or copy of the UI extension will be created.\n\n" +
                "Important: Restart ComfyUI after the installation is finished."
        );
        infoArea.setEditable(false);
        infoArea.setFocusable(false);
        infoArea.setBackground(content.getBackground());
        infoArea.setLineWrap(true);
        infoArea.setWrapStyleWord(true);
        content.add(infoArea, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        content.add(new JLabel("ComfyUI Main Directory:"), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        JPanel pathRow = new JPanel(new BorderLayout(5, 0));
        JTextField pathField = new JTextField(configService.getComfyUIPath());
        JButton browseBtn = new JButton("Browse...");
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                pathField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        pathRow.add(pathField, BorderLayout.CENTER);
        pathRow.add(browseBtn, BorderLayout.EAST);
        content.add(pathRow, gbc);

        gbc.gridy++;
        gbc.weighty = 1.0;
        content.add(new JPanel(), gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton syncBtn = new JButton("Repair / Sync Token", SvgIconFactory.get(AppIcon.RESTART));
        syncBtn.setToolTipText("Only updates the API token in your existing ComfyUI extension.");
        syncBtn.addActionListener(e -> {
            String selectedPath = pathField.getText().trim();
            if (selectedPath.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Please select a path first.");
                return;
            }
            configService.setComfyUIPath(selectedPath);
            if (onSyncBridge != null) onSyncBridge.run();
            JOptionPane.showMessageDialog(dialog, "API Token synchronized successfully.");
        });

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dialog.dispose());
        JButton installBtn = new JButton("Start Installation", SvgIconFactory.get(AppIcon.LAUNCH));
        installBtn.putClientProperty("JButton.buttonType", "accent");
        installBtn.addActionListener(e -> {
            String selectedPath = pathField.getText().trim();
            if (selectedPath.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Please select a path first.");
                return;
            }
            if (onInstallBridge != null) onInstallBridge.accept(dialog);
        });
        buttonPanel.add(syncBtn);
        buttonPanel.add(new JSeparator(JSeparator.VERTICAL));
        buttonPanel.add(cancelBtn);
        buttonPanel.add(installBtn);

        dialog.add(content, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    /**
     * Displays the user guide and documentation dialog.
     *
     * @param parent        the parent JFrame
     * @param configService configuration service to read theme mode
     */
    public static void showHelpDialog(JFrame parent, ConfigService configService) {
        HelpDialog.show(parent, configService);
    }
}
