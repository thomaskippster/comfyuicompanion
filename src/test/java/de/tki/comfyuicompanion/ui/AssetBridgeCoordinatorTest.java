package de.tki.comfyuicompanion.ui;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.PngMetadataReaderService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.video.VideoArchitectConfigPanel;
import de.tki.comfyuicompanion.ui.video.VideoArchitectWorkspacePanel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import javax.swing.DefaultListModel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssetBridgeCoordinatorTest {

    private ConfigService configService;
    private PngMetadataReaderService metadataReaderService;
    private AssetBridgeCoordinator coordinator;

    @BeforeEach
    void setUp() {
        configService = mock(ConfigService.class);
        metadataReaderService = mock(PngMetadataReaderService.class);
        coordinator = new AssetBridgeCoordinator(configService, metadataReaderService);
    }

    @Test
    void testSendToVideoArchitectAsSpeakerUpdatesConfigAndPanel(@TempDir Path tempDir) throws IOException {
        File dummyFile = tempDir.resolve("start_portrait.png").toFile();
        assertTrue(dummyFile.createNewFile());

        VideoArchitectTab videoArchitectTab = mock(VideoArchitectTab.class);
        VideoArchitectConfigPanel configPanel = mock(VideoArchitectConfigPanel.class);
        VideoArchitectWorkspacePanel workspacePanel = mock(VideoArchitectWorkspacePanel.class);
        JTextField speakerField = new JTextField();

        when(videoArchitectTab.getConfigPanel()).thenReturn(configPanel);
        when(videoArchitectTab.getWorkspacePanel()).thenReturn(workspacePanel);
        when(configPanel.getSpeakerImageField()).thenReturn(speakerField);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Output Gallery", new javax.swing.JPanel());
        tabs.addTab("Video Architect (Beta)", new javax.swing.JPanel());

        coordinator.registerUiReferences(tabs, null, null, videoArchitectTab);
        coordinator.sendToVideoArchitectAsSpeaker(dummyFile);

        assertEquals(dummyFile.getAbsolutePath(), speakerField.getText());
        verify(configService).setSpeakerImagePath(dummyFile.getAbsolutePath());
    }

    @Test
    void testSendToVideoArchitectAsNewSceneAppendsToTimeline(@TempDir Path tempDir) throws IOException {
        File dummyFile = tempDir.resolve("keyframe_forest.png").toFile();
        assertTrue(dummyFile.createNewFile());

        VideoArchitectTab videoArchitectTab = mock(VideoArchitectTab.class);
        VideoArchitectWorkspacePanel workspacePanel = mock(VideoArchitectWorkspacePanel.class);
        DefaultListModel<Scene> timelineModel = new DefaultListModel<>();

        when(videoArchitectTab.getWorkspacePanel()).thenReturn(workspacePanel);
        when(workspacePanel.getTimelineListModel()).thenReturn(timelineModel);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Video Architect (Beta)", new javax.swing.JPanel());

        coordinator.registerUiReferences(tabs, null, null, videoArchitectTab);
        coordinator.sendToVideoArchitectAsNewScene(dummyFile, "Sunlight breaking through ancient pine trees");

        assertEquals(1, timelineModel.size());
        Scene created = timelineModel.get(0);
        assertEquals("S1", created.getSceneId());
        assertEquals(dummyFile.getAbsolutePath(), created.getSpeakerImagePath());
        assertEquals("Sunlight breaking through ancient pine trees", created.getPrompt());
    }

    @Test
    void testAssignKeyframeToSelectedSceneUpdatesTargetScene(@TempDir Path tempDir) throws IOException {
        File dummyFile = tempDir.resolve("updated_frame.png").toFile();
        assertTrue(dummyFile.createNewFile());

        VideoArchitectTab videoArchitectTab = mock(VideoArchitectTab.class);
        VideoArchitectWorkspacePanel workspacePanel = mock(VideoArchitectWorkspacePanel.class);
        javax.swing.JList<Scene> jlist = new javax.swing.JList<>();
        Scene targetScene = new Scene("S2", "Initial prompt");
        DefaultListModel<Scene> model = new DefaultListModel<>();
        model.addElement(targetScene);
        jlist.setModel(model);
        jlist.setSelectedValue(targetScene, true);

        when(videoArchitectTab.getWorkspacePanel()).thenReturn(workspacePanel);
        when(workspacePanel.getTimelineListView()).thenReturn(jlist);

        coordinator.registerUiReferences(new JTabbedPane(), null, null, videoArchitectTab);
        coordinator.assignKeyframeToSelectedScene(dummyFile);

        assertEquals(dummyFile.getAbsolutePath(), targetScene.getSpeakerImagePath());
        verify(workspacePanel).logToConsole(contains("Updated keyframe for Scene S2"));
    }
}
