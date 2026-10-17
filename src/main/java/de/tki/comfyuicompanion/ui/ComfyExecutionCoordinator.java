package de.tki.comfyuicompanion.ui;

import de.tki.comfyuicompanion.controller.PromptLabController;
import de.tki.comfyuicompanion.domain.LaunchProfile;
import de.tki.comfyuicompanion.service.impl.ComfyProcessController;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.EnvironmentBootstrapperImpl;
import de.tki.comfyuicompanion.service.impl.ProfileManager;
import de.tki.comfyuicompanion.service.impl.UpdaterService;
import de.tki.comfyuicompanion.util.BackgroundExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Coordinates ComfyUI process execution, launch profile switching, CUDA error recovery,
 * environment repair workflows, and UI console log streaming.
 */
public class ComfyExecutionCoordinator {
    private static final Logger logger = LoggerFactory.getLogger(ComfyExecutionCoordinator.class);

    private final JFrame parentFrame;
    private final ComfyProcessController processController;
    private final ProfileManager profileManager;
    private final ConfigService configService;
    private final BackgroundExecutor backgroundExecutor;
    private final UpdaterService updaterService;
    private final EnvironmentBootstrapperImpl bootstrapper;
    private final PromptLabController promptLabController;
    private final Supplier<MainWindowBuilder.MainWindowComponents> componentsSupplier;

    /**
     * Constructs a new ComfyExecutionCoordinator with the required service and UI references.
     *
     * @param parentFrame        the parent JFrame for displaying modal alerts
     * @param processController  the controller managing process start/stop lifecycles
     * @param profileManager     the profile manager for launch profiles
     * @param configService      the configuration service for paths and toggles
     * @param backgroundExecutor background executor for asynchronous process management
     * @param updaterService     service for environment repair and git updates
     * @param bootstrapper       environment bootstrapper for WSL dependency fixing
     * @param promptLabController controller for prompt lab model refreshes
     * @param componentsSupplier supplier returning the current main window UI components
     */
    public ComfyExecutionCoordinator(JFrame parentFrame,
                                     ComfyProcessController processController,
                                     ProfileManager profileManager,
                                     ConfigService configService,
                                     BackgroundExecutor backgroundExecutor,
                                     UpdaterService updaterService,
                                     EnvironmentBootstrapperImpl bootstrapper,
                                     PromptLabController promptLabController,
                                     Supplier<MainWindowBuilder.MainWindowComponents> componentsSupplier) {
        this.parentFrame = parentFrame;
        this.processController = processController;
        this.profileManager = profileManager;
        this.configService = configService;
        this.backgroundExecutor = backgroundExecutor;
        this.updaterService = updaterService;
        this.bootstrapper = bootstrapper;
        this.promptLabController = promptLabController;
        this.componentsSupplier = componentsSupplier;
    }

    /**
     * Starts the ComfyUI process with the specified launch profile.
     *
     * @param selected     the selected launch profile
     * @param clearConsole whether to clear the UI console before launching
     * @param openBrowser  whether to automatically launch the default browser once ready
     */
    public void startComfyUI(LaunchProfile selected, boolean clearConsole, boolean openBrowser) {
        if (selected == null || processController == null) {
            return;
        }
        MainWindowBuilder.MainWindowComponents comps = componentsSupplier.get();
        if (clearConsole && comps != null && comps.dashboardPanelCoordinator != null && comps.dashboardPanelCoordinator.getComfyConsoleArea() != null) {
            comps.dashboardPanelCoordinator.getComfyConsoleArea().setText("");
        }
        AtomicBoolean cudaError = new AtomicBoolean(false);
        String comfyPath = configService != null ? configService.getComfyUIPath() : "";
        String pythonPath = configService != null ? configService.getPythonPath() : "";

        processController.start(selected, Paths.get(comfyPath), pythonPath, log -> {
            if (log.contains("Torch not compiled with CUDA enabled") || log.contains("AssertionError: Torch not compiled with CUDA enabled")) {
                if (!cudaError.getAndSet(true)) {
                    SwingUtilities.invokeLater(() -> {
                        int choice = JOptionPane.showConfirmDialog(parentFrame,
                                "ComfyUI / PyTorch reported a CUDA error:\n'Torch not compiled with CUDA enabled'\n\nWould you like to automatically reinstall PyTorch with CUDA support now?",
                                "CUDA PyTorch Error Detected", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);
                        if (choice == JOptionPane.YES_OPTION) {
                            triggerEnvironmentRepair();
                        } else if (choice == JOptionPane.NO_OPTION) {
                            switchToCpuModeProfile();
                        }
                    });
                }
            }
            if (log.contains("To see the GUI go to:")) {
                if (promptLabController != null) {
                    promptLabController.refreshPromptLabModels();
                }
                int idx = log.indexOf("http://");
                if (idx == -1) {
                    idx = log.indexOf("https://");
                }
                if (idx != -1) {
                    String url = log.substring(idx).trim();
                    if (backgroundExecutor != null) {
                        backgroundExecutor.execute(() -> {
                            try {
                                boolean shouldOpen = openBrowser && (configService == null || !configService.isHideComfyUI());
                                if (Desktop.isDesktopSupported() && shouldOpen) {
                                    Desktop.getDesktop().browse(new java.net.URI(url));
                                }
                            } catch (Exception ignored) {
                                logger.debug("Browser opening failed: {}", ignored.getMessage());
                            }
                        });
                    }
                }
            }
            appendToConsole(log);
        }).thenAccept(exitCode -> {
            if (exitCode != 0 && cudaError.get()) {
                SwingUtilities.invokeLater(() -> {
                    int choice = JOptionPane.showConfirmDialog(parentFrame,
                            "ComfyUI failed to start because CUDA is not enabled/supported on this PyTorch installation.\nWould you like to switch to 'CPU Mode' profile and start ComfyUI on your CPU?",
                            "CUDA Error Detected", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);
                    if (choice == JOptionPane.YES_OPTION) {
                        switchToCpuModeProfile();
                    }
                });
            }
        });
    }

    /**
     * Switches the active launch profile to CPU Mode and starts ComfyUI.
     */
    public void switchToCpuModeProfile() {
        if (profileManager == null) {
            return;
        }
        List<LaunchProfile> profiles = profileManager.loadProfiles();
        LaunchProfile cpuProfile = profiles.stream().filter(p -> p.id().equals("cpu_mode")).findFirst().orElse(null);
        if (cpuProfile != null) {
            MainWindowBuilder.MainWindowComponents comps = componentsSupplier.get();
            if (comps != null && comps.dashboardPanelCoordinator != null && comps.dashboardPanelCoordinator.getProfileList() != null) {
                comps.dashboardPanelCoordinator.getProfileList().setSelectedValue(cpuProfile, true);
            }
            startComfyUI(cpuProfile, true, configService == null || !configService.isHideComfyUI());
        }
    }

    /**
     * Performs a complete service restart by terminating any running ComfyUI process
     * and restarting it using the active launch profile.
     */
    public void performFullServiceRestart() {
        if (backgroundExecutor == null || processController == null) {
            return;
        }
        backgroundExecutor.execute(() -> {
            processController.stop();
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            SwingUtilities.invokeLater(() -> {
                MainWindowBuilder.MainWindowComponents comps = componentsSupplier.get();
                if (comps != null && comps.mainTabs != null) {
                    comps.mainTabs.setSelectedIndex(0);
                }
                LaunchProfile activeProfile = null;
                if (comps != null && comps.dashboardPanelCoordinator != null && comps.dashboardPanelCoordinator.getProfileList() != null) {
                    activeProfile = comps.dashboardPanelCoordinator.getProfileList().getSelectedValue();
                }
                if (activeProfile == null && configService != null && profileManager != null) {
                    String activeId = configService.getActiveProfile();
                    List<LaunchProfile> profiles = profileManager.loadProfiles();
                    activeProfile = profiles.stream().filter(p -> p.id().equals(activeId)).findFirst().orElse(null);
                    if (activeProfile == null && !profiles.isEmpty()) {
                        activeProfile = profiles.get(0);
                    }
                }
                if (activeProfile != null) {
                    if (comps != null && comps.dashboardPanelCoordinator != null) {
                        if (comps.dashboardPanelCoordinator.getProfileList() != null) {
                            comps.dashboardPanelCoordinator.getProfileList().setSelectedValue(activeProfile, true);
                        }
                        if (comps.dashboardPanelCoordinator.getComfyConsoleArea() != null) {
                            comps.dashboardPanelCoordinator.getComfyConsoleArea().append("\n🔄 Restarting ComfyUI...\n");
                        }
                    }
                    startComfyUI(activeProfile, false, configService == null || !configService.isHideComfyUI());
                }
            });
        });
    }

    /**
     * Appends a log line to the dashboard ComfyUI console area.
     *
     * @param log the log text to append
     */
    public void appendToConsole(String log) {
        SwingUtilities.invokeLater(() -> {
            MainWindowBuilder.MainWindowComponents comps = componentsSupplier.get();
            if (comps != null && comps.dashboardPanelCoordinator != null && comps.dashboardPanelCoordinator.getComfyConsoleArea() != null) {
                JTextArea area = comps.dashboardPanelCoordinator.getComfyConsoleArea();
                area.append(log.endsWith("\n") ? log : log + "\n");
                area.setCaretPosition(area.getDocument().getLength());
            }
        });
    }

    /**
     * Triggers the environment repair routine in a background thread.
     */
    public void triggerEnvironmentRepair() {
        if (updaterService == null || backgroundExecutor == null) {
            return;
        }
        MainWindowBuilder.MainWindowComponents comps = componentsSupplier.get();
        if (comps != null && comps.mainTabs != null) {
            comps.mainTabs.setSelectedIndex(0);
        }
        if (comps != null && comps.dashboardPanelCoordinator != null && comps.dashboardPanelCoordinator.getComfyConsoleArea() != null) {
            comps.dashboardPanelCoordinator.getComfyConsoleArea().setText("");
        }
        backgroundExecutor.execute(() -> updaterService.repairEnvironment(this::appendToConsole));
    }

    /**
     * Triggers the WSL dependency repair routine in a background thread.
     */
    public void triggerWslDependencyFix() {
        if (bootstrapper == null || backgroundExecutor == null) {
            return;
        }
        MainWindowBuilder.MainWindowComponents comps = componentsSupplier.get();
        if (comps != null && comps.mainTabs != null) {
            comps.mainTabs.setSelectedIndex(0);
        }
        if (comps != null && comps.dashboardPanelCoordinator != null && comps.dashboardPanelCoordinator.getComfyConsoleArea() != null) {
            comps.dashboardPanelCoordinator.getComfyConsoleArea().setText("");
        }
        String comfyPathStr = configService != null ? configService.getComfyUIPath() : null;
        Path comfyDir = (comfyPathStr != null && !comfyPathStr.isEmpty()) ? Paths.get(comfyPathStr) : null;
        backgroundExecutor.execute(() -> bootstrapper.fixWslDependencies(comfyDir, this::appendToConsole));
    }
}
