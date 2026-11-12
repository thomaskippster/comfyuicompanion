package de.tki.comfyuicompanion.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/**
 * FlowLayout subclass that fully supports component wrapping across lines
 * based on the target container's current width.
 */
public class WrapLayout extends FlowLayout {

    /**
     * Constructs a new WrapLayout with the specified alignment and gaps.
     *
     * @param align the alignment value
     * @param hgap  the horizontal gap between components
     * @param vgap  the vertical gap between components
     */
    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        synchronized (target.getTreeLock()) {
            int targetWidth = target.getSize().width;
            if (targetWidth == 0) {
                targetWidth = Integer.MAX_VALUE;
            }

            Insets insets = target.getInsets();
            int maxWidth = targetWidth - (insets.left + insets.right + getHgap() * 2);

            int x = 0;
            int y = 0;
            int height = 0;
            for (Component c : target.getComponents()) {
                if (c.isVisible()) {
                    Dimension d = c.getPreferredSize();
                    if (x + d.width > maxWidth) {
                        x = 0;
                        y += height + getVgap();
                        height = 0;
                    }
                    x += d.width + getHgap();
                    height = Math.max(height, d.height);
                }
            }
            return new Dimension(targetWidth, y + height + insets.top + insets.bottom + getVgap() * 2);
        }
    }
}
