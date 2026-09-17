package de.tki.comfyuicompanion.ui.blueprint;

import javax.swing.JToggleButton;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * A self-painting checkbox that renders its own box and checkmark using Java2D,
 * completely bypassing FlatLaf's LAF checkbox icon for reliable appearance in both
 * dark and light themes.
 */
public class ThemedCheckBox extends JToggleButton {
    private boolean darkMode;

    // Colors
    private static final Color DARK_BOX_BG        = new Color(0x1C202B);
    private static final Color DARK_BOX_BORDER     = new Color(0x8E98B0);
    private static final Color DARK_HOVER_BG       = new Color(0x262B3A);
    private static final Color DARK_HOVER_BORDER   = new Color(0x00D2BE);
    private static final Color DARK_CHECKED_BG     = new Color(0x009688);
    private static final Color DARK_CHECKED_BORDER = new Color(0x00D2BE);

    private static final Color LIGHT_BOX_BG        = Color.WHITE;
    private static final Color LIGHT_BOX_BORDER     = new Color(0x6B7280);
    private static final Color LIGHT_HOVER_BG       = new Color(0xF3F4F6);
    private static final Color LIGHT_HOVER_BORDER   = new Color(0x009688);
    private static final Color LIGHT_CHECKED_BG     = new Color(0x009688);
    private static final Color LIGHT_CHECKED_BORDER = new Color(0x00796B);

    private static final Color CHECK_COLOR = Color.WHITE;
    private static final int   BOX_SIZE    = 14;
    private static final int   ARC         = 4;
    private static final int   GAP         = 6;

    private boolean hovered = false;

    public ThemedCheckBox(String text, boolean darkMode) {
        super(text);
        this.darkMode = darkMode;
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setFont(new Font("SansSerif", Font.BOLD, 11));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        applyColors();

        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hovered = true;  repaint(); }
            @Override public void mouseExited (MouseEvent e) { hovered = false; repaint(); }
        });
    }

    public void setDarkMode(boolean dark) {
        this.darkMode = dark;
        applyColors();
        repaint();
    }

    private void applyColors() {
        setForeground(darkMode ? new Color(0xE2E8F0) : new Color(0x1F2937));
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        int textW = fm.stringWidth(getText());
        int textH = fm.getAscent() + fm.getDescent();
        int h = Math.max(BOX_SIZE, textH) + 6;
        return new Dimension(BOX_SIZE + GAP + textW + 4, h);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        int h  = getHeight();
        int bY = (h - BOX_SIZE) / 2;

        Color boxBg;
        Color boxBorder;
        if (isSelected()) {
            boxBg     = darkMode ? DARK_CHECKED_BG     : LIGHT_CHECKED_BG;
            boxBorder = darkMode ? DARK_CHECKED_BORDER : LIGHT_CHECKED_BORDER;
        } else if (hovered) {
            boxBg     = darkMode ? DARK_HOVER_BG     : LIGHT_HOVER_BG;
            boxBorder = darkMode ? DARK_HOVER_BORDER : LIGHT_HOVER_BORDER;
        } else {
            boxBg     = darkMode ? DARK_BOX_BG     : LIGHT_BOX_BG;
            boxBorder = darkMode ? DARK_BOX_BORDER : LIGHT_BOX_BORDER;
        }

        RoundRectangle2D box = new RoundRectangle2D.Float(1, bY, BOX_SIZE - 2, BOX_SIZE - 2, ARC, ARC);
        g2.setColor(boxBg);
        g2.fill(box);
        g2.setColor(boxBorder);
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(box);

        if (isSelected()) {
            g2.setColor(CHECK_COLOR);
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int x0 = 3, y0 = bY + BOX_SIZE / 2;
            int x1 = 3 + (BOX_SIZE - 6) / 3, y1 = bY + BOX_SIZE - 4;
            int x2 = BOX_SIZE - 3, y2 = bY + 4;
            g2.drawLine(x0, y0, x1, y1);
            g2.drawLine(x1, y1, x2, y2);
        }

        g2.setColor(getForeground());
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        int textX = BOX_SIZE + GAP;
        int textY = (h - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(getText(), textX, textY);

        g2.dispose();
    }
}
