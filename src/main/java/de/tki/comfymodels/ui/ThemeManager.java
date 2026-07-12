package de.tki.comfymodels.ui;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ThemeManager {
    
    private static boolean isDarkMode = true;
    private static final List<Runnable> observers = new ArrayList<>();

    // --- DARK MODE COLORS (Tron Legacy Style) ---
    public static final Color DARK_BG = new Color(10, 10, 15, 230);
    public static final Color DARK_TEXT = new Color(200, 240, 255);
    public static final Color DARK_BORDER = new Color(0, 100, 120, 180);
    public static final Color DARK_ACCENT = new Color(0, 255, 255); // Neon Cyan
    
    // --- LIGHT MODE COLORS ---
    public static final Color LIGHT_BG = new Color(245, 245, 250, 220);
    public static final Color LIGHT_TEXT = new Color(40, 40, 45);
    public static final Color LIGHT_BORDER = new Color(200, 200, 210, 150);
    public static final Color LIGHT_ACCENT = new Color(0, 150, 136); // Dunkleres Türkis/Teal

    public static boolean isDarkMode() {
        return isDarkMode;
    }

    public static void toggleTheme() {
        isDarkMode = !isDarkMode;
        notifyObservers();
    }

    public static Color getBackgroundColor() {
        return isDarkMode ? DARK_BG : LIGHT_BG;
    }

    public static Color getTextColor() {
        return isDarkMode ? DARK_TEXT : LIGHT_TEXT;
    }

    public static Color getBorderColor() {
        return isDarkMode ? DARK_BORDER : LIGHT_BORDER;
    }

    public static Color getAccentColor() {
        return isDarkMode ? DARK_ACCENT : LIGHT_ACCENT;
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
