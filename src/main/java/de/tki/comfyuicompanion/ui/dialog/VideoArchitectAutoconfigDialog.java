package de.tki.comfyuicompanion.ui.dialog;

import de.tki.comfyuicompanion.service.impl.DependencyService;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.ExecutorService;

/**
 * Modal progress dialog that installs the Video Architect dependencies (FFmpeg, etc.)
 * in the background while streaming progress messages into a text area.
 */
final class VideoArchitectAutoconfigDialog {

    private VideoArchitectAutoconfigDialog() {
    }

    /**
     * Displays the autoconfig dialog and starts the dependency installation asynchronously.
     *
     * @param parent             the parent frame
     * @param dependencyService  service performing the actual installation
     * @param backgroundExecutor executor on which the installation runs
     */
    static void show(JFrame parent,
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
}
