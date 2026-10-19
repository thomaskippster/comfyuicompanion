package de.tki.comfyuicompanion.ui.dialog;

import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.StandardDialog;
import de.tki.comfyuicompanion.util.PlatformUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.concurrent.ExecutorService;

/**
 * Modal dialog for configuring application paths, including ComfyUI base directory,
 * Python executable, extra model paths, and cold storage archives.
 */
public final class PathsSettingsDialog {
    private static final Logger logger = LoggerFactory.getLogger(PathsSettingsDialog.class);

    private PathsSettingsDialog() {
        // Utility class
    }

    /**
     * Displays the directory and executable settings dialog.
     *
     * @param parent             the parent JFrame
     * @param configService      the configuration service to read and save paths
     * @param lifecycleService   the ComfyUI lifecycle service for automatic restart on path change
     * @param backgroundExecutor executor for background tasks
     * @param onSettingsUpdated  callback triggered after settings are saved
     * @param onSyncBridge       callback triggered to sync bridge files
     */
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
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
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
                File embeddedPy = PlatformUtils.isWindows()
                        ? new File(dir.getParentFile(), "python_embeded\\python.exe")
                        : new File(dir.getParentFile(), "python_embeded/bin/python3");
                if (embeddedPy.exists()) {
                    field4.setText(embeddedPy.getAbsolutePath());
                } else {
                    File venvPy = PlatformUtils.isWindows()
                            ? new File(dir, "venv\\Scripts\\python.exe")
                            : new File(dir, "venv/bin/python3");
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
}
