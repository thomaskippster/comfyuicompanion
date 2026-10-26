package de.tki.comfyuicompanion.ui;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import java.awt.Image;

/**
 * Utility handling proportional scaling and rendering of generated image previews
 * within the Prompt Lab interface.
 */
public final class PromptLabImagePreviewScaler {

    private PromptLabImagePreviewScaler() {
        // Utility class
    }

    /**
     * Scales an image proportionally to fit within the preview label while maintaining aspect ratio,
     * updating the label's icon without unnecessary re-scalings.
     *
     * @param label     the target preview label
     * @param img       the source image to render
     * @param sameImage true if the image reference has not changed
     */
    public static void scaleAndRender(JLabel label, Image img, boolean sameImage) {
        if (label == null || img == null) {
            return;
        }

        int pw = Math.max(64, label.getWidth() > 8 ? label.getWidth() - 8 : 512);
        int ph = Math.max(64, label.getHeight() > 8 ? label.getHeight() - 8 : 512);
        int iw = img.getWidth(null);
        int ih = img.getHeight(null);
        if (iw <= 0 || ih <= 0) {
            return;
        }

        double scale = Math.min((double) pw / iw, (double) ph / ih);
        int tw = Math.max(1, (int) (iw * scale));
        int th = Math.max(1, (int) (ih * scale));
        if (sameImage && label.getIcon() instanceof ImageIcon existing
                && Math.abs(existing.getIconWidth() - tw) <= 2 && Math.abs(existing.getIconHeight() - th) <= 2) {
            return;
        }
        Image scaled = img.getScaledInstance(tw, th, Image.SCALE_SMOOTH);
        label.setIcon(new ImageIcon(scaled));
        label.setText("");
    }
}
