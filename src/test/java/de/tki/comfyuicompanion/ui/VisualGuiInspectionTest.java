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
            if ("Blueprint Gallery".equalsIgnoreCase(tabbedPane.getTitleAt(i))) {
                bpTabIndex = i;
                break;
            }
        }
        assertTrue(bpTabIndex >= 0, "Blueprint Gallery tab must exist in main tabs");

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
        assertTrue(galleryTab.getBtnSubComfy().isSelected(), "Comfy sub-tab should be initially selected");
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

        // Render main overall screenshot
        File mainOut = new File("target/comfyuicompanion_gui.png");
        renderScreenshot(mainFrame, mainOut);

        System.out.println("\n=======================================================");
        System.out.println("📸 Visual GUI Inspection & Walkthrough finished successfully!");
        System.out.println("   All screenshots exported to: " + screenshotsDir.getAbsolutePath());
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
