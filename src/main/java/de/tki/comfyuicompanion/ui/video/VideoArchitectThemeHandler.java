package de.tki.comfyuicompanion.ui.video;

import de.tki.comfyuicompanion.ui.ThemeManager;

import javax.swing.*;
import java.awt.*;

/**
 * Handles UI theming and button state management for VideoArchitectTab.
 */
public final class VideoArchitectThemeHandler {

    private VideoArchitectThemeHandler() {}

    public static void applyTheme(JComponent root,
                                  boolean darkMode,
                                  JLabel videoPresetLabel,
                                  JLabel videoPreviewLabel,
                                  JList<?> timelineListView,
                                  JScrollPane timelineScroll,
                                  JTextArea videoConsoleArea,
                                  JTextArea storyboardJsonArea,
                                  JButton btnSuggestSubject,
                                  JButton btnAutoPilot,
                                  JButton btnGenerateScenes,
                                  JButton btnMasterRender) {
        SwingUtilities.updateComponentTreeUI(root);
        if (videoPresetLabel != null) {
            Color c = UIManager.getColor("PromptLab.presetForeground");
            if (c != null) videoPresetLabel.setForeground(c);
        }
        if (videoPreviewLabel != null) {
            videoPreviewLabel.setBackground(UIManager.getColor("TextField.background"));
        }
        if (timelineListView != null) {
            timelineListView.setBackground(darkMode ? new Color(15, 17, 22) : Color.WHITE);
        }
        if (timelineScroll != null && timelineScroll.getViewport() != null) {
            timelineScroll.getViewport().setBackground(darkMode ? new Color(15, 17, 22) : Color.WHITE);
        }
        if (videoConsoleArea != null) {
            if (darkMode) {
                videoConsoleArea.setBackground(UIManager.getColor("TextArea.background"));
                videoConsoleArea.setForeground(UIManager.getColor("TextArea.foreground"));
            } else {
                videoConsoleArea.setBackground(new Color(245, 247, 250));
                videoConsoleArea.setForeground(new Color(30, 30, 30));
            }
        }
        if (storyboardJsonArea != null) {
            if (darkMode) {
                storyboardJsonArea.setBackground(UIManager.getColor("TextArea.background"));
                storyboardJsonArea.setForeground(UIManager.getColor("TextArea.foreground"));
            } else {
                storyboardJsonArea.setBackground(new Color(245, 247, 250));
                storyboardJsonArea.setForeground(new Color(30, 30, 30));
            }
        }
        if (btnSuggestSubject != null) {
            btnSuggestSubject.putClientProperty("Button.background", ThemeManager.getAccentColor());
            btnSuggestSubject.putClientProperty("Button.foreground", Color.WHITE);
        }
        if (btnAutoPilot != null) {
            btnAutoPilot.putClientProperty("Button.background", new Color(255, 204, 0));
            btnAutoPilot.putClientProperty("Button.foreground", Color.BLACK);
        }
        if (btnGenerateScenes != null) {
            btnGenerateScenes.putClientProperty("Button.background", ThemeManager.getAccentColor());
            btnGenerateScenes.putClientProperty("Button.foreground", Color.WHITE);
        }
        if (btnMasterRender != null) {
            btnMasterRender.putClientProperty("Button.background", new Color(255, 204, 0));
            btnMasterRender.putClientProperty("Button.foreground", Color.BLACK);
        }
        root.revalidate();
        root.repaint();
    }

    public static void setButtonsDisabled(boolean disabled, JButton... buttons) {
        SwingUtilities.invokeLater(() -> {
            for (JButton btn : buttons) {
                if (btn != null) {
                    btn.setEnabled(!disabled);
                }
            }
        });
    }
}
