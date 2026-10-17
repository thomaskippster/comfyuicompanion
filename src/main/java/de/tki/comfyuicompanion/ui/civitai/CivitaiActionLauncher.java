package de.tki.comfyuicompanion.ui.civitai;

import de.tki.comfyuicompanion.domain.ModelInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Utility component handling external browser navigation, preview image downloading,
 * and user notifications for Civitai dialogs.
 */
public final class CivitaiActionLauncher {

    private static final Logger logger = LoggerFactory.getLogger(CivitaiActionLauncher.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private CivitaiActionLauncher() {
        // Utility class
    }

    /**
     * Opens the generated image in the system browser using the ComfyUI view endpoint.
     *
     * @param comfyUrl base ComfyUI URL
     * @param filename generated image filename
     */
    public static void openGeneratedImage(String comfyUrl, String filename) {
        try {
            String baseUrl = comfyUrl != null ? comfyUrl : "http://127.0.0.1:8188";
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(baseUrl + "/view?filename=" + filename + "&type=output"));
            }
        } catch (Exception ex) {
            logger.warn("Could not open generated image: {}", ex.getMessage());
        }
    }

    /**
     * Opens the ComfyUI web interface in the default browser.
     *
     * @param comfyUrl base ComfyUI URL
     */
    public static void openComfyUIBrowser(String comfyUrl) {
        try {
            String baseUrl = comfyUrl != null ? comfyUrl : "http://127.0.0.1:8188";
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(baseUrl));
            }
        } catch (Exception ex) {
            logger.warn("Could not open ComfyUI browser: {}", ex.getMessage());
        }
    }

    /**
     * Opens the Civitai web page for the specified model ID.
     *
     * @param modelId the Civitai model ID
     * @param parent  parent component for dialog fallbacks
     */
    public static void openCivitaiWebPage(long modelId, Component parent) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI("https://civitai.com/models/" + modelId));
            }
        } catch (Exception ex) {
            logger.warn("Could not launch web browser: {}", ex.getMessage());
            JOptionPane.showMessageDialog(parent, "URL: https://civitai.com/models/" + modelId, "Civitai URL", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Displays a warning modal listing missing model dependencies.
     *
     * @param parent       parent component
     * @param stillMissing list of missing models
     */
    public static void showMissingModelsWarning(Component parent, List<ModelInfo> stillMissing) {
        StringBuilder sb = new StringBuilder("Cannot proceed. The following required models are missing on disk:\n\n");
        for (ModelInfo m : stillMissing) {
            String fname = m.getFilename() != null && !m.getFilename().isBlank() ? m.getFilename() : m.getName();
            sb.append(" • ").append(fname).append("\n");
        }
        sb.append("\nPlease download the required models before sending or generating.");
        JOptionPane.showMessageDialog(parent, sb.toString(), "Missing Models", JOptionPane.WARNING_MESSAGE);
    }

    /**
     * Loads a preview image asynchronously from the specified URL into the target label.
     *
     * @param url    the image URL
     * @param target the JLabel to display the scaled image
     */
    public static void loadAsyncPreview(String url, JLabel target) {
        if (url == null || url.isBlank()) {
            target.setText("No preview image available");
            return;
        }

        Thread.ofVirtual().name("Civitai-DetailPreview").start(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(12)).GET().build();
                HttpResponse<InputStream> res = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (res.statusCode() == 200) {
                    try (InputStream in = res.body()) {
                        BufferedImage img = ImageIO.read(in);
                        if (img != null) {
                            int targetW = 280;
                            int targetH = 380;
                            Image scaled = img.getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);
                            SwingUtilities.invokeLater(() -> {
                                target.setText(null);
                                target.setIcon(new ImageIcon(scaled));
                            });
                        }
                    }
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> target.setText("Preview unavailable"));
            }
        });
    }

    /**
     * Displays a notification when download fails due to authentication requirements.
     *
     * @param parent parent component
     */
    public static void showAuthFailureMessage(Component parent) {
        JOptionPane.showMessageDialog(parent,
                "One or more models require authentication (Civitai API Key or HuggingFace Token)!\n\n" +
                "The creator requires you to be logged in to download this model.\n" +
                "Please configure your API key in Settings -> API Keys & Services and try again.",
                "Authentication Required", JOptionPane.WARNING_MESSAGE);
    }

    /**
     * Displays an error dialog for ComfyUI execution failures.
     *
     * @param parent      parent component
     * @param ex          throwable error
     * @param actionTitle action description
     */
    public static void showComfyErrorMessage(Component parent, Throwable ex, String actionTitle) {
        String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
        JOptionPane.showMessageDialog(parent,
                "Failed " + actionTitle + ":\n" + msg,
                "ComfyUI Error", JOptionPane.ERROR_MESSAGE);
    }
}
