package de.tki.comfymodels.ui;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ThemeManager {
    
    private static boolean isDarkMode = true;
    private static final List<Runnable> observers = new ArrayList<>();

    // --- SCI-FI CYBERSPACE DARK MODE COLORS ---
    public static final Color APP_BG_COLOR = new Color(0, 0, 0); // Pure Black
    public static final Color CARD_BG_COLOR = new Color(0, 0, 0); // Pure Black
    public static final Color TEXT_PRIMARY = new Color(0, 255, 255); // Neon Cyan
    public static final Color TEXT_SECONDARY = new Color(180, 190, 205);
    public static final Color BORDER_COLOR = new Color(0, 255, 255); // Neon Cyan
    public static final Color ACCENT_COLOR_PRIMARY = new Color(0, 255, 255); // Neon Cyan
    
    // --- LIGHT MODE COLORS ---
    public static final Color LIGHT_BG = new Color(245, 245, 250);
    public static final Color LIGHT_CARD_BG = new Color(255, 255, 255);
    public static final Color LIGHT_TEXT = new Color(40, 40, 45);
    public static final Color LIGHT_TEXT_SEC = new Color(100, 100, 105);
    public static final Color LIGHT_BORDER = new Color(0, 0, 0, 30);
    public static final Color LIGHT_ACCENT = new Color(0, 120, 215);

    public static boolean isDarkMode() {
        return isDarkMode;
    }

    public static void toggleTheme() {
        isDarkMode = !isDarkMode;
        notifyObservers();
    }

    public static Color getAppBackgroundColor() {
        return isDarkMode ? APP_BG_COLOR : LIGHT_BG;
    }

    public static Color getCardBackgroundColor() {
        return isDarkMode ? CARD_BG_COLOR : LIGHT_CARD_BG;
    }

    public static Color getTextColor() {
        return isDarkMode ? TEXT_PRIMARY : LIGHT_TEXT;
    }

    public static Color getTextSecondaryColor() {
        return isDarkMode ? TEXT_SECONDARY : LIGHT_TEXT_SEC;
    }

    public static Color getBorderColor() {
        return isDarkMode ? BORDER_COLOR : LIGHT_BORDER;
    }

    public static Color getAccentColor() {
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
