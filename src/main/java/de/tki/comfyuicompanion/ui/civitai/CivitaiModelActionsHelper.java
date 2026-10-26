package de.tki.comfyuicompanion.ui.civitai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tki.comfyuicompanion.domain.CivitaiModelEntry;
import de.tki.comfyuicompanion.domain.ModelFolder;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.ICivitaiOperationalModelService;
import de.tki.comfyuicompanion.service.IDownloadManager;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Enterprise action helper managing ComfyUI workflow dispatching, remote image generation,
 * partial download fragment verification and cleanup, and download queue coordination for Civitai models.
 */
public final class CivitaiModelActionsHelper {
    private static final Logger logger = LoggerFactory.getLogger(CivitaiModelActionsHelper.class);

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
                try { size = Files.size(cmfd); } catch (Exception ignored) {
                    logger.debug("Ignored non-fatal exception: {}", ignored.getMessage());
                }
            } else if (Files.exists(part0)) {
                try { size = Files.size(part0); } catch (Exception ignored) {
                    logger.debug("Ignored non-fatal exception: {}", ignored.getMessage());
                }
            }

            boolean hasFragment = Files.exists(cmfd) || Files.exists(part0);
            if (hasFragment) {
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
                } catch (Exception ignored) {
                    logger.debug("Ignored non-fatal exception: {}", ignored.getMessage());
                }
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

        if (btnSendToComfy != null) btnSendToComfy.setEnabled(false);
        if (btnSendAndGenerate != null) btnSendAndGenerate.setEnabled(false);
        btnSendToComfy.setText("Sending to ComfyUI... ⏳");
        btnSendToComfy.setIcon(SvgIconFactory.get(AppIcon.REFRESH, 14));

        operationalModelService.sendWorkflowToComfyUI(entry, requiredModels, customPrompt)
                .thenAccept(success -> SwingUtilities.invokeLater(() -> {
                    if (btnSendToComfy != null) btnSendToComfy.setEnabled(true);
                    if (btnSendAndGenerate != null) btnSendAndGenerate.setEnabled(true);
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
                        if (btnSendToComfy != null) btnSendToComfy.setEnabled(true);
                        if (btnSendAndGenerate != null) btnSendAndGenerate.setEnabled(true);
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

        if (btnSendToComfy != null) btnSendToComfy.setEnabled(false);
        if (btnSendAndGenerate != null) btnSendAndGenerate.setEnabled(false);
        btnSendAndGenerate.setText("Generating in ComfyUI... ⏳");
        btnSendAndGenerate.setIcon(SvgIconFactory.get(AppIcon.REFRESH, 14));

        operationalModelService.sendToComfyUIAndGenerate(entry, requiredModels, customPrompt)
                .thenAccept(result -> SwingUtilities.invokeLater(() -> {
                    if (btnSendToComfy != null) btnSendToComfy.setEnabled(true);
                    if (btnSendAndGenerate != null) btnSendAndGenerate.setEnabled(true);
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
                        if (btnSendToComfy != null) btnSendToComfy.setEnabled(true);
                        if (btnSendAndGenerate != null) btnSendAndGenerate.setEnabled(true);
                        btnSendAndGenerate.setText("Regenerate Image");
                        btnSendAndGenerate.setIcon(SvgIconFactory.get(AppIcon.LAUNCH, 14));
                        CivitaiActionLauncher.showComfyErrorMessage(parent, ex, "generating in ComfyUI");
                    });
                    return null;
                });
    }

    /**
     * Builds the top header block for the Civitai model details dialog.
     *
     * @param entry         the model metadata entry
     * @param parent        the parent window
     * @param textPrimary   primary theme text color
     * @param textSecondary secondary theme text color
     * @return configured header JPanel
     */
    public static JPanel buildHeaderPanel(CivitaiModelEntry entry, Window parent, Color textPrimary, Color textSecondary) {
        JPanel headerPanel = new JPanel(new BorderLayout(15, 0));
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(16, 20, 10, 20));

        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.setOpaque(false);

        JLabel titleLbl = new JLabel(entry.name());
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 18));
        titleLbl.setForeground(textPrimary);
        titleBlock.add(titleLbl);
        titleBlock.add(Box.createVerticalStrut(4));

        String subText = String.format("By %s  •  Type: %s  •  Base Architecture: %s  •  Size: %s  •  ⭐ %.1f",
                entry.creator(), entry.type(),
                entry.baseModel() != null && !entry.baseModel().isBlank() ? entry.baseModel() : "General",
                entry.formattedSize(), entry.rating());
        JLabel subLbl = new JLabel(subText);
        subLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subLbl.setForeground(textSecondary);
        titleBlock.add(subLbl);
        headerPanel.add(titleBlock, BorderLayout.CENTER);

        JButton civitaiWebBtn = new JButton("Open on Civitai ↗");
        civitaiWebBtn.setFont(new Font("SansSerif", Font.PLAIN, 12));
        civitaiWebBtn.addActionListener(e -> CivitaiActionLauncher.openCivitaiWebPage(entry.id(), parent));
        headerPanel.add(civitaiWebBtn, BorderLayout.EAST);

        return headerPanel;
    }

    /**
     * Dispatches the download queue for missing operational models and manages completion state.
     *
     * @param parent                  parent window
     * @param missing                 list of missing models to download
     * @param downloadManager         download manager service
     * @param configService           configuration service
     * @param operationalModelService operational models resolution service
     * @param onProgress              callback per model status update
     * @param onFinished              callback when download queue finishes
     */
    public static void executeDownloadQueue(
            Window parent,
            List<ModelInfo> missing,
            IDownloadManager downloadManager,
            ConfigService configService,
            ICivitaiOperationalModelService operationalModelService,
            java.util.function.BiConsumer<ModelInfo, String> onProgress,
            Runnable onFinished) {

        if (downloadManager == null || missing == null || missing.isEmpty()) return;

        List<ModelInfo> validQueue = new java.util.ArrayList<>();
        for (ModelInfo m : missing) {
            if (operationalModelService != null && operationalModelService.isModelFilePresent(m)) {
                m.setPopularity("installed");
                m.setDescription("✅ Already exists");
                continue;
            }
            String url = m.getUrl();
            if (url == null || url.isBlank() || url.equalsIgnoreCase("LOCAL") || (!url.toLowerCase().startsWith("http://") && !url.toLowerCase().startsWith("https://"))) {
                logger.warn("Cannot download model [{}] because it has no remote download URL.", m.getName());
                m.setDescription("❌ No remote download URL available");
                continue;
            }
            validQueue.add(m);
        }

        if (validQueue.isEmpty()) {
            if (onFinished != null) onFinished.run();
            return;
        }

        boolean[] selection = new boolean[validQueue.size()];
        for (int i = 0; i < selection.length; i++) selection[i] = true;

        String baseDir = configService != null ? configService.getModelsPath() : "models";

        for (ModelInfo m : validQueue) {
            m.setDescription("Starting...");
            if (onProgress != null) {
                onProgress.accept(m, "Starting...");
            }
        }

        downloadManager.startQueue(
                validQueue,
                selection,
                baseDir,
                (index, status) -> SwingUtilities.invokeLater(() -> {
                    if (index >= 0 && index < validQueue.size()) {
                        ModelInfo currentInfo = validQueue.get(index);
                        currentInfo.setDescription(status);
                        if (onProgress != null) {
                            onProgress.accept(currentInfo, status);
                        }
                    }
                }),
                () -> SwingUtilities.invokeLater(() -> {
                    boolean allInstalled = true;
                    boolean authFailure = false;
                    for (ModelInfo m : missing) {
                        boolean present = operationalModelService != null && operationalModelService.isModelFilePresent(m);
                        if (present) {
                            m.setPopularity("installed");
                            m.setDescription("✅ Installed");
                        } else {
                            allInstalled = false;
                            String desc = m.getDescription();
                            if (desc != null && (desc.contains("Auth") || desc.contains("Token") || desc.contains("401") || desc.contains("API Key"))) {
                                authFailure = true;
                            }
                        }
                    }
                    if (onFinished != null) {
                        onFinished.run();
                    }
                    if (allInstalled) {
                        downloadManager.notifyComfyUI(true);
                        JOptionPane.showMessageDialog(parent,
                                "All operational models successfully downloaded!\nYou can now send the workflow to ComfyUI.",
                                "Download Finished", JOptionPane.INFORMATION_MESSAGE);
                    } else if (authFailure) {
                        CivitaiActionLauncher.showAuthFailureMessage(parent);
                    }
                })
        );
    }
}
