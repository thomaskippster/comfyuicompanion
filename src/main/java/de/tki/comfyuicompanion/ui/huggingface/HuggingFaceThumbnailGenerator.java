package de.tki.comfyuicompanion.ui.huggingface;

import de.tki.comfyuicompanion.domain.HuggingFaceModelEntry;
import de.tki.comfyuicompanion.util.WorkflowImageLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service generating dynamic procedural preview images and managing asynchronous remote thumbnail loading
 * for Hugging Face repositories in the Model Hub.
 */
public final class HuggingFaceThumbnailGenerator {

    private static final Logger log = LoggerFactory.getLogger(HuggingFaceThumbnailGenerator.class);

    private static final int THUMB_WIDTH = 480;
    private static final int THUMB_HEIGHT = 300;

    private static final Map<String, Image> IMAGE_CACHE = new ConcurrentHashMap<>();

    private HuggingFaceThumbnailGenerator() {
        // Utility class
    }

    /**
     * Resolves the preview image for the given repository, loading remotely if available,
     * or synthesizing a procedural preview banner.
     *
     * @param entry         the Hugging Face repository entry
     * @param darkMode      whether dark theme is active
     * @param repaintTarget component to repaint when remote thumbnail finishes loading
     * @return an {@link Image} instance ready for rendering
     */
    public static Image getThumbnail(HuggingFaceModelEntry entry, boolean darkMode, Component repaintTarget) {
        if (entry == null) {
            return generateProceduralThumbnail(null, darkMode);
        }

        String remoteUrl = entry.thumbnailUrl();
        if (remoteUrl != null && !remoteUrl.isBlank()) {
            Image cached = IMAGE_CACHE.get(remoteUrl);
            if (cached != null) {
                return cached;
            }
            triggerAsyncDownload(remoteUrl, repaintTarget);
        }

        String procKey = "proc://" + entry.repoId() + (darkMode ? "_dark" : "_light");
        return IMAGE_CACHE.computeIfAbsent(procKey, k -> generateProceduralThumbnail(entry, darkMode));
    }

    private static void triggerAsyncDownload(String url, Component repaintTarget) {
        if (IMAGE_CACHE.containsKey(url)) return;

        Thread.ofVirtual().name("HF-ThumbDownload").start(() -> {
            try {
                BufferedImage img = WorkflowImageLoader.loadOrFetchCached(url, Duration.ofSeconds(10));
                if (img != null) {
                    IMAGE_CACHE.put(url, img);
                    if (repaintTarget != null) {
                        repaintTarget.repaint();
                    }
                }
            } catch (Exception e) {
                log.debug("Remote thumbnail download bypassed for {}: {}", url, e.getMessage());
            }
        });
    }

    /**
     * Synthesizes a high-resolution, stylized procedural banner preview for a Hugging Face repository.
     *
     * @param entry    the model entry
     * @param darkMode true for dark theme
     * @return high-quality {@link BufferedImage} preview
     */
    public static BufferedImage generateProceduralThumbnail(HuggingFaceModelEntry entry, boolean darkMode) {
        BufferedImage image = new BufferedImage(THUMB_WIDTH, THUMB_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        String repoId = entry != null ? entry.repoId() : "Generic/Model";
        String author = entry != null ? entry.author() : "Community";
        String modelName = entry != null ? entry.modelName() : "Model";
        String pipeline = (entry != null && entry.pipelineTag() != null && !entry.pipelineTag().isBlank())
                ? entry.pipelineTag().toUpperCase(Locale.ROOT)
                : "DIFFUSION";

        // 1. Futuristic Studio Background Gradient
        Color startGrad = darkMode ? new Color(15, 23, 42) : new Color(241, 245, 249);
        Color endGrad = darkMode ? new Color(30, 27, 75) : new Color(219, 234, 254);
        g2.setPaint(new GradientPaint(0, 0, startGrad, THUMB_WIDTH, THUMB_HEIGHT, endGrad));
        g2.fillRect(0, 0, THUMB_WIDTH, THUMB_HEIGHT);

        // 2. Decorative Neural Mesh / Latent Waves
        g2.setColor(darkMode ? new Color(6, 182, 212, 35) : new Color(14, 165, 233, 40));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawOval(THUMB_WIDTH - 160, -60, 220, 220);
        g2.drawOval(THUMB_WIDTH - 220, -100, 320, 320);

        g2.setColor(darkMode ? new Color(168, 85, 247, 30) : new Color(147, 51, 234, 35));
        g2.drawOval(-80, THUMB_HEIGHT - 140, 240, 240);

        // 3. Central Brand & Model Family
        String archTitle = resolveDisplayTitle(repoId, modelName);
        g2.setColor(darkMode ? new Color(248, 250, 252) : new Color(15, 23, 42));
        g2.setFont(new Font("SansSerif", Font.BOLD, 26));
        FontMetrics fmTitle = g2.getFontMetrics();
        int titleW = fmTitle.stringWidth(archTitle);
        g2.drawString(archTitle, Math.max(24, (THUMB_WIDTH - titleW) / 2), 125);

        // 4. Subtitle / Creator
        g2.setColor(darkMode ? new Color(148, 163, 184) : new Color(71, 85, 105));
        g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
        String sub = "by @" + author;
        FontMetrics fmSub = g2.getFontMetrics();
        g2.drawString(sub, Math.max(24, (THUMB_WIDTH - fmSub.stringWidth(sub)) / 2), 155);

        // 5. Header Badge Pill (Pipeline Task)
        drawPillBadge(g2, 24, 24, pipeline, new Color(14, 116, 144, 220), Color.WHITE);

        // Gated overlay badge if applicable
        if (entry != null && entry.isGated()) {
            drawPillBadge(g2, THUMB_WIDTH - 85, 24, "GATED", new Color(217, 119, 6, 230), Color.WHITE);
        }

        // 6. Bottom Status Indicator
        String bottomText = "ComfyUI Synthesized Workflow";
        g2.setColor(darkMode ? new Color(52, 211, 153) : new Color(5, 150, 105));
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2.drawString(bottomText, 24, THUMB_HEIGHT - 24);

        g2.dispose();
        return image;
    }

    private static String resolveDisplayTitle(String repoId, String modelName) {
        String lower = (repoId + " " + modelName).toLowerCase(Locale.ROOT);
        if (lower.contains("flux")) return "FLUX.1 DEV";
        if (lower.contains("wan")) return "WAN 2.1 VIDEO";
        if (lower.contains("ltx")) return "LTX-VIDEO";
        if (lower.contains("sdxl")) return "SDXL BASE";
        if (lower.contains("sd3")) return "STABLE DIFFUSION 3";
        if (lower.contains("qwen")) return "QWEN VISION";
        if (lower.contains("gguf")) return "GGUF QUANT";
        return modelName.length() > 22 ? modelName.substring(0, 20) + "..." : modelName;
    }

    private static void drawPillBadge(Graphics2D g2, int x, int y, String text, Color bg, Color fg) {
        g2.setFont(new Font("SansSerif", Font.BOLD, 10));
        FontMetrics fm = g2.getFontMetrics();
        int textW = fm.stringWidth(text);
        int badgeW = textW + 14;
        int badgeH = 20;

        g2.setColor(bg);
        g2.fillRoundRect(x, y, badgeW, badgeH, 6, 6);
        g2.setColor(fg);
        g2.drawString(text, x + 7, y + 14);
    }
}
