package de.tki.comfymodels.ui;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ThemeManager {
    
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
        notifyObservers();
    }

    public static Color getAppBackgroundColor() {
        // Pull from UIManager to match Dashboard panels
        Color c = UIManager.getColor("MainTabs.gradientStart");
        if (c != null) return c;
        return isDarkMode ? APP_BG_COLOR : LIGHT_BG;
    }

    public static Color getCardBackgroundColor() {
        // Pull from UIManager so GlassPanel/CardPanel match the FlatLaf-styled panels
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
        // Pull from UIManager so GlassPanel/CardPanel match the FlatLaf-styled panels
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
