package de.tki.comfymodels.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ThemeManager {

    private static final Logger logger = LoggerFactory.getLogger(ThemeManager.class);
    
    private static boolean isDarkMode = true;
    private static final List<Runnable> observers = new ArrayList<>();

    // --- SCI-FI CYBERSPACE DARK MODE COLORS (Redesign) ---
    public static final Color APP_BG_COLOR = new Color(10, 11, 14); // #0a0b0e (Deep blue-black)
    public static final Color CARD_BG_COLOR = new Color(24, 27, 33); // #181b21 (Panels)
    public static final Color TEXT_PRIMARY = new Color(224, 248, 245); // #e0f8f5 (Soft cyan/white)
    public static final Color TEXT_SECONDARY = new Color(112, 138, 144); // #708a90 (Dimmed text)
    public static final Color BORDER_COLOR = new Color(42, 46, 56); // #2a2e38 (Subtle borders)
    public static final Color ACCENT_COLOR_PRIMARY = new Color(30, 190, 170); // #1ebeaa (Subtle Teal/Cyan)
    
    // --- LIGHT MODE FALLBACK COLORS (used when UIManager keys are not yet set) ---
    public static final Color LIGHT_BG = new Color(245, 245, 250);
    public static final Color LIGHT_CARD_BG = new Color(255, 255, 255);
    public static final Color LIGHT_TEXT = new Color(40, 40, 45);
    public static final Color LIGHT_TEXT_SEC = new Color(100, 100, 105);
    public static final Color LIGHT_BORDER = new Color(0, 0, 0, 30);
    public static final Color LIGHT_ACCENT = new Color(0, 120, 215);

    public static boolean isDarkMode() {
        return isDarkMode;
    }

    public static void setDarkMode(boolean dark) {
        isDarkMode = dark;
    }

    public static void toggleTheme() {
        isDarkMode = !isDarkMode;
        applyTheme(isDarkMode);
        notifyObservers();
    }

    /**
     * Applies complete LookAndFeel and color scheme overrides for dark or light mode.
     */
    public static void applyTheme(boolean darkMode) {
        try {
            setDarkMode(darkMode);

            // Global arcs for modern cyber/sci-fi styling
            UIManager.put("Button.arc", 0);
            UIManager.put("Component.arc", 0);
            UIManager.put("TextComponent.arc", 0);
            UIManager.put("ProgressBar.arc", 0);
            UIManager.put("TitlePane.unifiedBackground", true);
            UIManager.put("CheckBox.icon.focusWidth", 1);

            // Typography setup
            Font defaultFont = new Font("Segoe UI", Font.PLAIN, 13);
            for (String fontName : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
                if (fontName.equalsIgnoreCase("Inter") || fontName.equalsIgnoreCase("Roboto")) {
                    defaultFont = new Font(fontName, Font.PLAIN, 13);
                    break;
                }
            }
            UIManager.put("defaultFont", defaultFont);

            if (darkMode) {
                Color nodeBg = new javax.swing.plaf.ColorUIResource(10, 11, 14);
                Color comfySurface = new javax.swing.plaf.ColorUIResource(10, 11, 14);
                Color componentBg = new javax.swing.plaf.ColorUIResource(24, 27, 33);
                Color comfyAccent = new javax.swing.plaf.ColorUIResource(30, 190, 170);
                Color comfyText = new javax.swing.plaf.ColorUIResource(224, 248, 245);
                Color paleBlue = new javax.swing.plaf.ColorUIResource(112, 138, 144);
                Color comfyBorder = new javax.swing.plaf.ColorUIResource(42, 46, 56);

                UIManager.put("DefaultBackgroundColor", comfySurface);
                UIManager.put("Panel.background", nodeBg);
                UIManager.put("Table.background", componentBg);
                UIManager.put("TextArea.background", nodeBg);
                UIManager.put("TextField.background", nodeBg);
                UIManager.put("PasswordField.background", nodeBg);

                UIManager.put("Label.foreground", comfyText);
                UIManager.put("Table.foreground", comfyText);
                UIManager.put("TextArea.foreground", comfyText);
                UIManager.put("TextField.foreground", comfyText);

                UIManager.put("Table.selectionBackground", comfyAccent);
                UIManager.put("Table.selectionForeground", nodeBg);
                UIManager.put("List.selectionBackground", comfyAccent);
                UIManager.put("List.selectionForeground", nodeBg);
                UIManager.put("Component.focusedBorderColor", comfyAccent);
                UIManager.put("Component.borderColor", comfyBorder);
                UIManager.put("TextComponent.borderWidth", 1);
                UIManager.put("Separator.foreground", comfyBorder);

                UIManager.put("Button.background", componentBg);
                UIManager.put("Button.foreground", comfyText);
                UIManager.put("Button.focusedBackground", comfyAccent);
                UIManager.put("Button.hoverBackground", comfyAccent);
                UIManager.put("Button.hoverForeground", nodeBg);
                UIManager.put("Button.pressedBackground", paleBlue);
                UIManager.put("Button.borderColor", comfyBorder);
                UIManager.put("Button.borderWidth", 1);

                UIManager.put("ScrollBar.track", comfySurface);
                UIManager.put("ScrollBar.thumb", comfyBorder);

                UIManager.put("TabbedPane.selectedBackground", nodeBg);
                UIManager.put("TabbedPane.selectedForeground", comfyAccent);
                UIManager.put("TabbedPane.foreground", paleBlue);
                UIManager.put("TabbedPane.underlineColor", comfyAccent);
                UIManager.put("TabbedPane.underlineHeight", 3);

                UIManager.put("ProgressBar.foreground", comfyAccent);
                UIManager.put("ProgressBar.background", componentBg);
                UIManager.put("ProgressBar.arc", 0);

                UIManager.put("Card.background", componentBg);
                UIManager.put("Card.border", comfyBorder);
                UIManager.put("Card.placeholder", new Color(10, 11, 14, 200));
                UIManager.put("Card.placeholderBorder", comfyBorder);
                UIManager.put("Toolbar.customBg", componentBg);
                UIManager.put("SlimStat.titleForeground", comfyText);
                UIManager.put("SlimStat.valueForeground", comfyAccent);
                UIManager.put("SlimStat.barForeground", comfyAccent);
                UIManager.put("SlimStat.barBackground", nodeBg);
                UIManager.put("MainTabs.gradientStart", nodeBg);
                UIManager.put("MainTabs.gradientEnd", nodeBg);
                UIManager.put("MainTabs.glowStart", new Color(30, 190, 170, 10));
                UIManager.put("PromptLab.presetForeground", comfyAccent);

                UIManager.setLookAndFeel(new FlatDarkLaf());
            } else {
                String[] keysToClear = {
                    "DefaultBackgroundColor", "Panel.background", "Table.background", "TextArea.background",
                    "TextField.background", "PasswordField.background", "Label.foreground",
                    "Table.foreground", "TextArea.foreground", "Table.selectionBackground",
                    "Table.selectionForeground", "List.selectionBackground", "List.selectionForeground", "Component.focusedBorderColor", "Separator.foreground",
                    "Button.background", "Button.foreground", "Button.focusedBackground",
                    "Button.hoverBackground", "Button.pressedBackground", "Button.borderColor",
                    "ScrollBar.track", "ScrollBar.thumb", "TabbedPane.selectedBackground",
                    "TabbedPane.selectedForeground", "ProgressBar.foreground", "ProgressBar.background"
                };
                for (String key : keysToClear) {
                    UIManager.put(key, null);
                }
                UIManager.setLookAndFeel(new FlatLightLaf());

                Color pronouncedBorder = new javax.swing.plaf.ColorUIResource(180, 185, 195);
                UIManager.put("Component.borderColor", pronouncedBorder);
                UIManager.put("Button.borderColor", pronouncedBorder);
                UIManager.put("Separator.foreground", new javax.swing.plaf.ColorUIResource(200, 205, 215));

                UIManager.put("TabbedPane.selectedBackground", new javax.swing.plaf.ColorUIResource(new Color(0, 120, 150, 25)));
                UIManager.put("TabbedPane.selectedForeground", new Color(20, 30, 40));
                UIManager.put("TabbedPane.underlineColor", new Color(0, 120, 150));

                Color lightPanel = new Color(245, 247, 250);
                UIManager.put("Card.background", lightPanel);
                UIManager.put("Card.border", pronouncedBorder);
                UIManager.put("Card.placeholder", new Color(240, 240, 240, 200));
                UIManager.put("Card.placeholderBorder", pronouncedBorder);
                UIManager.put("Toolbar.customBg", lightPanel);
                UIManager.put("SlimStat.titleForeground", new Color(40, 50, 60));
                UIManager.put("SlimStat.valueForeground", new Color(0, 120, 150));
                UIManager.put("SlimStat.barForeground", new Color(0, 120, 150));
                UIManager.put("SlimStat.barBackground", new Color(225, 230, 235));
                UIManager.put("MainTabs.gradientStart", new Color(245, 247, 250));
                UIManager.put("MainTabs.gradientEnd", new Color(255, 255, 255));
                UIManager.put("MainTabs.glowStart", new Color(0, 120, 150, 8));
                UIManager.put("PromptLab.presetForeground", new Color(30, 100, 200));
            }

            UIManager.put("Button.arc", 12);
            UIManager.put("Component.arc", 16);
            UIManager.put("TextComponent.arc", 12);
            UIManager.put("ProgressBar.arc", 999);
        } catch (Exception e) {
            logger.error("Failed to apply LookAndFeel theme: {}", e.getMessage(), e);
        }
    }

    public static Color getAppBackgroundColor() {
        Color c = UIManager.getColor("MainTabs.gradientStart");
        if (c != null) return c;
        return isDarkMode ? APP_BG_COLOR : LIGHT_BG;
    }

    public static Color getCardBackgroundColor() {
        Color c = UIManager.getColor("Card.background");
        if (c != null) return c;
        return isDarkMode ? CARD_BG_COLOR : LIGHT_CARD_BG;
    }

    public static Color getTextColor() {
        Color c = UIManager.getColor("Label.foreground");
        if (c != null) return c;
        return isDarkMode ? TEXT_PRIMARY : LIGHT_TEXT;
    }

    public static Color getTextSecondaryColor() {
        return isDarkMode ? TEXT_SECONDARY : LIGHT_TEXT_SEC;
    }

    public static Color getBorderColor() {
        Color c = UIManager.getColor("Card.border");
        if (c != null) return c;
        return isDarkMode ? BORDER_COLOR : LIGHT_BORDER;
    }

    public static Color getAccentColor() {
        Color c = UIManager.getColor("ProgressBar.foreground");
        if (c != null) return c;
        return isDarkMode ? ACCENT_COLOR_PRIMARY : LIGHT_ACCENT;
    }

    public static void registerObserver(Runnable observer) {
        observers.add(observer);
    }

    private static void notifyObservers() {
        for (Runnable obs : observers) {
            SwingUtilities.invokeLater(obs);
        }
    }
}
