package de.tki.comfymodels.ui;

import javax.swing.*;
import java.awt.*;

public class GlassPanel extends JPanel {

    private int cornerRadius = 15;

    public GlassPanel() {
        setOpaque(false); // Sehr wichtig für Transparenz in Swing
        
        // Registriere dieses Panel für dynamische Theme-Updates
        ThemeManager.registerObserver(this::repaint);
    }

    public void setCornerRadius(int radius) {
        this.cornerRadius = radius;
        repaint();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        // Repaint when FlatLaf theme changes so we pick up new UIManager colors
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        // Nicht super.paintComponent aufrufen, da wir custom zeichnen
        Graphics2D g2 = (Graphics2D) g.create();
        
        // Anti-Aliasing für weiche Ecken
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 1. Hintergrund zeichnen (mit Alpha für Frosted Glass Effekt)
        g2.setColor(ThemeManager.getCardBackgroundColor());
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, cornerRadius, cornerRadius);

        // 2. Subtilen Rand zeichnen (Lichtkante)
        g2.setColor(ThemeManager.getBorderColor());
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, cornerRadius, cornerRadius);

        g2.dispose();
        
        super.paintComponent(g); // Erlaubt Child-Komponenten, sich zu zeichnen
    }
}
