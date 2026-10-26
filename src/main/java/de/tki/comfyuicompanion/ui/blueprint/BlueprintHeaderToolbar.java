package de.tki.comfyuicompanion.ui.blueprint;

import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintEntry;
import de.tki.comfyuicompanion.ui.BlueprintGalleryTab.BlueprintStatus;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.Function;

/**
 * Header toolbar for the Blueprint Gallery tab.
 * Encapsulates search input, category dropdown, filter checkboxes,
 * summary counts, status notifications, and the refresh button.
 */
public class BlueprintHeaderToolbar extends JPanel {

    private final JTextField txtSearch;
    private final JComboBox<String> cbCategory;
    private final ThemedCheckBox chkReadyOnly;
    private final ThemedCheckBox chkHideCloud;
    private final JButton btnRefresh;
    private final JLabel lblSummary;
    private final JLabel lblStatus;

    private Runnable onSearchChanged;
    private Runnable onCategoryChanged;
    private Runnable onReadyOnlyChanged;
    private Runnable onHideCloudChanged;
    private Runnable onRefreshClicked;

    /**
     * Constructs the header toolbar with the given initial theme mode.
     *
     * @param initialDarkMode true if the initial theme is dark mode, false otherwise
     */
    public BlueprintHeaderToolbar(boolean initialDarkMode) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setBorder(new EmptyBorder(14, 22, 6, 22));

        // Row 1: Summary title and search/filter/refresh controls
        JPanel row1 = new JPanel(new BorderLayout(12, 0));
        row1.setOpaque(false);

        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.setOpaque(false);

        lblSummary = new JLabel(" ");
        lblSummary.setFont(new Font("SansSerif", Font.PLAIN, 12));
        titleBlock.add(lblSummary);
        row1.add(titleBlock, BorderLayout.WEST);

        JPanel mainControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        mainControls.setOpaque(false);

        txtSearch = new JTextField(15);
        txtSearch.putClientProperty("JTextField.placeholderText", "Search blueprints...");
        txtSearch.putClientProperty("JTextField.leadingIcon", SvgIconFactory.get(AppIcon.SEARCH));
        txtSearch.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (onSearchChanged != null) {
                    onSearchChanged.run();
                }
            }
        });

        cbCategory = new JComboBox<>();
        cbCategory.addItem("All Categories");
        cbCategory.addActionListener(e -> {
            if (onCategoryChanged != null) {
                onCategoryChanged.run();
            }
        });

        btnRefresh = new JButton("Refresh", SvgIconFactory.get(AppIcon.REFRESH));
        btnRefresh.setFont(new Font("SansSerif", Font.PLAIN, 12));
        btnRefresh.addActionListener(e -> {
            if (onRefreshClicked != null) {
                onRefreshClicked.run();
            }
        });

        mainControls.add(txtSearch);
        mainControls.add(cbCategory);
        mainControls.add(btnRefresh);
        row1.add(mainControls, BorderLayout.EAST);

        add(row1);
        add(Box.createVerticalStrut(10));

        // Row 2: Checkbox options and status indicator
        JPanel row2 = new JPanel(new BorderLayout());
        row2.setOpaque(false);

        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        optionsPanel.setOpaque(false);

        chkReadyOnly = new ThemedCheckBox("Ready to Run only", initialDarkMode);
        chkReadyOnly.setFont(new Font("SansSerif", Font.PLAIN, 12));
        chkReadyOnly.addActionListener(e -> {
            if (onReadyOnlyChanged != null) {
                onReadyOnlyChanged.run();
            }
        });

        chkHideCloud = new ThemedCheckBox("Hide Cloud Workflows", initialDarkMode);
        chkHideCloud.setFont(new Font("SansSerif", Font.PLAIN, 12));
        chkHideCloud.setSelected(true);
        chkHideCloud.addActionListener(e -> {
            if (onHideCloudChanged != null) {
                onHideCloudChanged.run();
            }
        });

        optionsPanel.add(chkReadyOnly);
        optionsPanel.add(chkHideCloud);

        lblStatus = new JLabel(" ");
        lblStatus.setFont(new Font("SansSerif", Font.ITALIC, 11));

        row2.add(optionsPanel, BorderLayout.WEST);
        row2.add(lblStatus, BorderLayout.EAST);

        add(row2);
        add(Box.createVerticalStrut(6));

        updateTheme(initialDarkMode);
    }

    /**
     * Gets the current text entered in the search field.
     *
     * @return the search query string
     */
    public String getSearchText() {
        return txtSearch != null ? txtSearch.getText() : "";
    }

    /**
     * Sets the text in the search input field.
     *
     * @param text new search query
     */
    public void setSearchText(String text) {
        if (txtSearch != null) {
            txtSearch.setText(text != null ? text : "");
        }
    }

    /**
     * Returns the raw search text component.
     *
     * @return JTextField
     */
    public JTextField getSearchField() {
        return txtSearch;
    }

    /**
     * Gets the currently selected category from the dropdown.
     *
     * @return the selected category name, or null if none selected
     */
    public String getSelectedCategory() {
        return cbCategory != null ? (String) cbCategory.getSelectedItem() : null;
    }

    /**
     * Checks if the "Ready to Run only" filter checkbox is selected.
     *
     * @return true if ready-only filter is active
     */
    public boolean isReadyOnly() {
        return chkReadyOnly != null && chkReadyOnly.isSelected();
    }

    /**
     * Checks if the "Hide Cloud Workflows" filter checkbox is selected.
     *
     * @return true if hide cloud workflows filter is active
     */
    public boolean isHideCloud() {
        return chkHideCloud != null && chkHideCloud.isSelected();
    }

    /**
     * Updates the available category items in the dropdown while maintaining current selection.
     *
     * @param categories the list of category names to display
     */
    public void updateCategories(List<String> categories) {
        if (cbCategory == null) {
            return;
        }

        ActionListener[] listeners = cbCategory.getActionListeners();
        for (ActionListener l : listeners) {
            cbCategory.removeActionListener(l);
        }

        String selected = (String) cbCategory.getSelectedItem();
        cbCategory.removeAllItems();
        cbCategory.addItem("All Categories");

        if (categories != null) {
            for (String cat : categories) {
                cbCategory.addItem(cat);
            }
        }

        if (selected != null) {
            cbCategory.setSelectedItem(selected);
        } else {
            cbCategory.setSelectedIndex(0);
        }

        for (ActionListener l : listeners) {
            cbCategory.addActionListener(l);
        }
    }

    /**
     * Sets the text displayed in the summary label.
     *
     * @param text the summary description text
     */
    public void setSummaryText(String text) {
        if (lblSummary != null) {
            lblSummary.setText(text != null ? text : " ");
        }
    }

    /**
     * Computes and updates the summary text from the provided entries and status supplier.
     *
     * @param entries the list of all entries
     * @param statusSupplier function to resolve blueprint status
     */
    public void updateSummary(List<BlueprintEntry> entries,
                              Function<BlueprintEntry, BlueprintStatus> statusSupplier) {
        if (lblSummary == null || entries == null) {
            return;
        }
        int total = entries.size();
        int ready = 0;
        int partial = 0;
        int missing = 0;
        int checking = 0;
        for (BlueprintEntry e : entries) {
            if (!e.hasBeenValidated && e.requiredModels.isEmpty()) {
                checking++;
                continue;
            }
            BlueprintStatus s = statusSupplier.apply(e);
            if (s.missingCount == 0 && s.presentCount > 0) {
                ready++;
            } else if (s.presentCount > 0) {
                partial++;
            } else {
                missing++;
            }
        }
        String suffix = checking > 0 ? "  ·  " + checking + " checking" : "";
        setSummaryText(total + " workflows on Comfy.org  ·  "
                + ready + " ready  ·  " + partial + " partial  ·  " + missing + " not installed" + suffix);
    }

    /**
     * Sets the text displayed in the status label.
     *
     * @param text the status message text
     */
    public void setStatusText(String text) {
        if (lblStatus != null) {
            lblStatus.setText(text != null ? text : " ");
        }
    }

    /**
     * Sets the visual state and enabled status of the refresh button.
     *
     * @param refreshing true if a refresh operation is currently executing
     */
    public void setRefreshing(boolean refreshing) {
        if (btnRefresh == null) {
            return;
        }
        if (refreshing) {
            btnRefresh.setEnabled(false);
            btnRefresh.setText("Refreshing…");
        } else {
            btnRefresh.setEnabled(true);
            btnRefresh.setText("Refresh");
        }
    }

    /**
     * Updates the toolbar colors and styles based on the active theme mode.
     *
     * @param darkMode true for dark theme, false for light theme
     */
    public void updateTheme(boolean darkMode) {
        Color bgPage = darkMode ? new Color(15, 16, 21) : new Color(245, 247, 250);
        Color textSecondary = darkMode ? new Color(120, 126, 152) : new Color(90, 100, 110);
        Color textDim = darkMode ? new Color(80, 86, 110) : new Color(140, 145, 160);

        setBackground(bgPage);

        if (lblSummary != null) {
            lblSummary.setForeground(textSecondary);
        }
        if (lblStatus != null) {
            lblStatus.setForeground(textDim);
        }
        if (chkReadyOnly != null) {
            chkReadyOnly.setDarkMode(darkMode);
        }
        if (chkHideCloud != null) {
            chkHideCloud.setDarkMode(darkMode);
        }
    }

    /**
     * Registers a callback invoked when the search field text changes.
     *
     * @param onSearchChanged the callback runnable
     */
    public void setOnSearchChanged(Runnable onSearchChanged) {
        this.onSearchChanged = onSearchChanged;
    }

    /**
     * Registers a callback invoked when the category selection changes.
     *
     * @param onCategoryChanged the callback runnable
     */
    public void setOnCategoryChanged(Runnable onCategoryChanged) {
        this.onCategoryChanged = onCategoryChanged;
    }

    /**
     * Registers a callback invoked when the ready-to-run checkbox is toggled.
     *
     * @param onReadyOnlyChanged the callback runnable
     */
    public void setOnReadyOnlyChanged(Runnable onReadyOnlyChanged) {
        this.onReadyOnlyChanged = onReadyOnlyChanged;
    }

    /**
     * Registers a callback invoked when the hide-cloud-workflows checkbox is toggled.
     *
     * @param onHideCloudChanged the callback runnable
     */
    public void setOnHideCloudChanged(Runnable onHideCloudChanged) {
        this.onHideCloudChanged = onHideCloudChanged;
    }

    /**
     * Registers a callback invoked when the refresh button is clicked.
     *
     * @param onRefreshClicked the callback runnable
     */
    public void setOnRefreshClicked(Runnable onRefreshClicked) {
        this.onRefreshClicked = onRefreshClicked;
    }
}
