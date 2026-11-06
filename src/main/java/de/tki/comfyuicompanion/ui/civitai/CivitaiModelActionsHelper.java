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
import java.io.File;
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
            String filename = info.getName();
            if (filename == null || filename.isBlank()) continue;

            Path cmfd = Path.of(base, sub, filename + ".cmfd");
            Path part0 = Path.of(base, sub, filename + ".part0");

            long size = 0;
            try {
                if (Files.exists(cmfd)) size += Files.size(cmfd);
                if (Files.exists(part0)) size += Files.size(part0);
            } catch (Exception ignored) {
            }

            boolean hasFragment = Files.exists(cmfd) || Files.exists(part0);
            if (hasFragment) {
                String s = size >= 1024 * 1024 * 1024
                        ? String.format(Locale.US, "%.1f GB", size / (1024.0 * 1024.0 * 1024.0))
                        : String.format(Locale.US, "%.0f MB", size / (1024.0 * 1024.0));
                info.setDescription("Incomplete (" + s + ")");
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
        int cleaned = 0;
        long bytesReclaimed = 0;

        for (ModelInfo info : requiredModels) {
            String sub = info.getSave_path() != null ? info.getSave_path() : (info.getType() != null ? info.getType() : ModelFolder.CHECKPOINTS.getDefaultFolderName());
            String filename = info.getName();
            if (filename == null || filename.isBlank()) continue;

            File dir = new File(base, sub);
            if (!dir.exists() || !dir.isDirectory()) continue;

            File cmfd = new File(dir, filename + ".cmfd");
            if (cmfd.exists()) {
                bytesReclaimed += cmfd.length();
                if (cmfd.delete()) cleaned++;
            }

            File[] parts = dir.listFiles((d, name) -> name.startsWith(filename + ".part"));
            if (parts != null) {
                for (File p : parts) {
                    bytesReclaimed += p.length();
                    if (p.delete()) cleaned++;
                }
            }
            info.setDescription("Missing");
        }

        double mb = bytesReclaimed / (1024.0 * 1024.0);
        JOptionPane.showMessageDialog(parent,
                String.format(Locale.US, "Cleaned %d fragment files.\nReclaimed %.1f MB disk space.", cleaned, mb),
                "Fragments Removed", JOptionPane.INFORMATION_MESSAGE);

        if (onRefreshed != null) onRefreshed.run();
    }

    /**
     * Dispatches the workflow loading process to ComfyUI.
     */
    public static void dispatchSendWorkflow(
            Component parent,
            CivitaiModelEntry entry,
            List<ModelInfo> requiredModels,
            ICivitaiOperationalModelService operationalModelService,
            JButton btnSendToComfy,
            JButton btnSendAndGenerate,
            String customPrompt,
            ConfigService configService,
            Runnable onFinished) {

        if (operationalModelService == null) return;
        List<ModelInfo> stillMissing = operationalModelService.getMissingModels(requiredModels);
        if (!stillMissing.isEmpty()) {
            CivitaiActionLauncher.showMissingModelsWarning(parent, stillMissing);
            return;
        }

        if (btnSendToComfy != null) btnSendToComfy.setEnabled(false);
        if (btnSendAndGenerate != null) btnSendAndGenerate.setEnabled(false);
        btnSendToComfy.setText("Sending to ComfyUI...");
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
                            "Workflow Loaded",
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
                        JOptionPane.showMessageDialog(
                                parent,
                                "Failed to send workflow to ComfyUI: " + ex.getCause().getMessage(),
                                "ComfyUI Error",
                                JOptionPane.ERROR_MESSAGE
                        );
                    });
                    return null;
                });
    }

    /**
     * Sends the workflow to ComfyUI and directly triggers prompt execution.
     */
    public static void dispatchSendAndGenerate(
            Component parent,
            CivitaiModelEntry entry,
            List<ModelInfo> requiredModels,
            ICivitaiOperationalModelService operationalModelService,
            JButton btnSendToComfy,
            JButton btnSendAndGenerate,
            String customPrompt,
            ConfigService configService,
            JLabel previewLabel,
            Runnable onFinished) {

        if (operationalModelService == null) return;
        List<ModelInfo> stillMissing = operationalModelService.getMissingModels(requiredModels);
        if (!stillMissing.isEmpty()) {
            CivitaiActionLauncher.showMissingModelsWarning(parent, stillMissing);
            return;
        }

        if (btnSendToComfy != null) btnSendToComfy.setEnabled(false);
        if (btnSendAndGenerate != null) btnSendAndGenerate.setEnabled(false);
        btnSendAndGenerate.setText("Generating in ComfyUI...");
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
                                "Generation Completed!",
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
                        JOptionPane.showMessageDialog(
                                parent,
                                "Prompt queued successfully in ComfyUI!\n(Check ComfyUI interface for real-time progress)",
                                "Generation Queued",
                                JOptionPane.INFORMATION_MESSAGE
                        );
                        CivitaiActionLauncher.openComfyUIBrowser(comfyUrl);
                    }
                    if (onFinished != null) onFinished.run();
                }))
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        if (btnSendToComfy != null) btnSendToComfy.setEnabled(true);
                        if (btnSendAndGenerate != null) btnSendAndGenerate.setEnabled(true);
                        btnSendAndGenerate.setText("Generate in ComfyUI");
                        btnSendAndGenerate.setIcon(SvgIconFactory.get(AppIcon.LAUNCH, 14));
                        JOptionPane.showMessageDialog(
                                parent,
                                "Execution failed: " + ex.getCause().getMessage(),
                                "ComfyUI Error",
                                JOptionPane.ERROR_MESSAGE
                        );
                    });
                    return null;
                });
    }

    /**
     * Builds the standard header panel with title, creator info, and web link button.
     */
    public static JPanel buildHeaderPanel(CivitaiModelEntry entry, Window parent, Color textPrimary, Color textSecondary) {
        JPanel headerPanel = new JPanel(new BorderLayout(12, 0));
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(0, 0, 10, 0));

        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.setOpaque(false);

        JLabel titleLbl = new JLabel(entry.name());
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 18));
        titleLbl.setForeground(textPrimary);
        titleBlock.add(titleLbl);
        titleBlock.add(Box.createVerticalStrut(4));

        String subText = String.format("By %s  |  Type: %s  |  Base Architecture: %s  |  Size: %s  |  Rating: %.1f",
                entry.creator(), entry.type(),
                entry.baseModel() != null && !entry.baseModel().isBlank() ? entry.baseModel() : "General",
                entry.formattedSize(), entry.rating());
        JLabel subLbl = new JLabel(subText);
        subLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subLbl.setForeground(textSecondary);
        titleBlock.add(subLbl);
        headerPanel.add(titleBlock, BorderLayout.CENTER);

        JButton civitaiWebBtn = new JButton("Open on Civitai", SvgIconFactory.get(AppIcon.LINK, 12));
        civitaiWebBtn.setFont(new Font("SansSerif", Font.PLAIN, 12));
        civitaiWebBtn.addActionListener(e -> CivitaiActionLauncher.openCivitaiWebPage(entry.id(), parent));
        headerPanel.add(civitaiWebBtn, BorderLayout.EAST);

        return headerPanel;
    }

    /**
     * Dispatches the download queue for missing operational models and manages completion state.
     */
    public static void executeDownloadQueue(
            Component parent,
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
                m.setDescription("Already exists");
                continue;
            }
            String url = m.getUrl();
            if (url == null || url.isBlank() || url.equalsIgnoreCase("LOCAL") || (!url.toLowerCase().startsWith("http://") && !url.toLowerCase().startsWith("https://"))) {
                logger.warn("Cannot download model [{}] because it has no remote download URL.", m.getName());
                m.setDescription("No remote download URL available");
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
                            m.setDescription("Installed");
                        } else {
                            allInstalled = false;
                            String desc = m.getDescription();
                            if (desc != null && (desc.contains("Auth") || desc.contains("Token") || desc.contains("401") || desc.contains("API Key"))) {
                                authFailure = true;
                            }
                        }
                    }

                    if (onFinished != null) onFinished.run();

                    if (allInstalled) {
                        JOptionPane.showMessageDialog(
                                parent,
                                "All required models have been successfully downloaded and placed in their respective folders!",
                                "Download Complete",
                                JOptionPane.INFORMATION_MESSAGE
                        );
                    } else if (authFailure) {
                        CivitaiActionLauncher.showAuthFailureMessage(parent);
                    }
                })
        );
    }

    /**
     * Exports and saves the given Civitai workflow and its preview thumbnail into My Workflow Vault.
     */
    public static void saveWorkflowToVault(Window parent, CivitaiModelEntry entry, List<ModelInfo> requiredModels,
                                           ICivitaiOperationalModelService operationalModelService, JButton btnSaveToVault) {
        if (operationalModelService == null || entry == null) return;
        if (btnSaveToVault != null) {
            btnSaveToVault.setEnabled(false);
            btnSaveToVault.setText("Saving to Vault...");
        }

        Thread.ofVirtual().name("civitai-vault-save").start(() -> {
            try {
                String json = operationalModelService.generateGuiWorkflowJson(entry, requiredModels, null);
                if (json == null || json.isBlank()) {
                    SwingUtilities.invokeLater(() -> {
                        if (btnSaveToVault != null) {
                            btnSaveToVault.setEnabled(true);
                            btnSaveToVault.setText("Save Workflow to My Vault");
                        }
                        JOptionPane.showMessageDialog(parent, "No workflow JSON available to save.", "Vault Error", JOptionPane.WARNING_MESSAGE);
                    });
                    return;
                }

                File vaultDir = new File("user_workflows", "vault");
                if (!vaultDir.exists()) {
                    vaultDir.mkdirs();
                }
                String safeName = entry.name().replaceAll("[^a-zA-Z0-9._-]", "_");
                File targetJson = new File(vaultDir, safeName + ".json");
                Files.writeString(targetJson.toPath(), json);

                if (entry.thumbnailUrl() != null && !entry.thumbnailUrl().isBlank()) {
                    try {
                        String cleanUrl = entry.thumbnailUrl().replace(" ", "%20");
                        byte[] imgBytes = de.tki.comfyuicompanion.util.WorkflowImageLoader.loadImageBytes(cleanUrl);
                        String ext = entry.thumbnailUrl().toLowerCase(Locale.ROOT).endsWith(".webp") ? ".webp" : ".png";
                        File targetImg = new File(vaultDir, safeName + ext);
                        Files.write(targetImg.toPath(), imgBytes);
                    } catch (Exception imgEx) {
                        logger.debug("Failed saving preview image: {}", imgEx.getMessage());
                    }
                }

                try {
                    String imgExt = entry.thumbnailUrl() != null && entry.thumbnailUrl().toLowerCase(Locale.ROOT).endsWith(".webp") ? "webp" : "png";
                    de.tki.comfyuicompanion.domain.VaultWorkflowMetadata meta = new de.tki.comfyuicompanion.domain.VaultWorkflowMetadata(
                            safeName, entry.name(), "Imported Civitai workflow: " + entry.name(),
                            "Text to Image", entry.type() != null ? entry.type() : "General",
                            "CIVITAI", "https://civitai.com/models/" + entry.id(), safeName + "." + imgExt,
                            requiredModels != null ? requiredModels : List.of(), System.currentTimeMillis()
                    );
                    new com.fasterxml.jackson.databind.ObjectMapper().writeValue(new File(vaultDir, safeName + ".meta.json"), meta);
                } catch (Exception ignored) {}

                SwingUtilities.invokeLater(() -> {
                    if (btnSaveToVault != null) {
                        btnSaveToVault.setText("Saved in My Vault");
                        btnSaveToVault.setIcon(SvgIconFactory.get(AppIcon.CHECK, 14));
                        btnSaveToVault.setEnabled(false);
                    }
                });
            } catch (Exception ex) {
                logger.error("Failed saving Civitai workflow to vault: {}", ex.getMessage(), ex);
                SwingUtilities.invokeLater(() -> {
                    if (btnSaveToVault != null) {
                        btnSaveToVault.setEnabled(true);
                        btnSaveToVault.setText("Save Workflow to My Vault");
                    }
                    JOptionPane.showMessageDialog(parent, "Failed to save workflow to vault: " + ex.getMessage(), "Vault Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }
}
