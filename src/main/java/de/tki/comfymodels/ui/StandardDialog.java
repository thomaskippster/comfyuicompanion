package de.tki.comfymodels.ui;

import javax.swing.*;
import java.awt.*;

public class StandardDialog extends JDialog {
    public StandardDialog(Frame owner, String title) {
        super(owner, title, true);
        setLayout(new BorderLayout());
    }

    public JPanel createContentPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));
        return panel;
    }
}
