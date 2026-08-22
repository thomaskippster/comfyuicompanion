package de.tki.comfymodels.ui;

import de.tki.comfymodels.Main;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class VisualGuiInspectionTest {

    @Test
    @DisplayName("Verify and visually inspect all GUI elements in Main window")
    public void testGuiElementsAndRenderScreenshot() throws Exception {
        // Run in headless-safe or virtual environment
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Main.AppConfig.class);
        Main mainFrame = context.getBean(Main.class);
        assertNotNull(mainFrame, "Main frame bean must not be null");

        // Initialize UI components on EDT
        SwingUtilities.invokeAndWait(() -> {
            mainFrame.launch(new String[]{});
        });

        // Give EDT time to process all pending events
        Thread.sleep(1000);
        SwingUtilities.invokeAndWait(() -> {
            mainFrame.setSize(1400, 900);
            mainFrame.validate();
            mainFrame.doLayout();
        });

        // 1. Recursive check of all GUI components
        List<Component> allComponents = getAllComponents(mainFrame);
        System.out.println("\n=======================================================");
        System.out.println("🔍 [GUI-INSPEKTION] Gefundene Komponenten insgesamt: " + allComponents.size());
        System.out.println("=======================================================");

        long buttonCount = allComponents.stream().filter(c -> c instanceof JButton).count();
        long tableCount = allComponents.stream().filter(c -> c instanceof JTable).count();
        long tabCount = allComponents.stream().filter(c -> c instanceof JTabbedPane).count();
        long labelCount = allComponents.stream().filter(c -> c instanceof JLabel).count();
        long textComponentCount = allComponents.stream().filter(c -> c instanceof javax.swing.text.JTextComponent).count();
        long progressBarCount = allComponents.stream().filter(c -> c instanceof JProgressBar).count();

        System.out.println("🔘 Buttons: " + buttonCount);
        System.out.println("📊 Tabellen: " + tableCount);
        System.out.println("📑 TabbedPanes: " + tabCount);
        System.out.println("🏷️ Labels: " + labelCount);
        System.out.println("📝 Textfelder/Areas: " + textComponentCount);
        System.out.println("📈 Progress Bars: " + progressBarCount);

        assertTrue(buttonCount >= 5, "Es sollten mindestens 5 Buttons vorhanden sein");
        assertTrue(tabCount >= 1, "Mindestens ein JTabbedPane muss vorhanden sein");
        assertTrue(labelCount >= 10, "Es sollten mindestens 10 Labels vorhanden sein");

        // 2. Prüfe Tabs
        JTabbedPane tabbedPane = (JTabbedPane) allComponents.stream()
                .filter(c -> c instanceof JTabbedPane)
                .findFirst()
                .orElse(null);

        assertNotNull(tabbedPane, "JTabbedPane nicht gefunden!");
        int totalTabs = tabbedPane.getTabCount();
        System.out.println("\n📑 Gefundene Tabs (" + totalTabs + "):");
        List<String> tabTitles = new ArrayList<>();
        for (int i = 0; i < totalTabs; i++) {
            String title = tabbedPane.getTitleAt(i);
            tabTitles.add(title);
            System.out.println("   [" + (i + 1) + "] " + title);
        }
        assertTrue(totalTabs >= 3, "Mindestens 3 Tabs erwartet!");

        // 3. Render GUI to BufferedImage
        BufferedImage screenshot = new BufferedImage(1400, 900, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = screenshot.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        mainFrame.printAll(g2d);
        g2d.dispose();

        // 4. Save screenshots to both target and tools directory
        File targetOut = new File("target/comfyuicompanion_gui.png");
        File toolsOut = new File("C:/Users/thoma/Desktop/Tools/comfyuicompanion_gui.png");
        
        targetOut.getParentFile().mkdirs();
        ImageIO.write(screenshot, "png", targetOut);
        ImageIO.write(screenshot, "png", toolsOut);

        System.out.println("\n📸 Screenshot erfolgreich gerendert & gespeichert:");
        System.out.println("   -> " + targetOut.getAbsolutePath());
        System.out.println("   -> " + toolsOut.getAbsolutePath());
        System.out.println("=======================================================\n");

        context.close();
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
