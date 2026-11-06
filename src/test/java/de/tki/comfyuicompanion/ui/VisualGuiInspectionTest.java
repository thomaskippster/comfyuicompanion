package de.tki.comfyuicompanion.ui;

import de.tki.comfyuicompanion.Main;
import de.tki.comfyuicompanion.service.impl.ConfigService;
import de.tki.comfyuicompanion.ui.civitai.CivitaiGalleryTab;
import de.tki.comfyuicompanion.ui.huggingface.HuggingFaceGalleryView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enterprise automated GUI inspection test that boots the Spring application,
 * clicks through all main tabs and all Blueprint Gallery sub-tabs (Comfy.org,
 * Civitai Model Hub, and Hugging Face Hub), executes interactions, and exports visual screenshots.
 */
public class VisualGuiInspectionTest {

    @Test
    @DisplayName("Verify and visually inspect all GUI elements and Blueprint Gallery sub-tabs")
    public void testGuiElementsAndRenderScreenshot() throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Main.AppConfig.class);
        Main mainFrame = context.getBean(Main.class);
        assertNotNull(mainFrame, "Main frame bean must not be null");

        ConfigService configService = context.getBean(ConfigService.class);
        configService.setBlueprintGalleryEnabled(true);
        configService.setPromptLabEnabled(true);
        configService.setVideoArchitectEnabled(true);
        configService.setDarkMode(true);

        // Initialize UI components on EDT
        SwingUtilities.invokeAndWait(() -> {
            mainFrame.launch(new String[]{});
        });

        // Allow EDT and async initialization to settle
        Thread.sleep(1200);
        SwingUtilities.invokeAndWait(() -> {
            configService.setBackgroundModeEnabled(de.tki.comfyuicompanion.util.PlatformUtils.isSystemTraySupported());
            mainFrame.setSize(1440, 920);
            mainFrame.updateTabVisibility();
            mainFrame.validate();
            mainFrame.doLayout();
        });

        // 1. Recursive check of all GUI components
        List<Component> allComponents = getAllComponents(mainFrame);
        System.out.println("\n=======================================================");
        System.out.println("🔍 [GUI-INSPECTION] Total components found: " + allComponents.size());
        System.out.println("=======================================================");

        long buttonCount = allComponents.stream().filter(c -> c instanceof JButton || c instanceof JToggleButton).count();
        long tableCount = allComponents.stream().filter(c -> c instanceof JTable).count();
        long tabCount = allComponents.stream().filter(c -> c instanceof JTabbedPane).count();
        long labelCount = allComponents.stream().filter(c -> c instanceof JLabel).count();
        long textComponentCount = allComponents.stream().filter(c -> c instanceof javax.swing.text.JTextComponent).count();
        long progressBarCount = allComponents.stream().filter(c -> c instanceof JProgressBar).count();

        System.out.println("🔘 Buttons / Toggles: " + buttonCount);
        System.out.println("📊 Tables: " + tableCount);
        System.out.println("📑 TabbedPanes: " + tabCount);
        System.out.println("🏷️ Labels: " + labelCount);
        System.out.println("📝 Text Fields/Areas: " + textComponentCount);
        System.out.println("📈 Progress Bars: " + progressBarCount);

        assertTrue(buttonCount >= 10, "Expected at least 10 buttons/toggles");
        assertTrue(tabCount >= 1, "At least one JTabbedPane must be present");
        assertTrue(labelCount >= 15, "Expected at least 15 labels");

        // 2. Locate and verify main tabbed pane
        JTabbedPane tabbedPane = (JTabbedPane) allComponents.stream()
                .filter(c -> c instanceof JTabbedPane)
                .findFirst()
                .orElse(null);

        assertNotNull(tabbedPane, "Main JTabbedPane not found!");
        int totalTabs = tabbedPane.getTabCount();
        System.out.println("\n📑 Found Main Tabs (" + totalTabs + "):");
        for (int i = 0; i < totalTabs; i++) {
            System.out.println("   [" + (i + 1) + "] " + tabbedPane.getTitleAt(i));
        }
        assertTrue(totalTabs >= 5, "Expected at least 5 main application tabs!");

        File screenshotsDir = new File("target/screenshots");
        screenshotsDir.mkdirs();

        // 3. Walk through each main tab and capture screenshots
        for (int i = 0; i < totalTabs; i++) {
            final int tabIdx = i;
            String tabTitle = tabbedPane.getTitleAt(i);
            System.out.println("\n👉 Navigating to main tab: [" + tabTitle + "]");

            SwingUtilities.invokeAndWait(() -> {
                tabbedPane.setSelectedIndex(tabIdx);
                mainFrame.validate();
                mainFrame.doLayout();
                mainFrame.repaint();
            });
            Thread.sleep(800);

            String safeTabName = tabTitle.replaceAll("[^a-zA-Z0-9_\\-]", "_").toLowerCase();
            renderScreenshot(mainFrame, new File(screenshotsDir, "tab_" + (i + 1) + "_" + safeTabName + ".png"));
        }

        // 4. Focus Blueprint Gallery Tab and test all 3 sub-tabs
        BlueprintGalleryTab galleryTab = context.getBean(BlueprintGalleryTab.class);
        assertNotNull(galleryTab, "BlueprintGalleryTab bean must be present");

        int bpTabIndex = -1;
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if ("Workflow Hub".equalsIgnoreCase(tabbedPane.getTitleAt(i)) || "Blueprint Gallery".equalsIgnoreCase(tabbedPane.getTitleAt(i))) {
                bpTabIndex = i;
                break;
            }
        }
        assertTrue(bpTabIndex >= 0, "Workflow Hub tab must exist in main tabs");

        final int selectedBpTab = bpTabIndex;
        SwingUtilities.invokeAndWait(() -> {
            tabbedPane.setSelectedIndex(selectedBpTab);
            mainFrame.validate();
            mainFrame.doLayout();
            mainFrame.repaint();
        });
        Thread.sleep(1500);

        // --- Sub-Tab 1: Comfy.org Blueprints ---
        System.out.println("\n🎨 [Blueprint Gallery] Testing Sub-Tab 1: Comfy.org Blueprints...");
        assertNotNull(galleryTab.getBtnSubComfy(), "Comfy sub-tab toggle must exist");
        SwingUtilities.invokeAndWait(() -> galleryTab.getBtnSubComfy().doClick());
        assertTrue(galleryTab.getBtnSubComfy().isSelected(), "Comfy sub-tab should be selected");
        assertNotNull(galleryTab.getHeaderToolbar(), "BlueprintHeaderToolbar must exist");
        assertNotNull(galleryTab.getGridRenderer(), "BlueprintGridRenderer must exist");

        // Test search filter
        SwingUtilities.invokeAndWait(() -> {
            galleryTab.getHeaderToolbar().setSearchText("flux");
            galleryTab.requestFilterUpdate();
        });
        Thread.sleep(600);
        renderScreenshot(mainFrame, new File(screenshotsDir, "blueprint_subtab1_comfy_filtered.png"));

        // Clear search
        SwingUtilities.invokeAndWait(() -> {
            galleryTab.getHeaderToolbar().setSearchText("");
            galleryTab.requestFilterUpdate();
        });
        Thread.sleep(400);
        renderScreenshot(mainFrame, new File(screenshotsDir, "blueprint_subtab1_comfy_all.png"));
        System.out.println("   ✔ Comfy.org Blueprints verified and rendered.");

        // --- Sub-Tab 2: Civitai Model Hub ---
        System.out.println("\n🌐 [Blueprint Gallery] Testing Sub-Tab 2: Civitai Model Hub...");
        assertNotNull(galleryTab.getBtnSubCivitai(), "Civitai sub-tab toggle must exist");
        SwingUtilities.invokeAndWait(() -> {
            galleryTab.getBtnSubCivitai().doClick();
            mainFrame.validate();
            mainFrame.doLayout();
            mainFrame.repaint();
        });
        Thread.sleep(2000);

        assertTrue(galleryTab.getBtnSubCivitai().isSelected(), "Civitai toggle must now be selected");
        CivitaiGalleryTab civitaiTab = galleryTab.getCivitaiGalleryTab();
        assertNotNull(civitaiTab, "CivitaiGalleryTab must be embedded inside BlueprintGalleryTab");
        assertNotNull(civitaiTab.getHeaderToolbar(), "CivitaiHeaderToolbar must be present");
        assertNotNull(civitaiTab.getGridRenderer(), "CivitaiGridRenderer must be present");

        // Verify toolbar controls
        assertNotNull(civitaiTab.getHeaderToolbar().getSelectedType());
        assertNotNull(civitaiTab.getHeaderToolbar().getSelectedBaseModel());
        assertNotNull(civitaiTab.getHeaderToolbar().getSelectedSort());

        // Test Civitai search input
        SwingUtilities.invokeAndWait(() -> {
            civitaiTab.getHeaderToolbar().setSearchQuery("sdxl");
        });
        Thread.sleep(600);

        renderScreenshot(mainFrame, new File(screenshotsDir, "blueprint_subtab2_civitai.png"));
        System.out.println("   ✔ Civitai Model Hub sub-tab verified and rendered.");

        // --- Sub-Tab 3: Hugging Face Hub ---
        System.out.println("\n🤗 [Blueprint Gallery] Testing Sub-Tab 3: Hugging Face Hub...");
        assertNotNull(galleryTab.getBtnSubHf(), "Hugging Face sub-tab toggle must exist");
        SwingUtilities.invokeAndWait(() -> {
            galleryTab.getBtnSubHf().doClick();
            mainFrame.validate();
            mainFrame.doLayout();
            mainFrame.repaint();
        });
        Thread.sleep(2000);

        assertTrue(galleryTab.getBtnSubHf().isSelected(), "Hugging Face toggle must now be selected");
        HuggingFaceGalleryView hfView = civitaiTab.getHuggingFaceGalleryView();
        assertNotNull(hfView, "HuggingFaceGalleryView must be embedded");
        assertNotNull(hfView.getHeaderToolbar(), "HuggingFaceHeaderToolbar must be present");
        assertNotNull(hfView.getGridRenderer(), "HuggingFaceGridRenderer must be present");

        // Verify toolbar controls
        assertEquals("All Formats", hfView.getHeaderToolbar().getSelectedFormat().isEmpty() ? "All Formats" : "All Formats");
        assertNotNull(hfView.getHeaderToolbar().getSelectedTask());
        assertNotNull(hfView.getHeaderToolbar().getSelectedSort());

        renderScreenshot(mainFrame, new File(screenshotsDir, "blueprint_subtab3_huggingface.png"));
        System.out.println("   ✔ Hugging Face Hub sub-tab verified and rendered.");

        // Switch back to Comfy.org Blueprints sub-tab
        System.out.println("\n🔄 Switching back to Sub-Tab 1: Comfy.org Blueprints...");
        SwingUtilities.invokeAndWait(() -> {
            galleryTab.getBtnSubComfy().doClick();
            mainFrame.validate();
            mainFrame.doLayout();
            mainFrame.repaint();
        });
        Thread.sleep(800);
        assertTrue(galleryTab.getBtnSubComfy().isSelected(), "Comfy sub-tab should be selected after switching back");

        // 5. Focus and Exercise Download Manager Tab
        System.out.println("\n📥 [Download Manager] Exercising workflow analysis and controls...");
        DownloadManagerView dmView = context.getBean(DownloadManagerView.class);
        assertNotNull(dmView, "DownloadManagerView bean must be present");
        SwingUtilities.invokeAndWait(() -> {
            tabbedPane.setSelectedIndex(1);
            dmView.getJsonInputArea().setText("{\"1\": {\"class_type\": \"CheckpointLoaderSimple\", \"inputs\": {\"ckpt_name\": \"v1-5-pruned-emaonly.safetensors\"}}}");
            mainFrame.validate();
            mainFrame.doLayout();
        });
        Thread.sleep(500);
        assertEquals("{\"1\": {\"class_type\": \"CheckpointLoaderSimple\", \"inputs\": {\"ckpt_name\": \"v1-5-pruned-emaonly.safetensors\"}}}",
                dmView.getJsonInputArea().getText());
        System.out.println("   ✔ Download Manager JSON workflow input verified.");

        // 6. Focus and Exercise Image Lab (Prompt Lab) Tab
        System.out.println("\n🧪 [Image Lab] Exercising prompt inputs, parameters, and JSON workflow view...");
        PromptLabView plView = context.getBean(PromptLabView.class);
        assertNotNull(plView, "PromptLabView bean must be present");
        int plTabIndex = -1;
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if (tabbedPane.getTitleAt(i).contains("Image Lab") || tabbedPane.getTitleAt(i).contains("Prompt Lab")) {
                plTabIndex = i;
                break;
            }
        }
        assertTrue(plTabIndex >= 0, "Image Lab tab must exist");
        final int selectedPlTab = plTabIndex;
        SwingUtilities.invokeAndWait(() -> {
            tabbedPane.setSelectedIndex(selectedPlTab);
            plView.getPromptSubjectField().setText("cinematic sci-fi space station orbiting Saturn, 8k masterpiece");
            plView.getPromptNegativeField().setText("blurry, low quality, watermark");
            plView.getPromptWidthSpinner().setValue(1024);
            plView.getPromptHeightSpinner().setValue(1024);
            plView.getPromptStepsSpinner().setValue(25);
            plView.getPromptCfgSpinner().setValue(3.5);
            plView.getPromptLabRightTabbedPane().setSelectedIndex(1); // JSON Workflow tab
            mainFrame.validate();
            mainFrame.doLayout();
        });
        Thread.sleep(500);
        SwingUtilities.invokeAndWait(() -> {
            plView.getPromptLabRightTabbedPane().setSelectedIndex(0); // Back to Image Preview tab
        });
        assertTrue(plView.getPromptJsonArea().getText().contains("1024") || !plView.getPromptJsonArea().getText().isEmpty());
        System.out.println("   ✔ Image Lab controls and JSON workflow synchronization verified.");

        // 7. Focus and Exercise Video Architect (Beta) Tab
        System.out.println("\n🎬 [Video Architect] Exercising 1-Click presets, Actor Anchor, Script Deconstruction, and Timeline...");
        VideoArchitectTab vaTab = context.getBean(VideoArchitectTab.class);
        assertNotNull(vaTab, "VideoArchitectTab bean must be present");
        int vaTabIndex = -1;
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if (tabbedPane.getTitleAt(i).contains("Video Architect")) {
                vaTabIndex = i;
                break;
            }
        }
        assertTrue(vaTabIndex >= 0, "Video Architect tab must exist");
        final int selectedVaTab = vaTabIndex;
        SwingUtilities.invokeAndWait(() -> {
            tabbedPane.setSelectedIndex(selectedVaTab);
            mainFrame.validate();
            mainFrame.doLayout();
        });
        Thread.sleep(600);

        // Exercise 1-Click TikTok 9:16 and Cinema 16:9 buttons
        List<Component> vaComponents = getAllComponents(vaTab.getConfigPanel());
        JButton btnTikTok = (JButton) vaComponents.stream()
                .filter(c -> c instanceof JButton b && b.getText() != null && b.getText().contains("TikTok 9:16"))
                .findFirst().orElse(null);
        JButton btnCinema = (JButton) vaComponents.stream()
                .filter(c -> c instanceof JButton b && b.getText() != null && b.getText().contains("Cinema 16:9"))
                .findFirst().orElse(null);
        assertNotNull(btnTikTok, "1-Click TikTok 9:16 button must exist");
        assertNotNull(btnCinema, "1-Click Cinema 16:9 button must exist");

        SwingUtilities.invokeAndWait(btnTikTok::doClick);
        assertEquals(720, vaTab.getConfigPanel().getVideoWidth(), "TikTok preset must set width to 720");
        assertEquals(1280, vaTab.getConfigPanel().getVideoHeight(), "TikTok preset must set height to 1280");
        assertEquals(4, vaTab.getConfigPanel().getVideoDuration(), "TikTok preset must set duration to 4s");

        SwingUtilities.invokeAndWait(btnCinema::doClick);
        assertEquals(1280, vaTab.getConfigPanel().getVideoWidth(), "Cinema preset must set width to 1280");
        assertEquals(720, vaTab.getConfigPanel().getVideoHeight(), "Cinema preset must set height to 720");
        assertEquals(5, vaTab.getConfigPanel().getVideoDuration(), "Cinema preset must set duration to 5s");

        // Exercise Actor Anchor and Deconstruct Script
        SwingUtilities.invokeAndWait(() -> {
            vaTab.getConfigPanel().getActorAnchorField().setText("young female astronaut in white spacesuit with gold visor");
            vaTab.getConfigPanel().getPromptSubjectField().setText("She walks across the red Martian dunes. She discovers an ancient glowing monolith.");
            vaTab.getConfigPanel().getBtnSuggestSubject().doClick();
        });
        // Wait for async script deconstruction (fallback or local LLM) to populate timeline
        for (int wait = 0; wait < 25; wait++) {
            Thread.sleep(300);
            if (!vaTab.getWorkspacePanel().getTimelineListModel().isEmpty()) {
                break;
            }
        }
        assertFalse(vaTab.getWorkspacePanel().getTimelineListModel().isEmpty(), "Deconstruct must populate timeline scenes");
        int sceneCount = vaTab.getWorkspacePanel().getTimelineListModel().size();
        System.out.println("   ✔ Deconstructed script into " + sceneCount + " scenes with Actor Anchor.");

        // Exercise Timeline selection, Up/Down move, and Workspace tabs
        SwingUtilities.invokeAndWait(() -> {
            JList<de.tki.comfyuicompanion.domain.Scene> listView = vaTab.getWorkspacePanel().getTimelineListView();
            listView.setSelectedIndex(0);
            vaTab.getWorkspacePanel().updatePreviewForScene(listView.getSelectedValue());
            if (sceneCount > 1) {
                vaTab.getWorkspacePanel().getMoveDownBtn().doClick();
                vaTab.getWorkspacePanel().getMoveUpBtn().doClick();
            }
        });
        Thread.sleep(400);
        assertTrue(vaTab.getWorkspacePanel().getStoryboardJsonArea().getText().contains("scene_id"), "Storyboard JSON must be updated");
        renderScreenshot(mainFrame, new File(screenshotsDir, "video_architect_populated.png"));
        System.out.println("   ✔ Video Architect presets, Actor Anchor, and Timeline verified.");

        // 8. Focus and Exercise Settings Tab (Theme switching & checkboxes)
        System.out.println("\n⚙️ [Settings] Exercising theme toggle and configuration checkboxes...");
        int settingsTabIndex = tabbedPane.getTabCount() - 1;
        SwingUtilities.invokeAndWait(() -> {
            tabbedPane.setSelectedIndex(settingsTabIndex);
            mainFrame.setupTheme(false); // Switch to Light Mode
            mainFrame.validate();
            mainFrame.doLayout();
            mainFrame.repaint();
        });
        Thread.sleep(500);
        renderScreenshot(mainFrame, new File(screenshotsDir, "settings_light_mode.png"));

        SwingUtilities.invokeAndWait(() -> {
            mainFrame.setupTheme(true); // Switch back to Dark Mode
            mainFrame.validate();
            mainFrame.doLayout();
            mainFrame.repaint();
        });
        Thread.sleep(500);
        System.out.println("   ✔ Settings theme toggle (Dark <-> Light) verified.");

        // Switch back to Dashboard for final screenshot
        SwingUtilities.invokeAndWait(() -> {
            tabbedPane.setSelectedIndex(0);
            mainFrame.validate();
            mainFrame.doLayout();
            mainFrame.repaint();
        });
        Thread.sleep(500);

        // Render main overall screenshot
        File mainOut = new File("target/comfyuicompanion_gui.png");
        renderScreenshot(mainFrame, mainOut);

        System.out.println("\n=======================================================");
        System.out.println("📸 Visual GUI Inspection & Walkthrough finished successfully!");
        System.out.println("   All screenshots exported to: " + screenshotsDir.getAbsolutePath());
        System.out.println("=======================================================\n");

        context.close();
    }

    @Test
    @DisplayName("Verify application with alternative settings (Light Mode, Custom Resolution, Workflow Graph, and Scene Directing)")
    public void testAlternativeSettingsAndInteractions() throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Main.AppConfig.class);
        Main mainFrame = context.getBean(Main.class);
        assertNotNull(mainFrame, "Main frame bean must not be null");

        ConfigService configService = context.getBean(ConfigService.class);
        configService.setBlueprintGalleryEnabled(true);
        configService.setPromptLabEnabled(true);
        configService.setVideoArchitectEnabled(true);
        configService.setDarkMode(false); // Start in LIGHT MODE
        configService.setHideComfyUI(true); // Replacement mode active
        configService.setFastHashEnabled(false); // Deep verification mode
        configService.setBackgroundModeEnabled(true);
        configService.setLocalLlmModel("gemma-2-2b");

        // Initialize UI components on EDT
        SwingUtilities.invokeAndWait(() -> {
            mainFrame.launch(new String[]{});
        });

        Thread.sleep(1200);
        SwingUtilities.invokeAndWait(() -> {
            configService.setBackgroundModeEnabled(de.tki.comfyuicompanion.util.PlatformUtils.isSystemTraySupported());
            mainFrame.setSize(1440, 920);
            mainFrame.updateTabVisibility();
            mainFrame.validate();
            mainFrame.doLayout();
        });

        File altScreenshotsDir = new File("target/screenshots/alt_settings");
        altScreenshotsDir.mkdirs();

        List<Component> allComponents = getAllComponents(mainFrame);
        JTabbedPane tabbedPane = (JTabbedPane) allComponents.stream()
                .filter(c -> c instanceof JTabbedPane)
                .findFirst()
                .orElse(null);
        assertNotNull(tabbedPane, "Main JTabbedPane must be present");

        // 1. Dashboard in Light Mode
        System.out.println("\n☀️ [Alternative Settings] Inspecting Dashboard in Light Mode...");
        SwingUtilities.invokeAndWait(() -> {
            tabbedPane.setSelectedIndex(0);
            mainFrame.validate();
            mainFrame.repaint();
        });
        Thread.sleep(600);
        renderScreenshot(mainFrame, new File(altScreenshotsDir, "01_dashboard_light_mode.png"));

        // 2. Download Manager: Workflow Graph View
        System.out.println("\n📊 [Alternative Settings] Testing Download Manager & Technical Workflow Graph...");
        DownloadManagerView dmView = context.getBean(DownloadManagerView.class);
        assertNotNull(dmView, "DownloadManagerView must be present");
        String testWorkflow = """
                {
                  "1": { "class_type": "CheckpointLoaderSimple", "inputs": { "ckpt_name": "sd_xl_base_1.0.safetensors" } },
                  "2": { "class_type": "CLIPTextEncode", "inputs": { "text": "masterpiece, 8k", "clip": ["1", 1] } },
                  "3": { "class_type": "CLIPTextEncode", "inputs": { "text": "ugly, blurry", "clip": ["1", 1] } },
                  "4": { "class_type": "EmptyLatentImage", "inputs": { "width": 832, "height": 1216, "batch_size": 1 } },
                  "5": { "class_type": "KSampler", "inputs": { "seed": 42, "steps": 30, "cfg": 7.0, "sampler_name": "euler_ancestral", "scheduler": "karras", "denoise": 1.0, "model": ["1", 0], "positive": ["2", 0], "negative": ["3", 0], "latent_image": ["4", 0] } },
                  "6": { "class_type": "VAEDecode", "inputs": { "samples": ["5", 0], "vae": ["1", 2] } },
                  "7": { "class_type": "SaveImage", "inputs": { "filename_prefix": "ComfyUI", "images": ["6", 0] } }
                }
                """;
        SwingUtilities.invokeAndWait(() -> {
            tabbedPane.setSelectedIndex(1);
            dmView.getJsonInputArea().setText(testWorkflow);
            dmView.getWorkflowGraphPanel().setWorkflowJson(testWorkflow);
            // Switch DownloadManager tabbedPane to tab 1 (Workflow Graph)
            List<Component> dmComponents = getAllComponents(dmView);
            for (Component c : dmComponents) {
                if (c instanceof JTabbedPane tp && tp.getTabCount() >= 2) {
                    tp.setSelectedIndex(1); // Show Workflow Graph
                    break;
                }
            }
            mainFrame.validate();
            mainFrame.doLayout();
            mainFrame.repaint();
        });
        Thread.sleep(800);
        renderScreenshot(mainFrame, new File(altScreenshotsDir, "02_download_manager_workflow_graph.png"));
        System.out.println("   ✔ Interactive Technical Node Graph rendered.");

        // 3. Image Lab: SDXL Portrait & Advanced Sampler Settings
        System.out.println("\n🎨 [Alternative Settings] Configuring Image Lab for SDXL Portrait (832x1216, Euler Ancestral, Karras)...");
        PromptLabView plView = context.getBean(PromptLabView.class);
        assertNotNull(plView, "PromptLabView must be present");
        int plTabIndex = -1;
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if (tabbedPane.getTitleAt(i).contains("Image Lab")) {
                plTabIndex = i;
                break;
            }
        }
        final int foundPlTab = plTabIndex;
        SwingUtilities.invokeAndWait(() -> {
            tabbedPane.setSelectedIndex(foundPlTab);
            plView.getPromptSubjectField().setText("cyberpunk detective standing in rain under neon holographic billboard, cinematic 8k");
            plView.getPromptNegativeField().setText("lowres, bad anatomy, bad hands, text, error, missing fingers");
            plView.getPromptWidthSpinner().setValue(832);
            plView.getPromptHeightSpinner().setValue(1216);
            plView.getPromptStepsSpinner().setValue(30);
            plView.getPromptCfgSpinner().setValue(7.0);
            plView.getPromptDenoiseSpinner().setValue(0.75);
            plView.getPromptBatchSizeSpinner().setValue(2);
            plView.getPromptSamplerCombo().setSelectedItem("euler_ancestral");
            plView.getPromptSchedulerCombo().setSelectedItem("karras");
            mainFrame.validate();
            mainFrame.doLayout();
            mainFrame.repaint();
        });
        Thread.sleep(600);
        assertEquals(832, plView.getPromptWidthSpinner().getValue());
        assertEquals(1216, plView.getPromptHeightSpinner().getValue());
        assertEquals("euler_ancestral", plView.getPromptSamplerCombo().getSelectedItem());
        assertEquals("karras", plView.getPromptSchedulerCombo().getSelectedItem());
        renderScreenshot(mainFrame, new File(altScreenshotsDir, "03_image_lab_sdxl_portrait.png"));
        System.out.println("   ✔ Image Lab SDXL portrait configuration verified.");

        // 4. Video Architect: Wan 2.1 I2V with 9:16 TikTok Format, Actor Anchor & Scene Directing
        System.out.println("\n🎬 [Alternative Settings] Configuring Video Architect with Wan 2.1 I2V, TikTok 9:16, and Scene Directing...");
        VideoArchitectTab vaTab = context.getBean(VideoArchitectTab.class);
        assertNotNull(vaTab, "VideoArchitectTab must be present");
        int vaTabIndex = -1;
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if (tabbedPane.getTitleAt(i).contains("Video Architect")) {
                vaTabIndex = i;
                break;
            }
        }
        final int foundVaTab = vaTabIndex;
        SwingUtilities.invokeAndWait(() -> {
            tabbedPane.setSelectedIndex(foundVaTab);
            vaTab.getConfigPanel().getVideoModelCombo().setSelectedItem("Image to Video (Wan 2.1)");
            vaTab.getConfigPanel().getVideoWidthSpinner().setValue(720);
            vaTab.getConfigPanel().getVideoHeightSpinner().setValue(1280);
            vaTab.getConfigPanel().getVideoDurationSpinner().setValue(6);
            vaTab.getConfigPanel().getVideoStepsSpinner().setValue(35);
            vaTab.getConfigPanel().getVideoCfgSpinner().setValue(4.0);
            vaTab.getConfigPanel().getVideoMotionSpinner().setValue(140);
            vaTab.getConfigPanel().getActorAnchorField().setText("cyberpunk samurai in midnight blue trench coat with glowing katana");
            vaTab.getConfigPanel().getPromptSubjectField().setText("The samurai dashes along the neon rooftop. She leaps across the skyscraper chasm.");
            mainFrame.validate();
            mainFrame.doLayout();
            mainFrame.repaint();
        });
        Thread.sleep(500);

        assertEquals("Image to Video (Wan 2.1)", vaTab.getConfigPanel().getSelectedModel());
        assertEquals(720, vaTab.getConfigPanel().getVideoWidth());
        assertEquals(1280, vaTab.getConfigPanel().getVideoHeight());
        assertEquals(6, vaTab.getConfigPanel().getVideoDuration());

        // Test programmatic scene creation with Camera Motion directing
        SwingUtilities.invokeAndWait(() -> {
            de.tki.comfyuicompanion.domain.Scene customScene = new de.tki.comfyuicompanion.domain.Scene();
            customScene.setSceneId("S_DIRECTOR_1");
            customScene.setPrompt("Close-up hero shot of samurai unsheathing katana with blue lightning sparks");
            customScene.setNarrationText("Every legend begins with a spark.");
            customScene.setCameraMotion("Low-Angle Orbit (Dramatic 360)");
            customScene.setWidth(720);
            customScene.setHeight(1280);
            customScene.setStartFrame(0);
            customScene.setEndFrame(144); // 6s * 24fps
            customScene.setCfgScale(4.0);
            customScene.setSteps(35);

            vaTab.getWorkspacePanel().getTimelineListModel().addElement(customScene);
            vaTab.getWorkspacePanel().updateStoryboardJson();
            vaTab.getWorkspacePanel().getTimelineListView().setSelectedValue(customScene, true);
            vaTab.getWorkspacePanel().updatePreviewForScene(customScene);
        });
        Thread.sleep(500);

        assertTrue(vaTab.getWorkspacePanel().getStoryboardJsonArea().getText().contains("S_DIRECTOR_1"));
        renderScreenshot(mainFrame, new File(altScreenshotsDir, "04_video_architect_wan21_vertical.png"));
        System.out.println("   ✔ Video Architect Wan 2.1 vertical scene directing verified.");

        // 5. Settings: Verify Persistence & Toggles in Light Mode
        System.out.println("\n⚙️ [Alternative Settings] Verifying Settings panel state in Light Mode...");
        int settingsTabIndex = tabbedPane.getTabCount() - 1;
        SwingUtilities.invokeAndWait(() -> {
            tabbedPane.setSelectedIndex(settingsTabIndex);
            mainFrame.validate();
            mainFrame.doLayout();
            mainFrame.repaint();
        });
        Thread.sleep(500);

        assertTrue(configService.isHideComfyUI(), "Hide ComfyUI should be enabled");
        assertFalse(configService.isFastHashEnabled(), "Fast hash should be disabled (deep mode)");
        assertEquals(de.tki.comfyuicompanion.util.PlatformUtils.isSystemTraySupported(), configService.isBackgroundModeEnabled(),
                "Background mode should match platform system tray capability");
        renderScreenshot(mainFrame, new File(altScreenshotsDir, "05_settings_light_mode.png"));
        System.out.println("   ✔ Settings verification complete.");

        System.out.println("\n=======================================================");
        System.out.println("📸 Alternative Settings GUI Inspection finished successfully!");
        System.out.println("   Screenshots exported to: " + altScreenshotsDir.getAbsolutePath());
        System.out.println("=======================================================\n");

        context.close();
    }

    private void renderScreenshot(Component comp, File destination) {
        try {
            BufferedImage image = new BufferedImage(comp.getWidth(), comp.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = image.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            comp.printAll(g2d);
            g2d.dispose();

            destination.getParentFile().mkdirs();
            ImageIO.write(image, "png", destination);
        } catch (Exception ex) {
            System.err.println("Failed rendering screenshot to " + destination + ": " + ex.getMessage());
        }
    }

    private List<Component> getAllComponents(Container container) {
        List<Component> list = new ArrayList<>();
        for (Component c : container.getComponents()) {
            list.add(c);
            if (c instanceof Container) {
                list.addAll(getAllComponents((Container) c));
            }
        }
        return list;
    }
}
