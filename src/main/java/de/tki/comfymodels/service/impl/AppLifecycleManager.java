package de.tki.comfymodels.service.impl;

import de.tki.comfymodels.service.IDownloadManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

import java.net.InetAddress;

/**
 * Service managing application lifecycle events, shutdown hooks, and vault unlock routines.
 */
@Service
public class AppLifecycleManager {

    private static final Logger logger = LoggerFactory.getLogger(AppLifecycleManager.class);

    private final ConfigService configService;
    private final ModelListService modelListService;
    private final ComfyProcessController processController;
    private final IDownloadManager downloadManager;
    private final HardwareMonitorService hardwareMonitorService;
    private final ProcessTracker processTracker;

    @Autowired
    public AppLifecycleManager(ConfigService configService,
                               ModelListService modelListService,
                               @Autowired(required = false) ComfyProcessController processController,
                               @Autowired(required = false) IDownloadManager downloadManager,
                               @Autowired(required = false) HardwareMonitorService hardwareMonitorService,
                               @Autowired(required = false) ProcessTracker processTracker) {
        this.configService = configService;
        this.modelListService = modelListService;
        this.processController = processController;
        this.downloadManager = downloadManager;
        this.hardwareMonitorService = hardwareMonitorService;
        this.processTracker = processTracker;
    }

    public boolean unlockDefaultVault() {
        String defaultPass = System.getProperty("user.name", "default") + "@" + getHostName();
        try {
            configService.unlock(defaultPass);
            if (configService.isVaultFresh()) {
                String url = "https://raw.githubusercontent.com/Comfy-Org/ComfyUI-Manager/main/model-list.json";
                modelListService.importFromUrl(url);
            }
            return true;
        } catch (Exception e) {
            logger.warn("Could not unlock vault with default password. Resetting vault to start clean...");
            try {
                configService.resetVault();
                configService.unlock(defaultPass);
                if (configService.isVaultFresh()) {
                    String url = "https://raw.githubusercontent.com/Comfy-Org/ComfyUI-Manager/main/model-list.json";
                    modelListService.importFromUrl(url);
                }
                return true;
            } catch (Exception ex) {
                logger.error("Failed to reset and unlock vault: {}", ex.getMessage(), ex);
                return false;
            }
        }
    }

    public void registerShutdownHook(ConfigurableApplicationContext appContext) {
        Thread shutdownHook = new Thread(() -> {
            try {
                logger.info("[LifecycleManager] Application shutdown initiated.");
                if (processController != null) {
                    processController.stop();
                }
                if (downloadManager != null) {
                    downloadManager.stop();
                }
                if (hardwareMonitorService != null) {
                    hardwareMonitorService.stop();
                }
                if (processTracker != null) {
                    processTracker.destroyAll();
                }

                Thread wslShutdown = Thread.ofVirtual().name("wsl-shutdown").start(() -> {
                    try {
                        Runtime.getRuntime().exec(new String[]{"wsl", "--shutdown"});
                    } catch (Exception e) {
                        logger.error("Failed to execute wsl --shutdown: {}", e.getMessage());
                    }
                });

                if (appContext != null) {
                    appContext.close();
                }
            } catch (Throwable t) {
                logger.error("Shutdown hook error: {}", t.getMessage(), t);
            }
        }, "comfy-shutdown-hook");
        shutdownHook.setDaemon(true);
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    public void performAppExit(ConfigurableApplicationContext appContext) {
        logger.info("[LifecycleManager] Exiting application...");
        if (processController != null) processController.stop();
        if (downloadManager != null) downloadManager.stop();
        if (hardwareMonitorService != null) hardwareMonitorService.stop();
        if (processTracker != null) processTracker.destroyAll();
        if (appContext != null) appContext.close();
        System.exit(0);
    }

    private String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "localhost";
        }
    }
}
