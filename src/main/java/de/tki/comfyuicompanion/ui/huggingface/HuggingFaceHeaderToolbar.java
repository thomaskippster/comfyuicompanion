package de.tki.comfyuicompanion.ui.huggingface;

import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * Header toolbar for the Hugging Face Hub gallery, matching the look and feel
 * of the Civitai Model Hub toolbar with title row, search input, format, task,
 * and sort filters, and refresh action.
 */
public class HuggingFaceHeaderToolbar extends JPanel {

    private final JTextField searchField = new JTextField();
    private final JComboBox<String> formatCombo = new JComboBox<>(new String[]{
            "All Formats", "ComfyUI Workflows", "GGUF", "Safetensors", "Diffusers"
    });
    private final JComboBox<String> taskCombo = new JComboBox<>(new String[]{
            "All Tasks", "Text-to-Image", "Image-to-Video", "Text-to-Video", "Image-to-Image"
    });
    private final JComboBox<String> sortCombo = new JComboBox<>(new String[]{
            "Trending", "Most Downloads", "Recently Updated", "Most Likes"
    });
    private final JButton refreshBtn = new JButton("Refresh", SvgIconFactory.get(AppIcon.REFRESH, 16));
    private final JLabel statusLabel = new JLabel(" ");

    private final Timer searchDebounceTimer;
    private Runnable filterChangedListener;

    /**
     * Constructs a new HuggingFaceHeaderToolbar and initializes its visual components.
     */
    public HuggingFaceHeaderToolbar() {
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
        // Top row: Title and status (matching CivitaiHeaderToolbar)
        JPanel topRow = new JPanel(new BorderLayout(10, 0));
        topRow.setOpaque(false);

        JLabel titleLabel = new JLabel("Hugging Face Hub");
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
        searchField.putClientProperty("JTextField.placeholderText", "Search Hugging Face models, e.g. flux, wan, city96, kijai...");
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

        // Filter: Format
        formatCombo.setPreferredSize(new Dimension(130, 32));
        formatCombo.addActionListener(e -> triggerFilterChange());
        gbc.gridx = 1;
        gbc.weightx = 0;
        controlsRow.add(formatCombo, gbc);

        // Filter: Task
        taskCombo.setPreferredSize(new Dimension(130, 32));
        taskCombo.addActionListener(e -> triggerFilterChange());
        gbc.gridx = 2;
        controlsRow.add(taskCombo, gbc);

        // Filter: Sort
        sortCombo.setPreferredSize(new Dimension(140, 32));
        sortCombo.addActionListener(e -> triggerFilterChange());
        gbc.gridx = 3;
        controlsRow.add(sortCombo, gbc);

        // Refresh Button
        refreshBtn.setPreferredSize(new Dimension(100, 32));
        refreshBtn.addActionListener(e -> triggerFilterChange());
        gbc.gridx = 4;
        gbc.insets = new Insets(0, 0, 0, 0);
        controlsRow.add(refreshBtn, gbc);

        add(controlsRow, BorderLayout.CENTER);
    }

    private void triggerFilterChange() {
        if (filterChangedListener != null) {
            filterChangedListener.run();
        }
    }

    public void setOnFilterChanged(Runnable listener) {
        this.filterChangedListener = listener;
    }

    public String getSearchQuery() {
        return searchField.getText() != null ? searchField.getText().trim() : "";
    }

    public String getSelectedFormat() {
        String sel = (String) formatCombo.getSelectedItem();
        return (sel != null && !sel.startsWith("All")) ? sel.toLowerCase(java.util.Locale.ROOT) : "";
    }

    public String getSelectedTask() {
        String sel = (String) taskCombo.getSelectedItem();
        return (sel != null && !sel.startsWith("All")) ? sel.toLowerCase(java.util.Locale.ROOT).replace(" ", "-") : "";
    }

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

    public void setStatusText(String text) {
        statusLabel.setText(text != null ? text : " ");
    }

    public void setRefreshEnabled(boolean enabled) {
        refreshBtn.setEnabled(enabled);
        searchField.setEnabled(enabled);
    }

    public void updateTheme(boolean darkMode) {
        statusLabel.setForeground(darkMode ? Color.LIGHT_GRAY : Color.DARK_GRAY);
        repaint();
    }
}
