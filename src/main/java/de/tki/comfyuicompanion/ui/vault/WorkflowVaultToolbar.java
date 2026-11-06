package de.tki.comfyuicompanion.ui.vault;

import de.tki.comfyuicompanion.ui.blueprint.ThemedCheckBox;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Filter and architecture grouping toolbar for My Workflow Vault.
 * Provides interactive pills for model architectures (Flux, Qwen, SDXL, Wan, etc.),
 * search debouncing, readiness filtering, and batch model download triggering.
 */
public class WorkflowVaultToolbar extends JPanel {

    private final JPanel archPillsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private JLabel lblGroup;
    private final ThemedCheckBox chkReadyOnly;
    private final JTextField txtSearch;
    private final JButton btnDownloadMissing;
    private final ButtonGroup pillGroup = new ButtonGroup();
    private final List<JToggleButton> pillButtons = new ArrayList<>();

    private String selectedArchitecture = "All";
    private Runnable onFilterChanged;
    private Runnable onBatchDownloadClicked;

    /**
     * Constructs a new WorkflowVaultToolbar.
     *
     * @param darkMode initial theme state
     */
    public WorkflowVaultToolbar(boolean darkMode) {
        setLayout(new BorderLayout(12, 0));
        setOpaque(false);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        setPreferredSize(new Dimension(1100, 36));

        // Left: Architecture pills
        archPillsPanel.setOpaque(false);
        lblGroup = new JLabel("Model Architecture:");
        lblGroup.setFont(new Font("SansSerif", Font.BOLD, 11));
        lblGroup.setForeground(darkMode ? new Color(150, 156, 180) : new Color(71, 85, 105));
        archPillsPanel.add(lblGroup);

        String[] defaultArchitectures = {"All", "Flux", "Qwen", "SDXL", "Wan", "Z-Image"};
        updateArchitecturePills(List.of(defaultArchitectures), selectedArchitecture);
        add(archPillsPanel, BorderLayout.WEST);

        // Right: Ready checkbox, search input, batch download button
        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightControls.setOpaque(false);

        chkReadyOnly = new ThemedCheckBox("Ready to Run only", darkMode);
        chkReadyOnly.setFont(new Font("SansSerif", Font.PLAIN, 12));
        chkReadyOnly.addActionListener(e -> {
            if (onFilterChanged != null) onFilterChanged.run();
        });

        txtSearch = new de.tki.comfyuicompanion.ui.workflow.DebouncedSearchField("Search My Vault...", 14, 300);
        ((de.tki.comfyuicompanion.ui.workflow.DebouncedSearchField) txtSearch).setOnSearch(() -> {
            if (onFilterChanged != null) onFilterChanged.run();
        });

        btnDownloadMissing = new JButton("Download Missing Models", SvgIconFactory.get(AppIcon.DOWNLOAD, 14));
        btnDownloadMissing.setFont(new Font("SansSerif", Font.BOLD, 11));
        btnDownloadMissing.setBackground(new Color(217, 119, 6));
        btnDownloadMissing.setForeground(Color.WHITE);
        btnDownloadMissing.putClientProperty("Button.arc", 999);
        btnDownloadMissing.addActionListener(e -> {
            if (onBatchDownloadClicked != null) onBatchDownloadClicked.run();
        });

        rightControls.add(chkReadyOnly);
        rightControls.add(txtSearch);
        rightControls.add(btnDownloadMissing);
        add(rightControls, BorderLayout.EAST);
    }

    /**
     * Updates the architecture pills dynamically based on available vault entries.
     *
     * @param architectures list of architecture names
     * @param activeName    the currently selected architecture
     */
    public void updateArchitecturePills(List<String> architectures, String activeName) {
        archPillsPanel.removeAll();
        pillButtons.clear();
        while (pillGroup.getButtonCount() > 0) {
            pillGroup.remove(pillGroup.getElements().nextElement());
        }

        lblGroup = new JLabel("Model Architecture:");
        lblGroup.setFont(new Font("SansSerif", Font.BOLD, 11));
        boolean dark = de.tki.comfyuicompanion.ui.ThemeManager.isDarkMode();
        lblGroup.setForeground(dark ? new Color(150, 156, 180) : new Color(71, 85, 105));
        archPillsPanel.add(lblGroup);

        for (String arch : architectures) {
            boolean isSelected = arch.equalsIgnoreCase(activeName);
            JToggleButton pill = new JToggleButton(arch, isSelected);
            pill.setFont(new Font("SansSerif", isSelected ? Font.BOLD : Font.PLAIN, 11));
            pill.putClientProperty("Button.arc", 999);
            if (isSelected) {
                pill.setBackground(new Color(45, 95, 200));
                pill.setForeground(Color.WHITE);
            }
            pill.addActionListener(e -> {
                this.selectedArchitecture = arch;
                highlightSelectedPill(pill);
                if (onFilterChanged != null) onFilterChanged.run();
            });

            pillGroup.add(pill);
            pillButtons.add(pill);
            archPillsPanel.add(pill);
        }

        archPillsPanel.revalidate();
        archPillsPanel.repaint();
    }

    private void highlightSelectedPill(JToggleButton activePill) {
        for (JToggleButton btn : pillButtons) {
            if (btn == activePill) {
                btn.setBackground(new Color(45, 95, 200));
                btn.setForeground(Color.WHITE);
                btn.setFont(new Font("SansSerif", Font.BOLD, 11));
            } else {
                btn.setBackground(null);
                btn.setForeground(null);
                btn.setFont(new Font("SansSerif", Font.PLAIN, 11));
            }
        }
    }

    public void setOnFilterChanged(Runnable onFilterChanged) { this.onFilterChanged = onFilterChanged; }
    public void setOnBatchDownloadClicked(Runnable onBatchDownloadClicked) { this.onBatchDownloadClicked = onBatchDownloadClicked; }

    public String getSelectedArchitecture() { return selectedArchitecture; }
    public String getSearchText() { return txtSearch.getText(); }
    public boolean isReadyOnly() { return chkReadyOnly.isSelected(); }

    public void updateMissingCount(int count) {
        if (count <= 0) {
            btnDownloadMissing.setVisible(false);
        } else {
            btnDownloadMissing.setVisible(true);
            btnDownloadMissing.setText("Download Missing Models (" + count + ")");
        }
    }

    public void updateTheme(boolean darkMode) {
        if (lblGroup != null) {
            lblGroup.setForeground(darkMode ? new Color(150, 156, 180) : new Color(71, 85, 105));
        }
        if (chkReadyOnly != null) {
            chkReadyOnly.setDarkMode(darkMode);
        }
        repaint();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        updateTheme(de.tki.comfyuicompanion.ui.ThemeManager.isDarkMode());
    }
}
