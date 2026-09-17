package de.tki.comfyuicompanion.ui.dialog;

import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.util.concurrent.ExecutorService;

/**
 * Modal dialog for inspecting ComfyUI process status and triggering
 * direct server start, stop, restart, or opening the web browser interface.
 */
public class ComfyLifecycleDialog {

    public static void showDialog(JFrame parent,
                                  ConfigService configService,
                                  IComfyLifecycleService lifecycleService,
                                  ExecutorService backgroundExecutor,
                                  Runnable startComfyAndReloadAction) {
        JDialog dialog = new JDialog(parent, "ComfyUI Control", true);
        dialog.setSize(880, 420);
        dialog.setLocationRelativeTo(parent);

        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0;

        JLabel titleLabel = new JLabel("ComfyUI Server Status");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        content.add(titleLabel, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(25, 0, 25, 0);
        JLabel lifecycleStatusLabel = new JLabel(lifecycleService != null ? lifecycleService.getStatus() : "Offline");
        lifecycleStatusLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        lifecycleStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        content.add(lifecycleStatusLabel, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(10, 0, 20, 0);
        JPanel controlButtons = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        JButton startBtn = new JButton("Start", SvgIconFactory.get(AppIcon.PLAY));
        startBtn.setPreferredSize(new Dimension(110, 40));
        startBtn.addActionListener(e -> {
            if (configService != null) configService.autoDiscoverPaths();
            if (startComfyAndReloadAction != null) startComfyAndReloadAction.run();
        });

        JButton stopBtn = new JButton("Stop", SvgIconFactory.get(AppIcon.STOP));
        stopBtn.setPreferredSize(new Dimension(110, 40));
        stopBtn.addActionListener(e -> {
            if (lifecycleService != null) lifecycleService.stop();
        });

        JButton restartBtn = new JButton("Restart", SvgIconFactory.get(AppIcon.RESTART));
        restartBtn.setPreferredSize(new Dimension(110, 40));
        restartBtn.addActionListener(e -> {
            if (configService != null) configService.autoDiscoverPaths();
            backgroundExecutor.execute(() -> {
                if (lifecycleService != null) lifecycleService.stop();
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                if (startComfyAndReloadAction != null) startComfyAndReloadAction.run();
            });
        });

        JButton openBrowserBtn = new JButton("Open Interface", SvgIconFactory.get(AppIcon.BROWSER));
        openBrowserBtn.setPreferredSize(new Dimension(150, 40));
        openBrowserBtn.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new URI(configService != null ? configService.getComfyUIUrl() : "http://127.0.0.1:8188"));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Could not open browser: " + ex.getMessage());
            }
        });

        controlButtons.add(startBtn);
        controlButtons.add(stopBtn);
        controlButtons.add(restartBtn);
        controlButtons.add(openBrowserBtn);
        content.add(controlButtons, gbc);

        Timer timer = new Timer(1000, e -> {
            if (lifecycleService != null) {
                lifecycleStatusLabel.setText(lifecycleService.getStatus());
                if (lifecycleService.isRunning()) {
                    lifecycleStatusLabel.setForeground(new Color(0, 150, 0));
                    startBtn.setEnabled(false);
                    stopBtn.setEnabled(true);
                } else {
                    lifecycleStatusLabel.setForeground(Color.RED);
                    startBtn.setEnabled(true);
                    stopBtn.setEnabled(false);
                }
            }
        });
        timer.start();
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { timer.stop(); }
        });

        gbc.gridy++;
        gbc.weighty = 1.0;
        content.add(new JPanel(), gbc);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        JButton close = new JButton("Close");
        close.addActionListener(e -> dialog.dispose());

        bottom.add(close);

        dialog.add(content, BorderLayout.CENTER);
        dialog.add(bottom, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }
}
