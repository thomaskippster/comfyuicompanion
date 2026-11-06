package de.tki.comfyuicompanion.ui.blueprint;

import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import javax.swing.JDialog;

/**
 * Enterprise utility coordinator for dynamically calculating and applying optimal dialog
 * dimensions and table heights for workflow blueprint and model details dialogs.
 * Prevents vertical clipping and internal nested scrollbars by inspecting content metrics
 * and adhering to monitor work-area boundaries.
 *
 * @author Senior Java Developer
 */
public final class WorkflowDialogDimensionHelper {

    private static final int BASE_CHROME_HEIGHT = 45;
    private static final int ROOT_BORDER_PADDING = 34;
    private static final int HEADER_BAR_HEIGHT = 45;
    private static final int HEADER_BOTTOM_GAP = 12;
    private static final int ROW_HEIGHT_ESTIMATE = 24;
    private static final int METADATA_BOTTOM_GAP = 12;
    private static final int SECTION_HEADER_HEIGHT = 30;
    private static final int PROMPT_AREA_HEIGHT = 85;
    private static final int FOOTER_BAR_HEIGHT = 55;

    private static final int TABLE_ROW_HEIGHT = 26;
    private static final int TABLE_EXTRA_PADDING = 6;
    private static final int MIN_TABLE_HEIGHT = 100;
    private static final int MAX_TABLE_HEIGHT = 750;

    private static final int MIN_DIALOG_WIDTH = 1260;
    private static final int DEFAULT_DIALOG_WIDTH = 1320;

    private WorkflowDialogDimensionHelper() {
        // Private constructor for static utility class
    }

    /**
     * Calculates the optimal banner height to fit comfortably alongside all model table rows
     * and metadata on the current display work-area.
     *
     * @param owner parent window
     * @param metadataRowCount count of key-value metadata rows
     * @param tableItemCount count of rows displayed in the models/files table
     * @param hasPromptArea whether an optional positive prompt text area is included
     * @return recommended banner height in pixels (140 to 260)
     */
    public static int calculateMaxBannerHeight(Window owner, int metadataRowCount, int tableItemCount, boolean hasPromptArea) {
        Rectangle usable = getUsableScreenBounds(owner);
        int maxAllowedHeight = (int) (usable.height * 0.96);
        boolean isSideBySide = tableItemCount > 4;
        int nonBannerHeight = BASE_CHROME_HEIGHT
                + ROOT_BORDER_PADDING
                + HEADER_BAR_HEIGHT
                + HEADER_BOTTOM_GAP
                + (isSideBySide ? 0 : (Math.max(1, metadataRowCount) * ROW_HEIGHT_ESTIMATE))
                + METADATA_BOTTOM_GAP
                + SECTION_HEADER_HEIGHT
                + calculateTablePreferredHeight(tableItemCount)
                + (hasPromptArea ? PROMPT_AREA_HEIGHT : 0)
                + FOOTER_BAR_HEIGHT;

        int remainingForBanner = maxAllowedHeight - nonBannerHeight;
        return isSideBySide ? 210 : Math.max(140, Math.min(260, remainingForBanner));
    }

    /**
     * Calculates the preferred height for the model table to render all rows without an inner scrollbar.
     *
     * @param itemCount the number of model or file items displayed in the table
     * @return preferred height in pixels
     */
    public static int calculateTablePreferredHeight(int itemCount) {
        int calculated = (Math.max(1, itemCount) + 1) * TABLE_ROW_HEIGHT + TABLE_EXTRA_PADDING;
        return Math.min(MAX_TABLE_HEIGHT, Math.max(MIN_TABLE_HEIGHT, calculated));
    }

    /**
     * Determines the optimal dimension for a workflow details dialog based on its content components
     * and the available screen geometry of the target display.
     *
     * @param owner parent window (used to identify target monitor), may be null
     * @param hasBanner whether a workflow preview banner image is or will be displayed
     * @param metadataRowCount count of key-value metadata rows
     * @param tableItemCount count of rows displayed in the models/files table
     * @param hasPromptArea whether an optional positive prompt text area is included
     * @return ideal dialog Dimension fitting content without clipping
     */
    public static Dimension calculateOptimalDialogSize(
            Window owner,
            boolean hasBanner,
            int metadataRowCount,
            int tableItemCount,
            boolean hasPromptArea) {

        boolean isSideBySide = tableItemCount > 4;
        int bannerHeight = hasBanner ? (isSideBySide ? 210 : 200) : 0;
        int metaHeight = Math.max(1, metadataRowCount) * ROW_HEIGHT_ESTIMATE;
        int topHeroHeight = isSideBySide ? Math.max(bannerHeight, metaHeight) : (bannerHeight + metaHeight);

        int totalContentHeight = BASE_CHROME_HEIGHT
                + ROOT_BORDER_PADDING
                + HEADER_BAR_HEIGHT
                + HEADER_BOTTOM_GAP
                + topHeroHeight
                + METADATA_BOTTOM_GAP
                + SECTION_HEADER_HEIGHT
                + calculateTablePreferredHeight(tableItemCount)
                + (hasPromptArea ? PROMPT_AREA_HEIGHT : 0)
                + FOOTER_BAR_HEIGHT;

        Rectangle usableScreen = getUsableScreenBounds(owner);

        int maxAllowedHeight = Math.max(480, (int) (usableScreen.height * 0.90));
        int minHeight = Math.min(maxAllowedHeight, 520);
        int targetHeight = Math.min(maxAllowedHeight, Math.max(minHeight, totalContentHeight));

        int targetWidth = Math.min((int) (usableScreen.width * 0.95), Math.max(MIN_DIALOG_WIDTH, DEFAULT_DIALOG_WIDTH));

        return new Dimension(targetWidth, targetHeight);
    }

    /**
     * Applies the optimal dimension and centers the dialog relative to its owner,
     * ensuring it remains strictly inside the usable monitor bounds.
     *
     * @param dialog target JDialog to resize and position
     * @param owner parent window
     * @param hasBanner whether a preview banner is present
     * @param metadataRowCount count of metadata entries
     * @param tableItemCount count of table rows
     * @param hasPromptArea whether prompt area is present
     */
    public static void applyOptimalSizeAndLocation(
            JDialog dialog,
            Window owner,
            boolean hasBanner,
            int metadataRowCount,
            int tableItemCount,
            boolean hasPromptArea) {

        Dimension optimalSize = calculateOptimalDialogSize(owner, hasBanner, metadataRowCount, tableItemCount, hasPromptArea);
        dialog.setSize(optimalSize);
        dialog.setLocationRelativeTo(owner);

        Rectangle screen = getUsableScreenBounds(owner);
        int x = Math.max(screen.x, Math.min(dialog.getX(), screen.x + screen.width - dialog.getWidth()));
        int y = Math.max(screen.y, Math.min(dialog.getY(), screen.y + screen.height - dialog.getHeight()));
        dialog.setLocation(x, y);
    }

    /**
     * Resolves the usable desktop boundaries for the specified window or default monitor,
     * accounting for system taskbars, docks, and DPI insets.
     *
     * @param owner optional window to resolve screen device for
     * @return Rectangle representing usable screen work-area
     */
    public static Rectangle getUsableScreenBounds(Window owner) {
        GraphicsConfiguration gc = null;
        if (owner != null) {
            gc = owner.getGraphicsConfiguration();
        }
        if (gc == null) {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            if (!GraphicsEnvironment.isHeadless() && ge.getDefaultScreenDevice() != null) {
                gc = ge.getDefaultScreenDevice().getDefaultConfiguration();
            }
        }
        if (gc == null) {
            return new Rectangle(0, 0, 1920, 1080);
        }

        Rectangle bounds = gc.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        return new Rectangle(
                bounds.x + insets.left,
                bounds.y + insets.top,
                Math.max(800, bounds.width - insets.left - insets.right),
                Math.max(600, bounds.height - insets.top - insets.bottom)
        );
    }
}
