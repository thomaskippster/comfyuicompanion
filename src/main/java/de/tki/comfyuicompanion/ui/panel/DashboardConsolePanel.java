package de.tki.comfyuicompanion.ui.panel;

import de.tki.comfyuicompanion.domain.LaunchProfile;
import de.tki.comfyuicompanion.ui.icons.AppIcon;
import de.tki.comfyuicompanion.ui.icons.SvgIconFactory;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.*;

/**
 * Dedicated UI panel for displaying live ComfyUI process console logs,
 * providing log search with in-place highlighting, and log clearing controls.
 */
public class DashboardConsolePanel extends JPanel {

    private final JTextArea comfyConsoleArea;
    private final JTextField logSearchField;
    private final JButton clearConsoleBtn;
    private Runnable onClearAction;

    /**
     * Constructs a new DashboardConsolePanel and initializes its child components.
     */
    public DashboardConsolePanel() {
        setLayout(new BorderLayout(5, 5));
        setOpaque(false);

        // Console text area configuration
        comfyConsoleArea = new JTextArea();
        Color consoleBg = UIManager.getColor("TextArea.background");
        Color consoleFg = UIManager.getColor("TextArea.foreground");
        comfyConsoleArea.setBackground(consoleBg != null ? consoleBg : new Color(15, 17, 26));
        comfyConsoleArea.setForeground(consoleFg != null ? consoleFg : new Color(220, 220, 220));
        comfyConsoleArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        comfyConsoleArea.setEditable(false);
        comfyConsoleArea.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane consoleScroll = new JScrollPane(comfyConsoleArea) {
            @Override
            public void updateUI() {
                super.updateUI();
                Color c = UIManager.getColor("Component.borderColor");
                if (c != null) {
                    setBorder(BorderFactory.createLineBorder(c, 1));
                }
            }
        };
        Color bc = UIManager.getColor("Component.borderColor");
        consoleScroll.setBorder(BorderFactory.createLineBorder(bc != null ? bc : new Color(60, 60, 60), 1));

        // Toolbar
        JPanel consoleToolbar = new JPanel(new BorderLayout(5, 5));
        consoleToolbar.setOpaque(false);

        logSearchField = new JTextField();
        logSearchField.putClientProperty("JTextField.leadingIcon", SvgIconFactory.get(AppIcon.SEARCH, 14));
        logSearchField.putClientProperty("JTextField.placeholderText", "Search console logs...");

        clearConsoleBtn = new JButton("Clear Console", SvgIconFactory.get(AppIcon.TRASH, 14));
        clearConsoleBtn.putClientProperty("JButton.buttonType", "roundRect");
        clearConsoleBtn.addActionListener(e -> {
            if (onClearAction != null) {
                onClearAction.run();
            } else {
                clear();
            }
        });

        consoleToolbar.add(logSearchField, BorderLayout.CENTER);
        consoleToolbar.add(clearConsoleBtn, BorderLayout.EAST);

        add(consoleToolbar, BorderLayout.NORTH);
        add(consoleScroll, BorderLayout.CENTER);

        initSearchHighlighter();
    }

    private void initSearchHighlighter() {
        logSearchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { highlight(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { highlight(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { highlight(); }

            private void highlight() {
                Highlighter h = comfyConsoleArea.getHighlighter();
                h.removeAllHighlights();
                String text = logSearchField.getText();
                if (text == null || text.trim().isEmpty()) return;
                String content = comfyConsoleArea.getText();
                if (content == null || content.isEmpty()) return;

                String pattern = text.toLowerCase();
                String lowerContent = content.toLowerCase();
                int index = lowerContent.indexOf(pattern);

                Highlighter.HighlightPainter painter =
                        new DefaultHighlighter.DefaultHighlightPainter(new Color(30, 190, 170, 120));
                while (index >= 0) {
                    try {
                        h.addHighlight(index, index + pattern.length(), painter);
                    } catch (BadLocationException ex) {
                        // ignore out of bounds exceptions
                    }
                    index = lowerContent.indexOf(pattern, index + pattern.length());
                }
            }
        });
    }

    /**
     * Appends text to the console area in a thread-safe manner and scrolls to bottom.
     *
     * @param text the text to append
     */
    public void appendLog(String text) {
        if (text == null) return;
        if (SwingUtilities.isEventDispatchThread()) {
            comfyConsoleArea.append(text);
            comfyConsoleArea.setCaretPosition(comfyConsoleArea.getDocument().getLength());
        } else {
            SwingUtilities.invokeLater(() -> {
                comfyConsoleArea.append(text);
                comfyConsoleArea.setCaretPosition(comfyConsoleArea.getDocument().getLength());
            });
        }
    }

    /**
     * Clears all content from the console area.
     */
    public void clear() {
        comfyConsoleArea.setText("");
    }

    /**
     * Returns the full text content currently displayed in the console.
     *
     * @return the console text
     */
    public String getText() {
        return comfyConsoleArea.getText();
    }

    /**
     * Sets the text content in the console area.
     *
     * @param text the new text to set
     */
    public void setText(String text) {
        comfyConsoleArea.setText(text != null ? text : "");
    }

    /**
     * Populates the console text area with an informative ready banner when offline.
     *
     * @param profile the active launch profile
     */
    public void showInitialBanner(LaunchProfile profile) {
        String profileName = profile != null ? profile.name() : "Standard Mode (Default)";
        String args = profile != null && profile.cliArguments() != null && !profile.cliArguments().isEmpty()
                ? String.join(" ", profile.cliArguments())
                : "--listen 127.0.0.1 --port 8188";

        StringBuilder sb = new StringBuilder();
        sb.append("========================================================================================\n");
        sb.append("  ComfyUI Companion | Live Process Console\n");
        sb.append("========================================================================================\n");
        sb.append("  Status        : Offline\n");
        sb.append("  Active Profile: ").append(profileName).append("\n");
        sb.append("  Arguments     : ").append(args).append("\n\n");
        sb.append("  Ready to launch. Click 'Launch' above to launch the ComfyUI server.\n");
        sb.append("========================================================================================\n");

        comfyConsoleArea.setText(sb.toString());
        comfyConsoleArea.setCaretPosition(0);
    }

    /**
     * Sets the custom callback invoked when the user clicks the clear console button.
     *
     * @param onClearAction runnable executed on clear
     */
    public void setOnClearAction(Runnable onClearAction) {
        this.onClearAction = onClearAction;
    }

    /**
     * Gets the process console text area.
     *
     * @return the console text area
     */
    public JTextArea getConsoleArea() {
        return comfyConsoleArea;
    }

    /**
     * Gets the console search text field.
     *
     * @return the search input field
     */
    public JTextField getLogSearchField() {
        return logSearchField;
    }

    /**
     * Gets the clear console button.
     *
     * @return the clear button
     */
    public JButton getClearConsoleBtn() {
        return clearConsoleBtn;
    }
}
