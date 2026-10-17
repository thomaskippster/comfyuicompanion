package de.tki.comfyuicompanion.service.impl;

import de.tki.comfyuicompanion.ui.EnvironmentInstallerDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Service responsible for scanning, verifying, and prompting user setup
 * for local ComfyUI installations on the host system.
 */
@Service
/**
 * Represents the comfy installation scanner class.
 */
public class ComfyInstallationScanner {

    private static final Logger logger = LoggerFactory.getLogger(ComfyInstallationScanner.class);

    private final ConfigService configService;
    private final EnvironmentBootstrapperImpl bootstrapper;
    private final ProfileManager profileManager;

    /**
     * Constructs a new ComfyInstallationScanner with required collaborators.
     *
     * @param configService  the application configuration service
     * @param bootstrapper   the environment bootstrapper for automated setups
     * @param profileManager the launch profile manager
     */
    @Autowired
    public ComfyInstallationScanner(
            ConfigService configService,
            EnvironmentBootstrapperImpl bootstrapper,
            ProfileManager profileManager) {
        this.configService = configService;
        this.bootstrapper = bootstrapper;
        this.profileManager = profileManager;
    }

    /**
     * Scans for an existing ComfyUI installation, verifies the integrity of the directory
     * (ensuring `main.py` is present), and optionally prompts the user to launch the installer dialog.
     *
     * @param parentComponent        the parent Swing component for UI dialogs
     * @param showDialogIfFound      whether to display an informational confirmation dialog if ComfyUI is verified
     * @param onInstallationFinished callback to invoke after a successful installation via the setup dialog
     * @return true if a valid ComfyUI installation exists, false otherwise
     */
    public boolean scanAndVerifyInstallation(
            Component parentComponent,
            boolean showDialogIfFound,
            Runnable onInstallationFinished) {
        return scanAndVerifyInstallation(parentComponent, showDialogIfFound, onInstallationFinished, null, null);
    }

    /**
     * Scans for an existing ComfyUI installation and checks whether local models are present,
     * launching the guided {@link de.tki.comfyuicompanion.ui.dialog.FirstRunSetupDialog} when needed.
     *
     * @param parentComponent        the parent Swing component for UI dialogs
     * @param showDialogIfFound      whether to display an informational confirmation dialog if ComfyUI is verified
     * @param onInstallationFinished callback to invoke after a successful installation
     * @param onPathsUpdated         callback to invoke when model paths are updated in the setup wizard
     * @param onOpenBlueprintGallery callback to invoke when the user chooses to browse blueprints
     * @return true if a valid ComfyUI installation exists, false otherwise
     */
    public boolean scanAndVerifyInstallation(
            Component parentComponent,
            boolean showDialogIfFound,
            Runnable onInstallationFinished,
            Runnable onPathsUpdated,
            Runnable onOpenBlueprintGallery) {
        if (configService != null) {
            configService.autoDiscoverPaths();
        }

        Path designatedPath = Paths.get(System.getProperty("user.home"), ".comfyui-companion", "ComfyUI");
        String currentComfyPath = configService != null ? configService.getComfyUIPath() : "";
        boolean installed = false;
        File verifiedDir = null;

        if (currentComfyPath != null && !currentComfyPath.trim().isEmpty()) {
            File comfyDir = new File(currentComfyPath);
            if (comfyDir.exists() && comfyDir.isDirectory() && new File(comfyDir, "main.py").exists()) {
                installed = true;
                verifiedDir = comfyDir;
            }
        }

        if (!installed && Files.exists(designatedPath)) {
            File designatedDir = designatedPath.toFile();
            if (designatedDir.exists() && designatedDir.isDirectory() && new File(designatedDir, "main.py").exists()) {
                installed = true;
                verifiedDir = designatedDir;
                if (configService != null) {
                    configService.setComfyUIPath(designatedDir.getAbsolutePath());
                }
            }
        }

        boolean isHeadlessOrTest = java.awt.GraphicsEnvironment.isHeadless()
                || (configService != null && configService.isTestEnvironment());

        int modelCount = 0;
        if (configService != null && configService.getModelsPath() != null && !configService.getModelsPath().isBlank()) {
            modelCount = de.tki.comfyuicompanion.ui.dialog.FirstRunSetupDialog.countModelFiles(
                    Paths.get(configService.getModelsPath()));
        }

        if (installed) {
            logger.info("✅ [Scan] ComfyUI installation verified at: {}", verifiedDir != null ? verifiedDir.getAbsolutePath() : currentComfyPath);
            if (showDialogIfFound) {
                JOptionPane.showMessageDialog(
                        parentComponent,
                        "✅ ComfyUI is installed and verified in designated folder:\n" + (verifiedDir != null ? verifiedDir.getAbsolutePath() : currentComfyPath),
                        "ComfyUI Verified",
                        JOptionPane.INFORMATION_MESSAGE
                );
            } else if (modelCount == 0 && !isHeadlessOrTest && onPathsUpdated != null) {
                openFirstRunWizard(parentComponent, true, onInstallationFinished, onPathsUpdated, onOpenBlueprintGallery);
            }
            return true;
        } else {
            logger.warn("⚠️ [Scan] ComfyUI is not installed in designated folder: {}", designatedPath);
            if (!showDialogIfFound && !isHeadlessOrTest && onPathsUpdated != null) {
                openFirstRunWizard(parentComponent, false, onInstallationFinished, onPathsUpdated, onOpenBlueprintGallery);
                return false;
            }
            int choice = JOptionPane.showConfirmDialog(
                    parentComponent,
                    "ComfyUI was not found in the designated folder (" + designatedPath + ").\n\nWould you like to start the ComfyUI installation setup now?",
                    "ComfyUI Installation Required",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );

            if (choice == JOptionPane.YES_OPTION) {
                launchEnvironmentInstaller(parentComponent, onInstallationFinished);
            }
            return false;
        }
    }

    private void openFirstRunWizard(Component parentComponent,
                                    boolean comfyInstalled,
                                    Runnable onInstallationFinished,
                                    Runnable onPathsUpdated,
                                    Runnable onOpenBlueprintGallery) {
        java.awt.Window owner = parentComponent instanceof java.awt.Window w
                ? w : (parentComponent != null ? javax.swing.SwingUtilities.getWindowAncestor(parentComponent) : null);
        de.tki.comfyuicompanion.ui.dialog.FirstRunSetupDialog wizard =
                new de.tki.comfyuicompanion.ui.dialog.FirstRunSetupDialog(
                        owner,
                        configService,
                        comfyInstalled,
                        onPathsUpdated,
                        () -> launchEnvironmentInstaller(parentComponent, onInstallationFinished),
                        onOpenBlueprintGallery
                );
        wizard.setVisible(true);
    }

    private void launchEnvironmentInstaller(Component parentComponent, Runnable onInstallationFinished) {
        javax.swing.JFrame parentFrame = null;
        if (parentComponent instanceof javax.swing.JFrame frame) {
            parentFrame = frame;
        } else if (parentComponent != null) {
            java.awt.Window window = javax.swing.SwingUtilities.getWindowAncestor(parentComponent);
            if (window instanceof javax.swing.JFrame frame) {
                parentFrame = frame;
            }
        }
        EnvironmentInstallerDialog dialog = new EnvironmentInstallerDialog(
                parentFrame,
                bootstrapper,
                configService,
                profileManager,
                onInstallationFinished
        );
        dialog.setVisible(true);
    }
}
