package de.tki.comfyuicompanion.ui.video;

import de.tki.comfyuicompanion.domain.Scene;
import de.tki.comfyuicompanion.service.impl.Video4jEditorService;
import de.tki.comfyuicompanion.ui.CardPanel;
import de.tki.comfyuicompanion.ui.ThemeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/**
 * Modal dialog for real-time OpenCV contrast and brightness visual adjustments on a scene.
 */
public class OpenCvEnhancementDialog extends JDialog {
    private static final Logger logger = LoggerFactory.getLogger(OpenCvEnhancementDialog.class);

    public OpenCvEnhancementDialog(Component parent,
                                   Scene scene,
                                   Video4jEditorService video4jEditorService,
                                   Runnable onApplied,
                                   Consumer<String> logConsumer) {
        super(SwingUtilities.getWindowAncestor(parent), "🎨 OpenCV Visual Enhancement - Scene " + scene.getSceneId(), Dialog.ModalityType.APPLICATION_MODAL);
        setSize(680, 580);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));

        JPanel topHeader = new JPanel(new BorderLayout(5, 5));
        topHeader.setBorder(new EmptyBorder(12, 16, 6, 16));
        JLabel title = new JLabel("OpenCV Frame Enhancement: " + scene.getSceneId());
        title.setFont(new Font("SansSerif", Font.BOLD, 15));
        JLabel desc = new JLabel("Adjust real-time contrast (alpha) and brightness (beta) applied during trimming and master render.");
        desc.setFont(new Font("SansSerif", Font.PLAIN, 11));
        desc.setForeground(ThemeManager.getTextSecondaryColor());
        topHeader.add(title, BorderLayout.NORTH);
        topHeader.add(desc, BorderLayout.SOUTH);
        add(topHeader, BorderLayout.NORTH);

        JLabel previewLabel = new JLabel("Loading OpenCV Frame Preview...", SwingConstants.CENTER);
        previewLabel.setFont(new Font("SansSerif", Font.ITALIC, 13));
        previewLabel.setOpaque(true);
        previewLabel.setBackground(ThemeManager.isDarkMode() ? new Color(15, 17, 22) : new Color(235, 238, 242));
        previewLabel.setBorder(BorderFactory.createLineBorder(ThemeManager.getBorderColor(), 1));

        JScrollPane previewScroll = new JScrollPane(previewLabel);
        previewScroll.setBorder(new EmptyBorder(0, 16, 0, 16));
        add(previewScroll, BorderLayout.CENTER);

        CardPanel controlsCard = new CardPanel();
        controlsCard.setLayout(new BoxLayout(controlsCard, BoxLayout.Y_AXIS));
        controlsCard.setBorder(new EmptyBorder(12, 16, 14, 16));

        int initialAlphaInt = (int) Math.round(scene.getContrast() * 100.0);
        if (initialAlphaInt < 50 || initialAlphaInt > 250) initialAlphaInt = 100;
        JSlider contrastSlider = new JSlider(50, 250, initialAlphaInt);
        JLabel contrastValLbl = new JLabel(String.format(java.util.Locale.US, "Contrast: %.2fx", initialAlphaInt / 100.0));
        contrastValLbl.setFont(new Font("SansSerif", Font.BOLD, 12));

        JPanel contrastRow = new JPanel(new BorderLayout(8, 0));
        contrastRow.setOpaque(false);
        contrastRow.add(contrastValLbl, BorderLayout.WEST);
        contrastRow.add(contrastSlider, BorderLayout.CENTER);
        controlsCard.add(contrastRow);
        controlsCard.add(Box.createVerticalStrut(8));

        int initialBetaInt = (int) Math.round(scene.getBrightness());
        if (initialBetaInt < -100 || initialBetaInt > 100) initialBetaInt = 0;
        JSlider brightnessSlider = new JSlider(-100, 100, initialBetaInt);
        JLabel brightnessValLbl = new JLabel(String.format(java.util.Locale.US, "Brightness: %+d", initialBetaInt));
        brightnessValLbl.setFont(new Font("SansSerif", Font.BOLD, 12));

        JPanel brightnessRow = new JPanel(new BorderLayout(8, 0));
        brightnessRow.setOpaque(false);
        brightnessRow.add(brightnessValLbl, BorderLayout.WEST);
        brightnessRow.add(brightnessSlider, BorderLayout.CENTER);
        controlsCard.add(brightnessRow);
        controlsCard.add(Box.createVerticalStrut(12));

        Runnable updatePreview = () -> {
            double alpha = contrastSlider.getValue() / 100.0;
            double beta = brightnessSlider.getValue();
            contrastValLbl.setText(String.format(java.util.Locale.US, "Contrast: %.2fx", alpha));
            brightnessValLbl.setText(String.format(java.util.Locale.US, "Brightness: %+d", (int) beta));

            Thread.ofVirtual().start(() -> {
                try {
                    BufferedImage img = video4jEditorService != null
                            ? video4jEditorService.applyBasicEnhancementAwt(scene, alpha, beta)
                            : null;
                    SwingUtilities.invokeLater(() -> {
                        if (img != null) {
                            int targetW = Math.max(320, previewLabel.getWidth() - 20);
                            int targetH = Math.max(240, previewLabel.getHeight() - 20);
                            double scale = Math.min((double) targetW / img.getWidth(), (double) targetH / img.getHeight());
                            int sw = Math.max(1, (int) (img.getWidth() * scale));
                            int sh = Math.max(1, (int) (img.getHeight() * scale));
                            Image scaled = img.getScaledInstance(sw, sh, Image.SCALE_SMOOTH);
                            previewLabel.setIcon(new ImageIcon(scaled));
                            previewLabel.setText("");
                        } else {
                            previewLabel.setIcon(null);
                            previewLabel.setText("No video or image available yet for scene " + scene.getSceneId());
                        }
                    });
                } catch (Exception ex) {
                    logger.warn("Preview update error: {}", ex.getMessage());
                }
            });
        };

        contrastSlider.addChangeListener(e -> updatePreview.run());
        brightnessSlider.addChangeListener(e -> updatePreview.run());

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnRow.setOpaque(false);

        JButton btnReset = new JButton("Reset");
        btnReset.addActionListener(e -> {
            contrastSlider.setValue(100);
            brightnessSlider.setValue(0);
            updatePreview.run();
        });
        btnRow.add(btnReset);

        JButton btnCancel = new JButton("Cancel");
        btnCancel.addActionListener(e -> dispose());
        btnRow.add(btnCancel);

        JButton btnApply = new JButton("Apply to Scene");
        btnApply.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnApply.putClientProperty("Button.background", ThemeManager.getAccentColor());
        btnApply.putClientProperty("Button.foreground", Color.WHITE);
        btnApply.addActionListener(e -> {
            double finalAlpha = contrastSlider.getValue() / 100.0;
            double finalBeta = brightnessSlider.getValue();
            scene.setContrast(finalAlpha);
            scene.setBrightness(finalBeta);
            if (onApplied != null) onApplied.run();
            if (logConsumer != null) {
                logConsumer.accept(String.format(java.util.Locale.US,
                        "Applied OpenCV enhancement to %s: Contrast=%.2fx, Brightness=%+d",
                        scene.getSceneId(), finalAlpha, (int) finalBeta));
            }
            dispose();
        });
        btnRow.add(btnApply);
        controlsCard.add(btnRow);

        add(controlsCard, BorderLayout.SOUTH);
        updatePreview.run();
    }
}
