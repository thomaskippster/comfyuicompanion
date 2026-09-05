package de.tki.comfymodels.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SvgIconTest {

    @Test
    public void testLoadSvgIcon() {
        FlatSVGIcon icon = new FlatSVGIcon("icons/svg/test.svg", 16, 16);
        assertTrue(icon.hasFound(), "Icon should be found in classpath");
        assertEquals(16, icon.getIconWidth());
        assertEquals(16, icon.getIconHeight());
    }
}
