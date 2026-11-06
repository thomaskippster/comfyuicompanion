package de.tki.comfyuicompanion.ui.huggingface;

import de.tki.comfyuicompanion.domain.HuggingFaceFileEntry;
import de.tki.comfyuicompanion.domain.HuggingFaceModelEntry;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.domain.VaultWorkflowMetadata;
import de.tki.comfyuicompanion.service.IDownloadManager;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.HuggingFaceWorkflowAnalyzer;
import de.tki.comfyuicompanion.service.impl.HuggingFaceWorkflowDispatcher;
import de.tki.comfyuicompanion.ui.blueprint.WorkflowDialogDimensionHelper;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
import de.tki.comfyuicompanion.util.WorkflowImageLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Enterprise action handler for Hugging Face model details dialog actions,
 * encapsulating download queueing, navigation to Download Manager, external URL browsing,
 * UI layout helpers, preview banner loading, and Vault preservation.
 */
public final class HuggingFaceDetailsActionsHandler {

    private static final Logger logger = LoggerFactory.getLogger(HuggingFaceDetailsActionsHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private HuggingFaceDetailsActionsHandler() {
        // Utility class
    }

    public static boolean isShardedRepository(List<HuggingFaceFileEntry> files) {
        return files != null && files.stream().filter(f -> f.filename() != null && f.filename().matches(".*-\\d{5}-of-\\d{5}\\.safetensors")).count() > 1;
    }

    public static long calculateTotalShardedBytes(List<HuggingFaceFileEntry> files) {
        if (files == null || files.isEmpty()) return 0L;
        return files.stream().filter(f -> f.filename() != null && f.filename().matches(".*-\\d{5}-of-\\d{5}\\.safetensors")).mapToLong(HuggingFaceFileEntry::sizeBytes).sum();
    }

    public static int getShardCount(List<HuggingFaceFileEntry> files) {
        if (files == null || files.isEmpty()) return 0;
        return (int) files.stream().filter(f -> f.filename() != null && f.filename().matches(".*-\\d{5}-of-\\d{5}\\.safetensors")).count();
    }

    public static void applyHardwareCheck(JLabel label, HuggingFaceFileEntry file, List<HuggingFaceFileEntry> allFiles) {
        if (label == null || file == null) return;
        if (isShardedRepository(allFiles)) {
            long totalBytes = calculateTotalShardedBytes(allFiles);
            int count = getShardCount(allFiles);
            label.setText("Sharded Model: " + count + " parts (~" + HuggingFaceFileEntry.formatBytes(totalBytes) + ") - Datacenter GPU required!");
            label.setForeground(new Color(168, 85, 247));
        } else {
            String hint = file.vramHint() != null && !file.vramHint().isBlank() ? file.vramHint() : "Standard VRAM requirements";
            label.setText(hint + " - " + file.formattedSize());
            label.setForeground(hint.contains("High") || hint.contains("Datacenter") ? new Color(239, 68, 68) : new Color(34, 197, 130));
        }
    }

    public static void applyDownloadButtonState(JButton btnDownload, int missing, List<HuggingFaceFileEntry> allFiles) {
        if (btnDownload == null) return;
        if (isShardedRepository(allFiles)) {
            long totalBytes = calculateTotalShardedBytes(allFiles);
            int count = getShardCount(allFiles);
            btnDownload.setText("Download All " + count + " Shards (" + HuggingFaceFileEntry.formatBytes(totalBytes) + ")");
            btnDownload.setToolTipText("Warning: Massive sharded model (~" + HuggingFaceFileEntry.formatBytes(totalBytes) + "). Requires datacenter server hardware.");
            btnDownload.setEnabled(missing > 0);
        } else {
            btnDownload.setText(missing > 0 ? "Download Missing Models (" + missing + ")" : "Download Missing Models");
            btnDownload.setEnabled(missing > 0);
            btnDownload.setToolTipText(null);
        }
    }

    public static void applyActionButtonsState(
            JButton btnDownload,
            JButton btnSend,
            int missing,
            List<HuggingFaceFileEntry> allFiles,
            boolean isSelectedWorkflow) {
        if (btnDownload != null) {
            if (missing == 0 && allFiles != null && !allFiles.isEmpty()) {
                btnDownload.setText("All Models Installed");
                btnDownload.setEnabled(false);
            } else {
                applyDownloadButtonState(btnDownload, missing, allFiles);
            }
        }
        if (btnSend != null) {
            if (missing == 0 || isSelectedWorkflow) {
                btnSend.setEnabled(true);
                btnSend.setText("Send to ComfyUI");
                btnSend.setToolTipText(isSelectedWorkflow && missing > 0
                        ? "Load workflow directly into ComfyUI canvas (model weights can be downloaded separately)."
                        : null);
            } else {
                btnSend.setEnabled(false);
                btnSend.setToolTipText("Please download all missing models before sending to ComfyUI.");
            }
        }
    }

    public static String formatAvailableFilesSummary(List<HuggingFaceFileEntry> files, List<HuggingFaceFileEntry> operational) {
        if (files == null || files.isEmpty()) return "0";
        int workflowCount = 0;
        for (HuggingFaceFileEntry f : files) {
            if (f != null && (HuggingFaceWorkflowAnalyzer.isWorkflowFile(f.rfilename())
                    || "workflows".equalsIgnoreCase(f.suggestedFolder()))) {
                workflowCount++;
            }
        }
        int weightCount = files.size() - workflowCount;
        int reqSize = operational != null ? operational.size() : 0;
        if (workflowCount > 0) {
            return reqSize + " required model" + (reqSize != 1 ? "s" : "")
                    + " (" + weightCount + " model" + (weightCount != 1 ? "s" : "")
                    + ", " + workflowCount + " workflow" + (workflowCount != 1 ? "s" : "") + " in repo)";
        }
        return (reqSize < files.size() && reqSize > 0)
                ? reqSize + " required (" + files.size() + " variants in repo)"
                : String.valueOf(files.size());
    }

    public static ModelInfo toModelInfo(HuggingFaceFileEntry file) {
        String folder = file.suggestedFolder() != null ? file.suggestedFolder() : "checkpoints";
        ModelInfo info = new ModelInfo(folder, file.filename(), file.downloadUrl());
        info.setSave_path(folder);
        info.setFilename(file.filename());
        info.setSize(file.formattedSize());
        info.setByteSize(file.sizeBytes());
        info.setDescription("Missing");
        return info;
    }

    public static void executeDownloadMissing(
            Window owner,
            List<HuggingFaceFileEntry> missingFiles,
            Consumer<List<ModelInfo>> onSwitchToDownloadManager,
            IDownloadManager downloadManager,
            ConfigService configService,
            Runnable onDialogClose) {

        if (missingFiles == null || missingFiles.isEmpty()) {
            JOptionPane.showMessageDialog(owner, "All required models are already installed locally!", "Information", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        List<ModelInfo> missingInfos = missingFiles.stream().map(HuggingFaceDetailsActionsHandler::toModelInfo).toList();

        if (onSwitchToDownloadManager != null) {
            if (onDialogClose != null) onDialogClose.run();
            onSwitchToDownloadManager.accept(missingInfos);
        } else if (downloadManager != null) {
            String baseDir = configService != null ? configService.getModelsPath() : "models";
            boolean[] selected = new boolean[missingInfos.size()];
            java.util.Arrays.fill(selected, true);
            downloadManager.startQueue(missingInfos, selected, baseDir, (i, st) -> {}, () -> {});
            if (onDialogClose != null) onDialogClose.run();
        }
    }

    public static void dispatchSendWorkflow(
            Window owner,
            HuggingFaceModelEntry repo,
            HuggingFaceFileEntry selectedFile,
            String prompt,
            HuggingFaceWorkflowDispatcher workflowDispatcher,
            JButton btnSend,
            Runnable onComplete) {

        if (workflowDispatcher == null || selectedFile == null) return;
        if (btnSend != null) {
            btnSend.setEnabled(false);
            btnSend.setText("Sending...");
        }

        workflowDispatcher.sendWorkflowToComfyUI(repo, selectedFile, prompt)
                .thenAccept(success -> SwingUtilities.invokeLater(() -> {
                    if (btnSend != null) {
                        btnSend.setEnabled(true);
                        btnSend.setText("Send to ComfyUI");
                    }
                    int res = JOptionPane.showConfirmDialog(
                            owner,
                            "Workflow for '" + selectedFile.filename() + "' successfully loaded into ComfyUI canvas!\n\nOpen ComfyUI in browser now?",
                            "ComfyUI Canvas Ready", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE
                    );
                    if (res == JOptionPane.YES_OPTION) {
                        openUrl(workflowDispatcher.getSanitizedComfyUrl());
                    }
                    if (onComplete != null) onComplete.run();
                }))
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        if (btnSend != null) {
                            btnSend.setEnabled(true);
                            btnSend.setText("Send to ComfyUI");
                        }
                        JOptionPane.showMessageDialog(owner, "Failed to load workflow into ComfyUI:\n" + ex.getMessage(), "Connection Error", JOptionPane.ERROR_MESSAGE);
                    });
                    return null;
                });
    }

    public static void loadAsyncPreviewBanner(HuggingFaceModelEntry repo, boolean darkMode, JLabel target) {
        loadAsyncPreviewBanner(repo, darkMode, target, null, 11, 5);
    }

    public static void loadAsyncPreviewBanner(HuggingFaceModelEntry repo, boolean darkMode, JLabel target, JDialog ownerDialog, int metadataCount, int tableItemCount) {
        if (target == null) return;
        String previewUrl = repo != null ? repo.thumbnailUrl() : null;
        if (previewUrl == null || previewUrl.isBlank()) {
            if (repo != null) {
                Image thumb = HuggingFaceThumbnailGenerator.getThumbnail(repo, darkMode, null);
                if (thumb != null) target.setIcon(new ImageIcon(thumb));
            }
            return;
        }
        Thread.ofVirtual().name("HF-BannerLoader").start(() -> {
            try {
                BufferedImage orig = WorkflowImageLoader.downloadAndDecodeImage(previewUrl, Duration.ofSeconds(10));
                if (orig != null) {
                    int maxBannerH = WorkflowDialogDimensionHelper.calculateMaxBannerHeight(ownerDialog != null ? ownerDialog.getOwner() : null, metadataCount, tableItemCount, false);
                    int targetW = Math.min(1100, orig.getWidth());
                    int targetH = (int) (orig.getHeight() * ((double) targetW / orig.getWidth()));
                    if (targetH > maxBannerH) {
                        targetH = maxBannerH;
                        targetW = (int) (orig.getWidth() * ((double) targetH / orig.getHeight()));
                    }
                    Image scaled = orig.getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);
                    SwingUtilities.invokeLater(() -> {
                        target.setIcon(new ImageIcon(scaled));
                        if (ownerDialog != null) {
                            WorkflowDialogDimensionHelper.applyOptimalSizeAndLocation(ownerDialog, ownerDialog.getOwner(), true, metadataCount, tableItemCount, false);
                            ownerDialog.revalidate();
                            ownerDialog.repaint();
                        }
                    });
                    return;
                }
            } catch (Exception ignored) {
                logger.debug("Failed loading banner image: {}", ignored.getMessage());
            }
            if (repo != null) {
                Image thumb = HuggingFaceThumbnailGenerator.getThumbnail(repo, darkMode, null);
                if (thumb != null) SwingUtilities.invokeLater(() -> target.setIcon(new ImageIcon(thumb)));
            }
        });
    }

    public static void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception e) {
            logger.warn("Could not open external URL {}: {}", url, e.getMessage());
        }
    }

    public static void addRow(JPanel parent, String label, String value, Color textSecondary, Color textPrimary) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

        JLabel lbl = new JLabel(label + ":");
        lbl.setFont(new Font("SansSerif", Font.BOLD, 11));
        lbl.setForeground(textSecondary);
        lbl.setPreferredSize(new Dimension(140, 18));

        JLabel val = new JLabel(value);
        val.setFont(new Font("SansSerif", Font.PLAIN, 11));
        val.setForeground(textPrimary);

        row.add(lbl, BorderLayout.WEST);
        row.add(val, BorderLayout.CENTER);
        parent.add(row);
        parent.add(Box.createVerticalStrut(3));
    }

    public static void addRowWithComponent(JPanel parent, String label, JComponent valueComponent, Color textSecondary) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

        JLabel lbl = new JLabel(label + ":");
        lbl.setFont(new Font("SansSerif", Font.BOLD, 11));
        lbl.setForeground(textSecondary);
        lbl.setPreferredSize(new Dimension(180, 18));

        row.add(lbl, BorderLayout.WEST);
        row.add(valueComponent, BorderLayout.CENTER);
        parent.add(row);
        parent.add(Box.createVerticalStrut(3));
    }

    public static void saveWorkflowToVault(Window owner, HuggingFaceModelEntry repo, HuggingFaceFileEntry selectedFile,
                                           HuggingFaceWorkflowDispatcher workflowDispatcher, JButton btnSave) {
        if (selectedFile == null) return;
        if (btnSave != null) {
            btnSave.setEnabled(false);
            btnSave.setText("Saving to Vault...");
        }
        Thread.ofVirtual().name("hf-vault-save").start(() -> {
            try {
                String downloadUrl = selectedFile.downloadUrl();
                if (downloadUrl == null || downloadUrl.isBlank()) {
                    downloadUrl = "https://huggingface.co/" + repo.repoId() + "/resolve/main/" + selectedFile.rfilename();
                }
                String cleanUrl = downloadUrl.replace(" ", "%20");
                HttpRequest req = HttpRequest.newBuilder(URI.create(cleanUrl)).timeout(Duration.ofSeconds(15)).GET().build();
                HttpResponse<String> res = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
                if (res.statusCode() != 200 || res.body() == null) {
                    throw new IllegalStateException("Failed to download workflow JSON (HTTP " + res.statusCode() + ")");
                }

                File vaultDir = new File("user_workflows", "vault");
                if (!vaultDir.exists()) vaultDir.mkdirs();
                String safeName = selectedFile.filename().replaceAll("[^a-zA-Z0-9._-]", "_");
                if (safeName.toLowerCase(Locale.ROOT).endsWith(".json")) {
                    safeName = safeName.substring(0, safeName.length() - 5);
                }
                File targetJson = new File(vaultDir, safeName + ".json");
                Files.writeString(targetJson.toPath(), res.body());

                String imgExt = "png";
                if (repo != null && repo.thumbnailUrl() != null && !repo.thumbnailUrl().isBlank()) {
                    try {
                        byte[] imgBytes = WorkflowImageLoader.loadImageBytes(repo.thumbnailUrl());
                        if (imgBytes != null && imgBytes.length > 0) {
                            imgExt = repo.thumbnailUrl().toLowerCase(Locale.ROOT).endsWith(".webp") ? "webp" : "png";
                            Files.write(new File(vaultDir, safeName + "." + imgExt).toPath(), imgBytes);
                        }
                    } catch (Exception ignored) {}
                }

                try {
                    VaultWorkflowMetadata meta = new VaultWorkflowMetadata(
                            safeName, selectedFile.filename(), "Workflow from Hugging Face: " + repo.repoId(),
                            "Text to Image", "General", "HUGGINGFACE", "https://huggingface.co/" + repo.repoId(),
                            safeName + "." + imgExt, java.util.List.of(), System.currentTimeMillis()
                    );
                    MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(vaultDir, safeName + ".meta.json"), meta);
                } catch (Exception ignored) {}

                SwingUtilities.invokeLater(() -> {
                    if (btnSave != null) {
                        btnSave.setText("Saved in My Vault");
                        btnSave.setIcon(SvgIconFactory.get(AppIcon.CHECK, 14));
                        btnSave.setEnabled(false);
                    }
                });
            } catch (Exception ex) {
                logger.error("Failed saving HuggingFace workflow to vault: {}", ex.getMessage(), ex);
                SwingUtilities.invokeLater(() -> {
                    if (btnSave != null) {
                        btnSave.setEnabled(true);
                        btnSave.setText("Save Workflow to My Vault");
                    }
                    JOptionPane.showMessageDialog(owner, "Failed to save workflow to vault: " + ex.getMessage(), "Vault Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }
}
