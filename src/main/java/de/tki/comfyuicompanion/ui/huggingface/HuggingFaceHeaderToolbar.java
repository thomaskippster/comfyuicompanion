package de.tki.comfyuicompanion.ui.huggingface;

import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;
import de.tki.comfyuicompanion.ui.workflow.DebouncedSearchField;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * Header toolbar for the Hugging Face Workflows gallery, providing debounced search input,
 * category filter, sort controls, and refresh action. Permanently restricted to ComfyUI workflows.
 *
 * @author Senior Java Developer
 */
public class HuggingFaceHeaderToolbar extends JPanel {

    private final DebouncedSearchField searchField = new DebouncedSearchField("Search ComfyUI workflows on Hugging Face...", 15, 350);
    private final JComboBox<String> categoryCombo = new JComboBox<>(new String[]{
            "All Categories", "Text-to-Image", "Image-to-Video", "Text-to-Video", "Image-to-Image"
    });
    private final JComboBox<String> sortCombo = new JComboBox<>(new String[]{
            "Trending", "Most Downloads", "Recently Updated", "Most Likes"
    });
    private final JButton refreshBtn = new JButton("Refresh", SvgIconFactory.get(AppIcon.REFRESH, 16));
    private final JLabel statusLabel = new JLabel(" ");

    private Runnable filterChangedListener;

    /**
     * Constructs a new HuggingFaceHeaderToolbar and initializes its visual components.
     */
    public HuggingFaceHeaderToolbar() {
        super(new BorderLayout(10, 8));
        setBorder(new EmptyBorder(10, 15, 10, 15));
        buildUi();
    }

    private void buildUi() {
        // Top row: Title and status
        JPanel topRow = new JPanel(new BorderLayout(10, 0));
        topRow.setOpaque(false);

        JLabel titleLabel = new JLabel("Hugging Face Workflows");
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

        // Debounced Search Input
        searchField.setPreferredSize(new Dimension(280, 32));
        searchField.setOnSearch(text -> triggerFilterChange());
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        controlsRow.add(searchField, gbc);

        // Filter: Category
        categoryCombo.setPreferredSize(new Dimension(150, 32));
        categoryCombo.addActionListener(e -> triggerFilterChange());
        gbc.gridx = 1;
        gbc.weightx = 0;
        controlsRow.add(categoryCombo, gbc);

        // Filter: Sort
        sortCombo.setPreferredSize(new Dimension(140, 32));
        sortCombo.addActionListener(e -> triggerFilterChange());
        gbc.gridx = 2;
        controlsRow.add(sortCombo, gbc);

        // Refresh Button
        refreshBtn.setPreferredSize(new Dimension(100, 32));
        refreshBtn.addActionListener(e -> triggerFilterChange());
        gbc.gridx = 3;
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
     * Registers a listener callback invoked when any filter or query changes.
     *
     * @param listener the runnable callback
     */
    public void setOnFilterChanged(Runnable listener) {
        this.filterChangedListener = listener;
    }

    /**
     * Retrieves the current search query string.
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
     * Returns the underlying search field component.
     *
     * @return JTextField component
     */
    public JTextField getSearchField() {
        return searchField;
    }

    /**
     * Retrieves the selected pipeline category filter.
     *
     * @return pipeline category or empty string if all categories
     */
    public String getSelectedCategory() {
        String sel = (String) categoryCombo.getSelectedItem();
        if (sel == null || sel.equalsIgnoreCase("All Categories")) {
            return "";
        }
        return sel.toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Retrieves the selected model format filter, permanently restricted to comfyui.
     *
     * @return "comfyui"
     */
    public String getSelectedFormat() {
        return "comfyui";
    }

    /**
     * Retrieves the selected pipeline task filter.
     *
     * @return task filter or empty string
     */
    public String getSelectedTask() {
        return getSelectedCategory();
    }

    /**
     * Retrieves the selected sort key.
     *
     * @return sort key (e.g. "downloads", "likes", "lastModified")
     */
    public String getSelectedSort() {
        String sel = (String) sortCombo.getSelectedItem();
        if (sel == null) return "trendingScore";
        return switch (sel) {
            case "Most Downloads" -> "downloads";
            case "Recently Updated" -> "lastModified";
            case "Most Likes" -> "likes";
            default -> "trendingScore";
        };
    }

    /**
     * Updates the status text displayed in the top right.
     *
     * @param text status text
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
