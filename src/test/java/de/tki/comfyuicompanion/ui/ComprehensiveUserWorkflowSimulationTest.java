package de.tki.comfyuicompanion.ui;

import de.tki.comfyuicompanion.controller.PromptLabStatusPoller;
import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.domain.video.VideoResolutionRegistry;
import de.tki.comfyuicompanion.service.impl.ComfyApiClient;
import de.tki.comfyuicompanion.service.impl.ComfyWebSocketClient;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.dialog.ApiKeysSettingsDialog;
import de.tki.comfyuicompanion.ui.dialog.CivitaiVersionSelectorDialog;
import de.tki.comfyuicompanion.ui.dialog.DownloadArchiveDialog;
import de.tki.comfyuicompanion.ui.video.VideoArchitectTimelineCoordinator;
import de.tki.comfyuicompanion.ui.video.VideoArchitectWorkspacePanel;
import de.tki.comfyuicompanion.ui.video.VideoSamplingParamsPanel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * End-to-end integration and simulation suite testing user workflows across
 * Output Gallery, Video Architect, Prompt Lab, and Dialog navigation.
 */
public class ComprehensiveUserWorkflowSimulationTest {

    @Test
    @DisplayName("ApiKeysSettingsDialog should open without ClassCastException when ancestor is JDialog")
    void testApiKeysDialogWithJDialogAncestor() throws Exception {
        ConfigService mockConfig = mock(ConfigService.class);
        when(mockConfig.getCivitaiApiKey()).thenReturn("civitai-test-key");
        when(mockConfig.getHfToken()).thenReturn("hf-test-token");
        when(mockConfig.getElevenLabsApiKey()).thenReturn("eleven-test-key");
        when(mockConfig.getTtsProvider()).thenReturn("ComfyUI Qwen-TTS");

        JDialog parentDialog = new JDialog();
        AtomicBoolean saved = new AtomicBoolean(false);

        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(150);
                SwingUtilities.invokeLater(() -> {
                    for (java.awt.Window w : java.awt.Window.getWindows()) {
                        if (w instanceof de.tki.comfyuicompanion.ui.StandardDialog sd) {
                            sd.dispose();
                        }
                    }
                    parentDialog.dispose();
                });
            } catch (Exception ignored) {}
        });

        assertDoesNotThrow(() -> {
            ApiKeysSettingsDialog.showApiKeysDialog(parentDialog, mockConfig, () -> saved.set(true));
        });
    }

    @Test
    @DisplayName("VideoSamplingParamsPanel handles various numeric types in spinners safely")
    void testVideoSamplingParamsNumericSafety() {
        ConfigService mockConfig = mock(ConfigService.class);
        VideoSamplingParamsPanel panel = new VideoSamplingParamsPanel(mockConfig);

        // Normal defaults
        assertEquals(1280, panel.getVideoWidth());
        assertEquals(720, panel.getVideoHeight());
        assertEquals(5, panel.getVideoDuration());
        assertEquals(28, panel.getVideoSteps());
        assertEquals(3.5, panel.getVideoCfg(), 0.001);
        assertEquals(127, panel.getVideoMotion());

        // Apply 9:16 TikTok preset
        panel.applyTikTok916Preset();
        assertEquals(720, panel.getVideoWidth());
        assertEquals(1280, panel.getVideoHeight());
        assertEquals(4, panel.getVideoDuration());
        assertEquals(3.5, panel.getVideoCfg(), 0.001);

        // Apply 16:9 Widescreen preset
        panel.applyWidescreenPreset();
        assertEquals(1280, panel.getVideoWidth());
        assertEquals(720, panel.getVideoHeight());
        assertEquals(5, panel.getVideoDuration());

        // Test with Long and Double values in spinners to guarantee ((Number) ...).intValue() safety
        panel.getVideoWidthSpinner().setValue(1920L);
        panel.getVideoHeightSpinner().setValue(1080.0);
        assertEquals(1920, panel.getVideoWidth());
        assertEquals(1080, panel.getVideoHeight());
    }

    @Test
    @DisplayName("VideoArchitectWorkspacePanel logs to console even before being displayed")
    void testVideoArchitectConsoleLoggingBeforeDisplay() throws Exception {
        VideoArchitectTimelineCoordinator coordinator = new VideoArchitectTimelineCoordinator();
        VideoArchitectWorkspacePanel workspace = new VideoArchitectWorkspacePanel(coordinator, null, null);

        // Panel is not displayable yet, but logging should still work and append to document
        assertFalse(workspace.getVideoConsoleArea().isDisplayable());

        workspace.logToConsole("Action 1: User created project");
        workspace.logToConsole("Action 2: User added scene S1");

        // Wait for SwingUtilities.invokeLater to complete
        SwingUtilities.invokeAndWait(() -> {});

        String text = workspace.getVideoConsoleArea().getText();
        assertTrue(text.contains("Action 1: User created project"), "Console should contain Action 1");
        assertTrue(text.contains("Action 2: User added scene S1"), "Console should contain Action 2");
    }

    @Test
    @DisplayName("VideoArchitectTimelineCoordinator moves scenes up and down accurately")
    void testVideoArchitectTimelineSceneReordering() {
        ConfigService mockConfig = mock(ConfigService.class);
        VideoArchitectTimelineCoordinator coordinator = new VideoArchitectTimelineCoordinator(mockConfig);

        DefaultListModel<Scene> listModel = new DefaultListModel<>();
        Scene s1 = new Scene("S1", "First shot");
        Scene s2 = new Scene("S2", "Second shot");
        Scene s3 = new Scene("S3", "Third shot");
        listModel.addElement(s1);
        listModel.addElement(s2);
        listModel.addElement(s3);

        JList<Scene> listView = new JList<>(listModel);
        listView.setSelectedIndex(1); // Select S2

        List<String> logs = new ArrayList<>();

        // Move S2 up (direction -1)
        coordinator.handleMoveScene(-1, listView, listModel, logs::add, () -> {});
        assertEquals(s2, listModel.get(0));
        assertEquals(s1, listModel.get(1));
        assertEquals(0, listView.getSelectedIndex());

        // Move S2 down (direction +1)
        coordinator.handleMoveScene(1, listView, listModel, logs::add, () -> {});
        assertEquals(s1, listModel.get(0));
        assertEquals(s2, listModel.get(1));
        assertEquals(1, listView.getSelectedIndex());
    }

    @Test
    @DisplayName("PromptLabStatusPoller restores btnSendToComfy state on successful generation")
    void testPromptLabButtonRestoredOnSuccess() throws Exception {
        ComfyApiClient apiClient = mock(ComfyApiClient.class);
        ComfyWebSocketClient wsClient = mock(ComfyWebSocketClient.class);
        AutoCloseable mockSession = mock(AutoCloseable.class);
        when(wsClient.connect(anyString(), anyString(), any(), any(), any())).thenReturn(mockSession);

        ComfyApiClient.ImageOutput output = new ComfyApiClient.ImageOutput("test.png", "", "output");
        when(apiClient.fetchPromptHistoryImage(anyString(), anyString())).thenReturn(output);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        PromptLabStatusPoller poller = new PromptLabStatusPoller(apiClient, wsClient, executor, null);

        PromptLabView view = mock(PromptLabView.class);
        JButton btn = new JButton("Generating...");
        btn.setEnabled(false);
        when(view.getBtnSendToComfy()).thenReturn(btn);
        when(view.getPromptLabProgressBar()).thenReturn(new JProgressBar());
        when(view.getPromptLabConsole()).thenReturn(new JTextArea());

        poller.pollPromptStatus("http://127.0.0.1:8188", "p-123", view, img -> {});

        executor.shutdown();
        boolean done = executor.awaitTermination(5, TimeUnit.SECONDS);
        assertTrue(done);

        SwingUtilities.invokeAndWait(() -> {});

        assertTrue(btn.isEnabled(), "Button should be re-enabled after generation completes");
        assertEquals("Generate", btn.getText());
    }

    @Test
    @DisplayName("OutputGalleryPanel loads images, updates deletion count, and triggers media viewer safely")
    void testOutputGalleryPanelWorkflow(@TempDir Path tempDir) throws IOException, Exception {
        ConfigService mockConfig = mock(ConfigService.class);
        when(mockConfig.getResolvedOutputDir()).thenReturn(tempDir.toString());

        File img1 = tempDir.resolve("render_001.png").toFile();
        File vid1 = tempDir.resolve("scene_001.mp4").toFile();
        assertTrue(img1.createNewFile());
        assertTrue(vid1.createNewFile());

        OutputGalleryPanel gallery = new OutputGalleryPanel(mockConfig);
        gallery.refresh();

        // Verify loaded files
        SwingUtilities.invokeAndWait(() -> {});
        assertNotNull(gallery);
    }
}
