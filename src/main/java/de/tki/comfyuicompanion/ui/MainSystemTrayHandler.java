package de.tki.comfyuicompanion.ui;

import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.util.PlatformUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Manages system tray integration, tray context menus, and background minimization.
 */
public class MainSystemTrayHandler {
    private static final Logger logger = LoggerFactory.getLogger(MainSystemTrayHandler.class);

    public static void setupTrayIcon(JFrame frame,
                                     Image appIcon,
                                     ConfigService configService,
                                     JCheckBox backgroundCheck,
                                     Runnable onExit) {
        if (!PlatformUtils.isSystemTraySupported()) {
            logger.error("[System-Tray] Not supported on this platform (e.g. Wayland). Background mode disabled.");
            if (backgroundCheck != null) {
                backgroundCheck.setSelected(false);
                backgroundCheck.setEnabled(false);
                backgroundCheck.setToolTipText("System Tray not supported on this OS.");
                configService.setBackgroundModeEnabled(false);
            }
            return;
        }

        SystemTray tray = SystemTray.getSystemTray();
        Image trayImage = appIcon;

        if (trayImage == null) {
            BufferedImage fallback = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = fallback.createGraphics();
            g2.setColor(new Color(255, 204, 0));
            g2.fillRect(0, 0, 16, 16);
            g2.dispose();
            trayImage = fallback;
        }

        PopupMenu popup = new PopupMenu();
        MenuItem showItem = new MenuItem("Show UI");
        showItem.addActionListener(e -> frame.setVisible(true));
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> {
            if (onExit != null) onExit.run();
        });

        popup.add(showItem);
        popup.addSeparator();
        popup.add(exitItem);

        TrayIcon trayIcon = new TrayIcon(trayImage, "Companion for ComfyUI", popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> frame.setVisible(true));

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            logger.error("TrayIcon could not be added.");
        }
    }
}
