package de.tki.comfyuicompanion.ui.civitai;

import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.domain.ModelFolder;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.ICivitaiOperationalModelService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Enterprise action helper managing ComfyUI workflow dispatching, remote image generation,
 * and partial download fragment verification and cleanup for Civitai models.
 */
public final class CivitaiModelActionsHelper {

    private CivitaiModelActionsHelper() {
        // Utility helper
    }

    /**
     * Inspects the model directory for incomplete download fragments (.cmfd, .part0)
     * and annotates missing models with partial download indicators.
     *
     * @param baseDir        the root directory for model storage
     * @param requiredModels list of models required for operation
     */
    public static void checkAndFlagIncompleteModels(String baseDir, List<ModelInfo> requiredModels) {
        if (requiredModels == null) return;
        String base = baseDir != null && !baseDir.isBlank() ? baseDir : "models";

        for (ModelInfo info : requiredModels) {
            if ("installed".equalsIgnoreCase(info.getPopularity()) || (info.getDescription() != null && info.getDescription().contains("Already exists"))) {
                continue;
            }
            String sub = info.getSave_path() != null ? info.getSave_path() : (info.getType() != null ? info.getType() : ModelFolder.CHECKPOINTS.getDefaultFolderName());
            if (sub.startsWith("models/") || sub.startsWith("models\\")) sub = sub.substring(7);
            String fname = info.getFilename() != null && !info.getFilename().isBlank() ? info.getFilename() : info.getName();
            Path target = Path.of(base, sub, fname);
            Path cmfd = target.resolveSibling(fname + ".cmfd");
            Path part0 = target.resolveSibling(fname + ".part0");

            long size = 0L;
            if (Files.exists(cmfd)) {
                try { size = Files.size(cmfd); } catch (Exception ignored) {}
            } else if (Files.exists(part0)) {
                try { size = Files.size(part0); } catch (Exception ignored) {}
            }

            if (size > 0) {
                String s = size >= 1024 * 1024 * 1024
                        ? String.format(Locale.US, "%.1f GB", size / (1024.0 * 1024.0 * 1024.0))
                        : String.format(Locale.US, "%.0f MB", size / (1024.0 * 1024.0));
                info.setDescription("⏸️ Incomplete (" + s + ")");
            }
        }
    }

    /**
     * Prompts the user and safely deletes all incomplete download fragments (.cmfd, .part*)
     * for missing models in the specified operational models list.
     *
     * @param parent         parent dialog or component
     * @param baseDir        base models directory
     * @param requiredModels operational models list
     * @param onRefreshed    callback to update table model and button states
     */
    public static void cleanIncompleteFragments(Component parent, String baseDir, List<ModelInfo> requiredModels, Runnable onRefreshed) {
        int res = JOptionPane.showConfirmDialog(parent,
                "Delete partial download files (.cmfd, .part*) for missing models to reclaim disk space?",
                "Clean Incomplete Fragments", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (res != JOptionPane.YES_OPTION) return;

        String base = baseDir != null && !baseDir.isBlank() ? baseDir : "models";
        for (ModelInfo info : requiredModels) {
            if (info.getDescription() != null && info.getDescription().contains("Incomplete")) {
                String sub = info.getSave_path() != null ? info.getSave_path() : (info.getType() != null ? info.getType() : ModelFolder.CHECKPOINTS.getDefaultFolderName());
                if (sub.startsWith("models/") || sub.startsWith("models\\")) sub = sub.substring(7);
                String fname = info.getFilename() != null && !info.getFilename().isBlank() ? info.getFilename() : info.getName();
                Path target = Path.of(base, sub, fname);
                try {
                    Files.deleteIfExists(target.resolveSibling(fname + ".cmfd"));
                    Files.deleteIfExists(target.resolveSibling(fname + ".cmfd_merge"));
                    for (int i = 0; i < 16; i++) {
                        Files.deleteIfExists(target.resolveSibling(fname + ".part" + i));
                    }
                } catch (Exception ignored) {}
                info.setDescription("Missing");
            }
        }
        if (onRefreshed != null) onRefreshed.run();
        JOptionPane.showMessageDialog(parent, "Incomplete fragments removed successfully.", "Cleaned", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Dispatches the generated LiteGraph GUI workflow directly to the ComfyUI canvas.
     */
    public static void dispatchSendWorkflow(
            Component parent,
            CivitaiModelEntry entry,
            List<ModelInfo> requiredModels,
            String customPrompt,
            ICivitaiOperationalModelService operationalModelService,
            ConfigService configService,
            JButton btnSendToComfy,
            JButton btnSendAndGenerate,
            Runnable onFinished) {
        if (operationalModelService == null) return;

        List<ModelInfo> stillMissing = operationalModelService.getMissingModels(requiredModels);
        if (!stillMissing.isEmpty()) {
            CivitaiActionLauncher.showMissingModelsWarning(parent, stillMissing);
            return;
        }

        btnSendToComfy.setEnabled(false);
        btnSendAndGenerate.setEnabled(false);
        btnSendToComfy.setText("Sending to ComfyUI... ⏳");
        btnSendToComfy.setIcon(SvgIconFactory.get(AppIcon.REFRESH, 14));

        operationalModelService.sendWorkflowToComfyUI(entry, requiredModels, customPrompt)
                .thenAccept(success -> SwingUtilities.invokeLater(() -> {
                    btnSendToComfy.setEnabled(true);
                    btnSendAndGenerate.setEnabled(true);
                    btnSendToComfy.setText("Send to ComfyUI");
                    btnSendToComfy.setIcon(SvgIconFactory.get(AppIcon.SEND, 14));

                    String comfyUrl = configService != null ? configService.getComfyUIUrl() : null;
                    CivitaiActionLauncher.openComfyUIBrowser(comfyUrl);

                    JOptionPane.showMessageDialog(
                            parent,
                            "Workflow for '" + entry.name() + "' has been loaded into ComfyUI!\n\n" +
                            "Your prompt has been placed into the Positive Prompt node on the canvas.\n" +
                            "You can now tweak parameters and generate directly in ComfyUI.",
                            "Workflow Loaded ✅",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                    if (onFinished != null) onFinished.run();
                }))
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        btnSendToComfy.setEnabled(true);
                        btnSendAndGenerate.setEnabled(true);
                        btnSendToComfy.setText("Send to ComfyUI");
                        btnSendToComfy.setIcon(SvgIconFactory.get(AppIcon.SEND, 14));
                        CivitaiActionLauncher.showComfyErrorMessage(parent, ex, "send workflow to ComfyUI");
                    });
                    return null;
                });
    }

    /**
     * Sends the prompt and workflow to ComfyUI queue and previews the generated image upon completion.
     */
    public static void dispatchSendAndGenerate(
            Component parent,
            CivitaiModelEntry entry,
            List<ModelInfo> requiredModels,
            String customPrompt,
            ICivitaiOperationalModelService operationalModelService,
            ConfigService configService,
            JButton btnSendToComfy,
            JButton btnSendAndGenerate,
            JLabel previewLabel) {
        if (operationalModelService == null) return;

        List<ModelInfo> stillMissing = operationalModelService.getMissingModels(requiredModels);
        if (!stillMissing.isEmpty()) {
            CivitaiActionLauncher.showMissingModelsWarning(parent, stillMissing);
            return;
        }

        btnSendToComfy.setEnabled(false);
        btnSendAndGenerate.setEnabled(false);
        btnSendAndGenerate.setText("Generating in ComfyUI... ⏳");
        btnSendAndGenerate.setIcon(SvgIconFactory.get(AppIcon.REFRESH, 14));

        operationalModelService.sendToComfyUIAndGenerate(entry, requiredModels, customPrompt)
                .thenAccept(result -> SwingUtilities.invokeLater(() -> {
                    btnSendToComfy.setEnabled(true);
                    btnSendAndGenerate.setEnabled(true);
                    btnSendAndGenerate.setText("Regenerate Image");
                    btnSendAndGenerate.setIcon(SvgIconFactory.get(AppIcon.LAUNCH, 14));

                    String comfyUrl = configService != null ? configService.getComfyUIUrl() : null;

                    if (result != null && result.filename() != null && !result.filename().isBlank()) {
                        CivitaiActionLauncher.loadAsyncPreview(result.imageUrl(), previewLabel);

                        String[] options = {"Open Image", "Open ComfyUI Browser", "Close"};
                        int choice = JOptionPane.showOptionDialog(
                                parent,
                                "Image successfully generated with '" + entry.name() + "'!\n\nFile: " + result.filename() + "\n(Preview updated in dialog)",
                                "Generation Completed! ✅",
                                JOptionPane.DEFAULT_OPTION,
                                JOptionPane.INFORMATION_MESSAGE,
                                SvgIconFactory.get(AppIcon.CHECK, 28),
                                options,
                                options[0]
                        );

                        if (choice == 0) {
                            CivitaiActionLauncher.openGeneratedImage(comfyUrl, result.filename());
                        } else if (choice == 1) {
                            CivitaiActionLauncher.openComfyUIBrowser(comfyUrl);
                        }
                    } else {
                        JOptionPane.showMessageDialog(parent,
                                "Workflow successfully queued in ComfyUI!\nPrompt ID: " + (result != null ? result.promptId() : "N/A"),
                                "ComfyUI Queued", JOptionPane.INFORMATION_MESSAGE);
                    }
                }))
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        btnSendToComfy.setEnabled(true);
                        btnSendAndGenerate.setEnabled(true);
                        btnSendAndGenerate.setText("Regenerate Image");
                        btnSendAndGenerate.setIcon(SvgIconFactory.get(AppIcon.LAUNCH, 14));
                        CivitaiActionLauncher.showComfyErrorMessage(parent, ex, "generating in ComfyUI");
                    });
                    return null;
                });
    }
}
