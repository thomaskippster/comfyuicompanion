package de.tki.comfymodels.ui;

import de.tki.comfymodels.ui.icons.AppIcon;
import de.tki.comfymodels.ui.icons.SvgIconFactory;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * Generic, enterprise-ready TableCellRenderer for the Download Manager table.
 * Translates semantic status and AI source states into high-resolution SVG vector icons,
 * color-coded typography, and sanitized text (stripping legacy Unicode emoji boxes).
 */
public class DownloadManagerTableCellRenderer extends DefaultTableCellRenderer {

    // Semantic palette definitions
    public static final Color COLOR_SUCCESS = new Color(0, 180, 160);
    public static final Color COLOR_KNOWN_GOOD = new Color(30, 190, 170);
    public static final Color COLOR_ARCHIVE = new Color(224, 169, 109);
    public static final Color COLOR_WARNING = new Color(226, 140, 60);
    public static final Color COLOR_ERROR = new Color(226, 88, 88);
    public static final Color COLOR_SEARCH = new Color(100, 180, 245);
    public static final Color COLOR_AI_VERIFIED = new Color(80, 160, 240);
    public static final Color COLOR_AI_PREDICTED = new Color(175, 120, 240);
    public static final Color COLOR_COMMUNITY = new Color(140, 150, 165);
    public static final Color COLOR_ACCENT = new Color(0, 190, 220);

    /**
     * Java 21 Record encapsulating the resolved visual representation of a cell.
     */
    public record CellPresentation(AppIcon icon, Color iconColor, String cleanText, Color textColor) {
        public CellPresentation(AppIcon icon, Color iconColor, String cleanText) {
            this(icon, iconColor, cleanText, null);
        }

        public CellPresentation(String cleanText) {
            this(null, null, cleanText, null);
        }
    }

    public DownloadManagerTableCellRenderer() {
        setIconTextGap(6);
        setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                   boolean hasFocus, int row, int column) {
        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        int modelColumn = table.convertColumnIndexToModel(column);
        CellPresentation presentation = resolvePresentation(modelColumn, value);

        if (presentation.icon() != null) {
            // Invert or omit custom filter when row is selected to preserve readability with selection background
            Color appliedIconColor = isSelected ? null : presentation.iconColor();
            setIcon(SvgIconFactory.get(presentation.icon(), SvgIconFactory.SIZE_SMALL, SvgIconFactory.SIZE_SMALL, appliedIconColor));
        } else {
            setIcon(null);
        }

        setText(presentation.cleanText());

        if (!isSelected) {
            if (presentation.textColor() != null) {
                c.setForeground(presentation.textColor());
            } else {
                c.setForeground(table.getForeground());
            }
        }

        setToolTipText(presentation.cleanText().isBlank() ? null : presentation.cleanText());
        return c;
    }

    /**
     * Resolves the presentation metadata based on model column index and cell value.
     */
    public static CellPresentation resolvePresentation(int modelColumn, Object value) {
        if (value == null) {
            return new CellPresentation("");
        }

        String raw = value.toString();

        return switch (modelColumn) {
            case 4 -> resolveAiSource(raw);
            case 7 -> resolveStatus(raw);
            case 6 -> resolveUrl(raw);
            case 5 -> resolveTargetPath(raw);
            default -> new CellPresentation(stripEmojis(raw));
        };
    }

    /**
     * Resolves icons and text for Column 4 (AI Source / Popularity).
     */
    public static CellPresentation resolveAiSource(String raw) {
        String clean = stripEmojis(raw);
        String upper = raw.toUpperCase();

        if (upper.contains("MODEL LIST MATCH") || upper.contains("USER DEFINED") || upper.contains("BLUEPRINT REQUIRED")) {
            return new CellPresentation(AppIcon.FOLDER, COLOR_SUCCESS, clean, COLOR_SUCCESS);
        }
        if (raw.contains("Context:") || upper.contains("CONTEXT:")) {
            return new CellPresentation(AppIcon.SUGGEST, COLOR_ACCENT, clean, null);
        }
        if (raw.contains("AI Verified:") || upper.contains("AI VERIFIED")) {
            return new CellPresentation(AppIcon.AI, COLOR_AI_VERIFIED, clean, COLOR_AI_VERIFIED);
        }
        if (raw.contains("AI Predicted:") || upper.contains("AI PREDICTED")) {
            return new CellPresentation(AppIcon.AI, COLOR_AI_PREDICTED, clean, COLOR_AI_PREDICTED);
        }
        if (raw.contains("Community") || upper.contains("COMMUNITY")) {
            return new CellPresentation(AppIcon.USER, COLOR_COMMUNITY, clean, COLOR_COMMUNITY);
        }
        if (upper.contains("UPDATE:")) {
            return new CellPresentation(AppIcon.REFRESH, COLOR_WARNING, clean, COLOR_WARNING);
        }

        // Emoji-based fallbacks if custom keywords are present
        if (raw.contains("📂")) {
            return new CellPresentation(AppIcon.FOLDER, COLOR_SUCCESS, clean, null);
        }
        if (raw.contains("✨")) {
            return new CellPresentation(AppIcon.SUGGEST, COLOR_ACCENT, clean, null);
        }
        if (raw.contains("🧠") || raw.contains("🎯")) {
            return new CellPresentation(AppIcon.AI, COLOR_AI_VERIFIED, clean, null);
        }
        if (raw.contains("👥")) {
            return new CellPresentation(AppIcon.USER, COLOR_COMMUNITY, clean, null);
        }
        if (raw.contains("⭐")) {
            return new CellPresentation(AppIcon.REFRESH, COLOR_WARNING, clean, null);
        }

        return new CellPresentation(clean);
    }

    /**
     * Resolves icons, colors, and sanitized text for Column 7 (Status).
     */
    public static CellPresentation resolveStatus(String raw) {
        String clean = stripEmojis(raw);

        if (raw.contains("Already exists") || raw.contains("Finished")) {
            return new CellPresentation(AppIcon.CHECK, COLOR_SUCCESS, clean, COLOR_SUCCESS);
        }
        if (raw.contains("Known Good")) {
            return new CellPresentation(AppIcon.CHECK, COLOR_KNOWN_GOOD, clean, COLOR_KNOWN_GOOD);
        }
        if (raw.contains("Restoring")) {
            return new CellPresentation(AppIcon.ARCHIVE, COLOR_ARCHIVE, clean, COLOR_ARCHIVE);
        }
        if (raw.contains("Archived")) {
            return new CellPresentation(AppIcon.ARCHIVE, COLOR_ARCHIVE, clean, COLOR_ARCHIVE);
        }
        if (raw.contains("Size Mismatch")) {
            return new CellPresentation(AppIcon.REFRESH, COLOR_ERROR, clean, COLOR_ERROR);
        }
        if (raw.contains("Retrying") || raw.contains("Fixing") || raw.contains("resuming")) {
            return new CellPresentation(AppIcon.REFRESH, COLOR_WARNING, clean, COLOR_WARNING);
        }
        if (raw.contains("Error") || raw.contains("Failed") || raw.contains("Validation failed")
                || raw.contains("Auth Required") || raw.contains("No trusted match")
                || raw.contains("No Space") || raw.contains("LFS Stub")) {
            return new CellPresentation(AppIcon.CLOSE, COLOR_ERROR, clean, COLOR_ERROR);
        }
        if (raw.contains("Searching") || raw.contains("Hashing")) {
            return new CellPresentation(AppIcon.SEARCH, COLOR_SEARCH, clean, COLOR_SEARCH);
        }
        if (raw.contains("Found in Model List")) {
            return new CellPresentation(AppIcon.FOLDER, COLOR_SUCCESS, clean, COLOR_SUCCESS);
        }
        if (raw.equalsIgnoreCase("Idle")) {
            return new CellPresentation(null, null, clean, ThemeManager.getTextSecondaryColor());
        }
        if (raw.startsWith("Skipped") || raw.equalsIgnoreCase("Stopped")) {
            return new CellPresentation(AppIcon.STOP, ThemeManager.getTextSecondaryColor(), clean, ThemeManager.getTextSecondaryColor());
        }
        if (raw.contains("Downloading") || raw.contains("%") || raw.contains("MB/s") || raw.contains("KB/s") || raw.contains("GB/s")) {
            return new CellPresentation(AppIcon.DOWNLOAD, COLOR_ACCENT, clean, COLOR_ACCENT);
        }

        // Emoji glyph fallbacks
        if (raw.contains("✅")) {
            return new CellPresentation(AppIcon.CHECK, COLOR_SUCCESS, clean, COLOR_SUCCESS);
        }
        if (raw.contains("📦")) {
            return new CellPresentation(AppIcon.ARCHIVE, COLOR_ARCHIVE, clean, COLOR_ARCHIVE);
        }
        if (raw.contains("🔄")) {
            return new CellPresentation(AppIcon.REFRESH, COLOR_WARNING, clean, COLOR_WARNING);
        }
        if (raw.contains("❌")) {
            return new CellPresentation(AppIcon.CLOSE, COLOR_ERROR, clean, COLOR_ERROR);
        }
        if (raw.contains("🔍")) {
            return new CellPresentation(AppIcon.SEARCH, COLOR_SEARCH, clean, COLOR_SEARCH);
        }
        if (raw.contains("📂")) {
            return new CellPresentation(AppIcon.FOLDER, COLOR_SUCCESS, clean, COLOR_SUCCESS);
        }

        return new CellPresentation(clean);
    }

    private static CellPresentation resolveUrl(String raw) {
        String clean = stripEmojis(raw);
        return new CellPresentation(clean);
    }

    private static CellPresentation resolveTargetPath(String raw) {
        String clean = stripEmojis(raw);
        return new CellPresentation(clean);
    }

    /**
     * Strips all Unicode emojis, pictographs, and dingbats that would render as broken boxes in Swing.
     * Uses explicit Unicode code-point filtering to guarantee normal punctuation and symbols (hyphens, dots)
     * are never modified.
     */
    public static String stripEmojis(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> {
            boolean isEmoji = (cp >= 0x1F000 && cp <= 0x1FAFF)
                    || (cp >= 0x2600 && cp <= 0x27BF)
                    || (cp >= 0x2300 && cp <= 0x23FF)
                    || (cp >= 0x2B50 && cp <= 0x2B55)
                    || (cp == 0x200D || cp == 0xFE0E || cp == 0xFE0F);
            if (!isEmoji) {
                sb.appendCodePoint(cp);
            }
        });

        return sb.toString()
                .replaceAll("\\(\\s+", "(")
                .replaceAll("\\s+\\)", ")")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }
}
