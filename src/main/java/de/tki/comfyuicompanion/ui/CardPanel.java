package de.tki.comfyuicompanion.ui;

import javax.swing.*;
import java.awt.*;

/**
 * A custom JPanel that renders with a card-like appearance,
 * featuring rounded corners and a subtle border.
 */
public class CardPanel extends JPanel {

    private int cornerRadius = 16;

    /**
     * Constructs a new CardPanel with default padding and registers it
     * for theme updates.
     */
    public CardPanel() {
        setOpaque(false);
        // Add default padding for card content
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        ThemeManager.registerObserver(this::repaint);
    }

    /**
     * Sets the corner radius for the card's rounded corners.
     * 
     * @param radius the corner radius in pixels
     */
    public void setCornerRadius(int radius) {
        this.cornerRadius = radius;
        repaint();
    }

    /**
     * Updates the UI and repaints the component to reflect any theme changes.
     */
    @Override
    public void updateUI() {
        super.updateUI();
        // Repaint when FlatLaf theme changes so we pick up new UIManager colors
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 1. Draw Card Background
        g2.setColor(ThemeManager.getCardBackgroundColor());
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, cornerRadius, cornerRadius);

        // 2. Draw subtle translucent border
        g2.setColor(ThemeManager.getBorderColor());
        g2.setStroke(new BasicStroke(1.0f));
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, cornerRadius, cornerRadius);

        g2.dispose();
        super.paintComponent(g);
    }
}
