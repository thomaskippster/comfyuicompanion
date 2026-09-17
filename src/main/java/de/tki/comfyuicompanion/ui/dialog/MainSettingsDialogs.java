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
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Encapsulates modal dialogs for application settings, directory paths,
 * download limits, audio/TTS configuration, bridge installation, and help.
 */
public class MainSettingsDialogs {
    private static final Logger logger = LoggerFactory.getLogger(MainSettingsDialogs.class);

    public static void showPathsDialog(JFrame parent,
                                       ConfigService configService,
                                       IComfyLifecycleService lifecycleService,
                                       ExecutorService backgroundExecutor,
                                       Runnable onSettingsUpdated,
                                       Runnable onSyncBridge) {
        StandardDialog dialog = new StandardDialog(parent, "Directory Settings");
        dialog.setSize(600, 480);
        dialog.setLocationRelativeTo(parent);

        JPanel panel = dialog.createContentPanel();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 5, 0);

        panel.add(new JLabel("Extra ComfyUI Path (contains models, input, output):"), gbc);

        gbc.gridy++;
        JPanel row1 = new JPanel(new BorderLayout(5, 0));
        JTextField field1 = new JTextField(configService.getExtraComfyUIPath());
        JButton browse1 = new JButton("Browse...");
        browse1.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                field1.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        row1.add(field1, BorderLayout.CENTER);
        row1.add(browse1, BorderLayout.EAST);
        panel.add(row1, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(10, 0, 5, 0);
        panel.add(new JLabel("Archive Path (Offload storage):"), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        JPanel row2 = new JPanel(new BorderLayout(5, 0));
        JTextField field2 = new JTextField(configService.getArchivePath());
        JButton browse2 = new JButton("Browse...");
        browse2.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                field2.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        row2.add(field2, BorderLayout.CENTER);
        row2.add(browse2, BorderLayout.EAST);
        panel.add(row2, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(10, 0, 5, 0);
        panel.add(new JLabel("ComfyUI Main Directory (contains main.py):"), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        JPanel row3 = new JPanel(new BorderLayout(5, 0));
        JTextField field3 = new JTextField(configService.getComfyUIPath());
        JButton browse3 = new JButton("Browse...");
        row3.add(field3, BorderLayout.CENTER);
        row3.add(browse3, BorderLayout.EAST);
        panel.add(row3, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(10, 0, 5, 0);
        panel.add(new JLabel("Python Executable Path:"), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        JPanel row4 = new JPanel(new BorderLayout(5, 0));
        JTextField field4 = new JTextField(configService.getPythonPath());
        JButton browse4 = new JButton("Browse...");
        row4.add(field4, BorderLayout.CENTER);
        row4.add(browse4, BorderLayout.EAST);
        panel.add(row4, gbc);

        browse3.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                File dir = chooser.getSelectedFile();
                field3.setText(dir.getAbsolutePath());
                File embeddedPy = new File(dir.getParentFile(), "python_embeded\\python.exe");
                if (embeddedPy.exists()) {
                    field4.setText(embeddedPy.getAbsolutePath());
                } else {
                    File venvPy = new File(dir, "venv\\Scripts\\python.exe");
                    if (venvPy.exists()) {
                        field4.setText(venvPy.getAbsolutePath());
                    }
                }
            }
        });

        browse4.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                field4.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });

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
            String oldExtraPath = configService.getExtraComfyUIPath();
            String oldComfyUIPath = configService.getComfyUIPath();

            String newExtraPath = field1.getText().trim();
            String newArchive = field2.getText().trim();
            String newComfyUIPath = field3.getText().trim();
            String newPythonPath = field4.getText().trim();

            configService.setExtraComfyUIPath(newExtraPath);
            configService.setArchivePath(newArchive);
            configService.setComfyUIPath(newComfyUIPath);
            configService.setPythonPath(newPythonPath);
            configService.autoDiscoverPaths();

            if (onSyncBridge != null) onSyncBridge.run();
            if (onSettingsUpdated != null) onSettingsUpdated.run();
            dialog.dispose();

            boolean pathsChanged = !newExtraPath.equalsIgnoreCase(oldExtraPath) || !newComfyUIPath.equalsIgnoreCase(oldComfyUIPath);
            if (pathsChanged && lifecycleService != null && lifecycleService.isHealthy()) {
                backgroundExecutor.execute(() -> {
                    logger.info("🔄 [Lifecycle] Custom paths updated. Restarting ComfyUI server to apply changes...");
                    lifecycleService.stop();
                    lifecycleService.start();
                });
            }
        });
        buttons.add(cancel);
        buttons.add(save);

        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    public static void showDownloadSettingsDialog(JFrame parent, ConfigService configService, Runnable onSaved) {
        StandardDialog dialog = new StandardDialog(parent, "Download Settings");
        dialog.setSize(600, 480);
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

    public static void showApiKeysDialog(JFrame parent, ConfigService configService, Runnable onSaved) {
        StandardDialog dialog = new StandardDialog(parent, "Audio & TTS Settings");
        dialog.setSize(600, 520);
        dialog.setLocationRelativeTo(parent);

        JPanel panel = dialog.createContentPanel();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0;

        panel.add(new JLabel("TTS Provider:"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(5, 0, 15, 0);
        JComboBox<String> ttsProviderCombo = new JComboBox<>(new String[]{"ComfyUI Qwen-TTS", "ComfyUI KokoroTTS", "ComfyUI ElevenLabs"});
        ttsProviderCombo.setSelectedItem(configService.getTtsProvider());
        panel.add(ttsProviderCombo, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(new JLabel("Qwen-TTS Voice (if using ComfyUI Qwen-TTS):"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(5, 0, 10, 0);
        String[] qwenVoices = new String[]{"Chelsie", "Ethan", "Serena", "Aiden", "Vivian"};
        JComboBox<String> qwenVoiceCombo = new JComboBox<>(qwenVoices);
        qwenVoiceCombo.setEditable(true);
        qwenVoiceCombo.setSelectedItem(configService.getQwenTtsVoice());
        panel.add(qwenVoiceCombo, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(new JLabel("Qwen-TTS Model Repo (Hugging Face id):"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(5, 0, 15, 0);
        JTextField qwenModelRepoField = new JTextField(configService.getQwenTtsModelRepo());
        panel.add(qwenModelRepoField, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 0, 0);
        JCheckBox qwenAutoSelectCheck = new JCheckBox("Auto-select best Qwen-TTS model for my hardware", configService.isQwenTtsModelAuto());
        panel.add(qwenAutoSelectCheck, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(5, 0, 15, 0);
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
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(new JLabel("Piper TTS Binary Path (if using Local Piper):"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(5, 0, 10, 0);
        JTextField piperPathField = new JTextField(configService.getPiperPath());
        panel.add(piperPathField, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(new JLabel("Piper TTS ONNX Model Path (if using Local Piper):"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(5, 0, 15, 0);
        JTextField piperModelField = new JTextField(configService.getPiperModelPath());
        panel.add(piperModelField, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(new JLabel("ElevenLabs Voice ID (if using ComfyUI ElevenLabs):"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(5, 0, 15, 0);
        JTextField elevenLabsVoiceField = new JTextField(configService.getElevenLabsVoiceId());
        panel.add(elevenLabsVoiceField, gbc);

        Runnable updateTtsVisibility = () -> {
            boolean isEleven = "ComfyUI ElevenLabs".equals(ttsProviderCombo.getSelectedItem());
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
            configService.setTtsProvider((String) ttsProviderCombo.getSelectedItem());
            configService.setPiperPath(piperPathField.getText().trim());
            configService.setPiperModelPath(piperModelField.getText().trim());
            configService.setQwenTtsVoice((String) qwenVoiceCombo.getSelectedItem());
            configService.setQwenTtsModelRepo(qwenModelRepoField.getText().trim());
            configService.setQwenTtsModelAuto(qwenAutoSelectCheck.isSelected());
            configService.setElevenLabsVoiceId(elevenLabsVoiceField.getText().trim());

            if (onSaved != null) onSaved.run();
            dialog.dispose();
        });
        buttons.add(cancel);
        buttons.add(save);

        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

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

    public static void showHelpDialog(JFrame parent, ConfigService configService) {
        JDialog helpDialog = new JDialog(parent, "User Guide & Documentation", true);
        helpDialog.setLayout(new BorderLayout());
        helpDialog.setSize(950, 750);
        helpDialog.setLocationRelativeTo(parent);

        JEditorPane editorPane = new JEditorPane();
        editorPane.setEditable(false);
        editorPane.setContentType("text/html");

        Color panelBg = UIManager.getColor("Panel.background");
        Color labelFg = UIManager.getColor("Label.foreground");

        boolean darkMode = configService != null && configService.isDarkMode();
        String bgColor = panelBg != null ? String.format("#%02x%02x%02x", panelBg.getRed(), panelBg.getGreen(), panelBg.getBlue()) : (darkMode ? "#121214" : "#f8fafc");
        String textColor = labelFg != null ? String.format("#%02x%02x%02x", labelFg.getRed(), labelFg.getGreen(), labelFg.getBlue()) : (darkMode ? "#e2e8f0" : "#1e293b");

        String accentColor = darkMode ? "#818cf8" : "#4f46e5";
        String boxBg = darkMode ? "#1a1a24" : "#ffffff";
        String boxBorder = darkMode ? "#2d2d3d" : "#e2e8f0";
        String tipBg = darkMode ? "#222230" : "#f1f5f9";

        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='padding: 25px; font-family: sans-serif; background-color: ").append(bgColor).append("; color: ").append(textColor).append(";'>");
        sb.append("<h1 style='color: ").append(accentColor).append("; text-align: center; margin-bottom: 25px;'>🚀 Companion for ComfyUI</h1>");

        sb.append("<div style='background-color: ").append(boxBg).append("; padding: 15px; border-radius: 8px; margin-bottom: 20px; border: 1px solid ").append(boxBorder).append(";'>");
        sb.append("<h2 style='color: ").append(accentColor).append("; margin-top: 0;'>🛠️ Getting Started (Setup)</h2>");
        sb.append("<ol>");
        sb.append("<li><b>Set Directories:</b> Navigate to <i>Settings -> Directories</i> and specify your ComfyUI root and 'models' paths. Choose a 'Cold Archive' path (e.g. on a high-capacity HDD) to offload unused models.</li>");
        sb.append("<li><b>Unlock Vault:</b> Establish a Master Password on first startup. This AES-256 encrypted vault securely stores your sensitive API keys.</li>");
        sb.append("<li><b>Configure Audio & TTS:</b> Go to <i>Settings -> Audio & TTS Settings</i> to configure text-to-speech output.</li>");
        sb.append("<li><b>Install Canvas Bridge:</b> Click <i>Settings -> ComfyUI Bridge...</i> to add the 🚀 floating button to your ComfyUI workspace for seamless 1-click canvas syncing.</li>");
        sb.append("</ol></div>");

        sb.append("<h2 style='color: ").append(accentColor).append(";'>✨ Core Features</h2>");
        sb.append("<p><b>📦 Archive Manager:</b> Reclaim SSD space. Move massive, rarely used models (SDXL checkpoints, Flux models, etc.) to your cold storage archive. The app automatically detects when a loaded workflow needs archived models and offers <b>one-click restoration</b>.</p>");
        sb.append("<p><b>🧼 Storage Optimizer:</b> Automatically scans model folders for duplicate files using SHA-256 and Fast AutoV1 Hashing. Clean up redundant copies or instantly merge duplicates into NTFS hardlinks to preserve folder structure while freeing up physical space.</p>");
        sb.append("<p><b>🎨 Prompt Lab & AI Optimization:</b> Draft and refine prompts, select styles, and pick optional environmental contexts (including 'None (Optional)'). Features a powerful <b>Optimize with AI</b> button which detects the target model architecture (SD 1.5, SDXL, Flux) and rewrites prompts to suit that target.</p>");
        sb.append("<p><b>🔍 Model Update Checker & Tracker:</b> Scans your local models and checks Civitai to see if newer versions or upgrades are available. Download and update your models with a single click.</p>");
        sb.append("<p><b>🖼️ Output Gallery:</b> Live monitoring of the ComfyUI output directory. Renders images and videos asynchronously with support for selection checkboxes, bulk deletion, and file actions without slowing down the UI.</p>");
        sb.append("<p><b>📊 Workflow Graph Preview:</b> Displays a visual diagram of the ComfyUI nodes and linkages directly in the companion app whenever a workflow is loaded or synced.</p>");
        sb.append("<p><b>⚡ System Monitoring & Server Lifecycle:</b> Keeps track of real-time CPU, RAM, and VRAM utilization. Automatically cleans up active ports to resolve server startup crashes, streams server logs, and refreshes the browser automatically upon reconnect.</p>");
        sb.append("<p><b>🔌 Automatic App & Node Updater:</b> Keeps ComfyUI and installed Custom Nodes up-to-date with 1-click updates that run `git pull` and set up virtual environments automatically.</p>");

        sb.append("<div style='background-color: ").append(tipBg).append("; padding: 15px; border-radius: 8px; margin-top: 20px; border: 1px solid ").append(boxBorder).append(";'>");
        sb.append("<b>💡 Pro Tips:</b><br>");
        sb.append("• <b>Drag & Drop:</b> Drag any workflow JSON or generated image (.png with metadata) directly into the app window to import its model list.<br>");
        sb.append("• <b>Overnight Queue:</b> Enable 'Shutdown after Queue' in the downloader to automatically turn off the computer when downloads are done.<br>");
        sb.append("• <b>System Tray:</b> Enable 'Stay in Background' so the app minimizes to the system tray instead of closing.<br>");
        sb.append("• <b>Launch Profiles:</b> Select from 10 optimized launch arguments (like CPU Mode, Low VRAM, Beast Mode, or FP8 Flux optimizations) matching your hardware.");
        sb.append("</div>");

        sb.append("<p style='margin-top: 30px; color: #888; font-style: italic; text-align: center;'>Version 1.1.0 - Open Source Software developed by TKI</p>");
        sb.append("</body></html>");

        editorPane.setText(sb.toString());
        editorPane.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(editorPane,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);
        helpDialog.add(scrollPane, BorderLayout.CENTER);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> helpDialog.dispose());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.add(closeBtn);
        helpDialog.add(btnPanel, BorderLayout.SOUTH);

        helpDialog.setVisible(true);
    }
}
