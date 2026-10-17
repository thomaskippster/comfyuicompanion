package de.tki.comfyuicompanion.ui;

import javax.swing.*;
import java.awt.*;

/**
 * A standard base class for application dialogs, providing consistent styling
 * and a default content panel layout.
 */
public class StandardDialog extends JDialog {
    
    /**
     * Constructs a new StandardDialog.
     * 
     * @param owner the owner frame
     * @param title the dialog title
     */
    public StandardDialog(Frame owner, String title) {
        super(owner, title, true);
        setLayout(new BorderLayout());
    }

    /**
     * Constructs a new StandardDialog with a generic Window owner.
     * 
     * @param owner the owner window
     * @param title the dialog title
     */
    public StandardDialog(Window owner, String title) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout());
    }

    /**
     * Creates and returns a consistently padded content panel using GridBagLayout.
     * 
     * @return a new JPanel for dialog content
     */
    public JPanel createContentPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));
        return panel;
    }
}
