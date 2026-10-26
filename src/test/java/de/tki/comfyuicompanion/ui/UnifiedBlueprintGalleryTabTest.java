package de.tki.comfyuicompanion.ui;

import de.tki.comfyuicompanion.service.*;
import de.tki.comfyuicompanion.service.impl.ArchiveService;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.service.impl.LocalAIService;
import de.tki.comfyuicompanion.service.impl.LocalModelScanner;
import de.tki.comfyuicompanion.ui.civitai.CivitaiGalleryTab;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Enterprise test suite verifying the unified Blueprint Gallery navigation, sub-tab provider switching,
 * CardLayout visibility transitions, tab manager consolidation, and download manager callback delegation.
 */
class UnifiedBlueprintGalleryTabTest {

    @Mock private ConfigService configService;
    @Mock private IModelArchitectureService modelArchitectureService;
    @Mock private IComfyLifecycleService lifecycleService;
    @Mock private IComfyRegistryClient registryClient;
    @Mock private ILocalModelValidator localModelValidator;
    @Mock private IWorkflowDownloader workflowDownloader;
    @Mock private LocalModelScanner localModelScanner;
    @Mock private ArchiveService archiveService;
    @Mock private LocalAIService localAIService;
    @Mock private IModelSearchService modelSearchService;
    @Mock private ICivitaiHubService civitaiHubService;
    @Mock private IDownloadManager downloadManager;

    private CivitaiGalleryTab civitaiGalleryTab;
    private BlueprintGalleryTab blueprintGalleryTab;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(configService.isDarkMode()).thenReturn(true);
        when(configService.getArchivePath()).thenReturn("");
        when(configService.getResolvedOutputDir()).thenReturn(System.getProperty("java.io.tmpdir"));
        when(civitaiHubService.getActiveBaseModels()).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(civitaiHubService.getModelTypes()).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(civitaiHubService.queryModels(any(), any(), any(), any(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        civitaiGalleryTab = spy(new CivitaiGalleryTab(civitaiHubService, downloadManager, configService));

        blueprintGalleryTab = new BlueprintGalleryTab(
                configService,
                modelArchitectureService,
                lifecycleService,
                registryClient,
                localModelValidator,
                workflowDownloader,
                localModelScanner,
                archiveService,
                localAIService,
                modelSearchService,
                null,
                civitaiGalleryTab
        );
    }

    @Test
    void testSubTabTogglesExistAndSwitchCardVisibility() {
        Component northComp = ((BorderLayout) blueprintGalleryTab.getLayout()).getLayoutComponent(BorderLayout.NORTH);
        assertNotNull(northComp, "Top sub-tab panel should be at BorderLayout.NORTH");

        Component centerComp = ((BorderLayout) blueprintGalleryTab.getLayout()).getLayoutComponent(BorderLayout.CENTER);
        assertNotNull(centerComp, "Root cards panel should be at BorderLayout.CENTER");
        JPanel rootCardsPanel = (JPanel) centerComp;

        Component comfyViewPanel = findComponentByName(rootCardsPanel, "comfyViewPanel");
        Component modelHubContainer = findComponentByName(rootCardsPanel, "modelHubContainer");
        assertNotNull(comfyViewPanel, "comfyViewPanel must be registered in rootCardsPanel");
        assertNotNull(modelHubContainer, "modelHubContainer must be registered in rootCardsPanel");

        assertSame(modelHubContainer, civitaiGalleryTab.getParent(), "civitaiGalleryTab must remain child of modelHubContainer");

        List<JToggleButton> subTabToggles = new ArrayList<>();
        findToggleButtons((Container) northComp, subTabToggles);
        assertEquals(3, subTabToggles.size(), "Should have exactly 3 top provider sub-tabs");

        JToggleButton comfyBtn = subTabToggles.get(0);
        JToggleButton civitaiBtn = subTabToggles.get(1);
        JToggleButton hfBtn = subTabToggles.get(2);

        // Initial state: Comfy.org visible, Model Hub hidden
        assertTrue(comfyBtn.isSelected());
        assertTrue(comfyViewPanel.isVisible(), "comfyViewPanel should be visible initially");
        assertFalse(modelHubContainer.isVisible(), "modelHubContainer should be hidden initially");

        // Switch to Civitai Model Hub
        civitaiBtn.doClick();
        assertFalse(comfyViewPanel.isVisible(), "comfyViewPanel must hide when Civitai is selected");
        assertTrue(modelHubContainer.isVisible(), "modelHubContainer must become visible when Civitai is selected");
        assertTrue(civitaiGalleryTab.isVisible(), "civitaiGalleryTab must be visible inside modelHubContainer");
        verify(civitaiGalleryTab, atLeastOnce()).setProviderBarVisible(false);
        verify(civitaiGalleryTab, atLeastOnce()).showCivitaiView();

        // Switch to Hugging Face Hub
        hfBtn.doClick();
        assertFalse(comfyViewPanel.isVisible(), "comfyViewPanel must remain hidden when Hugging Face is selected");
        assertTrue(modelHubContainer.isVisible(), "modelHubContainer must remain visible when Hugging Face is selected");
        verify(civitaiGalleryTab, atLeastOnce()).showHuggingFaceView();

        // Switch back to Comfy.org Blueprints
        comfyBtn.doClick();
        assertTrue(comfyViewPanel.isVisible(), "comfyViewPanel must become visible again when Comfy.org is selected");
        assertFalse(modelHubContainer.isVisible(), "modelHubContainer must hide when Comfy.org is selected");
    }

    @Test
    void testMainWindowTabManagerOmitsModelHubTab() {
        MainWindowBuilder.MainWindowComponents components = new MainWindowBuilder.MainWindowComponents();
        components.mainTabs = new JTabbedPane();
        components.dashboardPanel = new JPanel();
        components.downloadManagerPanel = new JPanel();
        components.galleryPanel = new OutputGalleryPanel(configService);
        components.blueprintGalleryWrapper = new JPanel();
        components.civitaiHubWrapper = new JPanel();

        when(configService.isBlueprintGalleryEnabled()).thenReturn(true);
        when(configService.isCivitaiGalleryEnabled()).thenReturn(true);

        MainWindowTabManager.updateTabVisibility(components, configService);

        List<String> titles = new ArrayList<>();
        for (int i = 0; i < components.mainTabs.getTabCount(); i++) {
            titles.add(components.mainTabs.getTitleAt(i));
        }

        assertTrue(titles.contains("Dashboard"), "Dashboard should be present");
        assertTrue(titles.contains("Download Manager"), "Download Manager should be present");
        assertTrue(titles.contains("Gallery"), "Gallery should be present");
        assertTrue(titles.contains("Blueprint Gallery"), "Blueprint Gallery should be present");
        assertFalse(titles.contains("Model Hub"), "Model Hub should NOT be present as a separate main tab");
    }

    @Test
    void testSwitchToDownloadManagerForwarding() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        blueprintGalleryTab.setOnSwitchToDownloadManager(missing -> invoked.set(true));

        verify(civitaiGalleryTab).setOnSwitchToDownloadManager(any());
    }

    private Component findComponentByName(Container container, String name) {
        for (Component c : container.getComponents()) {
            if (name.equals(c.getName())) {
                return c;
            }
            if (c instanceof Container child) {
                Component found = findComponentByName(child, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void findToggleButtons(Container container, List<JToggleButton> list) {
        for (Component c : container.getComponents()) {
            if (c instanceof JToggleButton tb) {
                list.add(tb);
            } else if (c instanceof Container child) {
                findToggleButtons(child, list);
            }
        }
    }
}
