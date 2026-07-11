package de.tki.comfymodels.ui;

import org.springframework.test.util.ReflectionTestUtils;
import javax.swing.JPanel;

public class BlueprintGalleryTabTestHelper {
    public static JPanel getGalleryWrapper(BlueprintGalleryTab tab) {
        return (JPanel) ReflectionTestUtils.getField(tab, "galleryWrapper");
    }

    public static void applyFilter(BlueprintGalleryTab tab) {
        ReflectionTestUtils.invokeMethod(tab, "applyFilter");
    }
}
