package de.tki.comfyuicompanion.ui.video;

import de.tki.comfyuicompanion.domain.Scene;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SceneEditDialogTest {

    @Test
    void testSceneEditDialogSavesPromptAndKeyframe(@TempDir Path tempDir) throws IOException, InterruptedException, InvocationTargetException {
        File keyframeFile = tempDir.resolve("knight_keyframe.png").toFile();
        assertTrue(keyframeFile.createNewFile());

        Scene existing = new Scene("S1", "Original shot prompt");
        AtomicReference<Scene> savedScene = new AtomicReference<>();

        final SceneEditDialog[] holder = new SceneEditDialog[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new SceneEditDialog(
                    new JPanel(),
                    existing,
                    false,
                    1,
                    1920,
                    1080,
                    null,
                    savedScene::set,
                    null
            );
        });

        SceneEditDialog dialog = holder[0];
        assertNotNull(dialog);

        // Find visual prompt area and change it
        JTextArea promptArea = findFirstComponent(dialog, JTextArea.class);
        assertNotNull(promptArea);
        promptArea.setText("Epic knight standing on a misty mountain ridge");

        // Find keyframe text field and set it
        JTextField keyframeField = findFirstKeyframeField(dialog);
        assertNotNull(keyframeField);
        keyframeField.setText(keyframeFile.getAbsolutePath());

        // Find Save button and click it
        JButton saveBtn = findButtonByText(dialog, "Save");
        assertNotNull(saveBtn);
        saveBtn.doClick();

        assertNotNull(savedScene.get());
        assertEquals("S1", savedScene.get().getSceneId());
        assertEquals("Epic knight standing on a misty mountain ridge", savedScene.get().getPrompt());
        assertEquals(keyframeFile.getAbsolutePath(), savedScene.get().getSpeakerImagePath());
        dialog.dispose();
    }

    @SuppressWarnings("unchecked")
    private <T extends Component> T findFirstComponent(Container container, Class<T> clazz) {
        for (Component c : container.getComponents()) {
            if (clazz.isInstance(c)) {
                return (T) c;
            }
            if (c instanceof Container child) {
                T found = findFirstComponent(child, clazz);
                if (found != null) return found;
            }
        }
        return null;
    }

    private JTextField findFirstKeyframeField(Container container) {
        for (Component c : container.getComponents()) {
            if (c instanceof JTextField tf && tf.getColumns() != 20) {
                // Return non-id field
                if (!"S1".equals(tf.getText())) {
                    return tf;
                }
            }
            if (c instanceof Container child) {
                JTextField found = findFirstKeyframeField(child);
                if (found != null) return found;
            }
        }
        return null;
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
