package de.tki.comfyuicompanion.service.prompt.sanitization;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Derives latent image dimensions from the literal inputs of an arbitrary (unknown) latent provider
 * node, e.g. explicit {@code width/height} values, resolution labels such as {@code "832x1216"} or
 * aspect ratio labels such as {@code "16:9"}.
 */
final class LatentDimensionResolver {

    /** Dimensions in pixels. */
    record Dimensions(int width, int height) {
    }

    private static final Pattern RESOLUTION = Pattern.compile("(\\d{3,5})\\s*[x×*]\\s*(\\d{3,5})");
    private static final Pattern ASPECT_RATIO = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*:\\s*(\\d+(?:\\.\\d+)?)");
    private static final int TARGET_PIXELS = 1024 * 1024;
    private static final int ALIGNMENT = 64;

    private LatentDimensionResolver() {
    }

    /**
     * Resolves dimensions from literal node inputs in order of reliability.
     *
     * @param literals literal inputs of the missing node
     * @return resolved dimensions, or empty if no hint is present
     */
    static Optional<Dimensions> resolve(Map<String, Object> literals) {
        Integer w = intValue(literals.get("width"));
        Integer h = intValue(literals.get("height"));
        if (w != null && h != null && w > 0 && h > 0) return Optional.of(new Dimensions(w, h));

        for (Object v : literals.values()) {
            if (v instanceof String s) {
                Matcher m = RESOLUTION.matcher(s);
                if (m.find()) {
                    return Optional.of(new Dimensions(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))));
                }
            }
        }
        for (Object v : literals.values()) {
            if (v instanceof String s) {
                Matcher m = ASPECT_RATIO.matcher(s);
                if (m.find()) {
                    return fromAspectRatio(Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)));
                }
            }
        }
        return Optional.empty();
    }

    /** Distributes a ~1 megapixel budget over the aspect ratio, aligned to the latent grid. */
    private static Optional<Dimensions> fromAspectRatio(double rw, double rh) {
        if (rw <= 0 || rh <= 0) return Optional.empty();
        double height = Math.sqrt(TARGET_PIXELS * rh / rw);
        double width = height * rw / rh;
        return Optional.of(new Dimensions(align(width), align(height)));
    }

    private static int align(double value) {
        return Math.max(ALIGNMENT, (int) Math.round(value / ALIGNMENT) * ALIGNMENT);
    }

    private static Integer intValue(Object v) {
        return v instanceof Number n ? n.intValue() : null;
    }
}
