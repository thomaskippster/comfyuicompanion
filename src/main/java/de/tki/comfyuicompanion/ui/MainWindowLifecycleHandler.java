package de.tki.comfyuicompanion.ui;

import de.tki.comfyuicompanion.Main;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.IDownloadManager;
import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import de.tki.comfyuicompanion.service.impl.ComfyProcessController;
import de.tki.comfyuicompanion.service.impl.AppLifecycleManager;
import de.tki.comfyuicompanion.util.PlatformUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Handles the application lifecycle, including window closing events,
 * system tray setup, and shutdown hooks.
 */
public class MainWindowLifecycleHandler {
    private static final Logger logger = LoggerFactory.getLogger(MainWindowLifecycleHandler.class);

    private final Main mainWindow;
    private final ConfigService configService;
    private final IDownloadManager downloadManager;
    private final IComfyLifecycleService lifecycleService;
    private final ComfyProcessController processController;
    private final AppLifecycleManager appLifecycleManager;
    private final ConfigurableApplicationContext appContext;
    private final JCheckBox backgroundCheckMenuItem;

    /**
     * Constructs a new MainWindowLifecycleHandler.
     *
     * @param mainWindow the main application window
     * @param configService the configuration service
     * @param downloadManager the download manager service
     * @param lifecycleService the ComfyUI lifecycle service
     * @param processController the ComfyUI process controller
     * @param appLifecycleManager the application lifecycle manager
     * @param appContext the Spring application context
     * @param backgroundCheckMenuItem optional menu item for background check in tray
     */
    public MainWindowLifecycleHandler(Main mainWindow, ConfigService configService,
                                      IDownloadManager downloadManager, IComfyLifecycleService lifecycleService,
                                      ComfyProcessController processController, AppLifecycleManager appLifecycleManager,
                                      ConfigurableApplicationContext appContext, JCheckBox backgroundCheckMenuItem) {
        this.mainWindow = mainWindow;
        this.configService = configService;
        this.downloadManager = downloadManager;
        this.lifecycleService = lifecycleService;
        this.processController = processController;
        this.appLifecycleManager = appLifecycleManager;
        this.appContext = appContext;
        this.backgroundCheckMenuItem = backgroundCheckMenuItem;
    }

    /**
     * Registers application shutdown hooks.
     */
    public void registerShutdownHooks() {
        if (appLifecycleManager != null) {
            appLifecycleManager.registerShutdownHook(appContext);
        } else {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (processController != null) processController.stop();
                if (downloadManager != null) downloadManager.stop();
            }));
        }
    }

    /**
     * Sets up the main window listener for closing behavior.
     */
    public void setupWindowListener() {
        mainWindow.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (configService != null && configService.isBackgroundModeEnabled()) {
                    mainWindow.setVisible(false);
                } else {
                    performAppExit();
                }
            }
        });
    }

    /**
     * Sets up the system tray icon for the application.
     *
     * @param appIcon the application icon image
     */
    public void setupTrayIcon(Image appIcon) {
        MainSystemTrayHandler.setupTrayIcon(
                mainWindow,
                appIcon,
                configService,
                backgroundCheckMenuItem,
                this::performAppExit
        );
    }

    /**
     * Executes the application exit routine, stopping services gracefully.
     */
    public void performAppExit() {
        if (lifecycleService != null) lifecycleService.stop();
        if (downloadManager != null) downloadManager.stop();
        System.exit(0);
    }

    /**
     * Executes a system shutdown.
     */
    public void performSystemShutdown() {
        PlatformUtils.shutdownSystem();
    }
}
