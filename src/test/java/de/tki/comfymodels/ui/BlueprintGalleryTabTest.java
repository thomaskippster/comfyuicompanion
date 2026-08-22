package de.tki.comfymodels.ui;

import de.tki.comfymodels.domain.ComfyRegistryWorkflow;
import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.IComfyLifecycleService;
import de.tki.comfymodels.service.IComfyRegistryClient;
import de.tki.comfymodels.service.IModelAnalyzer;
import de.tki.comfymodels.service.IModelArchitectureService;
import de.tki.comfymodels.service.IWorkflowDownloader;
import de.tki.comfymodels.service.impl.ComfyRegistryClient;
import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.LocalModelScanner;
import de.tki.comfymodels.service.impl.LocalModelValidator;
import de.tki.comfymodels.service.impl.ProcessTracker;
import de.tki.comfymodels.service.impl.ArchiveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class BlueprintGalleryTabTest {

    private BlueprintGalleryTab blueprintGalleryTab;

    @Mock private ConfigService configService;
    @Mock private IModelArchitectureService modelArchitectureService;
    @Mock private IComfyLifecycleService lifecycleService;
    @Mock private LocalModelScanner localModelScanner;
    @Mock private IModelAnalyzer modelAnalyzer;
    @Mock private IWorkflowDownloader workflowDownloader;
    @Mock private ArchiveService archiveService;
    @Mock private de.tki.comfymodels.service.impl.LocalAIService localAIService;
    @Mock private ProcessTracker processTracker;
    @Mock private de.tki.comfymodels.service.IModelSearchService modelSearchService;

    private ComfyRegistryClient registryClient;
    private LocalModelValidator localModelValidator;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(configService.isDarkMode()).thenReturn(true);
        when(configService.getArchivePath()).thenReturn("");
        when(localModelScanner.scanLocalModels()).thenReturn(Collections.emptyList());
        when(archiveService.normalizeFolder(any())).thenAnswer(inv -> inv.getArgument(0));
        
        registryClient = new ComfyRegistryClient();
        localModelValidator = new LocalModelValidator(configService, registryClient, localModelScanner, modelAnalyzer);

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
                processTracker
        );
    }

    @Test
    public void testRealDataLoading() throws Exception {
        // Run refresh
        blueprintGalleryTab.refreshAllData(true);
        
        // Wait for background thread to complete (needs network fetch + model checking)
        // Since downloading JSON files from github is mocked/refused/timed out, it will complete in a few seconds.
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 15000) {
            Thread.sleep(500);
            if (de.tki.comfymodels.ui.BlueprintGalleryTabTestHelper.getGalleryWrapper(blueprintGalleryTab).getComponents().length > 0) {
                // If components are added, we are loaded!
                break;
            }
        }
        
        JPanel galleryWrapper = de.tki.comfymodels.ui.BlueprintGalleryTabTestHelper.getGalleryWrapper(blueprintGalleryTab);
        
        // Force size so columns calculate
        galleryWrapper.setSize(1200, 800);
        
        SwingUtilities.invokeAndWait(() -> {
            de.tki.comfymodels.ui.BlueprintGalleryTabTestHelper.applyFilter(blueprintGalleryTab);
        });

        // Let's print out what components are in galleryWrapper
        SwingUtilities.invokeAndWait(() -> {
            Component[] comps = galleryWrapper.getComponents();
            System.out.println("--- SWING GALLERY STATE ---");
            System.out.println("Total components: " + comps.length);
            for (Component c : comps) {
                System.out.println("Component: " + c.getClass().getSimpleName() + ", visible: " + c.isVisible());
                if (c instanceof JLabel lbl) {
                    System.out.println("  Label text: " + lbl.getText());
                } else if (c instanceof JPanel jp) {
                    System.out.println("  Panel layout: " + jp.getLayout().getClass().getSimpleName());
                    System.out.println("  Panel subcomponents: " + jp.getComponents().length);
                    for (Component sub : jp.getComponents()) {
                        System.out.println("    Sub component: " + sub.getClass().getSimpleName());
                    }
                }
            }
        });
    }

    @Test
    public void testHiDreamModelStatusWhenNotInstalled() {
        ModelInfo info = new ModelInfo();
        info.setName("HiDream");
        info.setType("checkpoints");
        
        try {
            java.lang.reflect.Method m = BlueprintGalleryTab.class.getDeclaredMethod("getModelStatus", ModelInfo.class);
            m.setAccessible(true);
            String status = (String) m.invoke(blueprintGalleryTab, info);
            assertThat(status).isNotEqualTo("✅ Already exists");
            assertThat(status).isEqualTo("Idle");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testIdeogramModelStatusWhenNotInstalled() {
        ModelInfo info = new ModelInfo();
        info.setName("Ideogram");
        info.setType("checkpoints");
        
        try {
            java.lang.reflect.Method m = BlueprintGalleryTab.class.getDeclaredMethod("getModelStatus", ModelInfo.class);
            m.setAccessible(true);
            String status = (String) m.invoke(blueprintGalleryTab, info);
            assertThat(status).isNotEqualTo("✅ Already exists");
            assertThat(status).isEqualTo("Idle");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testFluxKreaDevModelStatusWhenNotInstalled() {
        ModelInfo info = new ModelInfo();
        info.setName("Flux.1 Krea Dev");
        info.setType("checkpoints");
        
        try {
            java.lang.reflect.Method m = BlueprintGalleryTab.class.getDeclaredMethod("getModelStatus", ModelInfo.class);
            m.setAccessible(true);
            String status = (String) m.invoke(blueprintGalleryTab, info);
            assertThat(status).isNotEqualTo("✅ Already exists");
            assertThat(status).isEqualTo("Idle");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testArchivedModelNotCountedAsReady() {
        ModelInfo info = new ModelInfo();
        info.setName("flux2-vae.safetensors");
        info.setType("vae");
        
        // Mock isModelArchived to return true
        LocalModelValidator mockValidator = org.mockito.Mockito.mock(LocalModelValidator.class);
        when(mockValidator.isModelActive(any())).thenReturn(false);
        when(mockValidator.isModelArchived("flux2-vae.safetensors")).thenReturn(true);
        when(mockValidator.getActiveLocalModelNames()).thenReturn(Collections.emptySet());
        
        BlueprintGalleryTab tabWithMock = new BlueprintGalleryTab(
                configService,
                modelArchitectureService,
                lifecycleService,
                registryClient,
                mockValidator,
                workflowDownloader,
                localModelScanner,
                archiveService,
                localAIService,
                modelSearchService,
                processTracker
        );
        
        try {
            java.lang.reflect.Method m = BlueprintGalleryTab.class.getDeclaredMethod("getModelStatus", ModelInfo.class);
            m.setAccessible(true);
            String status = (String) m.invoke(tabWithMock, info);
            assertThat(status).isEqualTo("📦 Archived");
            
            java.lang.reflect.Method mDeep = BlueprintGalleryTab.class.getDeclaredMethod("isModelPresentDeep", ModelInfo.class);
            mDeep.setAccessible(true);
            boolean present = (boolean) mDeep.invoke(tabWithMock, info);
            assertThat(present).isFalse();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testImageBlueprintsComeBeforeVideoBlueprints() {
        ComfyRegistryWorkflow imgWf = new ComfyRegistryWorkflow();
        imgWf.setTitle("SDXL Base Image");
        imgWf.setCategory("Text to Image");
        imgWf.setMediaType("image");
        imgWf.setPopularScore(100.0);

        ComfyRegistryWorkflow vidWf = new ComfyRegistryWorkflow();
        vidWf.setTitle("Wan 2.2 Video Generator");
        vidWf.setCategory("Text to Video");
        vidWf.setMediaType("video");
        vidWf.setThumbnailUrl("https://example.com/thumb.mp4");
        vidWf.setPopularScore(500.0);

        BlueprintGalleryTab.BlueprintEntry imgEntry = new BlueprintGalleryTab.BlueprintEntry(imgWf);
        BlueprintGalleryTab.BlueprintEntry vidEntry = new BlueprintGalleryTab.BlueprintEntry(vidWf);

        assertThat(BlueprintGalleryTab.isVideoBlueprint(imgEntry)).isFalse();
        assertThat(BlueprintGalleryTab.isVideoBlueprint(vidEntry)).isTrue();

        assertThat(BlueprintGalleryTab.getBlueprintMediaRank(imgEntry)).isEqualTo(0);
        assertThat(BlueprintGalleryTab.getBlueprintMediaRank(vidEntry)).isEqualTo(1);

        assertThat(BlueprintGalleryTab.getCategoryOrderScore("Text to Image"))
                .isLessThan(BlueprintGalleryTab.getCategoryOrderScore("Text to Video"));
        assertThat(BlueprintGalleryTab.getCategoryOrderScore("Image Edit"))
                .isLessThan(BlueprintGalleryTab.getCategoryOrderScore("Image to Video"));
        assertThat(BlueprintGalleryTab.getCategoryOrderScore("Inpainting"))
                .isLessThan(BlueprintGalleryTab.getCategoryOrderScore("Video"));
    }
}

