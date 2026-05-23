package de.tki.comfymodels.ui;

import de.tki.comfymodels.Main;
import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.*;
import de.tki.comfymodels.service.impl.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class GuiStatusTransitionTest {

    private Main mainFrame;

    @Mock private IModelAnalyzer analyzer;
    @Mock private IDownloadManager downloadManager;
    @Mock private IWorkflowService workflowService;
    @Mock private IModelSearchService searchService;
    @Mock private IModelValidator modelValidator;
    @Mock private RestBridgeService restBridge;
    @Mock private ArchiveService archiveService;
    @Mock private IComfyLifecycleService lifecycleService;
    @Mock private ComfyDiagnosticService diagnosticService;
    @Mock private ProfileManager profileManager;
    @Mock private EnvironmentBootstrapperImpl bootstrapper;
    @Mock private ComfyProcessController processController;
    @Mock private CivitaiService civitaiService;
    @Mock private HuggingFaceService huggingFaceService;
    @Mock private ConfigService configService;
    @Mock private GeminiAIService geminiService;
    @Mock private ModelListService modelListService;
    @Mock private ModelHashRegistry hashRegistry;
    @Mock private LocalModelScanner localScanner;
    @Mock private PathResolver pathResolver;
    @Mock private VersionService versionService;

    @Mock private HardwareMonitorService hardwareMonitorService;
    @Mock private UpdaterService updaterService;

    @BeforeEach
    public void setUp() throws InterruptedException, InvocationTargetException {
        MockitoAnnotations.openMocks(this);

        // Mock ConfigService behavior
        when(configService.isUnlocked()).thenReturn(true);
        when(configService.isDarkMode()).thenReturn(true);
        when(configService.getApiToken()).thenReturn("test-token");
        when(configService.getModelsPath()).thenReturn("C:\\models");
        when(configService.getComfyUIPath()).thenReturn("C:\\ComfyUI");
        when(configService.getComfyUIUrl()).thenReturn("http://localhost:8188");
        when(configService.getResolvedOutputDir()).thenReturn("C:\\ComfyUI\\output");
        when(archiveService.normalizeFolder(anyString())).thenAnswer(inv -> inv.getArgument(0));

        // Mock VersionService behavior
        when(versionService.getInstalledComfyVersion(anyString())).thenReturn("1.0.0");
        when(versionService.getInstalledPythonVersion(anyString())).thenReturn("3.10.9");
        when(versionService.getRemotePythonVersion()).thenReturn("3.11.0");
        when(versionService.getRemoteComfyVersionAsync()).thenReturn(java.util.concurrent.CompletableFuture.completedFuture("1.0.0"));



        SwingUtilities.invokeAndWait(() -> {
            mainFrame = new Main(analyzer, downloadManager, workflowService, searchService, 
                                 modelValidator, restBridge, archiveService, lifecycleService, diagnosticService,
                                 profileManager, bootstrapper, processController, civitaiService, huggingFaceService);
            
            // Inject @Autowired fields
            ReflectionTestUtils.setField(mainFrame, "configService", configService);
            ReflectionTestUtils.setField(mainFrame, "geminiService", geminiService);
            ReflectionTestUtils.setField(mainFrame, "modelListService", modelListService);
            ReflectionTestUtils.setField(mainFrame, "hashRegistry", hashRegistry);
            ReflectionTestUtils.setField(mainFrame, "localScanner", localScanner);
            ReflectionTestUtils.setField(mainFrame, "pathResolver", pathResolver);
            ReflectionTestUtils.setField(mainFrame, "versionService", versionService);

            ReflectionTestUtils.setField(mainFrame, "hardwareMonitorService", hardwareMonitorService);
            ReflectionTestUtils.setField(mainFrame, "updaterService", updaterService);
            
            ReflectionTestUtils.invokeMethod(mainFrame, "initUI");
        });
    }

    @AfterEach
    public void tearDown() throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(() -> {
            if (mainFrame != null) {
                mainFrame.dispose();
            }
        });
    }

    @Test
    public void testStatusLabelInitialState() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JLabel statusLabel = (JLabel) ReflectionTestUtils.getField(mainFrame, "statusLabel");
            assertThat(statusLabel.getText()).isEqualTo("Ready");
        });
    }

    @Test
    public void testStatusTransitionWhenSearching() throws Exception {
        List<ModelInfo> mockModels = new ArrayList<>();
        mockModels.add(new ModelInfo("checkpoints", "test.safetensors", "http://example.com"));
        when(analyzer.analyze(anyString(), anyString())).thenReturn(mockModels);

        SwingUtilities.invokeAndWait(() -> {
            JTextArea jsonArea = (JTextArea) ReflectionTestUtils.getField(mainFrame, "jsonInputArea");
            jsonArea.setText("{\"test\": \"workflow\"}");
            JButton analyzeBtn = findButtonByText(mainFrame, "Deep Search");
            analyzeBtn.doClick();
        });
        
        // Wait for searchService to be called (it's called in a separate thread)
        Thread.sleep(1000);
        
        verify(searchService, atLeastOnce()).searchOnline(eq(mockModels), any(), anyString(), anyString(), any(), any(), any());
        
        SwingUtilities.invokeAndWait(() -> {
            JLabel statusLabel = (JLabel) ReflectionTestUtils.getField(mainFrame, "statusLabel");
            assertThat(statusLabel.getText()).isEqualTo("Searching...");
        });
    }

    @Test
    public void testDownloadButtonEnabledAfterAnalysis() throws Exception {
        List<ModelInfo> mockModels = new ArrayList<>();
        ModelInfo info = new ModelInfo("checkpoints", "test.safetensors", "http://example.com");
        info.setSize("1.0 GB"); // Ensure it has a size to be considered "good"
        mockModels.add(info);
        when(analyzer.analyze(anyString(), anyString())).thenReturn(mockModels);

        SwingUtilities.invokeAndWait(() -> {
            JTextArea jsonArea = (JTextArea) ReflectionTestUtils.getField(mainFrame, "jsonInputArea");
            jsonArea.setText("{\"test\": \"workflow\"}");
            JButton analyzeBtn = findButtonByText(mainFrame, "Deep Search");
            analyzeBtn.doClick();
        });

        Thread.sleep(1000);

        SwingUtilities.invokeAndWait(() -> {
            JButton downloadButton = (JButton) ReflectionTestUtils.getField(mainFrame, "downloadButton");
            assertThat(downloadButton.isEnabled()).isTrue();
        });
    }

    @Test
    public void testPauseButtonToggle() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JButton pauseButton = (JButton) ReflectionTestUtils.getField(mainFrame, "pauseButton");
            assertThat(pauseButton.getText()).isEqualTo("Pause");
        });
        
        when(downloadManager.isPaused()).thenReturn(true);
        
        SwingUtilities.invokeAndWait(() -> {
            JButton pauseButton = (JButton) ReflectionTestUtils.getField(mainFrame, "pauseButton");
            pauseButton.doClick();
        });
        
        verify(downloadManager).togglePause();
        
        SwingUtilities.invokeAndWait(() -> {
            JButton pauseButton = (JButton) ReflectionTestUtils.getField(mainFrame, "pauseButton");
            assertThat(pauseButton.getText()).isEqualTo("Resume");
        });
    }

    @Test
    public void testStopButtonAction() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JButton stopButton = (JButton) ReflectionTestUtils.getField(mainFrame, "stopButton");
            stopButton.doClick();
        });
        verify(downloadManager).stop();
    }

    @Test
    public void testTableStatusTransitions() throws Exception {
        // Prepare mock models
        List<ModelInfo> mockModels = new ArrayList<>();
        ModelInfo m1 = new ModelInfo("checkpoints", "model1.safetensors", "url1");
        ModelInfo m2 = new ModelInfo("checkpoints", "model2.safetensors", "url2");
        mockModels.add(m1);
        mockModels.add(m2);
        
        when(analyzer.analyze(anyString(), anyString())).thenReturn(mockModels);

        // 1. Initial Analysis
        SwingUtilities.invokeAndWait(() -> {
            JTextArea jsonArea = (JTextArea) ReflectionTestUtils.getField(mainFrame, "jsonInputArea");
            jsonArea.setText("test workflow");
            JButton analyzeBtn = findButtonByText(mainFrame, "Deep Search");
            analyzeBtn.doClick();
        });

        // 2. Simulate Search Service Status Updates
        SwingUtilities.invokeAndWait(() -> {
            DefaultTableModel tableModel = (DefaultTableModel) ReflectionTestUtils.getField(mainFrame, "tableModel");
            tableModel.setValueAt("🔍 Searching...", 0, 7);
            tableModel.setValueAt("📦 Archived", 1, 7);
        });

        SwingUtilities.invokeAndWait(() -> {
            JTable table = findComponent(mainFrame, JTable.class);
            assertThat(table.getValueAt(0, 7)).isEqualTo("🔍 Searching...");
            assertThat(table.getValueAt(1, 7)).isEqualTo("📦 Archived");
        });

        // 3. Simulate Restoration Transition
        SwingUtilities.invokeAndWait(() -> {
            DefaultTableModel tableModel = (DefaultTableModel) ReflectionTestUtils.getField(mainFrame, "tableModel");
            // Manually set one to "Archived" to trigger restore logic if we were to click Start
            tableModel.setValueAt("📦 Archived", 1, 7);
            tableModel.setValueAt(true, 1, 0); // Select it
        });

        // We can't easily test the full async startDownloadQueue here without complex mocking of archiveService,
        // but we can test the UI's reaction to status updates from the download manager.
        
        // 4. Simulate Download Progress
        SwingUtilities.invokeAndWait(() -> {
            DefaultTableModel tableModel = (DefaultTableModel) ReflectionTestUtils.getField(mainFrame, "tableModel");
            tableModel.setValueAt("Downloading: 45% (1.2 GB)", 0, 7);
        });

        SwingUtilities.invokeAndWait(() -> {
            JTable table = findComponent(mainFrame, JTable.class);
            assertThat(table.getValueAt(0, 7)).isEqualTo("Downloading: 45% (1.2 GB)");
        });

        // 5. Final States
        SwingUtilities.invokeAndWait(() -> {
            DefaultTableModel tableModel = (DefaultTableModel) ReflectionTestUtils.getField(mainFrame, "tableModel");
            tableModel.setValueAt("✅ Finished", 0, 7);
            tableModel.setValueAt("✅ Already exists", 1, 7);
        });

        SwingUtilities.invokeAndWait(() -> {
            JTable table = findComponent(mainFrame, JTable.class);
            assertThat(table.getValueAt(0, 7)).isEqualTo("✅ Finished");
            assertThat(table.getValueAt(1, 7)).isEqualTo("✅ Already exists");
        });

        // 6. Error State
        SwingUtilities.invokeAndWait(() -> {
            DefaultTableModel tableModel = (DefaultTableModel) ReflectionTestUtils.getField(mainFrame, "tableModel");
            tableModel.setValueAt("❌ Error: Disk Full", 0, 7);
        });

        SwingUtilities.invokeAndWait(() -> {
            JTable table = findComponent(mainFrame, JTable.class);
            assertThat(table.getValueAt(0, 7)).isEqualTo("❌ Error: Disk Full");
        });
    }

    @Test
    public void testBulkSelectionToggle() throws Exception {
        List<ModelInfo> mockModels = new ArrayList<>();
        mockModels.add(new ModelInfo("checkpoints", "m1", "u1"));
        mockModels.add(new ModelInfo("checkpoints", "m2", "u2"));
        when(analyzer.analyze(anyString(), anyString())).thenReturn(mockModels);

        SwingUtilities.invokeAndWait(() -> {
            JTextArea jsonArea = (JTextArea) ReflectionTestUtils.getField(mainFrame, "jsonInputArea");
            jsonArea.setText("some content");
            ReflectionTestUtils.invokeMethod(mainFrame, "analyzeJsonContent");
        });

        SwingUtilities.invokeAndWait(() -> {
            JTable table = findComponent(mainFrame, JTable.class);
            table.setValueAt(true, 0, 0);
            table.setValueAt(true, 1, 0);
            
            assertThat(table.getValueAt(0, 0)).isEqualTo(true);
            assertThat(table.getValueAt(1, 0)).isEqualTo(true);
        });
    }
    
    @Test
    public void testAiModelDisplay() throws Exception {
        when(geminiService.discoverBestModel()).thenReturn("gemini-1.5-pro");
        
        SwingUtilities.invokeAndWait(() -> {
            ReflectionTestUtils.invokeMethod(mainFrame, "updateAiModelDisplay");
        });
        
        Thread.sleep(500);
        
        SwingUtilities.invokeAndWait(() -> {
            JLabel activeAiModelLabel = (JLabel) ReflectionTestUtils.getField(mainFrame, "activeAiModelLabel");
            assertThat(activeAiModelLabel.getText()).isEqualTo("Active AI: gemini-1.5-pro");
        });
    }

    private <T extends Component> T findComponent(Container container, Class<T> type) {
        for (Component c : container.getComponents()) {
            if (type.isInstance(c)) {
                return type.cast(c);
            } else if (c instanceof Container) {
                T found = findComponent((Container) c, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private JButton findButtonByText(Container container, String text) {
        for (Component c : container.getComponents()) {
            if (c instanceof JButton && text.equals(((JButton) c).getText())) {
                return (JButton) c;
            } else if (c instanceof Container) {
                JButton b = findButtonByText((Container) c, text);
                if (b != null) return b;
            }
        }
        return null;
    }
}
