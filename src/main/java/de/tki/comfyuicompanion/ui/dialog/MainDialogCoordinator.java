package de.tki.comfyuicompanion.ui.dialog;

import de.tki.comfyuicompanion.service.IComfyLifecycleService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.DependencyService;

import javax.swing.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Coordinates and displays application dialogs (paths, API keys, lifecycle, help, installation).
 * Decoupled from {@link de.tki.comfyuicompanion.Main} to maintain single responsibility and class size constraints.
 */
public class MainDialogCoordinator {

    private final JFrame parentFrame;
    private final ConfigService configService;
    private final IComfyLifecycleService lifecycleService;
    private final DependencyService dependencyService;
    private final ExecutorService backgroundExecutor;

    /**
     * Constructs a new dialog coordinator with required service dependencies.
     *
     * @param parentFrame        the parent application frame for dialog modality
     * @param configService      configuration service instance
     * @param lifecycleService   ComfyUI process lifecycle service
     * @param dependencyService  system and Python dependencies service
     * @param backgroundExecutor executor service for background dialog tasks
     */
    public MainDialogCoordinator(JFrame parentFrame,
                                 ConfigService configService,
                                 IComfyLifecycleService lifecycleService,
                                 DependencyService dependencyService,
                                 ExecutorService backgroundExecutor) {
        this.parentFrame = parentFrame;
        this.configService = configService;
        this.lifecycleService = lifecycleService;
        this.dependencyService = dependencyService;
        this.backgroundExecutor = backgroundExecutor;
    }

    /**
     * Displays the ComfyUI lifecycle status and management dialog.
     *
     * @param onStartComfyAndReload callback executed when ComfyUI needs to start and reload
     */
    public void showLifecycleDialog(Runnable onStartComfyAndReload) {
        ComfyLifecycleDialog.showDialog(parentFrame, configService, lifecycleService,
                backgroundExecutor, onStartComfyAndReload);
    }

    /**
     * Displays the directory path configuration dialog.
     *
     * @param onRefreshVersions callback executed to refresh detected component versions
     * @param onSyncBridge      callback executed to sync bridge extension scripts
     */
    public void showPathsDialog(Runnable onRefreshVersions, Runnable onSyncBridge) {
        MainSettingsDialogs.showPathsDialog(parentFrame, configService, lifecycleService,
                backgroundExecutor, onRefreshVersions, onSyncBridge);
    }

    /**
     * Displays the model download rate and concurrency settings dialog.
     */
    public void showDownloadSettingsDialog() {
        MainSettingsDialogs.showDownloadSettingsDialog(parentFrame, configService, null);
    }

    /**
     * Displays the Video Architect auto-configuration dialog for FFmpeg and TTS tools.
     */
    public void showVideoArchitectAutoconfigDialog() {
        MainSettingsDialogs.showVideoArchitectAutoconfigDialog(parentFrame, dependencyService, backgroundExecutor);
    }

    /**
     * Displays the API keys and cloud service credentials configuration dialog.
     *
     * @param onUpdateAiModelDisplay callback executed to refresh the active AI model badge
     */
    public void showApiKeysDialog(Runnable onUpdateAiModelDisplay) {
        MainSettingsDialogs.showApiKeysDialog(parentFrame, configService, onUpdateAiModelDisplay);
    }

    /**
     * Displays the initial installation and extension bridge installer dialog.
     *
     * @param onSyncBridge    callback executed to synchronize bridge files
     * @param onInstallBridge callback executed to install the bridge into ComfyUI
     */
    public void showInstallationDialog(Runnable onSyncBridge, Consumer<JDialog> onInstallBridge) {
        MainSettingsDialogs.showInstallationDialog(parentFrame, configService, onSyncBridge, onInstallBridge);
    }

    /**
     * Displays the about and troubleshooting help dialog.
     */
    public void showHelpDialog() {
        MainSettingsDialogs.showHelpDialog(parentFrame, configService);
    }

    /**
     * Displays a confirmation dialog and resets all settings to their default values if confirmed.
     *
     * @param onSettingsReset callback executed after settings have been reset
     */
    public void resetSettings(Runnable onSettingsReset) {
        int opt = JOptionPane.showConfirmDialog(parentFrame, "Reset all settings to default values?", "Confirm Reset", JOptionPane.YES_NO_OPTION);
        if (opt == JOptionPane.YES_OPTION && configService != null) {
            configService.resetVault();
            if (onSettingsReset != null) {
                onSettingsReset.run();
            }
        }
    }
}
