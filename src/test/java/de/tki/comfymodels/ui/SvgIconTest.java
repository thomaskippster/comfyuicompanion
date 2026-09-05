package de.tki.comfymodels.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import de.tki.comfymodels.ui.icons.AppIcon;
import de.tki.comfymodels.ui.icons.SvgIconFactory;
import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

public class SvgIconTest {

    @Test
    public void testAllAppIconsExistAndLoadSuccessfully() {
        for (AppIcon appIcon : AppIcon.values()) {
            FlatSVGIcon icon = SvgIconFactory.get(appIcon);
            assertNotNull(icon, "Icon must not be null for " + appIcon);
            assertTrue(icon.hasFound(), "SVG asset must be found in classpath: " + appIcon.getResourcePath());
            assertEquals(SvgIconFactory.SIZE_DEFAULT, icon.getIconWidth());
            assertEquals(SvgIconFactory.SIZE_DEFAULT, icon.getIconHeight());
        }
    }

    @Test
    public void testDerivedAndColoredIcons() {
        FlatSVGIcon tabIcon = SvgIconFactory.get(AppIcon.DASHBOARD, SvgIconFactory.SIZE_TAB);
        assertEquals(SvgIconFactory.SIZE_TAB, tabIcon.getIconWidth());
        assertEquals(SvgIconFactory.SIZE_TAB, tabIcon.getIconHeight());

        FlatSVGIcon coloredIcon = SvgIconFactory.get(AppIcon.QUICK_CHECK, 20, 20, Color.CYAN);
        assertNotNull(coloredIcon);
        assertEquals(20, coloredIcon.getIconWidth());
    }

    @Test
    public void testStatusDotCreation() {
        Icon dot = SvgIconFactory.createStatusDot(Color.GREEN, 10);
        assertNotNull(dot);
        assertEquals(10, dot.getIconWidth());
        assertEquals(10, dot.getIconHeight());
    }
}
