package de.tki.comfyuicompanion.ui.video;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.ui.ThemeManager;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import java.awt.FlowLayout;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.io.File;

/**
 * Custom ListCellRenderer that renders timeline scenes as styled cards with
 * badges, status flags, and narration previews.
 */
public class SceneCardCellRenderer extends JPanel implements ListCellRenderer<Scene> {
    private final JLabel titleLabel = new JLabel();
    private final JLabel statusBadge = new JLabel();
    private final JLabel filterBadge = new JLabel();
    private final JLabel detailsLabel = new JLabel();
    private final JLabel promptLabel = new JLabel();
    private final JLabel narrationLabel = new JLabel();
    private final JLabel pathLabel = new JLabel();

    public SceneCardCellRenderer() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(8, 12, 8, 12));
        setOpaque(true);

        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        statusBadge.setFont(new Font("SansSerif", Font.BOLD, 10));
        statusBadge.setOpaque(true);

        filterBadge.setFont(new Font("SansSerif", Font.BOLD, 10));
        filterBadge.setOpaque(true);

        detailsLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        detailsLabel.setForeground(ThemeManager.getTextSecondaryColor());

        promptLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        narrationLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        narrationLabel.setForeground(ThemeManager.getTextSecondaryColor());

        pathLabel.setFont(new Font("Monospaced", Font.PLAIN, 10));
        pathLabel.setForeground(ThemeManager.getTextSecondaryColor());

        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        topRow.setOpaque(false);
        topRow.add(titleLabel);
        topRow.add(statusBadge);
        topRow.add(filterBadge);
        topRow.add(detailsLabel);

        add(topRow);
        add(Box.createVerticalStrut(4));
        add(promptLabel);
        add(Box.createVerticalStrut(2));
        add(narrationLabel);
        add(Box.createVerticalStrut(3));
        add(pathLabel);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends Scene> list, Scene scene, int index, boolean isSelected, boolean cellHasFocus) {
        if (scene == null) return this;

        titleLabel.setText("Scene: " + scene.getSceneId());
        titleLabel.setForeground(ThemeManager.getAccentColor());

        int durSecs = Math.max(1, (scene.getEndFrame() - scene.getStartFrame()) / 24);
        detailsLabel.setText(String.format("(%ds, %d frames | CFG: %.1f | Steps: %d)",
                durSecs, (scene.getEndFrame() - scene.getStartFrame()), scene.getCfgScale(), scene.getSteps()));

        boolean hasVideo = scene.getVideoPath() != null && new File(scene.getVideoPath()).exists();
        boolean hasAudio = scene.getAudioPath() != null && new File(scene.getAudioPath()).exists();

        if (hasVideo && hasAudio) {
            statusBadge.setText(" 🎬 Video & Audio Ready ");
            statusBadge.setBackground(new Color(16, 185, 129, 45));
            statusBadge.setForeground(new Color(16, 185, 129));
            statusBadge.setBorder(BorderFactory.createLineBorder(new Color(16, 185, 129, 100), 1, true));
        } else if (hasVideo) {
            statusBadge.setText(" 🎬 Video Ready ");
            statusBadge.setBackground(new Color(16, 185, 129, 45));
            statusBadge.setForeground(new Color(16, 185, 129));
            statusBadge.setBorder(BorderFactory.createLineBorder(new Color(16, 185, 129, 100), 1, true));
        } else if (hasAudio) {
            statusBadge.setText(" 🎙️ Audio Ready ");
            statusBadge.setBackground(new Color(59, 130, 246, 45));
            statusBadge.setForeground(new Color(59, 130, 246));
            statusBadge.setBorder(BorderFactory.createLineBorder(new Color(59, 130, 246, 100), 1, true));
        } else {
            statusBadge.setText(" ⏳ Pending ");
            statusBadge.setBackground(new Color(112, 138, 144, 45));
            statusBadge.setForeground(ThemeManager.getTextSecondaryColor());
            statusBadge.setBorder(BorderFactory.createLineBorder(new Color(112, 138, 144, 100), 1, true));
        }

        if (Math.abs(scene.getContrast() - 1.0) > 0.01 || Math.abs(scene.getBrightness()) > 0.01) {
            filterBadge.setVisible(true);
            filterBadge.setText(String.format(java.util.Locale.US, " 🎨 OpenCV: %.2fx, %+d ", scene.getContrast(), (int) scene.getBrightness()));
            filterBadge.setBackground(new Color(168, 85, 247, 45));
            filterBadge.setForeground(new Color(168, 85, 247));
            filterBadge.setBorder(BorderFactory.createLineBorder(new Color(168, 85, 247, 100), 1, true));
        } else {
            filterBadge.setVisible(false);
        }

        promptLabel.setText("Visual: " + (scene.getPrompt() != null ? scene.getPrompt() : ""));
        promptLabel.setForeground(isSelected ? list.getSelectionForeground() : ThemeManager.getTextColor());

        String narration = scene.getNarrationText();
        if (narration != null && !narration.trim().isEmpty()) {
            narrationLabel.setText("Narration: \"" + narration + "\"");
            narrationLabel.setVisible(true);
        } else {
            narrationLabel.setVisible(false);
        }

        String audioInfo = hasAudio ? " | Audio: " + new File(scene.getAudioPath()).getName() : "";
        pathLabel.setText("File: " + (hasVideo ? scene.getVideoPath() : "Not generated") + audioInfo);

        if (isSelected) {
            setBackground(list.getSelectionBackground());
        } else {
            setBackground(index % 2 == 0 ? ThemeManager.getCardBackgroundColor()
                    : (ThemeManager.isDarkMode() ? new Color(20, 22, 28) : new Color(240, 243, 246)));
        }

        return this;
    }
}
