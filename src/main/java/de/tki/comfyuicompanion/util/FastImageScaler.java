package de.tki.comfyuicompanion.util;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * High-performance hardware-accelerated image scaling utility.
 * Replaces un-accelerated {@link Image#getScaledInstance(int, int, int)} with
 * GPU-accelerated Java2D bilinear rendering hints to avoid UI freezes and memory spikes.
 */
public final class FastImageScaler {

    private FastImageScaler() {
        // Utility class
    }

    /**
     * Scales a source image to the target dimensions using hardware-accelerated Java2D Graphics.
     *
     * @param src     the source image
     * @param targetW target width (must be > 0)
     * @param targetH target height (must be > 0)
     * @param smooth  true for high-quality bilinear interpolation, false for nearest-neighbor
     * @return the scaled BufferedImage, or null if input is invalid
     */
    public static BufferedImage scale(Image src, int targetW, int targetH, boolean smooth) {
        if (src == null || targetW <= 0 || targetH <= 0) {
            return null;
        }

        int type = BufferedImage.TYPE_INT_ARGB;
        if (src instanceof BufferedImage bi && !bi.getColorModel().hasAlpha()) {
            type = BufferedImage.TYPE_INT_RGB;
        }

        BufferedImage dest = new BufferedImage(targetW, targetH, type);
        Graphics2D g2 = dest.createGraphics();
        try {
            if (smooth) {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            } else {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            }
            g2.drawImage(src, 0, 0, targetW, targetH, null);
        } finally {
            g2.dispose();
        }
        return dest;
    }
}
