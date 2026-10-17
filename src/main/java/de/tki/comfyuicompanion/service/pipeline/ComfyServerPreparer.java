package de.tki.comfyuicompanion.service.pipeline;

import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.EnvironmentBootstrapperImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ensures the ComfyUI server is running and that required custom nodes
 * (ComfyUI-Video-Helper-Suite) are installed before a scene generation job is submitted.
 */
public class ComfyServerPreparer {

    private static final Logger logger = LoggerFactory.getLogger(ComfyServerPreparer.class);
    private static final Set<String> ATTEMPTED_INSTALLS = ConcurrentHashMap.newKeySet();
    private static final int MAX_WAIT_SECONDS = 90;

    private final ConfigService configService;
    private final ComfyNodeInspectionClient inspectionClient;
    private final EnvironmentBootstrapperImpl bootstrapper;

    /**
     * Creates a preparer.
     *
     * @param configService    configuration providing ComfyUI and Python paths
     * @param inspectionClient client used to check node availability
     * @param bootstrapper     optional bootstrapper used to install custom nodes
     */
    public ComfyServerPreparer(ConfigService configService, ComfyNodeInspectionClient inspectionClient,
                               EnvironmentBootstrapperImpl bootstrapper) {
        this.configService = configService;
        this.inspectionClient = inspectionClient;
        this.bootstrapper = bootstrapper;
    }

    /**
     * Starts the ComfyUI server if it is offline and waits until it reports healthy.
     *
     * @param lifecycle the lifecycle service; no-op if {@code null}
     * @throws RuntimeException if the server does not become healthy in time or the wait is interrupted
     */
    public void ensureComfyUiStarted(IComfyLifecycleService lifecycle) {
        if (lifecycle != null && !lifecycle.isHealthy()) {
            logger.info("🔄 [ScenePipelineRunner] ComfyUI server is offline. Attempting auto-start...");
            lifecycle.start();
            awaitHealthy(lifecycle, "Auto-start interrupted", "Failed to auto-start ComfyUI within " + MAX_WAIT_SECONDS + " seconds.");
            logger.info("✅ [ScenePipelineRunner] ComfyUI successfully started and healthy.");
        }
    }

    /**
     * Installs or activates the Video Helper Suite custom nodes if {@code VHS_VideoCombine} is unavailable.
     * Installation is attempted at most once per JVM.
     *
     * @param serverUrl the ComfyUI server address
     * @param lifecycle the lifecycle service; no-op if {@code null} or unhealthy
     */
    public void ensureVideoHelperSuite(String serverUrl, IComfyLifecycleService lifecycle) {
        if (lifecycle == null || !lifecycle.isHealthy() || bootstrapper == null) {
            return;
        }
        String comfyPath = configService.getComfyUIPath();
        boolean folderExists = false;
        if (comfyPath != null && !comfyPath.trim().isEmpty()) {
            File videoHelperDir = new File(new File(comfyPath, "custom_nodes"), "ComfyUI-Video-Helper-Suite");
            if (videoHelperDir.exists() && videoHelperDir.isDirectory()) {
                folderExists = true;
            }
        }

        boolean nodeAvailable = inspectionClient.isNodeClassAvailable(serverUrl, "VHS_VideoCombine");
        if (nodeAvailable) {
            return;
        }
        if (ATTEMPTED_INSTALLS.contains("VHS_VideoCombine")) {
            logger.info("⚠️ [ScenePipelineRunner] ComfyUI-Video-Helper-Suite installation already attempted. Skipping.");
            return;
        }
        ATTEMPTED_INSTALLS.add("VHS_VideoCombine");
        if (!folderExists) {
            logger.info("⚠️ [ScenePipelineRunner] VHS_VideoCombine is missing. Installing ComfyUI-Video-Helper-Suite...");
            lifecycle.stop();

            String pythonPath = configService.getPythonPath();
            if (comfyPath != null && !comfyPath.trim().isEmpty()) {
                Path comfyDir = Paths.get(comfyPath);
                Path pythonExe = (pythonPath != null && !pythonPath.trim().isEmpty()) ? Paths.get(pythonPath) : null;
                bootstrapper.ensureVideoHelperSuiteInstalled(comfyDir, pythonExe, msg -> logger.info("[Bootstrap] {}", msg));
            }
            lifecycle.start();
        } else {
            logger.info("🔄 [ScenePipelineRunner] VHS_VideoCombine not active but folder exists. Restarting ComfyUI...");
            lifecycle.restart();
        }

        awaitHealthy(lifecycle, "Restart interrupted", "Failed to restart ComfyUI within " + MAX_WAIT_SECONDS + " seconds.");
        logger.info("✅ [ScenePipelineRunner] ComfyUI successfully restarted and healthy.");
    }

    private void awaitHealthy(IComfyLifecycleService lifecycle, String interruptedMessage, String timeoutMessage) {
        for (int i = 0; i < MAX_WAIT_SECONDS; i++) {
            if (lifecycle.isHealthy()) {
                return;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(interruptedMessage, ie);
            }
        }
        throw new RuntimeException(timeoutMessage);
    }
}
