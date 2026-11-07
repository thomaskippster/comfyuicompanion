package de.tki.comfyuicompanion.ui.dialog;

import de.tki.comfyuicompanion.service.impl.ConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JButton;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecentGenerationsAssetPickerDialogTest {

    @Test
    void testPickerDialogScanningAndSelection(@TempDir Path tempDir) throws IOException, InterruptedException, InvocationTargetException {
        File img1 = tempDir.resolve("render_001.png").toFile();
        File img2 = tempDir.resolve("render_002.jpg").toFile();
        File txt = tempDir.resolve("notes.txt").toFile();
        assertTrue(img1.createNewFile());
        assertTrue(img2.createNewFile());
        assertTrue(txt.createNewFile());

        ConfigService configService = mock(ConfigService.class);
        when(configService.getResolvedOutputDir()).thenReturn(tempDir.toAbsolutePath().toString());

        final RecentGenerationsAssetPickerDialog[] holder = new RecentGenerationsAssetPickerDialog[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new RecentGenerationsAssetPickerDialog(null, configService, "Test Asset Picker");
        });

        RecentGenerationsAssetPickerDialog dialog = holder[0];
        assertNotNull(dialog);
        assertEquals("Test Asset Picker", dialog.getTitle());

        // Wait a brief moment for async load
        Thread.sleep(200);

        // Cancel button clears selection
        JButton cancelBtn = findButtonByText(dialog, "Cancel");
        assertNotNull(cancelBtn);
        cancelBtn.doClick();

        assertNull(dialog.getSelectedFile());
        dialog.dispose();
    }

    private JButton findButtonByText(Container container, String textFragment) {
        for (Component c : container.getComponents()) {
            if (c instanceof JButton btn && btn.getText() != null && btn.getText().contains(textFragment)) {
                return btn;
            }
            if (c instanceof Container child) {
                JButton found = findButtonByText(child, textFragment);
                if (found != null) return found;
            }
        }
        return null;
    }
}
