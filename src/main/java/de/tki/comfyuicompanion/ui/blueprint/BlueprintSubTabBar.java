package de.tki.comfyuicompanion.ui.blueprint;

import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Top navigation sub-tab switcher for the Blueprint Gallery tab.
 * Manages toggle buttons and transitions between My Workflow Vault,
 * Comfy.org Blueprints, Civitai Workflows, and Hugging Face Workflows.
 */
public class BlueprintSubTabBar extends JPanel {

    private final JToggleButton btnSubVault;
    private final JToggleButton btnSubComfy;
    private final JToggleButton btnSubCivitai;
    private final JToggleButton btnSubHf;
    private final ButtonGroup subGroup = new ButtonGroup();

    private Runnable onVaultSelected;
    private Runnable onComfySelected;
    private Runnable onCivitaiSelected;
    private Runnable onHfSelected;

    /**
     * Constructs a new BlueprintSubTabBar with default pill-styled toggle buttons.
     */
    public BlueprintSubTabBar() {
        setLayout(new BorderLayout(15, 0));
        setOpaque(false);
        setBorder(new EmptyBorder(10, 15, 6, 15));

        JPanel switchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        switchPanel.setOpaque(false);

        btnSubVault = createPillButton("My Workflow Vault", AppIcon.ARCHIVE, 175, true);
        btnSubComfy = createPillButton("Comfy.org Blueprints", AppIcon.BLUEPRINT_GALLERY, 185, false);
        btnSubCivitai = createPillButton("Civitai Workflows", AppIcon.DOWNLOAD_MANAGER, 165, false);
        btnSubHf = createPillButton("Hugging Face Workflows", AppIcon.MODEL_HUB, 185, false);

        subGroup.add(btnSubVault);
        subGroup.add(btnSubComfy);
        subGroup.add(btnSubCivitai);
        subGroup.add(btnSubHf);

        btnSubVault.addActionListener(e -> {
            if (onVaultSelected != null) onVaultSelected.run();
        });
        btnSubComfy.addActionListener(e -> {
            if (onComfySelected != null) onComfySelected.run();
        });
        btnSubCivitai.addActionListener(e -> {
            if (onCivitaiSelected != null) onCivitaiSelected.run();
        });
        btnSubHf.addActionListener(e -> {
            if (onHfSelected != null) onHfSelected.run();
        });

        switchPanel.add(btnSubVault);
        switchPanel.add(btnSubComfy);
        switchPanel.add(btnSubCivitai);
        switchPanel.add(btnSubHf);
        add(switchPanel, BorderLayout.WEST);
    }

    private JToggleButton createPillButton(String title, AppIcon icon, int width, boolean selected) {
        JToggleButton btn = new JToggleButton(title, SvgIconFactory.get(icon, 15), selected);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setPreferredSize(new Dimension(width, 32));
        btn.putClientProperty("Button.arc", 999);
        btn.setFocusPainted(false);
        return btn;
    }

    /** Sets the callback invoked when My Workflow Vault tab is selected. */
    public void setOnVaultSelected(Runnable callback) { this.onVaultSelected = callback; }

    /** Sets the callback invoked when Comfy.org Blueprints tab is selected. */
    public void setOnComfySelected(Runnable callback) { this.onComfySelected = callback; }

    /** Sets the callback invoked when Civitai Workflows tab is selected. */
    public void setOnCivitaiSelected(Runnable callback) { this.onCivitaiSelected = callback; }

    /** Sets the callback invoked when Hugging Face Workflows tab is selected. */
    public void setOnHfSelected(Runnable callback) { this.onHfSelected = callback; }

    /** Returns the My Workflow Vault toggle button. */
    public JToggleButton getBtnSubVault() { return btnSubVault; }

    /** Returns the Comfy.org Blueprints toggle button. */
    public JToggleButton getBtnSubComfy() { return btnSubComfy; }

    /** Returns the Civitai Workflows toggle button. */
    public JToggleButton getBtnSubCivitai() { return btnSubCivitai; }

    /** Returns the Hugging Face Workflows toggle button. */
    public JToggleButton getBtnSubHf() { return btnSubHf; }
}
