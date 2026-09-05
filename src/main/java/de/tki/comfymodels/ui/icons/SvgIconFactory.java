package de.tki.comfymodels.ui.icons;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central generic factory for scalable SVG vector icons.
 * Caches derived FlatSVGIcon instances to optimize memory usage and performance.
 */
public final class SvgIconFactory {

    private static final Logger logger = LoggerFactory.getLogger(SvgIconFactory.class);

    public static final int SIZE_SMALL = 14;
    public static final int SIZE_DEFAULT = 16;
    public static final int SIZE_TAB = 18;
    public static final int SIZE_LARGE = 24;

    private static final Map<IconCacheKey, FlatSVGIcon> CACHE = new ConcurrentHashMap<>();

    // Java 21 record for cache keys
    private record IconCacheKey(AppIcon icon, int width, int height, Color color) {}

    private SvgIconFactory() {
        // Utility class
    }

    /**
     * Resolves an icon with default dimensions (16x16).
     */
    public static FlatSVGIcon get(AppIcon icon) {
        return get(icon, SIZE_DEFAULT, SIZE_DEFAULT, null);
    }

    /**
     * Resolves an icon with square dimensions (size x size).
     */
    public static FlatSVGIcon get(AppIcon icon, int size) {
        return get(icon, size, size, null);
    }

    /**
     * Resolves an icon with custom dimensions.
     */
    public static FlatSVGIcon get(AppIcon icon, int width, int height) {
        return get(icon, width, height, null);
    }

    /**
     * Resolves an icon with custom dimensions and optional color filter.
     */
    public static FlatSVGIcon get(AppIcon icon, int width, int height, Color color) {
        if (icon == null) {
            return null;
        }

        IconCacheKey key = new IconCacheKey(icon, width, height, color);
        return CACHE.computeIfAbsent(key, SvgIconFactory::createIcon);
    }

    private static FlatSVGIcon createIcon(IconCacheKey key) {
        try {
            FlatSVGIcon svgIcon = new FlatSVGIcon(key.icon().getResourcePath(), key.width(), key.height());
            if (!svgIcon.hasFound()) {
                logger.warn("SVG icon not found at classpath resource: {}", key.icon().getResourcePath());
            }
            if (key.color() != null) {
                svgIcon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> key.color()));
            }
            return svgIcon;
        } catch (Exception ex) {
            logger.error("Failed to load SVG icon {}: {}", key.icon(), ex.getMessage(), ex);
            return new FlatSVGIcon("icons/svg/circle.svg", key.width(), key.height());
        }
    }

    /**
     * Creates a cleanly anti-aliased status indicator circle icon (e.g. green/yellow/red).
     */
    public static Icon createStatusDot(Color color, int diameter) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(color);
                    g2.fillOval(x + 1, y + 1, diameter - 2, diameter - 2);
                } finally {
                    g2.dispose();
                }
            }

            @Override
            public int getIconWidth() {
                return diameter;
            }

            @Override
            public int getIconHeight() {
                return diameter;
            }
        };
    }
}
