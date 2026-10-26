package de.tki.comfyuicompanion.ui.huggingface;

import de.tki.comfyuicompanion.domain.HuggingFaceFileEntry;
import de.tki.comfyuicompanion.domain.HuggingFaceModelEntry;
import de.tki.comfyuicompanion.domain.ModelInfo;
import de.tki.comfyuicompanion.service.IDownloadManager;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.HuggingFaceWorkflowDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Enterprise action handler for Hugging Face model details dialog actions,
 * encapsulating download queueing, navigation to Download Manager, external URL browsing,
 * UI layout helpers, and ComfyUI workflow dispatching.
 */
public final class HuggingFaceDetailsActionsHandler {

    private static final Logger logger = LoggerFactory.getLogger(HuggingFaceDetailsActionsHandler.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private HuggingFaceDetailsActionsHandler() {
        // Utility class
    }

    /**
     * Converts a HuggingFace file entry into a standardized {@link ModelInfo} domain object.
     *
     * @param file the file entry
     * @return populated ModelInfo
     */
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

    /**
     * Executes the download workflow for all missing files in a repository.
     * Switches to the Download Manager tab when available, or executes directly via IDownloadManager.
     *
     * @param owner                    parent window
     * @param missingFiles             list of missing file entries
     * @param onSwitchToDownloadManager callback to transition to Download Manager tab
     * @param downloadManager          download manager service
     * @param configService            application configuration
     * @param onDialogClose            callback to close parent dialog
     */
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

        List<ModelInfo> missingInfos = new ArrayList<>();
        for (HuggingFaceFileEntry file : missingFiles) {
            missingInfos.add(toModelInfo(file));
        }

        if (onSwitchToDownloadManager != null) {
            if (onDialogClose != null) onDialogClose.run();
            onSwitchToDownloadManager.accept(missingInfos);
        } else if (downloadManager != null) {
            String baseDir = configService != null ? configService.getModelsPath() : "models";
            boolean[] selected = new boolean[missingInfos.size()];
            java.util.Arrays.fill(selected, true);
            downloadManager.startQueue(
                    missingInfos,
                    selected,
                    baseDir,
                    (i, st) -> {},
                    () -> {}
            );
            if (onDialogClose != null) onDialogClose.run();
        }
    }

    /**
     * Dispatches the synthesized LiteGraph workflow to the running ComfyUI instance.
     *
     * @param owner              parent window
     * @param repo               repository entry
     * @param selectedFile       selected model file
     * @param prompt             user prompt
     * @param workflowDispatcher dispatcher service
     * @param btnSend            action button to update status text
     * @param onComplete         callback upon successful dispatch
     */
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
                        openUrl("http://127.0.0.1:8188");
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

    /**
     * Asynchronously loads a preview image banner into the target JLabel with fallback to thumbnail generator.
     *
     * @param repo     the model entry
     * @param darkMode whether dark mode is active
     * @param target   label receiving the scaled icon
     */
    public static void loadAsyncPreviewBanner(HuggingFaceModelEntry repo, boolean darkMode, JLabel target) {
        if (target == null) return;
        String previewUrl = repo != null ? repo.thumbnailUrl() : null;
        if (previewUrl == null || previewUrl.isBlank()) {
            if (repo != null) {
                Image thumb = HuggingFaceThumbnailGenerator.getThumbnail(repo, darkMode, null);
                if (thumb != null) {
                    target.setIcon(new ImageIcon(thumb));
                }
            }
            return;
        }
        Thread.ofVirtual().name("HF-BannerLoader").start(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(previewUrl)).timeout(Duration.ofSeconds(10)).GET().build();
                HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() == 200) {
                    try (InputStream in = response.body()) {
                        java.awt.image.BufferedImage orig = javax.imageio.ImageIO.read(in);
                        if (orig != null) {
                            int targetW = Math.min(1100, orig.getWidth());
                            int targetH = (int) (orig.getHeight() * ((double) targetW / orig.getWidth()));
                            if (targetH > 280) {
                                targetH = 280;
                                targetW = (int) (orig.getWidth() * ((double) targetH / orig.getHeight()));
                            }
                            Image scaled = orig.getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);
                            SwingUtilities.invokeLater(() -> target.setIcon(new ImageIcon(scaled)));
                            return;
                        }
                    }
                }
            } catch (Exception ignored) {
                logger.debug("Failed loading banner image: {}", ignored.getMessage());
            }
            if (repo != null) {
                Image thumb = HuggingFaceThumbnailGenerator.getThumbnail(repo, darkMode, null);
                if (thumb != null) {
                    SwingUtilities.invokeLater(() -> target.setIcon(new ImageIcon(thumb)));
                }
            }
        });
    }

    /**
     * Opens a web URL in the default system browser.
     *
     * @param url the URL to open
     */
    public static void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception e) {
            logger.warn("Could not open external URL {}: {}", url, e.getMessage());
        }
    }

    /**
     * Appends a standard metadata text row to a panel.
     *
     * @param parent        container panel
     * @param label         metadata title
     * @param value         metadata string
     * @param textSecondary secondary text color
     * @param textPrimary   primary text color
     */
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

    /**
     * Appends a metadata row containing a custom component to a panel.
     *
     * @param parent         container panel
     * @param label          metadata title
     * @param valueComponent component displaying the value or status
     * @param textSecondary  secondary text color
     */
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
}
