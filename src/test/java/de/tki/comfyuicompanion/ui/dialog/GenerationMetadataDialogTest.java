package de.tki.comfyuicompanion.ui.dialog;

import de.tki.comfyuicompanion.domain.ComfyGenerationMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JButton;
import javax.swing.JDialog;
import java.awt.Component;
import java.awt.Container;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class GenerationMetadataDialogTest {

    @Test
    void testDialogInitializationAndParameterDisplay(@TempDir Path tempDir) throws IOException {
        File dummyPng = tempDir.resolve("test_render.png").toFile();
        assertTrue(dummyPng.createNewFile());

        ComfyGenerationMetadata meta = new ComfyGenerationMetadata(
                "cinematic dragon soaring through storm clouds",
                "blurry, dark",
                "flux1-dev.safetensors",
                882244L,
                30,
                3.5,
                "euler",
                "normal",
                1024,
                768,
                "{}",
                "{}"
        );

        AtomicBoolean applied = new AtomicBoolean(false);

        GenerationMetadataDialog dialog = new GenerationMetadataDialog(null, dummyPng, meta, m -> {
            applied.set(true);
            assertEquals(882244L, m.seed());
        });

        assertNotNull(dialog);
        assertEquals("Generation Metadata: test_render.png", dialog.getTitle());

        JButton applyBtn = findButtonByText(dialog, "Apply to Image Lab");
        assertNotNull(applyBtn, "Apply to Image Lab button should be present when parameters exist");
        applyBtn.doClick();

        assertTrue(applied.get(), "Clicking Apply to Image Lab should invoke consumer callback");
        dialog.dispose();
    }

    @Test
    void testDialogWithEmptyMetadata(@TempDir Path tempDir) throws IOException {
        File dummyPng = tempDir.resolve("empty.png").toFile();
        assertTrue(dummyPng.createNewFile());

        GenerationMetadataDialog dialog = new GenerationMetadataDialog(null, dummyPng, ComfyGenerationMetadata.empty(), null);
        assertNotNull(dialog);

        JButton applyBtn = findButtonByText(dialog, "Apply to Image Lab");
        assertNull(applyBtn, "Apply button should NOT be present when metadata has no parameters");
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
