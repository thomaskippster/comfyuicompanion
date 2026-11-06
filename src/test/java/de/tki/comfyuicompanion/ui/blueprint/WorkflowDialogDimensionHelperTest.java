package de.tki.comfyuicompanion.ui.blueprint;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JDialog;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowDialogDimensionHelperTest {

    @Test
    @DisplayName("calculateTablePreferredHeight bounds heights between min and max")
    void testCalculateTablePreferredHeight() {
        // Zero or single item should respect MIN_TABLE_HEIGHT (100)
        assertEquals(100, WorkflowDialogDimensionHelper.calculateTablePreferredHeight(0));
        assertEquals(100, WorkflowDialogDimensionHelper.calculateTablePreferredHeight(1));

        // 18 items (like in user screenshot) -> (18 + 1) * 26 + 6 = 500
        assertEquals(500, WorkflowDialogDimensionHelper.calculateTablePreferredHeight(18));

        // Very large model list should be capped at MAX_TABLE_HEIGHT (750)
        assertEquals(750, WorkflowDialogDimensionHelper.calculateTablePreferredHeight(50));
    }

    @Test
    @DisplayName("calculateOptimalDialogSize calculates spacious non-clipping dimensions")
    void testCalculateOptimalDialogSize() {
        Dimension smallSize = WorkflowDialogDimensionHelper.calculateOptimalDialogSize(null, false, 5, 2, false);
        assertNotNull(smallSize);
        Rectangle screen = WorkflowDialogDimensionHelper.getUsableScreenBounds(null);
        int expectedWidth = Math.min((int) (screen.width * 0.95), Math.max(1260, 1320));
        assertEquals(expectedWidth, smallSize.width);
        assertTrue(smallSize.height >= Math.min((int) (screen.height * 0.90), 520));

        Dimension largeSize = WorkflowDialogDimensionHelper.calculateOptimalDialogSize(null, true, 10, 18, true);
        assertNotNull(largeSize);
        assertEquals(expectedWidth, largeSize.width);
        assertTrue(largeSize.height >= smallSize.height);

        assertTrue(largeSize.height <= screen.height);
        assertTrue(largeSize.width <= screen.width);
    }

    @Test
    @DisplayName("applyOptimalSizeAndLocation resizes dialog and keeps it on screen")
    void testApplyOptimalSizeAndLocation() {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }

        JDialog dialog = new JDialog();
        try {
            WorkflowDialogDimensionHelper.applyOptimalSizeAndLocation(dialog, null, true, 8, 18, false);

            Rectangle screen = WorkflowDialogDimensionHelper.getUsableScreenBounds(null);
            int expectedWidth = Math.min((int) (screen.width * 0.95), Math.max(1260, 1320));
            assertEquals(expectedWidth, dialog.getWidth());
            assertTrue(dialog.getHeight() >= Math.min((int) (screen.height * 0.90), 800));

            assertTrue(dialog.getX() >= screen.x);
            assertTrue(dialog.getY() >= screen.y);
            assertTrue(dialog.getX() + dialog.getWidth() <= screen.x + screen.width);
            assertTrue(dialog.getY() + dialog.getHeight() <= screen.y + screen.height);
        } finally {
            dialog.dispose();
        }
    }
}
