package de.tki.comfyuicompanion.ui.civitai;

import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * Header toolbar for the Civitai Model Hub gallery containing search input,
 * type and architecture filters, sort controls, and refresh actions.
 * Filter dropdowns are populated dynamically from Civitai metadata.
 */
public class CivitaiHeaderToolbar extends JPanel {

    private final JTextField searchField = new JTextField();
    private final JComboBox<String> typeCombo = new JComboBox<>(new String[]{
            "Workflows", "All Types", "Checkpoint", "LORA", "VAE", "ControlNet", "Upscaler", "TextualInversion"
    });
    private final JComboBox<String> baseModelCombo = new JComboBox<>(new String[]{
            "All Bases"
    });
    private final JComboBox<String> sortCombo = new JComboBox<>(new String[]{
            "Highest Rated", "Most Downloaded", "Newest"
    });
    private final JToggleButton favoritesOnlyToggle = new JToggleButton("★ Favorites");
    private final JButton legalBtn = new JButton("⚖️ Legal & Licenses");
    private final JButton incompleteDownloadsBtn = new JButton("Incomplete Downloads", SvgIconFactory.get(AppIcon.STORAGE_OPTIMIZER, 14));
    private final JButton refreshBtn = new JButton("Refresh", SvgIconFactory.get(AppIcon.REFRESH, 16));
    private final JLabel statusLabel = new JLabel(" ");

    private final Timer searchDebounceTimer;
    private Runnable filterChangedListener;
    private Runnable incompleteDownloadsListener;

    /**
     * Constructs a new CivitaiHeaderToolbar and initializes its visual components and listeners.
     */
    public CivitaiHeaderToolbar() {
        super(new BorderLayout(10, 8));
        setBorder(new EmptyBorder(10, 15, 10, 15));

        searchDebounceTimer = new Timer(450, e -> {
            if (filterChangedListener != null) {
                filterChangedListener.run();
            }
        });
        searchDebounceTimer.setRepeats(false);

        buildUi();
    }

    private void buildUi() {
        // Top row: Title and status
        JPanel topRow = new JPanel(new BorderLayout(10, 0));
        topRow.setOpaque(false);

        JLabel titleLabel = new JLabel("Civitai Model Hub");
        titleLabel.putClientProperty("FlatLaf.styleClass", "h2");
        topRow.add(titleLabel, BorderLayout.WEST);

        statusLabel.setForeground(Color.GRAY);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12f));
        topRow.add(statusLabel, BorderLayout.EAST);
        add(topRow, BorderLayout.NORTH);

        // Center row: Controls
        JPanel controlsRow = new JPanel(new GridBagLayout());
        controlsRow.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 0, 8);
        gbc.gridy = 0;

        // Search Input
        searchField.putClientProperty("JTextField.placeholderText", "Search Civitai models, LoRAs, Checkpoints...");
        searchField.setPreferredSize(new Dimension(280, 32));
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                searchDebounceTimer.restart();
            }
        });
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        controlsRow.add(searchField, gbc);

        // Filter: Type
        typeCombo.setPreferredSize(new Dimension(130, 32));
        typeCombo.addActionListener(e -> triggerFilterChange());
        gbc.gridx = 1;
        gbc.weightx = 0;
        controlsRow.add(typeCombo, gbc);

        // Filter: Base Model
        baseModelCombo.setPreferredSize(new Dimension(130, 32));
        baseModelCombo.addActionListener(e -> triggerFilterChange());
        gbc.gridx = 2;
        controlsRow.add(baseModelCombo, gbc);

        // Sort
        sortCombo.setPreferredSize(new Dimension(140, 32));
        sortCombo.addActionListener(e -> triggerFilterChange());
        gbc.gridx = 3;
        controlsRow.add(sortCombo, gbc);

        // Favorites Toggle
        favoritesOnlyToggle.setFont(new Font("SansSerif", Font.PLAIN, 11));
        favoritesOnlyToggle.setToolTipText("Filter and show only bookmarked favorite models");
        favoritesOnlyToggle.putClientProperty("Button.arc", 999);
        favoritesOnlyToggle.setFocusPainted(false);
        favoritesOnlyToggle.addActionListener(e -> triggerFilterChange());
        gbc.gridx = 4;
        controlsRow.add(favoritesOnlyToggle, gbc);

        // Legal notice & compliance button
        legalBtn.setToolTipText("View legal notices, user liability disclaimer, youth protection filter, and CivitAI licenses");
        legalBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        legalBtn.putClientProperty("Button.arc", 999);
        legalBtn.setFocusPainted(false);
        legalBtn.addActionListener(e -> de.tki.comfyuicompanion.ui.dialog.LegalNoticeDialog.show(this));
        gbc.gridx = 5;
        controlsRow.add(legalBtn, gbc);

        // Incomplete Downloads & Cleanup Button
        incompleteDownloadsBtn.setToolTipText("Inspect, resume, or clean up incomplete model downloads (.cmfd, .part, .tmp) to reclaim disk space");
        incompleteDownloadsBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        incompleteDownloadsBtn.putClientProperty("Button.arc", 999);
        incompleteDownloadsBtn.setFocusPainted(false);
        incompleteDownloadsBtn.addActionListener(e -> {
            if (incompleteDownloadsListener != null) {
                incompleteDownloadsListener.run();
            }
        });
        gbc.gridx = 6;
        controlsRow.add(incompleteDownloadsBtn, gbc);

        // Refresh Button
        refreshBtn.setPreferredSize(new Dimension(100, 32));
        refreshBtn.putClientProperty("Button.arc", 999);
        refreshBtn.setFocusPainted(false);
        refreshBtn.addActionListener(e -> triggerFilterChange());
        gbc.gridx = 7;
        gbc.insets = new Insets(0, 0, 0, 0);
        controlsRow.add(refreshBtn, gbc);

        add(controlsRow, BorderLayout.CENTER);
    }

    private void triggerFilterChange() {
        if (filterChangedListener != null) {
            filterChangedListener.run();
        }
    }

    /**
     * Dynamically updates the base models filter combo box with active architectures from Civitai.
     * Preserves the current selection if it exists in the updated list.
     *
     * @param baseModels list of base model architectures retrieved from Civitai metadata
     */
    public void setAvailableBaseModels(List<String> baseModels) {
        if (baseModels == null || baseModels.isEmpty()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            String selected = (String) baseModelCombo.getSelectedItem();
            baseModelCombo.removeAllItems();
            baseModelCombo.addItem("All Bases");
            for (String bm : baseModels) {
                if (bm != null && !bm.isBlank() && !bm.equalsIgnoreCase("All Bases") && !bm.equalsIgnoreCase("All")) {
                    baseModelCombo.addItem(bm.trim());
                }
            }
            if (selected != null) {
                baseModelCombo.setSelectedItem(selected);
            }
            if (baseModelCombo.getSelectedIndex() < 0) {
                baseModelCombo.setSelectedIndex(0);
            }
        });
    }

    /**
     * Dynamically updates the model type filter combo box with supported types from Civitai.
     * Preserves the current selection if it exists in the updated list.
     *
     * @param types list of model types retrieved from Civitai metadata
     */
    public void setAvailableTypes(List<String> types) {
        if (types == null || types.isEmpty()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            String selected = (String) typeCombo.getSelectedItem();
            typeCombo.removeAllItems();
            typeCombo.addItem("Workflows");
            typeCombo.addItem("All Types");
            for (String t : types) {
                if (t != null && !t.isBlank() && !t.equalsIgnoreCase("All Types") && !t.equalsIgnoreCase("All") && !t.equalsIgnoreCase("Workflows")) {
                    typeCombo.addItem(t.trim());
                }
            }
            if (selected != null) {
                typeCombo.setSelectedItem(selected);
            }
            if (typeCombo.getSelectedIndex() < 0) {
                typeCombo.setSelectedIndex(0);
            }
        });
    }

    /**
     * Sets the callback triggered when the incomplete downloads button is clicked.
     *
     * @param listener the runnable callback
     */
    public void setOnIncompleteDownloadsClicked(Runnable listener) {
        this.incompleteDownloadsListener = listener;
    }

    /**
     * Sets the callback triggered when any search or filter option changes.
     *
     * @param listener the runnable callback
     */
    public void setOnFilterChanged(Runnable listener) {
        this.filterChangedListener = listener;
    }

    /**
     * Retrieves the current search text query.
     *
     * @return search text
     */
    public String getSearchQuery() {
        return searchField.getText().trim();
    }

    /**
     * Sets the query text in the search input field.
     *
     * @param query the search query
     */
    public void setSearchQuery(String query) {
        if (searchField != null) {
            searchField.setText(query != null ? query : "");
        }
    }

    /**
     * Returns the raw search text component.
     *
     * @return JTextField
     */
    public JTextField getSearchField() {
        return searchField;
    }

    /**
     * Retrieves the selected model type filter.
     *
     * @return selected model type
     */
    public String getSelectedType() {
        return (String) typeCombo.getSelectedItem();
    }

    /**
     * Retrieves the selected base architecture filter.
     *
     * @return selected base model
     */
    public String getSelectedBaseModel() {
        return (String) baseModelCombo.getSelectedItem();
    }

    /**
     * Retrieves the selected sorting mode.
     *
     * @return selected sort
     */
    public String getSelectedSort() {
        return (String) sortCombo.getSelectedItem();
    }

    /**
     * Checks whether the favorites-only filter toggle is active.
     *
     * @return true if only bookmarked models should be shown
     */
    public boolean isFavoritesOnly() {
        return favoritesOnlyToggle.isSelected();
    }

    /**
     * Sets the state of the favorites-only filter toggle.
     *
     * @param favoritesOnly true to filter by favorites
     */
    public void setFavoritesOnly(boolean favoritesOnly) {
        favoritesOnlyToggle.setSelected(favoritesOnly);
    }

    /**
     * Retrieves whether NSFW content is permitted.
     * Always returns false as adult/NSFW content is prohibited app-wide.
     *
     * @return false (NSFW is permanently disabled)
     */
    public boolean isAllowNsfw() {
        return false;
    }

    /**
     * Updates the status text displayed in the top right.
     *
     * @param text the status text to display
     */
    public void setStatusText(String text) {
        statusLabel.setText(text != null ? text : " ");
    }

    /**
     * Enables or disables the refresh button.
     *
     * @param enabled true to enable, false to disable
     */
    public void setRefreshEnabled(boolean enabled) {
        refreshBtn.setEnabled(enabled);
    }

    /**
     * Updates theme styling for header labels and visual elements.
     *
     * @param darkMode true for dark theme, false for light theme
     */
    public void updateTheme(boolean darkMode) {
        statusLabel.setForeground(darkMode ? Color.LIGHT_GRAY : Color.DARK_GRAY);
        repaint();
    }
}
